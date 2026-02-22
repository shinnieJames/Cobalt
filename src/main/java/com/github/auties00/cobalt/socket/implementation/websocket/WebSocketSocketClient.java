package com.github.auties00.cobalt.socket.implementation.websocket;

import com.github.auties00.cobalt.client.WhatsAppClientProxy;
import com.github.auties00.cobalt.client.WhatsAppClientProxyAuthenticator;
import com.github.auties00.cobalt.socket.implementation.SocketClient;
import com.github.auties00.cobalt.socket.implementation.SocketListener;
import com.github.auties00.cobalt.socket.implementation.context.SocketContext;
import com.github.auties00.cobalt.socket.implementation.threading.CentralSelector;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Websocket transport for WhatsApp Web.
 */
public final class WebSocketSocketClient extends SocketClient {
    private static final String DEFAULT_WEBSOCKET_HOST = "web.whatsapp.com";
    private static final String DEFAULT_WEBSOCKET_PATH = "/ws/chat";
    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final String HTTPS_ENDPOINT_IDENTIFICATION_ALGORITHM = "HTTPS";
    private static final long TLS_HANDSHAKE_TIMEOUT = 15_000;
    private static final long UPGRADE_TIMEOUT = 30_000;
    private static final int MAX_PROXY_AUTH_ATTEMPTS = 3;
    private static final int MAX_RESPONSE_HEADER_SIZE = 65_536;
    private static final byte CARRIAGE_RETURN = '\r';
    private static final byte LINE_FEED = '\n';
    private static final byte SOCKS_VERSION_4 = 0x04;
    private static final byte SOCKS_VERSION_5 = 0x05;
    private static final byte SOCKS_COMMAND_CONNECT = 0x01;
    private static final byte SOCKS5_AUTH_NONE = 0x00;
    private static final byte SOCKS5_AUTH_USER_PASSWORD = 0x02;
    private static final byte SOCKS5_AUTH_REJECTED = (byte) 0xFF;
    private static final byte SOCKS5_ADDRESS_TYPE_IPV4 = 0x01;
    private static final byte SOCKS5_ADDRESS_TYPE_DOMAIN = 0x03;
    private static final byte SOCKS5_ADDRESS_TYPE_IPV6 = 0x04;

    private final WhatsAppClientProxy proxy;

    private ByteBuffer responseBuffer;

    public WebSocketSocketClient() {
        this(null);
    }

