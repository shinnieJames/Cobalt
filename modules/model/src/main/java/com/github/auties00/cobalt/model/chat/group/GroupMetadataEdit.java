package com.github.auties00.cobalt.model.chat.group;

import com.github.auties00.cobalt.model.jid.Jid;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Input model describing a batch of edits to apply to a WhatsApp group's
 * metadata. The {@link #group} JID identifies the target; every other
 * field is optional and only triggers a server- or store-side mutation
 * when it carries a value (or, for the explicit clear flags, when it is
 * {@code true}).
 *
 * <p>The same edit packet drives several distinct sites:
 * <ul>
 *   <li><strong>Direct {@code w:g2} / {@code w:profile:picture} edits</strong>
 *       ({@link #subject}, {@link #description}, {@link #picture}) are
 *       translated into a single {@code iq} stanza each and sent to the
 *       relay.</li>
 *   <li><strong>Batched {@code WASmaxGroupsSetPropertyRPC} edits</strong>
 *       (all the legacy binary toggles plus
 *       {@link #ephemeralExpiration}, {@link #ephemeralTrigger},
 *       {@link #notEphemeral} and
 *       {@link #membershipApprovalGroupJoinMode}) are batched into a
 *       single {@code w:g2} property IQ.</li>
 *   <li><strong>MEX-dispatched edits</strong>
 *       ({@link #limitSharingEnabled}, {@link #limitSharingDisabled},
 *       {@link #memberAddAdminOnly}, {@link #memberAddAllMember},
 *       {@link #memberLinkAdminOnly}, {@link #memberLinkAllMember},
 *       {@link #memberShareGroupHistoryAdminOnly},
 *       {@link #memberShareGroupHistoryAllMember},
 *       {@link #allowNonAdminSubGroupCreation},
 *       {@link #notAllowNonAdminSubGroupCreation}) flow through the
 *       {@code WAWebMexUpdateGroupPropertyJob} GraphQL endpoint. The
 *       {@link #limitSharingEnabled} / {@link #limitSharingDisabled}
 *       and {@link #allowNonAdminSubGroupCreation} /
 *       {@link #notAllowNonAdminSubGroupCreation} pairs additionally
 *       commit a WAM event after the mutation completes.</li>
 *   <li><strong>Local-only edits</strong> ({@link #statusMuted}) are
 *       merged into the in-memory {@link GroupMetadata} row for the
 *       target group without producing a network packet. This branch
 *       backs the {@code WAWebUserStatusMuteSync.applyMutations} sync
 *       path, where the relay has already decided the value and the
 *       client is only reflecting it locally.</li>
 * </ul>
 *
 * <p>Each settings boolean (locked/unlocked, announcement/notAnnouncement,
 * etc.) stays a primitive {@code boolean} because the wire encoding from
 * WA Web treats both sides of every binary setting as independent
 * set-only commands and "off" is encoded as the matching negated toggle,
 * not the absence of the positive flag. The two non-trivial scalar
 * properties — {@link #description} and {@link #picture} — instead use
 * the sealed {@link GroupDescription} and {@link GroupPicture} types to
 * fold the "replace" and "clear" intents into one field each; a
 * {@code null} field still means "leave untouched". {@link #subject}
 * stays a nullable {@link String} because WA Web has no clear-subject
 * operation for groups.
 *
 * <p>{@link #statusMuted} is declared as a nullable {@code Boolean} so
 * that a present {@code false} is distinguishable from an absent value.
 * This matches the WA Web behaviour where {@code userStatusMuteAction.muted}
 * being {@code undefined} yields {@code malformedActionValue} rather
 * than silently applying {@code muted=false}.
 */
@ProtobufMessage
public final class GroupMetadataEdit {
    /**
     * JID of the group whose metadata is being edited. Required.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    final Jid group;

    /**
     * Locks the group so that only admins can send messages.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
    final boolean locked;

    /**
     * Switches the group into announcement mode.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.BOOL)
    final boolean announcement;

    /**
     * Disables the "frequently forwarded" badge on group messages.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
    final boolean noFrequentlyForwarded;

    /**
     * Optional ephemeral-message expiration window, in seconds.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.INT32)
    final Integer ephemeralExpiration;

    /**
     * Optional ephemeral-message trigger code identifying who flipped
     * the ephemeral timer.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.INT32)
    final Integer ephemeralTrigger;

    /**
     * Unlocks a previously locked group.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.BOOL)
    final boolean unlocked;

    /**
     * Switches the group out of announcement mode.
     */
    @ProtobufProperty(index = 8, type = ProtobufType.BOOL)
    final boolean notAnnouncement;

    /**
     * Restores the "frequently forwarded" badge after it was disabled.
     */
    @ProtobufProperty(index = 9, type = ProtobufType.BOOL)
    final boolean frequentlyForwardedOk;

    /**
     * Disables ephemeral messages for the group.
     */
    @ProtobufProperty(index = 10, type = ProtobufType.BOOL)
    final boolean notEphemeral;

    /**
     * Optional membership-approval join-mode code; {@code null} leaves
     * the existing join mode untouched.
     */
    @ProtobufProperty(index = 11, type = ProtobufType.STRING)
    final String membershipApprovalGroupJoinMode;

    /**
     * Allows admins to file reports on behalf of the group.
     */
    @ProtobufProperty(index = 12, type = ProtobufType.BOOL)
    final boolean allowAdminReports;

    /**
     * Inverse of {@link #allowAdminReports}.
     */
    @ProtobufProperty(index = 13, type = ProtobufType.BOOL)
    final boolean notAllowAdminReports;

    /**
     * Allows non-admins to create sub-groups under this community
     * parent. Dispatched through the
     * {@code WAWebMexUpdateGroupPropertyJob} GraphQL endpoint and
     * followed by a {@code CommunityGroupJourneyEvent} WAM commit.
     */
    @ProtobufProperty(index = 14, type = ProtobufType.BOOL)
    final boolean allowNonAdminSubGroupCreation;

    /**
     * Inverse of {@link #allowNonAdminSubGroupCreation}.
     */
    @ProtobufProperty(index = 15, type = ProtobufType.BOOL)
    final boolean notAllowNonAdminSubGroupCreation;

    /**
     * Enables the "group history" feature, allowing new joiners to see
     * past messages.
     */
    @ProtobufProperty(index = 16, type = ProtobufType.BOOL)
    final boolean groupHistory;

    /**
     * Inverse of {@link #groupHistory}.
     */
    @ProtobufProperty(index = 17, type = ProtobufType.BOOL)
    final boolean noGroupHistory;

    /**
     * New group subject (display name). When non-{@code null}, the
     * editor emits a {@code <subject>NEW</subject>} body inside a
     * {@code w:g2} {@code iq} of type {@code set}. When {@code null},
     * the subject is left untouched.
     */
    @ProtobufProperty(index = 18, type = ProtobufType.STRING)
    final String subject;

    /**
     * Description-edit intent. When non-{@code null}, drives a
     * {@code w:g2} {@code <description>} IQ:
     * {@link GroupDescription.Set} wraps a new body, while
     * {@link GroupDescription.Clear} requests removal of the existing
     * body matching WA Web's {@code hasDescriptionDeleteTrue:!0}
     * branch. When {@code null}, the description is left untouched.
     */
    @ProtobufProperty(index = 19, type = ProtobufType.STRING)
    final GroupDescription description;

    /**
     * Picture-edit intent. When non-{@code null}, drives a
     * {@code w:profile:picture} IQ: {@link GroupPicture.Set} carries
     * the new bytes (typically a 256x256 JPEG) and emits a
     * {@code <picture type="image">BYTES</picture>} body, while
     * {@link GroupPicture.Clear} emits the no-body variant matching
     * WA Web's {@code WAWebSendProfilePictureJob(group, null)} removal
     * path. When {@code null}, the picture is left untouched.
     */
    @ProtobufProperty(index = 21, type = ProtobufType.BYTES)
    final GroupPicture picture;

    /**
     * Local-only override for the group's
     * {@link GroupMetadata#statusMuted()} flag. When non-{@code null},
     * the editor merges this value into the in-memory metadata row
     * without producing any network packet — the relay has already
     * decided the value (the sync action carrying this edit is itself
     * server-driven). When {@code null}, the flag is left untouched.
     */
    @ProtobufProperty(index = 23, type = ProtobufType.BOOL)
    final Boolean statusMuted;

    /**
     * Enables the per-chat "limit sharing" anti-forward feature.
     * Dispatched through {@code WAWebMexUpdateGroupPropertyJob} with
     * {@code limit_sharing.limit_sharing_enabled=true} and followed by
     * a {@code LimitSharingSettingUpdateWamEvent} commit carrying
     * {@code toggleUpdateAction=TURN_ON}.
     */
    @ProtobufProperty(index = 24, type = ProtobufType.BOOL)
    final boolean limitSharingEnabled;

    /**
     * Inverse of {@link #limitSharingEnabled}. Emits
     * {@code limit_sharing.limit_sharing_enabled=false} and commits a
     * {@code LimitSharingSettingUpdateWamEvent} carrying
     * {@code toggleUpdateAction=TURN_OFF}.
     */
    @ProtobufProperty(index = 25, type = ProtobufType.BOOL)
    final boolean limitSharingDisabled;

    /**
     * Restricts adding new members to admins. Dispatched through
     * {@code WAWebMexUpdateGroupPropertyJob} with
     * {@code member_add_mode="ADMIN_ADD"}, matching the WA Web
     * {@code WAWebSetPropertyGroupAction} {@code GROUP_SETTING_TYPE
     * .MEMBER_ADD_MODE} ADMINS branch.
     */
    @ProtobufProperty(index = 26, type = ProtobufType.BOOL)
    final boolean memberAddAdminOnly;

    /**
     * Inverse of {@link #memberAddAdminOnly}. Emits
     * {@code member_add_mode="ALL_MEMBER_ADD"}, matching the WA Web
     * non-ADMINS branch.
     */
    @ProtobufProperty(index = 27, type = ProtobufType.BOOL)
    final boolean memberAddAllMember;

    /**
     * Restricts sharing the invite link to admins. Dispatched through
     * {@code WAWebMexUpdateGroupPropertyJob} with
     * {@code member_link_mode="ADMIN_LINK"}, matching the WA Web
     * {@code MEMBER_LINK_MODE} ADMINS branch.
     */
    @ProtobufProperty(index = 28, type = ProtobufType.BOOL)
    final boolean memberLinkAdminOnly;

    /**
     * Inverse of {@link #memberLinkAdminOnly}. Emits
     * {@code member_link_mode="ALL_MEMBER_LINK"}, matching the WA Web
     * non-ADMINS branch.
     */
    @ProtobufProperty(index = 29, type = ProtobufType.BOOL)
    final boolean memberLinkAllMember;

    /**
     * Restricts sharing the message history with newly added members
     * to admins. Dispatched through
     * {@code WAWebMexUpdateGroupPropertyJob} with
     * {@code member_share_group_history_mode="ADMIN_SHARE"}, matching
     * the WA Web {@code MEMBER_SHARE_GROUP_HISTORY_MODE} ADMINS
     * branch.
     */
    @ProtobufProperty(index = 30, type = ProtobufType.BOOL)
    final boolean memberShareGroupHistoryAdminOnly;

    /**
     * Inverse of {@link #memberShareGroupHistoryAdminOnly}. Emits
     * {@code member_share_group_history_mode="ALL_MEMBER_SHARE"},
     * matching the WA Web non-ADMINS branch.
     */
    @ProtobufProperty(index = 31, type = ProtobufType.BOOL)
    final boolean memberShareGroupHistoryAllMember;

    /**
     * Constructs a new {@code GroupMetadataEdit}.
     *
     * @param group                            the group JID; required
     * @param locked                           whether to lock the group
     * @param announcement                     whether to enable announcement mode
     * @param noFrequentlyForwarded            whether to disable the frequently-forwarded badge
     * @param ephemeralExpiration              optional ephemeral expiration in seconds
     * @param ephemeralTrigger                 optional ephemeral trigger code
     * @param unlocked                         whether to unlock the group
     * @param notAnnouncement                  whether to disable announcement mode
     * @param frequentlyForwardedOk            whether to restore the frequently-forwarded badge
     * @param notEphemeral                     whether to disable ephemeral messages
     * @param membershipApprovalGroupJoinMode  optional join-mode code, or {@code null}
     * @param allowAdminReports                whether to allow admin reports
     * @param notAllowAdminReports             whether to disable admin reports
     * @param allowNonAdminSubGroupCreation    whether to allow non-admin sub-group creation
     * @param notAllowNonAdminSubGroupCreation whether to disable non-admin sub-group creation
     * @param groupHistory                     whether to enable group history
     * @param noGroupHistory                   whether to disable group history
     * @param subject                          new group subject, or {@code null} to leave unchanged
     * @param description                      description-edit intent, or {@code null} to leave unchanged
     * @param picture                          picture-edit intent, or {@code null} to leave unchanged
     * @param statusMuted                      new local statusMuted flag, or {@code null} to leave unchanged
     * @param limitSharingEnabled              whether to enable the per-chat sharing limit
     * @param limitSharingDisabled             whether to disable the per-chat sharing limit
     * @param memberAddAdminOnly               whether to restrict member-add to admins
     * @param memberAddAllMember               whether to open member-add to all members
     * @param memberLinkAdminOnly              whether to restrict invite-link sharing to admins
     * @param memberLinkAllMember              whether to open invite-link sharing to all members
     * @param memberShareGroupHistoryAdminOnly whether to restrict history sharing to admins
     * @param memberShareGroupHistoryAllMember whether to open history sharing to all members
     * @throws NullPointerException if {@code group} is {@code null}
     */
    GroupMetadataEdit(Jid group, boolean locked, boolean announcement, boolean noFrequentlyForwarded,
                      Integer ephemeralExpiration, Integer ephemeralTrigger, boolean unlocked,
                      boolean notAnnouncement, boolean frequentlyForwardedOk, boolean notEphemeral,
                      String membershipApprovalGroupJoinMode, boolean allowAdminReports,
                      boolean notAllowAdminReports, boolean allowNonAdminSubGroupCreation,
                      boolean notAllowNonAdminSubGroupCreation, boolean groupHistory,
                      boolean noGroupHistory, String subject, GroupDescription description,
                      GroupPicture picture, Boolean statusMuted,
                      boolean limitSharingEnabled, boolean limitSharingDisabled,
                      boolean memberAddAdminOnly, boolean memberAddAllMember,
                      boolean memberLinkAdminOnly, boolean memberLinkAllMember,
                      boolean memberShareGroupHistoryAdminOnly,
                      boolean memberShareGroupHistoryAllMember) {
        this.group = Objects.requireNonNull(group, "group cannot be null");
        this.locked = locked;
        this.announcement = announcement;
        this.noFrequentlyForwarded = noFrequentlyForwarded;
        this.ephemeralExpiration = ephemeralExpiration;
        this.ephemeralTrigger = ephemeralTrigger;
        this.unlocked = unlocked;
        this.notAnnouncement = notAnnouncement;
        this.frequentlyForwardedOk = frequentlyForwardedOk;
        this.notEphemeral = notEphemeral;
        this.membershipApprovalGroupJoinMode = membershipApprovalGroupJoinMode;
        this.allowAdminReports = allowAdminReports;
        this.notAllowAdminReports = notAllowAdminReports;
        this.allowNonAdminSubGroupCreation = allowNonAdminSubGroupCreation;
        this.notAllowNonAdminSubGroupCreation = notAllowNonAdminSubGroupCreation;
        this.groupHistory = groupHistory;
        this.noGroupHistory = noGroupHistory;
        this.subject = subject;
        this.description = description;
        this.picture = picture;
        this.statusMuted = statusMuted;
        this.limitSharingEnabled = limitSharingEnabled;
        this.limitSharingDisabled = limitSharingDisabled;
        this.memberAddAdminOnly = memberAddAdminOnly;
        this.memberAddAllMember = memberAddAllMember;
        this.memberLinkAdminOnly = memberLinkAdminOnly;
        this.memberLinkAllMember = memberLinkAllMember;
        this.memberShareGroupHistoryAdminOnly = memberShareGroupHistoryAdminOnly;
        this.memberShareGroupHistoryAllMember = memberShareGroupHistoryAllMember;
    }

    /**
     * Returns the group JID.
     *
     * @return the JID, never {@code null}
     */
    public Jid group() {
        return group;
    }

    /**
     * Returns the locked flag.
     *
     * @return {@code true} to lock the group
     */
    public boolean locked() {
        return locked;
    }

    /**
     * Returns the announcement flag.
     *
     * @return {@code true} to enable announcement mode
     */
    public boolean announcement() {
        return announcement;
    }

    /**
     * Returns the no-frequently-forwarded flag.
     *
     * @return {@code true} to disable the frequently-forwarded badge
     */
    public boolean noFrequentlyForwarded() {
        return noFrequentlyForwarded;
    }

    /**
     * Returns the optional ephemeral-expiration window.
     *
     * @return an {@link OptionalInt} carrying the expiration in seconds,
     *         or empty when unset
     */
    public OptionalInt ephemeralExpiration() {
        return ephemeralExpiration == null ? OptionalInt.empty() : OptionalInt.of(ephemeralExpiration);
    }

    /**
     * Returns the optional ephemeral-trigger code.
     *
     * @return an {@link OptionalInt} carrying the trigger code, or
     *         empty when unset
     */
    public OptionalInt ephemeralTrigger() {
        return ephemeralTrigger == null ? OptionalInt.empty() : OptionalInt.of(ephemeralTrigger);
    }

    /**
     * Returns the unlocked flag.
     *
     * @return {@code true} to unlock the group
     */
    public boolean unlocked() {
        return unlocked;
    }

    /**
     * Returns the not-announcement flag.
     *
     * @return {@code true} to disable announcement mode
     */
    public boolean notAnnouncement() {
        return notAnnouncement;
    }

    /**
     * Returns the frequently-forwarded-OK flag.
     *
     * @return {@code true} to restore the frequently-forwarded badge
     */
    public boolean frequentlyForwardedOk() {
        return frequentlyForwardedOk;
    }

    /**
     * Returns the not-ephemeral flag.
     *
     * @return {@code true} to disable ephemeral messages
     */
    public boolean notEphemeral() {
        return notEphemeral;
    }

    /**
     * Returns the optional membership-approval join-mode code.
     *
     * @return an {@link Optional} carrying the join-mode code, or empty
     *         when unset
     */
    public Optional<String> membershipApprovalGroupJoinMode() {
        return Optional.ofNullable(membershipApprovalGroupJoinMode);
    }

    /**
     * Returns the allow-admin-reports flag.
     *
     * @return {@code true} to allow admin reports
     */
    public boolean allowAdminReports() {
        return allowAdminReports;
    }

    /**
     * Returns the not-allow-admin-reports flag.
     *
     * @return {@code true} to disable admin reports
     */
    public boolean notAllowAdminReports() {
        return notAllowAdminReports;
    }

    /**
     * Returns the allow-non-admin-sub-group-creation flag.
     *
     * @return {@code true} to allow non-admin sub-group creation
     */
    public boolean allowNonAdminSubGroupCreation() {
        return allowNonAdminSubGroupCreation;
    }

    /**
     * Returns the not-allow-non-admin-sub-group-creation flag.
     *
     * @return {@code true} to disable non-admin sub-group creation
     */
    public boolean notAllowNonAdminSubGroupCreation() {
        return notAllowNonAdminSubGroupCreation;
    }

    /**
     * Returns the group-history flag.
     *
     * @return {@code true} to enable group history
     */
    public boolean groupHistory() {
        return groupHistory;
    }

    /**
     * Returns the no-group-history flag.
     *
     * @return {@code true} to disable group history
     */
    public boolean noGroupHistory() {
        return noGroupHistory;
    }

    /**
     * Returns the optional new subject. When present, the editor must
     * dispatch a {@code w:g2} {@code <subject>} IQ.
     *
     * @return an {@link Optional} carrying the new subject, or empty
     *         when the subject should be left unchanged
     */
    public Optional<String> subject() {
        return Optional.ofNullable(subject);
    }

    /**
     * Returns the optional description-edit intent. When present, the
     * editor must dispatch a {@code w:g2} {@code <description>} IQ
     * keyed on the underlying variant.
     *
     * @return an {@link Optional} carrying the
     *         {@link GroupDescription} intent, or empty when the
     *         description should be left unchanged
     */
    public Optional<GroupDescription> description() {
        return Optional.ofNullable(description);
    }

    /**
     * Returns the optional picture-edit intent. When present, the
     * editor must dispatch a {@code w:profile:picture} IQ keyed on the
     * underlying variant.
     *
     * @return an {@link Optional} carrying the {@link GroupPicture}
     *         intent, or empty when the picture should be left
     *         unchanged
     */
    public Optional<GroupPicture> picture() {
        return Optional.ofNullable(picture);
    }

    /**
     * Returns the optional new status-muted flag. When present, the
     * editor must merge this value into the in-memory
     * {@link GroupMetadata#statusMuted()} field for the target group;
     * no network packet is produced.
     *
     * @return an {@link Optional} carrying the new flag, or empty when
     *         the flag should be left unchanged
     */
    public Optional<Boolean> statusMuted() {
        return Optional.ofNullable(statusMuted);
    }

    /**
     * Returns the limit-sharing-enabled flag.
     *
     * @return {@code true} to enable the per-chat sharing limit
     */
    public boolean limitSharingEnabled() {
        return limitSharingEnabled;
    }

    /**
     * Returns the limit-sharing-disabled flag.
     *
     * @return {@code true} to disable the per-chat sharing limit
     */
    public boolean limitSharingDisabled() {
        return limitSharingDisabled;
    }

    /**
     * Returns the member-add-admin-only flag.
     *
     * @return {@code true} to restrict member-add to admins
     */
    public boolean memberAddAdminOnly() {
        return memberAddAdminOnly;
    }

    /**
     * Returns the member-add-all-member flag.
     *
     * @return {@code true} to open member-add to all members
     */
    public boolean memberAddAllMember() {
        return memberAddAllMember;
    }

    /**
     * Returns the member-link-admin-only flag.
     *
     * @return {@code true} to restrict invite-link sharing to admins
     */
    public boolean memberLinkAdminOnly() {
        return memberLinkAdminOnly;
    }

    /**
     * Returns the member-link-all-member flag.
     *
     * @return {@code true} to open invite-link sharing to all members
     */
    public boolean memberLinkAllMember() {
        return memberLinkAllMember;
    }

    /**
     * Returns the member-share-group-history-admin-only flag.
     *
     * @return {@code true} to restrict history sharing to admins
     */
    public boolean memberShareGroupHistoryAdminOnly() {
        return memberShareGroupHistoryAdminOnly;
    }

    /**
     * Returns the member-share-group-history-all-member flag.
     *
     * @return {@code true} to open history sharing to all members
     */
    public boolean memberShareGroupHistoryAllMember() {
        return memberShareGroupHistoryAllMember;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (GroupMetadataEdit) obj;
        return Objects.equals(group, that.group) &&
                locked == that.locked &&
                announcement == that.announcement &&
                noFrequentlyForwarded == that.noFrequentlyForwarded &&
                Objects.equals(ephemeralExpiration, that.ephemeralExpiration) &&
                Objects.equals(ephemeralTrigger, that.ephemeralTrigger) &&
                unlocked == that.unlocked &&
                notAnnouncement == that.notAnnouncement &&
                frequentlyForwardedOk == that.frequentlyForwardedOk &&
                notEphemeral == that.notEphemeral &&
                Objects.equals(membershipApprovalGroupJoinMode, that.membershipApprovalGroupJoinMode) &&
                allowAdminReports == that.allowAdminReports &&
                notAllowAdminReports == that.notAllowAdminReports &&
                allowNonAdminSubGroupCreation == that.allowNonAdminSubGroupCreation &&
                notAllowNonAdminSubGroupCreation == that.notAllowNonAdminSubGroupCreation &&
                groupHistory == that.groupHistory &&
                noGroupHistory == that.noGroupHistory &&
                Objects.equals(subject, that.subject) &&
                Objects.equals(description, that.description) &&
                Objects.equals(picture, that.picture) &&
                Objects.equals(statusMuted, that.statusMuted) &&
                limitSharingEnabled == that.limitSharingEnabled &&
                limitSharingDisabled == that.limitSharingDisabled &&
                memberAddAdminOnly == that.memberAddAdminOnly &&
                memberAddAllMember == that.memberAddAllMember &&
                memberLinkAdminOnly == that.memberLinkAdminOnly &&
                memberLinkAllMember == that.memberLinkAllMember &&
                memberShareGroupHistoryAdminOnly == that.memberShareGroupHistoryAdminOnly &&
                memberShareGroupHistoryAllMember == that.memberShareGroupHistoryAllMember;
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, locked, announcement, noFrequentlyForwarded, ephemeralExpiration,
                ephemeralTrigger, unlocked, notAnnouncement, frequentlyForwardedOk, notEphemeral,
                membershipApprovalGroupJoinMode, allowAdminReports, notAllowAdminReports,
                allowNonAdminSubGroupCreation, notAllowNonAdminSubGroupCreation, groupHistory,
                noGroupHistory, subject, description, picture, statusMuted,
                limitSharingEnabled, limitSharingDisabled, memberAddAdminOnly, memberAddAllMember,
                memberLinkAdminOnly, memberLinkAllMember, memberShareGroupHistoryAdminOnly,
                memberShareGroupHistoryAllMember);
    }

    @Override
    public String toString() {
        return "GroupMetadataEdit[" +
                "group=" + group + ", " +
                "locked=" + locked + ", " +
                "announcement=" + announcement + ", " +
                "noFrequentlyForwarded=" + noFrequentlyForwarded + ", " +
                "ephemeralExpiration=" + ephemeralExpiration + ", " +
                "ephemeralTrigger=" + ephemeralTrigger + ", " +
                "unlocked=" + unlocked + ", " +
                "notAnnouncement=" + notAnnouncement + ", " +
                "frequentlyForwardedOk=" + frequentlyForwardedOk + ", " +
                "notEphemeral=" + notEphemeral + ", " +
                "membershipApprovalGroupJoinMode=" + membershipApprovalGroupJoinMode + ", " +
                "allowAdminReports=" + allowAdminReports + ", " +
                "notAllowAdminReports=" + notAllowAdminReports + ", " +
                "allowNonAdminSubGroupCreation=" + allowNonAdminSubGroupCreation + ", " +
                "notAllowNonAdminSubGroupCreation=" + notAllowNonAdminSubGroupCreation + ", " +
                "groupHistory=" + groupHistory + ", " +
                "noGroupHistory=" + noGroupHistory + ", " +
                "subject=" + subject + ", " +
                "description=" + description + ", " +
                "picture=" + picture + ", " +
                "statusMuted=" + statusMuted + ", " +
                "limitSharingEnabled=" + limitSharingEnabled + ", " +
                "limitSharingDisabled=" + limitSharingDisabled + ", " +
                "memberAddAdminOnly=" + memberAddAdminOnly + ", " +
                "memberAddAllMember=" + memberAddAllMember + ", " +
                "memberLinkAdminOnly=" + memberLinkAdminOnly + ", " +
                "memberLinkAllMember=" + memberLinkAllMember + ", " +
                "memberShareGroupHistoryAdminOnly=" + memberShareGroupHistoryAdminOnly + ", " +
                "memberShareGroupHistoryAllMember=" + memberShareGroupHistoryAllMember + ']';
    }
}
