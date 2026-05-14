package com.github.auties00.cobalt.socket.datagram;

import com.github.auties00.cobalt.util.GcmUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Objects;

/**
 * Continuous {@link FilterInputStream} that exposes the
 * {@code int24}-prefixed, AES-GCM-encrypted WhatsApp datagram wire as a
 * plain byte stream.
 *
 * <p>The wire is a sequence of {@code int24}-prefixed datagrams. Each
 * datagram is its own AES-GCM ciphertext: the cipher must be
 * re-initialised with a fresh nonce at every datagram boundary, the
 * ciphertext bytes are fed through {@link Cipher#update}, and
 * {@link Cipher#doFinal} verifies the authentication tag at the end of
 * each datagram. Datagram boundaries are <strong>invisible</strong> to
 * the consumer: {@link #read()} returns plaintext bytes continuously
 * across datagram boundaries and only signals {@code -1} on a clean
 * end of the underlying stream.
 *
 * <p>This continuous mode is the natural fit for the post-handshake
 * reader thread, which decodes one {@link com.github.auties00.cobalt.node.Node}
 * per datagram via {@link com.github.auties00.cobalt.node.binary.NodeReader}:
 * each {@code readNode()} call consumes exactly one node's worth of
 * bytes (the wire format pairs one node body to one datagram), so the
 * cursor naturally arrives at the next datagram boundary without the
 * caller having to manage framing.
 *
 * <p>For the Noise XX handshake, where the consumer
 * ({@link it.auties.protobuf.stream.ProtobufInputStream}) needs an
 * {@link InputStream} that bounds reads to one handshake message,
 * {@link #readDatagramLength()} exposes the plaintext length of the
 * next datagram so a bounded view can be assembled around the
 * continuous {@link #read()}.
 *
 * <p>The stream owns two fixed-size buffers and nothing else: an 8 KiB
 * {@link #ibuffer} for one batched read from the underlying stream,
 * and a 16-byte {@link #finalStash} that catches the at-most-one-tag
 * worth of plaintext bytes that {@link Cipher#doFinal} releases when
 * the caller's {@code dst} is too small to receive them directly.
 * Neither buffer ever grows. Cipher output is written
 * <strong>directly into the caller's destination array</strong> from
 * {@link Cipher#update(byte[], int, int, byte[], int)} — there is no
 * intermediate output buffer.
 *
 * <p>Before the Noise handshake completes the stream is in
 * <strong>pre-handshake mode</strong>: {@code int24}-framed bytes are
 * still consumed from the underlying stream, but no cipher is applied
 * and the bytes are copied through {@link #ibuffer} into the caller's
 * {@code dst} verbatim. The caller flips the stream to encrypted mode
 * by invoking {@link #setReadKey(SecretKey)} once both Noise keys
 * have been derived.
 *
 * <p>Instances are <strong>not</strong> thread-safe — one input
 * stream is intended to be owned by a single reader thread.
 *
 * @implNote The state machine has three states keyed off
 *     {@link #textRemaining}: {@code < 0} means "no active
 *     datagram, read the next {@code int24} length";
 *     {@code > 0} means "feed more ciphertext through
 *     {@code cipher.update}"; {@code 0} means "all ciphertext has
 *     been consumed for this datagram, call {@code cipher.doFinal}".
 */
public final class WhatsAppDatagramInputStream extends FilterInputStream {

    /**
     * Number of bytes in the {@code int24} length prefix that frames
     * every datagram on the wire.
     */
    private static final int INT24_BYTE_SIZE = 3;

    /**
     * Size in bytes of the AES-GCM authentication tag.
     */
    private static final int GCM_TAG_BYTE_SIZE = 16;

    /**
     * Size of the fixed read buffer; one read from the underlying
     * stream draws at most this many bytes.
     */
    private static final int INPUT_BUFFER_SIZE = 8192;

    /**
     * Sentinel value for {@link #textRemaining} meaning "no
     * datagram is currently in flight; the next call must read the
     * next {@code int24} length prefix".
     */
    private static final int NO_ACTIVE_DATAGRAM = -1;

    /**
     * Fixed-size buffer used by {@link #produceMoreData(byte[], int, int)}
     * to read one chunk from the underlying stream before handing it
     * to {@link Cipher#update}.
     */
    private final byte[] ibuffer = new byte[INPUT_BUFFER_SIZE];

    /**
     * Fixed-size stash that catches the trailing plaintext bytes that
     * {@link Cipher#doFinal} releases when the caller's {@code dst}
     * is too small to receive them directly. Bounded by the GCM tag
     * size so it never needs to grow.
     */
    private final byte[] finalStash = new byte[GCM_TAG_BYTE_SIZE];

