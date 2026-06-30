package com.github.auties00.cobalt.calls2.net.transport;

import java.util.List;
import java.util.Objects;

/**
 * Serializes the outbound RTCP records WhatsApp emits on a hop-by-hop relay leg: the Sender Report plus
 * Source Description compound, the proprietary application-layer feedback (AFB) record, and the generic
 * NACK, each as the exact SRTCP-plaintext bytes the hop-by-hop SRTCP layer then protects.
 *
 * <p>Every multi-byte field is big-endian (network order). The sizes this builder produces are the
 * cleartext record sizes before SRTCP protection; the hop-by-hop SRTCP layer appends its own fourteen-byte
 * trailer (a four-byte E-flag-plus-index word and a ten-byte HMAC-SHA1-80 authentication tag) to the
 * whole compound or standalone record. The builder writes no padding bit and never pads a record, so the
 * length word of each record counts exactly the thirty-two-bit words after the common header.
 *
 * <p>The compound the steady-state relay leg sends roughly once per second per local media stream is a
 * Sender Report ({@link #buildSenderReportWithSdes}) immediately followed in the same datagram by a
 * one-chunk Source Description carrying the session canonical name ({@link #buildSdesChunk}). The
 * application-layer feedback record ({@link #buildAfb}) rides on a slower cadence from the video stream and
 * carries the per-stream hop-by-hop SRTP highest-index watermark in its {@code "HBHS"} feedback control
 * information. The generic NACK ({@link #buildNack}) is sent standalone on inbound loss to request a
 * retransmission of a run of lost sequence numbers.
 *
 * @implNote This implementation reproduces WhatsApp's RTCP serializers, which are the pjmedia (PJSIP)
 *           RTCP stack embedded in the wa-voip native module: the Sender Report and its report blocks of
 *           {@code pjmedia_rtcp_build_rtcp}, the Source Description chunk of {@code pjmedia_rtcp_build_sdes},
 *           the generic NACK feedback control information of {@code pjmedia_rtcp_fb_build_nack}, and the
 *           proprietary hop-by-hop SRTP authenticated-feedback batch of the {@code _srtp_afb_batch}
 *           builder. The records carry no provenance annotation because the pjmedia RTCP stack is an
 *           upstream third-party library WhatsApp embeds rather than a WhatsApp Web or Mobile source export.
 *           The Sender Report's reception-report count nibble is written to match the number of report
 *           blocks supplied, which a live capture's SRTCP transform layer perturbs on the wire; the
 *           seventy-six-byte length of a two-block Sender Report is the capture-stable fact this builder
 *           reproduces when fed the two remote-stream report blocks of a one-to-one video call.
 */
public final class RtcpReportBuilder {
    /**
     * Holds the first byte's version-and-padding prefix shared by every record, the two-bit version
     * {@code 2} in the high bits with the padding bit clear.
     *
     * @implNote This implementation uses {@code 0x80}: version two ({@code 10}) in bits seven and six and
     * a zero padding bit, to which the per-record reception-report count or feedback message type is
     * combined in the low five bits.
     */
    private static final int VERSION_PADDING_PREFIX = 0x80;

    /**
     * Holds the RTCP payload type of a Sender Report.
     *
     * @implNote This implementation uses {@code 200} per RFC 3550.
     */
    private static final int PT_SENDER_REPORT = 200;

    /**
     * Holds the RTCP payload type of a Source Description.
     *
     * @implNote This implementation uses {@code 202} per RFC 3550.
     */
    private static final int PT_SOURCE_DESCRIPTION = 202;

    /**
     * Holds the RTCP payload type of a transport-layer feedback message (RTPFB).
     *
     * @implNote This implementation uses {@code 205} per RFC 4585; the generic NACK rides this payload type
     * with feedback message type {@code 1}.
     */
    private static final int PT_RTPFB = 205;

