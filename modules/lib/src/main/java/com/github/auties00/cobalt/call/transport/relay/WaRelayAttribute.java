package com.github.auties00.cobalt.call.transport.relay;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.util.Arrays;
import java.util.Objects;

/**
 * Holds one type-length-value attribute inside a {@link WaRelayPacket}.
 *
 * <p>The attribute is encoded on the wire as a 16-bit type, a 16-bit value length, the value bytes,
 * and zero padding up to a 4-byte boundary. This class retains only the type code and the unpadded
 * value bytes; interpreting the value, such as parsing an {@link WaRelayAttributeType#XOR_RELAYED_ADDRESS}
 * endpoint or a {@link WaRelayAttributeType#WA_CALL_INFO} protobuf payload, is left to the caller.
 * The value bytes are defensively copied on construction and on {@link #value()}.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSctpConnectionManager")
public final class WaRelayAttribute {
    /**
     * Holds the 16-bit attribute-type code.
     */
    private final int type;

    /**
     * Holds the unpadded attribute value bytes.
     */
    private final byte[] value;

    /**
     * Constructs an attribute from a type code and its value bytes.
     *
     * <p>The {@code value} array is defensively copied, so later mutation of the caller's array does
     * not affect this attribute.
     *
     * @param type  the 16-bit attribute-type code
     * @param value the value bytes; must not be {@code null}
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public WaRelayAttribute(int type, byte[] value) {
        Objects.requireNonNull(value, "value cannot be null");
        this.type = type;
        this.value = value.clone();
    }

    /**
     * Returns the 16-bit attribute-type code.
     *
     * @return the wire-level attribute type
     */
    public int type() {
        return type;
    }

    /**
     * Resolves this attribute's type code to a {@link WaRelayAttributeType} constant.
     *
     * <p>Returns {@code null} for codes not enumerated by {@link WaRelayAttributeType}, which the
     * caller should treat as comprehension-optional.
     *
     * @return the resolved attribute type, or {@code null} when the code is unrecognised
     */
    public WaRelayAttributeType resolvedType() {
        return WaRelayAttributeType.ofWire(type);
    }

    /**
     * Returns a defensive copy of the unpadded value bytes.
     *
     * @return a fresh copy of the value
     */
    public byte[] value() {
        return value.clone();
    }

    /**
     * Compares this attribute with another for type-code and value-byte equality.
     *
     * @param o the object to compare against
     * @return {@code true} when {@code o} is a {@code WaRelayAttribute} with the same type code and
     *         the same value bytes
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof WaRelayAttribute a
                && type == a.type
                && Arrays.equals(value, a.value);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}, combining the type code and the
     * value bytes.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(type, Arrays.hashCode(value));
    }

    /**
     * Returns a diagnostic string carrying the hex type code, its resolved name when known, and the
     * value length.
     *
     * @return a human-readable representation
     */
    @Override
    public String toString() {
        var rt = resolvedType();
        return "WaRelayAttribute[type=0x" + Integer.toHexString(type)
                + (rt != null ? " (" + rt + ")" : "")
                + ", len=" + value.length + ']';
    }
}
