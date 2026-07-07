package com.github.auties00.cobalt.calls2.net.transport;

import java.util.Objects;
import java.util.Optional;

/**
 * Parses a single inbound, already-unprotected RTCP compound packet into the {@link RtcpFeedback}
 * congestion-control inputs the rate-control loop consumes.
 *
 * <p>An RTCP compound packet is a back-to-back sequence of RTCP records, each prefixed by the common
 * four-byte header {@code [V(2) P(1) RC/FMT(5)] [PT(8)] [length(16, BE)]} where {@code length} counts the
 * 32-bit words after the header. This parser walks that sequence once and extracts the two records that
 * carry bandwidth-estimation signal: the Receiver Report (payload type {@code 201}, yielding the loss
 * fraction, the cumulative loss count, and the round-trip time computed from the last-SR and
 * delay-since-last-SR fields) and the receiver-estimated-maximum-bitrate feedback (REMB, a payload-type
 * {@code 206} PSFB with the {@code "REMB"} application identifier, yielding the remote downlink estimate
 * in bits per second). The IETF transport-wide-congestion-control feedback (payload type {@code 205},
 * format {@code 15}) is recognised but intentionally not decoded, matching WhatsApp, which carries its
 * transport feedback in an application-specific AFB record rather than the IETF fmt-15 format.
 *
 * <p>The parser is tolerant: a record whose declared length runs past the packet end stops the walk, an
 * unknown payload type is skipped to the next record, and a record that fails its own internal bounds or
 * validity guard contributes nothing rather than aborting the whole parse. The result is the fusion of
 * whatever recognised records were present; a field for which no record was found keeps its sentinel
 * (a non-positive value), which the caller treats as "no signal this feedback".
 *
 * @implNote This implementation reproduces the inbound RTCP feedback handling of
 *           {@code transport/rtcp.cc} and the REMB decode {@code parse_rtcp_remb2} of
 *           {@code rate_control/wa_rate_control.cc} from the wa-voip WASM module {@code ff-tScznZ8P}: the
 *           Receiver Report block layout and the last-SR/delay-since-last-SR round-trip arithmetic of the
 *           SR/RR handler (the report block at {@code pbVar8}: fraction lost at byte offset {@code 4},
 *           cumulative loss in the same word, last-SR at {@code 0x10}, delay-since-last-SR at {@code 0x14},
 *           with the compact-NTP delta converted by {@code fn4568}), and the REMB exponent-and-mantissa
 *           decode with the over-large-exponent reject guard ({@code uVar25 < 0x3c0000}, i.e. exponent
 *           {@code < 15}). The RTPFB fmt-15 (RFC 8888 transport-wide-cc) record is recognised but
 *           intentionally not decoded: {@code transport_feedback_util.cc} carries WA's transport feedback
 *           in the application-specific AFB RXSB record ({@code parse_rtcp_rxsb_afb} /
 *           {@code process_rtcp_rxsb}, consumed by {@code rate_control/wa_rate_control.cc}) and has no
 *           fmt-15 status-chunk decoder, so WA never consumes IETF transport-cc feedback and decoding it
 *           here would add a signal WA does not use.
 */
public final class RtcpFeedbackParser {
    /**
     * Holds the length, in bytes, of the RTCP common header that prefixes every record.
     *
     * @implNote This implementation uses {@code 4}: the header is {@code [V/P/RC] [PT] [length:16]}.
     */
    private static final int RTCP_HEADER_LENGTH = 4;

    /**
     * Holds the RTCP payload type of a Sender Report.
     *
     * @implNote This implementation uses {@code 200} per RFC 3550; a Sender Report carries the sender
     * info block followed by zero or more report blocks identical in shape to a Receiver Report's.
     */
    private static final int PT_SENDER_REPORT = 200;

    /**
     * Holds the RTCP payload type of a Receiver Report.
     *
     * @implNote This implementation uses {@code 201} per RFC 3550.
     */
    private static final int PT_RECEIVER_REPORT = 201;

