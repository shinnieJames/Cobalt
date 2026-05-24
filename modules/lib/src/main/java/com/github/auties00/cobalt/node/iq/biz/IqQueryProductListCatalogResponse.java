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
 * Sealed family of inbound reply variants the relay produces in response
 * to an {@link IqQueryProductListCatalogRequest}.
 *
 * @apiNote
 * Pattern-match the returned variant to drive the catalog grid:
 * {@link Success} carries the typed product entries (full body or
 * synthetic "INVALID_PRODUCT" marker), {@link ClientError} surfaces a
 * rejected request and {@link ServerError} surfaces a transient
 * internal failure.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryProductListCatalogJob")
public sealed interface IqQueryProductListCatalogResponse extends IqOperation.Response
        permits IqQueryProductListCatalogResponse.Success, IqQueryProductListCatalogResponse.ClientError, IqQueryProductListCatalogResponse.ServerError {

    /**
     * Tries each variant in priority order until one matches.
     *
     * @apiNote
     * Use this entry point on every IQ stanza tagged with the
     * {@code <product_list/>} payload; the order is {@link Success},
     * then {@link ClientError}, then {@link ServerError}.
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
     * The {@code Success} variant carrying the typed product list.
     *
     * @apiNote
     * Use {@link #products()} to drive the catalog grid; each
     * {@link Product} entry is either a fully populated body or the
     * synthetic {@link Product#invalid(String) invalid marker} for ids
     * the relay flagged as no longer fetchable.
     */
    final class Success implements IqQueryProductListCatalogResponse {
        /**
         * One typed product entry decoded from a {@code <product/>}
         * child of the {@code <product_list/>} payload.
         *
         * @apiNote
         * Pattern on {@link #invalid()} before reading the remaining
         * fields; an invalid entry leaves every other field unset and
         * surfaces only its id.
         */
        public static final class Product {
            /**
             * The opaque product id.
             */
            private final String id;

            /**
             * Whether this entry is the synthetic
             * {@code "INVALID_PRODUCT"} marker; when {@code true} the
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
             * Constructs the synthetic invalid-product marker.
             *
             * @apiNote
             * Call this factory when the relay echoes a
             * {@code <product><status>INVALID_PRODUCT</status></product>}
             * child; the resulting entry only carries the id and the
             * invalid flag, every other field is unset.
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
             * @apiNote
             * Use this constructor only from the response parser; the
             * field-by-field optional shape matches the wire echo so
             * the catalog grid can render each entry without further
             * normalisation.
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
             * Returns the opaque product id.
             *
             * @apiNote
             * Use this getter as the catalog-grid key; the relay
             * echoes the same id for both valid and invalid entries.
             *
             * @return the id; never {@code null}
             */
            public String id() {
                return id;
            }

            /**
             * Returns the invalid-product flag.
             *
             * @apiNote
             * Use this getter to gate access to the remaining fields;
             * when {@code true} only {@link #id()} is meaningful and
             * the catalog grid should render a "product no longer
             * available" placeholder.
             *
             * @return {@code true} when the relay marked this entry as
             *         invalid
             */
            public boolean invalid() {
                return invalid;
            }

            /**
             * Returns the product display name.
             *
             * @apiNote
             * Use this getter to drive the catalog-card title; the
             * relay echoes the merchant-supplied raw string without
             * normalisation.
             *
             * @return an {@link Optional} carrying the name
             */
            public Optional<String> name() {
                return Optional.ofNullable(name);
            }

            /**
             * Returns the description body.
             *
             * @apiNote
             * Use this getter to drive the catalog-card body; the
             * relay echoes the merchant-supplied raw string without
             * normalisation.
             *
             * @return an {@link Optional} carrying the description
             */
            public Optional<String> description() {
                return Optional.ofNullable(description);
            }

            /**
             * Returns the canonical product URL.
             *
             * @apiNote
             * Use this getter to drive the "open in browser"
             * affordance on the catalog card.
             *
             * @return an {@link Optional} carrying the URL
             */
            public Optional<String> url() {
                return Optional.ofNullable(url);
            }

            /**
             * Returns the retailer-side identifier.
             *
             * @apiNote
             * Use this getter to map the entry back to the merchant's
             * external ERP / commerce platform; the relay echoes the
             * merchant-supplied SKU verbatim.
             *
             * @return an {@link Optional} carrying the id
             */
            public Optional<String> retailerId() {
                return Optional.ofNullable(retailerId);
            }

            /**
             * Returns the availability marker.
             *
             * @apiNote
             * Use this getter to drive the catalog-card stock badge;
             * the relay echoes the merchant-supplied marker (e.g.
             * {@code "in stock"}, {@code "out of stock"}) verbatim.
             *
             * @return an {@link Optional} carrying the marker
             */
            public Optional<String> availability() {
                return Optional.ofNullable(availability);
            }

            /**
             * Returns the maximum cart quantity.
             *
             * @apiNote
             * Use this getter to cap the quantity selector on the
             * catalog card; the default is 99 when the relay omits
             * the field.
             *
             * @return the cap
             */
            public int maxAvailable() {
                return maxAvailable;
            }

            /**
             * Returns the currency code.
             *
             * @apiNote
             * Use this getter together with {@link #price()} to
             * format the catalog-card price string; the relay echoes
             * the ISO 4217 code.
             *
             * @return an {@link Optional} carrying the code
             */
            public Optional<String> currency() {
                return Optional.ofNullable(currency);
            }

            /**
             * Returns the price string.
             *
             * @apiNote
             * Use this getter to drive the catalog-card price label;
             * the relay echoes the price as a major-units integer
             * string (e.g. {@code "1999"} for 19.99 in the
             * {@link #currency()} unit).
             *
             * @return an {@link Optional} carrying the price string
             */
            public Optional<String> price() {
                return Optional.ofNullable(price);
            }

            /**
             * Returns the hidden flag.
             *
             * @apiNote
             * Use this getter to filter the catalog grid; hidden
             * entries are returned by id-list fetches but not by
             * collection listings.
             *
             * @return {@code true} when hidden
             */
            public boolean hidden() {
                return hidden;
            }

            /**
             * Returns the sanctioned flag.
             *
             * @apiNote
             * Use this getter to hide the entry from the catalog
             * grid; sanctioned products are flagged by Meta
             * enforcement and cannot be checked out.
             *
             * @return {@code true} when sanctioned
             */
            public boolean sanctioned() {
                return sanctioned;
            }

            /**
             * Returns the featured-set flag (corresponds to the
             * {@code <belongs_to/>} grandchild).
             *
             * @apiNote
             * Use this getter to badge the entry on the catalog grid
             * (the "featured" checkmark surface); WA Web maps it to
             * the curated-collection membership flag.
             *
             * @return {@code true} when featured
             */
            public boolean checkmark() {
                return checkmark;
            }

            /**
             * Returns the whatsapp-side approval status.
             *
             * @apiNote
             * Use this getter to surface review-state badges to the
             * merchant on the catalog-management surface (e.g.
             * {@code "APPROVED"}, {@code "PENDING"},
             * {@code "REJECTED"}).
             *
             * @return an {@link Optional} carrying the status
             */
            public Optional<String> whatsappStatus() {
                return Optional.ofNullable(whatsappStatus);
            }

            /**
             * Returns the can-appeal flag.
             *
             * @apiNote
             * Use this getter to enable the appeal CTA on the
             * catalog-management surface for rejected products.
             *
             * @return {@code true} when the product can be appealed
             */
            public boolean canAppeal() {
                return canAppeal;
            }

            /**
             * Returns the image list.
             *
             * @apiNote
             * Use this getter to drive the catalog-card image
             * carousel; the order matches the merchant-supplied
             * upload order.
             *
             * @return an unmodifiable list; never {@code null}
             */
            public List<Image> images() {
                return images;
            }

            /**
             * Returns the video list.
             *
             * @apiNote
             * Use this getter to drive the catalog-card video
             * carousel; the order matches the merchant-supplied
             * upload order.
             *
             * @return an unmodifiable list; never {@code null}
             */
            public List<Video> videos() {
                return videos;
            }

            /**
             * Returns the sale-price block.
             *
             * @apiNote
             * Use this getter to drive the catalog-card "sale price"
             * label; an empty optional means the product is not on
             * sale.
             *
             * @return an {@link Optional} carrying the block
             */
            public Optional<SalePrice> salePrice() {
                return Optional.ofNullable(salePrice);
            }

            /**
             * Returns the compliance-info block.
             *
             * @apiNote
             * Use this getter to render the regulatory disclosure
             * block under the catalog card (country of origin,
             * importer name / address); required in India and other
             * markets.
             *
             * @return an {@link Optional} carrying the block
             */
            public Optional<ComplianceInfo> complianceInfo() {
                return Optional.ofNullable(complianceInfo);
            }

            /**
             * Returns the signed shimmed URL.
             *
             * @apiNote
             * Use this getter when ad-shimlinking is enabled; the URL
             * is a signed proxy that surfaces ad-click telemetry
             * before redirecting to {@link #url()}.
             *
             * @return an {@link Optional} carrying the URL
             */
            public Optional<String> signedShimmedUrl() {
                return Optional.ofNullable(signedShimmedUrl);
            }

            /**
             * Returns the compliance-category marker.
             *
             * @apiNote
             * Use this getter to dispatch on the product's compliance
             * profile (e.g. {@code "Default"},
             * {@code "BodyEnhancement"}); the value gates which
             * disclosure fields the catalog card must display.
             *
             * @return an {@link Optional} carrying the marker
             */
            public Optional<String> complianceCategory() {
                return Optional.ofNullable(complianceCategory);
            }

            /**
             * {@inheritDoc}
             */
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

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                var h = Objects.hash(id, invalid, name, description, url, retailerId,
                        availability, maxAvailable, currency, price, hidden, sanctioned,
                        checkmark, whatsappStatus, canAppeal);
                return 31 * h + Objects.hash(images, videos, salePrice, complianceInfo,
                        signedShimmedUrl, complianceCategory);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryProductListCatalogResponse.Success.Product[id=" + id
                        + ", invalid=" + invalid + ", name=" + name + ", price=" + price
                        + ", currency=" + currency + ']';
            }
        }

        /**
         * One image entry on a {@link Product}: id, requested-resolution
         * URL, and original-resolution URL.
         *
         * @apiNote
         * Use the requested-resolution URL on the catalog-card
         * thumbnail and the full URL on the product-detail surface;
         * both URLs are signed and short-lived.
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
             * @apiNote
             * Use this constructor only from the response parser; all
             * three fields are read from the {@code <media><image/>}
             * grandchild.
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
             * Returns the image id.
             *
             * @apiNote
             * Use this getter as a stable key for the catalog-card
             * carousel; the relay keeps the id stable across uploads.
             *
             * @return the id; never {@code null}
             */
            public String id() {
                return id;
            }

            /**
             * Returns the requested-resolution URL.
             *
             * @apiNote
             * Use this getter to drive the catalog-card thumbnail;
             * the URL is signed at the resolution the caller requested
             * via {@link IqQueryProductListCatalogRequest#width()} and
             * {@link IqQueryProductListCatalogRequest#height()}.
             *
             * @return the URL; never {@code null}
             */
            public String requestedUrl() {
                return requestedUrl;
            }

            /**
             * Returns the original-resolution URL.
             *
             * @apiNote
             * Use this getter to drive the product-detail surface;
             * the URL is signed at the original upload resolution.
             *
             * @return the URL; never {@code null}
             */
            public String fullUrl() {
                return fullUrl;
            }

            /**
             * {@inheritDoc}
             */
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

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(id, requestedUrl, fullUrl);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryProductListCatalogResponse.Success.Image[id=" + id
                        + ", requestedUrl=" + requestedUrl + ']';
            }
        }

        /**
         * One video entry on a {@link Product}: id, original-resolution
         * URL, and poster-frame thumbnail URL.
         *
         * @apiNote
         * Use the original URL to drive the in-line video player on
         * the product-detail surface and the thumbnail URL as the
         * carousel poster frame; both URLs are signed and short-lived.
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
             * @apiNote
             * Use this constructor only from the response parser; all
             * three fields are read from the {@code <media><video/>}
             * grandchild.
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
             * Returns the video id.
             *
             * @apiNote
             * Use this getter as a stable key for the catalog-card
             * carousel.
             *
             * @return the id; never {@code null}
             */
            public String id() {
                return id;
            }

            /**
             * Returns the original-resolution video URL.
             *
             * @apiNote
             * Use this getter to drive the in-line video player on
             * the product-detail surface.
             *
             * @return the URL; never {@code null}
             */
            public String originalVideoUrl() {
                return originalVideoUrl;
            }

            /**
             * Returns the poster-frame thumbnail URL.
             *
             * @apiNote
             * Use this getter as the carousel poster frame before
             * playback starts.
             *
             * @return the URL; never {@code null}
             */
            public String thumbnailUrl() {
                return thumbnailUrl;
            }

            /**
             * {@inheritDoc}
             */
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

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(id, originalVideoUrl, thumbnailUrl);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryProductListCatalogResponse.Success.Video[id=" + id
                        + ", originalVideoUrl=" + originalVideoUrl + ']';
            }
        }

        /**
         * One sale-price block on a {@link Product}: discounted price
         * plus optional sale date window.
         *
         * @apiNote
         * Use this block to drive the "sale price" label on the
         * catalog card; the start and end dates are optional and the
         * catalog card surfaces an indefinite sale when both are
         * absent.
         */
        public static final class SalePrice {
            /**
             * The sale-price string (same major-units encoding as
             * {@link Product#price()}).
             */
            private final String price;

            /**
             * The optional sale start date in
             * {@code YYYY-MM-DD'T'HH:MM:SS}.
             */
            private final String startDate;

            /**
             * The optional sale end date in
             * {@code YYYY-MM-DD'T'HH:MM:SS}.
             */
            private final String endDate;

            /**
             * Constructs a block.
             *
             * @apiNote
             * Use this constructor only from the response parser;
             * the start and end dates are independently optional on
             * the wire.
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
             * @apiNote
             * Use this getter to drive the catalog-card "sale price"
             * label; the encoding matches {@link Product#price()}.
             *
             * @return the price; never {@code null}
             */
            public String price() {
                return price;
            }

            /**
             * Returns the start date.
             *
             * @apiNote
             * Use this getter to gate the "sale price" label on the
             * catalog card; an empty optional means the sale has no
             * defined start.
             *
             * @return an {@link Optional} carrying the date
             */
            public Optional<String> startDate() {
                return Optional.ofNullable(startDate);
            }

            /**
             * Returns the end date.
             *
             * @apiNote
             * Use this getter to gate the "sale price" label on the
             * catalog card; an empty optional means the sale has no
             * defined end.
             *
             * @return an {@link Optional} carrying the date
             */
            public Optional<String> endDate() {
                return Optional.ofNullable(endDate);
            }

            /**
             * {@inheritDoc}
             */
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

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(price, startDate, endDate);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryProductListCatalogResponse.Success.SalePrice[price=" + price
                        + ", startDate=" + startDate + ", endDate=" + endDate + ']';
            }
        }

        /**
         * One compliance-info block on a {@link Product}: country of
         * origin plus optional importer details.
         *
         * @apiNote
         * Use this block to render the regulatory disclosure block
         * under the catalog card; required for products imported into
         * markets such as India where the importer name and address
         * must be visible to the buyer.
         */
        public static final class ComplianceInfo {
            /**
             * The ISO 3166-1 alpha-2 country code of origin.
             */
            private final String countryCodeOrigin;

            /**
             * The optional importer legal name.
             */
            private final String importerName;

            /**
             * The optional importer address block.
             */
            private final ImporterAddress importerAddress;

            /**
             * Constructs a compliance-info block.
             *
             * @apiNote
             * Use this constructor only from the response parser;
             * the country code is the only mandatory field and the
             * importer fields are independently optional.
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
             * Returns the country of origin code.
             *
             * @apiNote
             * Use this getter to render the "Country of origin" line
             * of the regulatory disclosure block.
             *
             * @return the code; never {@code null}
             */
            public String countryCodeOrigin() {
                return countryCodeOrigin;
            }

            /**
             * Returns the importer legal name.
             *
             * @apiNote
             * Use this getter to render the "Importer" line of the
             * regulatory disclosure block; an empty optional means
             * the relay omitted the field.
             *
             * @return an {@link Optional} carrying the name
             */
            public Optional<String> importerName() {
                return Optional.ofNullable(importerName);
            }

            /**
             * Returns the importer address.
             *
             * @apiNote
             * Use this getter to render the importer address lines
             * of the regulatory disclosure block; an empty optional
             * means the relay omitted the block.
             *
             * @return an {@link Optional} carrying the address
             */
            public Optional<ImporterAddress> importerAddress() {
                return Optional.ofNullable(importerAddress);
            }

            /**
             * {@inheritDoc}
             */
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

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(countryCodeOrigin, importerName, importerAddress);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryProductListCatalogResponse.Success.ComplianceInfo[countryCodeOrigin="
                        + countryCodeOrigin + ", importerName=" + importerName + ']';
            }
        }

        /**
         * The importer address block on a {@link ComplianceInfo}:
         * street, city, region, postcode, country.
         *
         * @apiNote
         * Use this block to render the importer address lines of the
         * regulatory disclosure block; street1, city and country
         * code are mandatory while the remaining lines are optional.
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
             * The optional state, region or province.
             */
            private final String region;

            /**
             * The ISO 3166-1 alpha-2 country code.
             */
            private final String countryCode;

            /**
             * Constructs an address.
             *
             * @apiNote
             * Use this constructor only from the response parser;
             * the wire schema requires street1, city and country
             * code to be present.
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
             * @apiNote
             * Use this getter to render the first street line of the
             * importer address.
             *
             * @return the street; never {@code null}
             */
            public String street1() {
                return street1;
            }

            /**
             * Returns the line-2 street.
             *
             * @apiNote
             * Use this getter to render the optional second street
             * line of the importer address; an empty optional means
             * the relay omitted the field.
             *
             * @return an {@link Optional} carrying the street
             */
            public Optional<String> street2() {
                return Optional.ofNullable(street2);
            }

            /**
             * Returns the postal code.
             *
             * @apiNote
             * Use this getter to render the optional postal-code
             * field of the importer address; an empty optional means
             * the relay omitted the field.
             *
             * @return an {@link Optional} carrying the code
             */
            public Optional<String> postalCode() {
                return Optional.ofNullable(postalCode);
            }

            /**
             * Returns the city.
             *
             * @apiNote
             * Use this getter to render the city line of the
             * importer address.
             *
             * @return the city; never {@code null}
             */
            public String city() {
                return city;
            }

            /**
             * Returns the state, region or province.
             *
             * @apiNote
             * Use this getter to render the optional region line of
             * the importer address; an empty optional means the
             * relay omitted the field.
             *
             * @return an {@link Optional} carrying the region
             */
            public Optional<String> region() {
                return Optional.ofNullable(region);
            }

            /**
             * Returns the ISO 3166-1 alpha-2 country code.
             *
             * @apiNote
             * Use this getter to render the country line of the
             * importer address.
             *
             * @return the code; never {@code null}
             */
            public String countryCode() {
                return countryCode;
            }

            /**
             * {@inheritDoc}
             */
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

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(street1, street2, postalCode, city, region, countryCode);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryProductListCatalogResponse.Success.ImporterAddress[street1=" + street1
                        + ", city=" + city + ", countryCode=" + countryCode + ']';
            }
        }

        /**
         * The decoded products in wire order.
         */
        private final List<Product> products;

        /**
         * Constructs a successful reply.
         *
         * @apiNote
         * Use this constructor only from {@link #of(Node, Node)};
         * the supplied list is defensively copied so the caller may
         * mutate the source freely after construction.
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
         * Returns the decoded products.
         *
         * @apiNote
         * Use this getter to drive the catalog grid; the order
         * matches the wire order, which matches the
         * caller-supplied id order on the
         * {@link IqQueryProductListCatalogRequest}.
         *
         * @return an unmodifiable list; never {@code null}
         */
        public List<Product> products() {
            return products;
        }

        /**
         * Tries to parse a {@link Success} variant.
         *
         * @apiNote
         * Call this from {@link #of(Node, Node)}; the method
         * validates the {@code <iq type="result">} envelope, iterates
         * over every {@code <product/>} child of the
         * {@code <product_list/>} payload and dispatches to
         * {@link #parseProduct(Node, String)} for non-invalid
         * entries.
         *
         * @implNote
         * This implementation matches the
         * {@code WAWebQueryProductListCatalogJob.QueryProductListCatalog}
         * WAP-IQ path; the synthetic invalid-product marker is
         * detected by checking the {@code <status/>} grandchild for
         * the literal {@code "INVALID_PRODUCT"} body, matching
         * {@code WAWebProductMessageListConstant.INVALID_PRODUCT_TOKEN}.
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
         * {@link Product} projection.
         *
         * @apiNote
         * Helper for {@link #of(Node, Node)}; reads every supported
         * grandchild and attribute of the product node and assembles
         * a fully populated entry.
         *
         * @implNote
         * This implementation mirrors
         * {@code WAWebBizCatalogParseProduct.parseProductNode}; the
         * {@code max_available} field is read from either the
         * attribute or the grandchild (the relay uses both shapes
         * across snapshots) and the cap defaults to 99 when both are
         * absent or unparseable.
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

        /**
         * {@inheritDoc}
         */
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

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(products);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "IqQueryProductListCatalogResponse.Success[products=" + products + ']';
        }
    }

    /**
     * The {@code ClientError} variant emitted when the relay rejects
     * the request as malformed or referencing an unknown merchant /
     * catalog.
     *
     * @apiNote
     * Use this variant to surface a user-facing 4xx-class error to
     * the catalog grid.
     */
    final class ClientError implements IqQueryProductListCatalogResponse {
        /**
         * The numeric error code echoed by the {@code <error/>} child.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text echoed by the
         * {@code <error/>} child.
         */
        private final String errorText;

        /**
         * Constructs a client-error reply.
         *
         * @apiNote
         * Use this constructor only from {@link #of(Node, Node)}; the
         * (code, text) pair comes from the relay's {@code <error/>}
         * envelope.
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
         * @apiNote
         * Use this getter to dispatch on the relay-side error code
         * when surfacing a localised message to the catalog grid.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the human-readable error text, when supplied.
         *
         * @apiNote
         * Use this getter for logging; the text is server-localised
         * and not stable across snapshots.
         *
         * @return an {@link Optional} carrying the error text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant.
         *
         * @apiNote
         * Call this from {@link #of(Node, Node)}; the method delegates
         * to {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}
         * to extract the (code, text) envelope.
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

        /**
         * {@inheritDoc}
         */
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

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "IqQueryProductListCatalogResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} variant emitted when the relay returns
     * a transient internal-failure status while processing the
     * request.
     *
     * @apiNote
     * Use this variant to drive a backoff-and-retry path in the
     * catalog grid; the relay returns this shape when the catalog
     * backend is temporarily unavailable.
     */
    final class ServerError implements IqQueryProductListCatalogResponse {
        /**
         * The numeric error code echoed by the {@code <error/>} child.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text echoed by the
         * {@code <error/>} child.
         */
        private final String errorText;

        /**
         * Constructs a server-error reply.
         *
         * @apiNote
         * Use this constructor only from {@link #of(Node, Node)}; the
         * (code, text) pair comes from the relay's {@code <error/>}
         * envelope.
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
         * @apiNote
         * Use this getter to log the relay-side error code; a
         * 5xx-class value is the canonical retry trigger.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the human-readable error text, when supplied.
         *
         * @apiNote
         * Use this getter for logging only; the text is
         * server-localised and not stable across snapshots.
         *
         * @return an {@link Optional} carrying the error text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant.
         *
         * @apiNote
         * Call this from {@link #of(Node, Node)}; the method delegates
         * to {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}
         * to extract the (code, text) envelope.
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

        /**
         * {@inheritDoc}
         */
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

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "IqQueryProductListCatalogResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
