package com.github.auties00.cobalt.model.chat.group;

import com.github.auties00.cobalt.model.jid.Jid;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;
import java.util.Optional;

@ProtobufMessage(name = "GroupParticipant")
public final class GroupParticipant {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid userJid;

    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    GroupPartipantRole rank;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    GroupParticipantLabel memberLabel;


    GroupParticipant(Jid userJid, GroupPartipantRole rank, GroupParticipantLabel memberLabel) {
        this.userJid = Objects.requireNonNull(userJid);
        this.rank = rank;
        this.memberLabel = memberLabel;
    }

    public Jid userJid() {
        return userJid;
    }

    public Optional<GroupPartipantRole> rank() {
        return Optional.ofNullable(rank);
    }

    public Optional<GroupParticipantLabel> memberLabel() {
        return Optional.ofNullable(memberLabel);
    }

    public void setUserJid(Jid userJid) {
        this.userJid = userJid;
    }

    public void setRank(GroupPartipantRole rank) {
        this.rank = rank;
    }

    public void setMemberLabel(GroupParticipantLabel memberLabel) {
        this.memberLabel = memberLabel;
    }
}
