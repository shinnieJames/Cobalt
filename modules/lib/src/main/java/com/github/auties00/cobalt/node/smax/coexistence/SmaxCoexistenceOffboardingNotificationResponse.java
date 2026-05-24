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
 * {@code <notification type="hosted"><offboarding/></notification>}.
 *
 * @apiNote
 * Surfaced by WA Web's {@code WAWebHandleHostedNotification} pipeline
 * when the relay tears down a third-party-coexistence link (the
 * {@code AI from Meta}, {@code automation} provider, or
 * {@code business_platform} surface stops handling messages on behalf
 * of the user); WA Web pivots on the {@code product_surface} attribute
 * to fire
 * {@code WAWebCTWADetectedOutcomeOnboardingStatusNotification.handleCTWADetectedOutcomeOnboardingStatusNotification(false)}.
 * Cobalt embedders wait on this projection to clear local provider
 * state and surface a user-visible "coexistence ended" toast.
 */
@WhatsAppWebModule(moduleName = "WASmaxInCoexistenceOffboardingNotificationRequest")
@WhatsAppWebModule(moduleName = "WASmaxInCoexistenceServerNotificationMixin")
@WhatsAppWebModule(moduleName = "WASmaxInCoexistenceProductSurfaceMixin")
@WhatsAppWebModule(moduleName = "WASmaxInCoexistenceEnums")
public final class SmaxCoexistenceOffboardingNotificationResponse implements SmaxOperation.Response {
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
     * Validated against the WA server domain in {@link #of(Node)};
     * present so the dispatch layer can route the projection through
     * the same {@code from}-keyed handler registry as other server
     * notifications.
     */
    private final Jid notificationFrom;

    /**
     * The {@code product_surface} attribute on {@code <offboarding/>}.
     *
     * @apiNote
     * One of {@code "ai_from_meta"}, {@code "automation"},
     * {@code "business_platform"} per
     * {@code WASmaxInCoexistenceEnums.ENUM_AIFROMMETA_AUTOMATION_BUSINESSPLATFORM};
     * WA Web only branches on the {@code "automation"} case to call
     * the CTWA outcome handler.
     */
    private final String offboardingProductSurface;

    /**
     * The {@code <provider_info/>} sub-child.
     *
     * @apiNote
     * Carries the provider's display name, logo URL, and stable id so
     * embedders can disambiguate which third-party integration ended.
     */
    private final SmaxCoexistenceOffboardingNotificationProviderInfo offboardingProviderInfo;

    /**
     * Constructs a new offboarding-notification projection.
     *
     * @apiNote
     * Called by {@link #of(Node)} after the inbound stanza passes the
     * type, surface, and provider-info validation; embedders rarely
     * instantiate this class directly outside tests.
     *
     * @param notificationId            the notification id; never
     *                                  {@code null}
     * @param notificationFrom          the from JID; never
     *                                  {@code null}
     * @param offboardingProductSurface the product-surface enum
     *                                  literal; never {@code null}
     * @param offboardingProviderInfo   the provider-info projection;
     *                                  never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public SmaxCoexistenceOffboardingNotificationResponse(String notificationId,
                    Jid notificationFrom,
                    String offboardingProductSurface,
                    SmaxCoexistenceOffboardingNotificationProviderInfo offboardingProviderInfo) {
        this.notificationId = Objects.requireNonNull(notificationId, "notificationId cannot be null");
        this.notificationFrom = Objects.requireNonNull(notificationFrom, "notificationFrom cannot be null");
        this.offboardingProductSurface = Objects.requireNonNull(offboardingProductSurface, "offboardingProductSurface cannot be null");
        this.offboardingProviderInfo = Objects.requireNonNull(offboardingProviderInfo, "offboardingProviderInfo cannot be null");
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
     * Returns the product-surface enum literal.
     *
     * @apiNote
     * One of {@code "ai_from_meta"}, {@code "automation"},
     * {@code "business_platform"}; embedders branch on the value to
     * decide which surface-specific cleanup to run.
     *
     * @return the literal; never {@code null}
     */
    public String offboardingProductSurface() {
        return offboardingProductSurface;
    }

    /**
     * Returns the provider-info projection.
     *
     * @apiNote
     * Carries the optional logo URL, name bytes, and provider id; see
     * {@link SmaxCoexistenceOffboardingNotificationProviderInfo} for
     * the per-field semantics.
     *
     * @return the projection; never {@code null}
     */
    public SmaxCoexistenceOffboardingNotificationProviderInfo offboardingProviderInfo() {
        return offboardingProviderInfo;
    }

    /**
     * Tries to parse a {@link SmaxCoexistenceOffboardingNotificationResponse}
     * projection from the given stanza.
     *
     * @apiNote
     * Mirrors
     * {@code WASmaxInCoexistenceOffboardingNotificationRequest.parseOffboardingNotificationRequest}
     * composed with the product-surface and provider-info mixins;
     * empty when any attribute fails the documented validation.
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
    @WhatsAppWebExport(moduleName = "WASmaxCoexistenceOffboardingNotificationRPC",
            exports = "receiveOffboardingNotificationRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInCoexistenceOffboardingNotificationRequest",
            exports = "parseOffboardingNotificationRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxCoexistenceOffboardingNotificationResponse> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        if (!node.hasDescription("notification")) {
            return Optional.empty();
        }
        var offboarding = node.getChild("offboarding").orElse(null);
        if (offboarding == null) {
            return Optional.empty();
        }
        var from = node.getAttributeAsJid("from").orElse(null);
        if (from == null || !from.isServerJid(JidServer.user())) {
            return Optional.empty();
        }
        if (!node.hasAttribute("type", "hosted")) {
            return Optional.empty();
        }
        var productSurface = offboarding.getAttributeAsString("product_surface").orElse(null);
        if (productSurface == null
                || (!"ai_from_meta".equals(productSurface)
                && !"automation".equals(productSurface)
                && !"business_platform".equals(productSurface))) {
            return Optional.empty();
        }
        var providerInfo = SmaxCoexistenceOffboardingNotificationProviderInfo.of(offboarding).orElse(null);
        if (providerInfo == null) {
            return Optional.empty();
        }
        var id = node.getAttributeAsString("id").orElse(null);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.of(new SmaxCoexistenceOffboardingNotificationResponse(id, from, productSurface, providerInfo));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxCoexistenceOffboardingNotificationResponse) obj;
        return Objects.equals(this.notificationId, that.notificationId)
                && Objects.equals(this.notificationFrom, that.notificationFrom)
                && Objects.equals(this.offboardingProductSurface, that.offboardingProductSurface)
                && Objects.equals(this.offboardingProviderInfo, that.offboardingProviderInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(notificationId, notificationFrom, offboardingProductSurface, offboardingProviderInfo);
    }

    @Override
    public String toString() {
        return "SmaxCoexistenceOffboardingNotificationResponse[notificationId=" + notificationId
                + ", notificationFrom=" + notificationFrom
                + ", offboardingProductSurface=" + offboardingProductSurface
                + ", offboardingProviderInfo=" + offboardingProviderInfo + ']';
    }
}
