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
 * The inbound family of relay notifications echoing the
 * SMB-to-Meta data-sharing consent across linked devices.
 *
 * @apiNote
 * Used by the CTWA biz-business-notification dispatcher in
 * {@code WAWebCTWAParsePrivacy.parseCTWAPrivacy}, which extracts
 * the post-update consent value and forwards it to
 * {@code WAWebHandlePrivacySettingsNotification.handleSmbDataSharingSettingNotification}
 * so other linked devices stay in sync after a
 * {@link SmaxSetPrivacySettingResponse.Success} write. The family
 * carries a single permit because {@code Receive}-shape SMAX RPCs
 * have no outbound counterpart.
 */
public sealed interface SmaxSyncPrivacySettingResponse extends SmaxOperation.Response permits SmaxSyncPrivacySettingResponse.Notification {

    /**
     * Tries to parse the supplied stanza as a {@link Notification}.
     *
     * @apiNote
     * Called by the biz-notification dispatcher after detecting a
     * {@code <privacy/>} child. Returns {@link Optional#empty()}
     * when the stanza does not match the documented shape.
     *
     * @param node the inbound notification stanza; never
     *             {@code null}
     * @return an {@link Optional} carrying the parsed notification,
     *         or {@link Optional#empty()} when the stanza shape
     *         does not match
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxBizSettingsSyncPrivacySettingRPC",
            exports = "receiveSyncPrivacySettingRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<Notification> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        return Notification.of(node);
    }

    /**
     * The single permitted relay-pushed privacy-sync notification.
     *
     * @apiNote
     * Carries the post-update consent value plus the standard
     * envelope echoes; the consumer extracts
     * {@link #dataSharingConsent()} and writes it to the local
     * device's biz-privacy preference cache.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBizSettingsSyncPrivacySettingRequest")
    @WhatsAppWebModule(moduleName = "WASmaxInBizSettingsSmbDataSharingSettingMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInBizSettingsSmbDataSharingSettingValueMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInBizSettingsServerNotificationMixin")
    final class Notification implements SmaxSyncPrivacySettingResponse {
        /**
         * The optional {@code to} attribute (the local user JID);
         * {@code null} when the relay broadcasts to every linked
         * device.
         */
        private final Jid to;

        /**
         * The optional consent value (one of {@code "true"} /
         * {@code "false"} / {@code "notset"}); {@code null} when
         * the relay cleared the preference.
         */
        private final String dataSharingConsent;

        /**
         * Constructs a new notification.
         *
         * @apiNote
         * Called by {@link #of(Node)} after validating the stanza
         * envelope.
         *
         * @param to                 the optional target user JID;
         *                           may be {@code null}
         * @param dataSharingConsent the optional consent value; may
         *                           be {@code null}
         */
        public Notification(Jid to, String dataSharingConsent) {
            this.to = to;
            this.dataSharingConsent = dataSharingConsent;
        }

        /**
         * Returns the optional target user JID.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when the relay
         * broadcasts to every linked device without a specific
         * target.
         *
         * @return an {@link Optional} carrying the JID
         */
        public Optional<Jid> to() {
            return Optional.ofNullable(to);
        }

        /**
         * Returns the optional post-update consent value.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when the relay cleared
         * the preference. Decoded values are restricted to the
         * documented {@code "true"} / {@code "false"} /
         * {@code "notset"} tuple via
         * {@link SmaxBizSettingsFalseNotsetTrueFlag#of(String)}.
         *
         * @return an {@link Optional} carrying the consent value
         */
        public Optional<String> dataSharingConsent() {
            return Optional.ofNullable(dataSharingConsent);
        }

        /**
         * Tries to parse a notification from the supplied stanza.
         *
         * @apiNote
         * Accepts only stanzas tagged
         * {@code <notification type="business" from="s.whatsapp.net">}
         * carrying a {@code <privacy/>} child; the inner
         * {@code <smb_data_sharing_with_meta_consent>} echo is
         * optional and is decoded under the
         * {@link SmaxBizSettingsFalseNotsetTrueFlag} dictionary.
         *
         * @implNote
         * This implementation drops the inner consent value when
         * its {@code value} attribute lies outside the documented
         * dictionary, matching the JS
         * {@code optionalMerge / attrStringEnum} composition that
         * folds a failed inner parse into {@code null} without
         * failing the whole notification.
         *
         * @param node the inbound notification stanza
         * @return an {@link Optional} carrying the parsed
         *         notification, or {@link Optional#empty()} when
         *         the stanza shape does not match
         * @throws NullPointerException if {@code node} is
         *                              {@code null}
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBizSettingsSyncPrivacySettingRequest",
                exports = "parseSyncPrivacySettingRequest",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBizSettingsServerNotificationMixin",
                exports = "parseServerNotificationMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBizSettingsSmbDataSharingSettingMixin",
                exports = "parseSmbDataSharingSettingMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBizSettingsSmbDataSharingSettingValueMixin",
                exports = "parseSmbDataSharingSettingValueMixin",
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
            var privacy = node.getChild("privacy").orElse(null);
            if (privacy == null) {
                return Optional.empty();
            }
            var to = node.getAttributeAsJid("to").orElse(null);
            String consent = null;
            var consentNode = privacy.getChild("smb_data_sharing_with_meta_consent").orElse(null);
            if (consentNode != null) {
                var value = consentNode.getAttributeAsString("value").orElse(null);
                if (value != null && SmaxBizSettingsFalseNotsetTrueFlag.of(value).isPresent()) {
                    consent = value;
                }
            }
            return Optional.of(new Notification(to, consent));
        }

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
                    && Objects.equals(this.dataSharingConsent, that.dataSharingConsent);
        }

        @Override
        public int hashCode() {
            return Objects.hash(to, dataSharingConsent);
        }

        @Override
        public String toString() {
            return "SmaxSyncPrivacySettingResponse.Notification[to=" + to
                    + ", dataSharingConsent=" + dataSharingConsent + ']';
        }
    }
}
