package com.github.auties00.cobalt.call.internal.audio;

import com.github.auties00.cobalt.call.internal.audio.opus.OpusApplication;
import com.github.auties00.cobalt.call.internal.audio.processing.AudioPreprocessor;
import com.github.auties00.cobalt.call.internal.audio.processing.EchoCanceller;
import com.github.auties00.cobalt.call.internal.audio.opus.OpusPacket;

/**
 * Configuration for an {@link AudioPipeline}. Defaults match
 * WhatsApp's wasm engine voice profile: 16 kHz mono, 10 ms (160-sample)
 * frames, AEC tail of 100 ms, AEC + denoise + AGC + VAD + DTX all on,
 * Opus VOIP application at 24 kbps target.
 *
 * @param sampleRate              the audio sample rate in Hz; one of
 *                                the rates Opus accepts (8000, 12000,
 *                                16000, 24000, 48000)
 * @param channels                1 for mono, 2 for stereo (WhatsApp
 *                                voice uses mono)
 * @param frameSize               per-channel sample count of one
 *                                frame; must be a legal Opus frame
 *                                size for {@code sampleRate} (e.g.
 *                                160 for 10 ms at 16 kHz)
 * @param aecFilterLength         echo-canceller tail length in
 *                                samples — usually
 *                                {@code aecTailMs * sampleRate / 1000};
 *                                set to {@code 0} to disable AEC
 *                                entirely
 * @param denoise                 whether to enable speexdsp denoise
 *                                (NS) on captured frames
 * @param agc                     whether to enable speexdsp automatic
 *                                gain control on captured frames
 * @param agcTarget               the AGC target level (linear int16
 *                                amplitude); only consulted when
 *                                {@code agc} is {@code true}
 * @param vad                     whether to enable speexdsp voice
 *                                activity detection on captured
 *                                frames; the result populates
 *                                {@link OpusPacket#voiceActive()}
 * @param noiseSuppressDb         the noise-suppression target in dB
 *                                (negative; e.g. -20 reduces noise
 *                                by ~20 dB); only consulted when
 *                                {@code denoise} is {@code true}
 * @param targetBitrateBps        the Opus encoder target bitrate
 *                                in bits per second (e.g. 24000 for
 *                                voice)
 * @param complexity              Opus encoder complexity in
 *                                {@code 0..10}; higher = better
 *                                quality at higher CPU cost
 * @param useDtx                  whether to enable Opus discontinuous
 *                                transmission (small comfort-noise
 *                                packets during silence)
 * @param useInbandFec            whether to enable Opus in-band
 *                                forward error correction
 * @param expectedPacketLossPct   expected packet-loss percentage
 *                                {@code 0..100}; tunes FEC redundancy
 *                                when {@code useInbandFec} is on
 * @param application             Opus application mode; should be
 *                                {@link OpusApplication#VOIP} for
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
     * Default sample rate for WhatsApp voice — 16 kHz wideband.
     */
    public static final int DEFAULT_SAMPLE_RATE = 16_000;

    /**
     * Default frame duration in milliseconds.
     */
    public static final int DEFAULT_FRAME_DURATION_MS = 10;

    /**
     * Default AEC tail length in milliseconds.
     */
    public static final int DEFAULT_AEC_TAIL_MS = 100;

    /**
     * Default Opus target bitrate for voice.
     */
    public static final int DEFAULT_BITRATE_BPS = 24_000;

    /**
     * Compact constructor — validates ranges and the
     * {@code aecFilterLength} disable sentinel.
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
            throw new IllegalArgumentException("aecFilterLength must be ≥ 0");
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
     * @return the default options
     */
    public static AudioPipelineOptions defaults() {
        var frameSize = DEFAULT_SAMPLE_RATE * DEFAULT_FRAME_DURATION_MS / 1000;
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
     * Returns whether the AEC is enabled — i.e. whether the pipeline
     * should construct an {@link EchoCanceller} and route every
     * captured frame through it.
     *
     * @return {@code true} if AEC is on
     */
    public boolean aecEnabled() {
        return aecFilterLength > 0;
    }

    /**
     * Returns whether any speexdsp preprocessor feature (denoise,
     * AGC, or VAD) is enabled, in which case the pipeline must
     * construct an {@link AudioPreprocessor} for every captured
     * frame.
     *
     * @return {@code true} if any preprocessor feature is on
     */
    public boolean preprocessorEnabled() {
        return denoise || agc || vad;
    }

    /**
     * Returns a copy of these options with {@code aecFilterLength=0},
     * disabling the echo canceller.
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
     * Returns a copy with all preprocessor features disabled.
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
     * Returns a copy with the given target bitrate.
     *
     * @param bps the new target bitrate in bps
     * @return the modified copy
     */
    public AudioPipelineOptions withBitrate(int bps) {
        return new AudioPipelineOptions(sampleRate, channels, frameSize, aecFilterLength,
                denoise, agc, agcTarget, vad, noiseSuppressDb,
                bps, complexity, useDtx, useInbandFec,
                expectedPacketLossPct, application);
    }
}
