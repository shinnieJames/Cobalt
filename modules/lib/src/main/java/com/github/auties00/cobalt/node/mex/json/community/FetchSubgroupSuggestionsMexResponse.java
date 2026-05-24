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
 * Parsed response of the {@code mexFetchSubgroupSuggestions} MEX query.
 *
 * @apiNote Carries the per-community list of suggested subgroups projected
 * from {@code data.xwa2_group_query_by_id.sub_group_suggestions}. Paired
 * with {@link FetchSubgroupSuggestionsMexRequest}; consumed by
 * {@code WAWebQueryAndUpdateSubgroupSuggestionsJob} to populate the
 * "suggested subgroups" picker.
 *
 * @implNote This implementation preserves the GraphQL connection shape
 * ({@code sub_group_suggestions.edges[].node}) verbatim rather than
 * flattening it into a single list, so callers can distinguish a missing
 * suggestions container from an empty edges list. Unlike WA Web's
 * {@code WAWebMexFetchSubgroupSuggestionsJob.m()} helper, missing
 * {@code id}, {@code creator.id} or {@code is_existing_group} fields do
 * not raise; they collapse to empty fields on the projection.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchSubgroupSuggestionsJob")
public final class FetchSubgroupSuggestionsMexResponse implements MexOperation.Response.Json {
    /**
     * Community group identifier returned by the relay.
     *
     * @apiNote Identifies the parent community that the suggestions belong
     * to; matches the {@code group_id} sent in the request.
     */
    private final String id;

    /**
     * Subgroup suggestions container returned by the relay.
     *
     * @apiNote Wraps the {@code edges} GraphQL array of suggested subgroup
     * nodes; empty when the relay did not project the container.
     */
    private final SubGroupSuggestions subGroupSuggestions;

    /**
     * Constructs a new response with the given fields.
     *
     * @apiNote Package-private; instances are produced by the
     * {@link #of(Node)} factory after parsing the inbound IQ payload.
     *
     * @param id                  the community group identifier
     * @param subGroupSuggestions the subgroup suggestions container
     */
    private FetchSubgroupSuggestionsMexResponse(String id, SubGroupSuggestions subGroupSuggestions) {
        this.id = id;
        this.subGroupSuggestions = subGroupSuggestions;
    }

    /**
     * Parses the MEX response carried by an inbound IQ stanza.
     *
     * @apiNote Entry point for receivers handling
     * {@code <iq xmlns="w:mex">} replies tagged with
     * {@link FetchSubgroupSuggestionsMexRequest#QUERY_ID}. Unwraps the
     * {@code <result>} child, reads its content bytes and decodes the
     * GraphQL JSON envelope.
     *
     * @param node the inbound IQ stanza carrying the {@code <result>} child
     * @return the parsed response, or {@link Optional#empty()} if the
     *         expected JSON shape is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchSubgroupSuggestionsJob", exports = "mexFetchSubgroupSuggestions",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<FetchSubgroupSuggestionsMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(FetchSubgroupSuggestionsMexResponse::of);
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
     * Returns the subgroup suggestions container.
     *
     * @apiNote Empty when the relay omitted the
     * {@code sub_group_suggestions} GraphQL container; the edges list
     * inside may itself be empty when the community has no candidates.
     *
     * @return an {@link Optional} containing the container, or empty if
     *         absent
     */
    public Optional<SubGroupSuggestions> subGroupSuggestions() {
        return Optional.ofNullable(subGroupSuggestions);
    }

    /**
     * Subgroup suggestions container wrapping the {@code edges} array of
     * suggested subgroup nodes.
     *
     * @apiNote Preserves the GraphQL connection shape; each {@link Edges}
     * entry wraps a single suggested {@link Edges.Node}.
     */
    public static final class SubGroupSuggestions {
        /**
         * Suggestion edges returned by the relay.
         *
         * @apiNote Mirrors the {@code sub_group_suggestions.edges} GraphQL
         * array; never {@code null} once
         * {@link SubGroupSuggestions#of(JSONObject)} has succeeded.
         */
        private final List<Edges> edges;

