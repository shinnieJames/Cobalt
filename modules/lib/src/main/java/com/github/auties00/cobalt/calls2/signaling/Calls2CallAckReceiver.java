package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.calls2.Calls2Service;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stream.SocketStreamHandler;

import java.util.Objects;

/**
 * Receives the asynchronous {@code <ack class="call">} the server returns for a fire-and-forget call leg
 * and decodes it into a {@link CallAckOutcome} for the engine.
 *
 * <p>Not every call ack is a synchronous reply. The offer is shipped through the id-correlated send and
 * its {@code <ack class="call" type="offer">} comes back as that send's return value (see
 * {@link CallAckParser}); the accept, by contrast, is shipped fire-and-forget, so its
 * {@code <ack class="call" type="accept">} arrives later as a top-level {@code <ack>} stanza with no
 * pending reply to correlate it. This handler is registered under the {@code "ack"} stream tag to catch
 * that uncorrelated ack: it parses the envelope through {@link CallAckParser#parse(Stanza)} and forwards the
 * {@link CallAckOutcome} to the injected {@link Calls2Service} for the engine's accept-ack handling. A NACK
 * on that ack is the engine's signal to abandon the answered call.
 *
 * <p>A top-level {@code <ack>} that is not a call ack (the ordinary message and receipt delivery acks the
 * server returns under a different {@code class}) yields an empty parse and is ignored, so this handler is
 * inert for every non-call ack. An ack the client correlated to an outbound id is consumed by the reply
 * path before stream-handler dispatch and never reaches here, so this handler never races the offer's
 * synchronous ack. The handler is {@link SocketStreamHandler.Ordered ordered} on the ack's {@code id},
 * which is the call identifier the answering leg stamped, so a call's async ack is serialised on the same
 * per-call chain as its enveloped signals.
 *
 * @implNote This implementation ports the inbound leg of {@code handle_incoming_xmpp_ack} (fn11546,
 * {@code protocol/xmpp/call_signaling_xml.cc}) for the fire-and-forget accept: the native engine dispatches
 * every inbound call ack through that handler, while Cobalt collapses the offer ack onto its id-correlated
 * send (see {@link CallAckParser}) and routes only the uncorrelated accept ack here. No acknowledgement is
 * emitted in reply, because an ack is itself the server's terminal confirmation.
 */
public final class Calls2CallAckReceiver extends SocketStreamHandler.Ordered {
    /**
     * Logs malformed-ack traces.
     */
    private static final System.Logger LOGGER = System.getLogger(Calls2CallAckReceiver.class.getName());

    /**
     * The stream tag this handler is registered under.
     */
    public static final String STREAM_TAG = "ack";

    /**
     * The wire attribute naming the correlation identifier, which the call signaling stamps with the call
     * identifier.
     */
    private static final String ID_ATTRIBUTE = "id";

    /**
     * The fallback ordering key used when an ack carries no {@code id}, so a malformed ack is still
     * serialised on one chain rather than spread across the key space.
     */
    private static final String UNKEYED_ORDERING_KEY = "";

    /**
     * The call service each decoded call {@link CallAckOutcome} is forwarded to for engine handling.
     */
    private final Calls2Service calls2Service;

    /**
     * Constructs a call-ack receiver bound to the call service it forwards to.
     *
     * @param calls2Service the call service each decoded call {@link CallAckOutcome} is forwarded to
     * @throws NullPointerException if {@code calls2Service} is {@code null}
     */
    public Calls2CallAckReceiver(Calls2Service calls2Service) {
        this.calls2Service = Objects.requireNonNull(calls2Service, "calls2Service cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec
     * Returns the ack's {@code id}, which the answering leg stamped with the call identifier, so a call's
     * async ack is serialised on the same per-call chain as any enveloped signal for that call; an ack with
     * no {@code id} falls back to a single shared key so it is still processed in arrival order.
     *
     * @param stanza the inbound {@code <ack>} stanza
     * @return the ordering key, never {@code null}
     */
    @Override
    protected String orderingKey(Stanza stanza) {
        return stanza.getAttributeAsString(ID_ATTRIBUTE, UNKEYED_ORDERING_KEY);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Parses the {@code <ack>} through {@link CallAckParser#parse(Stanza)} and forwards the decoded
     * {@link CallAckOutcome} to the injected {@link Calls2Service}. A top-level {@code <ack>} that is not a
     * call ack yields an empty parse and is ignored, so the handler is inert for the ordinary message and
     * receipt delivery acks; a malformed call ack is dropped without forwarding.
     *
     * @param stanza the inbound {@code <ack>} stanza
     */
    @Override
    public void handle(Stanza stanza) {
        CallAckOutcome outcome;
        try {
            outcome = CallAckParser.parse(stanza).orElse(null);
        } catch (RuntimeException _) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring malformed call <ack>: {0}", stanza);
            return;
        }
        if (outcome == null) {
            return;
        }
        calls2Service.handleInboundAck(outcome);
    }
}
