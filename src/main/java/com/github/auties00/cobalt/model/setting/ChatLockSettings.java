package com.github.auties00.cobalt.model.setting;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "ChatLockSettings")
public final class ChatLockSettings {
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

    public ChatLockSettings setHideLockedChats(Boolean hideLockedChats) {
        this.hideLockedChats = hideLockedChats;
        return this;
    }

    public ChatLockSettings setSecretCode(UserPassword secretCode) {
        this.secretCode = secretCode;
        return this;
    }
}