        /**
         * Constructs a new suggestions container with the given edges.
         *
         * @apiNote Package-private; instances are produced by
         * {@link SubGroupSuggestions#of(JSONObject)}.
         *
         * @param edges the suggestion edges
         */
        private SubGroupSuggestions(List<Edges> edges) {
            this.edges = edges;
        }

        /**
         * Returns the suggestion edges.
         *
         * @apiNote Iterate this list to walk the suggested subgroups; each
         * entry exposes a {@link Edges#node()} accessor that may be empty
         * when the relay returned a malformed edge.
         *
         * @return the list of edges, empty if absent
         */
        public List<Edges> edges() {
            return edges;
        }

        /**
         * Single edge wrapper around a suggested subgroup node.
         *
         * @apiNote The edge level exists to leave room for future GraphQL
         * cursor metadata without breaking the binary shape.
         */
        public static final class Edges {
            /**
             * Suggested subgroup node carried by the edge.
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
             * @param node the suggested subgroup node
             */
            private Edges(Node node) {
                this.node = node;
            }

            /**
             * Returns the suggested subgroup node.
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
             * Suggested subgroup node carrying the subgroup identifier with
             * its subject, description, creator, creation timestamp,
             * participant count, existing-group flag and hidden-group state.
             *
             * @apiNote One per candidate suggestion under the community;
             * this is the node consumed by
             * {@code WAWebQueryAndUpdateSubgroupSuggestionsJob} to populate
             * the suggestions store.
             */
            public static final class Node {
                /**
                 * Subgroup identifier.
                 *
                 * @apiNote The suggested subgroup's WhatsApp WID
                 * stringified; in WA Web it is funnelled through the
                 * {@code WAWebWid} constructor with
                 * {@code intentionallyUsePrivateConstructor: true}.
                 */
                private final String id;

                /**
                 * Subgroup subject metadata.
                 *
                 * @apiNote Carries the subject text only; the suggestions
                 * GraphQL document does not project the creation timestamp.
                 */
                private final Subject subject;

                /**
                 * Subgroup description metadata.
                 *
                 * @apiNote Carries the description text and the description
                 * metadata identifier; both fields are projected from the
                 * {@code description} GraphQL object.
                 */
                private final Description description;

                /**
                 * Subgroup creator metadata.
                 *
                 * @apiNote Carries the creator WID; WA Web treats a missing
                 * creator id as a hard error.
                 */
                private final Creator creator;

                /**
                 * Subgroup creation epoch-second timestamp.
                 *
                 * @apiNote Wire-side {@code Long} sourced from the
                 * {@code creation_time} GraphQL scalar; widened to
                 * {@link Instant} on read.
                 */
                private final Long creationTime;

                /**
                 * Total participant count for the subgroup.
                 *
                 * @apiNote Mirrors the {@code total_participants_count}
                 * GraphQL scalar; reflects all members, not just those who
                 * already overlap with the community.
                 */
                private final Long totalParticipantsCount;

                /**
                 * Whether the suggested subgroup is already an existing
                 * group the user is part of.
                 *
                 * @apiNote {@code true} when the user is already a member
                 * (and the community admin could promote it to a subgroup),
                 * {@code false} when it is a discovery suggestion.
                 */
                private final Boolean isExistingGroup;

                /**
                 * Hidden-group state tag.
                 *
                 * @apiNote Wire-side string sourced from the
                 * {@code hidden_group} GraphQL scalar; WA Web stores it
                 * verbatim on the suggestion record.
                 */
                private final String hiddenGroup;

