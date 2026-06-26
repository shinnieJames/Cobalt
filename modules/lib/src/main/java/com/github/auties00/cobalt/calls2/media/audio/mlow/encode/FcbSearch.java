package com.github.auties00.cobalt.calls2.media.audio.mlow.encode;

/**
 * Fixed-codebook (FCB) algebraic-codebook pulse search for one analysis-by-synthesis (AbS) subframe of the
 * MLow speech codec, the port of {@code smpl_fcb_search} and {@code smpl_fcb_search_deldec} (and their helpers
 * {@code add_pulse}, {@code calc_d_abs_and_sign}, {@code check_if_better}, {@code check_if_better_deldec},
 * {@code is_unique}, {@code get_PhiCol}) in {@code smpl_celp_util.c}, with the shared tournament primitives
 * {@code smpl_celp_q}, {@code smpl_get_maxi}, and {@code smpl_get_maxi_K} of {@code smpl_codec_util.c}.
 *
 * <p>The fixed codebook is sparse: each subframe's excitation is a handful of unit pulses with positions and
 * signs. The search places pulses one at a time to greedily maximize the ratio of the squared
 * perceptually-weighted correlation with the target to the weighted self-energy, the native numerator/denom
 * {@code Q = num^2 / den}. Two strategies share this class:
 * <ul>
 * <li><b>Delayed-decision tournament</b> ({@link #searchDeldec}), the native {@code smpl_fcb_search_deldec}.
 * Keeps a beam of survivor pulse trains; at each pulse stage it extends every survivor by every candidate
 * position, dedups by a rolling 64-bit signature so two trains that reach the same pulse set are not both
 * kept, and re-selects the top survivors by the tournament {@code smpl_get_maxi_K}. This is the path the
 * high-rate active-voice encoder always takes (its survivor budget exceeds one per pulse).</li>
 * <li><b>Single-survivor greedy</b> ({@link #search}), the native {@code smpl_fcb_search}. Adds one pulse per
 * stage at the {@code smpl_get_maxi} argmax with no beam; the fallback the encoder uses only when the survivor
 * budget collapses to one.</li>
 * </ul>
 * Both jointly track two rate points (the native {@code SMPL_CELP_IDX_MAIN} primary and
 * {@code SMPL_CELP_IDX_FEC} forward-error-correction), stopping each at the pulse count where adding another
 * pulse no longer beats the per-pulse weighted-energy threshold.
 *
 * <p>{@link Result} carries, per rate point, the signed pulse list (the native {@code pulses}, encoded as
 * {@code +-(position + 1)}), the pulse count, the weighted energy at the optimum, the open-loop gain estimate
 * {@code gain_from_search}, and the weighted self-energy {@code fcb_wnrg}; {@link GainQuantizer} consumes the
 * last two. The target {@code d} is the fixed-codebook target: the closed-loop {@code d_ltp} from
 * {@link AcbSearch} for a voiced subframe, or the raw {@code d_lpc} for an unvoiced subframe.
 *
 * <p>The pulse signatures {@code sgntrs} are the per-encoder random 64-bit tags the delayed-decision dedup
 * keys on (the native {@code CelpEncoder.sgntrs}, seeded once from the C {@code rand()} stream); the AbS loop
 * owns them and threads the same array in for every subframe so the dedup is reproducible. The flipped
 * weighting column {@code PhiFlip} and its zeroth lag {@code Phi[0]} are the perceptually-weighted
 * impulse-response auto-correlation the AbS loop builds.
 *
 * <p>Scope is the SMPL 16 kHz / 60 ms / mono low-band path. The pitch-sharpening branch of the
 * delayed-decision search is ported for the low-rate path but is inactive on the high-rate path
 * ({@code pitch_sharp == 0}), where the simpler denominator update is taken. This type is stateless across
 * subframes apart from two reused scratch survivor pools allocated per instance; construct one per encode
 * stream (not shared across threads) and call the search once per subframe.
 *
 * @implNote This implementation reproduces the survivor tournament float arithmetic exactly. {@code Q} is the
 * native {@code num*num/den} (not {@code num/den*num}); {@code smpl_get_maxi} and {@code smpl_get_maxi_K} are
 * transcribed with their pairwise tree reduction, the strict greater-than comparison, the leaf-flag toggle,
 * and the {@code -FLT_MAX} removal-and-re-propagation, so a candidate index never flips relative to the native
 * SSE reduction. The denominator update reads the flipped column {@code PhiFlip[SMPL_MAX_SF_LEN - col]} over
 * the non-zero band {@code [max(col - L_resp + 1, 0), min(col + L_resp, N))} as {@code get_PhiCol} does. The
 * delayed-decision survivor pools are sized to {@code SMPL_CELP_MAX_NUMSURV} and
 * {@code SMPL_CELP_MAX_NUMSURV^2} like the native {@code CelpScratch}. All accumulations are single precision.
 */
public final class FcbSearch {
    /**
     * The forward-error-correction rate-point index, the native {@code SMPL_CELP_IDX_FEC}.
     */
    private static final int IDX_FEC = 0;

    /**
     * The primary rate-point index, the native {@code SMPL_CELP_IDX_MAIN}.
     */
    private static final int IDX_MAIN = 1;

    /**
     * Number of jointly tracked rate points, the native {@code SMPL_CELP_MAX_RATES}.
     */
    private static final int MAX_RATES = 2;

    /**
     * Maximum low-band subframe length in samples, the native {@code SMPL_MAX_SF_LEN}; the flipped weighting
     * column {@code PhiFlip} is centered at this index.
     */
    private static final int MAX_SF_LEN = 160;

