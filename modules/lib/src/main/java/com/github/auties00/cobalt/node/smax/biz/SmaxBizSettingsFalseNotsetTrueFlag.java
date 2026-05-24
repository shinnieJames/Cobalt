package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.BusinessDataSharingConsent;

import java.util.Optional;

/**
 * Literal-tuple validator for the tri-state attribute carried by
 * {@code <smb_data_sharing_with_meta_consent value="..."/>}, accepting the
 * lowercase wire literals {@code "false"}, {@code "notset"}, and
 * {@code "true"}.
 *
 * @apiNote
 * Consumed by
 * {@code WASmaxInBizSettingsSmbDataSharingSettingValueMixin.parseSmbDataSharingSettingValueMixin}
 * to validate the {@code value} attribute on the
 * {@code <smb_data_sharing_with_meta_consent>} grandchild of the
 * {@code <privacy>} body in {@link SmaxGetPrivacySettingResponse.Success},
 * {@link SmaxSetPrivacySettingResponse.Success}, and
 * {@link SmaxSyncPrivacySettingResponse.Notification}. WA Web routes
 * the parsed value into {@code WAWebCommonCTWADataSharing}, which gates
 * the CTWA "share data with Meta" upsell modal and the per-customer
 * data-sharing controls; {@link #NOTSET} surfaces the consent dialog
 * proactively.
 *
 * @implNote
 * This implementation carries the model-side
 * {@link BusinessDataSharingConsent} as a bridge field so callers of
 * {@code WhatsAppClient.editBusinessPrivacySetting} can translate
 * between the wire form parsed here and the protobuf form used by the
 * higher-level surface. The two enums are kept separate because the
 * {@code modules/model} submodule does not carry source provenance
 * annotations per the lib-only rule.
 */
@WhatsAppWebModule(moduleName = "WASmaxInBizSettingsEnums")
@WhatsAppWebExport(
        moduleName = "WASmaxInBizSettingsEnums",
        exports = "ENUM_FALSE_NOTSET_TRUE",
        adaptation = WhatsAppAdaptation.ADAPTED
)
public enum SmaxBizSettingsFalseNotsetTrueFlag {
    /**
     * The wire literal {@code "false"}.
     *
     * @apiNote
     * The user has explicitly declined to share SMB data with Meta;
     * WA Web routes this into the upsell-modal eligibility check.
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizSettingsEnums",
            exports = "ENUM_FALSE_NOTSET_TRUE",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    FALSE("false", BusinessDataSharingConsent.FALSE),

    /**
     * The wire literal {@code "notset"}.
     *
     * @apiNote
     * The user has not yet been prompted to make a choice; WA Web's
     * {@code WAWebCommonCTWADataSharing.shouldShowCTWASmbDataSharingNux}
     * surfaces the consent dialog proactively whenever this value is
     * observed.
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizSettingsEnums",
            exports = "ENUM_FALSE_NOTSET_TRUE",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    NOTSET("notset", BusinessDataSharingConsent.NOT_SET),

    /**
     * The wire literal {@code "true"}.
     *
     * @apiNote
     * The user has explicitly granted consent to share SMB data with
     * Meta; downstream surfaces unlock the per-customer data-sharing
     * controls.
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizSettingsEnums",
            exports = "ENUM_FALSE_NOTSET_TRUE",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    TRUE("true", BusinessDataSharingConsent.TRUE);

    /**
     * The wire literal carried on the {@code value} attribute.
     */
    private final String wireValue;

    /**
     * The matching model-side {@link BusinessDataSharingConsent} constant.
     */
    private final BusinessDataSharingConsent consent;

    /**
     * Constructs a constant binding a wire literal to its model-side
     * companion.
     *
     * @apiNote
     * Internal enum constructor; not callable from outside the type.
     *
     * @param wireValue the wire literal; never {@code null}
     * @param consent   the matching model-side consent; never {@code null}
     */
    SmaxBizSettingsFalseNotsetTrueFlag(String wireValue, BusinessDataSharingConsent consent) {
        this.wireValue = wireValue;
        this.consent = consent;
    }

    /**
     * Returns the wire literal carried on the {@code value} attribute.
     *
     * @apiNote
     * Lowercase wire form; used by tests and by debug rendering. Round-trip
     * via {@link #of(String)}.
     *
     * @return the wire literal; never {@code null}
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * Returns the matching model-side {@link BusinessDataSharingConsent}
     * constant.
     *
     * @apiNote
     * Bridges between this lib-side wire-validator and the model-side
     * protobuf-wired enum exposed on {@code WhatsAppClient.editBusinessPrivacySetting}.
     *
     * @return the model-side consent; never {@code null}
     */
    public BusinessDataSharingConsent consent() {
        return consent;
    }

    /**
     * Parses a wire-form attribute string into the matching enum constant.
     *
     * @apiNote
     * Mirrors WA Web's {@code attrStringEnum} lookup against
     * {@code ENUM_FALSE_NOTSET_TRUE}: any value other than the three
     * documented lowercase literals yields empty and aborts the
     * surrounding stanza parse.
     *
     * @implNote
     * This implementation iterates the constants comparing
     * {@link #wireValue} rather than using
     * {@link Enum#valueOf(Class, String)} because the wire literal
     * {@code "notset"} does not match the Java constant name
     * {@code NOTSET} after a case-insensitive normalisation step (the
     * other two literals are lowercase). A field-by-field iteration
     * keeps the mapping explicit and avoids the surprise of relying on
     * locale-sensitive case normalisation.
     *
     * @param value the attribute value; may be {@code null}
     * @return an {@link Optional} carrying the matching constant, or empty
     *         when {@code value} is {@code null} or does not match
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizSettingsEnums",
            exports = "ENUM_FALSE_NOTSET_TRUE",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public static Optional<SmaxBizSettingsFalseNotsetTrueFlag> of(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (var flag : values()) {
            if (flag.wireValue.equals(value)) {
                return Optional.of(flag);
            }
        }
        return Optional.empty();
    }
}
