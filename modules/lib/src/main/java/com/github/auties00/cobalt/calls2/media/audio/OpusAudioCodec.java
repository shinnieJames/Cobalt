package com.github.auties00.cobalt.calls2.media.audio;

import com.github.auties00.cobalt.calls2.media.audio.opus.bindings.CobaltOpus;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.util.NativeLibLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * The libopus-backed call audio codec: opens an Opus encoder and decoder, applies the WhatsApp control
 * set, classifies each encoded frame, recovers losses through PLC and in-band FEC, and adapts encoder
 * complexity to the device's CPU budget.
 *
 * <p>An instance owns one native encoder state and one native decoder state plus reusable PCM and packet
 * scratch buffers, all from a per-instance arena, and is single-writer per the {@link AudioCodec}
 * contract. On open it applies the bitrate, variable-bitrate, complexity, in-band FEC, DTX,
 * force-channels, signal, least-significant-bit-depth, and maximum-bandwidth controls from the supplied
 * {@link OpusCodecParams}; on {@linkplain #modify(OpusCodecParams) modify} it re-applies the mutable
 * subset with the bitrate clamped by the maximum-bandwidth-derived cap. Each
 * {@linkplain #encode(short[], int) encode} measures wall time, classifies the result for voice activity
 * and DTX, feeds the timing to the {@link AdaptiveComplexityController}, and applies a new complexity
 * level only when the controller moves it. Decode runs normally or pulls a forward-error-correction copy
 * from the packet; {@linkplain #recover(byte[], int) recover} conceals or reconstructs a lost frame.
 *
 * @implNote This implementation ports {@code opus_codec_open} (fn6258), {@code opus_codec_encode}
 * (fn6266), {@code opus_codec_decode_impl} (fn6271), {@code opus_codec_recover_normal} (fn6272),
 * {@code opus_codec_modify} (fn6263), and {@code opus_get_stats} (fn6274) of the wa-voip WASM module
 * {@code ff-tScznZ8P}. The native vtable indirection ({@code OpusCodecInstance} fields {@code 0x14}
 * encode, {@code 0x2c} decode, {@code 0x10} control) collapses to direct {@link CobaltOpus} shim
 * downcalls; the variadic {@code opus_encoder_ctl} is reached through the shim's typed
 * {@code cobalt_opus_encoder_set_*} setters. The public control set applied is
 * SET_BITRATE/SET_VBR/SET_COMPLEXITY/SET_INBAND_FEC/SET_PACKET_LOSS_PERC/SET_DTX/SET_FORCE_CHANNELS/
 * SET_SIGNAL/SET_LSB_DEPTH/SET_MAX_BANDWIDTH; the WhatsApp-patched extended SILK/CELT CTLs
 * {@code 4050..4094} the native open path also issues are deliberately omitted because stock system
 * libopus rejects them. The maximum-bandwidth control is the shim's
 * {@code cobalt_opus_encoder_set_max_bandwidth} (libopus request {@code 4004}); the speech-vs-DTX
 * threshold is 3 bytes and the VAD flag is read from the TOC of the encoded packet, matching
 * {@code opus_codec_encode}.
 */
public final class OpusAudioCodec implements AudioCodec {
    static {
        NativeLibLoader.load("cobalt-native", Arena.global());
    }

    /**
     * Capacity, in bytes, of the native packet output buffer for one encoded packet.
     *
     * @implNote This implementation uses 1500, above the 1276-byte RFC 6716 single-frame maximum; voice
     * frames are far smaller. The buffer is allocated once so encoding never sizes per call.
     */
    private static final int MAX_PACKET_BYTES = 1500;

    /**
     * Worst-case per-channel sample count of one Opus frame, a 60 ms frame at 48 kHz.
     *
     * <p>The PCM scratch buffers are sized for this maximum so any legal frame fits without
     * reallocation.
     */
    private static final int MAX_FRAME_SAMPLES = 2880;

    /**
     * Minimum encoded length, in bytes, for a frame to be treated as speech rather than DTX/comfort
     * noise.
     *
     * <p>The native classifier treats any output shorter than this as a discontinuous-transmission or
     * comfort-noise frame.
     */
    private static final int SPEECH_THRESHOLD_BYTES = 3;

    /**
     * Full-scale reference amplitude the {@code -dBov} audio level divides the root-mean-square energy
     * by.
     *
     * @implNote This implementation uses {@code 32767.0}, the maximum positive 16-bit PCM sample,
     * exactly as {@code fn4303} of the wa-voip WASM module {@code ff-tScznZ8P}
     * ({@code media/src/audio/wa_audio_level_rtp_ext_utils.cc}) does; the native code divides the
     * integer root-mean-square by {@code 32767.0}, not {@code 32768.0}, before taking the logarithm.
     */
    private static final double AUDIO_LEVEL_FULL_SCALE = 32767.0;

    /**
     * Decibels-per-decade factor mapping the root-mean-square-to-full-scale ratio onto the
     * {@code -dBov} level.
     *
     * @implNote This implementation uses {@code -20.0}, the standard amplitude-ratio decibel factor
     * {@code fn4303} applies as {@code -20 * log10(rms / 32767)}.
     */
    private static final double AUDIO_LEVEL_DB_FACTOR = -20.0;

    /**
     * Per-second encode-time budget, in milliseconds of encode time per second of audio, seeding the
     * {@link AdaptiveComplexityController}.
     *
     * @implNote This implementation uses the compiled-in WhatsApp default of {@code 10} ms/s. The native
     * budget at {@code OpusCodecInstance+0x310} is {@code codec_param[0x116]} clamped to {@code 1..100},
     * which defaults to {@code 10} when the field is unset ({@code codec_param[0x116] == 0}); fn6258
     * (tree/xplat/wa-voip/wacall/media/src/codec/wa_opus.cc lines 742-748, default set at line 126) takes
     * the {@code iVar15 = 10} fallback whenever the setting is out of {@code 1..100}, and that setting is
     * unset throughout the reversed module. The controller compares this against the average encode time
     * per second in the same ms-of-encode-per-second-of-audio unit (the "avg encode time: %d ms/s" log at
     * string offset 0x3bca8).
     */
    private static final long ENCODE_BUDGET_MILLIS_PER_SECOND = 10;

    /**
     * Per-instance arena owning the native encoder and decoder states and the scratch buffers.
     */
    private final Arena arena;

    /**
     * The codec parameters this codec was opened with, updated on {@link #modify(OpusCodecParams)}.
     */
    private OpusCodecParams params;

    /**
     * The channel count, cached from the parameters for the PCM length checks.
     */
    private final int channels;

    /**
     * Pointer to the native {@code OpusEncoder} state, or {@link MemorySegment#NULL} once closed.
     */
    private MemorySegment encoder;

    /**
     * Pointer to the native {@code OpusDecoder} state, or {@link MemorySegment#NULL} once closed.
     */
    private MemorySegment decoder;

    /**
     * Reusable native buffer the caller's PCM is copied into before encoding.
     */
    private final MemorySegment pcmInBuf;

    /**
     * Reusable native buffer the decoded PCM is read back from.
     */
    private final MemorySegment pcmOutBuf;

    /**
     * Reusable native buffer the encoded packet bytes are written into.
     */
    private final MemorySegment packetBuf;

    /**
     * Reusable native buffer the input packet is copied into for decode, recover, and FEC inspection.
     */
    private final MemorySegment packetInBuf;

    /**
     * The adaptive-complexity controller deriving the encoder complexity from rolling encode time.
     */
    private final AdaptiveComplexityController complexityController;

    /**
     * The in-band forward-error-correction policy deciding, per lost frame, whether to reconstruct from
     * the following packet's LBRR copy or fall back to packet-loss concealment.
     */
    private final OpusInbandFecPacker fecPacker;

    /**
     * Cumulative count of frames passed to the encoder.
     */
    private long totalEncodedFrames;

    /**
     * Cumulative count of frames passed to the decoder.
     */
    private long totalDecodedFrames;

    /**
     * Decode-side count of frames reconstructed from in-band FEC.
     */
    private long fecFrames;

    /**
     * Decode-side count of frames filled by packet-loss concealment.
     */
    private long plcFrames;

    /**
     * Sum of native encode wall time, in microseconds, across the codec lifetime.
     */
    private long lifetimeEncodeMicros;

    /**
     * Sum of native decode wall time, in microseconds, across the codec lifetime.
     */
    private long lifetimeDecodeMicros;

    /**
     * Sum of encoded-frame sizes, in bytes, across the codec lifetime, for the observed-bitrate average.
     */
    private long lifetimeEncodedBytes;

    /**
     * The encoder target bitrate, in bits per second, currently applied to the native encoder.
     *
     * <p>Set to the open target on construction and to the bandwidth-capped target on each
     * {@link #modify(OpusCodecParams)}, so it tracks the value last handed to
     * {@code cobalt_opus_encoder_set_bitrate}. Sampled per encode into the running target-bitrate average.
     */
    private int appliedTargetBitrate;

    /**
     * Running sum of the per-frame applied target bitrate, in bits per second, across the codec lifetime.
     *
     * <p>Accumulated only on frames encoded while the applied target is a concrete value (not
     * {@link OpusCodecParams#BITRATE_AUTO}), forming the numerator of the average target bitrate.
     */
    private long lifetimeTargetBitrateSum;

    /**
     * Count of frames contributing to {@link #lifetimeTargetBitrateSum}.
     *
     * <p>Increments per encode whenever the applied target is a concrete value; the denominator of the
     * average target bitrate, kept separate from {@link #totalEncodedFrames} so a frame whose target is
     * {@link OpusCodecParams#BITRATE_AUTO} does not bias the average.
     */
    private long targetBitrateSamples;

    /**
     * Whether the most recent encoded frame was a discontinuous-transmission frame.
     */
    private boolean lastWasDiscontinuous;

    /**
     * Opens an Opus codec configured by the given parameters.
     *
     * <p>Allocates the native encoder and decoder states and the scratch buffers from a fresh shared
     * arena, then applies the full open control set. The complexity controller is seeded with the
     * parameter complexity and the {@link #ENCODE_BUDGET_MILLIS_PER_SECOND} encode-time budget. If
     * libopus rejects the configuration the partially built native state and the arena are released
     * before the exception propagates.
     *
     * @param params the codec parameters to open with
     * @throws NullPointerException       if {@code params} is {@code null}
     * @throws WhatsAppCallException.Opus if libopus rejects the configuration
     * @throws UnsatisfiedLinkError       if libopus cannot be loaded
     */
    public OpusAudioCodec(OpusCodecParams params) {
        this.params = Objects.requireNonNull(params, "params cannot be null");
        this.channels = params.channels();
        this.arena = Arena.ofShared();
        try {
            var outHandle = arena.allocate(ValueLayout.ADDRESS);
            this.encoder = createEncoder(params, outHandle);
            this.decoder = createDecoder(params, outHandle);
            this.pcmInBuf = arena.allocate((long) MAX_FRAME_SAMPLES * channels * 2);
            this.pcmOutBuf = arena.allocate((long) MAX_FRAME_SAMPLES * channels * 2);
            this.packetBuf = arena.allocate(MAX_PACKET_BYTES);
            this.packetInBuf = arena.allocate(MAX_PACKET_BYTES);
            this.complexityController = new AdaptiveComplexityController(
                    ENCODE_BUDGET_MILLIS_PER_SECOND, params.complexity());
            this.fecPacker = new OpusInbandFecPacker();
            applyOpenControls(params);
        } catch (RuntimeException e) {
            destroyStates();
            arena.close();
            throw e;
        }
    }

    /**
     * Creates the native Opus encoder for the given parameters.
     *
     * @param params    the codec parameters carrying the sample rate, channels, and application mode
     * @param outHandle the reusable single-pointer out-handle segment the shim writes the state into
     * @return the encoder state handle
     * @throws WhatsAppCallException.Opus if {@code cobalt_opus_encoder_create} fails or returns null
     */
    private MemorySegment createEncoder(OpusCodecParams params, MemorySegment outHandle) {
        int rc;
        try {
            rc = CobaltOpus.cobalt_opus_encoder_create(params.sampleRate(), channels, params.application().toNative(), outHandle);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Opus("cobalt_opus_encoder_create failed", t);
        }
        var state = outHandle.get(ValueLayout.ADDRESS, 0);
        if (rc != CobaltOpus.COBALT_OPUS_OK() || state.equals(MemorySegment.NULL)) {
            throw WhatsAppCallException.Opus.fromErr("cobalt_opus_encoder_create", rc);
        }
        return state;
    }

    /**
     * Creates the native Opus decoder for the given parameters.
     *
     * @param params    the codec parameters carrying the sample rate and channels
     * @param outHandle the reusable single-pointer out-handle segment the shim writes the state into
     * @return the decoder state handle
     * @throws WhatsAppCallException.Opus if {@code cobalt_opus_decoder_create} fails or returns null
     */
    private MemorySegment createDecoder(OpusCodecParams params, MemorySegment outHandle) {
        int rc;
        try {
            rc = CobaltOpus.cobalt_opus_decoder_create(params.sampleRate(), channels, outHandle);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Opus("cobalt_opus_decoder_create failed", t);
        }
        var state = outHandle.get(ValueLayout.ADDRESS, 0);
        if (rc != CobaltOpus.COBALT_OPUS_OK() || state.equals(MemorySegment.NULL)) {
            throw WhatsAppCallException.Opus.fromErr("cobalt_opus_decoder_create", rc);
        }
        return state;
    }

    /**
     * Applies the full open-time encoder control set from the given parameters.
     *
     * <p>Issues, in the order {@code opus_codec_open} uses, the bitrate, variable-bitrate, complexity,
     * in-band FEC, expected packet loss, DTX, force-channels, signal, least-significant-bit-depth, and
     * maximum-bandwidth controls through the typed {@link CobaltOpus} setters.
     *
     * @param params the parameters whose fields select the control values
     */
    private void applyOpenControls(OpusCodecParams params) {
        var signal = params.signalVoice() ? CobaltOpus.COBALT_OPUS_SIGNAL_VOICE() : CobaltOpus.COBALT_OPUS_SIGNAL_MUSIC();
        encApply("set_bitrate", CobaltOpus.cobalt_opus_encoder_set_bitrate(encoder, params.defaultBitrate()));
        this.appliedTargetBitrate = params.defaultBitrate();
        encApply("set_vbr", CobaltOpus.cobalt_opus_encoder_set_vbr(encoder, params.variableBitrate() ? 1 : 0));
        encApply("set_complexity", CobaltOpus.cobalt_opus_encoder_set_complexity(encoder, params.complexity()));
        encApply("set_inband_fec", CobaltOpus.cobalt_opus_encoder_set_inband_fec(encoder, params.inbandFec() ? 1 : 0));
        encApply("set_packet_loss_perc", CobaltOpus.cobalt_opus_encoder_set_packet_loss_perc(encoder, params.packetLossPercent()));
        encApply("set_dtx", CobaltOpus.cobalt_opus_encoder_set_dtx(encoder, params.discontinuousTransmission() ? 1 : 0));
        encApply("set_force_channels", CobaltOpus.cobalt_opus_encoder_set_force_channels(encoder, params.forceChannels()));
        encApply("set_signal", CobaltOpus.cobalt_opus_encoder_set_signal(encoder, signal));
        encApply("set_lsb_depth", CobaltOpus.cobalt_opus_encoder_set_lsb_depth(encoder, params.lsbDepth()));
        encApply("set_max_bandwidth", CobaltOpus.cobalt_opus_encoder_set_max_bandwidth(encoder, params.maxBandwidth().toNative()));
    }

    @Override
    public EncodedAudioFrame encode(short[] pcm, int frameSize) {
        Objects.requireNonNull(pcm, "pcm cannot be null");
        requireOpen();
        var required = frameSize * channels;
        if (pcm.length < required) {
            throw new WhatsAppCallException.Opus(
                    "pcm length " + pcm.length + " is below frameSize*channels " + required);
        }
        MemorySegment.copy(pcm, 0, pcmInBuf, ValueLayout.JAVA_SHORT, 0, required);
        var t0 = System.nanoTime();
        int written;
        try {
            written = CobaltOpus.cobalt_opus_encode(encoder, pcmInBuf, frameSize, packetBuf, MAX_PACKET_BYTES);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Opus("cobalt_opus_encode failed", t);
        }
        var encodeMicros = (System.nanoTime() - t0) / 1000L;
        if (written < 0) {
            throw WhatsAppCallException.Opus.fromErr("cobalt_opus_encode", written);
        }
        totalEncodedFrames++;
        lifetimeEncodeMicros += encodeMicros;
        lifetimeEncodedBytes += written;
        if (appliedTargetBitrate != OpusCodecParams.BITRATE_AUTO) {
            lifetimeTargetBitrateSum += appliedTargetBitrate;
            targetBitrateSamples++;
        }
        var payload = new byte[written];
        if (written > 0) {
            MemorySegment.copy(packetBuf, ValueLayout.JAVA_BYTE, 0, payload, 0, written);
        }
        var discontinuous = written < SPEECH_THRESHOLD_BYTES;
        lastWasDiscontinuous = discontinuous;
        var voiceActive = !discontinuous && packetHasVoiceActivity(payload);
        var hasFec = !discontinuous && packetHasInbandFec(payload);
        // TODO: [perf] when written==0, skip the audioLevelDbov RMS scan and the byte[0] allocation and
        //  return a frame from a shared static EMPTY_PAYLOAD with SILENCE_LEVEL. The sole in-pipeline
        //  consumer (AudioEncoderSender.accept) discards empty frames via isEmpty() without reading
        //  levelDbov, but encode() is public AudioCodec API whose documented contract computes the captured
        //  loudness even for a DTX frame, so substituting SILENCE_LEVEL is an observable change for any
        //  other AudioCodec.encode caller; left as-is until that contract is confirmed unobserved.
        var levelDbov = audioLevelDbov(pcm, required);
        adaptComplexity(encodeMicros);
        return new EncodedAudioFrame(payload, voiceActive, discontinuous, hasFec, levelDbov);
    }

    /**
     * Measures the audio level of one captured block as a positive {@code -dBov} magnitude.
     *
     * <p>Computes the root-mean-square energy of the interleaved 16-bit samples, divides it by the
     * full-scale amplitude, and maps the ratio onto a {@code [0, 127]} magnitude where {@code 0} is the
     * loudest possible signal and {@code 127} is silence; a block with no measurable energy reports
     * {@link EncodedAudioFrame#SILENCE_LEVEL}. The level reflects the captured loudness rather than the
     * encoder output, so it is well defined even for a discontinuous-transmission frame.
     *
     * @implNote This implementation ports {@code fn4303} of the wa-voip WASM module
     * {@code ff-tScznZ8P} ({@code media/src/audio/wa_audio_level_rtp_ext_utils.cc}), which the native
     * encode invokes ({@code wa_opus.cc} fn6453, storing the result on the encoded-frame struct field
     * {@code +0x4a}) when the {@code calculate_audio_level} codec flag is set. The native body sums the
     * squares of every sample, takes {@code rms = (int) sqrt(sumSquares / count)} truncated toward
     * zero, and returns {@code (int) clamp(-20 * log10(rms / 32767.0), 0, 127) & 0x7f}; when
     * {@code rms / 32767.0} is not positive it returns {@code 127}. The native code divides the squared
     * sum by the sample-element count (so for a stereo block it divides by {@code frameSize * channels}
     * rather than by the per-channel frame count); this port follows that exactly by summing and
     * dividing over the interleaved element count. The compiled-in {@code level_factor_q8} and
     * {@code use_fixed_level_factor} voip parameters are not applied: both are registered but unused in
     * the WASM level path (referenced only by the config-key registrar fn7526, never by {@code fn4303},
     * the history add, or the encode), and both are absent from the 759-key voip-settings union.
     *
     * @param pcm         the captured interleaved samples; never {@code null}
     * @param sampleCount the number of valid interleaved samples at the start of {@code pcm}
     * @return the {@code -dBov} level magnitude in {@code [0, 127]}
     */
    private static int audioLevelDbov(short[] pcm, int sampleCount) {
        if (sampleCount < 1) {
            return EncodedAudioFrame.SILENCE_LEVEL;
        }
        var sumSquares = 0.0;
        for (var index = 0; index < sampleCount; index++) {
            double sample = pcm[index];
            sumSquares += sample * sample;
        }
        var rms = (int) Math.sqrt(sumSquares / sampleCount);
        var ratio = rms / AUDIO_LEVEL_FULL_SCALE;
        if (ratio <= 0.0) {
            return EncodedAudioFrame.SILENCE_LEVEL;
        }
        var level = AUDIO_LEVEL_DB_FACTOR * Math.log10(ratio);
        return (int) Math.clamp(level, 0.0, EncodedAudioFrame.SILENCE_LEVEL) & 0x7F;
    }

    /**
     * Feeds the frame's encode time to the complexity controller and applies a new level when it moves.
     *
     * @param encodeMicros the native encode wall time of the frame, in microseconds
     */
    private void adaptComplexity(long encodeMicros) {
        complexityController.recordEncode(encodeMicros, params.frameMillis());
        if (complexityController.complexityChanged()) {
            encApply("set_complexity", CobaltOpus.cobalt_opus_encoder_set_complexity(encoder, complexityController.complexity()));
        }
    }

    @Override
    public short[] decode(byte[] payload, int frameSize, boolean decodeFec) {
        Objects.requireNonNull(payload, "payload cannot be null");
        requireOpen();
        MemorySegment.copy(payload, 0, packetInBuf, ValueLayout.JAVA_BYTE, 0, payload.length);
        var t0 = System.nanoTime();
        int decoded;
        try {
            decoded = CobaltOpus.cobalt_opus_decode(decoder, packetInBuf, payload.length, pcmOutBuf, frameSize, decodeFec ? 1 : 0);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Opus("cobalt_opus_decode failed", t);
        }
        lifetimeDecodeMicros += (System.nanoTime() - t0) / 1000L;
        if (decoded < 0) {
            throw WhatsAppCallException.Opus.fromErr("cobalt_opus_decode", decoded);
        }
        totalDecodedFrames++;
        if (decodeFec) {
            fecFrames++;
        }
        return readPcm(decoded);
    }

    @Override
    public short[] recover(byte[] nextPayload, int frameSize) {
        requireOpen();
        var t0 = System.nanoTime();
        var decodeFec = fecPacker.shouldDecodeFec(nextPayload != null);
        int decoded;
        try {
            if (decodeFec) {
                MemorySegment.copy(nextPayload, 0, packetInBuf, ValueLayout.JAVA_BYTE, 0, nextPayload.length);
                decoded = CobaltOpus.cobalt_opus_decode(decoder, packetInBuf, nextPayload.length, pcmOutBuf, frameSize, 1);
            } else {
                decoded = CobaltOpus.cobalt_opus_decode(decoder, MemorySegment.NULL, 0, pcmOutBuf, frameSize, 0);
            }
        } catch (Throwable t) {
            throw new WhatsAppCallException.Opus("cobalt_opus_decode recovery failed", t);
        }
        lifetimeDecodeMicros += (System.nanoTime() - t0) / 1000L;
        if (decoded < 0) {
            throw WhatsAppCallException.Opus.fromErr("cobalt_opus_decode recovery", decoded);
        }
        if (decodeFec) {
            fecFrames++;
        } else {
            plcFrames++;
        }
        return readPcm(decoded);
    }

    /**
     * Copies the decoded samples from the native output buffer into a fresh heap array.
     *
     * @param decodedSamples the per-channel sample count libopus reported
     * @return the decoded interleaved PCM samples
     */
    private short[] readPcm(int decodedSamples) {
        var total = decodedSamples * channels;
        var out = new short[total];
        MemorySegment.copy(pcmOutBuf, ValueLayout.JAVA_SHORT, 0, out, 0, total);
        return out;
    }

    @Override
    public void modify(OpusCodecParams params) {
        Objects.requireNonNull(params, "params cannot be null");
        requireOpen();
        var cappedBitrate = capBitrate(params.defaultBitrate());
        encApply("set_bitrate", CobaltOpus.cobalt_opus_encoder_set_bitrate(encoder, cappedBitrate));
        this.appliedTargetBitrate = cappedBitrate;
        encApply("set_packet_loss_perc", CobaltOpus.cobalt_opus_encoder_set_packet_loss_perc(encoder, params.packetLossPercent()));
        encApply("set_vbr", CobaltOpus.cobalt_opus_encoder_set_vbr(encoder, params.variableBitrate() ? 1 : 0));
        encApply("set_complexity", CobaltOpus.cobalt_opus_encoder_set_complexity(encoder, params.complexity()));
        encApply("set_max_bandwidth", CobaltOpus.cobalt_opus_encoder_set_max_bandwidth(encoder, params.maxBandwidth().toNative()));
        this.params = params;
    }

    /**
     * Clamps a requested target bitrate by the cap implied by the maximum-bandwidth ceiling.
     *
     * <p>An automatic bitrate is passed through unchanged. Otherwise the requested value is clamped to
     * the open parameters' {@linkplain OpusCodecParams#maxBitrate() maximum bitrate}, which the
     * {@link OpusDefaultAttr} table derives per sample rate; the maximum-bandwidth ceiling further bounds
     * the effective rate libopus reaches.
     *
     * @implNote This implementation ports the bitrate-cap branch of {@code opus_codec_modify} (fn6263):
     * when no explicit cap is set the native code derives one from {@code opus_max_bandwidth}; calls2
     * uses the per-sample-rate {@link OpusDefaultAttr} maximum as that derived cap and leaves the
     * narrower bandwidth bound to libopus itself.
     *
     * @param requestedBitrate the requested target bitrate, or {@link OpusCodecParams#BITRATE_AUTO}
     * @return the clamped target bitrate
     */
    private int capBitrate(int requestedBitrate) {
        if (requestedBitrate == OpusCodecParams.BITRATE_AUTO) {
            return requestedBitrate;
        }
        return Math.min(requestedBitrate, params.maxBitrate());
    }

    @Override
    public AudioCodecStats stats() {
        var avgEncode = totalEncodedFrames == 0 ? 0 : lifetimeEncodeMicros / totalEncodedFrames;
        var avgDecode = totalDecodedFrames == 0 ? 0 : lifetimeDecodeMicros / totalDecodedFrames;
        return new AudioCodecStats(
                totalEncodedFrames,
                totalDecodedFrames,
                fecFrames,
                plcFrames,
                avgEncode,
                avgDecode,
                avgTargetBitrate(),
                observedBitrate());
    }

    /**
     * Derives the running average encoder target bitrate from the per-frame target-bitrate sum.
     *
     * @implNote This implementation reproduces the average-target-bitrate field
     * {@code opus_get_stats} (fn6274) of the wa-voip WASM module {@code ff-tScznZ8P} computes as the
     * encoder sub-state's running target-bitrate sum divided by its sample count (offsets
     * {@code 0x120 / 0x128}), yielding {@code 0} when no frame has been sampled. The native code samples
     * the resolved encoder bitrate each encode through {@code opus_encoder_ctl(OPUS_GET_BITRATE)}; this
     * port samples the target last applied through {@code cobalt_opus_encoder_set_bitrate}, which equals
     * the resolved target for every concrete bitrate. A frame encoded while the target is
     * {@link OpusCodecParams#BITRATE_AUTO} is not sampled, since the portable shim exposes no
     * {@code OPUS_GET_BITRATE} read to resolve the libopus-chosen value.
     *
     * @return the running average target bitrate in bits per second, or {@code 0} before the first
     * sampled frame
     */
    private int avgTargetBitrate() {
        if (targetBitrateSamples == 0) {
            return 0;
        }
        return (int) (lifetimeTargetBitrateSum / targetBitrateSamples);
    }

    /**
     * Derives the observed bitrate from the running average encoded-frame size and the frame duration.
     *
     * @implNote This implementation ports {@code calc_bitrate_from_avg_size} (fn6267): the average bytes
     * per frame times eight bits, scaled from per-frame to per-second by the frame duration in
     * milliseconds.
     *
     * @return the observed bitrate in bits per second, or {@code 0} before the first frame
     */
    private int observedBitrate() {
        if (totalEncodedFrames == 0 || params.frameMillis() <= 0) {
            return 0;
        }
        var avgBytes = (double) lifetimeEncodedBytes / totalEncodedFrames;
        return (int) Math.round(avgBytes * 8.0 * 1000.0 / params.frameMillis());
    }

    @Override
    public boolean lastFrameWasDiscontinuous() {
        return lastWasDiscontinuous;
    }

    /**
     * Inspects the encoded packet for the voice-activity flag.
     *
     * @implNote This implementation ports {@code wa_opus_check_vad_flags_wrapper} (fn6250) for the
     * standard (non-MLow) Opus path: it derives speech presence from the packet bandwidth, treating a
     * decodable bandwidth as voice activity. A more precise SILK-internal VAD read requires the patched
     * libopus expert CTLs the public binding does not expose.
     *
     * @param payload the encoded packet bytes
     * @return whether the packet is voice-active
     */
    private boolean packetHasVoiceActivity(byte[] payload) {
        if (payload.length < SPEECH_THRESHOLD_BYTES) {
            return false;
        }
        MemorySegment.copy(payload, 0, packetInBuf, ValueLayout.JAVA_BYTE, 0, payload.length);
        try {
            return CobaltOpus.cobalt_opus_packet_get_bandwidth(packetInBuf) >= 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Inspects the encoded packet for an in-band forward-error-correction (LBRR) copy of the previous
     * frame.
     *
     * @implNote This implementation ports {@code wa_opus_packet_has_fec_wrapper} (fn6249,
     * tree/xplat/wa-voip/wacall/media/src/codec/wa_opus.cc lines 10-24) for the standard Opus path: the
     * native standard-path branch ({@code *(param1+0x74) == 0}) calls fn9810 (lines 3424-3459), which
     * parses the Opus packet with the {@code opus_packet_parse_impl}-equivalent fn9553 (lines 3051-3294)
     * and reads the per-SILK-frame LBRR flag at the TOC-derived bit positions (frame count from fn9811,
     * lines 3466-3491). The portable {@link CobaltOpus} shim performs exactly that read in
     * {@code cobalt_opus_packet_has_lbrr}, so this method reads the per-packet LBRR flag directly from the
     * encoded packet rather than approximating it from the encoder's in-band-FEC configuration. A native
     * parse failure falls back to {@code false}; the result feeds FEC statistics only, not interop.
     *
     * @param payload the encoded packet bytes
     * @return whether the packet carries an in-band FEC (LBRR) copy of the previous frame
     */
    private boolean packetHasInbandFec(byte[] payload) {
        if (payload.length == 0) {
            return false;
        }
        MemorySegment.copy(payload, 0, packetInBuf, ValueLayout.JAVA_BYTE, 0, payload.length);
        try {
            return CobaltOpus.cobalt_opus_packet_has_lbrr(packetInBuf, payload.length) == 1;
        } catch (Throwable _) {
            return false;
        }
    }

    /**
     * Checks the return code of one typed {@link CobaltOpus} encoder setter, throwing on failure.
     *
     * <p>The typed {@code cobalt_opus_encoder_set_*} shim functions apply the matching variadic
     * {@code opus_encoder_ctl} request C-side and return the libopus result directly; this turns any
     * non-OK code into a thrown exception carrying libopus's textual description.
     *
     * @param control a short name of the control for the error message (e.g. {@code "set_bitrate"})
     * @param rc      the return code from the {@code cobalt_opus_encoder_set_*} call
     * @throws WhatsAppCallException.Opus if {@code rc} is not {@code COBALT_OPUS_OK}
     */
    private void encApply(String control, int rc) {
        if (rc != CobaltOpus.COBALT_OPUS_OK()) {
            throw WhatsAppCallException.Opus.fromErr("cobalt_opus_encoder_" + control, rc);
        }
    }

    /**
     * Verifies that both native states are still live.
     *
     * @throws IllegalStateException if the codec has been closed
     */
    private void requireOpen() {
        if (encoder == null || encoder.equals(MemorySegment.NULL)
                || decoder == null || decoder.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("OpusAudioCodec is closed");
        }
    }

    /**
     * Destroys the native encoder and decoder states if live, swallowing any native error.
     *
     * <p>Used from a failed constructor and from {@link #close()}; nulls each pointer to
     * {@link MemorySegment#NULL} so a later call detects the closed codec.
     */
    private void destroyStates() {
        if (encoder != null && !encoder.equals(MemorySegment.NULL)) {
            try {
                CobaltOpus.cobalt_opus_encoder_destroy(encoder);
            } catch (Throwable _) {
            }
            encoder = MemorySegment.NULL;
        }
        if (decoder != null && !decoder.equals(MemorySegment.NULL)) {
            try {
                CobaltOpus.cobalt_opus_decoder_destroy(decoder);
            } catch (Throwable _) {
            }
            decoder = MemorySegment.NULL;
        }
    }

    @Override
    public void close() {
        if (encoder == null || encoder.equals(MemorySegment.NULL)) {
            return;
        }
        destroyStates();
        arena.close();
    }
}
