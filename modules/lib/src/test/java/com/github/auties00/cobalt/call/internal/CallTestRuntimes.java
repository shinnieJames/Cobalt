package com.github.auties00.cobalt.call.internal;

import com.github.auties00.cobalt.call.CallRuntime;
import com.github.auties00.cobalt.call.CallService;
import com.github.auties00.cobalt.call.stream.AudioInputStream;
import com.github.auties00.cobalt.call.stream.AudioOutputStream;
import com.github.auties00.cobalt.call.stream.VideoInputStream;
import com.github.auties00.cobalt.call.stream.VideoOutputStream;
import com.github.auties00.cobalt.model.call.Call;
import com.github.auties00.cobalt.model.call.CallState;
import com.github.auties00.cobalt.model.jid.Jid;

/**
 * Test helper that builds a {@link CallRuntime} with the four default buffered media streams, the
 * 1:1-call shape used by the media-session and pipeline tests.
 */
public final class CallTestRuntimes {
    private CallTestRuntimes() {
        throw new AssertionError("CallTestRuntimes is not instantiable");
    }

    /**
     * Builds a connecting 1:1 {@link CallRuntime} backed by buffered streams.
     *
     * @param engine   the owning call service
     * @param callId   the call identifier
     * @param peer     the peer JID
     * @param chatJid  the chat the call belongs to
     * @param creator  the call creator JID
     * @param outgoing whether the local side placed the call
     * @param video    whether the call carries video
     * @return the runtime
     */
    public static CallRuntime of(CallService engine, String callId, Jid peer, Jid chatJid, Jid creator,
                                 boolean outgoing, boolean video) {
        var call = new Call(callId, peer, chatJid, creator, outgoing, false, video, CallState.CONNECTING);
        return new CallRuntime(engine, call,
                AudioOutputStream.buffered(), AudioInputStream.buffered(),
                VideoOutputStream.buffered(), VideoInputStream.buffered());
    }
}
