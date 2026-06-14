package com.github.auties00.cobalt.call.audio.opus;

import com.github.auties00.cobalt.call.audio.opus.bindings.Opus;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.util.NativeLibLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * Decodes Opus packets into PCM samples through libopus over the Foreign
 * Function and Memory API.
 *
 * <p>Each instance owns one native {@code OpusDecoder} state and a
 * reusable native output buffer, both allocated from a per-instance
 * arena. Decoding is performed by copying the packet into a confined
 * native scratch segment, invoking {@code opus_decode}, and copying the
 * resulting interleaved 16-bit PCM back into a fresh Java {@code short[]}.
 * The instance is not thread-safe: the shared output buffer and the
 * native state must be driven from a single thread. Closing the decoder
 * destroys the native state and releases the arena; any later decode call
 * throws.
 *
 * <p>To match WhatsApp's call engine, construct as
 * {@code new OpusDecoder(16000, 1)} and decode 160 samples per 10 ms
 * frame.
 */
public final class OpusDecoder implements AutoCloseable {
    static {
        NativeLibLoader.load("cobalt-native", Arena.global());
    }

    /**
     * Upper bound, in samples per channel, on a single decoded Opus frame.
     *
     * @implNote This implementation uses 5760, the sample count of a
     * 60 ms frame at 48 kHz, which is the largest frame duration and
     * sample rate the Opus format permits. The native output buffer is
     * sized for this worst case so that any legal {@code frameSize} fits
     * without reallocation.
     */
    private static final int MAX_FRAME_SAMPLES = 5760;

    /**
     * Per-instance arena owning the native decoder state pointer and the
     * reusable PCM output buffer.
     *
     * <p>The arena is shared rather than confined so the segments it
     * allocates outlive the constructing call frame; it is closed by
     * {@link #close()}.
     */
    private final Arena arena;

    /**
     * Pointer to the native {@code OpusDecoder} state allocated by
     * {@code opus_decoder_create}.
     *
     * <p>Set to {@link MemorySegment#NULL} once the state is destroyed, so
     * that {@link #requireOpen()} can detect a closed decoder.
     */
    private MemorySegment state;

    /**
     * Channel count this decoder was created with, either 1 for mono or 2
     * for stereo.
     *
     * <p>Used to derive the interleaved output length as
     * {@code samplesDecoded * channels}.
     */
    private final int channels;

    /**
     * Reusable native buffer that {@code opus_decode} writes the decoded
     * interleaved PCM into.
     *
     * <p>Sized once at construction for the worst-case frame
     * ({@link #MAX_FRAME_SAMPLES} samples per channel, two bytes per
     * sample) and reused across every decode call.
     */
    private final MemorySegment pcmBuf;

    /**
     * Constructs a decoder for the given output sample rate and channel
     * count.
     *
     * <p>Allocates the native decoder state via {@code opus_decoder_create}
     * and the reusable PCM output buffer from a fresh shared arena. If
     * libopus reports a non-zero error code or returns a null state
     * pointer, the partially built native state and the arena are released
     * before the exception propagates, so a failed construction leaks
     * nothing.
     *
     * @param sampleRate the output sample rate in Hz; one of 8000, 12000,
     *                   16000, 24000, or 48000
     * @param channels   the channel count, 1 for mono or 2 for stereo
     * @throws IllegalArgumentException  if {@code channels} is not 1 or 2
     * @throws WhatsAppCallException.Opus if libopus rejects the configuration
     * @throws UnsatisfiedLinkError      if libopus cannot be loaded
     */
    public OpusDecoder(int sampleRate, int channels) {
        if (channels < 1 || channels > 2) {
            throw new IllegalArgumentException("channels must be 1 or 2, got " + channels);
        }
        this.channels = channels;
        this.arena = Arena.ofShared();
        try {
            var errSeg = arena.allocate(ValueLayout.JAVA_INT);
            try {
                this.state = Opus.opus_decoder_create(sampleRate, channels, errSeg);
            } catch (Throwable t) {
                throw new WhatsAppCallException.Opus("opus_decoder_create failed", t);
            }
            var err = errSeg.get(ValueLayout.JAVA_INT, 0);
            if (err != Opus.OPUS_OK() || state.equals(MemorySegment.NULL)) {
                throw WhatsAppCallException.Opus.fromErr("opus_decoder_create", err);
            }
            this.pcmBuf = arena.allocate((long) MAX_FRAME_SAMPLES * channels * 2);
        } catch (RuntimeException e) {
            destroyState();
            arena.close();
            throw e;
        }
    }

