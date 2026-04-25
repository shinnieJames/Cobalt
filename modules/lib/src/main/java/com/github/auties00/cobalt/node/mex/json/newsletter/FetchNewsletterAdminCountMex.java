package com.github.auties00.cobalt.node.mex.json.newsletter;

import com.alibaba.fastjson2.JSON;
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
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Fetches the number of administrators on a newsletter.
 *
 * <p>This query reads the same {@code xwa2_newsletter_admin} root used by
 * {@code WAWebMexFetchNewsletterAdminInfoJob} but only exposes the
 * {@code admin_count} scalar field, which is enough to display an admin
 * headcount without loading full admin profile information.
 *
 * @implNote WAWebMexFetchNewsletterAdminInfoJob: adapts the
 * {@code mexFetchNewsletterAdminInfo} GraphQL query, narrowing the Cobalt
 * response to only the {@code admin_count} scalar while WA Web also hydrates
 * the {@code admin_profile} sub-object. Cobalt dispatches the same
 * {@code newsletter_id} variable through the shared MEX IQ pipeline.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterAdminInfoJob")
public sealed interface FetchNewsletterAdminCountMex extends MexJsonOperation permits FetchNewsletterAdminCountMex.Request, FetchNewsletterAdminCountMex.Response {
    /**
     * The numeric GraphQL query identifier assigned by the WhatsApp relay
     * to the {@code FetchNewsletterAdminCount} compiled query.
     *
     * @implNote WAWebMexFetchNewsletterAdminInfoJobQuery.graphql: corresponds to the compiled
     * document id registered for the {@code mexFetchNewsletterAdminInfo} query.
     */
    String QUERY_ID = "34983385154639574";

    /**
     * The request variant of {@link FetchNewsletterAdminCountMex} that serialises the
     * query variables and emits the outbound IQ stanza.
     *
     * @implNote WAWebMexFetchNewsletterAdminInfoJob.mexFetchNewsletterAdminInfo: adapts the {@code variables}
     * object constructed inline in the JS implementation into a dedicated
     * Java class.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterAdminInfoJob")
    final class Request implements FetchNewsletterAdminCountMex {
        private final String newsletterId;

        public Request(String newsletterId) {
            this.newsletterId = newsletterId;
        }

        /**
         * Builds the IQ stanza that dispatches this operation to the
         * WhatsApp relay.
         *
         * @implNote WAWebMexFetchNewsletterAdminInfoJob.mexFetchNewsletterAdminInfo: WA Web constructs the
         * {@code variables} object inline and delegates to
         * {@code WAWebMexClient.fetchQuery}. Cobalt writes the JSON directly
         * via {@code fastjson2.JSONWriter} and wraps it through
         * {@link MexJsonOperation#createMexNode(String, String)}.
         * @return a {@link NodeBuilder} carrying the IQ envelope and the
         *         serialised GraphQL variables
         */
        @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterAdminInfoJob", exports = "mexFetchNewsletterAdminInfo",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public NodeBuilder toNode() {
            // WAWebMexFetchNewsletterAdminInfoJob.mexFetchNewsletterAdminInfo
            // Opens a UTF-8 JSON writer that will serialise the GraphQL variables envelope
            try (var writer = JSONWriter.ofUTF8()) {
                // WAWebMexFetchNewsletterAdminInfoJob.mexFetchNewsletterAdminInfo
                // Begins the outer envelope and the nested "variables" object consumed by WAWebMexClient.fetchQuery
                writer.startObject();
                writer.writeName("variables");
                writer.writeColon();
                writer.startObject();
                // WAWebMexFetchNewsletterAdminInfoJob.mexFetchNewsletterAdminInfo
                // Emits the newsletter_id variable when present
                if (newsletterId != null) {
                    writer.writeName("newsletter_id");
                    writer.writeColon();
                    writer.writeString(newsletterId);
                }
                writer.endObject();
                writer.endObject();

                // ADAPTED: WAWebMexFetchNewsletterAdminInfoJob.mexFetchNewsletterAdminInfo
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
     * The response variant of {@link FetchNewsletterAdminCountMex} that exposes the data
     * returned by the server after a successful query.
     *
     * @implNote WAWebMexFetchNewsletterAdminInfoJob: adapts the JSON root returned by the GraphQL
     * query into a Java value object.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterAdminInfoJob")
    final class Response implements FetchNewsletterAdminCountMex {
        private final Long adminCount;
        private final String id;

        private Response(Long adminCount, String id) {
            this.adminCount = adminCount;
            this.id = id;
        }

        /**
         * Parses a MEX response from the given IQ response node.
         *
         * @implNote WAWebMexFetchNewsletterAdminInfoJob.mexFetchNewsletterAdminInfo: WA Web relies on the
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
         * Returns the {@code admin_count} field.
         *
         * @return an {@link OptionalLong} containing the value, or empty if absent
         */
        public OptionalLong adminCount() {
            return adminCount != null ? OptionalLong.of(adminCount) : OptionalLong.empty();
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
         * Parses a {@link Response} from the raw JSON bytes of the
         * {@code <result>} child.
         *
         * @implNote WAWebMexFetchNewsletterAdminInfoJob.mexFetchNewsletterAdminInfo: mirrors the implicit
         * unwrapping that WA Web performs on the GraphQL response,
         * extracting the {@code xwa2_newsletter_admin} root.
         * @param json the UTF-8 encoded JSON payload
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the envelope is missing expected fields
         */
        private static Optional<Response> of(byte[] json) {
            // WAWebMexFetchNewsletterAdminInfoJob.mexFetchNewsletterAdminInfo
            // Parses the raw JSON payload, bailing out if fastjson2 returns null
            var jsonObject = JSON.parseObject(json);
            if (jsonObject == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterAdminInfoJob.mexFetchNewsletterAdminInfo
            // Descends into the standard GraphQL "data" envelope
            var data = jsonObject.getJSONObject("data");
            if (data == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterAdminInfoJob.mexFetchNewsletterAdminInfo
            // Extracts the operation-specific root keyed by xwa2_newsletter_admin
            var root = data.getJSONObject("xwa2_newsletter_admin");
            if (root == null) {
                return Optional.empty();
            }

            var adminCount = root.getLong("admin_count");
            var id = root.getString("id");

            return Optional.of(new Response(adminCount, id));
        }
    }
}
