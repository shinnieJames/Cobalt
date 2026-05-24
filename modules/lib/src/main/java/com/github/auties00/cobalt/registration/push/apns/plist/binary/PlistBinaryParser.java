package com.github.auties00.cobalt.registration.push.apns.plist.binary;

import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistArrayValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistBooleanValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistDataValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistDateValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistDictionaryValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistFloatingPointValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistIntegerValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistStringValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistValue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * A random-access parser for Apple's {@code bplist00} binary
 * property-list format.
 *
 * @apiNote
 * Consumed indirectly via {@link com.github.auties00.cobalt.registration.push.apns.plist.Plist#parse(byte[])}.
 * The {@code bplist00} format puts a 32-byte trailer at the end of
 * the file carrying the offset-table location, the number of
 * objects, the top-object index, and the byte widths used for offset
 * and inter-object reference fields. Each object is identified by a
 * single marker byte whose high nibble selects the type and whose
 * low nibble carries either the count or {@code 1 << n} as the byte
 * width of the encoded scalar.
 *
 * @implNote
 * This implementation walks the object tree on demand: the trailer
 * is read once in the constructor, then {@link #readObject(int)}
 * decodes each object the top-level reference chains into. Strings
 * are materialised into Java {@link String}s (the JVM has no
 * zero-copy alternative for arbitrary encodings) but {@code <data>}
 * payloads are exposed as zero-copy offset / length slices over the
 * source buffer via {@link PlistDataValue}.
 */
public final class PlistBinaryParser {
    /**
     * The length in bytes of the trailer that closes a binary plist.
     */
    private static final int TRAILER_SIZE = 32;

    /**
     * The magic header bytes that identify a binary plist.
     *
     * @apiNote
     * Exposed package-private so {@link PlistBinaryWriter} can emit
     * the same bytes and {@link #isBinary(byte[])} can probe for
     * them.
     */
    static final byte[] MAGIC = {'b', 'p', 'l', 'i', 's', 't', '0', '0'};

    /**
     * The source bytes.
     */
    private final byte[] src;

    /**
     * The width in bytes of each offset-table entry.
     */
    private final int offsetSize;

    /**
     * The width in bytes of each inter-object reference.
     */
    private final int refSize;

    /**
     * The index of the top-level object within the object table.
     */
    private final int topObject;

    /**
     * The absolute offset of the offset table within {@link #src}.
     */
    private final int offsetTableOffset;

    /**
     * Constructs a parser bound to the source bytes and reads the
     * trailer.
     *
     * @apiNote
     * Private; callers must route through {@link #parse(byte[])}.
     *
     * @implNote
     * This implementation reads the trailer fields at fixed offsets
     * relative to {@code src.length - TRAILER_SIZE} per Apple's
     * {@code CFBinaryPList.c}, validates the offset and reference
     * widths are in {@code [1, 8]}, and bounds-checks the offset
     * table before any object reads can run.
     *
     * @param src the source bytes
     * @throws IOException if the source is too short for a trailer,
     *                     the offset or reference widths are out of
     *                     range, or the offset table escapes the
     *                     source
     */
    private PlistBinaryParser(byte[] src) throws IOException {
        if (src.length < MAGIC.length + TRAILER_SIZE) {
            throw new IOException("binary plist too short: " + src.length);
        }
        this.src = src;
        var trailer = src.length - TRAILER_SIZE;
        this.offsetSize = src[trailer + 6] & 0xFF;
        this.refSize = src[trailer + 7] & 0xFF;
        if (offsetSize < 1 || offsetSize > 8 || refSize < 1 || refSize > 8) {
            throw new IOException("invalid trailer widths: offset=" + offsetSize + " ref=" + refSize);
        }
        var numObjects = readUnsignedBigEndian(trailer + 8, 8);
        this.topObject = (int) readUnsignedBigEndian(trailer + 16, 8);
        this.offsetTableOffset = (int) readUnsignedBigEndian(trailer + 24, 8);
        if (offsetTableOffset < 0 || offsetTableOffset > src.length
                || (long) offsetTableOffset + numObjects * offsetSize > src.length) {
            throw new IOException("offset table escapes source: at " + offsetTableOffset
                    + " for " + numObjects + " objects of " + offsetSize + " bytes");
        }
    }

    /**
     * Reports whether the source bytes start with the
     * {@code bplist00} magic.
     *
     * @apiNote
     * Used by {@link com.github.auties00.cobalt.registration.push.apns.plist.Plist#parse(byte[])}
     * to dispatch between the binary and XML parsers.
     *
     * @param data the source bytes
     * @return {@code true} if {@code data} starts with the binary
     *         plist magic
     */
    public static boolean isBinary(byte[] data) {
        if (data.length < MAGIC.length) {
            return false;
        }
        for (var i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses the source bytes into a {@link PlistValue} tree.
     *
     * @apiNote
     * Entry point; constructs an internal parser instance, reads the
     * trailer and decodes the top object.
     *
     * @param data the binary plist bytes
     * @return the parsed root value
     * @throws IOException if the source is malformed
     */
    public static PlistValue parse(byte[] data) throws IOException {
        var parser = new PlistBinaryParser(data);
        return parser.readObject(parser.topObject);
    }

    /**
     * Resolves the on-disk offset of an object and decodes it.
     *
     * @apiNote
     * The internal entry point used by the array and dictionary
     * decoders to follow inter-object references.
     *
     * @param index the index into the object table
     * @return the decoded value
     * @throws IOException if the marker is unknown or the encoded
     *                     payload escapes the source
     */
    private PlistValue readObject(int index) throws IOException {
        var entry = offsetTableOffset + index * offsetSize;
        var offset = (int) readUnsignedBigEndian(entry, offsetSize);
        return readObjectAt(offset);
    }

    /**
     * Decodes the object starting at the given absolute offset.
     *
     * @apiNote
     * Dispatches on the high nibble of the marker byte: {@code 0x0n}
     * for singletons (true / false), {@code 0x1n} integers, {@code
     * 0x2n} reals, {@code 0x3n} dates, {@code 0x4n} data,
     * {@code 0x5n / 0x6n / 0x7n} strings,
     * {@code 0xAn} arrays, {@code 0xDn} dicts.
     *
     * @param offset the absolute offset within {@link #src}
     * @return the decoded value
     * @throws IOException if the marker is unknown or the encoded
     *                     payload escapes the source
     */
    private PlistValue readObjectAt(int offset) throws IOException {
        var marker = src[offset] & 0xFF;
        var type = marker >>> 4;
        var info = marker & 0x0F;
        return switch (type) {
            case 0x0 -> switch (info) {
                case 0x8 -> new PlistBooleanValue(false);
                case 0x9 -> new PlistBooleanValue(true);
                default -> throw new IOException("unsupported singleton 0x0/" + info);
            };
            case 0x1 -> readInteger(offset, info);
            case 0x2 -> readReal(offset, info);
            case 0x3 -> readDate(offset);
            case 0x4 -> readData(offset, info);
            case 0x5 -> readString(offset, info, StandardCharsets.US_ASCII, 1);
            case 0x6 -> readString(offset, info, StandardCharsets.UTF_16BE, 2);
            case 0x7 -> readString(offset, info, StandardCharsets.UTF_8, 1);
            case 0xA -> readArray(offset, info);
            case 0xD -> readDict(offset, info);
            default -> throw new IOException("unsupported plist marker 0x" + Integer.toHexString(marker));
        };
    }

    /**
     * Decodes a {@code 0x1n} integer marker.
     *
     * @apiNote
     * Apple's spec treats widths of 1, 2, and 4 bytes as unsigned
     * and 8 bytes as signed two's-complement; 16-byte (uint128)
     * integers are explicitly rejected because Cobalt has no
     * 128-bit integer type to surface them as.
     *
     * @param offset the marker offset
     * @param info   the low nibble of the marker, where
     *               {@code 1 << info} is the byte count
     * @return the integer value
     * @throws IOException if the encoded width exceeds 8 bytes
     */
    private PlistIntegerValue readInteger(int offset, int info) throws IOException {
        var byteCount = 1 << info;
        if (byteCount > 8) {
            throw new IOException("16-byte plist integer not supported");
        }
        return new PlistIntegerValue(readUnsignedBigEndian(offset + 1, byteCount));
    }

    /**
     * Decodes a {@code 0x2n} floating-point marker.
     *
     * @apiNote
     * Apple's spec allows widths of 4 ({@code float32}) and 8
     * ({@code float64}) bytes; other widths surface as
     * {@link IOException}.
     *
     * @implNote
     * This implementation uses
     * {@link Float#intBitsToFloat(int)} / {@link Double#longBitsToDouble(long)}
     * to reinterpret the big-endian bit pattern as the
     * corresponding IEEE-754 value.
     *
     * @param offset the marker offset
     * @param info   the low nibble of the marker, where
     *               {@code 1 << info} is the byte count
     * @return the floating-point value
     * @throws IOException if the encoded width is neither 4 nor 8
     */
    private PlistFloatingPointValue readReal(int offset, int info) throws IOException {
        var byteCount = 1 << info;
        return switch (byteCount) {
            case 4 -> new PlistFloatingPointValue(Float.intBitsToFloat((int) readUnsignedBigEndian(offset + 1, 4)));
            case 8 -> new PlistFloatingPointValue(Double.longBitsToDouble(readUnsignedBigEndian(offset + 1, 8)));
            default -> throw new IOException("unsupported real width: " + byteCount);
        };
    }

    /**
     * Decodes a {@code 0x33} date marker.
     *
     * @apiNote
     * Dates are IEEE-754 doubles holding seconds since
     * 2001-01-01T00:00:00Z (the Apple reference date).
     *
     * @implNote
     * This implementation rebases the offset onto the Unix epoch by
     * adding {@code 978307200} (the difference in seconds between
     * the two epochs) and preserves fractional seconds in nanos.
     *
     * @param offset the marker offset
     * @return the date value
     */
    private PlistDateValue readDate(int offset) {
        var seconds = Double.longBitsToDouble(readUnsignedBigEndian(offset + 1, 8));
        var whole = (long) Math.floor(seconds);
        var nanos = (long) ((seconds - whole) * 1_000_000_000L);
        return new PlistDateValue(Instant.ofEpochSecond(whole + 978_307_200L, nanos));
    }

    /**
     * Decodes a {@code 0x4n} data marker.
     *
     * @apiNote
     * The returned {@link PlistDataValue} is a zero-copy slice over
     * {@link #src}; callers that need an isolated copy must call
     * {@link PlistDataValue#toByteArray()}.
     *
     * @param offset the marker offset
     * @param info   the inline length, or {@code 0xF} when an
     *               extended length follows the marker
     * @return the data value
     * @throws IOException if the slice escapes the source
     */
    private PlistDataValue readData(int offset, int info) throws IOException {
        var span = readLength(offset, info);
        return new PlistDataValue(src, span.dataOffset(), span.length());
    }

    /**
     * Decodes a {@code 0x5n} / {@code 0x6n} / {@code 0x7n} string
     * marker.
     *
     * @apiNote
     * The three string-type nibbles encode ASCII (1 byte per code
     * unit), UTF-16BE (2 bytes per code unit) and UTF-8 (1 byte per
     * code unit). Callers always receive a Java {@link String}; the
     * cost of allocation is unavoidable.
     *
     * @param offset       the marker offset
     * @param info         the inline character count, or {@code 0xF}
     *                     for an extended count
     * @param charset      the source encoding
     * @param bytesPerUnit the source bytes per code unit
     * @return the string value
     * @throws IOException if the slice escapes the source
     */
    private PlistStringValue readString(int offset, int info, Charset charset, int bytesPerUnit) throws IOException {
        var span = readLength(offset, info);
        var byteLength = span.length() * bytesPerUnit;
        return new PlistStringValue(new String(src, span.dataOffset(), byteLength, charset));
    }

    /**
     * Decodes a {@code 0xAn} array marker.
     *
     * @apiNote
     * The on-disk layout is N inter-object references back-to-back;
     * each reference is decoded via {@link #readObject(int)}.
     *
     * @param offset the marker offset
     * @param info   the inline element count, or {@code 0xF} for an
     *               extended count
     * @return the array value
     * @throws IOException if any element fails to decode
     */
    private PlistArrayValue readArray(int offset, int info) throws IOException {
        var span = readLength(offset, info);
        var count = span.length();
        var items = new ArrayList<PlistValue>(count);
        for (var i = 0; i < count; i++) {
            var ref = (int) readUnsignedBigEndian(span.dataOffset() + i * refSize, refSize);
            items.add(readObject(ref));
        }
        return new PlistArrayValue(items);
    }

    /**
     * Decodes a {@code 0xDn} dictionary marker.
     *
     * @apiNote
     * The on-disk layout is N key references followed by N value
     * references, both contiguous; keys must resolve to
     * {@link PlistStringValue} per the spec.
     *
     * @implNote
     * This implementation uses {@link LinkedHashMap} so the
     * insertion order of the on-disk dictionary is preserved for
     * downstream callers that walk the entries.
     *
     * @param offset the marker offset
     * @param info   the inline entry count, or {@code 0xF} for an
     *               extended count
     * @return the dictionary value
     * @throws IOException if any key fails to decode as a string
     */
    private PlistDictionaryValue readDict(int offset, int info) throws IOException {
        var span = readLength(offset, info);
        var count = span.length();
        var keysOffset = span.dataOffset();
        var valuesOffset = keysOffset + count * refSize;
        var entries = new LinkedHashMap<String, PlistValue>(Math.max(count * 2, 4));
        for (var i = 0; i < count; i++) {
            var keyRef = (int) readUnsignedBigEndian(keysOffset + i * refSize, refSize);
            var valueRef = (int) readUnsignedBigEndian(valuesOffset + i * refSize, refSize);
            var key = readObject(keyRef);
            if (!(key instanceof PlistStringValue s)) {
                throw new IOException("dictionary key is not a string: " + key);
            }
            entries.put(s.value(), readObject(valueRef));
        }
        return new PlistDictionaryValue(entries);
    }

    /**
     * Resolves the count and the start offset of the payload for a
     * variable-length marker.
     *
     * @apiNote
     * When the marker's low nibble is below {@code 0xF} the count is
     * inline; otherwise the next byte is itself an integer marker
     * carrying the actual count.
     *
     * @param markerOffset the offset of the original marker
     * @param info         the low nibble of the original marker
     * @return the resolved {@code (length, dataOffset)} pair
     * @throws IOException if the extended-length marker is not an
     *                     integer or exceeds 8 bytes
     */
    private LengthSpan readLength(int markerOffset, int info) throws IOException {
        if (info < 0xF) {
            return new LengthSpan(info, markerOffset + 1);
        }
        var nextMarker = src[markerOffset + 1] & 0xFF;
        if ((nextMarker >>> 4) != 0x1) {
            throw new IOException("expected integer marker for extended length, got 0x"
                    + Integer.toHexString(nextMarker));
        }
        var byteCount = 1 << (nextMarker & 0x0F);
        if (byteCount > 8) {
            throw new IOException("extended length exceeds 8 bytes");
        }
        var length = (int) readUnsignedBigEndian(markerOffset + 2, byteCount);
        return new LengthSpan(length, markerOffset + 2 + byteCount);
    }

    /**
     * Reads {@code byteCount} big-endian bytes as a {@code long}.
     *
     * @apiNote
     * Used for offsets, reference indices, lengths and integer
     * payloads. The natural overflow gives the correct two's-
     * complement value for 8-byte signed integers per Apple's spec.
     *
     * @param offset    the start offset
     * @param byteCount the number of bytes from 1 to 8
     * @return the assembled value
     */
    private long readUnsignedBigEndian(int offset, int byteCount) {
        long value = 0;
        for (var i = 0; i < byteCount; i++) {
            value = (value << 8) | (src[offset + i] & 0xFFL);
        }
        return value;
    }

    /**
     * The pair of {@code (length, dataOffset)} returned by
     * {@link #readLength(int, int)}.
     *
     * @apiNote
     * Internal helper record; not exposed beyond the parser.
     *
     * @param length     the count or byte length
     * @param dataOffset the offset of the first payload byte
     */
    private record LengthSpan(int length, int dataOffset) {
    }
}