                /**
                 * Constructs a new suggested subgroup node.
                 *
                 * @apiNote Package-private; instances are produced by the
                 * {@link Node#of(JSONObject)} factory.
                 *
                 * @param id                     the subgroup identifier
                 * @param subject                the subject metadata
                 * @param description            the description metadata
                 * @param creator                the creator metadata
                 * @param creationTime           the creation epoch-second
                 *                               timestamp
                 * @param totalParticipantsCount the total participant count
                 * @param isExistingGroup        whether the user is already a
                 *                               participant
                 * @param hiddenGroup            the hidden-group state tag
                 */
                private Node(String id, Subject subject, Description description, Creator creator, Long creationTime, Long totalParticipantsCount, Boolean isExistingGroup, String hiddenGroup) {
                    this.id = id;
                    this.subject = subject;
                    this.description = description;
                    this.creator = creator;
                    this.creationTime = creationTime;
                    this.totalParticipantsCount = totalParticipantsCount;
                    this.isExistingGroup = isExistingGroup;
                    this.hiddenGroup = hiddenGroup;
                }

                /**
                 * Returns the subgroup identifier.
                 *
                 * @apiNote Empty when the relay omitted the {@code id} field
                 * from this suggested subgroup; WA Web treats this case as
                 * a hard error.
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
                 * Returns the subgroup description metadata.
                 *
                 * @apiNote Empty when the relay omitted the
                 * {@code description} GraphQL object; suggestions for groups
                 * without a description return empty here.
                 *
                 * @return an {@link Optional} containing the description, or
                 *         empty if absent
                 */
                public Optional<Description> description() {
                    return Optional.ofNullable(description);
                }

                /**
                 * Returns the subgroup creator metadata.
                 *
                 * @apiNote Empty when the relay omitted the {@code creator}
                 * GraphQL object; WA Web treats this case as a hard error.
                 *
                 * @return an {@link Optional} containing the creator, or
                 *         empty if absent
                 */
                public Optional<Creator> creator() {
                    return Optional.ofNullable(creator);
                }

                /**
                 * Returns the subgroup creation timestamp.
                 *
                 * @apiNote Widened from the wire epoch-second long through
                 * {@link Instant#ofEpochSecond(long)}; empty when the
                 * relay omitted the {@code creation_time} field.
                 *
                 * @return an {@link Optional} containing the
                 *         {@link Instant}, or empty if absent
                 */
                public Optional<Instant> creationTime() {
                    return Optional.ofNullable(creationTime).map(Instant::ofEpochSecond);
                }

                /**
                 * Returns the total participant count for the subgroup.
                 *
                 * @apiNote Empty when the relay omitted the
                 * {@code total_participants_count} field; callers that need
                 * a primitive can fall back to {@code 0}.
                 *
                 * @return an {@link OptionalLong} containing the count, or
                 *         empty if absent
                 */
                public OptionalLong totalParticipantsCount() {
                    return totalParticipantsCount != null ? OptionalLong.of(totalParticipantsCount) : OptionalLong.empty();
                }

                /**
                 * Returns whether the suggested subgroup is already an
                 * existing group the user is part of.
                 *
                 * @apiNote Returns {@code false} when the relay omitted the
                 * field; WA Web treats a missing field as a hard error, but
                 * Cobalt prefers to fall back to {@code false} so callers
                 * can decide.
                 *
                 * @return {@code true} when the flag is present and set,
                 *         {@code false} otherwise
                 */
                public boolean isExistingGroup() {
                    return isExistingGroup != null && isExistingGroup;
                }

                /**
                 * Returns the hidden-group state tag.
                 *
                 * @apiNote Empty when the GraphQL object did not project a
                 * {@code hidden_group} field.
                 *
                 * @return an {@link Optional} containing the tag, or empty
                 *         if absent
                 */
                public Optional<String> hiddenGroup() {
                    return Optional.ofNullable(hiddenGroup);
                }

