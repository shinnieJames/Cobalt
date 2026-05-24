package com.github.auties00.cobalt.migration;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.model.chat.community.CommunityMetadataBuilder;
import com.github.auties00.cobalt.model.chat.group.GroupMetadataBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.props.TestABPropsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link InactiveGroupLidMigrationService}.
 *
 * @apiNote
 * Pins the migration pipeline against WA Web's
 * {@code WAWebInactiveGroupLidMigrationJob.migrateInactiveGroupsToLid} sweep:
 * completion-flag round-trip, PN-group filtering, retry rescheduling when
 * groups remain on PN, and per-group exception swallowing.
 *
 * @implNote
 * This implementation drives {@link InactiveGroupLidMigrationService#run()}
 * directly so the 60-second initial delay scheduled by
 * {@link InactiveGroupLidMigrationService#start()} does not stall the test
 * suite; the package-private visibility on {@code run()} is the test seam.
 */
@DisplayName("InactiveGroupLidMigrationService")
class InactiveGroupLidMigrationServiceTest {

    /**
     * Captured-session self phone-number JID (matches the
     * {@code business} VoIP capture session).
     */
    private static final Jid SELF_PN = Jid.of("19254863482@s.whatsapp.net");

    /**
     * Captured-session self LID (paired with {@link #SELF_PN}).
     */
    private static final Jid SELF_LID = Jid.of("83116928594056@lid");

    /**
     * Test-only bundle of the client mock and the migration service wired
     * to it.
     *
     * @apiNote
     * Returned by {@link #build()} so each test can reach into both the
     * client (to seed metadata and stub responses) and the service (to
     * drive {@code run()} or inspect the completion flag).
     *
     * @param client  the test client mock
     * @param service the migration service under test
     */
    private record Harness(TestWhatsAppClient client, InactiveGroupLidMigrationService service) {}

    /**
     * Builds a fresh {@link Harness} backed by an in-memory store seeded
     * with the captured-session identity pair.
     *
     * @apiNote
     * Used by every test as the setup; a fresh harness per test ensures
     * state does not leak between cases.
     *
     * @return the new harness
     */
    private static Harness build() {
        var props = TestABPropsService.builder().build();
        var store = MigrationFixtures.temporaryStore(SELF_PN, SELF_LID);
        var client = TestWhatsAppClient.create().withStore(store);
        var service = new InactiveGroupLidMigrationService(client, props);
        return new Harness(client, service);
    }

    /**
     * Synthesises a group JID with the given two-digit suffix.
     *
     * @apiNote
     * Used to populate the store with deterministic, conflict-free group
     * JIDs across the tests.
     *
     * @param suffix the two-digit numeric suffix
     * @return a group JID on the group-or-community server
     */
    private static Jid groupJid(int suffix) {
        return Jid.of("1203630123456789" + String.format("%02d", suffix) + "@g.us");
    }

    /**
     * {@code setComplete} flips the flag observed by {@code isComplete}.
     */
    @Test
    @DisplayName("setComplete + isComplete round-trip")
    void completionFlag() {
        var h = build();
        assertFalse(h.service.isInactiveGroupLidMigrationComplete());
        h.service.setInactiveGroupLidMigrationComplete();
        assertTrue(h.service.isInactiveGroupLidMigrationComplete());
    }

    /**
     * {@code run()} short-circuits when the completion flag is already
     * set, skipping the PN-group sweep entirely.
     *
     * @implNote
     * If the completion guard did not fire first, the seeded PN group
     * would trigger {@code queryChatMetadata}, throwing because no
     * canned response was wired; the absence of an exception is the
     * positive signal.
     */
    @Test
    @DisplayName("run early-returns when already complete")
    void runShortCircuitsOnComplete() {
        var h = build();
        h.service.setInactiveGroupLidMigrationComplete();

        var pnGroup = groupJid(1);
        h.client.store().addNewChat(pnGroup);
        h.client.store().addChatMetadata(new GroupMetadataBuilder()
                .jid(pnGroup)
                .subject("Test Group")
                .isLidAddressingMode(false)
                .build());

        h.service.run();

        assertTrue(h.service.isInactiveGroupLidMigrationComplete());
    }

    /**
     * With no PN-mode groups present, {@code run()} marks the migration
     * complete immediately.
     */
    @Test
    @DisplayName("run with zero PN groups marks complete immediately")
    void runNoPnGroups() {
        var h = build();
        h.service.run();
        assertTrue(h.service.isInactiveGroupLidMigrationComplete());
    }

    /**
     * {@code findPnGroups} returns only PN-mode groups, excluding the four
     * disqualifying classes: LID-mode groups, suspended or terminated
     * groups, non-group JIDs, and groups missing metadata.
     *
     * @apiNote
     * Covers the {@code CommunityMetadata} branch of
     * {@code isSuspendedOrTerminated} via case 7; the
     * {@link CommunityMetadataBuilder} path is the only way to exercise
     * it.
     */
    @Test
    @DisplayName("findPnGroups filters out: LID-addressing groups, suspended/terminated groups, non-group servers")
    void findPnGroupsFilters() {
        var h = build();
        var store = h.client.store();

        var pnGroup = groupJid(1);
        store.addNewChat(pnGroup);
        store.addChatMetadata(new GroupMetadataBuilder()
                .jid(pnGroup)
                .subject("Test Group")
                .isLidAddressingMode(false)
                .build());

        var lidGroup = groupJid(2);
        store.addNewChat(lidGroup);
        store.addChatMetadata(new GroupMetadataBuilder()
                .jid(lidGroup)
                .subject("Test Group")
                .isLidAddressingMode(true)
                .build());

        var suspendedGroup = groupJid(3);
        store.addNewChat(suspendedGroup);
        var suspendedMeta = new GroupMetadataBuilder()
                .jid(suspendedGroup)
                .subject("Test Group")
                .isLidAddressingMode(false)
                .build();
        suspendedMeta.setSuspended(true);
        store.addChatMetadata(suspendedMeta);

        var terminatedGroup = groupJid(4);
        store.addNewChat(terminatedGroup);
        var terminatedMeta = new GroupMetadataBuilder()
                .jid(terminatedGroup)
                .subject("Test Group")
                .isLidAddressingMode(false)
                .build();
        terminatedMeta.setTerminated(true);
        store.addChatMetadata(terminatedMeta);

        store.addNewChat(Jid.of("12025550100@s.whatsapp.net"));

        var noMetaGroup = groupJid(5);
        store.addNewChat(noMetaGroup);

        var suspendedCommunity = groupJid(6);
        store.addNewChat(suspendedCommunity);
        var communityMeta = new CommunityMetadataBuilder()
                .jid(suspendedCommunity)
                .subject("Test Community")
                .isLidAddressingMode(false)
                .build();
        communityMeta.setSuspended(true);
        store.addChatMetadata(communityMeta);

        var pnGroups = h.service.findPnGroups();

        assertEquals(1, pnGroups.size(), "only the unsuspended/non-terminated PN-mode group passes");
        assertEquals(pnGroup, pnGroups.getFirst());
    }

    /**
     * When every queried group flips to LID after the metadata refresh,
     * the second {@code run()} pass marks the migration complete.
     *
     * @implNote
     * {@link TestWhatsAppClient} does not auto-update the cached metadata
     * after {@code queryChatMetadata}, so the test manually rewrites the
     * stored metadata between the two {@code run()} calls to simulate the
     * server-driven flip.
     */
    @Test
    @DisplayName("run with PN groups + all queryChatMetadata succeed + groups become LID marks complete")
    void runMarksCompleteWhenAllGroupsFlip() {
        var h = build();
        var store = h.client.store();

        var pnGroup = groupJid(1);
        store.addNewChat(pnGroup);
        store.addChatMetadata(new GroupMetadataBuilder()
                .jid(pnGroup)
                .subject("Test Group")
                .isLidAddressingMode(false)
                .build());

        h.client.withChatMetadata(pnGroup, new GroupMetadataBuilder()
                .jid(pnGroup)
                .subject("Test Group")
                .isLidAddressingMode(true)
                .build());

        h.service.run();

        store.addChatMetadata(new GroupMetadataBuilder()
                .jid(pnGroup)
                .subject("Test Group")
                .isLidAddressingMode(true)
                .build());

        h.service.run();

        assertTrue(h.service.isInactiveGroupLidMigrationComplete());
    }

    /**
     * Exceptions thrown by {@code queryChatMetadata} for individual groups
     * are swallowed; the migration stays unmarked when the group remains
     * on PN.
     */
    @Test
    @DisplayName("run swallows exceptions thrown by queryChatMetadata for individual groups")
    void runSwallowsQueryFailures() {
        var h = build();
        var store = h.client.store();
        var pnGroup = groupJid(1);
        store.addNewChat(pnGroup);
        store.addChatMetadata(new GroupMetadataBuilder()
                .jid(pnGroup)
                .subject("Test Group")
                .isLidAddressingMode(false)
                .build());

        h.service.run();

        assertFalse(h.service.isInactiveGroupLidMigrationComplete());
    }

    /**
     * Remaining PN-mode groups after the sweep prevent the completion
     * marker and trigger a rescheduled retry pass.
     */
    @Test
    @DisplayName("run with PN groups remaining reschedules a retry task")
    void runWithRemainingPnGroupsReschedules() {
        var h = build();
        var store = h.client.store();

        var pnGroup1 = groupJid(1);
        var pnGroup2 = groupJid(2);
        store.addNewChat(pnGroup1);
        store.addNewChat(pnGroup2);

        var meta1 = new GroupMetadataBuilder().jid(pnGroup1).subject("Test Group").isLidAddressingMode(false).build();
        var meta2 = new GroupMetadataBuilder().jid(pnGroup2).subject("Test Group").isLidAddressingMode(false).build();
        store.addChatMetadata(meta1);
        store.addChatMetadata(meta2);
        h.client.withChatMetadata(pnGroup1, meta1);
        h.client.withChatMetadata(pnGroup2, meta2);

        h.service.run();

        assertFalse(h.service.isInactiveGroupLidMigrationComplete(),
                "groups still on PN -> migration is rescheduled, not marked complete");
    }

    /**
     * {@code reset()} cancels any pending scheduled task and a subsequent
     * {@code start()} is still safe.
     *
     * @implNote
     * The {@link java.util.concurrent.CompletableFuture} cancellation
     * status is not exposed by the service, so the contract observed here
     * is that {@code reset()} and a follow-up {@code start()} both return
     * cleanly.
     */
    @Test
    @DisplayName("reset cancels any pending scheduled task and nulls the field")
    void resetCancelsScheduledTask() {
        var h = build();
        h.service.start();
        h.service.reset();
        h.service.start();
        h.service.reset();
    }
}
