package com.github.auties00.cobalt.exception;

/**
 * Thrown when the cached list of devices linked to this account has
 * become too stale to be used for routing messages.
 *
 * <p>Every device in a WhatsApp account keeps its own record of the full
 * set of devices linked to that account, refreshed periodically from the
 * server by the same device-verification maintenance pass that drives
 * {@link WhatsAppAdvCheckException}. This exception marks the point at
 * which that record has aged past the staleness threshold the server is
 * willing to accept, so sending a message would risk targeting a device
 * that is no longer authorized or omitting one that has just been added.
 *
 * @apiNote
 * Raised before a send when the local device list is too old to trust;
 * {@link #isFatal()} reports {@code true}, so a configured
 * {@code WhatsAppClientErrorHandler} typically refreshes the list and
 * reconnects, or logs the device out, rather than discarding the event.
 *
 * @see WhatsAppAdvCheckException
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
     * list is required to route outgoing messages and cannot be trusted
     * while expired.
     */
    @Override
    public boolean isFatal() {
        return true;
    }
}
