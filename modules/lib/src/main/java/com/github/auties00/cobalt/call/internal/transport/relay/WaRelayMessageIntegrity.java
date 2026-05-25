package com.github.auties00.cobalt.call.internal.transport.relay;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * Computes, stamps, and verifies the {@code MESSAGE-INTEGRITY} attribute of a {@link WaRelayPacket}
 * (RFC 5389 section 15.4 with WhatsApp-specific keying).
 *
 * <p>The attribute carries a {@value #MAC_LENGTH}-byte HMAC-SHA1 over every byte of the packet
 * preceding it, that is, the header plus all earlier attributes. {@link #compute(byte[], byte[])}
 * produces the MAC, {@link #locate(byte[])} finds the attribute's header offset,
 * {@link #stamp(byte[], byte[])} writes the MAC into an encoded packet in place, and
 * {@link #verify(byte[], byte[])} checks it.
 *
 * @implNote This implementation keys the HMAC with the raw ASCII bytes of the base64 string form of
 * {@code RelayListUpdate.relay_key}, including the trailing {@code "=="} padding, rather than the
 * binary bytes obtained by base64-decoding it. The wasm engine passes the base64 string directly to
 * its pjsip-derived authentication routine as the password parameter; this keying was confirmed by
 * dumping the wasm linear memory of an active call worker, observing that {@code relay_key} only ever
 * appears as its base64 string in the heap, and matching the captured MAC against the HMAC keyed on
 * those ASCII bytes. For a {@code relay_key} of {@code "zDotFhSrw70H8cdxsSNCiQ=="} the 24-byte HMAC
 * key is the ASCII codes of those characters:
 * {@snippet :
 * // ASCII bytes of "zDotFhSrw70H8cdxsSNCiQ=="
 * 0x7A 0x44 0x6F 0x74 0x46 0x68 0x53 0x72 0x77 0x37 0x30 0x48
 * 0x38 0x63 0x64 0x78 0x73 0x53 0x4E 0x43 0x69 0x51 0x3D 0x3D
 * }
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSctpConnectionManager")
public final class WaRelayMessageIntegrity {
    /**
     * Holds the byte length of the HMAC-SHA1 output.
     */
    public static final int MAC_LENGTH = 20;

    /**
     * Holds the JCA algorithm name for HMAC-SHA1.
     */
    private static final String ALGORITHM = "HmacSHA1";

    /**
     * Holds the wire code of the {@code MESSAGE-INTEGRITY} attribute.
     */
    private static final int MI_TYPE = WaRelayAttributeType.MESSAGE_INTEGRITY.wireValue();

    /**
     * Prevents instantiation of this utility holder.
     */
    private WaRelayMessageIntegrity() {
        throw new AssertionError("no instances");
    }

    /**
     * Computes the HMAC-SHA1 of the given prefix bytes under the given key.
     *
     * @param prefixBytes the bytes to authenticate: the packet header plus every attribute that
     *                    precedes {@code MESSAGE-INTEGRITY}
     * @param key         the HMAC key derived from {@code RelayListUpdate.relay_key}
     * @return a fresh {@value #MAC_LENGTH}-byte MAC
     * @throws NullPointerException if {@code prefixBytes} or {@code key} is {@code null}
     * @throws IllegalStateException if the JCA provider does not offer {@value #ALGORITHM}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.DIRECT)
    public static byte[] compute(byte[] prefixBytes, byte[] key) {
        Objects.requireNonNull(prefixBytes, "prefixBytes cannot be null");
        Objects.requireNonNull(key, "key cannot be null");
        try {
            var mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(prefixBytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA1 unavailable", e);
        }
    }

    /**
     * Locates the {@code MESSAGE-INTEGRITY} attribute inside an encoded packet.
     *
     * <p>Walks the attribute block bounded by the header's {@code msgLength}, skipping each
     * attribute's value and 4-byte padding, and returns the offset of the matching attribute's 4-byte
     * header. Returns {@code -1} when the packet is shorter than {@link WaRelayPacket#HEADER_LENGTH},
     * when {@code msgLength} overruns the buffer, when an attribute overruns the block, or when no
     * {@code MESSAGE-INTEGRITY} attribute is present.
     *
     * @param packetBytes the full packet bytes
     * @return the header offset of the attribute, or {@code -1} when it is absent or the packet is
     *         malformed
     * @throws NullPointerException if {@code packetBytes} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.DIRECT)
    public static int locate(byte[] packetBytes) {
        Objects.requireNonNull(packetBytes, "packetBytes cannot be null");
        if (packetBytes.length < WaRelayPacket.HEADER_LENGTH) {
            return -1;
        }
        var buf = ByteBuffer.wrap(packetBytes).order(ByteOrder.BIG_ENDIAN);
        buf.position(2);
        var msgLen = buf.getShort() & 0xFFFF;
        var attrEnd = WaRelayPacket.HEADER_LENGTH + msgLen;
        if (attrEnd > packetBytes.length) {
            return -1;
        }
        buf.position(WaRelayPacket.HEADER_LENGTH);
        while (buf.position() + 4 <= attrEnd) {
            var pos = buf.position();
            var aType = buf.getShort() & 0xFFFF;
            var aLen = buf.getShort() & 0xFFFF;
            if (aType == MI_TYPE) {
                return pos;
            }
            if (buf.position() + aLen > attrEnd) {
                return -1;
            }
            buf.position(buf.position() + aLen);
            var pad = (4 - (aLen & 3)) & 3;
            buf.position(buf.position() + pad);
        }
        return -1;
    }

    /**
     * Verifies the {@code MESSAGE-INTEGRITY} attribute embedded in an encoded packet against the given
     * key.
     *
     * <p>Locates the attribute via {@link #locate(byte[])}, reads its claimed MAC, recomputes the
     * HMAC-SHA1 over the preceding bytes, and compares the two in constant time.
     *
     * @param packetBytes the full packet bytes
     * @param key         the HMAC key
     * @return {@code true} only when the packet contains a {@code MESSAGE-INTEGRITY} attribute whose
     *         value equals the HMAC-SHA1 of the preceding bytes
     * @throws NullPointerException     if {@code packetBytes} or {@code key} is {@code null}
     * @throws IllegalArgumentException if the packet contains no {@code MESSAGE-INTEGRITY} attribute or
     *                                  the attribute is truncated
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.DIRECT)
    public static boolean verify(byte[] packetBytes, byte[] key) {
        Objects.requireNonNull(packetBytes, "packetBytes cannot be null");
        Objects.requireNonNull(key, "key cannot be null");
        var offset = locate(packetBytes);
        if (offset < 0) {
            throw new IllegalArgumentException("packet has no MESSAGE-INTEGRITY attribute");
        }
        if (offset + 4 + MAC_LENGTH > packetBytes.length) {
            throw new IllegalArgumentException("MESSAGE-INTEGRITY attribute truncated");
        }
        var claimed = new byte[MAC_LENGTH];
        System.arraycopy(packetBytes, offset + 4, claimed, 0, MAC_LENGTH);
        var prefix = new byte[offset];
        System.arraycopy(packetBytes, 0, prefix, 0, offset);
        var actual = compute(prefix, key);
        return MessageDigest.isEqual(actual, claimed);
    }

    /**
     * Stamps the {@code MESSAGE-INTEGRITY} attribute of a freshly encoded packet by overwriting its
     * {@value #MAC_LENGTH} MAC bytes in place.
     *
     * <p>The packet must already contain a {@code MESSAGE-INTEGRITY} attribute, typically with a
     * zero-filled value placeholder; the header and preceding attributes are left untouched. Computes
     * the HMAC-SHA1 over the bytes preceding the attribute and writes it into the attribute's value.
     *
     * @param packetBytes the encoded packet to stamp, mutated in place
     * @param key         the HMAC key
     * @throws NullPointerException     if {@code packetBytes} or {@code key} is {@code null}
     * @throws IllegalArgumentException if the packet contains no {@code MESSAGE-INTEGRITY} attribute or
     *                                  the attribute is truncated
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.DIRECT)
    public static void stamp(byte[] packetBytes, byte[] key) {
        Objects.requireNonNull(packetBytes, "packetBytes cannot be null");
        Objects.requireNonNull(key, "key cannot be null");
        var offset = locate(packetBytes);
        if (offset < 0) {
            throw new IllegalArgumentException("packet has no MESSAGE-INTEGRITY attribute");
        }
        if (offset + 4 + MAC_LENGTH > packetBytes.length) {
            throw new IllegalArgumentException("MESSAGE-INTEGRITY attribute truncated");
        }
        var prefix = new byte[offset];
        System.arraycopy(packetBytes, 0, prefix, 0, offset);
        var mac = compute(prefix, key);
        System.arraycopy(mac, 0, packetBytes, offset + 4, MAC_LENGTH);
    }
}
