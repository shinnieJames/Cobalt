package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppConnectionException;
import com.github.auties00.cobalt.exception.WhatsAppSessionException;
import com.github.auties00.cobalt.exception.WhatsAppServerRuntimeException;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

public final class FailureStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(FailureStreamHandler.class.getName());
    private static final int REASON_GENERIC_FAILURE = 400;
    private static final int REASON_NOT_AUTHORIZED = 401;
    private static final int REASON_TEMP_BANNED = 402;
    private static final int REASON_LOCKED = 403;
    private static final int REASON_CLIENT_TOO_OLD = 405;
    private static final int REASON_BANNED = 406;
    private static final int REASON_BAD_USER_AGENT = 409;
    private static final int REASON_INTERNAL_SERVER_ERROR = 500;
    private static final int REASON_EXPERIMENTAL = 501;
    private static final int REASON_SERVICE_UNAVAILABLE = 503;
    private final WhatsAppClient whatsapp;

    public FailureStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    @Override
    public void handle(Node node) {
        var reason = node.getAttributeAsInt("reason", (Integer) null);
        var code = node.getAttributeAsInt("code", (Integer) null);
        var expire = node.getAttributeAsInt("expire", (Integer) null);
        var message = node.getAttributeAsString("message", null);
        var url = node.getAttributeAsString("url", null);
        var header = node.getAttributeAsString("logout_message_header", null);
        var subtext = node.getAttributeAsString("logout_message_subtext", null);

        LOGGER.log(System.Logger.Level.WARNING,
                "Received failure stanza: reason={0}, code={1}, expire={2}, message={3}, url={4}",
                reason,
                code,
                expire,
                message,
                url);

        // WA Web explicitly treats the code+expire tuple as a temporary-ban payload.
        if (code != null && expire != null) {
            whatsapp.handleFailure(new WhatsAppSessionException.Banned(
                    "Temporary ban: code=" + code + ", expire=" + expire));
            return;
        }

        // WA Web logs out immediately for locked-account flows that carry custom logout copy.
        if ((header != null && !header.isBlank()) || (subtext != null && !subtext.isBlank())) {
            whatsapp.handleFailure(new WhatsAppSessionException.LoggedOut(
                    "Server logout requested: " + header));
            return;
        }

        if (reason == null) {
            return;
        }

        switch (reason) {
            case REASON_NOT_AUTHORIZED -> whatsapp.handleFailure(
                    new WhatsAppSessionException.LoggedOut("Server reported not authorized"));
            case REASON_BANNED, REASON_TEMP_BANNED -> whatsapp.handleFailure(
                    new WhatsAppSessionException.Banned("Server reported banned reason " + reason));
            case REASON_CLIENT_TOO_OLD, REASON_BAD_USER_AGENT -> whatsapp.handleFailure(
                    new WhatsAppConnectionException("Client version rejected by server, reason=" + reason));
            case REASON_GENERIC_FAILURE,
                 REASON_LOCKED,
                 REASON_INTERNAL_SERVER_ERROR,
                 REASON_EXPERIMENTAL,
                 REASON_SERVICE_UNAVAILABLE -> whatsapp.handleFailure(
                    new WhatsAppServerRuntimeException("Server failure reason " + reason));
            default -> LOGGER.log(System.Logger.Level.WARNING,
                    "Unhandled failure reason {0}", reason);
        }
    }
}
