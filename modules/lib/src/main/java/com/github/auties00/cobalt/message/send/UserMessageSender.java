package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.send.ack.AckParser;
import com.github.auties00.cobalt.message.send.ack.AckResult;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryptedPayload;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.send.stanza.*;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.profile.BusinessAutomatedType;
import com.github.auties00.cobalt.model.business.profile.BusinessProfile;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.ChatMessageContextInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.FutureProofMessageType;
import com.github.auties00.cobalt.model.message.Message;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.MessageThreadId;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.MdDeviceSyncAckEventBuilder;
import com.github.auties00.cobalt.wam.event.PrekeysDepletionEventBuilder;
import com.github.auties00.cobalt.wam.type.MessageChatType;
import com.github.auties00.cobalt.wam.type.MessageType;
import com.github.auties00.cobalt.wam.type.PrekeysFetchContext;
import com.github.auties00.cobalt.wam.type.WamSizeBuckets;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Sends messages to 1:1 user chats addressed by either PN or LID. The flow
 * resolves the device fanout list, encrypts the protobuf for each device,
 * builds the chat fanout stanza, dispatches it, and reacts to the server ack
 * by either triggering a LID refresh or resending to the delta devices when
 * the server reports a phash mismatch.
 */
@WhatsAppWebModule(moduleName = "WAWebSendUserMsgJob")
@WhatsAppWebModule(moduleName = "WAWebSendMsgToDeviceList")
@WhatsAppWebModule(moduleName = "WAWebSendMsgCreateFanoutStanza")
final class UserMessageSender extends MessageSender<ChatMessageInfo> {
    /**
     * Holds the logger used for user-send diagnostics.
     */
    private static final System.Logger LOGGER = System.getLogger(UserMessageSender.class.getName());

    /**
     * Holds the LID origin-type literal for phone-number-hiding click-to-WhatsApp
     * chats.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsernameTypes", exports = "LidOriginType",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final String LID_ORIGIN_TYPE_PNH_CTWA = "ctwa";

    /**
     * Holds the encryption service used for per-device Signal encryption.
     */
    private final MessageEncryption encryption;

    /**
     * Holds the device service used for fanout resolution and session management.
     */
    private final DeviceService deviceService;

    /**
     * Holds the bot-specific stanza builder.
     */
    private final BotStanza botStanza;

    /**
     * Holds the business-specific stanza builder used for payment native flows.
     */
    private final BizStanza bizStanza;

    /**
     * Holds the meta stanza builder.
     */
    private final MetaStanza metaStanza;

    /**
     * Holds the reporting stanza builder.
     */
    private final ReportingStanza reportingStanza;

    /**
     * Holds the CTWA attribution stanza builder.
     */
    private final CtwaAttributionStanza ctwaStanza;

    /**
     * Holds the trusted-contact-token stanza builder.
     */
    private final TcTokenStanza tcTokenStanza;

    /**
     * Constructs a user-chat sender bound to the given dependencies.
     *
     * @param client          the WhatsApp client used to dispatch stanzas
     * @param encryption      the message encryption service
     * @param deviceService   the device service
     * @param abPropsService  the AB-props service
     * @param botStanza       the bot stanza builder
     * @param bizStanza       the business stanza builder
     * @param metaStanza      the meta stanza builder
     * @param reportingStanza the reporting stanza builder
     * @param ctwaStanza      the CTWA attribution stanza builder
     * @param tcTokenStanza   the trusted-contact-token stanza builder
     * @param wamService      the WAM telemetry service shared with the base sender
     */
    @WhatsAppWebExport(moduleName = "WAWebSendUserMsgJob", exports = "encryptAndSendUserMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    UserMessageSender(
            WhatsAppClient client,
            MessageEncryption encryption,
            DeviceService deviceService,
            ABPropsService abPropsService,
            BotStanza botStanza,
            BizStanza bizStanza,
            MetaStanza metaStanza,
            ReportingStanza reportingStanza,
            CtwaAttributionStanza ctwaStanza,
            TcTokenStanza tcTokenStanza,
            WamService wamService
    ) {
        super(client, abPropsService, wamService);
        this.encryption = Objects.requireNonNull(encryption, "encryption");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService");
        this.botStanza = Objects.requireNonNull(botStanza, "botStanza");
        this.bizStanza = Objects.requireNonNull(bizStanza, "bizStanza");
        this.metaStanza = Objects.requireNonNull(metaStanza, "metaStanza");
        this.reportingStanza = Objects.requireNonNull(reportingStanza, "reportingStanza");
        this.ctwaStanza = Objects.requireNonNull(ctwaStanza, "ctwaStanza");
        this.tcTokenStanza = Objects.requireNonNull(tcTokenStanza, "tcTokenStanza");
    }

