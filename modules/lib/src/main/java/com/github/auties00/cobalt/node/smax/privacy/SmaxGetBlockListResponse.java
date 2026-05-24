package com.github.auties00.cobalt.node.smax.privacy;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
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
 * The sealed family of inbound replies to a {@link SmaxGetBlockListRequest}.
 *
 * @apiNote
 * Drives the Settings > Privacy > Blocked contacts list refresh; matches the seven response shapes that WA Web's
 * {@code WASmaxBlocklistsGetBlockListRPC.sendGetBlockListRPC} dispatches over: cache-match short-circuit,
 * PN-addressed full list, regular PN-to-LID migration, force migration (with {@code dirty="true"}), Cloud-API
 * fall-through, malformed-request error, and transient server error.
 *
 * @implNote
 * This implementation preserves WA Web's parser priority order in {@link #of(Node, Node)}; the order matters
 * because the migration variants overlap on wire shape and disambiguate only by {@code dirty} and by the
 * per-item {@code jid} requirement.
 */
public sealed interface SmaxGetBlockListResponse extends SmaxOperation.Response
        permits SmaxGetBlockListResponse.SuccessWithMatch,
        SmaxGetBlockListResponse.SuccessWithMismatch,
        SmaxGetBlockListResponse.MigratedSuccessWithMismatch,
        SmaxGetBlockListResponse.ForceMigratedSuccessWithMismatch,
        SmaxGetBlockListResponse.CAPISuccessWithMismatch,
        SmaxGetBlockListResponse.ClientError,
        SmaxGetBlockListResponse.ServerError {

    /**
     * Dispatches the inbound stanza onto the matching variant.
     *
     * @apiNote
     * Called by the SMAX dispatcher in response to a previously-issued {@link SmaxGetBlockListRequest}; an empty
     * {@link Optional} signals that none of the documented WA Web parser arms matched the stanza, which the WA
     * Web RPC reports as {@code SmaxParsingFailure}.
     *
     * @implNote
     * This implementation mirrors WA Web's per-arm priority chain exactly: mismatch variants before the
     * cache-match short-circuit so a forced-migration {@code <list/>} cannot be mistaken for a bare match.
     *
     * @param node    the inbound {@code <iq>} stanza; never {@code null}
     * @param request the original {@link SmaxGetBlockListRequest} stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty when no variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxBlocklistsGetBlockListRPC",
            exports = "sendGetBlockListRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxGetBlockListResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var successWithMismatch = SuccessWithMismatch.of(node, request);
        if (successWithMismatch.isPresent()) {
            return successWithMismatch;
        }
        var migratedSuccessWithMismatch = MigratedSuccessWithMismatch.of(node, request);
        if (migratedSuccessWithMismatch.isPresent()) {
            return migratedSuccessWithMismatch;
        }
        var forceMigratedSuccessWithMismatch = ForceMigratedSuccessWithMismatch.of(node, request);
        if (forceMigratedSuccessWithMismatch.isPresent()) {
            return forceMigratedSuccessWithMismatch;
        }
        var capiSuccessWithMismatch = CAPISuccessWithMismatch.of(node, request);
        if (capiSuccessWithMismatch.isPresent()) {
            return capiSuccessWithMismatch;
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
     * The descriptor for one blocked-user entry in a mismatch-shaped reply.
     *
     * @apiNote
     * Fed into {@code WAWebGetBlocklistJob.fetchBlocklist} which maps each item to a chat-list pill;
     * {@link #jid()} is the addressable identifier (PN on the standard variant, LID on the migrated/CAPI
     * variants), {@link #pnJid()} is the always-PN-form companion when the relay's
     * {@code parseBlocklistIds} disjunction resolves to the {@code PnJid} arm (needed by
     * {@code WAWebDBCreateLidPnMappings.createLidPnMappings} to seed the LID-to-PN cache on the LID-addressed
     * variants), {@link #username()} surfaces the {@code username} attribute when the disjunction resolves to
     * the {@code Username} arm, {@link #displayName()} surfaces the {@code display_name} echo when it resolves
     * to the {@code DisplayName} or {@code GuestNameAndDisplayName} branch, and {@link #active()} reflects
     * whether the relay marked the entry as currently blocked.
     *
     * @implNote
     * This record drops WA Web's {@code country_code} attribute and the {@code guest_name} field because no
     * Cobalt consumer reads them; the rest of the {@code parseBlocklistIds} six-way disjunction is exposed
     * verbatim so that {@code WAWebQueryBlockListJob.fetchAndUpdateBlocklist} and
     * {@code WAWebQueryBlockListJob.learnIdentifiers} can route on the per-arm metadata.
     *
     * @param jid         the addressable JID; PN on the standard variant, LID otherwise; may be {@code null}
     *                    on the sparsely-populated force-migration and Cloud-API variants
     * @param pnJid       the always-PN-form companion JID from the {@code PnJid} arm; may be {@code null}
     * @param username    the {@code username} attribute from the {@code Username} arm; may be {@code null}
     * @param active      whether the relay's {@code active="true"} attribute is present
     * @param displayName the optional display-name echo; may be {@code null}
     */
    record Item(Jid jid, Jid pnJid, String username, boolean active, String displayName) {
        /**
         * Returns the always-PN-form companion JID when present.
         *
         * @apiNote
         * Populated when the relay's disjunction resolves to the {@code PnJid} arm; used by the LID-to-PN
         * mapping cache to learn the pairing of the addressable {@link #jid()} (LID-side on a migrated
         * blocklist) with its PN counterpart.
         *
         * @return an {@link Optional} carrying the PN-form JID, or empty when the relay omitted it
         */
        public Optional<Jid> pnJidAsOptional() {
            return Optional.ofNullable(pnJid);
        }

        /**
         * Returns the username when present.
         *
         * @apiNote
         * Populated when the relay's disjunction resolves to the {@code Username} arm; used by the local
         * contact store to keep username-only contacts addressable.
         *
         * @return an {@link Optional} carrying the username, or empty when the relay omitted it
         */
        public Optional<String> usernameAsOptional() {
            return Optional.ofNullable(username);
        }

        /**
         * Returns the display name when present.
         *
         * @apiNote
         * Use when rendering the blocked-contacts list to fall back to the relay-supplied display name for
         * entries that lack a local contact record.
         *
         * @return an {@link Optional} carrying the display name, or empty when the relay omitted it
         */
        public Optional<String> displayNameAsOptional() {
            return Optional.ofNullable(displayName);
        }
    }

    /**
     * Validates the IQ-result envelope and extracts the inner {@code <list/>} child.
     *
     * @apiNote
     * Shared helper for the {@code SuccessWithMismatch}-shape variants
     * ({@link SuccessWithMismatch}, {@link MigratedSuccessWithMismatch},
     * {@link ForceMigratedSuccessWithMismatch}, {@link CAPISuccessWithMismatch}); the cache-match variant
     * intentionally does not call this helper because it requires the {@code <list/>} child to be absent.
     *
     * @param node    the inbound stanza
     * @param request the original outbound request
     * @return an {@link Optional} carrying the {@code <list/>} child, or empty when the envelope or list is missing
     */
    private static Optional<Node> validateMismatchEnvelope(Node node, Node request) {
        if (!SmaxIqResultResponseMixin.validate(node, request)) {
            return Optional.empty();
        }
        return node.getChild("list");
    }

    /**
     * Parses the {@code <item/>} children of a {@code <list/>} node into a list of {@link Item} descriptors.
     *
     * @apiNote
     * Shared by every mismatch-shape variant; pass {@code requireJid=true} for the standard and regular-migration
     * arms where the per-item JID is mandatory on the wire, and {@code false} for the force-migration and
     * Cloud-API arms where the per-item JID is optional.
     *
     * @implNote
     * This implementation mirrors WA Web's {@code mapChildrenWithTag} fail-the-parent semantics: when the
     * {@code requireJid} contract is set and an {@code <item/>} lacks a {@code jid} attribute, the entire parse
     * aborts so the priority chain in {@link #of(Node, Node)} can fall through to the variant whose per-item JID
     * is optional ({@link CAPISuccessWithMismatch}). The {@code parseBlocklistIdentifierMixin} disjunction is
     * exposed as flat {@code pn_jid}, {@code username}, and {@code display_name} fields; the {@code guest_name}
     * branch and the {@code country_code} attribute are dropped because no consumer reads them.
     *
     * @param list       the {@code <list/>} node
     * @param requireJid whether the per-item {@code jid} attribute is required
     * @return an {@link Optional} carrying the parsed list, or empty when {@code requireJid=true} and an item
     *         lacks a {@code jid}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsGetBlockListResponseSuccessWithMismatch",
            exports = "parseGetBlockListResponseSuccessWithMismatchListItem",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsGetBlockListResponseMigratedSuccessWithMismatch",
            exports = "parseGetBlockListResponseMigratedSuccessWithMismatchListItem",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsGetBlockListResponseForceMigratedSuccessWithMismatch",
            exports = "parseGetBlockListResponseForceMigratedSuccessWithMismatchListItem",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsGetBlockListResponseCAPISuccessWithMismatch",
            exports = "parseGetBlockListResponseCAPISuccessWithMismatchListItem",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsBlocklistIdentifierMixin",
            exports = "parseBlocklistIdentifierMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsBlocklistIds",
            exports = "parseBlocklistIds", adaptation = WhatsAppAdaptation.ADAPTED)
    private static Optional<List<Item>> parseItems(Node list, boolean requireJid) {
        var items = new ArrayList<Item>();
        for (var child : list.getChildren("item")) {
            var jid = child.getAttributeAsString("jid")
                    .map(Jid::of)
                    .orElse(null);
            if (jid == null && requireJid) {
                return Optional.empty();
            }
            var pnJid = child.getAttributeAsString("pn_jid")
                    .map(Jid::of)
                    .orElse(null);
            var username = child.getAttributeAsString("username").orElse(null);
            var active = child.hasAttribute("active", "true");
            var displayName = child.getAttributeAsString("display_name").orElse(null);
            items.add(new Item(jid, pnJid, username, active, displayName));
        }
        return Optional.of(Collections.unmodifiableList(items));
    }

    /**
     * The cache-match short-circuit reply, signalling that the client's cached digest matches the server's.
     *
     * @apiNote
     * {@code WAWebGetBlocklistJob.fetchBlocklist} converts this variant into {@code {type: "match"}} and keeps the
     * local cache as-is, skipping the list re-render.
     *
     * @implNote
     * This implementation rejects on the presence of any {@code <list/>} child to preserve the priority chain
     * in {@link SmaxGetBlockListResponse#of(Node, Node)} where the mismatch variants are tried first.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBlocklistsGetBlockListResponseSuccessWithMatch")
    final class SuccessWithMatch implements SmaxGetBlockListResponse {
        /**
         * Constructs a cache-match reply.
         *
         * @apiNote
         * The variant carries no payload; the constructor exists to satisfy the sealed-interface contract.
         */
        public SuccessWithMatch() {
        }

        /**
         * Parses a cache-match variant.
         *
         * @apiNote
         * Returns empty when the envelope fails the standard IQ-result echo checks or when a {@code <list/>}
         * child is present (which would indicate a mismatch variant that this arm intentionally does not handle).
         *
         * @param node    the inbound stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the variant, or empty when the envelope shape does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsGetBlockListResponseSuccessWithMatch",
                exports = "parseGetBlockListResponseSuccessWithMatch",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<SuccessWithMatch> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            if (node.getChild("list").isPresent()) {
                return Optional.empty();
            }
            return Optional.of(new SuccessWithMatch());
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            return obj != null && obj.getClass() == this.getClass();
        }

        @Override
        public int hashCode() {
            return SuccessWithMatch.class.hashCode();
        }

        @Override
        public String toString() {
            return "SmaxGetBlockListResponse.SuccessWithMatch[]";
        }
    }

    /**
     * The standard PN-addressed full-list reply when the client's cached digest is stale.
     *
     * @apiNote
     * {@code WAWebGetBlocklistJob.fetchBlocklist} unpacks this variant into a chat-list refresh with
     * {@code addressingMode: "pn"}; the items are addressed by their phone-number JID and the relay omits the
     * {@code addressing_mode} attribute (or sets it to {@code "pn"}).
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBlocklistsGetBlockListResponseSuccessWithMismatch")
    final class SuccessWithMismatch implements SmaxGetBlockListResponse {
        /**
         * The new server-side digest of the blocklist when present.
         */
        private final String listDhash;

        /**
         * Whether the relay tagged the list with {@code addressing_mode="pn"} explicitly.
         */
        private final boolean phoneNumberAddressed;

        /**
         * The parsed PN-addressed blocklist entries.
         */
        private final List<Item> listItem;

        /**
         * Constructs a PN-addressed mismatch reply.
         *
         * @apiNote
         * Invoked from {@link #of(Node, Node)} only; the {@code listItem} list is defensively copied to ensure
         * immutability of the response object.
         *
         * @param listDhash            the new server digest; may be {@code null}
         * @param phoneNumberAddressed whether the relay tagged the list as PN-addressed
         * @param listItem             the parsed item list; never {@code null}
         * @throws NullPointerException if {@code listItem} is {@code null}
         */
        public SuccessWithMismatch(String listDhash, boolean phoneNumberAddressed, List<Item> listItem) {
            this.listDhash = listDhash;
            this.phoneNumberAddressed = phoneNumberAddressed;
            this.listItem = List.copyOf(Objects.requireNonNull(listItem, "listItem cannot be null"));
        }

        /**
         * Returns the new server digest when present.
         *
         * @apiNote
         * The caller persists this through {@code WAWebUserPrefsMultiDevice.setBlocklistHash} so the next
         * {@link SmaxGetBlockListRequest} can use it for the cache-match short-circuit.
         *
         * @return an {@link Optional} carrying the digest, or empty when the relay omitted it
         */
        public Optional<String> listDhash() {
            return Optional.ofNullable(listDhash);
        }

        /**
         * Returns whether the relay marked the list as PN-addressed.
         *
         * @apiNote
         * Distinguishes a relay-tagged PN list from one where the {@code addressing_mode} attribute was omitted
         * entirely. Both are treated as PN by {@code WAWebGetBlocklistJob}, but consumers needing the wire
         * distinction can read this flag.
         *
         * @return {@code true} when the relay set {@code addressing_mode="pn"} explicitly
         */
        public boolean phoneNumberAddressed() {
            return phoneNumberAddressed;
        }

        /**
         * Returns the parsed blocklist entries.
         *
         * @apiNote
         * Each entry's {@link Item#jid()} is a PN-addressed user JID; consumers route the list through
         * {@code WAWebJidToWid.userJidToUserWid} before rendering.
         *
         * @return an unmodifiable list of items; never {@code null}
         */
        public List<Item> listItem() {
            return listItem;
        }

        /**
         * Parses a PN-addressed mismatch variant.
         *
         * @apiNote
         * Returns empty when the envelope is wrong, when {@code addressing_mode} is set to anything other than
         * {@code "pn"}, or when any per-item JID is missing (the per-item JID is mandatory on this variant).
         *
         * @param node    the inbound stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the variant, or empty when the envelope shape does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsGetBlockListResponseSuccessWithMismatch",
                exports = "parseGetBlockListResponseSuccessWithMismatch",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<SuccessWithMismatch> of(Node node, Node request) {
            var list = validateMismatchEnvelope(node, request).orElse(null);
            if (list == null) {
                return Optional.empty();
            }
            var addressingMode = list.getAttributeAsString("addressing_mode").orElse(null);
            if (addressingMode != null && !addressingMode.equals("pn")) {
                return Optional.empty();
            }
            var dhash = list.getAttributeAsString("dhash").orElse(null);
            var items = parseItems(list, true).orElse(null);
            if (items == null) {
                return Optional.empty();
            }
            return Optional.of(new SuccessWithMismatch(dhash, addressingMode != null, items));
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
            return this.phoneNumberAddressed == that.phoneNumberAddressed
                    && Objects.equals(this.listDhash, that.listDhash)
                    && Objects.equals(this.listItem, that.listItem);
        }

        @Override
        public int hashCode() {
            return Objects.hash(listDhash, phoneNumberAddressed, listItem);
        }

        @Override
        public String toString() {
            return "SmaxGetBlockListResponse.SuccessWithMismatch[listDhash=" + listDhash
                    + ", phoneNumberAddressed=" + phoneNumberAddressed
                    + ", listItem=" + listItem + ']';
        }
    }

    /**
     * The regular PN-to-LID migration reply, returned after the relay has migrated the user's blocklist to LID.
     *
     * @apiNote
     * {@code WAWebGetBlocklistJob.fetchBlocklist} routes this variant through the LID-aware contact-resolution
     * path, mapping each LID-addressed entry to its {@code lid} wid and surfacing it under
     * {@code addressingMode: "lid"} with {@code dirty: false}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBlocklistsGetBlockListResponseMigratedSuccessWithMismatch")
    final class MigratedSuccessWithMismatch implements SmaxGetBlockListResponse {
        /**
         * The new server-side digest when present.
         */
        private final String listDhash;

        /**
         * The parsed LID-addressed blocklist entries.
         */
        private final List<Item> listItem;

        /**
         * Constructs a migrated mismatch reply.
         *
         * @apiNote
         * Invoked from {@link #of(Node, Node)} only; the {@code listItem} list is defensively copied for
         * immutability.
         *
         * @param listDhash the new server digest; may be {@code null}
         * @param listItem  the parsed item list; never {@code null}
         * @throws NullPointerException if {@code listItem} is {@code null}
         */
        public MigratedSuccessWithMismatch(String listDhash, List<Item> listItem) {
            this.listDhash = listDhash;
            this.listItem = List.copyOf(Objects.requireNonNull(listItem, "listItem cannot be null"));
        }

        /**
         * Returns the new server digest when present.
         *
         * @apiNote
         * Stored alongside the resulting LID-addressed list for use in the next cache-match request.
         *
         * @return an {@link Optional} carrying the digest, or empty when the relay omitted it
         */
        public Optional<String> listDhash() {
            return Optional.ofNullable(listDhash);
        }

        /**
         * Returns the parsed blocklist entries.
         *
         * @apiNote
         * Each {@link Item#jid()} is a LID JID; consumers translate via {@code WAWebJidToWid.lidUserJidToUserLid}
         * before rendering.
         *
         * @return an unmodifiable list of items; never {@code null}
         */
        public List<Item> listItem() {
            return listItem;
        }

        /**
         * Parses a regular-migration variant.
         *
         * @apiNote
         * Returns empty when the envelope is wrong, when {@code addressing_mode} is not {@code "lid"}, or when
         * any per-item JID is missing. WA Web's migrated parser also does not check for {@code dirty="true"} so
         * Cobalt mirrors that exactly; the {@code dirty} marker is the sole discriminator between this variant
         * and {@link ForceMigratedSuccessWithMismatch}.
         *
         * @param node    the inbound stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the variant, or empty when the envelope shape does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsGetBlockListResponseMigratedSuccessWithMismatch",
                exports = "parseGetBlockListResponseMigratedSuccessWithMismatch",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<MigratedSuccessWithMismatch> of(Node node, Node request) {
            var list = validateMismatchEnvelope(node, request).orElse(null);
            if (list == null) {
                return Optional.empty();
            }
            if (!list.hasAttribute("addressing_mode", "lid")) {
                return Optional.empty();
            }
            var dhash = list.getAttributeAsString("dhash").orElse(null);
            var items = parseItems(list, true).orElse(null);
            if (items == null) {
                return Optional.empty();
            }
            return Optional.of(new MigratedSuccessWithMismatch(dhash, items));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (MigratedSuccessWithMismatch) obj;
            return Objects.equals(this.listDhash, that.listDhash)
                    && Objects.equals(this.listItem, that.listItem);
        }

        @Override
        public int hashCode() {
            return Objects.hash(listDhash, listItem);
        }

        @Override
        public String toString() {
            return "SmaxGetBlockListResponse.MigratedSuccessWithMismatch[listDhash=" + listDhash
                    + ", listItem=" + listItem + ']';
        }
    }

    /**
     * The force-migration reply, returned when the relay aggressively converts a stale PN blocklist to LID.
     *
     * @apiNote
     * {@code WAWebGetBlocklistJob.fetchBlocklist} treats the {@code dirty="true"} marker as a cue to log the
     * forced migration and to expand sparsely-populated entries through {@code WAWebLidMigrationUtils.toLid}
     * before rendering them. Surfaced under {@code addressingMode: "lid"} with {@code dirty: true}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBlocklistsGetBlockListResponseForceMigratedSuccessWithMismatch")
    final class ForceMigratedSuccessWithMismatch implements SmaxGetBlockListResponse {
        /**
         * The new server-side digest when present.
         */
        private final String listDhash;

        /**
         * The parsed LID-addressed blocklist entries; may contain items with missing JIDs.
         */
        private final List<Item> listItem;

        /**
         * Constructs a force-migration reply.
         *
         * @apiNote
         * Invoked from {@link #of(Node, Node)} only; the {@code listItem} list is defensively copied for
         * immutability.
         *
         * @param listDhash the new server digest; may be {@code null}
         * @param listItem  the parsed item list; never {@code null}
         * @throws NullPointerException if {@code listItem} is {@code null}
         */
        public ForceMigratedSuccessWithMismatch(String listDhash, List<Item> listItem) {
            this.listDhash = listDhash;
            this.listItem = List.copyOf(Objects.requireNonNull(listItem, "listItem cannot be null"));
        }

        /**
         * Returns the new server digest when present.
         *
         * @apiNote
         * Stored alongside the resulting LID list so the next request can use the cache-match short-circuit.
         *
         * @return an {@link Optional} carrying the digest, or empty when the relay omitted it
         */
        public Optional<String> listDhash() {
            return Optional.ofNullable(listDhash);
        }

        /**
         * Returns the parsed blocklist entries.
         *
         * @apiNote
         * Some entries may have a {@code null} JID; the WA Web consumer falls back to
         * {@code WAWebLidMigrationUtils.toLid} on the entry's {@code pn} discriminator to recover the LID.
         *
         * @return an unmodifiable list of items; never {@code null}
         */
        public List<Item> listItem() {
            return listItem;
        }

        /**
         * Parses a force-migration variant.
         *
         * @apiNote
         * Returns empty when the envelope is wrong, when {@code addressing_mode} is not {@code "lid"}, or when
         * the {@code dirty="true"} marker is absent. The per-item JID is optional on this variant, so
         * {@link #parseItems(Node, boolean)} is called with {@code requireJid=false}.
         *
         * @param node    the inbound stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the variant, or empty when the envelope shape does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsGetBlockListResponseForceMigratedSuccessWithMismatch",
                exports = "parseGetBlockListResponseForceMigratedSuccessWithMismatch",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ForceMigratedSuccessWithMismatch> of(Node node, Node request) {
            var list = validateMismatchEnvelope(node, request).orElse(null);
            if (list == null) {
                return Optional.empty();
            }
            if (!list.hasAttribute("addressing_mode", "lid")) {
                return Optional.empty();
            }
            if (!list.hasAttribute("dirty", "true")) {
                return Optional.empty();
            }
            var dhash = list.getAttributeAsString("dhash").orElse(null);
            var items = parseItems(list, false).orElseThrow();
            return Optional.of(new ForceMigratedSuccessWithMismatch(dhash, items));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ForceMigratedSuccessWithMismatch) obj;
            return Objects.equals(this.listDhash, that.listDhash)
                    && Objects.equals(this.listItem, that.listItem);
        }

        @Override
        public int hashCode() {
            return Objects.hash(listDhash, listItem);
        }

        @Override
        public String toString() {
            return "SmaxGetBlockListResponse.ForceMigratedSuccessWithMismatch[listDhash=" + listDhash
                    + ", listItem=" + listItem + ']';
        }
    }

    /**
     * The Cloud-API mismatch reply, returned by the cloud-relay flavour rather than the regular relay.
     *
     * @apiNote
     * {@code WAWebGetBlocklistJob.fetchBlocklist} logs this as an unexpected response (it indicates the user has
     * been routed through the Cloud API path) and folds it back into the standard LID rendering with
     * {@code dirty: false}.
     *
     * @implNote
     * This implementation is the fall-through arm of the LID-addressed parser priority chain: a LID stanza whose
     * items all carry a {@code jid} resolves to {@link MigratedSuccessWithMismatch}, a stanza marked
     * {@code dirty="true"} resolves to {@link ForceMigratedSuccessWithMismatch}, and any other LID stanza falls
     * through to this variant whose per-item JID is optional.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBlocklistsGetBlockListResponseCAPISuccessWithMismatch")
    final class CAPISuccessWithMismatch implements SmaxGetBlockListResponse {
        /**
         * The new server-side digest when present.
         */
        private final String listDhash;

        /**
         * The parsed blocklist entries; may contain items with missing JIDs.
         */
        private final List<Item> listItem;

        /**
         * Constructs a Cloud-API mismatch reply.
         *
         * @apiNote
         * Invoked from {@link #of(Node, Node)} only; the {@code listItem} list is defensively copied for
         * immutability.
         *
         * @param listDhash the new server digest; may be {@code null}
         * @param listItem  the parsed item list; never {@code null}
         * @throws NullPointerException if {@code listItem} is {@code null}
         */
        public CAPISuccessWithMismatch(String listDhash, List<Item> listItem) {
            this.listDhash = listDhash;
            this.listItem = List.copyOf(Objects.requireNonNull(listItem, "listItem cannot be null"));
        }

        /**
         * Returns the new server digest when present.
         *
         * @return an {@link Optional} carrying the digest, or empty when the relay omitted it
         */
        public Optional<String> listDhash() {
            return Optional.ofNullable(listDhash);
        }

        /**
         * Returns the parsed blocklist entries.
         *
         * @apiNote
         * The list is processed by the same sparse-entry recovery path as
         * {@link ForceMigratedSuccessWithMismatch#listItem()}.
         *
         * @return an unmodifiable list of items; never {@code null}
         */
        public List<Item> listItem() {
            return listItem;
        }

        /**
         * Parses a Cloud-API mismatch variant.
         *
         * @apiNote
         * Returns empty when the envelope is wrong or when {@code addressing_mode} is not {@code "lid"}. The
         * per-item JID is optional, so the parse never fails on missing JIDs.
         *
         * @param node    the inbound stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the variant, or empty when the envelope shape does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsGetBlockListResponseCAPISuccessWithMismatch",
                exports = "parseGetBlockListResponseCAPISuccessWithMismatch",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<CAPISuccessWithMismatch> of(Node node, Node request) {
            var list = validateMismatchEnvelope(node, request).orElse(null);
            if (list == null) {
                return Optional.empty();
            }
            if (!list.hasAttribute("addressing_mode", "lid")) {
                return Optional.empty();
            }
            var dhash = list.getAttributeAsString("dhash").orElse(null);
            var items = parseItems(list, false).orElseThrow();
            return Optional.of(new CAPISuccessWithMismatch(dhash, items));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (CAPISuccessWithMismatch) obj;
            return Objects.equals(this.listDhash, that.listDhash)
                    && Objects.equals(this.listItem, that.listItem);
        }

        @Override
        public int hashCode() {
            return Objects.hash(listDhash, listItem);
        }

        @Override
        public String toString() {
            return "SmaxGetBlockListResponse.CAPISuccessWithMismatch[listDhash=" + listDhash
                    + ", listItem=" + listItem + ']';
        }
    }

    /**
     * The malformed-request reply variant.
     *
     * @apiNote
     * {@code WAWebGetBlocklistJob.fetchBlocklist} maps this to the {@code {errorCode, errorText}} pair surfaced
     * to the user as a fetch-failure log line; no Cobalt-side retry policy.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBlocklistsGetBlockListResponseInvalidRequest")
    final class ClientError implements SmaxGetBlockListResponse {
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
         * Delegates the envelope check to {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)} so the
         * shared client-error parsing logic is exercised consistently across SMAX replies.
         *
         * @param node    the inbound stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the variant, or empty when the envelope shape does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsGetBlockListResponseInvalidRequest",
                exports = "parseGetBlockListResponseInvalidRequest",
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
            return "SmaxGetBlockListResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The transient server-error reply variant.
     *
     * @apiNote
     * {@code WAWebGetBlocklistJob.fetchBlocklist} logs the {@code (errorCode, errorText)} pair and bubbles it
     * back to the caller; WA Web does not retry inline, leaving recovery to the caller.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBlocklistsGetBlockListResponseInternalServerError")
    final class ServerError implements SmaxGetBlockListResponse {
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
        @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsGetBlockListResponseInternalServerError",
                exports = "parseGetBlockListResponseInternalServerError",
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
            return "SmaxGetBlockListResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
