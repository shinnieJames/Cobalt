package com.github.auties00.cobalt.device;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.device.DeviceListMetadataBuilder;
import com.github.auties00.cobalt.model.device.info.DeviceInfo;
import com.github.auties00.cobalt.model.device.info.DeviceListBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
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
 * <p>The handler consumes a {@link com.github.auties00.cobalt.model.device.DeviceListMetadata}
 * payload extracted from an inbound {@code <message>} stanza's
 * {@code messageContextInfo.deviceListMetadata} field. Capturing an
 * inbound message stanza with this metadata is operator-dependent
 * (someone has to message us), so this test instead constructs the
 * metadata payload directly — equivalent to what the Cobalt message
 * receiver pipeline would produce after decrypting an inbound message.
 *
 * <p>Coverage:
 *
 * <ul>
 *   <li>peer-sender messages update the cached device list's
 *       {@code expectedTimestamp} when the sender's snapshot is newer;</li>
 *   <li>messages with a missing cached device list are a no-op (nothing
 *       to update);</li>
 *   <li>primary-device sender with timestamp but no key-hash takes the
 *       minimal-sync path, bumping the cached timestamp by +1s.</li>
 * </ul>
 */
@DisplayName("DeviceService.handleICDCData")
class DeviceServiceHandleICDCDataTest {
    private static final Jid SELF_PN = Jid.of("393495089819@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("258252122116273@lid");
    private static final Jid PEER = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid PEER_DEVICE_1 = Jid.of("12025550100:1@s.whatsapp.net");

    private record Harness(TestWhatsAppClient client, DeviceService deviceService) {
    }

    private static Harness build() {
        var props = TestABPropsService.builder().build();
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        var client = TestWhatsAppClient.create().withStore(store);
        var wamService = new DefaultWamService(client, props);
        var lidMigration = new LidMigrationService(client, props, wamService);
        var snapshotRecovery = new SnapshotRecoveryService(client, props, wamService);
        var webAppState = new WebAppStateService(client, props, lidMigration, snapshotRecovery, wamService);
        var sessionCipher = new SignalSessionCipher(store);
        var deviceService = new DefaultDeviceService(client, webAppState, props, sessionCipher, wamService);
        return new Harness(client, deviceService);
    }

    @Test
    @DisplayName("null metadata is a no-op")
    void nullMetadata() {
        var h = build();
        h.deviceService.handleICDCData(PEER, null, null);
        // The handler returns early on null metadata; we just assert no exception.
    }

    @Test
    @DisplayName("metadata with no cached device list for the sender is a no-op")
    void senderWithNoCachedList() {
        var h = build();
        var metadata = new DeviceListMetadataBuilder()
                .senderTimestamp(Instant.now())
                .senderKeyHash(new byte[]{1, 2, 3})
                .build();

        // Sender is a peer's companion (device id != 0) so the handler skips the
        // minimal-sync path and walks the regular update flow. With no cached list
        // for the peer, the update loop continues without doing anything.
        h.deviceService.handleICDCData(PEER_DEVICE_1, null, metadata);

        assertTrue(h.client.store().findDeviceList(PEER).isEmpty(),
                "no cached list before, none after");
    }

    @Test
    @DisplayName("metadata with newer sender timestamp updates the cached expectedTimestamp")
    void newerSenderTimestampUpdatesExpected() {
        var h = build();
        var cachedTimestamp = Instant.parse("2026-01-01T00:00:00Z");
        var newerTimestamp = Instant.parse("2026-01-02T00:00:00Z");

        // Pre-populate a cached device list whose snapshot timestamp is older than
        // the sender's metadata timestamp.
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
                .senderKeyHash(new byte[]{1, 2, 3}) // non-empty so we hit the regular path
                .build();

        h.deviceService.handleICDCData(PEER_DEVICE_1, null, metadata);

        var updated = h.client.store().findDeviceList(PEER).orElseThrow();
        assertEquals(newerTimestamp, updated.expectedTimestamp(),
                "newer sender timestamp lands as the new expectedTimestamp on the cached list");
        // The cached timestamp itself is unchanged — only expectedTimestamp moves.
        assertEquals(cachedTimestamp, updated.timestamp(),
                "cached snapshot timestamp stays put; only expectedTimestamp moves");
    }

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
                // no senderKeyHash → triggers the minimal-sync path
                .build();

        // PEER (device 0 = primary) sender. The handler routes through
        // handleMinimalTimestampOnlySync which resets the cached list to
        // primary-only and bumps the timestamp by +1s.
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
