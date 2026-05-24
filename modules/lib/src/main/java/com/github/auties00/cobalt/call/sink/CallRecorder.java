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
 * Mux's a live call's encoded audio + video packets into an MKV
 * file via libavformat — no re-encoding. The recorder takes the
 * Opus and VP8 / H.264 RTP payloads exactly as the call layer
 * produces them and writes them to disk in container-correct
 * order, keeping recording overhead at "byte copy + framing".
 *
 * <p>Why MKV: it accepts both Opus and VP8 / H.264 natively
 * without timing or muxer-config gymnastics, where MP4 imposes
 * Annex B conversion / SPS-PPS extraction for H.264 and bans VP8
 * entirely.
 *
 * <p>API shape:
 * <ul>
 *   <li>Construct with the output path + the codec configuration
 *       of the call's streams. Pass {@code null} for either
 *       codec to omit it (audio-only / video-only recordings).</li>
 *   <li>Call {@link #writeAudioPacket} for every encoded audio
 *       payload as it arrives.</li>
 *   <li>Call {@link #writeVideoPacket} for every encoded video
 *       payload (full frame, not RTP fragment), tagging keyframes
 *       so the MKV index can seek to them.</li>
 *   <li>Close to flush + write the trailer.</li>
 * </ul>
 *
 * <p>Timestamps: callers pass PTS in milliseconds; the recorder
 * scales them to each stream's time base for libavformat.
 */
public final class CallRecorder implements AutoCloseable {
    /**
     * Audio codec identifiers the recorder knows how to mux. Opus
     * is what the call wire uses; we accept it directly. AAC is
     * here so apps that bridge a call to AAC content can also
     * record.
     */
    public enum AudioCodec {
        /**
         * Opus — the WhatsApp call wire codec.
         */
        OPUS,
        /**
         * AAC — included for non-call bridging scenarios.
         */
        AAC
    }

    /**
     * Video codec identifiers the recorder knows how to mux. VP8
     * is the Cobalt call default; H.264 is the alternative the
     * call also negotiates.
     */
    public enum VideoCodec {
        /**
         * VP8 — the WhatsApp call wire codec.
         */
        VP8,
        /**
         * H.264 — the alternate WhatsApp call wire codec.
         */
        H264
    }

    /**
     * Lifetime arena.
     */
    private final Arena arena;

    /**
     * libavformat output context.
     */
    private final MemorySegment formatCtx;

    /**
     * Audio stream pointer ({@code AVStream*}), or {@code null}
     * when no audio was configured.
     */
    private final MemorySegment audioStream;

    /**
     * Video stream pointer ({@code AVStream*}), or {@code null}
     * when no video was configured.
     */
    private final MemorySegment videoStream;

    /**
     * Audio stream index inside the container; -1 if no audio.
     */
    private final int audioStreamIndex;

    /**
     * Video stream index inside the container; -1 if no video.
     */
    private final int videoStreamIndex;

    /**
     * Reusable AVPacket — refilled per write.
     */
    private final MemorySegment packet;

    /**
     * Whether {@link #close} has been called.
     */
    private boolean closed;

    /**
     * Whether {@code avformat_write_header} ran successfully —
     * gates whether {@code av_write_trailer} is needed on close.
     */
    private boolean headerWritten;

    /**
     * Opens {@code path} as an MKV output and configures the
     * audio and video streams. Pass {@code null} for a stream's
     * codec to omit it.
     *
     * @param path            the output file
     * @param audio           the audio codec, or {@code null}
     * @param audioSampleRate audio sample rate (e.g. 48000 for
     *                        Opus on the call wire)
     * @param audioChannels   audio channel count (1 for the call
     *                        wire)
     * @param video           the video codec, or {@code null}
     * @param videoWidth      video width; ignored when
     *                        {@code video} is {@code null}
     * @param videoHeight     video height; ignored when
     *                        {@code video} is {@code null}
     * @throws NullPointerException if {@code path} is null
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
     * @param payload the encoded payload bytes (e.g. an Opus
     *                packet)
     * @param ptsMs   the presentation timestamp in milliseconds
     * @throws IllegalStateException if no audio stream was
     *                               configured
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
     * @param payload  the encoded video payload bytes (a complete
     *                 frame, not an RTP fragment)
     * @param ptsMs    the presentation timestamp in milliseconds
     * @param keyframe whether this packet starts a keyframe
     * @throws IllegalStateException if no video stream was
     *                               configured
     */
    public synchronized void writeVideoPacket(byte[] payload, long ptsMs, boolean keyframe) {
        Objects.requireNonNull(payload, "payload cannot be null");
        if (videoStreamIndex < 0) {
            throw new IllegalStateException("video stream not configured");
        }
        writePacket(videoStream, videoStreamIndex, payload, ptsMs, keyframe);
    }

    /**
     * Common write path — copies {@code payload} into a fresh
     * native buffer, fills the AVPacket fields, and forwards to
     * {@code av_interleaved_write_frame}.
     *
     * @param stream      the target {@code AVStream*}
     * @param streamIndex the stream's container index
     * @param payload     the encoded payload
     * @param ptsMs       the millisecond pts
     * @param keyframe    whether this is a keyframe
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
     * Flushes any buffered packets, writes the MKV trailer, and
     * closes the output file. Idempotent.
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
     * Adds an audio {@code AVStream} to {@code formatCtx},
     * configured for the given codec and rate / channel count.
     *
     * @param formatCtx the output context
     * @param arena     the lifetime arena
     * @param codec     which audio codec
     * @param rate      sample rate
     * @param channels  channel count
     * @return the stream pointer
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
     * Adds a video {@code AVStream} to {@code formatCtx},
     * configured for the given codec and dimensions.
     *
     * @param formatCtx the output context
     * @param codec     which video codec
     * @param w         width
     * @param h         height
     * @return the stream pointer
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
