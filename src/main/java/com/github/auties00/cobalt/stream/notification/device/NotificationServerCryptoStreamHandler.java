package com.github.auties00.cobalt.stream.notification.device;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.media.MediaProvider;
import com.github.auties00.cobalt.model.media.MediaRetryNotificationSpec;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.stream.SocketStream;

import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HKDFParameterSpec;
import java.util.Set;

final class NotificationServerCryptoStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(NotificationServerCryptoStreamHandler.class.getName());
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "encrypt",
            "mediaretry",
            "server",
            "registration"
    );

    private final WhatsAppClient whatsapp;
    private final ABPropsService abPropsService;

    NotificationServerCryptoStreamHandler(WhatsAppClient whatsapp, ABPropsService abPropsService) {
        this.whatsapp = whatsapp;
        this.abPropsService = abPropsService;
    }

    @Override
    public void handle(Node node) {
        var type = node.getAttributeAsString("type", null);
        if (!SUPPORTED_TYPES.contains(type)) {
            return;
        }

        try {
            switch (type) {
                case "encrypt" -> handleEncrypt(node);
                case "mediaretry" -> handleMediaRetry(node);
                case "server" -> handleServer(node);
                case "registration" -> handleRegistration(node);
                default -> {
                    // Exhaustive for SUPPORTED_TYPES.
                }
            }
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Cannot handle server-crypto notification {0}/{1}: {2}",
                    type,
                    node.getAttributeAsString("id", "<missing>"),
                    throwable.getMessage());
        } finally {
            sendNotificationAck(node);
        }
    }

    private void handleEncrypt(Node node) {
        var firstChild = node.getChild().orElse(null);
        if (firstChild == null) {
            return;
        }

        switch (firstChild.description()) {
            case "count" -> whatsapp.sendPreKeys(firstChild.getAttributeAsLong("value", 0L));
            case "digest" -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unsupported digest-key notification {0}",
                    node.getAttributeAsString("id", "<missing>"));
            case "identity" -> handleIdentityChange(node);
            default -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unsupported encrypt notification child {0}",
                    firstChild.description());
        }
    }

    private void handleIdentityChange(Node node) {
        var deviceJid = node.getAttributeAsJid("from").orElse(null);
        if (deviceJid == null || deviceJid.device() != 0) {
            return;
        }

        var userJid = deviceJid.toUserJid();
        var selfJid = whatsapp.store().jid()
                .map(Jid::toUserJid)
                .orElse(null);
        if (selfJid != null && selfJid.equals(userJid)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Ignoring self primary identity-change notification");
            return;
        }

        var lid = node.getAttributeAsJid("lid")
                .map(Jid::toUserJid)
                .orElse(null);
        if (lid != null) {
            whatsapp.store().registerLidMapping(userJid, lid);
        }

        var displayName = node.getAttributeAsString("display_name", null);
        var contact = whatsapp.store()
                .findContactByJid(userJid)
                .orElseGet(() -> whatsapp.store().addNewContact(userJid));
        if (displayName != null && !displayName.isBlank()) {
            contact.setChosenName(displayName);
        }
        whatsapp.store().addContact(contact);

        whatsapp.store().markIdentityChange(deviceJid);
        whatsapp.store().cleanupSignalSessions(deviceJid);
        whatsapp.store().clearSenderKeyDistributionForParticipant(deviceJid);
        whatsapp.store().markKeyRotation(userJid);
        fireListeners(listener -> listener.onDeviceIdentityChanged(whatsapp, userJid, Set.of(deviceJid)));
    }

    private void handleMediaRetry(Node node) {
        if (node.hasChild("error")) {
            return;
        }

        var encryptNode = node.getChild("encrypt").orElse(null);
        if (encryptNode == null) {
            return;
        }

        var message = findMessageById(node.getAttributeAsString("id", null));
        if (message == null || !(message.message().content() instanceof MediaProvider mediaProvider)) {
            return;
        }

        var mediaKey = mediaProvider.mediaKey().orElse(null);
        var encP = encryptNode.getChild("enc_p").flatMap(Node::toContentBytes).orElse(null);
        var encIv = encryptNode.getChild("enc_iv").flatMap(Node::toContentBytes).orElse(null);
        if (mediaKey == null || encP == null || encIv == null) {
            return;
        }

        try {
            var hkdf = KDF.getInstance("HKDF-SHA256");
            var params = HKDFParameterSpec.ofExtract()
                    .addIKM(mediaKey)
                    .thenExpand("WhatsApp Media Retry Notification".getBytes(java.nio.charset.StandardCharsets.UTF_8), 32);
            var retryKey = hkdf.deriveKey("AES", params);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, retryKey, new GCMParameterSpec(128, encIv));
            cipher.updateAAD(message.id().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var decoded = cipher.doFinal(encP);
            var notification = MediaRetryNotificationSpec.decode(decoded);
            notification.directPath().ifPresent(directPath -> {
                mediaProvider.setMediaUrl(null);
                mediaProvider.setMediaDirectPath(directPath);
            });
        } catch (Exception ignored) {
        }
    }

    private void handleServer(Node node) {
        var firstChild = node.getChild().map(Node::description).orElse(null);
        switch (firstChild) {
            case "log" -> LOGGER.log(System.Logger.Level.WARNING,
                    "Ignoring server log-request notification");
            case "abprops" -> abPropsService.sync();
            case "props" -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unsupported server notification child {0}", firstChild);
            default -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unknown server notification child {0}", firstChild);
        }
    }

    private void handleRegistration(Node node) {
        var registration = node.getChild("wa_old_registration").orElse(null);
        if (registration == null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Ignoring unsupported registration notification {0}",
                    node.getAttributeAsString("id", "<missing>"));
            return;
        }

        var code = registration.getAttributeAsString("code", null);
        var expiry = registration.getAttributeAsLong("expiry_t", (Long) null);
        var now = java.time.Instant.now().getEpochSecond();
        if (code == null || expiry == null || now > expiry) {
            return;
        }

        try {
            var numericCode = Long.parseLong(code);
            fireListeners(listener -> listener.onRegistrationCode(whatsapp, numericCode));
        } catch (NumberFormatException exception) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring non-numeric device-switch code {0}",
                    code);
        }
    }

    private void fireListeners(java.util.function.Consumer<com.github.auties00.cobalt.client.WhatsAppClientListener> consumer) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> consumer.accept(listener));
        }
    }

    private MessageInfo findMessageById(String id) {
        if (id == null) {
            return null;
        }

        for (var chat : whatsapp.store().chats()) {
            var message = whatsapp.store().findMessageById(chat, id).orElse(null);
            if (message != null) {
                return message;
            }
        }

        var statusMessage = whatsapp.store().status().stream()
                .filter(message -> java.util.Objects.equals(message.id(), id))
                .findFirst()
                .orElse(null);
        if (statusMessage != null) {
            return statusMessage;
        }

        for (var newsletter : whatsapp.store().newsletters()) {
            var message = whatsapp.store().findMessageById(newsletter, id).orElse(null);
            if (message != null) {
                return message;
            }
        }

        return null;
    }

    private void sendNotificationAck(Node node) {
        var stanzaId = node.getAttributeAsString("id", null);
        var stanzaFrom = node.getAttributeAsJid("from", null);
        if (stanzaId == null || stanzaFrom == null) {
            return;
        }

        whatsapp.sendNodeWithNoResponse(new com.github.auties00.cobalt.node.NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId)
                .attribute("class", node.description())
                .attribute("to", stanzaFrom)
                .attribute("type", node.getAttributeAsString("type", null))
                .attribute("participant", node.getAttributeAsJid("participant", null))
                .build());
    }
}