    /**
     * Maximum pulses searched per subframe, the native {@code SMPL_MAX_PULSES_PER_SF}.
     */
    private static final int MAX_PULSES_PER_SF = 40;

    /**
     * Survivor pool width, the native {@code SMPL_CELP_MAX_NUMSURV}.
     */
    private static final int MAX_NUMSURV = 8;

    /**
     * Largest tournament input length, the native {@code MAX_SORT_LEN} in {@code smpl_get_maxi_K}.
     */
    private static final int MAX_SORT_LEN = 187;

    /**
     * The largest finite single-precision value, the native {@code FLT_MAX} used as the removal sentinel in the
     * sorted tournament.
     */
    private static final float FLT_MAX = Float.MAX_VALUE;

    /**
     * One delayed-decision survivor pulse train, the native {@code FCB} struct.
     *
     * <p>Holds the running weighted energy, the pulse count placed so far, the last placed position and sign,
     * the rolling dedup signature, and the index of its backing {@link FcbState} in the read survivor buffer.
     */
    private static final class Fcb {
        float wnrg;
        int nPulses;
        int posNew;
        float signNew;
        long sgntr;
        int fcbStateIdx;

        void copyFrom(Fcb o) {
            wnrg = o.wnrg;
            nPulses = o.nPulses;
            posNew = o.posNew;
            signNew = o.signNew;
            sgntr = o.sgntr;
            fcbStateIdx = o.fcbStateIdx;
        }
    }

    /**
     * The per-survivor running numerator, denominator, and pulse history, the native {@code FCBstate} struct.
     */
    private static final class FcbState {
        final int[] pulsePositions = new int[MAX_SF_LEN];
        final float[] pulseSigns = new float[MAX_SF_LEN];
        final float[] num = new float[MAX_SF_LEN];
        final float[] den = new float[MAX_SF_LEN];

        void copyFrom(FcbState o) {
            System.arraycopy(o.pulsePositions, 0, pulsePositions, 0, MAX_SF_LEN);
            System.arraycopy(o.pulseSigns, 0, pulseSigns, 0, MAX_SF_LEN);
            System.arraycopy(o.num, 0, num, 0, MAX_SF_LEN);
            System.arraycopy(o.den, 0, den, 0, MAX_SF_LEN);
        }
    }

    /**
     * The fixed-codebook search result for one subframe, indexed by rate point.
     *
     * @param pulses         the signed pulse list per rate point, each entry {@code +-(position + 1)}; only
     *                       the first {@link #nPulses()} entries of each row are valid
     * @param nPulses        the pulse count per rate point
     * @param wnrg           the weighted energy at the search optimum per rate point, the native {@code wnrg}
     * @param gainFromSearch the open-loop fixed-codebook gain estimate per rate point, the native
     *                       {@code gain_from_search}
     * @param fcbWnrg        the weighted self-energy at the optimum per rate point, the native {@code fcb_wnrg}
     */
    public record Result(short[][] pulses, int[] nPulses, float[] wnrg, float[] gainFromSearch, float[] fcbWnrg) {
    }

    /**
     * The reused read-side survivor state buffer, the native {@code fcb_states[read_idx]}.
     */
    private final FcbState[] statesA = newStates();

    /**
     * The reused write-side survivor state buffer, the native {@code fcb_states[write_idx]}.
     */
    private final FcbState[] statesB = newStates();

    /**
     * The reused survivor pool, the native {@code fcbs}.
     */
    private final Fcb[] fcbs = newFcbs(MAX_NUMSURV);

    /**
     * The reused candidate pool, the native {@code fcb_candidates}.
     */
    private final Fcb[] fcbCandidates = newFcbs(MAX_NUMSURV * MAX_NUMSURV);

    /**
     * The reused candidate signature dedup set, the native {@code unique_sgntr}.
     */
    private final long[] uniqueSgntr = new long[MAX_NUMSURV * MAX_NUMSURV];

    /**
     * Allocates a survivor state buffer of {@link #MAX_NUMSURV} entries.
     *
     * @return a freshly allocated survivor state buffer
     */
    private static FcbState[] newStates() {
        FcbState[] s = new FcbState[MAX_NUMSURV];
        for (int i = 0; i < s.length; i++) {
            s[i] = new FcbState();
        }
        return s;
    }

    /**
     * Allocates a survivor pool of the given size.
     *
     * @param size the pool size
     * @return a freshly allocated survivor pool
     */
    private static Fcb[] newFcbs(int size) {
        Fcb[] f = new Fcb[size];
        for (int i = 0; i < f.length; i++) {
            f[i] = new Fcb();
        }
        return f;
    }

