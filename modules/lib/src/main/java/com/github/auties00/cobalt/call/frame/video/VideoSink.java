package com.github.auties00.cobalt.call.frame.video;

import com.github.auties00.cobalt.call.ActiveCall;
import com.github.auties00.cobalt.call.frame.audio.AudioSink;

/**
 * Consumes {@link VideoFrame}s, forming the inbound counterpart of {@link VideoSource}.
 *
 * <p>A sink is the destination side of a call's video path. The application supplies a sink to
 * render inbound video, and {@link ActiveCall#localVideoSink()} returns the sink the application
 * pushes outbound camera or file frames into. Each accepted frame is consumed in full before
 * {@link #write(VideoFrame)} returns; the call delivers frames in presentation order on a single
 * call-owned virtual thread, so a sink does not need to be reentrant. The sink shares the
 * {@link VideoFrame} buffer rather than receiving a copy, so it must finish reading the frame
 * before returning and must not retain the buffer past the call.
 *
 * @apiNote Implement this to render or forward inbound video, for example into a display window or
 * a file muxer. Blocking inside {@link #write(VideoFrame)} applies backpressure to the call, which
 * is acceptable for short stalls but starves the inbound media path if held for long; an
 * implementation that cannot keep up should drop frames rather than block indefinitely.
 */
@FunctionalInterface
public interface VideoSink {
    /**
     * Accepts one {@link VideoFrame} into the sink, blocking until the sink can take it.
     *
     * <p>Returning normally means the frame has been accepted and fully consumed. Throwing
     * {@link InterruptedException} means the calling thread was interrupted before the frame could
     * be delivered, mirroring the blocking contract of {@link AudioSink#write}.
     *
     * @implSpec Implementations must read all pixel data they need from {@code frame} before
     * returning and must not retain the frame's buffer afterwards, because the call reuses or
     * releases it. Implementations should avoid blocking longer than necessary, since the call's
     * inbound path stalls until this method returns.
     *
     * @param frame the frame to deliver; never {@code null}
     * @throws InterruptedException if the calling thread is interrupted while waiting for the sink
     *                              to accept the frame
     */
    void write(VideoFrame frame) throws InterruptedException;
}
