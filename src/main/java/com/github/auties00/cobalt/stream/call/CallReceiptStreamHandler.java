package com.github.auties00.cobalt.stream.call;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.call.CallOffer;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;

public final class CallReceiptStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(CallReceiptStreamHandler.class.getName());
    private final WhatsAppClient whatsapp;

    public CallReceiptStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    @Override
    public void handle(Node node) {
        var from = node.getAttributeAsJid("from", null);
        var payload = node.getChild().orElse(null);
        if (from == null || payload == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring malformed call receipt: {0}", node);
            return;
        }

        var callId = payload.getAttributeAsString("call-id", null);
        if (callId == null) {
            sendReceiptAck(node, from);
            return;
        }

        switch (payload.description()) {
            case "accept" -> whatsapp.store().findCallById(callId)
                    .ifPresent(call -> whatsapp.store().addCall(call.setStatus(CallOffer.Status.ACCEPTED)));
            case "reject" -> whatsapp.store().findCallById(callId)
                    .ifPresent(call -> whatsapp.store().addCall(call.setStatus(CallOffer.Status.REJECTED)));
            case "offer" -> {
                // Keep existing ringing state.
            }
            default -> {
                return;
            }
        }

        sendReceiptAck(node, from);
    }

    private void sendReceiptAck(Node node, Jid to) {
        var id = node.getAttributeAsString("id", null);
        if (id == null) {
            return;
        }

        var ack = new NodeBuilder()
                .description("ack")
                .attribute("id", id)
                .attribute("to", to)
                .attribute("class", "receipt")
                .attribute("type", node.getAttributeAsString("type", null))
                .build();
        whatsapp.sendNodeWithNoResponse(ack);
    }
}
