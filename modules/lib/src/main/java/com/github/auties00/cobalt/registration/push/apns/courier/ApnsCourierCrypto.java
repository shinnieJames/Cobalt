package com.github.auties00.cobalt.registration.push.apns.courier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * The crypto helpers consumed exclusively by the APNS courier
 * connection flow.
 *
 * @apiNote
 * Covers four narrow tasks the courier connection performs at the
 * boundary between the persisted session and the on-wire packets:
 * restoring the device RSA keypair from the PKCS#8 / X.509 DER blobs
 * captured in {@link com.github.auties00.cobalt.registration.push.apns.ApnsSession},
 * building and SHA-1-with-RSA signing the connect-time nonce embedded
 * in {@link ApnsPayloadTag#CONNECT}, re-encoding the Apple-signed
 * device certificate into its canonical DER form, and computing the
 * SHA-1 topic hashes the courier expects in
 * {@link ApnsPayloadTag#FILTER} subscription lists and
 * {@link ApnsPayloadTag#GET_TOKEN} requests. Sits in the
 * {@code courier} subpackage so the courier-side crypto and the
 * courier wire model stay together; the complementary
 * activation-side helpers (FairPlay key, CSR generation, RSA keypair
 * generation, activation-info signing) live in
 * {@code apns.activation.ApnsActivationCrypto}.
 *
 * @implNote
 * This implementation hand-rolls only the wire-level glue (nonce
 * layout, signature algorithm tag) and routes every cryptographic
 * primitive through the JCA. The class is a stateless namespace and
 * cannot be instantiated.
 */
public final class ApnsCourierCrypto {
    /**
     * The total length in bytes of the connect-time nonce.
     *
     * @apiNote
     * Layout: one version byte, then an 8-byte big-endian Unix
     * milliseconds timestamp, then 8 random bytes; total 17.
     */
    private static final int NONCE_LENGTH = 17;

    /**
     * The two-byte algorithm tag the courier expects prepended to a
     * nonce signature.
     *
     * @apiNote
     * The pair {@code 0x01 0x01} identifies SHA-1-with-RSA; the
     * courier's signature parser switches on these two bytes to pick
     * a verifier.
     */
    private static final byte[] NONCE_SIGNATURE_TAG = {0x01, 0x01};

    /**
     * Hidden constructor.
     *
     * @apiNote
     * Prevents instantiation; the class is a stateless namespace.
     */
    private ApnsCourierCrypto() {
    }

    /**
     * Restores an RSA {@link KeyPair} from the persisted DER blobs.
     *
     * @apiNote
     * Called once per courier connection from the session-bound
     * fields populated during activation. The public key is read from
     * an {@link X509EncodedKeySpec}, the private key from a
     * {@link PKCS8EncodedKeySpec}.
     *
     * @implNote
     * This implementation collapses any
     * {@link GeneralSecurityException} thrown by the JCA into an
     * {@link IOException} so callers treat key-restore failures as
     * ordinary transport-setup failures.
     *
     * @param publicKeyDer  the {@code SubjectPublicKeyInfo} DER bytes
     * @param privateKeyDer the PKCS#8 DER bytes
     * @return the reconstructed keypair
     * @throws IOException if either DER blob is invalid for an RSA
     *                     key
     */
    public static KeyPair restoreKeyPair(byte[] publicKeyDer, byte[] privateKeyDer) throws IOException {
        try {
            var keyFactory = KeyFactory.getInstance("RSA");
            var pub = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyDer));
            var priv = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyDer));
            return new KeyPair(pub, priv);
        } catch (GeneralSecurityException e) {
            throw new IOException("APNS keypair restore failed", e);
        }
    }

    /**
     * Builds a fresh connect-time nonce.
     *
     * @apiNote
     * Called once per courier handshake and embedded in
     * {@link ApnsPayloadTag#CONNECT} together with the signature from
     * {@link #signNonce(KeyPair, byte[])}.
     *
     * @implNote
     * This implementation lays the nonce out as one version byte
     * ({@code 0x00}; left untouched by {@link ByteBuffer#allocate})
     * followed by an 8-byte big-endian Unix-ms timestamp at offset 1
     * and 8 random bytes at offset 9, for a total of
     * {@value #NONCE_LENGTH} bytes.
     *
     * @param random the source of randomness used for the trailing 8
     *               bytes
     * @return the freshly built nonce
     */
    public static byte[] createNonce(SecureRandom random) {
        var buf = ByteBuffer.allocate(NONCE_LENGTH);
        buf.putLong(1, System.currentTimeMillis());
        var rnd = new byte[8];
        random.nextBytes(rnd);
        buf.put(9, rnd);
        return buf.array();
    }

    /**
     * Signs the connect-time nonce with the device RSA private key.
     *
     * @apiNote
     * The result is the value of the {@code 0x0E} field of the
     * {@link ApnsPayloadTag#CONNECT} packet; the courier verifies it
     * against the public key embedded in the device certificate.
     *
     * @implNote
     * This implementation computes a raw {@code SHA1withRSA}
     * signature and prepends the {@link #NONCE_SIGNATURE_TAG} so the
     * courier's signature parser picks the right verifier. JCA
     * failures are wrapped as {@link IllegalStateException} because
     * they indicate a configuration problem (a JVM without SHA-1 or
     * RSA), not a transient failure the caller can react to.
     *
     * @param keyPair the device keypair holding the signing private
     *                key
     * @param nonce   the nonce bytes returned by
     *                {@link #createNonce(SecureRandom)}
     * @return the {@code 0x01 0x01}-prefixed signature bytes
     * @throws IllegalStateException if the JCA cannot produce a
     *                               {@code SHA1withRSA} signature
     */
    public static byte[] signNonce(KeyPair keyPair, byte[] nonce) {
        try {
            var sig = Signature.getInstance("SHA1withRSA");
            sig.initSign(keyPair.getPrivate());
            sig.update(nonce);
            var raw = sig.sign();
            var out = new byte[raw.length + NONCE_SIGNATURE_TAG.length];
            System.arraycopy(NONCE_SIGNATURE_TAG, 0, out, 0, NONCE_SIGNATURE_TAG.length);
            System.arraycopy(raw, 0, out, NONCE_SIGNATURE_TAG.length, raw.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("nonce signature failed", e);
        }
    }

    /**
     * Re-encodes a device certificate into the canonical DER form
     * the courier expects.
     *
     * @apiNote
     * Called once before the {@link ApnsPayloadTag#CONNECT} packet is
     * sent so the value of the {@code 0x0C} field is byte-for-byte
     * what the courier validates against; tolerates both PEM and DER
     * input because the activation response itself may use either
     * form.
     *
     * @implNote
     * This implementation routes the input through
     * {@link CertificateFactory#generateCertificate} and re-emits
     * {@link X509Certificate#getEncoded()} so any extra envelope
     * (PEM headers, trailing whitespace) is dropped.
     *
     * @param certificateBytes the certificate blob in PEM or DER form
     * @return the canonical DER encoding of the same certificate
     * @throws CertificateException if the input is not a valid X.509
     *                              certificate
     */
    public static byte[] reencodeDeviceCertificate(byte[] certificateBytes) throws CertificateException {
        try (var in = new ByteArrayInputStream(certificateBytes)) {
            var factory = CertificateFactory.getInstance("X.509");
            var cert = (X509Certificate) factory.generateCertificate(in);
            return cert.getEncoded();
        } catch (IOException e) {
            throw new CertificateException(e);
        }
    }

    /**
     * Returns the SHA-1 digest of a string under UTF-8.
     *
     * @apiNote
     * Used to compute the topic hashes the courier indexes
     * subscriptions by; the SHA-1 of a bundle id appears verbatim in
     * the {@link ApnsPayloadTag#FILTER} list and in the
     * {@link ApnsPayloadTag#GET_TOKEN} request.
     *
     * @implNote
     * This implementation surfaces JCA failures as
     * {@link IllegalStateException} because they indicate a JVM
     * without SHA-1, not a transient failure the caller can react to.
     *
     * @param value the source string (typically an iOS bundle id)
     * @return the 20-byte SHA-1 digest of the UTF-8 encoded input
     * @throws IllegalStateException if the JVM does not provide SHA-1
     */
    public static byte[] sha1(String value) {
        try {
            var md = MessageDigest.getInstance("SHA-1");
            return md.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }
}
