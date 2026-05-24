package com.github.auties00.cobalt.device;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.device.DeviceListMetadataBuilder;
import com.github.auties00.cobalt.model.device.info.DeviceInfo;
import com.github.auties00.cobalt.model.device.info.DeviceListBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.media.TestMediaConnectionService;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.sync.SnapshotRecoveryService;
import com.github.auties00.cobalt.sync.WebAppStateService;
import com.github.auties00.cobalt.wam.DefaultWamService;
import com.github.auties00.libsignal.SignalSessionCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link DeviceService#handleICDCData}.
 *
 * @apiNote
 * Exercises the three branches the handler distinguishes: peer-sender messages
 * update the cached device list's {@code expectedTimestamp} when the sender's
 * snapshot is newer, messages with a missing cached device list are a no-op,
 * and primary-device sender with timestamp but no key-hash takes the
 * minimal-sync path that bumps the cached timestamp by one second.
 *
 * @implNote
 * This implementation constructs the {@link com.github.auties00.cobalt.model.device.DeviceListMetadata}
 * directly rather than capturing an inbound message stanza: capturing an
 * inbound message with this metadata is operator-dependent (someone has to
 * message the test session), and the metadata payload is identical to what
 * the Cobalt message receiver pipeline produces after decrypting an inbound
 * message.
 */
@DisplayName("DeviceService.handleICDCData")
class DeviceServiceHandleICDCDataTest {
    private static final Jid SELF_PN = Jid.of("393495089819@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("258252122116273@lid");
    private static final Jid PEER = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid PEER_DEVICE_1 = Jid.of("12025550100:1@s.whatsapp.net");

    /**
     * Bundles the constructed client and device service so each test can
     * share the same wiring.
     *
     * @apiNote
     * Local record; never exposed outside this class.
     *
     * @param client        the test client
     * @param deviceService the constructed device service
     */
    private record Harness(TestWhatsAppClient client, DeviceService deviceService) {
    }

    /**
     * Builds a fresh harness with all collaborators wired against a temporary
     * store.
     *
     * @apiNote
     * Called by every test for isolation; never reused across tests.
     *
     * @return the constructed harness
     */
    private static Harness build() {
        var props = TestABPropsService.builder().build();
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        var client = TestWhatsAppClient.create().withStore(store);
        var wamService = new DefaultWamService(client, props);
        var lidMigration = new LidMigrationService(client, props, wamService);
        var snapshotRecovery = new SnapshotRecoveryService(client, props, wamService);
        var webAppState = new WebAppStateService(client, props, lidMigration, snapshotRecovery, wamService, TestMediaConnectionService.create());
        var sessionCipher = new SignalSessionCipher(store);
        var deviceService = new DefaultDeviceService(client, webAppState, props, sessionCipher, wamService);
        return new Harness(client, deviceService);
    }

    /**
     * Verifies the handler is a no-op when the metadata is {@code null}.
     */
    @Test
    @DisplayName("null metadata is a no-op")
    void nullMetadata() {
        var h = build();
        h.deviceService.handleICDCData(PEER, null, null);
    }

    /**
     * Verifies the regular update flow is a no-op when no device list is
     * cached for the sender.
     *
     * @implNote
     * Sends from a peer companion (device id != 0) so the handler skips the
     * minimal-sync path; with no cached list for the peer, the update loop
     * has nothing to write.
     */
    @Test
    @DisplayName("metadata with no cached device list for the sender is a no-op")
    void senderWithNoCachedList() {
        var h = build();
        var metadata = new DeviceListMetadataBuilder()
                .senderTimestamp(Instant.now())
                .senderKeyHash(new byte[]{1, 2, 3})
                .build();

        h.deviceService.handleICDCData(PEER_DEVICE_1, null, metadata);

        assertTrue(h.client.store().findDeviceList(PEER).isEmpty(),
                "no cached list before, none after");
    }

    /**
     * Verifies a newer sender timestamp lands as the cached list's new
     * {@code expectedTimestamp} while leaving the snapshot timestamp alone.
     */
    @Test
    @DisplayName("metadata with newer sender timestamp updates the cached expectedTimestamp")
    void newerSenderTimestampUpdatesExpected() {
        var h = build();
        var cachedTimestamp = Instant.parse("2026-01-01T00:00:00Z");
        var newerTimestamp = Instant.parse("2026-01-02T00:00:00Z");

        var cached = new DeviceListBuilder()
                .userJid(PEER)
                .devices(List.of(DeviceInfo.ofE2EE(0, 0), DeviceInfo.ofE2EE(1, 1)))
                .timestamp(cachedTimestamp)
                .currentIndex(0)
                .validIndexes(new LinkedHashSet<>())
                .build();
        h.client.store().addDeviceList(cached);

        var metadata = new DeviceListMetadataBuilder()
                .senderTimestamp(newerTimestamp)
                .senderKeyHash(new byte[]{1, 2, 3})
                .build();

        h.deviceService.handleICDCData(PEER_DEVICE_1, null, metadata);

        var updated = h.client.store().findDeviceList(PEER).orElseThrow();
        assertEquals(newerTimestamp, updated.expectedTimestamp(),
                "newer sender timestamp lands as the new expectedTimestamp on the cached list");
        assertEquals(cachedTimestamp, updated.timestamp(),
                "cached snapshot timestamp stays put; only expectedTimestamp moves");
    }

    /**
     * Verifies the primary-device minimal-sync path resets the cached list to
     * primary-only and bumps the timestamp by one second.
     *
     * @implNote
     * The handler routes sender = primary (device id 0) with metadata that
     * carries only a timestamp (no key hash) through
     * {@code handleMinimalTimestampOnlySync}; that path resets the cached
     * list to primary-only and adds one second to the cached timestamp.
     */
    @Test
    @DisplayName("primary-device sender with no key-hash takes the minimal-sync path (+1s bump)")
    void primaryDeviceMinimalSyncBumpsTimestamp() {
        var h = build();
        var cachedTimestamp = Instant.parse("2026-01-01T00:00:00Z");
        var senderTimestamp = Instant.parse("2026-01-02T00:00:00Z");

        var cached = new DeviceListBuilder()
                .userJid(PEER)
                .devices(List.of(DeviceInfo.ofE2EE(0, 0), DeviceInfo.ofE2EE(1, 1)))
                .timestamp(cachedTimestamp)
                .currentIndex(0)
                .validIndexes(new LinkedHashSet<>())
                .build();
        h.client.store().addDeviceList(cached);

        var metadata = new DeviceListMetadataBuilder()
                .senderTimestamp(senderTimestamp)
                .build();

        h.deviceService.handleICDCData(PEER, null, metadata);

        var updated = h.client.store().findDeviceList(PEER).orElseThrow();
        assertEquals(senderTimestamp.plusSeconds(1), updated.timestamp(),
                "minimal-sync path bumps the timestamp to senderTimestamp+1s");
        assertEquals(1, updated.devices().size(),
                "minimal-sync resets the cached list to primary-only");
        assertEquals(0, updated.devices().getFirst().id(),
                "the remaining device is the primary (id=0)");
    }
}
