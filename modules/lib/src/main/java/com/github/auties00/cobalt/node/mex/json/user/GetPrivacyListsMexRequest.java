package com.github.auties00.cobalt.node.mex.json.user;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.mex.MexOperation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedCollection;

/**
 * Builds the MEX IQ stanza that fetches a server-side privacy contact list.
 *
 * @apiNote Powers privacy-list refreshes for features such as the call
 * deny-list (see WA Web's
 * {@code WAWebQueryPrivacyDisallowedListMexJob.fetchDisallowedList}). The
 * {@code dhash} field carries the digest of the locally cached list and
 * lets the relay reply with a delta rather than the full roster when the
 * cache is current. Pair the dispatched stanza with
 * {@link GetPrivacyListsMexResponse} to consume the reply.
 *
 * @implNote This implementation embeds the full
 * {@code {jid, privacy_contact_list_type: {dhash, category, type}}}
 * shape inline. WA Web's mirror constructs it via
 * {@code {input: {query_input: [...]}}} from a typed {@code GetPrivacyListInput}
 * payload; Cobalt accepts the components individually and writes the same
 * wire shape.
 *
 * @see GetPrivacyListsMexResponse
 */
@WhatsAppWebModule(moduleName = "WAWebMexGetPrivacyList")
public final class GetPrivacyListsMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled-document id the relay maps to the persisted query.
     *
     * @apiNote Used as the {@code query_id} attribute of the outbound
     * {@code <query>} node. Matches the {@code params.id} field of
     * {@code WAWebMexGetPrivacyListsQuery.graphql} for the snapshot this
     * file was generated against.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexGetPrivacyListsQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "26806428515612550";

    /**
     * The GraphQL operation name reported alongside this request.
     *
     * @apiNote Mirrors {@code params.name} on
     * {@code WAWebMexGetPrivacyListsQuery.graphql}; WA Web tags the value to
     * {@code MexPerfTracker} for per-operation telemetry bucketing.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexGetPrivacyListsQuery.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "fetchPrivacyList";

    /**
     * The {@code jid} field of the {@code query_input[0]} entry.
     */
    private final Jid jid;

    /**
     * The {@code dhash} of the locally cached list, possibly {@code null}.
     */
    private final String dhash;

    /**
     * The {@code category} string identifying the privacy domain.
     */
    private final String category;

    /**
     * The {@code type} string identifying the allow/deny polarity.
     */
    private final String type;

    /**
     * Constructs a privacy-list fetch request.
     *
     * @apiNote {@code category} and {@code type} are WA enum tokens declared
     * elsewhere in the WA Web JS source (for example {@code "CALL"} +
     * {@code "DENYLIST"} for the call deny-list); both are forwarded
     * verbatim. Pass the empty string for {@code dhash} when fetching a
     * fresh list, or the previously observed {@link Jid} digest to receive a
     * delta refresh.
     *
     * @param jid the requesting user's JID
     * @param dhash the digest of the locally cached list, or {@code null}
     *              to omit the variable
     * @param category the privacy list domain, or {@code null} to omit the
     *                 variable
     * @param type the allow/deny polarity, or {@code null} to omit the
     *             variable
     * @throws NullPointerException if {@code jid} is {@code null}
     */
    public GetPrivacyListsMexRequest(Jid jid, String dhash, String category, String type) {
        this.jid = Objects.requireNonNull(jid, "jid cannot be null");
        this.dhash = dhash;
        this.category = category;
        this.type = type;
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
     * @implNote This implementation serialises
     * {@code {"variables": {"input": {"query_input": [{"jid": ..., "privacy_contact_list_type": {"dhash"?, "category"?, "type"?}}]}}}}
     * and defers envelope construction to
     * {@link MexOperation.Request.Json#createMexNode(String, String)}. The
     * three optional sub-fields are emitted only when non-{@code null};
     * {@code query_input} is always a single-element array since the
     * Cobalt API takes one {@link Jid} per request.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexGetPrivacyList", exports = "fetchPrivacyList",
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

            writer.writeName("query_input");
            writer.writeColon();
            writer.startArray();
            writer.startObject();
            writer.writeName("jid");
            writer.writeColon();
            writer.writeString(jid.toString());

            writer.writeName("privacy_contact_list_type");
            writer.writeColon();
            writer.startObject();
            if (dhash != null) {
                writer.writeName("dhash");
                writer.writeColon();
                writer.writeString(dhash);
            }
            if (category != null) {
                writer.writeName("category");
                writer.writeColon();
                writer.writeString(category);
            }
            if (type != null) {
                writer.writeName("type");
                writer.writeColon();
                writer.writeString(type);
            }
            writer.endObject();
            writer.endObject();
            writer.endArray();

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
