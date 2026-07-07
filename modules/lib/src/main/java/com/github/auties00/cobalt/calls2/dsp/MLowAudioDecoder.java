package com.github.auties00.cobalt.calls2.dsp;

import com.github.auties00.cobalt.calls2.media.audio.mlow.MlowDecoder;
import com.github.auties00.cobalt.calls2.media.audio.mlow.postfilter.MlowDecodePostfilter;

import java.util.Objects;

/**
 * Decodes MLow low-bitrate speech packets into PCM for the {@link LiveNetEq} jitter buffer through the
 * pure-Java {@link MlowDecoder} kernel and {@link MlowDecodePostfilter} chain, a permitted
 * {@link AudioDecoder} the jitter buffer registers when a call negotiates the MLow codec.
 *
 * <p>MLow is WhatsApp's in-house low-bitrate speech codec (the {@code smpl} codec in the wa-voip sources),
 * a deterministic float CELP vocoder, not a neural network. Decoding a frame is a classical CELP
 * synthesis: the range coder arithmetic-decodes the quantized parameters behind a leading TOC byte naming
 * the frame configuration; the line spectral frequencies are dequantized from a two-stage LSF vector
 * quantization back into LPC short-term synthesis coefficients; the excitation is rebuilt from a
 * pitch/long-term-prediction adaptive codebook plus an algebraic fixed codebook of signed unit pulses; and
 * the short-term synthesis filter produces the speech. The only machine-learning component is an optional,
 * default-disabled bandwidth-extension (BWE) postfilter, applied after the codec core and not part of
 * decoding a frame.
 *
 * <p>{@link #decode(byte[], int, boolean)} decodes a packet in two stages: the {@link MlowDecoder} kernel
 * produces the pre-postfilter synthesis and the per-packet decode parameters
 * ({@link MlowDecoder#decodeWithSynthesis(byte[], boolean)}), and the {@link MlowDecodePostfilter} chain runs the
 * shipping decoder's harmonic, high-pass, and optional LPC postfilters over that synthesis before the
 * signal is scaled to signed 16-bit PCM with the native {@code smpl_float_to_int16} conversion;
 * {@link #reset()} returns both the kernel and the postfilter chain to their freshly constructed state
 * across a stream discontinuity. The decoder is single-writer: the kernel and the postfilter chain both
 * thread cross-frame and cross-packet state, so it must be driven from the jitter buffer's pull thread and
 * fed every packet of a stream in order.
 *
 * <p>Scope is the SMPL 16 kHz, 60 ms, mono, low-band path matching the {@link MlowDecoder} kernel. A 60 ms
 * MLow packet decodes to 960 samples regardless of the {@code frameSamples} the jitter buffer requests,
 * because the sample count is fixed by the packet's TOC, not by the caller; the returned array therefore
 * holds the packet's own sample count rather than exactly {@code frameSamples}. Packet-loss concealment and
 * in-band forward-error-correction are out of scope for this milestone, so {@link #conceal(int)} is
 * unimplemented and {@link #decode(byte[], int, boolean)} rejects a forward-error-correction request; the
 * encode side has no counterpart here at all.
 *
 * <p>The {@link MlowDecoder} kernel reproduces the native feature-dump decode path ({@code SMPL_DUMP_FEATURES}),
 * which the native build compiles with the harmonic, LPC, and high-pass postfilters switched off so its
 * {@code coded.s16} reference output is the bare CELP synthesis. The shipping decode path the live client
 * runs ({@code #if !SMPL_DUMP_FEATURES} in {@code smpl_core_decoder.c}) instead applies that postfilter
 * chain inside {@code smpl_core_decode} before scaling to {@code int16}. This wrapper restores the live
 * decode by running the {@link MlowDecodePostfilter} chain over the kernel's pre-postfilter synthesis, so
 * the PCM handed to the jitter buffer matches the live client's level and harmonic shaping; the kernel
 * itself stays byte-exact against the postfilter-off C oracle, since this wrapper consumes the kernel's
 * exposed synthesis rather than its scaled output.
 *
 * @implNote This implementation routes the decode dispatch the jitter buffer drives through the pure-Java
 * {@code AudioDecoderMLowImpl} port {@link MlowDecoder} of the wa-voip WASM module {@code ff-tScznZ8P}
 * ({@code xplat/rtc/audio_coding/mlow_codec/}); the RED framing ({@code MlowRedPayloadSplitter}) is
 * separate Java glue in the depacketization path, not here. The {@link MlowDecoder} kernel produces the
 * full packet's samples and ignores the requested per-frame sample count, so this wrapper does not enforce
 * the {@code frameSamples * channels} length the native NetEq path assumes for the fixed-rate codecs; the
 * concealment and forward-error-correction entry points stay unimplemented because the MLow PLC and the
 * RED-based recovery land in the concealment sub-milestone. The postfilter chain runs in this codec-to-NetEq
 * wrapper, over the kernel's {@link MlowDecoder#decodeWithSynthesis(byte[], boolean) exposed pre-postfilter
 * synthesis}, rather than inside the kernel, so the kernel keeps reproducing the native
 * {@code SMPL_DUMP_FEATURES} reference exactly while the receive path matches the live decoder. The
 * float-to-{@code int16} conversion is the shipping {@code smpl_float_to_int16} (a truncating cast clamped
 * to {@code [-32767, 32767]}), not the feature-dump rounding scale the kernel's
 * {@link MlowDecoder#decode(byte[])} applies, because the postfiltered signal is the shipping decode path.
 * The LPC postfilter is disabled by default, matching the native {@code LPC_postfilter_mode} default and the
 * live client's default-off {@code p->mlow_enable_lpc_postfilter} runtime parameter; a per-call value of
 * that parameter is resolved at session assembly and threaded in through the constructor.
 */
