package com.github.auties00.cobalt.stream.notification.account;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.chat.ChatEphemeralTimer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;

/**
 * Handles incoming {@code disappearing_mode} notification stanzas.
 *
 * <p>When a contact changes their default disappearing-message duration, the server
 * pushes a notification of type {@code disappearing_mode}. This handler parses the
 * duration and setting timestamp from the child {@code <disappearing_mode>} element,
 * updates the corresponding chat's ephemeral timer in the local store (with a
 * timestamp guard to prevent stale updates from overwriting newer ones), and sends
 * an acknowledgement stanza back to the server.
 *
 * @implNote WAWebHandleDisappearingModeNotification.handleDisappearingModeNotificationJob
 */
final class NotificationDisappearingModeStreamHandler implements SocketStream.Handler {
    /**
     * The logger used to record diagnostic messages for this handler.
     *
     * @implNote WAWebHandleDisappearingModeNotification (WALogger usage)
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationDisappearingModeStreamHandler.class.getName());

    /**
     * The WhatsApp client instance providing access to the store and socket operations.
     *
     * @implNote WAWebHandleDisappearingModeNotification (module-level dependency)
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new handler with the specified WhatsApp client.
     *
     * @param whatsapp the non-{@code null} WhatsApp client instance
     * @implNote WAWebHandleDisappearingModeNotification (module initialization)
     */
    NotificationDisappearingModeStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * Handles a {@code disappearing_mode} notification stanza by parsing the disappearing
     * mode duration and setting timestamp, updating the corresponding chat's ephemeral
     * timer in the local store, and sending an acknowledgement stanza.
     *
     * <p>The handler extracts the {@code from} attribute as a user JID and looks up the
     * corresponding chat. If the chat does not exist, the notification is silently ignored
     * (matching WA Web's behavior of skipping absent contact records). Updates are guarded
     * by a timestamp comparison: the chat's ephemeral settings are only modified if the
     * existing setting timestamp is {@code null} (and the new timestamp is non-zero) or
     * if the existing timestamp is strictly less than the new one, preventing stale
     * notifications from overwriting newer settings.
     *
     * @param node the non-{@code null} notification stanza node
     * @implNote WAWebHandleDisappearingModeNotification.handleDisappearingModeNotificationJob
     */
    @Override
    public void handle(Node node) {
        try {
            handleDisappearingModeNotification(node);
        } finally {
            sendNotificationAck(node);
        }
    }

    /**
     * Performs the core disappearing-mode update logic by parsing the notification,
     * looking up the chat, applying the timestamp guard, and updating the ephemeral
     * settings.
     *
     * @param node the non-{@code null} notification stanza node
     * @implNote WAWebHandleDisappearingModeNotification (function d / c)
     */
    private void handleDisappearingModeNotification(Node node) {
        // WAWebHandleDisappearingModeNotification parser
        var from = node.getAttributeAsJid("from")
                .map(jid -> jid.toUserJid())
                .orElse(null);
        var disappearingMode = node.getChild("disappearing_mode").orElse(null);
        if (from == null || disappearingMode == null) {
            return;
        }

        // WAWebHandleDisappearingModeNotification parser: attrInt("duration", 0)
        var duration = disappearingMode.getAttributeAsInt("duration", 0);
        // WAWebHandleDisappearingModeNotification parser: attrTime("t")
        var rawTimestamp = disappearingMode.getAttributeAsLong("t", (Long) null);
        var settingTimestamp = rawTimestamp != null ? Instant.ofEpochSecond(rawTimestamp) : null;

        // ADAPTED: WAWebUpdateDisappearingModeForContact.updateDisappearingModeForContact
        // WA Web looks up a contact record; Cobalt uses chat-level ephemeral fields
        var chat = whatsapp.store()
                .findChatByJid(from)
                .orElse(null);
        if (chat == null) {
            // WAWebUpdateDisappearingModeForContact: if (s) { ... } - silently exits if contact not found
            return;
        }

        // WAWebUpdateDisappearingModeForContact: timestamp guard
        // Only update if existing timestamp is null and new != 0, or existing < new
        var existingTimestamp = chat.ephemeralSettingTimestamp().orElse(null); // WAWebUpdateDisappearingModeForContact
        var newTimestampSeconds = rawTimestamp != null ? rawTimestamp : 0L;
        if (existingTimestamp == null && newTimestampSeconds != 0
                || existingTimestamp != null && settingTimestamp != null && existingTimestamp.isBefore(settingTimestamp)) {
            chat.setEphemeralExpiration(ChatEphemeralTimer.of(duration)); // WAWebUpdateDisappearingModeForContact
            chat.setEphemeralSettingTimestamp(settingTimestamp); // WAWebUpdateDisappearingModeForContact
        }
    }

    /**
     * Sends an acknowledgement stanza back to the server for the received notification.
     *
     * <p>The ack stanza includes the original stanza's {@code id} as its own {@code id},
     * the {@code from} JID as the {@code to} target, a {@code class} of
     * {@code "notification"}, and a {@code type} of {@code "disappearing_mode"}.
     *
     * @param node the non-{@code null} notification stanza node to acknowledge
     * @implNote WAWebHandleDisappearingModeNotification (function d - WAWap.wap("ack", ...))
     */
    private void sendNotificationAck(Node node) {
        // WAWebHandleDisappearingModeNotification: wap("ack", { id, to, class, type })
        var stanzaId = node.getAttributeAsString("id", null);
        var stanzaFrom = node.getAttributeAsJid("from", null);
        if (stanzaId == null || stanzaFrom == null) {
            return;
        }

        whatsapp.sendNodeWithNoResponse(new NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId)
                .attribute("to", stanzaFrom)
                .attribute("class", "notification")
                .attribute("type", "disappearing_mode")
                .build());
    }
}
