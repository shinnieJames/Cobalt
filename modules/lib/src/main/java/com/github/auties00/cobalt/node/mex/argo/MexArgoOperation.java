package com.github.auties00.cobalt.node.mex.argo;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.mex.MexOperation;

/**
 * Base interface for MEX operations whose GraphQL variables and responses
 * are encoded with the Argo binary format rather than JSON.
 *
 * <p>Argo is a compact binary wire format used by a small number of
 * performance-sensitive MEX endpoints. The transport envelope is identical
 * to the JSON variant: an IQ stanza with the {@code w:mex} namespace wrapping
 * a {@code <query>} node tagged with {@code query_id}. Only the body of the
 * query and the server reply differ, carrying raw Argo-encoded bytes.
 *
 * @implNote WAWebMexClient, WAWebMexNativeClient: this interface mirrors the
 * Argo path through {@code WAWebMexClient.fetchQuery} / {@code
 * WAWebMexNativeClient.fetchQuery}. In WA Web the transport choice is driven
 * by the GraphQL query metadata; in Cobalt it is expressed as a separate
 * sealed sub-hierarchy. The current WA Web snapshot
 * ({@code WAWebMexNativeClient}, {@code WAWebMexRelayEnvironment.sendMexIq})
 * dispatches every MEX operation through the JSON path - the Argo branch is
 * preserved here as a forward-looking ADAPTED extension that emits an
 * identical outer IQ envelope so it can be enabled without further changes
 * if WA Web introduces an Argo-encoded MEX endpoint.
 */
@WhatsAppWebModule(moduleName = "WAWebMexClient")
@WhatsAppWebModule(moduleName = "WAWebMexNativeClient")
public non-sealed interface MexArgoOperation extends MexOperation {
    /**
     * Builds the MEX IQ stanza that wraps an Argo-encoded GraphQL query.
     *
     * <p>The returned {@link NodeBuilder} is not yet built so callers can
     * attach additional attributes before the stanza is dispatched.
     *
     * @implNote WAWebMexRelayEnvironment.sendMexIq: the canonical WA Web
     * transport call constructs
     * {@code wap("iq", {id: generateId(), to: S_WHATSAPP_NET, type: "get",
     * xmlns: "w:mex"}, WapNode("query", {query_id: CUSTOM_STRING(t)}, bytes))}.
     * Cobalt emits the same stanza shape with the Argo payload occupying the
     * same byte-array body slot the JSON variant uses for its UTF-8 envelope;
     * the outer IQ attributes are byte-for-byte identical.
     * @param queryId the numeric query identifier assigned to the compiled
     *                GraphQL operation by the WA relay
     * @param argoPayload the Argo-encoded GraphQL variables
     * @return a {@link NodeBuilder} prepared for the IQ stanza; callers may
     *         still mutate attributes before building
     */
    @WhatsAppWebExport(moduleName = "WAWebMexClient", exports = "fetchQuery",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebMexNativeClient", exports = "fetchQuery",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static NodeBuilder createMexNode(String queryId, byte[] argoPayload) {
        // WAWebMexRelayEnvironment.sendMexIq: WapNode("query", {query_id: CUSTOM_STRING(t)},
        // <argoPayload bytes>) - identical to JSON variant but with raw Argo bytes
        var queryNode = new NodeBuilder()
                .description("query")
                .attribute("query_id", queryId)
                .content(argoPayload)
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
}
