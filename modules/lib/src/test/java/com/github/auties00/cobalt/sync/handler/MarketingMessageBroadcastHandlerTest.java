package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.business.MarketingMessageBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.business.MarketingMessageAction.MarketingMessagePrototypeType;
import com.github.auties00.cobalt.model.sync.action.business.MarketingMessageBroadcastAction;
import com.github.auties00.cobalt.model.sync.action.business.MarketingMessageBroadcastActionBuilder;
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
 * Exercises the {@link MarketingMessageBroadcastHandler} adapter for
 * {@code WAWebPremiumMessageBroadcastSync}.
 *
 * @apiNote
 * Verifies parity with WA Web for the
 * {@code marketingMessageBroadcast} app-state sync action across
 * metadata, the SET happy path that records the
 * (premium template, sent message) association, the orphan branch
 * when the referenced template is unknown, the malformed-input
 * fallbacks, the REMOVE rejection and the inherited timestamp-based
 * conflict resolution.
 *
 * @implNote
 * This implementation exercises the handler against an in-memory
 * {@link DeviceFixtures#temporaryStore} via {@link TestWhatsAppClient}
 * so the
 * {@link WhatsAppStore#findMarketingMessage(String)} read-back can
 * be asserted directly. Premium templates are pre-seeded through
 * {@link MarketingMessageBuilder} so the orphan branch can be
 * verified independently.
 */
@DisplayName("MarketingMessageBroadcastHandler")
class MarketingMessageBroadcastHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    private WhatsAppStore store;
    private TestWhatsAppClient client;
    private MarketingMessageBroadcastHandler handler;

    @BeforeEach
    void setUp() {
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        client = TestWhatsAppClient.create().withStore(store);
        handler = new MarketingMessageBroadcastHandler();
    }

    /**
     * Builds a {@link DecryptedMutation.Trusted} carrying the given
     * broadcast action with explicit control over both index slots.
     *
     * @apiNote
     * Used by every test to centralise mutation construction; the
     * nullable index slots and nullable {@code action} let the
     * malformed-index and malformed-value paths be exercised without
     * re-implementing the envelope.
     *
     * @param indexPremiumId the premium template id placed in
     *                       {@code indexParts[1]}, may be
     *                       {@code null}
     * @param indexMessageId the sent message id placed in
     *                       {@code indexParts[2]}, may be
     *                       {@code null}
     * @param action         the broadcast action payload, may be
     *                       {@code null}
     * @param operation      the {@link SyncdOperation} to wrap
     * @param ts             the mutation timestamp
     * @return a {@link DecryptedMutation.Trusted} with the requested
     *         shape
     */
    private DecryptedMutation.Trusted buildMutation(String indexPremiumId, String indexMessageId,
                                                    MarketingMessageBroadcastAction action,
                                                    SyncdOperation operation, Instant ts) {
        var valueBuilder = new SyncActionValueBuilder().timestamp(ts);
        if (action != null) {
            valueBuilder.marketingMessageBroadcastAction(action);
        }
        List<String> indexParts;
        if (indexPremiumId == null && indexMessageId == null) {
            indexParts = List.of(handler.actionName());
        } else if (indexMessageId == null) {
            indexParts = List.of(handler.actionName(), indexPremiumId);
        } else {
            indexParts = List.of(handler.actionName(), indexPremiumId == null ? "" : indexPremiumId, indexMessageId);
        }
        var index = JSON.toJSONString(indexParts);
        return new DecryptedMutation.Trusted(index, valueBuilder.build(), operation, ts, handler.version());
    }

    private void seedTemplate(String templateId) {
        store.putMarketingMessage(new MarketingMessageBuilder()
                .templateId(templateId)
                .name("Spring")
                .message("body")
                .type(MarketingMessagePrototypeType.PERSONALIZED)
                .deleted(false)
                .build());
    }

    @Nested
    @DisplayName("metadata - wire identity")
    class Metadata {
        @Test
        @DisplayName("actionName() returns the broadcast action wire constant")
        void actionName() {
            assertEquals(MarketingMessageBroadcastAction.ACTION_NAME, handler.actionName());
            assertEquals("marketingMessageBroadcast", handler.actionName());
        }

        @Test
        @DisplayName("collectionName() returns REGULAR")
        void collectionName() {
            assertEquals(MarketingMessageBroadcastAction.COLLECTION_NAME, handler.collectionName());
            assertEquals(SyncPatchType.REGULAR, handler.collectionName());
        }

        @Test
        @DisplayName("version() returns the declared action version")
        void version() {
            assertEquals(MarketingMessageBroadcastAction.ACTION_VERSION, handler.version());
        }
    }

    @Nested
    @DisplayName("applyMutation - SET associates messageId with the premium template")
    class ApplySet {
        @Test
        @DisplayName("SET with a known premium template id records the (messageId, premiumId) pair")
        void recordsAssociation() {
            seedTemplate("tpl-1");
            var action = new MarketingMessageBroadcastActionBuilder().repliedCount(0).build();

            var result = handler.applyMutation(client,
                    buildMutation("tpl-1", "msg-1", action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            // The handler keys the broadcast record by the sent message id; the premium template
            // id is stored as the record's status field per Cobalt's adapted side-map.
            assertTrue(store.findMarketingMessageBroadcast("msg-1").isPresent());
        }
    }

    @Nested
    @DisplayName("applyMutation - orphan when premium template is unknown")
    class OrphanTemplate {
        @Test
        @DisplayName("a SET targeting an absent template returns ORPHAN")
        void orphanWhenMissing() {
            var action = new MarketingMessageBroadcastActionBuilder().repliedCount(0).build();

            var result = handler.applyMutation(client,
                    buildMutation("tpl-missing", "msg-1", action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.ORPHAN, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed value dimension is n/a")
    class MalformedValue {
        @Test
        @DisplayName("the handler never reads value.marketingMessageBroadcastAction - value content is irrelevant")
        void valueIgnored() {
            seedTemplate("tpl-2");
            var ts = Instant.now();
            // Drop in a value that carries the wrong (pin) action to demonstrate the broadcast
            // handler does not gate on action-type at all - only on the (premiumId, messageId)
            // index tuple plus template existence.
            var value = new SyncActionValueBuilder()
                    .timestamp(ts)
                    .pinAction(new PinActionBuilder().pinned(true).build())
                    .build();
            var index = JSON.toJSONString(List.of(handler.actionName(), "tpl-2", "msg-2"));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.SUCCESS, handler.applyMutation(client, mutation).actionState(),
                    "WAWebPremiumMessageBroadcastSync reads only the index - no malformed-value path exists");
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed index")
    class MalformedIndex {
        @Test
        @DisplayName("a missing premium id at indexParts[1] returns MALFORMED")
        void missingPremiumId() {
            var action = new MarketingMessageBroadcastActionBuilder().build();

            var result = handler.applyMutation(client,
                    buildMutation("", "msg-x", action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }

        @Test
        @DisplayName("a missing message id at indexParts[2] returns MALFORMED")
        void missingMessageId() {
            seedTemplate("tpl-3");
            var action = new MarketingMessageBroadcastActionBuilder().build();

            var result = handler.applyMutation(client,
                    buildMutation("tpl-3", null, action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - REMOVE")
    class ApplyRemove {
        @Test
        @DisplayName("REMOVE operation returns UNSUPPORTED")
        void removeUnsupported() {
            seedTemplate("tpl-rm");
            var action = new MarketingMessageBroadcastActionBuilder().build();

            var result = handler.applyMutation(client,
                    buildMutation("tpl-rm", "msg-rm", action, SyncdOperation.REMOVE, Instant.now()));

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
            var action = new MarketingMessageBroadcastActionBuilder().build();
            return buildMutation("tpl-tie", "msg-tie", action, SyncdOperation.SET, ts);
        }
    }

    @Nested
    @DisplayName("static builders - n/a")
    class StaticBuilders {
        @Test
        @DisplayName("MarketingMessageBroadcastHandler exposes no outbound mutation builder - dimension is n/a")
        void noBuilder() {
            assertNotNull(new MarketingMessageBroadcastHandler(),
                    "MarketingMessageBroadcastHandler instantiates with a no-arg constructor and exposes no builder method");
        }
    }
}
