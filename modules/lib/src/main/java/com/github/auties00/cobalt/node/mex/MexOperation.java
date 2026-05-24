package com.github.auties00.cobalt.node.mex;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.util.Optional;

/**
 * Sealed root of every MEX (Media Exchange) operation modelled in Cobalt.
 *
 * <p>MEX is WhatsApp Web's GraphQL-over-XMPP transport. Each operation is
 * dispatched through {@code WAWebMexClient.fetchQuery}, which forwards to
 * {@code WAWebMexNativeClient.fetchQuery}, which in turn calls
 * {@code WAWebMexRelayEnvironment.fetchFunc} to wrap the GraphQL variables in
 * the canonical {@code <iq xmlns="w:mex"><query query_id="..."/></iq>} stanza
 * routed to {@link JidServer#user()} ({@code s.whatsapp.net}).
 *
 * <p>Cobalt collapses each WA Web {@code WAWeb<Name>Job} pair (the GraphQL
 * document plus the dispatcher wrapper) into a sealed operation that permits
 * exactly two variants, {@link Request} and {@link Response}. Concrete
 * operations further pick a payload encoding by implementing
 * {@link Request.Json} or {@link Request.Argo} on the request side and the
 * matching {@link Response.Json} or {@link Response.Argo} on the response
 * side.
 */
@WhatsAppWebModule(moduleName = "WAWebMexClient")
public sealed interface MexOperation permits MexOperation.Request, MexOperation.Response {
    /**
     * Outbound side of a MEX operation.
     *
     * @apiNote Every concrete operation's {@code Request} variant implements
     * either {@link Json} or {@link Argo} depending on the payload encoding.
     * Callers do not implement this type directly; they instantiate one of the
     * generated {@code *MexRequest} classes and pass it to the MEX dispatcher.
     */
    sealed interface Request extends MexOperation {
        /**
         * Returns the compiled GraphQL query identifier the WhatsApp relay
         * uses to look up the persisted document for this operation.
         *
         * @apiNote Mirrors the {@code params.id} field of the compiled GraphQL
         * document module (for example
         * {@code WAWebQueryCatalogQuery.graphql}). The relay maps the id to a
         * server-side persisted operation and never sees the GraphQL text.
         *
         * @return the GraphQL query identifier, never {@code null}
         */
        String id();

        /**
         * Returns the GraphQL operation name reported to
         * {@code MexPerfTracker.setOperationName} when the operation is
         * dispatched.
         *
         * @apiNote Used by WA Web's MEX perf tracker to tag query latency and
         * error metrics; Cobalt keeps the name on each request so that
         * embedders mirroring WA Web's telemetry surface can emit the same
         * tag.
         *
         * @return the GraphQL operation name, never {@code null}
         */
        String name();

        /**
         * Builds the outbound MEX IQ stanza for this request.
         *
         * @apiNote Implementations serialise their GraphQL variables (JSON for
         * {@link Json}, Argo-encoded bytes for {@link Argo}) and wrap them via
         * the corresponding {@link Json#createMexNode(String, String)} or
         * {@link Argo#createMexNode(String, byte[])} helper. Callers may
         * further mutate the returned {@link NodeBuilder} (for example to set
         * a custom IQ id) before calling {@link NodeBuilder#build()}.
         *
         * @return the outbound stanza builder, never {@code null}
         */
        NodeBuilder toNode();

        /**
         * Marker for MEX requests whose GraphQL variables are JSON-encoded.
         *
         * @apiNote JSON is the encoding used by the entire MEX surface
         * currently mapped in Cobalt, including catalog, order, product
         * collection, community subgroup and ownership-transfer queries. New
         * operations should default to this variant unless they are paired
         * with a binary {@code WAWeb*ArgoQuery} document.
         */
        @WhatsAppWebModule(moduleName = "WAWebMexClient")
        @WhatsAppWebModule(moduleName = "WAWebMexNativeClient")
        @WhatsAppWebModule(moduleName = "WAWebMexRelayEnvironment")
        non-sealed interface Json extends Request {
            /**
             * Builds the {@code <iq xmlns="w:mex">} envelope that wraps a
             * JSON-encoded GraphQL query.
             *
             * @apiNote Used by every {@link Json} implementation to produce
             * the wire-level stanza. The {@code query_id} attribute is the
             * compiled-document id, the body is the {@code {"variables": ...}}
             * JSON string, and the envelope is addressed to
             * {@link JidServer#user()} with {@code type="get"}.
             *
             * @implNote This implementation mirrors WA Web's
             * {@code WAWebMexRelayEnvironment.sendMexIq} envelope shape but
             * keeps the JSON payload as a {@link String} (WA Web wraps it in a
             * {@code WABinary.Binary} byte view before calling
             * {@code WapNode}); Cobalt's {@link NodeBuilder} accepts the
             * {@link String} directly.
             *
             * @param queryId     the numeric query identifier assigned to the
             *                    compiled GraphQL operation
             * @param jsonPayload the JSON string containing the serialised
             *                    {@code variables} envelope
             * @return a {@link NodeBuilder} prepared for the IQ stanza,
             *         callers may still mutate attributes before building
             */
            @WhatsAppWebExport(moduleName = "WAWebMexClient", exports = "fetchQuery",
                    adaptation = WhatsAppAdaptation.ADAPTED)
            @WhatsAppWebExport(moduleName = "WAWebMexNativeClient", exports = "fetchQuery",
                    adaptation = WhatsAppAdaptation.ADAPTED)
            static NodeBuilder createMexNode(String queryId, String jsonPayload) {
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
                        .content(queryNode);
            }
        }

