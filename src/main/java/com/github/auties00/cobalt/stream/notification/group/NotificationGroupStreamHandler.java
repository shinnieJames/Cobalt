package com.github.auties00.cobalt.stream.notification.group;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.ChatEphemeralTimer;
import com.github.auties00.cobalt.model.chat.ChatMetadata;
import com.github.auties00.cobalt.model.chat.ChatPolicy;
import com.github.auties00.cobalt.model.chat.community.CommunityMetadata;
import com.github.auties00.cobalt.model.chat.group.GroupMetadata;
import com.github.auties00.cobalt.model.chat.group.GroupParticipant;
import com.github.auties00.cobalt.model.chat.group.GroupParticipantBuilder;
import com.github.auties00.cobalt.model.chat.group.GroupPartipantRole;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;
import java.util.LinkedHashSet;

public final class NotificationGroupStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(NotificationGroupStreamHandler.class.getName());
    private final WhatsAppClient whatsapp;

    public NotificationGroupStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "w:gp2")) {
            return;
        }

        try {
            handleNotification(node);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Cannot handle w:gp2 notification {0}: {1}",
                    node.getAttributeAsString("id", "<missing>"),
                    throwable.getMessage());
        } finally {
            sendNotificationAck(node);
        }
    }

    private void handleNotification(Node node) {
        var groupJid = node.getAttributeAsJid("from").orElse(null);
        if (groupJid == null || !groupJid.hasGroupOrCommunityServer()) {
            return;
        }

        if (node.hasChild("groups_dirty")) {
            refreshGroup(groupJid);
            return;
        }

        var chat = whatsapp.store()
                .findChatByJid(groupJid)
                .orElseGet(() -> whatsapp.store().addNewChat(groupJid));
        var notificationTimestamp = resolveInstant(node, "t");
        if (notificationTimestamp != null) {
            chat.setConversationTimestamp(notificationTimestamp);
            chat.setLastMsgTimestamp(notificationTimestamp);
        }
        var relatedGroups = new LinkedHashSet<Jid>();
        relatedGroups.add(groupJid);

        for (var action : node.children()) {
            handleAction(node, chat, groupJid, action, relatedGroups);
        }

        for (var relatedGroup : relatedGroups) {
            refreshGroup(relatedGroup);
        }
    }

    private void handleAction(Node notification, Chat chat, Jid groupJid, Node action, LinkedHashSet<Jid> relatedGroups) {
        collectRelatedGroups(action, relatedGroups);

        switch (action.description()) {
            case "create" -> applyCreate(chat, groupJid, action);
            case "add" -> applyParticipants(groupJid, action, GroupParticipantMutation.ADD);
            case "remove" -> applyParticipants(groupJid, action, GroupParticipantMutation.REMOVE);
            case "promote", "linked_group_promote" -> applyParticipants(groupJid, action, GroupParticipantMutation.PROMOTE);
            case "demote", "linked_group_demote" -> applyParticipants(groupJid, action, GroupParticipantMutation.DEMOTE);
            case "modify" -> applyParticipants(groupJid, action, GroupParticipantMutation.MODIFY);
            case "subject" -> applySubject(notification, chat, groupJid, action);
            case "description", "desc" -> applyDescription(notification, chat, groupJid, action);
            case "locked" -> applyRestrict(groupJid, true);
            case "unlocked" -> applyRestrict(groupJid, false);
            case "announce", "announcement" -> applyAnnounce(groupJid, true);
            case "not_announce", "not_announcement" -> applyAnnounce(groupJid, false);
            case "no_frequently_forwarded" -> applyNoFrequentlyForwarded(groupJid, true);
            case "frequently_forwarded_ok" -> applyNoFrequentlyForwarded(groupJid, false);
            case "ephemeral" -> applyEphemeral(notification, chat, groupJid, action);
            case "not_ephemeral" -> clearEphemeral(chat, groupJid);
            case "growth_locked" -> applyGrowthLock(groupJid, action);
            case "growth_unlocked" -> clearGrowthLock(groupJid);
            case "link" -> applyLink(groupJid, action);
            case "unlink" -> applyUnlink(groupJid, action);
            case "membership_approval_mode" -> applyMembershipApproval(action, groupJid);
            case "allow_admin_reports" -> applyReportToAdmin(groupJid, true);
            case "not_allow_admin_reports" -> applyReportToAdmin(groupJid, false);
            case "allow_non_admin_sub_group_creation" -> applyNonAdminSubgroupCreation(groupJid, true);
            case "not_allow_non_admin_sub_group_creation" -> applyNonAdminSubgroupCreation(groupJid, false);
            case "member_add_mode" -> applyMemberAddMode(groupJid, action);
            case "auto_add_disabled" -> applyGeneralChatAutoAddDisabled(groupJid, true);
            case "group_safety_check" -> applyGroupSafetyCheck(groupJid, true);
            case "suspended" -> applySuspended(chat, groupJid, true);
            case "unsuspended" -> applySuspended(chat, groupJid, false);
            case "delete" -> {
                whatsapp.store().removeChatMetadata(groupJid);
                chat.setTerminated(true);
            }
            case "invite",
                 "revoke_invite",
                 "membership_approval_request",
                 "reports",
                 "created_membership_requests",
                 "revoked_membership_requests",
                 "created_sub_group_suggestion",
                 "created_subgroup_suggestion",
                 "revoked_sub_group_suggestions",
                 "change_number",
                 "missing_participant_identification" -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Handling w:gp2 action {0} conservatively via metadata refresh",
                    action.description());
            default -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unsupported w:gp2 action {0}", action.description());
        }
    }

    private void applyCreate(Chat chat, Jid groupJid, Node action) {
        var groupNode = action.getChild("group").orElse(action);
        var subject = groupNode.getAttributeAsString("subject", null);
        if (subject != null) {
            chat.setName(subject);
        }

        chat.setCreatedAt(groupNode.getAttributeAsLong("creation", (Long) null));
        chat.setCreatedBy(groupNode.getAttributeAsString("creator", null));
        chat.setDescription(resolveDescriptionBody(groupNode));
        chat.setSupport(groupNode.hasChild("support"));
        chat.setDefaultSubgroup(groupNode.hasChild("default_sub_group"));
        chat.setArchived(false);
        chat.setSuspended(groupNode.hasChild("suspended"));
        chat.setTerminated(false);

        applyEphemeral(groupNode, chat, groupJid, groupNode);

        var metadata = currentMetadata(groupJid);
        if (metadata != null) {
            metadata.clearParticipants();
            metadata.addAllParticipants(parseParticipants(groupNode, GroupParticipantMutation.ADD));
            setSubject(metadata, subject);
            setDescription(metadata, resolveDescriptionBody(groupNode), resolveDescriptionId(groupNode), resolveInstant(groupNode, "t"), groupNode.getAttributeAsJid("participant").orElse(null));
        }
    }

    private void applySubject(Node notification, Chat chat, Jid groupJid, Node action) {
        var subject = action.getAttributeAsString("subject", null);
        if (subject == null) {
            return;
        }

        chat.setName(subject);
        var metadata = currentMetadata(groupJid);
        if (metadata != null) {
            setSubject(metadata, subject);
            setSubjectTimestamp(metadata, resolveInstant(action, "s_t", "t"), action.getAttributeAsJid("s_o").orElse(notification.getAttributeAsJid("participant").orElse(null)));
        }
    }

    private void applyDescription(Node notification, Chat chat, Jid groupJid, Node action) {
        var deleted = action.hasChild("delete");
        var description = deleted ? null : resolveDescriptionBody(action);
        var descriptionId = deleted ? null : resolveDescriptionId(action);
        var timestamp = resolveInstant(action, "t");
        if (timestamp == null) {
            timestamp = resolveInstant(notification, "t");
        }
        var author = action.getAttributeAsJid("participant").orElse(notification.getAttributeAsJid("participant").orElse(null));

        chat.setDescription(description);
        var metadata = currentMetadata(groupJid);
        if (metadata != null) {
            setDescription(metadata, description, descriptionId, timestamp, author);
        }
    }

    private void applyRestrict(Jid groupJid, boolean restricted) {
        var metadata = currentMetadata(groupJid);
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setRestrict(ChatPolicy.of(restricted));
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setRestrict(restricted);
        }
    }

    private void applyAnnounce(Jid groupJid, boolean announcementOnly) {
        var metadata = currentMetadata(groupJid);
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setAnnounce(ChatPolicy.of(announcementOnly));
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setAnnounce(announcementOnly);
        }
    }

    private void applyNoFrequentlyForwarded(Jid groupJid, boolean value) {
        var metadata = currentMetadata(groupJid);
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setNoFrequentlyForwarded(value);
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setNoFrequentlyForwarded(value);
        }
    }

    private void applyEphemeral(Node notification, Chat chat, Jid groupJid, Node action) {
        var expiration = action.getAttributeAsInt("expiration", (Integer) null);
        if (expiration == null) {
            return;
        }

        var timer = ChatEphemeralTimer.of(expiration);
        var timestamp = resolveInstant(action, "t");
        if (timestamp == null) {
            timestamp = resolveInstant(notification, "t");
        }

        chat.setEphemeralExpiration(timer);
        if (timestamp != null) {
            chat.setEphemeralSettingTimestamp(timestamp);
        }

        var metadata = currentMetadata(groupJid);
        if (metadata != null) {
            metadata.setEphemeralExpiration(timer);
        }
    }

    private void clearEphemeral(Chat chat, Jid groupJid) {
        chat.setEphemeralExpiration(null);
        var metadata = currentMetadata(groupJid);
        if (metadata != null) {
            metadata.setEphemeralExpiration(null);
        }
    }

    private void applyGrowthLock(Jid groupJid, Node action) {
        var metadata = currentMetadata(groupJid);
        var expiration = resolveInstant(action, "expiration");
        var type = action.getAttributeAsString("type", null);
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setGrowthLockExpiration(expiration);
            groupMetadata.setGrowthLockType(type);
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setGrowthLockExpiration(expiration);
            communityMetadata.setGrowthLockType(type);
        }
    }

    private void clearGrowthLock(Jid groupJid) {
        var metadata = currentMetadata(groupJid);
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setGrowthLockExpiration(null);
            groupMetadata.setGrowthLockType(null);
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setGrowthLockExpiration(null);
            communityMetadata.setGrowthLockType(null);
        }
    }

    private void applyLink(Jid groupJid, Node action) {
        var linkType = action.getAttributeAsString("link_type", null);
        var metadata = currentMetadata(groupJid);
        if (metadata instanceof GroupMetadata groupMetadata && "parent_group".equals(linkType)) {
            action.getChildren("group").stream()
                    .map(child -> child.getAttributeAsJid("jid").orElse(null))
                    .filter(related -> related != null && related.hasGroupOrCommunityServer())
                    .findFirst()
                    .ifPresent(groupMetadata::setParentCommunityJid);
        }
    }

    private void applyUnlink(Jid groupJid, Node action) {
        var unlinkType = action.getAttributeAsString("unlink_type", null);
        var metadata = currentMetadata(groupJid);
        if (metadata instanceof GroupMetadata groupMetadata && "parent_group".equals(unlinkType)) {
            groupMetadata.setParentCommunityJid(null);
        }
    }

    private void applyMembershipApproval(Node action, Jid groupJid) {
        var state = action.getChild("group_join")
                .flatMap(child -> child.getAttributeAsString("state"))
                .orElse(null);
        var enabled = "on".equals(state);
        var metadata = currentMetadata(groupJid);
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setMembershipApprovalMode(ChatPolicy.of(enabled));
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setMembershipApprovalMode(enabled);
        }
    }

    private void applyReportToAdmin(Jid groupJid, boolean value) {
        var metadata = currentMetadata(groupJid);
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setReportToAdminMode(value);
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setReportToAdminMode(value);
        }
    }

    private void applyNonAdminSubgroupCreation(Jid groupJid, boolean value) {
        var metadata = currentMetadata(groupJid);
        if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setAllowNonAdminSubGroupCreation(value);
        }
    }

    private void applyMemberAddMode(Jid groupJid, Node action) {
        var adminOnly = "admin_add".equals(action.toContentString().orElse(null));
        var metadata = currentMetadata(groupJid);
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setMemberAddMode(ChatPolicy.of(adminOnly));
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setMemberAddModeAdminOnly(adminOnly);
        }
    }

    private void applyGeneralChatAutoAddDisabled(Jid groupJid, boolean value) {
        var metadata = currentMetadata(groupJid);
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setGeneralChatAutoAddDisabled(value);
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setGeneralChatAutoAddDisabled(value);
        }
    }

    private void applyGroupSafetyCheck(Jid groupJid, boolean value) {
        var metadata = currentMetadata(groupJid);
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setGroupSafetyCheck(value);
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setGroupSafetyCheck(value);
        }
    }

    private void applySuspended(Chat chat, Jid groupJid, boolean value) {
        chat.setSuspended(value);
        var metadata = currentMetadata(groupJid);
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setSuspended(value);
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setSuspended(value);
        }
    }

    private void applyParticipants(Jid groupJid, Node action, GroupParticipantMutation mutation) {
        var metadata = currentMetadata(groupJid);
        if (metadata == null) {
            return;
        }

        for (var participant : parseParticipants(action, mutation)) {
            switch (mutation) {
                case ADD, MODIFY -> {
                    metadata.removeParticipant(participant.userJid());
                    metadata.addParticipant(participant);
                }
                case REMOVE -> metadata.removeParticipant(participant.userJid());
                case PROMOTE, DEMOTE -> {
                    metadata.removeParticipant(participant.userJid());
                    metadata.addParticipant(participant);
                }
            }
        }
    }

    private LinkedHashSet<GroupParticipant> parseParticipants(Node action, GroupParticipantMutation mutation) {
        var participants = new LinkedHashSet<GroupParticipant>();
        for (var participantNode : action.getChildren("participant")) {
            var jid = participantNode.getAttributeAsJid("jid").orElse(null);
            if (jid == null) {
                continue;
            }

            var role = switch (mutation) {
                case PROMOTE -> GroupPartipantRole.ADMIN;
                case DEMOTE -> GroupPartipantRole.USER;
                default -> parseRole(participantNode.getAttributeAsString("type", null));
            };

            participants.add(new GroupParticipantBuilder()
                    .userJid(jid.toUserJid())
                    .rank(role)
                    .build());
        }
        return participants;
    }

    private GroupPartipantRole parseRole(String type) {
        if (type == null || type.isBlank()) {
            return GroupPartipantRole.USER;
        }

        try {
            return GroupPartipantRole.of(type);
        } catch (RuntimeException exception) {
            return GroupPartipantRole.USER;
        }
    }

    private void collectRelatedGroups(Node action, LinkedHashSet<Jid> relatedGroups) {
        action.streamChildren("group")
                .map(child -> child.getAttributeAsJid("jid").orElse(null))
                .filter(jid -> jid != null && jid.hasGroupOrCommunityServer())
                .forEach(relatedGroups::add);

        action.streamChildren("sub_group_suggestion")
                .map(child -> child.getAttributeAsJid("jid").orElse(null))
                .filter(jid -> jid != null && jid.hasGroupOrCommunityServer())
                .forEach(relatedGroups::add);

        action.getAttributeAsJid("context_group_jid")
                .filter(Jid::hasGroupOrCommunityServer)
                .ifPresent(relatedGroups::add);
        action.getAttributeAsJid("parent_group_jid")
                .filter(Jid::hasGroupOrCommunityServer)
                .ifPresent(relatedGroups::add);
    }

    private ChatMetadata currentMetadata(Jid groupJid) {
        return whatsapp.store().findChatMetadata(groupJid).orElse(null);
    }

    private void setSubject(ChatMetadata metadata, String subject) {
        if (subject == null) {
            return;
        }

        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setSubject(subject);
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setSubject(subject);
        }
    }

    private void setSubjectTimestamp(ChatMetadata metadata, Instant timestamp, Jid author) {
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setSubjectTimestamp(timestamp);
            groupMetadata.setSubjectAuthorJid(author);
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setSubjectTimestamp(timestamp);
            communityMetadata.setSubjectAuthorJid(author);
        }
    }

    private void setDescription(ChatMetadata metadata, String description, String id, Instant timestamp, Jid author) {
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setDescription(description);
            groupMetadata.setDescriptionId(id);
            groupMetadata.setDescriptionTimestamp(timestamp);
            groupMetadata.setDescriptionAuthorJid(author);
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setDescription(description);
            communityMetadata.setDescriptionId(id);
            communityMetadata.setDescriptionTimestamp(timestamp);
            communityMetadata.setDescriptionAuthorJid(author);
        }
    }

    private String resolveDescriptionBody(Node node) {
        return node.getChild("body")
                .flatMap(Node::toContentString)
                .orElse(null);
    }

    private String resolveDescriptionId(Node node) {
        return node.getAttributeAsString("id", null);
    }

    private Instant resolveInstant(Node node, String... keys) {
        for (var key : keys) {
            var epoch = node.getAttributeAsLong(key, (Long) null);
            if (epoch != null && epoch > 0) {
                return Instant.ofEpochSecond(epoch);
            }
        }
        return null;
    }

    private void refreshGroup(Jid groupJid) {
        try {
            whatsapp.queryChatMetadata(groupJid);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Cannot refresh group metadata for {0}: {1}",
                    groupJid,
                    throwable.getMessage());
        }
    }

    private enum GroupParticipantMutation {
        ADD,
        REMOVE,
        PROMOTE,
        DEMOTE,
        MODIFY
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
