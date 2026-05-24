package com.github.auties00.cobalt.exception;

/**
 * Thrown when Cobalt cannot establish the initial connection to the
 * WhatsApp servers.
 *
 * @apiNote
 * Covers DNS resolution, TCP, TLS, the WebSocket upgrade, and the Noise
 * XX handshake that authenticates the client. Any failure along that
 * path raises this exception, distinct from {@link WhatsAppReconnectionException}
 * which fires when a retry after a previously successful session was
 * lost cannot complete. Embedders typically respond by surfacing the
 * failure to the user or scheduling another connection attempt under the
 * configurable error handler's verdict.
 *
 * @implNote
 * This implementation always reports the failure as fatal because there
 * is no live session to recover.
 *
 * @see WhatsAppReconnectionException
 * @see WhatsAppSessionException.Closed
 */
public final class WhatsAppConnectionException extends WhatsAppException {

    /**
     * Constructs a new connection exception with the specified detail message.
     *
     * @param message the detail message describing the connection failure
     */
    public WhatsAppConnectionException(String message) {
        super(message);
    }

    /**
     * Constructs a new connection exception with the specified detail message and cause.
     *
     * @param message the detail message describing the connection failure
     * @param cause   the underlying cause of the connection failure
     */
    public WhatsAppConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns {@code true}: no session has
     * been established at the point a connection exception is thrown.
     */
    @Override
    public boolean isFatal() {
        return true;
    }
}
