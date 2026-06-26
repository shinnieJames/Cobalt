package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Represents an {@code <interruption>} in-call action: a peer's media stream has been interrupted or
 * has recovered.
 *
 * <p>An interruption signals that a participant's audio or video can no longer flow, or that a prior
 * interruption has cleared. It carries the universal call header, a {@code state} flag that is one of
 * the two literals {@code begin} (the interruption started) or {@code end} (it cleared), and a numeric
 * {@code type} code classifying the cause of the interruption. The engine surfaces these to the host
 * as an interruption state-change event and may play the interruption tone while a {@code begin} is
 * outstanding.
 *
 * <p>On the wire the element is {@code <interruption call-id="..." call-creator="..."
 * state="begin|end" type="N"/>}.
 *
 * @implNote This implementation models the {@code <interruption>} element built by
 * {@code serialize_interruption} in the wa-voip WASM module {@code ff-tScznZ8P}
 * ({@code stanzas/in_call_actions.cc}), carried in message-container type {@code 0x6c} and projecting
 * to taxonomy ordinal {@code 11} ({@link Calls2SignalingType#INTERRUPTION}). The {@code state}
 * attribute takes the literal {@code begin} or {@code end} and the {@code type} attribute is the
 * integer interruption-cause code, stamped over the common header written by
 * {@code populate_common_call_attr} (fn11591): {@code call-id} (data offset {@code 0x888f9}) and
 * {@code call-creator} (data offset {@code 0x45ea5}).
 *
 * @param callId           the call identifier; never {@code null}
 * @param callCreator      the call creator's device JID; never {@code null}
 * @param began            {@code true} when the interruption is starting ({@code state="begin"}),
 *                         {@code false} when it is clearing ({@code state="end"})
 * @param interruptionType the numeric interruption-cause code carried in the wire {@code type} attribute
 * @see Calls2SignalingType#INTERRUPTION
 */
public record InterruptionStanza(String callId, Jid callCreator, boolean began, int interruptionType)
        implements InCallActionStanza {
    /**
     * The wire element tag for an interruption action.
     */
    public static final String ELEMENT = "interruption";

    /**
     * The wire attribute naming the interruption phase.
     */
    private static final String STATE_ATTRIBUTE = "state";

    /**
     * The wire attribute naming the interruption-cause code.
     */
    private static final String TYPE_ATTRIBUTE = "type";

    /**
     * The {@code state} literal indicating an interruption is starting.
     */
    private static final String STATE_BEGIN = "begin";

    /**
     * The {@code state} literal indicating an interruption is clearing.
     */
    private static final String STATE_END = "end";

    /**
     * Validates the record components.
     *
     * @throws NullPointerException if {@code callId} or {@code callCreator} is {@code null}
     */
    public InterruptionStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#INTERRUPTION}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.INTERRUPTION;
    }

    /**
     * Builds the {@code <interruption call-id call-creator state type/>} action stanza.
     *
     * <p>The {@code state} attribute serializes to {@code begin} when {@link #began()} is
     * {@code true} and {@code end} otherwise.
     *
     * @return the interruption action stanza
     */
    @Override
    public Stanza toStanza() {
        return CallMessages.stampHeader(new StanzaBuilder().description(ELEMENT), callId, callCreator)
                .attribute(STATE_ATTRIBUTE, began ? STATE_BEGIN : STATE_END)
                .attribute(TYPE_ATTRIBUTE, interruptionType)
                .build();
    }

    /**
     * Decodes an {@code <interruption>} action stanza into an {@link InterruptionStanza}.
     *
     * <p>The {@code state} attribute is interpreted as {@link #began()} {@code true} only when it
     * equals the literal {@code begin}; any other value, including an absent attribute, decodes as
     * {@code false}. An absent {@code type} decodes to {@code 0}.
     *
     * @param stanza the {@code <interruption>} stanza
     * @return the decoded interruption action
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent
     */
    public static InterruptionStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var began = STATE_BEGIN.equals(stanza.getAttributeAsString(STATE_ATTRIBUTE, STATE_END));
        var interruptionType = stanza.getAttributeAsInt(TYPE_ATTRIBUTE, 0);
        return new InterruptionStanza(callId, callCreator, began, interruptionType);
    }
}
