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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Parsed response of the {@code mexQuerySubgroupParticipantCountJob} MEX
 * query.
 *
 * @apiNote Carries the per-subgroup participant counts projected from
 * {@code data.xwa2_group_query_by_id.sub_groups.edges}. Paired with
 * {@link QuerySubgroupParticipantCountMexRequest}; consumed by
 * {@code WAWebQueryAndUpdateSubgroupParticipantCountAction} to refresh the
 * counter badges next to each subgroup in the community panel.
 *
 * @implNote This implementation preserves the GraphQL connection shape
 * ({@code sub_groups.edges[].node}) verbatim rather than flattening it to
 * a {@code Map<id, count>}, so callers can distinguish a missing
 * {@code sub_groups} container from an empty edges list. Unlike WA Web's
 * {@code WAWebMexQuerySubgroupParticipantCountJob}, missing edges or
 * {@code null} edge nodes are surfaced as empty fields rather than raised
 * as {@code ServerStatusCodeError(500)} errors.
 */
@WhatsAppWebModule(moduleName = "WAWebMexQuerySubgroupParticipantCountJob")
public final class QuerySubgroupParticipantCountMexResponse implements MexOperation.Response.Json {
    /**
     * Subgroup edges container returned by the relay.
     *
     * @apiNote Wraps the {@code edges} GraphQL array carrying subgroup ids
     * and participant counts; empty when the relay did not project the
     * container.
     */
    private final SubGroups subGroups;

    /**
     * Community group identifier returned by the relay.
     *
     * @apiNote Matches the {@code group_id} sent in the request through
     * {@code input.group_jid}.
     */
    private final String id;

    /**
     * Constructs a new response with the given fields.
     *
     * @apiNote Package-private; instances are produced by the
     * {@link #of(Node)} factory after parsing the inbound IQ payload.
     *
     * @param subGroups the subgroup edges container
     * @param id        the community group identifier
     */
    private QuerySubgroupParticipantCountMexResponse(SubGroups subGroups, String id) {
        this.subGroups = subGroups;
        this.id = id;
    }

    /**
     * Parses the MEX response carried by an inbound IQ stanza.
     *
     * @apiNote Entry point for receivers handling
     * {@code <iq xmlns="w:mex">} replies tagged with
     * {@link QuerySubgroupParticipantCountMexRequest#QUERY_ID}. Unwraps the
     * {@code <result>} child, reads its content bytes and decodes the
     * GraphQL JSON envelope.
     *
     * @param node the inbound IQ stanza carrying the {@code <result>} child
     * @return the parsed response, or {@link Optional#empty()} if the
     *         expected JSON shape is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebMexQuerySubgroupParticipantCountJob", exports = "mexQuerySubgroupParticipantCountJob",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<QuerySubgroupParticipantCountMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(QuerySubgroupParticipantCountMexResponse::of);
    }

    /**
     * Returns the subgroup edges container.
     *
     * @apiNote Empty when the relay omitted the {@code sub_groups} GraphQL
     * container; the edges list inside may itself be empty when the
     * request named a community with no regular subgroups.
     *
     * @return an {@link Optional} containing the container, or empty if
     *         absent
     */
    public Optional<SubGroups> subGroups() {
        return Optional.ofNullable(subGroups);
    }

    /**
     * Returns the community group identifier.
     *
     * @apiNote Matches the {@code group_jid} sent in the request input;
     * surfaced as an {@link Optional} because the field may be absent from
     * malformed relay replies.
     *
     * @return an {@link Optional} containing the identifier, or empty if
     *         absent
     */
    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    /**
     * Subgroup edges container wrapping the array of subgroup nodes
     * carrying participant counts.
     *
     * @apiNote Preserves the GraphQL connection shape; each {@link Edges}
     * entry wraps a single {@link Edges.Node}.
     */
    public static final class SubGroups {
        /**
         * Subgroup edges returned by the relay.
         *
         * @apiNote Mirrors the {@code sub_groups.edges} GraphQL array;
         * never {@code null} once {@link SubGroups#of(JSONObject)} has
         * succeeded.
         */
        private final List<Edges> edges;

