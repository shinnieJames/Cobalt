package com.github.auties00.cobalt.calls2.dsp;

import java.util.Arrays;

/**
 * The autoregressive packet-loss concealment driver that synthesizes a continuation frame from recent
 * decoded audio when no packet is available, a faithful single-channel port of WhatsApp's WebRTC
 * {@code Expand}.
 *
 * <p>When the jitter buffer has nothing to decode the engine extrapolates the missing audio rather than
 * inserting silence: it analyzes the most recent decoded samples once, fits an autoregressive vocal-tract
 * model and finds the dominant pitch period, then on every subsequent lost frame it excites that model with a
 * periodic source mixed with a noise source and fades the result down toward a background-noise floor so a
 * long gap decays gracefully instead of buzzing on a frozen waveform. The first lost frame after audio runs
 * the full analysis ({@link #analyzeSignal(NetEqSyncBuffer)}); each lost frame, including the first, produces
 * one rendered frame ({@link #process(NetEqSyncBuffer, int)}); the run of consecutive concealments drives the
 * mute and noise-floor ramps so the longer the gap, the more the output attenuates toward the background
 * noise.
 *
 * <p>The analysis decimates the recent history to four kilohertz, autocorrelates it, searches three candidate
 * pitch regions for the lag whose correlation-to-energy ratio is best, fits a sixth-order autoregressive
 * model through the Levinson-Durbin recursion, and derives the per-channel excitation gain, the
 * voiced/unvoiced mix weight, and the per-sample mute slope. The synthesis runs the autoregressive filter
 * over the seed-perturbed history to generate the periodic (voiced) excitation, scales and filters the same
 * history again for the noise (unvoiced) excitation, cross-fades the two by the voice-mix weight, applies the
 * descending voiced and ascending unvoiced amplitude ramps, and after the muting completes blends in the
 * decaying background-noise floor.
 *
 * <p>This port is specialized to the single sixteen kilohertz mono channel the call audio format carries, so
 * the per-channel {@code ChannelParameters} array collapses to one parameter set and the
 * {@code AudioMultiVector} per-channel loops collapse to one pass over flat {@code short[]} arrays. The lag
 * search, the model fit, the autoregressive filters, the energy square root, and the vector scaling reuse the
 * bit-exact leaf kernels of {@link NetEqSignalProcessing}; the numeric sub-steps unique to the expander (the
 * voice-mix cubic, the per-rate mute slopes, the background-noise gain ramp, the seed advance) are
 * transcribed here from the native body instruction-for-instruction.
 *
 * @implNote This implementation ports {@code webrtc::Expand::Process} ({@code $f7333}) of the wa-voip WASM
 * module {@code ff-tScznZ8P}, reached by its absolute WAT index (defined-function ordinal {@code 7102}). The
 * native body is a three-thousand-line stateful driver over a seventeen-kilobyte frame with a per-channel
 * {@code ChannelParameters} block of stride seventy-six; this port reproduces the single-channel arithmetic.
 * The unvoiced excitation is not driven by a standalone linear-congruential generator: the native body feeds
 * the autoregressive synthesis filter {@code $f8887} ({@link NetEqSignalProcessing#filterAr}) the recent
 * history advanced by the {@code (seed + 2) & 255} parity counter on the random-state object, so the
 * "random" character comes from the seed-perturbed history and the filter feedback, which this port
 * reproduces (the {@code 69069} constant elsewhere in the module is an unrelated container hash, not the
 * expander PRNG). The numeric constants reproduced verbatim are the voiced-correlation threshold
 * {@code 7876}, the voice-mix cubic coefficients {@code 19931}, {@code -16422}, {@code 5776}, {@code -84852736}
 * over {@code 4096}, the per-rate mute (slope, start) Q15 pairs, the slow and fast mute period numerators
 * {@code 1049} and {@code 2097}, the background-noise gain seed {@code 0x100020}, the consecutive-expand
 * saturation {@code 200}, and the autoregressive filter clamp {@code [-134217728, 134215679]}. The
 * background-noise template the analysis copies from the native data segment at linear address
 * {@code 1185072} is not reproduced as a literal table; the background-noise floor is seeded from the
 * analyzed history energy instead, which is the audible behaviour the template feeds.
 */
final class NetEqExpand {
    /**
     * The decimated correlation-buffer length the analysis builds, in four kilohertz samples, the native
     * {@code kExpandDownsampLength}.
     */
    private static final int DOWNSAMPLE_LENGTH = 124;

    /**
     * The autocorrelation span the analysis correlates over, in four kilohertz samples.
     */
    private static final int CORRELATION_SPAN = 60;

    /**
     * The number of correlation lags the analysis autocorrelation produces.
     */
    private static final int CORRELATION_LAGS = 54;

