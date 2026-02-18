package com.github.auties00.cobalt.model.message.group;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.ContextInfo;
import com.github.auties00.cobalt.model.message.ContextualMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalLong;

@ProtobufMessage(name = "Message.GroupInviteMessage")
public final class GroupInviteMessage implements ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid groupJid;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String inviteCode;

    @ProtobufProperty(index = 3, type = ProtobufType.INT64)
    Long inviteExpiration;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String groupName;

    @ProtobufProperty(index = 5, type = ProtobufType.BYTES)
    byte[] jpegThumbnail;

    @ProtobufProperty(index = 6, type = ProtobufType.STRING)
    String caption;

    @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 8, type = ProtobufType.ENUM)
    GroupType groupType;


    GroupInviteMessage(Jid groupJid, String inviteCode, Long inviteExpiration, String groupName, byte[] jpegThumbnail, String caption, ContextInfo contextInfo, GroupType groupType) {
        this.groupJid = groupJid;
        this.inviteCode = inviteCode;
        this.inviteExpiration = inviteExpiration;
        this.groupName = groupName;
        this.jpegThumbnail = jpegThumbnail;
        this.caption = caption;
        this.contextInfo = contextInfo;
        this.groupType = groupType;
    }

    public Optional<Jid> groupJid() {
        return Optional.ofNullable(groupJid);
    }

    public Optional<String> inviteCode() {
        return Optional.ofNullable(inviteCode);
    }

    public OptionalLong inviteExpiration() {
        return inviteExpiration == null ? OptionalLong.empty() : OptionalLong.of(inviteExpiration);
    }

    public Optional<String> groupName() {
        return Optional.ofNullable(groupName);
    }

    public Optional<byte[]> jpegThumbnail() {
        return Optional.ofNullable(jpegThumbnail);
    }

    public Optional<String> caption() {
        return Optional.ofNullable(caption);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public Optional<GroupType> groupType() {
        return Optional.ofNullable(groupType);
    }

    public GroupInviteMessage setGroupJid(Jid groupJid) {
        this.groupJid = groupJid;
        return this;
    }

    public GroupInviteMessage setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
        return this;
    }

    public GroupInviteMessage setInviteExpiration(Long inviteExpiration) {
        this.inviteExpiration = inviteExpiration;
        return this;
    }

    public GroupInviteMessage setGroupName(String groupName) {
        this.groupName = groupName;
        return this;
    }

    public GroupInviteMessage setJpegThumbnail(byte[] jpegThumbnail) {
        this.jpegThumbnail = jpegThumbnail;
        return this;
    }

    public GroupInviteMessage setCaption(String caption) {
        this.caption = caption;
        return this;
    }

    public GroupInviteMessage setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
        return this;
    }

    public GroupInviteMessage setGroupType(GroupType groupType) {
        this.groupType = groupType;
        return this;
    }

    @ProtobufEnum(name = "Message.GroupInviteMessage.GroupType")
    public static enum GroupType {
        DEFAULT(0),
        PARENT(1);

        GroupType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
