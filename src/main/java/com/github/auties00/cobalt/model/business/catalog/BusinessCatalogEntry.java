package com.github.auties00.cobalt.model.business.catalog;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.net.URI;
import java.util.Optional;

/**
 * A single product entry within a WhatsApp Business catalog.
 *
 * <p>This class represents the metadata of a product as managed through
 * the WhatsApp catalog system. In the WhatsApp Web client, product data
 * is obtained through the {@code xwa_product_catalog} GraphQL query or
 * through the {@code w:biz:catalog} IQ stanza namespace. The protobuf
 * representation used in product messages is the
 * {@code Message$ProductMessage$ProductSnapshot} structure defined in
 * {@code WAWebProtobufsE2E.pb}.
 *
 * <p>Each product has a unique {@linkplain #id() identifier}, a display
 * {@linkplain #name() name}, a {@linkplain #description() description},
 * and a {@linkplain #price() price} expressed in thousandths of the base
 * currency unit paired with a {@linkplain #currency() currency code}.
 * Product images are stored on the WhatsApp media CDN and referenced
 * through the {@linkplain #encryptedImage() encrypted image URI}. The
 * {@linkplain #sellerId() seller identifier} corresponds to the
 * {@code retailer_id} field used throughout the WhatsApp catalog system,
 * and the {@linkplain #uri() product URI} is the shareable link to the
 * product page. Visibility to customers is controlled by the
 * {@linkplain #hidden() hidden flag}, which corresponds to the
 * {@code is_hidden} attribute in WhatsApp Web.
 */
@ProtobufMessage
public final class BusinessCatalogEntry {
    /**
     * The unique identifier assigned to this product by the WhatsApp
     * catalog system. This corresponds to the {@code productId} field
     * in the {@code ProductSnapshot} protobuf.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String id;

    /**
     * The URI of the product's primary encrypted image on the WhatsApp
     * media CDN. In the WhatsApp Web client, product images are
     * referenced through CDN URLs obtained from the
     * {@code image_cdn_urls} field in catalog responses or from the
     * {@code <image><url>} element in the {@code w:biz:catalog} IQ
     * stanza XML.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    URI encryptedImage;

    /**
     * The compliance review status of this product, as determined by
     * WhatsApp's content review process. Review statuses are parsed
     * from the {@code status_info.status} field in catalog query
     * responses.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    BusinessReviewStatus reviewStatus;

    /**
     * The stock availability of this product. In the WhatsApp Web
     * client, availability is read from the {@code availability}
     * attribute on the product node in the {@code w:biz:catalog} IQ
     * stanza or from the {@code product_availability} field in GraphQL
     * catalog responses.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.ENUM)
    BusinessItemAvailability availability;

    /**
     * The display name of this product. This corresponds to the
     * {@code title} field in the {@code ProductSnapshot} protobuf and
     * the {@code name} field in catalog XML and GraphQL responses.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    String name;

    /**
     * The retailer-assigned identifier for this product. This
     * corresponds to the {@code retailer_id} field in catalog query
     * responses and the {@code retailerId} field in the
     * {@code ProductSnapshot} protobuf.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.STRING)
    String sellerId;

    /**
     * The shareable URI of this product's page. This corresponds to
     * the {@code url} field in the {@code ProductSnapshot} protobuf
     * and in catalog query responses.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.STRING)
    URI uri;

    /**
     * The description text of this product. This corresponds to the
     * {@code description} field in the {@code ProductSnapshot}
     * protobuf and in catalog query responses.
     */
    @ProtobufProperty(index = 8, type = ProtobufType.STRING)
    String description;

    /**
     * The price of this product in thousandths of the base currency
     * unit. This corresponds to the {@code priceAmount1000} field in
     * the {@code ProductSnapshot} protobuf. For example, a value of
     * {@code 1500000} with a {@linkplain #currency() currency} of
     * {@code "USD"} represents $1,500.00.
     */
    @ProtobufProperty(index = 9, type = ProtobufType.INT64)
    long price;

    /**
     * The ISO 4217 currency code for this product's price. This
     * corresponds to the {@code currencyCode} field in the
     * {@code ProductSnapshot} protobuf and the {@code currency} field
     * in catalog query responses.
     */
    @ProtobufProperty(index = 10, type = ProtobufType.STRING)
    String currency;

    /**
     * Whether this product is hidden from customers. Hidden products
     * remain in the catalog but are not displayed in the storefront.
     * This corresponds to the {@code is_hidden} attribute on the
     * product node in the {@code w:biz:catalog} IQ stanza and the
     * {@code is_hidden} field in GraphQL catalog responses. The
     * WhatsApp Web client maps the string values {@code "TRUE"} and
     * {@code "FALSE"} to the corresponding boolean values via the
     * {@code mapIsHiddenToWASchema} function in
     * {@code WAWebProductTypes.flow}.
     */
    @ProtobufProperty(index = 11, type = ProtobufType.BOOL)
    boolean hidden;

