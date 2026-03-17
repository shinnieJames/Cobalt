package com.github.auties00.cobalt.model.chat.group;

import com.github.auties00.cobalt.model.jid.Jid;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "GroupMention")
public final class GroupMention {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid groupJid;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String groupSubject;


    GroupMention(Jid groupJid, String groupSubject) {
        this.groupJid = groupJid;
        this.groupSubject = groupSubject;
    }

    public Optional<Jid> groupJid() {
        return Optional.ofNullable(groupJid);
    }

    public Optional<String> groupSubject() {
        return Optional.ofNullable(groupSubject);
    }

    public void setGroupJid(Jid groupJid) {
        this.groupJid = groupJid;
    }

    public void setGroupSubject(String groupSubject) {
        this.groupSubject = groupSubject;
    }
}
