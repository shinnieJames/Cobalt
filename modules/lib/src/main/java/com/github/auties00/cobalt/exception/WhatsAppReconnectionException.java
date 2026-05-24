package com.github.auties00.cobalt.exception;

/**
 * Thrown when an attempt to re-establish a previously open WhatsApp
 * session fails.
 *
 * @apiNote
 * After a session is dropped (network blip, server cycling the
 * connection, or a {@link WhatsAppSessionException} being raised),
 * Cobalt schedules a reconnect attempt with backoff. This exception
 * carries the count of attempts made so far via {@link #attempts()} so
 * the caller can decide how long to wait before trying again or whether
 * to give up. Distinct from {@link WhatsAppConnectionException}, which
 * fires on the very first connection.
 *
 * @implNote
 * This implementation always reports the failure as fatal: there is no
 * usable session to keep working on. Whether to schedule a further
 * attempt is up to the configurable error handler.
 *
 * @see WhatsAppConnectionException
 */
public final class WhatsAppReconnectionException extends WhatsAppException {

    /**
     * The number of reconnection attempts that have already been made
     * when this exception is raised.
     */
    private final int attempts;

    /**
     * Constructs a new reconnection exception with the specified message and attempt count.
     *
     * @param message  the detail message describing the reconnection failure
     * @param attempts the number of reconnection attempts made so far
     */
    public WhatsAppReconnectionException(String message, int attempts) {
        super(message);
        this.attempts = attempts;
    }

    /**
     * Constructs a new reconnection exception with a message, attempt count, and cause.
     *
     * @param message  the detail message describing the reconnection failure
     * @param attempts the number of reconnection attempts made so far
     * @param cause    the underlying cause of the reconnection failure
     */
    public WhatsAppReconnectionException(String message, int attempts, Throwable cause) {
        super(message, cause);
        this.attempts = attempts;
    }

    /**
     * Returns the number of reconnection attempts already made.
     *
     * @apiNote
     * Use the count to bound retries or to compute a backoff delay
     * before scheduling another attempt.
     *
     * @return the attempt count, always non-negative
     */
    public int attempts() {
        return attempts;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns {@code true}: a failed
     * reconnect leaves the client with no live session.
     */
    @Override
    public boolean isFatal() {
        return true;
    }
}
