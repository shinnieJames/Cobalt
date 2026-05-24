package com.github.auties00.cobalt.registration.push.fcm.mcs;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

/**
 * Wire shape for both heartbeat directions.
 *
 * @apiNote
 * MCS frame tag {@code 0} (ping) is sent outbound on the 10-minute
 * timer and inbound from the server; the server may also send tag
 * {@code 1} (ack), which uses the same field shape via
 * {@link FcmMcsHeartbeatAck}.
 */
@ProtobufMessage(name = "FcmMcsHeartbeatPing")
public final class FcmMcsHeartbeatPing {
    /**
     * Last stream id the sender has observed.
     *
     * @apiNote
     * Lets the peer infer the cursor without parsing any stanza
     * payload.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.INT64)
    long lastStreamIdReceived;

    /**
     * Constructs a new ping.
     *
     * @param lastStreamIdReceived the last stream id observed
     */
    FcmMcsHeartbeatPing(long lastStreamIdReceived) {
        this.lastStreamIdReceived = lastStreamIdReceived;
    }

    /**
     * Returns the last stream id the sender has observed.
     *
     * @return the cursor
     */
    public long lastStreamIdReceived() {
        return lastStreamIdReceived;
    }
}
