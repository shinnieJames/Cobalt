package com.github.auties00.cobalt.node.smax.coexistence;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * The inbound projection of
 * {@code <notification type="hosted"><onboarding_status/></notification>}.
 *
 * @apiNote
 * Surfaced by WA Web's {@code WAWebHandleHostedNotification} pipeline
 * when a third-party-coexistence link finishes (or fails) being
 * established; WA Web pivots on
 * {@code (onboardingStatusStatus, productSurface)} and, on
 * {@code ("completed", "automation")}, fires
 * {@code WAWebCTWADetectedOutcomeOnboardingStatusNotification.handleCTWADetectedOutcomeOnboardingStatusNotification(true)}.
 * Cobalt embedders wait on this projection to flip local provider
 * state to active and unblock messages routed through the new
 * integration.
 */
@WhatsAppWebModule(moduleName = "WASmaxInCoexistenceOnboardingStatusNotificationRequest")
@WhatsAppWebModule(moduleName = "WASmaxInCoexistenceServerNotificationMixin")
@WhatsAppWebModule(moduleName = "WASmaxInCoexistenceProductSurfaceMixin")
@WhatsAppWebModule(moduleName = "WASmaxInCoexistenceEnums")
public final class SmaxCoexistenceOnboardingStatusNotificationResponse implements SmaxOperation.Response {
    /**
     * The notification id used for delivery acknowledgement.
     *
     * @apiNote
     * Routed back to the relay in the {@code <ack/>} the receiver
     * sends after handling the notification.
     */
    private final String notificationId;

    /**
     * The notification {@code from} JID, always {@code s.whatsapp.net}.
     *
     * @apiNote
     * Validated against the WA server domain in {@link #of(Node)}.
     */
    private final Jid notificationFrom;

    /**
     * The {@code status} attribute on {@code <onboarding_status/>}.
     *
     * @apiNote
     * One of {@code "completed"} or {@code "failed"} per
     * {@code WASmaxInCoexistenceEnums.ENUM_COMPLETED_FAILED}; only
     * {@code "completed"} crosses the CTWA outcome handler in WA Web.
     */
    private final String onboardingStatusStatus;

    /**
     * The {@code product_surface} attribute on
     * {@code <onboarding_status/>}.
     *
     * @apiNote
     * One of {@code "ai_from_meta"}, {@code "automation"},
     * {@code "business_platform"} per
     * {@code WASmaxInCoexistenceEnums.ENUM_AIFROMMETA_AUTOMATION_BUSINESSPLATFORM}.
     */
    private final String onboardingStatusProductSurface;

    /**
     * The {@code <provider_info/>} sub-child.
     *
     * @apiNote
     * Carries the provider's display name, logo URL, and stable id;
     * see {@link SmaxCoexistenceOffboardingNotificationProviderInfo}
     * for per-field semantics.
     */
    private final SmaxCoexistenceOffboardingNotificationProviderInfo onboardingStatusProviderInfo;

    /**
     * Constructs a new onboarding-status-notification projection.
     *
     * @apiNote
     * Called by {@link #of(Node)} after the inbound stanza passes the
     * type, status, surface, and provider-info validation; embedders
     * rarely instantiate this class directly outside tests.
     *
     * @param notificationId                 the notification id;
     *                                       never {@code null}
     * @param notificationFrom               the from JID; never
     *                                       {@code null}
     * @param onboardingStatusStatus         the status enum literal;
     *                                       never {@code null}
     * @param onboardingStatusProductSurface the product-surface enum
     *                                       literal; never
     *                                       {@code null}
     * @param onboardingStatusProviderInfo   the provider-info
     *                                       projection; never
     *                                       {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public SmaxCoexistenceOnboardingStatusNotificationResponse(String notificationId,
                    Jid notificationFrom,
                    String onboardingStatusStatus,
                    String onboardingStatusProductSurface,
                    SmaxCoexistenceOffboardingNotificationProviderInfo onboardingStatusProviderInfo) {
        this.notificationId = Objects.requireNonNull(notificationId, "notificationId cannot be null");
        this.notificationFrom = Objects.requireNonNull(notificationFrom, "notificationFrom cannot be null");
        this.onboardingStatusStatus = Objects.requireNonNull(onboardingStatusStatus, "onboardingStatusStatus cannot be null");
        this.onboardingStatusProductSurface = Objects.requireNonNull(onboardingStatusProductSurface, "onboardingStatusProductSurface cannot be null");
        this.onboardingStatusProviderInfo = Objects.requireNonNull(onboardingStatusProviderInfo, "onboardingStatusProviderInfo cannot be null");
    }

    /**
     * Returns the notification id.
     *
     * @apiNote
     * Used to build the corresponding {@code <ack/>} stanza.
     *
     * @return the id; never {@code null}
     */
    public String notificationId() {
        return notificationId;
    }

