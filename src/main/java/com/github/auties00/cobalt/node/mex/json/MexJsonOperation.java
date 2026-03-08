package com.github.auties00.cobalt.node.mex.json;

import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.mex.MexOperation;

/**
 * A non-sealed sub-interface of {@link MexOperation} for MEX operations that
 * use JSON-encoded payloads.
 *
 * <p>JSON MEX requests serialize their GraphQL variables as a JSON string
 * wrapped in the standard {@code {"variables": {...}}} envelope. The payload
 * is set as the text content of the {@code <query>} node. Responses are
 * parsed from the JSON bytes contained in the {@code <result>} child of the
 * response stanza.
 */
public non-sealed interface MexJsonOperation extends MexOperation {
    /**
     * Creates the MEX IQ stanza for a JSON-encoded query.
     *
     * @param queryId the numeric query identifier
     * @param jsonPayload the JSON string containing the serialized variables
     * @return the IQ {@link Node} ready to be sent
     */
    static Node createMexNode(String queryId, String jsonPayload) {
        var queryNode = new NodeBuilder()
                .description("query")
                .attribute("query_id", queryId)
                .content(jsonPayload)
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
