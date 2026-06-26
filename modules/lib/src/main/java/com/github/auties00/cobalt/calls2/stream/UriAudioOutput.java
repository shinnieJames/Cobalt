package com.github.auties00.cobalt.calls2.stream;

import java.net.URI;
import java.time.Duration;

/**
 * Transmits the audio track of a media stream addressed by a URI as the local audio of a call.
 *
 * <p>This is the device-backed {@link AudioOutput} returned by {@link AudioOutput#uri(URI, Duration)}. It
 * generalizes {@link FileAudioOutput} to any protocol the bundled FFmpeg build enables, opening the
 * stream through {@link FfmpegUriOpener} and handing the opened demuxer to {@link FfmpegAudioOutput},
 * which decodes its first audio stream, resamples each frame to 16 kHz mono signed 16-bit PCM, and paces
 * emission to wall-clock real time. A faster-than-real-time source, such as an {@code http:} file, is
 * paced to the call's send rate exactly as a local file is; a live source, such as an {@code rtsp:}
 * stream, is paced by its own arrival rate, in which case the base's pacing returns each frame as soon as
 * it is decoded.
 *
 * <p>Because a network operation can stall, every blocking demux call is bounded by a
 * {@link FfmpegIoWatchdog}: the connect and stream probe inside the open and each subsequent read are
 * armed with the configured timeout, and an aborted read is surfaced as an {@link IllegalStateException}
 * from {@link #take()} rather than mistaken for a clean end-of-input. The accepted schemes are restricted
 * to a fixed allowlist by {@link FfmpegUriOpener}, so an application-supplied string cannot reach an
 * unintended protocol.
 */
public final class UriAudioOutput extends FfmpegAudioOutput {
    /**
     * Opens the given URI and prepares the shared demux, decode, and resample chain, bounding every
     * blocking demux call with the given timeout.
     *
     * @param uri       the media stream to open
     * @param ioTimeout the maximum time any single connect, probe, or read may block; must be positive
     * @throws NullPointerException     if {@code uri} or {@code ioTimeout} is {@code null}
     * @throws IllegalArgumentException if {@code ioTimeout} is not positive, or the scheme is not permitted
     * @throws IllegalStateException    if the stream cannot be opened or has no audio stream
     */
    public UriAudioOutput(URI uri, Duration ioTimeout) {
        super(ioTimeout, (arena, watchdog) -> FfmpegUriOpener.open(arena, watchdog, uri, ioTimeout));
    }
}
