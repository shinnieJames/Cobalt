package com.github.auties00.cobalt.call.sink;

import com.github.auties00.cobalt.media.ffmpeg.AVCodecParameters;
import com.github.auties00.cobalt.media.ffmpeg.AVFormatContext;
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
import java.util.Objects;

/**
 * Records a live call's encoded audio and video into a Matroska file without re-encoding.
 *
 * <p>This recorder muxes the call's already-encoded packets straight into an MKV container through
 * libavformat, so its per-frame cost is a byte copy plus container framing rather than a codec pass.
 * An application constructs it with the output path and the codec configuration of the call's
 * streams, passing {@code null} for either media to omit it for an audio-only or video-only recording,
 * then calls {@link #writeAudioPacket(byte[], long)} for each encoded audio payload and
 * {@link #writeVideoPacket(byte[], long, boolean)} for each complete encoded video frame, tagging
 * keyframes so the container index can seek to them. Callers supply presentation timestamps in
 * milliseconds and the recorder scales each to its stream's time base. Closing flushes any buffered
 * packets and writes the container trailer.
 *
 * <p>Matroska is used because it accepts both Opus audio and VP8 or H.264 video natively, whereas MP4
 * would require Annex B conversion and parameter-set extraction for H.264 and rejects VP8 outright.
 *
 * @apiNote Use this to archive a call to a single file with no transcoding overhead, feeding it the
 * encoded payloads the call produces. Pass complete video frames, not RTP fragments, and tag
 * keyframes accurately, otherwise the recording cannot seek. For an uncompressed audio-only capture
 * with no native dependency, prefer {@link WavFileSink}.
 * @implNote This implementation requires the FFmpeg native libraries; the constructor calls
 * {@link FFmpegLoader#ensureLoaded()} before touching any binding.
 */
public final class CallRecorder implements AutoCloseable {
    /**
     * Enumerates the audio codecs the recorder can mux.
     */
    public enum AudioCodec {
        /**
         * Identifies Opus, the WhatsApp call wire audio codec, accepted directly.
         */
        OPUS,
        /**
         * Identifies AAC, included for bridging non-call audio into a recording.
         */
        AAC
    }

    /**
     * Enumerates the video codecs the recorder can mux.
     */
    public enum VideoCodec {
        /**
         * Identifies VP8, the default WhatsApp call wire video codec.
         */
        VP8,
        /**
         * Identifies H.264, the alternate WhatsApp call wire video codec.
         */
        H264
    }

    /**
     * Holds the arena that owns every native allocation for the recording's lifetime.
     */
    private final Arena arena;

    /**
     * Holds the libavformat output context ({@code AVFormatContext*}).
     */
    private final MemorySegment formatCtx;

    /**
     * Holds the audio stream pointer ({@code AVStream*}), or {@code null} when no audio was
     * configured.
     */
    private final MemorySegment audioStream;

    /**
     * Holds the video stream pointer ({@code AVStream*}), or {@code null} when no video was
     * configured.
     */
    private final MemorySegment videoStream;

    /**
     * Holds the audio stream's index inside the container, or -1 when no audio was configured.
     */
    private final int audioStreamIndex;

    /**
     * Holds the video stream's index inside the container, or -1 when no video was configured.
     */
    private final int videoStreamIndex;

    /**
     * Holds the reusable native packet ({@code AVPacket*}) refilled on each write.
     */
    private final MemorySegment packet;

    /**
     * Records whether {@link #close()} has run.
     */
    private boolean closed;

    /**
     * Records whether the container header was written, which gates whether the trailer must be
     * written on close.
     */
    private boolean headerWritten;

