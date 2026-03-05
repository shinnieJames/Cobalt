package com.github.auties00.cobalt.socket.layer.tunnel.socks;

import com.github.auties00.cobalt.client.WhatsAppClientProxy;
import com.github.auties00.cobalt.client.WhatsAppClientProxyAuthenticator;
import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.SocketClientLayerListener;
import com.github.auties00.cobalt.socket.layer.threading.SocketClientLayerContext;
import com.github.auties00.cobalt.socket.layer.tunnel.SocketClientTunnelLayer;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * A SOCKS tunnel layer for proxied socket connections.
 *
 * <p>Supports SOCKS4 (local DNS, IPv4 only), SOCKS4a (remote DNS via
 * sentinel IP), SOCKS5 with local DNS resolution (RFC 1928), and SOCKS5h
 * with remote DNS resolution.  SOCKS5 authentication is handled via
 * RFC 1929 username/password sub-negotiation.
 *
 * <p>This layer only performs the SOCKS handshake.  It does <b>not</b>
 * call {@code markReady()} — that is the responsibility of the caller
 * (typically a tunnel security layer or the layer stack assembly code).
 *
 * @apiNote This layer directly depends on
 * {@link WhatsAppClientProxy.Socks} for proxy configuration.  This
 * coupling is intentional for this project.  If the socket stack is
 * extracted as a standalone library, a generic proxy configuration
 * interface should be introduced to replace the direct dependency.
 */
public final class SocksSocketClientTunnelLayer implements SocketClientTunnelLayer {
    private static final byte SOCKS_VERSION_4 = 0x04;
    private static final byte SOCKS_VERSION_5 = 0x05;
    private static final byte CMD_CONNECT = 0x01;
    private static final byte METHOD_NO_AUTH = 0x00;
    private static final int METHOD_NO_ACCEPTABLE = 0xFF;
    private static final byte ADDR_TYPE_IPV4 = 0x01;
    private static final byte ADDR_TYPE_DOMAIN = 0x03;
    private static final byte ADDR_TYPE_IPV6 = 0x04;
    private static final byte AUTH_VERSION_1 = 0x01;
    private static final byte AUTH_SUCCESS = 0x00;
    private static final byte SOCKS5_REPLY_SUCCESS = 0x00;
    private static final int SOCKS5_REPLY_GENERAL_FAILURE = 0x01;
    private static final int SOCKS5_REPLY_CONNECTION_NOT_ALLOWED = 0x02;
    private static final int SOCKS5_REPLY_NETWORK_UNREACHABLE = 0x03;
    private static final int SOCKS5_REPLY_HOST_UNREACHABLE = 0x04;
    private static final int SOCKS5_REPLY_CONNECTION_REFUSED = 0x05;
    private static final int SOCKS5_REPLY_TTL_EXPIRED = 0x06;
    private static final int SOCKS5_REPLY_COMMAND_NOT_SUPPORTED = 0x07;
    private static final int SOCKS5_REPLY_ADDRESS_TYPE_UNSUPPORTED = 0x08;
    private static final byte SOCKS5_RESERVED = 0x00;
    private static final int SOCKS4_REPLY_VERSION = 0x00;
    private static final int SOCKS4_REQUEST_GRANTED = 0x5A;
    private static final int SOCKS4_REQUEST_REJECTED = 0x5B;
    private static final int SOCKS4_REQUEST_NO_IDENTD = 0x5C;
    private static final int SOCKS4_REQUEST_IDENTD_MISMATCH = 0x5D;
    private static final byte SOCKS4_NULL_TERMINATOR = 0x00;
    private static final byte[] SOCKS4_EMPTY_USER_ID = new byte[0];
    private static final byte[] SOCKS4A_SENTINEL_IP = {0x00, 0x00, 0x00, 0x01};
    private static final int IPV4_ADDR_LENGTH = 4;
    private static final int IPV6_ADDR_LENGTH = 16;
    private static final int PORT_LENGTH = 2;
    private static final int MAX_DOMAIN_LENGTH = 255;

    /**
     * The SOCKS proxy configuration.
     */
    private final WhatsAppClientProxy.Socks proxy;

