package com.github.auties00.cobalt.node.mex.json.community;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.mex.MexOperation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Parsed response of the {@code mexFetchAllSubgroups} MEX query.
 *
 * @apiNote Carries the community's default subgroup record together with the
 * list of regular subgroups, projected from
 * {@code data.xwa2_group_query_by_id}. Paired with
 * {@link FetchAllSubgroupsMexRequest}; consumed by
 * {@code WAWebQuerySubGroupAction} after the relay reply lands.
 *
 * @implNote This implementation preserves the GraphQL connection shape
 * ({@code sub_groups.edges[].node}) verbatim rather than flattening it
 * into a single list, so callers can distinguish a missing
 * {@code sub_groups} container from an empty edges list. The default
 * announcement subgroup and the regular subgroups are kept on separate
 * fields, mirroring how WA Web's
 * {@code WAWebMexFetchAllSubgroupsJob.mexFetchAllSubgroups} processes the
 * two slots through the same {@code d()} helper but seeds the
 * announcement entry with {@code defaultSubgroup=true}.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchAllSubgroupsJob")
public final class FetchAllSubgroupsMexResponse implements MexOperation.Response.Json {
    /**
     * Community group identifier returned by the relay.
     *
     * @apiNote Identifies the parent community that the default and regular
     * subgroups belong to; matches the {@code group_id} sent in the request.
     */
    private final String id;

    /**
     * Community's default subgroup record.
     *
     * @apiNote The default subgroup is the always-present announcement group
     * automatically attached to every community; in WA Web it is processed
     * separately from regular subgroups and tagged
     * {@code defaultSubgroup=true} downstream.
     */
    private final DefaultSubGroup defaultSubGroup;

    /**
     * List of regular subgroups under the community.
     *
     * @apiNote Each edge carries a subgroup id, subject metadata, property
     * flags and the pending approval-request counter.
     */
    private final SubGroups subGroups;

    /**
     * Constructs a new response with the given fields.
     *
     * @apiNote Package-private; instances are produced by the
     * {@link #of(Node)} factory after parsing the inbound IQ payload.
     *
     * @param id              the community group identifier
     * @param defaultSubGroup the default subgroup record
     * @param subGroups       the regular subgroups list
     */
    private FetchAllSubgroupsMexResponse(String id, DefaultSubGroup defaultSubGroup, SubGroups subGroups) {
        this.id = id;
        this.defaultSubGroup = defaultSubGroup;
        this.subGroups = subGroups;
    }

    /**
     * Parses the MEX response carried by an inbound IQ stanza.
     *
     * @apiNote Entry point for receivers handling
     * {@code <iq xmlns="w:mex">} replies tagged with
     * {@link FetchAllSubgroupsMexRequest#QUERY_ID}. Unwraps the
     * {@code <result>} child, reads its content bytes and decodes the
     * GraphQL JSON envelope.
     *
     * @param node the inbound IQ stanza carrying the {@code <result>} child
     * @return the parsed response, or {@link Optional#empty()} if the
     *         expected JSON shape is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchAllSubgroupsJob", exports = "mexFetchAllSubgroups",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<FetchAllSubgroupsMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(FetchAllSubgroupsMexResponse::of);
    }

    /**
     * Returns the community group identifier returned by the relay.
     *
     * @apiNote Matches the {@code group_id} sent in the request; surfaced as
     * an {@link Optional} because the field may be absent from malformed
     * relay replies.
     *
     * @return an {@link Optional} containing the identifier, or empty if
     *         absent
     */
    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    /**
     * Returns the community's default subgroup record.
     *
     * @apiNote Empty when the relay returned the community envelope without
     * the {@code default_sub_group} slot; WA Web treats this case as an
     * error ({@code ServerStatusCodeError(500, "missing announcement group
     * in response")}), Cobalt prefers to surface it as
     * {@link Optional#empty()} and let callers decide whether to reject.
     *
     * @return an {@link Optional} containing the record, or empty if absent
     */
    public Optional<DefaultSubGroup> defaultSubGroup() {
        return Optional.ofNullable(defaultSubGroup);
    }

