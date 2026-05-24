package com.github.auties00.cobalt.media.transcode.image;

import com.github.auties00.cobalt.exception.WhatsAppMediaException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Strips non-essential JPEG segments and emits a normalised JFIF stream.
 *
 * @apiNote
 * The output JPEG keeps the SOI prefix, a synthesised JFIF (APP0) header,
 * every Start-Of-Frame (SOF), DQT, DHT, and DRI marker, the encoded SOS
 * compressed-data stream, and the EOI terminator. Every other segment is
 * dropped: APP1 (EXIF) is verified for its magic and discarded, APP13 and
 * COM are silently discarded, and APP2-APP12 / APP14 / APP15 raise a
 * {@link WhatsAppMediaException.Processing}. Run on the output of FFmpeg's
 * {@code mjpeg} encoder before uploading to WhatsApp so the resulting JFIF
 * stream is reproducible and free of metadata that would leak through the
 * CDN.
 *
 * @implNote
 * This implementation mirrors WA Web's {@code WAWebMediaJpeg.cleanJPEG}
 * byte-for-byte: the same magic-byte enforcement (JFIF or EXIF must precede
 * structural markers), the same set of permitted markers, and the same
 * synthetic JFIF prefix layout. The shadow-reader trick from the JS source
 * is folded into a single {@link ByteBuffer} cursor here because Java's
 * absolute and relative get methods make the shadow indirection unnecessary.
 *
 * @see #clean(byte[])
 */
@WhatsAppWebModule(moduleName = "WAWebMediaJpeg")
@WhatsAppWebModule(moduleName = "WAProgressiveJpegMarkers")
public final class JpegCleaner {
    /**
     * Length of the synthetic JFIF APP0 header emitted before any retained
     * segment.
     */
    private static final int JFIF_HEADER_LENGTH = 18;

    /**
     * Number of bytes a marker takes on the wire: one prefix byte ({@code 0xFF})
     * plus one marker code byte.
     */
    private static final int MARKER_SIZE = 2;

    /**
     * Number of bytes the length field of a segment occupies on the wire,
     * including itself.
     */
    private static final int LENGTH_FIELD_SIZE = 2;

    /**
     * The 0xFF byte that prefixes every JPEG marker.
     */
    private static final int MARKER_PREFIX = 0xFF;

    /**
     * Marker code for Start-Of-Image, the first byte pair of every JPEG.
     */
    private static final int MARKER_SOI = 0xD8;

    /**
     * Marker code for End-Of-Image, the last byte pair of every JPEG.
     */
    private static final int MARKER_EOI = 0xD9;

    /**
     * Marker code for Start-Of-Scan, after which the compressed image data
     * begins.
     */
    private static final int MARKER_SOS = 0xDA;

    /**
     * Marker code for APP0; carries the JFIF identification block on
     * compliant JPEGs.
     */
    private static final int MARKER_APP0 = 0xE0;

    /**
     * Marker code for APP1; usually carries an EXIF metadata block.
     */
    private static final int MARKER_APP1 = 0xE1;

    /**
     * Marker code for APP13; usually carries a Photoshop IRB metadata block.
     */
    private static final int MARKER_APP13 = 0xED;

    /**
     * Marker code for the COM (comment) segment.
     */
    private static final int MARKER_COM = 0xFE;

    /**
     * Marker code for Define-Quantization-Table.
     */
    private static final int MARKER_DQT = 0xDB;

    /**
     * Marker code for Define-Huffman-Table.
     */
    private static final int MARKER_DHT = 0xC4;

    /**
     * Marker code for Define-Restart-Interval.
     */
    private static final int MARKER_DRI = 0xDD;

    /**
     * Lowest marker code in the contiguous APP0..APP15 range.
     */
    private static final int MARKER_APP_LO = 0xE0;

    /**
     * Highest marker code in the contiguous APP0..APP15 range.
     */
    private static final int MARKER_APP_HI = 0xEF;

    /**
     * Lowest marker code in the SOF0..SOF15 range (codec frame headers).
     * SOF4, SOF8, and SOF12 are reserved holes (DHT/JPG/DAC respectively)
     * and are handled separately in {@link #isStartOfFrame(int)}.
     */
    private static final int MARKER_SOF_LO = 0xC0;

    /**
     * Highest marker code in the SOF0..SOF15 range.
     */
    private static final int MARKER_SOF_HI = 0xCF;

    /**
     * Marker code value at the SOF position 4 ({@code 0xC4}). Used by DHT,
     * not a frame header. Skipped by {@link #isStartOfFrame(int)}.
     */
    private static final int MARKER_SOF_HOLE_DHT = 0xC4;