    /**
     * Holds the RTCP payload type of a payload-specific feedback message (PSFB).
     *
     * @implNote This implementation uses {@code 206} per RFC 4585; the proprietary application-layer
     * feedback rides this payload type with the application feedback message type {@code 15}.
     */
    private static final int PT_PSFB = 206;

    /**
     * Holds the feedback message type of a generic NACK.
     *
     * @implNote This implementation uses {@code 1}, the {@code FMT} value RFC 4585 assigns to the generic
     * NACK in the low five bits of the first header byte.
     */
    private static final int FMT_NACK = 1;

    /**
     * Holds the feedback message type of an application-layer feedback message.
     *
     * @implNote This implementation uses {@code 15}, the {@code FMT} value RFC 4585 assigns to
     * application-layer feedback (AFB) in the low five bits of the first header byte.
     */
    private static final int FMT_AFB = 15;

    /**
     * Holds the Source Description item type of the canonical name (CNAME).
     *
     * @implNote This implementation uses {@code 1}, the SDES item type RFC 3550 assigns to CNAME.
     */
    private static final int SDES_ITEM_CNAME = 1;

    /**
     * Holds the four-byte application identifier {@code "HBHS"} as a big-endian thirty-two-bit word.
     *
     * @implNote This implementation uses {@code 0x48424853}, the ASCII bytes {@code 'H','B','H','S'} that
     * tag the hop-by-hop SRTP authenticated-feedback control information the {@code _srtp_afb_batch} builder
     * writes after the AFB sender and media SSRCs.
     */
    private static final int HBHS_TAG = 0x48424853;

    /**
     * Holds the length, in bytes, of the AFB feedback control information that follows the media SSRC.
     *
     * @implNote This implementation uses {@code 16}: the four-byte {@code "HBHS"} tag, a two-byte
     * serialized-payload-length, and a ten-byte serialized payload.
     */
    private static final int AFB_FCI_LENGTH = 16;

    /**
     * Holds the length, in bytes, of the AFB serialized payload region carrying the per-stream highest
     * hop-by-hop SRTP indices.
     *
     * @implNote This implementation uses {@code 10}: a six-byte forty-eight-bit RTP packet index followed
     * by a four-byte thirty-two-bit SRTCP index, the two SRTP index widths RFC 3711 assigns to the RTP
     * packet index and the SRTCP index.
     */
    private static final int AFB_PAYLOAD_LENGTH = 10;

    /**
     * Holds the fixed sender synchronization source the generic NACK carries.
     *
     * @implNote This implementation uses {@code 0x00000001}: WhatsApp's NACK feedback writes the constant
     * sender SSRC {@code 1} rather than the local media SSRC, the value a live caller capture carried in the
     * NACK packet sender field.
     */
    private static final int NACK_SENDER_SSRC = 0x00000001;

    /**
     * Holds the length, in bytes, of one RFC 3550 report block.
     *
     * @implNote This implementation uses {@code 24}: SSRC ({@code 4}), fraction lost plus cumulative loss
     * ({@code 4}), extended highest sequence number ({@code 4}), interarrival jitter ({@code 4}), last SR
     * ({@code 4}), and delay since last SR ({@code 4}).
     */
    private static final int REPORT_BLOCK_LENGTH = 24;

    /**
     * Holds the length, in bytes, of a Sender Report's common header plus its sender info block.
     *
     * @implNote This implementation uses {@code 28}: the four-byte common header, the four-byte sender
     * SSRC, and the twenty-byte sender info block (NTP timestamp, RTP timestamp, sender packet count, and
     * sender octet count).
     */
    private static final int SENDER_REPORT_PREFIX_LENGTH = 28;

    /**
     * Prevents instantiation of this stateless builder.
     */
    private RtcpReportBuilder() {
        throw new AssertionError("RtcpReportBuilder cannot be instantiated");
    }

