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
 * Handles {@code type="disappearing_mode"} notifications carrying a
 * peer-initiated change to a one-to-one chat's ephemeral-message timer.
 *
 * @apiNote
 * Dispatched by {@link NotificationAccountDispatcher}. Each notification
 * targets the chat identified by its {@code from} attribute and updates
 * that chat's ephemeral expiration plus the per-chat "ephemeral setting
 * timestamp". The handler ignores stanzas whose target chat is not in
 * the local store; absent chats are not auto-created here.
 *
 * @implNote
 * This implementation operates on chat-level ephemeral fields; WA Web's
 * {@code WAWebUpdateDisappearingModeForContact.updateDisappearingModeForContact}
 * persists the same value on a contact record because WA Web models
 * disappearing-mode at the contact level rather than the chat level.
 * The semantics converge: each chat has at most one timer at a time
 * and the timestamp guard prevents a late stanza from clobbering a
 * fresher value.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleDisappearingModeNotification")
final class NotificationDisappearingModeStreamHandler implements SocketStream.Handler {
    /**
     * Logger reserved for future diagnostics; currently unused because
     * the handler logs only at exception sites.
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationDisappearingModeStreamHandler.class.getName());

    /**
     * The {@link WhatsAppClient} used for store reads and chat mutations.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The {@link AckSender} used to ship the post-processing
     * {@code <ack class="notification" type="disappearing_mode"/>}
     * stanza.
     */
    private final AckSender ackSender;

    /**
     * Constructs the handler with the shared client and ack sender.
     *
     * @apiNote
     * Called once by {@link NotificationAccountDispatcher}; embedders
     * do not instantiate this handler directly.
     *
     * @param whatsapp  the non-{@code null} client
     * @param ackSender the non-{@code null} ack sender
     */
    NotificationDisappearingModeStreamHandler(WhatsAppClient whatsapp, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.ackSender = ackSender;
    }

    /**
     * Applies the new ephemeral timer to the addressed chat (when known)
     * and sends the protocol-level ACK.
     *
     * @apiNote
     * Drives the in-thread "disappearing messages set to X" affordance
     * for the addressed contact. Invoked by
     * {@link NotificationAccountDispatcher}.
     *
     * @implNote
     * This implementation always sends the ACK regardless of whether
     * the chat existed, matching WA Web's
     * {@code WAWebHandleDisappearingModeNotification.handleDisappearingModeNotificationJob}
     * which returns the ack from the parser's promise resolution path.
     *
     * @param node the non-{@code null} {@code <notification>} stanza
     */
    @Override
    public void handle(Node node) {
        handleDisappearingModeNotification(node);
        sendNotificationAck(node);
    }

    /**
     * Parses the duration and setting timestamp from the
     * {@code <disappearing_mode>} child, looks up the addressed chat, and
     * writes the new timer only when the incoming timestamp is strictly
     * fresher than the stored one.
     *
     * @apiNote
     * The timestamp guard is what makes the mutation idempotent against
     * stanza replays: a stanza older than the stored value is ignored,
     * and a stanza with the same timestamp is also ignored.
     *
     * @implNote
     * This implementation only writes when (a) the stored timestamp is
     * absent and the incoming timestamp is non-zero, or (b) the stored
     * timestamp strictly precedes the incoming timestamp. WA Web's
     * {@code updateDisappearingModeForContact} performs the same guard
     * but at the contact-record level.
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
     * Sends the {@code <ack class="notification" type="disappearing_mode"/>}
     * stanza for the processed notification.
     *
     * @apiNote
     * Fire-and-forget; identical attribute set to WA Web's ack-builder
     * inside {@code handleDisappearingModeNotificationJob}.
     *
     * @param node the original {@code <notification>} stanza
     */
    private void sendNotificationAck(Node node) {
        ackSender.ack(AckClass.NOTIFICATION, node).type("disappearing_mode").send();
    }
}
