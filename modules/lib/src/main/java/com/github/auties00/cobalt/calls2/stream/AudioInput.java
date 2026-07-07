package com.github.auties00.cobalt.calls2.stream;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Defines the remote inbound audio sink of a call: the app-supplied destination for the
 * {@link AudioFrame}s the call engine decodes from the peer.
 *
 * <p>This is the read side of a call's audio. The engine decodes each audio packet received from the
 * peer and {@linkplain #offer(AudioFrame) delivers} the resulting frame to this sink; the embedder
 * decides what becomes of it. The contract has two faces. The engine-facing face is
 * {@link #offer(AudioFrame)} and {@link #shutdown()}, by which the engine fills the sink and signals
 * end-of-stream. The application-facing face is {@link #read()}, by which a programmatic consumer (a
 * bot or a call-to-call bridge) pulls received frames to forward or analyse them; a device-backed sink
 * instead renders each frame to its playback device inside {@link #offer(AudioFrame)} and is not read
 * from.
 *
 * <p>Frames carry mono 16-bit PCM at 16 kHz as described by {@link AudioFrame}. An implementation
 * decides its own buffering policy between the engine fill and the consumer; a buffered sink typically
 * prefers freshness, dropping the oldest buffered frame rather than stalling the decoder when the
 * consumer falls behind, so playback latency stays bounded. The application never ends the sink
 * itself; the engine invokes {@link #shutdown()} when the call ends, which an implementation uses to
 * finalize or release any device it bound.
 *
 * @apiNote An embedder implements this interface to consume or render received audio, or obtains a
 * bundled buffered or device-backed implementation from one of the factories on this type:
 * {@link #buffered()} for a manually-read sink, {@link #toSpeaker()} to render to the speaker, and
 * {@link #wav(Path)} to record to a WAV file. The {@link #offer(AudioFrame)} and {@link #shutdown()}
 * methods belong to the engine; application code drives a programmatic sink through {@link #read()} and
 * never calls the engine-facing pair directly.
 */
public interface AudioInput {
    /**
     * Returns a manually-read buffered sink the application drains with {@link #read()}.
     *
     * <p>This is the path a bot, a call-to-call bridge, or any programmatic consumer uses: the engine
     * fills the sink through {@link #offer(AudioFrame)} and the application pulls the buffered frames with
     * {@link #read()}, the oldest frame dropped when the consumer falls behind so playback latency stays
     * bounded.
     *
     * @return a new empty buffered sink
     */
    static AudioInput buffered() {
        return new BufferedAudioInput();
    }

    /**
     * Returns a sink bound to the operating-system speaker.
     *
     * <p>Each {@link #offer(AudioFrame)} renders the frame to the default output device, blocking while
     * the line buffer is full, until the call ends and the playback line is released. The application does
     * not read a speaker-bound sink.
     *
     * @return a speaker-bound sink
     * @throws IllegalStateException if no playback line is available on the running platform
     */
    static AudioInput toSpeaker() {
        return new SpeakerAudioInput();
    }

    /**
     * Returns a sink that records the received audio to a WAV file.
     *
     * <p>Each {@link #offer(AudioFrame)} appends the frame to the file; the file is finalized when the
     * call ends. The application does not read a file-bound sink.
     *
     * @param path the WAV file to write
     * @return a file-bound sink
     * @throws NullPointerException  if {@code path} is {@code null}
     * @throws IllegalStateException if the file cannot be created
     */
    static AudioInput wav(Path path) {
        Objects.requireNonNull(path, "path cannot be null");
        return new WavFileAudioInput(path);
    }

    /**
     * Delivers one decoded remote frame for the application to consume.
     *
     * <p>Invoked by the engine for each frame it decodes from the peer. A buffered sink enqueues the
     * frame for {@link #read()}, dropping the oldest buffered frame if the consumer is behind; a
     * device-backed sink renders the frame straight to its playback device or file. After
     * {@link #shutdown()} has run an implementation discards the frame. The frame is never
     * {@code null}.
     *
     * @param frame the decoded frame; never {@code null}
     * @throws NullPointerException if {@code frame} is {@code null}
     */
    void offer(AudioFrame frame);

    /**
     * Returns the next frame of received remote audio, blocking until one is available, or
     * {@code null} once the call has ended.
     *
     * <p>Returns frames previously delivered through {@link #offer(AudioFrame)} in order. The method
     * blocks while no frame is ready and returns {@code null} exactly once the sink has been
     * {@linkplain #shutdown() ended} and drained. A device-backed sink renders inside
     * {@link #offer(AudioFrame)} and is not read from.
     *
     * <p>The returned frame's {@linkplain AudioFrame#pcm() sample buffer} is borrowed from a pool the
     * engine reuses across frames: it is valid only until the next call to this method on the same
     * input, after which the engine may refill and re-offer it. A consumer that needs the samples beyond
     * the next read copies them out; it must never retain the returned array past the next read nor
     * mutate it.
     *
     * @return the next frame, or {@code null} at end-of-stream
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    AudioFrame read() throws InterruptedException;

    /**
     * Ends the sink, unblocking a pending {@link #read()} and finalizing any bound device.
     *
     * <p>Invoked by the engine when the call ends. After it runs, {@link #read()} returns {@code null}
     * once drained and the implementation finalizes or releases any playback device or file it held.
     * Implementations make this idempotent, since the engine may signal teardown more than once during
     * a racing shutdown.
     */
    void shutdown();
}
