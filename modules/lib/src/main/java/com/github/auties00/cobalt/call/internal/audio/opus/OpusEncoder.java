package com.github.auties00.cobalt.call.internal.audio.opus;

import com.github.auties00.cobalt.call.internal.audio.opus.bindings.Opus;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.util.NativeLibLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * Encodes PCM frames into Opus packets through libopus over the Foreign
 * Function and Memory API.
 *
 * <p>Each instance owns one native {@code OpusEncoder} state and a set of
 * reusable native scratch buffers (PCM input, packet output, and a
 * single-int control argument), all allocated from a per-instance arena.
 * Encoding copies the caller's PCM into the input buffer, invokes
 * {@code opus_encode}, and copies the produced packet bytes back into a
 * fresh Java array sized to the packet's actual length. Bitrate,
 * complexity, DTX, FEC, and expected packet loss are adjusted through the
 * {@code opus_encoder_ctl} controls exposed as setters. The instance is
 * not thread-safe: the shared buffers and the native state must be driven
 * from a single thread. Closing the encoder destroys the native state and
 * releases the arena; any later call throws.
 *
 * <p>WhatsApp's call engine encodes voice at 16 kHz mono in 10 ms frames
 * (160 samples per frame, 320 bytes of PCM); to match it, construct as
 * {@code new OpusEncoder(16000, 1, OpusApplication.VOIP)} and feed
 * 160-sample {@code short[]} buffers.
 */
public final class OpusEncoder implements AutoCloseable {
    static {
        NativeLibLoader.load("opus", Arena.global());
    }

    /**
     * Capacity, in bytes, of the native output buffer provisioned for one
     * encoded packet.
     *
     * @implNote This implementation uses 1500, comfortably above the 1276
     * bytes that RFC 6716 gives as the largest single Opus frame; 10 ms
     * voice frames at typical bitrates are far smaller. The buffer is
     * allocated once at construction so encoding never sizes per call.
     */
    public static final int MAX_PACKET_BYTES = 1500;

    /**
     * Per-instance arena owning the native encoder state pointer and the
     * reusable scratch buffers.
     *
     * <p>The arena is shared rather than confined so the segments it
     * allocates outlive the constructing call frame; it is closed by
     * {@link #close()}.
     */
    private final Arena arena;

    /**
     * Pointer to the native {@code OpusEncoder} state allocated by
     * {@code opus_encoder_create}.
     *
     * <p>Set to {@link MemorySegment#NULL} once the state is destroyed, so
     * that {@link #requireOpen()} can detect a closed encoder.
     */
    private MemorySegment state;

    /**
     * Reusable native buffer that {@link #encode(short[], int)} copies the
     * caller's PCM into before invoking {@code opus_encode}.
     *
     * @implNote This implementation sizes the buffer for a 60 ms frame at
     * 48 kHz stereo (5760 samples per channel, two channels, two bytes per
     * sample), the worst case the Opus format permits, so any legal frame
     * fits without reallocation.
     */
    private final MemorySegment pcmBuf;

    /**
     * Reusable native buffer that {@code opus_encode} writes the encoded
     * packet bytes into.
     *
     * <p>Sized once at construction to {@link #MAX_PACKET_BYTES} bytes.
     */
    private final MemorySegment packetBuf;

    /**
     * Single-int native scratch segment used as the {@code int *} out-arg
     * for {@code OPUS_GET_*} controls.
     *
     * <p>Reused across every integer-out control call; the caller writes a
     * zero into it before each invocation and reads back the value libopus
     * stored.
     */
    private final MemorySegment intScratch;

    /**
     * Lazily-built variadic invoker for the
     * {@code opus_encoder_ctl(state, request, int)} call shape shared by
     * every {@code OPUS_SET_*} integer control.
     *
     * @implNote This implementation caches one invoker per shape in a
     * static {@code volatile} field, built under double-checked locking on
     * the class monitor, because the jextract-generated variadic linker is
     * relatively expensive to materialize and the descriptor is identical
     * for all integer-set controls across all encoder instances.
     */
    private static volatile Opus.opus_encoder_ctl INT_CTL_INVOKER;

    /**
     * Lazily-built variadic invoker for the
     * {@code opus_encoder_ctl(state, request, int *)} call shape shared by
     * every {@code OPUS_GET_*} integer-out control.
     *
     * @implNote This implementation caches one invoker per shape in a
     * static {@code volatile} field, built under double-checked locking on
     * the class monitor, for the same reason as {@link #INT_CTL_INVOKER}:
     * the integer-out descriptor is identical across all instances and is
     * worth building only once.
     */
    private static volatile Opus.opus_encoder_ctl INT_OUT_CTL_INVOKER;

