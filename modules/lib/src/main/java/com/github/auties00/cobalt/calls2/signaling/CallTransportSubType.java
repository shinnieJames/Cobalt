package com.github.auties00.cobalt.calls2.signaling;

import java.util.Optional;

/**
 * Enumerates the inner sub-message types carried by a Transport signaling message.
 *
 * <p>A Transport message (the type-{@code 6} entry in the call signaling taxonomy) is a
 * container: every Transport message carries an inner sub-type byte that selects which
 * transport payload it conveys. The defined sub-types are the implicit remote candidate
 * ({@link #REMOTE_CANDIDATE}, which is sent with no explicit sub-type byte), the full
 * local candidate list ({@link #CANDIDATE_LIST}), the negotiated transport protocol and
 * network medium ({@link #TRANSPORT_PROTOCOL}), the relay-latency probe
 * ({@link #RELAY_LATENCY}), the peer network-health status ({@link #PEER_HEALTH}), and
 * the web-peer-to-peer ICE/DTLS handshake parameters ({@link #ICE_DTLS}).
 *
 * <p>Each constant carries the {@link #wireValue() wire value} the engine stamps into
 * the Transport message. The value {@code 14} ({@code 0xe}) is a reserved sentinel that
 * is never a real sub-type: the engine's type-only Transport sender explicitly refuses
 * to send a message whose sub-type is {@code 0} or {@code 14}, so {@code 14} marks an
 * absent or invalid sub-type rather than a payload kind and has no constant here.
 *
 * @implNote This implementation ports the Transport sub-type dispatch in the wa-voip
 * WASM module {@code ff-tScznZ8P} ({@code messages/handlers/transport.cc}). The wire
 * values are taken from the per-sub-type senders: the candidate-list sender writes
 * sub-type {@code 1}, the transport-protocol sender writes sub-type {@code 4}, and the
 * peer-network-health sender writes sub-type {@code 0xb}; the remote-candidate path
 * carries no explicit sub-type (treated as {@code 0}); the relay-latency ({@code 9}) and
 * ICE/DTLS ({@code 13}) sub-types are taken from {@code handle_transport}. The reserved
 * {@code 0xe} sentinel is the guard value {@code sub_type != 0 && sub_type != 0xe} in the
 * type-only Transport sender.
 */
public enum CallTransportSubType {
    /**
     * Conveys a single remote ICE candidate.
     *
     * <p>This sub-type is the implicit default: a Transport message carrying a remote
     * candidate is sent with no explicit sub-type byte, which the engine reads back as
     * the zero sub-type.
     */
    REMOTE_CANDIDATE(0),

    /**
     * Conveys the full list of local ICE candidates gathered by this device.
     *
     * <p>The engine sends this once the peer-to-peer transport is ready and the device is
     * not held in a waiting room, bundling every local candidate plus server-reflexive
     * port information into one Transport message.
     */
    CANDIDATE_LIST(1),

    /**
     * Conveys the negotiated transport protocol and network medium.
     *
     * <p>The payload tells the peer which transport protocol this device selected and
     * over which network medium it is operating.
     */
    TRANSPORT_PROTOCOL(4),

    /**
     * Conveys a relay-latency probe used to elect the lowest-latency relay.
     */
    RELAY_LATENCY(9),

    /**
     * Conveys the peer network-health status.
     *
     * <p>The payload reports the measured network-health status to the peer, for example
     * when a relay is unbound or no relay has been elected.
     */
    PEER_HEALTH(11),

    /**
     * Conveys the web-peer-to-peer ICE/DTLS handshake parameters.
     *
     * <p>The payload carries the peer-to-peer ufrag, password, and DTLS fingerprint used
     * to establish a direct browser-to-browser media path.
     */
    ICE_DTLS(13);

    /**
     * The reserved sentinel sub-type value the engine refuses to send.
     *
     * <p>The type-only Transport sender rejects a sub-type of {@code 0} or this value, so
     * {@code 0xe} denotes an absent or invalid sub-type rather than a payload kind.
     */
    private static final int RESERVED_SENTINEL = 0xe;

    /**
     * Caches the constant array so the per-message {@link #ofWireValue(int)} decode scan does not pay
     * the defensive-clone cost of {@link #values()} on every Transport message parsed.
     */
    private static final CallTransportSubType[] VALUES = values();

    /**
     * The integer value the wa-voip engine stamps into the Transport message for this
     * sub-type.
     */
    private final int wireValue;

    /**
     * Constructs a sub-type constant bound to its engine wire value.
     *
     * @param wireValue the integer value the engine uses for this sub-type
     */
    CallTransportSubType(int wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the integer value the wa-voip engine stamps into the Transport message for
     * this sub-type.
     *
     * @return the engine wire value for this sub-type
     */
    public int wireValue() {
        return wireValue;
    }

    /**
     * Returns the sub-type whose {@linkplain #wireValue() wire value} equals the given
     * value.
     *
     * <p>The result is empty for any value that does not correspond to a defined
     * sub-type, including the reserved {@code 0xe} sentinel the engine never sends.
     *
     * @param wireValue the engine wire value to resolve
     * @return the matching sub-type, or {@link Optional#empty()} if no sub-type matches
     */
    public static Optional<CallTransportSubType> ofWireValue(int wireValue) {
        if (wireValue == RESERVED_SENTINEL) {
            return Optional.empty();
        }
        for (var subType : VALUES) {
            if (subType.wireValue == wireValue) {
                return Optional.of(subType);
            }
        }
        return Optional.empty();
    }
}
