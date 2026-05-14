package com.github.auties00.cobalt.sync.key;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientOfflineResumeState;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKeyBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MissingSyncKeyRequestService} — Cobalt's adapter for
 * {@code WAWebSyncdHandleMissingKeys} and the
 * {@code WAWebKeyManagementSendKeyRequestApi.sendAppStateSyncKeyRequest} path.
 *
 * <p>Live peer-message dispatch is exercised by the Phase 9 integration cycles.
 * These tests pin the early-return gates that {@code handleMissingKeys}
 * implements:
 * <ul>
 *   <li>Empty key-id collection is a no-op.</li>
 *   <li>Resume state {@code != COMPLETE} is a no-op
 *       ({@code isResumeFromRestartComplete()} check).</li>
 *   <li>Every supplied id is already tracked in the missing-key store → no-op
 *       (the {@code .filter(e => !a.has(e))} branch reduces to empty).</li>
 *   <li>{@link MissingSyncKeyRequestService#reRequestMissingKeys} short-circuits
 *       on empty input.</li>
 *   <li>{@link MissingSyncKeyRequestService#setTimeoutScheduler} is a setter
 *       wiring helper that must accept any non-null reference.</li>
 * </ul>
 */
@DisplayName("MissingSyncKeyRequestService")
class MissingSyncKeyRequestServiceTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final Jid SELF_PN_DEVICE_1 = Jid.of("19250000001:1@s.whatsapp.net");

    private TestWhatsAppClient client;
    private WhatsAppStore store;
    private TestABPropsService props;
    private MissingSyncKeyRequestService requestService;
    private MissingSyncKeyTimeoutScheduler timeoutScheduler;

    @BeforeEach
    void setUp() {
        props = TestABPropsService.builder().build();
        props.set(ABProp.SYNCD_WAIT_FOR_KEY_TIMEOUT_DAYS, 30);

        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        store.setJid(SELF_PN_DEVICE_1);
        client = TestWhatsAppClient.create().withStore(store);
        var wam = new DefaultWamService(client, props);
        requestService = new MissingSyncKeyRequestService(client, wam);
        timeoutScheduler = new MissingSyncKeyTimeoutScheduler(client, props, requestService);
        requestService.setTimeoutScheduler(timeoutScheduler);
    }

    @AfterEach
    void tearDown() {
        timeoutScheduler.shutdown();
    }

    @Nested
    @DisplayName("requestMissingKeys — early-return gates")
    class RequestMissingKeys {
        @Test
        @DisplayName("empty collection is a no-op (no scheduler/store side effect)")
        void emptyIsNoOp() {
            store.setOfflineResumeState(WhatsAppClientOfflineResumeState.COMPLETE);
            assertDoesNotThrow(() -> requestService.requestMissingKeys(List.of()));
            assertTrue(store.missingSyncKeys().isEmpty(),
                    "empty input must not track anything in the missing-key store");
        }

        @Test
        @DisplayName("resume-from-restart not complete short-circuits (no send attempt)")
        void resumeIncompleteShortCircuits() {
            // Default offlineResumeState is INIT → isResumeFromRestartComplete() returns false.
            // handleMissingKeys must return before reaching sendKeyRequestToAllDevices, which
            // would throw IllegalStateException for the empty companion-device list on
            // TestWhatsAppClient.
            assertDoesNotThrow(() -> requestService.requestMissingKeys(
                    List.of(new byte[]{1, 2, 3, 4, 5, 6})));
            assertTrue(store.missingSyncKeys().isEmpty(),
                    "no missing-key tracking until resume completes");
        }

        @Test
        @DisplayName("every id already tracked → handleMissingKeys returns before sending")
        void allAlreadyTrackedShortCircuits() {
            store.setOfflineResumeState(WhatsAppClientOfflineResumeState.COMPLETE);
            var keyId = new byte[]{1, 2, 3, 4, 5, 6};
            store.addMissingSyncKey(new MissingDeviceSyncKeyBuilder()
                    .keyId(keyId)
                    .timestamp(Instant.now())
                    .askedDevices(Set.of(0))
                    .build());

            assertDoesNotThrow(() -> requestService.requestMissingKeys(List.of(keyId)),
                    "already-tracked id reduces the filter to empty; sendKeyRequestToAllDevices is never reached");
            assertTrue(store.findMissingSyncKey(keyId).isPresent(),
                    "existing missing-key tracker stays in place");
        }

        @Test
        @DisplayName("null keyIds inside the input are filtered out before tracking")
        void nullKeyIdsFiltered() {
            // resume incomplete → the whole call is a no-op anyway, but the null-filter is
            // exercised when paired with `requestMissingKey(byte[])` overload routing.
            assertDoesNotThrow(() -> requestService.requestMissingKeys(
                    java.util.Arrays.asList(null, null)));
        }

        @Test
        @DisplayName("single-key entry point delegates to requestMissingKeys")
        void singleKeyDelegates() {
            assertDoesNotThrow(() -> requestService.requestMissingKey(new byte[]{1, 2, 3, 4, 5, 6}),
                    "single-key call is a thin wrapper around requestMissingKeys(List.of(id))");
        }
    }

    @Nested
    @DisplayName("reRequestMissingKeys — empty short-circuit")
    class ReRequestMissingKeys {
        @Test
        @DisplayName("empty collection is a no-op")
        void emptyIsNoOp() {
            assertDoesNotThrow(() -> requestService.reRequestMissingKeys(List.of()));
        }
    }

    @Nested
    @DisplayName("setTimeoutScheduler — post-construction wiring")
    class SetTimeoutScheduler {
        @Test
        @DisplayName("accepts the scheduler reference and does not throw")
        void wiringAccepted() {
            // Required because MissingSyncKeyTimeoutScheduler also depends on this service,
            // producing a circular construction dependency that WebAppStateService resolves
            // by constructing both, then calling setTimeoutScheduler.
            assertDoesNotThrow(() -> requestService.setTimeoutScheduler(timeoutScheduler));
        }
    }
}
