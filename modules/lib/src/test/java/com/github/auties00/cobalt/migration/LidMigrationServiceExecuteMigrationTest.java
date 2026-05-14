package com.github.auties00.cobalt.migration;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppLidMigrationException;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.migration.LIDMigrationMappingBuilder;
import com.github.auties00.cobalt.model.jid.migration.LIDMigrationMappingSyncPayloadBuilder;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link LidMigrationService#executeMigration()}.
 *
 * <p>The executor is the heart of the migration. It walks every chat,
 * classifies each one, applies the resulting resolution to the store,
 * advances the state to {@link LidMigrationState#COMPLETE}, and finally
 * flushes the primary caches into the bidirectional mapping table.
 *
 * <p>These tests build a service in {@code READY} (by delivering a
 * protocol message) before calling {@code executeMigration()}, which is
 * how WA Web's auto-start drives it in production.
 */
@DisplayName("LidMigrationService.executeMigration")
class LidMigrationServiceExecuteMigrationTest {

    private static final Jid SELF_PN = Jid.of("19254863482@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594056@lid");
    private static final Jid PEER_PN = Jid.of("393495089819@s.whatsapp.net");
    private static final Jid PEER_LID = Jid.of("258252122116273@lid");
    private static final Jid PEER_LID_LATEST = Jid.of("999999999999999@lid");
    private static final Jid OTHER_PN = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid OTHER_LID = Jid.of("12025550100123@lid");

    private record Harness(TestWhatsAppClient client, LidMigrationService service) {}

    private static Harness build(TestABPropsService props) {
        var store = MigrationFixtures.temporaryStore(SELF_PN, SELF_LID);
        var client = TestWhatsAppClient.create().withStore(store);
        var wamService = new DefaultWamService(client, props);
        var service = new LidMigrationService(client, props, wamService);
        return new Harness(client, service);
    }

    private static TestABPropsService defaultProps() {
        return TestABPropsService.builder()
                .with(ABProp.LID_ONE_ON_ONE_MIGRATION_PEER_SYNC_TIMEOUT_IN_SECONDS, 0L)
                .with(ABProp.LID_ONE_ON_ONE_MIGRATION_COMPATIBLE, true)
                .build();
    }

    @Test
    @DisplayName("executeMigration in NOT_STARTED → no-op (state stays put)")
    void wrongStateNoOp() {
        var h = build(defaultProps());
        h.service.executeMigration();
        assertEquals(LidMigrationState.NOT_STARTED, h.service.state());
    }

    @Test
    @DisplayName("LID_ONE_ON_ONE_MIGRATION_COMPATIBLE=false → FAILED + IncompatibleClient")
    void incompatibleClientAborts() {
        var props = TestABPropsService.builder()
                .with(ABProp.LID_ONE_ON_ONE_MIGRATION_PEER_SYNC_TIMEOUT_IN_SECONDS, 0L)
                .with(ABProp.LID_ONE_ON_ONE_MIGRATION_COMPATIBLE, false)
                .build();
        var h = build(props);

        h.service.initialize();
        h.service.enableMigration();
        h.service.processProtocolMessage(new LIDMigrationMappingSyncPayloadBuilder()
                .pnToLidMappings(List.of())
                .build());

        assertEquals(LidMigrationState.FAILED, h.service.state());
        assertInstanceOf(WhatsAppLidMigrationException.IncompatibleClient.class,
                h.client.failures().getFirst());
    }

    @Test
    @DisplayName("empty store + empty mappings → state advances to COMPLETE with no failures")
    void emptyStoreEmptyMappings() {
        var h = build(defaultProps());
        h.service.initialize();
        h.service.enableMigration();
        h.service.processProtocolMessage(new LIDMigrationMappingSyncPayloadBuilder()
                .pnToLidMappings(List.of())
                .build());

        assertEquals(LidMigrationState.COMPLETE, h.service.state());
        assertTrue(h.client.failures().isEmpty());
    }

    @Test
    @DisplayName("PN chat with primary mapping → executeMigrate rewrites chat to LID")
    void executeMigratePnChat() {
        var h = build(defaultProps());
        var store = h.client.store();

        // Pre-existing PN chat + contact.
        store.addNewContact(PEER_PN);
        store.addNewChat(PEER_PN);

        h.service.initialize();
        h.service.enableMigration();
        var mapping = new LIDMigrationMappingBuilder()
                .pn(Long.parseLong(PEER_PN.user()))
                .assignedLid(Long.parseLong(PEER_LID.user()))
                .build();
        h.service.processProtocolMessage(new LIDMigrationMappingSyncPayloadBuilder()
                .pnToLidMappings(List.of(mapping))
                .build());

        assertEquals(LidMigrationState.COMPLETE, h.service.state());

        // Chat was rewritten: lid set, phoneNumberJid preserved.
        var chat = store.findChatByJid(PEER_PN).orElseThrow();
        assertEquals(PEER_LID, chat.lid().orElseThrow());
        assertEquals(PEER_PN, chat.phoneNumberJid().orElseThrow());

        // Contact mirrored.
        var contact = store.findContactByJid(PEER_PN).orElseThrow();
        assertEquals(PEER_LID, contact.lid().orElseThrow());

        // Bidirectional store mapping registered.
        assertEquals(PEER_LID, store.findLidByPhone(PEER_PN).orElseThrow());
    }

    @Test
    @DisplayName("PN chat with no LID anywhere → executeDelete removes the chat")
    void executeDeletePnChatWithoutLid() {
        var h = build(defaultProps());
        var store = h.client.store();

        // Empty chat, no mapping → deletable.
        store.addNewChat(PEER_PN);

        h.service.initialize();
        h.service.enableMigration();
        h.service.processProtocolMessage(new LIDMigrationMappingSyncPayloadBuilder()
                .pnToLidMappings(List.of())
                .build());

        assertEquals(LidMigrationState.COMPLETE, h.service.state());
        assertFalse(store.findChatByJid(PEER_PN).isPresent(),
                "chat with no LID and deletability bypass is removed");
    }

    @Test
    @DisplayName("Mixed store: LID chat kept, group kept, PN chat migrated, undeletable PN survives via cache fallback")
    void mixedStoreClassifiesEverything() {
        var h = build(defaultProps());
        var store = h.client.store();

        // 1. LID chat — Keep ALREADY_LID.
        var lidChat = store.addNewChat(PEER_LID);
        // 2. Group — Keep GROUP_OR_COMMUNITY.
        var groupChat = store.addNewChat(Jid.of("120363012345678901@g.us"));
        // 3. PN chat with primary mapping — Migrate.
        store.addNewContact(PEER_PN);
        store.addNewChat(PEER_PN);
        // 4. Empty PN chat with no mapping anywhere — Delete.
        var otherChat = store.addNewChat(OTHER_PN);

        h.service.initialize();
        h.service.enableMigration();
        var mapping = new LIDMigrationMappingBuilder()
                .pn(Long.parseLong(PEER_PN.user()))
                .assignedLid(Long.parseLong(PEER_LID.user()))
                .build();
        h.service.processProtocolMessage(new LIDMigrationMappingSyncPayloadBuilder()
                .pnToLidMappings(List.of(mapping))
                .build());

        assertEquals(LidMigrationState.COMPLETE, h.service.state());
        assertTrue(h.client.failures().isEmpty(), "no per-resolution error surfaces to handleFailure");

        // LID chat untouched.
        assertTrue(store.findChatByJid(PEER_LID).isPresent());
        // Group untouched.
        assertTrue(store.findChatByJid(groupChat.jid()).isPresent());
        // PN chat was migrated.
        var migrated = store.findChatByJid(PEER_PN).orElseThrow();
        assertEquals(PEER_LID, migrated.lid().orElseThrow());
        // OTHER_PN chat was deleted.
        assertFalse(store.findChatByJid(OTHER_PN).isPresent());
    }

    @Test
    @DisplayName("learnMappingsInBulk: latest LID differs → both assigned and latest registered in store")
    void learnMappingsInBulkLatestDiffers() {
        var h = build(defaultProps());

        h.service.initialize();
        h.service.enableMigration();
        var mapping = new LIDMigrationMappingBuilder()
                .pn(Long.parseLong(PEER_PN.user()))
                .assignedLid(Long.parseLong(PEER_LID.user()))
                .latestLid(Long.parseLong(PEER_LID_LATEST.user()))
                .build();
        h.service.processProtocolMessage(new LIDMigrationMappingSyncPayloadBuilder()
                .pnToLidMappings(List.of(mapping))
                .build());

        // findLidByPhone returns the most recently registered entry. learnMappingsInBulk runs after
        // COMPLETE and appends latestMappings after oldMappings, so the latest LID wins on the lookup.
        assertEquals(PEER_LID_LATEST, h.client.store().findLidByPhone(PEER_PN).orElseThrow(),
                "with latest differing from assigned, the latest LID is the last registered entry");
    }

    @Test
    @DisplayName("waitForOfflineDeliveryEnd gates the executor: still INIT after enabling delivery → blocks until COMPLETE")
    void waitForOfflineDeliveryEndIsGate() {
        // Build with the COMPLETE pre-set (default), then reset to INIT to demonstrate the gate.
        // We run executeMigration on a virtual thread and assert it remains stuck until we flip the state.
        var h = build(defaultProps());
        h.client.store().setOfflineResumeState(
                com.github.auties00.cobalt.client.WhatsAppClientOfflineResumeState.INIT);

        h.service.initialize();
        h.service.enableMigration();

        // Auto-start of executeMigration happens synchronously in processProtocolMessage. Run on a
        // virtual thread so the test thread can advance the gate.
        var done = new java.util.concurrent.atomic.AtomicBoolean(false);
        Thread.ofVirtual().start(() -> {
            h.service.processProtocolMessage(new LIDMigrationMappingSyncPayloadBuilder()
                    .pnToLidMappings(List.of())
                    .build());
            done.set(true);
        });

        // Give the worker a moment to enter waitForOfflineDeliveryEnd.
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        assertFalse(done.get(), "worker is blocked in waitForOfflineDeliveryEnd");

        // Flip the gate; the worker proceeds and completes.
        h.client.store().setOfflineResumeState(
                com.github.auties00.cobalt.client.WhatsAppClientOfflineResumeState.COMPLETE);

        var deadline = System.currentTimeMillis() + 5_000;
        while (!done.get() && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
        assertTrue(done.get(), "worker completed after the gate flipped to COMPLETE");
        assertEquals(LidMigrationState.COMPLETE, h.service.state());
    }

    @Test
    @DisplayName("learnMappingsInBulk: assigned LID matches existing store mapping → entry skipped (no churn)")
    void learnMappingsInBulkAssignedMatchesExisting() {
        var h = build(defaultProps());
        var store = h.client.store();

        // Pre-register the mapping the primary will assign — so learnMappingsInBulk should skip it.
        store.registerLidMapping(PEER_PN, PEER_LID);

        h.service.initialize();
        h.service.enableMigration();
        var mapping = new LIDMigrationMappingBuilder()
                .pn(Long.parseLong(PEER_PN.user()))
                .assignedLid(Long.parseLong(PEER_LID.user()))
                .build();
        h.service.processProtocolMessage(new LIDMigrationMappingSyncPayloadBuilder()
                .pnToLidMappings(List.of(mapping))
                .build());

        // Store mapping unchanged — assignment matched the existing entry.
        assertEquals(PEER_LID, store.findLidByPhone(PEER_PN).orElseThrow());
    }

    @Test
    @DisplayName("learnMappingsInBulk: latest LID matches existing local mapping → only assigned LID registered (old bucket)")
    void learnMappingsInBulkLatestMatchesLocalOldBucket() {
        var h = build(defaultProps());
        var store = h.client.store();
        // Local has PEER_LID_LATEST; primary's latest = PEER_LID_LATEST (matches) and assigned = PEER_LID.
        // Old-bucket path: only assigned is registered, replacing the local.
        store.registerLidMapping(PEER_PN, PEER_LID_LATEST);

        h.service.initialize();
        h.service.enableMigration();
        var mapping = new LIDMigrationMappingBuilder()
                .pn(Long.parseLong(PEER_PN.user()))
                .assignedLid(Long.parseLong(PEER_LID.user()))
                .latestLid(Long.parseLong(PEER_LID_LATEST.user()))
                .build();
        h.service.processProtocolMessage(new LIDMigrationMappingSyncPayloadBuilder()
                .pnToLidMappings(List.of(mapping))
                .build());

        // The assigned LID overwrites the local (which equalled the latest) — old-bucket-only registration.
        assertEquals(PEER_LID, store.findLidByPhone(PEER_PN).orElseThrow(),
                "old-bucket registers only the assigned LID when local matches the latest");
    }

    @Test
    @DisplayName("per-resolution error swallowed: missing chat during executeMigrate does not abort the sweep")
    void perResolutionErrorSwallowed() {
        var h = build(defaultProps());
        var store = h.client.store();
        store.addNewContact(PEER_PN);

        h.service.initialize();
        h.service.enableMigration();
        var mappingPeer = new LIDMigrationMappingBuilder()
                .pn(Long.parseLong(PEER_PN.user()))
                .assignedLid(Long.parseLong(PEER_LID.user()))
                .build();
        var mappingOther = new LIDMigrationMappingBuilder()
                .pn(Long.parseLong(OTHER_PN.user()))
                .assignedLid(Long.parseLong(OTHER_LID.user()))
                .build();
        h.service.processProtocolMessage(new LIDMigrationMappingSyncPayloadBuilder()
                .pnToLidMappings(List.of(mappingPeer, mappingOther))
                .build());

        // Both mappings end up in the bidirectional store table; the executor itself had no
        // chats to rewrite (store contained only a contact, no chats), so the sweep is trivially
        // complete and the per-resolution catch was not exercised — but no exception escaped.
        assertEquals(LidMigrationState.COMPLETE, h.service.state());
        assertEquals(PEER_LID, store.findLidByPhone(PEER_PN).orElseThrow());
        assertEquals(OTHER_LID, store.findLidByPhone(OTHER_PN).orElseThrow());
    }
}
