package com.github.auties00.cobalt.call.transport.dtls;

import com.github.auties00.cobalt.call.rtp.srtp.SrtpEndpoint;
import com.github.auties00.cobalt.call.rtp.srtp.SrtpRole;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.CertificateRequest;
import org.bouncycastle.tls.CipherSuite;
import org.bouncycastle.tls.ClientCertificateType;
import org.bouncycastle.tls.DTLSClientProtocol;
import org.bouncycastle.tls.DTLSServerProtocol;
import org.bouncycastle.tls.DTLSTransport;
import org.bouncycastle.tls.DatagramTransport;
import org.bouncycastle.tls.DefaultTlsClient;
import org.bouncycastle.tls.DefaultTlsServer;
import org.bouncycastle.tls.HashAlgorithm;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.SRTPProtectionProfile;
import org.bouncycastle.tls.SignatureAlgorithm;
import org.bouncycastle.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.tls.TlsAuthentication;
import org.bouncycastle.tls.TlsContext;
import org.bouncycastle.tls.TlsCredentials;
import org.bouncycastle.tls.TlsCredentialedSigner;
import org.bouncycastle.tls.TlsSRTPUtils;
import org.bouncycastle.tls.TlsServerCertificate;
import org.bouncycastle.tls.UseSRTPData;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.TlsCryptoParameters;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaDefaultTlsCredentialedSigner;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCertificate;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCrypto;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCryptoProvider;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;
import java.util.Vector;

/**
 * Drives one side of a DTLS-SRTP handshake (RFC 5764) and exposes the exported keying material as an
 * {@link SrtpEndpoint} ready for SRTP packet protect and unprotect.
 *
 * <p>An endpoint is created as a client via {@link #client(DtlsCertificate, byte[], DatagramTransport)}
 * or as a server via {@link #server(DtlsCertificate, byte[], DatagramTransport)}, then driven to
 * completion with {@link #handshake()} or {@link #handshakeWithDtls()}. The handshake authenticates
 * the peer by comparing its presented certificate against the SHA-256 fingerprint exchanged
 * out-of-band through call signaling, negotiates the {@code use_srtp} extension, and exports the
 * SRTP master-key block under the RFC 5705/5764 label {@code "EXTRACTOR-dtls_srtp"}. Typical use:
 *
 * {@snippet :
 * var localCert = DtlsCertificate.generate();
 * // exchange localCert.fingerprintHex() with the peer via signaling, and
 * // receive the peer's fingerprint the same way
 * var endpoint = DtlsSrtpEndpoint.client(localCert, peerFingerprint, transport);
 * try (var srtp = endpoint.handshake()) {
 *     // use srtp for media
 * }
 * }
 *
 * <p>Only {@code SRTP_AES128_CM_HMAC_SHA1_80} is advertised in the {@code use_srtp} extension, the
 * WebRTC default that matches WhatsApp's call engine.
 *
 * @implNote This implementation uses BouncyCastle's {@code org.bouncycastle.tls} stack rather than
 * JSSE because it is the only mainstream Java TLS provider that exposes both the {@code use_srtp}
 * extension and the {@code "EXTRACTOR-dtls_srtp"} keying-material export required by WebRTC; JSSE
 * offers DTLS but neither WebRTC-specific facility.
 */
public final class DtlsSrtpEndpoint {
    /**
     * Length, in bytes, of the keying material exported under the RFC 5764 label
     * {@code "EXTRACTOR-dtls_srtp"} for the AES-128-CM-HMAC-SHA1-80 profile.
     *
     * @implNote This implementation reuses {@link SrtpEndpoint#KEYING_MATERIAL_LENGTH}, which is
     * {@code 2 * (16 + 14) = 60}: two 16-byte master keys plus two 14-byte master salts, one pair
     * per direction.
     */
    public static final int KEYING_MATERIAL_LENGTH = SrtpEndpoint.KEYING_MATERIAL_LENGTH;

    /**
     * RFC 5705/5764 exporter label under which the SRTP master keys are extracted from the
     * negotiated DTLS session.
     */
    private static final String EXTRACTOR_LABEL = "EXTRACTOR-dtls_srtp";

