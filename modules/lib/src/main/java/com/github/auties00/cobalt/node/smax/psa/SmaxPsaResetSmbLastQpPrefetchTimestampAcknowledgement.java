package com.github.auties00.cobalt.node.smax.psa;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound acknowledgement stanza emitted in response to a
 * {@link SmaxPsaResetSmbLastQpPrefetchTimestampResponse} notification.
 *
 * @apiNote
 * Closes the loop for the SMB quick-promotions prefetch-timestamp reset
 * handled by {@code WAWebHandleQPPrefetchTimestampNotification}: the
 * notification-handler builds and returns this ack, while the worker-safe
 * fire-and-forget {@code fetchQuickPromotionsNow} kicks off the actual
 * SMB quick-promotion GraphQL refresh.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutPsaResetSmbLastQpPrefetchTimestampResponseAck")
@WhatsAppWebModule(moduleName = "WASmaxOutPsaNotificationClientAckMixin")
public final class SmaxPsaResetSmbLastQpPrefetchTimestampAcknowledgement implements SmaxOperation.Request {
    /**
     * The notification id being acknowledged; echoed verbatim back into the
     * ack stanza.
     */
    private final String notificationId;

    /**
     * The notification sender JID becomes the ack's {@code to} attribute.
     */
    private final Jid notificationFrom;

    /**
     * The notification type echoed back into the ack. Always
     * {@code "psa"} for this RPC.
     */
    private final String notificationType;

    /**
     * Constructs an acknowledgement.
     *
     * @param notificationId   the notification id; never {@code null}
     * @param notificationFrom the notification sender JID; never {@code null}
     * @param notificationType the notification type; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public SmaxPsaResetSmbLastQpPrefetchTimestampAcknowledgement(String notificationId, Jid notificationFrom, String notificationType) {
        this.notificationId = Objects.requireNonNull(notificationId, "notificationId cannot be null");
        this.notificationFrom = Objects.requireNonNull(notificationFrom, "notificationFrom cannot be null");
        this.notificationType = Objects.requireNonNull(notificationType, "notificationType cannot be null");
    }

    /**
     * Constructs an acknowledgement from a parsed inbound notification
     * stanza.
     *
     * @apiNote
     * Convenience factory mirroring the closure-builder returned by
     * {@code WASmaxPsaResetSmbLastQpPrefetchTimestampRPC.receiveResetSmbLastQpPrefetchTimestampRPC};
     * lifts {@code id}, {@code from}, and {@code type} verbatim from the
     * source notification.
     *
     * @param notification the inbound notification stanza; never {@code null}
     * @return a new acknowledgement
     * @throws NullPointerException     if {@code notification} is {@code null}
     * @throws IllegalArgumentException if the notification is missing one of
     *                                  the required echoed attributes
     */
    public static SmaxPsaResetSmbLastQpPrefetchTimestampAcknowledgement from(Node notification) {
        Objects.requireNonNull(notification, "notification cannot be null");
        var id = notification.getAttributeAsString("id")
                .orElseThrow(() -> new IllegalArgumentException("notification is missing id attribute"));
        var from = notification.getAttributeAsJid("from")
                .orElseThrow(() -> new IllegalArgumentException("notification is missing from attribute"));
        var type = notification.getAttributeAsString("type")
                .orElseThrow(() -> new IllegalArgumentException("notification is missing type attribute"));
        return new SmaxPsaResetSmbLastQpPrefetchTimestampAcknowledgement(id, from, type);
    }

    /**
     * Returns the notification id being acknowledged.
     *
     * @return the notification id; never {@code null}
     */
    public String notificationId() {
        return notificationId;
    }

    /**
     * Returns the notification sender JID.
     *
     * @return the sender JID; never {@code null}
     */
    public Jid notificationFrom() {
        return notificationFrom;
    }

    /**
     * Returns the notification type.
     *
     * @return the type; never {@code null}
     */
    public String notificationType() {
        return notificationType;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation emits an {@code <ack class="notification">} stanza
     * carrying the {@code id}, {@code to}, and {@code type} attributes
     * lifted from the source notification, mirroring
     * {@code makeResetSmbLastQpPrefetchTimestampResponseAck} +
     * {@code mergeNotificationClientAckMixin}.
     *
     * @return a {@link NodeBuilder} carrying the {@code <ack/>} stanza
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutPsaResetSmbLastQpPrefetchTimestampResponseAck",
            exports = "makeResetSmbLastQpPrefetchTimestampResponseAck",
            adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        return new NodeBuilder()
                .description("ack")
                .attribute("id", notificationId)
                .attribute("to", notificationFrom)
                .attribute("class", "notification")
                .attribute("type", notificationType);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxPsaResetSmbLastQpPrefetchTimestampAcknowledgement) obj;
        return Objects.equals(this.notificationId, that.notificationId)
                && Objects.equals(this.notificationFrom, that.notificationFrom)
                && Objects.equals(this.notificationType, that.notificationType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(notificationId, notificationFrom, notificationType);
    }

    @Override
    public String toString() {
        return "SmaxPsaResetSmbLastQpPrefetchTimestampAcknowledgement[notificationId=" + notificationId
                + ", notificationFrom=" + notificationFrom
                + ", notificationType=" + notificationType + ']';
    }
}
