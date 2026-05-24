package com.github.auties00.cobalt.call.internal.transport.sctp;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Smoke tests for the usrsctp FFM bindings and {@link SctpAssociation}
 * lifecycle. The tests fail loudly if libusrsctp is not loadable on
 * the running platform — silently skipping would let a broken native
 * bundle ship green.
 */
public class SctpAssociationTest {

    /**
     * Constructs and tears down an {@link SctpAssociation} —
     * verifies the FFM linker resolves all the expected symbols, the
     * receive-callback upcall stub allocates, and
     * {@code usrsctp_socket} returns non-NULL.
     */
    @Test
    public void createAndCloseRoundTrip() {
        var outbound = new AtomicInteger();
        try (var assoc = new SctpAssociation(
                packet -> outbound.incrementAndGet(),
                msg -> { /* no inbound expected */ })) {
            assoc.bind(5000);
        }
    }
}
