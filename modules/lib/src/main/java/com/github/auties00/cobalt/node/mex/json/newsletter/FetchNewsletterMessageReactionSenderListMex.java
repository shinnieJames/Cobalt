package com.github.auties00.cobalt.node.mex.json.newsletter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.mex.json.MexJsonOperation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Fetches the list of users who reacted to a newsletter message with a given emoji.
 *
 * <p>Admins can view who reacted to a specific newsletter message with a chosen reaction emoji. The result is paginated so large reaction sets can be walked in batches.
 *
 * @implNote WAWebMexFetchNewsletterMessageReactionSenderListJob: adapts the {@code mexFetchNewsletterMessageReactionSenderList} GraphQL query,
 * which in WA Web is invoked via {@code WAWebMexClient.fetchQuery} and
 * whose response is unwrapped by the same module. Cobalt models the request
 * and response as sibling variants of a sealed interface rather than a
 * free-standing async function.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterMessageReactionSenderListJob")
public sealed interface FetchNewsletterMessageReactionSenderListMex extends MexJsonOperation permits FetchNewsletterMessageReactionSenderListMex.Request, FetchNewsletterMessageReactionSenderListMex.Response {
    /**
     * The numeric GraphQL query identifier assigned by the WhatsApp relay
     * to the {@code FetchNewsletterMessageReactionSenderList} compiled query.
     *
     * @implNote WAWebMexFetchNewsletterMessageReactionSenderListJobQuery.graphql: corresponds to the compiled
     * document id registered for the {@code mexFetchNewsletterMessageReactionSenderList} query.
     */
    String QUERY_ID = "29575462448733991";

    /**
     * The request variant of {@link FetchNewsletterMessageReactionSenderListMex} that serialises the
     * query variables and emits the outbound IQ stanza.
     *
     * @implNote WAWebMexFetchNewsletterMessageReactionSenderListJob.mexFetchNewsletterMessageReactionSenderList: adapts the {@code variables}
     * object constructed inline in the JS implementation into a dedicated
     * Java class.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterMessageReactionSenderListJob")
    final class Request implements FetchNewsletterMessageReactionSenderListMex {
        private final String newsletterId;
        private final long serverId;

        /**
         * Creates a new request that targets the reaction senders for
         * {@code (newsletterId, serverId)}.
         *
         * @implNote WAWebMexFetchNewsletterMessageReactionSenderListJob.mexFetchNewsletterMessageReactionSenderList: mirrors
         * the JS function signature {@code function c(e, t)} where {@code e} is
         * the newsletter id and {@code t} is the server-assigned message id.
         * Both values land in the nested {@code variables.input} object.
         * @param newsletterId the newsletter id (becomes {@code input.id});
         *                     must not be {@code null}
         * @param serverId     the server-assigned message id (becomes
         *                     {@code input.server_id})
         */
        public Request(String newsletterId, long serverId) {
            this.newsletterId = Objects.requireNonNull(newsletterId, "newsletterId");
            this.serverId = serverId;
        }

