package com.github.auties00.cobalt.message.receive;

import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageStatus;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.store.WhatsAppStore;

/**
 * Processes incoming plaintext newsletter messages.
 *
 * <p>Newsletter messages are not E2E encrypted.  The message content
 * is in a {@code <plaintext>} child node as raw protobuf bytes.
 * The stanza also carries {@code server_id}, timestamp, and optional
 * edit/revoke attributes.
 *
 * @apiNote WASmaxInMessageDeliverNewsletterRequest: parses the
 * newsletter message stanza extracting from, id, server_id, t,
 * is_sender, plaintext payload, media, and content type metadata.
 * WASmaxInMessageDeliverNewsletterMessageFanoutContent: dispatches
 * to the correct content type parser (text, media, reaction, revoke,
 * poll, question, etc.).
 */
final class NewsletterMessageReceiver extends MessageReceiver<NewsletterMessageInfo> {
    private static final System.Logger LOGGER = System.getLogger(NewsletterMessageReceiver.class.getName());

    NewsletterMessageReceiver(WhatsAppStore store) {
        super(store);
    }

    /**
     * Processes an incoming plaintext newsletter message node.
     *
     * @param node    the raw {@code <message>} node
     * @param fromJid the newsletter JID (from the {@code from} attribute)
     * @return the processed newsletter message info, or {@code null} if
     *         the plaintext content is missing or invalid
     *
     * @apiNote WASmaxInMessageDeliverNewsletterRequest: extracts the
     * plaintext payload and metadata from the stanza.
     */
    @Override
    NewsletterMessageInfo receive(Node node, Jid fromJid) {
        var id = node.getRequiredAttributeAsString("id");
        var timestampSeconds = node.getRequiredAttributeAsLong("t");
        var serverId = (int) node.getAttributeAsLong("server_id").orElse(0L);

        var plaintext = node.getChild("plaintext")
                .or(() -> node.getChild("body"))
                .flatMap(Node::toContentBytes)
                .orElse(null);
        if (plaintext == null || plaintext.length == 0) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Newsletter message {0} has no plaintext content", id);
            return null;
        }

        var container = decodeProtobuf(id, plaintext);
        if (container == null) {
            return null;
        }

        var newsletter = store.findNewsletterByJid(fromJid).orElse(null);
        var info = new NewsletterMessageInfoBuilder()
                .id(id)
                .serverId(serverId)
                .timestampSeconds(timestampSeconds)
                .message(container)
                .status(MessageStatus.DELIVERED)
                .build();
        if (newsletter != null) {
            info.setNewsletter(newsletter);
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "Processed newsletter message {0} from {1}", id, fromJid);
        return info;
    }
}
