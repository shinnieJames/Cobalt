package com.github.auties00.cobalt.node.smax.privacy;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;

import java.util.Objects;
import java.util.Optional;

/**
 * The sealed family discriminating an opt-out list entry by either a marketing brand id or a business user JID.
 *
 * @apiNote
 * Drives the marketing-messages opt-out list surface exposed by {@link SmaxGetOptOutListResponse.Item#bizOptOutIds()}.
 * The {@link BrandId} arm is consumed by {@code WAWebGetOptOutList.getOptOutList} to expand a brand id into the
 * full set of business numbers via {@code WAWebGetNumbersForBrandIdsJob}, while the {@link UserJid} arm carries a
 * direct business JID that is rendered as a chat-list pill without further resolution.
 *
 * @implNote
 * This implementation collapses the wire tagged-union ({@code name} discriminator of {@code "BizOptOutBrandID"} or
 * {@code "BizOptOutJid"}) into a sealed interface whose variant type is the discriminator. Pattern matching on
 * {@link BrandId} versus {@link UserJid} replaces the WA Web {@code bizOptOutIds.name === "..."} branch.
 */
@WhatsAppWebModule(moduleName = "WASmaxInBlocklistsBizOptOutIds")
public sealed interface BizOptOutId permits BizOptOutId.BrandId, BizOptOutId.UserJid {
    /**
     * The {@code BizOptOutBrandID} arm of the disjunction, carrying a marketing-brand id with an optional business JID.
     *
     * @apiNote
     * Produced by {@link SmaxBizOptOutBrandIdMixin#parse(Node)} when the {@code <item>} carries a
     * {@code biz_opt_out_brand_id} attribute. {@code WAWebGetOptOutList.getOptOutList} collects the brand ids
     * into a batch and resolves them to business phone numbers and LIDs through
     * {@code WAWebGetNumbersForBrandIdsJob.getNumbersForBrandIdsJob}.
     *
     * @param bizOptOutBrandId the marketing brand identifier; never {@code null}
     * @param bizJid           the optional business user JID echoed by the relay; may be {@code null}
     */
    record BrandId(String bizOptOutBrandId, Jid bizJid) implements BizOptOutId {
        /**
         * Validates the {@link BrandId} payload.
         *
         * @apiNote
         * The compact constructor enforces the wire-level mandatory-versus-optional split before exposing the
         * value to callers of {@link SmaxGetOptOutListResponse.Item#bizOptOutIds()}.
         *
         * @param bizOptOutBrandId the brand id; never {@code null}
         * @param bizJid           the optional business JID; may be {@code null}
         * @throws NullPointerException if {@code bizOptOutBrandId} is {@code null}
         */
        public BrandId {
            Objects.requireNonNull(bizOptOutBrandId, "bizOptOutBrandId cannot be null");
        }

        /**
         * Returns the business JID when present.
         *
         * @apiNote
         * Use to detect whether the relay paired the brand id with a concrete business JID; absence means the
         * caller must run brand-id expansion before contacting the business.
         *
         * @return an {@link Optional} carrying the JID, or empty when the relay omitted {@code biz_jid}
         */
        public Optional<Jid> bizJidAsOptional() {
            return Optional.ofNullable(bizJid);
        }
    }

    /**
     * The {@code BizOptOutJid} arm of the disjunction, carrying a concrete business user JID.
     *
     * @apiNote
     * Produced by {@link SmaxBizOptOutJidMixin#parse(Node)} when the {@code <item>} carries a
     * {@code biz_opt_out_jid} attribute. Used directly by {@code WAWebGetOptOutList.getOptOutList} to push a
     * chat-list pill keyed on the resolved {@code wid} without any further brand-id lookup.
     *
     * @param bizOptOutJid the business user JID; never {@code null}
     */
    record UserJid(Jid bizOptOutJid) implements BizOptOutId {
        /**
         * Validates the {@link UserJid} payload.
         *
         * @apiNote
         * The compact constructor rejects a missing JID up front; the wire-level parser already validates the
         * attribute against {@link Jid#hasUserServer()} before construction.
         *
         * @param bizOptOutJid the business user JID; never {@code null}
         * @throws NullPointerException if {@code bizOptOutJid} is {@code null}
         */
        public UserJid {
            Objects.requireNonNull(bizOptOutJid, "bizOptOutJid cannot be null");
        }
    }

    /**
     * Projects an {@code <item>} node onto a {@link BizOptOutId} variant.
     *
     * @apiNote
     * Called by {@link SmaxGetOptOutListResponse#parseItem(Node)} and any direct caller decoding the
     * {@code biz_opt_out_ids} disjunction from an opt-out list response. Returns {@link Optional#empty()} when
     * neither arm matches; callers then reject the enclosing item to preserve WA Web's
     * {@code mapChildrenWithTag} fail-the-parent semantics.
     *
     * @implNote
     * This implementation tries {@link SmaxBizOptOutBrandIdMixin#parse(Node)} first, mirroring the WA Web
     * priority order, and falls back to {@link SmaxBizOptOutJidMixin#parse(Node)}. The
     * {@code errorMixinDisjunction} envelope WA Web returns on no-match collapses to an empty {@link Optional}
     * since no caller introspects the disjunction-failure detail.
     *
     * @param item the source {@code <item>} node; never {@code null}
     * @return an {@link Optional} carrying the projected variant, or empty when neither arm parses
     * @throws NullPointerException if {@code item} is {@code null}
     * @see SmaxBizOptOutBrandIdMixin#parse(Node)
     * @see SmaxBizOptOutJidMixin#parse(Node)
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsBizOptOutIds",
            exports = "parseBizOptOutIds", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<BizOptOutId> parse(Node item) {
        Objects.requireNonNull(item, "item cannot be null");
        var brand = SmaxBizOptOutBrandIdMixin.parse(item);
        if (brand.isPresent()) {
            var p = brand.get();
            return Optional.of(new BrandId(p.bizOptOutBrandId(), p.bizJid()));
        }
        var jid = SmaxBizOptOutJidMixin.parse(item);
        if (jid.isPresent()) {
            return Optional.of(new UserJid(jid.get().bizOptOutJid()));
        }
        return Optional.empty();
    }
}