    /**
     * Read cursor in {@link #finalStash}; bytes from
     * {@code finalStashStart} (inclusive) to {@link #finalStashEnd}
     * (exclusive) are valid plaintext waiting to be delivered.
     */
    private int finalStashStart;

    /**
     * Write cursor in {@link #finalStash}; one past the last valid
     * stashed plaintext byte.
     */
    private int finalStashEnd;

    /**
     * Reusable single-byte buffer used by {@link #read()} to delegate
     * to the bulk-read code path without per-call allocation.
     */
    private final byte[] oneByteBuf = new byte[1];

    /**
     * The AES-GCM cipher; re-initialised on every datagram boundary
     * with a fresh nonce derived from {@link #readCounter}.
     */
    private final Cipher cipher;

    /**
     * Number of ciphertext bytes still to be fed through
     * {@link Cipher#update} for the current datagram, or
     * {@link #NO_ACTIVE_DATAGRAM} when the next call must read the
     * length prefix.
     */
    private int textRemaining = NO_ACTIVE_DATAGRAM;

    /**
     * The current AES-GCM read key, or {@code null} before the Noise
     * handshake completes (the stream is in pre-handshake passthrough
     * mode). Volatile so the handshake thread's
     * {@link #setReadKey(SecretKey)} call is visible to the reader
     * thread that subsequently runs.
     */
    private volatile SecretKey readKey;

    /**
     * Monotonic counter that seeds the AES-GCM nonce for the next
     * datagram. Incremented every time the cipher is re-initialised.
     */
    private long readCounter;

    /**
     * Sticky end-of-stream flag set when the underlying stream
     * returned {@code -1} between datagrams (a clean disconnect).
     */
    private boolean endOfStream;

    /**
     * Builds a datagram input stream wrapping {@code in}.
     *
     * <p>The stream starts in pre-handshake passthrough mode; bytes
     * are int24-deframed but not decrypted until
     * {@link #setReadKey(SecretKey)} supplies a key.
     *
     * @param in the underlying byte stream
     * @throws IOException          if the JDK cannot instantiate the
     *                              AES-GCM cipher
     * @throws NullPointerException if {@code in} is {@code null}
     */
    public WhatsAppDatagramInputStream(InputStream in) throws IOException {
        super(Objects.requireNonNull(in, "in"));
        try {
            this.cipher = Cipher.getInstance("AES/GCM/NoPadding");
        } catch (GeneralSecurityException exception) {
            throw new IOException("Failed to initialise AES-GCM cipher", exception);
        }
    }

    /**
     * Installs the AES-GCM read key derived from the Noise handshake
     * and resets the nonce counter to zero.
     *
     * <p>Subsequent datagrams are decrypted under {@code key} with
     * monotonically increasing nonces. Call exactly once after the
     * handshake completes and before starting the reader loop.
     *
     * @param key the read key
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public void setReadKey(SecretKey key) {
        this.readKey = Objects.requireNonNull(key, "key");
        this.readCounter = 0;
    }

    /**
     * Reads the next {@code int24} length prefix from the wire,
     * initialises the AES-GCM cipher (in post-handshake mode) with a
     * fresh nonce, and returns the number of plaintext bytes that
     * will follow on the stream.
     *
     * <p>Combines what would otherwise be three separate calls — read
     * the {@code int24}, set up the cipher state, expose the
     * plaintext length to the caller — into one public entry point so
     * a bounded {@link InputStream} (as used by the Noise XX
     * handshake to feed
     * {@link it.auties.protobuf.stream.ProtobufInputStream#fromStream(InputStream)})
     * can be assembled without duplicating any of the framing logic.
     *
     * <p>The returned length is the <em>plaintext</em> byte count:
     * the wire's {@code int24} value in pre-handshake mode, and the
     * wire's {@code int24} value minus the 16-byte GCM tag in
     * post-handshake mode. {@link #read()} will yield exactly that
     * many plaintext bytes for the current datagram before the next
     * {@code readDatagramLength()} call advances to the next one.
     *
     * @return the plaintext length of the next datagram, or
     *         {@code -1} on clean end-of-stream at the datagram
     *         boundary
     * @throws IllegalStateException if a previous {@code read} call
     *                               left the stream mid-datagram
     * @throws IOException           if the underlying read fails,
     *                               the declared length is invalid,
     *                               or the cipher cannot be
     *                               initialised
     */
    public int readDatagramLength() throws IOException {
        if (textRemaining != NO_ACTIVE_DATAGRAM) {
            throw new IllegalStateException("Cannot read a new datagram length: "
                    + textRemaining + " ciphertext byte(s) still pending in the current datagram");
        }
        if (finalStashStart < finalStashEnd) {
            throw new IllegalStateException("Cannot read a new datagram length: "
                    + (finalStashEnd - finalStashStart) + " plaintext byte(s) still stashed from the previous datagram");
        }
        if (endOfStream) {
            return -1;
        }
        var length = readWireLength();
        if (length < 0) {
            endOfStream = true;
            return -1;
        }
        return startDatagram(length);
    }

