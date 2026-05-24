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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Builds the MEX IQ stanza that checks whether a candidate username can be
 * claimed.
 *
 * @apiNote Powers the live-validation indicator on the username picker
 * (WA Web's {@code WAWebCheckUsernameAvailabilityJob}) and the
 * {@code usernameCheckDebug} developer command in
 * {@code WAWebDebugUsername}. Pair the dispatched stanza with
 * {@link UsernameAvailabilityMexResponse} to consume the reply.
 *
 * @see UsernameAvailabilityMexResponse
 */
@WhatsAppWebModule(moduleName = "WAWebMexUsernameAvailability")
public final class UsernameAvailabilityMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled-document id the relay maps to the persisted query.
     *
     * @apiNote Used as the {@code query_id} attribute of the outbound
     * {@code <query>} node. Matches the {@code params.id} field of
     * {@code WAWebMexUsernameAvailabilityQuery.graphql} for the snapshot
     * this file was generated against.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexUsernameAvailabilityQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "9615795045169045";

    /**
     * The GraphQL operation name reported alongside this request.
     *
     * @apiNote Mirrors {@code params.name} on
     * {@code WAWebMexUsernameAvailabilityQuery.graphql}; WA Web tags the
     * value to {@code MexPerfTracker} for per-operation telemetry bucketing.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexUsernameAvailabilityQuery.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "mexCheckUsernameAvailabilityQueryJob";

    /**
     * The {@code input} GraphQL variable carrying the candidate username.
     */
    private final String input;

    /**
     * Constructs a username-availability check request.
     *
     * @apiNote {@code input} is forwarded verbatim as the {@code input}
     * variable; the relay validates length, charset, and reservation
     * status server-side.
     *
     * @param input the candidate username, or {@code null} to omit the
     *              variable
     */
    public UsernameAvailabilityMexRequest(String input) {
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
    @WhatsAppWebExport(moduleName = "WAWebMexUsernameAvailability", exports = "mexCheckUsernameAvailabilityQueryJob",
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
