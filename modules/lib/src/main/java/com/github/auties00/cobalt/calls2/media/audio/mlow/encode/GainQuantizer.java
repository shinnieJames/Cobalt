package com.github.auties00.cobalt.calls2.media.audio.mlow.encode;

import com.github.auties00.cobalt.calls2.media.audio.mlow.tables.EncoderTables;
import com.github.auties00.cobalt.calls2.media.audio.mlow.tables.MiscTables;

/**
 * Joint adaptive-codebook and fixed-codebook gain quantizer for one analysis-by-synthesis (AbS) subframe of the
 * MLow speech codec, the port of {@code calc_gains_v} and {@code quant_gain_uv} in {@code smpl_celp.c}.
 *
 * <p>After the closed-loop adaptive-codebook (ACB) search and the fixed-codebook (FCB) pulse search have run,
 * the encoder must commit the final quantized gains. For a voiced subframe this is a joint rate-distortion
 * decision: {@link #quantizeVoiced} walks the full adaptive-codebook gain codebook crossed with a small window
 * of fixed-codebook gain levels around the open-loop estimate, scoring each candidate by its
 * perceptually-weighted reconstruction error scaled by the entropy-coder bit cost of the pair, and keeps the
 * cheapest. It is the inverse of the {@code M3} residual-energy gain decode: the decoder dequantizes the
 * indices this routine selects. For an unvoiced subframe there is no adaptive codebook and the fixed-codebook
 * gain is a single scalar quantization, {@link #quantizeUnvoiced}.
 *
 * <p>{@link #quantizeVoiced} re-decides the adaptive-codebook gain index here, jointly with the fixed-codebook
 * gain, overriding the index {@link AcbSearch} chose with the fixed-codebook excitation excluded. The native
 * {@code calc_gains_v} does the same: it rebuilds the {@code (SMPL_ACBG_M + 1)}-dimensional cross-correlation
 * system that adds the just-searched fixed-codebook excitation as an extra basis vector, then minimizes the
 * joint weighted error. The adaptive-codebook index this routine returns is the one that goes into the
 * bitstream; the one {@link AcbSearch} returned was only used to form the fixed-codebook target.
 *
 * <p>The cost tables are entropy-coder self-information exponentiated into multiplicative rate-distortion
 * penalties ({@code pow(2, bits * mu)}), supplied by {@link EncoderTables#gainCosts()}; the adaptive-codebook
 * gain codebooks and fixed-codebook voiced gain table come from {@link MiscTables} and a locally built copy of
 * {@code fcbgains_v}. The conditional cost rows are selected by the previous subframe's committed indices
 * (the native {@code prev_acb_idx} / {@code prev_fcb_idx}), which the AbS loop threads in through
 * {@link #quantizeVoiced}; a previous index of {@code -1} selects the unconditional first-symbol context.
 *
 * <p>Scope is the SMPL 16 kHz / 60 ms / mono low-band path. The rate-distortion mu {@code SMPL_G_ACB_RD_MU} is
 * the positive build constant, so the joint search branch is always taken (the {@code mu <= 0} scalar-only
 * branch of {@code calc_gains_v} is unreachable and is not ported). All accumulations are single precision to
 * mirror the native arithmetic. This type is stateless and thread-safe; one instance may be shared, or the
 * static entry points called directly.
 *
 * @implNote This implementation reproduces the {@code calc_gains_v} two-step fixed-codebook gain window
 * exactly: {@code first_gain_idx} is {@code floor((gain_db - min) / step) - (N_GAIN_STEPS - 1) / 2} clamped to
 * {@code [0, max_gain_idx - 1]}, and the window is the {@code N_GAIN_STEPS == 2} consecutive levels from there.
 * The joint error is the quadratic form {@code werr_in + x' Phi x - 2 d' x} over the three-vector
 * {@code x = (acbGain0, acbGain1, fcbGain)} built by {@code smpl_wnrg3}, scaled by the product of the
 * adaptive-codebook context cost and the fixed-codebook (absolute or delta) context cost. The voiced
 * fixed-codebook gain table is recomputed by {@code (float) Math.pow(10.0, 0.05 * db)} as in
 * {@link com.github.auties00.cobalt.calls2.media.audio.mlow.celp.CelpSynthesizer}; it agrees with the native
 * {@code powf} table within the float envelope and feeds only the rate-distortion comparison, never an
 * entropy-coder symbol. The delta-cost index uses the native offset
 * {@code floor((min - max) / step)} so a previous-index-minus-candidate delta maps to the correct CMF row.
 */