    /**
     * Decodes a single Opus packet into interleaved PCM samples.
     *
     * <p>The packet bytes are copied into a confined native scratch
     * segment and passed to {@code opus_decode} together with the buffer
     * cap given by {@code frameSize}. The number of samples libopus
     * actually decoded determines the length of the returned array, which
     * is {@code samplesDecoded * channels}. A negative return from
     * {@code opus_decode} is turned into a thrown exception.
     *
     * @param packet    the Opus-encoded packet bytes
     * @param frameSize the maximum number of per-channel samples to
     *                  decode, for example 160 for a 10 ms frame at 16 kHz
     * @return a fresh interleaved-PCM {@code short[]} of length
     *         {@code samplesDecoded * channels}
     * @throws NullPointerException       if {@code packet} is {@code null}
     * @throws IllegalStateException      if the decoder is closed
     * @throws WhatsAppCallException.Opus if decoding fails
     */
    public short[] decode(byte[] packet, int frameSize) {
        Objects.requireNonNull(packet, "packet cannot be null");
        requireOpen();
        try (var scratch = Arena.ofConfined()) {
            var data = scratch.allocate(packet.length);
            MemorySegment.copy(packet, 0, data, ValueLayout.JAVA_BYTE, 0, packet.length);
            int samples;
            try {
                samples = Opus.opus_decode(state, data, packet.length, pcmBuf, frameSize, 0);
            } catch (Throwable t) {
                throw new WhatsAppCallException.Opus("opus_decode failed", t);
            }
            if (samples < 0) {
                throw WhatsAppCallException.Opus.fromErr("opus_decode", samples);
            }
            var out = new short[samples * channels];
            MemorySegment.copy(pcmBuf, ValueLayout.JAVA_SHORT, 0, out, 0, out.length);
            return out;
        }
    }

    /**
     * Generates concealment PCM for a lost packet using Opus packet-loss
     * concealment.
     *
     * <p>This invokes {@code opus_decode} with a null packet and zero
     * length, the libopus convention that requests packet-loss
     * concealment for one missing frame; the codec synthesizes plausible
     * audio from its decode history instead of returning silence. The
     * returned array has length {@code samplesDecoded * channels}.
     *
     * @param frameSize the frame size in per-channel samples
     * @return a fresh interleaved-PCM {@code short[]} holding the
     *         concealment samples
     * @throws IllegalStateException      if the decoder is closed
     * @throws WhatsAppCallException.Opus if concealment fails
     */
    public short[] decodePacketLoss(int frameSize) {
        requireOpen();
        int samples;
        try {
            samples = Opus.opus_decode(state, MemorySegment.NULL, 0, pcmBuf, frameSize, 0);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Opus("opus_decode (PLC) failed", t);
        }
        if (samples < 0) {
            throw WhatsAppCallException.Opus.fromErr("opus_decode (PLC)", samples);
        }
        var out = new short[samples * channels];
        MemorySegment.copy(pcmBuf, ValueLayout.JAVA_SHORT, 0, out, 0, out.length);
        return out;
    }

    /**
     * Resets the decoder's internal state to its just-created condition.
     *
     * <p>Issues the {@code OPUS_RESET_STATE} control through
     * {@code opus_decoder_ctl}, clearing decode history (including the
     * packet-loss concealment buffer) without changing the configured
     * sample rate or channel count. A non-zero return code is turned into
     * a thrown exception.
     *
     * @throws IllegalStateException      if the decoder is closed
     * @throws WhatsAppCallException.Opus if the control call fails
     */
    public void resetState() {
        requireOpen();
        var invoker = Opus.opus_decoder_ctl.makeInvoker(ValueLayout.JAVA_INT);
        int rc;
        try {
            rc = invoker.apply(state, Opus.OPUS_RESET_STATE(), 0);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Opus("opus_decoder_ctl OPUS_RESET_STATE failed", t);
        }
        if (rc != Opus.OPUS_OK()) {
            throw WhatsAppCallException.Opus.fromErr("opus_decoder_ctl OPUS_RESET_STATE", rc);
        }
    }

    /**
     * Verifies that the decoder is still open.
     *
     * <p>Treats both a {@code null} state field and a
     * {@link MemorySegment#NULL} state pointer as closed.
     *
     * @throws IllegalStateException if the decoder has been closed
     */
    private void requireOpen() {
        if (state == null || state.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("OpusDecoder is closed");
        }
    }

    /**
     * Destroys the native decoder state if it is still live.
     *
     * <p>Calls {@code opus_decoder_destroy} and then nulls the state
     * pointer to {@link MemorySegment#NULL}. Any throwable raised by the
     * native call is swallowed so this method can run safely from a failed
     * constructor or from {@link #close()}.
     */
    private void destroyState() {
        if (state == null || state.equals(MemorySegment.NULL)) {
            return;
        }
        try {
            Opus.opus_decoder_destroy(state);
        } catch (Throwable _) {
        }
        state = MemorySegment.NULL;
    }

    /**
     * Destroys the native decoder state and releases the per-instance
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
