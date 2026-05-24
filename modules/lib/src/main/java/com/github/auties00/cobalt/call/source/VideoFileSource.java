package com.github.auties00.cobalt.call.source;

import com.github.auties00.cobalt.call.frame.video.VideoFrame;
import com.github.auties00.cobalt.call.frame.video.VideoSource;
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
 * Plays a media file as a {@link VideoSource}. Demuxes the first
 * video stream, decodes it, and runs each decoded frame through
 * libswscale to produce I420 (matching {@link VideoFrame}'s wire
 * layout).
 *
 * <p>Output PTS is in milliseconds (rescaled from the stream's
 * native time base). End-of-stream is signalled by returning
 * {@code null} from {@link #next()}.
 *
 * <p>Lifecycle: {@link #close()} releases all libav* resources.
 * Use with try-with-resources.
 */
public final class VideoFileSource implements VideoSource, AutoCloseable {
    /**
     * Lifetime arena for native allocations.
     */
    private final Arena arena;

    /**
     * libavformat demuxer context.
     */
    private final MemorySegment formatCtx;

    /**
     * libavcodec decoder context.
     */
    private final MemorySegment codecCtx;

    /**
     * libswscale converter — input pixfmt → AV_PIX_FMT_YUV420P.
     */
    private MemorySegment swsCtx;

    /**
     * Reusable demuxer packet.
     */
    private final MemorySegment packet;

    /**
     * Reusable decoder output frame.
     */
    private final MemorySegment frame;

    /**
     * Index of the picked video stream.
     */
    private final int streamIndex;

    /**
     * Stream time base numerator — for PTS rescaling.
     */
    private final int timeBaseNum;

    /**
     * Stream time base denominator — for PTS rescaling.
     */
    private final int timeBaseDen;

    /**
     * Pre-converted output frames awaiting emission.
     */
    private final Deque<VideoFrame> ready = new ArrayDeque<>();

    /**
     * Whether the demuxer has hit EOF and the decoder has been
     * flushed.
     */
    private boolean drained;

    /**
     * The width of the most recently configured swscale context.
     */
    private int swsW;

    /**
     * The height of the most recently configured swscale context.
     */
    private int swsH;

    /**
     * The pixel format the swscale converter was built for. If a
     * later frame uses a different format, the converter is torn
     * down and rebuilt.
     */
    private int swsFmt;

    /**
     * Opens {@code path} and prepares the video decode pipeline.
     *
     * @param path the media file to open
     * @throws NullPointerException  if {@code path} is null
     * @throws IllegalStateException if the file can't be opened
     *                               or has no video stream
     */
    public VideoFileSource(Path path) {
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
     * Returns the next decoded I420 frame, or {@code null} on
     * end-of-stream.
     *
     * @return the next frame, or {@code null}
     */
    @Override
    public VideoFrame next() {
        while (ready.isEmpty() && !drained) {
            pump();
        }
        return ready.pollFirst();
    }

    /**
     * Drives one round of demux → decode → convert.
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
     * Pulls every available frame out of the decoder and converts
     * each to I420.
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
     * Runs libswscale to produce an I420 byte array for the
     * current {@link #frame}, then wraps it in a {@link VideoFrame}
     * with PTS rescaled to milliseconds.
     *
     * @return the converted frame
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
     * (Re)builds the libswscale context when dimensions or pixel
     * format change between frames.
     *
     * @param w   target width
     * @param h   target height
     * @param fmt source pixel format
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
     * Releases all native resources.
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
     * Walks {@code formatCtx.streams[]} and returns the index of
     * the first video stream, or {@code -1}.
     *
     * @param formatCtx the demuxer context
     * @return the video stream index, or -1
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
     * @param formatCtx the demuxer context
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
