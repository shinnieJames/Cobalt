package com.github.auties00.cobalt.call.session;

import com.github.auties00.cobalt.call.stream.AudioFrame;
import com.github.auties00.cobalt.model.jid.Jid;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Describes one remote peer in an active group call.
 *
 * <p>A participant binds three things together: the peer's {@link Jid}, the synchronization source
 * (SSRC) the conferencing server stamps onto that peer's forwarded audio packets, and the listener
 * the application registered to receive that peer's decoded {@link AudioFrame}s. The group call has
 * no server-side mixing; every peer's audio arrives as a distinct stream identified by its
 * {@code audioSsrc}, and the {@code onAudio} listener is invoked once per decoded frame so the
 * application can mix the streams itself.
 *
 * <p>Instances are constructed by the application when a peer joins and are surfaced back to call
 * callbacks. The {@code audioSsrc} must match the SSRC the group-call signaling layer advertised for
 * the peer, otherwise that peer's forwarded packets are demultiplexed to the wrong listener.
 *
 * @param jid       the peer's JID
 * @param audioSsrc the 32-bit SSRC the peer's outbound audio is stamped with as it passes through
 *                  the conferencing server, as advertised by group-call signaling
 * @param onAudio   the listener invoked once per decoded inbound {@link AudioFrame} from this peer
 */
public record GroupCallParticipant(Jid jid, int audioSsrc, Consumer<AudioFrame> onAudio) {
    /**
     * Validates that the JID and audio listener are present.
     *
     * @throws NullPointerException if {@code jid} or {@code onAudio} is {@code null}
     */
    public GroupCallParticipant {
        Objects.requireNonNull(jid, "jid cannot be null");
        Objects.requireNonNull(onAudio, "onAudio cannot be null");
    }
}