    /**
     * Opens the given path as a Matroska output and configures the audio and video streams.
     *
     * <p>Allocates the output context, configures one stream per non-{@code null} codec, opens the
     * output file, and writes the container header so the recorder is ready to accept packets. Pass
     * {@code null} for a stream's codec to omit it; the video dimensions are ignored when no video
     * codec is given.
     *
     * @param path            the output file; never {@code null}
     * @param audio           the audio codec, or {@code null} to omit audio
     * @param audioSampleRate the audio sample rate in hertz, for example 48000 for call-wire Opus
     * @param audioChannels   the audio channel count, 1 for the call wire
     * @param video           the video codec, or {@code null} to omit video
     * @param videoWidth      the video width in pixels; ignored when {@code video} is {@code null}
     * @param videoHeight     the video height in pixels; ignored when {@code video} is {@code null}
     * @throws NullPointerException     if {@code path} is {@code null}
     * @throws IllegalArgumentException if both {@code audio} and {@code video} are {@code null}
     */
    public CallRecorder(Path path,
                        AudioCodec audio, int audioSampleRate, int audioChannels,
                        VideoCodec video, int videoWidth, int videoHeight) {
        Objects.requireNonNull(path, "path cannot be null");
        if (audio == null && video == null) {
            throw new IllegalArgumentException("at least one of audio or video must be configured");
        }
        FFmpegLoader.ensureLoaded();
        this.arena = Arena.ofShared();
        try {
            var ctxPtr = arena.allocate(ValueLayout.ADDRESS);
            var formatName = arena.allocateFrom("matroska");
            var url = arena.allocateFrom(path.toAbsolutePath().toString());
            FFmpegError.check("avformat_alloc_output_context2",
                    Ffmpeg.avformat_alloc_output_context2(ctxPtr, MemorySegment.NULL, formatName, url));
            this.formatCtx = ctxPtr.get(ValueLayout.ADDRESS, 0L)
                    .reinterpret(AVFormatContext.layout().byteSize());

            var aIdx = -1;
            MemorySegment aStream = null;
            if (audio != null) {
                aStream = configureAudioStream(formatCtx, arena, audio, audioSampleRate, audioChannels);
                aIdx = AVStream.index(aStream);
            }
            this.audioStream = aStream;
            this.audioStreamIndex = aIdx;

            var vIdx = -1;
            MemorySegment vStream = null;
            if (video != null) {
                vStream = configureVideoStream(formatCtx, video, videoWidth, videoHeight);
                vIdx = AVStream.index(vStream);
            }
            this.videoStream = vStream;
            this.videoStreamIndex = vIdx;

            var pbPtr = formatCtx.asSlice(AVFormatContext.pb$offset(),
                    ValueLayout.ADDRESS.byteSize());
            FFmpegError.check("avio_open2",
                    Ffmpeg.avio_open2(pbPtr, url, Ffmpeg.AVIO_FLAG_WRITE(),
                            MemorySegment.NULL, MemorySegment.NULL));

            FFmpegError.check("avformat_write_header",
                    Ffmpeg.avformat_write_header(formatCtx, MemorySegment.NULL));
            this.headerWritten = true;

            this.packet = FFmpegError.requireNonNull("av_packet_alloc", Ffmpeg.av_packet_alloc());
        } catch (RuntimeException e) {
            arena.close();
            throw e;
        }
    }

    /**
     * Appends one encoded audio payload to the recording.
     *
     * @param payload the encoded payload bytes, for example one Opus packet; never {@code null}
     * @param ptsMs   the presentation timestamp in milliseconds
     * @throws NullPointerException  if {@code payload} is {@code null}
     * @throws IllegalStateException if no audio stream was configured, or if the recorder is closed,
     *                               or if the underlying write fails
     */
    public synchronized void writeAudioPacket(byte[] payload, long ptsMs) {
        Objects.requireNonNull(payload, "payload cannot be null");
        if (audioStreamIndex < 0) {
            throw new IllegalStateException("audio stream not configured");
        }
        writePacket(audioStream, audioStreamIndex, payload, ptsMs, true);
    }

    /**
     * Appends one encoded video payload to the recording.
     *
     * @param payload  the encoded video payload bytes, a complete frame rather than an RTP fragment;
     *                 never {@code null}
     * @param ptsMs    the presentation timestamp in milliseconds
     * @param keyframe whether this packet begins a keyframe
     * @throws NullPointerException  if {@code payload} is {@code null}
     * @throws IllegalStateException if no video stream was configured, or if the recorder is closed,
     *                               or if the underlying write fails
     */
    public synchronized void writeVideoPacket(byte[] payload, long ptsMs, boolean keyframe) {
        Objects.requireNonNull(payload, "payload cannot be null");
        if (videoStreamIndex < 0) {
            throw new IllegalStateException("video stream not configured");
        }
        writePacket(videoStream, videoStreamIndex, payload, ptsMs, keyframe);
    }

    /**
     * Copies one payload into a native buffer, fills the packet, and writes it to the container.
     *
     * <p>Copies the payload into a freshly allocated native buffer, sets the packet's data, size, and
     * stream index, scales the millisecond timestamp into the stream's time base for both the
     * presentation and decode timestamps, marks the keyframe flag, and forwards the packet to
     * interleaved writing so streams stay in container order.
     *
     * @param stream      the target stream pointer ({@code AVStream*})
     * @param streamIndex the stream's container index
     * @param payload     the encoded payload
     * @param ptsMs       the presentation timestamp in milliseconds
     * @param keyframe    whether this packet begins a keyframe
     * @throws IllegalStateException if the recorder is closed or the underlying write fails
     */
    private void writePacket(MemorySegment stream, int streamIndex,
                             byte[] payload, long ptsMs, boolean keyframe) {
        if (closed) {
            throw new IllegalStateException("CallRecorder already closed");
        }
        try (var local = Arena.ofConfined()) {
            var nativeBuf = local.allocate(payload.length);
            MemorySegment.copy(payload, 0, nativeBuf, ValueLayout.JAVA_BYTE, 0, payload.length);

            Ffmpeg.av_packet_unref(packet);
            AVPacket.data(packet, nativeBuf);
            AVPacket.size(packet, payload.length);
            AVPacket.stream_index(packet, streamIndex);
            var tb = AVStream.time_base(stream);
            var num = AVRational.num(tb);
            var den = AVRational.den(tb);
            var ts = num == 0 ? ptsMs : (ptsMs * den) / (1000L * num);
            AVPacket.pts(packet, ts);
            AVPacket.dts(packet, ts);
            AVPacket.duration(packet, 0L);
            AVPacket.flags(packet, keyframe ? 1 : 0);

            var wrote = Ffmpeg.av_interleaved_write_frame(formatCtx, packet);
            if (wrote < 0) {
                throw new IllegalStateException("av_interleaved_write_frame failed: "
                        + FFmpegError.describe(wrote));
            }
            Ffmpeg.av_packet_unref(packet);
        }
    }

