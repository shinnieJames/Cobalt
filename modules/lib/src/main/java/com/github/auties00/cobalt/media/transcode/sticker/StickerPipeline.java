package com.github.auties00.cobalt.media.transcode.sticker;

import com.github.auties00.cobalt.exception.WhatsAppMediaException;
import com.github.auties00.cobalt.media.MediaPayload;
import com.github.auties00.cobalt.media.ffmpeg.AVCodecContext;
import com.github.auties00.cobalt.media.ffmpeg.AVCodecParameters;
import com.github.auties00.cobalt.media.ffmpeg.AVFilterContext;
import com.github.auties00.cobalt.media.ffmpeg.AVFilterInOut;
import com.github.auties00.cobalt.media.ffmpeg.AVFormatContext;
import com.github.auties00.cobalt.media.ffmpeg.AVFrame;
import com.github.auties00.cobalt.media.ffmpeg.AVPacket;
import com.github.auties00.cobalt.media.ffmpeg.AVStream;
import com.github.auties00.cobalt.media.ffmpeg.FFmpegError;
import com.github.auties00.cobalt.media.ffmpeg.FFmpegLoader;
import com.github.auties00.cobalt.media.ffmpeg.Ffmpeg;
import com.github.auties00.cobalt.media.transcode.MediaTranscoderService;
import com.github.auties00.cobalt.media.transcode.avio.AvioReadBuffer;
import com.github.auties00.cobalt.model.media.MediaProvider;
import com.github.auties00.cobalt.model.message.media.StickerMessage;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.SeekableByteChannel;
import java.util.Map;

/**
 * Decodes a source image, normalises it to the WhatsApp sticker shape, and
 * encodes it as an extended WebP.
 *
 * @apiNote
 * Drives the sticker branch of the upload transcoder. The output is a
 * {@value #STICKER_DIMENSION}-square WebP with transparent padding around
 * the source aspect ratio so the sticker overlay looks the same regardless
 * of the source image proportions. When the caller supplies sticker
 * metadata (publisher, pack id, emojis, accessibility text) the pipeline
 * appends a WhatsApp-format EXIF chunk via {@link WebpMetadataWriter}.
 *
 * @implNote
 * This implementation drives libavfilter with the WA Web sticker filter
 * chain
 * {@snippet :
 *  scale=512:512:force_original_aspect_ratio=decrease,
 *  pad=512:512:(ow-iw)/2:(oh-ih)/2:0x00000000,
 *  format=rgba
 * }
 * then hands the RGBA frame to FFmpeg's {@code libwebp} encoder at
 * quality {@value #WEBP_QUALITY} with {@code lossless=0}. Encoding stops at
 * a single packet because stickers are static one-frame WebPs. The source
 * is read through {@link AvioReadBuffer} backed by a {@link FileChannel};
 * the encoded output is a single libwebp packet copied straight to a
 * {@link MediaPayload.OfBytes} so no temp file is created.
 *
 * @see WebpMetadataWriter
 */
public final class StickerPipeline {
    /**
     * Side of the output sticker in pixels. Mirrors
     * {@code WAWebStickerConstants.STICKER_DIMENSION}.
     */
    private static final int STICKER_DIMENSION = 512;

    /**
     * libwebp quality setting in the {@code 0..100} range. Mirrors WA Web's
     * sticker encoder configuration.
     */
    private static final int WEBP_QUALITY = 90;

    /**
     * Constructs the pipeline; the parent
     * {@link MediaTranscoderService} owns the single instance.
     */
    public StickerPipeline() {
    }

    /**
     * Transcodes the source image into the sticker wire format, applies
     * codec-derived metadata to {@code provider}, and returns the encoded
     * payload.
     *
     * @apiNote
     * Equivalent to {@link #run(MediaProvider, Path, Map)} with an empty
     * metadata map.
     *
     * @param provider the upload target; codec-derived fields are applied
     *                 to this instance
     * @param source   the raw image channel; not closed by this method
     * @return the encoded WebP payload
     * @throws WhatsAppMediaException.Processing if decoding or encoding
     *         fails
     */
    public MediaPayload run(MediaProvider provider, SeekableByteChannel source)
            throws WhatsAppMediaException.Processing {
        return run(provider, source, Map.of());
    }

