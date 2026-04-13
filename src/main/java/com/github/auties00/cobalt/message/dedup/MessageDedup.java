package com.github.auties00.cobalt.message.dedup;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A thread-safe cache of in-flight message IDs used to detect and suppress
 * duplicate send/receive attempts.
 *
 * <p>Before a message enters the send/receive pipeline, callers should check
 * {@link #isPending(String)} to determine whether an identical send/receive is
 * already in progress.  If not, {@link #add(String)} registers the key and
 * increments its reference count.
 *
 * <p>A single key may be added more than once (the cache keeps a reference
 * count); the key is considered pending as long as it exists in the cache.
 * The cache is cleared in bulk via {@link #maybeClear(int)} when the offline
 * delivery count reaches zero, matching WAWebMessageDedupUtils behavior.
 *
 * @implNote WAWebMessageDedupUtils: manages a module-level {@code Map} cache
 * of pending messages keyed by a composite string built from
 * {@code WAWebPendingMessageKey.createPendingMessageKey(key, ts, encs)}.
 * {@code addPendingMessage} increments a counter;
 * {@code hasPendingMessage} checks existence;
 * {@code maybeClearPendingMessages} clears the entire cache when the
 * offline delivery count equals zero.
 */
public final class MessageDedup {
    /**
     * Logger for dedup operations.
     *
     * @implNote WAWebMessageDedupUtils: uses {@code WALogger.LOG} for add, pending,
     * and clear operations with tagged template literal log messages.
     */
    private static final System.Logger LOGGER = System.getLogger(MessageDedup.class.getName());

    /**
     * Map from composite message key to its pending reference count.
     *
     * @implNote WAWebMessageDedupUtils: uses a module-level {@code Map} named
     * {@code c} with integer values, initialized as {@code new Map}.
     * Cobalt uses {@link ConcurrentHashMap} for thread safety on virtual threads.
     */
    private final ConcurrentMap<String, Integer> pending; // ADAPTED: WAWebMessageDedupUtils uses plain Map with integer values

    /**
     * Creates a new, empty deduplication cache.
     *
     * @implNote WAWebMessageDedupUtils: the module-level cache is initialized
     * as {@code new Map}.
     */
    public MessageDedup() {
        this.pending = new ConcurrentHashMap<>();
    }

    /**
     * Registers a message key as pending.
     *
     * <p>If the key is already pending, its reference count is incremented.
     * The reference count starts at 1 for the first addition.
     *
     * @param key the composite dedup key
     * @return the new reference count for this key
     * @throws NullPointerException if {@code key} is {@code null}
     * @implNote WAWebMessageDedupUtils.addPendingMessage: retrieves the current
     * counter (defaulting to 0), adds 1, stores it back via {@code c.set(key, count)},
     * and returns the new total. Logs {@code "[message-dedup] add message: key, total: count"}.
     */
    public int add(String key) {
        Objects.requireNonNull(key, "key");
        // WAWebMessageDedupUtils.addPendingMessage
        var newCount = pending.merge(key, 1, Integer::sum);
        LOGGER.log(System.Logger.Level.DEBUG,
                "[message-dedup] add message: {0}, total: {1}", key, newCount); // WAWebMessageDedupUtils.addPendingMessage
        return newCount;
    }

    /**
     * Returns whether a message key is currently pending in the cache.
     *
     * @param key the composite dedup key
     * @return {@code true} if the key exists in the cache
     * @throws NullPointerException if {@code key} is {@code null}
     * @implNote WAWebMessageDedupUtils.hasPendingMessage: retrieves the value
     * from the cache via {@code c.get(key)} and returns {@code false} if
     * {@code null}, {@code true} otherwise. Logs the key and count when pending.
     */
    public boolean isPending(String key) {
        Objects.requireNonNull(key, "key");
        // WAWebMessageDedupUtils.hasPendingMessage
        var count = pending.get(key);
        if (count == null) {
            return false;
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "[message-dedup] message {0} is pending, total: {1}", key, count); // WAWebMessageDedupUtils.hasPendingMessage
        return true;
    }

    /**
     * Conditionally clears the entire pending message cache.
     *
     * <p>The cache is only cleared when the provided {@code count} is zero,
     * which corresponds to the offline delivery count reaching zero.
     *
     * @param count the offline delivery count; the cache is cleared only
     *              when this is exactly zero
     * @implNote WAWebMessageDedupUtils.maybeClearPendingMessages: checks
     * {@code e === 0}, then if {@code c.size > 0} logs
     * {@code "[message-dedup] message cache cleared, total: size"}, and calls
     * {@code c.clear()}.
     */
    public void maybeClear(int count) {
        // WAWebMessageDedupUtils.maybeClearPendingMessages
        if (count == 0) {
            if (!pending.isEmpty()) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "[message-dedup] message cache cleared, total: {0}", pending.size()); // WAWebMessageDedupUtils.maybeClearPendingMessages
            }
            pending.clear();
        }
    }

    /**
     * Unconditionally clears the entire pending message cache.
     *
     * <p>This is a convenience overload that always clears regardless of
     * offline delivery count. Callers that need the conditional behavior
     * should use {@link #maybeClear(int)} instead.
     *
     * @implNote NO_WA_BASIS: WAWebMessageDedupUtils only exposes the
     * conditional {@code maybeClearPendingMessages(count)} which checks
     * {@code count === 0}. This unconditional variant exists for Cobalt
     * callers that have already performed the count check externally.
     */
    public void clear() {
        // NO_WA_BASIS
        maybeClear(0);
    }

    /**
     * Decrements the reference count for a message key and removes the
     * entry entirely when the count reaches zero.
     *
     * @param key the composite dedup key
     * @throws NullPointerException if {@code key} is {@code null}
     * @implNote NO_WA_BASIS: WAWebMessageDedupUtils has no per-entry removal.
     * Entries are only cleared in bulk by {@code maybeClearPendingMessages}.
     * This method exists for Cobalt's send-side dedup pattern where entries
     * are registered before a send and removed after the send completes.
     */
    public void remove(String key) {
        Objects.requireNonNull(key, "key");
        // NO_WA_BASIS
        pending.compute(key, (_, count) -> {
            if (count == null) {
                return null;
            }
            var decremented = count - 1;
            return decremented <= 0 ? null : decremented;
        });
    }

    /**
     * Returns the number of distinct message keys currently pending.
     *
     * @return the cache size
     * @implNote WAWebMessageDedupUtils.maybeClearPendingMessages: accesses
     * {@code c.size} inline for logging before clearing.
     */
    public int size() {
        return pending.size(); // ADAPTED: WAWebMessageDedupUtils accesses c.size inline
    }
}
