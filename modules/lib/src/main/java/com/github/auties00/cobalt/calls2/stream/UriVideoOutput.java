package com.github.auties00.cobalt.calls2.stream;

import java.net.URI;
import java.time.Duration;

/**
 * Transmits the video track of a media stream addressed by a URI as the local video of a call.
 *
 * <p>This is the device-backed {@link BufferedVideoOutput} returned by
 * {@link BufferedVideoOutput#uri(URI, int, int, int, int, Duration)}. It generalizes {@link FileVideoOutput}
 * to any protocol the bundled FFmpeg build enables, opening the stream through {@link FfmpegUriOpener} and
 * handing the opened demuxer to {@link FfmpegVideoOutput}, which decodes its first video stream and
 * converts each picture to {@link VideoPixelFormat#I420 I420} at the advertised geometry. The geometry
 * passed to {@code super} is the resolution, frame rate, and bitrate the call engine advertises and
 * encodes at, independent of the stream's native video resolution.
 *
 * <p>Because a network operation can stall, every blocking demux call is bounded by a
 * {@link FfmpegIoWatchdog}: the connect and stream probe inside the open and each subsequent read are
 * armed with the configured timeout, and an aborted read is surfaced as an {@link IllegalStateException}
 * from {@link #take()} rather than mistaken for a clean end-of-input. The accepted schemes are restricted
 * to a fixed allowlist by {@link FfmpegUriOpener}, so an application-supplied string cannot reach an
 * unintended protocol.
 */
public final class UriVideoOutput extends FfmpegVideoOutput {
    /**
     * Opens the given URI at the given advertised geometry and prepares the shared video decode pipeline,
     * bounding every blocking demux call with the given timeout.
     *
     * @param uri        the media stream to open
     * @param width      the advertised frame width in pixels; even and at least {@code 2}
     * @param height     the advertised frame height in pixels; even and at least {@code 2}
     * @param fps        the target frame rate; at least {@code 1}
     * @param bitrateBps the target encoder bitrate in bits per second; at least {@code 1}
     * @param ioTimeout  the maximum time any single connect, probe, or read may block; must be positive
     * @throws NullPointerException     if {@code uri} or {@code ioTimeout} is {@code null}
     * @throws IllegalArgumentException if {@code ioTimeout} is not positive, the scheme is not permitted,
     *                                  {@code width} or {@code height} is odd or below {@code 2}, or
     *                                  {@code fps} or {@code bitrateBps} is below {@code 1}
     * @throws IllegalStateException    if the stream cannot be opened or has no video stream
     */
    public UriVideoOutput(URI uri, int width, int height, int fps, int bitrateBps, Duration ioTimeout) {
        super(width, height, fps, bitrateBps, ioTimeout,
                (arena, watchdog) -> FfmpegUriOpener.open(arena, watchdog, uri, ioTimeout));
    }
}
