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
 * Builds the MEX IQ stanza that fetches the authenticated user's privacy preferences.
 *
 * <p>The reply feeds the Settings privacy screen and the gates that outgoing-send paths consult
 * before emitting presence and read receipts. The request carries an {@code input} GraphQL variable
 * naming the user and the privacy features to query (the feature set includes {@code LAST},
 * {@code ONLINE}, {@code PROFILE}, {@code ABOUT}, {@code READRECEIPTS}, {@code GROUPADD},
 * {@code CALLADD}, {@code STICKERS}, {@code MESSAGES}, {@code DEFENSE}); the dispatched stanza is
 * paired with {@link GetPrivacySettingsMexResponse} to consume the reply.
 *
 * @implNote This implementation accepts the variable as an opaque pre-serialised string so callers
 * may decide which feature subset to request, where WhatsApp Web passes a structured
 * {@code {jid, privacyFeatures}} input.
 *
 * @see GetPrivacySettingsMexResponse
 */
public final class GetPrivacySettingsMexRequest implements MexOperation.Request.Json {
    /**
     * Holds the compiled-document identifier the relay maps to the persisted query.
     *
     * <p>Emitted as the {@code query_id} attribute of the outbound {@code <query>} node.
     *
     * @implNote The value matches the compiled query for the WhatsApp Web snapshot this file was
     * generated against, and must be rotated together with that bundle.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexGetPrivacySettingsQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "25637004609323493";

    /**
     * Holds the GraphQL operation name reported alongside this request.
     *
     * <p>WhatsApp Web tags this name onto its per-operation latency metrics.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexGetPrivacySettingsQuery.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "fetchPrivacySettings";

    /**
     * Holds the {@code input} GraphQL variable carrying the pre-serialised payload.
     */
    private final String input;

    /**
     * Constructs a privacy-settings fetch request.
     *
     * <p>The argument must already be the serialised
     * {@code {query_input: [{jid, privacy_features: [...]}]}} payload WhatsApp Web sends; the payload
     * is not materialised on the caller's behalf.
     *
     * @param input the serialised input payload, or {@code null} to omit the variable
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
     * @implNote This implementation emits {@code {"variables": {"input": <input>}}}, or
     * {@code {"variables": {}}} when {@code input} is {@code null}, and defers envelope construction
     * to {@link MexOperation.Request.Json#createMexNode(String, String)}.
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
