package com.github.auties00.cobalt.model.sync.data;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collections;
import java.util.List;

@ProtobufMessage(name = "SyncdMutations")
public final class SyncdMutations {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    List<SyncdMutation> mutations;


    SyncdMutations(List<SyncdMutation> mutations) {
        this.mutations = mutations;
    }

    public List<SyncdMutation> mutations() {
        return mutations == null ? List.of() : Collections.unmodifiableList(mutations);
    }

    public void setMutations(List<SyncdMutation> mutations) {
        this.mutations = mutations;
    }
}
