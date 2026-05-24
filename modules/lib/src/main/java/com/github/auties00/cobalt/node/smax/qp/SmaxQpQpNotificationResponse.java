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
 * The inbound projection of a server-pushed quick-promotion (QP)
 * surfaces notification.
 *
 * @apiNote
 * Surfaces the relay's PSA-class push that carries the
 * {@code <surfaces/>} subtree of QP promotions and triggers. WA Web's
 * {@code WAWebParseQPSurfacesNotification} consumes this projection,
 * fans out the contained promotions through the QP UI pipeline, and
 * acknowledges receipt via
 * {@link SmaxQpQpNotificationAcknowledgement#from(SmaxQpQpNotificationResponse)};
 * embedders that surface QP promotions follow the same parse-then-ack
 * cadence.
 */
@WhatsAppWebModule(moduleName = "WASmaxInQpSurfacesQPNotificationRequest")
@WhatsAppWebModule(moduleName = "WASmaxInQpSurfacesQPSurfacesMixin")
@WhatsAppWebModule(moduleName = "WASmaxInQpSurfacesServerNotificationMixin")
public final class SmaxQpQpNotificationResponse implements SmaxOperation.Response {
    /**
     * The notification id; echoed verbatim into the ack stanza.
     */
    private final String notificationId;

    /**
     * The notification sender JID; becomes the ack's {@code to}.
     */
    private final Jid notificationFrom;

    /**
     * The notification type.
     *
     * @implNote
     * This implementation always stores the literal {@code "psa"}
     * after a successful parse; the field is preserved as typed state
     * rather than elided so the ack can echo it without a second
     * lookup against the original stanza.
     */
    private final String notificationType;

    /**
     * The raw {@code <surfaces/>} subtree carrying the QP surfaces,
     * promotions, and triggers payload.
     *
     * @implNote
     * This implementation keeps the subtree as a raw {@link Node}
     * rather than re-parsing it through the typed surfaces mixin;
     * consumers route it through their own protobuf pipeline. WA
     * Web's {@code WASmaxInQpSurfacesQPSurfacesMixin} performs the
     * equivalent typed parse upstream, but Cobalt has no per-surface
     * typed model and forwards the subtree verbatim.
     */
    private final Node surfacesNode;

    /**
     * Constructs a new inbound projection.
     *
     * @apiNote
     * Used by {@link #of(Node)} after the stanza shape has been
     * validated; embedders typically do not instantiate this
     * directly.
     *
     * @param notificationId the notification id; never {@code null}
     * @param notificationFrom the notification sender JID; never
     *                         {@code null}
     * @param notificationType the notification type; never {@code null}
     * @param surfacesNode the raw {@code <surfaces/>} subtree; never
     *                     {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public SmaxQpQpNotificationResponse(String notificationId, Jid notificationFrom, String notificationType,
                   Node surfacesNode) {
        this.notificationId = Objects.requireNonNull(notificationId, "notificationId cannot be null");
        this.notificationFrom = Objects.requireNonNull(notificationFrom, "notificationFrom cannot be null");
        this.notificationType = Objects.requireNonNull(notificationType, "notificationType cannot be null");
        this.surfacesNode = Objects.requireNonNull(surfacesNode, "surfacesNode cannot be null");
    }

    /**
     * Returns the notification id.
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
     * Returns the raw {@code <surfaces/>} subtree.
     *
     * @apiNote
     * Embedders re-parse this node through the QP-surfaces protobuf
     * pipeline; Cobalt does not project it into typed state.
     *
     * @return the {@code <surfaces/>} node; never {@code null}
     */
    public Node surfacesNode() {
        return surfacesNode;
    }

    /**
     * Parses an {@link SmaxQpQpNotificationResponse} projection from
     * the given {@code <notification/>} stanza.
     *
     * @apiNote
     * Returns {@link Optional#empty()} when the envelope is not a
     * {@code <notification type="psa">} carrying a non-null
     * {@code id}, a non-null {@code from}, and a {@code <surfaces/>}
     * child.
     *
     * @implNote
     * This implementation skips the typed surfaces and
     * server-notification mixins WA Web composes inside
     * {@code parseQPNotificationRequest}; both produce data the
     * consumer would simply forward to the QP pipeline, so the
     * subtree is kept as a raw {@link Node} and the typed parse is
     * deferred.
     *
     * @param node the inbound notification stanza; never {@code null}
     * @return an {@link Optional} carrying the projection, or empty
     *         when the stanza shape does not match
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInQpSurfacesQPNotificationRequest",
            exports = "parseQPNotificationRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxQpQpNotificationResponse> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        if (!node.hasDescription("notification")) {
            return Optional.empty();
        }
        if (!node.hasAttribute("type", "psa")) {
            return Optional.empty();
        }
        var id = node.getAttributeAsString("id").orElse(null);
        if (id == null) {
            return Optional.empty();
        }
        var from = node.getAttributeAsJid("from").orElse(null);
        if (from == null) {
            return Optional.empty();
        }
        var surfaces = node.getChild("surfaces").orElse(null);
        if (surfaces == null) {
            return Optional.empty();
        }
        return Optional.of(new SmaxQpQpNotificationResponse(id, from, "psa", surfaces));
    }

    /**
     * Returns whether the given object is a
     * {@link SmaxQpQpNotificationResponse} with equal echoed
     * attributes and surfaces subtree.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when every field matches
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxQpQpNotificationResponse) obj;
        return Objects.equals(this.notificationId, that.notificationId)
                && Objects.equals(this.notificationFrom, that.notificationFrom)
                && Objects.equals(this.notificationType, that.notificationType)
                && Objects.equals(this.surfacesNode, that.surfacesNode);
    }

    /**
     * Returns a hash code derived from every field.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(notificationId, notificationFrom, notificationType, surfacesNode);
    }

    /**
     * Returns a debug-friendly textual representation of this
     * projection.
     *
     * @apiNote
     * The {@code <surfaces/>} subtree is intentionally omitted from
     * the output so logs do not blow up on multi-promotion pushes.
     *
     * @return the textual representation
     */
    @Override
    public String toString() {
        return "SmaxQpQpNotificationResponse[notificationId=" + notificationId
                + ", notificationFrom=" + notificationFrom
                + ", notificationType=" + notificationType + ']';
    }
}
