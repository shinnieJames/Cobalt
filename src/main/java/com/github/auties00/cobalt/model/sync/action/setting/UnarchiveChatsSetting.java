package com.github.auties00.cobalt.model.sync.action.setting;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage(name = "SyncActionValue.UnarchiveChatsSetting")
public final class UnarchiveChatsSetting implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "setting_unarchiveChats";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 4;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR_LOW;

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
    Boolean unarchiveChats;


    UnarchiveChatsSetting(Boolean unarchiveChats) {
        this.unarchiveChats = unarchiveChats;
    }

    public boolean unarchiveChats() {
        return unarchiveChats != null && unarchiveChats;
    }

    public void setUnarchiveChats(Boolean unarchiveChats) {
        this.unarchiveChats = unarchiveChats;
    }
}
