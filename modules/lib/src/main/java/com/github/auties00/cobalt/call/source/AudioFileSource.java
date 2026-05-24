package com.github.auties00.cobalt.call.source;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSource;
import com.github.auties00.cobalt.media.ffmpeg.AVChannelLayout;
import com.github.auties00.cobalt.media.ffmpeg.AVCodecContext;
import com.github.auties00.cobalt.media.ffmpeg.AVCodecParameters;
import com.github.auties00.cobalt.media.ffmpeg.AVFormatContext;
import com.github.auties00.cobalt.media.ffmpeg.AVFrame;
import com.github.auties00.cobalt.media.ffmpeg.AVPacket;
import com.github.auties00.cobalt.media.ffmpeg.AVStream;
import com.github.auties00.cobalt.media.ffmpeg.Ffmpeg;
import com.github.auties00.cobalt.media.ffmpeg.FFmpegError;
import com.github.auties00.cobalt.media.ffmpeg.FFmpegLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Plays a media file as an {@link AudioSource}. Opens the file,
 * finds the first audio stream, decodes it, and converts each
 * decoded frame to 16 kHz mono signed-16-bit PCM so the output
 * matches the call wire format.
 *
 * <p>Any container / codec the toolkit's FFmpeg build enables
 * decoders for is supported — by default that's WAV, FLAC, MP3,
 * AAC, Opus, Vorbis, MP4, MKV, OGG.
 *
 * <p>Output cadence: each {@link #next()} returns a 10 ms frame
 * (160 samples at 16 kHz). When the file's last decoded sample
 * doesn't reach a 10 ms boundary, the trailing partial frame is
 * dropped and the source signals end-of-stream by returning
 * {@code null}.
 *
 * <p>Lifecycle: {@link #close()} releases the demuxer / decoder /
 * resampler — call it (or use {@code try-with-resources}) when
 * the source is no longer needed.
 */
public final class AudioFileSource implements AudioSource, AutoCloseable {
    /**
     * Output sample rate the call layer expects.
     */
    private static final int OUT_SAMPLE_RATE = 16_000;

    /**
     * Output samples per frame — 160 = 10 ms at 16 kHz.
     */
    private static final int OUT_FRAME_SAMPLES = 160;

    /**
     * Resource arena owning every libav* allocation. Closed when
     * the source closes.
     */
    private final Arena arena;

    /**
     * libavformat demuxer context — owns the input file handle.
     */
    private final MemorySegment formatCtx;

    /**
     * libavcodec decoder context — owns the codec's internal
     * buffers.
     */
    private final MemorySegment codecCtx;

    /**
     * libswresample context — converts the decoded format to
     * 16 kHz mono S16.
     */
    private final MemorySegment swrCtx;

    /**
     * Reusable {@code AVPacket} for the demuxer read loop.
     */
    private final MemorySegment packet;

    /**
     * Reusable {@code AVFrame} for the decoder output.
     */
    private final MemorySegment frame;

    /**
     * Index of the audio stream we picked.
     */
    private final int streamIndex;

    /**
     * Queue of decoded-and-resampled samples we haven't yet
     * emitted as a 10 ms frame.
     */
    private final Deque<short[]> readyFrames = new ArrayDeque<>();

    /**
     * The leftover samples from the last decode that didn't fit
     * a 10 ms frame. Carried into the next decode so we never
     * lose sub-frame audio.
     */
    private short[] leftover = new short[0];

    /**
     * Monotonic output pts in milliseconds — increments by 10
     * per emitted frame.
     */
    private long ptsMs;

    /**
     * Whether the demuxer has reached end-of-stream and the
     * decoder has been flushed.
     */
    private boolean drained;

    /**
     * Opens {@code path} and prepares the demuxer / decoder /
     * resampler chain.
     *
     * @param path the media file to open
     * @throws NullPointerException  if {@code path} is {@code null}
     * @throws IllegalStateException if the file can't be opened or
     *                               has no audio stream
     */
    public AudioFileSource(Path path) {
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

            this.streamIndex = pickAudioStream(formatCtx);
            if (streamIndex < 0) {
                throw new IllegalStateException("no audio stream in " + path);
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

            this.swrCtx = buildResampler(arena, codecCtx);
            this.packet = FFmpegError.requireNonNull("av_packet_alloc", Ffmpeg.av_packet_alloc());
            this.frame = FFmpegError.requireNonNull("av_frame_alloc", Ffmpeg.av_frame_alloc());
        } catch (RuntimeException e) {
            arena.close();
            throw e;
        }
    }

    /**
     * Returns the next decoded 10 ms frame, or {@code null} on
     * end-of-stream.
     *
     * @return the next frame, or {@code null} on EOS
     */
    @Override
    public AudioFrame next() {
        while (readyFrames.isEmpty() && !drained) {
            pump();
        }
        if (readyFrames.isEmpty()) {
            return null;
        }
        var pcm = readyFrames.pollFirst();
        var pts = ptsMs;
        ptsMs += 10;
        return new AudioFrame(pcm, pts);
    }

    /**
     * Drives one round of demux → decode → resample.
     */
    private void pump() {
        var read = Ffmpeg.av_read_frame(formatCtx, packet);
        if (read < 0) {
            Ffmpeg.avcodec_send_packet(codecCtx, MemorySegment.NULL);
            drainDecoder(true);
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
            drainDecoder(false);
        } finally {
            Ffmpeg.av_packet_unref(packet);
        }
    }

    /**
     * Pulls every available frame out of the decoder, resamples
     * it to 16 kHz mono S16, and queues it for emission.
     *
     * @param flush whether the decoder is in flush mode (post-EOF)
     */
    private void drainDecoder(boolean flush) {
        while (true) {
            var got = Ffmpeg.avcodec_receive_frame(codecCtx, frame);
            if (FFmpegError.isAgain(got) || (flush && FFmpegError.isEof(got))) {
                if (flush && FFmpegError.isEof(got)) {
                    flushResampler();
                }
                return;
            }
            if (got < 0) {
                throw new IllegalStateException("avcodec_receive_frame failed: "
                        + FFmpegError.describe(got));
            }
            try {
                resampleAndQueue();
            } finally {
                Ffmpeg.av_frame_unref(frame);
            }
        }
    }

    /**
     * Runs one libswresample conversion of the current
     * {@link #frame} into 16 kHz mono S16 and slices the output
     * into 10 ms ready frames.
     */
    private void resampleAndQueue() {
        var inSamples = AVFrame.nb_samples(frame);
        if (inSamples <= 0) {
            return;
        }
        var outCapacity = (int) (((long) inSamples * OUT_SAMPLE_RATE
                                  / Math.max(1, AVFrame.sample_rate(frame))) + 256);
        try (var local = Arena.ofConfined()) {
            var outBuf = local.allocate((long) outCapacity * Short.BYTES);
            var outPtrs = local.allocate(ValueLayout.ADDRESS);
            outPtrs.set(ValueLayout.ADDRESS, 0L, outBuf);
            var inPtrs = AVFrame.extended_data(frame);
            var produced = Ffmpeg.swr_convert(swrCtx, outPtrs, outCapacity, inPtrs, inSamples);
            if (produced < 0) {
                throw new IllegalStateException("swr_convert failed: " + FFmpegError.describe(produced));
            }
            queueResampled(outBuf, produced);
        }
    }

    /**
     * Drains any samples libswresample is still holding in its
     * internal buffer (called when the demuxer hits EOF).
     */
    private void flushResampler() {
        try (var local = Arena.ofConfined()) {
            var outCapacity = OUT_FRAME_SAMPLES * 4;
            var outBuf = local.allocate((long) outCapacity * Short.BYTES);
            var outPtrs = local.allocate(ValueLayout.ADDRESS);
            outPtrs.set(ValueLayout.ADDRESS, 0L, outBuf);
            var produced = Ffmpeg.swr_convert(swrCtx, outPtrs, outCapacity, MemorySegment.NULL, 0);
            if (produced < 0) {
                throw new IllegalStateException("swr_convert(flush) failed: "
                        + FFmpegError.describe(produced));
            }
            queueResampled(outBuf, produced);
        }
    }

    /**
     * Slices {@code produced} freshly-resampled samples (in
     * {@code outBuf}) into 10 ms chunks, prepending the
     * {@link #leftover} carry, and pushes the chunks into
     * {@link #readyFrames}.
     *
     * @param outBuf   the resampler output buffer
     * @param produced the number of samples produced
     */
    private void queueResampled(MemorySegment outBuf, int produced) {
        var total = leftover.length + produced;
        var combined = new short[total];
        System.arraycopy(leftover, 0, combined, 0, leftover.length);
        var nativeShorts = outBuf.asByteBuffer().order(ByteOrder.nativeOrder()).asShortBuffer();
        nativeShorts.get(combined, leftover.length, produced);

        var chunks = total / OUT_FRAME_SAMPLES;
        for (var i = 0; i < chunks; i++) {
            var chunk = new short[OUT_FRAME_SAMPLES];
            System.arraycopy(combined, i * OUT_FRAME_SAMPLES, chunk, 0, OUT_FRAME_SAMPLES);
            readyFrames.addLast(chunk);
        }
        var remainder = total - chunks * OUT_FRAME_SAMPLES;
        if (remainder > 0) {
            leftover = new short[remainder];
            System.arraycopy(combined, chunks * OUT_FRAME_SAMPLES, leftover, 0, remainder);
        } else {
            leftover = new short[0];
        }
    }

    /**
     * Releases every libav* resource allocated by the source.
     */
    @Override
    public void close() {
        try (arena) {
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
            if (swrCtx != null && swrCtx.address() != 0L) {
                try (var local = Arena.ofConfined()) {
                    var pp = local.allocate(ValueLayout.ADDRESS);
                    pp.set(ValueLayout.ADDRESS, 0L, swrCtx);
                    Ffmpeg.swr_free(pp);
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
     * the first audio stream, or {@code -1} when none exists.
     *
     * @param formatCtx the demuxer context
     * @return the audio stream index, or -1
     */
    private static int pickAudioStream(MemorySegment formatCtx) {
        var n = AVFormatContext.nb_streams(formatCtx);
        var streamsArr = AVFormatContext.streams(formatCtx)
                .reinterpret((long) n * ValueLayout.ADDRESS.byteSize());
        for (var i = 0; i < n; i++) {
            var stream = streamsArr.getAtIndex(ValueLayout.ADDRESS, i)
                    .reinterpret(AVStream.layout().byteSize());
            var params = AVStream.codecpar(stream);
            if (AVCodecParameters.codec_type(params) == Ffmpeg.AVMEDIA_TYPE_AUDIO()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the {@code AVStream*} at the given index inside
     * {@code formatCtx.streams[]}.
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

    /**
     * Builds a libswresample context that converts the decoder's
     * native channel layout / sample format / sample rate into
     * 16 kHz mono S16.
     *
     * @param arena    the lifetime arena
     * @param codecCtx the source codec context (decoder)
     * @return the initialized {@code SwrContext} pointer
     */
    private static MemorySegment buildResampler(Arena arena, MemorySegment codecCtx) {
        var swrPtr = arena.allocate(ValueLayout.ADDRESS);
        var monoLayout = arena.allocate(AVChannelLayout.layout());
        Ffmpeg.av_channel_layout_default(monoLayout, 1);
        var srcChLayout = AVCodecContext.ch_layout(codecCtx);
        var srcFmt = AVCodecContext.sample_fmt(codecCtx);
        var srcRate = AVCodecContext.sample_rate(codecCtx);

        FFmpegError.check("swr_alloc_set_opts2",
                Ffmpeg.swr_alloc_set_opts2(swrPtr,
                        monoLayout, Ffmpeg.AV_SAMPLE_FMT_S16(), OUT_SAMPLE_RATE,
                        srcChLayout, srcFmt, srcRate,
                        0, MemorySegment.NULL));
        var swr = swrPtr.get(ValueLayout.ADDRESS, 0L);
        FFmpegError.check("swr_init", Ffmpeg.swr_init(swr));
        return swr;
    }
}
