package com.github.auties00.cobalt.socket;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.client.proxy.WhatsAppProxy;
import com.github.auties00.cobalt.client.WhatsAppWebClientHistory;
import com.github.auties00.cobalt.exception.WhatsAppSessionException;
import com.github.auties00.cobalt.exception.WhatsAppStreamException;
import com.github.auties00.cobalt.model.device.DevicePlatformType;
import com.github.auties00.cobalt.model.device.DevicePropsBuilder;
import com.github.auties00.cobalt.model.device.DevicePropsHistorySyncConfigBuilder;
import com.github.auties00.cobalt.model.device.DevicePropsSpec;
import com.github.auties00.cobalt.model.device.pairing.*;
import com.github.auties00.cobalt.model.device.pairing.ClientPayload.DevicePairingRegistrationData;
import com.github.auties00.cobalt.model.device.pairing.ClientPayload.UserAgent;
import com.github.auties00.cobalt.model.signal.CertChainSpec;
import com.github.auties00.cobalt.model.signal.NoiseCertificateCertChainDetailsSpec;
import com.github.auties00.cobalt.model.signal.NoiseCertificateDetailsSpec;
import com.github.auties00.cobalt.model.signal.NoiseCertificateSpec;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.binary.NodeReader;
import com.github.auties00.cobalt.node.binary.NodeWriter;
import com.github.auties00.cobalt.node.binary.NodeTokens;
import com.github.auties00.cobalt.socket.datagram.WhatsAppDatagramInputStream;
import com.github.auties00.cobalt.socket.datagram.WhatsAppDatagramOutputStream;
import com.github.auties00.cobalt.socket.tunnel.HttpTunnel;
import com.github.auties00.cobalt.socket.tunnel.SocksTunnel;
import com.github.auties00.cobalt.socket.websocket.WebSocketFrameInputStream;
import com.github.auties00.cobalt.socket.websocket.WebSocketFrameOutputStream;
import com.github.auties00.cobalt.socket.websocket.WebSocketUpgrade;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.util.DataUtils;
import com.github.auties00.curve25519.Curve25519;
import com.github.auties00.libsignal.key.SignalIdentityKeyPair;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;

