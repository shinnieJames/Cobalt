package com.github.auties00.cobalt.node.mex.json.user;

import com.alibaba.fastjson2.JSON;
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
import java.util.Optional;

/**
 * Builds the MEX IQ stanza that registers or rotates the username recovery
 * PIN.
 *
 * @apiNote Powers the username PIN settings screen. WA Web's
 * {@code WAWebSetUsernameKeyQueryJob} dispatches this mutation under the
 * {@code UI_ACTION} job-priority bucket and treats a {@code null} pin as
 * the "clear PIN" intent. Pair the dispatched stanza with
 * {@link SetUsernameKeyMexResponse} to consume the reply.
 *
 * @implNote This implementation omits the {@code pin} variable when
 * {@code null}, mirroring WA Web's {@code t!=null?{pin:t}:{}} call site;
 * the relay then interprets the absent variable as the clear-PIN action.
 *
 * @see SetUsernameKeyMexResponse
 */
@WhatsAppWebModule(moduleName = "WAWebMexSetUsernameKeyJob")
public final class SetUsernameKeyMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled-document id the relay maps to the persisted mutation.
     *
     * @apiNote Used as the {@code query_id} attribute of the outbound
     * {@code <query>} node. Matches the {@code params.id} field of
     * {@code WAWebMexSetUsernameKeyJobMutation.graphql} for the snapshot
     * this file was generated against.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexSetUsernameKeyJobMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "9749436995157074";

    /**
     * The GraphQL operation name reported alongside this request.
     *
     * @apiNote Mirrors {@code params.name} on
     * {@code WAWebMexSetUsernameKeyJobMutation.graphql}; WA Web tags the
     * value to {@code MexPerfTracker} for per-operation telemetry bucketing.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexSetUsernameKeyJobMutation.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "mexSetUsernameKeyQueryJob";

    /**
     * The {@code pin} GraphQL variable carrying the new recovery PIN.
     */
    private final String pin;

    /**
     * Constructs a set-username-key mutation request.
     *
     * @apiNote Pass the cleartext PIN; the relay performs the hashing
     * server-side. Passing {@code null} omits the variable, which signals
     * the relay to clear any existing PIN.
     *
     * @param pin the new recovery PIN, or {@code null} to clear the
     *            existing PIN
     */
    public SetUsernameKeyMexRequest(String pin) {
        this.pin = pin;
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
     * @implNote This implementation emits {@code {"variables": {"pin": <pin>}}}
     * (or {@code {"variables": {}}} when {@code pin} is {@code null}) and
     * defers envelope construction to
     * {@link MexOperation.Request.Json#createMexNode(String, String)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexSetUsernameKeyJob", exports = "mexSetUsernameKeyQueryJob",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
            if (pin != null) {
                writer.writeName("pin");
                writer.writeColon();
                writer.writeString(pin);
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
