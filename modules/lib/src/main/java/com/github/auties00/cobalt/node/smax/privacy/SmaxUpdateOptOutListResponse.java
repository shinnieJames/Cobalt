package com.github.auties00.cobalt.node.smax.privacy;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed family of inbound replies to a {@link SmaxUpdateOptOutListRequest}.
 *
 * @apiNote
 * Drives the marketing-messages opt-out / opt-in / signup actions; matches the four response shapes that WA Web's
 * {@code WASmaxBlocklistsUpdateOptOutListRPC.sendUpdateOptOutListRPC} dispatches over: cache-match
 * single-item confirmation, full-list mismatch, malformed-request error, and transient server error.
 *
 * @implNote
 * This implementation preserves WA Web's parser priority order in {@link #of(Node, Node)}: match first, then
 * mismatch, then the two error variants.
 */
public sealed interface SmaxUpdateOptOutListResponse extends SmaxOperation.Response
        permits SmaxUpdateOptOutListResponse.SuccessWithMatch,
        SmaxUpdateOptOutListResponse.SuccessWithMismatch,
        SmaxUpdateOptOutListResponse.ClientError,
        SmaxUpdateOptOutListResponse.ServerError {

    /**
     * Dispatches the inbound stanza onto the matching variant.
     *
     * @apiNote
     * Called by the SMAX dispatcher in response to a previously-issued {@link SmaxUpdateOptOutListRequest}.
     *
     * @param node    the inbound {@code <iq>} stanza; never {@code null}
     * @param request the original {@link SmaxUpdateOptOutListRequest} stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty when no variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxBlocklistsUpdateOptOutListRPC",
            exports = "sendUpdateOptOutListRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxUpdateOptOutListResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var successWithMatch = SuccessWithMatch.of(node, request);
        if (successWithMatch.isPresent()) {
            return successWithMatch;
        }
        var successWithMismatch = SuccessWithMismatch.of(node, request);
        if (successWithMismatch.isPresent()) {
            return successWithMismatch;
        }
        var clientError = ClientError.of(node, request);
        if (clientError.isPresent()) {
            return clientError;
        }
        return ServerError.of(node, request);
    }

    /**
     * One {@code <item>} entry in an opt-out reply (single-item for match replies, list for mismatch replies).
     *
     * @apiNote
     * Surfaced from {@link SuccessWithMatch#item()} (single item) and {@link SuccessWithMismatch#listItem()}
     * (list of items); each carries the optional action, category, and expiry plus the
     * brand-id-versus-jid discriminator.
     *
     * @param action       the optional action marker; may be {@code null}
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
         * optional and arrive pre-validated by {@link SmaxUpdateOptOutListResponse#parseItem(Node)}.
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
     * Shared by both success variants; the implementation mirrors
     * {@link SmaxGetOptOutListResponse#parseItem(Node)} bit-for-bit so the same opt-out item shape decodes
     * identically across get and update flows.
     *
     * @param itemNode the source node; never {@code null}
     * @return an {@link Optional} carrying the populated {@link Item}, or empty when the discriminator does
     *         not match or {@code expiry_at} is negative
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsBizOptOutResponseMixin",
            exports = "parseBizOptOutResponseMixin", adaptation = WhatsAppAdaptation.ADAPTED)
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
     * The cache-match success reply, returned when the relay applied the action and the cache was up to date.
     *
     * @apiNote
     * {@code WAWebOptOutUserJob.optInOutUser} / {@code signupUser} treat this as the success path; the single
     * {@link Item} echoes the action's effect for confirmation purposes.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBlocklistsUpdateOptOutListResponseSuccessWithMatch")
    final class SuccessWithMatch implements SmaxUpdateOptOutListResponse {
        /**
         * The new server-side digest of the opt-out list.
         */
        private final String listDhash;

        /**
         * The single item descriptor echoed by the relay.
         */
        private final Item item;

        /**
         * Constructs a cache-match reply.
         *
         * @apiNote
         * Invoked from {@link #of(Node, Node)} only.
         *
         * @param listDhash the new server digest; never {@code null}
         * @param item      the echoed item descriptor; never {@code null}
         * @throws NullPointerException if either argument is {@code null}
         */
        public SuccessWithMatch(String listDhash, Item item) {
            this.listDhash = Objects.requireNonNull(listDhash, "listDhash cannot be null");
            this.item = Objects.requireNonNull(item, "item cannot be null");
        }

        /**
         * Returns the new server digest.
         *
         * @return the digest; never {@code null}
         */
        public String listDhash() {
            return listDhash;
        }

        /**
         * Returns the single item descriptor.
         *
         * @return the item; never {@code null}
         */
        public Item item() {
            return item;
        }

        /**
         * Parses a cache-match variant.
         *
         * @apiNote
         * Returns empty when the envelope is wrong, when the {@code <list/>} child is absent, when
         * {@code matched} is not {@code "true"}, when {@code dhash} is missing, when the single {@code <item>}
         * child is missing, or when the item fails the per-item parser.
         *
         * @param node    the inbound stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the variant, or empty when the envelope shape does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsUpdateOptOutListResponseSuccessWithMatch",
                exports = "parseUpdateOptOutListResponseSuccessWithMatch",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<SuccessWithMatch> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var list = node.getChild("list").orElse(null);
            if (list == null) {
                return Optional.empty();
            }
            if (!list.hasAttribute("matched", "true")) {
                return Optional.empty();
            }
            var dhash = list.getAttributeAsString("dhash").orElse(null);
            if (dhash == null) {
                return Optional.empty();
            }
            var itemNode = list.getChild("item").orElse(null);
            if (itemNode == null) {
                return Optional.empty();
            }
            var parsed = parseItem(itemNode).orElse(null);
            if (parsed == null) {
                return Optional.empty();
            }
            return Optional.of(new SuccessWithMatch(dhash, parsed));
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
            return Objects.equals(this.listDhash, that.listDhash)
                    && Objects.equals(this.item, that.item);
        }

        @Override
        public int hashCode() {
            return Objects.hash(listDhash, item);
        }

        @Override
        public String toString() {
            return "SmaxUpdateOptOutListResponse.SuccessWithMatch[listDhash=" + listDhash
                    + ", item=" + item + ']';
        }
    }

    /**
     * The full-list mismatch reply, returned when the relay applied the action and the cache was stale.
     *
     * @apiNote
     * {@code WAWebOptOutUserJob.optInOutUser} / {@code signupUser} treat this as the success path that also
     * requires a chat-list refresh; consumers persist the new digest and replace the local opt-out list with
     * {@link #listItem()}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBlocklistsUpdateOptOutListResponseSuccessWithMismatch")
    final class SuccessWithMismatch implements SmaxUpdateOptOutListResponse {
        /**
         * Whether the relay echoed the request's digest as a {@code c_dhash} attribute.
         */
        private final boolean hasListCDhash;

        /**
         * The new server-side digest.
         */
        private final String listDhash;

        /**
         * The parsed item list.
         */
        private final List<Item> listItem;

        /**
         * Constructs a mismatch reply.
         *
         * @apiNote
         * Invoked from {@link #of(Node, Node)} only; the {@code listItem} list is defensively copied for
         * immutability.
         *
         * @param hasListCDhash whether the {@code c_dhash} echo was present
         * @param listDhash     the new server digest; never {@code null}
         * @param listItem      the parsed list; never {@code null}
         * @throws NullPointerException if {@code listDhash} or {@code listItem} is {@code null}
         */
        public SuccessWithMismatch(boolean hasListCDhash, String listDhash, List<Item> listItem) {
            this.hasListCDhash = hasListCDhash;
            this.listDhash = Objects.requireNonNull(listDhash, "listDhash cannot be null");
            this.listItem = List.copyOf(Objects.requireNonNull(listItem, "listItem cannot be null"));
        }

        /**
         * Returns whether the relay echoed the request's digest.
         *
         * @return {@code true} when the {@code c_dhash} echo was present
         */
        public boolean hasListCDhash() {
            return hasListCDhash;
        }

        /**
         * Returns the new server digest.
         *
         * @return the digest; never {@code null}
         */
        public String listDhash() {
            return listDhash;
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
         * Returns empty when the envelope is wrong, when the {@code <list/>} child is absent, when
         * {@code matched} is not {@code "false"}, when {@code dhash} is missing, when the {@code c_dhash}
         * echo does not match the request's {@code <item dhash/>}, when more than {@code 64000} items are
         * present, or when any item fails the per-item parser.
         *
         * @implNote
         * This implementation mirrors WA Web's hard cap of {@code 64000} items per list; exceeding the cap
         * rejects the parse so the priority chain can fall through to the error variants.
         *
         * @param node    the inbound stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the variant, or empty when the envelope shape does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsUpdateOptOutListResponseSuccessWithMismatch",
                exports = "parseUpdateOptOutListResponseSuccessWithMismatch",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<SuccessWithMismatch> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var list = node.getChild("list").orElse(null);
            if (list == null) {
                return Optional.empty();
            }
            if (!list.hasAttribute("matched", "false")) {
                return Optional.empty();
            }
            var dhash = list.getAttributeAsString("dhash").orElse(null);
            if (dhash == null) {
                return Optional.empty();
            }
            var replyCDhash = list.getAttributeAsString("c_dhash").orElse(null);
            var hasListCDhash = replyCDhash != null;
            if (hasListCDhash) {
                var requestItemDhash = request.getChild("item")
                        .flatMap(itemRef -> itemRef.getAttributeAsString("dhash"))
                        .orElse(null);
                if (requestItemDhash != null && !replyCDhash.equals(requestItemDhash)) {
                    return Optional.empty();
                }
            }
            var itemNodes = list.getChildren("item");
            if (itemNodes.size() > 64000) {
                return Optional.empty();
            }
            var entries = new ArrayList<Item>(itemNodes.size());
            for (var itemNode : itemNodes) {
                var parsed = parseItem(itemNode).orElse(null);
                if (parsed == null) {
                    return Optional.empty();
                }
                entries.add(parsed);
            }
            return Optional.of(new SuccessWithMismatch(hasListCDhash, dhash, entries));
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
            return this.hasListCDhash == that.hasListCDhash
                    && Objects.equals(this.listDhash, that.listDhash)
                    && Objects.equals(this.listItem, that.listItem);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hasListCDhash, listDhash, listItem);
        }

        @Override
        public String toString() {
            return "SmaxUpdateOptOutListResponse.SuccessWithMismatch[hasListCDhash=" + hasListCDhash
                    + ", listDhash=" + listDhash
                    + ", listItem=" + listItem + ']';
        }
    }

    /**
     * The malformed-request reply variant.
     *
     * @apiNote
     * {@code WAWebOptOutUserJob.optInOutUser} / {@code signupUser} map this to the
     * {@code (errorCode, errorText, errorKind="invalid_request")} triple surfaced to the user.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBlocklistsUpdateOptOutListResponseInvalidRequest")
    final class ClientError implements SmaxUpdateOptOutListResponse {
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
         * Invoked from {@link #of(Node, Node)} only after the shared envelope check succeeds.
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
        @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsUpdateOptOutListResponseInvalidRequest",
                exports = "parseUpdateOptOutListResponseInvalidRequest",
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
            return "SmaxUpdateOptOutListResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The transient server-error reply variant.
     *
     * @apiNote
     * {@code WAWebOptOutUserJob} maps this to the {@code (errorCode, errorText, errorKind="server_error")}
     * triple; the caller decides the retry policy.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBlocklistsUpdateOptOutListResponseServerError")
    final class ServerError implements SmaxUpdateOptOutListResponse {
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
         * Invoked from {@link #of(Node, Node)} only after the shared envelope check succeeds.
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
        @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsUpdateOptOutListResponseServerError",
                exports = "parseUpdateOptOutListResponseServerError",
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
            return "SmaxUpdateOptOutListResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
