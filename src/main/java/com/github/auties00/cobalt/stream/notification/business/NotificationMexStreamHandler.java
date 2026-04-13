package com.github.auties00.cobalt.stream.notification.business;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.client.WhatsAppClient;
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
 * Handles incoming MEX (Meta Exchange) notification stanzas from the WhatsApp server.
 *
 * <p>This handler parses the notification, validates the MEX response payload (checking
 * for fatal extension errors and non-null data), dispatches to the appropriate operation
 * handler based on the {@code op_name} attribute, and sends an acknowledgement stanza
 * only on successful processing.</p>
 *
 * @implNote WAWebHandleMexNotification
 */
final class NotificationMexStreamHandler implements SocketStream.Handler {
    /**
     * Logger instance for this handler.
     *
     * @implNote WAWebHandleMexNotification — uses WALogger for logging
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationMexStreamHandler.class.getName());

    /**
     * Set of operation names that are known but unsupported.
     * When these operations are encountered and unhandled, a warning is logged
     * and the notification is nacked (no ack sent) rather than throwing an error.
     *
     * @implNote WAWebHandleMexNotification.c
     */
    private static final Set<String> KNOWN_UNSUPPORTED_OPS = Set.of(
            "NotificationLinkedProfilesUpdatesSideSub",
            "NotificationAgeCollection",
            "NotificationLinkedProfilesUpdates"
    );

    /**
     * The WhatsApp client instance used for making queries and accessing the store.
     *
     * @implNote WAWebHandleMexNotification — injected dependency, replaces module-level imports
     */
    private final WhatsAppClient whatsapp;

    /**
     * The LID migration service used for handling LID change notifications.
     *
     * @implNote WAWebMexLidChangeNotificationHandler — injected dependency
     */
    private final LidMigrationService lidMigrationService;

    /**
     * Constructs a new notification MEX stream handler.
     *
     * @implNote WAWebHandleMexNotification — constructor DI replaces module-level imports
     * @param whatsapp the WhatsApp client instance
     * @param lidMigrationService the LID migration service
     */
    NotificationMexStreamHandler(WhatsAppClient whatsapp, LidMigrationService lidMigrationService) {
        this.whatsapp = whatsapp;
        this.lidMigrationService = lidMigrationService;
    }

