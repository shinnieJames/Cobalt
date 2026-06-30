package com.github.auties00.cobalt.calls2.stream;

import java.net.URI;
import java.time.Duration;

/**
 * Transmits the video track of a media stream addressed by a URI as the local video of a call.
 *
 * <p>This is the device-backed {@link BufferedVideoOutput} returned by
 * {@link BufferedVideoOutput#uri(URI, Duration)}. It generalizes {@link FileVideoOutput} to any protocol
 * the bundled FFmpeg build enables, opening the stream through {@link FfmpegUriOpener} and handing the
 * opened demuxer to {@link FfmpegVideoOutput}, which decodes its first video stream and converts each
 * picture to {@link VideoPixelFormat#I420 I420} at the detected geometry. The geometry the call engine
 * advertises and encodes at is the stream's own native video resolution, capped to {@code 1280} on the
 * longer side and rounded to even, so a 16:9 stream is advertised as 16:9 rather than squished to a fixed
 * default.
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
     * Opens the given URI, detects its native video geometry, and prepares the shared video decode
     * pipeline, bounding every blocking demux call with the given timeout.
     *
     * <p>Advertises the stream's detected video resolution, capped to {@code 1280} on the longer side and
     * rounded to even, at the default 30 frames per second and the recovered initial bitrate.
     *
     * @param uri       the media stream to open
     * @param ioTimeout the maximum time any single connect, probe, or read may block; must be positive
     * @throws NullPointerException     if {@code uri} or {@code ioTimeout} is {@code null}
     * @throws IllegalArgumentException if {@code ioTimeout} is not positive or the scheme is not permitted
     * @throws IllegalStateException    if the stream cannot be opened or has no video stream
     */
    public UriVideoOutput(URI uri, Duration ioTimeout) {
        super(openInput(ioTimeout, (arena, watchdog) -> FfmpegUriOpener.open(arena, watchdog, uri, ioTimeout)),
                DEFAULT_BITRATE_BPS);
    }
}
