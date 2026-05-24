package com.github.auties00.cobalt.node.smax.groups;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <iq type="get" xmlns="w:g2">} stanza that fetches profile pictures for one or more
 * groups (parent or sub-group) in a single round trip.
 *
 * @apiNote
 * Drives the group-pictures fetch surface backed by {@code WAWebGroupGetProfilePicsJob}; pair with
 * {@link SmaxGroupsGetGroupProfilePicturesResponse} to read the per-group results. Build one
 * {@link PictureRequest} per group whose picture is needed; the relay batches up to {@code 1000} entries in
 * a single envelope.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsGetGroupProfilePicturesRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsGetGroupProfilePicturesProfilePicturesRequestMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseGetGroupOrServerMixinGroup")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseGetGroupMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseGetServerMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsParentOrSubGroupMixinGroup")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsParentGroupMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsSubGroupMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsProfilePictureIdMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsProfilePictureTypeMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsProfilePictureQueryMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsSubGroupHintMixin")
public final class SmaxGroupsGetGroupProfilePicturesRequest implements SmaxOperation.Request {
    /**
     * The optional addressing override stamped on the IQ envelope's {@code to} attribute; {@code null}
     * routes the IQ to the implicit {@code g.us} server.
     */
    private final Jid baseGroupJid;

    /**
     * The optional {@link Jid} stamped on the {@code <pictures linked_groups_membership_hint="...">}
     * attribute; carries the caller's linked-community hint.
     */
    private final Jid linkedGroupsMembershipHint;

    /**
     * The per-picture sub-requests, one per group whose picture is being fetched.
     */
    private final List<PictureRequest> pictures;

    /**
     * Constructs a request batching one or more picture sub-requests.
     *
     * @apiNote
     * Set {@code baseGroupJid} when the IQ must be routed to a specific group (most consumers route to
     * {@code g.us} and identify the group inside each {@link PictureRequest}); set
     * {@code linkedGroupsMembershipHint} to forward the caller's community-membership hint to the relay.
     *
     * @param baseGroupJid               optional IQ-{@code to} group override; {@code null} routes to
     *                                   {@code g.us}
     * @param linkedGroupsMembershipHint optional linked-community hint; may be {@code null}
     * @param pictures                   the per-picture requests; never {@code null}, never empty
     * @throws NullPointerException     if {@code pictures} is {@code null}
     * @throws IllegalArgumentException if {@code pictures} is empty
     */
    public SmaxGroupsGetGroupProfilePicturesRequest(Jid baseGroupJid, Jid linkedGroupsMembershipHint, List<PictureRequest> pictures) {
        Objects.requireNonNull(pictures, "pictures cannot be null");
        if (pictures.isEmpty()) {
            throw new IllegalArgumentException("pictures cannot be empty");
        }
        this.baseGroupJid = baseGroupJid;
        this.linkedGroupsMembershipHint = linkedGroupsMembershipHint;
        this.pictures = List.copyOf(pictures);
    }

    /**
     * Returns the optional IQ-{@code to} group override.
     *
     * @return an {@link Optional} carrying the group {@link Jid}, or empty when the IQ is routed to
     *         {@code g.us}
     */
    public Optional<Jid> baseGroupJid() {
        return Optional.ofNullable(baseGroupJid);
    }

    /**
     * Returns the optional linked-community membership hint.
     *
     * @return an {@link Optional} carrying the hint {@link Jid}, or empty when the caller did not supply one
     */
    public Optional<Jid> linkedGroupsMembershipHint() {
        return Optional.ofNullable(linkedGroupsMembershipHint);
    }

