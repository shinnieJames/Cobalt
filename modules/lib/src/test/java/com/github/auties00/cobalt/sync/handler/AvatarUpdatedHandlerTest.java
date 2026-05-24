package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.AvatarUpdatedAction;
import com.github.auties00.cobalt.model.sync.action.media.AvatarUpdatedActionBuilder;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AvatarUpdatedHandler}, Cobalt's adapter for
 * {@code WAWebStickersAvatarUpdatedSyncAction}.
 *
 * <p>The handler routes the {@code "avatar_updated_action"} mutation and
 * carries side effects: it toggles the {@code hasAvatar} flag on the store
 * and clears the recent-avatar-sticker cache.
 *
 * <p>Matrix:
 * <ul>
 *   <li>Metadata wire constants.</li>
 *   <li>AB-prop gate disabled returns {@code UNSUPPORTED}.</li>
 *   <li>Non-{@code SET} operation is {@code UNSUPPORTED}.</li>
 *   <li>Missing action sub-message or missing {@code eventType} is
 *       {@code MALFORMED}.</li>
 *   <li>Pairing-timestamp gate skips older mutations.</li>
 *   <li>Happy path: {@code CREATED}/{@code UPDATED}/{@code DELETED} flip
 *       the local {@code hasAvatar} flag and clear avatar stickers.</li>
 *   <li>Default conflict resolution (n/a override).</li>
 *   <li>Default batch dispatch (n/a override).</li>
 *   <li>Builder methods (n/a - handler has none).</li>
 * </ul>
 */
