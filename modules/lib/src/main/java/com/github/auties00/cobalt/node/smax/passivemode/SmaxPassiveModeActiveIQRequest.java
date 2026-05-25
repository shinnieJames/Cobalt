package com.github.auties00.cobalt.node.smax.passivemode;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;

/**
 * Models the outbound {@code <iq xmlns="passive" type="set">} stanza that flips the connection out of passive mode.
 *
 * <p>While the socket is in passive mode the relay holds back live message delivery so that the connecting client can
 * drain offline buffers and run startup tasks without competing with live traffic. Sending this stanza signals that the
 * client is ready to receive new messages, and the relay resumes streaming live deliveries to the socket. The request
 * carries no payload beyond a single empty {@code <active/>} child that discriminates the active transition from its
 * {@link SmaxPassiveModePassiveIQRequest passive} counterpart.
 *
 * @implNote
 * This implementation carries no per-call state, so a single instance can be reused across the connection lifecycle.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutPassiveModeActiveIQRequest")
public final class SmaxPassiveModeActiveIQRequest implements SmaxOperation.Request {

    /**
     * Constructs an empty request envelope.
     *
     * <p>There is no payload to supply; the stanza shape is fixed and rendered entirely by {@link #toNode()}.
     */
    public SmaxPassiveModeActiveIQRequest() {
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation hard-codes {@code xmlns="passive"}, {@code type="set"}, and {@code to=s.whatsapp.net}
     * resolved via {@link JidServer#user()}, then nests a single empty {@code <active/>} child as the payload
     * discriminator.
     * @return the outbound stanza builder; never {@code null}
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
     * Indicates whether the given object is equal to this request.
     *
     * <p>Any instance of this type is equal to any other because the request carries no per-instance state.
     *
     * @param obj the object to compare against
     * @return {@code true} when {@code obj} is a {@code SmaxPassiveModeActiveIQRequest}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        return obj != null && obj.getClass() == this.getClass();
    }

    /**
     * Returns a hash code for this request.
     *
     * <p>The hash is class-level so that it stays consistent with {@link #equals(Object)}, which treats every instance
     * as equal.
     *
     * @return the class-level hash code
     */
    @Override
    public int hashCode() {
        return SmaxPassiveModeActiveIQRequest.class.hashCode();
    }

    /**
     * Returns the string representation of this request.
     *
     * <p>The rendering mirrors the record-like form used across the {@code Smax*} stanza family.
     *
     * @return the string representation
     */
    @Override
    public String toString() {
        return "SmaxPassiveModeActiveIQRequest[]";
    }
}
