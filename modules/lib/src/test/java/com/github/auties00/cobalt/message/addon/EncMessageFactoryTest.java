package com.github.auties00.cobalt.message.addon;

import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.message.poll.PollEncValue;
import com.github.auties00.cobalt.model.message.text.CommentMessage;
import com.github.auties00.cobalt.model.message.text.CommentMessageBuilder;
import com.github.auties00.cobalt.model.message.text.ReactionMessage;
import com.github.auties00.cobalt.model.message.text.ReactionMessageBuilder;
import com.github.auties00.cobalt.model.message.security.EncCommentMessage;
import com.github.auties00.cobalt.model.message.security.EncReactionMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link EncMessageFactory}, mirroring WA Web's
 * {@code WAWebAddonEncryption.encryptAddOn} (for comment and reaction) and
 * {@code WAWebPollsVoteEncryption.encryptVote}.
 *
 * @apiNote The class under test sits one level above
 * {@link MessageAddonEncryption} and is responsible for: resolving the
 * {@code originalSender} from the parent message key (prefer
 * {@code senderJid}; fall back to the self JID when {@code fromMe};
 * otherwise use the parent {@code parentJid}); selecting the right HKDF
 * context label ({@link MessageAddonType#ENC_REACTION},
 * {@link MessageAddonType#ENC_COMMENT},
 * {@link MessageAddonType#POLL_VOTE}); wrapping the ciphertext and IV into
 * the matching protobuf model ({@link EncReactionMessage},
 * {@link EncCommentMessage}, {@link PollEncValue}); SHA-256-hashing each
 * selected poll option to its canonical 32-byte digest before encryption;
 * and failing loudly when the parent message lacks a {@code messageSecret},
 * when the parent key is missing an id or parent JID, or when a comment has
 * no inner {@link MessageContainer}.
 *
 * @implNote The lower-level encrypt/decrypt round-trip is covered by
 * {@link MessageAddonEncryptionTest}; this class focuses on wrapping,
 * sender resolution, and option-hashing logic.
 */
@DisplayName("EncMessageFactory")
class EncMessageFactoryTest {

    /**
     * 32-byte parent secret used as the HKDF input keying material.
     */
    private static final byte[] PARENT_SECRET = repeatedByte(32, (byte) 0x42);

    /**
     * Alternate 32-byte secret used by the key-isolation test.
     */
    private static final byte[] OTHER_SECRET = repeatedByte(32, (byte) 0x55);

    /**
     * Parent stanza id used as the HKDF info component.
     */
    private static final String PARENT_KEY_ID = "3EB0CAFEBABE0123456789";

    /**
     * Chat JID used as the parent key's {@code parentJid}.
     */
    private static final Jid CHAT_JID = Jid.of("12025550100@s.whatsapp.net");

    /**
     * Foreign sender JID used when the parent is authored by another user.
     */
    private static final Jid OTHER_SENDER = Jid.of("12025550200@s.whatsapp.net");

    /**
     * Self JID used as the addon sender and as the {@code originalSender}
     * fallback when {@code fromMe} is set.
     */
    private static final Jid SELF_JID = Jid.of("12025550999@s.whatsapp.net");

    /**
     * Verifies the encrypted-reaction wrapper shape: ciphertext present,
     * 12-byte IV, target message key propagated.
     */
    @Test
    @DisplayName("encryptReaction: result has ciphertext, 12-byte IV, and propagates target key")
    void encryptReactionShape() {
        var parent = parent(PARENT_SECRET, PARENT_KEY_ID, false, OTHER_SENDER);
        var reactionTarget = new MessageKeyBuilder()
                .id("3EB0TARGET")
                .fromMe(false)
                .parentJid(CHAT_JID)
                .build();
        var reaction = new ReactionMessageBuilder()
                .key(reactionTarget)
                .text("👍")
                .senderTimestampMs(Instant.ofEpochSecond(1700000000L))
                .build();

        var enc = EncMessageFactory.encryptReaction(reaction, parent, SELF_JID);

        assertNotNull(enc);
        assertNotNull(enc.encPayload().orElseThrow());
        assertNotNull(enc.encIv().orElseThrow());
        assertTrue(enc.encPayload().orElseThrow().length > 0, "ciphertext must be non-empty");
        assertEquals(12, enc.encIv().orElseThrow().length, "AES-GCM IV is 12 bytes");
        assertEquals(reactionTarget, enc.targetMessageKey().orElseThrow(),
                "target message key must propagate to the encrypted wrapper");
    }

    /**
     * Verifies that each encrypt call samples a fresh IV.
     */
    @Test
    @DisplayName("encryptReaction: each call samples a fresh IV (different output per call)")
    void encryptReactionFreshIv() {
        var parent = parent(PARENT_SECRET, PARENT_KEY_ID, false, OTHER_SENDER);
        var reaction = new ReactionMessageBuilder()
                .text("🎯")
                .build();
        var first = EncMessageFactory.encryptReaction(reaction, parent, SELF_JID);
        var second = EncMessageFactory.encryptReaction(reaction, parent, SELF_JID);
        assertNotSame(first, second);
        assertNotEquals(toHex(first.encIv().orElseThrow()), toHex(second.encIv().orElseThrow()));
        assertNotEquals(toHex(first.encPayload().orElseThrow()), toHex(second.encPayload().orElseThrow()));
    }

    /**
     * Verifies that a missing parent secret throws
     * {@link IllegalArgumentException}.
     */
    @Test
    @DisplayName("encryptReaction: missing parent secret throws IllegalArgumentException")
    void encryptReactionMissingSecret() {
        var parent = parent(null, PARENT_KEY_ID, false, OTHER_SENDER);
        var reaction = new ReactionMessageBuilder().text("👍").build();
        var ex = assertThrows(IllegalArgumentException.class,
                () -> EncMessageFactory.encryptReaction(reaction, parent, SELF_JID));
        assertTrue(ex.getMessage().contains("messageSecret"));
    }

    /**
     * Verifies that {@code null} reaction, parent, or self throws
     * {@link NullPointerException}.
     */
    @Test
    @DisplayName("encryptReaction: null reaction / parent / self all throw NullPointerException")
    void encryptReactionNullArgs() {
        var parent = parent(PARENT_SECRET, PARENT_KEY_ID, false, OTHER_SENDER);
        var reaction = new ReactionMessageBuilder().text("👍").build();
        assertThrows(NullPointerException.class,
                () -> EncMessageFactory.encryptReaction(null, parent, SELF_JID));
        assertThrows(NullPointerException.class,
                () -> EncMessageFactory.encryptReaction(reaction, null, SELF_JID));
        assertThrows(NullPointerException.class,
                () -> EncMessageFactory.encryptReaction(reaction, parent, null));
    }

    /**
     * Verifies the encrypted-comment wrapper shape: ciphertext present,
     * 12-byte IV, target message key propagated.
     */
    @Test
    @DisplayName("encryptComment: result wraps ciphertext + IV and propagates target message key")
    void encryptCommentShape() {
        var parent = parent(PARENT_SECRET, PARENT_KEY_ID, false, OTHER_SENDER);
        var targetKey = new MessageKeyBuilder()
                .id("3EB0TARGET")
                .fromMe(false)
                .parentJid(CHAT_JID)
                .build();
        var comment = new CommentMessageBuilder()
                .messageContainer(MessageContainer.of("hi"))
                .targetMessageKey(targetKey)
                .build();

        var enc = EncMessageFactory.encryptComment(comment, parent, SELF_JID);

        assertNotNull(enc);
        assertEquals(targetKey, enc.targetMessageKey().orElseThrow());
        assertTrue(enc.encPayload().orElseThrow().length > 0);
        assertEquals(12, enc.encIv().orElseThrow().length);
    }

    /**
     * Verifies that encrypting a comment with no inner message throws
     * {@link IllegalArgumentException}.
     */
    @Test
    @DisplayName("encryptComment: missing inner message throws IllegalArgumentException")
    void encryptCommentMissingInner() {
        var parent = parent(PARENT_SECRET, PARENT_KEY_ID, false, OTHER_SENDER);
        var emptyComment = new CommentMessageBuilder().build();
        var ex = assertThrows(IllegalArgumentException.class,
                () -> EncMessageFactory.encryptComment(emptyComment, parent, SELF_JID));
        assertTrue(ex.getMessage().toLowerCase().contains("message"));
    }

    /**
     * Verifies that encrypting a comment against a parent missing
     * {@code messageSecret} throws {@link IllegalArgumentException}.
     */
    @Test
    @DisplayName("encryptComment: missing parent secret throws IllegalArgumentException")
    void encryptCommentMissingSecret() {
        var parent = parent(null, PARENT_KEY_ID, false, OTHER_SENDER);
        var comment = new CommentMessageBuilder()
                .messageContainer(MessageContainer.of("hi"))
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> EncMessageFactory.encryptComment(comment, parent, SELF_JID));
    }

    /**
     * Verifies key isolation: a ciphertext bound to one parent secret
     * cannot be decrypted under a different secret.
     */
    @Test
    @DisplayName("encryptComment: ciphertext is bound to the parent secret; different secret breaks decrypt")
    void encryptCommentKeyIsolation() {
        var parent = parent(PARENT_SECRET, PARENT_KEY_ID, false, OTHER_SENDER);
        var comment = new CommentMessageBuilder()
                .messageContainer(MessageContainer.of("isolated"))
                .build();
        var enc = EncMessageFactory.encryptComment(comment, parent, SELF_JID);

        var encryptedAddon = new MessageEncryptedAddon(enc.encPayload().orElseThrow(), enc.encIv().orElseThrow());
        assertThrows(RuntimeException.class, () -> MessageAddonEncryption.decrypt(
                encryptedAddon, OTHER_SECRET, PARENT_KEY_ID, OTHER_SENDER, SELF_JID,
                MessageAddonType.ENC_COMMENT));
    }

    /**
     * Verifies that {@code null} comment, parent, or self throws
     * {@link NullPointerException}.
     */
    @Test
    @DisplayName("encryptComment: null arguments throw NullPointerException")
    void encryptCommentNullArgs() {
        var parent = parent(PARENT_SECRET, PARENT_KEY_ID, false, OTHER_SENDER);
        var comment = new CommentMessageBuilder()
                .messageContainer(MessageContainer.of("hi"))
                .build();
        assertThrows(NullPointerException.class,
                () -> EncMessageFactory.encryptComment(null, parent, SELF_JID));
        assertThrows(NullPointerException.class,
                () -> EncMessageFactory.encryptComment(comment, null, SELF_JID));
        assertThrows(NullPointerException.class,
                () -> EncMessageFactory.encryptComment(comment, parent, null));
    }

    /**
     * Verifies the encrypted poll-vote wrapper shape: ciphertext present,
     * 12-byte IV.
     */
    @Test
    @DisplayName("encryptPollVote: result has ciphertext + 12-byte IV")
    void encryptPollVoteShape() {
        var poll = parent(PARENT_SECRET, PARENT_KEY_ID, false, OTHER_SENDER);
        var encValue = EncMessageFactory.encryptPollVote(List.of("Option A"), poll, SELF_JID);
        assertNotNull(encValue.encPayload().orElseThrow());
        assertTrue(encValue.encPayload().orElseThrow().length > 0);
        assertEquals(12, encValue.encIv().orElseThrow().length);
    }

    /**
     * Verifies that the poll-vote ciphertext is AAD-bound to the voter JID:
     * decrypt under a different voter is rejected.
     */
    @Test
    @DisplayName("encryptPollVote: encryption is AAD-bound; decrypt with a different voter is rejected")
    void encryptPollVoteAadBindsVoter() {
        var poll = parent(PARENT_SECRET, PARENT_KEY_ID, false, OTHER_SENDER);
        var encValue = EncMessageFactory.encryptPollVote(List.of("Option A"), poll, SELF_JID);

        var encryptedAddon = new MessageEncryptedAddon(encValue.encPayload().orElseThrow(), encValue.encIv().orElseThrow());
        var otherVoter = Jid.of("12025550900@s.whatsapp.net");
        assertThrows(RuntimeException.class, () -> MessageAddonEncryption.decrypt(
                encryptedAddon, PARENT_SECRET, PARENT_KEY_ID, OTHER_SENDER, otherVoter,
                MessageAddonType.POLL_VOTE));
    }

    /**
     * Verifies that encrypting a vote with a missing parent secret throws
     * {@link IllegalArgumentException}.
     */
    @Test
    @DisplayName("encryptPollVote: missing parent secret throws IllegalArgumentException")
    void encryptPollVoteMissingSecret() {
        var poll = parent(null, PARENT_KEY_ID, false, OTHER_SENDER);
        assertThrows(IllegalArgumentException.class,
                () -> EncMessageFactory.encryptPollVote(List.of("Option A"), poll, SELF_JID));
    }

    /**
     * Verifies that a {@code null} entry inside {@code selectedOptions}
     * throws {@link NullPointerException}.
     */
    @Test
    @DisplayName("encryptPollVote: null selected entry throws NullPointerException")
    void encryptPollVoteNullEntry() {
        var poll = parent(PARENT_SECRET, PARENT_KEY_ID, false, OTHER_SENDER);
        var withNull = Arrays.asList("A", null);
        assertThrows(NullPointerException.class,
                () -> EncMessageFactory.encryptPollVote(withNull, poll, SELF_JID));
    }

    /**
     * Verifies that encrypting with an empty option list still produces a
     * valid envelope (zero votes).
     *
     * @implNote An empty {@code PollVoteMessage} encodes to a very small
     * protobuf body, but the AES-GCM tag adds 16 bytes, so the resulting
     * ciphertext is never empty.
     */
    @Test
    @DisplayName("encryptPollVote: empty option list still produces a valid envelope (zero votes)")
    void encryptPollVoteEmptyOptions() {
        var poll = parent(PARENT_SECRET, PARENT_KEY_ID, false, OTHER_SENDER);
        var encValue = EncMessageFactory.encryptPollVote(List.of(), poll, SELF_JID);
        assertNotNull(encValue.encPayload().orElseThrow());
        assertTrue(encValue.encPayload().orElseThrow().length >= 16,
                "ciphertext must include the 16-byte GCM tag even for empty input");
    }

    /**
     * Verifies that {@code null} selected options, poll, or voter throws
     * {@link NullPointerException}.
     */
    @Test
    @DisplayName("encryptPollVote: null arguments throw NullPointerException")
    void encryptPollVoteNullArgs() {
        var poll = parent(PARENT_SECRET, PARENT_KEY_ID, false, OTHER_SENDER);
        assertThrows(NullPointerException.class,
                () -> EncMessageFactory.encryptPollVote(null, poll, SELF_JID));
        assertThrows(NullPointerException.class,
                () -> EncMessageFactory.encryptPollVote(List.of("A"), null, SELF_JID));
        assertThrows(NullPointerException.class,
                () -> EncMessageFactory.encryptPollVote(List.of("A"), poll, null));
    }

    /**
     * Verifies sender resolution under {@code fromMe == true}: the addon
     * encrypts under {@code selfJid}.
     *
     * @apiNote Mirrors {@code WAWebMsgGetters.getOriginalSender}, which
     * prefers the {@code fromMe} branch.
     */
    @Test
    @DisplayName("sender resolution: fromMe=true parent encrypts under selfJid (mirrors WAWebMsgGetters.getOriginalSender)")
    void senderResolutionFromMeUsesSelf() {
        var parentFromMe = parent(PARENT_SECRET, PARENT_KEY_ID, true, null);
        var reaction = new ReactionMessageBuilder().text("✓").build();
        var enc = EncMessageFactory.encryptReaction(reaction, parentFromMe, SELF_JID);
        var addon = new MessageEncryptedAddon(enc.encPayload().orElseThrow(), enc.encIv().orElseThrow());

        var recovered = MessageAddonEncryption.decrypt(
                addon, PARENT_SECRET, PARENT_KEY_ID, SELF_JID, SELF_JID,
                MessageAddonType.ENC_REACTION);
        assertTrue(recovered.length > 0);

        assertThrows(RuntimeException.class, () -> MessageAddonEncryption.decrypt(
                addon, PARENT_SECRET, PARENT_KEY_ID, CHAT_JID, SELF_JID,
                MessageAddonType.ENC_REACTION));
    }

    /**
     * Verifies sender resolution under {@code fromMe == false} with an
     * explicit {@code senderJid}: the addon encrypts under that sender.
     */
    @Test
    @DisplayName("sender resolution: fromMe=false with explicit senderJid encrypts under senderJid")
    void senderResolutionUsesExplicitSender() {
        var parent = parent(PARENT_SECRET, PARENT_KEY_ID, false, OTHER_SENDER);
        var reaction = new ReactionMessageBuilder().text("✓").build();
        var enc = EncMessageFactory.encryptReaction(reaction, parent, SELF_JID);
        var addon = new MessageEncryptedAddon(enc.encPayload().orElseThrow(), enc.encIv().orElseThrow());

        var recovered = MessageAddonEncryption.decrypt(
                addon, PARENT_SECRET, PARENT_KEY_ID, OTHER_SENDER, SELF_JID,
                MessageAddonType.ENC_REACTION);
        assertTrue(recovered.length > 0);

        assertThrows(RuntimeException.class, () -> MessageAddonEncryption.decrypt(
                addon, PARENT_SECRET, PARENT_KEY_ID, SELF_JID, SELF_JID,
                MessageAddonType.ENC_REACTION));
    }

    /**
     * Verifies sender resolution under {@code fromMe == false} with no
     * explicit {@code senderJid}: the addon falls back to the
     * {@code parentJid}.
     */
    @Test
    @DisplayName("sender resolution: fromMe=false with no senderJid falls back to parentJid")
    void senderResolutionFallsBackToParentJid() {
        var parent = parent(PARENT_SECRET, PARENT_KEY_ID, false, null);
        var reaction = new ReactionMessageBuilder().text("✓").build();
        var enc = EncMessageFactory.encryptReaction(reaction, parent, SELF_JID);
        var addon = new MessageEncryptedAddon(enc.encPayload().orElseThrow(), enc.encIv().orElseThrow());

        var recovered = MessageAddonEncryption.decrypt(
                addon, PARENT_SECRET, PARENT_KEY_ID, CHAT_JID, SELF_JID,
                MessageAddonType.ENC_REACTION);
        assertTrue(recovered.length > 0);
    }

    /**
     * Builds a {@link ChatMessageInfo} configured as the parent for an
     * addon.
     *
     * @param secret    the message secret, or {@code null} to leave unset
     * @param keyId     the parent key id
     * @param fromMe    whether the parent was authored by the current user
     * @param senderJid the parent key's {@code senderJid}, or {@code null}
     *                  to leave unset
     * @return the configured parent message
     */
    private static ChatMessageInfo parent(byte[] secret, String keyId, boolean fromMe, Jid senderJid) {
        var key = new MessageKeyBuilder()
                .id(keyId)
                .fromMe(fromMe)
                .parentJid(CHAT_JID)
                .senderJid(senderJid)
                .build();
        var builder = new ChatMessageInfoBuilder()
                .key(key)
                .message(MessageContainer.of("parent body"));
        if (secret != null) {
            builder.messageSecret(secret);
        }
        return builder.build();
    }

    /**
     * Returns a byte array of {@code len} bytes filled with {@code b}.
     *
     * @param len the length
     * @param b   the fill byte
     * @return the filled array
     */
    private static byte[] repeatedByte(int len, byte b) {
        var out = new byte[len];
        Arrays.fill(out, b);
        return out;
    }

    /**
     * Returns the lowercase hex string for {@code bytes}.
     *
     * @param bytes the input
     * @return the hex form
     */
    private static String toHex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (var b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
