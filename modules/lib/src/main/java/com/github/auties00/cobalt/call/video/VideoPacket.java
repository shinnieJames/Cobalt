package com.github.auties00.cobalt.call.video;

import java.util.Objects;

/**
 * Holds one encoded video frame at the boundary between the {@link VideoPipeline}, which owns the
 * codec, and the RTP transport, which packetises the bytes into RTP, applies SRTP, and ships them.
 *
 * <p>The payload layout depends on the codec that produced it. For VP8 ({@link VideoCodec.Vp8}) the
 * payload is a single VP8 bitstream frame ready for an RFC 7741 packetiser. For H.264
 * ({@link VideoCodec.H264}) the payload is the concatenated NAL units for the picture in bitstream
 * order, ready for an RFC 6184 packetiser. The dimensions and {@code keyFrame} flag travel with each
 * packet so the transport can react to a mid-call resolution change or a new GOP without consulting
 * the codec.
 *
 * @param payload  the encoded frame bytes; never {@code null}
 * @param ptsMs    the presentation timestamp in milliseconds, copied through from the frame that
 *                 drove the encoder
 * @param width    the frame width in pixels, carried per packet because the encoder may be
 *                 re-initialised on a resolution change mid-call
 * @param height   the frame height in pixels, carried per packet for the same reason as
 *                 {@code width}
 * @param keyFrame {@code true} when this is a keyframe (VP8 keyframe or H.264 IDR) that starts a new
 *                 GOP, so the transport can mark the RTP packet's keyframe bit and the peer's jitter
 *                 buffer can discard everything prior on a recovery request
 */
public record VideoPacket(byte[] payload, long ptsMs, int width, int height, boolean keyFrame) {
    /**
     * Validates the payload reference and the frame dimensions.
     *
     * <p>Rejects a {@code null} payload and any non-positive {@code width} or {@code height}. The
     * payload reference is shared rather than copied.
     *
     * @throws NullPointerException     if {@code payload} is {@code null}
     * @throws IllegalArgumentException if {@code width} or {@code height} is not greater than
     *                                  {@code 0}
     */
    public VideoPacket {
        Objects.requireNonNull(payload, "payload cannot be null");
        if (width <= 0) {
            throw new IllegalArgumentException("width must be > 0");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be > 0");
        }
    }
}
