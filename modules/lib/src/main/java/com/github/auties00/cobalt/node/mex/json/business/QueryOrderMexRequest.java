package com.github.auties00.cobalt.node.mex.json.business;

import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.mex.MexOperation;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;

/**
 * Outbound MEX request that fetches the detail of a business order.
 *
 * @apiNote Drives the order-detail surface for customers viewing or
 * interacting with a business order. An order is produced when a customer
 * submits an {@code OrderMessage} to a business contact; the business reply
 * carries a sensitive base64 token that authenticates subsequent order
 * look-ups. Surfaced from
 * {@code WAWebBizOrderBridge.queryOrder} via
 * {@code WAWebBizOrderAction.queryOrder}.
 *
 * @implNote This implementation only models WA Web's GraphQL path
 * ({@code WAWebBizQueryOrderJob.queryOrder} when
 * {@code WAWebBizGatingUtils.graphQLForGetOrderInfoEnabled()} is on). The
 * legacy {@code fb:thrift_iq} fallback (the {@code WAWap.wap("order", ...)}
 * Smax IQ) is intentionally not implemented; Cobalt assumes the GraphQL gate
 * is enabled. Cobalt also omits the {@code direct_connection_encrypted_info}
 * variable used by the business direct-connection retry loop.
 */
@WhatsAppWebModule(moduleName = "WAWebBizQueryOrderJob")
@WhatsAppWebModule(moduleName = "WAWebBizQueryOrderJobQuery.graphql")
public final class QueryOrderMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the
     * {@code WAWebBizQueryOrderJobQuery} document.
     *
     * @apiNote Mirrors the {@code params.id} value baked into
     * {@code WAWebBizQueryOrderJobQuery.graphql}. The relay maps this id to
     * its persisted operation; the GraphQL text is never sent on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizQueryOrderJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "26593811266898374";

    /**
     * GraphQL operation name reported to
     * {@code MexPerfTracker.setOperationName} when this query is dispatched.
     *
     * @apiNote Used by WA Web's MEX perf tracker to tag the query in latency
     * and error metrics; Cobalt keeps the name on the request for embedders
     * mirroring WA Web's telemetry surface.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizQueryOrderJob", exports = "queryOrder",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "queryOrder";

    private final String userJid;
    private final String orderId;
    private final String tokenBase64;
    private final int imageWidth;
    private final int imageHeight;

    /**
     * Creates a new order query request.
     *
     * @apiNote The {@code userJid} mirrors WA Web's
     * {@code WAWebUserPrefsMeUser.getMePnUserOrThrow_DO_NOT_USE().toString()}:
     * it is the logged-in user's phone-number JID stringified, not the
     * business JID. {@code tokenBase64} is the sensitive token returned by
     * the business with the order message and must be replayed verbatim;
     * leaking it lets a third party read the order.
     *
     * @param userJid     the logged-in user JID stringified via
     *                    {@code toString()}, mirroring the WA Web
     *                    {@code WAWebUserPrefsMeUser.getMePnUserOrThrow_DO_NOT_USE().toString()}
     * @param orderId     the server-issued order identifier, typically the
     *                    id of the {@code OrderMessage} carrying the order
     * @param tokenBase64 the sensitive base64-encoded token returned by the
     *                    business with the order message
     * @param imageWidth  the requested thumbnail width in pixels used when
     *                    the relay rewrites image URLs
     * @param imageHeight the requested thumbnail height in pixels
     */
    public QueryOrderMexRequest(String userJid, String orderId, String tokenBase64, int imageWidth, int imageHeight) {
        this.userJid = userJid;
        this.orderId = orderId;
        this.tokenBase64 = tokenBase64;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
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
     * fastjson2's {@link JSONWriter}, mirroring the
     * {@code {"variables": {"request": {"order": {...}}}}} shape expected by
     * {@code xwa_checkout_get_order_info}. The token is wrapped in the WA
     * Web {@code {"sensitive_string_value": ...}} envelope so the relay
     * scrubs it from server-side logs, and the {@code image_dimensions}
     * height and width are sent as JSON integers (not strings) matching the
     * GraphQL {@code Int} type.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizQueryOrderJob", exports = "queryOrder",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
            writer.writeName("request");
            writer.writeColon();
            writer.startObject();
            writer.writeName("order");
            writer.writeColon();
            writer.startObject();
            writer.writeName("jid");
            writer.writeColon();
            writer.writeString(userJid);
            writer.writeName("token");
            writer.writeColon();
            writer.startObject();
            writer.writeName("sensitive_string_value");
            writer.writeColon();
            writer.writeString(tokenBase64);
            writer.endObject();
            writer.writeName("id");
            writer.writeColon();
            writer.writeString(orderId);
            writer.writeName("image_dimensions");
            writer.writeColon();
            writer.startObject();
            writer.writeName("height");
            writer.writeColon();
            writer.writeInt32(imageHeight);
            writer.writeName("width");
            writer.writeColon();
            writer.writeInt32(imageWidth);
            writer.endObject();
            writer.endObject();
            writer.endObject();
            writer.endObject();
            writer.endObject();
            try (var output = new StringWriter()) {
                writer.flushTo(output);
                return MexOperation.Request.Json.createMexNode(QUERY_ID, output.toString());
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
