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
 * <p>An order is produced when a customer submits an {@code OrderMessage} to a
 * business contact; the business reply carries a sensitive base64 token that
 * authenticates subsequent order look-ups. The matching decoder is
 * {@link QueryOrderMexResponse}.
 *
 * @implNote This implementation only models WA Web's GraphQL path
 * ({@code WAWebBizQueryOrderJob.queryOrder} when
 * {@code WAWebBizGatingUtils.graphQLForGetOrderInfoEnabled()} is on) and
 * assumes that gate is enabled; the legacy {@code fb:thrift_iq} fallback (the
 * {@code WAWap.wap("order", ...)} Smax IQ) is intentionally not implemented.
 * Cobalt also omits the {@code direct_connection_encrypted_info} variable used
 * by the business direct-connection retry loop.
 */
@WhatsAppWebModule(moduleName = "WAWebBizQueryOrderJob")
@WhatsAppWebModule(moduleName = "WAWebBizQueryOrderJobQuery.graphql")
public final class QueryOrderMexRequest implements MexOperation.Request.Json {
    /**
     * Holds the compiled GraphQL query identifier for the
     * {@code WAWebBizQueryOrderJobQuery} document.
     *
     * <p>The relay maps this id to its persisted operation; the GraphQL text
     * is never sent on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizQueryOrderJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "26593811266898374";

    /**
     * Holds the GraphQL operation name reported when this query is dispatched.
     *
     * <p>WA Web's MEX perf tracker uses this name to tag the query in latency
     * and error metrics; Cobalt keeps it on the request for embedders
     * mirroring that telemetry surface.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizQueryOrderJob", exports = "queryOrder",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "queryOrder";

    /**
     * Holds the logged-in user JID, stringified; this is the user's
     * phone-number JID, not the business JID.
     */
    private final String userJid;

    /**
     * Holds the server-issued order identifier, typically the id of the
     * {@code OrderMessage} carrying the order.
     */
    private final String orderId;

    /**
     * Holds the sensitive base64-encoded token returned by the business with
     * the order message; it must be replayed verbatim, since leaking it lets a
     * third party read the order.
     */
    private final String tokenBase64;

    /**
     * Holds the requested thumbnail width in pixels used when the relay
     * rewrites image URLs.
     */
    private final int imageWidth;

    /**
     * Holds the requested thumbnail height in pixels used when the relay
     * rewrites image URLs.
     */
    private final int imageHeight;

    /**
     * Creates a new order query request.
     *
     * <p>The {@code userJid} is the logged-in user's phone-number JID
     * stringified (mirroring WA Web's
     * {@code WAWebUserPrefsMeUser.getMePnUserOrThrow_DO_NOT_USE().toString()}),
     * not the business JID. The {@code tokenBase64} is the sensitive token
     * returned by the business with the order message and must be replayed
     * verbatim; leaking it lets a third party read the order.
     *
     * @param userJid     the logged-in user JID stringified via
     *                    {@code toString()}
     * @param orderId     the server-issued order identifier, typically the id
     *                    of the {@code OrderMessage} carrying the order
     * @param tokenBase64 the sensitive base64-encoded token returned by the
     *                    business with the order message
     * @param imageWidth  the requested thumbnail width in pixels used when the
     *                    relay rewrites image URLs
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
     * {@code xwa_checkout_get_order_info}. The token is wrapped in the WA Web
     * {@code {"sensitive_string_value": ...}} envelope so the relay scrubs it
     * from server-side logs, and the {@code image_dimensions} height and width
     * are sent as JSON integers (not strings) matching the GraphQL {@code Int}
     * type.
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
