package com.github.auties00.cobalt.call.internal.audio.processing;

import com.github.auties00.cobalt.call.internal.audio.processing.bindings.SpeexDsp;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.util.NativeLibLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * Acoustic echo canceller, backed by speexdsp's
 * {@code speex_echo_*} family. Bindings are jextract-generated; this
 * class is the high-level idiomatic-Java wrapper around them.
 *
 * <p>Pipeline:
 *
 * <pre>{@code
 *   var aec = new EchoCanceller(160 /*10ms@16kHz*\/, 1600 /*100ms tail*\/, 16000);
 *   while (call running) {
 *       short[] far  = receivedFromPeerThisFrame();
 *       short[] near = capturedFromMicThisFrame();
 *       short[] cleaned = aec.cancel(near, far);
 *       // feed `cleaned` into Opus encode
 *   }
 *   aec.close();
 * }</pre>
 *
 * <p>Filter length: speexdsp recommends {@code ~tail_ms * Fs / 1000};
 * 100 ms is a good default for a normal-sized room. Longer is not
 * better — adapter convergence slows.
 */
public final class EchoCanceller implements AutoCloseable {
    static {
        NativeLibLoader.load("speexdsp", Arena.global());
    }

    /**
     * Per-instance arena for native scratch buffers (state pointer
     * is heap-allocated by speexdsp itself, so isn't part of the
     * arena's lifetime).
     */
    private final Arena arena;

    /**
     * Pointer to the C-side {@code SpeexEchoState}; nulled out by
     * {@link #close()}.
     */
    private MemorySegment state;

    /**
     * Per-channel sample count of one frame.
     */
    private final int frameSize;

    /**
     * Reusable scratch segment for the mic (near-end) buffer.
     */
    private final MemorySegment recBuf;

    /**
     * Reusable scratch segment for the far-end (speaker) reference.
     */
    private final MemorySegment playBuf;

    /**
     * Reusable scratch segment for the cleaned output.
     */
    private final MemorySegment outBuf;

    /**
     * Constructs a new echo canceller.
     *
     * @param frameSize    the per-channel sample count of one frame
     *                     (e.g. 160 for 10 ms at 16 kHz)
     * @param filterLength the AEC tail length in samples — should be
     *                     roughly {@code tailMs * sampleRate / 1000}
     * @param sampleRate   the input/output sample rate in Hz (used
     *                     by the matching {@link AudioPreprocessor})
     * @throws IllegalArgumentException if any argument is &lt; 1
     * @throws UnsatisfiedLinkError     if libspeexdsp cannot be
     *                                  loaded on the running
     *                                  platform
     */
    public EchoCanceller(int frameSize, int filterLength, int sampleRate) {
        if (frameSize < 1) throw new IllegalArgumentException("frameSize must be ≥ 1");
        if (filterLength < 1) throw new IllegalArgumentException("filterLength must be ≥ 1");
        if (sampleRate < 1) throw new IllegalArgumentException("sampleRate must be ≥ 1");
        this.arena = Arena.ofShared();
        this.frameSize = frameSize;
        try {
            this.state = SpeexDsp.speex_echo_state_init(frameSize, filterLength);
        } catch (Throwable t) {
            arena.close();
            throw new WhatsAppCallException.SpeexDsp("speex_echo_state_init failed", t);
        }
        if (state.equals(MemorySegment.NULL)) {
            arena.close();
            throw new WhatsAppCallException.SpeexDsp("speex_echo_state_init returned NULL");
        }
        var byteSize = (long) frameSize * 2;
        this.recBuf = arena.allocate(byteSize);
        this.playBuf = arena.allocate(byteSize);
        this.outBuf = arena.allocate(byteSize);
    }

    /**
     * Cancels the far-end echo from the near-end (mic) signal.
     *
     * @param mic the mic capture (near-end) — exactly
     *            {@link #frameSize()} mono int16 samples
     * @param far the far-end (speaker) reference — what was played
     *            this same frame, exactly {@link #frameSize()}
     *            samples
     * @return a fresh {@code short[]} of {@link #frameSize()}
     *         containing the echo-cancelled mic signal
     * @throws IllegalArgumentException if either array is the wrong
     *                                  length
     * @throws IllegalStateException    if {@link #close} has been
     *                                  called
     */
    public short[] cancel(short[] mic, short[] far) {
        Objects.requireNonNull(mic, "mic cannot be null");
        Objects.requireNonNull(far, "far cannot be null");
        if (mic.length != frameSize) {
            throw new IllegalArgumentException("mic must be " + frameSize + " samples, got " + mic.length);
        }
        if (far.length != frameSize) {
            throw new IllegalArgumentException("far must be " + frameSize + " samples, got " + far.length);
        }
        requireOpenState();
        recBuf.copyFrom(MemorySegment.ofArray(mic));
        playBuf.copyFrom(MemorySegment.ofArray(far));
        try {
            SpeexDsp.speex_echo_cancellation(state, recBuf, playBuf, outBuf);
        } catch (Throwable t) {
            throw new WhatsAppCallException.SpeexDsp("speex_echo_cancellation failed", t);
        }
        var out = new short[frameSize];
        MemorySegment.copy(outBuf, ValueLayout.JAVA_SHORT, 0, out, 0, frameSize);
        return out;
    }

    /**
     * Resets the canceller's adaptive filter state, e.g. on
     * acoustic-path change (speaker volume bump, headphone plug,
     * etc.).
     */
    public void reset() {
        requireOpenState();
        try {
            SpeexDsp.speex_echo_state_reset(state);
        } catch (Throwable t) {
            throw new WhatsAppCallException.SpeexDsp("speex_echo_state_reset failed", t);
        }
    }

    /**
     * Returns the underlying {@code SpeexEchoState} pointer for use
     * by {@link AudioPreprocessor#linkEchoState} — the preprocessor's
     * residual-echo suppressor cooperates with the AEC's own
     * estimate.
     *
     * @return the state pointer
     */
    MemorySegment state() {
        requireOpenState();
        return state;
    }

    /**
     * Throws if the underlying C state has been destroyed via
     * {@link #close}.
     */
    private void requireOpenState() {
        if (state == null || state.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("EchoCanceller has been closed");
        }
    }

    /**
     * Returns the configured frame size.
     *
     * @return the per-channel samples per frame
     */
    public int frameSize() {
        return frameSize;
    }

    /**
     * Destroys the C-side state and releases scratch buffers.
     * Idempotent.
     */
    @Override
    public void close() {
        if (state == null || state.equals(MemorySegment.NULL)) {
            return;
        }
        try {
            SpeexDsp.speex_echo_state_destroy(state);
        } catch (Throwable t) {
            throw new WhatsAppCallException.SpeexDsp("speex_echo_state_destroy failed", t);
        } finally {
            state = MemorySegment.NULL;
            arena.close();
        }
    }
}
