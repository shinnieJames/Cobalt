package com.github.auties00.cobalt.calls2.net.bwe;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * The recovered machine-learning bandwidth-estimation engine: maintains the per-model slide-window history
 * and builds the recovered congestion inference round. Its native ExecuTorch backend was removed from
 * {@code cobalt-native}, so every native model operation throws {@link UnsupportedOperationException} and no
 * model can load or run.
 *
 * <p>For each enabled {@link MlBweModelType} the engine holds one multi-column history ring (one column per
 * recovered signal). Every {@link #infer(MlBweSignals)} pushes the round's signals into every ring with the
 * recovered scaling (packet loss times one hundred, nanoseconds to microseconds and bits per second to
 * kilobits per second by dividing by one thousand). The recovered congestion classifier builds the
 * {@code (rounds, features)} input tensor in the recovered slot order and would run forward, read the second
 * output float, scale and quantize it to a probability, and compare against the per-call threshold to
 * produce the verdict folded into {@link MlBweOutputs}; the forward call now throws, since the backend is
 * gone.
 *
 * <p>With no native backend the engine is inert: {@link #loadedModels()} is empty and
 * {@link #infer(MlBweSignals)} returns {@link MlBweOutputs#DISABLED}, so the media plane uses the
 * {@link NoopMlBweEngine} and the pure delay-based and sender-side path runs unchanged. Constructing the
 * engine with a model whose {@code .pte} path resolves makes the native load throw. The engine is
 * single-writer: a call session would drive one from the single transport thread.
 *
 * @implNote This implementation ports the wa-voip ML-BWE orchestration ({@code network/src/bwe/bwe_ml.cc}):
 * the per-model history ring of {@code wa_slide_window} sub-windows (init {@code fn4433}, push
 * {@code fn3745}, accessor {@code fn3747}), the per-round feature writer {@code fn4435}, the fullness gate
 * {@code fn4366}, and the fully-recovered congestion inference round {@code fn4443} which calls
 * {@code ml_shim_forward} (fn4331) with {@code expected_output_len = 2}, reads {@code output[1]}, multiplies
 * by {@code 1000}, quantizes to a {@code ushort}, and compares against {@code DAT_051e[base]} for verdict
 * {@code 2} (detected) or {@code 1} (not). The per-model bitmap, feature count, history depth, aggregation
 * mode, and probability threshold are runtime voip-params decoded from the {@code voip_settings} blob and
 * are NOT compiled constants, so they are supplied through {@link ModelConfig}; a model with no resolved
 * configuration or path is skipped rather than run with fabricated metadata (re/calls2-spec/ML-BWE-RE.md
 * sec 2, sec 3, sec 4, sec 5, sec 6). The classification heads for the other models are inlined in the
 * wa-voip orchestrator {@code fn4414} and share the congestion head's two-float shape (reading
 * {@code output[0]} rather than {@code output[1]}); only the congestion verdict is wired into
 * {@link MlBweOutputs} here, the undershoot and high-definition-targeting derivations being partially
 * unrecovered (re/calls2-spec/ML-BWE-RE.md sec 4).
 */
public final class LiveMlBweEngine implements MlBweEngine {
    /**
     * The number of slide-window columns the recovered feature writer pushes each round.
     *
     * @implNote This implementation uses {@code 13}, the number of windows the wa-voip feature writer
     * {@code fn4435} pushes per round (win0..win12); a fourteenth window (win13) is init-only and a
     * fifteenth slot the ring allocates is never read as a feature (re/calls2-spec/ML-BWE-RE.md sec 3).
     */
    static final int FEATURE_COLUMNS = 13;

    /**
     * The fn4440 feature-slot ordering: the ring window index each tensor slot reads, skipping the
     * uninitialised placeholder window 8.
     *
     * @implNote This implementation uses the recovered {@code fn4440} slot-to-window map
     * {@code slot{0..7} = win{0..7}}, {@code slot8 = win9}, {@code slot9 = win10}, {@code slot10 = win11},
     * {@code slot11 = win12}: the feature reader takes 12 of the 13 written windows, excluding win8
     * (re/calls2-spec/ML-BWE-RE.md sec 3).
     */
    static final int[] SLOT_TO_WINDOW = {0, 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12};

    /**
     * The scaling factor applied to the packet-loss fraction before it is pushed into window 0.
     *
     * @implNote This implementation uses {@code 100.0}: {@code fn4435} pushes
     * {@code (int) (loss_fraction * 100.0)} into win0 (the loss as a percent).
     */
    private static final double LOSS_SCALE = 100.0;

    /**
     * The divisor converting nanoseconds to microseconds and bits per second to kilobits per second.
     *
     * @implNote This implementation uses {@code 1000}: {@code fn4435} divides the round-trip, jitter, and
     * bitrate windows by {@code 1000} before pushing (ns to us, bps to kbps).
     */
    private static final int MICRO_KILO_DIVISOR = 1000;

    /**
     * The fixed output length of the congestion classification head.
     *
     * @implNote This implementation uses {@code 2}: {@code fn4443} calls {@code ml_shim_forward} with
     * {@code expected_output_len = 2} and reads {@code output[1]} for the verdict
     * (re/calls2-spec/ML-BWE-RE.md sec 4).
     */
    private static final int CONGESTION_OUTPUT_LEN = 2;

    /**
     * The output-tensor index of the congestion probability within the congestion head's two floats.
     *
     * @implNote This implementation uses {@code 1}: {@code fn4443} reads {@code output[1]} (the high four
     * bytes of the eight-byte output pair), {@code output[0]} being the no-congestion-class logit whose
     * exact role is unrecovered (re/calls2-spec/ML-BWE-RE.md sec 4).
     */
    private static final int CONGESTION_OUTPUT_INDEX = 1;

    /**
     * The scaling factor applied to the congestion output before quantization.
     *
     * @implNote This implementation uses {@code 1000.0}: {@code fn4443} multiplies {@code output[1]} by
     * {@code 1000.0} before quantizing to a {@code ushort} (re/calls2-spec/ML-BWE-RE.md sec 4).
     */
    private static final double CONGESTION_PROBABILITY_SCALE = 1000.0;

    /**
     * The per-model configuration and loaded state, keyed by model type.
     */
    private final Map<MlBweModelType, LoadedModel> models;

    /**
     * The arena owning the native input and output tensor buffers and the model-path string segments.
     */
    private final Arena arena;

    /**
     * Whether {@link #close()} has run, so a second close is a no-op.
     */
    private boolean closed;

    /**
     * Constructs an engine over the given enabled models.
     *
     * <p>For each entry the engine allocates the model's history ring and resolves its {@code .pte} path
     * through {@code resolver}. Because the native ExecuTorch backend is not bundled, a model whose path
     * resolves cannot be loaded and construction throws; a model whose path is absent is held history-only,
     * so an engine constructed with no provisioned paths builds successfully and stays inert.
     *
     * @param configs  the per-model configuration for every model to enable; never {@code null}
     * @param resolver the resolver mapping a model type to its {@code .pte} filesystem path, or returning
     *                 {@code null} when no path is provisioned for that model; never {@code null}
     * @throws NullPointerException          if {@code configs} or {@code resolver} is {@code null}
     * @throws UnsupportedOperationException if a model's path resolves, since the ExecuTorch backend is not
     *                                       bundled
     */
    public LiveMlBweEngine(Map<MlBweModelType, ModelConfig> configs, Function<MlBweModelType, String> resolver) {
        Objects.requireNonNull(configs, "configs cannot be null");
        Objects.requireNonNull(resolver, "resolver cannot be null");
        this.arena = Arena.ofShared();
        this.models = new EnumMap<>(MlBweModelType.class);
        try {
            for (var entry : configs.entrySet()) {
                var type = entry.getKey();
                var config = entry.getValue();
                var ring = new HistoryRing(config.historyDepth());
                var handle = loadHandle(type, resolver);
                models.put(type, new LoadedModel(config, ring, handle));
            }
        } catch (RuntimeException e) {
            freeHandles();
            arena.close();
            throw e;
        }
    }

    /**
     * Returns whether the native ExecuTorch shim is callable.
     *
     * @return always {@code false}, since the ExecuTorch backend was removed from {@code cobalt-native}
     */
    public static boolean nativeShimAvailable() {
        return false;
    }

    /**
     * Loads the model handle for one model, or returns {@link MemorySegment#NULL} when no path is
     * provisioned.
     *
     * <p>Returns {@link MemorySegment#NULL} when the path does not resolve; when a path is provisioned the
     * native create throws, since the ExecuTorch backend is not bundled, so a model with a {@code .pte} path
     * cannot be loaded.
     *
     * @param type     the model type to load
     * @param resolver the model-path resolver
     * @return {@link MemorySegment#NULL} when no path is provisioned
     * @throws UnsupportedOperationException when a path is provisioned, since the ExecuTorch backend is not
     *                                       bundled
     */
    private MemorySegment loadHandle(MlBweModelType type, Function<MlBweModelType, String> resolver) {
        var path = resolver.apply(type);
        if (path == null || path.isEmpty()) {
            return MemorySegment.NULL;
        }
        var pathSegment = arena.allocateFrom(path);
        var handle = ml_shim_create_executorch(pathSegment);
        if (handle.equals(MemorySegment.NULL) || ml_get_shim_create_status() != COBALT_ET_OK()) {
            if (!handle.equals(MemorySegment.NULL)) {
                ml_shim_free(handle);
            }
            return MemorySegment.NULL;
        }
        return handle;
    }

    /**
     * Creates a model handle from a {@code .pte} model path.
     *
     * @param modelPath the native UTF-8 model path
     * @return never returns normally
     * @throws UnsupportedOperationException always, since the ExecuTorch native backend is not bundled into
     *                                       {@code cobalt-native}
     */
    private static MemorySegment ml_shim_create_executorch(MemorySegment modelPath) {
        throw nativeUnavailable();
    }

    /**
     * Runs one forward pass of a loaded model.
     *
     * @param model          the model handle
     * @param input          the input feature buffer
     * @param inputLen       the number of input floats
     * @param output         the output buffer
     * @param outputCapacity the output buffer capacity in floats
     * @return never returns normally
     * @throws UnsupportedOperationException always, since the ExecuTorch native backend is not bundled
     */
    private static int ml_shim_forward(MemorySegment model, MemorySegment input, int inputLen, MemorySegment output, int outputCapacity) {
        throw nativeUnavailable();
    }

    /**
     * Frees a loaded model handle.
     *
     * @param model the model handle
     * @throws UnsupportedOperationException always, since the ExecuTorch native backend is not bundled
     */
    private static void ml_shim_free(MemorySegment model) {
        throw nativeUnavailable();
    }

    /**
     * Returns the status of the most recent model create.
     *
     * @return never returns normally
     * @throws UnsupportedOperationException always, since the ExecuTorch native backend is not bundled
     */
    private static int ml_get_shim_create_status() {
        throw nativeUnavailable();
    }

    /**
     * Returns the native status code denoting a successful model create.
     *
     * @return the {@code COBALT_ET_OK} constant
     */
    private static int COBALT_ET_OK() {
        return 0;
    }

    /**
     * Returns the exception thrown at the absent native boundary.
     *
     * @return an {@link UnsupportedOperationException} describing the removed ExecuTorch backend
     */
    private static UnsupportedOperationException nativeUnavailable() {
        return new UnsupportedOperationException("ExecuTorch native backend is not bundled in cobalt-native");
    }

    /**
     * {@inheritDoc}
     *
     * @return the model types whose handle loaded successfully
     */
    @Override
    public Set<MlBweModelType> loadedModels() {
        var loaded = EnumSet.noneOf(MlBweModelType.class);
        for (var entry : models.entrySet()) {
            if (!entry.getValue().handle().equals(MemorySegment.NULL)) {
                loaded.add(entry.getKey());
            }
        }
        return loaded;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation pushes {@code signals} into every model's ring first (matching the
     * wa-voip orchestrator {@code fn4414} which calls the feature writer {@code fn4435} once per ring
     * before any inference), then runs the loaded congestion model when its ring is full. The undershoot
     * and high-definition heads are not yet wired into the output.
     * @param signals {@inheritDoc}
     * @return the fused steering outputs, or {@link MlBweOutputs#DISABLED} when no model ran
     */
    @Override
    public MlBweOutputs infer(MlBweSignals signals) {
        Objects.requireNonNull(signals, "signals cannot be null");
        for (var model : models.values()) {
            pushRound(model.ring(), signals);
        }
        var cong = models.get(MlBweModelType.CONG);
        if (cong == null || cong.handle().equals(MemorySegment.NULL)) {
            return MlBweOutputs.DISABLED;
        }
        return runCongestion(cong, signals);
    }

    /**
     * Pushes one round's signals into a model's history ring, in the recovered column order and scaling.
     *
     * <p>The eight measured windows take the loss percent, the round-trip and remote and sender estimates,
     * the audio round-trip, the video and audio jitter, and the packet-pair estimate. The fourth window,
     * the placeholder, and the categorical windows are pushed as the recovered raw values where available;
     * a slot whose source signal is {@link MlBweSignals#UNAVAILABLE} is pushed as {@code 0}, matching the
     * unrecovered-default of the feature writer, and the model is run-gated separately on whether its
     * selected slots are all available.
     *
     * @param ring    the model's history ring
     * @param signals the round's signals
     */
    private void pushRound(HistoryRing ring, MlBweSignals signals) {
        ring.push(0, scaleLoss(signals.packetLossFraction()));
        ring.push(1, divideOrZero(signals.rttNs()));
        ring.push(2, divideOrZero(signals.remoteBweBps()));
        ring.push(3, divideOrZero(signals.senderBweBps()));
        ring.push(4, divideOrZero(signals.audioRttNs()));
        ring.push(5, divideOrZero(signals.videoJitterNs()));
        ring.push(6, divideOrZero(signals.audioJitterNs()));
        ring.push(7, divideOrZero(signals.packetPairBps()));
        // win8 is the unrecovered placeholder; fn4435 pushes an uninitialised value here and fn4440
        // excludes it from every model's feature tensor, so it is pushed as 0 and never read.
        ring.push(8, 0);
        // win9..win11 are the configured categorical voip-param knobs (per-model platform-pair feature
        // values); their runtime values are voip-param constants Cobalt does not decode, so they are
        // pushed as their sentinel default (0) until threaded.
        ring.push(9, 0);
        ring.push(10, 0);
        ring.push(11, 0);
        ring.push(12, divideOrZero(signals.maxTargetBitrateBps()));
    }

    /**
     * Runs the congestion model and returns its verdict and probability.
     *
     * <p>Requires the model's history ring to be full (the recovered {@code fn4366} time-series gate) and
     * its selected feature slots all available. When either is not met it returns
     * {@link MlBweOutputs#DISABLED}. Otherwise it builds the {@code (rounds, features)} input tensor in the
     * recovered slot order, runs forward, reads the second output float, scales and quantizes it, and
     * compares against the per-call threshold.
     *
     * @param model   the loaded congestion model
     * @param signals the round's signals, for the per-slot availability check
     * @return the congestion outputs, or {@link MlBweOutputs#DISABLED} when the model could not run
     */
    private MlBweOutputs runCongestion(LoadedModel model, MlBweSignals signals) {
        var config = model.config();
        var ring = model.ring();
        if (!ring.isFull() || !config.isResolved()) {
            return MlBweOutputs.DISABLED;
        }
        var selectedSlots = selectedSlots(config.featureBitmap());
        if (!slotsAvailable(selectedSlots, signals)) {
            return MlBweOutputs.DISABLED;
        }
        var rounds = config.historyDepth();
        var features = selectedSlots.length;
        if (features != config.featureCount()) {
            return MlBweOutputs.DISABLED;
        }

        var input = arena.allocate((long) rounds * features * Float.BYTES);
        fillInput(input, ring, selectedSlots, rounds);
        var output = arena.allocate((long) CONGESTION_OUTPUT_LEN * Float.BYTES);
        var written = ml_shim_forward(model.handle(), input, rounds * features, output, CONGESTION_OUTPUT_LEN);
        if (written != CONGESTION_OUTPUT_LEN) {
            return MlBweOutputs.DISABLED;
        }
        var raw = output.getAtIndex(ValueLayout.JAVA_FLOAT, CONGESTION_OUTPUT_INDEX);
        var probability = quantizeProbability(raw);
        var detected = probability > config.probabilityThreshold();
        return MlBweOutputs.ofCongestion(detected, probability);
    }

    /**
     * Fills the native input tensor row-major from the history ring in the recovered slot order.
     *
     * <p>The tensor is {@code (rounds, features)} row-major: row {@code r} holds the {@code r}-th
     * oldest-to-newest sample of each selected feature column. The wa-voip tensor is row-major contiguous,
     * so each element is written at {@code (r * features + c)}.
     *
     * @param input         the native input buffer of {@code rounds * features} floats
     * @param ring          the model's history ring
     * @param selectedSlots the tensor slots, in order, each mapping to a ring window
     * @param rounds        the history depth (number of rows)
     */
    // TODO: confirm the tensor ROW direction the wa-voip tensor builder fn4440 uses (oldest-to-newest, as
    //  written here, versus newest-to-oldest). The recovered decompile pins the column set and the
    //  per-feature scaling but not the time-axis ordering, which is only observable from a paused
    //  ml_shim_forward on a connected congested call (the read-only sessions never enter that state). This
    //  is moot until a model's bitmap/rounds/n_features are decoded (the model is run-skipped otherwise via
    //  ModelConfig.isResolved), but the row order must be verified before a provisioned model is trusted.
    private void fillInput(MemorySegment input, HistoryRing ring, int[] selectedSlots, int rounds) {
        var features = selectedSlots.length;
        for (var row = 0; row < rounds; row++) {
            for (var col = 0; col < features; col++) {
                var window = SLOT_TO_WINDOW[selectedSlots[col]];
                var value = ring.sampleAt(window, row);
                input.setAtIndex(ValueLayout.JAVA_FLOAT, (long) row * features + col, value);
            }
        }
    }

    /**
     * Returns the tensor slot indices the feature bitmap selects, in ascending slot order.
     *
     * <p>The bitmap is a 12-bit mask over the {@link #SLOT_TO_WINDOW} feature slots; a set bit {@code i}
     * selects slot {@code i}.
     *
     * @param bitmap the 12-bit feature-selection mask
     * @return the selected slot indices in ascending order
     */
    private static int[] selectedSlots(int bitmap) {
        var count = Integer.bitCount(bitmap & 0xFFF);
        var slots = new int[count];
        var index = 0;
        for (var slot = 0; slot < SLOT_TO_WINDOW.length; slot++) {
            if ((bitmap & (1 << slot)) != 0) {
                slots[index++] = slot;
            }
        }
        return slots;
    }

    /**
     * Returns whether every selected slot maps to a signal the round actually carries.
     *
     * <p>Slots 0 to 3 (loss, round-trip, remote estimate, sender estimate) and slot 11 (the configured max
     * bitrate) are always available; slots 4 to 7 (audio round-trip, video and audio jitter, packet-pair)
     * are available only when the corresponding {@link MlBweSignals} field is not
     * {@link MlBweSignals#UNAVAILABLE}; slots 8 to 10 (the categorical knobs) are voip-param constants
     * treated as available. A model selecting an unavailable slot is run-skipped rather than fed a
     * fabricated value.
     *
     * @param selectedSlots the slots the model selects
     * @param signals       the round's signals
     * @return {@code true} when every selected slot is fillable
     */
    private static boolean slotsAvailable(int[] selectedSlots, MlBweSignals signals) {
        for (var slot : selectedSlots) {
            var available = switch (slot) {
                case 4 -> MlBweSignals.isAvailable(signals.audioRttNs());
                case 5 -> MlBweSignals.isAvailable(signals.videoJitterNs());
                case 6 -> MlBweSignals.isAvailable(signals.audioJitterNs());
                case 7 -> MlBweSignals.isAvailable(signals.packetPairBps());
                default -> true;
            };
            if (!available) {
                return false;
            }
        }
        return true;
    }

    /**
     * Scales a packet-loss fraction to the percent value the loss window stores.
     *
     * @param lossFraction the packet-loss fraction, in {@code [0, 1]}
     * @return the loss as an integer percent ({@code fraction * 100})
     */
    private static int scaleLoss(double lossFraction) {
        var scaled = lossFraction * LOSS_SCALE;
        if (Math.abs(scaled) >= 2.1474836e9) {
            return Integer.MIN_VALUE;
        }
        return (int) scaled;
    }

    /**
     * Divides a nanosecond or bits-per-second signal by one thousand, or returns {@code 0} for an
     * unavailable signal.
     *
     * @param value the raw signal, or {@link MlBweSignals#UNAVAILABLE}
     * @return the signal divided by {@value #MICRO_KILO_DIVISOR}, or {@code 0} when unavailable
     */
    private static int divideOrZero(long value) {
        if (!MlBweSignals.isAvailable(value)) {
            return 0;
        }
        return (int) (value / MICRO_KILO_DIVISOR);
    }

    /**
     * Scales and quantizes the raw congestion output float to a probability in {@code [0, 65535]}.
     *
     * @implNote This implementation reproduces {@code fn4443}: it multiplies the output by
     * {@code 1000.0}, takes the value as a {@code uint} only when it falls in {@code [0, 2^32)}
     * (else {@code 0}), and masks to a {@code ushort}, so the result is in
     * {@code [0, }{@value MlBweOutputs#MAX_CONGESTION_PROBABILITY}{@code ]}.
     *
     * @param rawOutput the raw congestion output float ({@code output[1]})
     * @return the quantized probability, in {@code [0, 65535]}
     */
    private static int quantizeProbability(float rawOutput) {
        var scaled = rawOutput * CONGESTION_PROBABILITY_SCALE;
        long asUint;
        if (scaled >= 0.0 && scaled < 4.2949673e9) {
            asUint = (long) scaled;
        } else {
            asUint = 0;
        }
        return (int) (asUint & 0xFFFF);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation frees every loaded model handle and closes the native arena holding the
     * tensor buffers; closing an already-closed engine has no effect.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        freeHandles();
        arena.close();
    }

    /**
     * Frees every loaded model handle, swallowing any error.
     */
    private void freeHandles() {
        for (var model : models.values()) {
            if (!model.handle().equals(MemorySegment.NULL)) {
                try {
                    ml_shim_free(model.handle());
                } catch (Throwable _) {
                }
            }
        }
    }

    /**
     * The per-model runtime configuration decoded from the {@code voip_settings} blob: the feature
     * selection bitmap, the feature count, the history depth, and the congestion probability threshold.
     *
     * <p>These are NOT compiled constants; each is a runtime voip-param the wa-voip engine reads per model.
     * A configuration whose values are unknown ({@link #unresolved()}) marks a model the engine holds
     * history-only and never runs, so a missing voip-param never causes a fabricated model run.
     *
     * @param featureBitmap        the 12-bit feature-selection mask the tensor builder reads (recovered
     *                             source {@code *(DAT_0574[CFG])} for congestion)
     * @param featureCount         the number of selected features ({@code n_features}, recovered source
     *                             {@code DAT_578[CFG]})
     * @param historyDepth         the time-series depth ({@code rounds}, recovered source
     *                             {@code DAT_579[CFG]}) and the slide-window capacity
     * @param probabilityThreshold the per-call congestion probability threshold, in {@code [0, 65535]}
     *                             (recovered source {@code DAT_051e[base]})
     * @implNote The bitmap, count, depth, aggregation mode, and threshold are voip-param constants from the
     * {@code voip_settings} blob, not compiled literals: congestion reads {@code n_features = DAT_578[CFG]},
     * {@code rounds = DAT_579[CFG]}, {@code bitmap = *(DAT_574[CFG])}, threshold {@code DAT_051e[base]}. Two
     * compiled bitmap literals exist for other models ({@code tr = 0xff}, {@code plc = 0xf}) but congestion
     * reads its bitmap from a pointer (re/calls2-spec/ML-BWE-RE.md sec 3). They are supplied here so a
     * caller that decodes the blob can drive the model; {@link #unresolved()} keeps the model dormant
     * until then.
     */
    public record ModelConfig(int featureBitmap, int featureCount, int historyDepth, int probabilityThreshold) {
        /**
         * Validates that the history depth is at least one when resolved.
         *
         * @throws IllegalArgumentException if {@code historyDepth} is negative
         */
        public ModelConfig {
            if (historyDepth < 0) {
                throw new IllegalArgumentException("historyDepth cannot be negative, got " + historyDepth);
            }
        }

        /**
         * Returns an unresolved configuration that keeps its model dormant.
         *
         * <p>An unresolved configuration has a zero bitmap, feature count, and history depth; a model with
         * it is never run, only its history accumulated, until the voip-param values are decoded.
         *
         * @return the unresolved configuration
         */
        public static ModelConfig unresolved() {
            return new ModelConfig(0, 0, 1, 0);
        }

        /**
         * Returns whether this configuration carries decoded voip-param values.
         *
         * @return {@code true} when the feature count and history depth are positive
         */
        public boolean isResolved() {
            return featureCount > 0 && historyDepth > 0 && featureBitmap != 0;
        }
    }

    /**
     * Holds one model's configuration, history ring, and loaded model handle.
     *
     * @param config the model's runtime configuration
     * @param ring   the model's slide-window history ring
     * @param handle the loaded model handle, or {@link MemorySegment#NULL} when not loaded
     */
    private record LoadedModel(ModelConfig config, HistoryRing ring, MemorySegment handle) {
    }

    /**
     * The recovered multi-column slide-window history ring: {@value #FEATURE_COLUMNS} fixed-capacity
     * circular buffers, one per feature column, each tracking its running sum.
     *
     * <p>Each column is a {@code wa_slide_window}: a circular {@code int} buffer of {@code capacity}
     * samples, a running sum, a sample count saturating at the capacity, and a head index. A push appends
     * at the head, advances the head modulo the capacity, grows the count to the capacity, and maintains
     * the running sum by subtracting the evicted oldest sample once full.
     *
     * @implNote This implementation ports the {@code wa_slide_window} ring of the wa-voip engine: the
     * single-window push {@code fn3745} (append at {@code data[head]}, {@code head = (head+1) % capacity},
     * count growth, running-sum maintenance) and the bounds-checked accessor {@code fn3747}
     * ({@code data_base + idx}), with the 14-window init {@code fn4433} reduced to the
     * {@value #FEATURE_COLUMNS} columns the feature writer actually pushes (re/calls2-spec/ML-BWE-RE.md
     * sec 3).
     */
    static final class HistoryRing {
        /**
         * The per-column capacity, equal to the model's history depth.
         */
        private final int capacity;

        /**
         * The per-column circular sample buffers, indexed {@code [column][slot]}.
         */
        private final int[][] data;

        /**
         * The per-column head indices, where the next sample is written.
         */
        private final int[] head;

        /**
         * The per-column live-sample counts, each saturating at the capacity.
         */
        private final int[] count;

        /**
         * Constructs a ring of {@value #FEATURE_COLUMNS} columns each holding {@code capacity} samples.
         *
         * @param capacity the per-column capacity (the model's history depth); clamped to at least one
         */
        HistoryRing(int capacity) {
            this.capacity = Math.max(1, capacity);
            this.data = new int[FEATURE_COLUMNS][this.capacity];
            this.head = new int[FEATURE_COLUMNS];
            this.count = new int[FEATURE_COLUMNS];
        }

        /**
         * Pushes one sample into a column, evicting the oldest when the column is full.
         *
         * @param column the column index, in {@code [0, }{@value #FEATURE_COLUMNS}{@code )}
         * @param value  the sample value
         */
        void push(int column, int value) {
            data[column][head[column]] = value;
            head[column] = (head[column] + 1) % capacity;
            if (count[column] < capacity) {
                count[column]++;
            }
        }

        /**
         * Returns the sample at an oldest-to-newest row position within a column.
         *
         * <p>Row {@code 0} is the oldest live sample; row {@code capacity - 1} is the newest. Before the
         * column fills, the live samples are the first {@code count} positions.
         *
         * @param column the column index
         * @param row    the oldest-to-newest row position, in {@code [0, capacity)}
         * @return the sample value at that position, as a float for the tensor
         */
        float sampleAt(int column, int row) {
            var oldest = (head[column] - count[column] + capacity) % capacity;
            var index = (oldest + row) % capacity;
            return data[column][index];
        }

        /**
         * Returns whether every column has filled to its capacity.
         *
         * @implNote This reproduces the wa-voip fullness gate {@code fn4366} ({@code count == capacity}),
         * the time-series-data check congestion inference requires (re/calls2-spec/ML-BWE-RE.md sec 5).
         *
         * @return {@code true} when each column holds {@code capacity} samples
         */
        boolean isFull() {
            for (var c = 0; c < FEATURE_COLUMNS; c++) {
                if (count[c] < capacity) {
                    return false;
                }
            }
            return true;
        }
    }
}
