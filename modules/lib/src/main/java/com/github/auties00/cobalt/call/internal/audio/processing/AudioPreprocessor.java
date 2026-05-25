package com.github.auties00.cobalt.call.internal.audio.processing;

import com.github.auties00.cobalt.call.internal.audio.processing.bindings.SpeexDsp;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.util.NativeLibLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * Wraps the speexdsp {@code speex_preprocess_*} family to denoise a capture stream, apply automatic
 * gain control, and detect voice activity.
 *
 * <p>An instance holds a single C-side {@code SpeexPreprocessState} sized to one fixed frame and one
 * sample rate, both supplied at construction. Every feature (noise suppression, automatic gain
 * control, voice activity detection, dereverberation) starts disabled; callers opt in through the
 * setters before the first {@link #process(short[])} call. Each {@code process} call submits exactly
 * one frame of {@link #frameSize()} mono int16 samples, rewrites that same array with the cleaned
 * output, and reports whether the frame carried speech. The instance is single-threaded and owns
 * native memory; {@link #close()} destroys the underlying state and must run when the stream ends.
 *
 * <p>A preprocessor pairs with an {@link EchoCanceller} over the same frame size and sample rate.
 * Linking the two through {@link #linkEchoState(EchoCanceller)} lets the residual-echo suppressor
 * consult the canceller's adaptive estimate, so the canceller runs first per frame and the
 * preprocessor second:
 *
 * {@snippet :
 * var aec = new EchoCanceller(160, 1600, 16000);
 * var pp  = new AudioPreprocessor(160, 16000);
 * pp.setDenoise(true);
 * pp.setAgc(true);
 * pp.setVad(true);
 * pp.linkEchoState(aec);
 * // per 10 ms frame:
 * short[] cleaned = aec.cancel(mic, farEnd);
 * boolean voiceActive = pp.process(cleaned);
 * }
 */
public final class AudioPreprocessor implements AutoCloseable {
    static {
        NativeLibLoader.load("speexdsp", Arena.global());
    }

    /**
     * Owns the native scratch buffers allocated for this instance.
     *
     * <p>This arena is created shared at construction and closed by {@link #close()}, releasing
     * {@link #frameBuf} and {@link #intScratch}. The C-side {@code SpeexPreprocessState} is allocated
     * by speexdsp itself and is not part of this arena's lifetime; it is freed separately.
     */
    private final Arena arena;

    /**
     * Points to the C-side {@code SpeexPreprocessState} backing this preprocessor.
     *
     * <p>This field is assigned the value returned by {@code speex_preprocess_state_init} during
     * construction and reset to {@link MemorySegment#NULL} by {@link #close()}. A {@code null} or
     * {@code NULL} value marks a closed instance, which {@link #requireOpenState()} rejects.
     */
    private MemorySegment state;

    /**
     * Holds the per-channel sample count of one frame.
     *
     * <p>Every {@link #process(short[])} call requires an array of exactly this length.
     */
    private final int frameSize;

    /**
     * Provides reusable native storage for the int16 frame handed to {@code speex_preprocess_run}.
     *
     * <p>This segment spans {@code frameSize * 2} bytes (one int16 per sample) and is reused across
     * frames so that no per-call native allocation occurs on the audio path.
     */
    private final MemorySegment frameBuf;

    /**
     * Provides a four-byte native cell for the single integer argument passed to
     * {@code speex_preprocess_ctl}.
     */
    private final MemorySegment intScratch;

    /**
     * Constructs a preprocessor with every feature disabled, sized to the given frame and sample
     * rate.
     *
     * <p>The frame size and sample rate must match the {@link EchoCanceller} the stream pairs this
     * preprocessor with. On failure the shared arena is closed before the exception propagates, so a
     * thrown constructor leaks no native memory.
     *
     * @param frameSize  the per-channel frame size in samples; must match the paired {@link EchoCanceller}
     * @param sampleRate the input sample rate in Hz
     * @throws IllegalArgumentException if {@code frameSize} or {@code sampleRate} is less than {@code 1}
     * @throws WhatsAppCallException.SpeexDsp if {@code speex_preprocess_state_init} fails or returns {@code NULL}
     * @throws UnsatisfiedLinkError if libspeexdsp cannot be loaded on the running platform
     */
    public AudioPreprocessor(int frameSize, int sampleRate) {
        if (frameSize < 1) throw new IllegalArgumentException("frameSize must be >= 1");
        if (sampleRate < 1) throw new IllegalArgumentException("sampleRate must be >= 1");
        this.arena = Arena.ofShared();
        this.frameSize = frameSize;
        try {
            this.state = SpeexDsp.speex_preprocess_state_init(frameSize, sampleRate);
        } catch (Throwable t) {
            arena.close();
            throw new WhatsAppCallException.SpeexDsp("speex_preprocess_state_init failed", t);
        }
        if (state.equals(MemorySegment.NULL)) {
            arena.close();
            throw new WhatsAppCallException.SpeexDsp("speex_preprocess_state_init returned NULL");
        }
        this.frameBuf = arena.allocate((long) frameSize * 2);
        this.intScratch = arena.allocate(4);
    }

    /**
     * Processes one frame in place and reports whether it carried speech.
     *
     * <p>The supplied array is copied into native scratch, run through {@code speex_preprocess_run}
     * with whatever features are currently enabled, and copied back so the same {@code short[]} holds
     * the cleaned output on return. When voice activity detection is enabled the return value
     * reflects the per-frame voice flag; when it is disabled {@code speex_preprocess_run} reports a
     * nonzero result for every frame, so this method returns {@code true} for every frame.
     *
     * @param pcm the input PCM frame of exactly {@link #frameSize()} mono int16 samples, mutated in place with the cleaned output
     * @return {@code true} when voice activity is detected this frame, {@code false} otherwise
     * @throws NullPointerException if {@code pcm} is {@code null}
     * @throws IllegalArgumentException if {@code pcm} does not hold exactly {@link #frameSize()} samples
     * @throws IllegalStateException if this preprocessor has been closed
     * @throws WhatsAppCallException.SpeexDsp if {@code speex_preprocess_run} fails
     */
    public boolean process(short[] pcm) {
        Objects.requireNonNull(pcm, "pcm cannot be null");
        if (pcm.length != frameSize) {
            throw new IllegalArgumentException("pcm must be " + frameSize + " samples, got " + pcm.length);
        }
        requireOpenState();
        frameBuf.copyFrom(MemorySegment.ofArray(pcm));
        int rc;
        try {
            rc = SpeexDsp.speex_preprocess_run(state, frameBuf);
        } catch (Throwable t) {
            throw new WhatsAppCallException.SpeexDsp("speex_preprocess_run failed", t);
        }
        MemorySegment.copy(frameBuf, ValueLayout.JAVA_SHORT, 0, pcm, 0, frameSize);
        return rc != 0;
    }

    /**
     * Enables or disables noise suppression.
     *
     * @param enabled {@code true} to turn noise suppression on, {@code false} to turn it off
     * @throws IllegalStateException if this preprocessor has been closed
     * @throws WhatsAppCallException.SpeexDsp if the control request fails
     */
    public void setDenoise(boolean enabled) {
        ctlInt(SpeexDsp.SPEEX_PREPROCESS_SET_DENOISE(), enabled ? 1 : 0);
    }

    /**
     * Enables or disables automatic gain control.
     *
     * @param enabled {@code true} to turn automatic gain control on, {@code false} to turn it off
     * @throws IllegalStateException if this preprocessor has been closed
     * @throws WhatsAppCallException.SpeexDsp if the control request fails
     */
    public void setAgc(boolean enabled) {
        ctlInt(SpeexDsp.SPEEX_PREPROCESS_SET_AGC(), enabled ? 1 : 0);
    }

    /**
     * Sets the automatic gain control target level in 16-bit PCM amplitude units.
     *
     * @param target the target amplitude level
     * @throws IllegalStateException if this preprocessor has been closed
     * @throws WhatsAppCallException.SpeexDsp if the control request fails
     */
    public void setAgcTarget(int target) {
        ctlInt(SpeexDsp.SPEEX_PREPROCESS_SET_AGC_TARGET(), target);
    }

    /**
     * Enables or disables voice activity detection.
     *
     * <p>When enabled, the per-frame voice flag becomes the return value of
     * {@link #process(short[])}.
     *
     * @param enabled {@code true} to turn voice activity detection on, {@code false} to turn it off
     * @throws IllegalStateException if this preprocessor has been closed
     * @throws WhatsAppCallException.SpeexDsp if the control request fails
     */
    public void setVad(boolean enabled) {
        ctlInt(SpeexDsp.SPEEX_PREPROCESS_SET_VAD(), enabled ? 1 : 0);
    }

    /**
     * Enables or disables dereverberation.
     *
     * @param enabled {@code true} to turn dereverberation on, {@code false} to turn it off
     * @throws IllegalStateException if this preprocessor has been closed
     * @throws WhatsAppCallException.SpeexDsp if the control request fails
     */
    public void setDereverb(boolean enabled) {
        ctlInt(SpeexDsp.SPEEX_PREPROCESS_SET_DEREVERB(), enabled ? 1 : 0);
    }

    /**
     * Sets the maximum noise attenuation applied by the noise suppressor, in decibels.
     *
     * <p>The value is a negative integer; a larger magnitude attenuates residual noise more
     * aggressively.
     *
     * @param dB the maximum attenuation in decibels, as a negative integer
     * @throws IllegalStateException if this preprocessor has been closed
     * @throws WhatsAppCallException.SpeexDsp if the control request fails
     */
    public void setNoiseSuppressDb(int dB) {
        ctlInt(SpeexDsp.SPEEX_PREPROCESS_SET_NOISE_SUPPRESS(), dB);
    }

    /**
     * Links this preprocessor to an echo canceller so the residual-echo suppressor cooperates with
     * the canceller's adaptive estimate.
     *
     * <p>The linked {@link EchoCanceller} must share this preprocessor's frame size and sample rate
     * and must remain open for as long as the link is active. Once linked, the canceller is expected
     * to run before this preprocessor on each frame so its estimate is current.
     *
     * @param aec the echo canceller to link
     * @throws NullPointerException if {@code aec} is {@code null}
     * @throws IllegalStateException if this preprocessor has been closed
     * @throws WhatsAppCallException.SpeexDsp if the control request fails or returns a negative status
     */
    public void linkEchoState(EchoCanceller aec) {
        Objects.requireNonNull(aec, "aec cannot be null");
        requireOpenState();
        int rc;
        try {
            rc = SpeexDsp.speex_preprocess_ctl(state, SpeexDsp.SPEEX_PREPROCESS_SET_ECHO_STATE(), aec.state());
        } catch (Throwable t) {
            throw new WhatsAppCallException.SpeexDsp("speex_preprocess_ctl SET_ECHO_STATE failed", t);
        }
        if (rc < 0) throw new WhatsAppCallException.SpeexDsp("speex_preprocess_ctl SET_ECHO_STATE returned " + rc);
    }

    /**
     * Verifies that the underlying C state is still live.
     *
     * @throws IllegalStateException if {@link #close()} has destroyed the underlying state
     */
    private void requireOpenState() {
        if (state == null || state.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("AudioPreprocessor has been closed");
        }
    }

    /**
     * Sends one integer-valued control request to {@code speex_preprocess_ctl}.
     *
     * <p>The value is written into {@link #intScratch} and passed by pointer, matching the speexdsp
     * convention where every {@code SPEEX_PREPROCESS_SET_*} integer request reads its argument
     * through a pointer rather than by value. A negative return status is treated as a failure.
     *
     * @param request the {@code SPEEX_PREPROCESS_SET_*} request code
     * @param value   the integer payload for the request
     * @throws IllegalStateException if this preprocessor has been closed
     * @throws WhatsAppCallException.SpeexDsp if the control request fails or returns a negative status
     */
    private void ctlInt(int request, int value) {
        requireOpenState();
        intScratch.set(ValueLayout.JAVA_INT, 0, value);
        int rc;
        try {
            rc = SpeexDsp.speex_preprocess_ctl(state, request, intScratch);
        } catch (Throwable t) {
            throw new WhatsAppCallException.SpeexDsp("speex_preprocess_ctl request=" + request + " failed", t);
        }
        if (rc < 0) throw new WhatsAppCallException.SpeexDsp("speex_preprocess_ctl request=" + request + " returned " + rc);
    }

    /**
     * Returns the per-channel frame size this preprocessor was constructed with.
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
     * @throws WhatsAppCallException.SpeexDsp if {@code speex_preprocess_state_destroy} fails
     */
    @Override
    public void close() {
        if (state == null || state.equals(MemorySegment.NULL)) {
            return;
        }
        try {
            SpeexDsp.speex_preprocess_state_destroy(state);
        } catch (Throwable t) {
            throw new WhatsAppCallException.SpeexDsp("speex_preprocess_state_destroy failed", t);
        } finally {
            state = MemorySegment.NULL;
            arena.close();
        }
    }
}
