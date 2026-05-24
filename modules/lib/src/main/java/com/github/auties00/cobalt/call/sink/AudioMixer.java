package com.github.auties00.cobalt.call.sink;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSink;
import com.github.auties00.cobalt.call.frame.audio.AudioSource;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A fan-in mixer that combines N peer-specific
 * {@link AudioSource}s (one per group-call participant) into one
 * mixed stream the application can play through a single
 * {@link AudioSink} (the speaker).
 *
 * <h2>Why this lives here, not in the core call layer</h2>
 *
 * <p>WhatsApp's group-call SFU forwards per-peer audio streams;
 * it does not server-side-mix. The {@code GroupCallSession}
 * surfaces each peer's decoded PCM via a per-peer listener so
 * apps can do fancy things (talker indication, per-peer volume,
 * spatialisation). The simple-path "I just want one speaker
 * stream" use case is what this mixer covers.
 *
 * <h2>Echo-cancellation reference</h2>
 *
 * <p>{@link #mixedOutput()} is also the right signal to feed as
 * the AEC far-end reference in a group call (the AEC needs to
 * subtract what the speaker is actually playing, which is the mix
 * of all peers, not any single one).
 *
 * <h2>Mixing rule</h2>
 *
 * <p>Sums int16 PCM samples and clips at the int16 boundary. For
 * a small number of peers (the typical group-call case is &lt; 8)
 * this is fine; if you need higher fidelity, pre-attenuate
 * per-peer sources by {@code 1/N}.
 *
 * <h2>Threading</h2>
 *
 * <p>{@link #addPeer} / {@link #removePeer} are safe to call from
 * any thread. {@link #write} (called once per peer per frame, by
 * the group-call listener) is also thread-safe — frames are
 * matched on timestamp to assemble the mixed output.
 * {@link #mixedOutput()}'s {@code next()} blocks until a frame is
 * ready.
 */
public final class AudioMixer implements AudioSink {
    /**
     * Capacity of the internal per-peer queues — big enough to
     * absorb a few hundred ms of jitter, small enough to not OOM
     * if a peer goes silent and the others keep arriving.
     */
    private static final int PER_PEER_QUEUE_CAPACITY = 64;

    /**
     * Frames per peer keyed on the peer id.
     */
    private final ConcurrentHashMap<Object, LinkedBlockingDeque<AudioFrame>> peerQueues =
            new ConcurrentHashMap<>();

    /**
     * Output queue the mixer publishes mixed frames into.
     */
    private final LinkedBlockingDeque<AudioFrame> mixed = new LinkedBlockingDeque<>(PER_PEER_QUEUE_CAPACITY);

    /**
     * Default queue key when callers use {@link #write(AudioFrame)}
     * without specifying a peer.
     */
    private final Object defaultPeerKey = new Object();

    /**
     * Number of frames mixed and emitted, monotonic.
     */
    private final AtomicLong mixedFrameCount = new AtomicLong();

    /**
     * Adds a peer's audio queue to the mixer. Idempotent: calling
     * twice with the same key replaces nothing — the existing
     * queue is preserved.
     *
     * @param peerKey the peer identifier (typically a {@code Jid})
     * @return an {@link AudioSink} the per-peer listener writes
     *         decoded frames into
     */
    public AudioSink addPeer(Object peerKey) {
        Objects.requireNonNull(peerKey, "peerKey cannot be null");
        peerQueues.computeIfAbsent(peerKey,
                k -> new LinkedBlockingDeque<>(PER_PEER_QUEUE_CAPACITY));
        return frame -> writeFor(peerKey, frame);
    }

    /**
     * Removes a peer from the mixer — its queue is discarded and
     * subsequent {@link #write} calls for the peer will start a
     * fresh queue.
     *
     * @param peerKey the peer identifier
     */
    public void removePeer(Object peerKey) {
        Objects.requireNonNull(peerKey, "peerKey cannot be null");
        peerQueues.remove(peerKey);
    }

    /**
     * Returns the mixer's mixed output as an {@link AudioSource}.
     * Each {@link AudioSource#next()} call blocks until a mixed
     * frame is ready (i.e. every currently-registered peer has
     * contributed a frame). Use the same source as the AEC
     * far-end reference.
     *
     * @return the mixed source
     */
    public AudioSource mixedOutput() {
        return mixed::take;
    }

    /**
     * Returns the number of mixed frames emitted so far —
     * useful for diagnostics.
     *
     * @return the count
     */
    public long mixedFrameCount() {
        return mixedFrameCount.get();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Equivalent to {@code addPeer(defaultKey).write(frame)}
     * for apps that have only one inbound stream and just want a
     * write-side handle.
     */
    @Override
    public void write(AudioFrame frame) throws InterruptedException {
        writeFor(defaultPeerKey, frame);
    }

    /**
     * Routes one peer-specific frame into its queue, then attempts
     * to assemble + emit a mixed frame if every peer has at least
     * one queued.
     *
     * @param peerKey the peer identifier
     * @param frame   the frame
     * @throws InterruptedException if the calling thread is
     *                              interrupted while waiting
     */
    private void writeFor(Object peerKey, AudioFrame frame) throws InterruptedException {
        Objects.requireNonNull(frame, "frame cannot be null");
        var queue = peerQueues.computeIfAbsent(peerKey,
                k -> new LinkedBlockingDeque<>(PER_PEER_QUEUE_CAPACITY));
        queue.put(frame);
        tryEmit();
    }

    /**
     * If every peer's queue has a head frame, drains one from
     * each, sums them, and pushes the result to {@link #mixed}.
     *
     * @throws InterruptedException if the put on {@link #mixed} is
     *                              interrupted
     */
    private synchronized void tryEmit() throws InterruptedException {
        if (peerQueues.isEmpty()) {
            return;
        }
        for (var queue : peerQueues.values()) {
            if (queue.peek() == null) {
                return;
            }
        }
        var heads = new AudioFrame[peerQueues.size()];
        var i = 0;
        var frameSize = -1;
        long ptsMs = 0;
        for (var queue : peerQueues.values()) {
            var head = queue.poll();
            if (head == null) {
                return;
            }
            if (frameSize < 0) {
                frameSize = head.pcm().length;
                ptsMs = head.ptsMs();
            }
            heads[i++] = head;
        }
        if (frameSize <= 0) {
            return;
        }
        var sum = new int[frameSize];
        for (var head : heads) {
            var pcm = head.pcm();
            var len = Math.min(frameSize, pcm.length);
            for (var j = 0; j < len; j++) {
                sum[j] += pcm[j];
            }
        }
        var out = new short[frameSize];
        for (var j = 0; j < frameSize; j++) {
            var s = sum[j];
            if (s > Short.MAX_VALUE) s = Short.MAX_VALUE;
            else if (s < Short.MIN_VALUE) s = Short.MIN_VALUE;
            out[j] = (short) s;
        }
        mixed.put(new AudioFrame(out, ptsMs));
        mixedFrameCount.incrementAndGet();
    }
}
