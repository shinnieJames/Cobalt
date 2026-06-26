package com.github.auties00.cobalt.stanza.mex.json.community;

import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.stanza.StanzaBuilder;
import com.github.auties00.cobalt.stanza.mex.MexStanza;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;

/**
 * Outbound MEX request that fetches the current participant count for one or
 * more subgroups inside a community.
 *
 * <p>This query backs the community-panel participant-count refresh path:
 * it fetches only the {@code id} and {@code total_participants_count} fields
 * rather than reloading full subgroup metadata, and is typically issued when
 * the user scrolls or sorts the community subgroup list. The reply is modelled
 * by {@link QuerySubgroupParticipantCountMexResponse}.
 *
 * @implNote This implementation accepts the GraphQL {@code input} variable as a
 * single opaque pre-serialised JSON string rather than modelling its inner
 * shape ({@code group_jid}, {@code query_context}, {@code sub_group_jid_hint}).
 * Callers serialise the input themselves and pass the resulting JSON; the field
 * is dropped from the wire payload when {@code null}.
 */
public final class QuerySubgroupParticipantCountMexRequest implements MexStanza.Request.Json {
    /**
     * Compiled GraphQL query identifier for the participant-count document.
     *
     * <p>The relay maps this id to its persisted operation; the GraphQL text
     * is never sent on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexQuerySubgroupParticipantCountJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "24079399904996141";

    /**
     * GraphQL operation name carried by this query.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexQuerySubgroupParticipantCountJobQuery.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "mexQuerySubgroupParticipantCount";

    /**
     * Pre-serialised GraphQL {@code input} variable, or {@code null} to omit
     * it.
     */
    private final String input;

    /**
     * Constructs a new request carrying the serialised input payload.
     *
     * <p>The WA Web {@code input} variable is a nested object of the shape
     * {@snippet lang = json:
     * {
     *   "group_jid": "...",
     *   "query_context": "...",
     *   "sub_group_jid_hint": "..."
     * }
     *}
     * Callers serialise this themselves and pass the resulting JSON string;
     * passing {@code null} omits the field entirely from the wire payload.
     *
     * @param input the serialised input variable, may be {@code null}
     */
    public QuerySubgroupParticipantCountMexRequest(String input) {
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
     * @implNote This implementation streams the GraphQL variables through
     * fastjson2's {@link JSONWriter} and emits the {@code input} field only
     * when the constructor argument is non-null, matching WA Web's pattern of
     * omitting undefined GraphQL variables. The envelope is built through
     * {@link MexStanza.Request.Json#createMexNode(String, String)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexQuerySubgroupParticipantCountJobQuery.graphql", exports = "params.id",
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