    /**
     * Cipher suites advertised by both sides, restricted to ECDHE-ECDSA.
     *
     * @implNote This implementation lists only ECDHE-ECDSA suites because Cobalt always uses an
     * ECDSA P-256 identity. Restricting them here prevents the BouncyCastle server from selecting an
     * RSA suite, which would dispatch to the unimplemented RSA-signer credential method and raise an
     * {@code internal_error} alert.
     */
    private static final int[] CIPHER_SUITES = new int[] {
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
    };

    /**
     * DTLS role, selecting which side of the handshake this endpoint runs.
     */
    private final SrtpRole role;

    /**
     * Local certificate and private key offered as this endpoint's identity.
     */
    private final DtlsCertificate localCert;

    /**
     * Expected 32-byte SHA-256 fingerprint of the peer's certificate, exchanged out-of-band via
     * signaling.
     */
    private final byte[] expectedPeerFingerprint;

    /**
     * Datagram transport carrying the handshake, normally a UDP socket adapter.
     */
    private final DatagramTransport transport;

    /**
     * Constructs an endpoint with the given role, identity, expected peer fingerprint, and
     * transport.
     *
     * @param role                    the DTLS role
     * @param localCert               the local certificate and private key
     * @param expectedPeerFingerprint the peer's SHA-256 fingerprint from signaling
     * @param transport               the datagram transport
     */
    private DtlsSrtpEndpoint(SrtpRole role, DtlsCertificate localCert,
                             byte[] expectedPeerFingerprint, DatagramTransport transport) {
        this.role = role;
        this.localCert = localCert;
        this.expectedPeerFingerprint = expectedPeerFingerprint;
        this.transport = transport;
    }

    /**
     * Creates a DTLS client endpoint.
     *
     * @param localCert               the local certificate and private key
     * @param expectedPeerFingerprint the peer's 32-byte SHA-256 fingerprint
     * @param transport               the underlying datagram transport
     * @return a new endpoint configured as the DTLS client
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code expectedPeerFingerprint} is not exactly 32 bytes
     */
    public static DtlsSrtpEndpoint client(DtlsCertificate localCert, byte[] expectedPeerFingerprint,
                                          DatagramTransport transport) {
        return create(SrtpRole.CLIENT, localCert, expectedPeerFingerprint, transport);
    }

    /**
     * Creates a DTLS server endpoint.
     *
     * @param localCert               the local certificate and private key
     * @param expectedPeerFingerprint the peer's 32-byte SHA-256 fingerprint
     * @param transport               the underlying datagram transport
     * @return a new endpoint configured as the DTLS server
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code expectedPeerFingerprint} is not exactly 32 bytes
     */
    public static DtlsSrtpEndpoint server(DtlsCertificate localCert, byte[] expectedPeerFingerprint,
                                          DatagramTransport transport) {
        return create(SrtpRole.SERVER, localCert, expectedPeerFingerprint, transport);
    }

    /**
     * Validates the inputs and constructs an endpoint, shared by {@link #client} and
     * {@link #server}.
     *
     * <p>Defensively clones {@code expectedPeerFingerprint} into the returned endpoint after
     * checking that it is exactly 32 bytes.
     *
     * @param role                    the DTLS role
     * @param localCert               the local certificate and private key
     * @param expectedPeerFingerprint the expected peer fingerprint
     * @param transport               the datagram transport
     * @return a new endpoint
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code expectedPeerFingerprint} is not exactly 32 bytes
     */
    private static DtlsSrtpEndpoint create(SrtpRole role, DtlsCertificate localCert,
                                           byte[] expectedPeerFingerprint, DatagramTransport transport) {
        Objects.requireNonNull(role, "role cannot be null");
        Objects.requireNonNull(localCert, "localCert cannot be null");
        Objects.requireNonNull(expectedPeerFingerprint, "expectedPeerFingerprint cannot be null");
        Objects.requireNonNull(transport, "transport cannot be null");
        if (expectedPeerFingerprint.length != 32) {
            throw new IllegalArgumentException(
                    "expectedPeerFingerprint must be 32 bytes (SHA-256), got "
                            + expectedPeerFingerprint.length);
        }
        return new DtlsSrtpEndpoint(role, localCert, expectedPeerFingerprint.clone(), transport);
    }

