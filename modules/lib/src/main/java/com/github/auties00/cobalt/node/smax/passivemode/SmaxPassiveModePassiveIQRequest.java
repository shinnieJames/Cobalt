package com.github.auties00.cobalt.node.smax.passivemode;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;

/**
 * The outbound {@code <iq xmlns="passive" type="set">} stanza that pins the
 * connection back into passive mode.
 *
 * @apiNote
 * Sent by Cobalt embedders who mirror WA Web's
 * {@code WAWebPassiveModeManager} lifecycle. While in passive mode the
 * relay buffers live messages so that the client can finish a long
 * startup task without dropped or interleaved deliveries; embedders that
 * never expose a passive-mode UI almost never need to call this.
 *
 * @implNote
 * This implementation carries no per-call state, so a single instance can
 * be reused; the canonical caller is {@code WASendPassiveModeProtocol}
 * with the {@code "passive"} argument.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutPassiveModePassiveIQRequest")
public final class SmaxPassiveModePassiveIQRequest implements SmaxOperation.Request {

    /**
     * Constructs an empty request envelope.
     *
     * @apiNote
     * Useful for callers that explicitly hold the relay in passive mode
     * for the duration of a startup task. There is no payload to supply.
     */
    public SmaxPassiveModePassiveIQRequest() {
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation hard-codes {@code xmlns="passive"},
     * {@code type="set"}, and {@code to=s.whatsapp.net} per the
     * {@code WASmaxOutPassiveModePassiveIQRequest.makePassiveIQRequest}
     * fixture, then nests a single empty {@code <passive/>} child as the
     * payload discriminator.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutPassiveModePassiveIQRequest",
            exports = "makePassiveIQRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var passiveNode = new NodeBuilder()
                .description("passive")
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "passive")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .content(passiveNode);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation treats every instance as equal to every other
     * because the type carries no per-instance state.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        return obj != null && obj.getClass() == this.getClass();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns a class-level hash to stay consistent
     * with {@link #equals(Object)}.
     */
    @Override
    public int hashCode() {
        return SmaxPassiveModePassiveIQRequest.class.hashCode();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation mirrors the record-like rendering used across
     * the {@code Smax*} stanza family.
     */
    @Override
    public String toString() {
        return "SmaxPassiveModePassiveIQRequest[]";
    }
}
