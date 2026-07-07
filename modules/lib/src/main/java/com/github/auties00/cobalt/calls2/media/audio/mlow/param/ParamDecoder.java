package com.github.auties00.cobalt.calls2.media.audio.mlow.param;

import com.github.auties00.cobalt.calls2.media.audio.mlow.MlowTocByte;
import com.github.auties00.cobalt.calls2.media.audio.mlow.celp.PitchLagDecoder;
import com.github.auties00.cobalt.calls2.media.audio.mlow.celp.PulseDecoder;
import com.github.auties00.cobalt.calls2.media.audio.mlow.celp.ResNrgDequantizer;
import com.github.auties00.cobalt.calls2.media.audio.mlow.entropy.MlowEntropyWrapper;
import com.github.auties00.cobalt.calls2.media.audio.mlow.entropy.MlowRangeDecoder;
import com.github.auties00.cobalt.calls2.media.audio.mlow.lsf.LsfDequantizer;
import com.github.auties00.cobalt.calls2.media.audio.mlow.tables.MiscTables;
import com.github.auties00.cobalt.calls2.media.audio.mlow.tables.PulseTables;

/**
 * Per-frame low-band parameter decoder for the MLow speech codec, the orchestrating port of
 * {@code smpl_decode_lb_params} (and its {@code decode_lb_voiced} / {@code decode_lb_unvoiced} helpers) in
 * {@code smpl_param_coding.c}, plus the per-packet frame loop and conditional-coding bookkeeping of
 * {@code smpl_core_decode} ({@code smpl_core_decoder.c}).
 *
 * <p>This is the bit-exact checkpoint of the MLow decode front end. The range decoder, the entropy wrapper,
 * the cumulative-mass-function tables, and the four leaf decoders (LSF, pulses, residual energy, pitch lag)
 * are all individually correct only if they are driven in exactly the order, and with exactly the
 * conditional-coding state, that the native decoder uses. This class is that driver: per internal frame it
 * sets up nothing of its own beyond the supplied range decoder and reads the full low-band parameter set in
 * the native read order:
 * <ol>
 * <li>the voicing flag (only when the frame is coded as active voice), against the voicing CMF selected by
 * the frame number and the previous frame's voicing;</li>
 * <li>the stage-1 and per-coefficient stage-2 LSF indices, conditional on the previous frame when the
 * conditional-coding flag survives;</li>
 * <li>the LSF interpolation index, only for a multi-subframe frame coded as active voice;</li>
 * <li>the fixed-codebook excitation pulses (count, per-subframe split, positions, signs), unless the frame
 * is a SID frame in which case the pulse counts are zero;</li>
 * <li>then either the voiced gain set (per-subframe adaptive-codebook gain index and, for subframes with
 * pulses, the fixed-codebook gain index) followed by the pitch lags, or the unvoiced residual-energy set
 * (frame energy index, shape index, per-subframe fixed-codebook gain offsets).</li>
 * </ol>
 *
 * <p>Read order is load-bearing: the range coder is a single serial stream, so reading any symbol out of
 * order, or against the wrong CMF, desynchronizes every later symbol in the frame and every later frame in
 * the packet. The conditional-coding flag is threaded exactly as the native code threads it: it enters each
 * frame as {@code false} for the first frame of a packet and {@code true} for later frames, is then ANDed
 * with whether the current voicing equals the previous frame's voicing, and when it ends up {@code false}
 * the per-frame predictors (previous adaptive-codebook index, previous fixed-codebook index, previous
 * residual-energy index, and the pitch-lag carry) are reset.
 *
 * <p>This decoder is stateful across the frames of a packet and across packets in one continuous decode
 * session, mirroring the {@code ParamsDecoder} embedded in the native core decoder. Construct one decoder
 * per logical stream and feed it every packet in order. {@link #reset()} returns it to the freshly
 * constructed state.
 *
 * <p>Scope is the SMPL 16 kHz, 60 ms, mono low-band path. The high-band parameter decode
 * ({@code smpl_decode_hb_params}) is deliberately not invoked here; at 16 kHz the high band is absent. A
 * caller that passes a TOC announcing a sample rate above 16 kHz has supplied an out-of-scope packet, which
 * {@link #decodeFrame} rejects.
 *
 * @implNote This implementation merges three native functions into one driver: the top-level
 * {@code smpl_decode_lb_params}, its {@code decode_lb_voiced} gain-and-lag tail, and its
 * {@code decode_lb_unvoiced} residual-energy tail. The LSF, pulse, residual-energy, and pitch-lag leaf
 * decoders are delegated to {@link LsfDequantizer}, {@link PulseDecoder}, {@link ResNrgDequantizer}, and
 * {@link PitchLagDecoder} respectively, all of which reproduce their native counterparts statement for
 * statement. The voiced adaptive-codebook and fixed-codebook gain decode lives here rather than in a leaf
 * decoder because it both consumes and produces the conditional-coding predictors ({@code prev_acb_idx},
 * {@code prev_fcb_idx}) and computes the {@code mean_acbg_Q14} class that selects the pitch-lag delta model;
 * it is reproduced exactly, including the {@code (prev_acb_idx + 1) * (SMPL_ACBG_N + 1)} conditional CMF
 * row selection, the {@code mean += cb[acb*M] + 2*cb[acb*M+1]} fixed-point accumulation, the
 * absolute-versus-delta fixed-codebook gain branch, and the integer division
 * {@code mean_acbg_Q14 /= num_subfr}. The conditional-coding reset is split across this class
 * ({@code prev_acb_idx}, {@code prev_fcb_idx}, {@code prev_nrgres_idx}) and {@link PitchLagDecoder#reset()}
 * (the lag carry), matching the single native reset block. The {@code prev_voiced} predictor is held here
 * and seeded to the unvoiced state, mirroring the {@code calloc}/{@code memset}-zeroed
 * {@code ParamsDecoder.prev_voiced} of the native core decoder.
 */
