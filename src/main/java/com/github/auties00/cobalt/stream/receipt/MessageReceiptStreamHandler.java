package com.github.auties00.cobalt.stream.receipt;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
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
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import com.github.auties00.libsignal.state.SignalPreKeyBundleBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Handles incoming message receipt stanzas from WhatsApp.
 * <p>
 * Parses receipt nodes into typed receipt objects and processes them by updating
 * message status and receipt metadata in the local store. Also handles retry
 * receipt requests by re-sending the originally encrypted message.
 *
 * @implNote WAWebHandleMsgReceipt.default, WAWebHandleMsgReceiptParser.msgReceiptParser,
 *           WAWebHandleMessageRetryRequest.handleMessageRetryRequest,
 *           WAWebHandleRetryRequest.handleRetryRequest,
 *           WAWebHandleRetryRequest.getTargetChat
 */
public final class MessageReceiptStreamHandler implements SocketStream.Handler {
    /**
     * Logger instance for this handler.
     *
     * @implNote WAWebHandleMsgReceipt (uses WALogger)
     */
    private static final System.Logger LOGGER = System.getLogger(MessageReceiptStreamHandler.class.getName());

    /**
     * Maximum number of retry attempts that will be honoured before refusing further retries.
     *
     * @implNote WAWebPostMessageHighRetryCountMetric.MAX_RETRY
     */
    private static final int MAX_RETRY = 5; // WAWebPostMessageHighRetryCountMetric.MAX_RETRY

    /**
     * The WhatsApp client instance providing access to the store and networking.
     *
     * @implNote Constructor-injected dependency replacing WA Web module-level imports
     */
    private final WhatsAppClient whatsapp;

    /**
     * Service responsible for sending and re-sending messages.
     *
     * @implNote Constructor-injected dependency replacing WA Web module-level imports
     */
    private final MessageService messageService;

