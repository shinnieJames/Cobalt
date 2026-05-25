package com.github.auties00.cobalt.call.internal.transport.ice;

/**
 * Enumerates the two RTP components an ICE agent gathers candidates for, per RFC 8445 section 3.
 *
 * <p>A media stream is split into independently checked components: RTP itself and the optional
 * RTCP control channel demultiplexed alongside it. WhatsApp uses {@code rtcp-mux} (RFC 5761), so
 * the single {@link #RTP} component carries both RTP and RTCP on one transport address; the
 * {@link #RTCP} component is modeled only so the agent can express a legacy non-muxed peer.
 *
 * <p>The component id is the {@code component_id} term of the candidate-priority formula evaluated
 * by {@link IceCandidate#priority()}.
 */
public enum IceComponent {
    /**
     * The RTP component, which under {@code rtcp-mux} also carries demultiplexed RTCP.
     *
     * <p>Component id {@code 1}, the only component used on the WhatsApp muxed path.
     */
    RTP(1),
    /**
     * The legacy demultiplexed RTCP component.
     *
     * <p>Component id {@code 2}, modeled for non-muxed peers but unused on the WhatsApp path.
     */
    RTCP(2);

    /**
     * The wire component id used in candidate-priority computation per RFC 8445 section 5.1.2.1.
     */
    private final int componentId;

    /**
     * Constructs a component with the given wire component id.
     *
     * @param componentId the wire component id, {@code 1} for RTP or {@code 2} for RTCP
     */
    IceComponent(int componentId) {
        this.componentId = componentId;
    }

    /**
     * Returns the wire component id.
     *
     * <p>The returned value is the {@code component_id} term subtracted from {@code 256} in the
     * RFC 8445 section 5.1.2.1 priority formula evaluated by {@link IceCandidate#priority()}.
     *
     * @return {@code 1} for {@link #RTP}, {@code 2} for {@link #RTCP}
     */
    public int componentId() {
        return componentId;
    }
}
