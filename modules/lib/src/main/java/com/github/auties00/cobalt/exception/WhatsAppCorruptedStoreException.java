package com.github.auties00.cobalt.exception;

/**
 * Thrown when the persisted Cobalt store cannot be loaded because its
 * bytes are corrupt.
 *
 * @apiNote
 * Cobalt keeps a single on-disk store containing the registration
 * credentials, Signal protocol keys, identity material, and the cached
 * chat state. When the store is truncated, tampered with, or written by
 * an incompatible version, the deserializer raises this exception
 * instead of silently continuing with cryptographic material that cannot
 * be trusted. The configurable error handler typically logs the device
 * out so the application can drive a fresh pairing.
 *
 * @implNote
 * This implementation always reports the failure as fatal: the store
 * holds the keys needed to resume the session, so corruption leaves the
 * application with no usable identity. Cobalt collapses WA Web's
 * multi-IndexedDB schema into a single store, so a single corruption
 * affects every entity type at once.
 */
public final class WhatsAppCorruptedStoreException extends WhatsAppException {
    /**
     * Constructs a new corrupted store exception wrapping the specified cause.
     *
     * @param cause the underlying deserialization failure
     */
    public WhatsAppCorruptedStoreException(Throwable cause) {
        super("Store data is corrupted and cannot be deserialized", cause);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns {@code true}: a corrupted store
     * cannot yield usable keys, so the session cannot resume.
     */
    @Override
    public boolean isFatal() {
        return true;
    }
}