    /**
     * Returns the list of regular subgroups under the community.
     *
     * @apiNote Empty when the relay returned the community envelope without
     * the {@code sub_groups} container; the edges list inside may itself be
     * empty when the community has no regular subgroups.
     *
     * @return an {@link Optional} containing the subgroup list, or empty if
     *         absent
     */
    public Optional<SubGroups> subGroups() {
        return Optional.ofNullable(subGroups);
    }

    /**
     * Default subgroup record for a community.
     *
     * @apiNote Wraps the announcement subgroup automatically attached to a
     * community. Carries the subgroup identifier and the subject metadata;
     * unlike regular subgroups, no property flags or approval counters are
     * projected because the announcement subgroup's properties are fixed.
     */
    public static final class DefaultSubGroup {
        /**
         * Default subgroup identifier.
         *
         * @apiNote Mirrors the announcement subgroup's WhatsApp WID; in WA
         * Web it is funnelled through {@code WAWebWidFactory.createWid}.
         */
        private final String id;

        /**
         * Default subgroup subject metadata.
         *
         * @apiNote Pairs the subject text with the creation timestamp; both
         * fields are projected from the {@code subject} GraphQL object.
         */
        private final Subject subject;

        /**
         * Constructs a new default-subgroup record.
         *
         * @apiNote Package-private; instances are produced by the
         * {@link #of(JSONObject)} factory.
         *
         * @param id      the subgroup identifier
         * @param subject the subject metadata
         */
        private DefaultSubGroup(String id, Subject subject) {
            this.id = id;
            this.subject = subject;
        }

        /**
         * Returns the default subgroup identifier.
         *
         * @apiNote Surfaced as an {@link Optional} because the field may be
         * absent from malformed relay replies.
         *
         * @return an {@link Optional} containing the identifier, or empty if
         *         absent
         */
        public Optional<String> id() {
            return Optional.ofNullable(id);
        }

        /**
         * Returns the default subgroup subject metadata.
         *
         * @apiNote Empty when the relay returned the subgroup envelope
         * without the {@code subject} GraphQL object.
         *
         * @return an {@link Optional} containing the subject, or empty if
         *         absent
         */
        public Optional<Subject> subject() {
            return Optional.ofNullable(subject);
        }

        /**
         * Subject metadata for a subgroup.
         *
         * @apiNote Captures the subject text value and the epoch-second
         * creation timestamp the relay records when the subject is set.
         */
        public static final class Subject {
            /**
             * Subject text value.
             *
             * @apiNote The user-visible group title as the relay last
             * recorded it.
             */
            private final String value;

            /**
             * Subject creation epoch-second timestamp.
             *
             * @apiNote The timestamp the relay assigned the most recent
             * subject change; projected through
             * {@link Instant#ofEpochSecond(long)} on read.
             */
            private final Long creationTime;

            /**
             * Constructs a new subject record.
             *
             * @apiNote Package-private; instances are produced by the
             * {@link #of(JSONObject)} factory.
             *
             * @param value        the subject text value
             * @param creationTime the creation epoch-second timestamp
             */
            private Subject(String value, Long creationTime) {
                this.value = value;
                this.creationTime = creationTime;
            }

            /**
             * Returns the subject text value.
             *
             * @apiNote Empty when the GraphQL object did not project a
             * {@code value} field.
             *
             * @return an {@link Optional} containing the value, or empty if
             *         absent
             */
            public Optional<String> value() {
                return Optional.ofNullable(value);
            }

            /**
             * Returns the subject creation timestamp.
             *
             * @apiNote Empty when the GraphQL object did not project a
             * {@code creation_time} field; non-empty values are widened
             * through {@link Instant#ofEpochSecond(long)} from the wire
             * epoch-second long.
             *
             * @return an {@link Optional} containing the {@link Instant}, or
             *         empty if absent
             */
            public Optional<Instant> creationTime() {
                return Optional.ofNullable(creationTime).map(Instant::ofEpochSecond);
            }

