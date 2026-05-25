package com.github.auties00.cobalt.call.internal.signaling;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link CallIdGenerator} produces call identifiers in the same shape WhatsApp Web
 * emits: 32 characters total, the fixed prefix {@code "00"}, then 30 uppercase hex characters, with
 * no collisions across calls. The expected shape is anchored to identifiers captured from the live
 * primary session, which double as a tripwire if WhatsApp Web ever changes the format.
 */
public class CallIdGeneratorParityTest {

    private static final Pattern WA_WEB_CALL_ID = Pattern.compile("^00[0-9A-F]{30}$");

    // Captured from the live primary session at snapshot 1038697023-47d2a47020a5.
    private static final String[] CAPTURED_LIVE_IDS = {
            "00B8E865663D28CFFF9469A77156A381",
            "00949C98540619286C17161ABCA80113"
    };

    @Test
    public void capturedIdsMatchFormat() {
        for (var id : CAPTURED_LIVE_IDS) {
            assertEquals(32, id.length(), () -> "captured id wrong length: " + id);
            assertTrue(WA_WEB_CALL_ID.matcher(id).matches(),
                    () -> "captured id does not match WA Web format: " + id);
        }
    }

    @Test
    public void generatedIdMatchesFormat() {
        for (var i = 0; i < 64; i++) {
            var id = CallIdGenerator.generate();
            assertEquals(32, id.length(), () -> "generated id wrong length: " + id);
            assertTrue(WA_WEB_CALL_ID.matcher(id).matches(),
                    () -> "generated id does not match WA Web format: " + id);
            assertTrue(id.startsWith(CallIdGenerator.PREFIX),
                    () -> "generated id missing fixed prefix: " + id);
        }
    }

    @Test
    public void generatedIdsDoNotCollide() {
        var seen = new HashSet<String>();
        for (var i = 0; i < 1024; i++) {
            var id = CallIdGenerator.generate();
            assertTrue(seen.add(id), () -> "collision: " + id + " already seen");
        }
        assertNotEquals(0, seen.size());
    }
}