public final class MLowAudioDecoder implements AudioDecoder {
    /**
     * Bit mask of the voice-activity flag in the MLow TOC byte.
     *
     * <p>Set in the leading TOC byte of an MLow packet when the frame it heads carries active speech; the
     * same bit the encode side writes in {@code MLowAudioCodec}.
     */
    private static final int TOC_VAD_MASK = 0x40;

    /**
     * The float-to-{@code int16} scale, the native {@code smpl_float_to_int16} multiplier of {@code 32767}.
     */
    private static final float PCM_SCALE = 32767.0f;

    /**
     * The pure-Java MLow CELP synthesis kernel this decoder drives for the pre-postfilter synthesis.
     *
     * <p>Threads cross-frame and cross-packet state internally, so it is constructed once per stream and
     * driven only from the pull thread.
     */
    private final MlowDecoder kernel;

    /**
     * The pure-Java MLow decode postfilter chain this decoder runs over the kernel's synthesis.
     *
     * <p>Threads the harmonic, high-pass, and LPC postfilter state across packets, so it is constructed once
     * per stream alongside the kernel and reset with it.
     */
    private final MlowDecodePostfilter postfilter;

    /**
     * Whether the gated LPC postfilter runs, the native {@code p->mlow_enable_lpc_postfilter}; default
     * {@code false}.
     *
     * <p>The harmonic and high-pass postfilters always run as the live decoder's default level lift and
     * harmonic shaping; only the LPC postfilter is gated, so this flag is the per-call value of the runtime
     * parameter resolved at session assembly, defaulting to {@code false} when unset.
     */
    private final boolean lpcPostfilterEnabled;

    /**
     * The output sample rate in hertz this decoder reports.
     */
    private final int sampleRate;

    /**
     * The output channel count this decoder reports.
     */
    private final int channels;

    /**
     * Whether this decoder has been closed; once closed every decode, conceal, and reset call throws.
     */
    private boolean closed;

    /**
     * Constructs an MLow decoder for the given output geometry with the LPC postfilter disabled, backed by a
     * fresh {@link MlowDecoder} kernel and {@link MlowDecodePostfilter} chain.
     *
     * <p>Equivalent to {@link #MLowAudioDecoder(int, int, boolean)} with the LPC postfilter off, the live
     * client's default. Use the three-argument constructor when a per-call
     * {@code p->mlow_enable_lpc_postfilter} value has been resolved.
     *
     * @param sampleRate the output sample rate in Hz the decoder reports
     * @param channels   the output channel count the decoder reports, {@code 1} for mono
     */
    public MLowAudioDecoder(int sampleRate, int channels) {
        this(sampleRate, channels, false);
    }

