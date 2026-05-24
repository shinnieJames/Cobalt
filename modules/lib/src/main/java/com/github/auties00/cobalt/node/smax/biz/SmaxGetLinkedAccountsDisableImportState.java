package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import java.util.Locale;
import java.util.Optional;

/**
 * The {@code state} enum carried by the {@code <profile_sync>}
 * grandchild of {@code <fb_page/>} and the {@code <catalog>}
 * grandchild of {@code <fb_biz/>} in the
 * {@code GetLinkedAccounts} success reply.
 *
 * @apiNote
 * Surfaces the per-linked-page profile-sync toggle and the
 * per-linked-business catalog-sync toggle consumed by the WA Web
 * CTWA ads-identity pipeline
 * ({@code WAWebLinkedAccountsJob.queryLinkedPagesInfo}); callers
 * distinguish a paused/disabled sync from an in-progress import
 * to drive the linked-pages UI.
 *
 * @implNote
 * This implementation accepts only the case-insensitive literals
 * {@code "DISABLE"} and {@code "IMPORT"} surfaced by
 * {@code WASmaxInBizLinkingEnums.ENUM_DISABLE_IMPORT}.
 */
@WhatsAppWebModule(moduleName = "WASmaxInBizLinkingEnums")
public enum SmaxGetLinkedAccountsDisableImportState {
    /**
     * Wire form {@code "disable"}.
     *
     * @apiNote
     * Sync is currently disabled; the CTWA linked-pages UI shows
     * the corresponding entry as inert.
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBizLinkingEnums",
            exports = "ENUM_DISABLE_IMPORT", adaptation = WhatsAppAdaptation.DIRECT)
    DISABLE,
    /**
     * Wire form {@code "import"}.
     *
     * @apiNote
     * Sync is in progress or has been requested; the CTWA
     * linked-pages UI shows the corresponding entry as active.
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBizLinkingEnums",
            exports = "ENUM_DISABLE_IMPORT", adaptation = WhatsAppAdaptation.DIRECT)
    IMPORT;

    /**
     * Resolves a wire-form attribute string into the matching enum
     * constant.
     *
     * @apiNote
     * Invoked while parsing a successful {@code GetLinkedAccounts}
     * reply for both the {@code <fb_page><profile_sync state>} and
     * the {@code <fb_biz><catalog state>} positions.
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
            exports = "ENUM_DISABLE_IMPORT", adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxGetLinkedAccountsDisableImportState> of(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(SmaxGetLinkedAccountsDisableImportState.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
