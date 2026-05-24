package com.github.auties00.cobalt.media.transcode.video;

import com.github.auties00.cobalt.exception.WhatsAppMediaException;
import com.github.auties00.cobalt.media.MediaPayload;
import com.github.auties00.cobalt.media.ffmpeg.AVChannelLayout;
import com.github.auties00.cobalt.media.ffmpeg.AVCodecContext;
import com.github.auties00.cobalt.media.ffmpeg.AVCodecParameters;
import com.github.auties00.cobalt.media.ffmpeg.AVFilterContext;
import com.github.auties00.cobalt.media.ffmpeg.AVFilterInOut;
import com.github.auties00.cobalt.media.ffmpeg.AVFormatContext;
import com.github.auties00.cobalt.media.ffmpeg.AVFrame;
import com.github.auties00.cobalt.media.ffmpeg.AVOutputFormat;
import com.github.auties00.cobalt.media.ffmpeg.AVPacket;
import com.github.auties00.cobalt.media.ffmpeg.AVRational;
import com.github.auties00.cobalt.media.ffmpeg.AVStream;
import com.github.auties00.cobalt.media.ffmpeg.FFmpegError;
import com.github.auties00.cobalt.media.ffmpeg.FFmpegLoader;
import com.github.auties00.cobalt.media.ffmpeg.Ffmpeg;
import com.github.auties00.cobalt.media.transcode.MediaTranscoderService;
import com.github.auties00.cobalt.media.transcode.avio.AvioReadBuffer;
import com.github.auties00.cobalt.media.transcode.avio.AvioWriteBuffer;
import com.github.auties00.cobalt.media.transcode.image.ImagePipeline;
import com.github.auties00.cobalt.media.transcode.image.JpegCleaner;
import com.github.auties00.cobalt.model.media.MediaProvider;
import com.github.auties00.cobalt.model.message.media.VideoMessage;
import com.github.auties00.cobalt.model.sync.action.setting.SettingsSyncAction;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Decodes a source video and re-encodes it as a WhatsApp-compatible MP4
 * with H.264 video and AAC audio, plus an in-band JPEG thumbnail.
 *
 * @apiNote
 * Drives the video branch of the upload transcoder. Targets H.264
 * baseline level 3.1 (so the result plays on every WhatsApp client
 * without falling back to software decode) and AAC stereo audio at
 * 48 kHz. The output MP4 is muxed with {@code +faststart} so the moov
 * atom lands at the head of the file and playback can begin before the
 * full download completes.
 *
 * @implNote
 * This implementation drives FFmpeg end-to-end: libavformat demux of both
 * the video and audio streams, libavfilter for the
 * {@code scale} chain on the video path, libswresample for the audio
 * resample, libopenh264 for H.264 encoding, the native FFmpeg AAC encoder,
 * and the libavformat MP4 muxer with {@code +faststart}. The thumbnail is
 * the first decoded video frame run through the same {@code mjpeg} encoder
 * {@link ImagePipeline} uses.
 */
public final class VideoPipeline {
    /**
     * Maximum vertical resolution for the {@code STANDARD} quality
     * preset.
     */
    private static final int MAX_HEIGHT_STANDARD = 720;

    /**
     * Video bitrate for the {@code STANDARD} quality preset in bits per
     * second.
     */
    private static final long VIDEO_BITRATE_STANDARD = 1_000_000;

    /**
     * Video bitrate for the {@code HD} quality preset in bits per second.
     */
    private static final long VIDEO_BITRATE_HD = 3_000_000;

    /**
     * Audio bitrate for the {@code STANDARD} quality preset in bits per
     * second.
     */
    private static final long AUDIO_BITRATE_STANDARD = 128_000;

    /**
     * Audio bitrate for the {@code HD} quality preset in bits per second.
     */
    private static final long AUDIO_BITRATE_HD = 192_000;

    /**
     * Audio sample rate of the encoded output in Hz.
     */
    private static final int AUDIO_SAMPLE_RATE = 48_000;

    /**
     * Audio channel count of the encoded output (stereo).
     */
    private static final int AUDIO_CHANNELS = 2;

    /**
     * Maximum edge in pixels for the video thumbnail.
     */
    private static final int VIDEO_THUMB_MAX_EDGE = 480;

    /**
     * MJPEG qscale for the thumbnail.
     */
    private static final int THUMB_QSCALE = 4;

    /**
     * {@code AVFMT_GLOBALHEADER} flag value from
     * {@code libavformat/avformat.h}.
     */
    private static final int AVFMT_GLOBALHEADER = 0x40;

    /**
     * AAC frame size in samples; the native FFmpeg AAC encoder uses
     * {@code 1024}.
     */
    private static final int DEFAULT_AAC_FRAME_SAMPLES = 1024;

    /**
     * Constructs the pipeline; the parent
     * {@link MediaTranscoderService} owns the single instance.
     */
    public VideoPipeline() {
    }

    /**
     * Transcodes the source video, applies codec-derived metadata to
     * {@code provider}, and returns the encoded payload stream.
     *
     * @apiNote
     * Mutates the provider in place: when {@code provider} is a
     * {@link VideoMessage} the {@code mimetype}, {@code mediaSize},
     * {@code width}, {@code height}, {@code seconds}, and
     * {@code jpegThumbnail} fields are populated; every other
     * {@link MediaProvider} variant receives only the common
     * {@code mediaSize} update.
     *
     * @param provider the upload target; codec-derived fields are applied
     *                 to this instance
     * @param source   the raw video channel; not closed by this method
     * @param quality  the quality preset; selects video and audio bitrate
     * @return the encoded MP4 payload
     * @throws WhatsAppMediaException.Processing if any stage of the
     *         demux/decode/encode/mux pipeline fails
     */
    public MediaPayload run(MediaProvider provider, SeekableByteChannel source,
                            SettingsSyncAction.MediaQualitySetting quality)
            throws WhatsAppMediaException.Processing {
        FFmpegLoader.ensureLoaded();
        var hd = quality == SettingsSyncAction.MediaQualitySetting.HD;
        var videoBitrate = hd ? VIDEO_BITRATE_HD : VIDEO_BITRATE_STANDARD;
        var audioBitrate = hd ? AUDIO_BITRATE_HD : AUDIO_BITRATE_STANDARD;
        try (var arena = Arena.ofShared()) {
            return new Run(arena, source, hd, videoBitrate, audioBitrate).execute(provider);
        }
    }

    /**
     * One-shot state for a single transcode invocation. Holds every
     * libav* resource that needs cleanup on completion or failure.
     *
     * @apiNote
     * Lives only for the duration of {@link #execute()}. The shared
     * {@link Arena} is owned by the caller; this run owns every FFmpeg
     * pointer it allocates and releases them in {@link #cleanup()} on
     * every exit path.
     */
    private static final class Run {
        /**
         * Shared arena for AVIO bridges and transient FFmpeg arguments.
         */
        final Arena arena;

