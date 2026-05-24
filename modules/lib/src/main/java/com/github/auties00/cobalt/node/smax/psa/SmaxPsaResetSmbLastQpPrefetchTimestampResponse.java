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
 * The inbound projection of the
 * {@code <notification type="psa"><reset_smb_last_qp_prefetch_timestamp/></notification>}
 * stanza that asks the SMB client to refresh its locally-cached
 * quick-promotion prefetch timestamp.
 *
 * @apiNote
 * Drives the SMB quick-promotion refresh path:
 * {@code WAWebHandleQPPrefetchTimestampNotification} reacts to this
 * notification by clearing the locally-cached prefetch timestamp, returning
 * a {@link SmaxPsaResetSmbLastQpPrefetchTimestampAcknowledgement} to the
 * relay, and (when {@code qpGraphQLEnabledSMB()} is on) kicking off
 * {@code fetchQuickPromotionsNow} on the worker. This notification has no
 * effect on non-SMB accounts.
 */
@WhatsAppWebModule(moduleName = "WASmaxInPsaResetSmbLastQpPrefetchTimestampRequest")
@WhatsAppWebModule(moduleName = "WASmaxInPsaServerNotificationMixin")
public final class SmaxPsaResetSmbLastQpPrefetchTimestampResponse implements SmaxOperation.Response {
    /**
     * The notification id; echoed verbatim into the ack stanza.
     */
    private final String notificationId;

    /**
     * The notification sender JID. Always a user JID per the
     * {@code attrUserJid(from)} assertion in the WA Web parser.
     */
    private final Jid notificationFrom;

    /**
     * The notification type. Always the literal {@code "psa"} per the
     * {@code literal(attrString, "type", "psa")} assertion.
     */
    private final String notificationType;

    /**
     * The relay-side timestamp echoed by the
     * {@code WASmaxInPsaServerNotificationMixin} envelope.
     *
     * @apiNote
     * Carried by the {@code t} attribute as seconds since the Unix epoch.
     */
    private final long timestampSeconds;

    /**
     * The optional {@code offline} hint.
     *
     * @apiNote
     * When present, carries the count of offline notifications still queued
     * for delivery; the WA Web parser admits values {@code 0} to {@code 1024}
     * inclusive.
     */
    private final Integer offline;

    /**
     * Constructs an inbound projection.
     *
     * @param notificationId   the notification id; never {@code null}
     * @param notificationFrom the notification sender JID; never {@code null}
     * @param notificationType the notification type, always {@code "psa"}; never {@code null}
     * @param timestampSeconds the relay-side timestamp in seconds
     * @param offline          the optional offline-queue depth; may be {@code null}
     * @throws NullPointerException if any required argument is {@code null}
     */
    public SmaxPsaResetSmbLastQpPrefetchTimestampResponse(String notificationId, Jid notificationFrom, String notificationType,
                   long timestampSeconds, Integer offline) {
        this.notificationId = Objects.requireNonNull(notificationId, "notificationId cannot be null");
        this.notificationFrom = Objects.requireNonNull(notificationFrom, "notificationFrom cannot be null");
        this.notificationType = Objects.requireNonNull(notificationType, "notificationType cannot be null");
        this.timestampSeconds = timestampSeconds;
        this.offline = offline;
    }

    /**
     * Returns the notification id.
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
     * Returns the notification type. Always {@code "psa"} for this RPC.
     *
     * @return the type; never {@code null}
     */
    public String notificationType() {
        return notificationType;
    }

    /**
     * Returns the relay-side timestamp in seconds since the Unix epoch.
     *
     * @return the timestamp
     */
    public long timestampSeconds() {
        return timestampSeconds;
    }

    /**
     * Returns the optional offline-queue depth.
     *
     * @return an {@link Optional} carrying the depth, or empty when the
     *         attribute was absent
     */
    public Optional<Integer> offline() {
        return Optional.ofNullable(offline);
    }

    /**
     * Parses a {@code <notification>} stanza into an inbound projection.
     *
     * @apiNote
     * Mirrors {@code WASmaxPsaResetSmbLastQpPrefetchTimestampRPC.receiveResetSmbLastQpPrefetchTimestampRPC};
     * Cobalt returns {@link Optional#empty()} on schema mismatch instead of
     * throwing the JS {@code SmaxParsingFailure}.
     *
     * @implNote
     * This implementation mirrors {@code parseResetSmbLastQpPrefetchTimestampRequest}:
     * it requires the {@code <notification>} tag, the literal
     * {@code type="psa"} marker, the {@code <reset_smb_last_qp_prefetch_timestamp/>}
     * child, a user JID {@code from} attribute, an {@code t} attribute in
     * the {@code [0, +inf)} range, a stanza-id {@code id}, and an optional
     * {@code offline} attribute in {@code [0, 1024]} (modelled as a sentinel
     * {@code -1} when absent, surfaced via {@link #offline()}).
     *
     * @param node the inbound notification stanza; never {@code null}
     * @return an {@link Optional} carrying the projection, or empty on schema mismatch
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInPsaResetSmbLastQpPrefetchTimestampRequest",
            exports = "parseResetSmbLastQpPrefetchTimestampRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxPsaResetSmbLastQpPrefetchTimestampResponse> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        if (!node.hasDescription("notification")) {
            return Optional.empty();
        }
        if (!node.hasAttribute("type", "psa")) {
            return Optional.empty();
        }
        var resetChild = node.getChild("reset_smb_last_qp_prefetch_timestamp").orElse(null);
        if (resetChild == null) {
            return Optional.empty();
        }
        var from = node.getAttributeAsJid("from").orElse(null);
        if (from == null) {
            return Optional.empty();
        }
        var type = node.getAttributeAsString("type").orElse(null);
        if (type == null) {
            return Optional.empty();
        }
        var timestampOpt = node.getAttributeAsLong("t");
        if (timestampOpt.isEmpty()) {
            return Optional.empty();
        }
        var id = node.getAttributeAsString("id").orElse(null);
        if (id == null) {
            return Optional.empty();
        }
        var offlineAttr = node.getAttributeAsInt("offline").orElse(-1);
        var offline = offlineAttr < 0 ? null : Integer.valueOf(offlineAttr);
        return Optional.of(new SmaxPsaResetSmbLastQpPrefetchTimestampResponse(id, from, type, timestampOpt.getAsLong(), offline));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxPsaResetSmbLastQpPrefetchTimestampResponse) obj;
        return this.timestampSeconds == that.timestampSeconds
                && Objects.equals(this.notificationId, that.notificationId)
                && Objects.equals(this.notificationFrom, that.notificationFrom)
                && Objects.equals(this.notificationType, that.notificationType)
                && Objects.equals(this.offline, that.offline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(notificationId, notificationFrom, notificationType, timestampSeconds, offline);
    }

    @Override
    public String toString() {
        return "SmaxPsaResetSmbLastQpPrefetchTimestampResponse[notificationId=" + notificationId
                + ", notificationFrom=" + notificationFrom
                + ", notificationType=" + notificationType
                + ", timestampSeconds=" + timestampSeconds
                + ", offline=" + offline + ']';
    }
}
