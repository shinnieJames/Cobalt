package com.github.auties00.cobalt.node.mex.json.business;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.order.BusinessOrder;
import com.github.auties00.cobalt.model.business.order.BusinessOrderBuilder;
import com.github.auties00.cobalt.model.business.order.BusinessOrderItem;
import com.github.auties00.cobalt.model.business.order.BusinessOrderItemBuilder;
import com.github.auties00.cobalt.model.business.order.BusinessOrderItemProperty;
import com.github.auties00.cobalt.model.business.order.BusinessOrderItemPropertyBuilder;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.mex.MexOperation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parsed response of the {@code queryOrder} MEX query.
 *
 * <p>Carries the {@code xwa_checkout_get_order_info.order} projection mapped
 * onto a Cobalt {@link BusinessOrder} with its nested {@link BusinessOrderItem}
 * list, decoded from {@link QueryOrderMexRequest} replies and ready to be
 * displayed in an order-detail view.
 *
 * @implNote This implementation only models the GraphQL response shape; the
 * legacy {@code fb:thrift_iq} reply parsed by WA Web's
 * {@code WAWebBizQueryOrderJob.queryOrderResponse} {@code WapParser} is
 * intentionally not implemented (see {@link QueryOrderMexRequest}). The
 * {@code tax} field, present in WA Web's combined return shape, is dropped
 * because the GraphQL document ({@code WAWebBizQueryOrderJobQuery.graphql})
 * does not project it.
 */
@WhatsAppWebModule(moduleName = "WAWebBizQueryOrderJob")
public final class QueryOrderMexResponse implements MexOperation.Response.Json {
    /**
     * Holds the structured order detail.
     */
    private final BusinessOrder order;

    /**
     * Constructs a parsed order response.
     *
     * <p>Instances are produced by {@link #of(Node)} after parsing the inbound
     * IQ payload.
     *
     * @param order the structured order detail
     */
    private QueryOrderMexResponse(BusinessOrder order) {
        this.order = order;
    }

    /**
     * Parses the MEX response carried by an inbound IQ stanza.
     *
     * <p>Entry point for receivers handling {@code <iq xmlns="w:mex">} replies
     * tagged with {@link QueryOrderMexRequest#QUERY_ID}; unwraps the
     * {@code <result>} child, reads its content bytes and decodes the GraphQL
     * JSON envelope.
     *
     * @param node the inbound IQ stanza carrying the {@code <result>} child
     * @return the parsed response, or empty if the expected JSON shape is
     *         absent
     */
    @WhatsAppWebExport(moduleName = "WAWebBizQueryOrderJob", exports = "queryOrder",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<QueryOrderMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(QueryOrderMexResponse::of);
    }

    /**
     * Returns the parsed order detail.
     *
     * <p>The returned {@link BusinessOrder} is the projection of the GraphQL
     * {@code order} object onto the Cobalt domain model; its nested item list
     * is the projection of the {@code products} array.
     *
     * @return the order, never {@code null}
     */
    public BusinessOrder order() {
        return order;
    }

