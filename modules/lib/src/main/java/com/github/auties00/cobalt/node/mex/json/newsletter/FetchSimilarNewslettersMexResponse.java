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
 * Parses the MEX response of the fetch-similar-newsletters query built by
 * {@link FetchSimilarNewslettersMexRequest}.
 *
 * @apiNote
 * Surfaces the relay's {@code data.xwa2_newsletters_similar.result} array
 * as one {@link Result} per similar newsletter; each result carries the
 * newsletter Jid, a small {@link Result.ThreadMetadata} block (name,
 * thumbnail picture, verification badge) and a {@link Result.State}
 * lifecycle marker. Mirrors the slim payload WA Web's
 * {@code WAWebNewsletterDirectorySearchQueryJob} feeds into the similar-
 * channels carousel.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchSimilarNewslettersJob")
public final class FetchSimilarNewslettersMexResponse implements MexOperation.Response.Json {
    /**
     * The parsed list of similar newsletters; ordered as returned by the
     * relay.
     */
    private final List<Result> result;

    /**
     * Constructs a response wrapping the parsed similar-newsletter list.
     *
     * @apiNote
     * Reserved for the static parser; external callers obtain instances via
     * {@link #of(Node)}.
     *
     * @param result the parsed similar-newsletter list
     */
    private FetchSimilarNewslettersMexResponse(List<Result> result) {
        this.result = result;
    }

