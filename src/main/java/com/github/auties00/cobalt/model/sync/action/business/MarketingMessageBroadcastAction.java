package com.github.auties00.cobalt.model.sync.action.business;

import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.OptionalInt;

@ProtobufMessage(name = "SyncActionValue.MarketingMessageBroadcastAction")
public final class MarketingMessageBroadcastAction implements SyncAction<MarketingMessageBroadcastActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "marketingMessageBroadcast";

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


    @ProtobufProperty(index = 1, type = ProtobufType.INT32)
    Integer repliedCount;


    MarketingMessageBroadcastAction(Integer repliedCount) {
        this.repliedCount = repliedCount;
    }

    public OptionalInt repliedCount() {
        return repliedCount == null ? OptionalInt.empty() : OptionalInt.of(repliedCount);
    }

    public void setRepliedCount(Integer repliedCount) {
        this.repliedCount = repliedCount;
    }


}
