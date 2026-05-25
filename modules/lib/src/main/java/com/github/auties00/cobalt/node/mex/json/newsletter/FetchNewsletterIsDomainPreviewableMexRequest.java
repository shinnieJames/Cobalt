package com.github.auties00.cobalt.node.mex.json.newsletter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.mex.MexOperation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds the MEX request that asks the server whether the given URL domains may render link
 * previews inside newsletter messages.
 *
 * <p>Backs the newsletter composer's link-preview gate. The relay maintains a per-domain allowlist
 * for newsletter previews and this query batches the lookup for every domain the user has typed
 * before the composer decides to fetch and render previews. The matching response is parsed by
 * {@link FetchNewsletterIsDomainPreviewableMexResponse}.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterIsDomainPreviewableJob")
public final class FetchNewsletterIsDomainPreviewableMexRequest implements MexOperation.Request.Json {
    /**
     * Holds the compiled persisted-query identifier of
     * {@code WAWebMexFetchNewsletterIsDomainPreviewableJobQuery.graphql} on the WhatsApp relay.
     *
     * <p>Sent as the {@code query_id} attribute of the outgoing {@code <query>} child.
     */
    public static final String QUERY_ID = "9849510985088294";

    /**
     * Holds the GraphQL operation name reported by WhatsApp Web's MEX perf tracker for this query.
     */
    public static final String OPERATION_NAME = "mexFetchNewsletterIsDomainPreviewable";

    /**
     * Holds the URL domains to validate.
     */
    private final List<String> urlDomains;

    /**
     * Constructs a request asking whether each of the given URL domains is previewable.
     *
     * <p>Each entry is a bare host string with no scheme and no path; the relay matches against the
     * configured per-domain allowlist exactly.
     *
     * @param urlDomains the URL domains to validate, may be {@code null} or empty
     */
    public FetchNewsletterIsDomainPreviewableMexRequest(List<String> urlDomains) {
        this.urlDomains = urlDomains;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the value of {@link #QUERY_ID}.
     */
    @Override
    public String id() {
        return QUERY_ID;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the value of {@link #OPERATION_NAME}.
     */
    @Override
    public String name() {
        return OPERATION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Produces the {@code {variables: {url_domains: [...]}}} payload; the {@code url_domains} key
     * is always emitted and the array itself may be empty.
     *
     * @implNote This implementation writes the GraphQL variables directly through a
     * {@link JSONWriter} and wraps any {@link IOException} from the in-memory writer in an
     * {@link UncheckedIOException}.
     *
     * @return the {@link NodeBuilder} carrying the IQ envelope and serialised GraphQL variables
     * @throws UncheckedIOException if the underlying writer fails
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterIsDomainPreviewableJob", exports = "mexFetchNewsletterIsDomainPreviewable",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
            writer.writeName("url_domains");
            writer.writeColon();
            writeStringArray(writer, urlDomains);
            writer.endObject();
            writer.endObject();

            try (var output = new StringWriter()) {
                writer.flushTo(output);
                return Json.createMexNode(QUERY_ID, output.toString());
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Writes a list of strings as a JSON array into the given writer.
     *
     * <p>Emits an empty array when {@code values} is {@code null}.
     *
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
