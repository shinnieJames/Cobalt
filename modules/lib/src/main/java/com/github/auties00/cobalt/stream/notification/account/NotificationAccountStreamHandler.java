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
 * Handles {@code type="account_sync"} notifications carrying server-side
 * mutations to the authenticated account's own settings.
 *
 * @apiNote
 * Dispatched to by {@link NotificationAccountDispatcher}. Each notification
 * carries exactly one typed child element ({@code status}, {@code text_status},
 * {@code privacy}, {@code devices}, {@code blocklist}, {@code picture},
 * {@code disappearing_mode}, {@code tos}, {@code notice}, {@code user},
 * {@code biz_opt_out_list}) that selects the side-effect: an about refresh,
 * a text-status upsert, an app-state pull, an inline device-list update,
 * a blocklist reconciliation, a profile-picture refresh, a global ephemeral
 * timer change, a TOS notice acknowledgement, an AI-availability flag set,
 * or a business opt-out list reconciliation.
 *
 * @implNote
 * This implementation surfaces every mutation through the typed Cobalt
 * store API and fires listener callbacks; WA Web fans the same parsed
 * data out to its frontend pipeline through {@code frontendSendAndReceive}
 * and {@code BackendEventBus} calls. The protocol ACK is always sent in
 * the {@code finally} block of {@link #handle(Node)} regardless of
 * mutation success.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleAccountSyncNotification")
final class NotificationAccountStreamHandler implements SocketStream.Handler {

    /**
     * Logger used for warnings about unhandled notifications and debug
     * messages about ignored child elements.
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationAccountStreamHandler.class.getName());

    /**
     * The {@code PDFN_ACCEPTED} notice-stage value used by the {@code notice}
     * child path to distinguish accepted policy disclosures from pending ones.
     *
     * @apiNote
     * Mirrors WA Web's {@code WAWebPDFNTypes.NOTICE_STAGES.PDFN_ACCEPTED}
     * literal {@code "5"}. A {@code notice} stanza whose {@code stage}
     * attribute equals this value is recorded as an accepted notice id;
     * any other stage is ignored.
     */
    private static final String PDFN_ACCEPTED_STAGE = "5";

    /**
     * The {@link WhatsAppClient} used for store reads, queries (about,
     * picture, blocklist), node sends, and listener notifications.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The {@link DeviceService} used by the {@code devices} child path to
     * apply the inline device list against the authenticated user's own
     * device cache.
     */
    private final DeviceService deviceService;

    /**
     * The {@link AckSender} used to ship the post-processing
     * {@code <ack class="notification" type="account_sync"/>} stanza.
     */
    private final AckSender ackSender;

    /**
     * Constructs the handler with shared dependencies.
     *
     * @apiNote
     * Called once by {@link NotificationAccountDispatcher} during
     * construction; embedders do not instantiate this handler directly.
     *
     * @param whatsapp      the non-{@code null} client used for store and network access
     * @param deviceService the non-{@code null} device service used for device list mutations
     * @param ackSender     the non-{@code null} ack sender used for the
     *                      protocol-level {@code <ack>} response
     */
    NotificationAccountStreamHandler(WhatsAppClient whatsapp, DeviceService deviceService, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService cannot be null");
        this.ackSender = Objects.requireNonNull(ackSender, "ackSender cannot be null");
    }

    /**
     * Validates the stanza shape, dispatches to {@link #handleNotification(Node)},
     * logs any thrown exception, and always sends the protocol-level ACK.
     *
     * @apiNote
     * Invoked by {@link NotificationAccountDispatcher}. Stanzas whose
     * description is not {@code notification} or whose {@code type} is
     * not {@code account_sync} are dropped without ACK; valid stanzas
     * are always ACKed even when handling throws.
     *
     * @implNote
     * This implementation swallows {@link Throwable} from the mutation
     * branch and logs it; WA Web rejects the underlying job promise so
     * that the orchestrator can NACK the stanza. Cobalt ACKs
     * unconditionally to match the WA Web ack-promise return path of
     * {@code WAWebHandleAccountSyncNotification.handleAccountSyncNotification}
     * which constructs the ack node at the top of the wrapper before
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
     * Selects the per-type branch by iterating the notification children
     * until a recognised tag is found, then delegates to the matching
     * helper.
     *
     * @apiNote
     * Mirrors WA Web's {@code incomingAccountSyncNotification} parser
     * which checks each known child tag in order and produces a typed
     * record with an {@code AccountSyncType} discriminator.
     *
     * @implNote
     * This implementation returns after the first recognised child so a
     * stanza never triggers more than one branch, matching WA Web's
     * {@code if-else} chain. Unrecognised children are logged at
     * {@code DEBUG} when no branch matched at all.
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
     * Refreshes the authenticated user's about text by querying the server
     * and notifying listeners when the value changed.
     *
     * @apiNote
     * Triggered by a {@code <status/>} child on an {@code account_sync}
     * notification, meaning the user changed their about on another
     * device. Drives the {@link WhatsAppClientListener#onAboutChanged}
     * callback.
     *
     * @implNote
     * This implementation compares the stored about against the queried
     * value before writing, avoiding a redundant listener fire when the
     * server pushes the same value Cobalt already has. WA Web does not
     * compare; it fires {@code frontendSendAndReceive("setMyStatus", ...)}
     * unconditionally when the queried status is non-empty.
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
     * Applies a text-status change carried inside the {@code <text_status>}
     * child to the local store for the originating user.
     *
     * @apiNote
     * Drives the {@link WhatsAppClientListener#onContactTextStatus}
     * callback for the changed contact (which may be self or a paired
     * user). When the stanza's {@code action} is {@code "modify"} the
     * change applies to the authenticated user; otherwise it applies to
     * the user named in the stanza's {@code from} attribute.
     *
     * @implNote
     * This implementation collapses the WA Web {@code action === "modify"}
     * path (which re-queries the text status via
     * {@code WAWebContactTextStatusBridge.getTextStatus}) into the same
     * stanza-driven update used for the non-modify case, because Cobalt
     * does not maintain a dedicated text-status server query.
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
     * Applies the {@code action="modify"} text-status path by reading the
     * stanza's inline fields and writing them to the authenticated user's
     * text-status record.
     *
     * @apiNote
     * Used only on the {@code modify} action branch of
     * {@link #handleTextStatusNotification}. Reads the same attributes
     * the non-modify path reads.
     *
     * @implNote
     * This implementation duplicates the stanza-attribute parsing rather
     * than calling {@link #handleTextStatusNotification} reflexively
     * because the {@code from} resolution differs (self only) and the
     * parent path also fans out to the from-derived JID.
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
     * Replaces the authenticated user's cached device list with the inline
     * list carried by the notification's {@code <devices>} child.
     *
     * @apiNote
     * Drives Signal-session establishment for new companion devices and
     * Signal-session cleanup for removed companion devices. The inline
     * list also carries the server-computed {@code dhash} which Cobalt
     * stores so future device-list IQs can short-circuit when the hash
     * matches.
     *
     * @implNote
     * This implementation reads the inline {@code <device jid=...
     * key-index=.../>} children rather than firing a fresh USync device
     * query, because the server does not respond to a USync issued
     * immediately after an inline {@code devices} notification (the
     * server treats the inline payload as authoritative). An earlier
     * Cobalt version called {@link DeviceService#getDeviceLists} here
     * and blocked for 60 seconds on the silent response before timing
     * out.
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
     * Writes the {@code username} field of every {@code <item>} child of
     * a {@code <blocklist>} notification to the matching contact record.
     *
     * @apiNote
     * Drives username display next to a blocked contact's row in the
     * blocklist UI. WA Web reaches this path only when the AB prop
     * {@code WAWebUsernameGatingUtils.usernameDisplayedEnabled()} is
     * enabled; Cobalt applies the usernames unconditionally because the
     * Cobalt store has no equivalent gating prop wired here.
     *
     * @implNote
     * This implementation creates a new {@code Contact} when no record
     * exists for the JID, matching WA Web's
     * {@code WAWebSetUsernameJob.setUsernamesJob} which calls
     * {@code WAWebContactCollection.set} unconditionally.
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
     * Refreshes the authenticated user's profile-picture URI by querying
     * the server and firing
     * {@link WhatsAppClientListener#onProfilePictureChanged} when the URI
     * changed.
     *
     * @apiNote
     * Triggered when the user updates their profile picture on another
     * paired device.
     *
     * @implNote
     * This implementation short-circuits when the queried URI equals the
     * stored URI; WA Web's
     * {@code WAWebAccountSyncJob.getAndUpdateProfilePicture} always
     * dispatches the frontend event.
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
     * Applies the global "new chats" ephemeral timer encoded inside the
     * {@code <disappearing_mode>} child of an {@code account_sync}
     * notification.
     *
     * @apiNote
     * Sets the default ephemeral duration applied to new chats created
     * after this point; existing chats retain their per-chat setting.
     *
     * @implNote
     * This implementation only consumes the inline {@code duration} and
     * {@code t} attributes; WA Web's {@code modify} action would also
     * fire {@code WAWebGetDisappearingModeJob.getDisappearingMode} to
     * re-query the server. Cobalt has no equivalent
     * disappearing-mode query, so the {@code modify} branch falls
     * through and waits for the next full app-state sync to converge.
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
     * Records every {@code <notice id=... state=.../>} entry under a
     * {@code <tos>} child whose state is anything other than
     * {@code "false"} as an accepted notice id.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebAccountSyncJob.updateTosStateFromAccountSync} which
     * feeds the same {@code (id, state)} pairs to
     * {@code TosManager.setState}. The accepted ids gate UI elements
     * (banner dismissal, settings copy).
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
     * Records a single {@code <notice/>} child whose stage equals
     * {@link #PDFN_ACCEPTED_STAGE} as an accepted notice id.
     *
     * @apiNote
     * Mirrors WA Web's path through
     * {@code WAWebUserDisclosureCollection.updateNoticeStage} followed
     * by {@code updateTosStateFromAccountSync}. The {@code <notice/>}
     * stanza arrives independently of the {@code <tos/>} batch path
     * and carries the additional {@code stage}, {@code version}, and
     * {@code t} fields.
     *
     * @implNote
     * This implementation only persists the notice id when the stage
     * equals {@link #PDFN_ACCEPTED_STAGE}; WA Web also stores the
     * policy version on
     * {@code UserDisclosureCollection.updateNoticeStage}. Cobalt's
     * store keeps only the accepted-id set.
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
     * Writes the {@code isAiAvailable} boolean derived from the
     * {@code <user state="AI available"/>} child to the store.
     *
     * @apiNote
     * Gates the in-chat Meta AI affordances when WA Web's server-side
     * eligibility flips on.
     *
     * @implNote
     * This implementation persists the flag; WA Web only logs
     * {@code "Receieved account sync notification for Ai Available"}
     * via {@code WALogger.LOG} and relies on the next AB-prop sync to
     * surface the value to the UI.
     *
     * @param userNode the {@code <user/>} child node
     */
    private void handleUserNotification(Node userNode) {
        var isAiAvailable = "AI available".equals(userNode.getAttributeAsString("state", null));
        whatsapp.store().setAiAvailable(isAiAvailable);
    }

    /**
     * Reconciles the local business-opt-out blocklist against the
     * {@code <biz_opt_out_list>} child, applying each {@code <item
     * action=... biz_jid=.../>} entry and updating the stored hash on
     * success.
     *
     * @apiNote
     * Drives the per-contact blocked flag for business JIDs the user
     * has opted out of receiving messages from. Fires
     * {@link WhatsAppClientListener#onContactBlocked} for any change.
     *
     * @implNote
     * This implementation skips the full opt-out list refresh when
     * {@code prev_dhash} does not match the stored hash. WA Web fires
     * {@code workerSafeFireAndForget("updateOptOutList")} in that case
     * to trigger a full server-side refresh; Cobalt has no equivalent
     * batch endpoint and relies on the next app-state sync to converge.
     * The stored hash is intentionally left untouched on mismatch to
     * match WA Web semantics (mismatch implies do-not-persist).
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
     * Reads a JID-valued attribute and reduces it to its user-form
     * (server-only, no device, no agent).
     *
     * @apiNote
     * Internal helper for the per-child branches that need to address a
     * user record rather than a device record.
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
     * Fans the given callback out to every registered listener on its own
     * virtual thread so a slow listener cannot block the notification
     * stream.
     *
     * @apiNote
     * Internal helper used by every branch that surfaces a change to
     * embedders.
     *
     * @param consumer the callback to invoke against each listener
     */
    private void fireListeners(Consumer<WhatsAppClientListener> consumer) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> consumer.accept(listener));
        }
    }

    /**
     * Creates or merges a {@link ContactTextStatus} record for the given
     * contact and fires the change to listeners.
     *
     * @apiNote
     * Shared between the {@code text_status} child path (both
     * {@code modify} and inline) and the MEX text-status update path.
     *
     * @param contactJid              the JID of the contact whose record is being updated
     * @param text                    the new about text, or {@code null} to clear
     * @param emoji                   the new emoji, or {@code null} to clear
     * @param ephemeralDurationSeconds the per-text ephemeral duration in seconds, or {@code null}
     * @param lastUpdateTime          the server-reported last update time, or {@code null}
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
     * Fires {@link WhatsAppClientListener#onContactTextStatus} on every
     * registered listener.
     *
     * @apiNote
     * Internal helper used by {@link #upsertContactTextStatus} only.
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
     * Sends the {@code <ack class="notification" type="account_sync"/>}
     * stanza required by the server to mark this notification as
     * delivered.
     *
     * @apiNote
     * The ack is fire-and-forget: a closed socket surfaces as a
     * {@link com.github.auties00.cobalt.exception.WhatsAppSessionException.Closed}
     * which the surrounding {@link #handle(Node)} {@code finally} block
     * already isolates from the mutation path.
     *
     * @param node the original {@code <notification>} stanza
     */
    private void sendNotificationAck(Node node) {
        ackSender.ack(AckClass.NOTIFICATION, node).type("account_sync").send();
    }
}
