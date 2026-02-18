package com.github.auties00.cobalt.model.sync.setting;

import com.github.auties00.cobalt.model.sync.SyncAction;

import java.util.Collections;
import java.util.List;

@ProtobufMessage(name = "SyncActionValue.PrimaryFeature")
public final class PrimaryFeature implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    List<String> flags;


    PrimaryFeature(List<String> flags) {
        this.flags = flags;
    }

    public List<String> flags() {
        return flags == null ? List.of() : Collections.unmodifiableList(flags);
    }

    public PrimaryFeature setFlags(List<String> flags) {
        this.flags = flags;
        return this;
    }
}
