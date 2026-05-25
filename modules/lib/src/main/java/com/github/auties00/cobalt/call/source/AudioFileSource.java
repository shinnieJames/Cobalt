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
 * Plays a media file as an {@link AudioSource} for transmission in a call.
 *
 * <p>Opens the file, picks its first audio stream, decodes it, and resamples each decoded frame to
 * 16 kHz mono signed 16-bit PCM so the output matches the call wire format. Any container or codec
 * the bundled FFmpeg build enables a decoder for is supported, which by default covers WAV, FLAC,
 * MP3, AAC, Opus, Vorbis, MP4, MKV, and OGG.
 *
 * <p>Each {@link #next()} returns one 160-sample, 10 ms frame. Sub-frame audio is never lost: the
 * leftover samples from a decode that does not land on a 10 ms boundary are carried into the next
 * decode. When the file ends, the trailing partial frame (if any) is dropped and {@link #next()}
 * returns {@code null} to signal end-of-stream.
 *
 * @apiNote Wire this source into a call to stream a file's audio to the remote participant. Always
 * close the source (it is {@link AutoCloseable}, so prefer {@code try-with-resources}) to release
 * the native demuxer, decoder, and resampler; otherwise their memory leaks for the life of the JVM.
 */
public final class AudioFileSource implements AudioSource, AutoCloseable {
    /**
     * Holds the output sample rate, in Hz, that the call layer expects.
     *
     * @implNote This implementation uses 16000, the rate WhatsApp's Opus call configuration runs at,
     * so the resampled output feeds the encoder directly.
     */
    private static final int OUT_SAMPLE_RATE = 16_000;

    /**
     * Holds the number of output samples per emitted frame.
     *
     * @implNote This implementation uses 160, which is 10 ms at {@link #OUT_SAMPLE_RATE}, the frame
     * cadence the call layer consumes.
     */
    private static final int OUT_FRAME_SAMPLES = 160;

    /**
     * Holds the arena owning every libav* allocation this source makes, closed when the source
     * closes.
     */
    private final Arena arena;

    /**
     * Holds the libavformat demuxer context pointer, which owns the input file handle.
     */
    private final MemorySegment formatCtx;

    /**
     * Holds the libavcodec decoder context pointer, which owns the codec's internal buffers.
     */
    private final MemorySegment codecCtx;

    /**
     * Holds the libswresample context pointer that converts the decoded format to 16 kHz mono signed
     * 16-bit PCM.
     */
    private final MemorySegment swrCtx;

    /**
     * Holds the reusable {@code AVPacket} pointer driven by the demuxer read loop.
     */
    private final MemorySegment packet;

    /**
     * Holds the reusable {@code AVFrame} pointer that the decoder writes into.
     */
    private final MemorySegment frame;

    /**
     * Holds the index of the audio stream chosen from the container.
     */
    private final int streamIndex;

    /**
     * Holds the queue of decoded-and-resampled 10 ms frames not yet emitted by {@link #next()}.
     */
    private final Deque<short[]> readyFrames = new ArrayDeque<>();

    /**
     * Holds the leftover samples from the last decode that did not fill a 10 ms frame.
     *
     * <p>Carried into the next decode and prepended before the new samples are sliced, so sub-frame
     * audio is never dropped mid-stream.
     */
    private short[] leftover = new short[0];

    /**
     * Holds the presentation timestamp, in milliseconds, of the next emitted frame, advanced by
     * {@link #OUT_FRAME_SAMPLES} worth of time (10 ms) per frame.
     */
    private long ptsMs;

    /**
     * Holds whether the demuxer has reached end-of-stream and the decoder has been flushed.
     */
    private boolean drained;

    /**
     * Opens the given media file and prepares the demuxer, decoder, and resampler chain.
     *
     * <p>Ensures the FFmpeg libraries are loaded, opens the file, picks its first audio stream,
     * opens a decoder for that stream's codec, and builds a resampler targeting 16 kHz mono signed
     * 16-bit PCM. If any step fails the arena is closed before the exception propagates, so a failed
     * construction leaks no native resources.
     *
     * @param path the media file to open
     * @throws NullPointerException  if {@code path} is {@code null}
     * @throws IllegalStateException if the file cannot be opened or has no audio stream
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
     * {@inheritDoc}
     *
     * <p>Drives the demux-decode-resample pipeline until a 10 ms frame is ready, then returns it
     * with the next presentation timestamp and advances the timestamp by 10 ms. Returns
     * {@code null} once the file is fully drained and no further frames remain.
     *
     * @return {@inheritDoc}
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
     * Drives one round of demux, decode, and resample.
     *
     * <p>Reads one packet; on end-of-input it flushes the decoder and marks the source drained.
     * Otherwise it feeds packets belonging to the chosen audio stream to the decoder and drains the
     * resulting frames.
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
     * Pulls every available frame out of the decoder, resamples each to 16 kHz mono signed 16-bit
     * PCM, and queues it for emission.
     *
     * <p>When in flush mode and the decoder signals end-of-output, the resampler's internal buffer
     * is flushed too so no tail samples are lost.
     *
     * @param flush whether the decoder is in flush mode (after end-of-input)
     * @throws IllegalStateException if the decoder reports a hard failure
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
     * Resamples the current {@link #frame} to 16 kHz mono signed 16-bit PCM and queues the output as
     * 10 ms frames.
     *
     * <p>Sizes the output buffer from the input sample count rescaled to the output rate plus a small
     * margin, runs one libswresample conversion, and slices the result into ready frames. Frames with
     * no samples are skipped.
     *
     * @throws IllegalStateException if the resample fails
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
     * Flushes any samples libswresample is still holding in its internal buffer and queues them.
     *
     * <p>Called when the demuxer hits end-of-input so the resampler's tail is not lost.
     *
     * @throws IllegalStateException if the flush conversion fails
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
     * Slices freshly-resampled samples into 10 ms frames and pushes them onto the ready queue.
     *
     * <p>Prepends the {@link #leftover} carry from the previous decode, splits the combined samples
     * into whole {@link #OUT_FRAME_SAMPLES}-length chunks, and stores any remainder as the new
     * leftover carry for the next call.
     *
     * @param outBuf   the resampler output buffer
     * @param produced the number of samples produced into {@code outBuf}
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
     *
     * <p>Frees the decoder output frame, the demuxer packet, the resampler, the decoder context, and
     * the demuxer context, then closes the owning arena. Guards each pointer against {@code null} and
     * a zero address, so the call is idempotent.
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
     * Returns the index of the first audio stream in the container, or {@code -1} when none exists.
     *
     * @param formatCtx the demuxer context
     * @return the audio stream index, or {@code -1} if the container has no audio stream
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
     * Builds a libswresample context that converts the decoder's native format to 16 kHz mono signed
     * 16-bit PCM.
     *
     * <p>Reads the decoder's channel layout, sample format, and sample rate as the source parameters
     * and targets a default mono layout at {@link #OUT_SAMPLE_RATE}, then initializes the context.
     *
     * @param arena    the lifetime arena that owns the allocations
     * @param codecCtx the source decoder context
     * @return the initialized {@code SwrContext} pointer
     * @throws IllegalStateException if the resampler cannot be allocated or initialized
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
