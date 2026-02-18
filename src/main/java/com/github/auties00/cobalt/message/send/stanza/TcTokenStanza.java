package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.util.Objects;

/**
 * Builds the {@code <tctoken>} stanza child node with the trust
 * contact token for the recipient.
 *
 * <p>The token is included only when the
 * {@code privacy_token_sending_on_all_1_on_1_messages} AB prop is
 * enabled and the chat has a non-expired TC token.
 *
 * @apiNote WAWebSendMsgCreateFanoutStanza: checks
 * privacy_token_sending_on_all_1_on_1_messages AB prop, then includes
 * {@code <tctoken>} with the chat's tcToken bytes if the token is not
 * expired.
 * @see ChatFanoutStanza
 */
public final class TcTokenStanza {
    private final WhatsAppStore store;
    private final ABPropsService abPropsService;

    public TcTokenStanza(WhatsAppStore store, ABPropsService abPropsService) {
        this.store = Objects.requireNonNull(store, "store");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
    }

    /**
     * Builds the {@code <tctoken>} node for the given chat recipient.
     *
     * <p>Returns {@code null} if the AB prop is disabled, the chat is
     * not found, or the chat has no TC token.
     *
     * @param chatJid the recipient chat JID
     * @return the tctoken node, or {@code null} if not applicable
     *
     * @apiNote WAWebSendMsgCreateFanoutStanza.genTrustContactToken:
     * checks AB prop, retrieves tcToken from chat, verifies not expired.
     */
    public Node build(Jid chatJid) {
        var tcTokenEnabled = abPropsService.getBool(ABProp.PRIVACY_TOKEN_SENDING_ON_ALL_1_ON_1_MESSAGES);
        if (!tcTokenEnabled) {
            return null;
        }

        var chat = store.findChatByJid(chatJid).orElse(null);
        if (chat == null) {
            return null;
        }

        var tcToken = chat.tcToken().orElse(null);
        if (tcToken == null) {
            return null;
        }

        return new NodeBuilder()
                .description("tctoken")
                .content(tcToken)
                .build();
    }
}