    /**
     * Holds the RTCP payload type of a generic RTP feedback message (RTPFB).
     *
     * @implNote This implementation uses {@code 205} per RFC 4585; format {@code 15} carries
     * transport-wide congestion control feedback.
     */
    private static final int PT_RTPFB = 205;

    /**
     * Holds the RTCP payload type of a payload-specific feedback message (PSFB).
     *
     * @implNote This implementation uses {@code 206} per RFC 4585; REMB rides this payload type with the
     * application format {@code 15} and the {@code "REMB"} identifier.
     */
    private static final int PT_PSFB = 206;

    /**
     * Holds the RTPFB feedback message type that carries transport-wide congestion control feedback.
     *
     * @implNote This implementation uses {@code 15}, the {@code FMT} value the transport-cc feedback
     * format occupies in the low five bits of the first RTCP header byte.
     */
    private static final int FMT_TRANSPORT_CC = 15;

    /**
     * Holds the PSFB feedback message type that carries a picture-loss indication (PLI).
     *
     * @implNote This implementation uses {@code 1}, the {@code FMT} value RFC 4585 assigns to PLI in the low
     * five bits of the first RTCP header byte; a received PLI is the peer asking the local encoder for a key
     * frame.
     */
    private static final int FMT_PLI = 1;

    /**
     * Holds the PSFB feedback message type that carries a full-intra request (FIR).
     *
     * @implNote This implementation uses {@code 4}, the {@code FMT} value RFC 5104 assigns to FIR; like PLI
     * it requests a key frame, so it is treated identically here.
     */
    private static final int FMT_FIR = 4;

    /**
     * Holds the four-byte application identifier {@code "REMB"} as a big-endian 32-bit word.
     *
     * @implNote This implementation uses {@code 0x52454d42}: the ASCII bytes {@code 'R','E','M','B'} the
     * REMB application feedback carries in the word immediately after its sender and media SSRCs, matching
     * the {@code 0x52454d42} compared in {@code parse_rtcp_remb2}.
     */
    private static final int REMB_IDENTIFIER = 0x52454d42;

    /**
     * Holds the byte offset, within a PSFB record, of the REMB application identifier word.
     *
     * @implNote This implementation uses {@code 12}: the PSFB common header occupies bytes {@code 0..3},
     * the sender SSRC bytes {@code 4..7}, the media-source SSRC bytes {@code 8..11}, so the identifier
     * word begins at byte {@code 12}.
     */
    private static final int REMB_IDENTIFIER_OFFSET = 12;

    /**
     * Holds the byte offset, within a PSFB record, of the REMB exponent-and-mantissa word.
     *
     * @implNote This implementation uses {@code 16}: the word at byte {@code 16} packs the SSRC count in
     * its high eight bits and the six-bit exponent plus eighteen-bit mantissa in its low twenty-four bits,
     * matching the {@code param2[4]} word (byte offset {@code 16}) decoded in {@code parse_rtcp_remb2}.
     */
    private static final int REMB_BITRATE_OFFSET = 16;

    /**
     * Holds the exclusive upper bound, in the native packed units, that the REMB exponent guard compares
     * against.
     *
     * @implNote This implementation uses {@code 0x3c0000}: the native guard tests
     * {@code (exponent << 18) < 0x3c0000}, which rejects any exponent of {@code 15} or greater because
     * {@code 15 << 18 == 0x3c0000}; the comparison is reproduced exactly rather than simplified so the
     * boundary matches {@code parse_rtcp_remb2}.
     */
    private static final int REMB_EXPONENT_GUARD = 0x3c0000;

    /**
     * Holds the length, in bytes, of one RFC 3550 report block.
     *
     * @implNote This implementation uses {@code 24}: a report block is SSRC ({@code 4}), fraction lost
     * plus cumulative loss ({@code 4}), extended highest sequence number ({@code 4}), interarrival jitter
     * ({@code 4}), last SR ({@code 4}), and delay since last SR ({@code 4}).
     */
    private static final int REPORT_BLOCK_LENGTH = 24;

