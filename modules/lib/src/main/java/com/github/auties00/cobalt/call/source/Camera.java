package com.github.auties00.cobalt.call.source;

import com.github.auties00.cobalt.call.frame.video.VideoFrame;
import com.github.auties00.cobalt.call.frame.video.VideoSource;
import com.github.auties00.cobalt.media.ffmpeg.AVCodecParameters;
import com.github.auties00.cobalt.media.ffmpeg.AVFormatContext;
import com.github.auties00.cobalt.media.ffmpeg.AVFrame;
import com.github.auties00.cobalt.media.ffmpeg.AVInputFormat;
import com.github.auties00.cobalt.media.ffmpeg.AVPacket;
import com.github.auties00.cobalt.media.ffmpeg.AVStream;
import com.github.auties00.cobalt.media.ffmpeg.Ffmpeg;
import com.github.auties00.cobalt.media.ffmpeg.FFmpegError;
import com.github.auties00.cobalt.media.ffmpeg.FFmpegLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * A {@link VideoSource} that captures from an OS camera. The
 * default constructor auto-detects the running platform and opens
 * its conventional default camera; advanced callers can pass an
 * explicit libavdevice {@code (indev, url)} pair via
 * {@link #Camera(String, String)}.
 *
 * <h2>Platform defaults</h2>
 *
 * <ul>
 *   <li>Linux: {@code v4l2} on {@code /dev/video0}</li>
 *   <li>macOS: {@code avfoundation} on device index {@code 0}</li>
 *   <li>Windows: requires explicit selection — use
 *       {@link #Camera(String)} with the device's friendly name
 *       (e.g. {@code "Integrated Camera"}) since libavdevice's
 *       {@code dshow} input format does not have a stable
 *       default index</li>
 * </ul>
 *
 * <p>Each captured frame is converted to I420 via libswscale and
 * delivered through {@link #next()}. Camera capture is blocking;
 * {@code next()} returns once a frame is available or {@code null}
 * if the device closes.
 */
public final class Camera implements VideoSource, AutoCloseable {
    /**
     * Lifetime arena.
     */
    private final Arena arena;

    /**
     * libavformat input context — owns the device handle.
     */
    private final MemorySegment formatCtx;

    /**
     * libavcodec decoder context.
     */
    private final MemorySegment codecCtx;

    /**
     * Reusable demuxer packet.
     */
    private final MemorySegment packet;

    /**
     * Reusable decoded frame.
     */
    private final MemorySegment frame;

    /**
     * The video stream's index inside the device's container.
     */
    private final int streamIndex;

    /**
     * libswscale converter, lazily (re)built per
     * (width, height, srcFmt) triple.
     */
    private MemorySegment swsCtx;

    /**
     * Width the swscale converter was built for.
     */
    private int swsW;

    /**
     * Height the swscale converter was built for.
     */
    private int swsH;

    /**
     * Source pixel format the swscale converter was built for.
     */
    private int swsFmt;

    /**
     * Opens the platform's default camera. Equivalent to
     * {@code new Camera(defaultIndev(), defaultUrl())}.
     *
     * @throws UnsupportedOperationException if the platform has no
     *                                       conventional default
     *                                       (e.g. Windows)
     */
    public Camera() {
        this(defaultIndev(), defaultUrl());
    }

    /**
     * Opens a camera at the given device URL using the platform's
     * default libavdevice indev (v4l2 on Linux, avfoundation on
     * macOS, dshow on Windows). On Windows the URL is taken as a
     * device friendly name and prefixed with {@code "video="}.
     *
     * @param deviceUrl the device URL or name (e.g.
     *                  {@code "/dev/video1"} on Linux,
     *                  {@code "1"} on macOS,
     *                  {@code "Integrated Camera"} on Windows)
     */
    public Camera(String deviceUrl) {
        this(defaultIndev(), normalizeUrlForPlatform(deviceUrl));
    }

    /**
     * Opens the given libavdevice {@code (indev, url)} pair and
     * sets up the demux + decode pipeline. The power-user
     * constructor for cases where the platform default isn't
     * appropriate.
     *
     * @param indev the libavdevice input format name
     *              (e.g. {@code "v4l2"})
     * @param url   the device URL
     */
    public Camera(String indev, String url) {
        Objects.requireNonNull(indev, "indev cannot be null");
        Objects.requireNonNull(url, "url cannot be null");
        FFmpegLoader.ensureLoaded();
        Ffmpeg.avdevice_register_all();
        this.arena = Arena.ofShared();
        try {
            var ifmt = findInputDevice(indev);
            if (ifmt == null || ifmt.address() == 0L) {
                throw new IllegalStateException("libavdevice has no '" + indev
                        + "' input format on this build");
            }

            var formatPtr = arena.allocate(ValueLayout.ADDRESS);
            var urlSeg = arena.allocateFrom(url);
            FFmpegError.check("avformat_open_input(" + indev + ":" + url + ")",
                    Ffmpeg.avformat_open_input(formatPtr, urlSeg, ifmt, MemorySegment.NULL));
            this.formatCtx = formatPtr.get(ValueLayout.ADDRESS, 0L)
                    .reinterpret(AVFormatContext.layout().byteSize());
            FFmpegError.check("avformat_find_stream_info",
                    Ffmpeg.avformat_find_stream_info(formatCtx, MemorySegment.NULL));

            this.streamIndex = pickVideoStream(formatCtx);
            if (streamIndex < 0) {
                throw new IllegalStateException("device has no video stream");
            }
            var stream = streamPointer(formatCtx, streamIndex);
            var params = AVStream.codecpar(stream);
            var codecId = AVCodecParameters.codec_id(params);
            var codec = FFmpegError.requireNonNull(
                    "avcodec_find_decoder(" + codecId + ")",
                    Ffmpeg.avcodec_find_decoder(codecId));
            this.codecCtx = FFmpegError.requireNonNull(
                    "avcodec_alloc_context3",
                    Ffmpeg.avcodec_alloc_context3(codec));
            FFmpegError.check("avcodec_parameters_to_context",
                    Ffmpeg.avcodec_parameters_to_context(codecCtx, params));
            FFmpegError.check("avcodec_open2",
                    Ffmpeg.avcodec_open2(codecCtx, codec, MemorySegment.NULL));

            this.packet = FFmpegError.requireNonNull("av_packet_alloc", Ffmpeg.av_packet_alloc());
            this.frame = FFmpegError.requireNonNull("av_frame_alloc", Ffmpeg.av_frame_alloc());
        } catch (RuntimeException e) {
            arena.close();
            throw e;
        }
    }

    /**
     * Captures and returns the next I420 frame from the camera.
     * Returns {@code null} only when the underlying device emits
     * an unrecoverable EOF.
     *
     * @return the next frame, or {@code null} on device end
     */
    @Override
    public VideoFrame next() {
        while (true) {
            var read = Ffmpeg.av_read_frame(formatCtx, packet);
            if (read < 0) {
                return null;
            }
            try {
                if (AVPacket.stream_index(packet) != streamIndex) {
                    continue;
                }
                var sent = Ffmpeg.avcodec_send_packet(codecCtx, packet);
                if (sent < 0 && !FFmpegError.isAgain(sent)) {
                    throw new IllegalStateException("avcodec_send_packet failed: "
                            + FFmpegError.describe(sent));
                }
                var got = Ffmpeg.avcodec_receive_frame(codecCtx, frame);
                if (FFmpegError.isAgain(got)) {
                    continue;
                }
                if (got < 0) {
                    return null;
                }
                try {
                    return convertCurrentFrame();
                } finally {
                    Ffmpeg.av_frame_unref(frame);
                }
            } finally {
                Ffmpeg.av_packet_unref(packet);
            }
        }
    }

    /**
     * Returns the libavdevice indev name conventional for the
     * running platform.
     *
     * @return the indev name
     */
    static String defaultIndev() {
        var os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) return "avfoundation";
        if (os.contains("win")) return "dshow";
        return "v4l2";
    }

    /**
     * Returns the libavdevice URL conventional for the running
     * platform's default camera.
     *
     * @return the URL
     * @throws UnsupportedOperationException on Windows, where
     *                                       there is no stable
     *                                       default
     */
    private static String defaultUrl() {
        var os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) return "0";
        if (os.contains("win")) {
            throw new UnsupportedOperationException(
                    "Windows has no default camera URL — pass a device "
                            + "name to Camera(String), e.g. \"Integrated Camera\"");
        }
        return "/dev/video0";
    }

    /**
     * Adjusts a user-supplied device URL into the form the
     * platform's libavdevice indev expects (e.g. wraps a Windows
     * dshow name with {@code "video="}).
     *
     * @param url the user-supplied URL
     * @return the platform-correct URL
     */
    private static String normalizeUrlForPlatform(String url) {
        Objects.requireNonNull(url, "url cannot be null");
        var os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win") && !url.startsWith("video=")) {
            return "video=" + url;
        }
        return url;
    }

    /**
     * Finds the {@code AVInputFormat*} matching the given name
     * in libavdevice's registered set.
     *
     * @param name the input format name
     * @return the format pointer, or {@code null} if not found
     */
    private static MemorySegment findInputDevice(String name) {
        var cursor = MemorySegment.NULL;
        while (true) {
            cursor = Ffmpeg.av_input_video_device_next(cursor);
            if (cursor == null || cursor.address() == 0L) {
                return null;
            }
            var struct = cursor.reinterpret(AVInputFormat.layout().byteSize());
            var namePtr = AVInputFormat.name(struct);
            if (namePtr == null || namePtr.address() == 0L) {
                continue;
            }
            var n = namePtr.reinterpret(Long.MAX_VALUE).getString(0L);
            if (n.equals(name)) {
                return cursor;
            }
        }
    }

    /**
     * libswscale-driven conversion of the current decoded frame
     * into I420.
     *
     * @return the converted {@link VideoFrame}
     */
    private VideoFrame convertCurrentFrame() {
        var w = AVFrame.width(frame);
        var h = AVFrame.height(frame);
        var srcFmt = AVFrame.format(frame);
        if (w < 2 || h < 2 || (w & 1) != 0 || (h & 1) != 0) {
            throw new IllegalStateException(
                    "captured frame has unsupported dimensions " + w + "x" + h);
        }
        if (swsCtx == null || swsCtx.address() == 0L
                || w != swsW || h != swsH || srcFmt != swsFmt) {
            if (swsCtx != null && swsCtx.address() != 0L) {
                Ffmpeg.sws_freeContext(swsCtx);
            }
            swsCtx = FFmpegError.requireNonNull("sws_getContext",
                    Ffmpeg.sws_getContext(w, h, srcFmt, w, h, Ffmpeg.AV_PIX_FMT_YUV420P(),
                            Ffmpeg.SWS_BILINEAR(),
                            MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL));
            swsW = w;
            swsH = h;
            swsFmt = srcFmt;
        }

        var ySize = w * h;
        var uvSize = (w / 2) * (h / 2);
        var outSize = ySize + 2 * uvSize;
        var pcm = new byte[outSize];

        try (var local = Arena.ofConfined()) {
            var i420 = local.allocate(outSize);
            var dstData = local.allocate(8L * ValueLayout.ADDRESS.byteSize());
            var dstStride = local.allocate(8L * Integer.BYTES);
            dstData.setAtIndex(ValueLayout.ADDRESS, 0L, i420);
            dstData.setAtIndex(ValueLayout.ADDRESS, 1L, i420.asSlice(ySize));
            dstData.setAtIndex(ValueLayout.ADDRESS, 2L, i420.asSlice(ySize + uvSize));
            dstStride.setAtIndex(ValueLayout.JAVA_INT, 0L, w);
            dstStride.setAtIndex(ValueLayout.JAVA_INT, 1L, w / 2);
            dstStride.setAtIndex(ValueLayout.JAVA_INT, 2L, w / 2);
            var produced = Ffmpeg.sws_scale(swsCtx,
                    AVFrame.data(frame), AVFrame.linesize(frame),
                    0, h, dstData, dstStride);
            if (produced < 0) {
                throw new IllegalStateException("sws_scale failed: " + produced);
            }
            i420.asByteBuffer().get(pcm);
        }
        return new VideoFrame(pcm, w, h, System.currentTimeMillis());
    }

    /**
     * Releases all device + decoder resources. Idempotent.
     */
    @Override
    public void close() {
        try (arena) {
            if (swsCtx != null && swsCtx.address() != 0L) {
                Ffmpeg.sws_freeContext(swsCtx);
            }
            if (frame != null && frame.address() != 0L) {
                try (var local = Arena.ofConfined()) {
                    var pp = local.allocate(ValueLayout.ADDRESS);
                    pp.set(ValueLayout.ADDRESS, 0L, frame);
                    Ffmpeg.av_frame_free(pp);
                }
            }
            if (packet != null && packet.address() != 0L) {
                try (var local = Arena.ofConfined()) {
                    var pp = local.allocate(ValueLayout.ADDRESS);
                    pp.set(ValueLayout.ADDRESS, 0L, packet);
                    Ffmpeg.av_packet_free(pp);
                }
            }
            if (codecCtx != null && codecCtx.address() != 0L) {
                try (var local = Arena.ofConfined()) {
                    var pp = local.allocate(ValueLayout.ADDRESS);
                    pp.set(ValueLayout.ADDRESS, 0L, codecCtx);
                    Ffmpeg.avcodec_free_context(pp);
                }
            }
            if (formatCtx != null && formatCtx.address() != 0L) {
                try (var local = Arena.ofConfined()) {
                    var pp = local.allocate(ValueLayout.ADDRESS);
                    pp.set(ValueLayout.ADDRESS, 0L, formatCtx);
                    Ffmpeg.avformat_close_input(pp);
                }
            }
        }
    }

    /**
     * Walks {@code formatCtx.streams[]} and returns the first
     * video stream's index.
     *
     * @param formatCtx the device input context
     * @return the stream index, or -1
     */
    private static int pickVideoStream(MemorySegment formatCtx) {
        var n = AVFormatContext.nb_streams(formatCtx);
        var streamsArr = AVFormatContext.streams(formatCtx)
                .reinterpret((long) n * ValueLayout.ADDRESS.byteSize());
        for (var i = 0; i < n; i++) {
            var stream = streamsArr.getAtIndex(ValueLayout.ADDRESS, i)
                    .reinterpret(AVStream.layout().byteSize());
            var params = AVStream.codecpar(stream);
            if (AVCodecParameters.codec_type(params) == Ffmpeg.AVMEDIA_TYPE_VIDEO()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the {@code AVStream*} at the given index.
     *
     * @param formatCtx the input context
     * @param index     the stream index
     * @return the stream pointer
     */
    private static MemorySegment streamPointer(MemorySegment formatCtx, int index) {
        var n = AVFormatContext.nb_streams(formatCtx);
        var streamsArr = AVFormatContext.streams(formatCtx)
                .reinterpret((long) n * ValueLayout.ADDRESS.byteSize());
        return streamsArr.getAtIndex(ValueLayout.ADDRESS, index)
                .reinterpret(AVStream.layout().byteSize());
    }
}
