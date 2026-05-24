package com.github.auties00.cobalt.call.internal.transport.ice;

/**
 * The two RTP components an ICE agent gathers candidates for, per
 * RFC 8445 §3 — RTP itself and the optional RTCP demuxed alongside.
 *
 * <p>WhatsApp uses {@code rtcp-mux} (RFC 5761), so a single
 * {@link #RTP} component carries both RTP and RTCP. The
 * {@link #RTCP} component is included so the model can express
 * legacy / non-muxed peers if needed.
 */
public enum IceComponent {
    /**
     * The RTP / RTP+RTCP-muxed component — component-id {@code 1}.
     */
    RTP(1),
    /**
     * The (legacy) demultiplexed RTCP component — component-id
     * {@code 2}.
     */
    RTCP(2);

    /**
     * Wire component id, used in candidate-priority computation per
     * RFC 8445 §5.1.2.1.
     */
    private final int componentId;

    /**
     * Constructs a component with the given wire id.
     *
     * @param componentId the wire component id (1 or 2)
     */
    IceComponent(int componentId) {
        this.componentId = componentId;
    }

    /**
     * Returns the wire component id.
     *
     * @return {@code 1} for RTP, {@code 2} for RTCP
     */
    public int componentId() {
        return componentId;
    }
}
