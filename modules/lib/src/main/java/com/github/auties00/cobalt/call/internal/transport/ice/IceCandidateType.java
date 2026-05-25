package com.github.auties00.cobalt.call.internal.transport.ice;

/**
 * Enumerates the four ICE candidate types defined by RFC 8445 section 5.1.1.
 *
 * <p>The constants are declared in decreasing {@link #typePreference()} order so that the
 * candidate-priority computation in {@link IceCandidate#priority()} ranks a host candidate above
 * a server-reflexive one and a relayed candidate last. The type preference is the dominant term
 * of that formula because it is shifted left by 24 bits, so it outweighs both the local-preference
 * and the component-id terms.
 */
public enum IceCandidateType {
    /**
     * A local-machine transport address obtained directly from the operating-system network stack.
     *
     * <p>Host candidates carry the highest type preference because no NAT or relay sits between
     * the two endpoints, so a path built on a host candidate is the cheapest and lowest-latency
     * option when it works.
     *
     * @implNote This implementation uses the RFC 8445 section 5.1.2.2 recommended type preference
     * of {@code 126} for host candidates.
     */
    HOST(126),
    /**
     * A public transport address learned via a STUN binding observed from a server-side vantage
     * point, typically the WhatsApp relay's BIND surface.
     *
     * <p>A server-reflexive candidate is the address the public Internet sees the local endpoint
     * on, discovered by reflecting a binding request off a STUN server.
     *
     * @implNote This implementation uses the RFC 8445 section 5.1.2.2 recommended type preference
     * of {@code 100} for server-reflexive candidates.
     */
    SERVER_REFLEXIVE(100),
    /**
     * A public transport address learned via a STUN binding received from a peer during a
     * connectivity check.
     *
     * <p>A peer-reflexive candidate is discovered on demand, when an inbound binding request
     * arrives from a source address that does not match any previously known candidate. It ranks
     * above server-reflexive because it reflects a path the peer has already exercised.
     *
     * @implNote This implementation uses the RFC 8445 section 5.1.2.2 recommended type preference
     * of {@code 110} for peer-reflexive candidates.
     */
    PEER_REFLEXIVE(110),
    /**
     * A transport address allocated on a TURN-style relay, where every datagram is forwarded by
     * the relay on the endpoint's behalf.
     *
     * <p>Relayed candidates carry the lowest type preference because the relay adds a network hop
     * and consumes server resources, so they are used only when no direct or reflexive path
     * succeeds.
     *
     * @implNote This implementation uses the RFC 8445 section 5.1.2.2 recommended type preference
     * of {@code 0} for relayed candidates.
     */
    RELAYED(0);

    /**
     * The RFC 8445 section 5.1.2.2 type preference value for this candidate type.
     */
    private final int typePreference;

    /**
     * Constructs a candidate type with the given type preference.
     *
     * @param typePreference the type preference value used in candidate-priority computation
     */
    IceCandidateType(int typePreference) {
        this.typePreference = typePreference;
    }

    /**
     * Returns the type-preference value used in candidate-priority computation.
     *
     * <p>The returned value is the {@code type_preference} term of the RFC 8445 section 5.1.2.1
     * priority formula evaluated by {@link IceCandidate#priority()}.
     *
     * @return the type preference, in the range {@code 0} to {@code 126}
     */
    public int typePreference() {
        return typePreference;
    }
}
