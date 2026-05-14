package com.github.auties00.cobalt.device.icdc;

import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;

import java.time.Instant;
import java.util.List;

/**
 * Test-only helper that exposes the package-private
 * {@link IcdcResult} constructor to test code in other packages.
 *
 * <p>Living in {@code com.github.auties00.cobalt.device.icdc} gives this
 * class package access to the {@code IcdcResult} constructor.
 * Production code never sees this class — it's only on the test
 * classpath.
 */
public final class TestIcdcResults {
    /**
     * Hidden constructor; this is a static-helper class.
     */
    private TestIcdcResults() {
        throw new AssertionError("TestIcdcResults is not instantiable");
    }

    /**
     * Builds an {@link IcdcResult} from the supplied components. Any
     * argument may be {@code null} to model an absent field.
     *
     * @param keyHash     the truncated identity key hash, or {@code null}
     * @param timestamp   the device list timestamp, or {@code null}
     * @param keyIndexes  the indexes of included devices, or {@code null}
     * @param accountType the hosted account encryption type, or {@code null}
     * @return the constructed {@link IcdcResult}
     */
    public static IcdcResult create(
            byte[] keyHash,
            Instant timestamp,
            List<Integer> keyIndexes,
            ADVEncryptionType accountType
    ) {
        return new IcdcResult(keyHash, timestamp, keyIndexes, accountType);
    }

    /**
     * Builds an {@link IcdcResult} with all fields set to {@code null} —
     * useful as a sentinel for tests that need a non-{@code null}
     * {@link IcdcResult} reference but don't care about its contents.
     *
     * @return the empty {@link IcdcResult}
     */
    public static IcdcResult empty() {
        return new IcdcResult(null, null, null, null);
    }
}
