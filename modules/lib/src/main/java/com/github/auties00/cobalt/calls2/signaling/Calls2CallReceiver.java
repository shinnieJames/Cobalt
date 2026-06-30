package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stream.SocketStreamHandler;

import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Receives inbound VoIP call signaling stanzas and routes each into classification, acknowledgement,
 * buffering, and engine dispatch.
 *
 * <p>This handler is registered under the {@code "call"} stream tag and is the entry point for every
 * signaling action the server delivers inside a {@code <call>} envelope. It is an
 * {@link SocketStreamHandler.Ordered ordered} handler keyed on the call identifier so all signals for
 * one call are processed in arrival order on a single chain, while signals for distinct calls still
 * run in parallel; the native engine processes signaling on one serial message-router queue per call,
 * and ordering matters because a group's join and the rekey that depends on it must not race.
 *
 * <p>For each inbound stanza the handler reads the {@code <call>} envelope (the sender, the single
 * child payload, the optional {@code sender_lid}), classifies the payload through a
 * {@link CallSignalingRouter}, and acts on the verdict: a malformed or unroutable payload is dropped,
 * a payload whose call object does not yet exist is buffered through a {@link CallMessageBuffer} for
 * later replay, and a routable payload is decoded into a {@link CallMessage} and forwarded to the
 * engine sink. Independently of the verdict the handler emits the wire acknowledgement the payload
 * requires: a {@code <receipt>} for the offer, accept, reject, and rekey legs, and an
 * {@code <ack class="call" type="...">} echoing the payload tag for every other signal, so the server
 * stops retransmitting regardless of whether the local engine could act on the message.
 *
 * <p>The decoded {@link CallMessage} is handed to a sink supplied at construction rather than to a
 * hard-wired engine reference, so the signaling layer stays decoupled from the lifecycle controller
 * that consumes the messages; the integrator wires the sink to the engine. The sink receives the
 * decoded message together with the envelope {@code from}, the authoring device {@link Jid} the engine
 * needs as the decryption sender and as the companion-device discriminator the terminate guards key on,
 * because that sender is the {@code <call>} envelope's attribute rather than a field of the decoded
 * action. The handler never throws out of its work method for a single bad stanza: parse failures are
 * dropped, and the ordered base absorbs any escaping throwable.
 *
 * @implNote This implementation ports {@code handle_incoming_xmpp_msg} (fn11539) and
 * {@code handle_incoming_xmpp_offer} (fn11543) from the wa-voip WASM module {@code ff-tScznZ8P}
 * ({@code protocol/xmpp/call_signaling_xml.cc}), but collapses the native XML-stanza-to-voip-stanza bridge
 * ({@code xmlNodeToVoipNode} fn12158): a Cobalt {@link Stanza} is already the parsed tree, so the
 * receiver reads it directly. The per-call ordering keyed on call-id mirrors the single native
 * message-router queue ({@code message_router.cc}); the receipt-versus-ack split mirrors the native
 * {@code handle_incoming_xmpp_receipt} (fn11551) and {@code handle_incoming_xmpp_ack} (fn11546) legs.
 * The classification and buffering are delegated to {@link CallSignalingRouter} and
 * {@link CallMessageBuffer}, which port {@code preprocess_incoming_message} (fn11497) and
 * {@code message_buffer.cc}. Unlike the native path, which logs and drops an unsupported tag
 * ({@code "handle_incoming_xmpp_msg: msg tag %s not supported"}, strings.json {@code 0x0ee90})
 * silently, Cobalt still emits the {@code <ack class="call">} the server expects for the envelope, so
 * the server stops retransmitting.
 */
public final class Calls2CallReceiver extends SocketStreamHandler.Ordered {
    /**
     * Logs malformed-stanza and dropped-payload traces.
     */
    private static final System.Logger LOGGER = System.getLogger(Calls2CallReceiver.class.getName());

    /**
     * The stream tag this handler is registered under.
     */
    public static final String STREAM_TAG = "call";

    /**
     * The wire attribute naming the sender on the {@code <call>} envelope.
     */
    private static final String FROM_ATTRIBUTE = "from";

    /**
     * The wire attribute naming the sender LID on the {@code <call>} envelope.
     */
    private static final String SENDER_LID_ATTRIBUTE = "sender_lid";

    /**
     * The wire attribute naming the call identifier on a {@code <call>} child element.
     */
    private static final String CALL_ID_ATTRIBUTE = "call-id";

    /**
     * The fallback call-id used as the ordering key when a payload carries none, so a malformed
     * payload is still serialised on one chain rather than spreading across the key space.
     */
    private static final String UNKEYED_ORDERING_KEY = "";

