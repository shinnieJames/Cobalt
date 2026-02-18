package com.github.auties00.cobalt.model.sync.action.misc;

import com.github.auties00.cobalt.model.sync.SyncAction;

import java.util.OptionalInt;

@ProtobufMessage(name = "SyncActionValue.KeyExpiration")
public final class KeyExpiration implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.INT32)
    Integer expiredKeyEpoch;


    KeyExpiration(Integer expiredKeyEpoch) {
        this.expiredKeyEpoch = expiredKeyEpoch;
    }

    public OptionalInt expiredKeyEpoch() {
        return expiredKeyEpoch == null ? OptionalInt.empty() : OptionalInt.of(expiredKeyEpoch);
    }

    public KeyExpiration setExpiredKeyEpoch(Integer expiredKeyEpoch) {
        this.expiredKeyEpoch = expiredKeyEpoch;
        return this;
    }
}