                /**
                 * Subject metadata for a suggested subgroup.
                 *
                 * @apiNote Carries the subject text value only; unlike
                 * {@link FetchAllSubgroupsMexResponse.SubGroups.Edges.Node.Subject},
                 * the suggestions document does not project a creation
                 * timestamp here.
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
                     * Constructs a new subject record.
                     *
                     * @apiNote Package-private; instances are produced by
                     * the {@link Subject#of(JSONObject)} factory.
                     *
                     * @param value the subject text value
                     */
                    private Subject(String value) {
                        this.value = value;
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
                        return Optional.of(new Subject(value));
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
                 * Description metadata for a suggested subgroup.
                 *
                 * @apiNote Carries the description text and the description
                 * metadata identifier the relay assigns to each description
                 * change.
                 */
                public static final class Description {
                    /**
                     * Description text value.
                     *
                     * @apiNote User-visible group description as the relay
                     * last recorded it.
                     */
                    private final String value;

                    /**
                     * Description metadata identifier.
                     *
                     * @apiNote Opaque identifier the relay assigns to each
                     * description revision; used by WA Web to detect stale
                     * cached descriptions.
                     */
                    private final String id;

                    /**
                     * Constructs a new description record.
                     *
                     * @apiNote Package-private; instances are produced by
                     * the {@link Description#of(JSONObject)} factory.
                     *
                     * @param value the description text value
                     * @param id    the description metadata identifier
                     */
                    private Description(String value, String id) {
                        this.value = value;
                        this.id = id;
                    }

                    /**
                     * Returns the description text value.
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
                     * Returns the description metadata identifier.
                     *
                     * @apiNote Empty when the GraphQL object did not project
                     * an {@code id} field.
                     *
                     * @return an {@link Optional} containing the identifier,
                     *         or empty if absent
                     */
                    public Optional<String> id() {
                        return Optional.ofNullable(id);
                    }

                    /**
                     * Parses a description record from the given JSON
                     * object.
                     *
                     * @apiNote Package-private; invoked from the parent
                     * {@link Node#of(JSONObject)} factory.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed
                     *         record, or empty if {@code obj} is {@code null}
                     */
                    static Optional<Description> of(JSONObject obj) {
                        if (obj == null) {
                            return Optional.empty();
                        }

                        var value = obj.getString("value");
                        var id = obj.getString("id");
                        return Optional.of(new Description(value, id));
                    }

                    /**
                     * Parses a list of description records from the given
                     * JSON array.
                     *
                     * @apiNote Package-private; symmetry helper for callers
                     * that need to read array-shaped description data.
                     * Currently unused at the call sites of this response.
                     *
                     * @implNote This implementation skips {@code null}
                     * entries without raising.
                     *
                     * @param arr the JSON array to parse
                     * @return the list of parsed records, empty if
                     *         {@code arr} is {@code null}
                     */
                    static List<Description> ofArray(JSONArray arr) {
                        if (arr == null) {
                            return List.of();
                        }

                        var result = new ArrayList<Description>(arr.size());
                        for (var i = 0; i < arr.size(); i++) {
                            of(arr.getJSONObject(i)).ifPresent(result::add);
                        }
                        return result;
                    }
                }

                /**
                 * Creator metadata for a suggested subgroup.
                 *
                 * @apiNote Carries the creator WID; the suggestions document
                 * only projects the {@code id} field of the creator object.
                 */
                public static final class Creator {
                    /**
                     * Creator identifier.
                     *
                     * @apiNote The creator's WhatsApp WID stringified; WA
                     * Web treats a missing creator id as a hard error.
                     */
                    private final String id;

                    /**
                     * Constructs a new creator record.
                     *
                     * @apiNote Package-private; instances are produced by
                     * the {@link Creator#of(JSONObject)} factory.
                     *
                     * @param id the creator identifier
                     */
                    private Creator(String id) {
                        this.id = id;
                    }

                    /**
                     * Returns the creator identifier.
                     *
                     * @apiNote Empty when the GraphQL object did not project
                     * an {@code id} field.
                     *
                     * @return an {@link Optional} containing the identifier,
                     *         or empty if absent
                     */
                    public Optional<String> id() {
                        return Optional.ofNullable(id);
                    }

                    /**
                     * Parses a creator record from the given JSON object.
                     *
                     * @apiNote Package-private; invoked from the parent
                     * {@link Node#of(JSONObject)} factory.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed
                     *         record, or empty if {@code obj} is {@code null}
                     */
                    static Optional<Creator> of(JSONObject obj) {
                        if (obj == null) {
                            return Optional.empty();
                        }

                        var id = obj.getString("id");
                        return Optional.of(new Creator(id));
                    }

                    /**
                     * Parses a list of creator records from the given JSON
                     * array.
                     *
                     * @apiNote Package-private; symmetry helper for callers
                     * that need to read array-shaped creator data. Currently
                     * unused at the call sites of this response.
                     *
                     * @implNote This implementation skips {@code null}
                     * entries without raising.
                     *
                     * @param arr the JSON array to parse
                     * @return the list of parsed records, empty if
                     *         {@code arr} is {@code null}
                     */
                    static List<Creator> ofArray(JSONArray arr) {
                        if (arr == null) {
                            return List.of();
                        }

                        var result = new ArrayList<Creator>(arr.size());
                        for (var i = 0; i < arr.size(); i++) {
                            of(arr.getJSONObject(i)).ifPresent(result::add);
                        }
                        return result;
                    }
                }

                /**
                 * Parses a suggested subgroup node from the given JSON
                 * object.
                 *
                 * @apiNote Package-private; invoked from
                 * {@link Edges#of(JSONObject)} to project the
                 * {@code edge.node} GraphQL object.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed node, or
                 *         empty if {@code obj} is {@code null}
                 */
                static Optional<Node> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var id = obj.getString("id");
                    var subject = Subject.of(obj.getJSONObject("subject")).orElse(null);
                    var description = Description.of(obj.getJSONObject("description")).orElse(null);
                    var creator = Creator.of(obj.getJSONObject("creator")).orElse(null);
                    var creationTime = obj.getLong("creation_time");
                    var totalParticipantsCount = obj.getLong("total_participants_count");
                    var isExistingGroup = obj.getBoolean("is_existing_group");
                    var hiddenGroup = obj.getString("hidden_group");
                    return Optional.of(new Node(id, subject, description, creator, creationTime, totalParticipantsCount, isExistingGroup, hiddenGroup));
                }

