package com.github.auties00.cobalt.calls2.media.audio.mlow.lsf;

import com.github.auties00.cobalt.calls2.media.audio.mlow.MlowTocByte;
import com.github.auties00.cobalt.calls2.media.audio.mlow.param.ParamDecoder;

/**
 * Per-frame line-spectral-frequency to linear-prediction (LSF to LPC) driver for the MLow speech codec, the
 * port of the LSF interpolation block of {@code smpl_core_decode} in {@code smpl_core_decoder.c}.
 *
 * <p>The MLow decoder codes one line-spectral-frequency vector per 20 ms frame, but the short-term synthesis
 * filter is applied per subframe. The parameter decoder ({@link ParamDecoder}) reconstructs the per-frame
 * dequantized LSF vector and the frame's interpolation index; this class is the stage that turns that pair,
 * frame by frame, into the per-subframe stabilized linear-prediction filters the synthesis filter consumes.
 * It is the small driver that {@code smpl_core_decode} runs between {@code smpl_lsf_dequant} and the
 * excitation generation: it selects the per-subframe interpolation-factor row from the frame's
 * {@code lsf_interpol_idx}, hands the dequantized LSF vector and that row to {@link LpcInterpolator}, and
 * threads the previous-frame LSF state across frames.
 *
 * <p>For a regular (non-SID) frame the interpolation-factor row is row {@code lsf_interpol_idx} of the
 * subframe-count-specific factor table, exactly the native
 * {@code p_lsf_interpol + lsf_interpol_idx * num_subframes} pointer arithmetic. For a silence-insertion
 * descriptor (SID) frame the single comfort-noise (DTX) factor row is used instead, regardless of the index,
 * mirroring the {@code p_lsf_dtx_interpol} branch. The factor tables are the native {@code smpl_lsf_interpol_1}
 * / {@code smpl_lsf_interpol_2} / {@code smpl_lsf_interpol_4} and their {@code _dtx} counterparts.
 *
 * <p>The interpolation itself carries state: the previous frame's LSF vector is held inside the wrapped
 * {@link LpcInterpolator} (the native {@code dec_state->lsf_prev}) and updated after every frame. This driver
 * therefore must be fed every frame of one continuous decode session in order; construct one driver per
 * logical stream. {@link #reset()} returns both this driver and its interpolator to the freshly constructed
 * state, the equivalent of re-running {@code smpl_core_decode_init} on the LSF-interpolation state. It does
 * not reset {@link ParamDecoder}; a caller threading a whole decode resets both in lock step.
 *
 * <p>The integer NLSF2A conversion inside the interpolation is bit-exact against the native decoder (it runs
 * the integer fixed-point {@code silk_NLSF2A_32} via {@link NlsfBridge}); the float interpolation and the
 * bandwidth-expansion stabilization round identically to the native single-precision arithmetic, so the
 * produced per-subframe LPC filters and interpolated LSF vectors match the native decoder's to within
 * IEEE-754 rounding (validated to a relative epsilon of {@code 1e-4} against the trace oracle's
 * {@code features_lpc.f32}).
 *
 * <p>Scope is the SMPL 16 kHz, 60 ms, mono low-band decode path with {@code SMPL_LPC_ORDER == 16}. The
 * clean-stream PLC adaptations the native {@code smpl_core_decode} performs around this block
 * ({@code smpl_plc_adapt_lsf}, {@code smpl_plc_bwe_recover}) are no-ops on a continuous loss-free decode and
 * are not part of this driver; the high-band reuse of {@code smpl_lpc_interpol} is out of scope. This type is
 * stateful per stream and is not thread-safe.
 *
 * @implNote This implementation factors the LSF-interpolation block of {@code smpl_core_decode} out of the
 * surrounding excitation and synthesis loop. The subframe-count-to-table mapping reproduces the native
 * {@code if (num_subframes == 4) ... else if (num_subframes == 2) ... else} selection, and the row offset
 * reproduces the {@code lsf_interpol_idx * num_subframes} stride. The factor-table constants are copied
 * verbatim from {@code smpl_tables.c}. The actual blend, NLSF2A conversion, and stabilization are delegated
 * unchanged to {@link LpcInterpolator#interpolate(float[], float[])}, which holds the carried
 * previous-frame LSF state.
 */
public final class SubframeLpc {
    /**
     * Per-subframe interpolation factors for a single-subframe frame, the native {@code smpl_lsf_interpol_1}.
     *
     * <p>A 10 ms low-band frame has one subframe; there is no interpolation index (the parameter decoder
     * never codes one for a single-subframe frame, so {@code lsf_interpol_idx} is always {@code 0}), so the
     * single factor {@code 0.95f} is always used. Held as a flat one-row array, like its
     * {@link #INTERPOL_DTX_1} comfort-noise counterpart.
     */
    private static final float[] INTERPOL_1 = {0.95f};

    /**
     * Per-subframe interpolation factors for a two-subframe frame, the native {@code smpl_lsf_interpol_2}.
     *
     * <p>Indexed by {@code lsf_interpol_idx}: row {@code 0} is {@code {0.75f, 1.0f}} and row {@code 1} is
     * {@code {0.4f, 0.95f}}. The two-subframe layout is the low-rate 60 ms path.
     */
    private static final float[][] INTERPOL_2 = {
            {0.75f, 1.0f},
            {0.4f, 0.95f}
    };