            /**
             * Parses a subject record from the given JSON object.
             *
             * @apiNote Package-private; used by enclosing parsers and by the
             * sibling {@link #ofArray(JSONArray)} helper.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed record, or
             *         empty if {@code obj} is {@code null}
             */
            static Optional<Subject> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var value = obj.getString("value");
                var creationTime = obj.getLong("creation_time");
                return Optional.of(new Subject(value, creationTime));
            }

            /**
             * Parses a list of subject records from the given JSON array.
             *
             * @apiNote Package-private; companion to {@link #of(JSONObject)}
             * for array-shaped payloads. Currently unused at the call
             * sites of this response but kept for symmetry with the WA Web
             * GraphQL shape, which can project subjects as arrays elsewhere.
             *
             * @implNote This implementation skips {@code null} entries
             * without raising; the result is a mutable {@link ArrayList}
             * for consistency with the other {@code ofArray} helpers on the
             * inner types.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed records, empty if {@code arr} is
             *         {@code null}
             */
            static List<Subject> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<Subject>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Parses a default-subgroup record from the given JSON object.
         *
         * @apiNote Package-private; invoked from
         * {@link FetchAllSubgroupsMexResponse#of(byte[])} to project the
         * {@code default_sub_group} GraphQL object.
         *
         * @param obj the JSON object to parse
         * @return an {@link Optional} containing the parsed record, or empty
         *         if {@code obj} is {@code null}
         */
        static Optional<DefaultSubGroup> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var id = obj.getString("id");
            var subject = Subject.of(obj.getJSONObject("subject")).orElse(null);
            return Optional.of(new DefaultSubGroup(id, subject));
        }

        /**
         * Parses a list of default-subgroup records from the given JSON
         * array.
         *
         * @apiNote Package-private; symmetry helper for callers that need to
         * read array-shaped default-subgroup data. Currently unused at the
         * call sites of this response.
         *
         * @implNote This implementation skips {@code null} entries without
         * raising.
         *
         * @param arr the JSON array to parse
         * @return the list of parsed records, empty if {@code arr} is
         *         {@code null}
         */
        static List<DefaultSubGroup> ofArray(JSONArray arr) {
            if (arr == null) {
                return List.of();
            }

            var result = new ArrayList<DefaultSubGroup>(arr.size());
            for (var i = 0; i < arr.size(); i++) {
                of(arr.getJSONObject(i)).ifPresent(result::add);
            }
            return result;
        }
    }

    /**
     * Subgroups list wrapping the {@code edges} array of regular subgroups
     * under a community.
     *
     * @apiNote Preserves the GraphQL connection shape; each {@link Edges}
     * entry wraps a single subgroup {@link Edges.Node}.
     */
    public static final class SubGroups {
        /**
         * Subgroup edges returned by the relay.
         *
         * @apiNote Mirrors the {@code sub_groups.edges} GraphQL array; never
         * {@code null} once {@link SubGroups#of(JSONObject)} has succeeded.
         */
        private final List<Edges> edges;

        /**
         * Constructs a new subgroups list.
         *
         * @apiNote Package-private; instances are produced by
         * {@link SubGroups#of(JSONObject)}.
         *
         * @param edges the subgroup edges
         */
        private SubGroups(List<Edges> edges) {
            this.edges = edges;
        }

        /**
         * Returns the subgroup edges.
         *
         * @apiNote Iterate this list to walk the regular subgroups; each
         * entry exposes a {@link Edges#node()} accessor that may be empty
         * when the relay returned a malformed edge.
         *
         * @return the list of edges, empty if absent
         */
        public List<Edges> edges() {
            return edges;
        }

        /**
         * Single edge wrapper around a subgroup node, mirroring the GraphQL
         * connection pattern.
         *
         * @apiNote Each edge wraps exactly one {@link Node}; the edge level
         * exists to leave room for future GraphQL cursor metadata without
         * breaking the binary shape.
         */
        public static final class Edges {
            /**
             * Subgroup node carried by the edge.
             *
             * @apiNote Nullable; a {@code null} value indicates the relay
             * returned an edge envelope without a {@code node} child.
             */
            private final Node node;

