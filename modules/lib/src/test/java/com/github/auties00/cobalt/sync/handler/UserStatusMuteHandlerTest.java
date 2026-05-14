package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.chat.group.GroupMetadata;
import com.github.auties00.cobalt.model.chat.group.GroupMetadataBuilder;
import com.github.auties00.cobalt.model.contact.ContactBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.UserStatusMuteAction;
import com.github.auties00.cobalt.model.sync.action.contact.UserStatusMuteActionBuilder;
import com.github.auties00.cobalt.model.sync.action.media.FavoritesAction;
import com.github.auties00.cobalt.model.sync.action.media.FavoritesActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.client.WhatsAppClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link UserStatusMuteHandler} â€” Cobalt's adapter for
 * {@code WAWebUserStatusMuteSync}.
 *
 * <p>The handler applies {@code SET} mutations carrying the per-contact (or
 * per-group) status mute flag. The tests pin down metadata, every branch of
 * {@code applyMutation}, the static {@code getMutationForStatusMute} builder,
 * and the default timestamp-based conflict resolution.
 */
@DisplayName("UserStatusMuteHandler")
class UserStatusMuteHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final Jid CONTACT = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid GROUP = Jid.of("12025550100-1609459200@g.us");

    private WhatsAppClient client;

    @BeforeEach
    void setUp() {
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        client = TestWhatsAppClient.create().withStore(store);
    }

    private static DecryptedMutation.Trusted mutationFor(String widString, Boolean muted, SyncdOperation op, Instant ts) {
        var action = new UserStatusMuteActionBuilder().muted(muted).build();
        var value = new SyncActionValueBuilder()
                .timestamp(ts)
                .userStatusMuteAction(action)
                .build();
        var index = "[\"userStatusMute\",\"" + widString + "\"]";
        return new DecryptedMutation.Trusted(index, value, op, ts, 7);
    }

    @Nested
    @DisplayName("metadata")
    class Metadata {
        @Test
        @DisplayName("actionName() returns the WA wire constant 'userStatusMute'")
        void actionName() {
            assertEquals(UserStatusMuteAction.ACTION_NAME, new UserStatusMuteHandler().actionName());
            assertEquals("userStatusMute", new UserStatusMuteHandler().actionName());
        }

        @Test
        @DisplayName("collectionName() resolves to SyncPatchType.REGULAR_HIGH")
        void collectionName() {
            assertEquals(SyncPatchType.REGULAR_HIGH, new UserStatusMuteHandler().collectionName());
        }

        @Test
        @DisplayName("version() returns the declared action version")
        void version() {
            assertEquals(UserStatusMuteAction.ACTION_VERSION, new UserStatusMuteHandler().version());
        }
    }

    @Nested
    @DisplayName("applyMutation: SET on a known contact")
    class SetContact {
        @Test
        @DisplayName("flips the contact's statusMuted flag and returns SUCCESS")
        void appliesToContact() {
            client.store().addContact(new ContactBuilder().jid(CONTACT).build());

            var result = new UserStatusMuteHandler().applyMutation(
                    client, mutationFor(CONTACT.toString(), Boolean.TRUE, SyncdOperation.SET, Instant.ofEpochSecond(1700000000L)));

            assertEquals(SyncActionState.SUCCESS, result.actionState(),
                    "muting a known contact must succeed");
            assertTrue(client.store().findContactByJid(CONTACT).orElseThrow().statusMuted(),
                    "the contact's statusMuted field must reflect the mutation");
        }

        @Test
        @DisplayName("a second mutation can flip the flag back to false")
        void canUnmute() {
            var contact = new ContactBuilder().jid(CONTACT).statusMuted(true).build();
            client.store().addContact(contact);

            var result = new UserStatusMuteHandler().applyMutation(
                    client, mutationFor(CONTACT.toString(), Boolean.FALSE, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertFalse(client.store().findContactByJid(CONTACT).orElseThrow().statusMuted());
        }
    }

    @Nested
    @DisplayName("applyMutation: SET on a known group")
    class SetGroup {
        @Test
        @DisplayName("writes statusMuted to the GroupMetadata row")
        void appliesToGroup() {
            var metadata = new GroupMetadataBuilder()
                    .jid(GROUP)
                    .subject("Test Group")
                    .build();
            client.store().addChatMetadata(metadata);

            var result = new UserStatusMuteHandler().applyMutation(
                    client, mutationFor(GROUP.toString(), Boolean.TRUE, SyncdOperation.SET, Instant.ofEpochSecond(1700000000L)));

            assertEquals(SyncActionState.SUCCESS, result.actionState(),
                    "muting a known group must succeed");
            var stored = client.store().findChatMetadata(GROUP).orElseThrow();
            assertTrue(stored instanceof GroupMetadata, "group metadata row must be present");
            assertTrue(((GroupMetadata) stored).statusMuted(),
                    "the group's statusMuted field must reflect the mutation");
        }
    }

    @Nested
    @DisplayName("applyMutation: orphan paths")
    class Orphan {
        @Test
        @DisplayName("unknown contact wid returns ORPHAN with the wid as the model id")
        void unknownContact() {
            var result = new UserStatusMuteHandler().applyMutation(
                    client, mutationFor(CONTACT.toString(), Boolean.TRUE, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.ORPHAN, result.actionState());
            assertEquals(CONTACT.toString(), result.modelId());
            assertEquals("UserStatusMute", result.modelType());
        }

        @Test
        @DisplayName("unknown group wid returns ORPHAN with the wid as the model id")
        void unknownGroup() {
            var result = new UserStatusMuteHandler().applyMutation(
                    client, mutationFor(GROUP.toString(), Boolean.TRUE, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.ORPHAN, result.actionState());
            assertEquals(GROUP.toString(), result.modelId());
            assertEquals("UserStatusMute", result.modelType());
        }
    }

    @Nested
    @DisplayName("applyMutation: malformed paths")
    class Malformed {
        @Test
        @DisplayName("empty wid string yields MALFORMED (action index)")
        void emptyWid() {
            var action = new UserStatusMuteActionBuilder().muted(Boolean.TRUE).build();
            var value = new SyncActionValueBuilder().timestamp(Instant.now()).userStatusMuteAction(action).build();
            var mutation = new DecryptedMutation.Trusted(
                    "[\"userStatusMute\",\"\"]", value, SyncdOperation.SET, Instant.now(), 7);

            var result = new UserStatusMuteHandler().applyMutation(client, mutation);

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }

        @Test
        @DisplayName("missing wid in index yields MALFORMED (action index)")
        void missingWid() {
            var action = new UserStatusMuteActionBuilder().muted(Boolean.TRUE).build();
            var value = new SyncActionValueBuilder().timestamp(Instant.now()).userStatusMuteAction(action).build();
            var mutation = new DecryptedMutation.Trusted(
                    "[\"userStatusMute\"]", value, SyncdOperation.SET, Instant.now(), 7);

            var result = new UserStatusMuteHandler().applyMutation(client, mutation);

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }

        @Test
        @DisplayName("value carrying a different action yields MALFORMED (action value)")
        void wrongActionInValue() {
            var wrongValue = new SyncActionValueBuilder()
                    .timestamp(Instant.now())
                    .favoritesAction(new FavoritesActionBuilder().favorites(java.util.List.of()).build())
                    .build();
            var mutation = new DecryptedMutation.Trusted(
                    "[\"userStatusMute\",\"" + CONTACT + "\"]",
                    wrongValue, SyncdOperation.SET, Instant.now(), 7);

            var result = new UserStatusMuteHandler().applyMutation(client, mutation);

            assertEquals(SyncActionState.MALFORMED, result.actionState(),
                    "a value carrying " + FavoritesAction.class.getSimpleName() + " must be tagged malformed");
        }
    }

    @Nested
    @DisplayName("applyMutation: non-SET operations are UNSUPPORTED")
    class NonSet {
        @Test
        @DisplayName("REMOVE is rejected before any side effect")
        void removeIsUnsupported() {
            client.store().addContact(new ContactBuilder().jid(CONTACT).build());

            var result = new UserStatusMuteHandler().applyMutation(
                    client, mutationFor(CONTACT.toString(), Boolean.TRUE, SyncdOperation.REMOVE, Instant.now()));

            assertEquals(SyncActionState.UNSUPPORTED, result.actionState());
            assertFalse(client.store().findContactByJid(CONTACT).orElseThrow().statusMuted(),
                    "REMOVE must not flip the contact's mute flag");
        }
    }

    @Nested
    @DisplayName("getMutationForStatusMute â€” builder helper")
    class Builder {
        @Test
        @DisplayName("emits a SET pending mutation with the requested wid and flag")
        void buildsPendingMutation() {
            var ts = Instant.ofEpochSecond(1700000000L);

            var pending = new UserStatusMuteHandler().getMutationForStatusMute(CONTACT, true, ts);

            assertEquals(SyncdOperation.SET, pending.mutation().operation());
            assertEquals(ts, pending.mutation().timestamp());
            assertEquals(UserStatusMuteAction.ACTION_VERSION, pending.mutation().actionVersion());
            assertEquals("[\"userStatusMute\",\"" + CONTACT + "\"]", pending.mutation().index());
            var action = (UserStatusMuteAction) pending.mutation().value().action().orElseThrow();
            assertTrue(action.muted(), "the action carries the muted=true flag verbatim");
        }
    }

    @Nested
    @DisplayName("resolveConflicts â€” default timestamp-based")
    class ResolveConflicts {
        @Test
        @DisplayName("remote with the later timestamp wins (APPLY_REMOTE_DROP_LOCAL)")
        void remoteWins() {
            var earlier = mutationFor(CONTACT.toString(), Boolean.FALSE, SyncdOperation.SET, Instant.ofEpochSecond(1700000000L));
            var later   = mutationFor(CONTACT.toString(), Boolean.TRUE,  SyncdOperation.SET, Instant.ofEpochSecond(1700000010L));

            var resolution = new UserStatusMuteHandler().resolveConflicts(earlier, later);

            assertEquals(com.github.auties00.cobalt.model.sync.ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL, resolution.state());
        }

        @Test
        @DisplayName("local with the strictly later timestamp wins (SKIP_REMOTE)")
        void localWins() {
            var later   = mutationFor(CONTACT.toString(), Boolean.TRUE,  SyncdOperation.SET, Instant.ofEpochSecond(1700000010L));
            var earlier = mutationFor(CONTACT.toString(), Boolean.FALSE, SyncdOperation.SET, Instant.ofEpochSecond(1700000000L));

            var resolution = new UserStatusMuteHandler().resolveConflicts(later, earlier);

            assertEquals(com.github.auties00.cobalt.model.sync.ConflictResolutionState.SKIP_REMOTE, resolution.state());
        }
    }

}
