package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.message.send.id.MessageIdGenerator;
import com.github.auties00.cobalt.message.send.id.MessageIdVersion;
import com.github.auties00.cobalt.model.chat.ChatMessageContextInfoBuilder;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.chat.group.GroupMetadata;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.*;
import com.github.auties00.cobalt.model.message.event.EncEventResponseMessage;
import com.github.auties00.cobalt.model.message.poll.PollUpdateMessage;
import com.github.auties00.cobalt.model.message.security.*;
import com.github.auties00.cobalt.model.message.text.CommentMessage;
import com.github.auties00.cobalt.model.message.text.ReactionMessage;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfoBuilder;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.util.FastRandomUtils;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Prepares outgoing messages for sending by constructing the full
 * {@link ChatMessageInfo} or {@link NewsletterMessageInfo} from a raw
 * {@link MessageContainer}.
 *
 * <p>The preparation pipeline mirrors WA Web's
 * {@code WAWebOutgoingMessage.createOutgoingMessageProtobuf}:
 * <ol>
 *   <li>Generate a unique message ID</li>
 *   <li>Generate a 32-byte {@code messageSecret}</li>
 *   <li>Validate addon encryption (encrypted addons must have
 *       their {@code encPayload}/{@code encIv} populated)</li>
 *   <li>Auto-convert {@link ReactionMessage} to
 *       {@link EncReactionMessage} when targeting a CAG group</li>
 *   <li>Populate {@code messageContextInfo} with messageSecret</li>
 *   <li>Wrap everything into the appropriate {@link MessageInfo}</li>
 * </ol>
 *
 * <p>Device list metadata (ICDC) is populated later by
 * {@code IcdcEnricher.enrich()} in the send pipeline.
 * Random padding is applied at the Signal binary level by
 * {@code MessageEncryption} via {@code WACryptoPkcs7.writeRandomPadMax16}.
 *
 * @implNote WAWebOutgoingMessage.createOutgoingMessageProtobuf: creates
 * the protobuf from message data.
 * WAWebE2EProtoGenerator.getProtobufMessage: sets messageSecret on
 * messageContextInfo.
 * WAWebAddonEncryptAddonMsgData.encryptAddOn: applies inner AES-GCM
 * encryption for addon message types.
 */
final class MessagePreparer {
    /**
     * Logger for diagnostic output during message preparation.
     *
     * @implNote NO_WA_BASIS: Java logging infrastructure.
     */
    private static final System.Logger LOGGER = System.getLogger(MessagePreparer.class.getName());

    /**
     * Size of the message secret in bytes.
     *
     * <p>WA Web generates the 32-byte message secret via
     * {@code self.crypto.getRandomValues(new Uint8Array(32))} in
     * various message creation actions.
     *
     * @implNote WAWebAddonEncryptionError.getValidatedMessageSecret:
     * validates that messageSecret is exactly 32 bytes.
     */
    private static final int MESSAGE_SECRET_SIZE = 32;

    /**
     * The store used for JID lookups, newsletter resolution, and group
     * metadata queries.
     *
     * @implNote ADAPTED: WAWebE2EProtoGenerator uses module-level imports;
     * Cobalt uses constructor-injected store.
     */
    private final WhatsAppStore store;

