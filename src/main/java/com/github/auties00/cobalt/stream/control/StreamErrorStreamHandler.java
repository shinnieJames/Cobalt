package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppSessionException;
import com.github.auties00.cobalt.exception.WhatsAppStreamException;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

public final class StreamErrorStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(StreamErrorStreamHandler.class.getName());
    private static final int STREAM_ERROR_RESTART_LOGIN = 515;
    private static final int STREAM_ERROR_LOGOUT = 516;
    private final WhatsAppClient whatsapp;

    public StreamErrorStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    @Override
    public void handle(Node node) {
        var conflict = node.getChild("conflict").orElse(null);
        if (conflict != null) {
            handleConflict(conflict.getAttributeAsString("type", null));
            return;
        }

        var code = node.getAttributeAsInt("code", (Integer) null);
        if (code != null) {
            handleCode(code);
            return;
        }

        var ack = node.getChild("ack").orElse(null);
        if (ack != null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Received stream:error ack for id={0}",
                    ack.getAttributeAsString("id", null));
            return;
        }

        if (node.hasChild("xml-not-well-formed")) {
            whatsapp.handleFailure(new WhatsAppStreamException.MalformedNode("Server reported xml-not-well-formed"));
            return;
        }

        LOGGER.log(System.Logger.Level.WARNING, "Received unrecognized stream:error stanza: {0}", node);
    }

    private void handleConflict(String type) {
        if ("replaced".equals(type)) {
            whatsapp.handleFailure(new WhatsAppSessionException.Conflict("Stream replaced by another active session"));
            return;
        }

        // WA Web collapses unknown conflict types into the device-removed path.
        whatsapp.handleFailure(new WhatsAppSessionException.LoggedOut("Server removed or invalidated this device"));
    }

    private void handleCode(int code) {
        LOGGER.log(System.Logger.Level.WARNING, "Received stream:error code={0}", code);
        if (code >= 500 && code < 600) {
            if (code == STREAM_ERROR_RESTART_LOGIN) {
                whatsapp.handleFailure(new WhatsAppSessionException.Reconnect("Server requested reconnect"));
                return;
            }

            if (code == STREAM_ERROR_LOGOUT) {
                whatsapp.handleFailure(new WhatsAppSessionException.LoggedOut("Server requested logout"));
                return;
            }

            whatsapp.handleFailure(new WhatsAppSessionException.Reconnect("Server stream error " + code));
        }
    }
}
