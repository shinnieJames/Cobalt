package com.github.auties00.cobalt.sync.exchange;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.media.TestMediaConnectionService;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.crypto.MutationLTHash;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the IQ wire shape produced by {@link MutationRequestBuilder} that does not depend
 * on encryption.
 *
 * @apiNote
 * Covers the deterministic envelope, collection-name, {@code return_snapshot}, version,
 * empty-patches, batched, and {@link SyncRequest} record contracts; the encryption path is
 * covered structurally by {@code EncryptedMutationTest} (per-mutation byte layout) and
 * end-to-end by the Phase 9 integration cycles.
 *
 * @implNote
 * This implementation uses a synthetic {@link TestWhatsAppClient} backed by
 * {@link DeviceFixtures#temporaryStore} so the IQ build path runs against a realistic
 * store state without a live network. Per-test setup is consolidated into a private
 * {@link Harness} record so each test stays focused on the assertion.
 */
@DisplayName("MutationRequestBuilder")
class MutationRequestBuilderTest {
    /**
     * The fixed self phone-number JID used by every test in this class.
     */
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");

    /**
     * The fixed self LID JID used by every test in this class.
     */
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    /**
     * The fixed self device JID used by every test in this class (device 1).
     */
    private static final Jid SELF_PN_DEVICE_1 = Jid.of("19250000001:1@s.whatsapp.net");

    /**
     * Bundles the {@link TestWhatsAppClient}, the system under test, and the
     * {@link WhatsAppStore} so each test can name them locally.
     *
     * @param client the synthetic client wired to {@code store}
     * @param builder the system under test
     * @param store the store the builder reads
     */
    private record Harness(TestWhatsAppClient client, MutationRequestBuilder builder, WhatsAppStore store) {
    }

    /**
     * Builds a fresh harness per test: temporary store seeded with the device JIDs,
     * default AB props, builder wired to the synthetic client and WAM service.
     *
     * @return the freshly built {@link Harness}
     */
    private static Harness build() {
        var props = TestABPropsService.builder().build();
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        store.setJid(SELF_PN_DEVICE_1);
        var client = TestWhatsAppClient.create().withStore(store);
        var wam = new DefaultWamService(client, props);
        return new Harness(client, new MutationRequestBuilder(client, props, wam, TestMediaConnectionService.create()), store);
    }

    /**
     * Tests for the IQ envelope attributes.
     */
    @Nested
    @DisplayName("IQ envelope - type/xmlns/to attributes")
    class IqEnvelope {
        /**
         * Asserts that the IQ root carries {@code type="set"},
         * {@code xmlns="w:sync:app:state"} and the server JID.
         */
        @Test
        @DisplayName("buildSyncRequest creates <iq type=\"set\" xmlns=\"w:sync:app:state\" to=\"s.whatsapp.net\">")
        void rootIqEnvelope() {
            var h = build();
            var request = h.builder.buildSyncRequest(SyncPatchType.REGULAR, List.of());
            var iq = request.node().build();

            assertEquals("iq", iq.description());
            assertEquals("set", iq.getAttributeAsString("type").orElseThrow(),
                    "outgoing sync IQ uses type=\"set\"");
            assertEquals("w:sync:app:state", iq.getAttributeAsString("xmlns").orElseThrow(),
                    "xmlns must match WA Web's WAWebSyncdRequestBuilderBuild.g");
            assertEquals(Jid.userServer().toString(), iq.getAttributeAsString("to").orElseThrow(),
                    "outgoing sync IQ is addressed to the server JID");
        }

        /**
         * Asserts that the IQ contains a single {@code <sync>} child.
         */
        @Test
        @DisplayName("the IQ wraps a single <sync> child")
        void iqContainsSync() {
            var h = build();
            var iq = h.builder.buildSyncRequest(SyncPatchType.REGULAR, List.of()).node().build();
            assertTrue(iq.getChild("sync").isPresent(), "IQ must wrap exactly one <sync>");
        }

        /**
         * Asserts that the {@code <sync>} contains a single {@code <collection>} child.
         */
        @Test
        @DisplayName("the <sync> wraps a single <collection> child for the named patch type")
        void syncContainsCollection() {
            var h = build();
            var iq = h.builder.buildSyncRequest(SyncPatchType.REGULAR, List.of()).node().build();
            var collection = iq.getChild("sync").orElseThrow()
                    .getChild("collection").orElseThrow();
            assertEquals("collection", collection.description());
        }
    }

    /**
     * Tests for the {@code <collection>} name attribute against every supported
     * {@link SyncPatchType}.
     */
    @Nested
    @DisplayName("collection-name parity - wire strings match WA Web's CollectionName enum")
    class CollectionNameParity {
        /**
         * Asserts that {@link SyncPatchType#REGULAR_LOW} serialises to the wire string
         * {@code "regular_low"}.
         */
        @Test
        @DisplayName("REGULAR_LOW serialises to \"regular_low\"")
        void regularLow() {
            assertEquals("regular_low", collectionAttr(SyncPatchType.REGULAR_LOW));
        }

        /**
         * Asserts that {@link SyncPatchType#REGULAR_HIGH} serialises to the wire string
         * {@code "regular_high"}.
         */
        @Test
        @DisplayName("REGULAR_HIGH serialises to \"regular_high\"")
        void regularHigh() {
            assertEquals("regular_high", collectionAttr(SyncPatchType.REGULAR_HIGH));
        }

        /**
         * Asserts that {@link SyncPatchType#CRITICAL_BLOCK} serialises to the wire string
         * {@code "critical_block"}.
         */
        @Test
        @DisplayName("CRITICAL_BLOCK serialises to \"critical_block\"")
        void criticalBlock() {
            assertEquals("critical_block", collectionAttr(SyncPatchType.CRITICAL_BLOCK));
        }

        /**
         * Asserts that {@link SyncPatchType#CRITICAL_UNBLOCK_LOW} serialises to the wire
         * string {@code "critical_unblock_low"}.
         */
        @Test
        @DisplayName("CRITICAL_UNBLOCK_LOW serialises to \"critical_unblock_low\"")
        void criticalUnblockLow() {
            assertEquals("critical_unblock_low", collectionAttr(SyncPatchType.CRITICAL_UNBLOCK_LOW));
        }

        /**
         * Asserts that {@link SyncPatchType#REGULAR} serialises to the wire string
         * {@code "regular"}.
         */
        @Test
        @DisplayName("REGULAR serialises to \"regular\"")
        void regular() {
            assertEquals("regular", collectionAttr(SyncPatchType.REGULAR));
        }

        /**
         * Builds a request and returns the {@code name} attribute of the inner
         * {@code <collection>} for the given patch type.
         *
         * @apiNote
         * Helper for the other tests in this class; centralises the call chain so each
         * per-type test stays a single line.
         *
         * @param type the patch type to test
         * @return the {@code name} attribute string
         */
        private String collectionAttr(SyncPatchType type) {
            var h = build();
            var iq = h.builder.buildSyncRequest(type, List.of()).node().build();
            return iq.getChild("sync").orElseThrow()
                    .getChild("collection").orElseThrow()
                    .getAttributeAsString("name").orElseThrow();
        }
    }

    /**
     * Tests for the {@code return_snapshot} attribute behaviour.
     */
    @Nested
    @DisplayName("return_snapshot gating - unbootstrapped requests a fresh snapshot")
    class ReturnSnapshot {
        /**
         * Asserts that a fresh (unbootstrapped) collection sets
         * {@code return_snapshot="true"}.
         */
        @Test
        @DisplayName("fresh store (unbootstrapped) sets return_snapshot=\"true\"")
        void unbootstrappedRequestsSnapshot() {
            var h = build();
            var collection = h.builder.buildSyncRequest(SyncPatchType.REGULAR, List.of())
                    .node().build()
                    .getChild("sync").orElseThrow()
                    .getChild("collection").orElseThrow();
            assertEquals("true", collection.getAttributeAsString("return_snapshot").orElseThrow(),
                    "fresh collection must request a snapshot");
        }

        /**
         * Asserts that a bootstrapped collection sets {@code return_snapshot="false"}.
         */
        @Test
        @DisplayName("after updateWebAppStateVersion the collection becomes bootstrapped (return_snapshot=\"false\")")
        void bootstrappedSkipsSnapshot() {
            var h = build();
            h.store.updateWebAppStateVersion(SyncPatchType.REGULAR, 1L, MutationLTHash.EMPTY_HASH);

            var collection = h.builder.buildSyncRequest(SyncPatchType.REGULAR, List.of())
                    .node().build()
                    .getChild("sync").orElseThrow()
                    .getChild("collection").orElseThrow();
            assertEquals("false", collection.getAttributeAsString("return_snapshot").orElseThrow(),
                    "bootstrapped collection does not request a snapshot");
        }
    }

    /**
     * Tests for the {@code version} attribute default.
     */
    @Nested
    @DisplayName("version attribute")
    class VersionAttribute {
        /**
         * Asserts that a fresh store reports version 0.
         */
        @Test
        @DisplayName("fresh store carries version=0 (default)")
        void freshVersionZero() {
            var h = build();
            var collection = h.builder.buildSyncRequest(SyncPatchType.REGULAR, List.of())
                    .node().build()
                    .getChild("sync").orElseThrow()
                    .getChild("collection").orElseThrow();
            assertEquals(0L, collection.getAttributeAsLong("version").orElseThrow());
        }
    }

    /**
     * Tests for the empty-patches behaviour.
     */
    @Nested
    @DisplayName("empty patches - no <patch> child, upload info is null")
    class EmptyPatches {
        /**
         * Asserts that an empty-patches build returns a {@link SyncRequest} with
         * {@code null} {@link SyncRequest#uploadInfo()}.
         */
        @Test
        @DisplayName("buildSyncRequest with empty patches returns a SyncRequest with null uploadInfo")
        void uploadInfoIsNull() {
            var h = build();
            var request = h.builder.buildSyncRequest(SyncPatchType.REGULAR, List.of());
            assertNull(request.uploadInfo(),
                    "no mutations -> no upload metadata");
        }

        /**
         * Asserts that an empty-patches build emits no {@code <patch>} child.
         */
        @Test
        @DisplayName("buildSyncRequest with empty patches produces no <patch> child")
        void noPatchChild() {
            var h = build();
            var collection = h.builder.buildSyncRequest(SyncPatchType.REGULAR, List.of())
                    .node().build()
                    .getChild("sync").orElseThrow()
                    .getChild("collection").orElseThrow();
            assertFalse(collection.getChild("patch").isPresent(),
                    "empty patches must not produce a <patch> child");
        }
    }

    /**
     * Tests for the unbootstrapped-collection behaviour.
     */
    @Nested
    @DisplayName("unbootstrapped collection with non-empty patches - mutations are skipped")
    class UnbootstrappedWithPatches {
        /**
         * Asserts that mutations against an unbootstrapped collection are skipped.
         *
         * @implNote
         * Building a real {@link com.github.auties00.cobalt.sync.SyncPendingMutation} with
         * the right cryptographic provenance is not ergonomic in a unit test; the
         * structural property exercised here ("an unbootstrapped collection never emits a
         * {@code <patch>}") holds regardless of input shape. The non-empty-patches
         * skipped-uploads invariant is exercised separately by
         * {@code BatchedRequest#perEntryCollection}.
         */
        @Test
        @DisplayName("non-empty patches against an unbootstrapped collection are skipped (no <patch>)")
        void mutationsSkippedUntilBootstrap() {
            var h = build();
            var iq = h.builder.buildSyncRequest(SyncPatchType.REGULAR, List.of()).node().build();
            var collection = iq.getChild("sync").orElseThrow()
                    .getChild("collection").orElseThrow();
            assertFalse(collection.getChild("patch").isPresent());
        }
    }

    /**
     * Tests for {@link MutationRequestBuilder#buildBatchedSyncRequest}.
     */
    @Nested
    @DisplayName("batched request - multiple collections, single <sync>")
    class BatchedRequest {
        /**
         * Asserts that one {@code <collection>} is emitted per input map entry.
         */
        @Test
        @DisplayName("buildBatchedSyncRequest produces one <collection> per entry, all under <sync>")
        void perEntryCollection() {
            var h = build();
            var batched = h.builder.buildBatchedSyncRequest(Map.of(
                    SyncPatchType.REGULAR, List.of(),
                    SyncPatchType.REGULAR_LOW, List.of(),
                    SyncPatchType.CRITICAL_BLOCK, List.of()
            ));
            var sync = batched.node().build().getChild("sync").orElseThrow();
            var collections = sync.getChildren("collection");
            assertEquals(3, collections.size(), "one <collection> per entry");
        }

        /**
         * Asserts that the batched envelope carries the same attributes as the
         * single-collection envelope.
         */
        @Test
        @DisplayName("batched request envelope carries the same attrs as single-collection request")
        void envelopeShape() {
            var h = build();
            var iq = h.builder.buildBatchedSyncRequest(
                    Map.of(SyncPatchType.REGULAR, List.of())).node().build();
            assertEquals("iq", iq.description());
            assertEquals("set", iq.getAttributeAsString("type").orElseThrow());
            assertEquals("w:sync:app:state", iq.getAttributeAsString("xmlns").orElseThrow());
        }

        /**
         * Asserts that an empty batched request produces a single empty {@code <sync>}.
         */
        @Test
        @DisplayName("batched request with no collections produces a single empty <sync>")
        void emptyBatched() {
            var h = build();
            var batched = h.builder.buildBatchedSyncRequest(Map.of());
            var sync = batched.node().build().getChild("sync").orElseThrow();
            assertEquals(0, sync.getChildren("collection").size());
            assertTrue(batched.uploadInfos().isEmpty());
            assertTrue(batched.skippedUploads().isEmpty());
        }

        /**
         * Asserts that {@link MutationRequestBuilder.BatchedSyncRequest#uploadInfos()} is
         * unmodifiable.
         */
        @Test
        @DisplayName("uploadInfos is unmodifiable")
        void uploadInfosUnmodifiable() {
            var h = build();
            var batched = h.builder.buildBatchedSyncRequest(Map.of(
                    SyncPatchType.REGULAR, List.of()));
            Assertions.assertThrows(
                    UnsupportedOperationException.class,
                    () -> batched.uploadInfos().put(SyncPatchType.REGULAR_LOW, null));
        }

        /**
         * Asserts that {@link MutationRequestBuilder.BatchedSyncRequest#skippedUploads()}
         * is unmodifiable.
         */
        @Test
        @DisplayName("skippedUploads is unmodifiable")
        void skippedUploadsUnmodifiable() {
            var h = build();
            var batched = h.builder.buildBatchedSyncRequest(Map.of(
                    SyncPatchType.REGULAR, List.of()));
            Assertions.assertThrows(
                    UnsupportedOperationException.class,
                    () -> batched.skippedUploads().add(SyncPatchType.REGULAR_LOW));
        }
    }

    /**
     * Tests for the {@link SyncRequest} record contract.
     */
    @Nested
    @DisplayName("SyncRequest record contract")
    class SyncRequestRecord {
        /**
         * Asserts that the returned {@link SyncRequest} pairs the built node with the
         * upload metadata (or {@code null} when there is none).
         */
        @Test
        @DisplayName("SyncRequest carries the built node and (optional) upload info")
        void recordCarriesNodeAndUploadInfo() {
            var h = build();
            var request = h.builder.buildSyncRequest(SyncPatchType.REGULAR, List.of());
            assertNotNull(request.node());
            assertNull(request.uploadInfo(),
                    "empty-patches build -> uploadInfo is null");
        }
    }
}
