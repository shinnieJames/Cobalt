package com.github.auties00.cobalt.migration;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.ChatDisappearingMode;
import com.github.auties00.cobalt.model.chat.ChatDisappearingModeBuilder;
import com.github.auties00.cobalt.model.chat.ChatEphemeralTimer;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.chat.ChatMute;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parity tests for {@link LidMigrationService#canDeleteChat(Chat)}.
 *
 * @apiNote
 * Pins the deletability heuristic the LID migration applies to a 1:1
 * thread that resolves to no LID mapping anywhere; matches WA Web's
 * {@code WAWebLid1X1ThreadAccountMigrations.K} branch by branch.
 *
 * @implNote
 * This implementation constructs fresh in-memory harnesses via
 * {@link MigrationFixtures#temporaryStore(Jid, Jid)} so each branch
 * of the deletability cascade is exercised against an isolated chat.
 */
@DisplayName("LidMigrationService.canDeleteChat")
class LidMigrationServiceCanDeleteChatTest {

    private static final Jid SELF_PN = Jid.of("19254863482@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594056@lid");
    private static final Jid PEER_PN = Jid.of("393495089819@s.whatsapp.net");

    /**
     * Bundles the test client and the service under test.
     *
     * @apiNote
     * Carrier record returned from {@link #build()} so individual
     * tests can navigate to either the client (for failure
     * observation) or the service (for behaviour assertions).
     *
     * @param client  the test client harness
     * @param service the service under test
     */
    private record Harness(TestWhatsAppClient client, LidMigrationService service) {}

    /**
     * Builds a fresh harness with a default
     * {@link TestABPropsService}.
     *
     * @apiNote
     * The default AB props leave the
     * {@code lid_one_on_one_migration_*} switches at their defaults;
     * tests that need a specific AB prop wiring inline their own
     * harness builder.
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
     * Adds a fresh PN-keyed chat for {@link #PEER_PN} to the store.
     *
     * @param h the harness whose store will receive the chat
     * @return the newly-added {@link Chat}
     */
    private static Chat newChat(Harness h) {
        return h.client.store().addNewChat(PEER_PN);
    }

    /**
     * Builds a stub {@link ChatMessageInfo} of the given type at the
     * given timestamp.
     *
     * @apiNote
     * Stubs are the empty-body system messages the deletability
     * cascade discriminates against; tests populate them through
     * this helper so they all share identical key shape.
     *
     * @param stubType the system stub type to assign
     * @param ts       the message timestamp
     * @return the assembled stub message
     */
    private static ChatMessageInfo stubMessage(ChatMessageInfo.StubType stubType, Instant ts) {
        var key = new MessageKeyBuilder()
                .id("stub-" + stubType.name())
                .fromMe(false)
                .parentJid(PEER_PN)
                .build();
        return new ChatMessageInfoBuilder()
                .key(key)
                .message(MessageContainer.empty())
                .stubType(stubType)
                .timestamp(ts)
                .build();
    }

    /**
     * Builds a regular text {@link ChatMessageInfo} at the given
     * timestamp.
     *
     * @apiNote
     * Used to introduce a single non-stub message that breaks the
     * "all-stubs" deletability rule.
     *
     * @param ts the message timestamp
     * @return the assembled text message
     */
    private static ChatMessageInfo textMessage(Instant ts) {
        var key = new MessageKeyBuilder()
                .id("text")
                .fromMe(false)
                .parentJid(PEER_PN)
                .build();
        return new ChatMessageInfoBuilder()
                .key(key)
                .message(MessageContainer.of("hello"))
                .timestamp(ts)
                .build();
    }

    /**
     * Builds a broadcast {@link ChatMessageInfo} at the given
     * timestamp.
     *
     * @apiNote
     * Used to exercise the broadcast-exemption branch of the
     * deletability cascade; combined with a pre-pairing oldest
     * message timestamp, broadcast-only history is droppable.
     *
     * @param ts the message timestamp
     * @return the assembled broadcast message
     */
    private static ChatMessageInfo broadcastMessage(Instant ts) {
        var key = new MessageKeyBuilder()
                .id("broadcast")
                .fromMe(false)
                .parentJid(PEER_PN)
                .build();
        return new ChatMessageInfoBuilder()
                .key(key)
                .message(MessageContainer.empty())
                .broadcast(true)
                .timestamp(ts)
                .build();
    }

    /**
     * Verifies that an empty chat is deletable through the vacuous
     * all-safe-stubs branch.
     */
    @Test
    @DisplayName("empty chat is deletable (vacuous all-safe-stubs)")
    void emptyChat() {
        var h = build();
        var chat = newChat(h);
        assertTrue(h.service.canDeleteChat(chat));
    }

    /**
     * Verifies that a chat containing only E2E_ENCRYPTED-family
     * stubs satisfies the all-safe-stubs rule.
     */
    @Test
    @DisplayName("E2E_ENCRYPTED stubs only -> deletable")
    void allE2eEncryptedStubs() {
        var h = build();
        var chat = newChat(h);
        chat.addMessage(stubMessage(ChatMessageInfo.StubType.E2E_ENCRYPTED, Instant.now()));
        chat.addMessage(stubMessage(ChatMessageInfo.StubType.E2E_ENCRYPTED_NOW, Instant.now()));
        assertTrue(h.service.canDeleteChat(chat));
    }

    /**
     * Verifies that a chat containing only DISAPPEARING_MODE stubs
     * satisfies the all-safe-stubs rule.
     */
    @Test
    @DisplayName("DISAPPEARING_MODE stubs only -> deletable")
    void allDisappearingModeStubs() {
        var h = build();
        var chat = newChat(h);
        chat.addMessage(stubMessage(ChatMessageInfo.StubType.DISAPPEARING_MODE, Instant.now()));
        assertTrue(h.service.canDeleteChat(chat));
    }

    /**
     * Verifies that one call-log entry alongside safe stubs lets
     * the stubs-or-call-log rule fire.
     */
    @Test
    @DisplayName("safe stubs + one call-log entry -> deletable (call-log path)")
    void safeStubsPlusCallLog() {
        var h = build();
        var chat = newChat(h);
        chat.addMessage(stubMessage(ChatMessageInfo.StubType.E2E_ENCRYPTED, Instant.now()));
        chat.addMessage(stubMessage(ChatMessageInfo.StubType.CALL_MISSED_VOICE, Instant.now()));
        assertTrue(h.service.canDeleteChat(chat));
    }

    /**
     * Verifies that a single regular text message breaks every
     * deletability rule.
     */
    @Test
    @DisplayName("safe stubs + one regular text message -> not deletable")
    void safeStubsPlusText() {
        var h = build();
        var chat = newChat(h);
        chat.addMessage(stubMessage(ChatMessageInfo.StubType.E2E_ENCRYPTED, Instant.now()));
        chat.addMessage(textMessage(Instant.now()));
        assertFalse(h.service.canDeleteChat(chat));
    }

    /**
     * Verifies that safe stubs + broadcast, pairing-ts <= oldest msg -> deletable (broadcast-exempt).
     */
    @Test
    @DisplayName("safe stubs + broadcast, pairing-ts <= oldest msg -> deletable (broadcast-exempt)")
    void broadcastExemptPairingBeforeOldest() {
        var h = build();
        var pairing = Instant.parse("2026-01-01T00:00:00Z");
        var oldest = Instant.parse("2026-01-02T00:00:00Z");
        h.client.store().setPairingTimestamp(pairing);

        var chat = newChat(h);
        chat.addMessage(stubMessage(ChatMessageInfo.StubType.E2E_ENCRYPTED, oldest.plusSeconds(60)));
        chat.addMessage(broadcastMessage(oldest));

        assertTrue(h.service.canDeleteChat(chat),
                "pairing precedes the oldest message, so broadcast-only history is safe to drop");
    }

    /**
     * Verifies that safe stubs + broadcast, pairing-ts > oldest msg -> not deletable.
     */
    @Test
    @DisplayName("safe stubs + broadcast, pairing-ts > oldest msg -> not deletable")
    void broadcastNoExemptPairingAfterOldest() {
        var h = build();
        var oldest = Instant.parse("2026-01-01T00:00:00Z");
        var pairing = Instant.parse("2026-01-02T00:00:00Z");
        h.client.store().setPairingTimestamp(pairing);

        var chat = newChat(h);
        chat.addMessage(broadcastMessage(oldest));

        assertFalse(h.service.canDeleteChat(chat),
                "pairing happened after the broadcast was received, so the broadcast belongs to user history");
    }

    /**
     * Verifies that safe stubs + broadcast, no pairing-ts -> not deletable.
     */
    @Test
    @DisplayName("safe stubs + broadcast, no pairing-ts -> not deletable")
    void broadcastNoExemptNoPairing() {
        var h = build();
        var chat = newChat(h);
        chat.addMessage(broadcastMessage(Instant.now()));
        // No pairing timestamp set.
        assertFalse(h.service.canDeleteChat(chat));
    }

    /**
     * Verifies that ephemeralExpiration set -> not deletable.
     */
    @Test
    @DisplayName("ephemeralExpiration set -> not deletable")
    void ephemeralExpirationBlocks() {
        var h = build();
        var chat = newChat(h);
        chat.setEphemeralExpiration(ChatEphemeralTimer.ONE_WEEK);
        chat.addMessage(stubMessage(ChatMessageInfo.StubType.E2E_ENCRYPTED, Instant.now()));
        assertFalse(h.service.canDeleteChat(chat));
    }

    /**
     * Verifies that ephemeralSettingTimestamp set -> not deletable.
     */
    @Test
    @DisplayName("ephemeralSettingTimestamp set -> not deletable")
    void ephemeralSettingTimestampBlocks() {
        var h = build();
        var chat = newChat(h);
        chat.setEphemeralSettingTimestamp(Instant.now());
        chat.addMessage(stubMessage(ChatMessageInfo.StubType.E2E_ENCRYPTED, Instant.now()));
        assertFalse(h.service.canDeleteChat(chat));
    }

    /**
     * Verifies that ephemeral + ACCOUNT_SETTING trigger + DISAPPEARING_MODE stub -> ephemeral-exempt -> deletable.
     */
    @Test
    @DisplayName("ephemeral + ACCOUNT_SETTING trigger + DISAPPEARING_MODE stub -> ephemeral-exempt -> deletable")
    void ephemeralAccountSettingExempt() {
        var h = build();
        var chat = newChat(h);
        chat.setEphemeralExpiration(ChatEphemeralTimer.ONE_DAY);
        chat.setDisappearingMode(new ChatDisappearingModeBuilder()
                .trigger(ChatDisappearingMode.Trigger.ACCOUNT_SETTING)
                .build());
        chat.addMessage(stubMessage(ChatMessageInfo.StubType.DISAPPEARING_MODE, Instant.now()));
        assertTrue(h.service.canDeleteChat(chat),
                "ACCOUNT_SETTING trigger + DISAPPEARING_MODE stub bypasses the ephemeral block");
    }

    /**
     * Verifies that ephemeral + ACCOUNT_SETTING trigger but no DISAPPEARING_MODE stub -> not deletable.
     */
    @Test
    @DisplayName("ephemeral + ACCOUNT_SETTING trigger but no DISAPPEARING_MODE stub -> not deletable")
    void ephemeralAccountSettingNoStubBlocks() {
        var h = build();
        var chat = newChat(h);
        chat.setEphemeralExpiration(ChatEphemeralTimer.ONE_DAY);
        chat.setDisappearingMode(new ChatDisappearingModeBuilder()
                .trigger(ChatDisappearingMode.Trigger.ACCOUNT_SETTING)
                .build());
        chat.addMessage(stubMessage(ChatMessageInfo.StubType.E2E_ENCRYPTED, Instant.now()));
        assertFalse(h.service.canDeleteChat(chat),
                "exempt only fires when DISAPPEARING_MODE stub is present");
    }

    /**
     * Verifies that locked=true -> not deletable.
     */
    @Test
    @DisplayName("locked=true -> not deletable")
    void lockedBlocks() {
        var h = build();
        var chat = newChat(h);
        chat.setLocked(true);
        assertFalse(h.service.canDeleteChat(chat));
    }

    /**
     * Verifies that archived=true -> not deletable.
     */
    @Test
    @DisplayName("archived=true -> not deletable")
    void archivedBlocks() {
        var h = build();
        var chat = newChat(h);
        chat.setArchived(true);
        assertFalse(h.service.canDeleteChat(chat));
    }

    /**
     * Verifies that muted -> not deletable.
     */
    @Test
    @DisplayName("muted -> not deletable")
    void mutedBlocks() {
        var h = build();
        var chat = newChat(h);
        chat.setMute(ChatMute.muted());
        assertFalse(h.service.canDeleteChat(chat));
    }

    /**
     * Verifies that notMuted -> still deletable for empty chat.
     */
    @Test
    @DisplayName("notMuted -> still deletable for empty chat")
    void notMutedDoesNotBlock() {
        var h = build();
        var chat = newChat(h);
        chat.setMute(ChatMute.notMuted());
        assertTrue(h.service.canDeleteChat(chat));
    }

    /**
     * Verifies that regression: stub type set but message body non-empty -> not a migration-safe stub -> not deletable.
     */
    @Test
    @DisplayName("regression: stub type set but message body non-empty -> not a migration-safe stub -> not deletable")
    void stubTypeWithNonEmptyMessageNotSafe() {
        var h = build();
        var chat = newChat(h);
        var key = new MessageKeyBuilder()
                .id("real-msg-with-stub-type")
                .fromMe(false)
                .parentJid(PEER_PN)
                .build();
        var msg = new ChatMessageInfoBuilder()
                .key(key)
                .message(MessageContainer.of("not empty"))
                .stubType(ChatMessageInfo.StubType.E2E_ENCRYPTED)
                .timestamp(Instant.now())
                .build();
        chat.addMessage(msg);
        assertFalse(h.service.canDeleteChat(chat),
                "isMigrationSafeStub guards on !message.isEmpty() before checking stubType");
    }
}