        /**
         * Source channel.
         */
        final SeekableByteChannel sourceChannel;

        /**
         * Whether the {@code HD} quality preset is in effect.
         */
        final boolean hd;

        /**
         * Target H.264 bitrate in bits per second.
         */
        final long videoBitrate;

        /**
         * Target AAC bitrate in bits per second.
         */
        final long audioBitrate;

        /**
         * Demuxer AVIO bridge.
         */
        AvioReadBuffer inputBridge;

        /**
         * Muxer AVIO bridge.
         */
        AvioWriteBuffer.FileSystem outputBridge;

        /**
         * Open input demuxer.
         */
        MemorySegment inFmtCtx = MemorySegment.NULL;

        /**
         * Open output muxer.
         */
        MemorySegment outFmtCtx = MemorySegment.NULL;

        /**
         * Open video decoder.
         */
        MemorySegment videoDecCtx = MemorySegment.NULL;

        /**
         * Open audio decoder (or {@code NULL} when the source has no
         * audio stream).
         */
        MemorySegment audioDecCtx = MemorySegment.NULL;

        /**
         * Open H.264 encoder.
         */
        MemorySegment videoEncCtx = MemorySegment.NULL;

        /**
         * Open AAC encoder (or {@code NULL} when the source has no audio).
         */
        MemorySegment audioEncCtx = MemorySegment.NULL;

        /**
         * Scale filter graph.
         */
        MemorySegment filterGraph = MemorySegment.NULL;

        /**
         * Buffer source filter context inside {@link #filterGraph}.
         */
        MemorySegment filterSrcCtx = MemorySegment.NULL;

        /**
         * Buffer sink filter context inside {@link #filterGraph}.
         */
        MemorySegment filterSinkCtx = MemorySegment.NULL;

        /**
         * Audio resampler.
         */
        MemorySegment swr = MemorySegment.NULL;

        /**
         * Reusable demuxer packet.
         */
        MemorySegment demuxPacket = MemorySegment.NULL;

        /**
         * Reusable decoder output frame.
         */
        MemorySegment decFrame = MemorySegment.NULL;

        /**
         * Reusable filter graph output frame.
         */
        MemorySegment filterFrame = MemorySegment.NULL;

        /**
         * Reusable encoder input frame (audio path).
         */
        MemorySegment audioEncFrame = MemorySegment.NULL;

        /**
         * Reusable encoder output packet.
         */
        MemorySegment encPacket = MemorySegment.NULL;

        /**
         * Index of the picked video stream in the input.
         */
        int videoStreamIndex = -1;

        /**
         * Index of the picked audio stream in the input, or {@code -1}
         * when none.
         */
        int audioStreamIndex = -1;

        /**
         * Output stream index for the muxed video stream.
         */
        int videoOutIndex = -1;

        /**
         * Output stream index for the muxed audio stream, or {@code -1}
         * when no audio is present.
         */
        int audioOutIndex = -1;

        /**
         * The first decoded and scaled video frame, retained for the
         * thumbnail pass.
         */
        MemorySegment thumbnailFrame = MemorySegment.NULL;

        /**
         * Encoded width in pixels.
         */
        int encodedWidth;

        /**
         * Encoded height in pixels.
         */
        int encodedHeight;

        /**
         * Encoder PTS for the video stream.
         */
        long videoPts;

        /**
         * Encoder PTS for the audio stream.
         */
        long audioPts;

        /**
         * Source video frame rate numerator (defaulted to 30 when the
         * source is malformed).
         */
        int frameRateNum = 30;

        /**
         * Source video frame rate denominator.
         */
        int frameRateDen = 1;

        /**
         * AAC frame size; resolved from the encoder after open.
         */
        int aacFrameSize = DEFAULT_AAC_FRAME_SAMPLES;

        /**
         * Constructs the run state.
         *
         * @param arena         the shared arena
         * @param sourceChannel the source channel
         * @param hd            whether the HD preset is in effect
         * @param videoBitrate  the target H.264 bitrate
         * @param audioBitrate  the target AAC bitrate
         */
        Run(Arena arena, SeekableByteChannel sourceChannel, boolean hd,
            long videoBitrate, long audioBitrate) {
            this.arena = arena;
            this.sourceChannel = sourceChannel;
            this.hd = hd;
            this.videoBitrate = videoBitrate;
            this.audioBitrate = audioBitrate;
        }

        /**
         * Runs the full transcode, applies the codec-derived metadata to
         * {@code provider}, and returns the encoded payload stream.
         *
         * @param provider the upload target that receives the metadata
         *                 updates
         * @return the encoded MP4 payload
         * @throws WhatsAppMediaException.Processing if any stage fails
         */
        MediaPayload execute(MediaProvider provider) throws WhatsAppMediaException.Processing {
            var success = false;
            Path outputPath = null;
            try {
                openInput();
                openOutput();
                openVideoEncoder();
                if (audioStreamIndex >= 0) {
                    openAudioEncoder();
                }
                buildFilterGraph();
                if (audioStreamIndex >= 0) {
                    openResampler();
                }
                writeHeader();
                transcodeLoop();
                flushEncoders();
                FFmpegError.check("av_write_trailer", Ffmpeg.av_write_trailer(outFmtCtx));
                var durationSeconds = Math.max(1, computeDurationSeconds());
                try {
                    outputPath = outputBridge.release();
                } catch (IOException e) {
                    throw new WhatsAppMediaException.Processing("failed to flush video output", e);
                }
                var outputLength = 0L;
                try {
                    outputLength = Files.size(outputPath);
                } catch (IOException e) {
                    throw new WhatsAppMediaException.Processing("failed to size video output", e);
                }
                var thumbnail = encodeThumbnail();
                provider.setMediaSize(outputLength);
                if (provider instanceof VideoMessage video) {
                    video.setMimetype("video/mp4");
                    video.setWidth(encodedWidth);
                    video.setHeight(encodedHeight);
                    video.setSeconds(durationSeconds);
                    if (thumbnail != null) {
                        video.setJpegThumbnail(thumbnail);
                    }
                }
                success = true;
                return new MediaPayload.OfPath(outputPath, outputLength, true);
            } finally {
                if (!success && outputPath != null) {
                    try {
                        Files.deleteIfExists(outputPath);
                    } catch (IOException ignored) {
                    }
                }
                cleanup();
            }
        }

