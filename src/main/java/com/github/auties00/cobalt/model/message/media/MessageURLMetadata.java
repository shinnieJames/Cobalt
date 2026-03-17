package com.github.auties00.cobalt.model.message.media;

import com.github.auties00.cobalt.model.message.Message;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.OptionalInt;

@ProtobufMessage(name = "Message.URLMetadata")
public final class MessageURLMetadata implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
    Integer fbExperimentId;


    MessageURLMetadata(Integer fbExperimentId) {
        this.fbExperimentId = fbExperimentId;
    }

    public OptionalInt fbExperimentId() {
        return fbExperimentId == null ? OptionalInt.empty() : OptionalInt.of(fbExperimentId);
    }

    public void setFbExperimentId(Integer fbExperimentId) {
        this.fbExperimentId = fbExperimentId;
    }
}
