package com.github.auties00.cobalt.calls2.media.audio.mlow.celp;

import com.github.auties00.cobalt.calls2.media.audio.mlow.tables.MiscTables;

/**
 * Builds the per-subframe code-excited linear-prediction (CELP) excitation of an MLow voiced or unvoiced
 * low-band frame, the port of {@code smpl_gen_excitation}, {@code acb_dequant}, and {@code smpl_celp_decode}
 * (with their helpers {@code smpl_pitch_sharp}, {@code smpl_syn_ltp_basis}, {@code acb_synthesize}, and
 * {@code adjust_acbgains}) in {@code smpl_celp_util.c}.
 *
 * <p>The MLow decoder reconstructs each frame's linear-prediction residual (its excitation) in three additive
 * layers before running the short-term synthesis filter:
 * <ol>
 * <li><b>Fixed-codebook (FCB) excitation.</b> {@link #genExcitation} scatters the decoded signed pulses into
 * a zeroed excitation buffer, each pulse scaled by the per-subframe fixed-codebook gain looked up from the
 * voiced or unvoiced gain table. This is the whole-frame {@code lpc_res} the native
 * {@code smpl_gen_excitation} produces.</li>
 * <li><b>Adaptive-codebook (ACB) / long-term-prediction (LTP) contribution.</b> For a voiced frame
 * {@link #celpDecode} adds, per subframe, the three-tap symmetric LTP contribution synthesized from the
 * decoded pitch lags and the dequantized adaptive-codebook gains, sharpening the pitch first at low rate.
 * The adaptive-codebook history (the {@code acb_state} ring) is updated with the post-ACB excitation of every
 * subframe so the next subframe and the next frame can reference it. For an unvoiced frame the ACB step is a
 * pure state update.</li>
 * <li><b>Noise excitation.</b> Added by {@link NoiseGenerator} (a separate class) and not part of this
 * type.</li>
 * </ol>
 *
 * <p>{@link #celpDecode} mutates the supplied subframe slice of the excitation in place: on entry it holds
 * the fixed-codebook excitation of one subframe, on exit it holds that plus the adaptive-codebook
 * contribution (voiced) or is unchanged (unvoiced), and the adaptive-codebook state has advanced by one
 * subframe. The caller threads the same excitation array through {@link NoiseGenerator} and then the
 * short-term synthesis filter. The native dump order names these layers {@code fcb},
 * {@code lpc_res - fcb} (the ACB/LTP part), and {@code noise}.
 *
 * <p>This synthesizer is stateful: it owns the adaptive-codebook ring {@code acb_state} (the native
 * {@code CelpDecoder.acb_state}), which carries pitch history across subframes and frames. Construct one per
 * logical decode stream and call {@link #celpDecode} once per subframe in order; {@link #reset()} returns the
 * ring to the freshly constructed (zeroed) state. The fixed-codebook gain tables are computed once at
 * construction and shared read-only.
 *
 * <p>Scope is the SMPL 16 kHz, 60 ms, mono low-band path with {@code SMPL_LPC_ORDER == 16}, postfilter off.
 * The adaptive-codebook high boost is applied (it is part of the excitation, not a postfilter); the harmonic,
 * tilt, and LPC postfilters that the native decoder runs after the excitation are out of scope. This type is
 * not thread-safe.
 *
 * @implNote This implementation keeps the native two-pitch-cycle adaptive-codebook ring length
 * {@code 2 * SMPL_MAX_PITCH_LAG + SMPL_MAX_SF_LEN + SMPL_LTP_INTERPOL_DELAY} so the state-update
 * {@code memmove}/{@code memcpy} of {@code smpl_celp_decode} maps to array copies of identical spans. The
 * three-tap symmetric long-term predictor of {@code smpl_syn_ltp_basis} is reproduced for both the
 * integer-lag fast path (a plain history copy plus a one-sample-offset symmetric sum) and the fractional-lag
 * path (the eight-tap {@link MiscTables#INTERPOL_KERNEL} interpolation with the boundary dot-products). The
 * fixed-codebook gain tables are computed by {@code (float) Math.pow(10.0, 0.05 * db)} rather than the native
 * single-precision {@code powf}; the two agree to within the float envelope (validated against the C decoder's
 * own table dump), and they are constants so the small rounding difference does not accumulate. All
 * accumulations are performed in single-precision {@code float} to mirror the native arithmetic.
 */