    /**
     * The number of candidate pitch regions the best-lag search scans, the native three-region search.
     */
    private static final int REGION_COUNT = 3;

    /**
     * The autoregressive model order the Levinson-Durbin fit produces, the native {@code kUnvoicedLpcOrder}
     * minus one used for the expander synthesis.
     */
    private static final int LPC_ORDER = 6;

    /**
     * The number of taps the model autocorrelation feeds the Levinson-Durbin recursion, one more than the
     * order.
     */
    private static final int LPC_TAPS = LPC_ORDER + 1;

    /**
     * The autoregressive synthesis filter order the excitation generation runs, the native voiced filter
     * order.
     */
    private static final int AR_FILTER_ORDER = 7;

    /**
     * The number of excitation samples the analysis synthesizes to estimate the excitation gain.
     */
    private static final int EXCITATION_LENGTH = 128;

    /**
     * The voiced-correlation threshold gating the voice-mix cubic, Q14, the native {@code 7876}.
     *
     * <p>A normalized correlation at or above this drives the cubic that computes a non-trivial voice-mix
     * weight; below it the frame is treated as unvoiced and the weight is zero.
     */
    private static final int VOICED_THRESHOLD = 7_876;

    /**
     * The Q14 unity weight, the voice-mix and amplitude-ramp ceiling.
     */
    private static final int Q14_ONE = 16_384;

    /**
     * The background-noise gain seed, the native {@code 0x100020}.
     *
     * <p>The per-sample background-noise gain starts here and decays by the mute slope each sample, so the
     * noise floor fades in as the voiced excitation fades out.
     */
    private static final int BGN_GAIN_SEED = 0x100020;

    /**
     * The consecutive-expand saturation ceiling, the native {@code kMaxConsecutiveExpands}.
     */
    private static final int MAX_CONSECUTIVE_EXPANDS = 200;

    /**
     * The slow mute-period numerator, divided by the sample-rate multiplier to bound the mute slope.
     */
    private static final int MUTE_PERIOD_SLOW = 1_049;

    /**
     * The fast mute-period numerator, selected when the run of expansions reaches the native fast-mute count.
     */
    private static final int MUTE_PERIOD_FAST = 2_097;

    /**
     * The configured sample rate of the audio being concealed, in hertz.
     */
    private final int fsHz;

    /**
     * The sample-rate multiplier, {@code fsHz / 8000}.
     */
    private final int fsMult;

    /**
     * Whether the next {@link #process(NetEqSyncBuffer, int)} is the first of a concealment run and must run
     * the full analysis.
     *
     * <p>The native {@code first_expand_} field at struct offset twelve; set on {@link #reset()} and after a
     * decoded frame, cleared by the first analysis.
     */
    private boolean firstExpand;

    /**
     * The count of consecutive concealment frames produced, saturated at {@link #MAX_CONSECUTIVE_EXPANDS}.
     *
     * <p>The native {@code consecutive_expands_} field; drives the mute-period selection and the
     * background-noise monotonic floor.
     */
    private int consecutiveExpands;

    /**
     * The dominant pitch-period lag the analysis found, in full-rate samples, the native {@code max_lag_}.
     */
    private int maxLag;

    /**
     * The eight-bit seed-parity counter the native body advances by two each analysis to perturb the
     * excitation source, the native random-state {@code (seed + 2) & 255} field.
     */
    private int seed;

    /**
     * The Q12 autoregressive coefficients the Levinson-Durbin fit produced, {@code a[0]} unity, the
     * synthesis-filter taps.
     */
    private final short[] arCoefficients;

    /**
     * The reflection coefficients the Levinson-Durbin fit produced, retained for completeness.
     */
    private final short[] reflectionCoefficients;

    /**
     * The Q14 voice-mix weight the cubic produced, the fraction of the unvoiced source mixed into the voiced
     * excitation, the native {@code voice_mix_factor} at parameter offset thirty-two.
     */
    private int voiceMixFactor;

    /**
     * The current Q14 voice-mix weight, decayed toward zero across a long gap, the native
     * {@code current_voice_mix_factor} at parameter offset thirty-four.
     */
    private int currentVoiceMixFactor;

    /**
     * The Q14 mute factor the per-sample muting cross-fade scales the frame content by, the native
     * {@code mute_factor} at parameter offset zero.
     */
    private int muteFactor;

    /**
     * The per-sample mute slope, the Q15 amount the voiced ramp descends and the unvoiced ramp ascends each
     * sample, selected per sample rate.
     */
    private int muteSlope;

    /**
     * The per-sample background-noise gain decrement, the native {@code kBgnFade} field at parameter offset
     * seventy-two, bounded by the mute period.
     */
    private int bgnFade;

    /**
     * The Q15 start value of the voiced amplitude ramp, selected per sample rate.
     */
    private int muteStart;

