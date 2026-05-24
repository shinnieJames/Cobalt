package com.github.auties00.cobalt.node.smax.passivemode;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;

/**
 * The outbound {@code <iq xmlns="passive" type="set">} stanza that flips the
 * connection out of passive mode.
 *
 * @apiNote
 * Sent by Cobalt embedders who mirror WA Web's
 * {@code WAWebPassiveModeManager} lifecycle. The relay holds back live
 * message delivery while the socket is in passive mode so that the
 * connecting client can drain offline buffers and run startup tasks
 * without competing with live traffic; emitting this stanza signals that
 * the client is ready to receive new messages.
 *
 * @implNote
 * This implementation carries no per-call state, so a single instance can
 * be reused; the canonical site is {@code WASendPassiveModeProtocol} which
 * calls {@link SmaxPassiveModeActiveIQRequest}
 * after the {@code WAWebPassiveModeManager} executes its registered
 * passive tasks.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutPassiveModeActiveIQRequest")
public final class SmaxPassiveModeActiveIQRequest implements SmaxOperation.Request {

    /**
     * Constructs an empty request envelope.
     *
     * @apiNote
     * Useful for callers that want to flip the socket out of passive mode
     * exactly once per connection lifecycle. There is no payload to
     * supply.
     */
    public SmaxPassiveModeActiveIQRequest() {
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation hard-codes {@code xmlns="passive"},
     * {@code type="set"}, and {@code to=s.whatsapp.net} per the
     * {@code WASmaxOutPassiveModeActiveIQRequest.makeActiveIQRequest}
     * fixture, then nests a single empty {@code <active/>} child as the
     * payload discriminator.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutPassiveModeActiveIQRequest",
            exports = "makeActiveIQRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var activeNode = new NodeBuilder()
                .description("active")
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "passive")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .content(activeNode);
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
        return SmaxPassiveModeActiveIQRequest.class.hashCode();
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
        return "SmaxPassiveModeActiveIQRequest[]";
    }
}