    public WebSocketSocketClient(WhatsAppClientProxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public void connect(String host, int port, SocketListener listener) throws IOException, InterruptedException {
        var websocketHost = resolveHost(host);
        var context = openTunnel(websocketHost, port, listener);
        initTls(context, websocketHost, port);

        var websocketKey = createWebSocketKey();
        var expectedAccept = computeExpectedAccept(websocketKey);
        sendUpgradeRequest(websocketHost, port, websocketKey);
        readUpgradeResponse(expectedAccept);

        if (!CentralSelector.INSTANCE.markReadyWebSocket(channel)) {
            throw new IOException("Failed to switch websocket channel to ready state");
        }

        if (responseBuffer != null && responseBuffer.hasRemaining()
            && !CentralSelector.INSTANCE.preSeedDatagram(channel, responseBuffer)) {
            throw new IOException("Failed to pre-seed leftover bytes after websocket upgrade");
        }
        responseBuffer = null;

        if (!CentralSelector.INSTANCE.drainSslAppBuffer(channel)) {
            throw new IOException("Failed to drain leftover SSL data after websocket upgrade");
        }
    }

    @Override
    public void sendBinary(ByteBuffer buffer) {
        if (!isConnected()) {
            throw new IllegalStateException("Socket is not connected");
        }

        var frame = WebSocketFrameEncoder.encodeBinaryFrame(buffer);
        if (!CentralSelector.INSTANCE.addWrite(channel, frame.header(), frame.payload())) {
            throw new IllegalStateException("Failed to send websocket frame");
        }
    }

    private static String resolveHost(String host) {
        return "g.whatsapp.net".equalsIgnoreCase(host) ? DEFAULT_WEBSOCKET_HOST : host;
    }

    private SocketContext openTunnel(String host, int port, SocketListener listener) throws IOException, InterruptedException {
        if (proxy == null) {
            return super.openConnection(new InetSocketAddress(host, port), listener);
        }

        return switch (proxy) {
            case WhatsAppClientProxy.Http http -> openHttpTunnel(http, host, port, listener);
            case WhatsAppClientProxy.Socks socks -> openSocksTunnel(socks, host, port, listener);
        };
    }

    private SocketContext openHttpTunnel(
            WhatsAppClientProxy.Http httpProxy,
            String host,
            int port,
            SocketListener listener
    ) throws IOException, InterruptedException {
        if (httpProxy instanceof WhatsAppClientProxy.Http.Secure) {
            throw new IOException("Websocket transport over HTTPS proxies is currently unsupported");
        }

        var context = super.openConnection(new InetSocketAddress(httpProxy.host(), httpProxy.port()), listener);
        var target = host + ":" + port;
        String authorizationHeader = null;
        for (var attempt = 0; attempt <= MAX_PROXY_AUTH_ATTEMPTS; attempt++) {
            responseBuffer = null;
            sendConnectRequest(host, port, authorizationHeader);
            var deadline = System.currentTimeMillis() + UPGRADE_TIMEOUT;
            var statusCode = parseStatusCode(readLine(deadline));
            var headers = readHeaders(deadline);
            if (statusCode >= 200 && statusCode < 300) {
                responseBuffer = null;
                return context;
            }

            if (statusCode != 407) {
                throw new IOException("HTTP proxy CONNECT failed with status code " + statusCode);
            }

            var authenticator = httpProxy.authenticator()
                    .orElseThrow(() -> new IOException("Proxy requires authentication but no authenticator configured"));
            if (attempt == MAX_PROXY_AUTH_ATTEMPTS) {
                throw new IOException("HTTP proxy authentication failed after " + MAX_PROXY_AUTH_ATTEMPTS + " attempts");
            }

            var challenges = headers.getOrDefault("proxy-authenticate", List.of());
            authorizationHeader = authenticator.authenticate("CONNECT", target, challenges);
        }

        throw new IOException("HTTP proxy CONNECT failed");
    }

    private void sendConnectRequest(String host, int port, String authorizationHeader) {
        var request = new StringBuilder(192)
                .append("CONNECT ")
                .append(host)
                .append(':')
                .append(port)
                .append(" HTTP/1.1\r\n")
                .append("Host: ")
                .append(host)
                .append(':')
                .append(port)
                .append("\r\n")
                .append("Connection: keep-alive\r\n");
        if (authorizationHeader != null) {
            request.append("Proxy-Authorization: ")
                    .append(authorizationHeader)
                    .append("\r\n");
        }
        request.append("\r\n");
        super.sendBinary(ByteBuffer.wrap(request.toString().getBytes(StandardCharsets.US_ASCII)));
    }

    private SocketContext openSocksTunnel(
            WhatsAppClientProxy.Socks socksProxy,
            String host,
            int port,
            SocketListener listener
    ) throws IOException, InterruptedException {
        var context = super.openConnection(new InetSocketAddress(socksProxy.host(), socksProxy.port()), listener);
        switch (socksProxy) {
            case WhatsAppClientProxy.Socks.V4.Local v4 -> performSocks4Handshake(v4, host, port);
            case WhatsAppClientProxy.Socks.V4.Remote v4a -> performSocks4aHandshake(v4a, host, port);
            case WhatsAppClientProxy.Socks.V5.Local v5 -> performSocks5Handshake(v5, host, port);
            case WhatsAppClientProxy.Socks.V5.Remote v5h -> performSocks5Handshake(v5h, host, port);
        }
        return context;
    }

    private void performSocks4Handshake(WhatsAppClientProxy.Socks.V4.Local proxy, String host, int port) throws IOException {
        var resolved = InetAddress.getByName(host);
        if (!(resolved instanceof Inet4Address ipv4)) {
            throw new IOException("SOCKS4 local mode requires an IPv4 destination");
        }

        var userId = proxy.authenticator()
                .map(WhatsAppClientProxyAuthenticator.Socks.V4::userId)
                .orElse("");
        var userBytes = userId.getBytes(StandardCharsets.ISO_8859_1);
        var request = ByteBuffer.allocate(9 + userBytes.length);
        request.put(SOCKS_VERSION_4);
        request.put(SOCKS_COMMAND_CONNECT);
        request.putShort((short) port);
        request.put(ipv4.getAddress());
        request.put(userBytes);
        request.put((byte) 0);
        request.flip();
        super.sendBinary(request);

        var response = readFully(8);
        response.get();
        var status = response.get() & 0xFF;
        if (status != 0x5A) {
            throw new IOException("SOCKS4 proxy rejected CONNECT request with status " + status);
        }
    }

    private void performSocks4aHandshake(WhatsAppClientProxy.Socks.V4.Remote proxy, String host, int port) throws IOException {
        var userId = proxy.authenticator()
                .map(WhatsAppClientProxyAuthenticator.Socks.V4::userId)
                .orElse("");
        var userBytes = userId.getBytes(StandardCharsets.ISO_8859_1);
        var hostBytes = host.getBytes(StandardCharsets.ISO_8859_1);
        var request = ByteBuffer.allocate(10 + userBytes.length + hostBytes.length);
        request.put(SOCKS_VERSION_4);
        request.put(SOCKS_COMMAND_CONNECT);
        request.putShort((short) port);
        request.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 1);
        request.put(userBytes);
        request.put((byte) 0);
        request.put(hostBytes);
        request.put((byte) 0);
        request.flip();
        super.sendBinary(request);

        var response = readFully(8);
        response.get();
        var status = response.get() & 0xFF;
        if (status != 0x5A) {
            throw new IOException("SOCKS4a proxy rejected CONNECT request with status " + status);
        }
    }

