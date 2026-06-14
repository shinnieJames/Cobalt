package com.github.auties00.cobalt.call.stream;

import com.github.auties00.cobalt.call.stream.playback.SpeakerAudioInputStream;
import com.github.auties00.cobalt.call.stream.playback.WavFileAudioInputStream;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Carries the remote inbound audio of a call as a stream of {@link AudioFrame}s.
 *
 * <p>This is the read side of a call's audio: the call engine decodes each Opus packet received from
 * the peer and fills the stream, and the application consumes it. There are two ways to consume. The
 * application can pull frames itself by calling {@link #read()}, which is the path a bot or a
 * call-to-call bridge uses to forward or analyse received audio; this base class is exactly that
 * manually-read stream, backed by a small bounded buffer. Alternatively a static factory returns a
 * device-backed subclass, such as {@link #toSpeaker()} or {@link #toWav(Path)}, whose
 * {@link #offer(AudioFrame)} renders each frame straight to the device and which the application reads
 * nothing from.
 *
 * <p>Frames carry mono 16-bit PCM as described by {@link AudioFrame}. The buffered base stream is
 * internally bounded and prefers freshness: when the consumer falls behind, the oldest buffered frame
 * is dropped rather than stalling the decoder, so playback latency stays bounded. The application never
 * closes the stream; the call engine ends it when the call ends, which also finalizes or releases any
 * device a subclass bound.
 *
 * @apiNote Prefer a device factory when rendering to a speaker or a file, and {@link #read()} only when
 * consuming frames programmatically. Do not call {@link #offer(AudioFrame)} or {@link #shutdown()}:
 * they are the call engine's fill and teardown hooks, public only because the engine lives in a
 * different package.
 */
public class AudioInputStream {
    /**
     * Holds the maximum number of buffered frames before the oldest is dropped on {@link #offer(AudioFrame)}.
     *
     * @implNote This implementation uses 10, roughly 100 to 200 ms of audio; beyond this the consumer is
     * too far behind for the extra latency to be worth keeping.
     */
    private static final int CAPACITY = 10;

    /**
     * Marks the end of the stream so {@link #read()} returns {@code null} once drained.
     */
    private static final AudioFrame SENTINEL = new AudioFrame(new short[0], Long.MIN_VALUE);

    /**
     * Holds the bounded queue bridging the engine fill and the application consumer.
     */
    private final LinkedBlockingDeque<AudioFrame> queue = new LinkedBlockingDeque<>(CAPACITY);

    /**
     * Guards {@link #shutdown()} so the teardown runs at most once.
     *
     * <p>Exposed to device-backed subclasses so their {@link #shutdown()} override can make the same
     * end-of-stream transition atomic and idempotent.
     */
    protected final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Constructs an empty buffered stream with no device binding.
     *
     * <p>Device-backed subclasses in {@code com.github.auties00.cobalt.call.media.playback} invoke this
     * constructor and then override {@link #offer(AudioFrame)} to render to their device instead of the
     * bounded queue.
     */
    protected AudioInputStream() {
    }

    /**
     * Returns a manually-read stream that the application drains with {@link #read()}.
     *
     * @return a new empty buffered stream
     */
    public static AudioInputStream buffered() {
        return new AudioInputStream();
    }

    /**
     * Returns a stream bound to the operating-system speaker.
     *
     * <p>Each {@link #offer(AudioFrame)} renders the frame to the default output device, blocking while
     * the line buffer is full, until the call ends and the playback line is released. The application
     * does not read a speaker-bound stream.
     *
     * @return a speaker-bound stream
     * @throws IllegalStateException if no playback line is available on the running platform
     */
    public static AudioInputStream toSpeaker() {
        return new SpeakerAudioInputStream();
    }

    /**
     * Returns a stream that records the received audio to a WAV file.
     *
     * <p>Each {@link #offer(AudioFrame)} appends the frame to the file; the file is finalized when the
     * call ends. The application does not read a file-bound stream.
     *
     * @param path the WAV file to write
     * @return a file-bound stream
     * @throws NullPointerException  if {@code path} is {@code null}
     * @throws IllegalStateException if the file cannot be created
     */
    public static AudioInputStream toWav(Path path) {
        Objects.requireNonNull(path, "path cannot be null");
        return new WavFileAudioInputStream(path);
    }

    /**
     * Returns the next frame of received remote audio, blocking until one is available, or {@code null}
     * once the call has ended.
     *
     * <p>The buffered base stream returns frames previously delivered through
     * {@link #offer(AudioFrame)}. A device-backed subclass renders frames in its
     * {@link #offer(AudioFrame)} override and is not read from.
     *
     * @return the next frame, or {@code null} at end-of-stream
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public AudioFrame read() throws InterruptedException {
        var frame = queue.takeFirst();
        return frame == SENTINEL ? null : frame;
    }

    /**
     * Delivers one decoded remote frame for the application to consume, dropping the oldest buffered
     * frame if the consumer is behind.
     *
     * <p>A device-backed subclass overrides this hook to render the frame straight to its playback
     * device or file instead of buffering it.
     *
     * @param frame the decoded frame; never {@code null}
     * @throws NullPointerException if {@code frame} is {@code null}
     */
    public void offer(AudioFrame frame) {
        Objects.requireNonNull(frame, "frame cannot be null");
        if (closed.get()) {
            return;
        }
        while (!queue.offerLast(frame)) {
            queue.pollFirst();
        }
    }

    /**
     * Ends the stream, unblocking the consumer.
     *
     * <p>Enqueues the end-of-stream sentinel so a pending {@link #read()} returns {@code null}.
     * Idempotent. A device-backed subclass overrides this hook to also finalize or release its device.
     */
    public void shutdown() {
        if (closed.compareAndSet(false, true)) {
            queue.offerLast(SENTINEL);
        }
    }
}
