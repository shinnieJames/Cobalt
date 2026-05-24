package com.github.auties00.cobalt.registration.push.apns.plist.value;

import java.util.Arrays;
import java.util.Objects;

/**
 * Plist {@code <data>} leaf, the binary plist {@code 0x40..0x4F}
 * marker family, stored as a slice over a backing buffer.
 *
 * @apiNote
 * Used wherever an APNS plist payload carries opaque bytes (for
 * example the FairPlay token or the device push token blob); call
 * {@link #toByteArray()} to obtain a defensive copy when ownership
 * outside the parser is required.
 *
 * @implNote
 * This implementation retains the parser's source buffer by reference
 * to allow zero-copy decoding of binary plists; mutating
 * {@link #source()} after construction would silently corrupt the
 * stored value.
 *
 * @param source the backing buffer
 * @param offset the start offset within {@code source}
 * @param length the number of bytes
 */
public record PlistDataValue(byte[] source, int offset, int length) implements PlistValue {
    /**
     * Canonical constructor that validates the slice bounds.
     *
     * @apiNote
     * The constructor enforces {@code 0 <= offset && offset + length <= source.length}
     * via {@link Objects#checkFromIndexSize(int, int, int)}.
     *
     * @param source the backing buffer
     * @param offset the start offset within {@code source}
     * @param length the number of bytes
     * @throws NullPointerException      if {@code source} is {@code null}
     * @throws IndexOutOfBoundsException if the slice escapes the buffer
     */
    public PlistDataValue {
        Objects.requireNonNull(source, "source");
        Objects.checkFromIndexSize(offset, length, source.length);
    }

    /**
     * Wraps {@code bytes} as a full-buffer {@code PlistDataValue}
     * without copying.
     *
     * @apiNote
     * Convenience for callers that own the backing array and want the
     * whole array exposed as a data leaf; the caller must not mutate
     * {@code bytes} after this call returns.
     *
     * @param bytes the source bytes
     */
    public PlistDataValue(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    /**
     * Returns a freshly allocated copy of the stored bytes.
     *
     * @apiNote
     * Use when the caller needs an independent buffer it can mutate or
     * persist outside the lifetime of the parser source.
     *
     * @return a copy of the slice
     */
    public byte[] toByteArray() {
        return Arrays.copyOfRange(source, offset, offset + length);
    }
}
