package com.github.auties00.cobalt.calls2.media.audio.mlow.encode;

import com.github.auties00.cobalt.calls2.media.audio.mlow.tables.EncoderTables;
import com.github.auties00.cobalt.calls2.media.audio.mlow.tables.MiscTables;

/**
 * Per-subframe target-bitrate controller of the MLow speech encoder, the port of
 * {@code smpl_bitrate_controller.c}.
 *
 * <p>The native controller is the rate-allocation brain that sits between the rate state set by the encoder API
 * ({@code mainBitRate} / {@code fecBitRate}, derived by the in-band-FEC rate split {@link #controlLbrr}) and the
 * analysis-by-synthesis (AbS) CELP search. For every subframe of every frame the core encoder asks this object
 * two things: how many fixed-codebook pulses the subframe may spend ({@code max_pulses_per_subfr}) and how
 * perceptually important the subframe is relative to its neighbours ({@code subfr_importance}). The pulse budget
 * caps the CELP pulse search; the importance scales the rate-distortion weight the search trades bits against.
 * Together they steer the instantaneous bitrate toward the configured target without a hard per-frame bit cap.
 *
 * <p>The allocation is a model plus a feedback loop:
 * <ul>
 * <li>{@link #control} evaluates the open-loop pulse-target model {@code bitrate2pulses} (a degree-four
 * polynomial plus an exponential tail, with coefficients {@link EncoderTables#RATE_CONTROL_MODEL_COMP5}) to turn
 * the de-banded kbit/s target into a pulses-per-20 ms ceiling, converts that to a per-subframe pulse cap, then
 * forms the subframe importance from the smoothed weighted energy, the voicing and non-flatness features, the
 * speech-activity probability, and the running feedback scale. It also seeds the one-shot per-rate
 * {@code rate_cont_bitrate_scale} the first time a target bitrate is seen.</li>
 * <li>{@link #updateScale} closes the loop after the frame is coded: it compares the bits actually spent against
 * the target, integrates the relative error into a clamped smoothed delta, and turns that into the multiplicative
 * {@code adjustment_factor} the next {@link #control} call applies. Inactive (non-active-voice) frames decay the
 * smoothed delta back toward neutral instead.</li>
 * </ul>
 * The two non-flatness helpers {@link #nonflatness} and {@link #hrNonflatThres} are part of the same translation
 * unit: the encoder uses them to decide the unvoiced flatness threshold that, with the controller's pulse caps,
 * gates whether an unvoiced subframe is coded with pulses at all.
 *
 * <p>The high-versus-low-rate path is selected upstream by the encoder ({@code mainBitRate <= low_rate_thr}); the
 * controller receives the decision as the {@code lowRate} flag and indexes the model and threshold tables by it.
 * Scope is the SMPL 16 kHz / 60 ms / mono high-rate path (9600 bps, payload bucket {@value #FRAMELEN_IDX_60MS});
 * a 60 ms packet is three 20 ms frames of four 80-sample subframes each. There is no in-band FEC on this path, so
 * {@code fecBitRate} is zero and the per-rate loops collapse to the single main rate point
 * ({@value #IDX_MAIN}); the FEC point ({@value #IDX_FEC}) is carried for structural fidelity and is never
 * populated on this scope.
 *
 * <p>One controller instance carries the feedback state of a single logical stream (the native
 * {@code BitrateController}); construct one per base encoder, call {@link #control} for every subframe in order,
 * and call {@link #updateScale} once per coded frame. The pure model helpers ({@link #hrNonflatThres},
 * {@link #nonflatness}, {@link #controlLbrr}) are static and stateless. This type is stateful and is not
 * thread-safe.
 *
 * @implNote This implementation mirrors the native field-for-field state and reproduces every {@code float}
 * accumulation in source order. The native {@code smpl_bitrate_controller.c} is compiled with the default
 * optimization level, not the {@code -Ofast}/{@code -ffast-math} group, so its own reduction loops are strict
 * left-to-right single precision and are ported as written; only the two helpers it calls into,
 * {@code smpl_nrg} and {@code smpl_sum_vec} from the fast-math {@code smpl_codec_util.c}, are reproduced through
 * {@link #nrg} and {@code sum}. The model polynomial uses {@code powf} (libm, not the fast bit-hack), so the
 * cubic and quartic powers and the exponential tail are evaluated with {@code (float) Math.pow}. The
 * rate-class loop start index reproduces the native expression
 * {@code SMPL_CELP_IDX_FEC + (fecBitRate == 0) || (fecBitRate == mainBitRate)} verbatim, including its C operator
 * precedence: {@code +} binds tighter than {@code ||}, so the whole expression is a boolean that is {@code 1}
 * whenever {@code fecBitRate} is zero (the only case on this scope), making the loop run the single main rate
 * point. The model coefficients are stored as {@code double} in {@link EncoderTables#RATE_CONTROL_MODEL_COMP5}
 * but the native {@code bitrate2pulses} reads them as {@code float}, so each coefficient is narrowed to
 * {@code float} before use to match the native rounding. Validated byte-exact against a gcc oracle wrapping the
 * real {@code smpl_bitrate_controller.c}: the pulse caps, the importance values, and the full feedback state
 * ({@code rate_cont_wnrg_smth}, {@code rate_cont_bitrate_scale}, {@code rate_cont_bitrate},
 * {@code bitrate_delta_smth}, {@code adjustment_factor}) match bit-for-bit across an eight-packet sequence of
 * voiced and unvoiced, active and inactive frames at 9600 bps and across the {@link #hrNonflatThres},
 * {@link #nonflatness}, and {@link #controlLbrr} sweeps.
 */
