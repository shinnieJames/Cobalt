package com.github.auties00.cobalt.socket;

import com.github.auties00.cobalt.client.WhatsAppClientProxy;
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
import com.github.auties00.cobalt.node.binary.NodeDecoder;
import com.github.auties00.cobalt.node.binary.NodeEncoder;
import com.github.auties00.cobalt.node.binary.NodeTokens;
import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.SocketClientLayerListener;
import com.github.auties00.cobalt.socket.layer.application.websocket.WebSocketClientLayer;
import com.github.auties00.cobalt.socket.layer.application.whatsapp.WhatsAppSocketClientLayer;
import com.github.auties00.cobalt.socket.layer.security.SocketClientSecurityLayer;
import com.github.auties00.cobalt.socket.layer.transport.SocketClientTransportLayer;
import com.github.auties00.cobalt.socket.layer.tunnel.SocketClientTunnelLayer;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.util.DataUtils;
import com.github.auties00.cobalt.util.GcmUtils;
import com.github.auties00.curve25519.Curve25519;
import com.github.auties00.libsignal.key.SignalIdentityKeyPair;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import it.auties.protobuf.stream.ProtobufInputStream;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.DestroyFailedException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * A sealed WhatsApp socket client hierarchy that provides Noise XX encryption
 * and int24-framed datagram transport over either a WebSocket connection
 * ({@link Web}) or a raw TCP connection ({@link Mobile}).
 *
 * <p>For WEB clients, the data flow is:
 * <pre>
 * Node -> serialize -> Noise encrypt + int24 prefix -> WebSocket binary frame -> TLS -> TCP
 * </pre>
 *
 * <p>For mobile clients, the data flow is:
 * <pre>
 * Node -> serialize -> Noise encrypt + int24 prefix -> TCP
 * </pre>
 */
public sealed abstract class WhatsAppSocketClient {
    /**
     * The WhatsApp long-term root CA public key used to verify the
     * certificate chain received during the Noise handshake.
     *
     * <p>This 32-byte Ed25519 public key is the trust anchor for the
     * two-level certificate chain (root -> intermediate -> leaf). The
     * intermediate certificate must have {@code issuerSerial == 0} and be
     * signed by this key. The leaf certificate must be signed by the
     * intermediate and its key must match the server static key from
     * the handshake.
     *
     * @implNote WAVerifyChainCertificateWA6.R — the hex-encoded root CA
     *     public key constant used by {@code verifyChainCertificateWA6}
     */
    private static final byte[] NOISE_ROOT_CA_PUBLIC_KEY = HexFormat.of().parseHex(
            "142375574d0a587166aae71ebe516437c4a28b73e3695c6ce1f7f9545da8ee6b"
    );

    private static final byte[] WHATSAPP_VERSION_HEADER = "WA".getBytes(StandardCharsets.UTF_8);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int INT24_BYTE_SIZE = 3;
    private static final int GCM_TAG_BYTE_SIZE = 16;
    private static final int MAX_MESSAGE_LENGTH = 0xFFFFFF;
    private static final int MAX_HANDSHAKE_MESSAGE_LENGTH = 0xFFFF;

    /**
     * Creates a new WhatsApp socket client for the given store.
     *
     * <p>For WEB clients, builds a WebSocket client over TCP + TLS.
     * For mobile clients, builds a raw TCP transport.
     *
     * @param store the WhatsApp store
     * @return a new WhatsApp socket client
     */
    public static WhatsAppSocketClient newCipheredSocketClient(WhatsAppStore store) {
        return newCipheredSocketClient(store, WhatsAppSslEngineFactory.chrome());
    }

    /**
     * Creates a new WhatsApp socket client with a custom SSL engine factory.
     *
     * @param store            the WhatsApp store
     * @param sslEngineFactory the factory for creating {@link javax.net.ssl.SSLEngine} instances
     * @return a new WhatsApp socket client
     */
    static WhatsAppSocketClient newCipheredSocketClient(WhatsAppStore store, WhatsAppSslEngineFactory sslEngineFactory) {
        Objects.requireNonNull(store, "store cannot be null");
        Objects.requireNonNull(sslEngineFactory, "sslEngineFactory cannot be null");

        var platform = store.device().platform();
        var transport = createTransport();
        var tunnelSecurity = createTunnelSecurity(transport, store, sslEngineFactory);
        var tunnel = createTunnel(tunnelSecurity, store);
        var transportSecurity = createTransportSecurity(tunnel, store, sslEngineFactory);

        return switch (platform) {
            case WEB -> {
                var userAgent = store.device().toUserAgent(store.clientVersion());
                var webSocket = new WebSocketClientLayer(transportSecurity, "/ws/chat", userAgent);
                var whatsAppLayer = new WhatsAppSocketClientLayer(webSocket);
                yield new Browser(store, whatsAppLayer);
            }
            case WINDOWS, MACOS -> {
                var whatsAppLayer = new WhatsAppSocketClientLayer(transportSecurity);
                yield new Desktop(store, whatsAppLayer);
            }
            default -> {
                var whatsAppLayer = new WhatsAppSocketClientLayer(transportSecurity);
                yield new Mobile(store, whatsAppLayer);
            }
        };
    }

    private static SocketClientLayer<?> createTransport() {
        return SocketClientTransportLayer.newTcpTransport();
    }

    private static SocketClientLayer<?> createTunnelSecurity(SocketClientLayer<?> transport, WhatsAppStore store, WhatsAppSslEngineFactory engineFactory) {
        return switch (store.proxy().orElse(null)) {
            case WhatsAppClientProxy.Http.Secure _ -> SocketClientSecurityLayer.newTls(transport, engineFactory);
            case null, default -> SocketClientSecurityLayer.newPlain(transport);
        };
    }

