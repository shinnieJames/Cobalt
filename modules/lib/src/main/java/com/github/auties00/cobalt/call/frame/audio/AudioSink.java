package com.github.auties00.cobalt.call.frame.audio;

/**
 * Consumer of {@link AudioFrame}s — the Java side of the call's
 * inbound audio path on a user's end, or the entry point on the
 * outbound side for whoever produces the local mic frames.
 * {@code ActiveCall.remoteAudioSource()} feeds frames into a sink
 * the user provides (typically the OS speaker), while
 * {@code ActiveCall.localAudioSink()} is itself the sink the user
 * pushes mic/file frames into.
 *
 * <p>{@link #write(AudioFrame)} blocks if the sink can't accept the
 * frame yet (encoder/network backpressure on the outbound side, or
 * speaker buffer full on the inbound side). Returning normally
 * means the frame has been accepted; throwing
 * {@link InterruptedException} means the calling thread was
 * interrupted before the frame could be delivered.
 */
@FunctionalInterface
public interface AudioSink {
    /**
     * Accepts one {@link AudioFrame} into the sink, blocking until
     * the sink can take it.
     *
     * @param frame the frame to deliver; never {@code null}
     * @throws InterruptedException if the calling thread is
     *                              interrupted while waiting
     */
    void write(AudioFrame frame) throws InterruptedException;
}
