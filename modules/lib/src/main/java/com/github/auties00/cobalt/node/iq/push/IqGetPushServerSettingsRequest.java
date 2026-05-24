package com.github.auties00.cobalt.node.iq.push;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.util.RandomIdUtils;

/**
 * Outbound {@code <iq xmlns="urn:xmpp:whatsapp:push" type="get">} stanza requesting the
 * server-side push key used to validate inbound web-push payloads.
 *
 * @apiNote
 * Used by the browser-push subscription flow: WA Web's
 * {@code WAWebSubscribePushManagerAction} invokes this once it confirms a service-worker
 * push subscription is missing, then forwards the returned {@code webserverkey} (the
 * relay's VAPID public key) to {@code PushManager.subscribe} so the browser will accept the
 * relay's push messages for the lifetime of the subscription.
 */
@WhatsAppWebModule(moduleName = "WAWebGetPushServerSettingsJob")
public final class IqGetPushServerSettingsRequest implements IqOperation.Request {
    /**
     * Constructs a new query-push-server-settings request.
     *
     * @apiNote
     * The request carries no payload; the relay derives the response entirely from the
     * authenticated session.
     */
    public IqGetPushServerSettingsRequest() {
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Produces a {@code <iq xmlns="urn:xmpp:whatsapp:push" type="get">} envelope addressed
     * to {@link JidServer#user()} and wrapping a single bare {@code <settings/>} child.
     *
     * @return a {@link NodeBuilder} carrying the {@code <iq>} envelope and the
     *         {@code <settings/>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGetPushServerSettingsJob",
            exports = "getPushServerSettings", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var settingsNode = new NodeBuilder()
                .description("settings")
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("id", RandomIdUtils.newId())
                .attribute("xmlns", "urn:xmpp:whatsapp:push")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(settingsNode);
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
        return IqGetPushServerSettingsRequest.class.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "IqGetPushServerSettingsRequest[]";
    }
}
