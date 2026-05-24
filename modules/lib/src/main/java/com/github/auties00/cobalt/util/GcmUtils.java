package com.github.auties00.cobalt.util;

import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteOrder;

/**
 * Static helpers for assembling AES-GCM parameter specs.
 *
 * @apiNote
 * Call this when encrypting or decrypting payloads that are length-keyed
 * by a monotonically increasing counter, such as Noise transport
 * messages and the chunked media-stream cipher. The shape produced
 * matches the layout WhatsApp's wire protocols expect: a 12-byte IV
 * with the counter in the trailing eight bytes (big-endian) and a
 * 128-bit authentication tag.
 */
public final class GcmUtils {
    /**
     * Builds an AES-GCM {@link GCMParameterSpec} whose 12-byte IV
     * carries {@code counter} in the trailing eight bytes (big-endian)
     * and whose authentication tag length is 128 bits.
     *
     * @apiNote
     * Use this to derive the per-message IV for any AES-GCM cipher
     * that follows the WhatsApp Noise / media-stream convention of
     * deriving the IV from a monotonic counter. The first four bytes
     * of the IV are left zero, matching the wire format.
     *
     * @implNote
     * This implementation writes the counter via
     * {@link DataUtils#putLong(byte[], int, long, ByteOrder)} at
     * offset four; the {@link ByteOrder#BIG_ENDIAN} order matches
     * WhatsApp's Noise transport convention.
     *
     * @param counter the monotonic counter value to fold into the IV
     * @return a fresh {@link GCMParameterSpec} with a 128-bit tag and
     *         the derived 12-byte IV
     */
    public static GCMParameterSpec createNonce(long counter) {
        var iv = new byte[12];
        DataUtils.putLong(iv, 4, counter, ByteOrder.BIG_ENDIAN);
        return new GCMParameterSpec(128, iv);
    }
}
