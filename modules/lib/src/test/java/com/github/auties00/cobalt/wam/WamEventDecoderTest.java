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
 * Corrupt-input handling tests for {@link WamEventDecoder}.
 *
 * <p>The WAM wire format is permissive in some directions
 * (forward-compatible: unknown field ids are skipped by the
 * caller-supplied {@code skip}) and strict in others (truncated
 * payloads, type-tag mismatches). This test pins the documented
 * error semantics so a regression in either direction surfaces
 * immediately.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li><b>Truncated event marker</b> — fewer bytes than the tag +
 *       fieldId requires — throws
 *       {@link IndexOutOfBoundsException}.</li>
 *   <li><b>Truncated payload</b> — tag declares an int32 but only
 *       two bytes follow — throws
 *       {@link IndexOutOfBoundsException}.</li>
 *   <li><b>Type-tag mismatch</b> — calling {@code readString} on a
 *       header whose value-type bits encode an integer (or vice
 *       versa) — throws {@link IllegalStateException}.</li>
 *   <li><b>Unknown field id</b> — the decoder does not validate the
 *       fieldId byte; it propagates whatever the buffer holds, so
 *       the caller's {@code default -> skip(header)} branch can
 *       handle it without throwing.</li>
 * </ul>
 */
@DisplayName("WamEventDecoder corrupt-input handling")
class WamEventDecoderTest {
    /**
     * Verifies that reading a header when the buffer has been
     * truncated below the tag + fieldId minimum (1 or 3 bytes
     * depending on WIDE_ID) throws
     * {@link IndexOutOfBoundsException}.
     */
    @Test
    @DisplayName("truncated event marker: no fieldId byte → IndexOutOfBoundsException")
    void truncatedEventMarker() {
        // Only the tag byte is present; the decoder needs at least
        // one more byte for the (tiny-id) fieldId.
        var truncated = new byte[]{(byte) (WamTags.EVENT | WamTags.LAST | WamTags.VALUE_INT_0)};
        var decoder = WamEventDecoder.of(truncated, 0, truncated.length);
        assertThrows(IndexOutOfBoundsException.class, decoder::readHeader);
    }

    /**
     * Verifies that a header declaring a 2-byte WIDE_ID fieldId but
     * the buffer only carries one of the two bytes throws.
     */
    @Test
    @DisplayName("truncated WIDE_ID fieldId: only 1 of 2 bytes → IndexOutOfBoundsException")
    void truncatedWideId() {
        var truncated = new byte[]{
                (byte) (WamTags.FIELD | WamTags.WIDE_ID | WamTags.VALUE_INT_0),
                0x01 // first byte of fieldId only
        };
        var decoder = WamEventDecoder.of(truncated, 0, truncated.length);
        assertThrows(IndexOutOfBoundsException.class, decoder::readHeader);
    }

    /**
     * Verifies that a header declaring an int32 payload but with
     * only 2 of the 4 payload bytes present throws on
     * {@code readInt}.
     */
    @Test
    @DisplayName("truncated int32 payload: 2 of 4 bytes → IndexOutOfBoundsException")
    void truncatedInt32Payload() {
        var truncated = new byte[]{
                (byte) (WamTags.FIELD | WamTags.LAST | WamTags.VALUE_INT32),
                0x05, // fieldId = 5
                0x01, 0x02 // only 2 of 4 int32 bytes
        };
        var decoder = WamEventDecoder.of(truncated, 0, truncated.length);
        var header = decoder.readHeader();
        assertEquals(5, WamEventDecoder.fieldIdOf(header));
        assertThrows(IndexOutOfBoundsException.class, () -> decoder.readInt(header));
    }

    /**
     * Verifies that a header declaring a STR8 payload with a
     * length byte that overruns the buffer throws on
     * {@code readString}.
     */
    @Test
    @DisplayName("truncated STR8 payload: length byte declares more than remains → IndexOutOfBoundsException")
    void truncatedStringPayload() {
        var truncated = new byte[]{
                (byte) (WamTags.FIELD | WamTags.LAST | WamTags.VALUE_STR8),
                0x05, // fieldId = 5
                0x10, // declares 16 bytes of UTF-8...
                'a', 'b' // ...but only 2 bytes are present
        };
        var decoder = WamEventDecoder.of(truncated, 0, truncated.length);
        var header = decoder.readHeader();
        assertThrows(IndexOutOfBoundsException.class, () -> decoder.readString(header));
    }

    /**
     * Verifies that calling {@code readString} on a header whose
     * value-type bits encode an integer throws
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
     * Verifies that calling {@code readInt} on a header whose
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
     * Verifies that calling {@code readFloat} on a non-float header
     * throws.
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
     * Verifies that {@link WamEventDecoder#skip} correctly advances
     * past every well-formed value type, mirroring what the
     * generated {@code *Impl.decode} default branch relies on for
     * forward compatibility (unknown field ids → skip + continue).
     */
    @Test
    @DisplayName("skip advances past every known value-type cleanly")
    void skipCoversAllTypes() {
        // Encode a heterogeneous stream of fields (excluding the
        // null-FIELD case, which WAWebWamLibProtocol's shared write
        // helper emits as zero bytes — only the GLOBAL role writes
        // a tag for a null value).
        //
        // The null-GLOBAL path is exercised separately below.
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
     * Verifies that {@code writeNull} is a no-op for the FIELD and
     * EVENT roles, matching WAWebWamLibProtocol's shared write
     * helper: when value is {@code null} the underlying
     * {@code if (n == null) r === GLOBAL && writeTag(...);}
     * predicate suppresses the write for non-GLOBAL roles.
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
                "writeNull(EVENT) must emit zero bytes — WAWebWamLibProtocol.writeEvent skips null");
    }

    /**
     * Verifies that the decoder does not validate the fieldId byte
     * itself: an arbitrary fieldId — even one not present in any
     * Cobalt {@code @WamEvent} interface — is returned unchanged
     * via {@link WamEventDecoder#fieldIdOf}. Forward compatibility
     * is the caller's responsibility (via
     * {@code default -> skip(header)}).
     */
    @Test
    @DisplayName("unknown fieldId is propagated to caller, not rejected")
    void unknownFieldIdIsPropagated() {
        var buffer = new byte[16];
        var encoder = WamEventEncoder.of(buffer);
        encoder.writeIntField(99, 5L, false); // 99 is intentionally
                                              // an id no Cobalt event
                                              // happens to declare
        var decoder = WamEventDecoder.of(buffer, 0, encoder.written());
        var header = decoder.readHeader();
        assertEquals(99, WamEventDecoder.fieldIdOf(header),
                "decoder passes the fieldId through; caller decides whether to skip");
        // Caller can still skip it cleanly without throwing.
        decoder.skip(header);
        assertTrue(!decoder.hasMore(),
                "after skip, the decoder should be at the buffer end");
    }
}
