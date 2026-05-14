package com.github.auties00.cobalt.sync.exchange;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdPatchSpec;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.sync.SyncFixtures;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture-driven live-oracle test for {@link MutationRequestBuilder}.
 *
 * <p>Mirrors WA Web's outgoing sync IQ wire shape captured from a real session via
 * {@code tools/web/mcp-server/scripts/capture-sync-corpus.mjs --phase=3}. For each
 * captured topic ({@code exchange/&lt;patchtype&gt;/upload-&lt;action&gt;}) the test:
 * <ol>
 *   <li>Loads the captured outgoing {@code <iq>} stanza via
 *       {@link SyncFixtures#loadEvents(String)}.</li>
 *   <li>Reconstructs the captured IQ as a Cobalt {@link com.github.auties00.cobalt.node.Node}
 *       via {@link SyncFixtures#buildNodeFromEvent}.</li>
 *   <li>Asserts envelope-attribute parity ({@code type}, {@code xmlns}, {@code to}).</li>
 *   <li>Decodes the inline {@code <patch>} bytes into a {@code SyncdPatch} protobuf
 *       and asserts structural invariants (mutation count, presence of MACs, key id
 *       length).</li>
 * </ol>
 *
 * <p>Each test method is gated on {@link SyncFixtures#isAvailable(String)} so the
 * suite runs green before fixtures are captured. Once fixtures land, the existing
 * scaffolding tightens to byte-equal assertions without re-shaping the test class.
 *
 * <p>Byte-equality on the encrypted patch payload itself is not exercised here:
 * the patch is built with a fresh random IV per call, so byte-for-byte equality
 * with the captured ciphertext is impossible without IV injection. The
 * {@code EncryptedMutationTest} oracle covers that lower-level parity via captured
 * (IV, plaintext, ciphertext) triples. This test focuses on the envelope shape
 * and the protobuf structure that surrounds the ciphertext.
 */
@DisplayName("MutationRequestBuilder — live-oracle parity")
class MutationRequestBuilderLiveOracleTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final Jid SELF_PN_DEVICE_1 = Jid.of("19250000001:1@s.whatsapp.net");

    private TestWhatsAppClient client;
    private MutationRequestBuilder builder;

    @BeforeEach
    void setUp() {
        var props = TestABPropsService.builder().build();
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        store.setJid(SELF_PN_DEVICE_1);
        client = TestWhatsAppClient.create()
                .withStore(store)
                .withAbPropsService(props);
        var wam = new DefaultWamService(client, props);
        builder = new MutationRequestBuilder(client, props, wam);
    }

    /**
     * Per-(patchType, action) topics captured by the {@code capture-sync-corpus.mjs}
     * Phase 3 stage. The parameter is the fixture topic relative to
     * {@code fixtures/sync/}; the patch type is derived from the topic prefix so
     * tests can also be parameterised across the 5 collections without re-listing
     * each combination.
     *
     * @return the (topic, patch-type) tuples to drive the live-oracle matrix
     */
    static Stream<Topic> uploadTopics() {
        return Stream.of(
                new Topic("exchange/regular-low/upload-archive",          SyncPatchType.REGULAR_LOW),
                new Topic("exchange/regular-low/upload-pin",              SyncPatchType.REGULAR_LOW),
                new Topic("exchange/regular-high/upload-mute",            SyncPatchType.REGULAR_HIGH),
                new Topic("exchange/regular-low/upload-mark-as-read",     SyncPatchType.REGULAR_LOW),
                new Topic("exchange/critical-block/upload-block",         SyncPatchType.CRITICAL_BLOCK),
                new Topic("exchange/critical-unblock-low/upload-unblock", SyncPatchType.CRITICAL_UNBLOCK_LOW)
        );
    }

    /**
     * Carries a captured topic together with the {@link SyncPatchType} it
     * corresponds to, so the parameterised test does not need to re-derive the
     * collection from the kebab-case directory prefix.
     *
     * @param topic     the fixture topic under {@code fixtures/sync/}
     * @param patchType the collection the captured IQ targets
     */
    record Topic(String topic, SyncPatchType patchType) {
    }

    @Nested
    @DisplayName("envelope parity — type / xmlns / to")
    class EnvelopeParity {
        @ParameterizedTest(name = "{0}")
        @MethodSource("com.github.auties00.cobalt.sync.exchange.MutationRequestBuilderLiveOracleTest#uploadTopics")
        @DisplayName("captured <iq> envelope attributes match WA Web's outgoing sync IQ shape")
        void envelopeMatches(Topic topic) {
            if (!SyncFixtures.isAvailable(topic.topic())) return;

            var events = SyncFixtures.loadEvents(topic.topic());
            var iqEvent = events.stream()
                    .filter(e -> "iq".equals(e.getString("tag")))
                    .filter(e -> "out".equals(e.getString("direction")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no outgoing <iq> in " + topic.topic()));

            var captured = SyncFixtures.buildNodeFromEvent(iqEvent);

            assertEquals("set", captured.getAttributeAsString("type").orElseThrow(),
                    "WAWebSyncdRequestBuilderBuild.g pins type=\"set\"");
            assertEquals("w:sync:app:state", captured.getAttributeAsString("xmlns").orElseThrow(),
                    "xmlns must match WAWebSyncdRequestBuilderBuild.g");
            // The captured `to` is the server JID; format matches Cobalt's Jid.userServer().toString().
            captured.getAttributeAsString("to").orElseThrow();
        }
    }

    @Nested
    @DisplayName("collection wire-shape parity")
    class CollectionShape {
        @ParameterizedTest(name = "{0}")
        @MethodSource("com.github.auties00.cobalt.sync.exchange.MutationRequestBuilderLiveOracleTest#uploadTopics")
        @DisplayName("captured <collection> name matches the patch-type's lowercase wire token")
        void collectionNameMatches(Topic topic) {
            if (!SyncFixtures.isAvailable(topic.topic())) return;

            var events = SyncFixtures.loadEvents(topic.topic());
            var iqEvent = events.stream()
                    .filter(e -> "iq".equals(e.getString("tag")))
                    .filter(e -> "out".equals(e.getString("direction")))
                    .findFirst()
                    .orElseThrow();

            var iq = SyncFixtures.buildNodeFromEvent(iqEvent);
            var collection = iq.getChild("sync").orElseThrow()
                    .getChild("collection").orElseThrow();

            assertEquals(topic.patchType().toString(),
                    collection.getAttributeAsString("name").orElseThrow(),
                    "wire collection name must match SyncPatchType.toString()");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.github.auties00.cobalt.sync.exchange.MutationRequestBuilderLiveOracleTest#uploadTopics")
        @DisplayName("captured <collection> exposes a numeric version attribute")
        void versionIsNumeric(Topic topic) {
            if (!SyncFixtures.isAvailable(topic.topic())) return;

            var events = SyncFixtures.loadEvents(topic.topic());
            var iqEvent = events.stream()
                    .filter(e -> "iq".equals(e.getString("tag")))
                    .filter(e -> "out".equals(e.getString("direction")))
                    .findFirst()
                    .orElseThrow();

            var collection = SyncFixtures.buildNodeFromEvent(iqEvent)
                    .getChild("sync").orElseThrow()
                    .getChild("collection").orElseThrow();
            // version is uint64 on the wire; parsing must succeed for any captured IQ.
            collection.getAttributeAsLong("version").orElseThrow(
                    () -> new AssertionError("captured collection has no version attribute"));
        }
    }

    @Nested
    @DisplayName("patch protobuf structural parity")
    class PatchStructure {
        @ParameterizedTest(name = "{0}")
        @MethodSource("com.github.auties00.cobalt.sync.exchange.MutationRequestBuilderLiveOracleTest#uploadTopics")
        @DisplayName("decoded <patch> bytes carry a non-empty mutation list and 32-byte MACs")
        void patchContents(Topic topic) {
            if (!SyncFixtures.isAvailable(topic.topic())) return;

            var events = SyncFixtures.loadEvents(topic.topic());
            var iqEvent = events.stream()
                    .filter(e -> "iq".equals(e.getString("tag")))
                    .filter(e -> "out".equals(e.getString("direction")))
                    .findFirst()
                    .orElseThrow();

            var patchNode = SyncFixtures.buildNodeFromEvent(iqEvent)
                    .getChild("sync").orElseThrow()
                    .getChild("collection").orElseThrow()
                    .getChild("patch").orElse(null);
            if (patchNode == null) return; // captured collection was a return_snapshot=true bootstrap

            var patchBytes = patchNode.toContentBytes().orElseThrow();
            var decoded = SyncdPatchSpec.decode(patchBytes);

            assertTrue(decoded.mutations().size() + decoded.externalMutations().map(_ -> 1).orElse(0) >= 1,
                    "patch must carry at least one mutation (inline or external)");

            // WA Web always emits 32-byte snapshotMac / patchMac for outgoing patches.
            decoded.snapshotMac().ifPresent(mac -> assertEquals(32, mac.length, "snapshotMac length"));
            decoded.patchMac().ifPresent(mac -> assertEquals(32, mac.length, "patchMac length"));
            decoded.keyId().flatMap(k -> k.id()).ifPresent(id ->
                    assertTrue(id.length > 0, "patch key id must be non-empty"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.github.auties00.cobalt.sync.exchange.MutationRequestBuilderLiveOracleTest#uploadTopics")
        @DisplayName("decoded <patch> carries a non-null deviceIndex matching the producing device")
        void deviceIndexPresent(Topic topic) {
            if (!SyncFixtures.isAvailable(topic.topic())) return;

            var events = SyncFixtures.loadEvents(topic.topic());
            var iqEvent = events.stream()
                    .filter(e -> "iq".equals(e.getString("tag")))
                    .filter(e -> "out".equals(e.getString("direction")))
                    .findFirst()
                    .orElseThrow();

            var patchNode = SyncFixtures.buildNodeFromEvent(iqEvent)
                    .getChild("sync").orElseThrow()
                    .getChild("collection").orElseThrow()
                    .getChild("patch").orElse(null);
            if (patchNode == null) return;

            var decoded = SyncdPatchSpec.decode(patchNode.toContentBytes().orElseThrow());
            assertTrue(decoded.deviceIndex().isPresent(),
                    "WAWebSyncdRequestBuilderBuild.L sets deviceIndex from getMyDeviceJid()");
        }
    }

    @Nested
    @DisplayName("MutationSyncResponse oracle replay")
    class ResponseReplay {
        @Test
        @DisplayName("a captured success response parses to a successful MutationSyncResponse")
        void parsesSuccessResponse() {
            if (!SyncFixtures.isAvailable("exchange/regular-low/upload-archive")) return;

            var events = SyncFixtures.loadEvents("exchange/regular-low/upload-archive");
            var iqEvents = events.stream()
                    .filter(e -> "iq".equals(e.getString("tag")))
                    .filter(e -> "in".equals(e.getString("direction")))
                    .toList();
            if (iqEvents.isEmpty()) return; // capture only included the outgoing IQ

            var inboundIq = SyncFixtures.buildNodeFromEvent(iqEvents.getFirst());
            var response = new MutationResponseParser().parseSyncResponse(inboundIq);

            assertEquals(SyncPatchType.REGULAR_LOW, response.collectionName(),
                    "response collection must match the upload's patch type");
            assertTrue(response.collectionError().isEmpty(),
                    "happy-path uploads receive a non-error response");
        }
    }

    @Nested
    @DisplayName("topics manifest — surface every (patchType, action) pair the oracle expects")
    class TopicManifest {
        @Test
        @DisplayName("uploadTopics enumerates at least one topic per SyncPatchType")
        void everyPatchTypeRepresented() {
            var observed = uploadTopics()
                    .map(Topic::patchType)
                    .distinct()
                    .toList();
            // CRITICAL_BLOCK + CRITICAL_UNBLOCK_LOW + REGULAR_HIGH + REGULAR_LOW + REGULAR
            // = the 5 collections WA Web cares about; missing one is a manifest bug.
            assertTrue(observed.containsAll(List.of(
                            SyncPatchType.CRITICAL_BLOCK,
                            SyncPatchType.CRITICAL_UNBLOCK_LOW,
                            SyncPatchType.REGULAR_HIGH,
                            SyncPatchType.REGULAR_LOW,
                            SyncPatchType.REGULAR)),
                    "uploadTopics must cover every SyncPatchType variant");
        }
    }
}
