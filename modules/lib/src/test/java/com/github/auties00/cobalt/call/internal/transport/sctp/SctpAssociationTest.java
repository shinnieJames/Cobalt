package com.github.auties00.cobalt.call.internal.transport.sctp;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Smoke tests for the usrsctp FFM bindings and {@link SctpAssociation}
 * lifecycle. The tests deliberately do not catch {@link UnsatisfiedLinkError}:
 * a libusrsctp that fails to load must fail the build rather than skip green.
 */
public class SctpAssociationTest {

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
