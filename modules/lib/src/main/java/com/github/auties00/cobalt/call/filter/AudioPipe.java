package com.github.auties00.cobalt.call.filter;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSink;
import com.github.auties00.cobalt.call.frame.audio.AudioSource;

import java.util.concurrent.LinkedBlockingDeque;

/**
 * A self-contained {@link AudioSink} + {@link AudioSource} pair
 * connected by an in-process queue: every frame
 * {@link AudioSink#write} pushes is read out by the next
 * {@link AudioSource#next} call.
 *
 * <p>Uses:
 *
 * <ul>
 *   <li><b>Echo / loopback test</b>: feed
 *       {@code ActiveCall.remoteAudioSource()} into
 *       {@link #sink()}, and feed {@link #source()} into
 *       {@code ActiveCall.localAudioSink()} — every received
 *       frame is echoed straight back to the peer.</li>
 *   <li><b>Pipeline integration tests</b>: stand in for an OS
 *       speaker so the test thread can both write to the sink
 *       and inspect the frames it'd play.</li>
 *   <li><b>Bridging two calls</b>: hand one call's
 *       {@code remoteAudioSource} to {@link #sink()} and another
 *       call's {@code localAudioSink} to {@link #source()} to
 *       relay audio between them.</li>
 * </ul>
 *
 * <p>The internal queue is bounded so a stalled consumer applies
 * back-pressure to the producer rather than growing unbounded.
 */
public final class AudioPipe {
    /**
     * Default queue capacity — 100 ms of buffering at the
     * WhatsApp 10-ms-frame cadence.
     */
    private static final int DEFAULT_CAPACITY = 10;

    /**
     * The shared queue connecting sink writes to source reads.
     */
    private final LinkedBlockingDeque<AudioFrame> queue;

    /**
     * Constructs a pipe with the default queue capacity.
     */
    public AudioPipe() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Constructs a pipe with an explicit queue capacity.
     *
     * @param capacity maximum queued frames before
     *                 {@link AudioSink#write} blocks
     * @throws IllegalArgumentException if {@code capacity} is &lt; 1
     */
    public AudioPipe(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be ≥ 1");
        }
        this.queue = new LinkedBlockingDeque<>(capacity);
    }

    /**
     * Returns the sink end of the pipe.
     *
     * @return the sink
     */
    public AudioSink sink() {
        return queue::put;
    }

    /**
     * Returns the source end of the pipe.
     *
     * @return the source
     */
    public AudioSource source() {
        return queue::take;
    }

    /**
     * Returns the number of frames currently buffered.
     *
     * @return the size
     */
    public int size() {
        return queue.size();
    }
}
