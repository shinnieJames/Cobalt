package com.github.auties00.cobalt.stream.notification.device;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientListener;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.media.MediaProvider;
import com.github.auties00.cobalt.model.media.MediaRetryNotificationSpec;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.WaOldCodeEventBuilder;

import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HKDFParameterSpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Handles server-side cryptographic and device-related notifications received
 * via the WhatsApp notification stream.
 *
 * <p>This handler processes four distinct notification types:
 * <ul>
 *   <li>{@code encrypt} - pre-key count, digest key, and identity change sub-notifications</li>
 *   <li>{@code mediaretry} - media re-upload retry notifications</li>
 *   <li>{@code server} - server-initiated log upload and AB prop sync requests</li>
 *   <li>{@code registration} - device switching/OTP code notifications</li>
 * </ul>
 */
@WhatsAppWebModule(moduleName = "WAWebHandlePreKeyLow")
@WhatsAppWebModule(moduleName = "WAWebHandleIdentityChange")
@WhatsAppWebModule(moduleName = "WAWebHandleMediaRetryNotification")
@WhatsAppWebModule(moduleName = "WAWebHandleServerNotification")
@WhatsAppWebModule(moduleName = "WAWebHandleDeviceSwitchingNotification")
@WhatsAppWebModule(moduleName = "WAWebHandleDigestKey")
final class NotificationServerCryptoStreamHandler implements SocketStream.Handler {
    /**
     * Logger for this handler.
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationServerCryptoStreamHandler.class.getName());

    /**
     * The set of notification types supported by this handler, used to filter
     * incoming notification stanzas before dispatching.
     */
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "encrypt",
            "mediaretry",
            "server",
            "registration"
    );

    /**
     * The WhatsApp client instance used for sending pre-keys, accessing the
     * store, and dispatching listener events.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The AB props service used to synchronize A/B testing properties when
     * a server notification of type {@code abprops} is received.
     */
    private final ABPropsService abPropsService;

    /**
     * Guard set that prevents duplicate pre-key upload operations within the
     * same session. Each stanza ID is added before the upload begins and
     * removed when the upload completes, ensuring that only one upload is
     * in progress at any given time per session.
     */
    private final Set<String> preKeyUploadGuard;

    /**
     * The WAM telemetry service used to commit server-crypto events.
     */
    private final WamService wamService;

    /**
     * Constructs a new handler for server-crypto notifications.
     *
     * @param whatsapp       the WhatsApp client instance
     * @param abPropsService the AB props synchronization service
     * @param wamService     the WAM telemetry service used to commit server-crypto events
     */
    NotificationServerCryptoStreamHandler(WhatsAppClient whatsapp, ABPropsService abPropsService, WamService wamService) {
        this.whatsapp = whatsapp;
        this.abPropsService = abPropsService;
        this.wamService = wamService;
        this.preKeyUploadGuard = ConcurrentHashMap.newKeySet();
    }

    /**
     * Dispatches an incoming notification stanza to the appropriate sub-handler
     * based on the notification's {@code type} attribute.
     *
     * <p>Supported types are {@code encrypt}, {@code mediaretry}, {@code server},
     * and {@code registration}. Notifications with unsupported types are silently
     * ignored. An acknowledgment stanza is always sent after handling, regardless
     * of whether the handler succeeds or fails.
     *
     * @param node the incoming notification stanza to handle
     */
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
            sendNotificationAck(node, type);
        }
    }

    /**
     * Resets per-session state. Clears the pre-key upload deduplication guard
     * so that a new session can trigger fresh pre-key uploads.
     */
    @Override
    public void reset() {
        preKeyUploadGuard.clear();
    }

    /**
     * Handles {@code encrypt} type notifications by dispatching to the
     * appropriate sub-handler based on the first child element's tag.
     *
     * <p>Supported child tags:
     * <ul>
     *   <li>{@code count} - triggers pre-key upload via {@link #handlePreKeyLow(Node, long)}</li>
     *   <li>{@code digest} - triggers digest key verification (logged only in Cobalt)</li>
     *   <li>{@code identity} - triggers identity change handling via {@link #handleIdentityChange(Node)}</li>
     * </ul>
     *
     * @param node the encrypt notification stanza
     */
    private void handleEncrypt(Node node) {
        var firstChild = node.getChild().orElse(null);
        if (firstChild == null) {
            return;
        }

        switch (firstChild.description()) {
            case "count" -> handlePreKeyLow(node, firstChild.getAttributeAsLong("value", 0L));
            case "digest" -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unsupported digest-key notification {0}",
                    node.getAttributeAsString("id", "<missing>"));
            case "identity" -> handleIdentityChange(node);
            default -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unsupported encrypt notification child {0}",
                    firstChild.description());
        }
    }

    /**
     * Handles the pre-key low count notification by triggering a pre-key
     * upload to the server.
     *
     * <p>This method uses a deduplication guard to prevent multiple concurrent
     * pre-key uploads for the same notification. If a pre-key upload is already
     * in progress (the stanza ID is already in the guard set), the method returns
     * immediately, sending only the acknowledgment.
     *
     * @param node      the encrypt notification stanza containing the count child
     * @param keysCount the number of remaining pre-keys on the server
     */
    private void handlePreKeyLow(Node node, long keysCount) {
        var stanzaId = node.getAttributeAsString("id", null);
        if (stanzaId == null || !preKeyUploadGuard.add(stanzaId)) {
            return;
        }

        try {
            whatsapp.sendPreKeys(keysCount);
        } finally {
            preKeyUploadGuard.remove(stanzaId);
        }
    }

    /**
     * Handles an E2E identity change notification for a remote device.
     *
     * <p>When a contact's identity key changes (e.g., they reinstalled WhatsApp),
     * this method:
     * <ul>
     *   <li>Ignores changes from companion devices (non-zero device ID)</li>
     *   <li>Ignores changes for the user's own primary device</li>
     *   <li>Registers a LID-to-phone-number mapping if a LID is present</li>
     *   <li>Updates the contact's display name if provided</li>
     *   <li>Marks the identity as changed in the store</li>
     *   <li>Cleans up Signal sessions for the changed device</li>
     *   <li>Clears sender key distributions for the participant</li>
     *   <li>Marks the user for sender key rotation</li>
     *   <li>Fires the {@code onDeviceIdentityChanged} listener event</li>
     * </ul>
     *
     * @param node the encrypt notification stanza with an {@code identity} child
     */
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

    /**
     * Handles a media retry notification, decrypting the re-upload response
     * and updating the message's direct path with the new CDN location.
     *
     * <p>If the notification contains an {@code error} child, the handler returns
     * immediately since no decryptable payload is available. Otherwise, it locates
     * the original message by stanza ID, derives the decryption key from the
     * message's media key using HKDF, decrypts the AES-GCM payload, and updates
     * the media provider's direct path.
     *
     * @param node the media retry notification stanza
     */
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
                    .thenExpand("WhatsApp Media Retry Notification".getBytes(StandardCharsets.UTF_8), 32);
            var retryKey = hkdf.deriveKey("AES", params);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, retryKey, new GCMParameterSpec(128, encIv));
            cipher.updateAAD(message.key().id().orElse("").getBytes(StandardCharsets.UTF_8));
            var decoded = cipher.doFinal(encP);
            var notification = MediaRetryNotificationSpec.decode(decoded);
            notification.directPath().ifPresent(directPath -> {
                mediaProvider.setMediaUrl(null);
                mediaProvider.setMediaDirectPath(directPath);
            });
        } catch (Exception _) {
        }
    }

    /**
     * Handles a server notification by dispatching based on the first child
     * element's tag.
     *
     * <p>Supported child tags:
     * <ul>
     *   <li>{@code log} - server-requested log upload (logged only in Cobalt)</li>
     *   <li>{@code abprops} - triggers AB props synchronization</li>
     * </ul>
     *
     * @param node the server notification stanza
     */
    private void handleServer(Node node) {
        var firstChild = node.getChild().map(Node::description).orElse(null);
        switch (firstChild) {
            case "log" -> LOGGER.log(System.Logger.Level.WARNING,
                    "Ignoring server log-request notification");
            case "abprops" -> abPropsService.sync();
            case null, default -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unknown server notification child {0}", firstChild);
        }
    }

    /**
     * Handles a device-switching (registration) notification containing an
     * OTP code for transferring the WhatsApp account to a new device.
     *
     * <p>The method extracts the OTP code and expiry time from the
     * {@code wa_old_registration} child element. If the code has expired
     * (current time exceeds the expiry timestamp), the notification is
     * silently ignored. Otherwise, the OTP code is delivered to registered
     * listeners via {@code onRegistrationCode}.
     *
     * @param node the registration notification stanza
     */
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
        var now = Instant.now().getEpochSecond();
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

        var meDeviceId = whatsapp.store().jid()
                .map(Jid::device)
                .map(String::valueOf)
                .orElse(null);
        wamService.commit(new WaOldCodeEventBuilder()
                .deviceId(meDeviceId)
                .build());
    }

    /**
     * Fires an event to all registered listeners on separate virtual threads.
     *
     * @param consumer the listener callback to invoke
     */
    private void fireListeners(Consumer<WhatsAppClientListener> consumer) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> consumer.accept(listener));
        }
    }

    /**
     * Searches all chats, status messages, and newsletters for a message
     * with the given stanza identifier.
     *
     * @param id the stanza identifier to search for
     * @return the matching {@link MessageInfo}, or {@code null} if not found
     */
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

        var statusMessage = whatsapp.store().findStatusById(id).orElse(null);
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

    /**
     * Sends an acknowledgment stanza for the given notification.
     *
     * <p>The ack stanza includes the notification's stanza ID, the
     * {@code class} attribute set to {@code "notification"}, and the
     * {@code to} target from the notification's {@code from} attribute.
     * The {@code type} attribute is included only for notification types
     * that specify it in their WA Web ack format (i.e., {@code mediaretry},
     * {@code server}, and {@code registration}). The {@code encrypt} type
     * does NOT include a type attribute in the ack, matching WA Web behavior.
     *
     * @param node the notification stanza to acknowledge
     * @param type the notification type from the stanza's {@code type} attribute
     */
    private void sendNotificationAck(Node node, String type) {
        var stanzaId = node.getAttributeAsString("id", null);
        var stanzaFrom = node.getAttributeAsJid("from", null);
        if (stanzaId == null || stanzaFrom == null) {
            return;
        }

        var ackBuilder = new NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId)
                .attribute("class", "notification")
                .attribute("to", stanzaFrom);

        // do NOT include a type attribute in their acks. The other handlers do.
        if (!"encrypt".equals(type)) {
            ackBuilder.attribute("type", type);
        }

        // Include participant for mediaretry acks
        if ("mediaretry".equals(type)) {
            var participant = node.getAttributeAsJid("participant", null);
            if (participant != null) {
                ackBuilder.attribute("participant", participant);
            }
        }

        whatsapp.sendNodeWithNoResponse(ackBuilder.build());
    }
}
