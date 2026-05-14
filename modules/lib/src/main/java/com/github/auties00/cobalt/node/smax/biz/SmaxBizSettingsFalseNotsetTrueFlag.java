package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.BusinessDataSharingConsent;

import java.util.Optional;

/**
 * Literal-tuple validator for the tri-state attribute carried by
 * {@code <smb_data_sharing_with_meta_consent value="..."/>}. Accepts
 * the lowercase wire literals {@code "false"} / {@code "notset"} /
 * {@code "true"} mapped onto the named flag constants
 * {@link #FALSE} / {@link #NOTSET} / {@link #TRUE}.
 *
 * <p>Consumed by
 * {@code WASmaxInBizSettingsSmbDataSharingSettingValueMixin.parseSmbDataSharingSettingValueMixin}
 * to validate the {@code value} attribute on the
 * {@code <smb_data_sharing_with_meta_consent>} grandchild of the
 * {@code <privacy>} body in
 * {@link SmaxGetPrivacySettingResponse.Success},
 * {@link SmaxSetPrivacySettingResponse.Success}, and
 * {@link SmaxSyncPrivacySettingResponse.Notification}.
 *
 * <p>Cross-module note: the model-side {@link BusinessDataSharingConsent}
 * carries the same tri-state with full protobuf wiring and is the
 * surface used by {@code WhatsAppClient.editBusinessPrivacySetting}
 * callers; this enum exists separately to record the WA Web
 * {@code WASmaxInBizSettingsEnums.ENUM_FALSE_NOTSET_TRUE} provenance
 * lib-side (the {@code modules/model} submodule does not carry source
 * provenance annotations per the lib-only rule). The two enums share
 * the same wire literals; {@link #consent()} bridges to the model
 * enum.
 */
@WhatsAppWebModule(moduleName = "WASmaxInBizSettingsEnums")
@WhatsAppWebExport(
        moduleName = "WASmaxInBizSettingsEnums",
        exports = "ENUM_FALSE_NOTSET_TRUE",
        adaptation = WhatsAppAdaptation.ADAPTED
)
public enum SmaxBizSettingsFalseNotsetTrueFlag {
    /**
     * Wire literal {@code "false"}. The user has explicitly declined
     * to share SMB data with Meta.
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizSettingsEnums",
            exports = "ENUM_FALSE_NOTSET_TRUE",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    FALSE("false", BusinessDataSharingConsent.FALSE),

    /**
     * Wire literal {@code "notset"}. The user has not yet been
     * prompted to make a choice; the client surfaces the consent
     * dialog whenever this value is observed.
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizSettingsEnums",
            exports = "ENUM_FALSE_NOTSET_TRUE",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    NOTSET("notset", BusinessDataSharingConsent.NOT_SET),

    /**
     * Wire literal {@code "true"}. The user has explicitly granted
     * consent to share SMB data with Meta.
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
     * The matching model-side {@link BusinessDataSharingConsent}
     * constant.
     */
    private final BusinessDataSharingConsent consent;

    /**
     * Constructs a new {@code SmaxBizSettingsFalseNotsetTrueFlag}.
     *
     * @param wireValue the wire literal; never {@code null}
     * @param consent   the matching model-side consent; never
     *                  {@code null}
     */
    SmaxBizSettingsFalseNotsetTrueFlag(String wireValue, BusinessDataSharingConsent consent) {
        this.wireValue = wireValue;
        this.consent = consent;
    }

    /**
     * Returns the wire literal carried on the {@code value} attribute.
     *
     * @return the wire literal; never {@code null}
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * Returns the matching model-side
     * {@link BusinessDataSharingConsent} constant.
     *
     * @return the model-side consent; never {@code null}
     */
    public BusinessDataSharingConsent consent() {
        return consent;
    }

    /**
     * Tries to parse a wire-form attribute string into the matching
     * enum constant. Mirrors the WA Web {@code attrStringEnum} lookup,
     * which is a case-sensitive dictionary match against the lowercase
     * literals.
     *
     * @param value the attribute value; may be {@code null}
     * @return an {@link Optional} carrying the matching enum constant,
     *         or empty when {@code value} is {@code null} or does not
     *         match any documented literal
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