public final class CelpSynthesizer {
    /**
     * Linear-prediction order of the MLow short-term filter, the native {@code SMPL_LPC_ORDER}.
     */
    private static final int LPC_ORDER = 16;

    /**
     * Number of adaptive-codebook gain taps, the native {@code SMPL_ACBG_M}; the symmetric three-tap
     * long-term predictor is parameterized by two stored gains (a center and a symmetric side gain).
     */
    private static final int ACBG_M = 2;

    /**
     * Maximum pitch lag in samples, the native {@code SMPL_MAX_PITCH_LAG} = {@code 20 ms * 16 kHz} = 320.
     */
    private static final int MAX_PITCH_LAG = 320;

    /**
     * Maximum low-band subframe length in samples, the native {@code SMPL_MAX_SF_LEN} = {@code 10 ms * 16 kHz}.
     */
    private static final int MAX_SF_LEN = 160;

    /**
     * Length in samples of one pitch (lag) subframe, the native {@code SMPL_LAG_SUBFRLEN} = 40 (2.5 ms).
     */
    private static final int LAG_SUBFRLEN = 40;

    /**
     * Half-width of the fractional-lag interpolation kernel, the native {@code SMPL_LTP_INTERPOL_DELAY} = 8.
     */
    private static final int LTP_INTERPOL_DELAY = 8;

    /**
     * Number of entries in the voiced fixed-codebook gain table, the native {@code SMPL_FCBG_V_N} = 34.
     */
    private static final int FCBG_V_N = 34;

    /**
     * Index span of the unvoiced fixed-codebook gain table, the native {@code SMPL_UV_GAIN_IDX_LEN} = 90; the
     * table has {@code SMPL_UV_GAIN_IDX_LEN + 1} entries.
     */
    private static final int UV_GAIN_IDX_LEN = 90;

    /**
     * Pitch-sharpening feedback coefficient applied to the low-rate excitation before the long-term
     * predictor, the native {@code SMPL_PITCH_SHARPENING_COEF} = {@code 0.9881f}.
     */
    private static final float PITCH_SHARPENING_COEF = 0.9881f;

    /**
     * The two endpoints of the adaptive-codebook high-boost interpolation, the native
     * {@code smpl_dec_acb_high_boost} = {@code {0.35f, 0.18f}}; the boost at a given normalized bitrate is the
     * linear blend of these two endpoints.
     */
    private static final float[] DEC_ACB_HIGH_BOOST = {0.35f, 0.18f};

    /**
     * The map from a coarse position bucket to its subframe index, the native {@code pos2idx} table; a pulse
     * at position {@code pos} belongs to subframe {@code pos2idx[pos >> shift]}, which equals
     * {@code pos / subfr_len}.
     */
    private static final int[] POS2IDX = {0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3};

    /**
     * The unsigned Q14 scale {@code 1 / 2^14} used to dequantize the adaptive-codebook gain codebook entries.
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
     * The adaptive-codebook history ring, the native {@code CelpDecoder.acb_state}; length
     * {@code 2 * SMPL_MAX_PITCH_LAG + SMPL_MAX_SF_LEN + SMPL_LTP_INTERPOL_DELAY}. The most recent excitation
     * occupies the tail; the long-term predictor reaches back into it by the decoded pitch lags.
     */
    private final float[] acbState;

    /**
     * Reusable scratch for the interleaved long-term-prediction basis vectors, sized to the maximum
     * {@code SMPL_MAX_SF_LEN * SMPL_ACBG_M}. Each voiced {@link #celpDecode} fully overwrites its
     * {@code [0, 2 * numLags * SMPL_LAG_SUBFRLEN)} read region through {@link #synLtpBasis}, so reusing one
     * instance-owned buffer avoids a per-subframe allocation without altering any consumed value; the decode
     * kernel is single-threaded per stream, so the single owner is safe.
     */
    private final float[] acbBasisScratch;

