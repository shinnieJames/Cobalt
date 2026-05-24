package com.github.auties00.cobalt.node.iq.stats;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import java.util.Arrays;
import java.util.Objects;

/**
 * Outbound {@code <iq xmlns="privatestats" type="get">} stanza asking the relay to sign a
 * blinded credential point so the client can mint a redeemable private-stats token.
 *
 * @apiNote
 * Used by the privacy-preserving analytics pipeline (WA "Private Stats"): WA Web's
 * {@code WAWebIssuePrivateStatsToken.getToken} acquires a blinded EC point via
 * {@code WAACSTokenUtils}, sends it here to be signed by the relay, then unblinds the
 * returned signature to obtain an unlinkable token. The token is later redeemed (one per
 * project) by {@code WAWebUploadPrivateStatsBackend} when uploading anonymous metrics, so
 * the relay can verify the upload came from a valid client without learning which one.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutPrivatestatsSignCredentialRequest")
public final class IqIssuePrivateStatsTokenRequest implements IqOperation.Request {
    /**
     * Protocol version advertised on the {@code <sign_credential>} tag.
     *
     * @apiNote
     * Fixed at {@code "2"} in the current WA Web bundle; bumped when the relay's signing
     * scheme changes incompatibly.
     */
    private static final String SIGN_CREDENTIAL_VERSION = "2";

    /**
     * Raw bytes of the blinded elliptic-curve point.
     *
     * @apiNote
     * The relay signs this point and returns the signed-credential bytes; the client
     * unblinds the signature locally using the random blinding factor that was multiplied
     * into the point at request-build time, producing an unlinkable redeemable token.
     */
    private final byte[] blindedCredential;

    /**
     * Project-name bytes (UTF-8) that scope the minted credential to a particular collector.
     *
     * @apiNote
     * Routed verbatim into the {@code <project_name>} grandchild; the project name maps
     * one-to-one to the analytics surface the token will be redeemed against
     * (for example a specific WAM event family) so the relay can mint per-project rate
     * caps.
     */
    private final byte[] projectName;

    /**
     * Constructs a new issue-private-stats-token request.
     *
     * @apiNote
     * Defensively clones both byte arrays so subsequent mutation by the caller does not
     * affect the dispatched stanza.
     *
     * @param blindedCredential the blinded credential bytes
     * @param projectName       the project-name bytes
     * @throws NullPointerException if either argument is {@code null}
     */
    public IqIssuePrivateStatsTokenRequest(byte[] blindedCredential, byte[] projectName) {
        this.blindedCredential = Objects.requireNonNull(blindedCredential, "blindedCredential cannot be null").clone();
        this.projectName = Objects.requireNonNull(projectName, "projectName cannot be null").clone();
    }

    /**
     * Returns a defensive copy of the blinded-credential bytes routed into the
     * {@code <blinded_credential>} child.
     *
     * @return a clone of the blinded-credential bytes, never {@code null}
     */
    public byte[] blindedCredential() {
        return blindedCredential.clone();
    }

    /**
     * Returns a defensive copy of the project-name bytes routed into the
     * {@code <project_name>} child.
     *
     * @return a clone of the project-name bytes, never {@code null}
     */
    public byte[] projectName() {
        return projectName.clone();
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Produces a {@code <iq xmlns="privatestats" type="get">} envelope addressed to
     * {@link JidServer#user()} and wrapping a single {@code <sign_credential version="2">}
     * child carrying the {@code <blinded_credential>} and {@code <project_name>}
     * grandchildren in that order.
     *
     * @return a {@link NodeBuilder} carrying the {@code <iq>} envelope and the
     *         {@code <sign_credential>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutPrivatestatsSignCredentialRequest",
            exports = "makeSignCredentialRequest", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebIssuePrivateStatsToken",
            exports = "getToken", adaptation = WhatsAppAdaptation.ADAPTED)
    public NodeBuilder toNode() {
        var blindedNode = new NodeBuilder()
                .description("blinded_credential")
                .content(blindedCredential)
                .build();
        var projectNameNode = new NodeBuilder()
                .description("project_name")
                .content(projectName)
                .build();
        var signCredentialNode = new NodeBuilder()
                .description("sign_credential")
                .attribute("version", SIGN_CREDENTIAL_VERSION)
                .content(blindedNode, projectNameNode)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "privatestats")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(signCredentialNode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqIssuePrivateStatsTokenRequest) obj;
        return Arrays.equals(this.blindedCredential, that.blindedCredential)
                && Arrays.equals(this.projectName, that.projectName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(blindedCredential), Arrays.hashCode(projectName));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "IqIssuePrivateStatsTokenRequest[blindedCredentialLength=" + blindedCredential.length
                + ", projectNameLength=" + projectName.length + ']';
    }
}
