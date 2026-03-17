package com.github.auties00.cobalt.model.message.security;

import com.github.auties00.cobalt.message.addon.MessageAddonEncryption;
import com.github.auties00.cobalt.message.addon.MessageAddonType;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.Message;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.text.ReactionMessage;
import com.github.auties00.cobalt.model.message.text.ReactionMessageSpec;
import it.auties.protobuf.annotation.ProtobufBuilder;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;
import java.util.Optional;

@ProtobufMessage(name = "Message.EncReactionMessage")
public final class EncReactionMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey targetMessageKey;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] encPayload;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] encIv;

    /**
     * Constructs an encrypted reaction message from a plaintext
     * {@link ReactionMessage}, encrypting it with the parent message's
     * messageSecret.
     *
     * @param reaction      the plaintext reaction message
     * @param parentMessage the parent message being reacted to (must contain messageSecret)
     * @param selfJid       the sender's user JID
     * @return the encrypted reaction message
     * @throws IllegalArgumentException if the parent message has no messageSecret
     *
     * @apiNote WAWebReactionEncryptMsgData: encodes ReactionMessage protobuf,
     * encrypts via WAWebAddonEncryption.encryptAddOn with ENC_REACTION use case.
     */
    @ProtobufBuilder(className = "EncReactionMessageSimpleBuilder")
    static EncReactionMessage simpleBuilder(ReactionMessage reaction, ChatMessageInfo parentMessage, Jid selfJid) {
        Objects.requireNonNull(reaction, "reaction cannot be null");
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

        // WAWebReactionEncryptMsgData: encode the plaintext reaction as protobuf
        var plaintext = ReactionMessageSpec.encode(reaction);

        // WAWebAddonEncryption.encryptAddOn: encrypt with ENC_REACTION use case
        var encrypted = MessageAddonEncryption.encrypt(
                plaintext, parentSecret, parentKeyId,
                originalSender, selfJid.toUserJid(),
                MessageAddonType.ENC_REACTION);

        return new EncReactionMessageBuilder()
                .targetMessageKey(reaction.key().orElse(null))
                .encPayload(encrypted.ciphertext())
                .encIv(encrypted.iv())
                .build();
    }

    EncReactionMessage(MessageKey targetMessageKey, byte[] encPayload, byte[] encIv) {
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