        /**
         * Opens the input demuxer and picks the first video and audio
         * streams.
         */
        void openInput() throws WhatsAppMediaException.Processing {
            inputBridge = new AvioReadBuffer(arena, sourceChannel);
            inFmtCtx = FFmpegError.requireNonNull("avformat_alloc_context",
                    Ffmpeg.avformat_alloc_context());
            AVFormatContext.pb(inFmtCtx, inputBridge.ioContext());
            var formatPp = arena.allocate(ValueLayout.ADDRESS);
            formatPp.set(ValueLayout.ADDRESS, 0L, inFmtCtx);
            FFmpegError.check("avformat_open_input",
                    Ffmpeg.avformat_open_input(formatPp, MemorySegment.NULL,
                            MemorySegment.NULL, MemorySegment.NULL));
            inFmtCtx = formatPp.get(ValueLayout.ADDRESS, 0L)
                    .reinterpret(AVFormatContext.layout().byteSize());
            FFmpegError.check("avformat_find_stream_info",
                    Ffmpeg.avformat_find_stream_info(inFmtCtx, MemorySegment.NULL));
            videoStreamIndex = pickStream(inFmtCtx, Ffmpeg.AVMEDIA_TYPE_VIDEO());
            if (videoStreamIndex < 0) {
                throw new WhatsAppMediaException.Processing("no video stream in video source");
            }
            audioStreamIndex = pickStream(inFmtCtx, Ffmpeg.AVMEDIA_TYPE_AUDIO());
            videoDecCtx = openDecoder(streamPointer(inFmtCtx, videoStreamIndex));
            var videoStream = streamPointer(inFmtCtx, videoStreamIndex);
            var avgFr = AVStream.avg_frame_rate(videoStream);
            var num = AVRational.num(avgFr);
            var den = AVRational.den(avgFr);
            if (num > 0 && den > 0) {
                frameRateNum = num;
                frameRateDen = den;
            }
            if (audioStreamIndex >= 0) {
                audioDecCtx = openDecoder(streamPointer(inFmtCtx, audioStreamIndex));
            }
        }

        /**
         * Opens a libavcodec decoder for the given stream.
         *
         * @param stream the input stream pointer
         * @return the open decoder context
         */
        MemorySegment openDecoder(MemorySegment stream) throws WhatsAppMediaException.Processing {
            var params = AVStream.codecpar(stream);
            var codecId = AVCodecParameters.codec_id(params);
            var codec = FFmpegError.requireNonNull("avcodec_find_decoder(" + codecId + ")",
                    Ffmpeg.avcodec_find_decoder(codecId));
            var ctx = FFmpegError.requireNonNull("avcodec_alloc_context3",
                    Ffmpeg.avcodec_alloc_context3(codec));
            FFmpegError.check("avcodec_parameters_to_context",
                    Ffmpeg.avcodec_parameters_to_context(ctx, params));
            FFmpegError.check("avcodec_open2",
                    Ffmpeg.avcodec_open2(ctx, codec, MemorySegment.NULL));
            return ctx;
        }

        /**
         * Allocates the MP4 output context with {@code +faststart} set.
         */
        void openOutput() throws WhatsAppMediaException.Processing {
            try {
                outputBridge = AvioWriteBuffer.ofFileSystem(arena);
            } catch (IOException e) {
                throw new WhatsAppMediaException.Processing("failed to open video output", e);
            }
            var fmtCtxPp = arena.allocate(ValueLayout.ADDRESS);
            FFmpegError.check("avformat_alloc_output_context2(mp4)",
                    Ffmpeg.avformat_alloc_output_context2(fmtCtxPp, MemorySegment.NULL,
                            arena.allocateFrom("mp4"), MemorySegment.NULL));
            outFmtCtx = fmtCtxPp.get(ValueLayout.ADDRESS, 0L)
                    .reinterpret(AVFormatContext.layout().byteSize());
            AVFormatContext.pb(outFmtCtx, outputBridge.ioContext());
            var priv = AVFormatContext.priv_data(outFmtCtx);
            if (priv != null && priv != MemorySegment.NULL) {
                FFmpegError.check("av_opt_set(movflags=+faststart)",
                        Ffmpeg.av_opt_set(priv,
                                arena.allocateFrom("movflags"),
                                arena.allocateFrom("+faststart"),
                                Ffmpeg.AV_OPT_SEARCH_CHILDREN()));
            }
        }

        /**
         * Configures and opens the H.264 encoder via libopenh264.
         */
        void openVideoEncoder() throws WhatsAppMediaException.Processing {
            var srcW = AVCodecContext.width(videoDecCtx);
            var srcH = AVCodecContext.height(videoDecCtx);
            if (hd) {
                encodedWidth = roundEven(srcW);
                encodedHeight = roundEven(srcH);
            } else if (srcH > MAX_HEIGHT_STANDARD) {
                encodedHeight = MAX_HEIGHT_STANDARD;
                encodedWidth = roundEven((int) Math.round((double) srcW * MAX_HEIGHT_STANDARD / srcH));
            } else {
                encodedHeight = roundEven(srcH);
                encodedWidth = roundEven(srcW);
            }
            var encoder = FFmpegError.requireNonNull("avcodec_find_encoder_by_name(libopenh264)",
                    Ffmpeg.avcodec_find_encoder_by_name(arena.allocateFrom("libopenh264")));
            videoEncCtx = FFmpegError.requireNonNull("avcodec_alloc_context3(h264)",
                    Ffmpeg.avcodec_alloc_context3(encoder));
            AVCodecContext.width(videoEncCtx, encodedWidth);
            AVCodecContext.height(videoEncCtx, encodedHeight);
            AVCodecContext.pix_fmt(videoEncCtx, Ffmpeg.AV_PIX_FMT_YUV420P());
            AVCodecContext.bit_rate(videoEncCtx, videoBitrate);
            var tb = arena.allocate(8);
            tb.set(ValueLayout.JAVA_INT, 0L, frameRateDen);
            tb.set(ValueLayout.JAVA_INT, 4L, frameRateNum);
            AVCodecContext.time_base(videoEncCtx, tb);
            var fr = arena.allocate(8);
            fr.set(ValueLayout.JAVA_INT, 0L, frameRateNum);
            fr.set(ValueLayout.JAVA_INT, 4L, frameRateDen);
            AVCodecContext.framerate(videoEncCtx, fr);
            if ((AVOutputFormat.flags(AVFormatContext.oformat(outFmtCtx)) & AVFMT_GLOBALHEADER) != 0) {
                AVCodecContext.flags(videoEncCtx,
                        AVCodecContext.flags(videoEncCtx) | Ffmpeg.AV_CODEC_FLAG_GLOBAL_HEADER());
            }
            FFmpegError.check("av_opt_set(profile=baseline)",
                    Ffmpeg.av_opt_set(videoEncCtx,
                            arena.allocateFrom("profile"),
                            arena.allocateFrom("baseline"),
                            Ffmpeg.AV_OPT_SEARCH_CHILDREN()));
            FFmpegError.check("av_opt_set(level=3.1)",
                    Ffmpeg.av_opt_set(videoEncCtx,
                            arena.allocateFrom("level"),
                            arena.allocateFrom("3.1"),
                            Ffmpeg.AV_OPT_SEARCH_CHILDREN()));
            FFmpegError.check("avcodec_open2(libopenh264)",
                    Ffmpeg.avcodec_open2(videoEncCtx, encoder, MemorySegment.NULL));
            var stream = FFmpegError.requireNonNull("avformat_new_stream(video)",
                    Ffmpeg.avformat_new_stream(outFmtCtx, encoder));
            FFmpegError.check("avcodec_parameters_from_context(video)",
                    Ffmpeg.avcodec_parameters_from_context(AVStream.codecpar(stream), videoEncCtx));
            AVStream.time_base(stream, tb);
            videoOutIndex = AVStream.index(stream);
        }