            /**
             * Constructs a new edge wrapping the given node.
             *
             * @apiNote Package-private; instances are produced by
             * {@link Edges#of(JSONObject)}.
             *
             * @param node the subgroup node
             */
            private Edges(Node node) {
                this.node = node;
            }

            /**
             * Returns the subgroup node carried by this edge.
             *
             * @apiNote Empty when the relay returned the edge envelope
             * without a {@code node} child; WA Web treats this case as an
             * error ({@code ServerStatusCodeError(500, "null node in
             * sub_group_suggestions")}), Cobalt prefers to surface it as
             * {@link Optional#empty()}.
             *
             * @return an {@link Optional} containing the node, or empty if
             *         absent
             */
            public Optional<Node> node() {
                return Optional.ofNullable(node);
            }

            /**
             * Subgroup node carrying the identifier together with subject
             * metadata, group properties and the pending membership
             * approval-request counter.
             *
             * @apiNote One per regular subgroup under the community; this
             * is the node consumed by
             * {@code WAWebQuerySubGroupAction} to populate the community
             * subgroup store.
             */
            public static final class Node {
                /**
                 * Subgroup identifier.
                 *
                 * @apiNote The subgroup's WhatsApp WID stringified; in WA
                 * Web it is funnelled through {@code WAWebWidFactory.createWid}.
                 */
                private final String id;

                /**
                 * Subgroup subject metadata.
                 *
                 * @apiNote Pairs the subject text with the creation
                 * timestamp; the value the relay last recorded.
                 */
                private final Subject subject;

                /**
                 * Subgroup property flags.
                 *
                 * @apiNote Captures the general-chat tag, the
                 * membership-approval-mode flag and the hidden-group state.
                 */
                private final Properties properties;

                /**
                 * Pending membership-approval-request counter.
                 *
                 * @apiNote Non-zero when the subgroup has at least one
                 * pending join request that the current user can act on.
                 */
                private final MembershipApprovalRequests membershipApprovalRequests;

                /**
                 * Constructs a new subgroup node.
                 *
                 * @apiNote Package-private; instances are produced by the
                 * {@link Node#of(JSONObject)} factory.
                 *
                 * @param id                         the subgroup identifier
                 * @param subject                    the subject metadata
                 * @param properties                 the subgroup properties
                 * @param membershipApprovalRequests the pending membership
                 *                                   approval requests counter
                 */
                private Node(String id, Subject subject, Properties properties, MembershipApprovalRequests membershipApprovalRequests) {
                    this.id = id;
                    this.subject = subject;
                    this.properties = properties;
                    this.membershipApprovalRequests = membershipApprovalRequests;
                }

                /**
                 * Returns the subgroup identifier.
                 *
                 * @apiNote Empty when the relay omitted the {@code id} field
                 * from this subgroup node.
                 *
                 * @return an {@link Optional} containing the identifier, or
                 *         empty if absent
                 */
                public Optional<String> id() {
                    return Optional.ofNullable(id);
                }

                /**
                 * Returns the subgroup subject metadata.
                 *
                 * @apiNote Empty when the relay omitted the {@code subject}
                 * GraphQL object.
                 *
                 * @return an {@link Optional} containing the subject, or
                 *         empty if absent
                 */
                public Optional<Subject> subject() {
                    return Optional.ofNullable(subject);
                }

                /**
                 * Returns the subgroup properties.
                 *
                 * @apiNote Empty when the relay omitted the {@code properties}
                 * GraphQL object; callers that need a specific flag should
                 * fall back to {@code false} on empty.
                 *
                 * @return an {@link Optional} containing the properties, or
                 *         empty if absent
                 */
                public Optional<Properties> properties() {
                    return Optional.ofNullable(properties);
                }

