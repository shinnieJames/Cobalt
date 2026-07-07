package com.github.auties00.cobalt.calls2.core.control;

import com.github.auties00.cobalt.model.jid.Jid;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;

/**
 * Maintains the display ordering of group-call participants and emits a ranking event when it changes.
 *
 * <p>This service ranks participants for the call grid by a fixed comparator: hand-raised participants
 * first, then active speakers, then by descending engine rank, then by ascending stable index as a
 * tie-break. It holds one {@link RankingInputs} per participant, updated as the engine observes hand
 * raises, dominant-speaker changes, and rank or index assignments, and recomputes the order on each
 * update. When the resulting order differs from the last emitted order it emits a
 * {@link CallGridRankingChanged} carrying the new ordering, the highest-ranked participant first.
 *
 * <p>The per-participant inputs and the last emitted order are kept behind a lock so a concurrent update
 * never emits a torn ranking. The service is bound to its event sink at construction; it owns no timers.
 *
 * @implNote This implementation reproduces the grid-ranking comparator of the wa-voip WASM module
 * {@code ff-tScznZ8P}: the engine orders participants by hand-raised descending, active-speaker
 * descending, rank descending, and index ascending, and emits event {@code 0x6f}
 * ({@code CallGridRankingChanged}) when that order changes. Cobalt keeps the per-participant inputs the
 * comparator reads and re-sorts on each update; the info-mutex is replaced by a {@link ReentrantLock} per
 * the threading design.
 */
public final class SpeakerRankingService {
    /**
     * Orders participants by the grid-ranking rule: hand-raised first, then active speakers, then higher
     * rank, then lower index.
     *
     * <p>A {@code true} hand-raised flag and a {@code true} active-speaker flag sort before their
     * {@code false} counterparts, a larger rank sorts before a smaller one, and a smaller index sorts
     * before a larger one as the final stable tie-break.
     */
    private static final Comparator<RankingInputs> GRID_ORDER = Comparator
            .comparingInt((RankingInputs input) -> input.handRaised() ? 0 : 1)
            .thenComparingInt(input -> input.activeSpeaker() ? 0 : 1)
            .thenComparing(Comparator.comparingInt(RankingInputs::rank).reversed())
            .thenComparingInt(RankingInputs::index);

    /**
     * The event sink the ranking-changed event is emitted into.
     */
    private final CallEventSink events;

    /**
     * The current per-participant ranking inputs, keyed by participant device JID.
     */
    private final Map<Jid, RankingInputs> inputs = new HashMap<>();

    /**
     * Guards the recompute-and-compare against the last emitted order.
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * The participant order last emitted, used to suppress an unchanged re-emit.
     */
    private List<Jid> lastOrder = List.of();

    /**
     * Constructs a speaker-ranking service bound to its event sink.
     *
     * @param events the event sink to emit the ranking-changed event into; never {@code null}
     * @throws NullPointerException if {@code events} is {@code null}
     */
    public SpeakerRankingService(CallEventSink events) {
        this.events = Objects.requireNonNull(events, "events cannot be null");
    }

    /**
     * Sets a participant's hand-raised input and recomputes the ranking.
     *
     * @param participant the device JID whose hand state changed; never {@code null}
     * @param raised      {@code true} when the hand is raised
     * @throws NullPointerException if {@code participant} is {@code null}
     */
    public void setHandRaised(Jid participant, boolean raised) {
        update(participant, current -> current.withHandRaised(raised));
    }

    /**
     * Sets a participant's active-speaker input and recomputes the ranking.
     *
     * @param participant the device JID whose dominant-speaker status changed; never {@code null}
     * @param speaking    {@code true} when the participant is the dominant speaker
     * @throws NullPointerException if {@code participant} is {@code null}
     */
    public void setActiveSpeaker(Jid participant, boolean speaking) {
        update(participant, current -> current.withActiveSpeaker(speaking));
    }

    /**
     * Sets a participant's rank and stable index inputs and recomputes the ranking.
     *
     * @param participant the device JID whose rank or index changed; never {@code null}
     * @param rank        the engine rank, higher sorting first
     * @param index       the stable index, lower sorting first as the final tie-break
     * @throws NullPointerException if {@code participant} is {@code null}
     */
    public void setRank(Jid participant, int rank, int index) {
        update(participant, current -> current.withRank(rank, index));
    }

