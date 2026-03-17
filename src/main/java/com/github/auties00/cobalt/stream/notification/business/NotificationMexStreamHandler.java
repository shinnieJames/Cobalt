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
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

final class NotificationMexStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(NotificationMexStreamHandler.class.getName());
    private final WhatsAppClient whatsapp;
    private final LidMigrationService lidMigrationService;

    NotificationMexStreamHandler(WhatsAppClient whatsapp, LidMigrationService lidMigrationService) {
        this.whatsapp = whatsapp;
        this.lidMigrationService = lidMigrationService;
    }

    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "mex")) {
            return;
        }

        try {
            handleNotification(node);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Cannot handle mex notification {0}: {1}",
                    node.getAttributeAsString("id", "<missing>"),
                    throwable.getMessage());
        } finally {
            sendNotificationAck(node);
        }
    }

    private void handleNotification(Node node) {
        var updateNode = node.getChild("update").orElse(null);
        if (updateNode == null) {
            return;
        }

        var operationName = updateNode.getAttributeAsString("op_name", "");
        var payload = parsePayload(updateNode);
        switch (operationName) {
            case "MexNotificationEvent" -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring empty mex notification event");
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
                 "NotificationNewsletterPaidPartnership",
                 "NotificationNewsletterMilestone" -> handleNewsletterOperation(operationName, payload);
            case "TextStatusUpdateNotification",
                 "TextStatusUpdateNotificationSideSub" -> handleTextStatusOperation(payload);
            case "NotificationGroupPropertyUpdate",
                 "NotificationGroupHiddenPropertyUpdate",
                 "NotificationGroupSafetyCheckPropertyUpdate",
                 "NotificationGroupMemberLinkPropertyUpdate",
                 "NotificationGroupMemberShareGroupHistoryModePropertyUpdate",
                 "NotificationCommunityOwnerUpdate" -> refreshGroups(payload);
            case "UsernameSetNotification",
                 "UsernameDeleteNotification",
                 "UsernameUpdateNotification",
                 "AccountSyncUsernameNotification",
                 "LidChangeNotification" -> handleUserOperation(operationName, payload);
            case "NotificationUserBrigadingUpdate",
                 "NotificationGroupLimitSharingPropertyUpdate",
                 "NotificationUserReachoutTimelockUpdate",
                 "MessageCappingInfoNotification",
                 "NotificationLinkedProfilesUpdatesSideSub",
                 "NotificationAgeCollection",
                 "NotificationLinkedProfilesUpdates" -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unsupported mex operation {0}", operationName);
            default -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unknown mex operation {0}", operationName);
        }
    }

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

    private void refreshNewsletters(JSONObject payload) {
        for (var jid : collectJids(payload)) {
            if (!jid.hasNewsletterServer()) {
                continue;
            }

            refreshNewsletter(jid);
        }
    }

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

    private void updateUsername(Jid lidJid, String username) {
        var phoneJid = whatsapp.store().findPhoneByLid(lidJid).orElse(null);
        var contact = whatsapp.store()
                .findContactByJid(lidJid)
                .or(() -> phoneJid != null ? whatsapp.store().findContactByJid(phoneJid) : java.util.Optional.empty())
                .orElseGet(() -> whatsapp.store().addNewContact(phoneJid != null ? phoneJid : lidJid));
        contact.setLid(lidJid);
        contact.setUsername(username);
        whatsapp.store().addContact(contact);

        whatsapp.store().findChatByJid(lidJid)
                .or(() -> phoneJid != null ? whatsapp.store().findChatByJid(phoneJid) : java.util.Optional.empty())
                .ifPresent(chat -> {
                    chat.setLid(lidJid);
                    chat.setUsername(username);
                });
    }

    private Jid parseNewsletterId(JSONObject payload, String rootKey, String idKey) {
        var root = payload.getJSONObject(rootKey);
        if (root == null) {
            return null;
        }
        return parseNewsletterId(root, idKey);
    }

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

    private Set<com.github.auties00.cobalt.model.jid.Jid> collectJids(Object value) {
        var result = new LinkedHashSet<com.github.auties00.cobalt.model.jid.Jid>();
        collectJids(value, result);
        return result;
    }

    private void collectJids(Object value, Set<com.github.auties00.cobalt.model.jid.Jid> result) {
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

    private java.util.Optional<Jid> parseJid(String value) {
        if (value == null || value.isBlank() || !value.contains("@")) {
            return java.util.Optional.empty();
        }

        try {
            return java.util.Optional.of(Jid.of(value));
        } catch (Throwable throwable) {
            return java.util.Optional.empty();
        }
    }

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

    private void notifyContactTextStatusChanged(Jid contactJid, ContactTextStatus status) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onContactTextStatus(whatsapp, contactJid, status));
        }
    }

    private Newsletter ensureNewsletter(Jid newsletterJid) {
        return whatsapp.store()
                .findNewsletterByJid(newsletterJid)
                .orElseGet(() -> whatsapp.store().addNewNewsletter(newsletterJid));
    }

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

    private void markTerminatedNewsletter(Jid newsletterJid) {
        var newsletter = ensureNewsletter(newsletterJid);
        var metadata = newsletter.metadata().orElse(null);
        if (metadata == null) {
            metadata = new NewsletterMetadataBuilder().build();
            newsletter.setMetadata(metadata);
        }
        metadata.setTerminated(true);
    }

    private void removeNewsletter(Jid newsletterJid) {
        whatsapp.store().removeNewsletter(newsletterJid);
    }

    private void sendNotificationAck(Node node) {
        var stanzaId = node.getAttributeAsString("id", null);
        var stanzaFrom = node.getAttributeAsJid("from", null);
        if (stanzaId == null || stanzaFrom == null) {
            return;
        }

        whatsapp.sendNodeWithNoResponse(new com.github.auties00.cobalt.node.NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId)
                .attribute("class", node.description())
                .attribute("to", stanzaFrom)
                .attribute("type", node.getAttributeAsString("type", null))
                .attribute("participant", node.getAttributeAsJid("participant", null))
                .build());
    }
}
