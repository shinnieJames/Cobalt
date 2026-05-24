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
 * Outbound MEX request that fetches the product collections of a WhatsApp
 * Business catalog.
 *
 * @apiNote Drives the collections browser surface for business catalogs.
 * Collections are named groups of products that business owners define
 * inside a catalog; the response returns each collection's id and name
 * paired with the products nested inside it. Surfaced from
 * {@code WAWebProductCollCollection} via
 * {@code WAWebQueryProductCollections.default}, which dispatches to the
 * owner ({@code queryCollectionsGraphQLByOwner}) or guest
 * ({@code queryCollectionsGraphQLByGuest}) variant on the same compiled
 * document depending on the caller; Cobalt issues the single query and lets
 * the relay disambiguate.
 *
 * @implNote This implementation lets the caller pass every optional GraphQL
 * variable (cursor, encrypted direct-connection blob, variant info,
 * thumbnail dimensions) as explicit {@code null} when not needed, matching
 * WA Web's pass-through behaviour rather than baking in WA-Web defaults.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryProductCollections")
@WhatsAppWebModule(moduleName = "WAWebQueryProductCollectionsQuery.graphql")
public final class QueryProductCollectionsMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the
     * {@code WAWebQueryProductCollectionsQuery} document.
     *
     * @apiNote Mirrors the {@code params.id} value baked into
     * {@code WAWebQueryProductCollectionsQuery.graphql}. The relay maps this
     * id to its persisted operation; the GraphQL text is never sent on the
     * wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryProductCollectionsQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "9430970660362540";

    /**
     * GraphQL operation name reported to
     * {@code MexPerfTracker.setOperationName} when this query is dispatched.
     *
     * @apiNote Used by WA Web's MEX perf tracker to tag the query in latency
     * and error metrics; Cobalt keeps the name on the request for embedders
     * mirroring WA Web's telemetry surface.
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryProductCollections", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "queryProductCollections";

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
     * @apiNote {@code collectionLimit} and {@code itemLimit} are independent:
     * the first bounds the number of collections returned per page, the
     * second bounds the number of products returned inside each collection.
     * {@code directConnectionEncryptedInfo} is the optional payload the
     * business direct-connection retry loop produces; pass {@code null} when
     * the call is not routed through that retry path.
     *
     * @param businessJid                   the target business JID owning
     *                                      the catalog
     * @param collectionLimit               the maximum number of
     *                                      collections per page
     * @param itemLimit                     the maximum number of products
     *                                      returned inside every collection
     * @param afterCursor                   the pagination cursor returned
     *                                      by a previous page, or
     *                                      {@code null} for the first page
     * @param width                         the requested image width in
     *                                      pixels used when the relay
     *                                      rewrites image URLs
     * @param height                        the requested image height in
     *                                      pixels
     * @param directConnectionEncryptedInfo the optional direct-connection
     *                                      encrypted payload, or
     *                                      {@code null} when not used
     * @param variantInfoFields             the optional variant-info field
     *                                      selector, or {@code null} when
     *                                      not requested
     * @param variantThumbnailHeight        the optional variant thumbnail
     *                                      height in pixels, or
     *                                      {@code null} when not requested
     * @param variantThumbnailWidth         the optional variant thumbnail
     *                                      width in pixels, or {@code null}
     *                                      when not requested
     */
    public QueryProductCollectionsMexRequest(String businessJid, int collectionLimit, int itemLimit, String afterCursor, int width, int height,
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
     * {@code {"variables": {"request": {"collections": {...}}}}} shape
     * expected by {@code xwa_product_catalog_get_collections}. Width,
     * height, limit and pagination fields are stringified to match the WA
     * Web wire shape, which sends them as JSON strings even though they are
     * integers in the GraphQL schema. {@code null} variants of the four
     * optional fields are emitted as explicit JSON {@code null}, not
     * omitted, to match the WA Web {@code variant_thumbnail_height: m != null
     * ? String(m) : null} pattern.
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryProductCollections", exports = "default",
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
            writer.writeName("collections");
            writer.writeColon();
            writer.startObject();
            writer.writeName("biz_jid");
            writer.writeColon();
            writer.writeString(businessJid);
            writer.writeName("collection_limit");
            writer.writeColon();
            writer.writeString(Integer.toString(collectionLimit));
            writer.writeName("item_limit");
            writer.writeColon();
            writer.writeString(Integer.toString(itemLimit));
            writer.writeName("after");
            writer.writeColon();
            if (afterCursor == null) {
                writer.writeNull();
            } else {
                writer.writeString(afterCursor);
            }
            writer.writeName("width");
            writer.writeColon();
            writer.writeString(Integer.toString(width));
            writer.writeName("height");
            writer.writeColon();
            writer.writeString(Integer.toString(height));
            writer.writeName("direct_connection_encrypted_info");
            writer.writeColon();
            if (directConnectionEncryptedInfo == null) {
                writer.writeNull();
            } else {
                writer.writeString(directConnectionEncryptedInfo);
            }
            writer.writeName("variant_info_fields");
            writer.writeColon();
            if (variantInfoFields == null) {
                writer.writeNull();
            } else {
                writer.writeString(variantInfoFields);
            }
            writer.writeName("variant_thumbnail_height");
            writer.writeColon();
            if (variantThumbnailHeight == null) {
                writer.writeNull();
            } else {
                writer.writeString(Integer.toString(variantThumbnailHeight));
            }
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
                return MexOperation.Request.Json.createMexNode(QUERY_ID, output.toString());
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