    /**
     * Constructs a CELP excitation synthesizer with freshly computed gain tables and a zeroed
     * adaptive-codebook ring.
     *
     * <p>The voiced and unvoiced fixed-codebook gain tables are computed once; the adaptive-codebook history
     * starts silent, so the first voiced subframe's long-term contribution is zero until real excitation has
     * filled the ring.
     */
    public CelpSynthesizer() {
        this.fcbgainsV = buildVoicedGains();
        this.fcbgainsUv = buildUnvoicedGains();
        this.acbState = new float[2 * MAX_PITCH_LAG + MAX_SF_LEN + LTP_INTERPOL_DELAY];
        this.acbBasisScratch = new float[MAX_SF_LEN * ACBG_M];
    }

    /**
     * Returns this synthesizer to its freshly constructed state by zeroing the adaptive-codebook ring.
     *
     * <p>Call this between independent decode sessions; do not call it between the subframes or frames of one
     * continuous stream, which must thread the pitch history.
     */
    public void reset() {
        java.util.Arrays.fill(acbState, 0.0f);
    }

    /**
     * Scatters the decoded fixed-codebook pulses into a per-frame excitation buffer, {@code smpl_gen_excitation}.
     *
     * <p>Zeroes {@code exc} over the frame, then for each decoded position writes the signed stacked pulse
     * magnitude scaled by the per-subframe fixed-codebook gain. The gain table is the voiced table when
     * {@code voiced} is set, otherwise the unvoiced table; the subframe a pulse belongs to is recovered from
     * its position by {@code pos2idx[pos >> shift]}, where the shift is 4 for an 80-sample subframe and 5
     * otherwise (the {@code pos / subfr_len} identity).
     *
     * @param fcbgIdx    the per-subframe fixed-codebook gain index, the native {@code fcbg_idx}; at least
     *                   {@code numSubfr} entries
     * @param voiced     {@code true} to use the voiced gain table, {@code false} for the unvoiced table
     * @param numSubfr   the number of subframes in the frame
     * @param subfrLen   the subframe length in samples, the native {@code subfr_len}; 80 selects the shift-4
     *                   bucket map
     * @param nPositions the number of distinct decoded pulse positions
     * @param positions  the absolute sample position of each pulse, at least {@code nPositions} entries
     * @param posPulses  the signed stacked pulse magnitude at each position, at least {@code nPositions}
     *                   entries
     * @param exc        the excitation output, written over {@code [0, numSubfr * subfrLen)}; must be at least
     *                   that long
     */
    public void genExcitation(int[] fcbgIdx, boolean voiced, int numSubfr, int subfrLen,
                              int nPositions, short[] positions, short[] posPulses, float[] exc) {
        float[] gainTab = voiced ? fcbgainsV : fcbgainsUv;
        float[] fcbGains = new float[numSubfr];
        for (int sf = 0; sf < numSubfr; sf++) {
            fcbGains[sf] = gainTab[fcbgIdx[sf]];
        }
        int shift = subfrLen == 80 ? 4 : 5;
        int len = numSubfr * subfrLen;
        java.util.Arrays.fill(exc, 0, len, 0.0f);
        for (int n = 0; n < nPositions; n++) {
            int pos = positions[n];
            exc[pos] = posPulses[n] * fcbGains[POS2IDX[pos >> shift]];
        }
    }

    /**
     * Dequantizes one subframe's adaptive-codebook gains from their codebook index, {@code acb_dequant}.
     *
     * <p>Reads {@code SMPL_ACBG_M} signed Q14 codebook entries for the index from the rate-selected
     * adaptive-codebook gain codebook and scales them to real gains by {@code 1 / 2^14}.
     *
     * @param lowRate {@code true} for the low-rate codebook, {@code false} for the high-rate codebook
     * @param acbIdx  the decoded adaptive-codebook gain index
     * @return a freshly allocated {@code SMPL_ACBG_M}-entry array of real-valued tap gains
     */
    public static float[] acbDequant(boolean lowRate, int acbIdx) {
        short[] cb = lowRate ? MiscTables.ACB_GAINS_LR_Q14 : MiscTables.ACB_GAINS_HR_Q14;
        float[] acbG = new float[ACBG_M];
        for (int m = 0; m < ACBG_M; m++) {
            acbG[m] = cb[acbIdx * ACBG_M + m] * Q14_SCALE;
        }
        return acbG;
    }

