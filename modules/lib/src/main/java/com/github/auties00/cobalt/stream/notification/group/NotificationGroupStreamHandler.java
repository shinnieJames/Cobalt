package com.github.auties00.cobalt.stream.notification.group;

import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
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
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.GroupJoinCEventBuilder;

import java.time.Instant;
import java.util.LinkedHashSet;

/**
 * Handles {@code type="w:gp2"} group/community notifications by applying
 * each action child to the local chat and group metadata, then
 * triggering a full metadata refresh for every referenced group JID.
 *
 * @apiNote
 * Dispatched by {@code NotificationStreamHandler}. Each notification
 * targets exactly one group (named by {@code from}) and carries one or
 * more action children selected from WA Web's
 * {@code WAWebHandleGroupNotificationConst.GROUP_NOTIFICATION_TAG} set
 * ({@code create}, {@code add}, {@code remove}, {@code promote},
 * {@code demote}, {@code subject}, {@code description},
 * {@code ephemeral}, {@code locked}, {@code announcement}, link/unlink,
 * membership approval, etc.). Per WA Web, a {@code groups_dirty} child
 * short-circuits to a full metadata refresh and skips inline mutation.
 *
 * @implNote
 * This implementation follows a two-phase approach: every action child
 * is applied inline against the local chat and metadata, then a full
 * metadata refresh is fired for every group JID referenced by the
 * notification (including linked groups and subgroup suggestions). WA
 * Web routes each action through
 * {@code WAWebHandleGroupNotificationAction.handleAction} which
 * additionally synthesises system messages in the chat thread; Cobalt
 * relies on the post-loop refresh to reach the same final state and
 * lets the chat-message stream produce its own system messages.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleGroupNotification")
public final class NotificationGroupStreamHandler implements SocketStream.Handler {

    /**
     * Logger used for warnings about unhandled actions and debug
     * messages about deferred metadata-refresh actions.
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationGroupStreamHandler.class.getName());

    /**
     * The {@link WhatsAppClient} used for store reads and group
     * metadata queries.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The {@link WamService} used to commit the {@code GroupJoinC}
     * event when the user is added to a group they did not create.
     */
    private final WamService wamService;

    /**
     * The {@link AckSender} used to ship the post-processing
     * {@code <ack class="notification" type="w:gp2"/>} stanza,
     * echoing the original {@code participant} attribute back to the
     * server.
     */
    private final AckSender ackSender;

    /**
     * Constructs the handler with shared dependencies.
     *
     * @apiNote
     * Called once during
     * {@link SocketStream} setup;
     * embedders do not construct it directly.
     *
     * @param whatsapp   the {@link WhatsAppClient}
     * @param wamService the {@link WamService}
     * @param ackSender  the {@link AckSender}
     */
    public NotificationGroupStreamHandler(WhatsAppClient whatsapp, WamService wamService, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.wamService = wamService;
        this.ackSender = ackSender;
    }

    /**
     * Validates the stanza shape, delegates to
     * {@link #handleNotification(Node)}, and always sends the
     * protocol-level ACK.
     *
     * @apiNote
     * Invoked by the parent {@code NotificationStreamHandler}. Stanzas
     * whose description is not {@code notification} or whose type is
     * not {@code w:gp2} are silently dropped; valid stanzas always get
     * an ACK even when the handler throws.
     *
     * @param node the incoming stanza
     */
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

    /**
     * Resolves or creates the target chat, iterates every action child
     * inline, then refreshes metadata for every related group JID.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandleGroupNotification.x(notification)} which
     * short-circuits to
     * {@code handleGroupsDirtyNotificationJob} when the first child is
     * {@code groups_dirty}, then falls through to the parsed-action
     * dispatch loop.
     *
     * @implNote
     * This implementation bumps the chat's {@code conversationTimestamp}
     * and {@code lastMsgTimestamp} from the stanza's top-level {@code t}
     * attribute when present, matching WA Web's
     * {@code WAWebHandleGroupNotificationAction.handleAction} side
     * effect for actions that synthesise system messages.
     *
     * @param node the {@code <notification>} stanza
     */
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

    /**
     * Selects the per-action branch based on the action node's
     * description.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandleGroupNotificationConst.GROUP_NOTIFICATION_TAG}
     * tag set. Actions WA Web handles via synthesised system messages
     * ({@code invite}, {@code revoke}, membership requests, subgroup
     * suggestions, {@code change_number}, missing-participant) are
     * routed through a conservative metadata-refresh path here because
     * Cobalt's message generation runs from a different stream.
     *
     * @param notification  the parent {@code <notification>} stanza
     * @param chat          the local chat for the group
     * @param groupJid      the group JID
     * @param action        the action child being applied
     * @param relatedGroups the accumulator of related group JIDs to refresh post-loop
     */
    private void handleAction(Node notification, Chat chat, Jid groupJid, Node action, LinkedHashSet<Jid> relatedGroups) {
        collectRelatedGroups(action, relatedGroups);

        switch (action.description()) {
            case "create" -> applyCreate(notification, chat, groupJid, action);
            case "add" -> applyParticipants(groupJid, action, GroupParticipantMutation.ADD);
            case "remove" -> applyParticipants(groupJid, action, GroupParticipantMutation.REMOVE);
            case "promote", "linked_group_promote" -> applyParticipants(groupJid, action, GroupParticipantMutation.PROMOTE);
            case "demote", "linked_group_demote" -> applyParticipants(groupJid, action, GroupParticipantMutation.DEMOTE);
            case "modify" -> applyParticipants(groupJid, action, GroupParticipantMutation.MODIFY);
            case "subject" -> applySubject(notification, chat, groupJid, action);
            case "description" -> applyDescription(notification, chat, groupJid, action);
            case "locked" -> applyRestrict(groupJid, true);
            case "unlocked" -> applyRestrict(groupJid, false);
            case "announcement" -> applyAnnounce(groupJid, true);
            case "not_announcement" -> applyAnnounce(groupJid, false);
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
                 "revoke",
                 "membership_approval_request",
                 "reports",
                 "created_membership_requests",
                 "revoked_membership_requests",
                 "created_sub_group_suggestion",
                 "revoked_sub_group_suggestions",
                 "change_number",
                 "missing_participant_identification"
                 -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Handling w:gp2 action {0} conservatively via metadata refresh",
                    action.description());
            default -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unsupported w:gp2 action {0}", action.description());
        }
    }

    /**
     * Applies a {@code <create>} action by extracting every field from
     * the nested {@code <group>} element and writing it to both the
     * chat and the metadata.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandleGroupNotification.T(t, action, notificationAuthor)}
     * which builds the full {@code groupInfo} record from the
     * {@code <group>} child, including subject, description, creator,
     * boolean settings, ephemeral configuration, and the initial
     * participant list. Commits the {@code GroupJoinC} WAM event when
     * the local account did not author the create (i.e., the user was
     * added to a group they did not create).
     *
     * @implNote
     * This implementation handles both {@link GroupMetadata} and
     * {@link CommunityMetadata} variants for community-only fields
     * ({@code allow_non_admin_sub_group_creation},
     * {@code default_membership_approval_mode}) because Cobalt models
     * groups and communities as distinct metadata types whereas WA
     * Web shares one schema with discriminator fields.
     *
     * @param notification the parent {@code <notification>} stanza
     * @param chat         the local chat for the created group
     * @param groupJid     the group JID
     * @param action       the {@code <create>} action node
     */
    private void applyCreate(Node notification, Chat chat, Jid groupJid, Node action) {
        var groupNode = action.getChild("group").orElse(action);
        var notificationAuthor = notification.getAttributeAsJid("participant").orElse(null);
        var mePnUser = whatsapp.store().jid().orElse(null);
        if (notificationAuthor == null
                || mePnUser == null
                || !notificationAuthor.toUserJid().equals(mePnUser.toUserJid())) {
            wamService.commit(new GroupJoinCEventBuilder().build());
        }

        var subject = groupNode.getAttributeAsString("subject", null);
        if (subject != null) {
            chat.setName(subject);
        }

        var creation = groupNode.getAttributeAsLong("creation", (Long) null);
        chat.setCreatedAt(creation == null ? null : Instant.ofEpochSecond(creation));
        chat.setCreatedBy(groupNode.getAttributeAsString("creator", null));
        chat.setDescription(resolveCreateDescriptionBody(groupNode));
        chat.setSupport(groupNode.hasChild("support"));
        chat.setDefaultSubgroup(groupNode.hasChild("default_sub_group"));
        chat.setArchived(false);
        chat.setSuspended(groupNode.hasChild("suspended"));
        chat.setTerminated(false);

        applyEphemeral(notification, chat, groupJid, groupNode);

        var metadata = currentMetadata(groupJid);
        if (metadata != null) {
            metadata.clearParticipants();
            metadata.addAllParticipants(parseParticipants(groupNode, GroupParticipantMutation.ADD));

            setSubject(metadata, subject);

            var createDescBody = resolveCreateDescriptionBody(groupNode);
            var createDescId = resolveCreateDescriptionId(groupNode);
            setDescription(metadata, createDescBody, createDescId,
                    resolveInstant(groupNode, "t"),
                    groupNode.getAttributeAsJid("participant").orElse(null));

            applyRestrict(metadata, groupNode.hasChild("locked"));
            applyAnnounce(metadata, groupNode.hasChild("announcement"));
            applyNoFrequentlyForwarded(metadata, groupNode.hasChild("no_frequently_forwarded"));

            var approvalState = groupNode.getChild("membership_approval_mode")
                    .flatMap(mam -> mam.getChild("group_join"))
                    .flatMap(gj -> gj.getAttributeAsString("state"))
                    .orElse(null);
            applyMembershipApproval(metadata, "on".equals(approvalState));

            var memberAddModeStr = groupNode.getChild("member_add_mode")
                    .flatMap(Node::toContentString)
                    .orElse(null);
            applyMemberAddMode(metadata, "admin_add".equals(memberAddModeStr));

            var memberLinkModeContent = groupNode.getChild("member_link_mode")
                    .flatMap(Node::toContentString)
                    .orElse(null);
            applyMemberLinkMode(metadata, memberLinkModeContent);

            applyLimitSharingEnabled(metadata, groupNode.hasChild("limit_sharing_enabled"));

            applyGeneralChatAutoAddDisabled(metadata, action.hasChild("auto_add_disabled"));

            if (metadata instanceof GroupMetadata groupMetadata) {
                groupMetadata.setSupport(groupNode.hasChild("support"));
                groupMetadata.setDefaultSubgroup(groupNode.hasChild("default_sub_group"));
                groupMetadata.setGeneralSubgroup(groupNode.hasChild("general_chat"));
                groupMetadata.setHiddenSubgroup(groupNode.hasChild("hidden_group"));
                groupMetadata.setGroupSafetyCheck(groupNode.hasChild("group_safety_check"));
                groupMetadata.setHasCapi(groupNode.hasChild("capi"));
                var size = groupNode.getAttributeAsInt("size", (Integer) null);
                if (size != null) {
                    groupMetadata.setSize(size);
                }

                groupNode.getChild("linked_parent")
                        .flatMap(lp -> lp.getAttributeAsJid("jid"))
                        .ifPresent(groupMetadata::setParentCommunityJid);

                var groupAdder = notification.getAttributeAsJid("participant").orElse(null);
                if (groupAdder != null) {
                    groupMetadata.setGroupAdder(groupAdder.toUserJid());
                }
            } else if (metadata instanceof CommunityMetadata communityMetadata) {
                communityMetadata.setSupport(groupNode.hasChild("support"));
                communityMetadata.setDefaultSubgroup(groupNode.hasChild("default_sub_group"));
                communityMetadata.setGeneralSubgroup(groupNode.hasChild("general_chat"));
                communityMetadata.setHiddenSubgroup(groupNode.hasChild("hidden_group"));
                communityMetadata.setGroupSafetyCheck(groupNode.hasChild("group_safety_check"));
                communityMetadata.setHasCapi(groupNode.hasChild("capi"));
                var size = groupNode.getAttributeAsInt("size", (Integer) null);
                if (size != null) {
                    communityMetadata.setSize(size);
                }

                communityMetadata.setAllowNonAdminSubGroupCreation(
                        groupNode.hasChild("allow_non_admin_sub_group_creation"));

                var parentClosed = groupNode.getChild("parent")
                        .flatMap(parent -> parent.getAttributeAsString("default_membership_approval_mode"))
                        .map("request_required"::equals)
                        .orElse(false);
                communityMetadata.setParentGroupClosed(parentClosed);
            }
        }
    }

    /**
     * Applies a {@code memberLinkMode} content string ({@code "admin_link"}
     * or {@code "all_member_link"}) to the metadata.
     *
     * @apiNote
     * Internal helper called from {@link #applyCreate(Node, Chat, Jid, Node)}.
     * Unrecognised values leave the field unchanged.
     *
     * @param metadata the metadata being updated
     * @param content  the {@code <member_link_mode>} text content, or {@code null}
     */
    private void applyMemberLinkMode(ChatMetadata metadata, String content) {
        if (content == null) {
            return;
        }
        if (metadata instanceof GroupMetadata groupMetadata) {
            if ("admin_link".equals(content)) {
                groupMetadata.setMemberLinkMode(ChatPolicy.ADMINS);
            } else if ("all_member_link".equals(content)) {
                groupMetadata.setMemberLinkMode(ChatPolicy.ANYONE);
            }
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            if ("admin_link".equals(content)) {
                communityMetadata.setMemberLinkModeAdminOnly(true);
            } else if ("all_member_link".equals(content)) {
                communityMetadata.setMemberLinkModeAdminOnly(false);
            }
        }
    }

    /**
     * Writes the {@code limit_sharing_enabled} flag onto the metadata.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebLimitSharingGatingUtils.isOpusEnabled() ? false : d.hasChild("limit_sharing_enabled")}.
     * Cobalt does not gate the read on the Opus AB-prop because
     * Cobalt has no equivalent client-side kill switch.
     *
     * @param metadata the metadata being updated
     * @param value    whether link/media sharing is restricted to admins
     */
    private void applyLimitSharingEnabled(ChatMetadata metadata, boolean value) {
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setLimitSharingEnabled(value);
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setLimitSharingEnabled(value);
        }
    }

    /**
     * Writes the {@code generalChatAutoAddDisabled} flag directly onto
     * the metadata.
     *
     * @apiNote
     * Shared between the top-level {@code auto_add_disabled} action and
     * the create-flow read which sources the same value from the
     * notification (not the nested {@code <group>}).
     *
     * @param metadata the metadata being updated
     * @param value    whether auto-add to the general chat is disabled
     */
    private void applyGeneralChatAutoAddDisabled(ChatMetadata metadata, boolean value) {
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setGeneralChatAutoAddDisabled(value);
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setGeneralChatAutoAddDisabled(value);
        }
    }

    /**
     * Applies a {@code <subject>} action by writing the new name to both
     * the chat and the metadata along with the change timestamp and
     * author.
     *
     * @apiNote
     * Reads {@code s_t} (subject change time) and {@code s_o}
     * (subject owner) from the action node, falling back to the
     * notification's {@code participant} when {@code s_o} is absent.
     *
     * @param notification the parent {@code <notification>} stanza
     * @param chat         the local chat
     * @param groupJid     the group JID
     * @param action       the {@code <subject>} action node
     */
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

    /**
     * Applies a {@code <description>} action by writing the new body
     * and identifier to both the chat and the metadata; a nested
     * {@code <delete/>} clears the body while preserving the id.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebGroupType.GROUP_ACTIONS.DESC_ADD}/{@code DESC_REMOVE}
     * branches. The author is taken from the action's
     * {@code participant} attribute first, then the notification's
     * {@code participant} attribute.
     *
     * @param notification the parent {@code <notification>} stanza
     * @param chat         the local chat
     * @param groupJid     the group JID
     * @param action       the {@code <description>} action node
     */
    private void applyDescription(Node notification, Chat chat, Jid groupJid, Node action) {
        var deleted = action.hasChild("delete");
        var description = deleted ? null : resolveDescriptionBody(action);
        var descriptionId = resolveDescriptionId(action);
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

    /**
     * Applies the {@code <locked>}/{@code <unlocked>} toggle to the
     * metadata.
     *
     * @apiNote
     * Drives the "only admins can edit group info" affordance.
     *
     * @param groupJid   the group JID
     * @param restricted {@code true} for {@code <locked>}, {@code false} for {@code <unlocked>}
     */
    private void applyRestrict(Jid groupJid, boolean restricted) {
        var metadata = currentMetadata(groupJid);
        if (metadata != null) {
            applyRestrict(metadata, restricted);
        }
    }

    /**
     * Applies the {@code restrict} value directly to a metadata
     * instance.
     *
     * @apiNote
     * Internal helper shared by the per-JID
     * {@link #applyRestrict(Jid, boolean)} entry-point and the
     * create-flow which reads the value from the nested
     * {@code <group locked=.../>} child.
     *
     * @param metadata   the metadata being updated
     * @param restricted whether metadata editing is restricted to admins
     */
    private void applyRestrict(ChatMetadata metadata, boolean restricted) {
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setRestrict(ChatPolicy.of(restricted));
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setRestrict(restricted);
        }
    }

    /**
     * Applies the {@code <announcement>}/{@code <not_announcement>}
     * toggle to the metadata.
     *
     * @apiNote
     * Drives the "only admins can send messages" affordance.
     *
     * @param groupJid         the group JID
     * @param announcementOnly {@code true} for {@code <announcement>}, {@code false} for {@code <not_announcement>}
     */
    private void applyAnnounce(Jid groupJid, boolean announcementOnly) {
        var metadata = currentMetadata(groupJid);
        if (metadata != null) {
            applyAnnounce(metadata, announcementOnly);
        }
    }

    /**
     * Applies the {@code announce} value directly to a metadata
     * instance.
     *
     * @apiNote
     * Internal helper shared by the per-JID
     * {@link #applyAnnounce(Jid, boolean)} entry-point and the
     * create-flow.
     *
     * @param metadata         the metadata being updated
     * @param announcementOnly whether only admins can send messages
     */
    private void applyAnnounce(ChatMetadata metadata, boolean announcementOnly) {
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setAnnounce(ChatPolicy.of(announcementOnly));
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setAnnounce(announcementOnly);
        }
    }

    /**
     * Applies the {@code <no_frequently_forwarded>}/
     * {@code <frequently_forwarded_ok>} toggle to the metadata.
     *
     * @apiNote
     * Drives the "frequently forwarded messages are blocked" affordance.
     *
     * @param groupJid the group JID
     * @param value    whether frequently forwarded messages are blocked
     */
    private void applyNoFrequentlyForwarded(Jid groupJid, boolean value) {
        var metadata = currentMetadata(groupJid);
        if (metadata != null) {
            applyNoFrequentlyForwarded(metadata, value);
        }
    }

    /**
     * Applies the {@code noFrequentlyForwarded} value directly to a
     * metadata instance.
     *
     * @apiNote
     * Internal helper shared by the per-JID
     * {@link #applyNoFrequentlyForwarded(Jid, boolean)} entry-point and
     * the create-flow.
     *
     * @param metadata the metadata being updated
     * @param value    whether frequently forwarded messages are blocked
     */
    private void applyNoFrequentlyForwarded(ChatMetadata metadata, boolean value) {
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setNoFrequentlyForwarded(value);
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setNoFrequentlyForwarded(value);
        }
    }

    /**
     * Applies an {@code <ephemeral>} action by writing the expiration
     * duration and setting timestamp to both the chat and the metadata.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebGroupType.GROUP_ACTIONS.EPHEMERAL} which reads the
     * {@code expiration} attribute from either the action node or its
     * nested {@code <ephemeral>} child (the create flow uses the
     * nested form).
     *
     * @param notification the parent {@code <notification>} stanza or {@code <group>} container
     * @param chat         the local chat
     * @param groupJid     the group JID
     * @param action       the {@code <ephemeral>} action node or {@code <group>} container
     */
    private void applyEphemeral(Node notification, Chat chat, Jid groupJid, Node action) {
        var ephemeralNode = action.getChild("ephemeral").orElse(null);
        Node expirationSource;
        if (ephemeralNode != null) {
            expirationSource = ephemeralNode;
        } else {
            expirationSource = action;
        }

        var expiration = expirationSource.getAttributeAsInt("expiration", (Integer) null);
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

    /**
     * Applies a {@code <not_ephemeral>} action by clearing the ephemeral
     * timer on both the chat and the metadata.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebGroupType.GROUP_ACTIONS.EPHEMERAL} with
     * {@code duration: 0}.
     *
     * @param chat     the local chat
     * @param groupJid the group JID
     */
    private void clearEphemeral(Chat chat, Jid groupJid) {
        chat.setEphemeralExpiration(null);
        var metadata = currentMetadata(groupJid);
        if (metadata != null) {
            metadata.setEphemeralExpiration(null);
        }
    }

    /**
     * Applies a {@code <growth_locked>} action by writing the
     * expiration timestamp and lock type onto the metadata.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebGroupType.GROUP_ACTIONS.GROWTH_LOCKED} which gates
     * new members joining the group until the lock expires.
     *
     * @param groupJid the group JID
     * @param action   the {@code <growth_locked>} action node
     */
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

    /**
     * Applies a {@code <growth_unlocked>} action by clearing the growth
     * lock fields on the metadata.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebGroupType.GROUP_ACTIONS.GROWTH_UNLOCKED}.
     *
     * @param groupJid the group JID
     */
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

    /**
     * Applies a {@code <link link_type="parent_group">} action by
     * writing the parent community JID onto the group metadata.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebGroupType.GROUP_ACTIONS.PARENT_GROUP_LINK} branch.
     * Other link types ({@code sub_group}, {@code sibling_group})
     * have no direct field on Cobalt's metadata and are reconciled
     * by the post-loop metadata refresh.
     *
     * @param groupJid the group JID
     * @param action   the {@code <link>} action node
     */
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

    /**
     * Applies an {@code <unlink unlink_type="parent_group">} action by
     * clearing the parent community JID on the group metadata.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebGroupType.GROUP_ACTIONS.PARENT_GROUP_UNLINK} branch.
     *
     * @param groupJid the group JID
     * @param action   the {@code <unlink>} action node
     */
    private void applyUnlink(Jid groupJid, Node action) {
        var unlinkType = action.getAttributeAsString("unlink_type", null);
        var metadata = currentMetadata(groupJid);
        if (metadata instanceof GroupMetadata groupMetadata && "parent_group".equals(unlinkType)) {
            groupMetadata.setParentCommunityJid(null);
        }
    }

    /**
     * Applies a {@code <membership_approval_mode>} action by reading
     * the {@code state} attribute of the nested {@code <group_join>}
     * child.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebGroupType.GROUP_ACTIONS.MEMBERSHIP_APPROVAL_MODE}
     * branch.
     *
     * @param action   the {@code <membership_approval_mode>} action node
     * @param groupJid the group JID
     */
    private void applyMembershipApproval(Node action, Jid groupJid) {
        var state = action.getChild("group_join")
                .flatMap(child -> child.getAttributeAsString("state"))
                .orElse(null);
        var enabled = "on".equals(state);
        var metadata = currentMetadata(groupJid);
        if (metadata != null) {
            applyMembershipApproval(metadata, enabled);
        }
    }

    /**
     * Applies the membership-approval-mode value directly to a metadata
     * instance.
     *
     * @apiNote
     * Internal helper shared by the per-JID
     * {@link #applyMembershipApproval(Node, Jid)} entry-point and the
     * create-flow.
     *
     * @param metadata the metadata being updated
     * @param enabled  whether membership approval is required
     */
    private void applyMembershipApproval(ChatMetadata metadata, boolean enabled) {
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setMembershipApprovalMode(ChatPolicy.of(enabled));
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setMembershipApprovalMode(enabled);
        }
    }

    /**
     * Applies the {@code <allow_admin_reports>}/
     * {@code <not_allow_admin_reports>} toggle to the metadata.
     *
     * @apiNote
     * Drives the report-to-admin feature for groups.
     *
     * @param groupJid the group JID
     * @param value    whether reporting to admin is enabled
     */
    private void applyReportToAdmin(Jid groupJid, boolean value) {
        var metadata = currentMetadata(groupJid);
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setReportToAdminMode(value);
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setReportToAdminMode(value);
        }
    }

    /**
     * Applies the {@code <allow_non_admin_sub_group_creation>}/
     * {@code <not_allow_non_admin_sub_group_creation>} toggle on a
     * community metadata; ignored on plain group metadata.
     *
     * @apiNote
     * Community-only action. Plain groups have no sub-group concept.
     *
     * @param groupJid the community JID
     * @param value    whether non-admin sub-group creation is allowed
     */
    private void applyNonAdminSubgroupCreation(Jid groupJid, boolean value) {
        var metadata = currentMetadata(groupJid);
        if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setAllowNonAdminSubGroupCreation(value);
        }
    }

    /**
     * Applies a {@code <member_add_mode>} action by reading the text
     * content and checking for {@code "admin_add"}.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebGroupType.GROUP_ACTIONS.MEMBER_ADD_MODE} branch.
     *
     * @param groupJid the group JID
     * @param action   the {@code <member_add_mode>} action node
     */
    private void applyMemberAddMode(Jid groupJid, Node action) {
        var adminOnly = "admin_add".equals(action.toContentString().orElse(null));
        var metadata = currentMetadata(groupJid);
        if (metadata != null) {
            applyMemberAddMode(metadata, adminOnly);
        }
    }

    /**
     * Applies the member-add-mode value directly to a metadata
     * instance.
     *
     * @apiNote
     * Internal helper shared by the per-JID
     * {@link #applyMemberAddMode(Jid, Node)} entry-point and the
     * create-flow.
     *
     * @param metadata  the metadata being updated
     * @param adminOnly whether only admins can add members
     */
    private void applyMemberAddMode(ChatMetadata metadata, boolean adminOnly) {
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setMemberAddMode(ChatPolicy.of(adminOnly));
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setMemberAddModeAdminOnly(adminOnly);
        }
    }

    /**
     * Applies the {@code <auto_add_disabled>} action by writing the
     * {@code generalChatAutoAddDisabled} flag.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebGroupType.GROUP_ACTIONS.GENERAL_CHAT_AUTO_ADD_DISABLED}.
     *
     * @param groupJid the group JID
     * @param value    whether auto-add to the general chat is disabled
     */
    private void applyGeneralChatAutoAddDisabled(Jid groupJid, boolean value) {
        var metadata = currentMetadata(groupJid);
        if (metadata != null) {
            applyGeneralChatAutoAddDisabled(metadata, value);
        }
    }

    /**
     * Applies the {@code <group_safety_check>} action by writing the
     * safety-check flag on the metadata.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebGroupType.GROUP_ACTIONS.GROUP_SAFETY_CHECK}.
     *
     * @param groupJid the group JID
     * @param value    whether the group safety check is set
     */
    private void applyGroupSafetyCheck(Jid groupJid, boolean value) {
        var metadata = currentMetadata(groupJid);
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setGroupSafetyCheck(value);
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setGroupSafetyCheck(value);
        }
    }

    /**
     * Applies the {@code <suspended>}/{@code <unsuspended>} toggle by
     * updating both the chat's suspended flag and the metadata.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebGroupType.GROUP_ACTIONS.SUSPEND}.
     *
     * @param chat     the local chat
     * @param groupJid the group JID
     * @param value    whether the group is suspended
     */
    private void applySuspended(Chat chat, Jid groupJid, boolean value) {
        chat.setSuspended(value);
        var metadata = currentMetadata(groupJid);
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setSuspended(value);
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setSuspended(value);
        }
    }

    /**
     * Parses {@code <participant>} children and applies the requested
     * mutation to the metadata's participant set.
     *
     * @apiNote
     * The mutation determines whether the participant is added, removed,
     * or rebuilt with a new role; for {@code ADD}, {@code MODIFY},
     * {@code PROMOTE}, and {@code DEMOTE} the participant is first
     * removed (to clear the old role) before being re-added.
     *
     * @param groupJid the group JID
     * @param action   the action node carrying {@code <participant>} children
     * @param mutation the mutation type
     */
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

    /**
     * Parses {@code <participant>} children into a set of
     * {@link GroupParticipant} records with roles derived from the
     * mutation and the {@code type} attribute.
     *
     * @apiNote
     * Mirrors WA Web's {@code y(jid, action, tag)} helper.
     * {@code PROMOTE} always yields {@link GroupPartipantRole#ADMIN};
     * {@code DEMOTE} always yields {@link GroupPartipantRole#USER};
     * other mutations derive the role from the participant's
     * {@code type} attribute.
     *
     * @param action   the action node carrying participant children
     * @param mutation the mutation type
     * @return an insertion-ordered set of parsed participants
     */
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

    /**
     * Parses a participant {@code type} string into a
     * {@link GroupPartipantRole}, defaulting to
     * {@link GroupPartipantRole#USER} for unknown or absent values.
     *
     * @apiNote
     * Mirrors WA Web's enum fallback inside
     * {@code y(jid, action, tag)} which defaults the role to
     * {@code "participant"} when the attribute is missing.
     *
     * @param type the {@code type} attribute value, or {@code null}
     * @return the resolved role
     */
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

    /**
     * Collects every group/community JID referenced by the action
     * (inline {@code <group>}/{@code <sub_group_suggestion>} children
     * and {@code context_group_jid}/{@code parent_group_jid} attributes)
     * into the related-groups accumulator.
     *
     * @apiNote
     * The accumulated set drives the post-loop metadata refresh so
     * cross-group actions (links, subgroup suggestions, parent-group
     * changes) reach a consistent final state.
     *
     * @param action        the action node being scanned
     * @param relatedGroups the accumulator set
     */
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

    /**
     * Returns the current metadata record for the group, or {@code null}
     * when not in the store.
     *
     * @apiNote
     * Internal helper used by every action branch. WA Web's
     * {@code WAWebGroupMetadataCollection.get} provides the equivalent
     * lookup with eager population semantics; Cobalt's metadata is
     * populated lazily so the caller may receive {@code null} on the
     * first reference to a freshly observed group.
     *
     * @param groupJid the group JID
     * @return the matching metadata, or {@code null}
     */
    private ChatMetadata currentMetadata(Jid groupJid) {
        return whatsapp.store().findChatMetadata(groupJid).orElse(null);
    }

    /**
     * Writes a new subject onto the metadata when the value is non-null.
     *
     * @apiNote
     * Internal helper shared by {@link #applySubject(Node, Chat, Jid, Node)}
     * and the create-flow.
     *
     * @param metadata the metadata being updated
     * @param subject  the new subject, or {@code null} to skip
     */
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

    /**
     * Writes the subject change timestamp and author onto the metadata.
     *
     * @apiNote
     * Internal helper shared by {@link #applySubject(Node, Chat, Jid, Node)}
     * with the resolved {@code s_t}/{@code s_o} pair.
     *
     * @param metadata  the metadata being updated
     * @param timestamp the subject change time, or {@code null}
     * @param author    the subject author JID, or {@code null}
     */
    private void setSubjectTimestamp(ChatMetadata metadata, Instant timestamp, Jid author) {
        if (metadata instanceof GroupMetadata groupMetadata) {
            groupMetadata.setSubjectTimestamp(timestamp);
            groupMetadata.setSubjectAuthorJid(author);
        } else if (metadata instanceof CommunityMetadata communityMetadata) {
            communityMetadata.setSubjectTimestamp(timestamp);
            communityMetadata.setSubjectAuthorJid(author);
        }
    }

    /**
     * Writes the description, description id, timestamp, and author
     * onto the metadata.
     *
     * @apiNote
     * Internal helper shared by {@link #applyDescription(Node, Chat, Jid, Node)}
     * and the create-flow.
     *
     * @param metadata    the metadata being updated
     * @param description the description body, or {@code null} when cleared
     * @param id          the server-issued description revision id
     * @param timestamp   the description change time
     * @param author      the description author JID
     */
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

    /**
     * Extracts the description body from the create-flow nested path
     * {@code description > body}.
     *
     * @apiNote
     * Internal helper used by {@link #applyCreate(Node, Chat, Jid, Node)};
     * mirrors WA Web's {@code g()} extractor.
     *
     * @param groupNode the {@code <group>} element from the create stanza
     * @return the description body, or {@code null}
     */
    private String resolveCreateDescriptionBody(Node groupNode) {
        return groupNode.getChild("description")
                .flatMap(desc -> desc.getChild("body"))
                .flatMap(Node::toContentString)
                .orElse(null);
    }

    /**
     * Extracts the description id from the create-flow
     * {@code <description id=.../>} attribute.
     *
     * @apiNote
     * Internal helper used by {@link #applyCreate(Node, Chat, Jid, Node)};
     * mirrors WA Web's {@code g()} extractor.
     *
     * @param groupNode the {@code <group>} element from the create stanza
     * @return the description id, or {@code null}
     */
    private String resolveCreateDescriptionId(Node groupNode) {
        return groupNode.getChild("description")
                .flatMap(desc -> desc.getAttributeAsString("id"))
                .orElse(null);
    }

    /**
     * Extracts the description body from a non-create
     * {@code <description><body>...</body></description>} action node.
     *
     * @apiNote
     * Internal helper used by {@link #applyDescription(Node, Chat, Jid, Node)}.
     *
     * @param node the description action node
     * @return the body text, or {@code null}
     */
    private String resolveDescriptionBody(Node node) {
        return node.getChild("body")
                .flatMap(Node::toContentString)
                .orElse(null);
    }

    /**
     * Extracts the description id from a {@code <description id=.../>}
     * action node.
     *
     * @apiNote
     * Internal helper used by {@link #applyDescription(Node, Chat, Jid, Node)}.
     *
     * @param node the description action node
     * @return the description id, or {@code null}
     */
    private String resolveDescriptionId(Node node) {
        return node.getAttributeAsString("id", null);
    }

    /**
     * Returns the first positive epoch-seconds attribute as an
     * {@link Instant}, walking the supplied attribute keys in order.
     *
     * @apiNote
     * Internal helper used by every branch that needs a timestamp.
     * Mirrors WA Web's
     * {@code attrTime}-with-fallback usage where some actions name
     * the timestamp {@code t} and others {@code s_t} or
     * {@code expiration}.
     *
     * @param node the node to read from
     * @param keys the attribute keys to try in order
     * @return the resolved {@link Instant}, or {@code null}
     */
    private Instant resolveInstant(Node node, String... keys) {
        for (var key : keys) {
            var epoch = node.getAttributeAsLong(key, (Long) null);
            if (epoch != null && epoch > 0) {
                return Instant.ofEpochSecond(epoch);
            }
        }
        return null;
    }

    /**
     * Re-queries metadata for the given group from the server.
     *
     * @apiNote
     * Used as the post-loop reconciler for every related group JID;
     * failures are debug-logged and suppressed so a transient
     * network error does not abort the whole notification.
     *
     * @param groupJid the group JID to refresh
     */
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

    /**
     * Enumerates the participant mutation kinds applied by
     * {@link #applyParticipants(Jid, Node, GroupParticipantMutation)}.
     *
     * @apiNote
     * The values correspond one-to-one with the WA Web action tags
     * {@code add}, {@code remove}, {@code promote}, {@code demote},
     * and {@code modify}.
     */
    private enum GroupParticipantMutation {
        /**
         * The participant was added to the group.
         *
         * @apiNote
         * The participant's role is derived from the
         * {@code <participant type="..."/>} attribute.
         */
        ADD,
        /**
         * The participant was removed from the group.
         *
         * @apiNote
         * Only the JID is read; the role is ignored because the
         * record is being deleted.
         */
        REMOVE,
        /**
         * The participant was promoted to admin.
         *
         * @apiNote
         * Forces the role to {@link GroupPartipantRole#ADMIN}
         * regardless of the {@code type} attribute.
         */
        PROMOTE,
        /**
         * The participant was demoted from admin to plain member.
         *
         * @apiNote
         * Forces the role to {@link GroupPartipantRole#USER}
         * regardless of the {@code type} attribute.
         */
        DEMOTE,
        /**
         * The participant's metadata changed without role change.
         *
         * @apiNote
         * The role is derived from the {@code type} attribute just
         * like {@link #ADD}.
         */
        MODIFY
    }

    /**
     * Sends the {@code <ack class="notification" type="w:gp2"/>} stanza
     * for the processed notification.
     *
     * @apiNote
     * Fire-and-forget; identical attribute set to WA Web's
     * {@code WAWebHandleGroupNotification} ack-builder which mirrors
     * the original stanza's {@code participant} attribute back so the
     * server can attribute the ACK to the correct action author.
     *
     * @param node the original {@code <notification>} stanza
     */
    private void sendNotificationAck(Node node) {
        ackSender.ack(AckClass.NOTIFICATION, node)
                .type("w:gp2")
                .participant(node.getAttributeAsJid("participant").orElse(null))
                .send();
    }
}
