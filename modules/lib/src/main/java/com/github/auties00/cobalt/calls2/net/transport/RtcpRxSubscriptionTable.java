package com.github.auties00.cobalt.calls2.net.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The hop-by-hop RTCP-feedback subscription table held by the relay transport.
 *
 * <p>Tracks, per media SSRC, which RTCP feedback the client wants the
 * selective-forwarding unit to forward. The table holds at most
 * {@value #MAX_ENTRIES} entries: a {@link #subscribe(int, int, int) subscribe} for a
 * media SSRC already present overwrites that entry's peer SSRC and feedback flags in
 * place, while a subscribe for a new media SSRC occupies a free slot. Once every slot
 * is occupied a subscribe for a new media SSRC is rejected, matching the fixed-capacity
 * native table rather than growing without bound. {@link #unsubscribe(int)} frees the
 * slot for a media SSRC, and {@link #entries()} snapshots the live subscriptions for
 * the RTCP-feedback writer.
 *
 * <p>This table is not thread-safe: a single owner thread, the call transport thread
 * that also drives the {@link LiveSubscriptionPublisher}, performs all subscribe,
 * unsubscribe, and snapshot operations.
 *
 * @implNote This implementation reproduces the fixed 96-slot feedback table populated by
 * {@code hbh_srtp_fb_subscribe} (fn4814) in {@code wa_hbh_srtp_relay.cc} of the wa-voip WASM module
 * {@code ff-tScznZ8P}, where each of the 96 slots is {@code 0x60} bytes carrying a {@code used} flag,
 * the peer SSRC, the media SSRC, and the feedback flags. The native code scans the table for a slot
 * whose media SSRC matches to update it and otherwise takes the first free slot, refusing to register
 * once all 96 are used; this port keys an {@link ArrayList} bounded at {@value #MAX_ENTRIES} on the
 * media SSRC to the same effect, dropping the native {@code used} byte because list membership records
 * occupancy. The slot count of 96 is preserved exactly so the table saturates at the same point as the
 * engine, which bounds how many distinct media streams a single call can request feedback for.
 */
public final class RtcpRxSubscriptionTable {
    /**
     * The fixed number of feedback subscriptions the table can hold.
     *
     * <p>Matches the native 96-slot table; a {@link #subscribe(int, int, int)} for a new
     * media SSRC past this count is rejected.
     */
    public static final int MAX_ENTRIES = 96;

    /**
     * The live feedback subscriptions, one per subscribed media SSRC.
     *
     * <p>Holds at most {@value #MAX_ENTRIES} entries; the index of an entry is its slot.
     * A media SSRC appears at most once, so a re-subscribe replaces the matching entry
     * rather than appending a duplicate.
     */
    private final List<RtcpRxSubscriptionEntry> entries;

    /**
     * Constructs an empty feedback table.
     *
     * <p>The table starts with no subscriptions; {@link #size()} is zero and
     * {@link #entries()} is empty until the first {@link #subscribe(int, int, int)}.
     */
    public RtcpRxSubscriptionTable() {
        this.entries = new ArrayList<>(MAX_ENTRIES);
    }

    /**
     * Registers or updates the feedback subscription for a media SSRC.
     *
     * <p>When the media SSRC is already subscribed the existing entry's peer SSRC and
     * feedback flags are overwritten with the supplied values and the entry keeps its
     * slot. When the media SSRC is new and a slot is free a new entry is appended. When
     * the media SSRC is new and all {@value #MAX_ENTRIES} slots are occupied the call is
     * rejected and the table is left unchanged. The flags are a bitmask of
     * {@link RtcpRxSubscriptionEntry#FLAG_NACK}, {@link RtcpRxSubscriptionEntry#FLAG_PLI},
     * and {@link RtcpRxSubscriptionEntry#FLAG_FIR}.
     *
     * @param peerSsrc  the SSRC of the peer that owns the feedback relationship
     * @param mediaSsrc the media SSRC to subscribe feedback for
     * @param flags     the bitmask of requested feedback kinds
     * @return {@code true} if the subscription was registered or updated, {@code false}
     *         if the table was full and the media SSRC was not already present
     */
    public boolean subscribe(int peerSsrc, int mediaSsrc, int flags) {
        for (var i = 0; i < entries.size(); i++) {
            if (entries.get(i).mediaSsrc() == mediaSsrc) {
                entries.set(i, new RtcpRxSubscriptionEntry(peerSsrc, mediaSsrc, flags));
                return true;
            }
        }
        if (entries.size() >= MAX_ENTRIES) {
            return false;
        }
        entries.add(new RtcpRxSubscriptionEntry(peerSsrc, mediaSsrc, flags));
        return true;
    }

    /**
     * Removes the feedback subscription for a media SSRC.
     *
     * <p>Frees the slot the media SSRC occupied so a later {@link #subscribe(int, int, int)}
     * can reuse it. Removing a media SSRC that is not subscribed is a no-op.
     *
     * @param mediaSsrc the media SSRC to unsubscribe
     * @return {@code true} if an entry was removed, {@code false} if the media SSRC was
     *         not subscribed
     */
    public boolean unsubscribe(int mediaSsrc) {
        for (var i = 0; i < entries.size(); i++) {
            if (entries.get(i).mediaSsrc() == mediaSsrc) {
                entries.remove(i);
                return true;
            }
        }
        return false;
    }

    /**
     * Returns an immutable snapshot of the live feedback subscriptions.
     *
     * <p>The returned list is a point-in-time copy in slot order; later
     * {@link #subscribe(int, int, int)} or {@link #unsubscribe(int)} calls do not affect
     * it. The RTCP-feedback writer consumes this snapshot to emit the feedback the relay
     * should forward.
     *
     * @return an unmodifiable list of the current entries, never {@code null}
     */
    public List<RtcpRxSubscriptionEntry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /**
     * Returns the number of feedback subscriptions currently held.
     *
     * @return the live entry count, between zero and {@value #MAX_ENTRIES}
     */
    public int size() {
        return entries.size();
    }

    /**
     * Reports whether the table holds no feedback subscriptions.
     *
     * @return {@code true} if there are no subscriptions, {@code false} otherwise
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Removes every feedback subscription, leaving the table empty.
     *
     * <p>Invoked when the call tears down so the table holds no stale SSRCs for a later
     * call; after this call {@link #isEmpty()} is {@code true}.
     */
    public void clear() {
        entries.clear();
    }
}
