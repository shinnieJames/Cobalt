package com.github.auties00.cobalt.model.chat;

import com.github.auties00.cobalt.model.message.MessageKey;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "CommentMetadata")
public final class ChatCommentMetadata {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey commentParentKey;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT32)
    Integer replyCount;


    ChatCommentMetadata(MessageKey commentParentKey, Integer replyCount) {
        this.commentParentKey = commentParentKey;
        this.replyCount = replyCount;
    }

    public Optional<MessageKey> commentParentKey() {
        return Optional.ofNullable(commentParentKey);
    }

    public OptionalInt replyCount() {
        return replyCount == null ? OptionalInt.empty() : OptionalInt.of(replyCount);
    }

    public void setCommentParentKey(MessageKey commentParentKey) {
        this.commentParentKey = commentParentKey;
    }

    public void setReplyCount(Integer replyCount) {
        this.replyCount = replyCount;
    }
}
