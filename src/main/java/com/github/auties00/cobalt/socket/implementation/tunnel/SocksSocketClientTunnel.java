package com.github.auties00.cobalt.socket.implementation.tunnel;

import com.github.auties00.cobalt.client.WhatsAppClientProxy;
import com.github.auties00.cobalt.client.WhatsAppClientProxyAuthenticator;
import com.github.auties00.cobalt.socket.implementation.context.AbstractSocketSelector;
import com.github.auties00.cobalt.socket.implementation.SocketClientListener;
import com.github.auties00.cobalt.socket.implementation.transport.SocketClientTransport;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * SOCKS tunnel implementation for proxied socket connections.
 * <p>
 * Supports SOCKS4 (local DNS, IPv4 only), SOCKS4a (remote DNS via sentinel IP),
 * SOCKS5 with local DNS resolution (RFC 1928), and SOCKS5h with remote DNS resolution.
 * SOCKS5 authentication is handled via RFC 1929 username/password sub-negotiation.
 */
final class SocksSocketClientTunnel extends SocketClientTunnel {
    /** SOCKS4 protocol version byte. */
    private static final byte SOCKS_VERSION_4 = 0x04;
    /** SOCKS5 protocol version byte (RFC 1928). */
    private static final byte SOCKS_VERSION_5 = 0x05;

    /** CONNECT command: establish a TCP tunnel to the target. */
    private static final byte CMD_CONNECT = 0x01;

    /** No authentication required. */
    private static final byte METHOD_NO_AUTH = 0x00;
    /** Server rejected all offered authentication methods. */
    private static final int METHOD_NO_ACCEPTABLE = 0xFF;

    /** IPv4 address (4 bytes). */
    private static final byte ADDR_TYPE_IPV4 = 0x01;
    /** Domain name (length-prefixed). */
    private static final byte ADDR_TYPE_DOMAIN = 0x03;
    /** IPv6 address (16 bytes). */
    private static final byte ADDR_TYPE_IPV6 = 0x04;

    /** Username/password sub-negotiation version. */
    private static final byte AUTH_VERSION_1 = 0x01;
    /** Authentication succeeded. */
    private static final byte AUTH_SUCCESS = 0x00;

    /** Succeeded. */
    private static final byte SOCKS5_REPLY_SUCCESS = 0x00;
    /** General SOCKS server failure. */
    private static final int SOCKS5_REPLY_GENERAL_FAILURE = 0x01;
    /** Connection not allowed by ruleset. */
    private static final int SOCKS5_REPLY_CONNECTION_NOT_ALLOWED = 0x02;
    /** Network unreachable. */
    private static final int SOCKS5_REPLY_NETWORK_UNREACHABLE = 0x03;
    /** Host unreachable. */
    private static final int SOCKS5_REPLY_HOST_UNREACHABLE = 0x04;
    /** Connection refused. */
    private static final int SOCKS5_REPLY_CONNECTION_REFUSED = 0x05;
    /** TTL expired. */
    private static final int SOCKS5_REPLY_TTL_EXPIRED = 0x06;
    /** Command not supported. */
    private static final int SOCKS5_REPLY_COMMAND_NOT_SUPPORTED = 0x07;
    /** Address type not supported. */
    private static final int SOCKS5_REPLY_ADDRESS_TYPE_UNSUPPORTED = 0x08;
    /** Reserved byte in SOCKS5 request/reply frames. */
    private static final byte SOCKS5_RESERVED = 0x00;

    /** Expected version byte in SOCKS4 replies ({@code 0x00}). */
    private static final int SOCKS4_REPLY_VERSION = 0x00;
    /** Request granted. */
    private static final int SOCKS4_REQUEST_GRANTED = 0x5A;
    /** Request rejected or failed. */
    private static final int SOCKS4_REQUEST_REJECTED = 0x5B;
    /** Request rejected: SOCKS server cannot connect to identd on the client. */
    private static final int SOCKS4_REQUEST_NO_IDENTD = 0x5C;
    /** Request rejected: client program and identd report different user-ids. */
    private static final int SOCKS4_REQUEST_IDENTD_MISMATCH = 0x5D;
    /** Null terminator for SOCKS4/4a string fields. */
    private static final byte SOCKS4_NULL_TERMINATOR = 0x00;
    /** Empty byte array used when no user ID is configured. */
    private static final byte[] SOCKS4_EMPTY_USER_ID = new byte[0];

