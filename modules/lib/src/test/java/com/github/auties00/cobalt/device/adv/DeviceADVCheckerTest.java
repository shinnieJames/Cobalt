package com.github.auties00.cobalt.device.adv;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.device.DefaultDeviceService;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.device.info.DeviceInfo;
import com.github.auties00.cobalt.model.device.info.DeviceList;
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

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DeviceADVChecker}'s analysis core.
 *
 * @apiNote
 * The scheduler itself uses {@link Instant#now()} for the wall clock and a
 * singleton {@link java.util.concurrent.ScheduledExecutorService}; both are
 * difficult to drive under test without a production-side clock injection.
 * The staleness analysis was therefore extracted into a package-private
 * {@code analyzeDeviceLists} that takes {@code now} as an explicit parameter;
 * this test class exercises that method directly with synthetic device-list
 * states and asserts the scheduler-facing contract: fresh records survive,
 * stale records are flagged for expiration, own-device-list expiration sets
 * {@code selfExpired}, and primary-only and deleted records are skipped.
 *
 * @implNote
 * This implementation builds the full collaborator graph through
 * {@link DefaultDeviceService} so the checker can read the cached
 * {@code lastAdvCheckTime} from a real {@link com.github.auties00.cobalt.device.DeviceService};
 * tests then pass synthetic timestamps to {@code analyzeDeviceLists} so the
 * outcome is deterministic.
 */
@DisplayName("DeviceADVChecker")
class DeviceADVCheckerTest {
    private static final Jid SELF_PN = Jid.of("393495089819@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("258252122116273@lid");
    private static final Jid PEER = Jid.of("12025550100@s.whatsapp.net");

    /**
     * Bundles the constructed client, props, and checker so each test can
     * share the same wiring.
     *
     * @apiNote
     * Local record; never exposed outside this class.
     *
     * @param client  the test client
     * @param props   the test AB props service
     * @param checker the constructed ADV checker
     */
    private record Harness(TestWhatsAppClient client, TestABPropsService props, DeviceADVChecker checker) {
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
        var checker = new DeviceADVChecker(client, deviceService, props, wamService);
        return new Harness(client, props, checker);
    }

    /**
     * Builds a {@link DeviceList} for the given user with the supplied
     * timestamp and devices.
     *
     * @apiNote
     * Local test helper; uses a fresh empty {@code validIndexes} so the
     * analysis treats every device as potentially-valid.
     *
     * @param userJid   the user JID
     * @param timestamp the list timestamp
     * @param devices   the devices
     * @return the constructed device list
     */
    private static DeviceList list(Jid userJid, Instant timestamp, List<DeviceInfo> devices) {
        return new DeviceListBuilder()
                .userJid(userJid)
                .devices(devices)
                .timestamp(timestamp)
                .currentIndex(0)
                .validIndexes(new LinkedHashSet<>())
                .build();
    }

    /**
     * Verifies a fresh device list (one hour old, 24-hour threshold) is not
     * flagged for expiration.
     */
    @Test
    @DisplayName("analyzeDeviceLists: fresh records aren't flagged for expiration")
    void freshRecordsSurvive() {
        var h = build();
        var now = Instant.parse("2026-05-11T00:00:00Z");
        var lastCheck = now.minus(Duration.ofHours(23));
        var freshTimestamp = now.minus(Duration.ofHours(1));

        var peer = list(PEER, freshTimestamp,
                List.of(DeviceInfo.ofE2EE(0, 0), DeviceInfo.ofE2EE(1, 1)));

        var result = h.checker.analyzeDeviceLists(
                List.of(peer), now,
                Duration.ofHours(24), Duration.ofHours(20),
                lastCheck, SELF_PN);

        assertFalse(result.selfExpired(), "no self list, no self-expiry");
        assertEquals(0, result.expiredLists().size(),
                "fresh device list (1h old, 24h threshold) is not expired");
    }

    /**
     * Verifies a stale peer list is flagged for expiration and queued for
     * sync, with {@code selfExpired} unchanged.
     */
    @Test
    @DisplayName("analyzeDeviceLists: stale records get queued for sync, selfExpired stays false for non-self")
    void stalePeerRecord() {
        var h = build();
        var now = Instant.parse("2026-05-11T00:00:00Z");
        var lastCheck = now.minus(Duration.ofHours(23));
        var staleTimestamp = now.minus(Duration.ofDays(2));

        var peer = list(PEER, staleTimestamp,
                List.of(DeviceInfo.ofE2EE(0, 0), DeviceInfo.ofE2EE(1, 1)));

        var result = h.checker.analyzeDeviceLists(
                List.of(peer), now,
                Duration.ofHours(24), Duration.ofHours(20),
                lastCheck, SELF_PN);

        assertFalse(result.selfExpired(),
                "peer list expiring doesn't trigger self-expired");
        assertEquals(1, result.expiredLists().size(),
                "peer list older than the 24h threshold is flagged as expired");
        assertTrue(result.jidsNeedingSync().contains(PEER),
                "expired peer is queued for proactive sync");
    }

    /**
     * Verifies the local user's own expired device list sets
     * {@code selfExpired}.
     *
     * @apiNote
     * Downstream of this flag the checker logs the local user out (subject to
     * the {@code web_adv_logout_on_self_device_list_expired} AB prop).
     */
    @Test
    @DisplayName("analyzeDeviceLists: own device-list expiration sets selfExpired")
    void selfExpiredTriggers() {
        var h = build();
        var now = Instant.parse("2026-05-11T00:00:00Z");
        var lastCheck = now.minus(Duration.ofHours(23));
        var staleTimestamp = now.minus(Duration.ofDays(2));

        var self = list(SELF_PN, staleTimestamp,
                List.of(DeviceInfo.ofE2EE(0, 0), DeviceInfo.ofE2EE(75, 1)));

        var result = h.checker.analyzeDeviceLists(
                List.of(self), now,
                Duration.ofHours(24), Duration.ofHours(20),
                lastCheck, SELF_PN);

        assertTrue(result.selfExpired(),
                "own device list older than the threshold triggers selfExpired (downstream: logout)");
        assertEquals(1, result.expiredLists().size());
    }

    /**
     * Verifies primary-only and deleted device lists are skipped before the
     * staleness check.
     */
    @Test
    @DisplayName("analyzeDeviceLists: primary-only and deleted lists are skipped")
    void primaryOnlyAndDeletedSkipped() {
        var h = build();
        var now = Instant.parse("2026-05-11T00:00:00Z");
        var lastCheck = now.minus(Duration.ofHours(23));
        var staleTimestamp = now.minus(Duration.ofDays(2));

        var primaryOnly = list(PEER, staleTimestamp, List.of(DeviceInfo.ofE2EE(0, 0)));
        var deleted = new DeviceListBuilder()
                .userJid(Jid.of("12025550101@s.whatsapp.net"))
                .devices(List.of(DeviceInfo.ofE2EE(0, 0), DeviceInfo.ofE2EE(1, 1)))
                .timestamp(staleTimestamp)
                .deleted(true)
                .currentIndex(0)
                .validIndexes(new LinkedHashSet<>())
                .build();

        var result = h.checker.analyzeDeviceLists(
                List.of(primaryOnly, deleted), now,
                Duration.ofHours(24), Duration.ofHours(20),
                lastCheck, SELF_PN);

        assertEquals(0, result.expiredLists().size(),
                "primary-only and deleted lists are skipped before staleness check");
    }
}