    /**
     * Reads one byte of plaintext.
     *
     * @return the next plaintext byte in {@code 0..255}, or
     *         {@code -1} on end-of-stream
     * @throws IOException if the underlying read or cipher operation
     *                     fails
     */
    @Override
    public int read() throws IOException {
        var n = read(oneByteBuf, 0, 1);
        return n < 0 ? -1 : oneByteBuf[0] & 0xFF;
    }

    /**
     * Reads up to {@code len} bytes of plaintext into {@code dst}.
     *
     * <p>The flow per call: any bytes left in {@link #finalStash}
     * (residue from a previous {@code doFinal}) are drained first;
     * then {@link #produceMoreData(byte[], int, int)} is invoked in a
     * loop until it yields at least one plaintext byte or signals
     * end-of-stream. {@code cipher.update} is allowed to return zero
     * bytes (GCM can hold back the trailing 16 bytes pending tag
     * verification), so the loop is essential.
     *
     * @param dst the destination byte array
     * @param off the offset in {@code dst} of the first byte to fill
     * @param len the maximum number of bytes to fill
     * @return the number of bytes copied, or {@code -1} on
     *         end-of-stream
     * @throws IOException if the underlying read or cipher operation
     *                     fails
     */
    @Override
    public int read(byte[] dst, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        if (finalStashStart < finalStashEnd) {
            return drainFinalStash(dst, off, len);
        }
        if (endOfStream) {
            return -1;
        }
        int produced;
        while ((produced = produceMoreData(dst, off, len)) == 0) {
            // cipher.update can legitimately return zero bytes when
            // the chunk is held back pending tag verification; loop
            // until we observe progress, an EOF, or an exception.
        }
        return produced;
    }

    /**
     * Copies the pending {@link #finalStash} bytes into {@code dst}.
     *
     * @param dst the destination byte array
     * @param off the offset in {@code dst}
     * @param len the maximum number of bytes to copy
     * @return the number of bytes copied
     */
    private int drainFinalStash(byte[] dst, int off, int len) {
        var n = Math.min(finalStashEnd - finalStashStart, len);
        System.arraycopy(finalStash, finalStashStart, dst, off, n);
        finalStashStart += n;
        return n;
    }

    /**
     * Advances the cipher state machine by exactly one step: starts a
     * new datagram, processes one chunk of ciphertext through
     * {@link Cipher#update} writing the plaintext directly into
     * {@code dst}, or finalises the current datagram with
     * {@link Cipher#doFinal} (either into {@code dst} or into the
     * {@link #finalStash} when {@code dst} is too small).
     *
     * <p>Returns the number of plaintext bytes deposited into
     * {@code dst} on this step. {@code 0} means the cipher held bytes
     * back and the caller should re-invoke. {@code -1} means clean
     * end-of-stream at a datagram boundary.
     *
     * @param dst the destination byte array
     * @param off the offset in {@code dst} of the first byte to fill
     * @param len the maximum number of bytes to fill
     * @return the number of plaintext bytes produced, or {@code -1}
     *         on end-of-stream
     * @throws IOException if the underlying read fails, the cipher
     *                     rejects the tag, or the datagram length is
     *                     out of bounds
     */
    private int produceMoreData(byte[] dst, int off, int len) throws IOException {
        if (textRemaining == NO_ACTIVE_DATAGRAM) {
            var plaintextLen = readDatagramLength();
            if (plaintextLen < 0) {
                return -1;
            }
        }

        if (textRemaining == 0) {
            return finalizeDatagram(dst, off, len);
        }

        var toRead = Math.min(Math.min(textRemaining, len), ibuffer.length);
        var n = in.read(ibuffer, 0, toRead);
        if (n < 0) {
            throw new IOException("Unexpected end of stream mid-datagram (expected "
                    + textRemaining + " more byte(s))");
        }
        textRemaining -= n;
        var produced = decryptChunk(n, dst, off);

        if (textRemaining == 0) {
            var space = Math.max(0, len - produced);
            var extra = finalizeDatagram(dst, off + produced, space);
            if (extra > 0) {
                produced += extra;
            }
        }
        return produced;
    }

