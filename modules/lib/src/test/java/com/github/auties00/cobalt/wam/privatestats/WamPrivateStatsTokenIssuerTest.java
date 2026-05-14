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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural tests for {@link WamPrivateStatsTokenIssuer}'s IQ
 * orchestration, paired with a deterministic
 * {@link SecureRandom} so the resulting blinded credential and
 * unblinded token can be pinned against the
 * {@code ed25519-live-bundle-vectors.json} KAT.
 *
 * <p>The crypto primitives themselves (blind / unblind) are byte-for-byte
 * KAT'd in {@code Ed25519LiveBundleKatTest}; this class only asserts
 * that the issuer:
 *
 * <ul>
 *   <li>builds the outbound {@code <iq xmlns="privatestats" type="get">}
 *       stanza correctly with the blinded credential as the
 *       {@code <blinded_credential>} content;</li>
 *   <li>parses the server response's {@code <signed_credential>} +
 *       {@code <acs_public_key>} children and produces a token whose
 *       {@code token()} bytes equal the live-bundle's
 *       {@code unblinded} vector;</li>
 *   <li>raises the documented exception on server-side errors,
 *       missing fields, and wrong-length payloads.</li>
 * </ul>
 */
@DisplayName("WamPrivateStatsTokenIssuer behavioural")
class WamPrivateStatsTokenIssuerTest {
    /**
     * Path of the ed25519 KAT vectors fixture, reused as the source
     * of the deterministic token / blinding-factor / response
     * payload bytes.
     */
    private static final String VECTORS_FIXTURE = "/fixtures/wam/ed25519-live-bundle-vectors.json";

    /**
     * Verifies that {@link WamPrivateStatsTokenIssuer#issue()} sends
     * an IQ whose blinded-credential content equals the live JS
     * bundle's {@code blind(msg, scalar)} output for the same
     * inputs, and that the returned token's bytes equal the
     * captured {@code unblinded} value.
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
        // WamPrivateStatsToken.token() carries the original random
        // token bytes (what the scripted SecureRandom delivered as
        // the first nextBytes call, i.e. `msg`).
        assertArrayEquals(msg, token.token(),
                "token.token() bytes must equal the original randomised token");
        // sharedSecret = SHA-512(token || unblind(signed, scalar, pk))
        // per WAACSTokenUtils.getSharedSecret.
        var expectedSharedSecret = sha512Concat(msg, expectedUnblinded);
        assertArrayEquals(expectedSharedSecret, token.sharedSecret(),
                "sharedSecret bytes must equal SHA-512(token || unblinded)");

        // The IQ outbound shape: <iq xmlns="privatestats" type="get" to="s.whatsapp.net">
        //   <sign_credential version="1"><blinded_credential>BYTES</blinded_credential></sign_credential>
        // </iq>
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
     * Verifies that a server-side error response — {@code type="error"}
     * — raises {@code WhatsAppPrivateStatsTokenIssuerException}.
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
     * Verifies that a response missing the
     * {@code <signed_credential>} child throws.
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
     * Verifies that a response whose {@code signed_credential} is
     * the wrong length throws.
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
                                        .content(new byte[16]) // wrong: should be 32 bytes
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
     * {@code sign_credential} reply, with the given signed
     * credential and ACS public key as the two byte-content
     * children.
     *
     * @param signed the signed-credential bytes
     * @param pk     the ACS public key bytes
     * @return the synthetic success node
     */
    private static Node successResponse(byte[] signed, byte[] pk) {
        return new NodeBuilder()
                .description("iq")
                .attribute("type", "result")
                .content(new NodeBuilder()
                        .description("sign_credential")
                        .content(java.util.List.of(
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
     * Loads the first ed25519 KAT vector.
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
     * Returns a {@link SecureRandom} that returns {@code first}
     * on the first {@code nextBytes} call and {@code second} on
     * the second; any third call throws.
     *
     * <p>The token issuer calls {@code nextBytes} exactly twice
     * per {@code issue()} — once for the token, once for the
     * blinding factor — so this script suffices.
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
     * Smoke assertion that the script delivers exactly two
     * sequences and then refuses a third — defends against the
     * production code drifting to call {@code nextBytes} a third
     * time without updating this test.
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
     * Computes {@code SHA-512(a || b)}, matching
     * {@code WAACSTokenUtils.getSharedSecret(token, unblindedSignedToken)}.
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
     * Compact holder for the fixture's per-vector hex strings.
     */
    private record Vector(String msg, String scalar, String blinded, String pk, String signed, String unblinded) {
    }
}
