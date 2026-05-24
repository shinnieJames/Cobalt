package com.github.auties00.cobalt.node.smax.mdcompanion;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <ack class="notification" type="link_code_companion_reg"/>}
 * stanza emitted by a companion after consuming a
 * {@link SmaxMdRefreshCodeNotifyCompanionResponse}.
 *
 * @apiNote
 * Companions send exactly one ack per inbound refresh-code
 * notification so the relay marks the notification delivered and
 * stops replaying it. WA Web's
 * {@code WAWebAltDeviceLinkingHandleNotification.handleAltDeviceLinkingNotification}
 * builds this stanza via the
 * {@code makeRefreshCodeNotifyCompanionResponseAck} thunk returned by
 * the {@code receiveRefreshCodeNotifyCompanionRPC} entry point.
 *
 * @implNote
 * This implementation folds WA Web's
 * {@code WASmaxOutMdNotificationClientAckMixin.mergeNotificationClientAckMixin}
 * into the builder: {@code id}, {@code to} and {@code type} are echoed
 * from the inbound notification, and {@code class} is pinned to the
 * literal {@code "notification"} that the mixin merges in.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutMdRefreshCodeNotifyCompanionResponseAck")
@WhatsAppWebModule(moduleName = "WASmaxOutMdNotificationClientAckMixin")
public final class SmaxMdRefreshCodeNotifyCompanionAcknowledgement implements SmaxOperation.Request {
    /**
     * The {@code id} of the inbound notification being acknowledged.
     *
     * @apiNote
     * Echoed into the outbound {@code <ack id="..."/>} attribute.
     */
    private final String notificationId;

    /**
     * The sender JID of the inbound notification, always the
     * {@code s.whatsapp.net} server domain.
     *
     * @apiNote
     * Echoed into the outbound {@code <ack to="..."/>} attribute.
     */
    private final Jid notificationFrom;

    /**
     * The {@code type} attribute of the inbound notification, fixed by
     * the link-code pairing flow to {@code "link_code_companion_reg"}.
     *
     * @apiNote
     * Echoed back into {@code <ack type="..."/>}.
     */
    private final String notificationType;

    /**
     * Constructs an ack from already-resolved component fields.
     *
     * @apiNote
     * Library code typically calls
     * {@link #from(SmaxMdRefreshCodeNotifyCompanionResponse)} to derive
     * the three echoed fields from an already-parsed notification
     * projection; this constructor is exposed for unit tests.
     *
     * @param notificationId   the notification id; never {@code null}
     * @param notificationFrom the notification sender JID; never {@code null}
     * @param notificationType the notification type; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public SmaxMdRefreshCodeNotifyCompanionAcknowledgement(String notificationId, Jid notificationFrom, String notificationType) {
        this.notificationId = Objects.requireNonNull(notificationId, "notificationId cannot be null");
        this.notificationFrom = Objects.requireNonNull(notificationFrom, "notificationFrom cannot be null");
        this.notificationType = Objects.requireNonNull(notificationType, "notificationType cannot be null");
    }

    /**
     * Derives the ack from an already-parsed
     * {@link SmaxMdRefreshCodeNotifyCompanionResponse} projection.
     *
     * @apiNote
     * Mirrors the WA Web pattern of obtaining the ack builder thunk
     * directly from the {@code receiveRefreshCodeNotifyCompanionRPC}
     * return value.
     *
     * @implNote
     * This implementation hardcodes the type to
     * {@code "link_code_companion_reg"} rather than reading it back
     * from the inbound projection, because the inbound parser only
     * accepts that exact literal.
     *
     * @param inbound the parsed inbound notification projection
     * @return a new acknowledgement
     * @throws NullPointerException if {@code inbound} is {@code null}
     */
    public static SmaxMdRefreshCodeNotifyCompanionAcknowledgement from(SmaxMdRefreshCodeNotifyCompanionResponse inbound) {
        Objects.requireNonNull(inbound, "inbound cannot be null");
        return new SmaxMdRefreshCodeNotifyCompanionAcknowledgement(inbound.notificationId(), inbound.notificationFrom(), "link_code_companion_reg");
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
     * @apiNote
     * Becomes the ack's {@code to} attribute.
     *
     * @return the JID; never {@code null}
     */
    public Jid notificationFrom() {
        return notificationFrom;
    }

    /**
     * Returns the {@code type} attribute echoed by the ack.
     *
     * @return the type; never {@code null}
     */
    public String notificationType() {
        return notificationType;
    }

    /**
     * Builds the outbound ack stanza.
     *
     * @apiNote
     * Returns the unfinished {@link NodeBuilder} so the dispatch path
     * can stamp the wire-level identifiers before flushing, matching
     * {@link SmaxOperation.Request#toNode()}.
     *
     * @implNote
     * This implementation pins {@code class} to the literal
     * {@code "notification"} because WA Web's
     * {@code mergeNotificationClientAckMixin} hardcodes the same
     * value into the stanza shape.
     *
     * @return a {@link NodeBuilder} carrying the {@code <ack>} envelope
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutMdRefreshCodeNotifyCompanionResponseAck",
            exports = "makeRefreshCodeNotifyCompanionResponseAck",
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
        var that = (SmaxMdRefreshCodeNotifyCompanionAcknowledgement) obj;
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
        return "SmaxMdRefreshCodeNotifyCompanionAcknowledgement[notificationId=" + notificationId
                + ", notificationFrom=" + notificationFrom
                + ", notificationType=" + notificationType + ']';
    }
}
