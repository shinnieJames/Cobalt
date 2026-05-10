package com.github.auties00.cobalt.node.mex.json.business;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.catalog.BusinessCatalogEntry;
import com.github.auties00.cobalt.model.business.catalog.BusinessCatalogEntryBuilder;
import com.github.auties00.cobalt.model.business.catalog.BusinessItemAvailability;
import com.github.auties00.cobalt.model.business.catalog.BusinessReviewStatus;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stateless utility that parses GraphQL product objects into Cobalt
 * {@link BusinessCatalogEntry} values.
 *
 * <p>The parsing logic mirrors WA Web's
 * {@code WAWebBizParseProductGraphql.parseProductGraphQL}, invoked from both
 * the catalog query and the product-collections query response decoders.
 * Cobalt centralises the projection here so that the two decoders share the
 * same field handling.
 */
@WhatsAppWebModule(moduleName = "WAWebBizParseProductGraphql")
@WhatsAppWebModule(moduleName = "WAWebBizParseProductGraphql_product.graphql")
public final class CatalogProductParser {
    /**
     * Prevents instantiation of this stateless utility class.
     *
     * @throws AssertionError always
     */
    private CatalogProductParser() {
        throw new AssertionError("No CatalogProductParser instances for you!");
    }

    /**
     * Parses an array of GraphQL product objects into a list of
     * {@link BusinessCatalogEntry} values.
     *
     * @param array the GraphQL products array, possibly {@code null}
     * @return the parsed entries, never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebBizParseProductGraphql", exports = "parseProductGraphQL",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static List<BusinessCatalogEntry> parseProducts(JSONArray array) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        var out = new ArrayList<BusinessCatalogEntry>(array.size());
        for (var i = 0; i < array.size(); i++) {
            parseProduct(array.getJSONObject(i)).ifPresent(out::add);
        }
        return List.copyOf(out);
    }

    /**
     * Parses a single GraphQL product object into a
     * {@link BusinessCatalogEntry}.
     *
     * @param obj the GraphQL product object, possibly {@code null}
     * @return the parsed entry, or empty when {@code obj} is {@code null}
     */
    private static Optional<BusinessCatalogEntry> parseProduct(JSONObject obj) {
        if (obj == null) {
            return Optional.empty();
        }
        var id = obj.getString("id");
        var retailerId = obj.getString("retailer_id");
        var name = obj.getString("name");
        var description = obj.getString("description");
        var urlString = obj.getString("url");
        URI url = null;
        if (urlString != null && !urlString.isEmpty()) {
            try {
                url = URI.create(urlString);
            } catch (IllegalArgumentException _) {
                // url stays null when the relay sends a malformed URI
            }
        }
        var currency = obj.getString("currency");
        // WAWebBizParseProductGraphql.parseProductGraphQL: price arrives as a stringified decimal
        long price = 0L;
        var priceString = obj.getString("price");
        if (priceString != null && !priceString.isEmpty()) {
            try {
                price = Long.parseLong(priceString);
            } catch (NumberFormatException _) {
                // price stays 0L on parse failure
            }
        }
        // WAWebBizParseProductGraphql.parseProductGraphQL: is_hidden is the "ISHIDDEN_TRUE" enum literal
        var hidden = "ISHIDDEN_TRUE".equals(obj.getString("is_hidden"));
        var availability = parseAvailability(obj.getString("product_availability"));
        BusinessReviewStatus reviewStatus = null;
        var statusInfo = obj.getJSONObject("status_info");
        if (statusInfo != null) {
            var status = statusInfo.getString("status");
            if (status != null) {
                reviewStatus = BusinessReviewStatus.ofName(status).orElse(null);
            }
        }
        URI encryptedImage = null;
        var media = obj.getJSONObject("media");
        if (media != null) {
            var images = media.getJSONArray("images");
            if (images != null && !images.isEmpty()) {
                var first = images.getJSONObject(0);
                if (first != null) {
                    var originalUrl = first.getString("original_image_url");
                    if (originalUrl != null && !originalUrl.isEmpty()) {
                        try {
                            encryptedImage = URI.create(originalUrl);
                        } catch (IllegalArgumentException _) {
                            // encryptedImage stays null on parse failure
                        }
                    }
                }
            }
        }
        var entry = new BusinessCatalogEntryBuilder()
                .id(id)
                .encryptedImage(encryptedImage)
                .reviewStatus(reviewStatus)
                .availability(availability)
                .name(name)
                .sellerId(retailerId)
                .uri(url)
                .description(description)
                .price(price)
                .currency(currency)
                .hidden(hidden)
                .build();
        return Optional.of(entry);
    }

    /**
     * Maps the WA Web {@code product_availability} enum string to the Cobalt
     * {@link BusinessItemAvailability} constant.
     *
     * <p>WA Web uses the prefixed enum literals
     * {@code PRODUCTAVAILABILITY_IN_STOCK},
     * {@code PRODUCTAVAILABILITY_OUT_OF_STOCK} and
     * {@code PRODUCTAVAILABILITY_UNKNOWN}. The prefix is stripped before
     * feeding the value into {@link BusinessItemAvailability#ofName(String)},
     * which expects the pretty-printed form ({@code "in stock"}).
     *
     * @param raw the raw enum string, may be {@code null}
     * @return the matched constant, or {@code null} when the input is absent
     *         or unknown
     */
    @WhatsAppWebExport(moduleName = "WAWebProductTypes.flow", exports = "ProductAvailability",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static BusinessItemAvailability parseAvailability(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        var stripped = raw.startsWith("PRODUCTAVAILABILITY_")
                ? raw.substring("PRODUCTAVAILABILITY_".length())
                : raw;
        var pretty = stripped.toLowerCase().replace('_', ' ');
        return BusinessItemAvailability.ofName(pretty).orElse(null);
    }
}
