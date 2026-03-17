package com.github.auties00.cobalt.model.message;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;

@ProtobufMessage(name = "ThreadID")
public final class MessageThreadId {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    ThreadType threadType;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    MessageKey threadKey;


    MessageThreadId(ThreadType threadType, MessageKey threadKey) {
        this.threadType = threadType;
        this.threadKey = threadKey;
    }

    public Optional<ThreadType> threadType() {
        return Optional.ofNullable(threadType);
    }

    public Optional<MessageKey> threadKey() {
        return Optional.ofNullable(threadKey);
    }

    public void setThreadType(ThreadType threadType) {
        this.threadType = threadType;
    }

    public void setThreadKey(MessageKey threadKey) {
        this.threadKey = threadKey;
    }

    @ProtobufEnum(name = "ThreadID.ThreadType")
    public static enum ThreadType {
        UNKNOWN(0),
        VIEW_REPLIES(1),
        AI_THREAD(2);

        ThreadType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