    /**
     * Removes a participant from the ranking and recomputes it.
     *
     * <p>Used when a participant leaves the call; a participant that was not ranked leaves the ranking
     * unchanged.
     *
     * @param participant the device JID to remove; never {@code null}
     * @throws NullPointerException if {@code participant} is {@code null}
     */
    public void remove(Jid participant) {
        Objects.requireNonNull(participant, "participant cannot be null");
        lock.lock();
        try {
            if (inputs.remove(participant) != null) {
                recomputeAndEmit();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the current ranking, the highest-ranked participant first.
     *
     * @return the current participant order; never {@code null}, unmodifiable
     */
    public List<Jid> ranking() {
        lock.lock();
        try {
            return lastOrder;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Applies a mutation to a participant's inputs under the lock and recomputes the ranking.
     *
     * <p>The mutation is computed from the participant's current inputs, or a default-valued entry when the
     * participant is new, then stored; the ranking is recomputed and a change emitted.
     *
     * @param participant the device JID whose inputs change
     * @param mutation    the mutation from the current inputs to the updated inputs
     */
    private void update(Jid participant, UnaryOperator<RankingInputs> mutation) {
        Objects.requireNonNull(participant, "participant cannot be null");
        lock.lock();
        try {
            var current = inputs.get(participant);
            if (current == null) {
                current = RankingInputs.initial(participant);
            }
            inputs.put(participant, mutation.apply(current));
            recomputeAndEmit();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Recomputes the participant order and emits a ranking-changed event when it differs.
     *
     * <p>Sorts the current inputs by the grid order, compares the resulting JID order against the last
     * emitted order, and emits a {@link CallGridRankingChanged} and records the new order only when they
     * differ. Called under the lock. The recompute is unconditional, with no feature-flag gate, matching the
     * event-driven engine path this service models.
     *
     * @implNote This implementation models the hand-raise-driven ranking recompute
     * {@code update_hand_raise_ranking} (fn11127, {@code features/grid_ranking.cc}), which logs and calls the
     * recompute (fn11125) then emits event {@code 0x6f} ({@code CallGridRankingChanged}) with no
     * feature-flag gate of its own. The {@code enable_speaker_ranking} / {@code is_speaker_ranking_enabled}
     * gate and the speaker-ranking timer cadence apply to the separate, periodic active-speaker recompute
     * ({@code update_active_speaker_ranking}, fn10933), not to this event-driven path, and that gate is a
     * per-call {@code vp->} voip-settings field rather than a server AB-prop, so it is not threaded here. The
     * comparator dimensions are confirmed complete against {@code argsort_for_call_grid_rank_comparator}
     * (fn11126): it orders by hand-raised (byte {@code +9}) descending, active-speaker (byte {@code +8})
     * descending, rank (int {@code +4}) descending, and index (int {@code +0}) ascending, with no
     * screen-share or video-on dimension, exactly the four {@link #GRID_ORDER} keys.
     */
    private void recomputeAndEmit() {
        var order = inputs.values().stream()
                .sorted(GRID_ORDER)
                .map(RankingInputs::participant)
                .toList();
        if (!order.equals(lastOrder)) {
            lastOrder = order;
            events.emit(new CallGridRankingChanged(order));
        }
    }

    /**
     * The comparator inputs held for one participant: identity, hand-raised, active-speaker, rank, and
     * stable index.
     *
     * @param participant  the participant device JID
     * @param handRaised   whether the participant has a hand raised
     * @param activeSpeaker whether the participant is the dominant speaker
     * @param rank         the engine rank, higher sorting first
     * @param index        the stable index, lower sorting first as the final tie-break
     */
    public record RankingInputs(Jid participant, boolean handRaised, boolean activeSpeaker, int rank, int index) {
        /**
         * Validates the record components.
         *
         * @throws NullPointerException if {@code participant} is {@code null}
         */
        public RankingInputs {
            Objects.requireNonNull(participant, "participant cannot be null");
        }

        /**
         * Returns the default-valued inputs for a newly ranked participant.
         *
         * <p>A new participant starts with no hand raised, not speaking, rank {@code 0}, and index
         * {@link Integer#MAX_VALUE} so it sorts last until a real index is assigned.
         *
         * @param participant the participant device JID
         * @return the initial inputs for the participant
         */
        static RankingInputs initial(Jid participant) {
            return new RankingInputs(participant, false, false, 0, Integer.MAX_VALUE);
        }

        /**
         * Returns a copy with the hand-raised flag set to the given value.
         *
         * @param raised the new hand-raised flag
         * @return the updated inputs
         */
        RankingInputs withHandRaised(boolean raised) {
            return new RankingInputs(participant, raised, activeSpeaker, rank, index);
        }

        /**
         * Returns a copy with the active-speaker flag set to the given value.
         *
         * @param speaking the new active-speaker flag
         * @return the updated inputs
         */
        RankingInputs withActiveSpeaker(boolean speaking) {
            return new RankingInputs(participant, handRaised, speaking, rank, index);
        }

        /**
         * Returns a copy with the rank and stable index set to the given values.
         *
         * @param rank  the new engine rank
         * @param index the new stable index
         * @return the updated inputs
         */
        RankingInputs withRank(int rank, int index) {
            return new RankingInputs(participant, handRaised, activeSpeaker, rank, index);
        }
    }
}
