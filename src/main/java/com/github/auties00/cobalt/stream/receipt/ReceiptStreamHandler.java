package com.github.auties00.cobalt.stream.receipt;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.stream.call.CallReceiptStreamHandler;

public final class ReceiptStreamHandler implements SocketStream.Handler {
    private final CallReceiptStreamHandler callReceiptHandler;
    private final MessageReceiptStreamHandler messageReceiptHandler;

    public ReceiptStreamHandler(WhatsAppClient whatsapp, MessageService messageService) {
        this.callReceiptHandler = new CallReceiptStreamHandler(whatsapp);
        this.messageReceiptHandler = new MessageReceiptStreamHandler(whatsapp, messageService);
    }

    @Override
    public void handle(Node node) {
        if (isCallReceipt(node)) {
            callReceiptHandler.handle(node);
            return;
        }

        messageReceiptHandler.handle(node);
    }

    @Override
    public void reset() {
        callReceiptHandler.reset();
        messageReceiptHandler.reset();
    }

    private boolean isCallReceipt(Node node) {
        var child = node.getChild().orElse(null);
        return child != null && switch (child.description()) {
            case "offer", "accept", "reject" -> true;
            default -> false;
        };
    }
}
