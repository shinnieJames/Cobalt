package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the construction, immutability, and equality contract of
 * {@link SyncPendingMutation} against the WhatsApp Web
 * {@code WAWebPendingMutationStore} row shape.
 *
 * @apiNote Cobalt-internal exercise of the
 * {@link SyncPendingMutation} record-style class. WA Web has no
 * directly comparable test surface; the equivalent fields live in
 * IndexedDB rows produced by
 * {@code WAWebSchemaPendingMutations.convertToPendingMutationFromRow}.
 *
 * @implNote The fixture {@link DecryptedMutation.Trusted} is built
 * directly through the model builder with deterministic timestamps;
 * only the equality identity matters for these assertions.
 */
@DisplayName("SyncPendingMutation")
class SyncPendingMutationTest {

    /**
     * Builds a deterministic {@link DecryptedMutation.Trusted} keyed
     * on the supplied index.
     *
     * @apiNote Helper used by every nested test that needs a concrete
     * mutation; the index is parameterised so tests can vary the
     * payload while keeping the timestamps stable.
     *
     * @param index the index string baked into the
     *              {@link DecryptedMutation.Trusted}
     * @return a freshly built fixture
     */
    private static DecryptedMutation.Trusted sampleTrusted(String index) {
        var value = new SyncActionValueBuilder()
                .timestamp(Instant.ofEpochSecond(1700000000L))
                .build();
        return new DecryptedMutation.Trusted(index, value, SyncdOperation.SET,
                Instant.ofEpochSecond(1700000000L), 3);
    }

    /**
     * Pins the two-arg and three-arg constructor contracts.
     */
    @Nested
    @DisplayName("construction")
    class Construction {
        /**
         * The auto-id constructor mints a fresh
         * {@code mutationId} per instance.
         */
        @Test
        @DisplayName("auto-id constructor generates a unique mutationId")
        void autoIdGenerated() {
            var a = new SyncPendingMutation(sampleTrusted("a"), 0);
            var b = new SyncPendingMutation(sampleTrusted("a"), 0);
            assertNotNull(a.mutationId());
            assertNotEquals(a.mutationId(), b.mutationId(),
                    "auto-id should be unique per instance (random UUID)");
        }

        /**
         * The explicit-id constructor stores the supplied id verbatim.
         */
        @Test
        @DisplayName("explicit-id constructor preserves the supplied id")
        void explicitIdPreserved() {
            var mutation = new SyncPendingMutation("custom-id", sampleTrusted("a"), 0);
            assertEquals("custom-id", mutation.mutationId());
        }

        /**
         * The {@code attemptCount} accessor returns the value passed
         * at construction time.
         */
        @Test
        @DisplayName("attemptCount is preserved verbatim")
        void attemptCountPreserved() {
            var mutation = new SyncPendingMutation(sampleTrusted("a"), 7);
            assertEquals(7, mutation.attemptCount());
        }

        /**
         * The {@code mutation} accessor returns the supplied
         * {@link DecryptedMutation.Trusted} by reference.
         */
        @Test
        @DisplayName("mutation accessor returns the supplied Trusted by reference")
        void mutationReferenceStable() {
            var trusted = sampleTrusted("a");
            var pending = new SyncPendingMutation(trusted, 0);
            assertSame(trusted, pending.mutation());
        }
    }

    /**
     * Pins the immutable functional-update behaviour of
     * {@link SyncPendingMutation#incrementAttempt()}.
     */
    @Nested
    @DisplayName("incrementAttempt -- functional update")
    class IncrementAttempt {
        /**
         * The increment produces a fresh instance with
         * {@code attemptCount + 1}.
         */
        @Test
        @DisplayName("incrementAttempt returns a new instance with attemptCount + 1")
        void incrementsAndCopies() {
            var original = new SyncPendingMutation("id", sampleTrusted("a"), 3);
            var bumped = original.incrementAttempt();
            assertNotSame(original, bumped, "increment must produce a new instance");
            assertEquals(4, bumped.attemptCount());
            assertEquals(3, original.attemptCount(), "original is immutable");
        }

        /**
         * The increment preserves both the original
         * {@code mutationId} and the underlying mutation reference.
         */
        @Test
        @DisplayName("incrementAttempt preserves mutationId and underlying mutation")
        void preservesIdAndMutation() {
            var trusted = sampleTrusted("a");
            var original = new SyncPendingMutation("id", trusted, 0);
            var bumped = original.incrementAttempt();
            assertEquals("id", bumped.mutationId(), "mutationId persists through incrementAttempt");
            assertSame(trusted, bumped.mutation());
        }

        /**
         * Repeated increments compose as a simple numeric counter.
         */
        @Test
        @DisplayName("incrementAttempt is repeatable")
        void incrementRepeatable() {
            var pending = new SyncPendingMutation("id", sampleTrusted("a"), 0);
            for (var i = 0; i < 5; i++) {
                pending = pending.incrementAttempt();
            }
            assertEquals(5, pending.attemptCount());
        }
    }

    /**
     * Pins the {@link SyncPendingMutation#equals(Object)} and
     * {@link SyncPendingMutation#hashCode()} contract over every
     * field.
     */
    @Nested
    @DisplayName("equality and hashCode")
    class Equality {
        /**
         * Equal-id, equal-mutation, equal-attempt instances compare
         * equal and hash equal.
         */
        @Test
        @DisplayName("same id, same mutation, same attempt are equal")
        void equalsByEveryField() {
            var trusted = sampleTrusted("a");
            var a = new SyncPendingMutation("id", trusted, 1);
            var b = new SyncPendingMutation("id", trusted, 1);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        /**
         * Different ids compare unequal even when the underlying
         * mutation and attempt count match.
         */
        @Test
        @DisplayName("different mutationId is unequal")
        void differingIdUnequal() {
            var trusted = sampleTrusted("a");
            assertNotEquals(
                    new SyncPendingMutation("id-1", trusted, 0),
                    new SyncPendingMutation("id-2", trusted, 0));
        }

        /**
         * Different attempt counts compare unequal even when the id
         * and underlying mutation match.
         */
        @Test
        @DisplayName("different attemptCount is unequal")
        void differingAttemptUnequal() {
            var trusted = sampleTrusted("a");
            assertNotEquals(
                    new SyncPendingMutation("id", trusted, 0),
                    new SyncPendingMutation("id", trusted, 1));
        }
    }

    /**
     * Pins the diagnostic {@link SyncPendingMutation#toString()}
     * format.
     */
    @Nested
    @DisplayName("toString -- diagnostic format")
    class ToString {
        /**
         * The string carries every record field with its label.
         */
        @Test
        @DisplayName("toString lists every field with its label")
        void toStringHasAllFields() {
            var pending = new SyncPendingMutation("the-id", sampleTrusted("the-index"), 7);
            var s = pending.toString();
            assertTrue(s.contains("mutationId=the-id"), "mutationId");
            assertTrue(s.contains("attemptCount=7"),     "attemptCount");
            assertTrue(s.contains("mutation="),           "mutation");
        }
    }
}