    /**
     * Sends the given message to a 1:1 user chat. Resolves the device fanout
     * list, dispatches the encrypted stanza, and reacts to the server ack by
     * triggering a LID refresh and/or a phash-mismatch resend when needed.
     *
     * @param chatJid     the target chat JID
     * @param messageInfo the outgoing message and its metadata
     * @return the server ack result
     */
    @WhatsAppWebExport(moduleName = "WAWebSendUserMsgJob", exports = "encryptAndSendUserMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    @Override
    AckResult send(Jid chatJid, ChatMessageInfo messageInfo) {
        waitForOfflineDelivery();

        var routedChatJid = maybeReplaceWidWithAccountLid(chatJid);

        var fanoutDevices = deviceService.getUserFanout(routedChatJid, null);

        store.createOrMergeReceiptRecords(messageInfo.key().id().orElseThrow(), fanoutDevices);

        var ack = encryptBuildAndSend(routedChatJid, messageInfo, fanoutDevices, false);

        maybeRefreshLid(routedChatJid, ack);

        ack.phash().ifPresent(serverPhash ->
                handlePhashMismatch(routedChatJid, messageInfo, fanoutDevices, serverPhash));

        return ack;
    }

    /**
     * Rewrites a bare regular-user PN to its associated LID for the wire
     * stanza when the Lid-1:1 migration is enabled and a mapping is known.
     *
     * <p>For self-send, the LID is resolved from the local store
     * ({@code store.lid()}); for other regular users the LID is taken from
     * the chat record's {@code accountLid} field, populated during chat
     * resolution. When no LID is known the original PN is returned and the
     * downstream pipeline routes the stanza by PN.
     *
     * @param chatJid the chat JID supplied by the caller
     * @return the rewritten LID, or the original PN when no rewrite applies
     */
    @WhatsAppWebExport(moduleName = "WAWebPnlessStanzaMigration",
            exports = "maybeReplaceWidWithAccountLid",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Jid maybeReplaceWidWithAccountLid(Jid chatJid) {
        if (chatJid == null
                || !chatJid.hasUserServer()
                || chatJid.device() != 0) {
            return chatJid;
        }

        var selfPn = store.jid().map(Jid::toUserJid).orElse(null);
        if (selfPn != null && chatJid.toUserJid().equals(selfPn)) {
            return store.lid().map(Jid::toUserJid).orElse(chatJid);
        }

        return store.findChatByJid(chatJid)
                .flatMap(Chat::accountLid)
                .orElse(chatJid);
    }

    /**
     * Triggers a device-list sync when the server requests a LID refresh on
     * the ack. Cobalt uses a device-list sync as a stand-in for WA Web's
     * dedicated contact-sync job since both pathways drive a USync request
     * that refreshes LID-to-PN mappings.
     *
     * @param chatJid the target chat JID
     * @param ack     the server ack result
     */
    @WhatsAppWebExport(moduleName = "WAWebSendUserMsgJob", exports = "maybeRefreshLid",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void maybeRefreshLid(Jid chatJid, AckResult ack) {
        if (!ack.refreshLid()) {
            return;
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "Server requested LID refresh for {0}", chatJid);

        var pnJid = chatJid.hasLidServer()
                ? store.findPhoneByLid(chatJid.toUserJid()).orElse(null)
                : null;
        if (pnJid == null) {
            return;
        }

        deviceService.getDeviceLists(List.of(pnJid), "message", null, false);
    }

    /**
     * Encrypts the message for the given devices, builds the chat fanout
     * stanza, dispatches it, and parses the server ack.
     *
     * @param chatJid     the target chat JID
     * @param messageInfo the outgoing message
     * @param devices     the device JIDs to encrypt for
     * @param isResend    {@code true} when this is a phash-mismatch resend
     * @return the parsed ack result
     * @throws WhatsAppMessageException.Send.Unknown if encryption fails for every
     *                                               device or the ack carries an error
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgToDeviceList", exports = "sendMsgToDeviceList",
            adaptation = WhatsAppAdaptation.DIRECT)
    private AckResult encryptBuildAndSend(
            Jid chatJid,
            ChatMessageInfo messageInfo,
            Collection<Jid> devices,
            boolean isResend
    ) {
        var container = messageInfo.message();
        var depletedPrekeyCount = deviceService.ensureSessions(devices);
        emitPrekeysDepletionEvents(depletedPrekeyCount, MessageType.INDIVIDUAL, null);
        var senderIcdc = deviceService.computeIcdc(requireSelfJid())
                .orElse(null);
        var recipientIcdc = deviceService.computeIcdc(chatJid)
                .orElse(null);
        var payloads = encryptForDevices(encryption, devices, container, chatJid, senderIcdc, recipientIcdc);
        if (payloads.isEmpty()) {
            throw new WhatsAppMessageException.Send.Unknown(
                    "Encryption failed for all devices targeting " + chatJid, null);
        }

        var stanza = buildStanza(chatJid, messageInfo, payloads, devices, isResend);
        flushStore();
        var ackNode = client.sendNode(stanza);
        var ack = AckParser.parse(ackNode);

        if (ack.error().isPresent()) {
            throw new WhatsAppMessageException.Send.Unknown(
                    "Invalid ack from server (error " + ack.error().getAsInt() + ") for " + chatJid, null);
        }

        return ack;
    }

    /**
     * Builds the complete chat fanout stanza, resolving every attribute and
     * child node.
     *
     * @param chatJid     the target chat JID
     * @param messageInfo the outgoing message
     * @param payloads    the per-device encrypted payloads
     * @param devices     the device JIDs used for identity-node resolution
     * @param isResend    {@code true} when this is a phash-mismatch resend
     * @return the {@code <message>} stanza builder
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    private NodeBuilder buildStanza(
            Jid chatJid,
            ChatMessageInfo messageInfo,
            List<MessageEncryptedPayload> payloads,
            Collection<Jid> devices,
            boolean isResend
    ) {
        var container = messageInfo.message();
        var anyPkmsg = ParticipantsStanza.requiresIdentityNode(payloads);
        var identityNode = anyPkmsg ? buildIdentityNode() : null;

        var metaNode = metaStanza.buildChat(chatJid, container, null);
        var bizNode = bizStanza.build(chatJid);
        var botNode = botStanza.build(messageInfo, chatJid);
        var reportingNode = reportingStanza.build(messageInfo, requireSelfJid(), chatJid);
        var senderContentBinding = SenderContentBindingStanza.buildForUser(messageInfo, requireSelfJid());
        var ctwaNode = ctwaStanza.build(chatJid);

        var botMetadataNode = resolveBotMetadataNode(chatJid, messageInfo);

        return ChatFanoutStanza.build(
                messageInfo.key().id().orElseThrow(),
                chatJid,
                resolveStanzaType(container),
                payloads,
                resolveEditAttribute(container),
                null,
                (isResend || anyPkmsg) ? "false" : null,
                resolveMediaType(container),
                resolveDecryptFail(container),
                resolveNativeFlowName(container),
                null,
                false,
                resolvePeerRecipientLid(chatJid),
                resolvePeerRecipientPn(chatJid),
                resolveRecipientPn(chatJid),
                resolvePeerRecipientUsername(chatJid),
                identityNode,
                metaNode,
                bizNode,
                botNode,
                reportingNode,
                senderContentBinding,
                botMetadataNode,
                tcTokenStanza.build(chatJid),
                ctwaNode,
                null
        );
    }

    /**
     * Resolves the {@code peer_recipient_lid} attribute for a PN-addressed chat.
     *
     * @param chatJid the target chat JID
     * @return the peer recipient LID, or {@code null} when not applicable
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Jid resolvePeerRecipientLid(Jid chatJid) {
        if (!chatJid.hasUserServer()) {
            return null;
        }

        return store.findChatByJid(chatJid)
                .flatMap(Chat::accountLid)
                .orElse(null);
    }

    /**
     * Resolves the {@code peer_recipient_pn} attribute for a LID-addressed chat.
     * Returns {@code null} for non-LID chats and for chats whose
     * {@code lidOriginType} matches {@code PNH_CTWA}.
     *
     * @param chatJid the target chat JID
     * @return the peer recipient PN, or {@code null} when not applicable
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Jid resolvePeerRecipientPn(Jid chatJid) {
        if (!chatJid.hasLidServer()) {
            return null;
        }

        var lidOriginType = store.findChatByJid(chatJid)
                .flatMap(Chat::lidOriginType)
                .orElse(null);
        if (LID_ORIGIN_TYPE_PNH_CTWA.equals(lidOriginType)) {
            return null;
        }

        return store.findPhoneByLid(chatJid.toUserJid()).orElse(null);
    }

    /**
     * Resolves the {@code recipient_pn} attribute for a LID-addressed chat.
     * The phone number is only included when the chat's {@code lidOriginType}
     * is absent or matches {@code PNH_CTWA}, the contact has not opted in to
     * share their phone number, and a mapping is available in the store.
     *
     * @param chatJid the target chat JID
     * @return the recipient PN, or {@code null} when not applicable
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Jid resolveRecipientPn(Jid chatJid) {
        if (!chatJid.hasLidServer()) {
            return null;
        }

        var chat = store.findChatByJid(chatJid).orElse(null);
        var lidOriginType = chat != null ? chat.lidOriginType().orElse(null) : null;
        if (lidOriginType != null && !LID_ORIGIN_TYPE_PNH_CTWA.equals(lidOriginType)) {
            return null;
        }

        var contact = store.findContactByJid(chatJid).orElse(null);
        if (contact != null && contact.isPhoneNumberShared()) {
            return null;
        }

        return store.findPhoneByLid(chatJid.toUserJid()).orElse(null);
    }

    /**
     * Resolves the {@code peer_recipient_username} attribute for a
     * LID-addressed chat when the username-display AB prop is enabled and
     * a username is available on the contact.
     *
     * @param chatJid the target chat JID
     * @return the peer recipient username, or {@code null} when not applicable
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    private String resolvePeerRecipientUsername(Jid chatJid) {
        if (!chatJid.hasLidServer()) {
            return null;
        }

        var usernameEnabled = abPropsService.getBool(ABProp.USERNAME_CONTACT_DISPLAY);
        if (!usernameEnabled) {
            return null;
        }

        return store.findContactByJid(chatJid)
                .flatMap(Contact::username)
                .orElse(null);
    }

    /**
     * Resolves the metadata-only {@code <bot>} node, derived from the message
     * content (bot message body type), the AI-thread id stored on the device
     * context info, and the business profile's automated type.
     *
     * @param chatJid     the target chat JID
     * @param messageInfo the outgoing message
     * @return the bot metadata node, or {@code null} when no metadata applies
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Node resolveBotMetadataNode(Jid chatJid, ChatMessageInfo messageInfo) {
        var container = messageInfo.message();
        var content = container.content();

        String botMsgBodyType = null;
        if (content instanceof ProtocolMessage pm
                && pm.type().orElse(null) == ProtocolMessage.Type.REQUEST_WELCOME_MESSAGE) {
            botMsgBodyType = "request_welcome";
        } else if (chatJid.hasBotServer() || container.futureProofContentType() == FutureProofMessageType.BOT_INVOKE) {
            botMsgBodyType = resolveBotMsgBodyType(chatJid, content);
        }

        var clientThreadId = container.messageContextInfo()
                .map(ChatMessageContextInfo::threadId)
                .stream()
                .flatMap(Collection::stream)
                .filter(thread -> thread.threadType().orElse(null) == MessageThreadId.ThreadType.AI_THREAD)
                .findFirst()
                .flatMap(MessageThreadId::threadKey)
                .flatMap(MessageKey::id)
                .filter(id -> !id.isEmpty())
                .orElse(null);

        var bizBotType = client.queryBusinessProfile(chatJid)
                .flatMap(BusinessProfile::automatedType)
                .map(BusinessAutomatedType::value)
                .orElse(null);

        return BotStanza.buildMetadata(botMsgBodyType, bizBotType, clientThreadId);
    }

    /**
     * Returns {@code "command"} when the message text matches a registered
     * command on the bot's profile, otherwise {@code "prompt"}.
     *
     * @param botJid  the bot JID
     * @param content the inner message content
     * @return the bot message body type
     */
    @WhatsAppWebExport(moduleName = "WAWebBotCommandFormatMutator", exports = "formatBotCommand",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private String resolveBotMsgBodyType(Jid botJid, Message content) {
        if (!(content instanceof ExtendedTextMessage textMessage) || textMessage.text().isEmpty()) {
            return "prompt";
        } else {
            var isCommand = client.queryBotProfile(botJid)
                    .map(profile -> profile.isCommand(textMessage.text().get()))
                    .orElse(false);
            return isCommand ? "command" : "prompt";
        }
    }

    /**
     * Handles a phash mismatch by emitting the {@code MdDeviceSyncAck} WAM
     * event, refreshing the device fanout, and resending the message to the
     * delta devices using the resend stanza shape.
     *
     * @param chatJid         the target chat JID
     * @param messageInfo     the outgoing message
     * @param originalDevices the device list used for the original send
     * @param serverPhash     the phash returned by the server
     */
    @WhatsAppWebExport(moduleName = "WAWebResendUserMsg", exports = "resendUserMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebPostMdDeviceSyncAckMetric",
            exports = "postMdDeviceSyncAckMetric", adaptation = WhatsAppAdaptation.DIRECT)
    private void handlePhashMismatch(
            Jid chatJid,
            ChatMessageInfo messageInfo,
            Collection<Jid> originalDevices,
            String serverPhash
    ) {
        LOGGER.log(System.Logger.Level.DEBUG,
                "Phash mismatch for {0}, server phash: {1}", chatJid, serverPhash);

        wamService.commit(new MdDeviceSyncAckEventBuilder()
                .revoke(isRevokeMessage(messageInfo))
                .chatType(chatTypeFromJid(chatJid))
                .isLid(chatJid.hasLidServer())
                .build());

        var refreshedDevices = deviceService.getUserFanout(chatJid, serverPhash);

        var originalJids = originalDevices.stream()
                .map(Jid::toString)
                .collect(Collectors.toUnmodifiableSet());
        var deltaDevices = refreshedDevices.stream()
                .filter(device -> !originalJids.contains(device.toString()))
                .toList();

        if (deltaDevices.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "No new devices after phash resync for {0}", chatJid);
            return;
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "Resending to {0} new devices for {1}",
                deltaDevices.size(), chatJid);

        encryptBuildAndSend(chatJid, messageInfo, deltaDevices, true);
    }

    /**
     * Returns the WAM {@link MessageChatType} for the given chat JID,
     * matching the predicate order of the WA Web cascade. The {@code null}
     * input is a defensive Cobalt adaptation that maps to
     * {@link MessageChatType#OTHER}.
     *
     * @param jid the chat JID to classify, or {@code null}
     * @return the matching {@link MessageChatType}; never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebGetMessageChatTypeFromWid",
            exports = "getMessageChatTypeFromWid",
            adaptation = WhatsAppAdaptation.DIRECT)
    static MessageChatType chatTypeFromJid(Jid jid) {
        if (jid == null) {
            return MessageChatType.OTHER;
        }
        if (jid.hasUserServer()
                || jid.hasLidServer()
                || jid.hasBotServer()
                || jid.hasHostedServer()
                || jid.hasHostedLidServer()) {
            return MessageChatType.INDIVIDUAL;
        }
        if (jid.hasGroupOrCommunityServer()) {
            return MessageChatType.GROUP;
        }
        if (jid.hasBroadcastServer()) {
            return MessageChatType.BROADCAST;
        }
        if (jid.isStatusBroadcastAccount()) {
            return MessageChatType.STATUS;
        }
        if (jid.hasNewsletterServer()) {
            return MessageChatType.CHANNEL;
        }
        return MessageChatType.OTHER;
    }

    /**
     * Returns whether the given message wraps a {@code protocolMessage} of
     * type {@link ProtocolMessage.Type#REVOKE}.
     *
     * @param messageInfo the outgoing message, possibly {@code null}
     * @return {@code true} when the payload is a revoke protocol message
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "isRevokeMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    static boolean isRevokeMessage(ChatMessageInfo messageInfo) {
        if (messageInfo == null) {
            return false;
        }
        return messageInfo.message().content() instanceof ProtocolMessage protocol
                && protocol.type().orElse(null) == ProtocolMessage.Type.REVOKE;
    }

    /**
     * Commits one {@link com.github.auties00.cobalt.wam.event.PrekeysDepletionEvent}
     * per depleted one-time pre-key reported by the last
     * {@code ensureSessions} call. No-op when {@code depletedPrekeyCount} is
     * not positive.
     *
     * @param depletedPrekeyCount the number of depleted one-time pre-keys
     * @param messageType         the WAM message type for this send
     * @param deviceCount         the fanout device count used for the
     *                            {@code deviceSizeBucket} classification, or
     *                            {@code null} to omit the bucket
     */
    @WhatsAppWebExport(moduleName = "WAWebPostPrekeysDepletionMetric",
            exports = "maybePostPrekeysDepletionMetric",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitPrekeysDepletionEvents(int depletedPrekeyCount, MessageType messageType, Integer deviceCount) {
        if (depletedPrekeyCount <= 0) {
            return;
        }
        var bucket = deviceCount == null ? null : WamSizeBuckets.numberToSizeBucket(deviceCount);
        for (var i = 0; i < depletedPrekeyCount; i++) {
            wamService.commit(new PrekeysDepletionEventBuilder()
                    .prekeysFetchReason(PrekeysFetchContext.SEND_MESSAGE)
                    .messageType(messageType)
                    .deviceSizeBucket(bucket)
                    .build());
        }
    }
}
