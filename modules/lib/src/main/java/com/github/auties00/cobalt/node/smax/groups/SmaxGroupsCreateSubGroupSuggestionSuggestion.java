package com.github.auties00.cobalt.node.smax.groups;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
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
 * Sealed alternation modelling the suggestion-body oneof carried inside a
 * {@link SmaxGroupsCreateSubGroupSuggestionRequest}.
 *
 * @apiNote
 * Pick {@link NewGroup} to ask the relay to spin up a fresh sub-group with caller-chosen subject and policy
 * markers, or {@link ExistingGroups} to ask the relay to link one or more existing groups in as sub-groups
 * of the parent community.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsSuggestionForCreateSubGroupSuggestionNewGroupOrCreateSubGroupSuggestionExistingGroupsMixinGroup")
public sealed interface SmaxGroupsCreateSubGroupSuggestionSuggestion permits SmaxGroupsCreateSubGroupSuggestionSuggestion.NewGroup, SmaxGroupsCreateSubGroupSuggestionSuggestion.ExistingGroups {
    /**
     * Contributes this suggestion's children and attributes to the supplied
     * {@code <sub_group_suggestion/>} builder.
     *
     * @apiNote
     * Called by {@link SmaxGroupsCreateSubGroupSuggestionRequest#toNode()} so each variant can stamp the
     * branch-specific shape without exposing its private state to the request envelope.
     *
     * @implSpec
     * Implementations must mutate {@code builder} in place and must not call {@link NodeBuilder#build()}; the
     * caller decides when to seal the node and wrap it in the IQ envelope.
     *
     * @param builder the target builder; never {@code null}
     */
    void contributeTo(NodeBuilder builder);

    /**
     * Suggestion body that asks the relay to spin up a brand-new sub-group inside the parent community.
     *
     * @apiNote
     * Carries the new sub-group's subject and the same policy markers exposed on
     * {@link SmaxGroupsCreateRequest} ({@code locked}, {@code announcement}, {@code hidden_group},
     * membership-approval and member-mode mixins); pair with a {@link SmaxGroupsCreateSubGroupSuggestionRequest}
     * to dispatch.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutGroupsCreateSubGroupSuggestionSuggestionForNewGroupMixin")
    final class NewGroup implements SmaxGroupsCreateSubGroupSuggestionSuggestion {
        /**
         * The subject (display name) of the proposed sub-group, emitted as a {@code <subject/>} child.
         */
        private final String subject;

        /**
         * The optional description body; emitted as {@code <description><body/></description>} when present,
         * omitted entirely when {@code null}.
         */
        private final String descriptionBody;

        /**
         * Whether to attach a {@code <locked/>} marker so chat-info edits become admin-only.
         */
        private final boolean locked;

        /**
         * Whether to attach an {@code <announcement/>} marker so only admins may post.
         */
        private final boolean announcement;

        /**
         * Whether to attach a {@code <hidden_group/>} marker so the sub-group is hidden from the community
         * directory.
         */
        private final boolean hiddenGroup;

        /**
         * The optional {@code group_join_mode} attribute stamped on a {@code <membership_approval_mode/>}
         * child; {@code null} omits the child entirely.
         */
        private final String membershipApprovalGroupJoinMode;

        /**
         * The optional {@code member_add_mode} attribute stamped on the {@code <sub_group_suggestion/>} root.
         */
        private final String memberAddMode;

        /**
         * The optional {@code member_link_mode} attribute stamped on the {@code <sub_group_suggestion/>} root.
         */
        private final String memberLinkMode;

        /**
         * The optional {@code member_share_group_history_mode} attribute stamped on the
         * {@code <sub_group_suggestion/>} root.
         */
        private final String memberShareGroupHistoryMode;