    /**
     * Handles an incoming node by checking if it is a MEX notification
     * and dispatching it for processing.
     *
     * <p>Unlike the WA Web implementation which returns the ack stanza from the handler,
     * this method handles ack sending within the dispatch flow, sending it only on
     * successful processing.</p>
     *
     * @implNote WAWebHandleMexNotification.handleMexNotification
     * @param node the incoming node to handle
     */
    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "mex")) {
            return;
        }

        handleNotification(node);
    }

    /**
     * Parses and dispatches a MEX notification stanza.
     *
     * <p>Extracts the update child node, parses the JSON payload, validates it
     * for fatal extension errors and non-null data, then dispatches to the
     * appropriate operation handler. An ack is sent only on success.</p>
     *
     * @implNote WAWebHandleMexNotification.handleMexNotification (function p/_ )
     * @param node the notification node
     */
    private void handleNotification(Node node) {
        // WAWebHandleMexNotification — parser m: e.assertTag("notification"), e.assertAttr("type","mex")
        var updateNode = node.getChild("update").orElse(null);
        if (updateNode == null) {
            return;
        }

        // WAWebHandleMexNotification — parser m: extract fields
        var stanzaId = node.getAttributeAsString("id", null);
        var stanzaFrom = node.getAttributeAsJid("from", null);
        var operationName = updateNode.getAttributeAsString("op_name", "");
        var payload = parsePayload(updateNode);

        try {
            dispatch(operationName, stanzaId, stanzaFrom, payload);
        } catch (MissingMexNotificationHandlerException e) {
            // WAWebHandleMexNotification — error handling for MissingMEXNotificationHandler
            if (KNOWN_UNSUPPORTED_OPS.contains(e.operationName())) {
                // WAWebHandleMexNotification — known unsupported ops: just WARN, nack
                LOGGER.log(System.Logger.Level.WARNING,
                        "[mex] handleMexNotification: {0} unsupported, nack", e.operationName());
            } else {
                // WAWebHandleMexNotification — unknown ops: ERROR and nack (throw)
                LOGGER.log(System.Logger.Level.ERROR,
                        "[mex] handleMexNotification: {0} unknown op, nack", e.operationName());
            }
            // No ack sent (nack behavior)
        } catch (Throwable throwable) {
            // WAWebHandleMexNotification — unexpected error: no ack (nack)
            LOGGER.log(System.Logger.Level.WARNING,
                    "Cannot handle mex notification {0}: {1}",
                    stanzaId != null ? stanzaId : "<missing>",
                    throwable.getMessage());
        }
    }

    /**
     * Dispatches a MEX notification to the appropriate operation handler,
     * validates the payload, and sends an ack on success.
     *
     * <p>This method combines the WA Web dispatch function {@code f} with the
     * wrapper function {@code h/y} that checks for fatal extension errors,
     * validates non-null data, and produces the ack stanza.</p>
     *
     * @implNote WAWebHandleMexNotification.f and WAWebHandleMexNotification.h/y
     * @param operationName the MEX operation name from the {@code op_name} attribute
     * @param stanzaId the stanza id for acknowledgement
     * @param stanzaFrom the stanza sender jid for acknowledgement
     * @param payload the parsed JSON payload from the update node content
     * @throws MissingMexNotificationHandlerException if no handler exists for the operation
     */
    private void dispatch(String operationName, String stanzaId, Jid stanzaFrom, JSONObject payload) {
        // WAWebHandleMexNotification.h/y — check for fatal extension errors
        var errors = payload.getJSONArray("errors"); // WAWebHandleMexNotification.y — n.errors
        if (hasFatalExtensionError(errors)) { // WAWebMexRelayEnvironment.parseFatalExtensionError
            LOGGER.log(System.Logger.Level.WARNING,
                    "[mex] Fatal extension error in mex notification for operation {0}", operationName);
            return;
        }

        // WAWebHandleMexNotification.y — n.data != null check
        var data = payload.get("data");
        if (data == null) {
            // WAWebHandleMexNotification — MexNotificationEvent (function b/v) is a no-op handler
            if ("MexNotificationEvent".equals(operationName)) {
                // WAWebHandleMexNotification.b/v — empty async handler, still acks
                sendNotificationAck(stanzaId, stanzaFrom);
                return;
            }
            LOGGER.log(System.Logger.Level.WARNING,
                    "[mex] null data in parsed json for operation {0}", operationName);
            return;
        }

        // WAWebHandleMexNotification.f — dispatch by OperationName
        switch (operationName) {
            case "MexNotificationEvent" -> {
                // WAWebHandleMexNotification.b/v — empty async handler (no-op)
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
                    refreshGroups(payload); // WAWebMexGroupPropertyUpdateNotificationHandler.mexHandleGroupPropertyUpdate
            case "NotificationCommunityOwnerUpdate" ->
                    refreshGroups(payload); // ADAPTED: WAWebMexCommunityOwnerUpdateNotificationHandler.mexHandleCommunityOwnerUpdate
            case "UsernameSetNotification",
                 "UsernameDeleteNotification",
                 "UsernameUpdateNotification",
                 "AccountSyncUsernameNotification",
                 "LidChangeNotification" -> handleUserOperation(operationName, payload);
            case "NotificationUserBrigadingUpdate" ->
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "Ignoring brigading update mex operation (UI-only feature)");
                    // ADAPTED: WAWebHandleBrigadingUpdateNotification.mexHandleBrigadingNotification — UI/IndexedDB only
            case "NotificationGroupLimitSharingPropertyUpdate" ->
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "Ignoring limit sharing update mex operation (UI-only feature)");
                    // ADAPTED: WAWebMexLimitSharingUpdateHandler.mexHandleLimitSharingUpdate — UI/Opus gating only
            case "NotificationUserReachoutTimelockUpdate" ->
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "Ignoring reachout timelock update mex operation (UI-only feature)");
                    // ADAPTED: WAWebMexReachoutTimelockNotificationHandler.mexHandleReachoutTimelockNotification — UI only
            case "NotificationIntegrityChallengeRequest" ->
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "Ignoring integrity challenge request mex operation (UI-only feature)");
                    // ADAPTED: WAWebMexIntegrityChallengeNotificationHandler.mexHandleIntegrityChallengeNotification — UI only
            case "MessageCappingInfoNotification" ->
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "Ignoring message capping info mex operation (UI-only feature)");
                    // ADAPTED: WAWebNewChatMessageCappingNotificationHandler.mexHandleNewChatMessageCappingNotification — UI only
            default -> throw new MissingMexNotificationHandlerException(operationName);
        }

        // WAWebHandleMexNotification.y — on success, send ack via C(stanzaId, from)
        sendNotificationAck(stanzaId, stanzaFrom);
    }

    /**
     * Parses the JSON content of an update node into a {@link JSONObject}.
     *
     * <p>If the content is blank or cannot be parsed as JSON, returns an empty
     * {@link JSONObject}.</p>
     *
     * @implNote WAWebHandleMexNotification — parser m: JSON.parse(contentString)
     * @param updateNode the update child node of the notification
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
            // WAWebHandleMexNotification — parser m throws XmppParsingFailure on JSON parse error
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Cannot parse mex JSON payload: {0}", throwable.getMessage());
        }

        return new JSONObject();
    }

    /**
     * Checks whether the errors array from a MEX response contains a fatal extension error.
     *
     * <p>A fatal extension error is identified by finding an error with
     * {@code extensions.is_summary == true} or an error with a non-null
     * {@code extensions.error_code}. If neither is found but errors exist,
     * the first error is treated as fatal with error_code 500.</p>
     *
     * @implNote WAWebMexRelayEnvironment.parseFatalExtensionError (function p)
     * @param errors the errors array from the MEX response, may be {@code null}
     * @return {@code true} if a fatal extension error was found, {@code false} otherwise
     */
    private boolean hasFatalExtensionError(JSONArray errors) {
        if (errors == null || errors.isEmpty()) {
            return false; // WAWebMexRelayEnvironment.p — if errors null or empty, return null
        }

        // WAWebMexRelayEnvironment.p — look for is_summary or error_code
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

        // WAWebMexRelayEnvironment.p — if no specific match, first error is fatal (error_code=500)
        return true;
    }

    /**
     * Handles newsletter-related MEX operations by dispatching to the appropriate
     * newsletter-specific handler based on the operation name.
     *
     * @implNote WAWebHandleMexNotification.f — newsletter operation dispatch
     * @param operationName the MEX operation name
     * @param payload the parsed JSON payload
     */
    private void handleNewsletterOperation(String operationName, JSONObject payload) {
        switch (operationName) {
            case "NotificationNewsletterLeave" -> {
                // WAWebMexNewsletterLeaveHandler.mexHandleNewsletterLeave
                var newsletterJid = parseNewsletterId(payload, "xwa2_notify_newsletter_on_leave", "id");
                if (newsletterJid != null) {
                    removeNewsletter(newsletterJid);
                }
            }
            case "NotificationNewsletterStateChange" -> handleNewsletterStateChange(payload);
                // WAWebMexNewsletterStateChangeHandler.mexHandleNewsletterStateChange
            default -> refreshNewsletters(payload);
                // ADAPTED: remaining newsletter ops refresh metadata
        }
    }

    /**
     * Handles a newsletter state change notification by parsing the state and
     * either removing, marking as terminated, or refreshing the newsletter.
     *
     * @implNote WAWebMexNewsletterStateChangeHandler.mexHandleNewsletterStateChange
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
     * Handles text status update notifications by querying the about status
     * for each JID found in the payload and upserting the contact text status.
     *
     * @implNote WAWebMexTextStatusUpdateNotificationHandler.mexHandleTextStatusUpdate and mexHandleTextStatusUpdateSideSub
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
     * Dispatches user-related MEX operations to the appropriate handler based
     * on the operation name.
     *
     * @implNote WAWebHandleMexNotification.f — user operation dispatch
     * @param operationName the MEX operation name
     * @param payload the parsed JSON payload
     */
    private void handleUserOperation(String operationName, JSONObject payload) {
        switch (operationName) {
            case "UsernameSetNotification" -> handleUsernameSet(payload);
                // WAWebMexUsernameUpdateNotificationHandler.mexHandleUsernameChange
            case "UsernameDeleteNotification" -> handleUsernameDelete(payload);
                // WAWebMexUsernameUpdateNotificationHandler.mexHandleUsernameDelete
            case "UsernameUpdateNotification" -> refreshUsers(payload);
                // ADAPTED: WAWebMexUsernameUpdateNotificationHandler.mexHandleUsernameChangeForSideSub
            case "AccountSyncUsernameNotification" -> handleUsernameAccountSync(payload);
                // WAWebMexUsernameAccountSyncNotificationHandler.mexHandleUsernameAccountSync
            case "LidChangeNotification" -> handleLidChange(payload);
                // WAWebMexLidChangeNotificationHandler.mexHandleLidChangeNotification
            default -> refreshUsers(payload);
        }
    }

    /**
     * Handles a username set notification by extracting the LID and username
     * from the payload and updating the contact.
     *
     * @implNote WAWebMexUsernameUpdateNotificationHandler.mexHandleUsernameChange
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
     * Handles a username delete notification by extracting the LID from the
     * payload and clearing the username on the contact.
     *
     * @implNote WAWebMexUsernameUpdateNotificationHandler.mexHandleUsernameDelete
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
     * Handles an account sync username notification by extracting the LID and
     * optional username info from the payload.
     *
     * @implNote WAWebMexUsernameAccountSyncNotificationHandler.mexHandleUsernameAccountSync
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
     * Handles a LID change notification by migrating the old LID to the new LID
     * across contacts, chats, and the LID migration service.
     *
     * @implNote WAWebMexLidChangeNotificationHandler.mexHandleLidChangeNotification
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
     * Refreshes group metadata for all group/community JIDs found in the payload.
     *
     * @implNote WAWebMexGroupPropertyUpdateNotificationHandler.mexHandleGroupPropertyUpdate
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
     * Refreshes newsletter metadata for all newsletter JIDs found in the payload.
     *
     * @implNote WAWebHandleMexNotification.f — newsletter operations dispatch to per-op handlers
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
     * Refreshes user (contact) metadata for all user/LID/bot JIDs found in the payload.
     *
     * @implNote WAWebMexUsernameUpdateNotificationHandler.mexHandleUsernameChangeForSideSub
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
     * Updates the username for a contact identified by its LID JID.
     * Also updates the associated chat if one exists.
     *
     * @implNote WAWebMexUsernameUpdateNotificationHandler.mexHandleUsernameChange
     * @param lidJid the LID JID of the contact
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
     * Parses a newsletter JID from a nested JSON structure.
     *
     * @implNote WAWebHandleMexNotification — newsletter id extraction
     * @param payload the JSON payload
     * @param rootKey the key for the root JSON object
     * @param idKey the key for the newsletter id within the root
     * @return the parsed newsletter {@link Jid}, or {@code null} if not found
     */
    private Jid parseNewsletterId(JSONObject payload, String rootKey, String idKey) {
        var root = payload.getJSONObject(rootKey);
        if (root == null) {
            return null;
        }
        return parseNewsletterId(root, idKey);
    }

    /**
     * Parses a newsletter JID from a JSON object by key.
     * Appends {@code @newsletter} suffix if the id does not already contain {@code @}.
     *
     * @implNote WAWebHandleMexNotification — newsletter id extraction
     * @param payload the JSON object containing the newsletter id
     * @param idKey the key for the newsletter id
     * @return the parsed newsletter {@link Jid}, or {@code null} if not found
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
     * Recursively collects all JIDs from a JSON value (object, array, or string).
     *
     * @implNote WAWebHandleMexNotification — JID extraction from MEX response data
     * @param value the JSON value to scan
     * @return a set of all parsed JIDs, preserving insertion order
     */
    private Set<Jid> collectJids(Object value) {
        var result = new LinkedHashSet<Jid>();
        collectJids(value, result);
        return result;
    }

    /**
     * Recursively collects JIDs from a JSON value into the provided set.
     *
     * @implNote WAWebHandleMexNotification — JID extraction from MEX response data
     * @param value the JSON value to scan
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
     * Parses a string value into a {@link Jid}, returning empty if the value
     * is {@code null}, blank, or does not contain an {@code @} separator.
     *
     * @implNote WAWebHandleMexNotification — JID parsing utility
     * @param value the string to parse
     * @return an {@link Optional} containing the parsed JID, or empty
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
     * Upserts a contact text status in the store and notifies listeners.
     *
     * @implNote WAWebMexTextStatusUpdateNotificationHandler — status update logic
     * @param contactJid the contact JID to update
     * @param text the status text
     * @param emoji the status emoji, or {@code null}
     * @param ephemeralDurationSeconds the ephemeral duration in seconds, or {@code null}
     * @param lastUpdateTime the last update time, or {@code null}
     * @return the upserted contact text status
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
     * Notifies all registered listeners of a contact text status change.
     *
     * @implNote WAWebMexTextStatusUpdateNotificationHandler — listener notification
     * @param contactJid the contact JID whose status changed
     * @param status the updated contact text status
     */
    private void notifyContactTextStatusChanged(Jid contactJid, ContactTextStatus status) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onContactTextStatus(whatsapp, contactJid, status));
        }
    }

    /**
     * Ensures a newsletter exists in the store, creating a new one if necessary.
     *
     * @implNote WAWebHandleMexNotification — newsletter store lookup/creation
     * @param newsletterJid the newsletter JID
     * @return the existing or newly created newsletter
     */
    private Newsletter ensureNewsletter(Jid newsletterJid) {
        return whatsapp.store()
                .findNewsletterByJid(newsletterJid)
                .orElseGet(() -> whatsapp.store().addNewNewsletter(newsletterJid));
    }

    /**
     * Refreshes a newsletter's metadata by querying the server.
     *
     * @implNote WAWebHandleMexNotification — newsletter refresh after notification
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
     * Marks a newsletter as terminated by setting its metadata terminated flag.
     *
     * @implNote WAWebMexNewsletterStateChangeHandler — DELETED state without requestor
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
     * Removes a newsletter from the store.
     *
     * @implNote WAWebMexNewsletterLeaveHandler — newsletter removal
     * @param newsletterJid the newsletter JID to remove
     */
    private void removeNewsletter(Jid newsletterJid) {
        whatsapp.store().removeNewsletter(newsletterJid);
    }

    /**
     * Sends a notification acknowledgement stanza to the server.
     *
     * <p>The ack format matches WA Web's ack builder: tag {@code "ack"} with
     * attributes {@code id}, {@code to}, {@code class="notification"}, and
     * {@code type="mex"}.</p>
     *
     * @implNote WAWebHandleMexNotification.C — ack stanza builder
     * @param stanzaId the stanza id to acknowledge
     * @param stanzaFrom the JID to send the ack to
     */
    private void sendNotificationAck(String stanzaId, Jid stanzaFrom) {
        if (stanzaId == null || stanzaFrom == null) {
            return;
        }

        // WAWebHandleMexNotification.C — wap("ack", {id, to, class:"notification", type:"mex"})
        whatsapp.sendNodeWithNoResponse(new NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId)
                .attribute("to", stanzaFrom)
                .attribute("class", "notification")
                .attribute("type", "mex")
                .build());
    }

    /**
     * Exception thrown when no handler is registered for a MEX notification operation name.
     *
     * <p>This is the Cobalt equivalent of WA Web's {@code MissingMEXNotificationHandler} error
     * class. It is used internally to trigger the unsupported/unknown operation error handling
     * flow in the dispatch method.</p>
     *
     * @implNote WAWebHandleMexNotification.MissingMEXNotificationHandler (class g)
     */
    private static final class MissingMexNotificationHandlerException extends RuntimeException {
        /**
         * The operation name that has no registered handler.
         *
         * @implNote WAWebHandleMexNotification.MissingMEXNotificationHandler.operationName
         */
        private final String operationName;

        /**
         * Constructs a new exception for an unhandled MEX operation.
         *
         * @implNote WAWebHandleMexNotification.MissingMEXNotificationHandler constructor
         * @param operationName the unhandled operation name
         */
        MissingMexNotificationHandlerException(String operationName) {
            super("MissingMEXNotificationHandler: " + operationName);
            this.operationName = operationName;
        }

        /**
         * Returns the operation name that has no registered handler.
         *
         * @implNote WAWebHandleMexNotification.MissingMEXNotificationHandler.operationName
         * @return the unhandled operation name
         */
        String operationName() {
            return operationName;
        }

        /**
         * Returns a string representation including the operation name.
         *
         * @implNote WAWebHandleMexNotification.MissingMEXNotificationHandler.toString
         * @return the string representation
         */
        @Override
        public String toString() {
            return "MissingMEXNotificationHandler: " + operationName;
        }
    }
}
