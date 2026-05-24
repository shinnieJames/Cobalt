package com.github.auties00.cobalt.stream.notification.business;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.contact.ContactTextStatus;
import com.github.auties00.cobalt.model.contact.ContactTextStatusBuilder;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.newsletter.Newsletter;
import com.github.auties00.cobalt.model.newsletter.NewsletterMetadataBuilder;
import com.github.auties00.cobalt.model.newsletter.NewsletterViewerRole;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Handles {@code type="mex"} notifications carrying server-pushed Meta
 * Exchange (MEX) GraphQL subscription updates.
 *
 * @apiNote
 * Dispatched by {@link NotificationBusinessDispatcher}. The
 * {@code <update op_name="..." />} child carries the JSON payload, and
 * the {@code op_name} attribute selects the per-operation handler:
 * newsletter mutations, text-status updates, group property updates,
 * community-owner updates, username changes, and LID changes. Any
 * operation whose data carries a fatal extension error is logged and
 * skipped without ACK; any operation with no registered handler raises
 * {@link MissingMexNotificationHandlerException} which the dispatch
 * branch turns into a NACK (no ACK sent).
 *
 * @implNote
 * This implementation collapses WA Web's per-operation MEX handlers
 * ({@code WAWebMexNewsletterJoinHandler},
 * {@code WAWebMexUsernameUpdateNotificationHandler}, etc.) into one
 * Cobalt handler that walks the JSON payload directly. WA Web routes
 * each operation through its own SMAX RPC parser; Cobalt reads the
 * JSON inline because the {@code <update>} content is plain JSON
 * (not a SMAX-encoded sub-stanza) and a typed parser would add no
 * validation beyond a key check.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleMexNotification")
final class NotificationMexStreamHandler implements SocketStream.Handler {
    /**
     * Logger used for warnings about parse failures and debug messages
     * about UI-only operations that Cobalt explicitly drops.
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationMexStreamHandler.class.getName());

    /**
     * Operation names WA Web recognises but Cobalt has no consumer for.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandleMexNotification} top-level
     * {@code KNOWN_UNSUPPORTED_OPS} set. Hitting one of these emits a
     * warning log; hitting any other unknown op emits an error log so
     * the orchestrator can sample it for further inspection.
     */
    private static final Set<String> KNOWN_UNSUPPORTED_OPS = Set.of(
            "NotificationLinkedProfilesUpdatesSideSub",
            "NotificationAgeCollection",
            "NotificationLinkedProfilesUpdates"
    );

    /**
     * The {@link WhatsAppClient} used for store reads, server queries
     * (newsletter metadata, push name, about), and ack sends.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The {@link LidMigrationService} used by
     * {@link #handleLidChange(JSONObject)} to migrate a contact's
     * PN-LID mapping atomically across the store.
     */
    private final LidMigrationService lidMigrationService;

    /**
     * The {@link AckSender} used to ship the post-processing
     * {@code <ack class="notification" type="mex"/>} stanza on
     * success.
     */
    private final AckSender ackSender;

    /**
     * Constructs the handler with shared dependencies.
     *
     * @apiNote
     * Called once by {@link NotificationBusinessDispatcher}; embedders
     * do not instantiate this handler directly.
     *
     * @param whatsapp            the {@link WhatsAppClient}
     * @param lidMigrationService the {@link LidMigrationService}
     * @param ackSender           the {@link AckSender}
     */
    NotificationMexStreamHandler(WhatsAppClient whatsapp, LidMigrationService lidMigrationService, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.lidMigrationService = lidMigrationService;
        this.ackSender = ackSender;
    }

