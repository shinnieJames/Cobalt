package com.github.auties00.cobalt.model.message.newsletter;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.ContextInfo;
import com.github.auties00.cobalt.model.message.ContextualMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.NewsletterFollowerInviteMessage")
public final class NewsletterFollowerInviteMessage implements ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid newsletterJid;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String newsletterName;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] jpegThumbnail;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String caption;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;


    NewsletterFollowerInviteMessage(Jid newsletterJid, String newsletterName, byte[] jpegThumbnail, String caption, ContextInfo contextInfo) {
        this.newsletterJid = newsletterJid;
        this.newsletterName = newsletterName;
        this.jpegThumbnail = jpegThumbnail;
        this.caption = caption;
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

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public NewsletterFollowerInviteMessage setNewsletterJid(Jid newsletterJid) {
        this.newsletterJid = newsletterJid;
        return this;
    }

    public NewsletterFollowerInviteMessage setNewsletterName(String newsletterName) {
        this.newsletterName = newsletterName;
        return this;
    }

    public NewsletterFollowerInviteMessage setJpegThumbnail(byte[] jpegThumbnail) {
        this.jpegThumbnail = jpegThumbnail;
        return this;
    }

    public NewsletterFollowerInviteMessage setCaption(String caption) {
        this.caption = caption;
        return this;
    }

    public NewsletterFollowerInviteMessage setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
        return this;
    }
}
