package com.github.auties00.cobalt.call.internal.transport.relay;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.List;

/**
 * Protobuf payload of the {@link WaRelayAttributeType#WA_CALL_INFO}
 * (0x4024) attribute carried inside an {@link WaRelayPacket}.
 *
 * <p>The wasm engine emits one of these per Allocate Request to tell
 * the relay how it has prioritised every (IP version, relay) candidate
 * in the {@code RelayListUpdate}. The captured 95-byte payload from a
 * 3-relay session decoded as nine entries — {@code 3 relays × 3 IP
 * versions (any, IPv4, IPv6)} — each carrying an ICE candidate
 * priority. See {@link WaRelayCallInfoEntry} for the per-entry shape.
 *
 * <p>Field names are inferred from the observed packet pattern: the
 * underlying schema lives inside the wasm engine and is not exposed in
 * the JS source. The wire index ({@code 1}) is pinned by the captured
 * byte stream and round-trips byte-exact.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSctpConnectionManager")
@ProtobufMessage(name = "WaRelayCallInfo")
public final class WaRelayCallInfo {
    /**
     * The list of per-candidate priority entries.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    final List<WaRelayCallInfoEntry> entries;

    /**
     * Full protobuf constructor invoked by the generated builder and
     * the deserializer.
     *
     * @param entries the per-candidate priority list
     */
    WaRelayCallInfo(List<WaRelayCallInfoEntry> entries) {
        this.entries = entries;
    }

    /**
     * Returns the unmodifiable list of per-candidate priority entries.
     *
     * @return the entries in declaration order
     */
    public List<WaRelayCallInfoEntry> entries() {
        return entries;
    }
}
