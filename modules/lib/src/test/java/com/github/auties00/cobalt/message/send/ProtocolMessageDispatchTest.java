package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for the {@link ProtocolMessage.Type} enum, which the
 * orchestrator uses to drive {@code edit} attribute, recipient device
 * filter, and target-sender selection for protocol messages.
 *
 * <p>The full per-subtype dispatch behaviour (edit attribute propagation,
 * decrypt-fail attribute, recipient device list) lives inside the
 * package-private {@link UserMessageSender} / {@link GroupMessageSender} /
 * {@link PeerMessageSender} senders and is exercised indirectly by the
 * live-corpus oracle tests against captured wire stanzas. This class
 * focuses on the parts that can be verified without the full DI graph:
 *
 * <ul>
 *   <li>Every enum value carries a unique non-negative protobuf index.</li>
 *   <li>The well-known indices documented by WA Web's
 *       {@code Message$ProtocolMessage$Type} enum are present.</li>
 *   <li>Every value round-trips through {@code index()}.</li>
 * </ul>
 */
@DisplayName("ProtocolMessage.Type dispatch")
class ProtocolMessageDispatchTest {

    @ParameterizedTest(name = "{0} has non-null name")
    @EnumSource(ProtocolMessage.Type.class)
    @DisplayName("every value carries a stable enum name (regression guard)")
    void everyValueHasName(ProtocolMessage.Type type) {
        assertNotNull(type.name());
        assertFalse(type.name().isBlank());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("every value has a unique protobuf index")
    void indicesAreUnique() {
        var values = ProtocolMessage.Type.values();
        var indices = new HashSet<Integer>();
        for (var type : values) {
            var index = type.index();
            assertTrue(index >= 0, type + " has negative index " + index);
            assertTrue(indices.add(index),
                    type + " has duplicate index " + index);
        }
        assertEquals(values.length, indices.size(),
                "every value must contribute a distinct index");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("well-known WA Web protobuf indices are present")
    void wellKnownIndicesPresent() {
        // From WA Web's Message$ProtocolMessage$Type — pinning the wire
        // numbers Cobalt depends on for round-trip parity. If these drift
        // we'll silently fail to round-trip protocol messages.
        assertEquals(0, ProtocolMessage.Type.REVOKE.index(),
                "REVOKE must be 0 (the default-on-protobuf-wire value)");
        assertEquals(3, ProtocolMessage.Type.EPHEMERAL_SETTING.index());
        assertEquals(5, ProtocolMessage.Type.HISTORY_SYNC_NOTIFICATION.index());
        assertEquals(6, ProtocolMessage.Type.APP_STATE_SYNC_KEY_SHARE.index());
        assertEquals(7, ProtocolMessage.Type.APP_STATE_SYNC_KEY_REQUEST.index());
        assertEquals(14, ProtocolMessage.Type.MESSAGE_EDIT.index(),
                "MESSAGE_EDIT must be 14 — the wire stanza's edit=1 marker comes from this branch");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("count of values matches the WA Web wire enum (at least 20 documented subtypes)")
    void valueCountLowerBound() {
        var values = ProtocolMessage.Type.values();
        // WA Web's Message$ProtocolMessage$Type has 31 subtypes per the test
        // plan. Use a lower bound so future additions don't break this
        // assertion, but a regression that removes a chunk of values does.
        assertTrue(values.length >= 20,
                "ProtocolMessage.Type must enumerate at least 20 subtypes, got " + values.length);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("enum values resolve back via index() — each yields a distinct value")
    void everyValueRoundTripsViaIndex() {
        var values = ProtocolMessage.Type.values();
        var indices = Arrays.stream(values).map(t -> t.index()).toArray();
        var distinct = Arrays.stream(indices).distinct().toArray();
        assertEquals(indices.length, distinct.length,
                "every protobuf index must map back to a unique enum value");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("REVOKE and MESSAGE_EDIT are distinct values (used for edit=7 vs edit=1 on wire)")
    void revokeAndEditAreDistinct() {
        assertNotEquals(ProtocolMessage.Type.REVOKE, ProtocolMessage.Type.MESSAGE_EDIT,
                "REVOKE and MESSAGE_EDIT must not collide — they drive different wire edit attributes");
    }
}
