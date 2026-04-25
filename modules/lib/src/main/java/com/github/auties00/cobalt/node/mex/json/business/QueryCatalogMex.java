package com.github.auties00.cobalt.node.mex.json.business;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.catalog.BusinessCatalogEntry;
import com.github.auties00.cobalt.model.business.catalog.BusinessCatalogEntryBuilder;
import com.github.auties00.cobalt.model.business.catalog.BusinessItemAvailability;
import com.github.auties00.cobalt.model.business.catalog.BusinessReviewStatus;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.mex.json.MexJsonOperation;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fetches the list of products that belong to a WhatsApp Business catalog.
 *
 * <p>A catalog is the flat storefront attached to a business JID; this query
 * returns the products it contains along with their core metadata such as
 * name, description, currency, price, and image URLs. WA Web exposes the
 * operation under two names depending on whether the caller is the catalog
 * owner ({@code queryCatalogGraphQLByOwner}) or a guest browser
 * ({@code queryCatalogGraphQLByGuest}); both paths materialise the same
 * GraphQL query which the relay dispatches through
 * {@code WAWebMexClient.fetchQuery}.
 *
 * <p>This type is a sealed interface that models the two sides of the MEX
 * exchange as sibling variants, matching the pattern used across the rest of
 * the Cobalt {@code mex.json} package.
 *
 * @implNote WAWebQueryCatalog: adapts the
 * {@code WAWebQueryCatalogQuery.graphql} operation used both for the owner
 * and guest queryCatalog flows. The WA Web request pre-serialises the
 * GraphQL variables under a {@code request.product_catalog} envelope; this
 * class reproduces the exact shape directly.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryCatalog")
@WhatsAppWebModule(moduleName = "WAWebQueryCatalogQuery.graphql")
@WhatsAppWebModule(moduleName = "WAWebBizParseProductGraphql")
@WhatsAppWebModule(moduleName = "WAWebBizParseProductGraphql_product.graphql")
public sealed interface QueryCatalogMex extends MexJsonOperation permits QueryCatalogMex.Request, QueryCatalogMex.Response {
    /**
     * The numeric GraphQL query identifier assigned by the WhatsApp relay
     * to the {@code WAWebQueryCatalogQuery} compiled query.
     *
     * @implNote WAWebQueryCatalogQuery.graphql: corresponds to the
     * {@code params.id} field of the compiled query, extracted from the
     * current snapshot of the WA Web bundle.
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryCatalogQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    String QUERY_ID = "9916553288394782";

    /**
     * The request variant of {@link QueryCatalogMex} that serialises the
     * GraphQL variables and emits the outbound IQ stanza.
     *
     * @implNote WAWebQueryCatalog: adapts the inline {@code variables}
     * object constructed by the WA Web owner and guest helpers into a
     * dedicated Java record-style class.
     */
    @WhatsAppWebModule(moduleName = "WAWebQueryCatalog")
    final class Request implements QueryCatalogMex {
        private final String catalogJid;
        private final int limit;
        private final int width;
        private final int height;
        private final String afterCursor;
        private final boolean allowShopSource;

        /**
         * Creates a new catalog query request with the WA Web default
         * {@code allowShopSource=false}.
         *
         * @param catalogJid  the target business JID owning the catalog
         * @param limit       the page size (maximum number of products
         *                    returned per page)
         * @param width       the requested image width in pixels used when
         *                    the relay rewrites image URLs
         * @param height      the requested image height in pixels
         * @param afterCursor the pagination cursor returned by a previous
         *                    page, or {@code null} for the first page
         */
        public Request(String catalogJid, int limit, int width, int height, String afterCursor) {
            this(catalogJid, limit, width, height, afterCursor, false);
        }

