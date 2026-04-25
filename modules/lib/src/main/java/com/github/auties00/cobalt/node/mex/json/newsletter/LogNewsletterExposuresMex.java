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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Logs a batch of newsletter exposure events for attribution and ranking.
 *
 * <p>When a user browses newsletters the client records lightweight exposure events which are later flushed to the server via this mutation. The backend uses the exposure signal to improve directory ranking.
 *
 * @implNote WAWebMexLogNewsletterExposuresJob: adapts the {@code mexLogNewsletterExposures} GraphQL mutation,
 * which in WA Web is invoked via {@code WAWebMexClient.fetchQuery} and
 * whose response is unwrapped by the same module. Cobalt models the request
 * and response as sibling variants of a sealed interface rather than a
 * free-standing async function.
 */
@WhatsAppWebModule(moduleName = "WAWebMexLogNewsletterExposuresJob")
public sealed interface LogNewsletterExposuresMex extends MexJsonOperation permits LogNewsletterExposuresMex.Request, LogNewsletterExposuresMex.Response {
    /**
     * The numeric GraphQL query identifier assigned by the WhatsApp relay
     * to the {@code LogNewsletterExposures} compiled mutation.
     *
     * @implNote WAWebMexLogNewsletterExposuresJobMutation.graphql: corresponds to the compiled
     * document id registered for the {@code mexLogNewsletterExposures} mutation,
     * extracted from the {@code params.id} field of the compiled GraphQL artifact.
     */
    String QUERY_ID = "25260800823586918";

    /**
     * A single exposure entry that pairs a newsletter id with the capability
     * surface on which the exposure occurred.
     *
     * @implNote WAWebMexLogNewsletterExposuresJob.mexLogNewsletterExposures: adapts each element of the
     * {@code e} array argument: {@code e.map(function(e){var t=e.capability,
     * n=e.newsletterJid; return {newsletter_id:n, capability:
     * WAWebNewsletterQueryUtils.getNewsletterCapabilityFromEnum(t)}})}.
     * Cobalt expects the caller to pre-resolve the capability into its raw
     * server-side string token (e.g. {@code "INSIGHTS"},
     * {@code "PHOTO_POLLS"}, ...), mirroring the result of
     * {@code getNewsletterCapabilityFromEnum}.
     * @param newsletterId the newsletter JID serialised as a string, exactly
     *                     as it appears on the wire under the
     *                     {@code newsletter_id} key
     * @param capability   the resolved server-side capability token, exactly
     *                     as it appears on the wire under the
     *                     {@code capability} key
     */
    record Exposure(String newsletterId, String capability) {
        /**
         * Constructs an {@link Exposure} after validating both fields are
         * non-{@code null}.
         *
         * @implNote WAWebMexLogNewsletterExposuresJob.mexLogNewsletterExposures: WA Web does not validate
         * the inputs before serialising them, but Cobalt rejects {@code null}
         * values eagerly because they would be silently dropped by
         * {@code fastjson2} and produce a malformed envelope.
         * @param newsletterId the newsletter JID; must not be {@code null}
         * @param capability   the resolved capability token; must not be
         *                     {@code null}
         * @throws NullPointerException if either parameter is {@code null}
         */
        public Exposure {
            Objects.requireNonNull(newsletterId, "newsletterId cannot be null");
            Objects.requireNonNull(capability, "capability cannot be null");
        }
    }

    /**
     * The request variant of {@link LogNewsletterExposuresMex} that serialises the
     * mutation variables and emits the outbound IQ stanza.
     *
     * @implNote WAWebMexLogNewsletterExposuresJob.mexLogNewsletterExposures: adapts the {@code variables}
     * object constructed inline in the JS implementation into a dedicated
     * Java class. WA Web builds {@code {input:{exposures:[...]}}}: Cobalt
     * mirrors the same nested envelope rather than accepting a flat string.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexLogNewsletterExposuresJob")
    final class Request implements LogNewsletterExposuresMex {
        private final List<Exposure> exposures;

        /**
         * Constructs a {@link Request} from a batch of exposure entries.
         *
         * @implNote WAWebMexLogNewsletterExposuresJob.mexLogNewsletterExposures: WA Web's exported
         * function takes the {@code e} array directly and maps each entry
         * into a {@code {newsletter_id, capability}} object. Cobalt accepts
         * the pre-mapped {@link Exposure} records so the call site can
         * resolve the capability enum once before queuing the request.
         * @param exposures the batch of exposure entries; must not be
         *                  {@code null}, but may be empty
         * @throws NullPointerException if {@code exposures} is {@code null}
         */
        public Request(List<Exposure> exposures) {
            Objects.requireNonNull(exposures, "exposures cannot be null");
            this.exposures = List.copyOf(exposures);
        }

