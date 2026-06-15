package com.github.auties00.cobalt.socket.state;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.socket.SocketStream;

import static com.github.auties00.cobalt.client.WhatsAppClientErrorHandler.Location.MEDIA_CONNECTION;

public final class WebScheduleMediaConnectionUpdateStreamNodeHandler extends SocketStream.Handler {
    private final MediaConnectionBootstrap mediaConnectionBootstrap;

    public WebScheduleMediaConnectionUpdateStreamNodeHandler(WhatsAppClient whatsapp) {
        super(whatsapp, "success");
        this.mediaConnectionBootstrap = new MediaConnectionBootstrap(whatsapp);
    }

    @Override
    public void handle(Node node) {
        try {
            mediaConnectionBootstrap.start();
        } catch (Exception exception) {
            whatsapp.handleFailure(MEDIA_CONNECTION, exception);
        }
    }

    @Override
    public void reset() {
        mediaConnectionBootstrap.reset();
    }
}
