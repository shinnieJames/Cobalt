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
 * Builds the MEX IQ stanza that registers or rotates the username recovery PIN.
 *
 * <p>This request backs the username PIN settings screen. The PIN is dispatched as the {@code pin}
 * GraphQL variable; a {@code null} PIN is the clear-PIN intent and omits the variable entirely. The
 * reply is consumed through {@link SetUsernameKeyMexResponse}.
 *
 * @see SetUsernameKeyMexResponse
 */
@WhatsAppWebModule(moduleName = "WAWebMexSetUsernameKeyJob")
public final class SetUsernameKeyMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled-document id the relay maps to the persisted mutation.
     *
     * <p>Emitted as the {@code query_id} attribute of the outbound {@code <query>} node.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexSetUsernameKeyJobMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "9749436995157074";

    /**
     * The GraphQL operation name reported alongside this request.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexSetUsernameKeyJobMutation.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "mexSetUsernameKeyQueryJob";

    /**
     * The {@code pin} GraphQL variable carrying the new recovery PIN, or {@code null} to clear it.
     */
    private final String pin;

    /**
     * Constructs a set-username-key mutation request.
     *
     * <p>The cleartext PIN is forwarded as-is; the relay performs the hashing server-side. Passing
     * {@code null} omits the variable, which signals the relay to clear any existing PIN.
     *
     * @param pin the new recovery PIN, or {@code null} to clear the existing PIN
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
     * @implNote This implementation emits {@code {"variables": {"pin": <pin>}}}, or
     * {@code {"variables": {}}} when {@link #pin} is {@code null}, mirroring the relay's reading of
     * the absent variable as the clear-PIN action; envelope construction is delegated to
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