public final class BitrateController {
    /**
     * Number of rate points the controller allocates over, the native {@code SMPL_CELP_MAX_RATES}.
     */
    private static final int MAX_RATES = 2;

    /**
     * Index of the in-band-FEC rate point, the native {@code SMPL_CELP_IDX_FEC}.
     */
    private static final int IDX_FEC = 0;

    /**
     * Index of the main rate point, the native {@code SMPL_CELP_IDX_MAIN}.
     */
    private static final int IDX_MAIN = 1;

    /**
     * Linear-prediction order of the MLow short-term filter, the native {@code SMPL_LPC_ORDER}; the length of
     * the weighted-LSF vector the non-flatness measure folds in.
     */
    private static final int LPC_ORDER = 16;

    /**
     * Number of cross-frame non-flatness energy-state slots, the native {@code SMPL_NON_FLAT_STATE_LEN}.
     */
    private static final int NON_FLAT_STATE_LEN = 5;

    /**
     * Sub-block length of the non-flatness energy partition, the native {@code SMPL_NON_FLAT_SUBFR_LEN}.
     */
    private static final int NON_FLAT_SUBFR_LEN = 16;

    /**
     * Length of the non-flatness energy scratch, the native {@code SMPL_NON_FLAT_NRGS_LEN}; the maximum frame
     * length {@code SMPL_FRAME_LEN} of 320 divided by {@value #NON_FLAT_SUBFR_LEN} plus the state length.
     */
    private static final int NON_FLAT_NRGS_LEN = (320 / NON_FLAT_SUBFR_LEN) + NON_FLAT_STATE_LEN;

    /**
     * Ceiling on the unvoiced non-flatness threshold, the native {@code SMPL_UV_NONFLATNESS_THR}.
     */
    private static final float UV_NONFLATNESS_THR = 0.5f;

    /**
     * Per-subframe pulse-budget ceiling, the native {@code SMPL_MAX_PULSES_PER_SF}.
     */
    private static final int MAX_PULSES_PER_SF = 40;

    /**
     * Euler's number as the native single-precision literal {@code SMPL_E}, used by the model exponential tail.
     */
    private static final float E = 2.7182818284590f;

    /**
     * Payload-length bucket index for a 60 ms packet, the native {@code framelen_idx} value of 2.
     */
    private static final int FRAMELEN_IDX_60MS = 2;

    /**
     * One-shot bitrate-scale gain seeding the rate-control loop, the native {@code SMPL_RATE_CONT_SCALE}.
     */
    private static final float RATE_CONT_SCALE = 26.0f;

    /**
     * Maximum smoothed bitrate delta, the native {@code SMPL_RATE_CONT_CLAMP_MAX}.
     */
    private static final float RATE_CONT_CLAMP_MAX = 0.9f;

    /**
     * Minimum smoothed bitrate delta, the native {@code SMPL_RATE_CONT_CLAMP_MIN}.
     */
    private static final float RATE_CONT_CLAMP_MIN = -0.3f;

    /**
     * Integration gain on the feedback loop, the native {@code SMPL_RATE_CONT_GAIN}.
     */
    private static final float RATE_CONT_GAIN = 0.05f;

    /**
     * Rate-control compensation knee in kbit/s for the FEC pulse-target model, the native
     * {@code RATE_THRES_KBPS}.
     */
    private static final float RATE_THRES_KBPS = 9.0f;

    /**
     * The two bitrate anchors, in bits per second, of the high-rate unvoiced non-flatness threshold line,
     * the native {@code (10000, 18000)} pair {@link #hrNonflatThres} interpolates between.
     */
    private static final float[] HR_NONFLAT_BITRATES = {10000.0f, 18000.0f};

    /**
     * The two threshold anchors of the high-rate unvoiced non-flatness threshold line, the native
     * {@code (0.5, 0.0)} pair {@link #hrNonflatThres} interpolates between.
     */
    private static final float[] HR_NONFLAT_THRESHOLDS = {0.5f, 0.0f};

    /**
     * The previous frame's voicing decision, the native {@code prev_voiced}.
     *
     * <p>Read to bump the importance on a voicing transition and overwritten at the end of each rate-point loop
     * iteration; starts zeroed.
     */
    private int prevVoiced;

