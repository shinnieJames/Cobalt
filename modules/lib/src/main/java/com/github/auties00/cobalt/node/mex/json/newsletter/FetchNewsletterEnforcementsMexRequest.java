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
 * Builds the MEX request that fetches the moderation enforcement history
 * of a newsletter.
 *
 * @apiNote
 * Drives the admin moderation surface that lists profile-picture
 * deletions, account suspensions, violating-message takedowns and
 * geographical suspensions applied to the newsletter, plus their appeal
 * state. The {@code locale} variable selects the language for the
 * human-readable policy text the server attaches to each enforcement.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterEnforcementsJob")
public final class FetchNewsletterEnforcementsMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled persisted-query identifier of
     * {@code WAWebMexFetchNewsletterEnforcementsJobQuery.graphql} on the
     * WhatsApp relay.
     *
     * @apiNote
     * Sent as the {@code id} attribute of the outgoing {@code <query>} child.
     */
    public static final String QUERY_ID = "25987882310910935";

    /**
     * The GraphQL operation name reported by WA Web's {@code MexPerfTracker}
     * for this query.
     */
    public static final String OPERATION_NAME = "mexFetchNewsletterEnforcements";

    /**
     * The locale tag the server uses when localising the policy text
     * embedded in each enforcement record.
     */
    private final String locale;

    /**
     * The newsletter Jid whose enforcement history is being fetched.
     */
    private final String newsletterId;

    /**
     * Constructs a request for the enforcement history of the given newsletter
     * under the given locale.
     *
     * @apiNote
     * Both arguments are written as top-level GraphQL variables; pass
     * {@code null} for either to skip emitting it (the server then applies
     * its defaults).
     *
     * @param locale       the locale tag for localised policy text
     * @param newsletterId the newsletter Jid
     */
    public FetchNewsletterEnforcementsMexRequest(String locale, String newsletterId) {
        this.locale = locale;
        this.newsletterId = newsletterId;
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
     * Produces the {@code {variables: {locale, newsletter_id}}} payload;
     * either variable is omitted when its field is {@code null}.
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
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterEnforcementsJob", exports = "mexFetchNewsletterEnforcements",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
            if (locale != null) {
                writer.writeName("locale");
                writer.writeColon();
                writer.writeString(locale);
            }

            if (newsletterId != null) {
                writer.writeName("newsletter_id");
                writer.writeColon();
                writer.writeString(newsletterId);
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
