package com.github.auties00.cobalt.message.receive.crypto;

import com.github.auties00.cobalt.model.jid.Jid;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Parity tests for {@link SenderKeyNameFactory} against WhatsApp Web's
 * {@code WAWebSignalCommonUtils.createSignalLikeSenderKeyName}.
 *
 * @apiNote
 * Verifies that the {@link com.github.auties00.libsignal.groups.SignalSenderKeyName}
 * exposed to the libsignal group cipher is keyed by the same {@code (groupId,
 * senderAddress)} pair WhatsApp Web emits, so a sender-key written by either side
 * can be read by the other.
 *
 * @implNote
 * Pure-function tests on synthetic JIDs; no fixtures or live state are needed
 * because the factory only consumes the user and device portions of each JID.
 */
@DisplayName("SenderKeyNameFactory")
class SenderKeyNameFactoryTest {

    private static final Jid GROUP = Jid.of("120363023250764418@g.us");
    private static final Jid SENDER_PRIMARY = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid SENDER_COMPANION = Jid.of("12025550100:73@s.whatsapp.net");

    /**
     * Verifies that the resulting groupId equals the group JID's string form.
     */
    @Test
    @DisplayName("create(group, sender): groupId is the group JID string form")
    void groupIdIsGroupJidString() {
        var name = SenderKeyNameFactory.create(GROUP, SENDER_PRIMARY);
        assertNotNull(name);
        assertEquals(GROUP.toString(), name.groupId());
    }

    /**
     * Verifies that a primary device JID produces the expected user/device pair on
     * the sender address.
     */
    @Test
    @DisplayName("create(group, sender): sender address has user + device 0 for primary device JID")
    void primaryDeviceAddress() {
        var name = SenderKeyNameFactory.create(GROUP, SENDER_PRIMARY);
        assertEquals(SENDER_PRIMARY.user(), name.sender().name());
        assertEquals(SENDER_PRIMARY.device(), name.sender().id(),
                "primary device JIDs encode device id 0");
    }

    /**
     * Verifies that a companion device id propagates onto the sender address.
     */
    @Test
    @DisplayName("create(group, sender): companion device id propagates")
    void companionDeviceAddress() {
        var name = SenderKeyNameFactory.create(GROUP, SENDER_COMPANION);
        assertEquals("12025550100", name.sender().name());
        assertEquals(73, name.sender().id(),
                "companion device id (73) must propagate into the sender address");
    }

    /**
     * Verifies that different groups produce distinct sender-key names.
     */
    @Test
    @DisplayName("different group then different sender-key name")
    void groupDistinguishes() {
        var first = SenderKeyNameFactory.create(GROUP, SENDER_PRIMARY);
        var second = SenderKeyNameFactory.create(Jid.of("120363099999999999@g.us"), SENDER_PRIMARY);
        Assertions.assertNotEquals(first.groupId(), second.groupId(),
                "different group JID then different groupId on the SenderKeyName");
    }

    /**
     * Verifies that the same user on a different device produces a distinct
     * sender-key name.
     */
    @Test
    @DisplayName("different sender device then different sender-key name")
    void senderDeviceDistinguishes() {
        var primary = SenderKeyNameFactory.create(GROUP, SENDER_PRIMARY);
        var companion = SenderKeyNameFactory.create(GROUP, SENDER_COMPANION);
        Assertions.assertNotEquals(
                primary.sender().id(), companion.sender().id(),
                "device id must distinguish sender addresses for the same user");
    }
}
