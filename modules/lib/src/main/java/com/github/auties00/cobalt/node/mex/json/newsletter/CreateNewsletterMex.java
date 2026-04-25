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
 * Creates a new newsletter owned by the authenticated user.
 *
 * <p>This mutation registers a new newsletter channel with the provided name, description and picture metadata. On success the server returns the fully-hydrated newsletter metadata including the generated id, initial state, thread metadata and viewer role.
 *
 * @implNote WAWebMexCreateNewsletterJob: adapts the {@code mexCreateNewsletter} GraphQL mutation,
 * which in WA Web is invoked via {@code WAWebMexClient.fetchQuery} and
 * whose response is unwrapped by the same module. Cobalt models the request
 * and response as sibling variants of a sealed interface rather than a
 * free-standing async function.
 */
@WhatsAppWebModule(moduleName = "WAWebMexCreateNewsletterJob")
public sealed interface CreateNewsletterMex extends MexJsonOperation permits CreateNewsletterMex.Request, CreateNewsletterMex.Response {
    /**
     * The numeric GraphQL query identifier assigned by the WhatsApp relay
     * to the {@code CreateNewsletter} compiled mutation.
     *
     * @implNote WAWebMexCreateNewsletterJobMutation.graphql: corresponds to the compiled
     * document id registered for the {@code mexCreateNewsletter} mutation.
     */
    String QUERY_ID = "25149874324715067";

    /**
     * The request variant of {@link CreateNewsletterMex} that serialises the
     * mutation variables and emits the outbound IQ stanza.
     *
     * @implNote WAWebMexCreateNewsletterJob.mexCreateNewsletter: adapts the {@code variables}
     * object constructed inline in the JS implementation into a dedicated
     * Java class.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexCreateNewsletterJob")
    final class Request implements CreateNewsletterMex {
        private final String name;
        private final String description;
        private final String picture;

        /**
         * Constructs a new request with the given mutation variables.
         *
         * @implNote WAWebMexCreateNewsletterJob.mexCreateNewsletter: mirrors the
         * {@code (t, r, a)} positional arguments of the JS function which are
         * placed under {@code input.name}, {@code input.description} and
         * {@code input.picture}.
         * @param name        the newsletter display name; may be {@code null}
         * @param description the newsletter description; may be {@code null}
         * @param picture     the base64 or direct-path encoded picture payload;
         *                    may be {@code null}
         */
        public Request(String name, String description, String picture) {
            this.name = name;
            this.description = description;
            this.picture = picture;
        }

