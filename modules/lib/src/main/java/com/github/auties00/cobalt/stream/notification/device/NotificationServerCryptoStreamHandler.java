package com.github.auties00.cobalt.stream.notification.device;

import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Handles server-issued cryptographic notifications: {@code encrypt}
 * (pre-key low, digest key, identity change), {@code mediaretry}
 * (re-uploaded CDN URL delivery), {@code server} (log upload and AB
 * prop sync requests), and {@code registration} (device-switch OTP).
 *
 * @apiNote
 * Dispatched by {@link NotificationDeviceDispatcher}. Most branches
 * trigger a side-effect on the local crypto or AB state and do not
 * require a per-user mutation: pre-key low uploads more pre-keys to
 * the server, identity change wipes the Signal session and fires
 * {@link WhatsAppClientListener#onDeviceIdentityChanged}, media retry
 * decrypts the new direct path and writes it onto the message, server
 * runs the matching maintenance task, and registration delivers the
 * device-switch OTP to listeners.
 *
 * @implNote
 * This implementation merges six WA Web modules
 * ({@code WAWebHandlePreKeyLow}, {@code WAWebHandleIdentityChange},
 * {@code WAWebHandleMediaRetryNotification},
 * {@code WAWebHandleServerNotification},
 * {@code WAWebHandleDeviceSwitchingNotification},
 * {@code WAWebHandleDigestKey}) under one Cobalt handler because they
 * all share the per-stanza ACK pattern (read {@code type}, branch,
 * ACK in {@code finally}) and so live more comfortably as one class
 * than six near-empty ones.
 */
@WhatsAppWebModule(moduleName = "WAWebHandlePreKeyLow")
@WhatsAppWebModule(moduleName = "WAWebHandleIdentityChange")
@WhatsAppWebModule(moduleName = "WAWebHandleMediaRetryNotification")
@WhatsAppWebModule(moduleName = "WAWebHandleServerNotification")
@WhatsAppWebModule(moduleName = "WAWebHandleDeviceSwitchingNotification")
@WhatsAppWebModule(moduleName = "WAWebHandleDigestKey")
final class NotificationServerCryptoStreamHandler implements SocketStream.Handler {
    /**
     * Logger used for warnings and debug messages about server-crypto
     * notification handling.
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationServerCryptoStreamHandler.class.getName());

    /**
     * Set of notification {@code type} values routed to this handler.
     *
     * @apiNote
     * Used by {@link #handle(Node)} as the first-line filter. Any
     * stanza whose type is outside this set returns without
     * side-effects.
     */
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "encrypt",
            "mediaretry",
            "server",
            "registration"
    );

    /**
     * The {@link WhatsAppClient} used for store reads, Signal session
     * mutations, pre-key uploads, and ack sends.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The {@link ABPropsService} used by the {@code server/abprops}
     * branch to re-sync AB props from the server.
     */
    private final ABPropsService abPropsService;

    /**
     * Deduplication guard that prevents two concurrent pre-key uploads
     * for the same {@code stanzaId} within one session.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandlePreKeyLow} top-level {@code s} set which
     * stores the {@code n} session-id key during the upload and
     * removes it on completion. Cleared by {@link #reset()} when the
     * stream reconnects.
     */
    private final Set<String> preKeyUploadGuard;

    /**
     * The {@link WamService} used to commit the {@code WaOldCode}
     * event after a successful device-switch OTP delivery.
     */
    private final WamService wamService;

    /**
     * The {@link AckSender} used to ship the post-processing
     * {@code <ack class="notification">} stanza (special-casing the
     * {@code encrypt} branch to omit {@code type} and the
     * {@code mediaretry} branch to include {@code participant}).
     */
    private final AckSender ackSender;

    /**
     * Constructs the handler with shared dependencies.
     *
     * @apiNote
     * Called once by {@link NotificationDeviceDispatcher}; embedders
     * do not instantiate this handler directly.
     *
     * @param whatsapp       the {@link WhatsAppClient}
     * @param abPropsService the {@link ABPropsService}
     * @param wamService     the {@link WamService}
     */
    NotificationServerCryptoStreamHandler(WhatsAppClient whatsapp, ABPropsService abPropsService, WamService wamService, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.abPropsService = abPropsService;
        this.wamService = wamService;
        this.ackSender = ackSender;
        this.preKeyUploadGuard = ConcurrentHashMap.newKeySet();
    }

    /**
     * Routes the notification to its per-type sub-handler and always
     * sends the protocol-level ACK.
     *
     * @apiNote
     * Invoked by {@link NotificationDeviceDispatcher}. Stanzas whose
     * type is outside {@link #SUPPORTED_TYPES} return without
     * side-effects.
     *
     * @param node the incoming {@code <notification>} stanza
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
     * Clears the per-session pre-key deduplication guard so a new
     * session can trigger fresh uploads.
     *
     * @apiNote
     * Invoked by {@link NotificationDeviceDispatcher#reset()} on
     * socket reconnect. Embedders do not call this directly.
     */
    @Override
    public void reset() {
        preKeyUploadGuard.clear();
    }

    /**
     * Routes an {@code encrypt} notification to the per-child branch.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebCommsHandleLoggedInStanza} {@code encrypt} switch:
     * {@code <count>} routes to
     * {@code WAWebHandlePreKeyLow}, {@code <digest>} to
     * {@code WAWebHandleDigestKey}, {@code <identity>} to
     * {@code WAWebHandleIdentityChange}.
     *
     * @implNote
     * This implementation skips the {@code <digest>} branch with a
     * debug log; Cobalt has no equivalent of WA Web's
     * digest-key verification because the
     * {@code WAWebSignalStoreApi.waSignalStore} double-ratchet state
     * Cobalt uses already includes the digest in its session-state
     * envelope.
     *
     * @param node the {@code <notification type="encrypt"/>} stanza
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
     * Uploads a fresh batch of pre-keys when the server reports the
     * pre-key reserve is running low.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandlePreKeyLow.c} which sets
     * {@code waSignalStore.setServerHasPreKeys(false)}, waits for the
     * offline-delivery barrier, then calls
     * {@code WAWebUploadPreKeysJob.uploadPreKeys()}.
     *
     * @implNote
     * This implementation uses the stanza id as the deduplication
     * key; a second pre-key-low stanza with the same id arriving
     * concurrently exits without re-uploading.
     *
     * @param node      the {@code <notification type="encrypt"/>} stanza
     * @param keysCount the server-reported remaining pre-key count
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
     * Records an end-to-end identity change for a remote device,
     * wiping the existing Signal session and forcing a sender-key
     * rotation.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandleIdentityChange.handleE2eIdentityChange} which:
     * <ul>
     *   <li>Skips companion-device changes (non-zero device id).</li>
     *   <li>Skips the local primary device.</li>
     *   <li>Wipes the Signal session via
     *       {@code Session.deleteRemoteInfo}.</li>
     *   <li>Marks the user's broadcast and status sender keys for
     *       rotation.</li>
     *   <li>Fires the security-code-changed notification.</li>
     * </ul>
     * Cobalt collapses these into store mutations and fires
     * {@link WhatsAppClientListener#onDeviceIdentityChanged} so the
     * embedder can drive the equivalent UI.
     *
     * @param node the {@code <notification type="encrypt"/>} stanza with an {@code <identity/>} child
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
     * Decrypts the re-uploaded direct path delivered inside a
     * {@code mediaretry} notification and writes it to the original
     * media message.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandleMediaRetryNotification.default}: the
     * notification carries
     * {@code <encrypt><enc_p>...</enc_p><enc_iv>...</enc_iv></encrypt>}
     * which is the AES-GCM-encrypted
     * {@link MediaRetryNotificationSpec} blob keyed off the message's
     * media key. The decrypted blob's {@code directPath} replaces the
     * provider's URL so a subsequent download hits the new CDN path.
     *
     * @implNote
     * This implementation derives the AES key via HKDF-SHA256 with
     * info string {@code "WhatsApp Media Retry Notification"},
     * matching the WA crypto contract. The IV is the
     * {@code enc_iv} payload directly. Failures (missing media key,
     * decryption error, decode error) are silently swallowed because
     * the notification cannot be retried by the client.
     *
     * @param node the {@code <notification type="mediaretry"/>} stanza
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
     * Routes a {@code server} notification to the matching maintenance
     * task based on the first child tag.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandleServerNotification.m(type)}:
     * {@code <log>} triggers
     * {@code WAWebCrashlog.upload(SERVER_REQUESTED)}; {@code <abprops>}
     * triggers
     * {@code WAWebAbPropsSyncJob.syncABPropsTask({shouldSendHash: false})}.
     *
     * @implNote
     * This implementation does not run the crashlog upload because
     * Cobalt does not maintain a per-device crash log. The
     * {@code abprops} branch routes through {@link ABPropsService#sync()}
     * which re-runs the AB-prop fetch.
     *
     * @param node the {@code <notification type="server"/>} stanza
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
     * Delivers the device-switch OTP code to listeners and commits the
     * {@code WaOldCode} WAM event.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandleDeviceSwitchingNotification.default} which
     * fires {@code showDeviceSwitchOtp} to the frontend and commits
     * the equivalent WAM event. The notification is dropped when the
     * OTP has already expired (the server tolerates a small clock
     * skew and clients must re-check).
     *
     * @implNote
     * This implementation parses the numeric OTP and ignores
     * non-numeric values with a debug log. WA Web passes the raw
     * string through; Cobalt requires a {@code long} for the
     * {@link WhatsAppClientListener#onRegistrationCode} callback.
     *
     * @param node the {@code <notification type="registration"/>} stanza
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
     * Fans the callback out to every registered listener on its own
     * virtual thread.
     *
     * @apiNote
     * Internal helper used by {@link #handleIdentityChange(Node)} and
     * {@link #handleRegistration(Node)}.
     *
     * @param consumer the callback to invoke against each listener
     */
    private void fireListeners(Consumer<WhatsAppClientListener> consumer) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> consumer.accept(listener));
        }
    }

    /**
     * Searches chats, status messages, and newsletters for the message
     * matching the given stanza id.
     *
     * @apiNote
     * Used only by {@link #handleMediaRetry(Node)} to locate the
     * original media message whose direct path needs replacing. The
     * three-tier search matches WA Web's
     * {@code WAWebMsgCollection.get(msgKey)} fallback chain through
     * the chat, status, and newsletter collections.
     *
     * @param id the stanza identifier
     * @return the matching {@link MessageInfo}, or {@code null} when not found
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
     * Sends the protocol-level ACK with the type attribute reflected
     * from the original stanza (except for {@code encrypt} which uses
     * no type per WA Web).
     *
     * @apiNote
     * Fire-and-forget; mirrors WA Web's per-module ack-builders
     * which pin the type per handler. The {@code encrypt} type
     * deliberately omits the {@code type} attribute on the ack to
     * match
     * {@code WAWebHandlePreKeyLow}'s {@code wap("ack", {to, id, class})}.
     * The {@code mediaretry} ACK also reflects the {@code participant}
     * attribute when present, matching
     * {@code WAWebHandleMediaRetryNotification}.
     *
     * @param node the original {@code <notification>} stanza
     * @param type the notification type from the {@code type} attribute
     */
    private void sendNotificationAck(Node node, String type) {
        var builder = ackSender.ack(AckClass.NOTIFICATION, node);
        if ("encrypt".equals(type)) {
            builder.type(null);
        } else {
            builder.type(type);
        }
        if ("mediaretry".equals(type)) {
            var participant = node.getAttributeAsJid("participant").orElse(null);
            if (participant != null) {
                builder.participant(participant);
            }
        }
        builder.send();
    }
}
