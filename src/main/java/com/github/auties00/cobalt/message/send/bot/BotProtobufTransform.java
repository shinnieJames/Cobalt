package com.github.auties00.cobalt.message.send.bot;

import com.github.auties00.cobalt.model.bot.feedback.BotFeedbackMessage;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.system.FutureProofMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.util.Objects;

/**
 * Applies bot-specific protobuf transforms before encryption.
 *
 * <p>When sending a message to a bot device, the protobuf is modified
 * to avoid leaking the user's message secret, to strip quoted message
 * content for non-bot participants, and to convert PN JIDs to LID for
 * FBID bots.
 *
 * <p>All transforms mutate the container in place via setters.
 *
 * @implNote WAWebE2EProtoGenerator: provides
 * {@code updateBotInvokeMsgProtoCopyForCapi},
 * {@code updateFbidBotProtobuf}, {@code updateBotProtobuf}, and
 * {@code updateFbidBotInvokeProtobuf}.
 */
public final class BotProtobufTransform {
    /**
     * The store used for LID-to-phone lookups during FBID bot transforms.
     *
     * @implNote ADAPTED: WAWebE2EProtoGenerator uses
     * {@code WAWebLidMigrationUtils.toLid} directly; Cobalt uses
     * constructor-injected store.
     */
    private final WhatsAppStore store;

    /**
     * Creates a new bot protobuf transform with the specified store.
     *
     * @param store the store for JID lookups
     * @implNote ADAPTED: WAWebE2EProtoGenerator.updateFbidBotProtobuf
     */
    public BotProtobufTransform(WhatsAppStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Applies the CAPI bot invoke transform: replaces the message
     * secret with the bot-derived secret, strips quoted message
     * content for non-bot participants, and removes remoteJid from
     * protocol message keys.
     *
     * @param container        the message container (mutated in place)
     * @param botMessageSecret the derived bot message secret, or
     *                         {@code null} to just clear the secret
     *
     * @implNote WAWebE2EProtoGenerator.updateBotInvokeMsgProtoCopyForCapi
     */
    public void transformForCapi(MessageContainer container, byte[] botMessageSecret) {
        // Replace messageSecret with botMessageSecret
        container.messageContextInfo().ifPresent(info -> {
            info.setMessageSecret(null);
            info.setBotMessageSecret(botMessageSecret);
        });

        // Strip quoted message for non-bot participants
        stripQuotedMessageForNonBot(container);

        // Strip remoteJid from protocol message keys
        stripProtocolMessageRemoteJid(container);
    }

    /**
     * Applies the FBID bot transform: converts the PN participant
     * JID to LID in quoted message context info.
     *
     * @param container the message container (mutated in place)
     *
     * @implNote WAWebE2EProtoGenerator.updateFbidBotProtobuf
     */
    public void transformForFbidBot(MessageContainer container) {
        var contextInfo = resolveInnerContextInfo(container);
        if (contextInfo == null) {
            return;
        }

        var participant = contextInfo.quotedMessageSenderJid();
        if (participant.isEmpty() || participant.get().hasBotServer()) {
            return;
        }

        store.findLidByPhone(participant.get())
                .ifPresent(contextInfo::setQuotedMessageSenderJid);
    }

    /**
     * Applies the FBID bot invoke transform: converts the protocol
     * message key's participant from PN to LID.
     *
     * @param container the message container (mutated in place)
     *
     * @implNote WAWebE2EProtoGenerator.updateFbidBotInvokeProtobuf
     */
    public void transformForFbidBotInvoke(MessageContainer container) {
        if (!(container.content() instanceof ProtocolMessage pm)) {
            return;
        }

        var key = pm.key().orElse(null);
        if (key == null) {
            return;
        }

        var participant = key.senderJid().orElse(null);
        if (participant == null || participant.hasBotServer() || participant.hasLidServer()) {
            return;
        }

        store.findLidByPhone(participant).ifPresent(key::setSenderJid);
    }

    /**
     * Applies the generic bot transform: strips remoteJid and
     * participant from protocol message keys.
     *
     * @param container the message container (mutated in place)
     *
     * @implNote WAWebE2EProtoGenerator.updateBotProtobuf
     */
    public void transformForBot(MessageContainer container) {
        if (!(container.content() instanceof ProtocolMessage pm)) {
            return;
        }

        pm.key().ifPresent(key -> {
            key.setParentJid(null);
            key.setSenderJid(null);
        });
    }

    /**
     * Resolves the inner {@link ContextInfo}, handling the
     * botInvokeMessage {@link FutureProofMessage} wrapper.
     *
     * @param container the message container to resolve from
     * @return the inner context info, or {@code null} if not present
     * @implNote ADAPTED: WAWebE2EProtoGenerator.updateBotInvokeMsgProtoCopyForCapi
     * and updateFbidBotProtobuf both resolve contextInfo from
     * {@code botInvokeMessage.message.extendedTextMessage.contextInfo}
     * or {@code extendedTextMessage.contextInfo}. Cobalt's
     * {@code MessageContainer.content()} already unwraps through
     * the botInvokeMessage FutureProofMessage wrapper.
     */
    private static ContextInfo resolveInnerContextInfo(MessageContainer container) {
        return container.content() instanceof ContextualMessage contextualMessage
                ? contextualMessage.contextInfo().orElse(null)
                : null;
    }

    /**
     * Strips the quoted message from the inner context info if the
     * quoted participant is not a bot.
     *
     * @implNote WAWebE2EProtoGenerator.updateBotInvokeMsgProtoCopyForCapi:
     * deletes quotedMessage, stanzaId, remoteJid, participant when
     * the participant is not a bot.
     */
    private static void stripQuotedMessageForNonBot(MessageContainer container) {
        var contextInfo = resolveInnerContextInfo(container);
        if (contextInfo == null) {
            return;
        }

        var participant = contextInfo.quotedMessageSenderJid().orElse(null);
        if (participant == null || participant.hasBotServer()) {
            return;
        }

        contextInfo.clearQuotedMessage();
    }

    /**
     * Strips remoteJid from protocol message keys (feedback and revoke).
     *
     * @implNote WAWebE2EProtoGenerator.updateBotInvokeMsgProtoCopyForCapi:
     * deletes botFeedbackMessage.messageKey.remoteJid and
     * protocolMessage.key.remoteJid for revoke messages.
     */
    private static void stripProtocolMessageRemoteJid(MessageContainer container) {
        if (!(container.content() instanceof ProtocolMessage pm)) {
            return;
        }

        // Strip remoteJid from the protocol message key
        pm.key().ifPresent(key -> key.setParentJid(null));

        // Strip remoteJid from the bot feedback message key
        pm.botFeedbackMessage()
                .flatMap(BotFeedbackMessage::messageKey)
                .ifPresent(key -> key.setParentJid(null));
    }
}
