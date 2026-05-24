package com.github.auties00.cobalt.call.session;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.model.jid.Jid;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * One peer in an active {@link GroupCallSession} — its JID, the SSRC
 * the SFU stamps on its forwarded audio packets, and the listener
 * the application registered to receive the peer's decoded
 * {@link AudioFrame}s.
 *
 * @param jid       the participant's JID
 * @param audioSsrc the 32-bit SSRC the peer's outbound audio is
 *                  stamped with on the way through the SFU; received
 *                  via group-call signaling
 * @param onAudio   listener invoked once per decoded inbound
 *                  {@link AudioFrame} from this peer
 */
public record GroupCallParticipant(Jid jid, int audioSsrc, Consumer<AudioFrame> onAudio) {
    /**
     * Compact constructor — null-checks fields.
     */
    public GroupCallParticipant {
        Objects.requireNonNull(jid, "jid cannot be null");
        Objects.requireNonNull(onAudio, "onAudio cannot be null");
    }
}
