package com.github.auties00.cobalt.calls2.media.audio.mlow.lsf;

/**
 * Per-subframe line-spectral-frequency (LSF) interpolator and linear-prediction (LPC) stabilizer for the
 * MLow speech codec, the port of {@code smpl_lpc_interpol} ({@code smpl_lpc.c}) and the decode-needed LPC
 * stabilization chain of {@code smpl_lpc_util.c} ({@code smpl_lpc_stabilize}, {@code smpl_lpc_is_stable},
 * {@code smpl_bwe_expand}, {@code smpl_NLSF2A_stabilize}).
 *
 * <p>MLow codes one LSF vector per 20 ms frame, but the short-term synthesis filter is applied per subframe.
 * This class bridges that gap: it interpolates between the previous frame's reconstructed LSF vector and the
 * current frame's vector to produce one LSF vector per subframe, converts each to an LPC filter, and forces
 * each filter to be stable. The interpolation weight per subframe is supplied by the decoder as a fixed
 * factor table row selected by the frame's {@code lsf_interpol_idx}; this class consumes that already
 * resolved per-subframe factor vector.
 *
 * <p>The interpolation carries state across frames. The previous frame's LSF vector is held on this
 * instance and updated after every frame, exactly as the native {@code dec_state->lsf_prev} array is. The
 * first frame of a decode session is a reset: when the held previous vector is all zeros (its last
 * coefficient is {@code 0}), it is seeded from the current frame's vector so the first frame interpolates
 * against itself. Construct one interpolator per logical stream and feed it every frame in order;
 * {@link #reset()} returns it to the freshly constructed state.
 *
 * <p>For each subframe the interpolated vector {@code ilsf} is:
 * <ul>
 * <li>the current frame's LSF vector when the factor is exactly {@code 1.0f};</li>
 * <li>otherwise the convex blend {@code prev_lsf * (1 - factor) + lsf * factor}, computed in that
 * statement order so the {@code float} rounding matches the native {@code smpl_scale_vec} followed by
 * {@code smpl_add_scale_vec_inplace}.</li>
 * </ul>
 * When a subframe's factor equals the immediately preceding subframe's factor, the native code reuses the
 * previous subframe's already computed LPC filter verbatim and does not recompute {@code ilsf}; this class
 * does the same, copying the previous LPC row and leaving the carried {@code ilsf} unchanged. The held
 * previous-frame vector is then updated to the last value of {@code ilsf}, which for a trailing run of
 * repeated factors is the last distinctly computed vector, not the nominal last-subframe vector. That detail
 * is load-bearing for the next frame's interpolation and is reproduced exactly.
 *
 * <p>Each interpolated LSF vector is converted to an LPC filter by the NLSF2A bridge and then stabilized.
 * The LPC-to-LSF conversion itself is the integer fixed-point {@code silk_NLSF2A_32}, supplied by
 * {@link NlsfBridge#nlsf2a(float[])}; the stabilization that follows is this class. Stabilization first
 * tests the filter with the reflection-coefficient stability test {@code smpl_lpc_is_stable}, and if it
 * fails applies progressively stronger bandwidth expansion ({@code A[i] *= bwe^i}) with
 * {@code bwe = 1 - iter * 0.001f} until the test passes, mirroring {@code smpl_lpc_stabilize}.
 *
 * <p>The integer NLSF2A conversion is bit-exact; the float interpolation and the bandwidth-expansion
 * stabilization round identically to the native single-precision arithmetic, so the produced per-subframe
 * LPC filters match the native decoder's to within IEEE-754 rounding (validated to a relative epsilon of
 * {@code 1e-4} against the trace oracle's {@code features_lpc.f32}).
 *
 * <p>Scope is the SMPL 16 kHz, 60 ms, mono low-band decode path with {@code SMPL_LPC_ORDER == 16}. The
 * encoder-side interpolation-index search and the high-band reuse of {@code smpl_lpc_interpol} are out of
 * scope; this class is the low-band decode path only. It is stateful per stream and is not thread-safe.
 *
 * @implNote This implementation merges {@code smpl_lpc_interpol} with the {@code smpl_NLSF2A_stabilize}
 * tail it calls, splitting the NLSF2A conversion out to {@link NlsfBridge} (the integer fixed-point step,
 * a separate track) and keeping the float interpolation and the {@code smpl_lpc_stabilize} chain here. The
 * stability test {@code smpl_lpc_is_stable} is reproduced verbatim, including the {@code MAX_RC_STABLE}
 * threshold of {@code 0.9995f}, the leading-coefficient short-circuit, the double-precision Levinson
 * down-recursion, and the alternating {@code a0}/{@code a1} ping-pong that halves the work. The bandwidth
 * expansion uses a running {@code float} accumulator {@code c} multiplied by {@code bwe} each tap rather
 * than recomputing {@code bwe^i}, matching {@code smpl_bwe_expand} so the rounding is identical. The
 * non-decode {@code bwe <= 0} branch of {@code smpl_bwe_expand} (which zeroes the predictor) is retained
 * for completeness even though the stabilize loop never reaches a non-positive factor.
 */