    /**
     * Holds the byte offset, within a Sender Report record, of its first report block.
     *
     * @implNote This implementation uses {@code 28}: the SR common header occupies bytes {@code 0..3},
     * the sender SSRC bytes {@code 4..7}, and the twenty-byte sender info block bytes {@code 8..27}, so
     * the report blocks begin at byte {@code 28}.
     */
    private static final int SENDER_REPORT_BLOCK_OFFSET = 28;

    /**
     * Holds the byte offset, within a Receiver Report record, of its first report block.
     *
     * @implNote This implementation uses {@code 8}: the RR common header occupies bytes {@code 0..3} and
     * the reporter SSRC bytes {@code 4..7}, so the report blocks begin at byte {@code 8}.
     */
    private static final int RECEIVER_REPORT_BLOCK_OFFSET = 8;

    /**
     * Holds the divisor turning the eight-bit fraction-lost field into a unit fraction.
     *
     * @implNote This implementation uses {@code 256.0}: the fraction-lost byte is a fixed-point value
     * scaled by {@code 256}, matching the native {@code (double) fraction * 0.00390625} where
     * {@code 0.00390625 == 1.0 / 256.0}.
     */
    private static final double FRACTION_LOST_SCALE = 256.0;

    /**
     * Holds the compact-NTP value at or below which the round-trip conversion uses the high-precision
     * branch.
     *
     * @implNote This implementation uses {@code 0x10c5}: {@code fn4568} multiplies a small compact-NTP
     * delta by {@code 1000000} before the right shift, but switches to a divide-first form above this
     * threshold to avoid 32-bit overflow; the boundary is reproduced exactly.
     */
    private static final long COMPACT_NTP_PRECISION_THRESHOLD = 0x10c5L;

    /**
     * Holds the exclusive upper bound, in microseconds, beyond which a computed round-trip time is
     * rejected as implausible.
     *
     * @implNote This implementation uses {@code 0x1c9c381} ({@code 30000001}): the native guard tests
     * {@code rtt < 0x1c9c381} on the {@code fn4568} result, discarding any round-trip estimate of roughly
     * thirty seconds or more as a clock or parsing artifact.
     */
    private static final long RTT_SANITY_MICROS = 0x1c9c381L;

    /**
     * Holds the number of nanoseconds in one microsecond, used to widen the native microsecond round-trip
     * value to the nanosecond unit this parser reports.
     *
     * @implNote This implementation uses {@code 1000}: {@code fn4568} yields microseconds (its
     * {@code value * 1000000 >> 16} arithmetic divides a {@code 1/65536}-second delta into microseconds),
     * and {@link RtcpFeedback#rttNs()} is specified in nanoseconds, so the native value is multiplied by
     * {@code 1000}.
     */
    private static final long MICROS_TO_NANOS = 1000L;

    /**
     * Prevents instantiation of this stateless parser.
     */
    private RtcpFeedbackParser() {
        throw new AssertionError("RtcpFeedbackParser cannot be instantiated");
    }

