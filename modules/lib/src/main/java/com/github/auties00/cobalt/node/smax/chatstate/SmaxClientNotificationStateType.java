package com.github.auties00.cobalt.node.smax.chatstate;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.NodeBuilder;

/**
 * Sealed disjunction over the two outbound chat-state payloads that
 * {@link SmaxClientNotificationRequest} can carry: composing or paused.
 *
 * @apiNote
 * Surfaces the choice the bridge makes in
 * {@code WASendChatStateProtocol.sendChatStateProtocol}: {@code "idle"}
 * maps to {@link SmaxClientNotificationPaused},
 * {@code "typing"} and {@code "recording_audio"} both map to
 * {@link SmaxClientNotificationComposing} (differing only by
 * {@link SmaxClientNotificationComposing#hasComposingMediaAudio()}).
 */
@WhatsAppWebModule(moduleName = "WASmaxOutChatstateStateTypes")
public sealed interface SmaxClientNotificationStateType permits SmaxClientNotificationComposing, SmaxClientNotificationPaused {
    /**
     * Builds the state-type child stanza.
     *
     * @apiNote
     * Called by {@link SmaxClientNotificationRequest#toNode()} to attach the
     * inner child to the {@code <chatstate/>} envelope.
     *
     * @implSpec
     * Implementors must return a {@link NodeBuilder} whose root description
     * is either {@code "composing"} or {@code "paused"} so the relay's
     * {@code parseStateTypes} disjunction can route it. The returned builder
     * must be ready to {@code build()} into the inner child of the
     * {@code <chatstate/>} envelope; implementors must not nest it inside
     * another wrapper.
     *
     * @return a {@link NodeBuilder} carrying the state-type child
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutChatstateStateTypes",
            exports = "mergeStateTypes", adaptation = WhatsAppAdaptation.ADAPTED)
    NodeBuilder toNode();
}
