package com.github.auties00.cobalt.socket.security.whatsapp;

import com.github.auties00.cobalt.client.WhatsAppClientType;
import com.github.auties00.cobalt.client.WhatsAppWebClientHistory;
import com.github.auties00.cobalt.model.device.*;
import com.github.auties00.cobalt.model.device.pairing.*;
import com.github.auties00.cobalt.model.device.pairing.ClientPayload.DevicePairingRegistrationData;
import com.github.auties00.cobalt.model.device.pairing.ClientPayload.UserAgent;
import com.github.auties00.cobalt.node.binary.NodeTokens;
import com.github.auties00.cobalt.socket.SocketClientLayer;
import com.github.auties00.cobalt.socket.SocketClientLayerListener;
import com.github.auties00.cobalt.socket.application.SocketClientApplicationLayer;
import com.github.auties00.cobalt.socket.security.SocketClientTransportSecurityLayer;
import com.github.auties00.cobalt.socket.threading.SocketClientContext;
import com.github.auties00.cobalt.socket.threading.SocketClientSelector;
import com.github.auties00.cobalt.socket.tunnel.SocketClientTunnelLayer;
import com.github.auties00.cobalt.socket.tunnel.TunnelLayerContext;
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
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;

/**
 * A security layer that provides WhatsApp Noise XX encryption over an
 * existing connection.
 *
 * <p>This layer wraps an inner layer and adds Curve25519-based Noise XX
 * key exchange followed by AES-GCM authenticated encryption.  The
 * handshake derives separate read and write keys used for subsequent
 * message encryption and decryption.
 *
 * <p>Unlike TLS, Noise encryption and decryption happen at
 * {@code sendBinary()} call time on the caller's thread (not on the
 * selector thread).  Inbound decryption is performed in a listener
 * wrapper that intercepts assembled datagrams from the
 * {@link WhatsAppLayerContext}.
 *
 * <p>Outbound messages are encrypted and framed with a 3-byte int24
 * length prefix (of the ciphertext length) before being passed to the
 * inner layer.  The {@link WhatsAppLayerContext} on the inbound side
 * reassembles these int24-framed datagrams and delivers the encrypted
 * payload to the listener wrapper for decryption.
 *
 * <p>The {@link #init(SocketChannel, SocketClientContext)} method must
 * be called after the inner layer has connected and before
 * {@link #startHandshake()} is invoked.
 */
public final class WhatsAppSocketClientSecurityLayer implements SocketClientTransportSecurityLayer {
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
     * The inner layer that provides raw I/O.
     */
    private final SocketClientLayer innerLayer;

    /**
     * The WhatsApp store containing session state, keys, and configuration.
     */
    private final WhatsAppStore store;

    /**
     * The NIO socket channel, set by {@link #init(SocketChannel, SocketClientContext)}.
     */
    private SocketChannel channel;

    /**
     * The per-connection context, set by {@link #init(SocketChannel, SocketClientContext)}.
     */
    private SocketClientContext context;

    /**
     * The original listener to receive decrypted datagrams and close events.
     */
    private SocketClientLayerListener listener;

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

    /**
     * Creates a WhatsApp security layer wrapping the given inner layer.
     *
     * @param innerLayer the layer below (transport or tunnel)
     * @param store      the session store containing keys and configuration
     */
    public WhatsAppSocketClientSecurityLayer(SocketClientLayer innerLayer, WhatsAppStore store) {
        this.innerLayer = innerLayer;
        this.store = store;
    }

    /**
     * Initializes this layer with the NIO channel and connection context.
     *
     * <p>This method must be called after the inner layer has connected
     * and before {@link #startHandshake()}.  It registers the layer
     * contexts needed for the handshake (a {@link TunnelLayerContext} for
     * blocking reads) and for post-handshake data flow (an
     * {@link WhatsAppLayerContext} for int24 datagram reassembly with
     * decryption).
     *
     * @param channel the NIO socket channel
     * @param context the per-connection context
     */
    public void init(SocketChannel channel, SocketClientContext context) {
        this.channel = channel;
        this.context = context;
    }

    @Override
    public void connect(InetSocketAddress address, SocketClientLayerListener listener) throws IOException {
        this.listener = listener;
        innerLayer.connect(address, listener);
    }

