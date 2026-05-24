package com.github.auties00.cobalt.wam.privatestats;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppException;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link WamPrivateStatsTokenIssuer}'s IQ orchestration
 * against the captured live-bundle vectors.
 *
 * @apiNote
 * Pins the outbound {@code <iq xmlns="privatestats" type="get">}
 * stanza shape, the response parsing path, and the resulting
 * {@link WamPrivateStatsToken} bytes against the
 * {@code ed25519-live-bundle-vectors.json} KAT so issuance is
 * byte-validated against the same vectors that
 * {@code Ed25519LiveBundleKatTest} pins for the blind and unblind
 * primitives in isolation.
 *
 * @implNote
 * This implementation feeds a deterministic {@link SecureRandom} into
 * the package-private constructor so the {@code token} and
 * {@code blindingFactor} draws line up with the captured vector.
 */
@DisplayName("WamPrivateStatsTokenIssuer behavioural")
class WamPrivateStatsTokenIssuerTest {
    /**
     * The classpath path of the ed25519 KAT vectors fixture, reused
     * as the source of the deterministic token, blinding factor,
     * and response-payload bytes.
     */
    private static final String VECTORS_FIXTURE = "/fixtures/wam/ed25519-live-bundle-vectors.json";

    /**
     * Verifies that {@link WamPrivateStatsTokenIssuer#issue()}
     * produces an IQ whose blinded-credential bytes match the
     * captured {@code blind(msg, scalar)} output and returns a
     * token whose {@code token()} and {@code sharedSecret()} bytes
     * match the captured vector.
     */
    @Test
    @DisplayName("issue round-trip produces the captured unblinded token")
    void issueRoundTripMatchesLiveKat() {
        var vector = loadFirstVector();
        var msg = HexFormat.of().parseHex(vector.msg);
        var scalar = HexFormat.of().parseHex(vector.scalar);
        var signed = HexFormat.of().parseHex(vector.signed);
        var pk = HexFormat.of().parseHex(vector.pk);
        var expectedUnblinded = HexFormat.of().parseHex(vector.unblinded);
        var expectedBlinded = HexFormat.of().parseHex(vector.blinded);

        var capturedIq = new AtomicReference<Node>();
        var client = TestWhatsAppClient.create()
                .withSendNodeHandler(builder -> {
                    var built = builder.build();
                    capturedIq.set(built);
                    return successResponse(signed, pk);
                });
        var issuer = new WamPrivateStatsTokenIssuer(client, scriptedRandom(msg, scalar));

        var token = issuer.issue();

        assertNotNull(token, "issue() must return a non-null token");
        assertArrayEquals(msg, token.token(),
                "token.token() bytes must equal the original randomised token");
        var expectedSharedSecret = sha512Concat(msg, expectedUnblinded);
        assertArrayEquals(expectedSharedSecret, token.sharedSecret(),
                "sharedSecret bytes must equal SHA-512(token || unblinded)");

        var iq = capturedIq.get();
        assertNotNull(iq, "sendNode must have been invoked");
        assertEquals("iq", iq.description());
        assertEquals("privatestats", iq.getAttributeAsString("xmlns", ""));
        assertEquals("get", iq.getAttributeAsString("type", ""));
        assertEquals("s.whatsapp.net", iq.getAttributeAsString("to", ""));

        var signCredential = iq.getChild("sign_credential").orElseThrow();
        assertEquals("1", signCredential.getAttributeAsString("version", ""));
        var blindedCredential = signCredential.getChild("blinded_credential").orElseThrow();
        if (blindedCredential instanceof Node.BytesNode bytesNode) {
            assertArrayEquals(expectedBlinded, bytesNode.content(),
                    "outbound blinded_credential bytes must equal the live-bundle's blind(msg, scalar) output");
        } else {
            throw new AssertionError("blinded_credential is not a BytesNode: " + blindedCredential);
        }
    }

