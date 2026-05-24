package com.github.auties00.cobalt.node.mex.json.business;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.catalog.BusinessCatalogEntry;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.mex.MexOperation;

import java.util.List;
import java.util.Optional;

/**
 * Parsed response of the {@code queryCatalog} MEX query.
 *
 * @apiNote Carries the {@code xwa_product_catalog_get_product_catalog.product_catalog}
 * projection paired with the {@code paging.after} cursor needed to drive
 * subsequent pages. Surfaced from {@link QueryCatalogMexRequest} replies; the
 * paired WA Web entry point is {@code WAWebQueryCatalog.default} and the
 * downstream callers are {@code WAWebBizProductCatalogBridge.queryCatalog}
 * and the {@code WAWebBusinessProfileCollection} prefetch path.
 *
 * @implNote This implementation tolerates a relay reply that omits either
 * {@code xwa_product_catalog_get_product_catalog} or its
 * {@code product_catalog} child by returning an empty result, mirroring WA
 * Web's empty {@code data: []} fallback. A reply missing {@code data} or
 * unparseable JSON collapses to {@link Optional#empty()}.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryCatalog")
public final class QueryCatalogMexResponse implements MexOperation.Response.Json {
    private final List<BusinessCatalogEntry> products;
    private final String afterCursor;

    /**
     * Constructs a parsed catalog response.
     *
     * @apiNote Package-private; instances are produced by the
     * {@link #of(Node)} factory after parsing the inbound IQ payload.
     *
     * @param products    the catalog entries returned by this page
     * @param afterCursor the {@code paging.after} cursor, or the empty string
     *                    when the relay reported no further pages
     */
    private QueryCatalogMexResponse(List<BusinessCatalogEntry> products, String afterCursor) {
        this.products = products;
        this.afterCursor = afterCursor;
    }

    /**
     * Parses the MEX response carried by an inbound IQ stanza.
     *
     * @apiNote Entry point for receivers handling
     * {@code <iq xmlns="w:mex">} replies tagged with the
     * {@link QueryCatalogMexRequest#QUERY_ID} query id. Unwraps the
     * {@code <result>} child, reads its content bytes and decodes the
     * GraphQL JSON envelope.
     *
     * @param node the inbound IQ stanza carrying the {@code <result>} child
     * @return the parsed response, or empty if the expected JSON shape is
     *         absent
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryCatalog", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<QueryCatalogMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(QueryCatalogMexResponse::of);
    }

    /**
     * Returns the products carried by this page of the catalog.
     *
     * @apiNote Each entry is the projection produced by
     * {@link CatalogProductParser#parseProducts(com.alibaba.fastjson2.JSONArray)}.
     *
     * @return an unmodifiable list of {@link BusinessCatalogEntry} values,
     *         never {@code null}
     */
    public List<BusinessCatalogEntry> products() {
        return products;
    }

    /**
     * Returns the {@code paging.after} cursor usable to request the next page
     * of products.
     *
     * @apiNote Pass the returned value as the {@code afterCursor} argument of
     * the next {@link QueryCatalogMexRequest}. An empty {@link Optional}
     * means the relay did not advertise a continuation cursor; callers should
     * stop pagination.
     *
     * @return an {@link Optional} carrying the cursor when the relay returned
     *         a non-empty value, or empty otherwise
     */
    public Optional<String> afterCursor() {
        return afterCursor == null || afterCursor.isEmpty() ? Optional.empty() : Optional.of(afterCursor);
    }

    /**
     * Parses the raw JSON bytes of the {@code <result>} child.
     *
     * @apiNote Package-private; only invoked via the {@link #of(Node)} entry
     * point after unwrapping the IQ stanza.
     *
     * @implNote This implementation matches WA Web's empty-result fallback: a
     * reply where {@code xwa_product_catalog_get_product_catalog} or
     * {@code product_catalog} is {@code null} returns a response with an
     * empty product list and an empty cursor rather than
     * {@link Optional#empty()}. Only a structurally broken envelope (missing
     * {@code data} or unparseable JSON) yields {@link Optional#empty()}.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return the parsed response, or empty if the envelope is missing the
     *         expected fields
     */
    private static Optional<QueryCatalogMexResponse> of(byte[] json) {
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
            return Optional.of(new QueryCatalogMexResponse(List.of(), ""));
        }
        var catalog = getResult.getJSONObject("product_catalog");
        if (catalog == null) {
            return Optional.of(new QueryCatalogMexResponse(List.of(), ""));
        }
        var paging = catalog.getJSONObject("paging");
        var cursor = paging == null ? "" : Optional.ofNullable(paging.getString("after")).orElse("");
        var products = CatalogProductParser.parseProducts(catalog.getJSONArray("products"));
        return Optional.of(new QueryCatalogMexResponse(products, cursor));
    }
}
