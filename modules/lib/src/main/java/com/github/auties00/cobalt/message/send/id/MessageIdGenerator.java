package com.github.auties00.cobalt.message.send.id;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.util.DataUtils;

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
 *   <li>{@link MessageIdVersion#V2 V2} gives 18 hex chars from SHA-256
 *       (22 total)</li>
 *   <li>{@link MessageIdVersion#V1 V1} gives 16 random hex chars
 *       (20 total)</li>
 * </ul>
 *
 * @implNote WAWebMsgKey.newId: primary entry point; tries
 * WAWebMsgKeyNewId.getMsgKeyNewSHA256Id then falls back to newId_DEPRECATED.
 * @see MessageIdVersion
 */
@WhatsAppWebModule(moduleName = "WAWebMsgKey")
@WhatsAppWebModule(moduleName = "WAWebMsgKeyNewId")
public final class MessageIdGenerator {
    /**
     * The 4-character prefix shared by all WhatsApp Web message IDs.
     *
     * @implNote WAWebMsgKey.newId / WAWebMsgKey.newId_DEPRECATED:
     * both hard-code the literal {@code "3EB0"} as the message ID prefix.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgKey", exports = {"newId", "newId_DEPRECATED"},
            adaptation = WhatsAppAdaptation.DIRECT)
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
     * {@code WAWebMsgKeyNewId.getMsgKeyNewSHA256Id()} (V2), catches every
     * thrown error (logs it via {@code WALogger.ERROR(...).catching(n)}) and
     * returns {@code newId_DEPRECATED()} (V1). Cobalt narrows the fallback
     * catch to {@link NoSuchAlgorithmException} because that is the only
     * recoverable failure mode in the V2 path: clock access, JID stringify,
     * RNG draw, and {@link MessageDigest#digest} after a successful
     * {@code getInstance("SHA-256")} cannot throw checked exceptions, and
     * unchecked errors (OOM, programmer bugs) should propagate rather than
     * silently downgrade to V1.
     * WAWebMsgKeyNewId.genMsgKeyUint: uses
     * {@code getMePnUserOrThrow_DO_NOT_USE().toString()} as the JID
     * component of the SHA-256 pre-image, which is always the sender's
     * own PN user JID. Cobalt rejects a {@code null senderJid} with
     * {@link NullPointerException} instead of letting the WA Web
     * "me is undefined" error funnel through the V1 fallback, since a
     * null sender JID at this layer is a programmer error.
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
     * V1 (random-only): {@code "3EB0"} + 16 uppercase hex chars from 8 random bytes.
     *
     * @implNote WAWebMsgKey.newId_DEPRECATED: {@code "3EB0" + randomHex(8)},
     * where {@code randomHex(n)} produces {@code 2n} uppercase hex characters
     * from {@code n} random bytes.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgKey", exports = "newId_DEPRECATED",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static String generateV1() {
        // WARandomHex.randomHex(8): 8 random bytes → 16 hex chars
        var randomBytes = DataUtils.randomByteArray(V1_RANDOM_BYTES);
        return PREFIX + HEX.formatHex(randomBytes);
    }

    /**
     * V2 (SHA-256): {@code "3EB0"} + 18 uppercase hex chars from
     * {@code SHA256(int64(time) || utf8(jid) || random(16))[0:9]}.
     *
     * @param senderJid the sender PN user JID used in the pre-image
     * @return the V2 message ID
     * @throws NoSuchAlgorithmException if SHA-256 is unavailable
     *
     * @implNote WAWebMsgKey.newId: calls
     * {@code WAWebMsgKeyNewId.getMsgKeyNewSHA256Id()} and prepends the
     * {@code "3EB0"} prefix (the prefix concatenation lives in the
     * exported function itself).
     */
    private static String generateV2(Jid senderJid) throws NoSuchAlgorithmException {
        var payload = genMsgKeyUint(senderJid);
        return getMsgKeyNewSHA256Id(payload);
    }

    /**
     * Builds the SHA-256 pre-image bytes for a V2 message ID.
     *
     * <p>Layout: {@code int64(unixTime) || utf8(jid) || random(16)}.
     *
     * @param senderJid the sender PN user JID (stringified into the
     *                  pre-image without a length prefix)
     * @return the pre-image byte array
     *
     * @implNote WAWebMsgKeyNewId.genMsgKeyUint: builds the pre-image via
     * {@code new Binary()}, {@code writeInt64(unixTime())},
     * {@code writeString(getMePnUserOrThrow_DO_NOT_USE().toString())},
     * {@code writeBuffer(parseHex(randomHex(16)))}, then returns
     * {@code readByteArrayView()}. The WA export reads the sender JID
     * from {@code WAWebUserPrefsMeUser}; Cobalt receives it as a
     * parameter to avoid a hidden dependency on a global user store.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgKeyNewId", exports = "genMsgKeyUint",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static byte[] genMsgKeyUint(Jid senderJid) {
        // WATimeUtils.unixTime(): epoch seconds
        var timestamp = Instant.now().getEpochSecond();
        // WAWebUserPrefsMeUser.getMePnUserOrThrow_DO_NOT_USE().toString()
        var jidBytes = senderJid.toString().getBytes(StandardCharsets.UTF_8);
        // WAHex.parseHex(WARandomHex.randomHex(16)): 16 random bytes
        var randomBytes = DataUtils.randomByteArray(V2_RANDOM_BYTES);
        var payload = new byte[Long.BYTES + jidBytes.length + randomBytes.length];
        var offset = 0;

        // WABinary.writeInt64: big-endian signed 64-bit integer
        LONG_BE.set(payload, offset, timestamp);
        offset += Long.BYTES;

        // WABinary.writeString: raw UTF-8 bytes (no length prefix)
        System.arraycopy(jidBytes, 0, payload, offset, jidBytes.length);
        offset += jidBytes.length;

        // WABinary.writeBuffer: raw bytes appended verbatim
        System.arraycopy(randomBytes, 0, payload, offset, randomBytes.length);

        // Binary.readByteArrayView(): return the accumulated buffer
        return payload;
    }

    /**
     * Hashes the pre-image with SHA-256 and returns the prefixed V2 id.
     *
     * @param payload the pre-image produced by {@link #genMsgKeyUint}
     * @return {@code "3EB0"} concatenated with 18 uppercase hex chars
     * @throws NoSuchAlgorithmException if SHA-256 is unavailable
     *
     * @implNote WAWebMsgKeyNewId.getMsgKeyNewSHA256Id:
     * {@code "3EB0" + WAHex.toHex(new Uint8Array(await crypto.subtle.digest("SHA-256", genMsgKeyUint()), 0, 9))}.
     * {@code WAHex.toHex} emits uppercase hex, so Cobalt uses
     * {@link HexFormat#withUpperCase()}. The original WA export is
     * {@code async} and reads the pre-image from a no-arg
     * {@code genMsgKeyUint()}; Cobalt blocks on a virtual thread and
     * takes the pre-image as a parameter so the sender JID can be
     * injected explicitly.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgKeyNewId", exports = "getMsgKeyNewSHA256Id",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static String getMsgKeyNewSHA256Id(byte[] payload) throws NoSuchAlgorithmException {
        var digest = MessageDigest.getInstance("SHA-256").digest(payload);
        return PREFIX + HEX.formatHex(digest, 0, V2_DIGEST_SLICE);
    }
}
