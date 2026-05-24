package com.github.auties00.cobalt.call;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the M6 call-link record types — validation, defensive
 * copying, and Optional handling.
 */
public class CallLinkTest {

    /**
     * Reference creator JID.
     */
    private static final Jid CREATOR = Jid.of("12345", JidServer.user());

    /**
     * A fresh call-link preview round-trips its fields and rejects
     * negative participant counts + null fields.
     */
    @Test
    public void callLinkValidates() {
        var link = new CallLink("xyz123", CREATOR, Optional.empty(),
                Instant.parse("2026-05-06T00:00:00Z"), false, true);
        assertEquals("xyz123", link.token());
        assertSame(CREATOR, link.creator());
        assertTrue(link.callId().isEmpty());
        assertFalse(link.videoEnabled());
        assertTrue(link.requiresLobby());
    }

    /**
     * The token cannot be empty.
     */
    @Test
    public void emptyTokenRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CallLink("",
                CREATOR, Optional.empty(), Instant.now(), false, false));
    }

    /**
     * {@link CallLinkPreview} defensively copies the participant
     * list so callers can't mutate it after construction.
     */
    @Test
    public void previewCopiesParticipantsDefensively() {
        var participants = new ArrayList<Jid>();
        participants.add(CREATOR);
        var link = new CallLink("t", CREATOR, Optional.of("call-id"),
                Instant.now(), true, false);
        var preview = new CallLinkPreview(link, "Host", participants, 32);
        participants.clear();
        assertEquals(1, preview.activeParticipants().size(),
                "preview should have copied the list");
        assertEquals(CREATOR, preview.activeParticipants().get(0));
    }

    /**
     * Negative participant cap is rejected.
     */
    @Test
    public void negativeParticipantCountRejected() {
        var link = new CallLink("t", CREATOR, Optional.empty(), Instant.now(),
                false, false);
        assertThrows(IllegalArgumentException.class, () -> new CallLinkPreview(
                link, "Host", List.of(), -1));
    }
}
