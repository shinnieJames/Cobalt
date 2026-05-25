package com.github.auties00.cobalt.call.internal.transport.sctp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke tests for {@link SctpEngine}'s timer-tick wiring. The engine drives a
 * single {@code usrsctp_handle_timers} call per {@code scheduleTick}, and also
 * runs a daemon virtual thread that ticks at roughly 10 ms cadence for the
 * JVM's lifetime. The tests deliberately do not catch {@link UnsatisfiedLinkError}:
 * a libusrsctp that fails to load must fail the build rather than skip green.
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
        // 50ms covers several daemon-ticker iterations at the ~10ms cadence.
        Thread.sleep(50);
        assertDoesNotThrow(engine::scheduleTick);
    }
}
