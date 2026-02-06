package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.button.InteractiveMessage;
import com.github.auties00.cobalt.model.message.common.MessageContainer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.util.Objects;

/**
 * Builds biz nodes for message stanzas.
 * <p>
 * Biz nodes contain business privacy mode settings and native flow information
 * for interactive messages.
 *
 * @apiNote WAWebSendMsgCreateFanoutStanza: generates biz node with privacy mode info.
 */
public final class BizNode {
    private final WhatsAppStore store;

    public BizNode(WhatsAppStore store) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
    }

    /**
     * Builds a biz node for a 1:1 chat message.
     *
     * @param chatJid        the chat JID
     * @param message        the message container
     * @param nativeFlowName the native flow name, or null
     * @return the biz node, or null if not applicable
     *
     * @apiNote WAWebSendMsgCreateFanoutStanza.createFanoutMsgStanza
     */
    public Node build(Jid chatJid, MessageContainer message, String nativeFlowName) {
        Objects.requireNonNull(chatJid, "chatJid cannot be null");
        Objects.requireNonNull(message, "message cannot be null");

        var privacyMode = getPrivacyMode(chatJid);
        if (privacyMode != null) {
            return buildWithPrivacyMode(privacyMode, nativeFlowName);
        }

        if (nativeFlowName != null && isNativeFlowInteractiveMessage(message)) {
            return buildInteractive(nativeFlowName);
        }

        if (nativeFlowName != null) {
            return buildNativeFlowOnly(nativeFlowName);
        }

        return null;
    }

    /**
     * Builds a biz node for group messages.
     *
     * @param message        the message container
     * @param nativeFlowName the native flow name
     * @return the biz node, or null if not applicable
     *
     * @apiNote WAWebSendGroupSkmsgJob (function b)
     */
    public Node buildForGroup(MessageContainer message, String nativeFlowName) {
        Objects.requireNonNull(message, "message cannot be null");

        if (nativeFlowName == null) {
            return null;
        }

        if (!"payment_info".equalsIgnoreCase(nativeFlowName)) {
            return null;
        }

        if (!isNativeFlowInteractiveMessage(message)) {
            return null;
        }

        return buildInteractive(nativeFlowName);
    }

    private PrivacyMode getPrivacyMode(Jid chatJid) {
        var contact = store.findContactByJid(chatJid).orElse(null);
        if (contact == null) {
            return null;
        }

        var hostStorage = contact.privacyModeHostStorage().orElse(null);
        var actualActors = contact.privacyModeActualActors().orElse(null);
        var privacyModeTs = contact.privacyModeTs().orElse(null);

        if (hostStorage == null || actualActors == null || privacyModeTs == null) {
            return null;
        }

        return new PrivacyMode(hostStorage, actualActors, privacyModeTs);
    }

    private Node buildWithPrivacyMode(PrivacyMode privacyMode, String nativeFlowName) {
        return new NodeBuilder()
                .description("biz")
                .attribute("host_storage", String.valueOf(privacyMode.hostStorage()))
                .attribute("actual_actors", String.valueOf(privacyMode.actualActors()))
                .attribute("privacy_mode_ts", String.valueOf(privacyMode.privacyModeTs()))
                .attribute("native_flow_name", nativeFlowName)
                .build();
    }

    private Node buildInteractive(String nativeFlowName) {
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

    private Node buildNativeFlowOnly(String nativeFlowName) {
        return new NodeBuilder()
                .description("biz")
                .attribute("native_flow_name", nativeFlowName)
                .build();
    }

    private boolean isNativeFlowInteractiveMessage(MessageContainer message) {
        return message.content() instanceof InteractiveMessage interactiveMessage
               && interactiveMessage.contentNativeFlow().isPresent();
    }

    private record PrivacyMode(int hostStorage, int actualActors, long privacyModeTs) {}
}
