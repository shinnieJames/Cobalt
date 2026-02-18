package com.github.auties00.cobalt.model.sync.action.business;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.BroadcastListParticipant")
public final class BroadcastListParticipant implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid lidJid;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    Jid pnJid;


    BroadcastListParticipant(Jid lidJid, Jid pnJid) {
        this.lidJid = Objects.requireNonNull(lidJid);
        this.pnJid = pnJid;
    }

    public Jid lidJid() {
        return lidJid;
    }

    public Optional<Jid> pnJid() {
        return Optional.ofNullable(pnJid);
    }

    public BroadcastListParticipant setLidJid(Jid lidJid) {
        this.lidJid = lidJid;
        return this;
    }

    public BroadcastListParticipant setPnJid(Jid pnJid) {
        this.pnJid = pnJid;
        return this;
    }
}
