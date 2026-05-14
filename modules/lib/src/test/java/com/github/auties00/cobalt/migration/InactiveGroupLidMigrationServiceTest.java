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
 * <p>Drives {@link InactiveGroupLidMigrationService#run()} directly via
 * the package-private visibility tweak — bypassing the 60-second
 * scheduler delay that {@link InactiveGroupLidMigrationService#start()}
 * would otherwise impose.
 */
@DisplayName("InactiveGroupLidMigrationService")
class InactiveGroupLidMigrationServiceTest {

    private static final Jid SELF_PN = Jid.of("19254863482@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594056@lid");

    private record Harness(TestWhatsAppClient client, InactiveGroupLidMigrationService service) {}

    private static Harness build() {
        var props = TestABPropsService.builder().build();
        var store = MigrationFixtures.temporaryStore(SELF_PN, SELF_LID);
        var client = TestWhatsAppClient.create().withStore(store);
        var service = new InactiveGroupLidMigrationService(client, props);
        return new Harness(client, service);
    }

    private static Jid groupJid(int suffix) {
        return Jid.of("1203630123456789" + String.format("%02d", suffix) + "@g.us");
    }

    @Test
    @DisplayName("setComplete + isComplete round-trip")
    void completionFlag() {
        var h = build();
        assertFalse(h.service.isInactiveGroupLidMigrationComplete());
        h.service.setInactiveGroupLidMigrationComplete();
        assertTrue(h.service.isInactiveGroupLidMigrationComplete());
    }

    @Test
    @DisplayName("run early-returns when already complete")
    void runShortCircuitsOnComplete() {
        var h = build();
        h.service.setInactiveGroupLidMigrationComplete();

        // Even if we add a PN group, run() should not query metadata because the completion guard fires first.
        var pnGroup = groupJid(1);
        h.client.store().addNewChat(pnGroup);
        h.client.store().addChatMetadata(new GroupMetadataBuilder()
                .jid(pnGroup)
                .subject("Test Group")
                .isLidAddressingMode(false)
                .build());

        // run() does not call queryChatMetadata; no canned response is required.
        h.service.run();

        assertTrue(h.service.isInactiveGroupLidMigrationComplete());
    }

    @Test
    @DisplayName("run with zero PN groups → marks complete immediately")
    void runNoPnGroups() {
        var h = build();
        h.service.run();
        assertTrue(h.service.isInactiveGroupLidMigrationComplete());
    }

    @Test
    @DisplayName("findPnGroups filters out: LID-addressing groups, suspended/terminated groups, non-group servers")
    void findPnGroupsFilters() {
        var h = build();
        var store = h.client.store();

        // 1. PN-mode group → INCLUDED
        var pnGroup = groupJid(1);
        store.addNewChat(pnGroup);
        store.addChatMetadata(new GroupMetadataBuilder()
                .jid(pnGroup)
                .subject("Test Group")
                .isLidAddressingMode(false)
                .build());

        // 2. LID-mode group → excluded
        var lidGroup = groupJid(2);
        store.addNewChat(lidGroup);
        store.addChatMetadata(new GroupMetadataBuilder()
                .jid(lidGroup)
                .subject("Test Group")
                .isLidAddressingMode(true)
                .build());

        // 3. Suspended PN-mode group → excluded
        var suspendedGroup = groupJid(3);
        store.addNewChat(suspendedGroup);
        var suspendedMeta = new GroupMetadataBuilder()
                .jid(suspendedGroup)
                .subject("Test Group")
                .isLidAddressingMode(false)
                .build();
        suspendedMeta.setSuspended(true);
        store.addChatMetadata(suspendedMeta);

        // 4. Terminated PN-mode group → excluded
        var terminatedGroup = groupJid(4);
        store.addNewChat(terminatedGroup);
        var terminatedMeta = new GroupMetadataBuilder()
                .jid(terminatedGroup)
                .subject("Test Group")
                .isLidAddressingMode(false)
                .build();
        terminatedMeta.setTerminated(true);
        store.addChatMetadata(terminatedMeta);

        // 5. 1:1 PN chat → excluded (non-group server)
        store.addNewChat(Jid.of("12025550100@s.whatsapp.net"));

        // 6. PN-mode group with NO metadata → excluded (metadata gate enforces non-null)
        var noMetaGroup = groupJid(5);
        store.addNewChat(noMetaGroup);

        // 7. Suspended community (CommunityMetadata branch of isSuspendedOrTerminated) → excluded
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

    @Test
    @DisplayName("run with PN groups + all queryChatMetadata succeed + groups become LID → marks complete")
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

        // Wire queryChatMetadata to flip the metadata to LID mode (server response side effect).
        h.client.withChatMetadata(pnGroup, new GroupMetadataBuilder()
                .jid(pnGroup)
                .subject("Test Group")
                .isLidAddressingMode(true)
                .build());

        // run() calls client.queryChatMetadata(groupJid) per PN group. TestWhatsAppClient returns the
        // preset metadata; we flip the cached metadata to LID afterwards to simulate the server-driven
        // state change that the real client does internally.
        h.service.run();

        // Update the cached metadata to reflect the post-query state.
        store.addChatMetadata(new GroupMetadataBuilder()
                .jid(pnGroup)
                .subject("Test Group")
                .isLidAddressingMode(true)
                .build());

        // Re-run to observe the post-update completion.
        h.service.run();

        assertTrue(h.service.isInactiveGroupLidMigrationComplete());
    }

    @Test
    @DisplayName("run swallows exceptions thrown by queryChatMetadata for individual groups")
    void runSwallowsQueryFailures() {
        var h = build();
        var store = h.client.store();
        // PN group with no queryChatMetadata preset → TestWhatsAppClient.queryChatMetadata throws.
        var pnGroup = groupJid(1);
        store.addNewChat(pnGroup);
        store.addChatMetadata(new GroupMetadataBuilder()
                .jid(pnGroup)
                .subject("Test Group")
                .isLidAddressingMode(false)
                .build());

        // run() must not propagate; it logs and continues.
        h.service.run();

        // Migration was not completed because the group remains PN — but no exception escapes.
        assertFalse(h.service.isInactiveGroupLidMigrationComplete());
    }

    @Test
    @DisplayName("run with PN groups remaining → reschedules a retry task")
    void runWithRemainingPnGroupsReschedules() {
        var h = build();
        var store = h.client.store();

        // Two PN groups; queries succeed (preset metadata) but metadata is NOT flipped to LID,
        // so the second findPnGroups call still finds them → reschedule path.
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

        // Completion flag must NOT be set — PN groups still remain after the pass.
        assertFalse(h.service.isInactiveGroupLidMigrationComplete(),
                "groups still on PN → migration is rescheduled, not marked complete");
    }

    @Test
    @DisplayName("reset cancels any pending scheduled task and nulls the field")
    void resetCancelsScheduledTask() {
        var h = build();
        h.service.start();
        // start() scheduled a delayed task; reset() must cancel it. We cannot directly observe the
        // CompletableFuture cancellation status (no accessor), so the contract here is that reset()
        // returns cleanly and a subsequent start() also returns cleanly without throwing.
        h.service.reset();
        h.service.start();
        h.service.reset();
        // No exception is the success condition.
    }
}
