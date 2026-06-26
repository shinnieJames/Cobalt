package com.github.auties00.cobalt.stream.notification.device;

import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stream.SocketStreamHandler;
import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.listener.linked.LinkedDeviceIdentityChangedListener;
import com.github.auties00.cobalt.listener.linked.LinkedRegistrationCodeListener;
import com.github.auties00.cobalt.listener.WhatsAppListener;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.media.MediaProvider;
import com.github.auties00.cobalt.model.media.MediaRetryNotificationSpec;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.WaOldCodeEventBuilder;

import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HKDFParameterSpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Handles server-issued cryptographic notifications dispatched by
 * {@link NotificationDeviceDispatcher}.
 *
 * <p>Covers four notification types: {@code encrypt} (pre-key low, digest key, identity change),
 * {@code mediaretry} (re-uploaded CDN URL delivery), {@code server} (log upload and AB prop sync
 * requests), and {@code registration} (device-switch OTP). Most branches trigger a side-effect on
 * the local crypto or AB state: pre-key low uploads more pre-keys to the server, identity change
 * wipes the Signal session and fires {@link LinkedDeviceIdentityChangedListener#onDeviceIdentityChanged}, media
 * retry decrypts the new direct path and writes it onto the message, server runs the matching
 * maintenance task, and registration delivers the device-switch OTP to listeners. Every supported
 * stanza is acknowledged even when its branch throws; unsupported types return without
 * side-effects.
 *
 * @implNote This implementation merges six WA Web modules ({@code WAWebHandlePreKeyLow},
 * {@code WAWebHandleIdentityChange}, {@code WAWebHandleMediaRetryNotification},
 * {@code WAWebHandleServerNotification}, {@code WAWebHandleDeviceSwitchingNotification},
 * {@code WAWebHandleDigestKey}) under one Cobalt handler because they all share the per-stanza ACK
 * pattern (read {@code type}, branch, ACK in {@code finally}) and so live more comfortably as one
 * class than six near-empty ones.
 */
@WhatsAppWebModule(moduleName = "WAWebHandlePreKeyLow")
@WhatsAppWebModule(moduleName = "WAWebHandleIdentityChange")
@WhatsAppWebModule(moduleName = "WAWebHandleMediaRetryNotification")
@WhatsAppWebModule(moduleName = "WAWebHandleServerNotification")
@WhatsAppWebModule(moduleName = "WAWebHandleDeviceSwitchingNotification")
@WhatsAppWebModule(moduleName = "WAWebHandleDigestKey")
final class NotificationServerCryptoStreamHandler extends SocketStreamHandler.Concurrent {
    /**
     * Logs warnings and debug messages about server-crypto notification handling.
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationServerCryptoStreamHandler.class.getName());

    /**
     * Holds the notification {@code type} values routed to this handler.
     *
     * <p>Consulted by {@link #handle(Stanza)} as the first-line filter; any stanza whose type is
     * outside this set returns without side-effects.
     */
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "encrypt",
            "mediaretry",
            "server",
            "registration"
    );

    /**
     * Provides store reads, Signal session mutations, pre-key uploads, and ack sends.
     */
    private final LinkedWhatsAppClient whatsapp;

    /**
     * Re-syncs AB props from the server for the {@code server/abprops} branch.
     */
    private final ABPropsService abPropsService;

    /**
     * Guards against two concurrent pre-key uploads for the same {@code stanzaId} within one
     * session.
     *
     * <p>The stanza id is added before the upload and removed on completion, so a second pre-key-low
     * stanza with the same id arriving concurrently exits without re-uploading. Cleared by
     * {@link #reset()} when the stream reconnects.
     */
    private final Set<String> preKeyUploadGuard;

    /**
     * Commits the {@code WaOldCode} event after a successful device-switch OTP delivery.
     */
    private final WamService wamService;

    /**
     * Ships the post-processing {@code <ack class="notification">} stanza, special-casing the
     * {@code encrypt} branch to omit {@code type} and the {@code mediaretry} branch to include
     * {@code participant}.
     */
    private final AckSender ackSender;

    /**
     * Constructs the handler with shared dependencies.
     *
     * <p>Called once by {@link NotificationDeviceDispatcher}.
     *
     * @param whatsapp       the {@link LinkedWhatsAppClient}
     * @param abPropsService the {@link ABPropsService}
     * @param wamService     the {@link WamService}
     * @param ackSender      the {@link AckSender}
     */
    NotificationServerCryptoStreamHandler(LinkedWhatsAppClient whatsapp, ABPropsService abPropsService, WamService wamService, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.abPropsService = abPropsService;
        this.wamService = wamService;
        this.ackSender = ackSender;
        this.preKeyUploadGuard = ConcurrentHashMap.newKeySet();
    }

    /**
     * Routes the notification to its per-type sub-handler and always sends the protocol-level ACK.
     *
     * <p>Stanzas whose type is outside {@link #SUPPORTED_TYPES} return without side-effects. For a
     * supported stanza the matching branch handler runs inside a {@code try} block, any failure is
     * caught and warning-logged, and the ACK is sent in the {@code finally} block so a valid stanza
     * is always acknowledged even when its branch throws.
     *
     * @param stanza the incoming {@code <notification>} stanza
     */
    @Override
    public void handle(Stanza stanza) {
        var type = stanza.getAttributeAsString("type", null);
        if (!SUPPORTED_TYPES.contains(type)) {
            return;
        }

        try {
            switch (type) {
                case "encrypt" -> handleEncrypt(stanza);
                case "mediaretry" -> handleMediaRetry(stanza);
                case "server" -> handleServer(stanza);
                case "registration" -> handleRegistration(stanza);
                default -> {
                }
            }
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Cannot handle server-crypto notification {0}/{1}: {2}",
                    type,
                    stanza.getAttributeAsString("id", "<missing>"),
                    throwable.getMessage());
        } finally {
            sendNotificationAck(stanza, type);
        }
    }

    /**
     * Clears the per-session pre-key deduplication guard so a new session can trigger fresh uploads.
     *
     * <p>Invoked by {@link NotificationDeviceDispatcher#reset()} on socket reconnect.
     */
    @Override
    public void reset() {
        preKeyUploadGuard.clear();
    }

    /**
     * Routes an {@code encrypt} notification to the per-child branch.
     *
     * <p>The first child tag selects the branch: {@code <count>} triggers a pre-key-low upload,
     * {@code <identity>} records an end-to-end identity change, and {@code <digest>} is recognised
     * but skipped. A stanza with no child, and any other child tag, is debug-logged and ignored.
     *
     * @implNote This implementation skips the {@code <digest>} branch with a debug log because
     * Cobalt has no equivalent of WA Web's digest-key verification; the double-ratchet session-state
     * envelope Cobalt persists already includes the digest.
     *
     * @param stanza the {@code <notification type="encrypt"/>} stanza
     */
    private void handleEncrypt(Stanza stanza) {
        var firstChild = stanza.getChild().orElse(null);
        if (firstChild == null) {
            return;
        }

        switch (firstChild.description()) {
            case "count" -> handlePreKeyLow(stanza, firstChild.getAttributeAsLong("value", 0L));
            case "digest" -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unsupported digest-key notification {0}",
                    stanza.getAttributeAsString("id", "<missing>"));
            case "identity" -> handleIdentityChange(stanza);
            default -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unsupported encrypt notification child {0}",
                    firstChild.description());
        }
    }

    /**
     * Uploads a fresh batch of pre-keys when the server reports the pre-key reserve is running low.
     *
     * <p>Returns early when the stanza has no id or when an upload for the same id is already in
     * progress; otherwise it uploads the requested number of pre-keys and releases the guard once
     * the upload completes.
     *
     * @implNote This implementation uses the stanza id as the deduplication key; a second
     * pre-key-low stanza with the same id arriving concurrently exits without re-uploading.
     *
     * @param stanza      the {@code <notification type="encrypt"/>} stanza
     * @param keysCount the server-reported remaining pre-key count
     */
    private void handlePreKeyLow(Stanza stanza, long keysCount) {
        var stanzaId = stanza.getAttributeAsString("id", null);
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
     * Records an end-to-end identity change for a remote device, wiping the existing Signal session
     * and forcing a sender-key rotation.
     *
     * <p>Companion-device changes (non-zero device id) and the local primary device are skipped.
     * When a {@code lid} attribute is present it is registered as a LID-PN mapping, and the
     * {@code display_name} attribute, when non-blank, updates the contact's chosen name. The handler
     * then marks the identity change, cleans up the Signal session, clears the sender-key
     * distribution for the participant, marks the user for key rotation, re-issues the local user's
     * trusted-contact token to the peer (so its rotated identity keeps a valid token), and fires
     * {@link LinkedDeviceIdentityChangedListener#onDeviceIdentityChanged} so the embedder can drive the
     * equivalent UI.
     *
     * @param stanza the {@code <notification type="encrypt"/>} stanza with an {@code <identity/>} child
     */
    private void handleIdentityChange(Stanza stanza) {
        var deviceJid = stanza.getAttributeAsJid("from").orElse(null);
        if (deviceJid == null || deviceJid.device() != 0) {
            return;
        }

        var userJid = deviceJid.toUserJid();
        var selfJid = whatsapp.store().accountStore().jid()
                .map(Jid::toUserJid)
                .orElse(null);
        if (selfJid != null && selfJid.equals(userJid)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Ignoring self primary identity-change notification");
            return;
        }

        var lid = stanza.getAttributeAsJid("lid")
                .map(Jid::toUserJid)
                .orElse(null);
        if (lid != null) {
            whatsapp.store().contactStore().registerLidMapping(userJid, lid);
        }

        var displayName = stanza.getAttributeAsString("display_name", null);
        var contact = whatsapp.store().contactStore().findContactByJid(userJid)
                .orElseGet(() -> whatsapp.store().contactStore().addNewContact(userJid));
        if (displayName != null && !displayName.isBlank()) {
            contact.setChosenName(displayName);
        }
        whatsapp.store().contactStore().addContact(contact);

        whatsapp.store().signalStore().markIdentityChange(deviceJid);
        whatsapp.store().signalStore().cleanupSignalSessions(deviceJid);
        whatsapp.store().signalStore().clearSenderKeyDistributionForParticipant(deviceJid);
        whatsapp.store().signalStore().markKeyRotation(userJid);
        // Re-hand our trusted-contact token to the peer whose device identity just changed, matching
        // WAWebHandleIdentityChange, so the peer's rotated identity keeps a valid token to validate our
        // future offers and messages. Fire-and-forget on a virtual thread; the reciprocal is not needed.
        var tokenPeer = userJid;
        Thread.ofVirtual().name("tc-token-identity-" + userJid)
                .start(() -> whatsapp.issueTrustedContactToken(tokenPeer));
        fireListeners(LinkedDeviceIdentityChangedListener.class, listener -> listener.onDeviceIdentityChanged(whatsapp, userJid, Set.of(deviceJid)));
    }

    /**
     * Decrypts the re-uploaded direct path delivered inside a {@code mediaretry} notification and
     * writes it to the original media message.
     *
     * <p>Returns early when the stanza carries an {@code <error>} child, lacks an {@code <encrypt>}
     * child, or when no matching media message is found. The {@code <encrypt>} child carries
     * {@code <enc_p>} (the AES-GCM-encrypted {@link MediaRetryNotificationSpec} blob) and
     * {@code <enc_iv>} (the IV); both, plus the message's media key, are required. On success the
     * decoded {@code directPath} clears the provider's URL and replaces its direct path so a
     * subsequent download hits the new CDN path.
     *
     * @implNote This implementation derives the AES key via HKDF-SHA256 with info string
     * {@code "WhatsApp Media Retry Notification"}, matching the WA crypto contract, uses the
     * {@code enc_iv} payload as the IV directly, and feeds the message key id as the GCM additional
     * authenticated data. Failures (missing media key, decryption error, decode error) are silently
     * swallowed because the notification cannot be retried by the client.
     *
     * @param stanza the {@code <notification type="mediaretry"/>} stanza
     */
    private void handleMediaRetry(Stanza stanza) {
        if (stanza.hasChild("error")) {
            return;
        }

        var encryptNode = stanza.getChild("encrypt").orElse(null);
        if (encryptNode == null) {
            return;
        }

        var message = findMessageById(stanza.getAttributeAsString("id", null));
        if (message == null || !(message.message().content() instanceof MediaProvider mediaProvider)) {
            return;
        }

        var mediaKey = mediaProvider.mediaKey().orElse(null);
        var encP = encryptNode.getChild("enc_p").flatMap(Stanza::toContentBytes).orElse(null);
        var encIv = encryptNode.getChild("enc_iv").flatMap(Stanza::toContentBytes).orElse(null);
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
     * Routes a {@code server} notification to the matching maintenance task based on the first child
     * tag.
     *
     * <p>A {@code <log>} child requests a crash-log upload, which Cobalt does not perform; an
     * {@code <abprops>} child triggers an AB-prop resync via {@link ABPropsService#sync()}. A stanza
     * with no child, and any other child tag, is debug-logged and ignored.
     *
     * @implNote This implementation does not run the crashlog upload because Cobalt does not
     * maintain a per-device crash log.
     *
     * @param stanza the {@code <notification type="server"/>} stanza
     */
    private void handleServer(Stanza stanza) {
        var firstChild = stanza.getChild().map(Stanza::description).orElse(null);
        switch (firstChild) {
            case "log" -> LOGGER.log(System.Logger.Level.WARNING,
                    "Ignoring server log-request notification");
            case "abprops" -> abPropsService.sync();
            case null, default -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unknown server notification child {0}", firstChild);
        }
    }

    /**
     * Delivers the device-switch OTP code to listeners and commits the {@code WaOldCode} WAM event.
     *
     * <p>Returns early when the stanza lacks a {@code <wa_old_registration>} child, and drops the
     * notification when the code or expiry is missing or the OTP has already expired against the
     * current epoch second. A numeric code is delivered via
     * {@link LinkedRegistrationCodeListener#onRegistrationCode}; the WAM event is committed afterward with
     * the local device id.
     *
     * @implNote This implementation parses the numeric OTP and debug-logs non-numeric values because
     * the {@link LinkedRegistrationCodeListener#onRegistrationCode} callback requires a {@code long}, whereas
     * WA Web passes the raw string through.
     *
     * @param stanza the {@code <notification type="registration"/>} stanza
     */
    private void handleRegistration(Stanza stanza) {
        var registration = stanza.getChild("wa_old_registration").orElse(null);
        if (registration == null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Ignoring unsupported registration notification {0}",
                    stanza.getAttributeAsString("id", "<missing>"));
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
            fireListeners(LinkedRegistrationCodeListener.class, listener -> listener.onRegistrationCode(whatsapp, numericCode));
        } catch (NumberFormatException exception) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring non-numeric device-switch code {0}",
                    code);
        }

        var meDeviceId = whatsapp.store().accountStore().jid()
                .map(Jid::device)
                .map(String::valueOf)
                .orElse(null);
        wamService.commit(new WaOldCodeEventBuilder()
                .deviceId(meDeviceId)
                .build());
    }

    /**
     * Fans the callback out to every registered listener of the given type on
     * its own virtual thread.
     *
     * <p>Used by {@link #handleIdentityChange(Stanza)} and {@link #handleRegistration(Stanza)}.
     *
     * @param type     the per-event listener interface to dispatch against
     * @param consumer the callback to invoke against each matching listener
     * @param <L>      the per-event listener interface
     */
    private <L extends WhatsAppListener> void fireListeners(Class<L> type, Consumer<L> consumer) {
        for (var listener : whatsapp.store().listeners()) {
            if (type.isInstance(listener)) {
                var typed = type.cast(listener);
                Thread.startVirtualThread(() -> consumer.accept(typed));
            }
        }
    }

    /**
     * Searches chats, status messages, and newsletters for the message matching the given stanza id.
     *
     * <p>Used only by {@link #handleMediaRetry(Stanza)} to locate the original media message whose
     * direct path needs replacing. Returns {@code null} for a {@code null} id and when no message
     * matches in any of the three collections.
     *
     * @param id the stanza identifier
     * @return the matching {@link MessageInfo}, or {@code null} when not found
     */
    private MessageInfo findMessageById(String id) {
        if (id == null) {
            return null;
        }

        for (var chat : whatsapp.store().chatStore().chats()) {
            var message = whatsapp.store().chatStore().findMessageById(chat, id).orElse(null);
            if (message != null) {
                return message;
            }
        }

        var statusMessage = whatsapp.store().chatStore().findStatusById(id).orElse(null);
        if (statusMessage != null) {
            return statusMessage;
        }

        for (var newsletter : whatsapp.store().chatStore().newsletters()) {
            var message = whatsapp.store().chatStore().findMessageById(newsletter, id).orElse(null);
            if (message != null) {
                return message;
            }
        }

        return null;
    }

    /**
     * Sends the protocol-level ACK with the {@code type} attribute reflected from the original
     * stanza.
     *
     * <p>The {@code encrypt} type omits the {@code type} attribute on the ack; every other type
     * reflects it. The {@code mediaretry} ack additionally reflects the {@code participant}
     * attribute when present.
     *
     * @param stanza the original {@code <notification>} stanza
     * @param type the notification type from the {@code type} attribute
     */
    private void sendNotificationAck(Stanza stanza, String type) {
        var builder = ackSender.ack(AckClass.NOTIFICATION, stanza);
        if ("encrypt".equals(type)) {
            builder.type(null);
        } else {
            builder.type(type);
        }
        if ("mediaretry".equals(type)) {
            var participant = stanza.getAttributeAsJid("participant").orElse(null);
            if (participant != null) {
                builder.participant(participant);
            }
        }
        builder.send();
    }
}
