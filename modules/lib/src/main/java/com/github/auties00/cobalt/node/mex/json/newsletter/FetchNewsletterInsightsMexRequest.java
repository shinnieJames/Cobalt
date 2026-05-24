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

/**
 * Builds the MEX request that fetches admin analytics insights for a
 * newsletter.
 *
 * @apiNote
 * Drives the admin insights dashboard; the {@code metrics} list selects
 * which counters are pulled (views, reactions, forwards, follower growth)
 * and the relay returns a per-metric series plus a freshness timestamp.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterInsightsJob")
public final class FetchNewsletterInsightsMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled persisted-query identifier of
     * {@code WAWebMexFetchNewsletterInsightsJobQuery.graphql} on the
     * WhatsApp relay.
     *
     * @apiNote
     * Sent as the {@code id} attribute of the outgoing {@code <query>} child.
     */
    public static final String QUERY_ID = "9853618868050977";

    /**
     * The GraphQL operation name reported by WA Web's {@code MexPerfTracker}
     * for this query.
     */
    public static final String OPERATION_NAME = "mexFetchNewsletterInsights";

    /**
     * The newsletter Jid whose insights are being requested.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterInsightsJob", exports = "mexFetchNewsletterInsights",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final String newsletterId;

    /**
     * The list of metric identifiers to fetch values for.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterInsightsJob", exports = "mexFetchNewsletterInsights",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final List<String> metrics;

    /**
     * Constructs a request fetching the supplied metrics for the given
     * newsletter.
     *
     * @apiNote
     * Each metric identifier in {@code metrics} maps to one
     * {@code WAWebNewsletterInsightUtils} counter on the server side.
     *
     * @param newsletterId the newsletter Jid
     * @param metrics      the metric identifiers to pull, may be {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterInsightsJob", exports = "mexFetchNewsletterInsights",
            adaptation = WhatsAppAdaptation.DIRECT)
    public FetchNewsletterInsightsMexRequest(String newsletterId, List<String> metrics) {
        this.newsletterId = newsletterId;
        this.metrics = metrics;
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
     * Produces the {@code {variables: {input: {newsletter_id, metrics}}}}
     * payload; each scalar is omitted when its field is {@code null}.
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
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterInsightsJob", exports = "mexFetchNewsletterInsights",
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

            if (newsletterId != null) {
                writer.writeName("newsletter_id");
                writer.writeColon();
                writer.writeString(newsletterId);
            }

            if (metrics != null) {
                writer.writeName("metrics");
                writer.writeColon();
                writer.startArray();
                for (var i = 0; i < metrics.size(); i++) {
                    if (i > 0) {
                        writer.writeComma();
                    }
                    writer.writeString(metrics.get(i));
                }
                writer.endArray();
            }

            writer.endObject();
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
