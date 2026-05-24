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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * A multi-pass exact-allocation writer for Apple's {@code bplist00}
 * binary property-list format.
 *
 * @apiNote
 * Consumed indirectly via
 * {@link com.github.auties00.cobalt.registration.push.apns.plist.Plist#writeBinary(PlistValue)}.
 * Emits the same on-wire shape Apple's parsers ({@code CFBinaryPList.c})
 * accept: an 8-byte magic header, an object table laid out in DFS
 * order, an offset table whose entry width depends on the location
 * of the offset table itself, and a 32-byte trailer pointing at the
 * top object.
 *
 * @implNote
 * This implementation walks the tree once to enumerate every object
 * (synthesising a {@link PlistStringValue} per dictionary key, since
 * dict keys live in the object table just like values), then sizes
 * each object given the now-known reference width, computes per-
 * object offsets, sums them to locate the offset table, derives the
 * offset width, allocates a single {@code byte[]} of the exact total
 * size, and fills it in place. No object deduplication is performed;
 * equal-but-distinct values get separate slots, which Apple's
 * parsers accept.
 */
public final class PlistBinaryWriter {
    /**
     * The length in bytes of the trailer that closes a binary plist.
     */
    private static final int TRAILER_SIZE = 32;

    /**
     * Hidden constructor.
     *
     * @apiNote
     * Prevents instantiation; the class is a stateless namespace.
     */
    private PlistBinaryWriter() {
    }

    /**
     * Serialises a value tree as a {@code bplist00} binary plist.
     *
     * @apiNote
     * The single public entry point; drives the four-step pipeline
     * (collect, size, encode, trailer-fill) end to end.
     *
     * @implNote
     * This implementation computes the offset table location
     * {@code offsetTableOffset = MAGIC.length + sum(sizeOf(object))}
     * and derives {@code offsetSize} from that; this is the same
     * sequence Apple's own writer follows and is required because
     * the trailer's offset-width field must be picked after the
     * object payload is sized.
     *
     * @param root the root value
     * @return the binary plist bytes
     */
    public static byte[] write(PlistValue root) {
        var ctx = new Context();
        ctx.collect(root);
        var numObjects = ctx.objects.size();
        var refSize = byteCountFor(numObjects - 1);

        var offsets = new int[numObjects];
        var objectTableSize = 0;
        for (var i = 0; i < numObjects; i++) {
            offsets[i] = PlistBinaryParser.MAGIC.length + objectTableSize;
            objectTableSize += sizeOf(ctx.objects.get(i), refSize);
        }

        var offsetTableOffset = PlistBinaryParser.MAGIC.length + objectTableSize;
        var offsetSize = byteCountFor(offsetTableOffset);
        var trailerOffset = offsetTableOffset + numObjects * offsetSize;
        var totalSize = trailerOffset + TRAILER_SIZE;

        var out = new byte[totalSize];
        System.arraycopy(PlistBinaryParser.MAGIC, 0, out, 0, PlistBinaryParser.MAGIC.length);

        var pos = PlistBinaryParser.MAGIC.length;
        for (var i = 0; i < numObjects; i++) {
            pos = encodeObject(out, pos, ctx.objects.get(i), refSize, ctx);
        }

        for (var i = 0; i < numObjects; i++) {
            writeBigEndian(out, offsetTableOffset + i * offsetSize, offsets[i], offsetSize);
        }

        out[trailerOffset + 6] = (byte) offsetSize;
        out[trailerOffset + 7] = (byte) refSize;
        writeBigEndian(out, trailerOffset + 8, numObjects, 8);
        writeBigEndian(out, trailerOffset + 16, 0L, 8);
        writeBigEndian(out, trailerOffset + 24, offsetTableOffset, 8);

        return out;
    }

    /**
     * Returns the encoded byte length of a value.
     *
     * @apiNote
     * Used by {@link #write(PlistValue)} to lay out the object table
     * before any encode call runs.
     *
     * @implNote
     * This implementation accounts for the marker byte, any
     * extended-length suffix (when the count is at least
     * {@code 0xF}), and either the scalar payload or
     * {@code refSize} bytes per child reference for containers.
     *
     * @param v       the value
     * @param refSize the inter-object reference width
     * @return the byte count
     */
    private static int sizeOf(PlistValue v, int refSize) {
        return switch (v) {
            case PlistBooleanValue b -> 1;
            case PlistIntegerValue i -> 1 + integerWidth(i.value());
            case PlistFloatingPointValue r -> 1 + 8;
            case PlistDateValue d -> 1 + 8;
            case PlistDataValue d -> 1 + extendedLengthBytes(d.length()) + d.length();
            case PlistStringValue s -> {
                var charCount = s.value().length();
                var payloadBytes = isAscii(s.value()) ? charCount : charCount * 2;
                yield 1 + extendedLengthBytes(charCount) + payloadBytes;
            }
            case PlistArrayValue a -> 1 + extendedLengthBytes(a.items().size()) + a.items().size() * refSize;
            case PlistDictionaryValue d -> 1 + extendedLengthBytes(d.entries().size()) + 2 * d.entries().size() * refSize;
        };
    }

    /**
     * Encodes one object into the destination buffer.
     *
     * @apiNote
     * Dispatches on the sealed {@link PlistValue} hierarchy and
     * writes the marker byte plus the scalar payload (for primitives)
     * or the reference array (for containers).
     *
     * @implNote
     * This implementation reads child indices through
     * {@code ctx.indices}; for dictionaries it additionally
     * resolves the synthesised key {@link PlistStringValue}s via
     * {@code ctx.dictKeys} so the on-disk N-keys / N-values layout
     * stays consistent with the entry iteration order chosen by
     * {@link Context#collect(PlistValue)}.
     *
     * @param out     the destination buffer
     * @param pos     the current write position
     * @param v       the object to encode
     * @param refSize the inter-object reference width
     * @param ctx     the collection context resolving child
     *                references
     * @return the position after the object
     */
    private static int encodeObject(byte[] out, int pos, PlistValue v, int refSize, Context ctx) {
        return switch (v) {
            case PlistBooleanValue b -> {
                out[pos++] = (byte) (b.value() ? 0x09 : 0x08);
                yield pos;
            }
            case PlistIntegerValue i -> {
                var width = integerWidth(i.value());
                out[pos++] = (byte) (0x10 | Integer.numberOfTrailingZeros(width));
                writeBigEndian(out, pos, i.value(), width);
                yield pos + width;
            }
            case PlistFloatingPointValue r -> {
                out[pos++] = 0x23;
                writeBigEndian(out, pos, Double.doubleToLongBits(r.value()), 8);
                yield pos + 8;
            }
            case PlistDateValue d -> {
                out[pos++] = 0x33;
                var seconds = (d.value().getEpochSecond() - 978_307_200L) + d.value().getNano() / 1e9;
                writeBigEndian(out, pos, Double.doubleToLongBits(seconds), 8);
                yield pos + 8;
            }
            case PlistDataValue d -> {
                pos = writeMarkerWithLength(out, pos, 0x40, d.length());
                System.arraycopy(d.source(), d.offset(), out, pos, d.length());
                yield pos + d.length();
            }
            case PlistStringValue s -> {
                var value = s.value();
                var charCount = value.length();
                if (isAscii(value)) {
                    pos = writeMarkerWithLength(out, pos, 0x50, charCount);
                    for (var i = 0; i < charCount; i++) {
                        out[pos++] = (byte) value.charAt(i);
                    }
                } else {
                    pos = writeMarkerWithLength(out, pos, 0x60, charCount);
                    for (var i = 0; i < charCount; i++) {
                        var c = value.charAt(i);
                        out[pos++] = (byte) (c >> 8);
                        out[pos++] = (byte) c;
                    }
                }
                yield pos;
            }
            case PlistArrayValue a -> {
                pos = writeMarkerWithLength(out, pos, 0xA0, a.items().size());
                for (var item : a.items()) {
                    writeBigEndian(out, pos, ctx.indices.get(item), refSize);
                    pos += refSize;
                }
                yield pos;
            }
            case PlistDictionaryValue d -> {
                pos = writeMarkerWithLength(out, pos, 0xD0, d.entries().size());
                var keys = ctx.dictKeys.get(d);
                for (var key : keys) {
                    writeBigEndian(out, pos, ctx.indices.get(key), refSize);
                    pos += refSize;
                }
                for (var entry : d.entries().entrySet()) {
                    writeBigEndian(out, pos, ctx.indices.get(entry.getValue()), refSize);
                    pos += refSize;
                }
                yield pos;
            }
        };
    }

    /**
     * Writes a marker byte with either an inline or an extended
     * count.
     *
     * @apiNote
     * Used for every container, data, and string marker. When
     * {@code count} is below 15 the count is encoded in the low
     * nibble of the marker byte itself; otherwise the marker carries
     * the extension nibble {@code 0xF} and is followed by an integer
     * marker whose payload is the actual count.
     *
     * @param out        the destination buffer
     * @param pos        the current write position
     * @param markerBase the high nibble of the marker
     *                   (e.g. {@code 0x40} for data, {@code 0xA0} for
     *                   array)
     * @param count      the count to encode
     * @return the position after the marker
     */
    private static int writeMarkerWithLength(byte[] out, int pos, int markerBase, int count) {
        if (count < 0xF) {
            out[pos++] = (byte) (markerBase | count);
            return pos;
        }
        out[pos++] = (byte) (markerBase | 0x0F);
        var width = integerWidth(count);
        out[pos++] = (byte) (0x10 | Integer.numberOfTrailingZeros(width));
        writeBigEndian(out, pos, count, width);
        return pos + width;
    }

    /**
     * Returns the smallest power-of-two byte width that holds an
     * integer value.
     *
     * @apiNote
     * Negative values always require 8 bytes because Apple's spec
     * encodes 8-byte integers as signed two's complement;
     * non-negative values use 1, 2, 4, or 8 bytes unsigned.
     *
     * @param value the integer value
     * @return the width in bytes (1, 2, 4, or 8)
     */
    private static int integerWidth(long value) {
        if (value < 0) {
            return 8;
        }
        if (value <= 0xFFL) {
            return 1;
        }
        if (value <= 0xFFFFL) {
            return 2;
        }
        if (value <= 0xFFFF_FFFFL) {
            return 4;
        }
        return 8;
    }

    /**
     * Returns the byte length of the extended-length suffix for a
     * count.
     *
     * @apiNote
     * Used by {@link #sizeOf(PlistValue, int)} to size container
     * markers. Returns zero when the count fits in the marker's low
     * nibble; otherwise one byte for the integer marker plus the
     * integer's own bytes.
     *
     * @param count the count
     * @return the suffix size in bytes
     */
    private static int extendedLengthBytes(int count) {
        if (count < 0xF) {
            return 0;
        }
        return 1 + integerWidth(count);
    }

    /**
     * Returns the smallest power-of-two byte width sufficient to
     * encode a value unsigned.
     *
     * @apiNote
     * Used to pick the reference width (sized to the largest object
     * index) and the offset width (sized to the offset table
     * location).
     *
     * @param maxValue the largest value to be stored
     * @return the width in bytes (1, 2, 4, or 8)
     */
    private static int byteCountFor(long maxValue) {
        if (maxValue < 0) {
            return 8;
        }
        if (maxValue <= 0xFFL) {
            return 1;
        }
        if (maxValue <= 0xFFFFL) {
            return 2;
        }
        if (maxValue <= 0xFFFF_FFFFL) {
            return 4;
        }
        return 8;
    }

    /**
     * Reports whether every character of a string is in the ASCII
     * range.
     *
     * @apiNote
     * Used to choose between the {@code 0x5n} (ASCII, 1 byte per
     * code unit) and {@code 0x6n} (UTF-16BE, 2 bytes per code unit)
     * string markers.
     *
     * @param s the string
     * @return {@code true} if every character is below
     *         {@code U+0080}
     */
    private static boolean isAscii(String s) {
        for (var i = 0; i < s.length(); i++) {
            if (s.charAt(i) >= 0x80) {
                return false;
            }
        }
        return true;
    }

    /**
     * Writes a value as a fixed-width big-endian integer.
     *
     * @apiNote
     * Used for every multi-byte field the format emits (offsets,
     * references, scalar integers, the trailer fields).
     *
     * @param out       the destination buffer
     * @param pos       the start offset
     * @param value     the value to write
     * @param byteCount the byte width from 1 to 8
     */
    private static void writeBigEndian(byte[] out, int pos, long value, int byteCount) {
        for (var i = byteCount - 1; i >= 0; i--) {
            out[pos + i] = (byte) (value & 0xFF);
            value >>>= 8;
        }
    }

    /**
     * The tree-walk state shared between the sizing and the
     * encoding passes.
     *
     * @apiNote
     * Holds the DFS-ordered object table, the identity-keyed index
     * map used to resolve child references, and the synthesised key
     * {@link PlistStringValue}s for each dictionary (since dict
     * keys are themselves objects in the table).
     *
     * @implNote
     * This implementation uses {@link IdentityHashMap} so two equal
     * but distinct values get separate slots; Apple's parsers accept
     * this and the alternative (value-equality keying) would require
     * the writer to track which slot a future caller will choose.
     */
    private static final class Context {
        /**
         * The object table in DFS order; the root is at index 0.
         */
        private final List<PlistValue> objects = new ArrayList<>();

        /**
         * The identity-keyed index of each collected object.
         *
         * @apiNote
         * Identity-keyed so equal-but-distinct values do not get
         * deduplicated.
         */
        private final IdentityHashMap<PlistValue, Integer> indices = new IdentityHashMap<>();

        /**
         * The synthesised key {@link PlistStringValue}s for each
         * dictionary, in entry order.
         *
         * @apiNote
         * Dictionary keys are themselves objects in the table; the
         * writer materialises one {@link PlistStringValue} per key
         * so the encode pass can emit a uniform "N keys followed by
         * N values" reference layout.
         */
        private final IdentityHashMap<PlistDictionaryValue, List<PlistStringValue>> dictKeys = new IdentityHashMap<>();

        /**
         * Assigns an object-table index to a value and recursively
         * collects its descendants.
         *
         * @apiNote
         * Called by {@link #write(PlistValue)} on the root before
         * any sizing or encoding runs.
         *
         * @implNote
         * This implementation guards against re-collection of the
         * same instance via the {@link #indices} lookup, walks
         * arrays by item index, and walks dictionaries by entry
         * order, synthesising one key string per entry into
         * {@link #dictKeys} along the way.
         *
         * @param v the value
         * @return the assigned index
         */
        int collect(PlistValue v) {
            var existing = indices.get(v);
            if (existing != null) {
                return existing;
            }
            var idx = objects.size();
            objects.add(v);
            indices.put(v, idx);
            switch (v) {
                case PlistArrayValue arr -> {
                    for (var item : arr.items()) {
                        collect(item);
                    }
                }
                case PlistDictionaryValue dict -> {
                    var keys = new ArrayList<PlistStringValue>(dict.entries().size());
                    for (var entry : dict.entries().entrySet()) {
                        var keyObject = new PlistStringValue(entry.getKey());
                        keys.add(keyObject);
                        collect(keyObject);
                    }
                    dictKeys.put(dict, keys);
                    for (var entry : dict.entries().entrySet()) {
                        collect(entry.getValue());
                    }
                }
                default -> {
                }
            }
            return idx;
        }
    }
}
