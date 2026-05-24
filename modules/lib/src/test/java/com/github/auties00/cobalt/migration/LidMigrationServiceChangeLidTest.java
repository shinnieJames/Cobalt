package com.github.auties00.cobalt.migration;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link LidMigrationService#changeLid(Jid, Jid, Jid)} and
 * {@link LidMigrationService#registerOriginalLid(Jid, Jid)}.
 *
 * @apiNote
 * Pins the two LID-rotation entry points: {@code changeLid} keeps the
 * primary cache, the bidirectional store mapping, the contact LID,
 * and any phone-keyed chat in sync when a contact's LID rotates;
 * {@code registerOriginalLid} populates the fallback cache that the
 * migration cascade consults when neither the primary mapping nor a
 * local mapping is known.
 *
 * @implNote
 * This implementation uses an isolated harness per test through
 * {@link MigrationFixtures#temporaryStore(Jid, Jid)} so no rotation
 * bleeds across cases.
 */
@DisplayName("LidMigrationService.changeLid / registerOriginalLid")
class LidMigrationServiceChangeLidTest {

    private static final Jid SELF_PN = Jid.of("19254863482@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594056@lid");
    private static final Jid PEER_PN = Jid.of("393495089819@s.whatsapp.net");
    private static final Jid PEER_LID_OLD = Jid.of("258252122116273@lid");
    private static final Jid PEER_LID_NEW = Jid.of("999999999999999@lid");

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
     * Verifies that changeLid is a no-op when phoneJid is null.
     */
    @Test
    @DisplayName("changeLid is a no-op when phoneJid is null")
    void changeLidNullPhoneJid() {
        var h = build();
        h.service.changeLid(null, PEER_LID_NEW, PEER_LID_OLD);
        // Nothing throws; nothing populated.
        assertFalse(h.client.store().findLidByPhone(PEER_PN).isPresent());
    }

    /**
     * Verifies that changeLid is a no-op when newLid is null.
     */
    @Test
    @DisplayName("changeLid is a no-op when newLid is null")
    void changeLidNullNewLid() {
        var h = build();
        h.service.changeLid(PEER_PN, null, PEER_LID_OLD);
        assertFalse(h.client.store().findLidByPhone(PEER_PN).isPresent());
    }

    /**
     * Verifies that changeLid mirrors the new LID onto store, contact, chat, and lookupLid cache.
     */
    @Test
    @DisplayName("changeLid mirrors the new LID onto store, contact, chat, and lookupLid cache")
    void changeLidPropagatesNewLid() {
        var h = build();
        var store = h.client.store();

        // Pre-existing state: contact + chat under the phone JID.
        store.addNewContact(PEER_PN);
        store.addNewChat(PEER_PN);

        h.service.changeLid(PEER_PN, PEER_LID_NEW, PEER_LID_OLD);

        // Bidirectional store mapping.
        assertEquals(PEER_LID_NEW, store.findLidByPhone(PEER_PN).orElseThrow());

        // Contact LID set.
        var contact = store.findContactByJid(PEER_PN).orElseThrow();
        assertEquals(PEER_LID_NEW, contact.lid().orElseThrow());

        // Chat LID + reverse phone-number JID set.
        var chat = store.findChatByJid(PEER_PN).orElseThrow();
        assertEquals(PEER_LID_NEW, chat.lid().orElseThrow());
        assertEquals(PEER_PN, chat.phoneNumberJid().orElseThrow());

        // lookupLid reads the primary cache first; the cache must contain the new LID.
        assertEquals(PEER_LID_NEW, h.service.lookupLid(PEER_PN).orElseThrow());
    }

    /**
     * Verifies that changeLid still works when only the contact is pre-existing (no chat).
     */
    @Test
    @DisplayName("changeLid still works when only the contact is pre-existing (no chat)")
    void changeLidContactOnly() {
        var h = build();
        h.client.store().addNewContact(PEER_PN);

        h.service.changeLid(PEER_PN, PEER_LID_NEW, null);

        assertEquals(PEER_LID_NEW, h.client.store().findLidByPhone(PEER_PN).orElseThrow());
        assertEquals(PEER_LID_NEW, h.client.store().findContactByJid(PEER_PN).orElseThrow().lid().orElseThrow());
        assertFalse(h.client.store().findChatByJid(PEER_PN).isPresent());
    }

    /**
     * Verifies that registerOriginalLid is a no-op when phoneJid is null.
     */
    @Test
    @DisplayName("registerOriginalLid is a no-op when phoneJid is null")
    void registerOriginalLidNullPhone() {
        var h = build();
        h.service.registerOriginalLid(null, PEER_LID_OLD);
        // No observable change; resolveThread fallback cannot find this.
    }

    /**
     * Verifies that registerOriginalLid is a no-op when lid is null.
     */
    @Test
    @DisplayName("registerOriginalLid is a no-op when lid is null")
    void registerOriginalLidNullLid() {
        var h = build();
        h.service.registerOriginalLid(PEER_PN, null);
        // Same: nothing observable.
    }

    /**
     * Verifies that registerOriginalLid populates the originalLidCache for use by resolveThread.
     */
    @Test
    @DisplayName("registerOriginalLid populates the originalLidCache for use by resolveThread")
    void registerOriginalLidPopulatesFallback() {
        var h = build();
        var store = h.client.store();

        // Pre-existing state: PN-only chat, no primary cache hit, no localLid -> fallback should kick in.
        store.addNewChat(PEER_PN);
        h.service.registerOriginalLid(PEER_PN, PEER_LID_OLD);

        var chat = store.findChatByJid(PEER_PN).orElseThrow();
        var resolution = h.service.resolveThread(chat);

        assertInstanceOfMigrate(resolution, PEER_LID_OLD.toUserJid());
    }

    /**
     * Asserts that the resolution is a
     * {@link LidMigrationResolution.Migrate} whose
     * {@link LidMigrationResolution.Migrate#targetLid()} equals the
     * expected value.
     *
     * @param resolution        the resolution returned by
     *                          {@link LidMigrationService#resolveThread}
     * @param expectedTargetLid the expected target LID at user level
     */
    private static void assertInstanceOfMigrate(LidMigrationResolution resolution, Jid expectedTargetLid) {
        assertTrue(resolution instanceof LidMigrationResolution.Migrate,
                "expected Migrate, got " + resolution);
        var migrate = (LidMigrationResolution.Migrate) resolution;
        assertEquals(expectedTargetLid, migrate.targetLid(),
                "originalLidCache fallback returns the cached LID at user level");
    }
}
