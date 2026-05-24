package com.github.auties00.cobalt.call.internal.signaling;

import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.binary.NodeReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parity test: Cobalt's {@link NodeReader} produces the same logical
 * tree that {@code WAWap.decodeStanza} produces in WhatsApp Web for a
 * captured outbound {@code <offer>} payload.
 *
 * <p>The fixture is a real WAP-encoded offer body emitted by the live
 * VoIP engine during an outbound 1:1 call (snapshot
 * {@code 1038697023-47d2a47020a5}; primary device JID
 * {@code 39110693621863:1@lid} → peer {@code 83116928594056@lid}).
 *
 * <p>This is the foundational parity test: every later test that
 * compares Java-built call stanzas against captured fixtures depends
 * on the decoder agreeing with the JS one.
 */
public class OfferDecodeParityTest {

    /**
     * The base64-encoded WAP body the wasm produced for the first
     * captured offer.
     */
    private static final String OFFER_B64 =
            "APgG7Bxo/CAwMEI4RTg2NTY2M0QyOENGRkY5NDY5QTc3MTU2QTM4MUf3AQH8DjM5MTEwNjkzNjIxODYz" +
            "+Af4AuL8CwQBKoq42KdjGfny+AWhHewe7B3sh/gFoR3sHuwd/AUxNjAwMPgD7GjsJKL4BLTsIVX8BwEF" +
            "9wnkuxP4Auyp+AL4BBEM+vwOODMxMTY5Mjg1OTQwNTb8A2xpZPgB+AId/CCQE2p3uv4uuSEa0OhzzqAh" +
            "gGsCBJp9382QF2J73fCqQPgEEQz3AQH8DjgzMTE2OTI4NTk0MDU2+AH4Ah38IGzjNmpdPyGKl3ZD+0pK" +
            "J799mVshmvhMzPsUEfKX8dyH+APsFewaRQ==";

    /**
     * The privacy child's content bytes, hex-encoded.
     */
    private static final String EXPECTED_PRIVACY_HEX = "04012A8AB8D8A76319F9F2";

    /**
     * The capability child's content bytes, hex-encoded.
     */
    private static final String EXPECTED_CAPABILITY_HEX = "0105F709E4BB13";

    /**
     * Asserts the decoder produces an offer with all the expected
     * children, in the right order, with the right attribute / content
     * shape.
     *
     * @throws IOException if the decoder fails to read the buffer
     */
    @Test
    public void decodesOfferStructure() throws IOException {
        var bytes = Base64.getDecoder().decode(OFFER_B64.replace("\n", ""));
        Node offer;
        try (var decoder = NodeReader.fromBuffer(ByteBuffer.wrap(bytes))) {
            offer = decoder.decode();
        }
        assertNotNull(offer);
        assertEquals("offer", offer.description());
        assertEquals("00B8E865663D28CFFF9469A77156A381", offer.getAttributeAsString("call-id", null));
        var creator = offer.getAttributeAsJid("call-creator", null);
        assertNotNull(creator);
        assertEquals("39110693621863", creator.user());
        assertEquals(1, creator.device());

        var children = List.copyOf(offer.children());
        // Expected layout: privacy, audio×2, net, capability, destination, encopt
        assertEquals(7, children.size(), () -> "expected 7 children, got " + children.size() + ": " + children);
        assertEquals("privacy", children.get(0).description());
        assertEquals("audio", children.get(1).description());
        assertEquals("audio", children.get(2).description());
        assertEquals("net", children.get(3).description());
        assertEquals("capability", children.get(4).description());
        assertEquals("destination", children.get(5).description());
        assertEquals("encopt", children.get(6).description());

        // privacy bytes
        var privacyBytes = children.get(0).toContentBytes().orElseThrow();
        assertEquals(EXPECTED_PRIVACY_HEX, hex(privacyBytes), "privacy bytes mismatch");

        // audio rates
        assertEquals("opus", children.get(1).getAttributeAsString("enc", null));
        assertEquals("8000", children.get(1).getAttributeAsString("rate", null));
        assertEquals("16000", children.get(2).getAttributeAsString("rate", null));

        // net medium
        assertEquals("3", children.get(3).getAttributeAsString("medium", null));

        // capability bytes
        var capBytes = children.get(4).toContentBytes().orElseThrow();
        assertEquals(EXPECTED_CAPABILITY_HEX, hex(capBytes), "capability bytes mismatch");
        assertEquals("1", children.get(4).getAttributeAsString("ver", null));

        // destination → 2 <to> children, each with one <enc> 32-byte child
        var destination = children.get(5);
        var destChildren = List.copyOf(destination.children());
        assertEquals(2, destChildren.size(), "expected 2 <to> children");
        for (var to : destChildren) {
            assertEquals("to", to.description());
            assertNotNull(to.getAttributeAsJid("jid", null));
            var encChildren = List.copyOf(to.children());
            assertEquals(1, encChildren.size(), "expected exactly one <enc> child per <to>");
            var enc = encChildren.get(0);
            assertEquals("enc", enc.description());
            assertTrue(enc.attributes().isEmpty(),
                    () -> "expected <enc> with no attrs (raw call-key envelope, not yet pkmsg-wrapped); got " + enc);
            var encBytes = enc.toContentBytes().orElseThrow();
            assertEquals(32, encBytes.length,
                    () -> "expected 32-byte raw call-key envelope, got " + encBytes.length);
        }

        // encopt
        assertEquals("2", children.get(6).getAttributeAsString("keygen", null));
    }

    /**
     * Asserts that the two destination call-keys differ — confirming
     * that the wasm derives per-device keys rather than emitting one
     * shared key. Drives the SRTP-KDF reverse work.
     *
     * @throws IOException if the decoder fails to read the buffer
     */
    @Test
    public void perDeviceCallKeysDiffer() throws IOException {
        var bytes = Base64.getDecoder().decode(OFFER_B64.replace("\n", ""));
        Node offer;
        try (var decoder = NodeReader.fromBuffer(ByteBuffer.wrap(bytes))) {
            offer = decoder.decode();
        }
        var children = List.copyOf(offer.children());
        var destination = children.get(5);
        var destChildren = List.copyOf(destination.children());
        var firstEnc = List.copyOf(destChildren.get(0).children()).get(0).toContentBytes().orElseThrow();
        var secondEnc = List.copyOf(destChildren.get(1).children()).get(0).toContentBytes().orElseThrow();
        assertNotNull(firstEnc);
        assertNotNull(secondEnc);
        assertEquals(firstEnc.length, secondEnc.length);
        // bytes must differ — if they're equal, our assumption about per-device derivation is wrong
        var equal = true;
        for (var i = 0; i < firstEnc.length; i++) {
            if (firstEnc[i] != secondEnc[i]) {
                equal = false;
                break;
            }
        }
        assertTrue(!equal, "per-device call-keys are byte-equal — wasm uses one shared key, not per-device");
    }

    /**
     * Hex-encodes the given bytes in upper-case, no separators.
     *
     * @param bytes the byte array to encode
     * @return the hex string
     */
    private static String hex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (var b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }
}
