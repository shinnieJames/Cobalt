package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.model.chat.group.GroupMetadata;
import com.github.auties00.cobalt.model.info.*;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.*;
import com.github.auties00.cobalt.model.message.standard.EncryptedCommentMessageSimpleBuilder;
import com.github.auties00.cobalt.model.message.standard.EncryptedReactionMessageSimpleBuilder;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.sync.DeviceListMetadataBuilder;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.util.SecureBytes;

import java.time.Instant;
import java.util.Objects;

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
 *   <li>Generate random padding bytes</li>
 *   <li>Validate addon encryption (encrypted addons must have
 *       their {@code encPayload}/{@code encIv} populated)</li>
 *   <li>Auto-convert {@link ReactionMessage} to
 *       {@link EncryptedReactionMessage} when targeting a CAG group</li>
 *   <li>Populate {@code DeviceContextInfo} with messageSecret,
 *       paddingBytes, and device list metadata</li>
 *   <li>Wrap everything into the appropriate {@link MessageInfo}</li>
 * </ol>
 *
 * @apiNote WAWebOutgoingMessage.createOutgoingMessageProtobuf: creates
 * the protobuf from message data, populating messageContextInfo with
 * messageSecret, paddingBytes, and device metadata.
 * WAWebE2EProtoGenerator.populateMessageContextInfo: sets the
 * messageContextInfo fields on the outgoing protobuf.
 * WAWebAddonEncryptAddonMsgData.encryptAddOn: applies inner AES-GCM
 * encryption for addon message types.
 */
final class MessagePreparer {
    private static final System.Logger LOGGER = System.getLogger("MessagePreparer");

    /**
     * Size of the message secret in bytes.
     *
     * @apiNote WAWebAddonEncryptionError.getValidatedMessageSecret:
     * validates that messageSecret is exactly 32 bytes.
     */
    private static final int MESSAGE_SECRET_SIZE = 32;

    /**
     * Minimum padding size in bytes.
     *
     * @apiNote WAWebE2EProtoGenerator.populateMessageContextInfo:
     * adds random padding bytes to messageContextInfo.
     */
    private static final int MIN_PADDING_SIZE = 1;

    /**
     * Maximum padding size in bytes.
     */
    private static final int MAX_PADDING_SIZE = 16;

    private final WhatsAppStore store;

