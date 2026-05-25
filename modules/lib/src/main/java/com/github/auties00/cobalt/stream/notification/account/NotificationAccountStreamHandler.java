package com.github.auties00.cobalt.stream.notification.account;

import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
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
 * Handles {@code type="account_sync"} notifications carrying server-side mutations to the authenticated
 * account's own settings.
 *
 * <p>Each notification carries exactly one typed child element that selects the side-effect:
 * {@code status} (about refresh), {@code text_status} (text-status upsert), {@code privacy} (app-state
 * pull), {@code devices} (inline device-list update), {@code blocklist} (blocklist reconciliation),
 * {@code picture} (profile-picture refresh), {@code disappearing_mode} (global ephemeral-timer change),
 * {@code tos} (TOS notice acknowledgement), {@code notice} (single accepted-notice record), {@code user}
 * (AI-availability flag), and {@code biz_opt_out_list} (business opt-out reconciliation). The protocol
 * ACK is always sent in the {@code finally} block of {@link #handle(Node)} regardless of mutation
 * success.</p>
 *
 * @implNote This implementation surfaces every mutation through the typed Cobalt store API and fires
 * listener callbacks, whereas WA Web fans the same parsed data out to its frontend pipeline. Cobalt ACKs
 * unconditionally, matching the WA Web ack-promise return path which constructs the ack node before
 * dispatching to the per-type branch.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleAccountSyncNotification")
final class NotificationAccountStreamHandler implements SocketStream.Handler {

    /**
     * Logs warnings about unhandled notifications and debug messages about ignored child elements.
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationAccountStreamHandler.class.getName());

    /**
     * Holds the {@code PDFN_ACCEPTED} notice-stage value used by the {@code notice} child path to
     * distinguish accepted policy disclosures from pending ones.
     *
     * <p>A {@code <notice>} stanza whose {@code stage} attribute equals this value is recorded as an
     * accepted notice id; any other stage is ignored.</p>
     *
     * @implNote This implementation hard-codes the literal {@code "5"} taken from WA Web's
     * {@code WAWebPDFNTypes.NOTICE_STAGES.PDFN_ACCEPTED}.
     */
    private static final String PDFN_ACCEPTED_STAGE = "5";

    /**
     * Holds the client used for store reads, queries (about, picture, blocklist), node sends, and
     * listener notifications.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Holds the device service used by the {@code devices} child path to apply the inline device list
     * against the authenticated user's own device cache.
     */
    private final DeviceService deviceService;

    /**
     * Holds the ack sender used to ship the post-processing
     * {@code <ack class="notification" type="account_sync"/>} stanza.
     */
    private final AckSender ackSender;

    /**
     * Constructs the handler with the shared client, device service, and ack sender.
     *
     * @param whatsapp      the non-{@code null} client used for store and network access
     * @param deviceService the non-{@code null} device service used for device-list mutations
     * @param ackSender     the non-{@code null} ack sender used for the protocol-level {@code <ack>} response
     */
    NotificationAccountStreamHandler(WhatsAppClient whatsapp, DeviceService deviceService, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService cannot be null");
        this.ackSender = Objects.requireNonNull(ackSender, "ackSender cannot be null");
    }

    /**
     * Validates the stanza shape, dispatches to {@link #handleNotification(Node)}, logs any thrown
     * exception, and always sends the protocol-level ACK.
     *
     * <p>Stanzas whose description is not {@code notification} or whose {@code type} is not
     * {@code account_sync} are dropped without ACK; valid stanzas are always ACKed even when handling
     * throws.</p>
     *
     * @implNote This implementation swallows {@link Throwable} from the mutation branch and logs it,
     * whereas WA Web rejects the underlying job promise so the orchestrator can NACK the stanza. Cobalt
     * ACKs unconditionally because the WA Web ack node is constructed at the top of the wrapper before
     * dispatching to the per-type branch.
     *
     * @param node the incoming {@code <notification>} stanza
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
     * Selects the per-type branch by iterating the notification children until a recognised tag is
     * found, then delegates to the matching helper.
     *
     * <p>Iterates the children in order and dispatches on the first recognised description, so a stanza
     * never triggers more than one branch. When no child matches any known tag, the stanza is logged at
     * {@code DEBUG} and otherwise ignored.</p>
     *
     * @param node the {@code <notification>} stanza
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
                    whatsapp.refreshBlockList();
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
                }
            }
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "Ignoring unrecognized account_sync notification {0}",
                node.getAttributeAsString("id", "<missing>"));
    }

    /**
     * Refreshes the authenticated user's about text by querying the server and notifying listeners when
     * the value changed.
     *
     * <p>Triggered by a {@code <status/>} child, meaning the user changed their about on another device.
     * Queries the current about for the local account, compares it against the stored value, and on a
     * change writes the new value to the store and fires {@link WhatsAppClientListener#onAboutChanged}.</p>
     *
     * @implNote This implementation compares the stored about against the queried value before writing,
     * avoiding a redundant listener fire when the server pushes a value Cobalt already has; WA Web fires
     * the frontend event unconditionally when the queried status is non-empty.
     */
    void refreshOwnAbout() {
        var self = whatsapp.store().jid().orElse(null);
        if (self == null) {
            return;
        }

        var oldAbout = whatsapp.store().selfTextStatus().flatMap(ContactTextStatus::text).orElse("");
        var newAbout = whatsapp.queryAbout(self).orElse("");
        if (Objects.equals(oldAbout, newAbout)) {
            return;
        }

        var newStatus = new ContactTextStatusBuilder()
                .text(newAbout)
                .build();
        whatsapp.store().setSelfTextStatus(newStatus);
        fireListeners(listener -> listener.onAboutChanged(whatsapp, oldAbout, newAbout));
    }

    /**
     * Applies a text-status change carried inside the {@code <text_status>} child to the local store for
     * the originating user.
     *
     * <p>When the stanza's {@code action} is {@code "modify"} the change applies to the authenticated
     * user via {@link #updateOwnTextStatus(Node)}; otherwise it applies to the user named in the
     * stanza's {@code from} attribute (falling back to the local account), reading the inline
     * {@code text}, {@code emoji}, {@code ephemeral_duration_sec}, and {@code last_update_time} fields.
     * Either path drives {@link WhatsAppClientListener#onContactTextStatus} for the changed contact.</p>
     *
     * @implNote This implementation collapses the WA Web {@code action === "modify"} path (which
     * re-queries the text status from the server) into the same stanza-driven update used for the
     * non-modify case, because Cobalt does not maintain a dedicated text-status server query.
     *
     * @param node           the {@code <notification>} stanza
     * @param textStatusNode the {@code <text_status>} child carrying the new values
     */
    private void handleTextStatusNotification(Node node, Node textStatusNode) {
        var action = textStatusNode.getAttributeAsString("action", null);
        if ("modify".equals(action)) {
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
     * Applies the {@code action="modify"} text-status path by reading the stanza's inline fields and
     * writing them to the authenticated user's text-status record.
     *
     * <p>Reads the same {@code text}, {@code emoji}, {@code ephemeral_duration_sec}, and
     * {@code last_update_time} attributes the non-modify path reads, but targets the local account only.</p>
     *
     * @implNote This implementation duplicates the stanza-attribute parsing rather than delegating to
     * {@link #handleTextStatusNotification(Node, Node)} because the {@code from} resolution differs (self
     * only) and the parent path also fans out to the from-derived JID.
     *
     * @param textStatusNode the {@code <text_status>} child node
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
     * Replaces the authenticated user's cached device list with the inline list carried by the
     * notification's {@code <devices>} child.
     *
     * <p>Reads each {@code <device jid=... key-index=.../>} child into a {@link DeviceInfo}, captures the
     * server-computed {@code dhash}, and writes the resulting {@code DeviceList} to the store so future
     * device-list IQs can short-circuit when the hash matches. This drives Signal-session establishment
     * for new companion devices and cleanup for removed ones.</p>
     *
     * @implNote This implementation reads the inline device children rather than firing a fresh USync
     * device query, because the server does not respond to a USync issued immediately after an inline
     * {@code devices} notification (it treats the inline payload as authoritative). An earlier Cobalt
     * version called {@link DeviceService#getDeviceLists} here and blocked for 60 seconds on the silent
     * response before timing out.
     *
     * @param node the {@code <notification>} stanza carrying the {@code devices} child and {@code from} attribute
     */
    private void refreshOwnDevices(Node node) {
        var self = whatsapp.store().jid()
                .map(Jid::toUserJid)
                .orElseGet(() -> getUserJid(node, "from"));
        if (self == null) {
            return;
        }

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
     * Writes the {@code username} field of every {@code <item>} child of a {@code <blocklist>}
     * notification to the matching contact record.
     *
     * <p>Iterates each {@code <item jid=... username=.../>} entry, resolves the contact (creating a new
     * record when none exists for the JID), and stores the username so it can display next to the
     * blocked contact's row.</p>
     *
     * @implNote This implementation applies the usernames unconditionally, whereas WA Web reaches this
     * path only when its username-display gating prop is enabled; the Cobalt store has no equivalent
     * gating prop wired here.
     *
     * @param blocklistNode the {@code <blocklist>} child carrying {@code <item jid=... username=.../>} entries
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
            var contact = whatsapp.store()
                    .findContactByJid(userJid)
                    .orElseGet(() -> whatsapp.store().addNewContact(userJid));
            contact.setUsername(username);
            whatsapp.store().addContact(contact);
        }
    }

    /**
     * Refreshes the authenticated user's profile-picture URI by querying the server and firing
     * {@link WhatsAppClientListener#onProfilePictureChanged} when the URI changed.
     *
     * <p>Triggered when the user updates their profile picture on another paired device. Compares the
     * queried URI against the stored URI and writes plus fires the listener only on a change.</p>
     *
     * @implNote This implementation short-circuits when the queried URI equals the stored URI, whereas WA
     * Web always dispatches the frontend event.
     */
    private void refreshOwnPicture() {
        var self = whatsapp.store().jid().orElse(null);
        if (self == null) {
            return;
        }

        var oldPicture = whatsapp.store().profilePicture().orElse(null);
        var newPicture = whatsapp.queryPicture(self).orElse(null);
        if (Objects.equals(oldPicture, newPicture)) {
            return;
        }

        whatsapp.store().setProfilePicture(newPicture);
        fireListeners(listener -> listener.onProfilePictureChanged(whatsapp, self));
    }

    /**
     * Applies the global "new chats" ephemeral timer encoded inside the {@code <disappearing_mode>}
     * child of an {@code account_sync} notification.
     *
     * <p>Sets the default ephemeral duration applied to chats created after this point; existing chats
     * retain their per-chat setting. The inline {@code duration} and {@code t} attributes are read only
     * when the child carries no {@code action} attribute.</p>
     *
     * @implNote This implementation consumes only the inline {@code duration} and {@code t} attributes;
     * WA Web's {@code modify} action would re-query the server for the disappearing mode. Cobalt has no
     * equivalent query, so the {@code modify} branch falls through and waits for the next full app-state
     * sync to converge.
     *
     * @param node             the {@code <notification>} stanza
     * @param disappearingMode the {@code <disappearing_mode>} child
     */
    private void handleDisappearingModeNotification(Node node, Node disappearingMode) {
        var action = disappearingMode.getAttributeAsString("action", null);
        Integer duration;
        Integer settingTimestamp;
        if (action != null) {
            duration = null;
            settingTimestamp = null;
        } else {
            duration = disappearingMode.getAttributeAsInt("duration", (Integer) null);
            settingTimestamp = disappearingMode.getAttributeAsInt("t", (Integer) null);
        }

        if ("modify".equals(action)) {
            // TODO: implement a disappearing-mode server query; today the modify branch waits for the next full app-state sync to converge.
            var selfJid = getUserJid(node, "from");
            if (selfJid != null) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Deferring disappearing_mode modify for {0} to next app-state sync",
                        selfJid);
            }
        }

        if (duration != null) {
            whatsapp.store().setNewChatsEphemeralTimer(ChatEphemeralTimer.of(duration));
        }
    }

    /**
     * Records every {@code <notice id=... state=.../>} entry under a {@code <tos>} child whose state is
     * anything other than {@code "false"} as an accepted notice id.
     *
     * <p>Collects the accepted ids, merges them into the stored notice-id set, and writes the union back.
     * The accepted ids gate UI elements such as banner dismissal and settings copy.</p>
     *
     * @param tosNode the {@code <tos>} child containing one or more {@code <notice/>} entries
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
        if (!notices.isEmpty()) {
            var currentNotices = new HashSet<>(whatsapp.store().tosNoticeIds());
            currentNotices.addAll(notices);
            whatsapp.store().setTosNoticeIds(currentNotices);
        }
    }

    /**
     * Records a single {@code <notice/>} child whose stage equals {@link #PDFN_ACCEPTED_STAGE} as an
     * accepted notice id.
     *
     * <p>The standalone {@code <notice/>} stanza arrives independently of the {@code <tos/>} batch path
     * and carries the additional {@code stage}, {@code version}, and {@code t} fields; all four of
     * {@code id}, {@code stage}, {@code version}, and {@code t} must be present or the stanza is ignored.</p>
     *
     * @implNote This implementation persists only the notice id when the stage equals
     * {@link #PDFN_ACCEPTED_STAGE}; WA Web also stores the policy version, but Cobalt's store keeps only
     * the accepted-id set.
     *
     * @param noticeNode the {@code <notice/>} child node
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

        if (accepted) {
            var currentNotices = new HashSet<>(whatsapp.store().tosNoticeIds());
            currentNotices.add(noticeId);
            whatsapp.store().setTosNoticeIds(currentNotices);
        }
    }

    /**
     * Writes the {@code isAiAvailable} boolean derived from the {@code <user state="AI available"/>}
     * child to the store.
     *
     * <p>Gates the in-chat Meta AI affordances when the server-side eligibility flips on.</p>
     *
     * @implNote This implementation persists the flag, whereas WA Web only logs the receipt and relies on
     * the next AB-prop sync to surface the value to the UI.
     *
     * @param userNode the {@code <user/>} child node
     */
    private void handleUserNotification(Node userNode) {
        var isAiAvailable = "AI available".equals(userNode.getAttributeAsString("state", null));
        whatsapp.store().setAiAvailable(isAiAvailable);
    }

    /**
     * Reconciles the local business-opt-out blocklist against the {@code <biz_opt_out_list>} child.
     *
     * <p>Applies each {@code <item action=... biz_jid=.../>} entry to the matching contact's blocked
     * flag and updates the stored hash on success, firing {@link WhatsAppClientListener#onContactBlocked}
     * for any contact whose flag changed. The reconciliation is skipped entirely when {@code prev_dhash}
     * does not match the stored hash.</p>
     *
     * @implNote This implementation skips the full opt-out list refresh on a {@code prev_dhash} mismatch
     * and leaves the stored hash untouched (mismatch implies do-not-persist). WA Web fires a full
     * server-side refresh in that case; Cobalt has no equivalent batch endpoint and relies on the next
     * app-state sync to converge.
     *
     * @param optOutListNode the {@code <biz_opt_out_list>} child carrying {@code dhash}, {@code prev_dhash}, and item children
     */
    private void handleBizOptOutListNotification(Node optOutListNode) {
        var dhash = optOutListNode.getAttributeAsString("dhash", null);
        var prevDhash = optOutListNode.getAttributeAsString("prev_dhash", null);
        var storedHash = whatsapp.store().businessOptOutListHash().orElse(null);

        if (!Objects.equals(storedHash, prevDhash)) {
            // TODO: trigger a full opt-out list refresh when prev_dhash mismatches; today Cobalt waits for the next app-state sync to converge.
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
     * Reads a JID-valued attribute and reduces it to its user form (server-only, no device, no agent).
     *
     * <p>Used by the per-child branches that need to address a user record rather than a device record.</p>
     *
     * @param node the node to read from
     * @param key  the attribute name
     * @return the parsed user JID, or {@code null} if the attribute is absent or unparsable
     */
    private Jid getUserJid(Node node, String key) {
        return node.getAttributeAsJid(key)
                .map(Jid::toUserJid)
                .orElse(null);
    }

    /**
     * Fans the given callback out to every registered listener on its own virtual thread.
     *
     * <p>Each listener runs on a fresh virtual thread so a slow listener cannot block the notification
     * stream.</p>
     *
     * @param consumer the callback to invoke against each listener
     */
    private void fireListeners(Consumer<WhatsAppClientListener> consumer) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> consumer.accept(listener));
        }
    }

    /**
     * Creates or merges a {@link ContactTextStatus} record for the given contact and fires the change to
     * listeners.
     *
     * <p>Loads the existing record for the canonical user JID (or a fresh one when none exists), overwrites
     * its text, emoji, ephemeral duration, and last-update time, stores the result, and notifies listeners
     * via {@link #notifyContactTextStatusChanged(Jid, ContactTextStatus)}.</p>
     *
     * @param contactJid               the JID of the contact whose record is being updated
     * @param text                     the new about text, or {@code null} to clear
     * @param emoji                    the new emoji, or {@code null} to clear
     * @param ephemeralDurationSeconds the per-text ephemeral duration in seconds, or {@code null}
     * @param lastUpdateTime           the server-reported last update time, or {@code null}
     * @return the merged {@link ContactTextStatus} now stored in the local store
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
     * Fires {@link WhatsAppClientListener#onContactTextStatus} on every registered listener, each on its
     * own virtual thread.
     *
     * @param contactJid the JID whose text status changed
     * @param status     the updated text-status record
     */
    private void notifyContactTextStatusChanged(Jid contactJid, ContactTextStatus status) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onContactTextStatus(whatsapp, contactJid, status));
        }
    }

    /**
     * Sends the {@code <ack class="notification" type="account_sync"/>} stanza required by the server to
     * mark this notification as delivered.
     *
     * <p>The ack is fire-and-forget; a closed socket surfaces as a
     * {@link com.github.auties00.cobalt.exception.WhatsAppSessionException.Closed} which the surrounding
     * {@link #handle(Node)} {@code finally} block already isolates from the mutation path.</p>
     *
     * @param node the original {@code <notification>} stanza
     */
    private void sendNotificationAck(Node node) {
        ackSender.ack(AckClass.NOTIFICATION, node).type("account_sync").send();
    }
}
