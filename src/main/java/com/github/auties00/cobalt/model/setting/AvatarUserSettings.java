package com.github.auties00.cobalt.model.setting;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "AvatarUserSettings")
public final class AvatarUserSettings {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String fbid;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String password;


    AvatarUserSettings(String fbid, String password) {
        this.fbid = fbid;
        this.password = password;
    }

    public Optional<String> fbid() {
        return Optional.ofNullable(fbid);
    }

    public Optional<String> password() {
        return Optional.ofNullable(password);
    }

    public void setFbid(String fbid) {
        this.fbid = fbid;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
