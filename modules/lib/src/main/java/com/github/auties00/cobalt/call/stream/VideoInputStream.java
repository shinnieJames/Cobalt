package com.github.auties00.cobalt.call.stream;

import java.util.Objects;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Carries the remote inbound video of a call as a stream of {@link VideoFrame}s.
 *
 * <p>This is the read side of a call's video: the call engine decodes each H.264 or VP8 frame received
 * from the peer and fills the stream, and the application consumes it with {@link #read()} to render it
 * to whatever surface it chooses. Because rendering is inherently application-specific, this stream has
 * no device factory; it is always read manually.
 *
 * <p>Frames carry I420 planar pixels as described by {@link VideoFrame}, and the resolution may change
 * frame to frame as the decoder follows the peer's bandwidth adaptation. The stream is internally
 * bounded and prefers freshness: when the renderer falls behind, the oldest buffered frame is dropped
 * rather than stalling the decoder. The application never closes the stream; the call engine ends it
 * when the call ends.
 *
 * @apiNote Do not call {@link #offer(VideoFrame)} or {@link #shutdown()}: they are the call engine's
 * fill and teardown hooks, public only because the engine lives in a different package.
 */
public class VideoInputStream {
    /**
     * Holds the maximum number of buffered frames before the oldest is dropped on {@link #offer(VideoFrame)}.
     *
     * @implNote This implementation uses 4: video frames are large and a renderer more than a few frames
     * behind is better served the freshest picture.
     */
    private static final int CAPACITY = 4;

    /**
     * Marks the end of the stream so {@link #read()} returns {@code null} once drained.
     */
    private static final VideoFrame SENTINEL = new VideoFrame(new byte[6], 2, 2, Long.MIN_VALUE);

    /**
     * Holds the bounded queue bridging the engine fill and the application consumer.
     */
    private final LinkedBlockingDeque<VideoFrame> queue = new LinkedBlockingDeque<>(CAPACITY);

    /**
     * Guards {@link #shutdown()} so the sentinel is enqueued at most once.
     */
    protected final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Constructs an empty buffered stream.
     */
    protected VideoInputStream() {
    }

    /**
     * Returns a stream that the application drains with {@link #read()}.
     *
     * @return a new empty buffered stream
     */
    public static VideoInputStream buffered() {
        return new VideoInputStream();
    }

    /**
     * Returns the next frame of received remote video, blocking until one is available, or {@code null}
     * once the call has ended.
     *
     * @return the next frame, or {@code null} at end-of-stream
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public VideoFrame read() throws InterruptedException {
        var frame = queue.takeFirst();
        return frame == SENTINEL ? null : frame;
    }

    /**
     * Delivers one decoded remote frame for the application to consume, dropping the oldest buffered
     * frame if the renderer is behind.
     *
     * @param frame the decoded frame; never {@code null}
     * @throws NullPointerException if {@code frame} is {@code null}
     */
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
     * Ends the stream, unblocking the consumer. Idempotent.
     */
    public void shutdown() {
        if (closed.compareAndSet(false, true)) {
            queue.offerLast(SENTINEL);
        }
    }
}
