package com.github.auties00.cobalt.call.audio;

import com.github.auties00.cobalt.call.audio.opus.OpusApplication;
import com.github.auties00.cobalt.call.audio.processing.AudioPreprocessor;
import com.github.auties00.cobalt.call.audio.processing.EchoCanceller;
import com.github.auties00.cobalt.call.audio.opus.OpusPacket;

import java.util.Objects;

/**
 * Carries the immutable configuration for an {@link AudioPipeline}.
 *
 * <p>The {@link #defaults()} profile matches WhatsApp's wasm voice engine: 16 kHz mono, 10 ms
 * (160-sample) frames, an AEC tail of 100 ms, AEC and denoise and AGC and VAD and DTX all enabled, and
 * the Opus VoIP application at a 24 kbps target. Helper queries report whether AEC and the preprocessor
 * should be constructed, and the {@code with*}/{@code without*} factories derive variant profiles from
 * an existing one.
 *
 * @param sampleRate              the audio sample rate in Hz; one of the rates Opus accepts (8000,
 *                                12000, 16000, 24000, 48000)
 * @param channels                {@code 1} for mono, {@code 2} for stereo (WhatsApp voice uses mono)
 * @param frameSize               the per-channel sample count of one frame; must be a legal Opus frame
 *                                size for {@code sampleRate} (for example 160 for 10 ms at 16 kHz)
 * @param aecFilterLength         the echo-canceller tail length in samples, usually
 *                                {@code aecTailMs * sampleRate / 1000}; {@code 0} disables AEC entirely
 * @param denoise                 whether to enable speexdsp denoise on captured frames
 * @param agc                     whether to enable speexdsp automatic gain control on captured frames
 * @param agcTarget               the AGC target level (linear int16 amplitude); consulted only when
 *                                {@code agc} is {@code true}
 * @param vad                     whether to enable speexdsp voice-activity detection on captured frames;
 *                                the result populates {@link OpusPacket#voiceActive()}
 * @param noiseSuppressDb         the noise-suppression target in dB (negative; for example {@code -20}
 *                                reduces noise by roughly 20 dB); consulted only when {@code denoise} is
 *                                {@code true}
 * @param targetBitrateBps        the Opus encoder target bitrate in bits per second (for example 24000
 *                                for voice)
 * @param complexity              the Opus encoder complexity in {@code 0..10}; higher trades CPU cost
 *                                for quality
 * @param useDtx                  whether to enable Opus discontinuous transmission (small comfort-noise
 *                                packets during silence)
 * @param useInbandFec            whether to enable Opus in-band forward error correction
 * @param expectedPacketLossPct   the expected packet-loss percentage {@code 0..100}; tunes FEC
 *                                redundancy when {@code useInbandFec} is {@code true}
 * @param application             the Opus application mode; should be {@link OpusApplication#VOIP} for
 *                                calls
 */
