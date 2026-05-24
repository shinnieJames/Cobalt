package com.github.auties00.cobalt.exception;

/**
 * Thrown when the cached list of devices linked to this account has
 * become too stale to be used for routing messages.
 *
 * @apiNote
 * Every device in a WhatsApp account keeps its own record of the full
 * set of devices linked to that account, refreshed periodically from
 * the server by the same ADV job that drives
 * {@link WhatsAppAdvCheckException}. This exception is raised when the
 * staleness threshold the server is willing to accept has been crossed.
 * Sending a message while the record is in this state would risk
 * targeting a device that is no longer authorized or omitting one that
 * has just been added. The configurable error handler decides whether
 * to refresh and reconnect or to log the device out.
 *
 * @implNote
 * This implementation always reports the failure as fatal: until the
 * device list is refreshed, the local view of the account cannot be
 * trusted for routing.
 */
public final class WhatsAppOwnDeviceListExpiredException extends WhatsAppException {
    /**
     * Constructs a new device list expired exception.
     */
    public WhatsAppOwnDeviceListExpiredException() {
        super("Own device list has expired");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns {@code true}: the local device
     * list is required for correctly routing outgoing messages and is
     * untrustworthy while expired.
     */
    @Override
    public boolean isFatal() {
        return true;
    }
}
