package com.github.auties00.cobalt.stream.newsletter;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.message.MessageStatus;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfoBuilder;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;

public final class NewsletterStatusStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(NewsletterStatusStreamHandler.class.getName());
    private final WhatsAppClient whatsapp;

    public NewsletterStatusStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    @Override
    public void handle(Node node) {
        var from = node.getAttributeAsJid("from", null);
        if (from == null || !from.hasNewsletterServer()) {
            return;
        }

        var id = node.getAttributeAsString("id", null);
        if (id == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring newsletter status without id: {0}", node);
            return;
        }

        var plaintext = node.getChild("plaintext")
                .or(() -> node.getChild("body"))
                .flatMap(Node::toContentBytes)
                .orElse(null);
        if (plaintext == null || plaintext.length == 0) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unsupported newsletter status content for {0}", id);
            return;
        }

        var message = decodeMessage(id, plaintext);
        if (message == null) {
            return;
        }

        var newsletter = whatsapp.store()
                .findNewsletterByJid(from)
                .orElseGet(() -> whatsapp.store().addNewNewsletter(from));
        var info = new NewsletterMessageInfoBuilder()
                .key(new MessageKeyBuilder()
                        .id(id)
                        .parentJid(from)
                        .fromMe(node.getAttributeAsBool("is_sender", false))
                        .senderJid(from)
                        .build())
                .serverId(node.getAttributeAsInt("server_id", 0))
                .timestamp(resolveTimestamp(node))
                .message(message)
                .status(MessageStatus.DELIVERED)
                .build();
        newsletter.addMessage(info);
        notifyNewMessage(info);
    }

    private MessageContainer decodeMessage(String id, byte[] plaintext) {
        try {
            return MessageContainerSpec.decode(plaintext);
        } catch (Exception exception) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Failed to decode newsletter status {0}: {1}", id, exception.getMessage());
            return null;
        }
    }

    private Instant resolveTimestamp(Node node) {
        var timestamp = node.getAttributeAsLong("t", (Long) null);
        return timestamp == null ? null : Instant.ofEpochSecond(timestamp);
    }

    private void notifyNewMessage(com.github.auties00.cobalt.model.message.MessageInfo info) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onNewMessage(whatsapp, info));
        }
    }
}
