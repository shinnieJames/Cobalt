package com.github.auties00.cobalt.socket.state;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.socket.SocketStream;

public final class MobileFinishLoginStreamNodeHandler extends SocketStream.Handler {
    public MobileFinishLoginStreamNodeHandler(WhatsAppClient whatsapp) {
        super(whatsapp, "success");
    }

    @Override
    public void handle(Node node) {
        // Mobile login may complete before a dedicated identity update arrives.
        // Ensure local identity is available for callbacks that immediately send messages.
        var store = whatsapp.store();
        if (store.jid().isEmpty()) {
            node.getAttributeAsJid("jid")
                    .or(() -> node.getAttributeAsJid("from"))
                    .or(() -> node.getChild("device").flatMap(device -> device.getAttributeAsJid("jid")))
                    .or(() -> store.phoneNumber().stream().mapToObj(Jid::of).findFirst())
                    .ifPresent(store::setJid);
        }

        if (store.lid().isEmpty()) {
            node.getAttributeAsJid("lid")
                    .or(() -> node.getChild("device").flatMap(device -> device.getAttributeAsJid("lid")))
                    .ifPresent(store::setLid);
        }

        if (!whatsapp.store().registered()) {
            store
                    .setRegistered(true);
            store
                    .serialize();
        }

        for (var listener : store.listeners()) {
            Thread.startVirtualThread(() -> listener.onLoggedIn(whatsapp));
        }
    }
}
