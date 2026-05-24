package com.github.auties00.cobalt.call.internal.interaction;

import com.github.auties00.cobalt.call.CallInteraction;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CallInteractionEncoder} — verifies the
 * per-interaction (byte0, byte1) tag pairs, header layout,
 * sequence/timestamp counter advancement, and body size
 * relationships observed in live captures.
 */
public class CallInteractionEncoderTest {
    /**
     * Reaction packets use V2/PT=119 framing and the body length
     * tracks the UTF-8 emoji length exactly.
     */
    @Test
    public void reactionEnvelope() {
        var state = new InteractionStreamState();
        var thumb = CallInteractionEncoder.encode(
                new CallInteraction.Reaction("👍"), state);
        // RTP V2, no padding/ext/CSRC -> byte0 = 0x80, PT=119 -> byte1 = 0x77.
        assertEquals((byte) 0x80, thumb[0]);
        assertEquals((byte) 0x77, thumb[1]);
        // 12-byte header + 12-byte wrapper + 4-byte UTF-8(thumbsup) = 28.
        assertEquals(28, thumb.length);
        // Last 4 bytes of the body are the emoji UTF-8.
        var thumbUtf8 = "👍".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(thumbUtf8,
                Arrays.copyOfRange(thumb, thumb.length - 4, thumb.length));
    }

    /**
     * The two reaction emojis we captured (thumbsup, heart) differ
     * in encoded length by exactly 2 bytes, matching their UTF-8
     * length delta (4 vs 6).
     */
    @Test
    public void reactionLengthTracksUtf8Delta() {
        var state = new InteractionStreamState();
        var thumb = CallInteractionEncoder.encode(
                new CallInteraction.Reaction("👍"), state);
        var heart = CallInteractionEncoder.encode(
                new CallInteraction.Reaction("❤️"), state);
        // thumbsup UTF-8 = 4 bytes; heart UTF-8 = 6 bytes.
        assertEquals(2, heart.length - thumb.length);
    }

    /**
     * Hand toggles share an envelope (byte0=0x81, byte1=0xc8) and a
     * fixed body length (110 bytes -> 122-byte packet).
     */
    @Test
    public void handToggleEnvelope() {
        var state = new InteractionStreamState();
        var raise = CallInteractionEncoder.encode(new CallInteraction.RaiseHand(), state);
        var lower = CallInteractionEncoder.encode(new CallInteraction.LowerHand(), state);
        assertEquals(122, raise.length);
        assertEquals(122, lower.length);
        assertEquals((byte) 0x81, raise[0]);
        assertEquals((byte) 0xc8, raise[1]);
        assertEquals((byte) 0x81, lower[0]);
        assertEquals((byte) 0xc8, lower[1]);
        // The bool sits in byte 12 (first body byte).
        assertEquals((byte) 0x01, raise[12]);
        assertEquals((byte) 0x00, lower[12]);
    }

    /**
     * Key-frame and peer-mute requests share the byte0=0x90,
     * byte1=0x78 framing.
     */
    @Test
    public void requestEnvelope() {
        var state = new InteractionStreamState();
        var keyFrame = CallInteractionEncoder.encode(new CallInteraction.KeyFrameRequest(), state);
        var peerMute = CallInteractionEncoder.encode(
                new CallInteraction.PeerMuteRequest("19153544650@lid", Optional.empty()), state);
        assertEquals((byte) 0x90, keyFrame[0]);
        assertEquals((byte) 0x78, keyFrame[1]);
        assertEquals((byte) 0x90, peerMute[0]);
        assertEquals((byte) 0x78, peerMute[1]);
    }

    /**
     * Video-upgrade packets use byte0=0x91, byte1=0xc8 and a fixed
     * 122-byte total length.
     */
    @Test
    public void videoUpgradeEnvelope() {
        var state = new InteractionStreamState();
        var pkt = CallInteractionEncoder.encode(new CallInteraction.VideoUpgradeRequest(), state);
        assertEquals(122, pkt.length);
        assertEquals((byte) 0x91, pkt[0]);
        assertEquals((byte) 0xc8, pkt[1]);
    }

    /**
     * Sequence number increments by 1 per packet within the same
     * stream; timestamp advances by {@code TIMESTAMP_STEP}.
     */
    @Test
    public void streamCountersAdvance() {
        var state = new InteractionStreamState();
        var p1 = CallInteractionEncoder.encode(
                new CallInteraction.Reaction("👍"), state);
        var p2 = CallInteractionEncoder.encode(
                new CallInteraction.Reaction("👍"), state);
        var seq1 = ((p1[2] & 0xff) << 8) | (p1[3] & 0xff);
        var seq2 = ((p2[2] & 0xff) << 8) | (p2[3] & 0xff);
        assertEquals(seq1 + 1, seq2);
        var ts1 = ((p1[4] & 0xff) << 24) | ((p1[5] & 0xff) << 16)
                | ((p1[6] & 0xff) << 8) | (p1[7] & 0xff);
        var ts2 = ((p2[4] & 0xff) << 24) | ((p2[5] & 0xff) << 16)
                | ((p2[6] & 0xff) << 8) | (p2[7] & 0xff);
        assertEquals(ts1 + InteractionStreamState.TIMESTAMP_STEP, ts2);
    }

    /**
     * The same SSRC is reused across all packets within one stream;
     * different streams have independent SSRCs.
     */
    @Test
    public void perStreamSsrc() {
        var state = new InteractionStreamState();
        var reactA = CallInteractionEncoder.encode(
                new CallInteraction.Reaction("a"), state);
        var reactB = CallInteractionEncoder.encode(
                new CallInteraction.Reaction("b"), state);
        var hand = CallInteractionEncoder.encode(
                new CallInteraction.RaiseHand(), state);
        var ssrcReactA = readSsrc(reactA);
        var ssrcReactB = readSsrc(reactB);
        var ssrcHand = readSsrc(hand);
        assertEquals(ssrcReactA, ssrcReactB, "reaction stream SSRC stable");
        assertNotEquals(ssrcReactA, ssrcHand, "reaction and control streams differ");
    }

    /**
     * Null arguments are rejected.
     */
    @Test
    public void nullArgsRejected() {
        var state = new InteractionStreamState();
        assertThrows(NullPointerException.class,
                () -> CallInteractionEncoder.encode(null, state));
        assertThrows(NullPointerException.class,
                () -> CallInteractionEncoder.encode(new CallInteraction.RaiseHand(), null));
    }

    /**
     * Reads the SSRC out of a packet (bytes 8..12, big-endian).
     *
     * @param packet the packet
     * @return the SSRC as an int
     */
    private static int readSsrc(byte[] packet) {
        return ((packet[8] & 0xff) << 24)
                | ((packet[9] & 0xff) << 16)
                | ((packet[10] & 0xff) << 8)
                | (packet[11] & 0xff);
    }
}