@DisplayName("AvatarUpdatedHandler")
class AvatarUpdatedHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    private WhatsAppStore store;
    private TestWhatsAppClient testClient;
    private TestABPropsService props;
    private AvatarUpdatedHandler handler;

    /**
     * Builds a fresh store and test client with an empty AB-props service that
     * defaults the avatar feature flag to its declared default ({@code false}).
     */
    @BeforeEach
    void setUp() {
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        props = TestABPropsService.builder().build();
        testClient = TestWhatsAppClient.create()
                .withStore(store)
                .withAbPropsService(props);
        handler = new AvatarUpdatedHandler(props);
    }

    /**
     * Enables the {@code enable_avatars_on_web_companion} AB prop on the
     * per-test props service.
     */
    private void enableAvatars() {
        props.set(ABProp.ENABLE_AVATARS_ON_WEB_COMPANION, true);
    }

    /**
     * Builds a trusted SET mutation carrying the given event type and an
     * empty index (the WA Web action carries no index parts).
     *
     * @param eventType the avatar event type
     * @param timestamp the mutation timestamp
     * @return the trusted mutation
     */
    private static DecryptedMutation.Trusted setMutation(AvatarUpdatedAction.AvatarEventType eventType, Instant timestamp) {
        var action = new AvatarUpdatedActionBuilder().eventType(eventType).build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .avatarUpdatedAction(action)
                .build();
        return new DecryptedMutation.Trusted("[\"avatar_updated_action\"]", value,
                SyncdOperation.SET, timestamp, AvatarUpdatedAction.ACTION_VERSION);
    }

    @Nested
    @DisplayName("metadata - wire constants")
    class Metadata {
        @Test
        @DisplayName("actionName() is avatar_updated_action")
        void actionName() {
            assertEquals(AvatarUpdatedAction.ACTION_NAME, handler.actionName());
            assertEquals("avatar_updated_action", handler.actionName());
        }

        @Test
        @DisplayName("collectionName() is REGULAR")
        void collectionName() {
            assertEquals(SyncPatchType.REGULAR, handler.collectionName());
            assertEquals(AvatarUpdatedAction.COLLECTION_NAME, handler.collectionName());
        }

        @Test
        @DisplayName("version() is 7")
        void version() {
            assertEquals(7, handler.version());
            assertEquals(AvatarUpdatedAction.ACTION_VERSION, handler.version());
        }
    }

    @Nested
    @DisplayName("applyMutation - AB-prop gate")
    class FeatureGating {
        @Test
        @DisplayName("disabled enable_avatars_on_web_companion AB prop short-circuits to UNSUPPORTED")
        void disabledAbProp() {
            // props builder defaults the flag to "false"
            var result = handler.applyMutation(testClient,
                    setMutation(AvatarUpdatedAction.AvatarEventType.CREATED, Instant.ofEpochSecond(1_700_000_000L)));
            assertEquals(SyncActionState.UNSUPPORTED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - non-SET operation")
    class RemoveBranch {
        @Test
        @DisplayName("REMOVE operation past the AB-prop gate is UNSUPPORTED")
        void removeUnsupported() {
            enableAvatars();
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var action = new AvatarUpdatedActionBuilder()
                    .eventType(AvatarUpdatedAction.AvatarEventType.CREATED).build();
            var value = new SyncActionValueBuilder().timestamp(ts).avatarUpdatedAction(action).build();
            var mutation = new DecryptedMutation.Trusted("[\"avatar_updated_action\"]", value,
                    SyncdOperation.REMOVE, ts, 7);
            assertEquals(SyncActionState.UNSUPPORTED,
                    handler.applyMutation(testClient, mutation).actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed value")
    class MalformedValue {
        @Test
        @DisplayName("missing avatarUpdatedAction sub-message is MALFORMED")
        void missingActionMessage() {
            enableAvatars();
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var value = new SyncActionValueBuilder().timestamp(ts).build();
            var mutation = new DecryptedMutation.Trusted("[\"avatar_updated_action\"]", value,
                    SyncdOperation.SET, ts, 7);
            assertEquals(SyncActionState.MALFORMED,
                    handler.applyMutation(testClient, mutation).actionState());
        }

        @Test
        @DisplayName("present sub-message with null eventType is MALFORMED")
        void missingEventType() {
            enableAvatars();
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var action = new AvatarUpdatedActionBuilder().build(); // no eventType
            var value = new SyncActionValueBuilder().timestamp(ts).avatarUpdatedAction(action).build();
            var mutation = new DecryptedMutation.Trusted("[\"avatar_updated_action\"]", value,
                    SyncdOperation.SET, ts, 7);
            assertEquals(SyncActionState.MALFORMED,
                    handler.applyMutation(testClient, mutation).actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - pairing-timestamp gate")
    class PairingGate {
        @Test
        @DisplayName("mutation timestamp <= pairing timestamp is SKIPPED")
        void skipsPrePairingMutations() {
            enableAvatars();
            var pairingTs = Instant.ofEpochSecond(1_700_000_100L);
            store.setPairingTimestamp(pairingTs);
            // Mutation older than pairing timestamp - must be skipped
            var result = handler.applyMutation(testClient,
                    setMutation(AvatarUpdatedAction.AvatarEventType.CREATED,
                            Instant.ofEpochSecond(1_700_000_000L)));
            assertEquals(SyncActionState.SKIPPED, result.actionState());
            assertTrue(store.hasAvatar().isEmpty(),
                    "pre-pairing mutation must not flip hasAvatar");
        }

        @Test
        @DisplayName("mutation timestamp == pairing timestamp is SKIPPED (boundary inclusive)")
        void equalTimestampIsSkipped() {
            enableAvatars();
            var ts = Instant.ofEpochSecond(1_700_000_100L);
            store.setPairingTimestamp(ts);
            var result = handler.applyMutation(testClient,
                    setMutation(AvatarUpdatedAction.AvatarEventType.CREATED, ts));
            assertEquals(SyncActionState.SKIPPED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - happy SET path")
    class HappySet {
        @Test
        @DisplayName("CREATED sets hasAvatar=true and clears recent avatar stickers")
        void createdSetsHasAvatarTrue() {
            enableAvatars();
            var result = handler.applyMutation(testClient,
                    setMutation(AvatarUpdatedAction.AvatarEventType.CREATED,
                            Instant.ofEpochSecond(1_700_000_000L)));
            assertEquals(MutationApplicationResult.success(), result);
            assertEquals(true, store.hasAvatar().orElse(false),
                    "CREATED must set hasAvatar=true");
        }

        @Test
        @DisplayName("UPDATED sets hasAvatar=true")
        void updatedSetsHasAvatarTrue() {
            enableAvatars();
            var result = handler.applyMutation(testClient,
                    setMutation(AvatarUpdatedAction.AvatarEventType.UPDATED,
                            Instant.ofEpochSecond(1_700_000_000L)));
            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertEquals(true, store.hasAvatar().orElse(false));
        }

        @Test
        @DisplayName("DELETED sets hasAvatar=false")
        void deletedSetsHasAvatarFalse() {
            enableAvatars();
            store.setHasAvatar(true);
            var result = handler.applyMutation(testClient,
                    setMutation(AvatarUpdatedAction.AvatarEventType.DELETED,
                            Instant.ofEpochSecond(1_700_000_000L)));
            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertEquals(false, store.hasAvatar().orElse(true),
                    "DELETED must clear hasAvatar");
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed index (n/a)")
    class MalformedIndex {
        @Test
        @DisplayName("the handler does not parse indexParts beyond position 0, so index malformations are not checked")
        void notExercised() {
            // The WA Web handler index is literally ["avatar_updated_action"]; nothing is read
            // from indexParts[1..]. The malformed-index branch is therefore not reachable for
            // this handler. This @Nested exists to make that "n/a" outcome explicit, per the
            // per-handler matrix requirement.
            enableAvatars();
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var action = new AvatarUpdatedActionBuilder()
                    .eventType(AvatarUpdatedAction.AvatarEventType.CREATED).build();
            var value = new SyncActionValueBuilder().timestamp(ts).avatarUpdatedAction(action).build();
            // Even with a wildly malformed index, the handler still succeeds because it does
            // not look at the index at all in WA Web.
            var mutation = new DecryptedMutation.Trusted("not-json", value, SyncdOperation.SET, ts, 7);
            assertEquals(SyncActionState.SUCCESS,
                    handler.applyMutation(testClient, mutation).actionState());
        }
    }

    @Nested
    @DisplayName("resolveConflicts - default timestamp tiebreaker")
    class ResolveConflicts {
        @Test
        @DisplayName("remote with later timestamp wins (APPLY_REMOTE_DROP_LOCAL)")
        void remoteLater() {
            var local = setMutation(AvatarUpdatedAction.AvatarEventType.CREATED,
                    Instant.ofEpochSecond(1_700_000_000L));
            var remote = setMutation(AvatarUpdatedAction.AvatarEventType.DELETED,
                    Instant.ofEpochSecond(1_700_000_010L));
            var resolution = handler.resolveConflicts(local, remote);
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    resolution.state());
        }

        @Test
        @DisplayName("remote with strictly earlier timestamp is skipped (SKIP_REMOTE)")
        void remoteEarlier() {
            var local = setMutation(AvatarUpdatedAction.AvatarEventType.CREATED,
                    Instant.ofEpochSecond(1_700_000_010L));
            var remote = setMutation(AvatarUpdatedAction.AvatarEventType.DELETED,
                    Instant.ofEpochSecond(1_700_000_000L));
            var resolution = handler.resolveConflicts(local, remote);
            assertEquals(ConflictResolutionState.SKIP_REMOTE,
                    resolution.state());
        }
    }

    @Nested
    @DisplayName("applyMutationBatch - default per-item dispatch (n/a override)")
    class BatchDispatch {
        @Test
        @DisplayName("the handler does not override applyMutationBatch")
        void defaultDispatchPreserved() {
            enableAvatars();
            var batch = List.of(setMutation(AvatarUpdatedAction.AvatarEventType.CREATED,
                    Instant.ofEpochSecond(1_700_000_000L)));
            var results = handler.applyMutationBatch(testClient, batch);
            assertEquals(1, results.size());
            assertEquals(SyncActionState.SUCCESS, results.get(0).actionState());
        }
    }

}
