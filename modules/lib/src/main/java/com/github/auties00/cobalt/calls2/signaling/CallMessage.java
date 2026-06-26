package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.stanza.Stanza;

/**
 * Represents one decoded action element of the wa-voip {@code <call>} signaling plane.
 *
 * <p>Every call action travels as exactly one child element inside a top-level {@code <call>} stanza.
 * This sealed interface is the common type of every such action: each implementing record is the typed,
 * decoded form of one child element, holds the action's attributes and nested tree as Java fields, and
 * knows both how to render itself back to a {@link Stanza} ({@link #toStanza()}) and which taxonomy entry it
 * belongs to ({@link #type()}). The {@link Calls2CallStanza} factory wraps an instance in the
 * {@code <call>} envelope on the send side and dispatches an inbound child element to the matching
 * record on the receive side.
 *
 * <p>The hierarchy has two tiers. The action-bearing signaling messages, the offer, accept, preaccept,
 * reject, terminate, transport, relay-latency, group and rekey messages, the call-link control plane,
 * and the waiting-room control plane, implement this interface directly. The in-call control actions,
 * which mutate a connected call rather than drive its setup, are grouped under the
 * {@link InCallActionStanza} sub-interface, which is itself one of the permitted subtypes; an
 * {@link InCallActionStanza} is therefore also a {@link CallMessage} and flows through the same envelope
 * codec and the same receiver sink.
 *
 * <p>{@link #type()} is the projection onto the eighty-five-entry {@link Calls2SignalingType} taxonomy,
 * but it is deliberately nullable: a handful of action elements ({@link RingingStanza},
 * {@link RaiseHandStanza}) name a {@code <call>} child element yet carry no entry in the numeric
 * {@code voip_signaling_message_type} table, so they report {@code null} and are routed by their wire
 * tag alone. Inbound dispatch in {@link Calls2CallStanza#parse(Stanza)} therefore keys on the child
 * element tag through {@link Stanza#description()}, not on {@link #type()}, and {@link #type()} is a
 * convenience for callers that classify an already-decoded message.
 *
 * @implNote This implementation models the flattened {@code wa_call_message} of the wa-voip WASM module
 * {@code ff-tScznZ8P}: the native engine parses each {@code <call>} child element into a struct whose
 * first word is the numeric {@code message_type} the dispatcher branches on. Cobalt keeps the typed
 * decoded form as a record per action rather than a single flat C struct, and reuses {@code stanza/binary}
 * verbatim for the wire bytes, so this interface carries only the two operations every action needs:
 * {@link #toStanza()} (the {@code serialize_*} side, over the common header stamped by
 * {@code populate_common_call_attr} fn11591) and {@link #type()} (the projection onto the
 * {@code voip_signaling_message_type} taxonomy table at WASM data offset {@code 0x1291ac}).
 *
 * @see Calls2CallStanza
 * @see Calls2SignalingType
 * @see InCallActionStanza
 */
public sealed interface CallMessage permits
        AcceptStanza,
        DestinationStanza,
        GroupInfoStanza,
        GroupUpdateStanza,
        HeartbeatStanza,
        InCallActionStanza,
        LinkCreateStanza,
        LinkEditStanza,
        LinkJoinStanza,
        LinkQueryStanza,
        OfferStanza,
        PreacceptStanza,
        RejectStanza,
        RekeyStanza,
        RelayLatencyStanza,
        RingingStanza,
        TerminateStanza,
        TransportStanza,
        WaitingRoomAdmitStanza,
        WaitingRoomDenyStanza,
        WaitingRoomLeaveStanza,
        WaitingRoomToggleStanza,
        WaitingRoomUpdateStanza {
    /**
     * Returns the signaling taxonomy entry this message projects onto.
     *
     * <p>The result is the {@link Calls2SignalingType} whose numeric id the wa-voip engine dispatches
     * on for this action, or {@code null} for the few action elements that name a {@code <call>} child
     * element but carry no entry in the numeric {@code voip_signaling_message_type} table (notably
     * {@link RingingStanza} and {@link RaiseHandStanza}). Callers that route inbound messages must key
     * on the wire child tag through {@link Calls2CallStanza#parse(Stanza)} rather than on this projection,
     * because a {@code null} result is not routable; this accessor exists for callers that classify or
     * log an already-decoded message.
     *
     * @return the matching {@link Calls2SignalingType}, or {@code null} when this message carries no
     *         taxonomy ordinal
     */
    Calls2SignalingType type();

    /**
     * Renders this message to its {@code <call>} child element {@link Stanza}.
     *
     * <p>The returned stanza is the action element only: it carries the action's wire tag, its universal
     * {@code call-id} and {@code call-creator} header, its type-specific attributes, and its nested tree,
     * but not the surrounding {@code <call>} envelope. Wrapping it in the envelope with the recipient and
     * the dispatcher-assigned stanza id is {@link Calls2CallStanza#toCall(CallMessage, com.github.auties00.cobalt.model.jid.Jid, String)}'s
     * responsibility, so the same action stanza can be addressed to different recipients without rebuilding
     * it.
     *
     * @return the action element stanza; never {@code null}
     */
    Stanza toStanza();
}
