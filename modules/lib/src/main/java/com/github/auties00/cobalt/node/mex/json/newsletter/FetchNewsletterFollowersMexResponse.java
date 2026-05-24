package com.github.auties00.cobalt.node.mex.json.newsletter;

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

/**
 * Parses the MEX response of the fetch-newsletter-followers query
 * built by {@link FetchNewsletterFollowersMexRequest}.
 *
 * @apiNote
 * Exposes the follower roster echoed under
 * {@code xwa2_newsletter_followers}; the {@link Followers} sub-object
 * wraps the Relay-style {@code edges} array where each {@link Followers.Edges}
 * carries one follower's profile (id, optional phone number, optional
 * username), role and follow time.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterFollowersJob")
public final class FetchNewsletterFollowersMexResponse implements MexOperation.Response.Json {
    /**
     * The follower-edges container.
     */
    private final Followers followers;

    /**
     * Constructs a response wrapping the parsed followers container.
     *
     * @apiNote
     * Reserved for the static parser.
     *
     * @param followers the follower-edges container
     */
    private FetchNewsletterFollowersMexResponse(Followers followers) {
        this.followers = followers;
    }

    /**
     * Parses the MEX response carried by the given IQ result node.
     *
     * @apiNote
     * Drains the {@code <result>} child's byte content into the JSON parser;
     * the returned {@link Optional} is empty when the result child is
     * missing or when the JSON envelope omits the expected
     * {@code data.xwa2_newsletter_followers} root.
     *
     * @param node the IQ result node received from the relay
     * @return the parsed response, or empty when the node does not carry a
     *         well-formed result payload
     */
    public static Optional<FetchNewsletterFollowersMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(FetchNewsletterFollowersMexResponse::of);
    }

    /**
     * Returns the follower-edges container.
     *
     * @return the parsed {@link Followers}, or empty when the relay omitted
     *         the field
     */
    public Optional<Followers> followers() {
        return Optional.ofNullable(followers);
    }

    /**
     * Wraps the {@code followers} sub-object.
     *
     * @apiNote
     * Holds the Relay-style {@code edges} array; an empty list with no
     * edges indicates the newsletter has no followers visible to the
     * caller.
     */
    public static final class Followers {
        /**
         * The Relay-style edges of the follower connection.
         */
        private final List<Edges> edges;

        /**
         * Constructs a followers wrapper from the parsed sub-fields.
         *
         * @apiNote
         * Reserved for the static parser.
         *
         * @param edges the follower edges
         */
        private Followers(List<Edges> edges) {
            this.edges = edges;
        }

        /**
         * Returns the follower edges.
         *
         * @return the parsed edges, empty when the relay returned none
         */
        public List<Edges> edges() {
            return edges;
        }

        /**
         * Wraps one entry of the {@code edges} array.
         *
         * @apiNote
         * Carries one follower's profile {@link Node}, the per-follower
         * {@code follow_time} epoch-second, and the {@code role} label
         * ({@code OWNER}/{@code ADMIN}/{@code SUBSCRIBER}); WA Web sorts
         * admins and owners ahead of subscribers in the UI.
         */
        public static final class Edges {
            /**
             * The follower profile sub-object.
             */
            private final Node node;

            /**
             * The follow epoch-second.
             */
            private final Long followTime;

            /**
             * The follower role label.
             */
            private final String role;

            /**
             * Constructs an edge wrapper from the parsed sub-fields.
             *
             * @apiNote
             * Reserved for the static parser.
             *
             * @param node       the follower profile sub-object
             * @param followTime the follow epoch-second
             * @param role       the follower role label
             */
            private Edges(Node node, Long followTime, String role) {
                this.node = node;
                this.followTime = followTime;
                this.role = role;
            }

            /**
             * Returns the follower profile sub-object.
             *
             * @return the parsed {@link Node}, or empty when the relay
             *         omitted the field
             */
            public Optional<Node> node() {
                return Optional.ofNullable(node);
            }

            /**
             * Returns the follow instant.
             *
             * @return the follow instant, or empty when the relay omitted
             *         the field
             */
            public Optional<Instant> followTime() {
                return Optional.ofNullable(followTime).map(Instant::ofEpochSecond);
            }

            /**
             * Returns the follower role label.
             *
             * @return the role label, or empty when the relay omitted the
             *         field
             */
            public Optional<String> role() {
                return Optional.ofNullable(role);
            }

            /**
             * Wraps the follower profile {@code node} sub-object.
             *
             * @apiNote
             * Carries the follower's Jid string ({@code id}), a display
             * name, an optional phone number string ({@code pn}), and an
             * optional username sub-object populated only when WA Web's
             * username-PN-privacy gate is on.
             */
            public static final class Node {
                /**
                 * The follower Jid string.
                 */
                private final String id;

                /**
                 * The follower display name.
                 */
                private final String displayName;

                /**
                 * The follower phone-number string.
                 */
                private final String pn;

                /**
                 * The follower username sub-object.
                 */
                private final UsernameInfo usernameInfo;

                /**
                 * Constructs a node wrapper from the parsed sub-fields.
                 *
                 * @apiNote
                 * Reserved for the static parser.
                 *
                 * @param id           the follower Jid string
                 * @param displayName  the follower display name
                 * @param pn           the follower phone-number string
                 * @param usernameInfo the follower username sub-object
                 */
                private Node(String id, String displayName, String pn, UsernameInfo usernameInfo) {
                    this.id = id;
                    this.displayName = displayName;
                    this.pn = pn;
                    this.usernameInfo = usernameInfo;
                }

                /**
                 * Returns the follower Jid string.
                 *
                 * @return the Jid string, or empty when the relay omitted
                 *         the field
                 */
                public Optional<String> id() {
                    return Optional.ofNullable(id);
                }

                /**
                 * Returns the follower display name.
                 *
                 * @return the display name, or empty when the relay omitted
                 *         the field
                 */
                public Optional<String> displayName() {
                    return Optional.ofNullable(displayName);
                }

                /**
                 * Returns the follower phone-number string.
                 *
                 * @return the phone number, or empty when the relay omitted
                 *         the field
                 */
                public Optional<String> pn() {
                    return Optional.ofNullable(pn);
                }

                /**
                 * Returns the follower username sub-object.
                 *
                 * @return the parsed {@link UsernameInfo}, or empty when
                 *         the relay omitted the field
                 */
                public Optional<UsernameInfo> usernameInfo() {
                    return Optional.ofNullable(usernameInfo);
                }

                /**
                 * Wraps the {@code username_info} sub-object.
                 *
                 * @apiNote
                 * Populated only when WA Web's
                 * {@code WAWebUsernameWorkerCompatibleGatingUtils.isNewsletterUsernamePnPrivacyEnabled()}
                 * gate is on; otherwise the relay omits the sub-object.
                 */
                public static final class UsernameInfo {
                    /**
                     * The follower username string.
                     */
                    private final String username;

                    /**
                     * Constructs a username-info wrapper from the parsed
                     * sub-fields.
                     *
                     * @apiNote
                     * Reserved for the static parser.
                     *
                     * @param username the follower username string
                     */
                    private UsernameInfo(String username) {
                        this.username = username;
                    }

                    /**
                     * Returns the follower username string.
                     *
                     * @return the username, or empty when the relay omitted
                     *         the field
                     */
                    public Optional<String> username() {
                        return Optional.ofNullable(username);
                    }

                    /**
                     * Parses a {@link UsernameInfo} from the given JSON
                     * object.
                     *
                     * @apiNote
                     * Used by {@link Node#of(JSONObject)} to hydrate the
                     * nested {@code username_info} entry.
                     *
                     * @param obj the JSON object to parse
                     * @return the parsed entry, or empty when {@code obj}
                     *         is {@code null}
                     */
                    static Optional<UsernameInfo> of(JSONObject obj) {
                        if (obj == null) {
                            return Optional.empty();
                        }

                        var username = obj.getString("username");
                        return Optional.of(new UsernameInfo(username));
                    }

                    /**
                     * Parses a list of {@link UsernameInfo} entries from
                     * the given JSON array.
                     *
                     * @apiNote
                     * Provided for symmetry.
                     *
                     * @param arr the JSON array to parse
                     * @return the parsed list, empty when {@code arr} is
                     *         {@code null}
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
                 * Parses a {@link Node} from the given JSON object.
                 *
                 * @apiNote
                 * Used by {@link Edges#of(JSONObject)} to hydrate the
                 * nested {@code node} entry.
                 *
                 * @param obj the JSON object to parse
                 * @return the parsed entry, or empty when {@code obj} is
                 *         {@code null}
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
                 * Parses a list of {@link Node} entries from the given JSON
                 * array.
                 *
                 * @apiNote
                 * Provided for symmetry.
                 *
                 * @param arr the JSON array to parse
                 * @return the parsed list, empty when {@code arr} is
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
             * Parses an {@link Edges} from the given JSON object.
             *
             * @apiNote
             * Used by {@link Followers#of(JSONObject)} to hydrate one
             * entry of the {@code edges} array.
             *
             * @param obj the JSON object to parse
             * @return the parsed entry, or empty when {@code obj} is
             *         {@code null}
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
             * Parses a list of {@link Edges} entries from the given JSON
             * array.
             *
             * @apiNote
             * Used by {@link Followers#of(JSONObject)} to hydrate the
             * {@code edges} array.
             *
             * @param arr the JSON array to parse
             * @return the parsed list, empty when {@code arr} is
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
         * Parses a {@link Followers} from the given JSON object.
         *
         * @apiNote
         * Used by {@link FetchNewsletterFollowersMexResponse#of(byte[])}
         * to hydrate the nested {@code followers} entry.
         *
         * @param obj the JSON object to parse
         * @return the parsed entry, or empty when {@code obj} is
         *         {@code null}
         */
        static Optional<Followers> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var edges = Edges.ofArray(obj.getJSONArray("edges"));
            return Optional.of(new Followers(edges));
        }

        /**
         * Parses a list of {@link Followers} entries from the given JSON
         * array.
         *
         * @apiNote
         * Provided for symmetry; the followers envelope does not carry a
         * {@code followers} array.
         *
         * @param arr the JSON array to parse
         * @return the parsed list, empty when {@code arr} is {@code null}
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
     * Parses the response from the raw UTF-8 JSON payload of the
     * {@code <result>} child.
     *
     * @apiNote
     * Reserved for the public {@link #of(Node)} overload.
     *
     * @implNote
     * This implementation guards every nested object lookup so a malformed
     * envelope produces {@link Optional#empty()} rather than a parser
     * exception.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return the parsed response, or empty when the envelope lacks the
     *         expected {@code data.xwa2_newsletter_followers} root
     */
    private static Optional<FetchNewsletterFollowersMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_newsletter_followers");
        if (root == null) {
            return Optional.empty();
        }

        var followers = Followers.of(root.getJSONObject("followers")).orElse(null);

        return Optional.of(new FetchNewsletterFollowersMexResponse(followers));
    }
}
