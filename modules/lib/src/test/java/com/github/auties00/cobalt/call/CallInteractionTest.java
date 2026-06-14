package com.github.auties00.cobalt.call;

import com.github.auties00.cobalt.model.call.CallInteraction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the M8 {@link CallInteraction} sealed hierarchy: the wire-kind
 * discriminator, field validation, and pattern-match exhaustiveness.
 */
public class CallInteractionTest {

    @Test
    @DisplayName("each variant exposes its expected wire-kind string")
    public void wireKindIsDistinctPerVariant() {
        assertEquals("reaction", new CallInteraction.Reaction("👍").wireKind());
        assertEquals("raise_hand", new CallInteraction.RaiseHand().wireKind());
        assertEquals("lower_hand", new CallInteraction.LowerHand().wireKind());
        assertEquals("peer_mute_request",
                new CallInteraction.PeerMuteRequest("11@s.whatsapp.net", Optional.empty()).wireKind());
        assertEquals("keyframe_request",
                new CallInteraction.KeyFrameRequest().wireKind());
    }

    @Test
    @DisplayName("Reaction with empty or null emoji is rejected")
    public void emptyReactionRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new CallInteraction.Reaction(""));
        assertThrows(NullPointerException.class,
                () -> new CallInteraction.Reaction(null));
    }

    @Test
    @DisplayName("pattern matching across the sealed hierarchy is exhaustive")
    public void patternMatchExhaustive() {
        CallInteraction[] all = {
                new CallInteraction.Reaction("🎉"),
                new CallInteraction.RaiseHand(),
                new CallInteraction.LowerHand(),
                new CallInteraction.PeerMuteRequest("x", Optional.of("noise")),
                new CallInteraction.KeyFrameRequest(),
                new CallInteraction.VideoUpgradeRequest()
        };
        for (var interaction : all) {
            var name = switch (interaction) {
                case CallInteraction.Reaction r -> "reaction:" + r.emoji();
                case CallInteraction.RaiseHand ignored -> "raise";
                case CallInteraction.LowerHand ignored -> "lower";
                case CallInteraction.PeerMuteRequest m -> "mute:" + m.target();
                case CallInteraction.KeyFrameRequest ignored -> "keyframe";
                case CallInteraction.VideoUpgradeRequest ignored -> "video_upgrade";
            };
            assertTrue(name.length() > 0);
        }
    }

    @Test
    @DisplayName("PeerMuteRequest carries an optional reason")
    public void peerMuteCarriesOptionalReason() {
        var req = new CallInteraction.PeerMuteRequest("target", Optional.of("background noise"));
        assertEquals("background noise", req.reason().orElseThrow());
        var noReason = new CallInteraction.PeerMuteRequest("target", Optional.empty());
        assertTrue(noReason.reason().isEmpty());
        assertInstanceOf(CallInteraction.class, req);
    }
}
