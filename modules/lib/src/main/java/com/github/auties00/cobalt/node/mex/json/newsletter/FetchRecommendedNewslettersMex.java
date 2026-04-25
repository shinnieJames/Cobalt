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
import java.util.OptionalLong;

/**
 * Fetches a list of newsletters recommended to the authenticated user.
 *
 * <p>The recommendation engine returns channels that the WhatsApp backend estimates are relevant to the user based on follow history, directory browsing and regional signals.
 *
 * @implNote WAWebMexFetchRecommendedNewslettersJob: adapts the {@code mexFetchRecommendedNewsletters} GraphQL query,
 * which in WA Web is invoked via {@code WAWebMexClient.fetchQuery} and
 * whose response is unwrapped by the same module. Cobalt models the request
 * and response as sibling variants of a sealed interface rather than a
 * free-standing async function.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchRecommendedNewslettersJob")
public sealed interface FetchRecommendedNewslettersMex extends MexJsonOperation permits FetchRecommendedNewslettersMex.Request, FetchRecommendedNewslettersMex.Response {
    /**
     * The numeric GraphQL query identifier assigned by the WhatsApp relay
     * to the {@code FetchRecommendedNewsletters} compiled query.
     *
     * @implNote WAWebMexFetchRecommendedNewslettersJobQuery.graphql: corresponds to the
     * {@code params.id} field of the compiled GraphQL document
     * ({@code id:"25806748772361516"}) bundled in the WA Web client.
     */
    String QUERY_ID = "25806748772361516";

    /**
     * The request variant of {@link FetchRecommendedNewslettersMex} that serialises the
     * query variables and emits the outbound IQ stanza.
     *
     * @implNote WAWebMexFetchRecommendedNewslettersJob.mexFetchRecommendedNewsletters: adapts the
     * {@code variables} object constructed inline in the JS implementation
     * ({@code {input:{limit:t,country_codes:r}, fetch_status_metadata: WAWebNewsletterGatingUtils.isNewsletterStatusReceiverEnabled()}})
     * into a dedicated Java class. The two positional parameters of the JS
     * function ({@code limit} and {@code country_codes}) become fields on this
     * record-like class, and the gating flag is also exposed so callers can
     * decide whether to request newsletter status metadata.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchRecommendedNewslettersJob")
    final class Request implements FetchRecommendedNewslettersMex {
        private final Long limit;
        private final List<String> countryCodes;
        private final boolean fetchStatusMetadata;

        /**
         * Constructs a new request with the given variables.
         *
         * @param limit               the maximum number of recommended
         *                            newsletters to return, or {@code null}
         *                            to omit the field (the relay then
         *                            applies its default page size)
         * @param countryCodes        the list of ISO country codes used to
         *                            scope the recommendation, or
         *                            {@code null} to omit the field
         * @param fetchStatusMetadata {@code true} to request the optional
         *                            {@code status_metadata} sub-selection,
         *                            mirroring
         *                            {@code WAWebNewsletterGatingUtils.isNewsletterStatusReceiverEnabled()}
         *                            in the JS source
         */
        public Request(Long limit, List<String> countryCodes, boolean fetchStatusMetadata) {
            this.limit = limit;
            this.countryCodes = countryCodes;
            this.fetchStatusMetadata = fetchStatusMetadata;
        }

        /**
         * Builds the IQ stanza that dispatches this operation to the
         * WhatsApp relay.
         *
         * @implNote WAWebMexFetchRecommendedNewslettersJob.mexFetchRecommendedNewsletters: WA Web constructs the
         * {@code variables} object inline as
         * {@code {input:{limit:t,country_codes:r}, fetch_status_metadata: <bool>}}
         * and delegates to {@code WAWebMexClient.fetchQuery}. Cobalt writes
         * the JSON directly via {@code fastjson2.JSONWriter} and wraps it
         * through {@link MexJsonOperation#createMexNode(String, String)}.
         * @return a {@link NodeBuilder} carrying the IQ envelope and the
         *         serialised GraphQL variables
         */
        @WhatsAppWebExport(moduleName = "WAWebMexFetchRecommendedNewslettersJob", exports = "mexFetchRecommendedNewsletters",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public NodeBuilder toNode() {
            // WAWebMexFetchRecommendedNewslettersJob.mexFetchRecommendedNewsletters
            // Opens a UTF-8 JSON writer that will serialise the GraphQL variables envelope
            try (var writer = JSONWriter.ofUTF8()) {
                // WAWebMexFetchRecommendedNewslettersJob.mexFetchRecommendedNewsletters
                // Begins the outer envelope and the nested "variables" object consumed by WAWebMexClient.fetchQuery
                writer.startObject();
                writer.writeName("variables");
                writer.writeColon();
                writer.startObject();

                // WAWebMexFetchRecommendedNewslettersJob.mexFetchRecommendedNewsletters
                // Emits {input:{limit:t, country_codes:r}}; the inner object is emitted even when both children are null
                // to mirror the JS object literal shape.
                writer.writeName("input");
                writer.writeColon();
                writer.startObject();
                if (limit != null) {
                    writer.writeName("limit");
                    writer.writeColon();
                    writer.writeInt64(limit);
                }
                if (countryCodes != null) {
                    writer.writeName("country_codes");
                    writer.writeColon();
                    writer.startArray();
                    for (var i = 0; i < countryCodes.size(); i++) {
                        if (i > 0) {
                            writer.writeComma();
                        }
                        writer.writeString(countryCodes.get(i));
                    }
                    writer.endArray();
                }
                writer.endObject();

                // WAWebMexFetchRecommendedNewslettersJob.mexFetchRecommendedNewsletters
                // Emits the sibling {fetch_status_metadata: <bool>} field, mirroring the JS variables literal
                writer.writeName("fetch_status_metadata");
                writer.writeColon();
                writer.writeBool(fetchStatusMetadata);

                writer.endObject();
                writer.endObject();

                // ADAPTED: WAWebMexFetchRecommendedNewslettersJob.mexFetchRecommendedNewsletters
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
     * The response variant of {@link FetchRecommendedNewslettersMex} that exposes the data
     * returned by the server after a successful query.
     *
     * @implNote WAWebMexFetchRecommendedNewslettersJob: adapts the JSON root returned by the GraphQL
     * query into a Java value object.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchRecommendedNewslettersJob")
    final class Response implements FetchRecommendedNewslettersMex {
        private final PageInfo pageInfo;
        private final List<Result> result;

        private Response(PageInfo pageInfo, List<Result> result) {
            this.pageInfo = pageInfo;
            this.result = result;
        }

        /**
         * Parses a MEX response from the given IQ response node.
         *
         * @implNote WAWebMexFetchRecommendedNewslettersJob.mexFetchRecommendedNewsletters: WA Web relies on the
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
         * Returns the {@code page_info} field.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<PageInfo> pageInfo() {
            return Optional.ofNullable(pageInfo);
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
         * A parsed {@code PageInfo} object.
         */
        public static final class PageInfo {
            private final Boolean hasNextPage;
            private final Boolean hasPreviousPage;
            private final String startCursor;
            private final String endCursor;

            private PageInfo(Boolean hasNextPage, Boolean hasPreviousPage, String startCursor, String endCursor) {
                this.hasNextPage = hasNextPage;
                this.hasPreviousPage = hasPreviousPage;
                this.startCursor = startCursor;
                this.endCursor = endCursor;
            }

            /**
             * Returns the {@code hasNextPage} field.
             *
             * @return {@code true} if the value is present and true, {@code false} otherwise
             */
            public boolean hasNextPage() {
                return hasNextPage != null && hasNextPage;
            }

            /**
             * Returns the {@code hasPreviousPage} field.
             *
             * @return {@code true} if the value is present and true, {@code false} otherwise
             */
            public boolean hasPreviousPage() {
                return hasPreviousPage != null && hasPreviousPage;
            }

            /**
             * Returns the {@code startCursor} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> startCursor() {
                return Optional.ofNullable(startCursor);
            }

            /**
             * Returns the {@code endCursor} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> endCursor() {
                return Optional.ofNullable(endCursor);
            }

            /**
             * Parses a {@code PageInfo} from the given JSON object.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
             */
            static Optional<PageInfo> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var hasNextPage = obj.getBoolean("hasNextPage");
                var hasPreviousPage = obj.getBoolean("hasPreviousPage");
                var startCursor = obj.getString("startCursor");
                var endCursor = obj.getString("endCursor");
                return Optional.of(new PageInfo(hasNextPage, hasPreviousPage, startCursor, endCursor));
            }

            /**
             * Parses a list of {@code PageInfo} from the given JSON array.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed results, empty if {@code arr} is {@code null}
             */
            static List<PageInfo> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<PageInfo>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * A parsed {@code Result} object.
         */
        public static final class Result {
            private final String id;
            private final State state;
            private final ThreadMetadata threadMetadata;
            private final StatusMetadata statusMetadata;

            private Result(String id, State state, ThreadMetadata threadMetadata, StatusMetadata statusMetadata) {
                this.id = id;
                this.state = state;
                this.threadMetadata = threadMetadata;
                this.statusMetadata = statusMetadata;
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
             * Returns the {@code state} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<State> state() {
                return Optional.ofNullable(state);
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
             * Returns the {@code status_metadata} field.
             *
             * @implNote WAWebMexFetchRecommendedNewslettersJobQuery.graphql: present in the
             * response only when the request opted in via
             * {@code fetch_status_metadata = true}
             * (Relay {@code Condition} with {@code passingValue:!0}).
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<StatusMetadata> statusMetadata() {
                return Optional.ofNullable(statusMetadata);
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
             * A parsed {@code ThreadMetadata} object.
             */
            public static final class ThreadMetadata {
                private final Long creationTime;
                private final Name name;
                private final Description description;
                private final Preview preview;
                private final String invite;
                private final String handle;
                private final String verification;
                private final Long subscribersCount;

                private ThreadMetadata(Long creationTime, Name name, Description description, Preview preview, String invite, String handle, String verification, Long subscribersCount) {
                    this.creationTime = creationTime;
                    this.name = name;
                    this.description = description;
                    this.preview = preview;
                    this.invite = invite;
                    this.handle = handle;
                    this.verification = verification;
                    this.subscribersCount = subscribersCount;
                }

                /**
                 * Returns the {@code creation_time} field.
                 *
                 * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
                 */
                public Optional<Instant> creationTime() {
                    return Optional.ofNullable(creationTime).map(Instant::ofEpochSecond);
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
                 * Returns the {@code description} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<Description> description() {
                    return Optional.ofNullable(description);
                }

                /**
                 * Returns the {@code preview} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<Preview> preview() {
                    return Optional.ofNullable(preview);
                }

                /**
                 * Returns the {@code invite} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> invite() {
                    return Optional.ofNullable(invite);
                }

                /**
                 * Returns the {@code handle} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> handle() {
                    return Optional.ofNullable(handle);
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
                 * Returns the {@code subscribers_count} field.
                 *
                 * @return an {@link OptionalLong} containing the value, or empty if absent
                 */
                public OptionalLong subscribersCount() {
                    return subscribersCount != null ? OptionalLong.of(subscribersCount) : OptionalLong.empty();
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
                 * A parsed {@code Description} object.
                 */
                public static final class Description {
                    private final String id;
                    private final String text;
                    private final Long updateTime;

                    private Description(String id, String text, Long updateTime) {
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
                     * Parses a {@code Description} from the given JSON object.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                     */
                    static Optional<Description> of(JSONObject obj) {
                        if (obj == null) {
                            return Optional.empty();
                        }

                        var id = obj.getString("id");
                        var text = obj.getString("text");
                        var updateTime = obj.getLong("update_time");
                        return Optional.of(new Description(id, text, updateTime));
                    }

                    /**
                     * Parses a list of {@code Description} from the given JSON array.
                     *
                     * @param arr the JSON array to parse
                     * @return the list of parsed results, empty if {@code arr} is {@code null}
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
                 * A parsed {@code Preview} object.
                 */
                public static final class Preview {
                    private final String id;
                    private final String type;
                    private final String directPath;

                    private Preview(String id, String type, String directPath) {
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
                     * Parses a {@code Preview} from the given JSON object.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                     */
                    static Optional<Preview> of(JSONObject obj) {
                        if (obj == null) {
                            return Optional.empty();
                        }

                        var id = obj.getString("id");
                        var type = obj.getString("type");
                        var directPath = obj.getString("direct_path");
                        return Optional.of(new Preview(id, type, directPath));
                    }

                    /**
                     * Parses a list of {@code Preview} from the given JSON array.
                     *
                     * @param arr the JSON array to parse
                     * @return the list of parsed results, empty if {@code arr} is {@code null}
                     */
                    static List<Preview> ofArray(JSONArray arr) {
                        if (arr == null) {
                            return List.of();
                        }

                        var result = new ArrayList<Preview>(arr.size());
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

                    var creationTime = obj.getLong("creation_time");
                    var name = Name.of(obj.getJSONObject("name")).orElse(null);
                    var description = Description.of(obj.getJSONObject("description")).orElse(null);
                    var preview = Preview.of(obj.getJSONObject("preview")).orElse(null);
                    var invite = obj.getString("invite");
                    var handle = obj.getString("handle");
                    var verification = obj.getString("verification");
                    var subscribersCount = obj.getLong("subscribers_count");
                    return Optional.of(new ThreadMetadata(creationTime, name, description, preview, invite, handle, verification, subscribersCount));
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
             * A parsed {@code StatusMetadata} object exposing per-newsletter
             * status counters returned when {@code fetch_status_metadata}
             * was set on the request.
             *
             * @implNote WAWebMexFetchRecommendedNewslettersJobQuery.graphql: corresponds to the
             * {@code XWA2NewsletterStatusMetadata} concrete type, included
             * conditionally on the {@code fetch_status_metadata} variable.
             */
            public static final class StatusMetadata {
                private final String lastStatusServerId;
                private final Long lastStatusSentTime;

                private StatusMetadata(String lastStatusServerId, Long lastStatusSentTime) {
                    this.lastStatusServerId = lastStatusServerId;
                    this.lastStatusSentTime = lastStatusSentTime;
                }

                /**
                 * Returns the {@code last_status_server_id} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> lastStatusServerId() {
                    return Optional.ofNullable(lastStatusServerId);
                }

                /**
                 * Returns the {@code last_status_sent_time} field.
                 *
                 * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
                 */
                public Optional<Instant> lastStatusSentTime() {
                    return Optional.ofNullable(lastStatusSentTime).map(Instant::ofEpochSecond);
                }

                /**
                 * Parses a {@code StatusMetadata} from the given JSON object.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                 */
                static Optional<StatusMetadata> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var lastStatusServerId = obj.getString("last_status_server_id");
                    var lastStatusSentTime = obj.getLong("last_status_sent_time");
                    return Optional.of(new StatusMetadata(lastStatusServerId, lastStatusSentTime));
                }

                /**
                 * Parses a list of {@code StatusMetadata} from the given JSON array.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed results, empty if {@code arr} is {@code null}
                 */
                static List<StatusMetadata> ofArray(JSONArray arr) {
                    if (arr == null) {
                        return List.of();
                    }

                    var result = new ArrayList<StatusMetadata>(arr.size());
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
                var state = State.of(obj.getJSONObject("state")).orElse(null);
                var threadMetadata = ThreadMetadata.of(obj.getJSONObject("thread_metadata")).orElse(null);
                var statusMetadata = StatusMetadata.of(obj.getJSONObject("status_metadata")).orElse(null);
                return Optional.of(new Result(id, state, threadMetadata, statusMetadata));
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
         * @implNote WAWebMexFetchRecommendedNewslettersJob.mexFetchRecommendedNewsletters: mirrors the implicit
         * unwrapping that WA Web performs on the GraphQL response,
         * extracting the {@code xwa2_newsletters_recommended} root.
         * @param json the UTF-8 encoded JSON payload
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the envelope is missing expected fields
         */
        private static Optional<Response> of(byte[] json) {
            // WAWebMexFetchRecommendedNewslettersJob.mexFetchRecommendedNewsletters
            // Parses the raw JSON payload, bailing out if fastjson2 returns null
            var jsonObject = JSON.parseObject(json);
            if (jsonObject == null) {
                return Optional.empty();
            }

            // WAWebMexFetchRecommendedNewslettersJob.mexFetchRecommendedNewsletters
            // Descends into the standard GraphQL "data" envelope
            var data = jsonObject.getJSONObject("data");
            if (data == null) {
                return Optional.empty();
            }

            // WAWebMexFetchRecommendedNewslettersJob.mexFetchRecommendedNewsletters
            // Extracts the operation-specific root keyed by xwa2_newsletters_recommended
            var root = data.getJSONObject("xwa2_newsletters_recommended");
            if (root == null) {
                return Optional.empty();
            }

            var pageInfo = PageInfo.of(root.getJSONObject("page_info")).orElse(null);
            var result = Result.ofArray(root.getJSONArray("result"));

            return Optional.of(new Response(pageInfo, result));
        }
    }
}