    /**
     * The smoothed weighted-energy running mean, the native {@code rate_cont_wnrg_smth}.
     *
     * <p>A first-order leaky integrator of the per-subframe weighted energy; the importance denominator. Starts
     * zeroed.
     */
    private float rateContWnrgSmth;

    /**
     * The per-rate one-shot bitrate scale, the native {@code rate_cont_bitrate_scale}.
     *
     * <p>Seeded the first time each rate point's target bitrate is seen (when {@link #rateContBitrate} differs)
     * and held thereafter; the final multiplicative factor on the importance. Starts zeroed.
     */
    private final float[] rateContBitrateScale;

    /**
     * The per-rate smoothed bitrate delta, the native {@code bitrate_delta_smth}.
     *
     * <p>The clamped integral of the relative bits-spent error that {@link #updateScale} maintains and that the
     * adjustment factor is derived from. Starts zeroed.
     */
    private final float[] bitrateDeltaSmth;

    /**
     * The per-rate target bitrate last seen, the native {@code rate_cont_bitrate}.
     *
     * <p>Guards the one-shot {@link #rateContBitrateScale} seeding; reseeds the scale whenever the target
     * bitrate changes. Starts zeroed.
     */
    private final float[] rateContBitrate;

    /**
     * The per-rate feedback adjustment factor, the native {@code adjustment_factor}.
     *
     * <p>The multiplicative correction {@code max(1 - bitrate_delta_smth, 0)} that the next {@link #control}
     * folds into the importance. Initialized to {@code 1.0} for every rate point by {@link #init}.
     */
    private final float[] adjustmentFactor;

    /**
     * The per-subframe rate-allocation result of one {@link #control} call.
     *
     * <p>{@code maxPulsesPerSubfr[r]} is the fixed-codebook pulse budget for rate point {@code r} (the native
     * {@code max_pulses_per_subfr}); {@code subfrImportance[r]} is the perceptual importance weight (the native
     * {@code subfr_importance}). Both arrays have {@value #MAX_RATES} entries indexed by rate point; on the
     * 9600 bps scope only the main point {@value #IDX_MAIN} is populated and the FEC point {@value #IDX_FEC}
     * stays zero.
     *
     * @param maxPulsesPerSubfr the per-rate fixed-codebook pulse budget
     * @param subfrImportance   the per-rate subframe importance weight
     */
    public record Allocation(short[] maxPulsesPerSubfr, float[] subfrImportance) {
    }

    /**
     * The {@code mainBitRate} / {@code fecBitRate} rate split produced by {@link #controlLbrr}.
     *
     * @param mainBitRate the main-payload bitrate in bits per second, the native {@code mainBitRate}
     * @param fecBitRate  the in-band-FEC bitrate in bits per second, the native {@code fecBitRate}
     */
    public record RateSplit(int mainBitRate, int fecBitRate) {
    }

    /**
     * Constructs a controller in the native post-{@code bitrate_controller_init} state.
     *
     * <p>Zeroes the feedback state (as the native encoder zeroes the whole struct on init) and sets every rate
     * point's adjustment factor to {@code 1.0}, the neutral correction.
     */
    public BitrateController() {
        this.rateContBitrateScale = new float[MAX_RATES];
        this.bitrateDeltaSmth = new float[MAX_RATES];
        this.rateContBitrate = new float[MAX_RATES];
        this.adjustmentFactor = new float[MAX_RATES];
        init();
    }

    /**
     * Resets the controller to the native post-{@code bitrate_controller_init} state, {@code bitrate_controller_init}.
     *
     * <p>Zeroes every feedback field and sets each rate point's adjustment factor to {@code 1.0}. The native
     * init only writes the adjustment factors; the remaining fields are zero because the encoder zeroes the
     * whole state on construction, which this reproduces. Call between independent streams.
     */
    public void init() {
        prevVoiced = 0;
        rateContWnrgSmth = 0.0f;
        for (int r = 0; r < MAX_RATES; r++) {
            rateContBitrateScale[r] = 0.0f;
            bitrateDeltaSmth[r] = 0.0f;
            rateContBitrate[r] = 0.0f;
            adjustmentFactor[r] = 1.0f;
        }
    }

