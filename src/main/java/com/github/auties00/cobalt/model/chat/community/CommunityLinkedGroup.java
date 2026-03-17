package com.github.auties00.cobalt.model.chat.community;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A snapshot of a subgroup that is linked to a WhatsApp community.
 *
 * <p>Every community (parent group) maintains an ordered set of linked
 * subgroups. Each subgroup is represented by its {@link Jid} and a set of
 * metadata properties that describe its state. This class captures those
 * values as they appear in the protobuf wire format exchanged with the
 * WhatsApp servers.
 *
 * <p>In the WhatsApp Web client the same data is split across two models.
 * Subgroups the user has already joined are tracked by the
 * {@code WAWebGroupMetadataModel} collection, while subgroups the user has
 * not yet joined are tracked by {@code WAWebUnjoinedSubgroupMetadataModel}.
 * Both models expose a {@code size} property that corresponds to the
 * {@link #participantCount()} field of this class.
 *
 * <p>Instances of this class are mutable. All fields can be changed after
 * construction through the fluent setter methods, each of which returns
 * the same instance for method chaining.
 *
 * @see CommunityMetadata#communityGroups()
 */
@ProtobufMessage(name = "CommunityLinkedGroup")
public final class CommunityLinkedGroup {
    /**
     * The JID that uniquely identifies the linked subgroup.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid jid;

    /**
     * The number of participants currently in the linked subgroup, or
     * {@code null} if the participant count is not available.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.UINT32)
    Integer participantCount;

    /**
     * The display name (subject) of this linked subgroup, or {@code null}
     * if the subject is not available. In the WhatsApp Web client this
     * corresponds to the {@code subject} property of the
     * {@code WAWebUnjoinedSubgroupMetadataModel}.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String subject;

    /**
     * The instant at which the subject was last changed, or {@code null}
     * if the timestamp is not available. In the WhatsApp Web client this
     * corresponds to the {@code subjectTime} property.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant subjectTimestamp;

    /**
     * The JID of the parent community this subgroup belongs to, or
     * {@code null} if not available. In the WhatsApp Web client this
     * corresponds to the {@code parentGroupId} property.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    Jid parentGroupJid;

    /**
     * Whether this subgroup is the default announcement subgroup of the
     * parent community. In the WhatsApp Web client this corresponds to the
     * {@code defaultSubgroup} property and identifies the subgroup as a
     * {@code LINKED_ANNOUNCEMENT_GROUP}.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.BOOL)
    boolean defaultSubgroup;

    /**
     * Whether this subgroup is the general chat subgroup of the parent
     * community. In the WhatsApp Web client this corresponds to the
     * {@code generalSubgroup} property and identifies the subgroup as a
     * {@code LINKED_GENERAL_GROUP}.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.BOOL)
    boolean generalSubgroup;

    /**
     * The description text of this linked subgroup, or {@code null} if no
     * description has been set. In the WhatsApp Web client this corresponds
     * to the {@code desc} property.
     */
    @ProtobufProperty(index = 8, type = ProtobufType.STRING)
    String description;

    /**
     * The instant at which this linked subgroup was created, or
     * {@code null} if the timestamp is not available. In the WhatsApp Web
     * client this corresponds to the {@code creation} property.
     */
    @ProtobufProperty(index = 9, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant creationTimestamp;

    /**
     * The JID of the user who originally created this subgroup, or
     * {@code null} if not known. In the WhatsApp Web client this corresponds
     * to the {@code owner} property.
     */
    @ProtobufProperty(index = 10, type = ProtobufType.STRING)
    Jid ownerJid;

    /**
     * Whether admin approval is required for new members to join this
     * subgroup. In the WhatsApp Web client this corresponds to the
     * {@code membershipApprovalMode} property.
     */
    @ProtobufProperty(index = 11, type = ProtobufType.BOOL)
    boolean membershipApprovalMode;

    /**
     * Whether this subgroup is hidden from the community's subgroup list.
     * In the WhatsApp Web client this corresponds to the
     * {@code hiddenSubgroup} property.
     */
    @ProtobufProperty(index = 12, type = ProtobufType.BOOL)
    boolean hiddenSubgroup;

    /**
     * Whether this subgroup has been suspended. A suspended subgroup cannot
     * be interacted with until it is restored. In the WhatsApp Web client
     * this corresponds to the {@code suspended} property.
     */
    @ProtobufProperty(index = 13, type = ProtobufType.BOOL)
    boolean suspended;

    /**
     * Constructs a new {@code CommunityLinkedGroup} with the specified
     * values.
     *
     * @param jid                    the JID of the linked subgroup
     * @param participantCount       the number of participants, or
     *                               {@code null} if unknown
     * @param subject                the display name, or {@code null}
     * @param subjectTimestamp       the instant at which the subject was
     *                               last changed, or {@code null}
     * @param parentGroupJid         the parent community JID, or
     *                               {@code null}
     * @param defaultSubgroup        whether this is the default
     *                               announcement subgroup
     * @param generalSubgroup        whether this is the general chat
     *                               subgroup
     * @param description            the description text, or {@code null}
     * @param creationTimestamp      the instant at which this subgroup was
     *                               created, or {@code null}
     * @param ownerJid               the creator JID, or {@code null}
     * @param membershipApprovalMode whether admin approval is required
     * @param hiddenSubgroup         whether this subgroup is hidden
     * @param suspended              whether this subgroup is suspended
     */
    CommunityLinkedGroup(
            Jid jid,
            Integer participantCount,
            String subject,
            Instant subjectTimestamp,
            Jid parentGroupJid,
            boolean defaultSubgroup,
            boolean generalSubgroup,
            String description,
            Instant creationTimestamp,
            Jid ownerJid,
            boolean membershipApprovalMode,
            boolean hiddenSubgroup,
            boolean suspended
    ) {
        this.jid = jid;
        this.participantCount = participantCount;
        this.subject = subject;
        this.subjectTimestamp = subjectTimestamp;
        this.parentGroupJid = parentGroupJid;
        this.defaultSubgroup = defaultSubgroup;
        this.generalSubgroup = generalSubgroup;
        this.description = description;
        this.creationTimestamp = creationTimestamp;
        this.ownerJid = ownerJid;
        this.membershipApprovalMode = membershipApprovalMode;
        this.hiddenSubgroup = hiddenSubgroup;
        this.suspended = suspended;
    }

    /**
     * Returns the JID that uniquely identifies this linked subgroup.
     *
     * @return the subgroup JID
     */
    public Jid jid() {
        return jid;
    }

    /**
     * Sets the JID that identifies this linked subgroup.
     *
     * @param jid the subgroup JID
     */
    public void setJid(Jid jid) {
        this.jid = jid;
    }

    /**
     * Returns the number of participants currently in this linked subgroup,
     * if available. The count may be absent when the server did not include
     * participant information in the response.
     *
     * @return an {@code OptionalInt} containing the participant count, or
     *         empty if the count is not available
     */
    public OptionalInt participantCount() {
        return participantCount == null
                ? OptionalInt.empty()
                : OptionalInt.of(participantCount);
    }

    /**
     * Sets the number of participants in this linked subgroup.
     *
     * @param participantCount the participant count, or {@code null} to
     *                         clear the value
     */
    public void setParticipantCount(Integer participantCount) {
        this.participantCount = participantCount;
    }

    /**
     * Returns the display name (subject) of this linked subgroup, if
     * available.
     *
     * @return an {@code Optional} containing the subject, or empty if not
     *         available
     */
    public Optional<String> subject() {
        return Optional.ofNullable(subject);
    }

    /**
     * Sets the display name (subject) of this linked subgroup.
     *
     * @param subject the subject text, or {@code null} to clear
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * Returns the instant at which the subject was last changed, if known.
     *
     * @return an {@code Optional} containing the subject timestamp, or
     *         empty if not available
     */
    public Optional<Instant> subjectTimestamp() {
        return Optional.ofNullable(subjectTimestamp);
    }

    /**
     * Sets the instant at which the subject was last changed.
     *
     * @param subjectTimestamp the subject change timestamp, or {@code null}
     *                        to clear
     */
    public void setSubjectTimestamp(Instant subjectTimestamp) {
        this.subjectTimestamp = subjectTimestamp;
    }

    /**
     * Returns the JID of the parent community this subgroup belongs to, if
     * available.
     *
     * @return an {@code Optional} containing the parent community JID, or
     *         empty if not available
     */
    public Optional<Jid> parentGroupJid() {
        return Optional.ofNullable(parentGroupJid);
    }

    /**
     * Sets the JID of the parent community this subgroup belongs to.
     *
     * @param parentGroupJid the parent community JID, or {@code null} to
     *                       clear
     */
    public void setParentGroupJid(Jid parentGroupJid) {
        this.parentGroupJid = parentGroupJid;
    }

    /**
     * Returns whether this subgroup is the default announcement subgroup
     * of the parent community.
     *
     * @return {@code true} if this is the default subgroup
     */
    public boolean isDefaultSubgroup() {
        return defaultSubgroup;
    }

    /**
     * Sets whether this subgroup is the default announcement subgroup.
     *
     * @param defaultSubgroup {@code true} to mark as default subgroup
     */
    public void setDefaultSubgroup(boolean defaultSubgroup) {
        this.defaultSubgroup = defaultSubgroup;
    }

    /**
     * Returns whether this subgroup is the general chat subgroup of the
     * parent community.
     *
     * @return {@code true} if this is the general chat subgroup
     */
    public boolean isGeneralSubgroup() {
        return generalSubgroup;
    }

    /**
     * Sets whether this subgroup is the general chat subgroup.
     *
     * @param generalSubgroup {@code true} to mark as general subgroup
     */
    public void setGeneralSubgroup(boolean generalSubgroup) {
        this.generalSubgroup = generalSubgroup;
    }

    /**
     * Returns the description text of this linked subgroup, if one has been
     * set.
     *
     * @return an {@code Optional} containing the description, or empty if
     *         not set
     */
    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    /**
     * Sets the description text of this linked subgroup.
     *
     * @param description the description text, or {@code null} to clear
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the instant at which this subgroup was created, if known.
     *
     * @return an {@code Optional} containing the creation timestamp, or
     *         empty if not available
     */
    public Optional<Instant> creationTimestamp() {
        return Optional.ofNullable(creationTimestamp);
    }

    /**
     * Sets the instant at which this subgroup was created.
     *
     * @param creationTimestamp the creation timestamp, or {@code null} to
     *                         clear
     */
    public void setCreationTimestamp(Instant creationTimestamp) {
        this.creationTimestamp = creationTimestamp;
    }

    /**
     * Returns the JID of the user who originally created this subgroup, if
     * known.
     *
     * @return an {@code Optional} containing the owner JID, or empty if
     *         not known
     */
    public Optional<Jid> ownerJid() {
        return Optional.ofNullable(ownerJid);
    }

    /**
     * Sets the JID of the user who originally created this subgroup.
     *
     * @param ownerJid the creator JID, or {@code null} to clear
     */
    public void setOwnerJid(Jid ownerJid) {
        this.ownerJid = ownerJid;
    }

    /**
     * Returns whether admin approval is required for new members to join
     * this subgroup.
     *
     * @return {@code true} if membership approval mode is enabled
     */
    public boolean isMembershipApprovalMode() {
        return membershipApprovalMode;
    }

    /**
     * Sets whether admin approval is required for new members to join
     * this subgroup.
     *
     * @param membershipApprovalMode {@code true} to enable membership
     *                               approval mode
     */
    public void setMembershipApprovalMode(boolean membershipApprovalMode) {
        this.membershipApprovalMode = membershipApprovalMode;
    }

    /**
     * Returns whether this subgroup is hidden from the community's subgroup
     * list.
     *
     * @return {@code true} if this subgroup is hidden
     */
    public boolean isHiddenSubgroup() {
        return hiddenSubgroup;
    }

    /**
     * Sets whether this subgroup is hidden from the community's subgroup
     * list.
     *
     * @param hiddenSubgroup {@code true} to mark as hidden
     */
    public void setHiddenSubgroup(boolean hiddenSubgroup) {
        this.hiddenSubgroup = hiddenSubgroup;
    }

    /**
     * Returns whether this subgroup has been suspended.
     *
     * @return {@code true} if this subgroup is suspended
     */
    public boolean isSuspended() {
        return suspended;
    }

    /**
     * Sets whether this subgroup has been suspended.
     *
     * @param suspended {@code true} to mark as suspended
     */
    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }
}
