package com.github.auties00.cobalt.socket;

import com.github.auties00.cobalt.client.WhatsAppClientProxy;
import com.github.auties00.cobalt.client.WhatsAppClientType;
import com.github.auties00.cobalt.client.WhatsAppWebClientHistory;
import com.github.auties00.cobalt.exception.WhatsAppStreamException;
import com.github.auties00.cobalt.model.device.*;
import com.github.auties00.cobalt.model.device.pairing.*;
import com.github.auties00.cobalt.model.device.pairing.ClientPayload.DevicePairingRegistrationData;
import com.github.auties00.cobalt.model.device.pairing.ClientPayload.UserAgent;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.binary.NodeDecoder;
import com.github.auties00.cobalt.node.binary.NodeEncoder;
import com.github.auties00.cobalt.node.binary.NodeTokens;
import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.SocketClientLayerListener;
import com.github.auties00.cobalt.socket.layer.application.websocket.WebSocketClient;
import com.github.auties00.cobalt.socket.layer.security.SocketClientTransportSecurityLayer;
import com.github.auties00.cobalt.socket.layer.security.SocketClientTunnelSecurityLayer;
import com.github.auties00.cobalt.socket.layer.transport.SocketClientTransportLayer;
import com.github.auties00.cobalt.socket.layer.transport.SocketClientTransportLayerContext;
import com.github.auties00.cobalt.socket.layer.tunnel.SocketClientTunnelLayer;
import com.github.auties00.cobalt.socket.layer.tunnel.TunnelLayerContext;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.util.FastRandomUtils;
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
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.Objects;

/**
 * A standalone WhatsApp socket client that provides Noise XX encryption
 * and int24-framed datagram transport over either a WebSocket connection
 * (WEB) or a raw TCP connection (mobile).
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
 *
 * <p>The Noise XX handshake derives separate read and write AES-GCM keys.
 * Outbound messages are encrypted at {@code sendBinary()} call time.
 * Inbound decryption is performed through a listener wrapper that
 * intercepts assembled datagrams from the {@link WhatsAppLayerContext}.
 */