    /**
     * The set of payload tags that create a call, so a buffered one is drained and replayed at once.
     *
     * <p>An offer is the call-creating signal: the call object does not exist when the offer arrives, so
     * the offer is buffered like any pre-call message, but unlike a transport or rekey message that must
     * wait for its call, the offer is what brings the call into being. Buffering it and immediately draining
     * the call's buffer therefore replays the offer (and any non-offer message that raced ahead of it) into
     * the engine in arrival order, which rings the call; the engine's own message router treats an offer for
     * a not-yet-created call as a process-and-create verdict.
     */
    private static final Set<String> CALL_CREATING_TAGS = Set.of("offer");

    /**
     * Emits the wire acknowledgement each inbound payload requires, choosing between a
     * {@code <receipt>} for the offer, accept, reject, and rekey legs and an
     * {@code <ack class="call">} for every other signal.
     */
    private final CallSignalingAcknowledger acknowledger;

    /**
     * Classifies each inbound payload into its signaling type and routing verdict.
     */
    private final CallSignalingRouter router;

    /**
     * Buffers payloads whose call object does not yet exist for later replay.
     */
    private final CallMessageBuffer buffer;

    /**
     * Reports whether a call object already exists for a given call identifier, so the router can
     * decide whether a payload is processed now or buffered.
     */
    private final Predicate<String> callExists;

    /**
     * Receives each decoded routable {@link CallMessage} together with the envelope {@code from} for
     * engine handling.
     */
    private final BiConsumer<CallMessage, Jid> sink;

    /**
     * Receives each decoded {@link OfferNoticeStanza} for out-of-engine handling.
     */
    private final Consumer<OfferNoticeStanza> offerNoticeSink;

