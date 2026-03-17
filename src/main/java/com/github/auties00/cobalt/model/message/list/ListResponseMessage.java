package com.github.auties00.cobalt.model.message.list;

import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.ListResponseMessage")
public final class ListResponseMessage implements ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String title;

    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    ListType listType;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    SingleSelectReply singleSelectReply;

    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    String description;


    ListResponseMessage(String title, ListType listType, SingleSelectReply singleSelectReply, ContextInfo contextInfo, String description) {
        this.title = title;
        this.listType = listType;
        this.singleSelectReply = singleSelectReply;
        this.contextInfo = contextInfo;
        this.description = description;
    }

    public Optional<String> title() {
        return Optional.ofNullable(title);
    }

    public Optional<ListType> listType() {
        return Optional.ofNullable(listType);
    }

    public Optional<SingleSelectReply> singleSelectReply() {
        return Optional.ofNullable(singleSelectReply);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setListType(ListType listType) {
        this.listType = listType;
    }

    public void setSingleSelectReply(SingleSelectReply singleSelectReply) {
        this.singleSelectReply = singleSelectReply;
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @ProtobufEnum(name = "Message.ListResponseMessage.ListType")
    public static enum ListType {
        UNKNOWN(0),
        SINGLE_SELECT(1);

        ListType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufMessage(name = "Message.ListResponseMessage.SingleSelectReply")
    public static final class SingleSelectReply {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String selectedRowId;


        SingleSelectReply(String selectedRowId) {
            this.selectedRowId = selectedRowId;
        }

        public Optional<String> selectedRowId() {
            return Optional.ofNullable(selectedRowId);
        }

        public void setSelectedRowId(String selectedRowId) {
            this.selectedRowId = selectedRowId;
    }
    }
}