    /**
     * Computes the per-subframe pulse budget and perceptual importance for one subframe, {@code bitrate_controller}.
     *
     * <p>Updates the smoothed weighted-energy mean, then for each active rate point evaluates the pulse-target
     * model, derives the per-subframe pulse cap (scaled by speech activity and bounded by the per-frame pulse
     * PDF), and forms the importance from the weighted energies, the voicing and non-flatness features, the
     * speech-activity probability, the voicing-transition bump, the voicing-strength damping, the
     * speech-activity importance factor, and the running feedback scale. On this scope the loop runs only the
     * main rate point.
     *
     * @param dtxSidFrame              {@code true} when the current frame is a discontinuous-transmission SID
     *                                 frame, the native {@code dtx->sid_frame}
     * @param codedAsActiveVoice       {@code true} when the frame is coded as active voice, the native
     *                                 {@code coded_as_active_voice}
     * @param spActProb                the speech-activity probability in {@code [0, 1]}, the native
     *                                 {@code sp_act_prob}
     * @param nonflatness              the subframe spectral non-flatness, the native {@code nonflatness}
     * @param voicingStrength          the subframe voicing strength, the native {@code voicing_strength}
     * @param voiced                   {@code 1} for a voiced frame, {@code 0} otherwise, the native
     *                                 {@code voiced}
     * @param wnrg                     the subframe weighted energy, the native {@code wnrg}
     * @param wnrgNext                 the next subframe's weighted energy, the native {@code wnrg_next}
     * @param lowRate                  {@code true} on the low-rate path, {@code false} on high rate, the native
     *                                 {@code low_rate}
     * @param framelen                 the frame length in samples, the native {@code framelen}
     * @param subfrlen                 the subframe length in samples, the native {@code subfrlen}
     * @param internalSampleRate       the internal sample rate in Hertz, the native
     *                                 {@code enc_status->internalSampleRate}
     * @param payloadSizeMs            the packet payload size in milliseconds, the native
     *                                 {@code enc_status->payloadSize_ms}
     * @param fecBitRate               the in-band-FEC bitrate in bits per second, the native
     *                                 {@code enc_status->fecBitRate}
     * @param mainBitRate              the main bitrate in bits per second, the native
     *                                 {@code enc_status->mainBitRate}
     * @param complexity               the encoder complexity setting, the native {@code enc_status->complexity}
     * @param useDtx                   {@code true} when discontinuous transmission is enabled, the native
     *                                 {@code enc_status->useDTX}
     * @param useFecRateCompensation   {@code true} when the FEC rate-compensation gate is enabled, the native
     *                                 {@code enc_status->useFecRateCompensation}
     * @param subFrameImportanceFactor the speech-activity importance shaping factor, the native
     *                                 {@code enc_status->subFrameImportanceFactor}
     * @return the per-rate pulse budget and importance for the subframe
     */
    public Allocation control(boolean dtxSidFrame, boolean codedAsActiveVoice, float spActProb, float nonflatness,
                              float voicingStrength, int voiced, float wnrg, float wnrgNext, boolean lowRate,
                              int framelen, int subfrlen, int internalSampleRate, int payloadSizeMs,
                              int fecBitRate, int mainBitRate, int complexity, boolean useDtx,
                              boolean useFecRateCompensation, float subFrameImportanceFactor) {
        int lowRateIdx = lowRate ? 1 : 0;
        // The model and threshold tables index the rate class as the native (low_rate ? 0 : 1), the inverse of
        // the plain low-rate flag; only smpl_max_pulses_per_frame uses the flag directly.
        int modelIdx = lowRate ? 0 : 1;

        int bweBitrate = 0;
        if (internalSampleRate > 16000) {
            bweBitrate += lowRate ? 450 : 750;
            bweBitrate += payloadSizeMs == 10 ? 450 : 0;
        }

        rateContWnrgSmth += 0.6f * (wnrg - rateContWnrgSmth);

        int framelenIdx = (payloadSizeMs == 10) ? 0
                : payloadSizeMs == 20 ? 1
                : payloadSizeMs == 60 ? 2 : 3;

        short[] maxPulsesPerSubfr = new short[MAX_RATES];
        float[] subfrImportance = new float[MAX_RATES];

        int startR = startRate(fecBitRate, mainBitRate);
        for (int r = startR; r <= IDX_MAIN; r++) {
            float bitRate = (r == IDX_FEC) ? (float) fecBitRate : (float) mainBitRate;
            bitRate = Math.min(bitRate, 30000.0f);
            float rateKbps = (bitRate - bweBitrate) / 1000.0f;
            if (!lowRate) {
                rateKbps *= complexity == 1 ? 0.9900990f
                        : complexity == 2 ? 0.9900990f
                        : complexity == 3 ? 1.0101010f
                        : complexity == 4 ? 1.0101010f
                        : 1.0f;
            }
            float pulsesPer20msTargetMax;
            float rateControlThrs = EncoderTables.RATE_CONTROL_THRS_COMP5[framelenIdx][modelIdx];
            if (bitRate - bweBitrate < rateControlThrs) {
                pulsesPer20msTargetMax = 1.0f;
            } else {
                double[] coeff = EncoderTables.RATE_CONTROL_MODEL_COMP5[framelenIdx][modelIdx];
                if ((r == IDX_FEC) && !lowRate && useFecRateCompensation) {
                    pulsesPer20msTargetMax = Math.max(bitrate2pulsesHrFec(rateKbps, coeff, rateControlThrs), 1.0f);
                } else {
                    pulsesPer20msTargetMax = Math.max(bitrate2pulses(rateKbps, coeff), 1.0f);
                }
            }

            float relPulserate = pulsesPer20msTargetMax / 16.0f * (320.0f / framelen);
            float relPulserateLog = (float) Math.log(relPulserate);
            if (rateContBitrate[r] != bitRate) {
                float bitrateScale = RATE_CONT_SCALE * relPulserate * (1 + 0.4f * relPulserateLog * relPulserateLog);
                rateContBitrateScale[r] = bitrateScale;
                rateContBitrate[r] = bitRate;
            }

            int numsubfrs = framelen / subfrlen;
            maxPulsesPerSubfr[r] = (short) (1 + (int) rint(pulsesPer20msTargetMax * (1 + 0.5f) / numsubfrs));
            if (useDtx && dtxSidFrame) {
                maxPulsesPerSubfr[r] = 0;
            } else {
                maxPulsesPerSubfr[r] = (short) rint(maxPulsesPerSubfr[r] * (0.5f + 0.5f * (float) Math.sqrt(spActProb + 1e-12f)));
                int frameType = !codedAsActiveVoice ? 0 : (voiced == 1) ? 2 : 1;
                int maxPulses = MiscTables.MAX_PULSES_PER_FRAME[lowRateIdx][frameType] * framelen / 320;
                maxPulsesPerSubfr[r] = (short) Math.min(maxPulsesPerSubfr[r], maxPulses / numsubfrs);
            }

            float importance = (wnrg + 0.01f * wnrgNext) / (rateContWnrgSmth + 0.02f * wnrgNext + 1e-12f);
            if (voiced != 0) {
                if (bitRate <= 9000) {
                    importance = (float) Math.sqrt(importance + 1e-12f);
                }
            } else {
                importance *= 0.9f + 0.3f * sigmoid(nonflatness - 2.0f);
                importance *= 0.8f;
            }
            if (voiced != prevVoiced) {
                importance *= 1.1f;
            }
            importance *= 0.9f + 0.3f * 1.0f / (1.0f + 25.0f * voicingStrength * voicingStrength);

            float impFactor = subFrameImportanceFactor;
            if (impFactor <= 1.0f) {
                importance *= (1 - impFactor) + impFactor * (float) Math.sqrt(spActProb + 1e-12f);
            } else if (impFactor <= 2.0f) {
                impFactor -= 1;
                importance *= (1 - impFactor) + impFactor * spActProb;
            } else {
                impFactor -= 2;
                importance *= (1 - impFactor) + impFactor * spActProb * spActProb;
            }
            importance *= adjustmentFactor[r] * rateContBitrateScale[r];
            subfrImportance[r] = importance;
            prevVoiced = voiced;
        }

        return new Allocation(maxPulsesPerSubfr, subfrImportance);
    }

