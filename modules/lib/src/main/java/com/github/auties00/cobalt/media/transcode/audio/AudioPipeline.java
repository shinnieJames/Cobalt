package com.github.auties00.cobalt.media.transcode.audio;

import com.github.auties00.cobalt.exception.WhatsAppMediaException;
import com.github.auties00.cobalt.media.MediaPayload;
import com.github.auties00.cobalt.media.ffmpeg.AVChannelLayout;
import com.github.auties00.cobalt.media.ffmpeg.AVCodecContext;
import com.github.auties00.cobalt.media.ffmpeg.AVCodecParameters;
import com.github.auties00.cobalt.media.ffmpeg.AVFormatContext;
import com.github.auties00.cobalt.media.ffmpeg.AVFrame;
import com.github.auties00.cobalt.media.ffmpeg.AVOutputFormat;
import com.github.auties00.cobalt.media.ffmpeg.AVPacket;
import com.github.auties00.cobalt.media.ffmpeg.AVStream;
import com.github.auties00.cobalt.media.ffmpeg.FFmpegError;
import com.github.auties00.cobalt.media.ffmpeg.FFmpegLoader;
import com.github.auties00.cobalt.media.ffmpeg.Ffmpeg;
import com.github.auties00.cobalt.media.transcode.MediaTranscoderService;
import com.github.auties00.cobalt.media.transcode.avio.AvioReadBuffer;
import com.github.auties00.cobalt.media.transcode.avio.AvioWriteBuffer;
import com.github.auties00.cobalt.model.media.MediaProvider;
import com.github.auties00.cobalt.model.message.media.AudioMessage;
import com.github.auties00.cobalt.model.sync.action.setting.SettingsSyncAction;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Decodes a source music or general-audio stream and re-encodes it as a
 * WhatsApp non-PTT audio attachment (AAC inside an MP4/M4A container).
 *
 * @apiNote
 * Drives the non-voice audio branch of the upload transcoder. Targets
 * 48 kHz stereo AAC at either {@value #AAC_BITRATE_STANDARD} bps
 * ({@code STANDARD} quality) or {@value #AAC_BITRATE_HD} bps
 * ({@code HD} quality). The output container is M4A (the {@code ipod}
 * muxer in libavformat) so the file plays directly on every WhatsApp
 * client without further re-encoding.
 *
 * @implNote
 * This implementation drives FFmpeg end-to-end: libavformat demux,
 * libswresample to {@code FLTP} 48 kHz stereo, the native FFmpeg AAC
 * encoder, libavformat M4A mux. Sources sampled above 48 kHz are
 * downsampled; sources below 48 kHz are upsampled by libswresample so the
 * encoder always sees a uniform input format. The pipeline does not
 * preserve embedded metadata (ID3, FLAC tags) because the upload protobuf
 * carries its own caption and filename fields.
 */
public final class AudioPipeline {
    /**
     * Sample rate of the encoded output in Hz.
     */
    private static final int OUTPUT_SAMPLE_RATE = 48_000;

    /**
     * Channel count of the encoded output (stereo).
     */
    private static final int OUTPUT_CHANNELS = 2;

    /**
     * AAC bitrate for the {@code STANDARD} quality preset.
     */
    private static final int AAC_BITRATE_STANDARD = 128_000;

    /**
     * AAC bitrate for the {@code HD} quality preset.
     */
    private static final int AAC_BITRATE_HD = 192_000;

    /**
     * Default AAC frame size in samples; the native FFmpeg AAC encoder
     * announces {@code 1024} via {@code AVCodecContext.frame_size} after
     * {@code avcodec_open2}, so this is a deliberately defensive default
     * for the unlikely case of a build that reports zero.
     */
    private static final int DEFAULT_AAC_FRAME_SAMPLES = 1024;

    /**
     * Constructs the pipeline; the parent
     * {@link MediaTranscoderService} owns the single instance.
     */
    public AudioPipeline() {
    }

    /**
     * Transcodes the source audio, applies codec-derived metadata to
     * {@code provider}, and returns the encoded payload stream.
     *
     * @apiNote
     * Mutates the provider in place: when {@code provider} is an
     * {@link AudioMessage} the {@code mimetype}, {@code mediaSize}, and
     * {@code seconds} fields are populated; every other
     * {@link MediaProvider} variant receives only the common
     * {@code mediaSize} update.
     *
     * @param provider the upload target; codec-derived fields are applied
     *                 to this instance
     * @param source   the raw audio channel; not closed by this method
     * @param quality  the quality preset; selects AAC bitrate
     * @return the encoded M4A payload
     * @throws WhatsAppMediaException.Processing if demuxing, resampling,
     *         encoding, or muxing fails
     */
    public MediaPayload run(MediaProvider provider, SeekableByteChannel source,
                            SettingsSyncAction.MediaQualitySetting quality)
            throws WhatsAppMediaException.Processing {
        FFmpegLoader.ensureLoaded();
        var bitrate = quality == SettingsSyncAction.MediaQualitySetting.HD
                ? AAC_BITRATE_HD
                : AAC_BITRATE_STANDARD;
        try (var arena = Arena.ofShared()) {
            var result = transcode(arena, source, bitrate);
            var durationSeconds = Math.max(1, result.durationSeconds);
            long outputLength;
            try {
                outputLength = Files.size(result.path);
            } catch (IOException e) {
                throw new WhatsAppMediaException.Processing("failed to size audio output", e);
            }
            provider.setMediaSize(outputLength);
            if (provider instanceof AudioMessage audio) {
                audio.setMimetype("audio/mp4");
                audio.setSeconds(durationSeconds);
            }
            return new MediaPayload.OfPath(result.path, outputLength, true);
        }
    }

    /**
     * Drives the demux, resample, encode, and mux passes; returns the
     * encoded bytes and the duration computed from the input.
     *
     * @param arena   the shared arena
     * @param channel the source channel
     * @param bitrate the target AAC bitrate in bits per second
     * @return the encoded output path and duration
     * @throws WhatsAppMediaException.Processing if any stage fails
     */
    private static EncodedAudio transcode(Arena arena, SeekableByteChannel channel, int bitrate)
            throws WhatsAppMediaException.Processing {
        var bridge = new AvioReadBuffer(arena, channel);
        AvioWriteBuffer.FileSystem sink;
        try {
            sink = AvioWriteBuffer.ofFileSystem(arena);
        } catch (IOException e) {
            throw new WhatsAppMediaException.Processing("failed to open audio output", e);
        }
        var inFmtCtx = MemorySegment.NULL;
        var outFmtCtx = MemorySegment.NULL;
        var decCtx = MemorySegment.NULL;
        var encCtx = MemorySegment.NULL;
        var swr = MemorySegment.NULL;
        var inFrame = MemorySegment.NULL;
        var encFrame = MemorySegment.NULL;
        var encPacket = MemorySegment.NULL;
        var decPacket = MemorySegment.NULL;
        try {
            inFmtCtx = openInput(arena, bridge);
            var audioStream = pickStream(inFmtCtx, Ffmpeg.AVMEDIA_TYPE_AUDIO());
            if (audioStream < 0) {
                throw new WhatsAppMediaException.Processing("no audio stream in audio source");
            }
            var stream = streamPointer(inFmtCtx, audioStream);
            var params = AVStream.codecpar(stream);
            var codecId = AVCodecParameters.codec_id(params);
            var decoder = FFmpegError.requireNonNull("avcodec_find_decoder(" + codecId + ")",
                    Ffmpeg.avcodec_find_decoder(codecId));
            decCtx = FFmpegError.requireNonNull("avcodec_alloc_context3",
                    Ffmpeg.avcodec_alloc_context3(decoder));
            FFmpegError.check("avcodec_parameters_to_context",
                    Ffmpeg.avcodec_parameters_to_context(decCtx, params));
            FFmpegError.check("avcodec_open2",
                    Ffmpeg.avcodec_open2(decCtx, decoder, MemorySegment.NULL));
            var outFmtCtxPp = arena.allocate(ValueLayout.ADDRESS);
            FFmpegError.check("avformat_alloc_output_context2(ipod)",
                    Ffmpeg.avformat_alloc_output_context2(outFmtCtxPp, MemorySegment.NULL,
                            arena.allocateFrom("ipod"), MemorySegment.NULL));
            outFmtCtx = outFmtCtxPp.get(ValueLayout.ADDRESS, 0L)
                    .reinterpret(AVFormatContext.layout().byteSize());
            AVFormatContext.pb(outFmtCtx, sink.ioContext());
            var encoder = FFmpegError.requireNonNull("avcodec_find_encoder(AAC)",
                    Ffmpeg.avcodec_find_encoder(Ffmpeg.AV_CODEC_ID_AAC()));
            encCtx = FFmpegError.requireNonNull("avcodec_alloc_context3(aac)",
                    Ffmpeg.avcodec_alloc_context3(encoder));
            AVCodecContext.sample_rate(encCtx, OUTPUT_SAMPLE_RATE);
            AVCodecContext.sample_fmt(encCtx, Ffmpeg.AV_SAMPLE_FMT_FLTP());
            AVCodecContext.bit_rate(encCtx, bitrate);
            var encLayout = AVChannelLayout.allocate(arena);
            Ffmpeg.av_channel_layout_default(encLayout, OUTPUT_CHANNELS);
            Ffmpeg.av_channel_layout_copy(AVCodecContext.ch_layout(encCtx), encLayout);
            var tb = arena.allocate(8);
            tb.set(ValueLayout.JAVA_INT, 0L, 1);
            tb.set(ValueLayout.JAVA_INT, 4L, OUTPUT_SAMPLE_RATE);
            AVCodecContext.time_base(encCtx, tb);
            var output = AVFormatContext.oformat(outFmtCtx);
            if ((AVOutputFormat.flags(output) & 0x40 /* AVFMT_GLOBALHEADER */) != 0) {
                AVCodecContext.flags(encCtx,
                        AVCodecContext.flags(encCtx) | Ffmpeg.AV_CODEC_FLAG_GLOBAL_HEADER());
            }
            FFmpegError.check("avcodec_open2(aac)",
                    Ffmpeg.avcodec_open2(encCtx, encoder, MemorySegment.NULL));
            var muxStream = FFmpegError.requireNonNull("avformat_new_stream",
                    Ffmpeg.avformat_new_stream(outFmtCtx, encoder));
            FFmpegError.check("avcodec_parameters_from_context",
                    Ffmpeg.avcodec_parameters_from_context(AVStream.codecpar(muxStream), encCtx));
            AVStream.time_base(muxStream, tb);
            swr = openResampler(arena, decCtx, encLayout);
            FFmpegError.check("avformat_write_header",
                    Ffmpeg.avformat_write_header(outFmtCtx, MemorySegment.NULL));
            var frameSize = AVCodecContext.frame_size(encCtx);
            if (frameSize <= 0) {
                frameSize = DEFAULT_AAC_FRAME_SAMPLES;
            }
            encFrame = FFmpegError.requireNonNull("av_frame_alloc(enc)", Ffmpeg.av_frame_alloc());
            AVFrame.format(encFrame, Ffmpeg.AV_SAMPLE_FMT_FLTP());
            Ffmpeg.av_channel_layout_copy(AVFrame.ch_layout(encFrame), encLayout);
            AVFrame.sample_rate(encFrame, OUTPUT_SAMPLE_RATE);
            AVFrame.nb_samples(encFrame, frameSize);
            FFmpegError.check("av_frame_get_buffer(enc)",
                    Ffmpeg.av_frame_get_buffer(encFrame, 0));
            encPacket = FFmpegError.requireNonNull("av_packet_alloc(enc)",
                    Ffmpeg.av_packet_alloc());
            inFrame = FFmpegError.requireNonNull("av_frame_alloc(in)",
                    Ffmpeg.av_frame_alloc());
            decPacket = FFmpegError.requireNonNull("av_packet_alloc(in)",
                    Ffmpeg.av_packet_alloc());
            var encPts = decodeAndEncode(arena, inFmtCtx, audioStream, decCtx,
                    decPacket, inFrame, swr, encCtx, encFrame, encPacket,
                    outFmtCtx, frameSize);
            FFmpegError.check("av_write_trailer", Ffmpeg.av_write_trailer(outFmtCtx));
            var durationSeconds = (int) (encPts / OUTPUT_SAMPLE_RATE);
            Path outputPath;
            try {
                outputPath = sink.release();
            } catch (IOException e) {
                throw new WhatsAppMediaException.Processing("failed to flush audio output", e);
            }
            return new EncodedAudio(outputPath, durationSeconds);
        } finally {
            freePointer(encPacket, Ffmpeg::av_packet_free);
            freePointer(decPacket, Ffmpeg::av_packet_free);
            freePointer(encFrame, Ffmpeg::av_frame_free);
            freePointer(inFrame, Ffmpeg::av_frame_free);
            freePointer(swr, Ffmpeg::swr_free);
            freePointer(encCtx, Ffmpeg::avcodec_free_context);
            freePointer(decCtx, Ffmpeg::avcodec_free_context);
            if (outFmtCtx != MemorySegment.NULL) {
                Ffmpeg.avformat_free_context(outFmtCtx);
            }
            freePointer(inFmtCtx, Ffmpeg::avformat_close_input);
            sink.close();
            bridge.close();
        }
    }

    /**
     * Drives the demux/decode/resample/encode pipeline frame by frame
     * until the source is exhausted.
     *
     * @param arena       the shared arena
     * @param inFmtCtx    the open input demuxer
     * @param audioStream the picked audio stream index
     * @param decCtx      the open decoder context
     * @param decPacket   reusable packet for demux output
     * @param inFrame     reusable frame for decoder output
     * @param swr         the initialised resampler
     * @param encCtx      the open encoder context
     * @param encFrame    pre-allocated frame at the encoder's required
     *                    shape
     * @param encPacket   reusable packet for encoder output
     * @param outFmtCtx   the open output muxer
     * @param frameSize   AAC frame size in samples per channel
     * @return the final encoder PTS, equal to the total number of encoded
     *         samples
     * @throws WhatsAppMediaException.Processing if any stage fails
     */
    private static long decodeAndEncode(Arena arena, MemorySegment inFmtCtx, int audioStream,
                                          MemorySegment decCtx, MemorySegment decPacket,
                                          MemorySegment inFrame, MemorySegment swr,
                                          MemorySegment encCtx, MemorySegment encFrame,
                                          MemorySegment encPacket, MemorySegment outFmtCtx,
                                          int frameSize)
            throws WhatsAppMediaException.Processing {
        long encPts = 0;
        var eof = false;
        while (!eof) {
            var read = Ffmpeg.av_read_frame(inFmtCtx, decPacket);
            if (read < 0) {
                Ffmpeg.avcodec_send_packet(decCtx, MemorySegment.NULL);
                eof = true;
            } else if (AVPacket.stream_index(decPacket) != audioStream) {
                Ffmpeg.av_packet_unref(decPacket);
                continue;
            } else {
                var sent = Ffmpeg.avcodec_send_packet(decCtx, decPacket);
                Ffmpeg.av_packet_unref(decPacket);
                if (sent < 0 && !FFmpegError.isAgain(sent)) {
                    throw new WhatsAppMediaException.Processing(
                            "avcodec_send_packet failed: " + FFmpegError.describe(sent));
                }
            }
            while (true) {
                var got = Ffmpeg.avcodec_receive_frame(decCtx, inFrame);
                if (FFmpegError.isAgain(got)) {
                    break;
                }
                if (FFmpegError.isEof(got)) {
                    eof = true;
                    break;
                }
                if (got < 0) {
                    throw new WhatsAppMediaException.Processing(
                            "avcodec_receive_frame failed: " + FFmpegError.describe(got));
                }
                encPts = resampleAndEncode(arena, swr, inFrame, encCtx, encFrame,
                        encPacket, outFmtCtx, frameSize, encPts);
                Ffmpeg.av_frame_unref(inFrame);
            }
        }
        encPts = drainAndEncode(arena, swr, encCtx, encFrame, encPacket, outFmtCtx,
                frameSize, encPts);
        flushEncoder(encCtx, encPacket, outFmtCtx);
        return encPts;
    }

    /**
     * Pushes one decoded frame through the resampler, accumulates the
     * resulting samples, and dispatches every full AAC frame to the
     * encoder.
     *
     * @param arena     the shared arena
     * @param swr       the initialised resampler
     * @param inFrame   the decoded source frame
     * @param encCtx    the open encoder
     * @param encFrame  pre-allocated encoder input frame
     * @param encPacket reusable encoder output packet
     * @param outFmtCtx the open output muxer
     * @param frameSize AAC frame size in samples per channel
     * @param encPts    the current encoder PTS
     * @return the updated encoder PTS
     * @throws WhatsAppMediaException.Processing if any stage fails
     */
    private static long resampleAndEncode(Arena arena, MemorySegment swr,
                                            MemorySegment inFrame,
                                            MemorySegment encCtx, MemorySegment encFrame,
                                            MemorySegment encPacket, MemorySegment outFmtCtx,
                                            int frameSize, long encPts)
            throws WhatsAppMediaException.Processing {
        int produced;
        do {
            var outBufSamples = frameSize;
            var outChannels = AVCodecContext.ch_layout(encCtx);
            var channels = AVChannelLayout.nb_channels(outChannels);
            var outDataPtrs = arena.allocate((long) channels * ValueLayout.ADDRESS.byteSize());
            for (var c = 0; c < channels; c++) {
                var plane = arena.allocate((long) outBufSamples * Float.BYTES);
                outDataPtrs.setAtIndex(ValueLayout.ADDRESS, c, plane);
            }
            produced = FFmpegError.check("swr_convert",
                    Ffmpeg.swr_convert(swr, outDataPtrs, outBufSamples,
                            AVFrame.extended_data(inFrame), AVFrame.nb_samples(inFrame)));
            if (produced > 0) {
                AVFrame.nb_samples(encFrame, produced);
                AVFrame.pts(encFrame, encPts);
                FFmpegError.check("av_frame_make_writable",
                        Ffmpeg.av_frame_make_writable(encFrame));
                for (var c = 0; c < channels; c++) {
                    var dstPlane = AVFrame.data(encFrame).getAtIndex(ValueLayout.ADDRESS, c);
                    var srcPlane = outDataPtrs.getAtIndex(ValueLayout.ADDRESS, c)
                            .reinterpret((long) produced * Float.BYTES);
                    MemorySegment.copy(srcPlane, 0L,
                            dstPlane.reinterpret((long) produced * Float.BYTES), 0L,
                            (long) produced * Float.BYTES);
                }
                encPts += produced;
                pushFrameAndDrain(encCtx, encFrame, encPacket, outFmtCtx);
            }
        } while (produced > 0);
        return encPts;
    }

    /**
     * Drains the resampler at end-of-input and encodes the trailing
     * samples.
     *
     * @param arena     the shared arena
     * @param swr       the resampler
     * @param encCtx    the encoder
     * @param encFrame  reusable encoder input frame
     * @param encPacket reusable encoder output packet
     * @param outFmtCtx the muxer
     * @param frameSize AAC frame size
     * @param encPts    current encoder PTS
     * @return the updated encoder PTS
     * @throws WhatsAppMediaException.Processing if any stage fails
     */
    private static long drainAndEncode(Arena arena, MemorySegment swr,
                                         MemorySegment encCtx, MemorySegment encFrame,
                                         MemorySegment encPacket, MemorySegment outFmtCtx,
                                         int frameSize, long encPts)
            throws WhatsAppMediaException.Processing {
        int produced;
        do {
            var outBufSamples = frameSize;
            var channels = AVChannelLayout.nb_channels(AVCodecContext.ch_layout(encCtx));
            var outDataPtrs = arena.allocate((long) channels * ValueLayout.ADDRESS.byteSize());
            for (var c = 0; c < channels; c++) {
                var plane = arena.allocate((long) outBufSamples * Float.BYTES);
                outDataPtrs.setAtIndex(ValueLayout.ADDRESS, c, plane);
            }
            produced = FFmpegError.check("swr_convert(flush)",
                    Ffmpeg.swr_convert(swr, outDataPtrs, outBufSamples,
                            MemorySegment.NULL, 0));
            if (produced > 0) {
                AVFrame.nb_samples(encFrame, produced);
                AVFrame.pts(encFrame, encPts);
                FFmpegError.check("av_frame_make_writable",
                        Ffmpeg.av_frame_make_writable(encFrame));
                for (var c = 0; c < channels; c++) {
                    var dstPlane = AVFrame.data(encFrame).getAtIndex(ValueLayout.ADDRESS, c);
                    var srcPlane = outDataPtrs.getAtIndex(ValueLayout.ADDRESS, c)
                            .reinterpret((long) produced * Float.BYTES);
                    MemorySegment.copy(srcPlane, 0L,
                            dstPlane.reinterpret((long) produced * Float.BYTES), 0L,
                            (long) produced * Float.BYTES);
                }
                encPts += produced;
                pushFrameAndDrain(encCtx, encFrame, encPacket, outFmtCtx);
            }
        } while (produced > 0);
        return encPts;
    }

    /**
     * Sends one frame to the encoder and drains every produced packet to
     * the muxer.
     *
     * @param encCtx    the encoder
     * @param encFrame  the input frame, or {@code NULL} for end-of-stream
     * @param encPacket reusable output packet
     * @param outFmtCtx the muxer
     * @throws WhatsAppMediaException.Processing if any call fails
     */
    private static void pushFrameAndDrain(MemorySegment encCtx, MemorySegment encFrame,
                                            MemorySegment encPacket, MemorySegment outFmtCtx)
            throws WhatsAppMediaException.Processing {
        var sent = Ffmpeg.avcodec_send_frame(encCtx, encFrame);
        if (sent < 0 && !FFmpegError.isAgain(sent)) {
            throw new WhatsAppMediaException.Processing(
                    "avcodec_send_frame failed: " + FFmpegError.describe(sent));
        }
        while (true) {
            var got = Ffmpeg.avcodec_receive_packet(encCtx, encPacket);
            if (FFmpegError.isAgain(got) || FFmpegError.isEof(got)) {
                return;
            }
            if (got < 0) {
                throw new WhatsAppMediaException.Processing(
                        "avcodec_receive_packet failed: " + FFmpegError.describe(got));
            }
            AVPacket.stream_index(encPacket, 0);
            var written = Ffmpeg.av_interleaved_write_frame(outFmtCtx, encPacket);
            Ffmpeg.av_packet_unref(encPacket);
            if (written < 0) {
                throw new WhatsAppMediaException.Processing(
                        "av_interleaved_write_frame failed: " + FFmpegError.describe(written));
            }
        }
    }

    /**
     * Flushes the encoder at end-of-input by sending a {@code NULL} frame.
     *
     * @param encCtx    the encoder
     * @param encPacket reusable output packet
     * @param outFmtCtx the muxer
     * @throws WhatsAppMediaException.Processing if any call fails
     */
    private static void flushEncoder(MemorySegment encCtx, MemorySegment encPacket,
                                       MemorySegment outFmtCtx)
            throws WhatsAppMediaException.Processing {
        pushFrameAndDrain(encCtx, MemorySegment.NULL, encPacket, outFmtCtx);
    }

    /**
     * Allocates and initialises the {@code SwrContext} that converts the
     * decoder's native format to the encoder's planar float 48 kHz stereo
     * format.
     *
     * @param arena    the shared arena
     * @param decCtx   the open decoder
     * @param encLayout the encoder's channel layout
     * @return the initialised resampler
     */
    private static MemorySegment openResampler(Arena arena, MemorySegment decCtx,
                                                 MemorySegment encLayout) {
        var swrPp = arena.allocate(ValueLayout.ADDRESS);
        FFmpegError.check("swr_alloc_set_opts2",
                Ffmpeg.swr_alloc_set_opts2(swrPp, encLayout,
                        Ffmpeg.AV_SAMPLE_FMT_FLTP(), OUTPUT_SAMPLE_RATE,
                        AVCodecContext.ch_layout(decCtx),
                        AVCodecContext.sample_fmt(decCtx),
                        AVCodecContext.sample_rate(decCtx),
                        0, MemorySegment.NULL));
        var swr = FFmpegError.requireNonNull("swr_alloc_set_opts2 out",
                swrPp.get(ValueLayout.ADDRESS, 0L));
        FFmpegError.check("swr_init", Ffmpeg.swr_init(swr));
        return swr;
    }

    /**
     * Opens the source via the supplied AVIO bridge.
     *
     * @param arena  the shared arena
     * @param bridge the AVIO read bridge
     * @return the open demuxer
     */
    private static MemorySegment openInput(Arena arena, AvioReadBuffer bridge) {
        var formatCtx = FFmpegError.requireNonNull("avformat_alloc_context",
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
        return formatCtx;
    }

    /**
     * Returns the index of the first stream of the given media type.
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
     * Returns the i-th stream pointer.
     *
     * @param formatCtx the open demuxer
     * @param index     the zero-based stream index
     * @return the stream pointer
     */
    private static MemorySegment streamPointer(MemorySegment formatCtx, int index) {
        return AVFormatContext.streams(formatCtx)
                .getAtIndex(ValueLayout.ADDRESS, index)
                .reinterpret(AVStream.layout().byteSize());
    }

    /**
     * Calls the given {@code av_*_free} variant on a pointer slot if the
     * pointer is non-NULL.
     *
     * @apiNote
     * Wraps the standard FFmpeg pattern of allocating a single
     * pointer-to-pointer slot in a confined arena, stuffing the pointer
     * into it, and handing the slot to libav so libav can null the field
     * out after free.
     *
     * @param ptr   the pointer to free; ignored when {@code NULL}
     * @param freer the libav free function accepting a pointer-to-pointer
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
     * Functional handle to one of libav's pointer-slot {@code free}
     * functions.
     */
    @FunctionalInterface
    private interface FreePointer {
        /**
         * Calls the libav free function on the given pointer slot.
         *
         * @param pp pointer-to-pointer slot to free
         */
        void free(MemorySegment pp);
    }

    /**
     * Result of the encode + mux passes.
     *
     * @param path            the encoded output file
     * @param durationSeconds the duration of the output in whole seconds
     */
    private record EncodedAudio(Path path, int durationSeconds) {
    }
}
