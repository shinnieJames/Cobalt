package com.github.auties00.cobalt.model.sync.action.call;

import com.github.auties00.cobalt.model.call.CallLog;
import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;

import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.CallLogAction")
public final class CallLogAction implements SyncAction<CallLogActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "call_log";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 1;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR;

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int actionVersion() {
        return ACTION_VERSION;
    }


    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    CallLog log;


    CallLogAction(CallLog log) {
        this.log = log;
    }

    public Optional<CallLog> log() {
        return Optional.ofNullable(log);
    }

    public void setLog(CallLog log) {
        this.log = log;
    }



}