    /**
     * Closes the feedback loop after a frame is coded, {@code bitrate_controller_update_scale}.
     *
     * <p>For each active rate point either decays the smoothed bitrate delta toward neutral (inactive frames) or
     * integrates the relative error between the measured and target bitrate into the clamped smoothed delta and
     * recomputes the adjustment factor {@code max(1 - bitrate_delta_smth, 0)}. The measured bitrate splits the
     * per-packet TOC and byte-rounding overhead evenly across the active rate points.
     *
     * @param frameMs            the frame length in milliseconds, the native {@code frame_ms}
     * @param framesPerPacket    the number of frames per packet, the native {@code frames_per_packet}
     * @param bitsUsed           the bits spent per rate point this frame, the native {@code bits_used}, indexed
     *                           by rate point with {@value #MAX_RATES} entries
     * @param fecBitRate         the in-band-FEC bitrate in bits per second, the native
     *                           {@code enc_status->fecBitRate}
     * @param mainBitRate        the main bitrate in bits per second, the native {@code enc_status->mainBitRate}
     * @param codedAsActiveVoice {@code true} when the frame was coded as active voice, the native
     *                           {@code coded_as_active_voice}
     */
    public void updateScale(int frameMs, int framesPerPacket, float[] bitsUsed, int fecBitRate, int mainBitRate,
                            boolean codedAsActiveVoice) {
        int startR = startRate(fecBitRate, mainBitRate);
        float externalBits = 8.0f / (float) framesPerPacket / (MAX_RATES - startR);
        externalBits += 4.5f / (float) framesPerPacket / (MAX_RATES - startR);
        for (int r = startR; r <= IDX_MAIN; r++) {
            if (!codedAsActiveVoice) {
                float smthCoef = 1.0f - (float) frameMs * 0.00125f;
                bitrateDeltaSmth[r] *= smthCoef;
            } else {
                float bitRate = (r == IDX_FEC) ? (float) fecBitRate : (float) mainBitRate;
                float measuredBitrate = (bitsUsed[r] + externalBits) * (1000.0f / (float) frameMs);
                float measuredBitrateDelta = (measuredBitrate - bitRate) / bitRate;
                bitrateDeltaSmth[r] += measuredBitrateDelta * RATE_CONT_GAIN * frameMs / 20.0f;
                bitrateDeltaSmth[r] = Math.max(Math.min(bitrateDeltaSmth[r], RATE_CONT_CLAMP_MAX), RATE_CONT_CLAMP_MIN);
                adjustmentFactor[r] = Math.max(1.0f - bitrateDeltaSmth[r], 0.0f);
            }
        }
    }

