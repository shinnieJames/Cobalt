package com.github.auties00.cobalt.socket.client.webSocket;

import com.github.auties00.cobalt.client.WhatsAppClientProxy;
import com.github.auties00.cobalt.socket.client.tcp.TcpSocketClient;
import com.github.auties00.cobalt.socket.client.tcp.TcpSocketClientListener;
import com.github.auties00.cobalt.socket.context.AbstractSocketClientContext;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class WebSocketClient {
    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final String HTTPS_ENDPOINT_IDENTIFICATION_ALGORITHM = "HTTPS";
    private static final long TLS_HANDSHAKE_TIMEOUT = 15_000;
    private static final long UPGRADE_TIMEOUT = 30_000;
    private static final int MAX_RESPONSE_HEADER_SIZE = 65_536;
    private static final byte CARRIAGE_RETURN = '\r';
    private static final byte LINE_FEED = '\n';

    private final TcpSocketClient client;
    private ByteBuffer responseBuffer;

    private WebSocketClient(WhatsAppClientProxy proxy) {
        this.client = TcpSocketClient.newSocketClient(proxy);
    }

    public static WebSocketClient newWebSocketClient(WhatsAppClientProxy proxy) {
        return new WebSocketClient(proxy);
    }

    public void connect(URI path, WebSocketClientListener listener) throws IOException, InterruptedException {
        Objects.requireNonNull(path, "path is not null");
        Objects.requireNonNull(listener, "listener is not null");

        var host = path.getHost();
        var port = path.getPort();

        client.connect(new InetSocketAddress(host, port), new TcpSocketClientListener() {
            @Override
            public void onDatagram(ByteBuffer buffer) {

            }

            @Override
            public void onClose() {
                // TODO: Unexpected closure
            }
        });

        var context = client.socketContext();
        if (context.sslEngine != null) {
            throw new IOException("Websocket transport requires end-to-end TLS and cannot reuse proxy TLS");
        }

        // Move back to pre-tunnel mode while reading the HTTP upgrade response synchronously.
        context.tunnelled = false;
        context.stopListenerExecutor();

        initTls(context, host, port);

        var websocketKey = createWebSocketKey();
        var expectedAccept = computeExpectedAccept(websocketKey);
        sendUpgradeRequest(host, port, path, websocketKey);
        readUpgradeResponse(expectedAccept);

        if (!client.markReadyWebSocket()) {
            throw new IOException("Failed to switch websocket channel to ready state");
        }

        if (responseBuffer != null && responseBuffer.hasRemaining() && !client.preSeedDatagram(responseBuffer)) {
            throw new IOException("Failed to pre-seed leftover bytes after websocket upgrade");
        }
        responseBuffer = null;

        if (!client.drainSslAppBuffer()) {
            throw new IOException("Failed to drain leftover SSL data after websocket upgrade");
        }
    }

    public void sendBinary(ByteBuffer... buffers) {
        if (!client.isConnected()) {
            throw new IllegalStateException("Socket is not connected");
        }

        client.sendBinary(buffers);
    }

    public void disconnect() throws IOException {
        client.disconnect();
    }

    public boolean isConnected() {
        return client.isConnected();
    }

    private void initTls(AbstractSocketClientContext context, String host, int port) throws IOException {
        try {
            var sslContext = SSLContext.getDefault();
            var engine = sslContext.createSSLEngine(host, port);
            engine.setUseClientMode(true);
            var params = engine.getSSLParameters();
            params.setEndpointIdentificationAlgorithm(HTTPS_ENDPOINT_IDENTIFICATION_ALGORITHM);
            engine.setSSLParameters(params);
            context.initSsl(engine);
            client.startTlsHandshake(TLS_HANDSHAKE_TIMEOUT);
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

    private void sendUpgradeRequest(String host, int port, String path, String websocketKey) {
        var hostHeader = port == 443 ? host : host + ":" + port;
        var request = "GET " +
                         path +
                         " HTTP/1.1\r\n" +
                         "Host: " +
                         hostHeader +
                         "\r\n" +
                         "Upgrade: websocket\r\n" +
                         "Connection: Upgrade\r\n" +
                         "Sec-WebSocket-Version: 13\r\n" +
                         "Sec-WebSocket-Key: " +
                         websocketKey +
                         "\r\n" +
                         "Origin: https://" +
                         host +
                         "\r\n" +
                         "\r\n";
        client.sendBinary(ByteBuffer.wrap(request.getBytes(StandardCharsets.US_ASCII)));
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

        var length = client.readBinary(responseBuffer, false);
        if (length <= 0) {
            throw new IOException("Unexpected end of stream during websocket upgrade");
        }

        responseBuffer.position(0);
        responseBuffer.limit(length);
        return responseBuffer.get();
    }
}
