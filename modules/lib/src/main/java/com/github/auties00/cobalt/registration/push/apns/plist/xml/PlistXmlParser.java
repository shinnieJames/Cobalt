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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;

/**
 * A recursive-descent parser for Apple's XML property-list format.
 *
 * @apiNote
 * Tailored to the small subset Cobalt's APNS code exchanges with
 * Apple's activation and bag endpoints; consumed indirectly via
 * {@link com.github.auties00.cobalt.registration.push.apns.plist.Plist#parse(byte[])}.
 * The recognised element set is exactly
 * {@code true / false / string / integer / real / data / date /
 * dict / array}; anything else surfaces as {@link IOException}.
 *
 * @implNote
 * This implementation runs single-pass over the source
 * {@code byte[]} (no {@link java.io.Reader} wrappers) and tolerates
 * the {@code <?xml?>} prolog, a single {@code <!DOCTYPE>}, and
 * {@code <!-- ... -->} comments interleaved between elements;
 * namespaces and DTD validation are deliberately not honoured
 * because Apple's plist DTD has been stable since 2002 and no
 * production plist Cobalt parses uses either feature.
 */
public final class PlistXmlParser {
    /**
     * The source bytes.
     */
    private final byte[] src;

    /**
     * The current read position within {@link #src}.
     */
    private int pos;

    /**
     * Constructs a parser bound to a source buffer.
     *
     * @apiNote
     * Private; callers must route through {@link #parse(byte[])}.
     *
     * @param src the source bytes
     */
    private PlistXmlParser(byte[] src) {
        this.src = src;
    }

    /**
     * Parses the source bytes into a {@link PlistValue} tree.
     *
     * @apiNote
     * Entry point; constructs an internal parser instance and drives
     * the root parse.
     *
     * @param data the XML plist bytes
     * @return the parsed root value
     * @throws IOException if the source is malformed
     */
    public static PlistValue parse(byte[] data) throws IOException {
        return new PlistXmlParser(data).parseRoot();
    }

    /**
     * Drives the parse from the start of the source through the
     * {@code <plist>} root.
     *
     * @apiNote
     * Skips the {@code <?xml?>} prolog and {@code <!DOCTYPE>}
     * preamble, optionally opens the {@code <plist>} wrapper, then
     * delegates to {@link #parseValue()} for the single child.
     *
     * @return the root value
     * @throws IOException if the source is malformed
     */
    private PlistValue parseRoot() throws IOException {
        skipMisc();
        if (matchAt("<plist")) {
            pos = indexOf('>') + 1;
            skipMisc();
        }
        return parseValue();
    }

    /**
     * Reads exactly one value element starting at the current
     * position.
     *
     * @apiNote
     * Dispatches on the opening element name through a
     * {@code switch}; self-closing variants (e.g. {@code <true/>},
     * {@code <dict/>}) are tolerated and yield the empty equivalent.
     *
     * @return the parsed value
     * @throws IOException if the element name is unknown or the
     *                     element body is malformed
     */
    private PlistValue parseValue() throws IOException {
        skipMisc();
        if (pos >= src.length || src[pos] != '<') {
            throw new IOException("expected '<' at " + pos);
        }
        pos++;
        var tagStart = pos;
        while (pos < src.length) {
            var c = src[pos];
            if (c == '>' || c == '/' || c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                break;
            }
            pos++;
        }
        var tagName = new String(src, tagStart, pos - tagStart, StandardCharsets.US_ASCII);
        var selfClose = false;
        while (pos < src.length && src[pos] != '>') {
            if (src[pos] == '/') {
                selfClose = true;
            }
            pos++;
        }
        if (pos >= src.length) {
            throw new IOException("unterminated tag <" + tagName);
        }
        pos++;
        return switch (tagName) {
            case "true" -> {
                if (!selfClose) {
                    consumeClose("true");
                }
                yield new PlistBooleanValue(true);
            }
            case "false" -> {
                if (!selfClose) {
                    consumeClose("false");
                }
                yield new PlistBooleanValue(false);
            }
            case "string" -> selfClose
                    ? new PlistStringValue("")
                    : new PlistStringValue(readTextAndClose("string"));
            case "integer" -> new PlistIntegerValue(Long.parseLong(readTextAndClose("integer").trim()));
            case "real" -> new PlistFloatingPointValue(Double.parseDouble(readTextAndClose("real").trim()));
            case "data" -> {
                if (selfClose) {
                    yield new PlistDataValue(new byte[0], 0, 0);
                }
                var raw = readTextAndClose("data");
                var decoded = Base64.getMimeDecoder().decode(raw);
                yield new PlistDataValue(decoded, 0, decoded.length);
            }
            case "date" -> new PlistDateValue(Instant.parse(readTextAndClose("date").trim()));
            case "dict" -> selfClose ? new PlistDictionaryValue(new LinkedHashMap<>()) : parseDict();
            case "array" -> selfClose ? new PlistArrayValue(new ArrayList<>()) : parseArray();
            default -> throw new IOException("unknown plist element <" + tagName + ">");
        };
    }

