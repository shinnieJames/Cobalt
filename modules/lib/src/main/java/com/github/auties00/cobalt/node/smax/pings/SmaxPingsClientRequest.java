package com.github.auties00.cobalt.node.smax.pings;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;

/**
 * Models the outbound {@code <iq xmlns="w:p" type="get" to="s.whatsapp.net"/>} keep-alive ping.
 *
 * <p>This request backs the relay-reachability probe dispatched on the keep-alive cadence. The
 * reply is parsed by {@link SmaxPingsClientResponseServerResponse}; a missing or malformed reply
 * lets the stream-stall detector flag the active ping as lost and surface a recovery path. Every
 * ping carries the same envelope, so the type holds no state and the only varying wire attribute is
 * the {@code id} stamped at dispatch time.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutPingsClientRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutPingsClientWellFormedToMixin")
public final class SmaxPingsClientRequest implements SmaxOperation.Request {
    /**
     * Constructs a new keep-alive ping.
     *
     * <p>Takes no parameters because every keep-alive ping carries the same envelope; the only
     * varying attribute is the {@code id} stamped at dispatch time by
     * {@link com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient#sendNode(NodeBuilder)}.
     */
    public SmaxPingsClientRequest() {
    }

    /**
     * Builds the outbound {@code <iq xmlns="w:p" type="get" to="s.whatsapp.net"/>} stanza with no
     * body.
     *
     * <p>The returned builder is left unbuilt so the dispatch path can stamp a fresh {@code id}
     * before flushing the stanza, letting the reply parser match the response back to this request.
     *
     * @implNote
     * This implementation leaves the {@code id} attribute unset on the returned builder; the
     * {@link com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient#sendNode(NodeBuilder)} dispatch path
     * stamps a fresh id on every outbound stanza, so pre-stamping here would be wasted work.
     *
     * @return a {@link NodeBuilder} carrying the empty
     *         {@code <iq xmlns="w:p" type="get" to="s.whatsapp.net"/>} envelope; never {@code null}
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
     * Returns whether the given object is also a {@link SmaxPingsClientRequest}.
     *
     * <p>All instances are interchangeable because the type holds no state, so any two
     * {@link SmaxPingsClientRequest} values compare equal.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when {@code obj} is a {@link SmaxPingsClientRequest}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        return obj != null && obj.getClass() == this.getClass();
    }

    /**
     * Returns a constant hash code shared by every ping.
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
