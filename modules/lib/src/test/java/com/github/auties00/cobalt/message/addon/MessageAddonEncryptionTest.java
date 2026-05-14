package com.github.auties00.cobalt.message.addon;

import com.github.auties00.cobalt.model.jid.Jid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MessageAddonEncryption}, mirroring
 * {@code WAWebAddonEncryption.encryptAddOn / decryptAddOn}.
 *
 * <p>Coverage:
 *
 * <ul>
 *   <li>Round-trip encrypt → decrypt for every {@link MessageAddonType}
 *       variant. Verifies the recovered plaintext matches the original
 *       under both the AAD and non-AAD branches.</li>
 *   <li>Per-call IV freshness: two encryptions of the same plaintext with
 *       the same key produce different ciphertexts because the IV is
 *       sampled fresh from a CSPRNG each call.</li>
 *   <li>AAD tamper-resistance for {@link MessageAddonType#POLL_VOTE} and
 *       {@link MessageAddonType#EVENT_RESPONSE}: changing the
 *       {@code stanzaId} or {@code addonSender} on decrypt invalidates the
 *       auth tag.</li>
 *   <li>Key isolation: a ciphertext bound to one parent secret cannot be
 *       decrypted under a different secret.</li>
 *   <li>Argument validation: 32-byte secret enforcement and full null-arg
 *       coverage.</li>
 * </ul>
 */
@DisplayName("MessageAddonEncryption")
class MessageAddonEncryptionTest {

    private static final byte[] SECRET = repeatedByte(32, (byte) 0x42);
    private static final byte[] OTHER_SECRET = repeatedByte(32, (byte) 0x55);
    private static final String STANZA_ID = "3EB0CAFEBABE0123456789";
    private static final Jid ORIGINAL_SENDER = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid ADDON_SENDER = Jid.of("12025550200@s.whatsapp.net");

    @ParameterizedTest(name = "round-trip {0}")
    @EnumSource(MessageAddonType.class)
    @DisplayName("encrypt → decrypt recovers the plaintext for every addon use case")
    void roundTripEveryUseCase(MessageAddonType useCase) {
        var plaintext = "addon-payload-for-" + useCase.name();
        var bytes = plaintext.getBytes();

        var addon = MessageAddonEncryption.encrypt(
                bytes, SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, useCase);
        assertNotNull(addon);
        assertEquals(12, addon.iv().length, "AES-GCM IV is always 12 bytes");
        assertTrue(addon.ciphertext().length > bytes.length,
                "ciphertext must be at least plaintext + 16-byte auth tag");

        var recovered = MessageAddonEncryption.decrypt(
                addon, SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, useCase);
        assertArrayEquals(bytes, recovered, "decrypt must return the original plaintext");
    }

    @Test
    @DisplayName("two encryptions of the same plaintext yield different ciphertexts (fresh IV per call)")
    void freshIvPerCall() {
        var bytes = "same-message".getBytes();
        var first = MessageAddonEncryption.encrypt(
                bytes, SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION);
        var second = MessageAddonEncryption.encrypt(
                bytes, SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION);

        assertNotSame(first, second);
        assertNotEquals(toHex(first.iv()), toHex(second.iv()),
                "IV must be sampled fresh from the RNG on every encrypt call");
        assertNotEquals(toHex(first.ciphertext()), toHex(second.ciphertext()),
                "different IVs must produce different ciphertexts under AES-GCM");
    }

    @Test
    @DisplayName("AAD use cases: decrypt with a different stanzaId is rejected")
    void aadStanzaIdTamperRejected() {
        var bytes = "vote: option A".getBytes();
        var addon = MessageAddonEncryption.encrypt(
                bytes, SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.POLL_VOTE);

        assertThrows(RuntimeException.class, () -> MessageAddonEncryption.decrypt(
                addon, SECRET, STANZA_ID + "-tampered", ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.POLL_VOTE));
    }

    @Test
    @DisplayName("AAD use cases: decrypt with a different addonSender is rejected")
    void aadAddonSenderTamperRejected() {
        var bytes = "vote: option B".getBytes();
        var addon = MessageAddonEncryption.encrypt(
                bytes, SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.POLL_VOTE);

        var differentSender = Jid.of("12025550300@s.whatsapp.net");
        assertThrows(RuntimeException.class, () -> MessageAddonEncryption.decrypt(
                addon, SECRET, STANZA_ID, ORIGINAL_SENDER, differentSender, MessageAddonType.POLL_VOTE));
    }

    @Test
    @DisplayName("AAD use cases: decrypt with a different originalSender is rejected (info-binding mismatch)")
    void aadOriginalSenderTamperRejected() {
        var bytes = "vote: option C".getBytes();
        var addon = MessageAddonEncryption.encrypt(
                bytes, SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.POLL_VOTE);

        var different = Jid.of("12025550400@s.whatsapp.net");
        assertThrows(RuntimeException.class, () -> MessageAddonEncryption.decrypt(
                addon, SECRET, STANZA_ID, different, ADDON_SENDER, MessageAddonType.POLL_VOTE));
    }

    @Test
    @DisplayName("ciphertext bound to one parent secret cannot be decrypted under another")
    void keyIsolation() {
        var bytes = "secret reaction".getBytes();
        var addon = MessageAddonEncryption.encrypt(
                bytes, SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION);

        assertThrows(RuntimeException.class, () -> MessageAddonEncryption.decrypt(
                addon, OTHER_SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION));
    }

    @Test
    @DisplayName("ciphertext bound to one use case cannot be decrypted as another (HKDF info isolation)")
    void useCaseIsolation() {
        var bytes = "use-case-bound".getBytes();
        var addon = MessageAddonEncryption.encrypt(
                bytes, SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION);

        // ENC_COMMENT uses the same AAD policy (no AAD) but a different
        // HKDF info label, so the derived key differs and decrypt must fail.
        assertThrows(RuntimeException.class, () -> MessageAddonEncryption.decrypt(
                addon, SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_COMMENT));
    }

    @Test
    @DisplayName("non-32-byte secret throws IllegalArgumentException on encrypt and decrypt")
    void wrongSecretLengthThrows() {
        var shortSecret = new byte[16];
        assertThrows(IllegalArgumentException.class, () -> MessageAddonEncryption.encrypt(
                "x".getBytes(), shortSecret, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION));

        // For decrypt, use a stub addon — we never reach the cipher because the
        // secret-length check happens first.
        var stub = new MessageEncryptedAddon(new byte[0], new byte[12]);
        assertThrows(IllegalArgumentException.class, () -> MessageAddonEncryption.decrypt(
                stub, shortSecret, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION));
    }

    @Test
    @DisplayName("null arguments throw NullPointerException on encrypt")
    void nullArgsThrowOnEncrypt() {
        assertThrows(NullPointerException.class, () -> MessageAddonEncryption.encrypt(
                null, SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION));
        assertThrows(NullPointerException.class, () -> MessageAddonEncryption.encrypt(
                new byte[0], null, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION));
        assertThrows(NullPointerException.class, () -> MessageAddonEncryption.encrypt(
                new byte[0], SECRET, null, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION));
        assertThrows(NullPointerException.class, () -> MessageAddonEncryption.encrypt(
                new byte[0], SECRET, STANZA_ID, null, ADDON_SENDER, MessageAddonType.ENC_REACTION));
        assertThrows(NullPointerException.class, () -> MessageAddonEncryption.encrypt(
                new byte[0], SECRET, STANZA_ID, ORIGINAL_SENDER, null, MessageAddonType.ENC_REACTION));
        assertThrows(NullPointerException.class, () -> MessageAddonEncryption.encrypt(
                new byte[0], SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, null));
    }

    @Test
    @DisplayName("null arguments throw NullPointerException on decrypt")
    void nullArgsThrowOnDecrypt() {
        var stub = new MessageEncryptedAddon(new byte[16], new byte[12]);
        assertThrows(NullPointerException.class, () -> MessageAddonEncryption.decrypt(
                null, SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION));
        assertThrows(NullPointerException.class, () -> MessageAddonEncryption.decrypt(
                stub, null, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION));
        assertThrows(NullPointerException.class, () -> MessageAddonEncryption.decrypt(
                stub, SECRET, null, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION));
        assertThrows(NullPointerException.class, () -> MessageAddonEncryption.decrypt(
                stub, SECRET, STANZA_ID, null, ADDON_SENDER, MessageAddonType.ENC_REACTION));
        assertThrows(NullPointerException.class, () -> MessageAddonEncryption.decrypt(
                stub, SECRET, STANZA_ID, ORIGINAL_SENDER, null, MessageAddonType.ENC_REACTION));
        assertThrows(NullPointerException.class, () -> MessageAddonEncryption.decrypt(
                stub, SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, null));
    }

    @Test
    @DisplayName("empty plaintext round-trips correctly")
    void emptyPlaintextRoundTrip() {
        var addon = MessageAddonEncryption.encrypt(
                new byte[0], SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION);
        var recovered = MessageAddonEncryption.decrypt(
                addon, SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION);
        assertEquals(0, recovered.length);
        // For 0-byte plaintext, ciphertext is exactly the 16-byte GCM tag.
        assertEquals(16, addon.ciphertext().length);
    }

    /**
     * Returns a byte array of {@code len} bytes filled with {@code b}.
     *
     * @param len the array length
     * @param b   the fill byte
     * @return the filled byte array
     */
    private static byte[] repeatedByte(int len, byte b) {
        var out = new byte[len];
        Arrays.fill(out, b);
        return out;
    }

    /**
     * Returns the lowercase hex string for {@code bytes}.
     *
     * @param bytes the input bytes
     * @return the hex-encoded representation
     */
    private static String toHex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (var b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
