package com.github.auties00.cobalt.message;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.StubDeviceService;
import com.github.auties00.cobalt.media.TestMediaConnectionService;
import com.github.auties00.cobalt.media.transcode.MediaTranscoderService;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.wam.DefaultWamService;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.groups.SignalGroupCipher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the {@link MessageService} facade that wires the send and receive pipelines
 * together: constructor null-argument rejection, happy-path assembly, and the idempotence
 * of {@link MessageService#clearPendingMessages()}. Each subject is built through
 * {@link MessageFixtures#temporaryStore} plus {@link TestWhatsAppClient} so the assembled
 * facade is isolated from any process-wide state. The heavier per-pipeline behaviour is
 * covered by the {@link com.github.auties00.cobalt.message.send.MessageSendingService} and
 * {@link com.github.auties00.cobalt.message.receive.MessageReceivingService} test suites.
 */
@DisplayName("MessageService")
class MessageServiceTest {

    private static final Jid SELF_PN = Jid.of("12025550100@s.whatsapp.net");

    @Test
    @DisplayName("constructor: every collaborator is required (null throws NullPointerException)")
    void constructorNullArgs() {
        var store = MessageFixtures.temporaryStore(SELF_PN, null);
        var client = TestWhatsAppClient.create()
                .withStore(store)
                .withAbPropsService(TestABPropsService.builder().build());
        var session = new SignalSessionCipher(store);
        var group = new SignalGroupCipher(store);
        var device = StubDeviceService.create();
        var props = client.abPropsService();
        var wam = new DefaultWamService(client, props);
        var migration = new LidMigrationService(client, props, wam);
        var transcoder = new MediaTranscoderService(client, props, TestMediaConnectionService.create());

        assertThrows(NullPointerException.class, () -> new MessageService(null, session, group, device, migration, props, wam, transcoder));
        assertThrows(NullPointerException.class, () -> new MessageService(client, null, group, device, migration, props, wam, transcoder));
        assertThrows(NullPointerException.class, () -> new MessageService(client, session, null, device, migration, props, wam, transcoder));
        assertThrows(NullPointerException.class, () -> new MessageService(client, session, group, null, migration, props, wam, transcoder));
        assertThrows(NullPointerException.class, () -> new MessageService(client, session, group, device, null, props, wam, transcoder));
        assertThrows(NullPointerException.class, () -> new MessageService(client, session, group, device, migration, null, wam, transcoder));
        assertThrows(NullPointerException.class, () -> new MessageService(client, session, group, device, migration, props, null, transcoder));
        assertThrows(NullPointerException.class, () -> new MessageService(client, session, group, device, migration, props, wam, null));
    }

    @Test
    @DisplayName("constructor: with all valid collaborators, the service is constructed successfully")
    void constructorHappyPath() {
        var store = MessageFixtures.temporaryStore(SELF_PN, null);
        var client = TestWhatsAppClient.create()
                .withStore(store)
                .withAbPropsService(TestABPropsService.builder().build());
        var session = new SignalSessionCipher(store);
        var group = new SignalGroupCipher(store);
        var device = StubDeviceService.create();
        var props = client.abPropsService();
        var wam = new DefaultWamService(client, props);
        var migration = new LidMigrationService(client, props, wam);
        var transcoder = new MediaTranscoderService(client, props, TestMediaConnectionService.create());

        var service = new MessageService(client, session, group, device, migration, props, wam, transcoder);
        assertNotNull(service,
                "with all valid collaborators MessageService must be constructed without throwing");
    }

    @Test
    @DisplayName("clearPendingMessages: idempotent no-op on a fresh service")
    void clearPendingMessagesIdempotent() {
        var store = MessageFixtures.temporaryStore(SELF_PN, null);
        var client = TestWhatsAppClient.create()
                .withStore(store)
                .withAbPropsService(TestABPropsService.builder().build());
        var session = new SignalSessionCipher(store);
        var group = new SignalGroupCipher(store);
        var device = StubDeviceService.create();
        var props = client.abPropsService();
        var wam = new DefaultWamService(client, props);
        var migration = new LidMigrationService(client, props, wam);
        var transcoder = new MediaTranscoderService(client, props, TestMediaConnectionService.create());
        var service = new MessageService(client, session, group, device, migration, props, wam, transcoder);

        Assertions.assertDoesNotThrow(service::clearPendingMessages);
        Assertions.assertDoesNotThrow(service::clearPendingMessages);
    }
}
