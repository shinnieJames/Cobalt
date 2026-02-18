package com.github.auties00.cobalt.model.sync.action.contact;

import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.LidContactAction")
public final class LidContactAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String fullName;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String firstName;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String username;


    LidContactAction(String fullName, String firstName, String username) {
        this.fullName = fullName;
        this.firstName = firstName;
        this.username = username;
    }

    public Optional<String> fullName() {
        return Optional.ofNullable(fullName);
    }

    public Optional<String> firstName() {
        return Optional.ofNullable(firstName);
    }

    public Optional<String> username() {
        return Optional.ofNullable(username);
    }

    public LidContactAction setFullName(String fullName) {
        this.fullName = fullName;
        return this;
    }

    public LidContactAction setFirstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    public LidContactAction setUsername(String username) {
        this.username = username;
        return this;
    }
}