    /**
     * Flushes buffered packets, writes the container trailer, and releases native resources.
     *
     * <p>Writes the trailer only when the header was written, closes the output file, frees the
     * format context and the reusable packet, and closes the owning arena. A trailer write that fails
     * is logged rather than thrown so the remaining resources are still released. Calling this more
     * than once does nothing after the first time, so it is idempotent.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try (arena) {
            if (headerWritten && formatCtx != null && formatCtx.address() != 0L) {
                var trailer = Ffmpeg.av_write_trailer(formatCtx);
                if (trailer < 0) {
                    System.getLogger(CallRecorder.class.getName())
                            .log(System.Logger.Level.WARNING,
                                    "av_write_trailer failed: " + FFmpegError.describe(trailer));
                }
            }
            if (formatCtx != null && formatCtx.address() != 0L) {
                var pbPtr = formatCtx.asSlice(AVFormatContext.pb$offset(),
                        ValueLayout.ADDRESS.byteSize());
                Ffmpeg.avio_closep(pbPtr);
                Ffmpeg.avformat_free_context(formatCtx);
            }
            if (packet != null && packet.address() != 0L) {
                try (var local = Arena.ofConfined()) {
                    var pp = local.allocate(ValueLayout.ADDRESS);
                    pp.set(ValueLayout.ADDRESS, 0L, packet);
                    Ffmpeg.av_packet_free(pp);
                }
            }
        }
    }

    /**
     * Adds and configures an audio stream on the output context.
     *
     * <p>Allocates a new stream, sets its codec parameters to the requested codec with the given
     * sample rate and a default channel layout for the channel count, and sets the stream time base
     * to milliseconds so callers can pass millisecond timestamps directly.
     *
     * @param formatCtx the output context ({@code AVFormatContext*})
     * @param arena     the lifetime arena
     * @param codec     the audio codec to configure
     * @param rate      the sample rate in hertz
     * @param channels  the channel count
     * @return the configured stream pointer ({@code AVStream*})
     */
    private static MemorySegment configureAudioStream(MemorySegment formatCtx, Arena arena,
                                                      AudioCodec codec, int rate, int channels) {
        var stream = FFmpegError.requireNonNull(
                "avformat_new_stream(audio)",
                Ffmpeg.avformat_new_stream(formatCtx, MemorySegment.NULL));
        var params = AVStream.codecpar(stream);
        AVCodecParameters.codec_type(params, Ffmpeg.AVMEDIA_TYPE_AUDIO());
        AVCodecParameters.codec_id(params, switch (codec) {
            case OPUS -> Ffmpeg.AV_CODEC_ID_OPUS();
            case AAC -> Ffmpeg.AV_CODEC_ID_AAC();
        });
        AVCodecParameters.sample_rate(params, rate);
        var layout = AVCodecParameters.ch_layout(params);
        Ffmpeg.av_channel_layout_default(layout, channels);
        var tb = AVStream.time_base(stream);
        AVRational.num(tb, 1);
        AVRational.den(tb, 1000);
        return stream;
    }

    /**
     * Adds and configures a video stream on the output context.
     *
     * <p>Allocates a new stream, sets its codec parameters to the requested codec with the given
     * dimensions and a planar 4:2:0 pixel format, and sets the stream time base to milliseconds so
     * callers can pass millisecond timestamps directly.
     *
     * @param formatCtx the output context ({@code AVFormatContext*})
     * @param codec     the video codec to configure
     * @param w         the width in pixels
     * @param h         the height in pixels
     * @return the configured stream pointer ({@code AVStream*})
     */
    private static MemorySegment configureVideoStream(MemorySegment formatCtx,
                                                       VideoCodec codec, int w, int h) {
        var stream = FFmpegError.requireNonNull(
                "avformat_new_stream(video)",
                Ffmpeg.avformat_new_stream(formatCtx, MemorySegment.NULL));
        var params = AVStream.codecpar(stream);
        AVCodecParameters.codec_type(params, Ffmpeg.AVMEDIA_TYPE_VIDEO());
        AVCodecParameters.codec_id(params, switch (codec) {
            case VP8 -> Ffmpeg.AV_CODEC_ID_VP8();
            case H264 -> Ffmpeg.AV_CODEC_ID_H264();
        });
        AVCodecParameters.width(params, w);
        AVCodecParameters.height(params, h);
        AVCodecParameters.format(params, Ffmpeg.AV_PIX_FMT_YUV420P());
        var tb = AVStream.time_base(stream);
        AVRational.num(tb, 1);
        AVRational.den(tb, 1000);
        return stream;
    }
}
