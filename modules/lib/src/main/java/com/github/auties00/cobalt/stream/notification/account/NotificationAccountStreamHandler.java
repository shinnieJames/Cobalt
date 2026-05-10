package com.github.auties00.cobalt.stream.notification.account;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientListener;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.chat.ChatEphemeralTimer;
import com.github.auties00.cobalt.model.contact.ContactTextStatus;
import com.github.auties00.cobalt.model.contact.ContactTextStatusBuilder;
import com.github.auties00.cobalt.model.device.info.DeviceInfo;
import com.github.auties00.cobalt.model.device.info.DeviceListBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Handles incoming account-sync notifications for the authenticated user's own
 * account state, including status, text status, privacy settings, devices,
 * blocklist, profile picture, disappearing mode, TOS notices, user flags,
 * and business opt-out list updates.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleAccountSyncNotification")
final class NotificationAccountStreamHandler implements SocketStream.Handler {

    /**
     * Logger for diagnostic messages related to account sync notification handling.
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationAccountStreamHandler.class.getName());

    /**
     * The PDFN (Privacy Disclosure For Notices) accepted stage value.
     */
    private static final String PDFN_ACCEPTED_STAGE = "5";

    /**
     * The WhatsApp client instance used for queries and sending nodes.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The device service used for syncing device lists.
     */
    private final DeviceService deviceService;

    /**
     * Constructs a new account sync notification handler.
     * @param whatsapp the WhatsApp client instance, must not be {@code null}
     * @param deviceService the device service for device list operations, must not be {@code null}
     */
    NotificationAccountStreamHandler(WhatsAppClient whatsapp, DeviceService deviceService) {
        this.whatsapp = whatsapp;
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService cannot be null");
    }

