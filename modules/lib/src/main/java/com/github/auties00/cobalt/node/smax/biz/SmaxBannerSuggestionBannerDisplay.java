package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.util.Locale;
import java.util.Optional;

/**
 * Literal-tuple validator for the {@code display} attribute on the
 * {@code <config/>} child of the CTWA banner-suggestion stanza, accepting
 * the wire literals {@code "info"} and {@code "warning"}.
 *
 * @apiNote
 * Selects the visual styling applied by the WhatsApp Business "suggested
 * banner" panel: {@link #INFO} renders as a neutral informational tile,
 * {@link #WARNING} renders as a warning-styled tile to flag user
 * attention. The classification flows from
 * {@code WAWebCTWAParseSuggestion} into the banner-view component.
 *
 * @implNote
 * This implementation enumerates only the two literals exported by
 * {@code WASmaxInBizCtwaActionEnums.ENUM_INFO_WARNING}; any other value
 * fails validation.
 */
@WhatsAppWebModule(moduleName = "WASmaxInBizCtwaActionEnums")
@WhatsAppWebExport(
        moduleName = "WASmaxInBizCtwaActionEnums",
        exports = "ENUM_INFO_WARNING",
        adaptation = WhatsAppAdaptation.ADAPTED
)
public enum SmaxBannerSuggestionBannerDisplay {
    /**
     * The wire literal {@code "info"}.
     *
     * @apiNote
     * Indicates the banner should render as a neutral informational tile.
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizCtwaActionEnums",
            exports = "ENUM_INFO_WARNING",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    INFO,
    /**
     * The wire literal {@code "warning"}.
     *
     * @apiNote
     * Indicates the banner should render with warning-style chrome to
     * draw the user's attention.
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizCtwaActionEnums",
            exports = "ENUM_INFO_WARNING",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    WARNING;

    /**
     * Parses a wire-form attribute string into the matching enum constant.
     *
     * @apiNote
     * Mirrors WA Web's {@code attrStringEnum} lookup against
     * {@code ENUM_INFO_WARNING}: any value other than the two documented
     * lowercase literals yields empty and aborts the surrounding stanza
     * parse.
     *
     * @implNote
     * This implementation upper-cases the input via
     * {@link Locale#ROOT} before delegating to
     * {@link Enum#valueOf(Class, String)}; the wire is always lowercase but
     * the constant names are upper-case in line with Java conventions.
     *
     * @param value the attribute value; may be {@code null}
     * @return an {@link Optional} carrying the matching constant, or empty
     *         when {@code value} is {@code null} or does not match
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizCtwaActionEnums",
            exports = "ENUM_INFO_WARNING",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public static Optional<SmaxBannerSuggestionBannerDisplay> of(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(SmaxBannerSuggestionBannerDisplay.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