    /**
     * Returns the rate-point loop start index, the native expression
     * {@code SMPL_CELP_IDX_FEC + (fecBitRate == 0) || (fecBitRate == mainBitRate)}.
     *
     * <p>Reproduces the native C operator precedence exactly: {@code +} binds tighter than {@code ||}, so the
     * expression evaluates {@code (IDX_FEC + (fecBitRate == 0)) || (fecBitRate == mainBitRate)} and yields a
     * boolean {@code 0} or {@code 1}, not an arithmetic sum. It is {@code 1} (the main rate point) whenever
     * {@code fecBitRate} is zero or equals {@code mainBitRate}, which is always the case on the no-FEC scope.
     *
     * @param fecBitRate  the in-band-FEC bitrate in bits per second
     * @param mainBitRate the main bitrate in bits per second
     * @return {@value #IDX_FEC} when the FEC point is active, {@value #IDX_MAIN} otherwise
     */
    private static int startRate(int fecBitRate, int mainBitRate) {
        boolean expr = (IDX_FEC + (fecBitRate == 0 ? 1 : 0)) != 0 || (fecBitRate == mainBitRate);
        return expr ? 1 : 0;
    }

    /**
     * Evaluates the pulse-target model, {@code bitrate2pulses}.
     *
     * <p>A degree-four polynomial in the kbit/s target plus a single exponential tail. The coefficients are read
     * as {@code float} (narrowed from the {@code double} table) to match the native {@code const float*} reads,
     * and the cubic, quartic, and exponential terms use {@code powf} (libm), reproduced with
     * {@code (float) Math.pow}.
     *
     * @param rateKbps the de-banded target bitrate in kbit/s, the native {@code rate_kbps}
     * @param coeff    the eight model coefficients for the payload bucket and rate class
     * @return the modelled pulses-per-20 ms target
     */
    private static float bitrate2pulses(float rateKbps, double[] coeff) {
        float c0 = (float) coeff[0];
        float c1 = (float) coeff[1];
        float c2 = (float) coeff[2];
        float c3 = (float) coeff[3];
        float c4 = (float) coeff[4];
        float c5 = (float) coeff[5];
        float c6 = (float) coeff[6];
        float c7 = (float) coeff[7];
        return c0
                + c1 * rateKbps
                + c2 * rateKbps * rateKbps
                + c3 * (float) Math.pow(rateKbps, 3.0f)
                + c4 * (float) Math.pow(rateKbps, 4.0f)
                + c5 * (float) Math.pow(E, (rateKbps - c6) * c7);
    }

    /**
     * Evaluates the FEC pulse-target model with low-rate compensation, {@code bitrate2pulses_hr_fec}.
     *
     * <p>Above {@value #RATE_THRES_KBPS} kbit/s this is the plain model; below it the target is interpolated
     * between the one-pulse rate and the model value at the knee so the FEC stream does not undershoot to a
     * single pulse too early. The {@code one_pulse_rate_bps} argument is the rate-control threshold for the
     * payload bucket and rate class.
     *
     * @param rateKbps        the de-banded target bitrate in kbit/s, the native {@code rate_kbps}
     * @param coeff           the eight model coefficients for the payload bucket and rate class
     * @param onePulseRateBps the one-pulse rate threshold in bits per second, the native
     *                        {@code one_pulse_rate_bps}
     * @return the compensated pulses-per-20 ms target
     */
    private static float bitrate2pulsesHrFec(float rateKbps, double[] coeff, float onePulseRateBps) {
        if (rateKbps >= RATE_THRES_KBPS) {
            return bitrate2pulses(rateKbps, coeff);
        } else if (onePulseRateBps >= RATE_THRES_KBPS * 1000.0f) {
            return 1.0f;
        } else {
            float pulsesThres = bitrate2pulses(RATE_THRES_KBPS, coeff);
            float sc = (RATE_THRES_KBPS - rateKbps) / (RATE_THRES_KBPS - onePulseRateBps / 1000.0f);
            return pulsesThres - sc * (pulsesThres - 1.0f);
        }
    }

