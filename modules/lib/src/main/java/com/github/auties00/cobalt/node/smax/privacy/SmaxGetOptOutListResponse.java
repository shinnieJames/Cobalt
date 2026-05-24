package com.github.auties00.cobalt.node.smax.privacy;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed family of inbound replies to a {@link SmaxGetOptOutListRequest}.
 *
 * @apiNote
 * Drives the marketing-messages opt-out list refresh; matches the four response shapes that WA Web's
 * {@code WASmaxBlocklistsGetOptOutListRPC.sendGetOptOutListRPC} dispatches over: cache-match short-circuit,
 * full-list mismatch, malformed request, and transient server error.
 *
 * @implNote
 * This implementation preserves WA Web's parser priority order in {@link #of(Node, Node)}: mismatch first so a
 * full-body reply cannot be mistaken for a cache-match short-circuit.
 */
public sealed interface SmaxGetOptOutListResponse extends SmaxOperation.Response
        permits SmaxGetOptOutListResponse.SuccessWithMatch,
        SmaxGetOptOutListResponse.SuccessWithMismatch,
        SmaxGetOptOutListResponse.ClientError,
        SmaxGetOptOutListResponse.ServerError {

    /**
     * Dispatches the inbound stanza onto the matching variant.
     *
     * @apiNote
     * Called by the SMAX dispatcher in response to a previously-issued {@link SmaxGetOptOutListRequest}.
     *
     * @param node    the inbound {@code <iq>} stanza; never {@code null}
     * @param request the original {@link SmaxGetOptOutListRequest} stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty when no variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxBlocklistsGetOptOutListRPC",
            exports = "sendGetOptOutListRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxGetOptOutListResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var successWithMismatch = SuccessWithMismatch.of(node, request);
        if (successWithMismatch.isPresent()) {
            return successWithMismatch;
        }
        var successWithMatch = SuccessWithMatch.of(node, request);
        if (successWithMatch.isPresent()) {
            return successWithMatch;
        }
        var clientError = ClientError.of(node, request);
        if (clientError.isPresent()) {
            return clientError;
        }
        return ServerError.of(node, request);
    }

    /**
     * One {@code <item>} entry in a mismatch reply's opt-out list.
     *
     * @apiNote
     * Surfaced through {@link SuccessWithMismatch#listItem()}; consumed by
     * {@code WAWebGetOptOutList.getOptOutList} to either render a direct {@link BizOptOutId.UserJid} pill or
     * expand a {@link BizOptOutId.BrandId} into the full business-numbers set via
     * {@code WAWebGetNumbersForBrandIdsJob}.
     *
     * @param action       the optional action marker (e.g. {@code "block"}); may be {@code null}
     * @param category     the optional marketing category; may be {@code null}
     * @param expiryAt     the optional non-negative expiry timestamp in seconds; may be {@code null}
     * @param bizOptOutIds the brand-id-versus-jid discriminator; never {@code null}
     */
    record Item(String action, String category, Long expiryAt, BizOptOutId bizOptOutIds) {
        /**
         * Validates the {@link Item} payload.
         *
         * @apiNote
         * The compact constructor enforces the discriminator's non-null contract; the other fields are
         * optional and arrive pre-validated by {@link SmaxGetOptOutListResponse#parseItem(Node)}.
         *
         * @param action       the optional action; may be {@code null}
         * @param category     the optional category; may be {@code null}
         * @param expiryAt     the optional expiry; may be {@code null}
         * @param bizOptOutIds the discriminator; never {@code null}
         * @throws NullPointerException if {@code bizOptOutIds} is {@code null}
         */
        public Item {
            Objects.requireNonNull(bizOptOutIds, "bizOptOutIds cannot be null");
        }

        /**
         * Returns the action marker when present.
         *
         * @return an {@link Optional} carrying the action, or empty when the relay omitted it
         */
        public Optional<String> actionAsOptional() {
            return Optional.ofNullable(action);
        }

        /**
         * Returns the marketing category when present.
         *
         * @return an {@link Optional} carrying the category, or empty when the relay omitted it
         */
        public Optional<String> categoryAsOptional() {
            return Optional.ofNullable(category);
        }

        /**
         * Returns the expiry timestamp when present.
         *
         * @return an {@link Optional} carrying the timestamp in seconds, or empty when the relay omitted it
         */
        public Optional<Long> expiryAtAsOptional() {
            return Optional.ofNullable(expiryAt);
        }
    }

    /**
     * Parses one {@code <item>} entry from an opt-out reply.
     *
     * @apiNote
     * Shared by {@link SuccessWithMismatch#of(Node, Node)} and {@link SmaxUpdateOptOutListResponse} for the
     * single-item update reply.
     *
     * @implNote
     * This implementation rejects a present-but-negative {@code expiry_at} so the priority chain can route
     * malformed entries to the error variants instead of accepting a nonsensical timestamp; the brand-id
     * disjunction is delegated to {@link BizOptOutId#parse(Node)}.
     *
     * @param itemNode the {@code <item>} node; never {@code null}
     * @return an {@link Optional} carrying the populated {@link Item}, or empty when the discriminator does
     *         not match or {@code expiry_at} is negative
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsBizOptOutResponseMixin",
            exports = "parseBizOptOutResponseMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsGetOptOutListResponseSuccessWithMismatch",
            exports = "parseGetOptOutListResponseSuccessWithMismatchListItem",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static Optional<Item> parseItem(Node itemNode) {
        var action = itemNode.getAttributeAsString("action").orElse(null);
        var category = itemNode.getAttributeAsString("category").orElse(null);
        Long expiryAt = null;
        if (itemNode.hasAttribute("expiry_at")) {
            var parsed = itemNode.getAttributeAsLong("expiry_at");
            if (parsed.isEmpty() || parsed.getAsLong() < 0L) {
                return Optional.empty();
            }
            expiryAt = parsed.getAsLong();
        }
        var ids = BizOptOutId.parse(itemNode).orElse(null);
        if (ids == null) {
            return Optional.empty();
        }
        return Optional.of(new Item(action, category, expiryAt, ids));
    }

    /**
     * The cache-match short-circuit reply.
     *
     * @apiNote
     * {@code WAWebGetOptOutList.getOptOutList} treats this variant as a no-op: the local cache is up to date
     * and the chat-list pills are kept as-is.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBlocklistsGetOptOutListResponseSuccessWithMatch")
    final class SuccessWithMatch implements SmaxGetOptOutListResponse {
        /**
         * Whether the relay echoed the request's category attribute.
         *
         * @apiNote
         * Distinguishes a relay-echoed category match (the relay confirmed the request was scoped) from a
         * relay-omitted echo (the relay treated the request as unscoped). The WA Web caller does not branch on
         * this, but it is preserved for symmetry with WA Web's {@code hasCategory} payload field.
         */
        private final boolean hasCategory;

        /**
         * Constructs a cache-match reply.
         *
         * @apiNote
         * Invoked from {@link #of(Node, Node)} only.
         *
         * @param hasCategory whether the reply echoed a category attribute
         */
        public SuccessWithMatch(boolean hasCategory) {
            this.hasCategory = hasCategory;
        }

        /**
         * Returns whether the reply echoed a category attribute.
         *
         * @return {@code true} when the relay echoed the request's category
         */
        public boolean hasCategory() {
            return hasCategory;
        }

        /**
         * Parses a cache-match variant.
         *
         * @apiNote
         * Returns empty when the envelope is wrong or when the request specified a category and the reply
         * echoes a different one (a mismatch on the category narrows the reply out of this variant so the
         * priority chain can fall through).
         *
         * @implNote
         * This implementation mirrors WA Web's {@code optionalLiteral} contract on the category attribute:
         * the reply is allowed to omit the attribute entirely, but a present attribute must echo the request.
         *
         * @param node    the inbound stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the variant, or empty when the envelope shape does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsGetOptOutListResponseSuccessWithMatch",
                exports = "parseGetOptOutListResponseSuccessWithMatch",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<SuccessWithMatch> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var requestCategory = request.getAttributeAsString("category").orElse(null);
            var replyCategory = node.getAttributeAsString("category").orElse(null);
            if (requestCategory != null && replyCategory != null && !replyCategory.equals(requestCategory)) {
                return Optional.empty();
            }
            return Optional.of(new SuccessWithMatch(replyCategory != null));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (SuccessWithMatch) obj;
            return this.hasCategory == that.hasCategory;
        }

        @Override
        public int hashCode() {
            return Objects.hash(hasCategory);
        }

        @Override
        public String toString() {
            return "SmaxGetOptOutListResponse.SuccessWithMatch[hasCategory=" + hasCategory + ']';
        }
    }

    /**
     * The full-list mismatch reply.
     *
     * @apiNote
     * {@code WAWebGetOptOutList.getOptOutList} consumes this variant to refresh the marketing-messages
     * opt-out pills: each {@link Item#bizOptOutIds()} either resolves directly to a {@code wid} via
     * {@code WAWebJidToWid.userJidToUserWid} (for the {@link BizOptOutId.UserJid} arm) or is batched into a
     * brand-id-to-business-numbers lookup via {@code WAWebGetNumbersForBrandIdsJob} (for the
     * {@link BizOptOutId.BrandId} arm).
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBlocklistsGetOptOutListResponseSuccessWithMismatch")
    final class SuccessWithMismatch implements SmaxGetOptOutListResponse {
        /**
         * The new server-side digest when present.
         */
        private final String listDhash;

        /**
         * The parsed opt-out list entries.
         */
        private final List<Item> listItem;

        /**
         * Constructs a mismatch reply.
         *
         * @apiNote
         * Invoked from {@link #of(Node, Node)} only; the {@code listItem} list is defensively copied for
         * immutability.
         *
         * @param listDhash the new server digest; may be {@code null}
         * @param listItem  the parsed list; never {@code null}
         * @throws NullPointerException if {@code listItem} is {@code null}
         */
        public SuccessWithMismatch(String listDhash, List<Item> listItem) {
            this.listDhash = listDhash;
            this.listItem = List.copyOf(Objects.requireNonNull(listItem, "listItem cannot be null"));
        }

        /**
         * Returns the new server digest when present.
         *
         * @apiNote
         * Persisted alongside the resulting list so the next request can use the cache-match short-circuit.
         *
         * @return an {@link Optional} carrying the digest, or empty when the relay omitted it
         */
        public Optional<String> listDhash() {
            return Optional.ofNullable(listDhash);
        }

        /**
         * Returns the parsed item list.
         *
         * @return an unmodifiable list of items; never {@code null}
         */
        public List<Item> listItem() {
            return listItem;
        }

        /**
         * Parses a mismatch variant.
         *
         * @apiNote
         * Returns empty when the envelope is wrong, when the {@code <list/>} child is absent, or when any
         * {@code <item>} fails the per-item parser (because the brand-id-versus-jid disjunction did not match
         * or {@code expiry_at} was negative).
         *
         * @param node    the inbound stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the variant, or empty when the envelope shape does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsGetOptOutListResponseSuccessWithMismatch",
                exports = "parseGetOptOutListResponseSuccessWithMismatch",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<SuccessWithMismatch> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var list = node.getChild("list").orElse(null);
            if (list == null) {
                return Optional.empty();
            }
            var dhash = list.getAttributeAsString("dhash").orElse(null);
            var items = new ArrayList<Item>();
            for (var child : list.getChildren("item")) {
                var parsed = parseItem(child).orElse(null);
                if (parsed == null) {
                    return Optional.empty();
                }
                items.add(parsed);
            }
            return Optional.of(new SuccessWithMismatch(dhash, Collections.unmodifiableList(items)));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (SuccessWithMismatch) obj;
            return Objects.equals(this.listDhash, that.listDhash)
                    && Objects.equals(this.listItem, that.listItem);
        }

        @Override
        public int hashCode() {
            return Objects.hash(listDhash, listItem);
        }

        @Override
        public String toString() {
            return "SmaxGetOptOutListResponse.SuccessWithMismatch[listDhash=" + listDhash
                    + ", listItem=" + listItem + ']';
        }
    }

    /**
     * The malformed-request reply variant.
     *
     * @apiNote
     * {@code WAWebGetOptOutList.getOptOutList} folds this into the {@code (errorCode, errorText)} pair returned
     * to the caller.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBlocklistsGetOptOutListResponseInvalidRequest")
    final class ClientError implements SmaxGetOptOutListResponse {
        /**
         * The numeric server-side error code.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a client-error reply.
         *
         * @apiNote
         * Invoked from {@link #of(Node, Node)} only after the shared
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)} envelope check succeeds.
         *
         * @param errorCode the numeric error code echoed by the relay
         * @param errorText the optional human-readable text; may be {@code null}
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
         * Returns the human-readable error text when present.
         *
         * @return an {@link Optional} carrying the text, or empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Parses a malformed-request variant.
         *
         * @apiNote
         * Delegates the envelope check to {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}.
         *
         * @param node    the inbound stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the variant, or empty when the envelope shape does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsGetOptOutListResponseInvalidRequest",
                exports = "parseGetOptOutListResponseInvalidRequest",
                adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "SmaxGetOptOutListResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The transient server-error reply variant.
     *
     * @apiNote
     * {@code WAWebGetOptOutList.getOptOutList} folds this into the {@code (errorCode, errorText)} pair; the
     * caller decides the retry policy.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBlocklistsGetOptOutListResponseInternalServerError")
    final class ServerError implements SmaxGetOptOutListResponse {
        /**
         * The numeric server-side error code.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a server-error reply.
         *
         * @apiNote
         * Invoked from {@link #of(Node, Node)} only after the shared
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)} envelope check succeeds.
         *
         * @param errorCode the numeric error code echoed by the relay
         * @param errorText the optional human-readable text; may be {@code null}
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
         * Returns the human-readable error text when present.
         *
         * @return an {@link Optional} carrying the text, or empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Parses a server-error variant.
         *
         * @apiNote
         * Delegates the envelope check to {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}.
         *
         * @param node    the inbound stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the variant, or empty when the envelope shape does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsGetOptOutListResponseInternalServerError",
                exports = "parseGetOptOutListResponseInternalServerError",
                adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "SmaxGetOptOutListResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
