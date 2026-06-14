package com.github.auties00.cobalt.call.signaling;

import com.github.auties00.cobalt.stream.SocketStreamHandler;
import com.github.auties00.cobalt.call.CallService;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.listener.linked.LinkedCallEndedListener;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.call.CallRuntime;
import com.github.auties00.cobalt.model.call.CallEndReason;

/**
 * Handles top-level inbound bare {@code <terminate>} stanzas.
 *
 * <p>The call-end signal does not always arrive wrapped in a {@code <call>} envelope. The server emits
 * a bare {@code <terminate call-id call-creator reason .../>} stanza when another device of the same
 * account accepted or rejected the call ({@code reason="accepted_elsewhere"}), when the server times
 * out the offer, or when the peer's call infrastructure forcibly ends the call for any reason that
 * does not fit a wrapped {@code <call><terminate>} payload. This handler routes the event through the
 * same {@link CallService} peer-terminate path that {@link CallReceiver} uses for envelope-wrapped
 * terminates, so listener fan-out and registry cleanup converge regardless of envelope shape.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleVoipCall")
public final class CallTerminateReceiver extends SocketStreamHandler.Concurrent {
    /**
     * Logs parse traces for malformed terminate stanzas.
     */
    private static final System.Logger LOGGER = System.getLogger(CallTerminateReceiver.class.getName());

    /**
     * Holds the owning client used for store access and listener fan-out.
     */
    private final LinkedWhatsAppClient whatsapp;

    /**
     * Holds the call engine, which dispatches peer-side end transitions to the matching
     * {@link CallRuntime}.
     */
    private final CallService engine;

    /**
     * Constructs a bare-terminate receiver bound to its client and call engine.
     *
     * @param whatsapp the owning client used for store access and listener fan-out
     * @param engine   the call engine that dispatches peer-side end transitions
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCall", exports = "handleCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public CallTerminateReceiver(LinkedWhatsAppClient whatsapp, CallService engine) {
        this.whatsapp = whatsapp;
        this.engine = engine;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads the {@code call-id} attribute and ignores the stanza when it is absent. Otherwise it
     * resolves the end reason from {@code reason}, derives the ending party from {@code call-creator}
     * (falling back to {@code from}), routes the peer-terminate transition through the engine, removes
     * the call from the store, and notifies listeners.
     *
     * @param node the inbound bare {@code <terminate>} stanza
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
        whatsapp.store().chatStore().removeCall(callId);
        notifyEnded(callId, fromJid, reason);
    }

    /**
     * Notifies every registered listener that the call has terminated.
     *
     * <p>The wire reason literal is parsed into a typed {@link CallEndReason} via
     * {@link CallEndReason#fromWireValue(String)}; unrecognized or absent literals surface as
     * {@link CallEndReason#UNKNOWN}. Each listener is invoked on its own virtual thread so the socket
     * stream handler thread is not blocked.
     *
     * @param callId     the identifier of the call that ended
     * @param fromJid    the JID of the party that ended the call, or {@code null} when the server
     *                   elided it
     * @param wireReason the wire-level reason literal, or {@code null}
     */
    private void notifyEnded(String callId, Jid fromJid, String wireReason) {
        var parsed = CallEndReason.fromWireValue(wireReason);
        for (var listener : whatsapp.store().listeners()) {
            if (listener instanceof LinkedCallEndedListener typed) {
                Thread.startVirtualThread(() -> typed.onCallEnded(whatsapp, callId, fromJid, parsed));
            }
        }
    }
}
