package com.github.auties00.cobalt.model.sync.action.contact;

import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage(name = "SyncActionValue.LabelAssociationAction")
public final class LabelAssociationAction implements SyncAction<LabelAssociationActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "label_jid";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 3;

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
    Boolean labeled;


    LabelAssociationAction(Boolean labeled) {
        this.labeled = labeled;
    }

    public boolean labeled() {
        return labeled != null && labeled;
    }

    public LabelAssociationAction setLabeled(Boolean labeled) {
        this.labeled = labeled;
        return this;
    }


}
