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
 * Checks whether a given list of domains is previewable inside newsletter messages.
 *
 * <p>The WhatsApp backend maintains a list of allowed domains whose link previews may be rendered inside newsletter messages. This query validates one or more URL domains before publishing.
 *
 * @implNote WAWebMexFetchNewsletterIsDomainPreviewableJob: adapts the {@code mexFetchNewsletterIsDomainPreviewable} GraphQL query,
 * which in WA Web is invoked via {@code WAWebMexClient.fetchQuery} and
 * whose response is unwrapped by the same module. Cobalt models the request
 * and response as sibling variants of a sealed interface rather than a
 * free-standing async function.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterIsDomainPreviewableJob")
public sealed interface FetchNewsletterIsDomainPreviewableMex extends MexJsonOperation permits FetchNewsletterIsDomainPreviewableMex.Request, FetchNewsletterIsDomainPreviewableMex.Response {
    /**
     * The numeric GraphQL query identifier assigned by the WhatsApp relay
     * to the {@code FetchNewsletterIsDomainPreviewable} compiled query.
     *
     * @implNote WAWebMexFetchNewsletterIsDomainPreviewableJobQuery.graphql: corresponds to the compiled
     * document id registered for the {@code mexFetchNewsletterIsDomainPreviewable} query
     * (see {@code params.id} in the generated relay descriptor).
     */
    String QUERY_ID = "9849510985088294";

    /**
     * The request variant of {@link FetchNewsletterIsDomainPreviewableMex} that serialises the
     * query variables and emits the outbound IQ stanza.
     *
     * @implNote WAWebMexFetchNewsletterIsDomainPreviewableJob.mexFetchNewsletterIsDomainPreviewable: adapts the {@code variables}
     * object constructed inline in the JS implementation ({@code r = {url_domains: e}}) into a dedicated
     * Java class. The JS function accepts the {@code url_domains} parameter as a list of strings (see
     * {@code WAWebNewsletterIsDomainPreviewableAction} which calls {@code mexFetchNewsletterIsDomainPreviewable([t])}
     * and the GraphQL relay descriptor which defines {@code url_domains} as an input field of an
     * {@code XWA2NewsletterMessageIntegrityInput} ObjectValue). Cobalt therefore models the variable as a
     * {@link List} of strings and serialises it as a JSON array.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterIsDomainPreviewableJob")
    final class Request implements FetchNewsletterIsDomainPreviewableMex {
        private final List<String> urlDomains;

        /**
         * Creates a new request variant carrying the given list of URL domains.
         *
         * @param urlDomains the URL domains to validate; may be {@code null} or empty
         */
        public Request(List<String> urlDomains) {
            this.urlDomains = urlDomains;
        }

