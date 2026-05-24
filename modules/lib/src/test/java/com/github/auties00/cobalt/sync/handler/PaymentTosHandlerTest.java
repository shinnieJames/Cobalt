package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentTosAction;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentTosActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.factory.PaymentTosMutationFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises {@link PaymentTosHandler} against the
 * {@code WAWebPaymentTosSync.applyMutations} per-mutation flow.
 *
 * @apiNote
 * Verifies the SMB platform gate, the
 * {@code payments_br_pix_on_web} AB-prop gate, the
 * {@link SyncdOperation#SET}
 * happy path that persists the action via
 * {@code WhatsAppStore.setPaymentTos}, the malformed-value
 * classification when the
 * {@link PaymentTosAction}
 * payload is missing, and that non-{@code SET} operations and gate
 * failures all surface as
 * {@link SyncActionState#UNSUPPORTED}.
 *
 * @implNote
 * This implementation builds mutations directly via the local helper
 * because the corresponding mutation factory is exercised in a
 * separate test; the handler does not parse {@code indexParts} so
 * the malformed-index dimension has no surface here.
 */
@DisplayName("PaymentTosHandler")
class PaymentTosHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    private WhatsAppStore store;
    private TestWhatsAppClient testClient;
    private TestABPropsService props;
    private PaymentTosHandler handler;
    private PaymentTosMutationFactory factory;

    /**
     * Builds a fresh harness, AB-props service, and mutation factory
     * before each test.
     *
     * @apiNote
     * Each test path opts into the SMB platform via
     * {@link #smbPlatform()} and the AB prop via {@link #enablePix()}
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
        handler = new PaymentTosHandler(props);
        factory = new PaymentTosMutationFactory();
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
     * Enables the {@link ABProp#PAYMENTS_BR_PIX_ON_WEB} AB prop on
     * the test fixture.
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
    private void enablePix() {
        props.set(ABProp.PAYMENTS_BR_PIX_ON_WEB, true);
    }

    /**
     * Builds a fully-populated
     * {@link PaymentTosAction}
     * carrying the only known
     * {@link PaymentTosAction.PaymentNotice}
     * variant.
     *
     * @apiNote
     * Internal helper consumed by every happy-path test in this
     * class; not used outside it.
     *
     * @implNote
     * This implementation pins {@code accepted=true} and the only
     * known notice so the test surface stays declarative as new
     * notice variants are added; tests that need a different notice
     * should build their own action.
     *
     * @return a fully-populated action
     */
    private static PaymentTosAction validAction() {
        return new PaymentTosActionBuilder()
                .paymentNotice(PaymentTosAction.PaymentNotice.BR_PAY_PRIVACY_POLICY)
                .accepted(true)
                .build();
    }

    /**
     * Builds a trusted
     * {@link SyncdOperation#SET}
     * mutation carrying the given action under the singleton
     * {@code ["payment_tos"]} index.
     *
     * @apiNote
     * Internal helper consumed by every test in this class; not used
     * outside it. Setting {@code action} to {@code null} omits the
     * {@code paymentTosAction} field on the value so the
     * malformed-value branch can be exercised.
     *
     * @implNote
     * This implementation pins the timestamp to a fixed second so
     * tests that compare timestamps (none today) stay deterministic.
     *
     * @param action the payment-tos action payload; may be
     *               {@code null} to omit the sub-message
     * @return the trusted mutation
     */
    private static DecryptedMutation.Trusted setMutation(PaymentTosAction action) {
        var ts = Instant.ofEpochSecond(1_700_000_000L);
        var builder = new SyncActionValueBuilder().timestamp(ts);
        if (action != null) builder.paymentTosAction(action);
        return new DecryptedMutation.Trusted("[\"payment_tos\"]", builder.build(),
                SyncdOperation.SET, ts, PaymentTosAction.ACTION_VERSION);
    }

    @Nested
    @DisplayName("metadata — wire constants")
    class Metadata {
        @Test
        @DisplayName("actionName() is payment_tos")
        void actionName() {
            assertEquals(PaymentTosAction.ACTION_NAME, handler.actionName());
            assertEquals("payment_tos", handler.actionName());
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
            assertEquals(PaymentTosAction.ACTION_VERSION, handler.version());
        }
    }

    @Nested
    @DisplayName("applyMutation — platform gating")
    class PlatformGating {
        @Test
        @DisplayName("default WEB platform short-circuits to UNSUPPORTED")
        void unsupportedOnNonSmb() {
            assertEquals(SyncActionState.UNSUPPORTED,
                    handler.applyMutation(testClient, setMutation(validAction())).actionState());
        }

        @Test
        @DisplayName("IOS_BUSINESS platform is accepted")
        void iosBusinessAccepted() {
            store.device().setPlatform(ClientPlatformType.IOS_BUSINESS);
            enablePix();
            assertEquals(SyncActionState.SUCCESS,
                    handler.applyMutation(testClient, setMutation(validAction())).actionState());
        }

        @Test
        @DisplayName("ANDROID_BUSINESS platform is accepted")
        void androidBusinessAccepted() {
            store.device().setPlatform(ClientPlatformType.ANDROID_BUSINESS);
            enablePix();
            assertEquals(SyncActionState.SUCCESS,
                    handler.applyMutation(testClient, setMutation(validAction())).actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation — AB-prop gate")
    class AbPropGating {
        @Test
        @DisplayName("disabled payments_br_pix_on_web AB prop returns UNSUPPORTED")
        void abPropDisabled() {
            smbPlatform();
            // AB prop defaults to false
            assertEquals(SyncActionState.UNSUPPORTED,
                    handler.applyMutation(testClient, setMutation(validAction())).actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation — non-SET operation")
    class RemoveBranch {
        @Test
        @DisplayName("REMOVE operation past the gates is UNSUPPORTED")
        void removeUnsupported() {
            smbPlatform();
            enablePix();
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var value = new SyncActionValueBuilder().timestamp(ts).paymentTosAction(validAction()).build();
            var mutation = new DecryptedMutation.Trusted("[\"payment_tos\"]", value,
                    SyncdOperation.REMOVE, ts, 7);
            assertEquals(SyncActionState.UNSUPPORTED,
                    handler.applyMutation(testClient, mutation).actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation — malformed value")
    class MalformedValue {
        @Test
        @DisplayName("missing paymentTosAction sub-message is MALFORMED")
        void missingActionMessage() {
            smbPlatform();
            enablePix();
            assertEquals(SyncActionState.MALFORMED,
                    handler.applyMutation(testClient, setMutation(null)).actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation — happy SET path")
    class HappySet {
        @Test
        @DisplayName("SET persists the action to the store and reports SUCCESS")
        void persistsAction() {
            smbPlatform();
            enablePix();
            var action = validAction();
            var result = handler.applyMutation(testClient, setMutation(action));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            var persisted = store.paymentTos().orElseThrow(
                    () -> new AssertionError("paymentTos must be persisted"));
            assertEquals(PaymentTosAction.PaymentNotice.BR_PAY_PRIVACY_POLICY, persisted.paymentNotice());
            assertEquals(true, persisted.accepted());
        }
    }

    @Nested
    @DisplayName("applyMutation — malformed index (n/a)")
    class MalformedIndex {
        @Test
        @DisplayName("the handler does not parse indexParts beyond position 0, so index malformations are not exercised")
        void notExercised() {
            // The wire index is literally ["payment_tos"]; the handler does not consult any
            // other element. This @Nested makes the absence explicit per the per-handler matrix.
            smbPlatform();
            enablePix();
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var value = new SyncActionValueBuilder().timestamp(ts).paymentTosAction(validAction()).build();
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
            var local = setMutation(validAction());
            var remoteTs = Instant.ofEpochSecond(1_700_000_010L);
            var remoteValue = new SyncActionValueBuilder().timestamp(remoteTs)
                    .paymentTosAction(validAction()).build();
            var remote = new DecryptedMutation.Trusted("[\"payment_tos\"]", remoteValue,
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
            enablePix();
            var batch = List.of(setMutation(validAction()));
            var results = handler.applyMutationBatch(testClient, batch);
            assertEquals(1, results.size());
            assertEquals(SyncActionState.SUCCESS, results.get(0).actionState());
        }
    }

    @Nested
    @DisplayName("getPaymentTosSetMutation — pending mutation builder")
    class Builder {
        @Test
        @DisplayName("builder emits a SET pending mutation at [\"payment_tos\"]")
        void buildsCorrect() {
            var action = validAction();
            var pending = factory.getPaymentTosSetMutation(action);
            var mutation = pending.mutation();
            assertEquals(SyncdOperation.SET, mutation.operation());
            assertEquals(PaymentTosAction.ACTION_VERSION, mutation.actionVersion());
            assertEquals("[\"payment_tos\"]", mutation.index());
            var roundtrip = mutation.value().action().filter(a -> a instanceof PaymentTosAction).map(a -> (PaymentTosAction) a).orElseThrow();
            assertEquals(PaymentTosAction.PaymentNotice.BR_PAY_PRIVACY_POLICY, roundtrip.paymentNotice());
        }

        @Test
        @DisplayName("attemptCount of a freshly built pending mutation is zero")
        void freshAttemptCount() {
            assertEquals(0,
                    factory.getPaymentTosSetMutation(validAction()).attemptCount());
        }
    }

}
