package com.github.auties00.cobalt.model.chat.group;

import com.github.auties00.cobalt.model.jid.Jid;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ProtobufMessage(name = "PastParticipants")
public final class GroupPastParticipants {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid groupJid;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    List<GroupPastParticipant> pastParticipants;


    GroupPastParticipants(Jid groupJid, List<GroupPastParticipant> pastParticipants) {
        this.groupJid = groupJid;
        this.pastParticipants = pastParticipants;
    }

    public Optional<Jid> groupJid() {
        return Optional.ofNullable(groupJid);
    }

    public List<GroupPastParticipant> pastParticipants() {
        return pastParticipants == null ? List.of() : Collections.unmodifiableList(pastParticipants);
    }

    public void setGroupJid(Jid groupJid) {
        this.groupJid = groupJid;
    }

    public void setPastParticipants(List<GroupPastParticipant> pastParticipants) {
        this.pastParticipants = pastParticipants;
    }
}
