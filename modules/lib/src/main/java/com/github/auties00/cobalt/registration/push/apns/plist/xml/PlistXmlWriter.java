package com.github.auties00.cobalt.registration.push.apns.plist.xml;

import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistArrayValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistBooleanValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistDataValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistDateValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistDictionaryValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistFloatingPointValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistIntegerValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistStringValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistValue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * A two-pass exact-allocation writer for Apple's XML property-list
 * format.
 *
 * @apiNote
 * Consumed indirectly via
 * {@link com.github.auties00.cobalt.registration.push.apns.plist.Plist#writeXml(PlistValue)}.
 * Emits the canonical Apple preamble (XML prolog, the
 * {@code PropertyList-1.0.dtd} doctype, and the
 * {@code <plist version="1.0">} root) so {@code albert.apple.com}
 * accepts the bytes verbatim.
 *
 * @implNote
 * This implementation runs pass one to compute the exact UTF-8 byte
 * count via {@link #sizeOf(PlistValue, int)}, then pass two to fill
 * a single {@code byte[]} of that size in place. There is no
 * {@link StringBuilder} growth, no intermediate {@link String}
 * conversion, and no {@link java.nio.charset.CharsetEncoder} pass.
 * All XML element tags and entity replacements are pre-encoded as
 * {@code byte[]} constants.
 */
public final class PlistXmlWriter {
    /**
     * The XML preamble.
     *
     * @apiNote
     * Matches the canonical Apple {@code PropertyList-1.0.dtd}
     * declaration so Apple's parsers accept the bytes verbatim;
     * emitted at the head of every {@link #write(PlistValue)} output.
     */
    private static final byte[] PREAMBLE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            """.getBytes(StandardCharsets.US_ASCII);

    /**
     * The closing {@code </plist>} block.
     */
    private static final byte[] EPILOGUE = "\n</plist>\n".getBytes(StandardCharsets.US_ASCII);

    /**
     * The opening {@code <dict>} tag with trailing newline.
     */
    private static final byte[] DICT_OPEN = {'<', 'd', 'i', 'c', 't', '>', '\n'};
    /**
     * The closing {@code </dict>} tag.
     */
    private static final byte[] DICT_CLOSE = {'<', '/', 'd', 'i', 'c', 't', '>'};
    /**
     * The opening {@code <array>} tag with trailing newline.
     */
    private static final byte[] ARRAY_OPEN = {'<', 'a', 'r', 'r', 'a', 'y', '>', '\n'};
    /**
     * The closing {@code </array>} tag.
     */
    private static final byte[] ARRAY_CLOSE = {'<', '/', 'a', 'r', 'r', 'a', 'y', '>'};
    /**
     * The opening {@code <key>} tag.
     */
    private static final byte[] KEY_OPEN = {'<', 'k', 'e', 'y', '>'};
    /**
     * The closing {@code </key>} tag with trailing newline.
     */
    private static final byte[] KEY_CLOSE = {'<', '/', 'k', 'e', 'y', '>', '\n'};
    /**
     * The opening {@code <string>} tag.
     */
    private static final byte[] STRING_OPEN = {'<', 's', 't', 'r', 'i', 'n', 'g', '>'};
    /**
     * The closing {@code </string>} tag.
     */
    private static final byte[] STRING_CLOSE = {'<', '/', 's', 't', 'r', 'i', 'n', 'g', '>'};
    /**
     * The opening {@code <data>} tag.
     */
    private static final byte[] DATA_OPEN = {'<', 'd', 'a', 't', 'a', '>'};
    /**
     * The closing {@code </data>} tag.
     */
    private static final byte[] DATA_CLOSE = {'<', '/', 'd', 'a', 't', 'a', '>'};
    /**
     * The opening {@code <integer>} tag.
     */
    private static final byte[] INTEGER_OPEN = {'<', 'i', 'n', 't', 'e', 'g', 'e', 'r', '>'};
    /**
     * The closing {@code </integer>} tag.
     */
    private static final byte[] INTEGER_CLOSE = {'<', '/', 'i', 'n', 't', 'e', 'g', 'e', 'r', '>'};
    /**
     * The opening {@code <real>} tag.
     */
    private static final byte[] REAL_OPEN = {'<', 'r', 'e', 'a', 'l', '>'};
    /**
     * The closing {@code </real>} tag.
     */
    private static final byte[] REAL_CLOSE = {'<', '/', 'r', 'e', 'a', 'l', '>'};
    /**
     * The opening {@code <date>} tag.
     */
    private static final byte[] DATE_OPEN = {'<', 'd', 'a', 't', 'e', '>'};
    /**
     * The closing {@code </date>} tag.
     */
    private static final byte[] DATE_CLOSE = {'<', '/', 'd', 'a', 't', 'e', '>'};
    /**
     * The self-closing {@code <true/>} tag.
     */
    private static final byte[] TRUE_TAG = {'<', 't', 'r', 'u', 'e', '/', '>'};
    /**
     * The self-closing {@code <false/>} tag.
     */
    private static final byte[] FALSE_TAG = {'<', 'f', 'a', 'l', 's', 'e', '/', '>'};
    /**
     * The XML entity replacement for {@code &}.
     */
    private static final byte[] AMP_ENTITY = {'&', 'a', 'm', 'p', ';'};
    /**
     * The XML entity replacement for {@code <}.
     */
    private static final byte[] LT_ENTITY = {'&', 'l', 't', ';'};
    /**
     * The XML entity replacement for {@code >}.
     */
    private static final byte[] GT_ENTITY = {'&', 'g', 't', ';'};
    /**
     * The pre-encoded decimal representation of
     * {@link Long#MIN_VALUE}.
     *
     * @apiNote
     * Special-cased because {@code -Long.MIN_VALUE} overflows, so
     * the generic {@link #writeLong(byte[], int, long)} path cannot
     * negate the input.
     */
    private static final byte[] LONG_MIN_VALUE = "-9223372036854775808".getBytes(StandardCharsets.US_ASCII);
    /**
     * The standard Base64 alphabet, indexed by 6-bit value.
     */
    private static final byte[] BASE64_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".getBytes(StandardCharsets.US_ASCII);

    /**
     * Hidden constructor.
     *
     * @apiNote
     * Prevents instantiation; the class is a stateless namespace.
     */
    private PlistXmlWriter() {
    }

    /**
     * Serialises a value tree as an XML plist with the canonical
     * Apple preamble.
     *
     * @apiNote
     * The single public entry point; drives the two-pass pipeline
     * (size, fill) end to end.
     *
     * @param root the root value
     * @return the UTF-8 encoded XML bytes
     */
    public static byte[] write(PlistValue root) {
        var size = PREAMBLE.length + sizeOf(root, 0) + EPILOGUE.length;
        var out = new byte[size];
        var pos = writeBytes(out, 0, PREAMBLE);
        pos = writeValue(out, pos, root, 0);
        writeBytes(out, pos, EPILOGUE);
        return out;
    }

    /**
     * Returns the exact UTF-8 byte count required to serialise a
     * value at a given indent depth.
     *
     * @apiNote
     * Pass-one driver: recurses into containers and excludes the
     * preamble and epilogue (the caller adds those once).
     *
     * @param value  the value to size
     * @param indent the indentation depth in tabs
     * @return the byte count
     */
    private static int sizeOf(PlistValue value, int indent) {
        return switch (value) {
            case PlistDictionaryValue d -> sizeOfDict(d, indent);
            case PlistArrayValue a -> sizeOfArray(a, indent);
            case PlistStringValue s -> indent + STRING_OPEN.length + escapedXmlByteLength(s.value()) + STRING_CLOSE.length;
            case PlistDataValue d -> indent + DATA_OPEN.length + base64Length(d.length()) + DATA_CLOSE.length;
            case PlistIntegerValue i -> indent + INTEGER_OPEN.length + decimalDigitCount(i.value()) + INTEGER_CLOSE.length;
            case PlistFloatingPointValue r -> indent + REAL_OPEN.length + Double.toString(r.value()).length() + REAL_CLOSE.length;
            case PlistBooleanValue b -> indent + (b.value() ? TRUE_TAG.length : FALSE_TAG.length);
            case PlistDateValue d -> indent + DATE_OPEN.length + d.value().toString().length() + DATE_CLOSE.length;
        };
    }

    /**
     * Returns the byte count required to serialise a dictionary
     * node.
     *
     * @apiNote
     * Sized as the open tag, the indented {@code <key>} / value
     * pairs separated by newlines, and the close tag.
     *
     * @param dict   the dictionary
     * @param indent the indentation depth
     * @return the byte count
     */
    private static int sizeOfDict(PlistDictionaryValue dict, int indent) {
        var total = indent + DICT_OPEN.length;
        var childIndent = indent + 1;
        for (var entry : dict.entries().entrySet()) {
            total += childIndent + KEY_OPEN.length + escapedXmlByteLength(entry.getKey()) + KEY_CLOSE.length;
            total += sizeOf(entry.getValue(), childIndent) + 1;
        }
        total += indent + DICT_CLOSE.length;
        return total;
    }

    /**
     * Returns the byte count required to serialise an array node.
     *
     * @apiNote
     * Sized as the open tag, the indented items separated by
     * newlines, and the close tag.
     *
     * @param array  the array
     * @param indent the indentation depth
     * @return the byte count
     */
    private static int sizeOfArray(PlistArrayValue array, int indent) {
        var total = indent + ARRAY_OPEN.length;
        var childIndent = indent + 1;
        for (var item : array.items()) {
            total += sizeOf(item, childIndent) + 1;
        }
        total += indent + ARRAY_CLOSE.length;
        return total;
    }

    /**
     * Emits a value into the destination buffer.
     *
     * @apiNote
     * Pass-two driver: dispatches on the sealed {@link PlistValue}
     * hierarchy and writes directly into {@code out} starting at
     * {@code pos}.
     *
     * @param out    the destination buffer
     * @param pos    the current write position
     * @param value  the value to emit
     * @param indent the current indentation depth in tabs
     * @return the position after the value
     */
    private static int writeValue(byte[] out, int pos, PlistValue value, int indent) {
        return switch (value) {
            case PlistDictionaryValue d -> writeDict(out, pos, d, indent);
            case PlistArrayValue a -> writeArray(out, pos, a, indent);
            case PlistStringValue s -> {
                var p = writeIndent(out, pos, indent);
                p = writeBytes(out, p, STRING_OPEN);
                p = writeXmlEscaped(out, p, s.value());
                yield writeBytes(out, p, STRING_CLOSE);
            }
            case PlistDataValue d -> {
                var p = writeIndent(out, pos, indent);
                p = writeBytes(out, p, DATA_OPEN);
                p = writeBase64(out, p, d.source(), d.offset(), d.length());
                yield writeBytes(out, p, DATA_CLOSE);
            }
            case PlistIntegerValue i -> {
                var p = writeIndent(out, pos, indent);
                p = writeBytes(out, p, INTEGER_OPEN);
                p = writeLong(out, p, i.value());
                yield writeBytes(out, p, INTEGER_CLOSE);
            }
            case PlistFloatingPointValue r -> {
                var p = writeIndent(out, pos, indent);
                p = writeBytes(out, p, REAL_OPEN);
                p = writeAsciiString(out, p, Double.toString(r.value()));
                yield writeBytes(out, p, REAL_CLOSE);
            }
            case PlistBooleanValue b -> {
                var p = writeIndent(out, pos, indent);
                yield writeBytes(out, p, b.value() ? TRUE_TAG : FALSE_TAG);
            }
            case PlistDateValue d -> {
                var p = writeIndent(out, pos, indent);
                p = writeBytes(out, p, DATE_OPEN);
                p = writeAsciiString(out, p, d.value().toString());
                yield writeBytes(out, p, DATE_CLOSE);
            }
        };
    }

    /**
     * Emits a dictionary with one {@code <key>} / value pair per
     * line.
     *
     * @apiNote
     * Routes through {@link #writeXmlEscaped(byte[], int, String)}
     * for the key text so dict keys containing XML metacharacters
     * survive the round-trip.
     *
     * @param out    the destination buffer
     * @param pos    the current write position
     * @param dict   the dictionary
     * @param indent the current indentation depth
     * @return the position after the dictionary
     */
    private static int writeDict(byte[] out, int pos, PlistDictionaryValue dict, int indent) {
        pos = writeIndent(out, pos, indent);
        pos = writeBytes(out, pos, DICT_OPEN);
        var childIndent = indent + 1;
        for (var entry : dict.entries().entrySet()) {
            pos = writeIndent(out, pos, childIndent);
            pos = writeBytes(out, pos, KEY_OPEN);
            pos = writeXmlEscaped(out, pos, entry.getKey());
            pos = writeBytes(out, pos, KEY_CLOSE);
            pos = writeValue(out, pos, entry.getValue(), childIndent);
            out[pos++] = '\n';
        }
        pos = writeIndent(out, pos, indent);
        return writeBytes(out, pos, DICT_CLOSE);
    }

    /**
     * Emits an array with one entry per line.
     *
     * @param out    the destination buffer
     * @param pos    the current write position
     * @param array  the array
     * @param indent the current indentation depth
     * @return the position after the array
     */
    private static int writeArray(byte[] out, int pos, PlistArrayValue array, int indent) {
        pos = writeIndent(out, pos, indent);
        pos = writeBytes(out, pos, ARRAY_OPEN);
        var childIndent = indent + 1;
        for (var item : array.items()) {
            pos = writeValue(out, pos, item, childIndent);
            out[pos++] = '\n';
        }
        pos = writeIndent(out, pos, indent);
        return writeBytes(out, pos, ARRAY_CLOSE);
    }

    /**
     * Copies a byte array into the destination buffer.
     *
     * @apiNote
     * Used for every pre-encoded tag and entity constant.
     *
     * @param out the destination buffer
     * @param pos the current write position
     * @param src the bytes to copy
     * @return the position after the copy
     */
    private static int writeBytes(byte[] out, int pos, byte[] src) {
        System.arraycopy(src, 0, out, pos, src.length);
        return pos + src.length;
    }

    /**
     * Writes the requested number of tab characters at the current
     * position.
     *
     * @param out   the destination buffer
     * @param pos   the current write position
     * @param count the indentation depth
     * @return the position after the tabs
     */
    private static int writeIndent(byte[] out, int pos, int count) {
        for (var i = 0; i < count; i++) {
            out[pos++] = '\t';
        }
        return pos;
    }

    /**
     * Writes a string as UTF-8 with XML entity escaping.
     *
     * @apiNote
     * Used for string element content and dict key text; escapes
     * {@code &}, {@code <}, and {@code >}.
     *
     * @implNote
     * This implementation inlines the UTF-8 encoder so the writer
     * stays allocation-free: ASCII characters emit one byte,
     * two-byte BMP characters emit two, surrogate pairs decode to
     * a single 4-byte sequence, and lone high-plane characters emit
     * three bytes.
     *
     * @param out   the destination buffer
     * @param pos   the current write position
     * @param value the source string
     * @return the position after the encoded text
     */
    private static int writeXmlEscaped(byte[] out, int pos, String value) {
        for (var i = 0; i < value.length(); i++) {
            var c = value.charAt(i);
            switch (c) {
                case '&' -> pos = writeBytes(out, pos, AMP_ENTITY);
                case '<' -> pos = writeBytes(out, pos, LT_ENTITY);
                case '>' -> pos = writeBytes(out, pos, GT_ENTITY);
                default -> {
                    if (c < 0x80) {
                        out[pos++] = (byte) c;
                    } else if (c < 0x800) {
                        out[pos++] = (byte) (0xC0 | (c >> 6));
                        out[pos++] = (byte) (0x80 | (c & 0x3F));
                    } else if (Character.isHighSurrogate(c) && i + 1 < value.length()
                            && Character.isLowSurrogate(value.charAt(i + 1))) {
                        var cp = Character.toCodePoint(c, value.charAt(++i));
                        out[pos++] = (byte) (0xF0 | (cp >> 18));
                        out[pos++] = (byte) (0x80 | ((cp >> 12) & 0x3F));
                        out[pos++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
                        out[pos++] = (byte) (0x80 | (cp & 0x3F));
                    } else {
                        out[pos++] = (byte) (0xE0 | (c >> 12));
                        out[pos++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                        out[pos++] = (byte) (0x80 | (c & 0x3F));
                    }
                }
            }
        }
        return pos;
    }

    /**
     * Writes an already-ASCII string verbatim.
     *
     * @apiNote
     * Used for the small outputs of
     * {@link Double#toString(double)} and
     * {@link Instant#toString()}, neither of which can produce
     * non-ASCII characters.
     *
     * @param out the destination buffer
     * @param pos the current write position
     * @param s   the ASCII source string
     * @return the position after the bytes
     */
    private static int writeAsciiString(byte[] out, int pos, String s) {
        for (var i = 0; i < s.length(); i++) {
            out[pos++] = (byte) s.charAt(i);
        }
        return pos;
    }

    /**
     * Writes the decimal representation of an integer.
     *
     * @apiNote
     * Used for the body of every {@code <integer>} element; includes
     * a leading minus sign when {@code value} is negative.
     *
     * @implNote
     * This implementation avoids {@link Long#toString(long)} so no
     * intermediate {@link String} is allocated; the
     * {@link Long#MIN_VALUE} edge case routes through the
     * pre-encoded {@link #LONG_MIN_VALUE} constant because
     * {@code -Long.MIN_VALUE} overflows.
     *
     * @param out   the destination buffer
     * @param pos   the current write position
     * @param value the integer value
     * @return the position after the digits
     */
    private static int writeLong(byte[] out, int pos, long value) {
        if (value == Long.MIN_VALUE) {
            return writeBytes(out, pos, LONG_MIN_VALUE);
        }
        if (value == 0) {
            out[pos++] = '0';
            return pos;
        }
        var negative = value < 0;
        if (negative) {
            value = -value;
            out[pos++] = '-';
        }
        var digitCount = 0;
        var probe = value;
        while (probe > 0) {
            digitCount++;
            probe /= 10;
        }
        var end = pos + digitCount;
        var writeAt = end - 1;
        while (value > 0) {
            out[writeAt--] = (byte) ('0' + (value % 10));
            value /= 10;
        }
        return end;
    }

    /**
     * Writes the Base64 encoding of a source slice directly into
     * the destination buffer.
     *
     * @apiNote
     * Used for the body of every {@code <data>} element.
     *
     * @implNote
     * This implementation streams from {@code src} into {@code out}
     * without an intermediate slice or {@link Base64.Encoder} call;
     * the partial final group is padded with {@code '='} as
     * Base64-standard.
     *
     * @param out       the destination buffer
     * @param pos       the current write position
     * @param src       the source buffer
     * @param srcOffset the start offset within {@code src}
     * @param srcLength the number of source bytes to encode
     * @return the position after the encoded bytes
     */
    private static int writeBase64(byte[] out, int pos, byte[] src, int srcOffset, int srcLength) {
        var srcEnd = srcOffset + srcLength;
        var i = srcOffset;
        while (i + 3 <= srcEnd) {
            var b = ((src[i] & 0xFF) << 16) | ((src[i + 1] & 0xFF) << 8) | (src[i + 2] & 0xFF);
            out[pos++] = BASE64_ALPHABET[(b >> 18) & 0x3F];
            out[pos++] = BASE64_ALPHABET[(b >> 12) & 0x3F];
            out[pos++] = BASE64_ALPHABET[(b >> 6) & 0x3F];
            out[pos++] = BASE64_ALPHABET[b & 0x3F];
            i += 3;
        }
        var remaining = srcEnd - i;
        if (remaining == 1) {
            var b = (src[i] & 0xFF) << 16;
            out[pos++] = BASE64_ALPHABET[(b >> 18) & 0x3F];
            out[pos++] = BASE64_ALPHABET[(b >> 12) & 0x3F];
            out[pos++] = '=';
            out[pos++] = '=';
        } else if (remaining == 2) {
            var b = ((src[i] & 0xFF) << 16) | ((src[i + 1] & 0xFF) << 8);
            out[pos++] = BASE64_ALPHABET[(b >> 18) & 0x3F];
            out[pos++] = BASE64_ALPHABET[(b >> 12) & 0x3F];
            out[pos++] = BASE64_ALPHABET[(b >> 6) & 0x3F];
            out[pos++] = '=';
        }
        return pos;
    }

    /**
     * Returns the UTF-8 byte count required to serialise a string
     * with XML entity escaping applied.
     *
     * @apiNote
     * Pass-one helper; counts {@code &}, {@code <}, and {@code >}
     * as their entity replacement widths and the remaining
     * characters as their UTF-8 byte width.
     *
     * @param s the source string
     * @return the byte count
     */
    private static int escapedXmlByteLength(String s) {
        var len = 0;
        for (var i = 0; i < s.length(); i++) {
            var c = s.charAt(i);
            switch (c) {
                case '&' -> len += AMP_ENTITY.length;
                case '<' -> len += LT_ENTITY.length;
                case '>' -> len += GT_ENTITY.length;
                default -> {
                    if (c < 0x80) {
                        len++;
                    } else if (c < 0x800) {
                        len += 2;
                    } else if (Character.isHighSurrogate(c) && i + 1 < s.length()
                            && Character.isLowSurrogate(s.charAt(i + 1))) {
                        len += 4;
                        i++;
                    } else {
                        len += 3;
                    }
                }
            }
        }
        return len;
    }

    /**
     * Returns the length of the Base64 encoding (with padding) of a
     * source slice.
     *
     * @apiNote
     * Pass-one helper; the canonical formula
     * {@code ((n + 2) / 3) * 4} accounts for both the 4-character
     * groups and the trailing {@code '='} padding.
     *
     * @param srcLength the source length
     * @return the encoded length
     */
    private static int base64Length(int srcLength) {
        return ((srcLength + 2) / 3) * 4;
    }

    /**
     * Returns the number of characters
     * {@code Long.toString(value)} would produce.
     *
     * @apiNote
     * Pass-one helper; counts digits plus an optional leading
     * minus sign without allocating a {@link String}.
     *
     * @param value the integer value
     * @return the character count
     */
    private static int decimalDigitCount(long value) {
        if (value == Long.MIN_VALUE) {
            return LONG_MIN_VALUE.length;
        }
        var sign = value < 0 ? 1 : 0;
        var abs = value < 0 ? -value : value;
        var count = 1;
        while (abs >= 10) {
            count++;
            abs /= 10;
        }
        return count + sign;
    }
}
