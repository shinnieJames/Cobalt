package com.github.auties00.cobalt.calls2.media.video;

import com.github.auties00.cobalt.calls2.common.VideoDecoderCapability;
import com.github.auties00.cobalt.calls2.stream.VideoFrame;
import com.github.auties00.cobalt.exception.WhatsAppCallException;

/**
 * The call video codec abstraction: encodes raw 4:2:0 pictures into compressed access units, decodes
 * compressed access units back to raw pictures, forces an intra refresh on demand, and reconfigures
 * itself mid-call.
 *
 * <p>An instance owns one encoder and one decoder for a single video stream and is single-writer: the
 * encode path, decode path, key-frame request, and reconfiguration must be driven from one thread,
 * since the codec holds mutable native state and reusable scratch buffers. The
 * {@linkplain #encode(VideoFrame, boolean) encode} method compresses one picture, optionally forcing
 * it to be a key frame; the {@linkplain #decode(byte[], long) decode} method reconstructs the next
 * picture from a compressed access unit; {@linkplain #requestKeyFrame() requestKeyFrame} arms the
 * encoder so its next output is a key frame, the recovery action a receiver takes after a loss it
 * could not conceal; {@linkplain #modify(VideoCodecParams) modify} re-applies the mutable rate-control
 * controls; and {@link #stats()} snapshots the lifetime counters.
 *
 * <p>The hierarchy is sealed for exhaustive matching: {@link H264VideoCodec} wraps OpenH264,
 * {@link VpxVideoCodec} wraps libvpx (VP8 and VP9), and {@link Av1VideoCodec} is the deferred AV1 codec
 * the engine can select once its native binding lands. The 1:1 and group video paths negotiate the
 * concrete codec through {@link VideoDecoderCapability}, defaulting to
 * {@link VideoDecoderCapability#H264 H264} when no higher-priority codec is common to both sides.
 *
 * @implNote This implementation mirrors the PJMEDIA video-codec abstraction the wa-voip WASM module
 * {@code ff-tScznZ8P} drives through {@code vid_codec.cc}, {@code vid_stream_encoder.cc}, and
 * {@code vid_stream_decoder.cc}: the {@code pjmedia_vid_codec} function-pointer table (open, encode,
 * decode, recover) collapses to direct native downcalls in the concrete implementations, and the
 * codec-type negotiation that selects which factory to instantiate becomes the sealed permits split
 * keyed by {@link VideoDecoderCapability}.
 */
