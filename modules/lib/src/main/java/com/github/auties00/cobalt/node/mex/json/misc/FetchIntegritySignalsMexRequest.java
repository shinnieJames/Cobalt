package com.github.auties00.cobalt.node.mex.json.misc;

import com.alibaba.fastjson2.JSON;
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
import java.util.Objects;
import java.util.Optional;

/**
 * Outbound MEX query that fetches first-message-experience (FMX) integrity
 * signals for a target user, returning whether the account is new and
 * whether starting a chat with them is considered suspicious.
 *
 * @apiNote Issued by WA Web's
 * {@code WAWebFetchAndSetIntegritySignals.fetchAndSetIntegritySignals}
 * before the user is allowed to open a chat with an unfamiliar contact; the
 * resulting {@code isSenderNewAccount} and {@code isSenderSuspicious}
 * flags drive the FMX safety nudges shown above the message composer. WA
 * Web wraps the call in a 600 ms {@code promiseTimeout} and propagates
 * timeouts and errors through the {@code WALogger} pipeline; Cobalt's
 * caller chooses its own timeout policy.
 *
 * @implNote This implementation hard-codes {@code use_case = "CHAT_FMX"}
 * and {@code telemetry.context = "INTERACTIVE"} to match the only
 * call-site WA Web emits today. The {@code query_input} array is shaped as
 * a single-entry list so the wire format stays compatible with the
 * batched form, even though only one JID is ever requested.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchIntegritySignals")
public final class FetchIntegritySignalsMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the
     * {@code WAWebMexFetchIntegritySignalsQuery} document.
     *
     * @apiNote Mirrors the {@code params.id} value baked into
     * {@code WAWebMexFetchIntegritySignalsQuery.graphql}. The relay maps the
     * id to a server-side persisted operation and never sees the GraphQL
     * text on the wire.
     */
    public static final String QUERY_ID = "26438847999065394";

    /**
     * GraphQL operation name reported to
     * {@code MexPerfTracker.setOperationName} when this query is dispatched.
     *
     * @apiNote Used by WA Web's MEX perf tracker to tag the query in
     * latency and error metrics; Cobalt keeps the name on the request for
     * embedders mirroring WA Web's telemetry surface.
     */
    public static final String OPERATION_NAME = "fetchIntegritySignals";

    /**
     * The target user JID bound to the {@code query_input[0].jid} GraphQL
     * variable.
     */
    private final Jid userJid;

    /**
     * Constructs a new request targeting the given user.
     *
     * @apiNote The JID must address an individual user (the FMX surface
     * never queries groups or broadcasts); WA Web emits the call through
     * {@code WAWebWidFactory.asUserWidOrThrow}. Embedders typically issue
     * the request as the user opens a chat for the first time with a
     * recipient not already in their contact list.
     *
     * @param userJid the target user JID, must not be {@code null}
     * @throws NullPointerException if {@code userJid} is {@code null}
     */
    public FetchIntegritySignalsMexRequest(Jid userJid) {
        this.userJid = Objects.requireNonNull(userJid, "userJid cannot be null");
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
     * fastjson2's {@link JSONWriter}, packs the lone {@link Jid} into the
     * single-element {@code query_input} array, fixes the FMX
     * {@code use_case} discriminator at {@code "CHAT_FMX"} and the
     * telemetry {@code context} at {@code "INTERACTIVE"}, then wraps the
     * payload via
     * {@link MexOperation.Request.Json#createMexNode(String, String)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchIntegritySignals", exports = "fetchIntegritySignals",
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
            writer.writeString(userJid.toString());
            writer.writeName("integrity_signals");
            writer.writeColon();
            writer.startObject();
            writer.writeName("use_case");
            writer.writeColon();
            writer.writeString("CHAT_FMX");
            writer.endObject();
            writer.endObject();
            writer.endArray();

            writer.writeName("telemetry");
            writer.writeColon();
            writer.startObject();
            writer.writeName("context");
            writer.writeColon();
            writer.writeString("INTERACTIVE");
            writer.endObject();

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
