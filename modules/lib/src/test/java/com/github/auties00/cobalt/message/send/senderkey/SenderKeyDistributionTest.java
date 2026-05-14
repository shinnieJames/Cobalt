package com.github.auties00.cobalt.message.send.senderkey;

import com.github.auties00.cobalt.device.StubDeviceService;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.message.TestSignalSession;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.groups.SignalGroupCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SenderKeyDistribution}, mirroring
 * {@code WAWebGetGroupKeyDistributionMsg.getKeyDistributionMsg}.
 *
 * <p>The unit takes a serialised
 * {@code SenderKeyDistributionMessage} (produced by the libsignal group
 * cipher) and produces one
 * {@link com.github.auties00.cobalt.message.send.crypto.MessageEncryptedPayload}
 * per recipient device by wrapping the distribution protobuf and
 * encrypting it through the Signal session cipher.
 */
@DisplayName("SenderKeyDistribution")
class SenderKeyDistributionTest {

    private static final Jid SENDER_BARE = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid GROUP = Jid.of("120363023250764418@g.us");
    private static final Jid DEVICE_ONE = Jid.of("19254863482:0@s.whatsapp.net");
    private static final Jid DEVICE_TWO = Jid.of("19254863482:1@s.whatsapp.net");

    @Test
    @DisplayName("constructor: every collaborator is required (null throws NullPointerException)")
    void constructorNullArgs() {
        var senderStore = MessageFixtures.temporaryStore(SENDER_BARE, null);
        var encryption = new MessageEncryption(senderStore,
                new SignalSessionCipher(senderStore),
                new SignalGroupCipher(senderStore));
        var deviceService = StubDeviceService.create();

        assertThrows(NullPointerException.class,
                () -> new SenderKeyDistribution(null, deviceService, senderStore));
        assertThrows(NullPointerException.class,
                () -> new SenderKeyDistribution(encryption, null, senderStore));
        assertThrows(NullPointerException.class,
                () -> new SenderKeyDistribution(encryption, deviceService, null));
    }

    @Test
    @DisplayName("encrypt: produces one MessageEncryptedPayload per recipient device with PKMSG type for new sessions")
    void encryptPerDevicePkmsg() {
        var senderStore = MessageFixtures.temporaryStore(SENDER_BARE, null);
        var recipientStore = MessageFixtures.temporaryStore(SENDER_BARE, null);
        TestSignalSession.establishSession(senderStore, DEVICE_ONE, recipientStore);

        var encryption = new MessageEncryption(senderStore,
                new SignalSessionCipher(senderStore),
                new SignalGroupCipher(senderStore));
        var distribution = new SenderKeyDistribution(encryption,
                StubDeviceService.create(), senderStore);

        // Bootstrap the sender key for the group so encryptForGroup is callable
        // (not strictly needed by encrypt() — it just packages senderKeyBytes —
        // but we use real bytes here, taken from the encryption service).
        var senderKeyBytes = encryption.getSenderKeyBytes(GROUP, SENDER_BARE);

        var payloads = distribution.encrypt(GROUP, senderKeyBytes, List.of(DEVICE_ONE));

        assertEquals(1, payloads.size(),
                "one payload per recipient device");
        var payload = payloads.getFirst();
        assertNotNull(payload.ciphertext());
        assertEquals(MessageEncryptionType.PKMSG, payload.type(),
                "first-distribution to a fresh session produces PKMSG");
        assertTrue(payload.ciphertext().length > 0);
    }

    @Test
    @DisplayName("encrypt: multi-device fanout yields one payload per device when each has a session")
    void encryptMultiDevice() {
        var senderStore = MessageFixtures.temporaryStore(SENDER_BARE, null);
        var recipientStoreOne = MessageFixtures.temporaryStore(SENDER_BARE, null);
        var recipientStoreTwo = MessageFixtures.temporaryStore(SENDER_BARE, null);
        TestSignalSession.establishSession(senderStore, DEVICE_ONE, recipientStoreOne);
        TestSignalSession.establishSession(senderStore, DEVICE_TWO, recipientStoreTwo);

        var encryption = new MessageEncryption(senderStore,
                new SignalSessionCipher(senderStore),
                new SignalGroupCipher(senderStore));
        var distribution = new SenderKeyDistribution(encryption,
                StubDeviceService.create(), senderStore);
        var senderKeyBytes = encryption.getSenderKeyBytes(GROUP, SENDER_BARE);

        var payloads = distribution.encrypt(GROUP, senderKeyBytes, List.of(DEVICE_ONE, DEVICE_TWO));

        assertEquals(2, payloads.size(),
                "two recipient devices → two distribution payloads");
        for (var payload : payloads) {
            assertEquals(MessageEncryptionType.PKMSG, payload.type());
        }
    }

    @Test
    @DisplayName("encrypt: devices without a session are silently dropped (when non-primary)")
    void noSessionNonPrimaryDevicesDropped() {
        // DEVICE_TWO has no session established. Since it's a companion
        // (non-primary), the encryption failure is logged + dropped, not thrown.
        var senderStore = MessageFixtures.temporaryStore(SENDER_BARE, null);
        var recipientStore = MessageFixtures.temporaryStore(SENDER_BARE, null);
        TestSignalSession.establishSession(senderStore, DEVICE_ONE, recipientStore);

        var encryption = new MessageEncryption(senderStore,
                new SignalSessionCipher(senderStore),
                new SignalGroupCipher(senderStore));
        var distribution = new SenderKeyDistribution(encryption,
                StubDeviceService.create(), senderStore);
        var senderKeyBytes = encryption.getSenderKeyBytes(GROUP, SENDER_BARE);

        // DEVICE_TWO has no session — encryption for it fails internally;
        // since it's a companion (device != 0) the failure is swallowed.
        var payloads = distribution.encrypt(GROUP, senderKeyBytes, List.of(DEVICE_ONE, DEVICE_TWO));
        assertEquals(1, payloads.size(),
                "companion device without a session must be dropped, not throw");
    }

    @Test
    @DisplayName("encrypt: null arguments throw NullPointerException")
    void encryptNullArgs() {
        var senderStore = MessageFixtures.temporaryStore(SENDER_BARE, null);
        var encryption = new MessageEncryption(senderStore,
                new SignalSessionCipher(senderStore),
                new SignalGroupCipher(senderStore));
        var distribution = new SenderKeyDistribution(encryption,
                StubDeviceService.create(), senderStore);
        var senderKeyBytes = encryption.getSenderKeyBytes(GROUP, SENDER_BARE);

        assertThrows(NullPointerException.class,
                () -> distribution.encrypt(null, senderKeyBytes, List.of(DEVICE_ONE)));
        assertThrows(NullPointerException.class,
                () -> distribution.encrypt(GROUP, null, List.of(DEVICE_ONE)));
        assertThrows(NullPointerException.class,
                () -> distribution.encrypt(GROUP, senderKeyBytes, null));
    }

    @Test
    @DisplayName("encrypt: empty device list returns empty list (no payloads)")
    void encryptEmptyDeviceList() {
        var senderStore = MessageFixtures.temporaryStore(SENDER_BARE, null);
        var encryption = new MessageEncryption(senderStore,
                new SignalSessionCipher(senderStore),
                new SignalGroupCipher(senderStore));
        var distribution = new SenderKeyDistribution(encryption,
                StubDeviceService.create(), senderStore);
        var senderKeyBytes = encryption.getSenderKeyBytes(GROUP, SENDER_BARE);

        var payloads = distribution.encrypt(GROUP, senderKeyBytes, List.of());
        assertEquals(0, payloads.size(),
                "empty device list → empty payload list (no work)");
    }
}