        /**
         * Constructs a new-group suggestion body.
         *
         * @apiNote
         * Every parameter except {@code subject} is optional; pass {@code null} (or {@code false} for
         * booleans) to omit the corresponding child or attribute.
         *
         * @param subject                          the subject; never {@code null}
         * @param descriptionBody                  the optional description body; may be {@code null}
         * @param locked                           see {@link #locked()}
         * @param announcement                     see {@link #announcement()}
         * @param hiddenGroup                      see {@link #hiddenGroup()}
         * @param membershipApprovalGroupJoinMode  the optional membership-approval join-mode value;
         *                                         may be {@code null}
         * @param memberAddMode                    the optional member-add mode; may be {@code null}
         * @param memberLinkMode                   the optional member-link mode; may be {@code null}
         * @param memberShareGroupHistoryMode      the optional member-share-history mode; may be {@code null}
         * @throws NullPointerException if {@code subject} is {@code null}
         */
        public NewGroup(String subject,
                        String descriptionBody,
                        boolean locked,
                        boolean announcement,
                        boolean hiddenGroup,
                        String membershipApprovalGroupJoinMode,
                        String memberAddMode,
                        String memberLinkMode,
                        String memberShareGroupHistoryMode) {
            this.subject = Objects.requireNonNull(subject, "subject cannot be null");
            this.descriptionBody = descriptionBody;
            this.locked = locked;
            this.announcement = announcement;
            this.hiddenGroup = hiddenGroup;
            this.membershipApprovalGroupJoinMode = membershipApprovalGroupJoinMode;
            this.memberAddMode = memberAddMode;
            this.memberLinkMode = memberLinkMode;
            this.memberShareGroupHistoryMode = memberShareGroupHistoryMode;
        }

        /**
         * Returns the subject text emitted as the {@code <subject/>} child.
         *
         * @return the subject; never {@code null}
         */
        public String subject() {
            return subject;
        }

        /**
         * Returns the optional description body.
         *
         * @return an {@link Optional} carrying the description body, or empty when omitted
         */
        public Optional<String> descriptionBody() {
            return Optional.ofNullable(descriptionBody);
        }

        /**
         * Returns whether the {@code <locked/>} marker is attached.
         *
         * @apiNote
         * When {@code true} the server pins chat-info editing to admins.
         *
         * @return {@code true} when the marker is emitted
         */
        public boolean locked() {
            return locked;
        }

        /**
         * Returns whether the {@code <announcement/>} marker is attached.
         *
         * @apiNote
         * When {@code true} the server restricts posting to admins.
         *
         * @return {@code true} when the marker is emitted
         */
        public boolean announcement() {
            return announcement;
        }

        /**
         * Returns whether the {@code <hidden_group/>} marker is attached.
         *
         * @apiNote
         * When {@code true} the sub-group is hidden from the community directory.
         *
         * @return {@code true} when the marker is emitted
         */
        public boolean hiddenGroup() {
            return hiddenGroup;
        }

        /**
         * Returns the optional {@code group_join_mode} attribute stamped on a
         * {@code <membership_approval_mode/>} child.
         *
         * @return an {@link Optional} carrying the join-mode value, or empty when omitted
         */
        public Optional<String> membershipApprovalGroupJoinMode() {
            return Optional.ofNullable(membershipApprovalGroupJoinMode);
        }

        /**
         * Returns the optional {@code member_add_mode} attribute stamped on the {@code <sub_group_suggestion/>}
         * root.
         *
         * @return an {@link Optional} carrying the value, or empty when omitted
         */
        public Optional<String> memberAddMode() {
            return Optional.ofNullable(memberAddMode);
        }

        /**
         * Returns the optional {@code member_link_mode} attribute stamped on the {@code <sub_group_suggestion/>}
         * root.
         *
         * @return an {@link Optional} carrying the value, or empty when omitted
         */
        public Optional<String> memberLinkMode() {
            return Optional.ofNullable(memberLinkMode);
        }

