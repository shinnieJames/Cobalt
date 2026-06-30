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
 * Transmits the video track of a local media file as the local video of a call.
 *
 * <p>This is the device-backed {@link BufferedVideoOutput} returned by
 * {@link BufferedVideoOutput#file(Path)}. It opens the file with libavformat and hands the opened demuxer
 * to {@link FfmpegVideoOutput}, which decodes its first video stream and converts each picture to
 * {@link VideoPixelFormat#I420 I420} at the detected geometry. The geometry the call engine advertises and
 * encodes at is the file's own native video resolution, capped to {@code 1280} on the longer side and
 * rounded to even, so a 16:9 file is advertised as 16:9 rather than squished to a fixed default. A local
 * file read does not stall, so this source configures no read timeout and does not arm the demux watchdog;
 * {@link UriVideoOutput} is the timeout-bounded sibling for network inputs.
 *
 * @implNote This implementation is the calls2 port of the legacy media-file video source; each converted
 * picture the base produces carries the {@link VideoFrame#ptsMicros()} microsecond presentation timestamp
 * rescaled from the stream time base, rather than the legacy millisecond clock.
 */
public final class FileVideoOutput extends FfmpegVideoOutput {
    /**
     * Opens the given media file, detects its native video geometry, and prepares the shared video decode
     * pipeline.
     *
     * <p>Opens and probes the file with a {@code null} read timeout, since a local file read does not block
     * on the network, then advertises the file's detected video resolution, capped to {@code 1280} on the
     * longer side and rounded to even, at the default 30 frames per second and the recovered initial
     * bitrate. The detected geometry replaces any fixed default, so a 16:9 file is advertised as 16:9.
     *
     * @param path the media file to open
     * @throws NullPointerException  if {@code path} is {@code null}
     * @throws IllegalStateException if the file cannot be opened or has no video stream
     */
    public FileVideoOutput(Path path) {
        super(openInput(null, (arena, watchdog) -> openFile(arena, path)), DEFAULT_BITRATE_BPS);
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
