package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Represents a {@code <ringing>} signal: the callee's device is alerting the local user.
 *
 * <p>A ringing signal is a content-less acknowledgement the callee emits when it begins alerting the
 * user, before any pre-accept or accept. It carries only the universal call header, the call
 * identifier, and the call creator, the same two attributes {@code populate_common_call_attr} stamps
 * on every action element. Unlike most signaling actions it has no entry in the numeric
 * {@code voip_signaling_message_type} table; it is a thin status element keyed on its wire tag.
 *
 * <p>On the wire the element is {@code <ringing call-id="..." call-creator="..."/>}.
 *
 * @implNote This implementation models the {@code <ringing>} element of the wa-voip WASM module
 * {@code ff-tScznZ8P}. It carries no message-type ordinal in the eighty-five-entry taxonomy and is
 * built with only the common header stamped by {@code populate_common_call_attr} (fn11591): the
 * {@code call-id} attribute (data offset {@code 0x888f9}) and the {@code call-creator} attribute
 * (data offset {@code 0x45ea5}).
 *
 * @param callId      the call identifier; never {@code null}
 * @param callCreator the call creator's device JID; never {@code null}
 * @see Calls2SignalingType
 */
public record RingingStanza(String callId, Jid callCreator) implements CallMessage {
    /**
     * The wire element tag for a ringing signal.
     */
    public static final String ELEMENT = "ringing";

    /**
     * Validates the record components.
     *
     * @throws NullPointerException if {@code callId} or {@code callCreator} is {@code null}
     */
    public RingingStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>A ringing signal has no entry in the numeric {@code voip_signaling_message_type} table, so
     * this projection has no {@link Calls2SignalingType} and the method returns {@code null}; the
     * element is dispatched on its {@link #ELEMENT wire tag} instead.
     *
     * @return {@code null}, since ringing carries no taxonomy ordinal
     */
    @Override
    public Calls2SignalingType type() {
        return null;
    }

    /**
     * Builds the {@code <ringing call-id call-creator/>} action stanza.
     *
     * @return the ringing action stanza
     */
    @Override
    public Stanza toStanza() {
        return CallMessages.stampHeader(new StanzaBuilder().description(ELEMENT), callId, callCreator)
                .build();
    }

    /**
     * Decodes a {@code <ringing>} action stanza into a {@link RingingStanza}.
     *
     * @param stanza the {@code <ringing>} stanza
     * @return the decoded ringing signal
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent
     */
    public static RingingStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        return new RingingStanza(callId, callCreator);
    }
}
