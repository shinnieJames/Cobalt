package com.github.auties00.cobalt.calls2.net.ratecontrol;

import com.github.auties00.cobalt.calls2.media.audio.OpusCodecParams;

/**
 * Turns the combined bandwidth-estimate target into the audio encoder's bitrate, packet-loss
 * percentage, and forward-error-correction settings, gated by the unified audio quality state.
 *
 * <p>Each {@link #apply(long, double, double, long, OpusCodecParams)} round takes the combiner's target
 * bitrate for the audio stream, deducts the {@linkplain #transportOverheadBps() transport overhead}
 * (zero in the reversed engine), then advances the {@link UnifiedAudioQualityControl} with the latest
 * loss, round-trip, and receiver-estimate measurements. The resulting quality state and its
 * forward-error-correction overhead decide how much of the budget funds redundancy: while
 * {@link UaqcState#PROBING} a fraction of the target is reserved for in-band forward error correction and
 * the codec bitrate is reduced accordingly; in the steady state the full budget goes to the codec. The
 * expected-loss percentage the codec uses to size its in-band redundancy tracks the measured loss. The
 * method returns an updated {@link OpusCodecParams} carrying the new bitrate, loss percentage, and
 * forward-error-correction flag, clamped to the codec's configured bitrate floor and ceiling.
 *
 * <p>The controller holds the quality control across rounds and is otherwise stateless. It holds no
 * clock; the caller supplies a current-time reading each round. Instances are not thread-safe; the single
 * rate-control thread that owns one drives all rounds.
 *
 * @implNote This implementation ports {@code update_audio_encode_bitrate} and {@code get_audio_rc_data}
 * ({@code rate_control/wa_rate_control.cc} of the wa-voip WASM module {@code ff-tScznZ8P},
 * {@code rev-net-bwe}) together with the unified-audio-quality-control forward-error-correction step
 * ({@code uaqc_probing_update_fec} fn3867). The transport-overhead deduction reproduces the
 * {@code "Deducting transport overhead %u from audio bitrate %u"} path of {@code fn4407}, which subtracts
 * a precomputed transformation overhead held at the rate-control-data struct field {@code +0x78} only
 * when that field is strictly positive; the field is written to the literal {@code 0} by the codec fill
 * {@code fn4363} and never set non-zero, so the deduction is inert in this build and
 * {@link #transportOverheadBps()} returns {@code 0} (see its {@code @implNote}). The
 * forward-error-correction budget split and the loss-driven packet-loss percentage are the
 * {@code vid_rc_dyn}/{@code uaqc} FEC-ratio structure; the codec bitrate floor and ceiling come from the
 * supplied {@link OpusCodecParams}.
 */
public final class AudioRateController {
    /**
     * The default expected-loss percentage applied when no loss is measured.
     *
     * <p>A small floor so the codec keeps a minimal in-band redundancy even on a clean link.
     */
    private static final int MIN_PACKET_LOSS_PERCENT = 0;

    /**
     * The unified audio quality control whose state gates the forward-error-correction budget.
     */
    private final UnifiedAudioQualityControl qualityControl;

    /**
     * Constructs an audio rate controller over the given quality control.
     *
     * @param qualityControl the unified audio quality control; never {@code null}
     */
    public AudioRateController(UnifiedAudioQualityControl qualityControl) {
        this.qualityControl = qualityControl;
    }

    /**
     * Constructs an audio rate controller with a default-configured quality control.
     */
    public AudioRateController() {
        this(new UnifiedAudioQualityControl(UnifiedAudioQualityControl.Config.defaults()));
    }

