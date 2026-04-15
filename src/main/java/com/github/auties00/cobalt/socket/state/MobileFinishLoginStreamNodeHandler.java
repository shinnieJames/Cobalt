package com.github.auties00.cobalt.socket.state;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.contact.ContactBuilder;
import com.github.auties00.cobalt.model.contact.ContactStatus;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.socket.SocketStream;
import com.github.auties00.cobalt.util.Clock;

public final class MobileFinishLoginStreamNodeHandler extends SocketStream.Handler {
    public MobileFinishLoginStreamNodeHandler(WhatsAppClient whatsapp) {
        super(whatsapp, "success");
    }

    @Override
    public void handle(Node node) {
        saveCompanion(node);

        if (!whatsapp.store().registered()) {
            whatsapp.store()
                    .setRegistered(true);
            whatsapp.store()
                    .serialize();
        }

        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onLoggedIn(whatsapp));
        }
    }

    private void saveCompanion(Node node) {
        var jid = node.getAttributeAsJid("jid")
                .or(() -> node.getChild("device").flatMap(device -> device.getAttributeAsJid("jid")))
                .or(() -> whatsapp.store().phoneNumber().stream().mapToObj(Jid::of).findFirst())
                .orElse(null);
        if (jid != null) {
            whatsapp.store()
                    .setJid(jid);
            whatsapp.store()
                    .setPhoneNumber(Long.parseUnsignedLong(jid.user()));
            if (whatsapp.store().findContactByJid(jid).isEmpty()) {
                var contact = new ContactBuilder()
                        .jid(jid)
                        .chosenName(whatsapp.store().name())
                        .lastKnownPresence(ContactStatus.AVAILABLE)
                        .lastSeenSeconds(Clock.nowSeconds())
                        .blocked(false)
                        .build();
                whatsapp.store().addContact(contact);
            }
        }

        node.getAttributeAsJid("lid")
                .or(() -> node.getChild("device").flatMap(device -> device.getAttributeAsJid("lid")))
                .ifPresent(whatsapp.store()::setLid);
    }
}
