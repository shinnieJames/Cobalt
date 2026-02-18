package com.github.auties00.cobalt.model.message;

import com.github.auties00.cobalt.model.jid.Jid;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "MessageKey")
public final class MessageKey {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid remoteJid;

    @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
    Boolean fromMe;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String id;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String participant;


    MessageKey(Jid remoteJid, Boolean fromMe, String id, String participant) {
        this.remoteJid = remoteJid;
        this.fromMe = fromMe;
        this.id = id;
        this.participant = participant;
    }

    public Optional<Jid> remoteJid() {
        return Optional.ofNullable(remoteJid);
    }

    public boolean fromMe() {
        return fromMe != null && fromMe;
    }

    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    public Optional<String> participant() {
        return Optional.ofNullable(participant);
    }

    public MessageKey setRemoteJid(Jid remoteJid) {
        this.remoteJid = remoteJid;
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

    public MessageKey setParticipant(String participant) {
        this.participant = participant;
        return this;
    }
}