    /**
     * Returns the per-picture requests.
     *
     * @return an unmodifiable list of picture requests; never {@code null}
     */
    public List<PictureRequest> pictures() {
        return pictures;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation builds one {@code <picture/>} child per {@link PictureRequest} (each with its own
     * addressing attribute and optional hints), wraps them in a {@code <pictures/>} container, stamps the
     * optional {@code linked_groups_membership_hint} on that container, then wraps the result in the
     * canonical {@code <iq xmlns="w:g2" type="get">} envelope. The IQ's {@code to} attribute is the
     * {@link #baseGroupJid()} when supplied, otherwise the implicit {@code g.us} server.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutGroupsGetGroupProfilePicturesRequest",
            exports = "makeGetGroupProfilePicturesRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var pictureNodes = new ArrayList<Node>(pictures.size());
        for (var pictureRequest : pictures) {
            var pictureBuilder = new NodeBuilder()
                    .description("picture");
            var parentGroupJid = pictureRequest.parentGroupJid().orElse(null);
            if (parentGroupJid != null) {
                pictureBuilder.attribute("parent_group_jid", parentGroupJid);
            }
            var subGroupJid = pictureRequest.subGroupJid().orElse(null);
            if (subGroupJid != null) {
                pictureBuilder.attribute("sub_group_jid", subGroupJid);
            }
            var pictureId = pictureRequest.pictureId().orElse(null);
            if (pictureId != null) {
                pictureBuilder.attribute("id", pictureId);
            }
            var pictureType = pictureRequest.pictureType().orElse(null);
            if (pictureType != null) {
                pictureBuilder.attribute("type", pictureType);
            }
            var pictureQuery = pictureRequest.pictureQuery().orElse(null);
            if (pictureQuery != null) {
                pictureBuilder.attribute("query", pictureQuery);
            }
            pictureNodes.add(pictureBuilder.build());
        }
        var picturesBuilder = new NodeBuilder()
                .description("pictures")
                .content(pictureNodes);
        if (linkedGroupsMembershipHint != null) {
            picturesBuilder.attribute("linked_groups_membership_hint", linkedGroupsMembershipHint);
        }
        var iqBuilder = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2")
                .attribute("type", "get");
        if (baseGroupJid != null) {
            iqBuilder.attribute("to", baseGroupJid);
        } else {
            iqBuilder.attribute("to", JidServer.groupOrCommunity());
        }
        iqBuilder.content(picturesBuilder.build());
        return iqBuilder;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxGroupsGetGroupProfilePicturesRequest) obj;
        return Objects.equals(this.baseGroupJid, that.baseGroupJid)
                && Objects.equals(this.linkedGroupsMembershipHint, that.linkedGroupsMembershipHint)
                && Objects.equals(this.pictures, that.pictures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseGroupJid, linkedGroupsMembershipHint, pictures);
    }

    @Override
    public String toString() {
        return "SmaxGroupsGetGroupProfilePicturesRequest[baseGroupJid=" + baseGroupJid
                + ", linkedGroupsMembershipHint=" + linkedGroupsMembershipHint
                + ", pictures=" + pictures + ']';
    }

    /**
     * Per-group picture sub-payload carried inside a {@link SmaxGroupsGetGroupProfilePicturesRequest}.
     *
     * @apiNote
     * Each entry must carry exactly one of {@link #parentGroupJid()} or {@link #subGroupJid()} as the
     * addressing attribute; the optional {@link #pictureId()} lets the relay return the
     * {@code did_not_change} marker when the caller's cache is still current. {@link #pictureType()} selects
     * resolution ({@code "image"} or {@code "preview"}); {@link #pictureQuery()} selects the projection mode
     * ({@code "url"} for URL plus {@code direct_path}, {@code "blob"} for inline bytes).
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutGroupsGetGroupProfilePicturesProfilePicturesRequestMixin")
    public static final class PictureRequest {
        /**
         * The parent-group {@link Jid} stamped on the {@code <picture parent_group_jid="...">} attribute;
         * mutually exclusive with {@link #subGroupJid}.
         */
        private final Jid parentGroupJid;

        /**
         * The sub-group {@link Jid} stamped on the {@code <picture sub_group_jid="...">} attribute; mutually
         * exclusive with {@link #parentGroupJid}.
         */
        private final Jid subGroupJid;

        /**
         * The optional dehydration-hint id of a previously-known picture; when supplied the relay returns
         * the {@code did_not_change} marker instead of re-shipping unchanged bytes.
         */
        private final String pictureId;

        /**
         * The optional picture type ({@code "image"} or {@code "preview"}) selecting the resolution variant.
         */
        private final String pictureType;

