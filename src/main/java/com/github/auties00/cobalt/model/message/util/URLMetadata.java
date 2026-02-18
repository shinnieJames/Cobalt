package com.github.auties00.cobalt.model.message.util;

import com.github.auties00.cobalt.model.message.Message;

import java.util.OptionalInt;

@ProtobufMessage(name = "Message.URLMetadata")
public final class URLMetadata implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
    Integer fbExperimentId;


    URLMetadata(Integer fbExperimentId) {
        this.fbExperimentId = fbExperimentId;
    }

    public OptionalInt fbExperimentId() {
        return fbExperimentId == null ? OptionalInt.empty() : OptionalInt.of(fbExperimentId);
    }

    public URLMetadata setFbExperimentId(Integer fbExperimentId) {
        this.fbExperimentId = fbExperimentId;
        return this;
    }
}
