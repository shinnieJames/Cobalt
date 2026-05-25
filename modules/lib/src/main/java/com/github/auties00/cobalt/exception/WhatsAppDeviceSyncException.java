package com.github.auties00.cobalt.exception;

/**
 * Thrown when a USync device-list query against the WhatsApp servers
 * returns an error.
 *
 * USync is the request Cobalt issues before sending a message to learn the
 * set of devices that belong to each recipient. The server can reject the
 * request as a whole (a batch-wide failure that blocks the entire send) or
 * report a per-device issue inside an otherwise successful response (a
 * partial failure that still lets other recipients be addressed). The
 * {@code fatal} flag passed to the constructor mirrors that distinction
 * and is reflected in {@link #isFatal()}; the server-reported error code is
 * available through {@link #errorCode()}.
 *
 * @apiNote
 * Inspect {@link #isFatal()} to tell a batch-wide rejection from a partial
 * one: a non-fatal instance leaves the rest of the USync response usable,
 * so the send can proceed to the recipients that resolved.
 */
public final class WhatsAppDeviceSyncException extends WhatsAppException {
    /**
     * The numeric error code returned in the USync error stanza.
     */
    private final int errorCode;

    /**
     * Whether the USync server response marked this failure as
     * batch-wide.
     */
    private final boolean fatal;

    /**
     * Constructs a new device sync exception.
     *
     * @param errorCode the numeric error code returned by the server
     * @param errorText the human-readable description returned by the server
     * @param fatal     {@code true} when the failure affects the whole batch,
     *                  {@code false} when only a subset of devices failed
     */
    public WhatsAppDeviceSyncException(int errorCode, String errorText, boolean fatal) {
        super("USync error " + errorCode + ": " + errorText);
        this.errorCode = errorCode;
        this.fatal = fatal;
    }

    /**
     * Returns the numeric error code returned by the USync server.
     *
     * The code is the value the server reported for this failure, copied
     * verbatim at construction time.
     *
     * @apiNote
     * Use it to disambiguate the server-side failure mode beyond the
     * coarse {@link #isFatal()} distinction.
     *
     * @return the error code
     */
    public int errorCode() {
        return errorCode;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns the {@code fatal} flag captured at
     * construction time: {@code true} for batch-wide rejections and
     * {@code false} when only a subset of devices failed so the rest of
     * the response can still be used.
     */
    @Override
    public boolean isFatal() {
        return fatal;
    }
}
