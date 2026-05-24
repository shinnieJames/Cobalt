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
 * Asserts that Cobalt's {@link HistorySync} decoder reproduces the
 * value produced by WhatsApp Web's
 * {@code decodeProtobuf(WAWebProtobufsHistorySync.pb.HistorySyncSpec, ...)}
 * for the same inflated chunk bytes.
 *
 * @apiNote Parity test: every captured fixture under
 * {@code modules/lib/src/test/resources/fixtures/sync/history/<slug>/}
 * carries the inflated protobuf bytes, the parsed value oracle, and
 * the {@code HistorySyncNotification} that introduced the chunk.
 * Each {@code @Nested} block covers one {@link HistorySyncType}
 * variant. Tests gate themselves on
 * {@link SyncFixtures#isHistoryChunkAvailable(String)} via
 * {@link Assumptions#assumeTrue(boolean)} so the suite stays green
 * before fixtures are captured and immediately exercises new
 * fixtures the moment they land.
 *
 * <p>Three chunk-type slugs ({@code full}, {@code recent},
 * {@code on-demand}) are intentionally not captured at this
 * revision because:
 * <ul>
 *   <li>{@code full} is rejected by WA Web's
 *       {@code WAWebSendNonMessageDataRequest}: the
 *       {@code FULL_HISTORY_SYNC_ON_DEMAND} branch logs "full
 *       history sync on demand not supported in web" and drops the
 *       request.</li>
 *   <li>{@code recent} is server-gated and was not delivered to the
 *       freshly-paired session that drove the corpus.</li>
 *   <li>{@code on-demand} is dropped by the primary when the
 *       companion already has every eligible message for the target
 *       chat.</li>
 * </ul>
 *
 * <p>The {@code @Nested} blocks for those slugs are still present
 * so any future capture run that does land them activates the
 * tests without code changes.
 *
 * @implNote This implementation asserts only the structural
 * invariants that survive across both the {@link HistorySync.Full}
 * and {@link HistorySync.Light} variants
 * ({@code syncType}, {@code chunkOrder}, {@code progress}, list
 * sizes); per-element field assertions belong in dedicated decoders
 * for each list type and are out of scope here.
 */
@DisplayName("WebHistorySyncService -- live-oracle parity")
class WebHistorySyncServiceLiveOracleTest {

    /**
     * Asserts that the decoder produces the same key fields as the
     * captured WA Web oracle for one fixture slug.
     *
     * @apiNote Helper called from every per-syncType
     * {@code @Nested} block. Skips cleanly via
     * {@link Assumptions#assumeTrue(boolean)} when the fixture is
     * not on the classpath.
     *
     * @implNote This implementation always invokes
     * {@link HistorySync#ofFull(ProtobufInputStream)}; the
     * {@link HistorySync.Light} variant shares the same wire layout
     * for the asserted fields so the full decoder is sufficient
     * for parity. The {@code conversations} and
     * {@code statusV3Messages} list sizes are still asserted because
     * the full decoder populates them and they are key inputs to
     * Cobalt's chat-and-status fan-out in
     * {@link WebHistorySyncService}.
     *
     * @param typeSlug         the per-syncType fixture slug (for
     *                         example {@code "initial-bootstrap"})
     * @param expectedSyncType the {@link HistorySyncType} the slug
     *                         refers to
     */
    private static void assertChunkDecodesToOracle(String typeSlug, HistorySyncType expectedSyncType) {
        Assumptions.assumeTrue(SyncFixtures.isHistoryChunkAvailable(typeSlug),
                "history fixture not captured: " + typeSlug);
        var bytes = SyncFixtures.loadHistoryChunkBytes(typeSlug);
        var oracle = SyncFixtures.loadHistoryExpected(typeSlug);
        var decoded = HistorySync.ofFull(ProtobufInputStream.fromBytes(bytes));
        assertNotNull(decoded, "decoder returned null for " + typeSlug);
        assertEquals(expectedSyncType.index(), oracle.getIntValue("syncType"),
                "oracle syncType mismatch for " + typeSlug);
        assertEquals(expectedSyncType, decoded.syncType(),
                "decoded syncType mismatch for " + typeSlug);
        if (oracle.containsKey("chunkOrder")) {
            assertEquals(oracle.getIntValue("chunkOrder"),
                    decoded.chunkOrder().orElseThrow(() -> new AssertionError("chunkOrder missing in decoded")),
                    "chunkOrder mismatch for " + typeSlug);
        }
        if (oracle.containsKey("progress")) {
            assertEquals(oracle.getIntValue("progress"),
                    decoded.progress().orElseThrow(() -> new AssertionError("progress missing in decoded")),
                    "progress mismatch for " + typeSlug);
        }
        assertListSize(oracle, "pushnames", decoded.pushnames().size(), typeSlug);
        assertListSize(oracle, "phoneNumberToLidMappings",
                decoded.phoneNumberToLidMappings().size(), typeSlug);
        assertListSize(oracle, "pastParticipants",
                decoded.pastParticipants().size(), typeSlug);
        assertListSize(oracle, "recentStickers",
                decoded.recentStickers().size(), typeSlug);
        assertListSize(oracle, "accounts", decoded.accounts().size(), typeSlug);
        assertListSize(oracle, "conversations", decoded.chats().size(), typeSlug);
        assertListSize(oracle, "statusV3Messages",
                decoded.statusV3Messages().size(), typeSlug);
    }

    /**
     * Asserts that the oracle's named list and the decoded list
     * have the same size.
     *
     * @apiNote Helper used by
     * {@link #assertChunkDecodesToOracle(String, HistorySyncType)}.
     * A missing list on the oracle side is treated as empty, matching
     * the protobuf default for repeated fields.
     *
     * @param oracle      the oracle JSON document
     * @param fieldName   the protobuf field name to look up on the
     *                    oracle
     * @param decodedSize the size reported by the decoded
     *                    {@link HistorySync}
     * @param typeSlug    the slug used in the assertion message
     */
    private static void assertListSize(JSONObject oracle, String fieldName, int decodedSize, String typeSlug) {
        var array = oracle.getJSONArray(fieldName);
        var expected = array == null ? 0 : array.size();
        assertEquals(expected, decodedSize,
                fieldName + " count mismatch for " + typeSlug);
    }

    /**
     * Asserts that the captured notification's {@code syncType}
     * field matches the slug.
     *
     * @apiNote Helper used by every per-syncType
     * {@code @Nested} block. Guards that the fixture slug matches
     * the runtime payload that produced it.
     *
     * @param typeSlug         the per-syncType fixture slug
     * @param expectedSyncType the {@link HistorySyncType} the slug
     *                         refers to
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
     * Asserts that the captured notification carries either an
     * inline payload or a complete CDN handle pair.
     *
     * @apiNote Helper used by every per-syncType
     * {@code @Nested} block. Mirrors the WA Web invariant that any
     * non-{@code MESSAGE_ACCESS_STATUS} chunk must be downloadable
     * either inline or via the CDN, since
     * {@code WAWebHandleHistorySyncNotification.handleHistorySyncNotification}
     * gates its work on the presence of {@code downloadOptions}.
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
        var downloadOptions = notification.getJSONObject("downloadOptions");
        var hasCdn = downloadOptions != null
                && downloadOptions.containsKey("directPath")
                && downloadOptions.containsKey("mediaKey");
        assertTrue(hasInline || hasCdn,
                "notification must carry either inline payload or CDN download options for " + typeSlug);
    }

    /**
     * Parity block for the {@link HistorySyncType#INITIAL_BOOTSTRAP}
     * chunk variant.
     */
    @Nested
    @DisplayName("INITIAL_BOOTSTRAP")
    class InitialBootstrap {
        /**
         * The fixture slug for this nested block.
         */
        private static final String SLUG = "initial-bootstrap";

        /**
         * The decoder reproduces the WA Web oracle for the
         * freshly-paired bootstrap chunk.
         */
        @Test
        @DisplayName("decoder matches WA Web oracle")
        void decoderMatchesOracle() {
            assertChunkDecodesToOracle(SLUG, HistorySyncType.INITIAL_BOOTSTRAP);
        }

        /**
         * The notification syncType is INITIAL_BOOTSTRAP on both
         * the outer wrapper and the inner notification.
         */
        @Test
        @DisplayName("notification syncType is INITIAL_BOOTSTRAP")
        void notificationSyncType() {
            assertNotificationSyncType(SLUG, HistorySyncType.INITIAL_BOOTSTRAP);
        }

        /**
         * The notification carries an inline bootstrap payload or
         * CDN handle.
         */
        @Test
        @DisplayName("notification is deliverable (inline payload or CDN handle)")
        void notificationDeliverable() {
            assertNotificationDeliverable(SLUG);
        }
    }

    /**
     * Parity block for the {@link HistorySyncType#INITIAL_STATUS_V3}
     * chunk variant.
     */
    @Nested
    @DisplayName("INITIAL_STATUS_V3")
    class InitialStatusV3 {
        /**
         * The fixture slug for this nested block.
         */
        private static final String SLUG = "initial-status-v3";

        /**
         * The decoder reproduces the WA Web oracle.
         */
        @Test
        @DisplayName("decoder matches WA Web oracle")
        void decoderMatchesOracle() {
            assertChunkDecodesToOracle(SLUG, HistorySyncType.INITIAL_STATUS_V3);
        }

        /**
         * The notification syncType is INITIAL_STATUS_V3.
         */
        @Test
        @DisplayName("notification syncType is INITIAL_STATUS_V3")
        void notificationSyncType() {
            assertNotificationSyncType(SLUG, HistorySyncType.INITIAL_STATUS_V3);
        }

        /**
         * The notification is deliverable inline or via CDN.
         */
        @Test
        @DisplayName("notification is deliverable (inline payload or CDN handle)")
        void notificationDeliverable() {
            assertNotificationDeliverable(SLUG);
        }
    }

    /**
     * Parity block for the {@link HistorySyncType#FULL} chunk
     * variant; reserved for future captures (currently absent).
     */
    @Nested
    @DisplayName("FULL")
    class Full {
        /**
         * The fixture slug for this nested block.
         */
        private static final String SLUG = "full";

        /**
         * The decoder reproduces the WA Web oracle.
         */
        @Test
        @DisplayName("decoder matches WA Web oracle")
        void decoderMatchesOracle() {
            assertChunkDecodesToOracle(SLUG, HistorySyncType.FULL);
        }

        /**
         * The notification syncType is FULL.
         */
        @Test
        @DisplayName("notification syncType is FULL")
        void notificationSyncType() {
            assertNotificationSyncType(SLUG, HistorySyncType.FULL);
        }

        /**
         * The notification is deliverable inline or via CDN.
         */
        @Test
        @DisplayName("notification is deliverable (inline payload or CDN handle)")
        void notificationDeliverable() {
            assertNotificationDeliverable(SLUG);
        }
    }

    /**
     * Parity block for the {@link HistorySyncType#RECENT} chunk
     * variant; reserved for future captures (currently absent).
     */
    @Nested
    @DisplayName("RECENT")
    class Recent {
        /**
         * The fixture slug for this nested block.
         */
        private static final String SLUG = "recent";

        /**
         * The decoder reproduces the WA Web oracle.
         */
        @Test
        @DisplayName("decoder matches WA Web oracle")
        void decoderMatchesOracle() {
            assertChunkDecodesToOracle(SLUG, HistorySyncType.RECENT);
        }

        /**
         * The notification syncType is RECENT.
         */
        @Test
        @DisplayName("notification syncType is RECENT")
        void notificationSyncType() {
            assertNotificationSyncType(SLUG, HistorySyncType.RECENT);
        }

        /**
         * The notification is deliverable inline or via CDN.
         */
        @Test
        @DisplayName("notification is deliverable (inline payload or CDN handle)")
        void notificationDeliverable() {
            assertNotificationDeliverable(SLUG);
        }
    }

    /**
     * Parity block for the {@link HistorySyncType#PUSH_NAME} chunk
     * variant.
     */
    @Nested
    @DisplayName("PUSH_NAME")
    class PushName {
        /**
         * The fixture slug for this nested block.
         */
        private static final String SLUG = "push-name";

        /**
         * The decoder reproduces the WA Web oracle.
         */
        @Test
        @DisplayName("decoder matches WA Web oracle")
        void decoderMatchesOracle() {
            assertChunkDecodesToOracle(SLUG, HistorySyncType.PUSH_NAME);
        }

        /**
         * The notification syncType is PUSH_NAME.
         */
        @Test
        @DisplayName("notification syncType is PUSH_NAME")
        void notificationSyncType() {
            assertNotificationSyncType(SLUG, HistorySyncType.PUSH_NAME);
        }

        /**
         * The notification is deliverable inline or via CDN.
         */
        @Test
        @DisplayName("notification is deliverable (inline payload or CDN handle)")
        void notificationDeliverable() {
            assertNotificationDeliverable(SLUG);
        }
    }

    /**
     * Parity block for the
     * {@link HistorySyncType#NON_BLOCKING_DATA} chunk variant.
     */
    @Nested
    @DisplayName("NON_BLOCKING_DATA")
    class NonBlockingData {
        /**
         * The fixture slug for this nested block.
         */
        private static final String SLUG = "non-blocking-data";

        /**
         * The decoder reproduces the WA Web oracle.
         */
        @Test
        @DisplayName("decoder matches WA Web oracle")
        void decoderMatchesOracle() {
            assertChunkDecodesToOracle(SLUG, HistorySyncType.NON_BLOCKING_DATA);
        }

        /**
         * The notification syncType is NON_BLOCKING_DATA.
         */
        @Test
        @DisplayName("notification syncType is NON_BLOCKING_DATA")
        void notificationSyncType() {
            assertNotificationSyncType(SLUG, HistorySyncType.NON_BLOCKING_DATA);
        }

        /**
         * The notification is deliverable inline or via CDN.
         */
        @Test
        @DisplayName("notification is deliverable (inline payload or CDN handle)")
        void notificationDeliverable() {
            assertNotificationDeliverable(SLUG);
        }
    }

    /**
     * Parity block for the {@link HistorySyncType#ON_DEMAND} chunk
     * variant; reserved for future captures (currently absent).
     */
    @Nested
    @DisplayName("ON_DEMAND")
    class OnDemand {
        /**
         * The fixture slug for this nested block.
         */
        private static final String SLUG = "on-demand";

        /**
         * The decoder reproduces the WA Web oracle.
         */
        @Test
        @DisplayName("decoder matches WA Web oracle")
        void decoderMatchesOracle() {
            assertChunkDecodesToOracle(SLUG, HistorySyncType.ON_DEMAND);
        }

        /**
         * The notification syncType is ON_DEMAND.
         */
        @Test
        @DisplayName("notification syncType is ON_DEMAND")
        void notificationSyncType() {
            assertNotificationSyncType(SLUG, HistorySyncType.ON_DEMAND);
        }

        /**
         * The notification is deliverable inline or via CDN.
         */
        @Test
        @DisplayName("notification is deliverable (inline payload or CDN handle)")
        void notificationDeliverable() {
            assertNotificationDeliverable(SLUG);
        }
    }
}