    /**
     * Runs the single-survivor greedy fixed-codebook search, {@code smpl_fcb_search}.
     *
     * <p>Places one pulse per stage at the running {@code Q = num^2 / den} argmax, jointly tracking the primary
     * and forward-error-correction rate points, each stopping where adding a pulse no longer beats its
     * per-pulse threshold. Used only when the survivor budget is one per pulse.
     *
     * @param d            the fixed-codebook target, {@code fcbSubfrlen} entries
     * @param wnrgPerPulse the per-pulse weighted-energy threshold per rate point
     * @param fcbPulsesMax the maximum pulse count per rate point
     * @param phi          the perceptually-weighted impulse-response auto-correlation, the native {@code Phi};
     *                     {@code phi[0]} is the zeroth lag
     * @param phiFlip      the flipped auto-correlation column, the native {@code PhiFlip}, centered at
     *                     {@code SMPL_MAX_SF_LEN}
     * @param lResp        the perceptual-response length, the native {@code L_resp}
     * @param fcbSubfrlen  the subframe length in samples, the native {@code fcb_subfrlen}
     * @return the per-rate-point pulse lists, counts, energies, and gain estimates
     */
    public Result search(float[] d, float[] wnrgPerPulse, int[] fcbPulsesMax, float[] phi, float[] phiFlip,
                         int lResp, int fcbSubfrlen) {
        short[][] pulses = new short[MAX_RATES][MAX_PULSES_PER_SF];
        int[] nPulses = new int[MAX_RATES];
        float[] wnrg = new float[MAX_RATES];
        float[] gainFromSearch = new float[MAX_RATES];
        float[] fcbWnrg = new float[MAX_RATES];

        int[] positions = new int[MAX_PULSES_PER_SF];
        float[] dAbs = new float[fcbSubfrlen];
        float[] dSign = new float[fcbSubfrlen];
        float[] num = new float[fcbSubfrlen];
        float[] den = new float[fcbSubfrlen];
        calcDAbsAndSign(d, fcbSubfrlen, dAbs, dSign);
        for (int i = 0; i < fcbSubfrlen; i++) {
            den[i] = phi[0] + 1e-16f;
        }
        System.arraycopy(dAbs, 0, num, 0, fcbSubfrlen);
        positions[0] = getMaxi(num, fcbSubfrlen);
        float[] nrgThr = new float[MAX_RATES];
        float ratio = num[positions[0]] / den[positions[0]];
        float wnrg0 = num[positions[0]] * ratio;
        if (checkIfBetter(wnrg0, nrgThr, IDX_MAIN, wnrgPerPulse[IDX_MAIN])) {
            nPulses[IDX_MAIN] = 1;
            wnrg[IDX_MAIN] = wnrg[IDX_FEC] = wnrg0;
            gainFromSearch[IDX_MAIN] = gainFromSearch[IDX_FEC] = ratio;
            fcbWnrg[IDX_MAIN] = fcbWnrg[IDX_FEC] = den[positions[0]];
            if (fcbPulsesMax[IDX_FEC] > 0) {
                nPulses[IDX_FEC] = nPulses[IDX_MAIN];
                wnrg[IDX_FEC] = wnrg[IDX_MAIN];
                gainFromSearch[IDX_FEC] = gainFromSearch[IDX_MAIN];
                fcbWnrg[IDX_FEC] = fcbWnrg[IDX_MAIN];
            }
        }

        float[] q = new float[fcbSubfrlen];
        for (int pulseNr = 1; pulseNr < fcbPulsesMax[IDX_MAIN]; pulseNr++) {
            int position = positions[pulseNr - 1];
            float sgn = dSign[position];
            for (int i = 0; i < fcbSubfrlen; i++) {
                num[i] += dAbs[position];
            }
            int[] nzr = new int[2];
            int phiCol = getPhiCol(position, lResp, fcbSubfrlen, nzr);
            float dDen = 0.0f;
            for (int i = 0; i < pulseNr - 1; i++) {
                dDen += phiFlip[phiCol + positions[i]] * dSign[positions[i]];
            }
            dDen *= 2.0f * sgn;
            dDen += phiFlip[phiCol + position];
            for (int i = 0; i < fcbSubfrlen; i++) {
                den[i] += dDen;
            }
            float twoSign = 2.0f * sgn;
            for (int i = nzr[0]; i < nzr[1]; i++) {
                den[i] += bandTerm(dSign[i], phiFlip[phiCol + i], twoSign);
            }
            celpQ(num, den, fcbSubfrlen, q);
            positions[pulseNr] = getMaxi(q, fcbSubfrlen);
            if (checkIfBetter(q[positions[pulseNr]], nrgThr, IDX_MAIN, wnrgPerPulse[IDX_MAIN])) {
                nPulses[IDX_MAIN] = pulseNr + 1;
                wnrg[IDX_MAIN] = q[positions[pulseNr]];
                gainFromSearch[IDX_MAIN] = num[positions[pulseNr]] / den[positions[pulseNr]];
                fcbWnrg[IDX_MAIN] = den[positions[pulseNr]];
            }
            if (fcbPulsesMax[IDX_FEC] >= pulseNr
                    && checkIfBetter(q[positions[pulseNr]], nrgThr, IDX_FEC, wnrgPerPulse[IDX_FEC])) {
                nPulses[IDX_FEC] = pulseNr + 1;
                wnrg[IDX_FEC] = q[positions[pulseNr]];
                gainFromSearch[IDX_FEC] = num[positions[pulseNr]] / den[positions[pulseNr]];
                fcbWnrg[IDX_FEC] = den[positions[pulseNr]];
            }
        }
        for (int r = IDX_FEC; r <= IDX_MAIN; r++) {
            if (nrgThr[r] > 0.0f) {
                for (int i = 0; i < nPulses[r]; i++) {
                    int position = positions[i];
                    pulses[r][i] = (short) (dSign[position] > 0 ? (1 + position) : -(1 + position));
                }
            } else {
                wnrg[r] = 0.0f;
                gainFromSearch[r] = 0.0f;
                fcbWnrg[r] = 0.0f;
                nPulses[r] = 0;
            }
        }
        return new Result(pulses, nPulses, wnrg, gainFromSearch, fcbWnrg);
    }

