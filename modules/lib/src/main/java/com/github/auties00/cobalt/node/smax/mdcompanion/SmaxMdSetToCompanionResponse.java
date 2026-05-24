package com.github.auties00.cobalt.node.smax.mdcompanion;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The typed projection of the inbound
 * {@code <iq xmlns="md" type="set"><pair-device><ref/>...<ref/></pair-device></iq>}
 * stanza pushed to a companion at the start of a QR-code pair-device
 * handshake.
 *
 * @apiNote
 * Companions consume this to receive the six rotating
 * {@code <ref/>} byte payloads that WA Web's
 * {@code WAWebHandlePairDevice} feeds into a 60-second
 * {@code Conn.ref} rotation timer (then a 20-second tail once five
 * refs have been consumed); the first ref is rendered as a QR code on
 * the companion's pairing screen, and each rotation marks the
 * companion's socket as
 * {@code SOCKET_STATE.UNPAIRED}.
 *
 * @implNote
 * This implementation enforces the same shape as WA Web's
 * {@code parseSetToCompanionRequest}: the outer tag is {@code iq},
 * the {@code xmlns} is {@code md}, the {@code type} is {@code set},
 * the {@code from} is the {@code s.whatsapp.net} domain, the
 * {@code <pair-device/>} child is present, and the number of
 * {@code <ref/>} children whose {@code contentBytes} resolves is
 * exactly six (upstream uses
 * {@code mapChildrenWithTag("ref", 6, 6, ...)}). Empty-content
 * children are dropped before the size check, matching the JS
 * filter behaviour.
 */
@WhatsAppWebModule(moduleName = "WASmaxInMdSetToCompanionRequest")
@WhatsAppWebModule(moduleName = "WASmaxInMdBaseIQSetRequestMixin")
public final class SmaxMdSetToCompanionResponse implements SmaxOperation.Response {
    /**
     * The {@code id} attribute of the inbound IQ stanza.
     *
     * @apiNote
     * Echoed back into the
     * {@link SmaxMdSetToCompanionAcknowledgement} stanza's {@code id}
     * attribute by
     * {@link SmaxMdSetToCompanionAcknowledgement#from(SmaxMdSetToCompanionResponse)}.
     */
    private final String iqId;

    /**
     * The {@code from} JID of the inbound IQ stanza, always the
     * {@code s.whatsapp.net} server domain.
     *
     * @apiNote
     * Echoed back into the ack's {@code to} attribute by
     * {@link SmaxMdSetToCompanionAcknowledgement#from(SmaxMdSetToCompanionResponse)}.
     */
    private final Jid iqFrom;

    /**
     * The six rotating pair-device reference byte payloads carried in
     * the inner {@code <pair-device><ref/>...<ref/></pair-device>}.
     *
     * @apiNote
     * Driven through WA Web's {@code ShiftTimer} so each ref is
     * surfaced as the active QR-code for 60 seconds (20 seconds once
     * five have rotated). The companion serialises each ref as a
     * length-prefixed string before rendering.
     */
    private final List<byte[]> pairDeviceRefs;

    /**
     * Constructs the typed projection from already-validated component
     * fields.
     *
     * @apiNote
     * Library code does not normally call this constructor; it is the
     * target of {@link #of(Node)} after parsing has succeeded. Public
     * visibility is preserved so unit tests can construct fixtures.
     *
     * @param iqId           the IQ id; never {@code null}
     * @param iqFrom         the IQ sender JID; never {@code null}
     * @param pairDeviceRefs the six pair-device ref payloads; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public SmaxMdSetToCompanionResponse(String iqId, Jid iqFrom, List<byte[]> pairDeviceRefs) {
        this.iqId = Objects.requireNonNull(iqId, "iqId cannot be null");
        this.iqFrom = Objects.requireNonNull(iqFrom, "iqFrom cannot be null");
        this.pairDeviceRefs = List.copyOf(Objects.requireNonNull(pairDeviceRefs, "pairDeviceRefs cannot be null"));
    }

    /**
     * Returns the IQ id.
     *
     * @apiNote
     * Echoed back as the matching ack's {@code id} attribute.
     *
     * @return the id; never {@code null}
     */
    public String iqId() {
        return iqId;
    }

