package com.github.auties00.cobalt.call.internal.transport.ice;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * Represents one ICE candidate, the transport-address tuple defined by RFC 8445 section 5.1.
 *
 * <p>A candidate bundles the transport address datagrams actually flow over, the base address it
 * was learned from, the candidate {@link IceCandidateType type}, the {@link IceComponent component}
 * it serves, a foundation string grouping candidates of common network provenance, and a
 * local-preference tie-breaker. {@link #priority()} folds the type, the local preference, and the
 * component id into the single ordering key the agent uses to rank candidate pairs.
 *
 * <p>The priority is the RFC 8445 section 5.1.2.1 formula:
 *
 * {@snippet :
 *   priority = (2^24 * type_preference)
 *            + (2^8  * local_preference)
 *            + (2^0  * (256 - component_id));
 * }
 *
 * @param type             the candidate type, which supplies the {@code type_preference} term of
 *                         the priority formula
 * @param component        the RTP or RTCP component this candidate serves
 * @param transportAddress the {@code (host, port)} datagrams are sent from and received on; for a
 *                         {@link IceCandidateType#HOST} candidate this is the local NIC address,
 *                         and for a {@link IceCandidateType#RELAYED} candidate it is the
 *                         relay-allocated address
 * @param baseAddress      the base address for a non-host candidate, namely the local address the
 *                         candidate was learned from; equal to {@code transportAddress} for a host
 *                         candidate
 * @param foundation       the foundation string per RFC 8445 section 5.1.1.3, identifying
 *                         candidates that share network provenance (same base, same type, and same
 *                         STUN server)
 * @param localPreference  the local-preference term of the priority formula, in the range
 *                         {@code 0} to {@code 65535}, ordering multiple candidates of the same type
 *                         such as several IPv4 NICs
 */
public record IceCandidate(
        IceCandidateType type,
        IceComponent component,
        InetSocketAddress transportAddress,
        InetSocketAddress baseAddress,
        String foundation,
        int localPreference
) {
    /**
     * The default local preference assigned to the single candidate of each type the basic agent
     * gathers.
     *
     * @implNote This implementation uses {@code 65535}, the largest 16-bit value, following the
     * RFC 8445 section 5.1.2.1 convention that the highest local preference denotes the preferred
     * network of its kind.
     */
    public static final int DEFAULT_LOCAL_PREFERENCE = 65535;

    /**
     * Validates the components, rejecting {@code null} or empty fields and out-of-range local
     * preferences.
     *
     * @throws NullPointerException     if any reference component is {@code null}
     * @throws IllegalArgumentException if {@code foundation} is empty or {@code localPreference} is
     *                                  outside {@code [0, 65535]}
     */
    public IceCandidate {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(component, "component cannot be null");
        Objects.requireNonNull(transportAddress, "transportAddress cannot be null");
        Objects.requireNonNull(baseAddress, "baseAddress cannot be null");
        Objects.requireNonNull(foundation, "foundation cannot be null");
        if (foundation.isEmpty()) {
            throw new IllegalArgumentException("foundation cannot be empty");
        }
        if (localPreference < 0 || localPreference > 0xFFFF) {
            throw new IllegalArgumentException(
                    "localPreference out of range [0, 65535]: " + localPreference);
        }
    }

    /**
     * Creates a {@link IceCandidateType#HOST} candidate whose base address equals its transport
     * address.
     *
     * <p>The candidate is built at {@link #DEFAULT_LOCAL_PREFERENCE} since the basic gathering pass
     * emits one host candidate per address family per interface.
     *
     * @param component  the RTP or RTCP component the candidate serves
     * @param address    the local NIC transport address, used for both the transport and base
     *                   address
     * @param foundation the foundation string, typically the NIC name combined with the IP family
     * @return a new host candidate at the default local preference
     */
    public static IceCandidate host(IceComponent component, InetSocketAddress address, String foundation) {
        return new IceCandidate(IceCandidateType.HOST, component, address, address,
                foundation, DEFAULT_LOCAL_PREFERENCE);
    }

    /**
     * Creates a {@link IceCandidateType#RELAYED} candidate.
     *
     * <p>The candidate is built at {@link #DEFAULT_LOCAL_PREFERENCE}. The transport address is the
     * relay-allocated address the peer sends datagrams to, while the base address is the local
     * socket the relay is reached through.
     *
     * @param component   the RTP or RTCP component the candidate serves
     * @param relayedAddr the relay-allocated address the peer sends datagrams to
     * @param baseAddr    the local socket the relay is reached through
     * @param foundation  the foundation string, typically the relay's STUN server identifier
     * @return a new relayed candidate at the default local preference
     */
    public static IceCandidate relayed(IceComponent component, InetSocketAddress relayedAddr,
                                       InetSocketAddress baseAddr, String foundation) {
        return new IceCandidate(IceCandidateType.RELAYED, component, relayedAddr, baseAddr,
                foundation, DEFAULT_LOCAL_PREFERENCE);
    }

    /**
     * Creates a {@link IceCandidateType#SERVER_REFLEXIVE} candidate.
     *
     * <p>The candidate is built at {@link #DEFAULT_LOCAL_PREFERENCE}. The transport address is the
     * mapped address the STUN server reported, while the base address is the local socket the STUN
     * server was reached through.
     *
     * @param component  the RTP or RTCP component the candidate serves
     * @param mappedAddr the mapped address the STUN server reported
     * @param baseAddr   the local socket the STUN server was reached through
     * @param foundation the foundation string, typically the STUN server identifier
     * @return a new server-reflexive candidate at the default local preference
     */
    public static IceCandidate serverReflexive(IceComponent component, InetSocketAddress mappedAddr,
                                               InetSocketAddress baseAddr, String foundation) {
        return new IceCandidate(IceCandidateType.SERVER_REFLEXIVE, component, mappedAddr, baseAddr,
                foundation, DEFAULT_LOCAL_PREFERENCE);
    }

    /**
     * Computes the candidate's RFC 8445 section 5.1.2.1 priority.
     *
     * <p>The result combines the {@link IceCandidateType#typePreference() type preference} shifted
     * left by 24 bits, the {@link #localPreference()} shifted left by 8 bits, and {@code 256} minus
     * the {@link IceComponent#componentId() component id}. It is non-negative and fits in a
     * {@code long}, matching the 32-bit unsigned range of the SDP {@code priority} attribute.
     *
     * @return the candidate priority
     */
    public long priority() {
        var typePref = (long) type.typePreference();
        var localPref = (long) localPreference;
        long component = component().componentId();
        return (typePref << 24) + (localPref << 8) + (256 - component);
    }
}