        /**
         * Returns the optional {@code member_share_group_history_mode} attribute stamped on the
         * {@code <sub_group_suggestion/>} root.
         *
         * @return an {@link Optional} carrying the value, or empty when omitted
         */
        public Optional<String> memberShareGroupHistoryMode() {
            return Optional.ofNullable(memberShareGroupHistoryMode);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation stamps the {@code member_*_mode} attributes on the {@code <sub_group_suggestion/>}
         * root first, then appends the {@code <subject/>}, optional {@code <description><body/></description>},
         * and any boolean-gated marker children in their canonical order.
         */
        @Override
        public void contributeTo(NodeBuilder builder) {
            Objects.requireNonNull(builder, "builder cannot be null");
            if (memberAddMode != null) {
                builder.attribute("member_add_mode", memberAddMode);
            }
            if (memberLinkMode != null) {
                builder.attribute("member_link_mode", memberLinkMode);
            }
            if (memberShareGroupHistoryMode != null) {
                builder.attribute("member_share_group_history_mode", memberShareGroupHistoryMode);
            }
            var children = new ArrayList<Node>();
            var subjectNode = new NodeBuilder()
                    .description("subject")
                    .content(subject.getBytes(StandardCharsets.UTF_8))
                    .build();
            children.add(subjectNode);
            if (descriptionBody != null) {
                var bodyNode = new NodeBuilder()
                        .description("body")
                        .content(descriptionBody.getBytes(StandardCharsets.UTF_8))
                        .build();
                var descriptionNode = new NodeBuilder()
                        .description("description")
                        .content(bodyNode)
                        .build();
                children.add(descriptionNode);
            }
            if (locked) {
                children.add(new NodeBuilder().description("locked").build());
            }
            if (announcement) {
                children.add(new NodeBuilder().description("announcement").build());
            }
            if (hiddenGroup) {
                children.add(new NodeBuilder().description("hidden_group").build());
            }
            if (membershipApprovalGroupJoinMode != null) {
                var membershipNode = new NodeBuilder()
                        .description("membership_approval_mode")
                        .attribute("group_join_mode", membershipApprovalGroupJoinMode)
                        .build();
                children.add(membershipNode);
            }
            builder.content(children);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (NewGroup) obj;
            return this.locked == that.locked
                    && this.announcement == that.announcement
                    && this.hiddenGroup == that.hiddenGroup
                    && Objects.equals(this.subject, that.subject)
                    && Objects.equals(this.descriptionBody, that.descriptionBody)
                    && Objects.equals(this.membershipApprovalGroupJoinMode, that.membershipApprovalGroupJoinMode)
                    && Objects.equals(this.memberAddMode, that.memberAddMode)
                    && Objects.equals(this.memberLinkMode, that.memberLinkMode)
                    && Objects.equals(this.memberShareGroupHistoryMode, that.memberShareGroupHistoryMode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subject, descriptionBody, locked, announcement, hiddenGroup,
                    membershipApprovalGroupJoinMode, memberAddMode, memberLinkMode,
                    memberShareGroupHistoryMode);
        }

        @Override
        public String toString() {
            return "SmaxGroupsCreateSubGroupSuggestionSuggestion.NewGroup[subject=" + subject
                    + ", descriptionBody=" + descriptionBody
                    + ", locked=" + locked
                    + ", announcement=" + announcement
                    + ", hiddenGroup=" + hiddenGroup
                    + ", membershipApprovalGroupJoinMode=" + membershipApprovalGroupJoinMode
                    + ", memberAddMode=" + memberAddMode
                    + ", memberLinkMode=" + memberLinkMode
                    + ", memberShareGroupHistoryMode=" + memberShareGroupHistoryMode + ']';
        }
    }

    /**
     * Suggestion body that asks the relay to link one or more already-existing groups into the parent
     * community as sub-groups.
     *
     * @apiNote
     * Each candidate is rendered as a {@code <group jid="..."/>} child of {@code <sub_group_suggestion/>}
     * with an optional {@code <hidden_group/>} sub-child; per-candidate verdicts surface on the response
     * side as {@link SmaxGroupsCreateSubGroupSuggestionResponse.ExistingGroupsSuggestionSuccess.Candidate}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutGroupsCreateSubGroupSuggestionSuggestionForExistingGroupsMixin")
    final class ExistingGroups implements SmaxGroupsCreateSubGroupSuggestionSuggestion {
        /**
         * The candidate groups to propose; the server accepts between {@code 1} and {@code 1000} entries.
         */
        private final List<Candidate> groups;

