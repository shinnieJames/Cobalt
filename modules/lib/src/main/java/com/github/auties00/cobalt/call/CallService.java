package com.github.auties00.cobalt.call;

import com.github.auties00.cobalt.call.signaling.CallRelay;
import com.github.auties00.cobalt.call.stream.AudioInputStream;
import com.github.auties00.cobalt.call.stream.AudioOutputStream;
import com.github.auties00.cobalt.call.stream.VideoInputStream;
import com.github.auties00.cobalt.call.stream.VideoOutputStream;
import com.github.auties00.cobalt.model.call.Call;
import com.github.auties00.cobalt.model.call.CallEndReason;
import com.github.auties00.cobalt.model.call.CallInteraction;
import com.github.auties00.cobalt.model.call.CallState;
import com.github.auties00.cobalt.model.call.IncomingCall;
import com.github.auties00.cobalt.call.signaling.CallReceiver;
import com.github.auties00.cobalt.call.signaling.CallStanza;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;

import java.util.Set;

/**
 * Coordinates one client's call activity.
 *
 * <p>This service owns the registry of in-flight {@link Call} sessions and exposes the
 * call-control entry points used by {@link LinkedWhatsAppClient}:
 * {@link #placeCall(Jid, AudioOutputStream, AudioInputStream, VideoOutputStream, VideoInputStream)}
 * for outbound calls, and
 * {@link #accept(IncomingCall, AudioOutputStream, AudioInputStream, VideoOutputStream, VideoInputStream)}
 * and {@link #reject(IncomingCall, CallEndReason)} for inbound offers. It sits between the public
 * client API and listener surface above and the signaling classes ({@link CallReceiver},
 * {@link CallStanza}) and transport and media layers below. There is one instance per
 * {@link LinkedWhatsAppClient}.
 *
 * <p>The service implements the signaling and state-machine portion of a call: it produces
 * {@link Call} instances that own their lifecycle and react to peer-side state changes routed
 * back through {@link #onPeerAccept(String)}, {@link #onPeerReject(String, String)}, and
 * {@link #onPeerTerminate(String, String)}.
 *
 * @implSpec
 * Implementations must track each placed or accepted call in a registry keyed by call id and must
 * route peer-side state changes to the matching {@link Call}. The video-state family
 * ({@link #sendVideoUpgradeRequest(Jid, Jid, String)} and
 * {@link #sendVideoUpgradeReject(Jid, Jid, String)}) should funnel through
 * {@link #sendVideoState(Jid, Jid, String, boolean)} so one override intercepts all of them.
 */
public interface CallService {
    /**
     * Places an outbound call to {@code peer} carrying the given media streams and returns its live
     * session.
     *
     * <p>A fresh call identifier is generated, an {@link Call} is registered in the in-flight registry
     * with the supplied streams along with a caller-side telemetry accumulator, and the offer stanza is
     * sent. The call is a video call when {@code videoOut} is non-{@code null} and audio-only otherwise.
     * The session is parked in {@link CallState#CONNECTING} until the peer's acceptance arrives through
     * {@link #onPeerAccept(String)}.
     *
     * <p>This is the one-to-one entry point; {@code peer} must be a user JID. A group or community JID
     * is rejected, since a group call must be placed through
     * {@link #placeGroupCall(Set, Jid, AudioOutputStream, AudioInputStream, VideoOutputStream, VideoInputStream)},
     * which builds the group offer and never issues the one-to-one trusted-contact privacy token.
     *
     * @implSpec
     * Implementations must register the session before sending the offer.
     *
     * @param peer     the {@link Jid} of the callee; must be a user JID
     * @param audioOut the stream the engine drains local audio from for transmission
     * @param audioIn  the stream the engine fills with received remote audio
     * @param videoOut the stream the engine drains local video from for transmission, or {@code null}
     *                 for an audio-only call
     * @param videoIn  the stream the engine fills with received remote video, or {@code null} for an
     *                 audio-only call
     * @return the live session
     * @throws NullPointerException     if {@code peer}, {@code audioOut}, or {@code audioIn} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code peer} is a group or community JID
     * @throws IllegalStateException    if the client is not logged in
     */
    Call placeCall(Jid peer, AudioOutputStream audioOut, AudioInputStream audioIn,
                   VideoOutputStream videoOut, VideoInputStream videoIn);

