package com.github.auties00.cobalt.node.mex;

import com.github.auties00.cobalt.node.mex.argo.MexArgoOperation;
import com.github.auties00.cobalt.node.mex.json.MexJsonOperation;

/**
 * A sealed base interface for all MEX (Media Exchange) operations.
 *
 * <p>MEX operations are WhatsApp's GraphQL-over-XMPP relay protocol for
 * structured queries and mutations. Each operation is a sealed interface
 * that permits a {@code Request} record (with a {@code toNode()} method)
 * and a {@code Response} record (with an {@code of(Node)} factory).
 *
 * <p>This interface permits two transport variants:
 * <ul>
 * <li>{@link MexJsonOperation} — JSON-encoded payloads
 * <li>{@link MexArgoOperation} — binary Argo-encoded payloads
 * </ul>
 */
public sealed interface MexOperation permits MexJsonOperation, MexArgoOperation {
}
