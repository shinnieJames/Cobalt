package com.github.auties00.cobalt.calls2.media.audio;

/**
 * Adapts the Opus encoder complexity at runtime from a rolling measurement of how much encode time the
 * encoder spends per second of audio, restoring complexity toward its configured level when there is
 * timing headroom and lowering it (permanently capping if necessary) when the encoder runs over budget.
 *
 * <p>The encode path feeds every frame's measured encode time and duration into
 * {@link #recordEncode(long, long)}; the controller accumulates them into a window and, once the window
 * has covered at least the evaluation span, computes the average encode time per second of audio in the
 * same milliseconds-of-encode-per-second-of-audio unit as the budget. When that average sits below eight
 * times the budget the encoder has timing headroom and complexity is raised by one toward its cap, but
 * only on a longer gating interval so a brief lull does not immediately climb; when the average exceeds
 * ten times the budget the complexity is halved, a peak-tracking hysteresis advances, and once the
 * encoder has persistently overrun the controller lowers a permanent ceiling so complexity can never
 * climb back above the level the device sustains. The window resets after every evaluation so the
 * estimate tracks current CPU load rather than the whole call. The controller only computes target
 * levels; the codec reads {@link #complexity()} and applies it through its complexity control.
 *
 * <p>The cap starts at the configured complexity, so the controller never raises complexity above the
 * level the call was opened with; the increase step only restores complexity after a prior decrease, up
 * to that original ceiling, and the ceiling itself only ever drops.
 *
 * <p>Instances are not thread-safe; the codec drives one controller from its single encode thread.
 *
 * @implNote This implementation ports the adaptive-complexity branch of {@code opus_codec_encode}
 * (fn6266) in the wa-voip WASM module {@code ff-tScznZ8P}
 * (tree/xplat/wa-voip/wacall/media/src/codec/wa_opus.cc lines 1844-1936), whose state block lives at
 * {@code OpusCodecInstance+0x300} (window audio-ms accumulator), {@code +0x308} (window encode-ns
 * accumulator), {@code +0x310} (budget), {@code +0x314} (complexity cap), {@code +0x318} (increase-gate
 * accumulator), {@code +0x320} (tracked complexity peak), and {@code +0x324} (sustained-overrun counter).
 * The recovered control flow is: evaluate once at least 500 ms of audio has accumulated
 * ({@code 499 < uVar15}, line 1844); compute the average via fn6262 as encode-ns scaled to milliseconds
 * of encode per second of audio (matching the "avg encode time: %d ms/s" log at offset 0x3bca8); when
 * the average is below {@code budget << 3} (eight times the budget, line 1846) accumulate audio into the
 * increase gate and, once it passes {@code 1999 < +0x318} (line 1849), raise complexity by one if the
 * current level is below the cap ({@code local_20 < +0x314}, line 1857); when the average exceeds
 * {@code budget * 10} (line 1882) raise the cap to at least the current level, and if the current level
 * exceeds one, halve it ({@code local_20 / 2}, line 1916) and advance the peak/overrun hysteresis,
 * lowering the cap to {@code peak - 1} once the overrun counter passes {@code 8 < iVar8} (line 1907,
 * logging "Permanently capping encoder complexity at %d" at offset 0x99dd3); reset both window
 * accumulators after every evaluation (lines 1934-1935). The decrease branch's intermediate band
 * (average between eight and ten times the budget) and the at-peak increase branch both decay the
 * overrun counter toward zero (lines 1871-1876, 1926-1931). The budget is the value at {@code +0x310},
 * seeded from {@code codec_param[0x116]} clamped to {@code 1..100} and defaulting to {@code 10} ms/s
 * (fn6258 lines 742-748, default {@code codec_param[0x116] == 0} at line 126).
 */
public final class AdaptiveComplexityController {
    /**
     * Minimum accumulated audio, in milliseconds, before the window yields an estimate.
     *
     * <p>The native check is {@code 499 < uVar15}; the controller evaluates once the accumulated audio
     * duration reaches this span.
     */
    private static final long EVAL_WINDOW_MILLIS = 500;

    /**
     * Accumulated audio span, in milliseconds, of the increase gate that an increase step waits for.
     *
     * <p>The native check is {@code 1999 < +0x318}: even when the encoder has timing headroom the
     * complexity is raised by one only once the gate accumulator passes this span, so a brief lull does
     * not immediately climb.
     */
    private static final long INCREASE_GATE_MILLIS = 2000;

