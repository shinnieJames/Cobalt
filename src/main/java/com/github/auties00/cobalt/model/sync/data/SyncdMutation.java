package com.github.auties00.cobalt.model.sync.data;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncdMutation")
public final class SyncdMutation {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    SyncdOperation operation;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    SyncdRecord record;


    SyncdMutation(SyncdOperation operation, SyncdRecord record) {
        this.operation = operation;
        this.record = record;
    }

    public Optional<SyncdOperation> operation() {
        return Optional.ofNullable(operation);
    }

    public Optional<SyncdRecord> record() {
        return Optional.ofNullable(record);
    }

    public void setOperation(SyncdOperation operation) {
        this.operation = operation;
    }

    public void setRecord(SyncdRecord record) {
        this.record = record;
    }
}
