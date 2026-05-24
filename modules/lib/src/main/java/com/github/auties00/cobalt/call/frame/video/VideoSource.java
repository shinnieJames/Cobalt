package com.github.auties00.cobalt.call.frame.video;

import com.github.auties00.cobalt.call.frame.audio.AudioSource;

/**
 * Producer of {@link VideoFrame}s — the Java side of the call's
 * outbound video path. {@code ActiveCall.localVideoSink()} consumes
 * from one of these, encoding each frame with VP8 or H.264 and
 * shipping it over SRTP. Implementations are typically:
 *
 * <ul>
 *   <li>OS camera capture (via the {@code cobalt-media-local}
 *       companion module).</li>
 *   <li>A demuxed video track from an MP4 file.</li>
 *   <li>A bridge from another {@code ActiveCall.remoteVideoSource()}.</li>
 *   <li>A screen-share or synthetic generator.</li>
 * </ul>
 *
 * <p>Same blocking semantics as {@link AudioSource}: {@link #next()}
 * blocks until a frame is available, returns {@code null} for
 * end-of-stream, and may throw {@link InterruptedException}.
 */
@FunctionalInterface
public interface VideoSource {
    /**
     * Returns the next {@link VideoFrame}, blocking until one is
     * available, or {@code null} if the source has been exhausted.
     *
     * @return the next frame, or {@code null} on end-of-stream
     * @throws InterruptedException if the calling thread is
     *                              interrupted while waiting
     */
    VideoFrame next() throws InterruptedException;
}
