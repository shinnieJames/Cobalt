package com.github.auties00.cobalt.wam;

import com.github.auties00.cobalt.wam.binary.WamEventDecoder;
import com.github.auties00.cobalt.wam.binary.WamEventEncoder;
import com.github.auties00.cobalt.wam.binary.WamTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Corrupt-input and forward-compatibility tests for
 * {@link WamEventDecoder}.
 *
 * @apiNote
 * Pins the documented error semantics so a regression in either
 * direction (over-strict rejection of forward-compatible input, or
 * silent acceptance of truncated or type-mismatched input) surfaces
 * immediately. The matrix covers truncated event markers, truncated
 * payloads, type-tag mismatches across the read primitives, and the
 * forward-compatible {@code unknown fieldId} path that the generated
 * {@code *Impl.decode default -> skip(header)} branch relies on.
 *
 * @implNote
 * Cobalt-internal: WA Web's matching {@code WAWebWamLibProtocol} read
 * helpers have no per-error externally observable surface, so MCP
 * grounding is via the encoder/decoder contract rather than a
 * captured trace.
 */
@DisplayName("WamEventDecoder corrupt-input handling")
class WamEventDecoderTest {
    /**
     * Verifies that reading a header from a buffer truncated below
     * the tag + fieldId minimum throws
     * {@link IndexOutOfBoundsException}.
     */
    @Test
    @DisplayName("truncated event marker: no fieldId byte → IndexOutOfBoundsException")
    void truncatedEventMarker() {
        var truncated = new byte[]{(byte) (WamTags.EVENT | WamTags.LAST | WamTags.VALUE_INT_0)};
        var decoder = WamEventDecoder.of(truncated, 0, truncated.length);
        assertThrows(IndexOutOfBoundsException.class, decoder::readHeader);
    }

    /**
     * Verifies that a header declaring a 2-byte WIDE_ID fieldId but
     * with only the first byte present throws.
     */
    @Test
    @DisplayName("truncated WIDE_ID fieldId: only 1 of 2 bytes → IndexOutOfBoundsException")
    void truncatedWideId() {
        var truncated = new byte[]{
                (byte) (WamTags.FIELD | WamTags.WIDE_ID | WamTags.VALUE_INT_0),
                0x01
        };
        var decoder = WamEventDecoder.of(truncated, 0, truncated.length);
        assertThrows(IndexOutOfBoundsException.class, decoder::readHeader);
    }

    /**
     * Verifies that a header declaring a 4-byte int32 payload but
     * carrying only 2 payload bytes throws on
     * {@link WamEventDecoder#readInt}.
     */
    @Test
    @DisplayName("truncated int32 payload: 2 of 4 bytes → IndexOutOfBoundsException")
    void truncatedInt32Payload() {
        var truncated = new byte[]{
                (byte) (WamTags.FIELD | WamTags.LAST | WamTags.VALUE_INT32),
                0x05,
                0x01, 0x02
        };
        var decoder = WamEventDecoder.of(truncated, 0, truncated.length);
        var header = decoder.readHeader();
        assertEquals(5, WamEventDecoder.fieldIdOf(header));
        assertThrows(IndexOutOfBoundsException.class, () -> decoder.readInt(header));
    }

    /**
     * Verifies that a header declaring a STR8 payload whose length
     * byte overruns the buffer throws on
     * {@link WamEventDecoder#readString}.
     */
    @Test
    @DisplayName("truncated STR8 payload: length byte declares more than remains → IndexOutOfBoundsException")
    void truncatedStringPayload() {
        var truncated = new byte[]{
                (byte) (WamTags.FIELD | WamTags.LAST | WamTags.VALUE_STR8),
                0x05,
                0x10,
                'a', 'b'
        };
        var decoder = WamEventDecoder.of(truncated, 0, truncated.length);
        var header = decoder.readHeader();
        assertThrows(IndexOutOfBoundsException.class, () -> decoder.readString(header));
    }

    /**
     * Verifies that {@link WamEventDecoder#readString} on a header
     * whose value-type bits encode an integer throws
     * {@link IllegalStateException}.
     */
    @Test
    @DisplayName("type-tag mismatch: readString on int header → IllegalStateException")
    void readStringOnIntHeader() {
        var buffer = new byte[16];
        var encoder = WamEventEncoder.of(buffer);
        encoder.writeIntField(5, 42L, false);
        var decoder = WamEventDecoder.of(buffer, 0, encoder.written());
        var header = decoder.readHeader();
        assertThrows(IllegalStateException.class, () -> decoder.readString(header));
    }