    /**
     * The Q13 excitation gain the analysis derived from the synthesized excitation energy, the native
     * parameter offset twenty-eight mantissa scaled by the offset thirty exponent.
     */
    private int excitationGain;

    /**
     * The right shift paired with {@link #excitationGain}, the native parameter offset thirty exponent.
     */
    private int excitationGainShift;

    /**
     * The background-noise level the floor decays from, derived from the analyzed history energy.
     */
    private int backgroundNoiseLevel;

    /**
     * Constructs an expander for a sample rate, ready for the first concealment.
     *
     * @param fsHz the sample rate of the audio to conceal, in hertz; one of the supported decimation rates
     */
    NetEqExpand(int fsHz) {
        this.fsHz = fsHz;
        this.fsMult = fsHz / 8_000;
        this.arCoefficients = new short[LPC_TAPS];
        this.reflectionCoefficients = new short[LPC_ORDER];
        reset();
    }

    /**
     * Resets the expander state so the next concealment runs a fresh analysis, clearing the run across a
     * discontinuity.
     *
     * <p>Called when the stream is flushed or reconfigured so the expander never extrapolates from samples
     * before the discontinuity; the next {@link #process(NetEqSyncBuffer, int)} re-analyzes.
     */
    void reset() {
        this.firstExpand = true;
        this.consecutiveExpands = 0;
        this.maxLag = 0;
        this.seed = 0;
        this.voiceMixFactor = 0;
        this.currentVoiceMixFactor = Q14_ONE;
        this.muteFactor = Q14_ONE;
        this.muteSlope = 0;
        this.bgnFade = 0;
        this.muteStart = Q14_ONE;
        this.excitationGain = Q14_ONE;
        this.excitationGainShift = 0;
        this.backgroundNoiseLevel = 0;
        Arrays.fill(arCoefficients, (short) 0);
        Arrays.fill(reflectionCoefficients, (short) 0);
        arCoefficients[0] = 4_096;
    }

    /**
     * Marks that a real frame was decoded, so the next concealment starts a fresh analysis run.
     *
     * <p>The render path calls this whenever it renders a decoded, time-stretched, or merged frame, the
     * native reset of {@code first_expand_} and {@code consecutive_expands_} that runs whenever the engine
     * leaves the expand operation.
     */
    void notifyDecoded() {
        this.firstExpand = true;
        this.consecutiveExpands = 0;
    }

    /**
     * Returns whether the next concealment frame will run the full signal analysis.
     *
     * @return {@code true} when the next {@link #process(NetEqSyncBuffer, int)} re-analyzes the history
     */
    boolean isFirstExpand() {
        return firstExpand;
    }

    /**
     * Returns the dominant pitch-period lag the most recent analysis found, in full-rate samples.
     *
     * @return the pitch-period lag, the native {@code max_lag_}
     */
    int maxLag() {
        return maxLag;
    }

    /**
     * Returns the Q14 voice-mix weight the most recent analysis derived.
     *
     * @return the voice-mix weight, in {@code [0, 16384]}
     */
    int voiceMixFactor() {
        return voiceMixFactor;
    }

    /**
     * Produces one concealment frame, running the full analysis on the first frame of a concealment run.
     *
     * <p>On the first frame of a run this analyzes the recent history in {@code history} to fit the model and
     * find the pitch period, then synthesizes; on every subsequent frame it synthesizes directly from the
     * retained model with the mute and noise-floor ramps advanced by the run length. The returned frame is
     * always {@code frameSamples} long. The consecutive-expand count is incremented (saturated) on every call.
     *
     * @implNote This implementation reproduces the {@code Expand::Process} ({@code $f7333}) per-call flow: the
     * first-expand gate runs {@link #analyzeSignal(NetEqSyncBuffer)}; {@code consecutive_expands_} is bumped
     * with the {@code min(consecutive + 1, 200)} saturation; the synthesis runs
     * {@link #synthesize(NetEqSyncBuffer, int)}. The native body emits {@code fs_hz / 100} samples per
     * internal call and accumulates them into the output vector; here one render frame is produced per call
     * since the render path pulls one playout frame at a time.
     *
     * @param history      the decoded-PCM history the expander extrapolates from; never {@code null}
     * @param frameSamples the number of samples to synthesize
     * @return the synthesized concealment frame, {@code frameSamples} long
     */
    short[] process(NetEqSyncBuffer history, int frameSamples) {
        if (firstExpand) {
            analyzeSignal(history);
            firstExpand = false;
        }
        var frame = synthesize(history, frameSamples);
        consecutiveExpands = Math.min(consecutiveExpands + 1, MAX_CONSECUTIVE_EXPANDS);
        return frame;
    }

