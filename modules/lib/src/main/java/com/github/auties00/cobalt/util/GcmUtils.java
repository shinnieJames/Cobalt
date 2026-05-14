package com.github.auties00.cobalt.util;

import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteOrder;

/**
 * Utilities for AES-GCM
 */
public final class GcmUtils {
    /**
     * Builds an AES-GCM parameter spec whose IV is derived from
     * {@code counter} using the standard zero-prefixed big-endian layout.
     *
     * @param counter the monotonically increasing counter value
     * @return a fresh {@link GCMParameterSpec} with a 128-bit tag and the
     *         derived 12-byte IV
     */
    public static GCMParameterSpec createNonce(long counter) {
        var iv = new byte[12];
        DataUtils.putLong(iv, 4, counter, ByteOrder.BIG_ENDIAN);
        return new GCMParameterSpec(128, iv);
    }
}
