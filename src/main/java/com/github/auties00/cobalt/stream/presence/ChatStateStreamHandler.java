package com.github.auties00.cobalt.stream.presence;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactStatus;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

public final class ChatStateStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(ChatStateStreamHandler.class.getName());
    private final WhatsAppClient whatsapp;

    public ChatStateStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    @Override
    public void handle(Node node) {
        var from = node.getAttributeAsJid("from", null);
        if (from == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring chatstate stanza without from: {0}", node);
            return;
        }

        var participant = node.getAttributeAsJid("participant", null);
        var state = resolveState(node);
        if (state == null) {
            return;
        }

        if (participant != null) {
            var contact = getOrCreateContact(participant);
            if (contact != null) {
                contact.setLastKnownPresence(state);
                whatsapp.store().addContact(contact);
                notifyPresence(from, contact.toJid());
            }
            return;
        }

        var contact = getOrCreateContact(from);
        if (contact == null) {
            return;
        }

        contact.setLastKnownPresence(state);
        whatsapp.store().addContact(contact);
        notifyPresence(contact.toJid(), contact.toJid());
    }

    private ContactStatus resolveState(Node node) {
        var child = node.getChild().orElse(null);
        if (child == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring empty chatstate stanza: {0}", node);
            return null;
        }

        return switch (child.description()) {
            case "composing" -> "audio".equals(child.getAttributeAsString("media", null))
                    ? ContactStatus.RECORDING
                    : ContactStatus.COMPOSING;
            case "paused" -> ContactStatus.AVAILABLE;
            default -> {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Ignoring unsupported chatstate child {0} in {1}",
                        child.description(), node);
                yield null;
            }
        };
    }

    private Contact getOrCreateContact(Jid jid) {
        if (jid == null) {
            return null;
        }

        var canonical = jid.toUserJid().hasLidServer()
                ? whatsapp.store().findPhoneByLid(jid.toUserJid()).orElse(jid.toUserJid())
                : jid.toUserJid();
        return whatsapp.store()
                .findContactByJid(canonical)
                .orElseGet(() -> whatsapp.store().addNewContact(canonical));
    }

    private void notifyPresence(Jid conversation, Jid participant) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onContactPresence(whatsapp, conversation, participant));
        }
    }
}
