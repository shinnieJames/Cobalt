package com.github.auties00.cobalt.passkey;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientPasskeyAuthenticator;
import com.github.auties00.cobalt.model.device.identity.LocalPasskeyCredential;
import com.github.auties00.cobalt.store.linked.LinkedWhatsAppStore;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Pure-Java WebAuthn authenticator that holds a resident credential and signs assertions in process.
 *
 * <p>Unlike the remote and system strategies, this authenticator owns the credential
 * private key itself (persisted as a {@link LocalPasskeyCredential} in the Signal sub-store) and
 * therefore needs no browser, platform service, or external device. It builds the WebAuthn
 * {@code clientDataJSON} and authenticator data, signs their concatenation, and evaluates the PRF
 * extension from the stored per-credential secret. Because the assertion origin is a string this
 * authenticator chooses, it can present {@code https://web.whatsapp.com} and satisfy the relying
 * party exactly as a genuine browser would.
 *
 * <p>The credential is supplied by the caller, imported from wherever the passkey was enrolled (for
 * example a password-manager passkey provider that exposes the private key) and set on the store's
 * Signal sub-store; Cobalt never enrolls it. The authenticator therefore needs no registration
 * ceremony, only the stored credential to sign with.
 *
 * @implNote This implementation builds authenticator data of exactly
 * {@code SHA-256(rpId) || flags || signCount} (37 bytes), with the flags byte set to user-present and
 * user-verified ({@code 0x05}) and the extension-data flag clear, and returns the PRF result out of
 * band in {@link LinkedWhatsAppClientPasskeyAuthenticator.Assertion#prfOutput()}. This matches what a
 * genuine CTAP2 platform authenticator emits for WhatsApp's {@code prf} request: the hmac-secret
 * output is not embedded in the signed authenticator data, so the relying party receives the PRF value
 * as a separate field rather than inside the signature.
 */
public final class StoredPasskeyAuthenticator implements LinkedWhatsAppClientPasskeyAuthenticator {
    /**
     * The COSE algorithm identifier for ECDSA over P-256 with SHA-256 (ES256).
     */
    private static final int COSE_ES256 = -7;

    /**
     * The COSE algorithm identifier for EdDSA over Ed25519.
     */
    private static final int COSE_EDDSA = -8;

    /**
     * The WebAuthn user-present authenticator-data flag.
     */
    private static final int FLAG_USER_PRESENT = 0x01;

    /**
     * The WebAuthn user-verified authenticator-data flag.
     */
    private static final int FLAG_USER_VERIFIED = 0x04;

    /**
     * The fixed label the WebAuthn PRF extension prepends to the evaluation input before hashing it
     * into the hmac-secret salt.
     */
    private static final byte[] PRF_LABEL = "WebAuthn PRF".getBytes(StandardCharsets.UTF_8);

    /**
     * The store whose Signal sub-store holds the resident credential.
     */
    private final LinkedWhatsAppStore store;

    /**
     * Constructs an authenticator over the given store.
     *
     * @param store the store holding the credential; never {@code null}
     */
    private StoredPasskeyAuthenticator(LinkedWhatsAppStore store) {
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    /**
     * Returns a stored-credential authenticator backed by the credential persisted in the given store.
     *
     * @param store the store holding the passkey credential; never {@code null}
     * @return a stored-credential authenticator over the store's credential
     * @throws NullPointerException if {@code store} is {@code null}
     */
    public static StoredPasskeyAuthenticator of(LinkedWhatsAppStore store) {
        return new StoredPasskeyAuthenticator(store);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation loads the resident credential from the Signal sub-store, rejects
     * the request when the relying party does not match or a non-empty allow list excludes the
     * credential, builds {@code clientDataJSON} with {@code origin} taken verbatim from the request,
     * increments and persists the credential signature counter, and signs
     * {@code authenticatorData || SHA-256(clientDataJSON)} with the credential algorithm.
     */
    @Override
    public Assertion assertCredential(Request request) {
        var credential = store.signalStore()
                .passkeyCredential()
                .orElseThrow(() -> new IllegalStateException("No stored passkey credential is provisioned"));
        var rpId = credential.rpId()
                .orElseThrow(() -> new IllegalStateException("Stored passkey credential has no relying-party id"));
        if (!rpId.equals(request.relyingPartyId())) {
            throw new IllegalStateException("Stored passkey credential is scoped to " + rpId
                    + ", not " + request.relyingPartyId());
        }
        var credentialId = credential.credentialId()
                .orElseThrow(() -> new IllegalStateException("Stored passkey credential has no id"));
        if (!request.allowedCredentialIds().isEmpty()
                && request.allowedCredentialIds().stream().noneMatch(id -> Arrays.equals(id, credentialId))) {
            throw new IllegalStateException("Stored passkey credential is not in the request allow list");
        }

        try {
            var clientDataJson = buildClientDataJson(request.challenge(), request.origin());
            var signCount = credential.signCount() + 1;
            var authenticatorData = buildAuthenticatorData(rpId, signCount);
            var signature = sign(credential, authenticatorData, clientDataJson);
            byte[] prfOutput = null;
            if (request.prfEvalFirst() != null && credential.prfSecret().isPresent()) {
                prfOutput = evaluatePrf(credential.prfSecret().get(), request.prfEvalFirst());
            }
            credential.setSignCount(signCount);
            return new Assertion(credentialId, authenticatorData, clientDataJson, signature,
                    credential.userHandle().orElse(null), prfOutput);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot produce stored passkey assertion", exception);
        }
    }

    /**
     * Builds the WebAuthn {@code clientDataJSON} for a {@code webauthn.get} ceremony.
     *
     * @param challenge the raw server challenge bytes
     * @param origin    the origin to declare
     * @return the UTF-8 encoded {@code clientDataJSON}
     */
    private byte[] buildClientDataJson(byte[] challenge, String origin) {
        var clientData = new JSONObject();
        clientData.put("type", "webauthn.get");
        clientData.put("challenge", Base64.getUrlEncoder().withoutPadding().encodeToString(challenge));
        clientData.put("origin", origin);
        clientData.put("crossOrigin", false);
        return clientData.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Builds the WebAuthn authenticator data for an assertion.
     *
     * <p>The layout is the SHA-256 of the relying-party id (32 bytes), the flags byte (user present
     * and user verified), and the big-endian signature counter (4 bytes). No extensions are embedded.
     *
     * @param rpId      the relying-party id whose hash anchors the data
     * @param signCount the signature counter to embed
     * @return the authenticator data bytes
     * @throws GeneralSecurityException if SHA-256 is unavailable
     */
    private byte[] buildAuthenticatorData(String rpId, int signCount) throws GeneralSecurityException {
        var rpIdHash = MessageDigest.getInstance("SHA-256").digest(rpId.getBytes(StandardCharsets.UTF_8));
        var flags = (byte) (FLAG_USER_PRESENT | FLAG_USER_VERIFIED);
        return ByteBuffer.allocate(rpIdHash.length + 1 + Integer.BYTES)
                .put(rpIdHash)
                .put(flags)
                .putInt(signCount)
                .array();
    }

    /**
     * Signs {@code authenticatorData || SHA-256(clientDataJSON)} with the credential private key.
     *
     * @param credential        the credential carrying the private key and algorithm
     * @param authenticatorData the authenticator data
     * @param clientDataJson    the {@code clientDataJSON}
     * @return the signature bytes (ASN.1 DER for ES256, raw for EdDSA)
     * @throws GeneralSecurityException if the key or algorithm is unsupported or signing fails
     */
    private byte[] sign(LocalPasskeyCredential credential, byte[] authenticatorData, byte[] clientDataJson)
            throws GeneralSecurityException {
        var privateKeyBytes = credential.privateKey()
                .orElseThrow(() -> new IllegalStateException("Stored passkey credential has no private key"));
        var clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientDataJson);
        var signedData = ByteBuffer.allocate(authenticatorData.length + clientDataHash.length)
                .put(authenticatorData)
                .put(clientDataHash)
                .array();

        var keyAlgorithm = switch (credential.algorithm()) {
            case COSE_ES256 -> "EC";
            case COSE_EDDSA -> "Ed25519";
            default -> throw new GeneralSecurityException("Unsupported COSE algorithm " + credential.algorithm());
        };
        var signatureAlgorithm = switch (credential.algorithm()) {
            case COSE_ES256 -> "SHA256withECDSA";
            case COSE_EDDSA -> "Ed25519";
            default -> throw new GeneralSecurityException("Unsupported COSE algorithm " + credential.algorithm());
        };

        var privateKey = parsePrivateKey(keyAlgorithm, privateKeyBytes);
        var signer = Signature.getInstance(signatureAlgorithm);
        signer.initSign(privateKey);
        signer.update(signedData);
        return signer.sign();
    }

    /**
     * Decodes a PKCS8-encoded private key for the given key algorithm.
     *
     * @param keyAlgorithm      the JCE key-factory algorithm ({@code EC} or {@code Ed25519})
     * @param privateKeyBytes the PKCS8 key bytes
     * @return the decoded private key
     * @throws GeneralSecurityException if the key cannot be decoded
     */
    private PrivateKey parsePrivateKey(String keyAlgorithm, byte[] privateKeyBytes) throws GeneralSecurityException {
        return KeyFactory.getInstance(keyAlgorithm).generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
    }

    /**
     * Evaluates the WebAuthn PRF (hmac-secret) extension over the given input.
     *
     * <p>The client-side mapping hashes the fixed label, a separating zero byte, and the evaluation
     * input into a 32-byte salt, then the authenticator returns the HMAC-SHA256 of that salt under
     * the per-credential secret.
     *
     * @param prfSecret    the per-credential PRF secret
     * @param prfEvalFirst the first PRF evaluation input
     * @return the PRF result bytes
     * @throws GeneralSecurityException if HMAC-SHA256 is unavailable
     */
    private byte[] evaluatePrf(byte[] prfSecret, byte[] prfEvalFirst) throws GeneralSecurityException {
        var digest = MessageDigest.getInstance("SHA-256");
        digest.update(PRF_LABEL);
        digest.update((byte) 0x00);
        var salt = digest.digest(prfEvalFirst);
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(prfSecret, "HmacSHA256"));
        return mac.doFinal(salt);
    }
}
