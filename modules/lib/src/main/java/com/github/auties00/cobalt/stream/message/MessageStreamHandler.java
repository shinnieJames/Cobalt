package com.github.auties00.cobalt.stream.message;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.message.receive.receipt.MessageReceiptHandler;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveStanza;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveStanzaParser;
import com.github.auties00.cobalt.message.send.id.MessageIdGenerator;
import com.github.auties00.cobalt.message.send.id.MessageIdVersion;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.migration.LIDMigrationMappingSyncPayload;
import com.github.auties00.cobalt.model.jid.migration.LIDMigrationMappingSyncPayloadSpec;
import com.github.auties00.cobalt.model.message.*;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessageBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.*;
import com.github.auties00.cobalt.model.message.system.peer.PeerDataOperationRequestResponseMessage;
import com.github.auties00.cobalt.model.message.system.peer.PeerDataOperationRequestType;
import com.github.auties00.cobalt.model.message.text.CommentMessage;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.payment.OrphanPaymentNotificationBuilder;
import com.github.auties00.cobalt.model.payment.PaymentInfo;
import com.github.auties00.cobalt.model.payment.PaymentInfoBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdSnapshotRecovery;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.sync.SnapshotRecoveryService;
import com.github.auties00.cobalt.sync.WebAppStateService;
import com.github.auties00.cobalt.sync.WebHistorySyncService;
import com.github.auties00.cobalt.sync.key.SyncKeyRotationService;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.IncomingMessageDropEventBuilder;
import com.github.auties00.cobalt.wam.event.MdBadDeviceSentMessageEventBuilder;
import com.github.auties00.cobalt.wam.event.MessageHighRetryCountEventBuilder;
import com.github.auties00.cobalt.wam.event.MessageReceiveEventBuilder;
import com.github.auties00.cobalt.wam.event.NonMessagePeerDataOperationResponseEventBuilder;
import com.github.auties00.cobalt.wam.event.OfflineCountTooHighEventBuilder;
import com.github.auties00.cobalt.wam.event.StructuredMessageReceiveEventBuilder;
import com.github.auties00.cobalt.wam.event.UnknownStanzaEventBuilder;
import com.github.auties00.cobalt.wam.type.BizPlatform;
import com.github.auties00.cobalt.wam.type.StructuredMessageClass;
import com.github.auties00.cobalt.wam.type.PeerDataRequestType;
import com.github.auties00.cobalt.wam.type.AddressingMode;
import com.github.auties00.cobalt.wam.type.BotType;
import com.github.auties00.cobalt.wam.type.ChatOriginsType;
import com.github.auties00.cobalt.wam.type.DeviceType;
import com.github.auties00.cobalt.wam.type.DisappearingChatInitiatorType;
import com.github.auties00.cobalt.wam.type.DsmError;
import com.github.auties00.cobalt.wam.type.E2eCiphertextType;
import com.github.auties00.cobalt.wam.type.E2eDestination;
import com.github.auties00.cobalt.wam.type.EditType;
import com.github.auties00.cobalt.wam.type.EncryptionTypeCode;
import com.github.auties00.cobalt.wam.type.EphemeralityInitiatorType;
import com.github.auties00.cobalt.wam.type.EphemeralityTriggerActionType;
import com.github.auties00.cobalt.wam.type.MediaType;
import com.github.auties00.cobalt.wam.type.MessageDropReasonType;
import com.github.auties00.cobalt.wam.type.RevokeType;
import com.github.auties00.cobalt.wam.type.StanzaType;
import com.github.auties00.cobalt.wam.type.TypeOfGroupEnum;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.chat.ChatDisappearingMode;
import it.auties.protobuf.stream.ProtobufInputStream;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * Handles incoming {@code <message>} stanzas from the WhatsApp server.
 *
 * <p>This is the top-level entry point for all incoming message stanzas.
 * It routes messages through three paths:
 * <ul>
 *   <li><b>Media notify:</b> stanzas with {@code type="medianotify"} are
 *       acknowledged immediately without parsing or decryption.</li>
 *   <li><b>Newsletter:</b> stanzas from newsletter JIDs are routed to
 *       a separate processing path that does not send receipts.</li>
 *   <li><b>Normal messages:</b> parsed, decrypted via {@link MessageService},
 *       then receipts (delivery, retry, nack, or bot ack) are sent based
 *       on the processing result.</li>
 * </ul>
 *
 * <p>After successful processing, the handler stores the message, handles
 * protocol messages (key shares, key requests, snapshot recovery, LID
 * migration, history sync), resolves orphan payment notifications, and
 * notifies registered listeners.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleMsg")
@WhatsAppWebModule(moduleName = "WAWebCommsHandleMessagingStanza")
@WhatsAppWebModule(moduleName = "WAWebCommsHandleWorkerCompatibleStanza")
public final class MessageStreamHandler implements SocketStream.Handler {
    /**
     * Logger for this handler.
     */
    private static final System.Logger LOGGER = System.getLogger(MessageStreamHandler.class.getName());

    /**
     * Minimum retry count (inclusive) that causes the
     * {@code MessageHighRetryCount} WAM metric to be emitted for the
     * current retry receipt.
     *
     * <p>Mirrors the private module-level constant {@code e = 5} that
     * {@code WAWebPostMessageHighRetryCountMetric} exports as
     * {@code MAX_RETRY}: the metric posts only when
     * {@code retryCount >= MAX_RETRY}.
     */
    @WhatsAppWebExport(moduleName = "WAWebPostMessageHighRetryCountMetric",
            exports = "MAX_RETRY", adaptation = WhatsAppAdaptation.DIRECT)
    private static final int MAX_MESSAGE_RETRY_COUNT = 5;

    /**
     * Minimum {@code offline} attribute value (inclusive) that causes the
     * {@code OfflineCountTooHigh} WAM metric to be emitted for the current
     * incoming message stanza.
     *
     * <p>Mirrors the private module-level constant {@code s = 11} that
     * {@code WAWebMaybePostOfflineCountTooHighMetric} exports as
     * {@code OFFLINE_COUNT_TOO_HIGH_THRESHOLD}: the metric posts only when
     * {@code offlineCount >= OFFLINE_COUNT_TOO_HIGH_THRESHOLD}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMaybePostOfflineCountTooHighMetric",
            exports = "OFFLINE_COUNT_TOO_HIGH_THRESHOLD", adaptation = WhatsAppAdaptation.DIRECT)
    private static final int OFFLINE_COUNT_TOO_HIGH_THRESHOLD = 11;

    /**
     * The WhatsApp client used for sending stanzas, accessing the store,
     * and notifying listeners.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The message service that coordinates parsing, decryption, and
     * processing of incoming message payloads.
     */
    private final MessageService messageService;

    /**
     * The receipt handler that sends delivery, retry, nack, and bot ack
     * receipts after message processing.
     */
    private final MessageReceiptHandler receiptHandler;

    /**
     * The snapshot recovery service for handling syncd snapshot fatal
     * recovery responses in peer data operation messages.
     */
    private final SnapshotRecoveryService snapshotRecoveryService;

    /**
     * The sync key rotation service for handling app state sync key
     * shares and requests in protocol messages.
     */
    private final SyncKeyRotationService syncKeyRotationService;

    /**
     * The LID migration service for processing LID migration mapping
     * sync payloads in protocol messages.
     */
    private final LidMigrationService lidMigrationService;

    /**
     * The history sync service that downloads, decrypts and decodes
     * {@link com.github.auties00.cobalt.model.message.system.history.HistorySyncNotification}
     * payloads carried by protocol messages and fans the decoded
     * {@link com.github.auties00.cobalt.model.sync.history.HistorySync}
     * chunks out to the registered listeners.
     */
    private final WebHistorySyncService webHistorySyncService;

    /**
     * The WAM telemetry service used to commit incoming-message-related events.
     */
    private final WamService wamService;

    /**
     * Constructs a new message stream handler with the specified
     * dependencies.
     *
     * @param whatsapp                the WhatsApp client
     * @param messageService          the message processing service
     * @param snapshotRecoveryService the snapshot recovery service
     * @param webAppStateService      the web app state service (provides
     *                                the sync key rotation service)
     * @param lidMigrationService     the LID migration service
     * @param wamService              the WAM telemetry service for committing inbound message events
     */
    public MessageStreamHandler(
            WhatsAppClient whatsapp,
            MessageService messageService,
            SnapshotRecoveryService snapshotRecoveryService,
            WebAppStateService webAppStateService,
            LidMigrationService lidMigrationService,
            WamService wamService
    ) {
        this.whatsapp = whatsapp;
        this.messageService = Objects.requireNonNull(messageService, "messageService cannot be null");
        this.receiptHandler = new MessageReceiptHandler(whatsapp);
        this.snapshotRecoveryService = Objects.requireNonNull(snapshotRecoveryService, "snapshotRecoveryService cannot be null");
        this.syncKeyRotationService = Objects.requireNonNull(webAppStateService, "webAppStateService cannot be null").syncKeyRotationService();
        this.lidMigrationService = Objects.requireNonNull(lidMigrationService, "lidMigrationService cannot be null");
        this.wamService = Objects.requireNonNull(wamService, "wamService cannot be null");
        this.webHistorySyncService = new WebHistorySyncService(whatsapp, lidMigrationService, wamService);
    }

    /**
     * Handles an incoming {@code <message>} stanza.
     *
     * <p>Dispatches the stanza through the appropriate processing path
     * based on the message type and sender, then sends the appropriate
     * receipt (delivery, retry, nack, or bot ack).
     *
     * @param node the raw {@code <message>} stanza node
     */
    @Override
    public void handle(Node node) {
        // type with SUCCESS result, sends ack instead of delivery receipt.
        // Cobalt short-circuits here since medianotify stanzas don't carry
        // actual message content requiring decryption.
        if ("medianotify".equals(node.getAttributeAsString("type", null))) {
            sendAck(node);
            return;
        }

        var from = node.getAttributeAsJid("from").orElse(null);
        if (from == null) {
            // from is null, meaning no response is sent to the server
            return;
        }

        if (from.hasNewsletterServer()) {
            handleNewsletterMessage(node);
            return;
        }

        MessageReceiveStanza stanza;
        try {
            stanza = MessageReceiveStanzaParser.parse(
                    node,
                    whatsapp.store().jid().orElse(null),
                    whatsapp.store().lid().orElse(null));
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to parse incoming message stanza: {0}",
                    exception.getMessage());
            // IncomingMessageDrop + NACK path. Emits UnknownStanza (id 3448)
            // with only the stanza tag and type populated; WA Web leaves
            // unknownStanzaDropReason unset.
            emitUnknownStanzaMetric(node);
            // on a parse failure WA Web calls
            // postIncomingMessageDropMetric.postIncomingMessageDropInvalidStanza(t) and then
            // createNackFromStanza with NackReason.ParsingError.
            emitIncomingMessageDropFromNode(node, MessageDropReasonType.INVALID_STANZA);
            sendNack(node, "487");
            return;
        }

        // This is the first thing WA Web invokes after the parser returns a successful stanza, before
        // any decryption or routing. Cobalt mirrors the ordering so the offline-count metric fires on
        // every successfully-parsed incoming message stanza.
        maybePostOfflineCountTooHigh(stanza);

