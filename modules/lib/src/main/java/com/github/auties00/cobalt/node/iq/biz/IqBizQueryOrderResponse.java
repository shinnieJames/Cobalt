package com.github.auties00.cobalt.node.iq.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The typed sealed family of inbound reply variants produced by the relay in response to an {@link IqBizQueryOrderRequest}.
 *
 * @apiNote
 * Use this type to switch over the three documented outcomes of a business-order fetch: {@link Success} carries the decoded order envelope, {@link ClientError} surfaces a code-451 sanctions block or a relay validation rejection, and {@link ServerError} reports a transport or backend failure. The dispatcher invokes {@link #of(Node, Node)} to project the raw {@link Node} into the right variant before handing it to the caller.
 */
@WhatsAppWebModule(moduleName = "WAWebBizQueryOrderJob")
public sealed interface IqBizQueryOrderResponse extends IqOperation.Response
        permits IqBizQueryOrderResponse.Success, IqBizQueryOrderResponse.ClientError, IqBizQueryOrderResponse.ServerError {

    /**
     * Tries each {@link IqBizQueryOrderResponse} variant in priority order.
     *
     * @apiNote
     * Call this entry from the dispatcher to fan the inbound stanza into the matching sealed variant; the success path is tried first, then the client-error envelope, then the server-error envelope. Returns empty only when none of the three documented shapes apply.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant
     * @throws NullPointerException if either argument is {@code null}
     */
    static Optional<? extends IqBizQueryOrderResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant carrying the typed order envelope.
     *
     * @apiNote
     * Use this variant to read the per-order totals (subtotal, tax, total, currency) and the list of typed {@link LineItem} entries; consumers render the order-details surface from these fields.
     */
    final class Success implements IqBizQueryOrderResponse {
        /**
         * One typed order line item carrying the product identity, unit price, currency, quantity, optional thumbnail and variant properties.
         *
         * @apiNote
         * Use this class to model one row in the order-details table; the optional fields mirror the wire shape where {@code price}, {@code quantity}, {@code currency}, {@code thumbnailId} and {@code thumbnailUrl} can each be absent depending on the merchant configuration.
         */
        public static final class LineItem {
            /**
             * The product identifier within the merchant catalog.
             */
            private final String id;

            /**
             * The product display name shown in the order-details row.
             */
            private final String name;

            /**
             * The unit price encoded as a major-units integer, when supplied.
             */
            private final Integer price;

            /**
             * The catalog thumbnail identifier, when supplied.
             */
            private final String thumbnailId;

            /**
             * The catalog thumbnail URL, when supplied.
             */
            private final String thumbnailUrl;

            /**
             * The currency code (ISO 4217), when supplied.
             */
            private final String currency;

            /**
             * The line quantity, when supplied.
             */
            private final Integer quantity;

            /**
             * The list of variant properties as {@code (name, value)} pairs, in wire order.
             */
            private final List<Map.Entry<String, String>> properties;

            /**
             * Constructs a typed line item.
             *
             * @apiNote
             * Call this constructor when projecting a {@code <product/>} child into the typed model; pass {@code null} for any of the optional fields that the wire shape omitted, but never for {@code id}, {@code name} or {@code properties}.
             *
             * @param id           the product identifier; never {@code null}
             * @param name         the display name; never {@code null}
             * @param price        the unit price; may be {@code null}
             * @param thumbnailId  the thumbnail identifier; may be {@code null}
             * @param thumbnailUrl the thumbnail URL; may be {@code null}
             * @param currency     the currency code; may be {@code null}
             * @param quantity     the line quantity; may be {@code null}
             * @param properties   the variant properties; never {@code null}
             * @throws NullPointerException if {@code id}, {@code name} or {@code properties} is {@code null}
             */
            public LineItem(String id,
                            String name,
                            Integer price,
                            String thumbnailId,
                            String thumbnailUrl,
                            String currency,
                            Integer quantity,
                            List<Map.Entry<String, String>> properties) {
                this.id = Objects.requireNonNull(id, "id cannot be null");
                this.name = Objects.requireNonNull(name, "name cannot be null");
                this.price = price;
                this.thumbnailId = thumbnailId;
                this.thumbnailUrl = thumbnailUrl;
                this.currency = currency;
                this.quantity = quantity;
                Objects.requireNonNull(properties, "properties cannot be null");
                this.properties = List.copyOf(properties);
            }

            /**
             * Returns the product identifier.
             *
             * @apiNote
             * Use this getter to read back the merchant-catalog id that identifies the product the line refers to.
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
             * Use this getter to read back the localised name to render in the order-details row.
             *
             * @return the display name; never {@code null}
             */
            public String name() {
                return name;
            }

            /**
             * Returns the unit price encoded as a major-units integer.
             *
             * @apiNote
             * Use this getter to read back the merchant-quoted unit price; the value is absent when the merchant did not stamp a per-line price on the order.
             *
             * @return an {@link Optional} carrying the price
             */
            public Optional<Integer> price() {
                return Optional.ofNullable(price);
            }

            /**
             * Returns the catalog thumbnail identifier.
             *
             * @apiNote
             * Use this getter to fetch the thumbnail-asset id when downloading the image bytes separately; the value is absent when the order references a product without a thumbnail.
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
             * Returns the line currency code (ISO 4217).
             *
             * @apiNote
             * Use this getter to read back the per-line currency; this may diverge from the order-wide currency when the merchant stamps line-level currencies, otherwise the value is absent.
             *
             * @return an {@link Optional} carrying the code
             */
            public Optional<String> currency() {
                return Optional.ofNullable(currency);
            }

            /**
             * Returns the line quantity.
             *
             * @apiNote
             * Use this getter to read back the count of units that the buyer purchased for this line; the value is absent when the wire shape omitted the quantity.
             *
             * @return an {@link Optional} carrying the quantity
             */
            public Optional<Integer> quantity() {
                return Optional.ofNullable(quantity);
            }

            /**
             * Returns the variant properties.
             *
             * @apiNote
             * Use this getter to render the per-line variant labels (for example {@code size=large}, {@code colour=red}) decoded from the {@code <variant_info><properties>} block.
             *
             * @return an unmodifiable list; never {@code null}
             */
            public List<Map.Entry<String, String>> properties() {
                return properties;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (LineItem) obj;
                return Objects.equals(this.id, that.id)
                        && Objects.equals(this.name, that.name)
                        && Objects.equals(this.price, that.price)
                        && Objects.equals(this.thumbnailId, that.thumbnailId)
                        && Objects.equals(this.thumbnailUrl, that.thumbnailUrl)
                        && Objects.equals(this.currency, that.currency)
                        && Objects.equals(this.quantity, that.quantity)
                        && Objects.equals(this.properties, that.properties);
            }

            @Override
            public int hashCode() {
                return Objects.hash(id, name, price, thumbnailId, thumbnailUrl,
                        currency, quantity, properties);
            }

            @Override
            public String toString() {
                return "IqBizQueryOrderResponse.Success.LineItem[id=" + id
                        + ", name=" + name + ", price=" + price
                        + ", quantity=" + quantity + ", currency=" + currency + ']';
            }
        }

        /**
         * The order creation time decoded from the {@code creation_ts} attribute, when supplied.
         */
        private final Instant createdAt;

        /**
         * The order-wide currency code (ISO 4217), when supplied.
         */
        private final String currency;

        /**
         * The pre-tax subtotal in major-units, when supplied.
         */
        private final Integer subtotal;

        /**
         * The tax amount in major-units, when supplied.
         */
        private final Integer tax;

        /**
         * The grand total in major-units, when supplied.
         */
        private final Integer total;

        /**
         * The list of typed line items in wire order.
         */
        private final List<LineItem> products;

        /**
         * Constructs a typed success reply.
         *
         * @apiNote
         * Call this constructor when projecting a {@code <order/>} child into the typed model; pass {@code null} for any of {@code createdAt}, {@code currency}, {@code subtotal}, {@code tax} or {@code total} that the wire shape omitted, but never for {@code products}.
         *
         * @param createdAt the creation timestamp; may be {@code null}
         * @param currency  the order-wide currency code; may be {@code null}
         * @param subtotal  the pre-tax subtotal; may be {@code null}
         * @param tax       the tax amount; may be {@code null}
         * @param total     the grand total; may be {@code null}
         * @param products  the line items; never {@code null}
         * @throws NullPointerException if {@code products} is {@code null}
         */
        public Success(Instant createdAt,
                       String currency,
                       Integer subtotal,
                       Integer tax,
                       Integer total,
                       List<LineItem> products) {
            this.createdAt = createdAt;
            this.currency = currency;
            this.subtotal = subtotal;
            this.tax = tax;
            this.total = total;
            Objects.requireNonNull(products, "products cannot be null");
            this.products = List.copyOf(products);
        }

        /**
         * Returns the order creation time.
         *
         * @apiNote
         * Use this getter to read back the merchant-stamped creation timestamp rendered as the order-receipt date; the value is absent when the wire shape omitted the {@code creation_ts} attribute.
         *
         * @return an {@link Optional} carrying the time
         */
        public Optional<Instant> createdAt() {
            return Optional.ofNullable(createdAt);
        }

        /**
         * Returns the order-wide currency code (ISO 4217).
         *
         * @apiNote
         * Use this getter to read back the currency that frames the subtotal, tax and total amounts when rendering the totals row of the order-details surface.
         *
         * @return an {@link Optional} carrying the code
         */
        public Optional<String> currency() {
            return Optional.ofNullable(currency);
        }

        /**
         * Returns the pre-tax subtotal in major-units.
         *
         * @apiNote
         * Use this getter to render the subtotal row of the order-details totals block.
         *
         * @return an {@link Optional} carrying the subtotal
         */
        public Optional<Integer> subtotal() {
            return Optional.ofNullable(subtotal);
        }

        /**
         * Returns the tax amount in major-units.
         *
         * @apiNote
         * Use this getter to render the tax row of the order-details totals block.
         *
         * @return an {@link Optional} carrying the tax
         */
        public Optional<Integer> tax() {
            return Optional.ofNullable(tax);
        }

        /**
         * Returns the grand total in major-units.
         *
         * @apiNote
         * Use this getter to render the headline total of the order-details surface.
         *
         * @return an {@link Optional} carrying the total
         */
        public Optional<Integer> total() {
            return Optional.ofNullable(total);
        }

        /**
         * Returns the typed line items in wire order.
         *
         * @apiNote
         * Use this getter to iterate the per-line entries when rendering the order-details table; the list is empty when the relay returned an order envelope without any product rows.
         *
         * @return an unmodifiable list; never {@code null}
         */
        public List<LineItem> products() {
            return products;
        }

        /**
         * Tries to parse a {@link Success} variant from the inbound stanza.
         *
         * @apiNote
         * Call this entry from {@link IqBizQueryOrderResponse#of(Node, Node)} or directly when only the success branch is interesting; returns empty when the stanza does not carry a {@code result} envelope matching the original request.
         *
         * @implNote
         * This implementation mirrors the deprecated WAP parser inside {@code WAWebBizQueryOrderJob.queryOrderResponse}: when the {@code <order/>} child is absent the result is an empty success envelope, the price totals are read from the nested {@code <price>} block as major-units integers, and each {@code <product>} child contributes a {@link LineItem} carrying its optional {@code <image><id></id><url></url></image>} and {@code <variant_info><properties>} sub-blocks.
         *
         * @param node    the inbound IQ stanza; never {@code null}
         * @param request the original outbound request; never {@code null}
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebBizQueryOrderJob",
                exports = "queryOrderResponse", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var orderNode = node.getChild("order").orElse(null);
            if (orderNode == null) {
                return Optional.of(new Success(null, null, null, null, null,
                        Collections.emptyList()));
            }
            var createdAtSeconds = orderNode.getAttributeAsLong("creation_ts").orElse(-1L);
            var createdAt = createdAtSeconds >= 0 ? Instant.ofEpochSecond(createdAtSeconds) : null;
            Integer subtotal = null;
            Integer tax = null;
            Integer total = null;
            String currency = null;
            var priceNode = orderNode.getChild("price").orElse(null);
            if (priceNode != null) {
                subtotal = priceNode.getChild("subtotal")
                        .flatMap(Node::toContentString)
                        .map(Integer::parseInt).orElse(null);
                tax = priceNode.getChild("tax")
                        .flatMap(Node::toContentString)
                        .map(Integer::parseInt).orElse(null);
                total = priceNode.getChild("total")
                        .flatMap(Node::toContentString)
                        .map(Integer::parseInt).orElse(null);
                currency = priceNode.getChild("currency")
                        .flatMap(Node::toContentString).orElse(null);
            }
            var products = new ArrayList<LineItem>();
            for (var productNode : orderNode.getChildren("product")) {
                var idNode = productNode.getChild("id").orElse(null);
                var nameNode = productNode.getChild("name").orElse(null);
                if (idNode == null || nameNode == null) {
                    continue;
                }
                var id = idNode.toContentString().orElse("");
                var name = nameNode.toContentString().orElse("");
                var price = productNode.getChild("price")
                        .flatMap(Node::toContentString)
                        .map(Integer::parseInt).orElse(null);
                var quantity = productNode.getChild("quantity")
                        .flatMap(Node::toContentString)
                        .map(Integer::parseInt).orElse(null);
                var lineCurrency = productNode.getChild("currency")
                        .flatMap(Node::toContentString).orElse(null);
                String thumbnailId = null;
                String thumbnailUrl = null;
                var imageNode = productNode.getChild("image").orElse(null);
                if (imageNode != null) {
                    thumbnailId = imageNode.getChild("id")
                            .flatMap(Node::toContentString).orElse(null);
                    thumbnailUrl = imageNode.getChild("url")
                            .flatMap(Node::toContentString).orElse(null);
                }
                var properties = new ArrayList<Map.Entry<String, String>>();
                productNode.getChild("variant_info").ifPresent(variantInfo ->
                        variantInfo.getChild("properties").ifPresent(props -> {
                            for (var prop : props.getChildren("property")) {
                                var pname = prop.getAttributeAsString("name").orElse(null);
                                var pvalue = prop.getAttributeAsString("value").orElse(null);
                                if (pname != null && pvalue != null) {
                                    properties.add(Map.entry(pname, pvalue));
                                }
                            }
                        }));
                products.add(new LineItem(id, name, price, thumbnailId, thumbnailUrl,
                        lineCurrency, quantity, properties));
            }
            return Optional.of(new Success(createdAt, currency, subtotal, tax, total, products));
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
            return Objects.equals(this.createdAt, that.createdAt)
                    && Objects.equals(this.currency, that.currency)
                    && Objects.equals(this.subtotal, that.subtotal)
                    && Objects.equals(this.tax, that.tax)
                    && Objects.equals(this.total, that.total)
                    && Objects.equals(this.products, that.products);
        }

        @Override
        public int hashCode() {
            return Objects.hash(createdAt, currency, subtotal, tax, total, products);
        }

        @Override
        public String toString() {
            return "IqBizQueryOrderResponse.Success[createdAt=" + createdAt
                    + ", currency=" + currency + ", subtotal=" + subtotal
                    + ", tax=" + tax + ", total=" + total + ", products=" + products + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant surfacing a client-side rejection.
     *
     * @apiNote
     * Use this variant to react to a refused order fetch; WA Web's GraphQL path maps the e-commerce 451 sanctions block here and any other code-based rejection from {@code WAWebBackendErrors.ServerStatusCodeError} surfaces with the same shape.
     */
    final class ClientError implements IqBizQueryOrderResponse {
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
         * Call this entry from {@link IqBizQueryOrderResponse#of(Node, Node)} or directly when only the client-error branch is interesting; returns empty when the stanza does not carry a client-error envelope matching the original request.
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
            return "IqBizQueryOrderResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant surfacing a server-side failure.
     *
     * @apiNote
     * Use this variant to react to a backend failure that did not produce a typed order; typical examples include the relay returning a 500-class status when the GraphQL path returned an unparseable shape.
     */
    final class ServerError implements IqBizQueryOrderResponse {
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
         * Call this entry from {@link IqBizQueryOrderResponse#of(Node, Node)} or directly when only the server-error branch is interesting; returns empty when the stanza does not carry a server-error envelope matching the original request.
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
            return "IqBizQueryOrderResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
