package com.github.auties00.cobalt.calls2.net.ratecontrol;

import com.github.auties00.cobalt.calls2.media.video.VideoCodecParams;

/**
 * Turns the combined bandwidth-estimate target into the video encoder's target bitrate and
 * forward-error-correction ratios, clamping the rate when the SCTP send buffer is congested.
 *
 * <p>Each {@link #apply(long, double, long, long, VideoCodecParams)} round takes the combiner's target
 * bitrate for the video stream, clamps it against the {@link SctpBufferCongestionController} so a filling
 * data-channel buffer backs the encoder off, and then funds forward error correction from the remaining
 * budget in proportion to the measured loss. The forward-error-correction ratio is split between key
 * frames and delta frames, with key frames protected more heavily because a lost key frame freezes the
 * stream until the next one. The method returns an updated {@link VideoCodecParams} carrying the new
 * target bitrate clamped to the codec's configured floor and ceiling, alongside the key-frame and
 * delta-frame protection ratios the packetizer applies.
 *
 * <p>The controller holds the SCTP buffer congestion controller across rounds and is otherwise
 * stateless. It holds no clock; the caller supplies a current-time reading each round so the buffer
 * congestion feedback recency is computed correctly. Instances are not thread-safe; the single
 * rate-control thread that owns one drives all rounds.
 *
 * @implNote This implementation ports {@code wa_vid_rate_control.cc} (video target bitrate) together
 * with the SCTP buffer congestion clamp {@code wa_sctp_buffer_congestion_controller_update} (fn4285) of
 * the wa-voip WASM module {@code ff-tScznZ8P} ({@code rev-net-bwe}). The target is the combiner output
 * clamped first to the codec bitrate window and then by the SCTP buffer controller; the key-frame and
 * delta-frame forward-error-correction ratios reproduce the {@code vid_rc_dyn} FEC-ratio split, scaled by
 * the measured loss. The maximum protection ratios {@link #MAX_KEY_FEC_RATIO} and
 * {@link #MAX_DELTA_FEC_RATIO} are the server voip-params {@code max_key_fec_ratio} and
 * {@code max_fec_ratio}: the live server pushes {@code vid_rc.max_fec_ratio=0} (delta ceiling, so
 * delta-frame FEC is disabled) and {@code vid_rc.enable_fec_for_key_frames=1} while omitting
 * {@code max_key_fec_ratio}, so the key-frame ceiling keeps its compiled-in half-rate default (decoded from
 * {@code <voip_settings uncompressed=1>} in stanzas-primary.jsonl, union in voip-settings-merged.json). The
 * codec bitrate floor and ceiling come from the supplied {@link VideoCodecParams}.
 */
public final class VideoRateController {
    /**
     * The maximum forward-error-correction ratio applied to key frames, in {@code [0, 1]}.
     *
     * <p>A key frame may be protected with up to this fraction of redundant data because losing one
     * freezes the stream until the next key frame.
     *
     * @implNote The key-frame FEC ceiling is the server voip-param {@code max_key_fec_ratio} (string at
     * strings.json addr 0x531ec), read by the {@code vid_rc_dyn} FEC computation in
     * {@code bwe/wa_fast_ramp_controller.cc} (fn4287 {@code update_vid_rate_control_params}). The key
     * {@code max_key_fec_ratio} is absent from the 759-key live voip_settings union (its {@code vid_rc_dyn}
     * conditional entries carry only {@code max_fec_ratio}, never a per-key bound), so the server does not
     * push it and the compiled-in upstream default of half-rate is the operative ceiling; key-frame FEC
     * itself is enabled by the live {@code vid_rc.enable_fec_for_key_frames=1}.
     */
    private static final double MAX_KEY_FEC_RATIO = 0.5;

    /**
     * The maximum forward-error-correction ratio applied to delta frames, in {@code [0, 1]}.
     *
     * <p>Delta frames are protected more lightly than key frames since a lost delta frame degrades only
     * until the next frame. The live server pushes a zero ceiling, so loss-driven delta-frame protection
     * is disabled and only key frames carry redundancy.
     *
     * @implNote The delta-frame FEC ceiling is the server voip-param {@code max_fec_ratio} (string at
     * strings.json addr 0x53216), the general (non-key) ratio bound paired with {@code min_fec_ratio} in the
     * {@code vid_rc_dyn} computation ({@code bwe/wa_fast_ramp_controller.cc} fn4287). The compiled-in default
     * is 0 and the live server confirms it: voip-settings-merged.json {@code vid_rc.max_fec_ratio=0}
     * (decoded from {@code <voip_settings uncompressed=1>} in stanzas-primary.jsonl). The {@code max_fec_ratio=0.25}
     * entries elsewhere in the union are conditional {@code vid_rc_dyn} overrides gated on loss and round-trip
     * predicates, not this static flat ceiling.
     */
    private static final double MAX_DELTA_FEC_RATIO = 0.0;

