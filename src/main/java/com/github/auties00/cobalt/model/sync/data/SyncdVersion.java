package com.github.auties00.cobalt.model.sync.data;

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

    public SyncdVersion setVersion(Long version) {
        this.version = version;
        return this;
    }
}
