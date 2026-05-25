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
 * Issues a fresh {@link WamPrivateStatsToken} per call by performing one {@code sign_credential} IQ round-trip
 * against {@code s.whatsapp.net}.
 *
 * <p>This collapses three WA Web concerns into a single class:
 *
 * <ul>
 *   <li>the {@code getBlindedToken} export, which generates the random token, the random blinding factor, and
 *       computes {@code blindedToken = blind(token, blindingFactor)};</li>
 *   <li>the {@code getToken} export, which orchestrates the IQ exchange behind a semaphore and a WAM-event
 *       emitter, and further delegates to {@code WAWebRedeemACSToken.redeemACSToken} for caching, retry, and
 *       project-aware token reuse;</li>
 *   <li>the {@code getSharedSecret} export, which derives {@code SHA-512(token || unblindedSignedToken)} as the
 *       upload authentication key.</li>
 * </ul>
 *
 * <p>One issuer is used per {@link WhatsAppClient}; each {@link #issue()} call performs a fresh round-trip and
 * returns a single-use token. The token must not be reused across uploads; VOPRF unlinkability rests on each
 * scalar being used at most once.
 *
 * @implNote
 * This implementation diverges from WA Web in three ways. (1) It does not cache tokens; WA Web batches a
 * project-keyed pool of tokens via {@code WAWebCRUDOperationsACSTokens}, redeeming them lazily. (2) It uses the
 * older {@code version="1"} request shape without the {@code project_name} child; the current
 * {@code WASmaxOutPrivatestatsSignCredentialRequest} module emits {@code version="2"} with a
 * {@code project_name} element. (3) The {@code dleq_proof} block carried in the response is not verified; a
 * fully trust-minimised implementation would Chaum-Pedersen-check that the server used the same secret key for
 * {@code signedCredential = sk * blindedToken} and for {@code acsPublicKey = sk * B}.
 */
@WhatsAppWebModule(moduleName = "WAWebIssuePrivateStatsToken")
@WhatsAppWebModule(moduleName = "WAACSTokenUtils")
public final class WamPrivateStatsTokenIssuer {
    /**
     * The XMPP namespace under which the private-stats issuance IQ is routed.
     *
     * @implNote
     * This implementation matches the {@code xmlns="privatestats"} attribute emitted by the
     * {@code WASmaxOutPrivatestatsSignCredentialRequest} module.
     */
    private static final String XMLNS = "privatestats";

    /**
     * The server JID accepting the {@code sign_credential} IQ.
     *
     * @implNote
     * This implementation matches the {@code WAWap.S_WHATSAPP_NET} target used by the
     * {@code WASmaxOutPrivatestatsSignCredentialRequest} module.
     */
    private static final String SERVER = "s.whatsapp.net";

    /**
     * The WhatsApp client used to dispatch the IQ.
     */
    private final WhatsAppClient client;

    /**
     * The cryptographic random source used to generate the token nonce and the blinding factor.
     *
     * <p>Supplied via the package-private constructor so behavioural tests can pin issuance to the captured
     * live-bundle vectors.
     */
    private final SecureRandom random;

    /**
     * Constructs a new issuer bound to the given client and a default-provider {@link SecureRandom}.
     *
     * @param client the {@link WhatsAppClient} used to dispatch the IQ
     * @throws NullPointerException if {@code client} is {@code null}
     */
    public WamPrivateStatsTokenIssuer(WhatsAppClient client) {
        this(client, new SecureRandom());
    }

    /**
     * Constructs a new issuer bound to the given client and an explicit {@link SecureRandom} source.
     *
     * <p>Intended for behavioural tests that script the random source so the produced blinded credential and
     * unblinded token can be checked against captured live-bundle known-answer vectors.
     *
     * @param client the {@link WhatsAppClient} used to dispatch the IQ
     * @param random the random source for the token and the blinding factor
     * @throws NullPointerException if either argument is {@code null}
     */
    WamPrivateStatsTokenIssuer(WhatsAppClient client, SecureRandom random) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    /**
     * Performs one {@code sign_credential} round-trip and returns the resulting {@link WamPrivateStatsToken}.
     *
     * <p>Returns a single-use token; the caller is responsible for pairing it with exactly one
     * {@link WamPrivateStatsUploader#upload(byte[])} invocation. Reusing the same token across uploads breaks
     * the VOPRF unlinkability that motivates the protocol. The IQ shape is:
     * {@snippet :
     *     <iq xmlns="privatestats" type="get" to="s.whatsapp.net">
     *       <sign_credential version="1">
     *         <blinded_credential>BLINDED_BYTES</blinded_credential>
     *       </sign_credential>
     *     </iq>
     * }
     *
     * @implNote
     * This implementation inlines the three WA Web responsibilities ({@code getBlindedToken}, {@code getToken},
     * {@code getSharedSecret}) into one call:
     * <ul>
     *   <li>draws two 32-byte sequences from {@link #random},</li>
     *   <li>blinds via {@link WamPrivateStatsTokenBlinder#blind(byte[], byte[])},</li>
     *   <li>dispatches the IQ via {@link WhatsAppClient#sendNode(NodeBuilder)},</li>
     *   <li>parses {@code signed_credential} and {@code acs_public_key} under the {@code sign_credential}
     *       reply,</li>
     *   <li>unblinds via {@link WamPrivateStatsTokenBlinder#unblind(byte[], byte[], byte[])},</li>
     *   <li>computes {@code SHA-512(token || unblindedSignedToken)}.</li>
     * </ul>
     *
     * @return the freshly issued token
     * @throws WhatsAppPrivateStatsTokenIssuerException if the server returns a non-result IQ, if the response is
     *         missing either the signed credential or the ACS public key, if either has the wrong byte length,
     *         or if the unblinding fails to decode the signed credential
     * @see WamPrivateStatsTokenBlinder#blind(byte[], byte[])
     * @see WamPrivateStatsTokenBlinder#unblind(byte[], byte[], byte[])
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
