package com.github.auties00.cobalt.model.sync.action.call;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.DeleteIndividualCallLogAction")
public final class DeleteIndividualCallLogAction implements SyncAction<DeleteIndividualCallLogActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "delete_individual_call_log";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 1;

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


    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid peerJid;

    @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
    Boolean isIncoming;


    DeleteIndividualCallLogAction(Jid peerJid, Boolean isIncoming) {
        this.peerJid = peerJid;
        this.isIncoming = isIncoming;
    }

    public Optional<Jid> peerJid() {
        return Optional.ofNullable(peerJid);
    }

    public boolean isIncoming() {
        return isIncoming != null && isIncoming;
    }

    public void setPeerJid(Jid peerJid) {
        this.peerJid = peerJid;
    }

    public void setIncoming(Boolean isIncoming) {
        this.isIncoming = isIncoming;
    }


}
