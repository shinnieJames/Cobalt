package com.github.auties00.cobalt.node.mex.json;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.mex.MexOperation;

import java.util.Optional;

/**
 * Base interface for MEX operations whose GraphQL variables and responses
 * are encoded as JSON strings.
 *
 * <p>JSON MEX requests serialise their GraphQL variables under the standard
 * {@code {"variables": {...}}} envelope and attach that string as the text
 * content of a {@code <query>} element. The request is then wrapped in an
 * IQ stanza with the {@code w:mex} namespace. Responses arrive as an IQ
 * {@code <result>} child whose text content carries the JSON reply produced
 * by the WhatsApp relay server.
 *
 * <p>This is the default encoding used by the vast majority of WA Web MEX
 * jobs, including newsletter management, username administration, group
 * metadata queries and community subgroup operations.
 *
 * @implNote WAWebMexClient, WAWebMexNativeClient: every WA Web job ending in
 * {@code Job} or {@code JobMutation} that depends on {@code WAWebMexClient}
 * ultimately calls {@code fetchQuery(queryDef, variables)} which serialises
 * the variables as JSON before dispatching through
 * {@code WAWebMexNativeClient}. Cobalt collapses the same flow into a shared
 * {@link #createMexNode(String, String)} helper invoked from every concrete
 * {@code Request.toNode()} implementation.
 */
@WhatsAppWebModule(moduleName = "WAWebMexClient")
@WhatsAppWebModule(moduleName = "WAWebMexNativeClient")
@WhatsAppWebModule(moduleName = "WAWebMexGetTypename")
public non-sealed interface MexJsonOperation extends MexOperation {
    /**
     * Builds the MEX IQ stanza that wraps a JSON-encoded GraphQL query.
     *
     * <p>The returned {@link NodeBuilder} is not yet built so callers can
     * attach additional attributes before the stanza is dispatched.
     *
     * @implNote WAWebMexRelayEnvironment.sendMexIq: the canonical WA Web
     * transport call constructs
     * {@code wap("iq", {id: generateId(), to: S_WHATSAPP_NET, type: "get",
     * xmlns: "w:mex"}, WapNode("query", {query_id: CUSTOM_STRING(t)},
     * Binary.build(JSON.stringify(e)).readByteArrayView()))}. Cobalt emits the
     * same stanza shape: identical {@code xmlns="w:mex"}, {@code to=s.whatsapp.net},
     * {@code type="get"} attributes on the outer IQ, and a single
     * {@code <query query_id="...">} child carrying the JSON envelope. The
     * caller-side {@code id} attribute is injected by
     * {@code WhatsAppClient.sendNode} when missing, mirroring WA Web's
     * {@code generateId()} call.
     * @implNote WAWebMexRelayEnvironment.sendMexIq: WA Web passes the JSON
     * payload as a {@code Uint8Array} (via {@code Binary.build(...).
     * readByteArrayView()}). Cobalt accepts a {@code String} here because the
     * WAWap encoder produces identical wire bytes for either representation:
     * the JSON envelope contains characters outside the dictionary and
     * nibble/hex packed alphabets, so {@code writeString} falls through to
     * the same {@code writeBinary(utf8Length) + arraycopy} path that
     * {@code writeBytes} would take. The two code paths are wire-equivalent.
     * @param queryId the numeric query identifier assigned to the compiled
     *                GraphQL operation by the WA relay
     * @param jsonPayload the JSON string containing the serialised
     *                    {@code {"variables": ...}} envelope
     * @return a {@link NodeBuilder} prepared for the IQ stanza; callers may
     *         still mutate attributes before building
     */
    @WhatsAppWebExport(moduleName = "WAWebMexClient", exports = "fetchQuery",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebMexNativeClient", exports = "fetchQuery",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static NodeBuilder createMexNode(String queryId, String jsonPayload) {
        // WAWebMexRelayEnvironment.sendMexIq: WapNode("query", {query_id: CUSTOM_STRING(t)},
        // Binary.build(JSON.stringify(e)).readByteArrayView())
        var queryNode = new NodeBuilder()
                .description("query")
                .attribute("query_id", queryId)
                .content(jsonPayload)
                .build();

        // WAWebMexRelayEnvironment.sendMexIq: wap("iq", {id, to: S_WHATSAPP_NET,
        // type: "get", xmlns: "w:mex"}, queryNode) - id is added by sendNode when missing
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:mex")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(queryNode);
    }

    /**
     * Returns the GraphQL {@code __typename} discriminator carried by a MEX
     * response object, if any.
     *
     * <p>MEX responses are GraphQL payloads whose concrete shape is identified
     * by the synthetic {@code __typename} field injected by the relay. For
     * example, the {@code xwa2_group_query_by_id} envelope carries one of
     * {@code "XWA2Group"}, {@code "XWA2CommunityGroup"},
     * {@code "XWA2CommunityDefaultSubGroup"} or {@code "XWA2CommunitySubGroup"}
     * to distinguish standalone groups from community parents and subgroups.
     * Callers use the returned tag to branch on the underlying entity type
     * before projecting the rest of the payload into a domain model.
     *
     * <p>The helper is null-safe: when the response object is missing or does
     * not carry a {@code __typename} field, it returns {@link Optional#empty()}
     * to mirror the {@code obj?.__typename} optional-chaining semantics used
     * in the original JavaScript helper.
     *
     * @implNote WAWebMexGetTypename.getTypename: {@code function e(e){return
     * e==null?void 0:e.__typename}}. Cobalt exposes the same null-safe
     * property read as an {@link Optional} accessor so that consumers can use
     * standard {@code Optional} pattern matching instead of checking for
     * {@code null} manually.
     * @param obj the MEX response JSON object to inspect; may be {@code null}
     * @return an {@link Optional} wrapping the {@code __typename} string, or
     *         {@link Optional#empty()} if {@code obj} is {@code null} or does
     *         not expose that field
     */
    @WhatsAppWebExport(moduleName = "WAWebMexGetTypename", exports = "getTypename",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<String> getTypename(JSONObject obj) {
        // WAWebMexGetTypename.getTypename: return obj==null ? void 0 : obj.__typename
        if (obj == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(obj.getString("__typename"));
    }
}