    /**
     * The SCTP send-buffer congestion controller that clamps the video target when the buffer fills.
     */
    private final SctpBufferCongestionController sctpBufferController;

    /**
     * Constructs a video rate controller over the given SCTP buffer congestion controller.
     *
     * @param sctpBufferController the SCTP buffer congestion controller; never {@code null}
     */
    public VideoRateController(SctpBufferCongestionController sctpBufferController) {
        this.sctpBufferController = sctpBufferController;
    }

    /**
     * Constructs a video rate controller with a default-configured SCTP buffer congestion controller.
     */
    public VideoRateController() {
        this(SctpBufferCongestionController.defaults());
    }

    /**
     * Computes the video encoder settings for the combined target and the latest network and buffer
     * measurements.
     *
     * <p>Clamps the target to the codec bitrate window, advances the SCTP buffer congestion controller
     * with the current occupancy and the last feedback time and applies its clamp, then funds the
     * key-frame and delta-frame forward-error-correction ratios from the measured loss. Returns the
     * supplied parameters with the new target bitrate applied and clamped, alongside the protection
     * ratios.
     *
     * @param combinedTargetBps   the combiner's target bitrate for the video stream, in bits per second
     * @param plr                 the measured packet-loss ratio over the recent window, in {@code [0, 1]}
     * @param sctpBufferOccupancy the current SCTP send-buffer occupancy in bytes
     * @param lastFeedbackMs      the time of the most recent feedback in milliseconds, from a monotonic
     *                            source
     * @param params              the current codec parameters to re-target; never {@code null}
     * @return the video rate result carrying the updated parameters and the protection ratios
     */
    public VideoRateResult apply(long combinedTargetBps, double plr, long sctpBufferOccupancy,
                                 long lastFeedbackMs, VideoCodecParams params) {
        var nowMs = System.nanoTime() / 1_000_000L;
        var windowClamped = Math.clamp(combinedTargetBps, params.minBitrate(), params.maxBitrate());

        sctpBufferController.update(sctpBufferOccupancy, lastFeedbackMs, nowMs);
        var bufferClamped = sctpBufferController.clampRate(windowClamped);
        var targetBps = Math.clamp(bufferClamped, params.minBitrate(), params.maxBitrate());

        var keyFecRatio = Math.clamp(plr * 2.0, 0.0, MAX_KEY_FEC_RATIO);
        var deltaFecRatio = Math.clamp(plr, 0.0, MAX_DELTA_FEC_RATIO);

        var newTarget = (int) targetBps;
        var updated = newTarget == params.targetBitrate() ? params : params.withTargetBitrate(newTarget);
        return new VideoRateResult(updated, keyFecRatio, deltaFecRatio, sctpBufferController.isCongested());
    }

    /**
     * Returns whether the SCTP send buffer is currently congested.
     *
     * @return {@code true} when the buffer controller reports congestion
     */
    public boolean sctpBufferCongested() {
        return sctpBufferController.isCongested();
    }

    /**
     * The outcome of one video rate-control round: the re-targeted codec parameters, the
     * forward-error-correction ratios, and whether the SCTP buffer is congested.
     *
     * <p>The {@link #params()} are ready to hand to a codec reconfigure; the {@link #keyFecRatio()} and
     * {@link #deltaFecRatio()} drive the packetizer's redundancy, and {@link #sctpBufferCongested()} is
     * surfaced for telemetry.
     *
     * @param params              the updated codec parameters with the new target bitrate; never
     *                            {@code null}
     * @param keyFecRatio         the forward-error-correction ratio for key frames, in {@code [0, 1]}
     * @param deltaFecRatio       the forward-error-correction ratio for delta frames, in {@code [0, 1]}
     * @param sctpBufferCongested whether the SCTP send buffer was congested this round
     */
    public record VideoRateResult(VideoCodecParams params, double keyFecRatio, double deltaFecRatio,
                                  boolean sctpBufferCongested) {
    }
}
