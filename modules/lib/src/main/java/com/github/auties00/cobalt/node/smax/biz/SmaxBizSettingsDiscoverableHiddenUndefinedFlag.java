package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.util.Optional;

/**
 * Literal-tuple validator for the SMB-profile discoverability tri-state on
 * business-settings stanzas, accepting the uppercase wire literals
 * {@code "DISCOVERABLE"}, {@code "HIDDEN"}, and {@code "UNDEFINED"}.
 *
 * @apiNote
 * Provided for parity with the WA literal universe; no Cobalt parser
 * observes this enum on the wire today. The corresponding
 * {@code WASmaxInBizSettingsEnums.ENUM_DISCOVERABLE_HIDDEN_UNDEFINED}
 * tuple is referenced by SMB business-profile settings flows on WA Web.
 *
 * @implNote
 * This implementation enumerates only the three literals exported by
 * {@code WASmaxInBizSettingsEnums.ENUM_DISCOVERABLE_HIDDEN_UNDEFINED};
 * the wire literals are uppercase (unlike most other SMAX enums) and
 * {@link #of(String)} therefore preserves the input case rather than
 * normalising it.
 */
@WhatsAppWebModule(moduleName = "WASmaxInBizSettingsEnums")
@WhatsAppWebExport(
        moduleName = "WASmaxInBizSettingsEnums",
        exports = "ENUM_DISCOVERABLE_HIDDEN_UNDEFINED",
        adaptation = WhatsAppAdaptation.ADAPTED
)
public enum SmaxBizSettingsDiscoverableHiddenUndefinedFlag {
    /**
     * The wire literal {@code "DISCOVERABLE"}.
     *
     * @apiNote
     * Indicates the SMB profile is publicly discoverable in WhatsApp's
     * business directory and search surfaces.
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizSettingsEnums",
            exports = "ENUM_DISCOVERABLE_HIDDEN_UNDEFINED",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    DISCOVERABLE,

    /**
     * The wire literal {@code "HIDDEN"}.
     *
     * @apiNote
     * Indicates the SMB profile is hidden from directory and search
     * surfaces.
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizSettingsEnums",
            exports = "ENUM_DISCOVERABLE_HIDDEN_UNDEFINED",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    HIDDEN,

    /**
     * The wire literal {@code "UNDEFINED"}.
     *
     * @apiNote
     * Indicates the user has not yet picked a discoverability mode; the
     * client surfaces the relevant onboarding dialog whenever this value
     * is observed.
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizSettingsEnums",
            exports = "ENUM_DISCOVERABLE_HIDDEN_UNDEFINED",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    UNDEFINED;

    /**
     * Parses a wire-form attribute string into the matching enum constant.
     *
     * @apiNote
     * Mirrors WA Web's {@code attrStringEnum} lookup against
     * {@code ENUM_DISCOVERABLE_HIDDEN_UNDEFINED}: any value other than
     * the three documented uppercase literals yields empty and aborts
     * the surrounding stanza parse.
     *
     * @implNote
     * This implementation does NOT case-normalise the input because the
     * wire literals are themselves uppercase; passing a lowercase value
     * yields empty.
     *
     * @param value the attribute value; may be {@code null}
     * @return an {@link Optional} carrying the matching constant, or empty
     *         when {@code value} is {@code null} or does not match
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizSettingsEnums",
            exports = "ENUM_DISCOVERABLE_HIDDEN_UNDEFINED",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public static Optional<SmaxBizSettingsDiscoverableHiddenUndefinedFlag> of(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(SmaxBizSettingsDiscoverableHiddenUndefinedFlag.valueOf(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
