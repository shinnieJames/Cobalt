package com.github.auties00.cobalt.node.smax.receipt;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The closed family of inbound reply variants to a
 * {@link SmaxReceiptPublishViewRequest}.
 *
 * @apiNote
 * Permits only the {@link Success} shape; WA Web's
 * {@code WASmaxReceiptPublishViewRPC.sendPublishViewRPC} throws a
 * {@code SmaxParsingFailure} on any other shape, so embedders that
 * receive a non-success reply should treat it as a relay-protocol
 * violation and disconnect.
 */
public sealed interface SmaxReceiptPublishViewResponse extends SmaxOperation.Response
        permits SmaxReceiptPublishViewResponse.Success {

    /**
     * Parses the inbound stanza into the sealed family's single
     * permit.
     *
     * @apiNote
     * Delegates to {@link Success#of(Node, Node)}; returns
     * {@link Optional#empty()} when the stanza does not match the
     * documented {@code <ack class="receipt">} shape.
     *
     * @param node the inbound ack stanza; never {@code null}
     * @param request the original outbound stanza used to validate
     *                echoed identifiers; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or
     *         empty when the stanza does not match
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxReceiptPublishViewRPC",
            exports = "sendPublishViewRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxReceiptPublishViewResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        return Success.of(node, request);
    }

    /**
     * The {@code Success} reply variant; the relay produced an
     * {@code <ack class="receipt">} envelope.
     *
     * @apiNote
     * Carries the optional timestamp echo, the optional
     * {@code readreceipts} echo ({@code "all"} or {@code "none"}),
     * and the optional deprecated {@code edit} marker
     * ({@code "0"}, {@code "1"}, or {@code "7"}).
     */
    @WhatsAppWebModule(moduleName = "WASmaxInReceiptPublishViewResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInReceiptPublishSuccessMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInReceiptDeprecatedEditMixin")
    final class Success implements SmaxReceiptPublishViewResponse {
        /**
         * The optional Unix-epoch timestamp echo carrying the relay's
         * processing time.
         */
        private final Long timestamp;

        /**
         * The optional {@code readreceipts} echo; one of {@code "all"}
         * or {@code "none"}.
         */
        private final String readReceipts;

        /**
         * The optional deprecated {@code edit} marker; one of
         * {@code "0"}, {@code "1"}, or {@code "7"}.
         */
        private final String deprecatedEdit;

        /**
         * Constructs a new success reply.
         *
         * @apiNote
         * Used by {@link #of(Node, Node)} after the envelope shape
         * has been validated; embedders typically do not instantiate
         * this directly.
         *
         * @param timestamp the optional timestamp; may be {@code null}
         * @param readReceipts the optional read-receipts echo; may be
         *                     {@code null}
         * @param deprecatedEdit the optional deprecated edit marker;
         *                       may be {@code null}
         */
        public Success(Long timestamp, String readReceipts, String deprecatedEdit) {
            this.timestamp = timestamp;
            this.readReceipts = readReceipts;
            this.deprecatedEdit = deprecatedEdit;
        }

        /**
         * Returns the optional timestamp echo.
         *
         * @return an {@link Optional} carrying the timestamp, or
         *         empty when the relay omitted it
         */
        public Optional<Long> timestamp() {
            return Optional.ofNullable(timestamp);
        }

        /**
         * Returns the optional {@code readreceipts} echo.
         *
         * @return an {@link Optional} carrying the value, or empty
         *         when the relay omitted it
         */
        public Optional<String> readReceipts() {
            return Optional.ofNullable(readReceipts);
        }

        /**
         * Returns the optional deprecated {@code edit} marker.
         *
         * @return an {@link Optional} carrying the marker, or empty
         *         when the relay omitted it
         */
        public Optional<String> deprecatedEdit() {
            return Optional.ofNullable(deprecatedEdit);
        }

        /**
         * Parses a {@link Success} variant from the given inbound
         * stanza.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when the stanza is not an
         * {@code <ack class="receipt">} that echoes the original
         * request's {@code id}/{@code to}/{@code type} triple, or
         * when any of the optional attributes is present with a value
         * outside its documented enum.
         *
         * @implNote
         * This implementation inlines the
         * {@code parsePublishSuccessMixin} predicates from WA Web's
         * {@code WASmaxInReceiptPublishSuccessMixin} (envelope-echo
         * validation, optional {@code t}, optional
         * {@code readreceipts} enum, optional deprecated {@code edit}
         * enum) rather than splitting them across helper classes; the
         * three optional enums are validated by exact-match equality
         * because Cobalt has no equivalent of WA Web's reusable
         * {@code attrStringEnum} helper here.
         *
         * @param node the inbound ack stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInReceiptPublishViewResponseSuccess",
                exports = "parsePublishViewResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInReceiptPublishSuccessMixin",
                exports = "parsePublishSuccessMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            Objects.requireNonNull(node, "node cannot be null");
            Objects.requireNonNull(request, "request cannot be null");
            if (!node.hasDescription("ack")) {
                return Optional.empty();
            }
            if (!node.hasAttribute("class", "receipt")) {
                return Optional.empty();
            }
            var requestId = request.getAttributeAsString("id").orElse(null);
            if (requestId == null || !node.hasAttribute("id", requestId)) {
                return Optional.empty();
            }
            var requestTo = request.getAttributeAsString("to").orElse(null);
            if (requestTo == null || !node.hasAttribute("from", requestTo)) {
                return Optional.empty();
            }
            var requestType = request.getAttributeAsString("type").orElse(null);
            if (requestType == null || !node.hasAttribute("type", requestType)) {
                return Optional.empty();
            }
            var timestamp = node.getAttributeAsLong("t");
            var timestampValue = timestamp.isPresent() ? Long.valueOf(timestamp.getAsLong()) : null;
            var readReceipts = node.getAttributeAsString("readreceipts").orElse(null);
            if (readReceipts != null
                    && !"all".equals(readReceipts)
                    && !"none".equals(readReceipts)) {
                return Optional.empty();
            }
            var deprecatedEdit = node.getAttributeAsString("edit").orElse(null);
            if (deprecatedEdit != null
                    && !"0".equals(deprecatedEdit)
                    && !"1".equals(deprecatedEdit)
                    && !"7".equals(deprecatedEdit)) {
                return Optional.empty();
            }
            return Optional.of(new Success(timestampValue, readReceipts, deprecatedEdit));
        }

        /**
         * Returns whether the given object is a {@link Success} with
         * equal timestamp, read-receipts, and edit marker.
         *
         * @param obj the candidate; may be {@code null}
         * @return {@code true} when every field matches
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
            return Objects.equals(this.timestamp, that.timestamp)
                    && Objects.equals(this.readReceipts, that.readReceipts)
                    && Objects.equals(this.deprecatedEdit, that.deprecatedEdit);
        }

        /**
         * Returns a hash code derived from every field.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(timestamp, readReceipts, deprecatedEdit);
        }

        /**
         * Returns a debug-friendly textual representation of this
         * variant.
         *
         * @return the textual representation
         */
        @Override
        public String toString() {
            return "SmaxReceiptPublishViewResponse.Success[timestamp=" + timestamp
                    + ", readReceipts=" + readReceipts
                    + ", deprecatedEdit=" + deprecatedEdit + ']';
        }
    }
}
