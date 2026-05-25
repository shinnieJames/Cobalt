package com.github.auties00.cobalt.stream.notification.account;

import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.chat.ChatEphemeralTimer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;

/**
 * Handles {@code type="disappearing_mode"} notifications carrying a peer-initiated change to a
 * one-to-one chat's ephemeral-message timer.
 *
 * <p>Each notification targets the chat identified by its {@code from} attribute and updates that chat's
 * ephemeral expiration plus its per-chat ephemeral setting timestamp. Stanzas whose target chat is not
 * in the local store are ignored; absent chats are not auto-created here.</p>
 *
 * @implNote This implementation operates on chat-level ephemeral fields, whereas WA Web persists the
 * same value on a contact record because it models disappearing mode at the contact level. The
 * semantics converge: each chat has at most one timer at a time and the timestamp guard prevents a late
 * stanza from clobbering a fresher value.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleDisappearingModeNotification")
final class NotificationDisappearingModeStreamHandler implements SocketStream.Handler {
    /**
     * Logs diagnostics for this handler.
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationDisappearingModeStreamHandler.class.getName());

    /**
     * Holds the client used for store reads and chat mutations.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Holds the ack sender used to ship the post-processing
     * {@code <ack class="notification" type="disappearing_mode"/>} stanza.
     */
    private final AckSender ackSender;

    /**
     * Constructs the handler with the shared client and ack sender.
     *
     * @param whatsapp  the non-{@code null} client
     * @param ackSender the non-{@code null} ack sender
     */
    NotificationDisappearingModeStreamHandler(WhatsAppClient whatsapp, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.ackSender = ackSender;
    }

    /**
     * Applies the new ephemeral timer to the addressed chat (when known) and sends the protocol-level
     * ACK.
     *
     * <p>Delegates to {@link #handleDisappearingModeNotification(Node)} for the mutation, then always
     * sends the ACK regardless of whether the chat existed.</p>
     *
     * @implNote This implementation always sends the ACK regardless of whether the chat existed, matching
     * WA Web which returns the ack from the parser's promise resolution path.
     *
     * @param node the non-{@code null} {@code <notification>} stanza
     */
    @Override
    public void handle(Node node) {
        handleDisappearingModeNotification(node);
        sendNotificationAck(node);
    }

    /**
     * Parses the duration and setting timestamp from the {@code <disappearing_mode>} child, looks up the
     * addressed chat, and writes the new timer only when the incoming timestamp is strictly fresher than
     * the stored one.
     *
     * <p>The timestamp guard makes the mutation idempotent against stanza replays: the write happens only
     * when the stored timestamp is absent and the incoming timestamp is non-zero, or the stored timestamp
     * strictly precedes the incoming timestamp. A stanza older than or equal to the stored value is
     * ignored.</p>
     *
     * @implNote This implementation applies the same timestamp guard WA Web applies, but at the
     * chat-record level rather than the contact-record level.
     *
     * @param node the {@code <notification>} stanza
     */
    private void handleDisappearingModeNotification(Node node) {
        var from = node.getAttributeAsJid("from")
                .map(jid -> jid.toUserJid())
                .orElse(null);
        var disappearingMode = node.getChild("disappearing_mode").orElse(null);
        if (from == null || disappearingMode == null) {
            return;
        }

        var duration = disappearingMode.getAttributeAsInt("duration", 0);
        var rawTimestamp = disappearingMode.getAttributeAsLong("t", (Long) null);
        var settingTimestamp = rawTimestamp != null ? Instant.ofEpochSecond(rawTimestamp) : null;

        var chat = whatsapp.store()
                .findChatByJid(from)
                .orElse(null);
        if (chat == null) {
            return;
        }

        var existingTimestamp = chat.ephemeralSettingTimestamp().orElse(null);
        var newTimestampSeconds = rawTimestamp != null ? rawTimestamp : 0L;
        if (existingTimestamp == null && newTimestampSeconds != 0
                || existingTimestamp != null && settingTimestamp != null && existingTimestamp.isBefore(settingTimestamp)) {
            chat.setEphemeralExpiration(ChatEphemeralTimer.of(duration));
            chat.setEphemeralSettingTimestamp(settingTimestamp);
        }
    }

    /**
     * Sends the {@code <ack class="notification" type="disappearing_mode"/>} stanza for the processed
     * notification.
     *
     * <p>The ack is fire-and-forget.</p>
     *
     * @param node the original {@code <notification>} stanza
     */
    private void sendNotificationAck(Node node) {
        ackSender.ack(AckClass.NOTIFICATION, node).type("disappearing_mode").send();
    }
}
