package com.github.auties00.cobalt.call.video;

import com.github.auties00.cobalt.call.io.VideoFrame;
import com.github.auties00.cobalt.call.video.h264.H264Decoder;
import com.github.auties00.cobalt.call.video.h264.H264Encoder;
import com.github.auties00.cobalt.call.video.vpx.VP8Decoder;
import com.github.auties00.cobalt.call.video.vpx.VP8Encoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Codec adapter that hides the VP8-vs-H.264 API differences from the
 * {@link VideoPipeline}. A single instance owns the encoder + decoder
 * pair and exposes a uniform shape:
 *
 * <ul>
 *   <li>{@link #encode(byte[], long, boolean)} — encodes one I420
 *       frame, returning zero or more {@link VideoPacket}s (VP8 may
 *       buffer; H.264 returns at most one per call).</li>
 *   <li>{@link #decode(byte[], long)} — decodes one packet's bytes
 *       to a {@link VideoFrame}, or {@code null} when the codec
 *       produced no output for that input.</li>
 *   <li>{@link #setBitrate(int)} — runtime BWE-driven bitrate update.
 *       VP8 retargets {@code rc_target_bitrate} via libvpx; H.264
 *       calls {@code ISVCEncoder.SetOption(ENCODER_OPTION_BITRATE)}
 *       on the openh264 vtable.</li>
 *   <li>{@link #frameByteSize()} — the I420 byte count the encoder
 *       expects per frame, used by the pipeline to validate inbound
 *       captures.</li>
 * </ul>
 *
 * <p>Constructed via the {@link #vp8} or {@link #h264} factories.
 * Implementations are sealed so the pipeline can pattern-match if it
 * needs codec-specific handling.
 */
public sealed interface VideoCodec extends AutoCloseable
        permits VideoCodec.Vp8, VideoCodec.H264 {
    /**
     * Returns the configured frame width in pixels.
     *
     * @return the width
     */
    int width();

    /**
     * Returns the configured frame height in pixels.
     *
     * @return the height
     */
    int height();

    /**
     * Returns the I420 byte size the encoder expects per call:
     * {@code w*h + 2*(w/2)*(h/2)}.
     *
     * @return the byte size
     */
    int frameByteSize();

    /**
     * Updates the encoder's target bitrate. Drives the BWE feedback
     * loop so the call's outbound video tracks the latest bandwidth
     * estimate.
     *
     * @param targetBitrateBps the new target bitrate in bits per
     *                         second; must be &gt;= 1
     */
    void setBitrate(int targetBitrateBps);

    /**
     * Encodes one raw I420 frame, optionally requesting a keyframe.
     *
     * @param yuvI420        the input frame bytes; must be exactly
     *                       {@link #frameByteSize()} bytes
     * @param ptsMs          presentation timestamp in milliseconds
     * @param forceKeyFrame  {@code true} to force a keyframe (VP8
     *                       golden frame / H.264 IDR)
     * @return zero or more output packets; the list is empty when
     *         the encoder buffered the frame
     */
    List<VideoPacket> encode(byte[] yuvI420, long ptsMs, boolean forceKeyFrame);

    /**
     * Decodes one encoded packet to a {@link VideoFrame}.
     *
     * @param payload the encoded packet bytes
     * @param ptsMs   the packet's presentation timestamp in
     *                milliseconds — copied through to the
     *                {@link VideoFrame#ptsMs()} of the result
     * @return the decoded frame, or {@code null} when the codec
     *         produced no output (e.g. a fragmented intermediate
     *         packet for H.264, or a missing keyframe for VP8)
     */
    VideoFrame decode(byte[] payload, long ptsMs);

    /**
     * Closes the codec and releases all native state. Idempotent.
     */
    @Override
    void close();

    /**
     * Constructs a VP8 codec adapter.
     *
     * @param width            the frame width in pixels (even, &gt;= 2)
     * @param height           the frame height in pixels (even, &gt;= 2)
     * @param targetBitrateBps initial target bitrate in bps
     * @param fps              capture frame rate
     * @return the codec
     */
    static VideoCodec vp8(int width, int height, int targetBitrateBps, int fps) {
        return new Vp8(width, height, targetBitrateBps, fps);
    }

    /**
     * Constructs an H.264 codec adapter.
     *
     * @param width            the frame width in pixels (even, &gt;= 2)
     * @param height           the frame height in pixels (even, &gt;= 2)
     * @param targetBitrateBps initial target bitrate in bps
     * @param fps              capture frame rate
     * @return the codec
     */
    static VideoCodec h264(int width, int height, int targetBitrateBps, int fps) {
        return new H264(width, height, targetBitrateBps, fps);
    }

    /**
     * VP8 codec adapter — wraps {@link VP8Encoder} +
     * {@link VP8Decoder}.
     */
    final class Vp8 implements VideoCodec {
        /**
         * The wrapped libvpx encoder.
         */
        private final VP8Encoder encoder;

        /**
         * The wrapped libvpx decoder.
         */
        private final VP8Decoder decoder;

        /**
         * Configured width.
         */
        private final int width;

        /**
         * Configured height.
         */
        private final int height;

        /**
         * Constructs a fresh VP8 encoder + decoder pair.
         *
         * @param width            the frame width
         * @param height           the frame height
         * @param targetBitrateBps initial target bitrate
         * @param fps              capture frame rate
         */
        Vp8(int width, int height, int targetBitrateBps, int fps) {
            this.width = width;
            this.height = height;
            this.encoder = new VP8Encoder(width, height, targetBitrateBps, fps);
            try {
                this.decoder = new VP8Decoder();
            } catch (RuntimeException e) {
                encoder.close();
                throw e;
            }
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public int frameByteSize() {
            return encoder.frameByteSize();
        }

        @Override
        public void setBitrate(int targetBitrateBps) {
            encoder.setBitrate(targetBitrateBps);
        }

        @Override
        public List<VideoPacket> encode(byte[] yuvI420, long ptsMs, boolean forceKeyFrame) {
            Objects.requireNonNull(yuvI420, "yuvI420 cannot be null");
            var packets = encoder.encode(yuvI420, ptsMs, forceKeyFrame);
            if (packets.isEmpty()) {
                return List.of();
            }
            var out = new ArrayList<VideoPacket>(packets.size());
            for (var pkt : packets) {
                out.add(new VideoPacket(pkt.payload(), pkt.pts(), width, height, pkt.keyFrame()));
            }
            return out;
        }

        @Override
        public VideoFrame decode(byte[] payload, long ptsMs) {
            Objects.requireNonNull(payload, "payload cannot be null");
            var frame = decoder.decode(payload);
            if (frame == null) {
                return null;
            }
            return new VideoFrame(frame.yuvI420(), frame.width(), frame.height(), ptsMs);
        }

        @Override
        public void close() {
            try {
                encoder.close();
            } catch (Throwable _) {
            }
            try {
                decoder.close();
            } catch (Throwable _) {
            }
        }
    }

    /**
     * H.264 codec adapter — wraps {@link H264Encoder} +
     * {@link H264Decoder}. Runtime bitrate adjustment is dispatched
     * through {@code ISVCEncoder.SetOption(ENCODER_OPTION_BITRATE)}.
     */
    final class H264 implements VideoCodec {
        /**
         * The wrapped openh264 encoder.
         */
        private final H264Encoder encoder;

        /**
         * The wrapped openh264 decoder.
         */
        private final H264Decoder decoder;

        /**
         * Configured width.
         */
        private final int width;

        /**
         * Configured height.
         */
        private final int height;

        /**
         * Constructs a fresh H.264 encoder + decoder pair.
         *
         * @param width            the frame width
         * @param height           the frame height
         * @param targetBitrateBps initial target bitrate
         * @param fps              capture frame rate
         */
        H264(int width, int height, int targetBitrateBps, int fps) {
            this.width = width;
            this.height = height;
            this.encoder = new H264Encoder(width, height, targetBitrateBps, fps);
            try {
                this.decoder = new H264Decoder();
            } catch (RuntimeException e) {
                encoder.close();
                throw e;
            }
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public int frameByteSize() {
            return encoder.frameByteSize();
        }

        @Override
        public void setBitrate(int targetBitrateBps) {
            encoder.setBitrate(targetBitrateBps);
        }

        @Override
        public List<VideoPacket> encode(byte[] yuvI420, long ptsMs, boolean forceKeyFrame) {
            Objects.requireNonNull(yuvI420, "yuvI420 cannot be null");
            var pkt = encoder.encode(yuvI420, ptsMs, forceKeyFrame);
            if (pkt == null) {
                return List.of();
            }
            return List.of(new VideoPacket(pkt.payload(), pkt.pts(), width, height, pkt.keyFrame()));
        }

        @Override
        public VideoFrame decode(byte[] payload, long ptsMs) {
            Objects.requireNonNull(payload, "payload cannot be null");
            var frame = decoder.decode(payload);
            if (frame == null) {
                return null;
            }
            return new VideoFrame(frame.yuvI420(), frame.width(), frame.height(), ptsMs);
        }

        @Override
        public void close() {
            try {
                encoder.close();
            } catch (Throwable _) {
            }
            try {
                decoder.close();
            } catch (Throwable _) {
            }
        }
    }
}
