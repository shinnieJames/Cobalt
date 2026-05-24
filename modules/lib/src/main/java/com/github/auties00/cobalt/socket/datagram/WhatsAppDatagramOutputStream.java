package com.github.auties00.cobalt.socket.datagram;

import com.github.auties00.cobalt.socket.websocket.WebSocketFrameOutputStream;
import com.github.auties00.cobalt.util.GcmUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.util.Objects;

/**
 * A streaming {@link FilterOutputStream} that emits one
 * {@code int24}-prefixed, AES-GCM-encrypted WhatsApp datagram per
 * {@link #beginDatagram(byte[], int)}/{@link #flush()} cycle without
 * buffering the plaintext or the ciphertext.
 *
 * <p>The lifecycle of one datagram is:
 *
 * <ol>
 *   <li>{@link #beginDatagram(byte[], int)} declares the plaintext
 *       byte count up front (optionally prefixed by a one-shot
 *       prologue), initialises the AES-GCM cipher (post-handshake) and
 *       writes the prologue and {@code int24} length prefix straight
 *       to the downstream.</li>
 *   <li>{@link #write(byte[], int, int)} and {@link #write(int)} stream
 *       the declared plaintext through {@link Cipher#update}; the
 *       resulting ciphertext is forwarded to the downstream as it is
 *       produced.</li>
 *   <li>{@link #flush()} calls {@link Cipher#doFinal} to release the
 *       final partial-block ciphertext and the 16-byte GCM tag, then
 *       flushes the downstream.</li>
 * </ol>
 *
 * <p>The stream owns one fixed-size scratch buffer (8 KiB plus a
 * 32-byte tag-margin) that captures the ciphertext produced by one
 * round of {@link Cipher#update} plus the trailing bytes that
 * {@link Cipher#doFinal} emits. Neither the plaintext nor the
 * ciphertext is ever copied into a buffer sized by the datagram.
 *
 * <p>Before the Noise handshake completes the stream is in
 * <strong>pre-handshake mode</strong>: writes are forwarded verbatim
 * and the {@code int24} prefix encodes the plaintext length directly
 * (no GCM tag). The caller flips the stream to encrypted mode by
 * invoking {@link #setWriteKey(SecretKey)} once both Noise keys have
 * been derived.
 *
 * <p>When the downstream is a {@link WebSocketFrameOutputStream},
 * {@link #beginDatagram(byte[], int)} announces the total wire byte
 * count (prologue plus header plus ciphertext plus tag) to the
 * WebSocket layer so the WebSocket frame header is emitted up front
 * and the whole datagram lands in a single binary frame; this matches
 * the WhatsApp server's expectation of one logical datagram per
 * WebSocket frame.
 *
 * <p>Instances are <strong>not</strong> thread-safe; one output stream
 * is intended to be owned by a single writer thread. The
 * {@link com.github.auties00.cobalt.socket.WhatsAppSocketClient}
 * serialises concurrent senders externally through its write lock.
 *
 * @implNote
 * This implementation sources the AES-GCM cipher from the BouncyCastle
 * JCE provider rather than the JDK's default SunJCE because
 * BouncyCastle's {@code GCMBlockCipher} streams ciphertext out of
 * {@link Cipher#update} block by block (appending only the tag at
 * {@link Cipher#doFinal}), whereas SunJCE buffers the entire
 * plaintext internally and releases the full ciphertext only at
 * {@code doFinal}. Streaming lets the output buffer stay at a fixed
 * 8 KiB regardless of how large a single datagram is. The state
 * machine keys off {@code textRemaining}: {@link #NO_ACTIVE_DATAGRAM}
 * means "no datagram in flight, the caller must call
 * {@link #beginDatagram(byte[], int)} next"; a non-negative value is
 * the count of plaintext bytes still expected before the matching
 * {@link #flush()} call.
 */
public final class WhatsAppDatagramOutputStream extends FilterOutputStream {

    /**
     * The size in bytes of the AES-GCM authentication tag.
     */
    private static final int GCM_TAG_BYTE_SIZE = 16;

    /**
     * The number of bytes in the {@code int24} length prefix that
     * frames every datagram on the wire.
     */
    private static final int INT24_BYTE_SIZE = 3;

    /**
     * The maximum length encodable in an unsigned {@code int24}
     * prefix.
     */
    private static final int MAX_DATAGRAM_LENGTH = 0xFF_FFFF;

