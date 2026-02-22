package com.github.auties00.cobalt.model.sync.action.device;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage(name = "SyncActionValue.TimeFormatAction")
public final class TimeFormatAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "time_format";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 7;

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


    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean isTwentyFourHourFormatEnabled;


    TimeFormatAction(Boolean isTwentyFourHourFormatEnabled) {
        this.isTwentyFourHourFormatEnabled = isTwentyFourHourFormatEnabled;
    }

    public boolean isTwentyFourHourFormatEnabled() {
        return isTwentyFourHourFormatEnabled != null && isTwentyFourHourFormatEnabled;
    }

    public TimeFormatAction setTwentyFourHourFormatEnabled(Boolean isTwentyFourHourFormatEnabled) {
        this.isTwentyFourHourFormatEnabled = isTwentyFourHourFormatEnabled;
        return this;
    }
}
