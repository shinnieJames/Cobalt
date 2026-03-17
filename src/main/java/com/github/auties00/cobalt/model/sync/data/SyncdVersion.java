package com.github.auties00.cobalt.model.sync.data;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.OptionalLong;

@ProtobufMessage(name = "SyncdVersion")
public final class SyncdVersion {
    @ProtobufProperty(index = 1, type = ProtobufType.UINT64)
    Long version;


    SyncdVersion(Long version) {
        this.version = version;
    }

    public OptionalLong version() {
        return version == null ? OptionalLong.empty() : OptionalLong.of(version);
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
