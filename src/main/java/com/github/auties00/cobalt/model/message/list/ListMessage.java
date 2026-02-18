package com.github.auties00.cobalt.model.message.list;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.ContextInfo;
import com.github.auties00.cobalt.model.message.ContextualMessage;

import java.util.Collections;
import java.util.List;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.ListMessage")
public final class ListMessage implements ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String title;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String description;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String buttonText;

    @ProtobufProperty(index = 4, type = ProtobufType.ENUM)
    ListType listType;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    List<Section> sections;

    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    ProductListInfo productListInfo;

    @ProtobufProperty(index = 7, type = ProtobufType.STRING)
    String footerText;

    @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;


    ListMessage(String title, String description, String buttonText, ListType listType, List<Section> sections, ProductListInfo productListInfo, String footerText, ContextInfo contextInfo) {
        this.title = title;
        this.description = description;
        this.buttonText = buttonText;
        this.listType = listType;
        this.sections = sections;
        this.productListInfo = productListInfo;
        this.footerText = footerText;
        this.contextInfo = contextInfo;
    }

    public Optional<String> title() {
        return Optional.ofNullable(title);
    }

    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    public Optional<String> buttonText() {
        return Optional.ofNullable(buttonText);
    }

    public Optional<ListType> listType() {
        return Optional.ofNullable(listType);
    }

    public List<Section> sections() {
        return sections == null ? List.of() : Collections.unmodifiableList(sections);
    }

    public Optional<ProductListInfo> productListInfo() {
        return Optional.ofNullable(productListInfo);
    }

    public Optional<String> footerText() {
        return Optional.ofNullable(footerText);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public ListMessage setTitle(String title) {
        this.title = title;
        return this;
    }

    public ListMessage setDescription(String description) {
        this.description = description;
        return this;
    }

    public ListMessage setButtonText(String buttonText) {
        this.buttonText = buttonText;
        return this;
    }

    public ListMessage setListType(ListType listType) {
        this.listType = listType;
        return this;
    }

    public ListMessage setSections(List<Section> sections) {
        this.sections = sections;
        return this;
    }

    public ListMessage setProductListInfo(ProductListInfo productListInfo) {
        this.productListInfo = productListInfo;
        return this;
    }

    public ListMessage setFooterText(String footerText) {
        this.footerText = footerText;
        return this;
    }

    public ListMessage setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
        return this;
    }

    @ProtobufEnum(name = "Message.ListMessage.ListType")
    public static enum ListType {
        UNKNOWN(0),
        SINGLE_SELECT(1),
        PRODUCT_LIST(2);

        ListType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufMessage(name = "Message.ListMessage.Product")
    public static final class Product {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String productId;


        Product(String productId) {
            this.productId = productId;
        }

        public Optional<String> productId() {
            return Optional.ofNullable(productId);
        }

        public Product setProductId(String productId) {
            this.productId = productId;
            return this;
        }
    }

    @ProtobufMessage(name = "Message.ListMessage.ProductListHeaderImage")
    public static final class ProductListHeaderImage {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String productId;

        @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
        byte[] jpegThumbnail;


        ProductListHeaderImage(String productId, byte[] jpegThumbnail) {
            this.productId = productId;
            this.jpegThumbnail = jpegThumbnail;
        }

        public Optional<String> productId() {
            return Optional.ofNullable(productId);
        }

        public Optional<byte[]> jpegThumbnail() {
            return Optional.ofNullable(jpegThumbnail);
        }

        public ProductListHeaderImage setProductId(String productId) {
            this.productId = productId;
            return this;
        }

        public ProductListHeaderImage setJpegThumbnail(byte[] jpegThumbnail) {
            this.jpegThumbnail = jpegThumbnail;
            return this;
        }
    }

    @ProtobufMessage(name = "Message.ListMessage.ProductListInfo")
    public static final class ProductListInfo {
        @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
        List<ProductSection> productSections;

        @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
        ProductListHeaderImage headerImage;

        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        Jid businessOwnerJid;


        ProductListInfo(List<ProductSection> productSections, ProductListHeaderImage headerImage, Jid businessOwnerJid) {
            this.productSections = productSections;
            this.headerImage = headerImage;
            this.businessOwnerJid = businessOwnerJid;
        }

        public List<ProductSection> productSections() {
            return productSections == null ? List.of() : Collections.unmodifiableList(productSections);
        }

        public Optional<ProductListHeaderImage> headerImage() {
            return Optional.ofNullable(headerImage);
        }

        public Optional<Jid> businessOwnerJid() {
            return Optional.ofNullable(businessOwnerJid);
        }

        public ProductListInfo setProductSections(List<ProductSection> productSections) {
            this.productSections = productSections;
            return this;
        }

        public ProductListInfo setHeaderImage(ProductListHeaderImage headerImage) {
            this.headerImage = headerImage;
            return this;
        }

        public ProductListInfo setBusinessOwnerJid(Jid businessOwnerJid) {
            this.businessOwnerJid = businessOwnerJid;
            return this;
        }
    }

    @ProtobufMessage(name = "Message.ListMessage.ProductSection")
    public static final class ProductSection {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String title;

        @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
        List<Product> products;


        ProductSection(String title, List<Product> products) {
            this.title = title;
            this.products = products;
        }

        public Optional<String> title() {
            return Optional.ofNullable(title);
        }

        public List<Product> products() {
            return products == null ? List.of() : Collections.unmodifiableList(products);
        }

        public ProductSection setTitle(String title) {
            this.title = title;
            return this;
        }

        public ProductSection setProducts(List<Product> products) {
            this.products = products;
            return this;
        }
    }

    @ProtobufMessage(name = "Message.ListMessage.Row")
    public static final class Row {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String title;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String description;

        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        String rowId;


        Row(String title, String description, String rowId) {
            this.title = title;
            this.description = description;
            this.rowId = rowId;
        }

        public Optional<String> title() {
            return Optional.ofNullable(title);
        }

        public Optional<String> description() {
            return Optional.ofNullable(description);
        }

        public Optional<String> rowId() {
            return Optional.ofNullable(rowId);
        }

        public Row setTitle(String title) {
            this.title = title;
            return this;
        }

        public Row setDescription(String description) {
            this.description = description;
            return this;
        }

        public Row setRowId(String rowId) {
            this.rowId = rowId;
            return this;
        }
    }

    @ProtobufMessage(name = "Message.ListMessage.Section")
    public static final class Section {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String title;

        @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
        List<Row> rows;


        Section(String title, List<Row> rows) {
            this.title = title;
            this.rows = rows;
        }

        public Optional<String> title() {
            return Optional.ofNullable(title);
        }

        public List<Row> rows() {
            return rows == null ? List.of() : Collections.unmodifiableList(rows);
        }

        public Section setTitle(String title) {
            this.title = title;
            return this;
        }

        public Section setRows(List<Row> rows) {
            this.rows = rows;
            return this;
        }
    }
}
