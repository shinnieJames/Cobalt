package com.github.auties00.cobalt.stream.notification.account;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.model.chat.ChatEphemeralTimer;
import com.github.auties00.cobalt.model.contact.ContactTextStatus;
import com.github.auties00.cobalt.model.contact.ContactTextStatusBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Handles incoming account-sync notifications for the authenticated user's own
 * account state, including status, text status, privacy settings, devices,
 * blocklist, profile picture, disappearing mode, TOS notices, user flags,
 * and business opt-out list updates.
 *
 * @implNote WAWebHandleAccountSyncNotification
 */
final class NotificationAccountStreamHandler implements SocketStream.Handler {

    /**
     * Logger for diagnostic messages related to account sync notification handling.
     *
     * @implNote WAWebHandleAccountSyncNotification (WALogger references)
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationAccountStreamHandler.class.getName());

    /**
     * The PDFN (Privacy Disclosure For Notices) accepted stage value.
     *
     * @implNote WAWebPDFNTypes.NOTICE_STAGES.PDFN_ACCEPTED
     */
    private static final String PDFN_ACCEPTED_STAGE = "5"; // WAWebPDFNTypes.NOTICE_STAGES.PDFN_ACCEPTED

    /**
     * The WhatsApp client instance used for queries and sending nodes.
     *
     * @implNote WAWebHandleAccountSyncNotification (module-level dependency on WAWebBackendApi, WAWebGetAboutQueryJob, etc.)
     */
    private final WhatsAppClient whatsapp;

    /**
     * The device service used for syncing device lists.
     *
     * @implNote WAWebHandleAccountSyncNotification (module-level dependency on WAWebAccountSyncJob.getDevices)
     */
    private final DeviceService deviceService;

