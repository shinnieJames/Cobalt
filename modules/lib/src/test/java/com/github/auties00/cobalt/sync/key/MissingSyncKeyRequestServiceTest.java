package com.github.auties00.cobalt.sync.key;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientOfflineResumeState;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKeyBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the early-return gates of {@link MissingSyncKeyRequestService} that decide whether a
 * request reaches the peer-message dispatch path: empty input, the
 * {@link WhatsAppClientOfflineResumeState#COMPLETE} resume guard, all-ids-already-tracked,
 * and {@code null}-id filtering.
 *
 * <p>Each test wires both {@link MissingSyncKeyRequestService} and
 * {@link MissingSyncKeyTimeoutScheduler} the same way
 * {@link com.github.auties00.cobalt.sync.WebAppStateService} resolves their cyclic
 * dependency (construct both, then {@link MissingSyncKeyRequestService#setTimeoutScheduler});
 * without the scheduler the all-already-tracked path would not reach the same terminal call.
 * The wait-for-key timeout is set to 30 days so no scheduled timer can fire mid-test.
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
    @DisplayName("requestMissingKeys - early-return gates")
    class RequestMissingKeys {
        @Test
        @DisplayName("empty collection is a no-op (no scheduler/store side effect)")
        void emptyIsNoOp() {
            store.setOfflineResumeState(WhatsAppClientOfflineResumeState.COMPLETE);
            assertDoesNotThrow(() -> requestService.requestMissingKeys(List.of()));
            assertTrue(store.missingSyncKeys().isEmpty(),
                    "empty input must not track anything in the missing-key store");
        }

        // Default offlineResumeState is INIT, so the resume guard returns before
        // sendKeyRequestToAllDevices, which would otherwise throw on the empty companion-device list.
        @Test
        @DisplayName("resume-from-restart not complete short-circuits (no send attempt)")
        void resumeIncompleteShortCircuits() {
            assertDoesNotThrow(() -> requestService.requestMissingKeys(
                    List.of(new byte[]{1, 2, 3, 4, 5, 6})));
            assertTrue(store.missingSyncKeys().isEmpty(),
                    "no missing-key tracking until resume completes");
        }

        @Test
        @DisplayName("every id already tracked -> handleMissingKeys returns before sending")
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
            assertDoesNotThrow(() -> requestService.requestMissingKeys(
                    Arrays.asList(null, null)));
        }

        @Test
        @DisplayName("single-key entry point delegates to requestMissingKeys")
        void singleKeyDelegates() {
            assertDoesNotThrow(() -> requestService.requestMissingKey(new byte[]{1, 2, 3, 4, 5, 6}),
                    "single-key call is a thin wrapper around requestMissingKeys(List.of(id))");
        }
    }

    @Nested
    @DisplayName("reRequestMissingKeys - empty short-circuit")
    class ReRequestMissingKeys {
        @Test
        @DisplayName("empty collection is a no-op")
        void emptyIsNoOp() {
            assertDoesNotThrow(() -> requestService.reRequestMissingKeys(List.of()));
        }
    }

    @Nested
    @DisplayName("setTimeoutScheduler - post-construction wiring")
    class SetTimeoutScheduler {
        @Test
        @DisplayName("accepts the scheduler reference and does not throw")
        void wiringAccepted() {
            assertDoesNotThrow(() -> requestService.setTimeoutScheduler(timeoutScheduler));
        }
    }
}