public final class ParamDecoder {
    /**
     * Number of taps per adaptive-codebook gain codebook vector, the native {@code SMPL_ACBG_M}.
     *
     * <p>Each codebook entry holds a leading and a trailing tap gain, which the mean-gain accumulation
     * reads as {@code cb[acb * M]} and {@code cb[acb * M + 1]}.
     */
    private static final int ACBG_M = MiscTables.ACBG_M;

    /**
     * Number of symbols in the voiced fixed-codebook-gain absolute CMF, the native {@code SMPL_FCBG_V_N}.
     *
     * <p>The delta CMF for the conditional fixed-codebook gain is windowed around index
     * {@code SMPL_FCBG_V_N - 1}, the zero-delta position.
     */
    private static final int FCBG_V_N = 34;

    /**
     * Linear-prediction order of the MLow short-term filter, the native {@code SMPL_LPC_ORDER}; the LSF
     * vector length and the count of stage-2 indices.
     */
    private static final int LPC_ORDER = 16;

    /**
     * Number of pitch subframes a 20 ms frame's lags span, the native {@code SMPL_PITCH_NUM_SUBFRAMES}.
     */
    private static final int PITCH_NUM_SUBFRAMES = 8;

    /**
     * The shared prebuilt pulse-coding CMF families, the native {@code smpl_get_pulse_data()} product.
     */
    private final PulseTables.Tables pulseTables;

    /**
     * The LSF inverse vector quantizer, threaded with the previous frame's reconstructed LSF vector.
     */
    private final LsfDequantizer lsfDequantizer;

    /**
     * The residual-energy and unvoiced fixed-codebook gain dequantizer.
     */
    private final ResNrgDequantizer resNrgDequantizer;

    /**
     * The pitch-lag decoder, which carries its own previous-lag-block and previous-lag-index predictors.
     */
    private final PitchLagDecoder pitchLagDecoder;

    /**
     * The previous frame's reconstructed LSF vector, the native {@code dec_state->lsfq_prev}; the LSF
     * dequantizer reads it when the conditional stage-1 centroid is selected and it is overwritten with
     * each frame's reconstruction.
     */
    private final float[] previousLsf;

    /**
     * The reusable range decoder, re-pointed at each packet's range-coded body by
     * {@link MlowRangeDecoder#reset(byte[], int, int)} so no per-packet decoder or payload copy is
     * allocated. {@code null} until the first packet primes it. It carries no cross-packet state: every
     * {@link #decodePacket} call fully re-primes it before reading.
     */
    private MlowRangeDecoder rangeDecoder;

