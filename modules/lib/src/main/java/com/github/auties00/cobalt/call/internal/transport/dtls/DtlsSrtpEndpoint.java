package com.github.auties00.cobalt.call.internal.transport.dtls;

import com.github.auties00.cobalt.call.internal.rtp.srtp.SrtpEndpoint;
import com.github.auties00.cobalt.call.internal.rtp.srtp.SrtpRole;
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
 * Drives a DTLS-SRTP handshake (RFC 5764) and exposes the resulting
 * 60-byte keying material as an {@link SrtpEndpoint} ready for SRTP
 * packet protect/unprotect.
 *
 * <p>Usage:
 *
 * <pre>{@code
 *   var localCert = DtlsCertificate.generate();
 *   // exchange localCert.fingerprintHex() with peer via signaling;
 *   // receive peer's fingerprint from signaling
 *   var endpoint = DtlsSrtpEndpoint.client(localCert, peerFingerprint, transport);
 *   try (var srtp = endpoint.handshake()) {
 *       // use srtp for media
 *   }
 * }</pre>
 *
 * <p>Pure Java via BouncyCastle's {@code org.bouncycastle.tls} —
 * which is the only mainstream Java TLS stack that exposes both the
 * {@code use_srtp} extension and the
 * {@code "EXTRACTOR-dtls_srtp"} keying-material export needed for
 * WebRTC. The standard JSSE provides DTLS but not these
 * WebRTC-specific bits.
 *
 * <p>Cipher suite: the WebRTC default
 * {@code SRTP_AES128_CM_HMAC_SHA1_80} is the only profile advertised,
 * matching WhatsApp's wasm engine.
 */
public final class DtlsSrtpEndpoint {
    /**
     * Length, in bytes, of the keying material exported with the
     * RFC 5764 label {@code "EXTRACTOR-dtls_srtp"} for the
     * AES-128-CM-HMAC-SHA1-80 profile: {@code 2 * (16 + 14)}.
     */
    public static final int KEYING_MATERIAL_LENGTH = SrtpEndpoint.KEYING_MATERIAL_LENGTH;

    /**
     * RFC 5705 / 5764 label for SRTP master-key extraction.
     */
    private static final String EXTRACTOR_LABEL = "EXTRACTOR-dtls_srtp";

    /**
     * Cipher suites both sides advertise, restricted to
     * ECDHE-ECDSA-only since Cobalt always uses an ECDSA P-256
     * identity. Restricting here avoids the BC server falling back
     * to an RSA suite (which would call our unimplemented RSA-signer
     * credential method and trigger an internal_error alert).
     */
    private static final int[] CIPHER_SUITES = new int[] {
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
    };

    /**
     * Our DTLS role — determines which side of the handshake we run.
     */
    private final SrtpRole role;

    /**
     * Local cert + private key.
     */
    private final DtlsCertificate localCert;

    /**
     * Expected SHA-256 fingerprint of the peer's certificate (32
     * bytes), exchanged out-of-band via signaling.
     */
    private final byte[] expectedPeerFingerprint;

    /**
     * Underlying transport — typically a UDP socket adapter.
     */
    private final DatagramTransport transport;

    /**
     * Constructs a new endpoint.
     *
     * @param role                    our DTLS role
     * @param localCert               our local cert + private key
     * @param expectedPeerFingerprint the peer's SHA-256 fingerprint
     *                                from signaling
     * @param transport               the datagram transport (UDP
     *                                adapter)
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
     * @param localCert               our local cert + private key
     * @param expectedPeerFingerprint the peer's SHA-256 fingerprint
     *                                (32 bytes)
     * @param transport               the underlying datagram
     *                                transport
     * @return a new {@code DtlsSrtpEndpoint} configured as the DTLS
     *         client
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if
     *                                  {@code expectedPeerFingerprint}
     *                                  is not exactly 32 bytes
     */
    public static DtlsSrtpEndpoint client(DtlsCertificate localCert, byte[] expectedPeerFingerprint,
                                          DatagramTransport transport) {
        return create(SrtpRole.CLIENT, localCert, expectedPeerFingerprint, transport);
    }

