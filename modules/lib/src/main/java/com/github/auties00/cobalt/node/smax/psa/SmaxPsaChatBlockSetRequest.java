package com.github.auties00.cobalt.node.smax.psa;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <iq type="set"><blocking action="..."/></iq>} request
 * that mutes or unmutes the PSA broadcast channel for the current account.
 *
 * @apiNote
 * Surfaces {@code WAWebBlockUserJob.blockUnblockPSAUser}, called from the
 * "Mute PSA messages" toggle on the chat settings surface. The
 * {@link #blockingAction()} string matches one of the
 * {@link SmaxPsaChatBlockGetBlockingStatus} wire literals
 * ({@code "blocked"} or {@code "unblocked"}) although the wire layer
 * accepts any non-empty string and lets the relay reject unknown literals
 * server-side.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutPsaChatBlockSetRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutPsaBaseIQSetRequestMixin")
public final class SmaxPsaChatBlockSetRequest implements SmaxOperation.Request {
    /**
     * The free-form action string carried by the {@code action} attribute.
     *
     * @apiNote
     * The WA Web layer wraps this in {@code WAWap.CUSTOM_STRING}; typed
     * callers pass {@code "blocked"} or {@code "unblocked"} to flip the
     * PSA-mute flag.
     */
    private final String blockingAction;

    /**
     * Constructs a request.
     *
     * @param blockingAction the action string; never {@code null}
     * @throws NullPointerException if {@code blockingAction} is {@code null}
     */
    public SmaxPsaChatBlockSetRequest(String blockingAction) {
        this.blockingAction = Objects.requireNonNull(blockingAction, "blockingAction cannot be null");
    }

    /**
     * Returns the action string.
     *
     * @return the action string; never {@code null}
     */
    public String blockingAction() {
        return blockingAction;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation emits the canonical
     * {@code <iq xmlns="w:comms:chat" type="set" to="s.whatsapp.net">}
     * envelope around a {@code <blocking action="..."/>} child, mirroring
     * {@code makeChatBlockSetRequest} + {@code mergeBaseIQSetRequestMixin}.
     *
     * @return a {@link NodeBuilder} carrying the
     *         {@code <iq><blocking action="..."/></iq>} stanza
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutPsaChatBlockSetRequest",
            exports = "makeChatBlockSetRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var blockingNode = new NodeBuilder()
                .description("blocking")
                .attribute("action", blockingAction)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:comms:chat")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .content(blockingNode);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxPsaChatBlockSetRequest) obj;
        return Objects.equals(this.blockingAction, that.blockingAction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockingAction);
    }

    @Override
    public String toString() {
        return "SmaxPsaChatBlockSetRequest[blockingAction=" + blockingAction + ']';
    }
}