    /** Deliberate invalid IP ({@code 0.0.0.1}) that signals remote DNS resolution. */
    private static final byte[] SOCKS4A_SENTINEL_IP = {0x00, 0x00, 0x00, 0x01};

    /** IPv4 address length in bytes. */
    private static final int IPV4_ADDR_LENGTH = 4;
    /** IPv6 address length in bytes. */
    private static final int IPV6_ADDR_LENGTH = 16;
    /** Port field length in bytes. */
    private static final int PORT_LENGTH = 2;
    /** Maximum domain name length in bytes (SOCKS5). */
    private static final int MAX_DOMAIN_LENGTH = 255;

    /** The proxy to use. */
    private final WhatsAppClientProxy.Socks proxy;
    /** The target host for the CONNECT request. */
    private String host;
    /** The target port for the CONNECT request. */
    private int port;

    /**
     * Creates a SOCKS proxy socket client.
     *
     * @param proxy the SOCKS proxy configuration
     */
    SocksSocketClientTunnel(SocketClientTransport transport, WhatsAppClientProxy.Socks proxy) {
        super(transport);
        this.proxy = proxy;
    }

    /**
     * Connects through the SOCKS proxy to the specified target host and port.
     * <p>
     * Opens a TCP connection to the proxy server and performs the appropriate
     * SOCKS handshake (4, 4a, or 5) based on the proxy configuration.
     *
     * @param endpoint the address to connect to
     * @param listener the callback for data received through the tunnel
     * @throws IOException          if the connection or handshake fails
     * @throws InterruptedException if the thread is interrupted during connection
     */

    @Override
    public void connect(InetSocketAddress endpoint, SocketClientListener listener) throws IOException, InterruptedException {
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        this.host = endpoint.getHostString();
        this.port = endpoint.getPort();

        var proxyHost = proxy.host();
        var proxyPort = proxy.port();

        transport.connect(new InetSocketAddress(proxyHost, proxyPort), listener);

        switch (proxy) {
            case WhatsAppClientProxy.Socks.V4.Local v4 -> performSocks4Handshake(v4);
            case WhatsAppClientProxy.Socks.V4.Remote v4a -> performSocks4aHandshake(v4a);
            case WhatsAppClientProxy.Socks.V5.Local v5 -> performSocks5Handshake(v5);
            case WhatsAppClientProxy.Socks.V5.Remote v5h -> performSocks5Handshake(v5h);
        }

        if (!AbstractSocketSelector.INSTANCE.markReady(transport)) {
            throw new IOException("Failed to authenticate with proxy: rejected");
        }
    }