        /**
         * Builds the IQ stanza that dispatches this operation to the
         * WhatsApp relay.
         *
         * @implNote WAWebMexCreateNewsletterJob.mexCreateNewsletter: WA Web constructs the
         * {@code {input: {name: t, description: r, picture: a}}} variables
         * object inline and delegates to {@code WAWebMexClient.fetchQuery}.
         * Cobalt writes the JSON directly via {@code fastjson2.JSONWriter} and
         * wraps it through {@link MexJsonOperation#createMexNode(String, String)}.
         * @return a {@link NodeBuilder} carrying the IQ envelope and the
         *         serialised GraphQL variables
         */
        @WhatsAppWebExport(moduleName = "WAWebMexCreateNewsletterJob", exports = "mexCreateNewsletter",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public NodeBuilder toNode() {
            // WAWebMexCreateNewsletterJob.mexCreateNewsletter
            // Opens a UTF-8 JSON writer that will serialise the GraphQL variables envelope
            try (var writer = JSONWriter.ofUTF8()) {
                // WAWebMexCreateNewsletterJob.mexCreateNewsletter
                // Begins the outer envelope and the nested "variables" object consumed by WAWebMexClient.fetchQuery
                writer.startObject();
                writer.writeName("variables");
                writer.writeColon();
                writer.startObject();
                // WAWebMexCreateNewsletterJob.mexCreateNewsletter: {input: {name: t, description: r, picture: a}}
                writer.writeName("input");
                writer.writeColon();
                writer.startObject();
                writer.writeName("name");
                writer.writeColon();
                writer.writeString(name);
                writer.writeName("description");
                writer.writeColon();
                writer.writeString(description);
                writer.writeName("picture");
                writer.writeColon();
                writer.writeString(picture);
                writer.endObject();
                writer.endObject();
                writer.endObject();

                // ADAPTED: WAWebMexCreateNewsletterJob.mexCreateNewsletter
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
     * The response variant of {@link CreateNewsletterMex} that exposes the data
     * returned by the server after a successful mutation.
     *
     * @implNote WAWebMexCreateNewsletterJob: adapts the JSON root returned by the GraphQL
     * mutation into a Java value object.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexCreateNewsletterJob")
    final class Response implements CreateNewsletterMex {
        private final String id;
        private final State state;
        private final ThreadMetadata threadMetadata;
        private final ViewerMetadata viewerMetadata;

        private Response(String id, State state, ThreadMetadata threadMetadata, ViewerMetadata viewerMetadata) {
            this.id = id;
            this.state = state;
            this.threadMetadata = threadMetadata;
            this.viewerMetadata = viewerMetadata;
        }

        /**
         * Parses a MEX response from the given IQ response node.
         *
         * @implNote WAWebMexCreateNewsletterJob.mexCreateNewsletter: WA Web relies on the
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
         * Returns the {@code viewer_metadata} field.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<ViewerMetadata> viewerMetadata() {
            return Optional.ofNullable(viewerMetadata);
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
            private final Name name;
            private final Description description;
            private final Picture picture;
            private final Preview preview;
            private final String invite;
            private final String handle;
            private final String verification;
            private final Long subscribersCount;
            private final Long creationTime;

            private ThreadMetadata(Name name, Description description, Picture picture, Preview preview, String invite, String handle, String verification, Long subscribersCount, Long creationTime) {
                this.name = name;
                this.description = description;
                this.picture = picture;
                this.preview = preview;
                this.invite = invite;
                this.handle = handle;
                this.verification = verification;
                this.subscribersCount = subscribersCount;
                this.creationTime = creationTime;
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
             * Returns the {@code creation_time} field.
             *
             * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
             */
            public Optional<Instant> creationTime() {
                return Optional.ofNullable(creationTime).map(Instant::ofEpochSecond);
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

                var name = Name.of(obj.getJSONObject("name")).orElse(null);
                var description = Description.of(obj.getJSONObject("description")).orElse(null);
                var picture = Picture.of(obj.getJSONObject("picture")).orElse(null);
                var preview = Preview.of(obj.getJSONObject("preview")).orElse(null);
                var invite = obj.getString("invite");
                var handle = obj.getString("handle");
                var verification = obj.getString("verification");
                var subscribersCount = obj.getLong("subscribers_count");
                var creationTime = obj.getLong("creation_time");
                return Optional.of(new ThreadMetadata(name, description, picture, preview, invite, handle, verification, subscribersCount, creationTime));
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
         * A parsed {@code ViewerMetadata} object.
         */
        public static final class ViewerMetadata {
            private final List<Settings> settings;
            private final String role;

            private ViewerMetadata(List<Settings> settings, String role) {
                this.settings = settings;
                this.role = role;
            }

            /**
             * Returns the {@code settings} field.
             *
             * @return the list of values, empty if absent
             */
            public List<Settings> settings() {
                return settings;
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
             * A parsed {@code Settings} object.
             */
            public static final class Settings {
                private final String type;
                private final String value;

                private Settings(String type, String value) {
                    this.type = type;
                    this.value = value;
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
                 * Returns the {@code value} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> value() {
                    return Optional.ofNullable(value);
                }

                /**
                 * Parses a {@code Settings} from the given JSON object.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                 */
                static Optional<Settings> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var type = obj.getString("type");
                    var value = obj.getString("value");
                    return Optional.of(new Settings(type, value));
                }

                /**
                 * Parses a list of {@code Settings} from the given JSON array.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed results, empty if {@code arr} is {@code null}
                 */
                static List<Settings> ofArray(JSONArray arr) {
                    if (arr == null) {
                        return List.of();
                    }

                    var result = new ArrayList<Settings>(arr.size());
                    for (var i = 0; i < arr.size(); i++) {
                        of(arr.getJSONObject(i)).ifPresent(result::add);
                    }
                    return result;
                }
            }

            /**
             * Parses a {@code ViewerMetadata} from the given JSON object.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
             */
            static Optional<ViewerMetadata> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var settings = Settings.ofArray(obj.getJSONArray("settings"));
                var role = obj.getString("role");
                return Optional.of(new ViewerMetadata(settings, role));
            }

            /**
             * Parses a list of {@code ViewerMetadata} from the given JSON array.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed results, empty if {@code arr} is {@code null}
             */
            static List<ViewerMetadata> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<ViewerMetadata>(arr.size());
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
         * @implNote WAWebMexCreateNewsletterJob.mexCreateNewsletter: mirrors the implicit
         * unwrapping that WA Web performs on the GraphQL response,
         * extracting the {@code xwa2_newsletter_create} root.
         * @param json the UTF-8 encoded JSON payload
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the envelope is missing expected fields
         */
        private static Optional<Response> of(byte[] json) {
            // WAWebMexCreateNewsletterJob.mexCreateNewsletter
            // Parses the raw JSON payload, bailing out if fastjson2 returns null
            var jsonObject = JSON.parseObject(json);
            if (jsonObject == null) {
                return Optional.empty();
            }

            // WAWebMexCreateNewsletterJob.mexCreateNewsletter
            // Descends into the standard GraphQL "data" envelope
            var data = jsonObject.getJSONObject("data");
            if (data == null) {
                return Optional.empty();
            }

            // WAWebMexCreateNewsletterJob.mexCreateNewsletter
            // Extracts the operation-specific root keyed by xwa2_newsletter_create
            var root = data.getJSONObject("xwa2_newsletter_create");
            if (root == null) {
                return Optional.empty();
            }

            var id = root.getString("id");
            var state = State.of(root.getJSONObject("state")).orElse(null);
            var threadMetadata = ThreadMetadata.of(root.getJSONObject("thread_metadata")).orElse(null);
            var viewerMetadata = ViewerMetadata.of(root.getJSONObject("viewer_metadata")).orElse(null);

            return Optional.of(new Response(id, state, threadMetadata, viewerMetadata));
        }
    }
}
