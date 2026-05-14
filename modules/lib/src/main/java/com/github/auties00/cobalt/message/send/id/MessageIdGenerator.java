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
 * Generates unique message identifiers for the WhatsApp protocol.
 *
 * <p>Every id starts with {@value #PREFIX}. The remainder depends on the
 * {@linkplain MessageIdVersion version}: {@link MessageIdVersion#V2 V2}
 * appends 18 hex chars derived from SHA-256, while {@link MessageIdVersion#V1
 * V1} appends 16 random hex chars.
 *
 * @see MessageIdVersion
 */
@WhatsAppWebModule(moduleName = "WAWebMsgKey")
@WhatsAppWebModule(moduleName = "WAWebMsgKeyNewId")
public final class MessageIdGenerator {
    /**
     * Holds the four-character prefix shared by every WhatsApp Web message id.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgKey", exports = {"newId", "newId_DEPRECATED"},
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String PREFIX = "3EB0";

    /**
     * Holds the number of leading SHA-256 digest bytes used for the V2 hex
     * suffix.
     */
    private static final int V2_DIGEST_SLICE = 9;

    /**
     * Holds the number of random bytes mixed into the V2 pre-image.
     */
    private static final int V2_RANDOM_BYTES = 16;

    /**
     * Holds the number of random bytes hex-encoded into the V1 suffix.
     */
    private static final int V1_RANDOM_BYTES = 8;

    /**
     * Holds the uppercase hex formatter that matches WA Web's {@code WAHex.toHex}.
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
     * Generates a message id using the given algorithm version.
     *
     * <p>When {@code version} is {@link MessageIdVersion#V2 V2} and SHA-256 is
     * unavailable in the runtime, this method silently falls back to V1.
     *
     * @param version   the algorithm version
     * @param senderJid the logged-in user's own PN user JID; mixed into the V2
     *                  pre-image
     * @return the generated message id
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
     * Generates a V1 message id, made of {@code "3EB0"} followed by 16
     * uppercase hex characters drawn from 8 random bytes.
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
     * Generates a V2 message id, made of {@code "3EB0"} followed by 18
     * uppercase hex characters taken from the first 9 bytes of
     * {@code SHA256(int64(time) || utf8(jid) || random(16))}.
     *
     * @param senderJid the sender PN user JID mixed into the pre-image
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
     * <p>The layout is {@code int64(unixTime) || utf8(jid) || random(16)}.
     *
     * @param senderJid the sender PN user JID, written into the pre-image
     *                  without a length prefix
     * @return the pre-image bytes
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
     * @param payload the pre-image produced by {@link #genMsgKeyUint}
     * @return {@code "3EB0"} concatenated with 18 uppercase hex characters
     * @throws NoSuchAlgorithmException if SHA-256 is unavailable
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgKeyNewId", exports = "getMsgKeyNewSHA256Id",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static String getMsgKeyNewSHA256Id(byte[] payload) throws NoSuchAlgorithmException {
        var digest = MessageDigest.getInstance("SHA-256").digest(payload);
        return PREFIX + HEX.formatHex(digest, 0, V2_DIGEST_SLICE);
    }
}
