package com.github.auties00.cobalt.message.dedup;

import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveEncryptedPayload;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link MessageDedup} and {@link PendingMessageKey}, mirroring
 * {@code WAWebMessageDedupUtils} and {@code WAWebPendingMessageKey}.
 *
 * @apiNote Coverage spans composite-key shape (fromMe / parentJid / id /
 * optional participant / timestamp / encs), refcount semantics on
 * {@code add} and {@code remove},
 * {@link MessageDedup#maybeClear(int)} triggering only at zero, the
 * composite-overload variant of {@link MessageDedup#isPending(MessageKey, Instant, List)},
 * null-input contract on every entry point, and the
 * encryption-type-sensitivity invariant: the same message id under
 * different {@code <enc>} variants yields distinct dedup keys.
 *
 * @implNote The fixtures build messages with {@link MessageKeyBuilder} and
 * synthesise encrypted-payload stubs through {@link #payload}, avoiding any
 * dependency on a captured stanza corpus; the test runs in-process and
 * never opens a socket.
 */
@DisplayName("MessageDedup + PendingMessageKey")
class MessageDedupTest {
    /**
     * Chat JID used by every test in this suite.
     */
    private static final Jid CHAT = Jid.of("12025550100@s.whatsapp.net");

    /**
     * Self JID used as the {@code senderJid} in group-message cases.
     */
    private static final Jid SELF = Jid.of("393495089819@s.whatsapp.net");

    /**
     * Reference timestamp used by every test in this suite.
     */
    private static final Instant T = Instant.ofEpochSecond(1700000000L);

    /**
     * Verifies the canonical composite-key shape
     * {@code fromMe_remote_id_timestamp_enc:retry}.
     */
    @Test
    @DisplayName("composite key includes fromMe, parentJid, id, timestamp, and enc segments")
    void compositeKeyShape() {
        var key = msgKey(false, CHAT, "ABCDEF", null);
        var encs = List.of(payload(MessageEncryptionType.PKMSG, 0));
        var composite = PendingMessageKey.create(key, T, encs);

        assertEquals(
                "false_" + CHAT + "_ABCDEF_" + T.getEpochSecond() + "_pkmsg:0",
                composite,
                "composite key follows fromMe_parent_id_ts_enc:retry");
    }

    /**
     * Verifies that the participant suffix is suppressed when it equals the
     * parent JID (one-to-one chats).
     *
     * @implNote Mirrors {@code WAWebMsgKey.prototype.toString}, which
     * suppresses the participant segment when the sender's JID equals the
     * chat JID.
     */
    @Test
    @DisplayName("composite key omits participant when it matches parentJid (one-to-one chats)")
    void compositeKeyOmitsParticipantWhenSameAsParent() {
        var key = msgKey(false, CHAT, "ID1", CHAT);
        var composite = PendingMessageKey.create(key, T, List.of());

        assertFalse(composite.contains(CHAT + "_" + CHAT),
                "participant suffix must collapse when participant == parentJid");
    }

    /**
     * Verifies that the participant suffix is preserved when distinct from
     * the parent JID (group chats).
     */
    @Test
    @DisplayName("composite key includes participant when distinct from parentJid (group chats)")
    void compositeKeyIncludesGroupParticipant() {
        var group = Jid.of("12025550100-1700000000@g.us");
        var sender = Jid.of("19255550000@s.whatsapp.net");
        var key = msgKey(false, group, "ID2", sender);
        var composite = PendingMessageKey.create(key, T, List.of());

        assertTrue(composite.contains(sender.toString()),
                "group messages must include the participant JID in the dedup key");
    }

    /**
     * Verifies that multiple enc variants on the same message appear as a
     * comma-joined list, in input order.
     */
    @Test
    @DisplayName("composite key reflects multiple enc variants, joined by commas in protocol order")
    void compositeKeyMultipleEncs() {
        var key = msgKey(false, CHAT, "ID3", null);
        var encs = List.of(
                payload(MessageEncryptionType.PKMSG, 1),
                payload(MessageEncryptionType.MSG, 2)
        );
        var composite = PendingMessageKey.create(key, T, encs);

        assertTrue(composite.endsWith("pkmsg:1,msg:2"),
                "enc segments must follow the input order, joined by comma: " + composite);
    }

    /**
     * Verifies that the same logical id under different
     * {@link MessageEncryptionType} variants produces distinct dedup keys.
     */
    @Test
    @DisplayName("same id under different enc variants produces distinct dedup keys")
    void encryptionTypeChangesKey() {
        var key = msgKey(false, CHAT, "ID4", null);
        var pkmsg = PendingMessageKey.create(key, T, List.of(payload(MessageEncryptionType.PKMSG, 0)));
        var msg = PendingMessageKey.create(key, T, List.of(payload(MessageEncryptionType.MSG, 0)));
        assertNotEquals(pkmsg, msg,
                "PKMSG and MSG variants of the same logical id must dedup separately");
    }

    /**
     * Verifies that the same logical id with different retry counts
     * produces distinct dedup keys.
     */
    @Test
    @DisplayName("same id with different retry counts produces distinct dedup keys")
    void retryCountChangesKey() {
        var key = msgKey(false, CHAT, "ID5", null);
        var retry0 = PendingMessageKey.create(key, T, List.of(payload(MessageEncryptionType.PKMSG, 0)));
        var retry1 = PendingMessageKey.create(key, T, List.of(payload(MessageEncryptionType.PKMSG, 1)));
        assertNotEquals(retry0, retry1,
                "different retry counts of the same enc variant must dedup separately");
    }

    /**
     * Verifies that {@link MessageDedup#add(String)} returns an incrementing
     * refcount and that {@link MessageDedup#isPending(String)} mirrors
     * presence.
     */
    @Test
    @DisplayName("add returns incrementing refcount; isPending mirrors presence")
    void addAndRefcount() {
        var dedup = new MessageDedup();
        assertFalse(dedup.isPending("KEY"), "empty cache reports nothing pending");
        assertEquals(1, dedup.add("KEY"), "first add returns refcount=1");
        assertTrue(dedup.isPending("KEY"));
        assertEquals(2, dedup.add("KEY"), "second add returns refcount=2");
        assertEquals(1, dedup.size(), "size counts distinct keys, not the refcount sum");
    }

    /**
     * Verifies that {@link MessageDedup#remove(String)} decrements the
     * refcount and evicts the entry when the count reaches zero.
     */
    @Test
    @DisplayName("remove decrements refcount; key disappears at zero")
    void removeDecrements() {
        var dedup = new MessageDedup();
        dedup.add("KEY");
        dedup.add("KEY");
        dedup.remove("KEY");
        assertTrue(dedup.isPending("KEY"));
        dedup.remove("KEY");
        assertFalse(dedup.isPending("KEY"), "refcount reaching zero must remove the entry");
        assertEquals(0, dedup.size());
    }

    /**
     * Verifies that removing an unknown key is a no-op.
     */
    @Test
    @DisplayName("remove on unknown key is a no-op")
    void removeUnknownIsNoop() {
        var dedup = new MessageDedup();
        dedup.remove("KEY");
        assertEquals(0, dedup.size());
    }

    /**
     * Verifies that {@link MessageDedup#maybeClear(int)} clears the cache
     * only when the offline counter is exactly zero.
     */
    @Test
    @DisplayName("maybeClear(0) drops all entries; maybeClear(>0) keeps them")
    void maybeClearOnlyOnZero() {
        var dedup = new MessageDedup();
        dedup.add("A");
        dedup.add("B");
        dedup.add("C");
        assertEquals(3, dedup.size());

        dedup.maybeClear(2);
        assertEquals(3, dedup.size(), "non-zero offline counter must leave the cache intact");

        dedup.maybeClear(0);
        assertEquals(0, dedup.size(), "zero offline counter clears the cache in bulk");
    }

    /**
     * Verifies that {@link MessageDedup#clear()} unconditionally empties
     * the cache.
     */
    @Test
    @DisplayName("clear() unconditionally empties the cache")
    void clearForce() {
        var dedup = new MessageDedup();
        dedup.add("X");
        dedup.add("Y");
        dedup.clear();
        assertEquals(0, dedup.size());
    }

    /**
     * Verifies that the composite-overload of
     * {@link MessageDedup#add(MessageKey, Instant, List)} round-trips with
     * the matching {@link MessageDedup#isPending(MessageKey, Instant, List)}.
     */
    @Test
    @DisplayName("add(MessageKey, Instant, encs) delegates to the composite key path")
    void compositeAddPath() {
        var dedup = new MessageDedup();
        var key = msgKey(true, CHAT, "ID6", SELF);
        var encs = List.of(payload(MessageEncryptionType.PKMSG, 0));
        dedup.add(key, T, encs);
        assertTrue(dedup.isPending(key, T, encs),
                "isPending(key, ts, encs) must hit the entry added via add(key, ts, encs)");
    }

    /**
     * Verifies that every entry point throws {@link NullPointerException}
     * on null input.
     */
    @Test
    @DisplayName("null arguments throw NullPointerException across the API surface")
    void nullRejection() {
        var dedup = new MessageDedup();
        assertThrows(NullPointerException.class, () -> dedup.add(null));
        assertThrows(NullPointerException.class, () -> dedup.add(null, T, List.of()));
        assertThrows(NullPointerException.class,
                () -> dedup.add(msgKey(false, CHAT, "X", null), null, List.of()));
        assertThrows(NullPointerException.class,
                () -> dedup.add(msgKey(false, CHAT, "X", null), T, null));
        assertThrows(NullPointerException.class, () -> dedup.isPending(null));
        assertThrows(NullPointerException.class, () -> dedup.remove(null));
        assertThrows(NullPointerException.class,
                () -> PendingMessageKey.create(null, T, List.of()));
        assertThrows(NullPointerException.class,
                () -> PendingMessageKey.create(msgKey(false, CHAT, "X", null), null, List.of()));
        assertThrows(NullPointerException.class,
                () -> PendingMessageKey.create(msgKey(false, CHAT, "X", null), T, null));
    }

    /**
     * Builds a {@link MessageKey} configured with the given direction, chat
     * JID, id, and optional participant.
     *
     * @param fromMe whether the parent message was authored by the local
     *               user
     * @param parent the chat JID
     * @param id     the message id
     * @param sender the participant JID, or {@code null} to leave unset
     * @return the configured key
     */
    private static MessageKey msgKey(boolean fromMe, Jid parent, String id, Jid sender) {
        var builder = new MessageKeyBuilder()
                .fromMe(fromMe)
                .parentJid(parent)
                .id(id);
        if (sender != null) {
            builder.senderJid(sender);
        }
        return builder.build();
    }

    /**
     * Builds a stub {@link MessageReceiveEncryptedPayload} carrying the
     * given encryption type and retry count.
     *
     * @param type  the {@code <enc>} variant
     * @param retry the retry count
     * @return the stub payload
     */
    private static MessageReceiveEncryptedPayload payload(MessageEncryptionType type, int retry) {
        return new MessageReceiveEncryptedPayload(type, null, new byte[]{0}, retry, false);
    }
}
