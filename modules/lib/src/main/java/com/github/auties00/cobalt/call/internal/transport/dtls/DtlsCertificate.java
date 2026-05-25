package com.github.auties00.cobalt.call.internal.transport.dtls;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Holds a self-signed X.509 certificate and its matching private key used as the local identity in
 * a DTLS-SRTP handshake (RFC 5764).
 *
 * <p>WebRTC peers do not exchange a CA-rooted certificate chain. Each side advertises the SHA-256
 * fingerprint of its certificate out-of-band (an {@code a=fingerprint:sha-256} SDP line in the
 * signaling offer/answer, or the equivalent field in WhatsApp's call-signaling payload), and once
 * the DTLS handshake completes the peer's presented certificate is compared against the advertised
 * fingerprint rather than against a trust anchor. A {@code DtlsCertificate} is therefore an
 * ephemeral, throwaway identity rather than a long-lived credential: a fresh one is generated per
 * call via {@link #generate()}, and {@link #fingerprintHex()} yields the value to place in the
 * outgoing offer.
 *
 * <p>The certificate is an ECDSA P-256 (secp256r1) leaf, self-signed under {@code SHA256withECDSA},
 * with subject and issuer both {@code "CN=Cobalt"}. The fingerprint is computed once at
 * construction and cached. {@link #fingerprintMatches(byte[], byte[])} performs the post-handshake
 * comparison of a peer's DER bytes against an expected fingerprint.
 *
 * @implNote This implementation backdates {@code notBefore} by one day and sets {@code notAfter}
 * one year ahead so that clock skew between the two endpoints during a call cannot cause a
 * spurious validity-window rejection; the dates are otherwise irrelevant because peers authenticate
 * by fingerprint, not by certificate-path validity.
 */
public final class DtlsCertificate {
    /**
     * One day expressed in milliseconds, subtracted from the issuance instant to set the
     * certificate's {@code notBefore} backstop against clock skew.
     */
    private static final long ONE_DAY_MS = TimeUnit.DAYS.toMillis(1);

    /**
     * One year expressed in milliseconds, added to the issuance instant to set the certificate's
     * {@code notAfter} expiry.
     */
    private static final long ONE_YEAR_MS = TimeUnit.DAYS.toMillis(365);

    /**
     * Hex formatter that emits upper-case digits with a {@code ':'} separator between bytes,
     * producing the textual fingerprint form carried in SDP and call signaling.
     */
    private static final HexFormat HEX_FORMAT = HexFormat.ofDelimiter(":")
            .withUpperCase();

    /**
     * DER-encoded bytes of the X.509 certificate.
     */
    private final byte[] derEncoded;

    /**
     * ECDSA P-256 private key matching the certificate's public key.
     */
    private final PrivateKey privateKey;

    /**
     * SHA-256 digest of {@link #derEncoded}, computed once at construction and reused for
     * {@link #sha256Fingerprint()} and {@link #fingerprintHex()}.
     */
    private final byte[] sha256Fingerprint;

    /**
     * Parsed BouncyCastle certificate holder, retained for handover into the BouncyCastle TLS layer
     * via {@link #bcHolder()}.
     */
    private final X509CertificateHolder holder;

    /**
     * Constructs a certificate from already-encoded DER bytes, the matching private key, and the
     * parsed holder, computing and caching the SHA-256 fingerprint of {@code derEncoded}.
     *
     * @param derEncoded the DER-encoded X.509 certificate bytes
     * @param privateKey the matching ECDSA private key
     * @param holder     the parsed BouncyCastle certificate holder
     * @throws IllegalStateException if the platform does not provide a SHA-256 {@link MessageDigest}
     */
    private DtlsCertificate(byte[] derEncoded, PrivateKey privateKey, X509CertificateHolder holder) {
        this.derEncoded = derEncoded;
        this.privateKey = privateKey;
        this.holder = holder;
        try {
            this.sha256Fingerprint = MessageDigest.getInstance("SHA-256").digest(derEncoded);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Generates a fresh ephemeral ECDSA P-256 self-signed certificate suitable for a single
     * DTLS-SRTP session.
     *
     * <p>A new secp256r1 key pair is generated and wrapped in a self-signed leaf whose subject and
     * issuer are both {@code "CN=Cobalt"}, signed with {@code SHA256withECDSA}, and valid from one
     * day before the current instant until one year after it.
     *
     * @return a fresh {@link DtlsCertificate}
     * @throws IllegalStateException if the platform lacks ECDSA P-256 support, rejects the secp256r1
     *                               parameter spec, or cannot build the signer or certificate
     */
    public static DtlsCertificate generate() {
        try {
            var kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            var kp = kpg.generateKeyPair();
            return buildSelfSigned(kp);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("ECDSA P-256 unavailable", e);
        } catch (OperatorCreationException | IOException | CertificateException e) {
            throw new IllegalStateException("certificate generation failed", e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new IllegalStateException("ECDSA P-256 parameter spec rejected", e);
        }
    }

    /**
     * Builds a self-signed certificate wrapping the supplied EC key pair.
     *
     * <p>Constructs an X.509v3 leaf with subject and issuer {@code "CN=Cobalt"}, a serial derived
     * from the current epoch milliseconds, the standard backdated validity window, and the key
     * pair's public key, then signs it with {@code SHA256withECDSA} using the private key.
     *
     * @param kp the EC key pair to wrap
     * @return a fresh {@link DtlsCertificate}
     * @throws OperatorCreationException if the {@link ContentSigner} cannot be built
     * @throws IOException               if the certificate cannot be DER-encoded
     * @throws CertificateException      if the certificate cannot be constructed
     */
    private static DtlsCertificate buildSelfSigned(KeyPair kp)
            throws OperatorCreationException, IOException, CertificateException {
        var subject = new X500Name("CN=Cobalt");
        var serial = BigInteger.valueOf(System.currentTimeMillis());
        var notBefore = new Date(System.currentTimeMillis() - ONE_DAY_MS);
        var notAfter = new Date(System.currentTimeMillis() + ONE_YEAR_MS);
        var pubKeyInfo = SubjectPublicKeyInfo.getInstance(kp.getPublic().getEncoded());
        var builder = new X509v3CertificateBuilder(subject, serial, notBefore, notAfter, subject, pubKeyInfo);
        var signer = new JcaContentSignerBuilder("SHA256withECDSA").build(kp.getPrivate());
        var holder = builder.build(signer);
        return new DtlsCertificate(holder.getEncoded(), kp.getPrivate(), holder);
    }

    /**
     * Returns a defensive copy of the DER-encoded certificate bytes.
     *
     * @return a fresh copy of the DER bytes
     */
    public byte[] derEncoded() {
        return derEncoded.clone();
    }

    /**
     * Returns the ECDSA P-256 private key matching the certificate.
     *
     * @return the private key
     */
    public PrivateKey privateKey() {
        return privateKey;
    }

    /**
     * Returns a defensive copy of the 32-byte SHA-256 fingerprint of the certificate.
     *
     * @return a fresh copy of the fingerprint bytes
     */
    public byte[] sha256Fingerprint() {
        return sha256Fingerprint.clone();
    }

    /**
     * Returns the certificate's SHA-256 fingerprint as a colon-separated upper-case hex string.
     *
     * <p>This is the textual form placed in the SDP {@code a=fingerprint:sha-256 ...} attribute and
     * in WhatsApp's call-signaling offer, for example:
     *
     * {@snippet :
     * 12:34:AB:CD:EF:...
     * }
     *
     * @return the formatted fingerprint
     */
    public String fingerprintHex() {
        return HEX_FORMAT.formatHex(sha256Fingerprint);
    }

    /**
     * Returns the parsed BouncyCastle certificate holder for handover into the BouncyCastle TLS
     * layer.
     *
     * @return the parsed certificate holder
     */
    X509CertificateHolder bcHolder() {
        return holder;
    }

    /**
     * Verifies a peer's DER-encoded certificate against an expected SHA-256 fingerprint.
     *
     * <p>Computes the SHA-256 of {@code peerDerBytes} and compares it to {@code expectedFingerprint}
     * using a constant-time {@link MessageDigest#isEqual(byte[], byte[])} comparison. The expected
     * value is the fingerprint advertised by the peer through call signaling (the
     * {@code a=fingerprint:sha-256} attribute), so a match authenticates the peer without a trust
     * anchor.
     *
     * @param peerDerBytes        the peer's DER-encoded certificate
     * @param expectedFingerprint the expected 32-byte SHA-256 fingerprint
     * @return {@code true} if the SHA-256 of {@code peerDerBytes} equals {@code expectedFingerprint},
     * {@code false} otherwise
     * @throws NullPointerException  if either argument is {@code null}
     * @throws IllegalStateException if the platform does not provide a SHA-256 {@link MessageDigest}
     */
    public static boolean fingerprintMatches(byte[] peerDerBytes, byte[] expectedFingerprint) {
        Objects.requireNonNull(peerDerBytes, "peerDerBytes cannot be null");
        Objects.requireNonNull(expectedFingerprint, "expectedFingerprint cannot be null");
        try {
            var actual = MessageDigest.getInstance("SHA-256").digest(peerDerBytes);
            return MessageDigest.isEqual(actual, expectedFingerprint);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
