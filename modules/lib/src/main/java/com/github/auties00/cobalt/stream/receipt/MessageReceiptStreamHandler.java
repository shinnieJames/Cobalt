package com.github.auties00.cobalt.stream.receipt;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.device.DeviceConstants;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.MessageReceipt;
import com.github.auties00.cobalt.model.message.MessageReceiptBuilder;
import com.github.auties00.cobalt.model.message.MessageStatus;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.MdRetryFromUnknownDeviceEventBuilder;
import com.github.auties00.cobalt.wam.event.ReceiptStanzaReceiveEventBuilder;
import com.github.auties00.cobalt.wam.type.DeviceType;
import com.github.auties00.cobalt.wam.type.MessageType;
import com.github.auties00.cobalt.wam.type.ReceiptStanzaStage;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import com.github.auties00.libsignal.state.SignalPreKeyBundleBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Handles incoming message receipt stanzas from WhatsApp.
 * <p>
 * Parses receipt nodes into typed receipt objects and processes them by updating
 * message status and receipt metadata in the local store. Also handles retry
 * receipt requests by re-sending the originally encrypted message.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleMsgReceipt")
@WhatsAppWebModule(moduleName = "WAWebHandleMsgReceiptParser")
@WhatsAppWebModule(moduleName = "WAWebHandleMessageRetryRequest")
@WhatsAppWebModule(moduleName = "WAWebHandleRetryRequest")
@WhatsAppWebModule(moduleName = "WAWebRetryRequestParser")
public final class MessageReceiptStreamHandler implements SocketStream.Handler {
    /**
     * Logger instance for this handler.
     */
    private static final System.Logger LOGGER = System.getLogger(MessageReceiptStreamHandler.class.getName());

    /**
     * Maximum number of retry attempts that will be honoured before refusing further retries.
     */
    private static final int MAX_RETRY = 5;

    /**
     * The WhatsApp client instance providing access to the store and networking.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Service responsible for sending and re-sending messages.
     */
    private final MessageService messageService;

    /**
     * The WAM telemetry service used to commit receipt-related events.
     */
    private final WamService wamService;

    /**
     * Constructs a new message receipt stream handler.
     *
     * @param whatsapp       the WhatsApp client instance
     * @param messageService the message service for re-sending on retry
     * @param wamService     the WAM telemetry service for committing receipt events
     */
    public MessageReceiptStreamHandler(WhatsAppClient whatsapp, MessageService messageService, WamService wamService) {
        this.whatsapp = whatsapp;
        this.messageService = messageService;
        this.wamService = wamService;
    }

    /**
     * Handles an incoming receipt stanza node.
     * <p>
     * If the receipt is a retry request, delegates to {@link #handleRetryReceipt(Node)}.
     * Otherwise, parses the receipt into a typed object and dispatches to the
     * appropriate handler based on receipt structure. When the ack type is
     * {@link ReceiptAck#CONTENT_GONE}, only an ack is sent without processing.
     *
     * @param node the incoming receipt stanza node
     */
    @Override
    public void handle(Node node) {
        if (isRetryReceipt(node)) {
            handleRetryReceipt(node);
            return;
        }

        // construct the ReceiptStanzaReceive metric with OVERALL stage and
        // default totalCount=1, and start its duration timer. Commit is deferred
        // until after parse/process, and only happens for non-offline receipts
        // (WAWebHandleMsgReceipt.b: "u==null && t(a)").
        var receiptMetric = new ReceiptStanzaReceiveEventBuilder()
                .receiptStanzaStage(ReceiptStanzaStage.OVERALL)
                .receiptStanzaTotalCount(1)
                .startReceiptStanzaDuration();

        var parsed = parseReceipt(node);
        if (parsed == null) {
            sendAck(node, null);
            return;
        }

        if (parsed instanceof SimpleReceipt simple && simple.ack() == ReceiptAck.CONTENT_GONE) {
            sendAck(node, parsed);
            commitReceiptMetric(receiptMetric, parsed);
            return;
        }

        try {
            switch (parsed) {
                case SimpleReceipt simpleReceipt -> handleSimple(simpleReceipt);
                case AggregatedByTypeReceipt aggregatedByTypeReceipt -> handleAggregatedByType(aggregatedByTypeReceipt);
                case AggregatedByMessageReceipt aggregatedByMessageReceipt -> handleAggregatedByMessage(aggregatedByMessageReceipt);
            }
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to process incoming receipt {0}: {1}",
                    node.getAttributeAsString("id", "<unknown>"),
                    exception.getMessage());
        }

