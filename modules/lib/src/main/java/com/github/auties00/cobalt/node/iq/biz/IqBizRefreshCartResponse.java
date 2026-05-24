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
 * The typed sealed family of inbound reply variants produced by the relay in response to an {@link IqBizRefreshCartRequest}.
 *
 * @apiNote
 * Use this type to switch over the three documented outcomes of a cart refresh: {@link Success} carries the per-line price, the cart-wide totals and the cap available, {@link ClientError} surfaces a relay validation rejection, and {@link ServerError} reports a transport or backend failure. The dispatcher invokes {@link #of(Node, Node)} to project the raw {@link Node} into the right variant before handing it to the caller.
 */
@WhatsAppWebModule(moduleName = "WAWebBizRefreshCartJob")
public sealed interface IqBizRefreshCartResponse extends IqOperation.Response
        permits IqBizRefreshCartResponse.Success, IqBizRefreshCartResponse.ClientError, IqBizRefreshCartResponse.ServerError {

    /**
     * Tries each {@link IqBizRefreshCartResponse} variant in priority order.
     *
     * @apiNote
     * Call this entry from the dispatcher to fan the inbound stanza into the matching sealed variant; the success path is tried first, then the client-error envelope, then the server-error envelope. Returns empty only when none of the three documented shapes apply.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant
     * @throws NullPointerException if either argument is {@code null}
     */
    static Optional<? extends IqBizRefreshCartResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant carrying the typed cart envelope.
     *
     * @apiNote
     * Use this variant to read the cart-wide totals block and the per-line {@link Product} entries; consumers render the cart-revalidate surface from these fields before allowing the buyer to advance to checkout.
     */
    final class Success implements IqBizRefreshCartResponse {
        /**
         * The cart totals block carrying the pre-tax subtotal, the grand total, the currency code and the status marker.
         *
         * @apiNote
         * Use this class to model the {@code <price/>} child of the {@code <cart/>} envelope; consumers render the totals row of the cart-revalidate surface from these fields.
         */
        public static final class Price {
            /**
             * The pre-tax subtotal string lifted from {@code <price><subtotal/></price>}, when supplied.
             */
            private final String subtotal;

            /**
             * The grand total string lifted from {@code <price><total/></price>}, when supplied.
             */
            private final String total;

            /**
             * The currency code (ISO 4217) lifted from {@code <price><currency/></price>}, when supplied.
             */
            private final String currency;

            /**
             * The price-status marker lifted from {@code <price><price_status/></price>}, when supplied.
             */
            private final String priceStatus;

            /**
             * Constructs a typed price block.
             *
             * @apiNote
             * Call this constructor when projecting the {@code <price/>} child of a {@code <cart/>} envelope; pass {@code null} for any field that the wire shape omitted.
             *
             * @param subtotal    the pre-tax subtotal string; may be {@code null}
             * @param total       the grand total string; may be {@code null}
             * @param currency    the currency code; may be {@code null}
             * @param priceStatus the status marker; may be {@code null}
             */
            public Price(String subtotal, String total, String currency, String priceStatus) {
                this.subtotal = subtotal;
                this.total = total;
                this.currency = currency;
                this.priceStatus = priceStatus;
            }

            /**
             * Returns the pre-tax subtotal string.
             *
             * @apiNote
             * Use this getter to render the subtotal row of the cart totals block.
             *
             * @return an {@link Optional} carrying the subtotal
             */
            public Optional<String> subtotal() {
                return Optional.ofNullable(subtotal);
            }

            /**
             * Returns the grand total string.
             *
             * @apiNote
             * Use this getter to render the headline total of the cart-revalidate surface.
             *
             * @return an {@link Optional} carrying the total
             */
            public Optional<String> total() {
                return Optional.ofNullable(total);
            }

            /**
             * Returns the currency code (ISO 4217).
             *
             * @apiNote
             * Use this getter to read back the currency that frames the subtotal and total strings when rendering the totals row.
             *
             * @return an {@link Optional} carrying the code
             */
            public Optional<String> currency() {
                return Optional.ofNullable(currency);
            }

            /**
             * Returns the price-status marker.
             *
             * @apiNote
             * Use this getter to read back the per-cart price status that the relay returns alongside the totals (typically {@code "OK"} when the cart resolves cleanly).
             *
             * @return an {@link Optional} carrying the marker
             */
            public Optional<String> priceStatus() {
                return Optional.ofNullable(priceStatus);
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (Price) obj;
                return Objects.equals(this.subtotal, that.subtotal)
                        && Objects.equals(this.total, that.total)
                        && Objects.equals(this.currency, that.currency)
                        && Objects.equals(this.priceStatus, that.priceStatus);
            }

            @Override
            public int hashCode() {
                return Objects.hash(subtotal, total, currency, priceStatus);
            }

            @Override
            public String toString() {
                return "IqBizRefreshCartResponse.Success.Price[subtotal=" + subtotal
                        + ", total=" + total + ", currency=" + currency
                        + ", priceStatus=" + priceStatus + ']';
            }
        }

        /**
         * One typed cart line carrying the product identity, the canonical price, the cart cap, the optional thumbnail, the optional sale-price block and the per-line status marker.
         *
         * @apiNote
         * Use this class to model one row in the cart-revalidate surface; the status marker lets the UI flag entries that have been removed or are no longer purchasable.
         */
        public static final class Product {
            /**
             * The product identifier within the merchant catalog.
             */
            private final String id;

            /**
             * The product display name, when supplied.
             */
            private final String name;

            /**
             * The unit price string, when supplied.
             */
            private final String price;

            /**
             * The line currency code (ISO 4217), when supplied.
             */
            private final String currency;

            /**
             * The maximum cart quantity that the relay allows for this line.
             */
            private final int maxAvailable;

            /**
             * The catalog thumbnail identifier, when supplied.
             */
            private final String thumbnailId;

            /**
             * The catalog thumbnail URL, when supplied.
             */
            private final String thumbnailUrl;

            /**
             * The optional sale-price block carrying a discounted price and an optional sale window.
             */
            private final SalePrice salePrice;

            /**
             * The per-line status marker (for example {@code "INVALID_PRODUCT"}, {@code "MISSING"}), when supplied.
             */
            private final String status;

            /**
             * Constructs a typed cart line.
             *
             * @apiNote
             * Call this constructor when projecting a {@code <product/>} child of the {@code <cart/>} envelope; pass {@code null} for any optional field that the wire shape omitted. The {@code maxAvailable} cap defaults to {@code 99} when the relay does not stamp a per-line limit.
             *
             * @param id           the product identifier; never {@code null}
             * @param name         the display name; may be {@code null}
             * @param price        the unit price string; may be {@code null}
             * @param currency     the currency code; may be {@code null}
             * @param maxAvailable the cart cap
             * @param thumbnailId  the thumbnail identifier; may be {@code null}
             * @param thumbnailUrl the thumbnail URL; may be {@code null}
             * @param salePrice    the sale-price block; may be {@code null}
             * @param status       the status marker; may be {@code null}
             * @throws NullPointerException if {@code id} is {@code null}
             */
            public Product(String id,
                           String name,
                           String price,
                           String currency,
                           int maxAvailable,
                           String thumbnailId,
                           String thumbnailUrl,
                           SalePrice salePrice,
                           String status) {
                this.id = Objects.requireNonNull(id, "id cannot be null");
                this.name = name;
                this.price = price;
                this.currency = currency;
                this.maxAvailable = maxAvailable;
                this.thumbnailId = thumbnailId;
                this.thumbnailUrl = thumbnailUrl;
                this.salePrice = salePrice;
                this.status = status;
            }

            /**
             * Returns the product identifier.
             *
             * @apiNote
             * Use this getter to read back the merchant-catalog id that identifies the product the cart line refers to.
             *
             * @return the identifier; never {@code null}
             */
            public String id() {
                return id;
            }

            /**
             * Returns the product display name.
             *
             * @apiNote
             * Use this getter to render the cart-line display name in the cart-revalidate surface; the value is absent when the relay omitted the {@code <name/>} child.
             *
             * @return an {@link Optional} carrying the name
             */
            public Optional<String> name() {
                return Optional.ofNullable(name);
            }

            /**
             * Returns the unit price string.
             *
             * @apiNote
             * Use this getter to render the per-line price column; the value is absent when the relay did not stamp a per-line price.
             *
             * @return an {@link Optional} carrying the price
             */
            public Optional<String> price() {
                return Optional.ofNullable(price);
            }

            /**
             * Returns the line currency code (ISO 4217).
             *
             * @apiNote
             * Use this getter to read back the per-line currency when it diverges from the cart-wide currency; absent when the relay omitted the {@code <currency/>} child.
             *
             * @return an {@link Optional} carrying the code
             */
            public Optional<String> currency() {
                return Optional.ofNullable(currency);
            }

            /**
             * Returns the maximum cart quantity that the relay allows for this line.
             *
             * @apiNote
             * Use this getter to clamp the buyer's quantity selector to the merchant-supplied cap before re-submitting the cart.
             *
             * @return the cap
             */
            public int maxAvailable() {
                return maxAvailable;
            }

            /**
             * Returns the catalog thumbnail identifier.
             *
             * @apiNote
             * Use this getter to fetch the thumbnail asset id when downloading the image bytes separately; the value is absent when the cart line references a product without a thumbnail.
             *
             * @return an {@link Optional} carrying the identifier
             */
            public Optional<String> thumbnailId() {
                return Optional.ofNullable(thumbnailId);
            }

            /**
             * Returns the catalog thumbnail URL.
             *
             * @apiNote
             * Use this getter to load the thumbnail image directly via HTTP when an URL is provided by the relay.
             *
             * @return an {@link Optional} carrying the URL
             */
            public Optional<String> thumbnailUrl() {
                return Optional.ofNullable(thumbnailUrl);
            }

            /**
             * Returns the sale-price block.
             *
             * @apiNote
             * Use this getter to surface the discounted price and optional sale window when the merchant has a promo running on this line.
             *
             * @return an {@link Optional} carrying the block
             */
            public Optional<SalePrice> salePrice() {
                return Optional.ofNullable(salePrice);
            }

            /**
             * Returns the per-line status marker.
             *
             * @apiNote
             * Use this getter to detect cart lines that the relay has flagged as no longer purchasable (for example {@code "INVALID_PRODUCT"} or {@code "MISSING"}).
             *
             * @return an {@link Optional} carrying the marker
             */
            public Optional<String> status() {
                return Optional.ofNullable(status);
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
                return this.maxAvailable == that.maxAvailable
                        && Objects.equals(this.id, that.id)
                        && Objects.equals(this.name, that.name)
                        && Objects.equals(this.price, that.price)
                        && Objects.equals(this.currency, that.currency)
                        && Objects.equals(this.thumbnailId, that.thumbnailId)
                        && Objects.equals(this.thumbnailUrl, that.thumbnailUrl)
                        && Objects.equals(this.salePrice, that.salePrice)
                        && Objects.equals(this.status, that.status);
            }

            @Override
            public int hashCode() {
                return Objects.hash(id, name, price, currency, maxAvailable,
                        thumbnailId, thumbnailUrl, salePrice, status);
            }

            @Override
            public String toString() {
                return "IqBizRefreshCartResponse.Success.Product[id=" + id
                        + ", name=" + name + ", price=" + price
                        + ", currency=" + currency + ", maxAvailable=" + maxAvailable + ']';
            }
        }

        /**
         * The sale-price sub-block carrying a discounted price and an optional sale window.
         *
         * @apiNote
         * Use this class to model the {@code <sale_price/>} child of a cart {@code <product/>}; consumers render the strike-through price and a sale window when both endpoints are stamped.
         */
        public static final class SalePrice {
            /**
             * The discounted price string lifted from {@code <sale_price><price/></sale_price>}.
             */
            private final String price;

            /**
             * The optional sale start date string lifted from {@code <sale_price><start_date/></sale_price>}.
             */
            private final String startDate;

            /**
             * The optional sale end date string lifted from {@code <sale_price><end_date/></sale_price>}.
             */
            private final String endDate;

            /**
             * Constructs a typed sale-price block.
             *
             * @apiNote
             * Call this constructor when projecting a {@code <sale_price/>} child; pass {@code null} for either endpoint when the wire shape omitted the sale window.
             *
             * @param price     the discounted price; never {@code null}
             * @param startDate the sale start date; may be {@code null}
             * @param endDate   the sale end date; may be {@code null}
             * @throws NullPointerException if {@code price} is {@code null}
             */
            public SalePrice(String price, String startDate, String endDate) {
                this.price = Objects.requireNonNull(price, "price cannot be null");
                this.startDate = startDate;
                this.endDate = endDate;
            }

            /**
             * Returns the discounted price string.
             *
             * @apiNote
             * Use this getter to render the sale price alongside the strike-through {@link Product#price()}.
             *
             * @return the price; never {@code null}
             */
            public String price() {
                return price;
            }

            /**
             * Returns the sale start date string.
             *
             * @apiNote
             * Use this getter to surface the sale window's start endpoint when the merchant stamped one on the line.
             *
             * @return an {@link Optional} carrying the date
             */
            public Optional<String> startDate() {
                return Optional.ofNullable(startDate);
            }

            /**
             * Returns the sale end date string.
             *
             * @apiNote
             * Use this getter to surface the sale window's end endpoint when the merchant stamped one on the line.
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
                return "IqBizRefreshCartResponse.Success.SalePrice[price=" + price
                        + ", startDate=" + startDate + ", endDate=" + endDate + ']';
            }
        }

        /**
         * The cart totals block carrying subtotal, total, currency and status marker.
         */
        private final Price price;

        /**
         * The cart lines in wire order.
         */
        private final List<Product> products;

        /**
         * Constructs a typed success reply.
         *
         * @apiNote
         * Call this constructor when projecting a {@code <cart/>} child into the typed model.
         *
         * @param price    the totals block; never {@code null}
         * @param products the cart lines; never {@code null}
         * @throws NullPointerException if either argument is {@code null}
         */
        public Success(Price price, List<Product> products) {
            this.price = Objects.requireNonNull(price, "price cannot be null");
            Objects.requireNonNull(products, "products cannot be null");
            this.products = List.copyOf(products);
        }

        /**
         * Returns the cart totals block.
         *
         * @apiNote
         * Use this getter to render the cart totals row containing subtotal, total, currency and status marker.
         *
         * @return the block; never {@code null}
         */
        public Price price() {
            return price;
        }

        /**
         * Returns the typed cart lines.
         *
         * @apiNote
         * Use this getter to iterate the per-line entries when rendering the cart-revalidate table; the list is empty when the relay returned a cart envelope without any product rows.
         *
         * @return an unmodifiable list; never {@code null}
         */
        public List<Product> products() {
            return products;
        }

        /**
         * Tries to parse a {@link Success} variant from the inbound stanza.
         *
         * @apiNote
         * Call this entry from {@link IqBizRefreshCartResponse#of(Node, Node)} or directly when only the success branch is interesting; returns empty when the stanza does not carry a {@code result} envelope matching the original request.
         *
         * @implNote
         * This implementation mirrors the cart-shape projection done by {@code WAWebBizGraphQLRefreshCartJob.RefreshCart}: when the {@code <cart/>} child is absent the result is an empty success envelope, the totals are read from the nested {@code <price/>} block, and each {@code <product/>} child contributes a {@link Product} carrying its optional {@code <media><image>} sub-block (mapping {@code request_image_url} to {@link Product#thumbnailUrl()}) and {@code <sale_price/>} sub-block. The {@code max_available} cap defaults to {@code 99} when the wire shape omits it.
         *
         * @param node    the inbound IQ stanza; never {@code null}
         * @param request the original outbound request; never {@code null}
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebBizRefreshCartJob",
                exports = "refreshCartResponse", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var cartNode = node.getChild("cart").orElse(null);
            if (cartNode == null) {
                return Optional.of(new Success(new Price(null, null, null, null),
                        Collections.emptyList()));
            }
            String subtotal = null;
            String total = null;
            String currency = null;
            String priceStatus = null;
            var priceNode = cartNode.getChild("price").orElse(null);
            if (priceNode != null) {
                subtotal = priceNode.getChild("subtotal")
                        .flatMap(Node::toContentString).orElse(null);
                total = priceNode.getChild("total")
                        .flatMap(Node::toContentString).orElse(null);
                currency = priceNode.getChild("currency")
                        .flatMap(Node::toContentString).orElse(null);
                priceStatus = priceNode.getChild("price_status")
                        .flatMap(Node::toContentString).orElse(null);
            }
            var price = new Price(subtotal, total, currency, priceStatus);
            var products = new ArrayList<Product>();
            for (var productNode : cartNode.getChildren("product")) {
                var idNode = productNode.getChild("id").orElse(null);
                if (idNode == null) {
                    continue;
                }
                var id = idNode.toContentString().orElse(null);
                if (id == null) {
                    continue;
                }
                var name = productNode.getChild("name")
                        .flatMap(Node::toContentString).orElse(null);
                var pprice = productNode.getChild("price")
                        .flatMap(Node::toContentString).orElse(null);
                var pcurrency = productNode.getChild("currency")
                        .flatMap(Node::toContentString).orElse(null);
                var maxAvailable = 99;
                var maxAvailableContent = productNode.getChild("max_available")
                        .flatMap(Node::toContentString).orElse(null);
                if (maxAvailableContent != null) {
                    try {
                        maxAvailable = Integer.parseInt(maxAvailableContent);
                    } catch (NumberFormatException _) {
                    }
                }
                String thumbId = null;
                String thumbUrl = null;
                productNode.getChild("media").ifPresent(media ->
                        media.getChild("image").orElse(null));
                var imageNode = productNode.getChild("media")
                        .flatMap(media -> media.getChild("image")).orElse(null);
                if (imageNode != null) {
                    thumbId = imageNode.getChild("id")
                            .flatMap(Node::toContentString).orElse(null);
                    thumbUrl = imageNode.getChild("request_image_url")
                            .flatMap(Node::toContentString).orElse(null);
                }
                SalePrice salePrice = null;
                var salePriceNode = productNode.getChild("sale_price").orElse(null);
                if (salePriceNode != null) {
                    var sp = salePriceNode.getChild("price")
                            .flatMap(Node::toContentString).orElse("");
                    var startDate = salePriceNode.getChild("start_date")
                            .flatMap(Node::toContentString).orElse(null);
                    var endDate = salePriceNode.getChild("end_date")
                            .flatMap(Node::toContentString).orElse(null);
                    salePrice = new SalePrice(sp, startDate, endDate);
                }
                var status = productNode.getChild("status")
                        .flatMap(Node::toContentString).orElse(null);
                products.add(new Product(id, name, pprice, pcurrency, maxAvailable,
                        thumbId, thumbUrl, salePrice, status));
            }
            return Optional.of(new Success(price, products));
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
            return Objects.equals(this.price, that.price)
                    && Objects.equals(this.products, that.products);
        }

        @Override
        public int hashCode() {
            return Objects.hash(price, products);
        }

        @Override
        public String toString() {
            return "IqBizRefreshCartResponse.Success[price=" + price
                    + ", products=" + products + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant surfacing a client-side rejection.
     *
     * @apiNote
     * Use this variant to react to a refused cart refresh; typical examples include a relay validation failure surfaced as a SMAX error envelope.
     */
    final class ClientError implements IqBizRefreshCartResponse {
        /**
         * The numeric error code lifted from the SMAX error envelope.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text lifted from the SMAX error envelope.
         */
        private final String errorText;

        /**
         * Constructs a typed client-error reply.
         *
         * @apiNote
         * Call this constructor when projecting a client-error envelope into the typed model; pass {@code null} for {@code errorText} when the wire shape omitted the text field.
         *
         * @param errorCode the numeric error code
         * @param errorText the human-readable error text; may be {@code null}
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * Use this getter to read back the SMAX error code that the relay used to classify the failure.
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
         * Use this getter to surface the relay-supplied error explanation in the UI when present.
         *
         * @return an {@link Optional} carrying the error text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the inbound stanza.
         *
         * @apiNote
         * Call this entry from {@link IqBizRefreshCartResponse#of(Node, Node)} or directly when only the client-error branch is interesting; returns empty when the stanza does not carry a client-error envelope matching the original request.
         *
         * @param node    the inbound IQ stanza; never {@code null}
         * @param request the original outbound request; never {@code null}
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the client-error schema
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
            return "IqBizRefreshCartResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant surfacing a server-side failure.
     *
     * @apiNote
     * Use this variant to react to a backend failure that did not produce a typed cart; typical examples include the relay returning a 500-class status when the GraphQL path returned an unparseable shape.
     */
    final class ServerError implements IqBizRefreshCartResponse {
        /**
         * The numeric error code lifted from the SMAX error envelope.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text lifted from the SMAX error envelope.
         */
        private final String errorText;

        /**
         * Constructs a typed server-error reply.
         *
         * @apiNote
         * Call this constructor when projecting a server-error envelope into the typed model; pass {@code null} for {@code errorText} when the wire shape omitted the text field.
         *
         * @param errorCode the numeric error code
         * @param errorText the human-readable error text; may be {@code null}
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * Use this getter to read back the SMAX error code that the relay used to classify the failure.
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
         * Use this getter to surface the relay-supplied error explanation in the UI when present.
         *
         * @return an {@link Optional} carrying the error text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from the inbound stanza.
         *
         * @apiNote
         * Call this entry from {@link IqBizRefreshCartResponse#of(Node, Node)} or directly when only the server-error branch is interesting; returns empty when the stanza does not carry a server-error envelope matching the original request.
         *
         * @param node    the inbound IQ stanza; never {@code null}
         * @param request the original outbound request; never {@code null}
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the server-error schema
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
            return "IqBizRefreshCartResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
