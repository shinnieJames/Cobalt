package com.github.auties00.cobalt.model.setting;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.OptionalInt;
import java.util.OptionalLong;

@ProtobufMessage(name = "EphemeralSetting")
public final class EphemeralSetting {
    @ProtobufProperty(index = 1, type = ProtobufType.SFIXED32)
    Integer duration;

    @ProtobufProperty(index = 2, type = ProtobufType.SFIXED64)
    Long timestamp;


    EphemeralSetting(Integer duration, Long timestamp) {
        this.duration = duration;
        this.timestamp = timestamp;
    }

    public OptionalInt duration() {
        return duration == null ? OptionalInt.empty() : OptionalInt.of(duration);
    }

    public OptionalLong timestamp() {
        return timestamp == null ? OptionalLong.empty() : OptionalLong.of(timestamp);
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
