package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code <ad_status/>} child projection shared by the SMB Facebook-page
 * linking and WhatsApp-ad-identity reply mixins.
 *
 * @apiNote
 * Bundles the two boolean flags the relay surfaces on a linked
 * Facebook-page entry or on a WhatsApp ad-identity entry: whether the
 * business has ever created an ad, and whether at least one
 * click-to-WhatsApp ad is currently active. WA Web composes the projection
 * into the mixins consumed by {@code WASmaxInBizLinkingFBPageResponseBaseMixin}
 * and {@code WASmaxInBizLinkingWhatsAppAdIdentityResponseMixin}, which
 * drive the SMB Business Tools linking surface (active-CTWA badge,
 * "create an ad" CTA gating).
 *
 * @implNote
 * This implementation reuses {@link SmaxGetLinkedAccountsFalseTrueFlag}
 * as the wire validator for both flags; WA Web instantiates the same
 * {@code WASmaxInBizLinkingEnums.ENUM_FALSE_TRUE} tuple for the two
 * {@code attrStringEnum} calls.
 */
@WhatsAppWebModule(moduleName = "WASmaxInBizLinkingAdStatusMixin")
public final class SmaxAdStatusMixin {
    /**
     * The mandatory {@code has_created_ad} flag.
     */
    private final SmaxGetLinkedAccountsFalseTrueFlag hasCreatedAd;

    /**
     * The mandatory {@code has_active_ctwa_ad} flag.
     */
    private final SmaxGetLinkedAccountsFalseTrueFlag hasActiveCtwaAd;

    /**
     * Constructs a projection from already-validated wire flags.
     *
     * @apiNote
     * Cobalt callers normally obtain a projection by parsing a parent
     * node via {@link #of(Node)}; this constructor is exposed for tests
     * and for hand-built fixtures that bypass the parser.
     *
     * @param hasCreatedAd    the {@code has_created_ad} flag; never {@code null}
     * @param hasActiveCtwaAd the {@code has_active_ctwa_ad} flag; never {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public SmaxAdStatusMixin(SmaxGetLinkedAccountsFalseTrueFlag hasCreatedAd,
                             SmaxGetLinkedAccountsFalseTrueFlag hasActiveCtwaAd) {
        this.hasCreatedAd = Objects.requireNonNull(hasCreatedAd, "hasCreatedAd cannot be null");
        this.hasActiveCtwaAd = Objects.requireNonNull(hasActiveCtwaAd, "hasActiveCtwaAd cannot be null");
    }

    /**
     * Returns the {@code has_created_ad} flag.
     *
     * @apiNote
     * {@link SmaxGetLinkedAccountsFalseTrueFlag#TRUE} indicates the
     * business has previously created at least one ad on the linked
     * Facebook account; the value gates the "create your first ad"
     * onboarding copy on the linking surface.
     *
     * @return the flag; never {@code null}
     */
    public SmaxGetLinkedAccountsFalseTrueFlag hasCreatedAd() {
        return hasCreatedAd;
    }

    /**
     * Returns the {@code has_active_ctwa_ad} flag.
     *
     * @apiNote
     * {@link SmaxGetLinkedAccountsFalseTrueFlag#TRUE} indicates that at
     * least one click-to-WhatsApp ad on the linked Facebook account is
     * currently live; the value drives the active-CTWA badge on the
     * SMB Business Tools linking surface.
     *
     * @return the flag; never {@code null}
     */
    public SmaxGetLinkedAccountsFalseTrueFlag hasActiveCtwaAd() {
        return hasActiveCtwaAd;
    }

    /**
     * Parses the projection from a parent node by locating its mandatory
     * {@code <ad_status/>} child.
     *
     * @apiNote
     * Callers pass the parent stanza ({@code <fb_page/>} or
     * {@code <whatsapp_ad_identity/>}) directly; the method walks down to
     * the {@code <ad_status/>} grandchild itself. Returns empty whenever
     * the child is absent or either of the two mandatory enum attributes
     * is missing or carries an unrecognised literal, mirroring WA Web's
     * {@code WAResultOrError.makeResult}/error propagation.
     *
     * @implNote
     * This implementation matches the WA Web parser exactly: the
     * {@code <ad_status/>} child is mandatory; both
     * {@code has_created_ad} and {@code has_active_ctwa_ad} are
     * validated against {@link SmaxGetLinkedAccountsFalseTrueFlag} as a
     * case-sensitive lowercase dictionary match.
     *
     * @param node the parent node hosting the {@code <ad_status/>} child; never {@code null}
     * @return an {@link Optional} carrying the projection, or empty when the
     *         child is missing or either mandatory attribute fails validation
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBizLinkingAdStatusMixin",
            exports = "parseAdStatusMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxAdStatusMixin> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        var adStatusNode = node.getChild("ad_status").orElse(null);
        if (adStatusNode == null) {
            return Optional.empty();
        }
        var hasCreatedAdStr = adStatusNode.getAttributeAsString("has_created_ad").orElse(null);
        var hasCreatedAd = SmaxGetLinkedAccountsFalseTrueFlag.of(hasCreatedAdStr).orElse(null);
        if (hasCreatedAd == null) {
            return Optional.empty();
        }
        var hasActiveCtwaAdStr = adStatusNode.getAttributeAsString("has_active_ctwa_ad").orElse(null);
        var hasActiveCtwaAd = SmaxGetLinkedAccountsFalseTrueFlag.of(hasActiveCtwaAdStr).orElse(null);
        if (hasActiveCtwaAd == null) {
            return Optional.empty();
        }
        return Optional.of(new SmaxAdStatusMixin(hasCreatedAd, hasActiveCtwaAd));
    }

    /**
     * Compares this projection to {@code obj} for structural equality on the
     * two wire flags.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when {@code obj} is a {@link SmaxAdStatusMixin}
     *         with matching {@link #hasCreatedAd()} and {@link #hasActiveCtwaAd()}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxAdStatusMixin) obj;
        return this.hasCreatedAd == that.hasCreatedAd
                && this.hasActiveCtwaAd == that.hasActiveCtwaAd;
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash of the two wire flags
     */
    @Override
    public int hashCode() {
        return Objects.hash(hasCreatedAd, hasActiveCtwaAd);
    }

    /**
     * Returns a debug-friendly rendering naming both wire flags.
     *
     * @return a record-style string with the two flag values
     */
    @Override
    public String toString() {
        return "SmaxAdStatusMixin[hasCreatedAd=" + hasCreatedAd
                + ", hasActiveCtwaAd=" + hasActiveCtwaAd + ']';
    }
}
