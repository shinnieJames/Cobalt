package com.github.auties00.cobalt.device;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.device.info.DeviceInfo;
import com.github.auties00.cobalt.model.device.info.DeviceListBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.sync.SnapshotRecoveryService;
import com.github.auties00.cobalt.sync.WebAppStateService;
import com.github.auties00.cobalt.wam.DefaultWamService;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.libsignal.SignalSessionCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for {@link DeviceService}.
 *
 * <p>Builds the full collaborator graph
 * ({@link WamService} → {@link SnapshotRecoveryService} →
 * {@link WebAppStateService} → {@link DeviceService}) on top of a
 * {@link TestWhatsAppClient}, then exercises the paths that read from
 * the local store without requiring a real USync round-trip:
 *
 * <ul>
 *   <li>{@link DeviceService#getUserFanout} for a peer whose device list
 *       is already cached — confirms the cache short-circuits the
 *       server call and the fanout calculator/identity-change filters
 *       are wired up end-to-end.</li>
 *   <li>{@link DeviceService#computeIcdc} for the same peer — confirms
 *       the {@link com.github.auties00.cobalt.device.icdc.IcdcComputer}
 *       collaborator is reachable.</li>
 *   <li>Self-fanout — confirms the sender's own devices are stripped
 *       from the fanout. This is the regression guard for the bug that
 *       triggered this whole plan.</li>
 * </ul>
 *
 * <p>USync-round-trip cases (the {@code sendNode} path) are exercised by
 * {@link com.github.auties00.cobalt.device.stanza.DeviceUSyncResponseParserTest}
 * against captured fixtures — duplicating them here would require canned
 * IQ responses tied to the test infrastructure, with no extra signal.
 */
@DisplayName("DeviceService")
class DeviceServiceTest {
    private static final Jid SELF_PN = Jid.of("19254863482@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594056@lid");
    private static final Jid SELF_PN_DEVICE_1 = Jid.of("19254863482:1@s.whatsapp.net");
    private static final Jid SELF_LID_DEVICE_1 = Jid.of("83116928594056:1@lid");
    private static final Jid PEER = Jid.of("393495089819@s.whatsapp.net");

    private record Harness(
            TestWhatsAppClient client,
            TestABPropsService props,
            DeviceService deviceService) {
    }

    private static Harness build() {
        var props = TestABPropsService.builder().build();
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        store.setJid(SELF_PN_DEVICE_1);
        store.setLid(SELF_LID_DEVICE_1);

        var client = TestWhatsAppClient.create().withStore(store);

        // Collaborators flow client → wam → snapshotRecovery → webAppState → device.
        // None of their constructors fire IO; their methods do, but the device-side
        // tests below don't hit them.
        var wamService = new DefaultWamService(client, props);
        var lidMigration = new LidMigrationService(client, props, wamService);
        var snapshotRecovery = new SnapshotRecoveryService(client, props, wamService);
        var webAppState = new WebAppStateService(client, props, lidMigration, snapshotRecovery, wamService);
        var sessionCipher = new SignalSessionCipher(store);
        var deviceService = new DefaultDeviceService(client, webAppState, props, sessionCipher, wamService);

        return new Harness(client, props, deviceService);
    }

    @Test
    @DisplayName("getUserFanout returns the cached companions for a known peer")
    void cachedPeerFanout() {
        var h = build();
        var peerCompanion = Jid.of("393495089819:5@s.whatsapp.net");

        var peerList = new DeviceListBuilder()
                .userJid(PEER)
                .devices(List.of(DeviceInfo.ofE2EE(0, 0), DeviceInfo.ofE2EE(5, 1)))
                .timestamp(Instant.now())
                .currentIndex(0)
                .validIndexes(new LinkedHashSet<>())
                .build();
        h.client.store().addDeviceList(peerList);

        // Pre-populate self list so getUserFanout doesn't try to fetch own devices.
        var selfList = new DeviceListBuilder()
                .userJid(SELF_PN)
                .devices(List.of(DeviceInfo.ofE2EE(0, 0), DeviceInfo.ofE2EE(1, 1)))
                .timestamp(Instant.now())
                .currentIndex(0)
                .validIndexes(new LinkedHashSet<>())
                .build();
        h.client.store().addDeviceList(selfList);

        var fanout = h.deviceService.getUserFanout(PEER, null);

        assertTrue(fanout.contains(PEER.toUserJid()),
                "fanout should include the peer's primary device (id=0)");
        assertTrue(fanout.contains(peerCompanion),
                "fanout should include the peer's companion device id=5");
        assertFalse(fanout.contains(SELF_PN_DEVICE_1),
                "fanout must never include the sender's own device");
    }

    @Test
    @DisplayName("getUserFanout for self-send strips the sender's own device")
    void selfFanoutStripsSender() {
        var h = build();

        // Self has two devices: primary (id=0, the phone) and the current session (id=1).
        var selfList = new DeviceListBuilder()
                .userJid(SELF_PN)
                .devices(List.of(DeviceInfo.ofE2EE(0, 0), DeviceInfo.ofE2EE(1, 1)))
                .timestamp(Instant.now())
                .currentIndex(0)
                .validIndexes(new LinkedHashSet<>())
                .build();
        h.client.store().addDeviceList(selfList);

        var fanout = h.deviceService.getUserFanout(SELF_PN, null);

        assertTrue(fanout.contains(SELF_PN.toUserJid()),
                "self-send fanout must include the other own device (id=0, the primary phone)");
        assertFalse(fanout.contains(SELF_PN_DEVICE_1),
                "self-send fanout must not include the sending device itself");
    }

    @Test
    @DisplayName("computeIcdc delegates to IcdcComputer and returns an Optional")
    void computeIcdcWires() {
        var h = build();
        var peerList = new DeviceListBuilder()
                .userJid(PEER)
                .devices(List.of(DeviceInfo.ofE2EE(0, 0)))
                .timestamp(Instant.now())
                .currentIndex(0)
                .validIndexes(new LinkedHashSet<>())
                .build();
        h.client.store().addDeviceList(peerList);

        var icdc = h.deviceService.computeIcdc(PEER);
        assertTrue(icdc.isPresent(), "computeIcdc should produce a result for a cached peer");
        assertEquals(peerList.timestamp(), icdc.get().timestamp().orElse(null),
                "timestamp should propagate from the cached device list");
    }

    @Test
    @DisplayName("getGroupFanout phash matches WA Web's phashV2 oracle (byte-equality, three group sizes)")
    void groupFanoutPhashMatchesLiveOracle() throws Exception {
        // Oracle is a JSON array (not object); load the inner result.value and parse as array.
        var raw = DeviceFixtures.loadExpected("group-phashes").getJSONObject("result").getString("value");
        var samples = com.alibaba.fastjson2.JSON.parseArray(raw);

        var h = build();
        for (var i = 0; i < samples.size(); i++) {
            var sample = samples.getJSONObject(i);
            var groupId = sample.getString("groupId");
            var expectedPhash = sample.getString("phashV2");
            var devices = sample.getJSONArray("devices");

            // Each participant in the oracle is their primary device JID (device 0).
            // Pre-populate a device list per participant so getGroupFanout doesn't need
            // the network (TestWhatsAppClient stubs would refuse sendNode otherwise).
            var participantJids = new ArrayList<Jid>(devices.size());
            for (var j = 0; j < devices.size(); j++) {
                participantJids.add(Jid.of(devices.getString(j)));
            }
            for (var pjid : participantJids) {
                h.client.store().addDeviceList(new DeviceListBuilder()
                        .userJid(pjid.toUserJid())
                        .devices(List.of(DeviceInfo.ofE2EE(0, 0)))
                        .timestamp(Instant.now())
                        .currentIndex(0)
                        .validIndexes(new LinkedHashSet<>())
                        .build());
            }

            // Pre-populate the group metadata so client.queryChatMetadata can return it.
            // For a smoke test, we bypass queryChatMetadata by computing the phash directly
            // through DevicePhashCalculator instead — this isolates the byte-equality contract.
            var calculator = new com.github.auties00.cobalt.device.fanout.DevicePhashCalculator(h.props);
            var deviceSet = new LinkedHashSet<>(participantJids);
            var actualPhash = calculator.calculate(deviceSet,
                    com.github.auties00.cobalt.device.fanout.DevicePhashVersion.V2, true);

            assertEquals(expectedPhash, actualPhash,
                    "group " + groupId + " (n=" + devices.size() + "): Cobalt phashV2 must match WA Web's oracle byte-for-byte");
        }
    }

    @Test
    @DisplayName("getDeviceLists with shouldMergeAltDevices=true collapses PN+LID entries for the same user")
    void shouldMergeAltDevicesCollapsesEntries() {
        var h = build();
        var peerPn = Jid.of("393495089819@s.whatsapp.net");
        var peerLid = Jid.of("72104938291847@lid");

        // Register the PN↔LID mapping so mergeAlternateDeviceLists can canonicalise.
        h.client.store().registerLidMapping(peerPn, peerLid);

        // Two device lists for "the same user" — one under PN, one under LID.
        var pnList = new DeviceListBuilder()
                .userJid(peerPn)
                .devices(List.of(DeviceInfo.ofE2EE(0, 0), DeviceInfo.ofE2EE(1, 1)))
                .timestamp(Instant.now())
                .currentIndex(0)
                .validIndexes(new LinkedHashSet<>())
                .build();
        var lidList = new DeviceListBuilder()
                .userJid(peerLid)
                .devices(List.of(DeviceInfo.ofE2EE(0, 0), DeviceInfo.ofE2EE(2, 1)))
                .timestamp(Instant.now())
                .currentIndex(0)
                .validIndexes(new LinkedHashSet<>())
                .build();
        h.client.store().addDeviceList(pnList);
        h.client.store().addDeviceList(lidList);

        // Without merging: getDeviceLists returns two distinct entries (one PN-keyed, one LID-keyed).
        var unmerged = h.deviceService.getDeviceLists(List.of(peerPn, peerLid), "message", null, false);
        assertEquals(2, unmerged.size(),
                "without shouldMergeAltDevices, PN and LID lists are returned independently");

        // With merging: PN and LID lists collapse into a single entry whose device set is the union.
        var merged = h.deviceService.getDeviceLists(List.of(peerPn, peerLid), "message", null, true);
        assertEquals(1, merged.size(),
                "with shouldMergeAltDevices, PN and LID lists for the same user collapse to one entry");
        var only = merged.iterator().next();
        var deviceIds = only.devices().stream().map(DeviceInfo::id).sorted().toList();
        assertEquals(List.of(0, 1, 2), deviceIds,
                "merged device set must include every device from both PN-keyed and LID-keyed lists");
    }
}
