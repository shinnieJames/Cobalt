package com.github.auties00.cobalt.calls2.stream;

import com.github.auties00.cobalt.util.ffmpeg.AVCodecParameters;
import com.github.auties00.cobalt.util.ffmpeg.AVFormatContext;
import com.github.auties00.cobalt.util.ffmpeg.AVFrame;
import com.github.auties00.cobalt.util.ffmpeg.AVInputFormat;
import com.github.auties00.cobalt.util.ffmpeg.AVPacket;
import com.github.auties00.cobalt.util.ffmpeg.AVStream;
import com.github.auties00.cobalt.util.ffmpeg.Ffmpeg;
import com.github.auties00.cobalt.util.ffmpeg.FFmpegError;
import com.github.auties00.cobalt.util.ffmpeg.FFmpegLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * Captures an operating-system camera as the local video of a call.
 *
 * <p>This is the device-backed {@link BufferedVideoOutput} returned by
 * {@link BufferedVideoOutput#fromCamera()}. Its constructor opens a libavdevice capture device, demuxes
 * its video stream, decodes it, and arranges to convert each decoded picture to
 * {@link VideoPixelFormat#I420 I420} with libswscale so the output matches {@link VideoFrame}'s layout.
 * The geometry the call engine advertises and encodes at is the device's own native capture resolution,
 * capped to {@code 1280} on the longer side and rounded to even, detected when the device is opened rather
 * than supplied by the caller, so a 16:9 camera is advertised as 16:9 rather than squished to a fixed
 * default. The no-argument constructor auto-detects the running platform and opens its conventional default
 * camera; {@link #CameraVideoOutput(String)} opens a named device using the platform-default input format;
 * and {@link #CameraVideoOutput(String, String)} is the power-user entry point that opens an explicit
 * libavdevice input-format and URL pair for cases the platform default does not cover. The same pipeline
 * backs the screen-grab sources created through {@link ScreenVideoOutput}.
 *
 * <h2>Platform defaults</h2>
 *
 * <ul>
 *   <li>Linux: {@code v4l2} on {@code /dev/video0}</li>
 *   <li>macOS: {@code avfoundation} on device index {@code 0}</li>
 *   <li>Windows: requires explicit selection, so
 *       {@link #CameraVideoOutput(String, int, int, int, int)} must be given the device's friendly name
 *       (for example {@code "Integrated Camera"}), because libavdevice's {@code dshow} input format has
 *       no stable default index</li>
 * </ul>
 *
 * <p>Capture is blocking: {@link #take()} returns once a frame is available, or {@code null} when the
 * device closes. {@link #shutdown()} releases the operating-system device and the native decoder.
 *
 * @implNote This implementation is the calls2 port of the legacy camera capture stream. Each captured
 * picture is scaled to the advertised {@link #width()} by {@link #height()} geometry before emission, so
 * the engine's encoder, built at that geometry, never rejects a native-resolution frame; and each frame
 * carries a monotonic zero-based {@link VideoFrame#ptsMicros()} measured from {@link System#nanoTime()}
 * on the first frame rather than absolute wall-clock time, so the derived RTP timestamp never moves
 * backward.
 */
public sealed class CameraVideoOutput extends BufferedVideoOutput
        permits ScreenVideoOutput {
    /**
     * Holds the arena owning every native allocation this source makes, closed when the source shuts
     * down.
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
     * Holds the libswscale converter pointer, lazily built and rebuilt whenever the captured
     * {@code (width, height, source pixel format)} triple changes between frames; its destination is
     * fixed at the advertised {@link #width()} by {@link #height()} geometry.
     */
    private MemorySegment swsCtx;

    /**
     * Holds the captured source width the current {@link #swsCtx} converter was built for.
     */
    private int swsW;

    /**
     * Holds the captured source height the current {@link #swsCtx} converter was built for.
     */
    private int swsH;

    /**
     * Holds the captured source pixel format the current {@link #swsCtx} converter was built for.
     */
    private int swsFmt;

    /**
     * Holds the {@link System#nanoTime()} reading taken on the first converted frame, or
     * {@link Long#MIN_VALUE} until the first frame is converted.
     *
     * <p>Each frame's presentation timestamp is the elapsed time since this base, so the source clock
     * starts at zero on the first captured frame and only ever increases, independent of any wall-clock
     * step.
     */
    private long ptsBaseNanos = Long.MIN_VALUE;

    /**
     * Opens the platform's default camera, detects its native geometry, and begins capturing.
     *
     * <p>Equivalent to {@link #CameraVideoOutput(String, String)} with the platform's default input format
     * and default device URL.
     *
     * @throws UnsupportedOperationException if the platform has no conventional default camera (such as
     *                                       Windows)
     * @throws IllegalStateException         if the device cannot be opened or has no video stream
     */
    public CameraVideoOutput() {
        this(defaultIndev(), defaultUrl());
    }

    /**
     * Opens a named camera using the platform's default libavdevice input format, detects its native
     * geometry, and begins capturing.
     *
     * <p>Uses {@code v4l2} on Linux, {@code avfoundation} on macOS, and {@code dshow} on Windows. On
     * Windows the argument is treated as a device friendly name and prefixed with {@code "video="} as
     * {@code dshow} requires.
     *
     * @param deviceUrl the device URL or name (for example {@code "/dev/video1"} on Linux, {@code "1"} on
     *                  macOS, or {@code "Integrated Camera"} on Windows)
     * @throws NullPointerException  if {@code deviceUrl} is {@code null}
     * @throws IllegalStateException if the device cannot be opened or has no video stream
     */
    public CameraVideoOutput(String deviceUrl) {
        this(defaultIndev(), normalizeUrlForPlatform(deviceUrl));
    }

    /**
     * Opens an explicit libavdevice input-format and URL pair, detects its native geometry, and prepares
     * the demux and decode pipeline.
     *
     * <p>This is the power-user constructor for cases where neither {@link #CameraVideoOutput()} nor
     * {@link #CameraVideoOutput(String)} selects the right device. The advertised geometry is the device's
     * own native capture resolution, capped to {@code 1280} on the longer side and rounded to even, at the
     * default 30 frames per second and the recovered initial bitrate.
     *
     * @param indev the libavdevice input-format name (for example {@code "v4l2"})
     * @param url   the device URL
     * @throws NullPointerException  if {@code indev} or {@code url} is {@code null}
     * @throws IllegalStateException if the named input format is unavailable in this build, the device
     *                               cannot be opened, or it has no video stream
     */
    public CameraVideoOutput(String indev, String url) {
        this(openDevice(indev, url));
    }

    /**
     * Adopts the native handles of an already-opened capture device and advertises its detected geometry.
     *
     * <p>The {@link #openDevice(String, String)} probe runs the whole open-and-decode-prepare sequence
     * ahead of this constructor and reports the device's detected geometry, capped to {@code 1280} on the
     * longer side, through the {@link CapturedInput} it returns; this constructor advertises that geometry
     * to the engine at a fixed 30 frames per second and adopts the probe's demuxer, decoder, packet, and
     * frame handles together with its arena. The probe already releases its arena on any open failure, so
     * reaching this constructor means the pipeline is ready and nothing leaks here.
     *
     * @param in the opened-and-probed capture device whose detected geometry and native handles this source
     *           adopts
     */
    private CameraVideoOutput(CapturedInput in) {
        super(in.width(), in.height(), 30, DEFAULT_BITRATE_BPS);
        this.arena = in.arena();
        this.formatCtx = in.formatCtx();
        this.codecCtx = in.codecCtx();
        this.packet = in.packet();
        this.frame = in.frame();
        this.streamIndex = in.streamIndex();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads packets from the device until one decodes into a frame, converts that frame to
     * {@link VideoPixelFormat#I420 I420}, and returns it. Packets belonging to other streams and decoder
     * "need more input" results are skipped transparently. Returns {@code null} when the device reports
     * an unrecoverable end-of-input or once {@link #shutdown()} has ended the source.
     *
     * @return {@inheritDoc}
     * @implNote This implementation does not sleep between frames: the device read blocks until a frame
     * is available, which paces a live camera to its native frame rate, exactly as the legacy capture
     * pump was paced by the same blocking read.
     */
    @Override
    public VideoFrame take() {
        if (closed.get()) {
            return null;
        }
        while (true) {
            int read;
            try {
                read = Ffmpeg.av_read_frame(formatCtx, packet);
            } catch (RuntimeException _) {
                return null;
            }
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
     * {@inheritDoc}
     *
     * <p>Marks the source ended and frees the libswscale converter and every libav* allocation, then
     * closes the owning arena. Guards each pointer against {@code null} and a zero address, so the call
     * is idempotent.
     */
    @Override
    public void shutdown() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
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
                            + "name to CameraVideoOutput(String), e.g. \"Integrated Camera\"");
        }
        return "/dev/video0";
    }

    /**
     * Adjusts a caller-supplied device URL into the form the platform's libavdevice input format
     * expects.
     *
     * <p>Wraps a Windows {@code dshow} name with {@code "video="} when it is not already prefixed, and
     * leaves the URL unchanged on other platforms.
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
     * <p>Walks the registered input-device list and returns the first whose name equals the argument, or
     * {@code null} when none matches.
     *
     * @param name the input-format name to find
     * @return the matching input-format pointer, or {@code null} if none is registered under that name
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
     * Converts the current decoded frame to {@link VideoPixelFormat#I420 I420} at the advertised geometry
     * with libswscale and wraps it in a {@link VideoFrame}.
     *
     * <p>Rejects captured frames whose dimensions are below {@code 2} or odd, since I420's half-resolution
     * chroma planes require even dimensions. Builds the libswscale converter to scale from the captured
     * {@code (width, height, source pixel format)} triple to the advertised {@link #width()} by
     * {@link #height()} geometry the engine encodes at, rebuilding it whenever the captured triple changes,
     * scales the three planes into a contiguous I420 buffer sized for the advertised geometry, and stamps
     * the frame with a monotonic zero-based presentation timestamp.
     *
     * @return the converted frame at the advertised geometry
     * @throws IllegalStateException if the captured frame has unsupported dimensions, the converter
     *                               cannot be built, or the scale fails
     * @implNote This implementation scales the captured picture to the advertised geometry here, in the
     * source, because the source already owns an {@code sws} context; WhatsApp likewise scales the raw
     * capture buffer to the negotiated encode resolution before encoding, so the advertised and encoded
     * resolutions stay identical and the peer receives a decodable stream. The presentation timestamp is
     * the microseconds elapsed since {@link System#nanoTime()} on the first frame rather than absolute
     * wall-clock time, so it is zero-based per source and never decreases across a manual clock change or
     * NTP step, matching the per-stream monotonic capture clock WhatsApp derives RTP timestamps from.
     */
    private VideoFrame convertCurrentFrame() {
        var w = AVFrame.width(frame);
        var h = AVFrame.height(frame);
        var srcFmt = AVFrame.format(frame);
        if (w < 2 || h < 2 || (w & 1) != 0 || (h & 1) != 0) {
            throw new IllegalStateException(
                    "captured frame has unsupported dimensions " + w + "x" + h);
        }
        var dstW = width();
        var dstH = height();
        if (swsCtx == null || swsCtx.address() == 0L
                || w != swsW || h != swsH || srcFmt != swsFmt) {
            if (swsCtx != null && swsCtx.address() != 0L) {
                Ffmpeg.sws_freeContext(swsCtx);
            }
            swsCtx = FFmpegError.requireNonNull("sws_getContext",
                    Ffmpeg.sws_getContext(w, h, srcFmt, dstW, dstH, Ffmpeg.AV_PIX_FMT_YUV420P(),
                            Ffmpeg.SWS_BILINEAR(),
                            MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL));
            swsW = w;
            swsH = h;
            swsFmt = srcFmt;
        }

        var ySize = dstW * dstH;
        var uvSize = (dstW / 2) * (dstH / 2);
        var outSize = ySize + 2 * uvSize;
        var pixels = new byte[outSize];

        try (var local = Arena.ofConfined()) {
            var i420 = local.allocate(outSize);
            var dstData = local.allocate(8L * ValueLayout.ADDRESS.byteSize());
            var dstStride = local.allocate(8L * Integer.BYTES);
            dstData.setAtIndex(ValueLayout.ADDRESS, 0L, i420);
            dstData.setAtIndex(ValueLayout.ADDRESS, 1L, i420.asSlice(ySize));
            dstData.setAtIndex(ValueLayout.ADDRESS, 2L, i420.asSlice(ySize + uvSize));
            dstStride.setAtIndex(ValueLayout.JAVA_INT, 0L, dstW);
            dstStride.setAtIndex(ValueLayout.JAVA_INT, 1L, dstW / 2);
            dstStride.setAtIndex(ValueLayout.JAVA_INT, 2L, dstW / 2);
            var produced = Ffmpeg.sws_scale(swsCtx,
                    AVFrame.data(frame), AVFrame.linesize(frame),
                    0, h, dstData, dstStride);
            if (produced < 0) {
                throw new IllegalStateException("sws_scale failed: " + produced);
            }
            i420.asByteBuffer().get(pixels);
        }
        var now = System.nanoTime();
        if (ptsBaseNanos == Long.MIN_VALUE) {
            ptsBaseNanos = now;
        }
        var ptsMicros = (now - ptsBaseNanos) / 1_000L;
        return new VideoFrame(pixels, VideoPixelFormat.I420, dstW, dstH, ptsMicros);
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

    /**
     * Opens and decode-prepares a libavdevice capture device and reports its detected geometry and native
     * handles.
     *
     * <p>Ensures the FFmpeg libraries are loaded, registers libavdevice, resolves the named input format,
     * opens the device, probes it, picks its first video stream, reads that stream's native pixel geometry,
     * opens a decoder for its codec, and allocates the reusable packet and frame. The reported geometry is
     * the native geometry passed through {@link #capGeometry(int, int)}, so the source advertises the
     * device's own resolution capped to {@code 1280} on the longer side rather than a fixed default. If any
     * step fails the arena is closed before the exception propagates, so a failed probe leaks no native
     * resource.
     *
     * <p>This probe runs inside the {@code this(...)} argument of the public constructors, so its result
     * feeds the {@link #CameraVideoOutput(CapturedInput)} constructor without a flexible constructor body:
     * the device must be fully opened before {@code super} runs because the advertised geometry is only
     * known after the stream is probed.
     *
     * @param indev the libavdevice input-format name (for example {@code "v4l2"})
     * @param url   the device URL
     * @return the opened device's detected geometry and native handles
     * @throws NullPointerException  if {@code indev} or {@code url} is {@code null}
     * @throws IllegalStateException if the named input format is unavailable in this build, the device
     *                               cannot be opened, or it has no video stream
     */
    private static CapturedInput openDevice(String indev, String url) {
        Objects.requireNonNull(indev, "indev cannot be null");
        Objects.requireNonNull(url, "url cannot be null");
        FFmpegLoader.ensureLoaded();
        Ffmpeg.avdevice_register_all();
        var arena = Arena.ofShared();
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
            var formatCtx = formatPtr.get(ValueLayout.ADDRESS, 0L)
                    .reinterpret(AVFormatContext.layout().byteSize());
            FFmpegError.check("avformat_find_stream_info",
                    Ffmpeg.avformat_find_stream_info(formatCtx, MemorySegment.NULL));

            var streamIndex = pickVideoStream(formatCtx);
            if (streamIndex < 0) {
                throw new IllegalStateException("device has no video stream");
            }
            var stream = streamPointer(formatCtx, streamIndex);
            var params = AVStream.codecpar(stream);
            var nativeWidth = AVCodecParameters.width(params);
            var nativeHeight = AVCodecParameters.height(params);
            var codecId = AVCodecParameters.codec_id(params);
            var codec = FFmpegError.requireNonNull(
                    "avcodec_find_decoder(" + codecId + ")",
                    Ffmpeg.avcodec_find_decoder(codecId));
            var codecCtx = FFmpegError.requireNonNull(
                    "avcodec_alloc_context3",
                    Ffmpeg.avcodec_alloc_context3(codec));
            FFmpegError.check("avcodec_parameters_to_context",
                    Ffmpeg.avcodec_parameters_to_context(codecCtx, params));
            FFmpegError.check("avcodec_open2",
                    Ffmpeg.avcodec_open2(codecCtx, codec, MemorySegment.NULL));

            var packet = FFmpegError.requireNonNull("av_packet_alloc", Ffmpeg.av_packet_alloc());
            var frame = FFmpegError.requireNonNull("av_frame_alloc", Ffmpeg.av_frame_alloc());
            var capped = capGeometry(nativeWidth, nativeHeight);
            return new CapturedInput(capped[0], capped[1], arena, formatCtx, codecCtx, packet, frame,
                    streamIndex);
        } catch (RuntimeException e) {
            arena.close();
            throw e;
        }
    }

    /**
     * Caps a native pixel geometry to the engine's maximum encoded resolution, preserving aspect ratio.
     *
     * <p>Returns the geometry rounded down to even when neither dimension exceeds {@code 1280}; otherwise
     * scales the longer dimension down to {@code 1280} and the shorter dimension proportionally, then rounds
     * both down to the nearest even value of at least {@code 2}. H264 requires even dimensions, and capping
     * the longer side bounds the encode cost of a high-resolution capture while keeping its aspect ratio, so
     * a 16:9 camera is advertised as 16:9 rather than squished to a fixed 4:3 default.
     *
     * @param width  the native pixel width
     * @param height the native pixel height
     * @return a two-element array of the capped, even {@code [width, height]}
     */
    private static int[] capGeometry(int width, int height) {
        if (Math.max(width, height) <= 1280) {
            return new int[]{evenDown(width), evenDown(height)};
        }
        int cappedWidth;
        int cappedHeight;
        if (width >= height) {
            cappedWidth = 1280;
            cappedHeight = (int) Math.round((double) height * 1280 / width);
        } else {
            cappedHeight = 1280;
            cappedWidth = (int) Math.round((double) width * 1280 / height);
        }
        return new int[]{evenDown(cappedWidth), evenDown(cappedHeight)};
    }

    /**
     * Rounds a dimension down to the nearest even value of at least {@code 2}.
     *
     * @param value the dimension to round
     * @return the largest even value not exceeding {@code value}, or {@code 2} when that would be below
     *         {@code 2}
     */
    private static int evenDown(int value) {
        return Math.max(2, value & ~1);
    }

    /**
     * Carries the detected geometry and native handles of a capture device opened by
     * {@link #openDevice(String, String)}.
     *
     * <p>Lets the probe run the full open-and-probe sequence before the
     * {@link #CameraVideoOutput(CapturedInput)} constructor runs, so the constructor can advertise the
     * detected geometry through {@code super(...)} and then adopt the native handles, without a flexible
     * constructor body.
     *
     * @param width       the advertised frame width in pixels, capped and even
     * @param height      the advertised frame height in pixels, capped and even
     * @param arena       the arena owning every native allocation the open made
     * @param formatCtx   the libavformat input context pointer
     * @param codecCtx    the libavcodec decoder context pointer
     * @param packet      the reusable demuxer packet pointer
     * @param frame       the reusable decoded-frame pointer
     * @param streamIndex the index of the chosen video stream
     */
    private record CapturedInput(int width, int height, Arena arena, MemorySegment formatCtx,
                                 MemorySegment codecCtx, MemorySegment packet, MemorySegment frame,
                                 int streamIndex) {
    }
}