    /**
     * Performs a SOCKS4 handshake: resolves the host locally to an IPv4 address,
     * sends the CONNECT request with an optional user ID, and validates the reply.
     *
     * @param v4 the SOCKS4 proxy configuration
     * @throws IOException if DNS resolution fails, the host is not IPv4, or the proxy rejects the request
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
        super.sendBinary(request);

        handleSocks4Reply();
    }

    /**
     * Performs a SOCKS4a handshake: sends a CONNECT request with the
     * {@linkplain #SOCKS4A_SENTINEL_IP sentinel IP} and the domain name for
     * remote DNS resolution.
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
        super.sendBinary(request);

        handleSocks4Reply();
    }

    /**
     * Reads and validates the 8-byte SOCKS4/4a reply. Checks the reply version
     * and status code, ignoring the bound port and address.
     *
     * @throws IOException if the reply version is invalid or the request was rejected
     */
    private void handleSocks4Reply() throws IOException {
        var reply = ByteBuffer.allocate(8);
        super.readBinary(reply, true);

        var replyVersion = Byte.toUnsignedInt(reply.get());
        if (replyVersion != SOCKS4_REPLY_VERSION) {
            throw new IOException("Invalid SOCKS4 reply version: " + replyVersion);
        }

        var status = Byte.toUnsignedInt(reply.get());
        if (status != SOCKS4_REQUEST_GRANTED) {
            var reason = getSocks4ReplyReason(status);
            throw new IOException("SOCKS4 proxy request failed: " + reason + " (Code: 0x" + Integer.toHexString(status) + ")");
        }
        // Remaining 6 bytes (port + ip) are ignored
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
     * <p>
     * For {@link WhatsAppClientProxy.Socks.V5.Local}, the host is resolved locally
     * and sent as an IPv4 or IPv6 address. For {@link WhatsAppClientProxy.Socks.V5.Remote},
     * the domain name is sent for remote resolution.
     *
     * @param socks the socks proxy
     * @throws IOException if negotiation, authentication, or the CONNECT request fails
     */
    @SuppressWarnings("OptionalIsPresent")
    private void performSocks5Handshake(WhatsAppClientProxy.Socks.V5 socks) throws IOException {
        var authenticatorOpt = socks.authenticator();
        var greeting = authenticatorOpt.isPresent()
                ? new byte[]{SOCKS_VERSION_5, 2, METHOD_NO_AUTH, (byte) (authenticatorOpt.get()).methodId()}
                : new byte[]{SOCKS_VERSION_5, 1, METHOD_NO_AUTH};
        super.sendBinary(ByteBuffer.wrap(greeting));

        var serverChoice = ByteBuffer.allocate(2);
        super.readBinary(serverChoice, true);
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
                    super.sendBinary(authRequest);

                    var authResponse = ByteBuffer.allocate(2);
                    super.readBinary(authResponse, true);
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
        super.sendBinary(connRequest);

        handleSocks5Reply();
    }

    /**
     * Builds a SOCKS5 CONNECT request with the destination as a domain name
     * ({@link #ADDR_TYPE_DOMAIN}) for remote DNS resolution.
     *
     * @return the serialized CONNECT request, ready to send
     * @throws IOException if the domain name exceeds {@value #MAX_DOMAIN_LENGTH} bytes
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
     * Builds a SOCKS5 CONNECT request with the destination as a resolved IP address
     * ({@link #ADDR_TYPE_IPV4} or {@link #ADDR_TYPE_IPV6}).
     *
     * @return the serialized CONNECT request, ready to send
     * @throws IOException if DNS resolution fails or yields an unsupported address type
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
     * Reads and validates the SOCKS5 reply. Checks the version and reply code,
     * then drains the variable-length bound address and port fields.
     *
     * @throws IOException if the reply version is invalid or the request was rejected
     */
    private void handleSocks5Reply() throws IOException {
        var replyInfo = ByteBuffer.allocate(2);
        super.readBinary(replyInfo, true);

        if (replyInfo.get() != SOCKS_VERSION_5) {
            throw new IOException("Invalid SOCKS version in server reply.");
        }
        var replyCode = replyInfo.get();
        if (replyCode != SOCKS5_REPLY_SUCCESS) {
            var reason = getSocks5ReplyReason(replyCode);
            throw new IOException("SOCKS proxy request failed: " + reason + " (Code: " + replyCode + ")");
        }

        var addrInfo = ByteBuffer.allocate(2);
        super.readBinary(addrInfo, true);

        addrInfo.get(); // reserved

        var addrType = addrInfo.get();
        int remainingBytes;
        switch (addrType) {
            case ADDR_TYPE_IPV4 -> remainingBytes = IPV4_ADDR_LENGTH + PORT_LENGTH;
            case ADDR_TYPE_DOMAIN -> {
                var lenBuf = ByteBuffer.allocate(1);
                super.readBinary(lenBuf, true);
                remainingBytes = Byte.toUnsignedInt(lenBuf.get()) + PORT_LENGTH;
            }
            case ADDR_TYPE_IPV6 -> remainingBytes = IPV6_ADDR_LENGTH + PORT_LENGTH;
            default -> throw new IOException("Proxy returned an unsupported address type in reply: " + addrType);
        }
        super.readBinary(ByteBuffer.allocate(remainingBytes), true);
    }

    /**
     * Maps a SOCKS5 reply code to a human-readable reason string.
     *
     * @param replyCode the SOCKS5 reply code byte (unsigned)
     * @return a description of the failure reason
     */
    public static String getSocks5ReplyReason(int replyCode) {
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
}
