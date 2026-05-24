package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.util.Locale;
import java.util.Optional;

/**
 * Literal-tuple validator for the CTWA-banner-suggestion boolean attributes,
 * accepting the wire literals {@code "false"} and {@code "true"}.
 *
 * @apiNote
 * Currently consumed by
 * {@code WASmaxInBizCtwaActionBannerSuggestionRequest} on the
 * {@code <config revoked=".../>} attribute that drives the
 * "revoked banner" short-circuit inside
 * {@code WAWebCTWAParseSuggestion.parseCTWASuggestion} (when the value is
 * {@link #TRUE}, the consumer emits a {@code "revokedBanner"} result and
 * skips rendering).
 *
 * @implNote
 * This implementation enumerates only the two literals exported by
 * {@code WASmaxInBizCtwaActionEnums.ENUM_FALSE_TRUE} (independent of the
 * identically-named {@link SmaxBizSettingsFalseTrueFlag} from
 * {@code WASmaxInBizSettingsEnums}); the two enums are kept separate to
 * preserve module-level source provenance.
 */
@WhatsAppWebModule(moduleName = "WASmaxInBizCtwaActionEnums")
@WhatsAppWebExport(
        moduleName = "WASmaxInBizCtwaActionEnums",
        exports = "ENUM_FALSE_TRUE",
        adaptation = WhatsAppAdaptation.ADAPTED
)
public enum SmaxBannerSuggestionFalseTrueFlag {
    /**
     * The wire literal {@code "false"}.
     *
     * @apiNote
     * On the {@code <config revoked=".../>} attribute, indicates the
     * banner is still active and should be rendered.
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizCtwaActionEnums",
            exports = "ENUM_FALSE_TRUE",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    FALSE,
    /**
     * The wire literal {@code "true"}.
     *
     * @apiNote
     * On the {@code <config revoked=".../>} attribute, indicates the
     * banner has been pulled server-side and the consumer should emit a
     * "revoked" result instead of rendering.
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizCtwaActionEnums",
            exports = "ENUM_FALSE_TRUE",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    TRUE;

    /**
     * Parses a wire-form attribute string into the matching enum constant.
     *
     * @apiNote
     * Mirrors WA Web's {@code attrStringEnum} lookup against
     * {@code ENUM_FALSE_TRUE}: any value other than the two documented
     * lowercase literals yields empty and aborts the surrounding stanza
     * parse.
     *
     * @implNote
     * This implementation upper-cases the input via
     * {@link Locale#ROOT} before delegating to
     * {@link Enum#valueOf(Class, String)}; the wire form is always
     * lowercase, the Java constants are upper-case.
     *
     * @param value the attribute value; may be {@code null}
     * @return an {@link Optional} carrying the matching constant, or
     *         empty when {@code value} is {@code null} or does not match
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizCtwaActionEnums",
            exports = "ENUM_FALSE_TRUE",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public static Optional<SmaxBannerSuggestionFalseTrueFlag> of(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(SmaxBannerSuggestionFalseTrueFlag.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
