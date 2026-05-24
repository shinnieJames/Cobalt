package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import java.util.Locale;
import java.util.Optional;

/**
 * The {@code state} enum carried by the
 * {@code <whatsapp_as_page_button>} grandchild of
 * {@code <fb_page/>} in the {@code GetLinkedAccounts} success
 * reply.
 *
 * @apiNote
 * Surfaces the opt-in toggle for the "WhatsApp as page button"
 * overlay on the linked Facebook page; the WA Web CTWA pipeline
 * consults this state to decide whether the page renders a
 * direct-message call-to-action.
 *
 * @implNote
 * This implementation accepts only the case-insensitive literals
 * {@code "OFF"} and {@code "ON"} surfaced by
 * {@code WASmaxInBizLinkingEnums.ENUM_OFF_ON}.
 */
@WhatsAppWebModule(moduleName = "WASmaxInBizLinkingEnums")
public enum SmaxGetLinkedAccountsOffOnState {
    /**
     * Wire form {@code "off"}.
     *
     * @apiNote
     * The WhatsApp page button is disabled.
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBizLinkingEnums",
            exports = "ENUM_OFF_ON", adaptation = WhatsAppAdaptation.DIRECT)
    OFF,
    /**
     * Wire form {@code "on"}.
     *
     * @apiNote
     * The WhatsApp page button is enabled.
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBizLinkingEnums",
            exports = "ENUM_OFF_ON", adaptation = WhatsAppAdaptation.DIRECT)
    ON;

    /**
     * Resolves a wire-form attribute string into the matching enum
     * constant.
     *
     * @apiNote
     * Invoked while parsing a successful {@code GetLinkedAccounts}
     * reply for the {@code <fb_page><whatsapp_as_page_button state>}
     * position.
     *
     * @implNote
     * This implementation upper-cases the input under
     * {@link Locale#ROOT} before delegating to
     * {@link #valueOf(String)} and swallows the resulting
     * {@link IllegalArgumentException} as an empty result.
     *
     * @param value the attribute value; may be {@code null}
     * @return an {@link Optional} carrying the matching enum
     *         constant, or empty when the value is {@code null} or
     *         does not match a documented literal
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBizLinkingEnums",
            exports = "ENUM_OFF_ON", adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxGetLinkedAccountsOffOnState> of(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(SmaxGetLinkedAccountsOffOnState.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