        /**
         * Builds the IQ stanza that dispatches this operation to the
         * WhatsApp relay.
         *
         * @implNote WAWebMexFetchNewsletterIsDomainPreviewableJob.mexFetchNewsletterIsDomainPreviewable: WA Web constructs the
         * {@code variables} object inline as {@code {url_domains: e}} where
         * {@code e} is a string array, and delegates to
         * {@code WAWebMexClient.fetchQuery}. Cobalt writes the JSON directly
         * via {@code fastjson2.JSONWriter} and wraps it through
         * {@link MexJsonOperation#createMexNode(String, String)}.
         * @return a {@link NodeBuilder} carrying the IQ envelope and the
         *         serialised GraphQL variables
         */
        @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterIsDomainPreviewableJob", exports = "mexFetchNewsletterIsDomainPreviewable",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public NodeBuilder toNode() {
            // WAWebMexFetchNewsletterIsDomainPreviewableJob.mexFetchNewsletterIsDomainPreviewable
            // Opens a UTF-8 JSON writer that will serialise the GraphQL variables envelope
            try (var writer = JSONWriter.ofUTF8()) {
                // WAWebMexFetchNewsletterIsDomainPreviewableJob.mexFetchNewsletterIsDomainPreviewable
                // Begins the outer envelope and the nested "variables" object consumed by WAWebMexClient.fetchQuery
                writer.startObject();
                writer.writeName("variables");
                writer.writeColon();
                writer.startObject();
                // WAWebMexFetchNewsletterIsDomainPreviewableJob.mexFetchNewsletterIsDomainPreviewable
                // Emits the url_domains variable as a JSON array; matches the JS object literal {url_domains: e}
                // which always populates the key (the array itself may be empty).
                writer.writeName("url_domains");
                writer.writeColon();
                writeStringArray(writer, urlDomains);
                writer.endObject();
                writer.endObject();

                // ADAPTED: WAWebMexFetchNewsletterIsDomainPreviewableJob.mexFetchNewsletterIsDomainPreviewable
                // Flushes the JSON buffer into a StringWriter and wraps it in the shared MEX IQ envelope
                try (var output = new StringWriter()) {
                    writer.flushTo(output);
                    return MexJsonOperation.createMexNode(QUERY_ID, output.toString());
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        /**
         * Writes a list of strings as a JSON array into the given writer.
         *
         * @implNote The array is always emitted (possibly empty) so the on-wire
         * shape always contains the {@code url_domains} key, mirroring the JS
         * object literal {@code {url_domains: e}} which never omits the key.
         * @param writer the JSON writer to emit into
         * @param values the string values to serialise, may be {@code null}
         */
        private static void writeStringArray(JSONWriter writer, List<String> values) {
            writer.startArray();
            if (values != null) {
                for (var i = 0; i < values.size(); i++) {
                    if (i > 0) {
                        writer.writeComma();
                    }
                    writer.writeString(values.get(i));
                }
            }
            writer.endArray();
        }
    }

    /**
     * The response variant of {@link FetchNewsletterIsDomainPreviewableMex} that exposes the data
     * returned by the server after a successful query.
     *
     * @implNote WAWebMexFetchNewsletterIsDomainPreviewableJob: adapts the JSON root returned by the GraphQL
     * query into a Java value object.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterIsDomainPreviewableJob")
    final class Response implements FetchNewsletterIsDomainPreviewableMex {
        private final List<UrlPreviews> urlPreviews;

        /**
         * Creates a new response variant carrying the given list of url previews.
         *
         * @param urlPreviews the parsed list of url previews
         */
        private Response(List<UrlPreviews> urlPreviews) {
            this.urlPreviews = urlPreviews;
        }

        /**
         * Parses a MEX response from the given IQ response node.
         *
         * @implNote WAWebMexFetchNewsletterIsDomainPreviewableJob.mexFetchNewsletterIsDomainPreviewable: WA Web relies on the
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
         * Returns the {@code url_previews} field.
         *
         * @return the list of values, empty if absent
         */
        public List<UrlPreviews> urlPreviews() {
            return urlPreviews;
        }

        /**
         * A parsed {@code UrlPreviews} object.
         *
         * @implNote WAWebMexFetchNewsletterIsDomainPreviewableJob.mexFetchNewsletterIsDomainPreviewable: mirrors the per-entry
         * shape produced by the JS handler when it constructs
         * {@code new Map(i.map(({is_previewable, url_domain}) => [url_domain, is_previewable === true]))}.
         */
        public static final class UrlPreviews {
            private final String urlDomain;
            private final Boolean isPreviewable;

            /**
             * Creates a new UrlPreviews object.
             *
             * @param urlDomain the {@code url_domain} field
             * @param isPreviewable the {@code is_previewable} field, {@code null} if absent
             */
            private UrlPreviews(String urlDomain, Boolean isPreviewable) {
                this.urlDomain = urlDomain;
                this.isPreviewable = isPreviewable;
            }

            /**
             * Returns the {@code url_domain} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> urlDomain() {
                return Optional.ofNullable(urlDomain);
            }

            /**
             * Returns the {@code is_previewable} field.
             *
             * @implNote WAWebMexFetchNewsletterIsDomainPreviewableJob.mexFetchNewsletterIsDomainPreviewable: matches the JS
             * coalescing {@code t === true} which collapses {@code null}, {@code undefined}
             * and any non-{@code true} value to {@code false}.
             * @return {@code true} if the value is present and true, {@code false} otherwise
             */
            public boolean isPreviewable() {
                return isPreviewable != null && isPreviewable;
            }

            /**
             * Parses a {@code UrlPreviews} from the given JSON object.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
             */
            static Optional<UrlPreviews> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var urlDomain = obj.getString("url_domain");
                var isPreviewable = obj.getBoolean("is_previewable");
                return Optional.of(new UrlPreviews(urlDomain, isPreviewable));
            }

            /**
             * Parses a list of {@code UrlPreviews} from the given JSON array.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed results, empty if {@code arr} is {@code null}
             */
            static List<UrlPreviews> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<UrlPreviews>(arr.size());
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
         * @implNote WAWebMexFetchNewsletterIsDomainPreviewableJob.mexFetchNewsletterIsDomainPreviewable: mirrors the implicit
         * unwrapping that WA Web performs on the GraphQL response,
         * extracting the {@code xwa2_newsletter_message_integrity} root.
         * @param json the UTF-8 encoded JSON payload
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the envelope is missing expected fields
         */
        private static Optional<Response> of(byte[] json) {
            // WAWebMexFetchNewsletterIsDomainPreviewableJob.mexFetchNewsletterIsDomainPreviewable
            // Parses the raw JSON payload, bailing out if fastjson2 returns null
            var jsonObject = JSON.parseObject(json);
            if (jsonObject == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterIsDomainPreviewableJob.mexFetchNewsletterIsDomainPreviewable
            // Descends into the standard GraphQL "data" envelope
            var data = jsonObject.getJSONObject("data");
            if (data == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterIsDomainPreviewableJob.mexFetchNewsletterIsDomainPreviewable
            // Extracts the operation-specific root keyed by xwa2_newsletter_message_integrity
            var root = data.getJSONObject("xwa2_newsletter_message_integrity");
            if (root == null) {
                return Optional.empty();
            }

            var urlPreviews = UrlPreviews.ofArray(root.getJSONArray("url_previews"));

            return Optional.of(new Response(urlPreviews));
        }
    }
}
