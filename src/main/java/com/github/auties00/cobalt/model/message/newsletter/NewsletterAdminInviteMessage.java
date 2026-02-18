package com.github.auties00.cobalt.model.message.newsletter;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.ContextInfo;
import com.github.auties00.cobalt.model.message.ContextualMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalLong;

@ProtobufMessage(name = "Message.NewsletterAdminInviteMessage")
public final class NewsletterAdminInviteMessage implements ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid newsletterJid;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String newsletterName;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] jpegThumbnail;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String caption;

    @ProtobufProperty(index = 5, type = ProtobufType.INT64)
    Long inviteExpiration;

    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;


    NewsletterAdminInviteMessage(Jid newsletterJid, String newsletterName, byte[] jpegThumbnail, String caption, Long inviteExpiration, ContextInfo contextInfo) {
        this.newsletterJid = newsletterJid;
        this.newsletterName = newsletterName;
        this.jpegThumbnail = jpegThumbnail;
        this.caption = caption;
        this.inviteExpiration = inviteExpiration;
        this.contextInfo = contextInfo;
    }

    public Optional<Jid> newsletterJid() {
        return Optional.ofNullable(newsletterJid);
    }

    public Optional<String> newsletterName() {
        return Optional.ofNullable(newsletterName);
    }

    public Optional<byte[]> jpegThumbnail() {
        return Optional.ofNullable(jpegThumbnail);
    }

    public Optional<String> caption() {
        return Optional.ofNullable(caption);
    }

    public OptionalLong inviteExpiration() {
        return inviteExpiration == null ? OptionalLong.empty() : OptionalLong.of(inviteExpiration);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public NewsletterAdminInviteMessage setNewsletterJid(Jid newsletterJid) {
        this.newsletterJid = newsletterJid;
        return this;
    }

    public NewsletterAdminInviteMessage setNewsletterName(String newsletterName) {
        this.newsletterName = newsletterName;
        return this;
    }

    public NewsletterAdminInviteMessage setJpegThumbnail(byte[] jpegThumbnail) {
        this.jpegThumbnail = jpegThumbnail;
        return this;
    }

    public NewsletterAdminInviteMessage setCaption(String caption) {
        this.caption = caption;
        return this;
    }

    public NewsletterAdminInviteMessage setInviteExpiration(Long inviteExpiration) {
        this.inviteExpiration = inviteExpiration;
        return this;
    }

    public NewsletterAdminInviteMessage setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
        return this;
    }
}
