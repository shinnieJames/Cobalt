package com.github.auties00.cobalt.socket.datagram;

import com.github.auties00.cobalt.util.GcmUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Objects;

/**
 * Buffered {@link FilterOutputStream} that emits one
 * {@code int24}-prefixed AES-GCM datagram on each {@link #flush()}
 * call.
 *
 * <p>Writes accumulate in an internal buffer; {@link #flush()} reads
 * the accumulated plaintext, runs it through AES-GCM (in post-handshake
 * mode), assembles the wire bytes — an optional prologue prefix
 * (set via {@link #writePrologue(byte[])} once per connection, for the
 * first Noise handshake message) followed by the {@code int24} length
 * prefix followed by the ciphertext (or plaintext in pre-handshake
 * mode) — and forwards everything to the underlying stream as a
 * single {@link OutputStream#write(byte[])} call so a framing layer
 * below (the WebSocket frame stream in one-shot mode on the browser
 * client) emits a single frame containing the whole datagram.
 *
 * <p>The buffered design means peak memory per datagram is on the
 * order of {@code 2 × payloadSize} (plaintext plus ciphertext) plus
 * the frame header — comfortably within budget for WhatsApp's stanza
 * sizes (typically a few KiB, up to a few MiB for history sync) and
 * crucially preserves the "one logical datagram per WebSocket frame"
 * invariant the WhatsApp server expects.
 *
 * <p>Before the Noise handshake completes the stream is in
 * <strong>pre-handshake mode</strong>: writes are forwarded
 * verbatim and the {@code int24} prefix encodes the plaintext length
 * directly (no GCM tag). The caller flips the stream to encrypted
 * mode by invoking {@link #setWriteKey(SecretKey)} once both Noise
 * keys have been derived.
 *
 * <p>All public state-mutating methods are {@code synchronized} on
 * {@code this} so concurrent writers (the writer thread and the input
 * thread's auto-PONG, if any) cannot interleave halves of a datagram
 * in the underlying byte stream.
 */
public final class WhatsAppDatagramOutputStream extends FilterOutputStream {

    /**
     * Number of bytes in the {@code int24} length prefix that frames
     * every datagram on the wire.
     */
    private static final int INT24_BYTE_SIZE = 3;

    /**
     * Maximum length encodable in an unsigned {@code int24} prefix.
     */
    private static final int MAX_DATAGRAM_LENGTH = 0xFF_FFFF;

    /**
     * Plaintext buffer that accumulates payload bytes between
     * {@link #flush()} calls.
     */
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    /**
     * The AES-GCM cipher; re-initialised on every {@link #flush()}
     * call (in post-handshake mode) with a fresh nonce derived from
     * {@link #writeCounter}.
     */
    private final Cipher cipher;

    /**
     * The current AES-GCM write key, or {@code null} before the
     * Noise handshake completes (the stream is in pre-handshake
     * passthrough mode). Volatile so the handshake thread's
     * {@link #setWriteKey(SecretKey)} call is visible to the writer
     * thread that subsequently runs.
     */
    private volatile SecretKey writeKey;

    /**
     * Monotonic counter that seeds the AES-GCM nonce for the next
     * datagram. Incremented every time {@link #flush()} fires the
     * cipher initialisation.
     */
    private long writeCounter;

    /**
     * One-shot prologue prefix consumed by the next {@link #flush()}
     * call (and cleared once consumed). Used for the very first
     * Noise handshake message, which prepends the four-byte WhatsApp
     * version header (e.g. {@code "WA\x06\x07"}) before the
     * {@code int24} length so the prologue and the {@code ClientHello}
     * sit inside the same WebSocket frame.
     */
    private byte[] nextPrologue;

    /**
     * Builds a datagram output stream wrapping {@code out}.
     *
     * @param out the underlying byte stream
     * @throws IOException          if the JDK cannot instantiate the
     *                              AES-GCM cipher
     * @throws NullPointerException if {@code out} is {@code null}
     */
    public WhatsAppDatagramOutputStream(OutputStream out) throws IOException {
        super(Objects.requireNonNull(out, "out"));
        try {
            this.cipher = Cipher.getInstance("AES/GCM/NoPadding");
        } catch (GeneralSecurityException exception) {
            throw new IOException("Failed to initialise AES-GCM cipher", exception);
        }
    }