    /**
     * Per-subframe interpolation factors for a four-subframe frame, the native {@code smpl_lsf_interpol_4}.
     *
     * <p>Indexed by {@code lsf_interpol_idx}: row {@code 0} is {@code {0.55f, 0.88f, 1.0f, 1.0f}} and row
     * {@code 1} is {@code {0.3f, 0.65f, 0.95f, 1.0f}}. The four-subframe layout is the high-rate 60 ms path,
     * the common case of the SMPL scope. A trailing {@code 1.0f} that repeats the prior factor triggers the
     * filter-reuse fast path in {@link LpcInterpolator}.
     */
    private static final float[][] INTERPOL_4 = {
            {0.55f, 0.88f, 1.0f, 1.0f},
            {0.3f, 0.65f, 0.95f, 1.0f}
    };

    /**
     * Comfort-noise (DTX) per-subframe interpolation factors for a single-subframe frame, the native
     * {@code smpl_lsf_interpol_dtx_1}.
     *
     * <p>Used for a single-subframe SID frame in place of {@link #INTERPOL_1}; the comfort-noise model
     * interpolates more slowly toward the new spectrum, hence the smaller {@code 0.25f} factor.
     */
    private static final float[] INTERPOL_DTX_1 = {0.25f};

    /**
     * Comfort-noise (DTX) per-subframe interpolation factors for a two-subframe frame, the native
     * {@code smpl_lsf_interpol_dtx_2}.
     *
     * <p>A single row {@code {0.15f, 0.3f}} used for a two-subframe SID frame regardless of the coded
     * interpolation index.
     */
    private static final float[] INTERPOL_DTX_2 = {0.15f, 0.3f};

    /**
     * Comfort-noise (DTX) per-subframe interpolation factors for a four-subframe frame, the native
     * {@code smpl_lsf_interpol_dtx_4}.
     *
     * <p>A single row {@code {0.1f, 0.157f, 0.2f, 0.3f}} used for a four-subframe SID frame regardless of the
     * coded interpolation index.
     */
    private static final float[] INTERPOL_DTX_4 = {0.1f, 0.157f, 0.2f, 0.3f};

    /**
     * The wrapped per-subframe interpolator and LPC stabilizer, holding the carried previous-frame LSF state.
     */
    private final LpcInterpolator interpolator;

    /**
     * Constructs an LSF-to-LPC driver with a freshly constructed interpolator.
     *
     * <p>The wrapped {@link LpcInterpolator} starts with a zeroed previous-frame LSF vector, so the first
     * frame fed to {@link #process(ParamDecoder.DecodedFrame, MlowTocByte)} is treated as the reset frame and
     * interpolates against itself.
     */
    public SubframeLpc() {
        this.interpolator = new LpcInterpolator();
    }

    /**
     * Returns this driver to its freshly constructed state.
     *
     * <p>Resets the wrapped {@link LpcInterpolator}, zeroing its carried previous-frame LSF vector so the next
     * frame is treated as the reset frame. Call this between independent decode sessions; do not call it
     * between the frames of one continuous stream, which must thread the previous-frame vector.
     */
    public void reset() {
        interpolator.reset();
    }

    /**
     * Converts one decoded frame's dequantized LSF vector into per-subframe stabilized LPC filters, the
     * LSF-interpolation block of {@code smpl_core_decode}.
     *
     * <p>Selects the per-subframe interpolation-factor row from the frame's
     * {@link ParamDecoder.DecodedFrame#lsfInterpolIdx()} (or the comfort-noise row when {@code toc} announces
     * a SID frame), then drives {@link LpcInterpolator#interpolate(float[], float[])} with the frame's
     * dequantized LSF vector and that row, advancing the carried previous-frame state. The subframe count is
     * taken from {@code toc} so the row stride matches the native pointer arithmetic exactly.
     *
     * @param frame the decoded frame whose dequantized LSF vector ({@link ParamDecoder.DecodedFrame#lsf()})
     *              and interpolation index ({@link ParamDecoder.DecodedFrame#lsfInterpolIdx()}) drive the
     *              interpolation
     * @param toc   the decoded TOC of the packet, supplying the subframe count, low-rate flag, and SID flag
     * @return the per-subframe stabilized LPC filters and interpolated LSF vectors
     */
    public LpcInterpolator.InterpolatedFrame process(ParamDecoder.DecodedFrame frame, MlowTocByte toc) {
        int numSubframes = toc.numSubframes();
        float[] interpol = interpolRow(numSubframes, frame.lsfInterpolIdx(), toc.sid());
        return interpolator.interpolate(frame.lsf(), interpol);
    }

    /**
     * Selects the per-subframe interpolation-factor row for one frame, the native
     * {@code p_lsf_interpol + lsf_interpol_idx * num_subframes} (or {@code p_lsf_dtx_interpol}) selection.
     *
     * <p>For a regular frame the row is row {@code lsfInterpolIdx} of the factor table chosen by
     * {@code numSubframes}; for a SID frame the single comfort-noise row for that subframe count is returned
     * regardless of the index. The returned array's length is {@code numSubframes}.
     *
     * @param numSubframes   the subframe count of the frame; 1, 2, or 4
     * @param lsfInterpolIdx the coded interpolation index, the native {@code lsf_interpol_idx}; ignored for a
     *                       SID frame
     * @param sid            {@code true} for a silence-insertion-descriptor frame, selecting the comfort-noise
     *                       factor row
     * @return the per-subframe interpolation-factor row of length {@code numSubframes}
     * @throws IllegalArgumentException if {@code numSubframes} is not 1, 2, or 4
     */
    private static float[] interpolRow(int numSubframes, int lsfInterpolIdx, boolean sid) {
        return switch (numSubframes) {
            case 1 -> sid ? INTERPOL_DTX_1 : INTERPOL_1;
            case 2 -> sid ? INTERPOL_DTX_2 : INTERPOL_2[lsfInterpolIdx];
            case 4 -> sid ? INTERPOL_DTX_4 : INTERPOL_4[lsfInterpolIdx];
            default -> throw new IllegalArgumentException("unsupported subframe count " + numSubframes);
        };
    }
}
