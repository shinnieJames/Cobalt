package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentInfoAction;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentInfoActionBuilder;
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

/**
 * Exercises {@link PaymentInfoHandler} against the
 * {@code WAWebPaymentInfoSync.applyMutations} per-mutation flow.
 *
 * @apiNote
 * Verifies the SMB platform gate
 * ({@link ClientPlatformType#IOS_BUSINESS} /
 * {@link ClientPlatformType#ANDROID_BUSINESS}), the
 * {@link ABProp#ORDER_DETAILS_PAYMENT_INSTRUCTIONS_SYNC_ENABLED}
 * gate, the
 * {@link SyncdOperation#SET}
 * happy path that persists the CPI string via
 * {@link WhatsAppStore#setPaymentInstructionCpi(String)}, and the
 * malformed-value classification when {@link PaymentInfoAction#cpi()}
 * is missing. Non-{@code SET} operations and gate failures all
 * surface as
 * {@link SyncActionState#UNSUPPORTED}.
 *
 * @implNote
 * This implementation builds mutations directly via the local
 * helper because no public outgoing-mutation factory exists for this
 * action; the handler does not parse {@code indexParts} so the
 * malformed-index dimension has no surface here.
 */
@DisplayName("PaymentInfoHandler")
class PaymentInfoHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    private WhatsAppStore store;
    private TestWhatsAppClient testClient;
    private TestABPropsService props;
    private PaymentInfoHandler handler;

    /**
     * Builds a fresh harness and a fresh AB-props service before each
     * test.
     *
     * @apiNote
     * Each test path opts into the SMB platform via
     * {@link #smbPlatform()} and the AB prop via {@link #enableSync()}
     * explicitly so the gating dimension under test is declarative.
     *
     * @implNote
     * This implementation creates a clean
     * {@link WhatsAppStore} per test
     * via {@code DeviceFixtures.temporaryStore} so no state leaks
     * between tests.
     */
    @BeforeEach
    void setUp() {
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        props = TestABPropsService.builder().build();
        testClient = TestWhatsAppClient.create()
                .withStore(store)
                .withAbPropsService(props);
        handler = new PaymentInfoHandler(props);
    }

    /**
     * Sets the local store's platform to
     * {@link ClientPlatformType#IOS_BUSINESS}.
     *
     * @apiNote
     * Internal helper used by every test that needs to clear the SMB
     * platform gate without picking a specific business variant; the
     * {@code IOS_BUSINESS} choice is interchangeable with
     * {@link ClientPlatformType#ANDROID_BUSINESS}.
     *
     * @implNote
     * This implementation mutates the device record on the shared
     * fixture store rather than rebuilding the test client, because
     * the platform read happens at every {@code applyMutation} call.
     */
    private void smbPlatform() {
        store.device().setPlatform(ClientPlatformType.IOS_BUSINESS);
    }

    /**
     * Enables the
     * {@link ABProp#ORDER_DETAILS_PAYMENT_INSTRUCTIONS_SYNC_ENABLED}
     * AB prop on the test fixture.
     *
     * @apiNote
     * Internal helper used by every test that needs to clear the
     * AB-prop gate. Combined with {@link #smbPlatform()} to land on
     * the happy path.
     *
     * @implNote
     * This implementation mutates the
     * {@link TestABPropsService}
     * instance owned by the test client; no test isolation is broken
     * because the harness is rebuilt per test.
     */
    private void enableSync() {
        props.set(ABProp.ORDER_DETAILS_PAYMENT_INSTRUCTIONS_SYNC_ENABLED, true);
    }

    /**
     * Builds a trusted
     * {@link SyncdOperation#SET}
     * mutation carrying the given action under the singleton
     * {@code ["payment_info"]} index.
     *
     * @apiNote
     * Internal helper consumed by every test in this class; not used
     * outside it. Setting {@code action} to {@code null} omits the
     * {@code paymentInfoAction} field on the value so the
     * malformed-value branch can be exercised.
     *
     * @implNote
     * This implementation pins the timestamp to a fixed second so
     * tests that compare timestamps (none today) stay deterministic.
     *
     * @param action the payment-info action payload; may be
     *               {@code null} to omit the sub-message
     * @return the trusted mutation
     */
    private static DecryptedMutation.Trusted setMutation(PaymentInfoAction action) {
        var ts = Instant.ofEpochSecond(1_700_000_000L);
        var builder = new SyncActionValueBuilder().timestamp(ts);
        if (action != null) builder.paymentInfoAction(action);
        return new DecryptedMutation.Trusted("[\"payment_info\"]", builder.build(),
                SyncdOperation.SET, ts, PaymentInfoAction.ACTION_VERSION);
    }

    @Nested
    @DisplayName("metadata — wire constants")
    class Metadata {
        @Test
        @DisplayName("actionName() is payment_info")
        void actionName() {
            assertEquals(PaymentInfoAction.ACTION_NAME, handler.actionName());
            assertEquals("payment_info", handler.actionName());
        }

        @Test
        @DisplayName("collectionName() is REGULAR_LOW")
        void collectionName() {
            assertEquals(SyncPatchType.REGULAR_LOW, handler.collectionName());
        }

        @Test
        @DisplayName("version() is 7")
        void version() {
            assertEquals(7, handler.version());
            assertEquals(PaymentInfoAction.ACTION_VERSION, handler.version());
        }
    }

    @Nested
    @DisplayName("applyMutation — platform gating")
    class PlatformGating {
        @Test
        @DisplayName("default WEB platform short-circuits to UNSUPPORTED")
        void unsupportedOnNonSmb() {
            var action = new PaymentInfoActionBuilder().cpi("cpi-1234").build();
            assertEquals(SyncActionState.UNSUPPORTED,
                    handler.applyMutation(testClient, setMutation(action)).actionState());
        }

        @Test
        @DisplayName("ANDROID_BUSINESS platform is accepted")
        void androidBusinessAccepted() {
            store.device().setPlatform(ClientPlatformType.ANDROID_BUSINESS);
            enableSync();
            var action = new PaymentInfoActionBuilder().cpi("cpi-1234").build();
            assertEquals(SyncActionState.SUCCESS,
                    handler.applyMutation(testClient, setMutation(action)).actionState());
        }

        @Test
        @DisplayName("IOS_BUSINESS platform is accepted")
        void iosBusinessAccepted() {
            store.device().setPlatform(ClientPlatformType.IOS_BUSINESS);
            enableSync();
            var action = new PaymentInfoActionBuilder().cpi("cpi-1234").build();
            assertEquals(SyncActionState.SUCCESS,
                    handler.applyMutation(testClient, setMutation(action)).actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation — AB-prop gate")
    class AbPropGating {
        @Test
        @DisplayName("disabled order_details_payment_instructions_sync_enabled AB prop returns UNSUPPORTED")
        void abPropDisabled() {
            smbPlatform();
            // props builder defaults the flag to "false"
            var action = new PaymentInfoActionBuilder().cpi("cpi-1234").build();
            assertEquals(SyncActionState.UNSUPPORTED,
                    handler.applyMutation(testClient, setMutation(action)).actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation — non-SET operation")
    class RemoveBranch {
        @Test
        @DisplayName("REMOVE operation past the platform/AB-prop gates is UNSUPPORTED")
        void removeUnsupported() {
            smbPlatform();
            enableSync();
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var action = new PaymentInfoActionBuilder().cpi("cpi-1234").build();
            var value = new SyncActionValueBuilder().timestamp(ts).paymentInfoAction(action).build();
            var mutation = new DecryptedMutation.Trusted("[\"payment_info\"]", value,
                    SyncdOperation.REMOVE, ts, 7);
            assertEquals(SyncActionState.UNSUPPORTED,
                    handler.applyMutation(testClient, mutation).actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation — malformed value")
    class MalformedValue {
        @Test
        @DisplayName("missing paymentInfoAction sub-message is MALFORMED")
        void missingActionMessage() {
            smbPlatform();
            enableSync();
            assertEquals(SyncActionState.MALFORMED,
                    handler.applyMutation(testClient, setMutation(null)).actionState());
        }

        @Test
        @DisplayName("present sub-message with null cpi is MALFORMED")
        void missingCpi() {
            smbPlatform();
            enableSync();
            var action = new PaymentInfoActionBuilder().build(); // no cpi
            assertEquals(SyncActionState.MALFORMED,
                    handler.applyMutation(testClient, setMutation(action)).actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation — happy SET path")
    class HappySet {
        @Test
        @DisplayName("SET persists the cpi to the store and reports SUCCESS")
        void persistsCpi() {
            smbPlatform();
            enableSync();
            var action = new PaymentInfoActionBuilder().cpi("cpi-1234").build();
            var result = handler.applyMutation(testClient, setMutation(action));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertEquals("cpi-1234", store.paymentInstructionCpi().orElseThrow(
                    () -> new AssertionError("cpi must be persisted")));
        }
    }

    @Nested
    @DisplayName("applyMutation — malformed index (n/a)")
    class MalformedIndex {
        @Test
        @DisplayName("the handler does not parse indexParts beyond position 0, so index malformations are not exercised")
        void notExercised() {
            // The wire index is literally ["payment_info"]; the handler does not consult any
            // other element. Even a wildly malformed index reaches the success path when every
            // other precondition is satisfied. This @Nested makes the absence of the malformed-
            // index branch explicit per the per-handler matrix.
            smbPlatform();
            enableSync();
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var action = new PaymentInfoActionBuilder().cpi("cpi-malformed-index").build();
            var value = new SyncActionValueBuilder().timestamp(ts).paymentInfoAction(action).build();
            var mutation = new DecryptedMutation.Trusted("garbage-index", value, SyncdOperation.SET, ts, 7);
            assertEquals(SyncActionState.SUCCESS,
                    handler.applyMutation(testClient, mutation).actionState());
        }
    }

    @Nested
    @DisplayName("resolveConflicts — default timestamp tiebreaker")
    class ResolveConflicts {
        @Test
        @DisplayName("remote with later timestamp wins (APPLY_REMOTE_DROP_LOCAL)")
        void remoteLater() {
            var local = setMutation(new PaymentInfoActionBuilder().cpi("A").build());
            var remoteTs = Instant.ofEpochSecond(1_700_000_010L);
            var remoteValue = new SyncActionValueBuilder().timestamp(remoteTs)
                    .paymentInfoAction(new PaymentInfoActionBuilder().cpi("B").build())
                    .build();
            var remote = new DecryptedMutation.Trusted("[\"payment_info\"]", remoteValue,
                    SyncdOperation.SET, remoteTs, 7);
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(local, remote).state());
        }
    }

    @Nested
    @DisplayName("applyMutationBatch — default per-item dispatch (n/a override)")
    class BatchDispatch {
        @Test
        @DisplayName("the handler does not override applyMutationBatch")
        void defaultDispatchPreserved() {
            smbPlatform();
            enableSync();
            var batch = List.of(setMutation(new PaymentInfoActionBuilder().cpi("cpi-1234").build()));
            var results = handler.applyMutationBatch(testClient, batch);
            assertEquals(1, results.size());
            assertEquals(SyncActionState.SUCCESS, results.get(0).actionState());
        }
    }

}
