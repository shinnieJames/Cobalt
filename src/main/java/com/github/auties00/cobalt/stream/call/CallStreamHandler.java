package com.github.auties00.cobalt.stream.call;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.call.CallOffer;
import com.github.auties00.cobalt.model.call.CallOfferBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;

public final class CallStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(CallStreamHandler.class.getName());
    private final WhatsAppClient whatsapp;

    public CallStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    @Override
    public void handle(Node node) {
        handleCall(node);
    }

    private void handleCall(Node node) {
        var from = node.getAttributeAsJid("from", null);
        var payload = node.getChild().orElse(null);
        if (from == null || payload == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring malformed call stanza: {0}", node);
            return;
        }

        var callId = payload.getAttributeAsString("call-id", null);
        var callCreator = payload.getAttributeAsJid("call-creator", null);
        if (callId == null || callCreator == null) {
            sendCallAck(node, payload.description());
            return;
        }

        switch (payload.description()) {
            case "offer" -> handleOffer(node, payload, from, callId, callCreator);
            case "accept" -> {
                updateCall(node, callId, payload, from, callCreator, CallOffer.Status.ACCEPTED, node.hasAttribute("offline"));
                sendCallReceipt(node, from, callId, callCreator, "accept");
            }
            case "reject" -> {
                updateCall(node, callId, payload, from, callCreator, CallOffer.Status.REJECTED, node.hasAttribute("offline"));
                sendCallReceipt(node, from, callId, callCreator, "reject");
            }
            case "enc_rekey" -> sendCallReceipt(node, from, callId, callCreator, "enc_rekey");
            case "terminate" -> {
                var reason = payload.getAttributeAsString("reason", null);
                var status = "timeout".equals(reason)
                        ? CallOffer.Status.TIMED_OUT
                        : CallOffer.Status.CANCELLED;
                updateCall(node, callId, payload, from, callCreator, status, node.hasAttribute("offline"));
                sendCallAck(node, payload.description());
            }
            default -> sendCallAck(node, payload.description());
        }
    }

    private void handleOffer(Node node, Node payload, Jid from, String callId, Jid callCreator) {
        var call = buildOrUpdateCall(node, callId, payload, from, callCreator, CallOffer.Status.RINGING, node.hasAttribute("offline"));
        if (call == null) {
            sendCallAck(node, payload.description());
            return;
        }

        sendCallReceipt(node, from, callId, callCreator, "offer");
        if (!call.outgoing()) {
            notifyCall(call);
        }
    }

    private CallOffer buildOrUpdateCall(
            Node node,
            String callId,
            Node payload,
            Jid from,
            Jid callCreator,
            CallOffer.Status status,
            boolean offline
    ) {
        var groupJid = payload.getAttributeAsJid("group-jid", null);
        var chatJid = groupJid != null ? groupJid : canonicalUserJid(from);
        if (chatJid == null) {
            return null;
        }

        var self = whatsapp.store().jid().orElse(null);
        var outgoing = self != null && callCreator.toUserJid().equals(self.toUserJid());

        var existing = whatsapp.store().findCallById(callId).orElse(null);
        if (existing != null) {
            existing.setChatJid(chatJid);
            existing.setCallerJid(callCreator);
            existing.setVideo(payload.hasChild("video"));
            existing.setOfflineOffer(offline);
            existing.setGroup(groupJid != null);
            existing.setGroupJid(groupJid);
            existing.setOutgoing(outgoing);
            existing.setStatus(status);
            whatsapp.store().addCall(existing);
            return existing;
        }

        var call = new CallOfferBuilder()
                .chatJid(chatJid)
                .callerJid(callCreator)
                .callId(callId)
                .timestamp(resolveTimestamp(node))
                .video(payload.hasChild("video"))
                .status(status)
                .offlineOffer(offline)
                .group(groupJid != null)
                .groupJid(groupJid)
                .outgoing(outgoing)
                .build();
        whatsapp.store().addCall(call);
        return call;
    }

    private void updateCall(
            Node node,
            String callId,
            Node payload,
            Jid from,
            Jid callCreator,
            CallOffer.Status status,
            boolean offline
    ) {
        buildOrUpdateCall(node, callId, payload, from, callCreator, status, offline);
    }

    private Instant resolveTimestamp(Node node) {
        var rawTimestamp = node.getAttributeAsLong("t", (Long) null);
        var timestamp = rawTimestamp != null ? Instant.ofEpochSecond(rawTimestamp) : null;
        return timestamp != null ? timestamp : Instant.now();
    }

    private void sendCallReceipt(Node node, Jid to, String callId, Jid callCreator, String childTag) {
        var from = resolveReceiptFrom(to);
        var stanzaId = node.getAttributeAsString("id", null);
        if (from == null || stanzaId == null) {
            return;
        }

        var child = new NodeBuilder()
                .description(childTag)
                .attribute("call-id", callId)
                .attribute("call-creator", callCreator)
                .build();
        var receipt = new NodeBuilder()
                .description("receipt")
                .attribute("to", to)
                .attribute("from", from)
                .attribute("id", stanzaId)
                .content(child)
                .build();
        whatsapp.sendNodeWithNoResponse(receipt);
    }

    private void sendCallAck(Node node, String type) {
        var to = node.getAttributeAsJid("from", null);
        var id = node.getAttributeAsString("id", null);
        if (to == null || id == null) {
            return;
        }

        var ack = new NodeBuilder()
                .description("ack")
                .attribute("to", to)
                .attribute("id", id)
                .attribute("class", "call")
                .attribute("type", type)
                .build();
        whatsapp.sendNodeWithNoResponse(ack);
    }

    private Jid resolveReceiptFrom(Jid remote) {
        var self = whatsapp.store().jid().orElse(null);
        if (self == null) {
            return null;
        }

        if (remote.hasLidServer()) {
            return whatsapp.store().lid()
                    .map(Jid::toUserJid)
                    .orElse(self.toUserJid());
        }

        return self.toUserJid();
    }

    private Jid canonicalUserJid(Jid jid) {
        if (jid == null) {
            return null;
        }

        var userJid = jid.toUserJid();
        if (!userJid.hasLidServer()) {
            return userJid;
        }

        return whatsapp.store().findPhoneByLid(userJid).orElse(userJid);
    }

    private void notifyCall(CallOffer call) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onCall(whatsapp, call));
        }
    }
}
