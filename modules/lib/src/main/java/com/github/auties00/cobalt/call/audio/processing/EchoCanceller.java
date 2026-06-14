package com.github.auties00.cobalt.call.audio.processing;

import com.github.auties00.cobalt.call.audio.processing.bindings.SpeexDsp;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.util.NativeLibLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * Wraps the speexdsp {@code speex_echo_*} family to subtract the far-end (speaker) signal from the
 * near-end (microphone) capture.
 *
 * <p>An instance holds a single C-side {@code SpeexEchoState} sized to one fixed frame and one
 * adaptive-filter length, both supplied at construction. For each frame {@link #cancel(short[], short[])}
 * takes the microphone capture together with the audio that was played out over the same interval and
 * returns a fresh frame with the acoustic echo removed. The adaptive filter converges over successive
 * frames; {@link #reset()} clears that adaptation when the acoustic path changes (a speaker-volume
 * change, a headphone being plugged in, and similar). The instance is single-threaded and owns native
 * memory; {@link #close()} destroys the underlying state and must run when the call ends.
 *
 * <p>A canceller pairs with an {@link AudioPreprocessor} over the same frame size and sample rate.
 * The canceller runs first on each frame and the preprocessor second, and
 * {@link AudioPreprocessor#linkEchoState(EchoCanceller)} lets the preprocessor's residual-echo
 * suppressor consult this canceller's estimate through {@link #state()}:
 *
 * {@snippet :
 * var aec = new EchoCanceller(160, 1600, 16000);
 * while (callRunning) {
 *     short[] far  = receivedFromPeerThisFrame();
 *     short[] near = capturedFromMicThisFrame();
 *     short[] cleaned = aec.cancel(near, far);
 *     // feed cleaned into Opus encode
 * }
 * aec.close();
 * }
 *
 * @implNote This implementation expresses the adaptive-filter tail as a sample count
 * ({@code filterLength}) rather than a duration. speexdsp recommends a tail of
 * {@code tailMs * sampleRate / 1000} samples; 100 ms (1600 samples at 16 kHz) suits a normal-sized
 * room. A longer filter does not improve cancellation unconditionally: a larger filter slows the
 * adaptive convergence, so the tail is sized to the expected acoustic path rather than maximized.
 */
public final class EchoCanceller implements AutoCloseable {
    static {
        NativeLibLoader.load("cobalt-native", Arena.global());
    }

    /**
     * Owns the native scratch buffers allocated for this instance.
     *
     * <p>This arena is created shared at construction and closed by {@link #close()}, releasing
     * {@link #recBuf}, {@link #playBuf}, and {@link #outBuf}. The C-side {@code SpeexEchoState} is
     * allocated by speexdsp itself and is not part of this arena's lifetime; it is freed separately.
     */
    private final Arena arena;

    /**
     * Points to the C-side {@code SpeexEchoState} backing this canceller.
     *
     * <p>This field is assigned the value returned by {@code speex_echo_state_init} during
     * construction and reset to {@link MemorySegment#NULL} by {@link #close()}. A {@code null} or
     * {@code NULL} value marks a closed instance, which {@link #requireOpenState()} rejects.
     */
    private MemorySegment state;

    /**
     * Holds the per-channel sample count of one frame.
     *
     * <p>Both arrays passed to {@link #cancel(short[], short[])} must hold exactly this many samples,
     * and the returned array holds exactly this many.
     */
    private final int frameSize;

    /**
     * Provides reusable native storage for the microphone (near-end) frame handed to
     * {@code speex_echo_cancellation}.
     *
     * <p>This segment spans {@code frameSize * 2} bytes and is reused across frames so that no
     * per-call native allocation occurs for the input.
     */
    private final MemorySegment recBuf;

    /**
     * Provides reusable native storage for the far-end (speaker) reference frame handed to
     * {@code speex_echo_cancellation}.
     */
    private final MemorySegment playBuf;

    /**
     * Provides reusable native storage for the echo-cancelled output produced by
     * {@code speex_echo_cancellation}.
     *
     * <p>The contents are copied into a freshly allocated {@code short[]} on each
     * {@link #cancel(short[], short[])} call.
     */
    private final MemorySegment outBuf;

    /**
     * Constructs an echo canceller sized to the given frame, adaptive-filter length, and sample rate.
     *
     * <p>The frame size and sample rate must match the {@link AudioPreprocessor} the call pairs this
     * canceller with. On failure the shared arena is closed before the exception propagates, so a
     * thrown constructor leaks no native memory.
     *
     * @param frameSize    the per-channel sample count of one frame, such as {@code 160} for 10 ms at 16 kHz
     * @param filterLength the adaptive-filter tail length in samples, roughly {@code tailMs * sampleRate / 1000}
     * @param sampleRate   the input and output sample rate in Hz, shared with the paired {@link AudioPreprocessor}
     * @throws IllegalArgumentException if {@code frameSize}, {@code filterLength}, or {@code sampleRate} is less than {@code 1}
     * @throws WhatsAppCallException.SpeexDsp if {@code speex_echo_state_init} fails or returns {@code NULL}
     * @throws UnsatisfiedLinkError if libspeexdsp cannot be loaded on the running platform
     */
    public EchoCanceller(int frameSize, int filterLength, int sampleRate) {
        if (frameSize < 1) throw new IllegalArgumentException("frameSize must be >= 1");
        if (filterLength < 1) throw new IllegalArgumentException("filterLength must be >= 1");
        if (sampleRate < 1) throw new IllegalArgumentException("sampleRate must be >= 1");
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
     * Cancels the far-end echo present in the near-end microphone signal for one frame.
     *
     * <p>Both inputs are copied into native scratch, {@code speex_echo_cancellation} adapts its
     * filter and subtracts the estimated echo, and the cleaned result is copied into a freshly
     * allocated array. The {@code far} frame must be the audio that was played out over the same
     * interval as the {@code mic} capture for the cancellation to align.
     *
     * @param mic the microphone (near-end) capture of exactly {@link #frameSize()} mono int16 samples
     * @param far the far-end (speaker) reference played this same frame, of exactly {@link #frameSize()} samples
     * @return a freshly allocated {@code short[]} of {@link #frameSize()} samples holding the echo-cancelled signal
     * @throws NullPointerException if {@code mic} or {@code far} is {@code null}
     * @throws IllegalArgumentException if either array does not hold exactly {@link #frameSize()} samples
     * @throws IllegalStateException if this canceller has been closed
     * @throws WhatsAppCallException.SpeexDsp if {@code speex_echo_cancellation} fails
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
     * Resets the adaptive-filter state so cancellation re-converges from scratch.
     *
     * <p>This is intended for an acoustic-path change such as a speaker-volume change or a headphone
     * being plugged in, where the previously learned filter no longer matches the path.
     *
     * @throws IllegalStateException if this canceller has been closed
     * @throws WhatsAppCallException.SpeexDsp if {@code speex_echo_state_reset} fails
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
     * Returns the underlying {@code SpeexEchoState} pointer for the paired preprocessor.
     *
     * <p>The returned pointer is consumed by {@link AudioPreprocessor#linkEchoState(EchoCanceller)},
     * which hands it to the residual-echo suppressor so the suppressor cooperates with this
     * canceller's adaptive estimate. The pointer is valid only while this canceller stays open.
     *
     * @return the live {@code SpeexEchoState} pointer
     * @throws IllegalStateException if this canceller has been closed
     */
    MemorySegment state() {
        requireOpenState();
        return state;
    }

    /**
     * Verifies that the underlying C state is still live.
     *
     * @throws IllegalStateException if {@link #close()} has destroyed the underlying state
     */
    private void requireOpenState() {
        if (state == null || state.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("EchoCanceller has been closed");
        }
    }

    /**
     * Returns the per-channel frame size this canceller was constructed with.
     *
     * @return the per-channel samples per frame
     */
    public int frameSize() {
        return frameSize;
    }

    /**
     * Destroys the C-side state and releases the scratch arena.
     *
     * <p>This method is idempotent: invoking it on an already-closed instance returns without effect.
     * After a successful close the instance rejects every further operation through
     * {@link #requireOpenState()}.
     *
     * @throws WhatsAppCallException.SpeexDsp if {@code speex_echo_state_destroy} fails
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
