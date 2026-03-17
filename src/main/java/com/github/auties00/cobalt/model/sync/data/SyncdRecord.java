package com.github.auties00.cobalt.model.sync.data;

import com.github.auties00.cobalt.model.signal.KeyId;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncdRecord")
public final class SyncdRecord {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    SyncdIndex index;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    SyncdValue value;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    KeyId keyId;


    SyncdRecord(SyncdIndex index, SyncdValue value, KeyId keyId) {
        this.index = index;
        this.value = value;
        this.keyId = keyId;
    }

    public Optional<SyncdIndex> index() {
        return Optional.ofNullable(index);
    }

    public Optional<SyncdValue> value() {
        return Optional.ofNullable(value);
    }

    public Optional<KeyId> keyId() {
        return Optional.ofNullable(keyId);
    }

    public void setIndex(SyncdIndex index) {
        this.index = index;
    }

    public void setValue(SyncdValue value) {
        this.value = value;
    }

    public void setKeyId(KeyId keyId) {
        this.keyId = keyId;
    }
}