    /**
     * Marker code value at the SOF position 8 ({@code 0xC8}). Reserved JPG
     * extension, never a real frame header.
     */
    private static final int MARKER_SOF_HOLE_JPG = 0xC8;

    /**
     * Marker code value at the SOF position 12 ({@code 0xCC}). Reserved for
     * DAC (arithmetic conditioning), not a frame header.
     */
    private static final int MARKER_SOF_HOLE_DAC = 0xCC;

    /**
     * Lowest restart-marker code; the eight restart markers RST0..RST7
     * occupy {@code 0xD0..0xD7} and are inlined inside the SOS compressed
     * stream rather than introducing a new segment.
     */
    private static final int MARKER_RST_LO = 0xD0;

    /**
     * Highest restart-marker code.
     */
    private static final int MARKER_RST_HI = 0xD7;

    /**
     * Magic byte sequence that follows the APP0 length field in a JFIF JPEG.
     */
    private static final byte[] JFIF_MAGIC = "JFIF\0".getBytes(StandardCharsets.US_ASCII);

    /**
     * Magic byte sequence that follows the APP1 length field in an EXIF
     * JPEG.
     */
    private static final byte[] EXIF_MAGIC = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);

    /**
     * Hidden constructor; the type exposes only the static
     * {@link #clean(byte[])} entry point.
     */
    private JpegCleaner() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a sanitised copy of the given JPEG with extraneous segments
     * stripped and a deterministic JFIF prefix prepended.
     *
     * @apiNote
     * Designed to run on the output of FFmpeg's {@code mjpeg} encoder before
     * uploading to WhatsApp; the encoded stream that comes back from FFmpeg
     * may carry an EXIF block with the local capture timestamps and software
     * version, both of which leak through the upload pipeline if not
     * stripped.
     *
     * @param source the raw JPEG bytes to sanitise
     * @return a fresh byte array holding the cleaned JPEG
     * @throws WhatsAppMediaException.Processing if the source is not a valid
     *         JPEG, is missing the required magic bytes (JFIF or EXIF),
     *         contains an unsupported APP marker, or ends before EOI
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaJpeg", exports = "cleanJPEG", adaptation = WhatsAppAdaptation.DIRECT)
    public static byte[] clean(byte[] source) throws WhatsAppMediaException.Processing {
        var input = ByteBuffer.wrap(source).order(ByteOrder.BIG_ENDIAN);
        var output = new ByteArrayOutputStream(source.length) {
            @Override
            public byte[] toByteArray() {
                return buf; // Optimization: avoid copying the backing array
            }
        };
        var props = new JfifProps();
        var jfifSeen = false;
        var exifSeen = false;
        var app0Seen = false;
        var eoiSeen = false;
        if (readUnsigned8(input) != MARKER_PREFIX || readUnsigned8(input) != MARKER_SOI) {
            throw new WhatsAppMediaException.Processing("SOI marker not at the start of the file");
        }
        var currentMarker = -1;
        while (input.hasRemaining()) {
            if (currentMarker < 0) {
                var prefix = readUnsigned8(input);
                if (prefix != MARKER_PREFIX) {
                    throw new WhatsAppMediaException.Processing(
                            "0x" + Integer.toHexString(prefix) + " is not a marker prefix");
                }
                currentMarker = readUnsigned8(input);
            }
            switch (currentMarker) {
                case MARKER_APP0 -> {
                    var length = readUnsigned16(input);
                    var segmentBodyStart = input.position();
                    if (!app0Seen) {
                        var magic = new byte[JFIF_MAGIC.length];
                        input.get(magic);
                        if (!matches(magic, JFIF_MAGIC)) {
                            throw new WhatsAppMediaException.Processing(
                                    "APP0 marker missing 'JFIF' magic bytes");
                        }
                        jfifSeen = true;
                        props.version = readUnsigned16(input);
                        props.densityUnits = readUnsigned8(input);
                        props.xDensity = readUnsigned16(input);
                        props.yDensity = readUnsigned16(input);
                        app0Seen = true;
                    }
                    input.position(segmentBodyStart + length - LENGTH_FIELD_SIZE);
                }
                case MARKER_APP1 -> {
                    var length = readUnsigned16(input);
                    var segmentBodyStart = input.position();
                    if (!exifSeen) {
                        var magic = new byte[EXIF_MAGIC.length];
                        input.get(magic);
                        if (!matches(magic, EXIF_MAGIC)) {
                            throw new WhatsAppMediaException.Processing(
                                    "APP1 marker missing 'EXIF' magic bytes");
                        }
                    }
                    exifSeen = true;
                    input.position(segmentBodyStart + length - LENGTH_FIELD_SIZE);
                }
                case MARKER_APP13, MARKER_COM -> {
                    if (!exifSeen && !jfifSeen) {
                        throw new WhatsAppMediaException.Processing("Missing magic bytes marker");
                    }
                    var length = readUnsigned16(input);
                    input.position(input.position() + length - LENGTH_FIELD_SIZE);
                }
                case MARKER_SOS -> {
                    if (!exifSeen && !jfifSeen) {
                        throw new WhatsAppMediaException.Processing("Missing magic bytes marker");
                    }
                    var sosHeaderLength = readUnsigned16(input);
                    var sosHeaderBodyStart = input.position();
                    var sosCompressedStart = sosHeaderBodyStart + sosHeaderLength - LENGTH_FIELD_SIZE;
                    input.position(sosCompressedStart);
                    var prev = readUnsigned8(input);
                    var nextMarker = -1;
                    while (input.hasRemaining()) {
                        var cur = readUnsigned8(input);
                        if (prev == MARKER_PREFIX && !isSosStreamByte(cur)) {
                            nextMarker = cur;
                            break;
                        }
                        prev = cur;
                    }
                    if (nextMarker < 0) {
                        throw new WhatsAppMediaException.Processing("Premature end of SOS stream");
                    }
                    var markerBytesAlreadyConsumed = MARKER_SIZE;
                    var copyStart = sosHeaderBodyStart - LENGTH_FIELD_SIZE - MARKER_SIZE;
                    var copyEnd = input.position() - markerBytesAlreadyConsumed;
                    output.write(source, copyStart, copyEnd - copyStart);
                    currentMarker = nextMarker;
                    continue;
                }
                case MARKER_EOI -> {
                    if (!exifSeen && !jfifSeen) {
                        throw new WhatsAppMediaException.Processing("Missing magic bytes marker");
                    }
                    output.write(MARKER_PREFIX);
                    output.write(MARKER_EOI);
                    eoiSeen = true;
                }
                default -> {
                    if (isStartOfFrame(currentMarker)
                            || currentMarker == MARKER_DQT
                            || currentMarker == MARKER_DHT
                            || currentMarker == MARKER_DRI) {
                        if (!exifSeen && !jfifSeen) {
                            throw new WhatsAppMediaException.Processing("Missing magic bytes marker");
                        }
                        var length = readUnsigned16(input);
                        var segmentStart = input.position() - LENGTH_FIELD_SIZE - MARKER_SIZE;
                        var totalBytes = MARKER_SIZE + length;
                        output.write(source, segmentStart, totalBytes);
                        input.position(segmentStart + totalBytes);
                    } else if (currentMarker >= MARKER_APP_LO && currentMarker <= MARKER_APP_HI) {
                        throw new WhatsAppMediaException.Processing(
                                "Received unexpected APP marker 0x" + Integer.toHexString(currentMarker));
                    } else {
                        throw new WhatsAppMediaException.Processing(
                                "Did not understand marker: 0x" + Integer.toHexString(currentMarker));
                    }
                }
            }
            currentMarker = -1;
            if (eoiSeen) {
                break;
            }
        }
        if (!eoiSeen) {
            throw new WhatsAppMediaException.Processing("No EOI tag found");
        }
        var jfifPrefix = buildJfifPrefix(props);
        var body = output.toByteArray();
        var result = new byte[jfifPrefix.length + body.length];
        System.arraycopy(jfifPrefix, 0, result, 0, jfifPrefix.length);
        System.arraycopy(body, 0, result, jfifPrefix.length, body.length);
        return result;
    }

    /**
     * Reports whether the given marker code is an SOF (Start-Of-Frame)
     * marker.
     *
     * @apiNote
     * SOF markers occupy the {@code 0xC0..0xCF} range with three holes:
     * {@code 0xC4} (DHT), {@code 0xC8} (JPG extension), and {@code 0xCC}
     * (DAC). The cleaner copies every real SOF marker through to the output
     * verbatim because they define the image geometry and codec parameters.
     *
     * @param marker the marker code to test
     * @return {@code true} if {@code marker} is one of SOF0..SOF15
     */
    private static boolean isStartOfFrame(int marker) {
        return marker >= MARKER_SOF_LO
                && marker <= MARKER_SOF_HI
                && marker != MARKER_SOF_HOLE_DHT
                && marker != MARKER_SOF_HOLE_JPG
                && marker != MARKER_SOF_HOLE_DAC;
    }

    /**
     * Reports whether a byte appearing immediately after a {@code 0xFF} in
     * the SOS compressed stream is part of the stream rather than the start
     * of a new segment.
     *
     * @apiNote
     * Three values are inlined inside the SOS compressed stream: the
     * escaped-zero marker ({@code 0xFF 0x00}, which encodes a literal
     * {@code 0xFF} in the entropy-coded data) and the eight restart markers
     * {@code RST0..RST7} ({@code 0xD0..0xD7}). Anything else ends the SOS
     * stream and begins the next real segment.
     *
     * @param value the byte that follows a {@code 0xFF} prefix
     * @return {@code true} if {@code value} is part of the SOS stream and
     *         {@link #clean(byte[])} should continue scanning
     */
    private static boolean isSosStreamByte(int value) {
        return value == 0
                || (value >= MARKER_RST_LO && value <= MARKER_RST_HI);
    }

    /**
     * Builds the canonical 18-byte JFIF APP0 prefix that opens every cleaned
     * JPEG.
     *
     * @apiNote
     * The prefix layout matches WA Web's {@code WAWebMediaJpeg} synthesiser:
     * {@snippet :
     *  // bytes 0..1   SOI marker
     *  0xFF, 0xD8,
     *  // bytes 2..5   APP0 marker + length (always 16)
     *  0xFF, 0xE0, 0x00, 0x10,
     *  // bytes 6..10  "JFIF\0" magic
     *  'J', 'F', 'I', 'F', 0x00,
     *  // bytes 11..12 version (big-endian u16)
     *  // bytes 13     density units
     *  // bytes 14..15 x density (big-endian u16)
     *  // bytes 16..17 y density (big-endian u16)
     *  // bytes 18..19 thumbnail width=0, height=0 (final two zero bytes)
     * }
     *
     * @param props the JFIF parameters extracted from the source APP0
     * @return an 18-byte array holding the SOI + APP0 + JFIF block
     */
    private static byte[] buildJfifPrefix(JfifProps props) {
        var prefix = new byte[JFIF_HEADER_LENGTH];
        var buf = ByteBuffer.wrap(prefix).order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) 0xFF);
        buf.put((byte) MARKER_SOI);
        buf.put((byte) 0xFF);
        buf.put((byte) MARKER_APP0);
        buf.putShort((short) 0x0010);
        buf.put((byte) 'J');
        buf.put((byte) 'F');
        buf.put((byte) 'I');
        buf.put((byte) 'F');
        buf.put((byte) 0);
        buf.putShort((short) props.version);
        buf.put((byte) props.densityUnits);
        buf.putShort((short) props.xDensity);
        buf.putShort((short) props.yDensity);
        return prefix;
    }

    /**
     * Reads an unsigned 8-bit value from the given buffer.
     *
     * @param buf the buffer to read from
     * @return the next byte interpreted as an unsigned integer in
     *         {@code 0..255}
     */
    private static int readUnsigned8(ByteBuffer buf) {
        return Byte.toUnsignedInt(buf.get());
    }

    /**
     * Reads an unsigned 16-bit big-endian value from the given buffer.
     *
     * @param buf the buffer to read from
     * @return the next two bytes interpreted as an unsigned integer in
     *         {@code 0..65535}
     */
    private static int readUnsigned16(ByteBuffer buf) {
        return Short.toUnsignedInt(buf.getShort());
    }

    /**
     * Compares two byte arrays of equal length for byte-for-byte equality.
     *
     * @param a the first array
     * @param b the second array
     * @return {@code true} when every byte matches
     */
    private static boolean matches(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (var i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Mutable scratch holding the four JFIF APP0 parameters extracted from
     * the source JPEG.
     *
     * @apiNote
     * Populated when the first APP0 segment is encountered; values are
     * copied verbatim into the synthetic prefix that opens the cleaned
     * output. Default values match WA Web's defaults
     * ({@code version=257, density units=0, x density=1, y density=1}) and
     * are only used when no APP0 segment supplies replacements before the
     * first structural marker triggers the magic-bytes check; see
     * {@link #clean(byte[])} for the order of operations.
     */
    private static final class JfifProps {
        /**
         * JFIF version field; defaults to the WA Web default {@code 0x0101}.
         */
        int version = 0x0101;

        /**
         * Density units; {@code 0} (no units), {@code 1} (per inch), or
         * {@code 2} (per cm).
         */
        int densityUnits;

        /**
         * Horizontal density value.
         */
        int xDensity = 1;

        /**
         * Vertical density value.
         */
        int yDensity = 1;
    }
}
