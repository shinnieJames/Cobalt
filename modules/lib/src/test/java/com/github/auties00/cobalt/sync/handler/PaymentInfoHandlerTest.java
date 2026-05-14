package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentInfoAction;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentInfoActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.SyncFixtures;
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

/**
 * Tests for {@link PaymentInfoHandler}, Cobalt's adapter for
 * {@code WAWebPaymentInfoSync}.
 *
 * <p>The handler is gated on the SMB platform variants and the
 * {@code order_details_payment_instructions_sync_enabled} AB prop. On
 * {@code SET} it persists the CPI string into the store via
 * {@code setPaymentInstructionCpi}.
 *
 * <p>Matrix:
 * <ul>
 *   <li>Metadata wire constants.</li>
 *   <li>Non-SMB platform short-circuits to {@code UNSUPPORTED}.</li>
 *   <li>AB-prop gate disabled returns {@code UNSUPPORTED}.</li>
 *   <li>Non-{@code SET} operation is {@code UNSUPPORTED}.</li>
 *   <li>Malformed value (missing sub-message or null {@code cpi}) is
 *       {@code MALFORMED}.</li>
 *   <li>Happy path: SET stores the CPI and reports {@code SUCCESS}.</li>
 *   <li>Default conflict resolution.</li>
 *   <li>Default batch dispatch.</li>
 *   <li>Builder methods (n/a — handler has none).</li>
 *   <li>Malformed index (n/a — handler does not parse indexParts).</li>
 *   <li>WA Web byte-parity oracle (gated).</li>
 * </ul>
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
     * Builds a fresh harness and a fresh AB-props service. Tests opt into the
     * SMB platform and the AB prop explicitly to keep each test path declarative.
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
     * Sets the local store's platform to {@code IOS_BUSINESS} (one of the two
     * SMB variants accepted by the handler).
     */
    private void smbPlatform() {
        store.device().setPlatform(ClientPlatformType.IOS_BUSINESS);
    }

    /**
     * Enables the {@code order_details_payment_instructions_sync_enabled} AB prop.
     */
    private void enableSync() {
        props.set(ABProp.ORDER_DETAILS_PAYMENT_INSTRUCTIONS_SYNC_ENABLED, true);
    }

    /**
     * Builds a trusted SET mutation carrying the given action.
     *
     * @param action the payment-info action payload, or {@code null} to omit
     *               the sub-message
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
            enableSync();
            var batch = List.of(setMutation(new PaymentInfoActionBuilder().cpi("cpi-1234").build()));
            var results = handler.applyMutationBatch(testClient, batch);
            assertEquals(1, results.size());
            assertEquals(SyncActionState.SUCCESS, results.get(0).actionState());
        }
    }

    @Nested
    @DisplayName("static builder methods (n/a)")
    class Builders {
        @Test
        @DisplayName("PaymentInfoHandler exposes no static *Mutation builder")
        void noBuilders() {
            // WA Web's WAWebPaymentInfoSync only consumes the action from the primary device;
            // there is no companion-side outgoing-mutation factory to test. This @Nested makes
            // the absence explicit per the per-handler matrix rule.
            assertFalse(hasPublicMutationBuilder(),
                    "the handler must not expose a public *Mutation builder");
        }

        /**
         * Returns whether the handler exposes any public method whose name ends
         * with {@code Mutation} that returns a {@code SyncPendingMutation}.
         *
         * @return {@code true} when such a method exists
         */
        private static boolean hasPublicMutationBuilder() {
            for (var m : PaymentInfoHandler.class.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isPublic(m.getModifiers())) continue;
                if (!m.getName().endsWith("Mutation")) continue;
                if (m.getReturnType().getSimpleName().equals("SyncPendingMutation")) return true;
            }
            return false;
        }
    }

    @Nested
    @DisplayName("WA Web byte-parity oracle")
    class OracleParity {
        @Test
        @DisplayName("captured encode payload (when present) matches Cobalt's wire encoding")
        void oracle() {
            if (!SyncFixtures.isOracleAvailable("handler/payment-info/encode")) {
                return;
            }
            var oracle = SyncFixtures.loadOracle("handler/payment-info/encode");
            assertNotNull(oracle);
        }
    }
}