    /**
     * Places an outbound group call into {@code groupJid}, fanning the offer out to every
     * {@code peer} in {@code peers}, and returns its live session.
     *
     * <p>A fresh call identifier is generated and one {@link Call} is registered for the whole call
     * with the supplied streams. The call is a video call when {@code videoOut} is non-{@code null} and
     * audio-only otherwise. The same per-call shared key is encrypted per-device of every peer and
     * shipped as one {@code <call to="peer">} stanza per peer; every stanza shares the call-id and
     * call-creator so the relay binds them to one SFU allocation.
     *
     * @implSpec
     * Implementations must register one session keyed by the generated call-id and must send one
     * offer stanza per peer before returning. The session's {@link Call#chatJid()} is the
     * group; mid-call control plane goes through the call's DataChannel rather than per-peer XMPP.
     *
     * @param peers    the user JIDs of every other group participant (the local user is excluded)
     * @param groupJid the group {@link Jid}
     * @param audioOut the stream the engine drains local audio from for transmission
     * @param audioIn  the stream the engine fills with received remote audio
     * @param videoOut the stream the engine drains local video from for transmission, or {@code null}
     *                 for an audio-only call
     * @param videoIn  the stream the engine fills with received remote video, or {@code null} for an
     *                 audio-only call
     * @return the live session
     * @throws NullPointerException     if {@code peers}, {@code groupJid}, {@code audioOut}, or
     *                                  {@code audioIn} is {@code null}
     * @throws IllegalArgumentException if {@code peers} is empty
     * @throws IllegalStateException    if the client is not logged in
     */
    Call placeGroupCall(Set<Jid> peers, Jid groupJid, AudioOutputStream audioOut, AudioInputStream audioIn,
                        VideoOutputStream videoOut, VideoInputStream videoIn);

    /**
     * Accepts an inbound call offer with the given media streams and returns its live session.
     *
     * <p>An {@link Call} is registered with the supplied streams along with a callee-side telemetry
     * accumulator, the accept stanza is sent, and the session is parked in
     * {@link CallState#CONNECTING}. The answered leg is a video call when {@code videoOut} is
     * non-{@code null} and audio-only otherwise. This method is invoked after the one-shot guard on the
     * offer has been claimed.
     *
     * @implSpec
     * Implementations must register the session before sending the accept stanza.
     *
     * @param offer    the offer being accepted
     * @param audioOut the stream the engine drains local audio from for transmission
     * @param audioIn  the stream the engine fills with received remote audio
     * @param videoOut the stream the engine drains local video from for transmission, or {@code null}
     *                 to answer audio-only
     * @param videoIn  the stream the engine fills with received remote video, or {@code null} to answer
     *                 audio-only
     * @return the live session
     * @throws NullPointerException if {@code offer}, {@code audioOut}, or {@code audioIn} is
     *                              {@code null}
     */
    Call accept(IncomingCall offer, AudioOutputStream audioOut, AudioInputStream audioIn,
                VideoOutputStream videoOut, VideoInputStream videoIn);

