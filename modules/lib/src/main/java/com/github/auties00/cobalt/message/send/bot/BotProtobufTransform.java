package com.github.auties00.cobalt.message.send.bot;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.bot.feedback.BotFeedbackMessage;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.message.system.FutureProofMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.util.Objects;

/**
 * Applies the bot-specific protobuf rewrites that precede encryption of a
 * message bound for a bot or FBID-bot recipient.
 *
 * @apiNote
 * Invoked by the per-device fanout pipeline (matching WA Web's
 * {@code WAWebSendMsgCreateDeviceStanza} / {@code WAWebSendMsgCreateFanoutStanza}
 * call-sites) right before {@link com.github.auties00.cobalt.message.send.crypto.MessageEncryption#encryptForDevice}
 * is called. Each method mutates the supplied {@link MessageContainer} in
 * place via setters on the {@link com.github.auties00.cobalt.model.chat.ChatMessageContextInfo}
 * / {@link com.github.auties00.cobalt.model.message.MessageKey} carried by it,
 * so the same container instance should never be shared across recipient
 * fanouts that need different transforms.
 */
@WhatsAppWebModule(moduleName = "WAWebE2EProtoGenerator")
public final class BotProtobufTransform {
    /**
     * The store consulted for LID-to-PN lookups when retargeting FBID-bot
     * participants.
     */
    private final WhatsAppStore store;

    /**
     * Constructs a transform bound to the given store.
     *
     * @apiNote
     * The bound store is the same {@link WhatsAppStore} the rest of the send
     * pipeline reads; {@link WhatsAppStore#findLidByPhone} is used to upgrade
     * legacy PN participants to LID before the FBID-bot transforms emit the
     * key.
     *
     * @param store the store providing JID resolution
     * @throws NullPointerException if {@code store} is {@code null}
     */
    public BotProtobufTransform(WhatsAppStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Applies the CAPI bot-invoke transform to the supplied container.
     *
     * @apiNote
     * Mirrors WA Web's {@code updateBotInvokeMsgProtoCopyForCapi}: the
     * user-level {@code messageSecret} is replaced with the
     * {@code botMessageSecret} derived via {@link BotMessageSecret#derive}, the
     * quoted-message body is stripped from the {@link ContextInfo} when the
     * quoted author is not itself a bot, and the {@code remoteJid} on every
     * carried protocol-message key is cleared. Callers in
     * {@code WAWebSendGroupSkmsgJob}, {@code WAWebSendMsgCreateDeviceStanza},
     * and {@code WAWebSendMsgCreateFanoutStanza} all run this transform on a
     * per-device copy of the proto.
     * @implNote
     * This implementation passes {@code null} for {@code botMessageSecret}
     * through unchanged; the caller is expected to call this method with the
     * already-derived bot secret rather than re-derive it per fanout.
     *
     * @param container        the {@link MessageContainer} to mutate
     * @param botMessageSecret the derived bot message secret to install, or
     *                         {@code null} to merely clear the user secret
     */
    @WhatsAppWebExport(moduleName = "WAWebE2EProtoGenerator", exports = "updateBotInvokeMsgProtoCopyForCapi",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void transformForCapi(MessageContainer container, byte[] botMessageSecret) {
        container.messageContextInfo().ifPresent(info -> {
            info.setMessageSecret(null);
            info.setBotMessageSecret(botMessageSecret);
        });
        stripQuotedMessageForNonBot(container);
        stripProtocolMessageRemoteJid(container);
    }

    /**
     * Applies the FBID-bot transform that upgrades quoted-message participant
     * JIDs from PN to LID.
     *
     * @apiNote
     * Required for messages destined to an FBID (Facebook-account) bot: the
     * server only accepts LID-form participant JIDs on the
     * {@link ContextInfo#quotedMessageSenderJid()} field, so any leftover PN
     * needs to be rewritten via {@link WhatsAppStore#findLidByPhone}. Matches
     * WA Web's {@code updateFbidBotProtobuf}, called from
     * {@code WAWebSendMsgCreateFanoutStanza} when the recipient
     * {@code isFbidBot()}.
     *
     * @param container the {@link MessageContainer} to mutate
     */
    @WhatsAppWebExport(moduleName = "WAWebE2EProtoGenerator", exports = "updateFbidBotProtobuf",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
     * Applies the FBID-bot-invoke transform that retargets protocol-message
     * key senders from PN to LID.
     *
     * @apiNote
     * Counterpart of {@link #transformForFbidBot} for the
     * {@link ProtocolMessage#key()} payload: rewrites
     * {@link com.github.auties00.cobalt.model.message.MessageKey#senderJid()}
     * to its LID form so the FBID bot can resolve the originating user. Mirrors
     * WA Web's {@code updateFbidBotInvokeProtobuf}.
     *
     * @param container the {@link MessageContainer} to mutate
     */
    @WhatsAppWebExport(moduleName = "WAWebE2EProtoGenerator", exports = "updateFbidBotInvokeProtobuf",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
     * Applies the generic bot transform that strips identifying fields from
     * protocol-message keys.
     *
     * @apiNote
     * Mirrors WA Web's {@code updateBotProtobuf}, the catch-all bot transform
     * run after {@link #transformForFbidBot} when the recipient
     * {@code isBot()}. It clears the {@code remoteJid} (parent) and the
     * {@code participant} sender on the
     * {@link com.github.auties00.cobalt.model.message.MessageKey} so the bot
     * does not observe the user-side addressing context.
     *
     * @param container the {@link MessageContainer} to mutate
     */
    @WhatsAppWebExport(moduleName = "WAWebE2EProtoGenerator", exports = "updateBotProtobuf",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
     * Returns the inner {@link ContextInfo} carried by the container.
     *
     * @apiNote
     * Unwraps a {@link FutureProofMessage} botInvoke wrapper transparently so
     * callers can treat both wire shapes the same. Used by
     * {@link #transformForFbidBot} and {@link #stripQuotedMessageForNonBot}.
     *
     * @param container the {@link MessageContainer} to inspect
     * @return the inner {@link ContextInfo}, or {@code null} when none is
     *         present
     */
    private static ContextInfo resolveInnerContextInfo(MessageContainer container) {
        return container.content() instanceof ContextualMessage contextualMessage
                ? contextualMessage.contextInfo().orElse(null)
                : null;
    }

    /**
     * Clears the quoted-message body when the quoted author is not itself a
     * bot.
     *
     * @apiNote
     * Part of the CAPI transform applied by {@link #transformForCapi}: keeps
     * bot-to-bot quotes intact (so a bot can reference another bot's reply)
     * while stripping arbitrary user-to-user quote chains so the bot sees only
     * the immediate prompt.
     *
     * @param container the {@link MessageContainer} to mutate
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
     * Strips the {@code remoteJid} (parent) from every protocol-message key
     * carried by the container.
     *
     * @apiNote
     * Used by {@link #transformForCapi} so the bot does not observe the
     * user-side parent thread. Both the protocol message's primary key and the
     * optional {@link BotFeedbackMessage#messageKey()} are cleared.
     *
     * @param container the {@link MessageContainer} to mutate
     */
    private static void stripProtocolMessageRemoteJid(MessageContainer container) {
        if (!(container.content() instanceof ProtocolMessage pm)) {
            return;
        }

        pm.key().ifPresent(key -> key.setParentJid(null));
        pm.botFeedbackMessage()
                .flatMap(BotFeedbackMessage::messageKey)
                .ifPresent(key -> key.setParentJid(null));
    }
}
