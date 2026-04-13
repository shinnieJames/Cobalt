package com.github.auties00.cobalt.message.receive;

import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageStatus;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.time.Instant;

/**
 * Processes incoming plaintext newsletter messages.
 *
 * <p>Newsletter messages are not E2E encrypted.  The message content
 * is in a {@code <plaintext>} child node as raw protobuf bytes.
 * The stanza also carries {@code server_id}, timestamp, and optional
 * edit/revoke attributes.
 *
 * @implNote WAWebHandleNewsletterMsg.default: the main entry point for
 * incoming newsletter message handling.  Calls WAWebNewsletterMsgParser
 * to extract the parsed request (via WASmaxMessageDeliverNewsletterRPC),
 * then dispatches through WAWebNewsletterMsgProcessor.preprocessNewsletterMsg
 * and WAWebNewsletterMsgUtils.mapMsgStanzaToMsgData which decodes the
 * protobuf from the {@code <plaintext>} child node's payload.
 */
final class NewsletterMessageReceiver extends MessageReceiver<NewsletterMessageInfo> {
    /**
     * Logger for diagnostic messages during newsletter message processing.
     *
     * @implNote ADAPTED: WAWebHandleNewsletterMsg uses WALogger with tagged
     * template literals; Cobalt uses {@code System.Logger} instead.
     */
    private static final System.Logger LOGGER = System.getLogger(NewsletterMessageReceiver.class.getName());

    /**
     * Constructs a new newsletter message receiver with the required store
     * dependency.
     *
     * @param store the central session data store
     *
     * @implNote ADAPTED: WAWebHandleNewsletterMsg uses module-level imports
     * for store and WAWebNewsletterMsgParser access; Cobalt uses
     * constructor-based DI.
     */
    NewsletterMessageReceiver(WhatsAppStore store) {
        super(store);
    }

    /**
     * Processes an incoming plaintext newsletter message node.
     *
     * <p>Extracts the {@code id}, {@code t} (timestamp), {@code server_id},
     * and {@code is_sender} attributes from the stanza, reads the protobuf
     * payload from the {@code <plaintext>} child node, and builds a
     * {@link NewsletterMessageInfo}.
     *
     * @param node    the raw {@code <message>} node
     * @param fromJid the newsletter JID (from the {@code from} attribute)
     * @return the processed newsletter message info, or {@code null} if
     *         the plaintext content is missing or invalid
     *
     * @implNote WAWebHandleNewsletterMsg.default: calls
     * WAWebNewsletterMsgParser.default which delegates to
     * WASmaxMessageDeliverNewsletterRPC.receiveNewsletterRPC to parse the
     * stanza.  The SMAX parser extracts {@code id}, {@code server_id},
     * {@code t}, {@code is_sender}, and the {@code <plaintext>} child
     * payload from WASmaxInMessageDeliverNewsletterMessageFanoutMixin.
     * The parsed message is then processed through
     * WAWebNewsletterMsgProcessor.preprocessNewsletterMsg and
     * WAWebNewsletterMsgUtils.mapMsgStanzaToMsgData which calls
     * {@code decodeProtobuf(MessageSpec, payload)} on the plaintext bytes.
     */
    @Override
    NewsletterMessageInfo receive(Node node, Jid fromJid) {
        // WASmaxInMessageDeliverNewsletterMessageFanoutMixin: attrStanzaId(e, "id")
        var id = node.getRequiredAttributeAsString("id");
        // WASmaxInMessageDeliverNewsletterMessageFanoutMixin: attrIntRange(e, "t", 0, undefined)
        var timestampSeconds = node.getRequiredAttributeAsLong("t");
        var timestamp = Instant.ofEpochSecond(timestampSeconds);
        // WASmaxInMessageDeliverNewsletterMessageFanoutMixin: attrIntRange(e, "server_id", 99, 2147476647)
        var serverId = node.getRequiredAttributeAsInt("server_id");
        // WASmaxInMessageDeliverNewsletterMessageFanoutMixin: optionalLiteral(attrString, e, "is_sender", "true")
        // WAWebNewsletterMsgParser: isSender: i.isSender === "true"
        var isSender = "true".equals(node.getAttributeAsString("is_sender", null));

        // WASmaxInMessageDeliverNewsletterTextMixin: flattenedChildWithTag(e, "plaintext")
        // WASmaxInMessageDeliverPayloadMixin: parsePayloadMixin extracts binary content
        var plaintext = node.getChild("plaintext")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        if (plaintext == null || plaintext.length == 0) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Newsletter message {0} has no plaintext content", id);
            return null;
        }

        // WAWebNewsletterMsgUtils.mapMsgStanzaToMsgData: decodeProtobuf(MessageSpec, payload)
        var container = decodeProtobuf(id, plaintext);
        if (container == null) {
            return null;
        }

        // WAWebNewsletterMsgUtils.mapMsgStanzaToMsgData: constructs MsgKey with remote, fromMe, id
        var key = new MessageKeyBuilder()
                .id(id)
                .parentJid(fromJid)
                .fromMe(isSender) // WAWebNewsletterMsgParser: isSender: i.isSender === "true"
                .build();
        // WAWebNewsletterMsgUtils.mapMsgStanzaToMsgData: constructs base msg data
        var info = new NewsletterMessageInfoBuilder()
                .key(key)
                .serverId(serverId)
                .timestamp(timestamp)
                .message(container)
                .status(MessageStatus.DELIVERED) // WAWebNewsletterMsgUtils: ack: ACK.SENT
                .build();

        LOGGER.log(System.Logger.Level.DEBUG,
                "Processed newsletter message {0} from {1}", id, fromJid);
        return info;
    }
}
