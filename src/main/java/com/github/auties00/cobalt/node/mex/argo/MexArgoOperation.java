package com.github.auties00.cobalt.node.mex.argo;

import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.mex.MexOperation;

/**
 * A non-sealed sub-interface of {@link MexOperation} for MEX operations that
 * use binary Argo-encoded payloads.
 *
 * <p>Argo MEX requests use a compact binary encoding instead of JSON for their
 * GraphQL variables and responses.
 */
public non-sealed interface MexArgoOperation extends MexOperation {
    /**
     * Creates the MEX IQ stanza for an Argo-encoded query.
     *
     * @param queryId the numeric query identifier
     * @param argoPayload the binary Argo-encoded payload
     * @return the IQ {@link Node} ready to be sent
     */
    static Node createMexNode(String queryId, byte[] argoPayload) {
        var queryNode = new NodeBuilder()
                .description("query")
                .attribute("query_id", queryId)
                .content(argoPayload)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:mex")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(queryNode)
                .build();
    }
}
