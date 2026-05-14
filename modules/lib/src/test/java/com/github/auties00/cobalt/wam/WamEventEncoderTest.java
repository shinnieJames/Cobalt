package com.github.auties00.cobalt.wam;

import com.github.auties00.cobalt.wam.binary.WamEventDecoder;
import com.github.auties00.cobalt.wam.binary.WamEventEncoder;
import com.github.auties00.cobalt.wam.binary.WamEventSizes;
import com.github.auties00.cobalt.wam.binary.WamTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Behavioural tests for {@link WamEventEncoder} / {@link WamEventDecoder}
 * over the full primitive type matrix.
 *
 * <p>Each test exercises one of three properties:
 *
 * <ol>
 *   <li><b>Round-trip parity</b> — encode a value, decode it back, and
 *       assert the result is bit-equal to the input. Covers every
 *       {@code WamType} branch (INTEGER / BOOLEAN / STRING / FLOAT)
 *       and every numeric boundary
 *       ({@code 0}, {@code 1}, {@code Byte.MIN/MAX_VALUE},
 *       {@code Short.MIN/MAX_VALUE},
 *       {@code Integer.MIN/MAX_VALUE},
 *       {@code Float.NaN}, {@code Float.POSITIVE_INFINITY}).</li>
 *   <li><b>Sink parity</b> — the same input encoded via the
 *       byte-array sink and the {@link java.io.OutputStream} sink
 *       must produce byte-identical output and the same
 *       {@code written()} count.</li>
 *   <li><b>{@link WamEventSizes} parity</b> — the byte count returned
 *       by {@code WamEventSizes.xxxSize(...)} must equal the bytes
 *       actually emitted, and pre-allocating exactly that many bytes
 *       must succeed without overflow.</li>
 * </ol>
 *
 * <p>The KAT counterpart against WA Web is
 * {@link WamEventEncoderKatTest}; this class only asserts internal
 * consistency.
 */
@DisplayName("WamEventEncoder / WamEventDecoder behavioural")
class WamEventEncoderTest {
    /**
     * Headroom over the longest encoded entry used by these tests
     * — the 70 000-byte UTF-8 sink-parity case is the worst case.
     */
    private static final int BUFFER = 80_000;