    /**
     * Installs the AES-GCM write key derived from the Noise
     * handshake and resets the nonce counter to zero.
     *
     * <p>Subsequent {@link #flush()} calls encrypt the buffered
     * plaintext under {@code key} with monotonically increasing
     * nonces. Call exactly once after the handshake completes and
     * before the writer thread sends its first encrypted node.
     *
     * @param key the write key
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public synchronized void setWriteKey(SecretKey key) {
        this.writeKey = Objects.requireNonNull(key, "key");
        this.writeCounter = 0;
    }

    /**
     * Sets the prologue bytes that will be prepended to the wire
     * datagram emitted by the next {@link #flush()} call.
     *
     * <p>The prologue is a one-shot prefix: it is written before the
     * {@code int24} length and is cleared after one flush, so
     * subsequent datagrams do not carry it. Used by the Noise XX
     * handshake to fold the four-byte WhatsApp version header
     * ({@code "WA\x06\x07"} or {@code "WA\x05\x07"}) into the first
     * outbound frame.
     *
     * @param prologue the prologue bytes, or {@code null} to clear
     */
    public synchronized void writePrologue(byte[] prologue) {
        this.nextPrologue = prologue;
    }

    /**
     * Writes one plaintext byte into the per-datagram buffer.
     *
     * @param b the byte value to write
     */
    @Override
    public synchronized void write(int b) {
        buffer.write(b);
    }

    /**
     * Writes {@code len} plaintext bytes into the per-datagram buffer.
     *
     * @param src the source byte array
     * @param off the offset of the first byte to write
     * @param len the number of bytes to write
     */
    @Override
    public synchronized void write(byte[] src, int off, int len) {
        buffer.write(src, off, len);
    }

    /**
     * Emits the buffered plaintext as one wire datagram and forwards
     * it to the underlying stream as a single
     * {@link OutputStream#write(byte[])} call.
     *
     * <p>The flow per call:
     * <ol>
     *   <li>In post-handshake mode the AES-GCM cipher is initialised
     *       with a fresh nonce and runs over the buffered plaintext
     *       in one shot, yielding the ciphertext (plaintext length
     *       plus 16-byte GCM tag). In pre-handshake mode the
     *       plaintext is used verbatim.</li>
     *   <li>The wire bytes are assembled in a single byte array:
     *       optional prologue prefix, the three-byte {@code int24}
     *       length, then the ciphertext or plaintext.</li>
     *   <li>The byte array is forwarded to the underlying stream in
     *       one write call (so the WebSocket frame stream below
     *       emits one frame on the browser client), then the
     *       underlying stream is flushed.</li>
     *   <li>The per-datagram buffer and the one-shot prologue slot
     *       are cleared, so the next write cycle starts fresh.</li>
     * </ol>
     *
     * <p>If the buffer is empty and no prologue is pending, the call
     * is a no-op so a stray {@code flush()} from a higher-level
     * decorator (such as {@link com.github.auties00.cobalt.node.binary.NodeWriter})
     * does not emit a zero-length datagram on the wire.
     *
     * @throws IOException if the cipher fails, the wire length
     *                     overflows the {@code int24} prefix, or the
     *                     underlying write fails
     */
    @Override
    public synchronized void flush() throws IOException {
        var plaintext = buffer.toByteArray();
        if (plaintext.length == 0 && nextPrologue == null) {
            out.flush();
            return;
        }
        buffer.reset();

        byte[] wireBytes;
        int wireLen;
        var key = writeKey;
        if (key == null) {
            wireBytes = plaintext;
            wireLen = plaintext.length;
        } else {
            try {
                cipher.init(Cipher.ENCRYPT_MODE, key, GcmUtils.createNonce(writeCounter++));
            } catch (GeneralSecurityException exception) {
                throw new IOException("Failed to initialise AES-GCM cipher", exception);
            }
            try {
                wireBytes = cipher.doFinal(plaintext);
            } catch (GeneralSecurityException exception) {
                throw new IOException("AES-GCM encryption failed", exception);
            }
            wireLen = wireBytes.length;
        }

        if (wireLen > MAX_DATAGRAM_LENGTH) {
            throw new IOException("Datagram length " + wireLen + " exceeds " + MAX_DATAGRAM_LENGTH);
        }

        var prologue = nextPrologue;
        this.nextPrologue = null;
        var prologueLen = prologue != null ? prologue.length : 0;
        var totalLen = prologueLen + INT24_BYTE_SIZE + wireLen;
        var total = new byte[totalLen];
        var pos = 0;
        if (prologue != null) {
            System.arraycopy(prologue, 0, total, 0, prologueLen);
            pos = prologueLen;
        }
        total[pos++] = (byte) ((wireLen >>> 16) & 0xFF);
        total[pos++] = (byte) ((wireLen >>> 8) & 0xFF);
        total[pos++] = (byte) (wireLen & 0xFF);
        System.arraycopy(wireBytes, 0, total, pos, wireLen);

        out.write(total);
        out.flush();
    }

    /**
     * Closes the underlying stream.
     *
     * @throws IOException if closing the underlying stream fails
     */
    @Override
    public synchronized void close() throws IOException {
        out.close();
    }
}