    /**
     * Validates the stanza shape and delegates to
     * {@link #handleNotification(Node)}.
     *
     * @apiNote
     * Invoked by {@link NotificationBusinessDispatcher}. Stanzas whose
     * description is not {@code notification} or whose {@code type} is
     * not {@code mex} are silently dropped.
     *
     * @implNote
     * This implementation sends the ACK from the inner dispatch flow
     * (only on success); WA Web returns the ack from the parser
     * promise resolution path of
     * {@code WAWebHandleMexNotification.handleMexNotification}.
     *
     * @param node the incoming {@code <notification>} stanza
     */
    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "mex")) {
            return;
        }

        handleNotification(node);
    }

    /**
     * Parses the {@code <update>} child's JSON payload, validates it for
     * fatal extension errors, and dispatches to the per-operation
     * handler.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code mexNotificationParser} + {@code handleMexNotification}
     * combination. The ACK is only sent on success; failures (parser
     * error, unknown op) NACK by omitting the ACK.
     *
     * @param node the {@code <notification>} stanza
     */
    private void handleNotification(Node node) {
        var updateNode = node.getChild("update").orElse(null);
        if (updateNode == null) {
            return;
        }

        var stanzaId = node.getAttributeAsString("id", null);
        var stanzaFrom = node.getAttributeAsJid("from", null);
        var operationName = updateNode.getAttributeAsString("op_name", "");
        var payload = parsePayload(updateNode);

        try {
            dispatch(operationName, stanzaId, stanzaFrom, payload);
        } catch (MissingMexNotificationHandlerException e) {
            if (KNOWN_UNSUPPORTED_OPS.contains(e.operationName())) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "[mex] handleMexNotification: {0} unsupported, nack", e.operationName());
            } else {
                LOGGER.log(System.Logger.Level.ERROR,
                        "[mex] handleMexNotification: {0} unknown op, nack", e.operationName());
            }
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Cannot handle mex notification {0}: {1}",
                    stanzaId != null ? stanzaId : "<missing>",
                    throwable.getMessage());
        }
    }

    /**
     * Routes the operation to its handler, validates the payload, and
     * sends the ACK on success.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandleMexNotification.f(...)} dispatch table and the
     * {@code h/y(...)} wrapper which checks
     * {@code WAWebMexRelayEnvironment.parseFatalExtensionError} and
     * the non-null {@code data} guard before invoking the handler.
     *
     * @implNote
     * This implementation handles 23 known op names by name and
     * delegates the rest to
     * {@link MissingMexNotificationHandlerException}. UI-only ops
     * (brigading, limit sharing, reachout timelock, integrity
     * challenge, message capping) are explicitly debug-logged because
     * Cobalt has no equivalent UI surface.
     *
     * @param operationName the MEX op name from the {@code op_name} attribute
     * @param stanzaId      the stanza id used in the ACK
     * @param stanzaFrom    the {@code from} JID used in the ACK
     * @param payload       the parsed JSON payload
     * @throws MissingMexNotificationHandlerException when no handler exists for {@code operationName}
     */
    private void dispatch(String operationName, String stanzaId, Jid stanzaFrom, JSONObject payload) {
        var errors = payload.getJSONArray("errors");
        if (hasFatalExtensionError(errors)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "[mex] Fatal extension error in mex notification for operation {0}", operationName);
            return;
        }

        var data = payload.get("data");
        if (data == null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "[mex] null data in parsed json for operation {0}", operationName);
            return;
        }

        switch (operationName) {
            case "MexNotificationEvent" -> {
            }
            case "NotificationNewsletterUserSettingChange",
                 "NotificationNewsletterJoin",
                 "NotificationNewsletterLeave",
                 "NotificationNewsletterStateChange",
                 "NotificationNewsletterAdminMetadataUpdate",
                 "NotificationNewsletterOwnerUpdate",
                 "NotificationNewsletterUpdate",
                 "NotificationNewsletterAdminPromote",
                 "NotificationNewsletterAdminDemote",
                 "NotificationNewsletterAdminInviteRevoke",
                 "NotificationNewsletterWamoSubStatusChange",
                 "NewsletterResponseStateUpdate",
                 "NotificationNewsletterBlockUser",
                 "NotificationNewsletterPaidPartnershipUpdate",
                 "NotificationNewsletterMilestone" -> handleNewsletterOperation(operationName, payload);
            case "TextStatusUpdateNotification",
                 "TextStatusUpdateNotificationSideSub" -> handleTextStatusOperation(payload);
            case "NotificationGroupPropertyUpdate",
                 "NotificationGroupHiddenPropertyUpdate",
                 "NotificationGroupSafetyCheckPropertyUpdate",
                 "NotificationGroupMemberLinkPropertyUpdate",
                 "NotificationGroupMemberShareGroupHistoryModePropertyUpdate" ->
                    refreshGroups(payload);
            case "NotificationCommunityOwnerUpdate" ->
                    refreshGroups(payload);
            case "UsernameSetNotification",
                 "UsernameDeleteNotification",
                 "UsernameUpdateNotification",
                 "AccountSyncUsernameNotification",
                 "LidChangeNotification" -> handleUserOperation(operationName, payload);
            case "NotificationUserBrigadingUpdate" ->
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "Ignoring brigading update mex operation (UI-only feature)");
            case "NotificationGroupLimitSharingPropertyUpdate" ->
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "Ignoring limit sharing update mex operation (UI-only feature)");
            case "NotificationUserReachoutTimelockUpdate" ->
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "Ignoring reachout timelock update mex operation (UI-only feature)");
            case "NotificationIntegrityChallengeRequest" ->
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "Ignoring integrity challenge request mex operation (UI-only feature)");
            case "MessageCappingInfoNotification" ->
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "Ignoring message capping info mex operation (UI-only feature)");
            default -> throw new MissingMexNotificationHandlerException(operationName);
        }

        sendNotificationAck(stanzaId, stanzaFrom);
    }

    /**
     * Parses the {@code <update>} child's text content as a JSON object.
     *
     * @apiNote
     * Returns an empty {@link JSONObject} on blank content or parse
     * failure, mirroring WA Web's {@code XmppParsingFailure} fallback
     * which produces an empty object before
     * {@code parseFatalExtensionError} validates it.
     *
     * @param updateNode the {@code <update>} child of the notification
     * @return the parsed JSON object, never {@code null}
     */
    private JSONObject parsePayload(Node updateNode) {
        var content = updateNode.toContentString().orElse(null);
        if (content == null || content.isBlank()) {
            return new JSONObject();
        }

        try {
            var parsed = JSON.parse(content);
            if (parsed instanceof JSONObject jsonObject) {
                return jsonObject;
            }
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Cannot parse mex JSON payload: {0}", throwable.getMessage());
        }

        return new JSONObject();
    }

    /**
     * Returns whether the {@code errors} array contains a fatal
     * extension error.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebMexRelayEnvironment.parseFatalExtensionError} which
     * walks the array and flags any error whose
     * {@code extensions.is_summary} is {@code true} or whose
     * {@code extensions.error_code} is non-null. Any non-empty errors
     * array is also treated as fatal.
     *
     * @implNote
     * This implementation returns {@code true} for any non-empty errors
     * array even when no entry passes the {@code is_summary}/{@code error_code}
     * check, matching WA Web's behaviour where an unrecognised error
     * triggers a default 500 status.
     *
     * @param errors the {@code errors} JSON array, or {@code null}
     * @return {@code true} when a fatal extension error is present, {@code false} otherwise
     */
    private boolean hasFatalExtensionError(JSONArray errors) {
        if (errors == null || errors.isEmpty()) {
            return false;
        }

        for (var i = 0; i < errors.size(); i++) {
            var error = errors.getJSONObject(i);
            if (error == null) {
                continue;
            }
            var extensions = error.getJSONObject("extensions");
            if (extensions == null) {
                continue;
            }
            if (Boolean.TRUE.equals(extensions.getBoolean("is_summary"))) {
                return true;
            }
            if (extensions.get("error_code") != null) {
                return true;
            }
        }

        return true;
    }

    /**
     * Routes newsletter MEX operations to per-op helpers.
     *
     * @apiNote
     * Most newsletter ops resolve to a metadata refresh
     * ({@link #refreshNewsletters(JSONObject)}); the
     * {@code NotificationNewsletterLeave} op removes the newsletter
     * from the store and
     * {@code NotificationNewsletterStateChange} branches on
     * {@code state.type} ({@code "DELETED"} when requestor leaves vs
     * remote termination).
     *
     * @param operationName the MEX op name
     * @param payload       the parsed JSON payload
     */
    private void handleNewsletterOperation(String operationName, JSONObject payload) {
        switch (operationName) {
            case "NotificationNewsletterLeave" -> {
                var newsletterJid = parseNewsletterId(payload, "xwa2_notify_newsletter_on_leave", "id");
                if (newsletterJid != null) {
                    removeNewsletter(newsletterJid);
                }
            }
            case "NotificationNewsletterStateChange" -> handleNewsletterStateChange(payload);
            default -> refreshNewsletters(payload);
        }
    }

    /**
     * Applies a newsletter state-change event by removing, terminating,
     * or refreshing the newsletter depending on the new state.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebMexNewsletterStateChangeHandler.mexHandleNewsletterStateChange}:
     * a {@code DELETED} state with {@code is_requestor=true} removes the
     * newsletter locally; a {@code DELETED} state without
     * {@code is_requestor} marks it terminated (so the UI can show the
     * "this newsletter no longer exists" tombstone); any other state
     * refreshes the metadata.
     *
     * @param payload the parsed JSON payload
     */
    private void handleNewsletterStateChange(JSONObject payload) {
        var root = payload.getJSONObject("xwa2_notify_newsletter_on_state_change");
        if (root == null) {
            refreshNewsletters(payload);
            return;
        }

        var newsletterJid = parseNewsletterId(root, "id");
        if (newsletterJid == null) {
            return;
        }

        var state = root.getJSONObject("state");
        var stateType = state != null ? state.getString("type") : null;
        if ("DELETED".equals(stateType) && Boolean.TRUE.equals(root.getBoolean("is_requestor"))) {
            removeNewsletter(newsletterJid);
            return;
        }

        if ("DELETED".equals(stateType)) {
            markTerminatedNewsletter(newsletterJid);
            return;
        }

        refreshNewsletter(newsletterJid);
    }

    /**
     * Re-queries each user/LID JID in the payload's about text and
     * writes the result to the contact text-status record.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebMexTextStatusUpdateNotificationHandler.mexHandleTextStatusUpdate}
     * which refreshes the text status for every JID in the
     * {@code xwa2_notify_text_status_update.id} array.
     *
     * @param payload the parsed JSON payload
     */
    private void handleTextStatusOperation(JSONObject payload) {
        for (var jid : collectJids(payload)) {
            if (!jid.hasUserServer() && !jid.hasLidServer()) {
                continue;
            }

            try {
                var statusText = whatsapp.queryAbout(jid).orElse(null);
                upsertContactTextStatus(jid.toUserJid(), statusText, null, null, null);
            } catch (Throwable throwable) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Cannot refresh text-status data for {0}: {1}",
                        jid,
                        throwable.getMessage());
            }
        }
    }

    /**
     * Routes username/LID-change MEX operations to per-op helpers.
     *
     * @apiNote
     * Username operations split into set, delete, update (side-sub),
     * account-sync, and LID change. Each path applies the change to
     * both the contact record and the chat record so the username
     * displays consistently across surfaces.
     *
     * @param operationName the MEX op name
     * @param payload       the parsed JSON payload
     */
    private void handleUserOperation(String operationName, JSONObject payload) {
        switch (operationName) {
            case "UsernameSetNotification" -> handleUsernameSet(payload);
            case "UsernameDeleteNotification" -> handleUsernameDelete(payload);
            case "UsernameUpdateNotification" -> refreshUsers(payload);
            case "AccountSyncUsernameNotification" -> handleUsernameAccountSync(payload);
            case "LidChangeNotification" -> handleLidChange(payload);
            default -> refreshUsers(payload);
        }
    }

    /**
     * Writes the new username from a
     * {@code xwa2_notify_username_on_change} payload to the contact
     * keyed by LID.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebMexUsernameUpdateNotificationHandler.mexHandleUsernameChange}.
     *
     * @param payload the parsed JSON payload
     */
    private void handleUsernameSet(JSONObject payload) {
        var root = payload.getJSONObject("xwa2_notify_username_on_change");
        if (root == null) {
            return;
        }

        var lid = parseJid(root.getString("lid")).orElse(null);
        var username = root.getString("username");
        if (lid == null) {
            return;
        }

        updateUsername(lid.toUserJid(), username);
    }

    /**
     * Clears the username on the contact keyed by LID from a
     * {@code xwa2_notify_username_delete} payload.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebMexUsernameUpdateNotificationHandler.mexHandleUsernameDelete}.
     *
     * @param payload the parsed JSON payload
     */
    private void handleUsernameDelete(JSONObject payload) {
        var root = payload.getJSONObject("xwa2_notify_username_delete");
        if (root == null) {
            return;
        }

        var lid = parseJid(root.getString("lid")).orElse(null);
        if (lid == null) {
            return;
        }

        updateUsername(lid.toUserJid(), null);
    }

    /**
     * Applies the username carried by the
     * {@code xwa2_notify_wa_user} envelope (the account-sync username
     * payload) to the contact keyed by LID.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebMexUsernameAccountSyncNotificationHandler.mexHandleUsernameAccountSync}.
     *
     * @param payload the parsed JSON payload
     */
    private void handleUsernameAccountSync(JSONObject payload) {
        var root = payload.getJSONObject("xwa2_notify_wa_user");
        if (root == null) {
            return;
        }

        var lid = parseJid(root.getString("lid_jid")).orElse(null);
        if (lid == null) {
            return;
        }

        var usernameInfo = root.getJSONObject("username_info");
        var username = usernameInfo != null ? usernameInfo.getString("username") : null;
        updateUsername(lid.toUserJid(), username);
    }

    /**
     * Migrates a contact's LID from {@code old} to {@code new} across the
     * store using {@link LidMigrationService#changeLid(Jid, Jid, Jid)}.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebMexLidChangeNotificationHandler.mexHandleLidChangeNotification}
     * which feeds the {@code (oldLid, newLid)} pair to the LID
     * migration pipeline.
     *
     * @implNote
     * This implementation does not call the typed
     * {@link com.github.auties00.cobalt.node.mex.json.user.LidChangeNotificationMexResponse}
     * parser because the MEX notification body is inline JSON rather
     * than the IQ-wrapped envelope the typed parser expects. The
     * {@code old}/{@code new} keys under
     * {@code xwa2_notify_lid_change} are read directly. When neither
     * a {@code findPhoneByLid} mapping nor a contact record matches,
     * the LID is rewritten in-place on both the contact and chat
     * records as a fallback.
     *
     * @param payload the parsed JSON payload
     */
    private void handleLidChange(JSONObject payload) {
        var root = payload.getJSONObject("xwa2_notify_lid_change");
        if (root == null) {
            return;
        }

        var oldLid = parseJid(root.getString("old")).orElse(null);
        var newLid = parseJid(root.getString("new")).orElse(null);
        if (oldLid == null || newLid == null) {
            return;
        }

        var phoneJid = whatsapp.store().findPhoneByLid(oldLid.toUserJid()).orElse(null);
        if (phoneJid == null) {
            phoneJid = whatsapp.store().findContactByJid(oldLid)
                    .map(Contact::jid)
                    .orElse(null);
        }
        if (phoneJid == null) {
            phoneJid = whatsapp.store().findChatByJid(oldLid)
                    .flatMap(Chat::phoneNumberJid)
                    .orElse(null);
        }

        if (phoneJid != null) {
            lidMigrationService.changeLid(phoneJid, newLid.toUserJid(), oldLid.toUserJid());
            return;
        }

        whatsapp.store().findContactByJid(oldLid)
                .ifPresent(contact -> contact.setLid(newLid.toUserJid()));
        whatsapp.store().findChatByJid(oldLid)
                .ifPresent(chat -> chat.setLid(newLid.toUserJid()));
    }

    /**
     * Re-queries the metadata for every group/community JID found in the
     * payload.
     *
     * @apiNote
     * Used by every {@code NotificationGroup*PropertyUpdate} op and by
     * {@code NotificationCommunityOwnerUpdate}. Failures are debug-logged
     * and the loop continues.
     *
     * @param payload the parsed JSON payload
     */
    private void refreshGroups(JSONObject payload) {
        for (var jid : collectJids(payload)) {
            if (!jid.hasGroupOrCommunityServer()) {
                continue;
            }

            try {
                whatsapp.queryChatMetadata(jid);
            } catch (Throwable throwable) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Cannot refresh group metadata for {0}: {1}",
                        jid,
                        throwable.getMessage());
                }
        }
    }

    /**
     * Refreshes every newsletter JID found in the payload.
     *
     * @apiNote
     * Used by the catch-all newsletter branch when no specific handler
     * matches the op name.
     *
     * @param payload the parsed JSON payload
     */
    private void refreshNewsletters(JSONObject payload) {
        for (var jid : collectJids(payload)) {
            if (!jid.hasNewsletterServer()) {
                continue;
            }

            refreshNewsletter(jid);
        }
    }

    /**
     * Refreshes the push name for every user/LID/bot JID found in the
     * payload.
     *
     * @apiNote
     * Used by {@code UsernameUpdateNotification} (the side-sub variant)
     * and by the default user-op branch.
     *
     * @param payload the parsed JSON payload
     */
    private void refreshUsers(JSONObject payload) {
        for (var jid : collectJids(payload)) {
            if (!jid.hasUserServer() && !jid.hasLidServer() && !jid.hasBotServer()) {
                continue;
            }

            var userJid = jid.toUserJid();
            var contact = whatsapp.store()
                    .findContactByJid(userJid)
                    .orElseGet(() -> whatsapp.store().addNewContact(userJid));
            try {
                whatsapp.queryName(userJid).ifPresent(contact::setChosenName);
            } catch (Throwable throwable) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Cannot refresh username metadata for {0}: {1}",
                        userJid,
                        throwable.getMessage());
            }
        }
    }

    /**
     * Writes the username on the contact and its associated chat,
     * preferring the PN-keyed record when a LID-PN mapping exists.
     *
     * @apiNote
     * Internal helper shared by the username set/delete/account-sync
     * paths.
     *
     * @param lidJid   the LID JID of the contact
     * @param username the new username, or {@code null} to clear
     */
    private void updateUsername(Jid lidJid, String username) {
        var phoneJid = whatsapp.store().findPhoneByLid(lidJid).orElse(null);
        var contact = whatsapp.store()
                .findContactByJid(lidJid)
                .or(() -> phoneJid != null ? whatsapp.store().findContactByJid(phoneJid) : Optional.empty())
                .orElseGet(() -> whatsapp.store().addNewContact(phoneJid != null ? phoneJid : lidJid));
        contact.setLid(lidJid);
        contact.setUsername(username);
        whatsapp.store().addContact(contact);

        whatsapp.store().findChatByJid(lidJid)
                .or(() -> phoneJid != null ? whatsapp.store().findChatByJid(phoneJid) : Optional.empty())
                .ifPresent(chat -> {
                    chat.setLid(lidJid);
                    chat.setUsername(username);
                });
    }

    /**
     * Reads a newsletter JID nested under {@code payload[rootKey][idKey]}.
     *
     * @apiNote
     * Internal helper used by the newsletter leave/state-change paths
     * to descend the envelope before reading the id.
     *
     * @param payload the JSON payload to descend
     * @param rootKey the outer key (envelope name)
     * @param idKey   the inner key naming the id field
     * @return the parsed newsletter JID, or {@code null} when missing or unparsable
     */
    private Jid parseNewsletterId(JSONObject payload, String rootKey, String idKey) {
        var root = payload.getJSONObject(rootKey);
        if (root == null) {
            return null;
        }
        return parseNewsletterId(root, idKey);
    }

    /**
     * Reads a newsletter id from the given JSON object, appending the
     * {@code @newsletter} server when the value is bare.
     *
     * @apiNote
     * Internal helper that accepts both forms WA Web's server emits:
     * fully-qualified {@code 123@newsletter} and bare {@code 123}.
     *
     * @param payload the JSON object to read from
     * @param idKey   the id key
     * @return the parsed newsletter JID, or {@code null} when missing or blank
     */
    private Jid parseNewsletterId(JSONObject payload, String idKey) {
        var id = payload.getString(idKey);
        if (id == null || id.isBlank()) {
            return null;
        }

        if (id.contains("@")) {
            return parseJid(id).orElse(null);
        }

        return parseJid(id + "@newsletter").orElse(null);
    }

    /**
     * Recursively walks a JSON value and collects every string that
     * parses as a {@link Jid}.
     *
     * @apiNote
     * Used by {@link #refreshGroups(JSONObject)},
     * {@link #refreshNewsletters(JSONObject)},
     * {@link #refreshUsers(JSONObject)}, and
     * {@link #handleTextStatusOperation(JSONObject)} to extract the
     * affected ids without coupling to specific envelope shapes.
     *
     * @param value the JSON value to scan (an object, array, or string)
     * @return an insertion-ordered set of parsed JIDs
     */
    private Set<Jid> collectJids(Object value) {
        var result = new LinkedHashSet<Jid>();
        collectJids(value, result);
        return result;
    }

    /**
     * Accumulates JIDs into the supplied set as part of the recursive
     * walk started by {@link #collectJids(Object)}.
     *
     * @apiNote
     * Internal recursion helper; not intended for direct call from
     * outside {@link #collectJids(Object)}.
     *
     * @param value  the current JSON value
     * @param result the accumulator set
     */
    private void collectJids(Object value, Set<Jid> result) {
        if (value == null) {
            return;
        }

        if (value instanceof String stringValue) {
            parseJid(stringValue).ifPresent(result::add);
            return;
        }

        if (value instanceof JSONObject jsonObject) {
            jsonObject.forEach((ignored, nestedValue) -> collectJids(nestedValue, result));
            return;
        }

        if (value instanceof JSONArray jsonArray) {
            jsonArray.forEach(item -> collectJids(item, result));
        }
    }

    /**
     * Parses a string as a {@link Jid}, returning {@link Optional#empty()}
     * for blank or {@code @}-less values.
     *
     * @apiNote
     * Internal helper used by both the JID-collection walk and the
     * username payload paths.
     *
     * @param value the string to parse
     * @return the parsed JID, or empty
     */
    private Optional<Jid> parseJid(String value) {
        if (value == null || value.isBlank() || !value.contains("@")) {
            return Optional.empty();
        }

        try {
            return Optional.of(Jid.of(value));
        } catch (Throwable throwable) {
            return Optional.empty();
        }
    }

    /**
     * Creates or merges a {@link ContactTextStatus} record and fires
     * the change to listeners.
     *
     * @apiNote
     * Shared between the {@code TextStatusUpdateNotification} MEX op
     * branch and possible future text-status sources.
     *
     * @param contactJid              the contact JID
     * @param text                    the new text, or {@code null}
     * @param emoji                   the new emoji, or {@code null}
     * @param ephemeralDurationSeconds the new ephemeral duration in seconds, or {@code null}
     * @param lastUpdateTime          the new last-update time, or {@code null}
     * @return the merged text-status record
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
     * Fires
     * {@link com.github.auties00.cobalt.client.WhatsAppClientListener#onContactTextStatus}
     * on every registered listener on its own virtual thread.
     *
     * @apiNote
     * Internal helper used by
     * {@link #upsertContactTextStatus(Jid, String, String, Integer, Instant)}.
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
     * Returns the newsletter with the given JID, creating a blank record
     * when none exists.
     *
     * @apiNote
     * Internal helper used by every newsletter-update branch.
     *
     * @param newsletterJid the newsletter JID
     * @return the matching {@link Newsletter}
     */
    private Newsletter ensureNewsletter(Jid newsletterJid) {
        return whatsapp.store()
                .findNewsletterByJid(newsletterJid)
                .orElseGet(() -> whatsapp.store().addNewNewsletter(newsletterJid));
    }

    /**
     * Refreshes a newsletter's metadata by re-querying it from the
     * server and applying the result fields.
     *
     * @apiNote
     * Preserves the existing viewer role (subscriber, admin, etc.)
     * across the refresh by passing it as the query parameter, so a
     * server response that omits the role-specific fields does not
     * downgrade the local record.
     *
     * @param newsletterJid the newsletter JID to refresh
     */
    private void refreshNewsletter(Jid newsletterJid) {
        var newsletter = ensureNewsletter(newsletterJid);
        var role = newsletter.viewerMetadata()
                .map(viewerMetadata -> viewerMetadata.role())
                .filter(existingRole -> existingRole != NewsletterViewerRole.UNKNOWN)
                .orElse(NewsletterViewerRole.GUEST);
        var refreshed = whatsapp.queryNewsletter(newsletterJid, role).orElse(null);
        if (refreshed == null) {
            return;
        }

        newsletter.setState(refreshed.state().orElse(null));
        newsletter.setMetadata(refreshed.metadata().orElse(null));
        newsletter.setViewerMetadata(refreshed.viewerMetadata().orElse(null));
        newsletter.setUnreadMessagesCount(refreshed.unreadMessagesCount());
        newsletter.setTimestamp(refreshed.timestamp().orElse(null));
    }

    /**
     * Marks the newsletter as terminated by flipping the
     * {@code terminated} flag on the metadata.
     *
     * @apiNote
     * Used when a {@code DELETED} state arrives without
     * {@code is_requestor=true}, meaning the owner deleted the
     * newsletter; the local record is retained so the UI can show a
     * tombstone.
     *
     * @param newsletterJid the newsletter JID to mark as terminated
     */
    private void markTerminatedNewsletter(Jid newsletterJid) {
        var newsletter = ensureNewsletter(newsletterJid);
        var metadata = newsletter.metadata().orElse(null);
        if (metadata == null) {
            metadata = new NewsletterMetadataBuilder().build();
            newsletter.setMetadata(metadata);
        }
        metadata.setTerminated(true);
    }

    /**
     * Removes the newsletter from the local store.
     *
     * @apiNote
     * Used when the user leaves the newsletter (the
     * {@code NotificationNewsletterLeave} op) and when a
     * {@code DELETED} state arrives with {@code is_requestor=true}.
     *
     * @param newsletterJid the newsletter JID to remove
     */
    private void removeNewsletter(Jid newsletterJid) {
        whatsapp.store().removeNewsletter(newsletterJid);
    }

    /**
     * Sends the {@code <ack class="notification" type="mex"/>} stanza.
     *
     * @apiNote
     * Fire-and-forget; identical attribute set to WA Web's
     * {@code WAWebHandleMexNotification.C(stanzaId, from)} ack-builder.
     * Only invoked on successful dispatch; parse failures and
     * unsupported operations omit the ACK so the server retries.
     *
     * @param stanzaId   the stanza id
     * @param stanzaFrom the {@code from} JID
     */
    private void sendNotificationAck(String stanzaId, Jid stanzaFrom) {
        if (stanzaId == null || stanzaFrom == null) {
            return;
        }
        var synthetic = new NodeBuilder()
                .description("notification")
                .attribute("id", stanzaId)
                .attribute("from", stanzaFrom)
                .build();
        ackSender.ack(AckClass.NOTIFICATION, synthetic).type("mex").send();
    }

    /**
     * Raised when {@link #dispatch(String, String, Jid, JSONObject)}
     * encounters an op name no Cobalt handler knows.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandleMexNotification.MissingMEXNotificationHandler}.
     * Caught inside
     * {@link #handleNotification(Node)} and turned into a NACK (no ACK
     * sent) so the server retries.
     */
    private static final class MissingMexNotificationHandlerException extends RuntimeException {
        /**
         * The op name that had no registered handler.
         */
        private final String operationName;

        /**
         * Constructs the exception for the given op name.
         *
         * @apiNote
         * Constructor is private to the enclosing class; not part of
         * any public API.
         *
         * @param operationName the op name with no handler
         */
        MissingMexNotificationHandlerException(String operationName) {
            super("MissingMEXNotificationHandler: " + operationName);
            this.operationName = operationName;
        }

        /**
         * Returns the op name that triggered this exception.
         *
         * @apiNote
         * Read by {@link #handleNotification(Node)} to choose between
         * the {@code KNOWN_UNSUPPORTED_OPS} warning path and the
         * unknown-op error path.
         *
         * @return the op name
         */
        String operationName() {
            return operationName;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation formats as
         * {@code "MissingMEXNotificationHandler: <op>"} to match WA
         * Web's logging format for the same error.
         */
        @Override
        public String toString() {
            return "MissingMEXNotificationHandler: " + operationName;
        }
    }
}
