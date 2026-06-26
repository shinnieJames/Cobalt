package com.github.auties00.cobalt.calls2.stream;

import com.github.auties00.cobalt.util.ffmpeg.AVFormatContext;
import com.github.auties00.cobalt.util.ffmpeg.Ffmpeg;
import com.github.auties00.cobalt.util.ffmpeg.FFmpegError;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Transmits the audio track of a local media file as the local audio of a call.
 *
 * <p>This is the device-backed {@link AudioOutput} returned by {@link AudioOutput#file(Path)}. It opens
 * the file with libavformat and hands the opened demuxer to {@link FfmpegAudioOutput}, which decodes its
 * first audio stream, resamples each frame to 16 kHz mono signed 16-bit PCM, and paces emission to
 * wall-clock real time. Any container or codec the bundled FFmpeg build enables a decoder for is
 * supported, which by default covers WAV, FLAC, MP3, AAC, Opus, Vorbis, MP4, MKV, and OGG. A local file
 * read does not stall, so this source configures no read timeout and does not arm the demux watchdog;
 * {@link UriAudioOutput} is the timeout-bounded sibling for network inputs.
 *
 * @implNote This implementation is the calls2 port of the legacy media-file capture stream; the
 * presentation timestamp the base advances per frame is denominated in the {@link AudioFrame#ptsMicros()}
 * microsecond clock rather than the legacy millisecond clock.
 */
public final class FileAudioOutput extends FfmpegAudioOutput {
    /**
     * Opens the given media file and prepares the shared demux, decode, and resample chain.
     *
     * <p>Passes a {@code null} read timeout to the base, since a local file read does not block on the
     * network, and an opener that opens and probes the file with libavformat.
     *
     * @param path the media file to open
     * @throws NullPointerException  if {@code path} is {@code null}
     * @throws IllegalStateException if the file cannot be opened or has no audio stream
     */
    public FileAudioOutput(Path path) {
        super(null, (arena, watchdog) -> openFile(arena, path));
    }

    /**
     * Opens and probes the given media file, returning its demuxer context.
     *
     * <p>The watchdog is unused: a local file read does not stall, so no interrupt callback is installed.
     *
     * @param arena the source's lifetime arena that owns the native allocations the open makes
     * @param path  the media file to open
     * @return the opened and probed {@code AVFormatContext} pointer
     * @throws NullPointerException  if {@code path} is {@code null}
     * @throws IllegalStateException if the file cannot be opened or probed
     */
    private static MemorySegment openFile(Arena arena, Path path) {
        Objects.requireNonNull(path, "path cannot be null");
        var formatPtr = arena.allocate(ValueLayout.ADDRESS);
        var url = arena.allocateFrom(path.toAbsolutePath().toString());
        FFmpegError.check("avformat_open_input(" + path + ")",
                Ffmpeg.avformat_open_input(formatPtr, url, MemorySegment.NULL, MemorySegment.NULL));
        var formatCtx = formatPtr.get(ValueLayout.ADDRESS, 0L)
                .reinterpret(AVFormatContext.layout().byteSize());
        FFmpegError.check("avformat_find_stream_info",
                Ffmpeg.avformat_find_stream_info(formatCtx, MemorySegment.NULL));
        return formatCtx;
    }
}
