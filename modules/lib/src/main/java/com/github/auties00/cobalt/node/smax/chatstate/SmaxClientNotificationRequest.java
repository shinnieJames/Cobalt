package com.github.auties00.cobalt.node.smax.chatstate;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;

/**
 * The outbound {@code <chatstate to="..."><composing|paused/></chatstate>}
 * stanza that broadcasts the local user's typing or paused state to a peer
 * or group.
 *
 * @apiNote
 * Surfaces {@code WAWebChatStateBridge}'s
 * {@code sendChatStateComposing} / {@code sendChatStateRecording} /
 * {@code sendChatStatePaused} indicators. The stanza is fire-and-forget on
 * the wire ({@code WAComms.castSmaxStanza}); the relay does not ack.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutChatstateClientNotificationRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutChatstateStateTypes")
public final class SmaxClientNotificationRequest implements SmaxOperation.Request {
    /**
     * The chat JID receiving the indicator.
     *
     * @apiNote
     * For 1:1 chats this is the peer user JID; for group chats this is the
     * group JID. The relay fans out to active participants.
     */
    private final Jid chatstateTo;

    /**
     * The state-type payload; either {@link SmaxClientNotificationComposing}
     * or {@link SmaxClientNotificationPaused}.
     */
    private final SmaxClientNotificationStateType stateType;

    /**
     * Constructs a client-notification request.
     *
     * @param chatstateTo the chat JID; never {@code null}
     * @param stateType   the state-type payload; never {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public SmaxClientNotificationRequest(Jid chatstateTo, SmaxClientNotificationStateType stateType) {
        this.chatstateTo = Objects.requireNonNull(chatstateTo, "chatstateTo cannot be null");
        this.stateType = Objects.requireNonNull(stateType, "stateType cannot be null");
    }

    /**
     * Returns the chat JID receiving the indicator.
     *
     * @return the chat JID; never {@code null}
     */
    public Jid chatstateTo() {
        return chatstateTo;
    }

    /**
     * Returns the state-type payload.
     *
     * @return the state-type; never {@code null}
     */
    public SmaxClientNotificationStateType stateType() {
        return stateType;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation emits a {@code <chatstate to="...">} envelope and
     * delegates the inner child to
     * {@link SmaxClientNotificationStateType#toNode()}, mirroring the
     * {@code makeClientNotificationRequest} +
     * {@code mergeStateTypes} composition. The WA Web optional
     * {@code internalTestMixin} child (a dev-only debug payload) is not
     * emitted by Cobalt.
     *
     * @return a {@link NodeBuilder} carrying the
     *         {@code <chatstate><composing|paused/></chatstate>} stanza
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutChatstateClientNotificationRequest",
            exports = "makeClientNotificationRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var stateChild = stateType.toNode().build();
        return new NodeBuilder()
                .description("chatstate")
                .attribute("to", chatstateTo)
                .content(stateChild);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxClientNotificationRequest) obj;
        return Objects.equals(this.chatstateTo, that.chatstateTo)
                && Objects.equals(this.stateType, that.stateType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatstateTo, stateType);
    }

    @Override
    public String toString() {
        return "SmaxClientNotificationRequest[chatstateTo=" + chatstateTo
                + ", stateType=" + stateType + ']';
    }
}
