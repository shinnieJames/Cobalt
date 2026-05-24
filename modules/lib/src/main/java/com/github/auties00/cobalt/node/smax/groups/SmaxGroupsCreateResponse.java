package com.github.auties00.cobalt.node.smax.groups;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed family of inbound reply variants produced by the relay in response to a
 * {@link SmaxGroupsCreateRequest}.
 *
 * @apiNote
 * Pattern-match the result returned by {@link #of(Node, Node)} to drive group-create surfaces equivalent to
 * WA Web's {@code WAWebGroupCreateJob} / {@code WAWebGroupCommunityJob} switch: {@link Success} carries the
 * provisioned group's metadata and seed participants, {@link GroupAlreadyExists} surfaces the dedup-driven
 * "this creator+token tuple already mapped to a group" path that WA Web rejects with
 * {@code GroupAlreadyExistsError}, and the two error variants surface caller-side and relay-side failures.
 */
public sealed interface SmaxGroupsCreateResponse extends SmaxOperation.Response
        permits SmaxGroupsCreateResponse.Success, SmaxGroupsCreateResponse.GroupAlreadyExists,
                SmaxGroupsCreateResponse.ClientError, SmaxGroupsCreateResponse.ServerError {

    /**
     * Parses the inbound IQ stanza into the first matching {@link SmaxGroupsCreateResponse} variant.
     *
     * @apiNote
     * Mirrors WA Web's {@code WASmaxGroupsCreateRPC.sendCreateRPC} fall-through cascade: {@link Success},
     * {@link GroupAlreadyExists}, {@link ClientError}, {@link ServerError}. An empty {@link Optional}
     * signals a stanza shape outside the documented union.
     *
     * @implNote
     * This implementation runs the variant probes in the same priority order as WA Web; it does not throw a
     * parsing-failure exception, leaving the recovery decision to the caller.
     *
     * @param node    the inbound IQ stanza received from the relay; never {@code null}
     * @param request the original outbound {@link SmaxGroupsCreateRequest} stanza; used to validate the
     *                echoed {@code id} attribute; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()} when no
     *         documented variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxGroupsCreateRPC",
            exports = "sendCreateRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxGroupsCreateResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var success = Success.of(node, request);
        if (success.isPresent()) {
            return success;
        }
        var alreadyExists = GroupAlreadyExists.of(node, request);
        if (alreadyExists.isPresent()) {
            return alreadyExists;
        }
        var clientError = ClientError.of(node, request);
        if (clientError.isPresent()) {
            return clientError;
        }
        return ServerError.of(node, request);
    }

    /**
     * The success variant returned when the relay materialised the new group and echoed its provisioned
     * metadata.
     *
     * @apiNote
     * Carries the typed identity triple ({@link #groupId()}, {@link #groupCreator()},
     * {@link #groupCreation()}), the optional sync-token and sync-owner pair, every echoed
     * {@code <group/>} policy marker, and the non-empty list of seed-participant rows. WA Web's
     * {@code WAWebGroupCreateJob} forwards the equivalent payload as a {@code {wid, subject, creator, ts,
     * participants, invitedOutContacts}} object after running the {@code GroupCreateCWamEvent} hook.
     *
     * @implNote
     * This implementation surfaces the raw {@code <group/>} sub-node via {@link #group()} so callers can
     * read mixin metadata (addressing mode, subject-owner identity, member-add / link / share-history
     * mixins, dedup attribute echo) that Cobalt does not project as typed accessors.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsCreateResponseSuccess")
    final class Success implements SmaxGroupsCreateResponse {
        /**
         * The new group's user-component id (the portion of the JID preceding {@code @g.us}).
         */
        private final String groupId;

        /**
         * The fully-qualified group {@link Jid}, derived from {@code <groupId>@g.us}.
         */
        private final Jid groupJid;

        /**
         * The creator {@link Jid} that initiated the group.
         */
        private final Jid groupCreator;

        /**
         * The creation timestamp in seconds since epoch.
         */
        private final long groupCreation;

        /**
         * The optional {@code s_t} sync-time mixin echoed by the relay; {@code null} when omitted.
         */
        private final Long groupSyncTime;

        /**
         * The optional {@code s_o} sync-owner {@link Jid} echoed by the relay; {@code null} when omitted.
         */
        private final Jid groupSyncOwner;

        /**
         * The group's subject (display name) echoed by the relay.
         */
        private final String subject;

        /**
         * The optional description id echoed inside the {@code <description id="...">} attribute;
         * {@code null} when the relay did not commit a description.
         */
        private final String descriptionId;

        /**
         * The optional description-error string echoed inside the {@code <description error="...">}
         * attribute (e.g. {@code "406"} or {@code "500"}); {@code null} when the description committed
         * cleanly.
         */
        private final String descriptionError;

        /**
         * Whether the relay echoed a {@code <locked/>} child marking chat-info edits admin-only.
         */
        private final boolean locked;

        /**
         * Whether the relay echoed an {@code <announcement/>} child restricting posting to admins.
         */
        private final boolean announcement;

        /**
         * Whether the relay echoed a {@code <parent/>} child marking the new group as a community parent.
         */
        private final boolean parent;

        /**
         * Whether the relay echoed a {@code <no_frequently_forwarded/>} child.
         */
        private final boolean noFrequentlyForwarded;

        /**
         * The optional {@code <ephemeral expiration="...">} attribute echoed by the relay; {@code null} when
         * no ephemeral expiration was committed.
         */
        private final Integer ephemeralExpiration;

        /**
         * The optional {@code <ephemeral trigger="...">} attribute echoed by the relay; {@code null} when
         * omitted.
         */
        private final Integer ephemeralTrigger;

        /**
         * Whether the relay echoed a {@code <membership_approval_mode/>} child.
         */
        private final boolean membershipApprovalMode;

        /**
         * Whether the relay echoed a {@code <breakout/>} child marking the group as a breakout sub-group.
         */
        private final boolean breakout;

        /**
         * The optional linked parent community {@link Jid} echoed inside a
         * {@code <linked_parent jid="...">} child; {@code null} when the new group has no parent.
         */
        private final Jid linkedParentJid;

        /**
         * Whether the relay echoed a {@code <hidden_group/>} child hiding the group from the community
         * directory.
         */
        private final boolean hiddenGroup;

        /**
         * Whether the relay echoed an {@code <allow_non_admin_sub_group_creation/>} child.
         */
        private final boolean allowNonAdminSubGroupCreation;

        /**
         * Whether the relay echoed a {@code <group_history/>} child.
         */
        private final boolean groupHistory;

        /**
         * Whether the relay echoed a {@code <capi/>} child.
         */
        private final boolean capi;

        /**
         * The non-empty list of seed-participant echo rows, one per requested participant.
         */
        private final List<ResponseParticipant> participants;

        /**
         * The verbatim {@code <group/>} child exposed for callers that need to read mixin metadata
         * (addressing mode, subject-owner identity, member-add / link / share-history mixins, dedup attribute
         * echo) that Cobalt does not project as typed accessors.
         */
        private final Node group;

        /**
         * Constructs a success variant.
         *
         * @apiNote
         * Typically produced by {@link #of(Node, Node)}; direct construction is used to seed test fixtures.
         *
         * @param groupId                       the user-component id; never {@code null}
         * @param groupJid                      the full group {@link Jid}; never {@code null}
         * @param groupCreator                  the creator {@link Jid}; never {@code null}
         * @param groupCreation                 the creation timestamp in seconds since epoch
         * @param groupSyncTime                 the optional {@code s_t} mixin; may be {@code null}
         * @param groupSyncOwner                the optional {@code s_o} mixin; may be {@code null}
         * @param subject                       the subject; never {@code null}
         * @param descriptionId                 the optional description id; may be {@code null}
         * @param descriptionError              the optional description-error string; may be {@code null}
         * @param locked                        whether {@code <locked/>} was echoed
         * @param announcement                  whether {@code <announcement/>} was echoed
         * @param parent                        whether {@code <parent/>} was echoed
         * @param noFrequentlyForwarded         whether {@code <no_frequently_forwarded/>} was echoed
         * @param ephemeralExpiration           the optional ephemeral expiration; may be {@code null}
         * @param ephemeralTrigger              the optional ephemeral trigger; may be {@code null}
         * @param membershipApprovalMode        whether {@code <membership_approval_mode/>} was echoed
         * @param breakout                      whether {@code <breakout/>} was echoed
         * @param linkedParentJid               the optional linked parent community JID; may be {@code null}
         * @param hiddenGroup                   whether {@code <hidden_group/>} was echoed
         * @param allowNonAdminSubGroupCreation whether {@code <allow_non_admin_sub_group_creation/>} was
         *                                      echoed
         * @param groupHistory                  whether {@code <group_history/>} was echoed
         * @param capi                          whether {@code <capi/>} was echoed
         * @param participants                  the seed-participant rows; never {@code null} and must be
         *                                      non-empty
         * @param group                         the raw {@code <group/>} sub-node; never {@code null}
         * @throws NullPointerException     if any non-nullable argument is {@code null}
         * @throws IllegalArgumentException when {@code participants} is empty
         */
        public Success(String groupId,
                       Jid groupJid,
                       Jid groupCreator,
                       long groupCreation,
                       Long groupSyncTime,
                       Jid groupSyncOwner,
                       String subject,
                       String descriptionId,
                       String descriptionError,
                       boolean locked,
                       boolean announcement,
                       boolean parent,
                       boolean noFrequentlyForwarded,
                       Integer ephemeralExpiration,
                       Integer ephemeralTrigger,
                       boolean membershipApprovalMode,
                       boolean breakout,
                       Jid linkedParentJid,
                       boolean hiddenGroup,
                       boolean allowNonAdminSubGroupCreation,
                       boolean groupHistory,
                       boolean capi,
                       List<ResponseParticipant> participants,
                       Node group) {
            this.groupId = Objects.requireNonNull(groupId, "groupId cannot be null");
            this.groupJid = Objects.requireNonNull(groupJid, "groupJid cannot be null");
            this.groupCreator = Objects.requireNonNull(groupCreator, "groupCreator cannot be null");
            this.groupCreation = groupCreation;
            this.groupSyncTime = groupSyncTime;
            this.groupSyncOwner = groupSyncOwner;
            this.subject = Objects.requireNonNull(subject, "subject cannot be null");
            this.descriptionId = descriptionId;
            this.descriptionError = descriptionError;
            this.locked = locked;
            this.announcement = announcement;
            this.parent = parent;
            this.noFrequentlyForwarded = noFrequentlyForwarded;
            this.ephemeralExpiration = ephemeralExpiration;
            this.ephemeralTrigger = ephemeralTrigger;
            this.membershipApprovalMode = membershipApprovalMode;
            this.breakout = breakout;
            this.linkedParentJid = linkedParentJid;
            this.hiddenGroup = hiddenGroup;
            this.allowNonAdminSubGroupCreation = allowNonAdminSubGroupCreation;
            this.groupHistory = groupHistory;
            this.capi = capi;
            Objects.requireNonNull(participants, "participants cannot be null");
            if (participants.isEmpty()) {
                throw new IllegalArgumentException("participants must contain at least one entry");
            }
            this.participants = List.copyOf(participants);
            this.group = Objects.requireNonNull(group, "group cannot be null");
        }

        /**
         * Returns the new group's user-component id.
         *
         * @apiNote
         * Concatenate with {@code @g.us} to recover the fully-qualified group JID; the same value is exposed
         * pre-built via {@link #groupJid()}.
         *
         * @return the group id; never {@code null}
         */
        public String groupId() {
            return groupId;
        }

        /**
         * Returns the fully-qualified group {@link Jid}.
         *
         * @return the group JID; never {@code null}
         */
        public Jid groupJid() {
            return groupJid;
        }

        /**
         * Returns the creator {@link Jid}.
         *
         * @return the creator JID; never {@code null}
         */
        public Jid groupCreator() {
            return groupCreator;
        }

        /**
         * Returns the creation timestamp.
         *
         * @return the creation timestamp in seconds since epoch
         */
        public long groupCreation() {
            return groupCreation;
        }

        /**
         * Returns the optional {@code s_t} sync-time mixin value.
         *
         * @return an {@link Optional} carrying the value, or empty when omitted
         */
        public Optional<Long> groupSyncTime() {
            return Optional.ofNullable(groupSyncTime);
        }

        /**
         * Returns the optional {@code s_o} sync-owner {@link Jid}.
         *
         * @return an {@link Optional} carrying the JID, or empty when omitted
         */
        public Optional<Jid> groupSyncOwner() {
            return Optional.ofNullable(groupSyncOwner);
        }

        /**
         * Returns the subject (display name).
         *
         * @return the subject; never {@code null}
         */
        public String subject() {
            return subject;
        }

        /**
         * Returns the optional description id.
         *
         * @return an {@link Optional} carrying the id, or empty when the relay did not commit a description
         */
        public Optional<String> descriptionId() {
            return Optional.ofNullable(descriptionId);
        }

        /**
         * Returns the optional description-error string.
         *
         * @apiNote
         * Non-empty when the group was created but the description body was rejected (e.g. {@code "406"} or
         * {@code "500"}); callers should surface a partial-acceptance UI rather than treating the whole
         * operation as failed.
         *
         * @return an {@link Optional} carrying the error string, or empty when the description committed
         *         cleanly
         */
        public Optional<String> descriptionError() {
            return Optional.ofNullable(descriptionError);
        }

        /**
         * Returns whether the relay echoed the {@code <locked/>} marker.
         *
         * @return {@code true} when chat-info edits are admin-only
         */
        public boolean locked() {
            return locked;
        }

        /**
         * Returns whether the relay echoed the {@code <announcement/>} marker.
         *
         * @return {@code true} when posting is restricted to admins
         */
        public boolean announcement() {
            return announcement;
        }

        /**
         * Returns whether the relay echoed the {@code <parent/>} marker.
         *
         * @return {@code true} when the new group is a community parent
         */
        public boolean parent() {
            return parent;
        }

        /**
         * Returns whether the relay echoed the {@code <no_frequently_forwarded/>} marker.
         *
         * @return {@code true} when the marker is present
         */
        public boolean noFrequentlyForwarded() {
            return noFrequentlyForwarded;
        }

        /**
         * Returns the optional {@code <ephemeral expiration="...">} value.
         *
         * @return an {@link Optional} carrying the value in seconds, or empty when no ephemeral expiration
         *         was committed
         */
        public Optional<Integer> ephemeralExpiration() {
            return Optional.ofNullable(ephemeralExpiration);
        }

        /**
         * Returns the optional {@code <ephemeral trigger="...">} value.
         *
         * @return an {@link Optional} carrying the value, or empty when omitted
         */
        public Optional<Integer> ephemeralTrigger() {
            return Optional.ofNullable(ephemeralTrigger);
        }

        /**
         * Returns whether the relay echoed the {@code <membership_approval_mode/>} marker.
         *
         * @return {@code true} when the marker is present
         */
        public boolean membershipApprovalMode() {
            return membershipApprovalMode;
        }

        /**
         * Returns whether the relay echoed the {@code <breakout/>} marker.
         *
         * @return {@code true} when the new group is a breakout sub-group
         */
        public boolean breakout() {
            return breakout;
        }

        /**
         * Returns the optional linked parent community {@link Jid}.
         *
         * @return an {@link Optional} carrying the JID, or empty when the new group has no parent community
         */
        public Optional<Jid> linkedParentJid() {
            return Optional.ofNullable(linkedParentJid);
        }

        /**
         * Returns whether the relay echoed the {@code <hidden_group/>} marker.
         *
         * @return {@code true} when the new group is hidden from the community directory
         */
        public boolean hiddenGroup() {
            return hiddenGroup;
        }

        /**
         * Returns whether the relay echoed the {@code <allow_non_admin_sub_group_creation/>} marker.
         *
         * @return {@code true} when the marker is present
         */
        public boolean allowNonAdminSubGroupCreation() {
            return allowNonAdminSubGroupCreation;
        }

        /**
         * Returns whether the relay echoed the {@code <group_history/>} marker.
         *
         * @return {@code true} when the marker is present
         */
        public boolean groupHistory() {
            return groupHistory;
        }

        /**
         * Returns whether the relay echoed the {@code <capi/>} marker.
         *
         * @return {@code true} when the marker is present
         */
        public boolean capi() {
            return capi;
        }

        /**
         * Returns the seed-participant echo rows.
         *
         * @apiNote
         * One entry per participant the relay processed; an entry's
         * {@link ResponseParticipant#notRegisteredOnWa()} discriminates between "successfully added" and
         * "non-registered WA user".
         *
         * @return an unmodifiable list of participant rows; never empty
         */
        public List<ResponseParticipant> participants() {
            return participants;
        }

        /**
         * Returns the verbatim {@code <group/>} sub-node carrying the remaining {@code GroupInfoMixin}
         * metadata.
         *
         * @apiNote
         * Exposed for callers that need to read mixin metadata (addressing mode, subject-owner identity,
         * member-add / link / share-history mixins, dedup attribute echo) that Cobalt does not project as
         * typed accessors.
         *
         * @return the raw {@code <group/>} {@link Node}; never {@code null}
         */
        public Node group() {
            return group;
        }

        /**
         * Parses the inbound stanza into a {@link Success} variant.
         *
         * @apiNote
         * Invoked as the first probe in the variant cascade by
         * {@link SmaxGroupsCreateResponse#of(Node, Node)}.
         *
         * @implNote
         * This implementation validates that the IQ envelope is an {@code <iq type="result">} addressed
         * {@code from} a {@code g.us} JID with the echoed {@code id} attribute, then extracts the
         * {@code <group/>} child and reads the mandatory identity attributes ({@code id}, {@code creator},
         * {@code creation}, {@code subject}), the optional mixin attributes ({@code s_t}, {@code s_o}), the
         * boolean-gated marker children, the optional ephemeral / description / linked-parent sub-nodes,
         * and the participant rows. Any missing mandatory attribute or unparseable participant short-circuits
         * the parse and returns {@link Optional#empty()}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()} when the
         *         stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsCreateResponseSuccess",
                exports = "parseCreateResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!node.hasDescription("iq")) {
                return Optional.empty();
            }
            if (!node.hasAttribute("type", "result")) {
                return Optional.empty();
            }
            var requestId = request.getAttributeAsString("id").orElse(null);
            if (requestId == null) {
                return Optional.empty();
            }
            if (!node.hasAttribute("id", requestId)) {
                return Optional.empty();
            }
            var fromAttr = node.getAttributeAsString("from").orElse(null);
            if (fromAttr == null || !fromAttr.endsWith("g.us")) {
                return Optional.empty();
            }
            var group = node.getChild("group").orElse(null);
            if (group == null) {
                return Optional.empty();
            }
            var groupId = group.getAttributeAsString("id").orElse(null);
            if (groupId == null) {
                return Optional.empty();
            }
            var groupCreator = group.getAttributeAsJid("creator").orElse(null);
            if (groupCreator == null) {
                return Optional.empty();
            }
            if (group.getAttributeAsLong("creation").isEmpty()) {
                return Optional.empty();
            }
            var groupCreation = group.getAttributeAsLong("creation").getAsLong();
            Long groupSyncTime = null;
            if (group.getAttributeAsLong("s_t").isPresent()) {
                groupSyncTime = group.getAttributeAsLong("s_t").getAsLong();
            }
            var groupSyncOwner = group.getAttributeAsJid("s_o").orElse(null);
            var subject = group.getAttributeAsString("subject").orElse(null);
            if (subject == null) {
                return Optional.empty();
            }
            var groupJid = Jid.of(groupId, JidServer.groupOrCommunity());
            var description = group.getChild("description").orElse(null);
            String descriptionId = null;
            String descriptionError = null;
            if (description != null) {
                descriptionId = description.getAttributeAsString("id").orElse(null);
                descriptionError = description.getAttributeAsString("error").orElse(null);
            }
            var locked = group.getChild("locked").isPresent();
            var announcement = group.getChild("announcement").isPresent();
            var parent = group.getChild("parent").isPresent();
            var noFrequentlyForwarded = group.getChild("no_frequently_forwarded").isPresent();
            Integer ephemeralExpiration = null;
            Integer ephemeralTrigger = null;
            var ephemeral = group.getChild("ephemeral").orElse(null);
            if (ephemeral != null) {
                ephemeralExpiration = ephemeral.getAttributeAsInt("expiration").orElse(0);
                if (ephemeral.getAttributeAsInt("trigger").isPresent()) {
                    ephemeralTrigger = ephemeral.getAttributeAsInt("trigger").getAsInt();
                }
            }
            var membershipApprovalMode = group.getChild("membership_approval_mode").isPresent();
            var breakout = group.getChild("breakout").isPresent();
            Jid linkedParentJid = null;
            var linkedParent = group.getChild("linked_parent").orElse(null);
            if (linkedParent != null) {
                linkedParentJid = linkedParent.getAttributeAsJid("jid").orElse(null);
            }
            var hiddenGroup = group.getChild("hidden_group").isPresent();
            var allowNonAdmin = group.getChild("allow_non_admin_sub_group_creation").isPresent();
            var groupHistory = group.getChild("group_history").isPresent();
            var capi = group.getChild("capi").isPresent();
            var participants = new ArrayList<ResponseParticipant>();
            for (var participantNode : group.getChildren("participant")) {
                var parsed = ResponseParticipant.of(participantNode).orElse(null);
                if (parsed == null) {
                    return Optional.empty();
                }
                participants.add(parsed);
            }
            if (participants.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Success(groupId, groupJid, groupCreator, groupCreation, groupSyncTime,
                    groupSyncOwner, subject, descriptionId, descriptionError, locked, announcement, parent,
                    noFrequentlyForwarded, ephemeralExpiration, ephemeralTrigger, membershipApprovalMode,
                    breakout, linkedParentJid, hiddenGroup, allowNonAdmin, groupHistory, capi, participants,
                    group));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Success) obj;
            return this.groupCreation == that.groupCreation
                    && this.locked == that.locked
                    && this.announcement == that.announcement
                    && this.parent == that.parent
                    && this.noFrequentlyForwarded == that.noFrequentlyForwarded
                    && this.membershipApprovalMode == that.membershipApprovalMode
                    && this.breakout == that.breakout
                    && this.hiddenGroup == that.hiddenGroup
                    && this.allowNonAdminSubGroupCreation == that.allowNonAdminSubGroupCreation
                    && this.groupHistory == that.groupHistory
                    && this.capi == that.capi
                    && Objects.equals(this.groupId, that.groupId)
                    && Objects.equals(this.groupJid, that.groupJid)
                    && Objects.equals(this.groupCreator, that.groupCreator)
                    && Objects.equals(this.groupSyncTime, that.groupSyncTime)
                    && Objects.equals(this.groupSyncOwner, that.groupSyncOwner)
                    && Objects.equals(this.subject, that.subject)
                    && Objects.equals(this.descriptionId, that.descriptionId)
                    && Objects.equals(this.descriptionError, that.descriptionError)
                    && Objects.equals(this.ephemeralExpiration, that.ephemeralExpiration)
                    && Objects.equals(this.ephemeralTrigger, that.ephemeralTrigger)
                    && Objects.equals(this.linkedParentJid, that.linkedParentJid)
                    && Objects.equals(this.participants, that.participants)
                    && Objects.equals(this.group, that.group);
        }

        @Override
        public int hashCode() {
            var primary = Objects.hash(groupId, groupJid, groupCreator, groupCreation, groupSyncTime,
                    groupSyncOwner, subject, descriptionId, descriptionError, locked, announcement, parent,
                    noFrequentlyForwarded, ephemeralExpiration, ephemeralTrigger);
            var secondary = Objects.hash(membershipApprovalMode, breakout, linkedParentJid, hiddenGroup,
                    allowNonAdminSubGroupCreation, groupHistory, capi, participants);
            return primary * 31 + secondary;
        }

        @Override
        public String toString() {
            return "SmaxGroupsCreateResponse.Success[groupId=" + groupId
                    + ", groupJid=" + groupJid
                    + ", groupCreator=" + groupCreator
                    + ", groupCreation=" + groupCreation
                    + ", groupSyncTime=" + groupSyncTime
                    + ", groupSyncOwner=" + groupSyncOwner
                    + ", subject=" + subject
                    + ", descriptionId=" + descriptionId
                    + ", descriptionError=" + descriptionError
                    + ", locked=" + locked
                    + ", announcement=" + announcement
                    + ", parent=" + parent
                    + ", noFrequentlyForwarded=" + noFrequentlyForwarded
                    + ", ephemeralExpiration=" + ephemeralExpiration
                    + ", ephemeralTrigger=" + ephemeralTrigger
                    + ", membershipApprovalMode=" + membershipApprovalMode
                    + ", breakout=" + breakout
                    + ", linkedParentJid=" + linkedParentJid
                    + ", hiddenGroup=" + hiddenGroup
                    + ", allowNonAdminSubGroupCreation=" + allowNonAdminSubGroupCreation
                    + ", groupHistory=" + groupHistory
                    + ", capi=" + capi
                    + ", participants=" + participants + ']';
        }

        /**
         * Single seed-participant echo row inside a {@link Success}.
         *
         * @apiNote
         * Each requested participant surfaces either as a successfully-added participant
         * ({@link #notRegisteredOnWa()} returns {@code false}, with {@link #username()} populated when the
         * relay echoed the username mixin) or as a not-registered marker
         * ({@link #notRegisteredOnWa()} returns {@code true}). WA Web discriminates the two via the
         * {@code WASmaxInGroupsCreateParticipantAddedResponseMixin} vs
         * {@code WASmaxInGroupsNonRegisteredWaUserParticipantErrorLidResponseMixin} parsers.
         *
         * @implNote
         * This implementation collapses the WA Web alternation into a single class with the
         * {@link #notRegisteredOnWa()} flag because the two sub-shapes share the same attribute schema and
         * downstream callers branch on a single boolean.
         */
        @WhatsAppWebModule(moduleName = "WASmaxInGroupsCreateParticipantAddedResponseMixin")
        @WhatsAppWebModule(moduleName = "WASmaxInGroupsNonRegisteredWaUserParticipantErrorLidResponseMixin")
        public static final class ResponseParticipant {
            /**
             * The participant {@link Jid} echoed by the relay when the participant was successfully added;
             * {@code null} for not-registered entries.
             */
            private final Jid jid;

            /**
             * The optional phone-number mixin echoed by the relay (always present on not-registered entries;
             * optional on added entries).
             */
            private final Jid phoneNumber;

            /**
             * The optional username echoed by the relay for LID-addressed participants.
             */
            private final String username;

            /**
             * Whether the relay surfaced the not-registered marker (parsed via
             * {@code NonRegisteredWaUserParticipantErrorLidResponseMixin} rather than
             * {@code CreateParticipantAddedResponseMixin}).
             */
            private final boolean notRegisteredOnWa;

            /**
             * Constructs a participant echo row.
             *
             * @apiNote
             * Typically produced by {@link #of(Node)}; direct construction is used to seed test fixtures.
             *
             * @param jid               the participant JID; may be {@code null} for not-registered rows
             * @param phoneNumber       the optional phone JID
             * @param username          the optional username
             * @param notRegisteredOnWa whether the participant is a non-registered WA user
             */
            public ResponseParticipant(Jid jid, Jid phoneNumber, String username, boolean notRegisteredOnWa) {
                this.jid = jid;
                this.phoneNumber = phoneNumber;
                this.username = username;
                this.notRegisteredOnWa = notRegisteredOnWa;
            }

            /**
             * Returns the participant {@link Jid}.
             *
             * @return an {@link Optional} carrying the JID, or empty for not-registered rows
             */
            public Optional<Jid> jid() {
                return Optional.ofNullable(jid);
            }

            /**
             * Returns the optional phone-number {@link Jid}.
             *
             * @apiNote
             * Always present on not-registered rows; optional on added rows depending on whether the relay
             * echoed the phone-number mixin.
             *
             * @return an {@link Optional} carrying the phone JID, or empty when omitted
             */
            public Optional<Jid> phoneNumber() {
                return Optional.ofNullable(phoneNumber);
            }

            /**
             * Returns the optional username echoed by the relay for LID-addressed participants.
             *
             * @return an {@link Optional} carrying the username, or empty when omitted
             */
            public Optional<String> username() {
                return Optional.ofNullable(username);
            }

            /**
             * Returns whether this row represents a non-registered WhatsApp user.
             *
             * @apiNote
             * WA Web routes these rows into the {@code invitedOutContacts} array (for V4-invite-link
             * surfacing) rather than the {@code participants} array.
             *
             * @return {@code true} when the relay surfaced the not-registered marker
             */
            public boolean notRegisteredOnWa() {
                return notRegisteredOnWa;
            }

            /**
             * Parses a single {@code <participant/>} child into a {@link ResponseParticipant} row.
             *
             * @apiNote
             * Invoked by {@link Success#of(Node, Node)} for every {@code <participant/>} child inside the
             * echoed {@code <group/>} subtree.
             *
             * @implNote
             * This implementation reads {@code jid}, {@code phone_number}, and {@code username} attributes
             * and infers the not-registered flag from "no {@code jid} attribute but a {@code phone_number}
             * attribute". A row with no {@code jid} and no {@code phone_number} is rejected.
             *
             * @param participantNode the {@code <participant/>} child
             * @return an {@link Optional} carrying the parsed row, or {@link Optional#empty()} when the
             *         child satisfies neither sub-shape
             */
            static Optional<ResponseParticipant> of(Node participantNode) {
                var jid = participantNode.getAttributeAsJid("jid").orElse(null);
                var phoneNumber = participantNode.getAttributeAsJid("phone_number").orElse(null);
                var username = participantNode.getAttributeAsString("username").orElse(null);
                var notRegistered = jid == null && phoneNumber != null;
                if (jid == null && !notRegistered) {
                    return Optional.empty();
                }
                return Optional.of(new ResponseParticipant(jid, phoneNumber, username, notRegistered));
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (ResponseParticipant) obj;
                return this.notRegisteredOnWa == that.notRegisteredOnWa
                        && Objects.equals(this.jid, that.jid)
                        && Objects.equals(this.phoneNumber, that.phoneNumber)
                        && Objects.equals(this.username, that.username);
            }

            @Override
            public int hashCode() {
                return Objects.hash(jid, phoneNumber, username, notRegisteredOnWa);
            }

            @Override
            public String toString() {
                return "SmaxGroupsCreateResponse.Success.ResponseParticipant[jid=" + jid
                        + ", phoneNumber=" + phoneNumber
                        + ", username=" + username
                        + ", notRegisteredOnWa=" + notRegisteredOnWa + ']';
            }
        }
    }

    /**
     * The variant returned when the relay detected an existing group whose creator and dedup-token tuple
     * matches the new request, and returned the existing group's JID rather than creating a new one.
     *
     * @apiNote
     * WA Web's {@code WAWebGroupCreateJob} rejects this variant with its custom {@code GroupAlreadyExistsError};
     * embedders that want to surface the existing-group UI should branch on this variant explicitly and
     * extract {@link #groupJid()} to navigate to the existing chat.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsCreateResponseGroupAlreadyExists")
    final class GroupAlreadyExists implements SmaxGroupsCreateResponse {
        /**
         * The existing group {@link Jid} surfaced by the relay.
         */
        private final Jid groupJid;

        /**
         * Constructs a group-already-exists variant.
         *
         * @apiNote
         * Typically produced by {@link #of(Node, Node)}; direct construction is used to seed test fixtures.
         *
         * @param groupJid the existing group {@link Jid}; never {@code null}
         * @throws NullPointerException if {@code groupJid} is {@code null}
         */
        public GroupAlreadyExists(Jid groupJid) {
            this.groupJid = Objects.requireNonNull(groupJid, "groupJid cannot be null");
        }

        /**
         * Returns the existing group {@link Jid}.
         *
         * @return the group JID; never {@code null}
         */
        public Jid groupJid() {
            return groupJid;
        }

        /**
         * Parses the inbound stanza into a {@link GroupAlreadyExists} variant.
         *
         * @apiNote
         * Invoked as the second probe in the variant cascade by
         * {@link SmaxGroupsCreateResponse#of(Node, Node)}.
         *
         * @implNote
         * This implementation validates the {@code <iq type="result">} envelope addressed {@code from} a
         * {@code g.us} JID with the echoed {@code id} attribute, extracts the {@code <group/>} child, and
         * reads the {@code jid} attribute. The {@code <group/>} child on this branch carries only the
         * existing group's JID, distinct from the rich {@code <group/>} subtree on the {@link Success}
         * branch.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()} when the
         *         stanza does not match the already-exists schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsCreateResponseGroupAlreadyExists",
                exports = "parseCreateResponseGroupAlreadyExists",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<GroupAlreadyExists> of(Node node, Node request) {
            if (!node.hasDescription("iq")) {
                return Optional.empty();
            }
            if (!node.hasAttribute("type", "result")) {
                return Optional.empty();
            }
            var requestId = request.getAttributeAsString("id").orElse(null);
            if (requestId == null) {
                return Optional.empty();
            }
            if (!node.hasAttribute("id", requestId)) {
                return Optional.empty();
            }
            var fromAttr = node.getAttributeAsString("from").orElse(null);
            if (fromAttr == null || !fromAttr.endsWith("g.us")) {
                return Optional.empty();
            }
            var group = node.getChild("group").orElse(null);
            if (group == null) {
                return Optional.empty();
            }
            var groupJid = group.getAttributeAsJid("jid").orElse(null);
            if (groupJid == null) {
                return Optional.empty();
            }
            return Optional.of(new GroupAlreadyExists(groupJid));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (GroupAlreadyExists) obj;
            return Objects.equals(this.groupJid, that.groupJid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupJid);
        }

        @Override
        public String toString() {
            return "SmaxGroupsCreateResponse.GroupAlreadyExists[groupJid=" + groupJid + ']';
        }
    }

    /**
     * The client-error variant returned when the relay rejected the request as malformed, unauthorised, or
     * bumping a per-creator rate-limit cap.
     *
     * @apiNote
     * WA Web's {@code WAWebGroupCreateJob} forwards this variant as a {@code ServerStatusCodeError}; the
     * job additionally extracts the {@code rateLimitAddParticipantTimeOrCountRateLimitMixinGroup} mixin to
     * surface {@code GroupAddParticipantTimeRateLimitServerError} / {@code GroupAddParticipantCountRateLimitServerError}
     * for the participant-add rate-limit path. Cobalt exposes the raw error envelope and leaves the
     * rate-limit projection to the caller.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsCreateResponseClientError")
    final class ClientError implements SmaxGroupsCreateResponse {
        /**
         * The numeric server-side error code, mirroring the {@code <error code="...">} attribute on the
         * inbound stanza.
         */
        private final int errorCode;

        /**
         * The human-readable error text echoed by the relay; {@code null} when the relay omitted the
         * {@code <error text="...">} attribute.
         */
        private final String errorText;

        /**
         * Constructs a client-error variant.
         *
         * @apiNote
         * Typically produced by {@link #of(Node, Node)}; direct construction is used to seed test fixtures.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be {@code null}
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric server-side error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the error text, or empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Parses the inbound stanza into a {@link ClientError} envelope.
         *
         * @apiNote
         * Invoked as the third probe in the variant cascade by
         * {@link SmaxGroupsCreateResponse#of(Node, Node)}.
         *
         * @implNote
         * This implementation delegates the error-envelope extraction to
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)} so every SMAX response in the family
         * shares the same client-error parsing.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()} when the
         *         envelope does not match the client-error schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsCreateResponseClientError",
                exports = "parseCreateResponseClientError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ClientError(envelope.code(), envelope.text()));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ClientError) obj;
            return this.errorCode == that.errorCode && Objects.equals(this.errorText, that.errorText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "SmaxGroupsCreateResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The server-error variant returned when the relay encountered a transient internal failure.
     *
     * @apiNote
     * WA Web's {@code WAWebGroupCreateJob} forwards this variant as a {@code ServerStatusCodeError}; callers
     * can decide whether to retry based on the surfaced {@link #errorCode()}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsCreateResponseServerError")
    final class ServerError implements SmaxGroupsCreateResponse {
        /**
         * The numeric server-side error code, mirroring the {@code <error code="...">} attribute on the
         * inbound stanza.
         */
        private final int errorCode;

        /**
         * The human-readable error text echoed by the relay; {@code null} when the relay omitted the
         * {@code <error text="...">} attribute.
         */
        private final String errorText;

        /**
         * Constructs a server-error variant.
         *
         * @apiNote
         * Typically produced by {@link #of(Node, Node)}; direct construction is used to seed test fixtures.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be {@code null}
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric server-side error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the error text, or empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Parses the inbound stanza into a {@link ServerError} envelope.
         *
         * @apiNote
         * Invoked as the terminal probe in the variant cascade by
         * {@link SmaxGroupsCreateResponse#of(Node, Node)}.
         *
         * @implNote
         * This implementation delegates the error-envelope extraction to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)} so every SMAX response in the family
         * shares the same server-error parsing.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()} when the
         *         envelope does not match the server-error schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsCreateResponseServerError",
                exports = "parseCreateResponseServerError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ServerError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ServerError(envelope.code(), envelope.text()));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ServerError) obj;
            return this.errorCode == that.errorCode && Objects.equals(this.errorText, that.errorText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "SmaxGroupsCreateResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
