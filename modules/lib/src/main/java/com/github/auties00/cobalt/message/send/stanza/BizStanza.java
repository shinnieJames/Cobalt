package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
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
 * Builds the optional {@code <biz>} child node carried inside an outgoing
 * {@code <message>} stanza for hosted-business and native-flow sends.
 *
 * @apiNote
 * Embedders do not call this directly. {@link ChatFanoutStanza} composes a
 * {@code <biz>} node when the recipient is a verified business with privacy
 * mode set (the WhatsApp Business hosted-storage opt-in) or when the message
 * carries a native-flow name (interactive payment buttons, review-and-pay
 * forms, etc.); the server uses the resulting attributes to route the
 * stanza to the correct business backend.
 */
@WhatsAppWebModule(moduleName = "WAWebSendMsgCreateFanoutStanza")
public final class BizStanza {
    /**
     * The store consulted for the recipient's
     * {@link BusinessVerifiedName} privacy-mode record.
     */
    private final WhatsAppStore store;

    /**
     * Constructs a builder backed by the given store.
     *
     * @apiNote
     * Constructed once per client; the instance is stateless and may be
     * reused across sends.
     *
     * @param store the {@link WhatsAppStore} used to resolve the recipient's
     *              verified business name
     * @throws NullPointerException if {@code store} is {@code null}
     */
    public BizStanza(WhatsAppStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Builds the {@code <biz>} node for a recipient with no native-flow
     * context.
     *
     * @apiNote
     * Convenience overload that delegates to
     * {@link #build(Jid, String, boolean)} with no native-flow name; returns
     * {@code null} when the recipient has no hosted-business privacy mode
     * recorded.
     *
     * @param chatJid the recipient chat {@link Jid}
     * @return the {@code <biz>} {@link Node}, or {@code null} when not
     *         applicable
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Node build(Jid chatJid) {
        return build(chatJid, null, false);
    }

    /**
     * Builds the {@code <biz>} node for a recipient, optionally including a
     * native-flow name and an {@code <interactive>}/{@code <native_flow>}
     * subtree.
     *
     * @apiNote
     * Three forms are emitted in priority order: (a) a hosted-business node
     * carrying the {@code host_storage}, {@code actual_actors},
     * {@code privacy_mode_ts} attributes (plus {@code native_flow_name} when
     * present); (b) when the message is a native-flow interactive payload,
     * a node wrapping {@code <interactive v="1" type="native_flow"><native_flow
     * name="..."/></interactive>}; (c) a bare node carrying only
     * {@code native_flow_name}. Returns {@code null} when none of the three
     * conditions apply.
     *
     * @implNote
     * This implementation reads the privacy-mode triplet from the
     * {@link BusinessVerifiedName} contact record via
     * {@link WhatsAppStore#findVerifiedBusinessName(Jid)}; the WA Web
     * counterpart sources the same triplet from
     * {@code WAWebContactCollection.ContactCollection.get(chatJid).privacyMode}.
     *
     * @param chatJid                 the recipient chat {@link Jid}
     * @param nativeFlowName          the native-flow name from the message
     *                                protobuf, or {@code null}
     * @param isNativeFlowInteractive {@code true} when the message is a
     *                                {@code nativeFlowInteractiveMsg}
     *                                payload that must wrap an
     *                                {@code <interactive>} subtree
     * @return the {@code <biz>} {@link Node}, or {@code null} when none of
     *         the three branches apply
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Node build(Jid chatJid, String nativeFlowName, boolean isNativeFlowInteractive) {
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

            return new NodeBuilder()
                    .description("biz")
                    .attribute("host_storage", hostStorage)
                    .attribute("actual_actors", actualActors)
                    .attribute("privacy_mode_ts", privacyModeTs)
                    .attribute("native_flow_name", nativeFlowName)
                    .build();
        }

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

        if (nativeFlowName != null) {
            return new NodeBuilder()
                    .description("biz")
                    .attribute("native_flow_name", nativeFlowName)
                    .build();
        }

        return null;
    }

    /**
     * Builds the {@code <biz>} node for a group SKMSG send carrying a
     * {@code payment_info} native-flow interactive message.
     *
     * @apiNote
     * Mirrors the {@code b(t, e)} helper inside
     * {@code WAWebSendGroupSkmsgJob}: groups attach a
     * {@code <biz><interactive type="native_flow"><native_flow name="payment_info"/>}
     * subtree only for the payment-info native flow. Returns {@code null}
     * for every other message shape, including other native flows and
     * non-interactive bodies.
     *
     * @param container the outgoing {@link MessageContainer}
     * @return the {@code <biz>} {@link Node}, or {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendGroupSkmsgJob", exports = "encryptAndSendSenderKeyMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Node buildGroup(MessageContainer container) {
        if (!(container.content() instanceof InteractiveMessage im)) {
            return null;
        }

        var imc = im.content();
        if (imc.isEmpty() || !(imc.get() instanceof InteractiveMessage.NativeFlowMessage nativeFlow)) {
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
