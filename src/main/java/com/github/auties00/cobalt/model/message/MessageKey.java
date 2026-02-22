package com.github.auties00.cobalt.model.message;

import com.github.auties00.cobalt.model.jid.Jid;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "MessageKey")
public final class MessageKey {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid chatJid;

    @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
    Boolean fromMe;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String id;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    Jid senderJid;


    MessageKey(Jid chatJid, Boolean fromMe, String id, Jid senderJid) {
        this.chatJid = chatJid;
        this.fromMe = fromMe;
        this.id = id;
        this.senderJid = senderJid;
    }

    public Optional<Jid> chatJid() {
        return Optional.ofNullable(chatJid);
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
            return Optional.ofNullable(chatJid);
        }
    }

    public MessageKey setChatJid(Jid chatJid) {
        this.chatJid = chatJid;
        return this;
    }

    public MessageKey setFromMe(Boolean fromMe) {
        this.fromMe = fromMe;
        return this;
    }

    public MessageKey setId(String id) {
        this.id = id;
        return this;
    }

    public MessageKey setSenderJid(Jid senderJid) {
        this.senderJid = senderJid;
        return this;
    }
}
