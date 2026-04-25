package com.github.auties00.cobalt.message.dedup;

import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveEncryptedPayload;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.message.MessageKey;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Pure helper that builds the composite dedup key used by
 * {@link MessageDedup} to identify an in-flight message.
 *
 * <p>The key is a deterministic string derived from the message key,
 * the message timestamp, and the list of encrypted payloads carried on
 * the incoming stanza. Two messages that would produce the same key are
 * treated as duplicates by the offline-replay dedup cache; having the
 * composite include the encs list makes the key resilient to the case
 * where WhatsApp redelivers a different encryption variant of the same
 * logical message id during a retry.
 *
 * @implNote WAWebPendingMessageKey: one-export module that exposes
 * {@code createPendingMessageKey(key, ts, encs)}. The module body is
 * {@code r = encs.map(e => String(e.e2eType) + ":" + e.retryCount)
 *        .join(",");
 *  return key.toString() + "_" + ts.toString() + "_" + r;}.
 */
@WhatsAppWebModule(moduleName = "WAWebPendingMessageKey")
public final class PendingMessageKey {
    /**
     * Private constructor; this class is a namespace for the single
     * static helper and is not meant to be instantiated.
     */
    private PendingMessageKey() {
        throw new AssertionError("PendingMessageKey is a static helper and cannot be instantiated");
    }

    /**
     * Builds the composite pending-message cache key for a given
     * message key, timestamp, and list of encrypted payloads.
     *
     * <p>The returned string has the form
     * {@code <fromMe>_<remote>_<id>[_<participant>]_<ts>_<e2eType1>:<retryCount1>,<e2eType2>:<retryCount2>,...}
     * where the {@code <fromMe>_<remote>_<id>[_<participant>]} prefix
     * matches {@code WAWebMsgKey.prototype.toString}, {@code ts} is the
     * Unix-seconds value of the timestamp, and every enc payload
     * contributes a {@code e2eType:retryCount} segment joined by commas.
     *
     * @param key       the message key identifying the logical message
     * @param timestamp the message timestamp; serialised as its Unix
     *                  epoch-seconds value
     * @param encs      the list of encrypted payloads carried on the
     *                  incoming stanza; may be empty but must not be
     *                  {@code null}
     * @return the composite dedup key, never {@code null}
     * @throws NullPointerException if {@code key}, {@code timestamp},
     *         or {@code encs} is {@code null}
     *
     * @implNote WAWebPendingMessageKey.createPendingMessageKey: maps
     * each enc to {@code String(e.e2eType) + ":" + e.retryCount}, joins
     * with {@code ","}, and returns
     * {@code key.toString() + "_" + ts.toString() + "_" + r}. Cobalt
     * serialises {@code MessageEncryptionType} via its protocol value
     * ({@code "pkmsg"}, {@code "msg"}, {@code "skmsg"}, {@code "msmsg"})
     * and the timestamp via {@link Instant#getEpochSecond()} to match
     * the numeric string WA Web produces from the parsed {@code t}
     * stanza attribute.
     */
    @WhatsAppWebExport(moduleName = "WAWebPendingMessageKey", exports = "createPendingMessageKey",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static String create(MessageKey key, Instant timestamp, List<MessageReceiveEncryptedPayload> encs) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(encs, "encs cannot be null");

        // WAWebPendingMessageKey.createPendingMessageKey
        // Maps each enc payload to "e2eType:retryCount" and joins them with commas
        var encsJoiner = new StringJoiner(",");
        for (var enc : encs) {
            encsJoiner.add(enc.e2eType().protocolValue() + ":" + enc.retryCount());
        }
        var encsSegment = encsJoiner.toString();

        // WAWebPendingMessageKey.createPendingMessageKey
        // Concatenates key.toString() + "_" + ts.toString() + "_" + encsSegment
        return serializeKey(key) + "_" + timestamp.getEpochSecond() + "_" + encsSegment;
    }

    /**
     * Serialises a {@link MessageKey} the same way
     * {@code WAWebMsgKey.prototype.toString} does: the {@code fromMe}
     * flag, remote JID, id, and optional participant joined with
     * underscores.
     *
     * @param key the message key to serialise
     * @return the serialised form
     *
     * @implNote WAWebMsgKey.prototype.toString: returns
     * {@code [fromMe, remote, id, participant?].join("_")} cached as
     * {@code this._serialized}. Cobalt recomputes the string each call
     * because {@link MessageKey} is a protobuf value that does not
     * cache the serialisation.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgKey", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static String serializeKey(MessageKey key) {
        // WAWebMsgKey.prototype.toString
        // Builds the serialised form [fromMe, remote, id, participant?] joined by underscores
        var base = key.fromMe() + "_"
                + key.parentJid().map(Object::toString).orElse("") + "_"
                + key.id().orElse("");
        return key.senderJid()
                .filter(sender -> !key.parentJid().map(parent -> parent.equals(sender)).orElse(false))
                .map(sender -> base + "_" + sender)
                .orElse(base);
    }
}
