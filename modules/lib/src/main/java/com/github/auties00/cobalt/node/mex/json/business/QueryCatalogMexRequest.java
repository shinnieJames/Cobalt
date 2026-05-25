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
 * Outbound MEX request that fetches the products of a WhatsApp Business
 * catalog.
 *
 * <p>A catalog is the flat storefront attached to a business JID; the response
 * carries the contained products together with their core metadata (name,
 * description, currency, price, image URLs). The matching decoder is
 * {@link QueryCatalogMexResponse}.
 *
 * @implNote This implementation issues a single query and lets the relay
 * disambiguate between the owner and guest browse paths that WA Web routes
 * with two named functions on the same compiled document. It omits the WA Web
 * {@code checkmark_collection_id} variable (Cobalt does not surface the
 * WhatsApp shop checkmark UI), the {@code variant_info_fields},
 * {@code variant_thumbnail_height} and {@code variant_thumbnail_width}
 * variables (variant info is not projected onto the Cobalt domain model), the
 * {@code direct_connection_encrypted_info} variable (Cobalt does not implement
 * the business direct-connection retry loop), and the
 * {@code catalog_session_id} variable; all of those are sent as explicit
 * {@code null} so the relay accepts the document shape.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryCatalog")
@WhatsAppWebModule(moduleName = "WAWebQueryCatalogQuery.graphql")
public final class QueryCatalogMexRequest implements MexOperation.Request.Json {
    /**
     * Holds the compiled GraphQL query identifier for the
     * {@code WAWebQueryCatalogQuery} document.
     *
     * <p>The relay maps this id to its persisted operation; the GraphQL text
     * is never sent on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryCatalogQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "9916553288394782";

    /**
     * Holds the GraphQL operation name reported when this query is dispatched.
     *
     * <p>WA Web's MEX perf tracker uses this name to tag the query in latency
     * and error metrics; Cobalt keeps it on the request for embedders
     * mirroring that telemetry surface.
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryCatalog", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "queryCatalog";

    /**
     * Holds the target business JID owning the catalog.
     */
    private final String catalogJid;

    /**
     * Holds the page size, the maximum number of products returned per page.
     */
    private final int limit;

    /**
     * Holds the requested image width in pixels used when the relay rewrites
     * image URLs.
     */
    private final int width;

    /**
     * Holds the requested image height in pixels used when the relay rewrites
     * image URLs.
     */
    private final int height;

    /**
     * Holds the pagination cursor returned by a previous page, or {@code null}
     * for the first page.
     */
    private final String afterCursor;

    /**
     * Holds whether the request opts into the WhatsApp shop source surface.
     */
    private final boolean allowShopSource;

    /**
     * Creates a new catalog query request with the WA Web default
     * {@code allowShopSource=false}.
     *
     * <p>Convenience overload for the common browse path; equivalent to the
     * six-argument constructor with {@code allowShopSource=false}.
     *
     * @param catalogJid  the target business JID owning the catalog
     * @param limit       the page size (maximum number of products returned
     *                    per page)
     * @param width       the requested image width in pixels used when the
     *                    relay rewrites image URLs
     * @param height      the requested image height in pixels
     * @param afterCursor the pagination cursor returned by a previous page,
     *                    or {@code null} for the first page
     */
    public QueryCatalogMexRequest(String catalogJid, int limit, int width, int height, String afterCursor) {
        this(catalogJid, limit, width, height, afterCursor, false);
    }

    /**
     * Creates a new catalog query request.
     *
     * <p>Use this constructor when the caller needs to set
     * {@code allowShopSource} explicitly; the value is wire-serialised as the
     * WA Web enum literal {@code "ALLOWSHOPSOURCE_TRUE"} or
     * {@code "ALLOWSHOPSOURCE_FALSE"}.
     *
     * @param catalogJid      the target business JID owning the catalog
     * @param limit           the page size (maximum number of products
     *                        returned per page)
     * @param width           the requested image width in pixels used when
     *                        the relay rewrites image URLs
     * @param height          the requested image height in pixels
     * @param afterCursor     the pagination cursor returned by a previous
     *                        page, or {@code null} for the first page
     * @param allowShopSource whether the request opts into the WhatsApp shop
     *                        source surface, mapped to the WA Web
     *                        {@code allow_shop_source} enum string
     */
    public QueryCatalogMexRequest(String catalogJid, int limit, int width, int height, String afterCursor, boolean allowShopSource) {
        this.catalogJid = catalogJid;
        this.limit = limit;
        this.width = width;
        this.height = height;
        this.afterCursor = afterCursor;
        this.allowShopSource = allowShopSource;
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
     * {@code {"variables": {"request": {"product_catalog": {...}}}}} shape
     * expected by {@code xwa_product_catalog_get_product_catalog}, then
     * delegates to {@link MexOperation.Request.Json#createMexNode(String, String)}
     * to wrap the JSON in the {@code w:mex} envelope. Width, height and
     * pagination fields are stringified to match the WA Web wire shape, which
     * sends them as JSON strings even though they are integers in the GraphQL
     * schema.
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryCatalog", exports = "default",
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
            writer.writeName("product_catalog");
            writer.writeColon();
            writer.startObject();
            writer.writeName("jid");
            writer.writeColon();
            writer.writeString(catalogJid);
            writer.writeName("allow_shop_source");
            writer.writeColon();
            writer.writeString(allowShopSource ? "ALLOWSHOPSOURCE_TRUE" : "ALLOWSHOPSOURCE_FALSE");
            writer.writeName("width");
            writer.writeColon();
            writer.writeString(Integer.toString(width));
            writer.writeName("height");
            writer.writeColon();
            writer.writeString(Integer.toString(height));
            writer.writeName("direct_connection_encrypted_info");
            writer.writeColon();
            writer.writeNull();
            writer.writeName("limit");
            writer.writeColon();
            writer.writeString(Integer.toString(limit));
            writer.writeName("after");
            writer.writeColon();
            if (afterCursor != null) {
                writer.writeString(afterCursor);
            } else {
                writer.writeNull();
            }
            writer.writeName("catalog_session_id");
            writer.writeColon();
            writer.writeNull();
            writer.writeName("variant_info_fields");
            writer.writeColon();
            writer.writeNull();
            writer.writeName("variant_thumbnail_height");
            writer.writeColon();
            writer.writeNull();
            writer.writeName("variant_thumbnail_width");
            writer.writeColon();
            writer.writeNull();
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
