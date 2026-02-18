package com.github.auties00.cobalt.model.bot.feedback;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

/**
 * The type of transparency notice displayed to the user during an AI bot session.
 */
@ProtobufEnum(name = "SessionTransparencyType")
public enum SessionTransparencyType {
    /**
     * The transparency type is unknown or unspecified.
     */
    UNKNOWN_TYPE(0),

    /**
     * A New York State AI safety disclaimer, required by local regulations.
     */
    NEW_YORK_AI_SAFETY_DISCLAIMER(1);

    SessionTransparencyType(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    /**
     * The protobuf index of this enum constant.
     */
    final int index;

    /**
     * Returns the protobuf index of this enum constant.
     *
     * @return the protobuf index
     */
    public int index() {
        return this.index;
    }
}
