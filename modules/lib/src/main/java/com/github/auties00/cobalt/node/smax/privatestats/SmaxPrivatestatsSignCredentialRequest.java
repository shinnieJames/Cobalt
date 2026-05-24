package com.github.auties00.cobalt.node.smax.privatestats;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <iq xmlns="privatestats" type="get">} stanza
 * asking the relay to sign a blinded ACS credential.
 *
 * @apiNote
 * Backs the privatestats anonymous-credential pipeline driven by WA
 * Web's {@code WAWebFetchACSTokens}; the local client mints a blinded
 * credential, the relay signs it with the per-project ACS key, and the
 * signed credential is later spent on a privatestats event submission
 * so the relay can authenticate the report without learning the
 * device identity. The reply is parsed by
 * {@link SmaxPrivatestatsSignCredentialResponse}.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutPrivatestatsSignCredentialRequest")
public final class SmaxPrivatestatsSignCredentialRequest implements SmaxOperation.Request {
    /**
     * The blinded credential bytes generated locally by the client.
     */
    private final byte[] blindedCredentialElementValue;

    /**
     * The project-name string identifying the privatestats project
     * the credential is being minted for.
     */
    private final String projectNameElementValue;

    /**
     * Constructs a new sign-credential request.
     *
     * @apiNote
     * The pair {@code (projectName, blindedCredential)} matches the
     * keys WA Web's {@code WAWebFetchACSTokens} passes to
     * {@code WASmaxPrivatestatsSignCredentialRPC.sendSignCredentialRPC}.
     *
     * @param blindedCredentialElementValue the blinded credential
     *                                      bytes; never {@code null}
     * @param projectNameElementValue the project name; never
     *                                {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public SmaxPrivatestatsSignCredentialRequest(byte[] blindedCredentialElementValue, String projectNameElementValue) {
        this.blindedCredentialElementValue = Objects.requireNonNull(blindedCredentialElementValue,
                "blindedCredentialElementValue cannot be null");
        this.projectNameElementValue = Objects.requireNonNull(projectNameElementValue,
                "projectNameElementValue cannot be null");
    }

    /**
     * Returns the blinded credential bytes.
     *
     * @return the bytes; never {@code null}
     */
    public byte[] blindedCredentialElementValue() {
        return blindedCredentialElementValue;
    }

    /**
     * Returns the project name.
     *
     * @return the project name; never {@code null}
     */
    public String projectNameElementValue() {
        return projectNameElementValue;
    }

    /**
     * Builds the outbound IQ stanza ready for dispatch.
     *
     * @apiNote
     * Produces
     * {@code <iq xmlns="privatestats" type="get" to="s.whatsapp.net">
     *   <sign_credential version="2">
     *     <blinded_credential>BYTES</blinded_credential>
     *     <project_name>STRING</project_name>
     *   </sign_credential></iq>}; the envelope's {@code id} is
     * stamped by the dispatch path.
     *
     * @implNote
     * This implementation pins {@code version="2"} on the
     * {@code <sign_credential>} child to match WA Web's
     * {@code makeSignCredentialRequest}; the older version-1 shape is
     * not supported.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the
     *         {@code <sign_credential>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutPrivatestatsSignCredentialRequest",
            exports = "makeSignCredentialRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var blindedCredentialNode = new NodeBuilder()
                .description("blinded_credential")
                .content(blindedCredentialElementValue)
                .build();
        var projectNameNode = new NodeBuilder()
                .description("project_name")
                .content(projectNameElementValue)
                .build();
        var signCredentialNode = new NodeBuilder()
                .description("sign_credential")
                .attribute("version", "2")
                .content(blindedCredentialNode, projectNameNode)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "privatestats")
                .attribute("to", Jid.userServer())
                .attribute("type", "get")
                .content(signCredentialNode);
    }

    /**
     * Returns whether the given object is a
     * {@link SmaxPrivatestatsSignCredentialRequest} with equal
     * blinded credential bytes and project name.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when both fields match
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxPrivatestatsSignCredentialRequest) obj;
        return Arrays.equals(this.blindedCredentialElementValue, that.blindedCredentialElementValue)
                && Objects.equals(this.projectNameElementValue, that.projectNameElementValue);
    }

    /**
     * Returns a hash code derived from the blinded credential bytes
     * and the project name.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        var result = Objects.hash(projectNameElementValue);
        result = 31 * result + Arrays.hashCode(blindedCredentialElementValue);
        return result;
    }

    /**
     * Returns a debug-friendly textual representation of this request.
     *
     * @return the textual representation
     */
    @Override
    public String toString() {
        return "SmaxPrivatestatsSignCredentialRequest[blindedCredentialElementValue="
                + Arrays.toString(blindedCredentialElementValue)
                + ", projectNameElementValue=" + projectNameElementValue + ']';
    }
}
