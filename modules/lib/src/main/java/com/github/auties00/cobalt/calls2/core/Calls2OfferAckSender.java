package com.github.auties00.cobalt.calls2.core;

import com.github.auties00.cobalt.stanza.Stanza;

/**
 * Sends an outbound {@code <call><offer>} stanza and returns the synchronous call ack the server replies
 * with.
 *
 * <p>The offer is the one signaling leg whose acknowledgement is synchronous and load-bearing: the server
 * replies to the offer stanza with an {@code <ack class="call" type="offer">} as the send call's return
 * value, and that ack carries the caller's own {@code <relay>} block, the per-device {@code <voip_settings>}
 * bundles, and the participant roster the caller needs to bring up its media plane. Every other signaling
 * leg (accept, preaccept, reject, terminate) is fire-and-forget through
 * {@link com.github.auties00.cobalt.calls2.platform.VoipHostApi#sendSignaling(Stanza)}
 * with its acknowledgement arriving later on the inbound path, so only the offer needs this request and
 * response seam.
 *
 * <p>This seam is the offer half of the signaling-send glue; the controller builds the offer stanza and
 * hands it here, and the implementer ships it on the client transport and blocks the calling virtual
 * thread for the ack round-trip. A NACK is returned as the ack {@link Stanza} the same way a positive ack
 * is, carrying its {@code error} attribute and a relay block with only the denormalised call-creator and
 * call-id; the controller inspects the returned stanza to tell ack from nack.
 *
 * @apiNote This is an internal engine collaborator, not a public surface; embedders never call it.
 * @implNote This implementation seam corresponds to the offer-send-and-ack of {@code wa_call_start_call}
 * (fn10711) and {@code handle_incoming_xmpp_ack} (fn11546) in the wa-voip WASM module {@code ff-tScznZ8P}:
 * the offer rides {@code client.sendNode(envelope)} and its {@code <ack class="call">} arrives as that
 * call's return value, not through a separate handler (confirmed against the live capture in
 * {@code re/calls2-spec/captures/CAPTURE-FINDINGS.md} Q19, where the offer ack idx98 correlated to the
 * offer's id and carried the relay, five per-device {@code <voip_settings>}, {@code <user>}, and
 * {@code <rte>}). In Cobalt it is implemented over {@code LinkedWhatsAppClient.sendNode(StanzaBuilder)},
 * which blocks for the matching ack.
 */
@FunctionalInterface
public interface Calls2OfferAckSender {
    /**
     * Sends the offer stanza and returns the server's synchronous call ack.
     *
     * <p>The implementer ships the supplied {@code <call>} envelope on the client transport and blocks the
     * calling virtual thread until the matching {@code <ack class="call">} returns. The returned stanza is
     * the ack itself, whether positive (carrying the caller's relay block and settings) or a NACK
     * (carrying an {@code error} attribute), so the controller reads the stanza to classify the result and
     * to extract the relay block for the media-plane bring-up.
     *
     * @param offerEnvelope the built {@code <call>} envelope nesting the {@code <offer>} action
     * @return the server's {@code <ack class="call">} reply stanza
     * @throws NullPointerException                                                if {@code offerEnvelope}
     *                                                                             is {@code null}
     * @throws com.github.auties00.cobalt.exception.WhatsAppCallException.DataChannel if the offer could
     *                                                                             not be sent or no ack
     *                                                                             arrived
     */
    Stanza sendOfferAndAwaitAck(Stanza offerEnvelope);
}