        try {
            var info = messageService.process(node);
            if (info != null) {
                storeIncomingMessage(info);
                if (info instanceof ChatMessageInfo chatInfo) {
                    handleProtocolMessage(chatInfo);
                    // emits MessageReceiveWamEvent (id 450) for every successfully processed E2E message.
                    emitMessageReceiveForChatMessage(chatInfo, stanza);
                    // call WAWebGalaxyFlowWamLoggerUtils.logStructuredMessageReceivedWAMEvent and
                    // commit StructuredMessageReceiveWamEvent (id 3222) when the message is a CTA_FLOW
                    // or PAYMENT_REQUEST native-flow interactive message.
                    emitStructuredMessageReceiveIfApplicable(stanza);
                }
                resolveOrphanPayment(info);
                var quoted = whatsapp.store().findQuotedMessage(info);
                notifyMessageReceived(info, quoted);
            }

            if (!whatsapp.store().automaticMessageReceipts()) {
                return;
            }

            if (info == null) {
                if (receiptHandler.isBotSender(stanza)) {
                    receiptHandler.sendBotInvokeResponseAck(stanza);
                } else {
                    receiptHandler.sendAck(stanza);
                }
                return;
            }

            if (receiptHandler.isBotSender(stanza)) {
                receiptHandler.sendBotInvokeResponseAck(stanza);
            } else {
                receiptHandler.sendDeliveryReceipt(stanza, info);
            }
        } catch (WhatsAppMessageException.Receive exception) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Incoming message {0} failed: {1}",
                    stanza.id(),
                    exception.getMessage());
            // an error-specific drop reason on every failed decrypt slot.
            emitIncomingMessageDropFromStanza(stanza, exception);
            // MdBadDeviceSentMessageWamEvent({peerType, dsmError}). Cobalt raises the equivalent
            // InvalidDeviceSentMessage from ChatMessageReceiver and emits the event here.
            emitMdBadDeviceSentMessageIfApplicable(stanza, exception);
            handleReceiveFailure(stanza, exception);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Incoming message {0} failed unexpectedly: {1}",
                    stanza.id(),
                    exception.getMessage());
            // posts postIncomingMessageDropInternalError(t) before nacking with
            // NackReason.UnhandledError.
            emitIncomingMessageDropFromStanza(stanza, null);
            sendNack(node, "500");
        }
    }

    /**
     * Handles a newsletter message stanza separately from normal E2E
     * messages.
     *
     * <p>Newsletter messages are not end-to-end encrypted and do not
     * require receipts.  They are processed, stored, and listeners
     * are notified.
     *
     * @param node the raw newsletter message stanza
     */
    private void handleNewsletterMessage(Node node) {
        try {
            var info = messageService.process(node);
            if (info == null) {
                return;
            }

            storeIncomingMessage(info);
            resolveOrphanPayment(info);
            var quoted = whatsapp.store().findQuotedMessage(info);
            notifyMessageReceived(info, quoted);
            // emits MessageReceiveWamEvent (id 450) for every successfully processed newsletter message.
            if (info instanceof NewsletterMessageInfo newsletterInfo) {
                emitMessageReceiveForNewsletterMessage(newsletterInfo);
            }
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle newsletter message stanza: {0}",
                    exception.getMessage());
            // IncomingMessageDropWamEvent with messageDropReason = INVALID_PROTOBUF and
            // e2eDestination = CHANNEL whenever a newsletter message fails MessageValidationError.
            wamService.commit(new IncomingMessageDropEventBuilder()
                    .messageDropReason(MessageDropReasonType.INVALID_PROTOBUF)
                    .e2eDestination(E2eDestination.CHANNEL)
                    .build());
        }
    }

    /**
     * Handles a message decryption failure by sending the appropriate
     * receipt based on the exception type.
     *
     * <p>The receipt type is determined by the exception:
     * <ul>
     *   <li>{@link WhatsAppMessageException.Receive.HsmMismatch} ->
     *       no receipt sent (WA Web silently drops)</li>
     *   <li>Exceptions with an error code -> NACK receipt</li>
     *   <li>Retryable exceptions -> retry receipt</li>
     *   <li>All other exceptions -> delivery receipt (or bot ack)</li>
     * </ul>
     *
     * @param stanza    the parsed incoming stanza
     * @param exception the decryption exception
     */
    private void handleReceiveFailure(
            MessageReceiveStanza stanza,
            WhatsAppMessageException.Receive exception
    ) {
        if (!whatsapp.store().automaticMessageReceipts()) {
            return;
        }

        if (exception instanceof WhatsAppMessageException.Receive.HsmMismatch) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "HSM mismatch for message {0}, no receipt sent",
                    stanza.id());
            return;
        }

        var errorCode = exception.errorCode().orElse(null);
        if (errorCode != null) {
            receiptHandler.sendNackReceipt(stanza, parseErrorCode(errorCode));
            return;
        }

        if (exception.shouldSendRetryReceipt()) {
            var nextRetryCount = stanza.retryCount().orElse(0) + 1;
            receiptHandler.sendRetryReceipt(
                    stanza,
                    exception.retryReason(),
                    nextRetryCount
            );
            // handler calls WAWebPostMessageHighRetryCountMetric.maybePostMessageHighRetryCountMetric.
            maybeEmitMessageHighRetryCount(stanza, nextRetryCount);
            return;
        }

        if (receiptHandler.isBotSender(stanza)) {
            receiptHandler.sendBotInvokeResponseAck(stanza);
        } else {
            receiptHandler.sendDeliveryReceipt(stanza, null);
        }
    }

    /**
     * Emits a {@code MessageHighRetryCountEvent} (id 3132) when the
     * current retry attempt has reached the
     * {@link #MAX_MESSAGE_RETRY_COUNT} threshold.
     *
     * <p>Populates the properties that WA Web's
     * {@code WAWebPostMessageHighRetryCountMetric} is able to derive from
     * the incoming stanza alone:
     * <ul>
     *   <li>{@code retryCount}: the new retry attempt number.</li>
     *   <li>{@code messageType}: normalised from the parser-level
     *       stanza type via
     *       {@link WamService#getWamMessageTypeFromStanzaType(
     *       com.github.auties00.cobalt.message.receive.stanza.MessageType)}.</li>
     *   <li>{@code e2eSenderType}: classification of the sender JID
     *       relative to the current account, computed by
     *       {@link WamService#getWamE2eSenderType(Jid, Jid)}.</li>
     *   <li>{@code encryptionType}: forced to
     *       {@link EncryptionTypeCode#COEX} when the sender JID is on a
     *       hosted server, mirroring WA Web's
     *       {@code n.author.isHosted() -> ENCRYPTION_TYPE_CODE.COEX}
     *       branch.</li>
     * </ul>
     *
     * <p>The {@code deviceSizeBucket} property (groups only) is left
     * absent because Cobalt has no equivalent of
     * {@code WAWebWamGroupMetricCache.getGroupMetrics}; WA Web also
     * omits the property when the cached metric is unavailable.
     *
     * @param stanza     the parsed incoming stanza
     * @param retryCount the new retry attempt number
     */
    @WhatsAppWebExport(moduleName = "WAWebPostMessageHighRetryCountMetric",
            exports = "maybePostMessageHighRetryCountMetric",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void maybeEmitMessageHighRetryCount(MessageReceiveStanza stanza, int retryCount) {
        if (retryCount < MAX_MESSAGE_RETRY_COUNT) {
            return;
        }

        var builder = new MessageHighRetryCountEventBuilder()
                .retryCount(retryCount)
                .messageType(wamService.getWamMessageTypeFromStanzaType(stanza.messageType()));

        var selfJid = whatsapp.store().jid().orElse(null);
        var senderType = wamService.getWamE2eSenderType(stanza.senderJid(), selfJid);
        if (senderType != null) {
            builder.e2eSenderType(senderType);
        }

        if (stanza.senderJid().hasHostedServer() || stanza.senderJid().hasHostedLidServer()) {
            builder.encryptionType(EncryptionTypeCode.COEX);
        }

        // skips the field when the cached metric is absent, so omission is parity-preserving.

        wamService.commit(builder.build());
    }

    /**
     * Emits an {@link com.github.auties00.cobalt.wam.event.OfflineCountTooHighEvent}
     * (id 2638) when a successfully-parsed incoming message stanza carries
     * an {@code offline} attribute at or above
     * {@link #OFFLINE_COUNT_TOO_HIGH_THRESHOLD}.
     *
     * <p>Mirrors {@code WAWebMaybePostOfflineCountTooHighMetric.maybePostOfflineCountTooHigh}:
     * the event is fired for every parsed stanza whose
     * {@code parseInt(msgInfo.offline, 10)} is a valid integer greater
     * than or equal to the threshold.  The emission populates:
     * <ul>
     *   <li>{@code offlineCount}: the parsed {@code offline} attribute.</li>
     *   <li>{@code stanzaType}: always {@link StanzaType#MESSAGE} because
     *       the helper is only reachable from the message handler.</li>
     *   <li>{@code mediaType}: derived from the first enc's
     *       {@code mediatype}, the stanza type (for {@code reaction} and
     *       {@code medianotify}), and the poll type (for {@code creation}
     *       and {@code vote}) via
     *       {@link #mapEncMediaTypeToWamMediaType(String, String, String)}.</li>
     *   <li>{@code messageType}: from
     *       {@link WamService#getWamMessageTypeFromStanzaType(com.github.auties00.cobalt.message.receive.stanza.MessageType)}
     *       when non-null (mirroring WA Web's null-guarded assignment).</li>
     *   <li>{@code e2eSenderType}: from
     *       {@link WamService#getWamE2eSenderType(Jid, Jid)} when
     *       non-null.</li>
     *   <li>{@code encryptionType}: {@link EncryptionTypeCode#COEX} when
     *       the sender JID is on a hosted server.</li>
     * </ul>
     *
     * <p>The other spec properties ({@code callStanzaType},
     * {@code invisibleMessageCategory}, {@code notificationStanzaType},
     * {@code receiptStanzaType}) are intentionally omitted because the
     * WA Web emission site does not populate them either: they only apply
     * to call/notification/receipt stanzas that take different code paths.
     *
     * @param stanza the parsed incoming message stanza
     */
    @WhatsAppWebExport(moduleName = "WAWebMaybePostOfflineCountTooHighMetric",
            exports = "maybePostOfflineCountTooHigh",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void maybePostOfflineCountTooHigh(MessageReceiveStanza stanza) {
        var rawOffline = stanza.offline().orElse(null);
        if (rawOffline == null) {
            return;
        }
        int offlineCount;
        try {
            offlineCount = Integer.parseInt(rawOffline);
        } catch (NumberFormatException _) {
            return;
        }
        if (offlineCount < OFFLINE_COUNT_TOO_HIGH_THRESHOLD) {
            return;
        }

        //   offlineCount: l,
        //   stanzaType: STANZA_TYPE.MESSAGE,
        //   mediaType: getMetricMediaType({ encMediaType, msgType: i.type, msgPollType: i.pollType })
        // })
        var builder = new OfflineCountTooHighEventBuilder()
                .offlineCount(offlineCount)
                .stanzaType(StanzaType.MESSAGE);

        var encMediaType = stanza.encs().stream()
                .map(enc -> enc.encMediaType().orElse(null))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        var mediaType = mapEncMediaTypeToWamMediaType(
                encMediaType, stanza.stanzaType(), stanza.pollType().orElse(null));
        if (mediaType != null) {
            builder.mediaType(mediaType);
        }

        var messageType = wamService.getWamMessageTypeFromStanzaType(stanza.messageType());
        if (messageType != null) {
            builder.messageType(messageType);
        }

        var selfJid = whatsapp.store().jid().orElse(null);
        var senderType = wamService.getWamE2eSenderType(stanza.senderJid(), selfJid);
        if (senderType != null) {
            builder.e2eSenderType(senderType);
        }

        if (stanza.senderJid().hasHostedServer() || stanza.senderJid().hasHostedLidServer()) {
            builder.encryptionType(EncryptionTypeCode.COEX);
        }

        wamService.commit(builder.build());
    }

    /**
     * Maps the triple {@code (encMediaType, stanzaType, pollType)} to
     * the matching {@link MediaType} enum value, mirroring
     * {@code WAWebBackendJobsCommon.getMetricMediaType}.
     *
     * <p>The lookup order follows WA Web exactly: reaction and
     * medianotify stanza types win over everything; poll creation/vote
     * override the enc media type; otherwise the enc media type drives
     * the result.  When none of the inputs match, {@link MediaType#NONE}
     * is returned (matching WA Web's default branch).
     *
     * @param encMediaType the first non-null {@code mediatype} attribute
     *                     among the stanza's enc payloads, or
     *                     {@code null} when none carry one
     * @param stanzaType   the stanza's top-level {@code type} attribute,
     *                     or {@code null}
     * @param pollType     the {@code polltype} attribute from the
     *                     {@code <meta>} node, or {@code null}
     * @return the corresponding {@link MediaType} enum value
     */
    @WhatsAppWebExport(moduleName = "WAWebBackendJobsCommon", exports = "getMetricMediaType",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static MediaType mapEncMediaTypeToWamMediaType(
            String encMediaType,
            String stanzaType,
            String pollType
    ) {
        if ("reaction".equals(stanzaType)) {
            return MediaType.REACTION;
        }
        if ("medianotify".equals(stanzaType)) {
            return MediaType.MEDIA_EXPRESS_NOTIFY;
        }
        if ("creation".equals(pollType)) {
            return MediaType.POLL_CREATE;
        }
        if ("vote".equals(pollType)) {
            return MediaType.POLL_VOTE;
        }
        if (encMediaType == null) {
            return MediaType.NONE;
        }
        return switch (encMediaType) {
            case "image" -> MediaType.PHOTO;
            case "video" -> MediaType.VIDEO;
            case "ptv" -> MediaType.PUSH_TO_VIDEO;
            case "audio" -> MediaType.AUDIO;
            case "ptt" -> MediaType.PTT;
            case "location" -> MediaType.LOCATION;
            case "vcard" -> MediaType.CONTACT;
            case "document" -> MediaType.DOCUMENT;
            case "url" -> MediaType.URL;
            case "call" -> MediaType.CALL;
            case "gif" -> MediaType.GIF;
            case "future" -> MediaType.FUTURE;
            case "contact_array" -> MediaType.CONTACT_ARRAY;
            case "livelocation" -> MediaType.LIVE_LOCATION;
            case "profile_pic" -> MediaType.PROFILE_PIC;
            case "sticker" -> MediaType.STICKER;
            case "sticker_pack" -> MediaType.STICKER_PACK;
            case "hsm" -> MediaType.HSM;
            case "product_image", "product" -> MediaType.PRODUCT_IMAGE;
            case "template" -> MediaType.TEMPLATE;
            case "md_app_state" -> MediaType.MD_APP_STATE;
            case "md_history_sync" -> MediaType.MD_HISTORY_SYNC;
            case "list" -> MediaType.LIST;
            case "list_response" -> MediaType.LIST_REPLY;
            case "button" -> MediaType.BUTTON_MESSAGE;
            case "button_response" -> MediaType.BUTTON_RESPONSE_MESSAGE;
            case "order" -> MediaType.ORDER;
            case "native_flow_response" -> MediaType.INTERACTIVE_RESPONSE_NFM;
            case "group_history" -> MediaType.GROUP_HISTORY;
            default -> MediaType.NONE;
        };
    }

    /**
     * Emits an {@code IncomingMessageDropEvent} (id 3724) for a drop that
     * occurred before the stanza could be parsed into a
     * {@link MessageReceiveStanza}.
     *
     * <p>WA Web's {@code WAWebPostIncomingMessageDropMetric.postIncomingMessageDropInvalidStanza}
     * runs the raw stanza through an {@code incomingMsgParserForMetric}
     * parser to extract best-effort metadata (offline flag, offline count,
     * edit attribute, enc type, retry count, etc.).  Cobalt does not own
     * an equivalent best-effort parser at this call site, so only the
     * fields available directly from the stanza node attributes are
     * populated; the remaining properties default to absent, matching
     * WA Web's behaviour when the metric parser fails.
     *
     * @param node            the raw incoming stanza node
     * @param messageDropReason the drop reason to report
     */
    private void emitIncomingMessageDropFromNode(Node node, MessageDropReasonType messageDropReason) {
        var builder = new IncomingMessageDropEventBuilder()
                .messageDropReason(messageDropReason);

        var offline = node.getAttributeAsLong("offline", (Long) null);
        if (offline != null) {
            builder.offline(true).offlineCount(offline.intValue());
        } else {
            builder.offline(false);
        }

        wamService.commit(builder.build());
    }

    /**
     * Emits an {@link com.github.auties00.cobalt.wam.event.UnknownStanzaEvent}
     * (id 3448) for a stanza that failed to parse.
     *
     * <p>Mirrors {@code WAWebPostUnknownStanzaMetric.postUnknownStanzaMetric}:
     * constructs an {@code UnknownStanzaWamEvent} populated with the
     * stanza's top-level tag and its {@code type} attribute (the latter
     * may be {@code null}) and commits it. WA Web does not populate
     * {@code unknownStanzaDropReason}, so Cobalt also leaves it unset.
     *
     * @param node the stanza that failed to parse
     */
    @WhatsAppWebExport(moduleName = "WAWebPostUnknownStanzaMetric",
            exports = "postUnknownStanzaMetric",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void emitUnknownStanzaMetric(Node node) {
        wamService.commit(new UnknownStanzaEventBuilder()
                .unknownStanzaTag(node.description())
                .unknownStanzaType(node.getAttributeAsString("type", null))
                .build());
    }

    /**
     * Emits an {@code IncomingMessageDropEvent} (id 3724) for a drop that
     * occurred while processing an already-parsed stanza.
     *
     * <p>The drop reason is derived from the exception type following the
     * WA Web mapping in
     * {@code WAWebMsgProcessingDecryptionHandler.k()}:
     * {@link WhatsAppMessageException.Receive.InvalidProtobuf} and
     * {@link WhatsAppMessageException.Receive.InvalidDeviceSentMessage}
     * map to {@code INVALID_PROTOBUF}; {@link WhatsAppMessageException.Receive.HsmMismatch}
     * is skipped (the function returns without emitting); expired status
     * messages map to {@code EXPIRED}; everything else maps to
     * {@code INVALID_STANZA} via
     * {@code postIncomingMessageDropInvalidStanzaFromDecryptedMessageInfo}.
     * When the exception argument is {@code null}, the drop reason is
     * {@code INTERNAL_ERROR} matching
     * {@code postIncomingMessageDropInternalError} in
     * {@code WAWebCommsHandleStanzaUtils.handleMessageParsingFailure}.
     *
     * @param stanza    the parsed incoming stanza
     * @param exception the receive exception that triggered the drop, or
     *                  {@code null} for an internal unhandled error
     */
    private void emitIncomingMessageDropFromStanza(
            MessageReceiveStanza stanza,
            WhatsAppMessageException.Receive exception
    ) {
        // UnknownDevice, BroadcastEphSettings and SignalDuplicateMessage skip the metric.
        if (exception instanceof WhatsAppMessageException.Receive.HsmMismatch
                || exception instanceof WhatsAppMessageException.Receive.BroadcastEphemeralSettings
                || exception instanceof WhatsAppMessageException.Receive.DuplicateMessage
                || exception instanceof WhatsAppMessageException.Receive.UnknownDevice
                || exception instanceof WhatsAppMessageException.Receive.NoSession
                || exception instanceof WhatsAppMessageException.Receive.InvalidKey
                || exception instanceof WhatsAppMessageException.Receive.InvalidKeyId
                || exception instanceof WhatsAppMessageException.Receive.InvalidSignedPreKey
                || exception instanceof WhatsAppMessageException.Receive.InvalidOneTimeKey
                || exception instanceof WhatsAppMessageException.Receive.NoSenderKey
                || exception instanceof WhatsAppMessageException.Receive.InvalidSenderKey
                || exception instanceof WhatsAppMessageException.Receive.BadMac
                || exception instanceof WhatsAppMessageException.Receive.FutureMessage
                || exception instanceof WhatsAppMessageException.Receive.InvalidSignature
                || exception instanceof WhatsAppMessageException.Receive.AdvFailure) {
            return;
        }

        var messageDropReason = resolveDropReason(stanza, exception);
        if (messageDropReason == null) {
            return;
        }

        var builder = new IncomingMessageDropEventBuilder()
                .messageDropReason(messageDropReason);

        // offlineCount = parseInt(i.offline, 10).
        builder.offline(stanza.isOffline());
        stanza.offline().ifPresent(raw -> {
            try {
                builder.offlineCount(Integer.parseInt(raw));
            } catch (NumberFormatException _) {
                // WA Web: Number.isNaN(p) || (s.offlineCount = p)
            }
        });

        var encs = stanza.encs();
        if (!encs.isEmpty()) {
            var firstEnc = encs.getFirst();
            builder.retryCount(firstEnc.retryCount());

            builder.e2eCiphertextType(mapCiphertextTypeForDrop(firstEnc.e2eType()));
        }

        // Cobalt exposes stanza.category() but does not classify the WAM enum locally; this
        // matches the WA Web behaviour where the classifier (getWamInvisibleMessageCatgoryType)
        // returns null for unrecognised values and the field remains absent.

        var destination = mapDestination(stanza);
        if (destination != null) {
            builder.e2eDestination(destination);
        }

        // MessageValidationError. Cobalt does not track an e2eFailureReason on its
        // exception hierarchy today, so the field is left absent.

        wamService.commit(builder.build());
    }

    /**
     * Emits a {@link com.github.auties00.cobalt.wam.event.MdBadDeviceSentMessageEvent}
     * (id 2176) when the current receive failure corresponds to a DSM
     * validation error.
     *
     * <p>In WA Web the event is committed inside the
     * {@code DeviceSentMessageError} constructor with the two inputs
     * passed to the constructor itself ({@code peerType} and
     * {@code dsmError}).  Cobalt raises the equivalent
     * {@link WhatsAppMessageException.Receive.InvalidDeviceSentMessage}
     * exception from {@code ChatMessageReceiver} and catches it here,
     * where the WAM service is accessible without expanding the
     * receiver's constructor dependencies.
     *
     * <p>The {@code peerType} property is derived from the sender JID's
     * device id following {@code WAWebMsgProcessingApiUtils.getDeviceType}:
     * a zero device id ({@code DEFAULT_DEVICE_ID}) maps to
     * {@link DeviceType#PRIMARY}; any other value maps to
     * {@link DeviceType#COMPANION}.
     *
     * <p>The remaining properties declared on the event spec
     * ({@code editType}, {@code encryptionType}, {@code isLid},
     * {@code mediaType}, {@code messageType}, {@code revokeType}) are
     * left absent because the WA Web emission site populates only
     * {@code peerType} and {@code dsmError}.
     *
     * @param stanza    the parsed incoming stanza
     * @param exception the receive exception that triggered the drop
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgError", exports = "DeviceSentMessageError",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitMdBadDeviceSentMessageIfApplicable(
            MessageReceiveStanza stanza,
            WhatsAppMessageException.Receive exception
    ) {
        // validation failure occurs; all other exception subtypes bypass this emission.
        if (!(exception instanceof WhatsAppMessageException.Receive.InvalidDeviceSentMessage dsmException)) {
            return;
        }

        // → DEVICE_TYPE.PRIMARY, otherwise → DEVICE_TYPE.COMPANION.
        var peerType = stanza.senderJid().device() == 0
                ? DeviceType.PRIMARY
                : DeviceType.COMPANION;

        // to MdBadDeviceSentMessageWamEvent. Cobalt's DsmErrorType uses the same three enum constants
        // as the WAM enum, so the mapping is 1:1 by name.
        var dsmError = switch (dsmException.errorType()) {
            case INVALID_SENDER -> DsmError.INVALID_SENDER;
            case MISSING_DSM -> DsmError.MISSING_DSM;
            case INVALID_DSM -> DsmError.INVALID_DSM;
        };

        wamService.commit(new MdBadDeviceSentMessageEventBuilder()
                .peerType(peerType)
                .dsmError(dsmError)
                .build());
    }

    /**
     * Emits a {@link com.github.auties00.cobalt.wam.event.MessageReceiveEvent}
     * (id 450) for a successfully processed E2E chat message.
     *
     * <p>Mirrors the per-message builder constructed inside
     * {@code WAWebLogReceivedMessages.logReceivedMessagesInWAM} (internal
     * function {@code _}) for each decrypted message in the incoming batch.
     * Populates the properties Cobalt can derive directly from the parsed
     * stanza, the decoded {@link ChatMessageInfo}, and the store.
     *
     * <p>Properties that depend on WA Web-specific plumbing Cobalt does not
     * track ({@code deviceCount}, {@code deviceSizeBucket},
     * {@code oppositeVisibleIdentification}, {@code hasUsername},
     * {@code hasUsernamePin}, vcard {@code received*ContactSize},
     * sticker {@code stickerIs*}/{@code stickerMakerSourceType},
     * {@code invisibleMessageCategory}, {@code pairedMediaType},
     * {@code privateAiFeatureName}, {@code traceIdInt},
     * {@code appContext}, {@code stanzaProcessCount},
     * {@code processingDeferred}, {@code isPq}) are intentionally omitted to
     * match WA Web's behaviour when the upstream source is unavailable.
     *
     * <p>The three {@code messageReceiveT*} timers are encoded as elapsed
     * milliseconds via {@code Instant.ofEpochMilli}: {@code T0} is the
     * server-to-client latency ({@code clientReceivedTs - serverTs}),
     * {@code T1} is the client-receive-to-commit latency
     * ({@code now - clientReceivedTs}), and {@code T2} is zeroed exactly
     * as WA Web does at this call site.
     *
     * @param info   the decoded chat message info
     * @param stanza the parsed incoming stanza carrying timestamps,
     *               addressing, offline flag, and retry metadata
     */
    @WhatsAppWebExport(moduleName = "WAWebLogReceivedMessages", exports = "logReceivedMessagesInWAM",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitMessageReceiveForChatMessage(
            ChatMessageInfo info,
            MessageReceiveStanza stanza
    ) {
        var builder = new MessageReceiveEventBuilder();

        builder.messageType(wamService.getWamMessageType(info));

        builder.messageMediaType(wamService.getWamMediaType(info));

        builder.messageIsOffline(stanza.isOffline());

        stanza.offline().ifPresent(raw -> {
            try {
                builder.offlineCount(Integer.parseInt(raw));
            } catch (NumberFormatException _) {
                // Cobalt skips the property when the attribute is not numeric, matching WA Web
                // behaviour for malformed attributes.
            }
        });

        builder.isViewOnce(isViewOnceMessage(info.message()));

        var contextInfo = extractContextInfo(info.message()).orElse(null);
        if (contextInfo != null) {
            builder.isForwardedForward(contextInfo.forwardingScore().orElse(0) > 1);
            builder.isAReply(contextInfo.quotedMessageId().isPresent());

            // ephemeralityInitiator come from WAWebMsgGetters.getWamDisappearingMode*.
            contextInfo.disappearingMode().ifPresent(mode -> applyDisappearingMode(builder, mode));
        } else {
            builder.isForwardedForward(false);
            builder.isAReply(false);
        }

        // the stanza edit attribute and the ProtocolMessage REVOKE subtype.
        var editType = resolveEditType(stanza, info);
        if (editType != null) {
            builder.editType(editType);
        }

        // Cobalt reduces the branch to the primary METABOT classification because BizBotType /
        // BizBotAutomatedType are not modelled; WA Web's function also returns null when neither signal is set.
        if (stanza.senderJid().isBot()) {
            builder.botType(BotType.METABOT);
        }

        builder.isAComment(info.message().content() instanceof CommentMessage);

        builder.chatOrigins(stanza.chatJid().hasLidServer()
                ? ChatOriginsType.LID_CTWA
                : ChatOriginsType.OTHERS);

        builder.isLid(stanza.senderJid().hasLidServer());

        resolveRevokeType(stanza, info).ifPresent(builder::revokeType);

        info.ephemeralDuration().ifPresent(duration -> {
            if (duration > 0) {
                builder.ephemeralityDuration(duration);
            }
        });

        var clientReceivedTsMillis = Instant.now().toEpochMilli();
        var serverTsMillis = stanza.timestamp().toEpochMilli();
        builder.messageReceiveT0(Instant.ofEpochMilli(Math.max(0, clientReceivedTsMillis - serverTsMillis)));
        builder.messageReceiveT1(Instant.ofEpochMilli(0));
        builder.messageReceiveT2(Instant.ofEpochMilli(0));

        var selfJid = whatsapp.store().jid().orElse(null);
        var senderType = wamService.getWamE2eSenderType(stanza.senderJid(), selfJid);
        if (senderType != null) {
            builder.e2eSenderType(senderType);
        }

        if (stanza.senderJid().hasHostedServer() || stanza.senderJid().hasHostedLidServer()) {
            builder.encryptionType(EncryptionTypeCode.COEX);
        }

        // Cobalt does not track subgroup metadata, so the simplest faithful mapping is to emit GROUP when the chat
        // JID is a group/community and to leave the field absent otherwise - matching WA Web when that helper yields null.
        if (stanza.chatJid().hasGroupOrCommunityServer()) {
            builder.typeOfGroup(TypeOfGroupEnum.GROUP);
        }

        stanza.addressingMode()
                .flatMap(MessageStreamHandler::mapAddressingMode)
                .ifPresent(builder::serverAddressingMode);

        // Cobalt only tracks the server-side addressing mode attribute on the stanza; the WA Web
        // "local" variant is the worker-side decision. The builder leaves localAddressingMode
        // absent when Cobalt has no independent source, matching WA Web when that argument is null.

        wamService.commit(builder.build());
    }

    /**
     * Emits a {@link com.github.auties00.cobalt.wam.event.StructuredMessageReceiveEvent}
     * (id 3222) when the incoming chat message is a galaxy-flow CTA
     * ({@code nativeFlowName == "cta_flow"}) or payment-request native-flow
     * ({@code nativeFlowName == "payment_request"}) interactive message.
     *
     * <p>Mirrors the two WA Web sibling call sites invoked from
     * {@code WAWebLogReceivedMessages.logReceivedMessagesInWAM}:
     * <ul>
     *   <li>{@code WAWebGalaxyFlowWamLoggerUtils.logStructuredMessageReceivedWAMEvent}
     *       builds the event with {@code messageClass=BUTTON_NFM},
     *       {@code messageMediaType=getWamMediaType(e)} (which resolves to
     *       {@link MediaType#NONE} for CTA_FLOW per WA Web's inner
     *       {@code d(e)} helper), {@code bizPlatform=CLOUDAPI},
     *       {@code businessOwnerJid=getSender(e).user} and a JSON
     *       {@code messageClassAttributes} payload assembled from the
     *       galaxy-flow CTA button.</li>
     *   <li>{@code WAWebPaymentRequestWamLogger.logPaymentRequestReceivedWAMEvent}
     *       builds the event with {@code messageClass=BUTTON_NFM},
     *       {@code messageMediaType=INTERACTIVE_NFM}, {@code bizPlatform=CLOUDAPI},
     *       {@code businessOwnerJid=getSender(e).user} and a JSON
     *       {@code messageClassAttributes} payload that includes the
     *       payment-funnel id and the parsed payment method.</li>
     * </ul>
     *
     * <p>Cobalt cannot fully reproduce the {@code messageClassAttributes}
     * payload because the upstream helpers
     * ({@code WAWebGetGalaxyFlowCtaButton.getGalaxyFlowCtaButton},
     * {@code WAWebBrPaymentRequest.parsePaymentRequestButton},
     * {@code P2XFunnelIdGenerator.genFunnelInfo}) do not have counterparts
     * here and Cobalt does not track per-conversation CTWA entry-point
     * state. WA Web also omits these JSON fields when the upstream helper
     * yields {@code null}, so the subset emitted by Cobalt stays a
     * strict sub-set of the WA Web event rather than a divergence.
     *
     * @param stanza the parsed incoming stanza carrying the biz native-flow
     *               name attribute and the sender JID
     */
    @WhatsAppWebExport(moduleName = "WAWebGalaxyFlowWamLoggerUtils",
            exports = "logStructuredMessageReceivedWAMEvent",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebPaymentRequestWamLogger",
            exports = "logPaymentRequestReceivedWAMEvent",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitStructuredMessageReceiveIfApplicable(
            MessageReceiveStanza stanza
    ) {
        // both require nativeFlowName to be present on the biz node
        var nativeFlowName = stanza.bizInfo()
                .flatMap(bi -> bi.nativeFlowName())
                .orElse(null);
        if (nativeFlowName == null) {
            return;
        }

        // Per WAWebInteractiveMessagesNativeFlowName: CTA_FLOW="galaxy_message", PAYMENT_REQUEST="payment_request"
        MediaType mediaType;
        switch (nativeFlowName) {
            case "galaxy_message" -> {
                mediaType = MediaType.NONE;
            }
            case "payment_request" -> {
                mediaType = MediaType.INTERACTIVE_NFM;
            }
            default -> {
                return;
            }
        }

        // businessOwnerJid = getSender(e).user
        var businessOwnerJid = stanza.senderJid().toUserJid().user();

        // new StructuredMessageReceiveWamEvent({messageClass:BUTTON_NFM, messageMediaType, bizPlatform:CLOUDAPI, businessOwnerJid, messageClassAttributes:...}).commit()
        var builder = new StructuredMessageReceiveEventBuilder()
                .messageClass(StructuredMessageClass.BUTTON_NFM) // hard-coded at both WA Web call sites
                .messageMediaType(mediaType)
                .bizPlatform(BizPlatform.CLOUDAPI); // hard-coded at both WA Web call sites
        if (businessOwnerJid != null) {
            builder.businessOwnerJid(businessOwnerJid);
        }

        // messageClassAttributes is assembled from WAWebGetGalaxyFlowCtaButton / WAWebBrPaymentRequest
        // / P2XFunnelIdGenerator plus per-conversation CTWA state. Cobalt does not implement any of
        // those helpers, and WA Web also omits the field when its helpers yield null/undefined, so
        // the emission leaves messageClassAttributes absent rather than fabricating a partial JSON.

        wamService.commit(builder.build());
    }

    /**
     * Emits a {@link com.github.auties00.cobalt.wam.event.MessageReceiveEvent}
     * (id 450) for a successfully processed newsletter message.
     *
     * <p>Mirrors the WA Web call site in {@code WAWebHandleNewsletterMsg.default}
     * which forwards the newsletter entry through
     * {@code logReceivedMessagesInWAM({msgs:[_], offline, tsMillis,
     * clientReceivedTsMillis, msgProcessStartTsMillis, serverAddressingMode})}.
     * Only the properties WA Web's per-message loop can derive for a
     * newsletter entry are populated: the addressing, LID, ephemerality,
     * hosted-encryption and group-related branches are skipped because
     * newsletter messages never carry those signals.
     *
     * @param info the decoded newsletter message info
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleNewsletterMsg", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebLogReceivedMessages", exports = "logReceivedMessagesInWAM",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitMessageReceiveForNewsletterMessage(NewsletterMessageInfo info) {
        var builder = new MessageReceiveEventBuilder();

        var parent = info.key().parentJid().orElse(null);
        builder.messageType(wamService.getWamMessageType(parent));

        builder.messageMediaType(wamService.getWamMediaType(info.message()));

        // the offline argument straight through, so false here matches the default)
        builder.messageIsOffline(false);

        builder.isViewOnce(isViewOnceMessage(info.message()));
        var contextInfo = extractContextInfo(info.message()).orElse(null);
        if (contextInfo != null) {
            builder.isForwardedForward(contextInfo.forwardingScore().orElse(0) > 1);
            builder.isAReply(contextInfo.quotedMessageId().isPresent());
        } else {
            builder.isForwardedForward(false);
            builder.isAReply(false);
        }

        builder.chatOrigins(ChatOriginsType.OTHERS);

        // clientReceivedTsMillis, matching the newsletter invocation of logReceivedMessagesInWAM
        builder.messageReceiveT0(Instant.ofEpochMilli(0));
        builder.messageReceiveT1(Instant.ofEpochMilli(0));
        builder.messageReceiveT2(Instant.ofEpochMilli(0));

        wamService.commit(builder.build());
    }

    /**
     * Returns whether the decoded message container carries a view-once
     * wrapper in any of the three WA Web generations.
     *
     * @param container the decoded message container
     * @return {@code true} if the content is a view-once message
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgGetters", exports = "getIsViewOnce",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static boolean isViewOnceMessage(MessageContainer container) {
        if (container == null) {
            return false;
        }
        var type = container.futureProofContentType();
        return type == FutureProofMessageType.VIEW_ONCE;
    }

    /**
     * Extracts the {@link ContextInfo} from the container's resolved
     * content when the message is a {@link ContextualMessage}.
     *
     * @param container the decoded message container
     * @return the context info, or empty when the content carries none
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgGetters", exports = "getNumTimesForwarded",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebMsgGetters", exports = "getIsReply",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static Optional<ContextInfo> extractContextInfo(MessageContainer container) {
        if (container == null) {
            return Optional.empty();
        }
        if (container.content() instanceof ContextualMessage contextual) {
            return contextual.contextInfo();
        }
        return Optional.empty();
    }

    /**
     * Maps a {@link ChatDisappearingMode} onto the three WAM ephemerality
     * properties consumed by {@link MessageReceiveEventBuilder}.
     *
     * @param builder the event builder to populate
     * @param mode    the disappearing mode carried by the message context
     */
    @WhatsAppWebExport(moduleName = "WAWebEphemeralityWAMUtils", exports = "getWamDisappearingModeInitiator",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebEphemeralityWAMUtils", exports = "getWamDisappearingModeTrigger",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebEphemeralityWAMUtils", exports = "getWamDisappearingModeInitiatedByMe",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static void applyDisappearingMode(MessageReceiveEventBuilder builder, ChatDisappearingMode mode) {
        // to DISAPPEARING_CHAT_INITIATOR_TYPE.
        mode.initiator().ifPresent(initiator -> {
            var mapped = switch (initiator) {
                case CHANGED_IN_CHAT -> DisappearingChatInitiatorType.CHAT;
                case INITIATED_BY_ME -> DisappearingChatInitiatorType.INITIATED_BY_ME;
                case INITIATED_BY_OTHER -> DisappearingChatInitiatorType.INITIATED_BY_OTHER;
                case BIZ_UPGRADE_FB_HOSTING -> DisappearingChatInitiatorType.BIZ_UPGRADE_FB_HOSTING;
            };
            builder.disappearingChatInitiator(mapped);
        });

        // EPHEMERALITY_TRIGGER_ACTION_TYPE.
        mode.trigger().ifPresent(trigger -> {
            var mapped = switch (trigger) {
                case UNKNOWN -> EphemeralityTriggerActionType.UNKNOWN;
                case CHAT_SETTING -> EphemeralityTriggerActionType.CHAT_SETTINGS;
                case ACCOUNT_SETTING -> EphemeralityTriggerActionType.ACCOUNT_SETTINGS;
                case BULK_CHANGE -> EphemeralityTriggerActionType.BULK_CHANGE;
                case BIZ_SUPPORTS_FB_HOSTING -> EphemeralityTriggerActionType.BIZ_SUPPORTS_FB_HOSTING;
                case UNKNOWN_GROUPS -> EphemeralityTriggerActionType.UNKNOWN_GROUP;
            };
            builder.ephemeralityTriggerAction(mapped);
        });

        // onto EPHEMERALITY_INITIATOR_TYPE (INITIATED_BY_ME / INITIATED_BY_OTHER).
        builder.ephemeralityInitiator(mode.initiatedByMe()
                ? EphemeralityInitiatorType.INITIATED_BY_ME
                : EphemeralityInitiatorType.INITIATED_BY_OTHER);
    }

    /**
     * Resolves the {@link EditType} for the given stanza, covering both
     * the stanza-level {@code edit} attribute and the
     * {@link ProtocolMessage.Type#REVOKE} subtype carried by the
     * container.
     *
     * @param stanza the parsed incoming stanza
     * @param info   the decoded chat message info
     * @return the matching edit type, or {@code null} when the message
     *         is neither edited nor revoked
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgGetters", exports = "getWamEditType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static EditType resolveEditType(MessageReceiveStanza stanza, ChatMessageInfo info) {
        var editAttr = stanza.editAttribute();
        if (editAttr == MessageReceiveStanza.EDIT_MESSAGE) {
            return EditType.EDITED;
        }
        if (editAttr == MessageReceiveStanza.EDIT_PIN) {
            return EditType.PIN;
        }
        if (editAttr == MessageReceiveStanza.EDIT_SENDER_REVOKE) {
            return EditType.SENDER_REVOKE;
        }
        if (editAttr == MessageReceiveStanza.EDIT_ADMIN_REVOKE) {
            return EditType.ADMIN_REVOKE;
        }
        // the subtype indicates an admin revoke.
        if (info.message().content() instanceof ProtocolMessage protocol) {
            var protocolType = protocol.type().orElse(null);
            if (protocolType == ProtocolMessage.Type.REVOKE) {
                return EditType.SENDER_REVOKE;
            }
            if (protocolType == ProtocolMessage.Type.MESSAGE_EDIT) {
                return EditType.EDITED;
            }
        }
        return null;
    }

    /**
     * Resolves the {@link RevokeType} for the given stanza when the
     * message is a revoke.
     *
     * @param stanza the parsed incoming stanza
     * @param info   the decoded chat message info
     * @return the matching revoke type, or empty otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWebLogReceivedMessages", exports = "logReceivedMessagesInWAM",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static Optional<RevokeType> resolveRevokeType(MessageReceiveStanza stanza, ChatMessageInfo info) {
        var editAttr = stanza.editAttribute();
        if (editAttr == MessageReceiveStanza.EDIT_ADMIN_REVOKE) {
            return Optional.of(RevokeType.ADMIN);
        }
        if (editAttr == MessageReceiveStanza.EDIT_SENDER_REVOKE) {
            return Optional.of(RevokeType.SENDER);
        }
        if (info.message().content() instanceof ProtocolMessage protocol
                && protocol.type().orElse(null) == ProtocolMessage.Type.REVOKE) {
            return Optional.of(RevokeType.SENDER);
        }
        return Optional.empty();
    }

    /**
     * Converts the stanza {@code addressing_mode} attribute value into
     * the corresponding WAM {@link AddressingMode} enum.
     *
     * @param raw the raw attribute value ({@code "pn"} or {@code "lid"})
     * @return the matching enum constant, or empty for unrecognised values
     */
    @WhatsAppWebExport(moduleName = "WAWebWamAddressingModeUtils", exports = "getWamAddressingModeFromString",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static Optional<AddressingMode> mapAddressingMode(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        return switch (raw) {
            case "pn" -> Optional.of(AddressingMode.PN);
            case "lid" -> Optional.of(AddressingMode.LID);
            default -> Optional.empty();
        };
    }

    /**
     * Resolves the {@link MessageDropReasonType} that matches the given
     * exception, mirroring the {@code WAWebMsgProcessingDecryptionHandler.k()}
     * switch on {@code DecryptionErrorType}.
     *
     * @param stanza    the parsed incoming stanza
     * @param exception the receive exception, or {@code null} for an
     *                  internal error
     * @return the drop reason, or {@code null} to skip the emission
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptionHandler", exports = "createDecryptionHandler",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static MessageDropReasonType resolveDropReason(
            MessageReceiveStanza stanza,
            WhatsAppMessageException.Receive exception
    ) {
        // than DAY_SECONDS are reported with MESSAGE_DROP_REASON_TYPE.EXPIRED
        // regardless of the underlying error type.
        if (stanza.chatJid().isStatusBroadcastAccount()) {
            var age = ChronoUnit.HOURS.between(stanza.timestamp(), Instant.now());
            if (age > 24) {
                return MessageDropReasonType.EXPIRED;
            }
        }

        if (exception == null) {
            // arm calls postIncomingMessageDropInternalError.
            return MessageDropReasonType.INTERNAL_ERROR;
        }

        if (exception instanceof WhatsAppMessageException.Receive.InvalidProtobuf
                || exception instanceof WhatsAppMessageException.Receive.InvalidDeviceSentMessage) {
            // branch calls postIncomingMessageDropInvalidProtobuf.
            return MessageDropReasonType.INVALID_PROTOBUF;
        }

        if (exception instanceof WhatsAppMessageException.Receive.InvalidMessage) {
            // ADAPTED: Cobalt's InvalidMessage covers both the hosted-companion rejection
            // path (mapped by WA Web to INVALID_HOSTED_COMPANION_STANZA in
            // subclass the best-effort mapping is INVALID_STANZA, matching
            // postIncomingMessageDropInvalidStanza in WAWebHandleMsg.default.
            return MessageDropReasonType.INVALID_STANZA;
        }

        // calls postIncomingMessageDropInvalidStanzaFromDecryptedMessageInfo.
        return MessageDropReasonType.INVALID_STANZA;
    }

    /**
     * Maps a {@link MessageEncryptionType}
     * to the corresponding WAM {@link E2eCiphertextType}.
     *
     * @param type the Signal ciphertext type
     * @return the matching WAM enum constant
     */
    @WhatsAppWebExport(moduleName = "WAWebBackendJobsCommon", exports = "getMetricE2eCiphertextType",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static E2eCiphertextType mapCiphertextTypeForDrop(
            MessageEncryptionType type
    ) {
        return switch (type) {
            case MSG -> E2eCiphertextType.MESSAGE;
            case PKMSG -> E2eCiphertextType.PREKEY_MESSAGE;
            case SKMSG -> E2eCiphertextType.SENDER_KEY_MESSAGE;
            case MSMSG -> E2eCiphertextType.MESSAGE_SECRET_MESSAGE;
        };
    }

    /**
     * Maps the stanza's chat JID to the corresponding WAM
     * {@link E2eDestination} classification used by
     * {@code WAWebGetMetricE2eDestination.getMetricE2eDestination}.
     *
     * @param stanza the parsed incoming stanza
     * @return the matching destination, or {@code null} when the JID
     *         does not correspond to a tracked destination
     */
    @WhatsAppWebExport(moduleName = "WAWebGetMetricE2eDestination", exports = "getMetricE2eDestination",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static E2eDestination mapDestination(MessageReceiveStanza stanza) {
        var jid = stanza.chatJid();
        if (jid.isStatusBroadcastAccount()) {
            return E2eDestination.STATUS;
        }
        if (jid.hasGroupOrCommunityServer()) {
            return E2eDestination.GROUP;
        }
        if (jid.hasBroadcastServer()) {
            return E2eDestination.LIST;
        }
        if (jid.hasNewsletterServer()) {
            return E2eDestination.CHANNEL;
        }
        if (jid.hasUserServer() || jid.hasLidServer()) {
            return E2eDestination.INDIVIDUAL;
        }
        return null;
    }

    /**
     * Sends a plain acknowledgment for the given message stanza.
     *
     * <p>Used for stanzas that do not require full message processing
     * (e.g. {@code medianotify} type messages).
     *
     * @param node the raw message stanza to acknowledge
     */
    private void sendAck(Node node) {
        if (!whatsapp.store().automaticMessageReceipts()) {
            return;
        }

        var id = node.getAttributeAsString("id", null);
        var from = node.getAttributeAsJid("from").orElse(null);
        if (id == null || from == null) {
            return;
        }

        var ack = new NodeBuilder()
                .description("ack")
                .attribute("id", id)
                .attribute("class", "message")
                .attribute("to", from)
                .attribute("type", node.getAttributeAsString("type", null))
                .attribute("participant", node.getAttributeAsJid("participant").orElse(null))
                .build();
        whatsapp.sendNodeWithNoResponse(ack);
    }

    /**
     * Sends a negative acknowledgment (NACK) for the given message stanza.
     *
     * <p>Includes an integer error code matching the
     * {@code WAWebCreateNackFromStanza.NackReason} constants.
     *
     * @param node      the raw message stanza to nack
     * @param errorCode the string representation of the error code
     */
    private void sendNack(Node node, String errorCode) {
        var id = node.getAttributeAsString("id", null);
        var from = node.getAttributeAsJid("from").orElse(null);
        if (id == null || from == null) {
            return;
        }

        var ack = new NodeBuilder()
                .description("ack")
                .attribute("id", id)
                .attribute("class", "message")
                .attribute("to", from)
                .attribute("type", node.getAttributeAsString("type", null))
                .attribute("participant", node.getAttributeAsJid("participant").orElse(null))
                .attribute("error", parseErrorCode(errorCode))
                .build();
        whatsapp.sendNodeWithNoResponse(ack);
    }

    /**
     * Parses a string error code into an integer, defaulting to 500
     * ({@code NackReason.UnhandledError}) if parsing fails.
     *
     * @param value the string error code
     * @return the parsed integer error code
     */
    private static int parseErrorCode(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException _) {
            return 500;
        }
    }

    /**
     * Stores the processed message info in the appropriate store
     * collection (newsletter, status, or chat).
     *
     * <p>For chat messages, also updates the chat's last message
     * timestamp, conversation timestamp, and unread count.
     *
     * @param info the processed message info to store
     */
    private void storeIncomingMessage(MessageInfo info) {
        switch (info) {
            case NewsletterMessageInfo newsletterInfo -> {
                var newsletterJid = newsletterInfo.key().parentJid().orElse(null);
                if (newsletterJid == null) {
                    return;
                }

                var newsletter = whatsapp.store()
                        .findNewsletterByJid(newsletterJid)
                        .orElseGet(() -> whatsapp.store().addNewNewsletter(newsletterJid));
                newsletter.setTimestamp(newsletterInfo.timestamp().orElse(null));
                if (!newsletterInfo.key().fromMe()) {
                    newsletter.setUnreadMessagesCount(newsletter.unreadMessagesCount() + 1);
                }
                newsletter.addMessage(newsletterInfo);
            }
            case ChatMessageInfo chatInfo -> {
                if (isStatusMessage(chatInfo)) {
                    whatsapp.store().addStatus(chatInfo);
                    return;
                }

                var chatJid = chatInfo.key().parentJid().orElse(null);
                if (chatJid == null) {
                    return;
                }

                var chat = whatsapp.store()
                        .findChatByJid(chatJid)
                        .orElseGet(() -> whatsapp.store().addNewChat(chatJid));
                var timestamp = chatInfo.timestamp().orElse(null);
                chat.setLastMsgTimestamp(timestamp);
                chat.setConversationTimestamp(timestamp);
                if (!chatInfo.key().fromMe()) {
                    chat.setUnreadCount(chat.unreadCount().orElse(0) + 1);
                }
                chat.addMessage(chatInfo);
            }
        }
    }

    /**
     * Notifies all registered listeners about a received message.
     *
     * <p>Each listener is invoked on its own virtual thread.  Status
     * messages trigger an additional {@code onNewStatus} callback.
     * If the message quotes another message, {@code onMessageReply}
     * is also invoked.
     *
     * @param info          the received message info
     * @param quotedMessage the quoted message, if any
     */
    private void notifyMessageReceived(MessageInfo info, Optional<? extends MessageInfo> quotedMessage) {
        var statusMessage = isStatusMessage(info);
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> {
                listener.onNewMessage(whatsapp, info);
                if (statusMessage && info instanceof ChatMessageInfo chatMessageInfo) {
                    listener.onNewStatus(whatsapp, chatMessageInfo);
                }
                quotedMessage.ifPresent(quoted -> listener.onMessageReply(whatsapp, info, quoted));
            });
        }
    }

    /**
     * Returns whether the message is a status broadcast message.
     *
     * @param info the message info to check
     * @return {@code true} if the parent JID is the status broadcast account
     */
    private boolean isStatusMessage(MessageInfo info) {
        return info.key()
                .parentJid()
                .map(Jid::isStatusBroadcastAccount)
                .orElse(false);
    }

    /**
     * Resolves a previously stored orphan payment notification for
     * the given message, if one exists.
     *
     * <p>When a payment transaction notification arrives before the
     * corresponding message, it is stored as an orphan.  When the
     * message arrives later, this method matches and resolves it.
     *
     * @param info the received message info
     */
    private void resolveOrphanPayment(MessageInfo info) {
        if (!(info instanceof ChatMessageInfo chatMessageInfo)) {
            return;
        }

        var orphan = chatMessageInfo.key()
                .id()
                .flatMap(whatsapp.store()::removeOrphanPaymentNotification)
                .orElse(null);
        if (orphan == null) {
            return;
        }

        var transactionNode = new NodeBuilder()
                .description("transaction")
                .attribute("message-id", orphan.messageId())
                .attribute("receiver", orphan.receiverJid().orElse(null))
                .attribute("currency", orphan.currency().orElse(null))
                .attribute("amount_1000", orphan.amount1000().orElse(null))
                .attribute("transaction-type", orphan.transactionType().orElse(null))
                .attribute("status", orphan.status().orElse(null))
                .attribute("ts", orphan.transactionTimestamp().orElse(null))
                .attribute("sender", chatMessageInfo.senderJid().orElse(null))
                .build();
        handlePaymentTransaction(transactionNode);
    }

    /**
     * Processes a payment transaction node by locating the associated
     * message and updating its payment info.
     *
     * <p>If the associated message is not found, the transaction data
     * is stored as an orphan payment notification for later resolution.
     *
     * @param transaction the transaction node containing payment attributes
     */
    private void handlePaymentTransaction(Node transaction) {
        var sender = transaction.getAttributeAsJid("sender").orElse(null);
        var receiver = transaction.getAttributeAsJid("receiver").orElse(null);
        var messageId = transaction.getAttributeAsString("message-id", null);
        if (sender == null || receiver == null || messageId == null) {
            return;
        }

        var self = whatsapp.store().jid().orElse(null);
        var fromMe = self != null && Objects.equals(self.toUserJid(), sender.toUserJid());
        var group = transaction.getAttributeAsJid("group").orElse(null);
        var remote = group != null ? group : fromMe ? receiver : sender;
        var participant = group != null ? sender : null;

        var message = findPaymentMessage(remote, participant, messageId, fromMe);
        if (!(message instanceof ChatMessageInfo chatMessageInfo)) {
            whatsapp.store().addOrphanPaymentNotification(new OrphanPaymentNotificationBuilder()
                    .messageId(messageId)
                    .receiverJid(receiver)
                    .currency(transaction.getAttributeAsString("currency", null))
                    .amount1000(transaction.getAttributeAsLong("amount_1000", (Long) null))
                    .transactionType(transaction.getAttributeAsString("transaction-type", null))
                    .status(transaction.getAttributeAsString("status", null))
                    .transactionTimestamp(transaction.getAttributeAsLong("ts", (Long) null))
                    .build());
            return;
        }

        var paymentInfo = chatMessageInfo.paymentInfo().orElseGet(this::newPaymentInfo);
        var type = transaction.getAttributeAsString("transaction-type", null);
        var status = transaction.getAttributeAsString("status", null);

        paymentInfo.setReceiverJid(receiver);
        paymentInfo.setAmount1000(transaction.getAttributeAsLong("amount_1000", (Long) null));
        paymentInfo.setCurrency(transaction.getAttributeAsString("currency", null));
        paymentInfo.setTransactionTimestamp(transaction.getAttributeAsLong("ts", (Long) null));
        paymentInfo.setStatus(mapPaymentStatus(type, status, fromMe));
        paymentInfo.setTxnStatus(mapTxnStatus(type, status, fromMe));

        chatMessageInfo.setPaymentInfo(paymentInfo);
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onMessageStatus(whatsapp, chatMessageInfo));
        }
        whatsapp.store().removeOrphanPaymentNotification(messageId);
    }

    /**
     * Finds the message associated with a payment transaction.
     *
     * <p>First attempts a key-based lookup, then falls back to an
     * ID-based search within the chat.
     *
     * @param remote      the remote JID (chat or contact)
     * @param participant the participant JID for group messages
     * @param messageId   the message ID to search for
     * @param fromMe      whether the message was sent by us
     * @return the matching message info, or {@code null} if not found
     */
    private MessageInfo findPaymentMessage(Jid remote, Jid participant, String messageId, boolean fromMe) {
        var direct = whatsapp.store()
                .findMessageByKey(new MessageKeyBuilder()
                        .id(messageId)
                        .parentJid(remote)
                        .fromMe(fromMe)
                        .senderJid(participant != null ? participant : remote)
                        .build())
                .orElse(null);
        if (direct != null) {
            return direct;
        }

        return whatsapp.store().findMessageById(remote, messageId)
                .map(MessageInfo.class::cast)
                .orElse(null);
    }

    /**
     * Creates a new payment info with default unknown status values.
     *
     * @return a new {@link PaymentInfo} with unknown status
     */
    private PaymentInfo newPaymentInfo() {
        return new PaymentInfoBuilder()
                .status(PaymentInfo.Status.UNKNOWN_STATUS)
                .txnStatus(PaymentInfo.TxnStatus.UNKNOWN)
                .build();
    }

    /**
     * Maps a payment transaction type and status to a
     * {@link PaymentInfo.Status} value.
     *
     * @param type   the transaction type string
     * @param status the transaction status string
     * @param fromMe whether the transaction was initiated by us
     * @return the mapped payment status
     */
    private PaymentInfo.Status mapPaymentStatus(String type, String status, boolean fromMe) {
        return switch (paymentMessageStatus(type, status, fromMe)) {
            case SEND_PAY_INIT, SEND_PAY_PENDING, RECV_PAY_INIT, RECV_PAY_PENDING, RECV_PAY_RETRY_ON_FAILURE, REQUEST_PAY_INIT -> PaymentInfo.Status.PROCESSING;
            case SEND_PAY_PENDING_RECEIVER, SEND_PAY_FAILURE_RECEIVER -> PaymentInfo.Status.SENT;
            case REQUEST_PAY_SUCCESS -> paymentMessageTransactionType(type, fromMe) == PaymentMessageTransactionType.TYPE_P2P_REQ_SENT ? PaymentInfo.Status.WAITING_FOR_PAYER : PaymentInfo.Status.WAITING;
            case RECV_PAY_PENDING_SETUP -> PaymentInfo.Status.NEED_TO_ACCEPT;
            case SEND_PAY_SUCCESS, RECV_PAY_SUCCESS, REQUEST_PAY_FULFILLED -> PaymentInfo.Status.COMPLETE;
            case SEND_PAY_FAILURE, SEND_PAY_FAILURE_RISK, SEND_PAY_PENDING_REFUND, SEND_PAY_REFUND_PENDING, SEND_PAY_REFUND_FAILED, SEND_PAY_REFUND_FAILED_PROCESSING, RECV_PAY_FAILURE, REQUEST_PAY_FAILED, REQUEST_PAY_FAILED_RISK -> PaymentInfo.Status.COULD_NOT_COMPLETE;
            case SEND_PAY_REFUNDED -> PaymentInfo.Status.REFUNDED;
            case RECV_PAY_EXPIRED, REQUEST_PAY_EXPIRED, SEND_PAY_AUTH_CANCELED, SEND_PAY_AUTH_CANCEL_FAILED, SEND_PAY_AUTH_CANCEL_FAILED_PROCESSING -> PaymentInfo.Status.EXPIRED;
            case REQUEST_PAY_REJECTED -> PaymentInfo.Status.REJECTED;
            case REQUEST_PAY_CANCELLED -> PaymentInfo.Status.CANCELLED;
            case null, default -> PaymentInfo.Status.UNKNOWN_STATUS;
        };
    }

    /**
     * Maps a payment transaction type and status to a
     * {@link PaymentInfo.TxnStatus} value.
     *
     * @param type   the transaction type string
     * @param status the transaction status string
     * @param fromMe whether the transaction was initiated by us
     * @return the mapped transaction status
     */
    private PaymentInfo.TxnStatus mapTxnStatus(String type, String status, boolean fromMe) {
        return switch (paymentMessageStatus(type, status, fromMe)) {
            case RECV_PAY_EXPIRED, SEND_PAY_EXPIRED -> PaymentInfo.TxnStatus.EXPIRED_TXN;
            case RECV_PAY_FAILURE, SEND_PAY_FAILURE -> PaymentInfo.TxnStatus.FAILED;
            case RECV_PAY_INIT, SEND_PAY_INIT -> PaymentInfo.TxnStatus.INIT;
            case RECV_PAY_PENDING_SETUP -> PaymentInfo.TxnStatus.PENDING_SETUP;
            case RECV_PAY_PENDING, SEND_PAY_PENDING -> PaymentInfo.TxnStatus.FAILED_DA;
            case RECV_PAY_RETRY_ON_FAILURE -> PaymentInfo.TxnStatus.FAILED_PROCESSING;
            case RECV_PAY_SUCCESS, SEND_PAY_SUCCESS, REQUEST_PAY_FULFILLED -> PaymentInfo.TxnStatus.SUCCESS;
            case REQUEST_PAY_CANCELLED -> PaymentInfo.TxnStatus.COLLECT_CANCELED;
            case REQUEST_PAY_CANCELLING -> PaymentInfo.TxnStatus.COLLECT_CANCELLING;
            case REQUEST_PAY_EXPIRED -> PaymentInfo.TxnStatus.COLLECT_EXPIRED;
            case REQUEST_PAY_FAILED_RISK -> PaymentInfo.TxnStatus.COLLECT_FAILED_RISK;
            case REQUEST_PAY_FAILED -> PaymentInfo.TxnStatus.COLLECT_FAILED;
            case REQUEST_PAY_INIT -> PaymentInfo.TxnStatus.COLLECT_INIT;
            case REQUEST_PAY_REJECTED -> PaymentInfo.TxnStatus.COLLECT_REJECTED;
            case REQUEST_PAY_SUCCESS -> PaymentInfo.TxnStatus.COLLECT_SUCCESS;
            case SEND_PAY_AUTH_CANCELED -> PaymentInfo.TxnStatus.AUTH_CANCELED;
            case SEND_PAY_AUTH_CANCEL_FAILED_PROCESSING -> PaymentInfo.TxnStatus.AUTH_CANCEL_FAILED_PROCESSING;
            case SEND_PAY_AUTH_CANCEL_FAILED -> PaymentInfo.TxnStatus.AUTH_CANCEL_FAILED;
            case SEND_PAY_FAILURE_RECEIVER -> PaymentInfo.TxnStatus.FAILED_RECEIVER_PROCESSING;
            case SEND_PAY_FAILURE_RISK, RECV_PAY_FAILURE_RISK -> PaymentInfo.TxnStatus.FAILED_RISK;
            case SEND_PAY_PENDING_RECEIVER -> PaymentInfo.TxnStatus.PENDING_RECEIVER_SETUP;
            case SEND_PAY_PENDING_REFUND -> PaymentInfo.TxnStatus.FAILED_DA_FINAL;
            case SEND_PAY_REFUNDED -> PaymentInfo.TxnStatus.REFUNDED_TXN;
            case SEND_PAY_REFUND_FAILED_PROCESSING -> PaymentInfo.TxnStatus.REFUND_FAILED_PROCESSING;
            case SEND_PAY_REFUND_FAILED -> PaymentInfo.TxnStatus.REFUND_FAILED;
            case SEND_PAY_REFUND_PENDING -> PaymentInfo.TxnStatus.REFUND_FAILED_DA;
            case SEND_PAY_IN_REVIEW -> PaymentInfo.TxnStatus.IN_REVIEW;
            case null, default -> PaymentInfo.TxnStatus.UNKNOWN;
        };
    }

    /**
     * Determines the internal payment message status from the transaction
     * type, raw status string, and direction.
     *
     * @param type   the transaction type string
     * @param status the raw status string from the transaction node
     * @param fromMe whether the payment was sent by us
     * @return the resolved payment message status
     */
    private PaymentMessageStatus paymentMessageStatus(String type, String status, boolean fromMe) {
        var statusValue = status == null ? "" : status.toUpperCase();
        return switch (paymentMessageTransactionType(type, fromMe)) {
            case TYPE_P2M_PAYOUT -> PaymentMessageStatus.STATUS_UNSET;
            case TYPE_P2P_SENT, TYPE_P2M_SENT, TYPE_DEPOSIT -> switch (statusValue) {
                case "PENDING_RECEIVER_SETUP" -> PaymentMessageStatus.SEND_PAY_PENDING_RECEIVER;
                case "FAILED_DA" -> PaymentMessageStatus.SEND_PAY_PENDING;
                case "REFUND_FAILED_DA" -> PaymentMessageStatus.SEND_PAY_REFUND_PENDING;
                case "FAILED_RISK" -> PaymentMessageStatus.SEND_PAY_FAILURE_RISK;
                case "INITIAL" -> PaymentMessageStatus.SEND_PAY_INIT;
                case "SUCCESS", "COMPLETED" -> PaymentMessageStatus.SEND_PAY_SUCCESS;
                case "FAILURE", "FAILED" -> PaymentMessageStatus.SEND_PAY_FAILURE;
                case "REFUNDED" -> PaymentMessageStatus.SEND_PAY_REFUNDED;
                case "REFUND_FAILED" -> PaymentMessageStatus.SEND_PAY_REFUND_FAILED;
                case "FAILED_RECEIVER_PROCESSING" -> PaymentMessageStatus.SEND_PAY_FAILURE_RECEIVER;
                case "REFUND_FAILED_PROCESSING" -> PaymentMessageStatus.SEND_PAY_REFUND_FAILED_PROCESSING;
                case "FAILED_DA_FINAL" -> PaymentMessageStatus.SEND_PAY_PENDING_REFUND;
                case "AUTH_CANCEL_FAILED_PROCESSING" -> PaymentMessageStatus.SEND_PAY_AUTH_CANCEL_FAILED_PROCESSING;
                case "AUTH_CANCEL_FAILED" -> PaymentMessageStatus.SEND_PAY_AUTH_CANCEL_FAILED;
                case "AUTH_CANCELED" -> PaymentMessageStatus.SEND_PAY_AUTH_CANCELED;
                case "CANCELLED" -> PaymentMessageStatus.SEND_PAY_USER_CANCELED;
                case "EXPIRED" -> PaymentMessageStatus.SEND_PAY_EXPIRED;
                case "IN_REVIEW" -> PaymentMessageStatus.SEND_PAY_IN_REVIEW;
                case "PENDING" -> PaymentMessageStatus.SEND_PAY_PENDING_PROCESSING;
                default -> PaymentMessageStatus.STATUS_UNSET;
            };
            case TYPE_P2P_RCVD, TYPE_P2M_RCVD -> switch (statusValue) {
                case "PENDING_SETUP" -> PaymentMessageStatus.RECV_PAY_PENDING_SETUP;
                case "FAILED_DA" -> PaymentMessageStatus.RECV_PAY_PENDING;
                case "FAILED_PROCESSING" -> PaymentMessageStatus.RECV_PAY_RETRY_ON_FAILURE;
                case "SUCCESS", "COMPLETED" -> PaymentMessageStatus.RECV_PAY_SUCCESS;
                case "FAILURE", "FAILED" -> PaymentMessageStatus.RECV_PAY_FAILURE;
                case "EXPIRED" -> PaymentMessageStatus.RECV_PAY_EXPIRED;
                case "FAILED_RISK" -> PaymentMessageStatus.RECV_PAY_FAILURE_RISK;
                case "WITHDRAWAL_PROCESSING" -> PaymentMessageStatus.RECV_PAY_WITHDRAWAL_PROCESSING;
                case "WITHDRAWAL_FAILURE" -> PaymentMessageStatus.RECV_PAY_WITHDRAWAL_FAILURE;
                case "WITHDRAWAL_PERMANENT_FAILED" -> PaymentMessageStatus.RECV_PAY_WITHDRAWAL_PERMANENT_FAILED;
                case "CANCELLED" -> PaymentMessageStatus.RECV_PAY_SENDER_CANCELED;
                default -> PaymentMessageStatus.STATUS_UNSET;
            };
            case TYPE_P2P_REQ_SENT, TYPE_P2P_REQ_RCVD -> switch (statusValue) {
                case "COLLECT_SUCCESS" -> PaymentMessageStatus.REQUEST_PAY_SUCCESS;
                case "COLLECT_FAILED" -> PaymentMessageStatus.REQUEST_PAY_FAILED;
                case "COLLECT_FAILED_RISK" -> PaymentMessageStatus.REQUEST_PAY_FAILED_RISK;
                case "COLLECT_REJECTED" -> PaymentMessageStatus.REQUEST_PAY_REJECTED;
                case "COLLECT_EXPIRED" -> PaymentMessageStatus.REQUEST_PAY_EXPIRED;
                case "COLLECT_CANCELED" -> PaymentMessageStatus.REQUEST_PAY_CANCELLED;
                default -> PaymentMessageStatus.STATUS_UNSET;
            };
            case TYPE_P2P_REQ_SCHEDULED_PAYMENT_RCVD -> switch (statusValue) {
                case "COLLECT_SUCCESS" -> PaymentMessageStatus.REQUEST_PAY_SCHEDULED_PAYMENT_SUCCESS;
                case "AUTH_SUCCESS" -> PaymentMessageStatus.SEND_PAY_AUTH_SUCCESS;
                default -> PaymentMessageStatus.STATUS_UNSET;
            };
            case TYPE_REFUND -> switch (statusValue) {
                case "SUCCESS", "COMPLETED" -> PaymentMessageStatus.RECV_PAY_SUCCESS;
                default -> PaymentMessageStatus.STATUS_UNSET;
            };
            case TYPE_WITHDRAWAL -> switch (statusValue) {
                case "PENDING" -> PaymentMessageStatus.WITHDRAWAL_PENDING;
                case "IN_REVIEW" -> PaymentMessageStatus.WITHDRAWAL_IN_REVIEW;
                case "SUCCESS", "COMPLETED" -> PaymentMessageStatus.WITHDRAWAL_SUCCESS;
                case "FAILED", "DECLINED" -> PaymentMessageStatus.WITHDRAWAL_FAILED;
                case "CANCELLED" -> PaymentMessageStatus.WITHDRAWAL_USER_CANCELED;
                case "EXPIRED" -> PaymentMessageStatus.WITHDRAWAL_EXPIRED;
                case "WITHDRAWAL_ACTIVE" -> PaymentMessageStatus.WITHDRAWAL_ACTIVE;
                default -> PaymentMessageStatus.STATUS_UNSET;
            };
            case TYPE_UNSET, TYPE_P2P_GRP, TYPE_P2P_NO_INFO, TYPE_FUTURE, TYPE_P2P_REQ_GRP, TYPE_MISSING_DETAILS ->
                    PaymentMessageStatus.STATUS_UNSET;
        };
    }

    /**
     * Determines the payment transaction type from the raw type string
     * and direction.
     *
     * @param type   the raw transaction type string, or {@code null}
     * @param fromMe whether the payment was sent by us
     * @return the resolved transaction type
     */
    private PaymentMessageTransactionType paymentMessageTransactionType(String type, boolean fromMe) {
        if (type == null) {
            return fromMe ? PaymentMessageTransactionType.TYPE_P2P_SENT : PaymentMessageTransactionType.TYPE_P2P_RCVD;
        }

        return switch (type.toLowerCase()) {
            case "p2p" -> fromMe ? PaymentMessageTransactionType.TYPE_P2P_SENT : PaymentMessageTransactionType.TYPE_P2P_RCVD;
            case "p2m" -> fromMe ? PaymentMessageTransactionType.TYPE_P2M_SENT : PaymentMessageTransactionType.TYPE_P2M_RCVD;
            case "payout" -> PaymentMessageTransactionType.TYPE_P2M_PAYOUT;
            case "deposit" -> PaymentMessageTransactionType.TYPE_DEPOSIT;
            case "refund" -> PaymentMessageTransactionType.TYPE_REFUND;
            case "withdrawal" -> PaymentMessageTransactionType.TYPE_WITHDRAWAL;
            default -> fromMe ? PaymentMessageTransactionType.TYPE_P2P_SENT : PaymentMessageTransactionType.TYPE_P2P_RCVD;
        };
    }

    /**
     * Handles protocol messages embedded within chat messages.
     *
     * <p>Processes the following protocol message types:
     * <ul>
     *   <li>LID migration mapping sync</li>
     *   <li>Peer data operation request/response (snapshot recovery)</li>
     *   <li>App state sync key share</li>
     *   <li>App state sync key request</li>
     *   <li>History sync notification (download, decrypt and fan-out)</li>
     * </ul>
     *
     * @param info the chat message info containing a protocol message
     */
    private void handleProtocolMessage(ChatMessageInfo info) {
        var content = info.message().content();
        if (!(content instanceof ProtocolMessage protocolMessage)) {
            return;
        }

        // protobuf exposes a lidMigrationSyncMessage, WA Web invokes
        // raw encodedMappingPayload; the parser (WAWebLid1x1MigrationMsgParser.
        // parseLidMigrationMappingSyncMsg) may yield {mappings: [], ...} for an
        // empty or malformed buffer. Cobalt performs the decode here so the
        // service receives a decoded payload or a {@code null} sentinel which it
        // escalates via WhatsAppLidMigrationException.FailedToParseMappings.
        protocolMessage.lidMigrationMappingSyncMessage().ifPresent(message -> {
            var decoded = message.encodedMappingPayload()
                    .flatMap(this::decodeLidMappingPayload)
                    .orElse(null);
            lidMigrationService.processProtocolMessage(decoded);
        });

        protocolMessage.peerDataOperationRequestResponseMessage()
                .ifPresent(this::resolveSnapshotRecovery);

        protocolMessage.appStateSyncKeyShare()
                .ifPresent(keyShare -> processAppStateSyncKeyShare(info, keyShare));

        protocolMessage.appStateSyncKeyRequest()
                .ifPresent(request -> processAppStateSyncKeyRequest(info, request));

        // announces each history chunk with a HistorySyncNotification inside
        // a ProtocolMessage. The service downloads and decodes the chunk on a
        // dedicated virtual thread so the dispatch loop keeps draining.
        protocolMessage.historySyncNotification()
                .ifPresent(webHistorySyncService::process);

        // The primary phone forwards the user's "show security notifications"
        // preference to the companion via this protocol message right after
        // pairing (and whenever the user toggles the setting on the phone).
        // WAWebUserPrefsNotifications.setGlobalSecurityNotifications writes
        // the value into WAWebUserPrefsKeys.SECURITY_NOTIFICATIONS; the
        // Cobalt equivalent is store.setShowSecurityNotifications.
        protocolMessage.initialSecurityNotificationSettingSync()
                .ifPresent(sync -> whatsapp.store()
                        .setShowSecurityNotifications(sync.securityNotificationEnabled()));
    }

    /**
     * Processes an app state sync key share protocol message.
     *
     * <p>Validates key IDs (must be exactly 6 bytes) and delegates
     * the validated keys to the sync key rotation service.
     *
     * @param info     the chat message info containing the key share
     * @param keyShare the key share containing the shared keys
     */
    private void processAppStateSyncKeyShare(ChatMessageInfo info, AppStateSyncKeyShare keyShare) {
        syncKeyRotationService.logMissingKeysReceived();
        // to WAWebSyncdHandleKeyShare.handleKeyShare. Sender device ID is extracted from
        // the message info; keys with missing or malformed key IDs are filtered out so
        // they never reach the underlying handler.
        var senderDeviceId = info.senderJid().isPresent() ? info.senderJid().get().device() : -1;

        var keys = keyShare.keys();
        var validatedKeys = new ArrayList<AppStateSyncKey>(keys.size());
        for (var key : keys) {
            var keyId = key.keyId()
                    .flatMap(AppStateSyncKeyId::keyId)
                    .orElse(null);
            if (keyId == null) {
                continue;
            }

            // WA Web treats this as a fatal error and reports a metric; Cobalt
            // logs and skips the offending key (the error model elevates fatal
            // states via WhatsAppClientErrorHandler rather than inline calls).
            if (keyId.length != 6) {
                LOGGER.log(System.Logger.Level.ERROR,
                        "syncd: fatal error: key share key id has invalid bytelength of {0}",
                        keyId.length);
                continue;
            }

            validatedKeys.add(key);
        }

        if (validatedKeys.isEmpty()) {
            return;
        }

        // tracking, reschedules timeouts, and resumes blocked collections (which
        // includes the equivalent of WAWebSyncd.syncBlockedCollections).
        syncKeyRotationService.handleKeyShare(senderDeviceId, validatedKeys);
    }

    /**
     * Processes an app state sync key request protocol message by
     * sending the requested keys back to the requester.
     *
     * <p>Looks up each requested key ID in the store.  If a key is
     * not found, a placeholder entry with just the key ID is included.
     * The response is sent as a peer message.
     *
     * @param info    the chat message info containing the key request
     * @param request the key request listing the requested key IDs
     */
    private void processAppStateSyncKeyRequest(
            ChatMessageInfo info,
            AppStateSyncKeyRequest request
    ) {
        var sender = info.senderJid();
        if (sender.isEmpty()) {
            return;
        }

        var keysToShare = new ArrayList<AppStateSyncKey>();
        for (var requestedKeyId : request.keyIds()) {
            var rawKeyId = requestedKeyId.keyId().orElse(null);
            if (rawKeyId == null) {
                continue;
            }

            var keyToShare = whatsapp.store().findWebAppStateKeyById(rawKeyId)
                    .orElseGet(() -> new AppStateSyncKeyBuilder()
                            .keyId(new AppStateSyncKeyIdBuilder()
                                    .keyId(rawKeyId)
                                    .build())
                            .build());
            keysToShare.add(keyToShare);
        }

        if (keysToShare.isEmpty()) {
            return;
        }

        try {
            var keyShare = new AppStateSyncKeyShareBuilder()
                    .keys(keysToShare)
                    .build();
            var protocolMessage = new ProtocolMessageBuilder()
                    .type(ProtocolMessage.Type.APP_STATE_SYNC_KEY_SHARE)
                    .appStateSyncKeyShare(keyShare)
                    .build();
            var messageContainer = new MessageContainerBuilder()
                    .protocolMessage(protocolMessage)
                    .build();
            var self = whatsapp.store().jid().orElse(null);
            if (self == null) {
                return;
            }

            var key = new MessageKeyBuilder()
                    .id(MessageIdGenerator.generate(MessageIdVersion.V2, sender.get()))
                    .parentJid(self)
                    .fromMe(true)
                    .senderJid(self)
                    .build();
            var response = new ChatMessageInfoBuilder()
                    .key(key)
                    .message(messageContainer)
                    .timestamp(Instant.now())
                    .senderJid(self)
                    .build();
            whatsapp.sendPeerMessage(sender.get(), response);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Failed to answer app state sync key request from {0}: {1}",
                    sender,
                    throwable.getMessage());
        }
    }

    /**
     * Resolves a syncd snapshot fatal recovery response from a peer
     * data operation message.
     *
     * <p>Only processes responses of type
     * {@code COMPANION_SYNCD_SNAPSHOT_FATAL_RECOVERY}.  Decodes the
     * recovery snapshot and passes it to the snapshot recovery service.
     *
     * @param response the peer data operation response message
     */
    private void resolveSnapshotRecovery(PeerDataOperationRequestResponseMessage response) {
        if (response.peerDataOperationRequestType().orElse(null)
                != PeerDataOperationRequestType.COMPANION_SYNCD_SNAPSHOT_FATAL_RECOVERY) {
            return;
        }

        if (!snapshotRecoveryService.isRecoveryEnabled()) {
            return;
        }

        // primary device sends back a single result.
        var results = response.peerDataOperationResult();
        if (results.isEmpty()) {
            return;
        }

        var recovery = results.getFirst().syncdSnapshotFatalRecoveryResponse().orElse(null);
        if (recovery == null) {
            return;
        }

        var sessionId = response.stanzaId().orElse(null);

        // exactly once and pass the decoded SyncdSnapshotRecovery through the recovery
        // promise so the awaiting consumer in WebAppStateService does not decode again.
        SyncdSnapshotRecovery decoded;
        try {
            decoded = snapshotRecoveryService.decodeRecoverySnapshot(recovery);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to decode snapshot recovery payload: {0}",
                    exception.getMessage());
            // logNonMessagePeerDataResponse(COMPANION_SYNCD_SNAPSHOT_FATAL_RECOVERY, t, 0,0,0,1,0)
            // when the initial snapshot-recovery handling throws; errorCount=1.
            wamService.commit(new NonMessagePeerDataOperationResponseEventBuilder()
                    .peerDataRequestType(PeerDataRequestType.SYNCD_SNAPSHOT_RECOVERY)
                    .peerDataRequestSessionId(sessionId)
                    .peerDataResponseCount(0)
                    .peerDataSuccessResponseCount(0)
                    .peerDataSuccessProcessCount(0)
                    .peerDataErrorCount(1)
                    .peerDataNotFoundCount(0)
                    .build());
            return;
        }

        decoded.collectionName()
                .flatMap(SyncPatchType::of)
                .ifPresent(collectionName -> snapshotRecoveryService.resolveRecovery(collectionName, decoded));

        // logNonMessagePeerDataResponse(COMPANION_SYNCD_SNAPSHOT_FATAL_RECOVERY, t, 1,1,1,0,0)
        // after resolveRecoveryPromise; responseCount=1, successResponseCount=1, successProcessCount=1.
        wamService.commit(new NonMessagePeerDataOperationResponseEventBuilder()
                .peerDataRequestType(PeerDataRequestType.SYNCD_SNAPSHOT_RECOVERY)
                .peerDataRequestSessionId(sessionId)
                .peerDataResponseCount(1)
                .peerDataSuccessResponseCount(1)
                .peerDataSuccessProcessCount(1)
                .peerDataErrorCount(0)
                .peerDataNotFoundCount(0)
                .build());
    }

    /**
     * Decodes a GZIP-compressed LID migration mapping sync payload.
     *
     * @param payload the GZIP-compressed protobuf payload bytes
     * @return the decoded payload, or empty if decoding fails
     */
    private Optional<LIDMigrationMappingSyncPayload> decodeLidMappingPayload(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return Optional.empty();
        }

        try (var protobufStream = ProtobufInputStream.fromStream(new GZIPInputStream(new ByteArrayInputStream(payload)))) {
            return Optional.of(LIDMigrationMappingSyncPayloadSpec.decode(protobufStream));
        } catch (Exception exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to decode LID migration mapping payload: {0}",
                    exception.getMessage());
            return Optional.empty();
        }
    }
}
