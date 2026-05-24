package com.github.auties00.cobalt.exception;

import com.github.auties00.cobalt.client.WhatsAppClientErrorHandler;

/**
 * Sealed root of every exception thrown by Cobalt while talking to WhatsApp.
 *
 * @apiNote
 * Callers pattern-match on the concrete subtype to react to a specific
 * failure mode (a session conflict, a media download failure, an account
 * device verification mismatch, and so on). Each subtype documents the
 * WhatsApp feature it relates to and whether it should be treated as
 * fatal via {@link #isFatal()}. Cobalt does not pin a recovery action to
 * any exception: when a subtype is thrown, the configurable
 * {@link WhatsAppClientErrorHandler} decides whether the client should
 * discard the event, disconnect, reconnect, log out, or treat the account
 * as banned. Most embedders observe these exceptions through that handler
 * rather than through {@code try}/{@code catch} blocks around individual
 * calls.
 *
 * @implNote
 * This implementation is deliberately redesigned versus WhatsApp Web. WA
 * Web embeds inline recovery logic at each error site (try/catch with
 * retry, disconnect, or ignore); Cobalt throws the appropriate sealed
 * subtype and lets the user-configurable error handler decide the
 * recovery policy.
 *
 * @see #isFatal()
 *
 * @see WhatsAppClientErrorHandler
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
     * @apiNote
     * A fatal failure means the encrypted Noise channel can no longer be
     * trusted to exchange messages correctly and the client should be torn
     * down before any retry. A non-fatal failure is scoped to a single
     * operation (a single message, media transfer, or sync request) and
     * the rest of the session can keep running. The configurable error
     * handler consults this value when deciding between
     * {@code DISCARD}/{@code DISCONNECT}/{@code RECONNECT}/{@code LOG_OUT}
     * verdicts.
     *
     * @implSpec
     * Every concrete subtype declares a constant or per-instance answer.
     * Subtypes whose failure mode is structurally non-recoverable (Signal
     * MAC mismatch on the wire, account banned, server logout, ADV
     * validation failure, LID migration failure, corrupted store) must
     * return {@code true}; subtypes whose failure mode is local to a
     * single operation (media transfer, single message, single AB prop
     * lookup) must return {@code false}.
     *
     * @return {@code true} if the current session can no longer be used,
     *         {@code false} if the failure is local to one operation
     */
    public abstract boolean isFatal();
}