    /**
     * The inner layer that provides raw I/O.
     */
    private final SocketClientLayer innerLayer;

    /**
     * The target host for the CONNECT request.
     */
    private String host;

    /**
     * The target port for the CONNECT request.
     */
    private int port;

    /**
     * Creates a SOCKS tunnel layer wrapping the given inner layer.
     *
     * @param proxy      the SOCKS proxy configuration
     * @param innerLayer the layer below (typically a transport layer)
     */
    public SocksSocketClientTunnelLayer(WhatsAppClientProxy.Socks proxy, SocketClientLayer innerLayer) {
        this.proxy = proxy;
        this.innerLayer = innerLayer;
    }

    /**
     * Connects through the SOCKS proxy to the specified target endpoint.
     *
     * <p>Opens a connection to the proxy server via the inner layer and
     * performs the appropriate SOCKS handshake (4, 4a, 5, or 5h) based
     * on the proxy configuration.
     *
     * <p>After this method returns successfully, the tunnel is established
     * but the connection is not yet marked ready for application data.
     * The caller is responsible for calling {@code markReady()}.
     *
     * @param address  the target endpoint for the tunnel
     * @param listener the callback for events (not used during handshake)
     * @throws IOException if the connection or handshake fails
     */
    @Override
    public void connect(InetSocketAddress address, SocketClientLayerListener listener) throws IOException {
        this.host = address.getHostString();
        this.port = address.getPort();

        innerLayer.connect(
                new InetSocketAddress(proxy.host(), proxy.port()),
                listener
        );

        switch (proxy) {
            case WhatsAppClientProxy.Socks.V4.Local v4 -> performSocks4Handshake(v4);
            case WhatsAppClientProxy.Socks.V4.Remote v4a -> performSocks4aHandshake(v4a);
            case WhatsAppClientProxy.Socks.V5.Local v5 -> performSocks5Handshake(v5);
            case WhatsAppClientProxy.Socks.V5.Remote v5h -> performSocks5Handshake(v5h);
        }
    }

    /**
     * Performs a SOCKS4 handshake: resolves the host locally to an IPv4
     * address, sends the CONNECT request with an optional user ID, and
     * validates the reply.
     *
     * @param v4 the SOCKS4 proxy configuration
     * @throws IOException if DNS resolution fails, the host is not IPv4,
     *                     or the proxy rejects the request
     */
    private void performSocks4Handshake(WhatsAppClientProxy.Socks.V4.Local v4) throws IOException {
        var address = InetAddress.getByName(host);
        if (!(address instanceof Inet4Address)) {
            throw new IOException("SOCKS4 only supports IPv4 addresses, but resolved " + host + " to " + address.getHostAddress());
        }

        var ipBytes = address.getAddress();
        var userIdBytes = v4.authenticator()
                .map(auth -> auth.userId().getBytes(StandardCharsets.ISO_8859_1))
                .orElse(SOCKS4_EMPTY_USER_ID);
        var request = ByteBuffer.allocate(9 + userIdBytes.length);
        request.put(SOCKS_VERSION_4);
        request.put(CMD_CONNECT);
        request.putShort((short) port);
        request.put(ipBytes);
        request.put(userIdBytes);
        request.put(SOCKS4_NULL_TERMINATOR);
        request.flip();
        innerLayer.sendBinary(request);

        handleSocks4Reply();
    }

    /**
     * Performs a SOCKS4a handshake: sends a CONNECT request with the
     * sentinel IP and the domain name for remote DNS resolution.
     *
     * @param v4a the SOCKS4a proxy configuration
     * @throws IOException if the proxy rejects the request
     */
    private void performSocks4aHandshake(WhatsAppClientProxy.Socks.V4.Remote v4a) throws IOException {
        var userIdBytes = v4a.authenticator()
                .map(auth -> auth.userId().getBytes(StandardCharsets.ISO_8859_1))
                .orElse(SOCKS4_EMPTY_USER_ID);
        var domainBytes = host.getBytes(StandardCharsets.ISO_8859_1);
        var request = ByteBuffer.allocate(10 + userIdBytes.length + domainBytes.length);
        request.put(SOCKS_VERSION_4);
        request.put(CMD_CONNECT);
        request.putShort((short) port);
        request.put(SOCKS4A_SENTINEL_IP);
        request.put(userIdBytes);
        request.put(SOCKS4_NULL_TERMINATOR);
        request.put(domainBytes);
        request.put(SOCKS4_NULL_TERMINATOR);
        request.flip();
        innerLayer.sendBinary(request);

        handleSocks4Reply();
    }

