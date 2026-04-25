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
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Fetches the full newsletter directory list filtered by country or category.
 *
 * <p>This query powers the explore tab of the newsletter directory, returning paginated channels that match the supplied filter arguments such as country or category.
 *
 * @implNote WAWebMexFetchNewsletterDirectoryListJob: adapts the {@code mexFetchNewsletterDirectoryList} GraphQL query,
 * which in WA Web is invoked via {@code WAWebMexClient.fetchQuery} and
 * whose response is unwrapped by the same module. Cobalt models the request
 * and response as sibling variants of a sealed interface rather than a
 * free-standing async function.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterDirectoryListJob")
public sealed interface FetchNewsletterDirectoryListMex extends MexJsonOperation permits FetchNewsletterDirectoryListMex.Request, FetchNewsletterDirectoryListMex.Response {
    /**
     * The numeric GraphQL query identifier assigned by the WhatsApp relay
     * to the {@code FetchNewsletterDirectoryList} compiled query.
     *
     * @implNote WAWebMexFetchNewsletterDirectoryListJobQuery.graphql: corresponds to the compiled
     * document id {@code params.id} registered for the
     * {@code WAWebMexFetchNewsletterDirectoryListJobQuery} compiled query.
     */
    String QUERY_ID = "26125047313831973";

    /**
     * The request variant of {@link FetchNewsletterDirectoryListMex} that serialises the
     * query variables and emits the outbound IQ stanza.
     *
     * @implNote WAWebMexFetchNewsletterDirectoryListJob.mexFetchNewsletterDirectoryList: adapts the
     * {@code variables} object constructed inline in the JS implementation:
     * {@code {input:{view, filters:{country_codes, categories}, limit, start_cursor},
     * fetch_status_metadata}}. Cobalt exposes the same fields as constructor
     * arguments and serialises them in the same order.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterDirectoryListJob")
    final class Request implements FetchNewsletterDirectoryListMex {
        private final NewsletterDirectoryListView view;
        private final List<String> countryCodes;
        private final List<String> categories;
        private final Long limit;
        private final String cursorToken;
        private final boolean fetchStatusMetadata;

        /**
         * Constructs a new request for the newsletter directory list query.
         *
         * @param view                the directory list view (filter pill) to query;
         *                            translated to the uppercase enum string by
         *                            {@code u(i)} in the JS source
         * @param countryCodes        the country codes to filter by, may be {@code null}
         * @param categories          the categories to filter by as upper-case
         *                            on-wire values (e.g. {@code "BUSINESS"}); WA Web
         *                            obtains these via
         *                            {@code WAWebNewsletterDirectoryCategoryUtils.getCategoryValueFromEnum}
         * @param limit               the page size, may be {@code null}
         * @param cursorToken         the start cursor for pagination, may be {@code null}
         * @param fetchStatusMetadata whether to include
         *                            {@code status_metadata} in the response, set
         *                            from
         *                            {@code WAWebNewsletterGatingUtils.isNewsletterStatusReceiverEnabled()}
         * @implNote WAWebMexFetchNewsletterDirectoryListJob.mexFetchNewsletterDirectoryList:
         * mirrors the inline destructure {@code var t=e.categories,n=e.countryCodes,r=e.cursorToken,a=e.limit,i=e.view}.
         */
        public Request(NewsletterDirectoryListView view,
                       List<String> countryCodes,
                       List<String> categories,
                       Long limit,
                       String cursorToken,
                       boolean fetchStatusMetadata) {
            this.view = view;
            this.countryCodes = countryCodes;
            this.categories = categories;
            this.limit = limit;
            this.cursorToken = cursorToken;
            this.fetchStatusMetadata = fetchStatusMetadata;
        }

