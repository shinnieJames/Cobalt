package com.github.auties00.cobalt.call.transport.relay;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

/**
 * Models one ICE-candidate priority entry inside a {@link WaRelayCallInfo}.
 *
 * <p>Captured Allocate Requests carry a nine-entry list that enumerates every (IP version, relay)
 * pairing the engine has computed a priority for, along these axes:
 *
 * <ul>
 *   <li>{@link #ipVersion()} reports {@code 0} (or absent) for any IP version, {@code 1} for IPv4,
 *       and {@code 2} for IPv6.</li>
 *   <li>{@link #relayId()} reports {@code 0} (or absent) for the primary relay; a non-zero value
 *       indexes into the {@code RelayListUpdate.relays[]} array delivered on the engine event
 *       stream.</li>
 *   <li>{@link #priority()} reports the 32-bit ICE candidate priority, carried as a varint so the
 *       wire form stays compact for low-priority candidates.</li>
 * </ul>
 *
 * <p>The {@code ipVersion} and {@code relayId} fields are stored as nullable {@link Integer} so the
 * encoder can omit zero values from the wire.
 *
 * @implNote This implementation infers the field names and wire indices from the observed packet
 * pattern: the underlying schema lives inside the wasm engine and is not exposed in the JS source, so
 * the names are conservative. Storing {@code ipVersion} and {@code relayId} as nullable
 * {@link Integer} reproduces the proto3 default-omission behaviour seen in the captured stream, where
 * zero values are absent rather than encoded. The indices are pinned by the captured byte stream and
 * round-trip byte-exact.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSctpConnectionManager")
@ProtobufMessage(name = "WaRelayCallInfoEntry")
public final class WaRelayCallInfoEntry {
    /**
     * Holds the IP-version axis: {@code 0} or absent for any, {@code 1} for IPv4, {@code 2} for IPv6;
     * {@code null} encodes as omitted on the wire.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.INT32)
    final Integer ipVersion;

    /**
     * Holds the relay-index axis: {@code 0} or absent for the primary relay, otherwise the index into
     * {@code RelayListUpdate.relays[]}; {@code null} encodes as omitted on the wire.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.INT32)
    final Integer relayId;

    /**
     * Holds the ICE candidate priority for this (IP version, relay) pairing, carried as a varint so
     * values up to {@code 2^32 - 1} fit in five bytes.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.INT64)
    final long priority;

    /**
     * Constructs an entry from its three axes, invoked by the generated builder and the deserializer.
     *
     * @param ipVersion the IP-version axis, or {@code null} to omit it on the wire
     * @param relayId   the relay-index axis, or {@code null} to omit it on the wire
     * @param priority  the ICE candidate priority
     */
    WaRelayCallInfoEntry(Integer ipVersion, Integer relayId, long priority) {
        this.ipVersion = ipVersion;
        this.relayId = relayId;
        this.priority = priority;
    }

    /**
     * Returns the IP-version axis, collapsing {@code null} to {@code 0}.
     *
     * @return {@code 0} for any IP version, {@code 1} for IPv4, {@code 2} for IPv6
     */
    public int ipVersion() {
        return ipVersion == null ? 0 : ipVersion;
    }

    /**
     * Returns the relay-index axis, collapsing {@code null} to {@code 0}.
     *
     * @return {@code 0} for the primary relay, otherwise the index into
     *         {@code RelayListUpdate.relays[]}
     */
    public int relayId() {
        return relayId == null ? 0 : relayId;
    }

    /**
     * Returns the ICE candidate priority for this entry.
     *
     * @return the priority varint
     */
    public long priority() {
        return priority;
    }
}
