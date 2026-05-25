package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.message.addon.EncMessageFactory;
import com.github.auties00.cobalt.message.send.id.MessageIdGenerator;
import com.github.auties00.cobalt.message.send.id.MessageIdVersion;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
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
import com.github.auties00.cobalt.util.DataUtils;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Converts a raw {@link MessageContainer} into a fully-populated
 * {@link MessageInfo} ready for dispatch.
 *
 * <p>The preparer generates the wire id and a 32-byte per-message secret,
 * stamps the secret onto both the resulting info and the container's
 * {@code messageContextInfo} so the encryption stage can read it back, and
 * auto-promotes a {@link ReactionMessage} or {@link CommentMessage} into the
 * corresponding {@code Enc*} addon variant when the chat is a CAG default
 * subgroup. ICDC metadata is populated later by the per-device encryption
 * stage; random padding is added at the Signal binary level by
 * {@link com.github.auties00.cobalt.message.send.crypto.MessageEncryption}.
 */
@WhatsAppWebModule(moduleName = "WAWebOutgoingMessage")
@WhatsAppWebModule(moduleName = "WAWebE2EProtoGenerator")
@WhatsAppWebModule(moduleName = "WAWebAddonEncryptAddonMsgData")
final class MessagePreparer {
    /**
     * Surfaces preparation diagnostics.
     */
    private static final System.Logger LOGGER = System.getLogger(MessagePreparer.class.getName());

