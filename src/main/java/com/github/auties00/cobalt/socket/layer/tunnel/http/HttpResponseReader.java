package com.github.auties00.cobalt.socket.layer.tunnel.http;

import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorSpecies;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A buffered byte reader for HTTP-style response parsing on top of a
 * {@link SocketClientLayer}.
 *
 * <p>This utility centralizes status-line parsing, header scanning, and
 * timeout/EOF handling used by CONNECT and WebSocket upgrade handshakes.
 *
 * <p>The internal read buffer is backed by a raw {@code byte[]} with
 * manual position/limit tracking, so the per-byte fast path in
 * {@link #nextByte(long)} reduces to a bounds check and an array load
 * with no virtual dispatch.
 *
 * <p>In addition to the byte-at-a-time API ({@link #nextByte(long)},
 * {@link #nextHeaderByte(long)}), bulk buffer primitives are exposed
 * so that call sites can implement zero-resize, exact-sized piece
 * collection for header value reads:
 * {@link #distanceToLineFeed()},
 * {@link #getBuffered(byte[], int, int)}, {@link #skipBuffered(int)},
 * {@link #bufferedRemaining()}, {@link #refillBuffered(long)}, and
 * {@link #accountHeaderBytes(int)}.
 *
 * <p><strong>Performance notes (Java 25):</strong>
 * <ul>
 *   <li>The buffer is allocated lazily on first refill and reused across
 *       parses — {@link #reset()} only clears cursors, never the array.</li>
 *   <li>{@link #nextByte(long)} splits the slow (refill) path into a
 *       separate method so the JIT can always inline the fast path.</li>
 *   <li>Status-line parsing uses {@link #ensureBuffered(int, long)} to
 *       batch the entire {@code HTTP/1.x NNN} prefix into direct array
 *       accesses instead of per-byte method calls.</li>
 *   <li>{@link #skipHeaders(long)} and {@link #skipToEndOfLine(long)}
 *       use vectorized LF scanning when the remaining buffer is large
 *       enough to amortize the vector setup cost, falling back to a
 *       scalar loop otherwise.</li>
 * </ul>
 */
public final class HttpResponseReader {
    private static final int HTTP_VERSION_MAJOR = 1;
    private static final int HTTP_VERSION_MINOR_MIN = 0;
    private static final int HTTP_VERSION_MINOR_MAX = 1;
    private static final byte CARRIAGE_RETURN = '\r';
    private static final byte LINE_FEED = '\n';
    private static final byte SPACE = ' ';

    /**
     * The capacity of the internal read buffer.  Sized to hold typical
     * HTTP response headers in a single refill, reducing transport-layer
     * crossings and deadline checks.
     */
    private static final int BUFFER_SIZE = 8192;

    /**
     * Preferred vector species for byte-level SIMD scanning.
     */
    private static final VectorSpecies<Byte> BYTE_SPECIES = ByteVector.SPECIES_PREFERRED;

    /**
     * Lane count for {@link #BYTE_SPECIES}.
     */
    private static final int SPECIES_LENGTH = BYTE_SPECIES.length();

    /**
     * Broadcast vector containing {@link #LINE_FEED} in every lane,
     * reused across all vectorised scans.
     */
    private static final ByteVector LF_VEC =
            ByteVector.broadcast(BYTE_SPECIES, LINE_FEED);

    /**
     * Minimum number of bytes remaining before we use vector scan
     * instead of the scalar fallback.  Below this threshold the vector
     * setup/teardown overhead is not amortized.  Two full vector widths
     * is a conservative breakeven point on most micro-architectures.
     */
    private static final int VECTOR_SCAN_THRESHOLD = SPECIES_LENGTH * 2;

    /**
     * The transport layer used for binary reads.
     */
    private final SocketClientLayer innerLayer;

    /**
     * The error message used when a deadline is exceeded.
     */
    private final String timeoutMessage;

    /**
     * The error message used when header bytes exceed the configured limit.
     */
    private final String headersTooLargeMessage;

    /**
     * The error message used when EOF is reached while parsing.
     */
    private final String eofMessage;

    /**
     * The maximum number of header bytes allowed before an error is raised.
     */
    private final int maxHeaderSize;

    /**
     * The maximum number of spaces tolerated in the status line before the
     * status code.
     */
    private final int maxStatusLineSpaces;

    /**
     * The raw read buffer.  Bytes from the transport layer are read into
     * this array via {@link #bufWrapper} and then consumed directly with
     * {@link #bufPos}/{@link #bufLimit}.
     *
     * <p>Allocated lazily on the first {@link #refillBuffered(long)} call
     * and retained across {@link #reset()} calls to avoid repeated
     * allocation and zeroing.
     */
    private byte[] buf;

    /**
     * A reusable {@link ByteBuffer} wrapper around {@link #buf}, used only
     * for the {@link SocketClientLayer#readBinary(ByteBuffer, boolean)} call
     * during buffer refills.
     */
    private ByteBuffer bufWrapper;

    /**
     * The index of the next byte to return from {@link #buf}.
     */
    private int bufPos;

    /**
     * One past the index of the last valid byte in {@link #buf}.
     */
    private int bufLimit;

    /**
     * Running count of header bytes consumed since the last call to
     * {@link #startHeaderSection()}.
     */
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
     *
     * <p>The underlying buffer array is retained so it can be reused by
     * subsequent parses without reallocation.
     */
    public void reset() {
        this.bufPos = 0;
        this.bufLimit = 0;
        this.headerBytesRead = 0;
    }

    /**
     * Returns unread buffered bytes from the last refill, if any.
     *
     * @return leftover bytes in read mode, or {@code null} if none
     */
    public ByteBuffer buffered() {
        if (buf == null || bufPos >= bufLimit) {
            return null;
        }
        return ByteBuffer.wrap(buf, bufPos, bufLimit - bufPos).slice();
    }

    /**
     * Copies exactly {@code length} bytes from the current buffer position
     * into {@code dst} and advances the buffer position.
     *
     * @param dst    the destination array
     * @param dstPos the starting offset in {@code dst}
     * @param length the number of bytes to copy
     */
    public void getBuffered(byte[] dst, int dstPos, int length) {
        System.arraycopy(buf, bufPos, dst, dstPos, length);
        bufPos += length;
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
     * <p>After skipping leading whitespace, this method ensures the entire
     * {@code HTTP/1.x NNN} prefix is buffered and then parses it with
     * direct array accesses — no per-byte method calls or refill branches
     * on the fast path.
     *
     * @param deadline absolute deadline in epoch milliseconds
     * @return the parsed status code
     * @throws IOException on timeout, malformed response, or EOF
     */
    public int readStatusLine(long deadline) throws IOException {
        // Skip leading whitespace (rare but allowed by lenient parsers)
        byte b;
        do {
            b = nextByte(deadline);
        } while (b == SPACE || b == CARRIAGE_RETURN || b == LINE_FEED);

        if (b != 'H') {
            throw new IOException("Invalid HTTP response: expected HTTP/1.0 or HTTP/1.1");
        }

        // "HTTP/1.x " + at least 3 status-code digits = 11 bytes minimum
        ensureBuffered(11, deadline);

        if (buf[bufPos] != 'T'
            || buf[bufPos + 1] != 'T'
            || buf[bufPos + 2] != 'P'
            || buf[bufPos + 3] != '/') {
            throw new IOException("Invalid HTTP response: expected HTTP/1.0 or HTTP/1.1");
        }

        var major = buf[bufPos + 4] - '0';
        if (major != HTTP_VERSION_MAJOR) {
            throw new IOException("Invalid HTTP response: expected HTTP/1.0 or HTTP/1.1");
        }
        if (buf[bufPos + 5] != '.') {
            throw new IOException("Invalid HTTP response: expected HTTP/1.0 or HTTP/1.1");
        }

        var minor = buf[bufPos + 6] - '0';
        if (minor != HTTP_VERSION_MINOR_MIN && minor != HTTP_VERSION_MINOR_MAX) {
            throw new IOException("Invalid HTTP response: unsupported version HTTP/1." + minor);
        }

        if (buf[bufPos + 7] != SPACE) {
            throw new IOException("Invalid HTTP response: expected space after HTTP version");
        }

        // Skip optional extra spaces before the status code
        var off = 8;
        for (var spacesSkipped = 1; ; spacesSkipped++) {
            // Ensure we have the next byte available
            if (bufPos + off >= bufLimit) {
                // Consume what we've indexed so far, refill, and restart offset
                bufPos += off;
                ensureBuffered(1, deadline);
                off = 0;
            }
            if (buf[bufPos + off] != SPACE) {
                break;
            }
            off++;
            if (spacesSkipped >= maxStatusLineSpaces) {
                throw new IOException("Invalid HTTP response: too many spaces in status line");
            }
        }

        // Ensure at least 3 bytes for the status code digits
        if (bufPos + off + 3 > bufLimit) {
            bufPos += off;
            ensureBuffered(3, deadline);
            off = 0;
        }

        var d1 = buf[bufPos + off];
        var d2 = buf[bufPos + off + 1];
        var d3 = buf[bufPos + off + 2];
        bufPos += off + 3;

        if (d1 < '0' || d1 > '9' || d2 < '0' || d2 > '9' || d3 < '0' || d3 > '9') {
            throw new IOException("Invalid HTTP response: status code contains non-digit characters");
        }

        return (d1 - '0') * 100 + (d2 - '0') * 10 + (d3 - '0');
    }

    /**
     * Skips response headers until the terminating empty line.
     *
     * <p>Uses vectorized LF scanning to leap over non-LF bytes (the vast
     * majority of header content) and only performs scalar work around
     * each LF to maintain the consecutive-empty-line state machine.  Falls
     * back to scalar scanning when the remaining buffer is too small to
     * amortize vector setup.
     *
     * @param deadline absolute deadline in epoch milliseconds
     * @throws IOException on timeout, EOF, or size overflow
     */
    public void skipHeaders(long deadline) throws IOException {
        startHeaderSection();
        var consecutiveLf = 0;

        while (true) {
            if (bufPos >= bufLimit) {
                refillBuffered(deadline);
            }

            var scanStart = bufPos;

            while (bufPos < bufLimit) {
                var dist = vectorDistanceToLineFeed(bufPos);

                if (dist < 0) {
                    // No LF in rest of buffer — consume everything
                    bufPos = bufLimit;
                    break;
                }

                // Check if the bytes between the current position and the LF
                // are all CR (meaning the line is effectively empty content-wise
                // between the previous LF and this one).  Any non-CR byte means
                // a non-empty header line, which resets the consecutive counter.
                var lfIndex = bufPos + dist;
                for (var j = bufPos; j < lfIndex; j++) {
                    if (buf[j] != CARRIAGE_RETURN) {
                        consecutiveLf = 0;
                        break;
                    }
                }

                // Advance past the LF
                bufPos = lfIndex + 1;

                if (++consecutiveLf == 2) {
                    accountHeaderBytes(bufPos - scanStart);
                    return;
                }
            }

            accountHeaderBytes(bufPos - scanStart);
        }
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
     * <p>Uses the vectorized {@link #distanceToLineFeed()} scan when the
     * buffer is large enough, falling back to per-byte reads only when
     * a refill is needed.
     *
     * @param deadline absolute deadline in epoch milliseconds
     * @throws IOException on timeout, EOF, or size overflow
     */
    public void skipToEndOfLine(long deadline) throws IOException {
        while (true) {
            if (bufPos >= bufLimit) {
                refillBuffered(deadline);
            }

            var dist = distanceToLineFeed();
            if (dist >= 0) {
                accountHeaderBytes(dist + 1);
                bufPos += dist + 1; // consume up to and including LF
                return;
            }

            // No LF in buffer — account for everything, consume, refill
            accountHeaderBytes(bufLimit - bufPos);
            bufPos = bufLimit;
        }
    }

    /**
     * Scans the current buffer for a LF byte without consuming any data.
     *
     * <p>Delegates to {@link #vectorDistanceToLineFeed(int)} which
     * dynamically selects between a SIMD and a scalar scan depending on
     * the remaining buffer size.
     *
     * @return distance in bytes from the current position to the LF
     *         (exclusive), or {@code -1} if no LF is buffered
     */
    public int distanceToLineFeed() {
        return vectorDistanceToLineFeed(bufPos);
    }

    /**
     * Advances the buffer position by {@code count} bytes without copying.
     *
     * @param count the number of bytes to skip
     */
    public void skipBuffered(int count) {
        bufPos += count;
    }

    /**
     * Returns the byte at the given offset relative to the current
     * buffer position, without advancing the position.
     *
     * @param offset the zero-based offset from the current position
     * @return the byte at {@code bufPos + offset}
     */
    public byte peekBuffered(int offset) {
        return buf[bufPos + offset];
    }

    /**
     * Performs a case-insensitive comparison of {@code patternLen} bytes
     * starting at {@code bufPos + offset} against a lowercase ASCII
     * {@code pattern}.
     *
     * <p>Case folding uses {@code (b | 0x20)}, which is correct for
     * ASCII letters and is a no-op for digits, hyphens, colons, and
     * other non-letter bytes whose bit 5 is already set or irrelevant.
     *
     * @param offset     the zero-based offset from the current position
     * @param pattern    the expected bytes, already lowercase
     * @param patternLen the number of bytes to compare
     * @return {@code true} if the buffered region matches
     */
    public boolean regionMatchesIgnoreCase(int offset, byte[] pattern, int patternLen) {
        if (bufPos + offset + patternLen > bufLimit) {
            return false;
        }
        var base = bufPos + offset;
        for (var i = 0; i < patternLen; i++) {
            if ((buf[base + i] | 0x20) != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Performs an exact (case-sensitive) comparison of {@code patternLen}
     * bytes starting at {@code bufPos + offset} against {@code pattern}.
     *
     * @param offset     the zero-based offset from the current position
     * @param pattern    the expected bytes
     * @param patternLen the number of bytes to compare
     * @return {@code true} if the buffered region matches exactly
     */
    public boolean regionMatches(int offset, byte[] pattern, int patternLen) {
        if (bufPos + offset + patternLen > bufLimit) {
            return false;
        }
        var base = bufPos + offset;
        for (var i = 0; i < patternLen; i++) {
            if (buf[base + i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the number of unread bytes remaining in the buffer.
     *
     * @return buffered byte count
     */
    public int bufferedRemaining() {
        return bufLimit - bufPos;
    }

    /**
     * Adds {@code count} to the header-byte counter and throws if the
     * configured limit is exceeded.
     *
     * <p>Call sites that perform bulk reads via
     * {@link #getBuffered(byte[], int, int)} must use this method to
     * maintain the header size limit that
     * {@link #nextHeaderByte(long)} enforces per byte.
     *
     * @param count the number of header bytes consumed
     * @throws IOException if the header size limit is exceeded
     */
    public void accountHeaderBytes(int count) throws IOException {
        headerBytesRead += count;
        if (headerBytesRead > maxHeaderSize) {
            throw new IOException(headersTooLargeMessage);
        }
    }

    /**
     * Fills the read buffer from the transport layer.
     *
     * <p>After a successful refill, the buffer position is {@code 0} and
     * the limit is the number of bytes read.  Allocates the buffer on
     * first use.
     *
     * @param deadline absolute deadline in epoch milliseconds
     * @throws IOException on timeout, EOF, or zero-length read
     */
    public void refillBuffered(long deadline) throws IOException {
        if (buf == null) {
            buf = new byte[BUFFER_SIZE];
            bufWrapper = ByteBuffer.wrap(buf);
        }
        checkDeadline(deadline);
        bufWrapper.clear();
        var length = innerLayer.readBinary(bufWrapper, false);
        if (length > 0) {
            bufPos = 0;
            bufLimit = length;
            return;
        }
        if (length < 0) {
            throw new IOException(eofMessage);
        }
        throw new IOException("Unexpected zero-length read from transport layer");
    }

    /**
     * Reads the next byte from the response stream using buffered refills.
     *
     * <p>The fast path is a single array bounds check and load, with no
     * virtual dispatch.  The slow path (buffer refill) is split into a
     * separate method so the JIT can always inline this method at call
     * sites.
     *
     * @param deadline absolute deadline in epoch milliseconds
     * @return the next byte
     * @throws IOException on timeout or EOF
     */
    public byte nextByte(long deadline) throws IOException {
        if (bufPos < bufLimit) {
            return buf[bufPos++];
        }
        return nextByteSlow(deadline);
    }

    /**
     * Slow path for {@link #nextByte(long)}: refills the buffer and
     * returns the first byte.  Separated to keep the fast-path bytecode
     * small enough for the JIT to inline unconditionally.
     */
    private byte nextByteSlow(long deadline) throws IOException {
        refillBuffered(deadline);
        return buf[bufPos++];
    }

    /**
     * Ensures that at least {@code need} bytes are available in the buffer
     * starting at {@link #bufPos}.
     *
     * <p>If the buffer contains fewer than {@code need} bytes, the
     * remaining bytes are compacted to the front of the array and more
     * data is read from the transport layer until the requirement is met.
     *
     * @param need     the minimum number of bytes required
     * @param deadline absolute deadline in epoch milliseconds
     * @throws IOException on timeout, EOF, or zero-length read
     */
    private void ensureBuffered(int need, long deadline) throws IOException {
        if (buf == null) {
            buf = new byte[BUFFER_SIZE];
            bufWrapper = ByteBuffer.wrap(buf);
        }
        while (bufLimit - bufPos < need) {
            // Compact remaining bytes to the front
            var remaining = bufLimit - bufPos;
            if (remaining > 0 && bufPos > 0) {
                System.arraycopy(buf, bufPos, buf, 0, remaining);
            }
            bufPos = 0;
            bufLimit = remaining;

            checkDeadline(deadline);
            bufWrapper.clear().position(bufLimit);
            var n = innerLayer.readBinary(bufWrapper, false);
            if (n > 0) {
                bufLimit += n;
            } else if (n < 0) {
                throw new IOException(eofMessage);
            } else {
                throw new IOException("Unexpected zero-length read from transport layer");
            }
        }
    }

    /**
     * Scans for a {@link #LINE_FEED} byte starting at {@code from},
     * dynamically choosing between a SIMD vector scan and a scalar loop.
     *
     * <p>When the number of bytes to scan is below
     * {@link #VECTOR_SCAN_THRESHOLD} (two full vector widths), the scalar
     * fallback is used directly — the vector lane setup, mask extraction,
     * and tail handling would cost more than a simple byte loop over such
     * a small range.
     *
     * @param from the buffer index to start scanning from
     * @return the distance from {@code from} to the first LF (exclusive),
     *         or {@code -1} if no LF is found before {@link #bufLimit}
     */
    private int vectorDistanceToLineFeed(int from) {
        var remaining = bufLimit - from;

        if (remaining >= VECTOR_SCAN_THRESHOLD) {
            // --- Vectorised path ---
            var i = from;
            var vectorLimit = bufLimit - SPECIES_LENGTH;

            for (; i <= vectorLimit; i += SPECIES_LENGTH) {
                var vec = ByteVector.fromArray(BYTE_SPECIES, buf, i);
                var mask = vec.eq(LF_VEC);
                if (mask.anyTrue()) {
                    return (i + mask.firstTrue()) - from;
                }
            }

            // Scalar tail after the last full vector
            for (; i < bufLimit; i++) {
                if (buf[i] == LINE_FEED) {
                    return i - from;
                }
            }
        } else {
            // --- Scalar path (small buffer, vector overhead not worth it) ---
            for (var i = from; i < bufLimit; i++) {
                if (buf[i] == LINE_FEED) {
                    return i - from;
                }
            }
        }

        return -1;
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

    /**
     * Appends {@code count} bytes from the current buffer position to the
     * given {@link StringBuilder} as ISO-8859-1 characters and advances
     * the buffer position.
     *
     * <p>ISO-8859-1 is a 1:1 byte→char mapping so no decoding state is
     * needed.  This is intended for slow-path header value reads where
     * the value spans multiple buffer refills and a {@code byte[]}
     * accumulator is undesirable.
     *
     * @param sb    the target builder
     * @param count the number of bytes to append
     */
    public void writeTo(StringBuilder sb, int count) {
        sb.ensureCapacity(sb.length() + count);
        for (var i = 0; i < count; i++) {
            sb.append((char) (buf[bufPos + i] & 0xFF));
        }
        bufPos += count;
    }
}