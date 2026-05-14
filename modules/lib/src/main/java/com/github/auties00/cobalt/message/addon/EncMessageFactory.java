package com.github.auties00.cobalt.message.addon;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.poll.PollEncValue;
import com.github.auties00.cobalt.model.message.poll.PollEncValueBuilder;
import com.github.auties00.cobalt.model.message.poll.PollVoteMessageBuilder;
import com.github.auties00.cobalt.model.message.poll.PollVoteMessageSpec;
import com.github.auties00.cobalt.model.message.security.EncCommentMessageBuilder;
import com.github.auties00.cobalt.model.message.security.EncCommentMessage;
import com.github.auties00.cobalt.model.message.security.EncReactionMessageBuilder;
import com.github.auties00.cobalt.model.message.security.EncReactionMessage;
import com.github.auties00.cobalt.model.message.text.CommentMessage;
import com.github.auties00.cobalt.model.message.text.ReactionMessage;
import com.github.auties00.cobalt.model.message.text.ReactionMessageSpec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Factory that converts plaintext comment, reaction, and poll-vote messages
 * into the encrypted forms WhatsApp expects on the wire for CAG (community /
 * announcement group) threads.
 *
 * <p>Each addon is delivered as an {@code <enc>} child of the outer Signal
 * envelope whose content is an AES-GCM ciphertext bound to the parent
 * message's secret. The factory builds that ciphertext via
 * {@link MessageAddonEncryption#encrypt} and packages the resulting bytes
 * into the matching Cobalt model type ({@link EncCommentMessage},
 * {@link EncReactionMessage}, or {@link PollEncValue}).
 */
@WhatsAppWebModule(moduleName = "WAWebAddonEncryption")
public final class EncMessageFactory {
    /**
     * Private constructor preventing instantiation.
     *
     * @throws UnsupportedOperationException always
     */
    private EncMessageFactory() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Encrypts a plaintext {@link CommentMessage} into an
     * {@link EncCommentMessage} that can be attached to an outbound stanza
     * without exposing the comment body to the server.
     *
     * <p>The comment's inner {@link com.github.auties00.cobalt.model.message.MessageContainer}
     * is serialised via {@link MessageContainerSpec} and then encrypted with
     * a key derived from the parent message's {@code messageSecret}. The
     * original sender used in the key derivation is taken from the parent
     * key. If the parent was authored by the current user the self JID is
     * used, otherwise the parent participant or chat JID is used.
     *
     * @param comment       the plaintext comment to encrypt
     * @param parentMessage the message the comment is attached to
     * @param selfJid       the JID of the current user, used when the parent
     *                      was authored by the current user
     * @return the encrypted comment wrapper ready to be wrapped in a stanza
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the parent message does not carry a
     *                                  {@code messageSecret}, the parent key
     *                                  has no id or parent JID, or the comment
     *                                  has no inner message
     */
    @WhatsAppWebExport(moduleName = "WAWebAddonEncryption", exports = "encryptAddOn",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static EncCommentMessage encryptComment(CommentMessage comment, ChatMessageInfo parentMessage, Jid selfJid) {
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

        var originalSender = resolveOriginalSender(parentKey, parentKeyJid, selfJid);

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

    /**
     * Encrypts a plaintext {@link ReactionMessage} into an
     * {@link EncReactionMessage} so the emoji and target key stay hidden from
     * the server while still being deliverable through the fanout pipeline.
     *
     * <p>Follows the same HKDF-plus-AES-GCM scheme as
     * {@link #encryptComment(CommentMessage, ChatMessageInfo, Jid)} but uses
     * {@link MessageAddonType#ENC_REACTION} so the derivation is bound to a
     * different context string.
     *
     * @param reaction      the plaintext reaction to encrypt
     * @param parentMessage the message the reaction is attached to
     * @param selfJid       the JID of the current user, used when the parent
     *                      was authored by the current user
     * @return the encrypted reaction wrapper ready to be attached to a stanza
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the parent message does not carry a
     *                                  {@code messageSecret}, or the parent
     *                                  key has no id or parent JID
     */
    @WhatsAppWebExport(moduleName = "WAWebAddonEncryption", exports = "encryptAddOn",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static EncReactionMessage encryptReaction(ReactionMessage reaction, ChatMessageInfo parentMessage, Jid selfJid) {
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

        var originalSender = resolveOriginalSender(parentKey, parentKeyJid, selfJid);

        var plaintext = ReactionMessageSpec.encode(reaction);

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

    /**
     * Encrypts the voter's selected option labels into a {@link PollEncValue}
     * suitable for embedding in an outgoing
     * {@link com.github.auties00.cobalt.model.message.poll.PollUpdateMessage}.
     *
     * <p>Each selected label is hashed with SHA-256 to obtain the canonical
     * 32-byte option digest, the digests are wrapped in a
     * {@link com.github.auties00.cobalt.model.message.poll.PollVoteMessage},
     * the protobuf is serialised, and the resulting bytes are encrypted under
     * an HKDF-derived key bound to the parent poll's {@code messageSecret} via
     * {@link MessageAddonEncryption#encrypt}. The poll-vote use case mixes
     * the voter JID and the poll-creation stanza id into the AES-GCM AAD so a
     * malicious server cannot rebind the ciphertext to a different voter.
     *
     * @param selectedOptions the option labels the voter chose, in any order
     * @param pollCreation    the poll-creation message the vote refers to
     * @param voterJid        the JID of the user casting the vote
     * @return the {@link PollEncValue} containing the ciphertext and IV
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code pollCreation} carries no
     *                                  {@code messageSecret}, or its key has
     *                                  no id or parent JID
     */
    @WhatsAppWebExport(moduleName = "WAWebPollsVoteEncryption", exports = "encryptVote",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebPollOptionHashUtils", exports = "getHashBufferForString",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static PollEncValue encryptPollVote(List<String> selectedOptions, ChatMessageInfo pollCreation, Jid voterJid) {
        Objects.requireNonNull(selectedOptions, "selectedOptions cannot be null");
        Objects.requireNonNull(pollCreation, "pollCreation cannot be null");
        Objects.requireNonNull(voterJid, "voterJid cannot be null");

        var pollSecret = pollCreation.messageSecret()
                .orElseThrow(() -> new IllegalArgumentException("Poll creation has no messageSecret"));

        var pollKey = pollCreation.key();
        var pollKeyId = pollKey.id()
                .orElseThrow(() -> new IllegalArgumentException("Poll creation key has no id"));
        var pollKeyJid = pollKey.parentJid()
                .orElseThrow(() -> new IllegalArgumentException("Poll creation key has no parentJid"));

        var originalSender = resolveOriginalSender(pollKey, pollKeyJid, voterJid);

        var optionHashes = new ArrayList<byte[]>(selectedOptions.size());
        try {
            // WAWebPollOptionHashUtils.getHashBufferForString:
            //   self.crypto.subtle.digest("SHA-256", new TextEncoder().encode(e))
            // Cobalt stores the raw 32-byte digest because PollVoteMessage.selectedOptions
            // is wire-encoded as bytes (matches WAWebPollsCreateOptionLocalIdMap.getLocalIdForHash
            // which accepts the raw buffer rather than the hex form).
            var digest = MessageDigest.getInstance("SHA-256");
            for (var option : selectedOptions) {
                Objects.requireNonNull(option, "selectedOptions cannot contain null entries");
                digest.reset();
                optionHashes.add(digest.digest(option.getBytes(StandardCharsets.UTF_8)));
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }

        var voteMessage = new PollVoteMessageBuilder()
                .selectedOptions(optionHashes)
                .build();
        var plaintext = PollVoteMessageSpec.encode(voteMessage);

        var encrypted = MessageAddonEncryption.encrypt(
                plaintext, pollSecret, pollKeyId,
                originalSender, voterJid.toUserJid(),
                MessageAddonType.POLL_VOTE);

        return new PollEncValueBuilder()
                .encPayload(encrypted.ciphertext())
                .encIv(encrypted.iv())
                .build();
    }

    /**
     * Resolves the original-sender JID for the addon HKDF info, mirroring
     * {@code WAWebMsgGetters.getOriginalSender(parent)}: prefer the
     * self-author marker (the {@code fromMe} flag), fall back to the
     * explicit {@code senderJid}, then to the {@code parentJid}.
     *
     * @param parentKey    the parent message's key
     * @param parentKeyJid the parent key's chat JID
     * @param selfJid      the JID of the local user
     * @return the resolved original sender in user form
     */
    private static Jid resolveOriginalSender(
            com.github.auties00.cobalt.model.message.MessageKey parentKey,
            Jid parentKeyJid,
            Jid selfJid
    ) {
        Jid originalSender;
        if (parentKey.fromMe()) {
            originalSender = selfJid;
        } else {
            originalSender = parentKey.senderJid().orElse(parentKeyJid);
        }
        return originalSender.toUserJid();
    }
}
