package com.github.auties00.cobalt.model.sync.action.contact;

import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage(name = "SyncActionValue.UserStatusMuteAction")
public final class UserStatusMuteAction implements SyncAction<UserStatusMuteActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "userStatusMute";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 7;

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
    Boolean muted;


    UserStatusMuteAction(Boolean muted) {
        this.muted = muted;
    }

    public boolean muted() {
        return muted != null && muted;
    }

    public UserStatusMuteAction setMuted(Boolean muted) {
        this.muted = muted;
        return this;
    }


}
