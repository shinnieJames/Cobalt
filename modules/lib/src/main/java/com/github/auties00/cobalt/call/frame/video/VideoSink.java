package com.github.auties00.cobalt.call.frame.video;

import com.github.auties00.cobalt.call.frame.audio.AudioSink;

/**
 * Consumer of {@link VideoFrame}s — the inbound counterpart of
 * {@link VideoSource}. {@code ActiveCall.remoteVideoSource()} feeds
 * frames into a sink the user provides (typically a render window),
 * while {@code ActiveCall.localVideoSink()} is itself the sink the
 * user pushes camera/file frames into.
 *
 * <p>Same blocking semantics as {@link AudioSink}.
 */
@FunctionalInterface
public interface VideoSink {
    /**
     * Accepts one {@link VideoFrame} into the sink, blocking until
     * the sink can take it.
     *
     * @param frame the frame to deliver; never {@code null}
     * @throws InterruptedException if the calling thread is
     *                              interrupted while waiting
     */
    void write(VideoFrame frame) throws InterruptedException;
}