    /**
     * Reads and validates the 8-byte SOCKS4/4a reply.
     *
     * @throws IOException if the reply version is invalid or the request
     *                     was rejected
     */
    private void handleSocks4Reply() throws IOException {
        var reply = ByteBuffer.allocate(8);
        innerLayer.readBinary(reply, true);
        reply.flip();

        var replyVersion = Byte.toUnsignedInt(reply.get());
        if (replyVersion != SOCKS4_REPLY_VERSION) {
            throw new IOException("Invalid SOCKS4 reply version: " + replyVersion);
        }

        var status = Byte.toUnsignedInt(reply.get());
        if (status != SOCKS4_REQUEST_GRANTED) {
            var reason = getSocks4ReplyReason(status);
            throw new IOException("SOCKS4 proxy request failed: " + reason + " (Code: 0x" + Integer.toHexString(status) + ")");
        }
    }

    /**
     * Maps a SOCKS4 reply status code to a human-readable reason string.
     *
     * @param status the SOCKS4 reply status byte (unsigned)
     * @return a description of the failure reason
     */
    private static String getSocks4ReplyReason(int status) {
        return switch (status) {
            case SOCKS4_REQUEST_REJECTED -> "Request rejected or failed";
            case SOCKS4_REQUEST_NO_IDENTD -> "Request rejected because SOCKS server cannot connect to identd on the client";
            case SOCKS4_REQUEST_IDENTD_MISMATCH -> "Request rejected because the client program and identd report different user-ids";
            default -> "Unknown failure";
        };
    }

    /**
     * Performs the full SOCKS5 handshake: method negotiation, optional
     * authentication sub-negotiation (RFC 1929), and the CONNECT request.
     *
     * <p>For {@link WhatsAppClientProxy.Socks.V5.Local}, the host is
     * resolved locally and sent as an IPv4 or IPv6 address.  For
     * {@link WhatsAppClientProxy.Socks.V5.Remote}, the domain name is
     * sent for remote resolution.
     *
     * @param socks the SOCKS5 proxy configuration
     * @throws IOException if negotiation, authentication, or the CONNECT
     *                     request fails
     */
    @SuppressWarnings("OptionalIsPresent")
    private void performSocks5Handshake(WhatsAppClientProxy.Socks.V5 socks) throws IOException {
        var authenticatorOpt = socks.authenticator();
        var greeting = authenticatorOpt.isPresent()
                ? new byte[]{SOCKS_VERSION_5, 2, METHOD_NO_AUTH, (byte) (authenticatorOpt.get()).methodId()}
                : new byte[]{SOCKS_VERSION_5, 1, METHOD_NO_AUTH};
        innerLayer.sendBinary(ByteBuffer.wrap(greeting));

        var serverChoice = ByteBuffer.allocate(2);
        innerLayer.readBinary(serverChoice, true);
        serverChoice.flip();
        var version = serverChoice.get();
        if (version != SOCKS_VERSION_5) {
            throw new IOException("Unsupported socks version: " + Byte.toUnsignedInt(version));
        }

        var chosenMethod = Byte.toUnsignedInt(serverChoice.get());
        if (chosenMethod == METHOD_NO_ACCEPTABLE) {
            throw new IOException("SOCKS5 proxy rejected all offered authentication methods");
        }
        if (chosenMethod != METHOD_NO_AUTH) {
            if (authenticatorOpt.isEmpty()) {
                throw new IOException("Missing credentials for authentication: please provide them in the proxy configuration");
            }
            var authenticator = authenticatorOpt.get();
            if (authenticator.methodId() != chosenMethod) {
                throw new IOException("Proxy selected unsupported authentication method: " + chosenMethod);
            }
            switch (authenticator) {
                case WhatsAppClientProxyAuthenticator.Socks.V5.UserPassword credentials -> {
                    var userBytes = credentials.username().getBytes(StandardCharsets.ISO_8859_1);
                    var passBytes = credentials.password().getBytes(StandardCharsets.ISO_8859_1);

                    var authRequest = ByteBuffer.allocate(3 + userBytes.length + passBytes.length);
                    authRequest.put(AUTH_VERSION_1);
                    authRequest.put((byte) userBytes.length);
                    authRequest.put(userBytes);
                    authRequest.put((byte) passBytes.length);
                    authRequest.put(passBytes);
                    authRequest.flip();
                    innerLayer.sendBinary(authRequest);

                    var authResponse = ByteBuffer.allocate(2);
                    innerLayer.readBinary(authResponse, true);
                    authResponse.flip();
                    var authVersion = Byte.toUnsignedInt(authResponse.get());
                    if (authVersion != AUTH_VERSION_1) {
                        throw new IOException("Unsupported SOCKS5 auth sub-negotiation version: " + authVersion);
                    }
                    if (authResponse.get() != AUTH_SUCCESS) {
                        throw new IOException("SOCKS proxy authentication failed.");
                    }
                }
            }
        }

        var connRequest = switch (proxy) {
            case WhatsAppClientProxy.Socks.V5.Local _ -> buildResolvedConnectRequest();
            case WhatsAppClientProxy.Socks.V5.Remote _ -> buildDomainConnectRequest();
            case WhatsAppClientProxy.Socks.V4.Local _, WhatsAppClientProxy.Socks.V4.Remote _ ->
                    throw new AssertionError("SOCKS4/4a should not reach SOCKS5 handshake");
        };
        innerLayer.sendBinary(connRequest);

        handleSocks5Reply();
    }

