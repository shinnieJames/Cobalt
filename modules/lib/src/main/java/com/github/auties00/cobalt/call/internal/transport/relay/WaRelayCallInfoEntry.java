package com.github.auties00.cobalt.call.internal.transport.relay;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

/**
 * One ICE-candidate priority entry inside a {@link WaRelayCallInfo}.
 *
 * <p>Captured Allocate Requests carry a 9-entry list that enumerates
 * every (IP version, relay) pairing the engine has computed a priority
 * for. The grid axes:
 *
 * <ul>
 *   <li>{@link #ipVersion()} — {@code 0} (or absent) means "any IP
 *       version", {@code 1} means IPv4, {@code 2} means IPv6.</li>
 *   <li>{@link #relayId()} — {@code 0} (or absent) means the primary
 *       relay; non-zero values index into the
 *       {@code RelayListUpdate.relays[]} array delivered on the engine
 *       event stream.</li>
 *   <li>{@link #priority()} — the 32-bit ICE candidate priority,
 *       carried as a varint so the wire form stays compact for low
 *       priority candidates.</li>
 * </ul>
 *
 * <p>{@code ipVersion} and {@code relayId} are stored as nullable
 * {@link Integer} so the encoder can omit zero values from the wire,
 * matching proto3 default-omission semantics observed in the captured
 * stream.
 *
 * <p>Field names are inferred from the observed packet pattern: the
 * underlying schema lives inside the wasm engine and is not exposed in
 * the JS source, so the names are conservative. The wire indices are
 * pinned by the captured byte stream and round-trip byte-exact.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSctpConnectionManager")
@ProtobufMessage(name = "WaRelayCallInfoEntry")
public final class WaRelayCallInfoEntry {
    /**
     * The IP-version axis: {@code 0}/absent = any, {@code 1} = IPv4,
     * {@code 2} = IPv6. {@code null} encodes as omitted on the wire.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.INT32)
    final Integer ipVersion;

    /**
     * The relay-index axis: {@code 0}/absent = primary relay, otherwise
     * the index into {@code RelayListUpdate.relays[]}. {@code null}
     * encodes as omitted on the wire.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.INT32)
    final Integer relayId;

    /**
     * The ICE candidate priority for this (ipVersion, relay) pairing.
     * Carried as a varint, so values up to {@code 2^32 - 1} fit in
     * five bytes.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.INT64)
    final long priority;

    /**
     * Full protobuf constructor invoked by the generated builder and
     * the deserializer.
     *
     * @param ipVersion the IP-version axis, or {@code null} to omit
     * @param relayId   the relay-index axis, or {@code null} to omit
     * @param priority  the ICE candidate priority
     */
    WaRelayCallInfoEntry(Integer ipVersion, Integer relayId, long priority) {
        this.ipVersion = ipVersion;
        this.relayId = relayId;
        this.priority = priority;
    }

    /**
     * Returns the IP-version axis, collapsing {@code null} to
     * {@code 0} (the wasm engine treats absent as "any").
     *
     * @return {@code 0} for any, {@code 1} for IPv4, {@code 2} for IPv6
     */
    public int ipVersion() {
        return ipVersion == null ? 0 : ipVersion;
    }

    /**
     * Returns the relay-index axis, collapsing {@code null} to
     * {@code 0} (the wasm engine treats absent as the primary relay).
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
