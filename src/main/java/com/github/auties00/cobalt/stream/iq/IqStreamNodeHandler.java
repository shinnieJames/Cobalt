package com.github.auties00.cobalt.stream.iq;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientDisconnectReason;
import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.exception.WhatsAppSessionException;
import com.github.auties00.cobalt.model.auth.DeviceIdentitySpec;
import com.github.auties00.cobalt.model.auth.SignedDeviceIdentityBuilder;
import com.github.auties00.cobalt.model.auth.SignedDeviceIdentitySpec;
import com.github.auties00.cobalt.model.auth.UserAgent.PlatformType;
import com.github.auties00.cobalt.model.contact.ContactBuilder;
import com.github.auties00.cobalt.model.contact.ContactStatus;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketPhonePairing;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.libsignal.key.SignalIdentityKeyPair;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public final class IqStreamNodeHandler extends SocketStream.Handler {
    private static final int PING_INTERVAL = 30;

    private final WhatsAppClientVerificationHandler.Web webVerificationHandler;
    private final SocketPhonePairing pairingCode;
    private final Executor pingExecutor;
    private final DeviceService deviceService;

    public IqStreamNodeHandler(WhatsAppClient whatsapp, WhatsAppClientVerificationHandler.Web webVerificationHandler, SocketPhonePairing pairingCode, DeviceService deviceService) {
        super(whatsapp, "iq");
        this.webVerificationHandler = webVerificationHandler;
        this.pairingCode = pairingCode;
        this.pingExecutor = CompletableFuture.delayedExecutor(PING_INTERVAL, TimeUnit.SECONDS);
        this.deviceService = deviceService;
    }

    @Override
    public void handle(Node node) {
        if (node.hasAttribute("xmlns", "urn:xmpp:ping")) {
            handlePing();
        }else {
            handlePairing(node);
        }
    }

    private void handlePing() {
        var result = new NodeBuilder()
                .description("result")
                .build();
        var response = new NodeBuilder()
                .description("iq")
                .attribute("to", JidServer.user())
                .attribute("type", "result")
                .content(result)
                .build();
        whatsapp.sendNodeWithNoResponse(response);
    }

    private void handlePairing(Node node) {
        var container = node.getChild().orElse(null);
        if (container == null) {
            return;
        }

        switch (container.description()) {
            case "pair-device" -> handlePairInit(node, container);
            case "pair-success" -> handlePairSuccess(node, container);
        }
    }

    private void handlePairInit(Node node, Node container) {
        switch (webVerificationHandler) {
            case WhatsAppClientVerificationHandler.Web.QrCode qrHandler -> {
                printQrCode(qrHandler, container);
                sendConfirmNode(node, null);
                schedulePing();
            }
            case WhatsAppClientVerificationHandler.Web.PairingCode codeHandler -> {
                askPairingCode(codeHandler);
                schedulePing();
            }
            default -> throw new IllegalArgumentException("Cannot verify account: unknown verification method");
        }
    }

    private void printQrCode(WhatsAppClientVerificationHandler.Web.QrCode qrHandler, Node container) {
        var ref = container.getChild("ref")
                .flatMap(Node::toContentString)
                .orElseThrow(() -> new NoSuchElementException("Missing ref"));
        var noisePublicKey = whatsapp.store().noiseKeyPair().publicKey().toEncodedPoint();
        var identityPublicKey = whatsapp.store().identityKeyPair().publicKey().toEncodedPoint();
        var advSecretKey = whatsapp.store().generateAdvSecretKey();
        var qr = String.join(
                ",",
                ref,
                Base64.getEncoder().encodeToString(noisePublicKey),
                Base64.getEncoder().encodeToString(identityPublicKey),
                Base64.getEncoder().encodeToString(advSecretKey),
                "1"
        );
        qrHandler.handle(qr);
    }

    private void sendConfirmNode(Node node, Node content) {
        var nodeId = node.getRequiredAttributeAsString("id");
        var request = new NodeBuilder()
                .description("iq")
                .attribute("id", nodeId)
                .attribute("type", "result")
                .attribute("to", JidServer.user())
                .content(content)
                .build();
        whatsapp.sendNodeWithNoResponse(request);
    }

    private void schedulePing() {
        var result = sendPing();
        if(result == null) {
            whatsapp.disconnect(WhatsAppClientDisconnectReason.RECONNECTING);
        }else {
            whatsapp.store().save();
        }
        pingExecutor.execute(this::schedulePing);
    }

    private Node sendPing() {
        try {
            var pingBody = new NodeBuilder()
                    .description("ping")
                    .build();
            var pingRequest = new NodeBuilder()
                    .description("iq")
                    .attribute("to", JidServer.user())
                    .attribute("xmlns", "w:p")
                    .attribute("type", "get")
                    .content(pingBody);
            return whatsapp.sendNode(pingRequest);
        }catch (WhatsAppSessionException.Closed throwable) {
            return null;
        }
    }

    private void askPairingCode(WhatsAppClientVerificationHandler.Web.PairingCode codeHandler) {
        var phoneNumber = whatsapp.store()
                .phoneNumber()
                .orElseThrow(() -> new InternalError("Phone number was not set"));
        var companionKeyPair = SignalIdentityKeyPair.random();
        whatsapp.store().setCompanionKeyPair(companionKeyPair);
        // WAWebAdvSignatureApi.generateADVSecretKey: generate random 32-byte secret for HMAC verification
        // Note: For phone number pairing, this will be replaced by HKDF-derived secret in handlePrimaryHello
        whatsapp.store().generateAdvSecretKey();
        var linkCodePairingWrappedCompanionEphemeralPub = new NodeBuilder()
                .description("link_code_pairing_wrapped_companion_ephemeral_pub")
                .content(pairingCode.encrypt(companionKeyPair.publicKey()))
                .build();
        var companionServerAuthKeyPub = new NodeBuilder()
                .description("companion_server_auth_key_pub")
                .content(whatsapp.store().noiseKeyPair().publicKey().toEncodedPoint())
                .build();
        var companionPlatformId = new NodeBuilder()
                .description("companion_platform_id")
                .content(49)
                .build();
        var companionPlatformDisplay = new NodeBuilder()
                .description("companion_platform_display")
                .content("Chrome (Linux)".getBytes(StandardCharsets.UTF_8))
                .build();
        var linkCodePairingNonce = new NodeBuilder()
                .description("link_code_pairing_nonce")
                .content(0)
                .build();
        var registrationBody = new NodeBuilder()
                .description("link_code_companion_reg_request")
                .attribute("jid", Jid.of(phoneNumber))
                .attribute("stage", "companion_hello")
                .attribute("should_show_push_notification", true)
                .content(linkCodePairingWrappedCompanionEphemeralPub, companionServerAuthKeyPub, companionPlatformId, companionPlatformDisplay, linkCodePairingNonce)
                .build();
        var registrationRequest = new NodeBuilder()
                .description("iq")
                .attribute("to", JidServer.user())
                .attribute("xmlns", "md")
                .attribute("type", "set")
                        .content(registrationBody);
        whatsapp.sendNode(registrationRequest);
        pairingCode.accept(codeHandler);
    }

    private void handlePairSuccess(Node node, Node container) {
        saveCompanion(container);

        var signedDeviceIdentity = deviceService.extractAndValidateLocalSignedDeviceIdentity(container);
        if(signedDeviceIdentity.isEmpty()) {
            return;
        }
        whatsapp.store()
                .setSignedDeviceIdentity(signedDeviceIdentity.get());

        var platform = getWebPlatform(node);
        var device = whatsapp.store()
                .device()
                .withPlatform(platform);
        whatsapp.store()
                .setDevice(device);

        var deviceIdentity = DeviceIdentitySpec.decode(signedDeviceIdentity.get().details());
        var signedDeviceIdentityWithoutAccountSignatureKey = new SignedDeviceIdentityBuilder()
                .details(signedDeviceIdentity.get().details())
                .accountSignature(signedDeviceIdentity.get().accountSignature())
                .deviceSignature(signedDeviceIdentity.get().deviceSignature())
                .build();
        var encodedSignedDeviceIdentityWithoutAccountSignatureKey = SignedDeviceIdentitySpec.encode(signedDeviceIdentityWithoutAccountSignatureKey);
        var deviceIdentityNode = new NodeBuilder()
                .description("device-identity")
                .attribute("key-index", deviceIdentity.keyIndex())
                .content(encodedSignedDeviceIdentityWithoutAccountSignatureKey)
                .build();
        var devicePairRequest = new NodeBuilder()
                .description("pair-device-sign")
                .content(deviceIdentityNode)
                .build();
        sendConfirmNode(node, devicePairRequest);
    }

    private PlatformType getWebPlatform(Node node) {
        var name = node.getChild("platform")
                .flatMap(entry -> entry.getAttributeAsString("name"))
                .orElse(null);
        return switch (name) {
            case "smbi" -> PlatformType.IOS_BUSINESS;
            case "smba" -> PlatformType.ANDROID_BUSINESS;
            case "android" -> PlatformType.ANDROID;
            case "ios" -> PlatformType.IOS;
            case null, default -> null;
        };
    }

    private void saveCompanion(Node container) {
        var device = container.getRequiredChild("device");
        var jid = device.getRequiredAttributeAsJid("jid");
        whatsapp.store()
                .setJid(jid);
        whatsapp.store()
                .setPhoneNumber(Long.parseUnsignedLong(jid.user()));
        var contact = new ContactBuilder()
                .jid(jid)
                .chosenName(whatsapp.store().name())
                .lastKnownPresence(ContactStatus.AVAILABLE)
                .lastSeenSeconds(Instant.now().getEpochSecond())
                .blocked(false)
                .build();
        whatsapp.store()
                .addContact(contact);
        var lid = device.getRequiredAttributeAsJid("lid");
        whatsapp.store()
                .setLid(lid);
    }
}