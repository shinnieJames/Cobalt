package com.github.auties00.cobalt.call.stream.capture;

import com.github.auties00.cobalt.call.stream.VideoFrame;
import com.github.auties00.cobalt.call.stream.VideoOutputStream;
import com.github.auties00.cobalt.media.ffmpeg.AVCodecParameters;
import com.github.auties00.cobalt.media.ffmpeg.AVFormatContext;
import com.github.auties00.cobalt.media.ffmpeg.AVFrame;
import com.github.auties00.cobalt.media.ffmpeg.AVPacket;
import com.github.auties00.cobalt.media.ffmpeg.AVRational;
import com.github.auties00.cobalt.media.ffmpeg.AVStream;
import com.github.auties00.cobalt.media.ffmpeg.Ffmpeg;
import com.github.auties00.cobalt.media.ffmpeg.FFmpegError;
import com.github.auties00.cobalt.media.ffmpeg.FFmpegLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Transmits the video track of a media file as the local video of a call.
 *
 * <p>This is the device-backed {@link VideoOutputStream} returned by
 * {@link VideoOutputStream#fromFile(Path)}. Its constructor opens the file, picks its first video
 * stream, decodes it, and converts each decoded picture to I420 with libswscale so the output matches
 * {@link VideoFrame}'s layout. The geometry passed to {@code super} is the resolution, frame rate, and
 * bitrate the call engine advertises and encodes at, independent of the file's native video resolution.
 * Each frame's presentation timestamp is rescaled from the stream's native time base to milliseconds.
 * End-of-stream is signalled by {@link #take()} returning {@code null}, and {@link #shutdown()} releases
 * the native demuxer, decoder, and scaler.
 */
public final class FileVideoOutputStream extends VideoOutputStream {
    /**
     * Holds the arena owning every native allocation this stream makes, closed when the stream shuts
     * down.
     */
    private final Arena arena;

    /**
     * Holds the libavformat demuxer context pointer, which owns the input file handle.
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
     * milliseconds.
     */
    private final int timeBaseNum;

    /**
     * Holds the denominator of the stream's time base, used to rescale presentation timestamps to
     * milliseconds.
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
     * Holds the width the current {@link #swsCtx} converter was built for.
     */
    private int swsW;

    /**
     * Holds the height the current {@link #swsCtx} converter was built for.
     */
    private int swsH;

    /**
     * Holds the source pixel format the current {@link #swsCtx} converter was built for.
     *
     * <p>When a later frame uses a different format, the converter is torn down and rebuilt.
     */
    private int swsFmt;

    /**
     * Opens the given media file at the given advertised geometry and prepares the video decode
     * pipeline.
     *
     * <p>Ensures the FFmpeg libraries are loaded, opens the file, picks its first video stream, records
     * that stream's time base for timestamp rescaling, and opens a decoder for its codec. If any step
     * fails the arena is closed before the exception propagates, so a failed construction leaks no
     * native resources.
     *
     * @param path       the media file to open
     * @param width      the advertised frame width in pixels; even and at least 2
     * @param height     the advertised frame height in pixels; even and at least 2
     * @param fps        the target frame rate; at least 1
     * @param bitrateBps the target encoder bitrate in bits per second; at least 1
     * @throws NullPointerException     if {@code path} is {@code null}
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below 2, or
     *                                  {@code fps} or {@code bitrateBps} is below 1
     * @throws IllegalStateException    if the file cannot be opened or has no video stream
     */
    public FileVideoOutputStream(Path path, int width, int height, int fps, int bitrateBps) {
        super(width, height, fps, bitrateBps);
        Objects.requireNonNull(path, "path cannot be null");
        FFmpegLoader.ensureLoaded();
        this.arena = Arena.ofShared();
        try {
            var formatPtr = arena.allocate(ValueLayout.ADDRESS);
            var url = arena.allocateFrom(path.toAbsolutePath().toString());
            FFmpegError.check("avformat_open_input(" + path + ")",
                    Ffmpeg.avformat_open_input(formatPtr, url, MemorySegment.NULL, MemorySegment.NULL));
            this.formatCtx = formatPtr.get(ValueLayout.ADDRESS, 0L)
                    .reinterpret(AVFormatContext.layout().byteSize());
            FFmpegError.check("avformat_find_stream_info",
                    Ffmpeg.avformat_find_stream_info(formatCtx, MemorySegment.NULL));

            this.streamIndex = pickVideoStream(formatCtx);
            if (streamIndex < 0) {
                throw new IllegalStateException("no video stream in " + path);
            }
            var stream = streamPointer(formatCtx, streamIndex);
            var tb = AVStream.time_base(stream);
            this.timeBaseNum = AVRational.num(tb);
            this.timeBaseDen = AVRational.den(tb);
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
     * <p>Drives the demux-decode-convert pipeline until a frame is ready, then returns it. Returns
     * {@code null} once the file is fully drained and no further frames remain, or once
     * {@link #shutdown()} has ended the stream.
     *
     * @return {@inheritDoc}
     * @implNote This implementation does not pace itself: it returns frames as fast as the file decodes.
     * The call engine's capture loop paces the outbound video to wall-clock using each frame's
     * presentation timestamp, so the file is transmitted at its natural rate without this stream having
     * to sleep.
     */
    @Override
    public VideoFrame take() {
        if (closed.get()) {
            return null;
        }
        while (ready.isEmpty() && !drained) {
            pump();
        }
        return ready.pollFirst();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Marks the stream ended and frees the libswscale converter, the decoder output frame, the
     * demuxer packet, the decoder context, and the demuxer context, then closes the owning arena. Guards
     * each pointer against {@code null} and a zero address, so the call is idempotent.
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
     * Drives one round of demux, decode, and convert.
     *
     * <p>Reads one packet; on end-of-input it flushes the decoder and marks the stream drained.
     * Otherwise it feeds packets belonging to the chosen video stream to the decoder and converts the
     * resulting frames.
     */
    private void pump() {
        var read = Ffmpeg.av_read_frame(formatCtx, packet);
        if (read < 0) {
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
     * Pulls every available frame out of the decoder and converts each to I420, queuing them for
     * emission.
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
     * Converts the current {@link #frame} to I420 and wraps it in a {@link VideoFrame} with its
     * presentation timestamp rescaled to milliseconds.
     *
     * <p>Rejects frames whose dimensions are below {@code 2} or odd, since I420's half-resolution chroma
     * planes require even dimensions. Rebuilds the converter when the geometry changes, scales the three
     * planes into a contiguous I420 buffer, and rescales the frame's best-effort timestamp from the
     * stream time base to a non-negative millisecond value.
     *
     * @return the converted frame
     * @throws IllegalStateException if the decoded frame has unsupported dimensions or the scale fails
     */
    private VideoFrame convertCurrentFrame() {
        var w = AVFrame.width(frame);
        var h = AVFrame.height(frame);
        var srcFmt = AVFrame.format(frame);
        if (w < 2 || h < 2 || (w & 1) != 0 || (h & 1) != 0) {
            throw new IllegalStateException(
                    "decoded frame has unsupported dimensions " + w + "x" + h);
        }
        rebuildSwsIfNeeded(w, h, srcFmt);

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

        var ptsRaw = AVFrame.best_effort_timestamp(frame);
        var ptsMs = (timeBaseDen == 0)
                ? 0L
                : Math.max(0L, ptsRaw * 1000L * timeBaseNum / timeBaseDen);
        return new VideoFrame(pcm, w, h, ptsMs);
    }

    /**
     * Rebuilds the libswscale converter when the frame dimensions or pixel format change between
     * frames.
     *
     * <p>Returns immediately when the current converter already matches the requested geometry;
     * otherwise frees the old converter and builds one targeting {@code AV_PIX_FMT_YUV420P} at the same
     * dimensions.
     *
     * @param w   the target width
     * @param h   the target height
     * @param fmt the source pixel format
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
                Ffmpeg.sws_getContext(w, h, fmt, w, h, Ffmpeg.AV_PIX_FMT_YUV420P(),
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
}
