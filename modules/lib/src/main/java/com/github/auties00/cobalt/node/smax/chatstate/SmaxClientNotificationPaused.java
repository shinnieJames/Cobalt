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
 * The outbound "paused" state-type carried by a
 * {@link SmaxClientNotificationRequest}.
 *
 * @apiNote
 * Backs {@code WAWebChatStateBridge.sendChatStatePaused}, the "user stopped
 * typing" indicator. The "idle" branch of
 * {@code WASendChatStateProtocol.sendChatStateProtocol} routes to this
 * payload to clear the typing dot from the peer's chat UI.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutChatstatePausedMixin")
public final class SmaxClientNotificationPaused implements SmaxClientNotificationStateType {
    /**
     * Constructs the empty paused state-type.
     */
    public SmaxClientNotificationPaused() {
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation emits a bare {@code <paused/>} child with no
     * attributes, mirroring {@code mergePausedMixin}.
     *
     * @return a {@link NodeBuilder} carrying the {@code <paused/>} child
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutChatstatePausedMixin",
            exports = "mergePausedMixin", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        return new NodeBuilder()
                .description("paused");
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        return obj != null && obj.getClass() == this.getClass();
    }

    @Override
    public int hashCode() {
        return SmaxClientNotificationPaused.class.hashCode();
    }

    @Override
    public String toString() {
        return "SmaxClientNotificationPaused[]";
    }
}
