package com.github.auties00.cobalt.calls2.media.video;

import com.github.auties00.cobalt.calls2.common.VideoDecoderCapability;
import com.github.auties00.cobalt.calls2.stream.VideoFrame;

import java.util.Objects;

/**
 * An immutable set of video encoder and decoder parameters the call media path opens a
 * {@link VideoCodec} with and reconfigures it through mid-call.
 *
 * <p>The parameters cover the picture geometry ({@link #codec()}, {@link #width()}, {@link #height()},
 * {@link #frameRate()}), the bitrate triplet ({@link #targetBitrate()}, {@link #minBitrate()},
 * {@link #maxBitrate()}), the quantizer window ({@link #minQuantizer()}, {@link #maxQuantizer()}), the
 * keyframe cadence ({@link #keyFrameIntervalSeconds()}), the latency-versus-quality knob
 * ({@link #complexity()}), the rate-control safety valves ({@link #frameSkip()},
 * {@link #idrBitrateRatio()}), and the scalability and recovery features the engine drives codecs with
 * ({@link #temporalLayers()}, {@link #longTermReference()}). On open the codec applies every control
 * these fields select; on a {@linkplain VideoCodec#modify(VideoCodecParams) modify} round only the
 * mutable subset (the bitrate triplet, the frame rate, the quantizer window, frame skip, and the IDR
 * bitrate ratio) is re-applied, since the {@link #codec()}, the {@link #width()}/{@link #height()}
 * geometry, the layer count, and the long-term-reference toggle all require a codec reopen.
 *
 * <p>Build instances through {@link #forResolution(VideoDecoderCapability, int, int, int)}, which seeds
 * the bitrate triplet from the recovered WhatsApp initial rate-control constants and the remaining knobs
 * from the WhatsApp video defaults, then derive variants with the {@code with*} copy methods. The
 * {@link #width()} and {@link #height()} must be even so the 4:2:0 chroma planes have integral sizes,
 * matching the {@link VideoFrame} the encoder consumes.
 *
 * @param codec                  the codec this parameter set configures
 * @param width                  the encoded picture width in pixels; even and at least {@code 2}
 * @param height                 the encoded picture height in pixels; even and at least {@code 2}
 * @param frameRate              the target frame rate in frames per second; positive
 * @param targetBitrate          the target bitrate in bits per second
 * @param minBitrate             the floor bitrate in bits per second the rate controller will not go
 *                               below
 * @param maxBitrate             the ceiling bitrate in bits per second the rate controller will not
 *                               exceed
 * @param minQuantizer           the lowest quantization parameter the rate controller may pick
 * @param maxQuantizer           the highest quantization parameter the rate controller may pick
 * @param keyFrameIntervalSeconds the maximum number of seconds between forced key frames; {@code 0}
 *                               disables periodic key frames and emits one only on request
 * @param complexity             the latency-versus-quality level; lower trades quality for less encode
 *                               time
 * @param frameSkip              whether the rate controller may drop frames to hold the bitrate
 * @param idrBitrateRatio        the percentage by which a key frame's byte budget exceeds an inter
 *                               frame's
 * @param temporalLayers         the number of temporal scalability layers; {@code 1} is single-layer
 * @param longTermReference      whether long-term reference frames are enabled for loss recovery
 * @implNote This implementation collects the controls the OpenH264 and libvpx open and reconfigure
 * paths of the wa-voip WASM module {@code ff-tScznZ8P} apply. For OpenH264 the fields drive the
 * {@code SEncParamExt} block ({@code iTargetBitrate}, {@code iMaxBitrate}, {@code fMaxFrameRate},
 * {@code iMinQp}/{@code iMaxQp}, {@code uiIntraPeriod}, {@code iComplexityMode},
 * {@code bEnableFrameSkip}, {@code iIdrBitrateRatio}, {@code iTemporalLayerNum},
 * {@code bEnableLongTermReference}); for VP8 they drive the {@code vpx_codec_enc_cfg}
 * ({@code rc_target_bitrate} in kbps, {@code rc_min_quantizer}/{@code rc_max_quantizer},
 * {@code kf_max_dist}, {@code g_timebase}) plus the {@code VP8E_SET_CPUUSED} control for
 * {@link #complexity()}. The {@code voip_settings} {@code vid_rc} rate-control block the live captures
 * recovered (re/calls2-spec/captures/voip-settings-merged.json) is a condition-driven rule engine
 * ({@code vid_rc_dyn} rules keyed on bitrate range, network medium, round-trip time, and packet loss),
 * not a flat default set: its unconditioned base rule pins {@code openh264_max_qp = 51} and
 * {@code openh264_idr_bitrate_ratio = 100} (adopted here as {@link #DEFAULT_MAX_QUANTIZER} and
 * {@link #DEFAULT_IDR_BITRATE_RATIO}), and the base {@code vid_rc} block carries
 * {@code openh264_num_temporal_layers = 2}. The minimum quantizer is absent from the 759-key union
 * (the upstream encoder default applies), and the per-condition {@code max_fps}, {@code key_frame_interval},
 * {@code max_target_bitrate}, and {@code max_encode_width} overrides are runtime rate-control state this
 * static record does not model; threading the full {@code vid_rc} rule engine and the {@code openh264_*}
 * AB-prop overrides into the encoder configuration is a rate-control concern outside this record.
 */
