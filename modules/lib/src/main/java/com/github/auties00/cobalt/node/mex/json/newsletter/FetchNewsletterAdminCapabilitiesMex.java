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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fetches the capability flags granted to the authenticated user as newsletter admin.
 *
 * <p>The capability map tells the client which privileged operations the user may perform on a given newsletter, such as publishing, moderating or inviting other admins.
 *
 * @implNote WAWebMexFetchNewsletterAdminCapabilitiesJob: adapts the {@code mexFetchNewsletterAdminCapabilities} GraphQL query,
 * which in WA Web is invoked via {@code WAWebMexClient.fetchQuery} and
 * whose response is unwrapped by the same module. Cobalt models the request
 * and response as sibling variants of a sealed interface rather than a
 * free-standing async function.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterAdminCapabilitiesJob")
public sealed interface FetchNewsletterAdminCapabilitiesMex extends MexJsonOperation permits FetchNewsletterAdminCapabilitiesMex.Request, FetchNewsletterAdminCapabilitiesMex.Response {
    /**
     * The numeric GraphQL query identifier assigned by the WhatsApp relay
     * to the {@code FetchNewsletterAdminCapabilities} compiled query.
     *
     * @implNote WAWebMexFetchNewsletterAdminCapabilitiesJobQuery.graphql: corresponds to the compiled
     * document id registered for the {@code mexFetchNewsletterAdminCapabilities} query.
     */
    String QUERY_ID = "9801384413216421";

    /**
     * The request variant of {@link FetchNewsletterAdminCapabilitiesMex} that serialises the
     * query variables and emits the outbound IQ stanza.
     *
     * @implNote WAWebMexFetchNewsletterAdminCapabilitiesJob.mexFetchNewsletterAdminCapabilities: adapts the {@code variables}
     * object constructed inline in the JS implementation into a dedicated
     * Java class.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterAdminCapabilitiesJob")
    final class Request implements FetchNewsletterAdminCapabilitiesMex {
        private final String newsletterId;

        public Request(String newsletterId) {
            this.newsletterId = newsletterId;
        }

        /**
         * Builds the IQ stanza that dispatches this operation to the
         * WhatsApp relay.
         *
         * @implNote WAWebMexFetchNewsletterAdminCapabilitiesJob.mexFetchNewsletterAdminCapabilities: WA Web constructs the
         * {@code variables} object inline and delegates to
         * {@code WAWebMexClient.fetchQuery}. Cobalt writes the JSON directly
         * via {@code fastjson2.JSONWriter} and wraps it through
         * {@link MexJsonOperation#createMexNode(String, String)}.
         * @return a {@link NodeBuilder} carrying the IQ envelope and the
         *         serialised GraphQL variables
         */
        @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterAdminCapabilitiesJob", exports = "mexFetchNewsletterAdminCapabilities",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public NodeBuilder toNode() {
            // WAWebMexFetchNewsletterAdminCapabilitiesJob.mexFetchNewsletterAdminCapabilities
            // Opens a UTF-8 JSON writer that will serialise the GraphQL variables envelope
            try (var writer = JSONWriter.ofUTF8()) {
                // WAWebMexFetchNewsletterAdminCapabilitiesJob.mexFetchNewsletterAdminCapabilities
                // Begins the outer envelope and the nested "variables" object consumed by WAWebMexClient.fetchQuery
                writer.startObject();
                writer.writeName("variables");
                writer.writeColon();
                writer.startObject();
                // WAWebMexFetchNewsletterAdminCapabilitiesJob.mexFetchNewsletterAdminCapabilities
                // Emits the newsletter_id variable when present
                if (newsletterId != null) {
                    writer.writeName("newsletter_id");
                    writer.writeColon();
                    writer.writeString(newsletterId);
                }
                writer.endObject();
                writer.endObject();

                // ADAPTED: WAWebMexFetchNewsletterAdminCapabilitiesJob.mexFetchNewsletterAdminCapabilities
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
     * The response variant of {@link FetchNewsletterAdminCapabilitiesMex} that exposes the data
     * returned by the server after a successful query.
     *
     * @implNote WAWebMexFetchNewsletterAdminCapabilitiesJob: adapts the JSON root returned by the GraphQL
     * query into a Java value object.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterAdminCapabilitiesJob")
    final class Response implements FetchNewsletterAdminCapabilitiesMex {
        private final List<String> capabilities;

        private Response(List<String> capabilities) {
            this.capabilities = capabilities;
        }

        /**
         * Parses a MEX response from the given IQ response node.
         *
         * @implNote WAWebMexFetchNewsletterAdminCapabilitiesJob.mexFetchNewsletterAdminCapabilities: WA Web relies on the
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
         * Returns the raw newsletter capability values granted to the
         * authenticated admin.
         *
         * @implNote WAWebMexFetchNewsletterAdminCapabilitiesJob.mexFetchNewsletterAdminCapabilities: WA Web reads
         * {@code r.xwa2_newsletter_admin?.capabilities} and maps each entry
         * via {@code WAWebNewsletterModelUtils.getNewsletterCapabilityFromValue}
         * before wrapping the result in a {@code Set}. Cobalt returns the
         * raw string values; the enum mapping lives in the newsletter model
         * utilities.
         * @return an unmodifiable {@link List} of capability identifiers; never
         *         {@code null} but possibly empty
         */
        public List<String> capabilities() {
            return capabilities;
        }

        /**
         * Parses a {@link Response} from the raw JSON bytes of the
         * {@code <result>} child.
         *
         * @implNote WAWebMexFetchNewsletterAdminCapabilitiesJob.mexFetchNewsletterAdminCapabilities: mirrors the implicit
         * unwrapping that WA Web performs on the GraphQL response,
         * extracting the {@code xwa2_newsletter_admin} root.
         * @param json the UTF-8 encoded JSON payload
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the envelope is missing expected fields
         */
        private static Optional<Response> of(byte[] json) {
            // WAWebMexFetchNewsletterAdminCapabilitiesJob.mexFetchNewsletterAdminCapabilities
            // Parses the raw JSON payload, bailing out if fastjson2 returns null
            var jsonObject = JSON.parseObject(json);
            if (jsonObject == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterAdminCapabilitiesJob.mexFetchNewsletterAdminCapabilities
            // Descends into the standard GraphQL "data" envelope
            var data = jsonObject.getJSONObject("data");
            if (data == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterAdminCapabilitiesJob.mexFetchNewsletterAdminCapabilities
            // Extracts the operation-specific root keyed by xwa2_newsletter_admin (r.xwa2_newsletter_admin)
            var root = data.getJSONObject("xwa2_newsletter_admin");
            if (root == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterAdminCapabilitiesJob.mexFetchNewsletterAdminCapabilities
            // var a = r.xwa2_newsletter_admin?.capabilities; var i = a==null ? [] : a.map(...)
            var capabilitiesArray = root.getJSONArray("capabilities");
            var capabilities = new ArrayList<String>();
            if (capabilitiesArray != null) {
                for (var i = 0; i < capabilitiesArray.size(); i++) {
                    var value = capabilitiesArray.getString(i);
                    if (value != null) {
                        capabilities.add(value);
                    }
                }
            }

            return Optional.of(new Response(List.copyOf(capabilities)));
        }
    }
}