    /**
     * Returns the IQ sender JID.
     *
     * @apiNote
     * Always the {@code s.whatsapp.net} server domain; validated by
     * {@link #of(Node)} during parsing. Echoed back as the ack's
     * {@code to} attribute.
     *
     * @return the JID; never {@code null}
     */
    public Jid iqFrom() {
        return iqFrom;
    }

    /**
     * Returns the list of pair-device reference byte payloads.
     *
     * @apiNote
     * Always contains exactly six entries when the projection was
     * parsed by {@link #of(Node)}; embedders that construct fixtures
     * directly may supply a different number for testing.
     *
     * @return an unmodifiable list of byte arrays; never {@code null}
     */
    public List<byte[]> pairDeviceRefs() {
        return pairDeviceRefs;
    }

    /**
     * Parses an inbound {@code <iq><pair-device/></iq>} stanza into
     * the typed projection.
     *
     * @apiNote
     * Companions call this on every inbound IQ-set whose first child
     * is {@code <pair-device/>}; the returned {@link Optional} is
     * empty when the stanza shape diverges from the documented
     * schema, mirroring WA Web's {@code SmaxParsingFailure} swallowing
     * in {@code WAWebHandlePairDevice}.
     *
     * @implNote
     * This implementation enforces tag equality on {@code iq},
     * attribute literals on {@code xmlns="md"} and {@code type="set"},
     * domain-JID literal on {@code from}, presence of an {@code id}
     * attribute, presence of a {@code <pair-device/>} child, and an
     * exact-count of six {@code <ref/>} children with non-empty
     * content bytes (upstream {@code mapChildrenWithTag} with min=6
     * and max=6). Empty-content children are dropped before the size
     * check.
     *
     * @param node the inbound IQ stanza
     * @return an {@link Optional} carrying the projection, or empty
     *         when the stanza shape diverges from the schema
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInMdSetToCompanionRequest",
            exports = "parseSetToCompanionRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxMdSetToCompanionResponse> of(Node node) {
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
        var pairDevice = node.getChild("pair-device").orElse(null);
        if (pairDevice == null) {
            return Optional.empty();
        }
        var refs = pairDevice.streamChildren("ref")
                .map(ref -> ref.toContentBytes().orElse(null))
                .filter(Objects::nonNull)
                .toList();
        if (refs.size() != 6) {
            return Optional.empty();
        }
        return Optional.of(new SmaxMdSetToCompanionResponse(id, from, refs));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxMdSetToCompanionResponse) obj;
        if (!Objects.equals(this.iqId, that.iqId) || !Objects.equals(this.iqFrom, that.iqFrom)) {
            return false;
        }
        if (this.pairDeviceRefs.size() != that.pairDeviceRefs.size()) {
            return false;
        }
        for (var i = 0; i < this.pairDeviceRefs.size(); i++) {
            if (!Arrays.equals(this.pairDeviceRefs.get(i), that.pairDeviceRefs.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        var result = Objects.hash(iqId, iqFrom);
        for (var ref : pairDeviceRefs) {
            result = 31 * result + Arrays.hashCode(ref);
        }
        return result;
    }

    @Override
    public String toString() {
        var refsStr = new StringBuilder("[");
        for (var i = 0; i < pairDeviceRefs.size(); i++) {
            if (i > 0) {
                refsStr.append(", ");
            }
            refsStr.append(Arrays.toString(pairDeviceRefs.get(i)));
        }
        refsStr.append(']');
        return "SmaxMdSetToCompanionResponse[iqId=" + iqId
                + ", iqFrom=" + iqFrom
                + ", pairDeviceRefs=" + refsStr + ']';
    }
}
