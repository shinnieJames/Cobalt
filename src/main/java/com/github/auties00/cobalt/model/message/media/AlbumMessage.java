package com.github.auties00.cobalt.model.message.media;

import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "Message.AlbumMessage")
public final class AlbumMessage implements ContextualMessage {
    @ProtobufProperty(index = 2, type = ProtobufType.UINT32)
    Integer expectedImageCount;

    @ProtobufProperty(index = 3, type = ProtobufType.UINT32)
    Integer expectedVideoCount;

    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;


    AlbumMessage(Integer expectedImageCount, Integer expectedVideoCount, ContextInfo contextInfo) {
        this.expectedImageCount = expectedImageCount;
        this.expectedVideoCount = expectedVideoCount;
        this.contextInfo = contextInfo;
    }

    public OptionalInt expectedImageCount() {
        return expectedImageCount == null ? OptionalInt.empty() : OptionalInt.of(expectedImageCount);
    }

    public OptionalInt expectedVideoCount() {
        return expectedVideoCount == null ? OptionalInt.empty() : OptionalInt.of(expectedVideoCount);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public void setExpectedImageCount(Integer expectedImageCount) {
        this.expectedImageCount = expectedImageCount;
    }

    public void setExpectedVideoCount(Integer expectedVideoCount) {
        this.expectedVideoCount = expectedVideoCount;
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }
}
