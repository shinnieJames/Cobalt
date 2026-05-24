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
 * The outbound {@code <iq type="get"><query><blocking_status/></query></iq>}
 * request that asks the relay whether the current account has muted the
 * PSA broadcast channel.
 *
 * @apiNote
 * Surfaces {@code WAWebQueryBlockListJob.getBlockingStatusForPSAUser}, which
 * the post-login blocklist refresh consults to populate the local "PSA
 * muted" preference. The reply
 * ({@link SmaxPsaChatBlockGetResponse}) is the typed projection of the
 * server response.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutPsaChatBlockGetRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutPsaBaseIQGetRequestMixin")
public final class SmaxPsaChatBlockGetRequest implements SmaxOperation.Request {
    /**
     * Constructs the empty request.
     */
    public SmaxPsaChatBlockGetRequest() {
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation emits the canonical
     * {@code <iq xmlns="w:comms:chat" type="get" to="s.whatsapp.net">}
     * envelope around a {@code <query><blocking_status/></query>} payload,
     * mirroring the composition in
     * {@code makeChatBlockGetRequest} + {@code mergeBaseIQGetRequestMixin}.
     *
     * @return a {@link NodeBuilder} carrying the
     *         {@code <iq><query><blocking_status/></query></iq>} stanza
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutPsaChatBlockGetRequest",
            exports = "makeChatBlockGetRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var blockingStatusNode = new NodeBuilder()
                .description("blocking_status")
                .build();
        var queryNode = new NodeBuilder()
                .description("query")
                .content(blockingStatusNode)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:comms:chat")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(queryNode);
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
        return SmaxPsaChatBlockGetRequest.class.hashCode();
    }

    @Override
    public String toString() {
        return "SmaxPsaChatBlockGetRequest[]";
    }
}