    /**
     * Computes the audio encoder settings for the combined target and the latest network measurements.
     *
     * <p>Deducts the transport overhead from the target, advances the quality control, splits off the
     * forward-error-correction budget while probing, sets the expected-loss percentage from the measured
     * loss, and returns the supplied parameters with the new bitrate, loss percentage, and
     * forward-error-correction flag applied and clamped to the codec's bitrate window.
     *
     * @param combinedTargetBps the combiner's target bitrate for the audio stream, in bits per second
     * @param plr               the measured packet-loss ratio over the recent window, in {@code [0, 1]}
     * @param rttMs             the latest round-trip-time sample in milliseconds
     * @param rembBps           the latest receiver-estimated-maximum bitrate, in bits per second
     * @param params            the current codec parameters to re-target; never {@code null}
     * @return the audio rate result carrying the updated parameters and the chosen quality state
     */
    public AudioRateResult apply(long combinedTargetBps, double plr, double rttMs, long rembBps,
                                 OpusCodecParams params) {
        var nowMs = System.nanoTime() / 1_000_000L;
        var state = qualityControl.update(plr, rttMs, rembBps, nowMs);

        var overheadBps = transportOverheadBps();
        var budgetBps = Math.max(params.minBitrate(), combinedTargetBps - overheadBps);

        var fecFraction = qualityControl.fecOverheadFraction();
        var codecBps = (long) (budgetBps * (1.0 - fecFraction));
        codecBps = Math.clamp(codecBps, params.minBitrate(), params.maxBitrate());

        // TODO: route AudioRateController.apply through packer.encoderPacketLossPercent(measuredLossPercent) as the intended in-band-FEC policy object (logic currently inlined)
        var lossPercent = Math.clamp((int) Math.round(plr * 100.0), MIN_PACKET_LOSS_PERCENT, 100);
        var fecEnabled = fecFraction > 0.0 || lossPercent > 0;

        var updated = new OpusCodecParams(params.sampleRate(), params.channels(), params.application(),
                (int) codecBps, params.minBitrate(), params.maxBitrate(), params.variableBitrate(),
                params.maxBandwidth(), params.complexity(), fecEnabled, lossPercent,
                params.discontinuousTransmission(), params.forceChannels(), params.signalVoice(),
                params.lsbDepth(), params.framesPerPacket(), params.frameMillis());
        return new AudioRateResult(updated, state, fecFraction);
    }

    /**
     * Returns the current quality state of the unified audio quality control.
     *
     * @return the current {@link UaqcState}
     */
    public UaqcState state() {
        return qualityControl.state();
    }

    /**
     * Returns the transport-overhead bitrate the audio target is reduced by before it reaches the codec,
     * in bits per second.
     *
     * <p>Returns {@code 0}: in the reversed engine the deduction is gated off, so no overhead is removed
     * from the audio target.
     *
     * @implNote This implementation matches the compiled behaviour of {@code fn4407}
     * ({@code network/src/rate_control/wa_rate_control.cc} of the wa-voip WASM module
     * {@code ff-tScznZ8P}, the {@code "Deducting transport overhead %u from audio bitrate %u"} path). The
     * native code reads a precomputed max transformation overhead from the audio rate-control-data struct
     * field {@code +0x78} as a bits-per-second value and subtracts it directly from the target only when
     * that field is strictly positive ({@code 0 < (int) value}, then {@code *target -= value}). The field
     * is written exactly once, by the codec fill {@code fn4363} (in {@code codec.h}), to the literal
     * {@code 0}; no other writer sets it non-zero anywhere in the module, so the {@code 0 < value} gate is
     * always false and the deduction never fires in this build. The transformation-overhead getter the
     * field is named for ({@code wa_audio_get_max_transformation_overhead}) is inlined into the
     * encoder-sender {@code fn4295} and yields only a redundancy-block count there, never populating
     * {@code +0x78}. No {@code transport_overhead} or
     * {@code transformation_overhead} key exists in the 759-key voip-settings union, so the compiled
     * zero governs. The faithful audio target is therefore the combiner target unmodified; the previous
     * RTP plus UDP plus IP header-sum estimate matched neither the mechanism (the native code never sums
     * network headers) nor the value (zero in this build) and is removed.
     *
     * @return {@code 0}, the bits-per-second overhead the reversed engine deducts
     */
    private long transportOverheadBps() {
        return 0;
    }

    /**
     * The outcome of one audio rate-control round: the re-targeted codec parameters, the quality state,
     * and the forward-error-correction overhead applied.
     *
     * <p>The {@link #params()} are ready to hand to a codec reconfigure; the {@link #state()} and
     * {@link #fecOverheadFraction()} are surfaced for telemetry and for the redundancy packer.
     *
     * @param params              the updated codec parameters with the new bitrate, loss percentage, and
     *                            forward-error-correction flag; never {@code null}
     * @param state               the unified audio quality state after this round
     * @param fecOverheadFraction the forward-error-correction overhead fraction reserved this round, in
     *                            {@code [0, 1]}
     */
    public record AudioRateResult(OpusCodecParams params, UaqcState state, double fecOverheadFraction) {
    }
}
