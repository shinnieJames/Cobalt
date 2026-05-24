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
 * A self-signed X.509 certificate plus its private key, used as the
 * local identity in a DTLS-SRTP handshake (RFC 5764).
 *
 * <p>WebRTC peers do not exchange a CA-rooted certificate chain;
 * instead each side advertises its cert's fingerprint via an
 * {@code a=fingerprint} SDP line in the signaling offer/answer, and
 * after the DTLS handshake completes the actual peer certificate is
 * compared against the advertised fingerprint. WhatsApp's wasm engine
 * follows the same pattern.
 *
 * <p>Cobalt generates an ephemeral ECDSA P-256 key + cert per call
 * (a fresh identity for every DTLS session is the WebRTC convention)
 * and exposes the SHA-256 fingerprint for inclusion in the signaling
 * offer.
 */
public final class DtlsCertificate {
    /**
     * Validity window of the generated cert: roughly one day before
     * "now" through one year after, so clock skew across the call
     * doesn't cause spurious validation failures.
     */
    private static final long ONE_DAY_MS = TimeUnit.DAYS.toMillis(1);

    /**
     * One year, in milliseconds — the cert's nominal expiry.
     */
    private static final long ONE_YEAR_MS = TimeUnit.DAYS.toMillis(365);

    /**
     * Upper-case hex format with : as a separator for nibbles
     */
    private static final HexFormat HEX_FORMAT = HexFormat.ofDelimiter(":")
            .withUpperCase();

    /**
     * The DER-encoded X.509 certificate bytes.
     */
    private final byte[] derEncoded;

    /**
     * The matching private key (ECDSA P-256).
     */
    private final PrivateKey privateKey;

    /**
     * The SHA-256 fingerprint of {@link #derEncoded}, computed once
     * on construction.
     */
    private final byte[] sha256Fingerprint;

    /**
     * The parsed X.509 cert holder, kept around for handover into
     * BouncyCastle's TLS layer.
     */
    private final X509CertificateHolder holder;

    /**
     * Constructs a certificate from already-encoded DER bytes and the
     * matching private key.
     *
     * @param derEncoded  the DER-encoded X.509 cert
     * @param privateKey  the matching private key
     * @param holder      the parsed BC cert holder
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
     * Generates a fresh ephemeral ECDSA P-256 self-signed certificate
     * suitable for one DTLS-SRTP session. The subject and issuer are
     * both {@code "CN=Cobalt"}; the validity is one day before now
     * through one year ahead.
     *
     * @return a fresh {@link DtlsCertificate}
     * @throws IllegalStateException if the platform lacks ECDSA P-256
     *                               or signing capabilities
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
     * Builds a self-signed cert wrapping the supplied keypair.
     *
     * @param kp the EC keypair to wrap
     * @return a fresh {@link DtlsCertificate}
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
     * @return the DER bytes
     */
    public byte[] derEncoded() {
        return derEncoded.clone();
    }

    /**
     * Returns the matching private key.
     *
     * @return the EC private key
     */
    public PrivateKey privateKey() {
        return privateKey;
    }

    /**
     * Returns a defensive copy of the 32-byte SHA-256 fingerprint of
     * the certificate.
     *
     * @return the fingerprint bytes
     */
    public byte[] sha256Fingerprint() {
        return sha256Fingerprint.clone();
    }

    /**
     * Returns the cert's SHA-256 fingerprint formatted as a
     * colon-separated upper-case hex string, the form used in the
     * SDP {@code a=fingerprint:sha-256 ...} attribute and in
     * WhatsApp's signaling offer.
     *
     * @return the formatted fingerprint (e.g.
     *         {@code "12:34:AB:CD:..."})
     */
    public String fingerprintHex() {
        return HEX_FORMAT.formatHex(sha256Fingerprint);
    }

    /**
     * Returns the BouncyCastle cert holder for handover into the
     * BC TLS layer.
     *
     * @return the parsed cert holder
     */
    X509CertificateHolder bcHolder() {
        return holder;
    }

    /**
     * Verifies a peer's DER-encoded certificate against an expected
     * SHA-256 fingerprint (from the SDP {@code a=fingerprint:sha-256}
     * advertisement).
     *
     * @param peerDerBytes        the peer's DER-encoded cert
     * @param expectedFingerprint the expected 32-byte SHA-256
     *                            fingerprint
     * @return {@code true} iff the actual SHA-256 of the peer cert
     *         matches {@code expectedFingerprint}
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