    /**
     * Stashes the parsed relay block and decrypted call key from an inbound offer, keyed by call id,
     * so the later accept() can bring up the media plane (the slim IncomingCall model cannot carry
     * the lib relay type).
     *
     * <p>The two pieces of credential the callee needs to bring up its media plane after
     * {@link #accept(IncomingCall, AudioOutputStream, AudioInputStream, VideoOutputStream, VideoInputStream)}
     * do not live on {@link IncomingCall}: the relay block
     * is a {@code modules/lib} type the model module cannot reference, and the call key arrives
     * Signal-encrypted in the offer's per-device {@code <enc>} fanout. {@link CallReceiver} parses and
     * decrypts both as the offer is dispatched and hands them here, where they are held until the user
     * answers. Either argument may be {@code null} independently: a relay-less offer (native desktop
     * caller) stashes only the call key, and an offer whose call-key {@code <enc>} could not be
     * decrypted stashes only the relay.
     *
     * @implSpec
     * Implementations must store each non-{@code null} argument under {@code callId} and must tolerate
     * a {@code null} relay or {@code null} call key without throwing.
     *
     * @param callId  the call identifier
     * @param relay   the relay block parsed from the inbound offer, or {@code null}
     * @param callKey the 32-byte per-call shared key decrypted from the offer's self-device
     *                {@code <enc>}, or {@code null}
     */
    void noteOfferCredentials(String callId, CallRelay relay, byte[] callKey);

    /**
     * Rejects an inbound call offer with the given reason.
     *
     * <p>The reject stanza is sent to the peer, the offer is removed from the store, and
     * {@code onCallEnded} is fired on every listener. This method is invoked after the one-shot
     * guard on the offer has been claimed.
     *
     * @implSpec
     * Implementations must notify listeners of the end after sending the reject stanza.
     *
     * @param offer  the offer being rejected
     * @param reason the {@link CallEndReason} to communicate to the peer
     * @throws NullPointerException if {@code offer} or {@code reason} is {@code null}
     */
    void reject(IncomingCall offer, CallEndReason reason);

    /**
     * Returns the {@link CallRuntime} tracked under the given identifier, or {@code null} if none is
     * tracked.
     *
     * <p>A {@code null} argument yields {@code null}. This lookup lets {@link CallReceiver} route
     * peer-side state transitions to the matching session.
     *
     * @implSpec
     * Implementations must return {@code null} for a {@code null} argument and for an unknown id.
     *
     * @param callId the call's unique identifier
     * @return the matching session, or {@code null}
     */
    CallRuntime find(String callId);

    /**
     * Reports that the peer accepted a previously-placed outbound call.
     *
     * <p>The acceptance is forwarded to the matching session, if one is still tracked; otherwise
     * the report is dropped.
     *
     * @implSpec
     * Implementations must drop the report when no session is tracked for {@code callId}.
     *
     * @param callId the call identifier
     */
    void onPeerAccept(String callId);

    /**
     * Starts the local camera video track on a connected call, used by an audio-to-video upgrade.
     *
     * <p>Adds a {@link com.github.auties00.cobalt.call.session.VideoTrackOptions.Kind#CAMERA} track to
     * the call's media session so local video frames written to the call's video sink are encoded and
     * sent to the peer over the already-negotiated media transport. A no-op when the call is not
     * tracked or has no media session yet.
     *
     * @param callId the call identifier
     */
    void startLocalVideo(String callId);

    /**
     * Starts a screen-share video track on a connected call.
     *
     * <p>Adds a {@link com.github.auties00.cobalt.call.session.VideoTrackOptions.Kind#SCREEN_SHARE}
     * track to the call's media session so screen frames written to the call's video sink are encoded
     * and sent to the peer, signaled as a screen capture so the peer renders it distinctly from a
     * camera feed. A no-op when the call is not tracked or has no media session yet.
     *
     * <p>Equivalent to {@link #startScreenShare(String, com.github.auties00.cobalt.call.session.VideoTrackOptions)}
     * with a default screen-share track configuration.
     *
     * @param callId the call identifier
     */
    void startScreenShare(String callId);

    /**
     * Starts a screen-share video track on a connected call with explicit track options.
     *
     * <p>Adds the given screen-share track to the call's media session so screen frames written to the
     * call's video sink are encoded at the track's configured resolution and sent to the peer, and
     * announces the share to the peer carrying that resolution so it renders the capture distinctly
     * from a camera feed. A no-op when the call is not tracked or has no media session yet.
     *
     * @param callId  the call identifier
     * @param options the screen-share track configuration; its
     *                {@link com.github.auties00.cobalt.call.session.VideoTrackOptions#kind()} must be
     *                {@link com.github.auties00.cobalt.call.session.VideoTrackOptions.Kind#SCREEN_SHARE}
     */
    void startScreenShare(String callId, com.github.auties00.cobalt.call.session.VideoTrackOptions options);