    /**
     * Round-trip tests covering every primitive value type.
     */
    @Nested
    @DisplayName("round-trip encode → decode")
    class RoundTrip {
        /**
         * Returns one dynamic test per integer boundary.
         *
         * @return the test factory stream
         */
        @TestFactory
        List<DynamicTest> intBoundaries() {
            var values = List.of(
                    0L, 1L,
                    (long) Byte.MIN_VALUE, (long) Byte.MAX_VALUE,
                    (long) Short.MIN_VALUE, (long) Short.MAX_VALUE,
                    (long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE,
                    -1L, 42L, -42L);
            var tests = new ArrayList<DynamicTest>();
            for (var v : values) {
                tests.add(dynamicTest("int " + v, () -> assertIntRoundTrip(7, v, false)));
                tests.add(dynamicTest("int " + v + " (LAST)", () -> assertIntRoundTrip(7, v, true)));
                tests.add(dynamicTest("int " + v + " (WIDE_ID)", () -> assertIntRoundTrip(300, v, false)));
            }
            return tests;
        }

        /**
         * Returns one dynamic test per string boundary.
         *
         * @return the test factory stream
         */
        @TestFactory
        List<DynamicTest> stringBoundaries() {
            var values = List.of(
                    "",
                    "a",
                    "hello",
                    "héllo " + new String(Character.toChars(128512)),
                    "a".repeat(255),
                    "a".repeat(256),
                    "a".repeat(65535),
                    "a".repeat(65536));
            var tests = new ArrayList<DynamicTest>();
            for (var s : values) {
                var label = s.length() <= 32 ? "\"" + s + "\"" : "<str:" + s.length() + ">";
                tests.add(dynamicTest("str " + label,
                        () -> assertStringRoundTrip(9, s, false)));
            }
            return tests;
        }

        /**
         * Tests float round-trip including special IEEE-754 values.
         *
         * @return the test factory stream
         */
        @TestFactory
        List<DynamicTest> floatValues() {
            var values = List.of(
                    0.0, -0.0, 1.0, -1.0, 3.14159265358979, -2.5,
                    1e15, 1.5e-10, 0.5,
                    Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                    Double.MIN_VALUE, Double.MAX_VALUE);
            var tests = new ArrayList<DynamicTest>();
            for (var d : values) {
                tests.add(dynamicTest("float " + d, () -> assertFloatRoundTrip(11, d)));
            }
            return tests;
        }

        /**
         * Tests bool round-trip via the {@code writeBoolField}
         * convenience method.
         */
        @Test
        @DisplayName("bool true/false round-trip")
        void boolRoundTrip() {
            for (var v : new boolean[]{true, false}) {
                var buffer = new byte[16];
                var encoder = WamEventEncoder.of(buffer);
                encoder.writeBoolField(5, v, false);
                var decoder = WamEventDecoder.of(buffer, 0, encoder.written());
                var header = decoder.readHeader();
                assertEquals(5, WamEventDecoder.fieldIdOf(header));
                var decoded = decoder.readInt(header) != 0L;
                assertEquals(v, decoded);
            }
        }

        /**
         * Tests null entry round-trip for the GLOBAL role — encoder
         * writes the tag, decoder reads back with valueType =
         * VALUE_NULL.
         *
         * <p>Per WAWebWamLibProtocol, only the GLOBAL role emits
         * bytes for a null value (the dirty-write null-transition
         * path); FIELD and EVENT roles with a null value are a
         * no-op, covered separately by
         * {@link WamEventDecoderTest#writeNullNonGlobalIsNoop()}.
         */
        @Test
        @DisplayName("null GLOBAL entry: writeNull → tag with VALUE_NULL bits, no payload")
        void nullRoundTrip() {
            var buffer = new byte[16];
            var encoder = WamEventEncoder.of(buffer);
            encoder.writeNull(5, WamTags.GLOBAL);
            assertEquals(2, encoder.written(),
                    "null GLOBAL entry encodes to exactly 2 bytes: tag + fieldId");
            var decoder = WamEventDecoder.of(buffer, 0, encoder.written());
            var header = decoder.readHeader();
            assertEquals(5, WamEventDecoder.fieldIdOf(header));
            assertEquals(0x00, WamEventDecoder.valueTypeOf(header) & 0xF0,
                    "VALUE_NULL is the 0x00 nibble");
        }

        /**
         * Tests event-marker round-trip: writeEventMarker then
         * readHeader + readInt.
         */
        @Test
        @DisplayName("event marker: writeEventMarker → decoded eventId + negated weight")
        void eventMarkerRoundTrip() {
            var buffer = new byte[16];
            var encoder = WamEventEncoder.of(buffer);
            encoder.writeEventMarker(2862, 1, false);
            var decoder = WamEventDecoder.of(buffer, 0, encoder.written());
            var header = decoder.readHeader();
            assertEquals(2862, WamEventDecoder.fieldIdOf(header));
            assertEquals(-1L, decoder.readInt(header),
                    "event marker payload is the negated weight per JS convention");
        }
    }

    /**
     * Sink-parity tests: byte-array vs stream sinks must produce
     * identical bytes and the same {@code written()} count.
     */
    @Nested
    @DisplayName("ByteArray vs Stream sink parity")
    class SinkParity {
        /**
         * Verifies parity across a representative type matrix.
         */
        @Test
        @DisplayName("identical inputs yield identical bytes on both sinks")
        void parityAcrossTypes() {
            assertSinkParity(encoder -> encoder.writeNull(5, WamTags.GLOBAL));
            assertSinkParity(encoder -> encoder.writeInt(5, WamTags.GLOBAL, 0L));
            assertSinkParity(encoder -> encoder.writeInt(5, WamTags.GLOBAL, 1L));
            assertSinkParity(encoder -> encoder.writeInt(5, WamTags.GLOBAL, 127L));
            assertSinkParity(encoder -> encoder.writeInt(5, WamTags.GLOBAL, 200L));
            assertSinkParity(encoder -> encoder.writeInt(5, WamTags.GLOBAL, 70_000L));
            assertSinkParity(encoder -> encoder.writeInt(5, WamTags.GLOBAL, Integer.MAX_VALUE));
            assertSinkParity(encoder -> encoder.writeFloat(5, WamTags.GLOBAL, 3.14));
            assertSinkParity(encoder -> encoder.writeString(5, WamTags.GLOBAL, "hi"));
            assertSinkParity(encoder -> encoder.writeString(5, WamTags.GLOBAL, "a".repeat(300)));
            assertSinkParity(encoder -> encoder.writeString(5, WamTags.GLOBAL, "a".repeat(70_000)));
            assertSinkParity(encoder -> encoder.writeEventMarker(2862, 1, true));
            // Wide-id paths
            assertSinkParity(encoder -> encoder.writeInt(300, WamTags.FIELD, 7L));
            assertSinkParity(encoder -> encoder.writeString(300, WamTags.FIELD, "wide"));
        }
    }

