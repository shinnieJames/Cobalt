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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fetches a paginated list of followers for a given newsletter.
 *
 * <p>Each follower entry includes the member identifier, role, follow time and optional admin profile metadata. Admins use this query to display the follower roster and manage roles.
 *
 * @implNote WAWebMexFetchNewsletterFollowersJob: adapts the {@code mexFetchNewsletterFollowers} GraphQL query,
 * which in WA Web is invoked via {@code WAWebMexClient.fetchQuery} and
 * whose response is unwrapped by the same module. Cobalt models the request
 * and response as sibling variants of a sealed interface rather than a
 * free-standing async function.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterFollowersJob")
public sealed interface FetchNewsletterFollowersMex extends MexJsonOperation permits FetchNewsletterFollowersMex.Request, FetchNewsletterFollowersMex.Response {
    /**
     * The numeric GraphQL query identifier assigned by the WhatsApp relay
     * to the {@code FetchNewsletterFollowers} compiled query.
     *
     * @implNote WAWebMexFetchNewsletterFollowersJobQuery.graphql: corresponds to the compiled
     * document id registered for the {@code mexFetchNewsletterFollowers} query.
     */
    String QUERY_ID = "25895136756785869";

    /**
     * The request variant of {@link FetchNewsletterFollowersMex} that serialises the
     * query variables and emits the outbound IQ stanza.
     *
     * @implNote WAWebMexFetchNewsletterFollowersJob.mexFetchNewsletterFollowers: adapts the {@code variables}
     * object constructed inline in the JS implementation
     * ({@code {input:{newsletter_id:e, count:Math.min(WAWebNewsletterGatingUtils.getMaxSubscriberNumber(), t)}}})
     * into a dedicated Java class. The clamping against
     * {@code getMaxSubscriberNumber()} is the caller's responsibility.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterFollowersJob")
    final class Request implements FetchNewsletterFollowersMex {
        private final String newsletterId;
        private final Integer count;

        /**
         * Constructs a request for the given newsletter and follower page size.
         *
         * @param newsletterId the newsletter JID, written into the
         *                     {@code input.newsletter_id} variable when
         *                     non-{@code null}
         * @param count        the requested follower page size, written into
         *                     {@code input.count} when non-{@code null}; the
         *                     caller is responsible for clamping this to
         *                     {@code WAWebNewsletterGatingUtils.getMaxSubscriberNumber()}
         */
        public Request(String newsletterId, Integer count) {
            this.newsletterId = newsletterId;
            this.count = count;
        }

