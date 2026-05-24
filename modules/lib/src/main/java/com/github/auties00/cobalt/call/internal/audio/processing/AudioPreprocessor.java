package com.github.auties00.cobalt.call.internal.audio.processing;

import com.github.auties00.cobalt.call.internal.audio.processing.bindings.SpeexDsp;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.util.NativeLibLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * Audio preprocessor — denoise (NS) + automatic gain control (AGC) +
 * voice activity detection (VAD), backed by speexdsp's
 * {@code speex_preprocess_*} family. Bindings are jextract-generated;
 * this class is the high-level wrapper.
 *
 * <p>Pipeline (typical order in a call):
 *
 * <pre>{@code
 *   var aec = new EchoCanceller(160, 1600, 16000);
 *   var pp  = new AudioPreprocessor(160, 16000);
 *   pp.setDenoise(true);
 *   pp.setAgc(true);
 *   pp.setVad(true);
 *   pp.linkEchoState(aec); // residual-echo suppression cooperates with AEC
 *
 *   for each 10ms frame:
 *     short[] cleaned = aec.cancel(mic, farEnd);
 *     boolean voiceActive = pp.process(cleaned);
 *     if (voiceActive) opus.encode(cleaned, ...);
 * }</pre>
 *
 * <p>The preprocessor mutates its input buffer in place and returns
 * the result via the same {@code short[]} you pass in.
 */
public final class AudioPreprocessor implements AutoCloseable {
    static {
        NativeLibLoader.load("speexdsp", Arena.global());
    }

    /**
     * Per-instance arena for scratch buffers.
     */
    private final Arena arena;

    /**
     * Pointer to the C-side {@code SpeexPreprocessState}; nulled out
     * by {@link #close()}.
     */
    private MemorySegment state;

    /**
     * Per-channel sample count of one frame.
     */
    private final int frameSize;

    /**
     * Reusable native scratch for the int16 frame.
     */
    private final MemorySegment frameBuf;

    /**
     * Single-int scratch for {@code speex_preprocess_ctl} integer
     * arguments.
     */
    private final MemorySegment intScratch;

    /**
     * Constructs a new preprocessor with all features off — callers
     * explicitly opt in via the setters.
     *
     * @param frameSize  the per-channel frame size in samples (must
     *                   match the matching {@link EchoCanceller})
     * @param sampleRate the input sample rate in Hz
     * @throws IllegalArgumentException if {@code frameSize} or
     *                                  {@code sampleRate} is &lt; 1
     * @throws UnsatisfiedLinkError if libspeexdsp is not available
     */
    public AudioPreprocessor(int frameSize, int sampleRate) {
        if (frameSize < 1) throw new IllegalArgumentException("frameSize must be ≥ 1");
        if (sampleRate < 1) throw new IllegalArgumentException("sampleRate must be ≥ 1");
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
     * Processes one frame in place. Returns the VAD probability flag
     * (1 = voice detected, 0 = silence) when VAD is enabled,
     * otherwise always 1.
     *
     * @param pcm the input PCM frame — exactly {@link #frameSize()}
     *            mono int16 samples; mutated in place with the
     *            cleaned output
     * @return {@code true} iff voice activity is detected this frame
     * @throws IllegalArgumentException if {@code pcm} is the wrong
     *                                  length
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
     * @param enabled whether NS is on
     */
    public void setDenoise(boolean enabled) {
        ctlInt(SpeexDsp.SPEEX_PREPROCESS_SET_DENOISE(), enabled ? 1 : 0);
    }

    /**
     * Enables or disables AGC.
     *
     * @param enabled whether AGC is on
     */
    public void setAgc(boolean enabled) {
        ctlInt(SpeexDsp.SPEEX_PREPROCESS_SET_AGC(), enabled ? 1 : 0);
    }

    /**
     * Sets the AGC target level in 16-bit PCM amplitude units.
     *
     * @param target the target level
     */
    public void setAgcTarget(int target) {
        ctlInt(SpeexDsp.SPEEX_PREPROCESS_SET_AGC_TARGET(), target);
    }

    /**
     * Enables or disables VAD.
     *
     * @param enabled whether VAD is on
     */
    public void setVad(boolean enabled) {
        ctlInt(SpeexDsp.SPEEX_PREPROCESS_SET_VAD(), enabled ? 1 : 0);
    }

    /**
     * Enables or disables dereverberation.
     *
     * @param enabled whether dereverb is on
     */
    public void setDereverb(boolean enabled) {
        ctlInt(SpeexDsp.SPEEX_PREPROCESS_SET_DEREVERB(), enabled ? 1 : 0);
    }

    /**
     * Sets the maximum noise attenuation in dB (negative integer).
     *
     * @param dB the attenuation in dB
     */
    public void setNoiseSuppressDb(int dB) {
        ctlInt(SpeexDsp.SPEEX_PREPROCESS_SET_NOISE_SUPPRESS(), dB);
    }

    /**
     * Links the preprocessor to an echo canceller so the residual
     * echo suppressor cooperates with the AEC's own estimate.
     *
     * @param aec the echo canceller to link
     * @throws NullPointerException if {@code aec} is {@code null}
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
     * Throws if the underlying C state has been destroyed via
     * {@link #close}.
     */
    private void requireOpenState() {
        if (state == null || state.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("AudioPreprocessor has been closed");
        }
    }

    /**
     * Sends an integer-valued control to {@code speex_preprocess_ctl}.
     *
     * @param request the SPEEX_PREPROCESS_SET_* code
     * @param value   the integer payload
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
     * Returns the configured frame size.
     *
     * @return the per-channel samples per frame
     */
    public int frameSize() {
        return frameSize;
    }

    /**
     * Destroys the C-side state. Idempotent.
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
