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
 * Captures call video from an operating-system camera as a {@link VideoSource}.
 *
 * <p>Opens a libavdevice capture device, demuxes its video stream, decodes it, and converts each
 * decoded picture to I420 with libswscale so the output matches {@link VideoFrame}'s layout. The
 * no-argument constructor auto-detects the running platform and opens its conventional default
 * camera; {@link #Camera(String)} opens a named device using the platform-default input format; and
 * {@link #Camera(String, String)} is the power-user entry point that opens an explicit libavdevice
 * input-format and URL pair for cases the platform default does not cover. The same pipeline backs
 * the screen-grab sources created through {@link Screen}.
 *
 * <h2>Platform defaults</h2>
 *
 * <ul>
 *   <li>Linux: {@code v4l2} on {@code /dev/video0}</li>
 *   <li>macOS: {@code avfoundation} on device index {@code 0}</li>
 *   <li>Windows: requires explicit selection, so {@link #Camera(String)} must be given the device's
 *       friendly name (for example {@code "Integrated Camera"}), because libavdevice's {@code dshow}
 *       input format has no stable default index</li>
 * </ul>
 *
 * @apiNote Wire this source into a call to transmit live camera video. Prefer the no-argument
 * {@link #Camera()} on Linux and macOS; on Windows there is no conventional default, so use
 * {@link #Camera(String)} with the device's friendly name. Capture is blocking: {@link #next()}
 * returns once a frame is available, or {@code null} when the device closes. Always close the source
 * (it is {@link AutoCloseable}) so the operating-system device and native decoder are released.
 */
public final class Camera implements VideoSource, AutoCloseable {
    /**
     * Holds the arena owning every native allocation this source makes, closed when the source
     * closes.
     */
    private final Arena arena;

    /**
     * Holds the libavformat input context pointer, which owns the capture device handle.
     */
    private final MemorySegment formatCtx;

    /**
     * Holds the libavcodec decoder context pointer.
     */
    private final MemorySegment codecCtx;

    /**
     * Holds the reusable demuxer packet pointer driven by the read loop.
     */
    private final MemorySegment packet;

    /**
     * Holds the reusable decoded-frame pointer that the decoder writes into.
     */
    private final MemorySegment frame;

    /**
     * Holds the index of the video stream chosen from the device's container.
     */
    private final int streamIndex;

    /**
     * Holds the libswscale converter pointer, lazily built and rebuilt whenever the
     * {@code (width, height, source pixel format)} triple changes between captured frames.
     */
    private MemorySegment swsCtx;

    /**
     * Holds the width the current {@link #swsCtx} converter was built for.
     */
    private int swsW;

    /**
     * Holds the height the current {@link #swsCtx} converter was built for.
     */
    private int swsH;

    /**
     * Holds the source pixel format the current {@link #swsCtx} converter was built for.
     */
    private int swsFmt;

    /**
     * Opens the platform's default camera.
     *
     * <p>Equivalent to {@link #Camera(String, String)} with the platform's default input format and
     * default device URL.
     *
     * @throws UnsupportedOperationException if the platform has no conventional default camera (such
     *                                       as Windows)
     * @throws IllegalStateException         if the device cannot be opened or has no video stream
     */
    public Camera() {
        this(defaultIndev(), defaultUrl());
    }

    /**
     * Opens a named camera using the platform's default libavdevice input format.
     *
     * <p>Uses {@code v4l2} on Linux, {@code avfoundation} on macOS, and {@code dshow} on Windows.
     * On Windows the argument is treated as a device friendly name and prefixed with
     * {@code "video="} as {@code dshow} requires.
     *
     * @param deviceUrl the device URL or name (for example {@code "/dev/video1"} on Linux,
     *                  {@code "1"} on macOS, or {@code "Integrated Camera"} on Windows)
     * @throws IllegalStateException if the device cannot be opened or has no video stream
     */
    public Camera(String deviceUrl) {
        this(defaultIndev(), normalizeUrlForPlatform(deviceUrl));
    }

    /**
     * Opens an explicit libavdevice input-format and URL pair and prepares the demux and decode
     * pipeline.
     *
     * <p>Ensures the FFmpeg libraries are loaded, registers libavdevice, resolves the named input
     * format, opens the device, picks its first video stream, and opens a decoder for that stream's
     * codec. This is the power-user constructor for cases where neither {@link #Camera()} nor
     * {@link #Camera(String)} selects the right device. If any step fails the arena is closed before
     * the exception propagates, so a failed construction leaks no native resources.
     *
     * @param indev the libavdevice input-format name (for example {@code "v4l2"})
     * @param url   the device URL
     * @throws NullPointerException  if {@code indev} or {@code url} is {@code null}
     * @throws IllegalStateException if the named input format is unavailable in this build, the
     *                               device cannot be opened, or it has no video stream
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
     * {@inheritDoc}
     *
     * <p>Reads packets from the device until one decodes into a frame, converts that frame to I420,
     * and returns it. Packets belonging to other streams and decoder "need more input" results are
     * skipped transparently. Returns {@code null} only when the device reports an unrecoverable
     * end-of-input.
     *
     * @return {@inheritDoc}
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
     * Returns the libavdevice input-format name conventional for the running platform.
     *
     * <p>Resolves to {@code avfoundation} on macOS, {@code dshow} on Windows, and {@code v4l2}
     * elsewhere.
     *
     * @return the platform's conventional input-format name
     */
    static String defaultIndev() {
        var os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) return "avfoundation";
        if (os.contains("win")) return "dshow";
        return "v4l2";
    }

    /**
     * Returns the device URL conventional for the running platform's default camera.
     *
     * <p>Resolves to {@code "0"} on macOS and {@code "/dev/video0"} on Linux. Windows has no stable
     * default device, so it is unsupported here.
     *
     * @return the platform's conventional default-camera URL
     * @throws UnsupportedOperationException on Windows, where there is no stable default device
     */
    private static String defaultUrl() {
        var os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) return "0";
        if (os.contains("win")) {
            throw new UnsupportedOperationException(
                    "Windows has no default camera URL - pass a device "
                            + "name to Camera(String), e.g. \"Integrated Camera\"");
        }
        return "/dev/video0";
    }

    /**
     * Adjusts a caller-supplied device URL into the form the platform's libavdevice input format
     * expects.
     *
     * <p>Wraps a Windows {@code dshow} name with {@code "video="} when it is not already prefixed,
     * and leaves the URL unchanged on other platforms.
     *
     * @param url the caller-supplied device URL
     * @return the platform-correct device URL
     * @throws NullPointerException if {@code url} is {@code null}
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
     * Returns the {@code AVInputFormat} matching the given name from libavdevice's registered video
     * devices.
     *
     * <p>Walks the registered input-device list and returns the first whose name equals the
     * argument, or {@code null} when none matches.
     *
     * @param name the input-format name to find
     * @return the matching input-format pointer, or {@code null} if none is registered under that
     *         name
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
     * Converts the current decoded frame to I420 with libswscale and wraps it in a
     * {@link VideoFrame}.
     *
     * <p>Rejects frames whose dimensions are below {@code 2} or odd, since I420's half-resolution
     * chroma planes require even dimensions. Rebuilds the libswscale converter when the frame's
     * dimensions or source pixel format differ from the converter's current configuration, scales
     * the three planes into a contiguous I420 buffer, and stamps the frame with the current wall
     * clock.
     *
     * @return the converted frame
     * @throws IllegalStateException if the captured frame has unsupported dimensions, the converter
     *                               cannot be built, or the scale fails
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
     * Releases the capture device and decoder resources.
     *
     * <p>Frees the libswscale converter and every libav* allocation, then closes the owning arena.
     * Guards each pointer against {@code null} and a zero address, so the call is idempotent.
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
     * Returns the index of the first video stream in the device's container, or {@code -1} when none
     * exists.
     *
     * @param formatCtx the device input context
     * @return the video stream index, or {@code -1} if the container has no video stream
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
     * Returns the {@code AVStream} pointer at the given index in the device's container.
     *
     * @param formatCtx the device input context
     * @param index     the stream index
     * @return the stream pointer at that index
     */
    private static MemorySegment streamPointer(MemorySegment formatCtx, int index) {
        var n = AVFormatContext.nb_streams(formatCtx);
        var streamsArr = AVFormatContext.streams(formatCtx)
                .reinterpret((long) n * ValueLayout.ADDRESS.byteSize());
        return streamsArr.getAtIndex(ValueLayout.ADDRESS, index)
                .reinterpret(AVStream.layout().byteSize());
    }
}