        /**
         * Builds the IQ stanza that dispatches this operation to the
         * WhatsApp relay.
         *
         * @implNote WAWebMexFetchNewsletterFollowersJob.mexFetchNewsletterFollowers: WA Web constructs the
         * {@code variables} object inline as
         * {@code {input:{newsletter_id:e, count:Math.min(getMaxSubscriberNumber(), t)}}}
         * and delegates to {@code WAWebMexClient.fetchQuery}. Cobalt writes
         * the JSON directly via {@code fastjson2.JSONWriter} and wraps it
         * through {@link MexJsonOperation#createMexNode(String, String)}.
         * @return a {@link NodeBuilder} carrying the IQ envelope and the
         *         serialised GraphQL variables
         */
        @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterFollowersJob", exports = "mexFetchNewsletterFollowers",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public NodeBuilder toNode() {
            // WAWebMexFetchNewsletterFollowersJob.mexFetchNewsletterFollowers
            // Opens a UTF-8 JSON writer that will serialise the GraphQL variables envelope
            try (var writer = JSONWriter.ofUTF8()) {
                // WAWebMexFetchNewsletterFollowersJob.mexFetchNewsletterFollowers
                // Begins the outer envelope and the nested "variables" object consumed by WAWebMexClient.fetchQuery
                writer.startObject();
                writer.writeName("variables");
                writer.writeColon();
                writer.startObject();

                // WAWebMexFetchNewsletterFollowersJob.mexFetchNewsletterFollowers
                // Emits {input:{newsletter_id:e, count:Math.min(getMaxSubscriberNumber(),t)}}; the inner
                // object mirrors the JS object literal shape. Each scalar is omitted when null, leaving
                // the GraphQL schema defaults to apply server-side.
                writer.writeName("input");
                writer.writeColon();
                writer.startObject();
                if (newsletterId != null) {
                    writer.writeName("newsletter_id");
                    writer.writeColon();
                    writer.writeString(newsletterId);
                }
                if (count != null) {
                    writer.writeName("count");
                    writer.writeColon();
                    writer.writeInt32(count);
                }
                writer.endObject();
                writer.endObject();
                writer.endObject();

                // ADAPTED: WAWebMexFetchNewsletterFollowersJob.mexFetchNewsletterFollowers
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
     * The response variant of {@link FetchNewsletterFollowersMex} that exposes the data
     * returned by the server after a successful query.
     *
     * @implNote WAWebMexFetchNewsletterFollowersJob: adapts the JSON root returned by the GraphQL
     * query into a Java value object.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterFollowersJob")
    final class Response implements FetchNewsletterFollowersMex {
        private final Followers followers;

        private Response(Followers followers) {
            this.followers = followers;
        }

        /**
         * Parses a MEX response from the given IQ response node.
         *
         * @implNote WAWebMexFetchNewsletterFollowersJob.mexFetchNewsletterFollowers: WA Web relies on the
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
         * Returns the {@code followers} field.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<Followers> followers() {
            return Optional.ofNullable(followers);
        }

        /**
         * A parsed {@code Followers} object.
         */
        public static final class Followers {
            private final List<Edges> edges;

            private Followers(List<Edges> edges) {
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
                private final Long followTime;
                private final String role;

                private Edges(Node node, Long followTime, String role) {
                    this.node = node;
                    this.followTime = followTime;
                    this.role = role;
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
                 * Returns the {@code follow_time} field.
                 *
                 * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
                 */
                public Optional<Instant> followTime() {
                    return Optional.ofNullable(followTime).map(Instant::ofEpochSecond);
                }

                /**
                 * Returns the {@code role} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> role() {
                    return Optional.ofNullable(role);
                }

                /**
                 * A parsed {@code Node} object.
                 */
                public static final class Node {
                    private final String id;
                    private final String displayName;
                    private final String pn;
                    private final UsernameInfo usernameInfo;

                    private Node(String id, String displayName, String pn, UsernameInfo usernameInfo) {
                        this.id = id;
                        this.displayName = displayName;
                        this.pn = pn;
                        this.usernameInfo = usernameInfo;
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
                     * Returns the {@code display_name} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> displayName() {
                        return Optional.ofNullable(displayName);
                    }

                    /**
                     * Returns the {@code pn} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> pn() {
                        return Optional.ofNullable(pn);
                    }

                    /**
                     * Returns the {@code username_info} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<UsernameInfo> usernameInfo() {
                        return Optional.ofNullable(usernameInfo);
                    }

                    /**
                     * A parsed {@code UsernameInfo} object.
                     */
                    public static final class UsernameInfo {
                        private final String username;

                        private UsernameInfo(String username) {
                            this.username = username;
                        }

                        /**
                         * Returns the {@code username} field.
                         *
                         * @return an {@link Optional} containing the value, or empty if absent
                         */
                        public Optional<String> username() {
                            return Optional.ofNullable(username);
                        }

                        /**
                         * Parses a {@code UsernameInfo} from the given JSON object.
                         *
                         * @param obj the JSON object to parse
                         * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                         */
                        static Optional<UsernameInfo> of(JSONObject obj) {
                            if (obj == null) {
                                return Optional.empty();
                            }

                            var username = obj.getString("username");
                            return Optional.of(new UsernameInfo(username));
                        }

                        /**
                         * Parses a list of {@code UsernameInfo} from the given JSON array.
                         *
                         * @param arr the JSON array to parse
                         * @return the list of parsed results, empty if {@code arr} is {@code null}
                         */
                        static List<UsernameInfo> ofArray(JSONArray arr) {
                            if (arr == null) {
                                return List.of();
                            }

                            var result = new ArrayList<UsernameInfo>(arr.size());
                            for (var i = 0; i < arr.size(); i++) {
                                of(arr.getJSONObject(i)).ifPresent(result::add);
                            }
                            return result;
                        }
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
                        var displayName = obj.getString("display_name");
                        var pn = obj.getString("pn");
                        var usernameInfo = UsernameInfo.of(obj.getJSONObject("username_info")).orElse(null);
                        return Optional.of(new Node(id, displayName, pn, usernameInfo));
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
                    var followTime = obj.getLong("follow_time");
                    var role = obj.getString("role");
                    return Optional.of(new Edges(node, followTime, role));
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
             * Parses a {@code Followers} from the given JSON object.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
             */
            static Optional<Followers> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var edges = Edges.ofArray(obj.getJSONArray("edges"));
                return Optional.of(new Followers(edges));
            }

            /**
             * Parses a list of {@code Followers} from the given JSON array.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed results, empty if {@code arr} is {@code null}
             */
            static List<Followers> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<Followers>(arr.size());
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
         * @implNote WAWebMexFetchNewsletterFollowersJob.mexFetchNewsletterFollowers: mirrors the implicit
         * unwrapping that WA Web performs on the GraphQL response,
         * extracting the {@code xwa2_newsletter_followers} root.
         * @param json the UTF-8 encoded JSON payload
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the envelope is missing expected fields
         */
        private static Optional<Response> of(byte[] json) {
            // WAWebMexFetchNewsletterFollowersJob.mexFetchNewsletterFollowers
            // Parses the raw JSON payload, bailing out if fastjson2 returns null
            var jsonObject = JSON.parseObject(json);
            if (jsonObject == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterFollowersJob.mexFetchNewsletterFollowers
            // Descends into the standard GraphQL "data" envelope
            var data = jsonObject.getJSONObject("data");
            if (data == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterFollowersJob.mexFetchNewsletterFollowers
            // Extracts the operation-specific root keyed by xwa2_newsletter_followers
            var root = data.getJSONObject("xwa2_newsletter_followers");
            if (root == null) {
                return Optional.empty();
            }

            var followers = Followers.of(root.getJSONObject("followers")).orElse(null);

            return Optional.of(new Response(followers));
        }
    }
}
