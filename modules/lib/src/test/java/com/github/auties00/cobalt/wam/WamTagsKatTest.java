package com.github.auties00.cobalt.wam;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.wam.binary.WamEventEncoder;
import com.github.auties00.cobalt.wam.binary.WamTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static com.github.auties00.cobalt.wam.binary.WamTags.EVENT;
import static com.github.auties00.cobalt.wam.binary.WamTags.FIELD;
import static com.github.auties00.cobalt.wam.binary.WamTags.GLOBAL;
import static com.github.auties00.cobalt.wam.binary.WamTags.LAST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Byte-identical KAT for {@link WamEventEncoder} against vectors
 * captured from {@code WAWebWamLibProtocol}'s three write helpers.
 *
 * @apiNote
 * Pins the wire output of a single call to one of:
 * <ul>
 *   <li>{@code writeGlobalAttribute(buf, fieldId, value)} (role bits
 *       {@code 0});</li>
 *   <li>{@code writeEvent(buf, eventId, weight, hasFields)} (role
 *       bits {@code 1}; weight is written as {@code -weight} per the
 *       JS contract);</li>
 *   <li>{@code writeField(buf, fieldId, value, hasFollowing)} (role
 *       bits {@code 2}).</li>
 * </ul>
 * Agreement here is the strongest possible validation of the WAM tag
 * byte protocol: any divergence in role bits, the {@code LAST} flag,
 * the {@code WIDE_ID} flag, or the value-type mask surfaces as a hex
 * mismatch. Vectors live in {@code fixtures/wam/wam-tags-roundtrip.json}
 * and pin snapshot revision {@code 1039260921}.
 *
 * @implNote
 * Re-capture via {@code mcp__whatsapp__web_live_debug_eval_to_file}
 * if the WA Web protocol changes; the recapture procedure lives in
 * {@code src/test/resources/fixtures/wam/README.md}.
 */
@DisplayName("WamTags KAT against live WhatsApp Web bundle")
class WamTagsKatTest {
    /**
     * The classpath-relative name of the captured-vectors fixture
     * (without its {@code .expected.json} oracle suffix).
     */
    private static final String FIXTURE = "wam-tags-roundtrip.json";

    /**
     * The snapshot revision the KAT vectors were captured against;
     * compared against the fixture header so revision drift fails
     * loudly.
     */
    private static final long PINNED_SNAPSHOT_REVISION = 1039260921L;

    /**
     * The shared output buffer size, sized to comfortably hold the
     * 65 536-byte UTF-8 boundary case plus header overhead.
     */
    private static final int MAX_BUFFER = 70_000;

    /**
     * Returns one dynamic test per captured vector, named after the
     * vector's {@code name} so failures point at the row.
     *
     * @return the test factory stream
     */
    @TestFactory
    List<DynamicTest> tagBytesAgreeWithLiveBundle() {
        var fixture = WamFixtures.loadOracle("wam-tags-roundtrip");
        WamFixtures.requireSnapshotRevision(fixture, PINNED_SNAPSHOT_REVISION);
        var vectors = fixture.getJSONArray("vectors");
        var tests = new ArrayList<DynamicTest>(vectors.size());
        for (var entry : vectors) {
            var vector = (JSONObject) entry;
            tests.add(dynamicTest(vector.getString("name"), () -> assertVectorAgrees(vector)));
        }
        return tests;
    }

    /**
     * Encodes the given vector through Cobalt's {@link WamEventEncoder}
     * and asserts the produced hex matches the captured bytes.
     *
     * @param vector the captured vector
     */
    private static void assertVectorAgrees(JSONObject vector) {
        var role = vector.getString("role");
        var args = vector.getJSONArray("args");
        var expectedHex = vector.getString("bytes");
        var expectedLength = vector.getIntValue("byteLength");

        var buffer = new byte[MAX_BUFFER];
        var encoder = WamEventEncoder.of(buffer);

        switch (role) {
            case "global" -> encodeGlobal(encoder, args);
            case "event" -> encodeEvent(encoder, args);
            case "field" -> encodeField(encoder, args);
            default -> throw new IllegalStateException("unknown role: " + role);
        }

        var written = encoder.written();
        assertEquals(expectedLength, written, () -> "byteLength mismatch for " + vector.getString("name"));
        var actualHex = HexFormat.of().formatHex(buffer, 0, written);
        assertEquals(expectedHex, actualHex, () -> "bytes mismatch for " + vector.getString("name"));
    }

    /**
     * Dispatches a captured {@code writeGlobalAttribute} call to the
     * matching {@code encoder.writeXxx(fieldId, GLOBAL, value)}
     * overload based on the runtime value type.
     *
     * @param encoder the destination encoder
     * @param args    the {@code [fieldId, value]} arguments
     * @throws IllegalStateException if {@code value} is of an
     *                               unsupported runtime type
     */
    private static void encodeGlobal(WamEventEncoder encoder, JSONArray args) {
        var fieldId = args.getIntValue(0);
        var raw = args.get(1);
        if (raw == null) {
            encoder.writeNull(fieldId, GLOBAL);
        } else if (raw instanceof Number n && isWhole(n)) {
            encoder.writeInt(fieldId, GLOBAL, n.longValue());
        } else if (raw instanceof Number n) {
            encoder.writeFloat(fieldId, GLOBAL, n.doubleValue());
        } else if (raw instanceof JSONObject obj) {
            encoder.writeString(fieldId, GLOBAL, reconstructString(obj));
        } else if (raw instanceof String s) {
            encoder.writeString(fieldId, GLOBAL, s);
        } else {
            throw new IllegalStateException("unsupported global value: " + raw.getClass() + " = " + raw);
        }
    }

