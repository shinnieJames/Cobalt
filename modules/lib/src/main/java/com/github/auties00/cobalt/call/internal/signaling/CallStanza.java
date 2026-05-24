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
 * Stanza-building helpers for the VoIP signalling layer.
 *
 * <p>Every method returns a fully-formed {@code <call to="..." id?="...">...</call>}
 * envelope ready for dispatch through
 * {@link com.github.auties00.cobalt.client.WhatsAppClient#sendNodeWithNoResponse(Node)}.
 * The {@code id} attribute is left for the dispatcher to assign (matching
 * {@code WAWebVoipSendSignalingXmpp}'s use of {@code WAWap.generateId}).
 *
 * <p>The wire format mirrors {@code WAWebVoipSendSignalingXmpp}'s output and
 * the live captures gathered against the production WhatsApp Web client. Note
 * that the offer this class builds intentionally omits the per-device
 * {@code <destination>} fanout with Signal {@code pkmsg} payloads, the
 * {@code <device-identity>} envelope, and the {@code <encopt>}/{@code <privacy>}
 * children: those carry data produced by the WebRTC media plane, which Cobalt
 * does not implement. The resulting offer is a signalling-only announcement,
 * the same shape the previous in-line WhatsAppClient builders emitted, just
 * factored out so it can be reused and extended without touching that class.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSendSignalingXmpp")
public final class CallStanza {
    /**
     * Hidden constructor — this class is a static helper.
     */
    private CallStanza() {
        throw new AssertionError("CallStanza is not instantiable");
    }

    /**
     * Builds a {@code <call><offer call-id call-creator [group-jid]>...</offer></call>}
     * stanza for an outgoing 1:1 or group call.
     *
     * <p>The optional {@code participants} are placed inside a
     * {@code <group_info>} child mirroring the format
     * {@link CallReceiver} parses on the receive side. When the call is a
     * video call, an empty {@code <video/>} marker is included.
     *
     * @param target       the JID the {@code <call to>} attribute is set to
     *                     (the peer for 1:1 calls, the group JID for group
     *                     calls); must not be {@code null}
     * @param creator      the local user's device JID, placed on
     *                     {@code call-creator}; must not be {@code null}
     * @param callId       the locally-generated call identifier; must not be
     *                     {@code null}
     * @param video        whether to advertise this as a video call
     * @param groupJid     the group JID for group calls, or {@code null} for
     *                     1:1 calls
     * @param participants the participants to invite for a group call; ignored
     *                     when {@code groupJid} is {@code null}
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code target}, {@code creator}, or
     *                              {@code callId} is {@code null}
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
     * Builds a {@code <call><preaccept call-id call-creator/></call>} stanza.
     *
     * <p>WA Web emits this as soon as its receive-side
     * {@code WAWebHandleVoipCall} pipeline has validated the incoming offer
     * and decided that the ringing UI should be shown to the user, but
     * before the user has actually answered.
     *
     * @param caller the JID of the call creator; routed back as
     *               {@code <call to>} and as {@code call-creator}; must not
     *               be {@code null}
     * @param callId the call identifier from the original offer; must not be
     *               {@code null}
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code caller} or {@code callId} is
     *                              {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Node preaccept(Jid caller, String callId) {
        return simple(caller, callId, CallSignalingType.PREACCEPT.wireTag());
    }

    /**
     * Builds a {@code <call><accept call-id call-creator/></call>} stanza.
     *
     * @param caller the JID of the call creator; must not be {@code null}
     * @param callId the call identifier; must not be {@code null}
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code caller} or {@code callId} is
     *                              {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Node accept(Jid caller, String callId) {
        return simple(caller, callId, CallSignalingType.ACCEPT.wireTag());
    }

    /**
     * Builds a {@code <call><reject call-id call-creator/></call>} stanza.
     *
     * @param caller the JID of the call creator; must not be {@code null}
     * @param callId the call identifier; must not be {@code null}
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code caller} or {@code callId} is
     *                              {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Node reject(Jid caller, String callId) {
        return simple(caller, callId, CallSignalingType.REJECT.wireTag());
    }

    /**
     * Builds a {@code <call><terminate reason call-id call-creator/></call>}
     * stanza.
     *
     * <p>The {@code reason} attribute carries the
     * {@link CallEndReason#wireValue()} of the chosen end reason. The
     * {@code <call to>} attribute is the other party (the JID the local
     * user is talking to), and {@code call-creator} is whichever JID
     * actually started the call (which may be the local user or the peer).
     *
     * @param target       the JID of the other party — placed on
     *                     {@code <call to>}; must not be {@code null}
     * @param creator      the JID of the call creator — placed on
     *                     {@code call-creator}; must not be {@code null}
     * @param callId       the call identifier; must not be {@code null}
     * @param reason       the end reason; must not be {@code null}
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
     * Builds a {@code <call><mute_v2 call-id call-creator mute-state="0|1"/></call>}
     * stanza announcing that the local user has muted or unmuted their mic.
     *
     * <p>Wire shape verified against captured fixtures (see
     * {@code fixtures/call/1to1/mute-toggle.*.jsonl}): the attribute is
     * {@code mute-state} with values {@code "1"} for muted and {@code "0"}
     * for unmuted — not the legacy {@code state="muted"|"unmuted"} the
     * older WA Web snapshot used.
     *
     * @param target  the JID of the other party; must not be {@code null}
     * @param creator the JID of the call creator; must not be {@code null}
     * @param callId  the call identifier; must not be {@code null}
     * @param muted   {@code true} if announcing a mute, {@code false} for
     *                an unmute
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code target}, {@code creator}, or
     *                              {@code callId} is {@code null}
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
     * Builds a {@code <call><video_state call-id call-creator state=...></call>}
     * stanza announcing that the local user has turned video on or off.
     *
     * @param target  the JID of the other party; must not be {@code null}
     * @param creator the JID of the call creator; must not be {@code null}
     * @param callId  the call identifier; must not be {@code null}
     * @param enabled {@code true} if announcing a video-on,
     *                {@code false} for video-off
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code target}, {@code creator}, or
     *                              {@code callId} is {@code null}
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
     * Builds a {@code <call><group_update call-id call-creator action=...></call>}
     * stanza adding or removing participants from an in-progress group call.
     *
     * @param target       the group JID; must not be {@code null}
     * @param creator      the JID of the call creator; must not be
     *                     {@code null}
     * @param callId       the call identifier; must not be {@code null}
     * @param add          {@code true} if announcing additions,
     *                     {@code false} for removals
     * @param participants the participants to add or remove; must not be
     *                     {@code null} or empty
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException     if any reference argument is
     *                                  {@code null}
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
     * Builds a {@code <call><ringing call-id call-creator/></call>} stanza
     * confirming the device is alerting the local user.
     *
     * @param caller the JID of the call creator; must not be {@code null}
     * @param callId the call identifier; must not be {@code null}
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code caller} or {@code callId} is
     *                              {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Node ringing(Jid caller, String callId) {
        return simple(caller, callId, "ringing");
    }

    /**
     * Builds a payload-less {@code <call to=...><tag call-id call-creator/></call>}
     * envelope.
     *
     * @param caller the JID of the call creator
     * @param callId the call identifier
     * @param tag    the wire tag for the payload (e.g. {@code "accept"})
     * @return the wrapped stanza
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
     * Wraps the given payload in a {@code <call to=target>...</call>}
     * envelope.
     *
     * @param target  the {@code to} attribute
     * @param payload the inner payload
     * @return the wrapped stanza
     */
    private static Node wrap(Jid target, Node payload) {
        return new NodeBuilder()
                .description("call")
                .attribute("to", target)
                .content(payload)
                .build();
    }
}