    private static SocketClientLayer<?> createTunnel(SocketClientLayer<?> tunnelSecurity, WhatsAppStore store) {
        return switch (store.proxy().orElse(null)) {
            case WhatsAppClientProxy.Http http -> SocketClientTunnelLayer.newHttpTunnel(http, tunnelSecurity);
            case WhatsAppClientProxy.Socks socks -> SocketClientTunnelLayer.newSocksTunnel(socks, tunnelSecurity);
            case null -> SocketClientTunnelLayer.newDirectTunnel(tunnelSecurity);
        };
    }

    private static SocketClientLayer<?> createTransportSecurity(SocketClientLayer<?> tunnel, WhatsAppStore store, WhatsAppSslEngineFactory engineFactory) {
        if (store.device().platform() == ClientPlatformType.WEB) {
            return SocketClientSecurityLayer.newTls(tunnel, engineFactory);
        }
        return SocketClientSecurityLayer.newPlain(tunnel);
    }

    /**
     * The WhatsApp store for this connection.
     */
    final WhatsAppStore store;

    /**
     * The WhatsApp application layer that owns the per-connection layer
     * context and exposes the handshake-plumbing API.  All I/O flows
     * through this layer.
     */
    final WhatsAppSocketClientLayer whatsAppLayer;

    /**
     * The original listener to receive deserialized nodes and close events.
     */
    private WhatsAppSocketListener listener;

    /**
     * The AES-GCM cipher used for encrypting outbound messages.
     *
     * @implNote WANoiseSocket.NoiseSocket.$3 — corresponds to the write
     *     CryptoKey used with {@code p()} for encryption
     */
    private volatile Cipher writeCipher;

    /**
     * The AES write key derived from the Noise handshake.
     *
     * @implNote WANoiseSocket.NoiseSocket constructor parameter {@code n}
     *     — the encrypt key passed from {@code NoiseHandshake.finish()}
     */
    private volatile SecretKeySpec writeKey;

    /**
     * The write nonce counter, incremented for each outbound message.
     *
     * @implNote WANoiseSocket.NoiseSocket.$8 — initialized to 0,
     *     incremented in {@code sendFrame} via {@code this.$8++}
     */
    private long writeCounter;

    /**
     * The AES-GCM cipher used for decrypting inbound messages.
     *
     * @implNote WANoiseSocket.NoiseSocket.$2 — corresponds to the read
     *     CryptoKey used with {@code _()} for decryption
     */
    private volatile Cipher readCipher;

    /**
     * The AES read key derived from the Noise handshake.
     *
     * @implNote WANoiseSocket.NoiseSocket constructor parameter {@code r}
     *     — the decrypt key passed from {@code NoiseHandshake.finish()}
     */
    private volatile SecretKeySpec readKey;

    /**
     * The read nonce counter, incremented for each inbound datagram.
     *
     * @implNote WANoiseSocket.NoiseSocket.$7 — initialized to 0,
     *     incremented in {@code $12} (onFrame handler) via {@code a.$7++}
     */
    private long readCounter;

    /**
     * Reusable length prefix buffer for outbound messages.
     *
     * <p>Safe to reuse because {@link #sendBinary(ByteBuffer...)} is
     * {@code synchronized}.
     */
    private final ByteBuffer reusableLengthPrefix = ByteBuffer.allocate(INT24_BYTE_SIZE);

    /**
     * Reusable buffer for the GCM authentication tag produced by
     * {@code doFinal()}.  Lazily sized after the first cipher init.
     *
     * <p>Safe to reuse because {@link #sendBinary(ByteBuffer...)} is
     * {@code synchronized}.
     */
    private ByteBuffer reusableFinalChunk;

    /**
     * Constructs a new WhatsApp socket client with the given store and
     * WhatsApp application layer.
     *
     * @param store         the WhatsApp store
     * @param whatsAppLayer the WhatsApp application layer (already wrapping
     *                      the full stack)
     */
    private WhatsAppSocketClient(WhatsAppStore store, WhatsAppSocketClientLayer whatsAppLayer) {
        this.store = store;
        this.whatsAppLayer = whatsAppLayer;
    }

    /**
     * Connects and performs the Noise XX handshake.
     *
     * <p>Delegates the transport-specific sequencing (where to call
     * {@code finishConnect} relative to the Noise handshake) to the
     * subtype via {@link #connectImpl()}.
     *
     * @implNote ADAPTED: WANoiseSocket.NoiseSocket.constructor — in WA Web,
     *     the NoiseSocket constructor wires the FrameSocket's onFrame and
     *     onClose callbacks; in Cobalt, the DecryptingListener is wired
     *     during connection setup
     * @param listener the callback for deserialized nodes and close events
     * @throws IOException if the connection or handshake fails
     */
    public final void connect(WhatsAppSocketListener listener) throws IOException {
        Objects.requireNonNull(listener, "listener cannot be null");
        this.listener = listener;

        var decryptingListener = new DecryptingListener();
        whatsAppLayer.connect(getEndpoint(), decryptingListener);
        connectImpl();
    }

    /**
     * Returns the remote endpoint for this client (WEB WebSocket endpoint
     * or mobile TCP endpoint).
     *
     * @return the remote endpoint
     */
    abstract InetSocketAddress getEndpoint();

