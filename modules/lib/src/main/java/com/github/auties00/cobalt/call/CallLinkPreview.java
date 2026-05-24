package com.github.auties00.cobalt.call;

import com.github.auties00.cobalt.model.jid.Jid;

import java.util.List;
import java.util.Objects;

/**
 * The result of {@code previewCallLink(token)} — describes a call
 * link the user is considering joining. Populated from the
 * server-side preview RPC; carries enough metadata for the UI to
 * render a "Join now" prompt without committing the user to a
 * lobby admit.
 *
 * @param link             the underlying {@link CallLink}
 * @param creatorName      display name of the host (best-effort —
 *                         empty when the host's profile is hidden)
 * @param activeParticipants the JIDs currently in the call; an empty
 *                         list means the host hasn't started yet
 * @param participantCount the total participant cap configured by
 *                         the host (e.g. 32 for WhatsApp)
 */
public record CallLinkPreview(
        CallLink link,
        String creatorName,
        List<Jid> activeParticipants,
        int participantCount
) {
    /**
     * Compact constructor — null-checks + defensively copies the
     * participant list.
     */
    public CallLinkPreview {
        Objects.requireNonNull(link, "link cannot be null");
        Objects.requireNonNull(creatorName, "creatorName cannot be null");
        Objects.requireNonNull(activeParticipants, "activeParticipants cannot be null");
        if (participantCount < 0) {
            throw new IllegalArgumentException("participantCount must be ≥ 0");
        }
        activeParticipants = List.copyOf(activeParticipants);
    }
}
