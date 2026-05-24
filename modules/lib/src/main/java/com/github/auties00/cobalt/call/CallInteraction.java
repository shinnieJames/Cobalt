package com.github.auties00.cobalt.call;

import java.util.Objects;
import java.util.Optional;

/**
 * The M8 in-call interaction model — small signaling exchanges that
 * happen during a call to update peer-visible UX state without
 * affecting the media path. Each is conveyed via a discrete
 * {@code <call><interaction/></call>} stanza family on the wire;
 * this sealed type captures the relevant fields per kind.
 */
public sealed interface CallInteraction
        permits CallInteraction.Reaction,
        CallInteraction.RaiseHand,
        CallInteraction.LowerHand,
        CallInteraction.PeerMuteRequest,
        CallInteraction.KeyFrameRequest,
        CallInteraction.VideoUpgradeRequest {

    /**
     * Returns the textual {@code kind} attribute the wire uses to
     * discriminate.
     *
     * @return the wire kind
     */
    String wireKind();

    /**
     * Emoji reaction broadcast — one peer fires an emoji, every
     * other peer sees it.
     *
     * @param emoji the emoji string (typically a single grapheme)
     */
    record Reaction(String emoji) implements CallInteraction {
        /**
         * Compact constructor — null-checks emoji.
         */
        public Reaction {
            Objects.requireNonNull(emoji, "emoji cannot be null");
            if (emoji.isEmpty()) {
                throw new IllegalArgumentException("emoji cannot be empty");
            }
        }

        @Override
        public String wireKind() {
            return "reaction";
        }
    }

    /**
     * "Raise hand" gesture — typically shown in group calls when a
     * participant wants to be unmuted.
     */
    record RaiseHand() implements CallInteraction {
        @Override
        public String wireKind() {
            return "raise_hand";
        }
    }

    /**
     * "Lower hand" — clears a previously-raised hand.
     */
    record LowerHand() implements CallInteraction {
        @Override
        public String wireKind() {
            return "lower_hand";
        }
    }

    /**
     * An admin asks a participant to mute themselves. Carries the
     * target participant JID so the receiving peer can decide who
     * to mute.
     *
     * @param target  the participant being asked to mute (string
     *                form of the peer's JID)
     * @param reason  optional reason text; may be empty
     */
    record PeerMuteRequest(String target, Optional<String> reason) implements CallInteraction {
        /**
         * Compact constructor — null-checks fields.
         */
        public PeerMuteRequest {
            Objects.requireNonNull(target, "target cannot be null");
            Objects.requireNonNull(reason, "reason cannot be null");
        }

        @Override
        public String wireKind() {
            return "peer_mute_request";
        }
    }

    /**
     * Asks the peer to emit an immediate video keyframe — the
     * call layer wires this to
     * {@link com.github.auties00.cobalt.call.internal.video.VideoPipeline#requestKeyframe()}
     * once the request arrives.
     */
    record KeyFrameRequest() implements CallInteraction {
        @Override
        public String wireKind() {
            return "keyframe_request";
        }
    }

    /**
     * Asks the peer to renegotiate the call to include video — the
     * audio-only call upgrades to audio+video. Carries no payload
     * fields (the receiver decides whether to accept or reject).
     */
    record VideoUpgradeRequest() implements CallInteraction {
        @Override
        public String wireKind() {
            return "video_upgrade_request";
        }
    }
}
