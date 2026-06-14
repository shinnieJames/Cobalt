package com.github.auties00.cobalt.call.rtp;

import com.github.auties00.cobalt.call.rtp.srtp.SrtpEndpoint;
import com.github.auties00.cobalt.call.rtp.srtp.SrtpRole;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip coverage for the paired {@link RtpSender} and {@link RtpReceiver}: Opus payloads
 * sent by one side reach the other side's listener with timestamps and sequence numbers
 * preserved, packet loss surfaces as a missing marker driving packet loss concealment, SSRC
 * filtering is applied, backward presentation timestamps are rejected, and sequence numbers
 * wrap cleanly. Each pair is keyed from one shared random keying-material block, with the
 * sender as the SRTP client and the receiver as the SRTP server.
 */
public class RtpSenderReceiverTest {

    private static final int SSRC = 0x12345678;

    private static final int OPUS_PAYLOAD_TYPE = 111;

    private static final int OPUS_CLOCK_RATE = 48_000;

    private static byte[] randomKeying() {
        var k = new byte[SrtpEndpoint.KEYING_MATERIAL_LENGTH];
        new SecureRandom().nextBytes(k);
        return k;
    }

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
            sender.send("b".getBytes(), 20L, false);
            receiver.drain();

            // Consecutive timestamps differ by clockRate * 20ms / 1000 = 48000 * 20 / 1000 = 960
            var diff = (inbound.get(1).timestamp() - inbound.get(0).timestamp()) & 0xFFFFFFFFL;
            assertEquals(960L, diff);
        }
    }

    @Test
    public void packetLossSurfacesAsPlcTrigger() {
        var keying = randomKeying();
        try (var senderSrtp = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT);
             var receiverSrtp = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.SERVER)) {
            var inbound = new ArrayList<RtpReceiver.InboundRtp>();
            // The sender writes into this list instead of the receiver so the middle packet
            // can be withheld, simulating loss.
            var dropped = new ArrayList<byte[]>();
            var receiver = new RtpReceiver(receiverSrtp, SSRC, OPUS_PAYLOAD_TYPE,
                    inbound::add);
            var sender = new RtpSender(OPUS_PAYLOAD_TYPE, SSRC, OPUS_CLOCK_RATE,
                    senderSrtp, dropped::add);

            sender.send("first".getBytes(), 0L, false);
            sender.send("lost".getBytes(), 20L, false);
            sender.send("third".getBytes(), 40L, false);
            // Deliver the first and third packets only, withholding the middle one
            receiver.onSrtpPacket(dropped.get(0));
            receiver.onSrtpPacket(dropped.get(2));
            receiver.drain();

            // first (real), a missing marker for the withheld packet, then third (real)
            assertEquals(3, inbound.size());
            assertArrayEquals("first".getBytes(), inbound.get(0).payload());
            assertFalse(inbound.get(0).missing());
            assertTrue(inbound.get(1).missing(),
                    "gap must be reported as missing marker");
            assertArrayEquals("third".getBytes(), inbound.get(2).payload());
        }
    }

    @Test
    public void wrongSsrcIsDropped() {
        var keying = randomKeying();
        try (var senderSrtp = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT);
             var receiverSrtp = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.SERVER)) {
            var inbound = new ArrayList<RtpReceiver.InboundRtp>();
            var receiver = new RtpReceiver(receiverSrtp, SSRC, OPUS_PAYLOAD_TYPE,
                    inbound::add);
            // Sender uses an SSRC that differs from the one the receiver is configured to accept
            var otherSsrc = 0xCAFEBABE;
            var sender = new RtpSender(OPUS_PAYLOAD_TYPE, otherSsrc, OPUS_CLOCK_RATE,
                    senderSrtp, receiver::onSrtpPacket);

            sender.send("filtered".getBytes(), 0L, false);
            receiver.drain();
            assertEquals(0, inbound.size());
        }
    }

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

    @Test
    public void sequenceWrapsAroundCleanly() {
        var keying = randomKeying();
        try (var senderSrtp = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT);
             var receiverSrtp = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.SERVER)) {
            var inbound = new ArrayList<RtpReceiver.InboundRtp>();
            // Pin the initial sequence number to 65530 so the 10-packet run crosses the wrap
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
