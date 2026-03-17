package com.github.auties00.cobalt.model.message.security;

import com.github.auties00.cobalt.message.addon.MessageAddonEncryption;
import com.github.auties00.cobalt.message.addon.MessageAddonType;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.Message;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.text.CommentMessage;
import it.auties.protobuf.annotation.ProtobufBuilder;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;
import java.util.Optional;

@ProtobufMessage(name = "Message.EncCommentMessage")
public final class EncCommentMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey targetMessageKey;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] encPayload;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] encIv;

    /**
     * Constructs an encrypted comment from a plaintext
     * {@link CommentMessage}, encrypting the comment's inner
     * {@code Message} content with the parent message's messageSecret.
     *
     * @param comment       the plaintext comment message
     * @param parentMessage the parent message being commented on (must contain messageSecret)
     * @param selfJid       the sender's user JID
     * @return the encrypted comment message
     * @throws IllegalArgumentException if the parent message has no messageSecret
     *                                  or the comment has no message content
     *
     * @apiNote WAWebAddonEncryption.encryptAddOn: encrypts with
     * ENC_COMMENT use case, using full MessageSpec encoding.
     */
    @ProtobufBuilder(className = "EncCommentMessageSimpleBuilder")
    static EncCommentMessage simpleBuilder(CommentMessage comment, ChatMessageInfo parentMessage, Jid selfJid) {
        Objects.requireNonNull(comment, "comment cannot be null");
        Objects.requireNonNull(parentMessage, "parentMessage cannot be null");
        Objects.requireNonNull(selfJid, "selfJid cannot be null");

        var parentSecret = parentMessage.messageSecret()
                .orElseThrow(() -> new IllegalArgumentException("Parent message has no messageSecret"));
        var parentKey = parentMessage.key();
        var parentKeyId = parentKey.id()
                .orElseThrow(() -> new IllegalArgumentException("Parent key has no keyId"));
        var parentKeyJid = parentKey.parentJid()
                .orElseThrow(() -> new IllegalArgumentException("Parent key has no parentJid"));
        var originalSender = parentKey.senderJid()
                .orElse(parentKey.fromMe() ? selfJid : parentKeyJid)
                .toUserJid();

        // WAWebAddonEncryption: CommentEncrypted uses MessageSpec (full Message protobuf)
        var commentContent = comment.message()
                .orElseThrow(() -> new IllegalArgumentException("Comment has no message content"));
        var plaintext = MessageContainerSpec.encode(commentContent);

        var encrypted = MessageAddonEncryption.encrypt(
                plaintext, parentSecret, parentKeyId,
                originalSender, selfJid.toUserJid(),
                MessageAddonType.ENC_COMMENT);

        return new EncCommentMessageBuilder()
                .targetMessageKey(comment.targetMessageKey().orElse(null))
                .encPayload(encrypted.ciphertext())
                .encIv(encrypted.iv())
                .build();
    }

    EncCommentMessage(MessageKey targetMessageKey, byte[] encPayload, byte[] encIv) {
        this.targetMessageKey = targetMessageKey;
        this.encPayload = encPayload;
        this.encIv = encIv;
    }

    public Optional<MessageKey> targetMessageKey() {
        return Optional.ofNullable(targetMessageKey);
    }

    public Optional<byte[]> encPayload() {
        return Optional.ofNullable(encPayload);
    }

    public Optional<byte[]> encIv() {
        return Optional.ofNullable(encIv);
    }

    public void setTargetMessageKey(MessageKey targetMessageKey) {
        this.targetMessageKey = targetMessageKey;
    }

    public void setEncPayload(byte[] encPayload) {
        this.encPayload = encPayload;
    }

    public void setEncIv(byte[] encIv) {
        this.encIv = encIv;
    }
}
