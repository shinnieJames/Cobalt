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
 * The typed projection of the inbound
 * {@code <notification type="link_code_companion_reg" stage="refresh_code">}
 * stanza pushed to a companion when the primary device rotates the
 * link-code pairing reference.
 *
 * @apiNote
 * Surfaced to companion-side embedders so they can rerender the eight
 * character link-code display when the rolling pairing window expires
 * or when the user explicitly requests a new code on the primary. WA
 * Web routes the parsed payload to either
 * {@code WAWebBackendApi.frontendFireAndForget("refreshAltLinkingCode")}
 * (natural cadence) or {@code "forceManualRefresh"} (manual primary
 * trigger) depending on the {@code force_manual_refresh} attribute.
 *
 * @implNote
 * This implementation mirrors WA Web's
 * {@code parseRefreshCodeNotifyCompanionRequest} validation set: the
 * tag is {@code notification}, the {@code type} is
 * {@code link_code_companion_reg}, the {@code from} is the
 * {@code s.whatsapp.net} domain, the nested
 * {@code <link_code_companion_reg/>} child carries
 * {@code stage="refresh_code"}, the optional
 * {@code force_manual_refresh} attribute is constrained to the
 * {@code ENUM_FALSE_TRUE} enum, and the new
 * {@code <link_code_pairing_ref/>} child resolves to non-empty bytes.
 */
@WhatsAppWebModule(moduleName = "WASmaxInMdRefreshCodeNotifyCompanionRequest")
@WhatsAppWebModule(moduleName = "WASmaxInMdServerNotificationMixin")
public final class SmaxMdRefreshCodeNotifyCompanionResponse implements SmaxOperation.Response {
    /**
     * The {@code id} attribute of the inbound notification.
     *
     * @apiNote
     * Echoed back into the
     * {@link SmaxMdRefreshCodeNotifyCompanionAcknowledgement} stanza's
     * {@code id} attribute by
     * {@link SmaxMdRefreshCodeNotifyCompanionAcknowledgement#from(SmaxMdRefreshCodeNotifyCompanionResponse)}.
     */
    private final String notificationId;

    /**
     * The {@code from} JID of the inbound notification, always the
     * {@code s.whatsapp.net} server domain.
     *
     * @apiNote
     * Echoed back as the ack's {@code to} attribute by
     * {@link SmaxMdRefreshCodeNotifyCompanionAcknowledgement#from(SmaxMdRefreshCodeNotifyCompanionResponse)}.
     */
    private final Jid notificationFrom;

    /**
     * The optional {@code force_manual_refresh} attribute on the
     * nested {@code <link_code_companion_reg/>}, restricted to
     * {@code "true"} or {@code "false"}.
     *
     * @apiNote
     * {@code "true"} signals that the primary device user pressed the
     * "regenerate code" button and surfaces as a different WAM marker
     * point ({@code receive_force_refresh_code} versus
     * {@code receive_refresh_code}); {@code "false"} or absent denotes
     * the natural rotation cadence. WA Web rejects any other value
     * during parsing.
     */
    private final String linkCodeCompanionRegForceManualRefresh;

    /**
     * The new pairing-reference bytes carried in
     * {@code <link_code_pairing_ref/>}.
     *
     * @apiNote
     * Replaces the previously displayed ref on the companion only when
     * it matches the ref the companion already holds; WA Web's
     * {@code handleAltDeviceLinkingNotification} compares via
     * {@code WACryptoUtils.uint8ArraysEqual} and silently drops
     * mismatches.
     */
    private final byte[] linkCodePairingRef;

