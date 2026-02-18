package com.github.auties00.cobalt.model.message.system.appstate;

import com.github.auties00.cobalt.model.message.Message;

import java.util.Collections;
import java.util.List;

@ProtobufMessage(name = "Message.AppStateSyncKeyRequest")
public final class AppStateSyncKeyRequest implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    List<AppStateSyncKeyId> keyIds;


    AppStateSyncKeyRequest(List<AppStateSyncKeyId> keyIds) {
        this.keyIds = keyIds;
    }

    public List<AppStateSyncKeyId> keyIds() {
        return keyIds == null ? List.of() : Collections.unmodifiableList(keyIds);
    }

    public AppStateSyncKeyRequest setKeyIds(List<AppStateSyncKeyId> keyIds) {
        this.keyIds = keyIds;
        return this;
    }
}
