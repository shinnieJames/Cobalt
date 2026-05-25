package com.github.auties00.cobalt.call.frame.video;

import com.github.auties00.cobalt.call.ActiveCall;
import com.github.auties00.cobalt.call.frame.audio.AudioSource;

/**
 * Produces {@link VideoFrame}s for a call's outbound video path.
 *
 * <p>A source is the origin side of the video path: the call pulls frames from it, encodes each
 * with VP8 or H.264, and ships them over SRTP. Frames are pulled one at a time through
 * {@link #next()} on a single call-owned virtual thread, in the order the source returns them.
 * Returning {@code null} signals end-of-stream and the call stops pulling from the source. The
 * returned {@link VideoFrame} buffer may be read by the call after {@link #next()} returns, so a
 * source must not mutate a frame it has already handed out.
 *
 * <p>Typical implementations are operating-system camera capture (via the {@code cobalt-media-local}
 * companion module), a demuxed video track from an MP4 file, a bridge from another call's
 * {@link ActiveCall#remoteVideoSource()} for forwarding, or a screen-share or synthetic generator.
 *
 * @apiNote Implement this to feed outbound video into a call. Returning quickly keeps the encoder
 * fed at the target frame rate; if no frame is ready yet, block inside {@link #next()} rather than
 * returning {@code null}, because {@code null} permanently ends the stream. The blocking-and-may-be-
 * interrupted shape matches {@link AudioSource}, and Cobalt drives the source from a virtual thread,
 * so blocking is the idiomatic shape.
 */
@FunctionalInterface
public interface VideoSource {
    /**
     * Returns the next {@link VideoFrame}, blocking until one is available, or {@code null} if the
     * source has been exhausted.
     *
     * <p>A {@code null} return is the end-of-stream signal and causes the call to stop pulling from
     * this source. A non-{@code null} return is encoded and transmitted in order.
     *
     * @implSpec Implementations must block until a frame is ready rather than busy-spinning, must
     * return frames in presentation order, and must reserve {@code null} for genuine end-of-stream.
     * A frame returned from this method may still be read by the call afterwards, so its buffer must
     * not be mutated once returned.
     *
     * @return the next frame, or {@code null} on end-of-stream
     * @throws InterruptedException if the calling thread is interrupted while waiting for a frame
     */
    VideoFrame next() throws InterruptedException;
}