public record VideoCodecParams(
        VideoDecoderCapability codec,
        int width,
        int height,
        int frameRate,
        int targetBitrate,
        int minBitrate,
        int maxBitrate,
        int minQuantizer,
        int maxQuantizer,
        int keyFrameIntervalSeconds,
        int complexity,
        boolean frameSkip,
        int idrBitrateRatio,
        int temporalLayers,
        boolean longTermReference
) {
    /**
     * The default target frame rate in frames per second when none is specified.
     *
     * <p>The recovered {@code vid_rc} rate-control config caps the frame rate per runtime condition
     * ({@code max_fps} ranges across {@code 1..15} in the {@code vid_rc_dyn} rules, with a base
     * {@code max_fps} of {@code 20}; re/calls2-spec/captures/voip-settings-merged.json) rather than fixing
     * one encode rate. This {@code 30}-fps SPEC default is the unconstrained capture target the rule
     * engine then caps; the per-condition cap is a rate-control concern, not a static codec default.
     */
    public static final int DEFAULT_FRAME_RATE = 30;

    /**
     * The default lowest quantization parameter, the OpenH264 and libvpx low bound.
     *
     * <p>No {@code openh264_qp_min} (or any {@code qp_min}/{@code min_quantizer}) key appears in the
     * 759-key {@code voip_settings} union the live captures recovered, so the server pushes no minimum
     * quantizer and the operative value is the upstream encoder default; {@code 10} is the SPEC default
     * carried until the upstream {@code iMinQp} default is confirmed.
     */
    // TODO: confirm the OpenH264/libvpx upstream default iMinQp/rc_min_quantizer (no qp_min key is
    //  present in the recovered 759-key voip_settings union, so the compiled-in encoder default applies)
    public static final int DEFAULT_MIN_QUANTIZER = 10;

    /**
     * The default highest quantization parameter.
     *
     * <p>Matches the recovered base {@code vid_rc} rate-control rule {@code openh264_max_qp = 51} (and the
     * AV1 {@code av1_max_qp = 63} ceiling for that codec); see {@code re/calls2-spec/captures}
     * {@code voip-settings-merged.json} ({@code vid_rc_dyn[0].openh264_max_qp = 51}, the unconditioned
     * base rule).
     */
    public static final int DEFAULT_MAX_QUANTIZER = 51;

    /**
     * The default maximum seconds between forced key frames.
     *
     * <p>The recovered base {@code vid_rc.key_frame_interval} is {@code 60}
     * (re/calls2-spec/captures/voip-settings-merged.json), but that field's unit on the wire is not
     * confirmed (the codec-side {@code uiIntraPeriod}/{@code kf_max_dist} this maps to is in frames, and
     * the per-condition {@code vid_rc_dyn} overrides carry both small positive values like {@code 4} and
     * a negative {@code -6} sentinel that does not parse as a plain second count), so this constant keeps
     * the {@code 10}-second SPEC default rather than adopting {@code 60} under an unconfirmed unit.
     */
    // TODO: confirm the wire unit and sentinel semantics of vid_rc.key_frame_interval (recovered base
    //  value 60, with vid_rc_dyn overrides of 4 and -6) before seeding it as the keyFrameIntervalSeconds
    //  default; the field maps to the frame-count uiIntraPeriod/kf_max_dist, so the unit is load-bearing
    public static final int DEFAULT_KEY_FRAME_INTERVAL_SECONDS = 10;

    /**
     * The default percentage by which a key frame's byte budget exceeds an inter frame's.
     *
     * <p>Matches the recovered base {@code vid_rc} rate-control rule
     * {@code openh264_idr_bitrate_ratio = 100} (re/calls2-spec/captures/voip-settings-merged.json,
     * {@code vid_rc_dyn[0]}, the unconditioned base rule); {@code 100} means a key frame is budgeted the
     * same as an inter frame. The per-condition {@code vid_rc_dyn} rule engine raises this to {@code 200}
     * or {@code 300} under specific bitrate and packet-loss conditions, which the static rate-control
     * defaults here do not model.
     */
    public static final int DEFAULT_IDR_BITRATE_RATIO = 100;

    /**
     * The initial target bitrate the resolution seed opens the encoder at, in bits per second.
     *
     * <p>WhatsApp does not derive the initial video bitrate from the picture size: the bandwidth
     * estimator initializes the target independently of the resolution and then drives it, while the
     * recovered {@code vid_rc_dyn} rule engine maps a runtime target bitrate onto a resolution cap
     * ({@code cond_range_target_bitrate} to {@code max_encode_width}, the inverse direction), so no static
     * resolution-to-bitrate table exists. This is the recovered initial bandwidth-estimate ceiling
     * ({@code vid_rc.max_init_bwe = 350000}; re/calls2-spec/captures/voip-settings-merged.json), the value
     * the estimator starts the encoder at before it ramps toward {@link #DEFAULT_MAX_BITRATE}.
     */
    public static final int DEFAULT_INIT_TARGET_BITRATE = 350_000;

    /**
     * The floor bitrate the resolution seed configures the rate controller with, in bits per second.
     *
     * <p>The recovered minimum initial bitrate ({@code vid_rc.fr_min_init_bitrate_bps = 128000};
     * re/calls2-spec/captures/voip-settings-merged.json), below which the estimator does not initialize
     * the encoder target.
     */
    public static final int DEFAULT_MIN_BITRATE = 128_000;

    /**
     * The ceiling bitrate the resolution seed configures the rate controller with, in bits per second.
     *
     * <p>The recovered base rate-control ceiling ({@code vid_rc.max_target_bitrate = 1020000};
     * re/calls2-spec/captures/voip-settings-merged.json), the global cap the estimator ramps the target
     * toward; the {@code vid_rc_dyn} rules tighten it further at runtime (for instance a 600000 cap in the
     * first sixty seconds of a non-screen-share call, and a 2000000 cap for screen sharing).
     */
    public static final int DEFAULT_MAX_BITRATE = 1_020_000;

    /**
     * Validates the geometry, the bitrate ordering, and the layer count.
     *
     * @throws NullPointerException     if {@code codec} is {@code null}
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below {@code 2}, if
     *                                  {@code frameRate} is not positive, if the bitrate triplet is not
     *                                  ordered {@code minBitrate <= targetBitrate <= maxBitrate} with a
     *                                  positive target, if the quantizer window is not ordered
     *                                  {@code 0 <= minQuantizer <= maxQuantizer <= 63}, if
     *                                  {@code keyFrameIntervalSeconds} is negative, or if
     *                                  {@code temporalLayers} is below {@code 1}
     */
    public VideoCodecParams {
        Objects.requireNonNull(codec, "codec cannot be null");
        if (width < 2 || width % 2 != 0) {
            throw new IllegalArgumentException("width must be even and >= 2, got " + width);
        }
        if (height < 2 || height % 2 != 0) {
            throw new IllegalArgumentException("height must be even and >= 2, got " + height);
        }
        if (frameRate <= 0) {
            throw new IllegalArgumentException("frameRate must be positive, got " + frameRate);
        }
        if (targetBitrate <= 0) {
            throw new IllegalArgumentException("targetBitrate must be positive, got " + targetBitrate);
        }
        if (minBitrate < 0 || minBitrate > targetBitrate || targetBitrate > maxBitrate) {
            throw new IllegalArgumentException(
                    "bitrate triplet must satisfy 0 <= min <= target <= max, got min=" + minBitrate
                            + " target=" + targetBitrate + " max=" + maxBitrate);
        }
        if (minQuantizer < 0 || minQuantizer > maxQuantizer || maxQuantizer > 63) {
            throw new IllegalArgumentException(
                    "quantizer window must satisfy 0 <= min <= max <= 63, got min=" + minQuantizer
                            + " max=" + maxQuantizer);
        }
        if (keyFrameIntervalSeconds < 0) {
            throw new IllegalArgumentException(
                    "keyFrameIntervalSeconds must be non-negative, got " + keyFrameIntervalSeconds);
        }
        if (temporalLayers < 1) {
            throw new IllegalArgumentException("temporalLayers must be >= 1, got " + temporalLayers);
        }
    }

    /**
     * Returns the maximum number of frames between forced key frames for the configured frame rate.
     *
     * <p>Computed as {@code keyFrameIntervalSeconds * frameRate}; this is the {@code kf_max_dist}
     * argument for libvpx and the basis for {@code uiIntraPeriod} for OpenH264. A
     * {@link #keyFrameIntervalSeconds()} of {@code 0} yields {@code 0}, which the codecs read as
     * "emit a key frame only on request".
     *
     * @return the maximum inter-keyframe distance in frames
     */
    public int keyFrameIntervalFrames() {
        return keyFrameIntervalSeconds * frameRate;
    }

    /**
     * Returns a copy with the given target bitrate, clamping it into the {@code [min, max]} window.
     *
     * @param bitrate the requested target bitrate in bits per second
     * @return a copy whose target bitrate is the clamped value
     */
    public VideoCodecParams withTargetBitrate(int bitrate) {
        var clamped = Math.clamp(bitrate, minBitrate, maxBitrate);
        return new VideoCodecParams(codec, width, height, frameRate, clamped, minBitrate, maxBitrate,
                minQuantizer, maxQuantizer, keyFrameIntervalSeconds, complexity, frameSkip,
                idrBitrateRatio, temporalLayers, longTermReference);
    }

    /**
     * Returns a copy with the given target frame rate.
     *
     * @param frameRate the requested frame rate in frames per second; must be positive
     * @return a copy carrying the new frame rate
     * @throws IllegalArgumentException if {@code frameRate} is not positive
     */
    public VideoCodecParams withFrameRate(int frameRate) {
        return new VideoCodecParams(codec, width, height, frameRate, targetBitrate, minBitrate, maxBitrate,
                minQuantizer, maxQuantizer, keyFrameIntervalSeconds, complexity, frameSkip,
                idrBitrateRatio, temporalLayers, longTermReference);
    }

    /**
     * Returns a copy with the given quantizer window.
     *
     * @param minQuantizer the lowest quantization parameter
     * @param maxQuantizer the highest quantization parameter
     * @return a copy carrying the new quantizer window
     * @throws IllegalArgumentException if the window is not ordered {@code 0 <= min <= max <= 63}
     */
    public VideoCodecParams withQuantizerWindow(int minQuantizer, int maxQuantizer) {
        return new VideoCodecParams(codec, width, height, frameRate, targetBitrate, minBitrate, maxBitrate,
                minQuantizer, maxQuantizer, keyFrameIntervalSeconds, complexity, frameSkip,
                idrBitrateRatio, temporalLayers, longTermReference);
    }

    /**
     * Builds a parameter set for the given codec and picture geometry, seeding the bitrate triplet from
     * the recovered WhatsApp initial rate-control constants and the remaining knobs from the WhatsApp
     * video defaults.
     *
     * <p>The bitrate triplet is seeded resolution-independently from the captured base {@code vid_rc}
     * block: the target is {@link #DEFAULT_INIT_TARGET_BITRATE}, the floor is {@link #DEFAULT_MIN_BITRATE},
     * and the ceiling is {@link #DEFAULT_MAX_BITRATE}. WhatsApp initializes the video bitrate from the
     * bandwidth estimator rather than the picture size and drives it at runtime, so the geometry sets only
     * the encoded {@link #width()}, {@link #height()}, and {@link #frameRate()} here, not the bitrate. The
     * quantizer window ({@link #DEFAULT_MIN_QUANTIZER}, {@link #DEFAULT_MAX_QUANTIZER}), keyframe interval
     * ({@link #DEFAULT_KEY_FRAME_INTERVAL_SECONDS}), and IDR bitrate ratio
     * ({@link #DEFAULT_IDR_BITRATE_RATIO}) take their defaults, the layer count is a single temporal
     * layer, frame skip is enabled, complexity is the codec-neutral mid level ({@code 0}), and long-term
     * references are disabled. The recovered base {@code vid_rc} config carries
     * {@code openh264_num_temporal_layers = 2}; the seed keeps a single layer until the rate-control
     * config that selects the layer count is threaded in.
     *
     * @param codec     the codec to configure
     * @param width     the encoded picture width in pixels; even and at least {@code 2}
     * @param height    the encoded picture height in pixels; even and at least {@code 2}
     * @param frameRate the target frame rate in frames per second; positive
     * @return the seeded parameter set
     * @throws NullPointerException     if {@code codec} is {@code null}
     * @throws IllegalArgumentException if the geometry or frame rate is invalid
     * @implNote This implementation seeds the static triplet the rate controller then drives; the faithful
     * per-frame bitrate is the runtime bandwidth estimator output bounded by the {@code vid_rc_dyn} rule
     * engine, which this static record does not model. Threading the BWE and the {@code vid_rc_dyn} caps
     * into a live video rate controller is a separate concern.
     */
    public static VideoCodecParams forResolution(VideoDecoderCapability codec, int width, int height, int frameRate) {
        Objects.requireNonNull(codec, "codec cannot be null");
        if (frameRate <= 0) {
            throw new IllegalArgumentException("frameRate must be positive, got " + frameRate);
        }
        return new VideoCodecParams(
                codec,
                width,
                height,
                frameRate,
                DEFAULT_INIT_TARGET_BITRATE,
                DEFAULT_MIN_BITRATE,
                DEFAULT_MAX_BITRATE,
                DEFAULT_MIN_QUANTIZER,
                DEFAULT_MAX_QUANTIZER,
                DEFAULT_KEY_FRAME_INTERVAL_SECONDS,
                0,
                true,
                DEFAULT_IDR_BITRATE_RATIO,
                // TODO: seed temporal layers from vid_rc.openh264_num_temporal_layers (recovered value 2)
                //  once the rate-control config that selects the layer count is wired into video setup
                1,
                false
        );
    }
}
