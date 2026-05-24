package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.business.BusinessBroadcastListBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastAssociationAction;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastAssociationActionBuilder;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link BusinessBroadcastAssociationHandler}.
 *
 * <p>This handler is Cobalt-inferred: WA Web defines the
 * {@code SyncActionValue.BusinessBroadcastAssociationAction} protobuf for
 * {@code "broadcast_jid"} mutations, but ships no corresponding sync handler
 * module. The Cobalt implementation associates (SET, {@code deleted=false}) or
 * disassociates (SET, {@code deleted=true}) a recipient JID inside a parent
 * {@code BusinessBroadcastList} stored on {@link WhatsAppStore}. The recipient
 * is keyed by {@code indexParts[2]} and the list id by {@code indexParts[1]}.
 */
@DisplayName("BusinessBroadcastAssociationHandler")
class BusinessBroadcastAssociationHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final Jid CONTACT_LID = Jid.of("70000000000000@lid");
    private static final Jid CONTACT_PN = Jid.of("33330000@s.whatsapp.net");
    private static final String LIST_ID = "list-abc";

    private WhatsAppStore store;
    private WhatsAppClient client;
    private BusinessBroadcastAssociationHandler handler;

    @BeforeEach
    void setUp() {
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        client = TestWhatsAppClient.create().withStore(store);
        handler = new BusinessBroadcastAssociationHandler();
    }

    /**
     * Builds a trusted mutation whose value carries the given association action.
     *
     * @param listId       the broadcast list id placed at {@code indexParts[1]},
     *                     may be {@code null} to produce a short index
     * @param recipient    the recipient JID placed at {@code indexParts[2]},
     *                     may be {@code null} to produce a short index
     * @param action       the action payload, may be {@code null}
     * @param operation    the sync operation
     * @param ts           the mutation timestamp
     * @return the trusted mutation
     */
    private DecryptedMutation.Trusted build(String listId, Jid recipient,
                                            BusinessBroadcastAssociationAction action,
                                            SyncdOperation operation, Instant ts) {
        var valueBuilder = new SyncActionValueBuilder().timestamp(ts);
        if (action != null) {
            valueBuilder.businessBroadcastAssociationAction(action);
        }
        List<String> parts;
        if (listId == null) {
            parts = List.of(handler.actionName());
        } else if (recipient == null) {
            parts = List.of(handler.actionName(), listId);
        } else {
            parts = List.of(handler.actionName(), listId, recipient.toString());
        }
        var index = JSON.toJSONString(parts);
        return new DecryptedMutation.Trusted(index, valueBuilder.build(), operation, ts, handler.version());
    }

    private void seedList(String id) {
        var list = new BusinessBroadcastListBuilder().id(id).build();
        store.putBusinessBroadcastList(list);
    }

    @Nested
    @DisplayName("metadata")
    class Metadata {
        @Test
        @DisplayName("actionName() returns the BusinessBroadcastAssociationAction wire constant")
        void actionName() {
            assertEquals(BusinessBroadcastAssociationAction.ACTION_NAME, handler.actionName());
            assertEquals("broadcast_jid", handler.actionName());
        }

        @Test
        @DisplayName("collectionName() returns REGULAR")
        void collectionName() {
            assertEquals(BusinessBroadcastAssociationAction.COLLECTION_NAME, handler.collectionName());
            assertEquals(SyncPatchType.REGULAR, handler.collectionName());
        }

        @Test
        @DisplayName("version() returns the declared action version")
        void version() {
            assertEquals(BusinessBroadcastAssociationAction.ACTION_VERSION, handler.version());
        }
    }

    @Nested
    @DisplayName("applyMutation - SET adds a participant")
    class ApplySetHappy {
        @Test
        @DisplayName("SET with a LID recipient appends a participant carrying lidJid")
        void setAppendsLidParticipant() {
            seedList(LIST_ID);
            var action = new BusinessBroadcastAssociationActionBuilder().deleted(false).build();

            var result = handler.applyMutation(client,
                    build(LIST_ID, CONTACT_LID, action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            var stored = store.findBusinessBroadcastList(LIST_ID).orElseThrow();
            assertEquals(1, stored.participants().size(),
                    "the handler must append a single participant for the new LID recipient");
            assertEquals(CONTACT_LID, stored.participants().get(0).lidJid());
        }

        @Test
        @DisplayName("SET with a phone-number recipient appends a participant carrying pnJid")
        void setAppendsPnParticipant() {
            seedList(LIST_ID);
            var action = new BusinessBroadcastAssociationActionBuilder().deleted(false).build();

            var result = handler.applyMutation(client,
                    build(LIST_ID, CONTACT_PN, action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            var stored = store.findBusinessBroadcastList(LIST_ID).orElseThrow();
            assertEquals(1, stored.participants().size());
            // The participant must carry the recipient phone JID as pnJid; the lidJid falls back
            // to the recipient itself when the store has no LID mapping for the phone number.
            assertEquals(CONTACT_PN, stored.participants().get(0).pnJid().orElseThrow());
        }

        @Test
        @DisplayName("SET with deleted=true strips any matching participant and is a SUCCESS")
        void setDeletedRemovesParticipant() {
            // First, add the participant.
            seedList(LIST_ID);
            handler.applyMutation(client, build(LIST_ID, CONTACT_LID,
                    new BusinessBroadcastAssociationActionBuilder().deleted(false).build(),
                    SyncdOperation.SET, Instant.now()));
            assertFalse(store.findBusinessBroadcastList(LIST_ID).orElseThrow().participants().isEmpty());

            // Now strip it with deleted=true.
            var result = handler.applyMutation(client, build(LIST_ID, CONTACT_LID,
                    new BusinessBroadcastAssociationActionBuilder().deleted(true).build(),
                    SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(store.findBusinessBroadcastList(LIST_ID).orElseThrow().participants().isEmpty());
        }
    }

    @Nested
    @DisplayName("applyMutation - orphan dimension")
    class OrphanDimension {
        @Test
        @DisplayName("SET targeting a missing list yields ORPHAN with the list id")
        void missingListIsOrphan() {
            var action = new BusinessBroadcastAssociationActionBuilder().deleted(false).build();

            var result = handler.applyMutation(client,
                    build("missing-list", CONTACT_LID, action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.ORPHAN, result.actionState());
            assertTrue(result.isOrphan());
            assertEquals("missing-list", result.modelId());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed value")
    class MalformedActionValue {
        @Test
        @DisplayName("a SET whose value carries the wrong action returns MALFORMED")
        void wrongActionType() {
            seedList(LIST_ID);
            var ts = Instant.now();
            var value = new SyncActionValueBuilder()
                    .timestamp(ts)
                    .pinAction(new PinActionBuilder().pinned(true).build())
                    .build();
            var index = JSON.toJSONString(List.of(handler.actionName(), LIST_ID, CONTACT_LID.toString()));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed index")
    class MalformedActionIndex {
        @Test
        @DisplayName("a short index without the recipient slot returns MALFORMED")
        void missingRecipientSlot() {
            seedList(LIST_ID);
            var action = new BusinessBroadcastAssociationActionBuilder().deleted(false).build();
            var result = handler.applyMutation(client,
                    build(LIST_ID, null, action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }

        @Test
        @DisplayName("a blank list id at indexParts[1] returns MALFORMED")
        void blankListId() {
            var action = new BusinessBroadcastAssociationActionBuilder().deleted(false).build();
            var result = handler.applyMutation(client,
                    build("", CONTACT_LID, action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }

        @Test
        @DisplayName("a blank recipient JID at indexParts[2] returns MALFORMED")
        void blankRecipient() {
            seedList(LIST_ID);
            var ts = Instant.now();
            var action = new BusinessBroadcastAssociationActionBuilder().deleted(false).build();
            var value = new SyncActionValueBuilder().timestamp(ts).businessBroadcastAssociationAction(action).build();
            var index = JSON.toJSONString(List.of(handler.actionName(), LIST_ID, ""));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - REMOVE is UNSUPPORTED")
    class RemoveOperation {
        @Test
        @DisplayName("REMOVE returns UNSUPPORTED; the deleted flag inside the SET payload is the canonical delete signal")
        void removeIsUnsupported() {
            seedList(LIST_ID);
            var action = new BusinessBroadcastAssociationActionBuilder().deleted(true).build();

            var result = handler.applyMutation(client,
                    build(LIST_ID, CONTACT_LID, action, SyncdOperation.REMOVE, Instant.now()));

            assertEquals(SyncActionState.UNSUPPORTED, result.actionState());
        }
    }

    @Nested
    @DisplayName("resolveConflicts - default timestamp-based")
    class ConflictResolution {
        @Test
        @DisplayName("newer remote wins -> APPLY_REMOTE_DROP_LOCAL")
        void newerRemoteApplies() {
            var action = new BusinessBroadcastAssociationActionBuilder().deleted(false).build();
            var local = build(LIST_ID, CONTACT_LID, action, SyncdOperation.SET, Instant.ofEpochSecond(1_000));
            var remote = build(LIST_ID, CONTACT_LID, action, SyncdOperation.SET, Instant.ofEpochSecond(2_000));

            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(local, remote).state());
        }

        @Test
        @DisplayName("older remote loses -> SKIP_REMOTE")
        void olderRemoteSkipped() {
            var action = new BusinessBroadcastAssociationActionBuilder().deleted(false).build();
            var local = build(LIST_ID, CONTACT_LID, action, SyncdOperation.SET, Instant.ofEpochSecond(2_000));
            var remote = build(LIST_ID, CONTACT_LID, action, SyncdOperation.SET, Instant.ofEpochSecond(1_000));

            assertEquals(ConflictResolutionState.SKIP_REMOTE,
                    handler.resolveConflicts(local, remote).state());
        }
    }

}