    /**
     * Holds the result of a completed DTLS handshake: the SRTP endpoint primed with the exported
     * keying material and the BouncyCastle {@link DTLSTransport} wrapping the application-data layer.
     *
     * <p>The {@link SrtpEndpoint} is used directly for media-plane traffic; the {@link DTLSTransport}
     * carries in-band application-data payloads (SCTP packets in the WebRTC data-channel layering),
     * with {@code send(byte[], int, int)} writing one encrypted record and {@code receive(...)}
     * reading the next decrypted one.
     *
     * @param srtp the negotiated SRTP endpoint
     * @param dtls the BouncyCastle application-data DTLS transport
     */
    public record HandshakeResult(SrtpEndpoint srtp, DTLSTransport dtls) {
    }

    /**
     * Drives the DTLS handshake to completion and returns the negotiated {@link SrtpEndpoint}.
     *
     * <p>Validates the peer's certificate fingerprint against the signaling-exchanged value,
     * exports the SRTP keying material, and primes a fresh {@link SrtpEndpoint} with it. This is the
     * SRTP-only convenience over {@link #handshakeWithDtls()}, discarding the application-data
     * transport.
     *
     * @return a fresh {@link SrtpEndpoint} keyed on the negotiated master keys
     * @throws IOException                          if the handshake fails because the peer hung up,
     *                                              the transport errored, or an alert was received
     * @throws WhatsAppCallException.DtlsHandshake  if the peer presents no certificate or its
     *                                              fingerprint does not match the expected one
     */
    public SrtpEndpoint handshake() throws IOException {
        return handshakeWithDtls().srtp();
    }

    /**
     * Drives the handshake to completion and returns both the SRTP endpoint and the application-data
     * DTLS transport.
     *
     * <p>Creates a fresh {@link JcaTlsCrypto} seeded from a {@link SecureRandom} and dispatches to
     * the client or server handshake according to the configured role.
     *
     * @return a {@link HandshakeResult} carrying the negotiated endpoint and transport
     * @throws IOException                          if the handshake fails
     * @throws WhatsAppCallException.DtlsHandshake  if peer fingerprint validation fails
     */
    public HandshakeResult handshakeWithDtls() throws IOException {
        var crypto = new JcaTlsCryptoProvider().create(new SecureRandom());
        return switch (role) {
            case CLIENT -> handshakeClient(crypto);
            case SERVER -> handshakeServer(crypto);
        };
    }

    /**
     * Drives the client side of the DTLS handshake and primes the SRTP endpoint from the exported
     * keying material.
     *
     * @param crypto the BouncyCastle TLS crypto provider
     * @return a fully populated {@link HandshakeResult}
     * @throws IOException if the handshake fails
     */
    private HandshakeResult handshakeClient(JcaTlsCrypto crypto) throws IOException {
        var client = new CobaltDtlsClient(crypto, localCert, expectedPeerFingerprint);
        var dtls = new DTLSClientProtocol().connect(client, transport);
        var srtp = SrtpEndpoint.fromDtlsKeyingMaterial(client.exportedKeyingMaterial(), role);
        return new HandshakeResult(srtp, dtls);
    }

    /**
     * Drives the server side of the DTLS handshake and primes the SRTP endpoint from the exported
     * keying material.
     *
     * @param crypto the BouncyCastle TLS crypto provider
     * @return a fully populated {@link HandshakeResult}
     * @throws IOException if the handshake fails
     */
    private HandshakeResult handshakeServer(JcaTlsCrypto crypto) throws IOException {
        var server = new CobaltDtlsServer(crypto, localCert, expectedPeerFingerprint);
        var dtls = new DTLSServerProtocol().accept(server, transport);
        var srtp = SrtpEndpoint.fromDtlsKeyingMaterial(server.exportedKeyingMaterial(), role);
        return new HandshakeResult(srtp, dtls);
    }

    /**
     * Builds the {@code use_srtp} extension content advertising the single profile
     * {@code SRTP_AES128_CM_HMAC_SHA1_80} with an empty MKI.
     *
     * @return a populated {@link UseSRTPData}
     */
    private static UseSRTPData useSrtp() {
        return new UseSRTPData(new int[] { SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_80 }, null);
    }

    /**
     * Returns the given {@link DTLSTransport} unchanged.
     *
     * @param transport the wrapper around the negotiated DTLS session
     * @return the same transport
     */
    static DTLSTransport unwrap(DTLSTransport transport) {
        return transport;
    }

