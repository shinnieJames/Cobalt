package com.github.auties00.cobalt.socket.state;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.socket.SocketStream;

public final class WebQueryDisappearingModeStreamNodeHandler extends SocketStream.Handler {
    public WebQueryDisappearingModeStreamNodeHandler(WhatsAppClient whatsapp) {
        super(whatsapp, "success");
    }

    @Override
    public void handle(Node node) {
        whatsapp.refreshDisappearingMode();
    }
}
