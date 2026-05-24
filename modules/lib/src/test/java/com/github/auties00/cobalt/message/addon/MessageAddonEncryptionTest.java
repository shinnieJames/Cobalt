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
 * Exercises {@link MessageAddonEncryption}, mirroring
 * {@code WAWebAddonEncryption.encryptAddOn} and
 * {@code WAWebAddonEncryption.decryptAddOn}.
 *
 * @apiNote Coverage spans the round-trip for every
 * {@link MessageAddonType} (recovered plaintext matches the original under
 * both the AAD and non-AAD branches); per-call IV freshness (two
 * encryptions of the same plaintext with the same key produce different
 * ciphertexts because the IV is sampled fresh from a CSPRNG each call);
 * AAD tamper-resistance for {@link MessageAddonType#POLL_VOTE} and
 * {@link MessageAddonType#EVENT_RESPONSE} (changing the stanza id or addon
 * sender on decrypt invalidates the auth tag); key isolation (ciphertext
 * bound to one parent secret cannot be decrypted under a different secret);
 * use-case isolation (ciphertext bound to one HKDF label cannot be
 * decrypted as another); and argument validation (32-byte secret
 * enforcement and full null-arg coverage).
 *
 * @implNote Uses fixed 32-byte secrets and synthetic plaintexts; the IV
 * freshness assertion compares hex strings to keep the diagnostic output
 * readable.
 */
@DisplayName("MessageAddonEncryption")
class MessageAddonEncryptionTest {

    /**
     * 32-byte parent secret used as the HKDF input keying material.
     */
    private static final byte[] SECRET = repeatedByte(32, (byte) 0x42);

    /**
     * Alternate 32-byte secret used by the key-isolation test.
     */
    private static final byte[] OTHER_SECRET = repeatedByte(32, (byte) 0x55);

    /**
     * Parent stanza id used as the HKDF info component.
     */
    private static final String STANZA_ID = "3EB0CAFEBABE0123456789";

    /**
     * JID used as the parent message author.
     */
    private static final Jid ORIGINAL_SENDER = Jid.of("12025550100@s.whatsapp.net");

    /**
     * JID used as the addon author.
     */
    private static final Jid ADDON_SENDER = Jid.of("12025550200@s.whatsapp.net");

    /**
     * Verifies the encrypt-then-decrypt round-trip for every addon use
     * case.
     *
     * @apiNote Exercises both the AAD branch ({@link MessageAddonType#POLL_VOTE},
     * {@link MessageAddonType#EVENT_RESPONSE}) and the non-AAD branch
     * (every other use case).
     *
     * @param useCase the use case under test
     */
    @ParameterizedTest(name = "round-trip {0}")
    @EnumSource(MessageAddonType.class)
    @DisplayName("encrypt -> decrypt recovers the plaintext for every addon use case")
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

    /**
     * Verifies that the IV is sampled fresh from the CSPRNG on every
     * encrypt call.
     */
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

    /**
     * Verifies that AAD use cases reject a decrypt under a tampered
     * stanza id.
     */
    @Test
    @DisplayName("AAD use cases: decrypt with a different stanzaId is rejected")
    void aadStanzaIdTamperRejected() {
        var bytes = "vote: option A".getBytes();
        var addon = MessageAddonEncryption.encrypt(
                bytes, SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.POLL_VOTE);

        assertThrows(RuntimeException.class, () -> MessageAddonEncryption.decrypt(
                addon, SECRET, STANZA_ID + "-tampered", ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.POLL_VOTE));
    }

    /**
     * Verifies that AAD use cases reject a decrypt under a tampered addon
     * sender.
     */
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

    /**
     * Verifies that AAD use cases reject a decrypt under a tampered
     * {@code originalSender} (info-binding mismatch).
     */
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

    /**
     * Verifies key isolation: ciphertext bound to one parent secret cannot
     * be decrypted under another.
     */
    @Test
    @DisplayName("ciphertext bound to one parent secret cannot be decrypted under another")
    void keyIsolation() {
        var bytes = "secret reaction".getBytes();
        var addon = MessageAddonEncryption.encrypt(
                bytes, SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION);

        assertThrows(RuntimeException.class, () -> MessageAddonEncryption.decrypt(
                addon, OTHER_SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION));
    }

    /**
     * Verifies use-case isolation: ciphertext bound to one HKDF info label
     * cannot be decrypted as another, even when the AAD policy matches.
     *
     * @implNote {@link MessageAddonType#ENC_REACTION} and
     * {@link MessageAddonType#ENC_COMMENT} share the same AAD policy (no
     * AAD) but use different HKDF info labels, so the derived key differs
     * and the decrypt must fail.
     */
    @Test
    @DisplayName("ciphertext bound to one use case cannot be decrypted as another (HKDF info isolation)")
    void useCaseIsolation() {
        var bytes = "use-case-bound".getBytes();
        var addon = MessageAddonEncryption.encrypt(
                bytes, SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION);

        assertThrows(RuntimeException.class, () -> MessageAddonEncryption.decrypt(
                addon, SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_COMMENT));
    }

    /**
     * Verifies that a non-32-byte secret throws
     * {@link IllegalArgumentException} on both encrypt and decrypt.
     *
     * @implNote The secret-length check happens before the cipher is
     * touched, so the decrypt path can be exercised with a stub addon.
     */
    @Test
    @DisplayName("non-32-byte secret throws IllegalArgumentException on encrypt and decrypt")
    void wrongSecretLengthThrows() {
        var shortSecret = new byte[16];
        assertThrows(IllegalArgumentException.class, () -> MessageAddonEncryption.encrypt(
                "x".getBytes(), shortSecret, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION));

        var stub = new MessageEncryptedAddon(new byte[0], new byte[12]);
        assertThrows(IllegalArgumentException.class, () -> MessageAddonEncryption.decrypt(
                stub, shortSecret, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION));
    }

    /**
     * Verifies that every encrypt argument is required.
     */
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

    /**
     * Verifies that every decrypt argument is required.
     */
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

    /**
     * Verifies that empty plaintext round-trips and that the resulting
     * ciphertext is exactly the 16-byte GCM tag.
     */
    @Test
    @DisplayName("empty plaintext round-trips correctly")
    void emptyPlaintextRoundTrip() {
        var addon = MessageAddonEncryption.encrypt(
                new byte[0], SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION);
        var recovered = MessageAddonEncryption.decrypt(
                addon, SECRET, STANZA_ID, ORIGINAL_SENDER, ADDON_SENDER, MessageAddonType.ENC_REACTION);
        assertEquals(0, recovered.length);
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