import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLSocket;
import javax.security.auth.DestroyFailedException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Top-level WhatsApp socket client that performs the Noise XX handshake
 * and exchanges int24-prefixed encrypted datagrams with the WhatsApp
 * server.
 *
 * <p>The class is sealed into {@link Web} (with nested {@code Browser}
 * and {@code Desktop} variants) and {@link Mobile} subtypes that pick
 * the right transport for each client form factor. Browser and Windows
 * hybrid companions tunnel everything through a hand-rolled WebSocket
 * over a {@link SSLSocket}; the macOS desktop runs Noise directly over
 * a TLS-wrapped {@link Socket}; the native mobile clients run Noise
 * over a plain {@link Socket}.
 *
 * <p>Every subtype builds the same logical I/O pipeline as a chain of
 * standard {@code Filter*Stream} filters:
 * <pre>
 * Browser : raw Socket -&gt; SSLSocket -&gt; WebSocketFrame{In,Out}putStream -&gt; WhatsAppDatagram{In,Out}putStream
 * Desktop : raw Socket -&gt; SSLSocket -&gt; WhatsAppDatagram{In,Out}putStream
 * Mobile  : raw Socket -&gt; WhatsAppDatagram{In,Out}putStream
 * </pre>
 *
 * <p>During the Noise XX handshake the datagram streams are in their
 * pre-handshake passthrough mode (no AES-GCM) and the handshake messages
 * are framed directly through {@link #writeHandshakeMessage(byte[], byte[])}
 * and {@link #readHandshakeMessage()}, which each subtype implements
 * against its own underlying transport (one WebSocket frame per
 * handshake message on Browser, one raw write on Desktop and Mobile).
 * Once the handshake derives the read and write keys they are installed
 * on the datagram streams via
 * {@link WhatsAppDatagramInputStream#setReadKey(javax.crypto.SecretKey)}
 * and {@link WhatsAppDatagramOutputStream#setWriteKey(javax.crypto.SecretKey)},
 * and every subsequent {@link #sendNode(Node)} is encoded straight into
 * the wire by {@link NodeWriter#toStream(OutputStream)}; the reader
 * thread mirrors the same chain by decoding nodes from the datagram
 * input stream with {@link NodeReader#of(InputStream)}.
 *
 * <p>HTTP {@code CONNECT} and SOCKS4/4a/5/5h proxies are supported on
 * every form factor through {@link HttpTunnel} and {@link SocksTunnel};
 * {@link WhatsAppProxy.Http.Secure} additionally TLS-wraps the proxy
 * hop before sending {@code CONNECT}.
 */
@WhatsAppWebModule(moduleName = "WANoiseSocket")
@WhatsAppWebModule(moduleName = "WAFrameSocket")
public sealed abstract class WhatsAppSocketClient {

    /**
     * 32-byte Ed25519 public key of the WhatsApp Noise root CA.
     *
     * <p>Acts as the trust anchor for the two-level certificate chain
     * (root then intermediate then leaf) that the server presents
     * during the Noise handshake. The intermediate must have
     * {@code issuerSerial == 0} and be signed by this key; the leaf
     * must be signed by the intermediate and its embedded key must
     * match the server static key carried in the Noise hello.
     */
    private static final byte[] NOISE_ROOT_CA_PUBLIC_KEY = HexFormat.of().parseHex(
            "142375574d0a587166aae71ebe516437c4a28b73e3695c6ce1f7f9545da8ee6b"
    );

    /**
     * Two-byte WhatsApp protocol identifier ({@code "WA"}) prepended to
     * every handshake prologue.
     */
    private static final byte[] WHATSAPP_VERSION_HEADER = "WA".getBytes(StandardCharsets.UTF_8);

    /**
     * Number of bytes in the int24 length prefix that frames every
     * datagram on the wire.
     */
    private static final int INT24_BYTE_SIZE = 3;

    /**
     * Defensive upper bound on a single Noise handshake message.
     */
    private static final int MAX_HANDSHAKE_MESSAGE_LENGTH = 0xFFFF;

    /**
     * Connect timeout in milliseconds for the raw TCP open.
     */
    private static final int CONNECT_TIMEOUT_MILLIS = 30_000;

    /**
     * Builds a WhatsApp socket client tailored to the platform recorded
     * in {@code store}.
     *
     * <p>Web and Windows companions get a WebSocket-over-TLS transport;
     * the macOS desktop gets a TLS-wrapped {@link SSLSocket}; the native
     * mobile clients get a plain {@link Socket}. The TLS configuration
     * is the Chrome-style factory by default, which matches the JA3
     * fingerprint WhatsApp expects.
     *
     * @param store the WhatsApp store carrying the platform, identity
     *              keys and proxy configuration
     * @return a socket client ready to {@link #connect(WhatsAppSocketListener)}
     */
    public static WhatsAppSocketClient newCipheredSocketClient(WhatsAppStore store) {
        return newCipheredSocketClient(store, WhatsAppSslContextFactory.chrome());
    }

    /**
     * Variant of {@link #newCipheredSocketClient(WhatsAppStore)} that
     * accepts a custom {@link WhatsAppSslContextFactory}.
     *
     * @param store               the WhatsApp store
     * @param sslContextFactory   factory used for the end-to-end TLS
     *                            (the WebSocket on Web/Windows, the raw
     *                            {@link SSLSocket} on macOS); also used
     *                            for the optional TLS hop to a secure
     *                            HTTP proxy on every form factor
     * @return a socket client wired with the supplied SSL context
     *         factory
     */
    public static WhatsAppSocketClient newCipheredSocketClient(WhatsAppStore store, WhatsAppSslContextFactory sslContextFactory) {
        Objects.requireNonNull(store, "store cannot be null");
        Objects.requireNonNull(sslContextFactory, "sslContextFactory cannot be null");
        var platform = store.device().platform();
        return switch (platform) {
            case WEB, WINDOWS -> new Web.Browser(store, sslContextFactory);
            case MACOS -> new Web.Desktop(store, sslContextFactory);
            default -> new Mobile(store, sslContextFactory);
        };
    }

    /**
     * The WhatsApp store, owning the identity keys and platform metadata
     * required to assemble the handshake payload.
     */
    final WhatsAppStore store;

    /**
     * SSL configuration applied to every TLS hop on this connection,
     * including the optional TLS-to-proxy hop performed by
     * {@link #openTunneledSocket(InetSocketAddress)} and the end-to-end
     * hop on {@link Web.Browser} / {@link Web.Desktop}. The same factory
     * is used for both hops so a caller-supplied
     * {@link WhatsAppSslContextFactory} (typically a trust-all factory
     * in tests, or a custom-truststore factory for enterprise CA pinning)
     * applies consistently.
     */
    final WhatsAppSslContextFactory sslContextFactory;

    /**
     * Listener that receives deserialized nodes, errors and the close
     * event, set on every {@link #connect(WhatsAppSocketListener)}.
     */
    private WhatsAppSocketListener listener;

    /**
     * Virtual thread that drains int24-framed datagrams off the
     * transport after the Noise handshake completes.
     */
    private volatile Thread readerThread;

    /**
     * Sticky flag set by {@link #disconnect()} so the reader loop can
     * distinguish an orderly local close from a server-side drop.
     */
    private volatile boolean closed;

    /**
     * Datagram input stream that strips the int24 prefix and decrypts
     * inbound AES-GCM datagrams; lives on top of the subtype-specific
     * transport stream chain built in {@link #openTransport()}.
     * Subtypes assign this field directly inside {@code openTransport}.
     */
    protected WhatsAppDatagramInputStream in;

    /**
     * Datagram output stream that encrypts outbound plaintext with
     * AES-GCM and writes the int24-prefixed ciphertext; lives on top of
     * the subtype-specific transport stream chain built in
     * {@link #openTransport()}. Subtypes assign this field directly
     * inside {@code openTransport}.
     */
    protected WhatsAppDatagramOutputStream out;

    /**
     * AES write key derived from the Noise handshake; kept here so the
     * package-private test accessor {@link #writeKey()} can verify the
     * handshake completed and so {@link #disconnect()} can destroy the
     * key material eagerly.
     */
    @WhatsAppWebExport(moduleName = "WANoiseSocket", exports = "NoiseSocket", adaptation = WhatsAppAdaptation.ADAPTED)
    private volatile SecretKeySpec writeKey;

    /**
     * AES read key derived from the Noise handshake; kept here for the
     * same reasons as {@link #writeKey}.
     */
    @WhatsAppWebExport(moduleName = "WANoiseSocket", exports = "NoiseSocket", adaptation = WhatsAppAdaptation.ADAPTED)
    private volatile SecretKeySpec readKey;

    /**
     * Mutex that serialises concurrent {@link #sendNode(Node)} callers
     * so the datagram output stream observes one complete node-encode
     * per acquired monitor, with no interleaving across senders.
     */
    private final Object writeLock = new Object();

    /**
     * Wall-clock duration of the transport-open phase, captured between
     * the entry of {@link #connect(WhatsAppSocketListener)} and the
     * point at which the transport reports as open.
     *
     * <p>Mirrors WA Web's {@code socket_open} QPL span and the
     * {@code WebcSocketConnectWamEvent.webcSocketConnectDuration} field.
     */
    private volatile Duration socketConnectDuration;

    /**
     * Wall-clock duration of the Noise XX handshake, captured from the
     * first byte of {@code ClientHello} until the read and write keys
     * have been derived.
     *
     * <p>Mirrors WA Web's {@code auth_handshake} QPL span and the
     * {@code WebcSocketConnectWamEvent.webcAuthHandshakeDuration} field.
     */
    private volatile Duration authHandshakeDuration;

    /**
     * Common constructor invoked by every subtype.
     *
     * @param store             the WhatsApp store
     * @param sslContextFactory the SSL configuration applied to every
     *                          TLS hop on this connection
     */
    private WhatsAppSocketClient(WhatsAppStore store, WhatsAppSslContextFactory sslContextFactory) {
        this.store = store;
        this.sslContextFactory = sslContextFactory;
    }

    /**
     * Opens the underlying connection and performs the Noise XX
     * handshake, dispatching decoded nodes to {@code listener}.
     *
     * @param listener the callback that receives decoded nodes, errors
     *                 and the close event
     * @throws IOException if the connection or handshake fails
     */
    @WhatsAppWebExport(moduleName = "WANoiseSocket", exports = "NoiseSocket", adaptation = WhatsAppAdaptation.ADAPTED)
    public final void connect(WhatsAppSocketListener listener) throws IOException {
        Objects.requireNonNull(listener, "listener cannot be null");
        this.listener = listener;
        this.closed = false;

        var socketOpenStart = Instant.now();
        openTransport();
        this.socketConnectDuration = Duration.between(socketOpenStart, Instant.now());

        performNoiseHandshake();
        startReaderThread();
    }

    /**
     * Opens the platform-specific transport and assigns the
     * {@link #in} and {@link #out} fields with the freshly-built
     * datagram stream pair on top of it.
     *
     * @throws IOException if the transport cannot be opened
     */
    abstract void openTransport() throws IOException;

    /**
     * Closes the transport opened by {@link #openTransport()}.
     *
     * @throws IOException if the transport cannot be closed cleanly
     */
    abstract void closeTransport() throws IOException;

    /**
     * Returns whether the transport opened by {@link #openTransport()}
     * is currently open.
     *
     * @return {@code true} if connected, {@code false} otherwise
     */
    abstract boolean isTransportOpen();

    /**
     * Verifies the server certificate carried in the Noise handshake.
     *
     * <p>Web and Desktop companions receive a two-level chain
     * (intermediate plus leaf) and call into the chain verifier; Mobile
     * receives a flat {@code NoiseCertificate}. Each subtype overrides
     * this method with the matching verification.
     *
     * @param decryptedCertificate the decrypted certificate payload
     * @param serverStaticKey      the 32-byte server static public key
     *                             extracted from the Noise hello
     * @throws IOException if the certificate is malformed or invalid
     */
    abstract void verifyCertificateChain(byte[] decryptedCertificate, byte[] serverStaticKey) throws IOException;

    /**
     * Returns the handshake prologue for the current client type.
     *
     * @return the prologue bytes
     */
    abstract byte[] handshakePrologue();

    /**
     * Builds the serialized client payload sent inside the
     * {@code ClientFinish} message.
     *
     * @return the serialized client payload
     */
    abstract byte[] handshakePayload();

    /**
     * Returns the measured transport-open duration for the current
     * session.
     *
     * @return the duration, or {@code null} if the transport has not
     *         finished opening yet
     */
    public final Duration socketConnectDuration() {
        return socketConnectDuration;
    }

    /**
     * Returns the measured Noise handshake duration for the current
     * session.
     *
     * @return the duration, or {@code null} if the handshake has not
     *         finished yet
     */
    public final Duration authHandshakeDuration() {
        return authHandshakeDuration;
    }

    /**
     * Returns the AES write key derived by the Noise XX handshake.
     *
     * <p>Package-private hook for in-process tests that assert the
     * handshake completed; not exposed publicly because the key is
     * sensitive material destroyed by {@link #disconnect()}.
     *
     * @return the write key, or {@code null} if the handshake has not
     *         completed or has been torn down
     */
    final SecretKeySpec writeKey() {
        return writeKey;
    }

    /**
     * Returns the AES read key derived by the Noise XX handshake.
     *
     * <p>Package-private hook for in-process tests that assert the
     * handshake completed; not exposed publicly because the key is
     * sensitive material destroyed by {@link #disconnect()}.
     *
     * @return the read key, or {@code null} if the handshake has not
     *         completed or has been torn down
     */
    final SecretKeySpec readKey() {
        return readKey;
    }

    /**
     * Disconnects from the server and destroys the AES read and write
     * keys.
     *
     * <p>WA Web relies on the browser's garbage collector to dispose of
     * the keys; Cobalt destroys them eagerly via
     * {@link SecretKeySpec#destroy()} so the secrets do not linger in
     * heap memory beyond the lifetime of the connection.
     */
    @WhatsAppWebExport(moduleName = "WANoiseSocket", exports = "NoiseSocket", adaptation = WhatsAppAdaptation.ADAPTED)
    public final void disconnect() {
        this.closed = true;

        if (readKey != null) {
            try {
                readKey.destroy();
            } catch (DestroyFailedException _) {
            }
            readKey = null;
        }

        if (writeKey != null) {
            try {
                writeKey.destroy();
            } catch (DestroyFailedException _) {
            }
            writeKey = null;
        }

        try {
            closeTransport();
        } catch (IOException _) {
        }
    }

    /**
     * Returns whether the underlying connection is currently open.
     *
     * @return {@code true} if connected, {@code false} otherwise
     */
    public final boolean isConnected() {
        return !closed && isTransportOpen();
    }

    /**
     * Serialises a {@link Node} and sends it as one encrypted datagram.
     *
     * <p>The encoding is streamed straight through the datagram output
     * stream's cipher into the wire: no intermediate buffer is held by
     * Cobalt beyond the cipher's fixed-size chunk buffer.
     * {@link #writeLock} serialises concurrent senders so each datagram
     * is one atomic operation from {@link WhatsAppDatagramOutputStream#beginMessage(int)}
     * through the closing {@link OutputStream#flush()}.
     *
     * @param node the node to send
     * @throws IOException if serialisation or the underlying write fails
     */
    @WhatsAppWebExport(moduleName = "WAFrameSocket", exports = "FrameSocket", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WANoiseSocket", exports = "NoiseSocket", adaptation = WhatsAppAdaptation.ADAPTED)
    public final void sendNode(Node node) throws IOException {
        Objects.requireNonNull(node, "node cannot be null");
        synchronized (writeLock) {
            try (var encoder = NodeWriter.toStream(out)) {
                encoder.writeNode(node);
            }
        }
    }

    /**
     * Runs the full Noise XX handshake against the connected server.
     *
     * <p>Sends the {@code ClientHello}, processes the server's
     * {@code ServerHello}, verifies the certificate chain via
     * {@link #verifyCertificateChain(byte[], byte[])}, sends the
     * {@code ClientFinish} and finally splits the derived 64-byte key
     * material into the read and write AES keys, installing them on the
     * datagram streams so subsequent traffic is enciphered.
     *
     * @throws IOException if the handshake fails or any of its
     *         underlying I/O operations does
     */
    private void performNoiseHandshake() throws IOException {
        var handshakeStart = Instant.now();
        var ephemeralKeyPair = SignalIdentityKeyPair.random();
        var prologue = handshakePrologue();

        try (var handshake = new WhatsAppSocketHandshake(in, out, prologue)) {
            // Send client hello
            var clientHelloMessage = new HandshakeMessageBuilder()
                    .clientHello(new HandshakeMessageClientHelloBuilder()
                            .ephemeral(ephemeralKeyPair.publicKey().toEncodedPoint())
                            .build())
                    .build();
            handshake.updateHash(ephemeralKeyPair.publicKey().toEncodedPoint());
            handshake.writeClientHandshake(clientHelloMessage);

            // Read server hello
            var serverHello = handshake.readServerHandshake()
                    .serverHello()
                    .orElseThrow(() -> new IOException("Missing server hello"));

            var ephemeral = serverHello.ephemeral()
                    .orElseThrow(() -> new IOException("Missing server ephemeral key"));
            handshake.updateHash(ephemeral);

            var sharedEphemeral = Curve25519.sharedKey(
                    ephemeralKeyPair.privateKey().toEncodedPoint(), ephemeral
            );
            handshake.mixIntoKey(sharedEphemeral);

            var staticText = serverHello._static()
                    .orElseThrow(() -> new IOException("Missing server static key"));
            var decodedStaticText = handshake.cipher(staticText, false);

            var sharedStatic = Curve25519.sharedKey(
                    ephemeralKeyPair.privateKey().toEncodedPoint(), decodedStaticText
            );
            handshake.mixIntoKey(sharedStatic);

            var payload = serverHello.payload()
                    .orElseThrow(() -> new IOException("Missing server payload"));
            var decryptedCertificate = handshake.cipher(payload, false);

            // Verify certificate chain
            verifyCertificateChain(decryptedCertificate, decodedStaticText);

            // Send client finish
            var noiseKeyPair = store.noiseKeyPair();
            var encodedKey = handshake.cipher(noiseKeyPair.publicKey().toEncodedPoint(), true);
            var sharedPrivate = Curve25519.sharedKey(
                    noiseKeyPair.privateKey().toEncodedPoint(), ephemeral
            );
            handshake.mixIntoKey(sharedPrivate);

            var encodedPayload = handshake.cipher(handshakePayload(), true);
            var clientFinishMessage = new HandshakeMessageBuilder()
                    .clientFinish(new HandshakeMessageClientFinishBuilder()
                            ._static(encodedKey)
                            .payload(encodedPayload)
                            .build())
                    .build();
            handshake.writeClientHandshake(clientFinishMessage);

            // Derive read/write keys and install them on the datagram
            // streams so subsequent reads decrypt and subsequent writes
            // encrypt under the Noise-derived AES keys.
            var keys = handshake.finish();
            this.writeKey = new SecretKeySpec(keys, 0, 32, "AES");
            this.readKey = new SecretKeySpec(keys, 32, 32, "AES");
            out.setWriteKey(writeKey);
            in.setReadKey(readKey);

            this.authHandshakeDuration = Duration.between(handshakeStart, Instant.now());
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("Noise handshake failure", e);
        }
    }

    /**
     * Validates that a Curve25519 signature is exactly 64 bytes.
     *
     * @param signature the raw signature bytes
     * @return {@code signature}, returned for fluent chaining
     * @throws IOException if the signature is not exactly 64 bytes long
     */
    private static byte[] ensureSignatureSize(byte[] signature) throws IOException {
        if (signature.length != 64) {
            throw new IOException("Certificate signature has invalid length: " + signature.length + ", expected 64");
        }
        return signature;
    }

    /**
     * Spawns the reader virtual thread that drains datagrams from the
     * datagram input stream, decodes each one into a {@link Node} and
     * dispatches it to the application listener.
     */
    private void startReaderThread() {
        this.readerThread = Thread.ofVirtual()
                .name("cobalt-socket-reader")
                .start(this::runReaderLoop);
    }

    /**
     * Continuously decodes nodes from the datagram input stream until
     * the transport is closed or a fatal error is observed.
     *
     * <p>A bad MAC tears down the connection through
     * {@link WhatsAppSessionException.BadMac}; any other failure
     * surfaces as {@link WhatsAppStreamException.MalformedNode}.
     */
    @WhatsAppWebExport(moduleName = "WAFrameSocket", exports = "FrameSocket", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WANoiseSocket", exports = "NoiseSocket", adaptation = WhatsAppAdaptation.ADAPTED)
    private void runReaderLoop() {
        try {
            while (!closed && isTransportOpen()) {
                Node node;
                try (var decoder = NodeReader.of(in)) {
                    node = decoder.decode();
                }
                listener.onNode(node);
            }
        } catch (WhatsAppSessionException.BadMac e) {
            if (!closed) {
                listener.onError(e);
                disconnect();
            }
        } catch (IOException e) {
            if (!closed) {
                listener.onError(new WhatsAppStreamException.MalformedNode("Reader loop failed", e));
            }
        } catch (Exception e) {
            if (!closed) {
                listener.onError(new WhatsAppStreamException.MalformedNode("Failed to process inbound datagram", e));
            }
        } finally {
            listener.onClose();
        }
    }

    /**
     * Returns the configured proxy, if any.
     *
     * @return the proxy or {@code null} if none is configured
     */
    final WhatsAppProxy proxy() {
        return store.proxy().orElse(null);
    }

    /**
     * Opens a blocking {@link Socket} to the supplied endpoint, routing
     * via the configured proxy when present and dispatching to the
     * appropriate tunnel implementation in {@code socket.tunnel}.
     *
     * <p>Three proxy shapes are supported:
     * <ul>
     *   <li>{@link WhatsAppProxy.Http.Plain} / {@link WhatsAppProxy.Http.Secure}
     *       — {@link HttpTunnel} handles both, internally TLS-wrapping
     *       the proxy hop for the secure variant before sending
     *       {@code CONNECT}.
     *   <li>{@link WhatsAppProxy.Socks} (V4 / V4a / V5 / V5h) —
     *       {@link SocksTunnel} runs the protocol-specific handshake
     *       on the raw socket.
     * </ul>
     *
     * @param target the final destination the tunnel should reach
     * @return the connected socket, already past the proxy
     *         handshake; for an {@link WhatsAppProxy.Http.Secure}
     *         proxy this is the TLS-wrapped {@link SSLSocket}, in all
     *         other cases the raw socket
     * @throws IOException if any step of the connect or tunnel fails
     */
    final Socket openTunneledSocket(InetSocketAddress target) throws IOException {
        var proxy = proxy();
        var firstHop = proxy == null
                ? target
                : new InetSocketAddress(proxy.host(), proxy.port());

        var raw = new Socket();
        raw.connect(firstHop, CONNECT_TIMEOUT_MILLIS);

        if (proxy == null) {
            return raw;
        }

        try {
            return switch (proxy) {
                case WhatsAppProxy.Http http -> HttpTunnel.tunnel(raw,
                        target.getHostString(), target.getPort(), http, sslContextFactory);
                case WhatsAppProxy.Socks socks -> SocksTunnel.tunnel(raw,
                        target.getHostString(), target.getPort(), socks);
            };
        } catch (IOException e) {
            try {
                raw.close();
            } catch (IOException _) {
            }
            throw e;
        }
    }


    /**
     * Shared base for every companion-device socket client.
     *
     * <p>{@link Browser} (WebSocket over TLS) and {@link Desktop} (raw
     * TCP + TLS for the macOS app) share the {@code WEB_PROLOGUE}, the
     * two-level certificate chain verification against the WhatsApp
     * root CA, and the handshake payload shape (user agent plus
     * {@code webInfo} block plus either login credentials or
     * registration data with companion device props).
     *
     * <p>Subtypes diverge on the transport (hand-rolled WebSocket vs
     * {@link SSLSocket}) and on the {@link #getWebSubPlatform()}
     * advertised inside the {@code webInfo} block.
     */
    static abstract sealed class Web extends WhatsAppSocketClient {

        /**
         * Two-byte web version footer appended to {@link #WHATSAPP_VERSION_HEADER}
         * to form the handshake prologue.
         */
        private static final byte[] WEB_VERSION = new byte[]{6, NodeTokens.DICTIONARY_VERSION};

        /**
         * Handshake prologue shared by every companion client.
         */
        private static final byte[] WEB_PROLOGUE = DataUtils.concatByteArrays(WHATSAPP_VERSION_HEADER, WEB_VERSION);

        /**
         * Common constructor invoked by the companion subtypes.
         *
         * @param store             the WhatsApp store
         * @param sslContextFactory the SSL configuration for every TLS
         *                          hop on this connection
         */
        Web(WhatsAppStore store, WhatsAppSslContextFactory sslContextFactory) {
            super(store, sslContextFactory);
        }

        /**
         * Returns the {@code ClientPlatformType} that this client
         * advertises in the user agent.
         *
         * @return the platform value advertised at handshake time
         */
        abstract ClientPlatformType getPlatform();

        /**
         * Returns the {@code WebSubPlatform} that this client advertises
         * inside the {@code webInfo} block.
         *
         * <p>Browsers report {@code WEB_BROWSER}; native desktop apps
         * report one of {@code DARWIN}, {@code WIN_HYBRID}, etc.
         *
         * @return the sub-platform value advertised at handshake time
         */
        abstract ClientPayload.WebInfo.WebSubPlatform getWebSubPlatform();

        @Override
        final byte[] handshakePrologue() {
            return WEB_PROLOGUE.clone();
        }

        /**
         * Verifies a two-level companion certificate chain (intermediate
         * plus leaf) against the embedded root CA.
         *
         * @param decryptedCertificate the decrypted certificate payload
         * @param serverStaticKey      the 32-byte server static key
         * @throws IOException if the chain is malformed or any signature
         *         fails to verify
         */
        @Override
        final void verifyCertificateChain(byte[] decryptedCertificate, byte[] serverStaticKey) throws IOException {
            var certChain = CertChainSpec.decode(decryptedCertificate);
            var intermediate = certChain.intermediate()
                    .orElseThrow(() -> new IOException("Certificate chain missing intermediate certificate"));
            var leaf = certChain.leaf()
                    .orElseThrow(() -> new IOException("Certificate chain missing leaf certificate"));

            var intermediateDetails = intermediate.details()
                    .orElseThrow(() -> new IOException("Intermediate certificate missing details"));
            var intermediateSignature = intermediate.signature()
                    .orElseThrow(() -> new IOException("Intermediate certificate missing signature"));
            var parsedIntermediate = NoiseCertificateCertChainDetailsSpec.decode(intermediateDetails);

            var intermediateIssuerSerial = parsedIntermediate.issuerSerial()
                    .orElseThrow(() -> new IOException("Intermediate certificate missing issuerSerial"));
            if (intermediateIssuerSerial != 0) {
                throw new IOException("Intermediate certificate was not issued by root CA, issuerSerial: " + intermediateIssuerSerial);
            }

            if (!Curve25519.verifySignature(NOISE_ROOT_CA_PUBLIC_KEY, intermediateDetails, ensureSignatureSize(intermediateSignature))) {
                throw new IOException("Intermediate certificate has invalid signature");
            }

            var leafDetails = leaf.details()
                    .orElseThrow(() -> new IOException("Leaf certificate missing details"));
            var leafSignature = leaf.signature()
                    .orElseThrow(() -> new IOException("Leaf certificate missing signature"));
            var parsedLeaf = NoiseCertificateCertChainDetailsSpec.decode(leafDetails);

            var leafIssuerSerial = parsedLeaf.issuerSerial()
                    .orElseThrow(() -> new IOException("Leaf certificate missing issuerSerial"));
            var intermediateSerial = parsedIntermediate.serial()
                    .orElseThrow(() -> new IOException("Intermediate certificate missing serial"));
            if (leafIssuerSerial != intermediateSerial) {
                throw new IOException("Leaf certificate was not issued by intermediate");
            }

            var intermediateKey = parsedIntermediate.key()
                    .orElseThrow(() -> new IOException("Intermediate certificate missing key"));
            if (!Curve25519.verifySignature(intermediateKey, leafDetails, ensureSignatureSize(leafSignature))) {
                throw new IOException("Leaf certificate has invalid signature");
            }

            var leafKey = parsedLeaf.key()
                    .orElseThrow(() -> new IOException("Leaf certificate missing key"));
            if (!Arrays.equals(leafKey, serverStaticKey)) {
                throw new IOException("Leaf certificate key does not match handshake server static key");
            }
        }

        /**
         * Builds the serialized companion-device handshake payload by
         * choosing between login and registration based on whether the
         * store already carries a JID.
         *
         * @return the serialized client payload
         */
        @Override
        final byte[] handshakePayload() {
            var agent = getUserAgent();
            var payload = getClientPayload(agent);
            return ClientPayloadSpec.encode(payload);
        }

        /**
         * Builds the user agent advertised inside the handshake payload.
         *
         * @return the user agent value
         */
        private UserAgent getUserAgent() {
            var appVersion = store.clientVersion();
            var mcc = "000";
            var mnc = "000";
            // On the Windows hybrid shell the six-digit windowsBuild
            // URL parameter is copied into appVersion.quaternary and,
            // when six characters long, its halves overwrite mcc and
            // mnc. The quaternary is already set on the cached
            // ClientAppVersion by WhatsAppWindowsClientInfo, so this
            // block only mirrors the mcc/mnc override.
            if (store.device().platform() == ClientPlatformType.WINDOWS
                    && appVersion.quaternary().isPresent()) {
                var buildStr = Integer.toString(appVersion.quaternary().getAsInt());
                if (buildStr.length() == 6) {
                    mcc = buildStr.substring(0, 3);
                    mnc = buildStr.substring(3, 6);
                }
            }
            return new ClientPayloadUserAgentBuilder()
                    .platform(getPlatform())
                    .appVersion(appVersion)
                    .mcc(mcc)
                    .mnc(mnc)
                    .releaseChannel(store.releaseChannel())
                    .localeLanguageIso6391("en")
                    .localeCountryIso31661Alpha2("US")
                    .deviceType(ClientPayload.ClientType.PHONE)
                    .deviceModelType(store.device().modelId())
                    .build();
        }

        /**
         * Builds the companion-device client payload.
         *
         * <p>Returns a reconnection payload when the store already
         * carries a JID, otherwise a fresh pairing payload that
         * includes registration data. Either way the {@code webInfo}
         * sub-platform is the value returned by
         * {@link #getWebSubPlatform()}.
         *
         * @param agent the user agent value
         * @return the client payload
         */
        private ClientPayload getClientPayload(UserAgent agent) {
            var webInfo = new ClientPayloadWebInfoBuilder()
                    .webSubPlatform(getWebSubPlatform())
                    .build();
            var jid = store.jid();
            if (jid.isPresent()) {
                return new ClientPayloadBuilder()
                        .connectType(ClientPayload.ConnectType.WIFI_UNKNOWN)
                        .connectReason(ClientPayload.ConnectReason.USER_ACTIVATED)
                        .userAgent(agent)
                        .webInfo(webInfo)
                        .username(Long.parseLong(jid.get().user()))
                        .passive(true)
                        .pull(true)
                        .device(jid.get().device())
                        .build();
            } else {
                return new ClientPayloadBuilder()
                        .connectType(ClientPayload.ConnectType.WIFI_UNKNOWN)
                        .connectReason(ClientPayload.ConnectReason.USER_ACTIVATED)
                        .userAgent(agent)
                        .webInfo(webInfo)
                        .devicePairingData(createRegisterData())
                        .passive(false)
                        .pull(false)
                        .build();
            }
        }

        /**
         * Creates the device-pairing registration data for a new
         * companion-device session, including the encoded device
         * properties.
         *
         * @return the registration data
         */
        private DevicePairingRegistrationData createRegisterData() {
            return new ClientPayloadDevicePairingRegistrationDataBuilder()
                    .buildHash(store.clientVersion().toHash())
                    .eRegid(DataUtils.intToBytes(store.registrationId(), 4))
                    .eKeytype(DataUtils.intToBytes(SignalIdentityPublicKey.type(), 1))
                    .eIdent(store.identityKeyPair().publicKey().toEncodedPoint())
                    .eSkeyId(DataUtils.intToBytes(store.signedKeyPair().id(), 3))
                    .eSkeyVal(store.signedKeyPair().publicKey().toEncodedPoint())
                    .eSkeySig(store.signedKeyPair().signature())
                    .deviceProps(createCompanionProps())
                    .build();
        }

        /**
         * Creates and encodes the companion device properties.
         *
         * <p>Carries the history sync configuration flags that tell the
         * server which categories of history data to deliver to this
         * companion. The {@code platformType} is derived from the
         * store's device platform.
         *
         * @return the encoded companion device properties
         */
        private byte[] createCompanionProps() {
            var historyLength = store.webHistoryPolicy()
                    .orElse(WhatsAppWebClientHistory.standard(true));
            var config = new DevicePropsHistorySyncConfigBuilder()
                    .inlineInitialPayloadInE2EeMsg(true)
                    .supportBotUserAgentChatHistory(true)
                    .supportCagReactionsAndPolls(true)
                    .supportRecentSyncChunkMessageCountTuning(true)
                    .supportHostedGroupMsg(true)
                    .supportBizHostedMsg(true)
                    .supportFbidBotChatHistory(true)
                    .supportMessageAssociation(true)
                    .supportCallLogHistory(store.device().platform() == ClientPlatformType.WINDOWS)
                    .supportGroupHistory(true)
                    .storageQuotaMb(historyLength.size())
                    .fullSyncSizeMbLimit(historyLength.size())
                    .build();
            var platformType = switch (store.device().platform()) {
                case IOS, IOS_BUSINESS -> DevicePlatformType.IOS_PHONE;
                case ANDROID, ANDROID_BUSINESS -> DevicePlatformType.ANDROID_PHONE;
                case WINDOWS -> DevicePlatformType.UWP;
                case MACOS -> DevicePlatformType.IOS_CATALYST;
                case WEB -> DevicePlatformType.CHROME;
                default -> throw new IllegalStateException("Unexpected value: " + store.device().platform());
            };
            var props = new DevicePropsBuilder()
                    .os(store.name())
                    .platformType(platformType)
                    .requireFullSync(historyLength.isExtended())
                    .historySyncConfig(config)
                    .version(store.clientVersion())
                    .build();
            return DevicePropsSpec.encode(props);
        }

        /**
         * Companion client for browser tabs and the Windows hybrid
         * desktop shell.
         *
         * <p>Connects to {@code wss://web.whatsapp.com/ws/chat} by
         * opening a raw {@link Socket}, optionally tunnelling it
         * through an HTTP or SOCKS proxy via
         * {@link WhatsAppSocketClient#openTunneledSocket(InetSocketAddress)},
         * TLS-wrapping it with the supplied
         * {@link WhatsAppSslContextFactory}, and then driving the
         * RFC 6455 upgrade handshake via {@link WebSocketUpgrade}.
         * Once the upgrade completes, the {@link WebSocketFrameInputStream}
         * and {@link WebSocketFrameOutputStream} take over: the
         * WhatsApp datagrams ride on top as a continuous byte stream.
         *
         * <p>Browser and Windows hybrid share this class because the
         * hybrid shell ships the same JavaScript bundle and reuses the
         * same endpoint; only the handshake payload changes, expressed
         * through {@link #getWebSubPlatform()}.
         */
        static final class Browser extends Web {

            /**
             * TCP endpoint for browser companions; the WebSocket upgrade
             * negotiates the {@code /ws/chat} path on top of it.
             */
            private static final InetSocketAddress WEB_ENDPOINT = new InetSocketAddress("web.whatsapp.com", 443);

            /**
             * Host header sent on the WebSocket upgrade request.
             */
            private static final String WEB_HOST = "web.whatsapp.com";

            /**
             * Endpoint path sent on the WebSocket upgrade request.
             */
            private static final String WEB_PATH = "/ws/chat";

            /**
             * Desktop Chrome User-Agent advertised on the upgrade
             * request so the server does not redirect us to the mobile
             * landing page.
             */
            private static final String WEB_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";

            /**
             * The TLS-wrapped socket, set by {@link #openTransport()}.
             */
            private SSLSocket socket;

            /**
             * Constructs a browser companion client.
             *
             * @param store             the WhatsApp store
             * @param sslContextFactory the SSL configuration for the
             *                          WebSocket TLS hop
             */
            Browser(WhatsAppStore store, WhatsAppSslContextFactory sslContextFactory) {
                super(store, sslContextFactory);
            }

            @Override
            void openTransport() throws IOException {
                var raw = openTunneledSocket(WEB_ENDPOINT);
                SSLSocket ssl = null;
                try {
                    ssl = (SSLSocket) sslContextFactory.sslContext()
                            .getSocketFactory()
                            .createSocket(raw, WEB_HOST, WEB_ENDPOINT.getPort(), true);
                    ssl.setSSLParameters(sslContextFactory.sslParameters());
                    ssl.startHandshake();

                    var leftover = WebSocketUpgrade.upgrade(ssl, WEB_PATH, WEB_HOST,
                            WEB_ENDPOINT.getPort(), WEB_USER_AGENT);

                    this.socket = ssl;
                    var wsOut = new WebSocketFrameOutputStream(ssl.getOutputStream());
                    var wsIn = new WebSocketFrameInputStream(ssl.getInputStream(), leftover, wsOut);
                    this.in = new WhatsAppDatagramInputStream(wsIn);
                    this.out = new WhatsAppDatagramOutputStream(wsOut);
                } catch (IOException e) {
                    if (ssl != null) {
                        try {
                            ssl.close();
                        } catch (IOException _) {
                        }
                    } else {
                        try {
                            raw.close();
                        } catch (IOException _) {
                        }
                    }
                    throw e;
                }
            }

            @Override
            void closeTransport() throws IOException {
                if (socket != null) {
                    socket.close();
                }
            }

            @Override
            boolean isTransportOpen() {
                return socket != null && !socket.isClosed() && socket.isConnected();
            }

            @Override
            ClientPlatformType getPlatform() {
                // The Windows hybrid shell keeps UserAgent.platform as
                // WEB and only distinguishes itself from a browser
                // through the WebInfo.webSubPlatform field.
                return ClientPlatformType.WEB;
            }

            @Override
            ClientPayload.WebInfo.WebSubPlatform getWebSubPlatform() {
                return switch (store.device().platform()) {
                    case WINDOWS -> ClientPayload.WebInfo.WebSubPlatform.WIN_HYBRID;
                    case WEB -> ClientPayload.WebInfo.WebSubPlatform.WEB_BROWSER;
                    default -> throw new IllegalStateException(
                            "Browser client does not support platform: " + store.device().platform());
                };
            }
        }

        /**
         * Companion client for the native macOS desktop application.
         *
         * <p>Connects via a blocking {@link Socket} (optionally tunneled
         * through an HTTP or SOCKS proxy via
         * {@link WhatsAppSocketClient#openTunneledSocket(InetSocketAddress)})
         * and then upgrades to TLS using the Chrome-style
         * {@link javax.net.ssl.SSLContext}. The Noise XX handshake runs
         * over the {@link SSLSocket} once the TLS handshake completes.
         *
         * <p>Only macOS uses this client. The Windows desktop build is
         * a hybrid web/native shell that reuses the WebSocket endpoint
         * and is therefore served by {@link Browser}.
         */
        static final class Desktop extends Web {

            /**
             * TCP endpoint for desktop companions; shared with mobile.
             */
            private static final InetSocketAddress DESKTOP_ENDPOINT = new InetSocketAddress("g.whatsapp.net", 443);

            /**
             * The TLS-wrapped socket, set by {@link #openTransport()}.
             */
            private SSLSocket socket;

            /**
             * Constructs a desktop companion client.
             *
             * @param store             the WhatsApp store
             * @param sslContextFactory the SSL configuration for the
             *                          end-to-end TLS hop
             */
            Desktop(WhatsAppStore store, WhatsAppSslContextFactory sslContextFactory) {
                super(store, sslContextFactory);
            }

            @Override
            void openTransport() throws IOException {
                var raw = openTunneledSocket(DESKTOP_ENDPOINT);
                SSLSocket ssl = null;
                try {
                    ssl = (SSLSocket) sslContextFactory.sslContext()
                            .getSocketFactory()
                            .createSocket(raw, DESKTOP_ENDPOINT.getHostString(),
                                    DESKTOP_ENDPOINT.getPort(), true);
                    ssl.setSSLParameters(sslContextFactory.sslParameters());
                    ssl.startHandshake();
                    this.socket = ssl;
                    this.in = new WhatsAppDatagramInputStream(ssl.getInputStream());
                    this.out = new WhatsAppDatagramOutputStream(ssl.getOutputStream());
                } catch (IOException e) {
                    if (ssl != null) {
                        try {
                            ssl.close();
                        } catch (IOException _) {
                        }
                    } else {
                        try {
                            raw.close();
                        } catch (IOException _) {
                        }
                    }
                    throw e;
                }
            }

            @Override
            void closeTransport() throws IOException {
                if (socket != null) {
                    socket.close();
                }
            }

            @Override
            boolean isTransportOpen() {
                return socket != null && !socket.isClosed() && socket.isConnected();
            }

            @Override
            ClientPlatformType getPlatform() {
                return ClientPlatformType.MACOS;
            }

            @Override
            ClientPayload.WebInfo.WebSubPlatform getWebSubPlatform() {
                return ClientPayload.WebInfo.WebSubPlatform.DARWIN;
            }
        }
    }

    /**
     * Native mobile (iOS or Android) socket client.
     *
     * <p>Connects via a blocking {@link Socket} (optionally tunneled
     * through an HTTP or SOCKS proxy via
     * {@link #openTunneledSocket(InetSocketAddress)}). The Noise XX
     * handshake runs directly over the socket because mobile clients do
     * not negotiate a separate TLS layer.
     */
    static final class Mobile extends WhatsAppSocketClient {

        /**
         * Two-byte mobile version footer appended to
         * {@link #WHATSAPP_VERSION_HEADER} to form the handshake
         * prologue.
         */
        private static final byte[] MOBILE_VERSION = new byte[]{5, NodeTokens.DICTIONARY_VERSION};

        /**
         * Handshake prologue advertised by mobile clients.
         */
        private static final byte[] MOBILE_PROLOGUE = DataUtils.concatByteArrays(WHATSAPP_VERSION_HEADER, MOBILE_VERSION);

        /**
         * TCP endpoint for mobile connections.
         */
        private static final InetSocketAddress SOCKET_ENDPOINT = new InetSocketAddress("g.whatsapp.net", 443);

        /**
         * The TCP socket, set by {@link #openTransport()}.
         */
        private Socket socket;

        /**
         * Constructs a mobile socket client.
         *
         * @param store             the WhatsApp store
         * @param sslContextFactory the SSL configuration applied when
         *                          tunnelling through a TLS proxy; the
         *                          end-to-end Mobile hop does not use
         *                          TLS so this factory is otherwise
         *                          unused
         */
        Mobile(WhatsAppStore store, WhatsAppSslContextFactory sslContextFactory) {
            super(store, sslContextFactory);
        }

        @Override
        void openTransport() throws IOException {
            this.socket = openTunneledSocket(SOCKET_ENDPOINT);
            this.in = new WhatsAppDatagramInputStream(socket.getInputStream());
            this.out = new WhatsAppDatagramOutputStream(socket.getOutputStream());
        }

        @Override
        void closeTransport() throws IOException {
            if (socket != null) {
                socket.close();
            }
        }

        @Override
        boolean isTransportOpen() {
            return socket != null && !socket.isClosed() && socket.isConnected();
        }

        /**
         * Verifies the flat {@code NoiseCertificate} that the mobile
         * server returns instead of a two-level chain.
         *
         * <p>The certificate is verified against the root CA public key
         * and its embedded key must match the server static key from
         * the handshake.
         *
         * @param decryptedCertificate the decrypted certificate payload
         * @param serverStaticKey      the 32-byte server static key
         * @throws IOException if any check fails
         */
        @Override
        void verifyCertificateChain(byte[] decryptedCertificate, byte[] serverStaticKey) throws IOException {
            var cert = NoiseCertificateSpec.decode(decryptedCertificate);
            var details = cert.details()
                    .orElseThrow(() -> new IOException("NoiseCertificate missing details"));
            var signature = cert.signature()
                    .orElseThrow(() -> new IOException("NoiseCertificate missing signature"));

            if (!Curve25519.verifySignature(NOISE_ROOT_CA_PUBLIC_KEY, details, ensureSignatureSize(signature))) {
                throw new IOException("NoiseCertificate has invalid signature");
            }

            var parsedDetails = NoiseCertificateDetailsSpec.decode(details);
            var key = parsedDetails.key()
                    .orElseThrow(() -> new IOException("NoiseCertificate missing key"));
            if (!Arrays.equals(key, serverStaticKey)) {
                throw new IOException("NoiseCertificate key does not match handshake server static key");
            }
        }

        /**
         * Returns a defensive copy of the mobile handshake prologue.
         *
         * @return the prologue bytes
         */
        @Override
        byte[] handshakePrologue() {
            return MOBILE_PROLOGUE.clone();
        }

        /**
         * Builds the serialized mobile handshake payload.
         *
         * @return the serialized client payload
         */
        @Override
        byte[] handshakePayload() {
            var agent = getUserAgent();
            var payload = getClientPayload(agent);
            return ClientPayloadSpec.encode(payload);
        }

        /**
         * Builds the mobile-specific user agent, including the device
         * manufacturer, model, OS version and FDID.
         *
         * @return the user agent value
         */
        private UserAgent getUserAgent() {
            return new ClientPayloadUserAgentBuilder()
                    .platform(store.device().platform())
                    .appVersion(store.clientVersion())
                    .mcc("000")
                    .mnc("000")
                    .osVersion(store.device().osDeviceAppVersion().toString())
                    .manufacturer(store.device().manufacturer())
                    .device(store.device().model().replaceAll("_", " "))
                    .osBuildNumber(store.device().osBuildNumber())
                    .phoneId(store.fdid().toString().toUpperCase())
                    .releaseChannel(store.releaseChannel())
                    .localeLanguageIso6391("en")
                    .localeCountryIso31661Alpha2("US")
                    .deviceType(ClientPayload.ClientType.PHONE)
                    .deviceModelType(store.device().modelId())
                    .build();
        }

        /**
         * Builds the mobile client payload.
         *
         * @param agent the user agent value
         * @return the client payload
         */
        private ClientPayload getClientPayload(UserAgent agent) {
            var phoneNumber = store
                    .phoneNumber()
                    .orElseThrow(() -> new InternalError("Phone number was not set"));
            return new ClientPayloadBuilder()
                    .username(phoneNumber)
                    .passive(false)
                    .pushName(store.registered() ? store.name() : null)
                    .userAgent(agent)
                    .shortConnect(true)
                    .connectType(ClientPayload.ConnectType.WIFI_UNKNOWN)
                    .connectReason(ClientPayload.ConnectReason.USER_ACTIVATED)
                    .connectAttemptCount(0)
                    .device(0)
                    .oc(false)
                    .build();
        }
    }
}
