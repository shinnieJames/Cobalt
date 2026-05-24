package com.github.auties00.cobalt.node.iq.stats;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed family of inbound reply variants produced by the relay in response to an
 * {@link IqIssuePrivateStatsTokenRequest}.
 *
 * @apiNote
 * The {@link ClientError} and {@link ServerError} split mirrors WA Web's WAM telemetry
 * classification ({@code WAWebWamEnumSignCredentialResult.ERROR_BAD_REQUEST} versus
 * {@code ERROR_SERVER}); the private-stats driver retries the credential mint on
 * {@link ServerError} but not on {@link ClientError}.
 */
public sealed interface IqIssuePrivateStatsTokenResponse extends IqOperation.Response
        permits IqIssuePrivateStatsTokenResponse.Success, IqIssuePrivateStatsTokenResponse.ClientError, IqIssuePrivateStatsTokenResponse.ServerError {

    /**
     * Tries each {@link IqIssuePrivateStatsTokenResponse} variant in priority order and
     * returns the first that parses cleanly.
     *
     * @apiNote
     * The priority order ({@link Success}, {@link ClientError}, {@link ServerError}) mirrors
     * the order WA Web's reply parser tries.
     *
     * @param node    the inbound IQ stanza received from the relay
     * @param request the original outbound stanza, used to validate echoed identifiers
     * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()}
     *         when no documented variant matched the stanza shape
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxPrivatestatsSignCredentialRPC",
            exports = "sendSignCredentialRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends IqIssuePrivateStatsTokenResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var success = Success.of(node, request);
        if (success.isPresent()) {
            return success;
        }
        var clientError = ClientError.of(node, request);
        if (clientError.isPresent()) {
            return clientError;
        }
        return ServerError.of(node, request);
    }

    /**
     * Reply variant carrying the signed credential, the relay's public key, and the DLEQ
     * proof of correct signing.
     *
     * @apiNote
     * The signed credential, the public key, and both DLEQ coordinates ({@code c},
     * {@code s}) are 32-byte elliptic-curve scalars; the client unblinds the signed
     * credential against the blinding factor it retained from request-build time and
     * verifies the DLEQ proof against the relay's published public key before redeeming the
     * resulting token. The {@code signCredentialT} timestamp records the relay-side
     * wall-clock when the signature was minted.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInPrivatestatsSignCredentialResponseSuccess")
    final class Success implements IqIssuePrivateStatsTokenResponse {
        /**
         * Relay-side mint timestamp in seconds since epoch, read from the
         * {@code <sign_credential t/>} attribute.
         */
        private final long signCredentialT;

        /**
         * Raw 32-byte signed-credential elliptic-curve scalar.
         */
        private final byte[] signedCredential;

        /**
         * Raw 32-byte ACS public key the relay published when signing the credential.
         */
        private final byte[] acsPublicKey;

        /**
         * Raw 32-byte DLEQ proof {@code c} coordinate.
         */
        private final byte[] dleqProofC;

        /**
         * Raw 32-byte DLEQ proof {@code s} coordinate.
         */
        private final byte[] dleqProofS;

        /**
         * Echoed project-name string (UTF-8) carried under the {@code <project_name>}
         * grandchild; equals the request's project name modulo encoding.
         */
        private final String projectName;

        /**
         * Constructs a new successful reply.
         *
         * @apiNote
         * Defensively clones every byte-array argument; the string is immutable and stored
         * directly.
         *
         * @param signCredentialT  relay-side mint timestamp
         * @param signedCredential the 32-byte signed-credential scalar
         * @param acsPublicKey     the 32-byte ACS public key
         * @param dleqProofC       the 32-byte DLEQ proof {@code c} coordinate
         * @param dleqProofS       the 32-byte DLEQ proof {@code s} coordinate
         * @param projectName      the echoed project name
         * @throws NullPointerException if any byte-array argument or {@code projectName} is
         *                              {@code null}
         */
        public Success(long signCredentialT, byte[] signedCredential, byte[] acsPublicKey,
                       byte[] dleqProofC, byte[] dleqProofS, String projectName) {
            this.signCredentialT = signCredentialT;
            this.signedCredential = Objects.requireNonNull(signedCredential, "signedCredential cannot be null").clone();
            this.acsPublicKey = Objects.requireNonNull(acsPublicKey, "acsPublicKey cannot be null").clone();
            this.dleqProofC = Objects.requireNonNull(dleqProofC, "dleqProofC cannot be null").clone();
            this.dleqProofS = Objects.requireNonNull(dleqProofS, "dleqProofS cannot be null").clone();
            this.projectName = Objects.requireNonNull(projectName, "projectName cannot be null");
        }

        /**
         * Returns the relay-side mint timestamp.
         *
         * @return the mint timestamp in seconds since epoch
         */
        public long signCredentialT() {
            return signCredentialT;
        }

        /**
         * Returns a defensive copy of the signed-credential bytes.
         *
         * @return a clone of the signed-credential scalar, never {@code null}
         */
        public byte[] signedCredential() {
            return signedCredential.clone();
        }

        /**
         * Returns a defensive copy of the ACS public-key bytes.
         *
         * @return a clone of the public-key scalar, never {@code null}
         */
        public byte[] acsPublicKey() {
            return acsPublicKey.clone();
        }

        /**
         * Returns a defensive copy of the DLEQ proof {@code c} coordinate.
         *
         * @return a clone of the {@code c} scalar, never {@code null}
         */
        public byte[] dleqProofC() {
            return dleqProofC.clone();
        }

        /**
         * Returns a defensive copy of the DLEQ proof {@code s} coordinate.
         *
         * @return a clone of the {@code s} scalar, never {@code null}
         */
        public byte[] dleqProofS() {
            return dleqProofS.clone();
        }

        /**
         * Returns the echoed project name.
         *
         * @return the project-name string, never {@code null}
         */
        public String projectName() {
            return projectName;
        }

        /**
         * Tries to parse a {@link Success} variant from the given inbound stanza.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when any of the four mandatory scalar fields is
         * missing or not 32 bytes long, when the {@code <sign_credential t/>} timestamp is
         * absent or negative, when the {@code <dleq_proof/>} grandchild is missing, or when
         * the {@code <project_name>} grandchild is absent; the caller falls through to the
         * error variants in any of those cases.
         *
         * @implNote
         * This implementation enforces a strict 32-byte width on every scalar field
         * regardless of the relay's wire encoding; WA Web's parser is silent on the width
         * but the consuming {@code WAACSTokenUtils.unblindToken} requires it.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         {@link Optional#empty()} when the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInPrivatestatsSignCredentialResponseSuccess",
                exports = "parseSignCredentialResponseSuccess", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var signCredential = node.getChild("sign_credential").orElse(null);
            if (signCredential == null) {
                return Optional.empty();
            }
            var t = signCredential.getAttributeAsLong("t", -1L);
            if (t < 0L) {
                return Optional.empty();
            }
            var signedCredentialBytes = signCredential.getChild("signed_credential")
                    .flatMap(Node::toContentBytes)
                    .orElse(null);
            if (signedCredentialBytes == null || signedCredentialBytes.length != 32) {
                return Optional.empty();
            }
            var acsPublicKeyBytes = signCredential.getChild("acs_public_key")
                    .flatMap(Node::toContentBytes)
                    .orElse(null);
            if (acsPublicKeyBytes == null || acsPublicKeyBytes.length != 32) {
                return Optional.empty();
            }
            var dleqProof = signCredential.getChild("dleq_proof").orElse(null);
            if (dleqProof == null) {
                return Optional.empty();
            }
            var dleqCBytes = dleqProof.getChild("c")
                    .flatMap(Node::toContentBytes)
                    .orElse(null);
            if (dleqCBytes == null || dleqCBytes.length != 32) {
                return Optional.empty();
            }
            var dleqSBytes = dleqProof.getChild("s")
                    .flatMap(Node::toContentBytes)
                    .orElse(null);
            if (dleqSBytes == null || dleqSBytes.length != 32) {
                return Optional.empty();
            }
            var projectNameValue = signCredential.getChild("project_name")
                    .flatMap(Node::toContentString)
                    .orElse(null);
            if (projectNameValue == null) {
                return Optional.empty();
            }
            return Optional.of(new Success(t, signedCredentialBytes, acsPublicKeyBytes,
                    dleqCBytes, dleqSBytes, projectNameValue));
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
            var that = (Success) obj;
            return this.signCredentialT == that.signCredentialT
                    && Arrays.equals(this.signedCredential, that.signedCredential)
                    && Arrays.equals(this.acsPublicKey, that.acsPublicKey)
                    && Arrays.equals(this.dleqProofC, that.dleqProofC)
                    && Arrays.equals(this.dleqProofS, that.dleqProofS)
                    && Objects.equals(this.projectName, that.projectName);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(signCredentialT, Arrays.hashCode(signedCredential),
                    Arrays.hashCode(acsPublicKey), Arrays.hashCode(dleqProofC),
                    Arrays.hashCode(dleqProofS), projectName);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "IqIssuePrivateStatsTokenResponse.Success[signCredentialT=" + signCredentialT
                    + ", signedCredentialLength=" + signedCredential.length
                    + ", acsPublicKeyLength=" + acsPublicKey.length
                    + ", dleqProofCLength=" + dleqProofC.length
                    + ", dleqProofSLength=" + dleqProofS.length
                    + ", projectName=" + projectName + ']';
        }
    }

    /**
     * Reply variant signalling that the relay rejected the credential issuance request as
     * malformed or otherwise non-retryable.
     *
     * @apiNote
     * Maps to WA Web's {@code SIGN_CREDENTIAL_RESULT.ERROR_BAD_REQUEST} branch (covering
     * {@code bad-request}, {@code feature-not-implemented},
     * {@code service-unavailable}, {@code decryption-error}, and unknown SMAX statuses);
     * the private-stats driver should not retry the same blinded credential after this.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInPrivatestatsSignCredentialResponseErrorNoRetry")
    final class ClientError implements IqIssuePrivateStatsTokenResponse {
        /**
         * Numeric server-side error code from the {@code <error code/>} attribute.
         */
        private final int errorCode;

        /**
         * Optional human-readable error text from the {@code <error text/>} attribute.
         */
        private final String errorText;

        /**
         * Constructs a new client-error reply.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text, or {@code null} when omitted
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the text, or {@link Optional#empty()} when
         *         the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the given inbound stanza.
         *
         * @apiNote
         * Returns a populated {@link Optional} only when the stanza is a {@code type="error"}
         * envelope echoing the {@code request} id and carrying a {@code <error/>} child whose
         * {@code code} attribute falls in the {@code 4xx} range, per the parsing contract of
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         {@link Optional#empty()} when the stanza does not match the client-error
         *         schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInPrivatestatsSignCredentialResponseErrorNoRetry",
                exports = "parseSignCredentialResponseErrorNoRetry",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ClientError(envelope.code(), envelope.text()));
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
            var that = (ClientError) obj;
            return this.errorCode == that.errorCode && Objects.equals(this.errorText, that.errorText);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "IqIssuePrivateStatsTokenResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * Reply variant signalling that the relay encountered a transient internal failure while
     * issuing the credential; the caller may retry with the same blinded credential after a
     * backoff.
     *
     * @apiNote
     * Maps to WA Web's {@code SIGN_CREDENTIAL_RESULT.ERROR_SERVER} branch (covering
     * {@code internal-server-error}); WA Web's {@code getToken} retries up to three times
     * with the same blinded input on this branch.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInPrivatestatsSignCredentialResponseErrorRetry")
    final class ServerError implements IqIssuePrivateStatsTokenResponse {
        /**
         * Numeric server-side error code from the {@code <error code/>} attribute.
         */
        private final int errorCode;

        /**
         * Optional human-readable error text from the {@code <error text/>} attribute.
         */
        private final String errorText;

        /**
         * Constructs a new server-error reply.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text, or {@code null} when omitted
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the text, or {@link Optional#empty()} when
         *         the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from the given inbound stanza.
         *
         * @apiNote
         * Returns a populated {@link Optional} only when the stanza is a {@code type="error"}
         * envelope echoing the {@code request} id and carrying a {@code <error/>} child whose
         * {@code code} attribute falls outside the {@code 4xx} range, per the parsing
         * contract of {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         {@link Optional#empty()} when the stanza does not match the server-error
         *         schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInPrivatestatsSignCredentialResponseErrorRetry",
                exports = "parseSignCredentialResponseErrorRetry",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ServerError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ServerError(envelope.code(), envelope.text()));
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
            var that = (ServerError) obj;
            return this.errorCode == that.errorCode && Objects.equals(this.errorText, that.errorText);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "IqIssuePrivateStatsTokenResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
