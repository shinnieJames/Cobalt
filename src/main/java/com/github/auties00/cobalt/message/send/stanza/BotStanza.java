package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.send.bot.BotMessageSecret;
import com.github.auties00.cobalt.message.send.bot.BotProtobufTransform;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryptedPayload;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.model.chat.ChatMessageContextInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.security.GeneralSecurityException;
import java.util.Objects;

/**
 * Builds the {@code <bot>} stanza child node for bot messages.
 *
 * <p>Bot messages are encrypted separately to the bot's device and
 * included in the stanza as a {@code <bot>} node alongside the
 * regular {@code <enc>} or {@code <participants>} nodes.
 *
 * <p>The {@code <bot>} node structure is:
 * <pre>{@code
 * <bot type="feedback|prompt" persona_type="...">
 *   <to jid="botDevice">
 *     <enc v="2" type="pkmsg|msg">ciphertext</enc>
 *   </to>
 * </bot>
 * }</pre>
 *
 * @apiNote WAWebSendMsgCreateFanoutStanza: builds the bot body node
 * with type, persona_type, and the encrypted payload for the bot device.
 * WAWebSendGroupSkmsgJob: builds the bot node for group SKMSG stanzas.
 */
public final class BotStanza {
    private static final System.Logger LOGGER = System.getLogger(BotStanza.class.getName());

    private final MessageEncryption encryption;
    private final BotProtobufTransform protobufTransform;

    public BotStanza(MessageEncryption encryption, BotProtobufTransform protobufTransform) {
        this.encryption = Objects.requireNonNull(encryption, "encryption");
        this.protobufTransform = Objects.requireNonNull(protobufTransform, "protobufTransform");
    }

    /**
     * Builds the {@code <bot>} node for the given message, or
     * returns {@code null} if the message has no bot involvement.
     *
     * <p>Derives the bot JID, feedback flag, and message secret from
     * the message info and chat JID.  Applies bot protobuf transforms
     * and encrypts to the bot device.
     *
     * @param messageInfo the outgoing message
     * @param chatJid     the target chat JID
     * @return the bot node, or {@code null}
     *
     * @apiNote WAWebSendMsgCreateFanoutStanza: determines bot involvement
     * from invokedBotWid, isBotFeedbackMessage, etc., then encrypts
     * a transformed protobuf to the bot device.
     */
    public Node build(ChatMessageInfo messageInfo, Jid chatJid) {
        var botJid = resolveBotJid(messageInfo, chatJid);
        if (botJid == null) {
            return null;
        }

        var isFeedback = isBotFeedback(messageInfo);
        var container = messageInfo.message();

        // Derive bot message secret from the message secret
        var messageSecret = container.messageContextInfo()
                .flatMap(ChatMessageContextInfo::messageSecret)
                .orElse(null);
        byte[] botSecret = null;
        if (messageSecret != null) {
            try {
                botSecret = BotMessageSecret.derive(messageSecret);
            } catch (GeneralSecurityException e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Failed to derive bot message secret: {0}", e.getMessage());
            }
        }

        // Apply bot protobuf transforms
        protobufTransform.transformForCapi(container, botSecret);
        if (isFbidBot(botJid)) {
            protobufTransform.transformForFbidBot(container);
        }
        protobufTransform.transformForBot(container);

        // Encrypt to bot device
        var plaintext = MessageContainerSpec.encode(container);
        MessageEncryptedPayload payload;
        try {
            payload = encryption.encryptForDevice(botJid, plaintext);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Bot encryption failed for {0}: {1}", botJid, e.getMessage());
            return null;
        }

        // Build stanza
        var encNode = new NodeBuilder()
                .description("enc")
                .attribute("v", String.valueOf(MessageEncryption.CIPHERTEXT_VERSION))
                .attribute("type", payload.type().protocolValue())
                .content(payload.ciphertext())
                .build();
        var toNode = new NodeBuilder()
                .description("to")
                .attribute("jid", botJid)
                .content(encNode)
                .build();
        return new NodeBuilder()
                .description("bot")
                .attribute("type", isFeedback ? "feedback" : null)
                .content(toNode)
                .build();
    }

    /**
     * Builds the metadata-only {@code <bot>} node that carries bot
     * invocation type, business bot classification, AI thread ID,
     * and AI mode selection attributes.
     *
     * <p>This node is separate from the encrypted bot body built by
     * {@link #build(ChatMessageInfo, Jid)}.  It carries stanza-level
     * metadata that the server uses for routing and analytics.
     *
     * @param botMsgBodyType the bot message body type: {@code "prompt"},
     *                       {@code "command"}, {@code "request_welcome"},
     *                       or {@code null} if not a bot message
     * @param bizBotType the business bot type: {@code "1p_partial"},
     *                   {@code "3p_full"}, or {@code null}
     * @param clientThreadId the AI thread ID, or {@code null}
     * @param modeSelection the user's AI mode selection:
     *                      {@code "default"} or {@code "think_hard"},
     *                      or {@code null} if not applicable
     * @param modeSelected  the dynamic mode override string, or
     *                      {@code null} if not applicable
     * @return the bot metadata node, or {@code null} if no metadata applies
     *
     * @implNote WAWebSendMsgCreateFanoutStanza: builds {@code me} node with
     * type (prompt/command/request_welcome), local_automated_type
     * (1p_partial/3p_full), client_thread_id from AI thread,
     * mode_selection (default/think_hard), and mode_selected (dynamic
     * override from botModeOverride).
     */
    public static Node buildMetadata(
            String botMsgBodyType,
            String bizBotType,
            String clientThreadId,
            String modeSelection,
            String modeSelected
    ) {
        // WAWebSendMsgCreateFanoutStanza: only emit if any attribute is present
        if (botMsgBodyType == null && bizBotType == null && clientThreadId == null
                && modeSelection == null && modeSelected == null) {
            return null;
        }

        return new NodeBuilder()
                .description("bot")
                .attribute("type", botMsgBodyType)
                .attribute("local_automated_type", bizBotType)
                .attribute("client_thread_id", clientThreadId)
                .attribute("mode_selection", modeSelection)
                .attribute("mode_selected", modeSelected)
                .build();
    }

