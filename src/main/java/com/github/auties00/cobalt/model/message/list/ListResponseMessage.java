package com.github.auties00.cobalt.model.message.list;

import com.github.auties00.cobalt.model.message.ContextInfo;
import com.github.auties00.cobalt.model.message.ContextualMessage;

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

    public ListResponseMessage setTitle(String title) {
        this.title = title;
        return this;
    }

    public ListResponseMessage setListType(ListType listType) {
        this.listType = listType;
        return this;
    }

    public ListResponseMessage setSingleSelectReply(SingleSelectReply singleSelectReply) {
        this.singleSelectReply = singleSelectReply;
        return this;
    }

    public ListResponseMessage setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
        return this;
    }

    public ListResponseMessage setDescription(String description) {
        this.description = description;
        return this;
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

        public SingleSelectReply setSelectedRowId(String selectedRowId) {
            this.selectedRowId = selectedRowId;
            return this;
        }
    }
}
