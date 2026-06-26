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
 * {@link VideoPixelFormat#I420 I420} at the advertised geometry. The geometry passed to {@code super} is
 * the resolution, frame rate, and bitrate the call engine advertises and encodes at, independent of the
 * file's native video resolution. A local file read does not stall, so this source configures no read
 * timeout and does not arm the demux watchdog; {@link UriVideoOutput} is the timeout-bounded sibling for
 * network inputs.
 *
 * @implNote This implementation is the calls2 port of the legacy media-file video source; each converted
 * picture the base produces carries the {@link VideoFrame#ptsMicros()} microsecond presentation timestamp
 * rescaled from the stream time base, rather than the legacy millisecond clock.
 */
public final class FileVideoOutput extends FfmpegVideoOutput {
    /**
     * Opens the given media file at the given advertised geometry and prepares the shared video decode
     * pipeline.
     *
     * <p>Passes a {@code null} read timeout to the base, since a local file read does not block on the
     * network, and an opener that opens and probes the file with libavformat.
     *
     * @param path       the media file to open
     * @param width      the advertised frame width in pixels; even and at least {@code 2}
     * @param height     the advertised frame height in pixels; even and at least {@code 2}
     * @param fps        the target frame rate; at least {@code 1}
     * @param bitrateBps the target encoder bitrate in bits per second; at least {@code 1}
     * @throws NullPointerException     if {@code path} is {@code null}
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below {@code 2}, or
     *                                  {@code fps} or {@code bitrateBps} is below {@code 1}
     * @throws IllegalStateException    if the file cannot be opened or has no video stream
     */
    public FileVideoOutput(Path path, int width, int height, int fps, int bitrateBps) {
        super(width, height, fps, bitrateBps, null, (arena, watchdog) -> openFile(arena, path));
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