    private void performSocks5Handshake(WhatsAppClientProxy.Socks.V5 proxy, String host, int port) throws IOException {
        var authenticator = proxy.authenticator().orElse(null);
        sendSocks5Greeting(authenticator);
        var selectedMethod = readSocks5SelectedMethod();
        if (selectedMethod == SOCKS5_AUTH_REJECTED) {
            throw new IOException("SOCKS5 proxy rejected all authentication methods");
        }

        if (selectedMethod == SOCKS5_AUTH_USER_PASSWORD) {
            if (!(authenticator instanceof WhatsAppClientProxyAuthenticator.Socks.V5.UserPassword credentials)) {
                throw new IOException("SOCKS5 proxy selected username/password authentication but no credentials were configured");
            }
            authenticateSocks5UserPassword(credentials);
        } else if (selectedMethod != SOCKS5_AUTH_NONE) {
            throw new IOException("SOCKS5 proxy selected unsupported method " + selectedMethod);
        }

        sendSocks5ConnectRequest(proxy, host, port);
        readSocks5ConnectResponse();
    }

    private void sendSocks5Greeting(WhatsAppClientProxyAuthenticator.Socks.V5 authenticator) {
        var requiresAuth = authenticator instanceof WhatsAppClientProxyAuthenticator.Socks.V5.UserPassword;
        var request = requiresAuth
                ? new byte[]{SOCKS_VERSION_5, 0x02, SOCKS5_AUTH_NONE, SOCKS5_AUTH_USER_PASSWORD}
                : new byte[]{SOCKS_VERSION_5, 0x01, SOCKS5_AUTH_NONE};
        super.sendBinary(ByteBuffer.wrap(request));
    }

    private byte readSocks5SelectedMethod() throws IOException {
        var response = readFully(2);
        var version = response.get();
        if (version != SOCKS_VERSION_5) {
            throw new IOException("Invalid SOCKS5 greeting response version: " + (version & 0xFF));
        }
        return response.get();
    }

    private void authenticateSocks5UserPassword(WhatsAppClientProxyAuthenticator.Socks.V5.UserPassword credentials) throws IOException {
        var usernameBytes = credentials.username().getBytes(StandardCharsets.ISO_8859_1);
        var passwordBytes = credentials.password().getBytes(StandardCharsets.ISO_8859_1);
        var request = ByteBuffer.allocate(3 + usernameBytes.length + passwordBytes.length);
        request.put((byte) 0x01);
        request.put((byte) usernameBytes.length);
        request.put(usernameBytes);
        request.put((byte) passwordBytes.length);
        request.put(passwordBytes);
        request.flip();
        super.sendBinary(request);

        var response = readFully(2);
        var version = response.get() & 0xFF;
        var status = response.get() & 0xFF;
        if (version != 0x01 || status != 0x00) {
            throw new IOException("SOCKS5 username/password authentication failed");
        }
    }

    private void sendSocks5ConnectRequest(WhatsAppClientProxy.Socks.V5 proxy, String host, int port) throws IOException {
        var request = switch (proxy) {
            case WhatsAppClientProxy.Socks.V5.Local _ -> buildSocks5ResolvedConnectRequest(host, port);
            case WhatsAppClientProxy.Socks.V5.Remote _ -> buildSocks5DomainConnectRequest(host, port);
        };
        super.sendBinary(request);
    }