    /**
     * Creates a DTLS server endpoint.
     *
     * @param localCert               our local cert + private key
     * @param expectedPeerFingerprint the peer's SHA-256 fingerprint
     *                                (32 bytes)
     * @param transport               the underlying datagram
     *                                transport
     * @return a new {@code DtlsSrtpEndpoint} configured as the DTLS
     *         server
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if
     *                                  {@code expectedPeerFingerprint}
     *                                  is not exactly 32 bytes
     */
    public static DtlsSrtpEndpoint server(DtlsCertificate localCert, byte[] expectedPeerFingerprint,
                                          DatagramTransport transport) {
        return create(SrtpRole.SERVER, localCert, expectedPeerFingerprint, transport);
    }

    /**
     * Validates inputs and constructs an endpoint. Shared by
     * {@link #client} and {@link #server}.
     *
     * @param role                    the DTLS role
     * @param localCert               our local cert
     * @param expectedPeerFingerprint the expected peer fingerprint
     * @param transport               the datagram transport
     * @return a new endpoint
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
     * Result of a completed DTLS handshake: an {@link SrtpEndpoint}
     * primed with the exported keying material plus the BouncyCastle
     * {@link DTLSTransport} that wraps the application-data layer.
     *
     * <p>The {@code SrtpEndpoint} is used directly for media-plane
     * traffic; the {@code DTLSTransport} carries the in-band
     * application-data payloads (SCTP packets in the WebRTC
     * data-channel layering).
     *
     * @param srtp      the negotiated SRTP endpoint
     * @param dtls      the BC DTLS application-data transport — call
     *                  {@code send(byte[], int, int)} to write an
     *                  encrypted record, {@code receive(...)} to read
     *                  the next decrypted one
     */
    public record HandshakeResult(SrtpEndpoint srtp, DTLSTransport dtls) {
    }

    /**
     * Drives the DTLS handshake to completion. Validates the peer's
     * cert fingerprint against the signaling-exchanged value, exports
     * the SRTP keying material, and returns an {@link SrtpEndpoint}
     * primed with it.
     *
     * @return a fresh {@link SrtpEndpoint} keyed on the negotiated
     *         master keys
     * @throws IOException        if the handshake fails (peer hung
     *                            up, transport error, fingerprint
     *                            mismatch, alert received)
     * @throws WhatsAppCallException.DtlsHandshake if the negotiated SRTP profile
     *                                does not match the requested one
     */
    public SrtpEndpoint handshake() throws IOException {
        return handshakeWithDtls().srtp();
    }

    /**
     * Drives the handshake and returns both halves — the SRTP
     * endpoint AND the application-data DTLS transport.
     *
     * @return a {@link HandshakeResult} carrying both
     * @throws IOException if the handshake fails
     */
    public HandshakeResult handshakeWithDtls() throws IOException {
        var crypto = new JcaTlsCryptoProvider().create(new SecureRandom());
        return switch (role) {
            case CLIENT -> handshakeClient(crypto);
            case SERVER -> handshakeServer(crypto);
        };
    }

    /**
     * Drives the client side of the DTLS handshake.
     *
     * @param crypto the BC TLS crypto provider
     * @return a fully-populated {@link HandshakeResult}
     */
    private HandshakeResult handshakeClient(JcaTlsCrypto crypto) throws IOException {
        var client = new CobaltDtlsClient(crypto, localCert, expectedPeerFingerprint);
        var dtls = new DTLSClientProtocol().connect(client, transport);
        var srtp = SrtpEndpoint.fromDtlsKeyingMaterial(client.exportedKeyingMaterial(), role);
        return new HandshakeResult(srtp, dtls);
    }

    /**
     * Drives the server side of the DTLS handshake.
     *
     * @param crypto the BC TLS crypto provider
     * @return a fully-populated {@link HandshakeResult}
     */
    private HandshakeResult handshakeServer(JcaTlsCrypto crypto) throws IOException {
        var server = new CobaltDtlsServer(crypto, localCert, expectedPeerFingerprint);
        var dtls = new DTLSServerProtocol().accept(server, transport);
        var srtp = SrtpEndpoint.fromDtlsKeyingMaterial(server.exportedKeyingMaterial(), role);
        return new HandshakeResult(srtp, dtls);
    }