        /**
         * Configures and opens the native FFmpeg AAC encoder.
         */
        void openAudioEncoder() throws WhatsAppMediaException.Processing {
            var encoder = FFmpegError.requireNonNull("avcodec_find_encoder(AAC)",
                    Ffmpeg.avcodec_find_encoder(Ffmpeg.AV_CODEC_ID_AAC()));
            audioEncCtx = FFmpegError.requireNonNull("avcodec_alloc_context3(aac)",
                    Ffmpeg.avcodec_alloc_context3(encoder));
            AVCodecContext.sample_rate(audioEncCtx, AUDIO_SAMPLE_RATE);
            AVCodecContext.sample_fmt(audioEncCtx, Ffmpeg.AV_SAMPLE_FMT_FLTP());
            AVCodecContext.bit_rate(audioEncCtx, audioBitrate);
            var encLayout = AVChannelLayout.allocate(arena);
            Ffmpeg.av_channel_layout_default(encLayout, AUDIO_CHANNELS);
            Ffmpeg.av_channel_layout_copy(AVCodecContext.ch_layout(audioEncCtx), encLayout);
            var tb = arena.allocate(8);
            tb.set(ValueLayout.JAVA_INT, 0L, 1);
            tb.set(ValueLayout.JAVA_INT, 4L, AUDIO_SAMPLE_RATE);
            AVCodecContext.time_base(audioEncCtx, tb);
            if ((AVOutputFormat.flags(AVFormatContext.oformat(outFmtCtx)) & AVFMT_GLOBALHEADER) != 0) {
                AVCodecContext.flags(audioEncCtx,
                        AVCodecContext.flags(audioEncCtx) | Ffmpeg.AV_CODEC_FLAG_GLOBAL_HEADER());
            }
            FFmpegError.check("avcodec_open2(aac)",
                    Ffmpeg.avcodec_open2(audioEncCtx, encoder, MemorySegment.NULL));
            var fs = AVCodecContext.frame_size(audioEncCtx);
            if (fs > 0) {
                aacFrameSize = fs;
            }
            var stream = FFmpegError.requireNonNull("avformat_new_stream(audio)",
                    Ffmpeg.avformat_new_stream(outFmtCtx, encoder));
            FFmpegError.check("avcodec_parameters_from_context(audio)",
                    Ffmpeg.avcodec_parameters_from_context(AVStream.codecpar(stream), audioEncCtx));
            AVStream.time_base(stream, tb);
            audioOutIndex = AVStream.index(stream);
        }

        /**
         * Builds the scale filter graph driving every decoded video
         * frame to the encoded dimensions and {@code yuv420p}.
         */
        void buildFilterGraph() throws WhatsAppMediaException.Processing {
            filterGraph = FFmpegError.requireNonNull("avfilter_graph_alloc",
                    Ffmpeg.avfilter_graph_alloc());
            var srcArgs = String.format(
                    "video_size=%dx%d:pix_fmt=%d:time_base=%d/%d:pixel_aspect=1/1",
                    AVCodecContext.width(videoDecCtx),
                    AVCodecContext.height(videoDecCtx),
                    AVCodecContext.pix_fmt(videoDecCtx),
                    frameRateDen, frameRateNum);
            var bufferFilter = FFmpegError.requireNonNull("avfilter_get_by_name(buffer)",
                    Ffmpeg.avfilter_get_by_name(arena.allocateFrom("buffer")));
            var sinkFilter = FFmpegError.requireNonNull("avfilter_get_by_name(buffersink)",
                    Ffmpeg.avfilter_get_by_name(arena.allocateFrom("buffersink")));
            var srcPp = arena.allocate(ValueLayout.ADDRESS);
            var sinkPp = arena.allocate(ValueLayout.ADDRESS);
            FFmpegError.check("avfilter_graph_create_filter(in)",
                    Ffmpeg.avfilter_graph_create_filter(srcPp, bufferFilter,
                            arena.allocateFrom("in"), arena.allocateFrom(srcArgs),
                            MemorySegment.NULL, filterGraph));
            FFmpegError.check("avfilter_graph_create_filter(out)",
                    Ffmpeg.avfilter_graph_create_filter(sinkPp, sinkFilter,
                            arena.allocateFrom("out"), MemorySegment.NULL,
                            MemorySegment.NULL, filterGraph));
            filterSrcCtx = srcPp.get(ValueLayout.ADDRESS, 0L)
                    .reinterpret(AVFilterContext.layout().byteSize());
            filterSinkCtx = sinkPp.get(ValueLayout.ADDRESS, 0L)
                    .reinterpret(AVFilterContext.layout().byteSize());
            var chain = String.format("scale=%d:%d:flags=bicubic,format=yuv420p",
                    encodedWidth, encodedHeight);
            var outputs = FFmpegError.requireNonNull("avfilter_inout_alloc(outputs)",
                    Ffmpeg.avfilter_inout_alloc());
            var inputs = FFmpegError.requireNonNull("avfilter_inout_alloc(inputs)",
                    Ffmpeg.avfilter_inout_alloc());
            AVFilterInOut.name(outputs, Ffmpeg.av_strdup(arena.allocateFrom("in")));
            AVFilterInOut.filter_ctx(outputs, filterSrcCtx);
            AVFilterInOut.pad_idx(outputs, 0);
            AVFilterInOut.next(outputs, MemorySegment.NULL);
            AVFilterInOut.name(inputs, Ffmpeg.av_strdup(arena.allocateFrom("out")));
            AVFilterInOut.filter_ctx(inputs, filterSinkCtx);
            AVFilterInOut.pad_idx(inputs, 0);
            AVFilterInOut.next(inputs, MemorySegment.NULL);
            var outputsPp = arena.allocate(ValueLayout.ADDRESS);
            var inputsPp = arena.allocate(ValueLayout.ADDRESS);
            outputsPp.set(ValueLayout.ADDRESS, 0L, outputs);
            inputsPp.set(ValueLayout.ADDRESS, 0L, inputs);
            try {
                FFmpegError.check("avfilter_graph_parse_ptr",
                        Ffmpeg.avfilter_graph_parse_ptr(filterGraph, arena.allocateFrom(chain),
                                inputsPp, outputsPp, MemorySegment.NULL));
                FFmpegError.check("avfilter_graph_config",
                        Ffmpeg.avfilter_graph_config(filterGraph, MemorySegment.NULL));
            } finally {
                Ffmpeg.avfilter_inout_free(inputsPp);
                Ffmpeg.avfilter_inout_free(outputsPp);
            }
        }

