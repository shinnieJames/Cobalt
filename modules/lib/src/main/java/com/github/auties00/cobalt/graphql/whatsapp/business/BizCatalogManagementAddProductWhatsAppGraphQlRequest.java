package com.github.auties00.cobalt.graphql.whatsapp.business;

import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;

/**
 * Builds the relay mutation that adds a product to a WhatsApp Business catalog.
 *
 * <p>The single {@code input} GraphQL variable wraps a {@code product} object. WhatsApp Web's
 * {@code WAWebBizCatalogAddProductJob} fills it with the {@code biz_jid} business account that owns the
 * catalog, the requested thumbnail {@code width} and {@code height}, and a {@code product_info} object
 * carrying the product fields. The relay returns the created product under
 * {@code xfb_whatsapp_catalog_add_product}; the reply is consumed through
 * {@link BizCatalogManagementAddProductWhatsAppGraphQlResponse}.
 *
 * @implNote This implementation types the recoverable top-level {@code product} fields ({@code biz_jid}
 * as a {@link Jid}, {@code width} and {@code height} as integers) and accepts {@code product_info} as a
 * caller-supplied, already JSON-encoded object literal: WhatsApp Web assembles {@code product_info} in
 * {@code WAWebBizCatalogManagementParseProductGraphql.productModelToGraphQLInput} as a large,
 * conditionally-populated structure (name, media images and videos, price, sale price, compliance info,
 * compliance category, and more) whose field set varies per product, so it is emitted verbatim rather
 * than modelled as fixed typed fields.
 *
 * @see BizCatalogManagementAddProductWhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "WAWebBizCatalogManagementAddProductMutation")
public final class BizCatalogManagementAddProductWhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCatalogManagementAddProductMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "24249359867999500";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCatalogManagementAddProductMutation.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "WAWebBizCatalogManagementAddProductMutation";

    /**
     * The {@code biz_jid} field of the {@code product} object naming the catalog owner, or
     * {@code null} to omit it.
     */
    private final Jid bizJid;

    /**
     * The {@code width} field of the {@code product} object holding the requested thumbnail width, or
     * {@code null} to omit it.
     */
    private final Integer width;

    /**
     * The {@code height} field of the {@code product} object holding the requested thumbnail height,
     * or {@code null} to omit it.
     */
    private final Integer height;

    /**
     * The pre-encoded JSON of the {@code product_info} object carrying the product fields, or
     * {@code null} to omit it.
     */
    private final String productInfoJson;

    /**
     * Constructs an add-product mutation request.
     *
     * <p>The {@code bizJid} names the catalog owner, {@code width} and {@code height} request the
     * thumbnail dimensions, and {@code productInfoJson} is the already-JSON-encoded {@code product_info}
     * object holding the product fields (see the class {@code @implNote}). Each value that is
     * {@code null} omits its field from the serialized object.
     *
     * @param bizJid          the catalog owner {@link Jid}, or {@code null} to omit the field
     * @param width           the requested thumbnail width, or {@code null} to omit the field
     * @param height          the requested thumbnail height, or {@code null} to omit the field
     * @param productInfoJson the already-JSON-encoded {@code product_info} object, or {@code null} to
     *                        omit the field
     */
    public BizCatalogManagementAddProductWhatsAppGraphQlRequest(Jid bizJid, Integer width, Integer height, String productInfoJson) {
        this.bizJid = bizJid;
        this.width = width;
        this.height = height;
        this.productInfoJson = productInfoJson;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String docId() {
        return DOC_ID;
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
     * @implNote This implementation emits {@code {"input": {"product": {"biz_jid": <bizJid>, "width":
     * <width>, "height": <height>, "product_info": <productInfoJson>}}}}, writing each field only when
     * its value is non-null and emitting an empty {@code product} object when all are {@code null}. The
     * {@code product_info} value is spliced in as a raw JSON value via
     * {@link JSONWriter#writeRaw(String)} because it is supplied already encoded.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCatalogAddProductJob", exports = "addProductMD",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public String variables() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("input");
            writer.writeColon();
            writer.startObject();
            writer.writeName("product");
            writer.writeColon();
            writer.startObject();
            if (bizJid != null) {
                writer.writeName("biz_jid");
                writer.writeColon();
                writer.writeString(bizJid.toString());
            }

            if (width != null) {
                writer.writeName("width");
                writer.writeColon();
                writer.writeInt32(width);
            }

            if (height != null) {
                writer.writeName("height");
                writer.writeColon();
                writer.writeInt32(height);
            }

            if (productInfoJson != null) {
                writer.writeName("product_info");
                writer.writeColon();
                writer.writeRaw(productInfoJson);
            }
            writer.endObject();
            writer.endObject();
            writer.endObject();
            try (var output = new StringWriter()) {
                writer.flushTo(output);
                return output.toString();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