    /**
     * Verifies that {@link WamEventDecoder#readInt} on a header whose
     * value-type bits encode a string throws.
     */
    @Test
    @DisplayName("type-tag mismatch: readInt on string header → IllegalStateException")
    void readIntOnStringHeader() {
        var buffer = new byte[32];
        var encoder = WamEventEncoder.of(buffer);
        encoder.writeStringField(5, "hi", false);
        var decoder = WamEventDecoder.of(buffer, 0, encoder.written());
        var header = decoder.readHeader();
        assertThrows(IllegalStateException.class, () -> decoder.readInt(header));
    }

    /**
     * Verifies that {@link WamEventDecoder#readFloat} on a
     * non-float header throws.
     */
    @Test
    @DisplayName("type-tag mismatch: readFloat on int header → IllegalStateException")
    void readFloatOnIntHeader() {
        var buffer = new byte[16];
        var encoder = WamEventEncoder.of(buffer);
        encoder.writeIntField(5, 200L, false);
        var decoder = WamEventDecoder.of(buffer, 0, encoder.written());
        var header = decoder.readHeader();
        assertThrows(IllegalStateException.class, () -> decoder.readFloat(header));
    }

    /**
     * Verifies that {@link WamEventDecoder#skip} advances past every
     * well-formed value type, the property the generated
     * {@code *Impl.decode} default branch relies on for forward
     * compatibility.
     *
     * @implNote
     * Exercises one entry per value-type encoding (null GLOBAL, int 0,
     * int 1, int 7, int 200, int 70 000, float, str8, str16) so a
     * regression on any of them fails this test rather than surfacing
     * as a hard-to-diagnose downstream decoder hang.
     */
    @Test
    @DisplayName("skip advances past every known value-type cleanly")
    void skipCoversAllTypes() {
        var buffer = new byte[512];
        var encoder = WamEventEncoder.of(buffer);
        encoder.writeNull(1, WamTags.GLOBAL);
        encoder.writeIntField(2, 0L, true);
        encoder.writeIntField(3, 1L, true);
        encoder.writeIntField(4, 7L, true);
        encoder.writeIntField(5, 200L, true);
        encoder.writeIntField(6, 70_000L, true);
        encoder.writeFloatField(7, 3.14, true);
        encoder.writeStringField(8, "hi", true);
        encoder.writeStringField(9, "a".repeat(300), false);
        var size = encoder.written();

        var decoder = WamEventDecoder.of(buffer, 0, size);
        var skipped = 0;
        while (decoder.hasMore()) {
            var header = decoder.readHeader();
            decoder.skip(header);
            skipped++;
        }
        assertEquals(9, skipped, "skip must walk past all 9 entries");
    }

    /**
     * Verifies that {@code writeNull} for the FIELD and EVENT roles is
     * a no-op, matching {@code WAWebWamLibProtocol}'s shared write
     * helper: only the GLOBAL role emits bytes when the value is
     * {@code null}.
     */
    @Test
    @DisplayName("writeNull is a no-op for FIELD / EVENT roles (matches WA Web's writeField/writeEvent null shortcut)")
    void writeNullNonGlobalIsNoop() {
        var buffer = new byte[16];
        var encoder = WamEventEncoder.of(buffer);
        encoder.writeNull(5, WamTags.FIELD);
        assertEquals(0, encoder.written(),
                "writeNull(FIELD) must emit zero bytes, matching WAWebWamLibProtocol's behaviour");

        encoder = WamEventEncoder.of(buffer);
        encoder.writeNull(5, WamTags.FIELD | WamTags.LAST);
        assertEquals(0, encoder.written(),
                "writeNull(FIELD|LAST) must emit zero bytes");

        encoder = WamEventEncoder.of(buffer);
        encoder.writeNull(5, WamTags.EVENT);
        assertEquals(0, encoder.written(),
                "writeNull(EVENT) must emit zero bytes - WAWebWamLibProtocol.writeEvent skips null");
    }

    /**
     * Verifies that the decoder propagates an arbitrary fieldId byte
     * (even one no Cobalt {@code @WamEvent} declares) unchanged via
     * {@link WamEventDecoder#fieldIdOf}, leaving the
     * {@code default -> skip(header)} branch in caller code as the
     * forward-compatibility seam.
     */
    @Test
    @DisplayName("unknown fieldId is propagated to caller, not rejected")
    void unknownFieldIdIsPropagated() {
        var buffer = new byte[16];
        var encoder = WamEventEncoder.of(buffer);
        encoder.writeIntField(99, 5L, false);
        var decoder = WamEventDecoder.of(buffer, 0, encoder.written());
        var header = decoder.readHeader();
        assertEquals(99, WamEventDecoder.fieldIdOf(header),
                "decoder passes the fieldId through; caller decides whether to skip");
        decoder.skip(header);
        assertTrue(!decoder.hasMore(),
                "after skip, the decoder should be at the buffer end");
    }
}