    /**
     * Performs the transport-specific Noise handshake sequencing.  Web
     * finishes the connection before the handshake (because the WebSocket
     * upgrade already happened during {@link WhatsAppSocketClientLayer#connect}
     * and the chain is async); Mobile finishes the connection after the
     * handshake (because the transport is still in pre-tunnel mode and
     * the handshake reads need that).
     *
     * @throws IOException if the handshake fails
     */
    abstract void connectImpl() throws IOException;

    /**
     * Disconnects and destroys cipher keys.
     *
     * <p>In WA Web, closing is handled by {@code NoiseSocket.close()},
     * which delegates to {@code FrameSocket.close()}.  Key destruction
     * is handled by the browser's garbage collector.  In Cobalt, key
     * material is explicitly destroyed via {@link SecretKeySpec#destroy()}
     * and counters are reset to zero.
     *
     * @implNote ADAPTED: WANoiseSocket.NoiseSocket.close — Cobalt adds
     *     explicit key destruction and counter reset for security
     */
    public final void disconnect() {
        if (readKey != null) {
            try {
                readKey.destroy();
            } catch (DestroyFailedException _) {
            }
            readKey = null;
        }
        readCounter = 0;

        if (writeKey != null) {
            try {
                writeKey.destroy();
            } catch (DestroyFailedException _) {
            }
            writeKey = null;
        }
        writeCounter = 0;

        whatsAppLayer.disconnect();
    }

    /**
     * Returns whether the connection is active.
     *
     * @return {@code true} if connected
     */
    public final boolean isConnected() {
        return whatsAppLayer.isConnected();
    }

    /**
     * Encrypts the given plaintext buffers with AES-GCM and sends the
     * result, prefixed with a 3-byte int24 ciphertext length header.
     *
     * <p>Before the handshake completes (no write key), buffers are
     * passed through without encryption.
     *
     * <p>This method is synchronized to ensure the write counter stays
     * consistent across concurrent callers.
     *
     * <p>In WA Web, this corresponds to {@code NoiseSocket.sendFrame},
     * which increments the write counter, encrypts the plaintext via
     * AES-GCM (function {@code p}), and sends the ciphertext through
     * the underlying {@code FrameSocket.sendFrame} (which prepends
     * the int24 length header).  In Cobalt, the int24 prefix is
     * applied here because the frame socket and noise socket are
     * combined into a single class.
     *
     * @implNote WANoiseSocket.NoiseSocket.sendFrame — encrypts with
     *     {@code p(writeKey, writeCounter++, undefined, data)}, then
     *     sends via {@code FrameSocket.sendFrame} which adds the
     *     int24 length header
     * @param buffers the plaintext buffers to encrypt and send
     * @throws IOException if the write fails
     */
    public final synchronized void sendBinary(ByteBuffer... buffers) throws IOException {
        if (writeKey == null) {
            sendRaw(buffers);
            return;
        }

        var plaintextLength = 0;
        var payloadCount = 0;
        for (var buf : buffers) {
            if (buf != null && buf.hasRemaining()) {
                try {
                    plaintextLength = Math.addExact(plaintextLength, buf.remaining());
                } catch (ArithmeticException e) {
                    throw new IOException("Cannot encrypt plaintext: payload length overflow", e);
                }
                payloadCount++;
            }
        }
        if (payloadCount == 0) {
            return;
        }

        try {
            writeCipher.init(
                    Cipher.ENCRYPT_MODE,
                    writeKey,
                    GcmUtils.createNonce(writeCounter++)
            );
        } catch (InvalidAlgorithmParameterException | InvalidKeyException e) {
            throw new IOException("Cannot initialize write cipher", e);
        }

        try {
            var ciphertextLength = writeCipher.getOutputSize(plaintextLength);
            if (ciphertextLength > MAX_MESSAGE_LENGTH) {
                throw new IOException("Cannot encrypt plaintext: ciphertext length exceeds int24");
            }

            var output = new ByteBuffer[payloadCount + 2];
            reusableLengthPrefix.clear();
            reusableLengthPrefix.put((byte) ((ciphertextLength >> 16) & 0xFF));
            reusableLengthPrefix.put((byte) ((ciphertextLength >> 8) & 0xFF));
            reusableLengthPrefix.put((byte) (ciphertextLength & 0xFF));
            reusableLengthPrefix.flip();
            output[0] = reusableLengthPrefix;
            var outputIndex = 1;
            var producedCipherBytes = 0;

            for (var buffer : buffers) {
                if (buffer == null || !buffer.hasRemaining()) {
                    continue;
                }

                var encryptedSegment = encryptSegmentInPlace(buffer);
                producedCipherBytes += encryptedSegment.remaining();
                output[outputIndex++] = encryptedSegment;
            }

            var finalSize = writeCipher.getOutputSize(0);
            if (reusableFinalChunk == null || reusableFinalChunk.capacity() < finalSize) {
                reusableFinalChunk = ByteBuffer.allocate(finalSize);
            }
            reusableFinalChunk.clear();
            var finalProduced = writeCipher.doFinal(DataUtils.EMPTY_BYTE_BUFFER, reusableFinalChunk);
            reusableFinalChunk.flip();
            output[outputIndex] = reusableFinalChunk;

            if (producedCipherBytes + finalProduced != ciphertextLength) {
                throw new IOException(
                        "Cannot encrypt plaintext: produced ciphertext length mismatch"
                );
            }

            sendRaw(output);
        } catch (ShortBufferException | IllegalBlockSizeException | BadPaddingException e) {
            throw new IOException("Cannot encrypt plaintext", e);
        }
    }

