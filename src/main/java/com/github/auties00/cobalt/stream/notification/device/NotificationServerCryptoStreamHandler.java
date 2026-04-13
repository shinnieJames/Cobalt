package com.github.auties00.cobalt.stream.notification.device;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.media.MediaProvider;
import com.github.auties00.cobalt.model.media.MediaRetryNotificationSpec;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.stream.SocketStream;

import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HKDFParameterSpec;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
 *
 * @implNote WAWebHandlePreKeyLow.default, WAWebHandleIdentityChange.handleE2eIdentityChange,
 *           WAWebHandleMediaRetryNotification.default, WAWebHandleServerNotification.handleServerNotification,
 *           WAWebHandleDeviceSwitchingNotification.default
 */
final class NotificationServerCryptoStreamHandler implements SocketStream.Handler {
    /**
     * Logger for this handler.
     *
     * @implNote WAWebHandlePreKeyLow.default - WALogger.ERROR/LOG
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationServerCryptoStreamHandler.class.getName());

    /**
     * The set of notification types supported by this handler, used to filter
     * incoming notification stanzas before dispatching.
     *
     * @implNote WAWebCommsHandleLoggedInStanza.handleLoggedInStanza
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
     *
     * @implNote WAWebHandlePreKeyLow.default - o("WAWebUploadPreKeysJob").uploadPreKeys()
     */
    private final WhatsAppClient whatsapp;

    /**
     * The AB props service used to synchronize A/B testing properties when
     * a server notification of type {@code abprops} is received.
     *
     * @implNote WAWebHandleServerNotification.handleServerNotification - o("WAWebAbPropsSyncJob").syncABPropsTask
     */
    private final ABPropsService abPropsService;

    /**
     * Guard set that prevents duplicate pre-key upload operations within the
     * same session. Each stanza ID is added before the upload begins and
     * removed when the upload completes, ensuring that only one upload is
     * in progress at any given time per session.
     *
     * @implNote WAWebHandlePreKeyLow.default - var s = new Set
     */
    private final Set<String> preKeyUploadGuard;

