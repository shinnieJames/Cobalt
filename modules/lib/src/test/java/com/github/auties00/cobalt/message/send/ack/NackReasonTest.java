package com.github.auties00.cobalt.message.send.ack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link NackReason}, mirroring
 * {@code WAWebCreateNackFromStanza.NackReason}.
 *
 * <p>{@code NackReason} is a closed set of constants: no behaviour to test,
 * but the test pins the exact set of well-known error codes and rejects
 * accidental collisions (e.g. a future contributor recycling a constant
 * value).
 */
@DisplayName("NackReason")
class NackReasonTest {

    @Test
    @DisplayName("known nack codes match the WA Web canonical values")
    void canonicalCodes() {
        // These values are baked into the Cobalt source as the @WhatsAppWebExport
        // names in NackReason. The test pins them so a typo in either direction
        // (Cobalt or WA Web) trips immediately. Hard-coding the literal values
        // is intentional: any drift from the WA Web canonical names should
        // require a deliberate test update.
        assertEquals(421, NackReason.STALE_GROUP_ADDRESSING_MODE.code());
        assertEquals(475, NackReason.NEW_CHAT_MESSAGES_CAPPED.code());
        assertEquals(487, NackReason.PARSING_ERROR.code());
        assertEquals(488, NackReason.UNRECOGNIZED_STANZA.code());
        assertEquals(489, NackReason.UNRECOGNIZED_STANZA_CLASS.code());
        assertEquals(490, NackReason.UNRECOGNIZED_STANZA_TYPE.code());
        assertEquals(491, NackReason.INVALID_PROTOBUF.code());
        assertEquals(493, NackReason.INVALID_HOSTED_COMPANION_STANZA.code());
        assertEquals(495, NackReason.MISSING_MESSAGE_SECRET.code());
        assertEquals(496, NackReason.SIGNAL_ERROR_OLD_COUNTER.code());
        assertEquals(499, NackReason.MESSAGE_DELETED_ON_PEER.code());
        assertEquals(500, NackReason.UNHANDLED_ERROR.code());
        assertEquals(550, NackReason.UNSUPPORTED_ADMIN_REVOKE.code());
        assertEquals(551, NackReason.UNSUPPORTED_LID_GROUP.code());
        assertEquals(552, NackReason.DB_OPERATION_FAILED.code());
    }

    @Test
    @DisplayName("every declared nack reason is distinct (no value collision)")
    void distinctValues() {
        var codes = Arrays.stream(NackReason.values())
                .map(NackReason::code)
                .toList();
        var distinct = new HashSet<>(codes);
        assertEquals(codes.size(), distinct.size(),
                "every NackReason constant must hold a unique code: " + codes);
    }

    @Test
    @DisplayName("all codes fall within the HTTP-style 4xx/5xx server-error range")
    void codesAreInServerErrorRange() {
        for (var reason : NackReason.values()) {
            var value = reason.code();
            assertTrue(value >= 400 && value < 600,
                    reason.name() + " = " + value + " is outside the 4xx/5xx range");
        }
    }
}
