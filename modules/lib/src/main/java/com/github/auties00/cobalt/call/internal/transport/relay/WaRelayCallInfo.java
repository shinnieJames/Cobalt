package com.github.auties00.cobalt.call.internal.transport.relay;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.List;

/**
 * Models the protobuf payload of the {@link WaRelayAttributeType#WA_CALL_INFO} ({@code 0x4024})
 * attribute carried inside a {@link WaRelayPacket}.
 *
 * <p>The call engine emits one of these per Allocate Request to tell the relay how it has prioritised
 * every (IP version, relay) candidate from the {@code RelayListUpdate}. A captured 95-byte payload
 * from a 3-relay session decoded as nine {@link WaRelayCallInfoEntry} entries, one per relay across
 * the three IP-version axes (any, IPv4, IPv6), each carrying an ICE candidate priority.
 *
 * @implNote This implementation infers the field name and wire index from the observed packet
 * pattern: the underlying schema lives inside the wasm engine and is not exposed in the JS source.
 * Index {@code 1} is pinned by the captured byte stream and round-trips byte-exact.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSctpConnectionManager")
@ProtobufMessage(name = "WaRelayCallInfo")
public final class WaRelayCallInfo {
    /**
     * Holds the per-candidate priority entries.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    final List<WaRelayCallInfoEntry> entries;

    /**
     * Constructs a payload from its entry list, invoked by the generated builder and the deserializer.
     *
     * @param entries the per-candidate priority list
     */
    WaRelayCallInfo(List<WaRelayCallInfoEntry> entries) {
        this.entries = entries;
    }

    /**
     * Returns the unmodifiable list of per-candidate priority entries in declaration order.
     *
     * @return the entries
     */
    public List<WaRelayCallInfoEntry> entries() {
        return entries;
    }
}
