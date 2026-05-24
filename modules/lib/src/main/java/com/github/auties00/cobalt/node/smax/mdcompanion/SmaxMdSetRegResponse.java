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
 * {@code <iq xmlns="md" type="set"><pair-success/></iq>} stanza that
 * concludes a successful multi-device pair-device handshake.
 *
 * @apiNote
 * Companions consume this stanza to learn the JID assigned to them by
 * the relay, the signed ADV device-identity bundle they must
 * countersign, the platform string of the primary device, and the
 * optional post-pair payload that
 * {@code WAWebHandleCanonicalRegistration.handleCanonicalRegistration}
 * unwraps via the embedded {@link SmaxMdSetRegEncryptionMetadata}.
 * Once verified the companion responds with either a
 * {@link SmaxMdSetRegResponseClient} (regular pair-device-sign reply),
 * a {@link SmaxMdSetRegResponseHostedClient} (hosted-pair-set reply),
 * or a {@link SmaxMdSetRegResponseError} when verification fails.
 *
 * @implNote
 * This implementation enforces WA Web's full
 * {@code parseSetRegRequest} schema: the outer tag is {@code iq}, the
 * {@code xmlns} is {@code md}, the {@code type} is {@code set}, the
 * {@code from} is the {@code s.whatsapp.net} domain, the inner
 * {@code <pair-success/>} child carries a non-empty
 * {@code <device-identity/>} payload (WA Web caps the byte length to
 * {@code [1..500]}; Cobalt skips the cap), a {@code <device/>} child
 * exposing a {@code jid} attribute and an optional {@code lid} and
 * {@code beta} attribute, a {@code <platform name="..."/>} child, and
 * the three optional children {@code <biz name="..."/>},
 * {@code <client-props/>}, and {@code <encryption-metadata/>}.
 */
@WhatsAppWebModule(moduleName = "WASmaxInMdSetRegRequest")
public final class SmaxMdSetRegResponse implements SmaxOperation.Response {
    /**
     * The {@code id} attribute of the inbound IQ stanza.
     *
     * @apiNote
     * Echoed back into the matching
     * {@link SmaxMdSetRegResponseClient}, {@link SmaxMdSetRegResponseHostedClient}
     * or {@link SmaxMdSetRegResponseError} reply so the relay can pair
     * request and response.
     */
    private final String iqId;

    /**
     * The signed device-identity bytes carried in
     * {@code <pair-success><device-identity/></pair-success>}.
     *
     * @apiNote
     * Decoded as an ADV {@code ADVSignedDeviceIdentityHMAC} by the
     * post-pairing flow; WA Web verifies the HMAC against the ADV
     * secret key and then countersigns the inner
     * {@code ADVSignedDeviceIdentity}.
     */
    private final byte[] pairSuccessDeviceIdentity;

    /**
     * The device JID assigned to the companion by the relay, carried
     * in {@code <pair-success><device jid="..."/></pair-success>}.
     *
     * @apiNote
     * Pinned as the canonical "me" identity by
     * {@code WAWebUserPrefsMeUser.setMe} during post-pairing.
     */
    private final Jid pairSuccessDeviceJid;

    /**
     * The optional LID-form device JID, carried in the same
     * {@code <device/>} element via the {@code lid} attribute.
     *
     * @apiNote
     * When present, additionally pinned as the "me lid" via
     * {@code setMeLid}. Empty for devices on accounts that the relay
     * has not migrated to LID-form addressing.
     */
    private final Jid pairSuccessDeviceLid;

    /**
     * The optional {@code beta} attribute on {@code <device/>},
     * restricted to {@code "true"} or {@code "false"}.
     *
     * @apiNote
     * Signals the primary device's beta-channel enrolment to the
     * companion; preserved on the projection for embedders that wish
     * to mirror the flag in their own state. WA Web does not consume
     * the value directly inside {@code handlePairSuccess}.
     */
    private final String pairSuccessDeviceBeta;

    /**
     * The {@code name} attribute of the {@code <platform/>} child,
     * such as {@code "android"}, {@code "iphone"} or {@code "smbi"}.
     *
     * @apiNote
     * Stored as {@code Conn.platform} in WA Web; downstream consumers
     * key feature gates off this value.
     */
    private final String pairSuccessPlatformName;

