package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

/**
 * Handles the {@code <xmlstreamend>} stanza emitted by the WhatsApp server
 * when it intends to close the underlying stream without an explicit error.
 *
 * <p>The stanza carries no payload of interest; it simply signals that the
 * server has finished sending data and will close the socket shortly. This
 * handler logs the event for diagnostics and does not send any ack stanza
 * back to the server (the {@code "NO_ACK"} contract from WA Web).
 *
 * @implNote WA Web does not expose a dedicated module for this stanza: the
 * behavior is inlined inside the {@code "xmlstreamend"} branch of the
 * {@code handleLoggedInStanza} switch in {@code WAWebCommsHandleLoggedInStanza},
 * which logs the message and returns {@code "NO_ACK"} so that no
 * acknowledgement node is dispatched to the server.
 */
@WhatsAppWebModule(moduleName = "WAWebCommsHandleLoggedInStanza")
public final class XmlStreamEndStreamHandler implements SocketStream.Handler {
    /**
     * Logger for diagnostic messages when the server signals stream end.
     *
     * <p>The message text mirrors the literal logged by WA Web's
     * {@code WALogger.LOG} call inside the {@code "xmlstreamend"} switch arm.
     */
    private static final System.Logger LOGGER = System.getLogger(XmlStreamEndStreamHandler.class.getName());

    /**
     * Constructs a new {@code xmlstreamend} stanza handler.
     *
     * <p>No dependencies are required because the handler only logs the
     * stanza and intentionally does not send any acknowledgement back to
     * the server, matching WA Web's {@code "NO_ACK"} return value.
     */
    public XmlStreamEndStreamHandler() {
    }

    /**
     * Logs the reception of the {@code <xmlstreamend>} stanza and returns
     * without sending any acknowledgement.
     *
     * <p>WA Web's {@code handleLoggedInStanza} switch arm for
     * {@code "xmlstreamend"} performs exactly two actions: it calls
     * {@code WALogger.LOG} with a fixed template literal and then returns
     * the sentinel string {@code "NO_ACK"} so that the caller knows not to
     * dispatch an ack node back to the server. Cobalt mirrors this by
     * logging the same fixed message and returning {@code void} from this
     * handler &mdash; the dispatcher in {@link SocketStream} never sends an
     * ack on its own, so simply not sending one here preserves the
     * {@code "NO_ACK"} contract.
     *
     * @param node the {@code <xmlstreamend>} stanza received from the server
     * @implNote The log message text matches the WA Web template literal
     * exactly: {@code "Comms.handleStanza received xmlstreamend, return NO_ACK"}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCommsHandleLoggedInStanza", exports = "handleLoggedInStanza", adaptation = WhatsAppAdaptation.ADAPTED)
    public void handle(Node node) {
        // WAWebCommsHandleLoggedInStanza.handleLoggedInStanza: case "xmlstreamend" -> WALogger.LOG("Comms.handleStanza received xmlstreamend, return NO_ACK")
        LOGGER.log(System.Logger.Level.INFO, "Comms.handleStanza received xmlstreamend, return NO_ACK");
        // WAWebCommsHandleLoggedInStanza.handleLoggedInStanza: case "xmlstreamend" -> return "NO_ACK" (no ack node is dispatched)
    }
}
