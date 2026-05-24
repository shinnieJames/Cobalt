package com.github.auties00.cobalt.migration;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.model.chat.group.GroupMetadataBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.migration.LIDMigrationMappingSyncPayloadBuilder;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link LidMigrationService} JID conversion helpers.
 *
 * @apiNote
 * Covers the cross-addressing-mode utilities ({@code toPn},
 * {@code toLid}, {@code toUserLid}, {@code toUserLidOrThrow},
 * {@code toPnOrThrow}, {@code lookupLid},
 * {@code toAddressingModeFactory}, {@code toCommonAddressingMode},
 * {@code getPnAndLidToUpdate}, {@code chatIsLid},
 * {@code shouldUseLidAddressing}, {@code shouldHaveAccountLid}, and
 * {@code isRegularUser}); pins both the cache-first ordering and
 * the me-fast-path behaviour that WA Web's
 * {@code WAWebApiContact.getAlternateUserWid} relies on.
 *
 * @implNote
 * This implementation builds an isolated store per test through
 * {@link MigrationFixtures#temporaryStore(Jid, Jid)} so registered
 * mappings cannot bleed across cases.
 */
@DisplayName("LidMigrationService JID conversion helpers")
class LidMigrationServiceJidConversionTest {

    private static final Jid SELF_PN = Jid.of("19254863482@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594056@lid");
    private static final Jid PEER_PN = Jid.of("393495089819@s.whatsapp.net");
    private static final Jid PEER_LID = Jid.of("258252122116273@lid");
    private static final Jid PEER_DEVICE = Jid.of("393495089819:5@s.whatsapp.net");
    private static final Jid GROUP = Jid.of("120363012345678901@g.us");
    private static final Jid NEWSLETTER = Jid.of("120363023456789012@newsletter");
    private static final Jid BROADCAST = Jid.of("19254863482@broadcast");
    private static final Jid BOT = Jid.of("867051314767696@bot");
    private static final Jid HOSTED = Jid.of("18005550199@hosted");

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
     * Verifies that toPn returns null for null input.
     */
    @Test
    @DisplayName("toPn returns null for null input")
    void toPnNull() {
        assertNull(build().service.toPn(null));
    }

    /**
     * Verifies that toPn returns the input unchanged when it is already a phone-number JID.
     */
    @Test
    @DisplayName("toPn returns the input unchanged when it is already a phone-number JID")
    void toPnPassthroughPhoneNumber() {
        var h = build();
        assertEquals(PEER_PN, h.service.toPn(PEER_PN));
    }

    /**
     * Verifies that toPn returns null for a LID with no mapping.
     */
    @Test
    @DisplayName("toPn returns null for a LID with no mapping")
    void toPnLidUnmapped() {
        var h = build();
        assertNull(h.service.toPn(PEER_LID));
    }

    /**
     * Verifies that toPn looks up the LID through the store.
     */
    @Test
    @DisplayName("toPn looks up the LID through the store")
    void toPnLidMapped() {
        var h = build();
        h.client.store().registerLidMapping(PEER_PN, PEER_LID);
        assertEquals(PEER_PN, h.service.toPn(PEER_LID));
    }

    /**
     * Verifies that toLid returns null for null input.
     */
    @Test
    @DisplayName("toLid returns null for null input")
    void toLidNull() {
        assertNull(build().service.toLid(null));
    }

    /**
     * Verifies that toLid returns the input unchanged when it is already a LID.
     */
    @Test
    @DisplayName("toLid returns the input unchanged when it is already a LID")
    void toLidPassthroughLid() {
        var h = build();
        assertEquals(PEER_LID, h.service.toLid(PEER_LID));
    }

    /**
     * Verifies that toLid returns null for a phone-number JID with no mapping.
     */
    @Test
    @DisplayName("toLid returns null for a phone-number JID with no mapping")
    void toLidPhoneUnmapped() {
        var h = build();
        assertNull(h.service.toLid(PEER_PN));
    }

    /**
     * Verifies that toLid strips device suffix before looking up.
     */
    @Test
    @DisplayName("toLid strips device suffix before looking up")
    void toLidStripsDevice() {
        var h = build();
        h.client.store().registerLidMapping(PEER_PN, PEER_LID);
        assertEquals(PEER_LID, h.service.toLid(PEER_DEVICE));
    }

    /**
     * Verifies that toUserLid returns null for null input.
     */
    @Test
    @DisplayName("toUserLid returns null for null input")
    void toUserLidNull() {
        assertNull(build().service.toUserLid(null));
    }

    /**
     * Verifies that toUserLid strips device suffix before checking.
     */
    @Test
    @DisplayName("toUserLid strips device suffix before checking")
    void toUserLidStripsDevice() {
        var h = build();
        var lidWithDevice = Jid.of("258252122116273:3@lid");
        // Already-LID branch: strip device, return user-level LID.
        assertEquals(PEER_LID, h.service.toUserLid(lidWithDevice));
    }

    /**
     * Verifies that toUserLidOrThrow throws when no mapping exists.
     */
    @Test
    @DisplayName("toUserLidOrThrow throws when no mapping exists")
    void toUserLidOrThrow() {
        var h = build();
        assertThrows(IllegalStateException.class, () -> h.service.toUserLidOrThrow(PEER_PN));
        h.client.store().registerLidMapping(PEER_PN, PEER_LID);
        assertEquals(PEER_LID, h.service.toUserLidOrThrow(PEER_PN));
    }

    /**
     * Verifies that toPnOrThrow throws when no mapping exists.
     */
    @Test
    @DisplayName("toPnOrThrow throws when no mapping exists")
    void toPnOrThrow() {
        var h = build();
        assertThrows(IllegalStateException.class, () -> h.service.toPnOrThrow(PEER_LID));
        h.client.store().registerLidMapping(PEER_PN, PEER_LID);
        assertEquals(PEER_PN, h.service.toPnOrThrow(PEER_LID));
    }

    /**
     * Verifies that lookupLid returns empty for null.
     */
    @Test
    @DisplayName("lookupLid returns empty for null")
    void lookupLidNull() {
        var h = build();
        assertFalse(h.service.lookupLid(null).isPresent());
    }

    /**
     * Verifies that lookupLid falls back to store when primary cache is empty.
     */
    @Test
    @DisplayName("lookupLid falls back to store when primary cache is empty")
    void lookupLidStoreFallback() {
        var h = build();
        h.client.store().registerLidMapping(PEER_PN, PEER_LID);
        assertEquals(PEER_LID, h.service.lookupLid(PEER_PN).orElseThrow());
    }

    /**
     * Verifies that lookupLid: primary cache hit short-circuits store lookup.
     */
    @Test
    @DisplayName("lookupLid: primary cache hit short-circuits store lookup")
    void lookupLidPrimaryCacheShortCircuits() {
        var h = build();
        // Populate the PRIMARY cache via changeLid (which also writes the store, so we
        // overwrite the store's entry afterwards to prove the cache wins).
        h.service.changeLid(PEER_PN, PEER_LID, null);
        var differentLid = Jid.of("12345678901234@lid");
        h.client.store().registerLidMapping(PEER_PN, differentLid);

        assertEquals(PEER_LID, h.service.lookupLid(PEER_PN).orElseThrow(),
                "primary cache (PEER_LID) wins over store (differentLid)");
    }

    /**
     * Verifies that getAlternateUserWid (via toCommonAddressingMode): me LID -> me PN via store.jid()/store.lid() fast path.
     */
    @Test
    @DisplayName("getAlternateUserWid (via toCommonAddressingMode): me LID -> me PN via store.jid()/store.lid() fast path")
    void getAlternateUserWidMeFastPathLidToPn() {
        var h = build();
        // No registered mapping for self; only store.jid() and store.lid() are set.
        // Pair (selfLid, peerPn) is "mixed addressing" so toCommonAddressingMode converts.
        var result = h.service.toCommonAddressingMode(SELF_LID, PEER_PN);
        // selfLid's alternate (via me fast path) is the self PN at user level.
        assertEquals(SELF_PN.toUserJid(), result[0],
                "me LID -> me PN through the fast path that consults store.jid()");
        assertEquals(PEER_PN, result[1]);
    }

    /**
     * Verifies that getAlternateUserWid (via toCommonAddressingMode): me PN -> me LID via store.lid() fast path.
     */
    @Test
    @DisplayName("getAlternateUserWid (via toCommonAddressingMode): me PN -> me LID via store.lid() fast path")
    void getAlternateUserWidMeFastPathPnToLid() {
        var h = build();
        var result = h.service.toCommonAddressingMode(SELF_PN, PEER_LID);
        assertEquals(SELF_LID.toUserJid(), result[0],
                "me PN -> me LID through the fast path that consults store.lid()");
        assertEquals(PEER_LID, result[1]);
    }

    /**
     * Verifies that shouldUseLidAddressing: state=COMPLETE + 1:1 PN recipient with mapping -> true.
     */
    @Test
    @DisplayName("shouldUseLidAddressing: state=COMPLETE + 1:1 PN recipient with mapping -> true")
    void shouldUseLidAddressingPositiveCase() {
        var props = TestABPropsService.builder()
                .with(ABProp.LID_ONE_ON_ONE_MIGRATION_PEER_SYNC_TIMEOUT_IN_SECONDS, 0L)
                .with(ABProp.LID_ONE_ON_ONE_MIGRATION_COMPATIBLE, true)
                .build();
        var store = MigrationFixtures.temporaryStore(SELF_PN, SELF_LID);
        var client = TestWhatsAppClient.create().withStore(store);
        var wamService = new DefaultWamService(client, props);
        var service = new LidMigrationService(client, props, wamService);

        // Register a mapping so lookupLid finds something.
        store.registerLidMapping(PEER_PN, PEER_LID);

        service.initialize();
        service.enableMigration();
        service.processProtocolMessage(
                new LIDMigrationMappingSyncPayloadBuilder()
                        .pnToLidMappings(List.of())
                        .build());

        assertEquals(LidMigrationState.COMPLETE, service.state());
        assertTrue(service.shouldUseLidAddressing(PEER_PN),
                "COMPLETE + mapped 1:1 PN recipient -> LID addressing");
    }

    /**
     * Verifies that isRegularUser is true for the standard user/lid/hosted/hostedLid servers.
     */
    @Test
    @DisplayName("isRegularUser is true for the standard user/lid/hosted/hostedLid servers")
    void isRegularUserTrue() {
        assertTrue(LidMigrationService.isRegularUser(PEER_PN));
        assertTrue(LidMigrationService.isRegularUser(PEER_LID));
        assertTrue(LidMigrationService.isRegularUser(HOSTED));
    }

    /**
     * Verifies that isRegularUser is false for group/newsletter/broadcast.
     */
    @Test
    @DisplayName("isRegularUser is false for group/newsletter/broadcast")
    void isRegularUserNonUserServers() {
        assertFalse(LidMigrationService.isRegularUser(GROUP));
        assertFalse(LidMigrationService.isRegularUser(NEWSLETTER));
        assertFalse(LidMigrationService.isRegularUser(BROADCAST));
    }

    /**
     * Verifies that isRegularUser is false for bot and for the announcements account.
     */
    @Test
    @DisplayName("isRegularUser is false for bot and for the announcements account")
    void isRegularUserExcludesBotAndAnnouncements() {
        assertFalse(LidMigrationService.isRegularUser(BOT));
        assertFalse(LidMigrationService.isRegularUser(Jid.announcementsAccount()));
    }

    /**
     * Verifies that shouldHaveAccountLid is false when migration is not COMPLETE.
     */
    @Test
    @DisplayName("shouldHaveAccountLid is false when migration is not COMPLETE")
    void shouldHaveAccountLidPreMigration() {
        var h = build();
        assertFalse(h.service.shouldHaveAccountLid(PEER_PN));
    }

    /**
     * Verifies that shouldHaveAccountLid is false for null JID.
     */
    @Test
    @DisplayName("shouldHaveAccountLid is false for null JID")
    void shouldHaveAccountLidNull() {
        var h = build();
        assertFalse(h.service.shouldHaveAccountLid(null));
    }

    /**
     * Verifies that shouldUseLidAddressing is false for null.
     */
    @Test
    @DisplayName("shouldUseLidAddressing is false for null")
    void shouldUseLidAddressingNull() {
        assertFalse(build().service.shouldUseLidAddressing(null));
    }

    /**
     * Verifies that shouldUseLidAddressing is true when the recipient is already a LID.
     */
    @Test
    @DisplayName("shouldUseLidAddressing is true when the recipient is already a LID")
    void shouldUseLidAddressingAlreadyLid() {
        assertTrue(build().service.shouldUseLidAddressing(PEER_LID));
    }

    /**
     * Verifies that shouldUseLidAddressing is false for group, newsletter, and broadcast servers.
     */
    @Test
    @DisplayName("shouldUseLidAddressing is false for group, newsletter, and broadcast servers")
    void shouldUseLidAddressingNonUserServers() {
        var h = build();
        assertFalse(h.service.shouldUseLidAddressing(GROUP));
        assertFalse(h.service.shouldUseLidAddressing(NEWSLETTER));
        assertFalse(h.service.shouldUseLidAddressing(BROADCAST));
    }

    /**
     * Verifies that shouldUseLidAddressing is false for a 1:1 PN recipient when migration has not advanced.
     */
    @Test
    @DisplayName("shouldUseLidAddressing is false for a 1:1 PN recipient when migration has not advanced")
    void shouldUseLidAddressingNotMigrated() {
        var h = build();
        h.client.store().registerLidMapping(PEER_PN, PEER_LID);
        assertFalse(h.service.shouldUseLidAddressing(PEER_PN),
                "state machine is NOT_STARTED; LID addressing is gated on COMPLETE/IN_PROGRESS");
    }

    /**
     * Verifies that toAddressingModeFactory(true) returns toLid as a function reference.
     */
    @Test
    @DisplayName("toAddressingModeFactory(true) returns toLid as a function reference")
    void toAddressingModeFactoryTrue() {
        var h = build();
        h.client.store().registerLidMapping(PEER_PN, PEER_LID);
        var fn = h.service.toAddressingModeFactory(true);
        assertEquals(PEER_LID, fn.apply(PEER_PN));
    }

    /**
     * Verifies that toAddressingModeFactory(false) returns toPn as a function reference.
     */
    @Test
    @DisplayName("toAddressingModeFactory(false) returns toPn as a function reference")
    void toAddressingModeFactoryFalse() {
        var h = build();
        h.client.store().registerLidMapping(PEER_PN, PEER_LID);
        var fn = h.service.toAddressingModeFactory(false);
        assertEquals(PEER_PN, fn.apply(PEER_LID));
    }

    /**
     * Verifies that toCommonAddressingMode leaves a same-server pair unchanged.
     */
    @Test
    @DisplayName("toCommonAddressingMode leaves a same-server pair unchanged")
    void toCommonAddressingModeSameServer() {
        var h = build();
        var pn2 = Jid.of("12025550100@s.whatsapp.net");
        var result = h.service.toCommonAddressingMode(PEER_PN, pn2);
        assertArrayEquals(new Jid[]{PEER_PN, pn2}, result);
    }

    /**
     * Verifies that toCommonAddressingMode converts the first side when its alternate is known.
     */
    @Test
    @DisplayName("toCommonAddressingMode converts the first side when its alternate is known")
    void toCommonAddressingModeConvertsFirst() {
        var h = build();
        h.client.store().registerLidMapping(PEER_PN, PEER_LID);
        var other = Jid.of("999999999999999@lid");
        // first=PN (mapping known), second=LID (no mapping); first is converted to LID.
        var result = h.service.toCommonAddressingMode(PEER_PN, other);
        assertEquals(PEER_LID, result[0]);
        assertEquals(other, result[1]);
    }

    /**
     * Verifies that toCommonAddressingMode converts the second side when only its alternate is known.
     */
    @Test
    @DisplayName("toCommonAddressingMode converts the second side when only its alternate is known")
    void toCommonAddressingModeConvertsSecond() {
        var h = build();
        h.client.store().registerLidMapping(PEER_PN, PEER_LID);
        var unknownPn = Jid.of("12025550100@s.whatsapp.net");
        // first=unknownPn (no LID), second=PEER_LID (PN known); second is converted to PN.
        var result = h.service.toCommonAddressingMode(unknownPn, PEER_LID);
        assertEquals(unknownPn, result[0]);
        assertEquals(PEER_PN, result[1]);
    }

    /**
     * Verifies that toCommonAddressingMode passes through nulls.
     */
    @Test
    @DisplayName("toCommonAddressingMode passes through nulls")
    void toCommonAddressingModeNulls() {
        var h = build();
        var result = h.service.toCommonAddressingMode(null, PEER_PN);
        assertNull(result[0]);
        assertEquals(PEER_PN, result[1]);
    }

    /**
     * Verifies that getPnAndLidToUpdate returns empty list for null.
     */
    @Test
    @DisplayName("getPnAndLidToUpdate returns empty list for null")
    void getPnAndLidToUpdateNull() {
        assertTrue(build().service.getPnAndLidToUpdate(null).isEmpty());
    }

    /**
     * Verifies that getPnAndLidToUpdate returns [LID, PN] when the LID has a mapping.
     */
    @Test
    @DisplayName("getPnAndLidToUpdate returns [LID, PN] when the LID has a mapping")
    void getPnAndLidToUpdateFromLid() {
        var h = build();
        h.client.store().registerLidMapping(PEER_PN, PEER_LID);
        var pair = h.service.getPnAndLidToUpdate(PEER_LID);
        assertEquals(2, pair.size());
        assertEquals(PEER_LID, pair.get(0));
        assertEquals(PEER_PN, pair.get(1));
    }

    /**
     * Verifies that getPnAndLidToUpdate returns [PN, LID] when the PN has a mapping.
     */
    @Test
    @DisplayName("getPnAndLidToUpdate returns [PN, LID] when the PN has a mapping")
    void getPnAndLidToUpdateFromPn() {
        var h = build();
        h.client.store().registerLidMapping(PEER_PN, PEER_LID);
        var pair = h.service.getPnAndLidToUpdate(PEER_PN);
        assertEquals(2, pair.size());
        assertEquals(PEER_PN, pair.get(0));
        assertEquals(PEER_LID, pair.get(1));
    }

    /**
     * Verifies that getPnAndLidToUpdate returns single-element list when no alternate is known.
     */
    @Test
    @DisplayName("getPnAndLidToUpdate returns single-element list when no alternate is known")
    void getPnAndLidToUpdateNoAlternate() {
        var h = build();
        var pair = h.service.getPnAndLidToUpdate(PEER_PN);
        assertEquals(1, pair.size());
        assertEquals(PEER_PN, pair.getFirst());
    }

    /**
     * Verifies that chatIsLid is false for null chat.
     */
    @Test
    @DisplayName("chatIsLid is false for null chat")
    void chatIsLidNull() {
        assertFalse(build().service.chatIsLid(null));
    }

    /**
     * Verifies that chatIsLid is true for a chat on the LID server.
     */
    @Test
    @DisplayName("chatIsLid is true for a chat on the LID server")
    void chatIsLidLidServer() {
        var h = build();
        var chat = h.client.store().addNewChat(PEER_LID);
        assertTrue(h.service.chatIsLid(chat));
    }

    /**
     * Verifies that chatIsLid is true for a group whose metadata reports isLidAddressingMode=true.
     */
    @Test
    @DisplayName("chatIsLid is true for a group whose metadata reports isLidAddressingMode=true")
    void chatIsLidGroupOnLidMode() {
        var h = build();
        var chat = h.client.store().addNewChat(GROUP);
        var metadata = new GroupMetadataBuilder()
                .jid(GROUP)
                .subject("Test Group")
                .isLidAddressingMode(true)
                .build();
        h.client.store().addChatMetadata(metadata);
        assertTrue(h.service.chatIsLid(chat));
    }

    /**
     * Verifies that chatIsLid is false for a group whose metadata reports isLidAddressingMode=false.
     */
    @Test
    @DisplayName("chatIsLid is false for a group whose metadata reports isLidAddressingMode=false")
    void chatIsLidGroupNotOnLidMode() {
        var h = build();
        var chat = h.client.store().addNewChat(GROUP);
        var metadata = new GroupMetadataBuilder()
                .jid(GROUP)
                .subject("Test Group")
                .isLidAddressingMode(false)
                .build();
        h.client.store().addChatMetadata(metadata);
        assertFalse(h.service.chatIsLid(chat));
    }

    /**
     * Verifies that chatIsLid is false for a 1:1 PN chat regardless of mapping.
     */
    @Test
    @DisplayName("chatIsLid is false for a 1:1 PN chat regardless of mapping")
    void chatIsLidPnChat() {
        var h = build();
        var chat = h.client.store().addNewChat(PEER_PN);
        h.client.store().registerLidMapping(PEER_PN, PEER_LID);
        // The chat JID is PN; chatIsLid only flips for LID-server chats or LID-addressing groups.
        assertFalse(h.service.chatIsLid(chat));
    }
}