    /**
     * The previous adaptive-codebook gain index, the native {@code ParamsDecoder.prev_acb_idx}; {@code -1}
     * when conditional coding was reset.
     */
    private int prevAcbIdx;

    /**
     * The previous fixed-codebook gain index, the native {@code ParamsDecoder.prev_fcb_idx}; {@code -1}
     * when conditional coding was reset, which selects the absolute fixed-codebook gain decode.
     */
    private int prevFcbIdx;

    /**
     * The previous residual-energy index predictor, the native {@code ParamsDecoder.prev_nrgres_idx};
     * reset to {@code -1} alongside the other predictors. The low-band decode path resets but never reads
     * it, matching the native code, which keeps it only for symmetry with the encoder.
     */
    private int prevNrgresIdx;

    /**
     * The previous frame's voicing flag, the native {@code ParamsDecoder.prev_voiced}; seeded to the
     * unvoiced state and updated after each frame's voicing decode.
     */
    private int prevVoiced;

    /**
     * Constructs a low-band parameter decoder over the shared MLow decode tables and clears its state.
     *
     * <p>The pulse, LSF, residual-energy, and pitch-lag leaf decoders are created over their shared table
     * instances; the cross-frame predictors start in the reset state, ready to decode the first frame of
     * the first packet.
     */
    public ParamDecoder() {
        this.pulseTables = PulseTables.build();
        this.lsfDequantizer = new LsfDequantizer();
        this.resNrgDequantizer = new ResNrgDequantizer();
        this.pitchLagDecoder = new PitchLagDecoder();
        this.previousLsf = new float[LPC_ORDER];
        reset();
    }

    /**
     * Returns this decoder to its freshly constructed state, the equivalent of re-running
     * {@code smpl_core_decode_init} on the embedded {@code ParamsDecoder}.
     *
     * <p>Clears the conditional-coding predictors and the pitch-lag carry and zeroes the previous-frame LSF
     * vector and voicing flag. Call this between independent decode sessions; do not call it between the
     * packets of one continuous stream, which must thread state.
     */
    public void reset() {
        this.prevAcbIdx = -1;
        this.prevFcbIdx = -1;
        this.prevNrgresIdx = -1;
        this.prevVoiced = 0;
        this.pitchLagDecoder.reset();
        java.util.Arrays.fill(previousLsf, 0.0f);
    }

    /**
     * The decoded low-band parameters of one internal frame, the decode-relevant subset of the native
     * {@code LbQuantParams} together with the reconstructed LSF vector and the frame's quantized
     * adaptive-codebook gain indices.
     *
     * <p>{@code lsfIndices} and {@code lsf} are always present. The voiced fields ({@code acbgIdx},
     * {@code laginds}) are populated only when {@link #voiced()} is {@code true} and the frame is coded as
     * active voice; the unvoiced fields ({@code nrgresFrameQi}, {@code nrgresShapeQi}, {@code nrgresDbqQ14})
     * are populated otherwise. {@code fcbgIdx} is populated in both cases (voiced fixed-codebook gain or
     * unvoiced fixed-codebook gain offset), with entries for subframes without pulses left at zero.
     *
     * @param voiced            {@code true} when the frame was decoded as voiced
     * @param lsfIndices        the {@code SMPL_LPC_ORDER + 1} LSF indices: stage-1 centroid then the
     *                          stage-2 levels
     * @param lsf               the reconstructed line spectral frequencies, {@code SMPL_LPC_ORDER} entries
     * @param lsfInterpolIdx    the LSF interpolation index, or {@code 0} when not coded (single subframe or
     *                          not coded as active voice)
     * @param nPulses           the total decoded pulse count for the frame
     * @param nPositions        the number of distinct decoded pulse positions
     * @param positions         the absolute sample position of each pulse, valid for the first
     *                          {@code nPositions} entries
     * @param posPulses         the signed stacked pulse magnitude at each position, valid for the first
     *                          {@code nPositions} entries
     * @param sfPulses          the per-subframe pulse count
     * @param acbgIdx           the per-subframe adaptive-codebook gain index, valid only for a voiced frame
     * @param fcbgIdx           the per-subframe fixed-codebook gain index (voiced) or gain offset index
     *                          (unvoiced)
     * @param laginds           the per-pitch-subframe integer pitch lags, valid only for a voiced frame
     * @param nrgresFrameQi     the frame-level residual-energy index, valid only for an unvoiced frame
     * @param nrgresShapeQi     the residual-energy shape index, valid only for an unvoiced multi-subframe
     *                          frame
     * @param nrgresDbqQ14      the per-subframe reconstructed residual energy in Q14 decibels, valid only
     *                          for an unvoiced frame
     * @param condCoding        the conditional-coding flag in effect for this frame after the voicing AND,
     *                          the native {@code cond_coding} value the high-band decode would consume
     * @param blocksegsIx       the decoded pitch-lag block-segmentation index for a voiced frame, the native
     *                          {@code blocksegs_ix}; {@code -1} for an unvoiced frame where no lags are coded.
     *                          The synthesis path needs only {@code laginds}; this index is carried so an
     *                          encoder round-trip can reconstruct the value the serializer must re-emit
     */
    public record DecodedFrame(
            boolean voiced,
            int[] lsfIndices,
            float[] lsf,
            int lsfInterpolIdx,
            int nPulses,
            int nPositions,
            short[] positions,
            short[] posPulses,
            short[] sfPulses,
            int[] acbgIdx,
            int[] fcbgIdx,
            int[] laginds,
            int nrgresFrameQi,
            int nrgresShapeQi,
            int[] nrgresDbqQ14,
            boolean condCoding,
            int blocksegsIx) {
    }

