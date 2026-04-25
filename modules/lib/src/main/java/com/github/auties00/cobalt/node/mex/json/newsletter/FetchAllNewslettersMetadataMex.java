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
 * Fetches metadata for every newsletter followed by the authenticated user.
 *
 * <p>WA Web uses this query during login and periodic syncs to hydrate the local newsletter list. The response provides a collection of newsletter entries each with thread metadata, viewer role, and state.
 *
 * @implNote WAWebMexFetchAllNewslettersMetadataJob: adapts the {@code mexFetchAllNewsletters} GraphQL query,
 * which in WA Web is invoked via {@code WAWebMexClient.fetchQuery} and
 * whose response is unwrapped by the same module. Cobalt models the request
 * and response as sibling variants of a sealed interface rather than a
 * free-standing async function.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchAllNewslettersMetadataJob")
public sealed interface FetchAllNewslettersMetadataMex extends MexJsonOperation permits FetchAllNewslettersMetadataMex.Request, FetchAllNewslettersMetadataMex.Response {
    /**
     * The numeric GraphQL query identifier assigned by the WhatsApp relay
     * to the {@code FetchAllNewslettersMetadata} compiled query.
     *
     * @implNote WAWebMexFetchAllNewslettersMetadataJobQuery.graphql: corresponds to the compiled
     * document id registered for the {@code mexFetchAllNewsletters} query.
     */
    String QUERY_ID = "25399611239711790";

    /**
     * The request variant of {@link FetchAllNewslettersMetadataMex} that serialises the
     * query variables and emits the outbound IQ stanza.
     *
     * @implNote WAWebMexFetchAllNewslettersMetadataJob.mexFetchAllNewsletters: adapts the {@code variables}
     * object constructed inline in the JS implementation into a dedicated
     * Java class.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchAllNewslettersMetadataJob")
    final class Request implements FetchAllNewslettersMetadataMex {
        private final Boolean fetchWamoSub;
        private final Boolean fetchStatusMetadata;

        /**
         * Constructs a request that selects only the {@code fetch_wamo_sub}
         * gating flag.
         *
         * @implNote Convenience overload kept for backwards compatibility with
         *           callers that predate the {@code fetch_status_metadata}
         *           variable.
         * @param fetchWamoSub the value of the {@code fetch_wamo_sub} GraphQL
         *                     variable, or {@code null} to omit the field
         */
        public Request(Boolean fetchWamoSub) {
            this(fetchWamoSub, null);
        }

        /**
         * Constructs a request with both GraphQL gating variables.
         *
         * @implNote WAWebMexFetchAllNewslettersMetadataJob.mexFetchAllNewsletters:
         *           WA Web invokes
         *           {@code WAWebMexClient.fetchQuery(query, {fetch_wamo_sub,
         *           fetch_status_metadata})}; Cobalt mirrors the same two
         *           variables on the request object.
         * @param fetchWamoSub        the value of the {@code fetch_wamo_sub}
         *                            variable, or {@code null} to omit
         * @param fetchStatusMetadata the value of the
         *                            {@code fetch_status_metadata} variable,
         *                            or {@code null} to omit
         */
        public Request(Boolean fetchWamoSub, Boolean fetchStatusMetadata) {
            this.fetchWamoSub = fetchWamoSub;
            this.fetchStatusMetadata = fetchStatusMetadata;
        }

