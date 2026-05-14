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
 * Tests for {@link MessageDedup} and {@link PendingMessageKey}, mirroring
 * {@code WAWebMessageDedupUtils} and {@code WAWebPendingMessageKey}.
 *
 * <p>Cells exercised:
 *
 * <ul>
 *   <li>composite key shape (fromMe / parentJid / id / [participant] /
 *       timestamp / sorted encs);</li>
 *   <li>refcount semantics on add and remove;</li>
 *   <li>{@link MessageDedup#maybeClear} only triggers when the offline
 *       counter is exactly zero;</li>
 *   <li>{@link MessageDedup#isPending} hits with and without composite
 *       construction;</li>
 *   <li>null-input contract on all entry points;</li>
 *   <li>encryption-type sensitivity: same message id under different
 *       {@code <enc>} variants yields distinct dedup keys.</li>
 * </ul>
 */
@DisplayName("MessageDedup + PendingMessageKey")
class MessageDedupTest {
    private static final Jid CHAT = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid SELF = Jid.of("393495089819@s.whatsapp.net");
    private static final Instant T = Instant.ofEpochSecond(1700000000L);

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

    @Test
    @DisplayName("composite key omits participant when it matches parentJid (one-to-one chats)")
    void compositeKeyOmitsParticipantWhenSameAsParent() {
        // In 1:1 chats the sender's JID equals the chat JID; WAWebMsgKey.toString
        // suppresses the participant suffix in that case.
        var key = msgKey(false, CHAT, "ID1", CHAT);
        var composite = PendingMessageKey.create(key, T, List.of());

        assertFalse(composite.contains(CHAT + "_" + CHAT),
                "participant suffix must collapse when participant == parentJid");
    }

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

    @Test
    @DisplayName("same id under different enc variants produces distinct dedup keys")
    void encryptionTypeChangesKey() {
        var key = msgKey(false, CHAT, "ID4", null);
        var pkmsg = PendingMessageKey.create(key, T, List.of(payload(MessageEncryptionType.PKMSG, 0)));
        var msg = PendingMessageKey.create(key, T, List.of(payload(MessageEncryptionType.MSG, 0)));
        assertNotEquals(pkmsg, msg,
                "PKMSG and MSG variants of the same logical id must dedup separately");
    }

    @Test
    @DisplayName("same id with different retry counts produces distinct dedup keys")
    void retryCountChangesKey() {
        var key = msgKey(false, CHAT, "ID5", null);
        var retry0 = PendingMessageKey.create(key, T, List.of(payload(MessageEncryptionType.PKMSG, 0)));
        var retry1 = PendingMessageKey.create(key, T, List.of(payload(MessageEncryptionType.PKMSG, 1)));
        assertNotEquals(retry0, retry1,
                "different retry counts of the same enc variant must dedup separately");
    }

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

    @Test
    @DisplayName("remove decrements refcount; key disappears at zero")
    void removeDecrements() {
        var dedup = new MessageDedup();
        dedup.add("KEY");
        dedup.add("KEY"); // refcount=2
        dedup.remove("KEY"); // refcount=1
        assertTrue(dedup.isPending("KEY"));
        dedup.remove("KEY"); // refcount=0 → evict
        assertFalse(dedup.isPending("KEY"), "refcount reaching zero must remove the entry");
        assertEquals(0, dedup.size());
    }

    @Test
    @DisplayName("remove on unknown key is a no-op")
    void removeUnknownIsNoop() {
        var dedup = new MessageDedup();
        dedup.remove("KEY"); // must not throw
        assertEquals(0, dedup.size());
    }

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

    @Test
    @DisplayName("clear() unconditionally empties the cache")
    void clearForce() {
        var dedup = new MessageDedup();
        dedup.add("X");
        dedup.add("Y");
        dedup.clear();
        assertEquals(0, dedup.size());
    }

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

    private static MessageReceiveEncryptedPayload payload(MessageEncryptionType type, int retry) {
        return new MessageReceiveEncryptedPayload(type, null, new byte[]{0}, retry, false);
    }
}
