package com.github.auties00.cobalt.message.dedup;

import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveEncryptedPayload;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Reference-counted, in-memory map of in-flight message keys that suppresses
 * duplicate work during the offline-delivery replay window.
 *
 * @apiNote When the WhatsApp socket reconnects, the server replays every
 * message that was not acknowledged before the disconnect. Without a guard
 * the same message would be processed twice (once from the replay stream,
 * once from any queued stanza that had already arrived during the previous
 * session). Callers ask {@link #isPending(String)} (or the
 * {@link MessageKey}/{@link Instant}/payload-list overload) before spending
 * work on a stanza and register the key via {@link #add(String)} to take
 * ownership; the cache is dropped in bulk by {@link #maybeClear(int)} when
 * the offline-delivery counter reaches zero. Backed by a single map; each
 * entry holds a reference count so multiple cooperating call sites can claim
 * the same key without premature eviction.
 *
 * @implNote This implementation mirrors {@code WAWebMessageDedupUtils}, which
 * holds the cache in a module-local {@code Map} keyed by the same composite
 * string produced by {@link PendingMessageKey#create(MessageKey, Instant, List)}.
 * Compared with WA Web the Cobalt cache is process-local and is dropped in
 * full on JVM restart; WA Web's cache is likewise in-memory and is dropped on
 * tab reload.
 */
@WhatsAppWebModule(moduleName = "WAWebMessageDedupUtils")
public final class MessageDedup {
    /**
     * Logger used for the dedup add, pending-hit, and clear events.
     */
    private static final System.Logger LOGGER = System.getLogger(MessageDedup.class.getName());

    /**
     * Map from composite dedup key to outstanding reference count.
     */
    @WhatsAppWebExport(moduleName = "WAWebMessageDedupUtils", exports = {"addPendingMessage", "hasPendingMessage", "maybeClearPendingMessages"},
            adaptation = WhatsAppAdaptation.ADAPTED)
    private final ConcurrentMap<String, Integer> pending;

    /**
     * Constructs an empty dedup cache.
     *
     * @apiNote A single instance is owned by {@link com.github.auties00.cobalt.message.receive.MessageReceivingService}
     * and shared across the inbound dispatch pipeline; tests can construct
     * additional instances to isolate their dedup state.
     */
    public MessageDedup() {
        this.pending = new ConcurrentHashMap<>();
    }

    /**
     * Returns whether the pending-message cache is currently enabled by the
     * server-side AB prop.
     *
     * @apiNote WA Web's {@code WAWebHandleMsg} gates every call to
     * {@link #add(MessageKey, Instant, List)} on this predicate so the server
     * can roll the dedup feature back without a client redeploy. Cobalt
     * callers should follow the same convention and check this before calling
     * {@link #add(String)} or its composite overload.
     *
     * @implNote This implementation reads the
     * {@link ABProp#WEB_PENDING_MESSAGE_CACHE_ENABLED} flag through
     * {@link ABPropsService#getBool(ABProp)} rather than wrapping it in
     * {@code Optional}; the AB-prop layer returns the server's most recent
     * value, defaulting to {@code false} when the prop has never been seen.
     *
     * @param abPropsService the AB props service consulted for the
     *                       {@code web_pending_message_cache_enabled} flag
     * @return {@code true} when the server has flipped the
     *         {@code web_pending_message_cache_enabled} AB prop on
     * @throws NullPointerException if {@code abPropsService} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebMessageDedupUtils", exports = "isPengingMessageCacheEnabled",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static boolean isCacheEnabled(ABPropsService abPropsService) {
        Objects.requireNonNull(abPropsService, "abPropsService");
        return abPropsService.getBool(ABProp.WEB_PENDING_MESSAGE_CACHE_ENABLED);
    }

    /**
     * Registers the given composite key as pending and returns its new
     * reference count.
     *
     * @apiNote Use when the composite key has already been built (for
     * example, when caching the same key from two different call sites). For
     * the canonical message-key/timestamp/encs derivation use the
     * {@link #add(MessageKey, Instant, List)} overload. The new key starts
     * with refcount {@code 1}; a re-registration atomically increments the
     * existing count.
     *
     * @param key the composite dedup key produced by
     *            {@link PendingMessageKey#create(MessageKey, Instant, List)}
     * @return the new reference count for {@code key}
     * @throws NullPointerException if {@code key} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebMessageDedupUtils", exports = "addPendingMessage",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public int add(String key) {
        Objects.requireNonNull(key, "key");

        var newCount = pending.merge(key, 1, Integer::sum);

        LOGGER.log(System.Logger.Level.DEBUG,
                "[message-dedup] add message: {0}, total: {1}", key, newCount);

        return newCount;
    }

    /**
     * Registers an incoming message as pending under its canonical composite
     * key and returns the new reference count.
     *
     * @apiNote Convenience overload that delegates to
     * {@link #add(String)} after deriving the composite key via
     * {@link PendingMessageKey#create(MessageKey, Instant, List)}. Mirrors
     * WA Web's {@code addPendingMessage(msgKey, ts, encs)} call signature in
     * {@code WAWebHandleMsg}.
     *
     * @param key       the logical message key
     * @param timestamp the message timestamp, serialised as Unix epoch
     *                  seconds
     * @param encs      the encrypted payloads carried on the incoming
     *                  {@code <message>} stanza
     * @return the new reference count for the derived composite key
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebMessageDedupUtils", exports = "addPendingMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    public int add(MessageKey key, Instant timestamp, List<MessageReceiveEncryptedPayload> encs) {
        return add(PendingMessageKey.create(key, timestamp, encs));
    }

    /**
     * Returns whether the given composite key is currently registered as
     * pending.
     *
     * @apiNote Used by the inbound dispatch loop to decide whether to skip a
     * stanza whose decryption is already in progress (or has already
     * completed) on another execution path. A debug log entry is emitted on a
     * cache hit so diagnostic output can be correlated with WA Web's
     * {@code [message-dedup] message ... is pending} traces.
     *
     * @param key the composite dedup key
     * @return {@code true} if at least one outstanding reference exists for
     *         {@code key}
     * @throws NullPointerException if {@code key} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebMessageDedupUtils", exports = "hasPendingMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean isPending(String key) {
        Objects.requireNonNull(key, "key");

        var count = pending.get(key);
        if (count == null) {
            return false;
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "[message-dedup] message {0} is pending, total: {1}", key, count);
        return true;
    }

    /**
     * Returns whether the canonical composite key for an incoming message is
     * already registered.
     *
     * @apiNote Convenience overload over {@link #isPending(String)} that
     * derives the composite key from the same triple the
     * {@link #add(MessageKey, Instant, List)} overload uses. The caller does
     * not need to retain the composite string between the
     * {@code isPending}/{@code add} pair.
     *
     * @param key       the logical message key
     * @param timestamp the message timestamp, serialised as Unix epoch
     *                  seconds
     * @param encs      the encrypted payloads carried on the incoming
     *                  {@code <message>} stanza
     * @return {@code true} if the derived composite key is registered
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebMessageDedupUtils", exports = "hasPendingMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean isPending(MessageKey key, Instant timestamp, List<MessageReceiveEncryptedPayload> encs) {
        return isPending(PendingMessageKey.create(key, timestamp, encs));
    }

    /**
     * Drops every entry in the cache when the supplied offline-delivery
     * counter reaches zero.
     *
     * @apiNote Called from the offline-info-bulletin handler with the
     * remaining-message counter the server reports; mirrors WA Web's
     * {@code WAWebHandleInfoBulletin} call to
     * {@code WAWebMessageDedupUtils.maybeClearPendingMessages(n.count)} after
     * the {@code OFFLINE} bulletin lands. Any message id that was pending
     * during the replay window is no longer at risk of being duplicated once
     * the offline-delivery phase ends, so the memory is released in bulk
     * rather than per entry.
     *
     * @param count the remaining-message counter reported by the server; the
     *              cache is cleared only when this is exactly zero
     */
    @WhatsAppWebExport(moduleName = "WAWebMessageDedupUtils", exports = "maybeClearPendingMessages",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void maybeClear(int count) {
        if (count == 0) {
            if (!pending.isEmpty()) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "[message-dedup] message cache cleared, total: {0}", pending.size());
            }
            pending.clear();
        }
    }

    /**
     * Unconditionally empties the cache.
     *
     * @apiNote Convenience for callers that have verified the
     * offline-delivery counter externally and want to drop the cache without
     * rechecking. Most callers should prefer {@link #maybeClear(int)} so the
     * guard stays in one place.
     */
    public void clear() {
        maybeClear(0);
    }

    /**
     * Decrements the reference count for the given composite key and removes
     * the entry once the count reaches zero.
     *
     * @apiNote Paired with {@link #add(String)} or
     * {@link #add(MessageKey, Instant, List)}; calling code releases a key
     * when the work that took ownership has either completed or has thrown.
     * A {@code remove} for an unknown key is a no-op.
     *
     * @param key the composite dedup key
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public void remove(String key) {
        Objects.requireNonNull(key, "key");

        pending.compute(key, (_, count) -> {
            if (count == null) {
                return null;
            }
            var decremented = count - 1;
            return decremented <= 0 ? null : decremented;
        });
    }

    /**
     * Returns the number of distinct composite keys currently in the cache.
     *
     * @apiNote Surfaces the {@code Map.size()} of the underlying refcount
     * map; useful for diagnostics and for assertions in dedup tests. The
     * value is the count of distinct keys, not the sum of their reference
     * counts.
     *
     * @return the cache size
     */
    @WhatsAppWebExport(moduleName = "WAWebMessageDedupUtils", exports = "maybeClearPendingMessages",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public int size() {
        return pending.size();
    }
}
