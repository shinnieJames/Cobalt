package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

/**
 * Handles top-level {@code <error>} stanzas that report stanza-level
 * protocol problems not scoped to a specific request.
 *
 * @apiNote
 * This handler is registered under the {@code "error"} tag inside
 * {@link SocketStream} and is invoked whenever the server emits a bare
 * {@code <error code="..."/>} stanza. The common payload is
 * {@code code=479} ({@code smax-invalid}), reported when the client's
 * last outbound stanza failed schema-driven validation on the server.
 * Cobalt embedders do not call this class directly.
 *
 * @implNote
 * This implementation never dispatches to the configured error handler:
 * matching WA Web's {@link WhatsAppWebModule WABackendHandleError}.handleError,
 * the stanza is informational only and the session is allowed to stay up.
 * Unrecognised codes are logged at {@code ERROR} severity rather than
 * surfaced to the error handler.
 */
@WhatsAppWebModule(moduleName = "WABackendHandleError")
public final class ErrorStreamHandler implements SocketStream.Handler {
    /**
     * The system logger used to record the diagnostic line for every
     * inbound {@code <error>} stanza.
     */
    private static final System.Logger LOGGER = System.getLogger(ErrorStreamHandler.class.getName());

    /**
     * The reason code emitted by the server when the client sent a stanza
     * that failed validation against the SMAX schema.
     *
     * @apiNote
     * Mirrors the value of the {@code SMAX_INVALID} symbol declared on the
     * {@code c} map inside {@code WABackendHandleError}. When this code is
     * observed it almost always indicates a wire-encoding bug on the
     * Cobalt side rather than a transient server condition.
     */
    @WhatsAppWebExport(moduleName = "WABackendHandleError", exports = "SMAX_INVALID", adaptation = WhatsAppAdaptation.DIRECT)
    private static final int SMAX_INVALID_CODE = 479;

    /**
     * Constructs a new {@code <error>} stanza handler.
     *
     * @apiNote
     * Cobalt embedders never call this constructor directly; the
     * dispatcher in {@link SocketStream} instantiates the handler once per
     * client. The handler is stateless and stores no dependencies because
     * it only logs.
     */
    public ErrorStreamHandler() {
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Logs the received {@code code} attribute and returns without taking
     * any other action. A {@code code} value of {@link #SMAX_INVALID_CODE}
     * is logged at {@code ERROR} with a fixed message; any other recognised
     * code is logged at {@code ERROR} with a generic message; a missing or
     * non-numeric {@code code} is logged at {@code WARNING} as a defensive
     * fallback.
     *
     * @implNote
     * This implementation never throws and never delegates to the
     * pluggable error handler: WA Web's {@code WABackendHandleError.handleError}
     * also returns {@code "NO_ACK"} after logging, so leaving the socket
     * up matches the upstream contract. WA Web's deprecated parser would
     * raise {@code XmppParsingFailure} when the {@code code} attribute is
     * missing; Cobalt's {@link Node} accessor returns {@code null} instead
     * and the handler logs that case at {@code WARNING}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WABackendHandleError", exports = "handleError", adaptation = WhatsAppAdaptation.ADAPTED)
    public void handle(Node node) {
        var code = node.getAttributeAsInt("code", null);
        if (code == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Received error stanza without code: {0}", node);
            return;
        }

        if (code == SMAX_INVALID_CODE) {
            LOGGER.log(System.Logger.Level.ERROR, "Invalid stanza sent (smax-invalid)");
            return;
        }

        LOGGER.log(System.Logger.Level.ERROR, "Unknown error code: {0}", code);
    }
}
