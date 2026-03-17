package com.github.auties00.cobalt.model.message;

import com.github.auties00.cobalt.model.jid.Jid;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "MessageKey")
public final class MessageKey {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid parentJid;

    @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
    Boolean fromMe;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String id;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    Jid senderJid;


    MessageKey(Jid parentJid, Boolean fromMe, String id, Jid senderJid) {
        this.parentJid = parentJid;
        this.fromMe = fromMe;
        this.id = id;
        this.senderJid = senderJid;
    }

    public Optional<Jid> parentJid() {
        return Optional.ofNullable(parentJid);
    }

    public boolean fromMe() {
        return fromMe != null && fromMe;
    }

    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    public Optional<Jid> senderJid() {
        if(senderJid != null) {
            return Optional.of(senderJid);
        } else {
            return Optional.ofNullable(parentJid);
        }
    }

    public void setParentJid(Jid chatJid) {
        this.parentJid = chatJid;
    }

    public void setFromMe(Boolean fromMe) {
        this.fromMe = fromMe;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setSenderJid(Jid senderJid) {
        this.senderJid = senderJid;
    }
}