    /**
     * Builds the Sender Report plus Source Description compound the relay leg emits for one local media
     * stream.
     *
     * <p>The compound is one Sender Report carrying the sender info block and one report block per supplied
     * {@link ReportBlock}, immediately followed by a one-chunk Source Description naming the same SSRC with
     * the session canonical name. The Sender Report's reception-report count is set to the number of report
     * blocks; a two-block report is seventy-six bytes, matching the capture's per-stream Sender Report on a
     * one-to-one video call. The whole compound is returned as one byte array so the caller protects and
     * ships it as a single SRTCP datagram.
     *
     * @param senderSsrc   the local media synchronization source of the stream this report describes
     * @param ntpTimestamp the sixty-four-bit NTP wall-clock timestamp of this report
     * @param rtpTimestamp the RTP timestamp corresponding to {@code ntpTimestamp}, written as a thirty-two-bit
     *                     value
     * @param packetCount  the cumulative count of RTP data packets the sender has transmitted on this stream
     * @param octetCount   the cumulative count of RTP payload octets the sender has transmitted on this stream
     * @param reportBlocks the reception report blocks, one per remote stream this client receives
     * @param cname        the session canonical name bytes carried in the Source Description CNAME item
     * @return the Sender Report plus Source Description compound bytes
     * @throws NullPointerException     if {@code reportBlocks}, any element of it, or {@code cname} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code reportBlocks} holds more than thirty-one blocks, or
     *                                  {@code cname} is empty or longer than two hundred fifty-five bytes
     */
    public static byte[] buildSenderReportWithSdes(int senderSsrc,
                                                   long ntpTimestamp,
                                                   long rtpTimestamp,
                                                   long packetCount,
                                                   long octetCount,
                                                   List<ReportBlock> reportBlocks,
                                                   byte[] cname) {
        Objects.requireNonNull(reportBlocks, "reportBlocks cannot be null");
        Objects.requireNonNull(cname, "cname cannot be null");
        if (reportBlocks.size() > 0x1F) {
            throw new IllegalArgumentException("reportBlocks cannot exceed 31: " + reportBlocks.size());
        }
        var sdes = buildSdesChunk(senderSsrc, cname);
        var senderReportLength = SENDER_REPORT_PREFIX_LENGTH + REPORT_BLOCK_LENGTH * reportBlocks.size();
        var out = new byte[senderReportLength + sdes.length];
        out[0] = (byte) (VERSION_PADDING_PREFIX | (reportBlocks.size() & 0x1F));
        out[1] = (byte) PT_SENDER_REPORT;
        putU16(out, 2, senderReportLength / 4 - 1);
        putU32(out, 4, senderSsrc);
        putU64(out, 8, ntpTimestamp);
        putU32(out, 16, (int) rtpTimestamp);
        putU32(out, 20, (int) packetCount);
        putU32(out, 24, (int) octetCount);
        var offset = SENDER_REPORT_PREFIX_LENGTH;
        for (var block : reportBlocks) {
            Objects.requireNonNull(block, "reportBlocks cannot contain null");
            putU32(out, offset, block.ssrc());
            out[offset + 4] = (byte) block.fractionLost();
            out[offset + 5] = (byte) (block.cumulativeLost() >> 16);
            out[offset + 6] = (byte) (block.cumulativeLost() >> 8);
            out[offset + 7] = (byte) block.cumulativeLost();
            putU32(out, offset + 8, (int) block.extendedHighestSeq());
            putU32(out, offset + 12, (int) block.interarrivalJitter());
            putU32(out, offset + 16, (int) block.lastSr());
            putU32(out, offset + 20, (int) block.delaySinceLastSr());
            offset += REPORT_BLOCK_LENGTH;
        }
        System.arraycopy(sdes, 0, out, senderReportLength, sdes.length);
        return out;
    }

