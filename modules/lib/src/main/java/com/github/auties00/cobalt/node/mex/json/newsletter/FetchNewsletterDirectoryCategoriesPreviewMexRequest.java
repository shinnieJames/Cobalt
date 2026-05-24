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
 * Builds the MEX request that fetches a preview of newsletter directory
 * categories with featured channels.
 *
 * @apiNote
 * Drives the newsletter directory landing screen: the relay returns one
 * entry per requested category, each carrying a handful of featured
 * newsletters for visual preview. The {@code input} payload is supplied as
 * a pre-serialised string so callers can shape the categories list,
 * country code and per-category limit themselves.
 *
 * @implNote
 * This implementation passes the {@code input} variable as a raw string
 * value rather than building the JSON object in place; WA Web's
 * {@code mexFetchNewsletterDirectoryCategoriesPreview} accepts a
 * structured object with {@code categories}, {@code country_code} and
 * {@code per_category_limit} entries.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob")
public final class FetchNewsletterDirectoryCategoriesPreviewMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled persisted-query identifier of
     * {@code WAWebMexFetchNewsletterDirectoryCategoriesPreviewJobQuery.graphql}
     * on the WhatsApp relay.
     *
     * @apiNote
     * Sent as the {@code id} attribute of the outgoing {@code <query>} child.
     */
    public static final String QUERY_ID = "35266481849605779";

    /**
     * The GraphQL operation name reported by WA Web's {@code MexPerfTracker}
     * for this query.
     */
    public static final String OPERATION_NAME = "mexFetchNewsletterDirectoryCategoriesPreview";

    /**
     * The pre-serialised {@code input} GraphQL variable payload.
     */
    private final String input;

    /**
     * Constructs a request with the given pre-serialised {@code input}
     * payload.
     *
     * @apiNote
     * The {@code input} string is forwarded verbatim under the
     * {@code variables.input} key; callers are responsible for matching
     * the schema (categories list, country code, per-category limit).
     *
     * @param input the pre-serialised input payload, or {@code null} to
     *              omit
     */
    public FetchNewsletterDirectoryCategoriesPreviewMexRequest(String input) {
        this.input = input;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Returns {@link #QUERY_ID}.
     */
    @Override
    public String id() {
        return QUERY_ID;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Returns {@link #OPERATION_NAME}.
     */
    @Override
    public String name() {
        return OPERATION_NAME;
    }

    /**
     * Serialises this request into a MEX IQ {@link NodeBuilder}.
     *
     * @apiNote
     * Produces the {@code {variables: {input: "<payload>"}}} envelope; the
     * {@code input} entry is omitted when {@link #input} is {@code null}
     * so the GraphQL schema never receives an explicit {@code null}
     * variable.
     *
     * @implNote
     * This implementation writes the GraphQL variables directly through
     * {@link JSONWriter} and wraps any {@link IOException} from the
     * in-memory writer in an {@link UncheckedIOException}.
     *
     * @return the {@link NodeBuilder} carrying the IQ envelope and serialised
     *         GraphQL variables
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
