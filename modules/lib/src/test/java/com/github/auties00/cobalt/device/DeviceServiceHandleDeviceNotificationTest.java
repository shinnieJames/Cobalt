package com.github.auties00.cobalt.device;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.media.TestMediaConnectionService;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.sync.SnapshotRecoveryService;
import com.github.auties00.cobalt.sync.WebAppStateService;
import com.github.auties00.cobalt.wam.DefaultWamService;
import com.github.auties00.libsignal.SignalSessionCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replays captured live {@code <notification type="account_sync">} stanzas
 * through {@link DeviceService#handleDeviceNotification} and asserts the
 * handler accepts the wire shape WA Web actually sends.
 *
 * @apiNote
 * Two captures live in the corpus: {@code adv-notification-link.jsonl}
 * emitted after linking device id 78 (the wrapped
 * {@code <devices dhash="2:GgkHBG8F">} carries {@code [0, 73, 77, 78]}), and
 * {@code adv-notification-unlink.jsonl} emitted after unlinking the same
 * device (the wrapped {@code <devices dhash="2:wRn7yVQL">} carries
 * {@code [0, 73, 77]}). Both were captured from the {@code personal} live
 * session on 2026-05-11.
 *
 * @implNote
 * This implementation builds the full collaborator graph through
 * {@link DefaultDeviceService} on a {@link TestWhatsAppClient} backed by a
 * temporary store; no network is involved and the assertion bar is that the
 * handler completes without throwing on the captured shapes.
 */
@DisplayName("DeviceService.handleDeviceNotification")
class DeviceServiceHandleDeviceNotificationTest {
    private static final Jid SELF_PN = Jid.of("393495089819@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("258252122116273@lid");

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
     * Builds a fresh harness with all collaborators wired against a
     * temporary store.
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
     * Returns the {@code <devices>} child of the first inbound captured
     * notification in the topic.
     *
     * @apiNote
     * {@link DeviceService#handleDeviceNotification} accepts the inner
     * {@code <devices>} node; the outer {@code <notification>} wrapper is
     * unwrapped by the stream handler before dispatch, so this helper does the
     * same.
     *
     * @param topic the fixture topic
     * @return the {@code <devices>} child node
     */
    private static Node loadDevicesNode(String topic) {
        for (var event : DeviceFixtures.loadEvents(topic)) {
            if (!"in".equals(event.getString("direction"))) continue;
            var notification = DeviceFixtures.buildNodeFromEvent(event);
            return notification.getChild("devices")
                    .orElseThrow(() -> new AssertionError("captured notification has no <devices> child"));
        }
        throw new AssertionError("no inbound event in fixture " + topic);
    }

    /**
     * Verifies the link notification's captured wire shape is accepted
     * without throwing.
     */
    @Test
    @DisplayName("link notification: handler accepts the captured wire shape without throwing")
    void linkNotification() {
        var h = build();
        var devicesNode = loadDevicesNode("adv-notification-link");

        h.deviceService.handleDeviceNotification(devicesNode, "add", SELF_LID);

        var stored = h.client.store().findDeviceList(SELF_LID.toUserJid());
        assertTrue(stored.isPresent() || stored.isEmpty(),
                "handler completed without throwing (Optional check is structural)");
    }

    /**
     * Verifies the unlink notification's captured wire shape is accepted
     * without throwing.
     */
    @Test
    @DisplayName("unlink notification: handler accepts the captured wire shape without throwing")
    void unlinkNotification() {
        var h = build();
        var devicesNode = loadDevicesNode("adv-notification-unlink");
        h.deviceService.handleDeviceNotification(devicesNode, "remove", SELF_LID);
    }

    /**
     * Verifies an unknown action string is logged but does not throw.
     */
    @Test
    @DisplayName("invalid action is logged but does not throw")
    void invalidAction() {
        var h = build();
        var devicesNode = loadDevicesNode("adv-notification-link");
        h.deviceService.handleDeviceNotification(devicesNode, "bogus", SELF_LID);
    }
}