    /**
     * Builds the use_srtp extension content advertising
     * {@code SRTP_AES128_CM_HMAC_SHA1_80}.
     *
     * @return a populated {@link UseSRTPData}
     */
    private static UseSRTPData useSrtp() {
        return new UseSRTPData(new int[] { SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_80 }, null);
    }

    /**
     * Returns the {@link DTLSTransport} produced by the DTLS layer
     * for a given protocol driver. (Currently unused — handshake-only
     * mode releases the underlying transport once the handshake
     * completes; future revisions may expose this for handshake +
     * data-phase orchestration.)
     *
     * @param transport the wrapper around the negotiated DTLS session
     * @return the wrapped transport
     */
    static DTLSTransport unwrap(DTLSTransport transport) {
        return transport;
    }

    /**
     * BouncyCastle {@link DefaultTlsClient} subclass: provides the
     * Cobalt cert, advertises the {@code use_srtp} extension, and
     * validates the peer cert via fingerprint.
     */
    private static final class CobaltDtlsClient extends DefaultTlsClient {
        /**
         * Local cert + private key.
         */
        private final DtlsCertificate localCert;

        /**
         * Expected peer fingerprint.
         */
        private final byte[] expectedPeerFingerprint;

        /**
         * Constructs a new client.
         *
         * @param crypto                the BC TLS crypto provider
         * @param localCert             our local cert
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
         * <p>Restricts the protocol version range to DTLS 1.2 (the
         * version every WebRTC implementation supports).
         */
        @Override
        protected ProtocolVersion[] getSupportedVersions() {
            return new ProtocolVersion[] { ProtocolVersion.DTLSv12 };
        }

        /**
         * {@inheritDoc}
         *
         * <p>Restricts cipher suites to ECDHE-ECDSA only (Cobalt
         * uses an ECDSA P-256 identity).
         */
        @Override
        protected int[] getSupportedCipherSuites() {
            return CIPHER_SUITES.clone();
        }

        /**
         * {@inheritDoc}
         *
         * <p>Adds the {@code use_srtp} extension advertising
         * {@code SRTP_AES128_CM_HMAC_SHA1_80}.
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
         * Captured 60 bytes of SRTP master-key material exported
         * during {@link #notifyHandshakeComplete}, since BC zeros
         * the export source once that callback returns.
         */
        private byte[] exported;

        /**
         * {@inheritDoc}
         *
         * <p>Returns a {@link TlsAuthentication} that validates the
         * peer's cert against the signaling-exchanged SHA-256
         * fingerprint and offers our local cert if the server
         * requests client auth.
         */
        @Override
        public TlsAuthentication getAuthentication() {
            return new FingerprintAuthentication(
                    (JcaTlsCrypto) getCrypto(), context, localCert, expectedPeerFingerprint);
        }

        /**
         * {@inheritDoc}
         *
         * <p>Captures the SRTP keying material before the underlying
         * BC export source is zeroed.
         */
        @Override
        public void notifyHandshakeComplete() throws IOException {
            super.notifyHandshakeComplete();
            this.exported = context.exportKeyingMaterial(
                    EXTRACTOR_LABEL, null, KEYING_MATERIAL_LENGTH);
        }

        /**
         * Returns the keying material captured during the handshake.
         *
         * @return the 60-byte SRTP master-key block
         */
        byte[] exportedKeyingMaterial() {
            return exported;
        }
    }

    /**
     * BouncyCastle {@link DefaultTlsServer} subclass: mirror of
     * {@link CobaltDtlsClient} for the server side.
     */
    private static final class CobaltDtlsServer extends DefaultTlsServer {
        /**
         * Local cert + private key.
         */
        private final DtlsCertificate localCert;

        /**
         * Expected peer fingerprint.
         */
        private final byte[] expectedPeerFingerprint;

        /**
         * Constructs a new server.
         *
         * @param crypto                  the BC TLS crypto provider
         * @param localCert               our local cert
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
         * <p>Restricts the protocol version range to DTLS 1.2.
         */
        @Override
        protected ProtocolVersion[] getSupportedVersions() {
            return new ProtocolVersion[] { ProtocolVersion.DTLSv12 };
        }