public final class GainQuantizer {
    /**
     * Number of adaptive-codebook gain taps, the native {@code SMPL_ACBG_M}.
     */
    private static final int ACBG_M = 2;

    /**
     * Number of adaptive-codebook gain codebook entries, the native {@code SMPL_ACBG_N}.
     */
    private static final int ACBG_N = 16;

    /**
     * Number of voiced fixed-codebook absolute gain levels, the native {@code SMPL_FCBG_V_N}.
     */
    private static final int FCBG_V_N = 34;

    /**
     * Index span of the unvoiced fixed-codebook gain table, the native {@code SMPL_UV_GAIN_IDX_LEN}; the table
     * has {@code SMPL_UV_GAIN_IDX_LEN + 1} entries.
     */
    private static final int UV_GAIN_IDX_LEN = 90;

    /**
     * Minimum voiced fixed-codebook gain in decibels, the native {@code SMPL_V_GAIN_MIN_DB}.
     */
    private static final float V_GAIN_MIN_DB = -100.0f;

    /**
     * Maximum voiced fixed-codebook gain in decibels, the native {@code SMPL_V_GAIN_MAX_DB}.
     */
    private static final float V_GAIN_MAX_DB = 0.0f;

    /**
     * Voiced fixed-codebook gain quantization step in decibels, the native {@code SMPL_V_GAIN_STEP_DB}.
     */
    private static final float V_GAIN_STEP_DB = 3.0f;

    /**
     * Minimum unvoiced fixed-codebook gain in decibels, the native {@code SMPL_UV_GAIN_MIN_DB}.
     */
    private static final float UV_GAIN_MIN_DB = -90.0f;

    /**
     * Maximum unvoiced fixed-codebook gain in decibels, the native {@code SMPL_UV_GAIN_MAX_DB}.
     */
    private static final float UV_GAIN_MAX_DB = 0.0f;

    /**
     * Unvoiced fixed-codebook gain quantization step in decibels, the native {@code SMPL_UV_GAIN_STEP_DB}.
     */
    private static final float UV_GAIN_STEP_DB = 1.0f;

    /**
     * Number of fixed-codebook gain levels evaluated around the open-loop estimate, the native
     * {@code N_GAIN_STEPS}.
     */
    private static final int N_GAIN_STEPS = 2;

    /**
     * The Q14 scale {@code 1 / 2^14} dequantizing the adaptive-codebook gain codebook entries.
     */
    private static final float Q14_SCALE = 1.0f / (1 << 14);

    /**
     * The voiced fixed-codebook gain table, the native {@code CelpTables.fcbgains_v}; entry {@code ix} is
     * {@code 10^(0.05 * (ix * SMPL_V_GAIN_STEP_DB + SMPL_V_GAIN_MIN_DB))}.
     */
    private final float[] fcbgainsV;

    /**
     * The unvoiced fixed-codebook gain table, the native {@code CelpTables.fcbgains_uv}; entry {@code ix} is
     * {@code 10^(0.05 * (ix * SMPL_UV_GAIN_STEP_DB + SMPL_UV_GAIN_MIN_DB))}.
     */
    private final float[] fcbgainsUv;

    /**
     * The gain inverse-probability cost tables, the native {@code CelpTables} gain-cost fields.
     */
    private final EncoderTables.GainCosts gainCosts;

    /**
     * The quantized voiced gain decision for one subframe, the {@code acb_idx} and {@code gain_idx} entries
     * {@code calc_gains_v} writes.
     *
     * @param acbIdx  the chosen adaptive-codebook gain index, the bitstream {@code acb_idx}; overrides the
     *                index {@link AcbSearch} returned
     * @param fcbIdx  the chosen voiced fixed-codebook gain index, the bitstream {@code gain_idx}
     * @param fcbGain the dequantized fixed-codebook gain {@code fcbgains_v[fcbIdx]} the caller scales the
     *                fixed-codebook excitation by
     */
    public record VoicedGains(int acbIdx, int fcbIdx, float fcbGain) {
    }

    /**
     * Constructs a gain quantizer with freshly computed fixed-codebook gain tables and the shared gain cost
     * tables.
     *
     * <p>The voiced and unvoiced gain tables are computed once; the inverse-probability cost tables are the
     * process-wide cached {@link EncoderTables#gainCosts()}.
     */
    public GainQuantizer() {
        this.fcbgainsV = buildVoicedGains();
        this.fcbgainsUv = buildUnvoicedGains();
        this.gainCosts = EncoderTables.gainCosts();
    }

