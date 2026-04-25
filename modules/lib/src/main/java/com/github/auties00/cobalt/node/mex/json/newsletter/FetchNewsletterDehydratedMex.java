package com.github.auties00.cobalt.node.mex.json.newsletter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.mex.json.MexJsonOperation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Fetches a lightweight dehydrated representation of a newsletter.
 *
 * <p>The dehydrated form carries only the minimal identifiers and state needed to display the newsletter in a list without triggering a full metadata hydration. WA Web uses it on chat-list rendering paths and follow suggestions.
 *
 * @implNote WAWebMexFetchNewsletterDehydratedJob: adapts the {@code mexGetNewsletterDehydrated} GraphQL query,
 * which in WA Web is invoked via {@code WAWebMexClient.fetchQuery} and
 * whose response is unwrapped by the same module. Cobalt models the request
 * and response as sibling variants of a sealed interface rather than a
 * free-standing async function.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterDehydratedJob")
public sealed interface FetchNewsletterDehydratedMex extends MexJsonOperation permits FetchNewsletterDehydratedMex.Request, FetchNewsletterDehydratedMex.Response {
    /**
     * The numeric GraphQL query identifier assigned by the WhatsApp relay
     * to the {@code FetchNewsletterDehydrated} compiled query.
     *
     * @implNote WAWebMexFetchNewsletterDehydratedJobQuery.graphql: corresponds to the compiled
     * document id registered for the {@code mexGetNewsletterDehydrated} query.
     */
    String QUERY_ID = "30328461880085868";

    /**
     * The request variant of {@link FetchNewsletterDehydratedMex} that serialises the
     * query variables and emits the outbound IQ stanza.
     *
     * @implNote WAWebMexFetchNewsletterDehydratedJob.mexGetNewsletterDehydrated: adapts the {@code variables}
     * object constructed inline in the JS implementation into a dedicated
     * Java class.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterDehydratedJob")
    final class Request implements FetchNewsletterDehydratedMex {
        private final Jid key;
        private final String viewRole;
        private final boolean fetchWamoSub;

        /**
         * Constructs a request for the dehydrated representation of the given
         * newsletter key.
         *
         * @implNote WAWebMexFetchNewsletterDehydratedJob.mexGetNewsletterDehydrated: WA Web's
         * {@code function u(t, a, i)} accepts the key {@code t}, the
         * {@code view_role} {@code a} and an options object {@code i} carrying
         * {@code fetchWamoSub}. The {@code type} variable is derived from
         * {@code WAWebWid.isNewsletter(t) ? "JID" : "INVITE"}.
         * @param key          the newsletter Jid or invite identifier
         * @param viewRole     the GraphQL {@code view_role} variable
         * @param fetchWamoSub whether to request the optional
         *                     {@code wamo_sub} fragment selections
         */
        public Request(Jid key, String viewRole, boolean fetchWamoSub) {
            this.key = Objects.requireNonNull(key, "key cannot be null");
            this.viewRole = viewRole;
            this.fetchWamoSub = fetchWamoSub;
        }

