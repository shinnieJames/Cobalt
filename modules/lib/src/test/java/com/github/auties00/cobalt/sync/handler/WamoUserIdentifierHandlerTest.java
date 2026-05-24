package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.PinActionBuilder;
import com.github.auties00.cobalt.model.sync.action.device.WamoUserIdentifierAction;
import com.github.auties00.cobalt.model.sync.action.device.WamoUserIdentifierActionBuilder;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link WamoUserIdentifierHandler}'s forward-looking adapter
 * for the {@code generated_wui} action.
 *
 * @apiNote
 * Covers the wire-constant trio, the happy {@code SET} branch that
 * persists the resolved identifier on
 * {@link WhatsAppStore#setNewsletterSubscriptionUserIdentifier(String)},
 * the malformed branches (missing action, empty string, blank string),
 * the {@link SyncdOperation#REMOVE} unsupported branch, and the
 * default conflict-resolution tiebreaker. WA Web ships no concrete
 * handler so the test surface enforces the Cobalt-inferred shape.
 *
 * @implNote
 * The fixture instantiates a handler per test against a temporary
 * store so the newsletter-subscription identifier starts unset.
 */
@DisplayName("WamoUserIdentifierHandler")
class WamoUserIdentifierHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    private WhatsAppStore store;
    private WhatsAppClient client;
    private WamoUserIdentifierHandler handler;

    /**
     * Builds the per-test harness.
     *
     * @apiNote
     * Each test runs against a fresh
     * {@link WhatsAppStore} so the
     * newsletter-subscription user identifier starts unset.
     */
    @BeforeEach
    void setUp() {
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        client = TestWhatsAppClient.create().withStore(store);
        handler = new WamoUserIdentifierHandler();
    }

    /**
     * Wraps the given action and operation into a trusted mutation
     * under the canonical {@code [actionName]} index.
     *
     * @apiNote
     * Pass {@code null} for {@code action} to exercise the
     * missing-action malformed branch.
     *
     * @param action the {@code generated_wui} action, or {@code null} to omit
     * @param op     the mutation operation
     * @param ts     the mutation timestamp
     * @return the trusted mutation
     */
    private DecryptedMutation.Trusted build(WamoUserIdentifierAction action, SyncdOperation op, Instant ts) {
        var valueBuilder = new SyncActionValueBuilder().timestamp(ts);
        if (action != null) {
            valueBuilder.wamoUserIdentifierAction(action);
        }
        var index = JSON.toJSONString(List.of(handler.actionName()));
        return new DecryptedMutation.Trusted(index, valueBuilder.build(), op, ts, handler.version());
    }

    @Nested
    @DisplayName("metadata")
    class Metadata {
        @Test
        @DisplayName("actionName() returns 'generated_wui'")
        void actionName() {
            assertEquals(WamoUserIdentifierAction.ACTION_NAME, handler.actionName());
            assertEquals("generated_wui", handler.actionName());
        }

        @Test
        @DisplayName("collectionName() returns CRITICAL_BLOCK")
        void collectionName() {
            assertEquals(SyncPatchType.CRITICAL_BLOCK, handler.collectionName());
        }

        @Test
        @DisplayName("version() returns the declared action version")
        void version() {
            assertEquals(WamoUserIdentifierAction.ACTION_VERSION, handler.version());
        }
    }

    @Nested
    @DisplayName("applyMutation - SET persists the WAMO identifier")
    class ApplySetHappy {
        @Test
        @DisplayName("SET with a non-blank identifier persists it on the store")
        void setsIdentifier() {
            assertTrue(store.newsletterSubscriptionUserIdentifier().isEmpty(),
                    "precondition: identifier is unset");
            var action = new WamoUserIdentifierActionBuilder()
                    .identifier("wamo-user-abc-123").build();

            var result = handler.applyMutation(client, build(action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertEquals("wamo-user-abc-123",
                    store.newsletterSubscriptionUserIdentifier().orElseThrow());
        }

        @Test
        @DisplayName("SET overwrites any prior identifier")
        void overwrites() {
            store.setNewsletterSubscriptionUserIdentifier("old-id");
            var action = new WamoUserIdentifierActionBuilder().identifier("new-id").build();

            var result = handler.applyMutation(client, build(action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertEquals("new-id", store.newsletterSubscriptionUserIdentifier().orElseThrow());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed value")
    class MalformedActionValue {
        @Test
        @DisplayName("a SET with the wrong action type returns MALFORMED")
        void wrongActionType() {
            var ts = Instant.now();
            var value = new SyncActionValueBuilder()
                    .timestamp(ts)
                    .pinAction(new PinActionBuilder().pinned(true).build())
                    .build();
            var mutation = new DecryptedMutation.Trusted(
                    "[\"generated_wui\"]", value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }

        @Test
        @DisplayName("a SET whose identifier is unset returns MALFORMED")
        void emptyIdentifier() {
            var action = new WamoUserIdentifierActionBuilder().build();

            var result = handler.applyMutation(client, build(action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }

        @Test
        @DisplayName("a SET whose identifier is blank returns MALFORMED")
        void blankIdentifier() {
            var action = new WamoUserIdentifierActionBuilder().identifier("   ").build();

            var result = handler.applyMutation(client, build(action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - REMOVE is UNSUPPORTED")
    class RemoveOperation {
        @Test
        @DisplayName("REMOVE returns UNSUPPORTED without touching the store")
        void removeIsUnsupported() {
            var action = new WamoUserIdentifierActionBuilder().identifier("wamo-id").build();

            var result = handler.applyMutation(client, build(action, SyncdOperation.REMOVE, Instant.now()));

            assertEquals(SyncActionState.UNSUPPORTED, result.actionState());
            assertTrue(store.newsletterSubscriptionUserIdentifier().isEmpty());
        }
    }

    @Nested
    @DisplayName("resolveConflicts - default timestamp-based")
    class ConflictResolution {
        @Test
        @DisplayName("newer remote -> APPLY_REMOTE_DROP_LOCAL")
        void newerRemoteApplies() {
            var action = new WamoUserIdentifierActionBuilder().identifier("foo").build();
            var local = build(action, SyncdOperation.SET, Instant.ofEpochSecond(1_000));
            var remote = build(action, SyncdOperation.SET, Instant.ofEpochSecond(2_000));

            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(local, remote).state());
        }

        @Test
        @DisplayName("older remote -> SKIP_REMOTE")
        void olderRemoteSkipped() {
            var action = new WamoUserIdentifierActionBuilder().identifier("foo").build();
            var local = build(action, SyncdOperation.SET, Instant.ofEpochSecond(2_000));
            var remote = build(action, SyncdOperation.SET, Instant.ofEpochSecond(1_000));

            assertEquals(ConflictResolutionState.SKIP_REMOTE,
                    handler.resolveConflicts(local, remote).state());
        }
    }

}
