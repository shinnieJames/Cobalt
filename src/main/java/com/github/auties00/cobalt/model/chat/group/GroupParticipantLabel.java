package com.github.auties00.cobalt.model.chat.group;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Optional;

@ProtobufMessage(name = "MemberLabel")
public final class GroupParticipantLabel {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String label;

    @ProtobufProperty(index = 2, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant labelTimestamp;


    GroupParticipantLabel(String label, Instant labelTimestamp) {
        this.label = label;
        this.labelTimestamp = labelTimestamp;
    }

    public Optional<String> label() {
        return Optional.ofNullable(label);
    }

    public Optional<Instant> labelTimestamp() {
        return Optional.ofNullable(labelTimestamp);
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setLabelTimestamp(Instant labelTimestamp) {
        this.labelTimestamp = labelTimestamp;
    }
}
