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
 * Searches the newsletter directory for channels matching the given query.
 *
 * <p>This query powers the newsletter directory search experience, returning paginated channels whose names, handles or descriptions match the supplied text query.
 *
 * @implNote WAWebMexFetchNewsletterDirectorySearchResultsJob: adapts the {@code mexFetchNewsletterDirectorySearchResults} GraphQL query,
 * which in WA Web is invoked via {@code WAWebMexClient.fetchQuery} and
 * whose response is unwrapped by the same module. Cobalt models the request
 * and response as sibling variants of a sealed interface rather than a
 * free-standing async function.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterDirectorySearchResultsJob")
public sealed interface FetchNewsletterDirectorySearchResultsMex extends MexJsonOperation permits FetchNewsletterDirectorySearchResultsMex.Request, FetchNewsletterDirectorySearchResultsMex.Response {
    /**
     * The numeric GraphQL query identifier assigned by the WhatsApp relay
     * to the {@code FetchNewsletterDirectorySearchResults} compiled query.
     *
     * @implNote WAWebMexFetchNewsletterDirectorySearchResultsJobQuery.graphql: corresponds to the compiled
     * document id registered for the {@code mexFetchNewsletterDirectorySearchResults} query.
     */
    String QUERY_ID = "9699865846759651";

    /**
     * The request variant of {@link FetchNewsletterDirectorySearchResultsMex} that serialises the
     * query variables and emits the outbound IQ stanza.
     *
     * @implNote WAWebMexFetchNewsletterDirectorySearchResultsJob.mexFetchNewsletterDirectorySearchResults: adapts the {@code variables}
     * object constructed inline in the JS implementation into a dedicated
     * Java class.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterDirectorySearchResultsJob")
    final class Request implements FetchNewsletterDirectorySearchResultsMex {
        private final String searchText;
        private final List<String> categories;
        private final Long limit;
        private final String cursorToken;
        private final boolean fetchStatusMetadata;

        /**
         * Constructs a new request for the newsletter directory search query.
         *
         * @param searchText          the free-text search query; corresponds to {@code e.searchText} in the JS source
         * @param categories          the categories filter as upper-case on-wire values
         *                            (e.g. {@code "BUSINESS"}); WA Web obtains these via
         *                            {@code WAWebNewsletterDirectoryCategoryUtils.getCategoryValueFromEnum}
         * @param limit               the page size, may be {@code null}
         * @param cursorToken         the start cursor for pagination, may be {@code null}
         * @param fetchStatusMetadata whether to include {@code status_metadata} in the response, set
         *                            from {@code WAWebNewsletterGatingUtils.isNewsletterStatusReceiverEnabled()}
         * @implNote WAWebMexFetchNewsletterDirectorySearchResultsJob.mexFetchNewsletterDirectorySearchResults:
         * mirrors the inline destructure {@code var t=e.categories,n=e.cursorToken,r=e.limit,a=e.searchText}.
         */
        public Request(String searchText,
                       List<String> categories,
                       Long limit,
                       String cursorToken,
                       boolean fetchStatusMetadata) {
            this.searchText = searchText;
            this.categories = categories;
            this.limit = limit;
            this.cursorToken = cursorToken;
            this.fetchStatusMetadata = fetchStatusMetadata;
        }