    /**
     * Jointly quantizes the adaptive-codebook and fixed-codebook gains of a voiced subframe,
     * {@code calc_gains_v}.
     *
     * <p>Forms the {@code (SMPL_ACBG_M + 1)}-dimensional weighted cross-correlation system that augments the
     * adaptive-codebook basis with the searched fixed-codebook excitation, derives a two-level window of
     * fixed-codebook gain indices around the open-loop gain estimate, and over the full adaptive-codebook gain
     * codebook crossed with that window keeps the index pair whose weighted error scaled by the entropy-coder
     * cost is smallest. The returned adaptive-codebook index replaces the one {@link AcbSearch} produced.
     *
     * @param acbg              the adaptive-codebook search state holding {@code werr_in}, the weighted
     *                          adaptive-codebook auto-correlation {@code Phi_acb}, the target cross-correlation
     *                          {@code d_acb_lpc}, and the weighted basis {@code acb_basis_phi}
     * @param fcbWnrg           the weighted energy of the fixed-codebook excitation at the search optimum, the
     *                          native {@code fcb_wnrg}
     * @param gainFromSearch    the open-loop fixed-codebook gain estimate from the pulse search, the native
     *                          {@code gain_from_search}
     * @param excFcb            the fixed-codebook excitation of this subframe (post pitch-sharpening on the
     *                          low-rate path), {@code fcbSubfrlen} entries
     * @param dLpc              the perceptually-weighted target cross-correlation, {@code fcbSubfrlen} entries
     * @param fcbSubfrlen       the subframe length in samples, the native {@code fcb_subfrlen}
     * @param lowRate           {@code true} selects the low-rate adaptive-codebook codebook and cost rows
     * @param prevAcbIdx        the previous subframe's committed adaptive-codebook index, or {@code -1} for the
     *                          unconditional context
     * @param prevFcbIdx        the previous subframe's committed fixed-codebook index, or {@code -1} for the
     *                          unconditional context
     * @return the chosen adaptive-codebook index, fixed-codebook index, and dequantized fixed-codebook gain
     */
    public VoicedGains quantizeVoiced(AcbSearch.AcbParams acbg, float fcbWnrg, float gainFromSearch,
                                      float[] excFcb, float[] dLpc, int fcbSubfrlen, boolean lowRate,
                                      int prevAcbIdx, int prevFcbIdx) {
        float fcbgain = Math.max(gainFromSearch, 0.0f);
        float gainDb = 20.0f * log10f(fcbgain + 1.0e-16f);
        gainDb = Math.min(Math.max(gainDb, V_GAIN_MIN_DB), V_GAIN_MAX_DB);
        int maxGainIdx = Math.round((V_GAIN_MAX_DB - V_GAIN_MIN_DB) / V_GAIN_STEP_DB);

        float[] acbBasisPhi = acbg.acbBasisPhi();
        float[] acbFcb = new float[ACBG_M];
        for (int i = 0; i < ACBG_M; i++) {
            acbFcb[i] = dotProd(acbBasisPhi, i * fcbSubfrlen, excFcb, 0, fcbSubfrlen);
        }
        // Phi_all is the symmetric (ACBG_M + 1) system: top-left is Phi_acb, the border is acb_fcb, the
        // bottom-right corner is fcb_wnrg. Held row-major flat to feed smpl_wnrg3.
        float[] phiAll = new float[(ACBG_M + 1) * (ACBG_M + 1)];
        float[] phiAcb = acbg.phiAcb();
        for (int i = 0; i < ACBG_M; i++) {
            for (int j = 0; j < ACBG_M; j++) {
                phiAll[i * (ACBG_M + 1) + j] = phiAcb[i * ACBG_M + j];
            }
        }
        for (int i = 0; i < ACBG_M; i++) {
            phiAll[i * (ACBG_M + 1) + ACBG_M] = acbFcb[i];
            phiAll[ACBG_M * (ACBG_M + 1) + i] = acbFcb[i];
        }
        phiAll[ACBG_M * (ACBG_M + 1) + ACBG_M] = fcbWnrg;
        float[] dAll = new float[ACBG_M + 1];
        float[] dAcbLpc = acbg.dAcbLpc();
        dAll[0] = dAcbLpc[0];
        dAll[1] = dAcbLpc[1];
        dAll[ACBG_M] = dotProd(dLpc, 0, excFcb, 0, fcbSubfrlen);

        int[] gainIdxs = new int[N_GAIN_STEPS];
        float[] fcbgains = new float[N_GAIN_STEPS];
        float[] fcbgInvProb = new float[N_GAIN_STEPS];
        int firstGainIdx = Math.max((int) Math.floor((gainDb - V_GAIN_MIN_DB) / V_GAIN_STEP_DB) - (N_GAIN_STEPS - 1) / 2, 0);
        firstGainIdx = Math.min(firstGainIdx, maxGainIdx - 1);
        int offset = (int) Math.floor((V_GAIN_MIN_DB - V_GAIN_MAX_DB) / V_GAIN_STEP_DB);
        for (int i = 0; i < N_GAIN_STEPS; i++) {
            gainIdxs[i] = firstGainIdx + i;
            fcbgains[i] = fcbgainsV[gainIdxs[i]];
            if (prevFcbIdx == -1) {
                fcbgInvProb[i] = gainCosts.fcbgVInvProb()[gainIdxs[i]];
            } else {
                int delta = prevFcbIdx - gainIdxs[i];
                int cmfIdx = delta - offset;
                fcbgInvProb[i] = gainCosts.fcbgVDeltaInvProb()[cmfIdx];
            }
        }

        float bestRd = 1e30f;
        int bestAcbgIdx = 0;
        int bestFcbgIdx = 0;
        int transitionIdx = prevAcbIdx == -1 ? 0 : (prevAcbIdx + 1);
        short[] cbAcbgains = lowRate ? MiscTables.ACB_GAINS_LR_Q14 : MiscTables.ACB_GAINS_HR_Q14;
        float[] acbgInvProb = lowRate ? gainCosts.acbgInvProbLr()[transitionIdx] : gainCosts.acbgInvProbHr()[transitionIdx];
        float[] gains = new float[ACBG_M + 1];
        for (int n = 0; n < ACBG_N; n++) {
            for (int m = 0; m < ACBG_M; m++) {
                gains[m] = cbAcbgains[n * ACBG_M + m] * Q14_SCALE;
            }
            for (int i = 0; i < N_GAIN_STEPS; i++) {
                gains[ACBG_M] = fcbgains[i];
                float werrOut = acbg.werrIn() + wnrg3(phiAll, gains)
                        - 2.0f * (dAll[0] * gains[0] + dAll[1] * gains[1] + dAll[2] * gains[2]);
                float rd = werrOut * fcbgInvProb[i] * acbgInvProb[n];
                if (rd < bestRd) {
                    bestRd = rd;
                    bestAcbgIdx = n;
                    bestFcbgIdx = gainIdxs[i];
                }
            }
        }

        int fcbIdx = Math.min(Math.max(bestFcbgIdx, 0), maxGainIdx);
        return new VoicedGains(bestAcbgIdx, fcbIdx, fcbgainsV[fcbIdx]);
    }

