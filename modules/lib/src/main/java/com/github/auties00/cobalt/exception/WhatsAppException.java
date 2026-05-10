package com.github.auties00.cobalt.exception;

/**
 * Root of every exception thrown by Cobalt while talking to WhatsApp.
 *
 * <p>The hierarchy is sealed so callers can pattern-match on the concrete
 * subtype to react to a specific failure mode (a session conflict, a media
 * download failure, an account device verification mismatch, and so on).
 * Each subtype documents the WhatsApp feature it relates to and whether
 * it should be treated as fatal via {@link #isFatal()}.
 *
 * <p>Cobalt does not pin a recovery action to any exception. When a
 * subtype is thrown, the configurable {@code WhatsAppClientErrorHandler}
 * decides whether the client should discard the event, disconnect,
 * reconnect, log out, or treat the account as banned. Callers therefore
 * normally observe these exceptions through that handler rather than
 * through {@code try}/{@code catch} blocks around individual calls.
 *
 * @see #isFatal()
 */
public abstract sealed class WhatsAppException extends RuntimeException
        permits WhatsAppABPropTypeMismatchException, WhatsAppAdvCheckException, WhatsAppAdvValidationException, WhatsAppCallException, WhatsAppConnectionException, WhatsAppCorruptedStoreException, WhatsAppDeviceSyncException, WhatsAppHistorySyncException, WhatsAppLidMigrationException, WhatsAppMalformedJidException, WhatsAppMediaException, WhatsAppMessageException, WhatsAppOwnDeviceListExpiredException, WhatsAppPrivateStatsTokenIssuerException, WhatsAppReconnectionException, WhatsAppRegistrationException, WhatsAppServerRuntimeException, WhatsAppSessionException, WhatsAppStreamException, WhatsAppWebAppStateSyncException {

    /**
     * Constructs a new WhatsApp exception with no detail message.
     */
    protected WhatsAppException() {
        super();
    }

    /**
     * Constructs a new WhatsApp exception with the specified detail message.
     *
     * @param message the detail message describing the error condition
     */
    protected WhatsAppException(String message) {
        super(message);
    }

    /**
     * Constructs a new WhatsApp exception with the specified detail message and cause.
     *
     * @param message the detail message describing the error condition
     * @param cause   the underlying cause of this exception
     */
    protected WhatsAppException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new WhatsApp exception wrapping the specified cause.
     *
     * @param cause the underlying cause of this exception
     */
    protected WhatsAppException(Throwable cause) {
        super(cause);
    }

    /**
     * Returns whether the failure invalidates the current WhatsApp session.
     *
     * <p>A fatal failure means the session can no longer be trusted to
     * exchange messages correctly and the client should be torn down before
     * any retry. A non-fatal failure is scoped to a single operation (a
     * single message, media transfer, or sync request) and the rest of the
     * session can keep running.
     *
     * @return {@code true} if the current session can no longer be used,
     *         {@code false} if the failure is local to one operation
     */
    public abstract boolean isFatal();
}
