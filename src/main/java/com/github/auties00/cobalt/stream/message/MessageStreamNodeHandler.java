package com.github.auties00.cobalt.stream.message;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppHistorySyncException;
import com.github.auties00.cobalt.exception.WhatsAppLidMigrationException;
import com.github.auties00.cobalt.exception.WhatsAppMediaException;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.message.receive.receipt.MessageReceiptHandler;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveStanza;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveStanzaParser;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.action.ContactActionBuilder;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.ChatEphemeralTimer;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactStatus;
import com.github.auties00.cobalt.model.info.ChatMessageInfo;
import com.github.auties00.cobalt.model.info.MessageIndexInfoBuilder;
import com.github.auties00.cobalt.model.info.MessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.Message;
import com.github.auties00.cobalt.model.message.server.ProtocolMessage;
import com.github.auties00.cobalt.model.setting.EphemeralSettingsBuilder;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;
import it.auties.protobuf.stream.ProtobufInputStream;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.BitSet;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public final class MessageStreamNodeHandler extends SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger("MessageStreamNodeHandler");
    private static final int HISTORY_SYNC_MAX_TIMEOUT = 25;
    private static final Set<HistorySync.Type> REQUIRED_HISTORY_SYNC_TYPES = Set.of(HistorySync.Type.INITIAL_BOOTSTRAP, HistorySync.Type.PUSH_NAME, HistorySync.Type.NON_BLOCKING_DATA);

    private final LidMigrationService lidMigrationService;
    private final MessageService messageService;
    private final MessageReceiptHandler messageReceiptHandler;
    private final Set<Jid> historyCache;
    private final HistorySyncProgressTracker recentHistorySyncTracker;
    private final HistorySyncProgressTracker fullHistorySyncTracker;
    private final Set<HistorySync.Type> historySyncTypes;
    private CompletableFuture<Void> historySyncTask;

    public MessageStreamNodeHandler(
            WhatsAppClient whatsapp,
            LidMigrationService lidMigrationService,
            MessageService messageService,
            MessageReceiptHandler messageReceiptHandler
    ) {
        super(whatsapp, "message");
        this.lidMigrationService = lidMigrationService;
        this.historyCache = new HashSet<>();
        this.historySyncTypes = new HashSet<>();
        this.recentHistorySyncTracker = new HistorySyncProgressTracker();
        this.fullHistorySyncTracker = new HistorySyncProgressTracker();
        this.messageService = messageService;
        this.messageReceiptHandler = messageReceiptHandler;
    }

    @Override
    public void handle(Node node) {
        // Newsletter messages are handled by the unified service
        // (plaintext path) alongside E2E messages (decryption path)
        var fromJid = node.getRequiredAttributeAsJid("from");
        var isNewsletter = fromJid.hasNewsletterServer();

        // Parse the stanza for E2E messages (used for error handling receipts)
        // Newsletter messages don't need a parsed stanza for receipts
        MessageReceiveStanza parsedStanza = null;
        if (!isNewsletter) {
            var selfJid = whatsapp.store().jid().orElse(null);
            parsedStanza = MessageReceiveStanzaParser.parse(node, selfJid);
        }

        try {
            var info = messageService.process(node);

            if (info == null) {
                // Unavailable (fanout) message — send ack if E2E
                if (parsedStanza != null) {
                    messageReceiptHandler.sendAck(parsedStanza);
                }
                return;
            }

            // Handle protocol messages (history sync, app state, revoke, etc.)
            if (info instanceof ChatMessageInfo chatInfo) {
                var content = chatInfo.message().content();
                if (content instanceof ProtocolMessage pm) {
                    handleProtocolMessage(chatInfo, pm);
                }
            }

            saveMessage(info);
            updatePushName(parsedStanza);
            notifyListeners(info);

            // Send receipt for E2E messages
            if (parsedStanza != null) {
                if (messageReceiptHandler.isBotSender(parsedStanza)) {
                    // WAWebHandleMsgSendReceipt: bot messages get special acks
                    messageReceiptHandler.sendBotInvokeResponseAck(parsedStanza);
                } else if ("medianotify".equals(parsedStanza.stanzaType())) {
                    // WAWebHandleMsgSendReceipt: medianotify gets a plain ack
                    messageReceiptHandler.sendAck(parsedStanza);
                } else {
                    messageReceiptHandler.sendDeliveryReceipt(parsedStanza, info);
                }
            }

        } catch (WhatsAppMessageException.Receive.InvalidProtobuf e) {
            if (parsedStanza == null) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Parse error for newsletter message: {0}", e.getMessage());
                return;
            }
            LOGGER.log(System.Logger.Level.WARNING,
                    "Parse error for message {0}: {1}",
                    parsedStanza.id(), e.getMessage());
            messageReceiptHandler.sendNackReceipt(parsedStanza, 491);

        } catch (WhatsAppMessageException.Receive.HsmMismatch e) {
            if (parsedStanza == null) {
                return;
            }
            // HSM mismatch — no receipt sent per WAWebHandleMsgSendReceipt
            LOGGER.log(System.Logger.Level.WARNING,
                    "HSM mismatch for message {0}: {1}",
                    parsedStanza.id(), e.getMessage());

        } catch (WhatsAppMessageException.Receive.AdvFailure e) {
            if (parsedStanza == null) {
                return;
            }
            LOGGER.log(System.Logger.Level.WARNING,
                    "ADV validation failed for message {0}: {1}",
                    parsedStanza.id(), e.getMessage());
            messageReceiptHandler.sendRetryReceipt(
                    parsedStanza,
                    WhatsAppMessageException.Receive.RetryReason.ADV_FAILURE,
                    1
            );

        } catch (WhatsAppMessageException.Receive.DuplicateMessage e) {
            // WAWebHandleMsg: SIGNAL_OLD_COUNTER_ERROR gets a delivery receipt
            // (the message was already decrypted previously)
            if (parsedStanza != null) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Duplicate message {0}, sending delivery ack",
                        parsedStanza.id());
                messageReceiptHandler.sendAck(parsedStanza);
            }

        } catch (WhatsAppMessageException.Receive e) {
            if (parsedStanza == null) {
                return;
            }
            var retryReason = e.retryReason();
            if (retryReason.shouldSendRetryReceipt()) {
                var retryCount = parsedStanza.retryCount().orElse(0) + 1;
                LOGGER.log(System.Logger.Level.WARNING,
                        "Decryption failed for message {0} ({1}), sending retry receipt (count={2})",
                        parsedStanza.id(), retryReason, retryCount);
                messageReceiptHandler.sendRetryReceipt(
                        parsedStanza,
                        retryReason,
                        retryCount
                );
            } else {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Decryption issue for message {0} ({1}), not sending retry",
                        parsedStanza.id(), retryReason);
            }
        }
    }

    private void saveMessage(MessageInfo messageInfo) {
        if(messageInfo instanceof ChatMessageInfo chatMessageInfo && chatMessageInfo.parentJid().equals(Jid.statusBroadcastAccount())) {
            whatsapp.store().addStatus(chatMessageInfo);
        }else if (messageInfo instanceof ChatMessageInfo chatMessageInfo && !chatMessageInfo.message().hasCategory(Message.Category.SERVER)) {
            var chat = chatMessageInfo.chat()
                    .orElseGet(() -> whatsapp.store().addNewChat(chatMessageInfo.chatJid()));
            chat.addMessage(chatMessageInfo);
            if (chatMessageInfo.timestampSeconds().orElse(0L) > whatsapp.store().initializationTimeStamp()) {
                if (chat.archived() && whatsapp.store().unarchiveChats()) {
                    chat.setArchived(false);
                }
                chatMessageInfo.sender()
                        .filter(this::isTyping)
                        .ifPresent(sender -> {
                            var contact = whatsapp.store()
                                    .findContactByJid(sender);
                            if (contact.isPresent()) {
                                contact.get().setLastKnownPresence(ContactStatus.AVAILABLE);
                                contact.get().setLastSeen(Instant.now());
                            }

                            var provider = contact.orElse(sender);
                            chat.addPresence(sender, ContactStatus.AVAILABLE);
                            for (var listener : whatsapp.store().listeners()) {
                                Thread.startVirtualThread(() -> listener.onContactPresence(whatsapp, chatMessageInfo.chatJid(), provider.jid()));
                            }
                        });
                if (!chatMessageInfo.ignore() && !chatMessageInfo.fromMe()) {
                    chat.setUnreadMessagesCount(chat.unreadMessagesCount() + 1);
                }
            }
        }
    }

    /**
     * Updates the sender's push name from the stanza's {@code notify}
     * attribute when present.
     *
     * @param stanza the parsed stanza (nullable for newsletter messages)
     *
     * @apiNote WAWebHandlePushnameUpdate.updatePushname: called for every
     * incoming message with a non-empty notify attribute.
     */
    private void updatePushName(MessageReceiveStanza stanza) {
        if (stanza == null) {
            return;
        }

        stanza.pushName().ifPresent(pushName -> {
            var senderUserJid = stanza.senderJid().toUserJid();
            var contact = whatsapp.store()
                    .findContactByJid(senderUserJid)
                    .orElse(null);
            if (contact != null) {
                contact.setChosenName(pushName);
            }
        });
    }

    private boolean isTyping(Contact sender) {
        return sender.lastKnownPresence() == ContactStatus.COMPOSING
               || sender.lastKnownPresence() == ContactStatus.RECORDING;
    }

    private void notifyListeners(MessageInfo messageInfo) {
        if(messageInfo instanceof ChatMessageInfo chatInfo && chatInfo.chatJid().equals(Jid.statusBroadcastAccount())) {
            for (var listener : whatsapp.store().listeners()) {
                Thread.startVirtualThread(() -> listener.onNewStatus(whatsapp, chatInfo));
            }
        }else {
            for (var listener : whatsapp.store().listeners()) {
                Thread.startVirtualThread(() -> listener.onNewMessage(whatsapp, messageInfo));
            }

            var quotedMessageInfo = messageInfo.quotedMessage()
                    .orElse(null);
            if(quotedMessageInfo == null) {
                return;
            }

            for (var listener : whatsapp.store().listeners()) {
                Thread.startVirtualThread(() -> listener.onMessageReply(whatsapp, messageInfo, quotedMessageInfo));
            }
        }
    }

    private void handleProtocolMessage(ChatMessageInfo info, ProtocolMessage protocolMessage) {
        switch (protocolMessage.protocolType()) {
            case HISTORY_SYNC_NOTIFICATION -> onHistorySyncNotification(info, protocolMessage);

            case APP_STATE_SYNC_KEY_SHARE -> onAppStateSyncKeyShare(protocolMessage);

            case REVOKE -> onMessageRevoked(info, protocolMessage);

            case EPHEMERAL_SETTING -> onEphemeralSettings(info, protocolMessage);

            case EPHEMERAL_SYNC_RESPONSE -> {
                // TODO
            }

            case APP_STATE_SYNC_KEY_REQUEST -> {
                // TODO
            }

            case MSG_FANOUT_BACKFILL_REQUEST -> {
                // TODO
            }

            case INITIAL_SECURITY_NOTIFICATION_SETTING_SYNC -> {
                // TODO
            }

            case APP_STATE_FATAL_EXCEPTION_NOTIFICATION -> {
                // TODO
            }

            case SHARE_PHONE_NUMBER -> {
                // TODO
            }

            case MESSAGE_EDIT -> {
                // TODO
            }

            case PEER_DATA_OPERATION_REQUEST_MESSAGE -> {
                // TODO
            }

            case PEER_DATA_OPERATION_REQUEST_RESPONSE_MESSAGE -> {
                // TODO
            }

            case REQUEST_WELCOME_MESSAGE -> {
                // TODO
            }

            case BOT_FEEDBACK_MESSAGE -> {
                // TODO
            }

            case MEDIA_NOTIFY_MESSAGE -> {
                // TODO
            }

            case CLOUD_API_THREAD_CONTROL_NOTIFICATION -> {
                // TODO
            }

            case LID_MIGRATION_MAPPING_SYNC -> onLidMigrationMappingSync(protocolMessage);

            case REMINDER_MESSAGE -> {
                // TODO
            }

            case BOT_MEMU_ONBOARDING_MESSAGE -> {
                // TODO
            }

            case STATUS_MENTION_MESSAGE -> {
                // TODO
            }

            case STOP_GENERATION_MESSAGE -> {
                // TODO
            }

            case LIMIT_SHARING -> {
                // TODO
            }

            case AI_PSI_METADATA -> {
                // TODO
            }

            case AI_QUERY_FANOUT -> {
                // TODO
            }

            case GROUP_MEMBER_LABEL_CHANGE -> {
                // TODO
            }
        }
    }

    private void onLidMigrationMappingSync(ProtocolMessage protocolMessage) {
        var lidMigrationMappingSyncMessage = protocolMessage.lidMigrationMappingSyncMessage();
        if(lidMigrationMappingSyncMessage.isEmpty()) {
            whatsapp.handleFailure(new WhatsAppLidMigrationException.FailedToParseMappings("missing mapping sync message"));
            return;
        }

        var lidMigrationMappingPayload =  lidMigrationMappingSyncMessage.get()
                .encodedMappingPayload();
        if(lidMigrationMappingPayload.isEmpty()) {
            whatsapp.handleFailure(new WhatsAppLidMigrationException.FailedToParseMappings("missing encoded mapping payload"));
            return;
        }

        try(var stream = new GZIPInputStream(new ByteArrayInputStream(lidMigrationMappingPayload.get()))) {
            var lidMigrationMapping = LIDMigrationMappingSyncPayloadSpec.decode(ProtobufInputStream.fromStream(stream));
            lidMigrationService.processProtocolMessage(lidMigrationMapping);
        } catch (Throwable throwable) {
            whatsapp.handleFailure(new WhatsAppLidMigrationException.FailedToParseMappings("cannot parse protobuf message", throwable));
        }
    }

    private void onEphemeralSettings(ChatMessageInfo info, ProtocolMessage protocolMessage) {
        var chat = info.chat().orElse(null);
        var timestampSeconds = info.timestampSeconds().orElse(0L);
        if (chat != null) {
            chat.setEphemeralMessagesToggleTimeSeconds(timestampSeconds);
            chat.setEphemeralMessageDuration(ChatEphemeralTimer.of((int) protocolMessage.ephemeralExpirationSeconds()));
        }
        var setting = new EphemeralSettingsBuilder()
                .timestampSeconds((int) protocolMessage.ephemeralExpirationSeconds())
                .timestampSeconds(timestampSeconds)
                .build();
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onWebAppStateSetting(whatsapp, setting));
        }
    }

    private void onMessageRevoked(ChatMessageInfo info, ProtocolMessage protocolMessage) {
        var id = protocolMessage.key().orElseThrow().id();
        info.chat()
                .flatMap(chat -> whatsapp.store().findMessageById(chat, id))
                .ifPresent(message -> onMessageDeleted(info, message));
    }

    private void onAppStateSyncKeyShare(ProtocolMessage protocolMessage) {
        var data = protocolMessage.appStateSyncKeyShare()
                .orElseThrow(() -> new NoSuchElementException("Missing app state keys"));
        whatsapp.store()
                .addWebAppStateKeys(data.keys());
        whatsapp.pullWebAppState(PatchType.values());
    }

    private void onHistorySyncNotification(ChatMessageInfo info, ProtocolMessage protocolMessage) {
        scheduleHistorySyncTimeout();
        try {
            var historySync = downloadHistorySync(protocolMessage);
            onHistoryNotification(historySync);
        } catch (Throwable throwable) {
            whatsapp.handleFailure(new WhatsAppHistorySyncException(throwable));
        } finally {
            whatsapp.sendReceipt(info.id(), info.chatJid(), "hist_sync");
        }
    }

    private HistorySync downloadHistorySync(ProtocolMessage protocolMessage) {
        if (historySyncTypes.containsAll(REQUIRED_HISTORY_SYNC_TYPES) &&
             (whatsapp.store().webHistoryPolicy().isEmpty() || whatsapp.store().webHistoryPolicy().get().isZero())) {
            return null;
        }

        protocolMessage.historySyncNotification()
                .ifPresent(historySyncNotification -> historySyncTypes.add(historySyncNotification.syncType()));
        return protocolMessage.historySyncNotification()
                .map(this::downloadHistorySyncNotification)
                .orElse(null);
    }

    private HistorySync downloadHistorySyncNotification(HistorySyncNotification notification) {
        try {
            var initialPayload = notification.initialHistBootstrapInlinePayload();
            if (initialPayload.isPresent()) {
                var initialPayloadStream = new InflaterInputStream(new InputStream() {
                    @Override
                    public int read() {
                        return initialPayload.get().get() & 0xFF;
                    }

                    @Override
                    public int read(byte[] b, int off, int len) {
                        var length = Math.min(len, available());
                        initialPayload.get().get(b, off, length);
                        return length;
                    }

                    @Override
                    public int available() {
                        return initialPayload.get().remaining();
                    }
                });
                try(var mediaStream = ProtobufInputStream.fromStream(initialPayloadStream)) {
                    return HistorySyncSpec.decode(mediaStream);
                }
            }else {
                var mediaConnection = whatsapp.store()
                        .awaitMediaConnection();
                try(var mediaStream = ProtobufInputStream.fromStream(mediaConnection.download(notification))) {
                    return HistorySyncSpec.decode(mediaStream);
                }
            }
        } catch (Throwable throwable) {
            throw new WhatsAppMediaException.Download("Cannot download history sync", throwable);
        }
    }

    private void onHistoryNotification(HistorySync history) {
        if (history == null) {
            return;
        }

        handleHistorySync(history);
        if (history.progress() == null) {
            return;
        }

        var recent = history.syncType() == HistorySync.Type.RECENT;
        if (recent) {
            recentHistorySyncTracker.commit(history.chunkOrder(), history.progress() == 100);
            if (recentHistorySyncTracker.isDone()) {
                for (var listener : whatsapp.store().listeners()) {
                    Thread.startVirtualThread(() -> listener.onWebHistorySyncProgress(whatsapp, history.progress(), true));
                }
            }
        } else {
            fullHistorySyncTracker.commit(history.chunkOrder(), history.progress() == 100);
            if (fullHistorySyncTracker.isDone()) {
                for (var listener : whatsapp.store().listeners()) {
                    Thread.startVirtualThread(() -> listener.onWebHistorySyncProgress(whatsapp, history.progress(), false));
                }
            }
        }
    }

    private void onMessageDeleted(ChatMessageInfo info, ChatMessageInfo message) {
        info.chat().ifPresent(chat -> chat.removeMessage(message.id()));
        message.setRevokeTimestampSeconds(Instant.now().getEpochSecond());
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onMessageDeleted(whatsapp, message, true));
        }
    }

    private void handleHistorySync(HistorySync history) {
        // Process LID mappings from all history sync types that may contain them
        // This must happen before conversation processing to ensure mappings are available
        lidMigrationService.processHistorySync(history);

        switch (history.syncType()) {
            case INITIAL_STATUS_V3 -> handleInitialStatus(history);
            case PUSH_NAME -> handlePushNames(history);
            case INITIAL_BOOTSTRAP -> handleInitialBootstrap(history);
            case FULL -> handleChatsSync(history, false);
            case RECENT -> handleChatsSync(history, true);
            case NON_BLOCKING_DATA -> handleNonBlockingData(history);
            case ON_DEMAND -> {} // No specific handling needed
        }
    }

    private void handleInitialStatus(HistorySync history) {
        for (var messageInfo : history.statusV3Messages()) {
            whatsapp.store().addStatus(messageInfo);
        }
        whatsapp.store()
                .setSyncedStatus(true);
        var status = whatsapp.store()
                .status();
        for(var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onStatus(whatsapp, status));
        }
    }

    private void handlePushNames(HistorySync history) {
        for (var pushName : history.pushNames()) {
            handNewPushName(pushName);
        }
        whatsapp.store()
                .setSyncedContacts(true);
        var contacts = whatsapp.store()
                .contacts();
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onContacts(whatsapp, contacts));
        }
    }

    private void handNewPushName(PushName pushName) {
        var jid = Jid.of(pushName.id());
        var contact = whatsapp.store()
                .findContactByJid(jid)
                .orElseGet(() -> createNewContact(jid));
        pushName.name()
                .ifPresent(contact::setChosenName);
        var action = new ContactActionBuilder()
                .firstName(pushName.name().orElse(null))
                .build();
        var index = new MessageIndexInfoBuilder()
                .type("contact")
                .targetId(pushName.id())
                .fromMe(true)
                .build();
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onWebAppStateAction(whatsapp, action, index));
        }
    }

    private Contact createNewContact(Jid jid) {
        var contact = whatsapp.store().addNewContact(jid);
        for(var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onNewContact(whatsapp, contact));
        }
        return contact;
    }

    private void handleInitialBootstrap(HistorySync history) {
        var historyPolicy = whatsapp.store().webHistoryPolicy();
        if (historyPolicy.isEmpty() || !historyPolicy.get().isZero()) {
            var jids = history.conversations()
                    .stream()
                    .map(Chat::jid)
                    .toList();
            historyCache.addAll(jids);
        }

        handleConversations(history);
        whatsapp.store()
                .setSyncedChats(true);
        var chats = whatsapp.store().chats();
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onChats(whatsapp, chats));
        }
    }

    private void handleChatsSync(HistorySync history, boolean recent) {
        var historyPolicy = whatsapp.store().webHistoryPolicy();
        if (historyPolicy.isPresent() && historyPolicy.get().isZero()) {
            return;
        }

        handleConversations(history);
        handleConversationsNotifications(history, recent);
        scheduleHistorySyncTimeout();
    }

    private void handleConversationsNotifications(HistorySync history, boolean recent) {
        var toRemove = new HashSet<Jid>();
        for (var cachedJid : historyCache) {
            var chat = whatsapp.store()
                    .findChatByJid(cachedJid)
                    .orElse(null);
            if (chat == null) {
                continue;
            }

            var done = !recent && !history.conversations().contains(chat);
            if (done) {
                chat.setEndOfHistoryTransfer(true);
                chat.setEndOfHistoryTransferType(Chat.EndOfHistoryTransferType.COMPLETE_AND_NO_MORE_MESSAGE_REMAIN_ON_PRIMARY);
                toRemove.add(cachedJid);
            }

            for(var listener : whatsapp.store().listeners()) {
                Thread.startVirtualThread(() -> listener.onWebHistorySyncMessages(whatsapp, chat, done));
            }
        }

        historyCache.removeAll(toRemove);
    }

    private void scheduleHistorySyncTimeout() {
        if (historySyncTask != null && !historySyncTask.isDone()) {
            historySyncTask.cancel(true);
        }

        this.historySyncTask =  CompletableFuture.runAsync(this::onForcedHistorySyncCompletion,
                CompletableFuture.delayedExecutor(HISTORY_SYNC_MAX_TIMEOUT, TimeUnit.SECONDS));
    }

    private void onForcedHistorySyncCompletion() {
        for (var cachedJid : historyCache) {
            var chat = whatsapp.store()
                    .findChatByJid(cachedJid)
                    .orElse(null);
            if (chat == null) {
                continue;
            }

            for (var listener : whatsapp.store().listeners()) {
                Thread.startVirtualThread(() -> listener.onWebHistorySyncMessages(whatsapp, chat, true));
            }
        }

        historyCache.clear();
    }

    private void handleConversations(HistorySync history) {
        for (var chat : history.conversations()) {
            whatsapp.store().addChat(chat);
        }
    }

    private void handleNonBlockingData(HistorySync history) {
        handlePastParticipants(history);
        // LID mappings are processed at the top of handleHistorySync() via lidMigrationService.onHistorySyncReceived()
    }

    private void handlePastParticipants(HistorySync history) {
        for (var pastParticipants : history.pastParticipants()) {
            for (var listener : whatsapp.store().listeners()) {
                Thread.startVirtualThread(() -> listener.onWebHistorySyncPastParticipants(whatsapp, pastParticipants.groupJid(), pastParticipants.pastParticipants()));
            }
        }
    }

    @Override
    public void reset() {
        historyCache.clear();
        if (historySyncTask != null) {
            historySyncTask.cancel(true);
            historySyncTask = null;
        }
        recentHistorySyncTracker.clear();
        fullHistorySyncTracker.clear();
        historySyncTypes.clear();
    }

    private static final class HistorySyncProgressTracker {
        private final BitSet chunksMarker;
        private final AtomicInteger chunkEnd;

        private HistorySyncProgressTracker() {
            this.chunksMarker = new BitSet();
            this.chunkEnd = new AtomicInteger(0);
        }

        private boolean isDone() {
            var chunkEnd = this.chunkEnd.get();
            return chunkEnd > 0 && IntStream.range(0, chunkEnd)
                    .allMatch(chunksMarker::get);
        }

        private void commit(int chunk, boolean finished) {
            if (finished) {
                chunkEnd.set(chunk);
            }

            chunksMarker.set(chunk);
        }

        private void clear() {
            chunksMarker.clear();
            chunkEnd.set(0);
        }
    }
}
