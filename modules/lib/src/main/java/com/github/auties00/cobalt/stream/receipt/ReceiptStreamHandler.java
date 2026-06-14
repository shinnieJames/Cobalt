package com.github.auties00.cobalt.stream.receipt;

import com.github.auties00.cobalt.stream.SocketStreamHandler;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.call.signaling.CallReceiptReceiver;
import com.github.auties00.cobalt.wam.WamService;

/**
 * Routes an incoming {@code <receipt>} stanza to the appropriate specialised
 * {@link SocketStreamHandler}.
 *
 * <p>WhatsApp multiplexes three disjoint flows onto the {@code <receipt>} tag,
 * and this dispatcher is the single registered consumer for all of them. VoIP
 * signalling receipts whose first child is {@code <offer>}, {@code <accept>}
 * or {@code <reject>} are forwarded to {@link CallReceiptReceiver}. Every
 * other receipt, both retry receipts (whose stanza {@code type} is
 * {@code "retry"} or {@code "enc_rekey_retry"}) and regular delivery, read or
 * played acknowledgements, is forwarded to
 * {@link MessageReceiptStreamHandler}, which performs the secondary
 * retry-vs-regular split inside its own {@link #handle(Node)} entry point.
 *
 * @implNote
 * This implementation fuses three WA Web dispatch sites onto a single Java
 * entry point: the call-receipt branch
 * ({@code WAWebCommsHandleWorkerCompatibleStanza}), the non-retry messaging
 * branch ({@code WAWebCommsHandleMessagingStanza}) and the retry branch
 * ({@code WAWebCommsHandleLoggedInStanza}). Cobalt does not split per-worker,
 * per-messaging and per-logged-in entry points because it has no worker
 * thread model, so the three branches collapse into the single switch in
 * {@link #handle(Node)}.
 */
@WhatsAppWebModule(moduleName = "WAWebCommsHandleWorkerCompatibleStanza")
@WhatsAppWebModule(moduleName = "WAWebCommsHandleLoggedInStanza")
@WhatsAppWebModule(moduleName = "WAWebCommsHandleMessagingStanza")
@WhatsAppWebModule(moduleName = "WAWebCommsHandleStanzaUtils")
public final class ReceiptStreamHandler extends SocketStreamHandler.Concurrent {
    /**
     * The {@link CallReceiptReceiver} that consumes VoIP signalling
     * receipts.
     */
    private final CallReceiptReceiver callReceiptHandler;

    /**
     * The {@link MessageReceiptStreamHandler} that consumes retry receipts
     * and delivery, read or played acknowledgements.
     */
    private final MessageReceiptStreamHandler messageReceiptHandler;

    /**
     * Constructs a new {@link ReceiptStreamHandler} and eagerly instantiates
     * both downstream specialised handlers.
     *
     * <p>The {@link MessageService} is forwarded to
     * {@link MessageReceiptStreamHandler} for the retry-receipt re-send path,
     * and the {@link WamService} is forwarded for the receipt-stanza-receive
     * telemetry event.
     *
     * @param whatsapp       the non-{@code null} client used to send the
     *                       {@code <ack>} response and access the store
     * @param messageService the non-{@code null} service that re-sends a
     *                       message in response to a retry receipt
     * @param wamService     the non-{@code null} telemetry service that
     *                       commits the receipt-stanza-receive event
     * @param ackSender      the {@link AckSender} forwarded to both
     *                       sub-handlers for emitting outbound
     *                       {@code <ack>} stanzas
     */
    public ReceiptStreamHandler(LinkedWhatsAppClient whatsapp, MessageService messageService, WamService wamService, AckSender ackSender) {
        this.callReceiptHandler = new CallReceiptReceiver(whatsapp, ackSender);
        this.messageReceiptHandler = new MessageReceiptStreamHandler(whatsapp, messageService, wamService, ackSender);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Forwards VoIP-signalling receipts to {@link CallReceiptReceiver} and
     * everything else, both retry receipts and regular delivery, read or
     * played acknowledgements, to {@link MessageReceiptStreamHandler}.
     *
     * @implNote
     * This implementation defers the secondary retry-vs-regular split to
     * {@link MessageReceiptStreamHandler#handle(Node)} so the routing logic
     * stays close to the retry-specific state machine.
     *
     * @param node {@inheritDoc}
     */
    @Override
    public void handle(Node node) {
        if (isCallReceipt(node)) {
            callReceiptHandler.handle(node);
            return;
        }

        messageReceiptHandler.handle(node);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation forwards the reset to both downstream handlers
     * so any per-connection state held inside the call-receipt or
     * message-receipt paths is cleared on reconnect.
     */
    @Override
    public void reset() {
        callReceiptHandler.reset();
        messageReceiptHandler.reset();
    }

    /**
     * Returns {@code true} when the first child of the receipt stanza
     * identifies a VoIP signalling acknowledgement.
     *
     * <p>The split runs before the receipt is parsed as a delivery
     * acknowledgement. The three recognized children mirror WA Web's
     * classification: {@code <offer>} acknowledges a VoIP call offer,
     * {@code <accept>} acknowledges the peer's acceptance and
     * {@code <reject>} acknowledges a rejection.
     *
     * @implNote
     * This implementation mirrors WA Web's
     * {@code WAWebCommsHandleStanzaUtils.isCallReceipt}, including the
     * assumption that the first child alone is sufficient to discriminate
     * the stanza class.
     *
     * @param node the {@code <receipt>} stanza to classify
     * @return {@code true} when the first child has tag {@code offer},
     *         {@code accept} or {@code reject}; {@code false} otherwise
     */
    private boolean isCallReceipt(Node node) {
        var child = node.getChild().orElse(null);
        return child != null && switch (child.description()) {
            case "offer", "accept", "reject" -> true;
            default -> false;
        };
    }
}
