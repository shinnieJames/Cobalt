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
 * Builds the relay mutation that edits an existing product in a WhatsApp Business catalog.
 *
 * <p>The mutation takes one {@code input} GraphQL variable mapped onto the
 * {@code xfb_whatsapp_catalog_edit_product} field's {@code request} argument. WhatsApp Web's
 * {@code WAWebBizCatalogManagementEditProduct.editProduct(input)} forwards the object built by
 * {@code WAWebBizCatalogEditProductJob} as {@code {product: {biz_jid, product_id, width, height,
 * product_info}}}: the catalog-owner {@link Jid}, the id of the product being edited, the requested
 * image-render {@code width} and {@code height}, and the {@code product_info} write-model carrying the
 * fields to change. The relay returns the edited product under {@code xfb_whatsapp_catalog_edit_product};
 * the reply is consumed through {@link BizCatalogManagementEditProductWhatsAppGraphQlResponse}.
 *
 * @implNote This implementation accepts the {@code product_info} write-model as a caller-supplied,
 * already JSON-encoded object literal: WhatsApp Web builds it from a product domain model via
 * {@code WAWebBizCatalogManagementParseProductGraphql.productModelToGraphQLInput}, a deep optional
 * write-model (name, media image and video url lists, hidden flag, description, url, retailer id,
 * currency, price, sale price, compliance info, and compliance category) that this request does not
 * re-model field by field. The value is emitted verbatim as the {@code product_info} sub-field via
 * {@link JSONWriter#writeRaw(String)}. The catalog-owner {@code biz_jid}, the {@code product_id}, and
 * the render dimensions remain typed scalar fields.
 *
 * @see BizCatalogManagementEditProductWhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "WAWebBizCatalogManagementEditProductMutation")
public final class BizCatalogManagementEditProductWhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCatalogManagementEditProductMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "9889773371084956";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCatalogManagementEditProductMutation.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "WAWebBizCatalogManagementEditProductMutation";

    /**
     * The {@code product.biz_jid} field naming the business account that owns the catalog, or
     * {@code null} to omit it.
     */
    private final Jid bizJid;

    /**
     * The {@code product.product_id} field identifying the catalog product being edited, or
     * {@code null} to omit it.
     */
    private final String productId;

    /**
     * The {@code product.width} field requesting the rendered image width, or {@code null} to omit it.
     */
    private final Integer width;

    /**
     * The {@code product.height} field requesting the rendered image height, or {@code null} to omit
     * it.
     */
    private final Integer height;

    /**
     * The pre-encoded JSON of the {@code product.product_info} write-model carrying the product fields
     * to change, or {@code null} to omit it.
     */
    private final String productInfoJson;

    /**
     * Constructs an edit-product mutation request.
     *
     * <p>All values populate the nested {@code product} GraphQL object; each value that is
     * {@code null} is omitted from the serialized object. The {@code productInfoJson} is the
     * already-JSON-encoded {@code product_info} write-model (see the class {@code @implNote}).
     *
     * @param bizJid          the catalog-owner {@link Jid}, or {@code null} to omit the field
     * @param productId       the id of the product being edited, or {@code null} to omit the field
     * @param width           the requested rendered image width, or {@code null} to omit the field
     * @param height          the requested rendered image height, or {@code null} to omit the field
     * @param productInfoJson the already-JSON-encoded {@code product_info} write-model, or
     *                        {@code null} to omit the field
     */
    public BizCatalogManagementEditProductWhatsAppGraphQlRequest(Jid bizJid, String productId, Integer width, Integer height, String productInfoJson) {
        this.bizJid = bizJid;
        this.productId = productId;
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
     * @implNote This implementation emits {@code {"input": {"product": {"biz_jid": <bizJid>,
     * "product_id": <productId>, "width": <width>, "height": <height>, "product_info":
     * <productInfoJson>}}}}, writing each sub-field only when its value is non-null. The
     * {@code biz_jid} is rendered as its canonical {@link Jid} string and the {@code product_info}
     * value is spliced in raw via {@link JSONWriter#writeRaw(String)} because it is supplied already
     * encoded.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCatalogManagementEditProduct", exports = "editProduct",
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

            if (productId != null) {
                writer.writeName("product_id");
                writer.writeColon();
                writer.writeString(productId);
            }

            if (width != null) {
                writer.writeName("width");
                writer.writeColon();
                writer.writeString(String.valueOf(width));
            }

            if (height != null) {
                writer.writeName("height");
                writer.writeColon();
                writer.writeString(String.valueOf(height));
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