    /**
     * Builds a one-chunk Source Description record carrying the session canonical name for a stream.
     *
     * <p>The record is one SDES chunk: the stream SSRC followed by a single CNAME item (its one-byte type,
     * one-byte length, and the canonical name text), a zero END octet terminating the item list, and zero
     * padding to the next thirty-two-bit boundary. A canonical name eighteen bytes long, which is the
     * base64url encoding of thirteen random bytes the relay leg generates per session, produces a
     * thirty-two-byte record.
     *
     * @param ssrc  the stream synchronization source the description names
     * @param cname the canonical name bytes
     * @return the Source Description record bytes, padded to a thirty-two-bit boundary
     * @throws NullPointerException     if {@code cname} is {@code null}
     * @throws IllegalArgumentException if {@code cname} is empty or longer than two hundred fifty-five bytes
     */
    public static byte[] buildSdesChunk(int ssrc, byte[] cname) {
        Objects.requireNonNull(cname, "cname cannot be null");
        if (cname.length == 0 || cname.length > 0xFF) {
            throw new IllegalArgumentException("cname length out of range: " + cname.length);
        }
        // chunk = SSRC(4) + CNAME item(type 1 + len 1 + text) + END(1 zero octet), padded to 32 bits.
        var chunkLength = 4 + 2 + cname.length + 1;
        var paddedChunkLength = (chunkLength + 3) & ~3;
        var recordLength = 4 + paddedChunkLength;
        var out = new byte[recordLength];
        out[0] = (byte) (VERSION_PADDING_PREFIX | 0x01);
        out[1] = (byte) PT_SOURCE_DESCRIPTION;
        putU16(out, 2, recordLength / 4 - 1);
        putU32(out, 4, ssrc);
        out[8] = (byte) SDES_ITEM_CNAME;
        out[9] = (byte) cname.length;
        System.arraycopy(cname, 0, out, 10, cname.length);
        // The END octet and the trailing pad bytes are left zero by the fresh array allocation.
        return out;
    }

    /**
     * Builds the proprietary application-layer feedback record carrying a stream's highest hop-by-hop SRTP
     * indices.
     *
     * <p>The record is a payload-specific feedback message of application feedback type whose feedback
     * control information is the four-byte {@code "HBHS"} tag, a two-byte serialized-payload length of ten,
     * and the ten-byte payload: the forty-eight-bit highest RTP packet index in six big-endian bytes
     * followed by the thirty-two-bit highest SRTCP index in four big-endian bytes. The record is
     * twenty-eight bytes.
     *
     * @param senderSsrc the local stream synchronization source sending the feedback
     * @param mediaSsrc  the synchronization source of the stream the indices describe
     * @param rtpIndex   the highest rollover-extended RTP packet index, of which the low forty-eight bits
     *                   are written
     * @param rtcpIndex  the highest SRTCP index, written as a thirty-two-bit value
     * @return the application-layer feedback record bytes
     */
    public static byte[] buildAfb(int senderSsrc, int mediaSsrc, long rtpIndex, int rtcpIndex) {
        var out = new byte[12 + AFB_FCI_LENGTH];
        out[0] = (byte) (VERSION_PADDING_PREFIX | (FMT_AFB & 0x1F));
        out[1] = (byte) PT_PSFB;
        putU16(out, 2, out.length / 4 - 1);
        putU32(out, 4, senderSsrc);
        putU32(out, 8, mediaSsrc);
        putU32(out, 12, HBHS_TAG);
        putU16(out, 16, AFB_PAYLOAD_LENGTH);
        putU48(out, 18, rtpIndex);
        putU32(out, 24, rtcpIndex);
        return out;
    }

