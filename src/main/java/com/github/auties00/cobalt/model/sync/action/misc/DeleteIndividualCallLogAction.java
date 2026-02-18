package com.github.auties00.cobalt.model.sync.action.misc;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.DeleteIndividualCallLogAction")
public final class DeleteIndividualCallLogAction implements SyncAction {
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

    public DeleteIndividualCallLogAction setPeerJid(Jid peerJid) {
        this.peerJid = peerJid;
        return this;
    }

    public DeleteIndividualCallLogAction setIncoming(Boolean isIncoming) {
        this.isIncoming = isIncoming;
        return this;
    }
}