    /**
     * Multiple of the per-second budget below which the average encode time leaves the encoder eligible
     * to raise complexity.
     *
     * <p>The native test is {@code iVar7 < budget << 3}, i.e. an average below eight times the budget
     * selects the increase branch; an average at or above it selects the over-budget branch.
     */
    private static final int INCREASE_BUDGET_MULTIPLE = 8;

    /**
     * Multiple of the per-second budget above which the average encode time halves the complexity.
     *
     * <p>The native test is {@code budget * 10 < iVar7}: only when the average exceeds ten times the
     * budget does the over-budget branch halve the complexity and advance the hysteresis.
     */
    private static final int DECREASE_BUDGET_MULTIPLE = 10;

    /**
     * Sustained-overrun counter value beyond which the permanent cap is lowered.
     *
     * <p>The native test is {@code 8 < iVar8} on the pre-increment counter at {@code +0x324}, so the cap
     * is lowered on the ninth consecutive at-or-below-peak overrun.
     */
    private static final int SUSTAINED_OVERRUN_LIMIT = 8;

    /**
     * The lowest complexity level libopus accepts.
     */
    private static final int MIN_COMPLEXITY = 0;

    /**
     * The highest complexity level libopus accepts.
     */
    private static final int MAX_COMPLEXITY = 10;

    /**
     * The per-second encode-time budget, in milliseconds of encode time per second of audio.
     *
     * <p>The average measured encode time per second is compared against multiples of this budget to
     * decide whether to raise or lower complexity.
     */
    private final long budgetMillisPerSecond;

    /**
     * The current complexity level the encoder should run at.
     *
     * <p>Raised by one or halved per evaluation, bounded by {@code [0, 10]} intersected with
     * {@link #complexityCap}.
     */
    private int currentComplexity;

    /**
     * The permanent upper bound on complexity, seeded to the configured complexity and lowered after
     * sustained overruns.
     *
     * <p>Complexity can never climb back above this ceiling, so a slow device settles at a sustainable
     * level and a healthy device only ever restores complexity up to the level the call was opened with.
     */
    private int complexityCap;

    /**
     * Accumulated encode wall time across the current window, in microseconds.
     */
    private long windowEncodeMicros;

    /**
     * Accumulated audio duration across the current window, in milliseconds.
     */
    private long windowAudioMillis;

    /**
     * Accumulated audio duration, in milliseconds, gating the increase step.
     *
     * <p>Advanced only while the encoder has timing headroom and cleared once it passes
     * {@link #INCREASE_GATE_MILLIS} or the over-budget branch is taken.
     */
    private long increaseGateMillis;

    /**
     * The highest complexity level observed entering the halving branch, the peak the overrun hysteresis
     * is anchored to.
     *
     * <p>Corresponds to {@code +0x320}: a new peak resets the overrun counter to one, an at-peak overrun
     * advances it, and lowering the permanent cap clears both this and the counter.
     */
    private int complexityPeak;

    /**
     * Count of consecutive at-or-below-peak over-budget evaluations, advancing toward the cap lowering.
     */
    private int sustainedOverruns;

    /**
     * Whether the most recent evaluation changed the complexity level.
     */
    private boolean changed;

    /**
     * Constructs a controller with the given per-second encode-time budget and initial complexity.
     *
     * <p>The permanent cap starts at the initial complexity, so the controller may restore complexity up
     * to the configured level after a decrease but never raise it above that level; a sustained overrun
     * lowers the ceiling further.
     *
     * @param budgetMillisPerSecond the encode-time budget in milliseconds of encode time per second of
     *                              audio; must be positive
     * @param initialComplexity     the starting complexity level, clamped into {@code 0..10}
     * @throws IllegalArgumentException if {@code budgetMillisPerSecond} is not positive
     */
    public AdaptiveComplexityController(long budgetMillisPerSecond, int initialComplexity) {
        if (budgetMillisPerSecond <= 0) {
            throw new IllegalArgumentException("budgetMillisPerSecond must be positive: " + budgetMillisPerSecond);
        }
        this.budgetMillisPerSecond = budgetMillisPerSecond;
        this.currentComplexity = Math.clamp(initialComplexity, MIN_COMPLEXITY, MAX_COMPLEXITY);
        this.complexityCap = this.currentComplexity;
    }

    /**
     * Feeds one frame's encode time and audio duration into the window and evaluates the complexity when
     * the window is full.
     *
     * <p>The encode time and duration accumulate; once the accumulated audio reaches
     * {@link #EVAL_WINDOW_MILLIS} the average encode time per second is computed, the complexity is
     * adjusted, and the window resets. The {@linkplain #complexity() complexity} this controller reports
     * reflects the latest evaluation.
     *
     * @param encodeMicros the wall time the native encode of this frame took, in microseconds
     * @param frameMillis  the audio duration of this frame, in milliseconds
     */
    public void recordEncode(long encodeMicros, long frameMillis) {
        changed = false;
        windowEncodeMicros += encodeMicros;
        windowAudioMillis += frameMillis;
        if (windowAudioMillis > EVAL_WINDOW_MILLIS - 1) {
            evaluate();
            windowEncodeMicros = 0;
            windowAudioMillis = 0;
        }
    }