    /**
     * Computes the high-rate unvoiced non-flatness threshold, {@code smpl_get_hr_nonflat_thres}.
     *
     * <p>A line through {@code (10000 bps, 0.5)} and {@code (18000 bps, 0.0)} evaluated at the target bitrate,
     * with the bitrate first scaled by the square root of the speech-activity probability (the native
     * {@code SMPL_UV_NONFLATNESS_SA} build option is on for this scope), then clamped to
     * {@code [0, SMPL_UV_NONFLATNESS_THR]}. The scaling truncates back to {@code int} because the native
     * {@code bitrate} parameter is an {@code int} compound-assigned a {@code float}, so the line is evaluated at
     * the truncated bitrate.
     *
     * @param bitrate   the target bitrate in bits per second, the native {@code bitrate}
     * @param spActProb the speech-activity probability, the native {@code sp_act_prob}; must be non-negative
     * @return the clamped non-flatness threshold
     */
    public static float hrNonflatThres(int bitrate, float spActProb) {
        float[] bitrates = HR_NONFLAT_BITRATES;
        float[] thresholds = HR_NONFLAT_THRESHOLDS;
        float a = (thresholds[1] - thresholds[0]) / (bitrates[1] - bitrates[0]);
        float b = thresholds[0] - a * bitrates[0];
        int scaledBitrate = (int) (bitrate * (float) Math.sqrt(spActProb + 1e-12f));
        return Math.min(Math.max(a * scaledBitrate + b, 0.0f), UV_NONFLATNESS_THR);
    }

    /**
     * Computes the spectral non-flatness of an LPC residual frame, {@code smpl_get_nonflatness}.
     *
     * <p>Partitions the residual into {@value #NON_FLAT_SUBFR_LEN}-sample sub-blocks, measures each sub-block's
     * energy, prepends the carried cross-frame energy state when the new energies dominate it, updates the state
     * with the trailing sub-block energies, and returns the {@code nonflat} ratio of the selected energy run
     * plus a small weighted-LSF non-flatness term. Mutates {@code state} in place.
     *
     * @param resLpc    the LPC residual, read from {@code offset} for {@code length} samples, the native
     *                  {@code res_lpc}
     * @param offset    the first residual sample offset within {@code resLpc}
     * @param length    the residual length in samples, the native {@code L}
     * @param wlsf      the weighted LSF vector, {@value #LPC_ORDER} entries, the native {@code wlsf}
     * @param state     the cross-frame energy state, {@value #NON_FLAT_STATE_LEN} entries, mutated in place,
     *                  the native {@code state}
     * @return the combined residual and weighted-LSF non-flatness measure
     */
    public static float nonflatness(float[] resLpc, int offset, int length, float[] wlsf, float[] state) {
        float[] nrgs = new float[NON_FLAT_NRGS_LEN];
        int n = length / NON_FLAT_SUBFR_LEN;
        for (int i = 0; i < n; i++) {
            nrgs[i + NON_FLAT_STATE_LEN] = nrg(resLpc, offset + i * NON_FLAT_SUBFR_LEN, NON_FLAT_SUBFR_LEN)
                    + NON_FLAT_SUBFR_LEN * 2e-10f;
        }
        float sumState = 0.0f;
        float sumNrgs = 0.0f;
        for (int i = 0; i < NON_FLAT_STATE_LEN; i++) {
            sumState += state[i];
            sumNrgs += nrgs[i + NON_FLAT_STATE_LEN];
        }

        int run = n;
        if (sumState < sumNrgs) {
            System.arraycopy(state, 0, nrgs, 0, NON_FLAT_STATE_LEN);
            run += NON_FLAT_STATE_LEN;
        }
        System.arraycopy(nrgs, length / NON_FLAT_SUBFR_LEN, state, 0, NON_FLAT_STATE_LEN);

        int base = (length / NON_FLAT_SUBFR_LEN) + NON_FLAT_STATE_LEN - run;
        return nonflat(nrgs, base, run) + 0.05f * nonflat(wlsf, 0, LPC_ORDER);
    }

    /**
     * Computes the non-flatness ratio of an energy run, the native file-local {@code nonflat}.
     *
     * <p>Returns {@code L * sumOfSquares / sum^2 - 1}, the excess of the mean square over the squared mean, or
     * {@code -1} when the squared sum is non-positive. The sum is a strict left-to-right single-precision
     * reduction; the sum of squares comes from {@link #nrg}.
     *
     * @param x      the energy values, read from {@code offset}
     * @param offset the first value offset within {@code x}
     * @param length the run length, the native {@code L}
     * @return the non-flatness ratio, or {@code -1.0} when the squared sum is non-positive
     */
    private static float nonflat(float[] x, int offset, int length) {
        float sumx = 0.0f;
        for (int n = 0; n < length; n++) {
            sumx += x[offset + n];
        }
        float sumxSq = sumx * sumx;
        if (sumxSq <= 0.0f) {
            return -1.0f;
        }
        return (length * nrg(x, offset, length) / sumxSq) - 1.0f;
    }

