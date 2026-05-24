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
 * The outbound {@code <iq type="result"/>} stanza emitted by a
 * companion after consuming a {@link SmaxMdSetToCompanionResponse}.
 *
 * @apiNote
 * Sent by companions once the inbound pair-device refs have been
 * accepted into the rotation timer, so the relay can mark the
 * pair-device IQ delivered. WA Web's {@code WAWebHandlePairDevice}
 * builds this stanza via the
 * {@code makeSetToCompanionResponseClientResponse} thunk returned by
 * {@code receiveSetToCompanionRPC}.
 *
 * @implNote
 * This implementation emits the bare result envelope WA Web sends:
 * {@code <iq id="..." to="..." type="result"/>} with no inner
 * payload. The {@code to} attribute echoes the inbound {@code from}
 * (always the {@code s.whatsapp.net} server domain) rather than
 * pinning the literal in the builder.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutMdSetToCompanionResponseClientResponse")
public final class SmaxMdSetToCompanionAcknowledgement implements SmaxOperation.Request {
    /**
     * The {@code id} of the inbound IQ being acknowledged.
     *
     * @apiNote
     * Echoed into the outbound {@code <iq id="..."/>} attribute so
     * the relay can pair the response with its pending request.
     */
    private final String iqId;

    /**
     * The destination JID, echoed from the inbound IQ's
     * {@code from} attribute.
     *
     * @apiNote
     * Becomes the ack's {@code to} attribute; always the
     * {@code s.whatsapp.net} server domain for valid inbound stanzas.
     */
    private final Jid iqTo;

    /**
     * Constructs an ack from already-resolved component fields.
     *
     * @apiNote
     * Library code typically calls
     * {@link #from(SmaxMdSetToCompanionResponse)} to derive both
     * echoed fields from an already-parsed pair-device projection;
     * this constructor is exposed for unit tests.
     *
     * @param iqId the inbound IQ id; never {@code null}
     * @param iqTo the destination JID; never {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public SmaxMdSetToCompanionAcknowledgement(String iqId, Jid iqTo) {
        this.iqId = Objects.requireNonNull(iqId, "iqId cannot be null");
        this.iqTo = Objects.requireNonNull(iqTo, "iqTo cannot be null");
    }

    /**
     * Derives the ack from an already-parsed
     * {@link SmaxMdSetToCompanionResponse} projection.
     *
     * @apiNote
     * Mirrors WA Web's pattern of obtaining the ack builder thunk
     * directly from the {@code receiveSetToCompanionRPC} return
     * value: the same two echoed fields are folded back into the
     * outbound stanza without the caller having to copy them.
     *
     * @param inbound the parsed inbound pair-device projection
     * @return a new {@link SmaxMdSetToCompanionAcknowledgement}
     * @throws NullPointerException if {@code inbound} is {@code null}
     */
    public static SmaxMdSetToCompanionAcknowledgement from(SmaxMdSetToCompanionResponse inbound) {
        Objects.requireNonNull(inbound, "inbound cannot be null");
        return new SmaxMdSetToCompanionAcknowledgement(inbound.iqId(), inbound.iqFrom());
    }

    /**
     * Returns the IQ id being echoed.
     *
     * @return the id; never {@code null}
     */
    public String iqId() {
        return iqId;
    }

    /**
     * Returns the destination JID.
     *
     * @apiNote
     * Becomes the ack's {@code to} attribute.
     *
     * @return the JID; never {@code null}
     */
    public Jid iqTo() {
        return iqTo;
    }

    /**
     * Builds the outbound ack stanza.
     *
     * @apiNote
     * Returns the unfinished {@link NodeBuilder} so the dispatch
     * path can stamp the wire-level identifiers before flushing,
     * matching {@link SmaxOperation.Request#toNode()}.
     *
     * @implNote
     * This implementation emits an {@code <iq>} with no children,
     * mirroring the bare-result shape produced by WA Web's
     * {@code makeSetToCompanionResponseClientResponse}.
     *
     * @return a {@link NodeBuilder} carrying the {@code <iq>} envelope
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutMdSetToCompanionResponseClientResponse",
            exports = "makeSetToCompanionResponseClientResponse",
            adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        return new NodeBuilder()
                .description("iq")
                .attribute("id", iqId)
                .attribute("to", iqTo)
                .attribute("type", "result");
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxMdSetToCompanionAcknowledgement) obj;
        return Objects.equals(this.iqId, that.iqId)
                && Objects.equals(this.iqTo, that.iqTo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(iqId, iqTo);
    }

    @Override
    public String toString() {
        return "SmaxMdSetToCompanionAcknowledgement[iqId=" + iqId
                + ", iqTo=" + iqTo + ']';
    }
}
