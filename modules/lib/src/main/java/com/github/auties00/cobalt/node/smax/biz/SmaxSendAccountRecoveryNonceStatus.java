package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.util.Optional;

/**
 * The outcome literal carried by the
 * {@link SmaxSendAccountRecoveryNonceResponse.Success} reply's
 * {@code <Result><status>...</status></Result>} content.
 *
 * @apiNote
 * Distinguishes the two states returned by
 * {@code WAWebRequestAdAccountRecoveryCode.requestAdAccountRecoveryCode}
 * when the CTWA biz recovery-email flow runs: {@link #SUCCESS} means
 * the relay actually dispatched the recovery email (the UI advances
 * to the code-entry step), {@link #FAIL} means the relay refused to
 * dispatch it (the UI surfaces a generic failure).
 *
 * @implNote
 * This implementation mirrors the JS dictionary lookup performed by
 * {@code WASmaxParseUtils.contentStringEnum} against
 * {@code WASmaxInBizCtwaAdAccountEnums.ENUM_FAIL_SUCCESS} via a
 * case-sensitive {@code switch} on the wire literal in {@link #of(String)}.
 */
@WhatsAppWebModule(moduleName = "WASmaxInBizCtwaAdAccountEnums")
@WhatsAppWebExport(
        moduleName = "WASmaxInBizCtwaAdAccountEnums",
        exports = "ENUM_FAIL_SUCCESS",
        adaptation = WhatsAppAdaptation.ADAPTED
)
public enum SmaxSendAccountRecoveryNonceStatus {
    /**
     * Indicates that the relay attempted to dispatch the recovery
     * email and gave up.
     *
     * @apiNote
     * Carries the wire literal {@code "Fail"}. Triggers the
     * {@code requestAdAccountRecoveryCode} caller to annotate the
     * QPL flow with {@code failureReason="fail"} and surface a
     * generic failure to the user.
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizCtwaAdAccountEnums",
            exports = "ENUM_FAIL_SUCCESS",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    FAIL("Fail"),
    /**
     * Indicates that the relay successfully dispatched the recovery
     * email to the user's registered inbox.
     *
     * @apiNote
     * Carries the wire literal {@code "Success"}. Triggers the
     * {@code requestAdAccountRecoveryCode} caller to close the
     * request-code QPL marker successfully and advance the UI to
     * the recovery-code entry step.
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizCtwaAdAccountEnums",
            exports = "ENUM_FAIL_SUCCESS",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    SUCCESS("Success");

    /**
     * The wire literal carried by the {@code <status>} element content.
     */
    private final String wireValue;

    /**
     * Constructs a constant bound to the supplied wire literal.
     *
     * @apiNote
     * Package-private convention; the public surface is the two
     * named constants together with {@link #of(String)} and
     * {@link #wireValue()}.
     *
     * @param wireValue the exact wire-form literal; never {@code null}
     */
    SmaxSendAccountRecoveryNonceStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the wire literal that corresponds to this constant.
     *
     * @apiNote
     * Mirrors the values held by the {@code ENUM_FAIL_SUCCESS}
     * dictionary in {@code WASmaxInBizCtwaAdAccountEnums}.
     *
     * @return the wire literal (one of {@code "Fail"} /
     *         {@code "Success"}); never {@code null}
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * Returns the constant whose wire literal equals {@code value}.
     *
     * @apiNote
     * Used by {@link SmaxSendAccountRecoveryNonceResponse.Success#of(com.github.auties00.cobalt.node.Node, com.github.auties00.cobalt.node.Node)}
     * to decode the {@code <Result><status>} content. The lookup is
     * case-sensitive and rejects any literal outside the documented
     * pair, matching the JS
     * {@code WASmaxParseUtils.contentStringEnum} behaviour.
     *
     * @param value the candidate wire literal; may be {@code null}
     * @return an {@link Optional} carrying the matching constant, or
     *         {@link Optional#empty()} when {@code value} is
     *         {@code null} or does not match any documented literal
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizCtwaAdAccountEnums",
            exports = "ENUM_FAIL_SUCCESS",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public static Optional<SmaxSendAccountRecoveryNonceStatus> of(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return switch (value) {
            case "Fail" -> Optional.of(FAIL);
            case "Success" -> Optional.of(SUCCESS);
            default -> Optional.empty();
        };
    }
}
