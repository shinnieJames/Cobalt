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

/**
 * Fetches analytics insights for a newsletter.
 *
 * <p>Insights include aggregated metrics such as views, reactions, forwards and follower growth over time. The admin dashboard consumes this query to display publisher analytics.
 *
 * @implNote WAWebMexFetchNewsletterInsightsJob: adapts the {@code mexFetchNewsletterInsights} GraphQL query,
 * which in WA Web is invoked via {@code WAWebMexClient.fetchQuery} and
 * whose response is unwrapped by the same module. Cobalt models the request
 * and response as sibling variants of a sealed interface rather than a
 * free-standing async function.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterInsightsJob")
public sealed interface FetchNewsletterInsightsMex extends MexJsonOperation permits FetchNewsletterInsightsMex.Request, FetchNewsletterInsightsMex.Response {
    /**
     * The numeric GraphQL query identifier assigned by the WhatsApp relay
     * to the {@code FetchNewsletterInsights} compiled query.
     *
     * @implNote WAWebMexFetchNewsletterInsightsJobQuery.graphql: corresponds to the compiled
     * document id registered for the {@code mexFetchNewsletterInsights} query.
     */
    String QUERY_ID = "9853618868050977";

    /**
     * The request variant of {@link FetchNewsletterInsightsMex} that serialises the
     * query variables and emits the outbound IQ stanza.
     *
     * @implNote WAWebMexFetchNewsletterInsightsJob.mexFetchNewsletterInsights: adapts the {@code variables}
     * object constructed inline in the JS implementation into a dedicated
     * Java class.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterInsightsJob")
    final class Request implements FetchNewsletterInsightsMex {
        /**
         * The identifier of the newsletter whose insights are being fetched.
         *
         * @implNote WAWebMexFetchNewsletterInsightsJob.mexFetchNewsletterInsights: mirrors the
         * {@code newsletter_id} field of the GraphQL {@code input} variable
         * destructured from {@code e.newsletterJid}.
         */
        @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterInsightsJob", exports = "mexFetchNewsletterInsights",
                adaptation = WhatsAppAdaptation.DIRECT)
        private final String newsletterId;

        /**
         * The list of metric identifiers to fetch values for.
         *
         * @implNote WAWebMexFetchNewsletterInsightsJob.mexFetchNewsletterInsights: mirrors the
         * {@code metrics} field of the GraphQL {@code input} variable
         * destructured from {@code e.requestedMetrics}.
         */
        @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterInsightsJob", exports = "mexFetchNewsletterInsights",
                adaptation = WhatsAppAdaptation.DIRECT)
        private final List<String> metrics;

        /**
         * Creates a request fetching the supplied metrics for the given newsletter.
         *
         * @implNote WAWebMexFetchNewsletterInsightsJob.mexFetchNewsletterInsights: mirrors the
         * {@code {newsletterJid, requestedMetrics}} object destructured at the
         * head of the JS function.
         * @param newsletterId the newsletter identifier passed as
         *                     {@code newsletter_id}
         * @param metrics      the list of metric identifiers passed as
         *                     {@code metrics}; may be {@code null}
         */
        @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterInsightsJob", exports = "mexFetchNewsletterInsights",
                adaptation = WhatsAppAdaptation.DIRECT)
        public Request(String newsletterId, List<String> metrics) {
            this.newsletterId = newsletterId;
            this.metrics = metrics;
        }

        /**
         * Builds the IQ stanza that dispatches this operation to the
         * WhatsApp relay.
         *
         * @implNote WAWebMexFetchNewsletterInsightsJob.mexFetchNewsletterInsights: WA Web constructs
         * {@code {input:{newsletter_id:l, metrics:u}}} inline and delegates to
         * {@code WAWebMexClient.fetchQuery}. Cobalt writes the same nested
         * variables envelope directly via {@code fastjson2.JSONWriter} and
         * wraps it through {@link MexJsonOperation#createMexNode(String, String)}.
         * @return a {@link NodeBuilder} carrying the IQ envelope and the
         *         serialised GraphQL variables
         */
        @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterInsightsJob", exports = "mexFetchNewsletterInsights",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public NodeBuilder toNode() {
            // WAWebMexFetchNewsletterInsightsJob.mexFetchNewsletterInsights
            // Opens a UTF-8 JSON writer that will serialise the GraphQL variables envelope
            try (var writer = JSONWriter.ofUTF8()) {
                // WAWebMexFetchNewsletterInsightsJob.mexFetchNewsletterInsights
                // Begins the outer envelope and the nested "variables" object consumed by WAWebMexClient.fetchQuery
                writer.startObject();
                writer.writeName("variables");
                writer.writeColon();
                writer.startObject();

                // WAWebMexFetchNewsletterInsightsJob.mexFetchNewsletterInsights
                // {input:{newsletter_id:l, metrics:u}} - opens the nested input object
                writer.writeName("input");
                writer.writeColon();
                writer.startObject();

                // WAWebMexFetchNewsletterInsightsJob.mexFetchNewsletterInsights
                // input.newsletter_id = l (e.newsletterJid)
                if (newsletterId != null) {
                    writer.writeName("newsletter_id");
                    writer.writeColon();
                    writer.writeString(newsletterId);
                }

                // WAWebMexFetchNewsletterInsightsJob.mexFetchNewsletterInsights
                // input.metrics = u (e.requestedMetrics) - serialised as a JSON array of strings
                if (metrics != null) {
                    writer.writeName("metrics");
                    writer.writeColon();
                    writer.startArray();
                    for (var i = 0; i < metrics.size(); i++) {
                        if (i > 0) {
                            writer.writeComma();
                        }
                        writer.writeString(metrics.get(i));
                    }
                    writer.endArray();
                }

                writer.endObject();
                writer.endObject();
                writer.endObject();

                // ADAPTED: WAWebMexFetchNewsletterInsightsJob.mexFetchNewsletterInsights
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
     * The response variant of {@link FetchNewsletterInsightsMex} that exposes the data
     * returned by the server after a successful query.
     *
     * @implNote WAWebMexFetchNewsletterInsightsJob: adapts the JSON root returned by the GraphQL
     * query into a Java value object.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterInsightsJob")
    final class Response implements FetchNewsletterInsightsMex {
        private final String newsletterId;
        private final State state;
        private final Long lastUpdateTime;
        private final String metricsStatus;
        private final List<Result> result;

        private Response(String newsletterId, State state, Long lastUpdateTime, String metricsStatus, List<Result> result) {
            this.newsletterId = newsletterId;
            this.state = state;
            this.lastUpdateTime = lastUpdateTime;
            this.metricsStatus = metricsStatus;
            this.result = result;
        }

        /**
         * Parses a MEX response from the given IQ response node.
         *
         * @implNote WAWebMexFetchNewsletterInsightsJob.mexFetchNewsletterInsights: WA Web relies on the
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
         * Returns the {@code newsletter_id} field.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> newsletterId() {
            return Optional.ofNullable(newsletterId);
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
         * Returns the {@code last_update_time} field.
         *
         * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
         */
        public Optional<Instant> lastUpdateTime() {
            return Optional.ofNullable(lastUpdateTime).map(Instant::ofEpochSecond);
        }

        /**
         * Returns the {@code metrics_status} field.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> metricsStatus() {
            return Optional.ofNullable(metricsStatus);
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
         * A parsed {@code Result} object.
         */
        public static final class Result {
            private final String id;
            private final List<Values> values;

            private Result(String id, List<Values> values) {
                this.id = id;
                this.values = values;
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
             * Returns the {@code values} field.
             *
             * @return the list of values, empty if absent
             */
            public List<Values> values() {
                return values;
            }

            /**
             * A parsed {@code Values} object.
             */
            public static final class Values {
                private final String value;
                private final String country;
                private final String role;
                private final Long timestamp;

                private Values(String value, String country, String role, Long timestamp) {
                    this.value = value;
                    this.country = country;
                    this.role = role;
                    this.timestamp = timestamp;
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
                 * Returns the {@code country} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> country() {
                    return Optional.ofNullable(country);
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
                 * Returns the {@code timestamp} field.
                 *
                 * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
                 */
                public Optional<Instant> timestamp() {
                    return Optional.ofNullable(timestamp).map(Instant::ofEpochSecond);
                }

                /**
                 * Parses a {@code Values} from the given JSON object.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                 */
                static Optional<Values> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var value = obj.getString("value");
                    var country = obj.getString("country");
                    var role = obj.getString("role");
                    var timestamp = obj.getLong("timestamp");
                    return Optional.of(new Values(value, country, role, timestamp));
                }

                /**
                 * Parses a list of {@code Values} from the given JSON array.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed results, empty if {@code arr} is {@code null}
                 */
                static List<Values> ofArray(JSONArray arr) {
                    if (arr == null) {
                        return List.of();
                    }

                    var result = new ArrayList<Values>(arr.size());
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
                var values = Values.ofArray(obj.getJSONArray("values"));
                return Optional.of(new Result(id, values));
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
         * @implNote WAWebMexFetchNewsletterInsightsJob.mexFetchNewsletterInsights: mirrors the implicit
         * unwrapping that WA Web performs on the GraphQL response,
         * extracting the {@code xwa2_newsletter_admin_insights} root.
         * @param json the UTF-8 encoded JSON payload
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the envelope is missing expected fields
         */
        private static Optional<Response> of(byte[] json) {
            // WAWebMexFetchNewsletterInsightsJob.mexFetchNewsletterInsights
            // Parses the raw JSON payload, bailing out if fastjson2 returns null
            var jsonObject = JSON.parseObject(json);
            if (jsonObject == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterInsightsJob.mexFetchNewsletterInsights
            // Descends into the standard GraphQL "data" envelope
            var data = jsonObject.getJSONObject("data");
            if (data == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterInsightsJob.mexFetchNewsletterInsights
            // Extracts the operation-specific root keyed by xwa2_newsletter_admin_insights
            var root = data.getJSONObject("xwa2_newsletter_admin_insights");
            if (root == null) {
                return Optional.empty();
            }

            var newsletterId = root.getString("newsletter_id");
            var state = State.of(root.getJSONObject("state")).orElse(null);
            var lastUpdateTime = root.getLong("last_update_time");
            var metricsStatus = root.getString("metrics_status");
            var result = Result.ofArray(root.getJSONArray("result"));

            return Optional.of(new Response(newsletterId, state, lastUpdateTime, metricsStatus, result));
        }
    }
}