    MessagePreparer(WhatsAppStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Prepares a {@link MessageContainer} for sending to a chat,
     * producing a fully populated {@link ChatMessageInfo}.
     *
     * @param chatJid   the recipient chat JID
     * @param container the raw message container
     * @return the prepared message info, ready for the send pipeline
     *
     * @apiNote WAWebOutgoingMessage.createOutgoingMessageProtobuf:
     * generates the message ID, populates messageContextInfo, and
     * applies addon encryption.
     */
    ChatMessageInfo prepareChat(Jid chatJid, MessageContainer container) {
        Objects.requireNonNull(chatJid, "chatJid");
        Objects.requireNonNull(container, "container");

        var localJid = store.jid()
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        var messageId = ChatMessageKey.randomId(store.clientType());
        var timestamp = Instant.now().getEpochSecond();

        // WAWebOutgoingMessage: generate 32-byte message secret
        var messageSecret = SecureBytes.random(MESSAGE_SECRET_SIZE);

        // WAWebAddonEncryptAddonMsgData: validate or convert addon content
        var preparedContainer = prepareAddonContent(container, chatJid, localJid);

        // WAWebE2EProtoGenerator.populateMessageContextInfo: attach
        // messageSecret and paddingBytes to the container's deviceInfo
        var paddingBytes = SecureBytes.random(MIN_PADDING_SIZE, MAX_PADDING_SIZE);
        var deviceInfoMetadata = new DeviceListMetadataBuilder()
                .senderTimestamp(timestamp)
                .build();
        var deviceInfo = new DeviceContextInfoBuilder()
                .deviceListMetadata(deviceInfoMetadata)
                .deviceListMetadataVersion(2)
                .messageSecret(messageSecret)
                .paddingBytes(paddingBytes)
                .build();
        preparedContainer = preparedContainer.withDeviceInfo(deviceInfo);

        var key = new ChatMessageKeyBuilder()
                .id(messageId)
                .chatJid(chatJid)
                .fromMe(true)
                .senderJid(localJid)
                .build();

        return new ChatMessageInfoBuilder()
                .status(MessageStatus.PENDING)
                .senderJid(localJid)
                .key(key)
                .message(preparedContainer)
                .timestampSeconds(timestamp)
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
     * @apiNote WAWebNewsletterSendMessageQueryJob: newsletters don't use
     *          E2E encryption or messageSecret, so the container is sent as plaintext.
     */
    NewsletterMessageInfo prepareNewsletter(Jid newsletterJid, MessageContainer container) {
        Objects.requireNonNull(newsletterJid, "newsletterJid");
        Objects.requireNonNull(container, "container");

        var newsletter = store.findNewsletterByJid(newsletterJid)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cannot send to a newsletter that you didn't join: " + newsletterJid));
        var oldServerId = newsletter.newestMessage()
                .map(NewsletterMessageInfo::serverId)
                .orElse(0);
        var info = new NewsletterMessageInfoBuilder()
                .id(ChatMessageKey.randomId(store.clientType()))
                .serverId(oldServerId + 1)
                .timestampSeconds(Instant.now().getEpochSecond())
                .message(container)
                .status(MessageStatus.PENDING)
                .build();
        info.setNewsletter(newsletter);
        return info;
    }

    /**
     * Validates addon encryption state and auto-converts
     * {@link ReactionMessage} to {@link EncryptedReactionMessage} when
     * targeting a CAG group.
     *
     * @param container the original message container
     * @param chatJid   the target chat JID
     * @param selfJid   the sender's JID
     * @return the container, potentially with converted addon content
     *
     * @apiNote WAWebAddonEncryptAddonMsgData.encryptAddOn: validates
     * and encrypts addon content before the outer Signal encryption.
     * WAWebSendGroupMsgJob.isCagAddon: detects CAG context for
     * auto-conversion of reactions to encrypted reactions.
     */
    private MessageContainer prepareAddonContent(
            MessageContainer container,
            Jid chatJid,
            Jid selfJid
    ) {
        var message = container.content();

        return switch (message) {
            // PollUpdateMessage: validate encryptedMetadata is populated
            // (the simpleBuilder handles encryption, so this should already be set)
            case PollUpdateMessage poll -> {
                if (poll.encryptedMetadata().isEmpty()) {
                    throw new IllegalArgumentException(
                            "PollUpdateMessage must have encrypted metadata: "
                            + "use PollUpdateMessageSimpleBuilder to create poll votes");
                }
                yield container;
            }

            // ReactionMessage in CAG context: auto-convert to EncryptedReactionMessage
            // WAWebSendGroupMsgJob.isCagAddon: reactions in CAG groups need encryption
            case ReactionMessage reaction when requiresEncryptedReaction(chatJid) -> {
                var parentMessage = resolveParentMessage(chatJid, reaction.key());
                if (parentMessage == null) {
                    throw new IllegalArgumentException("Cannot encrypt reaction: parent message not found for " + reaction.key().id());
                }
                var encrypted = new EncryptedReactionMessageSimpleBuilder()
                        .reaction(reaction)
                    .parentMessage(parentMessage)
                        .selfJid(selfJid)
                        .build();
                yield MessageContainer.of(encrypted);
            }

            // EncryptedReactionMessage: validate already encrypted
            case EncryptedReactionMessage enc -> {
                Objects.requireNonNull(enc.encPayload(),
                        "EncryptedReactionMessage must have encPayload populated");
                Objects.requireNonNull(enc.encIv(),
                        "EncryptedReactionMessage must have encIv populated");
                yield container;
            }

            // EncryptedEventResponseMessage: validate already encrypted
            case EncryptedEventResponseMessage enc -> {
                Objects.requireNonNull(enc.encPayload(),
                        "EncryptedEventResponseMessage must have encPayload populated");
                Objects.requireNonNull(enc.encIv(),
                        "EncryptedEventResponseMessage must have encIv populated");
                yield container;
            }

            // SecretEncryptedMessage: validate already encrypted
            case SecretEncryptedMessage enc -> {
                Objects.requireNonNull(enc.encPayload(),
                        "SecretEncryptedMessage must have encPayload populated");
                Objects.requireNonNull(enc.encIv(),
                        "SecretEncryptedMessage must have encIv populated");
                yield container;
            }

            // CommentMessage in CAG context: auto-convert to EncryptedCommentMessage
            case CommentMessage comment when requiresEncryptedReaction(chatJid) -> {
                var parentMessage = resolveParentMessage(chatJid, comment.targetMessageKey());
                if (parentMessage == null) {
                    throw new IllegalArgumentException("Cannot encrypt comment: parent message not found for " + comment.targetMessageKey().id());
                }
                var encrypted = new EncryptedCommentMessageSimpleBuilder()
                        .comment(comment)
                        .parentMessage(parentMessage)
                        .selfJid(selfJid)
                        .build();
                yield MessageContainer.of(encrypted);
            }

            // EncryptedCommentMessage: validate already encrypted
            case EncryptedCommentMessage enc -> {
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
     * {@link EncryptedReactionMessage} instead of a plain
     * {@link ReactionMessage}.
     *
     * <p>Encrypted reactions are required for CAG (Community Announcement
     * Group) default subgroups that use LID addressing.
     *
     * @apiNote WAWebSendGroupMsgJob.isCagAddon: returns true for
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
     */
    private ChatMessageInfo resolveParentMessage(Jid chatJid, ChatMessageKey key) {
        return (ChatMessageInfo) store.findMessageById(chatJid, key.id())
                .filter(msg -> msg instanceof ChatMessageInfo)
                .orElse(null);
    }
}
