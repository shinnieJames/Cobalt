package com.github.auties00.cobalt.wam;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.wam.binary.WamEventEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static com.github.auties00.cobalt.wam.binary.WamTags.FIELD;
import static com.github.auties00.cobalt.wam.binary.WamTags.LAST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Byte-identical agreement tests for full event-with-fields encoding
 * sequences against vectors captured from the live WhatsApp Web
 * bundle's {@code WAWebWamLibProtocol} module.
 *
 * <p>Each vector reproduces what a Cobalt {@code *Impl.encode} emits
 * for a given event id, sampling weight, and ordered field list:
 *
 * <ol>
 *   <li>{@link WamEventEncoder#writeEventMarker(int, int, boolean)},</li>
 *   <li>followed by one
 *       {@link WamEventEncoder#writeIntField(int, long, boolean)} /
 *       {@link WamEventEncoder#writeStringField(int, String, boolean)} /
 *       {@link WamEventEncoder#writeBoolField(int, boolean, boolean)} /
 *       {@link WamEventEncoder#writeFloatField(int, double, boolean)} /
 *       {@link WamEventEncoder#writeNull(int, int)} call per field, with
 *       the {@code LAST} flag applied to the trailing entry.</li>
 * </ol>
 *
 * <p>The vector matrix covers every {@code WamType} (INTEGER, BOOLEAN,
 * STRING, FLOAT, ENUM-as-INT, NULL) and every numeric boundary
 * ({@code 0}, {@code 1}, {@code Byte.MAX_VALUE}, {@code Short.MIN_VALUE},
 * {@code Integer.MIN_VALUE / MAX_VALUE}), plus WIDE_ID interleaving
 * and weight permutations.
 *
 * <p>Vectors live in {@code fixtures/wam/wam-encoder-type-matrix.json};
 * see {@code tools/web/wam-fixtures/README.md} for the re-capture
 * procedure.
 */
@DisplayName("WamEventEncoder KAT against live WhatsApp Web bundle")
class WamEventEncoderKatTest {
    /**
     * Snapshot revision the vectors were captured against.
     */
    private static final long PINNED_SNAPSHOT_REVISION = 1039260921L;

    /**
     * Output buffer size, chosen with comfortable headroom for the
     * 256-byte UTF-8 boundary case plus header overhead.
     */
    private static final int MAX_BUFFER = 4_096;

    /**
     * Returns one dynamic test per captured vector.
     *
     * @return the test factory stream
     */
    @TestFactory
    List<DynamicTest> encoderBytesAgreeWithLiveBundle() {
        var fixture = WamFixtures.loadOracle("wam-encoder-type-matrix");
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
     * Builds the event-with-fields byte sequence through Cobalt's
     * {@link WamEventEncoder} and asserts the produced hex matches
     * the captured bytes.
     *
     * @param vector the captured vector
     */
    private static void assertVectorAgrees(JSONObject vector) {
        var eventId = vector.getIntValue("eventId");
        var capturedWeight = vector.getIntValue("weight");
        var fields = vector.getJSONArray("fields");
        var expectedHex = vector.getString("bytes");
        var expectedLength = vector.getIntValue("byteLength");

        var buffer = new byte[MAX_BUFFER];
        var encoder = WamEventEncoder.of(buffer);
        var hasFields = fields != null && !fields.isEmpty();
        // The JS encoder writes the weight argument directly; Cobalt's
        // writeEventMarker negates its weight argument internally to
        // match the JS sign convention, so we pass -capturedWeight.
        encoder.writeEventMarker(eventId, -capturedWeight, hasFields);

        if (hasFields) {
            var fieldCount = fields.size();
            for (var i = 0; i < fieldCount; i++) {
                var field = fields.getJSONObject(i);
                var hasMore = i < fieldCount - 1;
                emitField(encoder, field, hasMore);
            }
        }

        var written = encoder.written();
        assertEquals(expectedLength, written, () -> "byteLength mismatch for " + vector.getString("name"));
        var actualHex = HexFormat.of().formatHex(buffer, 0, written);
        assertEquals(expectedHex, actualHex, () -> "bytes mismatch for " + vector.getString("name"));
    }

    /**
     * Emits a single field entry into the encoder, dispatching to the
     * matching primitive based on the captured field {@code type}.
     *
     * @param encoder the destination encoder
     * @param field   the captured field descriptor
     *                {@code {id, value, type}}
     * @param hasMore whether more fields follow in this event
     */
    private static void emitField(WamEventEncoder encoder, JSONObject field, boolean hasMore) {
        var id = field.getIntValue("id");
        var type = field.getString("type");
        var rawValue = field.get("value");
        switch (type) {
            case "null" -> encoder.writeNull(id, FIELD | (hasMore ? 0 : LAST));
            case "int", "bool", "enum" -> encoder.writeIntField(id, ((Number) rawValue).longValue(), hasMore);
            case "float" -> encoder.writeFloatField(id, ((Number) rawValue).doubleValue(), hasMore);
            case "str" -> encoder.writeStringField(id, extractString(rawValue), hasMore);
            default -> throw new IllegalStateException("unsupported field type: " + type);
        }
    }

    /**
     * Extracts the field's string value, expanding the
     * {@code {kind, length, head}} summary form when present.
     *
     * @param raw the captured value (a {@link String} or a
     *            {@link JSONObject} summary)
     * @return the reconstructed string
     */
    private static String extractString(Object raw) {
        if (raw instanceof String s) {
            return s;
        }
        if (raw instanceof JSONObject obj && "str".equals(obj.getString("kind"))) {
            var head = obj.getString("head");
            if (head == null || head.isEmpty()) {
                throw new IllegalStateException("summarised string has no head: " + obj);
            }
            return String.valueOf(head.charAt(0)).repeat(obj.getIntValue("length"));
        }
        throw new IllegalStateException("unsupported string value: " + raw);
    }

    /**
     * Returns the count of captured vectors after the fixture loader
     * filters out malformed entries. Failing fast here surfaces
     * fixture-format regressions before the per-vector dynamic tests
     * run.
     *
     * @return the vector count for the matrix
     */
    @TestFactory
    DynamicTest vectorCountIsNonZero() {
        return dynamicTest("matrix vectors are present", () -> {
            var fixture = WamFixtures.loadOracle("wam-encoder-type-matrix");
            var vectors = fixture.getJSONArray("vectors");
            if (vectors == null || vectors.isEmpty()) {
                throw new AssertionError("wam-encoder-type-matrix.json has no 'vectors' array");
            }
        });
    }
}