        /**
         * Marker for MEX requests whose GraphQL variables are encoded with the
         * Argo binary format rather than JSON.
         *
         * @apiNote The wire envelope is identical to {@link Json}: a
         * {@code <iq xmlns="w:mex">} stanza wrapping a {@code <query>} node
         * tagged with {@code query_id}; only the body bytes differ. No MEX
         * operation currently surfaced by Cobalt uses this encoding, but the
         * variant is kept so the sealed hierarchy stays open for future
         * {@code WAWeb*ArgoQuery} document modules.
         */
        @WhatsAppWebModule(moduleName = "WAWebMexClient")
        @WhatsAppWebModule(moduleName = "WAWebMexNativeClient")
        @WhatsAppWebModule(moduleName = "WAWebMexRelayEnvironment")
        non-sealed interface Argo extends Request {
            /**
             * Builds the {@code <iq xmlns="w:mex">} envelope that wraps an
             * Argo-encoded GraphQL query.
             *
             * @apiNote Used by future {@link Argo} implementations to produce
             * the wire-level stanza. The {@code query_id} attribute is the
             * compiled-document id, the body is the Argo-encoded
             * {@code variables} payload, and the envelope is addressed to
             * {@link JidServer#user()} with {@code type="get"}.
             *
             * @implNote This implementation mirrors the JSON variant's
             * envelope shape; only the body content type differs.
             *
             * @param queryId     the numeric query identifier assigned to the
             *                    compiled GraphQL operation
             * @param argoPayload the Argo-encoded GraphQL variables
             * @return a {@link NodeBuilder} prepared for the IQ stanza,
             *         callers may still mutate attributes before building
             */
            @WhatsAppWebExport(moduleName = "WAWebMexClient", exports = "fetchQuery",
                    adaptation = WhatsAppAdaptation.ADAPTED)
            @WhatsAppWebExport(moduleName = "WAWebMexNativeClient", exports = "fetchQuery",
                    adaptation = WhatsAppAdaptation.ADAPTED)
            static NodeBuilder createMexNode(String queryId, byte[] argoPayload) {
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
                        .content(queryNode);
            }
        }
    }

    /**
     * Inbound side of a MEX operation.
     *
     * @apiNote Every concrete operation's {@code Response} variant implements
     * either {@link Json} or {@link Argo} depending on the payload encoding.
     * The interface carries no abstract methods; it exists purely as the
     * closed counterpart of {@link Request} so the entire MEX surface can be
     * reasoned about as a single sealed hierarchy.
     */
    sealed interface Response extends MexOperation {
        /**
         * Marker for MEX responses whose payloads decode from JSON.
         *
         * @apiNote Carries the shared {@link #getTypename(JSONObject)} helper
         * for branching on the GraphQL {@code __typename} discriminator
         * injected by the relay; concrete responses pull the field through
         * this helper before projecting the payload onto a domain model.
         */
        @WhatsAppWebModule(moduleName = "WAWebMexGetTypename")
        non-sealed interface Json extends Response {
            /**
             * Returns the GraphQL {@code __typename} discriminator carried by
             * a MEX response object, if any.
             *
             * @apiNote MEX responses are GraphQL payloads whose concrete shape
             * is identified by the synthetic {@code __typename} field injected
             * by the relay. For example the {@code xwa2_group_query_by_id}
             * envelope carries one of {@code "XWA2Group"},
             * {@code "XWA2CommunityGroup"},
             * {@code "XWA2CommunityDefaultSubGroup"} or
             * {@code "XWA2CommunitySubGroup"} so callers can branch on the
             * underlying entity before projecting the rest of the payload
             * into a domain model. Surfaced from
             * {@code WAWebMexFetchGroupInfoJob} and
             * {@code WAWebMexFetchGroupInfoIncludBotsJob} in WA Web.
             *
             * @implNote This implementation mirrors WA Web's
             * {@code obj?.__typename} optional-chaining semantics: a
             * {@code null} {@code obj} or a missing field both collapse to
             * {@link Optional#empty()}.
             *
             * @param obj the MEX response JSON object to inspect, may be
             *            {@code null}
             * @return an {@link Optional} wrapping the {@code __typename}
             *         string, or {@link Optional#empty()} if {@code obj} is
             *         {@code null} or does not expose that field
             */
            @WhatsAppWebExport(moduleName = "WAWebMexGetTypename", exports = "getTypename",
                    adaptation = WhatsAppAdaptation.ADAPTED)
            static Optional<String> getTypename(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }
                return Optional.ofNullable(obj.getString("__typename"));
            }
        }

        /**
         * Marker for MEX responses whose payloads decode from Argo binary.
         *
         * @apiNote Companion to {@link Request.Argo} on the inbound side. No
         * MEX response currently surfaced by Cobalt uses this encoding; the
         * variant is kept to leave the sealed hierarchy open for future
         * Argo-encoded responses.
         */
        non-sealed interface Argo extends Response {
        }
    }

}
