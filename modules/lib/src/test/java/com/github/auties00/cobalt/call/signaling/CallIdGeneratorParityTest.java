package com.github.auties00.cobalt.call.signaling;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parity test for {@link CallIdGenerator}.
 *
 * <p>Asserts that Cobalt produces call identifiers in the same shape WA
 * Web's {@code WAWebVoipStartCall.me()} produces. The shape, taken from
 * live captures (e.g. {@code 00B8E865663D28CFFF9469A77156A381},
 * {@code 00949C98540619286C17161ABCA80113}), is:
 *
 * <ul>
 *   <li>32 characters total</li>
 *   <li>First two characters are always {@code "00"}</li>
 *   <li>Remaining 30 characters are uppercase hex {@code [0-9A-F]}</li>
 *   <li>Generated identifiers must not collide across calls</li>
 * </ul>
 */
public class CallIdGeneratorParityTest {

    /**
     * Pattern that every captured call id must match.
     */
    private static final Pattern WA_WEB_CALL_ID = Pattern.compile("^00[0-9A-F]{30}$");

    /**
     * Sample call IDs captured from the live primary session at
     * snapshot {@code 1038697023-47d2a47020a5}, used as a tripwire — if
     * WA Web ever changes the format we want to know.
     */
    private static final String[] CAPTURED_LIVE_IDS = {
            "00B8E865663D28CFFF9469A77156A381",
            "00949C98540619286C17161ABCA80113"
    };

    /**
     * Captured live identifiers all match the format pattern.
     */
    @Test
    public void capturedIdsMatchFormat() {
        for (var id : CAPTURED_LIVE_IDS) {
            assertEquals(32, id.length(), () -> "captured id wrong length: " + id);
            assertTrue(WA_WEB_CALL_ID.matcher(id).matches(),
                    () -> "captured id does not match WA Web format: " + id);
        }
    }

    /**
     * Cobalt-generated identifiers also match the format pattern.
     */
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

    /**
     * Generator should produce distinct IDs across calls (CSPRNG-backed).
     */
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
