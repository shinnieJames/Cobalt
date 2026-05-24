package com.github.auties00.cobalt.stream.message;

import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.ack.NackReason;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.media.MediaConnectionService;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.message.receipt.MessageReceiptHandler;
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
import com.github.auties00.cobalt.props.ABPropsService;
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
 * Top-level entry point for inbound {@code <message>} stanzas pushed by
 * the WhatsApp server.
 *
 * <p>The handler dispatches each stanza along one of three branches:
 * <ul>
 *   <li>{@code type="medianotify"} stanzas, which carry no E2E payload,
 *       skip the parser/decryptor and return after the transport ack;</li>
 *   <li>stanzas whose {@code from} JID lives on a newsletter server
 *       are forwarded to {@link #handleNewsletterMessage(Node)}, which
 *       performs plaintext processing without sending a delivery
 *       receipt;</li>
 *   <li>every other stanza is parsed by
 *       {@link MessageReceiveStanzaParser}, processed by
 *       {@link MessageService}, then optionally enriched with a delivery
 *       receipt or bot-invoke ack through the {@link MessageReceiptHandler}.</li>
 * </ul>
 *
 * <p>Every inbound {@code <message>} receives an unconditional
 * {@code <ack class="message">} via {@link AckSender#sendAck} before any
 * branch decision is made; that ack is the transport-level
 * acknowledgement the WhatsApp relay requires for every delivered
 * stanza. Delivery, retry, NACK, and bot-invoke receipts layered on top
 * are higher-level state, not transport acks.
 *
 * <p>Successfully processed stanzas additionally trigger protocol-message
 * handling (key share / key request, snapshot recovery, LID migration
 * mapping sync, history sync, security-notification setting sync),
 * orphan-payment reconciliation, and a fan-out to every registered
 * listener.
 *
 * @apiNote
 * Implements the {@code "message"} arm of
 * {@code WAWebCommsHandleMessagingStanza.handleMessagingStanza}, which
 * is the master demultiplexer WA Web hangs off every inbound XMPP
 * stanza; the actual decryption pipeline matches
 * {@code WAWebHandleMsg.default} and its worker-compatible counterpart.
 * Application code does not invoke this handler directly; it is wired
 * into the socket stream at client construction and receives stanzas
 * via {@link #handle(Node)}.
 *
 * @implNote
 * This implementation collapses WA Web's split between
 * {@code WAWebHandleMsg}, {@code WAWebCommsHandleMessagingStanza}, and
 * {@code WAWebCommsHandleWorkerCompatibleStanza} into a single class
 * because Cobalt has no equivalent of WA Web's worker-vs-main split.
 * WAM emission helpers are colocated with the dispatch path that
 * triggers each event so that the receipt and metric are committed
 * atomically with the processing outcome.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleMsg")
@WhatsAppWebModule(moduleName = "WAWebCommsHandleMessagingStanza")
@WhatsAppWebModule(moduleName = "WAWebCommsHandleWorkerCompatibleStanza")
public final class MessageStreamHandler implements SocketStream.Handler {
    /**
     * Logger used for unstructured diagnostic output on parse failures,
     * receive-pipeline failures, and protocol-message helper failures.
     */
    private static final System.Logger LOGGER = System.getLogger(MessageStreamHandler.class.getName());

    /**
     * Retry-count threshold (inclusive) that triggers the
     * {@code MessageHighRetryCount} WAM metric.
     *
     * @apiNote
     * Mirrors the private module-level constant
     * {@code WAWebPostMessageHighRetryCountMetric.MAX_RETRY = 5}: the
     * metric is committed only when the post-increment retry attempt
     * is greater than or equal to this value.
     *
     * @implNote
     * This implementation matches WA Web bit-for-bit: the metric fires
     * for the fifth retry attempt and beyond, never on the first four.
     */
    @WhatsAppWebExport(moduleName = "WAWebPostMessageHighRetryCountMetric",
            exports = "MAX_RETRY", adaptation = WhatsAppAdaptation.DIRECT)
    private static final int MAX_MESSAGE_RETRY_COUNT = 5;

    /**
     * Threshold (inclusive) on the parsed stanza {@code offline}
     * attribute above which an {@code OfflineCountTooHigh} WAM metric is
     * committed.
     *
     * @apiNote
     * Mirrors the private module-level constant
     * {@code WAWebMaybePostOfflineCountTooHighMetric.OFFLINE_COUNT_TOO_HIGH_THRESHOLD = 11};
     * the metric is committed when the integer-parsed {@code offline}
     * value is greater than or equal to this threshold.
     */
    @WhatsAppWebExport(moduleName = "WAWebMaybePostOfflineCountTooHighMetric",
            exports = "OFFLINE_COUNT_TOO_HIGH_THRESHOLD", adaptation = WhatsAppAdaptation.DIRECT)
    private static final int OFFLINE_COUNT_TOO_HIGH_THRESHOLD = 11;

    /**
     * Reference to the owning {@link WhatsAppClient} used to send
     * acknowledgments and receipts, access the {@code Store}, dispatch
     * listener callbacks, and ship peer messages from protocol-message
     * key-request handling.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The {@link MessageService} that parses and decrypts the inbound
     * stanza into the typed {@link MessageInfo} consumed by Cobalt
     * downstream.
     */
    private final MessageService messageService;

    /**
     * Per-instance {@link MessageReceiptHandler} that ships delivery,
     * retry, NACK, and bot-ack receipts back to the server based on the
     * outcome of {@link MessageService#process(Node)}.
     */
    private final MessageReceiptHandler receiptHandler;

    /**
     * The {@link SnapshotRecoveryService} consulted from
     * {@link #resolveSnapshotRecovery} when a peer-data-operation
     * response carries a {@code COMPANION_SYNCD_SNAPSHOT_FATAL_RECOVERY}
     * payload.
     */
    private final SnapshotRecoveryService snapshotRecoveryService;

    /**
     * The {@link SyncKeyRotationService} consulted from
     * {@link #processAppStateSyncKeyShare} and
     * {@link #processAppStateSyncKeyRequest} for app-state-sync key
     * material exchange.
     */
    private final SyncKeyRotationService syncKeyRotationService;

    /**
     * The {@link LidMigrationService} consulted from
     * {@link #handleProtocolMessage(ChatMessageInfo)} for inbound LID
     * migration mapping sync payloads.
     */
    private final LidMigrationService lidMigrationService;

    /**
     * The {@link WebHistorySyncService} consulted from
     * {@link #handleProtocolMessage(ChatMessageInfo)} for inbound
     * {@code HistorySyncNotification} payloads carried as protocol
     * messages; downloads, decrypts, and decodes the announced history
     * chunk and fans the decoded chunks out to the registered listeners
     * on a dedicated virtual thread.
     */
    private final WebHistorySyncService webHistorySyncService;

    /**
     * The {@link WamService} telemetry sink used to commit every
     * inbound-message WAM event (receive, drop, high-retry,
     * offline-count-too-high, MD bad device-sent, structured-message
     * receive, non-message peer-data response).
     */
    private final WamService wamService;

    /**
     * The {@link AckSender} used to ship the {@code <ack class="message">}
     * stanza for {@code medianotify} stanzas and the
     * {@code <ack class="message" error=...>} NACK for parse and
     * runtime failures.
     */
    private final AckSender ackSender;

    /**
     * Constructs a handler bound to the given collaborators.
     *
     * @apiNote
     * Invoked by the socket-stream wiring at client construction; user
     * code does not instantiate this handler directly.
     *
     * @implNote
     * This implementation derives the receipt handler from the supplied
     * {@link WhatsAppClient} and constructs the history-sync service in
     * place because both are owned solely by the message handler. The
     * {@link SyncKeyRotationService} is pulled off the supplied
     * {@link WebAppStateService} so that the two services share their
     * underlying state.
     *
     * @param whatsapp                the owning {@link WhatsAppClient}
     * @param messageService          the {@link MessageService} that
     *                                drives parsing and decryption
     * @param snapshotRecoveryService the {@link SnapshotRecoveryService}
     *                                consulted for syncd snapshot fatal
     *                                recovery responses
     * @param webAppStateService      the {@link WebAppStateService} from
     *                                which the
     *                                {@link SyncKeyRotationService} is
     *                                obtained
     * @param lidMigrationService     the {@link LidMigrationService}
     *                                consulted for LID migration mapping
     *                                sync protocol messages
     * @param abPropsService          the {@link ABPropsService} threaded
     *                                into the history-sync media
     *                                download pipeline
     * @param wamService              the {@link WamService} telemetry
     *                                sink for inbound-message WAM
     *                                events
     * @param ackSender               the {@link AckSender} used for
     *                                the {@code <ack class="message">}
     *                                and NACK paths
     */
    public MessageStreamHandler(
            WhatsAppClient whatsapp,
            MessageService messageService,
            SnapshotRecoveryService snapshotRecoveryService,
            WebAppStateService webAppStateService,
            LidMigrationService lidMigrationService,
            ABPropsService abPropsService,
            WamService wamService,
            AckSender ackSender,
            MediaConnectionService mediaConnectionService
    ) {
        this.whatsapp = whatsapp;
        this.messageService = Objects.requireNonNull(messageService, "messageService cannot be null");
        this.receiptHandler = new MessageReceiptHandler(whatsapp);
        this.snapshotRecoveryService = Objects.requireNonNull(snapshotRecoveryService, "snapshotRecoveryService cannot be null");
        this.syncKeyRotationService = Objects.requireNonNull(webAppStateService, "webAppStateService cannot be null").syncKeyRotationService();
        this.lidMigrationService = Objects.requireNonNull(lidMigrationService, "lidMigrationService cannot be null");
        this.wamService = Objects.requireNonNull(wamService, "wamService cannot be null");
        Objects.requireNonNull(mediaConnectionService, "mediaConnectionService cannot be null");
        this.webHistorySyncService = new WebHistorySyncService(whatsapp, lidMigrationService, abPropsService, wamService, mediaConnectionService);
        this.ackSender = Objects.requireNonNull(ackSender, "ackSender cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Routes the inbound {@code <message>} stanza through the
     * media-notify, newsletter, or normal-E2E branch (see class-level
     * javadoc for the full table) and sends the matching server-side
     * receipt after processing completes. The receipt type is
     * determined by the processing outcome: a parse failure yields a
     * {@code 487} NACK; a successful decrypt yields a delivery receipt
     * (or a bot-invoke ack for bot senders); a decrypt failure yields
     * a retry, NACK, or delivery receipt depending on the exception
     * subtype; an unhandled runtime exception yields a {@code 500}
     * NACK.
     *
     * @implNote
     * This implementation commits the
     * {@link #maybePostOfflineCountTooHigh(MessageReceiveStanza)}
     * metric immediately after a successful parse, mirroring WA Web's
     * ordering so the metric fires before any decryption work begins.
     * The transport {@code <ack class="message">} is sent unconditionally
     * up front; if the relay is muting non-essential pushes the embedder
     * should use {@link WhatsAppClient#enablePassiveMode()} rather than
     * gating the ack itself.
     */
    @Override
    public void handle(Node node) {
        var from = node.getAttributeAsJid("from").orElse(null);
        if (from == null) {
            return;
        }

        ackSender.sendAck(AckClass.MESSAGE, node);

        if ("medianotify".equals(node.getAttributeAsString("type", null))) {
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
            emitUnknownStanzaMetric(node);
            emitIncomingMessageDropFromNode(node, MessageDropReasonType.INVALID_STANZA);
            sendNack(node, "487");
            return;
        }

        maybePostOfflineCountTooHigh(stanza);

        try {
            var info = messageService.process(node);
            if (info != null) {
                storeIncomingMessage(info);
                if (info instanceof ChatMessageInfo chatInfo) {
                    handleProtocolMessage(chatInfo);
                    emitMessageReceiveForChatMessage(chatInfo, stanza);
                    emitStructuredMessageReceiveIfApplicable(stanza);
                }
                resolveOrphanPayment(info);
                var quoted = whatsapp.store().findQuotedMessage(info);
                notifyMessageReceived(info, quoted);
            }

            if (info == null) {
                if (receiptHandler.isBotSender(stanza)) {
                    receiptHandler.sendBotInvokeResponseAck(stanza);
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
            emitIncomingMessageDropFromStanza(stanza, exception);
            emitMdBadDeviceSentMessageIfApplicable(stanza, exception);
            handleReceiveFailure(stanza, exception);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Incoming message {0} failed unexpectedly: {1}",
                    stanza.id(),
                    exception.getMessage());
            emitIncomingMessageDropFromStanza(stanza, null);
            sendNack(node, "500");
        }
    }

    /**
     * Processes inbound newsletter message stanzas that the main
     * {@link #handle(Node)} branch routes here because their {@code from}
     * JID lives on a newsletter server.
     *
     * @apiNote
     * Used for {@code message} stanzas published on a channel the
     * current account follows; the payload is plaintext and no
     * end-to-end decryption runs.
     *
     * @implNote
     * This implementation does not send a delivery receipt because
     * newsletter messages are server-fanned-out plaintext content. A
     * runtime exception during processing is converted to an
     * {@link com.github.auties00.cobalt.wam.event.IncomingMessageDropEvent}
     * with {@link MessageDropReasonType#INVALID_PROTOBUF} and
     * {@link E2eDestination#CHANNEL} matching the WA Web emission for
     * a {@code MessageValidationError} on the channel pipeline.
     *
     * @param node the inbound newsletter {@code <message>} stanza
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
            if (info instanceof NewsletterMessageInfo newsletterInfo) {
                emitMessageReceiveForNewsletterMessage(newsletterInfo);
            }
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle newsletter message stanza: {0}",
                    exception.getMessage());
            wamService.commit(new IncomingMessageDropEventBuilder()
                    .messageDropReason(MessageDropReasonType.INVALID_PROTOBUF)
                    .e2eDestination(E2eDestination.CHANNEL)
                    .build());
        }
    }

    /**
     * Selects and dispatches the server-side receipt for a stanza whose
     * decryption raised a {@link WhatsAppMessageException.Receive}.
     *
     * @apiNote
     * Picks among "no receipt"
     * ({@link WhatsAppMessageException.Receive.HsmMismatch}, matching
     * WA Web's silent drop), {@code NACK}
     * ({@link WhatsAppMessageException.Receive#errorCode()} is
     * non-empty), retry
     * ({@link WhatsAppMessageException.Receive#shouldSendRetryReceipt()}
     * is true; also fires the high-retry-count WAM metric), and a
     * fall-back delivery receipt or bot-invoke ack.
     *
     * @implNote
     * The retry-count increment runs locally because Cobalt's stanza
     * model does not mutate the parsed {@link MessageReceiveStanza}
     * itself.
     *
     * @param stanza    the parsed inbound stanza whose decryption
     *                  failed
     * @param exception the decryption failure
     */
    private void handleReceiveFailure(
            MessageReceiveStanza stanza,
            WhatsAppMessageException.Receive exception
    ) {
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
     * Commits a {@code MessageHighRetryCount} WAM event when the
     * post-increment retry count reaches the
     * {@link #MAX_MESSAGE_RETRY_COUNT} threshold.
     *
     * @apiNote
     * Surfaces the message-retry-storm telemetry that
     * {@code WAWebPostMessageHighRetryCountMetric} uses to flag chats
     * where Signal session negotiation keeps failing. The metric is
     * committed exactly once per retry attempt at or above the
     * threshold.
     *
     * @implNote
     * This implementation populates the
     * {@code retryCount}, {@code messageType},
     * {@code e2eSenderType}, and {@code encryptionType} properties
     * that are derivable from the parsed stanza alone. The
     * {@code deviceSizeBucket} (groups only) property is left absent
     * because Cobalt has no equivalent of
     * {@code WAWebWamGroupMetricCache.getGroupMetrics}; WA Web also
     * omits the property when the cached metric is unavailable, so
     * the omission is parity-preserving.
     *
     * @param stanza     the parsed inbound stanza
     * @param retryCount the post-increment retry attempt number
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

        wamService.commit(builder.build());
    }

    /**
     * Commits an {@link com.github.auties00.cobalt.wam.event.OfflineCountTooHighEvent}
     * when the parsed stanza carries an {@code offline} attribute at or
     * above {@link #OFFLINE_COUNT_TOO_HIGH_THRESHOLD}.
     *
     * @apiNote
     * Surfaces the queue-depth alarm WA Web posts during offline
     * delivery: every parsed stanza whose {@code offline} attribute
     * parses to an integer at or above the threshold contributes one
     * event so the server can detect clients that fell far enough
     * behind to risk dropping events.
     *
     * @implNote
     * This implementation populates {@code offlineCount},
     * {@code stanzaType} (hard-coded to {@link StanzaType#MESSAGE}
     * because the helper is reachable only from the message
     * dispatcher), {@code mediaType} (via
     * {@link #mapEncMediaTypeToWamMediaType(String, String, String)}),
     * {@code messageType}, {@code e2eSenderType}, and
     * {@code encryptionType}. The four spec properties that apply only
     * to call/notification/receipt stanzas
     * ({@code callStanzaType}, {@code invisibleMessageCategory},
     * {@code notificationStanzaType}, {@code receiptStanzaType}) are
     * intentionally absent, matching WA Web's emission site.
     *
     * @param stanza the parsed inbound message stanza
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
     * Maps the {@code (encMediaType, stanzaType, pollType)} triple
     * extracted from an inbound stanza onto the WAM {@link MediaType}
     * enum.
     *
     * @apiNote
     * Drives the {@code mediaType} property of every WAM metric that
     * needs to classify the inbound payload, including
     * {@link com.github.auties00.cobalt.wam.event.OfflineCountTooHighEvent}.
     *
     * @implNote
     * This implementation honours the same precedence as
     * {@code WAWebBackendJobsCommon.getMetricMediaType}: reaction and
     * medianotify stanza types win over poll types, poll creation and
     * vote win over the enc media type, and the enc media type drives
     * the remaining cases. Any unrecognised triple returns
     * {@link MediaType#NONE}, matching WA Web's default branch.
     *
     * @param encMediaType the first non-{@code null} {@code mediatype}
     *                     attribute among the stanza's enc payloads,
     *                     or {@code null} when none carry one
     * @param stanzaType   the stanza's top-level {@code type}
     *                     attribute, or {@code null}
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
     * Commits an
     * {@link com.github.auties00.cobalt.wam.event.IncomingMessageDropEvent}
     * for a drop that occurred before the stanza could even be parsed
     * into a {@link MessageReceiveStanza}.
     *
     * @apiNote
     * Surfaces the pre-parse drop emission triggered when
     * {@link MessageReceiveStanzaParser#parse} throws.
     *
     * @implNote
     * This implementation only populates the {@code offline} and
     * {@code offlineCount} properties because Cobalt has no equivalent
     * of WA Web's {@code incomingMsgParserForMetric} fall-back parser
     * that extracts best-effort metadata from a stanza that could not
     * be fully parsed; WA Web also leaves the remaining properties
     * absent when its metric parser fails.
     *
     * @param node              the raw inbound stanza node
     * @param messageDropReason the drop reason to record on the event
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
     * Commits an
     * {@link com.github.auties00.cobalt.wam.event.UnknownStanzaEvent}
     * for a stanza whose top-level shape did not parse.
     *
     * @apiNote
     * Surfaces the WA Web emission point where a stanza tag plus type
     * are recorded so the server can detect bundles that send shapes
     * the client cannot yet understand.
     *
     * @implNote
     * This implementation leaves {@code unknownStanzaDropReason} unset,
     * matching {@code WAWebPostUnknownStanzaMetric.postUnknownStanzaMetric}
     * which never populates it at this call site.
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
     * Commits an
     * {@link com.github.auties00.cobalt.wam.event.IncomingMessageDropEvent}
     * for a drop that occurred while processing an already-parsed
     * stanza.
     *
     * @apiNote
     * Surfaces the per-decrypt-slot drop telemetry WA Web emits from
     * {@code WAWebMsgProcessingDecryptionHandler.k} when an inbound
     * decrypt outcome is non-success and not a benign skip; the drop
     * reason is mapped from the exception subtype, and the event
     * carries the {@code offlineCount}, {@code retryCount},
     * {@code e2eCiphertextType}, and {@code e2eDestination} properties
     * derivable from the stanza alone.
     *
     * @implNote
     * This implementation skips emission entirely for the exception
     * subtypes WA Web ignores ({@link WhatsAppMessageException.Receive.HsmMismatch},
     * {@link WhatsAppMessageException.Receive.BroadcastEphemeralSettings},
     * {@link WhatsAppMessageException.Receive.DuplicateMessage},
     * {@link WhatsAppMessageException.Receive.UnknownDevice}, and the
     * full set of Signal-level pre/key-related faults). When the
     * {@code exception} argument is {@code null} the drop reason is
     * {@link MessageDropReasonType#INTERNAL_ERROR}, matching
     * {@code postIncomingMessageDropInternalError}.
     *
     * @param stanza    the parsed inbound stanza
     * @param exception the receive exception that triggered the drop,
     *                  or {@code null} for an internal unhandled error
     */
    private void emitIncomingMessageDropFromStanza(
            MessageReceiveStanza stanza,
            WhatsAppMessageException.Receive exception
    ) {
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

        builder.offline(stanza.isOffline());
        stanza.offline().ifPresent(raw -> {
            try {
                builder.offlineCount(Integer.parseInt(raw));
            } catch (NumberFormatException _) {
            }
        });

        var encs = stanza.encs();
        if (!encs.isEmpty()) {
            var firstEnc = encs.getFirst();
            builder.retryCount(firstEnc.retryCount());

            builder.e2eCiphertextType(mapCiphertextTypeForDrop(firstEnc.e2eType()));
        }

        var destination = mapDestination(stanza);
        if (destination != null) {
            builder.e2eDestination(destination);
        }

        // TODO: surface stanza.category() to WAM's invisibleMessageCategory and
        //       propagate an e2eFailureReason from the receive exception hierarchy
        //       instead of leaving both fields absent.

        wamService.commit(builder.build());
    }

    /**
     * Commits an
     * {@link com.github.auties00.cobalt.wam.event.MdBadDeviceSentMessageEvent}
     * when the current receive failure is a device-sent-message
     * validation error.
     *
     * @apiNote
     * Surfaces the WAM event WA Web emits from the
     * {@code WAWebHandleMsgError.DeviceSentMessageError} constructor;
     * the event records the broken DSM ({@code peerType}, {@code dsmError})
     * so the server can detect companion devices that ship malformed
     * device-sent envelopes.
     *
     * @implNote
     * This implementation derives {@code peerType} from
     * {@link Jid#device()}: zero (the {@code DEFAULT_DEVICE_ID}) maps
     * to {@link DeviceType#PRIMARY}, any other value to
     * {@link DeviceType#COMPANION}. The remaining event-spec
     * properties ({@code editType}, {@code encryptionType},
     * {@code isLid}, {@code mediaType}, {@code messageType},
     * {@code revokeType}) are left absent because the WA Web
     * emission site populates only {@code peerType} and
     * {@code dsmError}.
     *
     * @param stanza    the parsed inbound stanza whose decrypt failed
     * @param exception the receive exception that triggered the drop
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgError", exports = "DeviceSentMessageError",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitMdBadDeviceSentMessageIfApplicable(
            MessageReceiveStanza stanza,
            WhatsAppMessageException.Receive exception
    ) {
        if (!(exception instanceof WhatsAppMessageException.Receive.InvalidDeviceSentMessage dsmException)) {
            return;
        }

        var peerType = stanza.senderJid().device() == 0
                ? DeviceType.PRIMARY
                : DeviceType.COMPANION;

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
     * Commits a
     * {@link com.github.auties00.cobalt.wam.event.MessageReceiveEvent}
     * for a successfully decrypted E2E chat message.
     *
     * @apiNote
     * Surfaces the per-message WAM emission that
     * {@code WAWebLogReceivedMessages.logReceivedMessagesInWAM} runs
     * once per decrypted message in an incoming batch. The event
     * carries the typing, content, addressing, ephemerality, and
     * timing metadata the server uses to debug delivery and adoption
     * regressions.
     *
     * @implNote
     * This implementation populates the subset of properties Cobalt
     * can derive directly from the parsed
     * {@link MessageReceiveStanza}, the decoded {@link ChatMessageInfo},
     * and the store. The WA-Web-specific properties Cobalt does not
     * track ({@code deviceCount}, {@code deviceSizeBucket},
     * {@code oppositeVisibleIdentification}, {@code hasUsername},
     * {@code hasUsernamePin}, vcard {@code received*ContactSize},
     * sticker {@code stickerIs*}/{@code stickerMakerSourceType},
     * {@code invisibleMessageCategory}, {@code pairedMediaType},
     * {@code privateAiFeatureName}, {@code traceIdInt},
     * {@code appContext}, {@code stanzaProcessCount},
     * {@code processingDeferred}, {@code isPq}) are intentionally
     * absent, matching WA Web's behaviour when the upstream source is
     * unavailable. The three {@code messageReceiveT*} timers are
     * encoded as elapsed milliseconds via
     * {@link Instant#ofEpochMilli}: {@code T0} is the server-to-client
     * latency ({@code clientReceivedTs - serverTs}), {@code T1} and
     * {@code T2} are zeroed exactly as WA Web does at this call site.
     *
     * @param info   the decoded chat message info
     * @param stanza the parsed inbound stanza carrying timestamps,
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
            }
        });

        builder.isViewOnce(isViewOnceMessage(info.message()));

        var contextInfo = extractContextInfo(info.message()).orElse(null);
        if (contextInfo != null) {
            builder.isForwardedForward(contextInfo.forwardingScore().orElse(0) > 1);
            builder.isAReply(contextInfo.quotedMessageId().isPresent());

            contextInfo.disappearingMode().ifPresent(mode -> applyDisappearingMode(builder, mode));
        } else {
            builder.isForwardedForward(false);
            builder.isAReply(false);
        }

        var editType = resolveEditType(stanza, info);
        if (editType != null) {
            builder.editType(editType);
        }

        // TODO: surface BizBotType / BizBotAutomatedType so the bot-type
        //       classification is not collapsed to METABOT for every bot sender.
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

        // TODO: classify subgroup vs community vs plain group so typeOfGroup
        //       is more than a single GROUP bucket for every group/community chat.
        if (stanza.chatJid().hasGroupOrCommunityServer()) {
            builder.typeOfGroup(TypeOfGroupEnum.GROUP);
        }

        stanza.addressingMode()
                .flatMap(MessageStreamHandler::mapAddressingMode)
                .ifPresent(builder::serverAddressingMode);

        wamService.commit(builder.build());
    }

    /**
     * Commits a
     * {@link com.github.auties00.cobalt.wam.event.StructuredMessageReceiveEvent}
     * when the inbound chat message is a galaxy-flow CTA or a payment-
     * request native-flow interactive message.
     *
     * @apiNote
     * Surfaces two WA Web sibling emissions that fire from
     * {@code WAWebLogReceivedMessages.logReceivedMessagesInWAM}:
     * {@code WAWebGalaxyFlowWamLoggerUtils.logStructuredMessageReceivedWAMEvent}
     * (for {@code nativeFlowName == "galaxy_message"}) and
     * {@code WAWebPaymentRequestWamLogger.logPaymentRequestReceivedWAMEvent}
     * (for {@code nativeFlowName == "payment_request"}). Both emit the
     * same event class with {@code messageClass=BUTTON_NFM},
     * {@code bizPlatform=CLOUDAPI}, and the sender's business JID; they
     * differ only in {@code messageMediaType} ({@link MediaType#NONE}
     * for CTA-flow, {@link MediaType#INTERACTIVE_NFM} for payment
     * request) and in the {@code messageClassAttributes} JSON payload.
     *
     * @implNote
     * This implementation leaves {@code messageClassAttributes} absent
     * because the upstream helpers
     * ({@code WAWebGetGalaxyFlowCtaButton.getGalaxyFlowCtaButton},
     * {@code WAWebBrPaymentRequest.parsePaymentRequestButton},
     * {@code P2XFunnelIdGenerator.genFunnelInfo}) and the per-
     * conversation CTWA entry-point state they consume are not modelled
     * in Cobalt. WA Web also omits this field when its helpers yield
     * {@code null}, so the omission is parity-preserving rather than a
     * divergence.
     *
     * @param stanza the parsed inbound stanza carrying the biz
     *               {@code nativeFlowName} attribute and the sender JID
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
        var nativeFlowName = stanza.bizInfo()
                .flatMap(bi -> bi.nativeFlowName())
                .orElse(null);
        if (nativeFlowName == null) {
            return;
        }

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

        var businessOwnerJid = stanza.senderJid().toUserJid().user();

        var builder = new StructuredMessageReceiveEventBuilder()
                .messageClass(StructuredMessageClass.BUTTON_NFM)
                .messageMediaType(mediaType)
                .bizPlatform(BizPlatform.CLOUDAPI);
        if (businessOwnerJid != null) {
            builder.businessOwnerJid(businessOwnerJid);
        }

        wamService.commit(builder.build());
    }

    /**
     * Commits a
     * {@link com.github.auties00.cobalt.wam.event.MessageReceiveEvent}
     * for a successfully processed newsletter message.
     *
     * @apiNote
     * Surfaces the WAM emission WA Web triggers from
     * {@code WAWebHandleNewsletterMsg.default} when it forwards the
     * decoded channel entry through
     * {@code logReceivedMessagesInWAM}; only the properties Cobalt can
     * derive for a newsletter entry (typing, content, view-once,
     * reply/forward, chat-origins, timer fields) are populated.
     *
     * @implNote
     * This implementation skips the addressing, LID, ephemerality,
     * hosted-encryption, and group-only branches because newsletter
     * messages never carry those signals. The three
     * {@code messageReceiveT*} timer fields are zeroed, matching the
     * newsletter invocation of {@code logReceivedMessagesInWAM} which
     * omits {@code clientReceivedTsMillis} and {@code tsMillis} for
     * the channel pipeline.
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

        builder.messageReceiveT0(Instant.ofEpochMilli(0));
        builder.messageReceiveT1(Instant.ofEpochMilli(0));
        builder.messageReceiveT2(Instant.ofEpochMilli(0));

        wamService.commit(builder.build());
    }

    /**
     * Tests whether the decoded {@link MessageContainer} carries any of
     * the view-once wrappers WhatsApp ever shipped.
     *
     * @apiNote
     * Drives the {@code isViewOnce} property on the WAM
     * {@link com.github.auties00.cobalt.wam.event.MessageReceiveEvent}
     * builder; downstream listeners do not call this helper directly.
     *
     * @implNote
     * This implementation delegates to
     * {@link MessageContainer#futureProofContentType()} and checks for
     * {@link FutureProofMessageType#VIEW_ONCE}, which folds the three
     * historical view-once message shapes
     * ({@code viewOnceMessage}, {@code viewOnceMessageV2},
     * {@code viewOnceMessageV2Extension}) into one branch on the
     * Cobalt side.
     *
     * @param container the decoded message container; may be
     *                  {@code null}
     * @return {@code true} when the container carries a view-once
     *         payload; {@code false} otherwise (including
     *         {@code null} inputs)
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
     * Extracts the {@link ContextInfo} from a {@link MessageContainer}
     * when its content is a {@link ContextualMessage}.
     *
     * @apiNote
     * Surfaces the per-content {@code contextInfo} accessor that powers
     * the {@code isForwardedForward}, {@code isAReply}, and ephemerality
     * fields on the WAM
     * {@link com.github.auties00.cobalt.wam.event.MessageReceiveEvent}.
     *
     * @implNote
     * This implementation returns {@link Optional#empty()} when the
     * container is {@code null}, when the content is not a
     * {@link ContextualMessage}, or when the contextual message itself
     * has no embedded context info, mirroring the WA Web
     * {@code getNumTimesForwarded} / {@code getIsReply} accessors that
     * silently treat absent {@code contextInfo} as a no-op.
     *
     * @param container the decoded message container; may be
     *                  {@code null}
     * @return the resolved {@link ContextInfo}, or
     *         {@link Optional#empty()} when no context info is
     *         attached
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
     * Copies the three WAM ephemerality fields off a
     * {@link ChatDisappearingMode} onto a
     * {@link MessageReceiveEventBuilder}.
     *
     * @apiNote
     * Drives the {@code disappearingChatInitiator},
     * {@code ephemeralityTriggerAction}, and
     * {@code ephemeralityInitiator} properties on the WAM
     * {@link com.github.auties00.cobalt.wam.event.MessageReceiveEvent}
     * by mirroring the three
     * {@code WAWebEphemeralityWAMUtils.getWamDisappearingMode*}
     * accessors.
     *
     * @implNote
     * This implementation translates each enum constant on the Cobalt
     * side ({@link ChatDisappearingMode.Initiator},
     * {@link ChatDisappearingMode.Trigger}) into its WAM counterpart
     * via an exhaustive {@code switch}. The {@code initiatedByMe}
     * boolean is collapsed onto two
     * {@link EphemeralityInitiatorType} values, matching the binary
     * choice WA Web makes at this call site.
     *
     * @param builder the event builder to populate in place
     * @param mode    the disappearing-mode descriptor carried by the
     *                inbound message's context info
     */
    @WhatsAppWebExport(moduleName = "WAWebEphemeralityWAMUtils", exports = "getWamDisappearingModeInitiator",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebEphemeralityWAMUtils", exports = "getWamDisappearingModeTrigger",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebEphemeralityWAMUtils", exports = "getWamDisappearingModeInitiatedByMe",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static void applyDisappearingMode(MessageReceiveEventBuilder builder, ChatDisappearingMode mode) {
        mode.initiator().ifPresent(initiator -> {
            var mapped = switch (initiator) {
                case CHANGED_IN_CHAT -> DisappearingChatInitiatorType.CHAT;
                case INITIATED_BY_ME -> DisappearingChatInitiatorType.INITIATED_BY_ME;
                case INITIATED_BY_OTHER -> DisappearingChatInitiatorType.INITIATED_BY_OTHER;
                case BIZ_UPGRADE_FB_HOSTING -> DisappearingChatInitiatorType.BIZ_UPGRADE_FB_HOSTING;
            };
            builder.disappearingChatInitiator(mapped);
        });

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

        builder.ephemeralityInitiator(mode.initiatedByMe()
                ? EphemeralityInitiatorType.INITIATED_BY_ME
                : EphemeralityInitiatorType.INITIATED_BY_OTHER);
    }

    /**
     * Resolves the {@link EditType} for a parsed inbound stanza,
     * combining the stanza-level {@code edit} attribute with the
     * embedded {@link ProtocolMessage.Type} subtype.
     *
     * @apiNote
     * Drives the {@code editType} property on the
     * {@link com.github.auties00.cobalt.wam.event.MessageReceiveEvent},
     * mirroring {@code WAWebMsgGetters.getWamEditType}.
     *
     * @implNote
     * This implementation checks the stanza's {@code edit} attribute
     * first ({@code EDIT_MESSAGE}, {@code EDIT_PIN},
     * {@code EDIT_SENDER_REVOKE}, {@code EDIT_ADMIN_REVOKE}) and falls
     * back to the protocol-message type so revokes carried inside a
     * protocol message (typical for sender revokes) still surface
     * correctly.
     *
     * @param stanza the parsed inbound stanza
     * @param info   the decoded chat message info
     * @return the resolved {@link EditType}, or {@code null} when the
     *         message is neither edited nor revoked
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
     * Resolves the {@link RevokeType} for a parsed inbound stanza when
     * the message is a revoke.
     *
     * @apiNote
     * Drives the {@code revokeType} property on the
     * {@link com.github.auties00.cobalt.wam.event.MessageReceiveEvent}
     * via the equivalent ternary inside
     * {@code WAWebLogReceivedMessages.logReceivedMessagesInWAM}.
     *
     * @implNote
     * This implementation routes the stanza's {@code edit} attribute
     * to {@link RevokeType#ADMIN} for {@code admin_revoke} and
     * {@link RevokeType#SENDER} for {@code sender_revoke}, then falls
     * back to {@link RevokeType#SENDER} when the embedded protocol
     * message itself is a {@link ProtocolMessage.Type#REVOKE}.
     *
     * @param stanza the parsed inbound stanza
     * @param info   the decoded chat message info
     * @return the matching {@link RevokeType}, or
     *         {@link Optional#empty()} when the message is not a revoke
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
     * Converts the stanza-level {@code addressing_mode} attribute into
     * its WAM {@link AddressingMode} counterpart.
     *
     * @apiNote
     * Drives the {@code serverAddressingMode} property on the
     * {@link com.github.auties00.cobalt.wam.event.MessageReceiveEvent}.
     *
     * @implNote
     * This implementation accepts only the two values WA Web emits
     * ({@code "pn"} and {@code "lid"}) and returns
     * {@link Optional#empty()} for everything else, including
     * {@code null} inputs.
     *
     * @param raw the raw attribute value ({@code "pn"} or
     *            {@code "lid"}); may be {@code null}
     * @return the matching enum constant, or
     *         {@link Optional#empty()} for unrecognised or
     *         {@code null} inputs
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
     * Resolves the {@link MessageDropReasonType} for a given inbound
     * stanza and receive exception.
     *
     * @apiNote
     * Backs {@link #emitIncomingMessageDropFromStanza} by mirroring the
     * {@code DecryptionErrorType} switch inside
     * {@code WAWebMsgProcessingDecryptionHandler.createDecryptionHandler};
     * the resolved reason becomes the {@code messageDropReason}
     * property of the committed
     * {@link com.github.auties00.cobalt.wam.event.IncomingMessageDropEvent}.
     *
     * @implNote
     * This implementation reproduces three special cases in order:
     * status-broadcast stanzas older than 24 hours always map to
     * {@link MessageDropReasonType#EXPIRED}; a {@code null}
     * {@code exception} maps to
     * {@link MessageDropReasonType#INTERNAL_ERROR};
     * {@link WhatsAppMessageException.Receive.InvalidProtobuf} and
     * {@link WhatsAppMessageException.Receive.InvalidDeviceSentMessage}
     * both map to {@link MessageDropReasonType#INVALID_PROTOBUF};
     * everything else (including the
     * {@link WhatsAppMessageException.Receive.InvalidMessage} branch
     * that WA Web's
     * {@code WAWebPostIncomingMessageDropMetric.postIncomingMessageDropInvalidHostedCompanionStanza}
     * special-cases) collapses to
     * {@link MessageDropReasonType#INVALID_STANZA}. The
     * hosted-companion sub-case is currently not differentiated; see
     * the TODO below.
     *
     * @param stanza    the parsed inbound stanza
     * @param exception the receive exception, or {@code null} for an
     *                  internal unhandled error
     * @return the resolved {@link MessageDropReasonType}; never
     *         {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptionHandler", exports = "createDecryptionHandler",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static MessageDropReasonType resolveDropReason(
            MessageReceiveStanza stanza,
            WhatsAppMessageException.Receive exception
    ) {
        if (stanza.chatJid().isStatusBroadcastAccount()) {
            var age = ChronoUnit.HOURS.between(stanza.timestamp(), Instant.now());
            if (age > 24) {
                return MessageDropReasonType.EXPIRED;
            }
        }

        if (exception == null) {
            return MessageDropReasonType.INTERNAL_ERROR;
        }

        if (exception instanceof WhatsAppMessageException.Receive.InvalidProtobuf
                || exception instanceof WhatsAppMessageException.Receive.InvalidDeviceSentMessage) {
            return MessageDropReasonType.INVALID_PROTOBUF;
        }

        // TODO: distinguish the hosted-companion rejection inside
        //       WhatsAppMessageException.Receive.InvalidMessage so it
        //       maps to MessageDropReasonType.INVALID_HOSTED_COMPANION_STANZA
        //       instead of the generic INVALID_STANZA fallback.
        if (exception instanceof WhatsAppMessageException.Receive.InvalidMessage) {
            return MessageDropReasonType.INVALID_STANZA;
        }

        return MessageDropReasonType.INVALID_STANZA;
    }

    /**
     * Maps a Cobalt {@link MessageEncryptionType} onto its WAM
     * {@link E2eCiphertextType} counterpart.
     *
     * @apiNote
     * Drives the {@code e2eCiphertextType} property on the
     * {@link com.github.auties00.cobalt.wam.event.IncomingMessageDropEvent}
     * by mirroring {@code WAWebBackendJobsCommon.getMetricE2eCiphertextType}.
     *
     * @param type the Signal-level ciphertext type lifted from the
     *             first {@code <enc>} child of the inbound stanza
     * @return the matching WAM {@link E2eCiphertextType}; never
     *         {@code null}
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
     * Classifies the stanza's chat JID into its WAM
     * {@link E2eDestination} bucket.
     *
     * @apiNote
     * Drives the {@code e2eDestination} property on the
     * {@link com.github.auties00.cobalt.wam.event.IncomingMessageDropEvent}
     * by mirroring
     * {@code WAWebGetMetricE2eDestination.getMetricE2eDestination}.
     *
     * @implNote
     * This implementation checks the chat-JID flavours in the same
     * order as WA Web (status broadcast first, then group/community,
     * then broadcast list, then newsletter, then user/LID) and
     * returns {@code null} for JIDs that do not fall in any tracked
     * bucket; the absent property mirrors WA Web's
     * {@code undefined}-fallthrough behaviour.
     *
     * @param stanza the parsed inbound stanza
     * @return the matching {@link E2eDestination}, or {@code null}
     *         when the chat JID does not match any tracked bucket
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
     * Sends a negative acknowledgment ({@code <ack error="...">}) for
     * an inbound message stanza.
     *
     * @apiNote
     * Emitted on parse failures ({@code "487"} matching
     * {@code NackReason.ParsingError}) and on unhandled runtime
     * failures ({@code "500"} matching
     * {@code NackReason.UnhandledError}) to signal to the server that
     * the client cannot process the stanza.
     *
     * @implNote
     * This implementation drops the NACK when either the {@code id}
     * or the {@code from} attribute is missing, and delegates the
     * string-to-int parsing of the {@code errorCode} to
     * {@link #parseErrorCode(String)} so a malformed value falls back
     * to {@code 500} rather than throwing.
     *
     * @param node      the inbound message stanza to nack
     * @param errorCode the NACK error code, in string form (matches
     *                  the integer constants on WA Web's
     *                  {@code NackReason})
     */
    private void sendNack(Node node, String errorCode) {
        ackSender.sendNack(AckClass.MESSAGE, node, parseErrorReason(errorCode));
    }

    /**
     * Parses a NACK error code from string into its integer form.
     *
     * @apiNote
     * Used by {@link #sendNack(Node, String)} and
     * {@link #handleReceiveFailure} when the receive exception's
     * {@code errorCode()} is propagated.
     *
     * @implNote
     * This implementation falls back to {@code 500}
     * ({@code NackReason.UnhandledError}) on
     * {@link NumberFormatException} rather than propagating it, so
     * the calling code always gets a usable integer.
     *
     * @param value the raw error code, as carried on the inbound
     *              stanza or on a receive exception
     * @return the parsed integer error code, or {@code 500} when
     *         {@code value} is not a valid integer
     */
    private static NackReason parseErrorReason(String value) {
        var reason = NackReason.fromCode(parseErrorCode(value));
        return reason != null ? reason : NackReason.UNHANDLED_ERROR;
    }

    /**
     * Parses an error code carried on an inbound stanza or exception
     * into its raw integer form.
     *
     * @apiNote
     * Used by {@link #parseErrorReason(String)} for the outbound
     * {@code <ack>} path and directly by the
     * {@link MessageReceiptHandler}
     * for the outbound {@code <receipt>} nack path, which carries the
     * raw integer rather than a typed reason.
     *
     * @implNote
     * Falls back to {@code 500} ({@link NackReason#UNHANDLED_ERROR})
     * on a malformed value rather than propagating the
     * {@link NumberFormatException}, so the caller always gets a
     * usable integer.
     *
     * @param value the raw error code string
     * @return the parsed integer error code, or {@code 500} when
     *         {@code value} is not a valid integer
     */
    private static int parseErrorCode(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException _) {
            return 500;
        }
    }

    /**
     * Persists a freshly processed inbound {@link MessageInfo} into the
     * appropriate {@code Store} collection.
     *
     * @apiNote
     * Routes the message to one of three buckets: newsletter messages
     * land on the per-channel newsletter entry, status broadcast
     * messages land on the {@code Store.status()} collection, and
     * normal chat messages land on the per-chat history. Chat- and
     * newsletter-level metadata ({@code unread},
     * {@code conversationTimestamp}, {@code lastMsgTimestamp},
     * {@code timestamp}) is updated in place.
     *
     * @implNote
     * This implementation lazily creates the parent chat or newsletter
     * entry on first contact. Unread counters are only incremented for
     * messages the current account did not send, matching WA Web's
     * behaviour where outbound echoes do not bump the unread counter.
     *
     * @param info the typed inbound message to persist
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
     * Fans the {@code onNewMessage}, {@code onNewStatus}, and
     * {@code onMessageReply} callbacks out to every registered
     * listener.
     *
     * @apiNote
     * The single broadcast point through which user code observes
     * inbound messages. Status broadcasts additionally fire
     * {@code onNewStatus}; replies additionally fire
     * {@code onMessageReply} with the quoted message resolved by
     * {@code Store.findQuotedMessage}.
     *
     * @implNote
     * This implementation runs each listener on its own virtual
     * thread so that one slow or blocking listener cannot stall the
     * socket-stream dispatch loop or starve other listeners.
     *
     * @param info          the inbound message info
     * @param quotedMessage the message {@code info} quotes, or
     *                      {@link Optional#empty()} when none was
     *                      resolved
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
     * Tests whether the inbound message targets the status broadcast
     * account.
     *
     * @apiNote
     * Used by {@link #storeIncomingMessage} to route status broadcasts
     * to the dedicated {@code Store.status()} collection rather than
     * to a per-chat history, and by
     * {@link #notifyMessageReceived(MessageInfo, Optional)} to decide
     * whether to fire {@code onNewStatus}.
     *
     * @param info the inbound message info
     * @return {@code true} when the {@link MessageInfo}'s parent JID
     *         is the status broadcast account; {@code false}
     *         otherwise, including when the parent JID is absent
     */
    private boolean isStatusMessage(MessageInfo info) {
        return info.key()
                .parentJid()
                .map(Jid::isStatusBroadcastAccount)
                .orElse(false);
    }

    /**
     * Resolves a previously stored orphan payment notification when
     * the message it referenced has finally arrived.
     *
     * @apiNote
     * Surfaces the back-half of the orphan-payment reconciliation
     * that Cobalt runs for inbound payments: if a payment
     * {@code <transaction>} stanza arrived before its referenced
     * {@code <message>}, the notification handler buffered it; when
     * the late message lands here it is matched and the buffered
     * payment is replayed through
     * {@link #handlePaymentTransaction(Node)}.
     *
     * @implNote
     * This implementation looks up the orphan via
     * {@code Store.removeOrphanPaymentNotification(messageId)} and
     * rebuilds the original transaction {@link Node} so that
     * {@link #handlePaymentTransaction(Node)} can run unchanged.
     * Non-chat messages are ignored because only chat payments are
     * buffered as orphans.
     *
     * @param info the inbound message info
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
     * Processes a payment {@code <transaction>} stanza by updating the
     * payment info on its target {@link ChatMessageInfo}.
     *
     * @apiNote
     * Drives the payment-message status lifecycle by attaching a
     * {@link PaymentInfo} to the message the transaction references
     * and fan-broadcasting an {@code onMessageStatus} listener
     * callback. When the message cannot be located the transaction is
     * buffered as an orphan via
     * {@code Store.addOrphanPaymentNotification} so that
     * {@link #resolveOrphanPayment(MessageInfo)} can replay it later.
     *
     * @implNote
     * This implementation derives {@code fromMe} from the sender JID
     * compared against the current account, then computes the
     * {@code remote} JID and the optional {@code participant} (set
     * only for group conversations). Payment status and txn status
     * are resolved via
     * {@link #mapPaymentStatus(String, String, boolean)} and
     * {@link #mapTxnStatus(String, String, boolean)} which mirror
     * the matching helpers in {@code WAWebPaymentStatusUtils}.
     *
     * @param transaction the {@code <transaction>} stanza node
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
     * Locates the chat message a payment transaction stanza targets.
     *
     * @apiNote
     * Backs {@link #handlePaymentTransaction(Node)} when reconciling
     * an inbound {@code <transaction>} against the store.
     *
     * @implNote
     * This implementation first tries an exact-key lookup via
     * {@code Store.findMessageByKey} and falls back to a by-id lookup
     * via {@code Store.findMessageById}; the two-stage search handles
     * cases where the constructed
     * {@link MessageKey}
     * does not match the stored key bit-for-bit (different sender
     * derivation, different remote JID flavour) but the id is still
     * unique within the chat.
     *
     * @param remote      the remote JID resolved by
     *                    {@link #handlePaymentTransaction(Node)}
     * @param participant the participant JID for group messages,
     *                    or {@code null} for direct chats
     * @param messageId   the message id carried on the
     *                    {@code <transaction>} stanza
     * @param fromMe      {@code true} when the transaction was
     *                    initiated by the current account
     * @return the matching message info, or {@code null} when no
     *         message is found
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
     * Constructs a fresh {@link PaymentInfo} initialised to
     * {@code UNKNOWN_STATUS} / {@code UNKNOWN}.
     *
     * @apiNote
     * Used by {@link #handlePaymentTransaction(Node)} when the target
     * message does not already carry a {@link PaymentInfo}, so that
     * the per-transaction setters always have an instance to mutate.
     *
     * @return a freshly initialised {@link PaymentInfo}
     */
    private PaymentInfo newPaymentInfo() {
        return new PaymentInfoBuilder()
                .status(PaymentInfo.Status.UNKNOWN_STATUS)
                .txnStatus(PaymentInfo.TxnStatus.UNKNOWN)
                .build();
    }

    /**
     * Maps a transaction-type and server-status pair onto a
     * {@link PaymentInfo.Status} value.
     *
     * @apiNote
     * Mirrors {@code WAWebPaymentStatusUtils.getPaymentWebStatus} and
     * drives the high-level
     * {@code PaymentInfo.Status} field that surfaces in the WhatsApp
     * payment UI (processing, sent, complete, refunded, expired,
     * rejected, ...).
     *
     * @implNote
     * This implementation routes through the intermediate
     * {@link PaymentMessageStatus} returned by
     * {@link #paymentMessageStatus(String, String, boolean)} so the
     * mapping stays a single source of truth.
     *
     * @param type   the {@code transaction-type} attribute, or
     *               {@code null}
     * @param status the {@code status} attribute, or {@code null}
     * @param fromMe {@code true} when the transaction originated from
     *               the current account
     * @return the mapped {@link PaymentInfo.Status}; never
     *         {@code null}
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
     * Maps a transaction-type and server-status pair onto a
     * {@link PaymentInfo.TxnStatus} value.
     *
     * @apiNote
     * Mirrors {@code WAWebPaymentStatusUtils.getPaymentTxnWebStatus}
     * and drives the fine-grained
     * {@code PaymentInfo.TxnStatus} field used by the WhatsApp
     * payment UI to distinguish between the many failure and pending
     * sub-states ({@code COLLECT_FAILED_RISK},
     * {@code REFUND_FAILED_PROCESSING}, {@code FAILED_DA_FINAL}, ...).
     *
     * @implNote
     * This implementation routes through the intermediate
     * {@link PaymentMessageStatus} returned by
     * {@link #paymentMessageStatus(String, String, boolean)} so the
     * type/status decode happens exactly once.
     *
     * @param type   the {@code transaction-type} attribute, or
     *               {@code null}
     * @param status the {@code status} attribute, or {@code null}
     * @param fromMe {@code true} when the transaction originated from
     *               the current account
     * @return the mapped {@link PaymentInfo.TxnStatus}; never
     *         {@code null}
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
     * Resolves the internal {@link PaymentMessageStatus} for an
     * inbound {@code <transaction>} stanza.
     *
     * @apiNote
     * Mirrors {@code WAWebPaymentStatusUtils.getNotificationTransactionStatus}.
     * Drives the two surface-side mappings
     * ({@link #mapPaymentStatus(String, String, boolean)} and
     * {@link #mapTxnStatus(String, String, boolean)}) so the
     * decision lives in one place.
     *
     * @implNote
     * This implementation builds the canonical
     * {@link PaymentMessageTransactionType} via
     * {@link #paymentMessageTransactionType(String, boolean)} and
     * then switches on the upper-cased server status to mirror the
     * nested {@code switch} on
     * {@code PaymentTransactionStatusServerString} inside
     * {@code WAWebPaymentStatusUtils}. Any status that fails to
     * match its transaction-type branch yields
     * {@link PaymentMessageStatus#STATUS_UNSET}, matching WA Web's
     * fall-through.
     *
     * @param type   the {@code transaction-type} attribute, or
     *               {@code null}
     * @param status the {@code status} attribute, or {@code null}
     * @param fromMe {@code true} when the transaction originated from
     *               the current account
     * @return the resolved {@link PaymentMessageStatus}; never
     *         {@code null}
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
     * Resolves the {@link PaymentMessageTransactionType} for an
     * inbound {@code <transaction>} stanza.
     *
     * @apiNote
     * Mirrors {@code WAWebPaymentStatusUtils.getPaymentTransactionType}
     * and drives every subsequent payment-status decision in the
     * stream handler.
     *
     * @implNote
     * This implementation lower-cases the {@code transaction-type}
     * attribute before matching, so casing inconsistencies on the
     * wire do not change the resolved type. When the attribute is
     * missing or unrecognised, the fall-back is
     * {@link PaymentMessageTransactionType#TYPE_P2P_SENT} or
     * {@link PaymentMessageTransactionType#TYPE_P2P_RCVD} depending
     * on {@code fromMe}, matching WA Web's default branch.
     *
     * @param type   the {@code transaction-type} attribute, or
     *               {@code null}
     * @param fromMe {@code true} when the transaction originated from
     *               the current account
     * @return the resolved {@link PaymentMessageTransactionType};
     *         never {@code null}
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
     * Dispatches an embedded {@link ProtocolMessage} to the service
     * that owns its payload kind.
     *
     * @apiNote
     * Routes the six protocol-message slots Cobalt understands:
     * <ul>
     *   <li>{@code lidMigrationMappingSyncMessage} forwards into
     *       {@link LidMigrationService} after GZIP-decoding the
     *       payload via
     *       {@link #decodeLidMappingPayload(byte[])};</li>
     *   <li>{@code peerDataOperationRequestResponseMessage} forwards
     *       into {@link #resolveSnapshotRecovery} for syncd
     *       snapshot-fatal-recovery responses;</li>
     *   <li>{@code appStateSyncKeyShare} forwards into
     *       {@link #processAppStateSyncKeyShare};</li>
     *   <li>{@code appStateSyncKeyRequest} forwards into
     *       {@link #processAppStateSyncKeyRequest};</li>
     *   <li>{@code historySyncNotification} forwards into
     *       {@link WebHistorySyncService} which downloads, decrypts,
     *       and decodes the announced chunk on its own virtual
     *       thread;</li>
     *   <li>{@code initialSecurityNotificationSettingSync} updates the
     *       {@code Store.showSecurityNotifications} preference.</li>
     * </ul>
     *
     * @implNote
     * This implementation handles the LID-migration decode locally so
     * that {@link LidMigrationService} receives an already-decoded
     * payload (or a {@code null} sentinel it can escalate as
     * {@code WhatsAppLidMigrationException.FailedToParseMappings}).
     * The {@code initialSecurityNotificationSettingSync} branch
     * replaces WA Web's
     * {@code WAWebUserPrefsNotifications.setGlobalSecurityNotifications}
     * write into {@code WAWebUserPrefsKeys.SECURITY_NOTIFICATIONS}.
     *
     * @param info the chat message info whose content is a
     *             {@link ProtocolMessage}
     */
    private void handleProtocolMessage(ChatMessageInfo info) {
        var content = info.message().content();
        if (!(content instanceof ProtocolMessage protocolMessage)) {
            return;
        }

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

        protocolMessage.historySyncNotification()
                .ifPresent(webHistorySyncService::process);

        protocolMessage.initialSecurityNotificationSettingSync()
                .ifPresent(sync -> whatsapp.store()
                        .setShowSecurityNotifications(sync.securityNotificationEnabled()));
    }

    /**
     * Processes an inbound app-state-sync key share protocol message.
     *
     * @apiNote
     * Surfaces the syncd key-share handler that lets a companion
     * device receive new app-state encryption keys from the primary
     * device. Validates per-key id lengths, then forwards the
     * accepted keys to {@link SyncKeyRotationService} which updates
     * its tracking, reschedules timeouts, and resumes any
     * collections that were blocked waiting for these keys.
     *
     * @implNote
     * This implementation logs and skips key ids whose byte-length is
     * not exactly six bytes; WA Web treats the same condition as a
     * fatal error and reports a metric, but Cobalt's error model
     * delegates fatal escalation to
     * {@code WhatsAppClientErrorHandler} via the sealed exception
     * hierarchy rather than inline metric calls.
     *
     * @param info     the chat message info carrying the key share
     * @param keyShare the {@link AppStateSyncKeyShare} payload
     */
    private void processAppStateSyncKeyShare(ChatMessageInfo info, AppStateSyncKeyShare keyShare) {
        syncKeyRotationService.logMissingKeysReceived();
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

        syncKeyRotationService.handleKeyShare(senderDeviceId, validatedKeys);
    }

    /**
     * Answers an inbound app-state-sync key request by shipping the
     * requested keys back to the requester as a peer message.
     *
     * @apiNote
     * Powers the reverse direction of the syncd key-share dance: when
     * a companion device asks the primary for keys it is missing,
     * this method assembles a peer protocol message containing the
     * subset of requested keys that the local store knows about and
     * dispatches it via
     * {@link WhatsAppClient#sendPeerMessage(Jid,
     * ChatMessageInfo)}.
     *
     * @implNote
     * This implementation packs a placeholder entry with just the
     * key id when the requested key is not locally known, mirroring
     * WA Web's behaviour of responding with an
     * {@code AppStateSyncKey} whose key data is empty so the
     * requester can detect the miss. Failures shipping the peer
     * message are demoted to a debug log because key requests are
     * fire-and-forget on the WA Web side.
     *
     * @param info    the chat message info carrying the key request
     * @param request the {@link AppStateSyncKeyRequest} listing the
     *                requested key ids
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
     * Handles a {@code COMPANION_SYNCD_SNAPSHOT_FATAL_RECOVERY} peer-
     * data response that completes a previously issued snapshot
     * recovery request.
     *
     * @apiNote
     * Used by {@link #handleProtocolMessage(ChatMessageInfo)} when a
     * primary device replies to the companion's snapshot-recovery
     * request: the decoded recovery snapshot is handed off to
     * {@link SnapshotRecoveryService#resolveRecovery} so the consumer
     * blocked on the recovery promise in {@link WebAppStateService}
     * receives the result. A
     * {@link com.github.auties00.cobalt.wam.event.NonMessagePeerDataOperationResponseEvent}
     * is committed in both the success and decode-failure paths.
     *
     * @implNote
     * This implementation decodes the recovery snapshot exactly once
     * (the consumer is given the already-decoded
     * {@link SyncdSnapshotRecovery} so it does not decode again).
     * Responses for other request types or responses received while
     * recovery is disabled are silently dropped. Decode failures
     * commit the WAM event with
     * {@code peerDataErrorCount=1} while successful resolutions
     * commit it with {@code responseCount=successResponseCount=successProcessCount=1}.
     *
     * @param response the peer-data-operation response message
     */
    private void resolveSnapshotRecovery(PeerDataOperationRequestResponseMessage response) {
        if (response.peerDataOperationRequestType().orElse(null)
                != PeerDataOperationRequestType.COMPANION_SYNCD_SNAPSHOT_FATAL_RECOVERY) {
            return;
        }

        if (!snapshotRecoveryService.isRecoveryEnabled()) {
            return;
        }

        var results = response.peerDataOperationResult();
        if (results.isEmpty()) {
            return;
        }

        var recovery = results.getFirst().syncdSnapshotFatalRecoveryResponse().orElse(null);
        if (recovery == null) {
            return;
        }

        var sessionId = response.stanzaId().orElse(null);

        SyncdSnapshotRecovery decoded;
        try {
            decoded = snapshotRecoveryService.decodeRecoverySnapshot(recovery);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to decode snapshot recovery payload: {0}",
                    exception.getMessage());
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
     * Decodes the GZIP-compressed protobuf payload that ships inside a
     * LID migration mapping sync protocol message.
     *
     * @apiNote
     * Backs the {@code lidMigrationMappingSyncMessage} branch of
     * {@link #handleProtocolMessage(ChatMessageInfo)} so that
     * {@link LidMigrationService#processProtocolMessage} receives a
     * typed payload rather than raw GZIP bytes.
     *
     * @implNote
     * This implementation returns {@link Optional#empty()} on every
     * failure mode (null bytes, empty bytes, GZIP failure, protobuf
     * failure) and demotes the failure to a warning log;
     * {@link LidMigrationService} will then surface the empty payload
     * as a {@code WhatsAppLidMigrationException.FailedToParseMappings}.
     *
     * @param payload the GZIP-compressed protobuf payload bytes
     * @return the decoded
     *         {@link LIDMigrationMappingSyncPayload}, or
     *         {@link Optional#empty()} when the payload could not be
     *         decoded
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
