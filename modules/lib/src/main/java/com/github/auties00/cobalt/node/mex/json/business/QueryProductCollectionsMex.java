package com.github.auties00.cobalt.node.mex.json.business;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.catalog.BusinessCatalog;
import com.github.auties00.cobalt.model.business.catalog.BusinessCatalogBuilder;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.mex.json.MexJsonOperation;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fetches the list of product collections that belong to a WhatsApp
 * Business catalog.
 *
 * <p>Collections are named groups of products that business owners can
 * define inside a catalog. The query returns the top-level metadata of
 * each collection (id, name) along with the products nested inside it.
 * WA Web exposes both an owner and a guest entry point but both funnel the
 * same GraphQL document through {@code WAWebMexClient.fetchQuery}.
 *
 * <p>This type is a sealed interface that models the two sides of the MEX
 * exchange as sibling variants, matching the rest of the Cobalt
 * {@code mex.json} package.
 *
 * @implNote WAWebQueryProductCollections: adapts the
 * {@code WAWebQueryProductCollectionsQuery.graphql} operation used by the
 * owner and guest collection browsing flows.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryProductCollections")
@WhatsAppWebModule(moduleName = "WAWebQueryProductCollectionsQuery.graphql")
public sealed interface QueryProductCollectionsMex extends MexJsonOperation permits QueryProductCollectionsMex.Request, QueryProductCollectionsMex.Response {
    /**
     * The numeric GraphQL query identifier assigned by the WhatsApp relay
     * to the {@code WAWebQueryProductCollectionsQuery} compiled query.
     *
     * @implNote WAWebQueryProductCollectionsQuery.graphql: corresponds to
     * the {@code params.id} field of the compiled query, extracted from
     * the current snapshot of the WA Web bundle.
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryProductCollectionsQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    String QUERY_ID = "9430970660362540";

    /**
     * The request variant that serialises the GraphQL variables and emits
     * the outbound IQ stanza.
     *
     * @implNote WAWebQueryProductCollections: adapts the inline
     * {@code variables.request.collections} envelope used by the WA Web
     * owner and guest helpers.
     */
    @WhatsAppWebModule(moduleName = "WAWebQueryProductCollections")
    final class Request implements QueryProductCollectionsMex {
        private final String businessJid;
        private final int collectionLimit;
        private final int itemLimit;
        private final String afterCursor;
        private final int width;
        private final int height;
        private final String directConnectionEncryptedInfo;
        private final String variantInfoFields;
        private final Integer variantThumbnailHeight;
        private final Integer variantThumbnailWidth;

        /**
         * Creates a new collections query request.
         *
         * @param businessJid                   the target business JID owning the catalog
         * @param collectionLimit               the maximum number of collections per page
         * @param itemLimit                     the maximum number of products returned
         *                                      inside every collection
         * @param afterCursor                   the pagination cursor returned by a
         *                                      previous page, or {@code null} for the
         *                                      first page
         * @param width                         the requested image width in pixels used
         *                                      when the relay rewrites image URLs
         * @param height                        the requested image height in pixels
         * @param directConnectionEncryptedInfo the optional direct-connection encrypted
         *                                      payload, or {@code null} when not used
         * @param variantInfoFields             the optional variant-info field selector,
         *                                      or {@code null} when not requested
         * @param variantThumbnailHeight        the optional variant thumbnail height in
         *                                      pixels, or {@code null} when not requested
         * @param variantThumbnailWidth         the optional variant thumbnail width in
         *                                      pixels, or {@code null} when not requested
         */
        public Request(String businessJid, int collectionLimit, int itemLimit, String afterCursor, int width, int height,
                       String directConnectionEncryptedInfo, String variantInfoFields,
                       Integer variantThumbnailHeight, Integer variantThumbnailWidth) {
            this.businessJid = businessJid;
            this.collectionLimit = collectionLimit;
            this.itemLimit = itemLimit;
            this.afterCursor = afterCursor;
            this.width = width;
            this.height = height;
            this.directConnectionEncryptedInfo = directConnectionEncryptedInfo;
            this.variantInfoFields = variantInfoFields;
            this.variantThumbnailHeight = variantThumbnailHeight;
            this.variantThumbnailWidth = variantThumbnailWidth;
        }