    /**
     * Adds the adaptive-codebook (long-term-prediction) contribution to one subframe of the excitation and
     * advances the adaptive-codebook history, {@code smpl_celp_decode}.
     *
     * <p>On entry {@code excSubframe} holds the fixed-codebook excitation of one subframe; on exit, for a
     * voiced frame, it additionally holds the three-tap symmetric long-term contribution synthesized from the
     * decoded pitch lags and the dequantized adaptive-codebook gains. The low-rate path first sharpens the
     * excitation by feeding it back at the last lag. The adaptive-codebook gains are boosted by the
     * normalized-bitrate-dependent high boost before synthesis. Regardless of voicing, the adaptive-codebook
     * ring is shifted by one subframe and the post-contribution excitation is appended, so later subframes
     * and frames can reference it.
     *
     * @param voiced            {@code true} for a voiced frame (the long-term contribution is added),
     *                          {@code false} for an unvoiced frame (history update only)
     * @param acbGain           the dequantized adaptive-codebook tap gains, {@code SMPL_ACBG_M} entries;
     *                          mutated in place by the high boost
     * @param lags              the per-lag-subframe pitch lags for this subframe, {@code numLags} entries
     *                          starting at the subframe's first lag
     * @param numLags           the number of lag subframes spanning this subframe, the native
     *                          {@code lags_per_subframe}
     * @param subfrLen          the subframe length in samples, the native {@code subframe_length_16}
     * @param lowRate           {@code true} for the low-rate path (pitch sharpening is applied)
     * @param normalizedBitrate the frame's normalized bitrate, the native {@code normalized_bitrate}, in
     *                          {@code [0, 1]}; interpolates the adaptive-codebook high boost
     * @param excSubframe       the subframe excitation, mutated in place over {@code [0, subfrLen)}
     */
    public void celpDecode(boolean voiced, float[] acbGain, float[] lags, int numLags, int subfrLen,
                           boolean lowRate, float normalizedBitrate, float[] excSubframe) {
        int acbStateLen = subfrLen + 2 * MAX_PITCH_LAG + LTP_INTERPOL_DELAY;
        if (voiced) {
            float highBoost = DEC_ACB_HIGH_BOOST[0]
                    + (DEC_ACB_HIGH_BOOST[1] - DEC_ACB_HIGH_BOOST[0]) * normalizedBitrate;
            int iLag = (int) lags[numLags - 1];
            if (lowRate) {
                pitchSharp(excSubframe, iLag, subfrLen);
            }
            float[] acbBasis = acbBasisScratch;
            synLtpBasis(lags, numLags, acbStateLen, acbBasis);
            adjustAcbGains(acbGain, highBoost);
            for (int i = 0; i < subfrLen; i++) {
                excSubframe[i] += acbBasis[i] * acbGain[0] + acbBasis[subfrLen + i] * acbGain[1];
            }
        }
        System.arraycopy(acbState, subfrLen, acbState, 0, acbStateLen - 2 * subfrLen);
        System.arraycopy(excSubframe, 0, acbState, acbStateLen - 2 * subfrLen, subfrLen);
    }

    /**
     * Sharpens the excitation by adding a scaled, lag-delayed copy of itself, {@code smpl_pitch_sharp}.
     *
     * <p>For each sample past the lag, adds {@code SMPL_PITCH_SHARPENING_COEF} times the sample one lag
     * earlier, an in-place comb that emphasizes the pitch periodicity. Applied only on the low-rate path.
     *
     * @param x   the signal to sharpen, mutated in place
     * @param lag the pitch lag in samples
     * @param len the number of samples to process
     */
    private static void pitchSharp(float[] x, int lag, int len) {
        for (int i = lag; i < len; i++) {
            x[i] += x[i - lag] * PITCH_SHARPENING_COEF;
        }
    }

