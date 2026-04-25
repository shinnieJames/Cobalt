package com.github.auties00.cobalt.node.mex;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.mex.argo.MexArgoOperation;
import com.github.auties00.cobalt.node.mex.json.MexJsonOperation;

/**
 * A sealed base interface for all MEX (Media Exchange) operations.
 *
 * <p>MEX operations are WhatsApp's GraphQL-over-XMPP transport for structured
 * queries and mutations. Each concrete MEX type in Cobalt corresponds to a
 * single WA Web {@code WAWebMex...Job} module: a request payload is built from
 * GraphQL variables, wrapped in a {@code <query>} node, and sent as an IQ
 * stanza with the {@code w:mex} namespace. The server replies with a
 * {@code <result>} node whose contents are JSON or Argo encoded, depending on
 * the operation's transport variant.
 *
 * <p>Each concrete operation is itself a sealed interface that permits two
 * records:
 * <ul>
 * <li>{@code Request} with a {@code toNode()} method that serialises the
 * GraphQL variables and builds the outgoing IQ stanza
 * <li>{@code Response} with a {@code of(Node)} factory that parses the
 * server reply into typed fields
 * </ul>
 *
 * <p>This root interface permits the two transport variants used by WA Web:
 * <ul>
 * <li>{@link MexJsonOperation} for JSON-encoded payloads, the default for
 * most queries and mutations
 * <li>{@link MexArgoOperation} for binary Argo-encoded payloads, used by a
 * small number of performance-sensitive queries
 * </ul>
 *
 * @implNote WAWebMexClient.fetchQuery: in WA Web every MEX job module invokes
 * {@code o("WAWebMexClient").fetchQuery(queryDef, variables)} which
 * delegates to {@code WAWebMexNativeClient.fetchQuery}. Cobalt flattens this
 * into a per-operation sealed interface: the {@code Request.toNode()} method
 * on every concrete operation mirrors the WA Web call site by emitting the
 * same {@code iq} stanza, while the {@code Response.of(Node)} factory mirrors
 * WA Web's inline response destructuring.
 * @implNote WAWebMexClient.graphql: the {@code graphql} export re-exported
 * from {@code WAWebRelayClient} is a Relay compile-time tag function whose
 * runtime body is {@code throw err("Invariant Violation")}. It exists only so
 * the Relay compiler can identify GraphQL template literals during the build;
 * the bundle replaces every {@code graphql`...`} call site with a compiled
 * query object before shipping. Cobalt does not use Relay and instead
 * persists pre-compiled {@code query_id} constants on each concrete MEX
 * operation, so this tag function has no Java counterpart by design.
 */
@WhatsAppWebModule(moduleName = "WAWebMexClient")
public sealed interface MexOperation permits MexJsonOperation, MexArgoOperation {
}