        /**
         * The optional projection-mode hint ({@code "url"} for a URL plus {@code direct_path}, {@code "blob"}
         * for inline bytes).
         */
        private final String pictureQuery;

        /**
         * Constructs a per-group picture sub-request.
         *
         * @apiNote
         * Exactly one of {@code parentGroupJid} or {@code subGroupJid} must be supplied; the constructor
         * validates the disjunction.
         *
         * @param parentGroupJid optional parent-group {@link Jid}
         * @param subGroupJid    optional sub-group {@link Jid}
         * @param pictureId      optional dehydration-hint picture id
         * @param pictureType    optional picture type
         * @param pictureQuery   optional projection mode
         * @throws IllegalArgumentException if both {@code parentGroupJid} and {@code subGroupJid} are
         *                                  supplied or both are {@code null}
         */
        public PictureRequest(Jid parentGroupJid, Jid subGroupJid,
                              String pictureId, String pictureType, String pictureQuery) {
            if (parentGroupJid == null && subGroupJid == null) {
                throw new IllegalArgumentException(
                        "either parentGroupJid or subGroupJid must be supplied");
            }
            if (parentGroupJid != null && subGroupJid != null) {
                throw new IllegalArgumentException(
                        "parentGroupJid and subGroupJid are mutually exclusive");
            }
            this.parentGroupJid = parentGroupJid;
            this.subGroupJid = subGroupJid;
            this.pictureId = pictureId;
            this.pictureType = pictureType;
            this.pictureQuery = pictureQuery;
        }

        /**
         * Returns the parent-group {@link Jid} when this request targets a parent group.
         *
         * @return an {@link Optional} carrying the parent-group JID, or empty when the entry addresses a
         *         sub-group instead
         */
        public Optional<Jid> parentGroupJid() {
            return Optional.ofNullable(parentGroupJid);
        }

        /**
         * Returns the sub-group {@link Jid} when this request targets a sub-group.
         *
         * @return an {@link Optional} carrying the sub-group JID, or empty when the entry addresses a parent
         *         group instead
         */
        public Optional<Jid> subGroupJid() {
            return Optional.ofNullable(subGroupJid);
        }

        /**
         * Returns the optional dehydration-hint id.
         *
         * @apiNote
         * Empty means the relay should always ship bytes; a non-empty value lets the relay return the
         * {@code did_not_change} marker when the cached picture is still current.
         *
         * @return an {@link Optional} carrying the picture id
         */
        public Optional<String> pictureId() {
            return Optional.ofNullable(pictureId);
        }

        /**
         * Returns the optional picture type.
         *
         * @apiNote
         * {@code "image"} selects the full-resolution variant; {@code "preview"} selects the low-resolution
         * variant.
         *
         * @return an {@link Optional} carrying the picture type
         */
        public Optional<String> pictureType() {
            return Optional.ofNullable(pictureType);
        }

        /**
         * Returns the optional projection mode.
         *
         * @apiNote
         * {@code "url"} returns a CDN URL plus {@code direct_path}; {@code "blob"} returns the picture bytes
         * inline.
         *
         * @return an {@link Optional} carrying the projection mode
         */
        public Optional<String> pictureQuery() {
            return Optional.ofNullable(pictureQuery);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (PictureRequest) obj;
            return Objects.equals(this.parentGroupJid, that.parentGroupJid)
                    && Objects.equals(this.subGroupJid, that.subGroupJid)
                    && Objects.equals(this.pictureId, that.pictureId)
                    && Objects.equals(this.pictureType, that.pictureType)
                    && Objects.equals(this.pictureQuery, that.pictureQuery);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parentGroupJid, subGroupJid, pictureId, pictureType, pictureQuery);
        }

        @Override
        public String toString() {
            return "SmaxGroupsGetGroupProfilePicturesRequest.PictureRequest[parentGroupJid="
                    + parentGroupJid + ", subGroupJid=" + subGroupJid
                    + ", pictureId=" + pictureId + ", pictureType=" + pictureType
                    + ", pictureQuery=" + pictureQuery + ']';
        }
    }
}
