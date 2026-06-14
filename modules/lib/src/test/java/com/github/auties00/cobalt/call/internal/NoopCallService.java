package com.github.auties00.cobalt.call.internal;

import com.github.auties00.cobalt.call.CallRuntime;
import com.github.auties00.cobalt.call.CallService;
import com.github.auties00.cobalt.call.LiveCallService;
import com.github.auties00.cobalt.call.stream.AudioInputStream;
import com.github.auties00.cobalt.call.stream.AudioOutputStream;
import com.github.auties00.cobalt.call.stream.VideoInputStream;
import com.github.auties00.cobalt.call.stream.VideoOutputStream;
import com.github.auties00.cobalt.model.call.Call;
import com.github.auties00.cobalt.model.call.CallEndReason;
import com.github.auties00.cobalt.model.call.CallInteraction;
import com.github.auties00.cobalt.model.call.IncomingCall;
import com.github.auties00.cobalt.model.jid.Jid;

/**
 * Test-only {@link CallService} stand-in whose methods are all no-ops.
 *
 * <p>Used by media-session and pipeline tests that need a {@link CallRuntime} in isolation; the call
 * engine is never exercised beyond the registry hooks the runtime calls back into, so a fully no-op
 * implementation is the simplest stand-in. Production code must use {@link LiveCallService} with its
 * real collaborators.
 */
public final class NoopCallService implements CallService {
    @Override public Call placeCall(Jid peer, AudioOutputStream audioOut, AudioInputStream audioIn, VideoOutputStream videoOut, VideoInputStream videoIn) { return null; }
    @Override public Call placeGroupCall(java.util.Set<Jid> peers, Jid groupJid, AudioOutputStream audioOut, AudioInputStream audioIn, VideoOutputStream videoOut, VideoInputStream videoIn) { return null; }
    @Override public Call accept(IncomingCall offer, AudioOutputStream audioOut, AudioInputStream audioIn, VideoOutputStream videoOut, VideoInputStream videoIn) { return null; }
    @Override public void noteOfferCredentials(String callId, com.github.auties00.cobalt.call.signaling.CallRelay relay, byte[] callKey) {}
    @Override public void reject(IncomingCall offer, CallEndReason reason) {}
    @Override public CallRuntime find(String callId) { return null; }
    @Override public void onPeerAccept(String callId) {}
    @Override public void startLocalVideo(String callId) {}
    @Override public void startScreenShare(String callId) {}
    @Override public void startScreenShare(String callId, com.github.auties00.cobalt.call.session.VideoTrackOptions options) {}
    @Override public void stopScreenShare(String callId) {}
    @Override public void terminate(String callId, CallEndReason reason) {}
    @Override public void onPeerReject(String callId, String reason) {}
    @Override public void onPeerTerminate(String callId, String reason) {}
    @Override public void unregister(String callId) {}
    @Override public void sendMute(Jid peer, Jid creator, String callId, boolean muted) {}
    @Override public void sendVideoState(Jid peer, Jid creator, String callId, boolean enabled) {}
    @Override public void sendVideoUpgradeRequest(Jid peer, Jid creator, String callId) {}
    @Override public void sendVideoUpgradeReject(Jid peer, Jid creator, String callId) {}
    @Override public void sendInteraction(Jid peer, Jid creator, String callId, CallInteraction interaction) {}
    @Override public void onEncRekey(String callId, Jid senderJid, com.github.auties00.cobalt.message.MessageEncryptionType encType, byte[] ciphertext) {}
    @Override public void onGroupRelay(String callId, com.github.auties00.cobalt.call.signaling.CallRelay relay) {}
    @Override public void notifyEnded(String callId, Jid fromJid, String wireReason) {}
}
