package com.github.auties00.cobalt.node.iq.media;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.util.RandomIdUtils;

/**
 * Outbound {@code <iq xmlns="w:m" type="set">} stanza requesting the current media-server
 * connection configuration (auth token, host routes, retry budgets) the client should use
 * for uploads and downloads.
 *
 * @apiNote
 * Used by the media-upload and media-download pipelines to acquire (and periodically
 * refresh) the bearer token and host routes routed to the media CDN. WA Web invokes it from
 * {@code WAWebQueryMediaConnsBridge} with a soft TTL deadline; the bridge then re-issues the
 * query roughly five seconds before the {@code authTokenExpiry} the relay returns.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryMediaConnsJob")
public final class IqQueryMediaConnsRequest implements IqOperation.Request {
    /**
     * Constructs a new query-media-conn request.
     *
     * @apiNote
     * The request carries no payload; the relay derives the response entirely from the
     * authenticated session.
     */
    public IqQueryMediaConnsRequest() {
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Produces a {@code <iq xmlns="w:m" type="set">} envelope addressed to
     * {@link JidServer#user()} and wrapping a single bare {@code <media_conn/>} child.
     *
     * @return a {@link NodeBuilder} carrying the {@code <iq>} envelope and the
     *         {@code <media_conn/>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob",
            exports = "queryMediaConn", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var mediaConnNode = new NodeBuilder()
                .description("media_conn")
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("id", RandomIdUtils.newId())
                .attribute("xmlns", "w:m")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .content(mediaConnNode);
    }

    /**
     * {@inheritDoc}
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
     */
    @Override
    public int hashCode() {
        return IqQueryMediaConnsRequest.class.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "IqQueryMediaConnsRequest[]";
    }
}