        /**
         * Builds the IQ stanza that dispatches this operation to the
         * WhatsApp relay.
         *
         * @implNote WAWebQueryProductCollections: mirrors the
         * {@code request.collections} variable shape with
         * {@code biz_jid}, {@code collection_limit}, {@code item_limit},
         * {@code after}, {@code width}, {@code height},
         * {@code direct_connection_encrypted_info},
         * {@code variant_info_fields}, {@code variant_thumbnail_height} and
         * {@code variant_thumbnail_width}, in that order, with every numeric
         * field stringified as WA expects. Optional string fields and the
         * {@code after} cursor are emitted as JSON {@code null} when absent
         * to preserve byte-for-byte parity with the WA Web payload.
         * @return a {@link NodeBuilder} carrying the IQ envelope and the
         *         serialised GraphQL variables
         */
        @WhatsAppWebExport(moduleName = "WAWebQueryProductCollections", exports = "default",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public NodeBuilder toNode() {
            try (var writer = JSONWriter.ofUTF8()) {
                writer.startObject();
                writer.writeName("variables");
                writer.writeColon();
                writer.startObject();
                writer.writeName("request");
                writer.writeColon();
                writer.startObject();
                writer.writeName("collections");
                writer.writeColon();
                writer.startObject();
                // WAWebQueryProductCollections.default: biz_jid: a.toString()
                writer.writeName("biz_jid");
                writer.writeColon();
                writer.writeString(businessJid);
                // WAWebQueryProductCollections.default: collection_limit: String(s)
                writer.writeName("collection_limit");
                writer.writeColon();
                writer.writeString(Integer.toString(collectionLimit));
                // WAWebQueryProductCollections.default: item_limit: String(c)
                writer.writeName("item_limit");
                writer.writeColon();
                writer.writeString(Integer.toString(itemLimit));
                // WAWebQueryProductCollections.default: after: r (nullable string passthrough)
                writer.writeName("after");
                writer.writeColon();
                if (afterCursor == null) {
                    writer.writeNull();
                } else {
                    writer.writeString(afterCursor);
                }
                // WAWebQueryProductCollections.default: width: String(_)
                writer.writeName("width");
                writer.writeColon();
                writer.writeString(Integer.toString(width));
                // WAWebQueryProductCollections.default: height: String(l)
                writer.writeName("height");
                writer.writeColon();
                writer.writeString(Integer.toString(height));
                // WAWebQueryProductCollections.default: direct_connection_encrypted_info: i
                writer.writeName("direct_connection_encrypted_info");
                writer.writeColon();
                if (directConnectionEncryptedInfo == null) {
                    writer.writeNull();
                } else {
                    writer.writeString(directConnectionEncryptedInfo);
                }
                // WAWebQueryProductCollections.default: variant_info_fields: d
                writer.writeName("variant_info_fields");
                writer.writeColon();
                if (variantInfoFields == null) {
                    writer.writeNull();
                } else {
                    writer.writeString(variantInfoFields);
                }
                // WAWebQueryProductCollections.default: variant_thumbnail_height: m!=null?String(m):null
                writer.writeName("variant_thumbnail_height");
                writer.writeColon();
                if (variantThumbnailHeight == null) {
                    writer.writeNull();
                } else {
                    writer.writeString(Integer.toString(variantThumbnailHeight));
                }
                // WAWebQueryProductCollections.default: variant_thumbnail_width: p!=null?String(p):null
                writer.writeName("variant_thumbnail_width");
                writer.writeColon();
                if (variantThumbnailWidth == null) {
                    writer.writeNull();
                } else {
                    writer.writeString(Integer.toString(variantThumbnailWidth));
                }
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
     * @implNote WAWebQueryProductCollections: adapts the
     * {@code xwa_product_catalog_get_collections.collections} array into a
     * list of {@link BusinessCatalog}; the GraphQL {@code paging.after}
     * cursor is surfaced so callers can paginate.
     */
    @WhatsAppWebModule(moduleName = "WAWebQueryProductCollections")
    final class Response implements QueryProductCollectionsMex {
        private final List<BusinessCatalog> collections;
        private final String afterCursor;

        private Response(List<BusinessCatalog> collections, String afterCursor) {
            this.collections = collections;
            this.afterCursor = afterCursor;
        }

        /**
         * Parses the MEX response carried by an inbound IQ stanza.
         *
         * @implNote WAWebQueryProductCollections.default: the WA Web helper
         * reads {@code data.xwa_product_catalog_get_collections.collections}
         * and the sibling {@code paging.after} cursor. When the relay
         * returns a GraphQL error with code {@code 2498052} WA Web surfaces
         * an empty response; Cobalt applies the same behaviour by treating
         * a missing {@code xwa_product_catalog_get_collections} field as
         * an empty page.
         * @param node the inbound IQ stanza carrying the {@code <result>} child
         * @return the parsed response, or {@code Optional.empty()} if the
         *         expected JSON shape is absent
         */
        @WhatsAppWebExport(moduleName = "WAWebQueryProductCollections", exports = "default",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Response> of(Node node) {
            return node.getChild("result")
                    .flatMap(Node::toContentBytes)
                    .flatMap(Response::of);
        }

        /**
         * Returns the collections returned by this page of the query.
         *
         * @return an unmodifiable list of collections, never {@code null}
         */
        public List<BusinessCatalog> collections() {
            return collections;
        }

        /**
         * Returns the {@code paging.after} cursor usable to request the
         * next page of collections.
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
            var getResult = data.getJSONObject("xwa_product_catalog_get_collections");
            if (getResult == null) {
                // WAWebQueryProductCollections.default: missing field is treated as an empty page
                return Optional.of(new Response(List.of(), ""));
            }
            var paging = getResult.getJSONObject("paging");
            var cursor = paging == null ? "" : Optional.ofNullable(paging.getString("after")).orElse("");
            var collectionsArray = getResult.getJSONArray("collections");
            var collections = parseCollections(collectionsArray);
            return Optional.of(new Response(collections, cursor));
        }
    }

    /**
     * Parses an array of GraphQL collection objects into a list of
     * {@link BusinessCatalog} values.
     *
     * @implNote WAWebQueryProductCollections.default: each collection is
     * mapped onto {@code {id, name, products, ...}}; Cobalt drops the
     * {@code status_info} and {@code canAppeal} side-channels since
     * {@link BusinessCatalog} does not expose them yet. The inner
     * {@code products} array is parsed via
     * {@link QueryCatalogMex#parseProducts(JSONArray)} to share the same
     * field projection with the catalog query.
     * @param array the GraphQL collections array, possibly {@code null}
     * @return the parsed collections, never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryProductCollections", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static List<BusinessCatalog> parseCollections(JSONArray array) {
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
     * Parses a single GraphQL collection object into a
     * {@link BusinessCatalog}.
     *
     * @param obj the GraphQL collection object, possibly {@code null}
     * @return the parsed collection, or {@link Optional#empty()} if
     *         {@code obj} is {@code null}
     */
    private static Optional<BusinessCatalog> parseCollection(JSONObject obj) {
        if (obj == null) {
            return Optional.empty();
        }
        // WAWebQueryProductCollections.default: id defaults to empty string
        var id = Optional.ofNullable(obj.getString("id")).orElse("");
        // WAWebQueryProductCollections.default: name defaults to empty string
        var name = Optional.ofNullable(obj.getString("name")).orElse("");
        // WAWebQueryProductCollections.default: products parsed via parseProductGraphQL
        var products = QueryCatalogMex.parseProducts(obj.getJSONArray("products"));
        // ADAPTED: BusinessCatalog constructor is package-private so Cobalt uses the generated builder
        var collection = new BusinessCatalogBuilder()
                .id(id)
                .name(name)
                .products(products)
                .build();
        return Optional.of(collection);
    }
}
