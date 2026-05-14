package com.github.auties00.cobalt.node.binary;

import java.util.Arrays;

import static com.github.auties00.cobalt.node.binary.NodeTags.HEX_8;
import static com.github.auties00.cobalt.node.binary.NodeTags.NIBBLE_8;

/**
 * Shared lookup tables and classifier for the nibble and hex packed
 * string encodings.
 *
 * <p>Both {@link NodeSizer} and {@link NodeWriter} need to decide
 * whether a given string is eligible for the compact 4-bit-per-character
 * nibble or hex encoding, and {@link NodeWriter} additionally needs the
 * per-character lookup tables to emit the packed bytes. This class
 * centralises both so the two paths cannot drift.
 *
 * <p>The class is package-private; callers outside {@code node.binary}
 * should use {@link NodeSizer} or {@link NodeWriter}.
 */
final class NodePackedFormat {

    /**
     * Lookup table that maps an ASCII character to its 4 bit nibble code
     * or to {@code -1} when the character is outside the
     * {@code [0-9.-]} set.
     */
    static final byte[] NIBBLE_ENCODE = new byte[128];

    /**
     * Lookup table that maps an ASCII character to its 4 bit hex code or
     * to {@code -1} when the character is outside the {@code [0-9A-F]}
     * set.
     */
    static final byte[] HEX_ENCODE = new byte[128];

    static {
        Arrays.fill(NIBBLE_ENCODE, (byte) -1);
        Arrays.fill(HEX_ENCODE, (byte) -1);
        for (var i = 0; i <= 9; i++) {
            NIBBLE_ENCODE['0' + i] = (byte) i;
            HEX_ENCODE['0' + i] = (byte) i;
        }
        NIBBLE_ENCODE['-'] = 10;
        NIBBLE_ENCODE['.'] = 11;
        for (var i = 0; i < 6; i++) {
            HEX_ENCODE['A' + i] = (byte) (10 + i);
        }
    }

    /**
     * Prevents instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private NodePackedFormat() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Determines whether the supplied string is eligible for nibble or
     * hex packed encoding.
     *
     * @param input the string to inspect
     * @return {@link NodeTags#NIBBLE_8} if every character is in the
     *         nibble alphabet, {@link NodeTags#HEX_8} if every character
     *         is in the hex alphabet, or {@code -1} when neither shape
     *         applies
     */
    static byte getPackedType(String input) {
        var nibble = true;
        var hex = true;
        for (var i = 0; i < input.length(); i++) {
            var ch = input.charAt(i);
            if (ch >= 128 || NIBBLE_ENCODE[ch] < 0) {
                nibble = false;
            }
            if (ch >= 128 || HEX_ENCODE[ch] < 0) {
                hex = false;
            }
            if (!nibble && !hex) {
                return -1;
            }
        }
        if (nibble) {
            return NIBBLE_8;
        } else {
            return HEX_8;
        }
    }
}