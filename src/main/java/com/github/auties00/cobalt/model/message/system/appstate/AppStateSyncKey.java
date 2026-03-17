package com.github.auties00.cobalt.model.message.system.appstate;

import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.AppStateSyncKey")
public final class AppStateSyncKey implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    AppStateSyncKeyId keyId;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    AppStateSyncKeyData keyData;


    AppStateSyncKey(AppStateSyncKeyId keyId, AppStateSyncKeyData keyData) {
        this.keyId = keyId;
        this.keyData = keyData;
    }

    public Optional<AppStateSyncKeyId> keyId() {
        return Optional.ofNullable(keyId);
    }

    public Optional<AppStateSyncKeyData> keyData() {
        return Optional.ofNullable(keyData);
    }

    public void setKeyId(AppStateSyncKeyId keyId) {
        this.keyId = keyId;
    }

    public void setKeyData(AppStateSyncKeyData keyData) {
        this.keyData = keyData;
    }
}
