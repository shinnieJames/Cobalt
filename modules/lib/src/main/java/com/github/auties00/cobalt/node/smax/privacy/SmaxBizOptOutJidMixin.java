package com.github.auties00.cobalt.node.smax.privacy;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;

import java.util.Objects;
import java.util.Optional;

/**
 * The wire-level parser for the {@code BizOptOutJid} arm of an opt-out item's {@code biz_opt_out_ids} field.
 *
 * @apiNote
 * Invoked from {@link BizOptOutId#parse(Node)} as the fall-through arm after
 * {@link SmaxBizOptOutBrandIdMixin#parse(Node)}; reads the required {@code biz_opt_out_jid} attribute and projects
 * it as a business JID. Used by {@code WAWebGetOptOutList.getOptOutList} to attach a chat-list pill keyed directly
 * on the resolved {@code wid} without further brand-id expansion.
 *
 * @implNote
 * This implementation collapses WA Web's {@code WAResultOrError} singleton to a plain {@link Optional}: empty
 * means the required JID attribute is missing or fails user-JID validation.
 */
@WhatsAppWebModule(moduleName = "WASmaxInBlocklistsBizOptOutJidMixin")
public final class SmaxBizOptOutJidMixin {
    /**
     * The unreachable utility-class constructor.
     *
     * @apiNote
     * The class only exposes static parsing helpers; instantiation is forbidden to keep the call site obvious.
     *
     * @throws AssertionError on every invocation
     */
    private SmaxBizOptOutJidMixin() {
        throw new AssertionError("SmaxBizOptOutJidMixin cannot be instantiated");
    }

    /**
     * The decoded {@code BizOptOutJid} singleton wrapping the parsed business user JID.
     *
     * @apiNote
     * Returned by {@link SmaxBizOptOutJidMixin#parse(Node)} when the {@code biz_opt_out_jid} attribute is present
     * and validates as a user JID. Consumed by {@link BizOptOutId#parse(Node)} to lift the JID into the
     * {@link BizOptOutId.UserJid} variant.
     *
     * @param bizOptOutJid the parsed business user JID; never {@code null}
     */
    public record Projection(Jid bizOptOutJid) {
        /**
         * Validates the projection payload.
         *
         * @apiNote
         * The compact constructor rejects a missing JID up front; the calling parser has already validated the
         * server component, so this is a defensive check against incorrect builder use.
         *
         * @param bizOptOutJid the business JID; never {@code null}
         * @throws NullPointerException if {@code bizOptOutJid} is {@code null}
         */
        public Projection {
            Objects.requireNonNull(bizOptOutJid, "bizOptOutJid cannot be null");
        }
    }

    /**
     * Parses an {@code <item>} node as a {@code BizOptOutJid} projection.
     *
     * @apiNote
     * Returns {@link Optional#empty()} when the required {@code biz_opt_out_jid} attribute is missing or fails
     * {@link Jid#hasUserServer()}. Callers in the {@code biz_opt_out_ids} disjunction lift the projection into
     * the {@link BizOptOutId.UserJid} arm when present.
     *
     * @implNote
     * This implementation mirrors WA Web's {@code attrUserJid} validator inline: a present-but-non-user JID
     * rejects the projection so the disjunction can fail correctly rather than silently accepting a malformed
     * value.
     *
     * @param item the source {@code <item>} node; never {@code null}
     * @return an {@link Optional} carrying the projection, or empty when the schema does not match
     * @throws NullPointerException if {@code item} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsBizOptOutJidMixin",
            exports = "parseBizOptOutJidMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<Projection> parse(Node item) {
        Objects.requireNonNull(item, "item cannot be null");
        var parsed = item.getAttributeAsJid("biz_opt_out_jid").orElse(null);
        if (parsed == null || !parsed.hasUserServer()) {
            return Optional.empty();
        }
        return Optional.of(new Projection(parsed));
    }
}
