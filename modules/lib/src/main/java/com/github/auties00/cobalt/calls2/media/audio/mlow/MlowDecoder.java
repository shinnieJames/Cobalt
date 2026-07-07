package com.github.auties00.cobalt.calls2.media.audio.mlow;

import com.github.auties00.cobalt.calls2.media.audio.mlow.celp.CelpSynthesizer;
import com.github.auties00.cobalt.calls2.media.audio.mlow.celp.NoiseGenerator;
import com.github.auties00.cobalt.calls2.media.audio.mlow.celp.ResNrgDequantizer;
import com.github.auties00.cobalt.calls2.media.audio.mlow.filter.Filters;
import com.github.auties00.cobalt.calls2.media.audio.mlow.lsf.LpcInterpolator;
import com.github.auties00.cobalt.calls2.media.audio.mlow.lsf.SubframeLpc;
import com.github.auties00.cobalt.calls2.media.audio.mlow.param.ParamDecoder;

import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end low-band speech decoder for the MLow codec, the port of the master per-frame and per-subframe
 * decode loop {@code smpl_core_decode} ({@code smpl_core_decoder.c}) together with the single-frame and
 * multiframe MLow packet handling of {@code opus_smpl_decode_frame} / {@code opus_smpl_packet_parse_impl}
 * ({@code opus_smpl_decode.c}).
 *
 * <p>This is the payoff stage of the MLow decode pipeline: it wires the bit-exact parameter front end and the
 * float synthesis back end into one call that turns a coded MLow packet into reconstructed PCM. For each
 * internal 20 ms frame it runs, in the native order:
 * <ol>
 * <li>parameter decode ({@link ParamDecoder}): voicing, LSF indices, pulses, gains, pitch lags;</li>
 * <li>per-subframe LSF interpolation and LPC stabilization ({@link SubframeLpc});</li>
 * <li>pitch lag dequantization to fractional sample lags, the native {@code laginds[i] * 0.5f +
 * SMPL_MIN_PITCH_LAG};</li>
 * <li>fixed-codebook excitation scatter ({@link CelpSynthesizer#genExcitation}), then per subframe the
 * adaptive-codebook/long-term-prediction contribution ({@link CelpSynthesizer#celpDecode}), the shaped noise
 * floor ({@link NoiseGenerator#genNoise}) added to the excitation, the optional unvoiced pulse-shaping ARMA,
 * and the 16th-order auto-regressive short-term synthesis filter ({@link Filters#ar16}) producing the
 * subframe speech;</li>
 * <li>a frame-level fixed second-order ARMA high-pass on the assembled frame.</li>
 * </ol>
 * The synthesized frames of a packet are concatenated, scaled to {@code int16}, and returned.
 *
 * <p>This decoder is stateful across the frames of a packet and across packets of one continuous stream. The
 * LPC synthesis memory (the last {@code SMPL_LPC_ORDER} synthesized samples), the unvoiced residual-energy
 * tracker, the unvoiced pulse-shaping ARMA state, and the high-pass ARMA state are all threaded from one
 * frame to the next, as is every cross-frame state inside the wrapped {@link ParamDecoder},
 * {@link SubframeLpc}, {@link CelpSynthesizer}, and {@link NoiseGenerator}. Construct one decoder per logical
 * stream and feed it every packet in order; {@link #reset()} returns the whole pipeline to the freshly
 * constructed state, the equivalent of {@code smpl_core_decode_init}.
 *
 * <p>Scope is the SMPL 16 kHz, 60 ms (and the 10/20 ms sub-cases), mono, low-band path with
 * {@code SMPL_LPC_ORDER == 16} and every postfilter disabled. The frame-level high-pass that survives is the
 * fixed second-order ARMA the native decoder applies in its feature-dump configuration (the
 * {@code SMPL_DUMP_FEATURES} branch), which is the postfilter-off reference path; the harmonic, tilt, and LPC
 * postfilters, the comfort-noise injection, the high-band (32/48 kHz) synthesis and resampling, and the
 * packet-loss concealment are all out of scope and not invoked. A packet announcing a sample rate above
 * 16 kHz is rejected by {@link ParamDecoder}. This type is stateful per stream and is not thread-safe.
 *
 * @implNote This implementation collapses the {@code smpl_core_decode} subframe loop to the operations that
 * affect clean-stream output, dropping the packet-loss-concealment calls that are no-ops on a loss-free
 * decode. {@code smpl_plc_decay_exc} is invoked with its reset flag set on every normal frame, so it only
 * clears the attenuation accumulator and never touches the excitation; {@code smpl_plc_adapt_lsf} and
 * {@code smpl_plc_bwe_recover} act only when a prior packet was lost; {@code smpl_plc_update_celp},
 * {@code smpl_plc_update_nrg}, and {@code smpl_plc_update_cng} only maintain concealment state for a future
 * loss. None alter the reconstructed PCM of a continuous decode, so they are omitted here and deferred to the
 * concealment sub-milestone. The LPC synthesis memory is laid out as the native {@code dec_state->lpc_synth_mem}
 * leading the frame's output window: a contiguous buffer holds the previous frame's trailing
 * {@code SMPL_LPC_ORDER} samples in front of the current frame so {@link Filters#ar16} reads its history by
 * the same {@code y[n - i]} arithmetic, and the native frame>0 save/restore of those leading samples is a
 * no-op in a continuous decode because the saved samples equal the carried memory. The unvoiced pulse-shaping
 * coefficient table {@code smpl_uv_pulse_shaping_coefs} is transcribed here; at high rate the leading
 * coefficient is {@code 1.0f}, which the native guard treats as "no shaping" and which also clears the
 * shaping state. The fixed high-pass coefficients are the native feature-dump {@code hp_a2}/{@code hp_b2}
 * constants. The float synthesis path is not bit-exact against the C decoder (C double-precision promotion
 * under fast-math versus Java strict single precision), matching the float tolerance carried from the upstream
 * filter and LPC milestones.
 */
public final class MlowDecoder {
    /**
     * Linear-prediction order of the MLow short-term filter, the native {@code SMPL_LPC_ORDER}; the count of
     * synthesis-memory history samples carried across frames.
     */
    private static final int LPC_ORDER = 16;

    /**
     * Sample-clock rate of the CELP core in kilohertz, the native {@code SMPL_CELP_FS_KHZ}; the per-lag
     * subframe length and the minimum pitch lag are products of it.
     */
    private static final int CELP_FS_KHZ = 16;

    /**
     * Minimum pitch lag in samples, the native {@code SMPL_MIN_PITCH_LAG} = {@code SMPL_MINPITCH_MS * 16}
     * with {@code SMPL_MINPITCH_MS == 2}, so {@code 32}. Added to the half-sample-quantized lag index to
     * recover the fractional pitch lag.
     */
    private static final int MIN_PITCH_LAG = 2 * CELP_FS_KHZ;

    /**
     * Length in samples of one pitch (lag) subframe, the native {@code SMPL_LAG_SUBFRLEN} = 40 (2.5 ms); the
     * frame's lag count is the frame length divided by it.
     */
    private static final int LAG_SUBFRLEN = 40;

    /**
     * Maximum low-band subframe length in samples, the native {@code SMPL_MAX_SF_LEN} = {@code 10 ms * 16 kHz}
     * = 160; the ceiling every in-scope {@code subframeLength} (80 or 160) stays within, and the size of the
     * reusable per-subframe {@link #excSubframe} and {@link #noise} scratch buffers.
     */
    private static final int MAX_SF_LEN = 160;

    /**
     * The unvoiced pulse-shaping coefficient table, the native {@code smpl_uv_pulse_shaping_coefs[2][2][2]}
     * indexed {@code [lowRate][maOrAr][tap]}.
     *
     * <p>For the high-rate mode both stages are the identity ({@code {1.0f, 0.0f}}), so the leading-coefficient
     * guard disables the shaping; for the low-rate mode the moving-average numerator is
     * {@code {0.5f, 0.1665f}} and the auto-regressive denominator is {@code {1.0f, -0.333f}}. The first index
     * is the low-rate flag, the second selects the numerator (0) or denominator (1) row, the third the tap.
     */
    private static final float[][][] UV_PULSE_SHAPING_COEFS = {
            {{1.0f, 0.0f}, {1.0f, 0.0f}},
            {{0.5f, 0.1665f}, {1.0f, -0.333f}}
    };

    /**
     * The low-rate tilt postfilter two-tap moving-average coefficients, the native
     * {@code smpl_post_tilt_coefs[2][2]} indexed {@code [voiced]}.
     *
     * <p>The unvoiced row is the identity {@code {1.0f, 0.0f}}, which the leading-coefficient guard treats as
     * "no tilt" and which leaves the excitation untouched; the voiced row {@code {0.84f, 0.16f}} is the de-tilt
     * the shipping low-rate decoder applies to the excitation before the short-term synthesis filter. The tilt
     * is the low-rate alternative to the LPC postfilter and runs only when the LPC postfilter is disabled.
     */
    private static final float[][] POST_TILT_COEFS = {
            {1.0f, 0.0f},
            {0.84f, 0.16f}
    };

    /**
     * The fixed second-order high-pass moving-average (numerator) coefficients, the native feature-dump
     * {@code hp_b2} = {@code {0.99049276f, -1.9809836f, 0.99049276f}}.
     *
     * <p>These are the postfilter-off high-pass the reference decoder applies under {@code SMPL_DUMP_FEATURES}
     * in place of the harmonic high-pass postfilter; matching them is required to reproduce the oracle's
     * {@code coded.s16} reference output.
     */
    private static final float[] HP_B2 = {0.99049276f, -1.9809836f, 0.99049276f};

    /**
     * The fixed second-order high-pass auto-regressive (denominator) coefficients, the native feature-dump
     * {@code hp_a2} = {@code {1.0f, -1.9808896f, 0.9810795f}}; {@code hp_a2[0]} is the monic {@code 1.0f}.
     */
    private static final float[] HP_A2 = {1.0f, -1.9808896f, 0.9810795f};

    /**
     * The float-to-int16 scale, the native {@code 32767.0f} multiplier the feature-dump output applies before
     * clamping to the signed 16-bit range.
     */
    private static final float PCM_SCALE = 32767.0f;

    /**
     * The MLow multiframe TOC indicator mask, the native {@code MLOW_MULTI_TOC_MASK} = {@code 0xC0}.
     *
     * <p>A non-CELT TOC byte whose top two bits are both set marks a multiframe MLow packet whose second byte
     * is the contained-frame count; otherwise the packet carries a single self-contained MLow frame.
     */
    private static final int MULTI_TOC_MASK = 0xC0;

    /**
     * The per-frame low-band parameter decoder, threading conditional-coding and previous-frame state across
     * the frames of a packet and across packets.
     */
    private final ParamDecoder paramDecoder;

    /**
     * The per-subframe LSF interpolator and LPC stabilizer, threading the previous-frame LSF vector.
     */
    private final SubframeLpc subframeLpc;

    /**
     * The CELP excitation synthesizer, owning the adaptive-codebook history ring.
     */
    private final CelpSynthesizer celpSynthesizer;

    /**
     * The shaped-noise generator, owning the noise-envelope and shaping-filter smoothing state.
     */
    private final NoiseGenerator noiseGenerator;

    /**
     * The LPC synthesis memory, the native {@code dec_state->lpc_synth_mem}: the last {@value #LPC_ORDER}
     * synthesized output samples of the previous frame, the history the next frame's short-term synthesis
     * filter reads.
     */
    private final float[] lpcSynthMem;

    /**
     * The unvoiced pulse-shaping ARMA filter state, the native {@code dec_state->uv_pulse_shaping_state}, two
     * taps (the moving-average memory and the auto-regressive memory).
     */
    private final float[] uvPulseShapingState;

    /**
     * The fixed high-pass ARMA filter state, the native {@code dec_state->hp_arma2_state}, four taps (the
     * two-tap moving-average memory and the two-tap auto-regressive memory).
     */
    private final float[] hpArma2State;

    /**
     * The previous frame's reconstructed unvoiced residual energy, the native {@code dec_state->prev_nrgres}.
     *
     * <p>Updated after every unvoiced subframe; read only by the DTX residual-energy interpolation, which is
     * out of scope here, so it is tracked for state fidelity but does not affect the clean-decode output.
     */
    private float prevNrgres;

    /**
     * The low-rate tilt postfilter single-tap moving-average memory, the native
     * {@code dec_state->tilt_postfilter}, held as a single-element vector for {@link Filters#ma1}.
     *
     * <p>Threaded across the subframes, frames, and packets of the {@link #decodeWithSynthesis(byte[], boolean)
     * synthesis capture path}, which applies the tilt postfilter; the byte-exact {@link #decode(byte[])} path
     * does not run the tilt and never reads or writes this state.
     */
    private final float[] tiltState;

    /**
     * Reusable per-subframe excitation buffer, sized to {@value #MAX_SF_LEN}. Every subframe fully
     * overwrites its {@code [0, subframeLength)} region with {@code lpcRes} before any read, so one
     * instance-owned buffer replaces the per-subframe allocation without changing any consumed value; the
     * decode is single-threaded per stream, so the single owner is safe.
     */
    private final float[] excSubframe;

    /**
     * Reusable per-subframe noise buffer, sized to {@value #MAX_SF_LEN}. {@link NoiseGenerator#genNoise}
     * fully defines its {@code [0, subframeLength)} region on every call before it is added into
     * {@link #excSubframe}, so one instance-owned buffer replaces the per-subframe allocation.
     */
    private final float[] noise;

    /**
     * Constructs an MLow low-band decoder with a freshly constructed parameter front end and synthesis back
     * end and zeroed cross-frame state.
     *
     * <p>The wrapped parameter decoder, LSF interpolator, CELP synthesizer, and noise generator all start in
     * their reset state, and the LPC synthesis memory and filter states start silent, ready to decode the
     * first packet of a stream.
     */
    public MlowDecoder() {
        this.paramDecoder = new ParamDecoder();
        this.subframeLpc = new SubframeLpc();
        this.celpSynthesizer = new CelpSynthesizer();
        this.noiseGenerator = new NoiseGenerator();
        this.lpcSynthMem = new float[LPC_ORDER];
        this.uvPulseShapingState = new float[2];
        this.hpArma2State = new float[4];
        this.tiltState = new float[1];
        this.excSubframe = new float[MAX_SF_LEN];
        this.noise = new float[MAX_SF_LEN];
    }

    /**
     * Returns this decoder to its freshly constructed state, the equivalent of {@code smpl_core_decode_init}.
     *
     * <p>Resets the wrapped parameter decoder, LSF interpolator, CELP synthesizer, and noise generator and
     * zeroes the LPC synthesis memory, the unvoiced pulse-shaping state, the high-pass state, and the
     * residual-energy tracker. Call this between independent decode sessions; do not call it between the
     * packets of one continuous stream, which must thread state.
     */
    public void reset() {
        paramDecoder.reset();
        subframeLpc.reset();
        celpSynthesizer.reset();
        noiseGenerator.reset();
        java.util.Arrays.fill(lpcSynthMem, 0.0f);
        java.util.Arrays.fill(uvPulseShapingState, 0.0f);
        java.util.Arrays.fill(hpArma2State, 0.0f);
        prevNrgres = 0.0f;
        tiltState[0] = 0.0f;
    }

    /**
     * Decodes one MLow packet to reconstructed 16-bit PCM, the {@code opus_smpl_decode_frame} entry point
     * restricted to the low-band path.
     *
     * <p>Parses the packet's leading TOC byte, dispatches a single-frame or multiframe MLow packet, decodes
     * every contained MLow frame in order through {@link #decodeFrameFloat(byte[], int, int)}, concatenates
     * the float output, and scales it to {@code int16} with the native {@code round(y * 32767)} clamp. The
     * cross-packet state on this decoder advances, so packets must be supplied in stream order.
     *
     * @param packet the complete MLow packet bytes, beginning with the TOC byte
     * @return the reconstructed PCM samples for the packet, {@code numFrames * frameLength16} entries
     * @throws IllegalArgumentException if {@code packet} is empty, malformed, or announces a sample rate
     *                                  above 16 kHz
     */
    public short[] decode(byte[] packet) {
        float[] pcm = decodeFloat(packet);
        short[] out = new short[pcm.length];
        for (int i = 0; i < pcm.length; i++) {
            out[i] = toInt16(pcm[i]);
        }
        return out;
    }

    /**
     * Decodes one MLow packet to reconstructed floating-point PCM, the float-precision form of
     * {@link #decode(byte[])}.
     *
     * <p>Identical to {@link #decode(byte[])} but returns the synthesized signal at full single precision
     * before the {@code int16} scale and clamp, the native {@code yBuf} contents. A caller that wants the raw
     * float reconstruction (for an apples-to-apples comparison against the C decoder's float pipeline, or for
     * further float-domain processing) uses this; {@link #decode(byte[])} is the integer-PCM convenience over
     * it.
     *
     * @param packet the complete MLow packet bytes, beginning with the TOC byte
     * @return the reconstructed float PCM samples for the packet, nominally in {@code [-1, 1]}
     * @throws IllegalArgumentException if {@code packet} is empty, malformed, or announces a sample rate
     *                                  above 16 kHz
     */
    public float[] decodeFloat(byte[] packet) {
        if (packet == null || packet.length < 1) {
            throw new IllegalArgumentException("empty MLow packet");
        }
        int toc = packet[0] & 0xFF;
        if (isMultiframe(toc)) {
            return decodeMultiframe(packet);
        }
        return decodeFrameFloat(packet, 0, packet.length);
    }

    /**
     * Decodes one MLow packet to its pre-postfilter float synthesis together with the per-packet decode
     * parameters the {@code MlowDecodePostfilter} chain consumes, the synthesis-and-parameters form of
     * {@link #decodeFloat(byte[])}.
     *
     * <p>Runs the same CELP synthesis as {@link #decode(byte[])} and {@link #decodeFloat(byte[])}, advancing
     * the same cross-frame and cross-packet state, but instead of high-pass filtering and scaling the result
     * it returns, per contained self-contained MLow packet, the native {@code yBuf} synthesis (every internal
     * frame's post-{@code ar16}, post-tilt samples concatenated) and the LPC coefficient sets, pitch lags,
     * per-frame normalized bitrate, voicing, and rate flags that the postfilter chain reads. A multiframe MLow
     * container yields one {@link PacketSynthesis} per contained MLow packet, each the unit a native
     * {@code smpl_core_decode} call and the postfilter chain operate over.
     *
     * <p>The returned synthesis is the shipping decoder's pre-postfilter synthesis: the native pre-postfilter
     * {@code y} buffer, the exact input the shipping decoder hands to its postfilter chain. It is the
     * postfilter-off kernel synthesis with the low-rate tilt postfilter applied (the tilt the shipping decoder
     * interleaves with the residual synthesis when the LPC postfilter is disabled), captured before the
     * feature-dump high-pass {@link #decodeFloat(byte[])} would apply. When {@code lpcPostfilterEnabled} is
     * {@code true} the tilt is not applied, mirroring the native mutual exclusion: the LPC postfilter (run by
     * the postfilter chain) is the tilt's alternative. A caller that wants the live (postfilter-on)
     * reconstruction runs the synthesis of every returned {@link PacketSynthesis} through one stream-scoped
     * {@code MlowDecodePostfilter} in order with the same {@code lpcPostfilterEnabled}, then scales to
     * {@code int16}; a caller that wants the bare postfilter-off, no-tilt PCM uses {@link #decode(byte[])}
     * directly.
     *
     * @param packet               the complete MLow packet bytes, beginning with the TOC byte
     * @param lpcPostfilterEnabled {@code true} when the postfilter chain will run the LPC postfilter, which
     *                             suppresses the low-rate tilt the synthesis would otherwise carry
     * @return the per-contained-packet pre-postfilter synthesis and decode parameters, in stream order
     * @throws IllegalArgumentException if {@code packet} is empty, malformed, or announces a sample rate
     *                                  above 16 kHz
     */
    public DecodeResult decodeWithSynthesis(byte[] packet, boolean lpcPostfilterEnabled) {
        if (packet == null || packet.length < 1) {
            throw new IllegalArgumentException("empty MLow packet");
        }
        List<PacketSynthesis> packets = new ArrayList<>();
        int toc = packet[0] & 0xFF;
        if (isMultiframe(toc)) {
            decodeMultiframeWithSynthesis(packet, packets, lpcPostfilterEnabled);
        } else {
            packets.add(decodeFrameWithSynthesis(packet, 0, packet.length, lpcPostfilterEnabled));
        }
        return new DecodeResult(List.copyOf(packets));
    }

    /**
     * Decodes a multiframe MLow packet to the pre-postfilter synthesis of each contained MLow packet, the
     * synthesis-and-parameters form of {@link #decodeMultiframe(byte[])}.
     *
     * <p>Walks the multiframe layout exactly as {@link #decodeMultiframe(byte[])}, but decodes each contained
     * single-frame MLow packet through {@link #decodeFrameWithSynthesis(byte[], int, int, boolean)} and appends its
     * {@link PacketSynthesis} to {@code out}, threading the cross-frame state.
     *
     * @param packet               the complete multiframe MLow packet
     * @param out                  the accumulator the per-contained-packet synthesis blocks are appended to
     * @param lpcPostfilterEnabled {@code true} when the LPC postfilter suppresses the low-rate tilt
     * @throws IllegalArgumentException if the multiframe layout is malformed
     */
    private void decodeMultiframeWithSynthesis(byte[] packet, List<PacketSynthesis> out,
                                               boolean lpcPostfilterEnabled) {
        if (packet.length < 2) {
            throw new IllegalArgumentException("truncated multiframe MLow packet");
        }
        int numFrames = packet[1] & 0xFF;
        if (numFrames < 2) {
            throw new IllegalArgumentException("multiframe MLow packet with fewer than two frames");
        }
        int[] sizes = new int[numFrames];
        int pos = 2;
        for (int i = 0; i < numFrames - 1; i++) {
            int first = packet[pos] & 0xFF;
            if (first < 252) {
                sizes[i] = first;
                pos += 1;
            } else {
                if (pos + 1 >= packet.length) {
                    throw new IllegalArgumentException("truncated multiframe MLow size field");
                }
                sizes[i] = 4 * (packet[pos + 1] & 0xFF) + first;
                pos += 2;
            }
            if (sizes[i] <= 0) {
                throw new IllegalArgumentException("malformed multiframe MLow frame size");
            }
        }
        int consumed = 0;
        for (int i = 0; i < numFrames - 1; i++) {
            consumed += sizes[i];
        }
        sizes[numFrames - 1] = packet.length - pos - consumed;
        if (sizes[numFrames - 1] <= 0) {
            throw new IllegalArgumentException("malformed multiframe MLow last-frame size");
        }
        for (int i = 0; i < numFrames; i++) {
            out.add(decodeFrameWithSynthesis(packet, pos, sizes[i], lpcPostfilterEnabled));
            pos += sizes[i];
        }
    }

    /**
     * Decodes one self-contained single-frame MLow packet to its pre-postfilter synthesis and decode
     * parameters, the synthesis-and-parameters form of {@link #decodeFrameFloat(byte[], int, int)}.
     *
     * <p>Decodes the TOC and the packet's internal 20 ms frames through {@link ParamDecoder}, then runs the
     * per-frame synthesis loop ({@link #synthesizeFrameWithCapture}) over each decoded frame, accumulating the
     * pre-{@code arma2} synthesis, the per-frame LPC coefficient sets, the per-frame pitch lags, and the
     * per-frame normalized bitrate. The cross-frame state advances exactly as the byte-exact path; the only
     * difference is that the feature-dump high-pass is not applied and the synthesis is returned at full
     * single precision. The packet-level {@code voiced} and {@code lowRate} carried by the returned
     * {@link PacketSynthesis} are those of the packet's first internal frame, matching the postfilter chain's
     * per-packet gamma selection.
     *
     * @param packet               the backing array holding the single-frame packet
     * @param offset               the offset of the TOC byte within {@code packet}
     * @param length               the length of the single-frame packet, including the TOC byte
     * @param lpcPostfilterEnabled {@code true} when the LPC postfilter suppresses the low-rate tilt
     * @return the pre-postfilter synthesis and decode parameters of the packet
     * @throws IllegalArgumentException if the packet announces a sample rate above 16 kHz
     */
    private PacketSynthesis decodeFrameWithSynthesis(byte[] packet, int offset, int length,
                                                     boolean lpcPostfilterEnabled) {
        MlowTocByte tocByte = MlowTocByte.decode(packet[offset] & 0xFF);
        ParamDecoder.DecodedFrame[] decodedFrames = paramDecoder.decodePacket(tocByte, packet, offset, length);

        int frameLength = tocByte.frameLength16();
        int numSubframes = tocByte.numSubframes();
        int subframeLength = frameLength / numSubframes;
        boolean lowRate = tocByte.lowRate();
        int lagsPerSubframe = subframeLength / LAG_SUBFRLEN;
        int lagsPerFrame = frameLength / LAG_SUBFRLEN;
        int numFrames = decodedFrames.length;

        float[] synthesis = new float[numFrames * frameLength];
        float[][][] lpc = new float[numFrames][][];
        float[] lagsPerPacket = new float[numFrames * lagsPerFrame];
        float[] normalizedBitratePerFrame = new float[numFrames];
        for (int frame = 0; frame < numFrames; frame++) {
            float[][] frameLpc = new float[numSubframes][];
            float[] frameLags = new float[lagsPerFrame];
            float[] frameNormalizedBitrate = new float[1];
            float[] frameOut = synthesizeFrameWithCapture(decodedFrames[frame], tocByte, frameLength,
                    numSubframes, subframeLength, lowRate, lagsPerSubframe, lagsPerFrame, lpcPostfilterEnabled,
                    frameLpc, frameLags, frameNormalizedBitrate);
            System.arraycopy(frameOut, 0, synthesis, frame * frameLength, frameLength);
            lpc[frame] = frameLpc;
            System.arraycopy(frameLags, 0, lagsPerPacket, frame * lagsPerFrame, lagsPerFrame);
            normalizedBitratePerFrame[frame] = frameNormalizedBitrate[0];
        }

        boolean voiced = numFrames > 0 && decodedFrames[0].voiced();
        return new PacketSynthesis(synthesis, numFrames, lpc, numSubframes, subframeLength, lagsPerPacket,
                normalizedBitratePerFrame, voiced, lowRate);
    }

    /**
     * Synthesizes one internal 20 ms frame to its pre-{@code arma2} float PCM and captures the decode
     * parameters the postfilter chain reads, the parameter-capturing form of
     * {@link #synthesizeFrame(ParamDecoder.DecodedFrame, MlowTocByte, int, int, int, boolean, int, int)}.
     *
     * <p>Runs the identical synthesis as {@link #synthesizeFrame} up to and including the short-term synthesis
     * filter and the synthesis-memory save, but additionally applies the shipping decoder's low-rate tilt
     * postfilter to the excitation before the short-term synthesis filter (when the frame is low-rate, the
     * tilt coefficient is non-trivial, and the LPC postfilter is disabled), returns the assembled frame before
     * the feature-dump high-pass (the native pre-postfilter {@code y}), and writes the per-subframe LPC
     * coefficient sets, the frame's pitch lags, and the frame's normalized bitrate into the supplied capture
     * buffers. The tilt memory ({@link #tiltState}) threads across subframes, frames, and packets.
     *
     * @param df                       the decoded parameters of this frame
     * @param toc                      the decoded TOC of the packet
     * @param frameLength              the frame length in samples, the native {@code frame_length_16}
     * @param numSubframes             the subframe count of the frame
     * @param subframeLength           the subframe length in samples, the native {@code subframe_length_16}
     * @param lowRate                  {@code true} for the low-rate mode
     * @param lagsPerSubframe          the number of lag subframes per subframe
     * @param lagsPerFrame             the number of lag subframes per frame
     * @param lpcPostfilterEnabled     {@code true} when the LPC postfilter suppresses the low-rate tilt
     * @param lpcOut                   the per-subframe LPC capture buffer, filled with each subframe's
     *                                 {@value #LPC_ORDER}-plus-one coefficient set
     * @param lagsOut                  the frame-lag capture buffer of {@code lagsPerFrame} entries
     * @param normalizedBitrateOut     the single-entry normalized-bitrate capture buffer
     * @return the pre-{@code arma2} float PCM of the frame, {@code frameLength} entries
     */
    private float[] synthesizeFrameWithCapture(ParamDecoder.DecodedFrame df, MlowTocByte toc, int frameLength,
                                               int numSubframes, int subframeLength, boolean lowRate,
                                               int lagsPerSubframe, int lagsPerFrame, boolean lpcPostfilterEnabled,
                                               float[][] lpcOut, float[] lagsOut, float[] normalizedBitrateOut) {
        boolean voiced = df.voiced();
        LpcInterpolator.InterpolatedFrame interpolated = subframeLpc.process(df, toc);
        float[][] a = interpolated.lpc();
        float[][] lsfs = interpolated.lsf();

        float[] lags = new float[lagsPerFrame];
        for (int i = 0; i < lagsPerFrame; i++) {
            lags[i] = voiced ? df.laginds()[i] * 0.5f + MIN_PITCH_LAG : 0.0f;
        }

        float normalizedBitrate = normalizedBitrate(df.nPulses(), frameLength);

        float[] lpcRes = new float[frameLength];
        celpSynthesizer.genExcitation(df.fcbgIdx(), voiced, numSubframes, subframeLength,
                df.nPositions(), df.positions(), df.posPulses(), lpcRes);

        // y holds LPC_ORDER history samples then the frame's frameLength output samples.
        float[] y = new float[LPC_ORDER + frameLength];
        System.arraycopy(lpcSynthMem, 0, y, 0, LPC_ORDER);

        for (int sf = 0; sf < numSubframes; sf++) {
            float[] acbGain = CelpSynthesizer.acbDequant(lowRate, df.acbgIdx()[sf]);
            System.arraycopy(lpcRes, sf * subframeLength, excSubframe, 0, subframeLength);
            celpSynthesizer.celpDecode(voiced, acbGain, sliceLags(lags, sf * lagsPerSubframe, lagsPerSubframe),
                    lagsPerSubframe, subframeLength, lowRate, normalizedBitrate, excSubframe);

            float nrgres = ResNrgDequantizer.dequantizeResnrg(df.nrgresDbqQ14()[sf], subframeLength);
            if (!voiced) {
                prevNrgres = nrgres;
            }

            noiseGenerator.genNoise(excSubframe, subframeLength, voiced, df.sfPulses()[sf], nrgres,
                    df.fcbgIdx()[sf], lsfs[sf], normalizedBitrate, noise);

            int lowRateIx = lowRate ? 1 : 0;
            if (!voiced && df.sfPulses()[sf] > 0 && UV_PULSE_SHAPING_COEFS[lowRateIx][0][0] < 1.0f) {
                Filters.arma1(excSubframe, 0, subframeLength, UV_PULSE_SHAPING_COEFS[lowRateIx][0],
                        UV_PULSE_SHAPING_COEFS[lowRateIx][1], uvPulseShapingState, 0);
            } else {
                uvPulseShapingState[0] = 0.0f;
                uvPulseShapingState[1] = 0.0f;
            }

            for (int i = 0; i < subframeLength; i++) {
                excSubframe[i] += noise[i];
            }

            int voicedIx = voiced ? 1 : 0;
            if (!lpcPostfilterEnabled) {
                if (lowRate && POST_TILT_COEFS[voicedIx][0] < 1.0f) {
                    float[] tilted = new float[subframeLength];
                    Filters.ma1(excSubframe, 0, subframeLength, POST_TILT_COEFS[voicedIx], tiltState, 0,
                            tilted, 0);
                    System.arraycopy(tilted, 0, excSubframe, 0, subframeLength);
                } else {
                    tiltState[0] = excSubframe[subframeLength - 1];
                }
            }

            Filters.ar16(excSubframe, 0, subframeLength, a[sf], y, LPC_ORDER + sf * subframeLength);
            lpcOut[sf] = a[sf].clone();
        }

        System.arraycopy(y, frameLength, lpcSynthMem, 0, LPC_ORDER);

        float[] frameOut = new float[frameLength];
        System.arraycopy(y, LPC_ORDER, frameOut, 0, frameLength);

        System.arraycopy(lags, 0, lagsOut, 0, lagsPerFrame);
        normalizedBitrateOut[0] = normalizedBitrate;
        return frameOut;
    }

    /**
     * The pre-postfilter synthesis and decode parameters of one decoded MLow packet, the input one
     * {@code MlowDecodePostfilter} pass over a self-contained MLow packet consumes.
     *
     * <p>Carries every value the postfilter chain's entry point needs: the native pre-postfilter {@code yBuf}
     * synthesis (every internal frame's post-{@code ar16}, post-tilt samples concatenated), the per-frame
     * per-subframe LPC coefficient sets, the per-frame pitch lags, the per-frame normalized bitrate, and the
     * packet's voicing and rate flags. The {@link #synthesis()} array is owned by the caller and is the buffer
     * the postfilter rewrites in place.
     *
     * @param synthesis                  the pre-postfilter synthesis, {@code numFrames * frameLength} samples
     * @param numFrames                  the number of internal frames in the packet
     * @param lpc                        the per-frame per-subframe LPC coefficient sets, indexed
     *                                   {@code [frame][subframe][0..LPC_ORDER]} with index zero the monic
     *                                   {@code 1.0f}
     * @param numSubframes               the number of subframes per internal frame
     * @param subframeLength             the subframe length in samples
     * @param lagsPerPacket              the packet's pitch lags, one per lag subframe, frames concatenated
     * @param normalizedBitratePerFrame  the per-frame normalized bitrate, {@code numFrames} entries
     * @param voiced                     {@code true} when the packet's first internal frame is voiced
     * @param lowRate                    {@code true} for the low-rate mode
     */
    public record PacketSynthesis(float[] synthesis, int numFrames, float[][][] lpc, int numSubframes,
                                  int subframeLength, float[] lagsPerPacket, float[] normalizedBitratePerFrame,
                                  boolean voiced, boolean lowRate) {
    }

    /**
     * The result of {@link #decodeWithSynthesis(byte[], boolean)}: the per-contained-packet pre-postfilter synthesis
     * and decode parameters of one decoded MLow packet, in stream order.
     *
     * <p>A single-frame MLow packet yields one {@link PacketSynthesis}; a multiframe MLow container yields one
     * per contained MLow packet. A caller runs each {@link PacketSynthesis} through a stream-scoped
     * {@code MlowDecodePostfilter} in list order to reproduce the live decode.
     *
     * @param packets the per-contained-packet synthesis blocks, in stream order
     */
    public record DecodeResult(List<PacketSynthesis> packets) {
    }

    /**
     * Decodes a multiframe MLow packet by splitting it into its contained single-frame packets and decoding
     * each in stream order, the port of {@code opus_smpl_packet_parse_multiframe_mlow} followed by per-frame
     * decode.
     *
     * <p>A multiframe MLow packet is a one-byte multiframe TOC, a one-byte contained-frame count, a sequence
     * of self-delimiting per-frame sizes (all but the last, whose size is the remaining bytes), and then the
     * contained single-frame MLow packets back to back, each with its own TOC and body. This method walks
     * that layout, decodes each contained frame through {@link #decodeFrameFloat(byte[], int, int)} threading
     * the cross-frame state, and concatenates the float output.
     *
     * @param packet the complete multiframe MLow packet
     * @return the reconstructed float PCM for every contained frame, concatenated in order
     * @throws IllegalArgumentException if the multiframe layout is malformed
     */
    private float[] decodeMultiframe(byte[] packet) {
        if (packet.length < 2) {
            throw new IllegalArgumentException("truncated multiframe MLow packet");
        }
        int numFrames = packet[1] & 0xFF;
        if (numFrames < 2) {
            throw new IllegalArgumentException("multiframe MLow packet with fewer than two frames");
        }
        int[] sizes = new int[numFrames];
        int pos = 2;
        for (int i = 0; i < numFrames - 1; i++) {
            int first = packet[pos] & 0xFF;
            if (first < 252) {
                sizes[i] = first;
                pos += 1;
            } else {
                if (pos + 1 >= packet.length) {
                    throw new IllegalArgumentException("truncated multiframe MLow size field");
                }
                sizes[i] = 4 * (packet[pos + 1] & 0xFF) + first;
                pos += 2;
            }
            if (sizes[i] <= 0) {
                throw new IllegalArgumentException("malformed multiframe MLow frame size");
            }
        }
        int consumed = 0;
        for (int i = 0; i < numFrames - 1; i++) {
            consumed += sizes[i];
        }
        sizes[numFrames - 1] = packet.length - pos - consumed;
        if (sizes[numFrames - 1] <= 0) {
            throw new IllegalArgumentException("malformed multiframe MLow last-frame size");
        }
        float[][] frames = new float[numFrames][];
        int total = 0;
        for (int i = 0; i < numFrames; i++) {
            frames[i] = decodeFrameFloat(packet, pos, sizes[i]);
            total += frames[i].length;
            pos += sizes[i];
        }
        float[] out = new float[total];
        int off = 0;
        for (float[] frame : frames) {
            System.arraycopy(frame, 0, out, off, frame.length);
            off += frame.length;
        }
        return out;
    }

    /**
     * Decodes one self-contained single-frame MLow packet (TOC plus body) to reconstructed float PCM, the
     * core of {@code smpl_core_decode} over one packet.
     *
     * <p>Decodes the TOC, then decodes the packet's internal 20 ms frames through {@link ParamDecoder}, and
     * runs the per-frame synthesis loop ({@link #synthesizeFrame}) over each decoded frame, concatenating the
     * float output. The cross-frame state on this decoder advances. A SID packet decodes only its first
     * internal frame (later SID frames are concealment territory, out of scope here).
     *
     * @param packet the backing array holding the single-frame packet
     * @param offset the offset of the TOC byte within {@code packet}
     * @param length the length of the single-frame packet, including the TOC byte
     * @return the reconstructed float PCM for the packet
     * @throws IllegalArgumentException if the packet announces a sample rate above 16 kHz
     */
    private float[] decodeFrameFloat(byte[] packet, int offset, int length) {
        MlowTocByte tocByte = MlowTocByte.decode(packet[offset] & 0xFF);
        // TODO: wire MlowBandwidthExtension - on the >16 kHz (SWB/FB) branch ParamDecoder currently rejects, instantiate MlowBandwidthExtension and call decodeWideband(low band + LPC residual + nyquist gains + MlowHbParamDecoder HbFrameInput) to synthesize 32/48 kHz PCM, gated on the packet-announced sample rate from tocByte
        // TODO: wire MlowLsfInterpolTables - reachable via decodeWideband -> hbLsfInterpolate -> MlowLsfInterpolTables.factors once the SWB bandwidth-extension path is wired here and in MLowAudioDecoder.decode
        ParamDecoder.DecodedFrame[] decodedFrames = paramDecoder.decodePacket(tocByte, packet, offset, length);

        int frameLength = tocByte.frameLength16();
        int numSubframes = tocByte.numSubframes();
        int subframeLength = frameLength / numSubframes;
        boolean lowRate = tocByte.lowRate();
        int lagsPerSubframe = subframeLength / LAG_SUBFRLEN;
        int lagsPerFrame = frameLength / LAG_SUBFRLEN;

        float[] out = new float[decodedFrames.length * frameLength];
        for (int frame = 0; frame < decodedFrames.length; frame++) {
            float[] frameOut = synthesizeFrame(decodedFrames[frame], tocByte, frameLength, numSubframes,
                    subframeLength, lowRate, lagsPerSubframe, lagsPerFrame);
            System.arraycopy(frameOut, 0, out, frame * frameLength, frameLength);
        }
        return out;
    }

    /**
     * Synthesizes one internal 20 ms frame to float PCM, the body of the {@code smpl_core_decode} per-frame
     * loop.
     *
     * <p>Interpolates the frame's LSF to per-subframe LPC, dequantizes the pitch lags, builds the
     * fixed-codebook excitation, then per subframe adds the adaptive-codebook contribution and the shaped
     * noise, applies the optional unvoiced pulse-shaping ARMA, and runs the short-term synthesis filter to
     * produce the subframe speech against the carried LPC synthesis memory. After the subframes the frame's
     * trailing {@value #LPC_ORDER} samples are saved as the next frame's synthesis memory and the assembled
     * frame is high-pass filtered.
     *
     * @param df              the decoded parameters of this frame
     * @param toc             the decoded TOC of the packet
     * @param frameLength     the frame length in samples, the native {@code frame_length_16}
     * @param numSubframes    the subframe count of the frame
     * @param subframeLength  the subframe length in samples, the native {@code subframe_length_16}
     * @param lowRate         {@code true} for the low-rate mode
     * @param lagsPerSubframe the number of lag subframes per subframe
     * @param lagsPerFrame    the number of lag subframes per frame
     * @return the high-pass-filtered float PCM of the frame, {@code frameLength} entries
     */
    private float[] synthesizeFrame(ParamDecoder.DecodedFrame df, MlowTocByte toc, int frameLength,
                                    int numSubframes, int subframeLength, boolean lowRate,
                                    int lagsPerSubframe, int lagsPerFrame) {
        boolean voiced = df.voiced();
        LpcInterpolator.InterpolatedFrame interpolated = subframeLpc.process(df, toc);
        float[][] a = interpolated.lpc();
        float[][] lsfs = interpolated.lsf();

        float[] lags = new float[lagsPerFrame];
        for (int i = 0; i < lagsPerFrame; i++) {
            lags[i] = voiced ? df.laginds()[i] * 0.5f + MIN_PITCH_LAG : 0.0f;
        }

        float normalizedBitrate = normalizedBitrate(df.nPulses(), frameLength);

        float[] lpcRes = new float[frameLength];
        celpSynthesizer.genExcitation(df.fcbgIdx(), voiced, numSubframes, subframeLength,
                df.nPositions(), df.positions(), df.posPulses(), lpcRes);

        // y holds LPC_ORDER history samples then the frame's frameLength output samples.
        float[] y = new float[LPC_ORDER + frameLength];
        System.arraycopy(lpcSynthMem, 0, y, 0, LPC_ORDER);

        for (int sf = 0; sf < numSubframes; sf++) {
            float[] acbGain = CelpSynthesizer.acbDequant(lowRate, df.acbgIdx()[sf]);
            System.arraycopy(lpcRes, sf * subframeLength, excSubframe, 0, subframeLength);
            celpSynthesizer.celpDecode(voiced, acbGain, sliceLags(lags, sf * lagsPerSubframe, lagsPerSubframe),
                    lagsPerSubframe, subframeLength, lowRate, normalizedBitrate, excSubframe);

            float nrgres = ResNrgDequantizer.dequantizeResnrg(df.nrgresDbqQ14()[sf], subframeLength);
            if (!voiced) {
                prevNrgres = nrgres;
            }

            noiseGenerator.genNoise(excSubframe, subframeLength, voiced, df.sfPulses()[sf], nrgres,
                    df.fcbgIdx()[sf], lsfs[sf], normalizedBitrate, noise);

            int lowRateIx = lowRate ? 1 : 0;
            if (!voiced && df.sfPulses()[sf] > 0 && UV_PULSE_SHAPING_COEFS[lowRateIx][0][0] < 1.0f) {
                Filters.arma1(excSubframe, 0, subframeLength, UV_PULSE_SHAPING_COEFS[lowRateIx][0],
                        UV_PULSE_SHAPING_COEFS[lowRateIx][1], uvPulseShapingState, 0);
            } else {
                uvPulseShapingState[0] = 0.0f;
                uvPulseShapingState[1] = 0.0f;
            }

            for (int i = 0; i < subframeLength; i++) {
                excSubframe[i] += noise[i];
            }

            Filters.ar16(excSubframe, 0, subframeLength, a[sf], y, LPC_ORDER + sf * subframeLength);
        }

        System.arraycopy(y, frameLength, lpcSynthMem, 0, LPC_ORDER);

        float[] frameOut = new float[frameLength];
        System.arraycopy(y, LPC_ORDER, frameOut, 0, frameLength);
        Filters.arma2(frameOut, 0, frameLength, HP_B2, HP_A2, hpArma2State, 0);
        return frameOut;
    }

    /**
     * Extracts one subframe's slice of the frame lag array, mirroring the native pointer
     * {@code &lags[frame * lags_per_frame + sf * lags_per_subframe]}.
     *
     * @param lags   the frame's lag array
     * @param offset the index of the subframe's first lag
     * @param length the number of lags spanning the subframe
     * @return a freshly allocated copy of the subframe's lags
     */
    private static float[] sliceLags(float[] lags, int offset, int length) {
        float[] out = new float[length];
        System.arraycopy(lags, offset, out, 0, length);
        return out;
    }

    /**
     * Computes the frame's normalized bitrate from its pulse count, {@code smpl_get_normalized_bitrate}.
     *
     * <p>Forms the per-20-ms pulse density, applies the native log2 and sigmoid mapping, and returns the
     * normalized bitrate in {@code [0, 1]} that the adaptive-codebook high boost and the unvoiced noise
     * envelope interpolate against.
     *
     * @param numPulses   the frame's total pulse count, the native {@code n_pulses}
     * @param frameLength the frame length in samples, the native {@code frame_length_16}
     * @return the normalized bitrate in {@code [0, 1]}
     */
    private static float normalizedBitrate(int numPulses, int frameLength) {
        float pulsesPer20ms = (numPulses * frameLength) / (20.0f * 16.0f);
        float x = 1.4f * (float) (Math.log(pulsesPer20ms + 1.0f) / Math.log(2.0)) - 6.5f;
        return sigmoid(x);
    }

    /**
     * Computes the numerically guarded logistic sigmoid, {@code smpl_sigmoid}.
     *
     * <p>Saturates to one above {@code 80} and to zero below {@code -80} to keep the exponential finite,
     * matching the native helper the normalized-bitrate mapping uses.
     *
     * @param x the argument
     * @return the logistic value in {@code [0, 1]}
     */
    private static float sigmoid(float x) {
        if (x > 80.0f) {
            return 1.0f;
        }
        if (x < -80.0f) {
            return 0.0f;
        }
        return (float) (1.0 / (1.0 + Math.exp(-x)));
    }

    /**
     * Returns whether a TOC byte marks a multiframe MLow packet, {@code is_mlow_multiframe_packet}.
     *
     * <p>A non-CELT MLow TOC byte (top two bits not both set as the CELT marker) is multiframe when its top
     * two bits are both set. The CELT case is out of scope; a CELT TOC would have its top two bits set as the
     * CELT marker, but the SMPL low-band decoder only handles MLow packets, so the simple mask test suffices
     * for the in-scope inputs.
     *
     * @param toc the TOC byte, read from the low eight bits
     * @return {@code true} when the packet is a multiframe MLow packet
     */
    private static boolean isMultiframe(int toc) {
        return (toc & MULTI_TOC_MASK) == MULTI_TOC_MASK;
    }

    /**
     * Scales one float sample to a clamped signed 16-bit value, the native feature-dump
     * {@code SMPL_min(SMPL_max(round(y * 32767), -32768), 32767)}.
     *
     * @param sample the float sample, nominally in {@code [-1, 1]}
     * @return the rounded and clamped {@code int16} sample
     */
    private static short toInt16(float sample) {
        int v = Math.round(sample * PCM_SCALE);
        if (v > 32767) {
            v = 32767;
        } else if (v < -32768) {
            v = -32768;
        }
        return (short) v;
    }
}
