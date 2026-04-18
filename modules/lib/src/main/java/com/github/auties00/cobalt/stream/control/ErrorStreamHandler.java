package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

/**
 * Handles the top-level {@code <error>} stanza emitted by the WhatsApp
 * server for protocol-level problems that are not scoped to any specific
 * request or session event.
 *
 * <p>The most common payload here is {@code code=479}
 * ({@code smax-invalid}), which the server sends when the client's last
 * outbound stanza failed the server-side schema-driven parser. Any other
 * code value is logged at {@code ERROR} so that the unknown condition is
 * surfaced in the application logs without tearing down the session.
 *
 * <p>This handler never dispatches a failure to the client's error handler:
 * generic {@code <error>} stanzas are informational and do not require the
 * session to be reset. The handler is registered for the {@code "error"}
 * tag inside {@link SocketStream}, mirroring the
 * {@code WAWebCommsHandleLoggedInStanza} switch arm
 * {@code case "error": return WABackendHandleError.handleError(e)}.
 *
 * @implNote WA Web wraps the parser in a {@code WADeprecatedWapParser}
 * named {@code "errorParser"} that asserts the tag and reads the
 * {@code code} attribute via {@code attrInt}; Cobalt collapses both steps
 * into the {@code handle} method below because the {@link SocketStream}
 * dispatcher already routes by tag and {@link Node#getAttributeAsInt} is
 * the equivalent attribute accessor. WA Web's {@code handleError} also
 * returns {@code Promise.resolve("NO_ACK")} so that the dispatcher does
 * not emit an XMPP ack; Cobalt's dispatcher never auto-acks, so no
 * equivalent token is needed.
 */
@WhatsAppWebModule(moduleName = "WABackendHandleError")
public final class ErrorStreamHandler implements SocketStream.Handler {
    /**
     * Logger for diagnostic messages about received error stanzas.
     */
    private static final System.Logger LOGGER = System.getLogger(ErrorStreamHandler.class.getName());

    /**
     * Reason code emitted by the server when the client sent a stanza that
     * failed validation against the server-side schema.
     *
     * @implNote Mirrors the {@code c.SMAX_INVALID = 479} constant defined
     * at the top of {@code WABackendHandleError}.
     */
    @WhatsAppWebExport(moduleName = "WABackendHandleError", exports = "SMAX_INVALID", adaptation = WhatsAppAdaptation.DIRECT)
    private static final int SMAX_INVALID_CODE = 479;

    /**
     * Constructs a new top-level {@code <error>} stanza handler.
     *
     * <p>No dependencies are required because the handler only logs
     * diagnostics; it does not dispatch exceptions or send stanzas.
     */
    public ErrorStreamHandler() {
    }

    /**
     * Handles an incoming top-level {@code <error>} stanza by logging the
     * received code at the appropriate severity.
     *
     * <p>Behaviour by {@code code} attribute:
     * <ul>
     *   <li>{@code 479} (smax-invalid): logged at {@code ERROR} with the
     *       message {@code "Invalid stanza sent (smax-invalid)"} to flag
     *       that the previous outbound stanza failed server-side schema
     *       validation</li>
     *   <li>any other code: logged at {@code ERROR} with the message
     *       {@code "Unknown error code: <code>"}</li>
     *   <li>missing or non-numeric code: logged at {@code WARNING} as a
     *       defensive fallback (WA Web would raise
     *       {@code XmppParsingFailure} inside the parser, but Cobalt's
     *       {@link SocketStream} dispatcher never auto-acks and has no
     *       parsing-failure recovery on this path)</li>
     * </ul>
     *
     * @param node the {@code <error>} stanza received from the server
     * @implNote WA Web's {@code handleError} returns
     * {@code Promise.resolve("NO_ACK")} so that the surrounding
     * {@code WAWebCommsHandleLoggedInStanza} dispatcher suppresses the
     * automatic XMPP ack. Cobalt's {@link SocketStream} dispatcher does
     * not auto-ack any stanza, so the {@code "NO_ACK"} token is dropped
     * (see the class-level javadoc).
     */
    @Override
    @WhatsAppWebExport(moduleName = "WABackendHandleError", exports = "handleError", adaptation = WhatsAppAdaptation.ADAPTED)
    public void handle(Node node) {
        // WABackendHandleError.errorParser: var t = e.attrInt("code")
        var code = node.getAttributeAsInt("code", (Integer) null);
        if (code == null) {
            // ADAPTED: WA Web's WAParsableWapNode.attrInt would throw XmppParsingFailure here,
            // which the WADeprecatedWapParser wrapper catches and returns as {error: e}; Cobalt
            // logs and returns instead so that a malformed error stanza never propagates.
            LOGGER.log(System.Logger.Level.WARNING, "Received error stanza without code: {0}", node);
            return;
        }

        // WABackendHandleError.errorParser: switch (t) { case c.SMAX_INVALID: return p(); ... }
        if (code == SMAX_INVALID_CODE) {
            // WABackendHandleError.p: WALogger.ERROR(`Invalid stanza sent (smax-invalid)`)
            //                         .sendLogs("smax-invalid")  -- telemetry, skipped
            LOGGER.log(System.Logger.Level.ERROR, "Invalid stanza sent (smax-invalid)");
            return;
        }

        // WABackendHandleError.errorParser: default: return _(t)
        // WABackendHandleError._: WALogger.ERROR(`Unknown error code: ${e}`, e)
        //                         .sendLogs("unknown-error-code")  -- telemetry, skipped
        LOGGER.log(System.Logger.Level.ERROR, "Unknown error code: {0}", code);
    }
}