    /**
     * The size of the fixed ciphertext scratch buffer; one round of
     * {@link Cipher#update} processes at most this many plaintext
     * bytes.
     */
    private static final int CIPHERTEXT_CHUNK_SIZE = 8192;

    /**
     * The headroom appended to {@link #ciphertextChunk} to absorb the
     * worst-case overshoot from BouncyCastle's AES-GCM
     * {@link Cipher#update} plus the trailing partial-block bytes
     * and the 16-byte tag that {@link Cipher#doFinal} releases.
     *
     * @apiNote
     * Empirically bounded at {@code GCM_TAG_BYTE_SIZE - 1 = 15} bytes
     * for the {@code update} overshoot when small unaligned input
     * chunks straddle an AES block boundary, plus another
     * {@code GCM_TAG_BYTE_SIZE = 16} bytes for the {@code doFinal}
     * tail plus tag; rounded up to {@code 2 * GCM_TAG_BYTE_SIZE = 32}
     * for headroom.
     */
    private static final int CIPHERTEXT_CHUNK_OVERFLOW = 2 * GCM_TAG_BYTE_SIZE;

    /**
     * The sentinel value for {@link #textRemaining} meaning "no
     * datagram is currently in flight; the next call must be
     * {@link #beginDatagram(byte[], int)}".
     */
    private static final int NO_ACTIVE_DATAGRAM = -1;

    /**
     * The shared BouncyCastle JCE provider instance handed to every
     * {@link Cipher#getInstance(String, Provider)} call.
     *
     * @apiNote
     * Holding the provider here means the JVM never needs to (and
     * does not) register BouncyCastle globally; isolating the
     * dependency to this stream avoids pulling in BouncyCastle's
     * other providers system-wide.
     */
    private static final Provider BOUNCY_CASTLE = new BouncyCastleProvider();

    /**
     * The fixed-size scratch buffer that captures the ciphertext
     * emitted by one {@link Cipher#update} call (sized to absorb the
     * up-to-15-byte BouncyCastle excess) plus any trailing bytes
     * released by {@link Cipher#doFinal} at the end of the datagram.
     */
    private final byte[] ciphertextChunk = new byte[CIPHERTEXT_CHUNK_SIZE + CIPHERTEXT_CHUNK_OVERFLOW];

    /**
     * The reusable single-byte buffer used by {@link #write(int)} to
     * delegate to the bulk-write code path without per-call
     * allocation.
     */
    private final byte[] oneByteBuf = new byte[1];

    /**
     * The reusable three-byte scratch buffer used by
     * {@link #beginDatagram(byte[], int)} to emit the
     * {@code int24} length prefix in one downstream write.
     */
    private final byte[] header = new byte[INT24_BYTE_SIZE];

    /**
     * The AES-GCM cipher; re-initialised on every
     * {@link #beginDatagram(byte[], int)} call (in post-handshake
     * mode) with a fresh nonce derived from {@link #writeCounter}.
     */
    private final Cipher cipher;

    /**
     * The current AES-GCM write key, or {@code null} before the Noise
     * handshake completes (the stream is in pre-handshake passthrough
     * mode).
     *
     * @apiNote
     * Declared {@code volatile} so the handshake thread's
     * {@link #setWriteKey(SecretKey)} call is visible to the writer
     * thread that subsequently runs against this stream.
     */
    private volatile SecretKey writeKey;

    /**
     * The monotonic counter that seeds the AES-GCM nonce for the next
     * datagram. Incremented every time
     * {@link #beginDatagram(byte[], int)} fires the cipher
     * initialisation.
     */
    private long writeCounter;

    /**
     * The number of plaintext bytes still expected for the current
     * datagram, or {@link #NO_ACTIVE_DATAGRAM} when no datagram is in
     * flight and the caller must call
     * {@link #beginDatagram(byte[], int)} before writing.
     */
    private int textRemaining = NO_ACTIVE_DATAGRAM;

