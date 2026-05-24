package com.github.auties00.cobalt.call.internal.audio.opus;

import com.github.auties00.cobalt.call.internal.audio.opus.bindings.Opus;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.util.NativeLibLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * Opus decoder, backed by libopus via FFM.
 *
 * <p>To match WhatsApp's wasm engine, construct as
 * {@code new OpusDecoder(16000, 1)} and decode at 160 samples per
 * 10 ms frame.
 */
public final class OpusDecoder implements AutoCloseable {
    static {
        NativeLibLoader.load("opus", Arena.global());
    }

    /**
     * Largest legal Opus frame size in samples per channel: 60 ms at
     * 48 kHz.
     */
    private static final int MAX_FRAME_SAMPLES = 5760;

    /**
     * Per-instance arena for the decoder state pointer and the
     * native PCM scratch buffer.
     */
    private final Arena arena;

    /**
     * Pointer to the {@code OpusDecoder} state allocated by
     * {@code opus_decoder_create}. Nulled by {@link #close}.
     */
    private MemorySegment state;

    /**
     * The decoder's channel count.
     */
    private final int channels;

    /**
     * Reusable native PCM output buffer.
     */
    private final MemorySegment pcmBuf;

    /**
     * Constructs a new decoder.
     *
     * @param sampleRate the output sample rate (8000, 12000, 16000,
     *                   24000, or 48000 Hz)
     * @param channels   1 for mono, 2 for stereo
     * @throws IllegalArgumentException if {@code channels} is not 1 or 2
     * @throws WhatsAppCallException.Opus       if libopus rejects the
     *                                  configuration
     * @throws UnsatisfiedLinkError     if libopus cannot be loaded
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
     * Decodes a single Opus packet into PCM samples.
     *
     * @param packet    the Opus-encoded packet bytes
     * @param frameSize the maximum number of per-channel samples to
     *                  decode (e.g. 160 for 10 ms at 16 kHz)
     * @return a fresh {@code short[]} of length
     *         {@code samplesDecoded * channels} containing the
     *         interleaved PCM
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
     * Generates concealment PCM for a lost packet using Opus's
     * built-in PLC (packet loss concealment).
     *
     * @param frameSize the frame size in per-channel samples
     * @return concealment PCM samples
     * @throws WhatsAppCallException.Opus if PLC fails
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
     * Resets the decoder's internal state.
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
     * Throws if the decoder has been closed.
     */
    private void requireOpen() {
        if (state == null || state.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("OpusDecoder is closed");
        }
    }

    /**
     * Calls {@code opus_decoder_destroy} if the state pointer is
     * still live.
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
     * Destroys the decoder state and releases the per-instance
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