        /**
         * Sets up the audio resampler.
         */
        void openResampler() throws WhatsAppMediaException.Processing {
            var swrPp = arena.allocate(ValueLayout.ADDRESS);
            var outLayout = AVChannelLayout.allocate(arena);
            Ffmpeg.av_channel_layout_default(outLayout, AUDIO_CHANNELS);
            FFmpegError.check("swr_alloc_set_opts2",
                    Ffmpeg.swr_alloc_set_opts2(swrPp, outLayout,
                            Ffmpeg.AV_SAMPLE_FMT_FLTP(), AUDIO_SAMPLE_RATE,
                            AVCodecContext.ch_layout(audioDecCtx),
                            AVCodecContext.sample_fmt(audioDecCtx),
                            AVCodecContext.sample_rate(audioDecCtx),
                            0, MemorySegment.NULL));
            swr = FFmpegError.requireNonNull("swr_alloc_set_opts2 out",
                    swrPp.get(ValueLayout.ADDRESS, 0L));
            FFmpegError.check("swr_init", Ffmpeg.swr_init(swr));
        }

        /**
         * Writes the MP4 file header.
         */
        void writeHeader() throws WhatsAppMediaException.Processing {
            demuxPacket = FFmpegError.requireNonNull("av_packet_alloc(demux)",
                    Ffmpeg.av_packet_alloc());
            decFrame = FFmpegError.requireNonNull("av_frame_alloc(dec)",
                    Ffmpeg.av_frame_alloc());
            filterFrame = FFmpegError.requireNonNull("av_frame_alloc(filter)",
                    Ffmpeg.av_frame_alloc());
            encPacket = FFmpegError.requireNonNull("av_packet_alloc(enc)",
                    Ffmpeg.av_packet_alloc());
            if (audioEncCtx != MemorySegment.NULL) {
                audioEncFrame = FFmpegError.requireNonNull("av_frame_alloc(audio enc)",
                        Ffmpeg.av_frame_alloc());
                AVFrame.format(audioEncFrame, Ffmpeg.AV_SAMPLE_FMT_FLTP());
                var layout = AVChannelLayout.allocate(arena);
                Ffmpeg.av_channel_layout_default(layout, AUDIO_CHANNELS);
                Ffmpeg.av_channel_layout_copy(AVFrame.ch_layout(audioEncFrame), layout);
                AVFrame.sample_rate(audioEncFrame, AUDIO_SAMPLE_RATE);
                AVFrame.nb_samples(audioEncFrame, aacFrameSize);
                FFmpegError.check("av_frame_get_buffer(audio enc)",
                        Ffmpeg.av_frame_get_buffer(audioEncFrame, 0));
            }
            FFmpegError.check("avformat_write_header",
                    Ffmpeg.avformat_write_header(outFmtCtx, MemorySegment.NULL));
        }

        /**
         * Main demux + decode + encode + mux loop.
         */
        void transcodeLoop() throws WhatsAppMediaException.Processing {
            var eof = false;
            while (!eof) {
                var read = Ffmpeg.av_read_frame(inFmtCtx, demuxPacket);
                if (read < 0) {
                    if (videoDecCtx != MemorySegment.NULL) {
                        Ffmpeg.avcodec_send_packet(videoDecCtx, MemorySegment.NULL);
                    }
                    if (audioDecCtx != MemorySegment.NULL) {
                        Ffmpeg.avcodec_send_packet(audioDecCtx, MemorySegment.NULL);
                    }
                    eof = true;
                } else {
                    var idx = AVPacket.stream_index(demuxPacket);
                    if (idx == videoStreamIndex) {
                        var sent = Ffmpeg.avcodec_send_packet(videoDecCtx, demuxPacket);
                        Ffmpeg.av_packet_unref(demuxPacket);
                        if (sent < 0 && !FFmpegError.isAgain(sent)) {
                            throw new WhatsAppMediaException.Processing(
                                    "video send_packet failed: " + FFmpegError.describe(sent));
                        }
                    } else if (idx == audioStreamIndex && audioDecCtx != MemorySegment.NULL) {
                        var sent = Ffmpeg.avcodec_send_packet(audioDecCtx, demuxPacket);
                        Ffmpeg.av_packet_unref(demuxPacket);
                        if (sent < 0 && !FFmpegError.isAgain(sent)) {
                            throw new WhatsAppMediaException.Processing(
                                    "audio send_packet failed: " + FFmpegError.describe(sent));
                        }
                    } else {
                        Ffmpeg.av_packet_unref(demuxPacket);
                        continue;
                    }
                }
                drainVideoDecoder();
                if (audioDecCtx != MemorySegment.NULL) {
                    drainAudioDecoder();
                }
            }
        }

        /**
         * Drains every frame the video decoder has produced and routes
         * each through the filter graph and encoder.
         */
        void drainVideoDecoder() throws WhatsAppMediaException.Processing {
            while (true) {
                var got = Ffmpeg.avcodec_receive_frame(videoDecCtx, decFrame);
                if (FFmpegError.isAgain(got) || FFmpegError.isEof(got)) {
                    return;
                }
                if (got < 0) {
                    throw new WhatsAppMediaException.Processing(
                            "video receive_frame failed: " + FFmpegError.describe(got));
                }
                FFmpegError.check("av_buffersrc_add_frame_flags",
                        Ffmpeg.av_buffersrc_add_frame_flags(filterSrcCtx, decFrame, 0));
                while (true) {
                    var gotFiltered = Ffmpeg.av_buffersink_get_frame(filterSinkCtx, filterFrame);
                    if (FFmpegError.isAgain(gotFiltered) || FFmpegError.isEof(gotFiltered)) {
                        break;
                    }
                    if (gotFiltered < 0) {
                        throw new WhatsAppMediaException.Processing(
                                "buffersink_get_frame failed: " + FFmpegError.describe(gotFiltered));
                    }
                    if (thumbnailFrame == MemorySegment.NULL) {
                        thumbnailFrame = Ffmpeg.av_frame_alloc();
                        if (thumbnailFrame != MemorySegment.NULL) {
                            Ffmpeg.av_frame_ref(thumbnailFrame, filterFrame);
                        }
                    }
                    AVFrame.pts(filterFrame, videoPts++);
                    pushFrameAndDrain(videoEncCtx, filterFrame, videoOutIndex);
                    Ffmpeg.av_frame_unref(filterFrame);
                }
                Ffmpeg.av_frame_unref(decFrame);
            }
        }