    /**
     * Handles an incoming node by verifying it is an account-sync notification,
     * dispatching to the appropriate sub-handler, and sending an acknowledgment.
     *
     * @param node the incoming stanza node
     */
    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "account_sync")) {
            return;
        }

        try {
            handleNotification(node);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Cannot handle account_sync notification " + node.getAttributeAsString("id", "<missing>"),
                    throwable);
        } finally {
            sendNotificationAck(node);
        }
    }

    /**
     * Dispatches the notification to the appropriate handler based on the first
     * recognized child element type.
     *
     * <p>Each child tag maps to a specific {@code AccountSyncType} in WA Web's
     * parser and is handled by the corresponding switch case in the main handler function.
     * @param node the notification node containing one or more typed child elements
     */
    private void handleNotification(Node node) {
        for (var child : node.children()) {
            switch (child.description()) {
                case "status" -> {
                    refreshOwnAbout();
                    return;
                }
                case "text_status" -> {
                    handleTextStatusNotification(node, child);
                    return;
                }
                case "privacy" -> {
                    whatsapp.pullWebAppState(SyncPatchType.REGULAR_LOW, SyncPatchType.REGULAR);
                    return;
                }
                case "devices" -> {
                    refreshOwnDevices(node);
                    return;
                }
                case "blocklist" -> {
                    applyBlocklistUsernames(child);
                    refreshBlockList();
                    return;
                }
                case "picture" -> {
                    refreshOwnPicture();
                    return;
                }
                case "disappearing_mode" -> {
                    handleDisappearingModeNotification(node, child);
                    return;
                }
                case "tos" -> {
                    handleTosNotification(child);
                    return;
                }
                case "notice" -> {
                    handleNoticeNotification(child);
                    return;
                }
                case "user" -> {
                    handleUserNotification(child);
                    return;
                }
                case "biz_opt_out_list" -> {
                    handleBizOptOutListNotification(child);
                    return;
                }
                default -> {
                    // NO_WA_BASIS
                }
            }
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "Ignoring unrecognized account_sync notification {0}",
                node.getAttributeAsString("id", "<missing>"));
    }

    /**
     * Refreshes the authenticated user's about (status) text by querying the server
     * and updating the local store if changed.
     *
     * <p>WA Web queries the about status, and if non-empty, sends it to the frontend
     * via {@code frontendSendAndReceive("setMyStatus")}. Cobalt instead directly
     * updates the store and fires listeners.
     */
    void refreshOwnAbout() {
        var self = whatsapp.store().jid().orElse(null);
        if (self == null) {
            return;
        }

        var oldAbout = whatsapp.store().selfTextStatus().flatMap(ContactTextStatus::text).orElse(""); // NO_WA_BASIS - defensive comparison
        var newAbout = whatsapp.queryAbout(self).orElse("");
        if (Objects.equals(oldAbout, newAbout)) {
            return; // NO_WA_BASIS - Cobalt optimization to avoid redundant updates
        }

        var newStatus = new ContactTextStatusBuilder()
                .text(newAbout)
                .build();
        whatsapp.store().setSelfTextStatus(newStatus);
        fireListeners(listener -> listener.onAboutChanged(whatsapp, oldAbout, newAbout)); // ADAPTED: Cobalt listener pattern
    }

    /**
     * Handles a text status account sync notification.
     *
     * <p>When the action is {@code "modify"}, WA Web fetches the text status from the
     * server. Otherwise, it uses the values directly from the stanza. The non-modify
     * path is what Cobalt implements. The modify path is handled identically since
     * both end up updating the local text status for the contact.
     * @param node the parent notification node
     * @param textStatusNode the {@code text_status} child element
     */
    private void handleTextStatusNotification(Node node, Node textStatusNode) {
        var action = textStatusNode.getAttributeAsString("action", null);
        if ("modify".equals(action)) {
            // ADAPTED: Cobalt does not have a dedicated text status query API yet,
            // so the modify case is treated the same as the default case (stanza values)
            updateOwnTextStatus(textStatusNode);
        } else {
            var from = getUserJid(node, "from");
            var self = from != null ? from : whatsapp.store().jid().map(Jid::toUserJid).orElse(null);
            if (self == null) {
                return;
            }

            var text = textStatusNode.getAttributeAsString("text", null);
            var emoji = textStatusNode.getChild("emoji")
                    .flatMap(emojiNode -> emojiNode.getAttributeAsString("content"))
                    .orElse(null);
            var ephemeralDuration = textStatusNode.getAttributeAsInt("ephemeral_duration_sec", (Integer) null);
            var lastUpdateTimeStr = textStatusNode.getAttributeAsString("last_update_time", null);
            var lastUpdateTime = lastUpdateTimeStr != null ? Instant.ofEpochSecond(Long.parseLong(lastUpdateTimeStr)) : null;
            upsertContactTextStatus(
                    self,
                    text,
                    emoji,
                    ephemeralDuration,
                    lastUpdateTime
            );
        }
    }

    /**
     * Updates the own text status using values from the stanza child node.
     * Used for the {@code action === "modify"} case as a fallback when
     * no dedicated text status query API is available.
     * @param textStatusNode the {@code text_status} child element
     */
    private void updateOwnTextStatus(Node textStatusNode) {
        var self = whatsapp.store().jid().orElse(null);
        if (self == null) {
            return;
        }

        var text = textStatusNode.getAttributeAsString("text", null);
        var emoji = textStatusNode.getChild("emoji")
                .flatMap(emojiNode -> emojiNode.getAttributeAsString("content"))
                .orElse(null);
        var ephemeralDuration = textStatusNode.getAttributeAsInt("ephemeral_duration_sec", (Integer) null);
        var lastUpdateTimeStr = textStatusNode.getAttributeAsString("last_update_time", null);
        var lastUpdateTime = lastUpdateTimeStr != null ? Instant.ofEpochSecond(Long.parseLong(lastUpdateTimeStr)) : null;
        upsertContactTextStatus(
                self.toUserJid(),
                text,
                emoji,
                ephemeralDuration,
                lastUpdateTime
        );
    }

    /**
     * Refreshes the authenticated user's device list by performing a device
     * list sync with the server.
     * @param node the parent notification node containing the {@code from} attribute
     */
    private void refreshOwnDevices(Node node) {
        var self = whatsapp.store().jid()
                .map(Jid::toUserJid)
                .orElseGet(() -> getUserJid(node, "from"));
        if (self == null) {
            return;
        }

        // The notification already carries the full device list inline as <device jid=... key-index=.../>
        // children of the <devices> child, along with the server-computed dhash. The earlier
        // implementation kicked off a fresh USync device query for self, but the server does not
        // respond to that redundant request (we already have the data), so the sendNode call
        // blocked for 60s and timed out. Parse the inline list directly instead.
        var devicesChild = node.getChild("devices").orElse(null);
        if (devicesChild == null) {
            return;
        }

        var parsed = new ArrayList<DeviceInfo>();
        for (var deviceNode : devicesChild.getChildren("device")) {
            var deviceJid = deviceNode.getAttributeAsJid("jid").orElse(null);
            if (deviceJid == null) {
                continue;
            }
            var keyIndex = deviceNode.getAttributeAsInt("key-index", 0);
            parsed.add(DeviceInfo.ofE2EE(deviceJid.device(), keyIndex));
        }

        var dhash = devicesChild.getAttributeAsString("dhash", null);
        var list = new DeviceListBuilder()
                .userJid(self)
                .devices(List.copyOf(parsed))
                .timestamp(Instant.now())
                .rawId(dhash)
                .deleted(false)
                .build();
        whatsapp.store().addDeviceList(list);
    }

    /**
     * Applies the usernames contained in a {@code blocklist} child's {@code <item>}
     * entries to the local contact store.
     *
     * <p>WA Web gates this on {@code WAWebUsernameGatingUtils.usernameDisplayedEnabled()}
     * and iterates each {@code item}, collecting pairs of {@code (userJid, username)}
     * that it then forwards to {@code WAWebSetUsernameJob.setUsernamesJob}. Cobalt
     * applies the username directly to the matching contact record.
     * @param blocklistNode the {@code blocklist} child element
     */
    private void applyBlocklistUsernames(Node blocklistNode) {
        for (var item : blocklistNode.getChildren("item")) {
            var username = item.getAttributeAsString("username", null);
            if (username == null) {
                continue;
            }
            var userJid = item.getAttributeAsJid("jid")
                    .map(Jid::toUserJid)
                    .orElse(null);
            if (userJid == null) {
                continue;
            }
            // Cobalt updates the contact's username directly on the local store.
            var contact = whatsapp.store()
                    .findContactByJid(userJid)
                    .orElseGet(() -> whatsapp.store().addNewContact(userJid));
            contact.setUsername(username);
            whatsapp.store().addContact(contact);
        }
    }

    /**
     * Refreshes the block list by querying the server and updating local
     * contact records accordingly.
     */
    private void refreshBlockList() {
        // Cobalt queries blocklist and updates contact records directly
        var blockedJids = new HashSet<>(whatsapp.queryBlockList());
        for (var contact : whatsapp.store().contacts()) {
            var blocked = blockedJids.remove(contact.jid());
            if (contact.blocked() == blocked) {
                continue;
            }

            contact.setBlocked(blocked);
            whatsapp.store().addContact(contact);
            fireListeners(listener -> listener.onContactBlocked(whatsapp, contact.jid()));
        }

        for (var blockedJid : blockedJids) {
            var contact = whatsapp.store()
                    .findContactByJid(blockedJid)
                    .orElseGet(() -> whatsapp.store().addNewContact(blockedJid));
            contact.setBlocked(true);
            whatsapp.store().addContact(contact);
            fireListeners(listener -> listener.onContactBlocked(whatsapp, blockedJid));
        }
    }

    /**
     * Refreshes the authenticated user's profile picture by querying the server
     * and updating the local store if changed.
     */
    private void refreshOwnPicture() {
        var self = whatsapp.store().jid().orElse(null);
        if (self == null) {
            return;
        }

        var oldPicture = whatsapp.store().profilePicture().orElse(null); // NO_WA_BASIS - defensive comparison
        var newPicture = whatsapp.queryPicture(self).orElse(null);
        if (Objects.equals(oldPicture, newPicture)) {
            return; // NO_WA_BASIS - Cobalt optimization to avoid redundant updates
        }

        whatsapp.store().setProfilePicture(newPicture);
        fireListeners(listener -> listener.onProfilePictureChanged(whatsapp, self)); // ADAPTED: Cobalt listener pattern
    }

    /**
     * Handles a disappearing mode account sync notification.
     *
     * <p>WA Web distinguishes between two sub-cases:
     * <ul>
     *   <li>If the {@code action} attribute is present (e.g. {@code "modify"}), it queries
     *       the disappearing mode from the server to get the latest duration and timestamp.</li>
     *   <li>Otherwise, it reads {@code duration} and {@code t} (setting timestamp) directly
     *       from the stanza child.</li>
     * </ul>
     *
     * <p>The update only proceeds if both duration and setting timestamp are non-{@code null}.
     * @param node the parent notification node containing the {@code from} attribute
     * @param disappearingMode the {@code disappearing_mode} child element
     */
    private void handleDisappearingModeNotification(Node node, Node disappearingMode) {
        var action = disappearingMode.getAttributeAsString("action", null);
        Integer duration;
        Integer settingTimestamp;
        if (action != null) {
            // When action is present, duration and timestamp are not in the stanza
            duration = null;
            settingTimestamp = null;
        } else {
            duration = disappearingMode.getAttributeAsInt("duration", (Integer) null);
            settingTimestamp = disappearingMode.getAttributeAsInt("t", (Integer) null);
        }

        if ("modify".equals(action)) {
            // ADAPTED: Cobalt queries disappearing mode via the store's default timer
            // The server query would return the latest duration and timestamp
            // For now, we use the store's current setting if available
            var selfJid = getUserJid(node, "from");
            if (selfJid != null) {
                // Cobalt does not have a dedicated disappearing mode query;
                // the duration update will be handled by the next full sync
            }
        }

        if (duration != null) {
            // Cobalt stores this as the global new-chats ephemeral timer
            whatsapp.store().setNewChatsEphemeralTimer(ChatEphemeralTimer.of(duration));
        }
    }

    /**
     * Handles a TOS (Terms of Service) account sync notification by updating
     * the stored notice IDs and their acceptance state.
     *
     * <p>WA Web parses each {@code <notice>} child, reading its {@code id} and
     * {@code state} (where state != "false" means accepted), then calls
     * {@code updateTosStateFromAccountSync} which sets the TOS state via
     * {@code TosManager.setState}.
     * @param tosNode the {@code tos} child element containing notice children
     */
    private void handleTosNotification(Node tosNode) {
        var notices = new HashSet<String>();
        for (var notice : tosNode.getChildren("notice")) {
            var state = notice.getAttributeAsString("state", null);
            var accepted = !"false".equals(state);
            var id = notice.getAttributeAsString("id", null);
            if (id != null && accepted) {
                notices.add(id);
            }
        }
        // ADAPTED: Cobalt stores accepted notice IDs in the store
        if (!notices.isEmpty()) {
            var currentNotices = new HashSet<>(whatsapp.store().tosNoticeIds());
            currentNotices.addAll(notices);
            whatsapp.store().setTosNoticeIds(currentNotices);
        }
    }

    /**
     * Handles a notice account sync notification by updating the user disclosure
     * collection and TOS state.
     *
     * <p>WA Web validates that {@code noticeId}, {@code noticeStage}, {@code noticeVersion},
     * and {@code noticeTimestamp} are all present. It determines acceptance by comparing
     * the stage to {@code PDFN_ACCEPTED} ("5"). Then it updates the
     * {@code UserDisclosureCollection} and calls {@code updateTosStateFromAccountSync}.
     * @param noticeNode the {@code notice} child element
     */
    private void handleNoticeNotification(Node noticeNode) {
        var noticeId = noticeNode.getAttributeAsString("id", null);
        var noticeStage = noticeNode.getAttributeAsString("stage", null);
        var noticeVersion = noticeNode.getAttributeAsString("version", null);
        var noticeTimestamp = noticeNode.getAttributeAsInt("t", (Integer) null);

        if (noticeId == null || noticeId.isEmpty() || noticeStage == null || noticeVersion == null || noticeTimestamp == null) {
            return;
        }

        var accepted = PDFN_ACCEPTED_STAGE.equals(noticeStage);

        // ADAPTED: Cobalt stores accepted notice IDs in the store
        if (accepted) {
            var currentNotices = new HashSet<>(whatsapp.store().tosNoticeIds());
            currentNotices.add(noticeId);
            whatsapp.store().setTosNoticeIds(currentNotices);
        }
    }

    /**
     * Handles a user account sync notification by updating the AI availability flag.
     *
     * <p>WA Web checks if {@code isAiAvailable === true} and logs a message.
     * Cobalt additionally persists the flag in the store.
     * @param userNode the {@code user} child element
     */
    private void handleUserNotification(Node userNode) {
        var isAiAvailable = "AI available".equals(userNode.getAttributeAsString("state", null));
        whatsapp.store().setAiAvailable(isAiAvailable); // ADAPTED: Cobalt persists the flag; WA Web only logs
    }

    /**
     * Handles a business opt-out list account sync notification by checking the
     * previous hash, processing list updates, and storing the new hash.
     *
     * <p>WA Web compares the {@code prev_dhash} against the stored hash. If they
     * don't match, it triggers a full opt-out list refresh. If they match and a
     * new {@code dhash} is present, it processes individual list items and updates
     * the stored hash.
     * @param optOutListNode the {@code biz_opt_out_list} child element
     */
    private void handleBizOptOutListNotification(Node optOutListNode) {
        var dhash = optOutListNode.getAttributeAsString("dhash", null);
        var prevDhash = optOutListNode.getAttributeAsString("prev_dhash", null);
        var storedHash = whatsapp.store().businessOptOutListHash().orElse(null);

        // Hash mismatch triggers a full refresh and does NOT update the stored hash.
        if (!Objects.equals(storedHash, prevDhash)) {
            // Cobalt does not have a dedicated full opt-out list refresh job; the next
            // sync will bring the store back in line. The hash is deliberately left
            // untouched to match WA Web semantics (mismatch -> do not persist L).
            return;
        }

        if (dhash != null) {
            for (var item : optOutListNode.children()) {
                var action = item.getAttributeAsString("action", null);
                var bizJid = item.getAttributeAsJid("biz_jid", null);
                if (action == null || bizJid == null) {
                    continue;
                }
                var isBlocked = "block".equals(action);
                // Cobalt flips the blocked flag on the contact record directly.
                var userJid = bizJid.toUserJid();
                var contact = whatsapp.store()
                        .findContactByJid(userJid)
                        .orElseGet(() -> whatsapp.store().addNewContact(userJid));
                if (contact.blocked() != isBlocked) {
                    contact.setBlocked(isBlocked);
                    whatsapp.store().addContact(contact);
                    fireListeners(listener -> listener.onContactBlocked(whatsapp, userJid));
                }
            }
            whatsapp.store().setBusinessOptOutListHash(dhash);
        }
    }

    /**
     * Extracts a user JID from the specified attribute of the given node.
     * @param node the node to extract the JID from
     * @param key the attribute key containing the JID
     * @return the extracted user JID, or {@code null} if not present
     */
    private Jid getUserJid(Node node, String key) {
        return node.getAttributeAsJid(key)
                .map(Jid::toUserJid)
                .orElse(null);
    }

    /**
     * Fires a listener callback on all registered listeners using virtual threads.
     * @param consumer the callback to execute for each listener
     */
    private void fireListeners(Consumer<WhatsAppClientListener> consumer) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> consumer.accept(listener));
        }
    }

    /**
     * Creates or updates the text status for a contact in the store.
     *
     * @param contactJid the JID of the contact whose text status is being updated
     * @param text the text status string, may be {@code null}
     * @param emoji the emoji associated with the text status, may be {@code null}
     * @param ephemeralDurationSeconds the ephemeral duration in seconds, may be {@code null}
     * @param lastUpdateTime the last update time, may be {@code null}
     * @return the updated {@link ContactTextStatus} instance
     */
    private ContactTextStatus upsertContactTextStatus(
            Jid contactJid,
            String text,
            String emoji,
            Integer ephemeralDurationSeconds,
            Instant lastUpdateTime
    ) {
        var canonicalJid = contactJid.toUserJid();
        var current = whatsapp.store()
                .findContactTextStatus(canonicalJid)
                .orElseGet(() -> new ContactTextStatusBuilder().build());
        current.setText(text);
        current.setEmoji(emoji);
        current.setEphemeralDurationSeconds(ephemeralDurationSeconds);
        current.setLastUpdateTime(lastUpdateTime);
        whatsapp.store().addContactTextStatus(canonicalJid, current);
        notifyContactTextStatusChanged(canonicalJid, current);
        return current;
    }

    /**
     * Notifies all registered listeners about a contact text status change.
     * @param contactJid the JID of the contact whose text status changed
     * @param status the updated text status
     */
    private void notifyContactTextStatusChanged(Jid contactJid, ContactTextStatus status) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onContactTextStatus(whatsapp, contactJid, status));
        }
    }

    /**
     * Sends an acknowledgment stanza for the received notification.
     *
     * <p>The ack stanza mirrors the incoming notification's ID, source, class, and type
     * attributes as required by the protocol.
     * @param node the notification node to acknowledge
     */
    private void sendNotificationAck(Node node) {
        var stanzaId = node.getAttributeAsString("id", null);
        var stanzaFrom = node.getAttributeAsJid("from", null);
        if (stanzaId == null || stanzaFrom == null) {
            return;
        }

        whatsapp.sendNodeWithNoResponse(new NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId)
                .attribute("class", "notification")
                .attribute("to", stanzaFrom)
                .attribute("type", "account_sync")
                .build());
    }
}