    /**
     * Constructs an MLow decoder for the given output geometry, backed by a fresh {@link MlowDecoder} kernel
     * and {@link MlowDecodePostfilter} chain.
     *
     * <p>The kernel and the postfilter chain start in their reset state, ready to decode the first packet of
     * a stream. The supplied geometry is reported through {@link #sampleRate()} and {@link #channels()}; the
     * in-scope MLow path is 16 kHz mono, so the kernel itself produces 16 kHz mono samples regardless of
     * these values. The harmonic and high-pass postfilters always run; {@code lpcPostfilterEnabled} gates
     * only the LPC postfilter.
     *
     * @param sampleRate           the output sample rate in Hz the decoder reports
     * @param channels             the output channel count the decoder reports, {@code 1} for mono
     * @param lpcPostfilterEnabled the per-call {@code p->mlow_enable_lpc_postfilter} value; {@code false} for
     *                             the default-off live behaviour
     */
    public MLowAudioDecoder(int sampleRate, int channels, boolean lpcPostfilterEnabled) {
        this.kernel = new MlowDecoder();
        this.postfilter = new MlowDecodePostfilter();
        this.lpcPostfilterEnabled = lpcPostfilterEnabled;
        this.sampleRate = sampleRate;
        this.channels = channels;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation decodes the whole packet to its pre-postfilter synthesis through
     * {@link MlowDecoder#decodeWithSynthesis(byte[], boolean)}, runs the {@link MlowDecodePostfilter} chain over each
     * contained MLow packet's synthesis in stream order (so the postfilter state threads exactly as the live
     * decoder's), and converts the postfiltered synthesis to signed 16-bit PCM with the shipping
     * {@code smpl_float_to_int16} (a truncating cast clamped to {@code [-32767, 32767]}). The
     * {@code frameSamples} request is not enforced: an MLow packet's sample count is fixed by its TOC (a
     * 60 ms packet yields 960 samples), so the returned array holds the packet's own sample count. A
     * {@code fec} request is rejected because MLow in-band forward-error-correction is out of scope for this
     * milestone.
     */
    @Override
    public short[] decode(byte[] payload, int frameSamples, boolean fec) {
        Objects.requireNonNull(payload, "payload cannot be null");
        requireOpen();
        if (fec) {
            throw new UnsupportedOperationException(
                    "MLow in-band forward-error-correction decode is not implemented");
        }
        var result = kernel.decodeWithSynthesis(payload, lpcPostfilterEnabled);
        var totalSamples = 0;
        for (var packet : result.packets()) {
            totalSamples += packet.synthesis().length;
        }
        var pcm = new short[totalSamples];
        var offset = 0;
        for (var packet : result.packets()) {
            var synthesis = packet.synthesis();
            postfilter.process(synthesis, packet.numFrames(), packet.lpc(), packet.numSubframes(),
                    packet.subframeLength(), packet.lagsPerPacket(), packet.normalizedBitratePerFrame(),
                    packet.voiced(), packet.lowRate(), lpcPostfilterEnabled);
            for (var i = 0; i < synthesis.length; i++) {
                pcm[offset + i] = toInt16(synthesis[i]);
            }
            offset += synthesis.length;
        }
        return pcm;
    }

    /**
     * Scales one postfiltered float sample to a clamped signed 16-bit value, the native shipping
     * {@code smpl_float_to_int16} per-sample conversion.
     *
     * <p>Multiplies by {@value #PCM_SCALE}, clamps to {@code [-32767, 32767]}, and truncates toward zero by
     * the C {@code (int16_t)} cast, matching the conversion the shipping decode path applies after its
     * postfilter chain.
     *
     * @param sample the postfiltered float sample, nominally in {@code [-1, 1]}
     * @return the truncated and clamped {@code int16} sample
     */
    private static short toInt16(float sample) {
        var scaled = sample * PCM_SCALE;
        if (scaled > PCM_SCALE) {
            scaled = PCM_SCALE;
        } else if (scaled < -PCM_SCALE) {
            scaled = -PCM_SCALE;
        }
        return (short) (int) scaled;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation throws {@link UnsupportedOperationException}: MLow packet-loss
     * concealment lands in the concealment sub-milestone and is out of scope for the clean-decode wiring.
     */
    @Override
    public short[] conceal(int frameSamples) {
        requireOpen();
        throw new UnsupportedOperationException("MLow packet-loss concealment is not implemented");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation reads the voice-activity bit ({@link #TOC_VAD_MASK}) of the packet's
     * leading TOC byte, the same flag {@code MLowAudioCodec} writes on the encode side. An empty packet is
     * reported inactive.
     */
    @Override
    public boolean packetHasVoiceActivity(byte[] payload) {
        Objects.requireNonNull(payload, "payload cannot be null");
        return payload.length > 0 && (payload[0] & TOC_VAD_MASK) != 0;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation resets both the CELP kernel ({@link MlowDecoder#reset()}) and the
     * postfilter chain ({@link MlowDecodePostfilter#reset()}), returning the whole decode pipeline to its
     * freshly constructed state without releasing any resource.
     */
    @Override
    public void reset() {
        requireOpen();
        kernel.reset();
        postfilter.reset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int sampleRate() {
        return sampleRate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int channels() {
        return channels;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation marks the decoder closed; the kernel and the postfilter chain hold no
     * native resource, so closing only flips the closed flag. A second call has no effect.
     */
    @Override
    public void close() {
        closed = true;
    }

    /**
     * Verifies that the decoder is still open.
     *
     * @throws IllegalStateException if the decoder has been closed
     */
    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("MLowAudioDecoder is closed");
        }
    }
}
