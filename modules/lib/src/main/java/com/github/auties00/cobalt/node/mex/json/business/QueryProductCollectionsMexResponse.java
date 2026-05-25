package com.github.auties00.cobalt.node.mex.json.business;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.catalog.BusinessCatalog;
import com.github.auties00.cobalt.model.business.catalog.BusinessCatalogBuilder;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.mex.MexOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parsed response of the {@code queryProductCollections} MEX query.
 *
 * <p>Carries the {@code xwa_product_catalog_get_collections.collections} array
 * projected onto Cobalt {@link BusinessCatalog} values, paired with the
 * {@code paging.after} cursor needed to drive subsequent pages, decoded from
 * {@link QueryProductCollectionsMexRequest} replies.
 *
 * @implNote This implementation drops the WA Web {@code status_info},
 * {@code can_appeal}, {@code reject_reason} and {@code commerce_url} fields
 * because the Cobalt {@link BusinessCatalog} model does not represent the
 * collection review surface; only {@code id}, {@code name} and the nested
 * products are projected.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryProductCollections")
public final class QueryProductCollectionsMexResponse implements MexOperation.Response.Json {
    /**
     * Holds the business catalogs returned by this page.
     */
    private final List<BusinessCatalog> collections;

    /**
     * Holds the {@code paging.after} cursor, or the empty string when the
     * relay reported no further pages.
     */
    private final String afterCursor;

    /**
     * Constructs a parsed collections response.
     *
     * <p>Instances are produced by {@link #of(Node)} after parsing the inbound
     * IQ payload.
     *
     * @param collections the parsed business catalogs
     * @param afterCursor the {@code paging.after} cursor, or the empty string
     *                    when the relay reported no further pages
     */
    private QueryProductCollectionsMexResponse(List<BusinessCatalog> collections, String afterCursor) {
        this.collections = collections;
        this.afterCursor = afterCursor;
    }

    /**
     * Parses the MEX response carried by an inbound IQ stanza.
     *
     * <p>Entry point for receivers handling {@code <iq xmlns="w:mex">} replies
     * tagged with {@link QueryProductCollectionsMexRequest#QUERY_ID}; unwraps
     * the {@code <result>} child, reads its content bytes and decodes the
     * GraphQL JSON envelope.
     *
     * @param node the inbound IQ stanza carrying the {@code <result>} child
     * @return the parsed response, or empty if the expected JSON shape is
     *         absent
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryProductCollections", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<QueryProductCollectionsMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(QueryProductCollectionsMexResponse::of);
    }

    /**
     * Returns the collections carried by this page of the query.
     *
     * <p>Each {@link BusinessCatalog} pairs the collection's id and name with
     * the products nested inside it, projected through
     * {@link CatalogProductParser#parseProducts(JSONArray)}.
     *
     * @return an unmodifiable list of {@link BusinessCatalog} values, never
     *         {@code null}
     */
    public List<BusinessCatalog> collections() {
        return collections;
    }

    /**
     * Returns the {@code paging.after} cursor usable to request the next page
     * of collections.
     *
     * <p>Pass the returned value as the {@code afterCursor} argument of the
     * next {@link QueryProductCollectionsMexRequest}. An empty {@link Optional}
     * means the relay did not advertise a continuation cursor, so callers
     * should stop pagination.
     *
     * @return an {@link Optional} carrying the cursor when the relay returned a
     *         non-empty value, or empty otherwise
     */
    public Optional<String> afterCursor() {
        return afterCursor == null || afterCursor.isEmpty() ? Optional.empty() : Optional.of(afterCursor);
    }

    /**
     * Parses the raw JSON bytes of the {@code <result>} child.
     *
     * <p>Invoked only via the {@link #of(Node)} entry point after unwrapping
     * the IQ stanza.
     *
     * @implNote This implementation matches WA Web's empty-result fallback: a
     * reply where {@code xwa_product_catalog_get_collections} is {@code null}
     * returns an empty collections list with an empty cursor rather than
     * {@link Optional#empty()}. Only a structurally broken envelope (missing
     * {@code data} or unparseable JSON) yields {@link Optional#empty()}.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return the parsed response, or empty if the envelope is missing the
     *         expected fields
     */
    private static Optional<QueryProductCollectionsMexResponse> of(byte[] json) {
        var root = JSON.parseObject(json);
        if (root == null) {
            return Optional.empty();
        }
        var data = root.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }
        var getResult = data.getJSONObject("xwa_product_catalog_get_collections");
        if (getResult == null) {
            return Optional.of(new QueryProductCollectionsMexResponse(List.of(), ""));
        }
        var paging = getResult.getJSONObject("paging");
        var cursor = paging == null ? "" : Optional.ofNullable(paging.getString("after")).orElse("");
        var collectionsArray = getResult.getJSONArray("collections");
        var collections = parseCollections(collectionsArray);
        return Optional.of(new QueryProductCollectionsMexResponse(collections, cursor));
    }

    /**
     * Parses an array of GraphQL collection objects into a list of
     * {@link BusinessCatalog} values.
     *
     * <p>Invoked once per response to project the {@code collections} array
     * onto the Cobalt domain model.
     *
     * @implNote This implementation skips entries that
     * {@link #parseCollection(JSONObject)} rejects (currently only {@code null}
     * entries) and wraps the result in
     * {@link List#copyOf(java.util.Collection)} so callers receive an
     * unmodifiable list.
     *
     * @param array the GraphQL collections array, possibly {@code null}
     * @return the parsed collections, never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryProductCollections", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static List<BusinessCatalog> parseCollections(JSONArray array) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        var out = new ArrayList<BusinessCatalog>(array.size());
        for (var i = 0; i < array.size(); i++) {
            parseCollection(array.getJSONObject(i)).ifPresent(out::add);
        }
        return List.copyOf(out);
    }

    /**
     * Parses a single GraphQL collection object into a {@link BusinessCatalog}.
     *
     * <p>Invoked by {@link #parseCollections(JSONArray)} once per array
     * element.
     *
     * @implNote This implementation defaults missing {@code id} and
     * {@code name} fields to the empty string, mirroring the WA Web
     * {@code n || ""} and {@code r || ""} fall-throughs.
     *
     * @param obj the GraphQL collection object, possibly {@code null}
     * @return the parsed collection, or empty when {@code obj} is {@code null}
     */
    private static Optional<BusinessCatalog> parseCollection(JSONObject obj) {
        if (obj == null) {
            return Optional.empty();
        }
        var id = Optional.ofNullable(obj.getString("id")).orElse("");
        var name = Optional.ofNullable(obj.getString("name")).orElse("");
        var products = CatalogProductParser.parseProducts(obj.getJSONArray("products"));
        var collection = new BusinessCatalogBuilder()
                .id(id)
                .name(name)
                .products(products)
                .build();
        return Optional.of(collection);
    }
}