public final class LpcInterpolator {
    /**
     * Linear-prediction order of the MLow short-term filter, the native {@code SMPL_LPC_ORDER}; the LSF
     * vector length and the number of LPC predictor taps. Each LPC filter row is this many taps plus the
     * leading unity coefficient.
     */
    private static final int LPC_ORDER = 16;

    /**
     * Squared reflection-coefficient stability threshold, the native {@code MAX_RC_STABLE}.
     *
     * <p>A filter is judged unstable when any reflection coefficient of its Levinson down-recursion has a
     * square exceeding this value; {@code 0.9995f} corresponds to a reflection magnitude just under one, the
     * margin the native decoder uses to keep the synthesis filter comfortably inside the unit circle.
     */
    private static final float MAX_RC_STABLE = 0.9995f;

    /**
     * The previous frame's reconstructed LSF vector, the native {@code dec_state->lsf_prev}.
     *
     * <p>Read as the left endpoint of every subframe's interpolation and overwritten after each frame with
     * the last computed interpolated vector. Zero on construction and after {@link #reset()}, which the
     * first frame of a session detects as a reset.
     */
    private final float[] previousLsf;

    /**
     * Constructs a per-subframe LSF interpolator with a zeroed previous-frame vector.
     *
     * <p>The held previous-frame LSF vector starts all zeros, so the first frame fed to
     * {@link #interpolate(float[], float[])} is treated as a reset and interpolates against itself.
     */
    public LpcInterpolator() {
        this.previousLsf = new float[LPC_ORDER];
    }

    /**
     * Returns this interpolator to its freshly constructed state.
     *
     * <p>Zeroes the held previous-frame LSF vector so the next frame is treated as the reset frame. Call
     * this between independent decode sessions; do not call it between the frames of one continuous stream,
     * which must thread the previous-frame vector.
     */
    public void reset() {
        java.util.Arrays.fill(previousLsf, 0.0f);
    }

    /**
     * The per-subframe result of one frame's LSF interpolation and LPC stabilization.
     *
     * <p>{@code lpc[sf]} is the stabilized LPC filter of subframe {@code sf}, {@value #LPC_ORDER}{@code  + 1}
     * taps with a leading unity coefficient; it is the native {@code A[sf]} the synthesis filter consumes,
     * and its negated tail ({@code -lpc[sf][i + 1]}) is what the trace oracle dumps to
     * {@code features_lpc.f32}. {@code lsf[sf]} is the interpolated line-spectral-frequency vector of
     * subframe {@code sf}, {@value #LPC_ORDER} entries, the native {@code lsfs[sf]} that feeds the noise
     * generator and the comfort-noise model. For a trailing run of subframes sharing one interpolation
     * factor, each repeated {@code lsf[sf]} holds the same carried vector as its predecessor, matching the
     * native reuse.
     *
     * @param lpc the per-subframe stabilized LPC filters, indexed {@code lpc[subframe][tap]}
     * @param lsf the per-subframe interpolated LSF vectors, indexed {@code lsf[subframe][coefficient]}
     */
    public record InterpolatedFrame(float[][] lpc, float[][] lsf) {
    }

