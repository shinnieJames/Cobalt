package com.github.auties00.cobalt.call.filter;

import com.github.auties00.cobalt.call.ActiveCall;
import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSink;
import com.github.auties00.cobalt.call.frame.audio.AudioSource;

import java.util.concurrent.LinkedBlockingDeque;

/**
 * Connects an {@link AudioSink} and an {@link AudioSource} through a single in-process queue.
 *
 * <p>Every {@link AudioFrame} written to {@link #sink()} is enqueued and later dequeued by a
 * {@link #source()} read, so the pair behaves as a one-way conduit between a producer and a
 * consumer of call audio. The queue is bounded: when it is full, {@link AudioSink#write(AudioFrame)}
 * blocks until a {@link AudioSource#next()} read frees a slot, which propagates back-pressure from a
 * stalled consumer to the producer rather than letting the queue grow without bound. The two ends
 * are independent functional values; either may be handed to unrelated code that knows only the
 * {@link AudioSink} or {@link AudioSource} contract.
 *
 * <p>Typical wirings:
 * <ul>
 *   <li>Echo or loopback: route {@link ActiveCall#remoteAudioSource()} into {@link #sink()} and
 *       {@link #source()} into {@link ActiveCall#localAudioSink()}, so each received frame is
 *       echoed straight back to the peer.</li>
 *   <li>Pipeline integration tests: stand in for an OS speaker so a test thread both writes frames
 *       and inspects the frames it would have played.</li>
 *   <li>Bridging two calls: hand one call's {@link ActiveCall#remoteAudioSource()} to
 *       {@link #sink()} and another call's {@link ActiveCall#localAudioSink()} to {@link #source()}
 *       to relay audio between them.</li>
 * </ul>
 */
public final class AudioPipe {
    /**
     * Default queue capacity in frames.
     *
     * @implNote This implementation uses {@code 10}, which is 100 ms of buffering at WhatsApp's
     * 10 ms Opus frame cadence.
     */
    private static final int DEFAULT_CAPACITY = 10;

    /**
     * Bounded queue carrying frames from sink writes to source reads.
     */
    private final LinkedBlockingDeque<AudioFrame> queue;

    /**
     * Constructs a pipe whose queue holds {@link #DEFAULT_CAPACITY} frames.
     */
    public AudioPipe() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Constructs a pipe whose queue holds {@code capacity} frames.
     *
     * <p>Once the queue holds {@code capacity} frames, further {@link AudioSink#write(AudioFrame)}
     * calls block until a {@link AudioSource#next()} read frees a slot.
     *
     * @param capacity the maximum number of buffered frames before writes block
     * @throws IllegalArgumentException if {@code capacity} is less than {@code 1}
     */
    public AudioPipe(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.queue = new LinkedBlockingDeque<>(capacity);
    }

    /**
     * Returns the sink end of the pipe.
     *
     * <p>Each {@link AudioSink#write(AudioFrame)} on the returned sink enqueues the frame, blocking
     * while the queue is full.
     *
     * @return the sink that enqueues written frames
     */
    public AudioSink sink() {
        return queue::put;
    }

    /**
     * Returns the source end of the pipe.
     *
     * <p>Each {@link AudioSource#next()} on the returned source dequeues the oldest frame, blocking
     * while the queue is empty.
     *
     * @return the source that dequeues buffered frames
     */
    public AudioSource source() {
        return queue::take;
    }

    /**
     * Returns the number of frames currently buffered in the queue.
     *
     * @return the current queue size
     */
    public int size() {
        return queue.size();
    }
}
