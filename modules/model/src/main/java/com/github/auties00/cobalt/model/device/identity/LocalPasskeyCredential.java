package com.github.auties00.cobalt.model.device.identity;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;

/**
 * A WebAuthn resident credential held by a pure-Java local FIDO2 authenticator.
 *
 * <p>WhatsApp's passkey ceremonies (companion linking and the integrity checkpoint) never enroll a
 * credential themselves; they only assert an existing one whose private key normally lives in a
 * platform or roaming authenticator that never exports it. A headless client that wants to satisfy
 * those ceremonies without an external device must therefore act as its own authenticator and hold
 * the private half itself. This structure is that held material: the discoverable credential the
 * local authenticator signs with, scoped to a single relying party.
 *
 * <p>All of the secret components ({@link #privateKey} and {@link #prfSecret}) are confidential: a
 * holder of them can forge a valid assertion for {@link #rpId} bound to any origin the relying party
 * allows, exactly as a stolen platform passkey could. They are persisted alongside the other Signal
 * key material and must be protected to the same degree.
 */
@ProtobufMessage(name = "LocalPasskeyCredential")
public final class LocalPasskeyCredential {
    /**
     * Opaque credential identifier the relying party assigned at registration.
     *
     * <p>Echoed back in every assertion so the relying party can locate the matching public key, and
     * advertised in the authenticator's discoverable-credential set so an allow-list-less assertion
     * request can still resolve to this credential.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] credentialId;

    /**
     * Encoded private key the authenticator signs assertions with.
     *
     * <p>The encoding is the standard PKCS8 form of the key whose type matches {@link #algorithm}
     * (an EC P-256 key for COSE ES256, an Ed25519 key for COSE EdDSA). Confidential.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] privateKey;

    /**
     * Per-credential secret backing the WebAuthn PRF (hmac-secret) extension.
     *
     * <p>The integrity checkpoint evaluates the PRF over a fixed input to derive a deterministic,
     * credential-bound value the relying party can independently verify. A genuine authenticator
     * keeps this secret sealed; the local authenticator stores it here. Confidential, and absent
     * for credentials minted without PRF support.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] prfSecret;

    /**
     * Opaque user handle the relying party bound to the account at registration.
     *
     * <p>Returned in the assertion's {@code userHandle} field for a discoverable credential so the
     * relying party can identify the user without an allow list. Absent when registration did not
     * assign one.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
    byte[] userHandle;

    /**
     * Relying-party identifier this credential is scoped to.
     *
     * <p>For WhatsApp this is {@code whatsapp.com}. The authenticator refuses to assert the
     * credential for any other relying party, and the assertion's authenticator data carries the
     * SHA-256 of this value.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    String rpId;

    /**
     * Monotonic signature counter included in every assertion's authenticator data.
     *
     * <p>Incremented on each successful assertion. Relying parties may use a non-increasing counter
     * as a cloned-authenticator signal, so it is persisted across assertions rather than reset.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.UINT32)
    int signCount;

    /**
     * COSE algorithm identifier of {@link #privateKey}.
     *
     * <p>Selects the signature scheme the authenticator applies over the signed data; {@code -7} is
     * ES256 (ECDSA over P-256 with SHA-256) and {@code -8} is EdDSA (Ed25519). Persisted so the
     * authenticator signs with the same algorithm the credential was registered under.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.SINT32)
    int algorithm;

    /**
     * Constructs a credential with the given properties. Package-private: use the generated
     * {@code LocalPasskeyCredentialBuilder} or the protobuf decoder.
     *
     * @param credentialId the relying-party-assigned credential id, or {@code null}
     * @param privateKey   the PKCS8-encoded signing key, or {@code null}
     * @param prfSecret    the PRF secret, or {@code null} when PRF is unsupported
     * @param userHandle   the relying-party user handle, or {@code null}
     * @param rpId         the relying-party identifier, or {@code null}
     * @param signCount    the current signature counter
     * @param algorithm    the COSE algorithm identifier of {@code privateKey}
     */
    LocalPasskeyCredential(byte[] credentialId, byte[] privateKey, byte[] prfSecret, byte[] userHandle,
                              String rpId, int signCount, int algorithm) {
        this.credentialId = credentialId;
        this.privateKey = privateKey;
        this.prfSecret = prfSecret;
        this.userHandle = userHandle;
        this.rpId = rpId;
        this.signCount = signCount;
        this.algorithm = algorithm;
    }

    /**
     * Returns the relying-party-assigned credential identifier.
     *
     * @return the credential id bytes, or {@link Optional#empty()} when absent
     */
    public Optional<byte[]> credentialId() {
        return Optional.ofNullable(credentialId);
    }

    /**
     * Returns the PKCS8-encoded private signing key.
     *
     * @return the key bytes, or {@link Optional#empty()} when absent
     */
    public Optional<byte[]> privateKey() {
        return Optional.ofNullable(privateKey);
    }

    /**
     * Returns the PRF (hmac-secret) backing secret.
     *
     * @return the PRF secret bytes, or {@link Optional#empty()} when the credential has no PRF
     *         support
     */
    public Optional<byte[]> prfSecret() {
        return Optional.ofNullable(prfSecret);
    }

    /**
     * Returns the relying-party user handle.
     *
     * @return the user handle bytes, or {@link Optional#empty()} when none was assigned
     */
    public Optional<byte[]> userHandle() {
        return Optional.ofNullable(userHandle);
    }

    /**
     * Returns the relying-party identifier this credential is scoped to.
     *
     * @return the relying-party identifier, or {@link Optional#empty()} when absent
     */
    public Optional<String> rpId() {
        return Optional.ofNullable(rpId);
    }

    /**
     * Returns the current signature counter.
     *
     * @return the signature counter
     */
    public int signCount() {
        return signCount;
    }

    /**
     * Returns the COSE algorithm identifier of the private key.
     *
     * @return the COSE algorithm identifier ({@code -7} for ES256, {@code -8} for EdDSA)
     */
    public int algorithm() {
        return algorithm;
    }

    /**
     * Sets the relying-party-assigned credential identifier.
     *
     * @param credentialId the new credential id bytes, or {@code null} to clear
     */
    public void setCredentialId(byte[] credentialId) {
        this.credentialId = credentialId;
    }

    /**
     * Sets the PKCS8-encoded private signing key.
     *
     * @param privateKey the new key bytes, or {@code null} to clear
     */
    public void setPrivateKey(byte[] privateKey) {
        this.privateKey = privateKey;
    }

    /**
     * Sets the PRF backing secret.
     *
     * @param prfSecret the new PRF secret bytes, or {@code null} to clear
     */
    public void setPrfSecret(byte[] prfSecret) {
        this.prfSecret = prfSecret;
    }

    /**
     * Sets the relying-party user handle.
     *
     * @param userHandle the new user handle bytes, or {@code null} to clear
     */
    public void setUserHandle(byte[] userHandle) {
        this.userHandle = userHandle;
    }

    /**
     * Sets the relying-party identifier.
     *
     * @param rpId the new relying-party identifier, or {@code null} to clear
     */
    public void setRpId(String rpId) {
        this.rpId = rpId;
    }

    /**
     * Sets the signature counter.
     *
     * @param signCount the new signature counter
     */
    public void setSignCount(int signCount) {
        this.signCount = signCount;
    }

    /**
     * Sets the COSE algorithm identifier of the private key.
     *
     * @param algorithm the new COSE algorithm identifier
     */
    public void setAlgorithm(int algorithm) {
        this.algorithm = algorithm;
    }
}