    /**
     * Parses the raw JSON bytes of the {@code <result>} child.
     *
     * <p>Invoked only via the {@link #of(Node)} entry point after unwrapping
     * the IQ stanza.
     *
     * @implNote This implementation matches WA Web's empty-result fallback: a
     * reply where {@code xwa_checkout_get_order_info} is {@code null} or its
     * {@code order} child is missing collapses to {@link Optional#empty()},
     * mirroring the {@code ServerStatusCodeError(500)} WA Web throws on the
     * same condition. The {@code creation_time_stamp} field is a stringified
     * epoch-second value; failures to parse it leave the
     * {@link BusinessOrder#createdAt()} field {@code null}.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return the parsed response, or empty if the envelope is missing the
     *         expected fields
     */
    private static Optional<QueryOrderMexResponse> of(byte[] json) {
        var root = JSON.parseObject(json);
        if (root == null) {
            return Optional.empty();
        }
        var data = root.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }
        var getResult = data.getJSONObject("xwa_checkout_get_order_info");
        if (getResult == null) {
            return Optional.empty();
        }
        var orderObj = getResult.getJSONObject("order");
        if (orderObj == null) {
            return Optional.empty();
        }
        Long createdAt = null;
        var ts = orderObj.getString("creation_time_stamp");
        if (ts != null && !ts.isEmpty()) {
            try {
                createdAt = Long.parseLong(ts);
            } catch (NumberFormatException _) {
                createdAt = null;
            }
        }
        String currency = null;
        Long subtotal = null;
        Long total = null;
        var priceDetails = orderObj.getJSONObject("price_details");
        if (priceDetails != null) {
            currency = priceDetails.getString("currency");
            subtotal = parseLong(priceDetails.getString("subtotal_amount"));
            total = parseLong(priceDetails.getString("total_amount"));
        }
        var products = parseProducts(orderObj.getJSONArray("products"));
        var order = new BusinessOrderBuilder()
                .createdAt(createdAt == null ? null : Instant.ofEpochSecond(createdAt))
                .currency(currency)
                .subtotal(subtotal)
                .total(total)
                .items(products)
                .build();
        return Optional.of(new QueryOrderMexResponse(order));
    }

    /**
     * Parses an array of GraphQL product objects into a list of
     * {@link BusinessOrderItem} values.
     *
     * <p>Invoked once per response to project the {@code order.products} array
     * onto the Cobalt domain model.
     *
     * @implNote This implementation discards entries that
     * {@link #parseProduct(JSONObject)} rejects (missing {@code id} or
     * {@code name}) and wraps the result in
     * {@link List#copyOf(java.util.Collection)} so callers receive an
     * unmodifiable list.
     *
     * @param array the GraphQL products array, possibly {@code null}
     * @return the parsed items, never {@code null}
     */
    private static List<BusinessOrderItem> parseProducts(JSONArray array) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        var out = new ArrayList<BusinessOrderItem>(array.size());
        for (var i = 0; i < array.size(); i++) {
            parseProduct(array.getJSONObject(i)).ifPresent(out::add);
        }
        return List.copyOf(out);
    }

    /**
     * Parses a single GraphQL product object into a {@link BusinessOrderItem}.
     *
     * <p>Invoked by {@link #parseProducts(JSONArray)} once per array element.
     *
     * @implNote This implementation enforces the WA Web
     * {@code RequiredField}/{@code THROW} markers on {@code id} and
     * {@code name} (see the {@code WAWebBizQueryOrderJobQuery.graphql}
     * fragment) by skipping the entry when either is missing, rather than
     * throwing. Variant properties are projected through
     * {@link BusinessOrderItemPropertyBuilder} only when both {@code name} and
     * {@code value} are present; the thumbnail is taken from the first image's
     * {@code id} and {@code request_image_url} fields.
     *
     * @param obj the GraphQL product object, possibly {@code null}
     * @return the parsed item, or empty when {@code obj} is {@code null} or
     *         lacks the required {@code id} or {@code name} fields
     */
    private static Optional<BusinessOrderItem> parseProduct(JSONObject obj) {
        if (obj == null) {
            return Optional.empty();
        }
        var id = obj.getString("id");
        var name = obj.getString("name");
        if (id == null || name == null) {
            return Optional.empty();
        }
        var price = parseLong(obj.getString("price"));
        var currency = obj.getString("currency");
        var quantity = parseInt(obj.getString("quantity"));
        var properties = new ArrayList<BusinessOrderItemProperty>();
        var variantInfo = obj.getJSONObject("variant_info");
        if (variantInfo != null) {
            var variantProperties = variantInfo.getJSONArray("variant_properties");
            if (variantProperties != null) {
                for (var i = 0; i < variantProperties.size(); i++) {
                    var prop = variantProperties.getJSONObject(i);
                    if (prop == null) {
                        continue;
                    }
                    var pName = prop.getString("name");
                    var pValue = prop.getString("value");
                    if (pName != null && pValue != null) {
                        properties.add(new BusinessOrderItemPropertyBuilder()
                                .name(pName)
                                .value(pValue)
                                .build());
                    }
                }
            }
        }
        String thumbnailId = null;
        String thumbnailUrl = null;
        var media = obj.getJSONObject("media");
        if (media != null) {
            var images = media.getJSONArray("images");
            if (images != null && !images.isEmpty()) {
                var first = images.getJSONObject(0);
                if (first != null) {
                    thumbnailId = first.getString("id");
                    thumbnailUrl = first.getString("request_image_url");
                }
            }
        }
        return Optional.of(new BusinessOrderItemBuilder()
                .id(id)
                .name(name)
                .price(price)
                .currency(currency)
                .quantity(quantity)
                .thumbnailId(thumbnailId)
                .thumbnailUrl(thumbnailUrl)
                .properties(properties)
                .build());
    }

    /**
     * Parses a non-empty decimal string into a {@link Long}.
     *
     * <p>Used by the order-detail and item-detail projections; the WA Web
     * GraphQL schema reports monetary amounts as stringified decimals.
     *
     * @implNote This implementation returns {@code null} on a {@code null},
     * empty or non-numeric input rather than throwing, mirroring the WA Web
     * {@code parseInt(value, 10)} call where {@code NaN} is treated as a
     * missing value.
     *
     * @param value the value to parse, possibly {@code null}
     * @return the parsed long, or {@code null} when the input is missing or
     *         unparseable
     */
    private static Long parseLong(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException _) {
            return null;
        }
    }

    /**
     * Parses a non-empty decimal string into an {@link Integer}.
     *
     * <p>Used by the item-detail projection; the WA Web GraphQL schema reports
     * order quantities as stringified decimals.
     *
     * @implNote This implementation returns {@code null} on a {@code null},
     * empty or non-numeric input rather than throwing, matching the WA Web
     * {@code parseInt(value, 10)} call semantics.
     *
     * @param value the value to parse, possibly {@code null}
     * @return the parsed integer, or {@code null} when the input is missing or
     *         unparseable
     */
    private static Integer parseInt(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException _) {
            return null;
        }
    }
}