    /**
     * Reads alternating {@code <key>} / value pairs until the
     * closing {@code </dict>}.
     *
     * @apiNote
     * Drives the dict parse after {@link #parseValue()} has consumed
     * the opening {@code <dict>}.
     *
     * @implNote
     * This implementation uses {@link LinkedHashMap} so the source
     * order of the dict entries survives the round-trip.
     *
     * @return the parsed dictionary
     * @throws IOException if the contents are malformed
     */
    private PlistDictionaryValue parseDict() throws IOException {
        var entries = new LinkedHashMap<String, PlistValue>();
        while (true) {
            skipMisc();
            if (matchAt("</dict>")) {
                pos += "</dict>".length();
                return new PlistDictionaryValue(entries);
            }
            if (!matchAt("<key>")) {
                throw new IOException("expected <key> at " + pos);
            }
            pos += "<key>".length();
            var keyStart = pos;
            while (pos < src.length && src[pos] != '<') {
                pos++;
            }
            var key = decodeEntities(new String(src, keyStart, pos - keyStart, StandardCharsets.UTF_8));
            if (!matchAt("</key>")) {
                throw new IOException("expected </key> at " + pos);
            }
            pos += "</key>".length();
            entries.put(key, parseValue());
        }
    }

    /**
     * Reads values until the closing {@code </array>}.
     *
     * @apiNote
     * Drives the array parse after {@link #parseValue()} has
     * consumed the opening {@code <array>}.
     *
     * @return the parsed array
     * @throws IOException if the contents are malformed
     */
    private PlistArrayValue parseArray() throws IOException {
        var items = new ArrayList<PlistValue>();
        while (true) {
            skipMisc();
            if (matchAt("</array>")) {
                pos += "</array>".length();
                return new PlistArrayValue(items);
            }
            items.add(parseValue());
        }
    }

    /**
     * Reads the text content of an element up to the next
     * {@code <}, then consumes the matching closing tag.
     *
     * @apiNote
     * Used by the {@code string}, {@code integer}, {@code real},
     * {@code data}, and {@code date} parsers.
     *
     * @param tag the element name (used to validate the close)
     * @return the entity-decoded text content
     * @throws IOException if the close tag is missing
     */
    private String readTextAndClose(String tag) throws IOException {
        var start = pos;
        while (pos < src.length && src[pos] != '<') {
            pos++;
        }
        var raw = new String(src, start, pos - start, StandardCharsets.UTF_8);
        consumeClose(tag);
        return decodeEntities(raw);
    }

    /**
     * Consumes a closing tag at the current position.
     *
     * @apiNote
     * Helper for the value parsers; throws when the expected close
     * is not present rather than tolerating mismatches.
     *
     * @param tag the element name
     * @throws IOException if the expected close tag is not present
     */
    private void consumeClose(String tag) throws IOException {
        var close = "</" + tag + ">";
        if (!matchAt(close)) {
            throw new IOException("expected " + close + " at " + pos);
        }
        pos += close.length();
    }

