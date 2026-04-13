package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppSessionException;
import com.github.auties00.cobalt.exception.WhatsAppStreamException;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

/**
 * Handles {@code stream:error} stanzas received from the WhatsApp server.
 * <p>
 * Stream errors indicate critical protocol-level issues that require immediate action.
 * The handler classifies the error into one of several types and dispatches the
 * appropriate exception to the client's error handler:
 * <ul>
 *   <li><b>conflict (replaced):</b> another session took over this connection</li>
 *   <li><b>conflict (device_removed or unknown):</b> device was removed or invalidated</li>
 *   <li><b>code 515:</b> server requests reconnection and re-login</li>
 *   <li><b>code 516:</b> server forces logout</li>
 *   <li><b>other 5xx codes:</b> server stream error, socket closed without retry</li>
 *   <li><b>non-5xx codes:</b> unrecognized error code, socket closed</li>
 *   <li><b>ack:</b> acknowledgement-related stream error, socket closed</li>
 *   <li><b>xml-not-well-formed:</b> server reports malformed XML, socket closed</li>
 *   <li><b>other:</b> unrecognized stream error, socket closed</li>
 * </ul>
 *
 * @implNote WAWebHandleStreamError.default
 */
public final class StreamErrorStreamHandler implements SocketStream.Handler {

    /**
     * Logger for diagnostic output related to stream error handling.
     *
     * @implNote WAWebHandleStreamError.default -- logging via WALogger
     */
    private static final System.Logger LOGGER = System.getLogger(StreamErrorStreamHandler.class.getName());

    /**
     * Stream error code indicating the server requests the client to reconnect and re-login.
     *
     * @implNote WAWebHandleStreamError.default -- code 515 triggers startLogin
     */
    private static final int STREAM_ERROR_RESTART_LOGIN = 515;

    /**
     * Stream error code indicating the server forces logout of the companion device.
     *
     * @implNote WAWebHandleStreamError.default -- code 516 triggers startLogout
     */
    private static final int STREAM_ERROR_LOGOUT = 516;

    /**
     * The WhatsApp client instance used to dispatch failure exceptions.
     *
     * @implNote WAWebHandleStreamError.default -- dependency injection replaces module-level imports
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new stream error handler with the specified client.
     *
     * @param whatsapp the WhatsApp client to dispatch errors to; must not be {@code null}
     * @implNote WAWebHandleStreamError.default
     */
    public StreamErrorStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * Handles a {@code stream:error} stanza by classifying the error type and dispatching
     * the appropriate exception to the client's error handler.
     * <p>
     * The classification follows the same priority as WA Web's parser:
     * <ol>
     *   <li>Check for {@code conflict} child node</li>
     *   <li>Check for {@code code} attribute</li>
     *   <li>Check for {@code ack} child node</li>
     *   <li>Check for {@code xml-not-well-formed} child node</li>
     *   <li>Fall through as unrecognized error</li>
     * </ol>
     * All paths that do not produce an early-return exception (conflict, code 515, code 516)
     * result in a socket-close exception being dispatched.
     *
     * @param node the {@code stream:error} node received from the server
     * @implNote WAWebHandleStreamError.default
     */
    @Override
    public void handle(Node node) {
        // WAWebHandleStreamError.default -- parser checks conflict child first
        var conflict = node.getChild("conflict").orElse(null);
        if (conflict != null) {
            handleConflict(conflict.getAttributeAsString("type", null));
            return;
        }

        // WAWebHandleStreamError.default -- parser checks code attribute second
        var code = node.getAttributeAsInt("code", (Integer) null);
        if (code != null) {
            handleCode(code);
            return;
        }

        // WAWebHandleStreamError.default -- parser checks ack child third
        var ack = node.getChild("ack").orElse(null);
        if (ack != null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Received stream:error ack for id={0}",
                    ack.getAttributeAsString("id", null));
            // WAWebHandleStreamError.default -- ack type falls through to CLOSE_SOCKET
            whatsapp.handleFailure(new WhatsAppSessionException.Closed("Stream error: ack"));
            return;
        }

        // WAWebHandleStreamError.default -- parser checks xml-not-well-formed child fourth
        if (node.hasChild("xml-not-well-formed")) {
            // ADAPTED: WAWebHandleStreamError.default -- mapped to MalformedNode exception
            whatsapp.handleFailure(new WhatsAppStreamException.MalformedNode("Server reported xml-not-well-formed"));
            return;
        }

        // WAWebHandleStreamError.default -- unrecognized type falls through to CLOSE_SOCKET
        LOGGER.log(System.Logger.Level.WARNING, "Received unrecognized stream:error stanza: {0}", node);
        whatsapp.handleFailure(new WhatsAppSessionException.Closed("Stream error: unrecognized"));
    }

    /**
     * Handles a conflict-type stream error by dispatching the appropriate session exception.
     * <p>
     * A conflict type of {@code "replaced"} means another session has taken over this connection,
     * which maps to a {@link WhatsAppSessionException.Conflict}. All other conflict types
     * (including {@code "device_removed"} and unknown values) are treated as device removal,
     * which maps to a {@link WhatsAppSessionException.LoggedOut}.
     *
     * @param type the conflict type attribute value, or {@code null} if absent
     * @implNote WAWebHandleStreamError.default -- streamErrorParser conflict branch
     */
    private void handleConflict(String type) {
        if ("replaced".equals(type)) {
            // WAWebHandleStreamError.default -- replaced: stopComms, resolve NO_ACK
            whatsapp.handleFailure(new WhatsAppSessionException.Conflict("Stream replaced by another active session"));
            return;
        }

        // WAWebHandleStreamError.default -- device_removed/default: clearCredentialsAndStoredData, triggerLogout
        whatsapp.handleFailure(new WhatsAppSessionException.LoggedOut("Server removed or invalidated this device"));
    }

    /**
     * Handles a code-type stream error by dispatching the appropriate session exception.
     * <p>
     * Server error codes in the 500-599 range have specific handling:
     * <ul>
     *   <li>{@code 515}: server requests reconnection and re-login</li>
     *   <li>{@code 516}: server forces companion logout</li>
     *   <li>Other 5xx: server stream error, socket closed without retry</li>
     * </ul>
     * Codes outside the 500-599 range close the socket.
     *
     * @param code the error code from the {@code stream:error} stanza
     * @implNote WAWebHandleStreamError.default -- code branch
     */
    private void handleCode(int code) {
        LOGGER.log(System.Logger.Level.WARNING, "Received stream:error code={0}", code);
        if (code >= 500 && code < 600) {
            // WAWebHandleStreamError.default -- 5xx range
            if (code == STREAM_ERROR_RESTART_LOGIN) {
                // WAWebHandleStreamError.default -- 515: stopComms, startLogin, resolve NO_ACK
                whatsapp.handleFailure(new WhatsAppSessionException.Reconnect("Server requested reconnect"));
                return;
            }

            if (code == STREAM_ERROR_LOGOUT) {
                // WAWebHandleStreamError.default -- 516: stopComms, startLogout, resolve NO_ACK
                whatsapp.handleFailure(new WhatsAppSessionException.LoggedOut("Server requested logout"));
                return;
            }

            // WAWebHandleStreamError.default -- other 5xx: onStreamErrorReceived (cancel retry), CLOSE_SOCKET
            whatsapp.handleFailure(new WhatsAppSessionException.Closed("Server stream error " + code));
            return;
        }

        // WAWebHandleStreamError.default -- non-5xx codes fall through to CLOSE_SOCKET
        whatsapp.handleFailure(new WhatsAppSessionException.Closed("Server stream error code " + code));
    }
}
