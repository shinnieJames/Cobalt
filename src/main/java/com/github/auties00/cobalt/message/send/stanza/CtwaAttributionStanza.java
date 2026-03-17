package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;

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
        var isCtxLoggingEnabled = abPropsService.getBool(ABProp.EXTERNAL_CTX_AUTHORISE_WA_CHAT);
        if (!isCtxLoggingEnabled) {
            return null;
        }

        var chat = store.findChatByJid(chatJid).orElse(null);
        if (chat == null) {
            return null;
        }

        // TODO: Chat has no ctwa entry point
        //       We don't want to add non-protobuf entries to chat, so figure out a way to fix this
        return null;
    }
}
