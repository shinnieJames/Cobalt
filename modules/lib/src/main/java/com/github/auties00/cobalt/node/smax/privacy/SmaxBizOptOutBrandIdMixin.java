package com.github.auties00.cobalt.node.smax.privacy;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;

import java.util.Objects;
import java.util.Optional;

/**
 * The wire-level parser for the {@code BizOptOutBrandID} arm of an opt-out item's {@code biz_opt_out_ids} field.
 *
 * @apiNote
 * Invoked from {@link BizOptOutId#parse(Node)} as the first arm of the brand-id-versus-jid disjunction; reads the
 * {@code biz_opt_out_brand_id} string identifying a marketing brand and the optional {@code biz_jid} echo of the
 * business JID paired with that brand. The resulting pair feeds back into the
 * {@code WAWebGetOptOutList.getOptOutList} brand-id expansion path.
 *
 * @implNote
 * This implementation collapses WA Web's {@code WAResultOrError} envelope to a plain {@link Optional}: an empty
 * value covers both the required-attribute-missing case and the optional-jid-fails-user-validation case, since no
 * caller introspects the failure shape further.
 */
@WhatsAppWebModule(moduleName = "WASmaxInBlocklistsBizOptOutBrandIDMixin")
public final class SmaxBizOptOutBrandIdMixin {
    /**
     * The unreachable utility-class constructor.
     *
     * @apiNote
     * The class only exposes static parsing helpers; instantiation is forbidden to keep the call site obvious.
     *
     * @throws AssertionError on every invocation
     */
    private SmaxBizOptOutBrandIdMixin() {
        throw new AssertionError("SmaxBizOptOutBrandIdMixin cannot be instantiated");
    }

    /**
     * The decoded {@code BizOptOutBrandID} pair of brand id and optional business JID.
     *
     * @apiNote
     * Returned by {@link SmaxBizOptOutBrandIdMixin#parse(Node)} when both attributes parse cleanly. Consumed by
     * {@link BizOptOutId#parse(Node)} to lift the pair into the {@link BizOptOutId.BrandId} variant.
     *
     * @param bizOptOutBrandId the marketing-brand identifier; never {@code null}
     * @param bizJid           the optional paired business JID; may be {@code null}
     */
    public record Projection(String bizOptOutBrandId, Jid bizJid) {
        /**
         * Validates the projection payload.
         *
         * @apiNote
         * The compact constructor enforces the wire-level mandatory split; the optional JID arrives pre-validated
         * by the calling parser.
         *
         * @param bizOptOutBrandId the brand identifier; never {@code null}
         * @param bizJid           the optional business JID; may be {@code null}
         * @throws NullPointerException if {@code bizOptOutBrandId} is {@code null}
         */
        public Projection {
            Objects.requireNonNull(bizOptOutBrandId, "bizOptOutBrandId cannot be null");
        }

        /**
         * Returns the business JID when present.
         *
         * @apiNote
         * Use to detect whether the relay echoed a business JID alongside the brand id; consumers without an
         * {@code Optional} preference may read the raw field via the record accessor instead.
         *
         * @return an {@link Optional} carrying the JID, or empty when the relay omitted {@code biz_jid}
         */
        public Optional<Jid> bizJidAsOptional() {
            return Optional.ofNullable(bizJid);
        }
    }

    /**
     * Parses an {@code <item>} node as a {@code BizOptOutBrandID} projection.
     *
     * @apiNote
     * Returns {@link Optional#empty()} when the required {@code biz_opt_out_brand_id} attribute is missing or when
     * an optional {@code biz_jid} is present but is not a user JID (no user server, as enforced by
     * {@link Jid#hasUserServer()}). Callers in the {@code biz_opt_out_ids} disjunction fall through to
     * {@link SmaxBizOptOutJidMixin#parse(Node)} on empty.
     *
     * @implNote
     * This implementation mirrors WA Web's {@code attrUserJid} validator inline: a present-but-invalid JID rejects
     * the whole projection rather than degrading silently, which is load-bearing for the
     * {@link BizOptOutId#parse(Node)} priority chain.
     *
     * @param item the source {@code <item>} node; never {@code null}
     * @return an {@link Optional} carrying the projection, or empty when the schema does not match
     * @throws NullPointerException if {@code item} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsBizOptOutBrandIDMixin",
            exports = "parseBizOptOutBrandIDMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<Projection> parse(Node item) {
        Objects.requireNonNull(item, "item cannot be null");
        var brandId = item.getAttributeAsString("biz_opt_out_brand_id").orElse(null);
        if (brandId == null) {
            return Optional.empty();
        }
        Jid bizJid = null;
        if (item.hasAttribute("biz_jid")) {
            var parsed = item.getAttributeAsJid("biz_jid").orElse(null);
            if (parsed == null || !parsed.hasUserServer()) {
                return Optional.empty();
            }
            bizJid = parsed;
        }
        return Optional.of(new Projection(brandId, bizJid));
    }
}