    /**
     * Interpolates one frame's LSF vector to per-subframe LSF and stabilized LPC, advancing the carried
     * previous-frame state, the port of {@code smpl_lpc_interpol}.
     *
     * <p>If this is the reset frame (the held previous-frame vector is all zeros) the previous-frame vector
     * is first seeded from {@code lsf} so the frame interpolates against itself. Then, for each subframe in
     * order, the interpolation factor {@code interpol[sf]} selects the interpolated vector: the current
     * vector when the factor is {@code 1.0f}, the convex blend of the previous-frame and current vectors
     * otherwise, or a verbatim reuse of the previous subframe's filter and vector when the factor repeats.
     * Each freshly interpolated vector is converted to an LPC filter through {@link NlsfBridge#nlsf2a(float[])}
     * and stabilized in place. After the loop the held previous-frame vector is updated to the last
     * interpolated vector, ready for the next frame.
     *
     * @param lsf      the current frame's reconstructed LSF vector, {@value #LPC_ORDER} entries, as produced
     *                 by {@link LsfDequantizer#dequantize(int[], int, int, float[])}
     * @param interpol the per-subframe interpolation factors, the native
     *                 {@code smpl_lsf_interpol_N[lsf_interpol_idx]} row; its length is the subframe count
     * @return the per-subframe stabilized LPC filters and interpolated LSF vectors
     */
    public InterpolatedFrame interpolate(float[] lsf, float[] interpol) {
        if (previousLsf[LPC_ORDER - 1] == 0.0f) {
            System.arraycopy(lsf, 0, previousLsf, 0, LPC_ORDER);
        }
        int numSubfr = interpol.length;
        float[][] a = new float[numSubfr][];
        float[][] lsfs = new float[numSubfr][];
        float[] ilsf = new float[LPC_ORDER];
        float prevFactor = -1.0f;
        for (int j = 0; j < numSubfr; j++) {
            float factor = interpol[j];
            if (factor == prevFactor) {
                // Repeated factor reuses the prior subframe's filter and interpolated vector verbatim. Both
                // rows are read-only downstream (ar16 reads the coefficients, genNoise reads only lsf[0]/lsf[1],
                // and lpcOut takes its own clone) and are never pooled as scratch, so aliasing the prior rows
                // is bit-identical to cloning them.
                a[j] = a[j - 1];
                lsfs[j] = lsfs[j - 1];
            } else {
                if (factor == 1.0f) {
                    System.arraycopy(lsf, 0, ilsf, 0, LPC_ORDER);
                } else {
                    float oneMinus = 1.0f - factor;
                    for (int i = 0; i < LPC_ORDER; i++) {
                        ilsf[i] = previousLsf[i] * oneMinus;
                    }
                    for (int i = 0; i < LPC_ORDER; i++) {
                        ilsf[i] += factor * lsf[i];
                    }
                }
                a[j] = nlsf2aStabilize(ilsf);
                lsfs[j] = ilsf.clone();
            }
            prevFactor = factor;
        }
        System.arraycopy(ilsf, 0, previousLsf, 0, LPC_ORDER);
        return new InterpolatedFrame(a, lsfs);
    }

    /**
     * Converts an interpolated LSF vector to a stabilized LPC filter, the port of
     * {@code smpl_NLSF2A_stabilize}.
     *
     * <p>Runs the integer fixed-point NLSF2A conversion through {@link NlsfBridge#nlsf2a(float[])} and then
     * stabilizes the resulting filter in place with {@link #stabilize(float[])}.
     *
     * @param ilsf the interpolated LSF vector of one subframe, {@value #LPC_ORDER} entries
     * @return a freshly allocated stabilized LPC filter, {@value #LPC_ORDER}{@code  + 1} taps with a leading
     *         unity coefficient
     */
    private static float[] nlsf2aStabilize(float[] ilsf) {
        float[] a = NlsfBridge.nlsf2a(ilsf);
        stabilize(a);
        return a;
    }

