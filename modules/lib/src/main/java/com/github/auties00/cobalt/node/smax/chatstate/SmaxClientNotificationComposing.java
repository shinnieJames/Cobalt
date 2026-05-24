package com.github.auties00.cobalt.node.smax.chatstate;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound "composing" state-type carried by a
 * {@link SmaxClientNotificationRequest}.
 *
 * @apiNote
 * Backs the chat-state surfaces routed through
 * {@code WASendChatStateProtocol.sendChatStateProtocol}:
 * {@code WAWebChatStateBridge.sendChatStateComposing} maps the
 * "user is typing" indicator and
 * {@code WAWebChatStateBridge.sendChatStateRecording} maps the "user is
 * recording a voice note" indicator. The two surfaces differ only by
 * whether {@link #hasComposingMediaAudio()} is set.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutChatstateComposingMixin")
public final class SmaxClientNotificationComposing implements SmaxClientNotificationStateType {
    /**
     * Whether the {@code media="audio"} marker is emitted.
     *
     * @apiNote
     * {@code true} indicates a voice-note recording in progress (the "speech
     * bubble with mic" indicator in the chat UI); {@code false} indicates
     * plain text typing.
     */
    private final boolean hasComposingMediaAudio;

    /**
     * Constructs a composing state-type.
     *
     * @param hasComposingMediaAudio whether to emit the {@code media="audio"} marker
     */
    public SmaxClientNotificationComposing(boolean hasComposingMediaAudio) {
        this.hasComposingMediaAudio = hasComposingMediaAudio;
    }

    /**
     * Returns whether the {@code media="audio"} marker is emitted.
     *
     * @return {@code true} when audio recording, {@code false} when typing text
     */
    public boolean hasComposingMediaAudio() {
        return hasComposingMediaAudio;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation emits a {@code <composing>} child, attaching
     * {@code media="audio"} only when {@link #hasComposingMediaAudio()} is
     * {@code true}, mirroring {@code mergeComposingMixin}'s
     * {@code OPTIONAL_LITERAL("audio", ...)} behaviour.
     *
     * @return a {@link NodeBuilder} carrying the {@code <composing>} child
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutChatstateComposingMixin",
            exports = "mergeComposingMixin", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        return new NodeBuilder()
                .description("composing")
                .attribute("media", "audio", hasComposingMediaAudio);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxClientNotificationComposing) obj;
        return this.hasComposingMediaAudio == that.hasComposingMediaAudio;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hasComposingMediaAudio);
    }

    @Override
    public String toString() {
        return "SmaxClientNotificationComposing[hasComposingMediaAudio="
                + hasComposingMediaAudio + ']';
    }
}