    /**
     * Builds the two-tap symmetric long-term-prediction basis vectors from the adaptive-codebook history,
     * {@code smpl_syn_ltp_basis}.
     *
     * <p>For each lag subframe this synthesizes, in the adaptive-codebook ring's pitch region, the
     * pitch-period repetition at the decoded lag and writes two basis vectors: the center tap (the lag-delayed
     * history) and the symmetric side tap (the average of the samples one position on either side of the
     * lag-delayed history). When the lag is integral, the repetition is a plain history copy and the side tap
     * is the offset symmetric sum; when the lag is fractional, the eight-tap interpolation kernel reconstructs
     * the sub-sample-delayed history, with boundary dot-products supplying the first and last side-tap
     * samples. The two basis vectors are interleaved as {@code [center..., side...]} per the
     * {@code SMPL_ACBG_M == 2} layout.
     *
     * @param lags        the per-lag-subframe pitch lags, {@code numLags} entries
     * @param numLags     the number of lag subframes
     * @param acbStateLen the active length of the adaptive-codebook ring for this subframe
     * @param acbBasis    the basis output, {@code 2 * numLags * SMPL_LAG_SUBFRLEN} entries written
     */
    private void synLtpBasis(float[] lags, int numLags, int acbStateLen, float[] acbBasis) {
        int pEnd = acbStateLen - numLags * LAG_SUBFRLEN;
        for (int subfr = 0; subfr < numLags; subfr++) {
            int iLag = (int) Math.floor(lags[subfr]);
            int centerOut = subfr * LAG_SUBFRLEN;
            int sideOut = (numLags + subfr) * LAG_SUBFRLEN;
            if (iLag == lags[subfr]) {
                for (int i = 0; i < LAG_SUBFRLEN; i++) {
                    acbState[pEnd + i] = acbState[pEnd + i - iLag];
                }
                System.arraycopy(acbState, pEnd, acbBasis, centerOut, LAG_SUBFRLEN);
                int a = pEnd - iLag - 1;
                int b = pEnd - iLag + 1;
                for (int i = 0; i < LAG_SUBFRLEN; i++) {
                    acbBasis[sideOut + i] = acbState[a + i] + acbState[b + i];
                }
            } else {
                // first uses native i == -1, last uses native i == SMPL_LAG_SUBFRLEN; both index the
                // integer-floor lag, since the symmetric kernel supplies the implicit half-sample delay.
                float first = dotProd(acbState, pEnd - 1 - iLag - LTP_INTERPOL_DELAY,
                        MiscTables.INTERPOL_KERNEL, 2 * LTP_INTERPOL_DELAY);
                interpol(acbState, pEnd - iLag - LTP_INTERPOL_DELAY, acbState, pEnd, LAG_SUBFRLEN);
                float last = dotProd(acbState, pEnd + LAG_SUBFRLEN - iLag - LTP_INTERPOL_DELAY,
                        MiscTables.INTERPOL_KERNEL, 2 * LTP_INTERPOL_DELAY);
                System.arraycopy(acbState, pEnd, acbBasis, centerOut, LAG_SUBFRLEN);
                acbBasis[sideOut] = first + acbState[pEnd + 1];
                for (int i = 0; i < LAG_SUBFRLEN - 2; i++) {
                    acbBasis[sideOut + 1 + i] = acbState[pEnd + i] + acbState[pEnd + 2 + i];
                }
                acbBasis[sideOut + LAG_SUBFRLEN - 1] = acbState[pEnd + LAG_SUBFRLEN - 2] + last;
            }
            pEnd += LAG_SUBFRLEN;
        }
    }

    /**
     * Applies the high-frequency boost to the adaptive-codebook gains, {@code adjust_acbgains}.
     *
     * <p>Re-parameterizes the two stored gains into the sum and difference of the corresponding three-tap
     * symmetric filter, increases the difference magnitude by {@code highBoost} (capped at the sum
     * magnitude), then maps back. A zero boost is a no-op. The boost lifts the high-frequency end of the
     * long-term predictor without changing its overall energy budget.
     *
     * @param acbG      the two adaptive-codebook gains, mutated in place
     * @param highBoost the boost amount; {@code 0} leaves the gains unchanged
     */
    private static void adjustAcbGains(float[] acbG, float highBoost) {
        if (highBoost == 0.0f) {
            return;
        }
        float f0 = acbG[0] + 2.0f * acbG[1];
        float f1 = acbG[0] - acbG[1];
        float absF1New = Math.min(Math.abs(f1) + highBoost, Math.abs(f0));
        f1 *= absF1New / (Math.abs(f1) + 1e-12f);
        acbG[0] = (f0 + 2.0f * f1) / 3.0f;
        acbG[1] = (f0 - f1) / 3.0f;
    }

