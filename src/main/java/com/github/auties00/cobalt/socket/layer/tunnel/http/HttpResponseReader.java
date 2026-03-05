package com.github.auties00.cobalt.socket.layer.tunnel.http;

import com.github.auties00.cobalt.socket.layer.SocketClientLayer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A buffered byte reader for HTTP-style response parsing on top of a
 * {@link SocketClientLayer}.
 *
 * <p>This utility centralizes status-line parsing, header scanning, and
 * timeout/EOF handling used by CONNECT and WebSocket upgrade handshakes.
 */
public final class HttpResponseReader {
    private static final int HTTP_VERSION_MAJOR = 1;
    private static final int HTTP_VERSION_MINOR_MIN = 0;
    private static final int HTTP_VERSION_MINOR_MAX = 1;
    private static final byte CARRIAGE_RETURN = '\r';
    private static final byte LINE_FEED = '\n';
    private static final byte SPACE = ' ';

    private final SocketClientLayer innerLayer;
    private final String timeoutMessage;
    private final String headersTooLargeMessage;
    private final String eofMessage;
    private final int maxHeaderSize;
    private final int maxStatusLineSpaces;

    private ByteBuffer responseBuffer;
    private int headerBytesRead;

    /**
     * Creates a reusable response reader.
     *
     * @param innerLayer             the source layer used for reads
     * @param timeoutMessage         message used when deadlines are exceeded
     * @param headersTooLargeMessage message used when header bytes exceed the limit
     * @param eofMessage             message used when EOF is reached while parsing
     * @param maxHeaderSize          maximum header bytes allowed
     * @param maxStatusLineSpaces    maximum spaces tolerated in status line before code
     */
    public HttpResponseReader(
            SocketClientLayer innerLayer,
            String timeoutMessage,
            String headersTooLargeMessage,
            String eofMessage,
            int maxHeaderSize,
            int maxStatusLineSpaces
    ) {
        this.innerLayer = innerLayer;
        this.timeoutMessage = timeoutMessage;
        this.headersTooLargeMessage = headersTooLargeMessage;
        this.eofMessage = eofMessage;
        this.maxHeaderSize = maxHeaderSize;
        this.maxStatusLineSpaces = maxStatusLineSpaces;
    }

    /**
     * Clears buffered state before starting a new HTTP response parse.
     */
    public void reset() {
        this.responseBuffer = null;
        this.headerBytesRead = 0;
    }

    /**
     * Returns unread buffered bytes from the last refill, if any.
     *
     * @return leftover bytes in read mode, or {@code null} if none
     */
    public ByteBuffer remainingBytes() {
        if (responseBuffer == null || !responseBuffer.hasRemaining()) {
            return null;
        }
        return responseBuffer.slice();
    }

    /**
     * Starts header-byte accounting for an upcoming header section.
     */
    public void startHeaderSection() {
        this.headerBytesRead = 0;
    }

    /**
     * Reads and parses an HTTP status line from the current stream position.
     *
     * @param deadline absolute deadline in epoch milliseconds
     * @return the parsed status code
     * @throws IOException on timeout, malformed response, or EOF
     */
    public int readStatusLine(long deadline) throws IOException {
        byte b;
        do {
            b = nextByte(deadline);
        } while (b == SPACE || b == CARRIAGE_RETURN || b == LINE_FEED);

        if (b != 'H'
                || nextByte(deadline) != 'T'
                || nextByte(deadline) != 'T'
                || nextByte(deadline) != 'P'
                || nextByte(deadline) != '/') {
            throw new IOException("Invalid HTTP response: expected HTTP/1.0 or HTTP/1.1");
        }

        var major = nextByte(deadline) - '0';
        if (major != HTTP_VERSION_MAJOR) {
            throw new IOException("Invalid HTTP response: expected HTTP/1.0 or HTTP/1.1");
        }
        if (nextByte(deadline) != '.') {
            throw new IOException("Invalid HTTP response: expected HTTP/1.0 or HTTP/1.1");
        }

        var minor = nextByte(deadline) - '0';
        if (minor != HTTP_VERSION_MINOR_MIN && minor != HTTP_VERSION_MINOR_MAX) {
            throw new IOException("Invalid HTTP response: unsupported version HTTP/1." + minor);
        }

        b = nextByte(deadline);
        if (b != SPACE) {
            throw new IOException("Invalid HTTP response: expected space after HTTP version");
        }
        for (var spacesSkipped = 1; ; spacesSkipped++) {
            b = nextByte(deadline);
            if (b != SPACE) {
                break;
            }
            if (spacesSkipped >= maxStatusLineSpaces) {
                throw new IOException("Invalid HTTP response: too many spaces in status line");
            }
        }

        var d1 = b;
        var d2 = nextByte(deadline);
        var d3 = nextByte(deadline);
        if (d1 < '0' || d1 > '9' || d2 < '0' || d2 > '9' || d3 < '0' || d3 > '9') {
            throw new IOException("Invalid HTTP response: status code contains non-digit characters");
        }

        return (d1 - '0') * 100 + (d2 - '0') * 10 + (d3 - '0');
    }

