package com.github.auties00.cobalt.model.sync.action.business;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.BroadcastListParticipant")
public final class BroadcastListParticipantAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "broadcast_list_participant";

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
    Jid lidJid;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    Jid pnJid;


    public BroadcastListParticipantAction() {

    }

    BroadcastListParticipantAction(Jid lidJid, Jid pnJid) {
        this.lidJid = Objects.requireNonNull(lidJid);
        this.pnJid = pnJid;
    }

    public Jid lidJid() {
        return lidJid;
    }

    public Optional<Jid> pnJid() {
        return Optional.ofNullable(pnJid);
    }

    public void setLidJid(Jid lidJid) {
        this.lidJid = lidJid;
    }

    public void setPnJid(Jid pnJid) {
        this.pnJid = pnJid;
    }
}