                /**
                 * Returns the pending membership-approval-request counter.
                 *
                 * @apiNote Empty when the relay omitted the
                 * {@code membership_approval_requests} GraphQL object;
                 * callers should treat empty as zero.
                 *
                 * @return an {@link Optional} containing the counter record,
                 *         or empty if absent
                 */
                public Optional<MembershipApprovalRequests> membershipApprovalRequests() {
                    return Optional.ofNullable(membershipApprovalRequests);
                }

                /**
                 * Subject metadata for a subgroup.
                 *
                 * @apiNote Mirrors the {@link DefaultSubGroup.Subject}
                 * shape; kept as a separate type to scope it under the
                 * subgroup {@link Node} for the GraphQL connection model.
                 */
                public static final class Subject {
                    /**
                     * Subject text value.
                     *
                     * @apiNote User-visible group title as the relay last
                     * recorded it.
                     */
                    private final String value;

                    /**
                     * Subject creation epoch-second timestamp.
                     *
                     * @apiNote The timestamp the relay assigned the most
                     * recent subject change.
                     */
                    private final Long creationTime;

                    /**
                     * Constructs a new subject record.
                     *
                     * @apiNote Package-private; instances are produced by
                     * the {@link Subject#of(JSONObject)} factory.
                     *
                     * @param value        the subject text value
                     * @param creationTime the creation epoch-second timestamp
                     */
                    private Subject(String value, Long creationTime) {
                        this.value = value;
                        this.creationTime = creationTime;
                    }

                    /**
                     * Returns the subject text value.
                     *
                     * @apiNote Empty when the GraphQL object did not project
                     * a {@code value} field.
                     *
                     * @return an {@link Optional} containing the value, or
                     *         empty if absent
                     */
                    public Optional<String> value() {
                        return Optional.ofNullable(value);
                    }

                    /**
                     * Returns the subject creation timestamp.
                     *
                     * @apiNote Empty when the GraphQL object did not project
                     * a {@code creation_time} field.
                     *
                     * @return an {@link Optional} containing the
                     *         {@link Instant}, or empty if absent
                     */
                    public Optional<Instant> creationTime() {
                        return Optional.ofNullable(creationTime).map(Instant::ofEpochSecond);
                    }

                    /**
                     * Parses a subject record from the given JSON object.
                     *
                     * @apiNote Package-private; invoked from the parent
                     * {@link Node#of(JSONObject)} factory.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed
                     *         record, or empty if {@code obj} is {@code null}
                     */
                    static Optional<Subject> of(JSONObject obj) {
                        if (obj == null) {
                            return Optional.empty();
                        }

                        var value = obj.getString("value");
                        var creationTime = obj.getLong("creation_time");
                        return Optional.of(new Subject(value, creationTime));
                    }

                    /**
                     * Parses a list of subject records from the given JSON
                     * array.
                     *
                     * @apiNote Package-private; symmetry helper for callers
                     * that need to read array-shaped subject data. Currently
                     * unused at the call sites of this response.
                     *
                     * @implNote This implementation skips {@code null}
                     * entries without raising.
                     *
                     * @param arr the JSON array to parse
                     * @return the list of parsed records, empty if
                     *         {@code arr} is {@code null}
                     */
                    static List<Subject> ofArray(JSONArray arr) {
                        if (arr == null) {
                            return List.of();
                        }

                        var result = new ArrayList<Subject>(arr.size());
                        for (var i = 0; i < arr.size(); i++) {
                            of(arr.getJSONObject(i)).ifPresent(result::add);
                        }
                        return result;
                    }
                }

                /**
                 * Subgroup property flags.
                 *
                 * @apiNote Captures the three boolean-ish flags WA Web
                 * processes in
                 * {@code WAWebMexFetchAllSubgroupsJob}: general-chat tag,
                 * membership-approval-mode toggle and hidden-group state.
                 */
                public static final class Properties {
                    /**
                     * General-chat tag for the subgroup.
                     *
                     * @apiNote Wire-side string sourced from the
                     * {@code general_chat} GraphQL scalar; WA Web treats
                     * any non-null value as truthy in the downstream
                     * {@code generalSubgroup} projection.
                     */
                    private final String generalChat;