        /**
         * Drains every frame the audio decoder has produced and routes
         * each through the resampler and AAC encoder.
         */
        void drainAudioDecoder() throws WhatsAppMediaException.Processing {
            while (true) {
                var got = Ffmpeg.avcodec_receive_frame(audioDecCtx, decFrame);
                if (FFmpegError.isAgain(got) || FFmpegError.isEof(got)) {
                    return;
                }
                if (got < 0) {
                    throw new WhatsAppMediaException.Processing(
                            "audio receive_frame failed: " + FFmpegError.describe(got));
                }
                resampleAndEncodeAudio(decFrame);
                Ffmpeg.av_frame_unref(decFrame);
            }
        }

        /**
         * Resamples one decoded audio frame and dispatches every full
         * AAC frame to the encoder.
         *
         * @param inFrame the decoded source frame
         */
        void resampleAndEncodeAudio(MemorySegment inFrame) throws WhatsAppMediaException.Processing {
            var outBufSamples = aacFrameSize;
            var channels = AUDIO_CHANNELS;
            var outDataPtrs = arena.allocate((long) channels * ValueLayout.ADDRESS.byteSize());
            for (var c = 0; c < channels; c++) {
                var plane = arena.allocate((long) outBufSamples * Float.BYTES);
                outDataPtrs.setAtIndex(ValueLayout.ADDRESS, c, plane);
            }
            var produced = FFmpegError.check("swr_convert",
                    Ffmpeg.swr_convert(swr, outDataPtrs, outBufSamples,
                            AVFrame.extended_data(inFrame), AVFrame.nb_samples(inFrame)));
            if (produced > 0) {
                writeAudioFrame(outDataPtrs, produced, channels);
            }
        }

        /**
         * Copies resampled samples into the AAC encoder frame and dispatches it.
         *
         * @param outDataPtrs per-channel data pointer table
         * @param samples     number of produced samples per channel
         * @param channels    channel count
         */
        void writeAudioFrame(MemorySegment outDataPtrs, int samples, int channels)
                throws WhatsAppMediaException.Processing {
            AVFrame.nb_samples(audioEncFrame, samples);
            AVFrame.pts(audioEncFrame, audioPts);
            FFmpegError.check("av_frame_make_writable(audio)",
                    Ffmpeg.av_frame_make_writable(audioEncFrame));
            for (var c = 0; c < channels; c++) {
                var dstPlane = AVFrame.data(audioEncFrame).getAtIndex(ValueLayout.ADDRESS, c);
                var srcPlane = outDataPtrs.getAtIndex(ValueLayout.ADDRESS, c)
                        .reinterpret((long) samples * Float.BYTES);
                MemorySegment.copy(srcPlane, 0L,
                        dstPlane.reinterpret((long) samples * Float.BYTES), 0L,
                        (long) samples * Float.BYTES);
            }
            audioPts += samples;
            pushFrameAndDrain(audioEncCtx, audioEncFrame, audioOutIndex);
        }

        /**
         * Sends one frame to the encoder and drains every produced
         * packet to the muxer.
         *
         * @param encCtx     the encoder
         * @param frame      the frame, or {@code NULL} for end-of-stream
         * @param outIndex   the muxer stream index to tag packets with
         */
        void pushFrameAndDrain(MemorySegment encCtx, MemorySegment frame, int outIndex)
                throws WhatsAppMediaException.Processing {
            var sent = Ffmpeg.avcodec_send_frame(encCtx, frame);
            if (sent < 0 && !FFmpegError.isAgain(sent) && !FFmpegError.isEof(sent)) {
                throw new WhatsAppMediaException.Processing(
                        "send_frame failed: " + FFmpegError.describe(sent));
            }
            while (true) {
                var got = Ffmpeg.avcodec_receive_packet(encCtx, encPacket);
                if (FFmpegError.isAgain(got) || FFmpegError.isEof(got)) {
                    return;
                }
                if (got < 0) {
                    throw new WhatsAppMediaException.Processing(
                            "receive_packet failed: " + FFmpegError.describe(got));
                }
                AVPacket.stream_index(encPacket, outIndex);
                rescalePacketTimestamps(encCtx, outIndex);
                var written = Ffmpeg.av_interleaved_write_frame(outFmtCtx, encPacket);
                Ffmpeg.av_packet_unref(encPacket);
                if (written < 0) {
                    throw new WhatsAppMediaException.Processing(
                            "av_interleaved_write_frame failed: " + FFmpegError.describe(written));
                }
            }
        }

        /**
         * Rescales the packet timestamps from the encoder's time base to
         * the muxer stream's time base.
         *
         * @param encCtx   the encoder context
         * @param outIndex the muxer stream index
         */
        void rescalePacketTimestamps(MemorySegment encCtx, int outIndex) {
            var stream = AVFormatContext.streams(outFmtCtx)
                    .getAtIndex(ValueLayout.ADDRESS, outIndex)
                    .reinterpret(AVStream.layout().byteSize());
            Ffmpeg.av_packet_rescale_ts(encPacket,
                    AVCodecContext.time_base(encCtx), AVStream.time_base(stream));
        }

        /**
         * Flushes both encoders by sending a {@code NULL} frame.
         */
        void flushEncoders() throws WhatsAppMediaException.Processing {
            pushFrameAndDrain(videoEncCtx, MemorySegment.NULL, videoOutIndex);
            if (audioEncCtx != MemorySegment.NULL) {
                pushFrameAndDrain(audioEncCtx, MemorySegment.NULL, audioOutIndex);
            }
        }

        /**
         * Encodes the saved first video frame as an MJPEG thumbnail.
         *
         * @return the encoded JPEG bytes, or {@code null} when no frame
         *         was ever decoded
         */
        byte[] encodeThumbnail() throws WhatsAppMediaException.Processing {
            if (thumbnailFrame == MemorySegment.NULL) {
                return null;
            }
            var srcW = AVFrame.width(thumbnailFrame);
            var srcH = AVFrame.height(thumbnailFrame);
            var longest = Math.max(srcW, srcH);
            int dstW;
            int dstH;
            if (longest <= VIDEO_THUMB_MAX_EDGE) {
                dstW = roundEven(srcW);
                dstH = roundEven(srcH);
            } else {
                var scale = (double) VIDEO_THUMB_MAX_EDGE / longest;
                dstW = roundEven((int) Math.round(srcW * scale));
                dstH = roundEven((int) Math.round(srcH * scale));
            }
            var thumbScaled = scaleThumbnail(thumbnailFrame, dstW, dstH);
            try {
                var jpeg = encodeMjpeg(thumbScaled, dstW, dstH);
                return JpegCleaner.clean(jpeg);
            } finally {
                freeFrame(thumbScaled);
            }
        }

