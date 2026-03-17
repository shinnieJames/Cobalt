package com.github.auties00.cobalt.model.sync.action.device;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.OptionalLong;

@ProtobufMessage(name = "SyncActionValue.SubscriptionAction")
public final class SubscriptionAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "subscription";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 7;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR;

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
    Boolean isDeactivated;

    @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
    Boolean isAutoRenewing;

    @ProtobufProperty(index = 3, type = ProtobufType.INT64)
    Long expirationDate;


    SubscriptionAction(Boolean isDeactivated, Boolean isAutoRenewing, Long expirationDate) {
        this.isDeactivated = isDeactivated;
        this.isAutoRenewing = isAutoRenewing;
        this.expirationDate = expirationDate;
    }

    public boolean isDeactivated() {
        return isDeactivated != null && isDeactivated;
    }

    public boolean isAutoRenewing() {
        return isAutoRenewing != null && isAutoRenewing;
    }

    public OptionalLong expirationDate() {
        return expirationDate == null ? OptionalLong.empty() : OptionalLong.of(expirationDate);
    }

    public void setDeactivated(Boolean isDeactivated) {
        this.isDeactivated = isDeactivated;
    }

    public void setAutoRenewing(Boolean isAutoRenewing) {
        this.isAutoRenewing = isAutoRenewing;
    }

    public void setExpirationDate(Long expirationDate) {
        this.expirationDate = expirationDate;
    }
}
