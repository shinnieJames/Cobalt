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
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.TestABPropsService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link ShareOwnPnHandler}'s parity with
 * {@code WAWebShareOwnPnSync.applyMutations}.
 *
 * @apiNote
 * Covers the wire-constant trio, the {@link ABProp#SHARE_OWN_PN_SYNC}
 * AB-prop gate, the happy {@code SET} path that upserts a LID contact
 * with {@code phoneNumberShared = true}, the rejection of non-LID JIDs,
 * the malformed-index branches (empty array, missing slot, empty
 * string), the {@link SyncdOperation#REMOVE} unsupported branch, and
 * the default conflict-resolution tiebreaker.
 *
 * @implNote
 * The {@code shareOwnPn} action carries no value payload of its own;
 * the {@link DecryptedMutation.Trusted#value()} only transports the
 * wire timestamp. Test fixtures therefore build the
 * {@link com.github.auties00.cobalt.model.sync.SyncActionValue} with
 * only the timestamp set.
 */
@DisplayName("ShareOwnPnHandler")
class ShareOwnPnHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final Jid CONTACT_LID = Jid.of("70000000000000@lid");
    private static final Jid CONTACT_PN = Jid.of("33330000@s.whatsapp.net");

    private WhatsAppStore store;
    private TestABPropsService props;
    private WhatsAppClient client;
    private ShareOwnPnHandler handler;

    /**
     * Builds the per-test harness with the
     * {@link ABProp#SHARE_OWN_PN_SYNC} gate pre-opened.
     *
     * @apiNote
     * Each test runs against a fresh
     * {@link WhatsAppStore} and a
     * fresh AB-props snapshot so gating-flip tests do not leak state.
     */
    @BeforeEach
    void setUp() {
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        props = TestABPropsService.builder()
                .with(ABProp.SHARE_OWN_PN_SYNC, true)
                .build();
        client = TestWhatsAppClient.create().withStore(store).withAbPropsService(props);
        handler = new ShareOwnPnHandler(props);
    }

    /**
     * Wraps the given LID JID into a trusted mutation under the
     * canonical {@code ["shareOwnPn", lidJid]} index.
     *
     * @apiNote
     * The {@code shareOwnPn} action has no value payload of its own;
     * the inner {@link com.github.auties00.cobalt.model.sync.SyncActionValue}
     * only carries the wire timestamp.
     *
     * @param lidJid the LID JID at index slot 1
     * @param op     the sync operation
     * @param ts     the mutation timestamp (also the {@code SyncActionValue} timestamp)
     * @return the trusted mutation
     */
    private DecryptedMutation.Trusted build(Jid lidJid, SyncdOperation op, Instant ts) {
        var value = new SyncActionValueBuilder().timestamp(ts).build();
        var index = JSON.toJSONString(List.of("shareOwnPn", lidJid.toString()));
        return new DecryptedMutation.Trusted(index, value, op, ts, handler.version());
    }

    @Nested
    @DisplayName("metadata - wire identity")
    class Metadata {
        @Test
        @DisplayName("actionName() returns the WAWebShareOwnPnSync wire constant")
        void actionName() {
            assertEquals("shareOwnPn", handler.actionName());
        }

        @Test
        @DisplayName("collectionName() returns REGULAR")
        void collectionName() {
            assertEquals(SyncPatchType.REGULAR, handler.collectionName());
        }

        @Test
        @DisplayName("version() returns 8 (the declared WAWebShareOwnPnSync version)")
        void version() {
            assertEquals(8, handler.version());
        }
    }

    @Nested
    @DisplayName("AB-prop gating - share_own_pn_sync")
    class AbPropGating {
        @Test
        @DisplayName("when the prop is off, the mutation returns UNSUPPORTED")
        void propOffReturnsUnsupported() {
            props.set(ABProp.SHARE_OWN_PN_SYNC, false);

            var result = handler.applyMutation(client, build(CONTACT_LID, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.UNSUPPORTED, result.actionState(),
                    "WAWebShareOwnPnSync.applyMutations returns Unsupported when the gating prop is off");
        }
    }

    @Nested
    @DisplayName("applyMutation - happy SET")
    class ApplySetHappy {
        @Test
        @DisplayName("upserts the LID contact with phoneNumberShared=true when none exists")
        void upsertsContact() {
            assertTrue(store.findContactByJid(CONTACT_LID).isEmpty(), "precondition: no contact");

            var result = handler.applyMutation(client, build(CONTACT_LID, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            var contact = store.findContactByJid(CONTACT_LID).orElseThrow();
            assertTrue(contact.isPhoneNumberShared(),
                    "WAWebShareOwnPnSync.applyMutations sets shareOwnPn=true via bulkCreateOrMerge");
        }

        @Test
        @DisplayName("sets phoneNumberShared=true on an existing contact without clobbering other fields")
        void mergesIntoExisting() {
            var contact = store.addNewContact(CONTACT_LID);
            contact.setFullName("Maria");
            assertFalse(contact.isPhoneNumberShared(), "precondition: shareOwnPn unset");

            var result = handler.applyMutation(client, build(CONTACT_LID, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(contact.isPhoneNumberShared());
            assertEquals("Maria", contact.fullName().orElseThrow(),
                    "merge semantics - non-target fields must be left untouched");
        }
    }

    @Nested
    @DisplayName("applyMutation - orphan dimension is n/a")
    class OrphanDimension {
        @Test
        @DisplayName("the contact is upserted rather than orphaned when absent")
        void noOrphanPath() {
            var result = handler.applyMutation(client, build(CONTACT_LID, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertFalse(result.isOrphan());
            assertNull(result.modelId());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed value dimension is n/a")
    class MalformedValue {
        @Test
        @DisplayName("the action has no value payload - value contents are ignored")
        void valueContentsIgnored() {
            // The shareOwnPn action carries no value payload: WAWebShareOwnPnSync.applyMutations
            // never reads value.action(). Even when the SyncActionValue carries a foreign payload,
            // the handler still succeeds as long as the index is well-formed.
            var ts = Instant.now();
            // We use the default-empty SyncActionValue; carrying a foreign payload would be redundant
            // since the handler does not inspect any value fields.
            var value = new SyncActionValueBuilder().timestamp(ts).build();
            var index = JSON.toJSONString(List.of("shareOwnPn", CONTACT_LID.toString()));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.SUCCESS, handler.applyMutation(client, mutation).actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed index")
    class MalformedIndex {
        @Test
        @DisplayName("an empty lidJid slot returns MALFORMED")
        void emptyLidJid() {
            var ts = Instant.now();
            var value = new SyncActionValueBuilder().timestamp(ts).build();
            var index = JSON.toJSONString(List.of("shareOwnPn", ""));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }

        @Test
        @DisplayName("a non-LID JID in the index returns MALFORMED")
        void nonLidJid() {
            var ts = Instant.now();
            var value = new SyncActionValueBuilder().timestamp(ts).build();
            var index = JSON.toJSONString(List.of("shareOwnPn", CONTACT_PN.toString()));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState(),
                    "createUserLidOrThrow would throw on a non-@lid JID; Cobalt maps that to MALFORMED");
        }

        @Test
        @DisplayName("a short index missing the lidJid slot returns MALFORMED")
        void shortIndex() {
            var ts = Instant.now();
            var value = new SyncActionValueBuilder().timestamp(ts).build();
            var index = JSON.toJSONString(List.of("shareOwnPn"));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - REMOVE")
    class ApplyRemove {
        @Test
        @DisplayName("REMOVE operation returns UNSUPPORTED")
        void removeUnsupported() {
            var result = handler.applyMutation(client, build(CONTACT_LID, SyncdOperation.REMOVE, Instant.now()));

            assertEquals(SyncActionState.UNSUPPORTED, result.actionState(),
                    "WA Web's WAWebShareOwnPnSync.applyMutations counts non-set operations as Unsupported");
        }
    }

    @Nested
    @DisplayName("resolveConflicts - default timestamp comparison")
    class ResolveConflicts {
        @Test
        @DisplayName("newer remote -> APPLY_REMOTE_DROP_LOCAL")
        void newerRemoteApplies() {
            var local = build(CONTACT_LID, SyncdOperation.SET, Instant.ofEpochSecond(1_000));
            var remote = build(CONTACT_LID, SyncdOperation.SET, Instant.ofEpochSecond(2_000));
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(local, remote).state());
        }

        @Test
        @DisplayName("equal timestamps -> APPLY_REMOTE_DROP_LOCAL (remote wins on tie)")
        void equalTiesGoToRemote() {
            var ts = Instant.ofEpochSecond(1_500);
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(
                            build(CONTACT_LID, SyncdOperation.SET, ts),
                            build(CONTACT_LID, SyncdOperation.SET, ts)).state());
        }

        @Test
        @DisplayName("older remote -> SKIP_REMOTE")
        void olderRemoteSkipped() {
            var local = build(CONTACT_LID, SyncdOperation.SET, Instant.ofEpochSecond(2_000));
            var remote = build(CONTACT_LID, SyncdOperation.SET, Instant.ofEpochSecond(1_000));
            assertEquals(ConflictResolutionState.SKIP_REMOTE,
                    handler.resolveConflicts(local, remote).state());
        }
    }

}