    /**
     * The optional business-account display name, carried in the
     * {@code <biz name="..."/>} child.
     *
     * @apiNote
     * Present only for business-account pairings; surfaced through
     * {@link #pairSuccessBizName()} for embedders that wish to display
     * the business name during the post-pair handshake.
     */
    private final String pairSuccessBizName;

    /**
     * The optional client-pairing-properties bytes carried in
     * {@code <client-props/>}.
     *
     * @apiNote
     * Encoded as a {@code ClientPairingPropsSpec} protobuf; WA Web's
     * {@code handlePairSuccess} decodes the bytes to learn whether the
     * primary advertises a LID-migrated chat database, a syncd pure
     * LID session, syncd snapshot recovery support, and any seed
     * subscription-sync payload.
     */
    private final byte[] pairSuccessClientProps;

    /**
     * The optional encryption-metadata projection carried in
     * {@code <encryption-metadata/>}.
     *
     * @apiNote
     * Wraps the post-pairing key payload consumed by
     * {@code handleCanonicalRegistration}; empty for primaries that do
     * not ship a canonical-registration payload.
     */
    private final SmaxMdSetRegEncryptionMetadata pairSuccessEncryptionMetadata;

    /**
     * Constructs the typed projection from already-validated component
     * fields.
     *
     * @apiNote
     * Library code does not normally call this constructor; it is the
     * target of {@link #of(Node)} after parsing has succeeded. Public
     * visibility is preserved so unit tests can construct fixtures.
     *
     * @param iqId                          the IQ id; never {@code null}
     * @param pairSuccessDeviceIdentity     the device-identity bytes; never {@code null}
     * @param pairSuccessDeviceJid          the device JID; never {@code null}
     * @param pairSuccessDeviceLid          the optional LID-form JID; may be {@code null}
     * @param pairSuccessDeviceBeta         the optional beta flag; may be {@code null}
     * @param pairSuccessPlatformName       the platform name; never {@code null}
     * @param pairSuccessBizName            the optional business name; may be {@code null}
     * @param pairSuccessClientProps        the optional client-props bytes; may be {@code null}
     * @param pairSuccessEncryptionMetadata the optional encryption-metadata projection; may be {@code null}
     * @throws NullPointerException if any required argument is {@code null}
     */
    public SmaxMdSetRegResponse(String iqId,
                   byte[] pairSuccessDeviceIdentity,
                   Jid pairSuccessDeviceJid,
                   Jid pairSuccessDeviceLid,
                   String pairSuccessDeviceBeta,
                   String pairSuccessPlatformName,
                   String pairSuccessBizName,
                   byte[] pairSuccessClientProps,
                   SmaxMdSetRegEncryptionMetadata pairSuccessEncryptionMetadata) {
        this.iqId = Objects.requireNonNull(iqId, "iqId cannot be null");
        this.pairSuccessDeviceIdentity = Objects.requireNonNull(pairSuccessDeviceIdentity, "pairSuccessDeviceIdentity cannot be null");
        this.pairSuccessDeviceJid = Objects.requireNonNull(pairSuccessDeviceJid, "pairSuccessDeviceJid cannot be null");
        this.pairSuccessDeviceLid = pairSuccessDeviceLid;
        this.pairSuccessDeviceBeta = pairSuccessDeviceBeta;
        this.pairSuccessPlatformName = Objects.requireNonNull(pairSuccessPlatformName, "pairSuccessPlatformName cannot be null");
        this.pairSuccessBizName = pairSuccessBizName;
        this.pairSuccessClientProps = pairSuccessClientProps;
        this.pairSuccessEncryptionMetadata = pairSuccessEncryptionMetadata;
    }

    /**
     * Returns the IQ id.
     *
     * @apiNote
     * Echoed back as the matching reply's {@code id} attribute.
     *
     * @return the id; never {@code null}
     */
    public String iqId() {
        return iqId;
    }