    /**
     * BouncyCastle {@link DefaultTlsClient} that offers the Cobalt certificate, advertises the
     * {@code use_srtp} extension, and authenticates the peer by fingerprint.
     */
    private static final class CobaltDtlsClient extends DefaultTlsClient {
        /**
         * Local certificate and private key offered as the client identity.
         */
        private final DtlsCertificate localCert;

        /**
         * Expected 32-byte SHA-256 fingerprint of the server's certificate.
         */
        private final byte[] expectedPeerFingerprint;

        /**
         * Captured 60-byte SRTP master-key block exported during {@link #notifyHandshakeComplete()}.
         *
         * @implNote This implementation captures the export inside the completion callback because
         * BouncyCastle zeros the export source once the callback returns, leaving it unavailable to
         * a later {@link #exportedKeyingMaterial()} call.
         */
        private byte[] exported;

        /**
         * Constructs a client with the given crypto provider, local certificate, and expected peer
         * fingerprint.
         *
         * @param crypto                  the BouncyCastle TLS crypto provider
         * @param localCert               the local certificate and private key
         * @param expectedPeerFingerprint the expected peer fingerprint
         */
        CobaltDtlsClient(JcaTlsCrypto crypto, DtlsCertificate localCert, byte[] expectedPeerFingerprint) {
            super(crypto);
            this.localCert = localCert;
            this.expectedPeerFingerprint = expectedPeerFingerprint;
        }

        /**
         * {@inheritDoc}
         *
         * <p>Restricts the supported protocol versions to DTLS 1.2, the version every WebRTC
         * implementation supports.
         */
        @Override
        protected ProtocolVersion[] getSupportedVersions() {
            return new ProtocolVersion[] { ProtocolVersion.DTLSv12 };
        }

        /**
         * {@inheritDoc}
         *
         * <p>Restricts the supported cipher suites to the ECDHE-ECDSA set, matching Cobalt's ECDSA
         * P-256 identity.
         */
        @Override
        protected int[] getSupportedCipherSuites() {
            return CIPHER_SUITES.clone();
        }

        /**
         * {@inheritDoc}
         *
         * <p>Adds the {@code use_srtp} extension advertising {@code SRTP_AES128_CM_HMAC_SHA1_80} to
         * the client extensions.
         */
        @Override
        public Hashtable<?, ?> getClientExtensions() throws IOException {
            @SuppressWarnings("unchecked")
            var ext = (Hashtable<Integer, byte[]>) super.getClientExtensions();
            if (ext == null) {
                ext = new Hashtable<>();
            }
            TlsSRTPUtils.addUseSRTPExtension(ext, useSrtp());
            return ext;
        }

        /**
         * {@inheritDoc}
         *
         * <p>Returns a {@link FingerprintAuthentication} that validates the server's certificate
         * against the signaling-exchanged SHA-256 fingerprint and offers the local certificate when
         * the server requests client authentication.
         */
        @Override
        public TlsAuthentication getAuthentication() {
            return new FingerprintAuthentication(
                    (JcaTlsCrypto) getCrypto(), context, localCert, expectedPeerFingerprint);
        }

        /**
         * {@inheritDoc}
         *
         * <p>Exports and captures the SRTP keying material before the underlying BouncyCastle export
         * source is zeroed.
         */
        @Override
        public void notifyHandshakeComplete() throws IOException {
            super.notifyHandshakeComplete();
            this.exported = context.exportKeyingMaterial(
                    EXTRACTOR_LABEL, null, KEYING_MATERIAL_LENGTH);
        }

        /**
         * Returns the SRTP master-key block captured during the handshake.
         *
         * @return the 60-byte SRTP master-key block, or {@code null} if the handshake has not
         * completed
         */
        byte[] exportedKeyingMaterial() {
            return exported;
        }
    }

    /**
     * BouncyCastle {@link DefaultTlsServer} mirroring {@link CobaltDtlsClient} for the server side:
     * it offers the Cobalt certificate, requests and authenticates a client certificate by
     * fingerprint, and confirms the {@code use_srtp} extension.
     */
    private static final class CobaltDtlsServer extends DefaultTlsServer {
        /**
         * Local certificate and private key offered as the server identity.
         */
        private final DtlsCertificate localCert;

        /**
         * Expected 32-byte SHA-256 fingerprint of the client's certificate.
         */
        private final byte[] expectedPeerFingerprint;

