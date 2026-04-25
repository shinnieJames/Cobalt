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
 * Fetches newsletters similar to a given newsletter.
 *
 * <p>This query powers the similar-channel recommendations shown in the newsletter detail view, returning a curated list of channels related to the input newsletter.
 *
 * @implNote WAWebMexFetchSimilarNewslettersJob: adapts the {@code mexFetchSimilarNewsletters} GraphQL query,
 * which in WA Web is invoked via {@code WAWebMexClient.fetchQuery} and
 * whose response is unwrapped by the same module. Cobalt models the request
 * and response as sibling variants of a sealed interface rather than a
 * free-standing async function.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchSimilarNewslettersJob")
public sealed interface FetchSimilarNewslettersMex extends MexJsonOperation permits FetchSimilarNewslettersMex.Request, FetchSimilarNewslettersMex.Response {
    /**
     * The numeric GraphQL query identifier assigned by the WhatsApp relay
     * to the {@code FetchSimilarNewsletters} compiled query.
     *
     * @implNote WAWebMexFetchSimilarNewslettersJobQuery.graphql: corresponds to the
     * {@code params.id} field of the compiled GraphQL document
     * ({@code id:"26217043484590756"}) bundled in the WA Web client.
     */
    String QUERY_ID = "26217043484590756";

    /**
     * The request variant of {@link FetchSimilarNewslettersMex} that serialises the
     * query variables and emits the outbound IQ stanza.
     *
     * @implNote WAWebMexFetchSimilarNewslettersJob.mexFetchSimilarNewsletters: adapts the
     * {@code variables} object constructed inline in the JS implementation
     * ({@code {input:{newsletter_id:a,limit:r,country_codes:n!=null?n:[]}, fetch_status_metadata: WAWebNewsletterGatingUtils.isNewsletterStatusReceiverEnabled()}})
     * into a dedicated Java class. The three positional parameters of the JS
     * function ({@code newsletterId}, {@code limit}, {@code countryCodes}) become
     * fields on this record-like class, and the gating flag is also exposed so
     * callers can decide whether to request newsletter status metadata.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchSimilarNewslettersJob")
    final class Request implements FetchSimilarNewslettersMex {
        private final String newsletterId;
        private final Long limit;
        private final List<String> countryCodes;
        private final boolean fetchStatusMetadata;

        /**
         * Constructs a new request with the given variables.
         *
         * @param newsletterId        the JID of the newsletter whose similar
         *                            channels should be returned, or
         *                            {@code null} to omit the field
         * @param limit               the maximum number of similar
         *                            newsletters to return, or {@code null}
         *                            to omit the field
         * @param countryCodes        the list of ISO country codes used to
         *                            scope the recommendation; mirroring the
         *                            JS coalescing {@code n!=null?n:[]} a
         *                            {@code null} value is serialised as an
         *                            empty array
         * @param fetchStatusMetadata {@code true} to request the optional
         *                            {@code status_metadata} sub-selection,
         *                            mirroring
         *                            {@code WAWebNewsletterGatingUtils.isNewsletterStatusReceiverEnabled()}
         *                            in the JS source
         */
        public Request(String newsletterId, Long limit, List<String> countryCodes, boolean fetchStatusMetadata) {
            this.newsletterId = newsletterId;
            this.limit = limit;
            this.countryCodes = countryCodes;
            this.fetchStatusMetadata = fetchStatusMetadata;
        }

