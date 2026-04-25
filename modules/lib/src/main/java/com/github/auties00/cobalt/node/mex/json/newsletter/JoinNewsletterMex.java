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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Joins (subscribes to) the given newsletter as the authenticated user.
 *
 * <p>After a successful join the user starts receiving the newsletter's updates and the newsletter appears in the user's channel list. The mutation returns fresh thread metadata so the client can cache the newsletter locally.
 *
 * @implNote WAWebMexJoinNewsletterJob: adapts the {@code mexJoinNewsletter} GraphQL mutation,
 * which in WA Web is invoked via {@code WAWebMexClient.fetchQuery} and
 * whose response is unwrapped by the same module. Cobalt models the request
 * and response as sibling variants of a sealed interface rather than a
 * free-standing async function.
 */
@WhatsAppWebModule(moduleName = "WAWebMexJoinNewsletterJob")
public sealed interface JoinNewsletterMex extends MexJsonOperation permits JoinNewsletterMex.Request, JoinNewsletterMex.Response {
    /**
     * The numeric GraphQL query identifier assigned by the WhatsApp relay
     * to the {@code JoinNewsletter} compiled mutation.
     *
     * @implNote WAWebMexJoinNewsletterJobMutation.graphql: corresponds to the compiled
     * document id registered for the {@code mexJoinNewsletter} mutation.
     */
    String QUERY_ID = "24404358912487870";

    /**
     * The request variant of {@link JoinNewsletterMex} that serialises the
     * mutation variables and emits the outbound IQ stanza.
     *
     * @implNote WAWebMexJoinNewsletterJob.mexJoinNewsletter: adapts the {@code variables}
     * object constructed inline in the JS implementation into a dedicated
     * Java class.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexJoinNewsletterJob")
    final class Request implements JoinNewsletterMex {
        private final String newsletterId;

        public Request(String newsletterId) {
            this.newsletterId = newsletterId;
        }

        /**
         * Builds the IQ stanza that dispatches this operation to the
         * WhatsApp relay.
         *
         * @implNote WAWebMexJoinNewsletterJob.mexJoinNewsletter: WA Web constructs the
         * {@code variables} object inline and delegates to
         * {@code WAWebMexClient.fetchQuery}. Cobalt writes the JSON directly
         * via {@code fastjson2.JSONWriter} and wraps it through
         * {@link MexJsonOperation#createMexNode(String, String)}.
         * @return a {@link NodeBuilder} carrying the IQ envelope and the
         *         serialised GraphQL variables
         */
        @WhatsAppWebExport(moduleName = "WAWebMexJoinNewsletterJob", exports = "mexJoinNewsletter",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public NodeBuilder toNode() {
            // WAWebMexJoinNewsletterJob.mexJoinNewsletter
            // Opens a UTF-8 JSON writer that will serialise the GraphQL variables envelope
            try (var writer = JSONWriter.ofUTF8()) {
                // WAWebMexJoinNewsletterJob.mexJoinNewsletter
                // Begins the outer envelope and the nested "variables" object consumed by WAWebMexClient.fetchQuery
                writer.startObject();
                writer.writeName("variables");
                writer.writeColon();
                writer.startObject();
                // WAWebMexJoinNewsletterJob.mexJoinNewsletter
                // Emits the newsletter_id variable when present
                if (newsletterId != null) {
                    writer.writeName("newsletter_id");
                    writer.writeColon();
                    writer.writeString(newsletterId);
                }
                writer.endObject();
                writer.endObject();

                // ADAPTED: WAWebMexJoinNewsletterJob.mexJoinNewsletter
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
     * The response variant of {@link JoinNewsletterMex} that exposes the data
     * returned by the server after a successful mutation.
     *
     * @implNote WAWebMexJoinNewsletterJob: adapts the JSON root returned by the GraphQL
     * mutation into a Java value object.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexJoinNewsletterJob")
    final class Response implements JoinNewsletterMex {
        private final String id;
        private final State state;

        private Response(String id, State state) {
            this.id = id;
            this.state = state;
        }

        /**
         * Parses a MEX response from the given IQ response node.
         *
         * @implNote WAWebMexJoinNewsletterJob.mexJoinNewsletter: WA Web relies on the
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
         * Parses a {@link Response} from the raw JSON bytes of the
         * {@code <result>} child.
         *
         * @implNote WAWebMexJoinNewsletterJob.mexJoinNewsletter: mirrors the implicit
         * unwrapping that WA Web performs on the GraphQL response,
         * extracting the {@code xwa2_newsletter_join_v2} root.
         * @param json the UTF-8 encoded JSON payload
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the envelope is missing expected fields
         */
        private static Optional<Response> of(byte[] json) {
            // WAWebMexJoinNewsletterJob.mexJoinNewsletter
            // Parses the raw JSON payload, bailing out if fastjson2 returns null
            var jsonObject = JSON.parseObject(json);
            if (jsonObject == null) {
                return Optional.empty();
            }

            // WAWebMexJoinNewsletterJob.mexJoinNewsletter
            // Descends into the standard GraphQL "data" envelope
            var data = jsonObject.getJSONObject("data");
            if (data == null) {
                return Optional.empty();
            }

            // WAWebMexJoinNewsletterJob.mexJoinNewsletter
            // Extracts the operation-specific root keyed by xwa2_newsletter_join_v2
            var root = data.getJSONObject("xwa2_newsletter_join_v2");
            if (root == null) {
                return Optional.empty();
            }

            var id = root.getString("id");
            var state = State.of(root.getJSONObject("state")).orElse(null);

            return Optional.of(new Response(id, state));
        }
    }
}
