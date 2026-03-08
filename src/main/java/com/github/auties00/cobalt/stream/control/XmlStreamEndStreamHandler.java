package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

public final class XmlStreamEndStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(XmlStreamEndStreamHandler.class.getName());

    public XmlStreamEndStreamHandler() {
    }

    @Override
    public void handle(Node node) {
        LOGGER.log(System.Logger.Level.INFO, "Received xmlstreamend stanza; waiting for socket close");
    }
}
