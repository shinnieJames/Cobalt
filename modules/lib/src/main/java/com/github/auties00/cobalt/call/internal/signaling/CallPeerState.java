package com.github.auties00.cobalt.call.internal.signaling;

import com.github.auties00.cobalt.call.CallState;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * The transient mid-call states a peer can advertise via the
 * {@code <call><peer_state>} stanza. Used by
 * {@code WhatsAppClientListener.onCallPeerStateChanged} so listeners
 * can switch on a typed enum instead of comparing wire-string
 * literals.
 *
 * <p>Wire value comes off the {@code state} attribute on the
 * {@code <peer_state>} payload.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSignalingEnums")
public enum CallPeerState {
    /**
     * The peer's transport has come up and media is flowing — the
     * counterpart of the local-side {@link CallState#ACTIVE}.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "PEER_STATE", adaptation = WhatsAppAdaptation.DIRECT)
    CONNECTED("connected"),

    /**
     * The peer's transport has dropped and they're attempting to
     * re-establish — typically a network blip on the peer side.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "PEER_STATE", adaptation = WhatsAppAdaptation.DIRECT)
    RECONNECTING("reconnecting"),

    /**
     * The peer is busy in another call (received during the
     * {@link CallState#RINGING} window before they accept).
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "PEER_STATE", adaptation = WhatsAppAdaptation.DIRECT)
    BUSY("busy"),

    /**
     * The peer has placed the call on hold.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "PEER_STATE", adaptation = WhatsAppAdaptation.DIRECT)
    HOLD("hold"),

    /**
     * The peer has resumed from hold.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "PEER_STATE", adaptation = WhatsAppAdaptation.DIRECT)
    RESUMED("resumed"),

    /**
     * Fallback for wire values not in this enum — the receiver
     * surfaces the original literal via {@link #wireValue()} so
     * callers can still inspect it.
     */
    UNKNOWN(null);

    /**
     * Holds the literal from the wire {@code state} attribute, or
     * {@code null} for {@link #UNKNOWN}.
     */
    private final String wireValue;

    /**
     * Constructs a new state with its wire-value mapping.
     *
     * @param wireValue the literal from the {@code state} attribute
     */
    CallPeerState(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the wire-level {@code state} literal that maps to this
     * enum value, or {@code null} for {@link #UNKNOWN}.
     *
     * @return the wire literal
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * Maps a wire-level {@code state} attribute to its
     * {@link CallPeerState}, or {@link #UNKNOWN} if the literal is
     * not recognised.
     *
     * @param wire the wire string, or {@code null}
     * @return the matching enum, never {@code null}
     */
    public static CallPeerState fromWireValue(String wire) {
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
