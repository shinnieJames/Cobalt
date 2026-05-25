package com.github.auties00.cobalt.util;

import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteOrder;

/**
 * Assembles AES-GCM parameter specs for WhatsApp's counter-keyed wire ciphers.
 *
 * <p>Encrypting or decrypting payloads that are length-keyed by a monotonically
 * increasing counter, such as Noise transport messages and the chunked
 * media-stream cipher, requires a per-message {@link GCMParameterSpec}. The
 * shape produced here matches the layout WhatsApp's wire protocols expect: a
 * 12-byte IV with the counter in the trailing eight bytes (big-endian) and a
 * 128-bit authentication tag.
 */
public final class GcmUtils {
    /**
     * Builds an AES-GCM {@link GCMParameterSpec} from a monotonic counter.
     *
     * <p>The returned spec carries a 12-byte IV that holds {@code counter} in
     * its trailing eight bytes in {@link ByteOrder#BIG_ENDIAN} order, with the
     * leading four bytes left zero, and declares a 128-bit authentication tag.
     * This is the per-message IV derivation every AES-GCM cipher that follows
     * the WhatsApp Noise / media-stream convention relies on.
     *
     * @implNote
     * This implementation writes the counter via
     * {@link DataUtils#putLong(byte[], int, long, ByteOrder)} at offset four;
     * the {@link ByteOrder#BIG_ENDIAN} order matches WhatsApp's Noise transport
     * convention.
     *
     * @param counter the monotonic counter value to fold into the IV
     * @return a fresh {@link GCMParameterSpec} with a 128-bit tag and the
     *         derived 12-byte IV
     */
    public static GCMParameterSpec createNonce(long counter) {
        var iv = new byte[12];
        DataUtils.putLong(iv, 4, counter, ByteOrder.BIG_ENDIAN);
        return new GCMParameterSpec(128, iv);
    }
}
