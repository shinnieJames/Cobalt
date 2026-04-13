package com.github.auties00.cobalt.message.send.id;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.util.FastRandomUtils;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Generates unique message identifiers for the WhatsApp protocol.
 *
 * <p>All generated IDs share the {@value #PREFIX} prefix.  The remainder
 * depends on the {@linkplain MessageIdVersion version} in use:
 * <ul>
 *   <li>{@link MessageIdVersion#V2 V2} — 18 hex chars from SHA-256
 *       (22 total)</li>
 *   <li>{@link MessageIdVersion#V1 V1} — 16 random hex chars
 *       (20 total)</li>
 * </ul>
 *
 * @implNote WAWebMsgKey.newId: primary entry point; tries
 * WAWebMsgKeyNewId.getMsgKeyNewSHA256Id then falls back to newId_DEPRECATED.
 * @see MessageIdVersion
 */
public final class MessageIdGenerator {
    /**
     * The 4-character prefix shared by all WhatsApp Web message IDs.
     *
     * @implNote WAWebMsgKey.newId / WAWebMsgKey.newId_DEPRECATED:
     * both hard-code the literal {@code "3EB0"} as the message ID prefix.
     */
    public static final String PREFIX = "3EB0";

    /**
     * Number of leading SHA-256 digest bytes used for the V2 hex suffix.
     *
     * @implNote WAWebMsgKeyNewId: {@code new Uint8Array(digest, 0, 9)}
     */
    private static final int V2_DIGEST_SLICE = 9;

    /**
     * Number of random bytes fed into the V2 pre-image payload.
     *
     * @implNote WAWebMsgKeyNewId.genMsgKeyUint: uses
     * {@code parseHex(randomHex(16))}, which yields 16 random bytes.
     */
    private static final int V2_RANDOM_BYTES = 16;

    /**
     * Number of random bytes used for the V1 hex suffix.
     *
     * @implNote WAWebMsgKey.newId_DEPRECATED: uses {@code randomHex(8)},
     * which generates 8 random bytes then hex-encodes them.
     */
    private static final int V1_RANDOM_BYTES = 8;

    /**
     * Uppercase hex formatter matching the output of
     * {@code WAHex.toHex}, which uses the uppercase alphabet
     * {@code [0-9A-F]}.
     *
     * @implNote WAHex.toHex: uses lookup table
     * {@code [48,49,...,65,66,67,68,69,70]} which maps to
     * {@code 0123456789ABCDEF}.
     */
    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    /**
     * VarHandle for writing a {@code long} in big-endian order into a
     * {@code byte[]}.
     *
     * @implNote WABinary.writeInt64: writes a 64-bit signed integer
     * in big-endian byte order.
     */
    private static final VarHandle LONG_BE = MethodHandles.byteArrayViewVarHandle(
            long[].class, ByteOrder.BIG_ENDIAN
    );

    /**
     * Prevents instantiation of this utility class.
     *
     * @implNote NO_WA_BASIS: Java utility class pattern.
     */
    private MessageIdGenerator() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Generates a message ID using the specified version.
     *
     * <p>If {@code version} is {@link MessageIdVersion#V2 V2} and SHA-256
     * is unavailable, this method silently falls back to V1.
     *
     * @param version   the algorithm version
     * @param senderJid the sender's own user JID (i.e. the logged-in
     *                   user's phone number JID, not the chat JID).
     *                   This is used as part of the V2 SHA-256 pre-image.
     * @return a new message ID string
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebMsgKey.newId: tries
     * {@code WAWebMsgKeyNewId.getMsgKeyNewSHA256Id()} (V2), catches errors
     * and falls back to {@code newId_DEPRECATED()} (V1).
     * WAWebMsgKeyNewId.genMsgKeyUint: uses
     * {@code getMePnUserOrThrow_DO_NOT_USE().toString()} as the JID
     * component of the SHA-256 pre-image, which is always the sender's
     * own PN user JID.
     */
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
     * V1 (random-only): {@code "3EB0"} + 16 uppercase hex chars from 8 random bytes.
     *
     * @implNote WAWebMsgKey.newId_DEPRECATED: {@code "3EB0" + randomHex(8)},
     * where {@code randomHex(n)} produces {@code 2n} uppercase hex characters
     * from {@code n} random bytes.
     */
    private static String generateV1() {
        // WARandomHex.randomHex(8): 8 random bytes → 16 hex chars
        var randomBytes = FastRandomUtils.randomByteArray(V1_RANDOM_BYTES);
        return PREFIX + HEX.formatHex(randomBytes);
    }

    /**
     * V2 (SHA-256): {@code "3EB0"} + 18 uppercase hex chars from
     * {@code SHA256(int64(time) || utf8(jid) || random(16))[0:9]}.
     *
     * @implNote WAWebMsgKeyNewId.getMsgKeyNewSHA256Id: builds pre-image via
     * genMsgKeyUint (WABinary.writeInt64 + writeString + writeBuffer),
     * then {@code "3EB0" + toHex(SHA256(payload)[0:9])}.
     */
    private static String generateV2(Jid senderJid) throws NoSuchAlgorithmException {
        // WAWebMsgKeyNewId.genMsgKeyUint: build the pre-image payload
        var timestamp = Instant.now().getEpochSecond();
        var jidBytes = senderJid.toString().getBytes(StandardCharsets.UTF_8);
        var randomBytes = FastRandomUtils.randomByteArray(V2_RANDOM_BYTES);
        var payload = new byte[Long.BYTES + jidBytes.length + randomBytes.length];
        var offset = 0;

        // WABinary.writeInt64: big-endian 64-bit timestamp
        LONG_BE.set(payload, offset, timestamp);
        offset += Long.BYTES;

        // WABinary.writeString: raw UTF-8 bytes (no length prefix)
        System.arraycopy(jidBytes, 0, payload, offset, jidBytes.length);
        offset += jidBytes.length;

        // WABinary.writeBuffer: raw random bytes
        System.arraycopy(randomBytes, 0, payload, offset, randomBytes.length);

        // WAWebMsgKeyNewId: SHA256(payload)[0:9] → hex
        var digest = MessageDigest.getInstance("SHA-256").digest(payload);
        return PREFIX + HEX.formatHex(digest, 0, V2_DIGEST_SLICE);
    }
}
