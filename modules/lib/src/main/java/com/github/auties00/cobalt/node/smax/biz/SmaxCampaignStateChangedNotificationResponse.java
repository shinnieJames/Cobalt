package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed inbound reply hierarchy for the
 * {@code WASmaxSmbMeteredMessagesCampaignCampaignStateChangedNotificationRPC}
 * SMAX receive RPC.
 *
 * @apiNote
 * Carries the single {@link Notification} permit because the RPC is
 * {@code Receive}-shape (server-pushed only). The hierarchy mirrors the
 * WA Web pipeline in which
 * {@code WAWebHandleBusinessNotification} routes
 * {@code <notification type="business">} stanzas bearing an
 * {@code <mm_campaign/>} child into
 * {@code WAWebBizBroadcastMarketingCampaignNotificationEmitter.marketingCampaignNotificationEmitter},
 * which fans the integrity-check outcome of an SMB metered-messages
 * marketing campaign out to interested downstream listeners.
 */
public sealed interface SmaxCampaignStateChangedNotificationResponse extends SmaxOperation.Response
        permits SmaxCampaignStateChangedNotificationResponse.Notification {

    /**
     * Parses an inbound campaign-state-changed notification.
     *
     * @apiNote
     * Equivalent to {@link Notification#of(Node)}; the factory exists at
     * the sealed-interface level so callers can reach it without naming
     * the permitted concrete type. Returns empty when the stanza shape
     * does not match.
     *
     * @param node the inbound notification stanza received from the relay; never {@code null}
     * @return an {@link Optional} carrying the parsed notification, or empty when the stanza does not match
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxSmbMeteredMessagesCampaignCampaignStateChangedNotificationRPC",
            exports = "receiveCampaignStateChangedNotificationRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<Notification> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        return Notification.of(node);
    }

    /**
     * The server-pushed {@code Notification} variant carrying the
     * campaign identifiers, the new status, and the standard envelope
     * echoes.
     *
     * @apiNote
     * WA Web only re-emits the parsed notification to downstream
     * listeners when all three of {@link #adId()}, {@link #adGroupId()},
     * and {@link #adCreativeId()} are present; partial notifications are
     * silently dropped. The {@link #status()} literal selects between
     * {@code "ok"} (the campaign has cleared integrity review) and
     * {@code "integrityNotCleared"} (held back).
     */
    @WhatsAppWebModule(moduleName = "WASmaxInSmbMeteredMessagesCampaignCampaignStateChangedNotificationRequest")
    @WhatsAppWebModule(moduleName = "WASmaxInSmbMeteredMessagesCampaignServerNotificationMixin")
    final class Notification implements SmaxCampaignStateChangedNotificationResponse {
        /**
         * The optional {@code to} attribute (the local user JID); absent
         * when the relay broadcasts to every linked device simultaneously.
         */
        private final Jid to;

        /**
         * The optional {@code ad_id} attribute on the {@code <mm_campaign>}
         * child (Facebook ads-platform advertisement identifier).
         */
        private final String adId;

        /**
         * The optional {@code ad_group_id} attribute (parent
         * advertisement-group identifier).
         */
        private final String adGroupId;

        /**
         * The optional {@code ad_creative_id} attribute
         * (advertisement-creative identifier).
         */
        private final String adCreativeId;

        /**
         * The {@code status} attribute on the {@code <mm_campaign>} child,
         * one of the literals {@code "ok"} (cleared) or
         * {@code "integrityNotCleared"} (held back).
         */
        private final String status;

        /**
         * Constructs a notification from already-validated envelope and
         * payload values.
         *
         * @apiNote
         * Cobalt callers normally obtain a notification by parsing a
         * stanza via {@link #of(Node)}; this constructor is exposed for
         * tests and for hand-built fixtures.
         *
         * @param to           the optional target user JID; may be {@code null}
         * @param adId         the optional advertisement ID; may be {@code null}
         * @param adGroupId    the optional advertisement-group ID; may be {@code null}
         * @param adCreativeId the optional advertisement-creative ID; may be {@code null}
         * @param status       the campaign status literal; never {@code null}
         * @throws NullPointerException if {@code status} is {@code null}
         */
        public Notification(Jid to, String adId, String adGroupId,
                            String adCreativeId, String status) {
            this.to = to;
            this.adId = adId;
            this.adGroupId = adGroupId;
            this.adCreativeId = adCreativeId;
            this.status = Objects.requireNonNull(status, "status cannot be null");
        }

        /**
         * Returns the optional target user JID.
         *
         * @apiNote
         * Empty when the relay broadcasts the notification to every
         * linked device of the account.
         *
         * @return an {@link Optional} carrying the JID
         */
        public Optional<Jid> to() {
            return Optional.ofNullable(to);
        }

        /**
         * Returns the optional advertisement ID.
         *
         * @apiNote
         * Identifies the Facebook ads-platform advertisement whose
         * marketing-message campaign just transitioned state. WA Web
         * skips re-emission when this value is absent.
         *
         * @return an {@link Optional} carrying the ID
         */
        public Optional<String> adId() {
            return Optional.ofNullable(adId);
        }

        /**
         * Returns the optional advertisement-group ID.
         *
         * @apiNote
         * Identifies the parent ad group. WA Web skips re-emission when
         * this value is absent.
         *
         * @return an {@link Optional} carrying the ID
         */
        public Optional<String> adGroupId() {
            return Optional.ofNullable(adGroupId);
        }

        /**
         * Returns the optional advertisement-creative ID.
         *
         * @apiNote
         * Identifies the advertisement creative bound to the campaign.
         * WA Web skips re-emission when this value is absent.
         *
         * @return an {@link Optional} carrying the ID
         */
        public Optional<String> adCreativeId() {
            return Optional.ofNullable(adCreativeId);
        }

        /**
         * Returns the campaign status literal.
         *
         * @apiNote
         * One of {@code "ok"} (integrity review cleared) or
         * {@code "integrityNotCleared"} (held back); forwarded verbatim
         * to downstream listeners.
         *
         * @return the status; never {@code null}
         */
        public String status() {
            return status;
        }

        /**
         * Parses a notification from a {@code <notification type="business">}
         * stanza bearing an {@code <mm_campaign/>} child.
         *
         * @apiNote
         * Returns empty when the stanza tag is wrong, when the
         * {@code from} attribute is not the literal {@code s.whatsapp.net}
         * server JID, when the {@code type} attribute is not the literal
         * {@code "business"}, when the {@code <mm_campaign/>} child is
         * missing, or when its {@code status} attribute is absent. The
         * three optional ad identifiers may legally be absent; consumers
         * filter them out before re-emission.
         *
         * @implNote
         * This implementation matches the WA Web parser ordering: the
         * literal {@code from} server-JID and {@code type="business"}
         * checks fire before the {@code <mm_campaign/>} child lookup,
         * and the mandatory {@code status} attribute is validated before
         * the three optional ad identifiers are sampled.
         *
         * @param node the candidate {@code <notification/>} stanza; never {@code null}
         * @return an {@link Optional} carrying the parsed notification, or empty when parsing fails
         * @throws NullPointerException if {@code node} is {@code null}
         */
        @WhatsAppWebExport(moduleName = "WASmaxInSmbMeteredMessagesCampaignCampaignStateChangedNotificationRequest",
                exports = "parseCampaignStateChangedNotificationRequest",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Notification> of(Node node) {
            Objects.requireNonNull(node, "node cannot be null");
            if (!node.hasDescription("notification")) {
                return Optional.empty();
            }
            var fromValue = node.getAttributeAsString("from").orElse(null);
            if (!"s.whatsapp.net".equals(fromValue)) {
                return Optional.empty();
            }
            if (!node.hasAttribute("type", "business")) {
                return Optional.empty();
            }
            var campaign = node.getChild("mm_campaign").orElse(null);
            if (campaign == null) {
                return Optional.empty();
            }
            var status = campaign.getAttributeAsString("status").orElse(null);
            if (status == null) {
                return Optional.empty();
            }
            var to = node.getAttributeAsJid("to").orElse(null);
            var adId = campaign.getAttributeAsString("ad_id").orElse(null);
            var adGroupId = campaign.getAttributeAsString("ad_group_id").orElse(null);
            var adCreativeId = campaign.getAttributeAsString("ad_creative_id").orElse(null);
            return Optional.of(new Notification(to, adId, adGroupId, adCreativeId, status));
        }

        /**
         * Compares this notification to {@code obj} for structural
         * equality on all five slots.
         *
         * @param obj the candidate; may be {@code null}
         * @return {@code true} when {@code obj} is a {@link Notification}
         *         with matching {@link #to()}, {@link #adId()},
         *         {@link #adGroupId()}, {@link #adCreativeId()}, and
         *         {@link #status()}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Notification) obj;
            return Objects.equals(this.to, that.to)
                    && Objects.equals(this.adId, that.adId)
                    && Objects.equals(this.adGroupId, that.adGroupId)
                    && Objects.equals(this.adCreativeId, that.adCreativeId)
                    && Objects.equals(this.status, that.status);
        }

        /**
         * Returns a hash code consistent with {@link #equals(Object)}.
         *
         * @return the hash of all five slots
         */
        @Override
        public int hashCode() {
            return Objects.hash(to, adId, adGroupId, adCreativeId, status);
        }

        /**
         * Returns a debug-friendly rendering naming all five slots.
         *
         * @return a record-style string with the five slot values
         */
        @Override
        public String toString() {
            return "SmaxCampaignStateChangedNotificationResponse.Notification[to=" + to
                    + ", adId=" + adId
                    + ", adGroupId=" + adGroupId
                    + ", adCreativeId=" + adCreativeId
                    + ", status=" + status + ']';
        }
    }
}
