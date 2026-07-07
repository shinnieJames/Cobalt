package com.github.auties00.cobalt.calls2.media.video;

import com.github.auties00.cobalt.calls2.common.VideoDecoderCapability;
import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The call's video-codec manager: it reports which codecs this build can encode and decode, negotiates
 * a codec against a peer's advertised capability set, and instantiates a {@link VideoCodec} for a
 * chosen {@link VideoCodecParams}.
 *
 * <p>This is the calls2 analogue of the PJMEDIA video-codec-manager: a single front door the media
 * engine asks for a codec rather than constructing {@link H264VideoCodec}, {@link VpxVideoCodec}, or
 * {@link Av1VideoCodec} directly. The {@linkplain #supportedCodecs() supported set} is the subset of
 * {@link VideoDecoderCapability} this build has a working encoder and decoder for, which the signaling
 * layer advertises through the {@code vid_dec} descriptor and intersects with the peer's set during
 * negotiation. {@link #open(VideoCodecParams)} maps a codec onto its concrete implementation;
 * {@link #negotiateAndOpen(VideoCodecParams, Set, Set)} performs the intersect-and-take-max-priority
 * selection and opens the winner.
 *
 * <p>The registry is stateless and its methods are pure factories, so one instance is safe to share
 * across calls and threads; the {@link VideoCodec} instances it returns are the single-writer objects,
 * not the registry itself.
 *
 * @implNote This implementation ports the registration-and-lookup half of the PJMEDIA
 * {@code pjmedia_vid_codec_mgr} the wa-voip WASM module {@code ff-tScznZ8P} drives through
 * {@code vid_codec.cc}: the per-codec factory registration becomes the {@link #SUPPORTED} set and the
 * codec-type-to-factory dispatch becomes the {@link #open(VideoCodecParams)} switch. H.264, VP8, VP9,
 * and AV1 are the codecs this build advertises in {@code vid_dec} and negotiates through
 * {@code combine_vid_codec_capability_and_prio} (fn11246): H.264 and VP8 take the highest priorities,
 * VP9 routes through the shared libvpx {@link VpxVideoCodec}, and AV1 (lowest priority, only chosen when
 * it is the sole common codec) drives its dav1d decoder through {@link Av1VideoCodec}. H.265 is the one
 * recovered capability bit (mask {@code 0x08}) with no codec wired in, so it is excluded from
 * {@link #SUPPORTED} and never negotiated.
 */
public final class VideoCodecRegistry {
    /**
     * The codecs this build can both encode and decode, advertised to peers and intersected during
     * negotiation.
     *
     * <p>Holds {@link VideoDecoderCapability#H264 H264}, {@link VideoDecoderCapability#VP8 VP8},
     * {@link VideoDecoderCapability#VP9 VP9}, and {@link VideoDecoderCapability#AV1 AV1}. H.264 and VP8
     * route through {@link H264VideoCodec} and {@link VpxVideoCodec}; VP9 shares {@link VpxVideoCodec}
     * (libvpx exposes both VP8 and VP9 behind one context API and {@link VpxVideoCodec} resolves the
     * codec from {@link VideoCodecParams#codec()}); AV1 routes through {@link Av1VideoCodec}, whose dav1d
     * decoder is the decode capability {@code vid_dec} advertises. {@link VideoDecoderCapability#H265 H265}
     * is omitted because no H.265 codec is wired in.
     */
    private static final Set<VideoDecoderCapability> SUPPORTED =
            EnumSet.of(VideoDecoderCapability.H264, VideoDecoderCapability.VP8,
                    VideoDecoderCapability.VP9, VideoDecoderCapability.AV1);

    /**
     * The shared stateless registry instance.
     *
     * <p>Because the registry holds no per-call state and its methods are pure factories, one instance is
     * safe to share across every call and thread; callers use this rather than constructing a fresh
     * registry per call.
     */
    public static final VideoCodecRegistry INSTANCE = new VideoCodecRegistry();

    /**
     * Constructs a stateless registry.
     */
    public VideoCodecRegistry() {

    }

    /**
     * Returns the codecs this build can both encode and decode.
     *
     * <p>The returned set is a defensive copy, so a caller may freely mutate it; this is the local
     * capability set the signaling layer serializes into the {@code vid_dec} descriptor.
     *
     * @return a mutable copy of the supported codec set, never empty
     */
    public Set<VideoDecoderCapability> supportedCodecs() {
        return EnumSet.copyOf(SUPPORTED);
    }

    /**
     * Returns whether this build can both encode and decode the given codec.
     *
     * @param codec the codec to test
     * @return {@code true} if {@code codec} is supported
     * @throws NullPointerException if {@code codec} is {@code null}
     */
    public boolean isSupported(VideoDecoderCapability codec) {
        Objects.requireNonNull(codec, "codec cannot be null");
        return SUPPORTED.contains(codec);
    }

    /**
     * Opens a {@link VideoCodec} for the given parameters, selecting the concrete implementation from
     * {@link VideoCodecParams#codec()}.
     *
     * @apiNote Prefer {@link #negotiateAndOpen(VideoCodecParams, Set, Set)} on the call setup path so
     * the codec is chosen against the peer's capabilities; call this directly only when the codec is
     * already fixed.
     *
     * @param params the codec parameters, whose {@link VideoCodecParams#codec() codec} selects the
     *               implementation
     * @return the opened codec
     * @throws NullPointerException       if {@code params} is {@code null}
     * @throws IllegalArgumentException   if {@link VideoCodecParams#codec()} is not supported by this
     *                                    build
     * @throws WhatsAppCallException.H264 if an OpenH264 object fails to open
     * @throws WhatsAppCallException.Vpx  if a libvpx context fails to open
     * @throws WhatsAppCallException.Av1  if the dav1d decoder or rav1e encoder fails to open
     */
    public VideoCodec open(VideoCodecParams params) {
        Objects.requireNonNull(params, "params cannot be null");
        return switch (params.codec()) {
            case H264 -> new H264VideoCodec(params);
            case VP8, VP9 -> new VpxVideoCodec(params);
            case AV1 -> new Av1VideoCodec(params);
            case H265 -> throw new IllegalArgumentException(
                    "H265 is not supported by this build: no H.265 codec is wired in");
        };
    }

    /**
     * Negotiates a codec against a peer's advertised capabilities and opens it with the given base
     * parameters.
     *
     * <p>The negotiation intersects the local {@linkplain #supportedCodecs() supported set} with both
     * the supplied {@code self} and {@code peer} sets and selects the surviving codec of highest
     * {@link VideoDecoderCapability#priority() priority}; if a codec is chosen, the base parameters are
     * re-targeted to it via {@link VideoCodecParams#codec()} and opened. When no codec is common to all
     * three sets, no codec is opened.
     *
     * @param baseParams the geometry and rate-control parameters to open the chosen codec with; its own
     *                   {@link VideoCodecParams#codec()} is overridden by the negotiated codec
     * @param self       the local advertised capability set
     * @param peer       the peer's advertised capability set
     * @return the opened codec, or {@link Optional#empty()} if the three sets share no codec
     * @throws NullPointerException       if any argument is {@code null}
     * @throws WhatsAppCallException.H264 if the negotiated OpenH264 object fails to open
     * @throws WhatsAppCallException.Vpx  if the negotiated libvpx context fails to open
     * @throws WhatsAppCallException.Av1  if the negotiated dav1d decoder or rav1e encoder fails to open
     */
    public Optional<VideoCodec> negotiateAndOpen(VideoCodecParams baseParams,
                                                 Set<VideoDecoderCapability> self,
                                                 Set<VideoDecoderCapability> peer) {
        Objects.requireNonNull(baseParams, "baseParams cannot be null");
        Objects.requireNonNull(self, "self cannot be null");
        Objects.requireNonNull(peer, "peer cannot be null");
        var localCapable = VideoDecoderCapability.intersect(SUPPORTED, self);
        return VideoDecoderCapability.negotiate(localCapable, peer)
                .map(chosen -> open(retarget(baseParams, chosen)));
    }

    /**
     * Returns a copy of the base parameters re-targeted to the negotiated codec, or the base parameters
     * unchanged when they already select that codec.
     *
     * @param baseParams the base parameters
     * @param codec      the negotiated codec
     * @return the parameters bound to {@code codec}
     */
    private VideoCodecParams retarget(VideoCodecParams baseParams, VideoDecoderCapability codec) {
        if (baseParams.codec() == codec) {
            return baseParams;
        }
        return new VideoCodecParams(
                codec,
                baseParams.width(),
                baseParams.height(),
                baseParams.frameRate(),
                baseParams.targetBitrate(),
                baseParams.minBitrate(),
                baseParams.maxBitrate(),
                baseParams.minQuantizer(),
                baseParams.maxQuantizer(),
                baseParams.keyFrameIntervalSeconds(),
                baseParams.complexity(),
                baseParams.frameSkip(),
                baseParams.idrBitrateRatio(),
                baseParams.temporalLayers(),
                baseParams.longTermReference());
    }
}