    /**
     * Stops the screen-share video track on a connected call.
     *
     * <p>A no-op when the call is not tracked or has no screen-share track running.
     *
     * @param callId the call identifier
     */
    void stopScreenShare(String callId);

    /**
     * Sends a call-termination stanza and tears down the local call runtime.
     *
     * <p>Sends the terminate stanza to the peer, then ends the matching runtime so its media streams,
     * media session, and transport are released. A no-op when no call is tracked for {@code callId}.
     *
     * @param callId the call identifier
     * @param reason the {@link CallEndReason} to communicate
     */
    void terminate(String callId, CallEndReason reason);

    /**
     * Reports that the peer rejected a previously-placed outbound call.
     *
     * <p>The matching session, if still tracked, is ended with the given wire reason; otherwise the
     * report is dropped.
     *
     * @implSpec
     * Implementations must drop the report when no session is tracked for {@code callId}.
     *
     * @param callId the call identifier
     * @param reason the wire-level rejection reason, or {@code null}
     */
    void onPeerReject(String callId, String reason);

    /**
     * Reports that the peer terminated an in-flight call.
     *
     * <p>The matching session, if still tracked, is ended with the given wire reason; otherwise the
     * report is dropped.
     *
     * @implSpec
     * Implementations must drop the report when no session is tracked for {@code callId}.
     *
     * @param callId the call identifier
     * @param reason the wire-level reason, or {@code null}
     */
    void onPeerTerminate(String callId, String reason);

    /**
     * Removes a session from the registry and emits its end-of-call telemetry.
     *
     * <p>This method is invoked by a {@link CallRuntime} when it transitions to
     * {@link CallState#ENDED}: the session is removed from the registry, the call is removed from the
     * store, and its accumulated telemetry is drained into a WAM Call event.
     *
     * @implSpec
     * Implementations must remove the session from the registry and tolerate an unknown {@code callId}.
     *
     * @param callId the call identifier
     */
    void unregister(String callId);

    /**
     * Sends a mute-state stanza notifying the peer that the local microphone was muted or unmuted.
     *
     * @param peer    the peer {@link Jid}
     * @param creator the call-creator {@link Jid}
     * @param callId  the call identifier
     * @param muted   {@code true} if the microphone is now muted
     */
    void sendMute(Jid peer, Jid creator, String callId, boolean muted);

    /**
     * Sends a video-state stanza notifying the peer that local video was enabled or disabled.
     *
     * @implSpec
     * Implementations should route {@link #sendVideoUpgradeRequest(Jid, Jid, String)} and
     * {@link #sendVideoUpgradeReject(Jid, Jid, String)} through this method.
     *
     * @param peer    the peer {@link Jid}
     * @param creator the call-creator {@link Jid}
     * @param callId  the call identifier
     * @param enabled {@code true} if local video is now on
     */
    void sendVideoState(Jid peer, Jid creator, String callId, boolean enabled);

    /**
     * Sends the request leg of a mid-call video upgrade.
     *
     * <p>The request is expressed as an enabling video-state stanza.
     *
     * @param peer    the peer {@link Jid}
     * @param creator the call-creator {@link Jid}
     * @param callId  the call identifier
     */
    void sendVideoUpgradeRequest(Jid peer, Jid creator, String callId);

    /**
     * Sends the peer-side rejection of a mid-call video upgrade.
     *
     * <p>The rejection is expressed as a disabling video-state stanza and keeps the caller
     * audio-only.
     *
     * @param peer    the peer {@link Jid}
     * @param creator the call-creator {@link Jid}
     * @param callId  the call identifier
     */
    void sendVideoUpgradeReject(Jid peer, Jid creator, String callId);

