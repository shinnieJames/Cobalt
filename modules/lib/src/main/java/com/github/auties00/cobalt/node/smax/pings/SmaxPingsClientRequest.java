package com.github.auties00.cobalt.node.smax.pings;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;

/**
 * The outbound {@code <iq xmlns="w:p" type="get" to="s.whatsapp.net"/>}
 * keep-alive ping.
 *
 * @apiNote
 * Backs the relay-reachability probe that {@code WAComms} emits on its
 * keep-alive cadence. The reply is parsed by
 * {@link SmaxPingsClientResponseServerResponse}; a missing or malformed
 * reply causes WA Web's {@code WAComms.sendPing} to flag the active
 * ping as lost and surface a stream-stall recovery path. Cobalt's
 * keep-alive loop dispatches this same request through its socket
 * pipeline.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutPingsClientRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutPingsClientWellFormedToMixin")
public final class SmaxPingsClientRequest implements SmaxOperation.Request {
    /**
     * Constructs a new keep-alive ping.
     *
     * @apiNote
     * Takes no parameters: every keep-alive ping carries the same
     * envelope, and the only varying attribute is the {@code id}
     * stamped at dispatch time by
     * {@link com.github.auties00.cobalt.client.WhatsAppClient#sendNode(NodeBuilder)}
     * to match WA Web's {@code WAWap.generateId} contract.
     */
    public SmaxPingsClientRequest() {
    }

    /**
     * Builds the outbound IQ stanza ready for dispatch.
     *
     * @apiNote
     * Produces {@code <iq xmlns="w:p" type="get" to="s.whatsapp.net"/>}
     * with no body; the dispatch path stamps a fresh {@code id} before
     * flushing the stanza so the reply parser can match it back to
     * this request.
     *
     * @implNote
     * This implementation leaves the {@code id} attribute unset on the
     * returned builder rather than calling {@code WAWap.generateId}
     * inline; the
     * {@link com.github.auties00.cobalt.client.WhatsAppClient#sendNode(NodeBuilder)}
     * dispatch path stamps a fresh id on every outbound stanza so
     * pre-stamping would be wasted work.
     *
     * @return a {@link NodeBuilder} carrying the empty
     *         {@code <iq xmlns="w:p" type="get" to="s.whatsapp.net"/>}
     *         envelope; never {@code null}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutPingsClientRequest",
            exports = "makeClientRequest", adaptation = WhatsAppAdaptation.ADAPTED)
    public NodeBuilder toNode() {
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:p")
                .attribute("to", JidServer.user())
                .attribute("type", "get");
    }

    /**
     * Returns whether the given object is also a
     * {@link SmaxPingsClientRequest}.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when {@code obj} is a
     *         {@link SmaxPingsClientRequest}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        return obj != null && obj.getClass() == this.getClass();
    }

    /**
     * Returns a constant hash code; every ping carries the same
     * envelope.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return SmaxPingsClientRequest.class.hashCode();
    }

    /**
     * Returns a debug-friendly textual representation of this request.
     *
     * @return the textual representation
     */
    @Override
    public String toString() {
        return "SmaxPingsClientRequest[]";
    }
}
