package com.github.auties00.cobalt.sync;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.model.message.system.history.HistorySyncType;
import com.github.auties00.cobalt.model.sync.history.HistorySync;
import it.auties.protobuf.stream.ProtobufInputStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture-driven live-oracle test for {@link WebHistorySyncService} that asserts
 * Cobalt's history-sync decoder produces the same {@link HistorySync} value that
 * WhatsApp Web obtains from
 * {@code decodeProtobuf(WAWebProtobufsHistorySync.pb.HistorySyncSpec, ...)}
 * for the same inflated chunk bytes.
 *
 * <p>For every {@link HistorySyncType} variant, the suite carries one
 * {@code @Nested} block whose tests are gated on
 * {@link SyncFixtures#isHistoryChunkAvailable(String)}. Tests that lack a
 * captured fixture skip cleanly via {@link Assumptions#assumeTrue(boolean)};
 * the suite stays green and immediately exercises new fixtures the moment they
 * land in {@code modules/lib/src/test/resources/fixtures/sync/history/<slug>/}.
 *
 * <p>Fixtures are produced by the live in-page {@code __hs_capture} hook
 * installed via {@code web_live_debug_eval} on a freshly-paired WhatsApp Web
 * session and split into per-syncType triplets by
 * {@code tools/web/mcp-server/scripts/split-history-fixtures.mjs}. Each
 * captured chunk lives under
 * {@code fixtures/sync/history/<slug>/}{@code chunk.b64} (inflated protobuf
 * bytes), {@code expected.json} (parsed protobuf value oracle from WA Web's
 * own decoder), and {@code notification.json} (the
 * {@code HistorySyncNotification} that introduced the chunk).
 *
 * <p>Three chunk-type slugs are intentionally not captured at this revision
 * because:
 * <ul>
 *   <li>{@code full} — {@code WAWebSendNonMessageDataRequest} explicitly
 *       warns "full history sync on demand not supported in web" and drops
 *       the request, so a primary will never deliver a {@code FULL} chunk
 *       to a web companion.</li>
 *   <li>{@code recent} — the primary device emits {@code RECENT} chunks only
 *       under specific bootstrap-completion conditions controlled
 *       server-side; the freshly-paired session used to drive the corpus did
 *       not satisfy them.</li>
 *   <li>{@code on-demand} — the {@code HISTORY_SYNC_ON_DEMAND} peer-data
 *       request resolves with no chunk when the primary determines the
 *       companion already has all eligible messages for the target chat,
 *       which was the case for every chat available during capture.</li>
 * </ul>
 *
 * <p>The {@code @Nested} blocks for those slugs are still present so that any
 * future capture run that does land them activates the corresponding tests
 * without any test-code changes.
 */
@DisplayName("WebHistorySyncService — live-oracle parity")
class WebHistorySyncServiceLiveOracleTest {

    /**
     * Asserts that Cobalt's {@link HistorySync#ofFull(ProtobufInputStream)}
     * decodes the captured chunk bytes into a payload whose key fields match
     * the WhatsApp Web oracle.
     *
     * <p>The assertion intentionally covers the structural invariants that
     * survive across both the {@link HistorySync.Full} and
     * {@link HistorySync.Light} variants: {@code syncType},
     * {@code chunkOrder}, {@code progress}, plus the counts of the lists
     * common to both shapes ({@code pushnames},
     * {@code phoneNumberToLidMappings}, {@code pastParticipants},
     * {@code recentStickers}, {@code accounts}). The {@code conversations}
     * and {@code statusV3Messages} counts are asserted only for the full
     * variant where they are populated.
     *
     * @param typeSlug         the per-syncType fixture slug
     *                         (e.g. {@code "initial-bootstrap"})
     * @param expectedSyncType the {@link HistorySyncType} the slug refers to
     */
    private static void assertChunkDecodesToOracle(String typeSlug, HistorySyncType expectedSyncType) {
        Assumptions.assumeTrue(SyncFixtures.isHistoryChunkAvailable(typeSlug),
                "history fixture not captured: " + typeSlug);
        var bytes = SyncFixtures.loadHistoryChunkBytes(typeSlug);
        var oracle = SyncFixtures.loadHistoryExpected(typeSlug);
        var decoded = HistorySync.ofFull(ProtobufInputStream.fromBytes(bytes));
        assertNotNull(decoded, "decoder returned null for " + typeSlug);
        // WAWebProtobufsHistorySync.pb HistorySyncSpec field 1 (syncType): the
        // captured oracle value must match the slug-derived expectation, and
        // Cobalt's decoder must surface it through HistorySync.syncType().
        assertEquals(expectedSyncType.index(), oracle.getIntValue("syncType"),
                "oracle syncType mismatch for " + typeSlug);
        assertEquals(expectedSyncType, decoded.syncType(),
                "decoded syncType mismatch for " + typeSlug);
        // WAWebProtobufsHistorySync.pb HistorySyncSpec field 5 (chunkOrder):
        // optional; when present on the oracle, Cobalt must surface the same
        // value. The oracle stores Integer values as JSON numbers.
        if (oracle.containsKey("chunkOrder")) {
            assertEquals(oracle.getIntValue("chunkOrder"),
                    decoded.chunkOrder().orElseThrow(() -> new AssertionError("chunkOrder missing in decoded")),
                    "chunkOrder mismatch for " + typeSlug);
        }
        // WAWebProtobufsHistorySync.pb HistorySyncSpec field 6 (progress):
        // optional; same handling as chunkOrder.
        if (oracle.containsKey("progress")) {
            assertEquals(oracle.getIntValue("progress"),
                    decoded.progress().orElseThrow(() -> new AssertionError("progress missing in decoded")),
                    "progress mismatch for " + typeSlug);
        }
        // WAWebProtobufsHistorySync.pb HistorySyncSpec list-typed fields: the
        // oracle records every list as an array (possibly empty) under the
        // protobuf field name. Cobalt's accessor surface returns each list as
        // an unmodifiable List defaulting to List.of(), so asserting sizes
        // produces a stable byte-shape parity check that does not depend on
        // per-element field expansion.
        assertListSize(oracle, "pushnames", decoded.pushnames().size(), typeSlug);
        assertListSize(oracle, "phoneNumberToLidMappings",
                decoded.phoneNumberToLidMappings().size(), typeSlug);
        assertListSize(oracle, "pastParticipants",
                decoded.pastParticipants().size(), typeSlug);
        assertListSize(oracle, "recentStickers",
                decoded.recentStickers().size(), typeSlug);
        assertListSize(oracle, "accounts", decoded.accounts().size(), typeSlug);
        // WAWebHandleHistorySyncChunk.handleHistorySyncChunk reads
        // ae.conversations.length and ae.statusV3Messages.length out of the
        // decoded payload to populate the WAM metric counts and to fan out the
        // chunk into store collections. Cobalt's HistorySync.chats() abstracts
        // over the Full/Light split and returns the same list, so the sizes
        // must agree with WA Web's oracle counts.
        assertListSize(oracle, "conversations", decoded.chats().size(), typeSlug);
        assertListSize(oracle, "statusV3Messages",
                decoded.statusV3Messages().size(), typeSlug);
    }

    /**
     * Asserts that the oracle's named list and the decoded list have the
     * same size.
     *
     * <p>Missing entries on the oracle side are treated as "field absent →
     * empty list" because the protobuf decoder coerces unset repeated fields
     * to the empty list, matching the
     * {@code WAWebProtobufsHistorySync.pb HistorySyncSpec} default. The
     * decoded count must therefore also be zero in that case.
     * @param oracle      the oracle document
     * @param fieldName   the JSON field name (protobuf field name)
     * @param decodedSize the size reported by Cobalt's decoded
     *                    {@link HistorySync}
     * @param typeSlug    the slug used in assertion messages
     */
    private static void assertListSize(JSONObject oracle, String fieldName, int decodedSize, String typeSlug) {
        var array = oracle.getJSONArray(fieldName);
        var expected = array == null ? 0 : array.size();
        assertEquals(expected, decodedSize,
                fieldName + " count mismatch for " + typeSlug);
    }

    /**
     * Asserts that the captured notification document's {@code syncType}
     * field carries the expected {@link HistorySyncType} wire index.
     *
     * <p>The notification is the protobuf record that arrives in the E2EE
     * message wrapping a {@link HistorySync} chunk; WhatsApp Web routes the
     * chunk into the correct handler purely based on
     * {@code notification.syncType}, so this check guarantees the fixture
     * slug matches the runtime payload that produced it.
     * @param typeSlug         the per-syncType fixture slug
     * @param expectedSyncType the {@link HistorySyncType} the slug refers to
     */
    private static void assertNotificationSyncType(String typeSlug, HistorySyncType expectedSyncType) {
        Assumptions.assumeTrue(SyncFixtures.isHistoryChunkAvailable(typeSlug),
                "history fixture not captured: " + typeSlug);
        var notification = SyncFixtures.loadHistoryNotification(typeSlug);
        assertEquals(expectedSyncType.index(), notification.getIntValue("syncType"),
                "notification.syncType mismatch for " + typeSlug);
        var inner = notification.getJSONObject("notification");
        assertNotNull(inner, "notification.notification missing for " + typeSlug);
        assertEquals(expectedSyncType.index(), inner.getIntValue("syncType"),
                "notification.notification.syncType mismatch for " + typeSlug);
    }

    /**
     * Asserts that exactly one of {@code initialHistBootstrapInlinePayload}
     * (gzip-compressed inline chunk) or the
     * {@code directPath} / {@code mediaKey} download-options pair is present
     * on the captured notification, matching the WhatsApp Web invariant that
     * a non-{@code MESSAGE_ACCESS_STATUS} chunk must be downloadable either
     * inline or via the CDN.
     *
     * @param typeSlug the per-syncType fixture slug
     */
    private static void assertNotificationDeliverable(String typeSlug) {
        Assumptions.assumeTrue(SyncFixtures.isHistoryChunkAvailable(typeSlug),
                "history fixture not captured: " + typeSlug);
        var notification = SyncFixtures.loadHistoryNotification(typeSlug)
                .getJSONObject("notification");
        assertNotNull(notification, "notification.notification missing for " + typeSlug);
        var hasInline = notification.containsKey("initialHistBootstrapInlinePayload");
        // WAWebHandleHistorySyncNotification.handleHistorySyncNotification reads the CDN
        // handles out of notification.downloadOptions, not directly off the notification
        // (where it only sees the syncType and chunkOrder). The deliverability gate is
        // therefore "downloadOptions carries both directPath and mediaKey".
        var downloadOptions = notification.getJSONObject("downloadOptions");
        var hasCdn = downloadOptions != null
                && downloadOptions.containsKey("directPath")
                && downloadOptions.containsKey("mediaKey");
        assertTrue(hasInline || hasCdn,
                "notification must carry either inline payload or CDN download options for " + typeSlug);
    }

    @Nested
    @DisplayName("INITIAL_BOOTSTRAP")
    class InitialBootstrap {
        private static final String SLUG = "initial-bootstrap";

        /**
         * Decoder parity for the freshly-paired bootstrap chunk.
         */
        @Test
        @DisplayName("decoder matches WA Web oracle")
        void decoderMatchesOracle() {
            assertChunkDecodesToOracle(SLUG, HistorySyncType.INITIAL_BOOTSTRAP);
        }

        /**
         * Notification {@code syncType} parity.
         */
        @Test
        @DisplayName("notification syncType is INITIAL_BOOTSTRAP")
        void notificationSyncType() {
            assertNotificationSyncType(SLUG, HistorySyncType.INITIAL_BOOTSTRAP);
        }

        /**
         * Notification carries an inline bootstrap payload or CDN handle.
         */
        @Test
        @DisplayName("notification is deliverable (inline payload or CDN handle)")
        void notificationDeliverable() {
            assertNotificationDeliverable(SLUG);
        }
    }

    @Nested
    @DisplayName("INITIAL_STATUS_V3")
    class InitialStatusV3 {
        private static final String SLUG = "initial-status-v3";

        @Test
        @DisplayName("decoder matches WA Web oracle")
        void decoderMatchesOracle() {
            assertChunkDecodesToOracle(SLUG, HistorySyncType.INITIAL_STATUS_V3);
        }

        @Test
        @DisplayName("notification syncType is INITIAL_STATUS_V3")
        void notificationSyncType() {
            assertNotificationSyncType(SLUG, HistorySyncType.INITIAL_STATUS_V3);
        }

        @Test
        @DisplayName("notification is deliverable (inline payload or CDN handle)")
        void notificationDeliverable() {
            assertNotificationDeliverable(SLUG);
        }
    }

    @Nested
    @DisplayName("FULL")
    class Full {
        private static final String SLUG = "full";

        @Test
        @DisplayName("decoder matches WA Web oracle")
        void decoderMatchesOracle() {
            assertChunkDecodesToOracle(SLUG, HistorySyncType.FULL);
        }

        @Test
        @DisplayName("notification syncType is FULL")
        void notificationSyncType() {
            assertNotificationSyncType(SLUG, HistorySyncType.FULL);
        }

        @Test
        @DisplayName("notification is deliverable (inline payload or CDN handle)")
        void notificationDeliverable() {
            assertNotificationDeliverable(SLUG);
        }
    }

    @Nested
    @DisplayName("RECENT")
    class Recent {
        private static final String SLUG = "recent";

        @Test
        @DisplayName("decoder matches WA Web oracle")
        void decoderMatchesOracle() {
            assertChunkDecodesToOracle(SLUG, HistorySyncType.RECENT);
        }

        @Test
        @DisplayName("notification syncType is RECENT")
        void notificationSyncType() {
            assertNotificationSyncType(SLUG, HistorySyncType.RECENT);
        }

        @Test
        @DisplayName("notification is deliverable (inline payload or CDN handle)")
        void notificationDeliverable() {
            assertNotificationDeliverable(SLUG);
        }
    }

    @Nested
    @DisplayName("PUSH_NAME")
    class PushName {
        private static final String SLUG = "push-name";

        @Test
        @DisplayName("decoder matches WA Web oracle")
        void decoderMatchesOracle() {
            assertChunkDecodesToOracle(SLUG, HistorySyncType.PUSH_NAME);
        }

        @Test
        @DisplayName("notification syncType is PUSH_NAME")
        void notificationSyncType() {
            assertNotificationSyncType(SLUG, HistorySyncType.PUSH_NAME);
        }

        @Test
        @DisplayName("notification is deliverable (inline payload or CDN handle)")
        void notificationDeliverable() {
            assertNotificationDeliverable(SLUG);
        }
    }

    @Nested
    @DisplayName("NON_BLOCKING_DATA")
    class NonBlockingData {
        private static final String SLUG = "non-blocking-data";

        @Test
        @DisplayName("decoder matches WA Web oracle")
        void decoderMatchesOracle() {
            assertChunkDecodesToOracle(SLUG, HistorySyncType.NON_BLOCKING_DATA);
        }

        @Test
        @DisplayName("notification syncType is NON_BLOCKING_DATA")
        void notificationSyncType() {
            assertNotificationSyncType(SLUG, HistorySyncType.NON_BLOCKING_DATA);
        }

        @Test
        @DisplayName("notification is deliverable (inline payload or CDN handle)")
        void notificationDeliverable() {
            assertNotificationDeliverable(SLUG);
        }
    }

    @Nested
    @DisplayName("ON_DEMAND")
    class OnDemand {
        private static final String SLUG = "on-demand";

        @Test
        @DisplayName("decoder matches WA Web oracle")
        void decoderMatchesOracle() {
            assertChunkDecodesToOracle(SLUG, HistorySyncType.ON_DEMAND);
        }

        @Test
        @DisplayName("notification syncType is ON_DEMAND")
        void notificationSyncType() {
            assertNotificationSyncType(SLUG, HistorySyncType.ON_DEMAND);
        }

        @Test
        @DisplayName("notification is deliverable (inline payload or CDN handle)")
        void notificationDeliverable() {
            assertNotificationDeliverable(SLUG);
        }
    }
}