    /**
     * Verifies that a server-side error response (an IQ with
     * {@code type="error"}) raises
     * {@code WhatsAppPrivateStatsTokenIssuerException}.
     */
    @Test
    @DisplayName("server error response throws WhatsAppPrivateStatsTokenIssuerException")
    void serverErrorThrows() {
        var vector = loadFirstVector();
        var msg = HexFormat.of().parseHex(vector.msg);
        var scalar = HexFormat.of().parseHex(vector.scalar);
        var client = TestWhatsAppClient.create()
                .withSendNodeHandler(builder -> new NodeBuilder()
                        .description("iq")
                        .attribute("type", "error")
                        .build());
        var issuer = new WamPrivateStatsTokenIssuer(client, scriptedRandom(msg, scalar));
        assertThrows(WhatsAppException.class, issuer::issue);
    }

    /**
     * Verifies that a response missing the {@code <signed_credential>}
     * child throws rather than returning a malformed token.
     */
    @Test
    @DisplayName("response missing signed_credential throws")
    void missingSignedCredentialThrows() {
        var vector = loadFirstVector();
        var msg = HexFormat.of().parseHex(vector.msg);
        var scalar = HexFormat.of().parseHex(vector.scalar);
        var pk = HexFormat.of().parseHex(vector.pk);
        var client = TestWhatsAppClient.create()
                .withSendNodeHandler(builder -> new NodeBuilder()
                        .description("iq")
                        .attribute("type", "result")
                        .content(new NodeBuilder()
                                .description("sign_credential")
                                .content(new NodeBuilder()
                                        .description("acs_public_key")
                                        .content(pk)
                                        .build())
                                .build())
                        .build());
        var issuer = new WamPrivateStatsTokenIssuer(client, scriptedRandom(msg, scalar));
        assertThrows(Exception.class, issuer::issue,
                "missing signed_credential must raise an exception, not return a malformed token");
    }

    /**
     * Verifies that a wrong-length {@code <signed_credential>}
     * payload throws.
     */
    @Test
    @DisplayName("wrong-length signed_credential throws")
    void wrongLengthSignedCredentialThrows() {
        var vector = loadFirstVector();
        var msg = HexFormat.of().parseHex(vector.msg);
        var scalar = HexFormat.of().parseHex(vector.scalar);
        var pk = HexFormat.of().parseHex(vector.pk);
        var client = TestWhatsAppClient.create()
                .withSendNodeHandler(builder -> new NodeBuilder()
                        .description("iq")
                        .attribute("type", "result")
                        .content(new NodeBuilder()
                                .description("sign_credential")
                                .content(new NodeBuilder()
                                        .description("signed_credential")
                                        .content(new byte[16])
                                        .build())
                                .content(new NodeBuilder()
                                        .description("acs_public_key")
                                        .content(pk)
                                        .build())
                                .build())
                        .build());
        var issuer = new WamPrivateStatsTokenIssuer(client, scriptedRandom(msg, scalar));
        assertThrows(Exception.class, issuer::issue);
    }

    /**
     * Builds a successful IQ response shaped like the live server's
     * {@code sign_credential} reply.
     *
     * @apiNote
     * Used by the round-trip and edge-case tests to canned-respond
     * to the issuer's {@link com.github.auties00.cobalt.client.WhatsAppClient#sendNode}
     * call.
     *
     * @param signed the signed-credential bytes
     * @param pk     the ACS public key bytes
     * @return the synthetic IQ result node
     */
    private static Node successResponse(byte[] signed, byte[] pk) {
        return new NodeBuilder()
                .description("iq")
                .attribute("type", "result")
                .content(new NodeBuilder()
                        .description("sign_credential")
                        .content(List.of(
                                new NodeBuilder()
                                        .description("signed_credential")
                                        .content(signed)
                                        .build(),
                                new NodeBuilder()
                                        .description("acs_public_key")
                                        .content(pk)
                                        .build()))
                        .build())
                .build();
    }