                    /**
                     * Whether membership-approval mode is enabled.
                     *
                     * @apiNote Mirrors the
                     * {@code membership_approval_mode_enabled} GraphQL
                     * boolean; when {@code true} new members need
                     * admin approval before joining.
                     */
                    private final Boolean membershipApprovalModeEnabled;

                    /**
                     * Hidden-group state tag.
                     *
                     * @apiNote Wire-side string sourced from the
                     * {@code hidden_group} GraphQL scalar; WA Web treats
                     * any non-null value as truthy in the downstream
                     * {@code hiddenSubgroup} projection.
                     */
                    private final String hiddenGroup;

                    /**
                     * Constructs a new properties record.
                     *
                     * @apiNote Package-private; instances are produced by
                     * the {@link Properties#of(JSONObject)} factory.
                     *
                     * @param generalChat                   the general-chat
                     *                                      tag
                     * @param membershipApprovalModeEnabled whether approval
                     *                                      mode is enabled
                     * @param hiddenGroup                   the hidden-group
                     *                                      state tag
                     */
                    private Properties(String generalChat, Boolean membershipApprovalModeEnabled, String hiddenGroup) {
                        this.generalChat = generalChat;
                        this.membershipApprovalModeEnabled = membershipApprovalModeEnabled;
                        this.hiddenGroup = hiddenGroup;
                    }

                    /**
                     * Returns the general-chat tag for the subgroup.
                     *
                     * @apiNote Empty when the GraphQL object did not project
                     * a {@code general_chat} field.
                     *
                     * @return an {@link Optional} containing the tag, or
                     *         empty if absent
                     */
                    public Optional<String> generalChat() {
                        return Optional.ofNullable(generalChat);
                    }

                    /**
                     * Returns whether membership-approval mode is enabled
                     * for this subgroup.
                     *
                     * @apiNote Returns {@code false} when the relay omitted
                     * the field, matching WA Web's
                     * {@code (c?.membership_approval_mode_enabled) ?? false}
                     * fallback.
                     *
                     * @return {@code true} when the flag is present and
                     *         set, {@code false} otherwise
                     */
                    public boolean membershipApprovalModeEnabled() {
                        return membershipApprovalModeEnabled != null && membershipApprovalModeEnabled;
                    }

                    /**
                     * Returns the hidden-group state tag.
                     *
                     * @apiNote Empty when the GraphQL object did not project
                     * a {@code hidden_group} field.
                     *
                     * @return an {@link Optional} containing the tag, or
                     *         empty if absent
                     */
                    public Optional<String> hiddenGroup() {
                        return Optional.ofNullable(hiddenGroup);
                    }

                    /**
                     * Parses a properties record from the given JSON object.
                     *
                     * @apiNote Package-private; invoked from the parent
                     * {@link Node#of(JSONObject)} factory.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed
                     *         record, or empty if {@code obj} is {@code null}
                     */
                    static Optional<Properties> of(JSONObject obj) {
                        if (obj == null) {
                            return Optional.empty();
                        }

                        var generalChat = obj.getString("general_chat");
                        var membershipApprovalModeEnabled = obj.getBoolean("membership_approval_mode_enabled");
                        var hiddenGroup = obj.getString("hidden_group");
                        return Optional.of(new Properties(generalChat, membershipApprovalModeEnabled, hiddenGroup));
                    }

                    /**
                     * Parses a list of properties records from the given
                     * JSON array.
                     *
                     * @apiNote Package-private; symmetry helper for callers
                     * that need to read array-shaped property data. Currently
                     * unused at the call sites of this response.
                     *
                     * @implNote This implementation skips {@code null}
                     * entries without raising.
                     *
                     * @param arr the JSON array to parse
                     * @return the list of parsed records, empty if
                     *         {@code arr} is {@code null}
                     */
                    static List<Properties> ofArray(JSONArray arr) {
                        if (arr == null) {
                            return List.of();
                        }

                        var result = new ArrayList<Properties>(arr.size());
                        for (var i = 0; i < arr.size(); i++) {
                            of(arr.getJSONObject(i)).ifPresent(result::add);
                        }
                        return result;
                    }
                }

