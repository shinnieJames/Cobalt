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
 * {@code <notification type="link_code_companion_reg" stage="primary_hello">}
 * stanza pushed to a companion that is mid-flight in the link-code (alt
 * device) pairing flow.
 *
 * @apiNote
 * Surfaced to companion-side embedders so they can complete the
 * link-code handshake against a primary device that has just typed the
 * eight-character code. The payload carries the wrapped primary
 * ephemeral pubkey, the primary identity pubkey and the pairing
 * reference; the companion derives the shared secret from these three
 * inputs (see WA Web's {@code WAWebAltDeviceLinkingApi.handlePrimaryHello})
 * before replying with a {@link SmaxMdSetRegResponseClient} pair-device
 * signature.
 *
 * @implNote
 * This implementation maps the WA Web schema field names verbatim onto
 * Java fields ({@code linkCodePairingWrappedPrimaryEphemeralPub},
 * {@code primaryIdentityPub}, {@code linkCodePairingRef}) so the parser
 * can be diffed line by line against the upstream
 * {@link WhatsAppWebModule}
 * source. The {@code from} attribute is validated against the
 * {@code s.whatsapp.net} server domain to mirror the
 * {@code attrDomainJid("from", "s.whatsapp.net")} literal in the JS
 * parser.
 */
@WhatsAppWebModule(moduleName = "WASmaxInMdPrimaryHelloNotifyCompanionRequest")
@WhatsAppWebModule(moduleName = "WASmaxInMdServerNotificationMixin")
public final class SmaxMdPrimaryHelloNotifyCompanionResponse implements SmaxOperation.Response {
    /**
     * The {@code id} attribute of the inbound notification stanza.
     *
     * @apiNote
     * Echoed back into the
     * {@link SmaxMdPrimaryHelloNotifyCompanionAcknowledgement} stanza's
     * {@code id} attribute by
     * {@link SmaxMdPrimaryHelloNotifyCompanionAcknowledgement#from(SmaxMdPrimaryHelloNotifyCompanionResponse)}.
     */
    private final String notificationId;

    /**
     * The {@code from} JID of the inbound notification, always the
     * {@code s.whatsapp.net} server domain.
     *
     * @apiNote
     * Echoed back as the ack's {@code to} attribute by
     * {@link SmaxMdPrimaryHelloNotifyCompanionAcknowledgement#from(SmaxMdPrimaryHelloNotifyCompanionResponse)}.
     */
    private final Jid notificationFrom;

    /**
     * The wrapped primary ephemeral public-key bytes carried in
     * {@code <link_code_pairing_wrapped_primary_ephemeral_pub/>}.
     *
     * @apiNote
     * Used by the companion to unwrap the primary device's X25519
     * ephemeral pubkey with the AES-key derived from the eight-character
     * code, which then feeds the shared-secret derivation that produces
     * the ADV signing key.
     */
    private final byte[] linkCodePairingWrappedPrimaryEphemeralPub;

    /**
     * The primary device's identity public-key bytes carried in
     * {@code <primary_identity_pub/>}.
     *
     * @apiNote
     * Pinned by the companion as the trusted long-term identity of the
     * primary device before any pair-device-sign signature is sent.
     */
    private final byte[] primaryIdentityPub;

    /**
     * The pairing-reference bytes carried in
     * {@code <link_code_pairing_ref/>}.
     *
     * @apiNote
     * Matches the {@code ref} that the companion previously displayed in
     * its link-code prompt; consumed by WA Web as the join-token for
     * the rolling pairing window and re-issued by
     * {@link SmaxMdRefreshCodeNotifyCompanionResponse} when the window
     * expires.
     */
    private final byte[] linkCodePairingRef;

    /**
     * Constructs the typed projection from already-validated component
     * fields.
     *
     * @apiNote
     * Library code does not normally call this constructor; it is the
     * package-internal target of {@link #of(Node)} after parsing has
     * succeeded. Public visibility is preserved so unit tests can
     * construct fixtures directly.
     *
     * @param notificationId                            the notification id; never {@code null}
     * @param notificationFrom                          the notification sender JID; never {@code null}
     * @param linkCodePairingWrappedPrimaryEphemeralPub the wrapped ephemeral pubkey bytes; never {@code null}
     * @param primaryIdentityPub                        the primary identity pubkey bytes; never {@code null}
     * @param linkCodePairingRef                        the pairing-reference bytes; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public SmaxMdPrimaryHelloNotifyCompanionResponse(String notificationId,
                   Jid notificationFrom,
                   byte[] linkCodePairingWrappedPrimaryEphemeralPub,
                   byte[] primaryIdentityPub,
                   byte[] linkCodePairingRef) {
        this.notificationId = Objects.requireNonNull(notificationId, "notificationId cannot be null");
        this.notificationFrom = Objects.requireNonNull(notificationFrom, "notificationFrom cannot be null");
        this.linkCodePairingWrappedPrimaryEphemeralPub = Objects.requireNonNull(linkCodePairingWrappedPrimaryEphemeralPub, "linkCodePairingWrappedPrimaryEphemeralPub cannot be null");
        this.primaryIdentityPub = Objects.requireNonNull(primaryIdentityPub, "primaryIdentityPub cannot be null");
        this.linkCodePairingRef = Objects.requireNonNull(linkCodePairingRef, "linkCodePairingRef cannot be null");
    }

    /**
     * Returns the notification id.
     *
     * @apiNote
     * Used by
     * {@link SmaxMdPrimaryHelloNotifyCompanionAcknowledgement#from(SmaxMdPrimaryHelloNotifyCompanionResponse)}
     * to populate the matching {@code <ack id="..."/>}.
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
     * Always the {@code s.whatsapp.net} server domain JID; validated by
     * {@link #of(Node)} during parsing.
     *
     * @return the JID; never {@code null}
     */
    public Jid notificationFrom() {
        return notificationFrom;
    }

    /**
     * Returns the wrapped primary ephemeral public-key bytes.
     *
     * @apiNote
     * Feeds the link-code AES unwrap step that recovers the primary's
     * X25519 ephemeral pubkey.
     *
     * @return the wrapped pubkey bytes; never {@code null}
     */
    public byte[] linkCodePairingWrappedPrimaryEphemeralPub() {
        return linkCodePairingWrappedPrimaryEphemeralPub;
    }

    /**
     * Returns the primary device's identity public-key bytes.
     *
     * @apiNote
     * Pinned by the companion as the long-term identity of the primary
     * device before the pair-device-sign signature is generated.
     *
     * @return the identity pubkey bytes; never {@code null}
     */
    public byte[] primaryIdentityPub() {
        return primaryIdentityPub;
    }

    /**
     * Returns the pairing-reference bytes that the companion previously
     * displayed.
     *
     * @apiNote
     * Match against
     * {@link SmaxMdRefreshCodeNotifyCompanionResponse#linkCodePairingRef()}
     * when a refresh notification arrives to detect a stale ref versus
     * the current ref.
     *
     * @return the pairing-reference bytes; never {@code null}
     */
    public byte[] linkCodePairingRef() {
        return linkCodePairingRef;
    }

    /**
     * Parses a {@code <notification type="link_code_companion_reg" stage="primary_hello"/>}
     * stanza into a typed projection.
     *
     * @apiNote
     * The companion calls this once per inbound notification of that
     * type to unwrap the primary's pairing payload and continue the
     * link-code handshake. The returned {@link Optional} is empty when
     * the stanza shape diverges from the documented schema, mirroring
     * WA Web's {@code SmaxParsingFailure} swallowing in
     * {@code WAWebAltDeviceLinkingHandleNotification}.
     *
     * @implNote
     * This implementation enforces the same six structural checks as
     * WA Web's {@code parsePrimaryHelloNotifyCompanionRequest}: the
     * tag is {@code notification}, the {@code type} is
     * {@code link_code_companion_reg}, the {@code from} is the
     * {@code s.whatsapp.net} domain, the nested
     * {@code <link_code_companion_reg/>} child carries
     * {@code stage="primary_hello"}, and the three byte-payload
     * children {@code link_code_pairing_wrapped_primary_ephemeral_pub},
     * {@code primary_identity_pub} and {@code link_code_pairing_ref}
     * each resolve to non-empty content bytes. Any failure surfaces as
     * {@link Optional#empty()} rather than an exception.
     *
     * @param node the inbound notification stanza
     * @return an {@link Optional} carrying the typed projection, or
     *         empty when the stanza shape diverges from the schema
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInMdPrimaryHelloNotifyCompanionRequest",
            exports = "parsePrimaryHelloNotifyCompanionRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxMdPrimaryHelloNotifyCompanionResponse> of(Node node) {
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
        if (reg == null || !reg.hasAttribute("stage", "primary_hello")) {
            return Optional.empty();
        }
        var wrappedEphemeral = reg.getChild("link_code_pairing_wrapped_primary_ephemeral_pub")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        if (wrappedEphemeral == null) {
            return Optional.empty();
        }
        var identityPub = reg.getChild("primary_identity_pub")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        if (identityPub == null) {
            return Optional.empty();
        }
        var pairingRef = reg.getChild("link_code_pairing_ref")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        if (pairingRef == null) {
            return Optional.empty();
        }
        return Optional.of(new SmaxMdPrimaryHelloNotifyCompanionResponse(id, from, wrappedEphemeral, identityPub, pairingRef));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxMdPrimaryHelloNotifyCompanionResponse) obj;
        return Objects.equals(this.notificationId, that.notificationId)
                && Objects.equals(this.notificationFrom, that.notificationFrom)
                && Arrays.equals(this.linkCodePairingWrappedPrimaryEphemeralPub, that.linkCodePairingWrappedPrimaryEphemeralPub)
                && Arrays.equals(this.primaryIdentityPub, that.primaryIdentityPub)
                && Arrays.equals(this.linkCodePairingRef, that.linkCodePairingRef);
    }

    @Override
    public int hashCode() {
        var result = Objects.hash(notificationId, notificationFrom);
        result = 31 * result + Arrays.hashCode(linkCodePairingWrappedPrimaryEphemeralPub);
        result = 31 * result + Arrays.hashCode(primaryIdentityPub);
        result = 31 * result + Arrays.hashCode(linkCodePairingRef);
        return result;
    }

    @Override
    public String toString() {
        return "SmaxMdPrimaryHelloNotifyCompanionResponse[notificationId=" + notificationId
                + ", notificationFrom=" + notificationFrom
                + ", linkCodePairingWrappedPrimaryEphemeralPub=" + Arrays.toString(linkCodePairingWrappedPrimaryEphemeralPub)
                + ", primaryIdentityPub=" + Arrays.toString(primaryIdentityPub)
                + ", linkCodePairingRef=" + Arrays.toString(linkCodePairingRef) + ']';
    }
}