    /**
     * Returns the notification {@code from} JID.
     *
     * @apiNote
     * Always the WA server JID; exposed so dispatch can key handlers
     * by sender.
     *
     * @return the JID; never {@code null}
     */
    public Jid notificationFrom() {
        return notificationFrom;
    }

    /**
     * Returns the status enum literal.
     *
     * @apiNote
     * One of {@code "completed"} or {@code "failed"}; embedders branch
     * on the value to decide whether the provider is now active or the
     * onboarding attempt was rejected.
     *
     * @return the literal; never {@code null}
     */
    public String onboardingStatusStatus() {
        return onboardingStatusStatus;
    }

    /**
     * Returns the product-surface enum literal.
     *
     * @apiNote
     * One of {@code "ai_from_meta"}, {@code "automation"},
     * {@code "business_platform"}.
     *
     * @return the literal; never {@code null}
     */
    public String onboardingStatusProductSurface() {
        return onboardingStatusProductSurface;
    }

    /**
     * Returns the provider-info projection.
     *
     * @apiNote
     * Carries the optional logo URL, name bytes, and provider id; see
     * {@link SmaxCoexistenceOffboardingNotificationProviderInfo}.
     *
     * @return the projection; never {@code null}
     */
    public SmaxCoexistenceOffboardingNotificationProviderInfo onboardingStatusProviderInfo() {
        return onboardingStatusProviderInfo;
    }

    /**
     * Tries to parse a {@link SmaxCoexistenceOnboardingStatusNotificationResponse}
     * projection from the given stanza.
     *
     * @apiNote
     * Mirrors
     * {@code WASmaxInCoexistenceOnboardingStatusNotificationRequest.parseOnboardingStatusNotificationRequest}
     * composed with the status, product-surface, and provider-info
     * mixins; empty when any attribute fails the documented
     * validation.
     *
     * @implNote
     * This implementation only lifts the notification {@code id} from
     * the server-notification mixin; the server timestamp {@code t}
     * and the optional {@code offline} batch index (0 to 1024) are
     * dropped because Cobalt's notification dispatch keys solely on
     * the id. WA Web's {@code parseServerNotificationMixin} returns
     * all three fields.
     *
     * @param node the inbound notification stanza; never {@code null}
     * @return an {@link Optional} carrying the projection
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxCoexistenceOnboardingStatusNotificationRPC",
            exports = "receiveOnboardingStatusNotificationRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInCoexistenceOnboardingStatusNotificationRequest",
            exports = "parseOnboardingStatusNotificationRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxCoexistenceOnboardingStatusNotificationResponse> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        if (!node.hasDescription("notification")) {
            return Optional.empty();
        }
        var onboardingStatus = node.getChild("onboarding_status").orElse(null);
        if (onboardingStatus == null) {
            return Optional.empty();
        }
        var from = node.getAttributeAsJid("from").orElse(null);
        if (from == null || !from.isServerJid(JidServer.user())) {
            return Optional.empty();
        }
        if (!node.hasAttribute("type", "hosted")) {
            return Optional.empty();
        }
        var status = onboardingStatus.getAttributeAsString("status").orElse(null);
        if (status == null || (!"completed".equals(status) && !"failed".equals(status))) {
            return Optional.empty();
        }
        var productSurface = onboardingStatus.getAttributeAsString("product_surface").orElse(null);
        if (productSurface == null
                || (!"ai_from_meta".equals(productSurface)
                && !"automation".equals(productSurface)
                && !"business_platform".equals(productSurface))) {
            return Optional.empty();
        }
        var providerInfo = SmaxCoexistenceOffboardingNotificationProviderInfo.of(onboardingStatus).orElse(null);
        if (providerInfo == null) {
            return Optional.empty();
        }
        var id = node.getAttributeAsString("id").orElse(null);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.of(new SmaxCoexistenceOnboardingStatusNotificationResponse(id, from, status, productSurface, providerInfo));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxCoexistenceOnboardingStatusNotificationResponse) obj;
        return Objects.equals(this.notificationId, that.notificationId)
                && Objects.equals(this.notificationFrom, that.notificationFrom)
                && Objects.equals(this.onboardingStatusStatus, that.onboardingStatusStatus)
                && Objects.equals(this.onboardingStatusProductSurface, that.onboardingStatusProductSurface)
                && Objects.equals(this.onboardingStatusProviderInfo, that.onboardingStatusProviderInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(notificationId, notificationFrom, onboardingStatusStatus,
                onboardingStatusProductSurface, onboardingStatusProviderInfo);
    }

    @Override
    public String toString() {
        return "SmaxCoexistenceOnboardingStatusNotificationResponse[notificationId=" + notificationId
                + ", notificationFrom=" + notificationFrom
                + ", onboardingStatusStatus=" + onboardingStatusStatus
                + ", onboardingStatusProductSurface=" + onboardingStatusProductSurface
                + ", onboardingStatusProviderInfo=" + onboardingStatusProviderInfo + ']';
    }
}