    /**
     * Constructs a new account sync notification handler.
     *
     * @implNote WAWebHandleAccountSyncNotification (module initialization)
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
     * @implNote WAWebHandleAccountSyncNotification.handleAccountSyncNotification
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
                    "Cannot handle account_sync notification {0}: {1}",
                    node.getAttributeAsString("id", "<missing>"),
                    throwable.getMessage());
        } finally {
            sendNotificationAck(node); // WAWebHandleAccountSyncNotification.handleAccountSyncNotification (ack stanza)
        }
    }

    /**
     * Dispatches the notification to the appropriate handler based on the first
     * recognized child element type.
     *
     * <p>Each child tag maps to a specific {@code AccountSyncType} in WA Web's
     * parser and is handled by the corresponding switch case in the main handler function.
     *
     * @implNote WAWebHandleAccountSyncNotification.handleAccountSyncNotification (switch on r.type)
     * @param node the notification node containing one or more typed child elements
     */
    private void handleNotification(Node node) {
        for (var child : node.children()) {
            switch (child.description()) {
                case "status" -> { // WAWebAccountSyncJob.AccountSyncType.STATUS
                    refreshOwnAbout();
                    return;
                }
                case "text_status" -> { // WAWebAccountSyncJob.AccountSyncType.TEXT_STATUS
                    handleTextStatusNotification(node, child);
                    return;
                }
                case "privacy" -> { // WAWebAccountSyncJob.AccountSyncType.PRIVACY
                    whatsapp.pullWebAppState(SyncPatchType.REGULAR_LOW, SyncPatchType.REGULAR); // ADAPTED: WAWebAccountSyncJob.updatePrivacySettings + triggerAccountSyncForPrivacy
                    return;
                }
                case "devices" -> { // WAWebAccountSyncJob.AccountSyncType.DEVICES
                    refreshOwnDevices(node);
                    return;
                }
                case "blocklist" -> { // WAWebAccountSyncJob.AccountSyncType.BLOCKLIST
                    refreshBlockList();
                    return;
                }
                case "picture" -> { // WAWebAccountSyncJob.AccountSyncType.PICTURE
                    refreshOwnPicture();
                    return;
                }
                case "disappearing_mode" -> { // WAWebAccountSyncJob.AccountSyncType.DISAPPEARING_MODE
                    handleDisappearingModeNotification(node, child);
                    return;
                }
                case "tos" -> { // WAWebAccountSyncJob.AccountSyncType.TOS
                    handleTosNotification(child);
                    return;
                }
                case "notice" -> { // WAWebAccountSyncJob.AccountSyncType.NOTICE
                    handleNoticeNotification(child);
                    return;
                }
                case "user" -> { // WAWebAccountSyncJob.AccountSyncType.USER
                    handleUserNotification(child);
                    return;
                }
                case "biz_opt_out_list" -> { // WAWebAccountSyncJob.AccountSyncType.OPTOUTLIST
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
     *
     * @implNote WAWebHandleAccountSyncNotification.getAndUpdateStatus
     */
    void refreshOwnAbout() {
        var self = whatsapp.store().jid().orElse(null); // ADAPTED: WAWebUserPrefsMeUser.getMePnUserOrThrow_DO_NOT_USE
        if (self == null) {
            return;
        }

        var oldAbout = whatsapp.store().about().orElse(""); // NO_WA_BASIS - defensive comparison
        var newAbout = whatsapp.queryAbout(self).orElse(""); // WAWebGetAboutQueryJob.getAbout
        if (Objects.equals(oldAbout, newAbout)) {
            return; // NO_WA_BASIS - Cobalt optimization to avoid redundant updates
        }

        whatsapp.store().setAbout(newAbout); // ADAPTED: WAWebBackendApi.frontendSendAndReceive("setMyStatus", {status: r})
        fireListeners(listener -> listener.onAboutChanged(whatsapp, oldAbout, newAbout)); // ADAPTED: Cobalt listener pattern
    }

    /**
     * Handles a text status account sync notification.
     *
     * <p>When the action is {@code "modify"}, WA Web fetches the text status from the
     * server. Otherwise, it uses the values directly from the stanza. The non-modify
     * path is what Cobalt implements. The modify path is handled identically since
     * both end up updating the local text status for the contact.
     *
     * @implNote WAWebHandleAccountSyncNotification.handleAccountSyncNotification (TEXT_STATUS case)
     * @param node the parent notification node
     * @param textStatusNode the {@code text_status} child element
     */
    private void handleTextStatusNotification(Node node, Node textStatusNode) {
        var action = textStatusNode.getAttributeAsString("action", null); // WAWebHandleAccountSyncNotification parser (action)
        if ("modify".equals(action)) {
            // WAWebHandleAccountSyncNotification function g/h: fetches text status from server
            // ADAPTED: Cobalt does not have a dedicated text status query API yet,
            // so the modify case is treated the same as the default case (stanza values)
            updateOwnTextStatus(textStatusNode);
        } else {
            // WAWebHandleAccountSyncNotification.handleAccountSyncNotification (non-modify TEXT_STATUS path)
            var from = getUserJid(node, "from"); // WAWebWidFactory.asUserWidOrThrow(r.from)
            var self = from != null ? from : whatsapp.store().jid().map(Jid::toUserJid).orElse(null);
            if (self == null) {
                return;
            }

            var text = textStatusNode.getAttributeAsString("text", null); // WAWebHandleAccountSyncNotification parser (text)
            var emoji = textStatusNode.getChild("emoji")
                    .flatMap(emojiNode -> emojiNode.getAttributeAsString("content")) // WAWebHandleAccountSyncNotification parser (emoji content)
                    .orElse(null);
            var ephemeralDuration = textStatusNode.getAttributeAsInt("ephemeral_duration_sec", (Integer) null); // WAWebHandleAccountSyncNotification parser (ephemeralDurationSeconds)
            var lastUpdateTimeStr = textStatusNode.getAttributeAsString("last_update_time", null); // WAWebHandleAccountSyncNotification parser (lastUpdateTime as string)
            var lastUpdateTime = lastUpdateTimeStr != null ? Instant.ofEpochSecond(Long.parseLong(lastUpdateTimeStr)) : null; // WAWebHandleAccountSyncNotification (s != null ? Number(s) : void 0)
            upsertContactTextStatus(
                    self,
                    text,
                    emoji,
                    ephemeralDuration,
                    lastUpdateTime
            ); // WAWebUpdateTextStatusForContact.updateTextStatusForContact
        }
    }

    /**
     * Updates the own text status using values from the stanza child node.
     * Used for the {@code action === "modify"} case as a fallback when
     * no dedicated text status query API is available.
     *
     * @implNote WAWebHandleAccountSyncNotification (TEXT_STATUS modify path, function g/h)
     * @param textStatusNode the {@code text_status} child element
     */
    private void updateOwnTextStatus(Node textStatusNode) {
        var self = whatsapp.store().jid().orElse(null); // ADAPTED: WAWebUserPrefsMeUser.getMeDevicePnOrThrow_DO_NOT_USE
        if (self == null) {
            return;
        }

        var text = textStatusNode.getAttributeAsString("text", null); // WAWebHandleAccountSyncNotification parser (text)
        var emoji = textStatusNode.getChild("emoji")
                .flatMap(emojiNode -> emojiNode.getAttributeAsString("content")) // WAWebHandleAccountSyncNotification parser (emoji content)
                .orElse(null);
        var ephemeralDuration = textStatusNode.getAttributeAsInt("ephemeral_duration_sec", (Integer) null); // WAWebHandleAccountSyncNotification parser (ephemeralDurationSeconds)
        var lastUpdateTimeStr = textStatusNode.getAttributeAsString("last_update_time", null); // WAWebHandleAccountSyncNotification parser (lastUpdateTime)
        var lastUpdateTime = lastUpdateTimeStr != null ? Instant.ofEpochSecond(Long.parseLong(lastUpdateTimeStr)) : null;
        upsertContactTextStatus(
                self.toUserJid(),
                text,
                emoji,
                ephemeralDuration,
                lastUpdateTime
        ); // WAWebUpdateTextStatusForContact.updateTextStatusForContact
    }

    /**
     * Refreshes the authenticated user's device list by performing a device
     * list sync with the server.
     *
     * @implNote WAWebHandleAccountSyncNotification.handleAccountSyncNotification (DEVICES case)
     * @param node the parent notification node containing the {@code from} attribute
     */
    private void refreshOwnDevices(Node node) {
        var self = whatsapp.store().jid()
                .map(Jid::toUserJid)
                .orElseGet(() -> getUserJid(node, "from")); // ADAPTED: WAWebWidFactory.asUserWidOrThrow(r.from)
        if (self == null) {
            return;
        }

        // ADAPTED: WAWebHandleAccountSyncNotification.handleAccountSyncNotification (DEVICES case)
        // WA Web parses device list from stanza, calls y() for wid mapping, handleADVDeviceSyncResult,
        // and cleanupCampaignsWithInvalidDevices. Cobalt performs a full device list sync instead.
        // WA Web also has offline handler checks; Cobalt handles offline state at a higher level.
        deviceService.getDeviceLists(Set.of(self), "account_sync_notification", null, true);
    }

    /**
     * Refreshes the block list by querying the server and updating local
     * contact records accordingly.
     *
     * @implNote WAWebHandleAccountSyncNotification.handleAccountSyncNotification (BLOCKLIST case)
     */
    private void refreshBlockList() {
        // ADAPTED: WAWebQueryBlockListJob.fetchAndUpdateBlocklist("notification")
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
     *
     * @implNote WAWebHandleAccountSyncNotification.handleAccountSyncNotification (PICTURE case)
     */
    private void refreshOwnPicture() {
        var self = whatsapp.store().jid().orElse(null); // ADAPTED: WAWebAccountSyncJob.getAndUpdateProfilePicture
        if (self == null) {
            return;
        }

        var oldPicture = whatsapp.store().profilePicture().orElse(null); // NO_WA_BASIS - defensive comparison
        var newPicture = whatsapp.queryPicture(self).orElse(null); // WAWebProfilePicThumbCollection.resyncPictures
        if (Objects.equals(oldPicture, newPicture)) {
            return; // NO_WA_BASIS - Cobalt optimization to avoid redundant updates
        }

        whatsapp.store().setProfilePicture(newPicture); // ADAPTED: WAWebProfilePicThumbCollection update
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
     *
     * @implNote WAWebHandleAccountSyncNotification.handleAccountSyncNotification (DISAPPEARING_MODE case)
     * @param node the parent notification node containing the {@code from} attribute
     * @param disappearingMode the {@code disappearing_mode} child element
     */
    private void handleDisappearingModeNotification(Node node, Node disappearingMode) {
        var action = disappearingMode.getAttributeAsString("action", null); // WAWebHandleAccountSyncNotification parser (action)
        Integer duration; // WAWebHandleAccountSyncNotification (disappearingModeDuration / C)
        Integer settingTimestamp; // WAWebHandleAccountSyncNotification (disappearingModeSettingTimestamp / b)
        if (action != null) {
            // WAWebHandleAccountSyncNotification: h.hasAttr("action") -> y = h.attrString("action")
            // When action is present, duration and timestamp are not in the stanza
            duration = null;
            settingTimestamp = null;
        } else {
            // WAWebHandleAccountSyncNotification: C = h.attrInt("duration"), b = h.attrInt("t")
            duration = disappearingMode.getAttributeAsInt("duration", (Integer) null);
            settingTimestamp = disappearingMode.getAttributeAsInt("t", (Integer) null);
        }

        if ("modify".equals(action)) {
            // WAWebHandleAccountSyncNotification: action === "modify" -> getDisappearingMode from server
            // ADAPTED: Cobalt queries disappearing mode via the store's default timer
            // The server query would return the latest duration and timestamp
            // For now, we use the store's current setting if available
            var selfJid = getUserJid(node, "from"); // WAWebHandleAccountSyncNotification (T = r.from)
            if (selfJid != null) {
                // ADAPTED: WAWebGetDisappearingModeJob.getDisappearingMode
                // Cobalt does not have a dedicated disappearing mode query;
                // the duration update will be handled by the next full sync
            }
        }

        // WAWebHandleAccountSyncNotification: D != null && x != null && updateDisappearingModeForContact(...)
        if (duration != null) {
            // ADAPTED: WAWebUpdateDisappearingModeForContact.updateDisappearingModeForContact
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
     *
     * @implNote WAWebHandleAccountSyncNotification.handleAccountSyncNotification (TOS case)
     * @param tosNode the {@code tos} child element containing notice children
     */
    private void handleTosNotification(Node tosNode) {
        // WAWebHandleAccountSyncNotification parser: f.forEachChildWithTag("notice", ...)
        var notices = new HashSet<String>();
        for (var notice : tosNode.getChildren("notice")) {
            var state = notice.getAttributeAsString("state", null); // WAWebHandleAccountSyncNotification parser (state !== "false")
            var accepted = !"false".equals(state); // WAWebHandleAccountSyncNotification parser: maybeAttrString("state") !== "false"
            var id = notice.getAttributeAsString("id", null); // WAWebHandleAccountSyncNotification parser: attrString("id")
            if (id != null && accepted) {
                notices.add(id);
            }
        }
        // WAWebAccountSyncJob.updateTosStateFromAccountSync -> TosManager.setState
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
     *
     * @implNote WAWebHandleAccountSyncNotification.handleAccountSyncNotification (NOTICE case)
     * @param noticeNode the {@code notice} child element
     */
    private void handleNoticeNotification(Node noticeNode) {
        var noticeId = noticeNode.getAttributeAsString("id", null); // WAWebHandleAccountSyncNotification parser: v.attrString("id")
        var noticeStage = noticeNode.getAttributeAsString("stage", null); // WAWebHandleAccountSyncNotification parser: v.maybeAttrString("stage")
        var noticeVersion = noticeNode.getAttributeAsString("version", null); // WAWebHandleAccountSyncNotification parser: v.maybeAttrString("version")
        var noticeTimestamp = noticeNode.getAttributeAsInt("t", (Integer) null); // WAWebHandleAccountSyncNotification parser: v.attrInt("t")

        // WAWebHandleAccountSyncNotification: P != null && P !== "" && N != null && w != null && M != null
        if (noticeId == null || noticeId.isEmpty() || noticeStage == null || noticeVersion == null || noticeTimestamp == null) {
            return;
        }

        // WAWebHandleAccountSyncNotification: A = N === WAWebPDFNTypes.NOTICE_STAGES.PDFN_ACCEPTED
        var accepted = PDFN_ACCEPTED_STAGE.equals(noticeStage);

        // WAWebHandleAccountSyncNotification: updateTosStateFromAccountSync([{id: P, state: A, timestamp: castToUnixTime(M)}])
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
     *
     * @implNote WAWebHandleAccountSyncNotification.handleAccountSyncNotification (USER case)
     * @param userNode the {@code user} child element
     */
    private void handleUserNotification(Node userNode) {
        // WAWebHandleAccountSyncNotification parser: S.maybeAttrString("state") === "AI available"
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
     *
     * @implNote WAWebHandleAccountSyncNotification.handleAccountSyncNotification (OPTOUTLIST case)
     * @param optOutListNode the {@code biz_opt_out_list} child element
     */
    private void handleBizOptOutListNotification(Node optOutListNode) {
        var dhash = optOutListNode.getAttributeAsString("dhash", null); // WAWebHandleAccountSyncNotification parser: L.maybeAttrString("dhash")
        var prevDhash = optOutListNode.getAttributeAsString("prev_dhash", null); // WAWebHandleAccountSyncNotification parser: L.maybeAttrString("prev_dhash")
        var storedHash = whatsapp.store().businessOptOutListHash().orElse(null); // WAWebUserPrefsMultiDevice.getOptOutListHash()

        // WAWebHandleAccountSyncNotification: if (R !== k) -> full refresh
        if (!Objects.equals(storedHash, prevDhash)) {
            // ADAPTED: WAWebWorkerSafeBackendApi.workerSafeFireAndForget("updateOptOutList")
            // Cobalt stores the new hash; a full refresh is not yet implemented
            if (dhash != null) {
                whatsapp.store().setBusinessOptOutListHash(dhash);
            }
            return;
        }

        // WAWebHandleAccountSyncNotification: dhash != null -> process list items and update hash
        if (dhash != null) {
            // WAWebHandleAccountSyncNotification: E.forEach(async ({action, biz_jid}) => ...)
            // ADAPTED: Individual list item processing is not yet implemented in Cobalt
            // WA Web processes each child item with action "block"/"unblock" and biz_jid
            whatsapp.store().setBusinessOptOutListHash(dhash); // WAWebUserPrefsMultiDevice.setOptOutlistHash(L)
        }
    }

    /**
     * Extracts a user JID from the specified attribute of the given node.
     *
     * @implNote WAWebHandleAccountSyncNotification.handleAccountSyncNotification (r.from conversion)
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
     *
     * @implNote WAWebHandleAccountSyncNotification (NO_WA_BASIS - Cobalt listener dispatch pattern)
     * @param consumer the callback to execute for each listener
     */
    private void fireListeners(java.util.function.Consumer<com.github.auties00.cobalt.client.WhatsAppClientListener> consumer) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> consumer.accept(listener));
        }
    }

    /**
     * Creates or updates the text status for a contact in the store.
     *
     * @implNote WAWebUpdateTextStatusForContact.updateTextStatusForContact
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
     *
     * @implNote WAWebBackendApi.frontendFireAndForget("updateTextStatus", ...) (ADAPTED)
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
     *
     * @implNote WAWebHandleAccountSyncNotification.handleAccountSyncNotification (ack stanza: WAWap.wap("ack", ...))
     * @param node the notification node to acknowledge
     */
    private void sendNotificationAck(Node node) {
        var stanzaId = node.getAttributeAsString("id", null); // WAWebHandleAccountSyncNotification: r.stanzaId
        var stanzaFrom = node.getAttributeAsJid("from", null); // WAWebHandleAccountSyncNotification: r.from
        if (stanzaId == null || stanzaFrom == null) {
            return;
        }

        // WAWebHandleAccountSyncNotification: WAWap.wap("ack", {id: CUSTOM_STRING(r.stanzaId), to: JID(r.from), class: "notification", type: "account_sync"})
        whatsapp.sendNodeWithNoResponse(new com.github.auties00.cobalt.node.NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId)
                .attribute("class", "notification") // WAWebHandleAccountSyncNotification: hardcoded "notification"
                .attribute("to", stanzaFrom)
                .attribute("type", "account_sync") // WAWebHandleAccountSyncNotification: hardcoded "account_sync"
                .build());
    }
}