    /**
     * Computes the symmetric eight-tap fractional-delay interpolation, {@code smpl_interpol}.
     *
     * <p>For each output sample {@code n} this forms the kernel-weighted symmetric sum of the sixteen input
     * samples centered on {@code n}, writing the result to {@code y[yOff + n]}. The native code interpolates
     * in place into the same buffer; the source and destination here are the same {@code acbState} array,
     * read ahead of where it is written so the in-place semantics match.
     *
     * @param x    the input array
     * @param xOff the offset of the first input sample of output sample 0
     * @param y    the output array
     * @param yOff the offset of the first output sample
     * @param n    the number of output samples
     */
    private static void interpol(float[] x, int xOff, float[] y, int yOff, int n) {
        float[] kernel = MiscTables.INTERPOL_KERNEL;
        for (int m = 0; m < n; m++) {
            float ret = 0.0f;
            for (int i = 0; i < 8; i++) {
                ret += (x[xOff + m + i] + x[xOff + m + 15 - i]) * kernel[i];
            }
            y[yOff + m] = ret;
        }
    }

    /**
     * Computes the dot product of a kernel against a window of a signal, {@code smpl_dot_prod}.
     *
     * @param a    the signal array
     * @param aOff the offset of the first signal sample
     * @param b    the kernel array, read from index zero
     * @param len  the number of taps
     * @return the accumulated single-precision dot product
     */
    private static float dotProd(float[] a, int aOff, float[] b, int len) {
        float ret = 0.0f;
        for (int i = 0; i < len; i++) {
            ret += a[aOff + i] * b[i];
        }
        return ret;
    }

    /**
     * Computes the voiced fixed-codebook gain table, the {@code fcbgains_v} initialization of
     * {@code smpl_create_celp_tables}.
     *
     * <p>Entry {@code ix} is {@code 10^(0.05 * (ix * SMPL_V_GAIN_STEP_DB + SMPL_V_GAIN_MIN_DB))} with
     * {@code SMPL_V_GAIN_STEP_DB == 3} and {@code SMPL_V_GAIN_MIN_DB == -100}.
     *
     * @return a freshly allocated {@code SMPL_FCBG_V_N}-entry table
     */
    private static float[] buildVoicedGains() {
        float[] tab = new float[FCBG_V_N];
        for (int ix = 0; ix < FCBG_V_N; ix++) {
            float db = ix * 3.0f + (-100.0f);
            tab[ix] = (float) Math.pow(10.0, 0.05 * db);
        }
        return tab;
    }

    /**
     * Computes the unvoiced fixed-codebook gain table, the {@code fcbgains_uv} initialization of
     * {@code smpl_create_celp_tables}.
     *
     * <p>Entry {@code ix} is {@code 10^(0.05 * (ix * SMPL_UV_GAIN_STEP_DB + SMPL_UV_GAIN_MIN_DB))} with
     * {@code SMPL_UV_GAIN_STEP_DB == 1} and {@code SMPL_UV_GAIN_MIN_DB == -90}, for {@code ix} in
     * {@code [0, SMPL_UV_GAIN_IDX_LEN]}.
     *
     * @return a freshly allocated {@code SMPL_UV_GAIN_IDX_LEN + 1}-entry table
     */
    private static float[] buildUnvoicedGains() {
        float[] tab = new float[UV_GAIN_IDX_LEN + 1];
        for (int ix = 0; ix <= UV_GAIN_IDX_LEN; ix++) {
            float db = ix * 1.0f + (-90.0f);
            tab[ix] = (float) Math.pow(10.0, 0.05 * db);
        }
        return tab;
    }

    /**
     * Returns the adaptive-codebook history ring for direct inspection, the native
     * {@code CelpDecoder.acb_state}.
     *
     * <p>Exposed package-private so a harness can prime the ring with a known history before driving
     * {@link #celpDecode}, mirroring how the native decoder's {@code acb_state} is seeded. The returned array
     * is the live backing store; mutations are visible to subsequent synthesis.
     *
     * @return the live adaptive-codebook ring
     */
    float[] acbState() {
        return acbState;
    }
}
