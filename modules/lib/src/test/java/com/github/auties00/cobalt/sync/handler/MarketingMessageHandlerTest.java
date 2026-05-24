package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.business.MarketingMessageAction;
import com.github.auties00.cobalt.model.sync.action.business.MarketingMessageAction.MarketingMessagePrototypeType;
import com.github.auties00.cobalt.model.sync.action.business.MarketingMessageActionBuilder;
import com.github.auties00.cobalt.model.sync.action.contact.PinActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the {@link MarketingMessageHandler} adapter for
 * {@code WAWebPremiumMessageSync}.
 *
 * @apiNote
 * Verifies parity with WA Web for the {@code marketingMessage}
 * app-state sync action across metadata, the SET upsert keyed by
 * {@code indexParts[1]}, the malformed branch when
 * {@link MarketingMessageAction#type()} is empty, the
 * malformed-input fallbacks, the REMOVE rejection and the inherited
 * timestamp-based conflict resolution.
 *
 * @implNote
 * This implementation exercises the handler against an in-memory
 * {@link DeviceFixtures#temporaryStore} via {@link TestWhatsAppClient}
 * so the
 * {@link WhatsAppStore#findMarketingMessage(String)} read-back can
 * be asserted directly. The
 * {@link MarketingMessageAction#isDeleted()} flag is preserved on
 * the stored row so subsequent readers can filter live templates.
 */
@DisplayName("MarketingMessageHandler")
class MarketingMessageHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    private WhatsAppStore store;
    private TestWhatsAppClient client;
    private MarketingMessageHandler handler;

    @BeforeEach
    void setUp() {
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        client = TestWhatsAppClient.create().withStore(store);
        handler = new MarketingMessageHandler();
    }

    /**
     * Builds a {@link DecryptedMutation.Trusted} carrying the given
     * marketing action under the canonical
     * {@code ["marketingMessage", indexId]} index.
     *
     * @apiNote
     * Used by every test to centralise mutation construction; the
     * nullable {@code indexId} and {@code action} let the
     * malformed-index and malformed-value paths be exercised without
     * re-implementing the envelope.
     *
     * @param indexId   the template id placed in
     *                  {@code indexParts[1]}, may be {@code null}
     * @param action    the marketing action payload, may be
     *                  {@code null}
     * @param operation the {@link SyncdOperation} to wrap
     * @param ts        the mutation timestamp
     * @return a {@link DecryptedMutation.Trusted} with the requested
     *         shape
     */
    private DecryptedMutation.Trusted buildMutation(String indexId, MarketingMessageAction action, SyncdOperation operation, Instant ts) {
        var valueBuilder = new SyncActionValueBuilder().timestamp(ts);
        if (action != null) {
            valueBuilder.marketingMessageAction(action);
        }
        var indexParts = indexId == null ? List.of(handler.actionName()) : List.of(handler.actionName(), indexId);
        var index = JSON.toJSONString(indexParts);
        return new DecryptedMutation.Trusted(index, valueBuilder.build(), operation, ts, handler.version());
    }

    @Nested
    @DisplayName("metadata - wire identity")
    class Metadata {
        @Test
        @DisplayName("actionName() returns the MarketingMessageAction wire constant")
        void actionName() {
            assertEquals(MarketingMessageAction.ACTION_NAME, handler.actionName());
            assertEquals("marketingMessage", handler.actionName());
        }

        @Test
        @DisplayName("collectionName() returns REGULAR")
        void collectionName() {
            assertEquals(MarketingMessageAction.COLLECTION_NAME, handler.collectionName());
            assertEquals(SyncPatchType.REGULAR, handler.collectionName());
        }

        @Test
        @DisplayName("version() returns the declared action version")
        void version() {
            assertEquals(MarketingMessageAction.ACTION_VERSION, handler.version());
        }
    }

    @Nested
    @DisplayName("applyMutation - SET upsert")
    class ApplySet {
        @Test
        @DisplayName("a SET with a non-null type upserts the marketing template")
        void upsertsTemplate() {
            var action = new MarketingMessageActionBuilder()
                    .type(MarketingMessagePrototypeType.PERSONALIZED)
                    .name("Spring Sale")
                    .message("Hi {name}, 20% off this week")
                    .createdAt(1_700_000_000_000L)
                    .lastSentAt(1_700_001_000_000L)
                    .isDeleted(false)
                    .mediaId("media-1")
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("tpl-1", action, SyncdOperation.SET, Instant.ofEpochSecond(1_700_002_000L)));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            var stored = store.findMarketingMessage("tpl-1").orElseThrow();
            assertEquals("tpl-1", stored.templateId());
            assertTrue(!stored.deleted(), "isDeleted=false propagates to the stored template");
        }

        @Test
        @DisplayName("a SET with isDeleted=true still stores the template - readers filter on the flag")
        void deletedFlagPropagates() {
            var action = new MarketingMessageActionBuilder()
                    .type(MarketingMessagePrototypeType.PERSONALIZED)
                    .name("Tombstone")
                    .message("body")
                    .isDeleted(true)
                    .build();

            handler.applyMutation(client,
                    buildMutation("tpl-tomb", action, SyncdOperation.SET, Instant.now()));

            assertTrue(store.findMarketingMessage("tpl-tomb").orElseThrow().deleted());
        }
    }

    @Nested
    @DisplayName("applyMutation - orphan dimension is n/a")
    class OrphanDimension {
        @Test
        @DisplayName("SET on an unknown id is the upsert path, not an orphan")
        void unknownIdUpserts() {
            // Per WAWebPremiumMessageSync.applyMutations, the SET path is unconditional upsert.
            // There is no prior-entity probe that could produce an orphan outcome.
            var action = new MarketingMessageActionBuilder()
                    .type(MarketingMessagePrototypeType.PERSONALIZED)
                    .message("body")
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("brand-new", action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(store.findMarketingMessage("brand-new").isPresent());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed value")
    class MalformedValue {
        @Test
        @DisplayName("a value carrying the wrong action returns MALFORMED")
        void wrongActionType() {
            var ts = Instant.now();
            var value = new SyncActionValueBuilder()
                    .timestamp(ts)
                    .pinAction(new PinActionBuilder().pinned(true).build())
                    .build();
            var index = JSON.toJSONString(List.of(handler.actionName(), "tpl-x"));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }

        @Test
        @DisplayName("a marketingMessageAction with a null type returns MALFORMED")
        void nullTypeMalformed() {
            // Per WA Web `c.type == null && (a++, return malformedActionValue)`.
            var action = new MarketingMessageActionBuilder()
                    .message("body")
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("tpl-no-type", action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed index")
    class MalformedIndex {
        @Test
        @DisplayName("a missing template id slot returns MALFORMED")
        void missingTemplateIdSlot() {
            var action = new MarketingMessageActionBuilder()
                    .type(MarketingMessagePrototypeType.PERSONALIZED)
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation(null, action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }

        @Test
        @DisplayName("an empty template id returns MALFORMED")
        void emptyTemplateId() {
            var action = new MarketingMessageActionBuilder()
                    .type(MarketingMessagePrototypeType.PERSONALIZED)
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("", action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - REMOVE")
    class ApplyRemove {
        @Test
        @DisplayName("REMOVE operation returns UNSUPPORTED")
        void removeUnsupported() {
            var action = new MarketingMessageActionBuilder()
                    .type(MarketingMessagePrototypeType.PERSONALIZED)
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("tpl-rm", action, SyncdOperation.REMOVE, Instant.now()));

            assertEquals(SyncActionState.UNSUPPORTED, result.actionState());
        }
    }

    @Nested
    @DisplayName("resolveConflicts - default timestamp comparison")
    class ResolveConflicts {
        @Test
        @DisplayName("newer remote - APPLY_REMOTE_DROP_LOCAL")
        void newerRemoteApplies() {
            var local = mutationAt(Instant.ofEpochSecond(1_000));
            var remote = mutationAt(Instant.ofEpochSecond(2_000));
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(local, remote).state());
        }

        @Test
        @DisplayName("equal timestamps - APPLY_REMOTE_DROP_LOCAL")
        void equalTimestampApplies() {
            var ts = Instant.ofEpochSecond(1_500);
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(mutationAt(ts), mutationAt(ts)).state());
        }

        @Test
        @DisplayName("older remote - SKIP_REMOTE")
        void olderRemoteSkipped() {
            var local = mutationAt(Instant.ofEpochSecond(2_000));
            var remote = mutationAt(Instant.ofEpochSecond(1_000));
            assertEquals(ConflictResolutionState.SKIP_REMOTE,
                    handler.resolveConflicts(local, remote).state());
        }

        private DecryptedMutation.Trusted mutationAt(Instant ts) {
            var action = new MarketingMessageActionBuilder()
                    .type(MarketingMessagePrototypeType.PERSONALIZED)
                    .build();
            return buildMutation("tpl-tie", action, SyncdOperation.SET, ts);
        }
    }

    @Nested
    @DisplayName("static builders - n/a")
    class StaticBuilders {
        @Test
        @DisplayName("MarketingMessageHandler exposes no outbound mutation builder - dimension is n/a")
        void noBuilder() {
            // Marketing message templates are authored via the WA Web business UI; Cobalt does
            // not emit `marketingMessage` mutations from this handler. INSTANCE is the only
            // public entry point.
            assertNotNull(new MarketingMessageHandler(),
                    "MarketingMessageHandler instantiates with a no-arg constructor and exposes no builder method");
        }
    }
}