    /**
     * Returns the signed device-identity bytes.
     *
     * @apiNote
     * Decoded as an ADV {@code ADVSignedDeviceIdentityHMAC} by the
     * companion's post-pair handler; the HMAC must verify against the
     * ADV secret key before the companion countersigns.
     *
     * @return the bytes; never {@code null}
     */
    public byte[] pairSuccessDeviceIdentity() {
        return pairSuccessDeviceIdentity;
    }

    /**
     * Returns the device JID assigned to the companion.
     *
     * @return the JID; never {@code null}
     */
    public Jid pairSuccessDeviceJid() {
        return pairSuccessDeviceJid;
    }

    /**
     * Returns the optional LID-form device JID.
     *
     * @apiNote
     * Present only for LID-addressed accounts; callers should fall back
     * to {@link #pairSuccessDeviceJid()} when this is empty.
     *
     * @return an {@link Optional} carrying the LID, or empty when the
     *         relay omitted the attribute
     */
    public Optional<Jid> pairSuccessDeviceLid() {
        return Optional.ofNullable(pairSuccessDeviceLid);
    }

    /**
     * Returns the optional beta flag.
     *
     * @return an {@link Optional} carrying {@code "true"} or
     *         {@code "false"}, or empty when the relay omitted the
     *         attribute
     */
    public Optional<String> pairSuccessDeviceBeta() {
        return Optional.ofNullable(pairSuccessDeviceBeta);
    }

    /**
     * Returns the platform name.
     *
     * @return the name; never {@code null}
     */
    public String pairSuccessPlatformName() {
        return pairSuccessPlatformName;
    }

    /**
     * Returns the optional business-account display name.
     *
     * @return an {@link Optional} carrying the name, or empty for
     *         non-business pairings
     */
    public Optional<String> pairSuccessBizName() {
        return Optional.ofNullable(pairSuccessBizName);
    }

    /**
     * Returns the optional client-pairing-properties bytes.
     *
     * @apiNote
     * Decoded as a {@code ClientPairingPropsSpec} protobuf by
     * embedders that mirror WA Web's
     * {@code applyClientPairingProps}; callers that do not consume the
     * payload may safely ignore it.
     *
     * @return an {@link Optional} carrying the bytes, or empty when the
     *         relay omitted the child
     */
    public Optional<byte[]> pairSuccessClientProps() {
        return Optional.ofNullable(pairSuccessClientProps);
    }

    /**
     * Returns the optional encryption-metadata projection.
     *
     * @apiNote
     * Empty for primaries that do not ship a canonical-registration
     * payload; otherwise feeds the GCM unwrap step inside
     * {@code handleCanonicalRegistration}.
     *
     * @return an {@link Optional} carrying the projection, or empty
     */
    public Optional<SmaxMdSetRegEncryptionMetadata> pairSuccessEncryptionMetadata() {
        return Optional.ofNullable(pairSuccessEncryptionMetadata);
    }