    /**
     * Dispatches a captured {@code writeEvent} call to
     * {@link WamEventEncoder#writeEventMarker(int, int, boolean)}.
     *
     * @implNote
     * The captured JS arguments are
     * {@code [eventId, weight, hasFields]} where {@code weight} is
     * already the value written into the marker payload (the JS
     * writer applies {@code -weight} at the call site). Cobalt's
     * {@link WamEventEncoder#writeEventMarker(int, int, boolean)}
     * negates internally, so this method passes
     * {@code -capturedWeight} to recover identical bytes.
     *
     * @param encoder the destination encoder
     * @param args    the {@code [eventId, weight, hasFields]}
     *                arguments
     */
    private static void encodeEvent(WamEventEncoder encoder, JSONArray args) {
        var eventId = args.getIntValue(0);
        var capturedWeight = args.getIntValue(1);
        var hasFields = args.getBooleanValue(2);
        encoder.writeEventMarker(eventId, -capturedWeight, hasFields);
    }

    /**
     * Dispatches a captured {@code writeField} call to the matching
     * {@code encoder.writeXxxField(...)} or
     * {@code encoder.writeXxx(fieldId, FIELD | LAST, value)}
     * primitive based on the runtime value type.
     *
     * @param encoder the destination encoder
     * @param args    the {@code [fieldId, value, hasFollowing]}
     *                arguments
     * @throws IllegalStateException if {@code value} is of an
     *                               unsupported runtime type
     */
    private static void encodeField(WamEventEncoder encoder, JSONArray args) {
        var fieldId = args.getIntValue(0);
        var raw = args.get(1);
        var hasFollowing = args.getBooleanValue(2);
        if (raw == null) {
            encoder.writeNull(fieldId, FIELD | (hasFollowing ? 0 : LAST));
        } else if (raw instanceof Number n && isWhole(n)) {
            encoder.writeIntField(fieldId, n.longValue(), hasFollowing);
        } else if (raw instanceof Number n) {
            encoder.writeFloatField(fieldId, n.doubleValue(), hasFollowing);
        } else if (raw instanceof JSONObject obj) {
            encoder.writeStringField(fieldId, reconstructString(obj), hasFollowing);
        } else if (raw instanceof String s) {
            encoder.writeStringField(fieldId, s, hasFollowing);
        } else {
            throw new IllegalStateException("unsupported field value: " + raw.getClass() + " = " + raw);
        }
    }

    /**
     * Reconstructs a string from its summarised
     * {@code {kind, length, head}} form.
     *
     * @apiNote
     * The fixture summarises long repeated-character strings to keep
     * the on-disk corpus small; every summarised string in the
     * corpus is a repeat of its first character, so the head's first
     * byte is sufficient to regenerate the original.
     *
     * @param summary the summarised string descriptor
     * @return the reconstructed string
     * @throws IllegalStateException if {@code summary} is not a
     *                               {@code str}-kind object or its
     *                               {@code head} is missing
     */
    private static String reconstructString(JSONObject summary) {
        if (!"str".equals(summary.getString("kind"))) {
            throw new IllegalStateException("unsupported arg shape: " + summary);
        }
        var length = summary.getIntValue("length");
        var head = summary.getString("head");
        if (head == null || head.isEmpty()) {
            throw new IllegalStateException("summarised string has no head: " + summary);
        }
        return String.valueOf(head.charAt(0)).repeat(length);
    }

    /**
     * Returns whether the given number can be represented exactly as
     * a {@code long} without truncation.
     *
     * @param n the captured numeric value
     * @return {@code true} if {@code n} has no fractional part and
     *         fits in a {@code long}
     */
    private static boolean isWhole(Number n) {
        if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte) {
            return true;
        }
        var d = n.doubleValue();
        return !Double.isNaN(d) && !Double.isInfinite(d) && d == Math.floor(d) && d == (double) (long) d;
    }

    /**
     * Smoke assertions that {@link WamTags#EVENT}, {@link WamTags#FIELD},
     * {@link WamTags#GLOBAL}, and {@link WamTags#LAST} hold the
     * expected wire bit patterns.
     *
     * @apiNote
     * Guards against an accidental renumbering of the role / LAST
     * constants that would silently break every event Cobalt emits;
     * the per-vector KAT above would catch this too, but this single
     * test fails faster and isolates the cause.
     *
     * @return the bit-pattern dynamic tests
     */
    @TestFactory
    List<DynamicTest> tagConstantsHaveExpectedWireBits() {
        return List.of(
                dynamicTest("GLOBAL is 0x00", () -> assertEquals(0x00, GLOBAL)),
                dynamicTest("EVENT is 0x01", () -> assertEquals(0x01, EVENT)),
                dynamicTest("FIELD is 0x02", () -> assertEquals(0x02, FIELD)),
                dynamicTest("LAST is 0x04", () -> assertEquals(0x04, LAST))
        );
    }
}
