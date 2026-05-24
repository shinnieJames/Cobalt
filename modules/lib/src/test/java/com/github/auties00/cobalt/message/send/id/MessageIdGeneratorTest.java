package com.github.auties00.cobalt.message.send.id;

import com.github.auties00.cobalt.model.jid.Jid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural tests for {@link MessageIdGenerator#generate}.
 *
 * @apiNote
 * Mirrors WA Web's {@code WAWebMsgKey.newId} and
 * {@code WAWebMsgKey.newId_DEPRECATED}: V1 ids are exactly
 * {@value MessageIdGenerator#PREFIX} plus 16 uppercase hex characters; V2 ids
 * are exactly {@value MessageIdGenerator#PREFIX} plus 18 uppercase hex
 * characters from a SHA-256 digest. Both versions must mint distinct ids on
 * consecutive calls so the stanza-id namespace does not collide on a single
 * send burst.
 * @implNote
 * {@link MessageIdGenerator#generate} pulls randomness and the wall clock
 * internally, so a byte-equal known-answer test against WA Web requires either
 * a seeded RNG or refactoring the private V2 helpers to take their entropy as
 * a parameter, both out of scope. These tests pin the externally visible
 * structural contract; uniqueness is established statistically over 100 calls
 * per version.
 */
@DisplayName("MessageIdGenerator")
class MessageIdGeneratorTest {
    private static final Jid SENDER = Jid.of("393495089819@s.whatsapp.net");

    /**
     * Verifies that a V1 id is the {@value MessageIdGenerator#PREFIX} prefix
     * followed by 16 uppercase hex characters.
     */
    @Test
    @DisplayName("V1 id is \"3EB0\" + 16 uppercase hex characters")
    void v1IdStructure() {
        var id = MessageIdGenerator.generate(MessageIdVersion.V1, SENDER);
        assertEquals(20, id.length(), "V1 ids are exactly 20 characters long");
        assertTrue(id.startsWith(MessageIdGenerator.PREFIX), "V1 id must start with the shared prefix");
        var suffix = id.substring(MessageIdGenerator.PREFIX.length());
        assertEquals(16, suffix.length(), "V1 suffix is 16 hex chars (8 random bytes)");
        assertTrue(suffix.chars().allMatch(MessageIdGeneratorTest::isUpperHex),
                "V1 suffix must be uppercase hex: " + suffix);
    }

    /**
     * Verifies that a V2 id is the {@value MessageIdGenerator#PREFIX} prefix
     * followed by 18 uppercase hex characters.
     */
    @Test
    @DisplayName("V2 id is \"3EB0\" + 18 uppercase hex characters")
    void v2IdStructure() {
        var id = MessageIdGenerator.generate(MessageIdVersion.V2, SENDER);
        assertEquals(22, id.length(), "V2 ids are exactly 22 characters long");
        assertTrue(id.startsWith(MessageIdGenerator.PREFIX), "V2 id must start with the shared prefix");
        var suffix = id.substring(MessageIdGenerator.PREFIX.length());
        assertEquals(18, suffix.length(), "V2 suffix is 18 hex chars (first 9 bytes of SHA-256)");
        assertTrue(suffix.chars().allMatch(MessageIdGeneratorTest::isUpperHex),
                "V2 suffix must be uppercase hex: " + suffix);
    }

    /**
     * Verifies that 100 consecutive V1 ids are all distinct.
     */
    @Test
    @DisplayName("Consecutive V1 ids do not collide")
    void v1IdsAreUnique() {
        var ids = new HashSet<String>();
        for (var i = 0; i < 100; i++) {
            ids.add(MessageIdGenerator.generate(MessageIdVersion.V1, SENDER));
        }
        assertEquals(100, ids.size(), "100 consecutive V1 ids must all be distinct (RNG collision is astronomically unlikely)");
    }

    /**
     * Verifies that 100 consecutive V2 ids are all distinct.
     */
    @Test
    @DisplayName("Consecutive V2 ids do not collide")
    void v2IdsAreUnique() {
        var ids = new HashSet<String>();
        for (var i = 0; i < 100; i++) {
            ids.add(MessageIdGenerator.generate(MessageIdVersion.V2, SENDER));
        }
        assertEquals(100, ids.size(), "100 consecutive V2 ids must all be distinct");
    }

    /**
     * Verifies that V1 and V2 produce different-length ids for the same
     * sender.
     */
    @Test
    @DisplayName("V1 and V2 produce different-length ids for the same sender")
    void versionsProduceDifferentLengths() {
        var v1 = MessageIdGenerator.generate(MessageIdVersion.V1, SENDER);
        var v2 = MessageIdGenerator.generate(MessageIdVersion.V2, SENDER);
        assertNotEquals(v1.length(), v2.length(),
                "the two versions encode different amounts of entropy and must produce different-length suffixes");
    }

    /**
     * Verifies that V2 mints distinct ids for distinct sender JIDs.
     *
     * @apiNote
     * The random component varies between consecutive calls too, so this
     * test would still pass with V1; documents in combination with
     * {@link #v1IdsAreUnique} that V2 is sender-influenced via the SHA-256
     * pre-image.
     */
    @Test
    @DisplayName("V2 id differs across sender JIDs (sender feeds the SHA-256 pre-image)")
    void v2DiffersAcrossSenders() {
        var senderA = Jid.of("393495089819@s.whatsapp.net");
        var senderB = Jid.of("12025550100@s.whatsapp.net");
        var idA = MessageIdGenerator.generate(MessageIdVersion.V2, senderA);
        var idB = MessageIdGenerator.generate(MessageIdVersion.V2, senderB);
        assertNotEquals(idA, idB);
    }

    /**
     * Verifies that a {@code null} version throws
     * {@link NullPointerException}.
     */
    @Test
    @DisplayName("Null version throws NullPointerException")
    void nullVersionThrows() {
        assertThrows(NullPointerException.class,
                () -> MessageIdGenerator.generate(null, SENDER));
    }

    /**
     * Verifies that a {@code null} sender JID throws
     * {@link NullPointerException}.
     */
    @Test
    @DisplayName("Null sender throws NullPointerException")
    void nullSenderThrows() {
        assertThrows(NullPointerException.class,
                () -> MessageIdGenerator.generate(MessageIdVersion.V2, null));
    }

    /**
     * Returns whether the supplied code point is an uppercase hex character.
     *
     * @apiNote
     * Helper for the structural assertions in {@link #v1IdStructure} and
     * {@link #v2IdStructure}; matches the {@code [0-9A-F]} alphabet
     * {@link java.util.HexFormat#withUpperCase} emits.
     *
     * @param c the code point to test
     * @return {@code true} when {@code c} is one of {@code 0-9} or
     *         {@code A-F}
     */
    private static boolean isUpperHex(int c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F');
    }
}
