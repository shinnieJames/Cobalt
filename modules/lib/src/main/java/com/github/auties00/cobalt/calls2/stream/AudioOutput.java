package com.github.auties00.cobalt.calls2.stream;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Defines the local outbound audio source of a call: the app-supplied origin of the {@link AudioFrame}s
 * the call engine encodes and transmits to the peer.
 *
 * <p>This is the write side of a call's audio. An embedder supplies one of these when placing or
 * accepting a call; the engine repeatedly {@linkplain #take() pulls} frames from it on a dedicated
 * virtual thread, encodes each with the negotiated audio codec, and ships it to the peer. The contract
 * has two faces. The application-facing face is {@link #write(AudioFrame)}, by which a programmatic
 * producer (a bot, a call-to-call bridge, or a synthetic generator) pushes the audio it wants
 * transmitted. The engine-facing face is {@link #take()} and {@link #shutdown()}: the engine drains
 * frames and is signalled end-of-stream. A device-backed source instead fills itself inside its
 * {@link #take()} from a capture device and ignores {@link #write(AudioFrame)}.
 *
 * <p>Frames carry mono 16-bit PCM at 16 kHz as described by {@link AudioFrame}. An implementation
 * decides its own buffering and backpressure policy between the producer and the engine drain; the
 * engine itself only requires that {@link #take()} block until a frame is available or the source has
 * ended, and that {@link #isLiveCapture()} truthfully report whether the audio is live acoustic
 * capture so the engine can apply acoustic conditioning only where it is warranted. The application
 * never ends the source itself; the engine invokes {@link #shutdown()} when the call ends, which an
 * implementation uses to release any device it bound.
 *
 * @apiNote An embedder implements this interface for a custom audio source, or obtains a bundled
 * buffered or device-backed implementation from one of the factories on this type: {@link #buffered()}
 * for a manually-written source, {@link #fromMicrophone()} for live capture, {@link #file(Path)} for a
 * local media file, {@link #uri(URI)} for a media stream addressed by URI, and {@link #tone(double)} or
 * {@link #silence()} for synthetic audio. The {@link #take()}
 * and {@link #shutdown()} methods belong to the engine; application code drives a programmatic source
 * through {@link #write(AudioFrame)} and never calls the engine-facing pair directly.
 */
public interface AudioOutput {
    /**
     * Returns a manually-written buffered source the application fills with {@link #write(AudioFrame)}.
     *
     * <p>This is the path a bot, a call-to-call bridge, or any programmatic producer uses: the engine
     * drains the written frames through {@link #take()} on its own thread. The returned source reports
     * {@link #isLiveCapture()} as {@code false}, since application-written frames are clean line-level
     * audio the engine encodes without acoustic conditioning.
     *
     * @return a new empty buffered source
     */
    static AudioOutput buffered() {
        return new BufferedAudioOutput();
    }

    /**
     * Returns a source bound to the operating-system microphone.
     *
     * <p>Each {@link #take()} captures one 16 kHz mono frame from the default microphone, blocking on the
     * capture line until a full frame is available, until the call ends and the capture line is released.
     * The application does not write to a microphone-bound source, and the source reports
     * {@link #isLiveCapture()} as {@code true} so the engine applies echo cancellation and microphone
     * conditioning.
     *
     * @return a microphone-bound source
     * @throws IllegalStateException if no capture line is available on the running platform
     */
    static AudioOutput fromMicrophone() {
        return new MicrophoneAudioOutput();
    }

    /**
     * Returns a source that transmits the audio track of a media file.
     *
     * <p>Each {@link #take()} decodes and resamples the next 16 kHz mono frame of the file; the source
     * ends when the file is exhausted or the call ends. Any container the bundled FFmpeg build can decode
     * is accepted.
     *
     * @param path the media file to stream
     * @return a file-bound source
     * @throws NullPointerException  if {@code path} is {@code null}
     * @throws IllegalStateException if the file cannot be opened or has no audio stream
     */
    static AudioOutput file(Path path) {
        Objects.requireNonNull(path, "path cannot be null");
        return new FileAudioOutput(path);
    }

    /**
     * Returns a source that transmits the audio track of a media stream addressed by a URI, with a
     * fifteen-second timeout on every blocking network operation.
     *
     * <p>Generalizes {@link #file(Path)} to any protocol the bundled FFmpeg build enables: in addition to
     * a local {@code file:} path, an {@code http:}/{@code https:} asset, an {@code rtsp:}/{@code rtmp:}/
     * {@code srt:} live stream, and so on. A faster-than-real-time source is paced to the call's real-time
     * send rate exactly as a local file is; a live source is paced by its own arrival rate. The accepted
     * schemes are restricted to a fixed allowlist, so an application-supplied string cannot reach an
     * unintended protocol.
     *
     * @param uri the media stream to open
     * @return a URI-bound source
     * @throws NullPointerException     if {@code uri} is {@code null}
     * @throws IllegalArgumentException if the URI has no scheme or its scheme is not permitted
     * @throws IllegalStateException    if the stream cannot be opened or has no audio stream
     */
    static AudioOutput uri(URI uri) {
        return uri(uri, Duration.ofSeconds(15));
    }

    /**
     * Returns a source that transmits the audio track of a media stream addressed by a URI, bounding every
     * blocking network operation by the given timeout.
     *
     * <p>Behaves as {@link #uri(URI)} but takes the timeout explicitly: if a connect, stream probe, or
     * read makes no progress within {@code ioTimeout}, the operation is aborted and the source ends with an
     * error rather than stalling the call's encode thread. A short timeout fails fast on a dead host; a
     * longer one tolerates a slow link.
     *
     * @param uri       the media stream to open
     * @param ioTimeout the maximum time any single connect, probe, or read may block; must be positive
     * @return a URI-bound source
     * @throws NullPointerException     if {@code uri} or {@code ioTimeout} is {@code null}
     * @throws IllegalArgumentException if {@code ioTimeout} is not positive, the URI has no scheme, or its
     *                                  scheme is not permitted
     * @throws IllegalStateException    if the stream cannot be opened or has no audio stream
     */
    static AudioOutput uri(URI uri, Duration ioTimeout) {
        Objects.requireNonNull(uri, "uri cannot be null");
        Objects.requireNonNull(ioTimeout, "ioTimeout cannot be null");
        if (ioTimeout.isNegative() || ioTimeout.isZero()) {
            throw new IllegalArgumentException("ioTimeout must be positive, got " + ioTimeout);
        }
        return new UriAudioOutput(uri, ioTimeout);
    }

    /**
     * Returns a source that transmits continuous silence.
     *
     * @return a silence-bound source
     */
    static AudioOutput silence() {
        return new SilenceAudioOutput();
    }

    /**
     * Returns a source that transmits a constant sine tone, useful for diagnostics.
     *
     * @param frequencyHz the tone frequency in hertz
     * @return a tone-bound source
     * @throws IllegalArgumentException if {@code frequencyHz} is not positive
     */
    static AudioOutput tone(double frequencyHz) {
        if (frequencyHz <= 0) {
            throw new IllegalArgumentException("frequencyHz must be positive, got " + frequencyHz);
        }
        return new ToneAudioOutput(frequencyHz);
    }

    /**
     * Writes one frame of local audio to transmit.
     *
     * <p>Offers the frame to the engine for encoding and transmission. An implementation chooses
     * whether a full internal buffer blocks the caller (backpressure) or drops the oldest frame, and
     * what happens after {@link #shutdown()} has run; the only universal requirement is that the frame
     * is never {@code null}. A device-backed source produces frames inside {@link #take()} and may
     * treat this method as a no-op.
     *
     * @param frame the frame to transmit; never {@code null}
     * @throws NullPointerException if {@code frame} is {@code null}
     * @throws InterruptedException if the calling thread is interrupted while waiting for buffer space
     */
    void write(AudioFrame frame) throws InterruptedException;

    /**
     * Returns the next frame for the engine to encode, blocking until one is available, or
     * {@code null} once the source has ended.
     *
     * <p>A buffered source returns frames previously supplied through {@link #write(AudioFrame)}; a
     * device-backed source pulls the next frame straight from its capture device or decoder. The
     * method blocks while no frame is ready and returns {@code null} exactly once the source is
     * permanently drained, after which the engine stops pulling.
     *
     * @return the next frame, or {@code null} at end-of-stream
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    AudioFrame take() throws InterruptedException;

    /**
     * Ends the source, unblocking a pending {@link #take()} and releasing any bound device.
     *
     * <p>Invoked by the engine when the call ends. After it runs, {@link #take()} returns {@code null}
     * and the implementation releases any capture device or decoder it held. Implementations make this
     * idempotent, since the engine may signal teardown more than once during a racing shutdown.
     */
    void shutdown();

    /**
     * Returns whether this source delivers live acoustic audio captured from a microphone.
     *
     * <p>Live microphone capture carries acoustic echo, where the remote party's audio leaks from the
     * local speaker back into the microphone, together with ambient noise, so the engine conditions it
     * with echo cancellation, denoise, automatic gain control, and voice-activity detection. Every
     * other source (a media file, a synthetic tone, silence, or frames an application writes through
     * {@link #write(AudioFrame)}) is already clean line-level audio, and running microphone
     * conditioning over it distorts the signal, so the engine encodes those sources without
     * preprocessing. An implementation returns {@code true} only for a true live microphone capture.
     *
     * @return {@code true} if the source is live microphone capture needing acoustic conditioning,
     *         otherwise {@code false}
     */
    boolean isLiveCapture();
}
