package com.github.auties00.cobalt.call;

import com.github.auties00.cobalt.model.jid.Jid;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The M6 call-link descriptor — the joinable URL-style handle for a
 * lobby-style call. Created via {@link CallLinkPreview} and joined
 * through the call layer's {@code joinCallLink} signaling, which
 * eventually produces a {@link GroupCallSession}.
 *
 * @param token       the opaque server-generated link token; used as
 *                    the lookup key by all link APIs
 * @param creator     the JID of the host who created the link
 * @param callId      the underlying call identifier the link refers
 *                    to; populated once the host starts the call
 *                    (may be empty before then)
 * @param createdAt   the wall-clock timestamp the link was created
 * @param videoEnabled whether the link is configured for a video
 *                    call (vs. audio-only)
 * @param requiresLobby whether joiners are queued in a lobby and the
 *                     host has to admit them — RFC-style "waiting
 *                     room"
 */
public record CallLink(
        String token,
        Jid creator,
        Optional<String> callId,
        Instant createdAt,
        boolean videoEnabled,
        boolean requiresLobby
) {
    /**
     * Compact constructor — null-checks fields.
     */
    public CallLink {
        Objects.requireNonNull(token, "token cannot be null");
        Objects.requireNonNull(creator, "creator cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        if (token.isEmpty()) {
            throw new IllegalArgumentException("token cannot be empty");
        }
    }
}
