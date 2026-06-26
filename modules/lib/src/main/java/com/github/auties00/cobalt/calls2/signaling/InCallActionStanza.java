package com.github.auties00.cobalt.calls2.signaling;

/**
 * Represents an in-call control action of the wa-voip {@code <call>} signaling plane.
 *
 * <p>An in-call action mutates a call that is already connected rather than driving its setup or
 * teardown: muting a participant, raising a hand, toggling video or screen share, reporting a peer
 * state, flow-controlling the video bitrate, sending a DTMF tone, reconfiguring a bot, or attaching a
 * call extension. These actions form a distinct family from the lifecycle messages (offer, accept,
 * reject, terminate) and the control-plane messages (group, link, waiting room), and this sealed
 * sub-interface groups them so a consumer can exhaustively switch over the in-call actions alone.
 *
 * <p>This interface extends {@link CallMessage} and adds no operations of its own: an in-call action is
 * a {@link CallMessage} like any other action element, carried as one child of the {@code <call>}
 * envelope, decoded and encoded through {@link Calls2CallStanza}, and forwarded through the same
 * receiver sink. The separation is purely for exhaustive pattern matching over the in-call subset; the
 * {@link CallMessage#type()} and {@link CallMessage#toStanza()} contract is inherited unchanged.
 *
 * <p>Several in-call actions do not ride a dedicated {@code <call>} child of their own but are carried
 * inside a shared message container (the {@code 0x68}, {@code 0x6c}, {@code 0x70}, and {@code 0x74}
 * containers in the engine), and a couple ({@link RaiseHandStanza}) carry no entry in the numeric
 * {@code voip_signaling_message_type} table at all, so their {@link CallMessage#type()} is {@code null}
 * and they are routed by their wire tag. The grouping here is by control semantics, not by container or
 * taxonomy presence.
 *
 * @implNote This implementation models the in-call action serializers of the wa-voip WASM module
 * {@code ff-tScznZ8P} ({@code protocol/xmpp/stanzas/in_call_actions.cc}): each permitted record ports
 * one {@code serialize_*}/{@code deserialize_*} pair for an action carried inside one of the in-call
 * message containers. The interface is a marker sub-hierarchy of {@link CallMessage} and declares no
 * members because the engine treats these actions as ordinary {@code wa_call_message} instances on the
 * wire; the Cobalt split exists only to make the in-call control surface exhaustively matchable.
 *
 * @see CallMessage
 * @see Calls2CallStanza
 */
public sealed interface InCallActionStanza extends CallMessage permits
        DtmfStanza,
        ExtensionStanza,
        FlowControlStanza,
        InterruptionStanza,
        MuteV2Stanza,
        NotifyStanza,
        PeerStateStanza,
        RaiseHandStanza,
        ReconfigureBotStanza,
        ScreenShareStanza,
        VideoStateStanza {
}
