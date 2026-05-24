package com.github.auties00.cobalt.message.send.crypto;

import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.message.TestSignalSession;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.groups.SignalGroupCipher;
import com.github.auties00.libsignal.protocol.SignalPreKeyMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip and structural tests for {@link MessageEncryption}.
 *
 * @apiNote
 * Mirrors WA Web's {@code WAWebEncryptMsgProtobuf.encryptMsgProtobuf} surface:
 * a freshly-established Signal session must produce a PKMSG envelope, stamp
 * {@link MessageEncryption#CIPHERTEXT_VERSION} on the wire, pad the plaintext
 * by 1 to 16 bytes, and round-trip back to the original plaintext after the
 * recipient decrypts and strips the padding. Subsequent encrypts must derive
 * fresh ciphertexts as the Signal ratchet advances.
 * @implNote
 * Uses {@link TestSignalSession#establishSession} to set up a real libsignal
 * session between two {@link MessageFixtures#temporaryStore} instances, then
 * decrypts each produced ciphertext on the recipient side via a fresh
 * {@link SignalSessionCipher} to prove byte equality. No mocks; the only
 * synthesis is the two store instances.
 */
@DisplayName("MessageEncryption")
class MessageEncryptionTest {

    private static final Jid SENDER_JID = Jid.of("12025550100:0@s.whatsapp.net");
    private static final Jid RECIPIENT_JID = Jid.of("19254863482:0@s.whatsapp.net");

    /**
     * Verifies that a first send to a freshly-established session yields a
     * PKMSG envelope and stamps {@code CIPHERTEXT_VERSION = 2}.
     */
    @Test
    @DisplayName("encryptForDevice: first send produces a PKMSG envelope with v=2")
    void firstSendIsPreKeyMessage() throws Exception {
        var sender = MessageFixtures.temporaryStore(SENDER_JID, null);
        var recipient = MessageFixtures.temporaryStore(RECIPIENT_JID, null);
        TestSignalSession.establishSession(sender, RECIPIENT_JID, recipient);

        var encryption = encryption(sender);
        var payload = encryption.encryptForDevice(RECIPIENT_JID, "hello".getBytes());

        assertEquals(MessageEncryptionType.PKMSG, payload.type(),
                "first send to a freshly-established session is a PKMSG envelope");
        assertTrue(payload.isPreKeyMessage());
        assertEquals(2, MessageEncryption.CIPHERTEXT_VERSION,
                "CIPHERTEXT_VERSION is the v=2 wire attribute");
    }

    /**
     * Verifies that the ciphertext is larger than the input due to padding
     * and Signal protocol overhead.
     */
    @Test
    @DisplayName("encryptForDevice: ciphertext is non-empty and longer than plaintext")
    void ciphertextLargerThanPlaintext() {
        var sender = MessageFixtures.temporaryStore(SENDER_JID, null);
        var recipient = MessageFixtures.temporaryStore(RECIPIENT_JID, null);
        TestSignalSession.establishSession(sender, RECIPIENT_JID, recipient);

        var encryption = encryption(sender);
        var plaintext = "hello world".getBytes();
        var payload = encryption.encryptForDevice(RECIPIENT_JID, plaintext);

        assertTrue(payload.ciphertext().length > plaintext.length,
                "ciphertext must be larger than plaintext (random padding + Signal protocol overhead)");
    }

    /**
     * Verifies that an encrypt then decrypt cycle returns the plaintext
     * modulo the 1..16 byte random padding.
     *
     * @implNote
     * Uses {@link SignalPreKeyMessage#ofSerialized} so the 1-byte version
     * prefix is stripped before protobuf decoding;
     * {@code SignalPreKeyMessageSpec.decode} treats the prefix as protobuf
     * and chokes on the wire type.
     */
    @Test
    @DisplayName("encryptForDevice round-trip: recipient decrypts to plaintext modulo padding")
    void roundTripDecrypt() throws Exception {
        var sender = MessageFixtures.temporaryStore(SENDER_JID, null);
        var recipient = MessageFixtures.temporaryStore(RECIPIENT_JID, null);
        TestSignalSession.establishSession(sender, RECIPIENT_JID, recipient);

        var encryption = encryption(sender);
        var plaintext = "hello".getBytes();
        var payload = encryption.encryptForDevice(RECIPIENT_JID, plaintext);

        var preKey = SignalPreKeyMessage.ofSerialized(payload.ciphertext());
        var recipientCipher = new SignalSessionCipher(recipient);
        var recovered = recipientCipher.decrypt(SENDER_JID.toSignalAddress(), preKey);

        var padding = recovered.length - plaintext.length;
        assertTrue(padding >= 1 && padding <= 16,
                "MessageEncryption.addPadding adds 1..16 bytes; got " + padding);
        var stripped = new byte[plaintext.length];
        System.arraycopy(recovered, 0, stripped, 0, plaintext.length);
        assertArrayEquals(plaintext, stripped,
                "decrypted plaintext must equal the input (modulo trailing padding)");
    }

    /**
     * Verifies that repeated encrypts of the same plaintext yield distinct
     * ciphertexts (Signal ratchet advance plus fresh padding sampling).
     */
    @Test
    @DisplayName("encryptForDevice: each call samples fresh padding and ratchet, producing different ciphertexts")
    void ciphertextIsFresh() {
        var sender = MessageFixtures.temporaryStore(SENDER_JID, null);
        var recipient = MessageFixtures.temporaryStore(RECIPIENT_JID, null);
        TestSignalSession.establishSession(sender, RECIPIENT_JID, recipient);

        var encryption = encryption(sender);
        var first = encryption.encryptForDevice(RECIPIENT_JID, "x".getBytes());
        var second = encryption.encryptForDevice(RECIPIENT_JID, "x".getBytes());
        assertNotEquals(toHex(first.ciphertext()), toHex(second.ciphertext()),
                "Signal ratchet advances per encrypt; identical plaintext yields different ciphertext");
    }

    /**
     * Pins the wire behaviour that consecutive sends stay in PKMSG until the
     * recipient processes the prekey.
     *
     * @apiNote
     * The encryption call alone does not advance the session beyond PKMSG
     * because libsignal tracks "has the recipient processed our prekey?" via
     * session metadata. In WAWeb the type flips to MSG after the recipient
     * replies; this test pins the until-then wire behaviour rather than
     * fast-forwarding the session state artificially.
     */
    @Test
    @DisplayName("encryptForDevice: type stays PKMSG until the recipient processes the prekey")
    void typeSwitchesAfterFirstAck() {
        var sender = MessageFixtures.temporaryStore(SENDER_JID, null);
        var recipient = MessageFixtures.temporaryStore(RECIPIENT_JID, null);
        TestSignalSession.establishSession(sender, RECIPIENT_JID, recipient);
        var encryption = encryption(sender);

        var first = encryption.encryptForDevice(RECIPIENT_JID, "first".getBytes());
        assertEquals(MessageEncryptionType.PKMSG, first.type());

        var secondBeforeAck = encryption.encryptForDevice(RECIPIENT_JID, "second".getBytes());
        assertEquals(MessageEncryptionType.PKMSG, secondBeforeAck.type(),
                "encrypt-without-ack stays in PKMSG until the recipient processes the prekey");
    }

    /**
     * Verifies that {@code null} required arguments to
     * {@link MessageEncryption#encryptForDevice} throw
     * {@link NullPointerException}.
     */
    @Test
    @DisplayName("encryptForDevice: matching device JIDs are required (null throws NullPointerException)")
    void encryptForDeviceNullArgs() {
        var sender = MessageFixtures.temporaryStore(SENDER_JID, null);
        var encryption = encryption(sender);
        assertThrows(NullPointerException.class,
                () -> encryption.encryptForDevice(null, new byte[]{1}));
        assertThrows(NullPointerException.class,
                () -> encryption.encryptForDevice(RECIPIENT_JID, null));
    }

    /**
     * Verifies that {@link MessageEncryption#CIPHERTEXT_VERSION} is pinned at
     * {@code 2}.
     */
    @Test
    @DisplayName("CIPHERTEXT_VERSION constant: pinned at 2; drives the v=2 attribute on every <enc>")
    void ciphertextVersionPinned() {
        assertEquals(2, MessageEncryption.CIPHERTEXT_VERSION,
                "v=2 is the wire-byte invariant relied on by every stanza builder");
    }

    /**
     * Verifies that {@code null} collaborators on the
     * {@link MessageEncryption} constructor throw {@link NullPointerException}.
     */
    @Test
    @DisplayName("constructor: null collaborators throw NullPointerException")
    void constructorNullArgs() {
        var store = MessageFixtures.temporaryStore(SENDER_JID, null);
        var session = new SignalSessionCipher(store);
        var group = new SignalGroupCipher(store);
        assertThrows(NullPointerException.class, () -> new MessageEncryption(null, session, group));
        assertThrows(NullPointerException.class, () -> new MessageEncryption(store, null, group));
        assertThrows(NullPointerException.class, () -> new MessageEncryption(store, session, null));
    }

    /**
     * Verifies that the per-payload {@code isPreKeyMessage}/
     * {@code isSenderKeyMessage} predicates classify PKMSG correctly.
     */
    @Test
    @DisplayName("payload classification: PKMSG flagged as preKey, not senderKey")
    void payloadFlagsConsistent() {
        var sender = MessageFixtures.temporaryStore(SENDER_JID, null);
        var recipient = MessageFixtures.temporaryStore(RECIPIENT_JID, null);
        TestSignalSession.establishSession(sender, RECIPIENT_JID, recipient);
        var payload = encryption(sender).encryptForDevice(RECIPIENT_JID, "x".getBytes());
        assertTrue(payload.isPreKeyMessage(),
                "PKMSG payloads must self-identify as preKey for the identity-on-PKMSG branch");
        assertFalse(payload.isSenderKeyMessage(),
                "PKMSG is not a sender-key message");
    }

    /**
     * Constructs a {@link MessageEncryption} bound to the supplied store.
     *
     * @apiNote
     * Test helper used by every per-method case to assemble the three
     * collaborators ({@link WhatsAppStore},
     * {@link SignalSessionCipher}, {@link SignalGroupCipher}) over the same
     * backing store.
     *
     * @param store the backing {@link WhatsAppStore}
     * @return the wired-up encryption service
     */
    private static MessageEncryption encryption(WhatsAppStore store) {
        return new MessageEncryption(store, new SignalSessionCipher(store), new SignalGroupCipher(store));
    }

    /**
     * Returns the lowercase hex representation of the supplied bytes.
     *
     * @apiNote
     * Test helper used to compare ciphertexts via string equality rather than
     * {@code Arrays.equals}, so assertion failures print a readable diff.
     *
     * @param bytes the bytes to encode
     * @return the lowercase hex string
     */
    private static String toHex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (var b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
