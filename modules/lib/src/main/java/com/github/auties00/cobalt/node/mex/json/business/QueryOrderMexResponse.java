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
 * Parsed response of the {@code queryOrder} MEX query carrying the
 * {@code xwa_checkout_get_order_info.order} projection mapped onto a Cobalt
 * {@link BusinessOrder} with its nested {@link BusinessOrderItem} list.
 */
@WhatsAppWebModule(moduleName = "WAWebBizQueryOrderJob")
public final class QueryOrderMexResponse implements MexOperation.Response.Json {
    private final BusinessOrder order;

    /**
     * Constructs a parsed order response.
     *
     * @param order the structured order detail
     */
    private QueryOrderMexResponse(BusinessOrder order) {
        this.order = order;
    }

    /**
     * Parses the MEX response carried by an inbound IQ stanza.
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
     * @return the order, never {@code null}
     */
    public BusinessOrder order() {
        return order;
    }

    /**
     * Parses the raw JSON bytes of the {@code <result>} child.
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
     * Parses a single GraphQL product object into a
     * {@link BusinessOrderItem}.
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
     * @param value the value to parse, possibly {@code null}
     * @return the parsed integer, or {@code null} when the input is missing
     *         or unparseable
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
