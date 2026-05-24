package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.util.Locale;
import java.util.Optional;

/**
 * Literal-tuple validator for boolean-shaped attributes on SMB
 * business-settings stanzas, accepting the lowercase wire literals
 * {@code "false"} and {@code "true"}.
 *
 * @apiNote
 * Provided for parity with the WA literal universe; no Cobalt parser
 * observes this enum on the wire today. WA Web consumes the matching
 * tuple ({@code WASmaxInBizSettingsEnums.ENUM_FALSE_TRUE}) from UI flows
 * in {@code WAWebSmbDataSharingOptInModalDialog} to signal SMB
 * data-sharing opt-in / opt-out at the modal-confirmation layer.
 *
 * @implNote
 * This implementation enumerates only the two literals exported by
 * {@code WASmaxInBizSettingsEnums.ENUM_FALSE_TRUE} (independent of the
 * identically-named {@link SmaxBannerSuggestionFalseTrueFlag} from
 * {@code WASmaxInBizCtwaActionEnums}); the two enums are kept separate
 * so the source provenance of each module-level export survives.
 */
@WhatsAppWebModule(moduleName = "WASmaxInBizSettingsEnums")
@WhatsAppWebExport(
        moduleName = "WASmaxInBizSettingsEnums",
        exports = "ENUM_FALSE_TRUE",
        adaptation = WhatsAppAdaptation.ADAPTED
)
public enum SmaxBizSettingsFalseTrueFlag {
    /**
     * The wire literal {@code "false"}.
     *
     * @apiNote
     * Lowercase form. The Java constant name is upper-case to follow
     * Java conventions; round-trip via {@link #of(String)}.
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizSettingsEnums",
            exports = "ENUM_FALSE_TRUE",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    FALSE,

    /**
     * The wire literal {@code "true"}.
     *
     * @apiNote
     * Lowercase form. The Java constant name is upper-case to follow
     * Java conventions; round-trip via {@link #of(String)}.
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizSettingsEnums",
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
     * This implementation upper-cases the input via {@link Locale#ROOT}
     * before delegating to {@link Enum#valueOf(Class, String)}; the wire
     * form is always lowercase, the Java constants are upper-case.
     *
     * @param value the attribute value; may be {@code null}
     * @return an {@link Optional} carrying the matching constant, or empty
     *         when {@code value} is {@code null} or does not match
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizSettingsEnums",
            exports = "ENUM_FALSE_TRUE",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public static Optional<SmaxBizSettingsFalseTrueFlag> of(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(SmaxBizSettingsFalseTrueFlag.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