        /**
         * Builds the IQ stanza that dispatches this operation to the
         * WhatsApp relay.
         *
         * @implNote WAWebMexFetchSimilarNewslettersJob.mexFetchSimilarNewsletters: WA Web constructs the
         * {@code variables} object inline as
         * {@code {input:{newsletter_id:a,limit:r,country_codes:n!=null?n:[]}, fetch_status_metadata: <bool>}}
         * and delegates to {@code WAWebMexClient.fetchQuery}. Cobalt writes
         * the JSON directly via {@code fastjson2.JSONWriter} and wraps it
         * through {@link MexJsonOperation#createMexNode(String, String)}.
         * @return a {@link NodeBuilder} carrying the IQ envelope and the
         *         serialised GraphQL variables
         */
        @WhatsAppWebExport(moduleName = "WAWebMexFetchSimilarNewslettersJob", exports = "mexFetchSimilarNewsletters",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public NodeBuilder toNode() {
            // WAWebMexFetchSimilarNewslettersJob.mexFetchSimilarNewsletters
            // Opens a UTF-8 JSON writer that will serialise the GraphQL variables envelope
            try (var writer = JSONWriter.ofUTF8()) {
                // WAWebMexFetchSimilarNewslettersJob.mexFetchSimilarNewsletters
                // Begins the outer envelope and the nested "variables" object consumed by WAWebMexClient.fetchQuery
                writer.startObject();
                writer.writeName("variables");
                writer.writeColon();
                writer.startObject();

                // WAWebMexFetchSimilarNewslettersJob.mexFetchSimilarNewsletters
                // Emits {input:{newsletter_id:a, limit:r, country_codes:n!=null?n:[]}}; the inner object
                // is emitted unconditionally to mirror the JS object literal shape, and country_codes
                // defaults to [] when null per the JS coalescing.
                writer.writeName("input");
                writer.writeColon();
                writer.startObject();
                if (newsletterId != null) {
                    writer.writeName("newsletter_id");
                    writer.writeColon();
                    writer.writeString(newsletterId);
                }
                if (limit != null) {
                    writer.writeName("limit");
                    writer.writeColon();
                    writer.writeInt64(limit);
                }
                writer.writeName("country_codes");
                writer.writeColon();
                writer.startArray();
                if (countryCodes != null) {
                    for (var i = 0; i < countryCodes.size(); i++) {
                        if (i > 0) {
                            writer.writeComma();
                        }
                        writer.writeString(countryCodes.get(i));
                    }
                }
                writer.endArray();
                writer.endObject();

                // WAWebMexFetchSimilarNewslettersJob.mexFetchSimilarNewsletters
                // Emits the sibling {fetch_status_metadata: <bool>} field, mirroring the JS variables literal
                writer.writeName("fetch_status_metadata");
                writer.writeColon();
                writer.writeBool(fetchStatusMetadata);

                writer.endObject();
                writer.endObject();

                // ADAPTED: WAWebMexFetchSimilarNewslettersJob.mexFetchSimilarNewsletters
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
     * The response variant of {@link FetchSimilarNewslettersMex} that exposes the data
     * returned by the server after a successful query.
     *
     * @implNote WAWebMexFetchSimilarNewslettersJob: adapts the JSON root returned by the GraphQL
     * query into a Java value object.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchSimilarNewslettersJob")
    final class Response implements FetchSimilarNewslettersMex {
        private final List<Result> result;

        private Response(List<Result> result) {
            this.result = result;
        }

        /**
         * Parses a MEX response from the given IQ response node.
         *
         * @implNote WAWebMexFetchSimilarNewslettersJob.mexFetchSimilarNewsletters: WA Web relies on the
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
         * Returns the {@code result} field.
         *
         * @return the list of values, empty if absent
         */
        public List<Result> result() {
            return result;
        }

        /**
         * A parsed {@code Result} object.
         */
        public static final class Result {
            private final String id;
            private final ThreadMetadata threadMetadata;
            private final State state;

            private Result(String id, ThreadMetadata threadMetadata, State state) {
                this.id = id;
                this.threadMetadata = threadMetadata;
                this.state = state;
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
             * Returns the {@code thread_metadata} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<ThreadMetadata> threadMetadata() {
                return Optional.ofNullable(threadMetadata);
            }

            /**
             * Returns the {@code state} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<State> state() {
                return Optional.ofNullable(state);
            }

            /**
             * A parsed {@code ThreadMetadata} object.
             */
            public static final class ThreadMetadata {
                private final Name name;
                private final Picture picture;
                private final String verification;

                private ThreadMetadata(Name name, Picture picture, String verification) {
                    this.name = name;
                    this.picture = picture;
                    this.verification = verification;
                }

                /**
                 * Returns the {@code name} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<Name> name() {
                    return Optional.ofNullable(name);
                }

                /**
                 * Returns the {@code picture} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<Picture> picture() {
                    return Optional.ofNullable(picture);
                }

                /**
                 * Returns the {@code verification} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> verification() {
                    return Optional.ofNullable(verification);
                }

                /**
                 * A parsed {@code Name} object.
                 */
                public static final class Name {
                    private final String id;
                    private final String text;
                    private final Long updateTime;

                    private Name(String id, String text, Long updateTime) {
                        this.id = id;
                        this.text = text;
                        this.updateTime = updateTime;
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
                     * Returns the {@code text} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> text() {
                        return Optional.ofNullable(text);
                    }

                    /**
                     * Returns the {@code update_time} field.
                     *
                     * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
                     */
                    public Optional<Instant> updateTime() {
                        return Optional.ofNullable(updateTime).map(Instant::ofEpochSecond);
                    }

                    /**
                     * Parses a {@code Name} from the given JSON object.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
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
                     * Parses a list of {@code Name} from the given JSON array.
                     *
                     * @param arr the JSON array to parse
                     * @return the list of parsed results, empty if {@code arr} is {@code null}
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
                 * A parsed {@code Picture} object.
                 */
                public static final class Picture {
                    private final String id;
                    private final String type;
                    private final String directPath;

                    private Picture(String id, String type, String directPath) {
                        this.id = id;
                        this.type = type;
                        this.directPath = directPath;
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
                     * Returns the {@code type} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> type() {
                        return Optional.ofNullable(type);
                    }

                    /**
                     * Returns the {@code direct_path} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> directPath() {
                        return Optional.ofNullable(directPath);
                    }

                    /**
                     * Parses a {@code Picture} from the given JSON object.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
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
                     * Parses a list of {@code Picture} from the given JSON array.
                     *
                     * @param arr the JSON array to parse
                     * @return the list of parsed results, empty if {@code arr} is {@code null}
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
                 * Parses a {@code ThreadMetadata} from the given JSON object.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
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
                 * Parses a list of {@code ThreadMetadata} from the given JSON array.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed results, empty if {@code arr} is {@code null}
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
             * A parsed {@code State} object.
             */
            public static final class State {
                private final String type;

                private State(String type) {
                    this.type = type;
                }

                /**
                 * Returns the {@code type} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> type() {
                    return Optional.ofNullable(type);
                }

                /**
                 * Parses a {@code State} from the given JSON object.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                 */
                static Optional<State> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var type = obj.getString("type");
                    return Optional.of(new State(type));
                }

                /**
                 * Parses a list of {@code State} from the given JSON array.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed results, empty if {@code arr} is {@code null}
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
             * Parses a {@code Result} from the given JSON object.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
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
             * Parses a list of {@code Result} from the given JSON array.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed results, empty if {@code arr} is {@code null}
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
         * Parses a {@link Response} from the raw JSON bytes of the
         * {@code <result>} child.
         *
         * @implNote WAWebMexFetchSimilarNewslettersJob.mexFetchSimilarNewsletters: mirrors the implicit
         * unwrapping that WA Web performs on the GraphQL response,
         * extracting the {@code xwa2_newsletters_similar} root.
         * @param json the UTF-8 encoded JSON payload
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the envelope is missing expected fields
         */
        private static Optional<Response> of(byte[] json) {
            // WAWebMexFetchSimilarNewslettersJob.mexFetchSimilarNewsletters
            // Parses the raw JSON payload, bailing out if fastjson2 returns null
            var jsonObject = JSON.parseObject(json);
            if (jsonObject == null) {
                return Optional.empty();
            }

            // WAWebMexFetchSimilarNewslettersJob.mexFetchSimilarNewsletters
            // Descends into the standard GraphQL "data" envelope
            var data = jsonObject.getJSONObject("data");
            if (data == null) {
                return Optional.empty();
            }

            // WAWebMexFetchSimilarNewslettersJob.mexFetchSimilarNewsletters
            // Extracts the operation-specific root keyed by xwa2_newsletters_similar
            var root = data.getJSONObject("xwa2_newsletters_similar");
            if (root == null) {
                return Optional.empty();
            }

            var result = Result.ofArray(root.getJSONArray("result"));

            return Optional.of(new Response(result));
        }
    }
}
