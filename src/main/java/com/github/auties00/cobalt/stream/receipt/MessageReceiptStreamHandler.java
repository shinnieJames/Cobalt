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

public final class MessageReceiptStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(MessageReceiptStreamHandler.class.getName());
    private final WhatsAppClient whatsapp;
    private final MessageService messageService;

    public MessageReceiptStreamHandler(WhatsAppClient whatsapp, MessageService messageService) {
        this.whatsapp = whatsapp;
        this.messageService = messageService;
    }

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

        sendAck(node, parsed.ackString());
    }

    private void handleSimple(SimpleReceipt receipt) {
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

    private void handleAggregatedByType(AggregatedByTypeReceipt receipt) {
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

    private void handleAggregatedByMessage(AggregatedByMessageReceipt receipt) {
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
            sendAck(node, node.getAttributeAsString("type", null));
        }
    }

    private void processRetryRequest(Node node) {
        var from = node.getAttributeAsJid("from").orElse(null);
        var participant = node.getAttributeAsJid("participant").orElse(null);
        var retryNode = node.getChild("retry").orElse(null);
        var originalId = retryNode != null ? retryNode.getAttributeAsString("id", null) : null;
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

    private boolean isBroadcastSelfRetry(MessageInfo message, Jid from, Jid participant) {
        var parentJid = message.key().parentJid().orElse(null);
        if (parentJid == null || !parentJid.hasBroadcastServer() || parentJid.isStatusBroadcastAccount()) {
            return false;
        }

        var sender = participant != null ? participant.toUserJid() : from != null ? from.toUserJid() : null;
        return sender != null
                && whatsapp.store().jid().map(self -> java.util.Objects.equals(self.toUserJid(), sender)).orElse(false);
    }

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
        }
    }

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

    private void sendAck(Node node, String typeOverride) {
        var id = node.getAttributeAsString("id", null);
        var from = node.getAttributeAsJid("from").orElse(null);
        if (id == null || from == null) {
            return;
        }

        var ack = new NodeBuilder()
                .description("ack")
                .attribute("id", id)
                .attribute("class", "receipt")
                .attribute("to", from)
                .attribute("type", typeOverride != null ? typeOverride : node.getAttributeAsString("type", null))
                .attribute("participant", node.getAttributeAsJid("participant").orElse(null))
                .build();
        whatsapp.sendNodeWithNoResponse(ack);
    }

    private ParsedReceipt parseReceipt(Node node) {
        var id = node.getAttributeAsString("id", null);
        var from = node.getAttributeAsJid("from").orElse(null);
        if (id == null || from == null) {
            return null;
        }

        var ackString = node.getAttributeAsString("type", null);
        var ack = ReceiptAck.fromType(ackString);
        var participantsNode = node.getChild("participants").orElse(null);
        if (participantsNode == null) {
            return parseSimple(node, id, from, ack, ackString);
        }

        if (participantsNode.hasAttribute("message_id")) {
            return parseAggregatedByMessage(id, from, node.getAttributeAsJid("recipient").orElse(null), ackString, participantsNode);
        }

        return parseAggregatedByType(id, from, node.getAttributeAsJid("recipient").orElse(null), ack, ackString, participantsNode);
    }

    private ParsedReceipt parseSimple(
            Node node,
            String id,
            Jid from,
            ReceiptAck ack,
            String ackString
    ) {
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
        if (externalIds.isEmpty()) {
            externalIds.add(id);
        }

        return new SimpleReceipt(
                id,
                from,
                node.getAttributeAsJid("participant").orElse(null),
                node.getAttributeAsJid("recipient").orElse(null),
                attributeInstant(node, "t"),
                ack,
                ackString,
                List.copyOf(externalIds)
        );
    }

    private ParsedReceipt parseAggregatedByType(
            String id,
            Jid from,
            Jid recipient,
            ReceiptAck ack,
            String ackString,
            Node participantsNode
    ) {
        var externalId = participantsNode.getAttributeAsString("key", null);
        if (externalId == null) {
            return null;
        }

        var receipts = new ArrayList<ParticipantReceipt>();
        for (var userNode : participantsNode.getChildren("user")) {
            var participant = userNode.getAttributeAsJid("jid").orElse(null);
            if (participant == null) {
                continue;
            }
            receipts.add(new ParticipantReceipt(participant, attributeInstant(userNode, "t"), ack, ackString));
        }

        return new AggregatedByTypeReceipt(id, from, recipient, null, null, ack, ackString, externalId, List.copyOf(receipts));
    }

    private ParsedReceipt parseAggregatedByMessage(
            String id,
            Jid from,
            Jid recipient,
            String ackString,
            Node participantsNode
    ) {
        var externalId = participantsNode.getAttributeAsString("message_id", null);
        if (externalId == null) {
            return null;
        }

        var receipts = new ArrayList<ParticipantReceipt>();
        for (var userNode : participantsNode.getChildren("user")) {
            var participant = userNode.getAttributeAsJid("jid").orElse(null);
            if (participant == null) {
                continue;
            }
            var childAckString = userNode.getAttributeAsString("type", null);
            receipts.add(new ParticipantReceipt(
                    participant,
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
                List.copyOf(receipts)
        );
    }

    private static Instant attributeInstant(Node node, String key) {
        return node.getAttributeAsLong(key)
                .stream()
                .mapToObj(Instant::ofEpochSecond)
                .findFirst()
                .orElse(null);
    }

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

    private static MessageStatus mapStatus(ReceiptAck ack) {
        return switch (ack) {
            case READ -> MessageStatus.READ;
            case PLAYED -> MessageStatus.PLAYED;
            case CONTENT_GONE -> MessageStatus.ERROR;
            default -> MessageStatus.DELIVERED;
        };
    }

    private static boolean isDeliveryLike(ReceiptAck ack) {
        return ack != ReceiptAck.READ && ack != ReceiptAck.PLAYED;
    }

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

    private void notifyMessageStatus(MessageInfo info) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onMessageStatus(whatsapp, info));
        }
    }

    private boolean sameUser(Jid left, Jid right) {
        return left != null
                && right != null
                && Objects.equals(left.toUserJid(), right.toUserJid());
    }

    private static boolean isRetryReceipt(Node node) {
        var type = node.getAttributeAsString("type", null);
        return "retry".equals(type) || "enc_rekey_retry".equals(type);
    }

    private record ReceiptUpdate(List<Jid> deliveredDevices, List<Jid> pendingDevices) {
    }

    private sealed interface ParsedReceipt extends ReceiptLike
            permits SimpleReceipt, AggregatedByTypeReceipt, AggregatedByMessageReceipt {
    }

    private interface ReceiptLike {
        Jid from();

        Jid recipient();

        Instant timestamp();

        String ackString();
    }

    private record SimpleReceipt(
            String stanzaId,
            Jid from,
            Jid participant,
            Jid recipient,
            Instant timestamp,
            ReceiptAck ack,
            String ackString,
            List<String> externalIds
    ) implements ParsedReceipt {
    }

    private record AggregatedByTypeReceipt(
            String stanzaId,
            Jid from,
            Jid recipient,
            Jid participant,
            Instant timestamp,
            ReceiptAck ack,
            String ackString,
            String externalId,
            List<ParticipantReceipt> receipts
    ) implements ParsedReceipt {
    }

    private record AggregatedByMessageReceipt(
            String stanzaId,
            Jid from,
            Jid recipient,
            Jid participant,
            Instant timestamp,
            ReceiptAck ack,
            String ackString,
            String externalId,
            List<ParticipantReceipt> receipts
    ) implements ParsedReceipt {
    }

    private record ParticipantReceipt(
            Jid participant,
            Instant timestamp,
            ReceiptAck ack,
            String ackString
    ) {
    }

    private enum ReceiptAck {
        RECEIVED,
        READ,
        PLAYED,
        CONTENT_GONE,
        PEER;

        private static ReceiptAck fromType(String type) {
            if (type == null || "delivery".equals(type) || "sender".equals(type) || "inactive".equals(type)) {
                return RECEIVED;
            }
            return switch (type) {
                case "read", "read-self" -> READ;
                case "played", "played-self" -> PLAYED;
                case "server-error" -> CONTENT_GONE;
                case "peer_msg" -> PEER;
                default -> RECEIVED;
            };
        }
    }
}