    /**
     * Derives the {@code mainBitRate} / {@code fecBitRate} rate split from the requested total bitrate,
     * {@code smpl_control_lbrr} in {@code smpl_enc_api.c}.
     *
     * <p>When in-band FEC is enabled and the packet-loss rate is at least one percent, splits the total bitrate
     * into an FEC portion (a loss-dependent fraction floored at 4500 bps) and a main portion (floored at a
     * loss-dependent minimum), with corrective rebalancing when the main rate would fall below its minimum, the
     * FEC rate below its floor, or the two rates collapse together; otherwise the whole bitrate is the main rate
     * with no FEC. Both rates are finally clamped to the codec rate range. On the 9600 bps no-FEC scope this
     * always returns {@code (mainBitRate = bitRate, fecBitRate = 0)}.
     *
     * @param bitRate              the requested total bitrate in bits per second, the native {@code bitRate}
     * @param useInBandFEC         {@code true} when in-band FEC is enabled, the native {@code useInBandFEC}
     * @param packetLossPercentage the uplink packet-loss percentage, the native {@code packetLossPercentage}
     * @return the derived main and FEC bitrate split
     */
    public static RateSplit controlLbrr(int bitRate, boolean useInBandFEC, int packetLossPercentage) {
        int mainBitRate;
        int fecBitRate;
        if (useInBandFEC && packetLossPercentage >= 1) {
            float ratio = (packetLossPercentage - 2.0f) / (20.0f - 2.0f);
            ratio = Math.max(Math.min(ratio, 1.0f), 0.0f);
            float split = 0.25f + ratio * (0.5f - 0.25f);
            int minMainBitRate = (int) (12000.0f + ratio * (4500.0f - 12000.0f));
            fecBitRate = Math.max((int) rint(bitRate * split), 4500);
            mainBitRate = bitRate - fecBitRate;
            if (mainBitRate < minMainBitRate) {
                mainBitRate = minMainBitRate;
                fecBitRate = bitRate - mainBitRate;
            }
            if (fecBitRate < 4500) {
                fecBitRate = 0;
                mainBitRate = bitRate;
            }
            if ((mainBitRate - fecBitRate) <= 1000) {
                fecBitRate = bitRate / 2;
                mainBitRate = bitRate - fecBitRate;
            }
        } else {
            mainBitRate = bitRate;
            fecBitRate = 0;
        }
        mainBitRate = Math.max(Math.min(mainBitRate, 30000), 3000);
        fecBitRate = Math.max(Math.min(fecBitRate, 30000), 0);
        return new RateSplit(mainBitRate, fecBitRate);
    }

    /**
     * Computes the sum of squares of a value run, the native {@code smpl_nrg} from the fast-math
     * {@code smpl_codec_util.c}.
     *
     * <p>The native helper lives in the {@code -Ofast} translation unit and is autovectorized into a four-wide
     * SSE2 reduction: four lane accumulators each sum {@code x[4k + lane]^2} left-to-right, then a horizontal
     * reduction folds them as {@code (lane0 + lane2) + (lane1 + lane3)}, and a scalar tail adds the final
     * {@code length % 4} squares left-to-right. This reproduces that exact grouping; a naive left-to-right sum
     * disagrees by tens of thousands of ULP on long runs.
     *
     * @param x      the values, read from {@code offset}
     * @param offset the first value offset within {@code x}
     * @param length the run length, the native {@code N}
     * @return the single-precision sum of squares
     */
    private static float nrg(float[] x, int offset, int length) {
        float lane0 = 0.0f;
        float lane1 = 0.0f;
        float lane2 = 0.0f;
        float lane3 = 0.0f;
        int vecEnd = length & ~3;
        for (int n = 0; n < vecEnd; n += 4) {
            float x0 = x[offset + n];
            float x1 = x[offset + n + 1];
            float x2 = x[offset + n + 2];
            float x3 = x[offset + n + 3];
            lane0 += x0 * x0;
            lane1 += x1 * x1;
            lane2 += x2 * x2;
            lane3 += x3 * x3;
        }
        float nrg = (lane0 + lane2) + (lane1 + lane3);
        for (int n = vecEnd; n < length; n++) {
            nrg += x[offset + n] * x[offset + n];
        }
        return nrg;
    }

    /**
     * Evaluates the numerically guarded logistic sigmoid, the native {@code smpl_sigmoid}.
     *
     * <p>Returns {@code 1 / (1 + exp(-x))}, saturating to {@code 1.0} above {@code 80} and {@code 0.0} below
     * {@code -80} to keep the exponential out of the overflow and denormal ranges as the native helper does.
     *
     * @param x the logit, the native {@code x}
     * @return the sigmoid in {@code [0, 1]}
     */
    private static float sigmoid(float x) {
        if (x > 80.0f) {
            return 1.0f;
        }
        if (x < -80.0f) {
            return 0.0f;
        }
        return 1.0f / (1.0f + (float) Math.exp(-x));
    }

    /**
     * Rounds a single-precision value to the nearest integer with halves away from zero, the native
     * {@code roundf}.
     *
     * <p>The C library {@code roundf} rounds half-way cases away from zero, unlike Java's
     * {@link Math#round(float)} which rounds half up; this reproduces the away-from-zero rule on the
     * single-precision input the native code passes.
     *
     * @param x the value to round, evaluated in single precision
     * @return the nearest integer with halves away from zero
     */
    private static int rint(float x) {
        return (int) (x < 0.0f ? Math.ceil(x - 0.5f) : Math.floor(x + 0.5f));
    }
}
