package com.github.auties00.cobalt.stanza.mex.json.newsletter;

import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.stanza.StanzaBuilder;
import com.github.auties00.cobalt.stanza.mex.MexStanza;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;

/**
 * Builds the MEX request that fetches metadata for every newsletter followed by the authenticated
 * user.
 *
 * <p>This query drives the local newsletter list hydration run during login and periodic syncs; its
 * response is split into active channels and those the relay reports as deleted by
 * {@link FetchAllNewslettersMetadataMexResponse#partition()}. The two gating variables control
 * whether the response carries the optional {@code wamo_sub} (paid newsletter subscription) and
 * {@code status_metadata} fragments.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchAllNewslettersMetadataJob")
public final class FetchAllNewslettersMetadataMexRequest implements MexStanza.Request.Json {
    /**
     * Holds the compiled persisted-query identifier of
     * {@code WAWebMexFetchAllNewslettersMetadataJobQuery.graphql} on the WhatsApp relay.
     *
     * <p>Sent as the {@code query_id} attribute of the outgoing {@code <query>} child.
     */
    public static final String QUERY_ID = "25399611239711790";

    /**
     * Holds the GraphQL operation name reported by WhatsApp Web's MEX perf tracker for this query.
     */
    public static final String OPERATION_NAME = "mexFetchAllNewsletters";

    /**
     * Holds the value of the {@code fetch_wamo_sub} GraphQL variable, or {@code null} to omit the
     * entry.
     */
    private final Boolean fetchWamoSub;

    /**
     * Holds the value of the {@code fetch_status_metadata} GraphQL variable, or {@code null} to omit
     * the entry.
     */
    private final Boolean fetchStatusMetadata;

    /**
     * Constructs a request that selects only the {@code fetch_wamo_sub} gating flag.
     *
     * <p>The {@code fetch_status_metadata} variable is implicitly omitted, leaving the
     * {@code status_metadata} fragment unrequested.
     *
     * @param fetchWamoSub the value of the {@code fetch_wamo_sub} variable, or {@code null} to omit
     */
    public FetchAllNewslettersMetadataMexRequest(Boolean fetchWamoSub) {
        this(fetchWamoSub, null);
    }

    /**
     * Constructs a request with both GraphQL gating variables.
     *
     * <p>Passing {@code null} for either flag omits the corresponding variable from the GraphQL
     * payload so the relay applies its default.
     *
     * @param fetchWamoSub        the value of the {@code fetch_wamo_sub} variable, or {@code null}
     *                            to omit
     * @param fetchStatusMetadata the value of the {@code fetch_status_metadata} variable, or
     *                            {@code null} to omit
     */
    public FetchAllNewslettersMetadataMexRequest(Boolean fetchWamoSub, Boolean fetchStatusMetadata) {
        this.fetchWamoSub = fetchWamoSub;
        this.fetchStatusMetadata = fetchStatusMetadata;
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
     * <p>Produces the {@code {variables: {fetch_wamo_sub?, fetch_status_metadata?}}} payload; either
     * gating boolean is omitted when its backing {@link Boolean} is {@code null} so the GraphQL
     * schema never receives an explicit {@code null} variable.
     *
     * @implNote This implementation writes the GraphQL variables directly through a
     * {@link JSONWriter} and wraps any {@link IOException} from the in-memory writer in an
     * {@link UncheckedIOException}.
     *
     * @return the {@link StanzaBuilder} carrying the IQ envelope and serialised GraphQL variables
     * @throws UncheckedIOException if the underlying writer fails
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchAllNewslettersMetadataJob", exports = "mexFetchAllNewsletters",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public StanzaBuilder toStanza() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
            if (fetchWamoSub != null) {
                writer.writeName("fetch_wamo_sub");
                writer.writeColon();
                writer.writeBool(fetchWamoSub);
            }
            if (fetchStatusMetadata != null) {
                writer.writeName("fetch_status_metadata");
                writer.writeColon();
                writer.writeBool(fetchStatusMetadata);
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
