package com.github.auties00.cobalt.model.sync.action.chat;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.PnForLidChatAction")
public final class PnForLidChatAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid pnJid;


    PnForLidChatAction(Jid pnJid) {
        this.pnJid = pnJid;
    }

    public Optional<Jid> pnJid() {
        return Optional.ofNullable(pnJid);
    }

    public PnForLidChatAction setPnJid(Jid pnJid) {
        this.pnJid = pnJid;
        return this;
    }
}
