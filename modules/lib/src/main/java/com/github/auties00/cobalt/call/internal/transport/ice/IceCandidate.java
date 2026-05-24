package com.github.auties00.cobalt.call.internal.transport.ice;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * One ICE candidate — a {@code (transport address, type, base, etc.)}
 * tuple per RFC 8445 §5.1.
 *
 * <p>The {@link #priority()} is the standard RFC 8445 §5.1.2.1
 * formula:
 *
 * <pre>{@code
 *   priority = (2^24 * type_preference)
 *            + (2^8  * local_preference)
 *            + (2^0  * (256 - component_id))
 * }</pre>
 *
 * @param type             the candidate type — drives the
 *                         {@code type_preference}
 * @param component        which RTP/RTCP component this candidate
 *                         serves
 * @param transportAddress the {@code (host, port)} datagrams are
 *                         actually sent / received on; for HOST
 *                         candidates this is the local NIC; for
 *                         RELAYED it's the relay-allocated address
 * @param baseAddress      the base address for non-HOST candidates —
 *                         the local address from which the candidate
 *                         was learned. Equal to {@code transportAddress}
 *                         for HOST candidates
 * @param foundation       the foundation string per RFC 8445 §5.1.1.3,
 *                         identifying candidates that share network
 *                         provenance (same-base + same-type + same-stun
 *                         server)
 * @param localPreference  the local-preference component of the
 *                         priority formula (0..65535) — orders
 *                         multiple candidates of the same type, e.g.
 *                         multiple IPv4 NICs
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
     * Default local preference for the single candidate per type
     * gathered by the basic agent (RFC 8445 §5.1.2.1 hint: 65535
     * means "preferred network of its kind").
     */
    public static final int DEFAULT_LOCAL_PREFERENCE = 65535;

    /**
     * Compact constructor — null-checks fields and validates ranges.
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
     * Convenience factory for a HOST candidate, where
     * {@link #baseAddress()} equals {@link #transportAddress()}.
     *
     * @param component     the RTP/RTCP component
     * @param address       the local NIC's transport address
     * @param foundation    the foundation string (typically the NIC
     *                      name + IP family)
     * @return a new HOST candidate at default local preference
     */
    public static IceCandidate host(IceComponent component, InetSocketAddress address, String foundation) {
        return new IceCandidate(IceCandidateType.HOST, component, address, address,
                foundation, DEFAULT_LOCAL_PREFERENCE);
    }

    /**
     * Convenience factory for a RELAYED candidate.
     *
     * @param component     the RTP/RTCP component
     * @param relayedAddr   the relay-allocated address (the address
     *                      the peer will send datagrams to)
     * @param baseAddr      the local socket from which we reach the
     *                      relay
     * @param foundation    the foundation string (typically the
     *                      relay's STUN server identifier)
     * @return a new RELAYED candidate at default local preference
     */
    public static IceCandidate relayed(IceComponent component, InetSocketAddress relayedAddr,
                                       InetSocketAddress baseAddr, String foundation) {
        return new IceCandidate(IceCandidateType.RELAYED, component, relayedAddr, baseAddr,
                foundation, DEFAULT_LOCAL_PREFERENCE);
    }

    /**
     * Convenience factory for a SERVER_REFLEXIVE candidate.
     *
     * @param component   the RTP/RTCP component
     * @param mappedAddr  the address the STUN server reported
     * @param baseAddr    the local socket from which we reached the
     *                    STUN server
     * @param foundation  the foundation string (typically the STUN
     *                    server identifier)
     * @return a new SERVER_REFLEXIVE candidate at default local
     *         preference
     */
    public static IceCandidate serverReflexive(IceComponent component, InetSocketAddress mappedAddr,
                                               InetSocketAddress baseAddr, String foundation) {
        return new IceCandidate(IceCandidateType.SERVER_REFLEXIVE, component, mappedAddr, baseAddr,
                foundation, DEFAULT_LOCAL_PREFERENCE);
    }

    /**
     * Computes the candidate's RFC 8445 §5.1.2.1 priority. The result
     * is non-negative and fits in a {@code long} (32-bit unsigned in
     * the SDP "priority" attribute).
     *
     * @return the priority
     */
    public long priority() {
        var typePref = (long) type.typePreference();
        var localPref = (long) localPreference;
        long component = component().componentId();
        return (typePref << 24) + (localPref << 8) + (256 - component);
    }
}
