package com.github.auties00.cobalt.calls2.stream;

import java.util.Objects;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Carries the remote inbound audio of a call as a manually-read buffered {@link AudioInput}.
 *
 * <p>This is the buffered base sink returned by {@link AudioInput#buffered()} and the superclass of every
 * device-backed playback sink in this package. As a standalone sink it is the path a bot or a
 * call-to-call bridge uses to forward or analyse received audio: the engine fills frames with
 * {@link #offer(AudioFrame)} and the application drains them with {@link #read()}. As a base it supplies
 * the shared end-of-stream bookkeeping a device-backed subclass reuses through the {@link #closed} flag
 * while overriding {@link #offer(AudioFrame)} to render to its playback device instead of the buffer.
 *
 * <p>Frames carry mono 16-bit PCM at 16 kHz as described by {@link AudioFrame}. The buffer is internally
 * bounded and prefers freshness: when the consumer falls behind, the oldest buffered frame is dropped
 * rather than stalling the decoder, so playback latency stays bounded. The application never ends the
 * sink; the engine invokes {@link #shutdown()} when the call ends, which also finalizes or releases any
 * device a subclass bound.
 *
 * @implNote This implementation is the calls2 counterpart of the legacy buffered {@code AudioInputStream}
 * base, carried forward unchanged; the frames it buffers carry the {@link AudioFrame#ptsMicros()}
 * microsecond clock rather than the legacy millisecond clock.
 */
public sealed class BufferedAudioInput implements AudioInput
        permits SpeakerAudioInput, WavFileAudioInput {
    /**
     * Holds the maximum number of buffered frames before the oldest is dropped on
     * {@link #offer(AudioFrame)}.
     *
     * @implNote This implementation uses 10, roughly 100 to 200 ms of audio; beyond this the consumer is
     * too far behind for the extra latency to be worth keeping.
     */
    private static final int CAPACITY = 10;

    /**
     * Marks the end of the sink so {@link #read()} returns {@code null} once drained.
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
     * Constructs an empty buffered sink with no device binding.
     *
     * <p>Device-backed subclasses invoke this constructor and then override {@link #offer(AudioFrame)} to
     * render to their device instead of the bounded queue.
     */
    protected BufferedAudioInput() {
    }

    /**
     * {@inheritDoc}
     *
     * <p>A device-backed subclass overrides this hook to render the frame straight to its playback device
     * or file instead of buffering it.
     *
     * @param frame {@inheritDoc}
     * @throws NullPointerException if {@code frame} is {@code null}
     */
    @Override
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
     * {@inheritDoc}
     *
     * <p>The buffered base sink returns frames previously delivered through {@link #offer(AudioFrame)}. A
     * device-backed subclass renders frames in its {@link #offer(AudioFrame)} override and is not read
     * from.
     *
     * @return {@inheritDoc}
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    @Override
    public AudioFrame read() throws InterruptedException {
        var frame = queue.takeFirst();
        return frame == SENTINEL ? null : frame;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Enqueues the end-of-stream sentinel so a pending {@link #read()} returns {@code null}. Idempotent.
     * A device-backed subclass overrides this hook to also finalize or release its device.
     */
    @Override
    public void shutdown() {
        if (closed.compareAndSet(false, true)) {
            while (!queue.offerLast(SENTINEL)) {
                queue.pollFirst();
            }
        }
    }
}
