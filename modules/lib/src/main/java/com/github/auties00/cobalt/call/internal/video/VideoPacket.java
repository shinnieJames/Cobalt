package com.github.auties00.cobalt.call.internal.video;

import java.util.Objects;

/**
 * One encoded video frame — the boundary type between the
 * {@link VideoPipeline} (which owns the codec) and the RTP transport
 * (#78) (which packetises into RTP, applies SRTP, and ships the
 * bytes).
 *
 * <p>For VP8 ({@link VideoCodec.Vp8}) the {@code payload} is a single
 * VP8 bitstream frame ready for the RFC 7741 packetiser. For H.264
 * ({@link VideoCodec.H264}) the payload is the concatenated NAL units
 * for the picture in bitstream order, ready for the RFC 6184
 * packetiser.
 *
 * @param payload  the encoded frame bytes; never empty
 * @param ptsMs    presentation timestamp in milliseconds — the same
 *                 value that drove the encoder
 * @param width    the frame width in pixels (each packet carries it
 *                 because the encoder may be re-initialised on a
 *                 resolution change mid-call)
 * @param height   the frame height in pixels
 * @param keyFrame {@code true} if this is a keyframe (VP8 keyframe /
 *                 H.264 IDR), starting a new GOP — the RTP sender
 *                 must mark the corresponding RTP packet's keyframe
 *                 bit so the peer's jitter buffer can drop everything
 *                 prior on a recovery request
 */
public record VideoPacket(byte[] payload, long ptsMs, int width, int height, boolean keyFrame) {
    /**
     * Compact constructor — null-checks payload and validates
     * dimensions.
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
