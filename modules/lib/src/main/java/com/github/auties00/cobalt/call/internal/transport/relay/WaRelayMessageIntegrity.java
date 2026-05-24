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
 * MESSAGE-INTEGRITY computation and verification for {@link WaRelayPacket}
 * (RFC 5389 §15.4 with WhatsApp-specific keying).
 *
 * <p>The MAC is the 20-byte HMAC-SHA1 of every byte of the packet
 * preceding the MESSAGE-INTEGRITY attribute. The packet's
 * {@code msgLength} header field is computed assuming the
 * MESSAGE-INTEGRITY attribute is present, which is naturally true for
 * fully-encoded packets.
 *
 * <p><strong>Key derivation (non-RFC):</strong> the HMAC key is the
 * <em>raw ASCII bytes of the base64 string</em> form of
 * {@code RelayListUpdate.relay_key}, padding {@code "=="} included.
 * It is NOT the binary bytes you'd get by base64-decoding it. The
 * wasm engine (pjsip-derived
 * {@code wa_stun_finalize_integrity_and_fingerprint}) passes the
 * base64 string directly to {@code pj_stun_authenticate_request}'s
 * {@code password} parameter. This was confirmed empirically by
 * dumping the wasm linear memory of an active call worker, observing
 * that {@code relay_key} only ever appears as its base64 string in
 * heap, and brute-force matching the captured MAC against
 * {@code HMAC-SHA1(base64_string_bytes, prefix)}.
 *
 * <p>For example, when {@code RelayListUpdate.relay_key} is
 * {@code "zDotFhSrw70H8cdxsSNCiQ=="}, the HMAC key is the 24 bytes
 * {@code 7A 44 6F 74 46 68 53 72 77 37 30 48 38 63 64 78 73 53 4E 43 69 51 3D 3D}
 * (the ASCII codes of those 24 characters).
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSctpConnectionManager")
public final class WaRelayMessageIntegrity {
    /**
     * Length in bytes of the HMAC-SHA1 output.
     */
    public static final int MAC_LENGTH = 20;

    /**
     * JCA algorithm name for HMAC-SHA1.
     */
    private static final String ALGORITHM = "HmacSHA1";

    /**
     * Wire code of the MESSAGE-INTEGRITY attribute.
     */
    private static final int MI_TYPE = WaRelayAttributeType.MESSAGE_INTEGRITY.wireValue();

    /**
     * Prevents instantiation.
     */
    private WaRelayMessageIntegrity() {
        throw new AssertionError("no instances");
    }

    /**
     * Computes the HMAC-SHA1 of the given prefix bytes with the given
     * key.
     *
     * @param prefixBytes the bytes to MAC (the packet header plus every
     *                    attribute that precedes MESSAGE-INTEGRITY)
     * @param key         the HMAC key (the {@code relay_key} from
     *                    {@code RelayListUpdate})
     * @return a fresh 20-byte MAC
     * @throws NullPointerException if any argument is {@code null}
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
     * Locates the MESSAGE-INTEGRITY attribute inside an encoded packet
     * and returns its 4-byte header offset, or {@code -1} when absent.
     *
     * @param packetBytes the full packet bytes
     * @return the header offset, or {@code -1} if the attribute is
     *         missing or the packet is malformed
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
     * Verifies the MESSAGE-INTEGRITY attribute embedded in the given
     * fully-encoded packet against the given key.
     *
     * @param packetBytes the full packet bytes
     * @param key         the HMAC key
     * @return {@code true} iff the packet contains a MESSAGE-INTEGRITY
     *         attribute whose value matches the HMAC-SHA1 of the
     *         preceding bytes
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the packet does not contain a
     *                                  MESSAGE-INTEGRITY attribute or
     *                                  is malformed
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
     * Stamps a MESSAGE-INTEGRITY attribute into a freshly-encoded
     * packet by overwriting the 20 MAC bytes in place. The packet must
     * already contain a MESSAGE-INTEGRITY attribute (typically with a
     * zero-filled value placeholder); the surrounding header and
     * preceding attributes are left untouched.
     *
     * @param packetBytes the encoded packet to stamp; mutated in place
     * @param key         the HMAC key
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the packet does not contain a
     *                                  MESSAGE-INTEGRITY attribute or
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