        /**
         * Runs a one-shot scale filter graph for the thumbnail.
         *
         * @param srcFrame the source frame
         * @param dstW     target width
         * @param dstH     target height
         * @return the scaled frame (caller frees)
         */
        MemorySegment scaleThumbnail(MemorySegment srcFrame, int dstW, int dstH)
                throws WhatsAppMediaException.Processing {
            var graph = FFmpegError.requireNonNull("avfilter_graph_alloc(thumb)",
                    Ffmpeg.avfilter_graph_alloc());
            try {
                var srcArgs = String.format(
                        "video_size=%dx%d:pix_fmt=%d:time_base=1/1:pixel_aspect=1/1",
                        AVFrame.width(srcFrame), AVFrame.height(srcFrame),
                        AVFrame.format(srcFrame));
                var bufferFilter = FFmpegError.requireNonNull("avfilter_get_by_name(buffer)",
                        Ffmpeg.avfilter_get_by_name(arena.allocateFrom("buffer")));
                var sinkFilter = FFmpegError.requireNonNull("avfilter_get_by_name(buffersink)",
                        Ffmpeg.avfilter_get_by_name(arena.allocateFrom("buffersink")));
                var srcPp = arena.allocate(ValueLayout.ADDRESS);
                var sinkPp = arena.allocate(ValueLayout.ADDRESS);
                FFmpegError.check("avfilter_graph_create_filter(in)",
                        Ffmpeg.avfilter_graph_create_filter(srcPp, bufferFilter,
                                arena.allocateFrom("in"), arena.allocateFrom(srcArgs),
                                MemorySegment.NULL, graph));
                FFmpegError.check("avfilter_graph_create_filter(out)",
                        Ffmpeg.avfilter_graph_create_filter(sinkPp, sinkFilter,
                                arena.allocateFrom("out"), MemorySegment.NULL,
                                MemorySegment.NULL, graph));
                var srcCtx = srcPp.get(ValueLayout.ADDRESS, 0L)
                        .reinterpret(AVFilterContext.layout().byteSize());
                var sinkCtx = sinkPp.get(ValueLayout.ADDRESS, 0L)
                        .reinterpret(AVFilterContext.layout().byteSize());
                var chain = String.format("scale=%d:%d:flags=bicubic,format=yuvj420p",
                        dstW, dstH);
                var outputs = FFmpegError.requireNonNull("avfilter_inout_alloc(outputs)",
                        Ffmpeg.avfilter_inout_alloc());
                var inputs = FFmpegError.requireNonNull("avfilter_inout_alloc(inputs)",
                        Ffmpeg.avfilter_inout_alloc());
                AVFilterInOut.name(outputs, Ffmpeg.av_strdup(arena.allocateFrom("in")));
                AVFilterInOut.filter_ctx(outputs, srcCtx);
                AVFilterInOut.pad_idx(outputs, 0);
                AVFilterInOut.next(outputs, MemorySegment.NULL);
                AVFilterInOut.name(inputs, Ffmpeg.av_strdup(arena.allocateFrom("out")));
                AVFilterInOut.filter_ctx(inputs, sinkCtx);
                AVFilterInOut.pad_idx(inputs, 0);
                AVFilterInOut.next(inputs, MemorySegment.NULL);
                var outputsPp = arena.allocate(ValueLayout.ADDRESS);
                var inputsPp = arena.allocate(ValueLayout.ADDRESS);
                outputsPp.set(ValueLayout.ADDRESS, 0L, outputs);
                inputsPp.set(ValueLayout.ADDRESS, 0L, inputs);
                try {
                    FFmpegError.check("avfilter_graph_parse_ptr",
                            Ffmpeg.avfilter_graph_parse_ptr(graph, arena.allocateFrom(chain),
                                    inputsPp, outputsPp, MemorySegment.NULL));
                    FFmpegError.check("avfilter_graph_config",
                            Ffmpeg.avfilter_graph_config(graph, MemorySegment.NULL));
                } finally {
                    Ffmpeg.avfilter_inout_free(inputsPp);
                    Ffmpeg.avfilter_inout_free(outputsPp);
                }
                FFmpegError.check("av_buffersrc_add_frame_flags",
                        Ffmpeg.av_buffersrc_add_frame_flags(srcCtx, srcFrame, 0));
                var outFrame = FFmpegError.requireNonNull("av_frame_alloc(thumb)",
                        Ffmpeg.av_frame_alloc());
                var got = Ffmpeg.av_buffersink_get_frame(sinkCtx, outFrame);
                if (got < 0) {
                    freeFrame(outFrame);
                    throw new WhatsAppMediaException.Processing(
                            "thumb buffersink_get_frame failed: " + FFmpegError.describe(got));
                }
                return outFrame;
            } finally {
                try (var local = Arena.ofConfined()) {
                    var pp = local.allocate(ValueLayout.ADDRESS);
                    pp.set(ValueLayout.ADDRESS, 0L, graph);
                    Ffmpeg.avfilter_graph_free(pp);
                }
            }
        }

