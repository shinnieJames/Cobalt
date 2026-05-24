package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import java.util.Locale;
import java.util.Optional;

/**
 * The {@code show_on_profile} boolean enum surfaced by the
 * {@code WASmaxInBizLinkingHasShowOnProfileMixin} on the
 * {@code <fb_page/>} and {@code <ig_professional/>} children of
 * the {@code GetLinkedAccounts} success reply.
 *
 * @apiNote
 * Drives the visibility toggle for the linked Facebook page or
 * Instagram-professional identity on the business profile surface
 * consumed by
 * {@code WAWebLinkedAccountsJob.queryLinkedPagesInfo}; callers
 * branch on {@link #TRUE} to render the badge and on {@link #FALSE}
 * to suppress it.
 *
 * @implNote
 * This implementation accepts only the case-insensitive literals
 * {@code "TRUE"} and {@code "FALSE"} surfaced by
 * {@code WASmaxInBizLinkingEnums.ENUM_FALSE_TRUE}.
 */
@WhatsAppWebModule(moduleName = "WASmaxInBizLinkingEnums")
public enum SmaxGetLinkedAccountsFalseTrueFlag {
    /**
     * Wire form {@code "false"}.
     *
     * @apiNote
     * The linked identity is hidden on the business profile
     * surface.
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBizLinkingEnums",
            exports = "ENUM_FALSE_TRUE", adaptation = WhatsAppAdaptation.DIRECT)
    FALSE,
    /**
     * Wire form {@code "true"}.
     *
     * @apiNote
     * The linked identity is shown on the business profile
     * surface.
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBizLinkingEnums",
            exports = "ENUM_FALSE_TRUE", adaptation = WhatsAppAdaptation.DIRECT)
    TRUE;

    /**
     * Resolves a wire-form value into the matching enum constant.
     *
     * @apiNote
     * Invoked while parsing a successful {@code GetLinkedAccounts}
     * reply for both the attribute and the element-content forms
     * of the show-on-profile mixin.
     *
     * @implNote
     * This implementation upper-cases the input under
     * {@link Locale#ROOT} before delegating to
     * {@link #valueOf(String)} and swallows the resulting
     * {@link IllegalArgumentException} as an empty result.
     *
     * @param value the attribute or element-content value; may be
     *              {@code null}
     * @return an {@link Optional} carrying the matching enum
     *         constant, or empty when the value is {@code null} or
     *         does not match a documented literal
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBizLinkingEnums",
            exports = "ENUM_FALSE_TRUE", adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxGetLinkedAccountsFalseTrueFlag> of(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(SmaxGetLinkedAccountsFalseTrueFlag.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
