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
import com.github.auties00.cobalt.model.sync.action.contact.LidContactAction;
import com.github.auties00.cobalt.model.sync.action.contact.LidContactActionBuilder;
import com.github.auties00.cobalt.model.sync.action.contact.PinActionBuilder;
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
 * Exercises the {@link LidContactHandler} adapter for
 * {@code WAWebLidContactSync}.
 *
 * @apiNote
 * Verifies parity with WA Web for the {@code lid_contact} app-state
 * sync action across metadata, the
 * {@link ABProp#USERNAME_CONTACT_SYNCD_SUPPORT_ENABLE} gating, the
 * SET upsert (full name, short name, username), the REMOVE branch
 * that clears address-book fields, the non-LID JID rejection, the
 * malformed-input fallbacks and the inherited timestamp-based
 * conflict resolution. {@link LidContactHandler} exposes no
 * outbound builder, so that dimension is absent here.
 *
 * @implNote
 * This implementation builds the handler with a stubbed
 * {@link TestABPropsService} so the gating prop can be flipped per
 * test, wires a real {@link UserStatusMuteHandler} so the
 * orphan-replay loop can be observed end-to-end, and exercises the
 * handler against an in-memory {@link DeviceFixtures#temporaryStore}
 * via {@link TestWhatsAppClient} so the
 * {@link WhatsAppStore#findContactByJid(Jid)}
 * read-back can be asserted directly.
 */
@DisplayName("LidContactHandler")
class LidContactHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final Jid CONTACT_LID = Jid.of("70000000000000@lid");
    private static final Jid CONTACT_PN = Jid.of("33330000@s.whatsapp.net");

    private WhatsAppStore store;
    private TestABPropsService props;
    private WhatsAppClient client;
    private LidContactHandler handler;

    @BeforeEach
    void setUp() {
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        // Default-on so the happy-path tests don't all have to repeat .set(...) before invoking the handler.
        props = TestABPropsService.builder()
                .with(ABProp.USERNAME_CONTACT_SYNCD_SUPPORT_ENABLE, true)
                .build();
        client = TestWhatsAppClient.create().withStore(store).withAbPropsService(props);
        handler = new LidContactHandler(props, new UserStatusMuteHandler());
    }

    /**
     * Builds a {@link DecryptedMutation.Trusted} carrying the given LID
     * contact action under the canonical
     * {@code ["lid_contact", lidJid]} index.
     *
     * @apiNote
     * Used by every test to centralise mutation construction. The
     * {@code action} parameter is nullable so the malformed-value
     * path can be exercised without re-implementing the envelope.
     *
     * @param lidJid the LID {@link Jid} that keys the mutation
     * @param action the action payload, may be {@code null}
     * @param op     the {@link SyncdOperation} to wrap
     * @param ts     the mutation timestamp
     * @return a {@link DecryptedMutation.Trusted} with the requested
     *         shape
     */
    private DecryptedMutation.Trusted build(Jid lidJid, LidContactAction action, SyncdOperation op, Instant ts) {
        var valueBuilder = new SyncActionValueBuilder().timestamp(ts);
        if (action != null) valueBuilder.lidContactAction(action);
        var index = JSON.toJSONString(List.of("lid_contact", lidJid.toString()));
        return new DecryptedMutation.Trusted(index, valueBuilder.build(), op, ts, handler.version());
    }

    @Nested
    @DisplayName("metadata - wire identity")
    class Metadata {
        @Test
        @DisplayName("actionName() returns the LidContactAction wire constant")
        void actionName() {
            assertEquals(LidContactAction.ACTION_NAME, handler.actionName());
            assertEquals("lid_contact", handler.actionName());
        }

        @Test
        @DisplayName("collectionName() returns the LidContactAction collection")
        void collectionName() {
            assertEquals(LidContactAction.COLLECTION_NAME, handler.collectionName());
            assertEquals(SyncPatchType.CRITICAL_UNBLOCK_LOW, handler.collectionName());
        }

        @Test
        @DisplayName("version() returns the declared LidContactAction version")
        void version() {
            assertEquals(LidContactAction.ACTION_VERSION, handler.version());
        }
    }

    @Nested
    @DisplayName("AB-prop gating - username_contact_syncd_support_enable")
    class AbPropGating {
        @Test
        @DisplayName("when the prop is off, every mutation returns UNSUPPORTED")
        void propOffReturnsUnsupported() {
            props.set(ABProp.USERNAME_CONTACT_SYNCD_SUPPORT_ENABLE, false);
            var action = new LidContactActionBuilder().fullName("Maria").build();

            var result = handler.applyMutation(client, build(CONTACT_LID, action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.UNSUPPORTED, result.actionState(),
                    "WA Web's WAWebLidContactSync.applyMutations returns Unsupported when the gating prop is off");
        }
    }

    @Nested
    @DisplayName("applyMutation - happy SET")
    class ApplySetHappy {
        @Test
        @DisplayName("creates a new contact when none exists with the LID JID")
        void createsContact() {
            var action = new LidContactActionBuilder()
                    .fullName("Maria Garcia")
                    .firstName("Maria")
                    .build();

            var result = handler.applyMutation(client, build(CONTACT_LID, action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            var contact = store.findContactByJid(CONTACT_LID).orElseThrow();
            assertEquals("Maria Garcia", contact.fullName().orElseThrow());
            assertEquals("Maria", contact.shortName().orElseThrow());
        }

        @Test
        @DisplayName("when firstName is absent, shortName is derived from the first word of fullName")
        void derivesShortName() {
            var action = new LidContactActionBuilder().fullName("Maria Garcia").build();

            handler.applyMutation(client, build(CONTACT_LID, action, SyncdOperation.SET, Instant.now()));

            assertEquals("Maria", store.findContactByJid(CONTACT_LID).orElseThrow().shortName().orElseThrow());
        }

        @Test
        @DisplayName("a non-empty username sets addedByUsername=true and strips a leading '@'")
        void usernamePresent() {
            var action = new LidContactActionBuilder().fullName("X").username("@maria").build();

            handler.applyMutation(client, build(CONTACT_LID, action, SyncdOperation.SET, Instant.now()));

            var contact = store.findContactByJid(CONTACT_LID).orElseThrow();
            assertEquals("maria", contact.username().orElseThrow());
            assertTrue(contact.isAddedByUsername());
        }

        @Test
        @DisplayName("an empty or absent username sets addedByUsername=false and clears username")
        void usernameAbsent() {
            var action = new LidContactActionBuilder().fullName("X").build();

            handler.applyMutation(client, build(CONTACT_LID, action, SyncdOperation.SET, Instant.now()));

            var contact = store.findContactByJid(CONTACT_LID).orElseThrow();
            assertTrue(contact.username().isEmpty());
            assertFalse(contact.isAddedByUsername());
        }
    }

    @Nested
    @DisplayName("applyMutation - orphan dimension is n/a")
    class OrphanDimension {
        @Test
        @DisplayName("the contact is upserted rather than orphaned when absent")
        void upsertNotOrphan() {
            var action = new LidContactActionBuilder().fullName("X").build();
            var result = handler.applyMutation(client, build(CONTACT_LID, action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertFalse(result.isOrphan());
            assertNull(result.modelId());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed value")
    class MalformedValue {
        @Test
        @DisplayName("a SET value carrying the wrong action returns MALFORMED")
        void wrongActionType() {
            var ts = Instant.now();
            var value = new SyncActionValueBuilder()
                    .timestamp(ts)
                    .pinAction(new PinActionBuilder().pinned(true).build())
                    .build();
            var index = JSON.toJSONString(List.of("lid_contact", CONTACT_LID.toString()));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }

        @Test
        @DisplayName("a SET value with no lidContactAction returns MALFORMED")
        void missingPayload() {
            var ts = Instant.now();
            var value = new SyncActionValueBuilder().timestamp(ts).build();
            var index = JSON.toJSONString(List.of("lid_contact", CONTACT_LID.toString()));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed index")
    class MalformedIndex {
        @Test
        @DisplayName("an empty lidJid slot returns MALFORMED")
        void emptyLidJid() {
            var action = new LidContactActionBuilder().fullName("X").build();
            var ts = Instant.now();
            var value = new SyncActionValueBuilder().timestamp(ts).lidContactAction(action).build();
            var index = JSON.toJSONString(List.of("lid_contact", ""));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }

        @Test
        @DisplayName("a non-LID JID in the index returns MALFORMED")
        void nonLidJid() {
            var action = new LidContactActionBuilder().fullName("X").build();
            var ts = Instant.now();
            var value = new SyncActionValueBuilder().timestamp(ts).lidContactAction(action).build();
            // CONTACT_PN is a regular phone-number JID, not a LID - the handler rejects it.
            var index = JSON.toJSONString(List.of("lid_contact", CONTACT_PN.toString()));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState(),
                    "WAWebLidContactSync.applyMutations rejects non-LID JIDs in the index");
        }
    }

    @Nested
    @DisplayName("applyMutation - REMOVE")
    class ApplyRemove {
        @Test
        @DisplayName("REMOVE on a username-added contact clears its address-book fields")
        void removeClearsUsernameContact() {
            var contact = store.addNewContact(CONTACT_LID);
            contact.setFullName("Maria");
            contact.setShortName("Maria");
            contact.setUsername("maria");
            contact.setAddedByUsername(true);

            var result = handler.applyMutation(client, build(CONTACT_LID, null, SyncdOperation.REMOVE, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(contact.fullName().isEmpty());
            assertTrue(contact.shortName().isEmpty());
            assertTrue(contact.username().isEmpty());
            assertFalse(contact.isAddedByUsername(), "the addedByUsername flag must be cleared");
        }

        @Test
        @DisplayName("REMOVE on a non-username-added contact leaves the record untouched")
        void removeIsNoopForNonUsernameContact() {
            var contact = store.addNewContact(CONTACT_LID);
            contact.setFullName("Maria");
            // addedByUsername stays false.

            var result = handler.applyMutation(client, build(CONTACT_LID, null, SyncdOperation.REMOVE, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertEquals("Maria", contact.fullName().orElseThrow(),
                    "non-username-added contacts are not affected by the REMOVE branch");
        }

        @Test
        @DisplayName("REMOVE on an absent contact still returns SUCCESS")
        void removeAbsent() {
            var result = handler.applyMutation(client, build(CONTACT_LID, null, SyncdOperation.REMOVE, Instant.now()));
            assertEquals(SyncActionState.SUCCESS, result.actionState());
        }
    }

    @Nested
    @DisplayName("resolveConflicts - default timestamp comparison")
    class ResolveConflicts {
        @Test
        @DisplayName("newer remote -> APPLY_REMOTE_DROP_LOCAL")
        void newerRemoteApplies() {
            var local = build(CONTACT_LID, action("A"), SyncdOperation.SET, Instant.ofEpochSecond(1_000));
            var remote = build(CONTACT_LID, action("B"), SyncdOperation.SET, Instant.ofEpochSecond(2_000));
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(local, remote).state());
        }

        @Test
        @DisplayName("equal timestamps -> APPLY_REMOTE_DROP_LOCAL (remote wins on tie)")
        void equalTiesGoToRemote() {
            var ts = Instant.ofEpochSecond(1_500);
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(
                            build(CONTACT_LID, action("A"), SyncdOperation.SET, ts),
                            build(CONTACT_LID, action("B"), SyncdOperation.SET, ts)).state());
        }

        @Test
        @DisplayName("older remote -> SKIP_REMOTE")
        void olderRemoteSkipped() {
            var local = build(CONTACT_LID, action("A"), SyncdOperation.SET, Instant.ofEpochSecond(2_000));
            var remote = build(CONTACT_LID, action("B"), SyncdOperation.SET, Instant.ofEpochSecond(1_000));
            assertEquals(ConflictResolutionState.SKIP_REMOTE,
                    handler.resolveConflicts(local, remote).state());
        }

        private LidContactAction action(String name) {
            return new LidContactActionBuilder().fullName(name).build();
        }
    }

}