    /**
     * Runs the delayed-decision survivor-tournament fixed-codebook search, {@code smpl_fcb_search_deldec}.
     *
     * <p>Maintains a beam of survivor pulse trains; each pulse stage extends every survivor by every candidate
     * position, dedups extensions by their rolling signature, and keeps the top survivors. The primary and
     * forward-error-correction rate points are tracked jointly, each committing the best survivor at the pulse
     * count where adding another pulse no longer beats its per-pulse threshold. This is the high-rate
     * active-voice path.
     *
     * @param d            the fixed-codebook target, {@code fcbSubfrlen} entries
     * @param pitchSharp   the pitch-sharpening coefficient, {@code 0} on the high-rate path
     * @param lag          the integer pitch lag, used only when {@code pitchSharp} is non-zero
     * @param wnrgPerPulse the per-pulse weighted-energy threshold per rate point
     * @param fcbPulsesMax the maximum pulse count per rate point
     * @param surv         the survivor budget per pulse stage, the native {@code surv}; at least
     *                     {@code fcbPulsesMax[IDX_MAIN]} entries
     * @param phi          the perceptually-weighted impulse-response auto-correlation, the native {@code Phi}
     * @param phiFlip      the flipped auto-correlation column, the native {@code PhiFlip}, centered at
     *                     {@code SMPL_MAX_SF_LEN}
     * @param lResp        the perceptual-response length, the native {@code L_resp}
     * @param fcbSubfrlen  the subframe length in samples, the native {@code fcb_subfrlen}
     * @param sgntrs       the per-encoder random pulse signatures, the native {@code CelpEncoder.sgntrs}; at
     *                     least {@code fcbSubfrlen} entries
     * @return the per-rate-point pulse lists, counts, energies, and gain estimates
     */
    public Result searchDeldec(float[] d, float pitchSharp, int lag, float[] wnrgPerPulse, int[] fcbPulsesMax,
                               short[] surv, float[] phi, float[] phiFlip, int lResp, int fcbSubfrlen,
                               long[] sgntrs) {
        short[][] pulses = new short[MAX_RATES][MAX_PULSES_PER_SF];
        int[] nPulses = new int[MAX_RATES];
        float[] wnrg = new float[MAX_RATES];
        float[] gainFromSearch = new float[MAX_RATES];
        float[] fcbWnrg = new float[MAX_RATES];

        float[] dAbs = new float[fcbSubfrlen];
        float[] dSign = new float[fcbSubfrlen];
        if (pitchSharp != 0.0f && lag > 0 && lag < fcbSubfrlen) {
            float[] dNew = new float[fcbSubfrlen];
            System.arraycopy(d, 0, dNew, 0, fcbSubfrlen);
            for (int j = 0; j < fcbSubfrlen; j++) {
                float g = pitchSharp;
                for (int i = lag + j; i < fcbSubfrlen; i += lag) {
                    dNew[j] += g * d[i];
                    g *= pitchSharp;
                }
            }
            calcDAbsAndSign(dNew, fcbSubfrlen, dAbs, dSign);
        } else {
            calcDAbsAndSign(d, fcbSubfrlen, dAbs, dSign);
            pitchSharp = 0.0f;
        }

        // Double-buffered survivor state: read from statesA, write into statesB, swap after each stage.
        FcbState[] readBuf = statesA;
        FcbState[] writeBuf = statesB;
        Fcb[] bestFcb = {new Fcb(), new Fcb()};
        FcbState[] bestFcbState = {new FcbState(), new FcbState()};
        float[] nrgThr = new float[MAX_RATES];

        FcbState fcbState = writeBuf[0];
        System.arraycopy(dAbs, 0, fcbState.num, 0, fcbSubfrlen);
        if (pitchSharp == 0.0f) {
            for (int i = 0; i < fcbSubfrlen; i++) {
                fcbState.den[i] = phi[0] + 1e-16f;
            }
        } else {
            int[] nzr = new int[2];
            int off = fcbSubfrlen - 1;
            for (int i = fcbSubfrlen - 1; i >= 0; i -= lag) {
                float res = 1e-16f;
                float g1 = 1.0f;
                for (int j = i; j < fcbSubfrlen; j += lag) {
                    int phiCol = getPhiCol(j, lResp, fcbSubfrlen, nzr);
                    float g2 = 1.0f;
                    for (int k = i; k < fcbSubfrlen; k += lag) {
                        res += g1 * g2 * phiFlip[phiCol + k];
                        g2 *= pitchSharp;
                    }
                    g1 *= pitchSharp;
                }
                int len = Math.min(lag, off + 1);
                for (int j = 0; j < len; j++) {
                    fcbState.den[off - j] = res;
                }
                off -= len;
            }
        }
        // After the swap, readBuf == statesB (holds the seed state), writeBuf == statesA.
        FcbState[] tmpSwap = readBuf;
        readBuf = writeBuf;
        writeBuf = tmpSwap;

        float[] q = new float[Math.max(fcbSubfrlen, MAX_NUMSURV * MAX_NUMSURV)];
        if (pitchSharp == 0.0f) {
            System.arraycopy(fcbState.num, 0, q, 0, fcbSubfrlen);
        } else {
            celpQ(fcbState.num, fcbState.den, fcbSubfrlen, q);
        }

        int[] sortIx = new int[MAX_NUMSURV];
        int fcbsSize = 0;
        getMaxiK(q, sortIx, fcbSubfrlen, surv[0]);
        for (int i = 0; i < surv[0]; i++) {
            int pos = sortIx[i];
            Fcb f = fcbs[fcbsSize++];
            f.sgntr = sgntrs[pos];
            f.posNew = pos;
            f.signNew = dSign[pos];
            f.wnrg = (fcbState.num[pos] * fcbState.num[pos]) / fcbState.den[pos];
            f.nPulses = 0;
            f.fcbStateIdx = 0;
        }

        checkIfBetterDeldec(readBuf, fcbs[0], bestFcb[IDX_MAIN], bestFcbState[IDX_MAIN], nrgThr, IDX_MAIN, wnrgPerPulse[IDX_MAIN]);
        if (fcbPulsesMax[IDX_FEC] > 0) {
            checkIfBetterDeldec(readBuf, fcbs[0], bestFcb[IDX_FEC], bestFcbState[IDX_FEC], nrgThr, IDX_FEC, wnrgPerPulse[IDX_FEC]);
        }

        if (fcbPulsesMax[IDX_MAIN] > 1) {
            for (int pulseNr = 2; pulseNr < fcbPulsesMax[IDX_MAIN]; pulseNr++) {
                int candidatesSize = 0;
                int uniqueSize = 0;
                int idx = 0;
                for (int i = 0; i < fcbsSize; i++) {
                    int[] sizes = addPulse(fcbs[i], readBuf, writeBuf, dAbs, dSign, surv[pulseNr - 1], idx,
                            lag, pitchSharp, phiFlip, sgntrs, lResp, fcbSubfrlen, candidatesSize, uniqueSize);
                    candidatesSize = sizes[0];
                    uniqueSize = sizes[1];
                    idx++;
                }
                FcbState[] s2 = readBuf;
                readBuf = writeBuf;
                writeBuf = s2;
                for (int i = 0; i < candidatesSize; i++) {
                    q[i] = fcbCandidates[i].wnrg;
                }
                getMaxiK(q, sortIx, candidatesSize, surv[pulseNr - 1]);
                fcbsSize = 0;
                for (int i = 0; i < surv[pulseNr - 1]; i++) {
                    fcbs[fcbsSize++].copyFrom(fcbCandidates[sortIx[i]]);
                }
                checkIfBetterDeldec(readBuf, fcbs[0], bestFcb[IDX_MAIN], bestFcbState[IDX_MAIN], nrgThr, IDX_MAIN, wnrgPerPulse[IDX_MAIN]);
                if (fcbPulsesMax[IDX_FEC] >= pulseNr) {
                    checkIfBetterDeldec(readBuf, fcbs[0], bestFcb[IDX_FEC], bestFcbState[IDX_FEC], nrgThr, IDX_FEC, wnrgPerPulse[IDX_FEC]);
                }
            }
            // last pulse
            int candidatesSize = 0;
            int uniqueSize = 0;
            for (int i = 0; i < fcbsSize; i++) {
                int[] sizes = addPulse(fcbs[i], readBuf, writeBuf, dAbs, dSign, 1, i, lag, pitchSharp,
                        phiFlip, sgntrs, lResp, fcbSubfrlen, candidatesSize, uniqueSize);
                candidatesSize = sizes[0];
                uniqueSize = sizes[1];
            }
            FcbState[] s2 = readBuf;
            readBuf = writeBuf;
            writeBuf = s2;
            int bestIdx = 0;
            float maxWnrg = fcbCandidates[0].wnrg;
            for (int i = 1; i < candidatesSize; i++) {
                if (fcbCandidates[i].wnrg > maxWnrg) {
                    maxWnrg = fcbCandidates[i].wnrg;
                    bestIdx = i;
                }
            }
            checkIfBetterDeldec(readBuf, fcbCandidates[bestIdx], bestFcb[IDX_MAIN], bestFcbState[IDX_MAIN], nrgThr, IDX_MAIN, wnrgPerPulse[IDX_MAIN]);
        }

        for (int r = IDX_FEC; r <= IDX_MAIN; r++) {
            for (int i = 0; i < bestFcb[r].nPulses; i++) {
                pulses[r][i] = (short) (bestFcbState[r].pulseSigns[i] > 0
                        ? (1 + bestFcbState[r].pulsePositions[i])
                        : -(1 + bestFcbState[r].pulsePositions[i]));
            }
            pulses[r][bestFcb[r].nPulses] = (short) (bestFcb[r].signNew > 0
                    ? 1 + bestFcb[r].posNew
                    : -(1 + bestFcb[r].posNew));
            if (bestFcb[r].wnrg > 0.0f) {
                wnrg[r] = bestFcb[r].wnrg;
                gainFromSearch[r] = bestFcbState[r].num[bestFcb[r].posNew] / bestFcbState[r].den[bestFcb[r].posNew];
                fcbWnrg[r] = bestFcbState[r].den[bestFcb[r].posNew];
                nPulses[r] = bestFcb[r].nPulses + 1;
            } else {
                wnrg[r] = 0.0f;
                gainFromSearch[r] = 0.0f;
                fcbWnrg[r] = 0.0f;
                nPulses[r] = 0;
            }
        }
        return new Result(pulses, nPulses, wnrg, gainFromSearch, fcbWnrg);
    }

