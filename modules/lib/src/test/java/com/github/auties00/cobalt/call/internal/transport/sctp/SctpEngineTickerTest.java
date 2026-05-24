package com.github.auties00.cobalt.call.internal.transport.sctp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke tests for {@link SctpEngine}'s timer-tick wiring.
 *
 * <p>{@code SctpEngine.scheduleTick()} drives a single
 * {@code usrsctp_handle_timers(elapsed_ms)} call into the native
 * library. The engine also starts a daemon virtual thread on first
 * instantiation that ticks at ~10 ms cadence for the JVM's lifetime.
 *
 * <p>These tests prove the binding wired correctly: the native library
 * loads, the function symbol resolves, and one explicit tick + a
 * brief delay (which lets the daemon ticker run a few iterations)
 * completes without throwing.
 *
 * <p>Per the project's no-native-test-gating rule, the tests do NOT
 * catch {@link UnsatisfiedLinkError} — a broken libusrsctp shipment
 * must fail the build.
 */
@DisplayName("SctpEngine ticker wiring")
class SctpEngineTickerTest {

    @Test
    @DisplayName("instance() returns a non-null engine + boots the native library")
    void instanceLoads() {
        var engine = SctpEngine.instance();
        assertNotNull(engine);
    }

    @Test
    @DisplayName("scheduleTick() drives usrsctp_handle_timers without throwing")
    void scheduleTickDoesNotThrow() {
        var engine = SctpEngine.instance();
        assertDoesNotThrow(engine::scheduleTick);
    }

    @Test
    @DisplayName("scheduleTick() is idempotent and tolerates over-frequency calls")
    void scheduleTickRepeatable() {
        var engine = SctpEngine.instance();
        for (var i = 0; i < 16; i++) {
            assertDoesNotThrow(engine::scheduleTick);
        }
    }

    @Test
    @DisplayName("scheduleTick() remains healthy after a brief wait — the daemon ticker is also running")
    void engineStaysHealthyAfterDaemonTicks() throws InterruptedException {
        var engine = SctpEngine.instance();
        // Wait long enough for several daemon-ticker iterations to fire
        // (TICK_PERIOD_MILLIS = 10ms). If the daemon ticker had thrown,
        // it would have died but the engine itself stays usable — so the
        // assertion here is "explicit scheduleTick still works".
        Thread.sleep(50);
        assertDoesNotThrow(engine::scheduleTick);
    }
}