    /**
     * Builds a SOCKS5 CONNECT request with the destination as a domain
     * name for remote DNS resolution.
     *
     * @return the serialized CONNECT request, ready to send
     * @throws IOException if the domain name exceeds
     *                     {@value #MAX_DOMAIN_LENGTH} bytes
     */
    private ByteBuffer buildDomainConnectRequest() throws IOException {
        var destHostBytes = host.getBytes(StandardCharsets.ISO_8859_1);
        if (destHostBytes.length > MAX_DOMAIN_LENGTH) {
            throw new IOException("SOCKS5 domain name too long: " + destHostBytes.length + " bytes (max " + MAX_DOMAIN_LENGTH + ")");
        }
        var request = ByteBuffer.allocate(4 + 1 + destHostBytes.length + 2);
        request.put(SOCKS_VERSION_5);
        request.put(CMD_CONNECT);
        request.put(SOCKS5_RESERVED);
        request.put(ADDR_TYPE_DOMAIN);
        request.put((byte) destHostBytes.length);
        request.put(destHostBytes);
        request.putShort((short) port);
        request.flip();
        return request;
    }

    /**
     * Builds a SOCKS5 CONNECT request with the destination as a resolved
     * IP address.
     *
     * @return the serialized CONNECT request, ready to send
     * @throws IOException if DNS resolution fails or yields an
     *                     unsupported address type
     */
    private ByteBuffer buildResolvedConnectRequest() throws IOException {
        var address = InetAddress.getByName(host);
        var ipBytes = address.getAddress();
        var addrType = switch (address) {
            case Inet4Address _ -> ADDR_TYPE_IPV4;
            case Inet6Address _ -> ADDR_TYPE_IPV6;
            default -> throw new IOException("Unsupported address type: " + address.getClass().getName());
        };
        var request = ByteBuffer.allocate(4 + ipBytes.length + 2);
        request.put(SOCKS_VERSION_5);
        request.put(CMD_CONNECT);
        request.put(SOCKS5_RESERVED);
        request.put(addrType);
        request.put(ipBytes);
        request.putShort((short) port);
        request.flip();
        return request;
    }

