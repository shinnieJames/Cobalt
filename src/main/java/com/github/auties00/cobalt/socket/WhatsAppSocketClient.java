package com.github.auties00.cobalt.socket;

import com.github.auties00.cobalt.client.WhatsAppClientType;
import com.github.auties00.cobalt.exception.WhatsAppSessionException;
import com.github.auties00.cobalt.client.WhatsAppWebClientHistory;
import com.github.auties00.cobalt.exception.WhatsAppStreamException;
import com.github.auties00.cobalt.model.auth.*;
import com.github.auties00.cobalt.model.sync.HistorySyncConfigBuilder;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.binary.NodeDecoder;
import com.github.auties00.cobalt.node.binary.NodeEncoder;
import com.github.auties00.cobalt.node.binary.NodeTokens;
import com.github.auties00.cobalt.socket.implementation.SocketClient;
import com.github.auties00.cobalt.socket.implementation.SocketListener;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.util.GcmUtils;
import com.github.auties00.cobalt.util.SecureBytes;
import com.github.auties00.curve25519.Curve25519;
import com.github.auties00.libsignal.key.SignalIdentityKeyPair;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import com.github.auties00.libsignal.key.SignalKeyPair;
import it.auties.protobuf.stream.ProtobufInputStream;
import it.auties.protobuf.stream.ProtobufOutputStream;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.DestroyFailedException;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

// TODO: Better exceptions
public final class WhatsAppSocketClient {
    private static final String HOST_NAME = "g.whatsapp.net";
    private static final int PORT = 443;

