package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.model.businesss.VerifiedBusinessName;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.button.InteractiveMessage;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.time.Instant;
import java.util.Objects;

/**
 * Builds the {@code <biz>} stanza child node that carries verified
 * business account privacy mode metadata.
 *
 * <p>The node is included only when the recipient has a verified
 * business name with all three privacy mode fields populated
 * ({@code host_storage}, {@code actual_actors}, {@code privacy_mode_ts}).
 *
 * <p>The resulting stanza structure is:
 * <pre>{@code
 * <biz host_storage="1|2" actual_actors="1|2|3" privacy_mode_ts="epoch"/>
 * }</pre>
 *
 * @apiNote WAWebSendMsgCreateFanoutStanza.createFanoutMsgStanza: builds
 * the biz node from {@code contact.privacyMode} when the contact has a
 * verified business name.
 * WAWebApiVerifiedBusinessName.getPrivacyMode: queries the verified
 * business name record for the recipient JID.
 * @see ChatFanoutStanza
 */
public final class BizStanza {
    private final WhatsAppStore store;

    public BizStanza(WhatsAppStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Builds the {@code <biz>} node for the given chat recipient.
     *
     * <p>Returns {@code null} if the recipient has no verified business
     * name or the privacy mode fields are not fully populated.
     *
     * @param chatJid the recipient chat JID
     * @return the biz node, or {@code null} if not applicable
     *
     * @apiNote WAWebSendMsgCreateFanoutStanza: includes
     * {@code <biz host_storage="..." actual_actors="..." privacy_mode_ts="..."/>}
     * when the contact has a verified business name with privacy mode.
     */
    public Node build(Jid chatJid) {
        var verifiedName = store.findVerifiedBusinessName(chatJid)
                .orElse(null);
        if (verifiedName == null || !verifiedName.hasPrivacyMode()) {
            return null;
        }

        var hostStorage = verifiedName.hostStorage()
                .map(VerifiedBusinessName.HostStorageType::index)
                .orElse(null);
        var actualActors = verifiedName.actualActors()
                .map(VerifiedBusinessName.ActualActorsType::index)
                .orElse(null);
        var privacyModeTs = verifiedName.privacyModeTimestamp()
                .map(Instant::getEpochSecond)
                .orElse(null);

        return new NodeBuilder()
                .description("biz")
                .attribute("host_storage", hostStorage)
                .attribute("actual_actors", actualActors)
                .attribute("privacy_mode_ts", privacyModeTs)
                .build();
    }

    /**
     * Builds the {@code <biz>} node for group messages containing
     * payment native flow interactive messages.
     *
     * <p>Returns {@code null} for all other message types.
     *
     * @param container the message container
     * @return the biz node, or {@code null}
     *
     * @apiNote WAWebSendGroupSkmsgJob: builds
     * {@code <biz><interactive v="1" type="native_flow"><native_flow name="..."/></interactive></biz>}
     * when {@code nativeFlowName === PAYMENT_INFO && nativeFlowInteractiveMsg}.
     * @see GroupSkmsgFanoutStanza
     */
    public Node buildGroup(MessageContainer container) {
        if (!(container.content() instanceof InteractiveMessage im)) {
            return null;
        }

        var nativeFlow = im.contentNativeFlow().orElse(null);
        if (nativeFlow == null) {
            return null;
        }

        var buttons = nativeFlow.buttons();
        if (buttons.isEmpty()) {
            return null;
        }

        var nativeFlowName = buttons.getFirst().name();
        if (!"payment_info".equals(nativeFlowName)) {
            return null;
        }

        var nativeFlowNode = new NodeBuilder()
                .description("native_flow")
                .attribute("name", nativeFlowName)
                .build();
        var interactiveNode = new NodeBuilder()
                .description("interactive")
                .attribute("v", "1")
                .attribute("type", "native_flow")
                .content(nativeFlowNode)
                .build();
        return new NodeBuilder()
                .description("biz")
                .content(interactiveNode)
                .build();
    }
}
