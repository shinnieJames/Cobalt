package com.github.auties00.cobalt.migration;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link LidMigrationService#getAlternateMsgKey}.
 *
 * @apiNote
 * Pins the alternate-key helper that swaps the participant (for
 * groups, broadcasts, and status) or the remote (for 1:1) into
 * the opposite addressing mode so that two stored copies of the
 * same message (one keyed by PN, one keyed by LID) can be
 * reconciled. Mirrors WA Web's
 * {@code WAWebLidMigrationUtils.getAlternateMsgKey}.
 *
 * @implNote
 * This implementation builds an isolated harness via
 * {@link MigrationFixtures#temporaryStore(Jid, Jid)} and seeds
 * the store with the mappings each branch needs before driving
 * the helper.
 */
@DisplayName("LidMigrationService.getAlternateMsgKey")
class LidMigrationServiceMessageKeyAlternatesTest {

    private static final Jid SELF_PN = Jid.of("19254863482@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594056@lid");
    private static final Jid PEER_PN = Jid.of("393495089819@s.whatsapp.net");
    private static final Jid PEER_LID = Jid.of("258252122116273@lid");
    private static final Jid OTHER_PN = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid OTHER_LID = Jid.of("999999999999999@lid");
    private static final Jid GROUP = Jid.of("120363012345678901@g.us");
    private static final Jid BROADCAST = Jid.of("19254863482@broadcast");
    private static final Jid STATUS_BROADCAST = Jid.of("status@broadcast");
    private static final Jid PEER_PN_DEVICE_2 = Jid.of("393495089819:2@s.whatsapp.net");

    /**
     * Bundles the test client and the service under test.
     *
     * @param client  the test client harness
     * @param service the service under test
     */
    private record Harness(TestWhatsAppClient client, LidMigrationService service) {}

    /**
     * Builds a fresh harness with a default
     * {@link TestABPropsService}.
     *
     * @return a fresh {@link Harness}
     */
    private static Harness build() {
        var props = TestABPropsService.builder().build();
        var store = MigrationFixtures.temporaryStore(SELF_PN, SELF_LID);
        var client = TestWhatsAppClient.create().withStore(store);
        var wamService = new DefaultWamService(client, props);
        var service = new LidMigrationService(client, props, wamService);
        return new Harness(client, service);
    }

    /**
     * Verifies that returns null for a null message key.
     */
    @Test
    @DisplayName("returns null for a null message key")
    void nullKey() {
        assertNull(build().service.getAlternateMsgKey(null));
    }

    /**
     * Verifies that returns null when no parent JID is set.
     */
    @Test
    @DisplayName("returns null when no parent JID is set")
    void noParent() {
        var key = new MessageKeyBuilder().id("ABC").fromMe(true).build();
        assertNull(build().service.getAlternateMsgKey(key));
    }

    /**
     * Verifies that returns null for a remote that is neither user-wid nor group/broadcast (e.g. newsletter).
     */
    @Test
    @DisplayName("returns null for a remote that is neither user-wid nor group/broadcast (e.g. newsletter)")
    void unsupportedRemote() {
        var newsletter = Jid.of("120363023456789012@newsletter");
        var key = new MessageKeyBuilder().id("ABC").parentJid(newsletter).build();
        assertNull(build().service.getAlternateMsgKey(key));
    }

    /**
     * Verifies that 1:1 PN remote -> swaps to LID when mapping is known.
     */
    @Test
    @DisplayName("1:1 PN remote -> swaps to LID when mapping is known")
    void oneOnOnePnToLid() {
        var h = build();
        h.client.store().registerLidMapping(PEER_PN, PEER_LID);
        var key = new MessageKeyBuilder()
                .id("ABC")
                .fromMe(true)
                .parentJid(PEER_PN)
                .build();
        var alt = h.service.getAlternateMsgKey(key);
        assertEquals(PEER_LID, alt.parentJid().orElseThrow());
        assertEquals("ABC", alt.id().orElseThrow());
        assertTrue(alt.fromMe());
    }

    /**
     * Verifies that 1:1 LID remote -> swaps to PN when mapping is known.
     */
    @Test
    @DisplayName("1:1 LID remote -> swaps to PN when mapping is known")
    void oneOnOneLidToPn() {
        var h = build();
        h.client.store().registerLidMapping(PEER_PN, PEER_LID);
        var key = new MessageKeyBuilder()
                .id("XYZ")
                .fromMe(false)
                .parentJid(PEER_LID)
                .build();
        var alt = h.service.getAlternateMsgKey(key);
        assertEquals(PEER_PN, alt.parentJid().orElseThrow());
    }

    /**
     * Verifies that 1:1 PN remote -> returns null when no mapping exists.
     */
    @Test
    @DisplayName("1:1 PN remote -> returns null when no mapping exists")
    void oneOnOnePnUnmapped() {
        var h = build();
        var key = new MessageKeyBuilder()
                .id("ABC")
                .parentJid(PEER_PN)
                .build();
        assertNull(h.service.getAlternateMsgKey(key));
    }

    /**
     * Verifies that group -> swaps participant when mapping is known.
     */
    @Test
    @DisplayName("group -> swaps participant when mapping is known")
    void groupSwapsParticipant() {
        var h = build();
        h.client.store().registerLidMapping(PEER_PN, PEER_LID);
        var key = new MessageKeyBuilder()
                .id("G1")
                .parentJid(GROUP)
                .senderJid(PEER_PN)
                .build();
        var alt = h.service.getAlternateMsgKey(key);
        assertEquals(GROUP, alt.parentJid().orElseThrow(), "remote unchanged for group alt");
        assertEquals(PEER_LID, alt.senderJid().orElseThrow(), "participant swapped to LID");
    }

    /**
     * Verifies that group -> strips device suffix from participant before lookup.
     */
    @Test
    @DisplayName("group -> strips device suffix from participant before lookup")
    void groupStripsDeviceFromParticipant() {
        var h = build();
        h.client.store().registerLidMapping(PEER_PN, PEER_LID);
        var key = new MessageKeyBuilder()
                .id("G2")
                .parentJid(GROUP)
                .senderJid(PEER_PN_DEVICE_2)
                .build();
        var alt = h.service.getAlternateMsgKey(key);
        assertEquals(PEER_LID, alt.senderJid().orElseThrow(),
                "device-suffixed PN participant is normalised to user-level LID");
    }

    /**
     * Verifies that group -> returns null when participant has no alternate.
     */
    @Test
    @DisplayName("group -> returns null when participant has no alternate")
    void groupNoParticipantAlternate() {
        var h = build();
        // No mapping registered.
        var key = new MessageKeyBuilder()
                .id("G3")
                .parentJid(GROUP)
                .senderJid(OTHER_PN)
                .build();
        assertNull(h.service.getAlternateMsgKey(key));
    }

    /**
     * Verifies that group -> returns null when no participant is recorded.
     */
    @Test
    @DisplayName("group -> returns null when no participant is recorded")
    void groupNoParticipant() {
        var h = build();
        var key = new MessageKeyBuilder()
                .id("G4")
                .parentJid(GROUP)
                .build();
        // senderJid() falls back to parentJid (GROUP). getRawParticipant returns null
        // because sender==parent, so the alternate cannot be built.
        assertNull(h.service.getAlternateMsgKey(key));
    }

    /**
     * Verifies that broadcast -> swaps participant when mapping is known.
     */
    @Test
    @DisplayName("broadcast -> swaps participant when mapping is known")
    void broadcastSwapsParticipant() {
        var h = build();
        h.client.store().registerLidMapping(PEER_PN, PEER_LID);
        var key = new MessageKeyBuilder()
                .id("B1")
                .parentJid(BROADCAST)
                .senderJid(PEER_PN)
                .build();
        var alt = h.service.getAlternateMsgKey(key);
        assertEquals(BROADCAST, alt.parentJid().orElseThrow());
        assertEquals(PEER_LID, alt.senderJid().orElseThrow());
    }

    /**
     * Verifies that status broadcast -> swaps participant when mapping is known.
     */
    @Test
    @DisplayName("status broadcast -> swaps participant when mapping is known")
    void statusBroadcastSwapsParticipant() {
        var h = build();
        h.client.store().registerLidMapping(OTHER_PN, OTHER_LID);
        var key = new MessageKeyBuilder()
                .id("S1")
                .parentJid(STATUS_BROADCAST)
                .senderJid(OTHER_PN)
                .build();
        var alt = h.service.getAlternateMsgKey(key);
        assertEquals(STATUS_BROADCAST, alt.parentJid().orElseThrow());
        assertEquals(OTHER_LID, alt.senderJid().orElseThrow());
    }
}
