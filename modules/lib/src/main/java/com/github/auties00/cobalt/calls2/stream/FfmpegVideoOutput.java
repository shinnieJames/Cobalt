package com.github.auties00.cobalt.calls2.stream;

import com.github.auties00.cobalt.util.ffmpeg.AVCodecParameters;
import com.github.auties00.cobalt.util.ffmpeg.AVFormatContext;
import com.github.auties00.cobalt.util.ffmpeg.AVFrame;
import com.github.auties00.cobalt.util.ffmpeg.AVPacket;
import com.github.auties00.cobalt.util.ffmpeg.AVRational;
import com.github.auties00.cobalt.util.ffmpeg.AVStream;
import com.github.auties00.cobalt.util.ffmpeg.Ffmpeg;
import com.github.auties00.cobalt.util.ffmpeg.FFmpegError;
import com.github.auties00.cobalt.util.ffmpeg.FFmpegLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Provides the FFmpeg-backed base of the demuxed-media video sources of a call, decoding the video track
 * of an input into the call's {@link VideoPixelFormat#I420 I420} frames at the input's detected geometry.
 *
 * <p>This is the shared engine of {@link FileVideoOutput} (a local media file) and {@link UriVideoOutput}
 * (a media stream addressed by URI). A subclass supplies only how the input is opened, through the
 * {@link Opener} it hands to the shared {@link #openInput(Duration, Opener)} probe; the advertised geometry
 * is the input's own native pixel geometry, capped to {@code 1280} on the longer side and rounded to even,
 * detected by that probe rather than supplied by the subclass. Everything downstream of the opened demuxer
 * is identical and lives here: picking the first video stream, recording its time base for timestamp
 * rescaling, opening its decoder, decoding each packet, and converting each decoded picture to I420 at the
 * detected {@link #width()} by {@link #height()} geometry with libswscale. Each frame's presentation
 * timestamp is rescaled from the stream's native time base to microseconds. End-of-stream is signalled by
 * {@link #take()} returning {@code null}, and {@link #shutdown()} releases the native demuxer, decoder, and
 * scaler.
 *
 * <p>Unlike the audio base, this source decodes inline on the engine's drain thread rather than ahead of
 * it: the call engine's capture loop paces the outbound video to wall-clock using each frame's
 * presentation timestamp, so the source itself does not need to read ahead or sleep. Every blocking demux
 * read is bounded by the optional read timeout passed to the constructor: a local-file source passes
 * {@code null}, and a network source passes its timeout, in which case a watchdog-aborted read surfaces as
 * an {@link IllegalStateException} from {@link #take()} rather than a clean end-of-input.
 *
 * @implNote This implementation is the calls2 port of the legacy media-file video source; each converted
 * picture carries the {@link VideoFrame#ptsMicros()} microsecond presentation timestamp rescaled from the
 * stream time base, rather than the legacy millisecond clock.
 */
public sealed class FfmpegVideoOutput extends BufferedVideoOutput
        permits FileVideoOutput, UriVideoOutput {
    /**
     * Opens and probes the input demuxer for an {@link FfmpegVideoOutput}.
     *
     * <p>An implementation opens the demuxer with {@code avformat_open_input}, probes it with
     * {@code avformat_find_stream_info}, and returns the opened {@code AVFormatContext} pointer
     * reinterpreted to {@link AVFormatContext#layout()}'s size. A network opener also allocates the
     * context with {@code avformat_alloc_context}, installs the watchdog on it before opening, and arms
     * the watchdog around each blocking call; a file opener ignores the watchdog. The implementation runs
     * inside the {@link FfmpegVideoOutput} constructor, and any exception it throws aborts construction
     * with the owning arena already released.
     */
    @FunctionalInterface
    protected interface Opener {
        /**
         * Opens and probes the input, returning its demuxer context.
         *
         * @param arena    the source's lifetime arena that owns the native allocations the open makes
         * @param watchdog the timeout watchdog to install and arm around blocking calls, or ignore for a
         *                 non-blocking input
         * @return the opened and probed {@code AVFormatContext} pointer, reinterpreted to its layout size
         * @throws IllegalStateException if the input cannot be opened or probed
         */
        MemorySegment open(Arena arena, FfmpegIoWatchdog watchdog);
    }

    /**
     * Holds the maximum time any single blocking demux read may take before the watchdog aborts it, or
     * {@code null} for a non-blocking input that needs no read timeout.
     */
    private final Duration readTimeout;

    /**
     * Holds the timeout watchdog the opener installs and the read loop arms, shared with the opener so a
     * stalled open or read aborts instead of blocking the engine's video drain thread forever.
     */
    private final FfmpegIoWatchdog watchdog;

    /**
     * Holds the arena owning every native allocation this source makes, closed when the source shuts
     * down.
     */
    private final Arena arena;

    /**
     * Holds the libavformat demuxer context pointer, which owns the input handle.
     */
    private final MemorySegment formatCtx;

    /**
     * Holds the libavcodec decoder context pointer.
     */
    private final MemorySegment codecCtx;

    /**
     * Holds the libswscale converter pointer that scales the decoded pixel format to
     * {@code AV_PIX_FMT_YUV420P}, lazily built and rebuilt when the frame geometry changes.
     */
    private MemorySegment swsCtx;

    /**
     * Holds the reusable demuxer packet pointer driven by the read loop.
     */
    private final MemorySegment packet;

    /**
     * Holds the reusable decoder output frame pointer that the decoder writes into.
     */
    private final MemorySegment frame;

    /**
     * Holds the index of the video stream chosen from the container.
     */
    private final int streamIndex;

    /**
     * Holds the numerator of the stream's time base, used to rescale presentation timestamps to
     * microseconds.
     */
    private final int timeBaseNum;

    /**
     * Holds the denominator of the stream's time base, used to rescale presentation timestamps to
     * microseconds.
     */
    private final int timeBaseDen;

    /**
     * Holds the queue of converted frames awaiting emission by {@link #take()}.
     */
    private final Deque<VideoFrame> ready = new ArrayDeque<>();

    /**
     * Holds whether the demuxer has reached end-of-input and the decoder has been flushed.
     */
    private boolean drained;

    /**
     * Holds the decoded source width the current {@link #swsCtx} converter was built for.
     */
    private int swsW;

    /**
     * Holds the decoded source height the current {@link #swsCtx} converter was built for.
     */
    private int swsH;

    /**
     * Holds the decoded source pixel format the current {@link #swsCtx} converter was built for.
     *
     * <p>When a later frame uses a different format, the converter is torn down and rebuilt.
     */
    private int swsFmt;

    /**
     * Adopts the native handles of an already-opened input and advertises its detected geometry.
     *
     * <p>The shared {@link #openInput(Duration, Opener)} probe runs the whole open-and-decode-prepare
     * sequence ahead of this constructor and reports the input's detected pixel geometry, capped to
     * {@code 1280} on the longer side, through the {@link OpenedInput} it returns; this constructor
     * advertises that geometry to the engine at a fixed {@code 30} frames per second and adopts the probe's
     * demuxer, decoder, packet, and frame handles together with its arena, watchdog, time base, and read
     * timeout. The probe already releases its arena on any open failure, so reaching this constructor means
     * the pipeline is ready and nothing leaks here.
     *
     * @param in         the opened-and-probed input whose detected geometry and native handles this source
     *                   adopts
     * @param bitrateBps the target encoder bitrate in bits per second; at least {@code 1}
     * @throws IllegalArgumentException if {@code bitrateBps} is below {@code 1}
     */
    protected FfmpegVideoOutput(OpenedInput in, int bitrateBps) {
        super(in.width(), in.height(), 30, bitrateBps);
        this.readTimeout = in.readTimeout();
        this.watchdog = in.watchdog();
        this.arena = in.arena();
        this.formatCtx = in.formatCtx();
        this.codecCtx = in.codecCtx();
        this.packet = in.packet();
        this.frame = in.frame();
        this.streamIndex = in.streamIndex();
        this.timeBaseNum = in.timeBaseNum();
        this.timeBaseDen = in.timeBaseDen();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Drives the demux-decode-convert pipeline until a frame is ready, then returns it. Returns
     * {@code null} once the input is fully drained and no further frames remain, or once
     * {@link #shutdown()} has ended the source. A demux, decode, or convert failure, including a
     * watchdog-aborted read, ends the source and propagates as an {@link IllegalStateException}.
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if a configured read timeout aborts the demux read, or the demux,
     *                               decode, or convert reports a hard failure
     * @implNote This implementation does not pace itself: it returns frames as fast as the input decodes.
     * The call engine's capture loop paces the outbound video to wall-clock using each frame's
     * presentation timestamp, so the input is transmitted at its natural rate without this source having
     * to sleep.
     */
    @Override
    public VideoFrame take() {
        if (closed.get()) {
            return null;
        }
        try {
            while (ready.isEmpty() && !drained) {
                pump();
            }
        } catch (RuntimeException e) {
            drained = true;
            throw e;
        }
        return ready.pollFirst();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Marks the source ended, cancels the timeout watchdog so a parked demux read returns at once, then
     * frees the libswscale converter, the decoder output frame, the demuxer packet, the decoder context,
     * and the demuxer context, and closes the owning arena. Guards each pointer against {@code null} and a
     * zero address, so the call is idempotent.
     */
    @Override
    public void shutdown() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        watchdog.cancel();
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
     * Drives one round of demux, decode, and convert.
     *
     * <p>Reads one packet, arming the watchdog around the read when a read timeout is configured; on
     * end-of-input it flushes the decoder and marks the source drained, and on a watchdog-aborted read it
     * throws so {@link #take()} surfaces the timeout. Otherwise it feeds packets belonging to the chosen
     * video stream to the decoder and converts the resulting frames.
     *
     * @throws IllegalStateException if a configured read timeout aborts the demux read, or the decoder
     *                               reports a hard failure
     */
    private void pump() {
        if (readTimeout != null) {
            watchdog.arm(readTimeout);
        }
        var read = Ffmpeg.av_read_frame(formatCtx, packet);
        if (readTimeout != null) {
            watchdog.disarm();
        }
        if (read < 0) {
            if (readTimeout != null && watchdog.fired()) {
                throw new IllegalStateException("input read timed out after " + readTimeout);
            }
            Ffmpeg.avcodec_send_packet(codecCtx, MemorySegment.NULL);
            drainDecoder();
            drained = true;
            return;
        }
        try {
            var idx = AVPacket.stream_index(packet);
            if (idx != streamIndex) {
                return;
            }
            var sent = Ffmpeg.avcodec_send_packet(codecCtx, packet);
            if (sent < 0 && !FFmpegError.isAgain(sent)) {
                throw new IllegalStateException("avcodec_send_packet failed: "
                        + FFmpegError.describe(sent));
            }
            drainDecoder();
        } finally {
            Ffmpeg.av_packet_unref(packet);
        }
    }

    /**
     * Pulls every available frame out of the decoder and converts each to
     * {@link VideoPixelFormat#I420 I420}, queuing them for emission.
     *
     * @throws IllegalStateException if the decoder reports a hard failure
     */
    private void drainDecoder() {
        while (true) {
            var got = Ffmpeg.avcodec_receive_frame(codecCtx, frame);
            if (FFmpegError.isAgain(got) || FFmpegError.isEof(got)) {
                return;
            }
            if (got < 0) {
                throw new IllegalStateException("avcodec_receive_frame failed: "
                        + FFmpegError.describe(got));
            }
            try {
                ready.addLast(convertCurrentFrame());
            } finally {
                Ffmpeg.av_frame_unref(frame);
            }
        }
    }

    /**
     * Converts the current {@link #frame} to {@link VideoPixelFormat#I420 I420} at the advertised geometry
     * and wraps it in a {@link VideoFrame} with its presentation timestamp rescaled to microseconds.
     *
     * <p>Rejects decoded frames whose dimensions are below {@code 2} or odd, since I420's half-resolution
     * chroma planes require even dimensions. Rebuilds the converter when the decoded geometry changes,
     * scales the three planes into a contiguous I420 buffer sized for the advertised {@link #width()} by
     * {@link #height()} geometry the engine encodes at, and rescales the frame's best-effort timestamp
     * from the stream time base to a non-negative microsecond value.
     *
     * @return the converted frame at the advertised geometry
     * @throws IllegalStateException if the decoded frame has unsupported dimensions or the scale fails
     * @implNote This implementation scales the decoded picture to the advertised geometry here, in the
     * source, because the source already owns an {@code sws} context; WhatsApp likewise scales the decoded
     * media-file picture to the negotiated encode resolution before encoding, so the advertised and encoded
     * resolutions stay identical and the engine's encoder, built at the advertised geometry, never rejects
     * a native-resolution frame.
     */
    private VideoFrame convertCurrentFrame() {
        var w = AVFrame.width(frame);
        var h = AVFrame.height(frame);
        var srcFmt = AVFrame.format(frame);
        if (w < 2 || h < 2 || (w & 1) != 0 || (h & 1) != 0) {
            throw new IllegalStateException(
                    "decoded frame has unsupported dimensions " + w + "x" + h);
        }
        var dstW = width();
        var dstH = height();
        rebuildSwsIfNeeded(w, h, srcFmt);

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

        var ptsRaw = AVFrame.best_effort_timestamp(frame);
        var ptsMicros = (timeBaseDen == 0)
                ? 0L
                : Math.max(0L, ptsRaw * 1_000_000L * timeBaseNum / timeBaseDen);
        return new VideoFrame(pixels, VideoPixelFormat.I420, dstW, dstH, ptsMicros);
    }

    /**
     * Rebuilds the libswscale converter when the decoded frame dimensions or pixel format change between
     * frames.
     *
     * <p>Returns immediately when the current converter already matches the requested source geometry;
     * otherwise frees the old converter and builds one scaling from the decoded {@code (w, h, fmt)} triple
     * to {@code AV_PIX_FMT_YUV420P} at the advertised {@link #width()} by {@link #height()} geometry.
     *
     * @param w   the decoded source width
     * @param h   the decoded source height
     * @param fmt the decoded source pixel format
     * @throws IllegalStateException if the converter cannot be built
     */
    private void rebuildSwsIfNeeded(int w, int h, int fmt) {
        if (swsCtx != null && swsCtx.address() != 0L
                && w == swsW && h == swsH && fmt == swsFmt) {
            return;
        }
        if (swsCtx != null && swsCtx.address() != 0L) {
            Ffmpeg.sws_freeContext(swsCtx);
        }
        this.swsCtx = FFmpegError.requireNonNull(
                "sws_getContext",
                Ffmpeg.sws_getContext(w, h, fmt, width(), height(), Ffmpeg.AV_PIX_FMT_YUV420P(),
                        Ffmpeg.SWS_BILINEAR(),
                        MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL));
        this.swsW = w;
        this.swsH = h;
        this.swsFmt = fmt;
    }

    /**
     * Returns the index of the first video stream in the container, or {@code -1} when none exists.
     *
     * @param formatCtx the demuxer context
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
     * Returns the {@code AVStream} pointer at the given index in the container.
     *
     * @param formatCtx the demuxer context
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
     * Opens and decode-prepares an input through the given opener and reports its detected geometry and
     * native handles.
     *
     * <p>Ensures the FFmpeg libraries are loaded, allocates the lifetime arena and the timeout watchdog,
     * opens and probes the input through {@code opener}, picks its first video stream, records that stream's
     * time base for timestamp rescaling, reads the stream's native pixel geometry, opens a decoder for its
     * codec, and allocates the reusable packet and frame. The reported geometry is the native geometry
     * passed through {@link #capGeometry(int, int)}, so the source advertises the input's own resolution
     * capped to {@code 1280} on the longer side rather than a fixed default. If any step fails the arena is
     * closed before the exception propagates, so a failed probe leaks no native resource.
     *
     * <p>This probe runs inside the {@code super(...)} argument of a subclass constructor, so its result
     * feeds the {@link FfmpegVideoOutput#FfmpegVideoOutput(OpenedInput, int)} constructor without a flexible
     * constructor body: the input must be fully opened before {@code super} runs because the advertised
     * geometry is only known after the stream is probed.
     *
     * @param readTimeout the maximum time a single blocking demux read may take, recorded for the read loop,
     *                    or {@code null} for a non-blocking input that needs no read timeout
     * @param opener      the strategy that opens and probes the input demuxer
     * @return the opened input's detected geometry and native handles
     * @throws NullPointerException  if {@code opener} is {@code null}
     * @throws IllegalStateException if the input cannot be opened, has no video stream, or its decoder
     *                               cannot be initialized
     */
    protected static OpenedInput openInput(Duration readTimeout, Opener opener) {
        Objects.requireNonNull(opener, "opener cannot be null");
        FFmpegLoader.ensureLoaded();
        var arena = Arena.ofShared();
        var watchdog = new FfmpegIoWatchdog(arena);
        try {
            var formatCtx = opener.open(arena, watchdog);
            var streamIndex = pickVideoStream(formatCtx);
            if (streamIndex < 0) {
                throw new IllegalStateException("no video stream in input");
            }
            var stream = streamPointer(formatCtx, streamIndex);
            var tb = AVStream.time_base(stream);
            var timeBaseNum = AVRational.num(tb);
            var timeBaseDen = AVRational.den(tb);
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
            return new OpenedInput(capped[0], capped[1], readTimeout, watchdog, arena, formatCtx,
                    codecCtx, packet, frame, streamIndex, timeBaseNum, timeBaseDen);
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
     * the longer side bounds the encode cost of a high-resolution source while keeping its aspect ratio, so
     * a 16:9 input is advertised as 16:9 rather than squished to a fixed 4:3 default.
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
     * Carries the detected geometry and native handles of an input opened by
     * {@link #openInput(Duration, Opener)}.
     *
     * <p>Lets the probe run the full open-and-probe sequence before the
     * {@link FfmpegVideoOutput#FfmpegVideoOutput(OpenedInput, int)} constructor runs, so the constructor can
     * advertise the detected geometry through {@code super(...)} and then adopt the native handles, without
     * a flexible constructor body.
     *
     * @param width       the advertised frame width in pixels, capped and even
     * @param height      the advertised frame height in pixels, capped and even
     * @param readTimeout the maximum time a single blocking demux read may take, or {@code null} for a
     *                    non-blocking input
     * @param watchdog    the timeout watchdog the opener installed and the read loop arms
     * @param arena       the arena owning every native allocation the open made
     * @param formatCtx   the libavformat demuxer context pointer
     * @param codecCtx    the libavcodec decoder context pointer
     * @param packet      the reusable demuxer packet pointer
     * @param frame       the reusable decoder output frame pointer
     * @param streamIndex the index of the chosen video stream
     * @param timeBaseNum the numerator of the chosen stream's time base
     * @param timeBaseDen the denominator of the chosen stream's time base
     */
    private record OpenedInput(int width, int height, Duration readTimeout, FfmpegIoWatchdog watchdog,
                               Arena arena, MemorySegment formatCtx, MemorySegment codecCtx,
                               MemorySegment packet, MemorySegment frame, int streamIndex,
                               int timeBaseNum, int timeBaseDen) {
    }
}
