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
 * The outbound
 * {@code <iq type="error"><error text="not-authorized" code="401"/></iq>}
 * reply emitted when a companion refuses to complete the pair-device
 * handshake.
 *
 * @apiNote
 * Sent when the device-identity HMAC check fails, when the account
 * signature verification fails, or when any other validation step in
 * the pair-success handler rejects the inbound stanza; the not-
 * authorized 401 is the only error variant defined for this RPC. WA
 * Web's {@code handlePairSuccess} invokes
 * {@code makeSetRegResponseError} for both branches and then triggers
 * a session logout via
 * {@code logoutAfterValidationFail}.
 *
 * @implNote
 * This implementation folds WA Web's
 * {@code WASmaxOutMdIQErrorNotAuthorizedMixin} into the builder by
 * inlining the {@code <error text="not-authorized" code="401"/>}
 * shape; the outer envelope is pinned to
 * {@code <iq to="s.whatsapp.net" type="error">} with the original
 * {@code id} echoed back so the relay can pair the error with its
 * pending request.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutMdSetRegResponseError")
@WhatsAppWebModule(moduleName = "WASmaxOutMdIQErrorNotAuthorizedMixin")
public final class SmaxMdSetRegResponseError implements SmaxOperation.Request {
    /**
     * The {@code id} of the inbound IQ being replied to.
     *
     * @apiNote
     * Echoed into the outbound {@code <iq id="..."/>} attribute.
     */
    private final String iqId;

    /**
     * Constructs an error reply.
     *
     * @apiNote
     * Library code typically derives {@code iqId} from the rejected
     * {@link SmaxMdSetRegResponse}.
     *
     * @param iqId the inbound IQ id; never {@code null}
     * @throws NullPointerException if {@code iqId} is {@code null}
     */
    public SmaxMdSetRegResponseError(String iqId) {
        this.iqId = Objects.requireNonNull(iqId, "iqId cannot be null");
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
     * Builds the outbound error stanza.
     *
     * @apiNote
     * Returns the unfinished {@link NodeBuilder} so the dispatch path
     * can stamp the wire-level identifiers before flushing, matching
     * {@link SmaxOperation.Request#toNode()}.
     *
     * @implNote
     * This implementation hardcodes the inner {@code <error>} child
     * to {@code text="not-authorized"} and {@code code=401} because
     * WA Web's {@code mergeIQErrorNotAuthorizedMixin} pins the same
     * two values; no other error variant is defined for this RPC.
     *
     * @return a {@link NodeBuilder} carrying the {@code <iq>} envelope
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutMdSetRegResponseError",
            exports = "makeSetRegResponseError",
            adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var errorNode = new NodeBuilder()
                .description("error")
                .attribute("text", "not-authorized")
                .attribute("code", 401)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("id", iqId)
                .attribute("to", "s.whatsapp.net")
                .attribute("type", "error")
                .content(errorNode);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxMdSetRegResponseError) obj;
        return Objects.equals(this.iqId, that.iqId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(iqId);
    }

    @Override
    public String toString() {
        return "SmaxMdSetRegResponseError[iqId=" + iqId + ']';
    }
}
