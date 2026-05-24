package com.github.auties00.cobalt.call.internal.interaction;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-call RTP-stream bookkeeping for the in-call DataChannel
 * interaction pipeline.
 *
 * @apiNote WhatsApp Web's wasm multiplexes the five interaction types
 * across three logical RTP streams (each with its own SSRC, sequence
 * counter, and rolling timestamp): a "reaction" stream, a "hand and
 * peer-control" stream (raise/lower hand, peer mute, key-frame
 * request), and a "video upgrade" stream. SSRCs are randomized per
 * call. This class owns the three counter triples and hands them to
 * {@link CallInteractionEncoder} as each packet is built.
 * @implNote This implementation seeds SSRCs from
 * {@link ThreadLocalRandom} at construction; the sequence number is
 * 16-bit and wraps modulo {@code 65536}; the timestamp is 32-bit and
 * increments by {@link #TIMESTAMP_STEP} per packet to mimic the
 * 50-tick cadence observed in live captures (see
 * {@code reference_wa_voip_interaction_wire_format} memory).
 */
@WhatsAppWebModule(moduleName = "WAWebVoipStackInterfaceWeb")
public final class InteractionStreamState {
    /**
     * Per-packet timestamp increment matching the live-capture
     * observation (each retransmit bumps the 32-bit timestamp by
     * {@code 50}).
     */
    public static final int TIMESTAMP_STEP = 50;

    /**
     * Logical RTP-stream identifiers. Each interaction kind maps to
     * exactly one stream.
     */
    public enum Stream {
        /**
         * Carries reactions (byte0=0x80, byte1=0x77, RTP PT=119).
         */
        REACTION,
        /**
         * Carries raise/lower hand, peer-mute requests, and
         * key-frame requests. byte0 varies (0x81 RTCP for hand,
         * 0x90 RTP for requests); byte1 is 0xc8 or 0x78
         * respectively.
         */
        CONTROL,
        /**
         * Carries video-upgrade requests (byte0=0x91, byte1=0xc8,
         * RTCP with extension).
         */
        VIDEO_UPGRADE
    }

    /**
     * Per-stream mutable counter triple.
     */
    private static final class Counters {
        /**
         * The SSRC, randomized once at construction.
         */
        final int ssrc;
        /**
         * The next sequence number, incrementing modulo 2^16.
         */
        final AtomicInteger sequence = new AtomicInteger(0);
        /**
         * The next timestamp, incrementing by {@link #TIMESTAMP_STEP}
         * per packet.
         */
        final AtomicInteger timestamp;

        /**
         * Constructs counters with a random SSRC and starting
         * timestamp.
         *
         * @param rng the RNG to seed from
         */
        Counters(ThreadLocalRandom rng) {
            this.ssrc = rng.nextInt();
            this.timestamp = new AtomicInteger(rng.nextInt());
        }
    }

    /**
     * The per-stream counter triples, keyed by {@link Stream}.
     */
    private final Map<Stream, Counters> streams;

    /**
     * Constructs a fresh per-call state with randomized SSRCs.
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
     * @param stream the stream
     * @return the SSRC (signed 32-bit treated as unsigned on the wire)
     */
    public int ssrc(Stream stream) {
        return streams.get(stream).ssrc;
    }

    /**
     * Atomically takes the next sequence number for the stream,
     * wrapping at {@code 0x10000}.
     *
     * @param stream the stream
     * @return the sequence number to write into the next packet
     */
    public int nextSequence(Stream stream) {
        return streams.get(stream).sequence.getAndUpdate(s -> (s + 1) & 0xffff);
    }

    /**
     * Atomically takes the next timestamp for the stream, advancing
     * by {@link #TIMESTAMP_STEP}.
     *
     * @param stream the stream
     * @return the timestamp to write into the next packet
     */
    public int nextTimestamp(Stream stream) {
        return streams.get(stream).timestamp.getAndAdd(TIMESTAMP_STEP);
    }
}
