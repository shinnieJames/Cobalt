package com.github.auties00.cobalt.call.interaction;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds the per-call bookkeeping for the in-call DataChannel interaction pipeline.
 *
 * <p>The reaction interaction carries a monotonically increasing {@code transaction_id} so the receiver can deduplicate
 * the unreliable retransmissions of the same {@link com.github.auties00.cobalt.model.call.datachannel.AppDataMessage
 * AppDataMessage}; that counter is owned here and taken via {@link #nextTransactionId()} once per logical reaction. The
 * type also retains a per-stream SSRC, sequence, and rolling timestamp triple for each logical RTP stream (a reaction
 * stream, a control stream for raise-hand and lower-hand gestures, peer-mute requests, and key-frame requests, and a
 * video-upgrade stream); these mirror the SSRC space the live voip stack mints for the media-plane interactions, are
 * randomized per call, and stay stable for its lifetime. The accessors are thread-safe: the transaction id, sequence,
 * and timestamp advance atomically.
 *
 * @implNote This implementation seeds each stream's SSRC and starting timestamp from {@link ThreadLocalRandom} at
 * construction. The sequence number is 16-bit and wraps modulo 65536; the timestamp is 32-bit and advances by
 * {@link #TIMESTAMP_STEP} per packet. The reaction {@code transaction_id} starts at one and increments per reaction,
 * matching the cadence observed in live captures.
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
     * Holds the next reaction {@code transaction_id}, advanced by one on each {@link #nextTransactionId()} take.
     */
    private final AtomicLong transactionId;

    /**
     * Constructs a fresh per-call state with an independently randomized counter triple for every {@link Stream} and a
     * reaction transaction id that starts at one.
     */
    public InteractionStreamState() {
        var rng = ThreadLocalRandom.current();
        this.streams = new ConcurrentHashMap<>(3);
        for (var stream : Stream.values()) {
            streams.put(stream, new Counters(rng));
        }
        this.transactionId = new AtomicLong(1);
    }

    /**
     * Atomically takes and returns the next reaction {@code transaction_id}, advancing the counter by one.
     *
     * <p>The value is written into the {@link com.github.auties00.cobalt.model.call.datachannel.ReactionInfo
     * ReactionInfo} of the next outgoing reaction so the receiver can suppress its own echoed reaction and deduplicate
     * the DataChannel retransmissions of the same reaction (the channel is {@code maxRetransmits=0} unreliable, so the
     * same id is re-sent verbatim several times).
     *
     * @return the reaction transaction id to write into the next reaction, starting at one
     */
    public long nextTransactionId() {
        return transactionId.getAndIncrement();
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