    /**
     * Constructs an encoder for the given sample rate, channel count, and
     * application mode.
     *
     * <p>Allocates the native encoder state via {@code opus_encoder_create}
     * and the reusable scratch buffers from a fresh shared arena, then
     * enables DTX so silence frames are sent as the smallest possible
     * packets. If libopus reports a non-zero error code or returns a null
     * state pointer, the partially built native state and the arena are
     * released before the exception propagates, so a failed construction
     * leaks nothing.
     *
     * @param sampleRate the input sample rate in Hz; one of 8000, 12000,
     *                   16000, 24000, or 48000
     * @param channels   the channel count, 1 for mono or 2 for stereo
     * @param app        the application mode, {@link OpusApplication#VOIP}
     *                   for calls
     * @throws NullPointerException      if {@code app} is {@code null}
     * @throws IllegalArgumentException  if {@code channels} is not 1 or 2
     * @throws WhatsAppCallException.Opus if libopus rejects the configuration
     * @throws UnsatisfiedLinkError      if libopus cannot be loaded
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
            var err = errSeg.get(ValueLayout.JAVA_INT, 0);
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
     * Encodes one PCM frame and returns the compressed Opus packet.
     *
     * <p>The caller's samples are copied into the reusable native input
     * buffer and passed to {@code opus_encode} together with the
     * per-channel frame size and the output buffer cap
     * ({@link #MAX_PACKET_BYTES}). The byte count libopus reports
     * determines the length of the returned array. A negative return is
     * turned into a thrown exception; a return of one byte indicates a DTX
     * silence frame.
     *
     * @param pcm       the input PCM samples, signed 16-bit in native byte
     *                  order, interleaved if stereo
     * @param frameSize the per-channel sample count of one Opus frame, for
     *                  example 160 for a 10 ms frame at 16 kHz; must be a
     *                  legal Opus frame size
     * @return a fresh {@code byte[]} of the encoded packet's exact length
     * @throws NullPointerException       if {@code pcm} is {@code null}
     * @throws IllegalStateException      if the encoder is closed
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
     * Sets the target bitrate in bits per second.
     *
     * <p>Issues the {@code OPUS_SET_BITRATE} control. Accepted values
     * range from 500 to 512000; when left unset, libopus chooses a
     * profile-dependent default.
     *
     * @param bps the target bitrate in bits per second
     * @throws IllegalStateException      if the encoder is closed
     * @throws WhatsAppCallException.Opus if the control call fails
     */
    public void setBitrate(int bps) {
        ctlSetInt(Opus.OPUS_SET_BITRATE_REQUEST(), bps);
    }

    /**
     * Returns the current target bitrate.
     *
     * <p>Issues the {@code OPUS_GET_BITRATE} control and returns the value
     * libopus wrote.
     *
     * @return the target bitrate in bits per second
     * @throws IllegalStateException      if the encoder is closed
     * @throws WhatsAppCallException.Opus if the control call fails
     */
    public int bitrate() {
        return ctlGetInt(Opus.OPUS_GET_BITRATE_REQUEST());
    }

    /**
     * Sets the encoder complexity level.
     *
     * <p>Issues the {@code OPUS_SET_COMPLEXITY} control. The level ranges
     * from 0 (fastest, lowest quality) to 10 (slowest, highest quality).
     *
     * @param complexity the complexity level in 0..10
     * @throws IllegalStateException      if the encoder is closed
     * @throws WhatsAppCallException.Opus if the control call fails
     */
    public void setComplexity(int complexity) {
        ctlSetInt(Opus.OPUS_SET_COMPLEXITY_REQUEST(), complexity);
    }

    /**
     * Enables or disables discontinuous transmission (DTX).
     *
     * <p>Issues the {@code OPUS_SET_DTX} control. With DTX on, the encoder
     * emits very small packets during silence that the decoder
     * reconstructs as comfort noise. This encoder enables DTX at
     * construction to match WhatsApp's voice configuration.
     *
     * @param enabled {@code true} to enable DTX, {@code false} to disable it
     * @throws IllegalStateException      if the encoder is closed
     * @throws WhatsAppCallException.Opus if the control call fails
     */
    public void setUseDTX(boolean enabled) {
        ctlSetInt(Opus.OPUS_SET_DTX_REQUEST(), enabled ? 1 : 0);
    }

    /**
     * Returns whether discontinuous transmission (DTX) is enabled.
     *
     * <p>Issues the {@code OPUS_GET_DTX} control.
     *
     * @return {@code true} if DTX is enabled
     * @throws IllegalStateException      if the encoder is closed
     * @throws WhatsAppCallException.Opus if the control call fails
     */
    public boolean useDTX() {
        return ctlGetInt(Opus.OPUS_GET_DTX_REQUEST()) != 0;
    }