    /**
     * Defines the byte length of the per-message {@code messageSecret}
     * generated for every outbound chat message.
     *
     * <p>The receiver asserts the same 32-byte size via WA Web's
     * {@code getValidatedMessageSecret}; a mismatch causes the receiver to
     * reject the message with the
     * {@link com.github.auties00.cobalt.ack.NackReason#MISSING_MESSAGE_SECRET}
     * nack.
     */
    @WhatsAppWebExport(moduleName = "WAWebAddonEncryptionError", exports = "getValidatedMessageSecret",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final int MESSAGE_SECRET_SIZE = 32;

    /**
     * Supplies self-JID resolution, newsletter lookups, chat metadata, and
     * parent-message resolution during addon promotion.
     */
    private final WhatsAppStore store;

    /**
     * Constructs a {@link MessagePreparer} bound to the supplied store.
     *
     * <p>Constructed once by {@link MessageSendingService}; embedders should not
     * instantiate directly.
     *
     * @param store the {@link WhatsAppStore} providing JID and metadata lookups
     * @throws NullPointerException if {@code store} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebE2EProtoGenerator", exports = "getProtobufMessage",
            adaptation = WhatsAppAdaptation.ADAPTED)
    MessagePreparer(WhatsAppStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Builds a fully-populated {@link ChatMessageInfo} for the supplied chat
     * {@link Jid} and {@link MessageContainer}.
     *
     * <p>Generates a {@link MessageIdVersion#V2} wire id, samples a fresh
     * 32-byte {@code messageSecret}, runs the addon validation and
     * auto-promotion pipeline, and stamps the secret onto both the info and the
     * inner container's {@code messageContextInfo}. The {@code broadcast} flag
     * is set when the chat is the status broadcast account.
     *
     * @param chatJid   the recipient chat {@link Jid}
     * @param container the raw {@link MessageContainer}
     * @return the prepared {@link ChatMessageInfo}
     * @throws NullPointerException  if any argument is {@code null}
     * @throws IllegalStateException if the client is not logged in
     */
    @WhatsAppWebExport(moduleName = "WAWebOutgoingMessage", exports = "createOutgoingMessageProtobuf",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebOutgoingMessage", exports = "createOutgoingMsgModelProtobuf",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebE2EProtoGenerator", exports = "getProtobufMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    ChatMessageInfo prepareChat(Jid chatJid, MessageContainer container) {
        Objects.requireNonNull(chatJid, "chatJid");
        Objects.requireNonNull(container, "container");

        var localJid = store.jid()
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        var messageId = MessageIdGenerator.generate(MessageIdVersion.V2, localJid);
        var timestamp = Instant.now();

        var messageSecret = DataUtils.randomByteArray(MESSAGE_SECRET_SIZE);

        var preparedContainer = prepareAddonContent(container, chatJid, localJid);

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
     * Builds a fully-populated {@link NewsletterMessageInfo} for the supplied
     * newsletter {@link Jid} and {@link MessageContainer}.
     *
     * <p>Newsletter sends are plaintext SMAX publishes so no
     * {@code messageSecret} is generated and no addon stage runs; the only
     * extras over a chat prepare are looking up the newsletter to derive the
     * monotonically-increasing {@code serverId} and the membership
     * precondition.
     *
     * @param newsletterJid the newsletter {@link Jid}
     * @param container     the raw {@link MessageContainer}
     * @return the prepared {@link NewsletterMessageInfo}
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalStateException    if the client is not logged in
     * @throws IllegalArgumentException if the user has not joined the newsletter
     */
    @WhatsAppWebExport(moduleName = "WAWebNewsletterSendMessageQueryJob", exports = "querySendNewsletterMessage",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
     * Validates the addon-encryption state of the container and auto-promotes a
     * {@link ReactionMessage} or {@link CommentMessage} to its {@code Enc*}
     * variant when the target is a CAG default subgroup.
     *
     * <p>Plain-content payloads pass through unchanged. CAG reactions and
     * comments are promoted via {@link EncMessageFactory} using the resolved
     * parent message; payloads already in {@code Enc*} form (or
     * {@link SecretEncMessage}) must carry both their {@code encPayload} and
     * {@code encIv}, otherwise the call fails fast.
     *
     * @param container the raw {@link MessageContainer}
     * @param chatJid   the target chat {@link Jid}
     * @param selfJid   the sender's own {@link Jid}
     * @return the container, possibly with addon content converted in place
     * @throws IllegalArgumentException if a poll vote is missing its encrypted
     *                                  metadata, an {@code Enc*}-typed addon is
     *                                  missing its payload or IV, or a CAG addon
     *                                  cannot resolve its parent message
     */
    @WhatsAppWebExport(moduleName = "WAWebAddonEncryptAddonMsgData", exports = "encryptAddOn",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebAddonEncryptAddonMsgData", exports = "createDualEncryptionHelper",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private MessageContainer prepareAddonContent(
            MessageContainer container,
            Jid chatJid,
            Jid selfJid
    ) {

        return switch (container.content()) {
            case PollUpdateMessage poll -> {
                if (poll.metadata().isEmpty()) {
                    throw new IllegalArgumentException(
                            "PollUpdateMessage must have encrypted metadata: "
                            + "use PollUpdateMessageSimpleBuilder to create poll votes");
                }
                yield container;
            }

            case ReactionMessage reaction when requiresEncryptedReaction(chatJid) -> {
                var parentMessage = resolveParentMessage(chatJid, reaction.key().orElse(null));
                if (parentMessage.isEmpty()) {
                    throw new IllegalArgumentException("Cannot encrypt reaction: parent message not found");
                }
                var encrypted = EncMessageFactory.encryptReaction(reaction, parentMessage.get(), selfJid);
                yield MessageContainer.of(encrypted);
            }

            case EncReactionMessage enc -> {
                Objects.requireNonNull(enc.encPayload(),
                        "EncryptedReactionMessage must have encPayload populated");
                Objects.requireNonNull(enc.encIv(),
                        "EncryptedReactionMessage must have encIv populated");
                yield container;
            }

            case EncEventResponseMessage enc -> {
                Objects.requireNonNull(enc.encPayload(),
                        "EncryptedEventResponseMessage must have encPayload populated");
                Objects.requireNonNull(enc.encIv(),
                        "EncryptedEventResponseMessage must have encIv populated");
                yield container;
            }

            case SecretEncMessage enc -> {
                Objects.requireNonNull(enc.encPayload(),
                        "SecretEncryptedMessage must have encPayload populated");
                Objects.requireNonNull(enc.encIv(),
                        "SecretEncryptedMessage must have encIv populated");
                yield container;
            }

            case CommentMessage comment when requiresEncryptedReaction(chatJid) -> {
                var parentMessage = resolveParentMessage(chatJid, comment.targetMessageKey().orElse(null));
                if (parentMessage.isEmpty()) {
                    throw new IllegalArgumentException("Cannot encrypt comment: parent message not found");
                }
                var encrypted = EncMessageFactory.encryptComment(comment, parentMessage.get(), selfJid);
                yield MessageContainer.of(encrypted);
            }

            case EncCommentMessage enc -> {
                Objects.requireNonNull(enc.encPayload(),
                        "EncryptedCommentMessage must have encPayload populated");
                Objects.requireNonNull(enc.encIv(),
                        "EncryptedCommentMessage must have encIv populated");
                yield container;
            }

            default -> container;
        };
    }

    /**
     * Returns whether a reaction or comment to the supplied chat must be sent as
     * an encrypted addon.
     *
     * <p>Encrypted addons are required for CAG default subgroups (the same
     * branch WA Web's {@code isCagAddon} guards); standard groups, 1:1 chats,
     * and non-default community subgroups use the plain reaction payload.
     *
     * @param chatJid the target chat {@link Jid}
     * @return {@code true} when {@code chatJid} resolves to a CAG default
     *         subgroup
     */
    @WhatsAppWebExport(moduleName = "WAWebSendGroupMsgJob", exports = "isCagAddon",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean requiresEncryptedReaction(Jid chatJid) {
        if (!chatJid.hasGroupOrCommunityServer()) {
            return false;
        }

        var metadata = store.findChatMetadata(chatJid).orElse(null);
        return metadata instanceof GroupMetadata group
                && group.isDefaultSubgroup();
    }

    /**
     * Resolves the parent chat message referenced by the given key.
     *
     * <p>The addon-promotion branches use this to fetch the target message; the
     * encryption helper needs the parent's id and sender to derive the addon's
     * symmetric key.
     *
     * @param parentJid the chat {@link Jid} containing the parent message
     * @param key       the {@link MessageKey} referencing the parent, or
     *                  {@code null}
     * @return the resolved {@link ChatMessageInfo}, or {@link Optional#empty()}
     *         when not found
     */
    @WhatsAppWebExport(moduleName = "WAWebAddonEncryptAddonMsgData", exports = "encryptAddOn",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Optional<ChatMessageInfo> resolveParentMessage(Jid parentJid, MessageKey key) {
        return key == null ? Optional.empty() : key.id()
                .flatMap(id -> store.findMessageById(parentJid, id))
                .filter(entry -> entry instanceof ChatMessageInfo)
                .map(entry -> (ChatMessageInfo) entry);
    }
}
