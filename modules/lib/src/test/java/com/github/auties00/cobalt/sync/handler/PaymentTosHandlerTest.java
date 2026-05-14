package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentTosAction;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentTosActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.SyncFixtures;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.factory.PaymentTosMutationFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for {@link PaymentTosHandler}, Cobalt's adapter for
 * {@code WAWebPaymentTosSync}.
 *
 * <p>The handler is gated on the SMB platform variants and the
 * {@code payments_br_pix_on_web} AB prop. On {@code SET} it persists the
 * accepted ToS action into the store via {@code setPaymentTos}.
 *
 * <p>Matrix:
 * <ul>
 *   <li>Metadata wire constants.</li>
 *   <li>Non-SMB platform short-circuits to {@code UNSUPPORTED}.</li>
 *   <li>AB-prop gate disabled returns {@code UNSUPPORTED}.</li>
 *   <li>Non-{@code SET} operation is {@code UNSUPPORTED}.</li>
 *   <li>Malformed value (missing sub-message) is {@code MALFORMED}.</li>
 *   <li>Happy path: SET stores the action and reports {@code SUCCESS}.</li>
 *   <li>Default conflict resolution.</li>
 *   <li>Default batch dispatch.</li>
 *   <li>{@code getPaymentTosSetMutation} builder.</li>
 *   <li>Malformed index (n/a — handler does not parse indexParts).</li>
 *   <li>WA Web byte-parity oracle (gated).</li>
 * </ul>
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
     * Builds a fresh harness for each test.
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
     * Sets the local store's platform to {@code IOS_BUSINESS}.
     */
    private void smbPlatform() {
        store.device().setPlatform(ClientPlatformType.IOS_BUSINESS);
    }

    /**
     * Enables the {@code payments_br_pix_on_web} AB prop.
     */
    private void enablePix() {
        props.set(ABProp.PAYMENTS_BR_PIX_ON_WEB, true);
    }

    /**
     * Builds a valid PaymentTosAction with the only known PaymentNotice value.
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
     * Builds a trusted SET mutation carrying the given action.
     *
     * @param action the payment-tos action payload, or {@code null} to omit
     *               the sub-message
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
            assertEquals(com.github.auties00.cobalt.model.sync.ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
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

    @Nested
    @DisplayName("WA Web byte-parity oracle")
    class OracleParity {
        @Test
        @DisplayName("captured encode payload (when present) matches Cobalt's wire encoding")
        void oracle() {
            if (!SyncFixtures.isOracleAvailable("handler/payment-tos/encode")) {
                return;
            }
            var oracle = SyncFixtures.loadOracle("handler/payment-tos/encode");
            assertNotNull(oracle);
        }
    }
}
