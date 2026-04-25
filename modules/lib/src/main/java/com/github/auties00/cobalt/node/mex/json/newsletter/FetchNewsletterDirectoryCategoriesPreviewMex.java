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
 * Fetches a preview of newsletter directory categories with featured channels.
 *
 * <p>This query powers the newsletter directory landing screen, returning a list of categories each accompanied by a handful of featured newsletters for visual preview.
 *
 * @implNote WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob: adapts the {@code mexFetchNewsletterDirectoryCategoriesPreview} GraphQL query,
 * which in WA Web is invoked via {@code WAWebMexClient.fetchQuery} and
 * whose response is unwrapped by the same module. Cobalt models the request
 * and response as sibling variants of a sealed interface rather than a
 * free-standing async function.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob")
public sealed interface FetchNewsletterDirectoryCategoriesPreviewMex extends MexJsonOperation permits FetchNewsletterDirectoryCategoriesPreviewMex.Request, FetchNewsletterDirectoryCategoriesPreviewMex.Response {
    /**
     * The numeric GraphQL query identifier assigned by the WhatsApp relay
     * to the {@code FetchNewsletterDirectoryCategoriesPreview} compiled query.
     *
     * @implNote WAWebMexFetchNewsletterDirectoryCategoriesPreviewJobQuery.graphql: corresponds to the compiled
     * document id registered for the {@code mexFetchNewsletterDirectoryCategoriesPreview} query.
     */
    String QUERY_ID = "35266481849605779";

    /**
     * The request variant of {@link FetchNewsletterDirectoryCategoriesPreviewMex} that serialises the
     * query variables and emits the outbound IQ stanza.
     *
     * @implNote WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob.mexFetchNewsletterDirectoryCategoriesPreview: adapts the {@code variables}
     * object constructed inline in the JS implementation into a dedicated
     * Java class.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob")
    final class Request implements FetchNewsletterDirectoryCategoriesPreviewMex {
        private final String input;

        public Request(String input) {
            this.input = input;
        }

        /**
         * Builds the IQ stanza that dispatches this operation to the
         * WhatsApp relay.
         *
         * @implNote WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob.mexFetchNewsletterDirectoryCategoriesPreview: WA Web constructs the
         * {@code variables} object inline and delegates to
         * {@code WAWebMexClient.fetchQuery}. Cobalt writes the JSON directly
         * via {@code fastjson2.JSONWriter} and wraps it through
         * {@link MexJsonOperation#createMexNode(String, String)}.
         * @return a {@link NodeBuilder} carrying the IQ envelope and the
         *         serialised GraphQL variables
         */
        @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob", exports = "mexFetchNewsletterDirectoryCategoriesPreview",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public NodeBuilder toNode() {
            // WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob.mexFetchNewsletterDirectoryCategoriesPreview
            // Opens a UTF-8 JSON writer that will serialise the GraphQL variables envelope
            try (var writer = JSONWriter.ofUTF8()) {
                // WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob.mexFetchNewsletterDirectoryCategoriesPreview
                // Begins the outer envelope and the nested "variables" object consumed by WAWebMexClient.fetchQuery
                writer.startObject();
                writer.writeName("variables");
                writer.writeColon();
                writer.startObject();
                // WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob.mexFetchNewsletterDirectoryCategoriesPreview
                // Emits the input variable when present
                if (input != null) {
                    writer.writeName("input");
                    writer.writeColon();
                    writer.writeString(input);
                }
                writer.endObject();
                writer.endObject();

                // ADAPTED: WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob.mexFetchNewsletterDirectoryCategoriesPreview
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
     * The response variant of {@link FetchNewsletterDirectoryCategoriesPreviewMex} that exposes the data
     * returned by the server after a successful query.
     *
     * @implNote WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob: adapts the JSON root returned by the GraphQL
     * query into a Java value object.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob")
    final class Response implements FetchNewsletterDirectoryCategoriesPreviewMex {
        private final List<Result> result;

        private Response(List<Result> result) {
            this.result = result;
        }

        /**
         * Parses a MEX response from the given IQ response node.
         *
         * @implNote WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob.mexFetchNewsletterDirectoryCategoriesPreview: WA Web relies on the
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
            private final String category;
            private final String categoryTitle;
            private final List<Newsletters> newsletters;

            private Result(String category, String categoryTitle, List<Newsletters> newsletters) {
                this.category = category;
                this.categoryTitle = categoryTitle;
                this.newsletters = newsletters;
            }

            /**
             * Returns the {@code category} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> category() {
                return Optional.ofNullable(category);
            }

            /**
             * Returns the {@code category_title} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> categoryTitle() {
                return Optional.ofNullable(categoryTitle);
            }

            /**
             * Returns the {@code newsletters} field.
             *
             * @return the list of values, empty if absent
             */
            public List<Newsletters> newsletters() {
                return newsletters;
            }

            /**
             * A parsed {@code Newsletters} object.
             */
            public static final class Newsletters {
                private final String id;
                private final ThreadMetadata threadMetadata;

                private Newsletters(String id, ThreadMetadata threadMetadata) {
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
                 * Parses a {@code Newsletters} from the given JSON object.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                 */
                static Optional<Newsletters> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var id = obj.getString("id");
                    var threadMetadata = ThreadMetadata.of(obj.getJSONObject("thread_metadata")).orElse(null);
                    return Optional.of(new Newsletters(id, threadMetadata));
                }

                /**
                 * Parses a list of {@code Newsletters} from the given JSON array.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed results, empty if {@code arr} is {@code null}
                 */
                static List<Newsletters> ofArray(JSONArray arr) {
                    if (arr == null) {
                        return List.of();
                    }

                    var result = new ArrayList<Newsletters>(arr.size());
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

                var category = obj.getString("category");
                var categoryTitle = obj.getString("category_title");
                var newsletters = Newsletters.ofArray(obj.getJSONArray("newsletters"));
                return Optional.of(new Result(category, categoryTitle, newsletters));
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
         * @implNote WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob.mexFetchNewsletterDirectoryCategoriesPreview: mirrors the implicit
         * unwrapping that WA Web performs on the GraphQL response,
         * extracting the {@code xwa2_newsletters_directory_category_preview} root.
         * @param json the UTF-8 encoded JSON payload
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the envelope is missing expected fields
         */
        private static Optional<Response> of(byte[] json) {
            // WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob.mexFetchNewsletterDirectoryCategoriesPreview
            // Parses the raw JSON payload, bailing out if fastjson2 returns null
            var jsonObject = JSON.parseObject(json);
            if (jsonObject == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob.mexFetchNewsletterDirectoryCategoriesPreview
            // Descends into the standard GraphQL "data" envelope
            var data = jsonObject.getJSONObject("data");
            if (data == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob.mexFetchNewsletterDirectoryCategoriesPreview
            // Extracts the operation-specific root keyed by xwa2_newsletters_directory_category_preview
            var root = data.getJSONObject("xwa2_newsletters_directory_category_preview");
            if (root == null) {
                return Optional.empty();
            }

            var result = Result.ofArray(root.getJSONArray("result"));

            return Optional.of(new Response(result));
        }
    }
}
