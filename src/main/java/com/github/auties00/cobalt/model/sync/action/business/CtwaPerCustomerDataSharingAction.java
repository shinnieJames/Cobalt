package com.github.auties00.cobalt.model.sync.action.business;

import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage(name = "SyncActionValue.CtwaPerCustomerDataSharingAction")
public final class CtwaPerCustomerDataSharingAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean isCtwaPerCustomerDataSharingEnabled;


    CtwaPerCustomerDataSharingAction(Boolean isCtwaPerCustomerDataSharingEnabled) {
        this.isCtwaPerCustomerDataSharingEnabled = isCtwaPerCustomerDataSharingEnabled;
    }

    public boolean isCtwaPerCustomerDataSharingEnabled() {
        return isCtwaPerCustomerDataSharingEnabled != null && isCtwaPerCustomerDataSharingEnabled;
    }

    public CtwaPerCustomerDataSharingAction setCtwaPerCustomerDataSharingEnabled(Boolean isCtwaPerCustomerDataSharingEnabled) {
        this.isCtwaPerCustomerDataSharingEnabled = isCtwaPerCustomerDataSharingEnabled;
        return this;
    }
}
