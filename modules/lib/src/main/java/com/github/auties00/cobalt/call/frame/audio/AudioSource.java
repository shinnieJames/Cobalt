package com.github.auties00.cobalt.call.frame.audio;

import com.github.auties00.cobalt.call.ActiveCall;

/**
 * Produces {@link AudioFrame}s for a call to transmit.
 *
 * <p>A source is the origin of the outbound audio path. {@link ActiveCall} pulls frames from a
 * source, encodes each one with Opus, and ships it over SRTP to the remote participant. Frames must
 * carry 16 kHz mono signed 16-bit PCM, as described by {@link AudioFrame}. Typical implementations
 * capture from an operating system microphone, demux an audio track from a media file, bridge from
 * a remote source for multi-party mixing, or generate synthetic samples for tests.
 *
 * <p>{@link #next()} blocks until the next frame is available and returns {@code null} to signal
 * end-of-stream, after which the call stops consuming from this source.
 *
 * @apiNote Implement this interface to feed call audio from a custom capture or playback origin,
 * then hand the implementation to the call so it becomes the outbound stream. Return {@code null}
 * only when the stream is permanently finished, not to indicate a transient gap; produce a silent
 * frame for a gap instead.
 */
@FunctionalInterface
public interface AudioSource {
    /**
     * Returns the next frame, blocking until one is available, or {@code null} on end-of-stream.
     *
     * <p>Blocks while no frame is ready. Returning a frame transfers it to the call for encoding;
     * returning {@code null} ends the stream and the call stops polling this source.
     *
     * @implSpec Implementations may block the calling thread until a frame is ready; Cobalt drives
     * this method from a virtual thread, so blocking is the expected shape. Each returned frame must
     * be a fresh {@link AudioFrame} whose buffer the call may retain; an implementation must not
     * mutate a buffer it has already returned. An implementation that is interrupted while waiting
     * must throw {@link InterruptedException} rather than returning {@code null}, so a transient
     * interruption is not mistaken for end-of-stream.
     * @return the next frame, or {@code null} once the source is permanently exhausted
     * @throws InterruptedException if the calling thread is interrupted while waiting for a frame
     */
    AudioFrame next() throws InterruptedException;
}