        /**
         * Creates a new catalog query request.
         *
         * @param catalogJid       the target business JID owning the
         *                         catalog
         * @param limit            the page size (maximum number of products
         *                         returned per page)
         * @param width            the requested image width in pixels used
         *                         when the relay rewrites image URLs
         * @param height           the requested image height in pixels
         * @param afterCursor      the pagination cursor returned by a
         *                         previous page, or {@code null} for the
         *                         first page
         * @param allowShopSource  whether the request opts into the
         *                         WhatsApp shop source surface; mapped to
         *                         the WA Web {@code allow_shop_source}
         *                         enum string
         */
        public Request(String catalogJid, int limit, int width, int height, String afterCursor, boolean allowShopSource) {
            this.catalogJid = catalogJid;
            this.limit = limit;
            this.width = width;
            this.height = height;
            this.afterCursor = afterCursor;
            this.allowShopSource = allowShopSource;
        }

        /**
         * Builds the IQ stanza that dispatches this operation to the
         * WhatsApp relay.
         *
         * @implNote WAWebQueryCatalog: mirrors the exact WA Web
         * {@code request.product_catalog} variable shape, in order:
         * {@code jid}, {@code allow_shop_source} (enum string),
         * {@code width}, {@code height}, {@code direct_connection_encrypted_info}
         * (always {@code null} from this surface),
         * {@code limit}, {@code after} (always emitted, possibly {@code null}),
         * {@code catalog_session_id}, {@code variant_info_fields},
         * {@code variant_thumbnail_height}, {@code variant_thumbnail_width}.
         * WA Web emits explicit {@code null} values for the optional fields
         * rather than omitting the keys.
         * @return a {@link NodeBuilder} carrying the IQ envelope and the
         *         serialised GraphQL variables
         */
        @WhatsAppWebExport(moduleName = "WAWebQueryCatalog", exports = "default",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public NodeBuilder toNode() {
            // WAWebQueryCatalog.default
            // Opens a UTF-8 JSON writer that will serialise the request.product_catalog variables envelope
            try (var writer = JSONWriter.ofUTF8()) {
                writer.startObject();
                writer.writeName("variables");
                writer.writeColon();
                writer.startObject();
                writer.writeName("request");
                writer.writeColon();
                writer.startObject();
                writer.writeName("product_catalog");
                writer.writeColon();
                writer.startObject();
                // WAWebQueryCatalog.default: jid: catalogWid.toString()
                writer.writeName("jid");
                writer.writeColon();
                writer.writeString(catalogJid);
                // WAWebQueryCatalog.default: allow_shop_source: u ? "ALLOWSHOPSOURCE_TRUE" : "ALLOWSHOPSOURCE_FALSE"
                writer.writeName("allow_shop_source");
                writer.writeColon();
                writer.writeString(allowShopSource ? "ALLOWSHOPSOURCE_TRUE" : "ALLOWSHOPSOURCE_FALSE");
                // WAWebQueryCatalog.default: width: String(y)
                writer.writeName("width");
                writer.writeColon();
                writer.writeString(Integer.toString(width));
                // WAWebQueryCatalog.default: height: String(p)
                writer.writeName("height");
                writer.writeColon();
                writer.writeString(Integer.toString(height));
                // WAWebQueryCatalog.default: direct_connection_encrypted_info: m (always null at this surface)
                writer.writeName("direct_connection_encrypted_info");
                writer.writeColon();
                writer.writeNull();
                // WAWebQueryCatalog.default: limit: String(_)
                writer.writeName("limit");
                writer.writeColon();
                writer.writeString(Integer.toString(limit));
                // WAWebQueryCatalog.default: after: l (afterCursor, may be null but key always present)
                writer.writeName("after");
                writer.writeColon();
                if (afterCursor != null) {
                    writer.writeString(afterCursor);
                } else {
                    writer.writeNull();
                }
                // WAWebQueryCatalog.default: catalog_session_id: d (checkmarkCollectionId, null at this surface)
                writer.writeName("catalog_session_id");
                writer.writeColon();
                writer.writeNull();
                // WAWebQueryCatalog.default: variant_info_fields: f (null at this surface)
                writer.writeName("variant_info_fields");
                writer.writeColon();
                writer.writeNull();
                // WAWebQueryCatalog.default: variant_thumbnail_height: g!=null ? String(g) : null
                writer.writeName("variant_thumbnail_height");
                writer.writeColon();
                writer.writeNull();
                // WAWebQueryCatalog.default: variant_thumbnail_width: h!=null ? String(h) : null
                writer.writeName("variant_thumbnail_width");
                writer.writeColon();
                writer.writeNull();
                writer.endObject();
                writer.endObject();
                writer.endObject();
                writer.endObject();
                try (var output = new StringWriter()) {
                    writer.flushTo(output);
                    return MexJsonOperation.createMexNode(QUERY_ID, output.toString());
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }

    /**
     * The response variant that parses the JSON returned by the relay.
     *
     * @implNote WAWebQueryCatalog: adapts the
     * {@code xwa_product_catalog_get_product_catalog.product_catalog}
     * projection into a list of {@link BusinessCatalogEntry}; the GraphQL
     * {@code paging.after} cursor is surfaced so callers can paginate.
     */
    @WhatsAppWebModule(moduleName = "WAWebQueryCatalog")
    final class Response implements QueryCatalogMex {
        private final List<BusinessCatalogEntry> products;
        private final String afterCursor;

        private Response(List<BusinessCatalogEntry> products, String afterCursor) {
            this.products = products;
            this.afterCursor = afterCursor;
        }

        /**
         * Parses the MEX response carried by an inbound IQ stanza.
         *
         * @implNote WAWebQueryCatalog.default: the WA Web helper reads
         * {@code data.xwa_product_catalog_get_product_catalog.product_catalog.products}
         * and the sibling {@code paging} cursors. When the relay returns an
         * absent {@code product_catalog} both the WA Web helper and this
         * method yield an empty response.
         * @param node the inbound IQ stanza carrying the {@code <result>} child
         * @return the parsed response, or {@code Optional.empty()} if the
         *         expected JSON shape is absent
         */
        @WhatsAppWebExport(moduleName = "WAWebQueryCatalog", exports = "default",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Response> of(Node node) {
            return node.getChild("result")
                    .flatMap(Node::toContentBytes)
                    .flatMap(Response::of);
        }

        /**
         * Returns the list of products returned by this page of the
         * catalog.
         *
         * @return an unmodifiable list of entries, never {@code null}
         */
        public List<BusinessCatalogEntry> products() {
            return products;
        }

        /**
         * Returns the {@code paging.after} cursor usable to request the
         * next page of products.
         *
         * @return an {@link Optional} containing the cursor when the relay
         *         returned a non-empty value, or empty otherwise
         */
        public Optional<String> afterCursor() {
            return afterCursor == null || afterCursor.isEmpty() ? Optional.empty() : Optional.of(afterCursor);
        }

        private static Optional<Response> of(byte[] json) {
            var root = JSON.parseObject(json);
            if (root == null) {
                return Optional.empty();
            }
            var data = root.getJSONObject("data");
            if (data == null) {
                return Optional.empty();
            }
            var getResult = data.getJSONObject("xwa_product_catalog_get_product_catalog");
            if (getResult == null) {
                return Optional.of(new Response(List.of(), ""));
            }
            var catalog = getResult.getJSONObject("product_catalog");
            if (catalog == null) {
                // WAWebQueryCatalog.default: when product_catalog is absent WA Web returns an empty page
                return Optional.of(new Response(List.of(), ""));
            }
            var paging = catalog.getJSONObject("paging");
            var cursor = paging == null ? "" : Optional.ofNullable(paging.getString("after")).orElse("");
            var products = parseProducts(catalog.getJSONArray("products"));
            return Optional.of(new Response(products, cursor));
        }
    }

    /**
     * Parses an array of GraphQL product objects into a list of
     * {@link BusinessCatalogEntry} values.
     *
     * <p>This helper is shared between {@link QueryCatalogMex} and
     * {@link QueryProductCollectionsMex} since WA Web's
     * {@code WAWebBizParseProductGraphql.parseProductGraphQL} applies to
     * both response shapes.
     *
     * @implNote WAWebBizParseProductGraphql.parseProductGraphQL: Cobalt
     * restricts the parse to the fields surfaced by
     * {@link BusinessCatalogEntry} (id, retailer_id, name, description,
     * url, currency, price, visibility, first image URL, status, stock
     * availability). The remaining WA Web fields (variants, compliance,
     * videos, sale price) are intentionally dropped since Cobalt does not
     * expose them yet.
     * @param array the GraphQL products array, possibly {@code null}
     * @return the parsed entries, never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebBizParseProductGraphql", exports = "parseProductGraphQL",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static List<BusinessCatalogEntry> parseProducts(JSONArray array) {
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
     * @return the parsed entry, or {@link Optional#empty()} if {@code obj}
     *         is {@code null}
     */
    private static Optional<BusinessCatalogEntry> parseProduct(JSONObject obj) {
        if (obj == null) {
            return Optional.empty();
        }
        // WAWebBizParseProductGraphql.parseProductGraphQL: id
        var id = obj.getString("id");
        // WAWebBizParseProductGraphql.parseProductGraphQL: retailer_id
        var retailerId = obj.getString("retailer_id");
        // WAWebBizParseProductGraphql.parseProductGraphQL: name defaults to empty string in WA Web via WANullthrows
        var name = obj.getString("name");
        // WAWebBizParseProductGraphql.parseProductGraphQL: description defaults to empty string
        var description = obj.getString("description");
        // WAWebBizParseProductGraphql.parseProductGraphQL: url defaults to empty string
        var urlString = obj.getString("url");
        URI url = null;
        if (urlString != null && !urlString.isEmpty()) {
            try {
                url = URI.create(urlString);
            } catch (IllegalArgumentException ignored) {
                // url stays null
            }
        }
        // WAWebBizParseProductGraphql.parseProductGraphQL: currency
        var currency = obj.getString("currency");
        // WAWebBizParseProductGraphql.parseProductGraphQL: price arrives as a stringified decimal amount
        long price = 0L;
        var priceString = obj.getString("price");
        if (priceString != null && !priceString.isEmpty()) {
            try {
                price = Long.parseLong(priceString);
            } catch (NumberFormatException ignored) {
                // price stays 0L
            }
        }
        // WAWebBizParseProductGraphql.parseProductGraphQL: is_hidden is expressed as the "ISHIDDEN_TRUE" enum string
        var hidden = "ISHIDDEN_TRUE".equals(obj.getString("is_hidden"));
        // WAWebBizParseProductGraphql.parseProductGraphQL: product_availability enum mapped via pretty name lookup
        var availability = parseAvailability(obj.getString("product_availability"));
        // WAWebBizParseProductGraphql.parseProductGraphQL: capability_to_review_status defaults to "APPROVED"
        BusinessReviewStatus reviewStatus = null;
        var statusInfo = obj.getJSONObject("status_info");
        if (statusInfo != null) {
            var status = statusInfo.getString("status");
            if (status != null) {
                reviewStatus = BusinessReviewStatus.ofName(status).orElse(null);
            }
        }
        // WAWebBizParseProductGraphql.parseProductGraphQL: image_cdn_urls "full" entry
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
                        } catch (IllegalArgumentException ignored) {
                            // encryptedImage stays null
                        }
                    }
                }
            }
        }
        // ADAPTED: BusinessCatalogEntry constructor is package-private so Cobalt uses the generated builder
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
     * Maps the WA Web {@code product_availability} enum string to the
     * Cobalt {@link BusinessItemAvailability} constant.
     *
     * <p>WA Web uses the prefixed enum literals {@code PRODUCTAVAILABILITY_IN_STOCK},
     * {@code PRODUCTAVAILABILITY_OUT_OF_STOCK} and
     * {@code PRODUCTAVAILABILITY_UNKNOWN}. The prefix is stripped before
     * feeding the value into {@link BusinessItemAvailability#ofName(String)}
     * which expects the pretty-printed form ({@code "in stock"}).
     *
     * @implNote WAWebProductTypes.flow: defines the ProductAvailability
     * enum values; Cobalt keeps only the user-visible states.
     * @param raw the raw enum string, or {@code null}
     * @return the matched constant, or {@code null} when the input is
     *         absent or unknown
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
