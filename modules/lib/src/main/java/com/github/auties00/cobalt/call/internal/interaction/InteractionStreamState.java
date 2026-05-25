package com.github.auties00.cobalt.call.internal.interaction;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds the per-call RTP-stream bookkeeping for the in-call DataChannel interaction pipeline.
 *
 * <p>The five interaction kinds are multiplexed across three logical RTP streams, each carrying its own SSRC, sequence
 * counter, and rolling timestamp: a reaction stream, a control stream for raise-hand and lower-hand gestures, peer-mute
 * requests, and key-frame requests, and a video-upgrade stream. Each kind maps to exactly one {@link Stream}. The SSRCs
 * are randomized per call, and the counters are owned here and consumed by {@link CallInteractionEncoder} as each packet
 * is built. The accessors are thread-safe: sequence and timestamp advance atomically.
 *
 * @implNote This implementation seeds each stream's SSRC and starting timestamp from {@link ThreadLocalRandom} at
 * construction. The sequence number is 16-bit and wraps modulo 65536; the timestamp is 32-bit and advances by
 * {@link #TIMESTAMP_STEP} per packet to mimic the cadence observed in live captures.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipStackInterfaceWeb")
public final class InteractionStreamState {
    /**
     * Holds the per-packet timestamp increment applied to each stream's 32-bit timestamp.
     *
     * @implNote This implementation uses 50, the per-retransmit timestamp bump observed in live captures.
     */
    public static final int TIMESTAMP_STEP = 50;

    /**
     * Enumerates the logical RTP streams onto which interactions are multiplexed.
     *
     * <p>Each interaction kind maps to exactly one stream, and each stream owns an independent SSRC, sequence counter,
     * and timestamp.
     */
    public enum Stream {
        /**
         * Carries reactions, framed with byte 0 {@code 0x80} and byte 1 {@code 0x77}, RTP payload type 119.
         */
        REACTION,

        /**
         * Carries raise-hand and lower-hand gestures, peer-mute requests, and key-frame requests.
         *
         * <p>Byte 0 varies by interaction: {@code 0x81} for the RTCP-shaped hand toggle and {@code 0x90} for the
         * RTP-shaped requests; byte 1 is correspondingly {@code 0xc8} or {@code 0x78}.
         */
        CONTROL,

        /**
         * Carries video-upgrade requests, framed with byte 0 {@code 0x91} and byte 1 {@code 0xc8}, RTCP with extension.
         */
        VIDEO_UPGRADE
    }

    /**
     * Holds the mutable SSRC, sequence, and timestamp triple for one {@link Stream}.
     */
    private static final class Counters {
        /**
         * Holds the SSRC, randomized once at construction and constant for the life of the call.
         */
        final int ssrc;

        /**
         * Holds the next sequence number, advanced modulo 65536 by each take.
         */
        final AtomicInteger sequence = new AtomicInteger(0);

        /**
         * Holds the next timestamp, advanced by {@link #TIMESTAMP_STEP} on each take.
         */
        final AtomicInteger timestamp;

        /**
         * Constructs a counter triple with a randomized SSRC and starting timestamp.
         *
         * @param rng the random source from which the SSRC and starting timestamp are drawn
         */
        Counters(ThreadLocalRandom rng) {
            this.ssrc = rng.nextInt();
            this.timestamp = new AtomicInteger(rng.nextInt());
        }
    }

    /**
     * Maps each {@link Stream} to its counter triple.
     */
    private final Map<Stream, Counters> streams;

    /**
     * Constructs a fresh per-call state with an independently randomized counter triple for every {@link Stream}.
     */
    public InteractionStreamState() {
        var rng = ThreadLocalRandom.current();
        this.streams = new ConcurrentHashMap<>(3);
        for (var stream : Stream.values()) {
            streams.put(stream, new Counters(rng));
        }
    }

    /**
     * Returns the SSRC of the given stream.
     *
     * <p>The SSRC is fixed for the life of the call and is written, unchanged, into the header of every packet on the
     * stream.
     *
     * @param stream the stream whose SSRC is returned
     * @return the SSRC, a signed 32-bit value treated as unsigned on the wire
     */
    public int ssrc(Stream stream) {
        return streams.get(stream).ssrc;
    }

    /**
     * Atomically takes and returns the next sequence number for the stream, advancing the counter modulo 65536.
     *
     * @param stream the stream whose sequence counter is advanced
     * @return the 16-bit sequence number to write into the next packet
     */
    public int nextSequence(Stream stream) {
        return streams.get(stream).sequence.getAndUpdate(s -> (s + 1) & 0xffff);
    }

    /**
     * Atomically takes and returns the next timestamp for the stream, advancing the counter by {@link #TIMESTAMP_STEP}.
     *
     * @param stream the stream whose timestamp counter is advanced
     * @return the 32-bit timestamp to write into the next packet
     */
    public int nextTimestamp(Stream stream) {
        return streams.get(stream).timestamp.getAndAdd(TIMESTAMP_STEP);
    }
}
