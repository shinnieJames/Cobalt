package com.github.auties00.cobalt.call.internal.transport.relay;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.util.Arrays;
import java.util.Objects;

/**
 * A single TLV attribute inside a {@link WaRelayPacket}.
 *
 * <p>The wire encoding is {@code [type:u16, length:u16, value,
 * padding-to-4]}. This class holds the type code and the raw value
 * bytes only; higher-level parsing (e.g. of an {@code XOR-RELAYED-ADDRESS}
 * IPv4/IPv6 endpoint, or a {@link WaRelayAttributeType#WA_CALL_INFO}
 * protobuf payload) is the caller's responsibility.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSctpConnectionManager")
public final class WaRelayAttribute {
    /**
     * The 16-bit attribute type code.
     */
    private final int type;

    /**
     * The attribute value bytes (without padding).
     */
    private final byte[] value;

    /**
     * Constructs a new attribute.
     *
     * @param type  the 16-bit attribute type code
     * @param value the value bytes; must not be {@code null}
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public WaRelayAttribute(int type, byte[] value) {
        Objects.requireNonNull(value, "value cannot be null");
        this.type = type;
        this.value = value.clone();
    }

    /**
     * Returns the 16-bit attribute type code.
     *
     * @return the wire-level attribute type
     */
    public int type() {
        return type;
    }

    /**
     * Returns the resolved {@link WaRelayAttributeType} when known, or
     * {@code null} for unrecognised codes.
     *
     * @return the resolved attribute type, or {@code null}
     */
    public WaRelayAttributeType resolvedType() {
        return WaRelayAttributeType.ofWire(type);
    }

    /**
     * Returns a defensive copy of the raw value bytes.
     *
     * @return a fresh copy of the value
     */
    public byte[] value() {
        return value.clone();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof WaRelayAttribute a
                && type == a.type
                && Arrays.equals(value, a.value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(type, Arrays.hashCode(value));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        var rt = resolvedType();
        return "WaRelayAttribute[type=0x" + Integer.toHexString(type)
                + (rt != null ? " (" + rt + ")" : "")
                + ", len=" + value.length + ']';
    }
}