    /**
     * Parses one unprotected RTCP compound packet into its congestion-control feedback, or reports that it
     * carried no recognised feedback.
     *
     * <p>The packet is walked record by record. Each recognised record contributes its fields to a single
     * fused {@link RtcpFeedback}: a Sender or Receiver Report contributes the loss fraction, cumulative
     * loss, and round-trip time from its first valid report block, and a REMB contributes the remote
     * bandwidth estimate. A packet with no recognised record, or whose only records failed their guards,
     * yields {@link Optional#empty()}.
     *
     * @param packet the cleartext RTCP compound packet, already SRTCP-unprotected
     * @param length the number of valid leading bytes in {@code packet}
     * @return the fused feedback when at least one recognised record contributed, otherwise empty
     * @throws NullPointerException     if {@code packet} is {@code null}
     * @throws IllegalArgumentException if {@code length} is negative or exceeds {@code packet.length}
     */
    // TODO (item 7): return a nullable RtcpFeedback (and inline the private ReportFields holder) to drop
    //  the per-packet Optional allocation on this hot inbound-RTCP path. Blocked because the sole caller,
    //  LiveRelayTransport.parse (outside this owned set), consumes the Optional; changing the return type
    //  requires updating that call site, which is not in scope here.
    public static Optional<RtcpFeedback> parse(byte[] packet, int length) {
        Objects.requireNonNull(packet, "packet cannot be null");
        if (length < 0 || length > packet.length) {
            throw new IllegalArgumentException("length " + length + " out of range for buffer " + packet.length);
        }
        var arrivalMs = System.nanoTime() / 1_000_000L;
        double fractionLost = -1.0;
        long cumulativeLost = -1L;
        long rttNs = -1L;
        long remoteBweBps = -1L;
        var keyFrameRequested = false;
        var found = false;
        var offset = 0;
        while (offset + RTCP_HEADER_LENGTH <= length) {
            var firstByte = packet[offset] & 0xFF;
            var payloadType = packet[offset + 1] & 0xFF;
            var declaredWords = readUnsignedShort(packet, offset + 2);
            var recordLength = (declaredWords + 1) * 4;
            if (recordLength <= 0 || offset + recordLength > length) {
                break;
            }
            switch (payloadType) {
                case PT_SENDER_REPORT, PT_RECEIVER_REPORT -> {
                    var report = parseReport(packet, offset, recordLength, firstByte & 0x1F, payloadType);
                    if (report != null) {
                        fractionLost = report.fractionLost();
                        cumulativeLost = report.cumulativeLost();
                        rttNs = report.rttNs();
                        found = true;
                    }
                }
                case PT_PSFB -> {
                    var feedbackFormat = firstByte & 0x1F;
                    if (feedbackFormat == FMT_PLI || feedbackFormat == FMT_FIR) {
                        // A peer picture-loss indication or full-intra request asks the local encoder for a
                        // key frame; the media session arms the encoder when this is set.
                        keyFrameRequested = true;
                        found = true;
                    } else {
                        var bps = parseRemb(packet, offset, recordLength);
                        if (bps >= 0) {
                            remoteBweBps = bps;
                            found = true;
                        }
                    }
                }
                case PT_RTPFB -> {
                    if ((firstByte & 0x1F) == FMT_TRANSPORT_CC) {
                        // RTPFB fmt 15 (RFC 8888 / transport-wide-cc) is deliberately not decoded: WA does
                        // not carry transport feedback in the IETF RTPFB fmt-15 status-chunk format. Its
                        // transport feedback rides the application-specific AFB RXSB record (parse_rtcp_rxsb_afb
                        // / process_rtcp_rxsb), which transport_feedback_util.cc builds and wa_rate_control.cc
                        // consumes; transport_feedback_util.cc has no fmt-15 status-chunk decoder. Decoding a
                        // fmt-15 record here would add a signal WA does not use, so it is ignored to match WA.
                    }
                }
                default -> {
                    // An unrecognised payload type is skipped to the next record.
                }
            }
            offset += recordLength;
        }
        if (!found) {
            return Optional.empty();
        }
        return Optional.of(new RtcpFeedback(fractionLost, cumulativeLost, rttNs, remoteBweBps, arrivalMs,
                keyFrameRequested));
    }