    /**
     * Encrypts a single plaintext buffer segment in place.
     *
     * @implNote ADAPTED: WANoiseSocket.NoiseSocket.sendFrame — WA Web
     *     encrypts the entire payload at once via WebCrypto's
     *     {@code subtle.encrypt}; Cobalt processes each
     *     {@link ByteBuffer} segment individually for zero-copy
     *     in-place encryption
     * @param source the source buffer (must be writable)
     * @return a view of the encrypted segment
     * @throws ShortBufferException if the output buffer is too small
     * @throws IOException          if the source buffer is read-only
     */
    private ByteBuffer encryptSegmentInPlace(ByteBuffer source) throws ShortBufferException, IOException {
        if (source.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }

        var start = source.position();
        var inputView = source.duplicate();
        var outputView = source.duplicate();
        outputView.position(start);
        outputView.limit(source.limit());

        var produced = writeCipher.update(inputView, outputView);
        var encrypted = source.duplicate();
        encrypted.position(start);
        encrypted.limit(start + produced);
        source.position(source.limit());
        return encrypted;
    }

    /**
     * Sends raw buffers through the WhatsApp application layer.
     *
     * @param buffers the buffers to send
     * @throws IOException if the write fails
     */
    final void sendRaw(ByteBuffer... buffers) throws IOException {
        whatsAppLayer.sendBinary(buffers);
    }

    /**
     * Performs the Noise XX handshake.
     *
     * <p>Handshake reads flow through {@link WhatsAppSocketClientLayer#readBinary}
     * which uses the selector's chain-tail-walk to deliver bytes into the
     * WhatsApp layer context's pending-read buffer.  Outer crypto layers
     * (proxy TLS, end-to-end TLS) unwrap their envelopes as part of the
     * normal inbound flow.
     *
     * <p>After the handshake completes, the derived 64-byte key material
     * is split into a 32-byte write key (bytes 0-31) and a 32-byte read
     * key (bytes 32-63), matching the WA Web {@code NoiseHandshake.finish}
     * method which creates a {@code NoiseSocket(frameSocket, encryptKey,
     * decryptKey)}.
     *
     * @implNote WAWebOpenChatSocket.W (doFullHandshake), WAWebOpenChatSocket.H
     *     (processServerHello), WAWebOpenChatSocket.z (continueFullHandshakeCore),
     *     WAWebOpenChatSocket.j (staticAgreement) — orchestrates the full Noise XX
     *     handshake flow using WANoiseHandshake.NoiseHandshake primitives; key
     *     assignment to read/write ciphers corresponds to WANoiseSocket.NoiseSocket
     *     constructor parameters
     * @throws IOException if the handshake fails
     */
    final void performNoiseHandshake() throws IOException {
        var ephemeralKeyPair = SignalIdentityKeyPair.random();
        var prologue = getHandshakePrologue();

        try (var handshake = new WhatsAppSocketHandshake(prologue)) {
            // Send client hello
            var clientHello = new HandshakeMessageClientHelloBuilder()
                    .ephemeral(ephemeralKeyPair.publicKey().toEncodedPoint())
                    .build();
            var handshakeMessage = new HandshakeMessageBuilder()
                    .clientHello(clientHello)
                    .build();
            var requestBytes = HandshakeMessageSpec.encode(handshakeMessage);
            handshake.updateHash(ephemeralKeyPair.publicKey().toEncodedPoint());
            sendHandshakeMessage(ByteBuffer.wrap(prologue), requestBytes);

            // Read server hello
            var serverHelloPayload = readHandshakeMessage();

            // Process server hello
            var serverHandshake = HandshakeMessageSpec.decode(ProtobufInputStream.fromBuffer(serverHelloPayload));
            var serverHello = serverHandshake.serverHello()
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
            // WAWebOpenChatSocket.z -> WAWebProcessCertificate.verifyAndProcessCertificate
            verifyCertificateChain(decryptedCertificate, decodedStaticText);

            // Send client finish
            var noiseKeyPair = store.noiseKeyPair();
            var encodedKey = handshake.cipher(noiseKeyPair.publicKey().toEncodedPoint(), true);
            var sharedPrivate = Curve25519.sharedKey(
                    noiseKeyPair.privateKey().toEncodedPoint(), ephemeral
            );
            handshake.mixIntoKey(sharedPrivate);

            var encodedPayload = handshake.cipher(getHandshakePayload(), true);
            var clientFinish = new HandshakeMessageClientFinishBuilder()
                    ._static(encodedKey)
                    .payload(encodedPayload)
                    .build();
            var clientHandshake = new HandshakeMessageBuilder()
                    .clientFinish(clientFinish)
                    .build();
            var finishBytes = HandshakeMessageSpec.encode(clientHandshake);
            sendHandshakeMessage(null, finishBytes);

            // Derive read/write keys
            var keys = handshake.finish();

            this.writeCipher = Cipher.getInstance(ALGORITHM);
            this.writeCounter = 0;
            this.writeKey = new SecretKeySpec(keys, 0, 32, "AES");

            this.readCipher = Cipher.getInstance(ALGORITHM);
            this.readCounter = 0;
            this.readKey = new SecretKeySpec(keys, 32, 32, "AES");
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("Noise handshake failure", e);
        }
    }