    /**
     * Forces an LPC filter to be stable in place, the port of {@code smpl_lpc_stabilize}.
     *
     * <p>Tests the filter with {@link #isStable(float[])}; if it is already stable the filter is left
     * untouched. Otherwise progressively stronger bandwidth expansion is applied, the expansion factor
     * decreasing by {@code 0.001f} each iteration ({@code bwe = 1 - iter * 0.001f}), until the stability
     * test passes. The loop is guaranteed to terminate because the expansion drives every reflection
     * coefficient toward zero.
     *
     * @param a the LPC filter to stabilize in place, {@value #LPC_ORDER}{@code  + 1} taps
     */
    private static void stabilize(float[] a) {
        if (isStable(a)) {
            return;
        }
        int iter = 0;
        do {
            iter++;
            bweExpand(a, 1.0f - iter * 0.001f);
        } while (!isStable(a));
    }

    /**
     * Applies bandwidth expansion to an LPC filter in place, the port of {@code smpl_bwe_expand}.
     *
     * <p>Scales tap {@code i} (one-based, the leading unity coefficient untouched) by {@code bwe^i} using a
     * running accumulator: {@code A[1] *= bwe}, {@code A[2] *= bwe^2}, and so on. A non-positive {@code bwe}
     * zeroes the entire predictor; the decode stabilize loop never supplies such a value, but the branch is
     * retained to match the native function exactly.
     *
     * @param a   the LPC filter to expand in place, {@value #LPC_ORDER}{@code  + 1} taps
     * @param bwe the bandwidth-expansion factor, in {@code (0, 1)} for the decode path
     */
    private static void bweExpand(float[] a, float bwe) {
        if (bwe <= 0.0f) {
            for (int i = 1; i < LPC_ORDER + 1; i++) {
                a[i] = 0.0f;
            }
            return;
        }
        float c = bwe;
        for (int i = 1; i < LPC_ORDER + 1; i++) {
            a[i] *= c;
            c *= bwe;
        }
    }

    /**
     * Tests whether an LPC synthesis filter is stable, the port of {@code smpl_lpc_is_stable}.
     *
     * <p>Short-circuits to unstable when the last predictor tap's square exceeds {@link #MAX_RC_STABLE},
     * then runs a double-precision Levinson down-recursion: at each order it forms the next-lower-order
     * predictor from the current one, divided by {@code 1 - tail^2}, and rejects the filter when the
     * division denominator is zero or when the new leading reflection coefficient's square exceeds the
     * threshold. The recursion alternates between two scratch buffers ({@code a0} and {@code a1}) so each
     * pass reuses the other's output, exactly as the native code does to avoid copying; the filter is stable
     * when the recursion reaches order zero without a rejection.
     *
     * @param a the LPC filter to test, {@value #LPC_ORDER}{@code  + 1} taps with a leading unity coefficient
     * @return {@code true} when the filter is stable, {@code false} otherwise
     */
    private static boolean isStable(float[] a) {
        if (a[LPC_ORDER] * a[LPC_ORDER] > MAX_RC_STABLE) {
            return false;
        }
        double[] a0 = new double[LPC_ORDER];
        double[] a1 = new double[LPC_ORDER];
        for (int i = 0; i < LPC_ORDER; i++) {
            a0[i] = a[i + 1];
        }
        int m = LPC_ORDER - 1;
        while (true) {
            double den = 1.0 - a0[m] * a0[m];
            if (den == 0.0) {
                return false;
            }
            double invDen = 1.0 / den;
            for (int k = 0; k < m; k++) {
                a1[k] = (a0[k] - a0[m] * a0[m - k - 1]) * invDen;
            }
            if (a1[m - 1] * a1[m - 1] > MAX_RC_STABLE) {
                return false;
            }
            if (--m == 0) {
                return true;
            }
            den = 1.0 - a1[m] * a1[m];
            if (den == 0.0) {
                return false;
            }
            invDen = 1.0 / den;
            for (int k = 0; k < m; k++) {
                a0[k] = (a1[k] - a1[m] * a1[m - k - 1]) * invDen;
            }
            if (a0[m - 1] * a0[m - 1] > MAX_RC_STABLE) {
                return false;
            }
            if (--m == 0) {
                return true;
            }
        }
    }
}
