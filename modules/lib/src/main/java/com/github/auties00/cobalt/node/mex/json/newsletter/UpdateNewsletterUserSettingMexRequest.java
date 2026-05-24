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
 * Builds the MEX request that flips a per-user newsletter setting.
 *
 * @apiNote
 * Drives the newsletter mute toggles surfaced by
 * {@code WAWebNewsletterUpdateUserSettingJob.updateNewsletterUserSetting}:
 * when the local user toggles "mute admin notifications" or "mute follower
 * notifications" on a newsletter, the action runs this mutation with
 * {@code type} set to {@code "MUTE_ADMIN_ACTIVITY"} or
 * {@code "MUTE_FOLLOWER_ACTIVITY"} and {@code value} set to {@code "ON"}
 * or {@code "OFF"}. Build via the constructor with the newsletter Jid,
 * the setting type, and the new value; submit through the MEX IQ
 * dispatcher and pair the result with
 * {@link UpdateNewsletterUserSettingMexResponse#of(Node)}.
 *
 * @implNote
 * WA Web's caller wraps the underlying mutation in
 * {@code WAWebNewsletterRpcUtils.runWithBackoff} and forwards the result
 * to {@code WAWebMexNewsletterUtils.convertMutationResponse}; Cobalt
 * expects the caller to own the retry policy and to merge the result into
 * the local newsletter cache.
 */
@WhatsAppWebModule(moduleName = "WAWebMexUpdateNewsletterUserSetting")
public final class UpdateNewsletterUserSettingMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled persisted-query identifier of
     * {@code WAWebMexUpdateNewsletterUserSettingJobMutation.graphql} on
     * the WhatsApp relay.
     *
     * @apiNote
     * Sent as the {@code id} attribute of the outgoing {@code <query>} child;
     * the WhatsApp relay refuses requests whose persisted-query id is unknown.
     */
    public static final String QUERY_ID = "31938993655691868";

    /**
     * The GraphQL operation name reported by WA Web's {@code MexPerfTracker}
     * for this mutation.
     *
     * @apiNote
     * Reported to observability sinks that key telemetry on the operation
     * name; mirrors the export name exposed by
     * {@code WAWebMexUpdateNewsletterUserSetting}.
     */
    public static final String OPERATION_NAME = "mexUpdateNewsletterUserSetting";

    /**
     * The Jid string of the newsletter whose per-user setting is being
     * changed.
     */
    private final String newsletterId;

    /**
     * The setting-type identifier (for example
     * {@code "MUTE_ADMIN_ACTIVITY"}).
     */
    private final String type;

    /**
     * The new setting value (for example {@code "ON"} or {@code "OFF"}).
     */
    private final String value;

    /**
     * Constructs a request that flips a per-user setting on a newsletter.
     *
     * @apiNote
     * The {@code type} string mirrors WA Web's
     * {@code WAWebNewsletterModelUtils} constants
     * ({@code "MUTE_ADMIN_ACTIVITY"} for admin notifications,
     * {@code "MUTE_FOLLOWER_ACTIVITY"} for follower notifications). The
     * {@code value} string is the relay-defined toggle state
     * ({@code "ON"} for muted, {@code "OFF"} for unmuted in the mute
     * surface).
     *
     * @param newsletterId the newsletter Jid whose setting is being
     *                     changed
     * @param type         the setting-type identifier
     * @param value        the new setting value
     */
    public UpdateNewsletterUserSettingMexRequest(String newsletterId, String type, String value) {
        this.newsletterId = newsletterId;
        this.type = type;
        this.value = value;
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
     * {@code {variables: {input: {newsletter_id?, type?, value?}}}}
     * payload consumed by the persisted-query identified by
     * {@link #QUERY_ID}; each entry is omitted when {@code null} so the
     * GraphQL schema never receives explicit {@code null} variables.
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
    @WhatsAppWebExport(moduleName = "WAWebMexUpdateNewsletterUserSetting", exports = "mexUpdateNewsletterUserSetting",
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
            if (type != null) {
                writer.writeName("type");
                writer.writeColon();
                writer.writeString(type);
            }
            if (value != null) {
                writer.writeName("value");
                writer.writeColon();
                writer.writeString(value);
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
