package com.github.auties00.cobalt.node.iq.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed family of inbound reply variants produced by the relay in
 * response to an {@link IqQueryProductListCatalogRequest}.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryProductListCatalogJob")
public sealed interface IqQueryProductListCatalogResponse extends IqOperation.Response
        permits IqQueryProductListCatalogResponse.Success, IqQueryProductListCatalogResponse.ClientError, IqQueryProductListCatalogResponse.ServerError {

    /**
     * Tries each {@link IqQueryProductListCatalogResponse} variant in priority order.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant
     * @throws NullPointerException if either argument is {@code null}
     */
    static Optional<? extends IqQueryProductListCatalogResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var success = Success.of(node, request);
        if (success.isPresent()) {
            return success;
        }
        var clientError = ClientError.of(node, request);
        if (clientError.isPresent()) {
            return clientError;
        }
        return ServerError.of(node, request);
    }

    /**
     * The {@code Success} reply variant — projects the typed product
     * list.
     */
    final class Success implements IqQueryProductListCatalogResponse {
        /**
         * One typed product entry, decoded from a {@code <product/>}
         * child of the {@code <product_list/>} payload.
         */
        public static final class Product {
            /**
             * The product id.
             */
            private final String id;

            /**
             * Whether this entry is the synthetic
             * {@code "INVALID_PRODUCT"} marker — when {@code true} the
             * remaining fields are unset and only {@code id} is
             * meaningful.
             */
            private final boolean invalid;

            /**
             * The optional product name.
             */
            private final String name;

            /**
             * The optional description body.
             */
            private final String description;

            /**
             * The optional canonical URL.
             */
            private final String url;

            /**
             * The optional retailer-side identifier.
             */
            private final String retailerId;

            /**
             * The optional availability marker.
             */
            private final String availability;

            /**
             * The maximum cart quantity.
             */
            private final int maxAvailable;

            /**
             * The optional currency code.
             */
            private final String currency;

            /**
             * The optional price (string-encoded major-units integer).
             */
            private final String price;

            /**
             * Whether the product is hidden from the catalog grid.
             */
            private final boolean hidden;

            /**
             * Whether the product is sanctioned by Meta enforcement.
             */
            private final boolean sanctioned;

            /**
             * Whether the product is part of a featured set
             * ({@code belongs_to=true}).
             */
            private final boolean checkmark;

            /**
             * The optional whatsapp-side approval status (e.g.
             * {@code "APPROVED"}, {@code "PENDING"}).
             */
            private final String whatsappStatus;

            /**
             * Whether the product can be appealed from a rejection.
             */
            private final boolean canAppeal;

            /**
             * The list of decoded image entries, in wire order.
             */
            private final List<Image> images;

            /**
             * The list of decoded video entries, in wire order.
             */
            private final List<Video> videos;

            /**
             * The optional sale-price block.
             */
            private final SalePrice salePrice;

            /**
             * The optional compliance-info block.
             */
            private final ComplianceInfo complianceInfo;

            /**
             * The optional signed shimmed URL.
             */
            private final String signedShimmedUrl;

            /**
             * The optional compliance category marker (e.g.
             * {@code "Default"}).
             */
            private final String complianceCategory;

            /**
             * Constructs an invalid entry — only id and the
             * {@code invalid} flag are populated.
             *
             * @param id the product id; never {@code null}
             * @return an invalid product entry; never {@code null}
             * @throws NullPointerException if {@code id} is
             *                              {@code null}
             */
            public static Product invalid(String id) {
                Objects.requireNonNull(id, "id cannot be null");
                return new Product(id, true, null, null, null, null, null, 0, null, null,
                        false, false, false, null, false,
                        Collections.emptyList(), Collections.emptyList(),
                        null, null, null, null);
            }

            /**
             * Constructs a fully populated product entry.
             *
             * @param id                 see {@link #id()}
             * @param invalid            see {@link #invalid()}
             * @param name               see {@link #name()}
             * @param description        see {@link #description()}
             * @param url                see {@link #url()}
             * @param retailerId         see {@link #retailerId()}
             * @param availability       see {@link #availability()}
             * @param maxAvailable       see {@link #maxAvailable()}
             * @param currency           see {@link #currency()}
             * @param price              see {@link #price()}
             * @param hidden             see {@link #hidden()}
             * @param sanctioned         see {@link #sanctioned()}
             * @param checkmark          see {@link #checkmark()}
             * @param whatsappStatus     see {@link #whatsappStatus()}
             * @param canAppeal          see {@link #canAppeal()}
             * @param images             see {@link #images()}
             * @param videos             see {@link #videos()}
             * @param salePrice          see {@link #salePrice()}
             * @param complianceInfo     see {@link #complianceInfo()}
             * @param signedShimmedUrl   see
             *                           {@link #signedShimmedUrl()}
             * @param complianceCategory see
             *                           {@link #complianceCategory()}
             * @throws NullPointerException if {@code id},
             *                              {@code images}, or
             *                              {@code videos} is
             *                              {@code null}
             */
            public Product(String id,
                           boolean invalid,
                           String name,
                           String description,
                           String url,
                           String retailerId,
                           String availability,
                           int maxAvailable,
                           String currency,
                           String price,
                           boolean hidden,
                           boolean sanctioned,
                           boolean checkmark,
                           String whatsappStatus,
                           boolean canAppeal,
                           List<Image> images,
                           List<Video> videos,
                           SalePrice salePrice,
                           ComplianceInfo complianceInfo,
                           String signedShimmedUrl,
                           String complianceCategory) {
                this.id = Objects.requireNonNull(id, "id cannot be null");
                this.invalid = invalid;
                this.name = name;
                this.description = description;
                this.url = url;
                this.retailerId = retailerId;
                this.availability = availability;
                this.maxAvailable = maxAvailable;
                this.currency = currency;
                this.price = price;
                this.hidden = hidden;
                this.sanctioned = sanctioned;
                this.checkmark = checkmark;
                this.whatsappStatus = whatsappStatus;
                this.canAppeal = canAppeal;
                Objects.requireNonNull(images, "images cannot be null");
                this.images = List.copyOf(images);
                Objects.requireNonNull(videos, "videos cannot be null");
                this.videos = List.copyOf(videos);
                this.salePrice = salePrice;
                this.complianceInfo = complianceInfo;
                this.signedShimmedUrl = signedShimmedUrl;
                this.complianceCategory = complianceCategory;
            }

            /**
             * Returns the product id.
             *
             * @return the id; never {@code null}
             */
            public String id() {
                return id;
            }

            /**
             * Returns the invalid-product flag.
             *
             * @return {@code true} when the relay marked this entry as
             *         invalid
             */
            public boolean invalid() {
                return invalid;
            }

            /**
             * Returns the name.
             *
             * @return an {@link Optional} carrying the name
             */
            public Optional<String> name() {
                return Optional.ofNullable(name);
            }

            /**
             * Returns the description.
             *
             * @return an {@link Optional} carrying the description
             */
            public Optional<String> description() {
                return Optional.ofNullable(description);
            }

            /**
             * Returns the canonical URL.
             *
             * @return an {@link Optional} carrying the URL
             */
            public Optional<String> url() {
                return Optional.ofNullable(url);
            }

            /**
             * Returns the retailer-side id.
             *
             * @return an {@link Optional} carrying the id
             */
            public Optional<String> retailerId() {
                return Optional.ofNullable(retailerId);
            }

            /**
             * Returns the availability marker.
             *
             * @return an {@link Optional} carrying the marker
             */
            public Optional<String> availability() {
                return Optional.ofNullable(availability);
            }

            /**
             * Returns the maximum cart quantity.
             *
             * @return the cap
             */
            public int maxAvailable() {
                return maxAvailable;
            }

            /**
             * Returns the currency code.
             *
             * @return an {@link Optional} carrying the code
             */
            public Optional<String> currency() {
                return Optional.ofNullable(currency);
            }

            /**
             * Returns the price.
             *
             * @return an {@link Optional} carrying the price string
             */
            public Optional<String> price() {
                return Optional.ofNullable(price);
            }

            /**
             * Returns the hidden flag.
             *
             * @return {@code true} when hidden
             */
            public boolean hidden() {
                return hidden;
            }

            /**
             * Returns the sanctioned flag.
             *
             * @return {@code true} when sanctioned
             */
            public boolean sanctioned() {
                return sanctioned;
            }

            /**
             * Returns the featured-set flag.
             *
             * @return {@code true} when featured
             */
            public boolean checkmark() {
                return checkmark;
            }

            /**
             * Returns the whatsapp-side approval status.
             *
             * @return an {@link Optional} carrying the status
             */
            public Optional<String> whatsappStatus() {
                return Optional.ofNullable(whatsappStatus);
            }

            /**
             * Returns the can-appeal flag.
             *
             * @return {@code true} when the product can be appealed
             */
            public boolean canAppeal() {
                return canAppeal;
            }

            /**
             * Returns the image list.
             *
             * @return an unmodifiable list; never {@code null}
             */
            public List<Image> images() {
                return images;
            }

            /**
             * Returns the video list.
             *
             * @return an unmodifiable list; never {@code null}
             */
            public List<Video> videos() {
                return videos;
            }

            /**
             * Returns the sale-price block.
             *
             * @return an {@link Optional} carrying the block
             */
            public Optional<SalePrice> salePrice() {
                return Optional.ofNullable(salePrice);
            }

            /**
             * Returns the compliance-info block.
             *
             * @return an {@link Optional} carrying the block
             */
            public Optional<ComplianceInfo> complianceInfo() {
                return Optional.ofNullable(complianceInfo);
            }

            /**
             * Returns the signed shimmed URL.
             *
             * @return an {@link Optional} carrying the URL
             */
            public Optional<String> signedShimmedUrl() {
                return Optional.ofNullable(signedShimmedUrl);
            }

            /**
             * Returns the compliance-category marker.
             *
             * @return an {@link Optional} carrying the marker
             */
            public Optional<String> complianceCategory() {
                return Optional.ofNullable(complianceCategory);
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (Product) obj;
                return this.invalid == that.invalid
                        && this.maxAvailable == that.maxAvailable
                        && this.hidden == that.hidden
                        && this.sanctioned == that.sanctioned
                        && this.checkmark == that.checkmark
                        && this.canAppeal == that.canAppeal
                        && Objects.equals(this.id, that.id)
                        && Objects.equals(this.name, that.name)
                        && Objects.equals(this.description, that.description)
                        && Objects.equals(this.url, that.url)
                        && Objects.equals(this.retailerId, that.retailerId)
                        && Objects.equals(this.availability, that.availability)
                        && Objects.equals(this.currency, that.currency)
                        && Objects.equals(this.price, that.price)
                        && Objects.equals(this.whatsappStatus, that.whatsappStatus)
                        && Objects.equals(this.images, that.images)
                        && Objects.equals(this.videos, that.videos)
                        && Objects.equals(this.salePrice, that.salePrice)
                        && Objects.equals(this.complianceInfo, that.complianceInfo)
                        && Objects.equals(this.signedShimmedUrl, that.signedShimmedUrl)
                        && Objects.equals(this.complianceCategory, that.complianceCategory);
            }

            @Override
            public int hashCode() {
                var h = Objects.hash(id, invalid, name, description, url, retailerId,
                        availability, maxAvailable, currency, price, hidden, sanctioned,
                        checkmark, whatsappStatus, canAppeal);
                return 31 * h + Objects.hash(images, videos, salePrice, complianceInfo,
                        signedShimmedUrl, complianceCategory);
            }

            @Override
            public String toString() {
                return "IqQueryProductListCatalogResponse.Success.Product[id=" + id
                        + ", invalid=" + invalid + ", name=" + name + ", price=" + price
                        + ", currency=" + currency + ']';
            }
        }

        /**
         * One image entry — id, requested, and full URL.
         */
        public static final class Image {
            /**
             * The image id.
             */
            private final String id;

            /**
             * The requested-resolution URL (per-request resized).
             */
            private final String requestedUrl;

            /**
             * The original-resolution URL.
             */
            private final String fullUrl;

            /**
             * Constructs an image.
             *
             * @param id           the id; never {@code null}
             * @param requestedUrl the requested URL; never {@code null}
             * @param fullUrl      the full URL; never {@code null}
             * @throws NullPointerException if any argument is
             *                              {@code null}
             */
            public Image(String id, String requestedUrl, String fullUrl) {
                this.id = Objects.requireNonNull(id, "id cannot be null");
                this.requestedUrl = Objects.requireNonNull(requestedUrl, "requestedUrl cannot be null");
                this.fullUrl = Objects.requireNonNull(fullUrl, "fullUrl cannot be null");
            }

            /**
             * Returns the id.
             *
             * @return the id; never {@code null}
             */
            public String id() {
                return id;
            }

            /**
             * Returns the requested URL.
             *
             * @return the URL; never {@code null}
             */
            public String requestedUrl() {
                return requestedUrl;
            }

            /**
             * Returns the full URL.
             *
             * @return the URL; never {@code null}
             */
            public String fullUrl() {
                return fullUrl;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (Image) obj;
                return Objects.equals(this.id, that.id)
                        && Objects.equals(this.requestedUrl, that.requestedUrl)
                        && Objects.equals(this.fullUrl, that.fullUrl);
            }

            @Override
            public int hashCode() {
                return Objects.hash(id, requestedUrl, fullUrl);
            }

            @Override
            public String toString() {
                return "IqQueryProductListCatalogResponse.Success.Image[id=" + id
                        + ", requestedUrl=" + requestedUrl + ']';
            }
        }

        /**
         * One video entry — id, original URL, and thumbnail URL.
         */
        public static final class Video {
            /**
             * The video id.
             */
            private final String id;

            /**
             * The original-resolution video URL.
             */
            private final String originalVideoUrl;

            /**
             * The thumbnail URL.
             */
            private final String thumbnailUrl;

            /**
             * Constructs a video.
             *
             * @param id               the id; never {@code null}
             * @param originalVideoUrl the video URL; never {@code null}
             * @param thumbnailUrl     the thumbnail URL; never
             *                         {@code null}
             * @throws NullPointerException if any argument is
             *                              {@code null}
             */
            public Video(String id, String originalVideoUrl, String thumbnailUrl) {
                this.id = Objects.requireNonNull(id, "id cannot be null");
                this.originalVideoUrl = Objects.requireNonNull(
                        originalVideoUrl, "originalVideoUrl cannot be null");
                this.thumbnailUrl = Objects.requireNonNull(thumbnailUrl, "thumbnailUrl cannot be null");
            }

            /**
             * Returns the id.
             *
             * @return the id; never {@code null}
             */
            public String id() {
                return id;
            }

            /**
             * Returns the original-resolution URL.
             *
             * @return the URL; never {@code null}
             */
            public String originalVideoUrl() {
                return originalVideoUrl;
            }

            /**
             * Returns the thumbnail URL.
             *
             * @return the URL; never {@code null}
             */
            public String thumbnailUrl() {
                return thumbnailUrl;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (Video) obj;
                return Objects.equals(this.id, that.id)
                        && Objects.equals(this.originalVideoUrl, that.originalVideoUrl)
                        && Objects.equals(this.thumbnailUrl, that.thumbnailUrl);
            }

            @Override
            public int hashCode() {
                return Objects.hash(id, originalVideoUrl, thumbnailUrl);
            }

            @Override
            public String toString() {
                return "IqQueryProductListCatalogResponse.Success.Video[id=" + id
                        + ", originalVideoUrl=" + originalVideoUrl + ']';
            }
        }

        /**
         * One sale-price block — discounted price plus optional date
         * window.
         */
        public static final class SalePrice {
            /**
             * The sale price string.
             */
            private final String price;

            /**
             * The optional sale start date.
             */
            private final String startDate;

            /**
             * The optional sale end date.
             */
            private final String endDate;

            /**
             * Constructs a block.
             *
             * @param price     the price; never {@code null}
             * @param startDate the start date; may be {@code null}
             * @param endDate   the end date; may be {@code null}
             * @throws NullPointerException if {@code price} is
             *                              {@code null}
             */
            public SalePrice(String price, String startDate, String endDate) {
                this.price = Objects.requireNonNull(price, "price cannot be null");
                this.startDate = startDate;
                this.endDate = endDate;
            }

            /**
             * Returns the sale price.
             *
             * @return the price; never {@code null}
             */
            public String price() {
                return price;
            }

            /**
             * Returns the start date.
             *
             * @return an {@link Optional} carrying the date
             */
            public Optional<String> startDate() {
                return Optional.ofNullable(startDate);
            }

            /**
             * Returns the end date.
             *
             * @return an {@link Optional} carrying the date
             */
            public Optional<String> endDate() {
                return Optional.ofNullable(endDate);
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (SalePrice) obj;
                return Objects.equals(this.price, that.price)
                        && Objects.equals(this.startDate, that.startDate)
                        && Objects.equals(this.endDate, that.endDate);
            }

            @Override
            public int hashCode() {
                return Objects.hash(price, startDate, endDate);
            }

            @Override
            public String toString() {
                return "IqQueryProductListCatalogResponse.Success.SalePrice[price=" + price
                        + ", startDate=" + startDate + ", endDate=" + endDate + ']';
            }
        }

        /**
         * The compliance-info block — country of origin plus optional
         * importer details.
         */
        public static final class ComplianceInfo {
            /**
             * The country code of origin.
             */
            private final String countryCodeOrigin;

            /**
             * The optional importer name.
             */
            private final String importerName;

            /**
             * The optional importer address block.
             */
            private final ImporterAddress importerAddress;

            /**
             * Constructs a compliance-info block.
             *
             * @param countryCodeOrigin the country code; never
             *                          {@code null}
             * @param importerName      the importer name; may be
             *                          {@code null}
             * @param importerAddress   the importer address; may be
             *                          {@code null}
             * @throws NullPointerException if {@code countryCodeOrigin}
             *                              is {@code null}
             */
            public ComplianceInfo(String countryCodeOrigin, String importerName,
                                  ImporterAddress importerAddress) {
                this.countryCodeOrigin = Objects.requireNonNull(
                        countryCodeOrigin, "countryCodeOrigin cannot be null");
                this.importerName = importerName;
                this.importerAddress = importerAddress;
            }

            /**
             * Returns the country code.
             *
             * @return the code; never {@code null}
             */
            public String countryCodeOrigin() {
                return countryCodeOrigin;
            }

            /**
             * Returns the importer name.
             *
             * @return an {@link Optional} carrying the name
             */
            public Optional<String> importerName() {
                return Optional.ofNullable(importerName);
            }

            /**
             * Returns the importer address.
             *
             * @return an {@link Optional} carrying the address
             */
            public Optional<ImporterAddress> importerAddress() {
                return Optional.ofNullable(importerAddress);
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (ComplianceInfo) obj;
                return Objects.equals(this.countryCodeOrigin, that.countryCodeOrigin)
                        && Objects.equals(this.importerName, that.importerName)
                        && Objects.equals(this.importerAddress, that.importerAddress);
            }

            @Override
            public int hashCode() {
                return Objects.hash(countryCodeOrigin, importerName, importerAddress);
            }

            @Override
            public String toString() {
                return "IqQueryProductListCatalogResponse.Success.ComplianceInfo[countryCodeOrigin="
                        + countryCodeOrigin + ", importerName=" + importerName + ']';
            }
        }

        /**
         * The importer address block — street, city, region, postcode,
         * country.
         */
        public static final class ImporterAddress {
            /**
             * The street line 1.
             */
            private final String street1;

            /**
             * The optional street line 2.
             */
            private final String street2;

            /**
             * The optional postal code.
             */
            private final String postalCode;

            /**
             * The city.
             */
            private final String city;

            /**
             * The optional region.
             */
            private final String region;

            /**
             * The country code.
             */
            private final String countryCode;

            /**
             * Constructs an address.
             *
             * @param street1     the line-1 street; never {@code null}
             * @param street2     the line-2 street; may be {@code null}
             * @param postalCode  the postal code; may be {@code null}
             * @param city        the city; never {@code null}
             * @param region      the region; may be {@code null}
             * @param countryCode the country code; never {@code null}
             * @throws NullPointerException if {@code street1},
             *                              {@code city} or
             *                              {@code countryCode} is
             *                              {@code null}
             */
            public ImporterAddress(String street1, String street2, String postalCode,
                                   String city, String region, String countryCode) {
                this.street1 = Objects.requireNonNull(street1, "street1 cannot be null");
                this.street2 = street2;
                this.postalCode = postalCode;
                this.city = Objects.requireNonNull(city, "city cannot be null");
                this.region = region;
                this.countryCode = Objects.requireNonNull(countryCode, "countryCode cannot be null");
            }

            /**
             * Returns the line-1 street.
             *
             * @return the street; never {@code null}
             */
            public String street1() {
                return street1;
            }

            /**
             * Returns the line-2 street.
             *
             * @return an {@link Optional} carrying the street
             */
            public Optional<String> street2() {
                return Optional.ofNullable(street2);
            }

            /**
             * Returns the postal code.
             *
             * @return an {@link Optional} carrying the code
             */
            public Optional<String> postalCode() {
                return Optional.ofNullable(postalCode);
            }

            /**
             * Returns the city.
             *
             * @return the city; never {@code null}
             */
            public String city() {
                return city;
            }

            /**
             * Returns the region.
             *
             * @return an {@link Optional} carrying the region
             */
            public Optional<String> region() {
                return Optional.ofNullable(region);
            }

            /**
             * Returns the country code.
             *
             * @return the code; never {@code null}
             */
            public String countryCode() {
                return countryCode;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (ImporterAddress) obj;
                return Objects.equals(this.street1, that.street1)
                        && Objects.equals(this.street2, that.street2)
                        && Objects.equals(this.postalCode, that.postalCode)
                        && Objects.equals(this.city, that.city)
                        && Objects.equals(this.region, that.region)
                        && Objects.equals(this.countryCode, that.countryCode);
            }

            @Override
            public int hashCode() {
                return Objects.hash(street1, street2, postalCode, city, region, countryCode);
            }

            @Override
            public String toString() {
                return "IqQueryProductListCatalogResponse.Success.ImporterAddress[street1=" + street1
                        + ", city=" + city + ", countryCode=" + countryCode + ']';
            }
        }

        /**
         * The decoded products, in wire order.
         */
        private final List<Product> products;

        /**
         * Constructs a successful reply.
         *
         * @param products the product list; never {@code null}
         * @throws NullPointerException if {@code products} is
         *                              {@code null}
         */
        public Success(List<Product> products) {
            Objects.requireNonNull(products, "products cannot be null");
            this.products = List.copyOf(products);
        }

        /**
         * Returns the product list.
         *
         * @return an unmodifiable list; never {@code null}
         */
        public List<Product> products() {
            return products;
        }

        /**
         * Tries to parse a {@link Success} variant.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty when the stanza does not match the success
         *         schema
         */
        @WhatsAppWebExport(moduleName = "WAWebQueryProductListCatalogJob",
                exports = "QueryProductListCatalog", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var productListNode = node.getChild("product_list").orElse(null);
            if (productListNode == null) {
                return Optional.of(new Success(Collections.emptyList()));
            }
            var products = new ArrayList<Product>();
            for (var productNode : productListNode.getChildren("product")) {
                var idNode = productNode.getChild("id").orElse(null);
                if (idNode == null) {
                    continue;
                }
                var id = idNode.toContentString().orElse(null);
                if (id == null) {
                    continue;
                }
                var statusNode = productNode.getChild("status").orElse(null);
                if (statusNode != null
                        && "INVALID_PRODUCT".equals(statusNode.toContentString().orElse(""))) {
                    products.add(Product.invalid(id));
                    continue;
                }
                products.add(parseProduct(productNode, id));
            }
            return Optional.of(new Success(products));
        }

        /**
         * Decodes a single {@code <product/>} child into the typed
         * {@link Product} projection — mirrors
         * {@code WAWebBizCatalogParseProduct.parseProductNode}.
         *
         * @param productNode the {@code <product/>} child
         * @param id          the parsed product id
         * @return the decoded product entry; never {@code null}
         */
        private static Product parseProduct(Node productNode, String id) {
            var name = productNode.getChild("name")
                    .flatMap(Node::toContentString).orElse(null);
            var description = productNode.getChild("description")
                    .flatMap(Node::toContentString).orElse(null);
            var url = productNode.getChild("url")
                    .flatMap(Node::toContentString).orElse(null);
            var retailerId = productNode.getChild("retailer_id")
                    .flatMap(Node::toContentString).orElse(null);
            var availability = productNode.getAttributeAsString("availability").orElse(null);
            var currency = productNode.getChild("currency")
                    .flatMap(Node::toContentString).orElse(null);
            var price = productNode.getChild("price")
                    .flatMap(Node::toContentString).orElse(null);
            var hidden = productNode.getAttributeAsString("is_hidden")
                    .map("true"::equals).orElse(false);
            var sanctioned = productNode.getAttributeAsString("is_sanctioned")
                    .map("true"::equals).orElse(false);
            var checkmark = productNode.getChild("belongs_to")
                    .flatMap(Node::toContentString)
                    .map("true"::equals).orElse(false);
            var complianceCategory = productNode.getAttributeAsString("compliance_category").orElse(null);
            var maxAvailable = 99;
            var maxAvailableAttr = productNode.getAttributeAsString("max_available").orElse(null);
            if (maxAvailableAttr != null) {
                try {
                    maxAvailable = Integer.parseInt(maxAvailableAttr);
                } catch (NumberFormatException _) {
                }
            }
            var maxAvailableChild = productNode.getChild("max_available")
                    .flatMap(Node::toContentString).orElse(null);
            if (maxAvailableChild != null) {
                try {
                    maxAvailable = Integer.parseInt(maxAvailableChild);
                } catch (NumberFormatException _) {
                }
            }
            String whatsappStatus = null;
            var canAppeal = false;
            var statusInfoNode = productNode.getChild("status_info").orElse(null);
            if (statusInfoNode != null) {
                whatsappStatus = statusInfoNode.getChild("status")
                        .flatMap(Node::toContentString).orElse(null);
                canAppeal = statusInfoNode.getChild("can_appeal")
                        .flatMap(Node::toContentString)
                        .map("true"::equals).orElse(false);
            }
            var images = new ArrayList<Image>();
            var videos = new ArrayList<Video>();
            productNode.getChild("media").ifPresent(media -> {
                for (var img : media.getChildren("image")) {
                    var imgId = img.getChild("id")
                            .flatMap(Node::toContentString).orElse("");
                    var requested = img.getChild("request_image_url")
                            .flatMap(Node::toContentString).orElse("");
                    var full = img.getChild("original_image_url")
                            .flatMap(Node::toContentString).orElse("");
                    images.add(new Image(imgId, requested, full));
                }
                for (var vid : media.getChildren("video")) {
                    var vidId = vid.getChild("id")
                            .flatMap(Node::toContentString).orElse("");
                    var original = vid.getChild("original_video_url")
                            .flatMap(Node::toContentString).orElse("");
                    var thumb = vid.getChild("thumbnail_url")
                            .flatMap(Node::toContentString).orElse("");
                    videos.add(new Video(vidId, original, thumb));
                }
            });
            SalePrice salePrice = null;
            var salePriceNode = productNode.getChild("sale_price").orElse(null);
            if (salePriceNode != null) {
                var sp = salePriceNode.getChild("price")
                        .flatMap(Node::toContentString).orElse("");
                var start = salePriceNode.getChild("start_date")
                        .flatMap(Node::toContentString).orElse(null);
                var end = salePriceNode.getChild("end_date")
                        .flatMap(Node::toContentString).orElse(null);
                salePrice = new SalePrice(sp, start, end);
            }
            ComplianceInfo complianceInfo = null;
            var complianceInfoNode = productNode.getChild("compliance_info").orElse(null);
            if (complianceInfoNode != null) {
                var country = complianceInfoNode.getChild("country_code_origin")
                        .flatMap(Node::toContentString).orElse("");
                var importerName = complianceInfoNode.getChild("importer_name")
                        .flatMap(Node::toContentString).orElse(null);
                ImporterAddress importerAddress = null;
                var importerAddressNode = complianceInfoNode.getChild("importer_address").orElse(null);
                if (importerAddressNode != null) {
                    var street1 = importerAddressNode.getChild("street1")
                            .flatMap(Node::toContentString).orElse("");
                    var street2 = importerAddressNode.getChild("street2")
                            .flatMap(Node::toContentString).orElse(null);
                    var postal = importerAddressNode.getChild("postal_code")
                            .flatMap(Node::toContentString).orElse(null);
                    var city = importerAddressNode.getChild("city")
                            .flatMap(Node::toContentString).orElse("");
                    var region = importerAddressNode.getChild("region")
                            .flatMap(Node::toContentString).orElse(null);
                    var ccode = importerAddressNode.getChild("country_code")
                            .flatMap(Node::toContentString).orElse("");
                    importerAddress = new ImporterAddress(street1, street2, postal, city, region, ccode);
                }
                complianceInfo = new ComplianceInfo(country, importerName, importerAddress);
            }
            var signedShimmedUrl = productNode.getChild("shimmed_url")
                    .flatMap(Node::toContentString)
                    .filter(s -> !s.isEmpty()).orElse(null);
            return new Product(id, false, name, description, url, retailerId, availability,
                    maxAvailable, currency, price, hidden, sanctioned, checkmark,
                    whatsappStatus, canAppeal, images, videos, salePrice, complianceInfo,
                    signedShimmedUrl, complianceCategory);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Success) obj;
            return Objects.equals(this.products, that.products);
        }

        @Override
        public int hashCode() {
            return Objects.hash(products);
        }

        @Override
        public String toString() {
            return "IqQueryProductListCatalogResponse.Success[products=" + products + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant.
     */
    final class ClientError implements IqQueryProductListCatalogResponse {
        /**
         * The numeric error code.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a client-error reply.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be
         *                  {@code null}
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the human-readable error text, when supplied.
         *
         * @return an {@link Optional} carrying the error text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty when the stanza does not match the
         *         client-error schema
         */
        public static Optional<ClientError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ClientError(envelope.code(), envelope.text()));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ClientError) obj;
            return this.errorCode == that.errorCode && Objects.equals(this.errorText, that.errorText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "IqQueryProductListCatalogResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant.
     */
    final class ServerError implements IqQueryProductListCatalogResponse {
        /**
         * The numeric error code.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a server-error reply.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be
         *                  {@code null}
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the human-readable error text, when supplied.
         *
         * @return an {@link Optional} carrying the error text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty when the stanza does not match the
         *         server-error schema
         */
        public static Optional<ServerError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ServerError(envelope.code(), envelope.text()));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ServerError) obj;
            return this.errorCode == that.errorCode && Objects.equals(this.errorText, that.errorText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "IqQueryProductListCatalogResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