    /**
     * Decodes every internal frame of one MLow packet, the {@code smpl_core_decode} per-frame loop
     * restricted to low-band parameter decode.
     *
     * <p>Sets up one range decoder over {@code payload} (the packet bytes after the TOC byte) and runs
     * {@link #decodeFrame} once per internal frame, threading the conditional-coding flag: {@code false}
     * for the first frame, {@code true} thereafter. A SID packet decodes only its first frame's parameters
     * (later SID frames are concealed in the native synthesis path, which is out of scope here), so this
     * method decodes only frame zero when {@link MlowTocByte#sid()} is set. The cross-frame and cross-packet
     * predictors persist on this decoder.
     *
     * <p>The range-coded body is the window {@code [offset + 1, offset + length)} of {@code packet}: the TOC
     * byte sits at {@code packet[offset]} and is skipped. The decoder reads that window directly through the
     * reused {@link #rangeDecoder}, so no stripped-payload copy is made.
     *
     * @param toc    the decoded TOC of the packet, supplying the frame count, subframe count, frame length,
     *               SID flag, low-rate flag, and coded-as-active-voice flag
     * @param packet the backing array holding the packet, with the TOC byte at {@code offset}
     * @param offset the index of the TOC byte within {@code packet}
     * @param length the length of the packet including the TOC byte
     * @return one {@link DecodedFrame} per decoded internal frame, in decode order
     * @throws IllegalArgumentException if {@code toc} announces a sample rate above 16 kHz, which is out of
     *                                  the low-band scope of this decoder
     */
    public DecodedFrame[] decodePacket(MlowTocByte toc, byte[] packet, int offset, int length) {
        if (toc.sampleRateHz() > 16000) {
            throw new IllegalArgumentException(
                    "high-band decode (fs " + toc.sampleRateHz() + " Hz) is out of low-band scope");
        }
        if (rangeDecoder == null) {
            rangeDecoder = new MlowRangeDecoder(packet, offset + 1, length - 1);
        } else {
            rangeDecoder.reset(packet, offset + 1, length - 1);
        }
        MlowRangeDecoder decoder = rangeDecoder;
        int numFrames = toc.numFrames();
        int decodedFrames = toc.sid() ? 1 : numFrames;
        DecodedFrame[] out = new DecodedFrame[decodedFrames];
        boolean condCoding = false;
        for (int frame = 0; frame < decodedFrames; frame++) {
            out[frame] = decodeFrame(decoder, toc, frame, condCoding);
            condCoding = true;
        }
        return out;
    }

