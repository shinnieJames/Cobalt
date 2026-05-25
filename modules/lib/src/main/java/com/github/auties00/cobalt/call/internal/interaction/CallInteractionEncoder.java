package com.github.auties00.cobalt.call.internal.interaction;

import com.github.auties00.cobalt.call.CallInteraction;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Encodes a {@link CallInteraction} into the RTP/RTCP-shaped wire envelope written to the pre-negotiated DataChannel.
 *
 * <p>Each interaction is serialized as a fixed 12-byte header concatenated with a per-interaction body. The header is
 * an RTP/RTCP-shaped tuple: a byte-0 version/padding/extension/CSRC bitfield, a byte-1 marker/payload-type bitfield, a
 * 16-bit sequence number, a 32-bit timestamp, and a 32-bit SSRC, all big-endian. The (byte0, byte1) tag pair is fixed
 * per interaction kind, while the sequence, timestamp, and SSRC are drawn from the per-call {@link InteractionStreamState}.
 * The returned bytes are plaintext: callers hand them to an SRTP encryptor before transmission.
 *
 * @implNote This implementation is empirical: WhatsApp Web's encrypted SRTP body cannot be recovered without keys, so the
 * plaintext body of each interaction is reconstructed from the most plausible plain shape inferred from live captures. A
 * reaction body is a {@link #REACTION_WRAPPER_LEN}-byte wrapper followed by the UTF-8 emoji bytes, matching the 2-byte
 * length delta observed between thumbs-up and heart captures. The wrapper bytes themselves are unknown and zero-filled
 * pending end-to-end validation against a real WhatsApp peer.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipStackInterfaceWeb")
public final class CallInteractionEncoder {
    /**
     * Encodes the RTP byte-0 bitfield for version 2 with no padding, no extension, and a CSRC count of zero.
     */
    private static final int RTP_V2 = 0x80;

    /**
     * Encodes the RTP byte-0 bitfield for version 2 with no padding, no extension, and a CSRC count of one.
     */
    private static final int RTP_V2_CC1 = 0x81;

    /**
     * Encodes the RTP byte-1 bitfield for version 2 with the marker bit set and no padding, extension, or CSRC.
     */
    private static final int RTP_V2_MARKER = 0x90;

    /**
     * Encodes the RTP byte-1 bitfield for version 2 with the marker bit set and a CSRC count of one.
     */
    private static final int RTP_V2_MARKER_CC1 = 0x91;

    /**
     * Encodes the RTP payload type for reactions, payload type 119.
     */
    private static final int PT_REACTION = 0x77;

    /**
     * Encodes the RTP payload type for generic requests, payload type 120, shared by key-frame and peer-mute requests.
     */
    private static final int PT_REQUEST = 0x78;

    /**
     * Encodes the RTCP sender-report packet type, 200.
     */
    private static final int RTCP_SR = 0xc8;

    /**
     * Holds the fixed body length, in bytes, of a raise-hand or lower-hand packet.
     *
     * @implNote This implementation uses 110, which with the 12-byte header yields the 122-byte packet observed in live
     * captures.
     */
    private static final int RAISE_HAND_BODY_LEN = 110;

    /**
     * Holds the fixed body length, in bytes, of a video-upgrade packet.
     *
     * @implNote This implementation uses 110, which with the 12-byte header yields the 122-byte packet observed in live
     * captures.
     */
    private static final int VIDEO_UPGRADE_BODY_LEN = 110;

    /**
     * Holds the plaintext wrapper size, in bytes, that precedes the UTF-8 emoji in a reaction body.
     *
     * @implNote This implementation uses 12, derived from live captures: the 28-byte thumbs-up packet minus the 12-byte
     * header minus the 4 UTF-8 bytes of the thumbs-up emoji is 12, and the 30-byte heart packet minus 12 minus 6 is also
     * 12.
     */
    private static final int REACTION_WRAPPER_LEN = 12;

    /**
     * Holds the plaintext wrapper size, in bytes, that precedes the target-WID UTF-8 bytes in a peer-mute or key-frame
     * request body.
     *
     * @implNote This implementation uses 16, a best-effort value: live captures saw bodies of 40 to 238 bytes depending
     * on the target-WID encoding, modeled as 16 bytes of wrapper plus the WID UTF-8 bytes, subject to revision after
     * end-to-end validation.
     */
    private static final int REQUEST_WRAPPER_LEN = 16;

    /**
     * Prevents instantiation of this stateless utility class.
     */
    private CallInteractionEncoder() {
    }

    /**
     * Encodes one interaction into a plaintext packet comprising the 12-byte RTP/RTCP-shaped header and its body.
     *
     * <p>The interaction kind selects the header tag pair, the logical stream, and the body layout. The sequence,
     * timestamp, and SSRC written into the header are drawn from {@code state}, which is mutated as a side effect: the
     * sequence and timestamp counters of the selected stream advance by one packet.
     *
     * @param interaction the interaction to encode
     * @param state       the per-call stream state from which to draw the SSRC, sequence, and timestamp
     * @return the plaintext packet, header followed by body, ready for SRTP encryption
     * @throws NullPointerException if {@code interaction} or {@code state} is {@code null}
     */
    public static byte[] encode(CallInteraction interaction, InteractionStreamState state) {
        Objects.requireNonNull(interaction, "interaction cannot be null");
        Objects.requireNonNull(state, "state cannot be null");
        return switch (interaction) {
            case CallInteraction.Reaction r -> encodeReaction(r, state);
            case CallInteraction.RaiseHand _ -> encodeHandToggle(true, state);
            case CallInteraction.LowerHand _ -> encodeHandToggle(false, state);
            case CallInteraction.PeerMuteRequest r -> encodeRequest(r.target(), state);
            case CallInteraction.KeyFrameRequest _ -> encodeRequest("", state);
            case CallInteraction.VideoUpgradeRequest _ -> encodeVideoUpgrade(state);
        };
    }

    /**
     * Encodes a {@link CallInteraction.Reaction} into a reaction packet.
     *
     * <p>The packet carries the {@link #RTP_V2} byte-0 tag and the {@link #PT_REACTION} payload type, a
     * {@link #REACTION_WRAPPER_LEN}-byte zero wrapper, and the UTF-8 bytes of the reaction emoji. It is framed on the
     * {@link InteractionStreamState.Stream#REACTION} stream.
     *
     * @param reaction the reaction whose emoji is encoded
     * @param state    the per-call stream state
     * @return the encoded packet
     */
    private static byte[] encodeReaction(CallInteraction.Reaction reaction, InteractionStreamState state) {
        var emojiBytes = reaction.emoji().getBytes(StandardCharsets.UTF_8);
        var body = new byte[REACTION_WRAPPER_LEN + emojiBytes.length];
        System.arraycopy(emojiBytes, 0, body, REACTION_WRAPPER_LEN, emojiBytes.length);
        return frame(RTP_V2, PT_REACTION,
                state, InteractionStreamState.Stream.REACTION, body);
    }

    /**
     * Encodes a raise-hand or lower-hand gesture into a hand-toggle packet.
     *
     * <p>The packet carries the {@link #RTP_V2_CC1} byte-0 tag and the {@link #RTCP_SR} packet type and a fixed
     * {@link #RAISE_HAND_BODY_LEN}-byte body whose byte 0 holds 1 for raised and 0 for lowered; the remaining bytes are
     * zero. It is framed on the {@link InteractionStreamState.Stream#CONTROL} stream.
     *
     * @param raised {@code true} to encode a raise-hand gesture, {@code false} to encode a lower-hand gesture
     * @param state  the per-call stream state
     * @return the encoded packet
     * @implNote This implementation zero-fills every body byte after byte 0; the true layout of the remaining bytes is
     * unrecovered.
     */
    private static byte[] encodeHandToggle(boolean raised, InteractionStreamState state) {
        var body = new byte[RAISE_HAND_BODY_LEN];
        body[0] = (byte) (raised ? 1 : 0);
        return frame(RTP_V2_CC1, RTCP_SR,
                state, InteractionStreamState.Stream.CONTROL, body);
    }

    /**
     * Encodes a peer-mute or key-frame request into a request packet.
     *
     * <p>The packet carries the {@link #RTP_V2_MARKER} byte-0 tag and the {@link #PT_REQUEST} payload type, a
     * {@link #REQUEST_WRAPPER_LEN}-byte zero wrapper, and the UTF-8 bytes of {@code target}. A key-frame request passes
     * an empty target, yielding a body of only the wrapper. The packet is framed on the
     * {@link InteractionStreamState.Stream#CONTROL} stream.
     *
     * @param target the target peer WID in string form, or the empty string for a key-frame request
     * @param state  the per-call stream state
     * @return the encoded packet
     */
    private static byte[] encodeRequest(String target, InteractionStreamState state) {
        var wid = target.getBytes(StandardCharsets.UTF_8);
        var body = new byte[REQUEST_WRAPPER_LEN + wid.length];
        System.arraycopy(wid, 0, body, REQUEST_WRAPPER_LEN, wid.length);
        return frame(RTP_V2_MARKER, PT_REQUEST,
                state, InteractionStreamState.Stream.CONTROL, body);
    }

    /**
     * Encodes a video-upgrade request into a video-upgrade packet.
     *
     * <p>The packet carries the {@link #RTP_V2_MARKER_CC1} byte-0 tag and the {@link #RTCP_SR} packet type and a fixed
     * {@link #VIDEO_UPGRADE_BODY_LEN}-byte zero body. It is framed on the
     * {@link InteractionStreamState.Stream#VIDEO_UPGRADE} stream.
     *
     * @param state the per-call stream state
     * @return the encoded packet
     */
    private static byte[] encodeVideoUpgrade(InteractionStreamState state) {
        var body = new byte[VIDEO_UPGRADE_BODY_LEN];
        return frame(RTP_V2_MARKER_CC1, RTCP_SR,
                state, InteractionStreamState.Stream.VIDEO_UPGRADE, body);
    }

    /**
     * Assembles a 12-byte RTP/RTCP-shaped header and the given body into one packet.
     *
     * <p>The header is laid out big-endian as byte 0, byte 1, a 16-bit sequence number, a 32-bit timestamp, and a 32-bit
     * SSRC. The sequence and timestamp are pulled fresh from the stream state via {@link InteractionStreamState#nextSequence}
     * and {@link InteractionStreamState#nextTimestamp}, advancing those counters; the SSRC is read with
     * {@link InteractionStreamState#ssrc}. The body is copied verbatim after the header.
     *
     * @param byte0  the header byte 0, the version/padding/extension/CSRC bitfield
     * @param byte1  the header byte 1, the marker/payload-type bitfield
     * @param state  the per-call stream state
     * @param stream the logical stream whose counters and SSRC are used
     * @param body   the body bytes appended after the header
     * @return the assembled packet
     */
    private static byte[] frame(int byte0, int byte1,
                                InteractionStreamState state,
                                InteractionStreamState.Stream stream,
                                byte[] body) {
        var seq = state.nextSequence(stream);
        var ts = state.nextTimestamp(stream);
        var ssrc = state.ssrc(stream);
        var packet = new byte[12 + body.length];
        packet[0] = (byte) byte0;
        packet[1] = (byte) byte1;
        packet[2] = (byte) ((seq >>> 8) & 0xff);
        packet[3] = (byte) (seq & 0xff);
        packet[4] = (byte) ((ts >>> 24) & 0xff);
        packet[5] = (byte) ((ts >>> 16) & 0xff);
        packet[6] = (byte) ((ts >>> 8) & 0xff);
        packet[7] = (byte) (ts & 0xff);
        packet[8] = (byte) ((ssrc >>> 24) & 0xff);
        packet[9] = (byte) ((ssrc >>> 16) & 0xff);
        packet[10] = (byte) ((ssrc >>> 8) & 0xff);
        packet[11] = (byte) (ssrc & 0xff);
        System.arraycopy(body, 0, packet, 12, body.length);
        return packet;
    }
}