        /**
         * Constructs a new container with the given edges.
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
         * @apiNote Iterate this list to walk the subgroups whose counts the
         * relay returned; each entry exposes a {@link Edges#node()}
         * accessor that may be empty when the relay returned a malformed
         * edge.
         *
         * @return the list of edges, empty if absent
         */
        public List<Edges> edges() {
            return edges;
        }

        /**
         * Single edge wrapper around a subgroup participant-count node.
         *
         * @apiNote The edge level exists to leave room for future GraphQL
         * cursor metadata without breaking the binary shape.
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
             * error, Cobalt prefers to surface it as
             * {@link Optional#empty()}.
             *
             * @return an {@link Optional} containing the node, or empty if
             *         absent
             */
            public Optional<Node> node() {
                return Optional.ofNullable(node);
            }

            /**
             * Subgroup participant-count node carrying the subgroup
             * identifier and the total participant count.
             *
             * @apiNote One per subgroup whose count was requested; this is
             * the projection consumed by
             * {@code WAWebQueryAndUpdateSubgroupParticipantCountAction} to
             * update the per-subgroup counter badge.
             */
            public static final class Node {
                /**
                 * Subgroup identifier.
                 *
                 * @apiNote The subgroup's WhatsApp WID stringified; in WA
                 * Web it is funnelled through
                 * {@code WAWebWidFactory.createWid}.
                 */
                private final String id;

                /**
                 * Total participant count for the subgroup.
                 *
                 * @apiNote Mirrors the {@code total_participants_count}
                 * GraphQL scalar; reflects all members regardless of
                 * status.
                 */
                private final Long totalParticipantsCount;

                /**
                 * Constructs a new node.
                 *
                 * @apiNote Package-private; instances are produced by the
                 * {@link Node#of(JSONObject)} factory.
                 *
                 * @param id                     the subgroup identifier
                 * @param totalParticipantsCount the total participant count
                 */
                private Node(String id, Long totalParticipantsCount) {
                    this.id = id;
                    this.totalParticipantsCount = totalParticipantsCount;
                }

                /**
                 * Returns the subgroup identifier.
                 *
                 * @apiNote Empty when the relay omitted the {@code id} field
                 * from this node.
                 *
                 * @return an {@link Optional} containing the identifier, or
                 *         empty if absent
                 */
                public Optional<String> id() {
                    return Optional.ofNullable(id);
                }

                /**
                 * Returns the total participant count for this subgroup.
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
                 * Parses a participant-count node from the given JSON
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
                    var totalParticipantsCount = obj.getLong("total_participants_count");
                    return Optional.of(new Node(id, totalParticipantsCount));
                }

                /**
                 * Parses a list of participant-count nodes from the given
                 * JSON array.
                 *
                 * @apiNote Package-private; symmetry helper for callers
                 * that need to read array-shaped node data. Currently
                 * unused at the call sites of this response.
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
         * Parses a subgroups container from the given JSON object.
         *
         * @apiNote Package-private; invoked from
         * {@link QuerySubgroupParticipantCountMexResponse#of(byte[])} to
         * project the {@code sub_groups} GraphQL container.
         *
         * @param obj the JSON object to parse
         * @return an {@link Optional} containing the parsed container, or
         *         empty if {@code obj} is {@code null}
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
     * WA Web's pre-check before destructuring; a missing {@code sub_groups}
     * container collapses to {@code null} on the response rather than
     * raising.
     *
     * @param json the raw JSON bytes from the {@code <result>} child
     * @return an {@link Optional} containing the parsed response, or empty
     *         if the envelope is missing
     */
    private static Optional<QuerySubgroupParticipantCountMexResponse> of(byte[] json) {
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

        var subGroups = SubGroups.of(root.getJSONObject("sub_groups")).orElse(null);
        var id = root.getString("id");

        return Optional.of(new QuerySubgroupParticipantCountMexResponse(subGroups, id));
    }
}
