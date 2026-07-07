package com.github.auties00.cobalt.calls2.stream;

/**
 * Defines the remote inbound video sink of a call: the app-supplied destination for the
 * {@link VideoFrame}s the call engine decodes from the peer.
 *
 * <p>This is the read side of a call's video. The engine decodes each video frame received from the
 * peer and {@linkplain #offer(VideoFrame) delivers} it to this sink; the embedder renders it to
 * whatever surface it chooses. The contract has two faces. The engine-facing face is
 * {@link #offer(VideoFrame)} and {@link #shutdown()}, by which the engine fills the sink and signals
 * end-of-stream. The application-facing face is {@link #read()}, by which the embedder pulls received
 * frames to render them. Because rendering is inherently application-specific, there is no
 * device-backed video sink; a video sink is always consumed through {@link #read()}.
 *
 * <p>Frames carry planar 4:2:0 pixels as described by {@link VideoFrame}, and the resolution may
 * change frame to frame as the codec follows the peer's bandwidth adaptation. An implementation
 * decides its own buffering policy between the engine fill and the consumer; a buffered sink typically
 * prefers freshness, dropping the oldest buffered frame rather than stalling the decoder when the
 * renderer falls behind. The application never ends the sink itself; the engine invokes
 * {@link #shutdown()} when the call ends.
 *
 * @apiNote An embedder implements this interface to render received video, or obtains the bundled
 * buffered implementation from {@link #buffered()}. Because rendering is inherently
 * application-specific there is no device-backed video sink; a sink is always read manually. The
 * {@link #offer(VideoFrame)} and {@link #shutdown()} methods belong to the engine; application code
 * drives the sink through {@link #read()} and never calls the engine-facing pair directly.
 */
public interface VideoInput {
    /**
     * Returns a manually-read buffered sink the application drains with {@link #read()}.
     *
     * <p>The engine fills the sink through {@link #offer(VideoFrame)} and the application pulls the
     * buffered frames with {@link #read()} to render them; the sink is internally bounded and prefers
     * freshness, dropping the oldest buffered frame when the renderer falls behind rather than stalling
     * the decoder.
     *
     * @return a new empty buffered sink
     */
    static VideoInput buffered() {
        return BufferedVideoInput.buffered();
    }

    /**
     * Delivers one decoded remote frame for the application to consume.
     *
     * <p>Invoked by the engine for each frame it decodes from the peer. A buffered sink enqueues the
     * frame for {@link #read()}, dropping the oldest buffered frame if the renderer is behind. After
     * {@link #shutdown()} has run an implementation discards the frame. The frame is never
     * {@code null}.
     *
     * @param frame the decoded frame; never {@code null}
     * @throws NullPointerException if {@code frame} is {@code null}
     */
    void offer(VideoFrame frame);

    /**
     * Returns the next frame of received remote video, blocking until one is available, or
     * {@code null} once the call has ended.
     *
     * <p>Returns frames previously delivered through {@link #offer(VideoFrame)}. The method blocks
     * while no frame is ready and returns {@code null} exactly once the sink has been
     * {@linkplain #shutdown() ended} and drained.
     *
     * <p>The returned frame's {@linkplain VideoFrame#pixels() pixel buffer} is borrowed from a pool the
     * engine reuses across frames: it is valid only until the next call to this method on the same
     * input, after which the engine may refill and re-offer it. A consumer that needs the pixels beyond
     * the next read copies them out; it must never retain the returned array past the next read nor
     * mutate it.
     *
     * @return the next frame, or {@code null} at end-of-stream
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    VideoFrame read() throws InterruptedException;

    /**
     * Ends the sink, unblocking a pending {@link #read()}.
     *
     * <p>Invoked by the engine when the call ends. After it runs, {@link #read()} returns {@code null}
     * once drained. Implementations make this idempotent, since the engine may signal teardown more
     * than once during a racing shutdown.
     */
    void shutdown();
}
