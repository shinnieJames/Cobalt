package com.github.auties00.cobalt.call.rtp.srtp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Known-answer tests for {@link CallKeyDerivation} pinning the Family-B end-to-end participant SRTP
 * master derivation to live WhatsApp VoIP captures.
 *
 * <p>The call key and the expected masters were read verbatim as cipher {@code set_key} inputs from
 * the native call engine for call key
 * {@code bc4e7efa3efe251b9d5aeb3c36843d776c27777d67e5555f7956fd4f8ab003d8}; pinning them fixes the
 * empty HKDF salt, the participant-JID info bytes, and the 30-byte expansion.
 */
public class CallKeyDerivationTest {
    private static final HexFormat HEX = HexFormat.of();

    // 32-byte call key fanned out per device in the offer <enc> envelope, captured live.
    private static final byte[] CALL_KEY =
            HEX.parseHex("bc4e7efa3efe251b9d5aeb3c36843d776c27777d67e5555f7956fd4f8ab003d8");

    @Test
    @DisplayName("derives the caller participant master to the captured live vector")
    public void callerMaster() {
        assertArrayEquals(
                HEX.parseHex("c1087730f4b5c07801a37795c5335885f751ba1c1cbe9c3669965b50ba1d"),
                CallKeyDerivation.deriveMaster(CALL_KEY, "39110693621863:29@lid"));
    }

    @Test
    @DisplayName("derives the peer participant master whose captured prefix is 357a151c")
    public void peerMasterPrefix() {
        var master = CallKeyDerivation.deriveMaster(CALL_KEY, "258252122116273:71@lid");
        var prefix = HEX.formatHex(master).substring(0, 8);
        assertEquals("357a151c", prefix,
                "peer master must begin with the captured 357a151c prefix");
    }

    @Test
    @DisplayName("splits the 30-byte master into a 16-byte key and 14-byte salt that re-concatenate")
    public void splitMaster() {
        var master = CallKeyDerivation.deriveMaster(CALL_KEY, "39110693621863:29@lid");
        assertEquals(30, master.length);

        var key = CallKeyDerivation.masterKey(master);
        var salt = CallKeyDerivation.masterSalt(master);
        assertEquals(16, key.length);
        assertEquals(14, salt.length);

        var recombined = new byte[key.length + salt.length];
        System.arraycopy(key, 0, recombined, 0, key.length);
        System.arraycopy(salt, 0, recombined, key.length, salt.length);
        assertArrayEquals(master, recombined,
                "masterKey || masterSalt must reconstruct the master");
    }

    @Test
    @DisplayName("rejects a call key whose length is not 32 bytes")
    public void rejectsWrongLength() {
        assertThrows(IllegalArgumentException.class,
                () -> CallKeyDerivation.deriveMaster(new byte[31], "39110693621863:29@lid"));
        assertThrows(IllegalArgumentException.class,
                () -> CallKeyDerivation.deriveMaster(new byte[33], "39110693621863:29@lid"));
    }
}