        /**
         * Builds the IQ stanza that dispatches this operation to the
         * WhatsApp relay.
         *
         * @implNote WAWebMexFetchAllNewslettersMetadataJob.mexFetchAllNewsletters: WA Web constructs the
         * {@code variables} object inline ({@code {fetch_wamo_sub: ...,
         * fetch_status_metadata: ...}}) and delegates to
         * {@code WAWebMexClient.fetchQuery}. Cobalt writes the JSON directly
         * via {@code fastjson2.JSONWriter} and wraps it through
         * {@link MexJsonOperation#createMexNode(String, String)}.
         * @return a {@link NodeBuilder} carrying the IQ envelope and the
         *         serialised GraphQL variables
         */
        @WhatsAppWebExport(moduleName = "WAWebMexFetchAllNewslettersMetadataJob", exports = "mexFetchAllNewsletters",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public NodeBuilder toNode() {
            // WAWebMexFetchAllNewslettersMetadataJob.mexFetchAllNewsletters
            // Opens a UTF-8 JSON writer that will serialise the GraphQL variables envelope
            try (var writer = JSONWriter.ofUTF8()) {
                // WAWebMexFetchAllNewslettersMetadataJob.mexFetchAllNewsletters
                // Begins the outer envelope and the nested "variables" object consumed by WAWebMexClient.fetchQuery
                writer.startObject();
                writer.writeName("variables");
                writer.writeColon();
                writer.startObject();
                // WAWebMexFetchAllNewslettersMetadataJob.mexFetchAllNewsletters
                // Emits the fetch_wamo_sub boolean variable when present
                if (fetchWamoSub != null) {
                    writer.writeName("fetch_wamo_sub");
                    writer.writeColon();
                    writer.writeBool(fetchWamoSub);
                }
                // WAWebMexFetchAllNewslettersMetadataJob.mexFetchAllNewsletters
                // Emits the fetch_status_metadata boolean variable when present
                if (fetchStatusMetadata != null) {
                    writer.writeName("fetch_status_metadata");
                    writer.writeColon();
                    writer.writeBool(fetchStatusMetadata);
                }
                writer.endObject();
                writer.endObject();
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
     * The response variant of {@link FetchAllNewslettersMetadataMex} that exposes the data
     * returned by the server after a successful query.
     *
     * @implNote WAWebMexFetchAllNewslettersMetadataJob: adapts the JSON root returned by the GraphQL
     * query into a Java value object.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchAllNewslettersMetadataJob")
    final class Response implements FetchAllNewslettersMetadataMex {
        private final List<Item> items;

        private Response(List<Item> items) {
            this.items = items;
        }

        /**
         * Parses a MEX response from the given IQ response node.
         *
         * @implNote WAWebMexFetchAllNewslettersMetadataJob.mexFetchAllNewsletters: WA Web relies on the
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
         * Returns the list of items in this response.
         *
         * @return the list of items, empty if absent
         */
        public List<Item> items() {
            return items;
        }

        /**
         * Splits the items in this response into active newsletters and the
         * subset that the relay reported as deleted.
         *
         * <p>Mirrors the WA Web {@code handleMexGetAllNewsletters} helper that
         * forwards the response from {@code mexFetchAllNewsletters} to the
         * Newsletter Collections layer. WA Web filters out {@code null}
         * entries, parses each surviving item through
         * {@code WAWebMexNewsletterParseUtils.parseMexNewsletterResponse} and
         * routes any item whose {@code state.type} equals
         * {@code "DELETED"} into the {@code deletedNewsletters} bucket. Cobalt
         * surfaces the same partition without invoking the JS-only parser:
         * callers map the active items into their domain objects and use the
         * deleted bucket to evict cached newsletters.
         *
         * @implNote WAWebMexFetchAllNewslettersMetadataJob.handleMexGetAllNewsletters:
         *           {@code t.filter(e=>e!=null).map(e=>{...if(e.state?.type==="DELETED")
         *           r.push({jid: parsed.idJid}); else n.push(parsed);});
         *           return n.length>0||r.length>0
         *               ? {newsletters:n, deletedNewsletters: r.length>0?{id:r}:null}
         *               : {newsletters:[]};}.
         * @return a {@link Partitioned} carrying the active and deleted item
         *         lists; both lists are unmodifiable
         */
        @WhatsAppWebExport(moduleName = "WAWebMexFetchAllNewslettersMetadataJob",
                exports = "handleMexGetAllNewsletters", adaptation = WhatsAppAdaptation.ADAPTED)
        public Partitioned partition() {
            // WAWebMexFetchAllNewslettersMetadataJob.handleMexGetAllNewsletters:
            // bail out fast on the empty payload to mirror the {newsletters: []} return
            if (items.isEmpty()) {
                return new Partitioned(List.of(), List.of());
            }
            var active = new ArrayList<Item>(items.size());
            var deleted = new ArrayList<Item>();
            // WAWebMexFetchAllNewslettersMetadataJob.handleMexGetAllNewsletters:
            // t.filter(e=>e!=null).map(e=>{...e.state?.type==="DELETED" ? r.push(...) : n.push(...)})
            for (var item : items) {
                if (item == null) {
                    continue;
                }
                var stateType = item.state()
                        .flatMap(Item.State::type)
                        .orElse(null);
                if ("DELETED".equals(stateType)) {
                    deleted.add(item);
                } else {
                    active.add(item);
                }
            }
            return new Partitioned(List.copyOf(active), List.copyOf(deleted));
        }

        /**
         * Carries the result of {@link Response#partition()}: the active
         * newsletters surfaced to the UI and the subset that the relay
         * reported as deleted.
         *
         * @param newsletters         the items whose {@code state.type} is not
         *                            {@code "DELETED"}, in server-defined order
         * @param deletedNewsletters  the items whose {@code state.type} is
         *                            {@code "DELETED"}, in server-defined order
         * @implNote WAWebMexFetchAllNewslettersMetadataJob.handleMexGetAllNewsletters:
         *           mirrors the {@code {newsletters, deletedNewsletters}}
         *           shape returned to the Newsletter Collections layer.
         */
        public record Partitioned(List<Item> newsletters, List<Item> deletedNewsletters) {
        }

        /**
         * A parsed {@code Item} object.
         */
        public static final class Item {
            private final String id;
            private final State state;
            private final ThreadMetadata threadMetadata;
            private final ViewerMetadata viewerMetadata;
            private final StatusMetadata statusMetadata;

            private Item(String id, State state, ThreadMetadata threadMetadata, ViewerMetadata viewerMetadata, StatusMetadata statusMetadata) {
                this.id = id;
                this.state = state;
                this.threadMetadata = threadMetadata;
                this.viewerMetadata = viewerMetadata;
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
             * Returns the {@code viewer_metadata} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<ViewerMetadata> viewerMetadata() {
                return Optional.ofNullable(viewerMetadata);
            }

            /**
             * Returns the {@code status_metadata} field, populated only when
             * the request set {@code fetch_status_metadata=true}.
             *
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
                private final Picture picture;
                private final Preview preview;
                private final Description description;
                private final String invite;
                private final String handle;
                private final String verification;
                private final Settings settings;
                private final WamoSub wamoSub;

                private ThreadMetadata(Long creationTime, Name name, Picture picture, Preview preview, Description description, String invite, String handle, String verification, Settings settings, WamoSub wamoSub) {
                    this.creationTime = creationTime;
                    this.name = name;
                    this.picture = picture;
                    this.preview = preview;
                    this.description = description;
                    this.invite = invite;
                    this.handle = handle;
                    this.verification = verification;
                    this.settings = settings;
                    this.wamoSub = wamoSub;
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
                 * Returns the {@code description} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<Description> description() {
                    return Optional.ofNullable(description);
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
                 * Returns the {@code settings} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<Settings> settings() {
                    return Optional.ofNullable(settings);
                }

                /**
                 * Returns the {@code wamo_sub} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<WamoSub> wamoSub() {
                    return Optional.ofNullable(wamoSub);
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
                 * A parsed {@code Settings} object.
                 */
                public static final class Settings {
                    private final ReactionCodes reactionCodes;

                    private Settings(ReactionCodes reactionCodes) {
                        this.reactionCodes = reactionCodes;
                    }

                    /**
                     * Returns the {@code reaction_codes} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<ReactionCodes> reactionCodes() {
                        return Optional.ofNullable(reactionCodes);
                    }

                    /**
                     * A parsed {@code ReactionCodes} object.
                     */
                    public static final class ReactionCodes {
                        private final String value;

                        private ReactionCodes(String value) {
                            this.value = value;
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
                         * Parses a {@code ReactionCodes} from the given JSON object.
                         *
                         * @param obj the JSON object to parse
                         * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                         */
                        static Optional<ReactionCodes> of(JSONObject obj) {
                            if (obj == null) {
                                return Optional.empty();
                            }

                            var value = obj.getString("value");
                            return Optional.of(new ReactionCodes(value));
                        }

                        /**
                         * Parses a list of {@code ReactionCodes} from the given JSON array.
                         *
                         * @param arr the JSON array to parse
                         * @return the list of parsed results, empty if {@code arr} is {@code null}
                         */
                        static List<ReactionCodes> ofArray(JSONArray arr) {
                            if (arr == null) {
                                return List.of();
                            }

                            var result = new ArrayList<ReactionCodes>(arr.size());
                            for (var i = 0; i < arr.size(); i++) {
                                of(arr.getJSONObject(i)).ifPresent(result::add);
                            }
                            return result;
                        }
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

                        var reactionCodes = ReactionCodes.of(obj.getJSONObject("reaction_codes")).orElse(null);
                        return Optional.of(new Settings(reactionCodes));
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
                 * A parsed {@code WamoSub} object.
                 */
                public static final class WamoSub {
                    private final String planId;

                    private WamoSub(String planId) {
                        this.planId = planId;
                    }

                    /**
                     * Returns the {@code plan_id} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> planId() {
                        return Optional.ofNullable(planId);
                    }

                    /**
                     * Parses a {@code WamoSub} from the given JSON object.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                     */
                    static Optional<WamoSub> of(JSONObject obj) {
                        if (obj == null) {
                            return Optional.empty();
                        }

                        var planId = obj.getString("plan_id");
                        return Optional.of(new WamoSub(planId));
                    }

                    /**
                     * Parses a list of {@code WamoSub} from the given JSON array.
                     *
                     * @param arr the JSON array to parse
                     * @return the list of parsed results, empty if {@code arr} is {@code null}
                     */
                    static List<WamoSub> ofArray(JSONArray arr) {
                        if (arr == null) {
                            return List.of();
                        }

                        var result = new ArrayList<WamoSub>(arr.size());
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
                    var picture = Picture.of(obj.getJSONObject("picture")).orElse(null);
                    var preview = Preview.of(obj.getJSONObject("preview")).orElse(null);
                    var description = Description.of(obj.getJSONObject("description")).orElse(null);
                    var invite = obj.getString("invite");
                    var handle = obj.getString("handle");
                    var verification = obj.getString("verification");
                    var settings = Settings.of(obj.getJSONObject("settings")).orElse(null);
                    var wamoSub = WamoSub.of(obj.getJSONObject("wamo_sub")).orElse(null);
                    return Optional.of(new ThreadMetadata(creationTime, name, picture, preview, description, invite, handle, verification, settings, wamoSub));
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
                private final String wamoSubStatus;

                private ViewerMetadata(List<Settings> settings, String role, String wamoSubStatus) {
                    this.settings = settings;
                    this.role = role;
                    this.wamoSubStatus = wamoSubStatus;
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
                 * Returns the {@code wamo_sub_status} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> wamoSubStatus() {
                    return Optional.ofNullable(wamoSubStatus);
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
                    var wamoSubStatus = obj.getString("wamo_sub_status");
                    return Optional.of(new ViewerMetadata(settings, role, wamoSubStatus));
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
             * A parsed {@code StatusMetadata} object that exposes the
             * {@code last_status_server_id} and {@code last_status_sent_time}
             * scalars described by the GraphQL fragment under the
             * {@code XWA2NewsletterStatusMetadata} concrete type.
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
            }

            /**
             * Parses a {@code Item} from the given JSON object.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
             */
            static Optional<Item> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var id = obj.getString("id");
                var state = State.of(obj.getJSONObject("state")).orElse(null);
                var threadMetadata = ThreadMetadata.of(obj.getJSONObject("thread_metadata")).orElse(null);
                var viewerMetadata = ViewerMetadata.of(obj.getJSONObject("viewer_metadata")).orElse(null);
                var statusMetadata = StatusMetadata.of(obj.getJSONObject("status_metadata")).orElse(null);
                return Optional.of(new Item(id, state, threadMetadata, viewerMetadata, statusMetadata));
            }

            /**
             * Parses a list of {@code Item} from the given JSON array.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed results, empty if {@code arr} is {@code null}
             */
            static List<Item> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<Item>(arr.size());
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
         * @implNote WAWebMexFetchAllNewslettersMetadataJob.mexFetchAllNewsletters: mirrors the implicit
         * unwrapping that WA Web performs on the GraphQL response,
         * extracting the {@code xwa2_newsletter_subscribed} root.
         * @param json the UTF-8 encoded JSON payload
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the envelope is missing expected fields
         */
        private static Optional<Response> of(byte[] json) {
            // WAWebMexFetchAllNewslettersMetadataJob.mexFetchAllNewsletters
            // Parses the raw JSON payload, bailing out if fastjson2 returns null
            var jsonObject = JSON.parseObject(json);
            if (jsonObject == null) {
                return Optional.empty();
            }

            // WAWebMexFetchAllNewslettersMetadataJob.mexFetchAllNewsletters
            // Descends into the standard GraphQL "data" envelope
            var data = jsonObject.getJSONObject("data");
            if (data == null) {
                return Optional.empty();
            }

            // WAWebMexFetchAllNewslettersMetadataJob.mexFetchAllNewsletters
            // Extracts the xwa2_newsletter_subscribed JSON array and parses every newsletter entry through Item.ofArray
            var rootArr = data.getJSONArray("xwa2_newsletter_subscribed");
            var items = Item.ofArray(rootArr);

            return Optional.of(new Response(items));
        }
    }
}