                /**
                 * Counter record for pending membership-approval requests.
                 *
                 * @apiNote Drives the badge on the community subgroup row
                 * when approvals are pending; WA Web maps a non-zero
                 * {@code total_count} to the boolean
                 * {@code membershipApprovalRequest} downstream.
                 */
                public static final class MembershipApprovalRequests {
                    /**
                     * Number of pending approval requests.
                     *
                     * @apiNote Mirrors the {@code total_count} GraphQL
                     * scalar; a {@code null} value means the relay did not
                     * project the field, distinct from a zero count.
                     */
                    private final Long totalCount;

                    /**
                     * Constructs a new counter record.
                     *
                     * @apiNote Package-private; instances are produced by
                     * the {@link MembershipApprovalRequests#of(JSONObject)}
                     * factory.
                     *
                     * @param totalCount the number of pending approval
                     *                   requests
                     */
                    private MembershipApprovalRequests(Long totalCount) {
                        this.totalCount = totalCount;
                    }

                    /**
                     * Returns the number of pending approval requests.
                     *
                     * @apiNote Empty when the GraphQL object did not project
                     * a {@code total_count} field; callers that need a
                     * primitive can fall back to {@code 0}.
                     *
                     * @return an {@link OptionalLong} containing the count,
                     *         or empty if absent
                     */
                    public OptionalLong totalCount() {
                        return totalCount != null ? OptionalLong.of(totalCount) : OptionalLong.empty();
                    }

                    /**
                     * Parses a counter record from the given JSON object.
                     *
                     * @apiNote Package-private; invoked from the parent
                     * {@link Node#of(JSONObject)} factory.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed
                     *         record, or empty if {@code obj} is {@code null}
                     */
                    static Optional<MembershipApprovalRequests> of(JSONObject obj) {
                        if (obj == null) {
                            return Optional.empty();
                        }

                        var totalCount = obj.getLong("total_count");
                        return Optional.of(new MembershipApprovalRequests(totalCount));
                    }

                    /**
                     * Parses a list of counter records from the given JSON
                     * array.
                     *
                     * @apiNote Package-private; symmetry helper for callers
                     * that need to read array-shaped counter data. Currently
                     * unused at the call sites of this response.
                     *
                     * @implNote This implementation skips {@code null}
                     * entries without raising.
                     *
                     * @param arr the JSON array to parse
                     * @return the list of parsed records, empty if
                     *         {@code arr} is {@code null}
                     */
                    static List<MembershipApprovalRequests> ofArray(JSONArray arr) {
                        if (arr == null) {
                            return List.of();
                        }

                        var result = new ArrayList<MembershipApprovalRequests>(arr.size());
                        for (var i = 0; i < arr.size(); i++) {
                            of(arr.getJSONObject(i)).ifPresent(result::add);
                        }
                        return result;
                    }
                }

                /**
                 * Parses a subgroup node from the given JSON object.
                 *
                 * @apiNote Package-private; invoked from
                 * {@link Edges#of(JSONObject)} to project the
                 * {@code edge.node} GraphQL object.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed node,
                 *         or empty if {@code obj} is {@code null}
                 */
                static Optional<Node> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var id = obj.getString("id");
                    var subject = Subject.of(obj.getJSONObject("subject")).orElse(null);
                    var properties = Properties.of(obj.getJSONObject("properties")).orElse(null);
                    var membershipApprovalRequests = MembershipApprovalRequests.of(obj.getJSONObject("membership_approval_requests")).orElse(null);
                    return Optional.of(new Node(id, subject, properties, membershipApprovalRequests));
                }