public sealed interface VideoCodec extends AutoCloseable
        permits H264VideoCodec, VpxVideoCodec, Av1VideoCodec {
    /**
     * Returns the codec format this instance implements.
     *
     * @return the codec format, such as {@link VideoDecoderCapability#H264 H264} or
     *         {@link VideoDecoderCapability#VP8 VP8}
     */
    VideoDecoderCapability codec();

    /**
     * Encodes one raw 4:2:0 picture into a compressed access unit, optionally forcing it to be a key
     * frame.
     *
     * @implSpec Implementations must accept a planar
     * {@link com.github.auties00.cobalt.calls2.stream.VideoPixelFormat#I420 I420} {@link VideoFrame} whose
     * {@linkplain VideoFrame#width() width} and {@linkplain VideoFrame#height() height} match the
     * configured encoder geometry; the caller normalizes any capture to that layout and geometry (repacking
     * an {@link com.github.auties00.cobalt.calls2.stream.VideoPixelFormat#NV12 NV12} source and resampling
     * an off-geometry one) before the encode hand-off, so implementations feed the frame's pixels straight
     * to the native encoder. When {@code forceKeyFrame} is {@code true}, or when an earlier
     * {@linkplain #requestKeyFrame() key-frame request} is pending, the output must be an intra picture
     * and the {@linkplain EncodedVideoFrame#keyFrame() key-frame flag} must be set. When the rate
     * controller drops the frame, the result must be an {@linkplain EncodedVideoFrame#isEmpty() empty}
     * access unit rather than {@code null}. The returned frame must carry the source
     * {@linkplain VideoFrame#ptsMicros() presentation timestamp} and the configured dimensions.
     *
     * @param frame         the raw picture to encode
     * @param forceKeyFrame whether to force this picture to be an intra (key) frame
     * @return the encoded access unit with its key-frame classification
     * @throws NullPointerException                if {@code frame} is {@code null}
     * @throws IllegalArgumentException            if the frame dimensions do not match the configured
     *                                             geometry
     * @throws IllegalStateException               if the codec is closed
     * @throws WhatsAppCallException.H264          if an OpenH264 encode call fails
     * @throws WhatsAppCallException.Vpx           if a libvpx encode call fails
     */
    EncodedVideoFrame encode(VideoFrame frame, boolean forceKeyFrame);

    /**
     * Decodes one compressed access unit into a raw 4:2:0 picture.
     *
     * @implSpec Implementations must reconstruct the next picture from the supplied access unit and
     * return it as an {@link com.github.auties00.cobalt.calls2.stream.VideoPixelFormat#I420 I420}
     * {@link VideoFrame} stamped with the supplied presentation timestamp. A codec that buffers
     * references internally may consume an access unit that yields no displayable picture yet (for
     * example a decoder waiting for the first key frame); in that case implementations must return
     * {@code null} rather than an empty frame. When the access unit is malformed or the decoder rejects
     * it, implementations throw the codec-specific exception.
     *
     * @param payload   the compressed access-unit bytes
     * @param ptsMicros the presentation timestamp in microseconds to stamp the decoded frame with
     * @return the decoded picture, or {@code null} when the access unit produced no displayable frame
     * @throws NullPointerException       if {@code payload} is {@code null}
     * @throws IllegalStateException      if the codec is closed
     * @throws WhatsAppCallException.H264 if an OpenH264 decode call fails
     * @throws WhatsAppCallException.Vpx  if a libvpx decode call fails
     */
    VideoFrame decode(byte[] payload, long ptsMicros);

    /**
     * Decodes one compressed access unit into a raw 4:2:0 picture, packing the result into {@code reuse}
     * when that buffer exactly fits the decoded geometry.
     *
     * <p>This is the pooled decode path the media plane drives so a steady-resolution stream produces no
     * per-frame pixel allocation: the caller passes a buffer it owns from a small ring, and the codec
     * writes the decoded planes into it instead of minting a fresh array. The decoded picture size is
     * known only after the access unit is parsed, so {@code reuse} is a hint rather than a guarantee: the
     * codec adopts it only when {@code reuse.length} equals the decoded picture's packed 4:2:0 byte
     * count, and otherwise allocates a correctly sized buffer, so the caller inspects the returned
     * frame's {@linkplain VideoFrame#pixels() buffer} to learn which array was used and to re-seed its
     * ring slot after a geometry change.
     *
     * @implSpec The default implementation ignores {@code reuse} and delegates to
     * {@link #decode(byte[], long)}, always returning a freshly allocated buffer. An implementation that
     * packs its output into a caller buffer overrides this to write into {@code reuse} when
     * {@code reuse != null} and {@code reuse.length} equals the decoded picture's packed byte count,
     * returning a frame that wraps {@code reuse}; when {@code reuse} does not fit, when it is
     * {@code null}, or when the access unit yields no displayable picture, the implementation must not
     * write into {@code reuse} and must behave exactly as {@link #decode(byte[], long)}. The returned
     * frame and the {@code null} end-of-stream contract are identical to {@link #decode(byte[], long)}.
     *
     * @param payload   the compressed access-unit bytes
     * @param ptsMicros the presentation timestamp in microseconds to stamp the decoded frame with
     * @param reuse     a caller-owned buffer to pack the decoded picture into when it fits, or
     *                  {@code null} to always allocate
     * @return the decoded picture, wrapping {@code reuse} when it was adopted, or {@code null} when the
     *         access unit produced no displayable frame
     * @throws NullPointerException       if {@code payload} is {@code null}
     * @throws IllegalStateException      if the codec is closed
     * @throws WhatsAppCallException.H264 if an OpenH264 decode call fails
     * @throws WhatsAppCallException.Vpx  if a libvpx decode call fails
     */
    default VideoFrame decode(byte[] payload, long ptsMicros, byte[] reuse) {
        return decode(payload, ptsMicros);
    }

    /**
     * Arms the encoder so that its next {@linkplain #encode(VideoFrame, boolean) encode} produces a key
     * frame.
     *
     * @implSpec Implementations must make the very next encoded picture an intra picture, equivalent to
     * passing {@code forceKeyFrame = true} on that call, and must count the request in
     * {@link VideoCodecStats#keyFrameRequests()}. The request is one-shot: it is consumed by the next
     * encode and does not persist. Calling this method has no effect on the decode path.
     *
     * @throws IllegalStateException if the codec is closed
     */
    void requestKeyFrame();

    /**
     * Reconfigures the encoder mid-call from the mutable subset of the given parameters.
     *
     * @implSpec Implementations must re-apply only the controls a live encoder accepts without a
     * reopen: the bitrate triplet, the frame rate, the quantizer window, the frame-skip toggle, and the
     * IDR bitrate ratio. The codec ({@link VideoCodecParams#codec()}), the picture geometry
     * ({@link VideoCodecParams#width()}, {@link VideoCodecParams#height()}), the temporal-layer count,
     * and the long-term-reference toggle must not change; such a change requires tearing the codec down
     * and reopening it.
     *
     * @param params the parameter set whose mutable fields the encoder adopts
     * @throws NullPointerException       if {@code params} is {@code null}
     * @throws IllegalArgumentException   if {@code params} selects a different codec or geometry than
     *                                    the one this instance was opened with
     * @throws IllegalStateException      if the codec is closed
     * @throws WhatsAppCallException.H264 if an OpenH264 control call fails
     * @throws WhatsAppCallException.Vpx  if a libvpx control call fails
     */
    void modify(VideoCodecParams params);

    /**
     * Returns a snapshot of this codec's lifetime counters.
     *
     * @return the current stats snapshot
     */
    VideoCodecStats stats();

    /**
     * Releases the native codec state and any owned resources.
     *
     * @implSpec Implementations must be idempotent. After closing, every other method except a repeated
     * {@link #close()} throws {@link IllegalStateException}.
     */
    @Override
    void close();
}