    /**
     * {@link WamEventSizes} parity: every {@code xxxSize(...)} call
     * must equal the bytes actually written.
     */
    @Nested
    @DisplayName("WamEventSizes equals actual bytes written")
    class SizesParity {
        /**
         * Verifies int field-size parity.
         */
        @Test
        @DisplayName("intFieldSize matches written bytes")
        void intFieldSizeParity() {
            for (var v : new long[]{0L, 1L, 127L, 200L, 70_000L, Integer.MAX_VALUE, Integer.MIN_VALUE, -1L}) {
                assertSizeParity(WamEventSizes.intFieldSize(7, v),
                        encoder -> encoder.writeIntField(7, v, false));
            }
        }

        /**
         * Verifies bool field-size parity.
         */
        @Test
        @DisplayName("boolFieldSize matches written bytes")
        void boolFieldSizeParity() {
            assertSizeParity(WamEventSizes.boolFieldSize(7),
                    encoder -> encoder.writeBoolField(7, true, false));
            assertSizeParity(WamEventSizes.boolFieldSize(7),
                    encoder -> encoder.writeBoolField(7, false, false));
        }

        /**
         * Verifies string field-size parity across the str8 / str16 /
         * str32 length-prefix boundaries.
         */
        @Test
        @DisplayName("stringFieldSize matches written bytes (across all length-prefix tiers)")
        void stringFieldSizeParity() {
            for (var s : new String[]{"", "hi", "a".repeat(255), "a".repeat(256), "a".repeat(65535), "a".repeat(65536)}) {
                assertSizeParity(WamEventSizes.stringFieldSize(7, s),
                        encoder -> encoder.writeStringField(7, s, false));
            }
        }

        /**
         * Verifies float field-size parity.
         */
        @Test
        @DisplayName("floatFieldSize matches written bytes")
        void floatFieldSizeParity() {
            assertSizeParity(WamEventSizes.floatFieldSize(7),
                    encoder -> encoder.writeFloatField(7, 3.14, false));
        }

        /**
         * Verifies event-marker size parity.
         */
        @Test
        @DisplayName("eventMarkerSize matches written bytes")
        void eventMarkerSizeParity() {
            assertSizeParity(WamEventSizes.eventMarkerSize(2862, 1),
                    encoder -> encoder.writeEventMarker(2862, 1, false));
        }

        /**
         * Verifies null-entry size parity for the GLOBAL role.
         *
         * <p>{@code WamEventSizes.nullSize} returns the byte cost of
         * a GLOBAL null entry (the only role that emits bytes per
         * WAWebWamLibProtocol's null-shortcut); pairing it with a
         * FIELD or EVENT {@code writeNull} would always disagree
         * because those roles emit zero bytes.
         */
        @Test
        @DisplayName("nullSize matches GLOBAL null-entry written bytes")
        void nullSizeParity() {
            assertSizeParity(WamEventSizes.nullSize(7),
                    encoder -> encoder.writeNull(7, WamTags.GLOBAL));
            assertSizeParity(WamEventSizes.nullSize(300),
                    encoder -> encoder.writeNull(300, WamTags.GLOBAL));
        }
    }

    /**
     * Boundary tests for the buffer-overflow guard.
     */
    @Nested
    @DisplayName("buffer-overflow boundary")
    class OverflowBoundary {
        /**
         * Verifies that pre-allocating exactly {@code sizeOf()} bytes
         * succeeds without overflow.
         */
        @Test
        @DisplayName("pre-allocating exactly sizeOf bytes succeeds")
        void exactSizeSucceeds() {
            var size = WamEventSizes.intFieldSize(7, 70_000L);
            var buffer = new byte[size];
            var encoder = WamEventEncoder.of(buffer);
            encoder.writeIntField(7, 70_000L, false);
            assertEquals(size, encoder.written());
        }