    /**
     * Constructs a new {@code BusinessCatalogEntry} with the specified
     * product metadata.
     *
     * @param id              the unique identifier of this product
     * @param encryptedImage  the URI of the product's primary encrypted
     *                        image on the WhatsApp media CDN
     * @param reviewStatus    the compliance review status of this product
     * @param availability    the stock availability of this product
     * @param name            the display name of this product
     * @param sellerId        the retailer-assigned identifier
     * @param uri             the shareable URI of the product page
     * @param description     the description text of this product
     * @param price           the price in thousandths of the base
     *                        currency unit
     * @param currency        the ISO 4217 currency code
     * @param hidden          whether this product is hidden from
     *                        customers
     */
    BusinessCatalogEntry(String id, URI encryptedImage, BusinessReviewStatus reviewStatus, BusinessItemAvailability availability, String name, String sellerId, URI uri, String description, long price, String currency, boolean hidden) {
        this.id = id;
        this.encryptedImage = encryptedImage;
        this.reviewStatus = reviewStatus;
        this.availability = availability;
        this.name = name;
        this.sellerId = sellerId;
        this.uri = uri;
        this.description = description;
        this.price = price;
        this.currency = currency;
        this.hidden = hidden;
    }

    /**
     * Returns the unique identifier assigned to this product by the
     * WhatsApp catalog system.
     *
     * @return an {@code Optional} describing the product identifier, or
     *         an empty {@code Optional} if the identifier is not set
     */
    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    /**
     * Returns the URI of the product's primary encrypted image on the
     * WhatsApp media CDN.
     *
     * @return an {@code Optional} describing the encrypted image URI, or
     *         an empty {@code Optional} if no image is available
     */
    public Optional<URI> encryptedImage() {
        return Optional.ofNullable(encryptedImage);
    }

    /**
     * Returns the compliance review status of this product, as
     * determined by WhatsApp's content review process.
     *
     * @return an {@code Optional} describing the
     *         {@link BusinessReviewStatus}, or an empty {@code Optional}
     *         if the status is not set
     */
    public Optional<BusinessReviewStatus> reviewStatus() {
        return Optional.ofNullable(reviewStatus);
    }

    /**
     * Returns the stock availability of this product.
     *
     * @return an {@code Optional} describing the
     *         {@link BusinessItemAvailability}, or an empty
     *         {@code Optional} if the availability is not set
     */
    public Optional<BusinessItemAvailability> availability() {
        return Optional.ofNullable(availability);
    }

    /**
     * Returns the display name of this product.
     *
     * @return an {@code Optional} describing the product name, or an
     *         empty {@code Optional} if the name is not set
     */
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    /**
     * Returns the retailer-assigned identifier for this product,
     * corresponding to the {@code retailer_id} field in the WhatsApp
     * catalog system.
     *
     * @return an {@code Optional} describing the seller identifier, or
     *         an empty {@code Optional} if the identifier is not set
     */
    public Optional<String> sellerId() {
        return Optional.ofNullable(sellerId);
    }

    /**
     * Returns the shareable URI of this product's page.
     *
     * @return an {@code Optional} describing the product URI, or an
     *         empty {@code Optional} if the URI is not set
     */
    public Optional<URI> uri() {
        return Optional.ofNullable(uri);
    }

    /**
     * Returns the description text of this product.
     *
     * @return an {@code Optional} describing the product description, or
     *         an empty {@code Optional} if the description is not set
     */
    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    /**
     * Returns the price of this product in thousandths of the base
     * currency unit. For example, a value of {@code 1500000} with a
     * {@linkplain #currency() currency} of {@code "USD"} represents
     * $1,500.00.
     *
     * @return the price in thousandths of the base currency unit
     */
    public long price() {
        return price;
    }

    /**
     * Returns the ISO 4217 currency code for this product's price.
     *
     * @return an {@code Optional} describing the currency code, or an
     *         empty {@code Optional} if the currency is not set
     */
    public Optional<String> currency() {
        return Optional.ofNullable(currency);
    }

    /**
     * Returns whether this product is hidden from customers. Hidden
     * products remain in the catalog but are not displayed in the
     * storefront.
     *
     * @return {@code true} if the product is hidden, {@code false}
     *         otherwise
     */
    public boolean hidden() {
        return hidden;
    }

    /**
     * Sets the unique identifier of this product.
     *
     * @param id the product identifier to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Sets the URI of the product's primary encrypted image.
     *
     * @param encryptedImage the encrypted image URI to set
     */
    public void setEncryptedImage(URI encryptedImage) {
        this.encryptedImage = encryptedImage;
    }

    /**
     * Sets the compliance review status of this product.
     *
     * @param reviewStatus the review status to set
     */
    public void setReviewStatus(BusinessReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    /**
     * Sets the stock availability of this product.
     *
     * @param availability the availability to set
     */
    public void setAvailability(BusinessItemAvailability availability) {
        this.availability = availability;
    }

    /**
     * Sets the display name of this product.
     *
     * @param name the product name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Sets the retailer-assigned identifier for this product.
     *
     * @param sellerId the seller identifier to set
     */
    public void setSellerId(String sellerId) {
        this.sellerId = sellerId;
    }

    /**
     * Sets the shareable URI of this product's page.
     *
     * @param uri the product URI to set
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    /**
     * Sets the description text of this product.
     *
     * @param description the product description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Sets the price of this product in thousandths of the base currency
     * unit.
     *
     * @param price the price to set
     */
    public void setPrice(long price) {
        this.price = price;
    }

    /**
     * Sets the ISO 4217 currency code for this product's price.
     *
     * @param currency the currency code to set
     */
    public void setCurrency(String currency) {
        this.currency = currency;
    }

    /**
     * Sets whether this product is hidden from customers.
     *
     * @param hidden whether the product should be hidden
     */
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
}