    /**
     * Computes the average encode time per second over the window and adjusts the complexity.
     *
     * <p>When the average is below {@link #INCREASE_BUDGET_MULTIPLE} times the budget the encoder has
     * headroom and {@link #increase()} restores complexity once the increase gate has filled; when the
     * average exceeds {@link #DECREASE_BUDGET_MULTIPLE} times the budget {@link #decrease()} halves
     * complexity and advances the hysteresis; the intermediate band only decays the overrun counter.
     */
    private void evaluate() {
        var avgMillisPerSecond = windowEncodeMicros / windowAudioMillis;
        if (avgMillisPerSecond < budgetMillisPerSecond * INCREASE_BUDGET_MULTIPLE) {
            increaseGateMillis += windowAudioMillis;
            if (increaseGateMillis > INCREASE_GATE_MILLIS - 1) {
                increaseGateMillis = 0;
                increase();
            }
        } else {
            increaseGateMillis = 0;
            if (avgMillisPerSecond > budgetMillisPerSecond * DECREASE_BUDGET_MULTIPLE) {
                decrease();
            } else if (complexityPeak == 0) {
                decayOverruns();
            }
        }
    }

    /**
     * Halves the complexity, raises the cap to at least the current level, and advances the
     * sustained-overrun hysteresis, lowering the permanent cap to one below the tracked peak once the
     * overrun counter passes its limit.
     *
     * <p>Does nothing to the level when the current complexity is one or less, matching the native guard
     * {@code 1 < local_20}: the cap is still raised but neither the halving nor the hysteresis advances.
     */
    private void decrease() {
        complexityCap = Math.max(complexityCap, currentComplexity);
        if (currentComplexity <= 1) {
            return;
        }
        if (complexityPeak < currentComplexity) {
            complexityPeak = currentComplexity;
            sustainedOverruns = 1;
        } else {
            var previousOverruns = sustainedOverruns;
            sustainedOverruns = previousOverruns + 1;
            if (previousOverruns > SUSTAINED_OVERRUN_LIMIT) {
                if (complexityPeak <= complexityCap) {
                    complexityCap = complexityPeak - 1;
                }
                complexityPeak = 0;
                sustainedOverruns = 0;
            }
        }
        var next = currentComplexity / 2;
        if (next != currentComplexity) {
            currentComplexity = next;
            changed = true;
        }
    }

    /**
     * Raises the complexity by one step, bounded by the permanent cap, or decays the overrun hysteresis
     * when the current level already sits at the tracked peak.
     *
     * <p>When the current level is below the cap it is raised by one; when it equals the tracked peak the
     * overrun counter is decayed instead, matching the native at-peak branch {@code local_20 == +0x320}.
     */
    private void increase() {
        if (currentComplexity < complexityCap) {
            var next = Math.min(complexityCap, currentComplexity + 1);
            if (next != currentComplexity) {
                currentComplexity = next;
                changed = true;
            }
        } else if (currentComplexity == complexityPeak) {
            decayOverruns();
        }
    }

    /**
     * Decays the sustained-overrun counter by one, never below zero.
     *
     * <p>Models the native {@code +0x324 = max(0, +0x324 - 1)} decay taken on an at-peak increase or on
     * an over-budget evaluation in the intermediate band, which lets the hysteresis recover when overruns
     * stop being consecutive.
     */
    private void decayOverruns() {
        if (sustainedOverruns > 0) {
            sustainedOverruns--;
        }
    }

    /**
     * Returns the complexity level the encoder should currently run at.
     *
     * @return the current complexity level, {@code 0..10}
     */
    public int complexity() {
        return currentComplexity;
    }

    /**
     * Returns the permanent upper bound on complexity after any sustained-overrun lowering.
     *
     * @return the current complexity cap, {@code 0..10}
     */
    public int complexityCap() {
        return complexityCap;
    }

    /**
     * Returns whether the most recent {@link #recordEncode(long, long)} call changed the complexity
     * level.
     *
     * <p>The codec uses this to apply the complexity control only when the level actually moved, sparing
     * a native call on every frame.
     *
     * @return {@code true} if the last record changed the complexity
     */
    public boolean complexityChanged() {
        return changed;
    }
}
