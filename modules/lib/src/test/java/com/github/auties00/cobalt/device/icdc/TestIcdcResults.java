package com.github.auties00.cobalt.device.icdc;

import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;

import java.time.Instant;
import java.util.List;

/**
 * Test-only helper that exposes the package-private {@link IcdcResult}
 * constructor to test code in other packages.
 *
 * @apiNote
 * Lives in {@code com.github.auties00.cobalt.device.icdc} so it can call the
 * package-private {@link IcdcResult} constructor; production code never sees
 * this class because it lives only on the test classpath.
 */
public final class TestIcdcResults {
    /**
     * Hidden constructor; this is a static-helper class.
     */
    private TestIcdcResults() {
        throw new AssertionError("TestIcdcResults is not instantiable");
    }

    /**
     * Builds an {@link IcdcResult} from the supplied components.
     *
     * @apiNote
     * Any argument may be {@code null} to model an absent field. Use when the
     * test needs a specific {@link IcdcResult} shape rather than running the
     * full {@link IcdcComputer} pipeline.
     *
     * @param keyHash     the truncated identity key hash, or {@code null}
     * @param timestamp   the device-list snapshot timestamp, or {@code null}
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
     * Builds an {@link IcdcResult} with all fields set to {@code null}.
     *
     * @apiNote
     * Useful as a sentinel for tests that need a non-{@code null}
     * {@link IcdcResult} reference but do not care about its contents.
     *
     * @return the empty {@link IcdcResult}
     */
    public static IcdcResult empty() {
        return new IcdcResult(null, null, null, null);
    }
}