        /**
         * Builds the IQ stanza that dispatches this operation to the
         * WhatsApp relay.
         *
         * @implNote WAWebMexFetchNewsletterDehydratedJob.mexGetNewsletterDehydrated: WA Web constructs the
         * {@code variables} object inline as
         * {@code {input: {key: t, type: u, view_role: a}, fetch_wamo_sub: i.fetchWamoSub === true}}
         * and delegates to {@code WAWebMexClient.fetchQuery}. Cobalt writes
         * the JSON directly via {@code fastjson2.JSONWriter} and wraps it
         * through {@link MexJsonOperation#createMexNode(String, String)}.
         * @return a {@link NodeBuilder} carrying the IQ envelope and the
         *         serialised GraphQL variables
         */
        @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterDehydratedJob", exports = "mexGetNewsletterDehydrated",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public NodeBuilder toNode() {
            // WAWebMexFetchNewsletterDehydratedJob.mexGetNewsletterDehydrated
            // Opens a UTF-8 JSON writer that will serialise the GraphQL variables envelope
            try (var writer = JSONWriter.ofUTF8()) {
                // WAWebMexFetchNewsletterDehydratedJob.mexGetNewsletterDehydrated
                // Begins the outer envelope and the nested "variables" object consumed by WAWebMexClient.fetchQuery
                writer.startObject();
                writer.writeName("variables");
                writer.writeColon();
                writer.startObject();

                // WAWebMexFetchNewsletterDehydratedJob.mexGetNewsletterDehydrated
                // Builds the input object: {key: t, type: WAWebWid.isNewsletter(t) ? "JID" : "INVITE", view_role: a}
                writer.writeName("input");
                writer.writeColon();
                writer.startObject();
                writer.writeName("key");
                writer.writeColon();
                writer.writeString(key.toString());
                writer.writeName("type");
                writer.writeColon();
                // WAWebWid.isNewsletter(t) ? "JID" : "INVITE"
                writer.writeString(key.hasNewsletterServer() ? "JID" : "INVITE");
                writer.writeName("view_role");
                writer.writeColon();
                writer.writeString(viewRole);
                writer.endObject();

                // WAWebMexFetchNewsletterDehydratedJob.mexGetNewsletterDehydrated
                // fetch_wamo_sub: i.fetchWamoSub === true (always emitted, defaults to false)
                writer.writeName("fetch_wamo_sub");
                writer.writeColon();
                writer.writeBool(fetchWamoSub);

                writer.endObject();
                writer.endObject();

                // ADAPTED: WAWebMexFetchNewsletterDehydratedJob.mexGetNewsletterDehydrated
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
     * The response variant of {@link FetchNewsletterDehydratedMex} that exposes the data
     * returned by the server after a successful query.
     *
     * @implNote WAWebMexFetchNewsletterDehydratedJob: adapts the JSON root returned by the GraphQL
     * query into a Java value object.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterDehydratedJob")
    final class Response implements FetchNewsletterDehydratedMex {
        private final String id;
        private final ThreadMetadata threadMetadata;
        private final ViewerMetadata viewerMetadata;

        private Response(String id, ThreadMetadata threadMetadata, ViewerMetadata viewerMetadata) {
            this.id = id;
            this.threadMetadata = threadMetadata;
            this.viewerMetadata = viewerMetadata;
        }

        /**
         * Parses a MEX response from the given IQ response node.
         *
         * @implNote WAWebMexFetchNewsletterDehydratedJob.mexGetNewsletterDehydrated: WA Web relies on the
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
         * A parsed {@code ThreadMetadata} object.
         */
        public static final class ThreadMetadata {
            private final Long subscribersCount;
            private final String verification;
            private final Settings settings;
            private final WamoSub wamoSub;

            private ThreadMetadata(Long subscribersCount, String verification, Settings settings, WamoSub wamoSub) {
                this.subscribersCount = subscribersCount;
                this.verification = verification;
                this.settings = settings;
                this.wamoSub = wamoSub;
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

                var subscribersCount = obj.getLong("subscribers_count");
                var verification = obj.getString("verification");
                var settings = Settings.of(obj.getJSONObject("settings")).orElse(null);
                var wamoSub = WamoSub.of(obj.getJSONObject("wamo_sub")).orElse(null);
                return Optional.of(new ThreadMetadata(subscribersCount, verification, settings, wamoSub));
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
            private final String wamoSubStatus;

            private ViewerMetadata(String wamoSubStatus) {
                this.wamoSubStatus = wamoSubStatus;
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
             * Parses a {@code ViewerMetadata} from the given JSON object.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
             */
            static Optional<ViewerMetadata> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var wamoSubStatus = obj.getString("wamo_sub_status");
                return Optional.of(new ViewerMetadata(wamoSubStatus));
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
         * @implNote WAWebMexFetchNewsletterDehydratedJob.mexGetNewsletterDehydrated: mirrors the implicit
         * unwrapping that WA Web performs on the GraphQL response,
         * extracting the {@code xwa2_newsletter} root.
         * @param json the UTF-8 encoded JSON payload
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the envelope is missing expected fields
         */
        private static Optional<Response> of(byte[] json) {
            // WAWebMexFetchNewsletterDehydratedJob.mexGetNewsletterDehydrated
            // Parses the raw JSON payload, bailing out if fastjson2 returns null
            var jsonObject = JSON.parseObject(json);
            if (jsonObject == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterDehydratedJob.mexGetNewsletterDehydrated
            // Descends into the standard GraphQL "data" envelope
            var data = jsonObject.getJSONObject("data");
            if (data == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterDehydratedJob.mexGetNewsletterDehydrated
            // Extracts the operation-specific root keyed by xwa2_newsletter
            var root = data.getJSONObject("xwa2_newsletter");
            if (root == null) {
                return Optional.empty();
            }

            var id = root.getString("id");
            var threadMetadata = ThreadMetadata.of(root.getJSONObject("thread_metadata")).orElse(null);
            var viewerMetadata = ViewerMetadata.of(root.getJSONObject("viewer_metadata")).orElse(null);

            return Optional.of(new Response(id, threadMetadata, viewerMetadata));
        }
    }
}
