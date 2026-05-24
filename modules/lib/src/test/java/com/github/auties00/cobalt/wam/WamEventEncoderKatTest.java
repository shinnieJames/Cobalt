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
 * Byte-identical KAT for full event-with-fields encoding sequences
 * against vectors captured from {@code WAWebWamLibProtocol}.
 *
 * @apiNote
 * Reproduces what a generated Cobalt {@code *Impl.encode} emits for a
 * given (event id, sampling weight, field list) tuple: an
 * {@code writeEventMarker} call followed by one
 * {@code writeIntField} / {@code writeStringField} / {@code writeBoolField} /
 * {@code writeFloatField} / {@code writeNull} per field, with the
 * {@code LAST} flag on the trailing entry only. Vectors live in
 * {@code fixtures/wam/wam-encoder-type-matrix.json} and pin snapshot
 * revision {@code 1039260921}.
 *
 * @implNote
 * The vector matrix covers every {@code WamType} branch (INTEGER,
 * BOOLEAN, STRING, FLOAT, ENUM-as-INT, NULL) at every numeric boundary
 * ({@code 0}, {@code 1}, {@link Byte#MAX_VALUE},
 * {@link Short#MIN_VALUE}, {@link Integer#MIN_VALUE},
 * {@link Integer#MAX_VALUE}) and at the WIDE_ID transition. The JS
 * encoder writes its weight argument verbatim while Cobalt's
 * {@link WamEventEncoder#writeEventMarker(int, int, boolean)} negates
 * internally; {@link #assertVectorAgrees(JSONObject)} passes
 * {@code -capturedWeight} to recover identical bytes.
 */
@DisplayName("WamEventEncoder KAT against live WhatsApp Web bundle")
class WamEventEncoderKatTest {
    /**
     * The snapshot revision the KAT vectors were captured against;
     * compared against the fixture header so revision drift fails
     * loudly.
     */
    private static final long PINNED_SNAPSHOT_REVISION = 1039260921L;

    /**
     * The shared output buffer size, sized for the 256-byte UTF-8
     * boundary case plus header overhead.
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
     * {@link WamEventEncoder} and asserts the produced hex matches the
     * captured bytes.
     *
     * @implNote
     * Negates {@code capturedWeight} on the way in because the JS
     * encoder writes the weight argument verbatim while Cobalt's
     * {@link WamEventEncoder#writeEventMarker(int, int, boolean)}
     * negates internally; without the negation every vector with a
     * non-zero weight would fail at the first marker byte.
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
     * @param field   the captured field descriptor with {@code id},
     *                {@code value}, and {@code type} entries
     * @param hasMore whether another field follows this one in the
     *                same event (controls the {@code LAST} flag)
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
     * {@code {kind, length, head}} summary form when the capture
     * elided a long repeated-character string.
     *
     * @param raw the captured value (a {@link String} or a
     *            {@link JSONObject} summary)
     * @return the reconstructed string
     * @throws IllegalStateException if {@code raw} is neither a string
     *                               nor a recognised summary shape
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
     * Returns a single dynamic test that fails loudly when the fixture
     * decodes to zero vectors.
     *
     * @apiNote
     * Catches the most common fixture-format regression (the
     * {@code vectors} array gets renamed, or the file ships empty)
     * before the per-vector dynamic tests run and obscure the cause.
     *
     * @return the bookkeeping dynamic test
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
