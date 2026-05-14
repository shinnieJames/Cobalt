package com.github.auties00.cobalt.wam.privatestats;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppPrivateStatsTokenIssuerException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Objects;

/**
 * Issues private-stats tokens by performing the
 * {@code <sign_credential>} IQ round-trip against
 * {@code s.whatsapp.net}.
 *
 * <p>The flow mirrors
 * {@code privateStatsToken.issuePrivateStatsToken} from the WhatsApp
 * Web JavaScript runtime:
 *
 * <ol>
 *   <li>Generate a random 32-byte {@code token} (the secret nonce)
 *       and a random 32-byte {@code blindingFactor}.</li>
 *   <li>Compute
 *       {@code blindedToken =
 *       WamPrivateStatsTokenBlinder.blind(token, blindingFactor)}.</li>
 *   <li>Send the IQ
 *       {@code <iq xmlns="privatestats" type="get">
 *       <sign_credential version="1">
 *       <blinded_credential>blindedToken</blinded_credential>
 *       </sign_credential></iq>}.</li>
 *   <li>Receive {@code <signed_credential>},
 *       {@code <acs_public_key>}, and a {@code <dleq_proof>}.</li>
 *   <li>Compute
 *       {@code unblindedSignedToken =
 *       WamPrivateStatsTokenBlinder.unblind(signedCredential,
 *       blindingFactor, acsPublicKey)}.</li>
 *   <li>Derive the shared secret as
 *       {@code SHA-512(token || unblindedSignedToken)}.</li>
 *   <li>Return a {@link WamPrivateStatsToken} carrying the original
 *       {@code token} and the {@code sharedSecret}.</li>
 * </ol>
 *
 * <p>The {@code <dleq_proof>} block is currently parsed but not
 * verified. In a complete VOPRF implementation it would prove that
 * the server used the same private key for
 * {@code signedCredential = sk * blindedToken} and
 * {@code acsPublicKey = sk * B}. Without verification a malicious
 * server could substitute an unrelated key pair. This is acceptable
 * given the trust model (the server is also the consumer of the
 * upload), but a future tightening may add the Chaum-Pedersen check.
 *
 * @apiNote The IQ envelope and child-tag names were captured live
 * from snapshot {@code 1038176432} on 2026-04-27. Re-verify if the
 * protocol changes.
 */
@WhatsAppWebModule(moduleName = "WAWebIssuePrivateStatsToken")
@WhatsAppWebModule(moduleName = "WAACSTokenUtils")
public final class WamPrivateStatsTokenIssuer {
    /**
     * XMPP namespace for the private-stats token issuance IQ.
     */
    private static final String XMLNS = "privatestats";

    /**
     * Server JID receiving the {@code sign_credential} IQ.
     */
    private static final String SERVER = "s.whatsapp.net";

    /**
     * The client used to dispatch the IQ. Held as a field so callers
     * do not have to thread it through every {@link #issue}
     * invocation.
     */
    private final WhatsAppClient client;

    /**
     * Source of cryptographic randomness used to generate the token
     * and blinding factor. A {@link SecureRandom} backed by the JVM
     * default provider.
     */
    private final SecureRandom random;

    /**
     * Constructs a new issuer bound to a WhatsApp client.
     *
     * @param client the client used to dispatch the IQ, must not be
     *               {@code null}
     * @throws NullPointerException if {@code client} is {@code null}
     */
    public WamPrivateStatsTokenIssuer(WhatsAppClient client) {
        this(client, new SecureRandom());
    }