    /**
     * Extends one survivor by one pulse over all candidate positions, {@code add_pulse}.
     *
     * <p>Updates the running numerator and denominator for a new pulse at the survivor's pending position,
     * computes the per-position {@code Q}, and for each of the top {@code numsurv} positions whose rolling
     * signature is not already present appends a deduped candidate. The pitch-sharpening branch
     * ({@code pitchSharp != 0}) recomputes the denominator with the comb-delayed pulse train; the high-rate
     * path takes the simple branch.
     *
     * @param fcb            the survivor to extend, mutated to its new pending position and pulse count
     * @param readBuf        the read-side survivor state buffer
     * @param writeBuf       the write-side survivor state buffer
     * @param dAbs           the target absolute values
     * @param dSign          the target signs
     * @param numsurv        the number of top positions to spawn candidates from
     * @param idx            the write-buffer slot for this survivor's new state
     * @param lag            the integer pitch lag (pitch-sharpening branch only)
     * @param pitchSharp     the pitch-sharpening coefficient, {@code 0} for the simple branch
     * @param phiFlip        the flipped auto-correlation column
     * @param sgntrs         the per-encoder random pulse signatures
     * @param lResp          the perceptual-response length
     * @param fcbSubfrlen    the subframe length in samples
     * @param candidatesSize the current candidate count
     * @param uniqueSize     the current dedup set size
     * @return a two-element array holding the updated candidate count and dedup set size
     */
    private int[] addPulse(Fcb fcb, FcbState[] readBuf, FcbState[] writeBuf, float[] dAbs, float[] dSign,
                           int numsurv, int idx, int lag, float pitchSharp, float[] phiFlip, long[] sgntrs,
                           int lResp, int fcbSubfrlen, int candidatesSize, int uniqueSize) {
        FcbState stateW = writeBuf[idx];
        FcbState stateR = readBuf[fcb.fcbStateIdx];

        for (int i = 0; i < fcbSubfrlen; i++) {
            stateW.num[i] = stateR.num[i] + dAbs[fcb.posNew];
        }
        System.arraycopy(stateR.den, 0, stateW.den, 0, fcbSubfrlen);
        if (pitchSharp == 0.0f) {
            int[] nzr = new int[2];
            int phiCol = getPhiCol(fcb.posNew, lResp, fcbSubfrlen, nzr);
            float dDen = 0.0f;
            for (int i = 0; i < fcb.nPulses; i++) {
                dDen += phiFlip[phiCol + stateR.pulsePositions[i]] * stateR.pulseSigns[i];
            }
            dDen *= 2.0f * fcb.signNew;
            dDen += phiFlip[phiCol + fcb.posNew];
            for (int i = 0; i < fcbSubfrlen; i++) {
                stateW.den[i] += dDen;
            }
            float twoSign = 2.0f * fcb.signNew;
            for (int i = nzr[0]; i < nzr[1]; i++) {
                stateW.den[i] += bandTerm(dSign[i], phiFlip[phiCol + i], twoSign);
            }
        } else {
            int[] nzr = new int[2];
            float g1 = 1.0f;
            float dDen = 0.0f;
            for (int pos = fcb.posNew; pos < fcbSubfrlen; pos += lag) {
                int phiCol = getPhiCol(pos, lResp, fcbSubfrlen, nzr);
                for (int i = 0; i < fcb.nPulses; i++) {
                    float g2 = g1;
                    for (int posPrev = stateR.pulsePositions[i]; posPrev < fcbSubfrlen; posPrev += lag) {
                        dDen += g2 * phiFlip[phiCol + posPrev] * stateR.pulseSigns[i];
                        g2 *= pitchSharp;
                    }
                }
                g1 *= pitchSharp;
            }
            dDen *= 2.0f * fcb.signNew;
            g1 = 1.0f;
            for (int pos1 = fcb.posNew; pos1 < fcbSubfrlen; pos1 += lag) {
                int phiCol = getPhiCol(pos1, lResp, fcbSubfrlen, nzr);
                float g2 = g1;
                for (int pos2 = fcb.posNew; pos2 < fcbSubfrlen; pos2 += lag) {
                    dDen += g2 * phiFlip[phiCol + pos2];
                    g2 *= pitchSharp;
                }
                g1 *= pitchSharp;
            }
            for (int i = 0; i < fcbSubfrlen; i++) {
                stateW.den[i] += dDen;
            }
            float[] ddDen = new float[fcbSubfrlen];
            g1 = 1.0f;
            for (int pos = fcb.posNew; pos < fcbSubfrlen; pos += lag) {
                int phiCol = getPhiCol(pos, lResp, fcbSubfrlen, nzr);
                float g2 = g1;
                for (int k = 0; k < fcbSubfrlen; k += lag) {
                    int startI = Math.max(0, nzr[0] - k);
                    int endI = Math.min(fcbSubfrlen - k, nzr[1] - k);
                    for (int i = startI; i < endI; i++) {
                        ddDen[i] += g2 * phiFlip[phiCol + i + k];
                    }
                    g2 *= pitchSharp;
                }
                g1 *= pitchSharp;
            }
            for (int i = 0; i < fcbSubfrlen; i++) {
                stateW.den[i] += 2.0f * fcb.signNew * dSign[i] * ddDen[i];
            }
        }
        System.arraycopy(stateR.pulsePositions, 0, stateW.pulsePositions, 0, fcb.nPulses);
        stateW.pulsePositions[fcb.nPulses] = fcb.posNew;
        System.arraycopy(stateR.pulseSigns, 0, stateW.pulseSigns, 0, fcb.nPulses);
        stateW.pulseSigns[fcb.nPulses] = fcb.signNew;

        fcb.nPulses++;
        fcb.fcbStateIdx = idx;

        float[] qLocal = new float[fcbSubfrlen];
        celpQ(stateW.num, stateW.den, fcbSubfrlen, qLocal);
        int[] sortIx = new int[MAX_NUMSURV];
        getMaxiK(qLocal, sortIx, fcbSubfrlen, numsurv);

        long fcbSgntr = fcb.sgntr;
        for (int i = 0; i < numsurv; i++) {
            fcb.sgntr = fcbSgntr + sgntrs[sortIx[i]];
            if (isUnique(uniqueSize, fcb.sgntr)) {
                fcb.posNew = sortIx[i];
                fcb.signNew = dSign[sortIx[i]];
                fcb.wnrg = qLocal[sortIx[i]];
                fcbCandidates[candidatesSize++].copyFrom(fcb);
                uniqueSgntr[uniqueSize++] = fcb.sgntr;
            }
        }
        return new int[]{candidatesSize, uniqueSize};
    }

