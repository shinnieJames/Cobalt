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
 * Builds the MEX request that fetches the relay's recommended-newsletters
 * list for the local user.
 *
 * @apiNote
 * Drives the newsletter directory "recommended" carousel invoked by
 * {@code WAWebNewsletterDirectorySearchQueryJob}: WA Web feeds the result
 * straight into the directory search UI alongside category and similar
 * carousels. Build via the constructor with an optional page limit and
 * country-code scope, then submit through the MEX IQ dispatcher and pair
 * the result with {@link FetchRecommendedNewslettersMexResponse#of(Node)}.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchRecommendedNewslettersJob")
public final class FetchRecommendedNewslettersMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled persisted-query identifier of
     * {@code WAWebMexFetchRecommendedNewslettersJobQuery.graphql} on the
     * WhatsApp relay.
     *
     * @apiNote
     * Sent as the {@code id} attribute of the outgoing {@code <query>} child;
     * the WhatsApp relay refuses requests whose persisted-query id is unknown.
     */
    public static final String QUERY_ID = "25806748772361516";

    /**
     * The GraphQL operation name reported by WA Web's {@code MexPerfTracker}
     * for this query.
     *
     * @apiNote
     * Reported to observability sinks that key telemetry on the operation
     * name; mirrors the export name exposed by
     * {@code WAWebMexFetchRecommendedNewslettersJob}.
     */
    public static final String OPERATION_NAME = "mexFetchRecommendedNewsletters";

    /**
     * The page-size limit sent under {@code variables.input.limit}, or
     * {@code null} to defer to the relay's default page size.
     */
    private final Long limit;

    /**
     * The ISO country-code scope sent under
     * {@code variables.input.country_codes}, or {@code null} to omit the
     * key entirely.
     */
    private final List<String> countryCodes;

    /**
     * The flag sent under {@code variables.fetch_status_metadata}, gating
     * the optional {@code status_metadata} sub-selection on each result.
     */
    private final boolean fetchStatusMetadata;

    /**
     * Constructs a request with the given variables.
     *
     * @apiNote
     * The {@code fetchStatusMetadata} flag mirrors WA Web's
     * {@code WAWebNewsletterGatingUtils.isNewsletterStatusReceiverEnabled()}
     * gate, which toggles the {@code status_metadata} sub-selection
     * carrying per-newsletter status counters; Cobalt callers pass the
     * boolean explicitly because the gating heuristic is JS-only.
     *
     * @param limit               the maximum number of recommended
     *                            newsletters to return, or {@code null} to
     *                            defer to the relay's default page size
     * @param countryCodes        the ISO country-code scope, or
     *                            {@code null} to omit the field
     * @param fetchStatusMetadata {@code true} to request the optional
     *                            {@code status_metadata} sub-selection
     */
    public FetchRecommendedNewslettersMexRequest(Long limit, List<String> countryCodes, boolean fetchStatusMetadata) {
        this.limit = limit;
        this.countryCodes = countryCodes;
        this.fetchStatusMetadata = fetchStatusMetadata;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Returns {@link #QUERY_ID}, the persisted-query identifier of the query.
     */
    @Override
    public String id() {
        return QUERY_ID;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Returns {@link #OPERATION_NAME}, the value WA Web's
     * {@code MexPerfTracker} reports for this query.
     */
    @Override
    public String name() {
        return OPERATION_NAME;
    }

    /**
     * Serialises this request into a MEX IQ {@link NodeBuilder} ready to be
     * dispatched through the WhatsApp relay.
     *
     * @apiNote
     * Produces the
     * {@code {variables: {input: {limit?, country_codes?}, fetch_status_metadata}}}
     * payload consumed by the persisted-query identified by
     * {@link #QUERY_ID}; {@code limit} and {@code country_codes} are
     * omitted when {@code null} so the GraphQL schema never receives
     * explicit {@code null} variables, while {@code fetch_status_metadata}
     * is always emitted as a boolean.
     *
     * @implNote
     * This implementation writes the GraphQL variables directly through
     * {@link JSONWriter} and delegates IQ envelope construction to
     * {@link Json#createMexNode(String, String)}; any {@link IOException}
     * raised by the in-memory writer is wrapped in an
     * {@link UncheckedIOException} since neither sink can fail in practice.
     *
     * @return the {@link NodeBuilder} carrying the IQ envelope and serialised
     *         GraphQL variables
     * @throws UncheckedIOException if the underlying writer fails
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchRecommendedNewslettersJob", exports = "mexFetchRecommendedNewsletters",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();

            writer.writeName("input");
            writer.writeColon();
            writer.startObject();
            if (limit != null) {
                writer.writeName("limit");
                writer.writeColon();
                writer.writeInt64(limit);
            }
            if (countryCodes != null) {
                writer.writeName("country_codes");
                writer.writeColon();
                writer.startArray();
                for (var i = 0; i < countryCodes.size(); i++) {
                    if (i > 0) {
                        writer.writeComma();
                    }
                    writer.writeString(countryCodes.get(i));
                }
                writer.endArray();
            }
            writer.endObject();

            writer.writeName("fetch_status_metadata");
            writer.writeColon();
            writer.writeBool(fetchStatusMetadata);

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
