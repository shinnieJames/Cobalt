package com.github.auties00.cobalt.message.send.stanza;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Builds the {@code <ctwa_attribution>} stanza child node for
 * Click-to-WhatsApp attribution tracking.
 *
 * <p>When a user opens a chat via a CTWA ad link, WhatsApp records the
 * entry point (link type, partner name, auth status).  The first message
 * sent in that chat includes this attribution data so the server can
 * attribute the conversation to the ad.
 *
 * @apiNote WAWebSendMsgCtwaAttributionNode.getCtwaAttributionNode:
 * checks isCtxLoggingEnabled, retrieves the external entry point from
 * prefs, builds a JSON payload, and wraps in a
 * {@code <ctwa_attribution>} node.
 * WAWebExternalCtxConfig: controls whether logging is enabled and
 * which chats qualify (first message only, or all messages).
 */
public final class CtwaAttributionStanza {

    private final WhatsAppStore store;
    private final ABPropsService abPropsService;

    public CtwaAttributionStanza(WhatsAppStore store, ABPropsService abPropsService) {
        this.store = Objects.requireNonNull(store, "store");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
    }

    /**
     * Builds the {@code <ctwa_attribution>} node for the given chat,
     * if CTWA attribution is applicable.
     *
     * @param chatJid the chat JID being sent to
     * @return the ctwa attribution node, or {@code null} if not applicable
     *
     * @apiNote WAWebSendMsgCtwaAttributionNode.getCtwaAttributionNode:
     * returns {@code null} when logging is disabled, no entry point
     * exists, or the chat doesn't qualify (e.g. not the first message).
     */
    public Node build(Jid chatJid) {
        // WAWebExternalCtxConfig.isCtxLoggingEnabled
        var isCtxLoggingEnabled = abPropsService.getBool(ABProp.CTWA_CONTEXT_LOGGING_ENABLED);
        if (!isCtxLoggingEnabled) {
            return null;
        }

        var chat = store.findChatByJid(chatJid).orElse(null);
        if (chat == null) {
            return null;
        }

        var entryPoint = chat.ctwaEntryPoint().orElse(null);
        if (entryPoint == null) {
            return null;
        }

        // WAWebSendMsgCtwaAttributionNode: build JSON payload
        // {lt: "WEB_" + deepLinkType, s?: 0, p?: partnerName}
        var json = new JSONObject();
        json.put("lt", "WEB_" + entryPoint.deepLinkType());
        if (!entryPoint.authSuccess()) {
            json.put("s", 0);
        }
        entryPoint.partnerName().ifPresent(name -> json.put("p", name));

        var payload = json.toJSONString().getBytes(StandardCharsets.UTF_8);

        return new NodeBuilder()
                .description("ctwa_attribution")
                .content(payload)
                .build();
    }
}
