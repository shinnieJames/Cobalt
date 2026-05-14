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
 * RFC 5389 §15.2 XOR-MAPPED-ADDRESS / RFC 5766 §14.5 XOR-RELAYED-ADDRESS
 * codec.
 *
 * <p>Wire format:
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
 * <p>For IPv4 (Family = 0x01) the address is 4 bytes XOR'd with the
 * top 4 bytes of the magic cookie (0x2112A442). For IPv6 (Family =
 * 0x02) the address is 16 bytes XOR'd with the magic cookie followed
 * by the 12-byte transaction id.
 *
 * <p>The X-Port is the real port XOR'd with the top 16 bits of the
 * magic cookie (0x2112).
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSctpConnectionManager")
public final class WaRelayXorAddress {
    /**
     * Address family marker for IPv4.
     */
    public static final int FAMILY_IPV4 = 0x01;

    /**
     * Address family marker for IPv6.
     */
    public static final int FAMILY_IPV6 = 0x02;

    /**
     * The decoded inet address.
     */
    private final InetAddress address;

    /**
     * The decoded port (1..65535).
     */
    private final int port;

    /**
     * Constructs a new {@code WaRelayXorAddress}.
     *
     * @param address the decoded address; must not be {@code null}
     * @param port    the decoded port (0..65535)
     * @throws NullPointerException     if {@code address} is {@code null}
     * @throws IllegalArgumentException if {@code port} is out of range
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
     * Returns the decoded {@link InetAddress}.
     *
     * @return the address
     */
    public InetAddress address() {
        return address;
    }

    /**
     * Returns the decoded port.
     *
     * @return the port (0..65535)
     */
    public int port() {
        return port;
    }

    /**
     * Decodes an XOR-MAPPED/RELAYED-ADDRESS attribute value.
     *
     * @param attrValue   the 8-byte (IPv4) or 20-byte (IPv6) attribute
     *                    value, omitting the 4-byte attr header
     * @param transactionId the packet's 12-byte transaction id; only
     *                      consulted for IPv6
     * @return the decoded address
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the value is malformed
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
     * Encodes this address back to its on-wire XOR-MAPPED-ADDRESS form.
     *
     * @param transactionId the packet's 12-byte transaction id; only
     *                      consulted for IPv6
     * @return the encoded attribute value (8 bytes for IPv4, 20 for IPv6)
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
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof WaRelayXorAddress a
                && port == a.port
                && address.equals(a.address);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(address, port);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return address.getHostAddress() + ":" + port;
    }
}
