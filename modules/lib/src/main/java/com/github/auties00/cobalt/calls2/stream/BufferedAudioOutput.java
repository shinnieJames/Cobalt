package com.github.auties00.cobalt.calls2.stream;

import java.util.Objects;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Carries the local outbound audio of a call as a manually-written buffered {@link AudioOutput}.
 *
 * <p>This is the buffered base source returned by {@link AudioOutput#buffered()} and the superclass of
 * every device-backed capture source in this package. As a standalone source it is the path a bot or a
 * call-to-call bridge uses to feed synthetic or relayed audio: the application pushes frames with
 * {@link #write(AudioFrame)} and the engine drains them with {@link #take()}. As a base it supplies the
 * shared end-of-stream bookkeeping a device-backed subclass reuses through the {@link #closed} flag while
 * overriding {@link #take()} to pull from its capture device instead of the buffer.
 *
 * <p>Frames carry mono 16-bit PCM at 16 kHz as described by {@link AudioFrame}. The buffer is internally
 * bounded: {@link #write(AudioFrame)} blocks while the encoder has not caught up, propagating backpressure
 * to the producer rather than growing without limit. The application never ends the source; the engine
 * invokes {@link #shutdown()} when the call ends, which also releases any device a subclass bound.
 *
 * @implNote This implementation is the calls2 counterpart of the legacy buffered {@code AudioOutputStream}
 * base, carried forward unchanged except that the per-frame timestamp it relays is the
 * {@link AudioFrame#ptsMicros()} microsecond clock rather than the legacy millisecond clock.
 */
public sealed class BufferedAudioOutput implements AudioOutput
        permits FfmpegAudioOutput, MicrophoneAudioOutput, SilenceAudioOutput, ToneAudioOutput {
    /**
     * Holds the maximum number of buffered frames before {@link #write(AudioFrame)} blocks.
     *
     * @implNote This implementation uses 10, roughly 100 to 200 ms of audio, enough to absorb encoder
     * scheduling jitter without adding perceptible latency.
     */
    private static final int CAPACITY = 10;

    /**
     * Marks the end of the source so the engine's {@link #take()} returns {@code null} once drained.
     *
     * @implNote This implementation is identity-comparable against real frames, which never share this
     * instance.
     */
    private static final AudioFrame SENTINEL = new AudioFrame(new short[0], Long.MIN_VALUE);

    /**
     * Holds the bounded queue bridging the application producer and the engine drain.
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
     * Constructs an empty buffered source with no device binding.
     *
     * <p>Device-backed subclasses invoke this constructor and then override {@link #take()} to read from
     * their device instead of the bounded queue.
     */
    protected BufferedAudioOutput() {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The frame is queued for the call engine to encode and send. The call blocks rather than dropping
     * the frame when the internal buffer is full, propagating backpressure. After the source has ended the
     * frame is silently discarded. A device-backed subclass fills itself through its {@link #take()}
     * override and ignores this method, since nothing writes to it.
     *
     * @param frame {@inheritDoc}
     * @throws NullPointerException if {@code frame} is {@code null}
     * @throws InterruptedException if the calling thread is interrupted while waiting for buffer space
     */
    @Override
    public void write(AudioFrame frame) throws InterruptedException {
        Objects.requireNonNull(frame, "frame cannot be null");
        if (closed.get()) {
            return;
        }
        queue.putLast(frame);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The buffered base source returns frames previously supplied through {@link #write(AudioFrame)}.
     * A device-backed subclass overrides this hook to pull the next frame straight from its capture device
     * or decoder, returning {@code null} at end-of-stream.
     *
     * @return {@inheritDoc}
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    @Override
    public AudioFrame take() throws InterruptedException {
        var frame = queue.takeFirst();
        return frame == SENTINEL ? null : frame;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Enqueues the end-of-stream sentinel so a pending {@link #take()} returns {@code null}. Idempotent.
     * A device-backed subclass overrides this hook to also release its device and unblock a {@link #take()}
     * parked on the device.
     */
    @Override
    public void shutdown() {
        if (closed.compareAndSet(false, true)) {
            var interrupted = false;
            try {
                while (true) {
                    try {
                        queue.putFirst(SENTINEL);
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The buffered base source returns {@code false}: frames an application writes are already clean
     * line-level audio that the engine encodes without acoustic conditioning. Only the microphone-bound
     * subclass overrides this to {@code true}.
     *
     * @return {@inheritDoc}
     */
    @Override
    public boolean isLiveCapture() {
        return false;
    }
}
