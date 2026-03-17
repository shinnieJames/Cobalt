package com.github.auties00.cobalt.model.message.commerce;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.message.interactive.InteractiveMessage;
import com.github.auties00.cobalt.model.message.media.ImageMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

@ProtobufMessage(name = "Message.ProductMessage")
public final class ProductMessage implements ContextualMessage, InteractiveMessage.MediaSpec {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    ProductSnapshot product;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    Jid businessOwnerJid;

    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    CatalogSnapshot catalog;

    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    String body;

    @ProtobufProperty(index = 6, type = ProtobufType.STRING)
    String footer;

    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;


    ProductMessage(ProductSnapshot product, Jid businessOwnerJid, CatalogSnapshot catalog, String body, String footer, ContextInfo contextInfo) {
        this.product = product;
        this.businessOwnerJid = businessOwnerJid;
        this.catalog = catalog;
        this.body = body;
        this.footer = footer;
        this.contextInfo = contextInfo;
    }

    public Optional<ProductSnapshot> product() {
        return Optional.ofNullable(product);
    }

    public Optional<Jid> businessOwnerJid() {
        return Optional.ofNullable(businessOwnerJid);
    }

    public Optional<CatalogSnapshot> catalog() {
        return Optional.ofNullable(catalog);
    }

    public Optional<String> body() {
        return Optional.ofNullable(body);
    }

    public Optional<String> footer() {
        return Optional.ofNullable(footer);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public void setProduct(ProductSnapshot product) {
        this.product = product;
    }

    public void setBusinessOwnerJid(Jid businessOwnerJid) {
        this.businessOwnerJid = businessOwnerJid;
    }

    public void setCatalog(CatalogSnapshot catalog) {
        this.catalog = catalog;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public void setFooter(String footer) {
        this.footer = footer;
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    @ProtobufMessage(name = "Message.ProductMessage.CatalogSnapshot")
    public static final class CatalogSnapshot {
        @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
        ImageMessage catalogImage;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String title;

        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        String description;


        CatalogSnapshot(ImageMessage catalogImage, String title, String description) {
            this.catalogImage = catalogImage;
            this.title = title;
            this.description = description;
        }

        public Optional<ImageMessage> catalogImage() {
            return Optional.ofNullable(catalogImage);
        }

        public Optional<String> title() {
            return Optional.ofNullable(title);
        }

        public Optional<String> description() {
            return Optional.ofNullable(description);
        }

        public void setCatalogImage(ImageMessage catalogImage) {
            this.catalogImage = catalogImage;
    }

        public void setTitle(String title) {
            this.title = title;
    }

        public void setDescription(String description) {
            this.description = description;
    }
    }

    @ProtobufMessage(name = "Message.ProductMessage.ProductSnapshot")
    public static final class ProductSnapshot {
        @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
        ImageMessage productImage;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String productId;

        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        String title;

        @ProtobufProperty(index = 4, type = ProtobufType.STRING)
        String description;

        @ProtobufProperty(index = 5, type = ProtobufType.STRING)
        String currencyCode;

        @ProtobufProperty(index = 6, type = ProtobufType.INT64)
        Long priceAmount1000;

        @ProtobufProperty(index = 7, type = ProtobufType.STRING)
        String retailerId;

        @ProtobufProperty(index = 8, type = ProtobufType.STRING)
        String url;

        @ProtobufProperty(index = 9, type = ProtobufType.UINT32)
        Integer productImageCount;

        @ProtobufProperty(index = 11, type = ProtobufType.STRING)
        String firstImageId;

        @ProtobufProperty(index = 12, type = ProtobufType.INT64)
        Long salePriceAmount1000;

        @ProtobufProperty(index = 13, type = ProtobufType.STRING)
        String signedUrl;


        ProductSnapshot(ImageMessage productImage, String productId, String title, String description, String currencyCode, Long priceAmount1000, String retailerId, String url, Integer productImageCount, String firstImageId, Long salePriceAmount1000, String signedUrl) {
            this.productImage = productImage;
            this.productId = productId;
            this.title = title;
            this.description = description;
            this.currencyCode = currencyCode;
            this.priceAmount1000 = priceAmount1000;
            this.retailerId = retailerId;
            this.url = url;
            this.productImageCount = productImageCount;
            this.firstImageId = firstImageId;
            this.salePriceAmount1000 = salePriceAmount1000;
            this.signedUrl = signedUrl;
        }

        public Optional<ImageMessage> productImage() {
            return Optional.ofNullable(productImage);
        }

        public Optional<String> productId() {
            return Optional.ofNullable(productId);
        }

        public Optional<String> title() {
            return Optional.ofNullable(title);
        }

        public Optional<String> description() {
            return Optional.ofNullable(description);
        }

        public Optional<String> currencyCode() {
            return Optional.ofNullable(currencyCode);
        }

        public OptionalLong priceAmount1000() {
            return priceAmount1000 == null ? OptionalLong.empty() : OptionalLong.of(priceAmount1000);
        }

        public Optional<String> retailerId() {
            return Optional.ofNullable(retailerId);
        }

        public Optional<String> url() {
            return Optional.ofNullable(url);
        }

        public OptionalInt productImageCount() {
            return productImageCount == null ? OptionalInt.empty() : OptionalInt.of(productImageCount);
        }

        public Optional<String> firstImageId() {
            return Optional.ofNullable(firstImageId);
        }

        public OptionalLong salePriceAmount1000() {
            return salePriceAmount1000 == null ? OptionalLong.empty() : OptionalLong.of(salePriceAmount1000);
        }

        public Optional<String> signedUrl() {
            return Optional.ofNullable(signedUrl);
        }

        public void setProductImage(ImageMessage productImage) {
            this.productImage = productImage;
    }

        public void setProductId(String productId) {
            this.productId = productId;
    }

        public void setTitle(String title) {
            this.title = title;
    }

        public void setDescription(String description) {
            this.description = description;
    }

        public void setCurrencyCode(String currencyCode) {
            this.currencyCode = currencyCode;
    }

        public void setPriceAmount1000(Long priceAmount1000) {
            this.priceAmount1000 = priceAmount1000;
    }

        public void setRetailerId(String retailerId) {
            this.retailerId = retailerId;
    }

        public void setUrl(String url) {
            this.url = url;
    }

        public void setProductImageCount(Integer productImageCount) {
            this.productImageCount = productImageCount;
    }

        public void setFirstImageId(String firstImageId) {
            this.firstImageId = firstImageId;
    }

        public void setSalePriceAmount1000(Long salePriceAmount1000) {
            this.salePriceAmount1000 = salePriceAmount1000;
    }

        public void setSignedUrl(String signedUrl) {
            this.signedUrl = signedUrl;
    }
    }
}
