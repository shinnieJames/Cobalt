package com.github.auties00.cobalt.stream.newsletter;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.message.MessageStatus;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfoBuilder;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;

/**
 * Handles incoming {@code <status>} stanzas from newsletter channels.
 *
 * <p>Newsletter status stanzas carry plaintext protobuf content for text
 * and media statuses, revoke instructions for admin revokes, and reaction
 * events.  Reaction and reaction-revoke stanzas are silently acknowledged
 * (no message is stored).  Text and media stanzas are decoded, stored in
 * the newsletter's message collection, and broadcast to listeners.
 *
 * <p>The stanza structure parsed by the SMAX layer includes:
 * <ul>
 *   <li>{@code from}: newsletter JID (required)</li>
 *   <li>{@code id}: stanza identifier (required)</li>
 *   <li>{@code server_id}: server-assigned message identifier (required, range 99..2147476647)</li>
 *   <li>{@code t}: epoch-second timestamp (required, range 1577865600..4102473600)</li>
 *   <li>{@code is_sender}: optional literal {@code "true"}</li>
 *   <li>{@code type}: {@code "text"} or {@code "media"}</li>
 *   <li>{@code edit}: {@code "8"} for admin revoke</li>
 *   <li>{@code offline}: optional offline indicator (0..12)</li>
 *   <li>{@code <plaintext>}: child node with protobuf payload bytes</li>
 *   <li>{@code <reaction>}: child node for reaction/reaction-revoke</li>
 * </ul>
 *
 * @implNote WAWebHandleNewsletterStatus.default
 */
public final class NewsletterStatusStreamHandler implements SocketStream.Handler {
    /**
     * Logger for diagnostic messages during newsletter status processing.
     *
     * @implNote ADAPTED: WAWebHandleNewsletterStatus uses WALogger with tagged
     * template literals; Cobalt uses {@code System.Logger} instead.
     */
    private static final System.Logger LOGGER = System.getLogger(NewsletterStatusStreamHandler.class.getName());

    /**
     * The WhatsApp client instance providing access to the store and
     * listener notification.
     *
     * @implNote ADAPTED: WAWebHandleNewsletterStatus accesses stores and
     * services via module-level imports; Cobalt uses constructor-based DI.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new newsletter status stream handler with the required
     * client dependency.
     *
     * @param whatsapp the WhatsApp client instance, must not be {@code null}
     * @implNote ADAPTED: WAWebHandleNewsletterStatus.default uses module-level
     * imports for all dependencies; Cobalt uses constructor-based DI.
     */
    public NewsletterStatusStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * Handles an incoming {@code <status>} stanza from a newsletter channel.
     *
     * <p>The handler first validates that the stanza originates from a
     * newsletter JID.  It then determines the content type by inspecting
     * the stanza's child nodes and attributes:
     * <ul>
     *   <li>Stanzas with a {@code <reaction>} child are silently skipped
     *       (reaction and reaction-revoke).</li>
     *   <li>Text ({@code type="text"}) and media ({@code type="media"})
     *       stanzas are decoded from protobuf and stored.</li>
     *   <li>Revoke stanzas ({@code edit="8"}) are currently logged and
     *       skipped, as Cobalt's revoke model is handled at a higher
     *       layer.</li>
     * </ul>
     *
     * @param node the raw {@code <status>} stanza node
     * @implNote WAWebHandleNewsletterStatus.default: parses the stanza via
     * WASmaxStatusDeliverIncomingNewsletterStatusRPC.receiveIncomingNewsletterStatusRPC,
     * which delegates to WASmaxInStatusDeliverIncomingNewsletterStatusRequest
     * and WASmaxInStatusDeliverFromNewsletterMixin for attribute extraction.
     * The content type is determined by
     * WASmaxInStatusDeliverNewsletterStatusContentTypeMixins which checks
     * for StatusNewsletterRevoke, StatusNewsletterText, StatusNewsletterMedia,
     * StatusNewsletterReaction, and StatusNewsletterReactionRevoke in that
     * order.  Reactions and reaction-revokes are returned immediately with
     * a success response and no message processing.
     */
    @Override
    public void handle(Node node) {
        // WASmaxInStatusDeliverFromNewsletterMixin: attrNewsletterJid(e, "from")
        var from = node.getAttributeAsJid("from", null);
        if (from == null || !from.hasNewsletterServer()) {
            return;
        }

        // WASmaxInStatusDeliverIncomingNewsletterStatusRequest: attrStanzaId(e, "id")
        var id = node.getAttributeAsString("id", null);
        if (id == null) {
            return;
        }

        // WASmaxInStatusDeliverNewsletterStatusContentTypeMixins:
        // Reactions have a <reaction> child -> skip immediately
        // WAWebHandleNewsletterStatus.default: case "StatusNewsletterReaction":
        // case "StatusNewsletterReactionRevoke": return d;
        if (node.hasChild("reaction")) {
            return;
        }

        // WASmaxInStatusDeliverStatusAdminRevokeMixin: literal(attrString, e, "edit", "8")
        // WAWebHandleNewsletterStatus.default: case "StatusNewsletterRevoke" ->
        // mapStatusRevokeToMsgData which creates a PROTOCOL/ProtocolRevoke message
        var edit = node.getAttributeAsString("edit", null);
        if ("8".equals(edit)) {
            // Revoke handling: WA Web creates a protocol revoke message with
            // type=PROTOCOL, kind=ProtocolRevoke, subtype="admin_revoke".
            // In Cobalt, revoke processing for newsletters is handled at a
            // higher layer; log and skip here.
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Skipping newsletter status revoke for {0} from {1}", id, from);
            return;
        }

        // WASmaxInStatusDeliverStatusNewsletterTextMixin / StatusNewsletterMediaMixin:
        // flattenedChildWithTag(e, "plaintext") -> parseNewsletterPlaintextPayloadMixin
        // -> contentBytesRange(e, 1, 1048576)
        var plaintext = node.getChild("plaintext")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        if (plaintext == null || plaintext.length == 0) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring newsletter status with no plaintext content for {0}", id);
            return;
        }