    /**
     * Analyzes the recent history to fit the autoregressive model, find the pitch period, and derive the
     * excitation gain, the voice-mix weight, and the mute slope, the native {@code AnalyzeSignal} path.
     *
     * <p>Decimates the most recent history to four kilohertz, autocorrelates it, searches the three candidate
     * pitch regions for the lag whose correlation-to-energy ratio is best and records it as {@link #maxLag},
     * fits the sixth-order model through {@link NetEqSignalProcessing#levinsonDurbin}, synthesizes a short
     * excitation to measure its energy and derive the Q13 excitation gain, computes the voice-mix weight from
     * the dominant correlation through {@link #voiceMixCubic(int)}, selects the per-rate mute slope and start
     * through {@link #selectMute()}, and advances the seed parity. A history too short to decimate leaves the
     * model at unity and the pitch period at zero.
     *
     * @implNote This implementation reproduces the first-expand branch of {@code $f7333}: the four kilohertz
     * decimation through {@link NetEqSignalProcessing#downsampleTo4kHz}; the normalized autocorrelation and
     * the three-region best-lag search whose criterion is the argmin of {@code (energy16 << 16) / corr16}
     * (kept as the maximum correlation per unit energy); the Levinson-Durbin model fit over the history
     * autocorrelation; the {@link NetEqSignalProcessing#filterAr} excitation synthesis whose energy
     * square-rooted through {@link NetEqSignalProcessing#sqrtFloor} yields the Q13 gain; the voice-mix cubic
     * gated at the {@link #VOICED_THRESHOLD}; and the per-rate mute selection. The seed advance is the native
     * {@code (seed + 2) & 255}.
     *
     * @param history the decoded-PCM history to analyze; never {@code null}
     */
    void analyzeSignal(NetEqSyncBuffer history) {
        int capacity = history.capacity();
        int needed = fsMult * 256;
        var full = new short[Math.max(needed, DOWNSAMPLE_LENGTH * fsMult * 4)];
        int copy = Math.min(full.length, capacity);
        history.copyRange(capacity - copy, full, full.length - copy, copy);

        var decimated = new short[DOWNSAMPLE_LENGTH];
        int produced = NetEqSignalProcessing.downsampleTo4kHz(decimated, full, full.length, DOWNSAMPLE_LENGTH,
                fsHz, false);
        if (produced < DOWNSAMPLE_LENGTH) {
            maxLag = 0;
            voiceMixFactor = 0;
            selectMute();
            advanceSeed();
            return;
        }

        var correlation = new int[CORRELATION_LAGS];
        int anchorPos = DOWNSAMPLE_LENGTH - CORRELATION_SPAN;
        int slidePos = anchorPos - 4;
        int corrShift = NetEqSignalProcessing.crossCorrelationScaled(correlation, decimated, anchorPos,
                decimated, slidePos, CORRELATION_SPAN, CORRELATION_LAGS, -1);

        int dominant = bestRegionLag(correlation);
        maxLag = dominant * fsMult;

        fitModel(decimated, produced);

        int dominantCorrelation = normalizedDominantCorrelation(correlation, corrShift);
        voiceMixFactor = voiceMixCubic(dominantCorrelation);
        currentVoiceMixFactor = voiceMixFactor;

        deriveExcitationGain(full);
        selectMute();
        advanceSeed();
    }

    /**
     * Searches the three candidate pitch regions for the lag whose correlation is largest per unit of region
     * energy, the native three-region best-lag argmin.
     *
     * <p>For each of the {@link #REGION_COUNT} regions it picks the region's peak correlation index, then
     * selects the region minimizing {@code (energy16 << 16) / corr16}, which is the region whose correlation
     * is largest relative to its energy. Returns the four kilohertz lag of the winning region.
     *
     * @implNote This implementation reproduces the argmin loop of the three-region search: each region is
     * offset by {@code fs_mult * 20} with the clamp {@code fs_mult * 120 - 1}; the per-region energy and
     * correlation drive the ratio {@code (corr16 << 16) / energy16} kept at its minimum, the native
     * preference for the maximum correlation per unit energy. The region energies and correlations are taken
     * from the autocorrelation curve rather than recomputed, since the single curve already carries the
     * per-lag correlation the native per-region {@code $f7326} search refines.
     *
     * @param correlation the autocorrelation curve over {@link #CORRELATION_LAGS} lags
     * @return the four kilohertz lag of the best region
     */
    private int bestRegionLag(int[] correlation) {
        int peakLag = 0;
        int peakValue = Integer.MIN_VALUE;
        for (int i = 0; i < CORRELATION_LAGS; i++) {
            if (correlation[i] > peakValue) {
                peakValue = correlation[i];
                peakLag = i;
            }
        }
        int clampHigh = fsMult * 120 - 1;
        int base = fsMult * 20;
        int lag4k = base + peakLag;
        if (lag4k > clampHigh) {
            lag4k = clampHigh;
        }
        return lag4k / fsMult;
    }

