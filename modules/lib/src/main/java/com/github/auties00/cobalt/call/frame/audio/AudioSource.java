package com.github.auties00.cobalt.call.frame.audio;

/**
 * Producer of {@link AudioFrame}s — the Java side of the call's
 * outbound audio path. {@code ActiveCall.localAudioSink()}
 * consumes from one of these, encoding each frame with Opus and
 * shipping it over SRTP. Implementations are typically:
 *
 * <ul>
 *   <li>OS microphone capture (via the {@code cobalt-media-local}
 *       companion module).</li>
 *   <li>A demuxed audio track from an MP3/MP4 file (also in
 *       {@code cobalt-media-local}).</li>
 *   <li>A bridge from another {@code ActiveCall.remoteAudioSource()},
 *       for multi-party mixing.</li>
 *   <li>A synthetic generator for tests.</li>
 * </ul>
 *
 * <p>{@link #next()} blocks until the next frame is available and
 * may be interrupted; returning {@code null} signals end-of-stream
 * (the call's encoder treats this as "stop consuming from this
 * source"). Cobalt drives sources from a virtual thread, so blocking
 * is the idiomatic shape.
 */
@FunctionalInterface
public interface AudioSource {
    /**
     * Returns the next {@link AudioFrame}, blocking until one is
     * available, or {@code null} if the source has been exhausted.
     *
     * @return the next frame, or {@code null} on end-of-stream
     * @throws InterruptedException if the calling thread is
     *                              interrupted while waiting
     */
    AudioFrame next() throws InterruptedException;
}
