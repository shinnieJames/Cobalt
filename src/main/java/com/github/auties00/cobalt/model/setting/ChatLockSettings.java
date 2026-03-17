package com.github.auties00.cobalt.model.setting;

import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "ChatLockSettings")
public final class ChatLockSettings implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "setting_chatLock";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 7;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR_LOW;

    @Override
    public String actionName() {
        return ACTION_NAME;
    }

    @Override
    public int actionVersion() {
        return ACTION_VERSION;
    }

    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean hideLockedChats;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    UserPassword secretCode;


    ChatLockSettings(Boolean hideLockedChats, UserPassword secretCode) {
        this.hideLockedChats = hideLockedChats;
        this.secretCode = secretCode;
    }

    public boolean hideLockedChats() {
        return hideLockedChats != null && hideLockedChats;
    }

    public Optional<UserPassword> secretCode() {
        return Optional.ofNullable(secretCode);
    }

    public void setHideLockedChats(Boolean hideLockedChats) {
        this.hideLockedChats = hideLockedChats;
    }

    public void setSecretCode(UserPassword secretCode) {
        this.secretCode = secretCode;
    }
}
