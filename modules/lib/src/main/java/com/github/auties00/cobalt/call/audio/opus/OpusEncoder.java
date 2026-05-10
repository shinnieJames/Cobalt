package com.github.auties00.cobalt.call.audio.opus;

import com.github.auties00.cobalt.call.audio.opus.bindings.Opus;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.util.NativeLibLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * Opus encoder, backed by libopus via FFM. Bindings are
 * jextract-generated; this class is the high-level idiomatic-Java
 * wrapper.
 *
 * <p>WhatsApp's wasm engine encodes voice at 16 kHz mono in 10 ms
 * frames (160 samples per frame, 320 bytes of PCM); to match exactly,
 * construct as {@code new OpusEncoder(16000, 1, OpusApplication.VOIP)}
 * and feed 160-sample {@code short[]} buffers.
 *
 * <p>Maximum encoded frame size: by RFC 6716 §3, the worst-case Opus
 * packet is ~1275 bytes for a 60 ms frame at any bitrate; for 10 ms
 * frames at typical voice bitrates (16-32 kbps) it's well under 100
 * bytes. {@link #MAX_PACKET_BYTES} is sized for safety.
 */
public final class OpusEncoder implements AutoCloseable {
    static {
        NativeLibLoader.load("opus", Arena.global());
    }

    /**
     * Maximum Opus packet size we provision for the encode buffer.
     * RFC 6716 §3 gives 1276 as the absolute maximum for a single
     * 60 ms frame; for 10 ms / typical voice bitrates it's much
     * smaller, but allocating once at construction avoids per-call
     * sizing.
     */
    public static final int MAX_PACKET_BYTES = 1500;

    /**
     * Per-instance arena owning the encoder state pointer and the
     * native scratch buffers reused across encode calls.
     */
    private final Arena arena;

    /**
     * Pointer to the {@code OpusEncoder} state allocated by
     * {@code opus_encoder_create}. Nulled by {@link #close}.
     */
    private MemorySegment state;

    /**
     * Reusable native PCM input buffer ({@code MAX_FRAME_SAMPLES *
     * channels * 2} bytes, sized for a 60 ms frame at 48 kHz stereo).
     */
    private final MemorySegment pcmBuf;

    /**
     * Reusable native packet output buffer ({@link #MAX_PACKET_BYTES}
     * bytes).
     */
    private final MemorySegment packetBuf;

    /**
     * Single-int scratch for {@code opus_encoder_ctl} integer
     * arguments.
     */
    private final MemorySegment intScratch;

    /**
     * Lazily-cached variadic invoker for
     * {@code opus_encoder_ctl(state, request, int)} — the typed
     * shape of every {@code OPUS_SET_*_REQUEST} integer control.
     */
    private static volatile Opus.opus_encoder_ctl INT_CTL_INVOKER;

    /**
     * Lazily-cached variadic invoker for
     * {@code opus_encoder_ctl(state, request, int *)} — the typed
     * shape of every {@code OPUS_GET_*_REQUEST} integer-out control.
     */
    private static volatile Opus.opus_encoder_ctl INT_OUT_CTL_INVOKER;

    /**
     * Constructs a new encoder configured for the given sample rate,
     * channel count, and application mode. Defaults DTX to enabled
     * — matching WhatsApp's voice configuration.
     *
     * @param sampleRate the input sample rate (8000, 12000, 16000,
     *                   24000, or 48000 Hz)
     * @param channels   1 for mono, 2 for stereo
     * @param app        the application mode (VOIP for calls)
     * @throws NullPointerException if {@code app} is {@code null}
     * @throws WhatsAppCallException.Opus   if libopus rejects the
     *                              configuration
     * @throws UnsatisfiedLinkError if libopus cannot be loaded
     */
    public OpusEncoder(int sampleRate, int channels, OpusApplication app) {
        Objects.requireNonNull(app, "app cannot be null");
        if (channels < 1 || channels > 2) {
            throw new IllegalArgumentException("channels must be 1 or 2, got " + channels);
        }
        this.arena = Arena.ofShared();
        try {
            var errSeg = arena.allocate(ValueLayout.JAVA_INT);
            try {
                this.state = Opus.opus_encoder_create(sampleRate, channels, app.toNative(), errSeg);
            } catch (Throwable t) {
                throw new WhatsAppCallException.Opus("opus_encoder_create failed", t);
            }
            int err = errSeg.get(ValueLayout.JAVA_INT, 0);
            if (err != Opus.OPUS_OK() || state.equals(MemorySegment.NULL)) {
                throw WhatsAppCallException.Opus.fromErr("opus_encoder_create", err);
            }
            this.pcmBuf = arena.allocate(5760L * channels * 2);
            this.packetBuf = arena.allocate(MAX_PACKET_BYTES);
            this.intScratch = arena.allocate(ValueLayout.JAVA_INT);
            setUseDTX(true);
        } catch (RuntimeException e) {
            destroyState();
            arena.close();
            throw e;
        }
    }

    /**
     * Encodes a single PCM frame and returns the compressed Opus
     * packet bytes.
     *
     * @param pcm       the input PCM samples (signed 16-bit, native
     *                  byte order)
     * @param frameSize the per-channel sample count of one Opus frame
     *                  (e.g. 160 for 10 ms at 16 kHz). Must be a
     *                  legal Opus frame size.
     * @return a fresh byte array of length {@code packetLen}
     *         containing the encoded packet
     * @throws WhatsAppCallException.Opus if encoding fails
     */
    public byte[] encode(short[] pcm, int frameSize) {
        Objects.requireNonNull(pcm, "pcm cannot be null");
        requireOpen();
        MemorySegment.copy(pcm, 0, pcmBuf, ValueLayout.JAVA_SHORT, 0, pcm.length);
        int written;
        try {
            written = Opus.opus_encode(state, pcmBuf, frameSize, packetBuf, MAX_PACKET_BYTES);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Opus("opus_encode failed", t);
        }
        if (written < 0) {
            throw WhatsAppCallException.Opus.fromErr("opus_encode", written);
        }
        var out = new byte[written];
        MemorySegment.copy(packetBuf, ValueLayout.JAVA_BYTE, 0, out, 0, written);
        return out;
    }

    /**
     * Sets the target bitrate in bits per second. Range:
     * {@code 500..512000}; defaults to a profile-dependent value
     * chosen by libopus.
     *
     * @param bps the target bitrate
     */
    public void setBitrate(int bps) {
        ctlSetInt(Opus.OPUS_SET_BITRATE_REQUEST(), bps);
    }

    /**
     * Returns the current target bitrate.
     *
     * @return the bitrate in bits per second
     */
    public int bitrate() {
        return ctlGetInt(Opus.OPUS_GET_BITRATE_REQUEST());
    }

    /**
     * Sets the encoder complexity level (0 = fastest / lowest
     * quality, 10 = slowest / highest quality).
     *
     * @param complexity the level in {@code 0..10}
     */
    public void setComplexity(int complexity) {
        ctlSetInt(Opus.OPUS_SET_COMPLEXITY_REQUEST(), complexity);
    }

    /**
     * Enables or disables DTX (discontinuous transmission). When
     * enabled, the encoder emits very small packets during silence,
     * which the decoder can reconstruct as comfort noise. Defaults
     * to {@code true} for VOIP.
     *
     * @param enabled whether DTX is on
     */
    public void setUseDTX(boolean enabled) {
        ctlSetInt(Opus.OPUS_SET_DTX_REQUEST(), enabled ? 1 : 0);
    }

    /**
     * Returns whether DTX is currently enabled.
     *
     * @return {@code true} if DTX is on
     */
    public boolean useDTX() {
        return ctlGetInt(Opus.OPUS_GET_DTX_REQUEST()) != 0;
    }

    /**
     * Enables or disables in-band FEC (forward error correction).
     * When enabled, the encoder inserts a low-bitrate copy of the
     * previous frame into each packet so the decoder can recover from
     * a single dropped packet. Defaults to {@code false}.
     *
     * @param enabled whether FEC is on
     */
    public void setUseInbandFEC(boolean enabled) {
        ctlSetInt(Opus.OPUS_SET_INBAND_FEC_REQUEST(), enabled ? 1 : 0);
    }

    /**
     * Sets the expected packet-loss percentage in {@code 0..100}.
     * Used together with {@link #setUseInbandFEC(boolean)} to tune
     * FEC redundancy.
     *
     * @param percent the expected loss percentage
     */
    public void setPacketLossPercent(int percent) {
        ctlSetInt(Opus.OPUS_SET_PACKET_LOSS_PERC_REQUEST(), percent);
    }

    /**
     * Resets the encoder's internal state without changing
     * configuration — equivalent to a fresh encoder with the same
     * settings, but reuses the allocation. Useful at the start of a
     * new call.
     */
    public void resetState() {
        ctlNoArg(Opus.OPUS_RESET_STATE());
    }

    /**
     * Sends one integer-valued {@code OPUS_SET_*} control to the
     * encoder via {@code opus_encoder_ctl}.
     *
     * @param request the control request code
     * @param value   the integer payload
     */
    private void ctlSetInt(int request, int value) {
        requireOpen();
        var invoker = INT_CTL_INVOKER;
        if (invoker == null) {
            synchronized (OpusEncoder.class) {
                invoker = INT_CTL_INVOKER;
                if (invoker == null) {
                    invoker = Opus.opus_encoder_ctl.makeInvoker(ValueLayout.JAVA_INT);
                    INT_CTL_INVOKER = invoker;
                }
            }
        }
        int rc;
        try {
            rc = invoker.apply(state, request, value);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Opus("opus_encoder_ctl request=" + request + " failed", t);
        }
        if (rc != Opus.OPUS_OK()) {
            throw WhatsAppCallException.Opus.fromErr("opus_encoder_ctl request=" + request, rc);
        }
    }

    /**
     * Issues an {@code OPUS_GET_*} control whose out-arg is a
     * single {@code int *}, returning the value libopus wrote.
     *
     * @param request the control request code
     * @return the integer libopus wrote
     */
    private int ctlGetInt(int request) {
        requireOpen();
        var invoker = INT_OUT_CTL_INVOKER;
        if (invoker == null) {
            synchronized (OpusEncoder.class) {
                invoker = INT_OUT_CTL_INVOKER;
                if (invoker == null) {
                    invoker = Opus.opus_encoder_ctl.makeInvoker(ValueLayout.ADDRESS);
                    INT_OUT_CTL_INVOKER = invoker;
                }
            }
        }
        intScratch.set(ValueLayout.JAVA_INT, 0, 0);
        int rc;
        try {
            rc = invoker.apply(state, request, intScratch);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Opus("opus_encoder_ctl request=" + request + " failed", t);
        }
        if (rc != Opus.OPUS_OK()) {
            throw WhatsAppCallException.Opus.fromErr("opus_encoder_ctl request=" + request, rc);
        }
        return intScratch.get(ValueLayout.JAVA_INT, 0);
    }

    /**
     * Issues a no-argument control like {@code OPUS_RESET_STATE} via
     * the underlying variadic CTL surface.
     *
     * @param request the control request code
     */
    private void ctlNoArg(int request) {
        requireOpen();
        // OPUS_RESET_STATE has no payload — pass a dummy zero so the
        // variadic invoker descriptor stays uniform with the
        // integer-control case.
        ctlSetInt(request, 0);
    }

    /**
     * Throws if the encoder has been closed.
     */
    private void requireOpen() {
        if (state == null || state.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("OpusEncoder is closed");
        }
    }

    /**
     * Calls {@code opus_encoder_destroy} if the state pointer is
     * still live.
     */
    private void destroyState() {
        if (state == null || state.equals(MemorySegment.NULL)) {
            return;
        }
        try {
            Opus.opus_encoder_destroy(state);
        } catch (Throwable _) {
        }
        state = MemorySegment.NULL;
    }

    /**
     * Destroys the encoder state and releases the per-instance
     * arena. Idempotent.
     */
    @Override
    public void close() {
        if (state == null || state.equals(MemorySegment.NULL)) {
            return;
        }
        destroyState();
        arena.close();
    }
}
