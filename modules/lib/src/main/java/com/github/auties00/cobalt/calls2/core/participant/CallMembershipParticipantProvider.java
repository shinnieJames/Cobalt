package com.github.auties00.cobalt.calls2.core.participant;

import java.util.List;
import java.util.Objects;

/**
 * A {@link ParticipantProvider} backed by a {@link CallMembership}'s per-slot {@link CallParticipant}
 * aggregates.
 *
 * <p>This is the flyweight read seam {@link CallMembership#participantProvider()} hands out: it owns no
 * participant state of its own and holds only a reference to the membership manager, forwarding each query to
 * the manager's snapshot primitives. Every read takes a fresh snapshot under the membership lock, so a
 * returned {@link ParticipantView} is internally consistent and no live {@link CallParticipant} reference
 * escapes; the wider engine reads participants only through these snapshots. The default query accessors of
 * {@link ParticipantProvider} (first connected peer, active participant by device JID, per-stream subscriber
 * count, allocated count) ride on the three primitives this class supplies.
 *
 * <p>This provider is a thin view over its membership manager: it is as thread-safe as the manager, since
 * every method it implements forwards to a manager method that takes the per-call membership lock. It is
 * cheap to construct, so a caller may obtain a fresh one per use rather than caching it.
 *
 * @implNote This implementation maps the native participant-provider read seam ({@code participant_provider.cc}
 * in the wa-voip WASM module {@code ff-tScznZ8P}) onto a {@link CallMembership}: the native provider reads the
 * group-info-backed participant array, and here that array is the membership's slot set. {@link #views()} is
 * the iteration {@code participant_provider.cc} performs over every allocated participant, taken under the
 * membership lock; {@link #selfView()} resolves the local participant the native provider tracks; and
 * {@link #isValid()} reproduces the native validity check that the provider is backed by a live participant
 * set. The connected-peer, by-device, and subscriber-count accessors are inherited from
 * {@link ParticipantProvider}, which already reproduces fn11001/fn11004/fn11002 over these snapshots.
 * @see CallMembership#participantProvider()
 * @see ParticipantProvider
 */
final class CallMembershipParticipantProvider implements ParticipantProvider {
    /**
     * The membership manager whose slot aggregates this provider snapshots.
     */
    private final CallMembership membership;

    /**
     * Constructs a provider over the given membership manager.
     *
     * @param membership the membership manager to read participant snapshots from
     * @throws NullPointerException if {@code membership} is {@code null}
     */
    CallMembershipParticipantProvider(CallMembership membership) {
        this.membership = Objects.requireNonNull(membership, "membership cannot be null");
    }

    @Override
    public boolean isValid() {
        return membership.hasParticipants();
    }

    @Override
    public List<ParticipantView> views() {
        return membership.participantViews();
    }

    @Override
    public ParticipantView selfView() {
        return membership.participantSelfView();
    }

    // TODO (item 8): override the ParticipantProvider first-active-peer accessor here with a
    //  CallMembership.firstActivePeerView() that scans selfView + views under a single membership lock,
    //  closing the consistency window where a reconcile lands between this provider's separate selfView()
    //  and views() snapshots. Deferred: the accessor to override is a default on ParticipantProvider
    //  (outside this owned set) and the single-lock scan primitive must be added to CallMembership, so the
    //  two must be introduced together to keep the projection identical. The sibling half of item 8 (running
    //  participant projection + control dispatch under one lock in Calls2LifecycleController.handlePeerVideoState
    //  and handlePeerScreenShare) lives in the lifecycle controller and is deferred with it.
}
