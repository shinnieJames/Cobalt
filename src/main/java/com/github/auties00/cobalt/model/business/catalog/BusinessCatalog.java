package com.github.auties00.cobalt.model.business.catalog;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A product collection within a WhatsApp Business catalog.
 *
 * <p>In the WhatsApp Web client, collections organize and group related
 * products within a business's catalog. Each collection is assigned a
 * unique {@linkplain #id() identifier} by the catalog system, has a
 * display {@linkplain #name() name} visible to customers, and contains
 * a list of {@linkplain #products() product entries}. Collections are
 * fetched via the {@code xwa_product_catalog_get_single_collection}
 * GraphQL query or in batch through the
 * {@code xfb_whatsapp_catalog_collections} query.
 */
@ProtobufMessage
public final class BusinessCatalog {
    /**
     * The unique identifier assigned to this collection by the WhatsApp
     * catalog system.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String id;

    /**
     * The display name of this collection as shown to customers in the
     * catalog storefront.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String name;

    /**
     * The product entries contained in this collection.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    List<BusinessCatalogEntry> products;

    /**
     * Constructs a new {@code BusinessCatalog} with the specified
     * collection identifier, display name, and list of product entries.
     *
     * @param id       the unique identifier of this collection
     * @param name     the display name of this collection
     * @param products the product entries contained in this collection
     */
    BusinessCatalog(String id, String name, List<BusinessCatalogEntry> products) {
        this.id = id;
        this.name = name;
        this.products = products;
    }

    /**
     * Returns the unique identifier assigned to this collection by the
     * WhatsApp catalog system.
     *
     * @return an {@code Optional} describing the collection identifier,
     *         or an empty {@code Optional} if the identifier is not set
     */
    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    /**
     * Returns the display name of this collection as shown to customers
     * in the catalog storefront.
     *
     * @return an {@code Optional} describing the collection name, or an
     *         empty {@code Optional} if the name is not set
     */
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    /**
     * Returns the product entries contained in this collection.
     *
     * @return an unmodifiable list of {@link BusinessCatalogEntry}
     *         objects, or an empty list if no products are present
     */
    public List<BusinessCatalogEntry> products() {
        return products == null ? List.of() : Collections.unmodifiableList(products);
    }

    /**
     * Sets the unique identifier of this collection.
     *
     * @param id the collection identifier to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Sets the display name of this collection.
     *
     * @param name the collection name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Sets the product entries contained in this collection.
     *
     * @param products the product entries to set
     */
    public void setProducts(List<BusinessCatalogEntry> products) {
        this.products = products;
    }
}