    /**
     * Transcodes the source image into the sticker wire format with an
     * optional WhatsApp metadata chunk, applies codec-derived metadata to
     * {@code provider}, and returns the encoded payload.
     *
     * @apiNote
     * Keys in the {@code metadata} map are the canonical WhatsApp
     * descriptor names (for example {@code "sticker-pack-id"},
     * {@code "emojis"}, {@code "accessibility-text"}); see
     * {@code WAWebStickerMetadataParsing} for the full set. The map is
     * serialised to JSON and embedded as an EXIF chunk via
     * {@link WebpMetadataWriter#write(byte[], Map)}. When {@code provider}
     * is a {@link StickerMessage} the {@code mimetype}, {@code mediaSize},
     * {@code width}, and {@code height} fields are populated; every other
     * {@link MediaProvider} variant receives only the common
     * {@code mediaSize} update.
     *
     * @param provider the upload target; codec-derived fields are applied
     *                 to this instance
     * @param source   the raw image channel; not closed by this method
     * @param metadata sticker descriptor map; pass an empty map to skip
     *                 the metadata embed
     * @return the encoded WebP payload
     * @throws WhatsAppMediaException.Processing if decoding, encoding, or
     *         the metadata embed fails
     */
    public MediaPayload run(MediaProvider provider, SeekableByteChannel source,
                            Map<String, Object> metadata)
            throws WhatsAppMediaException.Processing {
        FFmpegLoader.ensureLoaded();
        try (var arena = Arena.ofShared();
             var decoded = decodeFirstFrame(arena, source)) {
            var scaled = scaleAndPad(decoded);
            byte[] webp;
            try {
                webp = encodeWebp(scaled);
            } finally {
                freeFrame(scaled);
            }
            if (!metadata.isEmpty()) {
                webp = WebpMetadataWriter.write(webp, metadata);
            }
            provider.setMediaSize(webp.length);
            if (provider instanceof StickerMessage sticker) {
                sticker.setMimetype("image/webp");
                sticker.setWidth(STICKER_DIMENSION);
                sticker.setHeight(STICKER_DIMENSION);
            }
            return new MediaPayload.OfBytes(webp);
        }
    }