    /**
     * Loads the first ed25519 KAT vector from the fixture file.
     *
     * @apiNote
     * Every test reuses the same vector so the captured bytes are
     * easy to cross-reference with the ones pinned by
     * {@code Ed25519LiveBundleKatTest}.
     *
     * @return the deserialised first vector
     */
    private static Vector loadFirstVector() {
        try (var stream = WamPrivateStatsTokenIssuerTest.class.getResourceAsStream(VECTORS_FIXTURE)) {
            if (stream == null) {
                throw new AssertionError("ed25519 vectors fixture missing on classpath: " + VECTORS_FIXTURE);
            }
            var json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            var root = JSON.parseObject(json);
            var vector = root.getJSONArray("vectors").getJSONObject(0);
            return new Vector(
                    vector.getString("msg"),
                    vector.getString("scalar"),
                    vector.getString("blinded"),
                    vector.getString("pk"),
                    vector.getString("signed"),
                    vector.getString("unblinded"));
        } catch (IOException error) {
            throw new UncheckedIOException("failed to load " + VECTORS_FIXTURE, error);
        }
    }

    /**
     * Returns a {@link SecureRandom} that delivers exactly two
     * scripted byte sequences and refuses a third call.
     *
     * @apiNote
     * The token issuer draws from {@code nextBytes} exactly twice
     * per {@link WamPrivateStatsTokenIssuer#issue()} (once for the
     * token, once for the blinding factor); failing loudly on a
     * third call ensures the test breaks if the production code's
     * draw count drifts.
     *
     * @param first  the first byte sequence to deliver
     * @param second the second byte sequence to deliver
     * @return the scripted {@link SecureRandom}
     */
    private static SecureRandom scriptedRandom(byte[] first, byte[] second) {
        return new SecureRandom() {
            private int call;

            @Override
            public void nextBytes(byte[] target) {
                var source = switch (call++) {
                    case 0 -> first;
                    case 1 -> second;
                    default -> throw new AssertionError(
                            "scripted SecureRandom asked for more than two byte sequences");
                };
                if (target.length != source.length) {
                    throw new AssertionError("scripted SecureRandom: target length " + target.length
                            + " != source length " + source.length);
                }
                System.arraycopy(source, 0, target, 0, source.length);
            }
        };
    }

    /**
     * Verifies that the scripted {@link SecureRandom} fails loudly on
     * a third call.
     *
     * @apiNote
     * Defends the surrounding tests against the production code
     * drifting to call {@code nextBytes} more than twice without
     * the helper being updated.
     */
    @Test
    @DisplayName("scripted SecureRandom rejects a third call (issue must use exactly two random sequences)")
    void scriptedRandomFailsLoudOnExtraCall() {
        var random = scriptedRandom(new byte[32], new byte[32]);
        random.nextBytes(new byte[32]);
        random.nextBytes(new byte[32]);
        assertThrows(AssertionError.class, () -> random.nextBytes(new byte[32]));
        assertTrue(true);
    }

    /**
     * Computes {@code SHA-512(a || b)}.
     *
     * @apiNote
     * Mirrors {@code WAACSTokenUtils.getSharedSecret(token, unblindedSignedToken)}
     * for use as the assertion oracle.
     *
     * @param a the first input
     * @param b the second input
     * @return the 64-byte SHA-512 digest
     */
    private static byte[] sha512Concat(byte[] a, byte[] b) {
        try {
            var md = MessageDigest.getInstance("SHA-512");
            md.update(a);
            md.update(b);
            return md.digest();
        } catch (NoSuchAlgorithmException error) {
            throw new AssertionError("SHA-512 must be available on every JVM", error);
        }
    }

    /**
     * A compact holder for the per-vector hex strings consumed by
     * the tests.
     *
     * @param msg       the message hex
     * @param scalar    the blinding-factor hex
     * @param blinded   the captured blinded-credential hex
     * @param pk        the captured ACS public-key hex
     * @param signed    the captured signed-credential hex
     * @param unblinded the captured unblinded hex
     */
    private record Vector(String msg, String scalar, String blinded, String pk, String signed, String unblinded) {
    }
}
