package com.github.auties00.cobalt.model.sync.action.device;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.OptionalInt;

@ProtobufMessage(name = "SyncActionValue.KeyExpiration")
public final class KeyExpirationAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "sentinel";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 3;

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


    @ProtobufProperty(index = 1, type = ProtobufType.INT32)
    Integer expiredKeyEpoch;


    KeyExpirationAction(Integer expiredKeyEpoch) {
        this.expiredKeyEpoch = expiredKeyEpoch;
    }

    public OptionalInt expiredKeyEpoch() {
        return expiredKeyEpoch == null ? OptionalInt.empty() : OptionalInt.of(expiredKeyEpoch);
    }

    public KeyExpirationAction setExpiredKeyEpoch(Integer expiredKeyEpoch) {
        this.expiredKeyEpoch = expiredKeyEpoch;
        return this;
    }
}