        /**
         * Builds the IQ stanza that dispatches this operation to the
         * WhatsApp relay.
         *
         * @implNote WAWebMexLogNewsletterExposuresJob.mexLogNewsletterExposures: WA Web constructs the
         * {@code variables} object inline as
         * {@code {input:{exposures: e.map(({capability,newsletterJid}) =>
         * ({newsletter_id:newsletterJid, capability:
         * getNewsletterCapabilityFromEnum(capability)}))}}} and delegates to
         * {@code WAWebMexClient.fetchQuery}. Cobalt writes the same nested
         * JSON envelope directly via {@code fastjson2.JSONWriter} and wraps
         * the result through
         * {@link MexJsonOperation#createMexNode(String, String)}.
         * @return a {@link NodeBuilder} carrying the IQ envelope and the
         *         serialised GraphQL variables
         */
        @WhatsAppWebExport(moduleName = "WAWebMexLogNewsletterExposuresJob", exports = "mexLogNewsletterExposures",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public NodeBuilder toNode() {
            // WAWebMexLogNewsletterExposuresJob.mexLogNewsletterExposures
            // Opens a UTF-8 JSON writer that will serialise the GraphQL variables envelope
            try (var writer = JSONWriter.ofUTF8()) {
                // WAWebMexLogNewsletterExposuresJob.mexLogNewsletterExposures
                // Begins the outer envelope and the nested "variables" object consumed by WAWebMexClient.fetchQuery
                writer.startObject();
                writer.writeName("variables");
                writer.writeColon();
                writer.startObject();

                // WAWebMexLogNewsletterExposuresJob.mexLogNewsletterExposures
                // var t={input:{exposures: e.map(...)}}
                writer.writeName("input");
                writer.writeColon();
                writer.startObject();

                // WAWebMexLogNewsletterExposuresJob.mexLogNewsletterExposures
                // exposures: e.map(function(e){var t=e.capability,n=e.newsletterJid;
                //                              return {newsletter_id:n, capability:getNewsletterCapabilityFromEnum(t)}})
                writer.writeName("exposures");
                writer.writeColon();
                writer.startArray();
                for (var i = 0; i < exposures.size(); i++) {
                    if (i > 0) {
                        writer.writeComma();
                    }
                    var exposure = exposures.get(i);
                    writer.startObject();
                    writer.writeName("newsletter_id");
                    writer.writeColon();
                    writer.writeString(exposure.newsletterId());
                    writer.writeName("capability");
                    writer.writeColon();
                    writer.writeString(exposure.capability());
                    writer.endObject();
                }
                writer.endArray();

                writer.endObject();
                writer.endObject();
                writer.endObject();

                // ADAPTED: WAWebMexLogNewsletterExposuresJob.mexLogNewsletterExposures
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
     * The response variant of {@link LogNewsletterExposuresMex} that exposes the data
     * returned by the server after a successful mutation.
     *
     * @implNote WAWebMexLogNewsletterExposuresJob: adapts the JSON root returned by the GraphQL
     * mutation into a Java value object. The compiled GraphQL artifact only
     * selects {@code __typename} on the {@code xwa2_newsletter_log_exposures}
     * field, so success is signalled by the mere presence of that root.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexLogNewsletterExposuresJob")
    final class Response implements LogNewsletterExposuresMex {

        /**
         * Constructs an empty {@link Response}.
         *
         * @implNote WAWebMexLogNewsletterExposuresJob.mexLogNewsletterExposures: WA Web ignores the
         * response payload entirely (the function does not even {@code return}
         * the awaited value). Cobalt still exposes a {@link Response}
         * placeholder so callers can pattern-match on the sealed hierarchy.
         */
        private Response() {
        }

        /**
         * Parses a MEX response from the given IQ response node.
         *
         * @implNote WAWebMexLogNewsletterExposuresJob.mexLogNewsletterExposures: WA Web relies on the
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
         * Parses a {@link Response} from the raw JSON bytes of the
         * {@code <result>} child.
         *
         * @implNote WAWebMexLogNewsletterExposuresJob.mexLogNewsletterExposures: mirrors the implicit
         * unwrapping that WA Web performs on the GraphQL response,
         * extracting the {@code xwa2_newsletter_log_exposures} root which the
         * compiled GraphQL artifact populates on success with a single
         * {@code __typename} selection.
         * @param json the UTF-8 encoded JSON payload
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the envelope is missing expected fields
         */
        private static Optional<Response> of(byte[] json) {
            // WAWebMexLogNewsletterExposuresJob.mexLogNewsletterExposures
            // Parses the raw JSON payload, bailing out if fastjson2 returns null
            var jsonObject = JSON.parseObject(json);
            if (jsonObject == null) {
                return Optional.empty();
            }

            // WAWebMexLogNewsletterExposuresJob.mexLogNewsletterExposures
            // Descends into the standard GraphQL "data" envelope
            var data = jsonObject.getJSONObject("data");
            if (data == null) {
                return Optional.empty();
            }

            // WAWebMexLogNewsletterExposuresJob.mexLogNewsletterExposures
            // Probes for the xwa2_newsletter_log_exposures marker so callers can confirm the batch was accepted
            var root = data.get("xwa2_newsletter_log_exposures");
            if (root == null) {
                return Optional.empty();
            }

            return Optional.of(new Response());
        }
    }
}