                /**
                 * Parses a list of subgroup nodes from the given JSON array.
                 *
                 * @apiNote Package-private; symmetry helper for callers that
                 * need to read array-shaped node data. Currently unused at
                 * the call sites of this response.
                 *
                 * @implNote This implementation skips {@code null} entries
                 * without raising.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed nodes, empty if {@code arr} is
                 *         {@code null}
                 */
                static List<Node> ofArray(JSONArray arr) {
                    if (arr == null) {
                        return List.of();
                    }

                    var result = new ArrayList<Node>(arr.size());
                    for (var i = 0; i < arr.size(); i++) {
                        of(arr.getJSONObject(i)).ifPresent(result::add);
                    }
                    return result;
                }
            }

            /**
             * Parses an edge wrapper from the given JSON object.
             *
             * @apiNote Package-private; invoked from
             * {@link SubGroups#of(JSONObject)} per array element of
             * {@code sub_groups.edges}.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed edge, or
             *         empty if {@code obj} is {@code null}
             */
            static Optional<Edges> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var node = Node.of(obj.getJSONObject("node")).orElse(null);
                return Optional.of(new Edges(node));
            }

            /**
             * Parses a list of edge wrappers from the given JSON array.
             *
             * @apiNote Package-private; called from
             * {@link SubGroups#of(JSONObject)} to project the entire
             * {@code edges} array in one shot.
             *
             * @implNote This implementation skips {@code null} entries
             * without raising.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed edges, empty if {@code arr} is
             *         {@code null}
             */
            static List<Edges> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<Edges>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Parses a subgroups list from the given JSON object.
         *
         * @apiNote Package-private; invoked from
         * {@link FetchAllSubgroupsMexResponse#of(byte[])} to project the
         * {@code sub_groups} GraphQL container.
         *
         * @param obj the JSON object to parse
         * @return an {@link Optional} containing the parsed list, or empty
         *         if {@code obj} is {@code null}
         */
        static Optional<SubGroups> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var edges = Edges.ofArray(obj.getJSONArray("edges"));
            return Optional.of(new SubGroups(edges));
        }

        /**
         * Parses a list of subgroups containers from the given JSON array.
         *
         * @apiNote Package-private; symmetry helper for callers that need to
         * read array-shaped container data. Currently unused at the call
         * sites of this response.
         *
         * @implNote This implementation skips {@code null} entries without
         * raising.
         *
         * @param arr the JSON array to parse
         * @return the list of parsed containers, empty if {@code arr} is
         *         {@code null}
         */
        static List<SubGroups> ofArray(JSONArray arr) {
            if (arr == null) {
                return List.of();
            }

            var result = new ArrayList<SubGroups>(arr.size());
            for (var i = 0; i < arr.size(); i++) {
                of(arr.getJSONObject(i)).ifPresent(result::add);
            }
            return result;
        }
    }

    /**
     * Parses the response from the raw JSON payload bytes.
     *
     * @apiNote Package-private; only invoked via the {@link #of(Node)} entry
     * point after unwrapping the IQ stanza.
     *
     * @implNote This implementation requires the {@code data} and
     * {@code data.xwa2_group_query_by_id} envelopes to be present, matching
     * WA Web's pre-check before destructuring; missing inner fields
     * ({@code default_sub_group}, {@code sub_groups}) collapse to
     * {@code null} on the response rather than raising, which is laxer than
     * the WA Web {@code ServerStatusCodeError(500)} path so callers can
     * decide how to surface the gap.
     *
     * @param json the raw JSON bytes from the {@code <result>} child
     * @return an {@link Optional} containing the parsed response, or empty
     *         if the envelope is missing
     */
    private static Optional<FetchAllSubgroupsMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_group_query_by_id");
        if (root == null) {
            return Optional.empty();
        }

        var id = root.getString("id");
        var defaultSubGroup = DefaultSubGroup.of(root.getJSONObject("default_sub_group")).orElse(null);
        var subGroups = SubGroups.of(root.getJSONObject("sub_groups")).orElse(null);

        return Optional.of(new FetchAllSubgroupsMexResponse(id, defaultSubGroup, subGroups));
    }
}
