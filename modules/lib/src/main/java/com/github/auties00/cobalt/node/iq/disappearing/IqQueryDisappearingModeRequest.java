package com.github.auties00.cobalt.node.iq.disappearing;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;

/**
 * Outbound {@code <iq xmlns="disappearing_mode" type="get">} stanza that fetches the user's
 * current default disappearing-mode duration.
 *
 * @apiNote
 * Use this to drive WA Web's "default disappearing message duration" Setting and to
 * back the account-sync warmup that propagates the duration into the per-contact
 * disappearing-mode override store via
 * {@code WAWebUpdateDisappearingModeForContact.updateDisappearingModeForContact}. The
 * relay reply is parsed by {@link IqQueryDisappearingModeResponse}.
 *
 * @implNote
 * This implementation emits a bare envelope; WA Web's
 * {@code WAWebQueryDisappearingModeJob.queryDisappearingMode} similarly attaches no
 * payload.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryDisappearingModeJob")
public final class IqQueryDisappearingModeRequest implements IqOperation.Request {
    /**
     * Constructs a new query-disappearing-mode request.
     *
     * @apiNote
     * The request carries no parameters; the relay infers the bound user from the
     * authenticated session.
     */
    public IqQueryDisappearingModeRequest() {
    }

    /**
     * Builds the outbound {@code <iq>} stanza wrapping the bare query envelope.
     *
     * @apiNote
     * The resulting {@link NodeBuilder} is wire-ready except for the IQ {@code id}
     * attribute, which the dispatch layer assigns.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryDisappearingModeJob",
            exports = "queryDisappearingMode",
            adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "disappearing_mode")
                .attribute("to", JidServer.user())
                .attribute("type", "get");
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        return obj != null && obj.getClass() == this.getClass();
    }

    @Override
    public int hashCode() {
        return IqQueryDisappearingModeRequest.class.hashCode();
    }

    @Override
    public String toString() {
        return "IqQueryDisappearingModeRequest[]";
    }
}
