package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckParser;
import com.github.auties00.cobalt.ack.CallAck;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.Objects;
import java.util.Optional;

/**
 * Parses the synchronous {@code <ack class="call">} a call offer or accept returns into a
 * {@link CallAckOutcome}.
 *
 * <p>This is the offer-to-relay seam of the signaling layer. An outbound offer is shipped through the
 * id-correlated {@link LinkedWhatsAppClient#sendNode(StanzaBuilder)}; that call blocks the calling
 * virtual thread and returns the matching {@code <ack class="call" type="offer">} as its value, with
 * the {@code <relay>} block the media plane needs carried inside. The outbound accept follows the same
 * contract and is rejected with an {@code error} code the same way. {@link #parse(Stanza)} turns that
 * return stanza into the typed outcome the engine branches on, so neither the offer sender nor the
 * accept sender has to hand-walk the ack envelope or re-derive the accept/reject decision.
 *
 * <p>The envelope ({@code id}, {@code type}, {@code from}, {@code error}) is decoded by reusing the
 * shared {@link AckParser}, whose {@link AckClass#CALL} branch already produces a {@link CallAck} with
 * its {@code <relay>} child parsed into the calls2 {@link RelayInfo} model through
 * {@link RelayInfo#of(Stanza)}. That already-parsed relay is read back off {@link CallAck#relay()} and
 * folded into the outcome the transport layer consumes, so the child is parsed once. The whole layer is
 * pure-Java glue: a Cobalt {@link Stanza} is already the parsed tree, so there is no
 * stanza-to-voip-stanza bridge to cross.
 *
 * @implNote This implementation ports {@code handle_incoming_xmpp_ack} (fn11546,
 * {@code convert_xmpp_ack_to_msg}) from the wa-voip WASM module {@code ff-tScznZ8P}
 * ({@code protocol/xmpp/call_signaling_xml.cc}), collapsed onto Cobalt's id-correlated send: the native
 * engine consumes the offer ack at a dedicated host entry point, while Cobalt receives the same ack as
 * the {@link LinkedWhatsAppClient#sendNode(StanzaBuilder)} return value. The accept NACK classification by
 * {@code error} code mirrors {@code stanzas/basic.cc} ("Accept ACK: error code = %d. Treating as an
 * Accept NACK"). The {@code <relay>} block is parsed once by the shared {@link AckParser} through
 * {@link RelayInfo#of(Stanza)}, which ports {@code fill_relay_info} (fn11630), and read back off
 * {@link CallAck#relay()} here rather than re-parsed.
 */
public final class CallAckParser {
    /**
     * Prevents instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private CallAckParser() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Parses the {@code <ack class="call">} returned for an outbound offer or accept into a typed
     * outcome.
     *
     * <p>The input must be the {@code <ack>} element itself, as returned by
     * {@link LinkedWhatsAppClient#sendNode(StanzaBuilder)} for the offer or accept envelope. The envelope
     * attributes are read through the shared {@link AckParser}; when that parser yields a
     * {@link CallAck} its already-parsed calls2 {@link RelayInfo} relay child is folded into the
     * result. A return stanza that the shared parser does not classify as a call ack (for
     * example a server error envelope under a different {@code class}) yields an empty result so the
     * caller treats it as a setup failure rather than a usable ack.
     *
     * @param ack the {@code <ack>} stanza returned by the server for the offer or accept
     * @return the parsed {@link CallAckOutcome}, or {@link Optional#empty()} when the stanza is not a
     *         call-class ack
     * @throws NullPointerException     if {@code ack} is {@code null}
     * @throws IllegalArgumentException if the stanza tag is not {@code "ack"}
     */
    public static Optional<CallAckOutcome> parse(Stanza ack) {
        Objects.requireNonNull(ack, "ack cannot be null");
        if (!(AckParser.parse(ack) instanceof CallAck callAck)) {
            return Optional.empty();
        }
        return Optional.of(CallAckOutcome.of(callAck, callAck.relay().orElse(null)));
    }
}