    /**
     * Creates a new message preparer with the specified store.
     *
     * @param store the store for JID lookups and metadata queries
     * @implNote ADAPTED: WAWebE2EProtoGenerator uses module-level imports;
     * Cobalt uses constructor-injected store.
     */
    MessagePreparer(WhatsAppStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Prepares a {@link MessageContainer} for sending to a chat,
     * producing a fully populated {@link ChatMessageInfo}.
     *
     * <p>The messageSecret is generated here and set on both the
     * {@code messageContextInfo} of the container and the
     * {@link ChatMessageInfo} itself. Device list metadata (ICDC) is
     * populated later by {@code IcdcEnricher.enrich()} during the
     * per-device encryption phase.
     *
     * @param chatJid   the recipient chat JID
     * @param container the raw message container
     * @return the prepared message info, ready for the send pipeline
     *
     * @implNote WAWebOutgoingMessage.createOutgoingMessageProtobuf:
     * generates the message ID, populates messageContextInfo, and
     * applies addon encryption.
     * WAWebE2EProtoGenerator.getProtobufMessage: sets messageSecret
     * on messageContextInfo when not invoking a bot.
     */
    ChatMessageInfo prepareChat(Jid chatJid, MessageContainer container) {
        Objects.requireNonNull(chatJid, "chatJid");
        Objects.requireNonNull(container, "container");

        var localJid = store.jid()
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        // WAWebMsgKeyNewId.genMsgKeyUint: uses getMePnUserOrThrow_DO_NOT_USE()
        // (the sender's own PN user JID), not the chat/recipient JID
        var messageId = MessageIdGenerator.generate(MessageIdVersion.V2, localJid);
        var timestamp = Instant.now();

        // WAWebSendTextMsgChatAction / WAWebChatForwardMessage:
        // generate 32-byte message secret via crypto.getRandomValues(new Uint8Array(32))
        var messageSecret = FastRandomUtils.randomByteArray(MESSAGE_SECRET_SIZE);

        // WAWebAddonEncryptAddonMsgData: validate or convert addon content
        var preparedContainer = prepareAddonContent(container, chatJid, localJid);

        // WAWebE2EProtoGenerator.getProtobufMessage: sets messageSecret on
        // messageContextInfo. Device list metadata is populated later by
        // IcdcEnricher.enrich() in the per-device encryption phase.
        var deviceInfo = new ChatMessageContextInfoBuilder()
                .messageSecret(messageSecret)
                .build();
        preparedContainer = preparedContainer.withMessageContextInfo(deviceInfo);

        var key = new MessageKeyBuilder()
                .id(messageId)
                .parentJid(chatJid)
                .fromMe(true)
                .senderJid(localJid)
                .build();

        return new ChatMessageInfoBuilder()
                .status(MessageStatus.PENDING)
                .senderJid(localJid)
                .key(key)
                .message(preparedContainer)
                .timestamp(timestamp)
                .broadcast(chatJid.hasServer(JidServer.broadcast()))
                .messageSecret(messageSecret)
                .build();
    }

    /**
     * Prepares a {@link MessageContainer} for sending to a newsletter,
     * producing a fully populated {@link NewsletterMessageInfo}.
     *
     * @param newsletterJid the newsletter JID
     * @param container     the raw message container
     * @return the prepared message info, ready for the send pipeline
     *
     * @implNote WAWebNewsletterSendMessageQueryJob: newsletters don't use
     *           E2E encryption or messageSecret, so the container is sent as plaintext.
     */
    NewsletterMessageInfo prepareNewsletter(Jid newsletterJid, MessageContainer container) {
        Objects.requireNonNull(newsletterJid, "newsletterJid");
        Objects.requireNonNull(container, "container");

        var localJid = store.jid()
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        var newsletter = store.findNewsletterByJid(newsletterJid)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cannot send to a newsletter that you didn't join: " + newsletterJid));
        var oldServerId = newsletter.newestMessage()
                .map(NewsletterMessageInfo::serverId)
                .orElse(0);
        // WAWebMsgKeyNewId.genMsgKeyUint: uses getMePnUserOrThrow_DO_NOT_USE()
        // (the sender's own PN user JID), not the newsletter JID
        var key = new MessageKeyBuilder()
                .id(MessageIdGenerator.generate(MessageIdVersion.V2, localJid))
                .parentJid(newsletterJid)
                .fromMe(true)
                .build();
        return new NewsletterMessageInfoBuilder()
                .key(key)
                .serverId(oldServerId + 1)
                .timestamp(Instant.now())
                .message(container)
                .status(MessageStatus.PENDING)
                .build();
    }

    /**
     * Validates addon encryption state and auto-converts
     * {@link ReactionMessage} to {@link EncReactionMessage} when
     * targeting a CAG group.
     *
     * @param container the original message container
     * @param chatJid   the target chat JID
     * @param selfJid   the sender's JID
     * @return the container, potentially with converted addon content
     *
     * @implNote WAWebAddonEncryptAddonMsgData.encryptAddOn: validates
     * and encrypts addon content before the outer Signal encryption.
     * WAWebSendGroupMsgJob.isCagAddon: detects CAG context for
     * auto-conversion of reactions to encrypted reactions.
     */
    private MessageContainer prepareAddonContent(
            MessageContainer container,
            Jid chatJid,
            Jid selfJid
    ) {

        return switch (container.content()) {
            // PollUpdateMessage: validate encryptedMetadata is populated
            // (the simpleBuilder handles encryption, so this should already be set)
            case PollUpdateMessage poll -> {
                if (poll.metadata().isEmpty()) {
                    throw new IllegalArgumentException(
                            "PollUpdateMessage must have encrypted metadata: "
                            + "use PollUpdateMessageSimpleBuilder to create poll votes");
                }
                yield container;
            }

            // ReactionMessage in CAG context: auto-convert to EncryptedReactionMessage
            // WAWebSendGroupMsgJob.isCagAddon: reactions in CAG groups need encryption
            case ReactionMessage reaction when requiresEncryptedReaction(chatJid) -> {
                var parentMessage = resolveParentMessage(chatJid, reaction.key().orElse(null));
                if (parentMessage.isEmpty()) {
                    throw new IllegalArgumentException("Cannot encrypt reaction: parent message not found");
                }
                var encrypted = new EncReactionMessageSimpleBuilder()
                        .reaction(reaction)
                        .parentMessage(parentMessage.get())
                        .selfJid(selfJid)
                        .build();
                yield MessageContainer.of(encrypted);
            }

            // EncryptedReactionMessage: validate already encrypted
            case EncReactionMessage enc -> {
                Objects.requireNonNull(enc.encPayload(),
                        "EncryptedReactionMessage must have encPayload populated");
                Objects.requireNonNull(enc.encIv(),
                        "EncryptedReactionMessage must have encIv populated");
                yield container;
            }

            // EncryptedEventResponseMessage: validate already encrypted
            case EncEventResponseMessage enc -> {
                Objects.requireNonNull(enc.encPayload(),
                        "EncryptedEventResponseMessage must have encPayload populated");
                Objects.requireNonNull(enc.encIv(),
                        "EncryptedEventResponseMessage must have encIv populated");
                yield container;
            }

            // SecretEncryptedMessage: validate already encrypted
            case SecretEncMessage enc -> {
                Objects.requireNonNull(enc.encPayload(),
                        "SecretEncryptedMessage must have encPayload populated");
                Objects.requireNonNull(enc.encIv(),
                        "SecretEncryptedMessage must have encIv populated");
                yield container;
            }

            // CommentMessage in CAG context: auto-convert to EncryptedCommentMessage
            case CommentMessage comment when requiresEncryptedReaction(chatJid) -> {
                var parentMessage = resolveParentMessage(chatJid, comment.targetMessageKey().orElse(null));
                if (parentMessage.isEmpty()) {
                    throw new IllegalArgumentException("Cannot encrypt comment: parent message not found");
                }
                var encrypted = new EncCommentMessageSimpleBuilder()
                        .comment(comment)
                        .parentMessage(parentMessage.get())
                        .selfJid(selfJid)
                        .build();
                yield MessageContainer.of(encrypted);
            }

            // EncryptedCommentMessage: validate already encrypted
            case EncCommentMessage enc -> {
                Objects.requireNonNull(enc.encPayload(),
                        "EncryptedCommentMessage must have encPayload populated");
                Objects.requireNonNull(enc.encIv(),
                        "EncryptedCommentMessage must have encIv populated");
                yield container;
            }

            // All other message types pass through unchanged
            default -> container;
        };
    }

    /**
     * Determines whether a reaction to this chat should be sent as an
     * {@link EncReactionMessage} instead of a plain
     * {@link ReactionMessage}.
     *
     * <p>Encrypted reactions are required for CAG (Community Announcement
     * Group) default subgroups that use LID addressing.
     *
     * @param chatJid the target chat JID
     * @return {@code true} if the chat requires encrypted addon messages
     *
     * @implNote WAWebSendGroupMsgJob.isCagAddon: returns true for
     * reaction_enc in linked groups with LID addressing.
     */
    private boolean requiresEncryptedReaction(Jid chatJid) {
        if (!chatJid.hasGroupOrCommunityServer()) {
            return false;
        }

        var metadata = store.findChatMetadata(chatJid).orElse(null);
        return metadata instanceof GroupMetadata group
                && group.isDefaultSubgroup();
    }

    /**
     * Resolves the parent message referenced by a message key.
     *
     * @param parentJid the chat JID containing the parent message
     * @param key       the message key referencing the parent, or {@code null}
     * @return the resolved parent message, or empty if not found
     *
     * @implNote ADAPTED: WAWebAddonEncryptAddonMsgData.encryptAddOn resolves
     * the parent message from the MsgCollection; Cobalt uses the store.
     */
    private Optional<ChatMessageInfo> resolveParentMessage(Jid parentJid, MessageKey key) {
        return key == null ? Optional.empty() : key.id()
                .flatMap(id -> store.findMessageById(parentJid, id))
                .filter(entry -> entry instanceof ChatMessageInfo)
                .map(entry -> (ChatMessageInfo) entry);
    }
}
