package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.ArchiveChatActionBuilder;
import com.github.auties00.cobalt.model.sync.action.contact.StarAction;
import com.github.auties00.cobalt.model.sync.action.contact.StarActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link StarMessageHandler}'s parity with
 * {@code WAWebStarMessageSync.applyMutations}.
 *
 * @apiNote
 * Covers the wire-constant trio, the happy-path star/unstar flow, the
 * orphan branch when the message is unknown, the malformed-index
 * branches (missing slots, empty strings), the malformed-value branch
 * when the action is not a {@code StarAction}, the {@code REMOVE}
 * unsupported branch, and the default conflict-resolution tiebreaker.
 *
 * @implNote
 * The fixture seeds a single peer chat and an inbound message keyed
 * under {@link #MESSAGE_ID}; tests that exercise the unstar branch
 * pre-flip the starred flag before calling the handler so they can
 * observe the transition back to {@code false}.
 */
@DisplayName("StarMessageHandler")
class StarMessageHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final Jid PEER = Jid.of("1234567890@s.whatsapp.net");
    private static final String MESSAGE_ID = "msg-1";

    private TestWhatsAppClient client;

    /**
     * Builds the per-test harness.
     *
     * @apiNote
     * Each test runs against a fresh
     * {@link com.github.auties00.cobalt.store.WhatsAppStore} so seeded
     * chats and messages do not leak between cases.
     */
    @BeforeEach
    void setUp() {
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        client = TestWhatsAppClient.create().withStore(store);
    }

    /**
     * Wraps the given star payload into a trusted {@code SET} mutation.
     *
     * @apiNote
     * The five-slot index follows WA Web's
     * {@code ["star", chatJid, messageId, fromMe, participant]} layout;
     * tests passing {@code "0"} for {@code fromMe} and {@code participant}
     * exercise the 1:1 chat branch.
     *
     * @param starred     the new starred flag
     * @param chatJid     the remote chat JID
     * @param messageId   the message id
     * @param fromMe      the {@code fromMe} flag as {@code "0"} or {@code "1"}
     * @param participant the participant JID string or {@code "0"}
     * @param ts          the mutation timestamp
     * @return the trusted mutation
     */
    private static DecryptedMutation.Trusted starMutation(boolean starred, Jid chatJid, String messageId, String fromMe, String participant, Instant ts) {
        var value = new SyncActionValueBuilder()
                .timestamp(ts)
                .starAction(new StarActionBuilder().starred(starred).build())
                .build();
        var index = JSON.toJSONString(List.of("star", chatJid.toString(), messageId, fromMe, participant));
        return new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, 2);
    }

    /**
     * Seeds the peer chat with an inbound message keyed under
     * {@link #MESSAGE_ID}.
     *
     * @apiNote
     * Used by the happy-path tests so the handler's
     * {@code findMessageById} lookup returns a populated message;
     * orphan-branch tests deliberately skip this step.
     */
    private void seedMessage() {
        var chat = client.store().addNewChat(PEER);
        var key = new MessageKeyBuilder()
                .id(MESSAGE_ID).fromMe(false).parentJid(PEER)
                .build();
        chat.addMessage(new ChatMessageInfoBuilder()
                .key(key)
                .message(MessageContainer.of("hello"))
                .timestamp(Instant.now())
                .build());
    }

    @Nested
    @DisplayName("metadata")
    class Metadata {
        @Test
        @DisplayName("actionName returns \"star\"")
        void actionName() {
            assertEquals("star", new StarMessageHandler().actionName());
            assertEquals(StarAction.ACTION_NAME, new StarMessageHandler().actionName());
        }

        @Test
        @DisplayName("collectionName resolves to REGULAR_HIGH")
        void collectionName() {
            assertEquals(SyncPatchType.REGULAR_HIGH, new StarMessageHandler().collectionName());
        }

        @Test
        @DisplayName("version returns the declared action version 2")
        void version() {
            assertEquals(2, new StarMessageHandler().version());
        }
    }

    @Nested
    @DisplayName("applyMutation SET - happy path")
    class HappySet {
        @Test
        @DisplayName("starred=true on an existing message stars it")
        void starsTheMessage() {
            seedMessage();

            var result = new StarMessageHandler().applyMutation(client,
                    starMutation(true, PEER, MESSAGE_ID, "0", "0", Instant.ofEpochSecond(1L)));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            var message = client.store().findMessageById(client.store().findChatByJid(PEER).orElseThrow(), MESSAGE_ID).orElseThrow();
            assertTrue(message.starred());
        }

        @Test
        @DisplayName("starred=false unstars an already-starred message")
        void unstarsTheMessage() {
            seedMessage();
            client.store().findMessageById(client.store().findChatByJid(PEER).orElseThrow(), MESSAGE_ID)
                    .orElseThrow().setStarred(true);

            var result = new StarMessageHandler().applyMutation(client,
                    starMutation(false, PEER, MESSAGE_ID, "0", "0", Instant.ofEpochSecond(2L)));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            var message = client.store().findMessageById(client.store().findChatByJid(PEER).orElseThrow(), MESSAGE_ID).orElseThrow();
            assertFalse(message.starred());
        }
    }

    @Nested
    @DisplayName("applyMutation - orphan")
    class Orphan {
        @Test
        @DisplayName("SET against an unknown message returns ORPHAN with modelType=Msg")
        void orphanReturnsMsgModel() {
            // Chat exists but the message is missing.
            client.store().addNewChat(PEER);

            var result = new StarMessageHandler().applyMutation(client,
                    starMutation(true, PEER, "absent-id", "0", "0", Instant.ofEpochSecond(1L)));

            assertEquals(SyncActionState.ORPHAN, result.actionState());
            assertEquals("Msg", result.modelType());
            assertNotNull(result.modelId(),
                    "orphan modelId must carry the serialized MsgKey for replay");
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed value")
    class MalformedValue {
        @Test
        @DisplayName("a SyncActionValue carrying an archiveChatAction instead of starAction is MALFORMED")
        void wrongActionTypeIsMalformed() {
            seedMessage();
            var wrong = new SyncActionValueBuilder()
                    .timestamp(Instant.ofEpochSecond(1L))
                    .archiveChatAction(new ArchiveChatActionBuilder().archived(true).build())
                    .build();
            var mutation = new DecryptedMutation.Trusted(
                    JSON.toJSONString(List.of("star", PEER.toString(), MESSAGE_ID, "0", "0")),
                    wrong, SyncdOperation.SET, Instant.ofEpochSecond(1L), 2);

            var result = new StarMessageHandler().applyMutation(client, mutation);
            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed index")
    class MalformedIndex {
        @Test
        @DisplayName("a 4-element index missing the participant slot is MALFORMED")
        void shortIndexIsMalformed() {
            seedMessage();
            var value = new SyncActionValueBuilder()
                    .timestamp(Instant.ofEpochSecond(1L))
                    .starAction(new StarActionBuilder().starred(true).build())
                    .build();
            var mutation = new DecryptedMutation.Trusted(
                    "[\"star\",\"" + PEER + "\",\"" + MESSAGE_ID + "\",\"0\"]",
                    value, SyncdOperation.SET, Instant.ofEpochSecond(1L), 2);

            var result = new StarMessageHandler().applyMutation(client, mutation);
            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }

        @Test
        @DisplayName("an index whose chatJid slot is empty is MALFORMED")
        void emptyChatJidIsMalformed() {
            seedMessage();
            var value = new SyncActionValueBuilder()
                    .timestamp(Instant.ofEpochSecond(1L))
                    .starAction(new StarActionBuilder().starred(true).build())
                    .build();
            var mutation = new DecryptedMutation.Trusted(
                    "[\"star\",\"\",\"" + MESSAGE_ID + "\",\"0\",\"0\"]",
                    value, SyncdOperation.SET, Instant.ofEpochSecond(1L), 2);

            var result = new StarMessageHandler().applyMutation(client, mutation);
            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - REMOVE")
    class RemoveOperation {
        @Test
        @DisplayName("REMOVE returns UNSUPPORTED")
        void removeReturnsUnsupported() {
            seedMessage();
            var mutation = new DecryptedMutation.Trusted(
                    JSON.toJSONString(List.of("star", PEER.toString(), MESSAGE_ID, "0", "0")),
                    new SyncActionValueBuilder()
                            .timestamp(Instant.ofEpochSecond(1L))
                            .starAction(new StarActionBuilder().starred(true).build())
                            .build(),
                    SyncdOperation.REMOVE, Instant.ofEpochSecond(1L), 2);

            var result = new StarMessageHandler().applyMutation(client, mutation);
            assertEquals(SyncActionState.UNSUPPORTED, result.actionState());
        }
    }

    @Nested
    @DisplayName("resolveConflicts - default timestamp-based behaviour")
    class ResolveConflicts {
        @Test
        @DisplayName("older local vs. newer remote -> APPLY_REMOTE_DROP_LOCAL")
        void newerRemoteWins() {
            var local = starMutation(false, PEER, MESSAGE_ID, "0", "0", Instant.ofEpochSecond(100L));
            var remote = starMutation(true, PEER, MESSAGE_ID, "0", "0", Instant.ofEpochSecond(200L));

            var resolution = new StarMessageHandler().resolveConflicts(local, remote);
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL, resolution.state());
        }

        @Test
        @DisplayName("newer local vs. older remote -> SKIP_REMOTE")
        void newerLocalWins() {
            var local = starMutation(true, PEER, MESSAGE_ID, "0", "0", Instant.ofEpochSecond(300L));
            var remote = starMutation(false, PEER, MESSAGE_ID, "0", "0", Instant.ofEpochSecond(200L));

            var resolution = new StarMessageHandler().resolveConflicts(local, remote);
            assertEquals(ConflictResolutionState.SKIP_REMOTE, resolution.state());
        }
    }

}
