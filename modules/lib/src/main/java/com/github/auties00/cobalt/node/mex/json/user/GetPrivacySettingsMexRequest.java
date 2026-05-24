package com.github.auties00.cobalt.node.mex.json.user;

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
 * Builds the MEX IQ stanza that fetches the authenticated user's privacy
 * preferences.
 *
 * @apiNote Powers the Settings privacy screen and feeds the gates that
 * outgoing-send paths consult before emitting presence and read receipts.
 * WA Web's {@code WAWebMexGetPrivacySetting.fetchPrivacySettings} passes a
 * structured {@code {jid, privacyFeatures}} input (the feature list
 * includes {@code LAST}, {@code ONLINE}, {@code PROFILE}, {@code ABOUT},
 * {@code READRECEIPTS}, {@code GROUPADD}, {@code CALLADD}, {@code STICKERS},
 * {@code MESSAGES}, {@code DEFENSE}); Cobalt accepts the variable as an
 * opaque pre-serialised string so callers may decide which feature subset
 * to request. Pair the dispatched stanza with
 * {@link GetPrivacySettingsMexResponse} to consume the reply.
 *
 * @see GetPrivacySettingsMexResponse
 */
public final class GetPrivacySettingsMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled-document id the relay maps to the persisted query.
     *
     * @apiNote Used as the {@code query_id} attribute of the outbound
     * {@code <query>} node. Matches the {@code params.id} field of
     * {@code WAWebMexGetPrivacySettingsQuery.graphql} for the snapshot this
     * file was generated against.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexGetPrivacySettingsQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "25637004609323493";

    /**
     * The GraphQL operation name reported alongside this request.
     *
     * @apiNote Mirrors {@code params.name} on
     * {@code WAWebMexGetPrivacySettingsQuery.graphql}; WA Web tags the value
     * to {@code MexPerfTracker} for per-operation telemetry bucketing.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexGetPrivacySettingsQuery.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "fetchPrivacySettings";

    /**
     * The {@code input} GraphQL variable carrying the pre-serialised payload.
     */
    private final String input;

    /**
     * Constructs a privacy-settings fetch request.
     *
     * @apiNote {@code input} must already be the serialised
     * {@code {query_input: [{jid, privacy_features: [...]}]}} payload WA Web
     * sends; Cobalt does not materialise it on the caller's behalf.
     *
     * @param input the serialised input payload, or {@code null} to omit
     *              the variable
     */
    public GetPrivacySettingsMexRequest(String input) {
        this.input = input;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String id() {
        return QUERY_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String name() {
        return OPERATION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation emits {@code {"variables": {"input": <input>}}}
     * (or {@code {"variables": {}}} when {@code input} is {@code null}) and
     * defers envelope construction to
     * {@link MexOperation.Request.Json#createMexNode(String, String)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexGetPrivacySettingsQuery.graphql", exports = "params.id",
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
