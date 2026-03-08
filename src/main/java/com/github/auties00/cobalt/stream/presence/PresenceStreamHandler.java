package com.github.auties00.cobalt.stream.presence;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactStatus;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;
import java.util.Set;

public final class PresenceStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(PresenceStreamHandler.class.getName());
    private static final Set<String> HIDDEN_LAST_VALUES = Set.of("deny", "none", "error");
    private final WhatsAppClient whatsapp;

    public PresenceStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    @Override
    public void handle(Node node) {
        var from = node.getAttributeAsJid("from", null);
        if (from == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring presence stanza without from: {0}", node);
            return;
        }

        var contact = getOrCreateContact(from);
        if (contact == null) {
            return;
        }

        var type = node.getAttributeAsString("type", "available");
        var status = "unavailable".equals(type) ? ContactStatus.UNAVAILABLE : ContactStatus.AVAILABLE;
        contact.setLastKnownPresence(status);

        var lastSeen = resolveLastSeen(node, status);
        if (lastSeen != null) {
            contact.setLastSeen(lastSeen);
        }

        whatsapp.store().addContact(contact);
        notifyPresence(contact.toJid(), contact.toJid());
    }

    private Instant resolveLastSeen(Node node, ContactStatus status) {
        if (status != ContactStatus.UNAVAILABLE) {
            return null;
        }

        var lastValue = node.getAttributeAsString("last", null);
        if (lastValue == null) {
            return Instant.now();
        }

        if (HIDDEN_LAST_VALUES.contains(lastValue)) {
            return null;
        }

        try {
            return Instant.ofEpochSecond(Long.parseLong(lastValue));
        } catch (NumberFormatException exception) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring malformed presence last value {0} in {1}", lastValue, node);
            return null;
        }
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