    private ByteBuffer buildSocks5ResolvedConnectRequest(String host, int port) throws IOException {
        var address = InetAddress.getByName(host);
        if (address instanceof Inet4Address ipv4) {
            var request = ByteBuffer.allocate(10);
            request.put(SOCKS_VERSION_5).put(SOCKS_COMMAND_CONNECT).put((byte) 0).put(SOCKS5_ADDRESS_TYPE_IPV4);
            request.put(ipv4.getAddress());
            request.putShort((short) port);
            request.flip();
            return request;
        }

        if (address instanceof Inet6Address ipv6) {
            var request = ByteBuffer.allocate(22);
            request.put(SOCKS_VERSION_5).put(SOCKS_COMMAND_CONNECT).put((byte) 0).put(SOCKS5_ADDRESS_TYPE_IPV6);
            request.put(ipv6.getAddress());
            request.putShort((short) port);
            request.flip();
            return request;
        }

        throw new IOException("Unsupported resolved address type for SOCKS5");
    }

    private ByteBuffer buildSocks5DomainConnectRequest(String host, int port) throws IOException {
        var hostBytes = host.getBytes(StandardCharsets.ISO_8859_1);
        if (hostBytes.length == 0 || hostBytes.length > 255) {
            throw new IOException("SOCKS5 domain target must be 1..255 bytes");
        }

        var request = ByteBuffer.allocate(7 + hostBytes.length);
        request.put(SOCKS_VERSION_5).put(SOCKS_COMMAND_CONNECT).put((byte) 0).put(SOCKS5_ADDRESS_TYPE_DOMAIN);
        request.put((byte) hostBytes.length);
        request.put(hostBytes);
        request.putShort((short) port);
        request.flip();
        return request;
    }

    private void readSocks5ConnectResponse() throws IOException {
        var header = readFully(4);
        var version = header.get() & 0xFF;
        var status = header.get() & 0xFF;
        header.get(); // reserved
        var addressType = header.get() & 0xFF;

        if (version != SOCKS_VERSION_5) {
            throw new IOException("Invalid SOCKS5 response version: " + version);
        }

        var addressLength = switch (addressType) {
            case SOCKS5_ADDRESS_TYPE_IPV4 -> 4;
            case SOCKS5_ADDRESS_TYPE_DOMAIN -> readFully(1).get() & 0xFF;
            case SOCKS5_ADDRESS_TYPE_IPV6 -> 16;
            default -> throw new IOException("Invalid SOCKS5 response address type: " + addressType);
        };
        readFully(addressLength + Short.BYTES);

        if (status != 0x00) {
            throw new IOException("SOCKS5 CONNECT failed with status " + status);
        }
    }

    private ByteBuffer readFully(int size) throws IOException {
        var buffer = ByteBuffer.allocate(size);
        super.readBinary(buffer, true);
        return buffer;
    }

    private void initTls(SocketContext context, String host, int port) throws IOException {
        try {
            var sslContext = SSLContext.getDefault();
            var engine = sslContext.createSSLEngine(host, port);
            engine.setUseClientMode(true);
            var params = engine.getSSLParameters();
            params.setEndpointIdentificationAlgorithm(HTTPS_ENDPOINT_IDENTIFICATION_ALGORITHM);
            engine.setSSLParameters(params);
            context.initSsl(engine);
            CentralSelector.INSTANCE.startTlsHandshake(channel, TLS_HANDSHAKE_TIMEOUT);
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("Cannot initialize TLS for websocket transport", exception);
        }
    }

    private static String createWebSocketKey() {
        var raw = new byte[16];
        ThreadLocalRandom.current().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    private static String computeExpectedAccept(String websocketKey) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-1");
            var bytes = (websocketKey + WEBSOCKET_GUID).getBytes(StandardCharsets.US_ASCII);
            return Base64.getEncoder().encodeToString(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("Cannot compute websocket accept key", exception);
        }
    }

    private void sendUpgradeRequest(String host, int port, String websocketKey) {
        var hostHeader = port == 443 ? host : host + ":" + port;
        var request = new StringBuilder(256)
                .append("GET ")
                .append(DEFAULT_WEBSOCKET_PATH)
                .append(" HTTP/1.1\r\n")
                .append("Host: ")
                .append(hostHeader)
                .append("\r\n")
                .append("Upgrade: websocket\r\n")
                .append("Connection: Upgrade\r\n")
                .append("Sec-WebSocket-Version: 13\r\n")
                .append("Sec-WebSocket-Key: ")
                .append(websocketKey)
                .append("\r\n")
                .append("Origin: https://")
                .append(host)
                .append("\r\n")
                .append("\r\n");
        super.sendBinary(ByteBuffer.wrap(request.toString().getBytes(StandardCharsets.US_ASCII)));
    }