    /**
     * Builds a datagram output stream wrapping {@code out}.
     *
     * @apiNote
     * The stream starts in pre-handshake passthrough mode; datagrams
     * are written verbatim until {@link #setWriteKey(SecretKey)}
     * supplies a key. The expected caller is
     * {@link com.github.auties00.cobalt.socket.WhatsAppSocketClient}
     * which constructs the stream over the transport-layer
     * {@link OutputStream} before running the Noise handshake on top.
     *
     * @param out the underlying byte stream
     * @throws IOException          if BouncyCastle cannot instantiate
     *                              the AES-GCM cipher
     * @throws NullPointerException if {@code out} is {@code null}
     */
    public WhatsAppDatagramOutputStream(OutputStream out) throws IOException {
        super(Objects.requireNonNull(out, "out"));
        try {
            this.cipher = Cipher.getInstance("AES/GCM/NoPadding", BOUNCY_CASTLE);
        } catch (GeneralSecurityException exception) {
            throw new IOException("Failed to initialise AES-GCM cipher", exception);
        }
    }

    /**
     * Installs the AES-GCM write key derived from the Noise handshake
     * and resets the nonce counter to zero.
     *
     * @apiNote
     * Subsequent {@link #beginDatagram(byte[], int)} calls encrypt
     * the streamed plaintext under {@code key} with monotonically
     * increasing nonces. Call exactly once after the handshake
     * completes and before the writer thread sends its first
     * encrypted datagram; calling twice resets the nonce counter and
     * produces unreadable traffic.
     *
     * @param key the write key
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public void setWriteKey(SecretKey key) {
        this.writeKey = Objects.requireNonNull(key, "key");
        this.writeCounter = 0;
    }

    /**
     * Begins one wire datagram whose plaintext payload will be the
     * next {@code plaintextSize} bytes written to this stream,
     * optionally prefixed on the wire by a one-shot {@code prologue}.
     *
     * @apiNote
     * Call this exactly once before the payload's first {@code write}
     * of each datagram, then write exactly {@code plaintextSize}
     * plaintext bytes, then call {@link #flush()} to release the GCM
     * tag and push the bytes downstream. The {@code prologue} is the
     * Noise XX version header ({@code "WA\x06\x07"} or
     * {@code "WA\x05\x07"}) on the very first handshake message; pass
     * {@code null} on every subsequent datagram. The cipher and the
     * downstream's length prefix are set up under this call, so the
     * caller can stream the payload through any byte-oriented encoder
     * (the {@link com.github.auties00.cobalt.node.binary.NodeWriter}
     * streaming variant, the protobuf
     * {@link it.auties.protobuf.stream.ProtobufOutputStream#toStream(OutputStream)}
     * variant) without that encoder having to know about framing or
     * encryption.
     *
     * @implNote
     * This implementation initialises the AES-GCM cipher (in
     * post-handshake mode) with a fresh nonce, computes the wire byte
     * count ({@code plaintextSize + 16} post-handshake or
     * {@code plaintextSize} pre-handshake), validates the
     * {@code int24} bound, and forwards a single
     * {@link WebSocketFrameOutputStream#beginFrame(int)} call to a
     * WebSocket downstream so the whole datagram lands in one
     * WebSocket binary frame. The prologue and the three-byte length
     * prefix are then written straight to the downstream so the
     * payload bytes can stream through {@link Cipher#update} into the
     * fixed-size {@link #ciphertextChunk} and onto the wire without
     * an intermediate per-datagram buffer.
     *
     * @param prologue       optional one-shot prologue bytes written
     *                       to the wire before the length prefix, or
     *                       {@code null} for none
     * @param plaintextSize  the exact number of plaintext bytes the
     *                       caller is about to write; must be
     *                       non-negative
     * @throws IOException              if the cipher cannot be
     *                                  initialised, the wire length
     *                                  overflows the {@code int24}
     *                                  prefix, or the underlying
     *                                  write fails
     * @throws IllegalArgumentException if {@code plaintextSize} is
     *                                  negative
     * @throws IllegalStateException    if a previous datagram is
     *                                  still in flight (the previous
     *                                  call to this method was not
     *                                  matched by a {@link #flush()})
     */
    public void beginDatagram(byte[] prologue, int plaintextSize) throws IOException {
        if (plaintextSize < 0) {
            throw new IllegalArgumentException("plaintextSize must be non-negative: " + plaintextSize);
        }
        if (textRemaining != NO_ACTIVE_DATAGRAM) {
            throw new IllegalStateException("Cannot begin a new datagram: "
                    + textRemaining + " plaintext byte(s) still pending in the current datagram");
        }
        var key = writeKey;
        int wireLen;
        if (key == null) {
            wireLen = plaintextSize;
        } else {
            wireLen = plaintextSize + GCM_TAG_BYTE_SIZE;
            try {
                cipher.init(Cipher.ENCRYPT_MODE, key, GcmUtils.createNonce(writeCounter++));
            } catch (GeneralSecurityException exception) {
                throw new IOException("Failed to initialise AES-GCM cipher", exception);
            }
        }
        if (wireLen > MAX_DATAGRAM_LENGTH) {
            throw new IOException("Datagram length " + wireLen + " exceeds " + MAX_DATAGRAM_LENGTH);
        }

        var prologueLen = prologue != null ? prologue.length : 0;
        if (out instanceof WebSocketFrameOutputStream ws) {
            ws.beginFrame(prologueLen + INT24_BYTE_SIZE + wireLen);
        }

        if (prologueLen > 0) {
            out.write(prologue, 0, prologueLen);
        }
        header[0] = (byte) ((wireLen >>> 16) & 0xFF);
        header[1] = (byte) ((wireLen >>> 8) & 0xFF);
        header[2] = (byte) (wireLen & 0xFF);
        out.write(header, 0, INT24_BYTE_SIZE);

        textRemaining = plaintextSize;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation delegates to {@link #write(byte[], int, int)}
     * through {@link #oneByteBuf} so the cipher-state machine has a
     * single code path; per-call allocation is avoided by reusing the
     * one-byte scratch buffer.
     */
    @Override
    public void write(int b) throws IOException {
        oneByteBuf[0] = (byte) b;
        write(oneByteBuf, 0, 1);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation feeds the caller's bytes through
     * {@link Cipher#update} in {@link #CIPHERTEXT_CHUNK_SIZE}-bounded
     * passes (in post-handshake mode) or forwards them verbatim (in
     * pre-handshake mode). The produced ciphertext is written to the
     * downstream as it leaves the cipher, so neither plaintext nor
     * ciphertext is ever copied into a buffer sized by the datagram.
     * Writing more bytes than declared by
     * {@link #beginDatagram(byte[], int)} is rejected.
     */
    @Override
    public void write(byte[] src, int off, int len) throws IOException {
        if (textRemaining == NO_ACTIVE_DATAGRAM) {
            throw new IOException("Cannot write before beginDatagram(byte[], int)");
        }
        if (len == 0) {
            return;
        }
        if (len > textRemaining) {
            throw new IOException("Streaming datagram overflow: tried to write " + len
                    + " bytes but only " + textRemaining + " remain");
        }
        textRemaining -= len;
        var key = writeKey;
        if (key == null) {
            out.write(src, off, len);
            return;
        }
        var i = 0;
        while (i < len) {
            var chunk = Math.min(len - i, CIPHERTEXT_CHUNK_SIZE);
            try {
                var produced = cipher.update(src, off + i, chunk, ciphertextChunk, 0);
                if (produced > 0) {
                    out.write(ciphertextChunk, 0, produced);
                }
            } catch (ShortBufferException exception) {
                throw new IOException("AES-GCM update produced more bytes than the ciphertext chunk buffer allowed", exception);
            }
            i += chunk;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation finalises the current datagram (in
     * post-handshake mode it calls {@link Cipher#doFinal} to emit the
     * final partial-block ciphertext and the 16-byte GCM tag) and
     * flushes the downstream. A flush issued with no datagram in
     * flight is forwarded straight to the downstream so a stray
     * {@code flush} from a higher-level decorator does not emit a
     * malformed datagram on the wire. A flush issued before all
     * declared plaintext bytes have been written is rejected.
     */
    @Override
    public void flush() throws IOException {
        if (textRemaining == NO_ACTIVE_DATAGRAM) {
            out.flush();
            return;
        }
        if (textRemaining != 0) {
            throw new IOException("Streaming datagram underflow: " + textRemaining
                    + " plaintext byte(s) still expected before flush");
        }
        var key = writeKey;
        if (key != null) {
            try {
                var produced = cipher.doFinal(ciphertextChunk, 0);
                if (produced > 0) {
                    out.write(ciphertextChunk, 0, produced);
                }
            } catch (GeneralSecurityException exception) {
                throw new IOException("AES-GCM encryption failed", exception);
            }
        }
        textRemaining = NO_ACTIVE_DATAGRAM;
        out.flush();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        out.close();
    }
}
