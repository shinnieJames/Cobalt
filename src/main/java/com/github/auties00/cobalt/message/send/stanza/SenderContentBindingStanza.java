package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.send.token.ContentBindingToken;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@code <sender_content_binding>} stanza child node
 * containing the sender's own RCAT content-binding tag.
 *
 * @implNote WAWebSendMsgCreateFanoutStanza: includes
 * {@code <sender_content_binding>} with the sender's own RCAT tag
 * looked up from the content binding map.
 * WAWebSendGroupSkmsgJob: same logic for group SKMSG stanzas.
 * WAWebMsgRcatUtils.genContentBindingForMsg: generates per-recipient
 * HMAC tags; the sender's own tag is placed in
 * {@code <sender_content_binding>}.
 */
public final class SenderContentBindingStanza {
    /**
     * Logger for warning on RCAT generation failures.
     *
     * @implNote NO_WA_BASIS: Java logging infrastructure.
     */
    private static final System.Logger LOGGER = System.getLogger(SenderContentBindingStanza.class.getName());

    /**
     * Prevents instantiation of this utility class.
     *
     * @implNote NO_WA_BASIS: Java utility class pattern.
     */
    private SenderContentBindingStanza() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Builds the {@code <sender_content_binding>} node for a 1:1 chat
     * message, generating the RCAT content binding for the sender.
     *
     * <p>Returns {@code null} if the message has no messageSecret, is
     * not a URL message, or RCAT generation fails.
     *
     * @param messageInfo the outgoing message
     * @param selfJid     the sender's user JID
     * @return the sender content binding node, or {@code null}
     *
     * @implNote WAWebMsgRcatUtils.genContentBindingForMsg: checks
     * messageSecret, URL message, generates per-recipient HMAC tags.
     * ADAPTED: the guard checks from {@code genContentBindingForMsg}
     * (type=CHAT, isUrlMessage, isSentByMe) are applied here at the
     * call site instead of inside the token generator.
     */
    public static Node buildForUser(ChatMessageInfo messageInfo, Jid selfJid) {
        var messageSecret = messageInfo.messageSecret().orElse(null);
        if (messageSecret == null) {
            return null;
        }

        var message = messageInfo.message().content();
        if (!(message instanceof ExtendedTextMessage text) || text.matchedText().isEmpty()) {
            return null;
        }

        var userJid = selfJid.toUserJid();
        var contentId = ContentBindingToken.resolveContentId(text.matchedText().get());

        try {
            var bindings = ContentBindingToken.generate(
                    messageInfo.key().id().orElseThrow(), messageSecret,
                    userJid, List.of(userJid), contentId);
            return build(userJid, bindings);
        } catch (GeneralSecurityException e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to generate sender content binding: {0}", e.getMessage());
            return null;
        }
    }

    /**
     * Builds the {@code <sender_content_binding>} node from a
     * pre-computed content binding map (used for group messages where
     * the bindings are computed externally for all participants).
     *
     * @param senderJid       the sender's device JID
     * @param contentBindings per-recipient RCAT tags keyed by user JID,
     *                        or {@code null} if RCAT is not applicable
     * @return the sender content binding node, or {@code null} if no
     *         binding exists for the sender
     *
     * @implNote WAWebSendGroupSkmsgJob: looks up the sender's content
     * binding from the RCAT map using
     * {@code widToUserJid(asUserWidOrThrow(senderJid))}.
     */
    public static Node build(Jid senderJid, Map<Jid, byte[]> contentBindings) {
        if (contentBindings == null) {
            return null;
        }

        var binding = contentBindings.get(senderJid.toUserJid());
        if (binding == null) {
            return null;
        }

        return new NodeBuilder()
                .description("sender_content_binding")
                .content(binding)
                .build();
    }
}