        sendAck(node, parsed);
        commitReceiptMetric(receiptMetric, parsed);
    }

    /**
     * Populates the parsed-receipt-dependent fields on the supplied
     * {@link ReceiptStanzaReceiveEventBuilder} and commits the event.
     * <p>
     * Mirrors the closure returned by
     * {@code WAWebCreateReceiptStanzaReceiveMetric.createReceiptStanzaReceiveMetric}:
     * derives {@code messageType} from the {@code from} JID, sets
     * {@code receiptStanzaType} to the raw ack string (or {@code "delivery"} when
     * absent, per {@code WAWebAck.ACK_STRING.DELIVERY}), overrides the default
     * {@code receiptStanzaTotalCount} of {@code 1} with the external id count
     * for simple receipts when available, stops the duration timer, and
     * commits. The caller gates the commit on the stanza's {@code offline}
     * attribute being absent, matching WA Web's
     * {@code u==null && t(a)} guard in {@code WAWebHandleMsgReceipt.b}.
     *
     * @param builder the pre-populated event builder returned by
     *                {@link #handle(Node)}
     * @param parsed  the parsed receipt carrying {@code from},
     *                {@code ackString}, {@code offline}, and (for simple
     *                receipts) the external id list
     */
    private void commitReceiptMetric(ReceiptStanzaReceiveEventBuilder builder, ParsedReceipt parsed) {
        // attribute was absent. Cobalt records that attribute via
        // ReceiptLike.offline() (true when the <receipt> carried offline="...").
        if (parsed.offline()) {
            return;
        }

        builder.messageType(resolveMessageType(parsed.from()));

        // ACK_STRING.DELIVERY ("delivery") when ackString is null; otherwise
        // it is the raw type string when (and only when) it matches a known
        // RECEIPT_TYPES_TO_ACK entry. Unknown type strings leave the field
        // unset (WA Web's metric body: `n==null ? type=DELIVERY :
        // RECEIPT_TYPES_TO_ACK[n]!=null && (type=n)`).
        var ackString = parsed.ackString();
        if (ackString == null) {
            builder.receiptStanzaType("delivery");
        } else if (isReceiptTypeToAck(ackString)) {
            builder.receiptStanzaType(ackString);
        }

        // (receipts?.length) != null && (receiptStanzaTotalCount = receipts.length)
        // WA Web's parser only attaches a "receipts" array to aggregated-by-type
        // and aggregated-by-message receipts (each entry corresponds to one
        // <user> child). Simple receipts never populate that field, so the
        // default count of 1 is retained by WA Web even when the <list>
        // contains multiple <item>s. Cobalt mirrors that exactly.
        switch (parsed) {
            case AggregatedByTypeReceipt aggregatedByType ->
                    builder.receiptStanzaTotalCount(aggregatedByType.receipts().size());
            case AggregatedByMessageReceipt aggregatedByMessage ->
                    builder.receiptStanzaTotalCount(aggregatedByMessage.receipts().size());
            case SimpleReceipt ignored -> {
                // WA Web: leave the default totalCount of 1 in place for simple receipts
            }
        }

        builder.stopReceiptStanzaDuration();
        wamService.commit(builder.build());
    }

    /**
     * Resolves the {@link MessageType} WAM enum value for a receipt's
     * {@code from} JID, mirroring the {@code s(e)} helper in
     * {@code WAWebCreateReceiptStanzaReceiveMetric}.
     * <p>
     * The status broadcast account is checked first (because it is also a
     * broadcast-server JID), followed by group/community, broadcast, and
     * newsletter server checks. Any other JID falls through to
     * {@link MessageType#INDIVIDUAL}.
     *
     * @param from the {@code from} attribute of the incoming receipt
     *             stanza
     * @return the resolved WAM message-type classification
     */
    private static MessageType resolveMessageType(Jid from) {
        if (from == null) {
            return MessageType.INDIVIDUAL;
        }
        if (from.isStatusBroadcastAccount()) {
            return MessageType.STATUS;
        }
        if (from.hasGroupOrCommunityServer()) {
            return MessageType.GROUP;
        }
        if (from.hasBroadcastServer()) {
            return MessageType.BROADCAST;
        }
        if (from.hasNewsletterServer()) {
            return MessageType.CHANNEL;
        }
        return MessageType.INDIVIDUAL;
    }

    /**
     * Handles a simple (non-aggregated) receipt by updating each referenced message.
     * <p>
     * If the receipt is for a peer ack, it is silently ignored (peer message deletion
     * is handled elsewhere in Cobalt). For broadcast receipts from self, processing
     * is skipped.
     *
     * @param receipt the parsed simple receipt
     */
    private void handleSimple(SimpleReceipt receipt) {
        // In Cobalt, peer message deletion is handled elsewhere
        if (receipt.ack() == ReceiptAck.PEER) {
            return;
        }

        if (receipt.from().hasBroadcastServer() && receipt.participant() != null) {
            var self = whatsapp.store().jid().orElse(null);
            if (self != null && sameUser(self, receipt.participant())) {
                return;
            }
        }

        for (var externalId : receipt.externalIds()) {
            var info = findMessage(receipt.from(), receipt.participant(), externalId);
            if (info == null) {
                continue;
            }

            updateMessage(receipt, info, externalId, receipt.participant(), receipt.ack(), receipt.timestamp());
        }
    }

    /**
     * Handles an aggregated-by-type receipt by deaggregating into individual simple
     * receipt updates, one per participant.
     * <p>
     * Validates that {@link ReceiptAck#CONTENT_GONE} cannot appear in aggregated form
     * and that only group or broadcast sources are supported.
     *
     * @param receipt the parsed aggregated-by-type receipt
     */
    private void handleAggregatedByType(AggregatedByTypeReceipt receipt) {
        if (receipt.ack() == ReceiptAck.CONTENT_GONE) {
            LOGGER.log(System.Logger.Level.WARNING, "Reupload receipts cannot be aggregated");
            return;
        }

        if (!receipt.from().hasGroupOrCommunityServer() && !receipt.from().hasBroadcastServer()) {
            LOGGER.log(System.Logger.Level.WARNING, "Aggregated receipts only supported for group/broadcast");
            return;
        }

        // becomes a simple receipt with the parent's ack/ackString
        for (var participantReceipt : receipt.receipts()) {
            var info = findMessage(receipt.from(), participantReceipt.participant(), receipt.externalId());
            if (info == null) {
                continue;
            }

            updateMessage(
                    receipt,
                    info,
                    receipt.externalId(),
                    participantReceipt.participant(),
                    receipt.ack(),
                    participantReceipt.timestamp()
            );
        }
    }

    /**
     * Handles an aggregated-by-message receipt by deaggregating into individual simple
     * receipt updates, one per participant. Each participant carries its own ack type.
     *
     * @param receipt the parsed aggregated-by-message receipt
     */
    private void handleAggregatedByMessage(AggregatedByMessageReceipt receipt) {
        // uses its own ack/ackString from the per-user element
        for (var participantReceipt : receipt.receipts()) {
            var info = findMessage(receipt.from(), participantReceipt.participant(), receipt.externalId());
            if (info == null) {
                continue;
            }

            updateMessage(
                    receipt,
                    info,
                    receipt.externalId(),
                    participantReceipt.participant(),
                    participantReceipt.ack(),
                    participantReceipt.timestamp()
            );
        }
    }

    /**
     * Handles a retry receipt request by processing any included key bundle
     * and re-sending the original message.
     * <p>
     * Sends an ack with the retry type regardless of whether re-send succeeds.
     *
     * @param node the retry receipt stanza node
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMessageRetryRequest",
            exports = "handleMessageRetryRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleRetryReceipt(Node node) {
        try {
            processRetryRequest(node);
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Processed retry receipt request type={0} id={1}",
                    node.getAttributeAsString("type", null),
                    node.getAttributeAsString("id", null));
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to inspect retry receipt {0}: {1}",
                    node.getAttributeAsString("id", "<unknown>"),
                    exception.getMessage());
        } finally {
            sendRetryAck(node);
        }
    }

    /**
     * Processes the internals of a retry receipt request.
     * <p>
     * Extracts the retry count and original message ID from the {@code <retry>} child,
     * validates the retry count against {@link #MAX_RETRY}, processes any included
     * key bundle, and then attempts to re-send the original message.
     *
     * @param node the retry receipt stanza node
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleRetryRequest",
            exports = "handleRetryRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void processRetryRequest(Node node) {
        // which Cobalt does not implement. Skip non-regular retry types.
        var type = node.getAttributeAsString("type", null);
        if ("enc_rekey_retry".equals(type) || "voip_1x1_retry".equals(type)) {
            return;
        }

        var from = node.getAttributeAsJid("from").orElse(null);
        var participant = node.getAttributeAsJid("participant").orElse(null);
        var retryNode = node.getChild("retry").orElse(null);
        var originalId = retryNode != null ? retryNode.getAttributeAsString("id", null) : null;

        var retryCount = retryNode != null
                ? retryNode.getAttributeAsInt("count").orElse(0)
                : 0;
        if (retryCount >= MAX_RETRY) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Refusing retry attempt #{0}, exceeds max retry count {1}",
                    retryCount, MAX_RETRY);
            return;
        }

        // In the stanza layout, user/bot 1:1 retries have participant == null and the top-level
        // from is the requester device; group/broadcast retries carry the device in participant
        // and the group JID in from. Using "participant ?? from" mirrors the WA Web selection.
        var requester = participant != null ? participant : from;
        if (requester == null) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "handleRetryRequest: no requester found for incoming retry request");
            return;
        }

        var deviceId = Math.max(requester.device(), 0);
        if (!isDeviceKnown(requester, deviceId)) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "handleRetryRequest: device {0} not found for {1}",
                    deviceId, requester.user());
            var offline = node.hasAttribute("offline");
            emitRetryFromUnknownDevice(deviceId, offline);
            return;
        }

        processRetryKeyBundle(node, requester);
        if (originalId == null) {
            return;
        }

        var message = findRetryMessage(from, participant, originalId);
        if (message == null || !message.key().fromMe()) {
            return;
        }

        if (message instanceof ChatMessageInfo chatMessageInfo
                && chatMessageInfo.message().content() instanceof ProtocolMessage
                && participant != null) {
            messageService.sendPeer(participant, chatMessageInfo);
            return;
        }

        var parentJid = message.key().parentJid().orElse(null);
        if (parentJid == null || isBroadcastSelfRetry(message, from, participant)) {
            return;
        }

        messageService.send(message);
    }

    /**
     * Finds the message targeted by a retry request, first looking up by the provider
     * JID and then falling back to the participant's user JID for broadcast messages.
     *
     * @param provider    the JID of the original message sender
     * @param participant the participant JID, used as fallback for broadcast lookups
     * @param id          the original message ID
     * @return the message info if found, or {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleRetryRequest",
            exports = "getTargetChat",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private MessageInfo findRetryMessage(Jid provider, Jid participant, String id) {
        var direct = whatsapp.store()
                .findMessageById(provider, id)
                .map(MessageInfo.class::cast)
                .orElse(null);
        if (direct != null) {
            return direct;
        }

        if (participant == null || provider == null || !provider.hasBroadcastServer() || provider.isStatusBroadcastAccount()) {
            return null;
        }

        return whatsapp.store()
                .findMessageById(participant.toUserJid(), id)
                .map(MessageInfo.class::cast)
                .orElse(null);
    }

    /**
     * Determines whether a retry request targets a broadcast message sent by the
     * current user, which should not be re-sent to avoid duplicate delivery.
     *
     * @param message     the message being retried
     * @param from        the JID that sent the retry request
     * @param participant the participant JID from the retry stanza
     * @return {@code true} if this is a self-retry on a broadcast
     */
    private boolean isBroadcastSelfRetry(MessageInfo message, Jid from, Jid participant) {
        var parentJid = message.key().parentJid().orElse(null);
        if (parentJid == null || !parentJid.hasBroadcastServer() || parentJid.isStatusBroadcastAccount()) {
            return false;
        }

        var sender = participant != null ? participant.toUserJid() : from != null ? from.toUserJid() : null;
        return sender != null
                && whatsapp.store().jid().map(self -> Objects.equals(self.toUserJid(), sender)).orElse(false);
    }

    /**
     * Processes a key bundle included in a retry receipt, rebuilding the Signal
     * session with the remote device using the provided pre-key material.
     *
     * <p>Mirrors the {@code WAWebRetryRequestParser} default export: the
     * {@code <registration>} child carries a 4-byte big-endian unsigned
     * registration id, the {@code <skey>} and optional {@code <key>}
     * children carry 3-byte big-endian unsigned ids in their {@code <id>}
     * sub-nodes, and the public-key / signature children carry raw 32 / 64
     * byte payloads. The parent assertion that {@code type} is
     * {@code "retry"} or {@code "enc_rekey_retry"} is performed by
     * {@link #isRetryReceipt(Node)} and {@link #processRetryRequest(Node)}.
     *
     * @param node         the retry receipt stanza node containing keys
     * @param remoteDevice the remote device JID to associate the session with
     */
    @WhatsAppWebExport(moduleName = "WAWebRetryRequestParser",
            exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    private void processRetryKeyBundle(Node node, Jid remoteDevice) {
        if (remoteDevice == null) {
            return;
        }

        var keysNode = node.getChild("keys").orElse(null);
        // <registration> content is exactly 4 raw bytes parsed as a big-endian
        // unsigned integer, NOT an ASCII number string.
        var registrationId = node.getChild("registration")
                .flatMap(Node::toContentBytes)
                .map(bytes -> convertBytesToUint(bytes, 4))
                .orElse(null);
        if (keysNode == null || registrationId == null) {
            return;
        }

        try {
            var identityKey = keysNode.getChild("identity")
                    .flatMap(Node::toContentBytes)
                    .map(SignalIdentityPublicKey::ofDirect)
                    .orElse(null);
            var signedPreKey = keysNode.getChild("skey").orElse(null);
            if (identityKey == null || signedPreKey == null) {
                return;
            }

            var builder = new SignalPreKeyBundleBuilder()
                    .registrationId(registrationId)
                    .deviceId(Math.max(remoteDevice.device(), 0))
                    .signedPreKeyId(signedPreKey.getChild("id")
                            .flatMap(Node::toContentBytes)
                            .map(bytes -> convertBytesToUint(bytes, 3))
                            .orElseThrow())
                    .signedPreKeyPublic(signedPreKey.getChild("value")
                            .flatMap(Node::toContentBytes)
                            .map(SignalIdentityPublicKey::ofDirect)
                            .orElseThrow())
                    .signedPreKeySignature(signedPreKey.getChild("signature")
                            .flatMap(Node::toContentBytes)
                            .orElseThrow())
                    .identityKey(identityKey);

            // both cases, since the session rebuild succeeds with skey alone if the server
            // omits the one-time prekey, and the bot/non-bot bifurcation only changes whether
            // the parser would throw — both branches still feed the same SignalPreKeyBundle.
            var preKey = keysNode.getChild("key").orElse(null);
            if (preKey != null) {
                //                         (or f.child("id").contentUint(3) for bot retry)
                var preKeyId = preKey.getChild("id")
                        .flatMap(Node::toContentBytes)
                        .map(bytes -> convertBytesToUint(bytes, 3))
                        .orElse(null);
                var preKeyValue = preKey.getChild("value")
                        .flatMap(Node::toContentBytes)
                        .map(SignalIdentityPublicKey::ofDirect)
                        .orElse(null);
                if (preKeyId != null && preKeyValue != null) {
                    builder.preKeyId(preKeyId);
                    builder.preKeyPublic(preKeyValue);
                }
            }

            var sessionCipher = new SignalSessionCipher(whatsapp.store());
            sessionCipher.process(remoteDevice.toSignalAddress(), builder.build());
            whatsapp.store().save();
        } catch (Throwable _) {
        }
    }

    /**
     * Converts a big-endian unsigned byte array to an int.
     *
     * <p>Mirrors the WA Web {@code WAParsableXmlNode.convertBytesToUint(bytes, byteCount)}
     * helper used by {@code WAWebRetryRequestParser} (and every smax {@code KeyIDMixin} /
     * {@code RegistrationIDMixin} parser): accumulates {@code n = n * 256 + bytes[i]} for
     * the first {@code byteCount} bytes.
     *
     * <p>Retry-receipt wire fields use this encoding instead of an ASCII number string:
     * the {@code <registration>} content is 4 bytes, {@code <skey><id></id></skey>} and
     * {@code <key><id></id></key>} contents are 3 bytes.
     *
     * @param bytes     the raw content bytes from the wire-format node
     * @param byteCount the number of leading bytes to interpret
     * @return the resulting big-endian unsigned integer
     * @throws IllegalArgumentException if {@code bytes} has fewer than {@code byteCount} bytes
     */
    @WhatsAppWebExport(moduleName = "WAParsableXmlNode",
            exports = "convertBytesToUint",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static int convertBytesToUint(byte[] bytes, int byteCount) {
        if (bytes == null || bytes.length < byteCount) {
            throw new IllegalArgumentException("Expected " + byteCount + " bytes, got " + (bytes == null ? 0 : bytes.length));
        }
        var n = 0;
        for (var i = 0; i < byteCount; i++) {
            n = (n << 8) | (bytes[i] & 0xFF);
        }
        return n;
    }

    /**
     * Determines whether the requester device is present in the cached device list
     * for the user identified by {@code requester}.
     * <p>
     * Mirrors {@code WAWebApiDeviceList.hasDevice}: the primary device (id
     * {@link DeviceConstants#PRIMARY_DEVICE_ID}) is always considered known; any
     * other device id is resolved against the device list stored for the user JID.
     *
     * @param requester the device-level JID whose presence is being checked
     * @param deviceId  the device id to locate; {@code 0} means primary
     * @return {@code true} if the device is known for the user; {@code false} otherwise
     */
    private boolean isDeviceKnown(Jid requester, int deviceId) {
        if (deviceId == DeviceConstants.PRIMARY_DEVICE_ID) {
            return true;
        }
        var deviceList = whatsapp.store().findDeviceList(requester.toUserJid()).orElse(null);
        if (deviceList == null || deviceList.deleted()) {
            return false;
        }
        return deviceList.devices().stream().anyMatch(device -> device.id() == deviceId);
    }

    /**
     * Emits the {@code MdRetryFromUnknownDevice} WAM event to signal that a retry
     * receipt referenced a device that is not present in the cached device list.
     * <p>
     * The {@code senderType} is {@code PRIMARY} when the requester device id equals
     * {@link DeviceConstants#PRIMARY_DEVICE_ID} ({@code 0}), and {@code COMPANION}
     * otherwise.
     *
     * @param deviceId the requester device id read from the retry stanza
     * @param offline  whether the original {@code <receipt>} stanza carried the
     *                 {@code offline} attribute (i.e. the retry was delivered from
     *                 the offline-processing queue)
     */
    private void emitRetryFromUnknownDevice(int deviceId, boolean offline) {
        var senderType = deviceId == DeviceConstants.PRIMARY_DEVICE_ID
                ? DeviceType.PRIMARY
                : DeviceType.COMPANION;
        wamService.commit(new MdRetryFromUnknownDeviceEventBuilder()
                .offline(offline)
                .senderType(senderType)
                .build());
    }

    /**
     * Finds a message by its JID and external ID, falling back to participant-based
     * lookup for broadcast messages.
     *
     * @param provider    the source JID (chat or group)
     * @param participant the participant JID, used for broadcast fallback
     * @param id          the message ID to search for
     * @return the message info if found, or {@code null}
     */
    private MessageInfo findMessage(Jid provider, Jid participant, String id) {
        var direct = whatsapp.store()
                .findMessageById(provider, id)
                .map(MessageInfo.class::cast)
                .orElse(null);
        if (direct != null) {
            return direct;
        }

        if (participant == null || !provider.hasBroadcastServer() || provider.isStatusBroadcastAccount()) {
            return null;
        }

        return whatsapp.store()
                .findMessageById(participant.toUserJid(), id)
                .map(MessageInfo.class::cast)
                .orElse(null);
    }

    /**
     * Updates a message's status and receipt metadata based on an incoming receipt.
     *
     * @param receipt          the receipt containing source and recipient metadata
     * @param info             the message to update
     * @param messageId        the external message ID
     * @param participantDevice the device JID of the participant
     * @param ack              the ack level of this receipt
     * @param timestamp        the timestamp of this receipt event
     */
    private void updateMessage(
            ReceiptLike receipt,
            MessageInfo info,
            String messageId,
            Jid participantDevice,
            ReceiptAck ack,
            Instant timestamp
    ) {
        var userJid = resolveReceiptUser(receipt.from(), participantDevice, receipt.recipient());
        var deviceUpdate = userJid != null
                ? resolveDeviceUpdate(messageId, userJid, participantDevice)
                : new ReceiptUpdate(List.of(), List.of());

        var nextStatus = mapStatus(ack);
        var receiptTimestamp = isDeliveryLike(ack) ? timestamp : null;
        var readTimestamp = ack == ReceiptAck.READ ? timestamp : null;
        var playedTimestamp = ack == ReceiptAck.PLAYED ? timestamp : null;
        var nextReceipts = userJid != null
                ? mergeReceipt(info, userJid, receiptTimestamp, readTimestamp, playedTimestamp, deviceUpdate.pendingDevices(), deviceUpdate.deliveredDevices())
                : info.receipts();

        switch (info) {
            case ChatMessageInfo chatMessageInfo -> {
                chatMessageInfo.setStatus(mergeStatus(chatMessageInfo.status().orElse(null), nextStatus));
                if (!nextReceipts.isEmpty()) {
                    chatMessageInfo.setReceipts(nextReceipts);
                }
            }
            case NewsletterMessageInfo newsletterMessageInfo -> {
                newsletterMessageInfo.setStatus(mergeStatus(newsletterMessageInfo.status().orElse(null), nextStatus));
                if (!nextReceipts.isEmpty()) {
                    newsletterMessageInfo.setReceipts(nextReceipts);
                }
            }
        }

        notifyMessageStatus(info);
    }

    /**
     * Sends an ack stanza in response to a regular (non-retry) receipt.
     * <p>
     * Constructs an ack node with the receipt's ID, from JID, optional participant,
     * and type string.
     *
     * @param node   the original receipt stanza node
     * @param parsed the parsed receipt, used to extract the participant for simple receipts
     */
    private void sendAck(Node node, ParsedReceipt parsed) {
        var id = node.getAttributeAsString("id", null);
        var from = node.getAttributeAsJid("from").orElse(null);
        if (id == null || from == null) {
            return;
        }

        // and only when participant differs from from
        var participant = parsed instanceof SimpleReceipt simple ? simple.participant() : null;
        var ackString = parsed != null ? parsed.ackString() : node.getAttributeAsString("type", null);

        var ack = new NodeBuilder()
                .description("ack")
                .attribute("id", id)
                .attribute("class", "receipt")
                .attribute("to", from)
                .attribute("type", ackString)
                .attribute("participant", participant != null && !Objects.equals(participant, from) ? participant : null)
                .build();
        whatsapp.sendNodeWithNoResponse(ack);
    }

    /**
     * Sends a retry-specific ack stanza.
     * <p>
     * For retry receipts, the ack type is always "retry" and the participant is
     * included as a device JID.
     *
     * @param node the retry receipt stanza node
     */
    private void sendRetryAck(Node node) {
        var id = node.getAttributeAsString("id", null);
        var from = node.getAttributeAsJid("from").orElse(null);
        if (id == null || from == null) {
            return;
        }

        var participant = node.getAttributeAsJid("participant").orElse(null);
        var ack = new NodeBuilder()
                .description("ack")
                .attribute("id", id)
                .attribute("class", "receipt")
                .attribute("to", from)
                .attribute("type", "retry")
                .attribute("participant", participant)
                .build();
        whatsapp.sendNodeWithNoResponse(ack);
    }

    /**
     * Parses an incoming receipt stanza node into a typed {@link ParsedReceipt}.
     * <p>
     * Determines the receipt structure (simple, aggregated-by-type, or
     * aggregated-by-message) and delegates to the appropriate parsing method.
     *
     * @param node the receipt stanza node
     * @return the parsed receipt, or {@code null} if essential attributes are missing
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgReceiptParser",
            exports = "msgReceiptParser", adaptation = WhatsAppAdaptation.ADAPTED)
    private ParsedReceipt parseReceipt(Node node) {
        var id = node.getAttributeAsString("id", null);
        var from = node.getAttributeAsJid("from").orElse(null);
        if (id == null || from == null) {
            return null;
        }

        var offline = node.hasAttribute("offline");
        var ackString = node.getAttributeAsString("type", null);
        var ack = ReceiptAck.fromType(ackString);

        // which overrides the ack to SENT (mapped to RECEIVED in Cobalt since SENT is not
        // a receipt-level concept)
        var errorNode = node.getChild("error").orElse(null);
        if (errorNode != null
                && "lid".equals(errorNode.getAttributeAsString("reason", null))
                && "feature-incapable".equals(errorNode.getAttributeAsString("type", null))) {
            ack = ReceiptAck.RECEIVED;
        }

        var participantsNode = node.getChild("participants").orElse(null);
        if (participantsNode == null) {
            return parseSimple(node, id, from, ack, ackString, offline);
        }

        if (participantsNode.hasAttribute("message_id")) {
            return parseAggregatedByMessage(id, from, node.getAttributeAsJid("recipient").orElse(null), ackString, participantsNode, offline);
        }

        return parseAggregatedByType(id, from, node.getAttributeAsJid("recipient").orElse(null), ack, ackString, participantsNode, offline);
    }

    /**
     * Parses a simple (non-aggregated) receipt from the stanza node.
     * <p>
     * Extracts the participant, recipient, timestamp, external IDs from any
     * {@code <list>} child, and optional business metadata from any {@code <biz>} child.
     *
     * @param node      the receipt stanza node
     * @param id        the stanza ID
     * @param from      the sender JID
     * @param ack       the resolved ack level
     * @param ackString the raw ack type string
     * @param offline   whether the receipt has the offline attribute
     * @return the parsed simple receipt
     */
    private ParsedReceipt parseSimple(
            Node node,
            String id,
            Jid from,
            ReceiptAck ack,
            String ackString,
            boolean offline
    ) {
        var participant = node.getAttributeAsJid("participant").orElse(null);

        var participantPn = node.getAttributeAsJid("participant_pn").orElse(null);

        var participantUsername = node.getAttributeAsString("participant_username", null);

        var recipient = node.getAttributeAsJid("recipient").orElse(null);

        var isLidBot = false;
        if (participant != null && participant.isBot() && node.hasAttribute("is_lid")) {
            isLidBot = "true".equals(node.getAttributeAsString("is_lid", null));
        }

        var externalIds = new ArrayList<String>();
        var listNode = node.getChild("list").orElse(null);
        var viewReceipt = "view".equals(ackString);
        if (listNode != null) {
            for (var item : listNode.getChildren("item")) {
                var childId = item.getAttributeAsString(viewReceipt ? "server_id" : "id", null);
                if (childId != null) {
                    externalIds.add(childId);
                }
            }
        }

        if (!viewReceipt) {
            externalIds.add(id);
        }

        BizInfo bizInfo = null;
        var bizNode = node.getChild("biz").orElse(null);
        if (bizNode != null) {
            var actualActors = bizNode.getAttributeAsInt("actual_actors").orElse(-1);
            var hostStorage = bizNode.getAttributeAsInt("host_storage").orElse(-1);
            var privacyModeTs = bizNode.getAttributeAsInt("privacy_mode_ts").orElse(-1);
            if (actualActors >= 0 && hostStorage >= 0 && privacyModeTs >= 0) {
                bizInfo = new BizInfo(actualActors, hostStorage, privacyModeTs);
            }
        }

        return new SimpleReceipt(
                id,
                from,
                participant,
                participantPn,
                participantUsername,
                recipient,
                attributeInstant(node, "t"),
                ack,
                ackString,
                List.copyOf(externalIds),
                offline,
                isLidBot,
                bizInfo
        );
    }

    /**
     * Parses an aggregated-by-type receipt from the participants node.
     * <p>
     * Each {@code <user>} child contributes a participant receipt with the parent's
     * ack type. The external ID comes from the {@code key} attribute of the
     * participants node.
     *
     * @param id               the stanza ID
     * @param from             the sender JID
     * @param recipient        the recipient JID, if present
     * @param ack              the resolved ack level
     * @param ackString        the raw ack type string
     * @param participantsNode the {@code <participants>} node
     * @param offline          whether the receipt has the offline attribute
     * @return the parsed aggregated receipt, or {@code null} if the key attribute is missing
     */
    private ParsedReceipt parseAggregatedByType(
            String id,
            Jid from,
            Jid recipient,
            ReceiptAck ack,
            String ackString,
            Node participantsNode,
            boolean offline
    ) {
        var externalId = participantsNode.getAttributeAsString("key", null);
        if (externalId == null) {
            return null;
        }

        var receipts = new ArrayList<ParticipantReceipt>();
        for (var userNode : participantsNode.getChildren("user")) {
            var participantJid = userNode.getAttributeAsJid("jid").orElse(null);
            if (participantJid == null) {
                continue;
            }
            var participantPn = userNode.getAttributeAsJid("participant_pn").orElse(null);
            var participantUsername = userNode.getAttributeAsString("participant_username", null);
            receipts.add(new ParticipantReceipt(
                    participantJid,
                    participantPn,
                    participantUsername,
                    attributeInstant(userNode, "t"),
                    ack,
                    ackString
            ));
        }

        return new AggregatedByTypeReceipt(id, from, recipient, null, null, ack, ackString, externalId, List.copyOf(receipts), offline);
    }

    /**
     * Parses an aggregated-by-message receipt from the participants node.
     * <p>
     * Each {@code <user>} child carries its own ack type. The external message ID
     * comes from the {@code message_id} attribute of the participants node.
     *
     * @param id               the stanza ID
     * @param from             the sender JID
     * @param recipient        the recipient JID, if present
     * @param ackString        the parent-level raw ack type string
     * @param participantsNode the {@code <participants>} node
     * @param offline          whether the receipt has the offline attribute
     * @return the parsed aggregated receipt, or {@code null} if the message_id attribute is missing
     */
    private ParsedReceipt parseAggregatedByMessage(
            String id,
            Jid from,
            Jid recipient,
            String ackString,
            Node participantsNode,
            boolean offline
    ) {
        var externalId = participantsNode.getAttributeAsString("message_id", null);
        if (externalId == null) {
            return null;
        }

        var receipts = new ArrayList<ParticipantReceipt>();
        for (var userNode : participantsNode.getChildren("user")) {
            var participantJid = userNode.getAttributeAsJid("jid").orElse(null);
            if (participantJid == null) {
                continue;
            }
            var childAckString = userNode.getAttributeAsString("type", null);
            var participantPn = userNode.getAttributeAsJid("participant_pn").orElse(null);
            var participantUsername = userNode.getAttributeAsString("participant_username", null);
            receipts.add(new ParticipantReceipt(
                    participantJid,
                    participantPn,
                    participantUsername,
                    attributeInstant(userNode, "t"),
                    ReceiptAck.fromType(childAckString),
                    childAckString
            ));
        }

        return new AggregatedByMessageReceipt(
                id,
                from,
                recipient,
                null,
                null,
                ReceiptAck.RECEIVED,
                ackString,
                externalId,
                List.copyOf(receipts),
                offline
        );
    }

    /**
     * Extracts an {@link Instant} from a node attribute containing epoch seconds.
     *
     * @param node the node to read the attribute from
     * @param key  the attribute name
     * @return the parsed instant, or {@code null} if the attribute is missing
     */
    private static Instant attributeInstant(Node node, String key) {
        return node.getAttributeAsLong(key)
                .stream()
                .mapToObj(Instant::ofEpochSecond)
                .findFirst()
                .orElse(null);
    }

    /**
     * Resolves the user JID to use for receipt tracking from the available JIDs.
     * <p>
     * Priority: participant (converted to user JID), then recipient, then from
     * (only if the from JID is a user-like server type).
     *
     * @param from        the sender JID
     * @param participant the participant device JID
     * @param recipient   the recipient JID
     * @return the resolved user JID, or {@code null} if none can be determined
     */
    private static Jid resolveReceiptUser(Jid from, Jid participant, Jid recipient) {
        if (participant != null) {
            return participant.toUserJid();
        }
        if (recipient != null) {
            return recipient.toUserJid();
        }
        if (from.hasUserServer() || from.hasLidServer() || from.hasBotServer() || from.hasHostedServer() || from.hasHostedLidServer()) {
            return from.toUserJid();
        }
        return null;
    }

    /**
     * Maps a receipt ack level to the corresponding message status.
     *
     * @param ack the receipt ack level
     * @return the corresponding message status
     */
    private static MessageStatus mapStatus(ReceiptAck ack) {
        return switch (ack) {
            case READ -> MessageStatus.READ;
            case PLAYED -> MessageStatus.PLAYED;
            case CONTENT_GONE -> MessageStatus.ERROR;
            case INACTIVE -> MessageStatus.ERROR;
            default -> MessageStatus.DELIVERED;
        };
    }

    /**
     * Determines whether a receipt ack level represents a delivery-like event
     * (not a read or played confirmation).
     *
     * @param ack the receipt ack level
     * @return {@code true} if the ack is delivery-like
     */
    private static boolean isDeliveryLike(ReceiptAck ack) {
        return ack != ReceiptAck.READ && ack != ReceiptAck.PLAYED;
    }

    /**
     * Resolves device-level delivery tracking for a message receipt.
     * <p>
     * Looks up pending receipt records for the message ID, partitions them into
     * delivered and remaining devices, and updates the store accordingly.
     *
     * @param messageId        the message ID to look up
     * @param userJid          the user JID to match against
     * @param participantDevice the specific device JID that delivered the receipt
     * @return a receipt update containing delivered and pending device lists
     */
    private ReceiptUpdate resolveDeviceUpdate(String messageId, Jid userJid, Jid participantDevice) {
        if (messageId == null || userJid == null) {
            return new ReceiptUpdate(List.of(), List.of());
        }

        var current = new LinkedHashSet<>(whatsapp.store().findReceiptRecords(messageId));
        if (current.isEmpty()) {
            return new ReceiptUpdate(List.of(), List.of());
        }

        var delivered = new LinkedHashSet<Jid>();
        var remaining = new LinkedHashSet<Jid>();
        for (var device : current) {
            if (sameUser(device, userJid) && (participantDevice == null || Objects.equals(device, participantDevice))) {
                delivered.add(device);
            } else {
                remaining.add(device);
            }
        }

        if (!delivered.isEmpty()) {
            whatsapp.store().removeReceiptRecords(messageId);
            if (!remaining.isEmpty()) {
                whatsapp.store().createOrMergeReceiptRecords(messageId, remaining);
            }
        }

        return new ReceiptUpdate(
                List.copyOf(delivered),
                remaining.stream().filter(device -> sameUser(device, userJid)).toList()
        );
    }

    /**
     * Merges a receipt event into the existing receipt list for a message.
     * <p>
     * Finds or creates a {@link MessageReceipt} for the given user JID and
     * updates timestamps and device lists. Timestamps are only updated if the
     * incoming value is later than the existing value.
     *
     * @param info             the message info containing existing receipts
     * @param userJid          the user JID to track the receipt for
     * @param receiptTimestamp  the delivery timestamp, or {@code null}
     * @param readTimestamp     the read timestamp, or {@code null}
     * @param playedTimestamp   the played timestamp, or {@code null}
     * @param pendingDevices   the list of devices still pending delivery
     * @param deliveredDevices the list of devices that have delivered
     * @return the updated receipt list
     */
    private List<MessageReceipt> mergeReceipt(
            MessageInfo info,
            Jid userJid,
            Instant receiptTimestamp,
            Instant readTimestamp,
            Instant playedTimestamp,
            List<Jid> pendingDevices,
            List<Jid> deliveredDevices
    ) {
        if (userJid == null) {
            return info.receipts();
        }

        var receipts = new ArrayList<>(info.receipts());
        MessageReceipt target = null;
        for (var receipt : receipts) {
            if (sameUser(receipt.userJid(), userJid)) {
                target = receipt;
                break;
            }
        }

        if (target == null) {
            target = new MessageReceiptBuilder()
                    .userJid(userJid)
                    .receiptTimestamp(null)
                    .readTimestamp(null)
                    .playedTimestamp(null)
                    .pendingDeviceJid(List.of())
                    .deliveredDeviceJid(List.of())
                    .build();
            receipts.add(target);
        }

        if (receiptTimestamp != null && target.receiptTimestamp().map(receiptTimestamp::isAfter).orElse(true)) {
            target.setReceiptTimestamp(receiptTimestamp);
        }
        if (readTimestamp != null && target.readTimestamp().map(readTimestamp::isAfter).orElse(true)) {
            target.setReadTimestamp(readTimestamp);
        }
        if (playedTimestamp != null && target.playedTimestamp().map(playedTimestamp::isAfter).orElse(true)) {
            target.setPlayedTimestamp(playedTimestamp);
        }
        target.setPendingDeviceJid(List.copyOf(pendingDevices));
        if (!deliveredDevices.isEmpty()) {
            var mergedDelivered = new LinkedHashSet<>(target.deliveredDeviceJid());
            mergedDelivered.addAll(deliveredDevices);
            target.setDeliveredDeviceJid(List.copyOf(mergedDelivered));
        }

        return List.copyOf(receipts);
    }

    /**
     * Merges two message statuses, keeping the higher-priority one.
     * <p>
     * Error status is replaced by any non-error incoming status. Otherwise, the
     * status with the higher ordinal value wins.
     *
     * @param current  the current message status, may be {@code null}
     * @param incoming the incoming message status, may be {@code null}
     * @return the merged status
     */
    private MessageStatus mergeStatus(MessageStatus current, MessageStatus incoming) {
        if (current == null) {
            return incoming;
        }
        if (incoming == null) {
            return current;
        }
        if (current == MessageStatus.ERROR) {
            return incoming;
        }
        if (incoming == MessageStatus.ERROR) {
            return current;
        }
        return incoming.ordinal() > current.ordinal() ? incoming : current;
    }

    /**
     * Notifies all registered listeners about a message status change.
     *
     * @param info the message whose status has changed
     */
    private void notifyMessageStatus(MessageInfo info) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onMessageStatus(whatsapp, info));
        }
    }

    /**
     * Determines whether two JIDs represent the same user, ignoring device identifiers.
     *
     * @param left  the first JID
     * @param right the second JID
     * @return {@code true} if both JIDs have the same user JID
     */
    private boolean sameUser(Jid left, Jid right) {
        return left != null
                && right != null
                && Objects.equals(left.toUserJid(), right.toUserJid());
    }

    /**
     * Determines whether a receipt stanza is a retry request.
     *
     * @param node the receipt stanza node
     * @return {@code true} if the type attribute is "retry" or "enc_rekey_retry"
     */
    private static boolean isRetryReceipt(Node node) {
        var type = node.getAttributeAsString("type", null);
        return "retry".equals(type) || "enc_rekey_retry".equals(type);
    }

    /**
     * Determines whether a raw {@code type} attribute string maps to a known
     * entry in WA Web's {@code RECEIPT_TYPES_TO_ACK} map.
     * <p>
     * Mirrors the keyset of the JS module-local object {@code u}:
     * {@code delivery, read, played, inactive, server-error, sender,
     * read-self, played-self, peer_msg}.
     *
     * @param type the raw {@code type} attribute string from the receipt stanza
     * @return {@code true} if the string is a recognized {@code RECEIPT_TYPES_TO_ACK} key
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgReceiptParser",
            exports = "RECEIPT_TYPES_TO_ACK", adaptation = WhatsAppAdaptation.ADAPTED)
    private static boolean isReceiptTypeToAck(String type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case "delivery", "read", "played", "inactive", "server-error",
                 "sender", "read-self", "played-self", "peer_msg" -> true;
            default -> false;
        };
    }

    /**
     * Holds the result of device-level delivery tracking resolution.
     *
     * @param deliveredDevices the devices that have confirmed delivery
     * @param pendingDevices   the devices still pending delivery
     */
    private record ReceiptUpdate(List<Jid> deliveredDevices, List<Jid> pendingDevices) {
    }

    /**
     * Sealed interface for all parsed receipt types.
     */
    private sealed interface ParsedReceipt extends ReceiptLike
            permits SimpleReceipt, AggregatedByTypeReceipt, AggregatedByMessageReceipt {
    }

    /**
     * Common interface for all receipt-like objects providing shared accessors.
     */
    private interface ReceiptLike {
        /**
         * Returns the sender JID.
         *
         * @return the from JID
         */
        Jid from();

        /**
         * Returns the recipient JID.
         *
         * @return the recipient JID, or {@code null}
         */
        Jid recipient();

        /**
         * Returns the receipt timestamp.
         *
         * @return the timestamp, or {@code null}
         */
        Instant timestamp();

        /**
         * Returns the raw ack type string.
         *
         * @return the ack string, or {@code null}
         */
        String ackString();

        /**
         * Returns whether this receipt arrived while offline.
         *
         * @return {@code true} if the receipt was received offline
         */
        boolean offline();
    }

    /**
     * Represents a simple (non-aggregated) receipt with a single participant
     * and potentially multiple external message IDs.
     *
     * @param stanzaId            the stanza ID
     * @param from                the sender JID
     * @param participant         the participant device JID
     * @param participantPn       the participant phone number JID
     * @param participantUsername  the participant username string
     * @param recipient           the recipient JID
     * @param timestamp           the receipt timestamp
     * @param ack                 the resolved ack level
     * @param ackString           the raw ack type string
     * @param externalIds         the list of message IDs this receipt covers
     * @param offline             whether the receipt was received offline
     * @param isLidBot            whether the participant is a LID-based bot
     * @param bizInfo             optional business metadata from the biz child
     */
    private record SimpleReceipt(
            String stanzaId,
            Jid from,
            Jid participant,
            Jid participantPn,
            String participantUsername,
            Jid recipient,
            Instant timestamp,
            ReceiptAck ack,
            String ackString,
            List<String> externalIds,
            boolean offline,
            boolean isLidBot,
            BizInfo bizInfo
    ) implements ParsedReceipt {
    }

    /**
     * Represents an aggregated-by-type receipt where all participants share the
     * same ack type from the parent stanza.
     *
     * @param stanzaId   the stanza ID
     * @param from       the sender JID
     * @param recipient  the recipient JID
     * @param participant unused, always {@code null}
     * @param timestamp  unused, always {@code null}
     * @param ack        the shared ack level
     * @param ackString  the shared raw ack type string
     * @param externalId the message ID this receipt covers
     * @param receipts   the list of per-participant receipts
     * @param offline    whether the receipt was received offline
     */
    private record AggregatedByTypeReceipt(
            String stanzaId,
            Jid from,
            Jid recipient,
            Jid participant,
            Instant timestamp,
            ReceiptAck ack,
            String ackString,
            String externalId,
            List<ParticipantReceipt> receipts,
            boolean offline
    ) implements ParsedReceipt {
    }

    /**
     * Represents an aggregated-by-message receipt where each participant carries
     * its own ack type.
     *
     * @param stanzaId   the stanza ID
     * @param from       the sender JID
     * @param recipient  the recipient JID
     * @param participant unused, always {@code null}
     * @param timestamp  unused, always {@code null}
     * @param ack        the parent-level ack (always RECEIVED)
     * @param ackString  the parent-level raw ack type string
     * @param externalId the message ID this receipt covers
     * @param receipts   the list of per-participant receipts with individual ack types
     * @param offline    whether the receipt was received offline
     */
    private record AggregatedByMessageReceipt(
            String stanzaId,
            Jid from,
            Jid recipient,
            Jid participant,
            Instant timestamp,
            ReceiptAck ack,
            String ackString,
            String externalId,
            List<ParticipantReceipt> receipts,
            boolean offline
    ) implements ParsedReceipt {
    }

    /**
     * Represents a single participant's receipt within an aggregated receipt.
     *
     * @param participant         the participant device JID
     * @param participantPn       the participant phone number JID
     * @param participantUsername  the participant username string
     * @param timestamp           the receipt timestamp for this participant
     * @param ack                 the ack level for this participant
     * @param ackString           the raw ack type string for this participant
     */
    private record ParticipantReceipt(
            Jid participant,
            Jid participantPn,
            String participantUsername,
            Instant timestamp,
            ReceiptAck ack,
            String ackString
    ) {
    }

    /**
     * Holds business-specific metadata from a receipt's {@code <biz>} child element.
     *
     * @param actualActors   the actual actors enum value
     * @param hostStorage    the host storage enum value
     * @param privacyModeTs  the privacy mode timestamp
     */
    private record BizInfo(
            int actualActors,
            int hostStorage,
            int privacyModeTs
    ) {
    }

    /**
     * Enumerates the receipt acknowledgement levels that map to WA Web's
     * {@code RECEIPT_TYPES_TO_ACK} map.
     * <p>
     * Each value corresponds to a WA Web {@code ACK} constant:
     * <ul>
     *   <li>{@code RECEIVED} = ACK.RECEIVED (2) — delivery, sender, inactive (as default)</li>
     *   <li>{@code READ} = ACK.READ (3)</li>
     *   <li>{@code PLAYED} = ACK.PLAYED (4)</li>
     *   <li>{@code CONTENT_GONE} = ACK.CONTENT_GONE (-3) — server-error</li>
     *   <li>{@code PEER} = ACK.PEER (5) — peer_msg</li>
     *   <li>{@code INACTIVE} = ACK.INACTIVE (-6) — inactive</li>
     * </ul>
     */
    private enum ReceiptAck {
        /**
         * Delivery-level acknowledgement (ACK.RECEIVED = 2).
         */
        RECEIVED,

        /**
         * Read acknowledgement (ACK.READ = 3).
         */
        READ,

        /**
         * Played acknowledgement for audio/video (ACK.PLAYED = 4).
         */
        PLAYED,

        /**
         * Content gone / server error (ACK.CONTENT_GONE = -3).
         */
        CONTENT_GONE,

        /**
         * Peer message acknowledgement (ACK.PEER = 5).
         */
        PEER,

        /**
         * Inactive acknowledgement (ACK.INACTIVE = -6).
         */
        INACTIVE;

        /**
         * Resolves a raw type string to the corresponding {@link ReceiptAck} value.
         * <p>
         * Maps the WA Web {@code RECEIPT_TYPES_TO_ACK} object:
         * {@code delivery} and {@code sender} map to {@code RECEIVED},
         * {@code read} and {@code read-self} map to {@code READ},
         * {@code played} and {@code played-self} map to {@code PLAYED},
         * {@code server-error} maps to {@code CONTENT_GONE},
         * {@code peer_msg} maps to {@code PEER},
         * {@code inactive} maps to {@code INACTIVE},
         * and {@code null} or unknown values default to {@code RECEIVED}.
         *
         * @param type the raw ack type string from the receipt stanza
         * @return the resolved ack level
         */
        @WhatsAppWebExport(moduleName = "WAWebHandleMsgReceiptParser",
                exports = "RECEIPT_TYPES_TO_ACK", adaptation = WhatsAppAdaptation.ADAPTED)
        private static ReceiptAck fromType(String type) {
            if (type == null || "delivery".equals(type) || "sender".equals(type)) {
                return RECEIVED;
            }
            return switch (type) {
                case "read", "read-self" -> READ;
                case "played", "played-self" -> PLAYED;
                case "server-error" -> CONTENT_GONE;
                case "peer_msg" -> PEER;
                case "inactive" -> INACTIVE;
                default -> RECEIVED;
            };
        }
    }
}
