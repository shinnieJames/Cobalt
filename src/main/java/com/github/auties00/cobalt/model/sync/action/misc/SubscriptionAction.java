package com.github.auties00.cobalt.model.sync.action.misc;

import com.github.auties00.cobalt.model.sync.SyncAction;

import java.util.OptionalLong;

@ProtobufMessage(name = "SyncActionValue.SubscriptionAction")
public final class SubscriptionAction implements SyncAction {
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

    public SubscriptionAction setDeactivated(Boolean isDeactivated) {
        this.isDeactivated = isDeactivated;
        return this;
    }

    public SubscriptionAction setAutoRenewing(Boolean isAutoRenewing) {
        this.isAutoRenewing = isAutoRenewing;
        return this;
    }

    public SubscriptionAction setExpirationDate(Long expirationDate) {
        this.expirationDate = expirationDate;
        return this;
    }
}
