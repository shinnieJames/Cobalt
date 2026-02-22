package com.github.auties00.cobalt.model.sync.action.chat;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.PnForLidChatAction")
public final class PnForLidChatAction implements SyncAction<PnForLidChatActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "pnForLidChat";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 8;

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