public final class WhatsAppSocketClient {
    private static final InetSocketAddress SOCKET_ENDPOINT = InetSocketAddress.createUnresolved("g.whatsapp.net", 443);
    private static final URI WEB_SOCKET_ENDPOINT = URI.create("wss://web.whatsapp.com/ws/chat");
    private static final byte[] WHATSAPP_VERSION_HEADER = "WA".getBytes(StandardCharsets.UTF_8);
    private static final byte[] WEB_VERSION = new byte[]{6, NodeTokens.DICTIONARY_VERSION};
    private static final byte[] WEB_PROLOGUE = FastRandomUtils.concatByteArrays(WHATSAPP_VERSION_HEADER, WEB_VERSION);
    private static final byte[] MOBILE_VERSION = new byte[]{5, NodeTokens.DICTIONARY_VERSION};
    private static final byte[] MOBILE_PROLOGUE = FastRandomUtils.concatByteArrays(WHATSAPP_VERSION_HEADER, MOBILE_VERSION);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int INT24_BYTE_SIZE = 3;
    private static final int GCM_TAG_BYTE_SIZE = 16;
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);
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
        Objects.requireNonNull(store, "store cannot be null");

        var transport = createTransport(store);
        var tunnel = createTunnel(transport, store);

        if (store.device().platform() == ClientPlatformType.WEB) {
            var webSocketClient = new WebSocketClient(tunnel);
            return new WhatsAppSocketClient(store, null, webSocketClient);
        } else {
            return new WhatsAppSocketClient(store, tunnel, null);
        }
    }

    private static SocketClientLayer createTransport(WhatsAppStore store) {
        var transport = SocketClientTransportLayer.ofTcp();
        if (store.device().platform() == ClientPlatformType.WEB) {
            return SocketClientTransportSecurityLayer.ofTls(transport);
        } else {
            return transport;
        }
    }

    private static SocketClientLayer createTunnel(SocketClientLayer transport, WhatsAppStore store) {
        return switch (store.proxy().orElse(null)) {
            case WhatsAppClientProxy.Http http -> {
                var httpTunnel = SocketClientTunnelLayer.ofHttp(http, transport);
                yield switch (http) {
                    case WhatsAppClientProxy.Http.Plain _ -> httpTunnel;
                    case WhatsAppClientProxy.Http.Secure secure -> SocketClientTunnelSecurityLayer.ofTls(httpTunnel);
                };
            }
            case WhatsAppClientProxy.Socks socks -> SocketClientTunnelLayer.ofSocks(socks, transport);
            case null -> transport;
        };
    }

    private final WhatsAppStore store;

    /**
     * The raw transport layer (mobile path only).
     */
    private final SocketClientLayer mobileLayer;

    /**
     * The WebSocket client (WEB path only).
     */
    private final WebSocketClient webSocketClient;

    /**
     * The original listener to receive deserialized nodes and close events.
     */
    private WhatsAppSocketListener listener;

    /**
     * The AES-GCM cipher used for encrypting outbound messages.
     */
    private volatile Cipher writeCipher;

    /**
     * The AES write key derived from the Noise handshake.
     */
    private volatile SecretKeySpec writeKey;

    /**
     * The write nonce counter, incremented for each outbound message.
     */
    private long writeCounter;

    /**
     * The AES-GCM cipher used for decrypting inbound messages.
     */
    private volatile Cipher readCipher;

    /**
     * The AES read key derived from the Noise handshake.
     */
    private volatile SecretKeySpec readKey;

    /**
     * The read nonce counter, incremented for each inbound datagram.
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

    private WhatsAppSocketClient(WhatsAppStore store, SocketClientLayer mobileLayer, WebSocketClient webSocketClient) {
        this.store = store;
        this.mobileLayer = mobileLayer;
        this.webSocketClient = webSocketClient;
    }

    /**
     * Connects and performs the Noise XX handshake.
     *
     * <p>For WEB clients, performs the WebSocket upgrade first, then the
     * Noise handshake over WebSocket frames.  For mobile clients, connects
     * the raw TCP transport and performs the Noise handshake directly.
     *
     * <p>After the handshake completes, the connection transitions to
     * asynchronous mode and the listener receives deserialized nodes.
     *
     * @param listener the callback for deserialized nodes and close events
     * @throws IOException if the connection or handshake fails
     */
    public void connect(WhatsAppSocketListener listener) throws IOException {
        Objects.requireNonNull(listener, "listener cannot be null");
        this.listener = listener;

        var decryptingListener = new DecryptingListener();
        var appContext = new WhatsAppLayerContext(decryptingListener);

        if (webSocketClient != null) {
            // WEB path: WebSocket upgrade (includes finishConnect),
            // then Noise handshake via appContext blocking reads, then async
            webSocketClient.connect(WEB_SOCKET_ENDPOINT, appContext, decryptingListener);
            performNoiseHandshake(appContext);
            appContext.markHandshakeComplete();
            appContext.startListenerExecutor();
        } else {
            // Mobile path: direct TCP, then Noise handshake, then async
            var gatingContext = new TunnelLayerContext(appContext, false);
            mobileLayer.registerLayerContext(SocketClientTunnelLayer.class, gatingContext);
            mobileLayer.connect(SOCKET_ENDPOINT, decryptingListener);
            performNoiseHandshake(null);
            appContext.startListenerExecutor();
            mobileLayer.finishConnect();
        }
    }

    /**
     * Disconnects and destroys cipher keys.
     */
    public void disconnect() {
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

        if (webSocketClient != null) {
            webSocketClient.disconnect();
        } else if (mobileLayer != null) {
            mobileLayer.disconnect();
        }
    }

    /**
     * Returns whether the connection is active.
     *
     * @return {@code true} if connected
     */
    public boolean isConnected() {
        if (webSocketClient != null) {
            return webSocketClient.isConnected();
        } else if (mobileLayer != null) {
            return mobileLayer.isConnected();
        }
        return false;
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
     * @param buffers the plaintext buffers to encrypt and send
     * @throws IOException if the write fails
     */
    public synchronized void sendBinary(ByteBuffer... buffers) throws IOException {
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
            var finalProduced = writeCipher.doFinal(EMPTY_BUFFER, reusableFinalChunk);
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
     * Sends raw buffers through the underlying transport (WebSocket or TCP).
     *
     * @param buffers the buffers to send
     * @throws IOException if the write fails
     */
    private void sendRaw(ByteBuffer... buffers) throws IOException {
        if (webSocketClient != null) {
            webSocketClient.sendBinary(buffers);
        } else {
            mobileLayer.sendBinary(buffers);
        }
    }

    /**
     * Reads raw bytes from the underlying transport.
     *
     * @param buffer the destination buffer
     * @param fully  {@code true} to fill the buffer completely
     * @return bytes read, or {@code -1} on end-of-stream
     * @throws IOException if reading fails
     */
    private int readRaw(ByteBuffer buffer, boolean fully) throws IOException {
        if (webSocketClient != null) {
            return webSocketClient.readBinary(buffer, fully);
        } else {
            return mobileLayer.readBinary(buffer, fully);
        }
    }

    /**
     * Performs the Noise XX handshake.
     *
     * <p>If {@code appContext} is non-null (WEB path), handshake reads
     * go through the layer context's blocking read mechanism.  Otherwise
     * (mobile path), reads go through the raw transport.
     *
     * @param appContext the WhatsApp layer context for blocking reads,
     *                   or {@code null} for raw transport reads
     * @throws IOException if the handshake fails
     */
    private void performNoiseHandshake(WhatsAppLayerContext appContext) throws IOException {
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
            sendHandshakeMessage(prologue, requestBytes);

            // Read server hello
            var serverHelloPayload = readHandshakeMessage(appContext);

            // Process server hello
            var serverHandshake = HandshakeMessageSpec.decode(ProtobufInputStream.fromBuffer(serverHelloPayload));
            var serverHello = serverHandshake.serverHello()
                    .orElseThrow(() -> new IOException("Missing server hello"));

            handshake.updateHash(ephemeralKeyPair.publicKey().toEncodedPoint());

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
            handshake.cipher(payload, false);

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
    private void sendHandshakeMessage(byte[] prologue, byte[] messageBytes) throws IOException {
        var lengthPrefix = ByteBuffer.allocate(INT24_BYTE_SIZE);
        var len = messageBytes.length;
        lengthPrefix.put((byte) ((len >> 16) & 0xFF));
        lengthPrefix.put((byte) ((len >> 8) & 0xFF));
        lengthPrefix.put((byte) (len & 0xFF));
        lengthPrefix.flip();
        if (prologue != null) {
            sendRaw(ByteBuffer.wrap(prologue), lengthPrefix, ByteBuffer.wrap(messageBytes));
        } else {
            sendRaw(lengthPrefix, ByteBuffer.wrap(messageBytes));
        }
    }

    /**
     * Reads a complete handshake message.
     *
     * <p>If {@code appContext} is non-null, reads go through the layer
     * context's blocking read mechanism.  Otherwise reads go through
     * the raw transport.
     *
     * @param appContext the WhatsApp layer context for blocking reads,
     *                   or {@code null} for raw transport reads
     * @return the message payload as a {@link ByteBuffer} in read mode
     * @throws IOException if the read fails or yields an invalid length
     */
    private ByteBuffer readHandshakeMessage(WhatsAppLayerContext appContext) throws IOException {
        var lengthBuf = ByteBuffer.allocate(INT24_BYTE_SIZE);
        var bytesRead = readHandshakeRaw(lengthBuf, true, appContext);
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
        bytesRead = readHandshakeRaw(payloadBuf, true, appContext);
        if (bytesRead < length) {
            throw new IOException("Failed to read handshake message payload");
        }

        payloadBuf.flip();
        return payloadBuf;
    }

    /**
     * Reads raw bytes during the handshake phase.
     *
     * <p>If {@code appContext} is non-null (WEB path), posts a pending
     * read to the layer context and blocks until the selector fulfills
     * it through the layer context chain.  Otherwise (mobile path),
     * reads directly from the raw transport.
     *
     * @param buffer     the destination buffer
     * @param fully      {@code true} to fill the buffer completely
     * @param appContext the layer context for blocking reads, or
     *                   {@code null} for raw transport reads
     * @return bytes read, or {@code -1} on end-of-stream
     * @throws IOException if reading fails
     */
    private int readHandshakeRaw(ByteBuffer buffer, boolean fully, WhatsAppLayerContext appContext) throws IOException {
        if (appContext != null) {
            var read = new SocketClientTransportLayerContext.PendingRead(buffer, fully);
            if (!appContext.setPendingRead(read)) {
                throw new IOException("Failed to post handshake read: another read is pending");
            }
            synchronized (read.lock) {
                while (read.length == -1 || (fully && read.length >= 0 && read.buffer.hasRemaining())) {
                    try {
                        read.lock.wait(30_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Handshake read interrupted", e);
                    }
                }
            }
            return read.length;
        }
        return readRaw(buffer, fully);
    }

    /**
     * Returns the handshake prologue bytes for the current client type.
     *
     * @return the prologue bytes
     */
    private byte[] getHandshakePrologue() {
        return switch (store.clientType()) {
            case WEB -> WEB_PROLOGUE;
            case MOBILE -> MOBILE_PROLOGUE;
        };
    }

    /**
     * Builds the client payload for the Noise handshake.
     *
     * @return the serialized client payload
     */
    private byte[] getHandshakePayload() {
        var agent = getUserAgent();
        var payload = getClientPayload(agent);
        return ClientPayloadSpec.encode(payload);
    }

    /**
     * Constructs the client payload based on the client type.
     *
     * @param agent the user agent
     * @return the client payload
     */
    private ClientPayload getClientPayload(UserAgent agent) {
        return switch (store.clientType()) {
            case WEB -> getWebClientPayload(agent);
            case MOBILE -> getMobileClientPayload(agent);
        };
    }

    /**
     * Constructs the web client payload.
     *
     * @param agent the user agent
     * @return the web client payload
     */
    private ClientPayload getWebClientPayload(UserAgent agent) {
        var jid = store.jid();
        if (jid.isPresent()) {
            return new ClientPayloadBuilder()
                    .connectType(ClientPayload.ConnectType.WIFI_UNKNOWN)
                    .connectReason(ClientPayload.ConnectReason.USER_ACTIVATED)
                    .userAgent(agent)
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
                    .devicePairingData(createRegisterData())
                    .passive(false)
                    .pull(false)
                    .build();
        }
    }

    /**
     * Constructs the mobile client payload.
     *
     * @param agent the user agent
     * @return the mobile client payload
     */
    private ClientPayload getMobileClientPayload(UserAgent agent) {
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

    /**
     * Constructs the user agent for the handshake payload.
     *
     * @return the user agent
     */
    private UserAgent getUserAgent() {
        var mobile = store.clientType() == WhatsAppClientType.MOBILE;
        return new ClientPayloadUserAgentBuilder()
                .platform(store.device().platform())
                .appVersion(store.clientVersion())
                .mcc("000")
                .mnc("000")
                .osVersion(mobile ? store.device().osDeviceAppVersion().toString() : null)
                .manufacturer(mobile ? store.device().manufacturer() : null)
                .device(mobile ? store.device().model().replaceAll("_", " ") : null)
                .osBuildNumber(mobile ? store.device().osBuildNumber() : null)
                .phoneId(mobile ? store.fdid().toString().toUpperCase() : null)
                .releaseChannel(store.releaseChannel())
                .localeLanguageIso6391("en")
                .localeCountryIso31661Alpha2("US")
                .deviceType(ClientPayload.ClientType.PHONE)
                .deviceModelType(store.device().modelId())
                .build();
    }

    /**
     * Creates the device pairing registration data for new sessions.
     *
     * @return the registration data
     */
    private DevicePairingRegistrationData createRegisterData() {
        var companion = new ClientPayloadDevicePairingRegistrationDataBuilder()
                .buildHash(store.clientVersion().toHash())
                .eRegid(FastRandomUtils.intToBytes(store.registrationId(), 4))
                .eKeytype(FastRandomUtils.intToBytes(SignalIdentityPublicKey.type(), 1))
                .eIdent(store.identityKeyPair().publicKey().toEncodedPoint())
                .eSkeyId(FastRandomUtils.intToBytes(store.signedKeyPair().id(), 3))
                .eSkeyVal(store.signedKeyPair().publicKey().toEncodedPoint())
                .eSkeySig(store.signedKeyPair().signature());
        if (store.clientType() == WhatsAppClientType.WEB) {
            var props = createCompanionProps();
            var encodedProps = props == null ? null : DevicePropsSpec.encode(props);
            companion.deviceProps(encodedProps);
        }

        return companion.build();
    }

    /**
     * Creates the companion device properties for web clients.
     *
     * @return the device properties, or {@code null} for mobile clients
     */
    private DeviceProps createCompanionProps() {
        return switch (store.clientType()) {
            case WEB -> {
                var historyLength = store.webHistoryPolicy()
                        .orElse(WhatsAppWebClientHistory.standard(true));
                var config = new DevicePropsHistorySyncConfigBuilder()
                        .inlineInitialPayloadInE2EeMsg(true)
                        .supportBotUserAgentChatHistory(true)
                        .supportCallLogHistory(true)
                        .storageQuotaMb(historyLength.size())
                        .fullSyncSizeMbLimit(historyLength.size())
                        .build();
                var platformType = switch (store.device().platform()) {
                    case IOS, IOS_BUSINESS -> DevicePlatformType.IOS_PHONE;
                    case ANDROID, ANDROID_BUSINESS -> DevicePlatformType.ANDROID_PHONE;
                    case WINDOWS -> DevicePlatformType.UWP;
                    case MACOS -> DevicePlatformType.IOS_CATALYST;
                    default -> throw new IllegalStateException("Unexpected value: " + store.device().platform());
                };
                yield new DevicePropsBuilder()
                        .os(store.name())
                        .platformType(platformType)
                        .requireFullSync(historyLength.isExtended())
                        .historySyncConfig(config)
                        .version(store.clientVersion())
                        .build();
            }
            case MOBILE -> null;
        };
    }

    /**
     * Serializes a {@link Node} and sends it through the encrypted channel.
     *
     * @param node the node to send
     * @throws IOException if serialization or sending fails
     */
    public void sendNode(Node node) throws IOException {
        var encoded = new byte[NodeEncoder.sizeOf(node)];
        var length = NodeEncoder.encode(node, encoded, 0, encoded.length);
        sendBinary(ByteBuffer.wrap(encoded, 0, length));
    }

    /**
     * A listener wrapper that decrypts each inbound datagram, deserializes
     * it into a {@link Node}, and forwards it to the application listener.
     */
    private class DecryptingListener implements SocketClientLayerListener {
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
            } catch (Exception e) {
                listener.onError(new WhatsAppStreamException.MalformedNode("Failed to process inbound datagram", e));
            }
        }

        private ByteBuffer decrypt(ByteBuffer datagram) {
            if (readKey == null) {
                return datagram;
            }

            try {
                readCipher.init(
                        Cipher.DECRYPT_MODE,
                        readKey,
                        GcmUtils.createNonce(readCounter++)
                );
                if (datagram.remaining() <= GCM_TAG_BYTE_SIZE) {
                    disconnect();
                    return null;
                }

                if (datagram.isReadOnly()) {
                    var output = ByteBuffer.allocate(datagram.remaining() - GCM_TAG_BYTE_SIZE);
                    readCipher.doFinal(datagram.duplicate(), output);
                    output.flip();
                    return output;
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
                disconnect();
                return null;
            }
        }

        @Override
        public void onClose() {
            listener.onClose();
        }
    }
}
