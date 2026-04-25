package com.github.auties00.cobalt.model.sync;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.util.Arrays;
import java.util.Objects;

/**
 * Bookkeeping record describing the local state of a single app state sync
 * collection.
 *
 * <p>App state data is partitioned across a fixed set of collections (see
 * {@link SyncPatchType}), each with its own monotonically increasing version
 * number and integrity hash. This record gathers the metadata tracked per
 * collection: its name, current version, 128 byte LT-Hash, last sync and
 * error timestamps, current state in the sync lifecycle, retry counter,
 * persistent MAC mismatch flag, and a bootstrapped flag that distinguishes
 * a collection that has never been synced from one that has synced and is
 * simply empty.
 *
 * @implNote The {@code bootstrapped} component adapts WA Web's
 *           {@code WAWebSyncdCollectionUtils.isBootstrap(n)} predicate, which
 *           returns {@code n == null} (i.e. no local version recorded yet).
 *           Cobalt represents this as an explicit boolean field with inverted
 *           polarity ({@code bootstrapped == true} means a sync round has
 *           settled, equivalent to {@code !isBootstrap}). The check is
 *           inlined at each call site rather than exposed as a separate
 *           predicate method.
 * @param name the identifier of the collection
 * @param version the current version counter, monotonically increasing as
 *                mutations are applied
 * @param ltHash the current 128 byte LT-Hash used to detect tampering or
 *               missed mutations against the server hash
 * @param lastSyncTimestamp the wall clock time in epoch milliseconds of the
 *                          most recent successful sync
 * @param state the current state in the collection sync lifecycle
 * @param retryCount the number of consecutive retry attempts made after the
 *                   last error
 * @param lastErrorTimestamp the wall clock time in epoch milliseconds of
 *                           the most recent error
 * @param macMismatch whether a snapshot MAC mismatch has been observed on
 *                    this collection; once set, the flag persists across
 *                    state transitions so that the mismatch is remembered
 *                    for future diagnostics
 * @param bootstrapped whether this collection has completed at least one
 *                     sync round; a fresh collection is considered not
 *                     bootstrapped until the first sync settles, which is
 *                     distinct from a synced but empty collection at
 *                     version zero. Adapts WA Web
 *                     {@code WAWebSyncdCollectionUtils.isBootstrap} with
 *                     inverted polarity.
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdCollectionUtils")
public record SyncCollectionMetadata(
        SyncPatchType name,
        long version,
        byte[] ltHash,
        long lastSyncTimestamp,
        SyncCollectionState state,
        int retryCount,
        long lastErrorTimestamp,
        boolean macMismatch,
        boolean bootstrapped
) {
    /**
     * Canonical constructor that validates the argument values.
     *
     * @throws NullPointerException if {@code name}, {@code ltHash}, or
     *                              {@code state} is {@code null}
     * @throws IllegalArgumentException if {@code version} or
     *                                  {@code retryCount} is negative, or
     *                                  if {@code ltHash} is not exactly
     *                                  128 bytes long
     */
    public SyncCollectionMetadata {
        if (name == null) {
            throw new NullPointerException("Collection name cannot be null");
        }
        if (ltHash == null) {
            throw new NullPointerException("LT-Hash cannot be null");
        }
        if (state == null) {
            throw new NullPointerException("Collection state cannot be null");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Version cannot be negative: " + version);
        }
        if (ltHash.length != 128) {
            throw new IllegalArgumentException("LT-Hash must be 128 bytes, got " + ltHash.length);
        }
        if (retryCount < 0) {
            throw new IllegalArgumentException("Retry count cannot be negative: " + retryCount);
        }
    }

    /**
     * Returns a copy of this metadata with the retry counter incremented by
     * one and the last error timestamp set to the current wall clock time.
     *
     * @return a new metadata instance reflecting an additional retry
     *         attempt
     */
    public SyncCollectionMetadata incrementRetry() {
        return new SyncCollectionMetadata(
                name,
                version,
                ltHash,
                lastSyncTimestamp,
                state,
                retryCount + 1,
                System.currentTimeMillis(),
                macMismatch,
                bootstrapped
        );
    }

    /**
     * Returns a copy of this metadata with the retry counter reset to zero
     * and the last error timestamp cleared.
     *
     * @return a new metadata instance reflecting a cleared error state
     */
    public SyncCollectionMetadata resetRetry() {
        return new SyncCollectionMetadata(
                name,
                version,
                ltHash,
                lastSyncTimestamp,
                state,
                0,
                0,
                macMismatch,
                bootstrapped
        );
    }

    /**
     * Compares this metadata with another object for equality, using
     * deep equality for the LT-Hash byte array.
     *
     * @param o the other object to compare with
     * @return {@code true} if the other object is a
     *         {@link SyncCollectionMetadata} with the same component values
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof SyncCollectionMetadata(var thatName, var thatVersion, var thatHash, var thatSyncTimestamp, var thatState, var thatCount, var thatErrorTimestamp, var thatMacMismatch, var thatBootstrapped)
               && version == thatVersion
               && retryCount == thatCount
               && lastSyncTimestamp == thatSyncTimestamp
               && lastErrorTimestamp == thatErrorTimestamp
               && macMismatch == thatMacMismatch
               && bootstrapped == thatBootstrapped
               && Objects.equals(name, thatName)
               && Objects.deepEquals(ltHash, thatHash)
               && state == thatState;
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}, using
     * the array hash of the LT-Hash bytes.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, version, Arrays.hashCode(ltHash), lastSyncTimestamp, state, retryCount, lastErrorTimestamp, macMismatch, bootstrapped);
    }
}
