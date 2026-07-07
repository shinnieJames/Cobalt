package com.github.auties00.cobalt.calls2.net.transport;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * Computes and verifies the two integrity attributes a finalized STUN message carries, the
 * {@code MESSAGE-INTEGRITY} HMAC-SHA1 keyed by the ICE password and the {@code FINGERPRINT} CRC32, both
 * over the carefully length-adjusted message prefix STUN defines.
 *
 * <p>STUN's two integrity attributes share one quirk: each is computed over the message header and all
 * preceding attributes, but with the header's sixteen-bit length field temporarily rewritten to the
 * length the message will have once that attribute is appended. The {@code MESSAGE-INTEGRITY} HMAC
 * (RFC 8489 section 14.6) is taken over the header and every attribute up to but not including itself,
 * with the length field set to span through the twenty-four-byte integrity attribute; the
 * {@code FINGERPRINT} CRC32 (RFC 8489 section 14.7) is taken over the header and every attribute up to
 * but not including itself, with the length field set to span through the eight-byte fingerprint
 * attribute, and the CRC32 is then XORed with the fixed value {@code 0x5354554E}. Both
 * helpers here take the message prefix exactly as it stands before the new attribute is appended and
 * apply the length rewrite internally, so a caller appends attributes, calls
 * {@link #computeMessageIntegrity(byte[], byte[])}, appends the resulting twenty bytes as a
 * {@link StunAttributeType#MESSAGE_INTEGRITY} attribute, calls {@link #computeFingerprint(byte[])},
 * and appends the resulting four bytes as a {@link StunAttributeType#FINGERPRINT} attribute, in that
 * order.
 *
 * <p>The length rewrite operates on a copy: neither helper mutates the caller's prefix array. The
 * verification helpers reverse the construction, recomputing the expected tag over the same
 * length-adjusted prefix and comparing it against the value carried in the message, the HMAC under a
 * constant-time comparison.
 *
 * @implNote This implementation reproduces {@code wa_stun_finalize_integrity_and_fingerprint} (fn4840)
 * and {@code wa_stun_verify_message_integrity} (fn4846) of {@code wa_stun_msg.cc} from the wa-voip WASM
 * module {@code ff-tScznZ8P}. The native finalizer writes the {@code MESSAGE-INTEGRITY} attribute
 * header, sets the STUN length to cover it, HMAC-SHA1s the buffer with the ICE password, writes the tag,
 * then writes the {@code FINGERPRINT} attribute header, sets the STUN length to cover it, CRC32s the
 * buffer, XORs with {@code 0x5354554E}, and writes the result, in that fixed order. HMAC-SHA1 and CRC32
 * are computed through {@code Mac("HmacSHA1")} and {@link java.util.zip.CRC32} rather than a native
 * binding, since both are exact standard algorithms (RFC 2104 HMAC over SHA-1, ISO 3309 CRC32) the
 * native engine reaches through statically-linked BoringSSL and zlib. The {@code 0x5354554E} constant
 * is the ASCII {@code "STUN"} the RFC fixes; the length-rewrite-on-a-copy avoids the in-place buffer
 * mutation the native code performs against its own scratch buffer.
 */
public final class StunIntegrity {
    /**
     * Holds the JCA algorithm name of the {@code MESSAGE-INTEGRITY} primitive.
     */
    private static final String HMAC_ALGORITHM = "HmacSHA1";

    /**
     * Holds the length, in bytes, of the {@code MESSAGE-INTEGRITY} HMAC-SHA1 tag.
     */
    public static final int MESSAGE_INTEGRITY_LENGTH = 20;

    /**
     * Holds the length, in bytes, of the {@code FINGERPRINT} CRC32 value.
     */
    public static final int FINGERPRINT_LENGTH = 4;

    /**
     * Holds the constant the {@code FINGERPRINT} CRC32 is XORed with, the ASCII bytes of {@code "STUN"}.
     */
    public static final long FINGERPRINT_XOR = 0x5354554EL;

    /**
     * Holds the byte offset of the STUN header's sixteen-bit message-length field.
     */
    private static final int LENGTH_FIELD_OFFSET = 2;

    /**
     * Holds the size, in bytes, of the fixed STUN message header that precedes the attribute section.
     */
    private static final int HEADER_LENGTH = 20;

    /**
     * Holds the total on-wire size, in bytes, of a {@code MESSAGE-INTEGRITY} attribute: a four-byte
     * type-length header plus the twenty-byte HMAC value.
     */
    private static final int MESSAGE_INTEGRITY_ATTR_SIZE = 4 + MESSAGE_INTEGRITY_LENGTH;

    /**
     * Holds the total on-wire size, in bytes, of a {@code FINGERPRINT} attribute: a four-byte
     * type-length header plus the four-byte CRC32 value.
     */
    private static final int FINGERPRINT_ATTR_SIZE = 4 + FINGERPRINT_LENGTH;

    /**
     * Holds the per-thread HMAC-SHA1 engine reused across {@code MESSAGE-INTEGRITY} computations.
     *
     * <p>The integrity helpers run on transport threads; the mutable JCA engine is thread-confined so a
     * concurrent computation on another transport thread cannot corrupt it. The ICE password varies per
     * call, so only the engine is reused and it is re-keyed on each computation.
     */
    private static final ThreadLocal<Mac> HMAC = ThreadLocal.withInitial(() -> {
        try {
            return Mac.getInstance(HMAC_ALGORITHM);
        } catch (GeneralSecurityException e) {
            throw new WhatsAppCallException.Srtp("Cannot create STUN MESSAGE-INTEGRITY HMAC-SHA1", e);
        }
    });

    /**
     * Prevents instantiation of this stateless integrity-primitive holder.
     */
    private StunIntegrity() {
        throw new AssertionError("StunIntegrity is not instantiable");
    }

    /**
     * Computes the {@code MESSAGE-INTEGRITY} HMAC-SHA1 over a STUN message prefix.
     *
     * <p>The {@code prefix} is the STUN header followed by every attribute that precedes the integrity
     * attribute, exactly as it stands before the integrity attribute is appended. This helper copies
     * the prefix, rewrites the copy's length field to the value the message will carry once the
     * twenty-four-byte integrity attribute is appended, HMAC-SHA1s the copy under {@code password}, and
     * returns the twenty-byte tag the caller writes as the integrity attribute value.
     *
     * @param prefix   the STUN header and preceding attributes, at least {@value #HEADER_LENGTH} bytes
     * @param password the ICE password keying the HMAC, in raw bytes
     * @return the {@value #MESSAGE_INTEGRITY_LENGTH}-byte HMAC-SHA1 tag
     * @throws NullPointerException       if {@code prefix} or {@code password} is {@code null}
     * @throws IllegalArgumentException   if {@code prefix} is shorter than the STUN header
     * @throws WhatsAppCallException.Srtp if the platform cannot compute HMAC-SHA1
     */
    public static byte[] computeMessageIntegrity(byte[] prefix, byte[] password) {
        Objects.requireNonNull(prefix, "prefix cannot be null");
        Objects.requireNonNull(password, "password cannot be null");
        var adjusted = withLengthThrough(prefix, MESSAGE_INTEGRITY_ATTR_SIZE);
        try {
            var mac = HMAC.get();
            mac.init(new SecretKeySpec(password, HMAC_ALGORITHM));
            return mac.doFinal(adjusted);
        } catch (GeneralSecurityException e) {
            throw new WhatsAppCallException.Srtp("Cannot compute STUN MESSAGE-INTEGRITY", e);
        }
    }

    /**
     * Computes the {@code FINGERPRINT} value over a STUN message prefix.
     *
     * <p>The {@code prefix} is the STUN header followed by every attribute that precedes the fingerprint
     * attribute, including the {@code MESSAGE-INTEGRITY} attribute when present, exactly as it stands
     * before the fingerprint attribute is appended. This helper copies the prefix, rewrites the copy's
     * length field to the value the message will carry once the eight-byte fingerprint attribute is
     * appended, CRC32s the copy, XORs the checksum with {@code 0x5354554E}, and returns the
     * four-byte big-endian result the caller writes as the fingerprint attribute value.
     *
     * @param prefix the STUN header and preceding attributes, at least {@value #HEADER_LENGTH} bytes
     * @return the {@value #FINGERPRINT_LENGTH}-byte fingerprint value, big-endian
     * @throws NullPointerException     if {@code prefix} is {@code null}
     * @throws IllegalArgumentException if {@code prefix} is shorter than the STUN header
     */
    public static byte[] computeFingerprint(byte[] prefix) {
        Objects.requireNonNull(prefix, "prefix cannot be null");
        var adjusted = withLengthThrough(prefix, FINGERPRINT_ATTR_SIZE);
        var crc = new CRC32();
        crc.update(adjusted);
        var value = (crc.getValue() ^ FINGERPRINT_XOR) & 0xFFFFFFFFL;
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }

    /**
     * Verifies that a STUN message's {@code MESSAGE-INTEGRITY} attribute matches the expected HMAC-SHA1
     * under the given password.
     *
     * <p>The {@code message} is the full STUN message; {@code integrityOffset} is the byte offset of the
     * {@code MESSAGE-INTEGRITY} attribute's type-length header. The expected tag is recomputed over the
     * message prefix up to {@code integrityOffset} with the length field rewritten to span through the
     * integrity attribute, exactly as {@link #computeMessageIntegrity(byte[], byte[])} does, then
     * compared in constant time against the twenty bytes carried in the message.
     *
     * @param message         the full STUN message bytes
     * @param integrityOffset the byte offset of the {@code MESSAGE-INTEGRITY} attribute header
     * @param password        the ICE password keying the HMAC, in raw bytes
     * @return {@code true} if the carried tag equals the recomputed tag, {@code false} otherwise
     * @throws NullPointerException       if {@code message} or {@code password} is {@code null}
     * @throws IllegalArgumentException   if {@code integrityOffset} does not leave room for the prefix
     *                                    and the twenty-four-byte integrity attribute
     * @throws WhatsAppCallException.Srtp if the platform cannot compute HMAC-SHA1
     */
    public static boolean verifyMessageIntegrity(byte[] message, int integrityOffset, byte[] password) {
        Objects.requireNonNull(message, "message cannot be null");
        Objects.requireNonNull(password, "password cannot be null");
        if (integrityOffset < HEADER_LENGTH
                || integrityOffset + MESSAGE_INTEGRITY_ATTR_SIZE > message.length) {
            throw new IllegalArgumentException("integrityOffset " + integrityOffset
                    + " does not fit a MESSAGE-INTEGRITY attribute in a " + message.length + "-byte message");
        }
        var prefix = Arrays.copyOf(message, integrityOffset);
        var expected = computeMessageIntegrity(prefix, password);
        var actual = Arrays.copyOfRange(
                message, integrityOffset + 4, integrityOffset + 4 + MESSAGE_INTEGRITY_LENGTH);
        return java.security.MessageDigest.isEqual(expected, actual);
    }

    /**
     * Verifies that a STUN message's trailing {@code FINGERPRINT} attribute matches the expected CRC32.
     *
     * <p>The {@code message} is the full STUN message ending in an eight-byte {@code FINGERPRINT}
     * attribute. The expected value is recomputed over the message prefix up to that attribute with the
     * length field rewritten to span through it, exactly as {@link #computeFingerprint(byte[])} does,
     * then compared against the four bytes carried in the message.
     *
     * @param message the full STUN message bytes, ending in a {@code FINGERPRINT} attribute
     * @return {@code true} if the carried value equals the recomputed value, {@code false} otherwise
     * @throws NullPointerException     if {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code message} is shorter than a header plus a
     *                                  {@code FINGERPRINT} attribute
     */
    public static boolean verifyFingerprint(byte[] message) {
        Objects.requireNonNull(message, "message cannot be null");
        if (message.length < HEADER_LENGTH + FINGERPRINT_ATTR_SIZE) {
            throw new IllegalArgumentException(
                    "message of " + message.length + " bytes is too short to hold a FINGERPRINT attribute");
        }
        var fingerprintOffset = message.length - FINGERPRINT_ATTR_SIZE;
        var prefix = Arrays.copyOf(message, fingerprintOffset);
        var expected = computeFingerprint(prefix);
        var actual = Arrays.copyOfRange(message, fingerprintOffset + 4, message.length);
        return Arrays.equals(expected, actual);
    }

    /**
     * Returns a copy of a STUN message prefix whose header length field is rewritten to span the prefix
     * attributes plus one trailing attribute of the given size.
     *
     * <p>STUN's integrity computations require the header length to reflect the message length through
     * the attribute being computed, even though that attribute is not yet present in the bytes being
     * hashed. The returned copy carries the attribute section length, that is the prefix length beyond
     * the twenty-byte header plus {@code trailingAttrSize}, in its sixteen-bit big-endian length field.
     *
     * @param prefix           the STUN header and preceding attributes
     * @param trailingAttrSize the on-wire size of the attribute about to be appended
     * @return a copy of {@code prefix} with the length field adjusted
     * @throws IllegalArgumentException if {@code prefix} is shorter than the STUN header
     */
    private static byte[] withLengthThrough(byte[] prefix, int trailingAttrSize) {
        if (prefix.length < HEADER_LENGTH) {
            throw new IllegalArgumentException(
                    "STUN prefix of " + prefix.length + " bytes is shorter than the " + HEADER_LENGTH
                            + "-byte header");
        }
        var copy = Arrays.copyOf(prefix, prefix.length);
        var attributeSectionLength = prefix.length - HEADER_LENGTH + trailingAttrSize;
        copy[LENGTH_FIELD_OFFSET] = (byte) (attributeSectionLength >>> 8);
        copy[LENGTH_FIELD_OFFSET + 1] = (byte) attributeSectionLength;
        return copy;
    }
}