    /**
     * Parses the first valid report block of a Sender or Receiver Report into its loss and round-trip
     * fields.
     *
     * <p>The first report block of the record is read when the reception-report count is at least one and
     * the block fits within the record. The fraction-lost byte is scaled to a unit fraction, the
     * cumulative-loss field is the signed twenty-four-bit count, and the round-trip time is computed from
     * the last-SR and delay-since-last-SR fields against the current compact-NTP clock; when the round-trip
     * fields are zero or fail their sanity guard the round-trip component is left absent ({@code -1}).
     *
     * @param packet            the RTCP packet bytes
     * @param recordOffset      the byte offset of the record within {@code packet}
     * @param recordLength      the byte length of the record
     * @param receptionCount    the reception-report count from the record's first header byte
     * @param payloadType       the record payload type, distinguishing a Sender Report from a Receiver
     *                          Report so the report blocks are read from the correct offset
     * @return the parsed report fields, or {@code null} when the record carries no readable report block
     */
    private static ReportFields parseReport(byte[] packet,
                                            int recordOffset,
                                            int recordLength,
                                            int receptionCount,
                                            int payloadType) {
        if (receptionCount < 1) {
            return null;
        }
        var blockOffset = recordOffset
                + (payloadType == PT_SENDER_REPORT ? SENDER_REPORT_BLOCK_OFFSET : RECEIVER_REPORT_BLOCK_OFFSET);
        if (blockOffset + REPORT_BLOCK_LENGTH > recordOffset + recordLength) {
            return null;
        }
        var fractionByte = packet[blockOffset + 4] & 0xFF;
        var fractionLost = fractionByte / FRACTION_LOST_SCALE;
        var cumulativeLost = readSigned24(packet, blockOffset + 5);
        var lastSr = readUnsignedInt(packet, blockOffset + 16);
        var delayLastSr = readUnsignedInt(packet, blockOffset + 20);
        var rttNs = computeRttNs(lastSr, delayLastSr);
        return new ReportFields(fractionLost, cumulativeLost, rttNs);
    }

    /**
     * Computes the round-trip time, in nanoseconds, from a report block's last-SR and delay-since-last-SR
     * fields, or reports that no estimate is available.
     *
     * <p>Both fields are compact-NTP timestamps (the middle thirty-two bits of a sixty-four-bit NTP value,
     * i.e. the low sixteen bits of the seconds and the high sixteen bits of the fraction). The round-trip
     * delta is {@code now - lastSr - delayLastSr} in compact-NTP units; it is converted to a duration and
     * accepted only when both fields are non-zero, the last-SR timestamp does not postdate the arrival,
     * and the result is below the sanity ceiling.
     *
     * @param lastSr      the compact-NTP last-SR timestamp from the report block
     * @param delayLastSr the compact-NTP delay-since-last-SR from the report block
     * @return the round-trip time in nanoseconds, or {@code -1} when no plausible estimate is available
     */
    private static long computeRttNs(long lastSr, long delayLastSr) {
        if (lastSr == 0 || delayLastSr == 0) {
            return -1L;
        }
        var now = nowCompactNtp();
        var sinceArrival = now - delayLastSr;
        if (Long.compareUnsigned(lastSr, sinceArrival & 0xFFFFFFFFL) > 0) {
            return -1L;
        }
        var delta = (now - (lastSr + delayLastSr)) & 0xFFFFFFFFL;
        var micros = compactNtpToMicros(delta);
        if (micros >= RTT_SANITY_MICROS) {
            return -1L;
        }
        return micros * MICROS_TO_NANOS;
    }

    /**
     * Converts a compact-NTP duration to microseconds, reproducing the native overflow-avoiding split.
     *
     * <p>A compact-NTP unit is {@code 1/65536} of a second. For a small delta the conversion multiplies by
     * one million before the sixteen-bit right shift; above the precision threshold it divides by the shift
     * first and scales by one thousand afterward, matching the two branches of {@code fn4568} that keep the
     * intermediate product inside thirty-two bits.
     *
     * @param compactNtp the duration in compact-NTP units
     * @return the duration in microseconds
     */
    private static long compactNtpToMicros(long compactNtp) {
        if (compactNtp > COMPACT_NTP_PRECISION_THRESHOLD) {
            return ((compactNtp * 1000L) >> 16) * 1000L;
        }
        return (compactNtp * 1_000_000L) >> 16;
    }

    /**
     * Returns the current time as a compact-NTP timestamp.
     *
     * <p>The compact-NTP value is the middle thirty-two bits of the sixty-four-bit NTP timestamp: the low
     * sixteen bits of the seconds since the NTP epoch concatenated with the high sixteen bits of the
     * fractional second. It is derived from the wall clock so it shares the timebase of a remote sender's
     * last-SR field, which is what the round-trip subtraction requires.
     *
     * @return the current compact-NTP timestamp masked to thirty-two bits
     */
    private static long nowCompactNtp() {
        var millis = System.currentTimeMillis();
        var seconds = millis / 1000L;
        var fractionMillis = millis % 1000L;
        var ntpFraction = (fractionMillis << 16) / 1000L;
        return ((seconds << 16) | (ntpFraction & 0xFFFFL)) & 0xFFFFFFFFL;
    }

