package com.github.auties00.cobalt.call.internal.interaction;

import com.github.auties00.cobalt.call.CallInteraction;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Encodes a {@link CallInteraction} into the RTP/RTCP-shaped wire
 * envelope WhatsApp Web's wasm produces and writes to the
 * pre-negotiated DataChannel.
 *
 * @apiNote The output is the PLAINTEXT 12-byte header concatenated
 * with a per-interaction body — ready to be handed to an SRTP
 * encryptor before transmission. Per-interaction (byte0, byte1) tag
 * pairs are stable across calls; the SSRC, sequence, and timestamp
 * come from the per-call {@link InteractionStreamState}. See
 * {@code reference_wa_voip_interaction_wire_format} memory for the
 * envelope mapping table.
 * @implNote The encoder is intentionally empirical: WhatsApp Web's
 * encrypted SRTP body cannot be recovered without keys, so the
 * plaintext body inside each interaction is built from the most
 * plausible plain shape (e.g. reaction body = ~12-byte wrapper +
 * UTF-8 emoji bytes, matching the 2-byte length delta observed
 * between thumbsup and heart captures). The exact wrapper bytes are
 * unknown and filled with zeros pending Phase 8 end-to-end
 * validation against a real WA peer.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipStackInterfaceWeb")
public final class CallInteractionEncoder {
    /**
     * RTP version 2, no padding, no extension, no CSRC count.
     */
    private static final int RTP_V2 = 0x80;
    /**
     * RTP version 2, no padding, no extension, CSRC count = 1.
     */
    private static final int RTP_V2_CC1 = 0x81;
    /**
     * RTP version 2, marker bit set, no padding/extension/CSRC.
     */
    private static final int RTP_V2_MARKER = 0x90;
    /**
     * RTP version 2, marker bit set, CSRC count = 1.
     */
    private static final int RTP_V2_MARKER_CC1 = 0x91;

    /**
     * Reaction payload type (RTP PT 119).
     */
    private static final int PT_REACTION = 0x77;
    /**
     * Generic request payload type (RTP PT 120) — used for
     * keyFrame and peerMute requests.
     */
    private static final int PT_REQUEST = 0x78;
    /**
     * RTCP sender-report packet type (200).
     */
    private static final int RTCP_SR = 0xc8;

    /**
     * Fixed body length for raise/lower hand packets (110-byte body
     * + 12-byte header = 122-byte packet, matching live captures).
     */
    private static final int RAISE_HAND_BODY_LEN = 110;
    /**
     * Fixed body length for video-upgrade packets (110-byte body +
     * 12-byte header = 122-byte packet, matching live captures).
     */
    private static final int VIDEO_UPGRADE_BODY_LEN = 110;
    /**
     * Plaintext wrapper size around the emoji UTF-8 in a reaction
     * body. Derived from live capture: 28-byte thumbsup packet -
     * 12-byte header - 4-byte UTF-8(thumbsup) = 12 bytes; 30-byte
     * heart packet - 12 - 6 = 12 bytes (consistent).
     */
    private static final int REACTION_WRAPPER_LEN = 12;
    /**
     * Best-effort empirical body size for a peer-mute or key-frame
     * request. Live captures saw 40-238 bytes depending on the
     * target WID encoding. We size as {@code 16} bytes of wrapper +
     * the WID UTF-8 bytes; subject to revision after Phase 8
     * validation.
     */
    private static final int REQUEST_WRAPPER_LEN = 16;

    /**
     * Private constructor — class is a stateless namespace.
     */
    private CallInteractionEncoder() {
    }

    /**
     * Encodes one interaction packet, including the 12-byte
     * RTP/RTCP-shaped header.
     *
     * @param interaction the interaction to encode
     * @param state       the per-call stream state from which to
     *                    draw SSRC, sequence, and timestamp
     * @return the plaintext packet (header + body), ready for SRTP
     * encryption
     * @throws NullPointerException if any argument is {@code null}
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
     * Builds a reaction packet: header (V2/PT=119) + 12-byte wrapper
     * + UTF-8 emoji bytes.
     *
     * @param reaction the reaction
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
     * Builds a raise/lower-hand packet: header (RTCP SR shape) +
     * fixed-size body. The single bool is encoded into byte 0 of
     * the body; remaining bytes are zeros pending Phase 8
     * validation.
     *
     * @param raised whether the hand is raised (vs lowered)
     * @param state  the per-call stream state
     * @return the encoded packet
     */
    private static byte[] encodeHandToggle(boolean raised, InteractionStreamState state) {
        var body = new byte[RAISE_HAND_BODY_LEN];
        body[0] = (byte) (raised ? 1 : 0);
        return frame(RTP_V2_CC1, RTCP_SR,
                state, InteractionStreamState.Stream.CONTROL, body);
    }

    /**
     * Builds a peer-mute or key-frame request packet: header (RTP
     * PT=120) + 16-byte wrapper + target-WID UTF-8 bytes.
     *
     * @param target the target user/peer WID as a string
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
     * Builds a video-upgrade request packet: header (RTCP with
     * extension) + fixed-size empty body.
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
     * Assembles a 12-byte RTP/RTCP-shaped header plus the given body
     * into one packet. Pulls fresh sequence + timestamp from the
     * stream state; reads the stream's SSRC.
     *
     * @param byte0  the header byte-0 (V/P/X/CC bitfield)
     * @param byte1  the header byte-1 (M/PT bitfield)
     * @param state  the per-call stream state
     * @param stream the logical stream
     * @param body   the body bytes (appended after the header)
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
