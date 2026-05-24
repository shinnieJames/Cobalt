package com.github.auties00.cobalt.call;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Mirrors {@code WAWebVoipSignalingEnums.EndCallReason} — the reasons a
 * {@code <terminate>} stanza can carry.
 *
 * <p>The {@link #wireValue()} is the literal that lands in the
 * {@code reason} attribute on the outgoing {@code <terminate>} payload.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSignalingEnums")
public enum CallEndReason {
    /**
     * Catch-all. Used when no specific reason fits.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "EndCallReason", adaptation = WhatsAppAdaptation.DIRECT)
    UNKNOWN("unknown"),

    /**
     * The call rang until the server-side timeout elapsed.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "EndCallReason", adaptation = WhatsAppAdaptation.DIRECT)
    TIMEOUT("timeout"),

    /**
     * The local user hung up. Default for outgoing terminate.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "EndCallReason", adaptation = WhatsAppAdaptation.DIRECT)
    HANGUP("hangup"),

    /**
     * The callee's device was set to "do not disturb".
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "EndCallReason", adaptation = WhatsAppAdaptation.DIRECT)
    REJECT_DO_NOT_DISTURB("dnd"),

    /**
     * The callee has the caller blocked.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "EndCallReason", adaptation = WhatsAppAdaptation.DIRECT)
    REJECT_BLOCKED("blocked"),

    /**
     * Microphone permission denied locally.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "EndCallReason", adaptation = WhatsAppAdaptation.DIRECT)
    MIC_PERMISSION_DENIED("mic_permission_denied"),

    /**
     * Camera permission denied locally.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "EndCallReason", adaptation = WhatsAppAdaptation.DIRECT)
    CAMERA_PERMISSION_DENIED("camera_permission_denied"),

    /**
     * The call was accepted on a different device of the same account.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "EndCallReason", adaptation = WhatsAppAdaptation.DIRECT)
    ACCEPTED_ELSEWHERE("accepted_elsewhere");

    /**
     * Holds the literal value that goes onto the {@code reason} attribute.
     */
    private final String wireValue;

    /**
     * Constructs a new {@code CallEndReason} with the given wire value.
     *
     * @param wireValue the literal placed on the {@code reason} attribute
     */
    CallEndReason(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the literal value placed on the outgoing {@code reason}
     * attribute.
     *
     * @return the wire literal
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * Maps an inbound wire {@code reason} literal back to its
     * {@link CallEndReason}. Returns {@link #UNKNOWN} for any
     * literal not in the enum, including {@code null}.
     *
     * @param wire the wire string, or {@code null}
     * @return the matching enum, never {@code null}
     */
    public static CallEndReason fromWireValue(String wire) {
        if (wire == null) {
            return UNKNOWN;
        }
        for (var v : values()) {
            if (wire.equals(v.wireValue)) {
                return v;
            }
        }
        return UNKNOWN;
    }
}
