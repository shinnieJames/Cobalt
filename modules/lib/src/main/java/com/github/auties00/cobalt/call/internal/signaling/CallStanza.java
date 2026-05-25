package com.github.auties00.cobalt.call.internal.signaling;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import com.github.auties00.cobalt.call.CallEndReason;

/**
 * Builds the {@code <call>} stanzas the VoIP signalling layer sends to the server.
 *
 * <p>Every factory method returns a complete {@code <call to="...">...</call>} envelope ready for
 * dispatch through {@link com.github.auties00.cobalt.client.WhatsAppClient#sendNodeWithNoResponse(Node)}.
 * The {@code id} attribute is left unset for the dispatcher to assign. The offer this class builds is
 * a signalling-only announcement: it deliberately omits the per-device fanout with Signal
 * {@code pkmsg} payloads, the device-identity envelope, and the encryption-option and privacy
 * children, all of which carry data produced by the WebRTC media plane that Cobalt does not implement.
 * The receive-side counterpart that parses these stanzas is {@link CallReceiver}.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSendSignalingXmpp")
public final class CallStanza {
    /**
     * Prevents instantiation of this static helper.
     *
     * @throws AssertionError always, since this class is not instantiable
     */
    private CallStanza() {
        throw new AssertionError("CallStanza is not instantiable");
    }

    /**
     * Builds an offer stanza for an outgoing one-to-one or group call.
     *
     * <p>The returned envelope wraps an {@code <offer call-id call-creator [group-jid]>} payload. When
     * {@code groupJid} is non-{@code null} and {@code participants} is non-empty, each participant is
     * emitted as a {@code <participant jid="..."/>} entry inside a {@code <group_info>} child, matching
     * the shape {@link CallReceiver} parses on the receive side. When {@code video} is {@code true}, an
     * empty {@code <video/>} marker is added. The {@code group-jid} attribute is present only for group
     * calls.
     *
     * @param target       the value of the {@code <call to>} attribute (the peer for one-to-one calls,
     *                     the group JID for group calls); must not be {@code null}
     * @param creator      the local user's device JID, placed on {@code call-creator}; must not be
     *                     {@code null}
     * @param callId       the locally generated call identifier; must not be {@code null}
     * @param video        whether to advertise this as a video call
     * @param groupJid     the group JID for group calls, or {@code null} for one-to-one calls
     * @param participants the participants to invite for a group call; ignored when {@code groupJid} is
     *                     {@code null}
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code target}, {@code creator}, or {@code callId} is
     *                              {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Node offer(Jid target, Jid creator, String callId, boolean video, Jid groupJid, Collection<Jid> participants) {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(creator, "creator cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        var offerChildren = new ArrayList<Node>(2);
        if (groupJid != null && participants != null && !participants.isEmpty()) {
            var participantNodes = new ArrayList<Node>(participants.size());
            for (var participant : participants) {
                participantNodes.add(new NodeBuilder()
                        .description("participant")
                        .attribute("jid", participant)
                        .build());
            }
            offerChildren.add(new NodeBuilder()
                    .description("group_info")
                    .content(participantNodes)
                    .build());
        }
        if (video) {
            offerChildren.add(new NodeBuilder()
                    .description("video")
                    .build());
        }
        var offerBuilder = new NodeBuilder()
                .description(CallSignalingType.OFFER.wireTag())
                .attribute("call-id", callId)
                .attribute("call-creator", creator);
        if (groupJid != null) {
            offerBuilder.attribute("group-jid", groupJid);
        }
        offerBuilder.content(offerChildren);
        return wrap(target, offerBuilder.build());
    }

    /**
     * Builds a pre-accept stanza.
     *
     * <p>The returned envelope wraps a {@code <preaccept call-id call-creator/>} payload. This stanza
     * is sent once the incoming offer has been validated and the device decides to show the ringing UI,
     * but before the user has answered.
     *
     * @param caller the JID of the call creator; routed back as both the {@code <call to>} attribute
     *               and {@code call-creator}; must not be {@code null}
     * @param callId the call identifier from the original offer; must not be {@code null}
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code caller} or {@code callId} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Node preaccept(Jid caller, String callId) {
        return simple(caller, callId, CallSignalingType.PREACCEPT.wireTag());
    }

    /**
     * Builds an accept stanza.
     *
     * <p>The returned envelope wraps an {@code <accept call-id call-creator/>} payload.
     *
     * @param caller the JID of the call creator; must not be {@code null}
     * @param callId the call identifier; must not be {@code null}
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code caller} or {@code callId} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Node accept(Jid caller, String callId) {
        return simple(caller, callId, CallSignalingType.ACCEPT.wireTag());
    }

    /**
     * Builds a reject stanza.
     *
     * <p>The returned envelope wraps a {@code <reject call-id call-creator/>} payload.
     *
     * @param caller the JID of the call creator; must not be {@code null}
     * @param callId the call identifier; must not be {@code null}
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code caller} or {@code callId} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Node reject(Jid caller, String callId) {
        return simple(caller, callId, CallSignalingType.REJECT.wireTag());
    }

    /**
     * Builds a terminate stanza.
     *
     * <p>The returned envelope wraps a {@code <terminate reason call-id call-creator/>} payload. The
     * {@code reason} attribute carries the {@link CallEndReason#wireValue()} of the chosen end reason.
     * The {@code <call to>} attribute is the other party, while {@code call-creator} is whichever party
     * started the call, which may be the local user or the peer.
     *
     * @param target  the JID of the other party, placed on {@code <call to>}; must not be {@code null}
     * @param creator the JID of the call creator, placed on {@code call-creator}; must not be
     *                {@code null}
     * @param callId  the call identifier; must not be {@code null}
     * @param reason  the end reason; must not be {@code null}
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Node terminate(Jid target, Jid creator, String callId, CallEndReason reason) {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(creator, "creator cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        var payload = new NodeBuilder()
                .description(CallSignalingType.TERMINATE.wireTag())
                .attribute("reason", reason.wireValue())
                .attribute("call-id", callId)
                .attribute("call-creator", creator)
                .build();
        return wrap(target, payload);
    }

    /**
     * Builds a mute-state stanza announcing that the local user has muted or unmuted their microphone.
     *
     * <p>The returned envelope wraps a {@code <mute_v2 call-id call-creator mute-state="0|1"/>} payload,
     * with {@code mute-state} set to {@code "1"} for a mute and {@code "0"} for an unmute.
     *
     * @implNote This implementation emits the {@code mute-state} attribute on a {@code mute_v2} payload
     * rather than the legacy {@code state="muted"|"unmuted"} attribute on a {@code mute} payload; the
     * captured {@code fixtures/call/1to1/mute-toggle.*.jsonl} corpus confirms the current shape.
     *
     * @param target  the JID of the other party; must not be {@code null}
     * @param creator the JID of the call creator; must not be {@code null}
     * @param callId  the call identifier; must not be {@code null}
     * @param muted   {@code true} to announce a mute, {@code false} to announce an unmute
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code target}, {@code creator}, or {@code callId} is
     *                              {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Node mute(Jid target, Jid creator, String callId, boolean muted) {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(creator, "creator cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        var payload = new NodeBuilder()
                .description(CallSignalingType.MUTE.wireTag())
                .attribute("call-id", callId)
                .attribute("call-creator", creator)
                .attribute("mute-state", muted ? "1" : "0")
                .build();
        return wrap(target, payload);
    }

    /**
     * Builds a video-state stanza announcing that the local user has turned video on or off.
     *
     * <p>The returned envelope wraps a {@code <video_state call-id call-creator state="on|off"/>}
     * payload, with {@code state} set to {@code "on"} for a video-on and {@code "off"} for a video-off.
     *
     * @param target  the JID of the other party; must not be {@code null}
     * @param creator the JID of the call creator; must not be {@code null}
     * @param callId  the call identifier; must not be {@code null}
     * @param enabled {@code true} to announce a video-on, {@code false} to announce a video-off
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code target}, {@code creator}, or {@code callId} is
     *                              {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Node videoState(Jid target, Jid creator, String callId, boolean enabled) {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(creator, "creator cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        var payload = new NodeBuilder()
                .description(CallSignalingType.VIDEO_STATE.wireTag())
                .attribute("call-id", callId)
                .attribute("call-creator", creator)
                .attribute("state", enabled ? "on" : "off")
                .build();
        return wrap(target, payload);
    }

    /**
     * Builds a group-update stanza that adds or removes participants in an in-progress group call.
     *
     * <p>The returned envelope wraps a {@code <group_update call-id call-creator action="add|remove">}
     * payload whose {@code <group_info>} child lists one {@code <participant jid="..."/>} entry per
     * supplied participant. The {@code action} attribute is {@code "add"} when {@code add} is
     * {@code true} and {@code "remove"} otherwise.
     *
     * @param target       the group JID; must not be {@code null}
     * @param creator      the JID of the call creator; must not be {@code null}
     * @param callId       the call identifier; must not be {@code null}
     * @param add          {@code true} to announce additions, {@code false} to announce removals
     * @param participants the participants to add or remove; must not be {@code null} or empty
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException     if any reference argument is {@code null}
     * @throws IllegalArgumentException if {@code participants} is empty
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Node groupUpdate(Jid target, Jid creator, String callId, boolean add, Collection<Jid> participants) {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(creator, "creator cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(participants, "participants cannot be null");
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("participants cannot be empty");
        }
        var participantNodes = new ArrayList<Node>(participants.size());
        for (var participant : participants) {
            participantNodes.add(new NodeBuilder()
                    .description("participant")
                    .attribute("jid", participant)
                    .build());
        }
        var groupInfo = new NodeBuilder()
                .description("group_info")
                .content(participantNodes)
                .build();
        var payload = new NodeBuilder()
                .description(CallSignalingType.GROUP_UPDATE.wireTag())
                .attribute("call-id", callId)
                .attribute("call-creator", creator)
                .attribute("action", add ? "add" : "remove")
                .content(List.of(groupInfo))
                .build();
        return wrap(target, payload);
    }

    /**
     * Builds a ringing stanza confirming the device is alerting the local user.
     *
     * <p>The returned envelope wraps a {@code <ringing call-id call-creator/>} payload.
     *
     * @param caller the JID of the call creator; must not be {@code null}
     * @param callId the call identifier; must not be {@code null}
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code caller} or {@code callId} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Node ringing(Jid caller, String callId) {
        return simple(caller, callId, "ringing");
    }

    /**
     * Builds a content-less call payload carrying only the call-id and call-creator attributes.
     *
     * <p>The returned envelope wraps a {@code <tag call-id call-creator/>} payload, where {@code tag}
     * is the supplied wire tag, and routes it to {@code caller} as the {@code <call to>} attribute.
     *
     * @param caller the JID of the call creator, also used as the {@code <call to>} target
     * @param callId the call identifier
     * @param tag    the wire tag for the payload, for example {@code "accept"}
     * @return the wrapped {@code <call>} stanza
     * @throws NullPointerException if {@code caller} or {@code callId} is {@code null}
     */
    private static Node simple(Jid caller, String callId, String tag) {
        Objects.requireNonNull(caller, "caller cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        var payload = new NodeBuilder()
                .description(tag)
                .attribute("call-id", callId)
                .attribute("call-creator", caller)
                .build();
        return wrap(caller, payload);
    }

    /**
     * Wraps a payload in a {@code <call to="...">...</call>} envelope.
     *
     * @param target  the value of the {@code to} attribute
     * @param payload the inner payload node
     * @return the wrapped {@code <call>} stanza
     */
    private static Node wrap(Jid target, Node payload) {
        return new NodeBuilder()
                .description("call")
                .attribute("to", target)
                .content(payload)
                .build();
    }
}