    /**
     * Enables or disables in-band forward error correction (FEC).
     *
     * <p>Issues the {@code OPUS_SET_INBAND_FEC} control. With FEC on, the
     * encoder embeds a low-bitrate copy of the previous frame in each
     * packet so the decoder can recover from a single dropped packet. FEC
     * is off unless this is called; tune its redundancy with
     * {@link #setPacketLossPercent(int)}.
     *
     * @param enabled {@code true} to enable FEC, {@code false} to disable it
     * @throws IllegalStateException      if the encoder is closed
     * @throws WhatsAppCallException.Opus if the control call fails
     */
    public void setUseInbandFEC(boolean enabled) {
        ctlSetInt(Opus.OPUS_SET_INBAND_FEC_REQUEST(), enabled ? 1 : 0);
    }

    /**
     * Sets the expected packet-loss percentage.
     *
     * <p>Issues the {@code OPUS_SET_PACKET_LOSS_PERC} control. The value
     * ranges from 0 to 100 and, in combination with
     * {@link #setUseInbandFEC(boolean)}, drives how much redundancy the
     * encoder spends on loss recovery.
     *
     * @param percent the expected loss percentage in 0..100
     * @throws IllegalStateException      if the encoder is closed
     * @throws WhatsAppCallException.Opus if the control call fails
     */
    public void setPacketLossPercent(int percent) {
        ctlSetInt(Opus.OPUS_SET_PACKET_LOSS_PERC_REQUEST(), percent);
    }

    /**
     * Resets the encoder's internal state without changing its
     * configuration.
     *
     * <p>Issues the {@code OPUS_RESET_STATE} control, returning the
     * encoder to the behavior of a freshly created one with the same
     * settings while reusing the existing allocation. Useful at the start
     * of a new call.
     *
     * @throws IllegalStateException      if the encoder is closed
     * @throws WhatsAppCallException.Opus if the control call fails
     */
    public void resetState() {
        ctlNoArg(Opus.OPUS_RESET_STATE());
    }

    /**
     * Sends one integer-valued {@code OPUS_SET_*} control to the encoder.
     *
     * <p>Lazily materializes the shared integer-set invoker
     * ({@link #INT_CTL_INVOKER}) under double-checked locking, then invokes
     * {@code opus_encoder_ctl} with the request code and payload. A
     * non-zero return code is turned into a thrown exception.
     *
     * @param request the {@code OPUS_SET_*} control request code
     * @param value   the integer payload for the control
     * @throws IllegalStateException      if the encoder is closed
     * @throws WhatsAppCallException.Opus if the control call fails
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
     * Issues an {@code OPUS_GET_*} control whose out-arg is a single
     * {@code int *} and returns the value libopus wrote.
     *
     * <p>Lazily materializes the shared integer-out invoker
     * ({@link #INT_OUT_CTL_INVOKER}) under double-checked locking, zeroes
     * the {@link #intScratch} segment, invokes {@code opus_encoder_ctl}
     * with the request code and the scratch pointer, then reads the value
     * back. A non-zero return code is turned into a thrown exception.
     *
     * @param request the {@code OPUS_GET_*} control request code
     * @return the integer libopus stored into {@link #intScratch}
     * @throws IllegalStateException      if the encoder is closed
     * @throws WhatsAppCallException.Opus if the control call fails
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
     * Issues a payload-less control such as {@code OPUS_RESET_STATE}.
     *
     * @implNote This implementation forwards to {@link #ctlSetInt(int, int)}
     * with a dummy zero argument so the shared integer-set invoker
     * descriptor stays uniform; libopus ignores the extra argument for
     * controls that take no payload.
     *
     * @param request the no-argument control request code
     * @throws IllegalStateException      if the encoder is closed
     * @throws WhatsAppCallException.Opus if the control call fails
     */
    private void ctlNoArg(int request) {
        requireOpen();
        ctlSetInt(request, 0);
    }

    /**
     * Verifies that the encoder is still open.
     *
     * <p>Treats both a {@code null} state field and a
     * {@link MemorySegment#NULL} state pointer as closed.
     *
     * @throws IllegalStateException if the encoder has been closed
     */
    private void requireOpen() {
        if (state == null || state.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("OpusEncoder is closed");
        }
    }

    /**
     * Destroys the native encoder state if it is still live.
     *
     * <p>Calls {@code opus_encoder_destroy} and then nulls the state
     * pointer to {@link MemorySegment#NULL}. Any throwable raised by the
     * native call is swallowed so this method can run safely from a failed
     * constructor or from {@link #close()}.
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
     * Destroys the native encoder state and releases the per-instance
     * arena.
     *
     * <p>Idempotent: a second call after the state has already been
     * destroyed returns without effect.
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