    /**
     * Sends a handshake message with an optional prologue prefix and a
     * 3-byte int24 length header.
     *
     * @param prologue     the prologue to prepend, or {@code null} for none
     * @param messageBytes the serialized handshake message
     * @throws IOException if the write fails
     */
    private void sendHandshakeMessage(ByteBuffer prologue, byte[] messageBytes) throws IOException {
        var lengthPrefix = ByteBuffer.allocate(INT24_BYTE_SIZE);
        var len = messageBytes.length;
        lengthPrefix.put((byte) ((len >> 16) & 0xFF));
        lengthPrefix.put((byte) ((len >> 8) & 0xFF));
        lengthPrefix.put((byte) (len & 0xFF));
        lengthPrefix.flip();
        if (prologue != null) {
            sendRaw(prologue, lengthPrefix, ByteBuffer.wrap(messageBytes));
        } else {
            sendRaw(lengthPrefix, ByteBuffer.wrap(messageBytes));
        }
    }

    /**
     * Reads a complete handshake message through the WhatsApp layer.
     *
     * <p>The layer's {@code readBinary} uses the selector's chain-tail
     * walk to accept a pending-read on the WhatsApp layer context.
     * Outer crypto layers unwrap their envelopes as part of the normal
     * inbound flow, so by the time bytes reach the WhatsApp context
     * they are plaintext handshake bytes.
     *
     * @return the message payload as a {@link ByteBuffer} in read mode
     * @throws IOException if the read fails or yields an invalid length
     */
    private ByteBuffer readHandshakeMessage() throws IOException {
        var lengthBuf = ByteBuffer.allocate(INT24_BYTE_SIZE);
        var bytesRead = whatsAppLayer.readBinary(lengthBuf, true);
        if (bytesRead < INT24_BYTE_SIZE) {
            throw new IOException("Failed to read handshake message length");
        }

        lengthBuf.flip();
        var length = ((lengthBuf.get() & 0xFF) << 16)
                | ((lengthBuf.get() & 0xFF) << 8)
                | (lengthBuf.get() & 0xFF);
        if (length <= 0) {
            throw new IOException("Invalid handshake message length: " + length);
        }
        if (length > MAX_HANDSHAKE_MESSAGE_LENGTH) {
            throw new IOException("Handshake message too large: " + length + " bytes (max " + MAX_HANDSHAKE_MESSAGE_LENGTH + ")");
        }

        var payloadBuf = ByteBuffer.allocate(length);
        bytesRead = whatsAppLayer.readBinary(payloadBuf, true);
        if (bytesRead < length) {
            throw new IOException("Failed to read handshake message payload");
        }

        payloadBuf.flip();
        return payloadBuf;
    }

    /**
     * Verifies the server certificate from the Noise handshake.
     *
     * <p>Web clients receive a two-level {@code CertChain} (intermediate +
     * leaf), while mobile clients receive a single
     * {@code NoiseCertificate}.  Subclasses implement the appropriate
     * verification logic.
     *
     * @param decryptedCertificate the decrypted certificate payload
     * @param serverStaticKey      the 32-byte server static public key
     * @throws IOException if the certificate is invalid
     */
    abstract void verifyCertificateChain(byte[] decryptedCertificate, byte[] serverStaticKey) throws IOException;

    /**
     * Ensures a signature byte array is exactly 64 bytes, as required by
     * the Ed25519 verification function.
     *
     * @implNote WASignalOther.ensureSize — called with size 64 on certificate
     *     signatures before verification
     * @param signature the raw signature bytes
     * @return the signature, guaranteed to be 64 bytes
     * @throws IOException if the signature is not exactly 64 bytes
     */
    private static byte[] ensureSignatureSize(byte[] signature) throws IOException {
        if (signature.length != 64) {
            throw new IOException("Certificate signature has invalid length: " + signature.length + ", expected 64");
        }
        return signature;
    }

    /**
     * Returns the handshake prologue for the current client type.
     *
     * <p>The returned buffer is read-only to prevent accidental mutation
     * of shared static state.
     *
     * @return a read-only buffer containing the prologue bytes
     */
    abstract byte[] getHandshakePrologue();

    /**
     * Builds the serialized client payload for the Noise handshake.
     *
     * @return the serialized client payload
     */
    abstract byte[] getHandshakePayload();

    /**
     * Serializes a {@link Node} and sends it through the encrypted channel.
     *
     * @implNote ADAPTED: WANoiseSocket.NoiseSocket.sendFrame — WA Web
     *     sends raw binary data; Cobalt adds a node serialization step
     *     before encryption because the noise socket and node encoding
     *     layers are combined
     * @param node the node to send
     * @throws IOException if serialization or sending fails
     */
    public final void sendNode(Node node) throws IOException {
        var encoded = new byte[NodeEncoder.sizeOf(node)];
        var length = NodeEncoder.encode(node, encoded, 0, encoded.length);
        sendBinary(ByteBuffer.wrap(encoded, 0, length));
    }

    /**
     * A listener wrapper that decrypts each inbound datagram, deserializes
     * it into a {@link Node}, and forwards it to the application listener.
     *
     * <p>In WA Web, the {@code NoiseSocket} class sets its {@code onFrame}
     * callback ({@code $12}) on the underlying {@code FrameSocket}, which
     * decrypts each frame using the read key and read counter, then delivers
     * the plaintext to the application-level {@code onFrame} callback
     * ({@code $10}).  This listener mirrors that pipeline: it decrypts
     * inbound datagrams, deserializes them into nodes, and forwards the
     * results to the application listener.
     *
     * @implNote WANoiseSocket.NoiseSocket.$12 and WANoiseSocket.NoiseSocket.$15
     */
    class DecryptingListener implements SocketClientLayerListener {
        /**
         * Decrypts and deserializes an inbound datagram, forwarding
         * each decoded {@link Node} to the application listener.
         *
         * <p>If decryption fails (bad MAC), a
         * {@link WhatsAppSessionException.BadMac} is reported to the
         * listener's error handler and the connection is torn down.
         *
         * @implNote WANoiseSocket.NoiseSocket.$12 — increments read counter,
         *     decrypts via AES-GCM, then delivers plaintext through
         *     {@code $15} to the application {@code $10} callback
         * @param datagram the raw inbound datagram
         */
        @Override
        public void onDatagram(ByteBuffer datagram) {
            try {
                var plaintext = decrypt(datagram);
                if (plaintext == null) {
                    return;
                }

                try(var decoder = NodeDecoder.of(plaintext)) {
                    while (decoder.hasData()) {
                        var node = decoder.decode();
                        listener.onNode(node);
                    }
                }
            } catch (WhatsAppSessionException.BadMac e) {
                listener.onError(e);
                disconnect();
            } catch (Exception e) {
                listener.onError(new WhatsAppStreamException.MalformedNode("Failed to process inbound datagram", e));
            }
        }