        /**
         * {@inheritDoc}
         *
         * <p>Restricts cipher suites to ECDHE-ECDSA only.
         */
        @Override
        protected int[] getSupportedCipherSuites() {
            return CIPHER_SUITES.clone();
        }

        /**
         * {@inheritDoc}
         *
         * <p>Adds the {@code use_srtp} extension on the server-hello
         * side, confirming
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
         * <p>Requires client authentication so we can validate the
         * peer's cert via the signaling-exchanged fingerprint.
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
         * <p>Validates the peer cert against the expected SHA-256
         * fingerprint exchanged via signaling.
         */
        @Override
        public void notifyClientCertificate(Certificate clientCertificate) throws IOException {
            validatePeerFingerprint(clientCertificate, expectedPeerFingerprint);
        }

        /**
         * Captured 60 bytes of SRTP master-key material exported
         * during {@link #notifyHandshakeComplete}.
         */
        private byte[] exported;

        /**
         * {@inheritDoc}
         *
         * <p>Provides our ECDSA cert + private key as the server's
         * credentials.
         */
        @Override
        protected TlsCredentialedSigner getECDSASignerCredentials() {
            return ecdsaCredentials((JcaTlsCrypto) getCrypto(), context, localCert);
        }

        /**
         * {@inheritDoc}
         *
         * <p>Captures the SRTP keying material before the underlying
         * BC export source is zeroed.
         */
        @Override
        public void notifyHandshakeComplete() throws IOException {
            super.notifyHandshakeComplete();
            this.exported = context.exportKeyingMaterial(
                    EXTRACTOR_LABEL, null, KEYING_MATERIAL_LENGTH);
        }

        /**
         * Returns the keying material captured during the handshake.
         *
         * @return the 60-byte SRTP master-key block
         */
        byte[] exportedKeyingMaterial() {
            return exported;
        }
    }

    /**
     * Validates a peer-supplied {@link Certificate} against the
     * expected SHA-256 fingerprint. The first cert in the chain is
     * the leaf; we compare its DER-encoded form's SHA-256 to the
     * expected value.
     *
     * @param peer                    the peer cert chain
     * @param expectedPeerFingerprint the expected fingerprint bytes
     * @throws IOException if the chain is empty or the fingerprint
     *                     does not match
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
     * Builds an ECDSA-flavored {@link TlsCredentialedSigner} wrapping
     * Cobalt's local cert + private key.
     *
     * @param crypto    the BC TLS crypto provider
     * @param context   the TLS context the credentials will run in
     * @param localCert the local cert to wrap
     * @return a credentialed signer suitable for client- or
     *         server-side authentication
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
     * The client-side {@link TlsAuthentication} that validates the
     * server's cert via the signaling-exchanged fingerprint and
     * provides client credentials when the server requests them.
     */
    private static final class FingerprintAuthentication implements TlsAuthentication {
        /**
         * The BC TLS crypto provider.
         */
        private final JcaTlsCrypto crypto;

        /**
         * The TLS context (needed by credentialed-signer
         * construction).
         */
        private final TlsContext context;

        /**
         * Local cert + private key.
         */
        private final DtlsCertificate localCert;

        /**
         * Expected peer fingerprint.
         */
        private final byte[] expectedPeerFingerprint;

        /**
         * Constructs a new authentication.
         *
         * @param crypto                  the BC TLS crypto provider
         * @param context                 the TLS context
         * @param localCert               our local cert
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
         * <p>Validates the server's cert against the expected
         * fingerprint.
         */
        @Override
        public void notifyServerCertificate(TlsServerCertificate serverCertificate) throws IOException {
            validatePeerFingerprint(serverCertificate.getCertificate(), expectedPeerFingerprint);
        }

        /**
         * {@inheritDoc}
         *
         * <p>Provides our local cert + private key when the server
         * requests client authentication.
         */
        @Override
        public TlsCredentials getClientCredentials(CertificateRequest certificateRequest) {
            return ecdsaCredentials(crypto, context, localCert);
        }
    }
}
