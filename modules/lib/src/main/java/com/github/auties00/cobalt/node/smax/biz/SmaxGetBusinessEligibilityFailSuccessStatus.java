package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import java.util.Locale;
import java.util.Optional;

/**
 * The binary pass/fail eligibility enum carried by the
 * {@code <meta_verified/>} and {@code <genai/>} children of the
 * {@code GetBusinessEligibility} success reply.
 *
 * @apiNote
 * Surfaced by
 * {@link SmaxGetBusinessEligibilityResponse.Success.MetaVerified}
 * and {@link SmaxGetBusinessEligibilityResponse.Success.Genai} when
 * mirroring WA Web's
 * {@code WAWebRefreshBusinessEligibility.exponentialBackoff} loop
 * that probes the Meta-Verified and GenAI per-broadcast eligibility
 * surfaces.
 *
 * @implNote
 * This implementation accepts only the documented case-insensitive
 * literals {@code "FAIL"} and {@code "SUCCESS"} surfaced by
 * {@code WASmaxInBizMarketingMessageEnums.ENUM_FAIL_SUCCESS}; any
 * other value is reported as {@link Optional#empty()} by
 * {@link #of(String)}.
 */
@WhatsAppWebModule(moduleName = "WASmaxInBizMarketingMessageEnums")
public enum SmaxGetBusinessEligibilityFailSuccessStatus {
    /**
     * The probed feature is not currently available to the calling
     * business.
     *
     * @apiNote
     * Wire form {@code "FAIL"}. Callers that mirror WA Web's
     * broadcast-gating logic hide the corresponding compose UI.
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBizMarketingMessageEnums",
            exports = "ENUM_FAIL_SUCCESS",
            adaptation = WhatsAppAdaptation.DIRECT)
    FAIL,
    /**
     * The probed feature is available.
     *
     * @apiNote
     * Wire form {@code "SUCCESS"}. Callers expose the corresponding
     * compose UI.
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBizMarketingMessageEnums",
            exports = "ENUM_FAIL_SUCCESS",
            adaptation = WhatsAppAdaptation.DIRECT)
    SUCCESS;

    /**
     * Resolves a wire-form attribute string into the matching enum
     * constant.
     *
     * @apiNote
     * Invoked while parsing a successful
     * {@code GetBusinessEligibility} reply; callers receive
     * {@link Optional#empty()} for any value outside the documented
     * dictionary and propagate it as a parse failure on the
     * enclosing projection.
     *
     * @implNote
     * This implementation upper-cases the input under
     * {@link Locale#ROOT} before delegating to
     * {@link #valueOf(String)} and swallows the resulting
     * {@link IllegalArgumentException} as an empty result, matching
     * WA's case-insensitive {@code attrStringEnum} dictionary
     * lookup.
     *
     * @param value the attribute value; may be {@code null}
     * @return an {@link Optional} carrying the matching enum
     *         constant, or empty when the value is {@code null} or
     *         does not match a documented literal
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBizMarketingMessageEnums",
            exports = "ENUM_FAIL_SUCCESS",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxGetBusinessEligibilityFailSuccessStatus> of(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(SmaxGetBusinessEligibilityFailSuccessStatus.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