    /**
     * Commits the lead survivor as the best for a rate point if it beats the per-pulse threshold,
     * {@code check_if_better_deldec}.
     *
     * <p>Advances the running threshold by the per-pulse weighted energy; when the lead survivor's weighted
     * energy exceeds it, raises the threshold to that energy and snapshots the survivor and its state.
     *
     * @param readBuf      the read-side survivor state buffer holding the survivor's state
     * @param fcb          the lead survivor
     * @param bestFcb      the destination best-survivor snapshot
     * @param bestFcbState the destination best-state snapshot
     * @param nrgThr       the per-rate-point running thresholds
     * @param r            the rate point
     * @param wnrgPerPulse the per-pulse weighted-energy increment
     */
    private static void checkIfBetterDeldec(FcbState[] readBuf, Fcb fcb, Fcb bestFcb, FcbState bestFcbState,
                                            float[] nrgThr, int r, float wnrgPerPulse) {
        nrgThr[r] += wnrgPerPulse;
        if (fcb.wnrg > nrgThr[r]) {
            nrgThr[r] = fcb.wnrg;
            bestFcb.copyFrom(fcb);
            bestFcbState.copyFrom(readBuf[fcb.fcbStateIdx]);
        }
    }

    /**
     * Tests whether a weighted energy beats the running per-pulse threshold, {@code check_if_better}.
     *
     * @param wnrg         the candidate weighted energy
     * @param nrgThr       the per-rate-point running thresholds
     * @param r            the rate point
     * @param wnrgPerPulse the per-pulse weighted-energy increment
     * @return {@code true} when the candidate beats the advanced threshold
     */
    private static boolean checkIfBetter(float wnrg, float[] nrgThr, int r, float wnrgPerPulse) {
        nrgThr[r] += wnrgPerPulse;
        if (wnrg > nrgThr[r]) {
            nrgThr[r] = wnrg;
            return true;
        }
        return false;
    }