    /**
     * Scalar-quantizes the fixed-codebook gain of an unvoiced subframe, {@code quant_gain_uv}.
     *
     * <p>Converts the search gain to decibels, clamps it to the unvoiced gain range, and rounds to the nearest
     * quantizer level.
     *
     * @param gainFromSearch the open-loop fixed-codebook gain estimate from the pulse search
     * @return the unvoiced fixed-codebook gain index in {@code [0, SMPL_UV_GAIN_IDX_LEN]}
     */
    public static int quantizeUnvoiced(float gainFromSearch) {
        float gainDb = 20.0f * log10f(gainFromSearch + 1.0e-16f);
        gainDb = Math.min(Math.max(gainDb, UV_GAIN_MIN_DB), UV_GAIN_MAX_DB);
        return Math.round((gainDb - UV_GAIN_MIN_DB) / UV_GAIN_STEP_DB);
    }

    /**
     * Returns the dequantized unvoiced fixed-codebook gain for an index, the {@code fcbgains_uv} lookup.
     *
     * @param fcbIdx the unvoiced fixed-codebook gain index
     * @return the gain {@code fcbgains_uv[fcbIdx]}
     */
    public float unvoicedGain(int fcbIdx) {
        return fcbgainsUv[fcbIdx];
    }

    /**
     * Evaluates the three-variable weighted-energy quadratic form, {@code smpl_wnrg3}.
     *
     * <p>Computes {@code x' C x} for the symmetric {@code 3x3} matrix {@code C} held row-major and the
     * three-vector {@code x}, in the native nested-accumulation order.
     *
     * @param c the row-major {@code 3x3} matrix
     * @param x the three-vector
     * @return the quadratic form {@code x' C x}
     */
    private static float wnrg3(float[] c, float[] x) {
        return x[0] * (c[0] * x[0] + c[1] * x[1] + c[2] * x[2])
                + x[1] * (c[3] * x[0] + c[4] * x[1] + c[5] * x[2])
                + x[2] * (c[6] * x[0] + c[7] * x[1] + c[8] * x[2]);
    }

