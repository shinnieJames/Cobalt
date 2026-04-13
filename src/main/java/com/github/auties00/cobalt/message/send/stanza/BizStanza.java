package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.model.business.BusinessVerifiedName;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.interactive.InteractiveMessage;
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
     * name or the privacy mode fields are not fully populated, and no
     * native flow name applies.
     *
     * <p>Delegates to
     * {@link #build(Jid, String, boolean)} with {@code null}
     * native flow name and {@code false} interactive flag.
     *
     * @param chatJid the recipient chat JID
     * @return the biz node, or {@code null} if not applicable
     *
     * @implNote WAWebSendMsgCreateFanoutStanza: includes
     * {@code <biz host_storage="..." actual_actors="..." privacy_mode_ts="..."
     * native_flow_name="..."/>} when the contact has a verified business
     * name with privacy mode.
     */
    public Node build(Jid chatJid) {
        return build(chatJid, null, false);
    }

    /**
     * Builds the {@code <biz>} node for the given chat recipient,
     * including the native flow name attribute and interactive child
     * when applicable.
     *
     * <p>The resolution follows this priority:
     * <ol>
     *   <li>If the contact has a verified business name with privacy
     *       mode, builds a {@code <biz>} node with {@code host_storage},
     *       {@code actual_actors}, {@code privacy_mode_ts}, and
     *       {@code native_flow_name} attributes.</li>
     *   <li>Otherwise, if {@code nativeFlowName} is non-null and
     *       {@code isNativeFlowInteractive} is {@code true}, builds a
     *       {@code <biz>} node with an {@code <interactive>} child
     *       containing a {@code <native_flow>} element.</li>
     *   <li>Otherwise, if {@code nativeFlowName} is non-null, builds a
     *       simple {@code <biz>} node with just the
     *       {@code native_flow_name} attribute.</li>
     *   <li>Otherwise, returns {@code null}.</li>
     * </ol>
     *
     * @param chatJid                  the recipient chat JID
     * @param nativeFlowName           the native flow name from the
     *                                 protobuf, or {@code null}
     * @param isNativeFlowInteractive  whether this is a native flow
     *                                 interactive message
     * @return the biz node, or {@code null} if not applicable
     *
     * @implNote WAWebSendMsgCreateFanoutStanza.createFanoutMsgStanza:
     * resolves the biz node from {@code contact.privacyMode} and
     * {@code getBizNativeFlowName(proto)}. When privacy mode exists,
     * includes {@code native_flow_name}. When privacy mode is absent
     * but native flow name exists, builds alternative biz nodes
     * (interactive or simple).
     */
    public Node build(Jid chatJid, String nativeFlowName, boolean isNativeFlowInteractive) {
        // WAWebSendMsgCreateFanoutStanza: check contact.privacyMode first
        var verifiedName = store.findVerifiedBusinessName(chatJid)
                .orElse(null);
        if (verifiedName != null && verifiedName.hasPrivacyMode()) {
            var hostStorage = verifiedName.hostStorage()
                    .map(BusinessVerifiedName.HostStorageType::index)
                    .orElse(null);
            var actualActors = verifiedName.actualActors()
                    .map(BusinessVerifiedName.ActualActorsType::index)
                    .orElse(null);
            var privacyModeTs = verifiedName.privacyModeTimestamp()
                    .map(Instant::getEpochSecond)
                    .orElse(null);

            // WAWebSendMsgCreateFanoutStanza: privacy mode biz node
            // includes native_flow_name: MAYBE_CUSTOM_STRING(H)
            return new NodeBuilder()
                    .description("biz")
                    .attribute("host_storage", hostStorage)
                    .attribute("actual_actors", actualActors)
                    .attribute("privacy_mode_ts", privacyModeTs)
                    .attribute("native_flow_name", nativeFlowName)
                    .build();
        }

        // WAWebSendMsgCreateFanoutStanza: z == null && H != null && G === true
        if (nativeFlowName != null && isNativeFlowInteractive) {
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

        // WAWebSendMsgCreateFanoutStanza: z == null && H != null
        if (nativeFlowName != null) {
            return new NodeBuilder()
                    .description("biz")
                    .attribute("native_flow_name", nativeFlowName)
                    .build();
        }

        return null;
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

        var imc = im.content();
        if(imc.isEmpty() || !(imc.get() instanceof InteractiveMessage.NativeFlowMessage nativeFlow)) {
            return null;
        }

        var buttons = nativeFlow.buttons();
        if (buttons.isEmpty()) {
            return null;
        }

        var nativeFlowName = buttons.getFirst().name();
        if (nativeFlowName.isEmpty() || !"payment_info".equals(nativeFlowName.get())) {
            return null;
        }

        var nativeFlowNode = new NodeBuilder()
                .description("native_flow")
                .attribute("name", nativeFlowName.get())
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
