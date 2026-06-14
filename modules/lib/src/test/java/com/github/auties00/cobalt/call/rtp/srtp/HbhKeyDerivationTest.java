package com.github.auties00.cobalt.call.rtp.srtp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Known-answer tests for {@link HbhKeyDerivation}.
 *
 * <p>The hop-by-hop key and the expected chaining salts were captured live from the WhatsApp VoIP
 * native call engine: the 30-byte key is the salt secret and key secret read at the
 * {@code VoipCrypto::HkdfSha256} call site, and the three expected chaining salts are the salt
 * outputs the engine fed as the HKDF salt into each group's key step. Pinning the chaining salts pins
 * the label bytes, the all-zero chaining-step salt, the 14|16 split, and the salt-into-key chaining
 * all at once; the keymat step then expands deterministically from the key secret and the chaining
 * salt.
 */
public class HbhKeyDerivationTest {
    private static final HexFormat HEX = HexFormat.of();

    // hop-by-hop key = salt secret (14) || key secret (16), captured live
    private static final byte[] HBH_KEY =
            HEX.parseHex("514c0e0b830d1e4e39c380dc21f3" + "6afb8e5d7a47949778e1996e538dc6e4");

    @Test
    @DisplayName("derives each group's chaining salt to the captured live vector")
    public void chainSalts() {
        assertArrayEquals(
                HEX.parseHex("2c47ed403b521274846dd60614ad6b25e77e8a6bc1eace6d6fd32d30ff55a717"),
                HbhKeyDerivation.deriveChainSalt(HBH_KEY, HbhKeyDerivation.Group.SRTCP));
        assertArrayEquals(
                HEX.parseHex("0519c97898bef3b8aa6f26d6cda91751c3565a691b5b1fae4c8d7950c0880422"),
                HbhKeyDerivation.deriveChainSalt(HBH_KEY, HbhKeyDerivation.Group.UPLINK_SRTCP));
        assertArrayEquals(
                HEX.parseHex("9d97c9864a6e1aea68fe5f2721fa007447f15140a3bfe4b2fb3cb42c8e452c3b"),
                HbhKeyDerivation.deriveChainSalt(HBH_KEY, HbhKeyDerivation.Group.DOWNLINK_SRTCP));
    }

    @Test
    @DisplayName("derives a 30-byte keymat splittable into a 16-byte key and 14-byte salt")
    public void keymat() {
        for (var group : HbhKeyDerivation.Group.values()) {
            var keymat = HbhKeyDerivation.deriveKeymat(HBH_KEY, group);
            assertEquals(30, keymat.length, group.name());
            assertEquals(16, HbhKeyDerivation.masterKey(keymat).length, group.name());
            assertEquals(14, HbhKeyDerivation.masterSalt(keymat).length, group.name());
        }
    }

    @Test
    @DisplayName("rejects a hop-by-hop key whose length is not 30 bytes")
    public void rejectsWrongLength() {
        assertThrows(IllegalArgumentException.class,
                () -> HbhKeyDerivation.deriveKeymat(new byte[29], HbhKeyDerivation.Group.SRTCP));
        assertThrows(IllegalArgumentException.class,
                () -> HbhKeyDerivation.deriveChainSalt(new byte[31], HbhKeyDerivation.Group.SRTCP));
    }
}
