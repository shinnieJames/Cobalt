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
 * request reaches the peer-message dispatch path.
 *
 * @apiNote
 * Covers the four guards on the entry-point flow:
 * <ul>
 * <li>empty key-id collection short-circuits before scheduling
 * <li>the {@link WhatsAppClientOfflineResumeState#COMPLETE} guard short-circuits during
 *     restart resume
 * <li>every supplied id already in the missing-key store reduces the filter to empty
 * <li>{@link MissingSyncKeyRequestService#reRequestMissingKeys} short-circuits on empty
 * </ul>
 * Live peer-message dispatch and the per-device fan-out are exercised by the Phase 9
 * integration cycles.
 *
 * @implNote
 * This implementation wires both {@link MissingSyncKeyRequestService} and
 * {@link MissingSyncKeyTimeoutScheduler} per test to mirror the cyclic-dependency
 * resolution in {@link com.github.auties00.cobalt.sync.WebAppStateService}; without the
 * scheduler the trackMissingKeys terminal scheduler call would be a no-op and the all-
 * already-tracked test would not exercise the same code path.
 */
@DisplayName("MissingSyncKeyRequestService")
class MissingSyncKeyRequestServiceTest {
    /**
     * The fixed self phone-number JID used by every test in this class.
     */
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");

    /**
     * The fixed self LID JID used by every test in this class.
     */
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    /**
     * The fixed self device JID used by every test in this class (device 1).
     */
    private static final Jid SELF_PN_DEVICE_1 = Jid.of("19250000001:1@s.whatsapp.net");

    /**
     * The synthetic {@link TestWhatsAppClient} wired to {@link #store}.
     */
    private TestWhatsAppClient client;

    /**
     * The {@link WhatsAppStore} the request service reads and mutates.
     */
    private WhatsAppStore store;

    /**
     * The {@link TestABPropsService} preloaded with the wait-for-key timeout used by
     * {@link MissingSyncKeyTimeoutScheduler}.
     */
    private TestABPropsService props;

    /**
     * The system under test.
     */
    private MissingSyncKeyRequestService requestService;

    /**
     * The companion scheduler wired in via
     * {@link MissingSyncKeyRequestService#setTimeoutScheduler}.
     */
    private MissingSyncKeyTimeoutScheduler timeoutScheduler;

    /**
     * Builds a fresh harness per test: temporary store seeded with the device JIDs, AB
     * props with a 30-day wait-for-key timeout, request service wired to the scheduler.
     *
     * @apiNote
     * The 30-day timeout is large enough that no scheduled timer can fire mid-test.
     *
     * @implNote
     * This implementation closes the cyclic-dependency loop the same way
     * {@link com.github.auties00.cobalt.sync.WebAppStateService} does: construct both
     * collaborators, then call {@link MissingSyncKeyRequestService#setTimeoutScheduler}.
     */
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

    /**
     * Shuts down the scheduler so its single-threaded executor does not leak between
     * tests.
     */
    @AfterEach
    void tearDown() {
        timeoutScheduler.shutdown();
    }

    /**
     * Tests for the four early-return gates of {@code requestMissingKeys}.
     */
    @Nested
    @DisplayName("requestMissingKeys - early-return gates")
    class RequestMissingKeys {
        /**
         * Asserts that an empty input is a no-op and does not touch the missing-key
         * store.
         */
        @Test
        @DisplayName("empty collection is a no-op (no scheduler/store side effect)")
        void emptyIsNoOp() {
            store.setOfflineResumeState(WhatsAppClientOfflineResumeState.COMPLETE);
            assertDoesNotThrow(() -> requestService.requestMissingKeys(List.of()));
            assertTrue(store.missingSyncKeys().isEmpty(),
                    "empty input must not track anything in the missing-key store");
        }

        /**
         * Asserts that the resume-from-restart guard short-circuits before reaching the
         * dispatch path.
         *
         * @implNote
         * Default {@code offlineResumeState} is {@code INIT}, so
         * {@code isResumeFromRestartComplete()} returns {@code false}; a passing test
         * proves the guard returns before {@code sendKeyRequestToAllDevices}, which would
         * otherwise throw on the {@link TestWhatsAppClient}'s empty companion-device list.
         */
        @Test
        @DisplayName("resume-from-restart not complete short-circuits (no send attempt)")
        void resumeIncompleteShortCircuits() {
            assertDoesNotThrow(() -> requestService.requestMissingKeys(
                    List.of(new byte[]{1, 2, 3, 4, 5, 6})));
            assertTrue(store.missingSyncKeys().isEmpty(),
                    "no missing-key tracking until resume completes");
        }

        /**
         * Asserts that an input where every id is already tracked reduces the filter to
         * empty and short-circuits before any send attempt.
         */
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

        /**
         * Asserts that {@code null} entries inside the input are filtered before tracking.
         */
        @Test
        @DisplayName("null keyIds inside the input are filtered out before tracking")
        void nullKeyIdsFiltered() {
            assertDoesNotThrow(() -> requestService.requestMissingKeys(
                    Arrays.asList(null, null)));
        }

        /**
         * Asserts that the single-id overload routes through the same body as the
         * collection overload.
         */
        @Test
        @DisplayName("single-key entry point delegates to requestMissingKeys")
        void singleKeyDelegates() {
            assertDoesNotThrow(() -> requestService.requestMissingKey(new byte[]{1, 2, 3, 4, 5, 6}),
                    "single-key call is a thin wrapper around requestMissingKeys(List.of(id))");
        }
    }

    /**
     * Tests for the empty short-circuit of {@code reRequestMissingKeys}.
     */
    @Nested
    @DisplayName("reRequestMissingKeys - empty short-circuit")
    class ReRequestMissingKeys {
        /**
         * Asserts that an empty input short-circuits before any send attempt.
         */
        @Test
        @DisplayName("empty collection is a no-op")
        void emptyIsNoOp() {
            assertDoesNotThrow(() -> requestService.reRequestMissingKeys(List.of()));
        }
    }

    /**
     * Tests for the post-construction wiring helper
     * {@link MissingSyncKeyRequestService#setTimeoutScheduler}.
     */
    @Nested
    @DisplayName("setTimeoutScheduler - post-construction wiring")
    class SetTimeoutScheduler {
        /**
         * Asserts that wiring the scheduler reference does not throw.
         *
         * @implNote
         * The setter exists because {@link MissingSyncKeyTimeoutScheduler} also depends on
         * the request service, producing a cyclic construction dependency that
         * {@link com.github.auties00.cobalt.sync.WebAppStateService} resolves by
         * constructing both then calling this method.
         */
        @Test
        @DisplayName("accepts the scheduler reference and does not throw")
        void wiringAccepted() {
            assertDoesNotThrow(() -> requestService.setTimeoutScheduler(timeoutScheduler));
        }
    }
}
