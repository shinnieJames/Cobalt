package com.github.auties00.cobalt.call.video;

import com.github.auties00.cobalt.call.stream.VideoFrame;
import com.github.auties00.cobalt.call.video.h264.H264Decoder;
import com.github.auties00.cobalt.call.video.h264.H264Encoder;
import com.github.auties00.cobalt.call.video.vpx.VP8Decoder;
import com.github.auties00.cobalt.call.video.vpx.VP8Encoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Adapts the VP8 and H.264 encoder/decoder pairs to a single shape the {@link VideoPipeline} can
 * drive without knowing which codec it holds.
 *
 * <p>One instance owns an encoder and a decoder for the same codec and resolution and presents them
 * uniformly: {@link #encode(byte[], long, boolean)} turns one I420 frame into zero or more
 * {@link VideoPacket}s, {@link #decode(byte[], long)} turns one packet's bytes back into a
 * {@link VideoFrame}, {@link #setBitrate(int)} retargets the encoder for BWE, and
 * {@link #frameByteSize()} reports the I420 byte count the encoder expects so the pipeline can reject
 * mismatched captures. The two halves are independent, so the pipeline may encode on one thread and
 * decode on another without contention. Instances are obtained from the {@link #vp8} and
 * {@link #h264} factories and the type is sealed so the pipeline can pattern-match for
 * codec-specific tuning.
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
     * Returns the I420 byte count the encoder expects per frame.
     *
     * <p>The value is {@code width*height + 2*(width/2)*(height/2)}: the full-resolution Y plane plus
     * the two half-resolution chroma planes. The pipeline uses it to validate inbound captures before
     * encoding.
     *
     * @return the per-frame I420 byte size
     */
    int frameByteSize();

    /**
     * Updates the encoder's target bitrate to follow the latest bandwidth estimate.
     *
     * <p>Drives the BWE feedback loop so the call's outbound video tracks available bandwidth. The
     * change takes effect from the next encoded frame.
     *
     * @param targetBitrateBps the new target bitrate in bits per second; must be at least {@code 1}
     */
    void setBitrate(int targetBitrateBps);

    /**
     * Encodes one raw I420 frame, optionally forcing a keyframe.
     *
     * <p>The input must be exactly {@link #frameByteSize()} bytes. The returned list may be empty when
     * the encoder buffered the frame and emitted nothing, may hold one packet, or for VP8 may hold
     * several; a forced keyframe is a VP8 golden frame or an H.264 IDR.
     *
     * @param yuvI420       the input frame bytes; must be exactly {@link #frameByteSize()} bytes
     * @param ptsMs         the presentation timestamp in milliseconds
     * @param forceKeyFrame {@code true} to force a keyframe
     * @return zero or more output packets; empty when the encoder buffered the frame
     */
    List<VideoPacket> encode(byte[] yuvI420, long ptsMs, boolean forceKeyFrame);

    /**
     * Decodes one encoded packet into a {@link VideoFrame}.
     *
     * <p>Returns {@code null} when the codec produced no output for the input, for example a
     * fragmented intermediate H.264 packet or a VP8 inter frame received before its keyframe. The
     * supplied {@code ptsMs} is copied through to {@link VideoFrame#ptsMs()} of any result.
     *
     * @param payload the encoded packet bytes
     * @param ptsMs   the packet's presentation timestamp in milliseconds, copied through to the
     *                {@link VideoFrame#ptsMs()} of the result
     * @return the decoded frame, or {@code null} when the codec produced no output
     */
    VideoFrame decode(byte[] payload, long ptsMs);

    /**
     * Closes the codec and releases all native state.
     *
     * <p>Closes both the encoder and the decoder; idempotent.
     */
    @Override
    void close();

    /**
     * Constructs a VP8 codec adapter wrapping a libvpx encoder and decoder.
     *
     * @param width            the frame width in pixels; even and at least {@code 2}
     * @param height           the frame height in pixels; even and at least {@code 2}
     * @param targetBitrateBps the initial target bitrate in bits per second
     * @param fps              the capture frame rate
     * @return the codec
     */
    static VideoCodec vp8(int width, int height, int targetBitrateBps, int fps) {
        return new Vp8(width, height, targetBitrateBps, fps);
    }

    /**
     * Constructs an H.264 codec adapter wrapping an openh264 encoder and decoder.
     *
     * @param width            the frame width in pixels; even and at least {@code 2}
     * @param height           the frame height in pixels; even and at least {@code 2}
     * @param targetBitrateBps the initial target bitrate in bits per second
     * @param fps              the capture frame rate
     * @return the codec
     */
    static VideoCodec h264(int width, int height, int targetBitrateBps, int fps) {
        return new H264(width, height, targetBitrateBps, fps);
    }

    /**
     * Adapts the VP8 encoder/decoder pair to the {@link VideoCodec} shape.
     *
     * <p>Wraps a {@link VP8Encoder} and a {@link VP8Decoder} over a fixed resolution. VP8 may buffer
     * frames, so {@link #encode(byte[], long, boolean)} can return any number of packets per call.
     */
    final class Vp8 implements VideoCodec {
        /**
         * Holds the wrapped libvpx encoder.
         */
        private final VP8Encoder encoder;

        /**
         * Holds the wrapped libvpx decoder.
         */
        private final VP8Decoder decoder;

        /**
         * Holds the configured frame width in pixels.
         */
        private final int width;

        /**
         * Holds the configured frame height in pixels.
         */
        private final int height;

        /**
         * Constructs a fresh VP8 encoder and decoder pair.
         *
         * <p>The encoder is allocated first; if the decoder fails to allocate, the encoder is closed
         * before the failure propagates so no native state leaks.
         *
         * @param width            the frame width in pixels
         * @param height           the frame height in pixels
         * @param targetBitrateBps the initial target bitrate in bits per second
         * @param fps              the capture frame rate
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
     * Adapts the H.264 encoder/decoder pair to the {@link VideoCodec} shape.
     *
     * <p>Wraps an {@link H264Encoder} and an {@link H264Decoder} over a fixed resolution. H.264 emits
     * at most one packet per encoded frame.
     */
    final class H264 implements VideoCodec {
        /**
         * Holds the wrapped openh264 encoder.
         */
        private final H264Encoder encoder;

        /**
         * Holds the wrapped openh264 decoder.
         */
        private final H264Decoder decoder;

        /**
         * Holds the configured frame width in pixels.
         */
        private final int width;

        /**
         * Holds the configured frame height in pixels.
         */
        private final int height;

        /**
         * Constructs a fresh H.264 encoder and decoder pair.
         *
         * <p>The encoder is allocated first; if the decoder fails to allocate, the encoder is closed
         * before the failure propagates so no native state leaks.
         *
         * @param width            the frame width in pixels
         * @param height           the frame height in pixels
         * @param targetBitrateBps the initial target bitrate in bits per second
         * @param fps              the capture frame rate
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
