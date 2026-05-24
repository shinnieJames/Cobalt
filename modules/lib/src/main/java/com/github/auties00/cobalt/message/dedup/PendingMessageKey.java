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
 * Pure helper that derives the composite dedup string {@link MessageDedup}
 * uses to identify an in-flight inbound message.
 *
 * @apiNote Mirrors {@code WAWebPendingMessageKey.createPendingMessageKey}.
 * The key is deterministic in the message key, the message timestamp, and
 * the list of encrypted payloads carried on the incoming stanza; two
 * different ciphertexts of the same logical message id (for example a
 * {@code pkmsg} replayed as a plain {@code msg} on retry) therefore produce
 * different dedup strings and do not collide.
 *
 * @implNote This implementation matches the WA Web serialisation exactly: a
 * {@code msgKey.toString() + "_" + ts.unixSeconds + "_" +
 * encs.map(e -> e2eType + ":" + retryCount).join(",")} concatenation, with
 * the message key serialised through {@link #serializeKey(MessageKey)} to
 * track {@code WAWebMsgKey.prototype.toString}.
 */
@WhatsAppWebModule(moduleName = "WAWebPendingMessageKey")
public final class PendingMessageKey {
    /**
     * Hidden constructor; this is a static helper class.
     *
     * @throws AssertionError always
     */
    private PendingMessageKey() {
        throw new AssertionError("PendingMessageKey is a static helper and cannot be instantiated");
    }

    /**
     * Derives the composite dedup string for an incoming message.
     *
     * @apiNote The returned string has the shape
     * {@snippet :
     *     // <fromMe>_<remote>_<id>[_<participant>]_<unixSeconds>_<e2eType1>:<retry1>,<e2eType2>:<retry2>
     *     // example:
     *     //   false_12025550100@s.whatsapp.net_3EB0ABCD_1700000000_pkmsg:0,msg:1
     * }
     * where the {@code <fromMe>_<remote>_<id>[_<participant>]} prefix matches
     * {@code WAWebMsgKey.prototype.toString}, {@code unixSeconds} is the
     * timestamp's Unix-seconds value, and every {@link MessageReceiveEncryptedPayload}
     * contributes one {@code e2eType:retryCount} segment in iteration order.
     *
     * @implNote This implementation reproduces the WA Web join in
     * {@code WAWebPendingMessageKey}: stanza ids and encs are concatenated in
     * input order rather than sorted, and an empty encs list yields a
     * trailing underscore with no per-enc segment.
     *
     * @param key       the logical message key
     * @param timestamp the message timestamp, used as Unix-seconds
     * @param encs      the encrypted payloads on the incoming stanza; may be
     *                  empty but must not be {@code null}
     * @return the composite dedup string, never {@code null}
     * @throws NullPointerException if {@code key}, {@code timestamp}, or
     *                              {@code encs} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebPendingMessageKey", exports = "createPendingMessageKey",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static String create(MessageKey key, Instant timestamp, List<MessageReceiveEncryptedPayload> encs) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(encs, "encs cannot be null");

        var encsJoiner = new StringJoiner(",");
        for (var enc : encs) {
            encsJoiner.add(enc.e2eType().protocolValue() + ":" + enc.retryCount());
        }
        var encsSegment = encsJoiner.toString();

        return serializeKey(key) + "_" + timestamp.getEpochSecond() + "_" + encsSegment;
    }

    /**
     * Serialises a {@link MessageKey} into the underscore-joined form used by
     * {@code WAWebMsgKey.prototype.toString}.
     *
     * @apiNote Emits {@code fromMe_remote_id} for one-to-one keys and
     * {@code fromMe_remote_id_participant} for group keys; the participant
     * segment is suppressed when the sender JID equals the parent JID,
     * matching the {@code _serialized} field WA Web builds inside the
     * {@code MsgKey} constructor.
     *
     * @implNote This implementation omits the {@code selfDir} ("in" / "out")
     * field that WA Web appends to the serialised form when a one-to-one
     * conversation with the primary device is detected; Cobalt does not
     * track {@code selfDir} on the key, and including it would never line up
     * with the inbound replay loop that consumes the dedup string.
     *
     * @param key the message key to serialise
     * @return the underscore-joined string form
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgKey", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static String serializeKey(MessageKey key) {
        var base = key.fromMe() + "_"
                + key.parentJid().map(Object::toString).orElse("") + "_"
                + key.id().orElse("");
        return key.senderJid()
                .filter(sender -> !key.parentJid().map(parent -> parent.equals(sender)).orElse(false))
                .map(sender -> base + "_" + sender)
                .orElse(base);
    }
}