    /**
     * Parses an inbound {@code <iq><pair-success/></iq>} stanza into
     * the typed projection.
     *
     * @apiNote
     * Companions call this on every inbound IQ-set whose first child
     * is {@code <pair-success/>}; the returned {@link Optional} is
     * empty when the stanza shape diverges from the documented
     * schema, matching WA Web's {@code SmaxParsingFailure} swallowing
     * in {@code WAWebHandlePairSuccess}.
     *
     * @implNote
     * This implementation runs the same eleven checks as WA Web's
     * {@code parseSetRegRequest}: tag equality on {@code iq},
     * attribute literal on {@code xmlns="md"} and {@code type="set"},
     * domain-JID literal on {@code from}, presence of an {@code id}
     * attribute, a present {@code <pair-success/>} child with a
     * non-empty {@code <device-identity/>} body, a {@code <device/>}
     * with a {@code jid} attribute (plus optional {@code lid} and
     * {@code beta}), a {@code <platform/>} with a {@code name}
     * attribute, and the three optional children. Upstream restricts
     * {@code <device-identity/>} content to {@code [1..500]} bytes;
     * Cobalt skips the upper-bound check and lets downstream protobuf
     * decoding fail on oversize payloads.
     *
     * @param node the inbound IQ stanza
     * @return an {@link Optional} carrying the projection, or empty
     *         when the stanza shape diverges from the schema
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInMdSetRegRequest",
            exports = "parseSetRegRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxMdSetRegResponse> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        if (!node.hasDescription("iq")) {
            return Optional.empty();
        }
        if (!node.hasAttribute("xmlns", "md")) {
            return Optional.empty();
        }
        if (!node.hasAttribute("type", "set")) {
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
        var pairSuccess = node.getChild("pair-success").orElse(null);
        if (pairSuccess == null) {
            return Optional.empty();
        }
        var deviceIdentity = pairSuccess.getChild("device-identity")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        if (deviceIdentity == null) {
            return Optional.empty();
        }
        var deviceChild = pairSuccess.getChild("device").orElse(null);
        if (deviceChild == null) {
            return Optional.empty();
        }
        var deviceJid = deviceChild.getAttributeAsJid("jid").orElse(null);
        if (deviceJid == null) {
            return Optional.empty();
        }
        var deviceLid = deviceChild.getAttributeAsJid("lid").orElse(null);
        var deviceBeta = deviceChild.getAttributeAsString("beta").orElse(null);
        if (deviceBeta != null && !"true".equals(deviceBeta) && !"false".equals(deviceBeta)) {
            return Optional.empty();
        }
        var platformChild = pairSuccess.getChild("platform").orElse(null);
        if (platformChild == null) {
            return Optional.empty();
        }
        var platformName = platformChild.getAttributeAsString("name").orElse(null);
        if (platformName == null) {
            return Optional.empty();
        }
        var bizName = pairSuccess.getChild("biz")
                .flatMap(biz -> biz.getAttributeAsString("name"))
                .orElse(null);
        var clientProps = pairSuccess.getChild("client-props")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        var encryptionMetadata = pairSuccess.getChild("encryption-metadata")
                .flatMap(SmaxMdSetRegEncryptionMetadata::of)
                .orElse(null);
        return Optional.of(new SmaxMdSetRegResponse(id, deviceIdentity, deviceJid, deviceLid, deviceBeta,
                platformName, bizName, clientProps, encryptionMetadata));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxMdSetRegResponse) obj;
        return Objects.equals(this.iqId, that.iqId)
                && Arrays.equals(this.pairSuccessDeviceIdentity, that.pairSuccessDeviceIdentity)
                && Objects.equals(this.pairSuccessDeviceJid, that.pairSuccessDeviceJid)
                && Objects.equals(this.pairSuccessDeviceLid, that.pairSuccessDeviceLid)
                && Objects.equals(this.pairSuccessDeviceBeta, that.pairSuccessDeviceBeta)
                && Objects.equals(this.pairSuccessPlatformName, that.pairSuccessPlatformName)
                && Objects.equals(this.pairSuccessBizName, that.pairSuccessBizName)
                && Arrays.equals(this.pairSuccessClientProps, that.pairSuccessClientProps)
                && Objects.equals(this.pairSuccessEncryptionMetadata, that.pairSuccessEncryptionMetadata);
    }

    @Override
    public int hashCode() {
        var result = Objects.hash(iqId, pairSuccessDeviceJid, pairSuccessDeviceLid,
                pairSuccessDeviceBeta, pairSuccessPlatformName, pairSuccessBizName,
                pairSuccessEncryptionMetadata);
        result = 31 * result + Arrays.hashCode(pairSuccessDeviceIdentity);
        result = 31 * result + Arrays.hashCode(pairSuccessClientProps);
        return result;
    }

    @Override
    public String toString() {
        return "SmaxMdSetRegResponse[iqId=" + iqId
                + ", pairSuccessDeviceIdentity=" + Arrays.toString(pairSuccessDeviceIdentity)
                + ", pairSuccessDeviceJid=" + pairSuccessDeviceJid
                + ", pairSuccessDeviceLid=" + pairSuccessDeviceLid
                + ", pairSuccessDeviceBeta=" + pairSuccessDeviceBeta
                + ", pairSuccessPlatformName=" + pairSuccessPlatformName
                + ", pairSuccessBizName=" + pairSuccessBizName
                + ", pairSuccessClientProps=" + Arrays.toString(pairSuccessClientProps)
                + ", pairSuccessEncryptionMetadata=" + pairSuccessEncryptionMetadata + ']';
    }
}