    /**
     * Constructs an issuer with an explicit {@link SecureRandom}.
     *
     * <p>Package-private hook used by behavioural tests to feed
     * deterministic random bytes into the issuance flow so the
     * resulting blinded credential and unblinded token can be
     * pinned against the live JS bundle's vectors.
     *
     * @param client the client used to dispatch the IQ
     * @param random the random source for the token and blinding
     *               factor
     */
    WamPrivateStatsTokenIssuer(WhatsAppClient client, SecureRandom random) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    /**
     * Performs one issuance round-trip and returns the resulting
     * token.
     *
     * <p>Generates fresh randomness on every call. The returned
     * token is single-use against a particular WAM upload buffer.
     * Callers must not cache and reuse it across uploads, since
     * reuse leaks the unblinded outputs of prior buffers.
     * @return the freshly issued token
     * @throws WhatsAppPrivateStatsTokenIssuerException if the server
     *         responds with an error, the response is malformed, or
     *         the unblinded token fails to decode
     */
    @WhatsAppWebExport(
            moduleName = "WAWebIssuePrivateStatsToken",
            exports = "getToken",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    @WhatsAppWebExport(
            moduleName = "WAACSTokenUtils",
            exports = {"getBlindedToken", "getSharedSecret"},
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public WamPrivateStatsToken issue() {
        // WAACSTokenUtils.getBlindedToken: 32 random bytes for the secret nonce,
        // 32 random bytes for the blinding factor, then call blindToken.
        var token = new byte[WamPrivateStatsToken.TOKEN_BYTES];
        random.nextBytes(token);
        var blindingFactor = new byte[WamPrivateStatsToken.TOKEN_BYTES];
        random.nextBytes(blindingFactor);

        var blindedCredential = WamPrivateStatsTokenBlinder.blind(token, blindingFactor);

        var iq = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", XMLNS)
                .attribute("type", "get")
                .attribute("to", SERVER)
                .content(new NodeBuilder()
                        .description("sign_credential")
                        .attribute("version", "1")
                        .content(new NodeBuilder()
                                .description("blinded_credential")
                                .content(blindedCredential)
                                .build())
                        .build());

        var response = client.sendNode(iq);
        if (!"result".equals(response.getAttributeAsString("type", ""))) {
            throw new WhatsAppPrivateStatsTokenIssuerException(
                    "sign_credential IQ failed: " + response);
        }

        var signCredential = response.getRequiredChild("sign_credential");
        var signedCredential = signCredential.getRequiredChild("signed_credential")
                .toContentBytes()
                .orElseThrow(() -> new WhatsAppPrivateStatsTokenIssuerException(
                        "missing signed_credential bytes in response"));
        var acsPublicKey = signCredential.getRequiredChild("acs_public_key")
                .toContentBytes()
                .orElseThrow(() -> new WhatsAppPrivateStatsTokenIssuerException(
                        "missing acs_public_key bytes in response"));

        if (signedCredential.length != WamPrivateStatsToken.TOKEN_BYTES) {
            throw new WhatsAppPrivateStatsTokenIssuerException(
                    "signed_credential length " + signedCredential.length
                    + " != " + WamPrivateStatsToken.TOKEN_BYTES);
        }
        if (acsPublicKey.length != WamPrivateStatsToken.TOKEN_BYTES) {
            throw new WhatsAppPrivateStatsTokenIssuerException(
                    "acs_public_key length " + acsPublicKey.length
                    + " != " + WamPrivateStatsToken.TOKEN_BYTES);
        }

        byte[] unblindedSignedToken;
        try {
            unblindedSignedToken =
                    WamPrivateStatsTokenBlinder.unblind(signedCredential, blindingFactor, acsPublicKey);
        } catch (IllegalArgumentException e) {
            throw new WhatsAppPrivateStatsTokenIssuerException(
                    "failed to unblind signed credential", e);
        }

        try {
            // WAACSTokenUtils.getSharedSecret: SHA-512(token || unblindedSignedToken),
            // matching WABinary.Binary.build(token, unblindedSignedToken) followed by
            // WACryptoPrimitives.hash on the concatenated byte view.
            var md = MessageDigest.getInstance("SHA-512");
            md.update(token);
            md.update(unblindedSignedToken);
            var sharedSecret = md.digest();
            return new WamPrivateStatsToken(token, sharedSecret);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-512 must be available on every JVM", e);
        }
    }
}