        /**
         * Decrypts a datagram using the read cipher and read counter.
         *
         * <p>If the read key is not yet set (pre-handshake), the datagram
         * is returned unchanged.  After the handshake, each datagram is
         * decrypted with AES-GCM using the read key and a nonce derived
         * from the monotonically increasing read counter.
         *
         * @implNote WANoiseSocket.NoiseSocket.$12 — calls
         *     {@code _(readKey, readCounter++, undefined, data)} which
         *     invokes {@code WACryptoDependencies.getCrypto().subtle.decrypt}
         *     with AES-GCM, a 12-byte nonce (8 zero bytes + uint32 counter),
         *     and no additional authenticated data
         * @param datagram the encrypted datagram
         * @return the decrypted plaintext, or {@code null} if the read key
         *     is not yet set
         * @throws WhatsAppSessionException.BadMac if decryption fails due
         *     to an authentication tag mismatch or the datagram is too
         *     short to contain a valid GCM tag
         */
        private ByteBuffer decrypt(ByteBuffer datagram) {
            if (readKey == null) {
                return datagram;
            }

            try {
                readCipher.init(
                        Cipher.DECRYPT_MODE,
                        readKey,
                        GcmUtils.createNonce(readCounter++) // WANoiseSocket.NoiseSocket.$12
                );
                if (datagram.remaining() <= GCM_TAG_BYTE_SIZE) {
                    throw new WhatsAppSessionException.BadMac("Datagram too short for GCM tag");
                }

                if (datagram.isReadOnly()) {
                    throw new ReadOnlyBufferException();
                }

                var start = datagram.position();
                var inputView = datagram.duplicate();
                var outputView = datagram.duplicate();
                outputView.position(start);
                outputView.limit(datagram.limit() - GCM_TAG_BYTE_SIZE);
                var produced = readCipher.doFinal(inputView, outputView);

                var plaintext = datagram.duplicate();
                plaintext.position(start);
                plaintext.limit(start + produced);
                return plaintext;
            } catch (GeneralSecurityException e) {
                throw new WhatsAppSessionException.BadMac("AES-GCM decryption failed", e);
            }
        }

        /**
         * Forwards the close event to the application listener.
         *
         * @implNote WANoiseSocket.NoiseSocket.$13 — in WA Web, the
         *     {@code onClose} handler sets the closing flag, waits for the
         *     read queue to drain, then invokes the application's
         *     {@code onClose} callback.  In Cobalt, the queue drain is
         *     handled by the listener executor shutdown in the WhatsApp
         *     layer context's {@code onDisconnect}.
         */
        @Override
        public void onClose() {
            listener.onClose();
        }
    }