    /**
     * Builds a generic NACK record requesting retransmission of a run of lost sequence numbers.
     *
     * <p>The record is a transport-layer feedback message of generic NACK type carrying the fixed sender
     * SSRC, the media SSRC whose packets are being requested, the packet identifier (the first lost sequence
     * number), and the bitmask of following lost packets (bit {@code i} set requests the packet
     * {@code pid + i + 1}). The record is sixteen bytes.
     *
     * @param mediaSsrc the synchronization source of the stream whose packets are requested
     * @param pid       the packet identifier, the first lost sequence number, written as a sixteen-bit value
     * @param blp       the bitmask of lost packets following the packet identifier, written as a sixteen-bit
     *                  value
     * @return the generic NACK record bytes
     */
    public static byte[] buildNack(int mediaSsrc, int pid, int blp) {
        var out = new byte[16];
        out[0] = (byte) (VERSION_PADDING_PREFIX | (FMT_NACK & 0x1F));
        out[1] = (byte) PT_RTPFB;
        putU16(out, 2, out.length / 4 - 1);
        putU32(out, 4, NACK_SENDER_SSRC);
        putU32(out, 8, mediaSsrc);
        putU16(out, 12, pid);
        putU16(out, 14, blp);
        return out;
    }

    /**
     * Writes an unsigned sixteen-bit value big-endian into a buffer.
     *
     * @param out    the destination buffer
     * @param offset the index of the high byte
     * @param value  the value to write, masked to sixteen bits
     */
    private static void putU16(byte[] out, int offset, int value) {
        out[offset] = (byte) (value >>> 8);
        out[offset + 1] = (byte) value;
    }

    /**
     * Writes an unsigned thirty-two-bit value big-endian into a buffer.
     *
     * @param out    the destination buffer
     * @param offset the index of the most-significant byte
     * @param value  the value to write
     */
    private static void putU32(byte[] out, int offset, int value) {
        out[offset] = (byte) (value >>> 24);
        out[offset + 1] = (byte) (value >>> 16);
        out[offset + 2] = (byte) (value >>> 8);
        out[offset + 3] = (byte) value;
    }

    /**
     * Writes the low forty-eight bits of a value big-endian into a buffer.
     *
     * @param out    the destination buffer
     * @param offset the index of the most-significant byte
     * @param value  the value whose low forty-eight bits are written
     */
    private static void putU48(byte[] out, int offset, long value) {
        out[offset] = (byte) (value >>> 40);
        out[offset + 1] = (byte) (value >>> 32);
        out[offset + 2] = (byte) (value >>> 24);
        out[offset + 3] = (byte) (value >>> 16);
        out[offset + 4] = (byte) (value >>> 8);
        out[offset + 5] = (byte) value;
    }

    /**
     * Writes an unsigned sixty-four-bit value big-endian into a buffer.
     *
     * @param out    the destination buffer
     * @param offset the index of the most-significant byte
     * @param value  the value to write
     */
    private static void putU64(byte[] out, int offset, long value) {
        putU32(out, offset, (int) (value >>> 32));
        putU32(out, offset + 4, (int) value);
    }

    /**
     * Carries the fields of one RFC 3550 reception report block, the per-remote-stream reception quality a
     * Sender Report or Receiver Report conveys.
     *
     * @param ssrc             the remote stream synchronization source this block reports on
     * @param fractionLost     the fraction of packets lost since the previous report, an eight-bit
     *                         fixed-point value scaled by two hundred fifty-six
     * @param cumulativeLost   the signed twenty-four-bit cumulative number of packets lost over the session
     * @param extendedHighestSeq the rollover-extended highest sequence number received from the stream
     * @param interarrivalJitter the estimated interarrival jitter, in RTP timestamp units
     * @param lastSr           the middle thirty-two bits of the NTP timestamp of the last Sender Report
     *                         received from the stream, or {@code 0} when none has been received
     * @param delaySinceLastSr the delay since the last Sender Report was received, in units of
     *                         {@code 1/65536} seconds, or {@code 0} when none has been received
     */
    public record ReportBlock(int ssrc,
                              int fractionLost,
                              long cumulativeLost,
                              long extendedHighestSeq,
                              long interarrivalJitter,
                              long lastSr,
                              long delaySinceLastSr) {
    }
}