    /**
     * Decodes the full low-band parameter set of one internal frame, {@code smpl_decode_lb_params}.
     *
     * <p>Reads, in the native order, the voicing flag, the LSF indices, the LSF interpolation index, the
     * excitation pulses, and then the voiced or unvoiced gain set, advancing {@code decoder} past every
     * symbol. The {@code condCoding} argument is the flag on entry ({@code false} for the first frame of a
     * packet, {@code true} otherwise); it is ANDed with whether the voicing matches the previous frame, and
     * when the result is {@code false} the cross-frame predictors are reset before the LSF and gain decode
     * read them.
     *
     * @param decoder    the range decoder positioned at the frame's first symbol
     * @param toc        the decoded TOC supplying the frame and subframe counts, frame length, SID flag,
     *                   low-rate flag, and coded-as-active-voice flag
     * @param frameNum   the zero-based index of this frame within the packet, the native {@code frame_num}
     * @param condCoding the conditional-coding flag on entry to this frame
     * @return the decoded parameters of the frame
     */
    public DecodedFrame decodeFrame(MlowRangeDecoder decoder, MlowTocByte toc, int frameNum, boolean condCoding) {
        int framelen = toc.frameLength16();
        int numSubfr = toc.numSubframes();
        boolean codedAsActiveVoice = toc.codedAsActiveVoice();
        boolean lowRate = toc.lowRate();
        boolean sid = toc.sid();

        // Voicing
        int voiced;
        if (codedAsActiveVoice) {
            int[] cmf = MiscTables.VUV_CMFS[frameNum == 0 ? 0 : prevVoiced == 0 ? 1 : 2];
            voiced = MlowEntropyWrapper.decodeUpdate(decoder, cmf);
        } else {
            voiced = 0;
        }

        boolean cond = condCoding && (voiced == prevVoiced);
        if (!cond) {
            prevAcbIdx = -1;
            prevFcbIdx = -1;
            prevNrgresIdx = -1;
            pitchLagDecoder.reset();
        }
        prevVoiced = voiced;

        // LSF stage-1 and stage-2 indices, and reconstructed vector
        LsfDequantizer.DecodedLsf lsf =
                lsfDequantizer.decode(decoder, voiced, lowRate ? 1 : 0, cond, previousLsf);
        System.arraycopy(lsf.lsf(), 0, previousLsf, 0, LPC_ORDER);

        // LSF interpolation index
        int lsfInterpolIdx = 0;
        if (codedAsActiveVoice && numSubfr > 1) {
            lsfInterpolIdx = MlowEntropyWrapper.decodeUpdate(decoder, MiscTables.LSF_INTERP_CMF);
        }

        // Excitation pulses
        PulseDecoder.Result pulses;
        if (!sid) {
            pulses = PulseDecoder.decode(decoder, pulseTables, framelen, numSubfr, lowRate,
                    voiced != 0, codedAsActiveVoice);
        } else {
            pulses = new PulseDecoder.Result(0, 0, new short[framelen], new short[framelen], new short[numSubfr]);
        }

        int[] acbgIdx = new int[numSubfr];
        int[] fcbgIdx = new int[numSubfr];
        int[] laginds;
        int blocksegsIx = -1;
        int nrgresFrameQi = 0;
        int nrgresShapeQi = 0;
        int[] nrgresDbqQ14;

        if (codedAsActiveVoice && voiced != 0) {
            int meanAcbgQ14 = decodeVoicedGains(decoder, pulses.sfPulses(), numSubfr, lowRate, acbgIdx, fcbgIdx);
            laginds = pitchLagDecoder.decodeLags(decoder, meanAcbgQ14);
            blocksegsIx = pitchLagDecoder.lastBlocksegsIx();
            nrgresDbqQ14 = new int[numSubfr];
        } else {
            ResNrgDequantizer.DecodeResult res =
                    resNrgDequantizer.decode(decoder, numSubfr, toShortlessIntArray(pulses.sfPulses()));
            nrgresFrameQi = res.nrgresFrameQi();
            nrgresShapeQi = res.nrgresShapeQi();
            nrgresDbqQ14 = res.nrgresDbqQ14();
            System.arraycopy(res.fcbgIdx(), 0, fcbgIdx, 0, numSubfr);
            laginds = new int[PITCH_NUM_SUBFRAMES];
        }

        return new DecodedFrame(
                voiced != 0,
                lsf.indices(),
                lsf.lsf(),
                lsfInterpolIdx,
                pulses.nPulses(),
                pulses.nPositions(),
                pulses.positions(),
                pulses.posPulses(),
                pulses.sfPulses(),
                acbgIdx,
                fcbgIdx,
                laginds,
                nrgresFrameQi,
                nrgresShapeQi,
                nrgresDbqQ14,
                cond,
                blocksegsIx);
    }

