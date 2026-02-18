package com.github.auties00.cobalt.model.sync.action.misc;

import com.github.auties00.cobalt.model.call.CallLog;
import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;

import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.CallLogAction")
public final class CallLogAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    CallLog log;


    CallLogAction(CallLog log) {
        this.log = log;
    }

    public Optional<CallLog> log() {
        return Optional.ofNullable(log);
    }

    public CallLogAction setLog(CallLog log) {
        this.log = log;
        return this;
    }

}
