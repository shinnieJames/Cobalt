package com.github.auties00.cobalt.calls2.signaling;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Holds the bounded, deduplicated relay list of a single call.
 *
 * <p>A call maintains at most {@value #MAX_RELAYS} relay candidates. Candidates are inserted or
 * updated through {@link #addOrUpdate(RelayCandidate)}, which keys on the relay identity pair (the
 * {@link RelayCandidate#relayId() relay id} and the {@link RelayCandidate#protoAfFlag() protocol
 * address-family flag}): a candidate whose pair already exists replaces the stored one in place, and a
 * candidate with a new pair is appended. The list rejects two error conditions exactly as the engine
 * does: a candidate whose pair matches a stored relay but whose {@link RelayCandidate#portByte() port
 * byte} differs is refused, and a new candidate is refused once the list already holds
 * {@value #MAX_RELAYS} entries.
 *
 * <p>Insertion order is preserved: an update keeps the existing slot, and an append goes to the end.
 * The list is not thread-safe; the call's relay list is mutated only behind the call's lock.
 *
 * <p>This is the re-derived relay list; it supersedes the legacy relay model. It iterates in
 * insertion order over its {@link RelayCandidate} entries.
 *
 * @implNote This implementation ports {@code wa_call_add_or_update_relay_item} (fn11778) from the
 * wa-voip WASM module {@code ff-tScznZ8P} ({@code transport/call_relay.cc}). The cap of
 * {@value #MAX_RELAYS} is the recovered {@code MAX_RELAYS} constant: the engine rejects a list whose
 * count already exceeds eight and refuses an append at exactly eight with the diagnostic "relay list
 * is full". The dedupe match is the engine's linear scan comparing only the two bytes {@code relay_id}
 * (item offset {@code +0x00}) and {@code proto_af_flag} (item offset {@code +0x34}); the
 * {@code port_byte} (item offset {@code +0x01}) is not part of the match. When the two bytes match, a
 * differing {@code port_byte} raises the engine's "relay token cannot be different for the same relay"
 * error, so the {@code port_byte} acts as a consistency check on the matched relay rather than as a
 * dedupe key. The native engine overwrites the matched slot's auth and encrypted relay tokens in place
 * without comparing them, so this implementation does not reject on a token-byte difference. The
 * "address with neither IPv4 nor IPv6" rejection is enforced upstream by the {@link RelayCandidate}
 * constructor.
 *
 * @see RelayCandidate
 */
public final class RelayCandidateList implements Iterable<RelayCandidate> {
    /**
     * The maximum number of relay candidates a call may hold.
     */
    public static final int MAX_RELAYS = 8;

    /**
     * Holds the relay candidates in insertion order.
     *
     * <p>Backed by an {@link ArrayList} bounded to {@value #MAX_RELAYS} entries; the dedupe scan and
     * the order-preserving update both operate over this list.
     */
    private final List<RelayCandidate> candidates;

    /**
     * Constructs an empty relay list.
     */
    public RelayCandidateList() {
        this.candidates = new ArrayList<>(MAX_RELAYS);
    }

    /**
     * Inserts or updates a relay candidate, keyed by its relay identity pair.
     *
     * <p>When a stored candidate shares the given candidate's relay identity pair (its
     * {@link RelayCandidate#relayId() relay id} and {@link RelayCandidate#protoAfFlag() protocol
     * address-family flag}), the stored candidate is replaced in place, preserving its slot; when no
     * stored candidate matches, the given candidate is appended. Before replacing, the
     * {@link RelayCandidate#portByte() port byte} is checked for consistency: a matched relay whose port
     * byte differs is rejected because the engine forbids the same relay carrying a different port byte.
     * The matched slot's tokens are not compared; a matched candidate simply overwrites the stored one,
     * mirroring the native in-place token copy.
     *
     * @param candidate the candidate to insert or update; never {@code null}
     * @throws NullPointerException     if {@code candidate} is {@code null}
     * @throws IllegalStateException    if the candidate has a new identity pair and the list already
     *                                  holds {@value #MAX_RELAYS} entries
     * @throws IllegalArgumentException if the candidate matches a stored relay's identity pair but
     *                                  carries a different port byte
     */
    public void addOrUpdate(RelayCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate cannot be null");
        for (var index = 0; index < candidates.size(); index++) {
            var existing = candidates.get(index);
            if (sameRelay(existing, candidate)) {
                if (existing.portByte() != candidate.portByte()) {
                    throw new IllegalArgumentException("relay token cannot be different for the same relay");
                }
                candidates.set(index, candidate);
                return;
            }
        }
        if (candidates.size() >= MAX_RELAYS) {
            throw new IllegalStateException("relay list is full");
        }
        candidates.add(candidate);
    }

    /**
     * Returns the stored candidate sharing the relay identity pair of the given candidate, if any.
     *
     * <p>The identity pair is the {@link RelayCandidate#relayId() relay id} and the
     * {@link RelayCandidate#protoAfFlag() protocol address-family flag}; the
     * {@link RelayCandidate#portByte() port byte} is not part of the match, so a stored relay with the
     * same pair but a different port byte is still returned.
     *
     * @param candidate the candidate whose identity pair to match; never {@code null}
     * @return an {@link Optional} holding the matching stored candidate, or empty when none matches
     * @throws NullPointerException if {@code candidate} is {@code null}
     */
    public Optional<RelayCandidate> find(RelayCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate cannot be null");
        for (var existing : candidates) {
            if (sameRelay(existing, candidate)) {
                return Optional.of(existing);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns whether two candidates share the relay identity pair the list deduplicates on.
     *
     * <p>The pair is the {@link RelayCandidate#relayId() relay id} and the
     * {@link RelayCandidate#protoAfFlag() protocol address-family flag}, matching the two bytes the
     * engine's linear scan compares; the {@link RelayCandidate#portByte() port byte} is deliberately
     * excluded because the engine treats a differing port byte on a matched relay as a rejection rather
     * than as a distinct relay.
     *
     * @param left  the stored candidate; never {@code null}
     * @param right the candidate being inserted or looked up; never {@code null}
     * @return {@code true} when both candidates carry the same relay id and protocol address-family flag
     */
    private static boolean sameRelay(RelayCandidate left, RelayCandidate right) {
        return left.relayId() == right.relayId()
                && left.protoAfFlag() == right.protoAfFlag();
    }

    /**
     * Returns the number of relay candidates currently held.
     *
     * @return the candidate count, never greater than {@value #MAX_RELAYS}
     */
    public int size() {
        return candidates.size();
    }

    /**
     * Returns whether this list holds no relay candidates.
     *
     * @return {@code true} when the list is empty
     */
    public boolean isEmpty() {
        return candidates.isEmpty();
    }

    /**
     * Returns whether this list cannot accept a candidate with a new relay identity pair.
     *
     * @return {@code true} when the list already holds {@value #MAX_RELAYS} entries
     */
    public boolean isFull() {
        return candidates.size() >= MAX_RELAYS;
    }

    /**
     * Returns an unmodifiable view of the relay candidates in insertion order.
     *
     * @return the candidates; never {@code null}, possibly empty
     */
    public List<RelayCandidate> candidates() {
        // FIXME: this returns a detached snapshot while the javadoc promises an unmodifiable view, so a
        //  caller holding the result sees stale data after addOrUpdate; the faithful fix is
        //  Collections.unmodifiableList(candidates) (live view), but a live view over the mutable
        //  backing ArrayList is a concurrency hazard (CME during addOrUpdate) and WA's intended
        //  snapshot-vs-view semantics are unconfirmed, so behavior is left unchanged until confirmed.
        return List.copyOf(candidates);
    }

    /**
     * Returns an iterator over the relay candidates in insertion order.
     *
     * <p>The iterator is read-only; it does not support {@link Iterator#remove()}.
     *
     * @return an iterator over an immutable snapshot of the candidates
     */
    @Override
    public Iterator<RelayCandidate> iterator() {
        return List.copyOf(candidates).iterator();
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof RelayCandidateList that
                && this.candidates.equals(that.candidates));
    }

    @Override
    public int hashCode() {
        return candidates.hashCode();
    }

    @Override
    public String toString() {
        return "RelayCandidateList[size=" + candidates.size() + ", candidates=" + candidates + ']';
    }
}