    /**
     * Abstract companion-device WhatsApp socket client.
     *
     * <p>Shared by every client that authenticates as a companion to a
     * primary device — {@link Browser} (WebSocket over TLS) and
     * {@link Desktop} (raw TCP).  All companions share:
     * <ul>
     * <li>The {@code WEB_PROLOGUE} used to prime the Noise handshake.
     * <li>The two-level {@code CertChain} verification against the
     *     WhatsApp root CA.
     * <li>The handshake payload structure — a user agent, a webInfo
     *     block (sub-platform varies per client), and either login
     *     credentials or registration data with companion device props.
     * </ul>
     *
     * <p>Subclasses differ in: endpoint, transport stack, the
     * {@code connectImpl} sequencing relative to {@code finishConnect},
     * and the {@link #getWebSubPlatform()} returned inside the webInfo
     * block.
     */
    static abstract sealed class Web extends WhatsAppSocketClient
            permits Browser, Desktop {
        private static final byte[] WEB_VERSION = new byte[]{6, NodeTokens.DICTIONARY_VERSION};
        private static final byte[] WEB_PROLOGUE = DataUtils.concatByteArrays(WHATSAPP_VERSION_HEADER, WEB_VERSION);

        Web(WhatsAppStore store, WhatsAppSocketClientLayer whatsAppLayer) {
            super(store, whatsAppLayer);
        }

        /**
         * Returns the {@code WebSubPlatform} this client advertises in
         * the webInfo block of the handshake payload.  For a browser
         * this is {@code WEB_BROWSER}; for a native desktop app it's
         * one of {@code DARWIN}, {@code WIN32}, etc.
         *
         * @return the web sub-platform for this client
         */
        abstract ClientPayload.WebInfo.WebSubPlatform getWebSubPlatform();

        @Override
        final byte[] getHandshakePrologue() {
            return WEB_PROLOGUE.clone();
        }

        /**
         * Verifies a two-level certificate chain (intermediate + leaf).
         *
         * @implNote WAVerifyChainCertificateWA6.verifyChainCertificateWA6
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
         * Builds the companion-device handshake payload.
         *
         * @implNote WAWebOpenChatSocket.z — calls either
         *     {@code WAWebGetClientPayloadForLogin.getClientPayloadForLogin}
         *     or {@code WAWebGetClientPayloadForRegistration.getClientPayloadForRegistration}
         *     depending on whether the client is registered
         * @return the serialized client payload
         */
        @Override
        final byte[] getHandshakePayload() {
            var agent = getUserAgent();
            var payload = getClientPayload(agent);
            return ClientPayloadSpec.encode(payload);
        }

        /**
         * Constructs the user agent for the handshake payload.
         *
         * @implNote WAWebClientPayload.y — builds the user agent with
         *     platform, app version, MCC/MNC, release channel, and locale
         * @return the user agent
         */
        private UserAgent getUserAgent() {
            return new ClientPayloadUserAgentBuilder()
                    .platform(store.device().platform())
                    .appVersion(store.clientVersion())
                    .mcc("000")
                    .mnc("000")
                    .releaseChannel(store.releaseChannel())
                    .localeLanguageIso6391("en")
                    .localeCountryIso31661Alpha2("US")
                    .deviceType(ClientPayload.ClientType.PHONE)
                    .deviceModelType(store.device().modelId())
                    .build();
        }

        /**
         * Constructs the companion-device client payload.
         *
         * <p>If a JID is present, builds a reconnection payload.
         * Otherwise, builds a new pairing payload with registration data.
         * The webInfo block's sub-platform is supplied by the subclass
         * via {@link #getWebSubPlatform()}.
         *
         * @implNote WAWebClientPayload.getClientPayloadForLogin and
         *     WAWebClientPayload.getClientPayloadForRegistration — uses
         *     {@code WAWebClientPayload.m} for the common payload fields
         *     including {@code webInfo} with {@code webSubPlatform}
         * @param agent the user agent
         * @return the client payload
         */
        private ClientPayload getClientPayload(UserAgent agent) {
            // WAWebClientPayload.m — common payload fields include webInfo
            var webInfo = new ClientPayloadWebInfoBuilder()
                    .webSubPlatform(getWebSubPlatform())
                    .build();
            var jid = store.jid();
            if (jid.isPresent()) {
                // WAWebClientPayload.getClientPayloadForLogin
                return new ClientPayloadBuilder()
                        .connectType(ClientPayload.ConnectType.WIFI_UNKNOWN)
                        .connectReason(ClientPayload.ConnectReason.USER_ACTIVATED)
                        .userAgent(agent)
                        .webInfo(webInfo) // WAWebClientPayload.m
                        .username(Long.parseLong(jid.get().user()))
                        .passive(true)
                        .pull(true)
                        .device(jid.get().device())
                        .build();
            } else {
                // WAWebClientPayload.getClientPayloadForRegistration
                return new ClientPayloadBuilder()
                        .connectType(ClientPayload.ConnectType.WIFI_UNKNOWN)
                        .connectReason(ClientPayload.ConnectReason.USER_ACTIVATED)
                        .userAgent(agent)
                        .webInfo(webInfo) // WAWebClientPayload.m
                        .devicePairingData(createRegisterData())
                        .passive(false)
                        .pull(false)
                        .build();
            }
        }

        /**
         * Creates the device pairing registration data for a new
         * companion-device session, including device properties.
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
         * <p>Includes the history sync configuration flags that control
         * which types of history data the server should sync to this
         * companion device.  The device {@code platformType} is derived
         * from the store's device platform.
         *
         * @implNote WAWebClientPayload.f — builds the device properties with
         *     historySyncConfig containing all supported sync feature flags
         * @return the encoded companion device properties
         */
        private byte[] createCompanionProps() {
            var historyLength = store.webHistoryPolicy()
                    .orElse(WhatsAppWebClientHistory.standard(true));
            var config = new DevicePropsHistorySyncConfigBuilder()
                    .inlineInitialPayloadInE2EeMsg(true) // WAWebClientPayload.f
                    .supportBotUserAgentChatHistory(true) // WAWebClientPayload.f
                    .supportCagReactionsAndPolls(true) // WAWebClientPayload.f
                    .supportRecentSyncChunkMessageCountTuning(true) // WAWebClientPayload.f
                    .supportHostedGroupMsg(true) // WAWebClientPayload.f
                    .supportBizHostedMsg(true) // WAWebClientPayload.f
                    .supportFbidBotChatHistory(true) // WAWebClientPayload.f
                    .supportMessageAssociation(true) // WAWebClientPayload.f
                    .supportCallLogHistory(store.device().platform() == ClientPlatformType.WINDOWS) // WAWebClientPayload.f: isWindows
                    .supportGroupHistory(true) // WAWebClientPayload.f: gkx("15338")
                    .storageQuotaMb(historyLength.size()) // WAWebClientPayload.f
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
    }

    /**
     * Browser-based WhatsApp companion client.
     *
     * <p>Connects via WebSocket over TLS to {@code web.whatsapp.com:443},
     * performs the HTTP upgrade inside {@link WhatsAppSocketClientLayer#connect},
     * then runs the Noise handshake through the async chain.
     */
    static final class Browser extends Web {
        /**
         * The WebSocket endpoint for browser companions.
         */
        private static final InetSocketAddress WEB_SOCKET_ENDPOINT = new InetSocketAddress("web.whatsapp.com", 443);

        Browser(WhatsAppStore store, WhatsAppSocketClientLayer whatsAppLayer) {
            super(store, whatsAppLayer);
        }

        @Override
        InetSocketAddress getEndpoint() {
            return WEB_SOCKET_ENDPOINT;
        }

        /**
         * For Browser the WebSocket upgrade has already completed inside
         * {@link WhatsAppSocketClientLayer#connect}, so we finish the
         * connection first (transitioning the chain to async mode), then
         * perform the Noise handshake through the async pipeline, then
         * mark the WhatsApp context ready for real datagrams.
         */
        @Override
        void connectImpl() throws IOException {
            whatsAppLayer.finishConnect();
            performNoiseHandshake();
            whatsAppLayer.markHandshakeComplete();
            whatsAppLayer.startListenerExecutor();
        }

        @Override
        ClientPayload.WebInfo.WebSubPlatform getWebSubPlatform() {
            return ClientPayload.WebInfo.WebSubPlatform.WEB_BROWSER;
        }
    }

    /**
     * Native desktop WhatsApp companion client (Windows / macOS).
     *
     * <p>Connects via raw TCP like {@link Mobile} but authenticates as a
     * companion device using the Web payload structure (webInfo block,
     * device-pairing registration data, two-level certificate chain).
     *
     * <p>Unlike {@link Browser}, there is no WebSocket upgrade: Noise
     * runs directly over the transport, so {@code finishConnect} is
     * deferred until after the handshake (same ordering as {@link Mobile}).
     */
    static final class Desktop extends Web {
        /**
         * The TCP endpoint for desktop companions.  Uses the same
         * {@code g.whatsapp.net:443} endpoint as mobile clients.
         */
        private static final InetSocketAddress DESKTOP_ENDPOINT = new InetSocketAddress("g.whatsapp.net", 443);

        Desktop(WhatsAppStore store, WhatsAppSocketClientLayer whatsAppLayer) {
            super(store, whatsAppLayer);
        }

        @Override
        InetSocketAddress getEndpoint() {
            return DESKTOP_ENDPOINT;
        }

        @Override
        void connectImpl() throws IOException {
            performNoiseHandshake();
            whatsAppLayer.markHandshakeComplete();
            whatsAppLayer.startListenerExecutor();
            whatsAppLayer.finishConnect();
        }

        @Override
        ClientPayload.WebInfo.WebSubPlatform getWebSubPlatform() {
            return switch (store.device().platform()) {
                case WINDOWS -> ClientPayload.WebInfo.WebSubPlatform.WIN32;
                case MACOS -> ClientPayload.WebInfo.WebSubPlatform.DARWIN;
                default -> throw new IllegalStateException(
                        "Desktop client does not support platform: " + store.device().platform());
            };
        }
    }

    /**
     * Raw TCP-based WhatsApp socket client for mobile platform connections.
     *
     * <p>Connects via direct TCP, performs the Noise XX handshake through
     * raw transport reads, then transitions to asynchronous mode.
     */
    static final class Mobile extends WhatsAppSocketClient {
        private static final byte[] MOBILE_VERSION = new byte[]{5, NodeTokens.DICTIONARY_VERSION};
        private static final byte[] MOBILE_PROLOGUE = DataUtils.concatByteArrays(WHATSAPP_VERSION_HEADER, MOBILE_VERSION);

        /**
         * The TCP endpoint for mobile connections.
         */
        private static final InetSocketAddress SOCKET_ENDPOINT = new InetSocketAddress("g.whatsapp.net", 443);

        /**
         * Constructs a new mobile socket client.
         *
         * @param store         the WhatsApp store
         * @param whatsAppLayer the WhatsApp application layer (over raw TCP)
         */
        Mobile(WhatsAppStore store, WhatsAppSocketClientLayer whatsAppLayer) {
            super(store, whatsAppLayer);
        }

        @Override
        InetSocketAddress getEndpoint() {
            return SOCKET_ENDPOINT;
        }

        /**
         * For mobile there is no WebSocket upgrade: the Noise handshake
         * runs over the still-pre-tunnel transport (so handshake reads
         * can use the blocking-read path), and only after the handshake
         * succeeds do we start the listener executor and transition the
         * chain to async mode via {@link WhatsAppSocketClientLayer#finishConnect}.
         */
        @Override
        void connectImpl() throws IOException {
            performNoiseHandshake();
            whatsAppLayer.markHandshakeComplete();
            whatsAppLayer.startListenerExecutor();
            whatsAppLayer.finishConnect();
        }

        /**
         * Verifies a single {@code NoiseCertificate} from the mobile handshake.
         *
         * <p>The mobile server sends a flat {@code NoiseCertificate} (not a
         * two-level {@code CertChain}).  The certificate is verified against
         * the root CA public key, and its embedded key is compared with the
         * server static key from the handshake.
         *
         * @implNote Mobile equivalent of WAVerifyChainCertificateWA6 — single-level verification
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
         * Returns the mobile handshake prologue.
         *
         * @return a copy of the mobile prologue
         */
        @Override
        byte[] getHandshakePrologue() {
            return MOBILE_PROLOGUE.clone();
        }

        /**
         * Builds the mobile client handshake payload.
         *
         * @return the serialized client payload
         */
        @Override
        byte[] getHandshakePayload() {
            var agent = getUserAgent();
            var payload = getClientPayload(agent);
            return ClientPayloadSpec.encode(payload);
        }

        /**
         * Constructs the user agent for the mobile handshake payload,
         * including device-specific fields.
         *
         * @return the user agent
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
         * Constructs the mobile client payload.
         *
         * @param agent the user agent
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
