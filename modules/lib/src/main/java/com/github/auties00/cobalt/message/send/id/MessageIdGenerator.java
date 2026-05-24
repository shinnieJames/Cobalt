package com.github.auties00.cobalt.message.send.id;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.util.DataUtils;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Generates the per-message stanza id used as the {@code id} attribute on
 * every outbound WhatsApp {@code <message>}.
 *
 * @apiNote
 * Called once per outgoing message by the stanza-build pipeline (the Cobalt
 * counterpart of WA Web's {@code WAWebMsgKey.newId}); the returned string is
 * the wire identifier the server, the recipient, and every downstream
 * acknowledgement uses to refer to the message. Embedders that hand Cobalt a
 * pre-built {@link com.github.auties00.cobalt.model.message.MessageKey} have
 * already picked an id and should not call this. Every id starts with
 * {@value #PREFIX}; the suffix shape depends on the requested
 * {@linkplain MessageIdVersion version}.
 *
 * @see MessageIdVersion
 */
@WhatsAppWebModule(moduleName = "WAWebMsgKey")
@WhatsAppWebModule(moduleName = "WAWebMsgKeyNewId")
public final class MessageIdGenerator {
    /**
     * The four-character prefix shared by every WhatsApp Web message id.
     *
     * @apiNote
     * Hard-coded on both client (matching WA Web's {@code "3EB0"+...} literal
     * in {@code newId_DEPRECATED}) and server, so embedders inspecting an
     * incoming id can rely on this prefix as a sanity check.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgKey", exports = {"newId", "newId_DEPRECATED"},
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String PREFIX = "3EB0";

    /**
     * The number of leading SHA-256 digest bytes used for the V2 hex suffix.
     */
    private static final int V2_DIGEST_SLICE = 9;

    /**
     * The number of random bytes mixed into the V2 pre-image.
     */
    private static final int V2_RANDOM_BYTES = 16;

    /**
     * The number of random bytes hex-encoded into the V1 suffix.
     */
    private static final int V1_RANDOM_BYTES = 8;

    /**
     * The uppercase hex formatter that matches WA Web's
     * {@code WAHex.toHex}.
     */
    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    /**
     * Prevents instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private MessageIdGenerator() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Generates a fresh message id using the supplied
     * {@linkplain MessageIdVersion version}.
     *
     * @apiNote
     * Used by the send pipeline to mint a stanza id for every outbound
     * message; the {@code senderJid} is read into the V2 pre-image so two
     * accounts sending at the same instant cannot collide deterministically.
     * Typical usage:
     * {@snippet :
     *     var id = MessageIdGenerator.generate(MessageIdVersion.V2, store.jid().orElseThrow());
     * }
     * @implNote
     * This implementation silently falls back to {@link MessageIdVersion#V1}
     * when {@link MessageIdVersion#V2} is requested but the JCA does not
     * surface {@code SHA-256}; that branch matches WA Web's
     * try/catch-then-{@code newId_DEPRECATED} fallback in
     * {@code WAWebMsgKey.newId}.
     *
     * @param version   the algorithm version to use
     * @param senderJid the logged-in user's own PN user {@link Jid}; only read
     *                  for V2
     * @return the generated stanza id
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgKey", exports = "newId",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static String generate(MessageIdVersion version, Jid senderJid) {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(senderJid, "senderJid");
        return switch (version) {
            case V1 -> generateV1();
            case V2 -> {
                try {
                    yield generateV2(senderJid);
                } catch (NoSuchAlgorithmException _) {
                    yield generateV1();
                }
            }
        };
    }

    /**
     * Generates a V1 message id.
     *
     * @apiNote
     * Mirrors WA Web's {@code newId_DEPRECATED}: the prefix
     * {@value #PREFIX} followed by {@value #V1_RANDOM_BYTES} random bytes
     * formatted as 16 uppercase hex characters. Kept as the SHA-256-unavailable
     * fallback path for {@link #generate}.
     *
     * @return the V1 message id
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgKey", exports = "newId_DEPRECATED",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static String generateV1() {
        var randomBytes = DataUtils.randomByteArray(V1_RANDOM_BYTES);
        return PREFIX + HEX.formatHex(randomBytes);
    }

    /**
     * Generates a V2 message id.
     *
     * @apiNote
     * Equivalent to WA Web's
     * {@code WAWebMsgKeyNewId.getMsgKeyNewSHA256Id}: the prefix
     * {@value #PREFIX} followed by the first {@value #V2_DIGEST_SLICE} bytes
     * of {@code SHA256(int64(unixTime) || utf8(senderJid) || random(16))},
     * formatted as 18 uppercase hex characters.
     *
     * @param senderJid the sender PN user {@link Jid} mixed into the
     *                  pre-image
     * @return the V2 message id
     * @throws NoSuchAlgorithmException if SHA-256 is unavailable
     */
    private static String generateV2(Jid senderJid) throws NoSuchAlgorithmException {
        var payload = genMsgKeyUint(senderJid);
        return getMsgKeyNewSHA256Id(payload);
    }

    /**
     * Builds the SHA-256 pre-image bytes for a V2 message id.
     *
     * @apiNote
     * Matches WA Web's {@code genMsgKeyUint} byte layout
     * {@code int64(unixTime) || utf8(senderJid) || random(16)}. No length
     * prefix is written for the JID; the JID is followed directly by the
     * random bytes, so the recipient cannot parse the pre-image, only verify
     * the hash.
     *
     * @param senderJid the sender PN user {@link Jid} written verbatim into
     *                  the pre-image
     * @return the pre-image bytes ready to feed into SHA-256
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgKeyNewId", exports = "genMsgKeyUint",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static byte[] genMsgKeyUint(Jid senderJid) {
        var timestamp = Instant.now().getEpochSecond();
        var jidBytes = senderJid.toString().getBytes(StandardCharsets.UTF_8);
        var randomBytes = DataUtils.randomByteArray(V2_RANDOM_BYTES);
        var payload = new byte[Long.BYTES + jidBytes.length + randomBytes.length];
        var offset = 0;

        DataUtils.putLong(payload, offset, timestamp, ByteOrder.BIG_ENDIAN);
        offset += Long.BYTES;

        System.arraycopy(jidBytes, 0, payload, offset, jidBytes.length);
        offset += jidBytes.length;

        System.arraycopy(randomBytes, 0, payload, offset, randomBytes.length);

        return payload;
    }

    /**
     * Hashes the pre-image with SHA-256 and returns the prefixed V2 id.
     *
     * @apiNote
     * Mirrors WA Web's {@code getMsgKeyNewSHA256Id}: the digest is sliced to
     * {@value #V2_DIGEST_SLICE} bytes (18 hex characters) so the full id
     * including the {@value #PREFIX} prefix fits in 22 characters.
     *
     * @param payload the pre-image bytes produced by {@link #genMsgKeyUint}
     * @return {@value #PREFIX} concatenated with 18 uppercase hex characters
     * @throws NoSuchAlgorithmException if SHA-256 is unavailable
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgKeyNewId", exports = "getMsgKeyNewSHA256Id",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static String getMsgKeyNewSHA256Id(byte[] payload) throws NoSuchAlgorithmException {
        var digest = MessageDigest.getInstance("SHA-256").digest(payload);
        return PREFIX + HEX.formatHex(digest, 0, V2_DIGEST_SLICE);
    }
}
