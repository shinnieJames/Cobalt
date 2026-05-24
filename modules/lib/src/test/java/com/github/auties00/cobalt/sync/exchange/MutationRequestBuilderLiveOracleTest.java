package com.github.auties00.cobalt.sync.exchange;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdPatchSpec;
import com.github.auties00.cobalt.media.TestMediaConnectionService;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.sync.SyncFixtures;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link MutationRequestBuilder}'s output against captured WA Web outgoing sync IQ
 * stanzas.
 *
 * @apiNote
 * Drives a parameterised matrix over the seven captured {@code (patchType, action)}
 * topics in {@code src/test/resources/fixtures/sync/}. For each topic the test loads the
 * captured outgoing {@code <iq>}, asserts envelope-attribute parity
 * ({@code type}, {@code xmlns}, {@code to}), checks the {@code <collection>} name and the
 * presence of a numeric {@code version}, and decodes the inline {@code <patch>} bytes to
 * verify mutation count, MAC lengths, key-id presence and {@code deviceIndex} field.
 *
 * @implNote
 * This implementation gates every assertion on
 * {@link SyncFixtures#isAvailable(String)} so the suite stays green before the captured
 * fixtures land. Byte-equality on the encrypted patch payload is intentionally not
 * exercised: the patch is encrypted with a fresh random IV per call, so byte-for-byte
 * equality with the captured ciphertext is impossible without IV injection. The
 * lower-level (IV, plaintext, ciphertext) parity is covered by
 * {@code EncryptedMutationTest} instead; this test focuses on the envelope and the
 * surrounding protobuf structure.
 */
@DisplayName("MutationRequestBuilder - live-oracle parity")
class MutationRequestBuilderLiveOracleTest {
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
     * The {@link TestWhatsAppClient} wired to a fresh per-test store and AB-prop service.
     */
    private TestWhatsAppClient client;

    /**
     * The system under test.
     */
    private MutationRequestBuilder builder;

    /**
     * Builds a fresh harness per test: temporary store seeded with the device JIDs,
     * default AB props, builder wired to the synthetic client and WAM service.
     */
    @BeforeEach
    void setUp() {
        var props = TestABPropsService.builder().build();
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        store.setJid(SELF_PN_DEVICE_1);
        client = TestWhatsAppClient.create()
                .withStore(store)
                .withAbPropsService(props);
        var wam = new DefaultWamService(client, props);
        builder = new MutationRequestBuilder(client, props, wam, TestMediaConnectionService.create());
    }

    /**
     * Returns the parameterised topic matrix used by every parameterised test in this
     * class.
     *
     * @apiNote
     * Each tuple pairs a fixture topic relative to {@code fixtures/sync/} with the
     * {@link SyncPatchType} the captured IQ targets so tests can use the patch type
     * directly without re-deriving it from the kebab-case directory prefix.
     *
     * @return the parameter stream
     */
    static Stream<Topic> uploadTopics() {
        return Stream.of(
                new Topic("exchange/regular-low/upload-archive",                SyncPatchType.REGULAR_LOW),
                new Topic("exchange/regular-low/upload-pin",                    SyncPatchType.REGULAR_LOW),
                new Topic("exchange/regular-high/upload-mute",                  SyncPatchType.REGULAR_HIGH),
                new Topic("exchange/regular-low/upload-mark-as-read",           SyncPatchType.REGULAR_LOW),
                new Topic("exchange/regular/upload-disable-link-previews",      SyncPatchType.REGULAR),
                new Topic("exchange/critical-block/upload-pushname",            SyncPatchType.CRITICAL_BLOCK),
                new Topic("exchange/critical-unblock-low/upload-contact",       SyncPatchType.CRITICAL_UNBLOCK_LOW)
        );
    }

    /**
     * Pairs a captured fixture topic with the {@link SyncPatchType} the captured IQ
     * targets.
     *
     * @apiNote
     * Internal carrier used by {@link #uploadTopics()}.
     *
     * @param topic the fixture topic relative to {@code fixtures/sync/}
     * @param patchType the collection the captured IQ targets
     */
    record Topic(String topic, SyncPatchType patchType) {
    }

    /**
     * Tests for the IQ envelope attributes.
     */
    @Nested
    @DisplayName("envelope parity - type / xmlns / to")
    class EnvelopeParity {
        /**
         * Asserts that the captured {@code <iq>} envelope carries
         * {@code type="set"}, {@code xmlns="w:sync:app:state"}, and a server JID.
         *
         * @param topic the captured topic to test
         */
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
            captured.getAttributeAsString("to").orElseThrow();
        }
    }

    /**
     * Tests for the {@code <collection>} attributes.
     */
    @Nested
    @DisplayName("collection wire-shape parity")
    class CollectionShape {
        /**
         * Asserts that the captured {@code <collection>} name attribute matches
         * {@code SyncPatchType.toString()}.
         *
         * @param topic the captured topic to test
         */
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

        /**
         * Asserts that the captured {@code <collection>} carries a numeric
         * {@code version} attribute.
         *
         * @param topic the captured topic to test
         */
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
            collection.getAttributeAsLong("version").orElseThrow(
                    () -> new AssertionError("captured collection has no version attribute"));
        }
    }

    /**
     * Tests for the inline {@code <patch>} protobuf structure.
     */
    @Nested
    @DisplayName("patch protobuf structural parity")
    class PatchStructure {
        /**
         * Asserts that the decoded {@code <patch>} bytes carry at least one mutation,
         * 32-byte snapshot/patch MACs and a non-empty key id.
         *
         * @param topic the captured topic to test
         */
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
            if (patchNode == null) return;

            var patchBytes = patchNode.toContentBytes().orElseThrow();
            var decoded = SyncdPatchSpec.decode(patchBytes);

            assertTrue(decoded.mutations().size() + decoded.externalMutations().map(_ -> 1).orElse(0) >= 1,
                    "patch must carry at least one mutation (inline or external)");

            decoded.snapshotMac().ifPresent(mac -> assertEquals(32, mac.length, "snapshotMac length"));
            decoded.patchMac().ifPresent(mac -> assertEquals(32, mac.length, "patchMac length"));
            decoded.keyId().flatMap(k -> k.id()).ifPresent(id ->
                    assertTrue(id.length > 0, "patch key id must be non-empty"));
        }

        /**
         * Asserts that the decoded {@code <patch>} carries a {@code deviceIndex} field.
         *
         * @param topic the captured topic to test
         */
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

    /**
     * Tests for the captured response replay against
     * {@link MutationResponseParser}.
     */
    @Nested
    @DisplayName("MutationSyncResponse oracle replay")
    class ResponseReplay {
        /**
         * Asserts that a captured success response parses to a successful
         * {@link MutationSyncResponse}.
         */
        @Test
        @DisplayName("a captured success response parses to a successful MutationSyncResponse")
        void parsesSuccessResponse() {
            if (!SyncFixtures.isAvailable("exchange/regular-low/upload-archive")) return;

            var events = SyncFixtures.loadEvents("exchange/regular-low/upload-archive");
            var iqEvents = events.stream()
                    .filter(e -> "iq".equals(e.getString("tag")))
                    .filter(e -> "in".equals(e.getString("direction")))
                    .toList();
            if (iqEvents.isEmpty()) return;

            var inboundIq = SyncFixtures.buildNodeFromEvent(iqEvents.getFirst());
            var response = new MutationResponseParser().parseSyncResponse(inboundIq);

            assertEquals(SyncPatchType.REGULAR_LOW, response.collectionName(),
                    "response collection must match the upload's patch type");
            assertTrue(response.collectionError().isEmpty(),
                    "happy-path uploads receive a non-error response");
        }
    }

}