    /**
     * Computes a single-precision dot product over a window of two arrays, {@code smpl_dot_prod}.
     *
     * <p>The reference {@code smpl_dot_prod} in {@code smpl_codec_util.c} is compiled at {@code -Ofast} with
     * {@code -msse2}, auto-vectorizing into a four-lane packed-single reduction: four partial sums gather the
     * products at indices congruent to their lane modulo four, are combined as {@code (s0 + s2) + (s1 + s3)},
     * and the trailing {@code len mod 4} products are added sequentially. The lane order changes the rounding
     * relative to a left-to-right sum and is reproduced here exactly.
     *
     * @param a    the first array
     * @param aOff the offset into the first array
     * @param b    the second array
     * @param bOff the offset into the second array
     * @param len  the number of elements
     * @return the accumulated single-precision dot product
     */
    private static float dotProd(float[] a, int aOff, float[] b, int bOff, int len) {
        float s0 = 0.0f;
        float s1 = 0.0f;
        float s2 = 0.0f;
        float s3 = 0.0f;
        int m = len & ~3;
        int i = 0;
        for (; i < m; i += 4) {
            s0 += a[aOff + i] * b[bOff + i];
            s1 += a[aOff + i + 1] * b[bOff + i + 1];
            s2 += a[aOff + i + 2] * b[bOff + i + 2];
            s3 += a[aOff + i + 3] * b[bOff + i + 3];
        }
        float acc = (s0 + s2) + (s1 + s3);
        for (; i < len; i++) {
            acc += a[aOff + i] * b[bOff + i];
        }
        return acc;
    }

    /**
     * Computes the base-ten logarithm in single precision, {@code log10f}.
     *
     * @param x the operand, strictly positive
     * @return the single-precision base-ten logarithm of {@code x}
     */
    private static float log10f(float x) {
        return (float) Math.log10(x);
    }

    /**
     * Computes the voiced fixed-codebook gain table, the {@code fcbgains_v} initialization of
     * {@code smpl_create_celp_tables}.
     *
     * @implNote This implementation forms the {@code powf} exponent {@code 0.05f * db} in single precision before
     * the power, matching the native {@code powf(10.0f, 0.05f * fcb_gain_db)} of {@code smpl_create_celp_tables}.
     * Multiplying the {@code 0.05} scale in {@code double} (then narrowing the power) rounds the exponent the
     * other way at 18 of the 34 levels, shifting the dequantized gain by one unit in the last place; that gain is
     * returned verbatim by {@code calc_gains_v} and scales the reconstructed fixed-codebook excitation, so the
     * one-ULP shift propagates through the adaptive-codebook ring and the weighting-filter memory into every
     * later subframe. Single-precision argument plus {@link Math#pow(double, double)} reproduces the reference
     * {@code powf} table byte-for-byte over all levels.
     *
     * @return a freshly allocated {@code SMPL_FCBG_V_N}-entry table
     */
    private static float[] buildVoicedGains() {
        float[] tab = new float[FCBG_V_N];
        for (int ix = 0; ix < FCBG_V_N; ix++) {
            float db = ix * V_GAIN_STEP_DB + V_GAIN_MIN_DB;
            tab[ix] = (float) Math.pow(10.0, 0.05f * db);
        }
        return tab;
    }

    /**
     * Computes the unvoiced fixed-codebook gain table, the {@code fcbgains_uv} initialization of
     * {@code smpl_create_celp_tables}.
     *
     * @implNote This implementation forms the {@code powf} exponent {@code 0.05f * db} in single precision, the
     * same reason given on {@link #buildVoicedGains()}: the native {@code powf(10.0f, 0.05f * fcb_gain_db)} rounds
     * differently from a {@code double}-scaled exponent at most levels, and the dequantized unvoiced gain scales
     * the reconstructed excitation directly.
     *
     * @return a freshly allocated {@code SMPL_UV_GAIN_IDX_LEN + 1}-entry table
     */
    private static float[] buildUnvoicedGains() {
        float[] tab = new float[UV_GAIN_IDX_LEN + 1];
        for (int ix = 0; ix <= UV_GAIN_IDX_LEN; ix++) {
            float db = ix * UV_GAIN_STEP_DB + UV_GAIN_MIN_DB;
            tab[ix] = (float) Math.pow(10.0, 0.05f * db);
        }
        return tab;
    }
}
