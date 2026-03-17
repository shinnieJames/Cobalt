package com.github.auties00.cobalt.model.message.system.appstate;

import com.github.auties00.cobalt.model.message.Message;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collections;
import java.util.List;

@ProtobufMessage(name = "Message.AppStateSyncKeyShare")
public final class AppStateSyncKeyShare implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    List<AppStateSyncKey> keys;


    AppStateSyncKeyShare(List<AppStateSyncKey> keys) {
        this.keys = keys;
    }

    public List<AppStateSyncKey> keys() {
        return keys == null ? List.of() : Collections.unmodifiableList(keys);
    }

    public void setKeys(List<AppStateSyncKey> keys) {
        this.keys = keys;
    }
}