    /**
     * Skips whitespace, XML processing instructions,
     * {@code <!DOCTYPE>} declarations and comments.
     *
     * @apiNote
     * Called before every value read so the parser tolerates
     * arbitrary whitespace and comment interleaving.
     *
     * @implNote
     * This implementation loops until a non-skip token is observed;
     * processing instructions and comments are required to be
     * terminated by their canonical {@code ?>} / {@code -->}
     * trailer respectively.
     *
     * @throws IOException if a processing instruction or comment is
     *                     unterminated
     */
    private void skipMisc() throws IOException {
        while (pos < src.length) {
            var c = src[pos];
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
                continue;
            }
            if (matchAt("<?")) {
                var end = indexOfPair("?>");
                if (end < 0) {
                    throw new IOException("unterminated <?...?>");
                }
                pos = end + 2;
                continue;
            }
            if (matchAt("<!--")) {
                var end = indexOfPair("-->");
                if (end < 0) {
                    throw new IOException("unterminated <!-- -->");
                }
                pos = end + 3;
                continue;
            }
            if (matchAt("<!DOCTYPE") || matchAt("<!doctype")) {
                pos = indexOf('>') + 1;
                continue;
            }
            break;
        }
    }

    /**
     * Reports whether a literal appears at the current read
     * position.
     *
     * @apiNote
     * Helper used by every keyword-driven branch of the parser.
     *
     * @param expected the literal to test
     * @return {@code true} if {@code expected} starts at {@link #pos}
     */
    private boolean matchAt(String expected) {
        if (pos + expected.length() > src.length) {
            return false;
        }
        for (var i = 0; i < expected.length(); i++) {
            if (src[pos + i] != (byte) expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the next byte position of a single character starting
     * from the current read position.
     *
     * @apiNote
     * Helper used to skip past {@code <plist ...>} attributes and
     * {@code <!DOCTYPE ...>} declarations to the closing
     * {@code >}.
     *
     * @param target the byte to find
     * @return the index, or {@code -1} if the source ends first
     */
    private int indexOf(char target) {
        for (var i = pos; i < src.length; i++) {
            if (src[i] == target) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the next position at which a multi-character sequence
     * starts.
     *
     * @apiNote
     * Helper used to locate {@code ?>} / {@code -->} terminators
     * during {@link #skipMisc()}.
     *
     * @param pair the two-or-more character sequence
     * @return the start index, or {@code -1} if the source ends
     *         first
     */
    private int indexOfPair(String pair) {
        outer:
        for (var i = pos; i + pair.length() <= src.length; i++) {
            for (var j = 0; j < pair.length(); j++) {
                if (src[i + j] != (byte) pair.charAt(j)) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /**
     * Decodes the XML entities that may appear in plist payloads.
     *
     * @apiNote
     * Recognises {@code &amp;}, {@code &lt;}, {@code &gt;},
     * {@code &quot;}, {@code &apos;}, and numeric character
     * references in both decimal ({@code &#NN;}) and hexadecimal
     * ({@code &#xNN;}) forms. Untranslated entities are passed
     * through verbatim so non-standard payloads still survive the
     * round-trip.
     *
     * @implNote
     * This implementation fast-paths the common case (no
     * {@code &}) by returning the input unchanged; otherwise it
     * walks character-by-character into a {@link StringBuilder}.
     *
     * @param raw the raw text
     * @return the decoded text
     */
    private static String decodeEntities(String raw) {
        if (raw.indexOf('&') < 0) {
            return raw;
        }
        var sb = new StringBuilder(raw.length());
        var i = 0;
        while (i < raw.length()) {
            var c = raw.charAt(i);
            if (c != '&') {
                sb.append(c);
                i++;
                continue;
            }
            var semi = raw.indexOf(';', i);
            if (semi < 0) {
                sb.append(raw, i, raw.length());
                break;
            }
            var name = raw.substring(i + 1, semi);
            switch (name) {
                case "amp" -> sb.append('&');
                case "lt" -> sb.append('<');
                case "gt" -> sb.append('>');
                case "quot" -> sb.append('"');
                case "apos" -> sb.append('\'');
                default -> {
                    if (name.startsWith("#x") || name.startsWith("#X")) {
                        sb.appendCodePoint(Integer.parseInt(name, 2, name.length(), 16));
                    } else if (name.startsWith("#")) {
                        sb.appendCodePoint(Integer.parseInt(name, 1, name.length(), 10));
                    } else {
                        sb.append('&').append(name).append(';');
                    }
                }
            }
            i = semi + 1;
        }
        return sb.toString();
    }
}
