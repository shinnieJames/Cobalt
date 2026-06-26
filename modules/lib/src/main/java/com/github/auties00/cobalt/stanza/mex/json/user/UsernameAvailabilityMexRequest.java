package com.github.auties00.cobalt.stanza.mex.json.user;

import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.stanza.StanzaBuilder;
import com.github.auties00.cobalt.stanza.mex.MexStanza;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;

/**
 * Builds the MEX IQ stanza that checks whether a candidate username can be claimed.
 *
 * <p>This query backs the live-validation indicator on the username picker. The candidate name is
 * sent as the {@code input} variable; the relay validates length, charset, and reservation status
 * server-side. The optional {@code source} variable records which surface triggered the check and
 * the optional {@code session_id} variable correlates a sequence of checks within one editing
 * session. The reply is consumed through {@link UsernameAvailabilityMexResponse}.
 *
 * @see UsernameAvailabilityMexResponse
 */
@WhatsAppWebModule(moduleName = "WAWebMexUsernameAvailability")
public final class UsernameAvailabilityMexRequest implements MexStanza.Request.Json {
    /**
     * The compiled-document id the relay maps to the persisted query.
     *
     * <p>Emitted as the {@code query_id} attribute of the outbound {@code <query>} stanza.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexUsernameAvailabilityQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "26122779627399568";

    /**
     * The GraphQL operation name reported alongside this request.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexUsernameAvailabilityQuery.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "mexCheckUsernameAvailabilityQueryJob";

    /**
     * The {@code input} GraphQL variable carrying the candidate username, or {@code null} to omit it.
     */
    private final String input;

    /**
     * The {@code source} GraphQL variable recording the surface that triggered the check, or
     * {@code null} to omit it.
     */
    private final String source;

    /**
     * The {@code session_id} GraphQL variable correlating a sequence of checks within one editing
     * session, or {@code null} to omit it.
     */
    private final String sessionId;

    /**
     * Constructs a username-availability check request carrying only the candidate name.
     *
     * <p>The candidate name is forwarded verbatim as the {@code input} variable; the relay validates
     * length, charset, and reservation status server-side. The {@code source} and {@code session_id}
     * variables are omitted from the wire payload.
     *
     * @param input the candidate username, or {@code null} to omit the variable
     */
    public UsernameAvailabilityMexRequest(String input) {
        this(input, null, null);
    }

    /**
     * Constructs a username-availability check request carrying the candidate name along with the
     * triggering surface and editing-session correlation id.
     *
     * <p>The candidate name is forwarded verbatim as the {@code input} variable; the relay validates
     * length, charset, and reservation status server-side. The {@code source} variable records which
     * surface triggered the check and the {@code session_id} variable correlates a sequence of checks
     * within one editing session. Each variable whose value is {@code null} is omitted from the wire
     * payload.
     *
     * @param input     the candidate username, or {@code null} to omit the variable
     * @param source    the triggering surface, or {@code null} to omit the variable
     * @param sessionId the editing-session correlation id, or {@code null} to omit the variable
     */
    public UsernameAvailabilityMexRequest(String input, String source, String sessionId) {
        this.input = input;
        this.source = source;
        this.sessionId = sessionId;
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
     * @implNote This implementation emits {@code {"variables": {"input": <input>, "source":
     * <source>, "session_id": <sessionId>}}}, writing each variable only when its value is non-null
     * and emitting {@code {"variables": {}}} when all three are {@code null}, then defers envelope
     * construction to {@link MexStanza.Request.Json#createMexNode(String, String)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexUsernameAvailability", exports = "mexCheckUsernameAvailabilityQueryJob",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public StanzaBuilder toStanza() {
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

            if (source != null) {
                writer.writeName("source");
                writer.writeColon();
                writer.writeString(source);
            }

            if (sessionId != null) {
                writer.writeName("session_id");
                writer.writeColon();
                writer.writeString(sessionId);
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
