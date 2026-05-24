package com.github.auties00.cobalt.call.internal.transport;

import com.github.auties00.cobalt.call.internal.transport.dtls.DtlsCertificate;
import com.github.auties00.cobalt.call.internal.transport.ice.IceCredentials;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 lifecycle tests for {@link ActiveCallTransport}. Verifies
 * IDLE → STARTED → CLOSED state transitions, idempotent close, the
 * one-shot start guard, and that {@link ActiveCallTransport#dataChannel()}
 * stays empty until Phase 5 wires the DCEP exchange.
 */
@DisplayName("ActiveCallTransport — Phase 1 lifecycle")
class ActiveCallTransportTest {

    /**
     * Constructs synthetic-but-valid {@link ActiveCallTransport.StartParameters}
     * — enough to exercise the lifecycle, without touching real
     * networking.
     */
    private static ActiveCallTransport.StartParameters synthetic() {
        var creds = new IceCredentials(
                "local-ufrag-test",
                "local-pwd-test-bytes".getBytes(StandardCharsets.UTF_8),
                "remote-ufrag-test",
                "remote-pwd-test-bytes".getBytes(StandardCharsets.UTF_8));
        var cert = DtlsCertificate.generate();
        // Phase-2-aware: pass a null OfferTransportSpec (synthetic
        // tests don't carry a real WA-relay payload).
        return new ActiveCallTransport.StartParameters(creds, cert, new byte[32], true, null);
    }

    @Test
    @DisplayName("fresh transport starts in IDLE")
    void freshIsIdle() {
        var t = new ActiveCallTransport();
        assertEquals(ActiveCallTransport.State.IDLE, t.state());
        assertTrue(t.dataChannel().isEmpty(), "no channel until Phase 5");
        assertTrue(t.iceAgent().isEmpty(), "no agent until start()");
        assertTrue(t.startParameters().isEmpty());
    }

    @Test
    @DisplayName("start(params) transitions IDLE → STARTED and constructs the underlying objects")
    void startTransitionsToStarted() {
        var t = new ActiveCallTransport();
        var params = synthetic();
        t.start(params);
        assertEquals(ActiveCallTransport.State.STARTED, t.state());
        assertEquals(params, t.startParameters().orElseThrow());
        assertTrue(t.iceAgent().isPresent(), "ICE agent must be constructed by start()");
        // DataChannel is still empty — opened only after the (future)
        // DCEP exchange in Phase 5.
        assertTrue(t.dataChannel().isEmpty());
    }

    @Test
    @DisplayName("close() transitions to CLOSED and is idempotent")
    void closeIdempotent() {
        var t = new ActiveCallTransport();
        t.start(synthetic());
        t.close();
        assertEquals(ActiveCallTransport.State.CLOSED, t.state());
        // Idempotent — second + third close are no-ops, no throw.
        t.close();
        t.close();
        assertEquals(ActiveCallTransport.State.CLOSED, t.state());
    }

    @Test
    @DisplayName("close() from IDLE is allowed and final")
    void closeFromIdleIsFinal() {
        var t = new ActiveCallTransport();
        t.close();
        assertEquals(ActiveCallTransport.State.CLOSED, t.state());
        // After CLOSED, start() must throw — the one-shot transition
        // from IDLE has already happened.
        assertThrows(IllegalStateException.class, () -> t.start(synthetic()));
    }

    @Test
    @DisplayName("start() twice throws IllegalStateException")
    void doubleStartThrows() {
        var t = new ActiveCallTransport();
        t.start(synthetic());
        assertThrows(IllegalStateException.class, () -> t.start(synthetic()));
    }
}
