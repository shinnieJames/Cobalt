package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.call.CallEndReason;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Represents a {@code <reject>} signal: the callee declines the call before answering.
 *
 * <p>A reject is sent by the callee in response to an inbound offer the user declines, or by the
 * engine when it refuses an offer it cannot service. It carries the universal call header, a
 * {@code reason} literal describing why the call was declined, and an optional fanout {@code count}.
 * The reason is the same vocabulary the terminate signal uses; it is modeled as a
 * {@link CallEndReason} with the original wire literal retained so an unrecognized literal can still
 * round-trip.
 *
 * <p>On the wire the element is {@code <reject call-id="..." call-creator="..." reason="<literal>"
 * count="N"/>}.
 *
 * @implNote This implementation models the {@code <reject>} element (data offset {@code 0x24a3d})
 * parsed and built by {@code serialize_reject}/{@code deserialize_reject} in the wa-voip WASM module
 * {@code ff-tScznZ8P} ({@code stanzas/basic.cc}): the {@code reason} attribute (data offset
 * {@code 0x55669}) and the {@code count} attribute (data offset {@code 0x1d50e}), over the common
 * header stamped by {@code populate_common_call_attr} (fn11591). The reason literal maps to the
 * internal {@code call_term_reason} enum through {@code call_terminate_reason_from_string} (fn10925);
 * here it maps onto {@link CallEndReason}, whose {@code UNKNOWN}-collapsing
 * {@link CallEndReason#fromWireValue(String)} contract preserves the inbound literal classification.
 *
 * @param callId      the call identifier; never {@code null}
 * @param callCreator the call creator's device JID; never {@code null}
 * @param reason      the decline reason; never {@code null}
 * @param reasonWire  the exact wire {@code reason} literal, retained so an unrecognized reason can be
 *                    re-emitted verbatim; never {@code null}
 * @param count       the fanout count, or {@code -1} when absent
 * @see Calls2SignalingType#REJECT
 * @see CallEndReason
 */
public record RejectStanza(String callId, Jid callCreator, CallEndReason reason, String reasonWire, int count)
        implements CallMessage {
    /**
     * The wire element tag for a reject signal.
     */
    public static final String ELEMENT = "reject";

    /**
     * The wire attribute naming the decline reason.
     */
    private static final String REASON_ATTRIBUTE = "reason";

    /**
     * The wire attribute naming the fanout count.
     */
    private static final String COUNT_ATTRIBUTE = "count";

    /**
     * Validates the record components.
     *
     * @throws NullPointerException if {@code callId}, {@code callCreator}, {@code reason}, or
     *                              {@code reasonWire} is {@code null}
     */
    public RejectStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(reasonWire, "reasonWire cannot be null");
    }

    /**
     * Returns a reject carrying a typed reason, with no fanout count, deriving the wire literal from
     * the reason.
     *
     * @param callId      the call identifier
     * @param callCreator the call creator's device JID
     * @param reason      the decline reason
     * @return the reject signal
     * @throws NullPointerException if any argument is {@code null}
     */
    public static RejectStanza of(String callId, Jid callCreator, CallEndReason reason) {
        Objects.requireNonNull(reason, "reason cannot be null");
        return new RejectStanza(callId, callCreator, reason, reason.wireValue(), -1);
    }

    /**
     * Returns the fanout count, if present.
     *
     * @return an {@link OptionalInt} holding the count, or empty when the signal carries none
     */
    public OptionalInt countValue() {
        return count < 0 ? OptionalInt.empty() : OptionalInt.of(count);
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#REJECT}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.REJECT;
    }

    /**
     * Builds the {@code <reject call-id call-creator reason count/>} action stanza.
     *
     * <p>An absent {@code count} is omitted from the stanza.
     *
     * @return the reject action stanza
     */
    @Override
    public Stanza toStanza() {
        return CallMessages.stampHeader(new StanzaBuilder().description(ELEMENT), callId, callCreator)
                .attribute(REASON_ATTRIBUTE, reasonWire)
                .attribute(COUNT_ATTRIBUTE, count, count >= 0)
                .build();
    }

    /**
     * Decodes a {@code <reject>} action stanza into a {@link RejectStanza}.
     *
     * <p>The {@code reason} literal is retained verbatim and also classified into a
     * {@link CallEndReason}; an absent {@code reason} classifies to {@link CallEndReason#UNKNOWN} with
     * an empty wire literal.
     *
     * @param stanza the {@code <reject>} stanza
     * @return the decoded reject signal
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent
     */
    public static RejectStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var reasonWire = stanza.getAttributeAsString(REASON_ATTRIBUTE, "");
        var reason = CallEndReason.fromWireValue(reasonWire);
        var count = stanza.getAttributeAsInt(COUNT_ATTRIBUTE, -1);
        return new RejectStanza(callId, callCreator, reason, reasonWire, count);
    }
}