    /**
     * Sends an in-call interaction over the transport the live WhatsApp voip engine uses for it.
     *
     * <p>The raise-hand, lower-hand, and peer-mute interactions are server-relayed {@code <call>} signaling stanzas. The
     * reaction, key-frame, and video-upgrade interactions are
     * {@link com.github.auties00.cobalt.model.call.datachannel.AppDataMessage AppDataMessage} protobuf messages written
     * directly to the call's pre-negotiated DataChannel (DTLS-SCTP carries them; they are not SRTP-wrapped) and
     * re-sent a few times because that channel is unreliable. The data-plane path is a no-op when no call is registered
     * for {@code callId}, when the DataChannel is not open, or for an interaction with no modeled {@code AppDataMessage}
     * field (currently only the reaction is encodable). The {@code peer} and {@code creator} parameters address the
     * stanza-relayed interactions.
     *
     * @implSpec
     * Implementations must be a no-op when the call or its DataChannel is unavailable, never throwing on a missing
     * media plane.
     *
     * @param peer        the peer {@link Jid}
     * @param creator     the call-creator {@link Jid}
     * @param callId      the call identifier
     * @param interaction the interaction payload
     * @throws NullPointerException if {@code callId} or {@code interaction} is {@code null}
     */
    void sendInteraction(Jid peer, Jid creator, String callId, CallInteraction interaction);

    /**
     * Reports that the peer published a new end-to-end keying material bundle for an in-flight call.
     *
     * <p>Group calls rotate their per-domain SRTP master keys (audio, video, app-data) on participant
     * join and leave. Each rotation is published as one Signal-encrypted {@code <enc_rekey>} stanza
     * whose plaintext is a {@link com.github.auties00.cobalt.model.call.datachannel.E2eRekeyPayload}.
     *
     * @implSpec
     * Implementations must drop the report when no session is tracked for {@code callId}. The
     * implementation owns the Signal-decryption step and the parse of
     * {@link com.github.auties00.cobalt.model.call.datachannel.E2eRekeyPayload}.
     *
     * @param callId    the call identifier
     * @param senderJid the device {@link Jid} that authored the rekey envelope
     * @param encType   the wire-level Signal envelope variant ({@code msg} or {@code pkmsg})
     * @param ciphertext the Signal-encrypted bytes carried inside the {@code <enc>} child
     */
    void onEncRekey(String callId, Jid senderJid, com.github.auties00.cobalt.message.MessageEncryptionType encType, byte[] ciphertext);

    /**
     * Brings up the callee media plane from a relay block delivered out-of-band in a
     * {@code <group_update>} push rather than inline in the original offer.
     *
     * <p>The native desktop caller omits the {@code <relay>} from the group offer; the server instead
     * sends it inside the {@code <group_update>} that confirms the join (after the callee's
     * {@code <preaccept>} and {@code <accept>}). This routes that late-arriving relay to the matching
     * session so it allocates the relay and starts hop-by-hop SRTP, exactly as the inline-relay path
     * does. The report is dropped when no session is tracked for {@code callId}, when the session is
     * not a group call, or when its media plane was already brought up (for example from an inline
     * relay).
     *
     * @implSpec
     * Implementations must bring the media plane up at most once per call and must drop the report
     * for an unknown or non-group {@code callId}.
     *
     * @param callId the call identifier
     * @param relay  the relay block parsed from the {@code <group_update>}
     */
    void onGroupRelay(String callId, CallRelay relay);

    /**
     * Notifies all registered listeners that a call ended.
     *
     * <p>The wire reason is parsed into a typed {@link CallEndReason} via
     * {@link CallEndReason#fromWireValue(String)}, so an unrecognized or absent literal surfaces as
     * {@link CallEndReason#UNKNOWN}.
     *
     * @implSpec
     * Implementations must surface an unrecognized or {@code null} wire reason as
     * {@link CallEndReason#UNKNOWN}.
     *
     * @param callId     the call identifier
     * @param fromJid    the {@link Jid} of the party that ended the call
     * @param wireReason the wire-level reason literal, or {@code null}
     */
    void notifyEnded(String callId, Jid fromJid, String wireReason);
}