    /**
     * Parses the MEX response carried by the given IQ result node.
     *
     * @apiNote
     * Drains the {@code <result>} child's byte content into the JSON parser;
     * the returned {@link Optional} is empty when the result child is
     * missing or when the JSON envelope omits the expected
     * {@code data.xwa2_newsletters_similar} root.
     *
     * @param node the IQ result node received from the relay
     * @return the parsed response, or empty when the node does not carry a
     *         well-formed result payload
     */
    public static Optional<FetchSimilarNewslettersMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(FetchSimilarNewslettersMexResponse::of);
    }

    /**
     * Returns the parsed list of similar newsletters.
     *
     * @apiNote
     * The list is ordered as returned by the relay and may be empty when
     * no similar channels are available for the seed newsletter.
     *
     * @return the parsed list, empty when the {@code result} array was
     *         missing or empty
     */
    public List<Result> result() {
        return result;
    }

    /**
     * A single similar-newsletter entry returned under
     * {@code xwa2_newsletters_similar.result}.
     *
     * @apiNote
     * Carries the newsletter Jid, a slim {@link ThreadMetadata} block
     * (name, thumbnail picture, verification badge) and a lifecycle
     * {@link State} marker; deliberately narrower than the
     * recommended-newsletter result because the similar-channels carousel
     * UI only renders the title and avatar.
     */
    public static final class Result {
        /**
         * The newsletter Jid string echoed under {@code id}.
         */
        private final String id;

        /**
         * The slim display-metadata block echoed under
         * {@code thread_metadata}.
         */
        private final ThreadMetadata threadMetadata;

        /**
         * The lifecycle state marker echoed under {@code state}.
         */
        private final State state;

        /**
         * Constructs a parsed {@code result} entry.
         *
         * @apiNote
         * Reserved for {@link #of(JSONObject)}.
         *
         * @param id             the newsletter Jid string
         * @param threadMetadata the slim display-metadata block
         * @param state          the lifecycle state marker
         */
        private Result(String id, ThreadMetadata threadMetadata, State state) {
            this.id = id;
            this.threadMetadata = threadMetadata;
            this.state = state;
        }

        /**
         * Returns the newsletter Jid string.
         *
         * @apiNote
         * Empty when the GraphQL envelope omits {@code id}.
         *
         * @return the {@code id} value, or empty when omitted
         */
        public Optional<String> id() {
            return Optional.ofNullable(id);
        }

        /**
         * Returns the slim display-metadata block.
         *
         * @apiNote
         * Empty when the GraphQL envelope omits {@code thread_metadata}.
         *
         * @return the parsed {@link ThreadMetadata}, or empty when omitted
         */
        public Optional<ThreadMetadata> threadMetadata() {
            return Optional.ofNullable(threadMetadata);
        }

        /**
         * Returns the lifecycle state marker.
         *
         * @apiNote
         * Empty when the GraphQL envelope omits {@code state}; otherwise
         * carries the relay-defined state-type string
         * (for example {@code "ACTIVE"}, {@code "DELETED"}).
         *
         * @return the parsed {@link State}, or empty when omitted
         */
        public Optional<State> state() {
            return Optional.ofNullable(state);
        }

        /**
         * The slim {@code thread_metadata} block on a similar-newsletter
         * entry.
         *
         * @apiNote
         * Carries the display fields the similar-channels carousel needs:
         * the localised {@link Name}, the thumbnail {@link Picture}, and
         * the verification badge string.
         */
        public static final class ThreadMetadata {
            /**
             * The localised display-name block.
             */
            private final Name name;

            /**
             * The thumbnail-image block.
             */
            private final Picture picture;

            /**
             * The verification-badge string.
             */
            private final String verification;

            /**
             * Constructs a parsed {@code thread_metadata} value.
             *
             * @apiNote
             * Reserved for {@link #of(JSONObject)}.
             *
             * @param name         the localised display-name block
             * @param picture      the thumbnail-image block
             * @param verification the verification-badge string
             */
            private ThreadMetadata(Name name, Picture picture, String verification) {
                this.name = name;
                this.picture = picture;
                this.verification = verification;
            }

            /**
             * Returns the localised display-name block.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits {@code name}.
             *
             * @return the parsed {@link Name}, or empty when omitted
             */
            public Optional<Name> name() {
                return Optional.ofNullable(name);
            }

            /**
             * Returns the thumbnail-image block.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits {@code picture}.
             *
             * @return the parsed {@link Picture}, or empty when omitted
             */
            public Optional<Picture> picture() {
                return Optional.ofNullable(picture);
            }

            /**
             * Returns the verification-badge string.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits {@code verification};
             * otherwise carries the relay-defined badge string (for
             * example {@code "VERIFIED"}, {@code "UNVERIFIED"}).
             *
             * @return the {@code verification} value, or empty when
             *         omitted
             */
            public Optional<String> verification() {
                return Optional.ofNullable(verification);
            }

            /**
             * The localised {@code name} block on a similar-newsletter
             * entry.
             *
             * @apiNote
             * Carries the localisation identifier, the display text, and
             * the last-update timestamp the relay uses to drive cache
             * invalidation.
             */
            public static final class Name {
                /**
                 * The localisation identifier.
                 */
                private final String id;

                /**
                 * The display text.
                 */
                private final String text;

                /**
                 * The last-update timestamp in epoch seconds.
                 */
                private final Long updateTime;

                /**
                 * Constructs a parsed {@code name} value.
                 *
                 * @apiNote
                 * Reserved for {@link #of(JSONObject)}.
                 *
                 * @param id         the localisation identifier
                 * @param text       the display text
                 * @param updateTime the last-update epoch seconds
                 */
                private Name(String id, String text, Long updateTime) {
                    this.id = id;
                    this.text = text;
                    this.updateTime = updateTime;
                }

                /**
                 * Returns the localisation identifier.
                 *
                 * @apiNote
                 * Empty when the GraphQL envelope omits {@code id}.
                 *
                 * @return the {@code id} value, or empty when omitted
                 */
                public Optional<String> id() {
                    return Optional.ofNullable(id);
                }

                /**
                 * Returns the display text.
                 *
                 * @apiNote
                 * Empty when the GraphQL envelope omits {@code text}.
                 *
                 * @return the {@code text} value, or empty when omitted
                 */
                public Optional<String> text() {
                    return Optional.ofNullable(text);
                }

                /**
                 * Returns the last-update timestamp.
                 *
                 * @apiNote
                 * Empty when the GraphQL envelope omits
                 * {@code update_time}; the underlying value is the
                 * wire-level epoch-second integer remapped to an
                 * {@link Instant}.
                 *
                 * @return the {@code update_time} as an {@link Instant},
                 *         or empty when omitted
                 */
                public Optional<Instant> updateTime() {
                    return Optional.ofNullable(updateTime).map(Instant::ofEpochSecond);
                }

                /**
                 * Parses a {@code name} fragment from the given JSON
                 * object.
                 *
                 * @apiNote
                 * Reserved for the parent parser; returns
                 * {@link Optional#empty()} when {@code obj} is
                 * {@code null}.
                 *
                 * @param obj the JSON object to parse
                 * @return the parsed value, or empty when {@code obj} is
                 *         {@code null}
                 */
                static Optional<Name> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var id = obj.getString("id");
                    var text = obj.getString("text");
                    var updateTime = obj.getLong("update_time");
                    return Optional.of(new Name(id, text, updateTime));
                }

                /**
                 * Parses every {@code name} fragment in the given JSON
                 * array.
                 *
                 * @apiNote
                 * Reserved for callers that handle batched name arrays;
                 * returns {@link List#of()} when {@code arr} is
                 * {@code null}.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed values, empty when
                 *         {@code arr} is {@code null}
                 */
                static List<Name> ofArray(JSONArray arr) {
                    if (arr == null) {
                        return List.of();
                    }

                    var result = new ArrayList<Name>(arr.size());
                    for (var i = 0; i < arr.size(); i++) {
                        of(arr.getJSONObject(i)).ifPresent(result::add);
                    }
                    return result;
                }
            }

            /**
             * The {@code picture} thumbnail block on a similar-newsletter
             * entry.
             *
             * @apiNote
             * Carries the relay-issued media handle, the media type, and
             * the direct download path the client uses to fetch the
             * thumbnail bytes.
             */
            public static final class Picture {
                /**
                 * The media handle.
                 */
                private final String id;

                /**
                 * The media type marker.
                 */
                private final String type;

                /**
                 * The direct download path.
                 */
                private final String directPath;

                /**
                 * Constructs a parsed {@code picture} value.
                 *
                 * @apiNote
                 * Reserved for {@link #of(JSONObject)}.
                 *
                 * @param id         the media handle
                 * @param type       the media type marker
                 * @param directPath the direct download path
                 */
                private Picture(String id, String type, String directPath) {
                    this.id = id;
                    this.type = type;
                    this.directPath = directPath;
                }

                /**
                 * Returns the media handle.
                 *
                 * @apiNote
                 * Empty when the GraphQL envelope omits {@code id}.
                 *
                 * @return the {@code id} value, or empty when omitted
                 */
                public Optional<String> id() {
                    return Optional.ofNullable(id);
                }

                /**
                 * Returns the media-type marker.
                 *
                 * @apiNote
                 * Empty when the GraphQL envelope omits {@code type}.
                 *
                 * @return the {@code type} value, or empty when omitted
                 */
                public Optional<String> type() {
                    return Optional.ofNullable(type);
                }

                /**
                 * Returns the direct download path.
                 *
                 * @apiNote
                 * Empty when the GraphQL envelope omits
                 * {@code direct_path}; otherwise carries the
                 * server-relative path the client uses to fetch the
                 * thumbnail bytes.
                 *
                 * @return the {@code direct_path} value, or empty when
                 *         omitted
                 */
                public Optional<String> directPath() {
                    return Optional.ofNullable(directPath);
                }

                /**
                 * Parses a {@code picture} fragment from the given JSON
                 * object.
                 *
                 * @apiNote
                 * Reserved for the parent parser; returns
                 * {@link Optional#empty()} when {@code obj} is
                 * {@code null}.
                 *
                 * @param obj the JSON object to parse
                 * @return the parsed value, or empty when {@code obj} is
                 *         {@code null}
                 */
                static Optional<Picture> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var id = obj.getString("id");
                    var type = obj.getString("type");
                    var directPath = obj.getString("direct_path");
                    return Optional.of(new Picture(id, type, directPath));
                }

                /**
                 * Parses every {@code picture} fragment in the given JSON
                 * array.
                 *
                 * @apiNote
                 * Reserved for callers that handle batched picture arrays;
                 * returns {@link List#of()} when {@code arr} is
                 * {@code null}.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed values, empty when
                 *         {@code arr} is {@code null}
                 */
                static List<Picture> ofArray(JSONArray arr) {
                    if (arr == null) {
                        return List.of();
                    }

                    var result = new ArrayList<Picture>(arr.size());
                    for (var i = 0; i < arr.size(); i++) {
                        of(arr.getJSONObject(i)).ifPresent(result::add);
                    }
                    return result;
                }
            }

            /**
             * Parses a {@code thread_metadata} fragment from the given
             * JSON object.
             *
             * @apiNote
             * Reserved for the parent parser; returns
             * {@link Optional#empty()} when {@code obj} is {@code null}.
             *
             * @param obj the JSON object to parse
             * @return the parsed value, or empty when {@code obj} is
             *         {@code null}
             */
            static Optional<ThreadMetadata> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var name = Name.of(obj.getJSONObject("name")).orElse(null);
                var picture = Picture.of(obj.getJSONObject("picture")).orElse(null);
                var verification = obj.getString("verification");
                return Optional.of(new ThreadMetadata(name, picture, verification));
            }

            /**
             * Parses every {@code thread_metadata} fragment in the given
             * JSON array.
             *
             * @apiNote
             * Reserved for callers that handle batched thread-metadata
             * arrays; returns {@link List#of()} when {@code arr} is
             * {@code null}.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed values, empty when {@code arr} is
             *         {@code null}
             */
            static List<ThreadMetadata> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<ThreadMetadata>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * The lifecycle {@code state} marker on a similar-newsletter
         * entry.
         *
         * @apiNote
         * Carries the relay-defined {@code type} string (for example
         * {@code "ACTIVE"}, {@code "DELETED"}, {@code "SUSPENDED"}) that
         * gates whether the carousel UI should surface the entry.
         */
        public static final class State {
            /**
             * The relay-defined state-type string.
             */
            private final String type;

            /**
             * Constructs a parsed {@code state} value.
             *
             * @apiNote
             * Reserved for {@link #of(JSONObject)}.
             *
             * @param type the state-type string
             */
            private State(String type) {
                this.type = type;
            }

            /**
             * Returns the state-type string.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits {@code type}.
             *
             * @return the {@code type} value, or empty when omitted
             */
            public Optional<String> type() {
                return Optional.ofNullable(type);
            }

            /**
             * Parses a {@code state} fragment from the given JSON object.
             *
             * @apiNote
             * Reserved for the parent parser; returns
             * {@link Optional#empty()} when {@code obj} is {@code null}.
             *
             * @param obj the JSON object to parse
             * @return the parsed value, or empty when {@code obj} is
             *         {@code null}
             */
            static Optional<State> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var type = obj.getString("type");
                return Optional.of(new State(type));
            }

            /**
             * Parses every {@code state} fragment in the given JSON array.
             *
             * @apiNote
             * Reserved for callers that handle batched state arrays;
             * returns {@link List#of()} when {@code arr} is {@code null}.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed values, empty when {@code arr} is
             *         {@code null}
             */
            static List<State> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<State>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Parses a single {@code result} entry from the given JSON object.
         *
         * @apiNote
         * Reserved for {@link #ofArray(JSONArray)}; returns
         * {@link Optional#empty()} when {@code obj} is {@code null}.
         *
         * @param obj the JSON object to parse
         * @return the parsed value, or empty when {@code obj} is
         *         {@code null}
         */
        static Optional<Result> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var id = obj.getString("id");
            var threadMetadata = ThreadMetadata.of(obj.getJSONObject("thread_metadata")).orElse(null);
            var state = State.of(obj.getJSONObject("state")).orElse(null);
            return Optional.of(new Result(id, threadMetadata, state));
        }

        /**
         * Parses every {@code result} entry in the given JSON array.
         *
         * @apiNote
         * Materialises the {@code xwa2_newsletters_similar.result} array
         * into a fresh {@link List}; returns {@link List#of()} when
         * {@code arr} is {@code null} so an absent or empty server
         * response surfaces as an empty list.
         *
         * @param arr the JSON array to parse
         * @return the list of parsed values, empty when {@code arr} is
         *         {@code null}
         */
        static List<Result> ofArray(JSONArray arr) {
            if (arr == null) {
                return List.of();
            }

            var result = new ArrayList<Result>(arr.size());
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
     * Reserved for the public {@link #of(Node)} overload; callers should not
     * hold raw JSON bytes.
     *
     * @implNote
     * This implementation guards every nested object lookup so a malformed
     * envelope produces {@link Optional#empty()} rather than a parser
     * exception, mirroring the defensive null-checks in WA Web's caller.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return the parsed response, or empty when the envelope lacks the
     *         expected {@code data.xwa2_newsletters_similar} root
     */
    private static Optional<FetchSimilarNewslettersMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_newsletters_similar");
        if (root == null) {
            return Optional.empty();
        }

        var result = Result.ofArray(root.getJSONArray("result"));

        return Optional.of(new FetchSimilarNewslettersMexResponse(result));
    }
}