        /**
         * Builds the IQ stanza that dispatches this operation to the
         * WhatsApp relay.
         *
         * @implNote WAWebMexFetchNewsletterMessageReactionSenderListJob.mexFetchNewsletterMessageReactionSenderList: WA Web builds the
         * variables object as {@code {input: {id: e, server_id: t}}} inline and
         * delegates to {@code WAWebMexClient.fetchQuery}. Cobalt writes the
         * same nested envelope directly via {@code fastjson2.JSONWriter} and
         * wraps it through {@link MexJsonOperation#createMexNode(String, String)}.
         * @return a {@link NodeBuilder} carrying the IQ envelope and the
         *         serialised GraphQL variables
         */
        @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterMessageReactionSenderListJob", exports = "mexFetchNewsletterMessageReactionSenderList",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public NodeBuilder toNode() {
            // WAWebMexFetchNewsletterMessageReactionSenderListJob.mexFetchNewsletterMessageReactionSenderList
            // Opens a UTF-8 JSON writer that will serialise the GraphQL variables envelope
            try (var writer = JSONWriter.ofUTF8()) {
                // WAWebMexFetchNewsletterMessageReactionSenderListJob.mexFetchNewsletterMessageReactionSenderList
                // Begins the outer envelope and the nested "variables" object consumed by WAWebMexClient.fetchQuery
                writer.startObject();
                writer.writeName("variables");
                writer.writeColon();
                writer.startObject();
                // WAWebMexFetchNewsletterMessageReactionSenderListJob.mexFetchNewsletterMessageReactionSenderList:
                // var a = { input: { id: e, server_id: t } }
                writer.writeName("input");
                writer.writeColon();
                writer.startObject();
                writer.writeName("id");
                writer.writeColon();
                writer.writeString(newsletterId);
                writer.writeName("server_id");
                writer.writeColon();
                writer.writeInt64(serverId);
                writer.endObject();
                writer.endObject();
                writer.endObject();

                // ADAPTED: WAWebMexFetchNewsletterMessageReactionSenderListJob.mexFetchNewsletterMessageReactionSenderList
                // Flushes the JSON buffer into a StringWriter and wraps it in the shared MEX IQ envelope
                try (var output = new StringWriter()) {
                    writer.flushTo(output);
                    return MexJsonOperation.createMexNode(QUERY_ID, output.toString());
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }

    /**
     * The response variant of {@link FetchNewsletterMessageReactionSenderListMex} that exposes the data
     * returned by the server after a successful query.
     *
     * @implNote WAWebMexFetchNewsletterMessageReactionSenderListJob: adapts the JSON root returned by the GraphQL
     * query into a Java value object.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterMessageReactionSenderListJob")
    final class Response implements FetchNewsletterMessageReactionSenderListMex {
        private final List<Reactions> reactions;

        private Response(List<Reactions> reactions) {
            this.reactions = reactions;
        }

        /**
         * Parses a MEX response from the given IQ response node.
         *
         * @implNote WAWebMexFetchNewsletterMessageReactionSenderListJob.mexFetchNewsletterMessageReactionSenderList: WA Web relies on the
         * GraphQL client to unwrap the response. Cobalt performs the
         * unwrapping manually from the IQ {@code <result>} child.
         * @param node the IQ response node received from the relay
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the node is missing a result payload
         */
        public static Optional<Response> of(Node node) {
            return node.getChild("result")
                    .flatMap(Node::toContentBytes)
                    .flatMap(Response::of);
        }

        /**
         * Returns the {@code reactions} field.
         *
         * @return the list of values, empty if absent
         */
        public List<Reactions> reactions() {
            return reactions;
        }

        /**
         * A parsed {@code Reactions} object.
         */
        public static final class Reactions {
            private final String reactionCode;
            private final SenderList senderList;

            private Reactions(String reactionCode, SenderList senderList) {
                this.reactionCode = reactionCode;
                this.senderList = senderList;
            }

            /**
             * Returns the {@code reaction_code} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> reactionCode() {
                return Optional.ofNullable(reactionCode);
            }

            /**
             * Returns the {@code sender_list} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<SenderList> senderList() {
                return Optional.ofNullable(senderList);
            }

            /**
             * A parsed {@code SenderList} object.
             */
            public static final class SenderList {
                private final List<Edges> edges;

                private SenderList(List<Edges> edges) {
                    this.edges = edges;
                }

                /**
                 * Returns the {@code edges} field.
                 *
                 * @return the list of values, empty if absent
                 */
                public List<Edges> edges() {
                    return edges;
                }

                /**
                 * A parsed {@code Edges} object.
                 */
                public static final class Edges {
                    private final Node node;

                    private Edges(Node node) {
                        this.node = node;
                    }

                    /**
                     * Returns the {@code node} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<Node> node() {
                        return Optional.ofNullable(node);
                    }

                    /**
                     * A parsed {@code Node} object.
                     */
                    public static final class Node {
                        private final String id;
                        private final String profilePicDirectPath;

                        private Node(String id, String profilePicDirectPath) {
                            this.id = id;
                            this.profilePicDirectPath = profilePicDirectPath;
                        }

                        /**
                         * Returns the {@code id} field.
                         *
                         * @return an {@link Optional} containing the value, or empty if absent
                         */
                        public Optional<String> id() {
                            return Optional.ofNullable(id);
                        }

                        /**
                         * Returns the {@code profile_pic_direct_path} field.
                         *
                         * @return an {@link Optional} containing the value, or empty if absent
                         */
                        public Optional<String> profilePicDirectPath() {
                            return Optional.ofNullable(profilePicDirectPath);
                        }

                        /**
                         * Parses a {@code Node} from the given JSON object.
                         *
                         * @param obj the JSON object to parse
                         * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                         */
                        static Optional<Node> of(JSONObject obj) {
                            if (obj == null) {
                                return Optional.empty();
                            }

                            var id = obj.getString("id");
                            var profilePicDirectPath = obj.getString("profile_pic_direct_path");
                            return Optional.of(new Node(id, profilePicDirectPath));
                        }

                        /**
                         * Parses a list of {@code Node} from the given JSON array.
                         *
                         * @param arr the JSON array to parse
                         * @return the list of parsed results, empty if {@code arr} is {@code null}
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
                     * Parses a {@code Edges} from the given JSON object.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                     */
                    static Optional<Edges> of(JSONObject obj) {
                        if (obj == null) {
                            return Optional.empty();
                        }

                        var node = Node.of(obj.getJSONObject("node")).orElse(null);
                        return Optional.of(new Edges(node));
                    }

                    /**
                     * Parses a list of {@code Edges} from the given JSON array.
                     *
                     * @param arr the JSON array to parse
                     * @return the list of parsed results, empty if {@code arr} is {@code null}
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
                 * Parses a {@code SenderList} from the given JSON object.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                 */
                static Optional<SenderList> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var edges = Edges.ofArray(obj.getJSONArray("edges"));
                    return Optional.of(new SenderList(edges));
                }

                /**
                 * Parses a list of {@code SenderList} from the given JSON array.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed results, empty if {@code arr} is {@code null}
                 */
                static List<SenderList> ofArray(JSONArray arr) {
                    if (arr == null) {
                        return List.of();
                    }

                    var result = new ArrayList<SenderList>(arr.size());
                    for (var i = 0; i < arr.size(); i++) {
                        of(arr.getJSONObject(i)).ifPresent(result::add);
                    }
                    return result;
                }
            }

            /**
             * Parses a {@code Reactions} from the given JSON object.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
             */
            static Optional<Reactions> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var reactionCode = obj.getString("reaction_code");
                var senderList = SenderList.of(obj.getJSONObject("sender_list")).orElse(null);
                return Optional.of(new Reactions(reactionCode, senderList));
            }

            /**
             * Parses a list of {@code Reactions} from the given JSON array.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed results, empty if {@code arr} is {@code null}
             */
            static List<Reactions> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<Reactions>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Parses a {@link Response} from the raw JSON bytes of the
         * {@code <result>} child.
         *
         * @implNote WAWebMexFetchNewsletterMessageReactionSenderListJob.mexFetchNewsletterMessageReactionSenderList: mirrors the implicit
         * unwrapping that WA Web performs on the GraphQL response,
         * extracting the {@code xwa2_newsletters_reaction_sender_list} root.
         * @param json the UTF-8 encoded JSON payload
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the envelope is missing expected fields
         */
        private static Optional<Response> of(byte[] json) {
            // WAWebMexFetchNewsletterMessageReactionSenderListJob.mexFetchNewsletterMessageReactionSenderList
            // Parses the raw JSON payload, bailing out if fastjson2 returns null
            var jsonObject = JSON.parseObject(json);
            if (jsonObject == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterMessageReactionSenderListJob.mexFetchNewsletterMessageReactionSenderList
            // Descends into the standard GraphQL "data" envelope
            var data = jsonObject.getJSONObject("data");
            if (data == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterMessageReactionSenderListJob.mexFetchNewsletterMessageReactionSenderList
            // Extracts the operation-specific root keyed by xwa2_newsletters_reaction_sender_list
            var root = data.getJSONObject("xwa2_newsletters_reaction_sender_list");
            if (root == null) {
                return Optional.empty();
            }

            var reactions = Reactions.ofArray(root.getJSONArray("reactions"));

            return Optional.of(new Response(reactions));
        }
    }
}