    /**
     * Decodes the voiced per-subframe gain set and computes the frame's mean quantized adaptive-codebook
     * gain, the gain block of {@code decode_lb_voiced}.
     *
     * <p>For each subframe this reads the adaptive-codebook gain index against the conditional CMF row
     * selected by {@code prevAcbIdx + 1}, accumulates the mean quantized gain from the rate-selected
     * codebook ({@code cb[acb * M] + 2 * cb[acb * M + 1]}), and, when the subframe carries pulses, reads the
     * fixed-codebook gain index either absolutely (first one of a conditional run) or as a signed delta from
     * the previous index. The accumulated mean is divided by the subframe count with native integer
     * (truncating) division so the selected pitch-lag delta model matches the encoder exactly.
     *
     * @param decoder  the range decoder positioned at the first adaptive-codebook gain symbol
     * @param sfPulses the per-subframe pulse counts from the pulse decode, read to gate the fixed-codebook
     *                 gain reads
     * @param numSubfr the number of subframes in the frame
     * @param lowRate  {@code true} for the low-rate gain codebooks and CMFs, {@code false} for high rate
     * @param acbgIdx  the per-subframe adaptive-codebook gain index output, written in place
     * @param fcbgIdx  the per-subframe fixed-codebook gain index output, written in place
     * @return the mean quantized adaptive-codebook gain in Q14, the native {@code mean_acbg_Q14}
     */
    private int decodeVoicedGains(MlowRangeDecoder decoder, short[] sfPulses, int numSubfr, boolean lowRate,
                                  int[] acbgIdx, int[] fcbgIdx) {
        long meanAcbgQ14 = 0;
        short[] acbgCbk = lowRate ? MiscTables.ACB_GAINS_LR_Q14 : MiscTables.ACB_GAINS_HR_Q14;
        for (int sf = 0; sf < numSubfr; sf++) {
            int[] cmf = lowRate ? MiscTables.acbGainsCmfLr(prevAcbIdx + 1) : MiscTables.acbGainsCmfHr(prevAcbIdx + 1);
            acbgIdx[sf] = MlowEntropyWrapper.decodeUpdate(decoder, cmf);
            prevAcbIdx = acbgIdx[sf];
            meanAcbgQ14 += acbgCbk[prevAcbIdx * ACBG_M] + 2 * acbgCbk[prevAcbIdx * ACBG_M + 1];
            if (sfPulses[sf] > 0) {
                if (prevFcbIdx == -1) {
                    fcbgIdx[sf] = MlowEntropyWrapper.decodeUpdate(decoder, MiscTables.fcbgVCmf());
                } else {
                    int minDelta = -prevFcbIdx;
                    int maxDelta = (FCBG_V_N - 1) - prevFcbIdx;
                    int delta = MlowEntropyWrapper.decodeUpdate(decoder, MiscTables.fcbgVDeltaCmf(),
                            (FCBG_V_N - 1) + minDelta, maxDelta - minDelta + 2) + minDelta;
                    fcbgIdx[sf] = prevFcbIdx + delta;
                }
                prevFcbIdx = fcbgIdx[sf];
            }
        }
        return (int) (meanAcbgQ14 / numSubfr);
    }

    /**
     * Widens a per-subframe pulse-count array to {@code int} for the residual-energy decoder, which reads
     * the per-subframe pulse counts to gate its fixed-codebook gain offset reads.
     *
     * @param sfPulses the per-subframe pulse counts as decoded by the pulse decoder
     * @return a freshly allocated {@code int} copy of {@code sfPulses}
     */
    private static int[] toShortlessIntArray(short[] sfPulses) {
        int[] out = new int[sfPulses.length];
        for (int i = 0; i < sfPulses.length; i++) {
            out[i] = sfPulses[i];
        }
        return out;
    }
}
