package com.github.auties00.cobalt.call.sink;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSink;
import com.github.auties00.cobalt.call.frame.audio.AudioSource;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Combines several per-peer audio streams into one mixed stream for a group call.
 *
 * <p>A WhatsApp group call delivers each remote participant's audio as a separate decoded stream
 * rather than a single server-mixed stream. This mixer fans those streams in: an application
 * registers one {@link AudioSink} per peer through {@link #addPeer(Object)}, writes each peer's
 * decoded frames into the sink it gets back, and reads a single combined {@link AudioSource} from
 * {@link #mixedOutput()} that it can render through one speaker. Each peer has its own bounded queue;
 * a mixed frame is emitted only once every currently-registered peer has contributed at least one
 * queued frame, at which point one frame is drained from each queue, the samples are summed, and the
 * result is published to the output queue. Frames are paired in queue order and the emitted frame
 * carries the presentation timestamp of the first peer's contributing frame.
 *
 * <p>The summed mixed signal is also the correct far-end reference for acoustic echo cancellation in
 * a group call, because the echo to be removed is whatever the speaker actually plays, which is the
 * combination of all peers rather than any single one.
 *
 * <p>{@link #addPeer(Object)} and {@link #removePeer(Object)} are safe to call from any thread, as is
 * writing frames through the returned sinks; {@link AudioSource#next()} on {@link #mixedOutput()}
 * blocks until a mixed frame is ready.
 *
 * @apiNote Use this only for the simple single-speaker group-call case. An application that wants
 * per-peer volume, talker indication, or spatialisation should render each peer stream itself rather
 * than collapsing them here.
 * @implNote This implementation sums signed 16-bit samples and clips at the signed 16-bit boundary,
 * which is adequate for the typical group-call size of fewer than eight peers. For higher fidelity
 * with many peers, pre-attenuate each peer source by {@code 1/N} before writing.
 */
public final class AudioMixer implements AudioSink {
    /**
     * Bounds each per-peer queue.
     *
     * @implNote This implementation caps each queue at sixty-four frames, large enough to absorb a
     * few hundred milliseconds of jitter yet small enough that a silent peer cannot let memory grow
     * without bound while the other peers keep arriving.
     */
    private static final int PER_PEER_QUEUE_CAPACITY = 64;

    /**
     * Holds one bounded frame queue per peer, keyed on the peer identifier.
     */
    private final ConcurrentHashMap<Object, LinkedBlockingDeque<AudioFrame>> peerQueues =
            new ConcurrentHashMap<>();

    /**
     * Holds the mixed frames produced by the mixer, drained by {@link #mixedOutput()}.
     */
    private final LinkedBlockingDeque<AudioFrame> mixed = new LinkedBlockingDeque<>(PER_PEER_QUEUE_CAPACITY);

    /**
     * Identifies the implicit peer used when frames are written through {@link #write(AudioFrame)}
     * without naming a peer.
     */
    private final Object defaultPeerKey = new Object();

    /**
     * Counts the mixed frames emitted so far, monotonically increasing.
     */
    private final AtomicLong mixedFrameCount = new AtomicLong();

    /**
     * Registers a peer and returns the sink its decoded frames are written into.
     *
     * <p>If the peer is already registered its existing queue is preserved and the same logical
     * stream continues. The returned sink routes every frame written to it into the peer's queue and
     * then attempts to assemble and emit a mixed frame.
     *
     * @param peerKey the peer identifier, typically a participant's identifier; never {@code null}
     * @return a sink the caller writes the peer's decoded frames into
     * @throws NullPointerException if {@code peerKey} is {@code null}
     */
    public AudioSink addPeer(Object peerKey) {
        Objects.requireNonNull(peerKey, "peerKey cannot be null");
        peerQueues.computeIfAbsent(peerKey,
                k -> new LinkedBlockingDeque<>(PER_PEER_QUEUE_CAPACITY));
        return frame -> writeFor(peerKey, frame);
    }

    /**
     * Unregisters a peer and discards its queued frames.
     *
     * <p>Any frame subsequently written for the peer starts a fresh queue, so removing a peer that
     * has left the call stops it from blocking emission of further mixed frames.
     *
     * @param peerKey the peer identifier; never {@code null}
     * @throws NullPointerException if {@code peerKey} is {@code null}
     */
    public void removePeer(Object peerKey) {
        Objects.requireNonNull(peerKey, "peerKey cannot be null");
        peerQueues.remove(peerKey);
    }

    /**
     * Returns the combined output of the mixer as a single source.
     *
     * <p>Each {@link AudioSource#next()} call on the returned source blocks until a mixed frame is
     * ready, that is until every currently-registered peer has contributed a frame. The same source
     * is the correct far-end reference to feed an echo canceller.
     *
     * @return the mixed source
     */
    public AudioSource mixedOutput() {
        return mixed::take;
    }

    /**
     * Returns the number of mixed frames emitted so far.
     *
     * @return the monotonically increasing emitted-frame count
     */
    public long mixedFrameCount() {
        return mixedFrameCount.get();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Routes the frame into the implicit default peer's queue, equivalent to writing it through
     * the sink returned by {@link #addPeer(Object)} for a single unnamed stream. This is the
     * convenience path for an application that has only one inbound stream and just wants a
     * write handle.
     */
    @Override
    public void write(AudioFrame frame) throws InterruptedException {
        writeFor(defaultPeerKey, frame);
    }

    /**
     * Routes one frame into the named peer's queue and then attempts to emit a mixed frame.
     *
     * <p>Creates the peer's queue on first use, blocks while that queue is full to propagate
     * backpressure, and triggers an emission attempt after the frame is queued.
     *
     * @param peerKey the peer identifier
     * @param frame   the frame to enqueue; never {@code null}
     * @throws NullPointerException if {@code frame} is {@code null}
     * @throws InterruptedException if the calling thread is interrupted while the peer queue is full
     */
    private void writeFor(Object peerKey, AudioFrame frame) throws InterruptedException {
        Objects.requireNonNull(frame, "frame cannot be null");
        var queue = peerQueues.computeIfAbsent(peerKey,
                k -> new LinkedBlockingDeque<>(PER_PEER_QUEUE_CAPACITY));
        queue.put(frame);
        tryEmit();
    }

    /**
     * Emits one mixed frame when every peer queue has a head frame to contribute.
     *
     * <p>Returns without emitting while any registered peer queue is empty, so a single lagging peer
     * holds back the mix until it catches up. When every queue is ready, drains one frame from each,
     * sums the overlapping samples into a wider accumulator, clips each summed sample to the signed
     * 16-bit range, and publishes the result with the first contributing frame's presentation
     * timestamp.
     *
     * @throws InterruptedException if the calling thread is interrupted while the output queue is full
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