    private void readUpgradeResponse(String expectedAccept) throws IOException {
        var deadline = System.currentTimeMillis() + UPGRADE_TIMEOUT;
        var statusLine = readLine(deadline);
        var statusCode = parseStatusCode(statusLine);
        if (statusCode != 101) {
            throw new IOException("Websocket upgrade failed with status code " + statusCode);
        }

        var headerSize = statusLine.length();
        var hasUpgradeHeader = false;
        var hasConnectionUpgrade = false;
        String acceptHeader = null;

        while (true) {
            var line = readLine(deadline);
            headerSize += line.length();
            if (headerSize > MAX_RESPONSE_HEADER_SIZE) {
                throw new IOException("Websocket response headers exceed maximum size");
            }

            if (line.isEmpty()) {
                break;
            }

            var separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }

            var headerName = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            var headerValue = line.substring(separator + 1).trim();
            switch (headerName) {
                case "upgrade" -> hasUpgradeHeader = "websocket".equalsIgnoreCase(headerValue);
                case "connection" -> hasConnectionUpgrade = containsToken(headerValue, "upgrade");
                case "sec-websocket-accept" -> acceptHeader = headerValue;
            }
        }

        if (!hasUpgradeHeader || !hasConnectionUpgrade) {
            throw new IOException("Websocket upgrade response is missing mandatory headers");
        }

        if (acceptHeader == null || !acceptHeader.equals(expectedAccept)) {
            throw new IOException("Websocket upgrade failed: invalid Sec-WebSocket-Accept");
        }
    }

    private HashMap<String, List<String>> readHeaders(long deadline) throws IOException {
        var headers = new HashMap<String, List<String>>();
        var headerSize = 0;
        while (true) {
            var line = readLine(deadline);
            headerSize += line.length();
            if (headerSize > MAX_RESPONSE_HEADER_SIZE) {
                throw new IOException("HTTP response headers exceed maximum size");
            }

            if (line.isEmpty()) {
                return headers;
            }

            var separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }

            var headerName = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            var headerValue = line.substring(separator + 1).trim();
            headers.computeIfAbsent(headerName, ignored -> new java.util.ArrayList<>()).add(headerValue);
        }
    }

    private static int parseStatusCode(String statusLine) throws IOException {
        if (!statusLine.startsWith("HTTP/1.1 ") && !statusLine.startsWith("HTTP/1.0 ")) {
            throw new IOException("Invalid HTTP status line for websocket upgrade");
        }

        if (statusLine.length() < 12) {
            throw new IOException("Invalid HTTP status line for websocket upgrade");
        }

        var first = statusLine.charAt(9);
        var second = statusLine.charAt(10);
        var third = statusLine.charAt(11);
        if (!Character.isDigit(first) || !Character.isDigit(second) || !Character.isDigit(third)) {
            throw new IOException("Invalid HTTP status code in websocket upgrade response");
        }

        return (first - '0') * 100 + (second - '0') * 10 + (third - '0');
    }

    private static boolean containsToken(String value, String token) {
        var tokenLength = token.length();
        var start = 0;
        while (start < value.length()) {
            var comma = value.indexOf(',', start);
            var end = comma == -1 ? value.length() : comma;
            while (start < end && Character.isWhitespace(value.charAt(start))) {
                start++;
            }
            while (end > start && Character.isWhitespace(value.charAt(end - 1))) {
                end--;
            }
            if (end - start == tokenLength && value.regionMatches(true, start, token, 0, tokenLength)) {
                return true;
            }
            if (comma == -1) {
                break;
            }
            start = comma + 1;
        }
        return false;
    }

    private String readLine(long deadline) throws IOException {
        var builder = new StringBuilder(64);
        while (true) {
            var value = nextByte(deadline);
            if (value == CARRIAGE_RETURN) {
                continue;
            }
            if (value == LINE_FEED) {
                return builder.toString();
            }
            builder.append((char) (value & 0xFF));
        }
    }

    private byte nextByte(long deadline) throws IOException {
        if (responseBuffer != null && responseBuffer.hasRemaining()) {
            return responseBuffer.get();
        }

        if (System.currentTimeMillis() > deadline) {
            throw new IOException("Websocket HTTP upgrade timed out");
        }

        if (responseBuffer == null) {
            responseBuffer = ByteBuffer.allocate(512);
        } else {
            responseBuffer.clear();
        }

        var length = super.readBinary(responseBuffer, false);
        if (length <= 0) {
            throw new IOException("Unexpected end of stream during websocket upgrade");
        }

        responseBuffer.position(0);
        responseBuffer.limit(length);
        return responseBuffer.get();
    }
}