    /**
     * Builds the metadata-only {@code <bot>} node without AI mode
     * selection attributes.
     *
     * <p>Convenience overload that delegates to the full
     * {@link #buildMetadata(String, String, String, String, String)}
     * with {@code null} for {@code modeSelection} and
     * {@code modeSelected}.
     *
     * @param botMsgBodyType the bot message body type, or {@code null}
     * @param bizBotType     the business bot type, or {@code null}
     * @param clientThreadId the AI thread ID, or {@code null}
     * @return the bot metadata node, or {@code null} if no metadata applies
     *
     * @implNote WAWebSendMsgCreateFanoutStanza: delegates to the
     * five-parameter variant with {@code null} mode attributes.
     */
    public static Node buildMetadata(
            String botMsgBodyType,
            String bizBotType,
            String clientThreadId
    ) {
        return buildMetadata(botMsgBodyType, bizBotType, clientThreadId, null, null);
    }

    /**
     * Builds the encrypted bot node for group messages when the group
     * has an open Meta AI bot.
     *
     * @param messageInfo    the outgoing message
     * @param isOpenBotGroup whether the group has the open bot enabled
     * @return the bot node, or {@code null} if not applicable
     *
     * @apiNote WAWebSendGroupSkmsgJob function L: resolves bot JID
     * to META_BOT_FBID_WID when isOpenBotGroup, encrypts to bot device.
     */
    public Node buildForGroup(ChatMessageInfo messageInfo, boolean isOpenBotGroup) {
        if (!isOpenBotGroup) {
            return null;
        }

        var botJid = Jid.metaAiBotAccount();
        var container = messageInfo.message();

        var messageSecret = container.messageContextInfo()
                .flatMap(ChatMessageContextInfo::messageSecret)
                .orElse(null);
        byte[] botSecret = null;
        if (messageSecret != null) {
            try {
                botSecret = BotMessageSecret.derive(messageSecret);
            } catch (GeneralSecurityException e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Failed to derive bot message secret for open group bot: {0}", e.getMessage());
            }
        }

        protobufTransform.transformForCapi(container, botSecret);
        protobufTransform.transformForBot(container);

        var plaintext = MessageContainerSpec.encode(container);
        MessageEncryptedPayload payload;
        try {
            payload = encryption.encryptForDevice(botJid, plaintext);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Open group bot encryption failed: {0}", e.getMessage());
            return null;
        }

        var encNode = new NodeBuilder()
                .description("enc")
                .attribute("v", String.valueOf(MessageEncryption.CIPHERTEXT_VERSION))
                .attribute("type", payload.type().protocolValue())
                .content(payload.ciphertext())
                .build();
        var toNode = new NodeBuilder()
                .description("to")
                .attribute("jid", botJid)
                .content(encNode)
                .build();
        return new NodeBuilder()
                .description("bot")
                .content(toNode)
                .build();
    }

    /**
     * Resolves the bot JID from the message and chat context.
     *
     * @return the bot device JID, or {@code null} if no bot is involved
     *
     * @apiNote WAWebSendMsgCreateFanoutStanza: uses invokedBotWid
     * for invoke messages, protocolMessageKey.participant for feedback.
     */
    private static Jid resolveBotJid(ChatMessageInfo messageInfo, Jid chatJid) {
        // 1:1 bot chats: the chat JID is the bot
        if (chatJid.hasBotServer()) {
            return chatJid;
        }

        // Feedback: the bot is the target of the protocol message
        if (isBotFeedback(messageInfo)
                && messageInfo.message().content() instanceof ProtocolMessage pm) {
            return pm.key()
                    .flatMap(MessageKey::senderJid)
                    .filter(Jid::hasBotServer)
                    .orElse(null);
        }

        return null;
    }

    /**
     * Checks whether the message is a bot feedback protocol message.
     *
     * @apiNote WAWebMsgGetters.getIsBotFeedbackMessage:
     * {@code type === PROTOCOL && subtype === "bot_feedback"}
     */
    private static boolean isBotFeedback(ChatMessageInfo messageInfo) {
        return messageInfo.message().content() instanceof ProtocolMessage pm
                && pm.type().orElse(null) == ProtocolMessage.Type.BOT_FEEDBACK_MESSAGE;
    }

    /**
     * Checks whether the JID is an FBID bot (numeric user on bot server).
     *
     * @apiNote WAWebWid.isFbidBot
     */
    private static boolean isFbidBot(Jid jid) {
        if (!jid.hasBotServer()) {
            return false;
        }
        var user = jid.user();
        return user != null && !user.isEmpty()
                && user.chars().allMatch(Character::isDigit);
    }
}
