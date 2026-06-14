package com.github.auties00.cobalt.call.session;

import com.github.auties00.cobalt.call.CallRuntime;
import com.github.auties00.cobalt.model.call.datachannel.E2eRekeyPayload;

/**
 * The media plane of an active call: the common supertype of the one-to-one {@link VoiceCallSession}
 * and the many-to-many {@link GroupCallSession}.
 *
 * <p>A {@link CallRuntime} owns exactly one media session for the life of a connected call, regardless
 * of topology, and drives it through this surface: it applies end-to-end rekeys published by the peer
 * and closes the session at teardown. The two implementations differ in their inbound topology (a
 * single remote stream versus one SFU-forwarded stream per participant) and in optional video-track
 * support (one-to-one only), but both key their media the same way and share the same handshake,
 * connect, and teardown lifecycle, so the runtime treats them uniformly through this sealed type.
 */
public sealed interface CallMediaSession extends AutoCloseable permits VoiceCallSession, GroupCallSession {
    /**
     * Applies a freshly-published end-to-end rekey bundle to this session's SRTP keying.
     *
     * <p>Each {@link com.github.auties00.cobalt.model.call.datachannel.RekeyKeyType#AUDIO AUDIO} or
     * {@link com.github.auties00.cobalt.model.call.datachannel.RekeyKeyType#VIDEO VIDEO} entry rotates
     * the corresponding media master key; APPDATA entries are outside the SRTP endpoint and ignored. A
     * bundle that arrives before the media plane is keyed is dropped.
     *
     * @implSpec
     * Implementations must tolerate a bundle delivered before keying without throwing and must ignore
     * APPDATA entries.
     *
     * @param payload the rekey bundle
     * @throws NullPointerException if {@code payload} is {@code null}
     */
    void applyRekey(E2eRekeyPayload payload);

    /**
     * Requests a fresh intra-frame (key frame) from every peer this session is receiving video from.
     *
     * <p>The request is an RTCP Picture Loss Indication (PSFB, payload type 206, feedback message type
     * 1) addressed to each inbound video stream's source SSRC, SRTCP-protected on the same hop-by-hop
     * control path as the periodic sender reports and forwarded by the relay to the peer, which responds
     * by emitting an immediate key frame so the local decoder can resynchronize.
     *
     * @implSpec
     * Implementations must be best-effort and side-effect-free when there is nothing to ask: a session
     * with no inbound video stream, or one called before the media plane is keyed, returns without
     * throwing and without sending anything.
     */
    void requestKeyframe();

    /**
     * Tears the media plane down, releasing the codecs, RTP plumbing, and DTLS driver (which closes the
     * underlying transport).
     *
     * @implSpec
     * Implementations must be idempotent: a second invocation after the first returns without effect.
     */
    @Override
    void close();
}