        /**
         * Captured 60-byte SRTP master-key block exported during {@link #notifyHandshakeComplete()}.
         *
         * @implNote This implementation captures the export inside the completion callback because
         * BouncyCastle zeros the export source once the callback returns.
         */
        private byte[] exported;

        /**
         * Constructs a server with the given crypto provider, local certificate, and expected peer
         * fingerprint.
         *
         * @param crypto                  the BouncyCastle TLS crypto provider
         * @param localCert               the local certificate and private key
         * @param expectedPeerFingerprint the expected peer fingerprint
         */
        CobaltDtlsServer(JcaTlsCrypto crypto, DtlsCertificate localCert, byte[] expectedPeerFingerprint) {
            super(crypto);
            this.localCert = localCert;
            this.expectedPeerFingerprint = expectedPeerFingerprint;
        }

        /**
         * {@inheritDoc}
         *
         * <p>Restricts the supported protocol versions to DTLS 1.2.
         */
        @Override
        protected ProtocolVersion[] getSupportedVersions() {
            return new ProtocolVersion[] { ProtocolVersion.DTLSv12 };
        }

        /**
         * {@inheritDoc}
         *
         * <p>Restricts the supported cipher suites to the ECDHE-ECDSA set.
         */
        @Override
        protected int[] getSupportedCipherSuites() {
            return CIPHER_SUITES.clone();
        }

        /**
         * {@inheritDoc}
         *
         * <p>Adds the {@code use_srtp} extension on the server-hello side, confirming
         * {@code SRTP_AES128_CM_HMAC_SHA1_80}.
         */
        @Override
        public Hashtable<?, ?> getServerExtensions() throws IOException {
            @SuppressWarnings("unchecked")
            var ext = (Hashtable<Integer, byte[]>) super.getServerExtensions();
            if (ext == null) {
                ext = new Hashtable<>();
            }
            TlsSRTPUtils.addUseSRTPExtension(ext, useSrtp());
            return ext;
        }

        /**
         * {@inheritDoc}
         *
         * <p>Requests an ECDSA client certificate signed under SHA-256 so the peer can be
         * authenticated by the signaling-exchanged fingerprint.
         */
        @Override
        public CertificateRequest getCertificateRequest() {
            return new CertificateRequest(
                    new short[] { ClientCertificateType.ecdsa_sign },
                    new Vector<>(List.of(
                            new SignatureAndHashAlgorithm(HashAlgorithm.sha256, SignatureAlgorithm.ecdsa))),
                    null);
        }

        /**
         * {@inheritDoc}
         *
         * <p>Validates the client's certificate against the expected SHA-256 fingerprint exchanged
         * via signaling.
         *
         * @throws IOException if the chain is empty or the fingerprint does not match
         */
        @Override
        public void notifyClientCertificate(Certificate clientCertificate) throws IOException {
            validatePeerFingerprint(clientCertificate, expectedPeerFingerprint);
        }

        /**
         * {@inheritDoc}
         *
         * <p>Provides the local ECDSA certificate and private key as the server's signing
         * credentials.
         */
        @Override
        protected TlsCredentialedSigner getECDSASignerCredentials() {
            return ecdsaCredentials((JcaTlsCrypto) getCrypto(), context, localCert);
        }

        /**
         * {@inheritDoc}
         *
         * <p>Exports and captures the SRTP keying material before the underlying BouncyCastle export
         * source is zeroed.
         */
        @Override
        public void notifyHandshakeComplete() throws IOException {
            super.notifyHandshakeComplete();
            this.exported = context.exportKeyingMaterial(
                    EXTRACTOR_LABEL, null, KEYING_MATERIAL_LENGTH);
        }

        /**
         * Returns the SRTP master-key block captured during the handshake.
         *
         * @return the 60-byte SRTP master-key block, or {@code null} if the handshake has not
         * completed
         */
        byte[] exportedKeyingMaterial() {
            return exported;
        }
    }