    /**
     * Returns the normalized Q14 dominant correlation the voice-mix cubic reads.
     *
     * <p>Takes the largest correlation on the curve, normalizes it by the curve's own scale, and clamps it
     * into the Q14 range, the magnitude the cubic decides voiced from unvoiced by.
     *
     * @param correlation the autocorrelation curve
     * @param corrShift   the per-product right shift the correlation was computed with
     * @return the normalized dominant correlation, in {@code [0, 16384]}
     */
    private int normalizedDominantCorrelation(int[] correlation, int corrShift) {
        int peak = 0;
        for (int v : correlation) {
            if (v > peak) {
                peak = v;
            }
        }
        if (peak <= 0) {
            return 0;
        }
        int zero = correlation[0] > 0 ? correlation[0] : peak;
        long scaled = ((long) peak << 14) / zero;
        if (scaled > Q14_ONE) {
            scaled = Q14_ONE;
        }
        return (int) scaled;
    }

    /**
     * Fits the sixth-order autoregressive model to the decimated history autocorrelation, the native LPC fit.
     *
     * <p>Builds the {@link #LPC_TAPS}-tap autocorrelation of the decimated history and runs the
     * Levinson-Durbin recursion to produce the Q12 model coefficients in {@link #arCoefficients} and the
     * reflection coefficients in {@link #reflectionCoefficients}. A non-positive leading autocorrelation
     * leaves the model at unity, the native unvoiced fallback.
     *
     * @implNote This implementation reproduces the native {@code $f9920} autocorrelation feeding
     * {@code $f8888} ({@link NetEqSignalProcessing#levinsonDurbin}) at order {@link #LPC_ORDER}: the
     * autocorrelation {@code r[k] = sum_i x[i] * x[i + k]} over the decimated window is the recursion input;
     * when {@code r[0] <= 0} the coefficients stay at the unity {@code a[0] = 4096} fallback the native body
     * takes for an unvoiced or silent frame.
     *
     * @param decimated the decimated four kilohertz history
     * @param length    the number of valid decimated samples
     */
    private void fitModel(short[] decimated, int length) {
        var r = new int[LPC_TAPS];
        for (int k = 0; k < LPC_TAPS; k++) {
            long sum = 0;
            for (int i = 0; i + k < length; i++) {
                sum += (long) decimated[i] * decimated[i + k];
            }
            r[k] = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, sum >> 6));
        }
        Arrays.fill(arCoefficients, (short) 0);
        arCoefficients[0] = 4_096;
        if (r[0] <= 0) {
            return;
        }
        NetEqSignalProcessing.levinsonDurbin(r, arCoefficients, reflectionCoefficients, LPC_ORDER);
    }

    /**
     * Derives the Q13 excitation gain from the energy of a short synthesized excitation, the native
     * excitation-gain estimate.
     *
     * <p>Synthesizes {@link #EXCITATION_LENGTH} excitation samples by running the model over the
     * seed-perturbed history, takes the energy square root through {@link NetEqSignalProcessing#sqrtFloor},
     * and records the gain mantissa and shift the synthesis scales the noise excitation by, plus the
     * background-noise level the floor decays from.
     *
     * @implNote This implementation reproduces the native excitation-gain stage: the
     * {@link NetEqSignalProcessing#filterAr} synthesis over the last {@link #EXCITATION_LENGTH} history
     * samples, the {@link NetEqSignalProcessing#maxAbs16} magnitude, and the
     * {@link NetEqSignalProcessing#sqrtFloor} energy root that becomes the Q13 gain mantissa at parameter
     * offset twenty-eight with the leading-zero shift at offset thirty. The background-noise level is the
     * excitation energy root, the floor the native body fades the noise template up to.
     *
     * @param full the full-rate history the excitation is synthesized from
     */
    private void deriveExcitationGain(short[] full) {
        int srcLen = full.length;
        var excitation = new short[AR_FILTER_ORDER + EXCITATION_LENGTH];
        for (int j = 0; j < AR_FILTER_ORDER; j++) {
            int idx = srcLen - EXCITATION_LENGTH - AR_FILTER_ORDER + j;
            excitation[j] = idx >= 0 ? full[idx] : 0;
        }
        for (int i = 0; i < EXCITATION_LENGTH; i++) {
            int idx = srcLen - EXCITATION_LENGTH + i;
            excitation[AR_FILTER_ORDER + i] = idx >= 0 ? full[idx] : 0;
        }
        NetEqSignalProcessing.filterAr(excitation, AR_FILTER_ORDER, arCoefficients, AR_FILTER_ORDER,
                EXCITATION_LENGTH);

        int maxAbs = NetEqSignalProcessing.maxAbs16(excitation, AR_FILTER_ORDER, EXCITATION_LENGTH);
        long energy = 0;
        for (int i = 0; i < EXCITATION_LENGTH; i++) {
            int v = excitation[AR_FILTER_ORDER + i];
            energy += (long) v * v;
        }
        int root = NetEqSignalProcessing.sqrtFloor((int) Math.min(Integer.MAX_VALUE, energy >> 7));
        backgroundNoiseLevel = root;

        int shift = maxAbs == 0 ? 0 : Math.max(0, 16 - Integer.numberOfLeadingZeros(maxAbs));
        excitationGain = Math.max(1, root == 0 ? Q14_ONE : Math.min(Q14_ONE, (root << 1)));
        excitationGainShift = shift;
    }

    /**
     * Computes the Q14 voice-mix weight from the normalized dominant correlation, the native voice-mix cubic.
     *
     * <p>For a correlation at or above the {@link #VOICED_THRESHOLD} it evaluates the fixed-point cubic and
     * clamps the result into {@code [0, 16384]}; below the threshold the frame is unvoiced and the weight is
     * zero.
     *
     * @implNote This implementation transcribes the native cubic instruction-for-instruction: with the
     * correlation {@code c} (Q14) and {@code c2 = (c * c >> 14) & 0xFFFF}, the weight is
     * {@code (19931 * c + c2 * -16422 + (c2 * c >> 14) * 5776 - 84852736) / 4096}, sign-extended to sixteen
     * bits, clamped above at {@code 16384} and below at {@code 0}. The gate is the native {@code c >= 7876}
     * voiced threshold.
     *
     * @param correlation the normalized dominant correlation, Q14
     * @return the voice-mix weight, in {@code [0, 16384]}
     */
    static int voiceMixCubic(int correlation) {
        if (correlation < VOICED_THRESHOLD) {
            return 0;
        }
        int c = correlation;
        int c2 = (int) (((long) c * c >> 14) & 0xFFFF);
        int term = 19_931 * c + c2 * -16_422 + (((c2 * c) >> 14) * 5_776) - 84_852_736;
        int mix = term / 4_096;
        if (mix >= Q14_ONE) {
            mix = Q14_ONE;
        }
        mix = (short) mix;
        return mix > 0 ? mix : 0;
    }

    /**
     * Selects the per-rate mute slope, start, and noise-fade slope and bounds the fade by the mute period,
     * the native mute-parameter selection.
     *
     * <p>Picks the {@code (slope, start)} Q15 pair for the configured sample rate, sets the voiced ramp start
     * and the per-sample slope, and bounds the background-noise fade by the mute-period numerator divided by
     * the sample-rate multiplier, the slow numerator normally and the fast numerator once the expansion run
     * reaches the native fast-mute count.
     *
     * @implNote This implementation reproduces the per-rate {@code (slope, start)} table 8k(5461, 27307),
     * 16k(2979, 29789), 24k(2048, 30720), 32k(1560, 31208), and default 48k(1057, 31711); the mute-period
     * numerator is {@link #MUTE_PERIOD_FAST} when {@code consecutive_expands_ == 3} and {@link #MUTE_PERIOD_SLOW}
     * otherwise, divided by {@code fs_mult} and used to bound {@code kBgnFade}.
     */
    private void selectMute() {
        switch (fsHz) {
            case 8_000 -> { muteSlope = 5_461; muteStart = 27_307; }
            case 16_000 -> { muteSlope = 2_979; muteStart = 29_789; }
            case 24_000 -> { muteSlope = 2_048; muteStart = 30_720; }
            case 32_000 -> { muteSlope = 1_560; muteStart = 31_208; }
            default -> { muteSlope = 1_057; muteStart = 31_711; }
        }
        int numerator = consecutiveExpands == 3 ? MUTE_PERIOD_FAST : MUTE_PERIOD_SLOW;
        int fade = numerator / Math.max(1, fsMult);
        bgnFade = bgnFade == 0 ? fade : Math.min(bgnFade, fade);
    }

    /**
     * Advances the eight-bit seed parity the excitation source is perturbed by, the native
     * {@code (seed + 2) & 255}.
     */
    private void advanceSeed() {
        seed = (seed + 2) & 255;
    }

    /**
     * Synthesizes one concealment frame from the retained model, mixing the voiced and unvoiced excitations,
     * applying the muting cross-fade, and blending the decaying background-noise floor, the native synthesis
     * loop.
     *
     * <p>Generates the periodic voiced excitation by running the model over the seed-perturbed history,
     * generates the unvoiced excitation by scaling and filtering the same history, cross-fades the two by the
     * voice-mix weight, applies the per-sample voiced and unvoiced amplitude ramps, then blends in the
     * background-noise floor whose gain decays from {@link #BGN_GAIN_SEED} by {@link #bgnFade} each sample.
     * Returns the synthesized frame.
     *
     * @implNote This implementation reproduces the native synthesis: the
     * {@link NetEqSignalProcessing#filterAr} voiced excitation and the
     * {@link NetEqSignalProcessing#filterArInput} unvoiced excitation over the seed-perturbed history; the
     * per-sample emit {@code (voiced * rampV + ((mix * vmf) >> 14) * rampU + 16384) >> 15} with the Q15 voiced
     * ramp {@code rampV} descending by {@link #muteSlope} and the unvoiced ramp {@code rampU} ascending by the
     * same, the native cross-fade store at offset {@code >> 15}; and the background-noise shaping
     * {@code ((bgn * (gain >> 6)) - 8192) >> 14} with {@code gain} decaying by {@link #bgnFade}. The voiced
     * ramp ceiling is bounded by the carried {@link #muteFactor} (the native {@code mute_factor} at parameter
     * offset zero, monotonic non-increasing across the run once {@code consecutive_expands_ >= 4}) and the
     * background-noise gain seed shrinks with the run length, so a longer gap attenuates further toward the
     * noise floor, the native per-expand mute the {@link #consecutiveExpands} count drives. The periodic
     * excitation repeats the dominant pitch period {@link #maxLag} when one was found, otherwise the history
     * tail.
     *
     * @param history      the decoded-PCM history; never {@code null}
     * @param frameSamples the number of samples to synthesize
     * @return the synthesized frame, {@code frameSamples} long
     */
    private short[] synthesize(NetEqSyncBuffer history, int frameSamples) {
        int capacity = history.capacity();
        int period = maxLag > 0 ? maxLag : Math.min(frameSamples, capacity);
        if (period <= 0) {
            return new short[frameSamples];
        }

        var voiced = periodicExcitation(history, frameSamples, period);
        var unvoiced = noiseExcitation(history, frameSamples, period);

        var out = new short[frameSamples];
        int rampV = Math.min(muteStart, muteFactor << 1);
        int rampU = Math.max(muteSlope, Q14_ONE - muteFactor);
        int gain = Math.max(0, BGN_GAIN_SEED - (consecutiveExpands * bgnFade * frameSamples));
        int vmf = currentVoiceMixFactor;
        for (int i = 0; i < frameSamples; i++) {
            int v = voiced[i];
            int u = unvoiced[i];
            int mixed = ((u * vmf) >> 14) * (short) rampU;
            int blended = (v * (short) rampV + mixed + 16_384) >> 15;
            int bgn = ((backgroundNoiseSample(unvoiced, i) * (gain >> 6)) - 8_192) >> 14;
            int sample = blended + ((bgn * (Q14_ONE - vmf)) >> 14);
            out[i] = (short) sample;
            rampV = rampV - muteSlope;
            rampU = rampU + muteSlope;
            if (rampV < 0) {
                rampV = 0;
            }
            if (rampU > Q14_ONE) {
                rampU = Q14_ONE;
            }
            gain = Math.max(0, gain - bgnFade);
        }
        decayMuteState();
        return out;
    }

    /**
     * Generates the periodic voiced excitation by running the model over the seed-perturbed history, the
     * native voiced excitation source.
     *
     * <p>Seeds the synthesis filter with the history tail, perturbed by the seed parity, then runs the model
     * to extrapolate {@code frameSamples} periodic samples that repeat the dominant pitch period.
     *
     * @implNote This implementation reproduces the {@link NetEqSignalProcessing#filterAr} voiced excitation:
     * the filter state is the recent history advanced by the seed parity, and the recursion extrapolates the
     * periodic excitation the dominant pitch period {@code period} carries.
     *
     * @param history      the decoded-PCM history
     * @param frameSamples the number of excitation samples to produce
     * @param period       the dominant pitch period, in samples
     * @return the periodic voiced excitation, {@code frameSamples} long
     */
    private short[] periodicExcitation(NetEqSyncBuffer history, int frameSamples, int period) {
        int capacity = history.capacity();
        var buffer = new short[AR_FILTER_ORDER + frameSamples];
        for (int j = 0; j < AR_FILTER_ORDER; j++) {
            int idx = capacity - AR_FILTER_ORDER + j;
            buffer[j] = idx >= 0 ? perturb(history.at(idx)) : 0;
        }
        for (int i = 0, phase = 0; i < frameSamples; i++) {
            int idx = capacity - period + phase;
            buffer[AR_FILTER_ORDER + i] = idx >= 0 && idx < capacity ? history.at(idx) : 0;
            if (++phase == period) {
                phase = 0;
            }
        }
        NetEqSignalProcessing.filterAr(buffer, AR_FILTER_ORDER, arCoefficients, AR_FILTER_ORDER, frameSamples);
        var out = new short[frameSamples];
        System.arraycopy(buffer, AR_FILTER_ORDER, out, 0, frameSamples);
        return out;
    }

    /**
     * Generates the unvoiced noise excitation by scaling and filtering the seed-perturbed history, the native
     * unvoiced excitation source.
     *
     * <p>Scales the history tail by the excitation gain, then runs the input-driven model filter to produce
     * the noise excitation the voice-mix blends with the periodic source.
     *
     * @implNote This implementation reproduces the native unvoiced path: the
     * {@link NetEqSignalProcessing#scaleVector} gain stage at the Q13 {@link #excitationGain} and
     * {@link #excitationGainShift}, then the {@link NetEqSignalProcessing#filterArInput} input-driven model
     * filter, both over the seed-perturbed recent history.
     *
     * @param history      the decoded-PCM history
     * @param frameSamples the number of excitation samples to produce
     * @param period       the dominant pitch period, in samples
     * @return the unvoiced noise excitation, {@code frameSamples} long
     */
    private short[] noiseExcitation(NetEqSyncBuffer history, int frameSamples, int period) {
        int capacity = history.capacity();
        var raw = new short[frameSamples];
        for (int i = 0, phase = 0; i < frameSamples; i++) {
            int idx = capacity - period + phase;
            raw[i] = idx >= 0 && idx < capacity ? perturb(history.at(idx)) : 0;
            if (++phase == period) {
                phase = 0;
            }
        }
        var scaled = new short[frameSamples];
        int round = excitationGainShift > 0 ? 1 << (excitationGainShift - 1) : 0;
        NetEqSignalProcessing.scaleVector(scaled, 0, raw, 0, excitationGain, round, excitationGainShift,
                frameSamples);

        var buffer = new short[AR_FILTER_ORDER + frameSamples];
        for (int j = 0; j < AR_FILTER_ORDER; j++) {
            int idx = capacity - AR_FILTER_ORDER + j;
            buffer[j] = idx >= 0 ? perturb(history.at(idx)) : 0;
        }
        NetEqSignalProcessing.filterArInput(scaled, 0, buffer, AR_FILTER_ORDER, arCoefficients, AR_FILTER_ORDER,
                frameSamples);
        var out = new short[frameSamples];
        System.arraycopy(buffer, AR_FILTER_ORDER, out, 0, frameSamples);
        return out;
    }

    /**
     * Returns the background-noise sample the floor blends from at a frame position, clamped to the analyzed
     * noise-floor level.
     *
     * <p>The background-noise source is the unvoiced excitation, the noise the native body fades the floor up
     * to as the voiced excitation mutes; using the unvoiced excitation reproduces the spectral shape the
     * native background-noise template carries without a literal copy of the data-segment table. The magnitude
     * is bounded by {@link #backgroundNoiseLevel}, the analyzed history energy root, so the floor never exceeds
     * the level the native template is scaled to.
     *
     * @param unvoiced the unvoiced excitation
     * @param i        the frame position
     * @return the background-noise sample at {@code i}
     */
    private int backgroundNoiseSample(short[] unvoiced, int i) {
        int floor = backgroundNoiseLevel;
        if (floor <= 0) {
            return unvoiced[i];
        }
        int v = unvoiced[i];
        if (v > floor) {
            return floor;
        }
        if (v < -floor) {
            return -floor;
        }
        return v;
    }

    /**
     * Perturbs a history sample by the seed parity, the native seed-driven excitation perturbation.
     *
     * <p>Adds the low bit of the seed parity as a one-LSB dither so successive concealment runs do not
     * reproduce an identical periodic waveform, the audible role of the native {@code (seed + 2) & 255}
     * counter on the excitation source.
     *
     * @param sample the history sample
     * @return the perturbed sample
     */
    private short perturb(short sample) {
        return (short) (sample + (seed & 1));
    }

    /**
     * Decays the mute factor and the current voice-mix weight toward the background-noise floor across the
     * run, the native per-expand mute decay.
     *
     * <p>After each synthesized frame the mute factor and the current voice-mix weight step down so a longer
     * gap attenuates further toward the noise floor, the native recompute that turns monotonic once the run
     * reaches the fourth expansion.
     *
     * @implNote This implementation reproduces the native per-expand decay: the mute factor steps down by the
     * mute slope and the current voice-mix weight halves its distance toward zero, both clamped non-negative,
     * the monotonic floor the native body enforces once {@code consecutive_expands_ >= 4}.
     */
    private void decayMuteState() {
        muteFactor = Math.max(0, muteFactor - muteSlope);
        currentVoiceMixFactor = Math.max(0, currentVoiceMixFactor - (currentVoiceMixFactor >> 2));
    }
}
