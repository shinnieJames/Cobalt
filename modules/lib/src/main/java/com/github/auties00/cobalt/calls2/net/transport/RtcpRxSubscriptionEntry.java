package com.github.auties00.cobalt.calls2.net.transport;

import java.util.Objects;

/**
 * One hop-by-hop RTCP-feedback subscription toward the relay.
 *
 * <p>A client tells the selective-forwarding unit which RTCP feedback it wants the unit
 * to forward for a given media stream by registering an entry in the hop-by-hop
 * feedback table. Each entry binds the {@linkplain #mediaSsrc() subscribed media SSRC}
 * to the {@linkplain #peerSsrc() owning peer SSRC} and a bitmask of the
 * {@linkplain #flags() feedback kinds} the subscriber wants relayed, drawn from
 * {@link #FLAG_NACK}, {@link #FLAG_PLI}, and {@link #FLAG_FIR}. The relay then forwards
 * only the selected feedback packets between the two SSRCs instead of every report.
 *
 * @param peerSsrc  the SSRC of the peer that owns the feedback relationship
 * @param mediaSsrc the media SSRC the feedback applies to
 * @param flags     the bitmask of requested feedback kinds
 * @implNote This implementation models one slot of the 96-entry feedback table populated by
 * {@code hbh_srtp_fb_subscribe} (fn4814) in {@code wa_hbh_srtp_relay.cc} of the wa-voip WASM module
 * {@code ff-tScznZ8P}. The native slot is {@code 0x60} bytes carrying a {@code used} flag at offset
 * {@code 0}, the peer SSRC at offset {@code 4}, the media SSRC at offset {@code 8}, and the
 * feedback flags at offset {@code 12} (logged as {@code %02X}). The {@code used} byte is not modelled
 * here because table occupancy is tracked by {@link RtcpRxSubscriptionTable} through the presence of an
 * entry rather than an in-band flag. The flag-bit assignment for NACK, PLI, and FIR is taken from the
 * RTCP feedback message types the relay forwards; the exact native bit positions beyond their relative
 * ordering are not separately recovered, so the constants here are defined as distinct single bits.
 */
public record RtcpRxSubscriptionEntry(int peerSsrc, int mediaSsrc, int flags) {
    /**
     * Feedback flag selecting negative-acknowledgement (NACK) packets.
     *
     * <p>When set in {@link #flags()} the relay forwards NACK feedback so the sender can
     * retransmit the lost packets the subscriber reports missing.
     */
    public static final int FLAG_NACK = 0x01;

    /**
     * Feedback flag selecting picture-loss-indication (PLI) packets.
     *
     * <p>When set in {@link #flags()} the relay forwards PLI feedback so the video sender
     * emits a fresh key frame when the subscriber's decoder loses reference frames.
     */
    public static final int FLAG_PLI = 0x02;

    /**
     * Feedback flag selecting full-intra-request (FIR) packets.
     *
     * <p>When set in {@link #flags()} the relay forwards FIR feedback so the video sender
     * produces a decoder refresh point on demand.
     */
    public static final int FLAG_FIR = 0x04;

    /**
     * Returns whether NACK feedback is requested by this entry.
     *
     * @return {@code true} when {@link #FLAG_NACK} is set in {@link #flags()}
     */
    public boolean wantsNack() {
        return (flags & FLAG_NACK) != 0;
    }

    /**
     * Returns whether PLI feedback is requested by this entry.
     *
     * @return {@code true} when {@link #FLAG_PLI} is set in {@link #flags()}
     */
    public boolean wantsPli() {
        return (flags & FLAG_PLI) != 0;
    }

    /**
     * Returns whether FIR feedback is requested by this entry.
     *
     * @return {@code true} when {@link #FLAG_FIR} is set in {@link #flags()}
     */
    public boolean wantsFir() {
        return (flags & FLAG_FIR) != 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(peerSsrc, mediaSsrc, flags);
    }

    @Override
    public String toString() {
        return "RtcpRxSubscriptionEntry[peerSsrc=" + peerSsrc
                + ", mediaSsrc=" + mediaSsrc
                + ", flags=0x" + Integer.toHexString(flags) + ']';
    }
}
