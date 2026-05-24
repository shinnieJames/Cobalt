package com.github.auties00.cobalt.call.internal.rtp;

import com.github.auties00.cobalt.call.internal.rtp.srtp.SrtpEndpoint;
import com.github.auties00.cobalt.call.internal.rtp.srtp.SrtpRole;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip tests for the paired {@link RtpSender} +
 * {@link RtpReceiver} — Opus payloads sent by one side appear at the
 * other side's downstream listener with timestamps and sequence
 * numbers preserved, packet loss surfaced as PLC triggers, and SSRC
 * filtering applied.
 */
public class RtpSenderReceiverTest {

    /**
     * Test SSRC.
     */
    private static final int SSRC = 0x12345678;

    /**
     * Opus payload type per WebRTC convention.
     */
    private static final int OPUS_PAYLOAD_TYPE = 111;

    /**
     * Opus clock rate.
     */
    private static final int OPUS_CLOCK_RATE = 48_000;

    /**
     * Builds a fresh keying-material block for the SRTP pair.
     */
    private static byte[] randomKeying() {
        var k = new byte[SrtpEndpoint.KEYING_MATERIAL_LENGTH];
        new SecureRandom().nextBytes(k);
        return k;
    }

    /**
     * Sender encodes one payload, receiver decodes it byte-exact —
     * verifies the SRTP-protect / unprotect / RTP header round-trip.
     */
    @Test
    public void singlePayloadRoundTrips() {
        var keying = randomKeying();
        try (var senderSrtp = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT);
             var receiverSrtp = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.SERVER)) {
            var inbound = new ArrayList<RtpReceiver.InboundRtp>();
            var receiver = new RtpReceiver(receiverSrtp, SSRC, OPUS_PAYLOAD_TYPE,
                    inbound::add);
            var sender = new RtpSender(OPUS_PAYLOAD_TYPE, SSRC, OPUS_CLOCK_RATE,
                    senderSrtp, receiver::onSrtpPacket);

            var payload = "opus-payload-bytes".getBytes();
            sender.send(payload, 0L, true);
            receiver.drain();

            assertEquals(1, inbound.size());
            assertArrayEquals(payload, inbound.get(0).payload());
            assertTrue(inbound.get(0).marker());
            assertFalse(inbound.get(0).missing());
        }
    }

    /**
     * Multiple payloads in monotonic-ptsMs order arrive in the same
     * order on the receiver side.
     */
    @Test
    public void multiplePayloadsArriveInOrder() {
        var keying = randomKeying();
        try (var senderSrtp = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT);
             var receiverSrtp = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.SERVER)) {
            var inbound = new ArrayList<RtpReceiver.InboundRtp>();
            var receiver = new RtpReceiver(receiverSrtp, SSRC, OPUS_PAYLOAD_TYPE,
                    inbound::add);
            var sender = new RtpSender(OPUS_PAYLOAD_TYPE, SSRC, OPUS_CLOCK_RATE,
                    senderSrtp, receiver::onSrtpPacket);

            for (var i = 0; i < 10; i++) {
                sender.send(("frame-" + i).getBytes(), i * 20L, false);
            }
            receiver.drain();

            assertEquals(10, inbound.size());
            for (var i = 0; i < 10; i++) {
                assertArrayEquals(("frame-" + i).getBytes(), inbound.get(i).payload());
            }
        }
    }

    /**
     * RTP timestamps step by {@code clockRate * frameDurationMs /
     * 1000} per frame.
     */
    @Test
    public void timestampsScaleByClockRate() {
        var keying = randomKeying();
        try (var senderSrtp = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT);
             var receiverSrtp = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.SERVER)) {
            var inbound = new ArrayList<RtpReceiver.InboundRtp>();
            var receiver = new RtpReceiver(receiverSrtp, SSRC, OPUS_PAYLOAD_TYPE,
                    inbound::add);
            var random = new SecureRandom(new byte[]{0, 0, 0, 0, 0, 0, 0, 0});
            var sender = new RtpSender(OPUS_PAYLOAD_TYPE, SSRC, OPUS_CLOCK_RATE,
                    senderSrtp, receiver::onSrtpPacket, random);

            sender.send("a".getBytes(), 0L, false);
            sender.send("b".getBytes(), 20L, false);  // 20ms later
            receiver.drain();

            // Difference between consecutive timestamps =
            // clockRate * 20ms / 1000 = 48000 * 20 / 1000 = 960.
            var diff = (inbound.get(1).timestamp() - inbound.get(0).timestamp()) & 0xFFFFFFFFL;
            assertEquals(960L, diff);
        }
    }

    /**
     * Lost packets (gap in the sequence) surface as a missing
     * marker on the receiver — the codec's PLC trigger.
     */
    @Test
    public void packetLossSurfacesAsPlcTrigger() {
        var keying = randomKeying();
        try (var senderSrtp = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT);
             var receiverSrtp = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.SERVER)) {
            var inbound = new ArrayList<RtpReceiver.InboundRtp>();
            // Capture sender output so we can selectively drop.
            var dropped = new ArrayList<byte[]>();
            var receiver = new RtpReceiver(receiverSrtp, SSRC, OPUS_PAYLOAD_TYPE,
                    inbound::add);
            var sender = new RtpSender(OPUS_PAYLOAD_TYPE, SSRC, OPUS_CLOCK_RATE,
                    senderSrtp, dropped::add);

            sender.send("first".getBytes(), 0L, false);
            sender.send("lost".getBytes(), 20L, false);
            sender.send("third".getBytes(), 40L, false);
            // Deliver only first and third; drop the middle one.
            receiver.onSrtpPacket(dropped.get(0));
            receiver.onSrtpPacket(dropped.get(2));
            receiver.drain();

            // Expect: first (real), missing-marker, third (real).
            assertEquals(3, inbound.size());
            assertArrayEquals("first".getBytes(), inbound.get(0).payload());
            assertFalse(inbound.get(0).missing());
            assertTrue(inbound.get(1).missing(),
                    "gap must be reported as missing marker");
            assertArrayEquals("third".getBytes(), inbound.get(2).payload());
        }
    }

    /**
     * Packets carrying the wrong SSRC are dropped — the receiver
     * only accepts the configured SSRC.
     */
    @Test
    public void wrongSsrcIsDropped() {
        var keying = randomKeying();
        try (var senderSrtp = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT);
             var receiverSrtp = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.SERVER)) {
            var inbound = new ArrayList<RtpReceiver.InboundRtp>();
            var receiver = new RtpReceiver(receiverSrtp, SSRC, OPUS_PAYLOAD_TYPE,
                    inbound::add);
            // Sender uses a DIFFERENT ssrc.
            var otherSsrc = 0xCAFEBABE;
            var sender = new RtpSender(OPUS_PAYLOAD_TYPE, otherSsrc, OPUS_CLOCK_RATE,
                    senderSrtp, receiver::onSrtpPacket);

            sender.send("filtered".getBytes(), 0L, false);
            receiver.drain();
            assertEquals(0, inbound.size());
        }
    }

    /**
     * Sending with a backward {@code ptsMs} is rejected.
     */
    @Test
    public void backwardsPtsRejected() {
        var keying = randomKeying();
        try (var senderSrtp = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT)) {
            var sender = new RtpSender(OPUS_PAYLOAD_TYPE, SSRC, OPUS_CLOCK_RATE,
                    senderSrtp, p -> {
            });
            sender.send("a".getBytes(), 100L, false);
            assertThrows(IllegalArgumentException.class,
                    () -> sender.send("b".getBytes(), 50L, false));
        }
    }

    /**
     * Sequence numbers wrap modulo 65536 and don't break.
     */
    @Test
    public void sequenceWrapsAroundCleanly() {
        var keying = randomKeying();
        try (var senderSrtp = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT);
             var receiverSrtp = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.SERVER)) {
            var inbound = new ArrayList<RtpReceiver.InboundRtp>();
            // Force the initial sequence to 65530 so we wrap quickly.
            var random = new SecureRandom() {
                @Override
                public int nextInt(int bound) {
                    return 65530;
                }
            };
            var receiver = new RtpReceiver(receiverSrtp, SSRC, OPUS_PAYLOAD_TYPE,
                    inbound::add);
            var sender = new RtpSender(OPUS_PAYLOAD_TYPE, SSRC, OPUS_CLOCK_RATE,
                    senderSrtp, receiver::onSrtpPacket, random);

            for (var i = 0; i < 10; i++) {
                sender.send(("p" + i).getBytes(), i * 20L, false);
            }
            receiver.drain();
            assertEquals(10, inbound.size());
            assertArrayEquals("p0".getBytes(), inbound.get(0).payload());
            assertArrayEquals("p9".getBytes(), inbound.get(9).payload());
        }
    }
}
