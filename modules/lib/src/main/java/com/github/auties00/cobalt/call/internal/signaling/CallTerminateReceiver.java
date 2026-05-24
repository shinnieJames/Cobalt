package com.github.auties00.cobalt.call.internal.signaling;

import com.github.auties00.cobalt.call.internal.CallService;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.call.ActiveCall;
import com.github.auties00.cobalt.call.CallEndReason;

/**
 * Handles top-level inbound {@code <terminate>} stanzas.
 *
 * <p>WA Web's call-end signal does not always arrive wrapped in a
 * {@code <call>} envelope. The server emits a bare
 * {@code <terminate call-id call-creator reason …/>} stanza when:
 * <ul>
 *   <li>another device of the same account accepted or rejected the
 *       call ({@code reason="accepted_elsewhere"});</li>
 *   <li>the server times out the offer;</li>
 *   <li>the peer's call infrastructure forcibly ends the call for
 *       any reason that doesn't fit a {@code <call><terminate>}
 *       envelope.</li>
 * </ul>
 *
 * <p>The receiver routes the event through the same {@link CallService}
 * peer-terminate path that {@link CallReceiver} uses for envelope-wrapped
 * terminates, so listener fan-out and registry cleanup converge.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleVoipCall")
public final class CallTerminateReceiver implements SocketStream.Handler {
    /**
     * Logger for parse traces.
     */
    private static final System.Logger LOGGER = System.getLogger(CallTerminateReceiver.class.getName());

    /**
     * The owning client used for store access + listener fan-out.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The call engine — dispatches peer-side end transitions to the
     * matching {@link ActiveCall}.
     */
    private final CallService engine;

    /**
     * Constructs a new bare-terminate receiver.
     *
     * @param whatsapp the owning client
     * @param engine   the call engine
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCall", exports = "handleCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public CallTerminateReceiver(WhatsAppClient whatsapp, CallService engine) {
        this.whatsapp = whatsapp;
        this.engine = engine;
    }

    /**
     * Handles a bare {@code <terminate>} stanza by routing the
     * peer-end transition through the engine and notifying listeners.
     *
     * @param node the inbound terminate stanza
     */
    @Override
    public void handle(Node node) {
        var callId = node.getAttributeAsString("call-id", null);
        if (callId == null) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring bare <terminate> without call-id: {0}", node);
            return;
        }

        var reason = node.getAttributeAsString("reason", null);
        var creator = node.getAttributeAsJid("call-creator", null);
        var fromJid = creator != null
                ? creator
                : node.getAttributeAsJid("from", null);

        engine.onPeerTerminate(callId, reason);
        whatsapp.store().removeCall(callId);
        notifyEnded(callId, fromJid, reason);
    }

    /**
     * Notifies every listener that the call has terminated, on a
     * virtual thread so the socket stream isn't blocked.
     *
     * @param callId  the call identifier
     * @param fromJid the JID of the party that ended the call, or
     *                {@code null} when the server elided it
     * @param wireReason the wire-level reason literal
     */
    private void notifyEnded(String callId, Jid fromJid, String wireReason) {
        var parsed = CallEndReason.fromWireValue(wireReason);
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onCallEnded(whatsapp, callId, fromJid, parsed));
        }
    }
}