        // WAWebNewsletterStatusUtils.mapStatusStanzaToMsgData:
        // decodeProtobuf(MessageSpec, n) decodes the plaintext payload
        var message = decodeMessage(id, plaintext);
        if (message == null) {
            return;
        }

        // WASmaxInStatusDeliverFromNewsletterMixin: attrIntRange(e, "server_id", 99, 2147476647)
        var serverId = node.getAttributeAsInt("server_id", 0);
        // WASmaxInStatusDeliverFromNewsletterMixin: attrIntRange(e, "t", 1577865600, 4102473600)
        var timestamp = resolveTimestamp(node);
        // WASmaxInStatusDeliverFromNewsletterMixin: optionalLiteral(attrString, e, "is_sender", "true")
        var isSender = "true".equals(node.getAttributeAsString("is_sender", null));

        // WAWebNewsletterStatusUtils.mapStatusStanzaToMsgData -> d(e, t):
        // new MsgKey({remote: t, fromMe: n, id: e.id})
        // Note: no senderJid/participant is set for newsletter status keys
        var key = new MessageKeyBuilder()
                .id(id)
                .parentJid(from)
                .fromMe(isSender)
                .build();

        // WAWebNewsletterStatusUtils.mapStatusStanzaToMsgData:
        // constructs message data with ack: ACK.SENT, serverId, isNewMsg
        var info = new NewsletterMessageInfoBuilder()
                .key(key)
                .serverId(serverId)
                .timestamp(timestamp)
                .message(message)
                .status(MessageStatus.DELIVERED)
                .build();

        // ADAPTED: WAWebHandleNewsletterStatus.default calls
        // handleSingleMsg({chatId: m, newMsg: t, handleSingleMsgOrigin: "addStatusMessages"})
        // which stores the message through the standard pipeline.
        // Cobalt directly stores to the newsletter and notifies listeners.
        storeMessage(from, info);
        notifyNewMessage(info);
    }

    /**
     * Decodes raw protobuf bytes into a {@link MessageContainer}.
     *
     * <p>Returns {@code null} and logs a warning if decoding fails, rather
     * than throwing, matching Cobalt's error model for non-fatal decode
     * failures.
     *
     * @param id        the message identifier for log context
     * @param plaintext the raw protobuf bytes from the {@code <plaintext>}
     *                  child node
     * @return the decoded message container, or {@code null} on failure
     * @implNote WAWebNewsletterStatusUtils.mapStatusStanzaToMsgData:
     * calls {@code decodeProtobuf(MessageSpec, n)} on the plaintext payload
     * bytes extracted from the {@code <plaintext>} child node.
     */
    private MessageContainer decodeMessage(String id, byte[] plaintext) {
        try {
            return MessageContainerSpec.decode(plaintext);
        } catch (Exception exception) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Failed to decode newsletter status {0}: {1}", id, exception.getMessage());
            return null;
        }
    }

    /**
     * Resolves the epoch-second timestamp from the {@code t} attribute of
     * the given node.
     *
     * @param node the stanza node containing the {@code t} attribute
     * @return the resolved timestamp, or {@code null} if the attribute is
     *         absent
     * @implNote WASmaxInStatusDeliverFromNewsletterMixin:
     * {@code attrIntRange(e, "t", 1577865600, 4102473600)} extracts the
     * timestamp as a required integer.  Cobalt treats it as optional for
     * defensive safety.
     */
    private Instant resolveTimestamp(Node node) {
        var timestamp = node.getAttributeAsLong("t", (Long) null);
        return timestamp == null ? null : Instant.ofEpochSecond(timestamp);
    }

    /**
     * Stores a newsletter status message in the newsletter's message
     * collection, creating the newsletter entity if it does not yet exist.
     *
     * <p>Also updates the newsletter timestamp and increments the unread
     * message count for messages not sent by the current user.
     *
     * @param newsletterJid the newsletter JID
     * @param info          the message to store
     * @implNote ADAPTED: WAWebHandleNewsletterStatus.default delegates to
     * WAWebHandleSingleMsgFactory.handleSingleMsg which stores the message
     * through the standard pipeline.  Cobalt directly stores to the
     * newsletter entity in the {@code WhatsAppStore}.
     */
    private void storeMessage(Jid newsletterJid, NewsletterMessageInfo info) {
        var newsletter = whatsapp.store()
                .findNewsletterByJid(newsletterJid)
                .orElseGet(() -> whatsapp.store().addNewNewsletter(newsletterJid));
        newsletter.setTimestamp(info.timestamp().orElse(null));
        if (!info.key().fromMe()) {
            newsletter.setUnreadMessagesCount(newsletter.unreadMessagesCount() + 1);
        }
        newsletter.addMessage(info);
    }

    /**
     * Notifies all registered listeners that a new newsletter status
     * message has been received.
     *
     * <p>Each listener is notified on a dedicated virtual thread to avoid
     * blocking the handler.
     *
     * @param info the received message info
     * @implNote ADAPTED: WAWebHandleNewsletterStatus.default fires
     * notifications through the standard message pipeline after
     * handleSingleMsg completes.  Cobalt directly notifies listeners
     * on virtual threads.
     */
    private void notifyNewMessage(com.github.auties00.cobalt.model.message.MessageInfo info) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onNewMessage(whatsapp, info));
        }
    }
}
