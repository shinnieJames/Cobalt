package com.github.auties00.cobalt.node.smax.qp;

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
 * The outbound {@code <ack class="notification">} stanza emitted to
 * acknowledge a server-pushed {@link SmaxQpQpNotificationResponse}.
 *
 * @apiNote
 * Sent by the client back through the socket pipeline after consuming
 * a quick-promotion (QP) surfaces notification; WA Web's
 * {@code WAWebParseQPSurfacesNotification} invokes
 * {@code WASmaxQpSurfacesQPNotificationRPC.receiveQPNotificationRPC}
 * which returns the parsed request paired with a deferred
 * {@code makeQPNotificationResponseAck} factory, and the caller is
 * expected to flush the ack so the relay does not re-push the same
 * notification on the next reconnect.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutQpSurfacesQPNotificationResponseAck")
@WhatsAppWebModule(moduleName = "WASmaxOutQpSurfacesNotificationClientAckMixin")
public final class SmaxQpQpNotificationAcknowledgement implements SmaxOperation.Request {
    /**
     * The {@code id} of the notification being acknowledged.
     */
    private final String notificationId;

    /**
     * The {@code from} of the notification, which becomes the ack's
     * {@code to}.
     */
    private final Jid notificationFrom;

    /**
     * The {@code type} of the notification, echoed verbatim into the
     * ack.
     */
    private final String notificationType;

    /**
     * Constructs an acknowledgement for the given notification
     * attributes.
     *
     * @apiNote
     * Embedders that have a typed
     * {@link SmaxQpQpNotificationResponse} should prefer
     * {@link #from(SmaxQpQpNotificationResponse)}; this raw constructor
     * exists so the ack can also be built from a captured stanza
     * without round-tripping through the parser.
     *
     * @param notificationId the notification id; never {@code null}
     * @param notificationFrom the notification's sender JID; never
     *                         {@code null}
     * @param notificationType the notification type; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public SmaxQpQpNotificationAcknowledgement(String notificationId, Jid notificationFrom, String notificationType) {
        this.notificationId = Objects.requireNonNull(notificationId, "notificationId cannot be null");
        this.notificationFrom = Objects.requireNonNull(notificationFrom, "notificationFrom cannot be null");
        this.notificationType = Objects.requireNonNull(notificationType, "notificationType cannot be null");
    }

    /**
     * Lifts the three echoed attributes off the given typed
     * notification into a fresh acknowledgement.
     *
     * @apiNote
     * Use after consuming a {@link SmaxQpQpNotificationResponse} so
     * the ack carries the same {@code id}/{@code from}/{@code type}
     * triple the relay expects.
     *
     * @param inbound the inbound notification; never {@code null}
     * @return a new acknowledgement
     * @throws NullPointerException if {@code inbound} is {@code null}
     */
    public static SmaxQpQpNotificationAcknowledgement from(SmaxQpQpNotificationResponse inbound) {
        Objects.requireNonNull(inbound, "inbound cannot be null");
        return new SmaxQpQpNotificationAcknowledgement(inbound.notificationId(),
                inbound.notificationFrom(), inbound.notificationType());
    }

    /**
     * Lifts the three echoed attributes off the given raw
     * notification stanza into a fresh acknowledgement.
     *
     * @apiNote
     * Use when the consumer holds a raw {@link Node} but not a typed
     * {@link SmaxQpQpNotificationResponse}; the three required
     * attributes are read verbatim and a missing one is reported as
     * {@link IllegalArgumentException} rather than silently absorbed
     * (the relay expects every ack to round-trip the full triple).
     *
     * @param notification the inbound notification stanza; never
     *                     {@code null}
     * @return a new acknowledgement
     * @throws NullPointerException if {@code notification} is
     *                              {@code null}
     * @throws IllegalArgumentException if the notification is missing
     *                                  one of the required echoed
     *                                  attributes
     */
    public static SmaxQpQpNotificationAcknowledgement from(Node notification) {
        Objects.requireNonNull(notification, "notification cannot be null");
        var id = notification.getAttributeAsString("id")
                .orElseThrow(() -> new IllegalArgumentException("notification is missing id attribute"));
        var from = notification.getAttributeAsJid("from")
                .orElseThrow(() -> new IllegalArgumentException("notification is missing from attribute"));
        var type = notification.getAttributeAsString("type")
                .orElseThrow(() -> new IllegalArgumentException("notification is missing type attribute"));
        return new SmaxQpQpNotificationAcknowledgement(id, from, type);
    }

    /**
     * Returns the notification id being acknowledged.
     *
     * @return the id; never {@code null}
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
     * Builds the outbound ack stanza ready for dispatch.
     *
     * @apiNote
     * Produces {@code <ack id to class="notification" type/>}; the
     * stanza is fire-and-forget so no reply is expected and the
     * envelope carries no body.
     *
     * @return a {@link NodeBuilder} carrying the ack envelope
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutQpSurfacesQPNotificationResponseAck",
            exports = "makeQPNotificationResponseAck",
            adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        return new NodeBuilder()
                .description("ack")
                .attribute("id", notificationId)
                .attribute("to", notificationFrom)
                .attribute("class", "notification")
                .attribute("type", notificationType);
    }

    /**
     * Returns whether the given object is a
     * {@link SmaxQpQpNotificationAcknowledgement} with equal echoed
     * attributes.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when all three fields match
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxQpQpNotificationAcknowledgement) obj;
        return Objects.equals(this.notificationId, that.notificationId)
                && Objects.equals(this.notificationFrom, that.notificationFrom)
                && Objects.equals(this.notificationType, that.notificationType);
    }

    /**
     * Returns a hash code derived from the three echoed attributes.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(notificationId, notificationFrom, notificationType);
    }

    /**
     * Returns a debug-friendly textual representation of this
     * acknowledgement.
     *
     * @return the textual representation
     */
    @Override
    public String toString() {
        return "SmaxQpQpNotificationAcknowledgement[notificationId=" + notificationId
                + ", notificationFrom=" + notificationFrom
                + ", notificationType=" + notificationType + ']';
    }
}