    /**
     * Decodes a REMB record's remote bandwidth estimate, applying the over-large-exponent reject guard.
     *
     * <p>The record is a REMB only when its application identifier word equals {@code "REMB"}. The
     * exponent-and-mantissa word is then unpacked: the six-bit exponent is rejected when it would shift the
     * eighteen-bit mantissa past the native ceiling, and otherwise the estimate is {@code mantissa
     * << exponent} bits per second. The mantissa is reassembled from its big-endian byte positions exactly
     * as {@code parse_rtcp_remb2} does.
     *
     * @param packet       the RTCP packet bytes
     * @param recordOffset the byte offset of the record within {@code packet}
     * @param recordLength the byte length of the record
     * @return the decoded estimate in bits per second, or {@code -1} when the record is not a valid REMB or
     *         its exponent fails the guard
     */
    private static long parseRemb(byte[] packet, int recordOffset, int recordLength) {
        if (recordLength < REMB_BITRATE_OFFSET + 4) {
            return -1L;
        }
        var identifier = readUnsignedInt(packet, recordOffset + REMB_IDENTIFIER_OFFSET);
        if (identifier != (REMB_IDENTIFIER & 0xFFFFFFFFL)) {
            return -1L;
        }
        var word = (int) readUnsignedInt(packet, recordOffset + REMB_BITRATE_OFFSET);
        var packedExponent = (word & 0x3f00) << 10;
        if (packedExponent >= REMB_EXPONENT_GUARD) {
            return -1L;
        }
        var mantissa = ((word & 0xc000) << 2) | (word >>> 8 & 0xff00) | (word >>> 24);
        var exponent = (packedExponent >>> 18) & 0x1f;
        return ((long) mantissa << exponent) & 0xFFFFFFFFL;
    }

    /**
     * Reads a big-endian unsigned sixteen-bit value from a buffer.
     *
     * @param packet the buffer
     * @param offset the byte offset of the value
     * @return the value in the range {@code 0..65535}
     */
    private static int readUnsignedShort(byte[] packet, int offset) {
        return ((packet[offset] & 0xFF) << 8) | (packet[offset + 1] & 0xFF);
    }

    /**
     * Reads a big-endian unsigned thirty-two-bit value from a buffer into a {@code long}.
     *
     * @param packet the buffer
     * @param offset the byte offset of the value
     * @return the value in the range {@code 0..4294967295}
     */
    private static long readUnsignedInt(byte[] packet, int offset) {
        return ((long) (packet[offset] & 0xFF) << 24)
                | ((long) (packet[offset + 1] & 0xFF) << 16)
                | ((long) (packet[offset + 2] & 0xFF) << 8)
                | (packet[offset + 3] & 0xFF);
    }

    /**
     * Reads a big-endian signed twenty-four-bit value from a buffer.
     *
     * <p>The cumulative-loss field is a signed twenty-four-bit count; the top bit is sign-extended so a
     * negative count (more packets received than expected, after duplicates) is preserved.
     *
     * @param packet the buffer
     * @param offset the byte offset of the value
     * @return the sign-extended value
     */
    private static long readSigned24(byte[] packet, int offset) {
        var raw = ((packet[offset] & 0xFF) << 16)
                | ((packet[offset + 1] & 0xFF) << 8)
                | (packet[offset + 2] & 0xFF);
        return (raw << 8) >> 8;
    }

    /**
     * Carries the loss and round-trip fields parsed from one report block.
     *
     * @param fractionLost   the loss fraction in {@code [0, 1]} from the fraction-lost byte
     * @param cumulativeLost the signed cumulative number of packets lost
     * @param rttNs          the round-trip time in nanoseconds, or {@code -1} when unavailable
     */
    private record ReportFields(double fractionLost, long cumulativeLost, long rttNs) {
    }
}
