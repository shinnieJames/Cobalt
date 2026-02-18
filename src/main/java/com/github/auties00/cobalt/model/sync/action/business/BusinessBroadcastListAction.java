package com.github.auties00.cobalt.model.sync.action.business;

import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.BusinessBroadcastListAction")
public final class BusinessBroadcastListAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean deleted;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    List<BroadcastListParticipant> participants;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String listName;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    List<String> labelIds;


    BusinessBroadcastListAction(Boolean deleted, List<BroadcastListParticipant> participants, String listName, List<String> labelIds) {
        this.deleted = deleted;
        this.participants = participants;
        this.listName = listName;
        this.labelIds = labelIds;
    }

    public boolean deleted() {
        return deleted != null && deleted;
    }

    public List<BroadcastListParticipant> participants() {
        return participants == null ? List.of() : Collections.unmodifiableList(participants);
    }

    public Optional<String> listName() {
        return Optional.ofNullable(listName);
    }

    public List<String> labelIds() {
        return labelIds == null ? List.of() : Collections.unmodifiableList(labelIds);
    }

    public BusinessBroadcastListAction setDeleted(Boolean deleted) {
        this.deleted = deleted;
        return this;
    }

    public BusinessBroadcastListAction setParticipants(List<BroadcastListParticipant> participants) {
        this.participants = participants;
        return this;
    }

    public BusinessBroadcastListAction setListName(String listName) {
        this.listName = listName;
        return this;
    }

    public BusinessBroadcastListAction setLabelIds(List<String> labelIds) {
        this.labelIds = labelIds;
        return this;
    }
}
