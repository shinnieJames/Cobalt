package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

public final class ErrorStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(ErrorStreamHandler.class.getName());
    private static final int SMAX_INVALID_CODE = 479;

    public ErrorStreamHandler() {
    }

    @Override
    public void handle(Node node) {
        var code = node.getAttributeAsInt("code", (Integer) null);
        if (code == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Received error stanza without code: {0}", node);
            return;
        }

        if (code == SMAX_INVALID_CODE) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Received error stanza for invalid stanza sent (smax-invalid, code={0})",
                    code);
            return;
        }

        LOGGER.log(System.Logger.Level.ERROR, "Received error stanza with unknown code={0}", code);
    }
}
