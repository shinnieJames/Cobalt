package com.github.auties00.cobalt.calls2.net.bwe;

/**
 * Holds the steering outputs a machine-learning bandwidth-estimation inference round produces, which
 * the sender estimator folds into its decisions.
 *
 * <p>The flags and values mirror the outputs the recovered inference path exposes through its getters:
 * a congestion verdict with its quantized probability, an undershoot verdict, a predicted loss ratio, and
 * a high-definition target bitrate. The {@link #DISABLED} instance is the value the
 * {@link NoopMlBweEngine} returns so the pure delay-based and sender-side path runs unchanged when machine
 * learning is off.
 *
 * @param congestionDetected     whether the congestion model reported congestion (the recovered verdict
 *                               {@code 2})
 * @param congestionProbability  the quantized congestion probability, in {@code [0, 65535]}; {@code 0}
 *                               when no congestion inference ran
 * @param undershootDetected     whether an undershoot model reported the estimate has undershot link
 *                               capacity
 * @param predictedLoss          the predicted packet-loss ratio, in {@code [0, 1]}; {@code 0} when no
 *                               prediction is available
 * @param hdTargetBps            the high-definition target bitrate, in bits per second; {@code 0} when no
 *                               target is available
 * @implNote This record models the consumed outputs of the wa-voip engine ({@code network/src/bwe/bwe_ml.cc}):
 * {@code congestionDetected} and {@code congestionProbability} come from the fully-recovered congestion
 * round {@code fn4443}, which reads {@code output[1]}, multiplies by {@code 1000}, quantizes to a
 * {@code ushort} in {@code [0, 65535]}, and compares against the per-call threshold {@code DAT_051e[base]}
 * to yield verdict {@code 2} (detected) or {@code 1} (not). {@code undershootDetected} comes from the
 * undershoot head's verdict. {@code predictedLoss} would come from the PLC head, whose feature/output is
 * unrecovered (it lives in the audio jitter-buffer path), so it is left {@code 0}. {@code hdTargetBps}
 * would come from the high-definition-targeting head, but that head yields a boolean positive/negative
 * verdict and the bitrate derivation is unrecovered, so it is left {@code 0}
 * (re/calls2-spec/ML-BWE-RE.md sec 4).
 */
public record MlBweOutputs(
        boolean congestionDetected,
        int congestionProbability,
        boolean undershootDetected,
        double predictedLoss,
        long hdTargetBps
) {
    /**
     * The maximum quantized congestion probability, the upper bound of the recovered {@code ushort} range.
     *
     * @implNote This implementation uses {@code 65535}, the {@code ushort} ceiling the congestion round
     * {@code fn4443} quantizes the scaled probability into ({@code (ushort) (output[1] * 1000)}).
     */
    public static final int MAX_CONGESTION_PROBABILITY = 65535;

    /**
     * The outputs returned when machine learning is disabled: no congestion, no undershoot, no loss
     * prediction, and no high-definition target.
     */
    public static final MlBweOutputs DISABLED = new MlBweOutputs(false, 0, false, 0.0, 0);

    /**
     * Returns a congestion-only output carrying the verdict and its quantized probability.
     *
     * <p>This is the output the fully-recovered congestion head produces: the verdict and the probability,
     * with no undershoot, loss prediction, or high-definition target.
     *
     * @param detected    whether the congestion model reported congestion (verdict {@code 2})
     * @param probability the quantized congestion probability, in {@code [0, 65535]}
     * @return the congestion-only outputs
     */
    public static MlBweOutputs ofCongestion(boolean detected, int probability) {
        if (!detected && probability == 0) {
            return DISABLED;
        }
        return new MlBweOutputs(detected, probability, false, 0.0, 0);
    }
}