    /**
     * Constructs a new handler for server-crypto notifications.
     *
     * @param whatsapp       the WhatsApp client instance
     * @param abPropsService the AB props synchronization service
     * @implNote WAWebHandlePreKeyLow.default, WAWebHandleServerNotification.handleServerNotification
     */
    NotificationServerCryptoStreamHandler(WhatsAppClient whatsapp, ABPropsService abPropsService) {
        this.whatsapp = whatsapp;
        this.abPropsService = abPropsService;
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
     * @implNote WAWebCommsHandleLoggedInStanza.handleLoggedInStanza
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
     *
     * @implNote WAWebHandlePreKeyLow.default - s.delete(n) / session reset
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
     * @implNote WAWebCommsHandleLoggedInStanza.handleLoggedInStanza (encrypt case),
     *           WAWebCommsHandleWorkerCompatibleStanza.handleWorkerCompatibleStanza (identity case)
     */
    private void handleEncrypt(Node node) {
        var firstChild = node.getChild().orElse(null);
        if (firstChild == null) {
            return;
        }

        switch (firstChild.description()) {
            case "count" -> handlePreKeyLow(node, firstChild.getAttributeAsLong("value", 0L));
            case "digest" -> LOGGER.log(System.Logger.Level.DEBUG, // ADAPTED: WAWebHandleDigestKey.default
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
     * @implNote WAWebHandlePreKeyLow.default
     */
    private void handlePreKeyLow(Node node, long keysCount) {
        var stanzaId = node.getAttributeAsString("id", null); // WAWebHandlePreKeyLow.default
        if (stanzaId == null || !preKeyUploadGuard.add(stanzaId)) { // WAWebHandlePreKeyLow.default - s.has(n) / s.add(n)
            return;
        }

        try {
            whatsapp.sendPreKeys(keysCount); // WAWebHandlePreKeyLow.default - o("WAWebUploadPreKeysJob").uploadPreKeys()
        } finally {
            preKeyUploadGuard.remove(stanzaId); // WAWebHandlePreKeyLow.default - s.delete(n)
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
     * @implNote WAWebHandleIdentityChange.handleE2eIdentityChange
     */
    private void handleIdentityChange(Node node) {
        var deviceJid = node.getAttributeAsJid("from").orElse(null); // WAWebHandleIdentityChange.handleE2eIdentityChange - wid
        if (deviceJid == null || deviceJid.device() != 0) { // WAWebHandleIdentityChange.handleE2eIdentityChange - h.device !== DEFAULT_DEVICE_ID
            return;
        }

        var userJid = deviceJid.toUserJid(); // WAWebHandleIdentityChange.handleE2eIdentityChange - asUserWidOrThrow(h)
        var selfJid = whatsapp.store().jid()
                .map(Jid::toUserJid)
                .orElse(null);
        if (selfJid != null && selfJid.equals(userJid)) { // WAWebHandleIdentityChange.handleE2eIdentityChange - isMePrimary(h)
            LOGGER.log(System.Logger.Level.WARNING,
                    "Ignoring self primary identity-change notification");
            return;
        }

        var lid = node.getAttributeAsJid("lid") // WAWebHandleIdentityChange.handleE2eIdentityChange - e.hasAttr("lid") ? deviceJidToDeviceWid(e.attrDeviceJid("lid")) : null
                .map(Jid::toUserJid)
                .orElse(null);
        if (lid != null) {
            whatsapp.store().registerLidMapping(userJid, lid); // ADAPTED: WAWebCreateOrReplaceDisplayNamesAndLidPnMappingsJob.createOrReplaceDisplayNamesAndLidPnMappings
        }

        var displayName = node.getAttributeAsString("display_name", null); // WAWebHandleIdentityChange.handleE2eIdentityChange - i.displayName
        var contact = whatsapp.store()
                .findContactByJid(userJid)
                .orElseGet(() -> whatsapp.store().addNewContact(userJid));
        if (displayName != null && !displayName.isBlank()) { // ADAPTED: WAWebCreateOrReplaceDisplayNamesAndLidPnMappingsJob
            contact.setChosenName(displayName);
        }
        whatsapp.store().addContact(contact);

        whatsapp.store().markIdentityChange(deviceJid); // ADAPTED: WAWebIdentityChangeApiFactory.clearDeviceRecordForIdentityChange
        whatsapp.store().cleanupSignalSessions(deviceJid); // WAWebHandleIdentityChange.handleE2eIdentityChange - Session.deleteRemoteInfo(h)
        whatsapp.store().clearSenderKeyDistributionForParticipant(deviceJid); // ADAPTED: WAWebUserPrefsStatus.markStatusSenderKeyRotate + WAWebBroadcastSenderKeyManager.markBroadcastSenderKeyRotateForUser
        whatsapp.store().markKeyRotation(userJid); // ADAPTED: WAWebUserPrefsStatus.markStatusSenderKeyRotate
        fireListeners(listener -> listener.onDeviceIdentityChanged(whatsapp, userJid, Set.of(deviceJid))); // ADAPTED: WAWebSecurityCodeApi.addSecurityCodeChangedNotifications
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
     * @implNote WAWebHandleMediaRetryNotification.default
     */
    private void handleMediaRetry(Node node) {
        if (node.hasChild("error")) { // WAWebHandleMediaRetryNotification.default - errorCode != null
            return;
        }

        var encryptNode = node.getChild("encrypt").orElse(null); // WAWebHandleMediaRetryNotification.default - e.child("encrypt")
        if (encryptNode == null) {
            return;
        }

        var message = findMessageById(node.getAttributeAsString("id", null)); // WAWebHandleMediaRetryNotification.default - a.msgId
        if (message == null || !(message.message().content() instanceof MediaProvider mediaProvider)) {
            return;
        }

        var mediaKey = mediaProvider.mediaKey().orElse(null); // WAWebHandleMediaRetryNotification.default - getMediaKey(msgId)
        var encP = encryptNode.getChild("enc_p").flatMap(Node::toContentBytes).orElse(null); // WAWebHandleMediaRetryNotification.default - a.child("enc_p").contentBytes()
        var encIv = encryptNode.getChild("enc_iv").flatMap(Node::toContentBytes).orElse(null); // WAWebHandleMediaRetryNotification.default - a.child("enc_iv").contentBytes(ENC_IV_SIZE)
        if (mediaKey == null || encP == null || encIv == null) {
            return;
        }

        try {
            var hkdf = KDF.getInstance("HKDF-SHA256"); // WAWebCryptoMediaRetry.extractAndExpand
            var params = HKDFParameterSpec.ofExtract()
                    .addIKM(mediaKey)
                    .thenExpand("WhatsApp Media Retry Notification".getBytes(StandardCharsets.UTF_8), 32); // WAWebCryptoMediaRetry - _ = "WhatsApp Media Retry Notification"
            var retryKey = hkdf.deriveKey("AES", params);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding"); // WAWebCryptoMediaRetry.decryptMediaRetryNotification - gcmDecrypt
            cipher.init(Cipher.DECRYPT_MODE, retryKey, new GCMParameterSpec(128, encIv));
            cipher.updateAAD(message.key().id().orElse("").getBytes(StandardCharsets.UTF_8)); // WAWebCryptoMediaRetry.decryptMediaRetryNotification - gcmDecrypt(a, n, r, t) where t is stanzaId
            var decoded = cipher.doFinal(encP);
            var notification = MediaRetryNotificationSpec.decode(decoded); // WAWebCryptoMediaRetry.decryptMediaRetryNotification - decodeProtobuf(MediaRetryNotificationSpec, i)
            notification.directPath().ifPresent(directPath -> { // WAWebHandleMediaRetryNotification.default - resolveMediaReupload({msgId, result, directPath})
                mediaProvider.setMediaUrl(null);
                mediaProvider.setMediaDirectPath(directPath);
            });
        } catch (Exception ignored) { // ADAPTED: WAWebHandleMediaRetryNotification.default - error handling
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
     * @implNote WAWebHandleServerNotification.handleServerNotification
     */
    private void handleServer(Node node) {
        var firstChild = node.getChild().map(Node::description).orElse(null); // WAWebHandleServerNotification.handleServerNotification
        switch (firstChild) {
            case "log" -> LOGGER.log(System.Logger.Level.WARNING, // ADAPTED: WAWebCrashlog.upload({reason: SERVER_REQUESTED, ...})
                    "Ignoring server log-request notification");
            case "abprops" -> abPropsService.sync(); // WAWebHandleServerNotification.handleServerNotification - syncABPropsTask({shouldSendHash: false})
            case null, default -> LOGGER.log(System.Logger.Level.DEBUG, // WAWebHandleServerNotification.handleServerNotification - type == null case
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
     * @implNote WAWebHandleDeviceSwitchingNotification.default
     */
    private void handleRegistration(Node node) {
        var registration = node.getChild("wa_old_registration").orElse(null); // WAWebHandleDeviceSwitchingNotification.default - e.child("wa_old_registration")
        if (registration == null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Ignoring unsupported registration notification {0}",
                    node.getAttributeAsString("id", "<missing>"));
            return;
        }

        var code = registration.getAttributeAsString("code", null); // WAWebHandleDeviceSwitchingNotification.default - r.attrString("code")
        var expiry = registration.getAttributeAsLong("expiry_t", (Long) null); // WAWebHandleDeviceSwitchingNotification.default - r.attrTime("expiry_t")
        var now = java.time.Instant.now().getEpochSecond(); // WAWebHandleDeviceSwitchingNotification.default - o("WATimeUtils").unixTime()
        if (code == null || expiry == null || now > expiry) { // WAWebHandleDeviceSwitchingNotification.default - l > i
            return;
        }

        try {
            var numericCode = Long.parseLong(code); // ADAPTED: WAWebHandleDeviceSwitchingNotification.default - code is string, Cobalt listener uses long
            fireListeners(listener -> listener.onRegistrationCode(whatsapp, numericCode)); // ADAPTED: WAWebBackendApi.frontendFireAndForget("showDeviceSwitchOtp", {otpCode: c})
        } catch (NumberFormatException exception) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring non-numeric device-switch code {0}",
                    code);
        }
        // WAM: WaOldCodeWamEvent - skipped (telemetry)
    }

    /**
     * Fires an event to all registered listeners on separate virtual threads.
     *
     * @param consumer the listener callback to invoke
     * @implNote WAWebBackendApi.frontendFireAndForget
     */
    private void fireListeners(java.util.function.Consumer<com.github.auties00.cobalt.client.WhatsAppClientListener> consumer) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> consumer.accept(listener)); // NO_WA_BASIS
        }
    }

    /**
     * Searches all chats, status messages, and newsletters for a message
     * with the given stanza identifier.
     *
     * @param id the stanza identifier to search for
     * @return the matching {@link MessageInfo}, or {@code null} if not found
     * @implNote WAWebHandleMediaRetryNotification.default - message lookup by msgId
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

        var statusMessage = whatsapp.store().status().stream()
                .filter(message -> Objects.equals(message.key().id().orElse(""), id))
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
     * @implNote WAWebHandlePreKeyLow.default, WAWebHandleIdentityChange.handleE2eIdentityChange,
     *           WAWebHandleMediaRetryNotification.default, WAWebHandleServerNotification.handleServerNotification,
     *           WAWebHandleDeviceSwitchingNotification.default
     */
    private void sendNotificationAck(Node node, String type) {
        var stanzaId = node.getAttributeAsString("id", null); // WAWebHandlePreKeyLow.default - a.stanzaId
        var stanzaFrom = node.getAttributeAsJid("from", null); // WAWebHandlePreKeyLow.default - to: S_WHATSAPP_NET (from server)
        if (stanzaId == null || stanzaFrom == null) {
            return;
        }

        var ackBuilder = new NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId)
                .attribute("class", "notification") // WAWebHandlePreKeyLow.default - class: "notification"
                .attribute("to", stanzaFrom);

        // WAWebHandlePreKeyLow.default and WAWebHandleIdentityChange.handleE2eIdentityChange
        // do NOT include a type attribute in their acks. The other handlers do.
        if (!"encrypt".equals(type)) { // WAWebHandleMediaRetryNotification.default, WAWebHandleServerNotification.handleServerNotification, WAWebHandleDeviceSwitchingNotification.default
            ackBuilder.attribute("type", type);
        }

        // Include participant for mediaretry acks
        if ("mediaretry".equals(type)) { // WAWebHandleMediaRetryNotification.default - participant: f || DROP_ATTR
            var participant = node.getAttributeAsJid("participant", null);
            if (participant != null) {
                ackBuilder.attribute("participant", participant);
            }
        }

        whatsapp.sendNodeWithNoResponse(ackBuilder.build());
    }
}
