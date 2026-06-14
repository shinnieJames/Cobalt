package com.github.auties00.cobalt.call.transport.relay;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.util.DataUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Codes and decodes the STUN {@code XOR-MAPPED-ADDRESS} (RFC 5389 section 15.2) and TURN
 * {@code XOR-RELAYED-ADDRESS} (RFC 5766 section 14.5) attribute value of a {@link WaRelayPacket}.
 *
 * <p>The attribute value is an address-family-tagged endpoint whose address and port are obfuscated
 * by XOR with the packet's magic cookie. An instance holds the plaintext {@link #address()} and
 * {@link #port()}; {@link #decode(byte[], byte[])} reads the obfuscated value into one and
 * {@link #encode(byte[])} produces the obfuscated value from one.
 *
 * <pre>{@code
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |0 0 0 0 0 0 0 0|    Family     |         X-Port                |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                X-Address (Variable)
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * }</pre>
 *
 * @implNote This implementation reproduces the RFC 5389 XOR obfuscation exactly. The first byte is
 * reserved zero and the second is the family ({@link #FAMILY_IPV4} or {@link #FAMILY_IPV6}). X-Port
 * is the real port XOR'd with the top 16 bits of {@link WaRelayPacket#MAGIC_COOKIE}
 * ({@code 0x2112}). For IPv4 the 4 address bytes are XOR'd with the full 32-bit magic cookie; for
 * IPv6 the first 4 address bytes are XOR'd with the magic cookie and the remaining 12 with the
 * packet's transaction id.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSctpConnectionManager")
public final class WaRelayXorAddress {
    /**
     * Holds the address-family marker for IPv4.
     */
    public static final int FAMILY_IPV4 = 0x01;

    /**
     * Holds the address-family marker for IPv6.
     */
    public static final int FAMILY_IPV6 = 0x02;

    /**
     * Holds the plaintext inet address.
     */
    private final InetAddress address;

    /**
     * Holds the plaintext port in the range 0 to 65535.
     */
    private final int port;

    /**
     * Constructs an address from a plaintext inet address and port.
     *
     * @param address the inet address; must not be {@code null}
     * @param port    the port in the range 0 to 65535
     * @throws NullPointerException     if {@code address} is {@code null}
     * @throws IllegalArgumentException if {@code port} is outside the range 0 to 65535
     */
    public WaRelayXorAddress(InetAddress address, int port) {
        Objects.requireNonNull(address, "address cannot be null");
        if (port < 0 || port > 0xFFFF) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        this.address = address;
        this.port = port;
    }

    /**
     * Returns the plaintext inet address.
     *
     * @return the address
     */
    public InetAddress address() {
        return address;
    }

    /**
     * Returns the plaintext port.
     *
     * @return the port in the range 0 to 65535
     */
    public int port() {
        return port;
    }

    /**
     * Decodes an XOR-mapped or XOR-relayed address attribute value.
     *
     * <p>Verifies the reserved leading byte is zero, reads the address family, de-obfuscates the port
     * against the top 16 bits of {@link WaRelayPacket#MAGIC_COOKIE}, and de-obfuscates the address
     * bytes. For an IPv4 family the address is the 4 bytes XOR'd with the magic cookie; for an IPv6
     * family it is the 16 bytes XOR'd with the magic cookie followed by the {@code transactionId}.
     *
     * @param attrValue     the attribute value with the 4-byte attribute header already stripped: 8
     *                      bytes for IPv4, 20 bytes for IPv6
     * @param transactionId the packet's 12-byte transaction id, consulted only for the IPv6 family
     * @return the decoded address
     * @throws NullPointerException     if {@code attrValue} or {@code transactionId} is {@code null}
     * @throws IllegalArgumentException if the value is shorter than 4 bytes, its reserved byte is
     *                                  non-zero, the family is unknown, the length does not match the
     *                                  family, or the address bytes do not form a valid address
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.DIRECT)
    public static WaRelayXorAddress decode(byte[] attrValue, byte[] transactionId) {
        Objects.requireNonNull(attrValue, "attrValue cannot be null");
        Objects.requireNonNull(transactionId, "transactionId cannot be null");
        if (attrValue.length < 4) {
            throw new IllegalArgumentException("XOR-ADDRESS attr too short: " + attrValue.length);
        }
        if (attrValue[0] != 0) {
            throw new IllegalArgumentException("XOR-ADDRESS reserved byte != 0");
        }
        var family = attrValue[1] & 0xFF;
        var xPort = DataUtils.getShort(attrValue, 2, ByteOrder.BIG_ENDIAN) & 0xFFFF;
        var port = xPort ^ (WaRelayPacket.MAGIC_COOKIE >>> 16);

        return switch (family) {
            case FAMILY_IPV4 -> {
                if (attrValue.length != 8) {
                    throw new IllegalArgumentException("IPv4 XOR-ADDRESS must be 8 bytes, got " + attrValue.length);
                }
                var addrBytes = new byte[4];
                System.arraycopy(attrValue, 4, addrBytes, 0, 4);
                DataUtils.putInt(addrBytes, 0,
                        DataUtils.getInt(addrBytes, 0, ByteOrder.BIG_ENDIAN) ^ WaRelayPacket.MAGIC_COOKIE,
                        ByteOrder.BIG_ENDIAN);
                try {
                    yield new WaRelayXorAddress(InetAddress.getByAddress(addrBytes), port);
                } catch (UnknownHostException e) {
                    throw new IllegalArgumentException("invalid IPv4 address", e);
                }
            }
            case FAMILY_IPV6 -> {
                if (attrValue.length != 20) {
                    throw new IllegalArgumentException("IPv6 XOR-ADDRESS must be 20 bytes, got " + attrValue.length);
                }
                var addrBytes = new byte[16];
                System.arraycopy(attrValue, 4, addrBytes, 0, 16);
                DataUtils.putInt(addrBytes, 0,
                        DataUtils.getInt(addrBytes, 0, ByteOrder.BIG_ENDIAN) ^ WaRelayPacket.MAGIC_COOKIE,
                        ByteOrder.BIG_ENDIAN);
                for (var i = 0; i < 12; i++) {
                    addrBytes[i + 4] ^= transactionId[i];
                }
                try {
                    yield new WaRelayXorAddress(InetAddress.getByAddress(addrBytes), port);
                } catch (UnknownHostException e) {
                    throw new IllegalArgumentException("invalid IPv6 address", e);
                }
            }
            default -> throw new IllegalArgumentException("unknown address family: 0x" + Integer.toHexString(family));
        };
    }

    /**
     * Encodes this address to its on-wire XOR-mapped-address attribute value.
     *
     * <p>Tags the family from the address length, obfuscates the address bytes against
     * {@link WaRelayPacket#MAGIC_COOKIE} (and, for IPv6, against {@code transactionId} for the
     * trailing 12 bytes), and obfuscates the port against the top 16 bits of the magic cookie.
     *
     * @param transactionId the packet's 12-byte transaction id, consulted only for the IPv6 family
     * @return the encoded attribute value: 8 bytes for IPv4, 20 bytes for IPv6
     * @throws NullPointerException if {@code transactionId} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.DIRECT)
    public byte[] encode(byte[] transactionId) {
        Objects.requireNonNull(transactionId, "transactionId cannot be null");
        var addrBytes = address.getAddress().clone();
        var family = addrBytes.length == 4 ? FAMILY_IPV4 : FAMILY_IPV6;
        DataUtils.putInt(addrBytes, 0,
                DataUtils.getInt(addrBytes, 0, ByteOrder.BIG_ENDIAN) ^ WaRelayPacket.MAGIC_COOKIE,
                ByteOrder.BIG_ENDIAN);
        if (family == FAMILY_IPV6) {
            for (var i = 0; i < 12; i++) {
                addrBytes[i + 4] ^= transactionId[i];
            }
        }
        var result = new byte[4 + addrBytes.length];
        result[1] = (byte) family;
        DataUtils.putShort(result, 2, (short) (port ^ (WaRelayPacket.MAGIC_COOKIE >>> 16)), ByteOrder.BIG_ENDIAN);
        System.arraycopy(addrBytes, 0, result, 4, addrBytes.length);
        return result;
    }

    /**
     * Compares this address with another for address and port equality.
     *
     * @param o the object to compare against
     * @return {@code true} when {@code o} is a {@code WaRelayXorAddress} with the same address and
     *         port
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof WaRelayXorAddress a
                && port == a.port
                && address.equals(a.address);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}, combining the address and port.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(address, port);
    }

    /**
     * Returns a {@code host:port} diagnostic string using the address's textual host form.
     *
     * @return a human-readable representation
     */
    @Override
    public String toString() {
        return address.getHostAddress() + ":" + port;
    }
}
