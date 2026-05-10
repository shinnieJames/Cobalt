package com.github.auties00.cobalt.util;

import javax.crypto.spec.GCMParameterSpec;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Utilities for AES-GCM
 */
public final class GcmUtils {
    /**
     * Fast writes
     */
    private static final VarHandle LONG_VIEW = MethodHandles.byteArrayViewVarHandle(
            long[].class,
            ByteOrder.BIG_ENDIAN
    );

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
        LONG_VIEW.set(iv, 4, counter);
        return new GCMParameterSpec(128, iv);
    }
}
