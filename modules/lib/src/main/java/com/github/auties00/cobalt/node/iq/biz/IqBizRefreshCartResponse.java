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
 * response to an {@link IqBizRefreshCartRequest}.
 */
@WhatsAppWebModule(moduleName = "WAWebBizRefreshCartJob")
public sealed interface IqBizRefreshCartResponse extends IqOperation.Response
        permits IqBizRefreshCartResponse.Success, IqBizRefreshCartResponse.ClientError, IqBizRefreshCartResponse.ServerError {

    /**
     * Tries each {@link IqBizRefreshCartResponse} variant in priority order.
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
     * The {@code Success} reply variant — projects the typed cart
     * detail.
     */
    final class Success implements IqBizRefreshCartResponse {
        /**
         * The cart's totals block.
         */
        public static final class Price {
            /**
             * The pre-tax subtotal, when supplied.
             */
            private final String subtotal;

            /**
             * The grand total, when supplied.
             */
            private final String total;

            /**
             * The currency code, when supplied.
             */
            private final String currency;

            /**
             * The price-status marker (e.g. {@code "OK"}), when
             * supplied.
             */
            private final String priceStatus;

            /**
             * Constructs a price block.
             *
             * @param subtotal    the subtotal; may be {@code null}
             * @param total       the total; may be {@code null}
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
             * Returns the subtotal.
             *
             * @return an {@link Optional} carrying the subtotal
             */
            public Optional<String> subtotal() {
                return Optional.ofNullable(subtotal);
            }

            /**
             * Returns the total.
             *
             * @return an {@link Optional} carrying the total
             */
            public Optional<String> total() {
                return Optional.ofNullable(total);
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
             * Returns the price-status marker.
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
         * One typed cart line.
         */
        public static final class Product {
            /**
             * The product id.
             */
            private final String id;

            /**
             * The display name, when supplied.
             */
            private final String name;

            /**
             * The unit price (string-encoded), when supplied.
             */
            private final String price;

            /**
             * The currency code, when supplied.
             */
            private final String currency;

            /**
             * The maximum cart quantity allowed for this line.
             */
            private final int maxAvailable;

            /**
             * The optional thumbnail image id.
             */
            private final String thumbnailId;

            /**
             * The optional thumbnail URL.
             */
            private final String thumbnailUrl;

            /**
             * The optional sale-price block.
             */
            private final SalePrice salePrice;

            /**
             * The optional status marker (e.g.
             * {@code "INVALID_PRODUCT"}, {@code "MISSING"}).
             */
            private final String status;

            /**
             * Constructs a line.
             *
             * @param id           the id; never {@code null}
             * @param name         the name; may be {@code null}
             * @param price        the price; may be {@code null}
             * @param currency     the currency code; may be
             *                     {@code null}
             * @param maxAvailable the cart cap
             * @param thumbnailId  the thumbnail id; may be {@code null}
             * @param thumbnailUrl the thumbnail URL; may be {@code null}
             * @param salePrice    the sale price; may be {@code null}
             * @param status       the status marker; may be {@code null}
             * @throws NullPointerException if {@code id} is
             *                              {@code null}
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
             * Returns the id.
             *
             * @return the id; never {@code null}
             */
            public String id() {
                return id;
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
             * Returns the unit price.
             *
             * @return an {@link Optional} carrying the price
             */
            public Optional<String> price() {
                return Optional.ofNullable(price);
            }

            /**
             * Returns the currency.
             *
             * @return an {@link Optional} carrying the code
             */
            public Optional<String> currency() {
                return Optional.ofNullable(currency);
            }

            /**
             * Returns the cart cap.
             *
             * @return the cap
             */
            public int maxAvailable() {
                return maxAvailable;
            }

            /**
             * Returns the thumbnail id.
             *
             * @return an {@link Optional} carrying the id
             */
            public Optional<String> thumbnailId() {
                return Optional.ofNullable(thumbnailId);
            }

            /**
             * Returns the thumbnail URL.
             *
             * @return an {@link Optional} carrying the URL
             */
            public Optional<String> thumbnailUrl() {
                return Optional.ofNullable(thumbnailUrl);
            }

            /**
             * Returns the sale price.
             *
             * @return an {@link Optional} carrying the block
             */
            public Optional<SalePrice> salePrice() {
                return Optional.ofNullable(salePrice);
            }

            /**
             * Returns the status marker.
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
         * The sale-price sub-block.
         */
        public static final class SalePrice {
            /**
             * The discounted price string.
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
             * @param startDate the start; may be {@code null}
             * @param endDate   the end; may be {@code null}
             * @throws NullPointerException if {@code price} is
             *                              {@code null}
             */
            public SalePrice(String price, String startDate, String endDate) {
                this.price = Objects.requireNonNull(price, "price cannot be null");
                this.startDate = startDate;
                this.endDate = endDate;
            }

            /**
             * Returns the price.
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
                return "IqBizRefreshCartResponse.Success.SalePrice[price=" + price
                        + ", startDate=" + startDate + ", endDate=" + endDate + ']';
            }
        }

        /**
         * The cart totals block.
         */
        private final Price price;

        /**
         * The cart lines, in wire order.
         */
        private final List<Product> products;

        /**
         * Constructs a successful reply.
         *
         * @param price    the totals block; never {@code null}
         * @param products the cart lines; never {@code null}
         * @throws NullPointerException if either argument is
         *                              {@code null}
         */
        public Success(Price price, List<Product> products) {
            this.price = Objects.requireNonNull(price, "price cannot be null");
            Objects.requireNonNull(products, "products cannot be null");
            this.products = List.copyOf(products);
        }

        /**
         * Returns the totals block.
         *
         * @return the block; never {@code null}
         */
        public Price price() {
            return price;
        }

        /**
         * Returns the cart lines.
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
     * The {@code ClientError} reply variant.
     */
    final class ClientError implements IqBizRefreshCartResponse {
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
            return "IqBizRefreshCartResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant.
     */
    final class ServerError implements IqBizRefreshCartResponse {
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
            return "IqBizRefreshCartResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
