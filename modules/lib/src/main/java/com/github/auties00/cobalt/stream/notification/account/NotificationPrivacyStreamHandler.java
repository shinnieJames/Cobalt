package com.github.auties00.cobalt.stream.notification.account;

import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;
import java.util.Arrays;

/**
 * Handles {@code type="privacy_token"} notifications carrying server-issued
 * trusted-contact tokens (TC tokens) for peers the user has interacted with.
 *
 * @apiNote
 * Dispatched by {@link NotificationAccountDispatcher}. A TC token is a
 * server-rolled blob whose presence on a chat is the trust signal WA Web
 * uses to gate the "verified identity" UI affordance for that contact.
 * Receiving a fresh token triggers a presence re-subscription so the
 * UI updates immediately.
 *
 * @implNote
 * This implementation persists the raw token content and its timestamp
 * on the chat record (preferring a LID-keyed chat over a PN-keyed one).
 * WA Web routes through {@code WAWebSetTcTokenChatAction.handleIncomingTcToken}
 * which records the same fields plus a server-issued sequence number;
 * Cobalt does not maintain that sequence number because the timestamp
 * guard inside {@link #updateChatTcToken} already serialises updates.
 */
@WhatsAppWebModule(moduleName = "WAWebHandlePrivacyTokensNotification")
final class NotificationPrivacyStreamHandler implements SocketStream.Handler {

    /**
     * Logger used for warnings about malformed stanzas and debug messages
     * about unknown token types.
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationPrivacyStreamHandler.class.getName());

    /**
     * The {@link WhatsAppClient} used for store reads and presence
     * re-subscription.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The {@link AckSender} used to ship the post-processing
     * {@code <ack class="notification" type="privacy_token"/>} stanza.
     */
    private final AckSender ackSender;

    /**
     * Constructs the handler with the shared client and ack sender.
     *
     * @apiNote
     * Called once by {@link NotificationAccountDispatcher}; embedders
     * do not instantiate this handler directly.
     *
     * @param whatsapp  the {@link WhatsAppClient}
     * @param ackSender the {@link AckSender}
     */
    NotificationPrivacyStreamHandler(WhatsAppClient whatsapp, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.ackSender = ackSender;
    }

