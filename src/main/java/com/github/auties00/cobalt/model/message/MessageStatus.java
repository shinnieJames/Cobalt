package com.github.auties00.cobalt.model.message;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

@ProtobufEnum(name = "WebMessageInfo.Status")
public enum MessageStatus {
    /**
     * Erroneous status(no ticks)
     */
    ERROR(0),
    /**
     * Pending status(no ticks)
     */
    PENDING(1),
    /**
     * Acknowledged by the server(one tick)
     */
    SERVER_ACK(2),
    /**
     * Delivered(two ticks)
     */
    DELIVERED(3),
    /**
     * Read(two blue ticks)
     */
    READ(4),
    /**
     * Played(two blue ticks)
     */
    PLAYED(5);

    final int index;

    MessageStatus(@ProtobufEnumIndex int index) {
        this.index = index;
    }
}
