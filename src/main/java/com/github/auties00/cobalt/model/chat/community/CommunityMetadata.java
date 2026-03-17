package com.github.auties00.cobalt.model.chat.community;

import com.github.auties00.cobalt.model.chat.ChatDisappearingMode;
import com.github.auties00.cobalt.model.chat.ChatEphemeralTimer;
import com.github.auties00.cobalt.model.chat.ChatMetadata;
import com.github.auties00.cobalt.model.chat.group.GroupParticipant;
import com.github.auties00.cobalt.model.chat.ChatPolicy;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.*;

/**
 * The metadata of a WhatsApp community (parent group).
 *
 * <p>A community is a top-level organizational entity that aggregates one or
 * more subgroups under a single umbrella. In the WhatsApp Web client the same
 * database table ({@code WAWebDBGroupsGroupMetadata}) stores both regular
 * group metadata and community metadata, differentiated by the
 * {@code isParentGroup} flag. When {@code isParentGroup} is {@code true} the
 * record describes a community and its metadata is represented by this class.
 *
 * <p>Community metadata captures the identity of the community (its JID and
 * subject), its origin (the founder and foundation timestamp), an optional
 * free-form description with a server-assigned revision identifier, the
 * current list of participants, the community-level administrative settings
 * as direct boolean properties, the ephemeral message expiration timer, the
 * set of linked subgroups, addressing-mode flags, and the open-bot-group
 * toggle.
 *
 * <p>Instances of this class are mutable. All fields can be changed after
 * construction through the fluent setter methods, each of which returns the
 * same instance for method chaining. Collection-typed fields (participants
 * and community groups) additionally expose dedicated add, remove, and clear
 * operations.
 *
 * @apiNote In the WhatsApp Web client the {@code WAWebGroupMetadataModel}
 * derives the community's {@code groupType} from the
 * {@code isParentGroup === true} condition. The
 * {@code WAWebGroupType.GroupType.COMMUNITY} constant identifies this case.
 * The community's subgroups can have their own type: a
 * {@code LINKED_ANNOUNCEMENT_GROUP} (the default subgroup, where
 * {@code defaultSubgroup === true}), a {@code LINKED_GENERAL_GROUP} (the
 * general chat subgroup, where {@code generalSubgroup === true}), or a plain
 * {@code LINKED_SUBGROUP} identified by a non-{@code null}
 * {@code parentGroup} reference.
 *
 * @see ChatMetadata
 * @see CommunityLinkedGroup
 */
@ProtobufMessage
public final class CommunityMetadata implements ChatMetadata {
    /**
     * The JID that uniquely identifies this community. In the WhatsApp Web
     * client this corresponds to the {@code id} property of the
     * {@code WAWebGroupMetadataModel}.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid jid;

    /**
     * The subject (display name) of this community. In the WhatsApp Web
     * client this is the {@code subject} property of the group metadata
     * record, set by a community administrator and visible to all
     * participants.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String subject;

    /**
     * The JID of the participant who last changed the subject, or
     * {@code null} if the author is not known.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    Jid subjectAuthorJid;

    /**
     * The instant at which the subject was last changed, or {@code null}
     * if the timestamp is not available. In the WhatsApp Web client this
     * corresponds to the {@code subjectTime} property.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant subjectTimestamp;

    /**
     * The instant at which this community was created, or {@code null} if
     * the timestamp is not available. In the WhatsApp Web client this
     * corresponds to the {@code creation} property.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant foundationTimestamp;

    /**
     * The JID of the user who originally created this community, or
     * {@code null} if the founder is not known. In the WhatsApp Web client
     * this corresponds to the {@code owner} property.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.STRING)
    Jid founderJid;

    /**
     * The free-form description text of this community, or {@code null} if
     * no description has been set. In the WhatsApp Web client this
     * corresponds to the {@code desc} property.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.STRING)
    String description;

    /**
     * The server-assigned identifier for the current description revision,
     * or {@code null} if no description identifier is available. In the
     * WhatsApp Web client this corresponds to the {@code descId} property
     * and is used to detect conflicting concurrent edits.
     */
    @ProtobufProperty(index = 8, type = ProtobufType.STRING)
    String descriptionId;

