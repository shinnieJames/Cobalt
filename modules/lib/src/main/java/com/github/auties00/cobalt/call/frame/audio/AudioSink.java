package com.github.auties00.cobalt.call.frame.audio;

import com.github.auties00.cobalt.call.ActiveCall;

/**
 * Consumes {@link AudioFrame}s delivered by a call.
 *
 * <p>A sink is the endpoint of an audio path. On the inbound side, {@link ActiveCall} reads frames
 * from the remote participant and writes them into a sink the caller provides, typically an
 * operating system speaker. On the outbound side the local mic-frame endpoint exposed by
 * {@link ActiveCall#localAudioSink()} is itself a sink: the caller pushes locally captured frames
 * into it and the call encodes and transmits them.
 *
 * <p>Every frame passed to {@link #write(AudioFrame)} carries 16 kHz mono signed 16-bit PCM, as
 * described by {@link AudioFrame}. The method blocks while the sink cannot yet accept the frame,
 * for example under encoder or network backpressure on the outbound side or a full speaker buffer
 * on the inbound side. A normal return means the frame has been accepted.
 *
 * @apiNote Implement this interface to render or forward call audio, then hand the implementation to
 * the call so inbound frames flow to it. The frame buffer is owned by the sink once accepted; the
 * caller does not reuse it.
 */
@FunctionalInterface
public interface AudioSink {
    /**
     * Accepts one frame into the sink, blocking until the sink can take it.
     *
     * <p>Returns once the frame has been accepted. If the frame cannot be accepted immediately the
     * call blocks rather than dropping the frame, propagating backpressure to the producer.
     *
     * @implSpec Implementations may block the calling thread until the frame is consumed; Cobalt
     * drives this method from a virtual thread, so blocking is the expected shape. An
     * implementation must take ownership of {@code frame} and its sample buffer and must not assume
     * the buffer is reused by the caller. An implementation that cannot accept the frame because
     * the calling thread is interrupted must throw {@link InterruptedException} rather than
     * silently discarding it.
     * @param frame the frame to deliver; never {@code null}
     * @throws InterruptedException if the calling thread is interrupted while waiting for the sink
     *                              to accept the frame
     */
    void write(AudioFrame frame) throws InterruptedException;
}
