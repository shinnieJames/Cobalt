package com.github.auties00.cobalt.model.message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "MessageAssociation")
public final class MessageAssociation {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    AssociationType associationType;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    MessageKey parentMessageKey;

    @ProtobufProperty(index = 3, type = ProtobufType.INT32)
    Integer messageIndex;


    MessageAssociation(AssociationType associationType, MessageKey parentMessageKey, Integer messageIndex) {
        this.associationType = associationType;
        this.parentMessageKey = parentMessageKey;
        this.messageIndex = messageIndex;
    }

    public Optional<AssociationType> associationType() {
        return Optional.ofNullable(associationType);
    }

    public Optional<MessageKey> parentMessageKey() {
        return Optional.ofNullable(parentMessageKey);
    }

    public OptionalInt messageIndex() {
        return messageIndex == null ? OptionalInt.empty() : OptionalInt.of(messageIndex);
    }

    public void setAssociationType(AssociationType associationType) {
        this.associationType = associationType;
    }

    public void setParentMessageKey(MessageKey parentMessageKey) {
        this.parentMessageKey = parentMessageKey;
    }

    public void setMessageIndex(Integer messageIndex) {
        this.messageIndex = messageIndex;
    }

    @ProtobufEnum(name = "MessageAssociation.AssociationType")
    public static enum AssociationType {
        UNKNOWN(0),
        MEDIA_ALBUM(1),
        BOT_PLUGIN(2),
        EVENT_COVER_IMAGE(3),
        STATUS_POLL(4),
        HD_VIDEO_DUAL_UPLOAD(5),
        STATUS_EXTERNAL_RESHARE(6),
        MEDIA_POLL(7),
        STATUS_ADD_YOURS(8),
        STATUS_NOTIFICATION(9),
        HD_IMAGE_DUAL_UPLOAD(10),
        STICKER_ANNOTATION(11),
        MOTION_PHOTO(12),
        STATUS_LINK_ACTION(13),
        VIEW_ALL_REPLIES(14),
        STATUS_ADD_YOURS_AI_IMAGINE(15),
        STATUS_QUESTION(16),
        STATUS_ADD_YOURS_DIWALI(17),
        STATUS_REACTION(18),
        HEVC_VIDEO_DUAL_UPLOAD(19);

        AssociationType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
