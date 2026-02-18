package com.github.auties00.cobalt.model.sync.action.business;

import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage(name = "SyncActionValue.BusinessBroadcastAssociationAction")
public final class BusinessBroadcastAssociationAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean deleted;


    BusinessBroadcastAssociationAction(Boolean deleted) {
        this.deleted = deleted;
    }

    public boolean deleted() {
        return deleted != null && deleted;
    }

    public BusinessBroadcastAssociationAction setDeleted(Boolean deleted) {
        this.deleted = deleted;
        return this;
    }
}