        /**
         * Constructs an existing-groups suggestion body.
         *
         * @apiNote
         * Each entry must address a group the caller has permission to link as a sub-group; per-candidate
         * permission failures surface as error tags on the response side.
         *
         * @param groups the list of candidate groups; never {@code null} and must be non-empty
         * @throws NullPointerException     if {@code groups} is {@code null}
         * @throws IllegalArgumentException when {@code groups} is empty
         */
        public ExistingGroups(List<Candidate> groups) {
            Objects.requireNonNull(groups, "groups cannot be null");
            if (groups.isEmpty()) {
                throw new IllegalArgumentException("groups must contain at least one entry");
            }
            this.groups = List.copyOf(groups);
        }

        /**
         * Returns the candidate groups.
         *
         * @return an unmodifiable list of candidate groups; never empty
         */
        public List<Candidate> groups() {
            return groups;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation appends one {@code <group jid="..."/>} child per candidate; the optional
         * {@code <hidden_group/>} marker is nested under that {@code <group/>} child when
         * {@link Candidate#hiddenGroup()} is {@code true}.
         */
        @Override
        public void contributeTo(NodeBuilder builder) {
            Objects.requireNonNull(builder, "builder cannot be null");
            var groupNodes = new ArrayList<Node>();
            for (var candidate : groups) {
                var groupBuilder = new NodeBuilder()
                        .description("group")
                        .attribute("jid", candidate.jid());
                if (candidate.hiddenGroup()) {
                    var hiddenNode = new NodeBuilder()
                            .description("hidden_group")
                            .build();
                    groupBuilder.content(hiddenNode);
                }
                groupNodes.add(groupBuilder.build());
            }
            builder.content(groupNodes);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ExistingGroups) obj;
            return Objects.equals(this.groups, that.groups);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groups);
        }

        @Override
        public String toString() {
            return "SmaxGroupsCreateSubGroupSuggestionSuggestion.ExistingGroups[groups=" + groups + ']';
        }

        /**
         * Single candidate sub-group entry inside an {@link ExistingGroups} suggestion.
         *
         * @apiNote
         * Carries the candidate {@link Jid} and an optional hidden-from-directory flag; the relay surfaces
         * per-candidate permission failures separately on the response side.
         */
        @WhatsAppWebModule(moduleName = "WASmaxOutGroupsCreateSubGroupSuggestionSuggestionForExistingGroupsMixin")
        public static final class Candidate {
            /**
             * The candidate sub-group {@link Jid} stamped on the {@code <group jid="...">} attribute.
             */
            private final Jid jid;

            /**
             * Whether to nest a {@code <hidden_group/>} marker under the {@code <group/>} child so the
             * candidate is hidden from the community directory.
             */
            private final boolean hiddenGroup;

            /**
             * Constructs a candidate entry.
             *
             * @apiNote
             * Pass {@code hiddenGroup=true} to keep this sub-group out of the community directory listing
             * after the link is accepted.
             *
             * @param jid         the candidate {@link Jid}; never {@code null}
             * @param hiddenGroup whether to nest the hidden-from-directory marker
             * @throws NullPointerException if {@code jid} is {@code null}
             */
            public Candidate(Jid jid, boolean hiddenGroup) {
                this.jid = Objects.requireNonNull(jid, "jid cannot be null");
                this.hiddenGroup = hiddenGroup;
            }

            /**
             * Returns the candidate {@link Jid}.
             *
             * @return the candidate JID; never {@code null}
             */
            public Jid jid() {
                return jid;
            }

            /**
             * Returns whether the candidate carries the hidden-from-directory marker.
             *
             * @return {@code true} when the marker is emitted
             */
            public boolean hiddenGroup() {
                return hiddenGroup;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (Candidate) obj;
                return this.hiddenGroup == that.hiddenGroup
                        && Objects.equals(this.jid, that.jid);
            }

            @Override
            public int hashCode() {
                return Objects.hash(jid, hiddenGroup);
            }

            @Override
            public String toString() {
                return "SmaxGroupsCreateSubGroupSuggestionSuggestion.ExistingGroups.Candidate[jid=" + jid
                        + ", hiddenGroup=" + hiddenGroup + ']';
            }
        }
    }
}
