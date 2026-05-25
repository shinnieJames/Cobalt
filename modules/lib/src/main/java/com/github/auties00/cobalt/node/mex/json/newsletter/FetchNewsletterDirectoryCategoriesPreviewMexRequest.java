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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Builds the MEX request that fetches a preview of newsletter directory categories with featured
 * channels.
 *
 * <p>This query drives the newsletter directory landing screen: the relay returns one entry per
 * requested category, each carrying a handful of featured newsletters for visual preview. The
 * {@code input} payload is supplied as a pre-serialised string so callers can shape the categories
 * list, country code and per-category limit themselves.
 *
 * @implNote This implementation passes the {@code input} variable as a raw string value rather than
 * building the JSON object in place; the relay's
 * {@code mexFetchNewsletterDirectoryCategoriesPreview} accepts a structured object with
 * {@code categories}, {@code country_code} and {@code per_category_limit} entries.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob")
public final class FetchNewsletterDirectoryCategoriesPreviewMexRequest implements MexOperation.Request.Json {
    /**
     * Holds the compiled persisted-query identifier of
     * {@code WAWebMexFetchNewsletterDirectoryCategoriesPreviewJobQuery.graphql} on the WhatsApp
     * relay.
     *
     * <p>Sent as the {@code query_id} attribute of the outgoing {@code <query>} child.
     */
    public static final String QUERY_ID = "35266481849605779";

    /**
     * Holds the GraphQL operation name reported by WhatsApp Web's MEX perf tracker for this query.
     */
    public static final String OPERATION_NAME = "mexFetchNewsletterDirectoryCategoriesPreview";

    /**
     * Holds the pre-serialised {@code input} GraphQL variable payload.
     */
    private final String input;

    /**
     * Constructs a request with the given pre-serialised {@code input} payload.
     *
     * <p>The {@code input} string is forwarded verbatim under the {@code variables.input} key;
     * callers are responsible for matching the schema (categories list, country code, per-category
     * limit).
     *
     * @param input the pre-serialised input payload, or {@code null} to omit
     */
    public FetchNewsletterDirectoryCategoriesPreviewMexRequest(String input) {
        this.input = input;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@link #QUERY_ID}.
     */
    @Override
    public String id() {
        return QUERY_ID;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@link #OPERATION_NAME}.
     */
    @Override
    public String name() {
        return OPERATION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Produces the {@code {variables: {input: "<payload>"}}} envelope; the {@code input} entry is
     * omitted when {@link #input} is {@code null} so the GraphQL schema never receives an explicit
     * {@code null} variable.
     *
     * @implNote This implementation writes the GraphQL variables directly through a
     * {@link JSONWriter} and wraps any {@link IOException} from the in-memory writer in an
     * {@link UncheckedIOException}.
     *
     * @return the {@link NodeBuilder} carrying the IQ envelope and serialised GraphQL variables
     * @throws UncheckedIOException if the underlying writer fails
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob", exports = "mexFetchNewsletterDirectoryCategoriesPreview",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
            if (input != null) {
                writer.writeName("input");
                writer.writeColon();
                writer.writeString(input);
            }
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
}