    /**
     * Constructs the typed projection from already-validated component
     * fields.
     *
     * @apiNote
     * Library code does not normally call this constructor; it is the
     * target of {@link #of(Node)} after parsing has succeeded. Public
     * visibility is preserved so unit tests can construct fixtures.
     *
     * @param notificationId                         the notification id; never {@code null}
     * @param notificationFrom                       the notification sender JID; never {@code null}
     * @param linkCodeCompanionRegForceManualRefresh the optional {@code force_manual_refresh} flag; may be {@code null}
     * @param linkCodePairingRef                     the pairing-reference bytes; never {@code null}
     * @throws NullPointerException if any required argument is {@code null}
     */
    public SmaxMdRefreshCodeNotifyCompanionResponse(String notificationId,
                   Jid notificationFrom,
                   String linkCodeCompanionRegForceManualRefresh,
                   byte[] linkCodePairingRef) {
        this.notificationId = Objects.requireNonNull(notificationId, "notificationId cannot be null");
        this.notificationFrom = Objects.requireNonNull(notificationFrom, "notificationFrom cannot be null");
        this.linkCodeCompanionRegForceManualRefresh = linkCodeCompanionRegForceManualRefresh;
        this.linkCodePairingRef = Objects.requireNonNull(linkCodePairingRef, "linkCodePairingRef cannot be null");
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
     * @apiNote
     * Always the {@code s.whatsapp.net} server domain; validated by
     * {@link #of(Node)} during parsing.
     *
     * @return the JID; never {@code null}
     */
    public Jid notificationFrom() {
        return notificationFrom;
    }

    /**
     * Returns the optional {@code force_manual_refresh} flag.
     *
     * @apiNote
     * Callers should treat {@link Optional#empty()} as equivalent to
     * {@code "false"}; WA Web makes the same equivalence inside
     * {@code handleAltDeviceLinkingNotification}.
     *
     * @return an {@link Optional} carrying {@code "true"} or
     *         {@code "false"}, or empty when the relay omitted the
     *         attribute
     */
    public Optional<String> linkCodeCompanionRegForceManualRefresh() {
        return Optional.ofNullable(linkCodeCompanionRegForceManualRefresh);
    }

    /**
     * Returns the new pairing-reference bytes.
     *
     * @apiNote
     * Compare byte-for-byte against the companion's currently displayed
     * ref before adopting; non-matching refs are silently dropped per
     * WA Web's {@code handleAltDeviceLinkingNotification} logic.
     *
     * @return the new pairing-reference bytes; never {@code null}
     */
    public byte[] linkCodePairingRef() {
        return linkCodePairingRef;
    }

    /**
     * Parses a {@code <notification type="link_code_companion_reg" stage="refresh_code"/>}
     * stanza into a typed projection.
     *
     * @apiNote
     * Companions call this on every inbound notification of that type
     * to decide whether to rerender the displayed code; the returned
     * {@link Optional} is empty when the stanza shape diverges from
     * the documented schema.
     *
     * @implNote
     * This implementation enforces WA Web's full check set: tag
     * equality on {@code notification}, attribute literal on
     * {@code type}, domain-JID literal on {@code from}, stage literal
     * on the nested {@code <link_code_companion_reg/>}, optional
     * {@code force_manual_refresh} constrained to the
     * {@code ENUM_FALSE_TRUE} pair, and a non-empty
     * {@code <link_code_pairing_ref/>} content body.
     *
     * @param node the inbound notification stanza
     * @return an {@link Optional} carrying the projection, or empty
     *         when the stanza shape diverges
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInMdRefreshCodeNotifyCompanionRequest",
            exports = "parseRefreshCodeNotifyCompanionRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxMdRefreshCodeNotifyCompanionResponse> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        if (!node.hasDescription("notification")) {
            return Optional.empty();
        }
        if (!node.hasAttribute("type", "link_code_companion_reg")) {
            return Optional.empty();
        }
        var from = node.getAttributeAsJid("from").orElse(null);
        if (from == null || !"s.whatsapp.net".equals(from.server().toString())) {
            return Optional.empty();
        }
        var id = node.getAttributeAsString("id").orElse(null);
        if (id == null) {
            return Optional.empty();
        }
        var reg = node.getChild("link_code_companion_reg").orElse(null);
        if (reg == null || !reg.hasAttribute("stage", "refresh_code")) {
            return Optional.empty();
        }
        var forceManualRefresh = reg.getAttributeAsString("force_manual_refresh").orElse(null);
        if (forceManualRefresh != null && !"true".equals(forceManualRefresh) && !"false".equals(forceManualRefresh)) {
            return Optional.empty();
        }
        var pairingRef = reg.getChild("link_code_pairing_ref")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        if (pairingRef == null) {
            return Optional.empty();
        }
        return Optional.of(new SmaxMdRefreshCodeNotifyCompanionResponse(id, from, forceManualRefresh, pairingRef));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxMdRefreshCodeNotifyCompanionResponse) obj;
        return Objects.equals(this.notificationId, that.notificationId)
                && Objects.equals(this.notificationFrom, that.notificationFrom)
                && Objects.equals(this.linkCodeCompanionRegForceManualRefresh, that.linkCodeCompanionRegForceManualRefresh)
                && Arrays.equals(this.linkCodePairingRef, that.linkCodePairingRef);
    }

    @Override
    public int hashCode() {
        var result = Objects.hash(notificationId, notificationFrom, linkCodeCompanionRegForceManualRefresh);
        result = 31 * result + Arrays.hashCode(linkCodePairingRef);
        return result;
    }

    @Override
    public String toString() {
        return "SmaxMdRefreshCodeNotifyCompanionResponse[notificationId=" + notificationId
                + ", notificationFrom=" + notificationFrom
                + ", linkCodeCompanionRegForceManualRefresh=" + linkCodeCompanionRegForceManualRefresh
                + ", linkCodePairingRef=" + Arrays.toString(linkCodePairingRef) + ']';
    }
}