    /**
     * Validates a peer-supplied {@link Certificate} chain against an expected SHA-256 fingerprint.
     *
     * <p>Treats the first entry in the chain as the leaf and compares the SHA-256 of its DER-encoded
     * form against {@code expectedPeerFingerprint} via
     * {@link DtlsCertificate#fingerprintMatches(byte[], byte[])}.
     *
     * @param peer                    the peer certificate chain
     * @param expectedPeerFingerprint the expected fingerprint bytes
     * @throws WhatsAppCallException.DtlsHandshake if the chain is empty or the fingerprint does not
     *                                             match
     * @throws IOException                         if the leaf certificate cannot be DER-encoded
     */
    private static void validatePeerFingerprint(Certificate peer, byte[] expectedPeerFingerprint) throws IOException {
        if (peer == null || peer.isEmpty()) {
            throw new WhatsAppCallException.DtlsHandshake("peer presented no certificate");
        }
        var derBytes = peer.getCertificateAt(0).getEncoded();
        if (!DtlsCertificate.fingerprintMatches(derBytes, expectedPeerFingerprint)) {
            throw new WhatsAppCallException.DtlsHandshake("peer certificate fingerprint did not match expected");
        }
    }

    /**
     * Builds an ECDSA-flavoured {@link TlsCredentialedSigner} wrapping the local certificate and
     * private key.
     *
     * <p>Wraps {@code localCert} in a BouncyCastle {@link Certificate} and binds it to the private
     * key under an SHA-256 ECDSA signature algorithm, suitable for either client- or server-side
     * authentication.
     *
     * @param crypto    the BouncyCastle TLS crypto provider
     * @param context   the TLS context the credentials run in
     * @param localCert the local certificate to wrap
     * @return a credentialed signer
     * @throws IllegalStateException if the local certificate cannot be wrapped for the BouncyCastle
     *                               TLS layer
     */
    private static TlsCredentialedSigner ecdsaCredentials(JcaTlsCrypto crypto, TlsContext context,
                                                          DtlsCertificate localCert) {
        try {
            var bcCert = new Certificate(new TlsCertificate[]{
                    new JcaTlsCertificate(crypto, localCert.bcHolder().getEncoded())
            });
            return new JcaDefaultTlsCredentialedSigner(
                    new TlsCryptoParameters(context),
                    crypto,
                    localCert.privateKey(),
                    bcCert,
                    new SignatureAndHashAlgorithm(HashAlgorithm.sha256, SignatureAlgorithm.ecdsa));
        } catch (IOException e) {
            throw new IllegalStateException("failed to wrap local cert for BouncyCastle TLS", e);
        }
    }

    /**
     * Client-side {@link TlsAuthentication} that authenticates the server by fingerprint and
     * provides client credentials on request.
     */
    private static final class FingerprintAuthentication implements TlsAuthentication {
        /**
         * BouncyCastle TLS crypto provider used to build credentialed signers.
         */
        private final JcaTlsCrypto crypto;

        /**
         * TLS context required by credentialed-signer construction.
         */
        private final TlsContext context;

        /**
         * Local certificate and private key offered when the server requests client authentication.
         */
        private final DtlsCertificate localCert;

        /**
         * Expected 32-byte SHA-256 fingerprint of the server's certificate.
         */
        private final byte[] expectedPeerFingerprint;

        /**
         * Constructs an authentication bound to the given crypto provider, context, local
         * certificate, and expected peer fingerprint.
         *
         * @param crypto                  the BouncyCastle TLS crypto provider
         * @param context                 the TLS context
         * @param localCert               the local certificate and private key
         * @param expectedPeerFingerprint the expected peer fingerprint
         */
        FingerprintAuthentication(JcaTlsCrypto crypto, TlsContext context,
                                  DtlsCertificate localCert, byte[] expectedPeerFingerprint) {
            this.crypto = crypto;
            this.context = context;
            this.localCert = localCert;
            this.expectedPeerFingerprint = expectedPeerFingerprint;
        }

        /**
         * {@inheritDoc}
         *
         * <p>Validates the server's certificate against the expected fingerprint.
         *
         * @throws IOException if the chain is empty or the fingerprint does not match
         */
        @Override
        public void notifyServerCertificate(TlsServerCertificate serverCertificate) throws IOException {
            validatePeerFingerprint(serverCertificate.getCertificate(), expectedPeerFingerprint);
        }

        /**
         * {@inheritDoc}
         *
         * <p>Provides the local certificate and private key as ECDSA credentials when the server
         * requests client authentication.
         */
        @Override
        public TlsCredentials getClientCredentials(CertificateRequest certificateRequest) {
            return ecdsaCredentials(crypto, context, localCert);
        }
    }
}