        /**
         * Verifies that overflowing the buffer throws
         * {@link IndexOutOfBoundsException} with a self-describing
         * message.
         */
        @Test
        @DisplayName("encoding beyond the buffer throws IndexOutOfBoundsException")
        void overflowThrows() {
            var buffer = new byte[3]; // too small for an int32 field
            var encoder = WamEventEncoder.of(buffer);
            assertThrows(IndexOutOfBoundsException.class,
                    () -> encoder.writeIntField(7, 70_000L, false));
        }
    }

    /**
     * Encodes an int field, decodes it, and asserts the value
     * survives the round-trip.
     *
     * @param fieldId      the wire field id
     * @param value        the value to round-trip
     * @param hasFollowing whether the LAST flag should be unset
     */
    private static void assertIntRoundTrip(int fieldId, long value, boolean hasFollowing) {
        var buffer = new byte[BUFFER];
        var encoder = WamEventEncoder.of(buffer);
        encoder.writeIntField(fieldId, value, hasFollowing);
        var written = encoder.written();
        var decoder = WamEventDecoder.of(buffer, 0, written);
        var header = decoder.readHeader();
        assertEquals(fieldId, WamEventDecoder.fieldIdOf(header));
        assertEquals(value, decoder.readInt(header));
    }

    /**
     * Encodes a string field, decodes it, and asserts the value
     * survives the round-trip (UTF-8 preserved).
     *
     * @param fieldId      the wire field id
     * @param value        the value to round-trip
     * @param hasFollowing whether the LAST flag should be unset
     */
    private static void assertStringRoundTrip(int fieldId, String value, boolean hasFollowing) {
        var size = WamEventSizes.stringFieldSize(fieldId, value);
        var buffer = new byte[size];
        var encoder = WamEventEncoder.of(buffer);
        encoder.writeStringField(fieldId, value, hasFollowing);
        var decoder = WamEventDecoder.of(buffer, 0, encoder.written());
        var header = decoder.readHeader();
        assertEquals(fieldId, WamEventDecoder.fieldIdOf(header));
        assertEquals(value, decoder.readString(header));
    }

    /**
     * Encodes a float field, decodes it, and asserts the value
     * survives the round-trip (bit-equal for NaN / Infinity).
     *
     * @param fieldId the wire field id
     * @param value   the value to round-trip
     */
    private static void assertFloatRoundTrip(int fieldId, double value) {
        var size = WamEventSizes.floatFieldSize(fieldId);
        var buffer = new byte[size];
        var encoder = WamEventEncoder.of(buffer);
        encoder.writeFloatField(fieldId, value, false);
        var decoder = WamEventDecoder.of(buffer, 0, encoder.written());
        var header = decoder.readHeader();
        assertEquals(fieldId, WamEventDecoder.fieldIdOf(header));
        var actual = decoder.readFloat(header);
        assertEquals(Double.doubleToRawLongBits(value), Double.doubleToRawLongBits(actual),
                "float round-trip must preserve all 64 bits (including NaN payload)");
    }

    /**
     * Encodes the same operation through both sinks and asserts the
     * produced bytes are byte-identical.
     *
     * @param op the encoder operation
     */
    private static void assertSinkParity(java.util.function.Consumer<WamEventEncoder> op) {
        var arrayBuffer = new byte[BUFFER];
        var arrayEncoder = WamEventEncoder.of(arrayBuffer);
        op.accept(arrayEncoder);

        var stream = new ByteArrayOutputStream();
        var streamEncoder = WamEventEncoder.of(stream);
        op.accept(streamEncoder);

        assertEquals(arrayEncoder.written(), streamEncoder.written(),
                "byte-array and stream sinks must report the same written count");
        var arrayBytes = new byte[arrayEncoder.written()];
        System.arraycopy(arrayBuffer, 0, arrayBytes, 0, arrayBytes.length);
        assertArrayEquals(arrayBytes, stream.toByteArray(),
                "byte-array and stream sinks must produce identical bytes");
    }

    /**
     * Encodes the operation into a buffer sized exactly to
     * {@code expectedSize}, then asserts the operation succeeded
     * without overflow and {@code written()} equals
     * {@code expectedSize}.
     *
     * @param expectedSize the size reported by {@link WamEventSizes}
     * @param op           the encoder operation
     */
    private static void assertSizeParity(int expectedSize, java.util.function.Consumer<WamEventEncoder> op) {
        var buffer = new byte[expectedSize];
        var encoder = WamEventEncoder.of(buffer);
        op.accept(encoder);
        assertEquals(expectedSize, encoder.written(),
                "WamEventSizes.xxxSize must equal actual bytes written");
    }
}
