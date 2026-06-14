package com.github.auties00.cobalt.model.call;

/**
 * Enumerates the reasons a call termination can carry.
 *
 * <p>Each constant pairs a stable Java name with the literal {@link #wireValue()} that lands in the
 * {@code reason} attribute of an outgoing call-termination payload. {@link #fromWireValue(String)}
 * performs the inverse lookup for inbound terminations, collapsing any unrecognized or absent literal
 * to {@link #UNKNOWN}.
 */
public enum CallEndReason {
    /**
     * Indicates no specific reason applies; the catch-all default.
     */
    UNKNOWN("unknown"),

    /**
     * Indicates the call rang until the server-side timeout elapsed.
     */
    TIMEOUT("timeout"),

    /**
     * Indicates the local user hung up; the default for an outgoing termination.
     */
    HANGUP("hangup"),

    /**
     * Indicates the callee's device was set to do-not-disturb.
     */
    REJECT_DO_NOT_DISTURB("dnd"),

    /**
     * Indicates the callee has the caller blocked.
     */
    REJECT_BLOCKED("blocked"),

    /**
     * Indicates microphone permission was denied on the local device.
     */
    MIC_PERMISSION_DENIED("mic_permission_denied"),

    /**
     * Indicates camera permission was denied on the local device.
     */
    CAMERA_PERMISSION_DENIED("camera_permission_denied"),

    /**
     * Indicates the call was accepted on another device of the same account.
     */
    ACCEPTED_ELSEWHERE("accepted_elsewhere");

    /**
     * Holds the literal placed on the {@code reason} attribute for this reason.
     */
    private final String wireValue;

    /**
     * Constructs a reason bound to the given wire literal.
     *
     * @param wireValue the literal placed on the {@code reason} attribute
     */
    CallEndReason(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the literal placed on the outgoing {@code reason} attribute for this reason.
     *
     * @return the wire literal
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * Maps an inbound wire {@code reason} literal back to its {@link CallEndReason}.
     *
     * <p>Any literal not declared by this enum, including {@code null}, maps to {@link #UNKNOWN}, so
     * the result is never {@code null}.
     *
     * @param wire the wire string, or {@code null}
     * @return the matching constant, never {@code null}
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
