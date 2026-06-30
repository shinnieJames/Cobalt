package com.github.auties00.cobalt.calls2.net.bwe;

import java.util.Set;

/**
 * Abstracts the machine-learning bandwidth-estimation inference engine behind one seam so the sender
 * estimator can run with or without it.
 *
 * <p>An engine loads the enabled {@link MlBweModelType} models and runs one inference round per feedback
 * cadence from the round's {@link MlBweSignals}, returning {@link MlBweOutputs} the sender estimator folds
 * into its congestion, loss, and high-definition-targeting decisions. The default engine,
 * {@link NoopMlBweEngine}, loads nothing and always returns {@link MlBweOutputs#DISABLED}, so the pure
 * delay-based and sender-side path runs standalone. {@link LiveMlBweEngine} is the recovered implementation
 * that would run the congestion model through its native backend; with that ExecuTorch backend removed it is
 * inert and its native operations throw. The recovered design is in re/calls2-spec/ML-BWE-RE.md.
 *
 * <p>Implementations are not required to be thread-safe; the call session drives one engine from the
 * single transport thread.
 *
 * @implNote Machine learning is gated by the per-model {@code cc_enable_ml_*_inference} voip-params
 * ({@code should_load_*} flags) in the wa-voip engine ({@code bwe/bwe_ml.cc}) and is fully optional, so a
 * Noop engine yields a complete working estimator (re/calls2-spec/ML-BWE-RE.md sec 2, sec 5).
 */
public sealed interface MlBweEngine permits NoopMlBweEngine, LiveMlBweEngine {
    /**
     * Returns the set of model types this engine has loaded and will run.
     *
     * <p>The {@link NoopMlBweEngine} returns an empty set.
     *
     * @return the loaded model types, possibly empty
     */
    Set<MlBweModelType> loadedModels();

    /**
     * Runs one inference round from the round's signals and returns the steering outputs.
     *
     * <p>The engine pushes {@code signals} into each enabled model's history ring, runs the models whose
     * full feature selection can be filled, and returns their fused verdicts. The {@link NoopMlBweEngine}
     * returns {@link MlBweOutputs#DISABLED}.
     *
     * @implSpec An implementation must push {@code signals} into its history before running any model, must
     * not fabricate a feature whose source slot is {@link MlBweSignals#UNAVAILABLE}, and must return
     * {@link MlBweOutputs#DISABLED} when no model runs.
     * @param signals the per-round network signals to feed the models; never {@code null}
     * @return the steering outputs for this round
     * @throws NullPointerException if {@code signals} is {@code null}
     */
    MlBweOutputs infer(MlBweSignals signals);

    /**
     * Returns whether this engine runs any model.
     *
     * @return {@code true} when at least one model is loaded
     */
    default boolean isEnabled() {
        return !loadedModels().isEmpty();
    }

    /**
     * Releases any native resources the engine holds.
     *
     * <p>The {@link NoopMlBweEngine} holds none and does nothing. Closing an already-closed engine has no
     * effect.
     */
    void close();
}
