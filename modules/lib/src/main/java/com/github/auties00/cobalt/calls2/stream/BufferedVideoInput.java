package com.github.auties00.cobalt.calls2.stream;

import java.util.Objects;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides the bundled buffered implementation of {@link VideoInput}, the remote inbound video sink of
 * a call.
 *
 * <p>This is the concrete read side of a call's video. The engine decodes each video frame received
 * from the peer and {@linkplain #offer(VideoFrame) fills} this sink, and the application drains it with
 * {@link #read()} to render to whatever surface it chooses. Because rendering is inherently
 * application-specific there is no device-backed video sink; a sink is always read manually, so this
 * type is obtained through {@link #buffered()} rather than subclassed for a device.
 *
 * <p>Frames carry planar 4:2:0 pixels as described by {@link VideoFrame}, and the resolution may change
 * frame to frame as the decoder follows the peer's bandwidth adaptation. The sink is internally bounded
 * and prefers freshness: when the renderer falls behind, the oldest buffered frame is dropped rather
 * than stalling the decoder. The application never ends the sink; the engine invokes {@link #shutdown()}
 * when the call ends.
 *
 * @apiNote Do not call {@link #offer(VideoFrame)} or {@link #shutdown()}: they are the engine's fill and
 * teardown hooks, public only because the engine lives in a different package.
 * @implNote This implementation is the calls2 counterpart of the legacy buffered video input stream
 * base, carried forward unchanged; the frames it buffers carry the {@link VideoFrame#ptsMicros()}
 * microsecond clock rather than the legacy millisecond clock.
 */
public final class BufferedVideoInput implements VideoInput {
    /**
     * Holds the maximum number of buffered frames before the oldest is dropped on
     * {@link #offer(VideoFrame)}.
     *
     * @implNote This implementation uses {@code 4}: video frames are large and a renderer more than a
     * few frames behind is better served the freshest picture.
     */
    private static final int CAPACITY = 4;

    /**
     * Marks the end of the sink so {@link #read()} returns {@code null} once drained.
     *
     * <p>A minimal {@code 2x2} {@link VideoPixelFormat#I420 I420} frame is used as a private identity
     * token; it is compared by reference in {@link #read()} and never decoded.
     */
    private static final VideoFrame SENTINEL =
            new VideoFrame(new byte[6], VideoPixelFormat.I420, 2, 2, Long.MIN_VALUE);

    /**
     * Holds the bounded queue bridging the engine fill and the application consumer.
     */
    private final LinkedBlockingDeque<VideoFrame> queue = new LinkedBlockingDeque<>(CAPACITY);

    /**
     * Guards {@link #shutdown()} so the sentinel is enqueued at most once.
     */
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Constructs an empty buffered sink.
     */
    private BufferedVideoInput() {
    }

    /**
     * Returns a sink that the application drains with {@link #read()}.
     *
     * @return a new empty buffered sink
     */
    public static BufferedVideoInput buffered() {
        return new BufferedVideoInput();
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws InterruptedException {@inheritDoc}
     */
    @Override
    public VideoFrame read() throws InterruptedException {
        var frame = queue.takeFirst();
        return frame == SENTINEL ? null : frame;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Enqueues the frame, dropping the oldest buffered frame if the renderer is behind, and discards
     * the frame once the sink has been {@linkplain #shutdown() ended}.
     *
     * @param frame {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    @Override
    public void offer(VideoFrame frame) {
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
     * <p>Enqueues the end-of-stream sentinel so a pending {@link #read()} returns {@code null} once
     * drained. Idempotent.
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
