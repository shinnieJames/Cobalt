package com.github.auties00.cobalt.stream.notification.account;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;
import java.util.Arrays;

/**
 * Handles incoming privacy token notifications from the WhatsApp server.
 *
 * <p>When a privacy token notification is received, this handler parses the
 * token data, updates the relevant chat's trusted-contact token in the store,
 * re-subscribes to presence for the sender, and sends an acknowledgement
 * stanza back to the server.
 *
 * @implNote WAWebHandlePrivacyTokensNotification.default
 */
final class NotificationPrivacyStreamHandler implements SocketStream.Handler {

    /**
     * Logger for this handler.
     *
     * @implNote WAWebHandlePrivacyTokensNotification (WALogger usage)
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationPrivacyStreamHandler.class.getName());

    /**
     * The WhatsApp client instance used for store access and node sending.
     *
     * @implNote WAWebHandlePrivacyTokensNotification (module-level dependency injection)
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new privacy stream handler.
     *
     * @param whatsapp the WhatsApp client instance
     * @implNote WAWebHandlePrivacyTokensNotification (constructor DI)
     */
    NotificationPrivacyStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * Handles a privacy token notification node.
     *
     * <p>Parses the notification, processes each trusted-contact token by
     * updating the chat store and re-subscribing to presence, then sends
     * an acknowledgement stanza. The ACK is always sent, even if processing
     * fails.
     *
     * @param node the incoming notification node
     * @implNote WAWebHandlePrivacyTokensNotification.default (function f/g)
     */
    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "privacy_token")) {
            return;
        }

        try {
            handleNotification(node);
        } catch (Throwable throwable) {
            // WAWebHandlePrivacyTokensNotification.default: logs parse error and throws
            LOGGER.log(System.Logger.Level.WARNING,
                    "Cannot handle privacy_token notification {0}: {1}",
                    node.getAttributeAsString("id", "<missing>"),
                    throwable.getMessage());
        } finally {
            sendNotificationAck(node); // WAWebHandlePrivacyTokensNotification.default: ACK is always returned
        }
    }

    /**
     * Parses the notification and processes each privacy token.
     *
     * <p>Extracts the sender's phone number JID and optional LID from the
     * notification attributes, then iterates over the token children,
     * dispatching each by type.
     *
     * @param node the notification node
     * @implNote WAWebHandlePrivacyTokensNotification.default (parser m + function g body)
     */
    private void handleNotification(Node node) {
        // WAWebHandlePrivacyTokensNotification: m.parse - attrUserJid("from")
        var senderPn = getUserJid(node, "from");
        // WAWebHandlePrivacyTokensNotification: m.parse - maybeAttrLidUserJid("sender_lid")
        var senderLid = node.getAttributeAsJid("sender_lid")
                .map(Jid::toUserJid)
                .orElse(null);
        if (senderPn == null) {
            return;
        }

        // WAWebHandlePrivacyTokensNotification: m.parse - t.child("tokens")
        var tokensNode = node.getChild("tokens").orElse(null);
        if (tokensNode == null) {
            return;
        }

        // WAWebHandlePrivacyTokensNotification: m.parse - i.forEachChildWithTag("token", ...)
        for (var tokenNode : tokensNode.getChildren("token")) {
            // WAWebHandlePrivacyTokensNotification: m.parse - t.attrString("type")
            var type = tokenNode.getAttributeAsString("type", "");
            switch (type) {
                case "trusted_contact" -> handleTrustedContactToken(senderPn, senderLid, tokenNode);
                default -> LOGGER.log(System.Logger.Level.DEBUG,
                        "Ignoring unsupported privacy token type {0}", type);
            }
        }
    }

    /**
     * Handles a single trusted-contact token by updating the chat store
     * and re-subscribing to the sender's presence.
     *
     * <p>The token content and timestamp are extracted from the token node.
     * The chat is located by JID (phone number first, then LID if available),
     * and its trusted-contact token fields are updated. Finally, presence is
     * re-subscribed for the sender's phone number JID.
     *
     * @param senderPn  the sender's phone number JID
     * @param senderLid the sender's LID JID, or {@code null} if absent
     * @param tokenNode the token node containing content and timestamp
     * @implNote WAWebHandlePrivacyTokensNotification._ (function p/_ body) +
     *           WAWebSetTcTokenChatAction.handleIncomingTcToken
     */
    private void handleTrustedContactToken(Jid senderPn, Jid senderLid, Node tokenNode) {
        // WAWebHandlePrivacyTokensNotification: d(t) - t.contentBytes()
        var content = tokenNode.toContentBytes().orElse(null);
        if (content == null || content.length == 0) {
            return; // WAWebSetTcTokenChatAction.handleIncomingTcToken: if (a != null ...)
        }

        // WAWebHandlePrivacyTokensNotification: d(t) - t.attrTime("t")
        var tokenTimestamp = getInstantAttribute(tokenNode, "t");

        // WAWebSetTcTokenChatAction.handleIncomingTcToken: update chat tc token
        updateChatTcToken(senderPn, senderLid, tokenTimestamp, content);

        // WAWebHandlePrivacyTokensNotification._: reSubscribeWhenActive(r) where r = userWid (PN)
        try {
            whatsapp.subscribeToPresence(senderPn);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Cannot resubscribe to presence for tc token sender {0}: {1}",
                    senderPn,
                    throwable.getMessage());
        }
    }

    /**
     * Updates the trusted-contact token on the chat corresponding to the
     * sender.
     *
     * <p>Looks up the chat first by the sender's phone number JID. If not
     * found, creates a new chat entry. Skips the update if the existing
     * token is identical and the existing timestamp is newer.
     *
     * @param senderPn       the sender's phone number JID
     * @param senderLid      the sender's LID JID, or {@code null}
     * @param tokenTimestamp  the timestamp from the token, or {@code null}
     * @param tcTokenContent the raw trusted-contact token bytes
     * @implNote WAWebSetTcTokenChatAction.handleIncomingTcToken
     */
    private void updateChatTcToken(Jid senderPn, Jid senderLid, Instant tokenTimestamp, byte[] tcTokenContent) {
        // WAWebSetTcTokenChatAction.handleIncomingTcToken: find chat by LID first, then by PN
        var chat = (senderLid != null
                ? whatsapp.store().findChatByJid(senderLid).orElse(null)
                : null);
        if (chat == null) {
            chat = whatsapp.store().findChatByJid(senderPn)
                    .orElseGet(() -> whatsapp.store().addNewChat(senderPn));
        }

        // WAWebSetTcTokenChatAction.handleIncomingTcToken:
        // skip if token already equals AND existing timestamp > new timestamp
        var existingToken = chat.tcToken().orElse(null);
        var existingTimestamp = chat.tcTokenTimestamp().orElse(null);
        if (existingToken != null && Arrays.equals(existingToken, tcTokenContent)
                || existingTimestamp != null && tokenTimestamp != null && existingTimestamp.isAfter(tokenTimestamp)) {
            return;
        }

        // WAWebSetTcTokenChatAction.handleIncomingTcToken: l.set({tcToken: a, tcTokenTimestamp: r})
        chat.setTcToken(tcTokenContent);
        chat.setTcTokenTimestamp(tokenTimestamp);
    }

    /**
     * Extracts a user JID from a node attribute.
     *
     * @param node the node to extract from
     * @param key  the attribute key
     * @return the user JID, or {@code null} if not present
     * @implNote WAWebHandlePrivacyTokensNotification (parser m: attrUserJid)
     */
    private Jid getUserJid(Node node, String key) {
        return node.getAttributeAsJid(key)
                .map(Jid::toUserJid)
                .orElse(null);
    }

    /**
     * Extracts an {@link Instant} from a node attribute representing epoch
     * seconds.
     *
     * @param node the node to extract from
     * @param key  the attribute key
     * @return the parsed instant, or {@code null} if absent or non-positive
     * @implNote WAWebHandlePrivacyTokensNotification.d (attrTime("t"))
     */
    private Instant getInstantAttribute(Node node, String key) {
        var seconds = node.getAttributeAsLong(key, (Long) null);
        return seconds == null || seconds <= 0 ? null : Instant.ofEpochSecond(seconds);
    }

    /**
     * Sends an acknowledgement stanza for the processed notification.
     *
     * <p>The ACK uses the notification's ID and sender JID, with
     * {@code class="notification"} and {@code type="privacy_token"}.
     *
     * @param node the original notification node
     * @implNote WAWebHandlePrivacyTokensNotification.default (ACK stanza construction in g)
     */
    private void sendNotificationAck(Node node) {
        // WAWebHandlePrivacyTokensNotification.default: wap("ack", {id, class, to, type})
        var stanzaId = node.getAttributeAsString("id", null);
        var stanzaFrom = node.getAttributeAsJid("from", null);
        if (stanzaId == null || stanzaFrom == null) {
            return;
        }

        whatsapp.sendNodeWithNoResponse(new NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId)
                .attribute("class", "notification")
                .attribute("to", stanzaFrom)
                .attribute("type", "privacy_token")
                .build());
    }
}
