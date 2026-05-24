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
 * Parity tests for {@link MessageDecryption} against WhatsApp Web's
 * {@code WAWebMsgProcessingDecryptEnc.decryptEnc}.
 *
 * @apiNote
 * Exercises the PKMSG and MSG branches of
 * {@link MessageDecryption#decryptFromDevice(byte[], Jid, MessageEncryptionType)}
 * together with the {@link MessageDecryption#hasSessionWith(Jid)} probe; the SKMSG
 * branch needs a full sender-key distribution exchange covered by the group-message
 * integration path and the MSMSG branch is exercised by {@code BotMessageSecretTest}
 * because it is HKDF then AES-GCM with no Signal-session state.
 *
 * @implNote
 * Drives the sender side via {@link MessageEncryption} on a real libsignal session
 * (installed by {@link TestSignalSession#establishSession}); plaintext arguments are
 * raw bytes so the assertion does not depend on protobuf encoding.
 */
@DisplayName("MessageDecryption")
class MessageDecryptionTest {

    private static final Jid SENDER_JID = Jid.of("12025550100:0@s.whatsapp.net");
    private static final Jid RECIPIENT_JID = Jid.of("19254863482:0@s.whatsapp.net");

    /**
     * Verifies that the recipient recovers the exact plaintext bytes from a
     * PKMSG sent by the established sender session.
     */
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
                "decryptFromDevice strips Signal-protocol padding then output equals input byte-for-byte");
    }

    /**
     * Verifies that decrypting a PKMSG installs the inbound Signal session on the
     * recipient side.
     */
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
                "PKMSG decryption installs the inbound session then hasSessionWith must now be true");
    }

    /**
     * Verifies that malformed ciphertext surfaces as
     * {@link WhatsAppMessageException.Receive.InvalidMessage}.
     */
    @Test
    @DisplayName("decryptFromDevice: malformed ciphertext throws WhatsAppMessageException.Receive.InvalidMessage")
    void malformedCiphertextThrows() {
        var recipient = MessageFixtures.temporaryStore(RECIPIENT_JID, null);
        var decryption = decryption(recipient);
        var bogus = new byte[]{0x00, 0x01, 0x02, 0x03};
        assertThrows(WhatsAppMessageException.Receive.InvalidMessage.class,
                () -> decryption.decryptFromDevice(bogus, SENDER_JID, MessageEncryptionType.PKMSG));
    }

    /**
     * Verifies that each argument of
     * {@link MessageDecryption#decryptFromDevice(byte[], Jid, MessageEncryptionType)}
     * is null-checked.
     */
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

    /**
     * Verifies that each constructor argument is null-checked.
     */
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

    /**
     * Verifies that {@link MessageDecryption#hasSessionWith(Jid)} returns
     * {@code false} on a fresh store without throwing.
     */
    @Test
    @DisplayName("hasSessionWith(null): returns false (no NPE on null device JID)")
    void hasSessionWithNullDoesNotThrow() {
        var recipient = MessageFixtures.temporaryStore(RECIPIENT_JID, null);
        var decryption = decryption(recipient);
        assertTrue(!decryption.hasSessionWith(SENDER_JID),
                "fresh store has no sessions");
    }

    /**
     * Builds a {@link MessageEncryption} bound to the supplied store for the
     * sender side of the round-trip.
     *
     * @apiNote
     * Used by every test that needs to produce an encrypted payload before handing
     * it to a {@link MessageDecryption}.
     *
     * @implNote
     * Constructs the libsignal session and group ciphers from the same store so
     * both sides share the same protocol state.
     *
     * @param store the sender's protocol store
     * @return the encryption service for the sender
     */
    private static MessageEncryption encryption(WhatsAppStore store) {
        return new MessageEncryption(store, new SignalSessionCipher(store), new SignalGroupCipher(store));
    }

    /**
     * Builds a {@link MessageDecryption} bound to the supplied store for the
     * recipient side of the round-trip.
     *
     * @apiNote
     * Used by every test that needs to decrypt a payload produced by
     * {@link #encryption(WhatsAppStore)}.
     *
     * @implNote
     * Constructs the libsignal session and group ciphers from the same store so
     * both sides share the same protocol state.
     *
     * @param store the recipient's protocol store
     * @return the decryption service for the recipient
     */
    private static MessageDecryption decryption(WhatsAppStore store) {
        return new MessageDecryption(store, new SignalSessionCipher(store), new SignalGroupCipher(store));
    }
}
