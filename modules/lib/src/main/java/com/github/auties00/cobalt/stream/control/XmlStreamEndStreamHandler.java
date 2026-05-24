package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

/**
 * Handles the {@code <xmlstreamend>} stanza that the WhatsApp server emits
 * to signal a graceful end of the encrypted stream.
 *
 * @apiNote
 * This handler is registered under the {@code "xmlstreamend"} tag inside
 * {@link SocketStream} and runs as the final stanza on every clean
 * server-initiated close. Cobalt embedders do not invoke this class
 * directly; the dispatcher routes the stanza here automatically and the
 * underlying socket teardown is driven by the next read returning
 * end-of-stream.
 *
 * @implNote
 * This implementation only logs the stanza and intentionally does not
 * send any acknowledgement back, preserving the {@code "NO_ACK"} sentinel
 * that WA Web's {@code WAWebCommsHandleLoggedInStanza} returns from its
 * {@code "xmlstreamend"} switch arm. {@link SocketStream} never auto-acks
 * on its own, so a no-op handler reproduces the same wire behaviour.
 */
@WhatsAppWebModule(moduleName = "WAWebCommsHandleLoggedInStanza")
public final class XmlStreamEndStreamHandler implements SocketStream.Handler {
    /**
     * The system logger used to record the diagnostic line that mirrors the
     * literal template logged by {@code WALogger.LOG} inside WA Web's
     * {@code "xmlstreamend"} switch arm.
     */
    private static final System.Logger LOGGER = System.getLogger(XmlStreamEndStreamHandler.class.getName());

    /**
     * Constructs a new {@code <xmlstreamend>} stanza handler.
     *
     * @apiNote
     * Cobalt embedders never call this constructor directly; the dispatcher
     * in {@link SocketStream} instantiates the handler once per client.
     */
    public XmlStreamEndStreamHandler() {
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Logs that the server has signalled end-of-stream and returns
     * without dispatching any reply, matching WA Web's {@code "NO_ACK"}
     * contract for the {@code "xmlstreamend"} branch of
     * {@code WAWebCommsHandleLoggedInStanza.handleLoggedInStanza}.
     *
     * @implNote
     * This implementation is intentionally a pure log call; the socket
     * teardown that follows is driven by the next read returning
     * end-of-stream rather than by any reply sent here.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCommsHandleLoggedInStanza", exports = "handleLoggedInStanza", adaptation = WhatsAppAdaptation.ADAPTED)
    public void handle(Node node) {
        LOGGER.log(System.Logger.Level.INFO, "Comms.handleStanza received xmlstreamend, return NO_ACK");
    }
}
