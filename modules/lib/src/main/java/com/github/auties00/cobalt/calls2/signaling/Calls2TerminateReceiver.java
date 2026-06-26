package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stream.SocketStreamHandler;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Receives top-level bare {@code <terminate>} stanzas that end a call without a {@code <call>}
 * envelope.
 *
 * <p>The call-end signal does not always arrive wrapped in a {@code <call>} stanza. The server emits a
 * bare {@code <terminate call-id call-creator reason .../>} at the top level when another device of the
 * same account answered or declined the call, when the server times out the offer, or when the peer
 * infrastructure forcibly ends the call in a way that does not fit a wrapped {@code <call><terminate>}
 * payload. This handler is registered under the {@code "terminate"} stream tag and routes the bare
 * terminate through the same decode-and-forward path the {@link Calls2CallReceiver} uses for an
 * enveloped terminate, so the engine sees one terminate model regardless of envelope shape.
 *
 * <p>The handler is {@link SocketStreamHandler.Ordered ordered} keyed on the call identifier so a bare
 * terminate is serialised on the same per-call chain as any enveloped signal for that call, and the
 * terminate is never reordered relative to an enveloped signal that races it. A bare terminate with no
 * {@code call-id} is dropped because it cannot be associated with a call. The handler forwards the
 * decoded {@link TerminateStanza}, together with the stanza {@code from}, to a sink supplied at
 * construction rather than to a hard-wired engine reference, keeping the signaling layer decoupled from
 * the lifecycle controller; the integrator wires the sink to the engine. The stanza {@code from} is the
 * authoring device {@link Jid} the engine needs as the companion-device discriminator the terminate
 * guards key on, which the bare {@code <terminate>} carries on its top-level {@code from} attribute
 * rather than inside the decoded action.
 *
 * @implNote This implementation substitutes the existing {@code CallTerminateReceiver} and ports the
 * bare-terminate handling fed by {@code handle_incoming_signaling_xmpp_msg} (fn10724) and
 * {@code handle_terminate} (fn11492) in the wa-voip WASM module {@code ff-tScznZ8P}. A bare terminate
 * is the {@code <terminate>} element itself rather than a {@code <call>} child, so it is decoded
 * directly through {@link TerminateStanza#of(Stanza)}; the reason literal maps onto the internal
 * {@code call_term_reason} enum through {@code call_terminate_reason_from_string} (fn10925) and onto
 * {@link com.github.auties00.cobalt.model.call.CallEndReason} inside the record. The native handler
 * acquires the global and per-call locks before dispatch; the ordered base's per-call chain provides
 * the equivalent per-call serialisation on a virtual thread.
 */
public final class Calls2TerminateReceiver extends SocketStreamHandler.Ordered {
    /**
     * Logs malformed-stanza traces.
     */
    private static final System.Logger LOGGER = System.getLogger(Calls2TerminateReceiver.class.getName());

    /**
     * The stream tag this handler is registered under.
     */
    public static final String STREAM_TAG = "terminate";

    /**
     * The wire attribute naming the call identifier on a bare {@code <terminate>} stanza.
     */
    private static final String CALL_ID_ATTRIBUTE = "call-id";

    /**
     * The wire attribute naming the sender on a bare {@code <terminate>} stanza.
     */
    private static final String FROM_ATTRIBUTE = "from";

    /**
     * The fallback ordering key used when a stanza carries no {@code call-id}, so a malformed terminate
     * is still serialised on one chain rather than spread across the key space.
     */
    private static final String UNKEYED_ORDERING_KEY = "";

    /**
     * Receives the decoded {@link TerminateStanza} together with the stanza {@code from} for engine
     * handling.
     */
    private final BiConsumer<TerminateStanza, Jid> sink;

    /**
     * Constructs a bare-terminate receiver bound to its engine sink.
     *
     * @param sink the consumer that receives each decoded {@link TerminateStanza} together with the
     *             stanza {@code from} that authored it
     * @throws NullPointerException if {@code sink} is {@code null}
     */
    public Calls2TerminateReceiver(BiConsumer<TerminateStanza, Jid> sink) {
        this.sink = Objects.requireNonNull(sink, "sink cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec
     * Returns the stanza's {@code call-id} so a bare terminate is serialised on the same per-call
     * chain as any enveloped signal for that call; a stanza with no {@code call-id} falls back to a
     * single shared key so it is still processed in arrival order.
     *
     * @param stanza the inbound bare {@code <terminate>} stanza
     * @return the ordering key, never {@code null}
     */
    @Override
    protected String orderingKey(Stanza stanza) {
        return stanza.getAttributeAsString(CALL_ID_ATTRIBUTE, UNKEYED_ORDERING_KEY);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Drops a stanza with no {@code call-id}; otherwise decodes the bare {@code <terminate>} into a
     * {@link TerminateStanza} and forwards it, with the stanza {@code from}, to the engine sink. A stanza
     * that carries a {@code call-id} but no {@code call-creator} fails decode and is dropped without
     * forwarding, because both attributes are required to address the ended call. A stanza with no
     * {@code from} forwards a {@code null} sender, which the engine treats as a non-companion device.
     *
     * @param stanza the inbound bare {@code <terminate>} stanza
     */
    @Override
    public void handle(Stanza stanza) {
        if (!stanza.hasAttribute(CALL_ID_ATTRIBUTE)) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring bare <terminate> without call-id: {0}", stanza);
            return;
        }
        TerminateStanza terminate;
        try {
            terminate = TerminateStanza.of(stanza);
        } catch (RuntimeException _) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring malformed bare <terminate>: {0}", stanza);
            return;
        }
        var from = stanza.getAttributeAsJid(FROM_ATTRIBUTE, null);
        sink.accept(terminate, from);
    }
}
