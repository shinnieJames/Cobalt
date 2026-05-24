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
 * The typed sealed family of inbound reply variants produced by the relay in response to an {@link IqQueryBusinessCategoriesRequest}.
 *
 * @apiNote
 * Use this type to switch over the three documented outcomes of a business-category lookup: {@link Success} carries the typed category list and the {@code not_a_biz} sentinel id, {@link ClientError} surfaces a relay validation rejection, and {@link ServerError} reports a transport or backend failure. The dispatcher invokes {@link #of(Node, Node)} to project the raw {@link Node} into the right variant before handing it to the caller.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryBusinessCategoriesJob")
public sealed interface IqQueryBusinessCategoriesResponse extends IqOperation.Response
        permits IqQueryBusinessCategoriesResponse.Success, IqQueryBusinessCategoriesResponse.ClientError, IqQueryBusinessCategoriesResponse.ServerError {

    /**
     * Tries each {@link IqQueryBusinessCategoriesResponse} variant in priority order.
     *
     * @apiNote
     * Call this entry from the dispatcher to fan the inbound stanza into the matching sealed variant; the success path is tried first, then the client-error envelope, then the server-error envelope. Returns empty only when none of the three documented shapes apply.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty when no documented variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    static Optional<? extends IqQueryBusinessCategoriesResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant carrying the typed category list and the {@code not_a_biz} sentinel id.
     *
     * @apiNote
     * Use this variant to render the category picker; the typed {@link Category} entries drive the rows of the picker and the {@code not_a_biz} sentinel id marks the synthetic opt-out row that lets the merchant signal "not a business".
     */
    final class Success implements IqQueryBusinessCategoriesResponse {
        /**
         * One typed category entry decoded from a {@code <category/>} child of the relay reply.
         *
         * @apiNote
         * Use this entry to render one row in the category picker; the {@link #notABiz()} flag marks the synthetic opt-out row.
         */
        public static final class Category {
            /**
             * The opaque category identifier carried by the {@code id} attribute of the {@code <category/>} child.
             */
            private final String id;

            /**
             * The localised display name shown in the picker UI, lifted from the {@code <category/>} child content.
             */
            private final String localizedDisplayName;

            /**
             * The {@code not_a_biz} sentinel flag; {@code true} only when the entry's id matches the id carried by the {@code <not_a_biz/>} sentinel block.
             */
            private final boolean notABiz;

            /**
             * Constructs a typed category entry.
             *
             * @apiNote
             * Call this constructor when projecting a {@code <category/>} child into the typed model; pass {@code true} for {@code notABiz} only when the entry's id matches the {@code <not_a_biz/>} sentinel.
             *
             * @param id                   the category identifier; never {@code null}
             * @param localizedDisplayName the display name; never {@code null}
             * @param notABiz              the sentinel flag
             * @throws NullPointerException if either string is {@code null}
             */
            public Category(String id, String localizedDisplayName, boolean notABiz) {
                this.id = Objects.requireNonNull(id, "id cannot be null");
                this.localizedDisplayName = Objects.requireNonNull(
                        localizedDisplayName, "localizedDisplayName cannot be null");
                this.notABiz = notABiz;
            }

            /**
             * Returns the category identifier.
             *
             * @apiNote
             * Use this getter to read back the opaque category id, which is the value the SMB profile editor sends back through {@link IqEditBusinessProfileRequest} to mark the merchant's category.
             *
             * @return the identifier; never {@code null}
             */
            public String id() {
                return id;
            }

            /**
             * Returns the localised display name.
             *
             * @apiNote
             * Use this getter to render the per-row label in the category picker.
             *
             * @return the display name; never {@code null}
             */
            public String localizedDisplayName() {
                return localizedDisplayName;
            }

            /**
             * Returns the {@code not_a_biz} sentinel flag.
             *
             * @apiNote
             * Use this getter to detect the synthetic opt-out row; consumers render this row differently from the catalogue rows because it does not name a real business category.
             *
             * @return {@code true} when this entry is the synthetic opt-out
             */
            public boolean notABiz() {
                return notABiz;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (Category) obj;
                return this.notABiz == that.notABiz
                        && Objects.equals(this.id, that.id)
                        && Objects.equals(this.localizedDisplayName, that.localizedDisplayName);
            }

            @Override
            public int hashCode() {
                return Objects.hash(id, localizedDisplayName, notABiz);
            }

            @Override
            public String toString() {
                return "IqQueryBusinessCategoriesResponse.Success.Category[id=" + id
                        + ", localizedDisplayName=" + localizedDisplayName
                        + ", notABiz=" + notABiz + ']';
            }
        }

        /**
         * The typed category entries in wire order.
         */
        private final List<Category> categories;

        /**
         * The id of the synthetic {@code not_a_biz} sentinel, or the empty string when the relay returned no sentinel block.
         */
        private final String notABizId;

        /**
         * Constructs a typed success reply.
         *
         * @apiNote
         * Call this constructor when projecting the typed category list and the optional {@code not_a_biz} sentinel id into the typed model; pass the empty string when the sentinel block is absent.
         *
         * @param categories the category list; never {@code null}
         * @param notABizId  the sentinel id; never {@code null} (empty string when absent)
         * @throws NullPointerException if either argument is {@code null}
         */
        public Success(List<Category> categories, String notABizId) {
            Objects.requireNonNull(categories, "categories cannot be null");
            this.categories = List.copyOf(categories);
            this.notABizId = Objects.requireNonNull(notABizId, "notABizId cannot be null");
        }

        /**
         * Returns the typed category entries.
         *
         * @apiNote
         * Use this getter to iterate the categories when rendering the picker; the list preserves the wire order which mirrors the merchant-facing display order.
         *
         * @return an unmodifiable list; never {@code null}
         */
        public List<Category> categories() {
            return categories;
        }

        /**
         * Returns the {@code not_a_biz} sentinel id.
         *
         * @apiNote
         * Use this getter to look up the sentinel id when filtering the category list; the value is the empty string when the relay did not stamp a sentinel block.
         *
         * @return the sentinel id; empty string when absent
         */
        public String notABizId() {
            return notABizId;
        }

        /**
         * Tries to parse a {@link Success} variant from the inbound stanza.
         *
         * @apiNote
         * Call this entry from {@link IqQueryBusinessCategoriesResponse#of(Node, Node)} or directly when only the success branch is interesting; returns empty when the stanza does not carry a {@code result} envelope matching the original request.
         *
         * @implNote
         * This implementation mirrors the deprecated WAP parser inside {@code WAWebQueryBusinessCategoriesJob.businessCategoriesResponse}: the {@code <response/>} envelope carries an optional {@code <not_a_biz/>} sentinel and a mandatory {@code <categories/>} block. Each {@code <category id/>} child contributes a {@link Category} whose {@code notABiz} flag is set when the entry id matches the sentinel id, replicating the WA Web parser's per-row classification. An empty {@code <response/>} produces an empty list and an empty sentinel id.
         *
         * @param node    the inbound IQ stanza; never {@code null}
         * @param request the original outbound request; never {@code null}
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebQueryBusinessCategoriesJob",
                exports = "queryBusinessCategories", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var responseNode = node.getChild("response").orElse(null);
            if (responseNode == null) {
                return Optional.of(new Success(Collections.emptyList(), ""));
            }
            var notABizId = responseNode.getChild("not_a_biz")
                    .stream()
                    .flatMap(child -> child.streamChildren("category"))
                    .map(category -> category.getAttributeAsString("id").orElse(""))
                    .reduce("", (acc, id) -> id);
            var categoriesNode = responseNode.getChild("categories").orElse(null);
            var categories = new ArrayList<Category>();
            if (categoriesNode != null) {
                for (var categoryNode : categoriesNode.getChildren("category")) {
                    var id = categoryNode.getAttributeAsString("id").orElse(null);
                    if (id == null) {
                        continue;
                    }
                    var displayName = categoryNode.toContentString().orElse("");
                    var isNotABiz = !notABizId.isEmpty() && id.equals(notABizId);
                    categories.add(new Category(id, displayName, isNotABiz));
                }
            }
            return Optional.of(new Success(categories, notABizId));
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
            return Objects.equals(this.categories, that.categories)
                    && Objects.equals(this.notABizId, that.notABizId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(categories, notABizId);
        }

        @Override
        public String toString() {
            return "IqQueryBusinessCategoriesResponse.Success[categories=" + categories
                    + ", notABizId=" + notABizId + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant surfacing a client-side rejection.
     *
     * @apiNote
     * Use this variant to react to a refused category lookup; typical examples include a relay validation rejection surfaced as a SMAX error envelope.
     */
    final class ClientError implements IqQueryBusinessCategoriesResponse {
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
         * Call this entry from {@link IqQueryBusinessCategoriesResponse#of(Node, Node)} or directly when only the client-error branch is interesting; returns empty when the stanza does not carry a client-error envelope matching the original request.
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
            return "IqQueryBusinessCategoriesResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant surfacing a server-side failure.
     *
     * @apiNote
     * Use this variant to react to a backend failure that did not produce a typed category list; WA Web's {@code WAWebQueryBusinessCategoriesJob.queryBusinessCategories} surfaces this as a {@code ServerStatusCodeError} carrying the relay-supplied status.
     */
    final class ServerError implements IqQueryBusinessCategoriesResponse {
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
         * Call this entry from {@link IqQueryBusinessCategoriesResponse#of(Node, Node)} or directly when only the server-error branch is interesting; returns empty when the stanza does not carry a server-error envelope matching the original request.
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
            return "IqQueryBusinessCategoriesResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