    private static final byte[] WHATSAPP_VERSION_HEADER = "WA".getBytes(StandardCharsets.UTF_8);
    private static final byte[] WEB_VERSION = new byte[]{6, NodeTokens.DICTIONARY_VERSION};
    private static final byte[] WEB_PROLOGUE = SecureBytes.concat(WHATSAPP_VERSION_HEADER, WEB_VERSION);
    private static final byte[] MOBILE_VERSION = new byte[]{5, NodeTokens.DICTIONARY_VERSION};
    private static final byte[] MOBILE_PROLOGUE = SecureBytes.concat(WHATSAPP_VERSION_HEADER, MOBILE_VERSION);
    private static final int HEADER_LENGTH = Integer.BYTES + Short.BYTES;
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final VarHandle INT_HANDLE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle SHORT_HANDLE = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);
    private static final int UNSIGNED_INT16_MAX_VALUE = 0xFFFF;

    private static final int DEFAULT_CONNECT_TIMEOUT = 30_000;

    private final SocketClient socketClient;
    private final WhatsAppStore store;
    private final AtomicReference<State> state;

    private SignalKeyPair handshakeEphemeralKeyPair;
    private byte[] handshakePrologue;

    private Cipher readCipher;
    private SecretKeySpec readKey;
    private long readCounter;

    private Cipher writeCipher;
    private SecretKeySpec writeKey;
    private long writeCounter;

    private volatile WhatsAppSocketListener listener;
    private volatile CompletableFuture<?> handshakeFuture;

    private WhatsAppSocketClient(SocketClient socketClient, WhatsAppStore store) {
        this.socketClient = socketClient;
        this.store = store;
        this.state = new AtomicReference<>(State.DISCONNECTED);
    }

    public static WhatsAppSocketClient newCipheredSocketClient(WhatsAppStore store) {
        Objects.requireNonNull(store, "store");
        var proxy = store.proxy().orElse(null);
        var plainSocketClient = SocketClient.newPlainSocketClient(proxy);
        return new WhatsAppSocketClient(plainSocketClient, store);
    }

    public void connect(WhatsAppSocketListener listener) throws IOException, InterruptedException {
        if(!state.compareAndSet(State.DISCONNECTED, State.CONNECTING)) {
            throw new IllegalStateException("Socket is already connecting or connected");
        }

        this.listener = listener;

        try {
            socketClient.connect(HOST_NAME, PORT, new SocketListener() {
                @Override
                public void onDatagram(ByteBuffer buffer) {
                    onMessage(buffer);
                }

                @Override
                public void onClose() {
                    disconnect(false);
                }
            });
        } catch (IOException | InterruptedException ex) {
            disconnect(false);
            throw ex;
        }

        if(!state.compareAndSet(State.CONNECTING, State.HANDSHAKE_START)) {
            return;
        }

        this.handshakeFuture = new CompletableFuture<>();

        var ephemeralKeyPair = SignalIdentityKeyPair.random();
        var prologue = getHandshakePrologue();
        handshakeEphemeralKeyPair = ephemeralKeyPair;
        handshakePrologue = prologue;
        var clientHello = new ClientHelloBuilder()
                .ephemeral(ephemeralKeyPair.publicKey().toEncodedPoint())
                .build();
        var handshakeMessage = new HandshakeMessageBuilder()
                .clientHello(clientHello)
                .build();
        var requestLength = HandshakeMessageSpec.sizeOf(handshakeMessage);
        var message = new byte[prologue.length + HEADER_LENGTH + requestLength];
        System.arraycopy(prologue, 0, message, 0, prologue.length);
        var offset = writeRequestHeader(requestLength, message, prologue.length);
        HandshakeMessageSpec.encode(handshakeMessage, ProtobufOutputStream.toBytes(message, offset));
        socketClient.sendBinary(ByteBuffer.wrap(message));

        try {
            handshakeFuture
                    .orTimeout(DEFAULT_CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
                    .join();
        } catch (CompletionException completionException) {
            disconnect(false);
            var cause = completionException.getCause();
            if (cause instanceof IOException io) {
                throw io;
            } else if (cause != null) {
                throw new IOException(cause.getMessage(), cause);
            } else {
                throw new IOException("Handshake failed", completionException);
            }
        } catch (CancellationException cancellationException) {
            disconnect(false);
            throw new IOException("Handshake cancelled", cancellationException);
        }
    }

    private void onMessage(ByteBuffer buffer) {
        if(state.compareAndSet(State.HANDSHAKE_START, State.HANDSHAKE_FINISH)) {
            finishHandshake(buffer);
        } else {
            decryptNodes(buffer);
        }
    }

    // Sending multiple binaries at the same time can cause
    // the GCM counter to go out of sync for the server
    public synchronized void sendNode(Node node) throws IOException {
        if(state.get() != State.CONNECTED) {
            throw new IOException("Socket is not connected");
        }

        try {
            writeCipher.init(
                    Cipher.ENCRYPT_MODE,
                    writeKey,
                    GcmUtils.createNonce(writeCounter++)
            );
        } catch (InvalidAlgorithmParameterException | InvalidKeyException exception) {
            throw new IOException("Cannot initialize cipher", exception);
        }

        try {
            var plaintextLength = NodeEncoder.sizeOf(node);
            var ciphertextLength = writeCipher.getOutputSize(plaintextLength);
            var ciphertext = new byte[HEADER_LENGTH + ciphertextLength];
            var offset = writeRequestHeader(ciphertextLength, ciphertext, 0);
            NodeEncoder.encode(node, ciphertext, offset, plaintextLength);
            writeCipher.doFinal(ciphertext, offset, plaintextLength, ciphertext, offset);
            socketClient.sendBinary(ByteBuffer.wrap(ciphertext)); // Write order is guaranteed
        } catch (ShortBufferException | IllegalBlockSizeException | BadPaddingException exception) {
            throw new IOException("Cannot cipher plaintext", exception);
        }
    }

    public void disconnect() {
        disconnect(true);
    }

    private void disconnect(boolean userRequested) {
        if (state.getAndSet(State.DISCONNECTED) == State.DISCONNECTED) {
            return;
        }

        var handshakeFuture = this.handshakeFuture;
        if(handshakeFuture != null && !handshakeFuture.isDone()) {
            if(userRequested) {
                handshakeFuture.completeExceptionally(new IOException("disconnect() was called while connecting"));
            } else {
                handshakeFuture.completeExceptionally(new IOException("No handshake response from WhatsApp"));
            }
        }

        if(readKey != null) {
            try {
                readKey.destroy();
            } catch (DestroyFailedException _) {

            }
        }
        readCounter = 0;

        if(writeKey != null) {
            try {
                writeKey.destroy();
            } catch (DestroyFailedException _) {

            }
        }
        writeCounter = 0;

        if(socketClient != null) {
            try {
                socketClient.disconnect();
            }catch (IOException _) {

            }
        }

        if(!userRequested) {
            listener.onClose();
        }
    }

    public boolean isConnected() {
        return state.get() != State.DISCONNECTED;
    }

    private byte[] getHandshakePrologue() {
        return switch (store.clientType()) {
            case WEB -> WEB_PROLOGUE;
            case MOBILE -> MOBILE_PROLOGUE;
        };
    }

    private void finishHandshake(ByteBuffer serverHelloPayload) {
        if(handshakeEphemeralKeyPair == null) {
            throw new IllegalStateException("Handshake has not started");
        }

        var serverHandshake = HandshakeMessageSpec.decode(ProtobufInputStream.fromBuffer(serverHelloPayload));
        var serverHello = serverHandshake.serverHello();
        try(var handshake = new WhatsAppSocketHandshake(handshakePrologue)) {
            handshake.updateHash(handshakeEphemeralKeyPair.publicKey().toEncodedPoint());
            handshake.updateHash(serverHello.ephemeral());
            var sharedEphemeral = Curve25519.sharedKey(handshakeEphemeralKeyPair.privateKey().toEncodedPoint(), serverHello.ephemeral());
            handshake.mixIntoKey(sharedEphemeral);
            var decodedStaticText = handshake.cipher(serverHello.staticText(), false);
            var sharedStatic = Curve25519.sharedKey(handshakeEphemeralKeyPair.privateKey().toEncodedPoint(), decodedStaticText);
            handshake.mixIntoKey(sharedStatic);
            handshake.cipher(serverHello.payload(), false);
            var noiseKeyPair = store.noiseKeyPair();
            var encodedKey = handshake.cipher(noiseKeyPair.publicKey().toEncodedPoint(), true);
            var sharedPrivate = Curve25519.sharedKey(noiseKeyPair.privateKey().toEncodedPoint(), serverHello.ephemeral());
            handshake.mixIntoKey(sharedPrivate);
            var encodedPayload = handshake.cipher(getHandshakePayload(), true);
            var clientFinish = new ClientFinish(encodedKey, encodedPayload);
            var clientHandshake = new HandshakeMessageBuilder()
                    .clientFinish(clientFinish)
                    .build();
            var requestLength = HandshakeMessageSpec.sizeOf(clientHandshake);
            var message = new byte[HEADER_LENGTH + requestLength];
            var offset = writeRequestHeader(requestLength, message, 0);
            HandshakeMessageSpec.encode(clientHandshake, ProtobufOutputStream.toBytes(message, offset));
            socketClient.sendBinary(ByteBuffer.wrap(message));
            var keys = handshake.finish();

            writeCipher = Cipher.getInstance(ALGORITHM);
            writeCounter = 0;
            writeKey = new SecretKeySpec(keys, 0, 32, "AES");

            readCipher = Cipher.getInstance(ALGORITHM);
            readCounter = 0;
            readKey = new SecretKeySpec(keys, 32, 32, "AES");

            state.compareAndSet(State.HANDSHAKE_FINISH, State.CONNECTED);
            handshakeFuture.complete(null);
        }catch (GeneralSecurityException exception) {
            handshakeFuture.completeExceptionally(new IOException("Noise handshake crypto failure", exception));
            disconnect(false);
        }finally {
            handshakeEphemeralKeyPair = null;
            handshakePrologue = null;
        }
    }

    private byte[] getHandshakePayload() {
        var agent = getUserAgent();
        var payload = getClientPayload(agent);
        return ClientPayloadSpec.encode(payload);
    }

    private ClientPayload getClientPayload(UserAgent agent) {
        return switch (store.clientType()) {
            case WEB -> getWebClientPayload(agent);
            case MOBILE -> getMobileClientPayload(agent);
        };
    }

    private ClientPayload getWebClientPayload(UserAgent agent) {
        var jid = store.jid();
        if (jid.isPresent()) {
            return new ClientPayloadBuilder()
                    .connectReason(ClientPayload.ClientPayloadConnectReason.USER_ACTIVATED)
                    .connectType(ClientPayload.ClientPayloadConnectType.WIFI_UNKNOWN)
                    .userAgent(agent)
                    .username(Long.parseLong(jid.get().user()))
                    .passive(true)
                    .pull(true)
                    .device(jid.get().device())
                    .build();
        } else {
            return new ClientPayloadBuilder()
                    .connectReason(ClientPayload.ClientPayloadConnectReason.USER_ACTIVATED)
                    .connectType(ClientPayload.ClientPayloadConnectType.WIFI_UNKNOWN)
                    .userAgent(agent)
                    .regData(createRegisterData())
                    .passive(false)
                    .pull(false)
                    .build();
        }
    }

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
                .connectType(ClientPayload.ClientPayloadConnectType.WIFI_UNKNOWN)
                .connectReason(ClientPayload.ClientPayloadConnectReason.USER_ACTIVATED)
                .connectAttemptCount(0)
                .device(0)
                .oc(false)
                .build();
    }

    private UserAgent getUserAgent() {
        var mobile = store.clientType() == WhatsAppClientType.MOBILE;
        return new UserAgentBuilder()
                .platform(store.device().platform())
                .appVersion(store.clientVersion())
                .mcc("000")
                .mnc("000")
                .osVersion(mobile ? store.device().osVersion().toString() : null)
                .manufacturer(mobile ? store.device().manufacturer() : null)
                .device(mobile ? store.device().model().replaceAll("_", " ") : null)
                .osBuildNumber(mobile ? store.device().osBuildNumber() : null)
                .phoneId(mobile ? store.fdid().toString().toUpperCase() : null)
                .releaseChannel(store.releaseChannel())
                .localeLanguageIso6391("en")
                .localeCountryIso31661Alpha2("US")
                .deviceType(UserAgent.DeviceType.PHONE)
                .deviceModelType(store.device().modelId())
                .build();
    }

    private CompanionRegistrationData createRegisterData() {
        var companion = new CompanionRegistrationDataBuilder()
                .buildHash(store.clientVersion().toHash())
                .eRegid(SecureBytes.intToBytes(store.registrationId(), 4))
                .eKeytype(SecureBytes.intToBytes(SignalIdentityPublicKey.type(), 1))
                .eIdent(store.identityKeyPair().publicKey().toEncodedPoint())
                .eSkeyId(SecureBytes.intToBytes(store.signedKeyPair().id(), 3))
                .eSkeyVal(store.signedKeyPair().publicKey().toEncodedPoint())
                .eSkeySig(store.signedKeyPair().signature());
        if (store.clientType() == WhatsAppClientType.WEB) {
            var props = createCompanionProps();
            var encodedProps = props == null ? null : CompanionPropertiesSpec.encode(props);
            companion.companionProps(encodedProps);
        }

        return companion.build();
    }

    private CompanionProperties createCompanionProps() {
        return switch (store.clientType()) {
            case WEB -> {
                var historyLength = store.webHistoryPolicy()
                        .orElse(WhatsAppWebClientHistory.standard(true));
                var config = new HistorySyncConfigBuilder()
                        .inlineInitialPayloadInE2EeMsg(true)
                        .supportBotUserAgentChatHistory(true)
                        .supportCallLogHistory(true)
                        .storageQuotaMb(historyLength.size())
                        .fullSyncSizeMbLimit(historyLength.size())
                        .build();
                var platformType = switch (store.device().platform()) {
                    case IOS, IOS_BUSINESS -> CompanionProperties.PlatformType.IOS_PHONE;
                    case ANDROID, ANDROID_BUSINESS -> CompanionProperties.PlatformType.ANDROID_PHONE;
                    case WINDOWS -> CompanionProperties.PlatformType.UWP;
                    case MACOS -> CompanionProperties.PlatformType.IOS_CATALYST;
                };
                yield new CompanionPropertiesBuilder()
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

    private void decryptNodes(ByteBuffer buffer) {
        var output = buffer.duplicate();

        try {
            readCipher.init(
                    Cipher.DECRYPT_MODE,
                    readKey,
                    GcmUtils.createNonce(readCounter++)
            );
            readCipher.doFinal(buffer, output);
        } catch (GeneralSecurityException e) {
            listener.onError(new WhatsAppSessionException.BadMac("Noise decryption failed", e));
            return;
        }

        output.flip();
        try (var decoder = new NodeDecoder(output)) {
            while (decoder.hasData()) {
                var node = decoder.decode();
                // Ordering is not important at this stage
                Thread.startVirtualThread(() -> listener.onNode(node));
            }
        }catch (IOException e) {
            listener.onError(new WhatsAppStreamException.MalformedNode());
        }
    }

    private static int writeRequestHeader(int requestLength, byte[] message, int offset) {
        INT_HANDLE.set(message, offset, requestLength >> 16);
        offset += 4;

        SHORT_HANDLE.set(message, offset, (short) (requestLength & UNSIGNED_INT16_MAX_VALUE));
        offset += 2;

        return offset;
    }

    private enum State {
        DISCONNECTED,
        CONNECTING,
        HANDSHAKE_START,
        HANDSHAKE_FINISH,
        CONNECTED
    }
}