                /**
                 * Parses a list of suggested subgroup nodes from the given
                 * JSON array.
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
             * {@link SubGroupSuggestions#of(JSONObject)} per array element
             * of {@code sub_group_suggestions.edges}.
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
             * {@link SubGroupSuggestions#of(JSONObject)} to project the
             * entire {@code edges} array in one shot.
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
         * Parses a suggestions container from the given JSON object.
         *
         * @apiNote Package-private; invoked from
         * {@link FetchSubgroupSuggestionsMexResponse#of(byte[])} to project
         * the {@code sub_group_suggestions} GraphQL container.
         *
         * @param obj the JSON object to parse
         * @return an {@link Optional} containing the parsed container, or
         *         empty if {@code obj} is {@code null}
         */
        static Optional<SubGroupSuggestions> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var edges = Edges.ofArray(obj.getJSONArray("edges"));
            return Optional.of(new SubGroupSuggestions(edges));
        }

        /**
         * Parses a list of suggestions containers from the given JSON array.
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
        static List<SubGroupSuggestions> ofArray(JSONArray arr) {
            if (arr == null) {
                return List.of();
            }

            var result = new ArrayList<SubGroupSuggestions>(arr.size());
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
     * WA Web's pre-check before destructuring; a missing
     * {@code sub_group_suggestions} container collapses to {@code null} on
     * the response rather than raising, which is laxer than WA Web's
     * {@code ServerStatusCodeError(500)} path so callers can decide how to
     * surface the gap.
     *
     * @param json the raw JSON bytes from the {@code <result>} child
     * @return an {@link Optional} containing the parsed response, or empty
     *         if the envelope is missing
     */
    private static Optional<FetchSubgroupSuggestionsMexResponse> of(byte[] json) {
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
        var subGroupSuggestions = SubGroupSuggestions.of(root.getJSONObject("sub_group_suggestions")).orElse(null);

        return Optional.of(new FetchSubgroupSuggestionsMexResponse(id, subGroupSuggestions));
    }
}
