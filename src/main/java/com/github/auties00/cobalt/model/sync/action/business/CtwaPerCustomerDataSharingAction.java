package com.github.auties00.cobalt.model.sync.action.business;

import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage(name = "SyncActionValue.CtwaPerCustomerDataSharingAction")
public final class CtwaPerCustomerDataSharingAction implements SyncAction<CtwaPerCustomerDataSharingActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "ctwaPerCustomerDataSharing";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 1;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR_HIGH;

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int actionVersion() {
        return ACTION_VERSION;
    }


    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean isCtwaPerCustomerDataSharingEnabled;


    CtwaPerCustomerDataSharingAction(Boolean isCtwaPerCustomerDataSharingEnabled) {
        this.isCtwaPerCustomerDataSharingEnabled = isCtwaPerCustomerDataSharingEnabled;
    }

    public boolean isCtwaPerCustomerDataSharingEnabled() {
        return isCtwaPerCustomerDataSharingEnabled != null && isCtwaPerCustomerDataSharingEnabled;
    }

    public void setCtwaPerCustomerDataSharingEnabled(Boolean isCtwaPerCustomerDataSharingEnabled) {
        this.isCtwaPerCustomerDataSharingEnabled = isCtwaPerCustomerDataSharingEnabled;
    }


}