    /**
     * Parses all response headers into a case-insensitive lookup map.
     *
     * @param deadline absolute deadline in epoch milliseconds
     * @return parsed headers with lowercased names
     * @throws IOException on timeout, EOF, or size overflow
     */
    public Map<String, List<String>> consumeHeaders(long deadline) throws IOException {
        startHeaderSection();
        var headers = new HashMap<String, List<String>>();
        var line = new StringBuilder();
        var firstLine = true;

        while (innerLayer.isConnected()) {
            var b = nextHeaderByte(deadline);

            if (b == CARRIAGE_RETURN) {
                continue;
            }

            if (b == LINE_FEED) {
                if (firstLine) {
                    firstLine = false;
                    line.setLength(0);
                    continue;
                }
                if (line.isEmpty()) {
                    return headers;
                }
                var colon = line.indexOf(":");
                if (colon > 0) {
                    var key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                    var value = line.substring(colon + 1).trim();
                    headers.computeIfAbsent(key, _ -> new ArrayList<>()).add(value);
                }
                line.setLength(0);
                continue;
            }

            if (!firstLine) {
                line.append((char) b);
            }
        }

        throw new IOException(eofMessage);
    }

    /**
     * Skips response headers until the terminating empty line.
     *
     * @param deadline absolute deadline in epoch milliseconds
     * @throws IOException on timeout, EOF, or size overflow
     */
    public void skipHeaders(long deadline) throws IOException {
        startHeaderSection();
        var firstLine = true;
        var lineIsEmpty = false;

        while (innerLayer.isConnected()) {
            var b = nextHeaderByte(deadline);

            if (b == CARRIAGE_RETURN) {
                continue;
            }

            if (b == LINE_FEED) {
                if (firstLine) {
                    firstLine = false;
                    lineIsEmpty = true;
                    continue;
                }
                if (lineIsEmpty) {
                    return;
                }
                lineIsEmpty = true;
                continue;
            }

            firstLine = false;
            lineIsEmpty = false;
        }

        throw new IOException(eofMessage);
    }

    /**
     * Reads one header byte and enforces the configured header limit.
     *
     * @param deadline absolute deadline in epoch milliseconds
     * @return the next header byte
     * @throws IOException on timeout, EOF, or size overflow
     */
    public byte nextHeaderByte(long deadline) throws IOException {
        if (++headerBytesRead > maxHeaderSize) {
            throw new IOException(headersTooLargeMessage);
        }
        return nextByte(deadline);
    }

    /**
     * Skips bytes until and including the next LF.
     *
     * @param deadline absolute deadline in epoch milliseconds
     * @throws IOException on timeout, EOF, or size overflow
     */
    public void skipToEndOfLine(long deadline) throws IOException {
        byte b;
        do {
            b = nextHeaderByte(deadline);
        } while (b != LINE_FEED);
    }

    /**
     * Reads the next byte from the response stream using buffered refills.
     *
     * @param deadline absolute deadline in epoch milliseconds
     * @return the next byte
     * @throws IOException on timeout or EOF
     */
    public byte nextByte(long deadline) throws IOException {
        if (responseBuffer != null && responseBuffer.hasRemaining()) {
            return responseBuffer.get();
        }

        if (responseBuffer == null) {
            responseBuffer = ByteBuffer.allocate(512);
        }

        while (true) {
            checkDeadline(deadline);
            responseBuffer.clear();
            var length = innerLayer.readBinary(responseBuffer, false);
            if (length > 0) {
                responseBuffer.position(0);
                responseBuffer.limit(length);
                return responseBuffer.get();
            }
            if (length < 0) {
                throw new IOException(eofMessage);
            }
            throw new IOException("Unexpected zero-length read from transport layer");
        }
    }

    /**
     * Throws if the deadline has expired.
     *
     * @param deadline absolute deadline in epoch milliseconds
     * @throws IOException on timeout
     */
    public void checkDeadline(long deadline) throws IOException {
        if (System.currentTimeMillis() > deadline) {
            throw new IOException(timeoutMessage);
        }
    }
}
