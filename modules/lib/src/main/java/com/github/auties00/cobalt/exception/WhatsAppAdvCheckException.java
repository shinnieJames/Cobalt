package com.github.auties00.cobalt.exception;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

/**
 * Thrown when the periodic Advanced Device Verification (ADV) maintenance
 * job fails.
 *
 * @apiNote
 * WA Web's {@code WAWebAdvDeviceInfoCheckJob} runs once per day to walk
 * every device list cached locally, evict companions whose stored
 * timestamp has crossed {@code num_days_key_index_list_expiration},
 * trigger a USync refresh for companions about to expire, and clear the
 * Signal sessions of evicted devices. This exception is raised when that
 * job cannot complete because the local store is unreachable, an AB prop
 * is missing, or a triggered resync fails. The configurable error handler
 * decides whether to log the failure, retry early, or escalate.
 *
 * @implNote
 * This implementation is non-fatal: the next scheduled run can recover
 * and message traffic in the meantime is unaffected.
 */
@WhatsAppWebModule(moduleName = "WAWebAdvDeviceInfoCheckJob")
public final class WhatsAppAdvCheckException extends WhatsAppException {

    /**
     * Constructs a new ADV check exception with no detail message.
     */
    public WhatsAppAdvCheckException() {
        super();
    }

    /**
     * Constructs a new ADV check exception with the specified detail message.
     *
     * @param message the detail message describing the check failure
     */
    public WhatsAppAdvCheckException(String message) {
        super(message);
    }

    /**
     * Constructs a new ADV check exception with a detail message and cause.
     *
     * @param message the detail message describing the check failure
     * @param cause   the underlying cause of the check failure
     */
    public WhatsAppAdvCheckException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new ADV check exception wrapping the specified cause.
     *
     * @param cause the underlying cause of the check failure
     */
    public WhatsAppAdvCheckException(Throwable cause) {
        super(cause);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns {@code false}: the periodic ADV
     * check runs in the background and a single failure does not affect
     * message sending or receiving.
     */
    @Override
    public boolean isFatal() {
        return false;
    }
}
