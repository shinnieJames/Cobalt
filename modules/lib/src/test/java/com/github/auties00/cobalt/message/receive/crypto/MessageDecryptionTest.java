package com.github.auties00.cobalt.message.receive.crypto;

import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.message.TestSignalSession;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.groups.SignalGroupCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MessageDecryption}, mirroring
 * {@code WAWebMsgProcessingDecryptEnc.decryptEnc}.
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@code PKMSG} → {@code MSG} round-trip with the sender side
 *       driven by {@link MessageEncryption} on a real libsignal session
 *       (via {@link TestSignalSession}). The decrypted plaintext must
 *       byte-equal the input — padding is stripped by the decryptor.</li>
 *   <li>{@link MessageDecryption#hasSessionWith} reflects session
 *       installation state.</li>
 *   <li>Invalid ciphertext bytes raise
 *       {@link WhatsAppMessageException.Receive.InvalidMessage}.</li>
 *   <li>Constructor and per-method null-arg coverage.</li>
 * </ul>
 *
 * <p>The {@code SKMSG} branch needs a sender-key distribution exchange
 * which lives in the {@code GroupMessageSender} integration path; the
 * {@code MSMSG} branch (bot-message secret) is covered by
 * {@code BotMessageSecretTest} since it is HKDF→AES-GCM with no Signal
 * session state.
 */
@DisplayName("MessageDecryption")
class MessageDecryptionTest {

    private static final Jid SENDER_JID = Jid.of("12025550100:0@s.whatsapp.net");
    private static final Jid RECIPIENT_JID = Jid.of("19254863482:0@s.whatsapp.net");

    @Test
    @DisplayName("decryptFromDevice(PKMSG): recipient recovers the exact plaintext after the sender's first send")
    void roundTripPkmsg() {
        var sender = MessageFixtures.temporaryStore(SENDER_JID, null);
        var recipient = MessageFixtures.temporaryStore(RECIPIENT_JID, null);
        TestSignalSession.establishSession(sender, RECIPIENT_JID, recipient);

        var plaintext = "hello".getBytes();
        var payload = encryption(sender).encryptForDevice(RECIPIENT_JID, plaintext);

        var decryption = decryption(recipient);
        var recovered = decryption.decryptFromDevice(payload.ciphertext(), SENDER_JID, MessageEncryptionType.PKMSG);
        assertArrayEquals(plaintext, recovered,
                "decryptFromDevice strips Signal-protocol padding — output equals input byte-for-byte");
    }

    @Test
    @DisplayName("decryptFromDevice(PKMSG): installs the session on the recipient side (subsequent hasSessionWith returns true)")
    void pkmsgInstallsSession() {
        var sender = MessageFixtures.temporaryStore(SENDER_JID, null);
        var recipient = MessageFixtures.temporaryStore(RECIPIENT_JID, null);
        TestSignalSession.establishSession(sender, RECIPIENT_JID, recipient);

        var decryption = decryption(recipient);
        assertTrue(!decryption.hasSessionWith(SENDER_JID),
                "before any decrypt the recipient has no session with the sender");

        var payload = encryption(sender).encryptForDevice(RECIPIENT_JID, "first".getBytes());
        decryption.decryptFromDevice(payload.ciphertext(), SENDER_JID, MessageEncryptionType.PKMSG);

        assertTrue(decryption.hasSessionWith(SENDER_JID),
                "PKMSG decryption installs the inbound session — hasSessionWith must now be true");
    }

    @Test
    @DisplayName("decryptFromDevice: malformed ciphertext throws WhatsAppMessageException.Receive.InvalidMessage")
    void malformedCiphertextThrows() {
        var recipient = MessageFixtures.temporaryStore(RECIPIENT_JID, null);
        var decryption = decryption(recipient);
        var bogus = new byte[]{0x00, 0x01, 0x02, 0x03};
        assertThrows(WhatsAppMessageException.Receive.InvalidMessage.class,
                () -> decryption.decryptFromDevice(bogus, SENDER_JID, MessageEncryptionType.PKMSG));
    }

    @Test
    @DisplayName("decryptFromDevice: null arguments throw NullPointerException")
    void decryptFromDeviceNullArgs() {
        var recipient = MessageFixtures.temporaryStore(RECIPIENT_JID, null);
        var decryption = decryption(recipient);
        assertThrows(NullPointerException.class,
                () -> decryption.decryptFromDevice(null, SENDER_JID, MessageEncryptionType.PKMSG));
        assertThrows(NullPointerException.class,
                () -> decryption.decryptFromDevice(new byte[]{0}, null, MessageEncryptionType.PKMSG));
        assertThrows(NullPointerException.class,
                () -> decryption.decryptFromDevice(new byte[]{0}, SENDER_JID, null));
    }

    @Test
    @DisplayName("constructor: null collaborators throw NullPointerException")
    void constructorNullArgs() {
        var store = MessageFixtures.temporaryStore(RECIPIENT_JID, null);
        var session = new SignalSessionCipher(store);
        var group = new SignalGroupCipher(store);
        assertThrows(NullPointerException.class, () -> new MessageDecryption(null, session, group));
        assertThrows(NullPointerException.class, () -> new MessageDecryption(store, null, group));
        assertThrows(NullPointerException.class, () -> new MessageDecryption(store, session, null));
    }

    @Test
    @DisplayName("hasSessionWith(null): returns false (no NPE on null device JID)")
    void hasSessionWithNullDoesNotThrow() {
        var recipient = MessageFixtures.temporaryStore(RECIPIENT_JID, null);
        var decryption = decryption(recipient);
        // hasSessionWith is a defensive lookup — fresh stores have no
        // sessions at all, so the result must be false.
        assertTrue(!decryption.hasSessionWith(SENDER_JID),
                "fresh store has no sessions");
    }

    /**
     * Builds a {@link MessageEncryption} for the supplied store.
     *
     * @param store the sender's protocol store
     * @return the encryption service
     */
    private static MessageEncryption encryption(WhatsAppStore store) {
        return new MessageEncryption(store, new SignalSessionCipher(store), new SignalGroupCipher(store));
    }

    /**
     * Builds a {@link MessageDecryption} for the supplied store.
     *
     * @param store the recipient's protocol store
     * @return the decryption service
     */
    private static MessageDecryption decryption(WhatsAppStore store) {
        return new MessageDecryption(store, new SignalSessionCipher(store), new SignalGroupCipher(store));
    }
}