public record AudioPipelineOptions(
        int sampleRate,
        int channels,
        int frameSize,
        int aecFilterLength,
        boolean denoise,
        boolean agc,
        int agcTarget,
        boolean vad,
        int noiseSuppressDb,
        int targetBitrateBps,
        int complexity,
        boolean useDtx,
        boolean useInbandFec,
        int expectedPacketLossPct,
        OpusApplication application
) {
    /**
     * Defines the default sample rate for WhatsApp voice, 16 kHz wideband.
     */
    public static final int DEFAULT_SAMPLE_RATE = 16_000;

    /**
     * Defines the default frame duration in milliseconds.
     */
    public static final int DEFAULT_FRAME_DURATION_MS = 10;

    /**
     * Defines the default AEC tail length in milliseconds.
     */
    public static final int DEFAULT_AEC_TAIL_MS = 100;

    /**
     * Defines the default Opus target bitrate for voice in bits per second.
     */
    public static final int DEFAULT_BITRATE_BPS = 24_000;

    /**
     * Validates the option ranges and the AEC disable sentinel.
     *
     * <p>The sample rate, channel count, and frame size must be positive, the channel count must be
     * {@code 1} or {@code 2}, the AEC filter length must be non-negative (with {@code 0} meaning AEC
     * disabled), the target bitrate must lie within the Opus range, the complexity must lie in
     * {@code 0..10}, the expected packet-loss percentage must lie in {@code 0..100}, and the
     * application must be non-{@code null}.
     *
     * @throws IllegalArgumentException if any component is outside its accepted range
     */
    public AudioPipelineOptions {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be > 0");
        }
        if (channels < 1 || channels > 2) {
            throw new IllegalArgumentException("channels must be 1 or 2");
        }
        if (frameSize <= 0) {
            throw new IllegalArgumentException("frameSize must be > 0");
        }
        if (aecFilterLength < 0) {
            throw new IllegalArgumentException("aecFilterLength must be >= 0");
        }
        if (targetBitrateBps < 500 || targetBitrateBps > 512_000) {
            throw new IllegalArgumentException("targetBitrateBps out of Opus range [500, 512000]");
        }
        if (complexity < 0 || complexity > 10) {
            throw new IllegalArgumentException("complexity must be in [0, 10]");
        }
        if (expectedPacketLossPct < 0 || expectedPacketLossPct > 100) {
            throw new IllegalArgumentException("expectedPacketLossPct must be in [0, 100]");
        }
        if (application == null) {
            throw new IllegalArgumentException("application cannot be null");
        }
    }

    /**
     * Returns the WhatsApp-voice default profile.
     *
     * <p>The frame size and AEC filter length are derived from {@link #DEFAULT_SAMPLE_RATE},
     * {@link #DEFAULT_FRAME_DURATION_MS}, and {@link #DEFAULT_AEC_TAIL_MS}, and the remaining settings
     * enable mono voice with denoise, AGC, VAD, and DTX on, FEC off, the
     * {@link #DEFAULT_BITRATE_BPS} target, and the {@link OpusApplication#VOIP} application.
     *
     * @return the default options
     * @implNote This implementation hard-codes the AGC target to {@code 8000} and the noise-suppression
     * target to {@code -20} dB, the levels captured from WhatsApp's wasm {@code AudioDriverConfig}; the
     * complexity is fixed at {@code 5} as the wasm engine's voice setting.
     *
     * <p>The codec choice itself matches WA Web's live offers, which advertise
     * {@code <audio enc="opus" rate="8000"/>} and {@code <audio enc="opus" rate="16000"/>};
     * the callee's preaccept selects {@code rate="16000"} for every observed web-linked call.
     * MLow does not appear on the web-linked path on the captured revisions.
     */
    public static AudioPipelineOptions defaults() {
        return defaults(DEFAULT_FRAME_DURATION_MS);
    }

    /**
     * Returns the WhatsApp-voice default profile with an explicit Opus frame duration.
     *
     * <p>Identical to {@link #defaults()} except the per-frame sample count is derived from
     * {@code frameDurationMs} rather than {@link #DEFAULT_FRAME_DURATION_MS}. A 1:1 relay call
     * negotiates a {@code frame_ms} (live capture shows {@code 60}) that the peer's Opus depacketiser
     * enforces, so the call layer selects the matching duration here; a frame whose duration does not
     * match the negotiated value is rejected by the peer with a codec parse error and never reaches its
     * jitter buffer.
     *
     * @param frameDurationMs the Opus frame duration in milliseconds; must yield a legal Opus frame size
     *                        for {@link #DEFAULT_SAMPLE_RATE} (one of {@code 10}, {@code 20}, {@code 40},
     *                        {@code 60})
     * @return the default options with the given frame duration
     */
    public static AudioPipelineOptions defaults(int frameDurationMs) {
        var frameSize = DEFAULT_SAMPLE_RATE * frameDurationMs / 1000;
        var aecFilterLength = DEFAULT_SAMPLE_RATE * DEFAULT_AEC_TAIL_MS / 1000;
        return new AudioPipelineOptions(
                DEFAULT_SAMPLE_RATE,
                1,
                frameSize,
                aecFilterLength,
                true,
                true,
                8000,
                true,
                -20,
                DEFAULT_BITRATE_BPS,
                5,
                true,
                false,
                0,
                OpusApplication.VOIP
        );
    }

    /**
     * Returns whether the echo canceller is enabled.
     *
     * <p>When this query is {@code true} the pipeline constructs an {@link EchoCanceller} and routes
     * every captured frame through it.
     *
     * @return {@code true} if the AEC filter length is positive
     */
    public boolean aecEnabled() {
        return aecFilterLength > 0;
    }

    /**
     * Returns whether any speexdsp preprocessor feature is enabled.
     *
     * <p>When denoise, AGC, or VAD is on, this query is {@code true} and the pipeline constructs an
     * {@link AudioPreprocessor} that runs over every captured frame.
     *
     * @return {@code true} if any preprocessor feature is on
     */
    public boolean preprocessorEnabled() {
        return denoise || agc || vad;
    }

    /**
     * Returns a copy of these options with the echo canceller disabled.
     *
     * <p>The copy sets {@code aecFilterLength} to {@code 0} and leaves every other component unchanged.
     *
     * @return the AEC-disabled copy
     */
    public AudioPipelineOptions withoutAec() {
        return new AudioPipelineOptions(sampleRate, channels, frameSize, 0,
                denoise, agc, agcTarget, vad, noiseSuppressDb,
                targetBitrateBps, complexity, useDtx, useInbandFec,
                expectedPacketLossPct, application);
    }

    /**
     * Returns a copy of these options with all preprocessor features disabled.
     *
     * <p>The copy clears {@code denoise}, {@code agc}, and {@code vad} and leaves every other component
     * unchanged.
     *
     * @return the preprocessor-disabled copy
     */
    public AudioPipelineOptions withoutPreprocessor() {
        return new AudioPipelineOptions(sampleRate, channels, frameSize, aecFilterLength,
                false, false, agcTarget, false, noiseSuppressDb,
                targetBitrateBps, complexity, useDtx, useInbandFec,
                expectedPacketLossPct, application);
    }

    /**
     * Returns a copy of these options with Opus in-band forward error correction enabled at the given
     * expected packet-loss percentage.
     *
     * <p>The copy sets {@code useInbandFec} and replaces {@code expectedPacketLossPct}, leaving every other
     * component unchanged. In-band FEC piggybacks a low-bitrate copy of the previous frame onto each
     * packet, so the peer reconstructs a single lost packet without a gap; WhatsApp runs FEC on every call,
     * and on a real network its absence turns mild packet loss into audible stutter.
     *
     * @param expectedPacketLossPct the loss percentage that tunes FEC redundancy, in {@code [0, 100]}
     * @return the FEC-enabled copy
     * @throws IllegalArgumentException if {@code expectedPacketLossPct} is outside {@code [0, 100]}
     */
    public AudioPipelineOptions withInbandFec(int expectedPacketLossPct) {
        if (expectedPacketLossPct < 0 || expectedPacketLossPct > 100) {
            throw new IllegalArgumentException(
                    "expectedPacketLossPct out of range [0, 100]: " + expectedPacketLossPct);
        }
        return new AudioPipelineOptions(sampleRate, channels, frameSize, aecFilterLength,
                denoise, agc, agcTarget, vad, noiseSuppressDb,
                targetBitrateBps, complexity, useDtx, true,
                expectedPacketLossPct, application);
    }

    /**
     * Returns a copy of these options with the given Opus application mode.
     *
     * <p>The copy replaces {@code application} and leaves every other component unchanged.
     * {@link OpusApplication#VOIP} pins the encoder to the SILK speech engine, which reproduces voice
     * efficiently but degrades music; {@link OpusApplication#AUDIO} lets the encoder pick between SILK and
     * the CELT general-audio engine per frame, so a single stream carries clean voice and clean music, the
     * way WhatsApp's call audio does.
     *
     * @param application the Opus application mode
     * @return the modified copy
     * @throws NullPointerException if {@code application} is {@code null}
     */
    public AudioPipelineOptions withApplication(OpusApplication application) {
        Objects.requireNonNull(application, "application cannot be null");
        return new AudioPipelineOptions(sampleRate, channels, frameSize, aecFilterLength,
                denoise, agc, agcTarget, vad, noiseSuppressDb,
                targetBitrateBps, complexity, useDtx, useInbandFec,
                expectedPacketLossPct, application);
    }

    /**
     * Returns a copy of these options with Opus discontinuous transmission disabled.
     *
     * <p>The copy clears {@code useDtx} and leaves every other component unchanged. DTX suits a live
     * microphone, where suppressing transmission during silence saves bandwidth; a file or tone source is
     * continuous, and the tiny silence-indicator frames DTX emits are turned into comfort noise by the
     * peer's jitter buffer rather than played as audio, breaking the stream up.
     *
     * @return the DTX-disabled copy
     */
    public AudioPipelineOptions withoutDtx() {
        return new AudioPipelineOptions(sampleRate, channels, frameSize, aecFilterLength,
                denoise, agc, agcTarget, vad, noiseSuppressDb,
                targetBitrateBps, complexity, false, useInbandFec,
                expectedPacketLossPct, application);
    }

    /**
     * Returns a copy of these options with the given target bitrate.
     *
     * <p>The copy replaces {@code targetBitrateBps} and leaves every other component unchanged.
     *
     * @param bps the new target bitrate in bits per second
     * @return the modified copy
     */
    public AudioPipelineOptions withBitrate(int bps) {
        return new AudioPipelineOptions(sampleRate, channels, frameSize, aecFilterLength,
                denoise, agc, agcTarget, vad, noiseSuppressDb,
                bps, complexity, useDtx, useInbandFec,
                expectedPacketLossPct, application);
    }
}
