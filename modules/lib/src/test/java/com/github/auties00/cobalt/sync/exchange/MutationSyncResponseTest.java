package com.github.auties00.cobalt.sync.exchange;

import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
import com.github.auties00.cobalt.model.media.ExternalBlobReferenceBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the structural invariants of {@link MutationSyncResponse}.
 *
 * @apiNote
 * Covers the value-class contract: required vs nullable fields,
 * defensive defaults (null patches list resolves to an empty list at the accessor),
 * unmodifiable patches accessor, equals/hashCode field coverage, and toString format.
 *
 * @implNote
 * This implementation is fully synthetic; the tests build {@link MutationSyncResponse}
 * instances directly via the public constructors so {@link MutationResponseParser} is
 * not in scope here.
 */
@DisplayName("MutationSyncResponse")
class MutationSyncResponseTest {

    /**
     * Tests for the constructor's required vs nullable field contract.
     */
    @Nested
    @DisplayName("required vs nullable fields")
    class Construction {
        /**
         * Asserts that a {@code null} collection name is rejected at construction.
         */
        @Test
        @DisplayName("collection name must not be null")
        void collectionNameRequired() {
            assertThrows(NullPointerException.class,
                    () -> new MutationSyncResponse(null, 0L, false, List.of(), null));
        }

        /**
         * Asserts that an absent snapshot reference surfaces as an empty
         * {@link java.util.Optional}.
         */
        @Test
        @DisplayName("snapshot reference defaults to empty Optional")
        void snapshotReferenceOptional() {
            var response = new MutationSyncResponse(SyncPatchType.REGULAR, 0L, false, List.of(), null);
            assertTrue(response.snapshotReference().isEmpty());
            assertFalse(response.isSnapshot());
        }

        /**
         * Asserts that the five-arg constructor leaves
         * {@link MutationSyncResponse#collectionError()} empty.
         */
        @Test
        @DisplayName("collectionError defaults to empty Optional (single-arg constructor)")
        void collectionErrorOptional() {
            var response = new MutationSyncResponse(SyncPatchType.REGULAR, 0L, false, List.of(), null);
            assertTrue(response.collectionError().isEmpty());
        }

        /**
         * Asserts that an explicitly-supplied {@link WhatsAppWebAppStateSyncException}
         * surfaces through {@link MutationSyncResponse#collectionError()}.
         */
        @Test
        @DisplayName("explicit collectionError surfaces through Optional accessor")
        void explicitCollectionError() {
            var error = new WhatsAppWebAppStateSyncException.Conflict(false);
            var response = new MutationSyncResponse(SyncPatchType.REGULAR, 0L, false, List.of(), null, error);
            assertTrue(response.collectionError().isPresent());
            assertEquals(error, response.collectionError().orElseThrow());
        }

        /**
         * Asserts that a {@code null} patches list defaults to an empty list at the
         * accessor.
         */
        @Test
        @DisplayName("patches list defaults to empty when null is passed")
        void nullPatchesYieldsEmpty() {
            var response = new MutationSyncResponse(SyncPatchType.REGULAR, 0L, false, null, null);
            assertTrue(response.patches().isEmpty(),
                    "constructor null patches -> accessor returns empty list");
        }

        /**
         * Asserts that {@link MutationSyncResponse#isSnapshot()} reflects whether a
         * snapshot reference was supplied.
         */
        @Test
        @DisplayName("isSnapshot returns true iff snapshotReference was supplied")
        void isSnapshotReflectsReference() {
            var blob = new ExternalBlobReferenceBuilder().build();
            var snapshot = new MutationSyncResponse(SyncPatchType.REGULAR, 0L, false, List.of(), blob);
            var patches  = new MutationSyncResponse(SyncPatchType.REGULAR, 0L, false, List.of(), null);
            assertTrue(snapshot.isSnapshot());
            assertFalse(patches.isSnapshot());
        }
    }

    /**
     * Tests for the unmodifiable contract on the patches accessor.
     */
    @Nested
    @DisplayName("patches accessor - unmodifiable")
    class PatchesAccessor {
        /**
         * Asserts that mutating the returned patches collection throws
         * {@link UnsupportedOperationException}.
         */
        @Test
        @DisplayName("patches() returns an unmodifiable sequenced collection")
        void patchesIsUnmodifiable() {
            var response = new MutationSyncResponse(SyncPatchType.REGULAR, 0L, false, List.of(), null);
            assertThrows(UnsupportedOperationException.class,
                    () -> response.patches().add(null));
        }
    }

    /**
     * Tests for the equality and hash-code contract.
     */
    @Nested
    @DisplayName("equality and hashCode")
    class Equality {
        /**
         * Asserts that two responses with identical fields are equal and share a hash code.
         */
        @Test
        @DisplayName("two responses with the same fields are equal")
        void equalsByField() {
            var a = new MutationSyncResponse(SyncPatchType.REGULAR, 7L, true, List.of(), null);
            var b = new MutationSyncResponse(SyncPatchType.REGULAR, 7L, true, List.of(), null);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        /**
         * Asserts that a difference in {@code version} breaks equality.
         */
        @Test
        @DisplayName("differing version makes responses unequal")
        void inequalityByVersion() {
            var a = new MutationSyncResponse(SyncPatchType.REGULAR, 7L, true, List.of(), null);
            var b = new MutationSyncResponse(SyncPatchType.REGULAR, 8L, true, List.of(), null);
            assertNotEquals(a, b);
        }

        /**
         * Asserts that a difference in {@code collectionName} breaks equality.
         */
        @Test
        @DisplayName("differing collection name makes responses unequal")
        void inequalityByCollection() {
            var a = new MutationSyncResponse(SyncPatchType.REGULAR, 7L, true, List.of(), null);
            var b = new MutationSyncResponse(SyncPatchType.REGULAR_LOW, 7L, true, List.of(), null);
            assertNotEquals(a, b);
        }

        /**
         * Asserts that a difference in {@code hasMore} breaks equality.
         */
        @Test
        @DisplayName("differing hasMore makes responses unequal")
        void inequalityByHasMore() {
            var a = new MutationSyncResponse(SyncPatchType.REGULAR, 0L, false, List.of(), null);
            var b = new MutationSyncResponse(SyncPatchType.REGULAR, 0L, true, List.of(), null);
            assertNotEquals(a, b);
        }
    }

    /**
     * Tests for the toString diagnostic format.
     */
    @Nested
    @DisplayName("toString - diagnostic format")
    class ToStringFormat {
        /**
         * Asserts that {@link MutationSyncResponse#toString()} lists every field with its
         * label.
         */
        @Test
        @DisplayName("toString lists every field with its label")
        void toStringListsEveryField() {
            var response = new MutationSyncResponse(SyncPatchType.REGULAR_LOW, 99L, true, List.of(), null);
            var s = response.toString();
            assertTrue(s.contains("collectionName=" + SyncPatchType.REGULAR_LOW), "must include name");
            assertTrue(s.contains("version=99"), "must include version");
            assertTrue(s.contains("hasMore=true"), "must include hasMore");
        }
    }
}
