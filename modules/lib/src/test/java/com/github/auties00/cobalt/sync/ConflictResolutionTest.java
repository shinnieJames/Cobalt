package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Pins the factory and equality contract of {@link ConflictResolution}
 * against WhatsApp Web's
 * {@code WAWebSyncdResolveConflict.resolveConflict} return shape.
 *
 * @apiNote Cobalt-internal exercise of the
 * {@link ConflictResolution#of(ConflictResolutionState)} and
 * {@link ConflictResolution#merged(DecryptedMutation.Trusted)} factories
 * plus the auto-generated record equality. WA Web's equivalent shape is
 * the {@code conflictResolutionState} value and an optional merged
 * pending-mutation row that the per-handler {@code resolveConflicts}
 * implementations return.
 *
 * @implNote The fixture {@link DecryptedMutation.Trusted} is constructed
 * directly via the model builder rather than loaded from a captured
 * fixture; only the equality identity matters for these assertions, not
 * the wire shape, so a synthetic value with deterministic timestamps is
 * sufficient.
 */
@DisplayName("ConflictResolution")
class ConflictResolutionTest {

    /**
     * Builds a deterministic {@link DecryptedMutation.Trusted} usable as
     * a merged-mutation fixture.
     *
     * @apiNote Helper used by every nested test that needs a non-null
     * merged mutation; the value carries fixed timestamps so equality
     * comparisons across calls remain stable.
     *
     * @return a freshly built {@link DecryptedMutation.Trusted} fixture
     */
    private static DecryptedMutation.Trusted sampleTrusted() {
        var value = new SyncActionValueBuilder()
                .timestamp(Instant.ofEpochSecond(1700000000L))
                .build();
        return new DecryptedMutation.Trusted("idx", value, SyncdOperation.SET,
                Instant.ofEpochSecond(1700000000L), 3);
    }

    /**
     * Pins the contract of
     * {@link ConflictResolution#of(ConflictResolutionState)}.
     */
    @Nested
    @DisplayName("factory of(state) -- non-merged resolutions")
    class StateOnly {
        /**
         * The state passed in survives unchanged and the merged
         * mutation is {@code null}.
         */
        @Test
        @DisplayName("of(state) carries the state and a null merged mutation")
        void stateOnly() {
            var resolution = ConflictResolution.of(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL);
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL, resolution.state());
            assertNull(resolution.mergedMutation(),
                    "of(state) never carries a merged mutation");
        }

        /**
         * Every {@link ConflictResolutionState} variant round-trips
         * through the factory.
         */
        @Test
        @DisplayName("every ConflictResolutionState variant is constructible via of(state)")
        void everyVariantConstructible() {
            for (var variant : ConflictResolutionState.values()) {
                var r = ConflictResolution.of(variant);
                assertEquals(variant, r.state(), "variant=" + variant);
                assertNull(r.mergedMutation(), "variant=" + variant);
            }
        }
    }

    /**
     * Pins the contract of
     * {@link ConflictResolution#merged(DecryptedMutation.Trusted)}.
     */
    @Nested
    @DisplayName("factory merged(mutation) -- message-range handler merge")
    class MergedMutation {
        /**
         * The factory always reports the
         * {@link ConflictResolutionState#SKIP_REMOTE_DROP_LOCAL}
         * verdict regardless of the supplied mutation.
         */
        @Test
        @DisplayName("merged() forces SKIP_REMOTE_DROP_LOCAL state")
        void mergedForcesState() {
            var merged = sampleTrusted();
            var resolution = ConflictResolution.merged(merged);
            assertEquals(ConflictResolutionState.SKIP_REMOTE_DROP_LOCAL, resolution.state(),
                    "merged() always sets the SKIP_REMOTE_DROP_LOCAL state per WAWebSyncActionStore.doConflictResolution");
        }

        /**
         * The supplied {@link DecryptedMutation.Trusted} is stored by
         * reference, not copied or normalised.
         */
        @Test
        @DisplayName("merged() preserves the supplied mutation by reference")
        void mergedPreservesReference() {
            var merged = sampleTrusted();
            var resolution = ConflictResolution.merged(merged);
            assertSame(merged, resolution.mergedMutation(),
                    "the merged mutation must be stored as-is; the handler is the source of truth");
        }
    }

    /**
     * Pins the auto-generated record equality and {@code toString}
     * contract.
     */
    @Nested
    @DisplayName("record equality and toString")
    class RecordContract {
        /**
         * Two state-only resolutions with the same state are equal by
         * record contract.
         */
        @Test
        @DisplayName("two resolutions with the same state and null merged are equal")
        void equalsStateOnly() {
            assertEquals(
                    ConflictResolution.of(ConflictResolutionState.SKIP_REMOTE),
                    ConflictResolution.of(ConflictResolutionState.SKIP_REMOTE));
            assertEquals(
                    ConflictResolution.of(ConflictResolutionState.SKIP_REMOTE).hashCode(),
                    ConflictResolution.of(ConflictResolutionState.SKIP_REMOTE).hashCode());
        }

        /**
         * Two state-only resolutions with different states compare as
         * unequal.
         */
        @Test
        @DisplayName("resolutions with different state are not equal")
        void differentStateUnequal() {
            assertNotEquals(
                    ConflictResolution.of(ConflictResolutionState.SKIP_REMOTE),
                    ConflictResolution.of(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL));
        }

        /**
         * Two merged resolutions backed by an equal
         * {@link DecryptedMutation.Trusted} are equal.
         */
        @Test
        @DisplayName("two merged resolutions with the same Trusted are equal")
        void equalsMerged() {
            var merged = sampleTrusted();
            assertEquals(ConflictResolution.merged(merged), ConflictResolution.merged(merged));
        }

        /**
         * The auto-generated record {@code toString} mentions the
         * {@link ConflictResolutionState} for diagnostic logging.
         */
        @Test
        @DisplayName("toString includes the state name")
        void toStringIncludesState() {
            var s = ConflictResolution.of(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL).toString();
            assertNotNull(s);
            Assertions.assertTrue(s.contains("APPLY_REMOTE_DROP_LOCAL"),
                    "toString should surface the state name for diagnostic logging");
        }
    }
}