        /**
         * Builds the IQ stanza that dispatches this operation to the
         * WhatsApp relay.
         *
         * @implNote WAWebMexFetchNewsletterDirectorySearchResultsJob.mexFetchNewsletterDirectorySearchResults: WA Web constructs the
         * {@code variables} object inline and delegates to
         * {@code WAWebMexClient.fetchQuery}. Cobalt writes the JSON directly
         * via {@code fastjson2.JSONWriter} and wraps it through
         * {@link MexJsonOperation#createMexNode(String, String)}.
         * @return a {@link NodeBuilder} carrying the IQ envelope and the
         *         serialised GraphQL variables
         */
        @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterDirectorySearchResultsJob", exports = "mexFetchNewsletterDirectorySearchResults",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public NodeBuilder toNode() {
            // WAWebMexFetchNewsletterDirectorySearchResultsJob.mexFetchNewsletterDirectorySearchResults
            // Opens a UTF-8 JSON writer that will serialise the GraphQL variables envelope
            try (var writer = JSONWriter.ofUTF8()) {
                // WAWebMexFetchNewsletterDirectorySearchResultsJob.mexFetchNewsletterDirectorySearchResults
                // Begins the outer envelope and the nested "variables" object consumed by WAWebMexClient.fetchQuery
                writer.startObject();
                writer.writeName("variables");
                writer.writeColon();
                writer.startObject();

                // WAWebMexFetchNewsletterDirectorySearchResultsJob.mexFetchNewsletterDirectorySearchResults
                // var i={input:{search_text:a,categories:t.map(...),limit:r,start_cursor:n}, fetch_status_metadata:...}
                writer.writeName("input");
                writer.writeColon();
                writer.startObject();

                // WAWebMexFetchNewsletterDirectorySearchResultsJob.mexFetchNewsletterDirectorySearchResults
                // input.search_text = a
                writer.writeName("search_text");
                writer.writeColon();
                writer.writeString(searchText);

                // WAWebMexFetchNewsletterDirectorySearchResultsJob.mexFetchNewsletterDirectorySearchResults
                // input.categories = t.map(WAWebNewsletterDirectoryCategoryUtils.getCategoryValueFromEnum)
                writer.writeName("categories");
                writer.writeColon();
                writer.startArray();
                if (categories != null) {
                    for (var i = 0; i < categories.size(); i++) {
                        if (i > 0) {
                            writer.writeComma();
                        }
                        writer.writeString(categories.get(i));
                    }
                }
                writer.endArray();

                // WAWebMexFetchNewsletterDirectorySearchResultsJob.mexFetchNewsletterDirectorySearchResults
                // input.limit = r
                if (limit != null) {
                    writer.writeName("limit");
                    writer.writeColon();
                    writer.writeInt64(limit);
                }
                // WAWebMexFetchNewsletterDirectorySearchResultsJob.mexFetchNewsletterDirectorySearchResults
                // input.start_cursor = n
                if (cursorToken != null) {
                    writer.writeName("start_cursor");
                    writer.writeColon();
                    writer.writeString(cursorToken);
                }

                writer.endObject();

                // WAWebMexFetchNewsletterDirectorySearchResultsJob.mexFetchNewsletterDirectorySearchResults
                // fetch_status_metadata: o("WAWebNewsletterGatingUtils").isNewsletterStatusReceiverEnabled()
                writer.writeName("fetch_status_metadata");
                writer.writeColon();
                writer.writeBool(fetchStatusMetadata);

                writer.endObject();
                writer.endObject();

                // ADAPTED: WAWebMexFetchNewsletterDirectorySearchResultsJob.mexFetchNewsletterDirectorySearchResults
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
     * The response variant of {@link FetchNewsletterDirectorySearchResultsMex} that exposes the data
     * returned by the server after a successful query.
     *
     * @implNote WAWebMexFetchNewsletterDirectorySearchResultsJob: adapts the JSON root returned by the GraphQL
     * query into a Java value object.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterDirectorySearchResultsJob")
    final class Response implements FetchNewsletterDirectorySearchResultsMex {
        private final PageInfo pageInfo;
        private final List<Result> result;

        private Response(PageInfo pageInfo, List<Result> result) {
            this.pageInfo = pageInfo;
            this.result = result;
        }

        /**
         * Parses a MEX response from the given IQ response node.
         *
         * @implNote WAWebMexFetchNewsletterDirectorySearchResultsJob.mexFetchNewsletterDirectorySearchResults: WA Web relies on the
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
         * @implNote WAWebMexFetchNewsletterDirectorySearchResultsJob.mexFetchNewsletterDirectorySearchResults: mirrors the implicit
         * unwrapping that WA Web performs on the GraphQL response,
         * extracting the {@code xwa2_newsletters_directory_search} root.
         * @param json the UTF-8 encoded JSON payload
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the envelope is missing expected fields
         */
        private static Optional<Response> of(byte[] json) {
            // WAWebMexFetchNewsletterDirectorySearchResultsJob.mexFetchNewsletterDirectorySearchResults
            // Parses the raw JSON payload, bailing out if fastjson2 returns null
            var jsonObject = JSON.parseObject(json);
            if (jsonObject == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterDirectorySearchResultsJob.mexFetchNewsletterDirectorySearchResults
            // Descends into the standard GraphQL "data" envelope
            var data = jsonObject.getJSONObject("data");
            if (data == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterDirectorySearchResultsJob.mexFetchNewsletterDirectorySearchResults
            // Extracts the operation-specific root keyed by xwa2_newsletters_directory_search
            var root = data.getJSONObject("xwa2_newsletters_directory_search");
            if (root == null) {
                return Optional.empty();
            }

            var pageInfo = PageInfo.of(root.getJSONObject("page_info")).orElse(null);
            var result = Result.ofArray(root.getJSONArray("result"));

            return Optional.of(new Response(pageInfo, result));
        }
    }
}