    /**
     * Performs the Noise XX handshake over the inner layer.
     *
     * <p>The handshake consists of three messages:
     * <ol>
     * <li><b>Client hello</b>: prologue + ephemeral public key
     * <li><b>Server hello</b>: ephemeral key, static key (encrypted),
     *     payload (encrypted)
     * <li><b>Client finish</b>: static key (encrypted), client payload
     *     (encrypted)
     * </ol>
     *
     * <p>After the handshake completes, this method derives the read and
     * write AES-GCM keys, registers the {@link WhatsAppLayerContext}
     * with a decrypting listener wrapper, and marks the connection as
     * ready for application data.
     *
     * @throws IOException if the handshake fails or times out
     */
    @Override
    public void startHandshake() throws IOException {
        if (channel == null || context == null) {
            throw new IOException("WhatsApp security layer not initialized: call init() first");
        }

        if (listener == null) {
            throw new IOException("WhatsApp security layer not connected: call connect() first");
        }

        // Register layer contexts for handshake reads and post-handshake data flow
        var decryptingListener = new Listener();
        var appContext = new WhatsAppLayerContext(decryptingListener);
        var gatingContext = new TunnelLayerContext(appContext, false);
        context.createLayerContext(SocketClientTunnelLayer.class, gatingContext);
        context.createLayerContext(SocketClientApplicationLayer.class, appContext);

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
            var serverHelloPayload = readHandshakeMessage();

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

        // Mark connection ready for application data
        if (!SocketClientSelector.INSTANCE.markReady(channel)) {
            throw new IOException("Failed to mark connection as ready after handshake");
        }
    }

    /**
     * Sends a handshake message with an optional prologue prefix and a
     * 3-byte int24 length header using scatter-gather I/O to avoid
     * copying into a single buffer.
     *
     * @param prologue     the prologue to prepend, or {@code null} for none
     * @param messageBytes the serialized handshake message
     */
    private void sendHandshakeMessage(byte[] prologue, byte[] messageBytes) throws IOException {
        var lengthPrefix = ByteBuffer.allocate(INT24_BYTE_SIZE);
        var len = messageBytes.length;
        lengthPrefix.put((byte) ((len >> 16) & 0xFF));
        lengthPrefix.put((byte) ((len >> 8) & 0xFF));
        lengthPrefix.put((byte) (len & 0xFF));
        lengthPrefix.flip();
        if (prologue != null) {
            innerLayer.sendBinary(ByteBuffer.wrap(prologue), lengthPrefix, ByteBuffer.wrap(messageBytes));
        } else {
            innerLayer.sendBinary(lengthPrefix, ByteBuffer.wrap(messageBytes));
        }
    }

    /**
     * Reads a complete handshake message from the inner layer.
     *
     * <p>Reads a 3-byte int24 length prefix followed by the message
     * payload.  Uses {@code readBinary()} which blocks on the selector
     * via the {@link TunnelLayerContext}'s pending read mechanism.
     *
     * @return the message payload as a {@link ByteBuffer} in read mode
     * @throws IOException if the read fails or yields an invalid length
     */
    private ByteBuffer readHandshakeMessage() throws IOException {
        var lengthBuf = ByteBuffer.allocate(INT24_BYTE_SIZE);
        var bytesRead = innerLayer.readBinary(lengthBuf, true);
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
        bytesRead = innerLayer.readBinary(payloadBuf, true);
        if (bytesRead < length) {
            throw new IOException("Failed to read handshake message payload");
        }

        payloadBuf.flip();
        return payloadBuf;
    }

    @Override
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

        innerLayer.disconnect();
    }

    @Override
    public boolean isConnected() {
        return innerLayer.isConnected();
    }

    /**
     * Encrypts the given plaintext buffers with AES-GCM and sends the
     * result through the inner layer, prefixed with a 3-byte int24
     * ciphertext length header.
     *
     * <p>Before the handshake completes (no write key), buffers are
     * passed through without encryption.
     *
     * <p>This method is synchronized to ensure the write counter stays
     * consistent across concurrent callers.
     *
     * @param buffers the plaintext buffers to encrypt and send
     */
    @Override
    public synchronized void sendBinary(ByteBuffer... buffers) throws IOException {
        if (writeKey == null) {
            innerLayer.sendBinary(buffers);
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

            innerLayer.sendBinary(output);
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

    @Override
    public int readBinary(ByteBuffer buffer, boolean fully) throws IOException {
        return innerLayer.readBinary(buffer, fully);
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
     * Constructs the web client payload, including session resumption
     * data if a JID is available.
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
     * Creates a listener wrapper that decrypts each inbound datagram
     * before forwarding to the original listener.
     *
     * <p>Decryption uses the read AES-GCM key with an incrementing nonce
     * counter.  The listener executor in {@link WhatsAppLayerContext}
     * serializes calls, so no synchronization is needed for the read
     */
    private class Listener implements SocketClientLayerListener {
        @Override
        public void onDatagram(ByteBuffer datagram) {
            if (readKey == null) {
                listener.onDatagram(datagram);
                return;
            }

            try {
                readCipher.init(
                        Cipher.DECRYPT_MODE,
                        readKey,
                        GcmUtils.createNonce(readCounter++)
                );
                if (datagram.remaining() <= GCM_TAG_BYTE_SIZE) {
                    disconnect();
                    return;
                }

                if (datagram.isReadOnly()) {
                    var output = ByteBuffer.allocate(datagram.remaining() - GCM_TAG_BYTE_SIZE);
                    readCipher.doFinal(datagram.duplicate(), output);
                    output.flip();
                    listener.onDatagram(output);
                    return;
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
                listener.onDatagram(plaintext);
            } catch (GeneralSecurityException e) {
                disconnect();
            }
        }

        @Override
        public void onClose() {
            listener.onClose();
        }
    }
}
