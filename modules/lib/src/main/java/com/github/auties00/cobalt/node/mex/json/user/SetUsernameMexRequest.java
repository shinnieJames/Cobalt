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
import java.util.Objects;
import java.util.Optional;

/**
 * Builds the MEX IQ stanza that claims, reserves, or updates a WhatsApp
 * username.
 *
 * @apiNote Powers the username picker on the Settings screen and the
 * username step during onboarding. WA Web's {@code WAWebSetUsernameJob}
 * dispatches this mutation with {@code source="USER_INPUT"} during the
 * standard settings flow and toggles {@code reserved} based on the
 * current {@code WAWebUserPrefsUsername.getUsernameState()}. Pair the
 * dispatched stanza with {@link SetUsernameMexResponse} to consume the
 * reply.
 *
 * @see SetUsernameMexResponse
 */
@WhatsAppWebModule(moduleName = "WAWebMexSetUsernameJob")
public final class SetUsernameMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled-document id the relay maps to the persisted mutation.
     *
     * @apiNote Used as the {@code query_id} attribute of the outbound
     * {@code <query>} node. Matches the {@code params.id} field of
     * {@code WAWebMexSetUsernameJobMutation.graphql} for the snapshot this
     * file was generated against.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexSetUsernameJobMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "25757341163897635";

    /**
     * The GraphQL operation name reported alongside this request.
     *
     * @apiNote Mirrors {@code params.name} on
     * {@code WAWebMexSetUsernameJobMutation.graphql}; WA Web tags the value
     * to {@code MexPerfTracker} for per-operation telemetry bucketing.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexSetUsernameJobMutation.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "mexSetUsernameQueryJob";

    /**
     * The {@code input} GraphQL variable carrying the candidate username.
     */
    private final String input;

    /**
     * The {@code reserved} flag, possibly {@code null}.
     */
    private final Boolean reserved;

    /**
     * The {@code session_id} field, possibly {@code null}.
     */
    private final String sessionId;

    /**
     * The {@code source} entry-point tag, possibly {@code null}.
     */
    private final String source;

    /**
     * Constructs a set-username mutation request.
     *
     * @apiNote {@code reserved=true} pre-claims the username during
     * onboarding without finalising it; the typical post-onboarding flow
     * later re-dispatches with {@code reserved=false}. {@code sessionId}
     * ties the reservation to a registration flow; {@code source} tags
     * the entry point ({@code "USER_INPUT"} is the value WA Web emits
     * from the settings UI).
     *
     * @param input the candidate username to claim or update
     * @param reserved whether the username should be reserved, or
     *                 {@code null} to omit the flag
     * @param sessionId the registration-flow session identifier, or
     *                  {@code null} to omit
     * @param source the entry-point tag, or {@code null} to omit
     */
    public SetUsernameMexRequest(String input, Boolean reserved, String sessionId, String source) {
        this.input = input;
        this.reserved = reserved;
        this.sessionId = sessionId;
        this.source = source;
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
     * @implNote This implementation mirrors WA Web's
     * {@code isStringNullOrEmpty(t.input) ? {} : t} gate: when
     * {@link #input} is {@code null} or empty, the variables object is
     * emitted as {@code {}} regardless of the other fields. When
     * {@link #input} is present, the remaining variables
     * ({@code reserved}, {@code session_id}, {@code source}) are forwarded
     * whenever non-{@code null}. Envelope construction is delegated to
     * {@link MexOperation.Request.Json#createMexNode(String, String)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexSetUsernameJob", exports = "mexSetUsernameQueryJob",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
            if (input != null && !input.isEmpty()) {
                writer.writeName("input");
                writer.writeColon();
                writer.writeString(input);
                if (reserved != null) {
                    writer.writeName("reserved");
                    writer.writeColon();
                    writer.writeBool(reserved);
                }
                if (sessionId != null) {
                    writer.writeName("session_id");
                    writer.writeColon();
                    writer.writeString(sessionId);
                }
                if (source != null) {
                    writer.writeName("source");
                    writer.writeColon();
                    writer.writeString(source);
                }
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