    /**
     * Constructs a call receiver bound to its client, acknowledgement sender, router, buffer,
     * call-existence predicate, and engine sink.
     *
     * @param whatsapp   the client used to send receipts and read the local account identity
     * @param ackSender  the {@link AckSender} used to emit the {@code <ack class="call">} for
     *                   non-receipt signals
     * @param router     the {@link CallSignalingRouter} that classifies each payload
     * @param buffer     the {@link CallMessageBuffer} that holds payloads whose call object does not
     *                   yet exist
     * @param callExists a predicate reporting whether a call object already exists for a call
     *                   identifier
     * @param sink       the consumer that receives each decoded routable {@link CallMessage} together
     *                   with the envelope {@code from} that authored it
     * @param offerNoticeSink the consumer that receives each decoded {@link OfferNoticeStanza}, which is
     *                   handled outside the call engine
     * @throws NullPointerException if any argument is {@code null}
     */
    public Calls2CallReceiver(LinkedWhatsAppClient whatsapp, AckSender ackSender, CallSignalingRouter router,
                              CallMessageBuffer buffer, Predicate<String> callExists,
                              BiConsumer<CallMessage, Jid> sink, Consumer<OfferNoticeStanza> offerNoticeSink) {
        this.acknowledger = new CallSignalingAcknowledger(whatsapp, ackSender);
        this.router = Objects.requireNonNull(router, "router cannot be null");
        this.buffer = Objects.requireNonNull(buffer, "buffer cannot be null");
        this.callExists = Objects.requireNonNull(callExists, "callExists cannot be null");
        this.sink = Objects.requireNonNull(sink, "sink cannot be null");
        this.offerNoticeSink = Objects.requireNonNull(offerNoticeSink, "offerNoticeSink cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec
     * Returns the payload's {@code call-id} so every signal for one call is serialised on one chain;
     * a malformed envelope with no payload, or a payload with no {@code call-id}, falls back to a
     * single shared key so it is still processed in arrival order rather than spread across the key
     * space.
     *
     * @param stanza the inbound {@code <call>} stanza
     * @return the ordering key, never {@code null}
     */
    @Override
    protected String orderingKey(Stanza stanza) {
        return stanza.getChild()
                .flatMap(payload -> payload.getAttributeAsString(CALL_ID_ATTRIBUTE))
                .orElse(UNKEYED_ORDERING_KEY);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads the {@code <call>} envelope, classifies the single child payload, emits the wire
     * acknowledgement the payload requires, and either drops, buffers, or decodes-and-forwards the
     * payload per the classification verdict. A stanza with no sender or no payload is dropped without
     * acknowledgement because there is nothing to acknowledge.
     *
     * @param stanza the inbound {@code <call>} stanza
     */
    @Override
    public void handle(Stanza stanza) {
        var from = stanza.getAttributeAsJid(FROM_ATTRIBUTE, null);
        var payload = stanza.getChild().orElse(null);
        if (from == null || payload == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring malformed call stanza: {0}", stanza);
            return;
        }

        if (OfferNoticeStanza.ELEMENT.equals(payload.description())) {
            handleOfferNotice(stanza, payload);
            return;
        }

        var senderLid = stanza.getAttributeAsJid(SENDER_LID_ATTRIBUTE, null);
        var verdict = router.classify(payload, senderLid,
                payload.getAttributeAsString(CALL_ID_ATTRIBUTE)
                        .map(callExists::test)
                        .orElse(false));

        acknowledger.acknowledge(stanza, payload, verdict.callId().orElse(null));

        switch (verdict.disposition()) {
            case DROP -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Dropping call payload {0}: {1}", payload.description(), stanza);
            case BUFFER -> verdict.callId().ifPresent(callId -> bufferAndMaybeReplay(callId, payload, from));
            case PROCESS -> forward(payload, from);
        }
    }

    /**
     * Acknowledges an {@code <offer_notice>} and hands the decoded notice to the out-of-engine sink.
     *
     * <p>An offer notice is not a voip-engine signaling action: it does not create, advance, or buffer a
     * call object. It is acknowledged with the same {@code <ack class="call" type="offer_notice">} the
     * server expects for any non-receipt signal, then decoded through {@link OfferNoticeStanza#of(Stanza)}
     * and forwarded to {@link #offerNoticeSink}, which surfaces it to the application and records it in the
     * call history. A notice that cannot be decoded is dropped after acknowledgement so the server stops
     * retransmitting.
     *
     * @param stanza  the inbound {@code <call>} envelope
     * @param payload the {@code <offer_notice>} child element
     */
    private void handleOfferNotice(Stanza stanza, Stanza payload) {
        acknowledger.acknowledge(stanza, payload, payload.getAttributeAsString(CALL_ID_ATTRIBUTE).orElse(null));
        OfferNoticeStanza.of(stanza).ifPresentOrElse(offerNoticeSink, () ->
                LOGGER.log(System.Logger.Level.DEBUG, "Could not decode offer_notice: {0}", stanza));
    }

    /**
     * Buffers a not-yet-routable payload and, when it is the call-creating offer, replays the call's
     * buffered payloads to the engine.
     *
     * <p>Every pre-call payload is buffered in arrival order through {@link CallMessageBuffer}. A non-offer
     * payload that races ahead of its call stays buffered until the offer arrives. An offer is the signal
     * that brings the call into being, so once it is buffered the call's buffer is drained and every drained
     * payload (the offer and any earlier-raced message) is forwarded to the engine sink in arrival order
     * with the offer envelope's {@code from}; the engine creates the call from the offer and applies the
     * replayed messages against it. This is the production caller that makes a fresh inbound offer reach the
     * lifecycle controller and ring.
     *
     * <p>The {@link CallMessageBuffer} stores only the payload {@link Stanza}, not the sender of the envelope
     * each was carried in, so a drained payload that raced ahead of the offer is replayed under the offer's
     * envelope sender rather than its own. This matches the engine, which processes the buffered offer and
     * the messages that raced ahead of it as one batch authored by the same peer device for the same call.
     *
     * @param callId  the call identifier the payload belongs to
     * @param payload the {@code <call>} child element being buffered
     * @param from    the envelope sender of the stanza that triggered this buffering, used as the
     *                attribution for every drained payload on a call-creating replay
     */
    private void bufferAndMaybeReplay(String callId, Stanza payload, Jid from) {
        buffer.buffer(callId, payload);
        if (CALL_CREATING_TAGS.contains(payload.description())) {
            buffer.drainBufferedMessages(callId).forEach(drained -> forward(drained, from));
        }
    }

    /**
     * Decodes a routable payload into a {@link CallMessage} and forwards it, with the envelope
     * {@code from}, to the engine sink.
     *
     * <p>Decoding goes through {@link Calls2CallStanza#parse(Stanza)}, the integrator-owned parser that
     * maps a {@code <call>} child element to its typed message record. The decoded message is paired with
     * {@code from}, the envelope sender, so the engine receives the authoring device JID alongside the
     * action. A payload the parser cannot decode is dropped without forwarding; the acknowledgement has
     * already been sent, so the server does not retransmit.
     *
     * @param payload the {@code <call>} child element to decode and forward
     * @param from    the envelope sender forwarded alongside the decoded message
     */
    private void forward(Stanza payload, Jid from) {
        Calls2CallStanza.parse(payload).ifPresentOrElse(
                message -> sink.accept(message, from),
                () -> LOGGER.log(System.Logger.Level.DEBUG, "Could not decode call payload: {0}", payload));
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation additionally clears the {@link CallMessageBuffer} so a new connection starts
     * with no recorded transaction ids, terminate reasons, or buffered messages from the previous
     * connection, then drops the ordered base's in-flight per-call chains.
     */
    @Override
    public void reset() {
        buffer.reset();
        super.reset();
    }
}