        /**
         * Builds the IQ stanza that dispatches this operation to the
         * WhatsApp relay.
         *
         * @implNote WAWebMexFetchNewsletterDirectoryListJob.mexFetchNewsletterDirectoryList: WA Web constructs the
         * {@code variables} object inline and delegates to
         * {@code WAWebMexClient.fetchQuery}. Cobalt writes the JSON directly
         * via {@code fastjson2.JSONWriter} and wraps it through
         * {@link MexJsonOperation#createMexNode(String, String)}.
         * @return a {@link NodeBuilder} carrying the IQ envelope and the
         *         serialised GraphQL variables
         */
        @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterDirectoryListJob", exports = "mexFetchNewsletterDirectoryList",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public NodeBuilder toNode() {
            // WAWebMexFetchNewsletterDirectoryListJob.mexFetchNewsletterDirectoryList
            // Opens a UTF-8 JSON writer that will serialise the GraphQL variables envelope
            try (var writer = JSONWriter.ofUTF8()) {
                // WAWebMexFetchNewsletterDirectoryListJob.mexFetchNewsletterDirectoryList
                // Begins the outer envelope and the nested "variables" object consumed by WAWebMexClient.fetchQuery
                writer.startObject();
                writer.writeName("variables");
                writer.writeColon();
                writer.startObject();

                // WAWebMexFetchNewsletterDirectoryListJob.mexFetchNewsletterDirectoryList
                // var l={input:{view:u(i),filters:{country_codes:n,categories:t.map(...)},limit:a,start_cursor:r}, ...}
                writer.writeName("input");
                writer.writeColon();
                writer.startObject();

                // WAWebMexFetchNewsletterDirectoryListJob.mexFetchNewsletterDirectoryList
                // input.view = u(i) -> uppercase enum string
                if (view != null) {
                    writer.writeName("view");
                    writer.writeColon();
                    writer.writeString(view.value());
                }

                // WAWebMexFetchNewsletterDirectoryListJob.mexFetchNewsletterDirectoryList
                // input.filters = {country_codes:n, categories:t.map(getCategoryValueFromEnum)}
                writer.writeName("filters");
                writer.writeColon();
                writer.startObject();
                writer.writeName("country_codes");
                writer.writeColon();
                writeStringArray(writer, countryCodes);
                writer.writeName("categories");
                writer.writeColon();
                writeStringArray(writer, categories);
                writer.endObject();

                // WAWebMexFetchNewsletterDirectoryListJob.mexFetchNewsletterDirectoryList
                // input.limit = a
                if (limit != null) {
                    writer.writeName("limit");
                    writer.writeColon();
                    writer.writeInt64(limit);
                }
                // WAWebMexFetchNewsletterDirectoryListJob.mexFetchNewsletterDirectoryList
                // input.start_cursor = r
                if (cursorToken != null) {
                    writer.writeName("start_cursor");
                    writer.writeColon();
                    writer.writeString(cursorToken);
                }

                writer.endObject();

                // WAWebMexFetchNewsletterDirectoryListJob.mexFetchNewsletterDirectoryList
                // fetch_status_metadata: o("WAWebNewsletterGatingUtils").isNewsletterStatusReceiverEnabled()
                writer.writeName("fetch_status_metadata");
                writer.writeColon();
                writer.writeBool(fetchStatusMetadata);

                writer.endObject();
                writer.endObject();

                // ADAPTED: WAWebMexFetchNewsletterDirectoryListJob.mexFetchNewsletterDirectoryList
                // Flushes the JSON buffer into a StringWriter and wraps it in the shared MEX IQ envelope
                try (var output = new StringWriter()) {
                    writer.flushTo(output);
                    return MexJsonOperation.createMexNode(QUERY_ID, output.toString());
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        /**
         * Writes a string array, emitting an empty array when the input is
         * {@code null} so that the on-wire shape always contains the
         * {@code country_codes} and {@code categories} keys (matching the
         * JS object literal which never omits them).
         *
         * @param writer the JSON writer to emit into
         * @param values the string values to serialise, may be {@code null}
         */
        private static void writeStringArray(JSONWriter writer, List<String> values) {
            writer.startArray();
            if (values != null) {
                for (var i = 0; i < values.size(); i++) {
                    if (i > 0) {
                        writer.writeComma();
                    }
                    writer.writeString(values.get(i));
                }
            }
            writer.endArray();
        }
    }

    /**
     * The response variant of {@link FetchNewsletterDirectoryListMex} that exposes the data
     * returned by the server after a successful query.
     *
     * @implNote WAWebMexFetchNewsletterDirectoryListJob: adapts the JSON root returned by the GraphQL
     * query into a Java value object.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterDirectoryListJob")
    final class Response implements FetchNewsletterDirectoryListMex {
        private final PageInfo pageInfo;
        private final List<Result> result;

        private Response(PageInfo pageInfo, List<Result> result) {
            this.pageInfo = pageInfo;
            this.result = result;
        }

        /**
         * Parses a MEX response from the given IQ response node.
         *
         * @implNote WAWebMexFetchNewsletterDirectoryListJob.mexFetchNewsletterDirectoryList: WA Web relies on the
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
            private final ThreadMetadata threadMetadata;

            private Result(String id, ThreadMetadata threadMetadata) {
                this.id = id;
                this.threadMetadata = threadMetadata;
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
             * A parsed {@code ThreadMetadata} object.
             */
            public static final class ThreadMetadata {
                private final Long creationTime;
                private final String invite;
                private final String handle;
                private final Long subscribersCount;
                private final Name name;
                private final Description description;
                private final Picture picture;
                private final String verification;

                private ThreadMetadata(Long creationTime, String invite, String handle, Long subscribersCount, Name name, Description description, Picture picture, String verification) {
                    this.creationTime = creationTime;
                    this.invite = invite;
                    this.handle = handle;
                    this.subscribersCount = subscribersCount;
                    this.name = name;
                    this.description = description;
                    this.picture = picture;
                    this.verification = verification;
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
                 * Returns the {@code subscribers_count} field.
                 *
                 * @return an {@link OptionalLong} containing the value, or empty if absent
                 */
                public OptionalLong subscribersCount() {
                    return subscribersCount != null ? OptionalLong.of(subscribersCount) : OptionalLong.empty();
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
                 * A parsed {@code Picture} object.
                 */
                public static final class Picture {
                    private final String id;
                    private final String directPath;
                    private final String type;

                    private Picture(String id, String directPath, String type) {
                        this.id = id;
                        this.directPath = directPath;
                        this.type = type;
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
                     * Returns the {@code direct_path} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> directPath() {
                        return Optional.ofNullable(directPath);
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
                        var directPath = obj.getString("direct_path");
                        var type = obj.getString("type");
                        return Optional.of(new Picture(id, directPath, type));
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

                    var creationTime = obj.getLong("creation_time");
                    var invite = obj.getString("invite");
                    var handle = obj.getString("handle");
                    var subscribersCount = obj.getLong("subscribers_count");
                    var name = Name.of(obj.getJSONObject("name")).orElse(null);
                    var description = Description.of(obj.getJSONObject("description")).orElse(null);
                    var picture = Picture.of(obj.getJSONObject("picture")).orElse(null);
                    var verification = obj.getString("verification");
                    return Optional.of(new ThreadMetadata(creationTime, invite, handle, subscribersCount, name, description, picture, verification));
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
                return Optional.of(new Result(id, threadMetadata));
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
         * @implNote WAWebMexFetchNewsletterDirectoryListJob.mexFetchNewsletterDirectoryList: mirrors the implicit
         * unwrapping that WA Web performs on the GraphQL response,
         * extracting the {@code xwa2_newsletters_directory_list} root.
         * @param json the UTF-8 encoded JSON payload
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the envelope is missing expected fields
         */
        private static Optional<Response> of(byte[] json) {
            // WAWebMexFetchNewsletterDirectoryListJob.mexFetchNewsletterDirectoryList
            // Parses the raw JSON payload, bailing out if fastjson2 returns null
            var jsonObject = JSON.parseObject(json);
            if (jsonObject == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterDirectoryListJob.mexFetchNewsletterDirectoryList
            // Descends into the standard GraphQL "data" envelope
            var data = jsonObject.getJSONObject("data");
            if (data == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterDirectoryListJob.mexFetchNewsletterDirectoryList
            // Extracts the operation-specific root keyed by xwa2_newsletters_directory_list
            var root = data.getJSONObject("xwa2_newsletters_directory_list");
            if (root == null) {
                return Optional.empty();
            }

            var pageInfo = PageInfo.of(root.getJSONObject("page_info")).orElse(null);
            var result = Result.ofArray(root.getJSONArray("result"));

            return Optional.of(new Response(pageInfo, result));
        }
    }
}
