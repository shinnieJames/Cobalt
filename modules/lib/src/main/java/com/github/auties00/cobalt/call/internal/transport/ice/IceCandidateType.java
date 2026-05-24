package com.github.auties00.cobalt.call.internal.transport.ice;

/**
 * The four candidate types defined by RFC 8445 §5.1.1, ordered by
 * decreasing {@link #typePreference()} so a high-priority host
 * candidate beats a low-priority relay one in {@link IceCandidate}'s
 * priority computation.
 */
public enum IceCandidateType {
    /**
     * A local-machine address obtained directly from the OS network
     * stack — the highest-preference type because no NAT or relay
     * sits between the two endpoints (RFC 8445 §5.1.2.2: 126).
     */
    HOST(126),
    /**
     * A public address learned via a STUN binding from a server-side
     * vantage point (typically the WhatsApp relay's BIND surface) —
     * the address the public Internet sees us on (RFC 8445 §5.1.2.2:
     * 100).
     */
    SERVER_REFLEXIVE(100),
    /**
     * A public address learned via a STUN binding sent from one of
     * our peers during a connectivity check — discovered on demand
     * (RFC 8445 §5.1.2.2: 110).
     */
    PEER_REFLEXIVE(110),
    /**
     * An address allocated on a TURN-style relay, where every byte
     * we ship is forwarded by the relay (RFC 8445 §5.1.2.2: 0).
     */
    RELAYED(0);

    /**
     * Type preference per RFC 8445 §5.1.2.2 (126/110/100/0).
     */
    private final int typePreference;

    /**
     * Constructs a new candidate type with the given preference.
     *
     * @param typePreference the type preference value
     */
    IceCandidateType(int typePreference) {
        this.typePreference = typePreference;
    }

    /**
     * Returns the type-preference value used in candidate-priority
     * computation.
     *
     * @return the type preference
     */
    public int typePreference() {
        return typePreference;
    }
}
