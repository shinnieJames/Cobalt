package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.send.token.ContentBindingToken;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

/**
 * Builds the optional {@code <sender_content_binding>} child of an outgoing {@code <message>} stanza carrying the
 * sender's own RCAT content-binding tag.
 * <p>
 * Composed by {@link ChatFanoutStanza} and {@link GroupSkmsgFanoutStanza}. The sender content binding pins the sender's
 * URL message content to a cryptographic tag so the recipient can verify the linked URL was the one the sender intended,
 * defending against URL-rewriting attacks. The 1:1 entrypoint ({@link #buildForUser(ChatMessageInfo, Jid)}) computes the
 * binding for the sender alone; the group entrypoint ({@link #build(Jid, Map)}) selects the sender's pre-computed
 * binding out of a per-recipient map.
 */
@WhatsAppWebModule(moduleName = "WAWebSendMsgCreateFanoutStanza")
@WhatsAppWebModule(moduleName = "WAWebSendGroupSkmsgJob")
@WhatsAppWebModule(moduleName = "WAWebMsgRcatUtils")
public final class SenderContentBindingStanza {
    /**
     * Logs RCAT generation failures.
     */
    private static final System.Logger LOGGER = System.getLogger(SenderContentBindingStanza.class.getName());

    /**
     * Prevents instantiation; this is a static composer.
     */
    private SenderContentBindingStanza() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Builds the {@code <sender_content_binding>} child for a 1:1 URL message by deriving the sender's RCAT tag on the
     * fly.
     * <p>
     * Returns {@code null} when any prerequisite fails: the message carries no {@link ChatMessageInfo#messageSecret()},
     * the body is not an {@link ExtendedTextMessage} with a non-empty matched URL, or RCAT derivation fails. The returned
     * node carries the raw RCAT bytes as its content; the surrounding stanza is rejected as malformed if the RCAT does
     * not match the URL after URL-rewriting.
     *
     * @implNote This implementation hashes on {@link ContentBindingToken#resolveContentId(String)} so URLs that differ
     * only in tracking parameters share the same tag.
     *
     * @param messageInfo the outgoing {@link ChatMessageInfo}
     * @param selfJid     the sender's {@link Jid}
     * @return the {@code <sender_content_binding>} {@link Node}, or {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebMsgRcatUtils", exports = "genContentBindingForMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
     * Builds the {@code <sender_content_binding>} child by selecting the sender's RCAT tag from a pre-computed
     * per-recipient map.
     * <p>
     * Used for group sends, where the bindings have already been derived for every participant. Returns {@code null}
     * when the map is {@code null} or contains no entry for the sender's user JID.
     *
     * @implNote This implementation looks up on {@link Jid#toUserJid()} so the device identifier in {@code senderJid} is
     * irrelevant.
     *
     * @param senderJid       the sender's device {@link Jid}
     * @param contentBindings per-recipient RCAT tags keyed by user {@link Jid}, or {@code null}
     * @return the {@code <sender_content_binding>} {@link Node}, or {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendGroupSkmsgJob", exports = "encryptAndSendSenderKeyMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
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