    /**
     * The instant at which the description was last changed, or
     * {@code null} if the timestamp is not available. In the WhatsApp Web
     * client this corresponds to the {@code descTime} property.
     */
    @ProtobufProperty(index = 9, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant descriptionTimestamp;

    /**
     * The ordered set of participants currently in this community. In the
     * WhatsApp Web client this corresponds to the {@code participants}
     * collection on the {@code WAWebGroupMetadataModel}.
     */
    @ProtobufProperty(index = 10, type = ProtobufType.MESSAGE)
    SequencedSet<GroupParticipant> participants;

    /**
     * The JID of the participant who last changed the description, or
     * {@code null} if not known. In the WhatsApp Web client this
     * corresponds to the {@code descOwner} property.
     */
    @ProtobufProperty(index = 11, type = ProtobufType.STRING)
    Jid descriptionAuthorJid;

    /**
     * The number of seconds after which messages in this community
     * automatically disappear, or {@code 0} if ephemeral messaging is
     * disabled. In the WhatsApp Web client this corresponds to the
     * {@code ephemeralDuration} property.
     */
    @ProtobufProperty(index = 12, type = ProtobufType.UINT32)
    ChatEphemeralTimer ephemeralExpiration;

    /**
     * Whether metadata editing is restricted to administrators only. When
     * {@code true}, only administrators can change the community's subject,
     * description, and profile picture. In the WhatsApp Web client this
     * corresponds to the {@code restrict} property (locked/unlocked).
     */
    @ProtobufProperty(index = 13, type = ProtobufType.BOOL)
    boolean restrict;

    /**
     * Whether the community is in announcement mode. When {@code true},
     * only administrators can send messages. In the WhatsApp Web client this
     * corresponds to the {@code announce} property.
     */
    @ProtobufProperty(index = 14, type = ProtobufType.BOOL)
    boolean announce;

    /**
     * The ordered set of subgroups linked to this community. In the
     * WhatsApp Web client the community's subgroups are tracked by
     * {@code joinedSubgroups} and {@code unjoinedSubgroups} arrays.
     */
    @ProtobufProperty(index = 15, type = ProtobufType.MESSAGE)
    SequencedSet<CommunityLinkedGroup> communityGroups;

    /**
     * Whether this community uses LID (Linked Identity) addressing mode
     * instead of traditional phone-number-based addressing. In the WhatsApp
     * Web client this corresponds to the {@code isLidAddressingMode}
     * property.
     */
    @ProtobufProperty(index = 16, type = ProtobufType.BOOL)
    boolean isLidAddressingMode;

    /**
     * Whether this community operates in incognito mode. In the WhatsApp
     * Web client this corresponds to the {@code incognito} column.
     */
    @ProtobufProperty(index = 17, type = ProtobufType.BOOL)
    boolean isIncognito;

    /**
     * Whether frequently forwarded messages are blocked in this community.
     * In the WhatsApp Web client this corresponds to the
     * {@code noFrequentlyForwarded} property.
     */
    @ProtobufProperty(index = 18, type = ProtobufType.BOOL)
    boolean noFrequentlyForwarded;

    /**
     * Whether admin approval is required for new members to join this
     * community. In the WhatsApp Web client this corresponds to the
     * {@code membershipApprovalMode} property.
     */
    @ProtobufProperty(index = 19, type = ProtobufType.BOOL)
    boolean membershipApprovalMode;

    /**
     * Whether invite link usage is restricted to administrators. When
     * {@code true}, only administrators can use invite links. In the
     * WhatsApp Web client this corresponds to the {@code memberLinkMode}
     * property with values {@code "admin_link"} or {@code "all_member_link"}.
     */
    @ProtobufProperty(index = 20, type = ProtobufType.BOOL)
    boolean memberLinkModeAdminOnly;

    /**
     * Whether non-admin members are allowed to create or link subgroups.
     * In the WhatsApp Web client this corresponds to the
     * {@code allowNonAdminSubGroupCreation} flag on the group metadata
     * record.
     */
    @ProtobufProperty(index = 21, type = ProtobufType.BOOL)
    boolean allowNonAdminSubGroupCreation;

    /**
     * Whether only administrators can add members to this community. When
     * {@code true}, adding members is restricted to administrators. In the
     * WhatsApp Web client this corresponds to the {@code memberAddMode}
     * field with the value {@code "admin_add"}.
     */
    @ProtobufProperty(index = 22, type = ProtobufType.BOOL)
    boolean memberAddModeAdminOnly;

    /**
     * The instant at which the growth lock expires, or {@code null} if no
     * growth lock is active. In the WhatsApp Web client this corresponds
     * to the {@code growthLockExpiration} property.
     */
    @ProtobufProperty(index = 23, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant growthLockExpiration;

    /**
     * The type of growth lock applied to this community (e.g.
     * {@code "invite"}), or {@code null} if no growth lock is active. In
     * the WhatsApp Web client this corresponds to the
     * {@code growthLockType} property.
     */
    @ProtobufProperty(index = 24, type = ProtobufType.STRING)
    String growthLockType;

    /**
     * Whether the "report to admin" feature is enabled for this community.
     * In the WhatsApp Web client this corresponds to the
     * {@code reportToAdminMode} property.
     */
    @ProtobufProperty(index = 25, type = ProtobufType.BOOL)
    boolean reportToAdminMode;

    /**
     * The instant of the last report-to-admin event, or {@code null} if no
     * such event has occurred. In the WhatsApp Web client this corresponds
     * to the {@code lastReportToAdminTimestamp} property.
     */
    @ProtobufProperty(index = 26, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant lastReportToAdminTimestamp;

    /**
     * The server-reported participant count, or {@code null} if not
     * available. In the WhatsApp Web client this corresponds to the
     * {@code size} property.
     */
    @ProtobufProperty(index = 27, type = ProtobufType.UINT32)
    Integer size;

    /**
     * Whether this is a support group. In the WhatsApp Web client this
     * corresponds to the {@code support} property.
     */
    @ProtobufProperty(index = 28, type = ProtobufType.BOOL)
    boolean support;

    /**
     * Whether this community has been suspended. A suspended community
     * cannot be interacted with until it is restored. In the WhatsApp Web
     * client this corresponds to the {@code suspended} property.
     */
    @ProtobufProperty(index = 29, type = ProtobufType.BOOL)
    boolean suspended;

    /**
     * Whether this community has been terminated. In the WhatsApp Web
     * client this corresponds to the {@code terminated} property.
     */
    @ProtobufProperty(index = 30, type = ProtobufType.BOOL)
    boolean terminated;

    /**
     * Whether the parent group is closed, meaning the default membership
     * approval is {@code "request_required"}. In the WhatsApp Web client
     * this corresponds to the {@code isParentGroupClosed} property.
     */
    @ProtobufProperty(index = 31, type = ProtobufType.BOOL)
    boolean isParentGroupClosed;

    /**
     * Whether this community has or is a default (announcement) subgroup.
     * In the WhatsApp Web client this corresponds to the
     * {@code defaultSubgroup} property.
     */
    @ProtobufProperty(index = 32, type = ProtobufType.BOOL)
    boolean defaultSubgroup;

    /**
     * Whether this community has or is a general chat subgroup. In the
     * WhatsApp Web client this corresponds to the {@code generalSubgroup}
     * property.
     */
    @ProtobufProperty(index = 33, type = ProtobufType.BOOL)
    boolean generalSubgroup;

    /**
     * Whether this community has a hidden subgroup. In the WhatsApp Web
     * client this corresponds to the {@code hiddenSubgroup} property.
     */
    @ProtobufProperty(index = 34, type = ProtobufType.BOOL)
    boolean hiddenSubgroup;

    /**
     * Whether the group safety check flag is set. In the WhatsApp Web
     * client this corresponds to the {@code groupSafetyCheck} property.
     */
    @ProtobufProperty(index = 35, type = ProtobufType.BOOL)
    boolean groupSafetyCheck;

    /**
     * The JID of the user who added the current user to this community, or
     * {@code null} if not known. In the WhatsApp Web client this
     * corresponds to the {@code groupAdder} property.
     */
    @ProtobufProperty(index = 36, type = ProtobufType.STRING)
    Jid groupAdder;

    /**
     * Whether automatic addition to the general chat is disabled. In the
     * WhatsApp Web client this corresponds to the
     * {@code generalChatAutoAddDisabled} property.
     */
    @ProtobufProperty(index = 37, type = ProtobufType.BOOL)
    boolean generalChatAutoAddDisabled;

    /**
     * The instant of the last community poll, or {@code null} if no poll
     * has occurred. In the WhatsApp Web client this corresponds to the
     * {@code lastCommunityPollTimestamp} property.
     */
    @ProtobufProperty(index = 38, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant lastCommunityPollTimestamp;

    /**
     * The instant of the last activity in this community, or {@code null}
     * if not available. In the WhatsApp Web client this corresponds to the
     * {@code lastActivityTimestamp} property.
     */
    @ProtobufProperty(index = 39, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant lastActivityTimestamp;

    /**
     * The instant of the last seen activity in this community, or
     * {@code null} if not available. In the WhatsApp Web client this
     * corresponds to the {@code lastSeenActivityTimestamp} property.
     */
    @ProtobufProperty(index = 40, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant lastSeenActivityTimestamp;

    /**
     * Whether this community has CAPI (Community API) capabilities. In the
     * WhatsApp Web client this corresponds to the {@code hasCapi} property.
     */
    @ProtobufProperty(index = 41, type = ProtobufType.BOOL)
    boolean hasCapi;

    /**
     * Whether this community is a TEE bot group. In the WhatsApp Web client
     * this corresponds to the {@code isTeeBotGroup} property.
     */
    @ProtobufProperty(index = 42, type = ProtobufType.BOOL)
    boolean isTeeBotGroup;

    /**
     * The trigger that caused the disappearing message mode to be set, or
     * {@code null} if not set. In the WhatsApp Web client this corresponds
     * to the {@code disappearingModeTrigger} property.
     */
    @ProtobufProperty(index = 43, type = ProtobufType.ENUM)
    ChatDisappearingMode.Trigger disappearingModeTrigger;

    /**
     * Whether the current user initiated the disappearing message mode.
     * In the WhatsApp Web client this corresponds to the
     * {@code disappearingModeInitiatedByMe} property.
     */
    @ProtobufProperty(index = 44, type = ProtobufType.BOOL)
    boolean disappearingModeInitiatedByMe;

    /**
     * The number of subgroups in this community, or {@code null} if not
     * available. In the WhatsApp Web client this corresponds to the
     * {@code numSubgroups} property on the model.
     */
    @ProtobufProperty(index = 45, type = ProtobufType.UINT32)
    Integer numSubgroups;

    /**
     * Whether the limit sharing feature is enabled for this community. In
     * the WhatsApp Web client this corresponds to the
     * {@code limitSharingEnabled} property.
     */
    @ProtobufProperty(index = 46, type = ProtobufType.BOOL)
    boolean limitSharingEnabled;

    /**
     * The group evolution version, or {@code null} if not available. In
     * the WhatsApp Web client this corresponds to the
     * {@code evolutionVersion} property.
     */
    @ProtobufProperty(index = 47, type = ProtobufType.UINT32)
    Integer evolutionVersion;

    /**
     * Whether participant labels are enabled for this community. In the
     * WhatsApp Web client this corresponds to the
     * {@code participantLabelEnabled} property.
     */
    @ProtobufProperty(index = 48, type = ProtobufType.BOOL)
    boolean participantLabelEnabled;

    /**
     * Whether this community has the open Meta AI bot feature enabled. This
     * field is not serialized as a protobuf property and is instead
     * populated programmatically from the group query response when bot
     * participants are detected.
     */
    boolean isOpenBotGroup;

    /**
     * Constructs a new {@code CommunityMetadata} with the specified values.
     *
     * <p>The {@code jid} and {@code subject} parameters are required and
     * must not be {@code null}. All other parameters accept {@code null} to
     * indicate an absent or unknown value. Collection-typed parameters that
     * are {@code null} are replaced with empty mutable collections so that
     * callers can safely add elements after construction.
     *
     * @param jid                          the non-{@code null} community JID
     * @param subject                      the non-{@code null} subject
     * @param subjectAuthorJid             the author of the last subject
     *                                     change, or {@code null}
     * @param subjectTimestamp              the subject change instant, or
     *                                     {@code null}
     * @param foundationTimestamp           the community creation instant, or
     *                                     {@code null}
     * @param founderJid                   the founder JID, or {@code null}
     * @param description                  the description text, or
     *                                     {@code null}
     * @param descriptionId                the server-assigned description
     *                                     revision identifier, or
     *                                     {@code null}
     * @param descriptionTimestamp         the description change instant, or
     *                                     {@code null}
     * @param participants                 the participant set, or
     *                                     {@code null}
     * @param descriptionAuthorJid         the description author JID, or
     *                                     {@code null}
     * @param ephemeralExpiration   the ephemeral expiration in
     *                                     seconds, or {@code 0} to disable
     * @param restrict                     whether metadata editing is
     *                                     restricted to admins
     * @param announce                     whether announcement mode is on
     * @param communityGroups              the linked subgroups, or
     *                                     {@code null}
     * @param isLidAddressingMode          whether LID addressing is enabled
     * @param isIncognito                  whether incognito mode is enabled
     * @param noFrequentlyForwarded        whether forwarded messages are
     *                                     blocked
     * @param membershipApprovalMode       whether admin approval is required
     * @param memberLinkModeAdminOnly      whether invite links are admin-only
     * @param allowNonAdminSubGroupCreation whether non-admins can create
     *                                     subgroups
     * @param memberAddModeAdminOnly       whether only admins can add members
     * @param growthLockExpiration         the growth lock expiration instant,
     *                                     or {@code null}
     * @param growthLockType               the growth lock type, or
     *                                     {@code null}
     * @param reportToAdminMode            whether report-to-admin is enabled
     * @param lastReportToAdminTimestamp   the last report-to-admin instant,
     *                                     or {@code null}
     * @param size                         the server-reported participant
     *                                     count, or {@code null}
     * @param support                      whether this is a support group
     * @param suspended                    whether the community is suspended
     * @param terminated                   whether the community is terminated
     * @param isParentGroupClosed          whether the parent group is closed
     * @param defaultSubgroup              whether this has a default subgroup
     * @param generalSubgroup              whether this has a general subgroup
     * @param hiddenSubgroup               whether this has a hidden subgroup
     * @param groupSafetyCheck             whether the safety check flag is
     *                                     set
     * @param groupAdder                   the JID of who added the user, or
     *                                     {@code null}
     * @param generalChatAutoAddDisabled   whether auto-add to general chat
     *                                     is disabled
     * @param lastCommunityPollTimestamp   the last community poll instant, or
     *                                     {@code null}
     * @param lastActivityTimestamp        the last activity instant, or
     *                                     {@code null}
     * @param lastSeenActivityTimestamp    the last seen activity instant, or
     *                                     {@code null}
     * @param hasCapi                      whether CAPI is available
     * @param isTeeBotGroup                whether this is a TEE bot group
     * @param disappearingModeTrigger      the disappearing mode trigger, or
     *                                     {@code null}
     * @param disappearingModeInitiatedByMe whether the user initiated
     *                                     disappearing mode
     * @param numSubgroups                 the subgroup count, or {@code null}
     * @param limitSharingEnabled          whether limit sharing is enabled
     * @param evolutionVersion             the evolution version, or
     *                                     {@code null}
     * @param participantLabelEnabled      whether participant labels are
     *                                     enabled
     */
    CommunityMetadata(
            Jid jid,
            String subject,
            Jid subjectAuthorJid,
            Instant subjectTimestamp,
            Instant foundationTimestamp,
            Jid founderJid,
            String description,
            String descriptionId,
            Instant descriptionTimestamp,
            SequencedSet<GroupParticipant> participants,
            Jid descriptionAuthorJid,
            ChatEphemeralTimer ephemeralExpiration,
            boolean restrict,
            boolean announce,
            SequencedSet<CommunityLinkedGroup> communityGroups,
            boolean isLidAddressingMode,
            boolean isIncognito,
            boolean noFrequentlyForwarded,
            boolean membershipApprovalMode,
            boolean memberLinkModeAdminOnly,
            boolean allowNonAdminSubGroupCreation,
            boolean memberAddModeAdminOnly,
            Instant growthLockExpiration,
            String growthLockType,
            boolean reportToAdminMode,
            Instant lastReportToAdminTimestamp,
            Integer size,
            boolean support,
            boolean suspended,
            boolean terminated,
            boolean isParentGroupClosed,
            boolean defaultSubgroup,
            boolean generalSubgroup,
            boolean hiddenSubgroup,
            boolean groupSafetyCheck,
            Jid groupAdder,
            boolean generalChatAutoAddDisabled,
            Instant lastCommunityPollTimestamp,
            Instant lastActivityTimestamp,
            Instant lastSeenActivityTimestamp,
            boolean hasCapi,
            boolean isTeeBotGroup,
            ChatDisappearingMode.Trigger disappearingModeTrigger,
            boolean disappearingModeInitiatedByMe,
            Integer numSubgroups,
            boolean limitSharingEnabled,
            Integer evolutionVersion,
            boolean participantLabelEnabled
    ) {
        this.jid = Objects.requireNonNull(jid, "jid cannot be null");
        this.subject = Objects.requireNonNull(subject, "subject cannot be null");
        this.subjectAuthorJid = subjectAuthorJid;
        this.subjectTimestamp = subjectTimestamp;
        this.foundationTimestamp = foundationTimestamp;
        this.founderJid = founderJid;
        this.description = description;
        this.descriptionId = descriptionId;
        this.descriptionTimestamp = descriptionTimestamp;
        this.participants = Objects.requireNonNullElseGet(participants, LinkedHashSet::new);
        this.descriptionAuthorJid = descriptionAuthorJid;
        this.ephemeralExpiration = ephemeralExpiration;
        this.restrict = restrict;
        this.announce = announce;
        this.communityGroups = Objects.requireNonNullElseGet(communityGroups, LinkedHashSet::new);
        this.isLidAddressingMode = isLidAddressingMode;
        this.isIncognito = isIncognito;
        this.noFrequentlyForwarded = noFrequentlyForwarded;
        this.membershipApprovalMode = membershipApprovalMode;
        this.memberLinkModeAdminOnly = memberLinkModeAdminOnly;
        this.allowNonAdminSubGroupCreation = allowNonAdminSubGroupCreation;
        this.memberAddModeAdminOnly = memberAddModeAdminOnly;
        this.growthLockExpiration = growthLockExpiration;
        this.growthLockType = growthLockType;
        this.reportToAdminMode = reportToAdminMode;
        this.lastReportToAdminTimestamp = lastReportToAdminTimestamp;
        this.size = size;
        this.support = support;
        this.suspended = suspended;
        this.terminated = terminated;
        this.isParentGroupClosed = isParentGroupClosed;
        this.defaultSubgroup = defaultSubgroup;
        this.generalSubgroup = generalSubgroup;
        this.hiddenSubgroup = hiddenSubgroup;
        this.groupSafetyCheck = groupSafetyCheck;
        this.groupAdder = groupAdder;
        this.generalChatAutoAddDisabled = generalChatAutoAddDisabled;
        this.lastCommunityPollTimestamp = lastCommunityPollTimestamp;
        this.lastActivityTimestamp = lastActivityTimestamp;
        this.lastSeenActivityTimestamp = lastSeenActivityTimestamp;
        this.hasCapi = hasCapi;
        this.isTeeBotGroup = isTeeBotGroup;
        this.disappearingModeTrigger = disappearingModeTrigger;
        this.disappearingModeInitiatedByMe = disappearingModeInitiatedByMe;
        this.numSubgroups = numSubgroups;
        this.limitSharingEnabled = limitSharingEnabled;
        this.evolutionVersion = evolutionVersion;
        this.participantLabelEnabled = participantLabelEnabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Jid jid() {
        return jid;
    }

    /**
     * Sets the JID that identifies this community.
     *
     * @param jid the community JID
     */
    public void setJid(Jid jid) {
        this.jid = jid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String subject() {
        return subject;
    }

    /**
     * Sets the subject (display name) of this community.
     *
     * @param subject the subject text
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Jid> subjectAuthorJid() {
        return Optional.ofNullable(subjectAuthorJid);
    }

    /**
     * Sets the JID of the participant who last changed the subject.
     *
     * @param subjectAuthorJid the author JID, or {@code null} to clear
     */
    public void setSubjectAuthorJid(Jid subjectAuthorJid) {
        this.subjectAuthorJid = subjectAuthorJid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Instant> subjectTimestamp() {
        return Optional.ofNullable(subjectTimestamp);
    }

    /**
     * Sets the instant at which the subject was last changed.
     *
     * @param subjectTimestamp the subject change instant, or {@code null}
     *                        to clear
     */
    public void setSubjectTimestamp(Instant subjectTimestamp) {
        this.subjectTimestamp = subjectTimestamp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Instant> foundationTimestamp() {
        return Optional.ofNullable(foundationTimestamp);
    }

    /**
     * Sets the instant at which this community was created.
     *
     * @param foundationTimestamp the creation instant, or {@code null} to
     *                           clear
     */
    public void setFoundationTimestamp(Instant foundationTimestamp) {
        this.foundationTimestamp = foundationTimestamp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Jid> founderJid() {
        return Optional.ofNullable(founderJid);
    }

    /**
     * Sets the JID of the user who originally created this community.
     *
     * @param founderJid the founder JID, or {@code null} to clear
     */
    public void setFounderJid(Jid founderJid) {
        this.founderJid = founderJid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    /**
     * Sets the free-form description text of this community.
     *
     * @param description the description text, or {@code null} to clear
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> descriptionId() {
        return Optional.ofNullable(descriptionId);
    }

    /**
     * Sets the server-assigned description revision identifier.
     *
     * @param descriptionId the description identifier, or {@code null} to
     *                      clear
     */
    public void setDescriptionId(String descriptionId) {
        this.descriptionId = descriptionId;
    }

    /**
     * Returns the instant at which the description was last changed, if
     * known.
     *
     * @return an {@code Optional} containing the description timestamp, or
     *         empty if not available
     */
    public Optional<Instant> descriptionTimestamp() {
        return Optional.ofNullable(descriptionTimestamp);
    }

    /**
     * Sets the instant at which the description was last changed.
     *
     * @param descriptionTimestamp the description change instant, or
     *                            {@code null} to clear
     */
    public void setDescriptionTimestamp(Instant descriptionTimestamp) {
        this.descriptionTimestamp = descriptionTimestamp;
    }

    /**
     * Returns the JID of the participant who last changed the description,
     * if known.
     *
     * @return an {@code Optional} containing the description author JID, or
     *         empty if not known
     */
    public Optional<Jid> descriptionAuthorJid() {
        return Optional.ofNullable(descriptionAuthorJid);
    }

    /**
     * Sets the JID of the participant who last changed the description.
     *
     * @param descriptionAuthorJid the author JID, or {@code null} to clear
     */
    public void setDescriptionAuthorJid(Jid descriptionAuthorJid) {
        this.descriptionAuthorJid = descriptionAuthorJid;
    }

    /**
     * {@inheritDoc}
     *
     * <p>If no participants have been added an empty set is returned.
     */
    @Override
    public Set<GroupParticipant> participants() {
        return participants == null ? Set.of() : Collections.unmodifiableSet(participants);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addParticipant(GroupParticipant participant) {
        return participants.add(participant);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeParticipant(GroupParticipant participant) {
        return participants.remove(participant);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeParticipant(Jid jid) {
        return participants.removeIf(participant -> participant.userJid().equals(jid));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearParticipants() {
        participants.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addAllParticipants(Collection<GroupParticipant> participants) {
        return this.participants.addAll(participants);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ChatEphemeralTimer> ephemeralExpiration() {
        return Optional.ofNullable(ephemeralExpiration);
    }

    /**
     * Sets the ephemeral message timer for this community.
     *
     * @param ephemeralExpiration the ephemeral timer to set, or {@code null}
     *        to disable ephemeral messaging
     */
    @Override
    public void setEphemeralExpiration(ChatEphemeralTimer ephemeralExpiration) {
        this.ephemeralExpiration = ephemeralExpiration;
    }

    /**
     * Returns whether metadata editing is restricted to administrators.
     *
     * @return {@code true} if only administrators can edit metadata
     */
    public boolean isRestrict() {
        return restrict;
    }

    /**
     * Sets whether metadata editing is restricted to administrators.
     *
     * @param restrict {@code true} to restrict to admins
     */
    public void setRestrict(boolean restrict) {
        this.restrict = restrict;
    }

    /**
     * Returns whether the community is in announcement mode.
     *
     * @return {@code true} if only administrators can send messages
     */
    public boolean isAnnounce() {
        return announce;
    }

    /**
     * Sets whether the community is in announcement mode.
     *
     * @param announce {@code true} to enable announcement mode
     */
    public void setAnnounce(boolean announce) {
        this.announce = announce;
    }

    /**
     * Returns an unmodifiable view of the subgroups linked to this
     * community. If no subgroups have been linked an empty set is returned.
     *
     * @return an unmodifiable {@code SequencedSet} of linked groups, never
     *         {@code null}
     */
    public SequencedSet<CommunityLinkedGroup> communityGroups() {
        return communityGroups == null
                ? Collections.emptyNavigableSet()
                : Collections.unmodifiableSequencedSet(communityGroups);
    }

    /**
     * Sets the collection of subgroups linked to this community, replacing
     * any previously linked subgroups.
     *
     * @param communityGroups the linked subgroups, or {@code null} to clear
     */
    public void setCommunityGroups(SequencedSet<CommunityLinkedGroup> communityGroups) {
        this.communityGroups = Objects.requireNonNullElseGet(communityGroups, LinkedHashSet::new);
    }

    /**
     * Adds a linked subgroup to this community.
     *
     * @param group the non-{@code null} linked group to add
     */
    public void addCommunityGroup(CommunityLinkedGroup group) {
        communityGroups.add(group);
    }

    /**
     * Removes the specified linked subgroup from this community.
     *
     * @param group the non-{@code null} linked group to remove
     * @return {@code true} if the group was present and removed
     */
    public boolean removeCommunityGroup(CommunityLinkedGroup group) {
        return communityGroups.remove(group);
    }

    /**
     * Removes the linked subgroup identified by the given JID from this
     * community.
     *
     * @param jid the non-{@code null} JID of the linked group to remove
     * @return {@code true} if a matching group was found and removed
     */
    public boolean removeCommunityGroup(Jid jid) {
        return communityGroups.removeIf(group -> group.jid().equals(jid));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLidAddressingMode() {
        return isLidAddressingMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLidAddressingMode(boolean lidAddressingMode) {
        this.isLidAddressingMode = lidAddressingMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isIncognito() {
        return isIncognito;
    }

    /**
     * Sets whether this community operates in incognito mode.
     *
     * @param incognito {@code true} to enable incognito mode, {@code false}
     *                  to disable it
     */
    public void setIncognito(boolean incognito) {
        this.isIncognito = incognito;
    }

    /**
     * Returns whether frequently forwarded messages are blocked.
     *
     * @return {@code true} if forwarded messages are blocked
     */
    public boolean isNoFrequentlyForwarded() {
        return noFrequentlyForwarded;
    }

    /**
     * Sets whether frequently forwarded messages are blocked.
     *
     * @param noFrequentlyForwarded {@code true} to block forwarded messages
     */
    public void setNoFrequentlyForwarded(boolean noFrequentlyForwarded) {
        this.noFrequentlyForwarded = noFrequentlyForwarded;
    }

    /**
     * Returns whether admin approval is required for new members.
     *
     * @return {@code true} if membership approval mode is enabled
     */
    public boolean isMembershipApprovalMode() {
        return membershipApprovalMode;
    }

    /**
     * Sets whether admin approval is required for new members.
     *
     * @param membershipApprovalMode {@code true} to enable approval mode
     */
    public void setMembershipApprovalMode(boolean membershipApprovalMode) {
        this.membershipApprovalMode = membershipApprovalMode;
    }

    /**
     * Returns whether invite link usage is restricted to administrators.
     *
     * @return {@code true} if only admins can use invite links
     */
    public boolean isMemberLinkModeAdminOnly() {
        return memberLinkModeAdminOnly;
    }

    /**
     * Sets whether invite link usage is restricted to administrators.
     *
     * @param memberLinkModeAdminOnly {@code true} to restrict to admins
     */
    public void setMemberLinkModeAdminOnly(boolean memberLinkModeAdminOnly) {
        this.memberLinkModeAdminOnly = memberLinkModeAdminOnly;
    }

    /**
     * Returns whether non-admin members can create or link subgroups.
     *
     * @return {@code true} if non-admins are allowed to create subgroups
     */
    public boolean isAllowNonAdminSubGroupCreation() {
        return allowNonAdminSubGroupCreation;
    }

    /**
     * Sets whether non-admin members can create or link subgroups.
     *
     * @param allowNonAdminSubGroupCreation {@code true} to allow non-admin
     *                                      subgroup creation
     */
    public void setAllowNonAdminSubGroupCreation(boolean allowNonAdminSubGroupCreation) {
        this.allowNonAdminSubGroupCreation = allowNonAdminSubGroupCreation;
    }

    /**
     * Returns the {@link ChatPolicy} corresponding to the
     * {@link #isAllowNonAdminSubGroupCreation()} state.
     *
     * @return {@link ChatPolicy#ANYONE} if non-admins are allowed,
     *         {@link ChatPolicy#ADMINS} otherwise
     */
    public ChatPolicy allowNonAdminSubGroupCreationPolicy() {
        return allowNonAdminSubGroupCreation ? ChatPolicy.ANYONE : ChatPolicy.ADMINS;
    }

    /**
     * Returns whether only administrators can add members.
     *
     * @return {@code true} if member addition is restricted to admins
     */
    public boolean isMemberAddModeAdminOnly() {
        return memberAddModeAdminOnly;
    }

    /**
     * Sets whether only administrators can add members.
     *
     * @param memberAddModeAdminOnly {@code true} to restrict to admins
     */
    public void setMemberAddModeAdminOnly(boolean memberAddModeAdminOnly) {
        this.memberAddModeAdminOnly = memberAddModeAdminOnly;
    }

    /**
     * Returns the {@link ChatPolicy} corresponding to the
     * {@link #isMemberAddModeAdminOnly()} state.
     *
     * @return {@link ChatPolicy#ADMINS} if member addition is
     *         restricted, {@link ChatPolicy#ANYONE} otherwise
     */
    public ChatPolicy memberAddModePolicy() {
        return memberAddModeAdminOnly ? ChatPolicy.ADMINS : ChatPolicy.ANYONE;
    }

    /**
     * Returns the instant at which the growth lock expires, if active.
     *
     * @return an {@code Optional} containing the growth lock expiration, or
     *         empty if no growth lock is active
     */
    public Optional<Instant> growthLockExpiration() {
        return Optional.ofNullable(growthLockExpiration);
    }

    /**
     * Sets the instant at which the growth lock expires.
     *
     * @param growthLockExpiration the expiration instant, or {@code null}
     *                            to clear
     */
    public void setGrowthLockExpiration(Instant growthLockExpiration) {
        this.growthLockExpiration = growthLockExpiration;
    }

    /**
     * Returns the type of growth lock applied, if any.
     *
     * @return an {@code Optional} containing the growth lock type, or empty
     *         if no growth lock is active
     */
    public Optional<String> growthLockType() {
        return Optional.ofNullable(growthLockType);
    }

    /**
     * Sets the type of growth lock applied to this community.
     *
     * @param growthLockType the growth lock type, or {@code null} to clear
     */
    public void setGrowthLockType(String growthLockType) {
        this.growthLockType = growthLockType;
    }

    /**
     * Returns whether the report-to-admin feature is enabled.
     *
     * @return {@code true} if report-to-admin is enabled
     */
    public boolean isReportToAdminMode() {
        return reportToAdminMode;
    }

    /**
     * Sets whether the report-to-admin feature is enabled.
     *
     * @param reportToAdminMode {@code true} to enable
     */
    public void setReportToAdminMode(boolean reportToAdminMode) {
        this.reportToAdminMode = reportToAdminMode;
    }

    /**
     * Returns the instant of the last report-to-admin event, if any.
     *
     * @return an {@code Optional} containing the timestamp, or empty if no
     *         event has occurred
     */
    public Optional<Instant> lastReportToAdminTimestamp() {
        return Optional.ofNullable(lastReportToAdminTimestamp);
    }

    /**
     * Sets the instant of the last report-to-admin event.
     *
     * @param lastReportToAdminTimestamp the timestamp, or {@code null} to
     *                                  clear
     */
    public void setLastReportToAdminTimestamp(Instant lastReportToAdminTimestamp) {
        this.lastReportToAdminTimestamp = lastReportToAdminTimestamp;
    }

    /**
     * Returns the server-reported participant count, if available.
     *
     * @return an {@code OptionalInt} containing the count, or empty if not
     *         available
     */
    public OptionalInt size() {
        return size == null ? OptionalInt.empty() : OptionalInt.of(size);
    }

    /**
     * Sets the server-reported participant count.
     *
     * @param size the participant count, or {@code null} to clear
     */
    public void setSize(Integer size) {
        this.size = size;
    }

    /**
     * Returns whether this is a support group.
     *
     * @return {@code true} if this is a support group
     */
    public boolean isSupport() {
        return support;
    }

    /**
     * Sets whether this is a support group.
     *
     * @param support {@code true} to mark as support
     */
    public void setSupport(boolean support) {
        this.support = support;
    }

    /**
     * Returns whether this community is suspended.
     *
     * @return {@code true} if suspended
     */
    public boolean isSuspended() {
        return suspended;
    }

    /**
     * Sets whether this community is suspended.
     *
     * @param suspended {@code true} to mark as suspended
     */
    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    /**
     * Returns whether this community is terminated.
     *
     * @return {@code true} if terminated
     */
    public boolean isTerminated() {
        return terminated;
    }

    /**
     * Sets whether this community is terminated.
     *
     * @param terminated {@code true} to mark as terminated
     */
    public void setTerminated(boolean terminated) {
        this.terminated = terminated;
    }

    /**
     * Returns whether the parent group is closed.
     *
     * @return {@code true} if the parent group is closed
     */
    public boolean isParentGroupClosed() {
        return isParentGroupClosed;
    }

    /**
     * Sets whether the parent group is closed.
     *
     * @param parentGroupClosed {@code true} to mark as closed
     */
    public void setParentGroupClosed(boolean parentGroupClosed) {
        this.isParentGroupClosed = parentGroupClosed;
    }

    /**
     * Returns whether this community has a default subgroup.
     *
     * @return {@code true} if a default subgroup exists
     */
    public boolean isDefaultSubgroup() {
        return defaultSubgroup;
    }

    /**
     * Sets whether this community has a default subgroup.
     *
     * @param defaultSubgroup {@code true} if a default subgroup exists
     */
    public void setDefaultSubgroup(boolean defaultSubgroup) {
        this.defaultSubgroup = defaultSubgroup;
    }

    /**
     * Returns whether this community has a general chat subgroup.
     *
     * @return {@code true} if a general subgroup exists
     */
    public boolean isGeneralSubgroup() {
        return generalSubgroup;
    }

    /**
     * Sets whether this community has a general chat subgroup.
     *
     * @param generalSubgroup {@code true} if a general subgroup exists
     */
    public void setGeneralSubgroup(boolean generalSubgroup) {
        this.generalSubgroup = generalSubgroup;
    }

    /**
     * Returns whether this community has a hidden subgroup.
     *
     * @return {@code true} if a hidden subgroup exists
     */
    public boolean isHiddenSubgroup() {
        return hiddenSubgroup;
    }

    /**
     * Sets whether this community has a hidden subgroup.
     *
     * @param hiddenSubgroup {@code true} if a hidden subgroup exists
     */
    public void setHiddenSubgroup(boolean hiddenSubgroup) {
        this.hiddenSubgroup = hiddenSubgroup;
    }

    /**
     * Returns whether the group safety check flag is set.
     *
     * @return {@code true} if the safety check is set
     */
    public boolean isGroupSafetyCheck() {
        return groupSafetyCheck;
    }

    /**
     * Sets the group safety check flag.
     *
     * @param groupSafetyCheck {@code true} to set the flag
     */
    public void setGroupSafetyCheck(boolean groupSafetyCheck) {
        this.groupSafetyCheck = groupSafetyCheck;
    }

    /**
     * Returns the JID of the user who added the current user to this
     * community, if known.
     *
     * @return an {@code Optional} containing the adder JID, or empty if
     *         not known
     */
    public Optional<Jid> groupAdder() {
        return Optional.ofNullable(groupAdder);
    }

    /**
     * Sets the JID of the user who added the current user to this community.
     *
     * @param groupAdder the adder JID, or {@code null} to clear
     */
    public void setGroupAdder(Jid groupAdder) {
        this.groupAdder = groupAdder;
    }

    /**
     * Returns whether auto-add to general chat is disabled.
     *
     * @return {@code true} if auto-add is disabled
     */
    public boolean isGeneralChatAutoAddDisabled() {
        return generalChatAutoAddDisabled;
    }

    /**
     * Sets whether auto-add to general chat is disabled.
     *
     * @param generalChatAutoAddDisabled {@code true} to disable auto-add
     */
    public void setGeneralChatAutoAddDisabled(boolean generalChatAutoAddDisabled) {
        this.generalChatAutoAddDisabled = generalChatAutoAddDisabled;
    }

    /**
     * Returns the instant of the last community poll, if any.
     *
     * @return an {@code Optional} containing the timestamp, or empty if no
     *         poll has occurred
     */
    public Optional<Instant> lastCommunityPollTimestamp() {
        return Optional.ofNullable(lastCommunityPollTimestamp);
    }

    /**
     * Sets the instant of the last community poll.
     *
     * @param lastCommunityPollTimestamp the timestamp, or {@code null} to
     *                                  clear
     */
    public void setLastCommunityPollTimestamp(Instant lastCommunityPollTimestamp) {
        this.lastCommunityPollTimestamp = lastCommunityPollTimestamp;
    }

    /**
     * Returns the instant of the last activity, if available.
     *
     * @return an {@code Optional} containing the timestamp, or empty if
     *         not available
     */
    public Optional<Instant> lastActivityTimestamp() {
        return Optional.ofNullable(lastActivityTimestamp);
    }

    /**
     * Sets the instant of the last activity.
     *
     * @param lastActivityTimestamp the timestamp, or {@code null} to clear
     */
    public void setLastActivityTimestamp(Instant lastActivityTimestamp) {
        this.lastActivityTimestamp = lastActivityTimestamp;
    }

    /**
     * Returns the instant of the last seen activity, if available.
     *
     * @return an {@code Optional} containing the timestamp, or empty if
     *         not available
     */
    public Optional<Instant> lastSeenActivityTimestamp() {
        return Optional.ofNullable(lastSeenActivityTimestamp);
    }

    /**
     * Sets the instant of the last seen activity.
     *
     * @param lastSeenActivityTimestamp the timestamp, or {@code null} to
     *                                 clear
     */
    public void setLastSeenActivityTimestamp(Instant lastSeenActivityTimestamp) {
        this.lastSeenActivityTimestamp = lastSeenActivityTimestamp;
    }

    /**
     * Returns whether this community has CAPI capabilities.
     *
     * @return {@code true} if CAPI is available
     */
    public boolean hasCapi() {
        return hasCapi;
    }

    /**
     * Sets whether this community has CAPI capabilities.
     *
     * @param hasCapi {@code true} to enable
     */
    public void setHasCapi(boolean hasCapi) {
        this.hasCapi = hasCapi;
    }

    /**
     * Returns whether this community is a TEE bot group.
     *
     * @return {@code true} if this is a TEE bot group
     */
    public boolean isTeeBotGroup() {
        return isTeeBotGroup;
    }

    /**
     * Sets whether this community is a TEE bot group.
     *
     * @param teeBotGroup {@code true} to mark as TEE bot group
     */
    public void setTeeBotGroup(boolean teeBotGroup) {
        this.isTeeBotGroup = teeBotGroup;
    }

    /**
     * Returns the trigger that caused disappearing message mode to be set,
     * if any.
     *
     * @return an {@code Optional} containing the trigger, or empty if not
     *         set
     */
    public Optional<ChatDisappearingMode.Trigger> disappearingModeTrigger() {
        return Optional.ofNullable(disappearingModeTrigger);
    }

    /**
     * Sets the trigger that caused disappearing message mode.
     *
     * @param disappearingModeTrigger the trigger, or {@code null} to clear
     */
    public void setDisappearingModeTrigger(ChatDisappearingMode.Trigger disappearingModeTrigger) {
        this.disappearingModeTrigger = disappearingModeTrigger;
    }

    /**
     * Returns whether the current user initiated the disappearing message
     * mode.
     *
     * @return {@code true} if initiated by the current user
     */
    public boolean isDisappearingModeInitiatedByMe() {
        return disappearingModeInitiatedByMe;
    }

    /**
     * Sets whether the current user initiated the disappearing message mode.
     *
     * @param disappearingModeInitiatedByMe {@code true} if initiated by
     *                                      current user
     */
    public void setDisappearingModeInitiatedByMe(boolean disappearingModeInitiatedByMe) {
        this.disappearingModeInitiatedByMe = disappearingModeInitiatedByMe;
    }

    /**
     * Returns the number of subgroups, if available.
     *
     * @return an {@code OptionalInt} containing the count, or empty if not
     *         available
     */
    public OptionalInt numSubgroups() {
        return numSubgroups == null ? OptionalInt.empty() : OptionalInt.of(numSubgroups);
    }

    /**
     * Sets the number of subgroups.
     *
     * @param numSubgroups the subgroup count, or {@code null} to clear
     */
    public void setNumSubgroups(Integer numSubgroups) {
        this.numSubgroups = numSubgroups;
    }

    /**
     * Returns whether the limit sharing feature is enabled.
     *
     * @return {@code true} if limit sharing is enabled
     */
    public boolean isLimitSharingEnabled() {
        return limitSharingEnabled;
    }

    /**
     * Sets whether the limit sharing feature is enabled.
     *
     * @param limitSharingEnabled {@code true} to enable
     */
    public void setLimitSharingEnabled(boolean limitSharingEnabled) {
        this.limitSharingEnabled = limitSharingEnabled;
    }

    /**
     * Returns the group evolution version, if available.
     *
     * @return an {@code OptionalInt} containing the version, or empty if
     *         not available
     */
    public OptionalInt evolutionVersion() {
        return evolutionVersion == null ? OptionalInt.empty() : OptionalInt.of(evolutionVersion);
    }

    /**
     * Sets the group evolution version.
     *
     * @param evolutionVersion the version, or {@code null} to clear
     */
    public void setEvolutionVersion(Integer evolutionVersion) {
        this.evolutionVersion = evolutionVersion;
    }

    /**
     * Returns whether participant labels are enabled.
     *
     * @return {@code true} if participant labels are enabled
     */
    public boolean isParticipantLabelEnabled() {
        return participantLabelEnabled;
    }

    /**
     * Sets whether participant labels are enabled.
     *
     * @param participantLabelEnabled {@code true} to enable
     */
    public void setParticipantLabelEnabled(boolean participantLabelEnabled) {
        this.participantLabelEnabled = participantLabelEnabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isOpenBotGroup() {
        return isOpenBotGroup;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOpenBotGroup(boolean openBotGroup) {
        this.isOpenBotGroup = openBotGroup;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CommunityMetadata that
                && Objects.equals(jid, that.jid);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(jid);
    }

    @Override
    public String toString() {
        return "CommunityMetadata[jid=" + jid + ", subject=" + subject + ']';
    }
}