    /**
     * Tests whether a rolling signature is absent from the dedup set, {@code is_unique}.
     *
     * @param uniqueSize the current dedup set size
     * @param sgntr      the candidate signature
     * @return {@code true} when the signature is not yet present
     */
    private boolean isUnique(int uniqueSize, long sgntr) {
        for (int i = 0; i < uniqueSize; i++) {
            if (uniqueSgntr[i] == sgntr) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the flipped-column base offset and non-zero band for a pulse position, {@code get_PhiCol}.
     *
     * <p>The flipped weighting column for position {@code col} starts at {@code SMPL_MAX_SF_LEN - col}; the
     * denominator update only touches the band {@code [max(col - L_resp + 1, 0), min(col + L_resp, N))}.
     *
     * @param col         the pulse position
     * @param lResp       the perceptual-response length
     * @param fcbSubfrlen the subframe length in samples
     * @param nonZeroRange the destination two-element band {@code [inclusive, exclusive)}
     * @return the flipped-column base offset into {@code PhiFlip}
     */
    private static int getPhiCol(int col, int lResp, int fcbSubfrlen, int[] nonZeroRange) {
        nonZeroRange[0] = Math.max(col - lResp + 1, 0);
        nonZeroRange[1] = Math.min(col + lResp, fcbSubfrlen);
        return MAX_SF_LEN - col;
    }

    /**
     * Computes one cross-energy band term of the denominator update, the native
     * {@code 2.0f * sign_new * d_sign[i] * PhiCol[i]} of {@code add_pulse} and {@code smpl_fcb_search}.
     *
     * <p>Multiplies the target sign, the flipped auto-correlation entry, and twice the new pulse sign with the
     * association {@code (dSign * phi) * twoSign}.
     *
     * @param dSign   the target sign at the band index, {@code d_sign[i]}
     * @param phi     the flipped auto-correlation entry at the band index, {@code PhiCol[i]}
     * @param twoSign twice the new pulse sign, {@code 2.0f * sign_new}
     * @return the band term to add to the running denominator at this index
     * @implNote This implementation reproduces the single-precision multiply association the {@code -Ofast}
     * (with {@code -ffast-math} reassociation) SSE-vectorized native band loop uses. The vectorized body, which
     * covers all but the up-to-three-element scalar remainder of the {@code 63}-wide non-zero band
     * ({@code 2 * SMPL_PERC_RESP_LEN - 1}), groups the product as {@code (d_sign[i] * PhiCol[i]) * (2 * sign)},
     * not the source left-to-right {@code ((2 * sign) * d_sign[i]) * PhiCol[i]}. The two orders can differ by
     * one unit in the last place in the running {@code den}, which the {@code Q = num^2 / den} argmax can
     * resolve differently when two candidate positions are near-tied. The factor {@code 2.0f * sign} equals
     * {@code sign + sign} bit-for-bit, so the native add-doubled formation needs no special handling here.
     */
    private static float bandTerm(float dSign, float phi, float twoSign) {
        return (dSign * phi) * twoSign;
    }

    /**
     * Computes the per-position search criterion {@code Q = num^2 / den}, {@code smpl_celp_q}.
     *
     * @param num the numerator array
     * @param den the denominator array
     * @param l   the length
     * @param q   the destination criterion array
     */
    private static void celpQ(float[] num, float[] den, int l, float[] q) {
        for (int i = 0; i < l; i++) {
            q[i] = (num[i] * num[i]) / den[i];
        }
    }

    /**
     * Splits a target into per-sample absolute values and signs, {@code calc_d_abs_and_sign}.
     *
     * <p>A zero or negative sample takes sign {@code -1}; a strictly positive sample takes sign {@code +1},
     * matching the native {@code d[i] > 0} test.
     *
     * @param d     the target
     * @param l     the length
     * @param dAbs  the destination absolute values
     * @param dSign the destination signs
     */
    private static void calcDAbsAndSign(float[] d, int l, float[] dAbs, float[] dSign) {
        for (int i = 0; i < l; i++) {
            if (d[i] > 0.0f) {
                dAbs[i] = d[i];
                dSign[i] = 1.0f;
            } else {
                dAbs[i] = -d[i];
                dSign[i] = -1.0f;
            }
        }
    }

    /**
     * Returns the index of the maximum, {@code smpl_get_maxi}.
     *
     * <p>Reproduces the native pairwise tree reduction: a first SIMD-style max-fold of the two array halves,
     * repeated halving while the length is even, a strict greater-than scan of the reduced leaf vector, then a
     * descent back down the tree resolving which child carried each maximum, with the strict less-than
     * tie-break the native code uses.
     *
     * @param x     the input array
     * @param xLen  the length
     * @return the index of the maximum element
     */
    private static int getMaxi(float[] x, int xLen) {
        float[] buf = new float[MAX_SF_LEN];
        int numHalves = 0;
        int len = (xLen + 1) >> 1;
        for (int i = 0; i < xLen - len; i++) {
            buf[i] = Math.max(x[i], x[i + len]);
        }
        buf[xLen - len] = x[xLen - len];
        int bufPtr = 0;
        while ((len & 1) == 0) {
            bufPtr += len;
            len >>= 1;
            for (int i = 0; i < len; i++) {
                buf[bufPtr + i] = Math.max(buf[bufPtr - 2 * len + i], buf[bufPtr - len + i]);
            }
            numHalves++;
        }
        int idx = 0;
        float maxtmp = buf[bufPtr];
        for (int nn = 1; nn < len; nn++) {
            float xtmp = buf[bufPtr + nn];
            if (xtmp > maxtmp) {
                maxtmp = xtmp;
                idx = nn;
            }
        }
        for (int nn = 0; nn < numHalves; nn++) {
            bufPtr -= 2 * len;
            if (buf[bufPtr + idx] < buf[bufPtr + idx + len]) {
                idx += len;
            }
            len <<= 1;
        }
        if (idx + len < xLen && x[idx] < x[idx + len]) {
            idx += len;
        }
        return idx;
    }

    /**
     * Returns the sorted indices of the {@code K} highest values, {@code smpl_get_maxi_K}.
     *
     * <p>Builds the same pairwise tree reduction as {@link #getMaxi} once, then repeats: scan the leaf vector
     * for its strict argmax, descend the tree recording the path, resolve the final index using the per-leaf
     * flag toggle and the {@code >=} tie-break, then remove the winner by writing {@code -FLT_MAX} into its
     * leaf and re-propagating the maxima back up the recorded path so the next iteration finds the next
     * largest. The leaf flag distinguishes the first from the second visit to a paired leaf.
     *
     * @param x    the input array
     * @param idx  the destination index array, at least {@code K} entries
     * @param xLen the length
     * @param k    the number of indices to return
     */
    private static void getMaxiK(float[] x, int[] idx, int xLen, int k) {
        float[] buf = new float[MAX_SORT_LEN];
        byte[] flags = new byte[MAX_SORT_LEN / 2];
        int[] is = new int[7];
        int numHalves = 0;
        int len = (xLen + 1) >> 1;
        for (int i = 0; i < xLen - len; i++) {
            buf[i] = Math.max(x[i], x[i + len]);
        }
        buf[xLen - len] = x[xLen - len];
        int bufPtr = 0;
        while ((len & 1) == 0) {
            bufPtr += len;
            len >>= 1;
            for (int i = 0; i < len; i++) {
                buf[bufPtr + i] = Math.max(buf[bufPtr - 2 * len + i], buf[bufPtr - len + i]);
            }
            numHalves++;
        }
        for (int kk = 0; kk < k; kk++) {
            int i = 0;
            float maxtmp = buf[bufPtr];
            for (int nn = 1; nn < len; nn++) {
                float xtmp = buf[bufPtr + nn];
                if (xtmp > maxtmp) {
                    maxtmp = xtmp;
                    i = nn;
                }
            }
            for (int nn = 0; nn < numHalves; nn++) {
                is[nn] = i;
                bufPtr -= 2 * len;
                if (buf[bufPtr + i] < buf[bufPtr + i + len]) {
                    i += len;
                }
                len <<= 1;
            }
            float xtmp = -FLT_MAX;
            int iFinal = i;
            if (i + len < xLen) {
                if (flags[i]++ == 0) {
                    if (x[i] < x[i + len]) {
                        xtmp = x[i];
                        iFinal += len;
                    } else {
                        xtmp = x[i + len];
                    }
                } else {
                    if (x[i] >= x[i + len]) {
                        iFinal += len;
                    }
                }
            }
            idx[kk] = iFinal;
            if (kk == k - 1) {
                return;
            }
            buf[bufPtr + i] = xtmp;
            for (int nn = numHalves - 1; nn >= 0; nn--) {
                i = is[nn];
                len >>= 1;
                buf[bufPtr + i + 2 * len] = Math.max(buf[bufPtr + i], buf[bufPtr + i + len]);
                bufPtr += 2 * len;
            }
        }
    }
}