    /**
     * Constructs a new message receipt stream handler.
     *
     * @param whatsapp       the WhatsApp client instance
     * @param messageService the message service for re-sending on retry
     * @implNote WAWebHandleMsgReceipt module-level constructor
     */
    public MessageReceiptStreamHandler(WhatsAppClient whatsapp, MessageService messageService) {
        this.whatsapp = whatsapp;
        this.messageService = messageService;
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
     * @implNote WAWebHandleMsgReceipt.default
     */
    @Override
    public void handle(Node node) {
        if (isRetryReceipt(node)) {
            handleRetryReceipt(node);
            return;
        }

        var parsed = parseReceipt(node);
        if (parsed == null) {
            sendAck(node, null);
            return;
        }

        // WAWebHandleMsgReceipt.default: CONTENT_GONE skips processing, only sends ack
        if (parsed instanceof SimpleReceipt simple && simple.ack() == ReceiptAck.CONTENT_GONE) {
            sendAck(node, parsed);
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
    }

    /**
     * Handles a simple (non-aggregated) receipt by updating each referenced message.
     * <p>
     * If the receipt is for a peer ack, it is silently ignored (peer message deletion
     * is handled elsewhere in Cobalt). For broadcast receipts from self, processing
     * is skipped.
     *
     * @param receipt the parsed simple receipt
     * @implNote WAWebHandleMsgReceipt.v (handleSimpleReceipt dispatch)
     */
    private void handleSimple(SimpleReceipt receipt) {
        // WAWebHandleMsgReceipt.v: PEER ack is handled by WAWebHandleAckPeerSimpleReceipt
        // In Cobalt, peer message deletion is handled elsewhere
        if (receipt.ack() == ReceiptAck.PEER) {
            return; // ADAPTED: WAWebHandleAckPeerSimpleReceipt.handleAckPeerSimpleReceipt
        }

        // WAWebHandleMsgReceipt.v: broadcast receipts from self are skipped
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
     * @implNote WAWebHandleMsgReceipt.S (handleAggregateReceipt)
     */
    private void handleAggregatedByType(AggregatedByTypeReceipt receipt) {
        // WAWebHandleMsgReceipt.S: CONTENT_GONE receipts cannot be aggregated
        if (receipt.ack() == ReceiptAck.CONTENT_GONE) {
            LOGGER.log(System.Logger.Level.WARNING, "Reupload receipts cannot be aggregated");
            return;
        }

        // WAWebHandleMsgReceipt.S: only group/broadcast supported for aggregated receipts
        if (!receipt.from().hasGroupOrCommunityServer() && !receipt.from().hasBroadcastServer()) {
            LOGGER.log(System.Logger.Level.WARNING, "Aggregated receipts only supported for group/broadcast");
            return;
        }

        // WAWebHandleMsgReceiptUtils.deaggregateGroupedByTypeReceipt: each participant
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
     * @implNote WAWebHandleMsgReceipt.R (handleAggregateByMessageReceipt)
     */
    private void handleAggregatedByMessage(AggregatedByMessageReceipt receipt) {
        // WAWebHandleMsgReceiptUtils.deaggregateGroupedByMessageReceipt: each participant
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
     * @implNote WAWebHandleMessageRetryRequest.handleMessageRetryRequest
     */
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
            // WAWebHandleMessageRetryRequest.handleMessageRetryRequest: builds ack with
            // class="receipt", type="retry"
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
     * @implNote WAWebHandleRetryRequest.handleRetryRequest, WAWebHandleRetryRequest.E (processRetryDetails)
     */
    private void processRetryRequest(Node node) {
        var from = node.getAttributeAsJid("from").orElse(null);
        var participant = node.getAttributeAsJid("participant").orElse(null);
        var retryNode = node.getChild("retry").orElse(null);
        var originalId = retryNode != null ? retryNode.getAttributeAsString("id", null) : null;

        // WAWebHandleRetryRequest.E (k function): check retry count against MAX_RETRY
        var retryCount = retryNode != null
                ? retryNode.getAttributeAsInt("count").orElse(0)
                : 0;
        if (retryCount >= MAX_RETRY) { // WAWebPostMessageHighRetryCountMetric.MAX_RETRY
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Refusing retry attempt #{0}, exceeds max retry count {1}",
                    retryCount, MAX_RETRY);
            return;
        }

        processRetryKeyBundle(node, participant != null ? participant : from);
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
     * @implNote WAWebHandleRetryRequest.getTargetChat (D function) combined with
     *           WAWebHandleRetryRequest.E message lookup
     */
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
     * @implNote WAWebHandleRetryRequest.getTargetChat (D function) — self-account check
     */
    private boolean isBroadcastSelfRetry(MessageInfo message, Jid from, Jid participant) {
        var parentJid = message.key().parentJid().orElse(null);
        if (parentJid == null || !parentJid.hasBroadcastServer() || parentJid.isStatusBroadcastAccount()) {
            return false;
        }

        var sender = participant != null ? participant.toUserJid() : from != null ? from.toUserJid() : null;
        return sender != null
                && whatsapp.store().jid().map(self -> java.util.Objects.equals(self.toUserJid(), sender)).orElse(false);
    }

    /**
     * Processes a key bundle included in a retry receipt, rebuilding the Signal
     * session with the remote device using the provided pre-key material.
     *
     * @param node         the retry receipt stanza node containing keys
     * @param remoteDevice the remote device JID to associate the session with
     * @implNote WAWebUpdateLocalSignalSession.updateLocalSignalSession,
     *           WAWebProcessRetryKeyBundle (key bundle extraction)
     */
    private void processRetryKeyBundle(Node node, Jid remoteDevice) {
        if (remoteDevice == null) {
            return;
        }

        var keysNode = node.getChild("keys").orElse(null);
        var registrationId = node.getChild("registration")
                .flatMap(Node::toContentInt)
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
                    .signedPreKeyId(signedPreKey.getChild("id").flatMap(Node::toContentInt).orElseThrow())
                    .signedPreKeyPublic(signedPreKey.getChild("value")
                            .flatMap(Node::toContentBytes)
                            .map(SignalIdentityPublicKey::ofDirect)
                            .orElseThrow())
                    .signedPreKeySignature(signedPreKey.getChild("signature")
                            .flatMap(Node::toContentBytes)
                            .orElseThrow())
                    .identityKey(identityKey);

            var preKey = keysNode.getChild("key").orElse(null);
            if (preKey != null) {
                var preKeyId = preKey.getChild("id").flatMap(Node::toContentInt).orElse(null);
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
        } catch (Throwable ignored) {
            // ADAPTED: WAWebHandleRetryRequest — WA Web logs warning but continues
        }
    }

    /**
     * Finds a message by its JID and external ID, falling back to participant-based
     * lookup for broadcast messages.
     *
     * @param provider    the source JID (chat or group)
     * @param participant the participant JID, used for broadcast fallback
     * @param id          the message ID to search for
     * @return the message info if found, or {@code null}
     * @implNote WAWebHandleDirectChatReceipt, WAWebHandleGroupChatReceipt — message lookup
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
     * @implNote WAWebHandleDirectChatReceipt.handleChatSimpleReceipt,
     *           WAWebHandleGroupChatReceipt.handleGroupSimpleReceipt — receipt update logic
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
     * @implNote WAWebReceiptAck.buildReceiptAck
     */
    private void sendAck(Node node, ParsedReceipt parsed) {
        var id = node.getAttributeAsString("id", null);
        var from = node.getAttributeAsJid("from").orElse(null);
        if (id == null || from == null) {
            return;
        }

        // WAWebReceiptAck.buildReceiptAck: participant is only set for simple receipts
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
     * @implNote WAWebHandleMessageRetryRequest.handleMessageRetryRequest — retry ack construction
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
     * @implNote WAWebHandleMsgReceiptParser.msgReceiptParser
     */
    private ParsedReceipt parseReceipt(Node node) {
        var id = node.getAttributeAsString("id", null);
        var from = node.getAttributeAsJid("from").orElse(null);
        if (id == null || from == null) {
            return null;
        }

        var offline = node.hasAttribute("offline"); // WAWebHandleMsgReceiptParser: offline field
        var ackString = node.getAttributeAsString("type", null);
        var ack = ReceiptAck.fromType(ackString);

        // WAWebHandleMsgReceiptParser: check for <error reason="lid" type="feature-incapable">
        // which overrides the ack to SENT (mapped to RECEIVED in Cobalt since SENT is not
        // a receipt-level concept)
        var errorNode = node.getChild("error").orElse(null);
        if (errorNode != null
                && "lid".equals(errorNode.getAttributeAsString("reason", null))
                && "feature-incapable".equals(errorNode.getAttributeAsString("type", null))) {
            ack = ReceiptAck.RECEIVED; // WAWebHandleMsgReceiptParser: overrides to ACK.SENT (=1)
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
     * @implNote WAWebHandleMsgReceiptParser.p (parseSimpleReceipt)
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

        // WAWebHandleMsgReceiptParser.p: parse participantPn from participant_pn attr
        var participantPn = node.getAttributeAsJid("participant_pn").orElse(null);

        // WAWebHandleMsgReceiptParser.p: parse participantUsername from participant_username attr
        var participantUsername = node.getAttributeAsString("participant_username", null);

        var recipient = node.getAttributeAsJid("recipient").orElse(null);

        // WAWebHandleMsgReceiptParser.p: parse isLidBot for bot participants
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

        // WAWebHandleMsgReceiptParser.p: for non-view receipts, stanzaId is appended
        if (!viewReceipt) {
            externalIds.add(id);
        }

        // WAWebHandleMsgReceiptParser.p: parse <biz> child for business metadata
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
     * @implNote WAWebHandleMsgReceiptParser.d (parseAggregatedByTypeReceipt)
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
            // WAWebHandleMsgReceiptParser.d: parse participantPn and participantUsername
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
     * @implNote WAWebHandleMsgReceiptParser.m (parseAggregatedByMessageReceipt)
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
            // WAWebHandleMsgReceiptParser.m: parse participantPn and participantUsername
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
     * @implNote WAWebHandleMsgReceiptParser — attrTime calls
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
     * @implNote WAWebHandleDirectChatReceipt, WAWebHandleGroupChatReceipt — user resolution
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
     * @implNote WAWebAck.ACK values mapped to MessageStatus enum
     */
    private static MessageStatus mapStatus(ReceiptAck ack) {
        return switch (ack) {
            case READ -> MessageStatus.READ;
            case PLAYED -> MessageStatus.PLAYED;
            case CONTENT_GONE -> MessageStatus.ERROR;
            case INACTIVE -> MessageStatus.ERROR; // WAWebAck.ACK.INACTIVE = -6
            default -> MessageStatus.DELIVERED;
        };
    }

    /**
     * Determines whether a receipt ack level represents a delivery-like event
     * (not a read or played confirmation).
     *
     * @param ack the receipt ack level
     * @return {@code true} if the ack is delivery-like
     * @implNote WAWebHandleDirectChatReceipt — receipt timestamp assignment logic
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
     * @implNote WAWebHandleDirectChatReceipt — device tracking logic
     */
    private ReceiptUpdate resolveDeviceUpdate(String messageId, Jid userJid, Jid participantDevice) {
        if (messageId == null || userJid == null) {
            return new ReceiptUpdate(List.of(), List.of());
        }

        var current = new java.util.LinkedHashSet<>(whatsapp.store().findReceiptRecords(messageId));
        if (current.isEmpty()) {
            return new ReceiptUpdate(List.of(), List.of());
        }

        var delivered = new java.util.LinkedHashSet<Jid>();
        var remaining = new java.util.LinkedHashSet<Jid>();
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
     * @implNote WAWebHandleDirectChatReceipt, WAWebHandleGroupChatReceipt — receipt merge
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
            var mergedDelivered = new java.util.LinkedHashSet<>(target.deliveredDeviceJid());
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
     * @implNote WAWebHandleDirectChatReceipt — status merge logic
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
     * @implNote WAWebHandleDirectChatReceipt — listener notification
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
     * @implNote WAWebWidFactory.asUserWidOrThrow — user-level JID comparison
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
     * @implNote WAWebRetryRequestParser — type assertion check
     */
    private static boolean isRetryReceipt(Node node) {
        var type = node.getAttributeAsString("type", null);
        return "retry".equals(type) || "enc_rekey_retry".equals(type);
    }

    /**
     * Holds the result of device-level delivery tracking resolution.
     *
     * @param deliveredDevices the devices that have confirmed delivery
     * @param pendingDevices   the devices still pending delivery
     * @implNote WAWebHandleDirectChatReceipt — device tracking result
     */
    private record ReceiptUpdate(List<Jid> deliveredDevices, List<Jid> pendingDevices) {
    }

    /**
     * Sealed interface for all parsed receipt types.
     *
     * @implNote WAWebHandleMsgReceiptParser — receipt type union
     */
    private sealed interface ParsedReceipt extends ReceiptLike
            permits SimpleReceipt, AggregatedByTypeReceipt, AggregatedByMessageReceipt {
    }

    /**
     * Common interface for all receipt-like objects providing shared accessors.
     *
     * @implNote WAWebHandleMsgReceiptParser — shared receipt fields
     */
    private interface ReceiptLike {
        /**
         * Returns the sender JID.
         *
         * @return the from JID
         * @implNote WAWebHandleMsgReceiptParser — from field
         */
        Jid from();

        /**
         * Returns the recipient JID.
         *
         * @return the recipient JID, or {@code null}
         * @implNote WAWebHandleMsgReceiptParser — recipient field
         */
        Jid recipient();

        /**
         * Returns the receipt timestamp.
         *
         * @return the timestamp, or {@code null}
         * @implNote WAWebHandleMsgReceiptParser — ts field
         */
        Instant timestamp();

        /**
         * Returns the raw ack type string.
         *
         * @return the ack string, or {@code null}
         * @implNote WAWebHandleMsgReceiptParser — ackString field
         */
        String ackString();

        /**
         * Returns whether this receipt arrived while offline.
         *
         * @return {@code true} if the receipt was received offline
         * @implNote WAWebHandleMsgReceiptParser — offline field
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
     * @implNote WAWebHandleMsgReceiptParser.p — simple receipt structure
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
     * @implNote WAWebHandleMsgReceiptParser.d — aggregated-by-type receipt structure
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
     * @implNote WAWebHandleMsgReceiptParser.m — aggregated-by-message receipt structure
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
     * @implNote WAWebHandleMsgReceiptParser.d, WAWebHandleMsgReceiptParser.m —
     *           per-user receipt fields
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
     * @implNote WAWebHandleMsgReceiptParser.p — biz child parsing,
     *           WAWebHandleMsgTypes.flow.ActualActorsEnumType,
     *           WAWebHandleMsgTypes.flow.HostStorageEnumType
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
     *
     * @implNote WAWebHandleMsgReceiptParser.RECEIPT_TYPES_TO_ACK, WAWebAck.ACK
     */
    private enum ReceiptAck {
        /**
         * Delivery-level acknowledgement (ACK.RECEIVED = 2).
         *
         * @implNote WAWebAck.ACK.RECEIVED
         */
        RECEIVED,

        /**
         * Read acknowledgement (ACK.READ = 3).
         *
         * @implNote WAWebAck.ACK.READ
         */
        READ,

        /**
         * Played acknowledgement for audio/video (ACK.PLAYED = 4).
         *
         * @implNote WAWebAck.ACK.PLAYED
         */
        PLAYED,

        /**
         * Content gone / server error (ACK.CONTENT_GONE = -3).
         *
         * @implNote WAWebAck.ACK.CONTENT_GONE
         */
        CONTENT_GONE,

        /**
         * Peer message acknowledgement (ACK.PEER = 5).
         *
         * @implNote WAWebAck.ACK.PEER
         */
        PEER,

        /**
         * Inactive acknowledgement (ACK.INACTIVE = -6).
         *
         * @implNote WAWebAck.ACK.INACTIVE
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
         * @implNote WAWebHandleMsgReceiptParser.RECEIPT_TYPES_TO_ACK
         */
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