    /**
     * Validates the stanza shape, processes each token child, and always
     * sends the protocol-level ACK.
     *
     * @apiNote
     * Invoked by {@link NotificationAccountDispatcher}.
     *
     * @param node the incoming {@code <notification>} stanza
     */
    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "privacy_token")) {
            return;
        }

        try {
            handleNotification(node);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Cannot handle privacy_token notification {0}: {1}",
                    node.getAttributeAsString("id", "<missing>"),
                    throwable.getMessage());
        } finally {
            sendNotificationAck(node);
        }
    }

    /**
     * Reads the sender PN and optional sender LID, waits for the offline
     * delivery window to end, then iterates each {@code <token>} child by
     * type.
     *
     * @apiNote
     * Mirrors WA Web's {@code incomingPrivacyTokensParser} which yields
     * a record with {@code from}, {@code senderLid}, and a list of
     * token entries. The
     * {@code waitForOfflineDeliveryEnd} barrier ensures the per-chat
     * record exists before the TC token is applied to it.
     *
     * @implNote
     * This implementation only knows the {@code trusted_contact} token
     * type; any other type is debug-logged and dropped, matching WA Web
     * which logs and ignores unknown types.
     *
     * @param node the {@code <notification>} stanza
     */
    private void handleNotification(Node node) {
        var senderPn = getUserJid(node, "from");
        var senderLid = node.getAttributeAsJid("sender_lid")
                .map(Jid::toUserJid)
                .orElse(null);
        if (senderPn == null) {
            return;
        }

        var tokensNode = node.getChild("tokens").orElse(null);
        if (tokensNode == null) {
            return;
        }

        whatsapp.store().waitForOfflineDeliveryEnd();

        for (var tokenNode : tokensNode.getChildren("token")) {
            var type = tokenNode.getAttributeAsString("type", "");
            switch (type) {
                case "trusted_contact" -> handleTrustedContactToken(senderPn, senderLid, tokenNode);
                default -> LOGGER.log(System.Logger.Level.DEBUG,
                        "incomingPrivacyTokensParser - receiving an unknown type: {0}", type);
            }
        }
    }

    /**
     * Applies a single {@code trusted_contact} token to the corresponding
     * chat and re-subscribes to the sender's presence.
     *
     * @apiNote
     * The presence re-subscription is what produces the visible "back
     * online" update in the chat thread once the TC token lands.
     * Non-user senders (PSAs, bots, server jids) are filtered out
     * because they cannot have a verified-identity affordance.
     *
     * @implNote
     * This implementation ignores presence-subscription failures by
     * debug-logging them; WA Web's
     * {@code PresenceCollection.reSubscribeWhenActive} retries
     * internally via the presence-queue worker.
     *
     * @param senderPn  the sender's phone-number JID
     * @param senderLid the sender's LID JID, or {@code null} when absent
     * @param tokenNode the {@code <token type="trusted_contact"/>} child carrying the token bytes and timestamp
     */
    private void handleTrustedContactToken(Jid senderPn, Jid senderLid, Node tokenNode) {
        if (!LidMigrationService.isRegularUser(senderPn)) {
            return;
        }

        var content = tokenNode.toContentBytes().orElse(null);
        if (content == null || content.length == 0) {
            return;
        }

        var tokenTimestamp = getInstantAttribute(tokenNode, "t");

        updateChatTcToken(senderPn, senderLid, tokenTimestamp, content);

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
     * Writes the new TC token bytes and timestamp to the chat record,
     * preferring a LID-keyed chat when one exists and creating a
     * PN-keyed chat record on demand otherwise.
     *
     * @apiNote
     * The chat preference order mirrors WA Web's
     * {@code handleIncomingTcToken} fallback chain: LID first, then PN
     * (with auto-creation).
     *
     * @implNote
     * This implementation short-circuits when the stored token equals
     * the incoming bytes (idempotent replay) or when the stored
     * timestamp is strictly fresher than the incoming one.
     *
     * @param senderPn       the sender's phone-number JID
     * @param senderLid      the sender's LID JID, or {@code null}
     * @param tokenTimestamp the server-reported token timestamp, or {@code null}
     * @param tcTokenContent the raw trusted-contact token bytes
     */
    private void updateChatTcToken(Jid senderPn, Jid senderLid, Instant tokenTimestamp, byte[] tcTokenContent) {
        var chat = (senderLid != null
                ? whatsapp.store().findChatByJid(senderLid).orElse(null)
                : null);
        if (chat == null) {
            chat = whatsapp.store().findChatByJid(senderPn)
                    .orElseGet(() -> whatsapp.store().addNewChat(senderPn));
        }

        var existingToken = chat.tcToken().orElse(null);
        var existingTimestamp = chat.tcTokenTimestamp().orElse(null);
        if (existingToken != null && Arrays.equals(existingToken, tcTokenContent)
                || existingTimestamp != null && tokenTimestamp != null && existingTimestamp.isAfter(tokenTimestamp)) {
            return;
        }

        chat.setTcToken(tcTokenContent);
        chat.setTcTokenTimestamp(tokenTimestamp);
    }

    /**
     * Reads a JID-valued attribute and reduces it to user form.
     *
     * @apiNote
     * Internal helper used by {@link #handleNotification} to extract
     * the sender's PN from the stanza's {@code from} attribute.
     *
     * @param node the node to read from
     * @param key  the attribute name
     * @return the parsed user JID, or {@code null} if absent
     */
    private Jid getUserJid(Node node, String key) {
        return node.getAttributeAsJid(key)
                .map(Jid::toUserJid)
                .orElse(null);
    }

    /**
     * Reads an epoch-seconds long attribute and converts it to an
     * {@link Instant}, returning {@code null} for absent or non-positive
     * values.
     *
     * @apiNote
     * Internal helper used by the {@code trusted_contact} branch only.
     *
     * @param node the node to read from
     * @param key  the attribute name
     * @return the parsed instant, or {@code null}
     */
    private Instant getInstantAttribute(Node node, String key) {
        var seconds = node.getAttributeAsLong(key, (Long) null);
        return seconds == null || seconds <= 0 ? null : Instant.ofEpochSecond(seconds);
    }

    /**
     * Sends the {@code <ack class="notification" type="privacy_token"/>}
     * stanza for the processed notification.
     *
     * @apiNote
     * Fire-and-forget; identical attribute set to WA Web's ack-builder
     * inside {@code incomingPrivacyTokensParser}.
     *
     * @param node the original {@code <notification>} stanza
     */
    private void sendNotificationAck(Node node) {
        ackSender.ack(AckClass.NOTIFICATION, node).type("privacy_token").send();
    }
}
