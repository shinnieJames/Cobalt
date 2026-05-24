package com.github.auties00.cobalt.registration.push.fcm.mcs;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

/**
 * Wire shape for an outbound heartbeat ack (MCS frame tag {@code 1}).
 *
 * @apiNote
 * Sent in response to an incoming server ping; carries the same
 * stream cursor as {@link FcmMcsHeartbeatPing} plus a status field
 * the native client always sets to {@code 0}.
 */
@ProtobufMessage(name = "FcmMcsHeartbeatAck")
public final class FcmMcsHeartbeatAck {
    /**
     * Last stream id the client has observed.
     *
     * @apiNote
     * Lets the server infer the cursor without parsing any stanza
     * payload.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.INT64)
    long lastStreamIdReceived;

    /**
     * Heartbeat status; the native client always sends {@code 0}.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.INT64)
    long status;

    /**
     * Constructs a new ack with the given values.
     *
     * @param lastStreamIdReceived the last stream id observed
     * @param status               the heartbeat status
     */
    FcmMcsHeartbeatAck(long lastStreamIdReceived, long status) {
        this.lastStreamIdReceived = lastStreamIdReceived;
        this.status = status;
    }
}
