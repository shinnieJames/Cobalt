package com.github.auties00.cobalt.socket.state;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientDisconnectReason;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.socket.SocketStream;

public final class EndStreamNodeHandler extends SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(EndStreamNodeHandler.class.getName());

    public EndStreamNodeHandler(WhatsAppClient whatsapp) {
        super(whatsapp, "xmlstreamend");
    }

    @Override
    public void handle(Node node) {
        LOGGER.log(System.Logger.Level.WARNING, "[stream_end] action=reconnect desc={0}", node.description());
        whatsapp.disconnect(WhatsAppClientDisconnectReason.RECONNECTING);
    }
}
