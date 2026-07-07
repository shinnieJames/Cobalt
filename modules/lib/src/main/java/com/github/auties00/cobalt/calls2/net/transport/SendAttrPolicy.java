package com.github.auties00.cobalt.calls2.net.transport;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Decides, per outbound media packet, which WARP control attributes to piggyback, applying the
 * freshness and interval throttles that keep the control channel from flooding.
 *
 * <p>Every outbound RTP packet may carry a piggybacked WARP message; this policy chooses its
 * attributes from the current transport state. It always attaches a fresh
 * {@link WarpAttribute.SequenceNumber} taken from a monotonic counter under the instance lock. It
 * attaches a {@link WarpAttribute.DownlinkBandwidth} only when a recent downlink sample exists (younger
 * than {@value #DL_BW_FRESHNESS_MS} milliseconds and non-zero). It attaches a
 * {@link WarpAttribute.SenderBandwidthAllocation} when the SRTP layer offers a value for the current
 * RTP index. It attaches a {@link WarpAttribute.VideoEncoding} when a video subscription is active and
 * its direction is known. It attaches a {@link WarpAttribute.ParticipantReport} only when one is due by
 * the minimum report interval. The result is a {@link WarpMessage.Piggybacked} ready to append, or an
 * empty result when nothing beyond a bare control packet would be produced and the caller chose to skip
 * one.
 *
 * <p>The policy holds the sequence counter and the last-report timestamps and is driven from the single
 * call transport thread; the sequence draw is guarded so a future caller from another thread still gets
 * a unique number.
 *
 * @implNote This implementation reproduces {@code set_send_attr_options} (fn5160) from the wa-voip WASM
 *           module {@code ff-tScznZ8P} ({@code transport/wa_transport_warp.cc}): it sets the
 *           sequence-number bit on a fresh message, the downlink-bandwidth bit when a sample younger
 *           than {@code 0xbb9} (3001) milliseconds and non-zero exists, the sender-BWA bit when the
 *           SRTP layer produces a value for the RTP index, the participant-report companion bits when
 *           the report is due, and the video-encoding bit when the subscription is active. The
 *           {@value #DL_BW_FRESHNESS_MS}-millisecond freshness window is the recovered {@code 0xbb9}
 *           constant; the participant-report minimum interval is gated by
 *           {@code wa_warp_should_send_pr_when_dl_bwe_drops} in the native code and is supplied here by
 *           the caller through {@link Inputs} rather than hard-coded, because its trigger lives in the
 *           rate-control layer.
 */
public final class SendAttrPolicy {
    /**
     * The maximum age, in milliseconds, of a downlink-bandwidth sample for it to be attached.
     *
     * <p>A sample older than this is considered stale and the downlink-bandwidth attribute is omitted.
     */
    public static final int DL_BW_FRESHNESS_MS = 0xbb9;

    /**
     * Holds the next WARP sequence number to assign, advanced atomically so a draw is unique across
     * threads.
     */
    private final AtomicInteger nextSequence = new AtomicInteger();

    /**
     * Holds the timestamp, in the caller's millisecond timebase, at which the last participant report
     * was attached, or {@code Long.MIN_VALUE} when none has been attached.
     */
    private long lastParticipantReportMs = Long.MIN_VALUE;

    /**
     * Constructs a policy with its sequence counter at zero and no report yet attached.
     */
    public SendAttrPolicy() {
    }

    /**
     * Chooses the piggybacked WARP message for one outbound packet from the current transport state.
     *
     * <p>A fresh sequence number is always taken. The downlink-bandwidth, sender-BWA, video-encoding,
     * and participant-report attributes are attached subject to their respective freshness and interval
     * gates as described in the class documentation. When the only attribute that would be attached is
     * the sequence number and {@code inputs.controlPacketDue()} is {@code false}, an empty result is
     * returned so the caller need not emit a bare control packet.
     *
     * @param inputs the current transport-state inputs for this packet
     * @return the WARP message to piggyback, or an empty result when no control packet is warranted
     * @throws NullPointerException if {@code inputs} is {@code null}
     */
    public Optional<WarpMessage.Piggybacked> choose(Inputs inputs) {
        java.util.Objects.requireNonNull(inputs, "inputs cannot be null");
        var attributes = new ArrayList<WarpAttribute>();
        attributes.add(new WarpAttribute.SequenceNumber(nextSequenceNumber()));

        var sampleAge = inputs.nowMs() - inputs.downlinkBwSampleAtMs();
        if (inputs.downlinkBwKbps() > 0 && sampleAge >= 0 && sampleAge < DL_BW_FRESHNESS_MS) {
            attributes.add(new WarpAttribute.DownlinkBandwidth(inputs.downlinkBwKbps()));
        }

        inputs.senderBandwidthAllocation()
                .ifPresent(value -> attributes.add(new WarpAttribute.SenderBandwidthAllocation(value)));

        if (inputs.videoSubscriptionActive()) {
            attributes.add(new WarpAttribute.VideoEncoding(inputs.videoEncodingFlags()));
        }

        var report = inputs.participantReport();
        if (report.isPresent() && isParticipantReportDue(inputs.nowMs(), inputs.participantReportMinIntervalMs())) {
            attributes.add(new WarpAttribute.ParticipantReport(report.get()));
            lastParticipantReportMs = inputs.nowMs();
        }

        if (attributes.size() == 1 && !inputs.controlPacketDue()) {
            return Optional.empty();
        }
        return Optional.of(new WarpMessage.Piggybacked(attributes));
    }

    /**
     * Returns the next monotonic WARP sequence number, advancing the counter.
     *
     * <p>The draw is guarded so two concurrent callers never receive the same number; the counter wraps
     * naturally at the sixteen-bit width the sequence attribute carries.
     *
     * @return the next sequence number, masked to sixteen bits
     */
    public int nextSequenceNumber() {
        return nextSequence.getAndIncrement() & 0xffff;
    }

    /**
     * Returns whether a participant report is due given the current time and minimum interval.
     *
     * @param nowMs       the current time in the caller's millisecond timebase
     * @param minIntervalMs the minimum interval between reports in milliseconds
     * @return {@code true} when no report has been attached or the interval has elapsed
     */
    private boolean isParticipantReportDue(long nowMs, long minIntervalMs) {
        if (lastParticipantReportMs == Long.MIN_VALUE) {
            return true;
        }
        return nowMs - lastParticipantReportMs >= minIntervalMs;
    }

    /**
     * Carries the transport-state inputs the policy reads to choose one packet's WARP attributes.
     *
     * <p>This record bundles the per-packet decision inputs so {@link #choose(Inputs)} takes one
     * argument: the current time, the freshest downlink-bandwidth sample and when it was taken, any
     * sender-bandwidth-allocation value the SRTP layer offers for this RTP index, the video
     * subscription state and encoding flags, an optional participant report and its minimum interval,
     * and whether the caller wants a control packet emitted even when only the sequence number would be
     * attached.
     *
     * @param nowMs                        the current time in the caller's millisecond timebase
     * @param downlinkBwKbps               the freshest downlink bandwidth estimate in kilobits per
     *                                     second, or {@code 0} when none
     * @param downlinkBwSampleAtMs         the time the downlink sample was taken, in the same timebase
     * @param senderBandwidthAllocation    the sender-BWA value for this RTP index, if the SRTP layer
     *                                     offers one
     * @param videoSubscriptionActive      whether a video subscription is active
     * @param videoEncodingFlags           the video-encoding direction flags to send when active
     * @param participantReport            the participant report to attach when due, if one is available
     * @param participantReportMinIntervalMs the minimum interval between participant reports in
     *                                       milliseconds
     * @param controlPacketDue             whether to emit a control packet even when only the sequence
     *                                     number would be attached
     */
    public record Inputs(long nowMs,
                         int downlinkBwKbps,
                         long downlinkBwSampleAtMs,
                         Optional<Integer> senderBandwidthAllocation,
                         boolean videoSubscriptionActive,
                         int videoEncodingFlags,
                         Optional<WarpParticipantReport> participantReport,
                         long participantReportMinIntervalMs,
                         boolean controlPacketDue) {
        /**
         * Canonicalizes the record components, defaulting the optional fields to empty when {@code null}.
         */
        public Inputs {
            senderBandwidthAllocation = senderBandwidthAllocation == null ? Optional.empty() : senderBandwidthAllocation;
            participantReport = participantReport == null ? Optional.empty() : participantReport;
        }
    }
}