    /**
     * Drives the source frame through the sticker filter chain.
     *
     * @apiNote
     * The chain mirrors WA Web's sticker pipeline: scale-with-aspect-fit
     * into the 512x512 box, pad the remainder with transparent black so
     * the sticker overlay is always the same size on every receiver, and
     * coerce to RGBA so the libwebp encoder picks up the alpha channel.
     *
     * @param decoded the demuxed source state with the first input frame
     * @return a freshly-allocated {@link AVFrame} (caller must
     *         {@code av_frame_free} via {@link #freeFrame(MemorySegment)})
     */
    private static MemorySegment scaleAndPad(DecodedSource decoded) {
        var arena = decoded.arena;
        var graph = FFmpegError.requireNonNull("avfilter_graph_alloc",
                Ffmpeg.avfilter_graph_alloc());
        try {
            var srcArgs = String.format(
                    "video_size=%dx%d:pix_fmt=%d:time_base=1/1:pixel_aspect=1/1",
                    AVFrame.width(decoded.frame),
                    AVFrame.height(decoded.frame),
                    AVFrame.format(decoded.frame));
            var bufferFilter = FFmpegError.requireNonNull("avfilter_get_by_name(buffer)",
                    Ffmpeg.avfilter_get_by_name(arena.allocateFrom("buffer")));
            var sinkFilter = FFmpegError.requireNonNull("avfilter_get_by_name(buffersink)",
                    Ffmpeg.avfilter_get_by_name(arena.allocateFrom("buffersink")));
            var srcCtxPtr = arena.allocate(ValueLayout.ADDRESS);
            var sinkCtxPtr = arena.allocate(ValueLayout.ADDRESS);
            FFmpegError.check("avfilter_graph_create_filter(in)",
                    Ffmpeg.avfilter_graph_create_filter(srcCtxPtr, bufferFilter,
                            arena.allocateFrom("in"), arena.allocateFrom(srcArgs),
                            MemorySegment.NULL, graph));
            FFmpegError.check("avfilter_graph_create_filter(out)",
                    Ffmpeg.avfilter_graph_create_filter(sinkCtxPtr, sinkFilter,
                            arena.allocateFrom("out"), MemorySegment.NULL,
                            MemorySegment.NULL, graph));
            var srcCtx = srcCtxPtr.get(ValueLayout.ADDRESS, 0L)
                    .reinterpret(AVFilterContext.layout().byteSize());
            var sinkCtx = sinkCtxPtr.get(ValueLayout.ADDRESS, 0L)
                    .reinterpret(AVFilterContext.layout().byteSize());
            var chain = String.format(
                    "scale=%d:%d:force_original_aspect_ratio=decrease,"
                            + "pad=%d:%d:(ow-iw)/2:(oh-ih)/2:0x00000000,"
                            + "format=rgba",
                    STICKER_DIMENSION, STICKER_DIMENSION,
                    STICKER_DIMENSION, STICKER_DIMENSION);
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
                    Ffmpeg.av_buffersrc_add_frame_flags(srcCtx, decoded.frame, 0));
            var outFrame = allocFrame();
            var got = Ffmpeg.av_buffersink_get_frame(sinkCtx, outFrame);
            if (got < 0) {
                freeFrame(outFrame);
                throw new WhatsAppMediaException.Processing(
                        "av_buffersink_get_frame failed: " + FFmpegError.describe(got));
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
     * Encodes the given RGBA frame as a WebP image via FFmpeg's
     * {@code libwebp} encoder.
     *
     * @apiNote
     * libwebp emits a single packet for a static image. The returned bytes
     * are a complete WebP file (RIFF + WEBP header + VP8L/VP8 chunk),
     * ready for {@link WebpMetadataWriter} when sticker metadata needs to
     * be embedded.
     *
     * @param frame the RGBA input frame at sticker dimensions
     * @return the encoded WebP bytes
     * @throws WhatsAppMediaException.Processing if any encoder call fails
     */
    private static byte[] encodeWebp(MemorySegment frame)
            throws WhatsAppMediaException.Processing {
        var codec = FFmpegError.requireNonNull("avcodec_find_encoder(WEBP)",
                Ffmpeg.avcodec_find_encoder(Ffmpeg.AV_CODEC_ID_WEBP()));
        var ctx = FFmpegError.requireNonNull("avcodec_alloc_context3(webp)",
                Ffmpeg.avcodec_alloc_context3(codec));
        try (var local = Arena.ofConfined()) {
            AVCodecContext.width(ctx, STICKER_DIMENSION);
            AVCodecContext.height(ctx, STICKER_DIMENSION);
            AVCodecContext.pix_fmt(ctx, Ffmpeg.AV_PIX_FMT_RGBA());
            var tb = local.allocate(8);
            tb.set(ValueLayout.JAVA_INT, 0L, 1);
            tb.set(ValueLayout.JAVA_INT, 4L, 1);
            AVCodecContext.time_base(ctx, tb);
            var searchChildren = Ffmpeg.AV_OPT_SEARCH_CHILDREN();
            FFmpegError.check("av_opt_set_int(quality)",
                    Ffmpeg.av_opt_set_int(ctx,
                            local.allocateFrom("quality"),
                            WEBP_QUALITY, searchChildren));
            FFmpegError.check("av_opt_set_int(lossless)",
                    Ffmpeg.av_opt_set_int(ctx,
                            local.allocateFrom("lossless"),
                            0, searchChildren));
            FFmpegError.check("avcodec_open2(webp)",
                    Ffmpeg.avcodec_open2(ctx, codec, MemorySegment.NULL));
            AVFrame.pts(frame, 0);
            FFmpegError.check("avcodec_send_frame",
                    Ffmpeg.avcodec_send_frame(ctx, frame));
            FFmpegError.check("avcodec_send_frame(EOF)",
                    Ffmpeg.avcodec_send_frame(ctx, MemorySegment.NULL));
            var packet = FFmpegError.requireNonNull("av_packet_alloc",
                    Ffmpeg.av_packet_alloc());
            try {
                var got = Ffmpeg.avcodec_receive_packet(ctx, packet);
                if (got < 0) {
                    throw new WhatsAppMediaException.Processing(
                            "avcodec_receive_packet failed: " + FFmpegError.describe(got));
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
     * Opens the source channel through FFmpeg and decodes the first frame.
     *
     * @param arena   the shared arena that owns the AVIO bridge
     * @param channel the source channel
     * @return the decoded source bundle
     * @throws WhatsAppMediaException.Processing if probing, opening, or
     *         decoding fails
     */
    private static DecodedSource decodeFirstFrame(Arena arena, SeekableByteChannel channel)
            throws WhatsAppMediaException.Processing {
        var bridge = new AvioReadBuffer(arena, channel);
        var formatCtx = MemorySegment.NULL;
        var codecCtx = MemorySegment.NULL;
        var frame = MemorySegment.NULL;
        var packet = MemorySegment.NULL;
        try {
            formatCtx = FFmpegError.requireNonNull("avformat_alloc_context",
                    Ffmpeg.avformat_alloc_context());
            AVFormatContext.pb(formatCtx, bridge.ioContext());
            var formatPp = arena.allocate(ValueLayout.ADDRESS);
            formatPp.set(ValueLayout.ADDRESS, 0L, formatCtx);
            FFmpegError.check("avformat_open_input",
                    Ffmpeg.avformat_open_input(formatPp, MemorySegment.NULL,
                            MemorySegment.NULL, MemorySegment.NULL));
            formatCtx = formatPp.get(ValueLayout.ADDRESS, 0L)
                    .reinterpret(AVFormatContext.layout().byteSize());
            FFmpegError.check("avformat_find_stream_info",
                    Ffmpeg.avformat_find_stream_info(formatCtx, MemorySegment.NULL));
            var streamIndex = pickVideoStream(formatCtx);
            if (streamIndex < 0) {
                throw new WhatsAppMediaException.Processing("no video stream in sticker source");
            }
            var stream = streamPointer(formatCtx, streamIndex);
            var params = AVStream.codecpar(stream);
            var codecId = AVCodecParameters.codec_id(params);
            var codec = FFmpegError.requireNonNull(
                    "avcodec_find_decoder(" + codecId + ")",
                    Ffmpeg.avcodec_find_decoder(codecId));
            codecCtx = FFmpegError.requireNonNull("avcodec_alloc_context3",
                    Ffmpeg.avcodec_alloc_context3(codec));
            FFmpegError.check("avcodec_parameters_to_context",
                    Ffmpeg.avcodec_parameters_to_context(codecCtx, params));
            FFmpegError.check("avcodec_open2",
                    Ffmpeg.avcodec_open2(codecCtx, codec, MemorySegment.NULL));
            packet = FFmpegError.requireNonNull("av_packet_alloc", Ffmpeg.av_packet_alloc());
            frame = allocFrame();
            while (true) {
                var read = Ffmpeg.av_read_frame(formatCtx, packet);
                if (read < 0) {
                    Ffmpeg.avcodec_send_packet(codecCtx, MemorySegment.NULL);
                } else if (AVPacket.stream_index(packet) != streamIndex) {
                    Ffmpeg.av_packet_unref(packet);
                    continue;
                } else {
                    var sent = Ffmpeg.avcodec_send_packet(codecCtx, packet);
                    Ffmpeg.av_packet_unref(packet);
                    if (sent < 0 && !FFmpegError.isAgain(sent)) {
                        throw new WhatsAppMediaException.Processing(
                                "avcodec_send_packet failed: " + FFmpegError.describe(sent));
                    }
                }
                var got = Ffmpeg.avcodec_receive_frame(codecCtx, frame);
                if (got == 0) {
                    break;
                }
                if (FFmpegError.isAgain(got)) {
                    if (read < 0) {
                        throw new WhatsAppMediaException.Processing(
                                "decoder produced no frame before EOF");
                    }
                    continue;
                }
                if (FFmpegError.isEof(got)) {
                    throw new WhatsAppMediaException.Processing(
                            "decoder reported EOF before producing a frame");
                }
                throw new WhatsAppMediaException.Processing(
                        "avcodec_receive_frame failed: " + FFmpegError.describe(got));
            }
            return new DecodedSource(arena, bridge, formatCtx, codecCtx, packet, frame);
        } catch (RuntimeException e) {
            freeOnFailure(formatCtx, codecCtx, frame, packet, bridge);
            throw e;
        }
    }

    /**
     * Returns the index of the first video stream in the demuxer, or
     * {@code -1} if none.
     *
     * @param formatCtx the open demuxer
     * @return the stream index or {@code -1}
     */
    private static int pickVideoStream(MemorySegment formatCtx) {
        var n = AVFormatContext.nb_streams(formatCtx);
        var streams = AVFormatContext.streams(formatCtx);
        for (var i = 0; i < n; i++) {
            var stream = streams.getAtIndex(ValueLayout.ADDRESS, i)
                    .reinterpret(AVStream.layout().byteSize());
            var params = AVStream.codecpar(stream);
            if (AVCodecParameters.codec_type(params) == Ffmpeg.AVMEDIA_TYPE_VIDEO()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the i-th stream pointer from the demuxer's stream array.
     *
     * @param formatCtx the open demuxer
     * @param index     the zero-based stream index
     * @return the stream pointer reinterpreted as an {@link AVStream}
     */
    private static MemorySegment streamPointer(MemorySegment formatCtx, int index) {
        return AVFormatContext.streams(formatCtx)
                .getAtIndex(ValueLayout.ADDRESS, index)
                .reinterpret(AVStream.layout().byteSize());
    }

    /**
     * Allocates a fresh {@link AVFrame}.
     *
     * @return the allocated frame
     */
    private static MemorySegment allocFrame() {
        return FFmpegError.requireNonNull("av_frame_alloc", Ffmpeg.av_frame_alloc());
    }

    /**
     * Frees the given frame via {@code av_frame_free}, tolerating
     * {@code NULL}.
     *
     * @param frame the frame to free
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
     * Releases half-initialised resources on the failure path.
     *
     * @param formatCtx demuxer context or {@code NULL}
     * @param codecCtx  decoder context or {@code NULL}
     * @param frame     decoder output frame or {@code NULL}
     * @param packet    demuxer packet or {@code NULL}
     * @param bridge    AVIO bridge to close
     */
    private static void freeOnFailure(MemorySegment formatCtx, MemorySegment codecCtx,
                                       MemorySegment frame, MemorySegment packet,
                                       AvioReadBuffer bridge) {
        if (packet != null && packet != MemorySegment.NULL) {
            try (var local = Arena.ofConfined()) {
                var pp = local.allocate(ValueLayout.ADDRESS);
                pp.set(ValueLayout.ADDRESS, 0L, packet);
                Ffmpeg.av_packet_free(pp);
            }
        }
        freeFrame(frame);
        if (codecCtx != null && codecCtx != MemorySegment.NULL) {
            try (var local = Arena.ofConfined()) {
                var pp = local.allocate(ValueLayout.ADDRESS);
                pp.set(ValueLayout.ADDRESS, 0L, codecCtx);
                Ffmpeg.avcodec_free_context(pp);
            }
        }
        if (formatCtx != null && formatCtx != MemorySegment.NULL) {
            try (var local = Arena.ofConfined()) {
                var pp = local.allocate(ValueLayout.ADDRESS);
                pp.set(ValueLayout.ADDRESS, 0L, formatCtx);
                Ffmpeg.avformat_close_input(pp);
            }
        }
        bridge.close();
    }

    /**
     * Holds the per-run FFmpeg state across the decode and filter passes.
     *
     * @apiNote
     * Implements {@link AutoCloseable} so the orchestrator can wrap it in a
     * try-with-resources block and guarantee FFmpeg cleanup runs even when
     * the filter or encode passes throw.
     */
    private static final class DecodedSource implements AutoCloseable {
        /**
         * Shared arena holding the AVIO bridge and helper allocations.
         */
        final Arena arena;

        /**
         * AVIO bridge backing the demuxer.
         */
        final AvioReadBuffer bridge;

        /**
         * Open libavformat demuxer.
         */
        final MemorySegment formatCtx;

        /**
         * Open libavcodec decoder.
         */
        final MemorySegment codecCtx;

        /**
         * Reusable demuxer packet.
         */
        final MemorySegment packet;

        /**
         * The first decoded frame; reused for the filter pass.
         */
        final MemorySegment frame;

        /**
         * Constructs the decoded source bundle.
         *
         * @param arena     the shared arena
         * @param bridge    the AVIO bridge
         * @param formatCtx the open demuxer
         * @param codecCtx  the open decoder
         * @param packet    the demuxer packet
         * @param frame     the first decoded frame
         */
        DecodedSource(Arena arena, AvioReadBuffer bridge, MemorySegment formatCtx,
                      MemorySegment codecCtx, MemorySegment packet, MemorySegment frame) {
            this.arena = arena;
            this.bridge = bridge;
            this.formatCtx = formatCtx;
            this.codecCtx = codecCtx;
            this.packet = packet;
            this.frame = frame;
        }

        @Override
        public void close() {
            try (var local = Arena.ofConfined()) {
                if (packet != null && packet != MemorySegment.NULL) {
                    var pp = local.allocate(ValueLayout.ADDRESS);
                    pp.set(ValueLayout.ADDRESS, 0L, packet);
                    Ffmpeg.av_packet_free(pp);
                }
            }
            freeFrame(frame);
            if (codecCtx != null && codecCtx != MemorySegment.NULL) {
                try (var local = Arena.ofConfined()) {
                    var pp = local.allocate(ValueLayout.ADDRESS);
                    pp.set(ValueLayout.ADDRESS, 0L, codecCtx);
                    Ffmpeg.avcodec_free_context(pp);
                }
            }
            if (formatCtx != null && formatCtx != MemorySegment.NULL) {
                try (var local = Arena.ofConfined()) {
                    var pp = local.allocate(ValueLayout.ADDRESS);
                    pp.set(ValueLayout.ADDRESS, 0L, formatCtx);
                    Ffmpeg.avformat_close_input(pp);
                }
            }
            bridge.close();
        }
    }
}
