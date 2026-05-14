package com.github.auties00.cobalt.message;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.StubDeviceService;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.wam.DefaultWamService;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.groups.SignalGroupCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link MessageService}, the public facade that wires the
 * send and receive pipelines together. The class is a thin orchestrator
 * — its value-add lives in constructor null-arg coverage and in the
 * delegation of the public {@code send / sendPeer / process /
 * clearPendingMessages} methods to the underlying services.
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

        // null client
        assertThrows(NullPointerException.class, () -> new MessageService(null, session, group, device, props, wam));
        // null sessionCipher
        assertThrows(NullPointerException.class, () -> new MessageService(client, null, group, device, props, wam));
        // null groupCipher
        assertThrows(NullPointerException.class, () -> new MessageService(client, session, null, device, props, wam));
        // null deviceService
        assertThrows(NullPointerException.class, () -> new MessageService(client, session, group, null, props, wam));
        // null abPropsService
        assertThrows(NullPointerException.class, () -> new MessageService(client, session, group, device, null, wam));
        // null wamService
        assertThrows(NullPointerException.class, () -> new MessageService(client, session, group, device, props, null));
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

        var service = new MessageService(client, session, group, device, props, wam);
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
        var service = new MessageService(client, session, group, device, props, wam);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(service::clearPendingMessages);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(service::clearPendingMessages);
    }
}
