package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Represents a {@code <heartbeat>} signal: a periodic group-call membership keepalive.
 *
 * <p>Each active participant of a group call emits a heartbeat on a fixed cadence so the call server
 * keeps the participant's membership fresh and does not evict it. It carries only the universal call
 * header, the call identifier and the call creator, the same two attributes
 * {@code populate_common_call_attr} stamps on every action element, and has no further attributes or
 * nested tree.
 *
 * <p>On the wire the element is {@code <heartbeat call-id="..." call-creator="..."/>}.
 *
 * @implNote This implementation models the {@code <heartbeat>} element of the wa-voip WASM module
 * {@code ff-tScznZ8P} that {@code make_and_send_heartbeat} (fn10936, {@code call_timer.cc}) emits as
 * message type {@code 29}. It is built with only the common header stamped by
 * {@code populate_common_call_attr} (fn11591): the {@code call-id} attribute and the
 * {@code call-creator} attribute, matching the captured shape
 * ({@code <heartbeat call-id="0023CDF8FF5621A306A3337E63C9F0B5" call-creator="...@lid"/>},
 * re/calls2-spec/captures/group-stanzas-peer.jsonl).
 *
 * @param callId      the call identifier; never {@code null}
 * @param callCreator the call creator's device JID; never {@code null}
 * @see Calls2SignalingType#HEARTBEAT
 */
public record HeartbeatStanza(String callId, Jid callCreator) implements CallMessage {
    /**
     * The wire element tag for a heartbeat signal.
     */
    public static final String ELEMENT = "heartbeat";

    /**
     * Validates the record components.
     *
     * @throws NullPointerException if {@code callId} or {@code callCreator} is {@code null}
     */
    public HeartbeatStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>A heartbeat carries the {@code message_type} ordinal {@code 29} in the numeric
     * {@code voip_signaling_message_type} table.
     *
     * @return {@link Calls2SignalingType#HEARTBEAT}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.HEARTBEAT;
    }

    /**
     * Builds the {@code <heartbeat call-id call-creator/>} action stanza.
     *
     * @return the heartbeat action stanza
     */
    @Override
    public Stanza toStanza() {
        return CallMessages.stampHeader(new StanzaBuilder().description(ELEMENT), callId, callCreator)
                .build();
    }

    /**
     * Decodes a {@code <heartbeat>} action stanza into a {@link HeartbeatStanza}.
     *
     * @param stanza the {@code <heartbeat>} stanza
     * @return the decoded heartbeat signal
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent
     */
    public static HeartbeatStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        return new HeartbeatStanza(callId, callCreator);
    }
}