        /**
         * Encodes a single MJPEG-ready frame and returns the encoded
         * packet bytes.
         *
         * @param frame  the input frame
         * @param w      frame width
         * @param h      frame height
         * @return the encoded JPEG bytes
         */
        byte[] encodeMjpeg(MemorySegment frame, int w, int h)
                throws WhatsAppMediaException.Processing {
            var codec = FFmpegError.requireNonNull("avcodec_find_encoder(MJPEG)",
                    Ffmpeg.avcodec_find_encoder(Ffmpeg.AV_CODEC_ID_MJPEG()));
            var ctx = FFmpegError.requireNonNull("avcodec_alloc_context3(mjpeg)",
                    Ffmpeg.avcodec_alloc_context3(codec));
            try (var local = Arena.ofConfined()) {
                AVCodecContext.width(ctx, w);
                AVCodecContext.height(ctx, h);
                AVCodecContext.pix_fmt(ctx, Ffmpeg.AV_PIX_FMT_YUVJ420P());
                var tb = local.allocate(8);
                tb.set(ValueLayout.JAVA_INT, 0L, 1);
                tb.set(ValueLayout.JAVA_INT, 4L, 1);
                AVCodecContext.time_base(ctx, tb);
                AVCodecContext.flags(ctx,
                        AVCodecContext.flags(ctx) | Ffmpeg.AV_CODEC_FLAG_QSCALE());
                AVCodecContext.global_quality(ctx, THUMB_QSCALE * Ffmpeg.FF_QP2LAMBDA());
                FFmpegError.check("avcodec_open2(mjpeg)",
                        Ffmpeg.avcodec_open2(ctx, codec, MemorySegment.NULL));
                AVFrame.pts(frame, 0);
                FFmpegError.check("avcodec_send_frame(thumb)",
                        Ffmpeg.avcodec_send_frame(ctx, frame));
                FFmpegError.check("avcodec_send_frame(thumb EOF)",
                        Ffmpeg.avcodec_send_frame(ctx, MemorySegment.NULL));
                var packet = FFmpegError.requireNonNull("av_packet_alloc(thumb)",
                        Ffmpeg.av_packet_alloc());
                try {
                    var got = Ffmpeg.avcodec_receive_packet(ctx, packet);
                    if (got < 0) {
                        throw new WhatsAppMediaException.Processing(
                                "thumb receive_packet failed: " + FFmpegError.describe(got));
                    }
                    var size = AVPacket.size(packet);
                    var data = AVPacket.data(packet).reinterpret(size);
                    var out = new byte[size];
                    MemorySegment.copy(data, ValueLayout.JAVA_BYTE, 0, out, 0, size);
                    return out;
                } finally {
                    Ffmpeg.av_packet_unref(packet);
                    var pp = local.allocate(ValueLayout.ADDRESS);
                    pp.set(ValueLayout.ADDRESS, 0L, packet);
                    Ffmpeg.av_packet_free(pp);
                }
            } finally {
                try (var local = Arena.ofConfined()) {
                    var pp = local.allocate(ValueLayout.ADDRESS);
                    pp.set(ValueLayout.ADDRESS, 0L, ctx);
                    Ffmpeg.avcodec_free_context(pp);
                }
            }
        }

        /**
         * Computes the encoded duration in whole seconds.
         *
         * @return the duration, with a one-second floor when the source
         *         is too short to round to a whole second
         */
        int computeDurationSeconds() {
            var durTb = AVFormatContext.duration(inFmtCtx);
            if (durTb > 0) {
                return (int) Math.max(1, durTb / 1_000_000L);
            }
            if (videoPts > 0 && frameRateNum > 0) {
                return (int) Math.max(1, (videoPts * frameRateDen) / frameRateNum);
            }
            return 1;
        }

        /**
         * Releases every libav* resource owned by the run.
         */
        void cleanup() {
            freeFrame(thumbnailFrame);
            freeFrame(filterFrame);
            freeFrame(audioEncFrame);
            freeFrame(decFrame);
            freePointer(encPacket, Ffmpeg::av_packet_free);
            freePointer(demuxPacket, Ffmpeg::av_packet_free);
            if (filterGraph != MemorySegment.NULL) {
                try (var local = Arena.ofConfined()) {
                    var pp = local.allocate(ValueLayout.ADDRESS);
                    pp.set(ValueLayout.ADDRESS, 0L, filterGraph);
                    Ffmpeg.avfilter_graph_free(pp);
                }
            }
            freePointer(swr, Ffmpeg::swr_free);
            freePointer(audioEncCtx, Ffmpeg::avcodec_free_context);
            freePointer(videoEncCtx, Ffmpeg::avcodec_free_context);
            freePointer(audioDecCtx, Ffmpeg::avcodec_free_context);
            freePointer(videoDecCtx, Ffmpeg::avcodec_free_context);
            if (outFmtCtx != MemorySegment.NULL) {
                Ffmpeg.avformat_free_context(outFmtCtx);
            }
            freePointer(inFmtCtx, Ffmpeg::avformat_close_input);
            if (outputBridge != null) {
                outputBridge.close();
            }
            if (inputBridge != null) {
                inputBridge.close();
            }
        }
    }

    /**
     * Returns the index of the first stream of the given media type, or
     * {@code -1} when none.
     *
     * @param formatCtx the open demuxer
     * @param mediaType the {@code AVMEDIA_TYPE_*} value
     * @return the stream index or {@code -1}
     */
    private static int pickStream(MemorySegment formatCtx, int mediaType) {
        var n = AVFormatContext.nb_streams(formatCtx);
        var streams = AVFormatContext.streams(formatCtx);
        for (var i = 0; i < n; i++) {
            var stream = streams.getAtIndex(ValueLayout.ADDRESS, i)
                    .reinterpret(AVStream.layout().byteSize());
            var params = AVStream.codecpar(stream);
            if (AVCodecParameters.codec_type(params) == mediaType) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the i-th stream pointer from a demuxer.
     *
     * @param formatCtx the open demuxer
     * @param index     the zero-based index
     * @return the stream pointer
     */
    private static MemorySegment streamPointer(MemorySegment formatCtx, int index) {
        return AVFormatContext.streams(formatCtx)
                .getAtIndex(ValueLayout.ADDRESS, index)
                .reinterpret(AVStream.layout().byteSize());
    }

    /**
     * Frees an {@link AVFrame}, tolerating {@code NULL}.
     *
     * @param frame the frame
     */
    private static void freeFrame(MemorySegment frame) {
        if (frame == null || frame == MemorySegment.NULL) {
            return;
        }
        try (var local = Arena.ofConfined()) {
            var pp = local.allocate(ValueLayout.ADDRESS);
            pp.set(ValueLayout.ADDRESS, 0L, frame);
            Ffmpeg.av_frame_free(pp);
        }
    }

    /**
     * Calls a libav free function on a pointer slot if the pointer is
     * non-{@code NULL}.
     *
     * @param ptr   the pointer
     * @param freer the libav free function
     */
    private static void freePointer(MemorySegment ptr, FreePointer freer) {
        if (ptr == null || ptr == MemorySegment.NULL) {
            return;
        }
        try (var local = Arena.ofConfined()) {
            var pp = local.allocate(ValueLayout.ADDRESS);
            pp.set(ValueLayout.ADDRESS, 0L, ptr);
            freer.free(pp);
        }
    }

    /**
     * Rounds a value down to the nearest even integer, floored at 2.
     *
     * @param value the value
     * @return the rounded value
     */
    private static int roundEven(int value) {
        var rounded = value - (value & 1);
        return rounded < 2 ? 2 : rounded;
    }

    /**
     * Functional handle to one of libav's pointer-slot free functions.
     */
    @FunctionalInterface
    private interface FreePointer {
        /**
         * Calls the libav free function.
         *
         * @param pp pointer-to-pointer slot
         */
        void free(MemorySegment pp);
    }
}
