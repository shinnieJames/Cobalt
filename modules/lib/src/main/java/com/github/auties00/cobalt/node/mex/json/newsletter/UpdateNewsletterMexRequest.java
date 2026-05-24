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
 * Builds the MEX request that updates the mutable metadata of a
 * newsletter.
 *
 * @apiNote
 * Drives the newsletter-edit owner flow surfaced by
 * {@code WAWebNewsletterMetadataQueryJob}: the owner edits one or more of
 * the newsletter's name, description, picture, or reaction-codes setting
 * in the channel-info screen, then the action runs this mutation with
 * only the changed fields populated in the {@code updates} object. The
 * relay echoes the full updated {@code thread_metadata} block so the
 * client can refresh its local cache through
 * {@link UpdateNewsletterMexResponse#threadMetadata()}.
 *
 * @implNote
 * WA Web's caller wraps the underlying mutation in
 * {@code WAWebNewsletterRpcUtils.runWithBackoff} and runs the response
 * through {@code WAWebMexNewsletterParseUtils.parseMexNewsletterResponse}
 * to merge it into the cached newsletter model; Cobalt expects callers to
 * own the retry policy and the merge step.
 */
@WhatsAppWebModule(moduleName = "WAWebMexUpdateNewsletterJob")
public final class UpdateNewsletterMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled persisted-query identifier of
     * {@code WAWebMexUpdateNewsletterJobMutation.graphql} on the WhatsApp
     * relay.
     *
     * @apiNote
     * Sent as the {@code id} attribute of the outgoing {@code <query>} child;
     * the WhatsApp relay refuses requests whose persisted-query id is unknown.
     */
    public static final String QUERY_ID = "24250201037901610";

    /**
     * The GraphQL operation name reported by WA Web's {@code MexPerfTracker}
     * for this mutation.
     *
     * @apiNote
     * Reported to observability sinks that key telemetry on the operation
     * name; mirrors the export name exposed by
     * {@code WAWebMexUpdateNewsletterJob}.
     */
    public static final String OPERATION_NAME = "mexUpdateNewsletter";

    /**
     * The Jid string of the newsletter being edited.
     */
    private final String newsletterId;

    /**
     * The pre-built {@code updates} object carrying the changed fields,
     * keyed by {@code name}, {@code description}, {@code picture}, and
     * {@code settings.reaction_codes.value}.
     */
    private final JSONObject updates;

    /**
     * Constructs a request that applies the given updates to the
     * newsletter.
     *
     * @apiNote
     * The {@code updates} object must mirror the JS inline literal
     * {@code {name, description, picture, settings}} WA Web builds before
     * calling {@code mexUpdateNewsletter}; only changed fields should be
     * populated. The {@code settings} sub-object only carries the
     * {@code reaction_codes.value} key mapped through WA Web's
     * {@code WAWebMexNewsletterUtils.mapReactionCodesSettingToMexInput}.
     *
     * @param newsletterId the newsletter Jid being edited
     * @param updates      the pre-built {@code updates} object carrying
     *                     the changed fields
     */
    public UpdateNewsletterMexRequest(String newsletterId, JSONObject updates) {
        this.newsletterId = newsletterId;
        this.updates = updates;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Returns {@link #QUERY_ID}, the persisted-query identifier of the
     * mutation.
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
     * {@code MexPerfTracker} reports for this mutation.
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
     * {@code {variables: {newsletter_id?, updates?: {...}}}} payload
     * consumed by the persisted-query identified by {@link #QUERY_ID}; the
     * {@code updates} variable is emitted as a structured JSON object
     * (not a string-encoded payload) to match the GraphQL input type the
     * relay expects, and both entries are omitted when {@code null} so
     * the GraphQL schema never receives explicit {@code null} variables.
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
    @WhatsAppWebExport(moduleName = "WAWebMexUpdateNewsletterJob", exports = "mexUpdateNewsletter",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
            if (newsletterId != null) {
                writer.writeName("newsletter_id");
                writer.writeColon();
                writer.writeString(newsletterId);
            }

            if (updates != null) {
                writer.writeName("updates");
                writer.writeColon();
                writer.writeAny(updates);
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