    /**
     * Reads and validates the SOCKS5 reply.  Checks the version and
     * reply code, then drains the variable-length bound address and
     * port fields.
     *
     * @throws IOException if the reply version is invalid or the request
     *                     was rejected
     */
    private void handleSocks5Reply() throws IOException {
        var replyInfo = ByteBuffer.allocate(2);
        innerLayer.readBinary(replyInfo, true);
        replyInfo.flip();

        if (replyInfo.get() != SOCKS_VERSION_5) {
            throw new IOException("Invalid SOCKS version in server reply.");
        }
        var replyCode = replyInfo.get();
        if (replyCode != SOCKS5_REPLY_SUCCESS) {
            var reason = getSocks5ReplyReason(replyCode);
            throw new IOException("SOCKS proxy request failed: " + reason + " (Code: " + replyCode + ")");
        }

        var addrInfo = ByteBuffer.allocate(2);
        innerLayer.readBinary(addrInfo, true);
        addrInfo.flip();

        addrInfo.get(); // reserved

        var addrType = addrInfo.get();
        int remainingBytes;
        switch (addrType) {
            case ADDR_TYPE_IPV4 -> remainingBytes = IPV4_ADDR_LENGTH + PORT_LENGTH;
            case ADDR_TYPE_DOMAIN -> {
                var lenBuf = ByteBuffer.allocate(1);
                innerLayer.readBinary(lenBuf, true);
                lenBuf.flip();
                remainingBytes = Byte.toUnsignedInt(lenBuf.get()) + PORT_LENGTH;
            }
            case ADDR_TYPE_IPV6 -> remainingBytes = IPV6_ADDR_LENGTH + PORT_LENGTH;
            default -> throw new IOException("Proxy returned an unsupported address type in reply: " + addrType);
        }
        innerLayer.readBinary(ByteBuffer.allocate(remainingBytes), true);
    }

    /**
     * Maps a SOCKS5 reply code to a human-readable reason string.
     *
     * @param replyCode the SOCKS5 reply code byte (unsigned)
     * @return a description of the failure reason
     */
    private static String getSocks5ReplyReason(int replyCode) {
        return switch (replyCode) {
            case SOCKS5_REPLY_GENERAL_FAILURE -> "General SOCKS server failure";
            case SOCKS5_REPLY_CONNECTION_NOT_ALLOWED -> "Connection not allowed by ruleset";
            case SOCKS5_REPLY_NETWORK_UNREACHABLE -> "Network unreachable";
            case SOCKS5_REPLY_HOST_UNREACHABLE -> "Host unreachable";
            case SOCKS5_REPLY_CONNECTION_REFUSED -> "Connection refused";
            case SOCKS5_REPLY_TTL_EXPIRED -> "TTL expired";
            case SOCKS5_REPLY_COMMAND_NOT_SUPPORTED -> "Command not supported";
            case SOCKS5_REPLY_ADDRESS_TYPE_UNSUPPORTED -> "Address type not supported";
            default -> "Unknown failure";
        };
    }

    @Override
    public void disconnect() {
        innerLayer.disconnect();
    }

    @Override
    public boolean isConnected() {
        return innerLayer.isConnected();
    }

    @Override
    public void sendBinary(ByteBuffer... buffers) throws IOException {
        innerLayer.sendBinary(buffers);
    }

    @Override
    public int readBinary(ByteBuffer buffer, boolean fully) throws IOException {
        return innerLayer.readBinary(buffer, fully);
    }

    @Override
    public void finishConnect() throws IOException {
        innerLayer.finishConnect();
    }

    @Override
    public void finishConnect(ByteBuffer leftover) throws IOException {
        innerLayer.finishConnect(leftover);
    }

    @Override
    public void startHandshake(SocketClientLayerContext tlsContext, long timeout) throws IOException {
        innerLayer.startHandshake(tlsContext, timeout);
    }

    @Override
    public void registerLayerContext(Class<? extends SocketClientLayer> key, SocketClientLayerContext context) throws IOException {
        innerLayer.registerLayerContext(key, context);
    }
}