    /**
     * Reads the next {@code int24} length prefix from the underlying
     * stream, returning the wire byte count (ciphertext plus GCM tag
     * in post-handshake mode, plaintext length in pre-handshake mode)
     * or {@code -1} if the underlying stream is cleanly exhausted at
     * the datagram boundary.
     *
     * @return the next datagram wire length, or {@code -1} on clean
     *         EOS
     * @throws IOException if the underlying read fails or the length
     *                     is out of bounds
     */
    private int readWireLength() throws IOException {
        var b0 = in.read();
        if (b0 < 0) {
            return -1;
        }
        var b1 = in.read();
        var b2 = in.read();
        if (b1 < 0 || b2 < 0) {
            throw new IOException("Unexpected end of stream while reading datagram length prefix");
        }
        var length = (b0 << 16) | (b1 << 8) | b2;
        if (length == 0) {
            throw new IOException("Invalid datagram length: 0");
        }
        return length;
    }

    /**
     * Initialises {@link #cipher} for the next datagram, sets
     * {@link #textRemaining} to the wire byte count and returns the
     * plaintext byte count that the consumer can expect to receive
     * for this datagram.
     *
     * <p>In pre-handshake mode no cipher operation occurs; the
     * datagram bytes will be copied through {@link #ibuffer} into
     * {@code dst} verbatim by {@link #decryptChunk(int, byte[], int)}
     * and the plaintext length equals the wire length. In
     * post-handshake mode the cipher is initialised with a fresh
     * nonce and the plaintext length is what
     * {@link Cipher#getOutputSize(int)} reports for the just-read
     * ciphertext (i.e. wire length minus the 16-byte GCM tag).
     *
     * @param length the wire byte count read from the {@code int24}
     *               prefix
     * @return the plaintext byte count for this datagram
     * @throws IOException if the cipher cannot be initialised or the
     *                     ciphertext is too short to contain a tag
     */
    private int startDatagram(int length) throws IOException {
        var key = readKey;
        if (key != null) {
            if (length <= GCM_TAG_BYTE_SIZE) {
                throw new IOException("Datagram too short to contain a GCM tag: " + length);
            }
            try {
                cipher.init(Cipher.DECRYPT_MODE, key, GcmUtils.createNonce(readCounter++));
            } catch (GeneralSecurityException exception) {
                throw new IOException("Failed to initialise AES-GCM cipher", exception);
            }
        }
        textRemaining = length;
        return key == null ? length : cipher.getOutputSize(length);
    }

    /**
     * Feeds {@code n} bytes from {@link #ibuffer} through
     * {@link Cipher#update} (or copies them verbatim in pre-handshake
     * mode), depositing the result directly into {@code dst}.
     *
     * @param n   the number of bytes in {@link #ibuffer} to process
     * @param dst the destination byte array
     * @param off the offset in {@code dst}
     * @return the number of plaintext bytes written to {@code dst}
     * @throws IOException if the cipher operation fails
     */
    private int decryptChunk(int n, byte[] dst, int off) throws IOException {
        var key = readKey;
        if (key == null) {
            System.arraycopy(ibuffer, 0, dst, off, n);
            return n;
        }
        try {
            return cipher.update(ibuffer, 0, n, dst, off);
        } catch (ShortBufferException exception) {
            throw new IOException("AES-GCM update produced more bytes than caller's buffer allowed", exception);
        }
    }

    /**
     * Calls {@link Cipher#doFinal} to release any held-back plaintext
     * and verify the GCM tag, writing the output into {@code dst}
     * when the caller's slice can fit a full tag's worth of bytes, or
     * into {@link #finalStash} otherwise.
     *
     * <p>The {@code finalStash} fallback is sized at exactly the GCM
     * tag length: {@link Cipher#doFinal} on a DECRYPT-mode AES-GCM
     * cipher cannot produce more bytes than that.
     *
     * @param dst the destination byte array
     * @param off the offset in {@code dst}
     * @param len the maximum number of bytes the caller can accept
     * @return the number of plaintext bytes deposited (possibly
     *         {@code 0} if doFinal produced nothing)
     * @throws IOException if the GCM tag fails to authenticate
     */
    private int finalizeDatagram(byte[] dst, int off, int len) throws IOException {
        var key = readKey;
        textRemaining = NO_ACTIVE_DATAGRAM;
        if (key == null) {
            return 0;
        }
        try {
            if (len >= GCM_TAG_BYTE_SIZE) {
                return cipher.doFinal(dst, off);
            }
            var produced = cipher.doFinal(finalStash, 0);
            finalStashStart = 0;
            finalStashEnd = produced;
            return drainFinalStash(dst, off, len);
        } catch (GeneralSecurityException exception) {
            throw new IOException("AES-GCM decryption failed (bad MAC)", exception);
        }
    }

    /**
     * Closes the underlying stream.
     *
     * @throws IOException if closing the underlying stream fails
     */
    @Override
    public void close() throws IOException {
        in.close();
    }
}
